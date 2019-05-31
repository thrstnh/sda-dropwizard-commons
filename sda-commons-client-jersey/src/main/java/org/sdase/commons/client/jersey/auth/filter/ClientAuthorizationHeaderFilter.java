package org.sdase.commons.client.jersey.auth.filter;

import java.util.Optional;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import org.sdase.commons.client.jersey.auth.AuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * A {@link ClientRequestFilter} that adds a Http header with a value from an
 * {@link AuthProvider}. If the header is already set, it will be overriden
 * </p>
 */
public class ClientAuthorizationHeaderFilter implements ClientRequestFilter {
   private static final Logger LOG = LoggerFactory.getLogger(ClientAuthorizationHeaderFilter.class);

   private AuthProvider authProvider;

   public ClientAuthorizationHeaderFilter(AuthProvider authProvider) {
      this.authProvider = authProvider;
   }

   @Override
   public void filter(ClientRequestContext requestContext) {
      Optional<String> headerValue = getHeaderValue();
      if (headerValue.isPresent()) {
         requestContext.getHeaders().add(HttpHeaders.AUTHORIZATION, headerValue.get());
      } else {
         LOG
               .error("Could not authorize request, got no auth header value from AuthProvider of type {}",
                     authProvider.getClass());
      }
   }

   private Optional<String> getHeaderValue() {
      String authHeader = authProvider.authHeaderValue();

      if (authHeader != null) {
         return Optional.of(authHeader);
      }

      LOG
            .error("Could not authorize request, got no auth header value from AuthProvider of type {}",
                  authProvider.getClass());

      return Optional.empty();
   }
}
