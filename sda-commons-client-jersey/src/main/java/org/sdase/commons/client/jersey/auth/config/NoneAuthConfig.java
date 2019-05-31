package org.sdase.commons.client.jersey.auth.config;

public class NoneAuthConfig extends ClientAuthConfig {
   public NoneAuthConfig() {
      super.setType(ClientAuthConfigType.NONE);
   }
}
