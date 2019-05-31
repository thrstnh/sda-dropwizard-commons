package org.sdase.commons.client.jersey.auth;

import org.junit.Test;
import org.sdase.commons.client.jersey.auth.config.ClientAuthConfig;
import org.sdase.commons.client.jersey.auth.config.ClientCredentialsOidcDiscoveryConfig;
import org.sdase.commons.client.jersey.auth.config.NoneAuthConfig;
import org.sdase.commons.client.jersey.auth.config.PassThroughAuthConfig;
import org.sdase.commons.client.jersey.auth.filter.ClientAuthorizationHeaderFilter;
import org.sdase.commons.client.jersey.filter.AuthHeaderClientFilter;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestFilter;

import static org.assertj.core.api.Assertions.assertThat;

public class ClientAuthFilterFactoryTest {
   private ClientAuthFilterFactory factory = new ClientAuthFilterFactory(ClientBuilder.newClient());

   @Test
   public void shouldCreateUnknownAuthFilter() {
      // given
      // when
      ClientRequestFilter filter = factory.builder().withClientAuthConfig(null).withName("unknown").build();

      // then
      assertThat(filter).isNull();
   }

   @Test
   public void shouldCreateNoneAuthFilter() {
      // given
      ClientAuthConfig config = new NoneAuthConfig();

      // when
      ClientRequestFilter filter = factory.builder().withClientAuthConfig(config).withName("none").build();

      // then
      assertThat(filter).isNull();
   }

   @Test
   public void shouldCreatePassThroughFilter() {
      // given
      ClientAuthConfig config = new PassThroughAuthConfig();

      // when
      ClientRequestFilter filter = factory.builder().withClientAuthConfig(config).withName("pass-through").build();

      // then
      assertThat(filter).isInstanceOf(AuthHeaderClientFilter.class);
   }

   @Test
   public void shouldCreateClientCredentialsOidcDiscoveryFilter() {
      // given
      ClientAuthConfig config = new ClientCredentialsOidcDiscoveryConfig()
            .setIssuerUrl("http://localhost")
            .setClientId("clientid")
            .setClientSecret("clientSecret")
            .setLeewaySeconds(0);

      // when
      ClientRequestFilter filter = factory.builder().withClientAuthConfig(config).withName("oidc-discovery").build();

      // then
      assertThat(filter).isInstanceOf(ClientAuthorizationHeaderFilter.class);
   }
}
