package org.sdase.commons.client.jersey;

import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.sdase.commons.client.jersey.builder.PlatformClientBuilder;
import org.sdase.commons.client.jersey.error.ClientRequestExceptionMapper;
import org.sdase.commons.client.jersey.filter.ContainerRequestContextHolder;

import java.util.function.Function;

/**
 * A bundle that provides Jersey clients with appropriate configuration for the SDA Platform.
 */
public class JerseyClientBundle<C extends Configuration> implements ConfiguredBundle<C> {

   private ClientFactory clientFactory;

   private boolean initialized;

   private Function<C, String> consumerTokenProvider;

   private Function<C, ClientConfiguration> defaultTimeoutConfigurationProvider;

   public static InitialBuilder<Configuration> builder() {
      return new Builder<>();
   }

   private JerseyClientBundle(
         Function<C, String> consumerTokenProvider,
         Function<C, ClientConfiguration> defaultTimeoutConfigurationProvider) {
      this.consumerTokenProvider = consumerTokenProvider;
      this.defaultTimeoutConfigurationProvider = defaultTimeoutConfigurationProvider;
   }

   @Override
   public void initialize(Bootstrap<?> bootstrap) {
      // no initialization needed here, we need the environment to initialize the client
   }

   @Override
   public void run(C configuration, Environment environment) {
      String consumerToken = consumerTokenProvider.apply(configuration);
      ClientConfiguration defaultTimeouts = defaultTimeoutConfigurationProvider.apply(configuration);
      this.clientFactory = new ClientFactory(environment, consumerToken, defaultTimeouts);
      environment.jersey().register(ContainerRequestContextHolder.class);
      environment.jersey().register(ClientRequestExceptionMapper.class);
      initialized = true;
   }

   /**
    * @return a factory to build clients that can be either used to call within the SDA platform or to call external
    *       services
    * @throws IllegalStateException if called before {@link io.dropwizard.Application#run(Configuration, Environment)}
    *       because the factory has to be initialized within {@link ConfiguredBundle#run(Object, Environment)}
    */
   public ClientFactory getClientFactory() {
      if (!initialized) {
         throw new IllegalStateException("Clients can be build in run(C, Environment), not in initialize(Bootstrap)");
      }
      return clientFactory;
   }


   //
   // Builder
   //

   public interface InitialBuilder<C extends Configuration> extends TimeoutBuilder<C> {
      /**
       * @param consumerTokenProvider A provider for the header value of the Http header
       *                              {@value org.sdase.commons.shared.tracing.ConsumerTracing#TOKEN_HEADER} that will
       *                              be send with each client request configured with
       *                              {@link PlatformClientBuilder#enableConsumerToken()}. If no such provider is
       *                              configured, {@link PlatformClientBuilder#enableConsumerToken()} will fail.
       * @return a builder instance for further configuration
       */
      <C1 extends Configuration> TimeoutBuilder<C1> withConsumerTokenProvider(ConsumerTokenProvider<C1> consumerTokenProvider);
   }

   public interface TimeoutBuilder<C extends Configuration> extends FinalBuilder<C> {
      /**
       * @param defaultConfigurationProvider A provider for the default configuration of clients. These parameters are
       *                                     applied to each client created by the
       *                                     {@link JerseyClientBundle#getClientFactory() client factory}. Individual
       *                                     changes may be configured per client.
       *                                     <p>
       *                                       It is STRONGLY RECOMMENDED to support configuration of client timeouts in
       *                                       every service. At least the default timeouts should be supported if there
       *                                       is not a specific timeout configuration for each client.
       *                                     </p>
       * @return a builder instance for further configuration
       */
      <C1 extends Configuration> FinalBuilder<C1> withConfigurationProvider(DefaultConfigurationProvider<C1> defaultConfigurationProvider);
   }

   public interface FinalBuilder<C extends Configuration> {
      JerseyClientBundle<C> build();
   }

   public static class Builder<C extends Configuration> implements InitialBuilder<C>, TimeoutBuilder<C>, FinalBuilder<C> {

      private ConsumerTokenProvider<C> consumerTokenProvider = (C c) -> null;
      private DefaultConfigurationProvider<C> defaultConfigurationProvider = (C c) -> new ClientConfiguration();

      private Builder() {
      }

      private Builder(ConsumerTokenProvider<C> consumerTokenProvider, DefaultConfigurationProvider<C> defaultConfigurationProvider) {
         this.consumerTokenProvider = consumerTokenProvider;
         this.defaultConfigurationProvider = defaultConfigurationProvider;
      }

      @Override
      public <C1 extends Configuration> TimeoutBuilder<C1> withConsumerTokenProvider(ConsumerTokenProvider<C1> consumerTokenProvider) {
         //noinspection unchecked
         return (Builder<C1>) new Builder(consumerTokenProvider, this.defaultConfigurationProvider);
      }

      @Override
      public <C1 extends Configuration> TimeoutBuilder<C1> withConfigurationProvider(DefaultConfigurationProvider<C1> defaultConfigurationProvider) {
         //noinspection unchecked
         return (Builder<C1>) new Builder(this.consumerTokenProvider, defaultConfigurationProvider);
      }

      @Override
      public JerseyClientBundle<C> build() {
         return new JerseyClientBundle<>(consumerTokenProvider, defaultConfigurationProvider);
      }
   }

   /**
    * Provides the consumer token that is added to outgoing requests from the configuration.
    *
    * @param <C> the type of the applications {@link Configuration} class
    */
   public interface ConsumerTokenProvider<C extends Configuration> extends Function<C, String> {}

   /**
    * Provides the configuration of default timeouts used by clients.
    *
    * @param <C> the type of the applications {@link Configuration} class
    */
   public interface DefaultConfigurationProvider<C extends Configuration> extends Function<C, ClientConfiguration> {}
}
