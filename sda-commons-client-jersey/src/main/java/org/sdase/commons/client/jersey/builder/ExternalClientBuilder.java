package org.sdase.commons.client.jersey.builder;

import io.dropwizard.client.JerseyClientBuilder;
import org.sdase.commons.client.jersey.ClientConfiguration;

public class ExternalClientBuilder extends AbstractBaseClientBuilder<ExternalClientBuilder> {

   public ExternalClientBuilder(
         JerseyClientBuilder jerseyClientBuilder,
         ClientConfiguration clientConfiguration) {
      super(jerseyClientBuilder, clientConfiguration);
   }

}
