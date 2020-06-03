package org.sdase.commons.shared.asyncapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjetland.jackson.jsonSchema.JsonSchemaConfig;
import com.kjetland.jackson.jsonSchema.JsonSchemaDraft;
import org.sdase.commons.server.jackson.ObjectMapperConfigurationUtil;
import org.sdase.commons.shared.yaml.YamlUtil;

/** Generator for JSON schemas from Jackson and mbknor-jackson-jsonSchema annotated Java classes. */
public class JsonSchemaGenerator {

  private JsonSchemaGenerator() {
    // No public constructor
  }

  /**
   * Creates a new generator for JSON schemas
   *
   * @return builder
   */
  public static SchemaBuilder builder() {
    return new Builder();
  }

  public interface SchemaBuilder {

    /**
     * Includes a class into the schema.
     *
     * @param clazz The class to include
     * @param <T> The type of the class.
     * @return builder
     */
    <T> FinalBuilder forClass(Class<T> clazz);
  }

  public interface FinalBuilder {

    /**
     * Generates a new JSON schema for the supplied class.
     *
     * @return A JSON object for the JSON schema.
     */
    JsonNode generate();

    /**
     * Generates a new JSON schema for the supplied class.
     *
     * @return A YAML representation for the JSON schema.
     */
    String generateYaml();
  }

  private static class Builder implements SchemaBuilder, FinalBuilder {
    Class<?> clazz;

    @Override
    public <T> FinalBuilder forClass(Class<T> clazz) {
      this.clazz = clazz;
      return this;
    }

    @Override
    public JsonNode generate() {
      ObjectMapper objectMapper = ObjectMapperConfigurationUtil.configureMapper().build();
      com.kjetland.jackson.jsonSchema.JsonSchemaGenerator jsonSchemaGenerator =
          new com.kjetland.jackson.jsonSchema.JsonSchemaGenerator(
              objectMapper,
              JsonSchemaConfig.vanillaJsonSchemaDraft4()
                  .withJsonSchemaDraft(JsonSchemaDraft.DRAFT_07));

      return jsonSchemaGenerator.generateJsonSchema(clazz);
    }

    @Override
    public String generateYaml() {
      return YamlUtil.writeValueAsString(generate());
    }
  }
}
