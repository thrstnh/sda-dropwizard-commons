package org.sdase.commons.client.jersey.auth.oidc;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.sdase.commons.client.jersey.proxy.ApiClientInvocationHandler.createProxy;

import io.prometheus.client.Counter;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.HttpHeaders;

import org.glassfish.jersey.client.proxy.WebResourceFactory;
import org.sdase.commons.client.jersey.auth.AccessTokenHolder;
import org.sdase.commons.client.jersey.auth.AuthProvider;
import org.sdase.commons.client.jersey.auth.config.ClientCredentialsOidcDiscoveryConfig;

public class OidcAuthProvider implements AuthProvider {
   private static final Counter TOKEN_REQUEST_COUNTER = Counter
         .build("oidc_auth_provider_token_request_counter",
               "Counts the number of access tokens that has been requested from the identity provider")
         .labelNames("name")
         .register();

   private final Object semaphore = new Object();

   private Client client;
   private ClientCredentialsOidcDiscoveryConfig config;
   private String name;

   private OpenIdDiscoveryApi discoveryApi;
   private Form tokenForm;

   private AccessTokenHolder accessToken;

   public OidcAuthProvider(Client client, ClientCredentialsOidcDiscoveryConfig config, String name) {
      this.client = client;
      this.config = config;
      this.name = name;

      discoveryApi = createProxy(OpenIdDiscoveryApi.class,
            WebResourceFactory.newResource(OpenIdDiscoveryApi.class, client.target(config.getIssuerUrl())));

      this.tokenForm = new Form()
            .param("grant_type", "client_credentials")
            .param("client_id", this.config.getClientId())
            .param("client_secret", this.config.getClientSecret());
   }

   @Override
   public String authHeaderName() {
      return HttpHeaders.AUTHORIZATION;
   }

   @Override
   public String authHeaderValue() {
      return "Bearer " + loadTokenIfNeeded();
   }

   private String loadTokenIfNeeded() {
      // shortcut without semaphore
      if (accessToken != null && accessToken.isValid()) {
         return accessToken.getAccessToken();
      }

      synchronized (semaphore) {
         // We need to check it again since another thread might have changed
         // the instance
         if (accessToken != null && accessToken.isValid()) {
            return accessToken.getAccessToken();
         }

         OpenIdDiscoveryResource discoveryResource = discoveryApi.getConfiguration();

         TokenResource tokenResource = client
               .target(discoveryResource.getTokenEndpoint())
               .request(APPLICATION_JSON)
               .buildPost(Entity.form(tokenForm))
               .invoke(TokenResource.class);

         TOKEN_REQUEST_COUNTER.labels(name).inc();

         this.accessToken = new AccessTokenHolder(tokenResource.getAccessToken(),
               Instant.now().plus(tokenResource.getAccessTokenExpiresInSeconds(), ChronoUnit.SECONDS),
               Duration.of(config.getLeewaySeconds(), ChronoUnit.SECONDS));

         return this.accessToken.getAccessToken();
      }
   }
}
