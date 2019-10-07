package org.sdase.commons.client.jersey.test;

import io.dropwizard.Configuration;
import org.sdase.commons.client.jersey.ClientConfiguration;

@SuppressWarnings("WeakerAccess")
public class ClientTestConfig extends Configuration {

   private ClientConfiguration client = new ClientConfiguration();

   private String consumerToken;

   private String mockBaseUrl;

   public ClientConfiguration getClient() {
      return client;
   }

   public ClientTestConfig setClient(ClientConfiguration client) {
      this.client = client;
      return this;
   }

   public String getConsumerToken() {
      return consumerToken;
   }

   public ClientTestConfig setConsumerToken(String consumerToken) {
      this.consumerToken = consumerToken;
      return this;
   }

   public String getMockBaseUrl() {
      return mockBaseUrl;
   }

   public ClientTestConfig setMockBaseUrl(String mockBaseUrl) {
      this.mockBaseUrl = mockBaseUrl;
      return this;
   }
}
