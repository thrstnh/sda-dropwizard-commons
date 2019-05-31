package org.sdase.commons.client.jersey.auth;

/**
 * A provider that is able to provide authentication for for a technical user
 * when calling another service.
 */
public interface AuthProvider {
   /**
    * @return the name of the header that should be added to the request, e.g.
    *         {@code Authorization}. Must not be blank
    */
   String authHeaderName();

   /**
    * @return the value for the header that should be set in the requests.
    *         Clients must get a new value for each request. Example value:
    *         {@code Bearer ey….ey….sig}
    */
   String authHeaderValue();
}
