package org.sdase.commons.client.jersey.auth.oidc;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * A client that is able to fetch an OpenID Connect configuration according to
 * <a href=
 * "https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfig">OpenID
 * spec 4.1</a>
 */
@Path("/.well-known/openid-configuration")
public interface OpenIdDiscoveryApi {

   /** @return the OpenID Connect configuration */
   @GET
   @Path("")
   @Produces(APPLICATION_JSON)
   OpenIdDiscoveryResource getConfiguration();
}
