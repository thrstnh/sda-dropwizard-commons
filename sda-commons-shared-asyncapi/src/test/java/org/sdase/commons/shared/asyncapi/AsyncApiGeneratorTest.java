package org.sdase.commons.shared.asyncapi;

import static org.sdase.commons.shared.asyncapi.TestUtil.isYamlEqual;

import java.io.IOException;
import java.net.URISyntaxException;
import org.junit.Test;
import org.sdase.commons.shared.asyncapi.models.BaseEvent;

public class AsyncApiGeneratorTest {

  @Test
  public void shouldGenerateAsyncApi() throws IOException, URISyntaxException {
    String actual =
        AsyncApiGenerator.builder()
            .withAsyncApiBase(getClass().getResource("/asyncapi_template.yaml"))
            .withSchema("./schema.json", BaseEvent.class)
            .generateYaml();
    String expected = TestUtil.readResource("/asyncapi_expected.yaml");

    isYamlEqual(actual, expected);
  }
}
