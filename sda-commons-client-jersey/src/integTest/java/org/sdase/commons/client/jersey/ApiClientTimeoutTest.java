package org.sdase.commons.client.jersey;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.sdase.commons.client.jersey.error.ClientRequestException;
import org.sdase.commons.client.jersey.test.ClientTestApp;
import org.sdase.commons.client.jersey.test.ClientTestConfig;
import org.sdase.commons.client.jersey.test.MockApiClient;
import org.sdase.commons.server.testing.EnvironmentRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.sdase.commons.client.jersey.test.util.ClientRequestExceptionConditions.connectTimeoutError;
import static org.sdase.commons.client.jersey.test.util.ClientRequestExceptionConditions.readTimeoutError;
import static org.sdase.commons.client.jersey.test.util.ClientRequestExceptionConditions.timeoutError;

/**
 * Test that timeouts are correctly mapped.
 */
public class ApiClientTimeoutTest {

   private static final Logger LOG = LoggerFactory.getLogger(ApiClientTimeoutTest.class);

   @ClassRule
   public static final WireMockClassRule WIRE = new WireMockClassRule(wireMockConfig().dynamicPort());

   private final DropwizardAppRule<ClientTestConfig> dw = new DropwizardAppRule<>(
         ClientTestApp.class, resourceFilePath("test-config.yaml"));

   @Rule
   public final RuleChain rule = RuleChain
         .outerRule(new EnvironmentRule().setEnv("MOCK_BASE_URL", WIRE.baseUrl()))
         .around(dw);

   private ClientTestApp app;

   @BeforeClass
   public static void start() {
      WIRE.start();
   }

   @Before
   public void resetRequests() {
      WIRE.resetRequests();
      app = dw.getApplication();
   }


   @Test
   public void runIntoDefaultConnectionTimeoutOf500Millis() {

      MockApiClient client = app.getJerseyClientBundle().getClientFactory()
            .externalClient()
            .api(MockApiClient.class)
            .atTarget("http://192.168.123.123");

      tryNTimes(5, () ->
            await().between(400, MILLISECONDS, 600, MILLISECONDS).pollDelay(20, MILLISECONDS)
                  .untilAsserted(() ->
                        assertThatExceptionOfType(ClientRequestException.class)
                              .isThrownBy(client::getCars)
                              .is(timeoutError()).is(connectTimeoutError())
                  )
      );
   }

   @Test
   public void runIntoConfiguredConnectionTimeout() {
      MockApiClient client = app.getJerseyClientBundle().getClientFactory()
            .externalClient()
            .withConnectionTimeout(Duration.ofMillis(10))
            .api(MockApiClient.class)
            .atTarget("http://192.168.123.123");

      tryNTimes(3, () ->
         await().between(5, MILLISECONDS, 150, MILLISECONDS).pollDelay(2, MILLISECONDS)
               .untilAsserted(() ->
                     assertThatExceptionOfType(ClientRequestException.class)
                           .isThrownBy(client::getCars)
                           .is(timeoutError()).is(connectTimeoutError())
               )
      );
   }

   @Test
   public void runIntoDefaultReadTimeoutOf2Seconds() {

      WIRE.stubFor(get("/api/cars")
            .willReturn(aResponse()
                  .withStatus(200)
                  .withBody("")
                  .withFixedDelay(3000)
            )
      );

      MockApiClient client = app.getJerseyClientBundle().getClientFactory()
            .externalClient()
            .api(MockApiClient.class)
            .atTarget(WIRE.baseUrl());

      tryNTimes(5, () ->
            await().between(1900, MILLISECONDS, 2200, MILLISECONDS).pollDelay(20, MILLISECONDS)
            .untilAsserted(() ->
                  assertThatExceptionOfType(ClientRequestException.class)
                        .isThrownBy(client::getCars)
                        .is(timeoutError()).is(readTimeoutError())
            )
      );

   }

   @Test
   public void runIntoConfiguredReadTimeoutOf100Millis() {

      WIRE.stubFor(get("/api/cars")
            .willReturn(aResponse()
                  .withStatus(200)
                  .withBody("")
                  .withFixedDelay(300)
            )
      );

      MockApiClient client = app.getJerseyClientBundle().getClientFactory()
            .externalClient()
            .withReadTimeout(Duration.ofMillis(100))
            .api(MockApiClient.class)
            .atTarget(WIRE.baseUrl());

      // the proxy seems to handle timeouts slower than the generic client
      tryNTimes(5, () ->
         await().between(40, MILLISECONDS, 900, MILLISECONDS).pollDelay(2, MILLISECONDS)
               .untilAsserted(() ->
                     assertThatExceptionOfType(ClientRequestException.class)
                           .isThrownBy(client::getCars)
                           .is(timeoutError()).is(readTimeoutError())
               )
      );

   }

   private void tryNTimes(int n, Runnable r) {
      int retries = n + 1;
      for (int i = 0; i < retries + 1; i++) {
         try {
            r.run();
            break;
         } catch (Throwable t) { // NOSONAR
            if (i < retries) {
               LOG.warn("Attempt {} failed.", i + 1, t);
            } else {
               LOG.error("Finally failed after {} attempts.", i + 1);
               throw t;
            }
         }
      }
   }
}
