package org.sdase.commons.server.dropwizard.bundles.internal;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import io.dropwizard.Configuration;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public class EnvironmentVariableSourceProvider<T extends Configuration>
    implements ConfigurationSourceProvider {

  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Class<T> klass;
  private final ObjectMapper objectMapper;
  private final ConfigurationSourceProvider delegate;

  /**
   * Create a new instance.
   *
   * @param delegate The underlying {@link ConfigurationSourceProvider}.
   */
  public EnvironmentVariableSourceProvider(
      Class<T> klass, ObjectMapper objectMapper, ConfigurationSourceProvider delegate) {
    this.klass = klass;
    this.objectMapper = objectMapper;
    this.delegate = requireNonNull(delegate);
  }

  /** {@inheritDoc} */
  @Override
  public InputStream open(String path) throws IOException {
    try (final InputStream in = delegate.open(path)) {
      Yaml yaml = new Yaml();
      @SuppressWarnings("unchecked")
      Map<String, Object> root = yaml.load(in);

      try {
        if (root == null) {
          root = new HashMap<>();
        }

        /*

        server:
          applicationConnectors:
          - type: http
            port: 0

                 */

        /*


        Rules for env variables:

        We will do regex matching, case incensetive

        we allow array access!

        camelCase properties are converted to CAMEL_CASE

        more rules?

        how about microprofile, exact matches, ...?

         */

        objectMapper.acceptJsonFormatVisitor(
            klass,
            new JsonFormatVisitorWrapper.Base() {
              private final JsonFormatVisitorWrapper parent = this;

              @Override
              public JsonObjectFormatVisitor expectObjectFormat(JavaType type)
                  throws JsonMappingException {
                return new JsonObjectFormatVisitor.Base() {
                  @Override
                  public void optionalProperty(BeanProperty prop) throws JsonMappingException {
                    LOG.warn("Found property {} ({})", prop.getFullName(), prop.getType());

                    TypeDeserializer typeDeserializer =
                        objectMapper
                            .getDeserializationConfig()
                            .findTypeDeserializer(prop.getType());
                    Class<?> defaultImpl =
                        typeDeserializer != null ? typeDeserializer.getDefaultImpl() : null;
                    objectMapper.acceptJsonFormatVisitor(
                        defaultImpl == null ? prop.getType().getRawClass() : defaultImpl, parent);
                  }
                };
                // TODO: visit klass with an object mapper and find all potential environment
                // variable names

                // TODO: Evaluate all environment variables and then put them into root

              }
            });

      } catch (ClassCastException ex) {
        LOG.error("Unable to inject environment variable config, invalid config format");
      }

      final String substituted = yaml.dump(root);
      return new ByteArrayInputStream(substituted.getBytes(StandardCharsets.UTF_8));
    }
  }
}
