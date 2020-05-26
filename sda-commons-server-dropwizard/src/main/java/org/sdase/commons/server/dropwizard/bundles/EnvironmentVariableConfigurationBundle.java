package org.sdase.commons.server.dropwizard.bundles;

import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.ConfigurationFactoryFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.sdase.commons.server.dropwizard.bundles.internal.EnvironmentVariableConfigurationFactory;

// TODO: Document this class with Javadoc!

// TODO: Mention how this works in the readme of the package

// TODO: Decide if we want to support it in the starter bundle, maybe configurable!

// TODO: Add integration tests: Arrays, empty config, with config, nested, json, ...

public class EnvironmentVariableConfigurationBundle<T extends Configuration>
    implements ConfiguredBundle<T> {

  public static <T extends Configuration>
      EnvironmentVariableConfigurationBundle.Builder<T> builder() {
    return new EnvironmentVariableConfigurationBundle.Builder<>();
  }

  @Override
  public void initialize(Bootstrap<?> bootstrap) {
    ConfigurationFactoryFactory<T> baseConfigurationFactoryFactory =
        (ConfigurationFactoryFactory<T>) bootstrap.getConfigurationFactoryFactory();

    ConfigurationFactoryFactory<T> environmentVariableConfigurationFactoryFactory =
        (klass, validator, objectMapper, propertyPrefix) -> {
          ConfigurationFactory<T> baseConfigurationFactory =
              baseConfigurationFactoryFactory.create(
                  klass, validator, objectMapper, propertyPrefix);

          return new EnvironmentVariableConfigurationFactory<>(
              baseConfigurationFactory, klass, objectMapper);
        };

    bootstrap.setConfigurationFactoryFactory(
        (ConfigurationFactoryFactory) environmentVariableConfigurationFactoryFactory);
  }

  @Override
  public void run(T configuration, Environment environment) throws Exception {}

  public static class Builder<T extends Configuration> {
    public EnvironmentVariableConfigurationBundle<T> build() {
      return new EnvironmentVariableConfigurationBundle<>();
    }
  }
}
