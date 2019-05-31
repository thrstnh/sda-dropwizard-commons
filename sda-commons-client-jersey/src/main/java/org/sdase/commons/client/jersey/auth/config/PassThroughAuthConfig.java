package org.sdase.commons.client.jersey.auth.config;

public class PassThroughAuthConfig extends ClientAuthConfig {
   public PassThroughAuthConfig() {
      super.setType(ClientAuthConfigType.PASS_THROUGH);
   }
}
