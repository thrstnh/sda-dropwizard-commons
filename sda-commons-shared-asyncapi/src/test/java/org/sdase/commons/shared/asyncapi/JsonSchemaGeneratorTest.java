package org.sdase.commons.shared.asyncapi;

import static org.sdase.commons.shared.asyncapi.TestUtil.isYamlEqual;

import java.io.IOException;
import java.net.URISyntaxException;
import org.junit.Test;
import org.sdase.commons.shared.asyncapi.models.BaseEvent;

public class JsonSchemaGeneratorTest {

  @Test
  public void shouldGenerateJsonSchema() throws IOException, URISyntaxException {
    String actual = JsonSchemaGenerator.builder().forClass(BaseEvent.class).generateYaml();
    String expected = TestUtil.readResource("/schema_expected.yaml");

    isYamlEqual(actual, expected);
  }
}
