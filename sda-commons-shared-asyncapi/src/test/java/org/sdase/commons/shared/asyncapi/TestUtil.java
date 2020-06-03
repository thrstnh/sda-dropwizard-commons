package org.sdase.commons.shared.asyncapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import org.sdase.commons.shared.yaml.YamlUtil;

public class TestUtil {

  private TestUtil() {
    // No public constructor
  }

  static String readResource(String filename) throws URISyntaxException, IOException {
    return new String(Files.readAllBytes(Paths.get(TestUtil.class.getResource(filename).toURI())));
  }

  static void isYamlEqual(String actual, String expected) {
    Map<String, Object> expectedJson =
        YamlUtil.load(actual, new TypeReference<Map<String, Object>>() {});
    Map<String, Object> actualJson =
        YamlUtil.load(expected, new TypeReference<Map<String, Object>>() {});

    assertThat(expectedJson).isEqualToComparingFieldByFieldRecursively(actualJson);
  }
}
