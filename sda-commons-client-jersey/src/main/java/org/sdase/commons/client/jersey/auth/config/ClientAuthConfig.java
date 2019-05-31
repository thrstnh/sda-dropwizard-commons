package org.sdase.commons.client.jersey.auth.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.constraints.NotNull;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({ @JsonSubTypes.Type(value = NoneAuthConfig.class, name = "NONE"),
      @JsonSubTypes.Type(value = PassThroughAuthConfig.class, name = "PASS_THROUGH"),
      @JsonSubTypes.Type(value = ClientCredentialsOidcDiscoveryConfig.class, name = "CLIENT_CREDENTIALS_OPEN_ID_DISCOVERY") })
public abstract class ClientAuthConfig {
   @NotNull
   private ClientAuthConfigType type;

   public ClientAuthConfigType getType() {
      return type;
   }

   void setType(ClientAuthConfigType type) {
      this.type = type;
   }
}
