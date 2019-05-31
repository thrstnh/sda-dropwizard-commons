package org.sdase.commons.client.jersey.auth;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientRequestFilter;
import org.sdase.commons.client.jersey.auth.config.ClientAuthConfig;
import org.sdase.commons.client.jersey.auth.config.ClientCredentialsOidcDiscoveryConfig;
import org.sdase.commons.client.jersey.auth.config.NoneAuthConfig;
import org.sdase.commons.client.jersey.auth.config.PassThroughAuthConfig;
import org.sdase.commons.client.jersey.auth.filter.ClientAuthorizationHeaderFilter;
import org.sdase.commons.client.jersey.auth.oidc.OidcAuthProvider;
import org.sdase.commons.client.jersey.filter.AuthHeaderClientFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientAuthFilterFactory {
   private static final Logger LOG = LoggerFactory.getLogger(ClientAuthFilterFactory.class);

   private Client client;

   public ClientAuthFilterFactory(Client client) {
      this.client = client;
   }

   public ConfigurationBuilder builder() {
      return new Builder(client);
   }

   public interface ConfigurationBuilder {
      NameBuilder withClientAuthConfig(ClientAuthConfig config);
   }

   public interface NameBuilder {
      FinalBuilder withName(String name);
   }

   public interface FinalBuilder {
      ClientRequestFilter build();
   }

   public class Builder implements ConfigurationBuilder, NameBuilder, FinalBuilder {
      Client client;

      ClientAuthConfig clientAuthConfig;
      String name;

      private Builder(Client client) {
         this.client = client;
      }

      @Override
      public NameBuilder withClientAuthConfig(ClientAuthConfig config) {
         this.clientAuthConfig = config;
         return this;
      }

      @Override
      public FinalBuilder withName(String name) {
         this.name = name;
         return this;
      }

      @Override
      public ClientRequestFilter build() {
         if (clientAuthConfig instanceof NoneAuthConfig) {
            return null;
         }

         if (clientAuthConfig instanceof PassThroughAuthConfig) {
            return new AuthHeaderClientFilter();
         }

         if (clientAuthConfig instanceof ClientCredentialsOidcDiscoveryConfig) {
            return build((ClientCredentialsOidcDiscoveryConfig) clientAuthConfig, name);
         }

         LOG.warn("No valid client authentication configured");
         return null;
      }

      private ClientRequestFilter build(ClientCredentialsOidcDiscoveryConfig config, String name) {
         AuthProvider authProvider = new OidcAuthProvider(client, config, name);

         return new ClientAuthorizationHeaderFilter(authProvider);
      }
   }
}
