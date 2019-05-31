package org.sdase.commons.client.jersey.builder;

import io.dropwizard.client.JerseyClientBuilder;
import org.sdase.commons.client.jersey.auth.ClientAuthFilterFactory;

public class ExternalClientBuilder extends AbstractBaseClientBuilder<ExternalClientBuilder> {

   public ExternalClientBuilder(JerseyClientBuilder jerseyClientBuilder,
         ClientAuthFilterFactory clientAuthFilterFactory) {
      super(jerseyClientBuilder, clientAuthFilterFactory);
   }

}
