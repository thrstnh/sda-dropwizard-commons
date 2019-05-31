package org.sdase.commons.client.jersey.auth;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class AccessTokenHolder {
   private String accessToken;
   private Instant expiresAt;
   private Duration requiredLeeway;

   /**
    * @param accessToken
    *           the access JWT, e.g. {@code ey….ey….sig}
    * @param expiresAt
    *           the time when the access token expires
    * @param requiredLeeway
    *           the duration we need between now and the expiration of the hold
    *           token to consider the token as valid
    */
   public AccessTokenHolder(String accessToken, Instant expiresAt, Duration requiredLeeway) {
      this.accessToken = accessToken;
      this.expiresAt = expiresAt;
      this.requiredLeeway = requiredLeeway == null ? Duration.ZERO : requiredLeeway;
   }

   public String getAccessToken() {
      return accessToken;
   }

   public boolean isValid() {
      if (accessToken == null) {
         return false;
      }
      if (expiresAt == null) {
         return false;
      }
      return Instant.now().plus(requiredLeeway.toNanos(), ChronoUnit.NANOS).isBefore(expiresAt);
   }
}
