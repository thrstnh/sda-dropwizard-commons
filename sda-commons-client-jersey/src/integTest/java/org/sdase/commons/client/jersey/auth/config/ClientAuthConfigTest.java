package org.sdase.commons.client.jersey.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.sdase.commons.server.jackson.ObjectMapperConfigurationUtil;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class ClientAuthConfigTest {
   private ObjectMapper mapper = ObjectMapperConfigurationUtil.configureMapper().build();

   @Test
   public void shouldParseNoneConfig() throws IOException {
      // given
      String configString = "{\"type\": \"NONE\"}";

      // when
      ClientAuthConfig config = mapper.readValue(configString, ClientAuthConfig.class);

      // then
      assertThat(config).isInstanceOf(NoneAuthConfig.class);
   }

   @Test
   public void shouldParsePassThroughConfig() throws IOException {
      // given
      String configString = "{\"type\": \"PASS_THROUGH\"}";

      // when
      ClientAuthConfig config = mapper.readValue(configString, ClientAuthConfig.class);

      // then
      assertThat(config).isInstanceOf(PassThroughAuthConfig.class);
   }

   @Test
   public void shouldParseOidcDiscoveryConfig() throws IOException {
      // given
      String configString = "{\"type\": \"CLIENT_CREDENTIALS_OPEN_ID_DISCOVERY\", \"issuerUrl\": \"http://localhost/\", \"clientId\": \"client-id\", \"clientSecret\": \"client-secret\", \"leewaySeconds\": 10}";

      // when
      ClientAuthConfig config = mapper.readValue(configString, ClientAuthConfig.class);

      // then
      assertThat(config)
            .isInstanceOf(ClientCredentialsOidcDiscoveryConfig.class)
            .extracting("issuerUrl", "clientId", "clientSecret", "leewaySeconds")
            .containsOnly("http://localhost", "client-id", "client-secret", 10L);
   }
}
