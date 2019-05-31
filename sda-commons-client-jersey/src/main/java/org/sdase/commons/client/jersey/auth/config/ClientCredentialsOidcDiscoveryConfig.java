package org.sdase.commons.client.jersey.auth.config;

import javax.validation.constraints.NotNull;

public class ClientCredentialsOidcDiscoveryConfig extends ClientAuthConfig {
   @NotNull
   private String issuerUrl;
   @NotNull
   private String clientId;
   @NotNull
   private String clientSecret;
   private long leewaySeconds;

   public ClientCredentialsOidcDiscoveryConfig() { // NOSONAR
      super.setType(ClientAuthConfigType.CLIENT_CREDENTIALS_OPEN_ID_DISCOVERY);
   }

   public String getIssuerUrl() {
      return issuerUrl;
   }

   public ClientCredentialsOidcDiscoveryConfig setIssuerUrl(String issuerUrl) {
      // Remove a trailing '/' that might be present
      this.issuerUrl = issuerUrl.replaceAll("/$", "");
      return this;
   }

   public String getClientId() {
      return clientId;
   }

   public ClientCredentialsOidcDiscoveryConfig setClientId(String clientId) {
      this.clientId = clientId;
      return this;
   }

   public String getClientSecret() {
      return clientSecret;
   }

   public ClientCredentialsOidcDiscoveryConfig setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
      return this;
   }

   public long getLeewaySeconds() {
      return leewaySeconds;
   }

   public ClientCredentialsOidcDiscoveryConfig setLeewaySeconds(long leewaySeconds) {
      this.leewaySeconds = leewaySeconds;
      return this;
   }
}
