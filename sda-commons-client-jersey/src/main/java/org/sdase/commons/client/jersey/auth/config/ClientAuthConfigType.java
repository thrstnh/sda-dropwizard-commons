package org.sdase.commons.client.jersey.auth.config;

public enum ClientAuthConfigType {
   /** A noop config that does nothing */
   NONE,

   /** The Authorization header of the request it passed through */
   PASS_THROUGH,

   /**
    * A Bearer token is received with the client_credentials grant from an OIDC
    * Discovery server {@link ClientCredentialsOidcDiscoveryConfig}.
    */
   CLIENT_CREDENTIALS_OPEN_ID_DISCOVERY
}
