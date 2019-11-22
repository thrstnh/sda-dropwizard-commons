package org.sdase.commons.server.morphia;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.bson.Document;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.sdase.commons.server.mongo.testing.MongoDbRule;
import org.sdase.commons.server.morphia.test.Config;
import org.sdase.commons.server.prometheus.PrometheusBundle;
import org.sdase.commons.server.testing.DropwizardRuleHelper;
import org.sdase.commons.server.testing.LazyRule;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests if database health check is registered and works
 */
public class MorphiaBundleMetricsIT {

   private static final MongoDbRule MONGODB = MongoDbRule.builder().build();

   private static final LazyRule<DropwizardAppRule<Config>> DW =
         new LazyRule<>(() ->
               DropwizardRuleHelper.dropwizardTestAppFrom(MorphiaTestApp.class)
                     .withConfigFrom(Config::new)
                     .withRandomPorts()
                     .withConfigurationModifier(c -> c.getMongo()
                           .setHosts(MONGODB.getHost())
                           .setDatabase("testMetrics"))
                     .build());

   @ClassRule
   public static final RuleChain CHAIN = RuleChain.outerRule(MONGODB).around(DW);



   @BeforeClass
   public static void addSomeDataToDb() {
      // We need to add some data.
      // This may be required to create the database.
      // Otherwise file system stats read from the DB are zero.
      MorphiaTestApp application = DW.getRule().getApplication();
      application.getMorphiaBundle()
            .datastore()
            .getDatabase()
            .getCollection("dummy")
            .insertOne(new Document("foo", "bar"));
   }

   @Test
   public void shouldRegisterSizeMetrics() {
      await().atMost(10L, TimeUnit.SECONDS).untilAsserted(() -> {
            String metricsResult = DW.getRule().client().target("http://localhost:" + DW.getRule().getAdminPort())
                  .path("/metrics/prometheus")
                  .request("text/plain")
                  .get(String.class);
            assertThat(metricsResult)
                  .contains("mongodb_data_filesystem_size_bytes")
            ;
      });
   }

   @Test
   public void shouldRegisterUsedMetrics() {
      await().atMost(10L, TimeUnit.SECONDS).untilAsserted(() -> {
            String metricsResult = DW.getRule().client().target("http://localhost:" + DW.getRule().getAdminPort())
                  .path("/metrics/prometheus")
                  .request("text/plain")
                  .get(String.class);
            assertThat(metricsResult)
                  .contains("mongodb_data_filesystem_used_bytes")
            ;
      });
   }

   @Test
   public void shouldReadSizeMetricsFromDatabase() {
      await().atMost(70L, TimeUnit.SECONDS).untilAsserted(() -> {
            String metricsResult = DW.getRule().client().target("http://localhost:" + DW.getRule().getAdminPort())
                  .path("/metrics/prometheus")
                  .request("text/plain")
                  .get(String.class);
            assertThat(metricsResult)
                  .contains("mongodb_data_filesystem_size_bytes")
                  .doesNotContain("mongodb_data_filesystem_size_bytes 0")
                  .doesNotContain("mongodb_data_filesystem_size_bytes 0.0")
            ;
      });
   }

   @Test
   public void shouldReadUsedMetricsFromDatabase() {
      await().atMost(70L, TimeUnit.SECONDS).untilAsserted(() -> {
            String metricsResult = DW.getRule().client().target("http://localhost:" + DW.getRule().getAdminPort())
                  .path("/metrics/prometheus")
                  .request("text/plain")
                  .get(String.class);
            assertThat(metricsResult)
                  .contains("mongodb_data_filesystem_used_bytes")
                  .doesNotContain("mongodb_data_filesystem_size_bytes 0")
                  .doesNotContain("mongodb_data_filesystem_used_bytes 0.0")
            ;
      });
   }


   public static class MorphiaTestApp extends Application<Config> {

      private MorphiaBundle<Config> morphiaBundle = MorphiaBundle.builder()
            .withConfigurationProvider(Config::getMongo)
            .withEntityScanPackage("java.lang")
            .build();

      @Override
      public void initialize(Bootstrap<Config> bootstrap) {
         bootstrap.addBundle(morphiaBundle);
         bootstrap.addBundle(PrometheusBundle.builder().build());
      }

      @Override
      public void run(Config configuration, Environment environment) {
         // nothing to run
      }

      public MorphiaBundle<Config> getMorphiaBundle() {
         return morphiaBundle;
      }
   }

}
