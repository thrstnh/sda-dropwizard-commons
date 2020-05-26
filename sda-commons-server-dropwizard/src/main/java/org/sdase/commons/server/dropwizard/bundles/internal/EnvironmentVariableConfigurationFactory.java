package org.sdase.commons.server.dropwizard.bundles.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.Configuration;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class EnvironmentVariableConfigurationFactory<T extends Configuration>
    implements ConfigurationFactory<T> {

  private final ConfigurationFactory<T> baseConfigurationFactory;
  private final Class<T> klass;
  private final ObjectMapper objectMapper;

  public EnvironmentVariableConfigurationFactory(
      ConfigurationFactory<T> baseConfigurationFactory, Class<T> klass, ObjectMapper objectMapper) {
    this.baseConfigurationFactory = baseConfigurationFactory;
    this.klass = klass;
    this.objectMapper = objectMapper;
  }

  @Override
  public T build(ConfigurationSourceProvider provider, String path)
      throws IOException, ConfigurationException {
    EnvironmentVariableSourceProvider<T> environmentVariableSourceProvider =
        new EnvironmentVariableSourceProvider<>(klass, objectMapper, provider);

    return baseConfigurationFactory.build(environmentVariableSourceProvider, path);
  }

  @Override
  public T build() throws IOException, ConfigurationException {
    // Create a empty configuration file
    return build(path -> new ByteArrayInputStream("{}".getBytes()), "config.yaml");
  }
}
