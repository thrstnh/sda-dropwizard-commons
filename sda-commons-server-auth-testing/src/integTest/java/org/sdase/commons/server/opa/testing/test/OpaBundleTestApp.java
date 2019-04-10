package org.sdase.commons.server.opa.testing.test;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.sdase.commons.server.dropwizard.bundles.ConfigurationSubstitutionBundle;
import org.sdase.commons.server.opa.OpaBundle;
import org.sdase.commons.server.swagger.SwaggerBundle;

public class OpaBundleTestApp extends Application<OpaBundeTestAppConfiguration> {

  @Override
  public void initialize(Bootstrap<OpaBundeTestAppConfiguration> bootstrap) {
    bootstrap.addBundle(ConfigurationSubstitutionBundle.builder().build());
    bootstrap.addBundle(SwaggerBundle.builder().withTitle("Test").addResourcePackageClass(OpaBundleTestApp.class).build());
    bootstrap.addBundle(OpaBundle.builder().withOpaConfigProvider(OpaBundeTestAppConfiguration::getOpa).build());
  }

  @Override
  public void run(OpaBundeTestAppConfiguration configuration, Environment environment) {
      environment.jersey().register(Endpoint.class);
  }

}
