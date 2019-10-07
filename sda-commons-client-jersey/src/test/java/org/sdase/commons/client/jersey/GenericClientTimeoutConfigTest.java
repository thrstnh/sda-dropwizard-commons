package org.sdase.commons.client.jersey;

import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.sdase.commons.client.jersey.test.ClientTestApp;
import org.sdase.commons.client.jersey.test.ClientTestConfig;
import org.sdase.commons.server.testing.RetryRule;

import javax.ws.rs.client.Client;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Tests that timeouts are correctly mapped.
 */
public class GenericClientTimeoutConfigTest {

   private final DropwizardAppRule<ClientTestConfig> dw = new DropwizardAppRule<>(
         ClientTestApp.class, resourceFilePath("test-config.yaml"),
         config("client.connectTimeoutMs", "5"), config("client.readTimeoutMs", "6"));

   @Rule
   public final RuleChain rule = RuleChain
         .outerRule(new RetryRule())
         .around(dw);

   private ClientTestApp app;

   @Before
   public void resetRequests() {
      app = dw.getApplication();
   }

   @Test
   public void hasConfiguredTimeouts() {
      Client client = app.getJerseyClientBundle().getClientFactory()
            .externalClient()
            .buildGenericClient("test");

      assertThat(client.getConfiguration().getProperties())
            .contains(
                  entry("jersey.config.client.connectTimeout", 5),
                  entry("jersey.config.client.readTimeout", 6)
            );
   }

}
