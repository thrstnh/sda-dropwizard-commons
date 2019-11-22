package org.sdase.commons.server.morphia.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.morphia.Datastore;
import org.bson.Document;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.sdase.commons.server.mongo.testing.MongoDbRule;
import org.sdase.commons.server.morphia.example.mongo.CarManager;
import org.sdase.commons.server.morphia.example.mongo.model.Car;
import org.sdase.commons.server.testing.DropwizardConfigurationHelper;
import org.sdase.commons.server.testing.LazyRule;
import org.sdase.commons.server.weld.testing.WeldAppRule;

import java.util.concurrent.TimeUnit;

public class MorphiaApplicationIT {

   private static final MongoDbRule MONGODB = MongoDbRule.builder().build(); // start a flapdoodle mongodb instance for this test. A random port is used.

   private static final LazyRule<WeldAppRule> LAZY_RULE = new LazyRule<>(() -> // use lazy rule to initialize application so that mongo connection parameters are available
         new WeldAppRule<>(MorphiaApplication.class, //normal WELD rule initialization
               DropwizardConfigurationHelper.configFrom(MorphiaApplicationConfiguration::new) // set mongo parameters dynamically
                     .withRandomPorts()
                     .withConfigurationModifier(c -> c.getMongo()
                           .setHosts(MONGODB.getHost())
                           .setDatabase(MongoDbRule.Builder.DEFAULT_DATABASE)
                     ).build()
         ));

   @ClassRule
   public static final RuleChain CHAIN = RuleChain.outerRule(MONGODB).around(LAZY_RULE); // initialize the test environment

   private static final Car HH = new Car().setColor("green").setModel("BMW").setSign("HH-AA 123");
   private static final Car WL = new Car().setColor("purple").setModel("VW").setSign("WL-ZZ 9876");

   private CarManager carManager;
   private Datastore datastore;

   @Before
   public void before() {
      MorphiaApplication app = (MorphiaApplication) LAZY_RULE.getRule().getApplication();
      carManager = app.carManager();
      datastore = app.morphiaDatastore();
      datastore.delete(datastore.createQuery(Car.class));
   }

   @Test
   public void shouldStoreCarEntity() {
      addData();
      assertThat(datastore.createQuery(Car.class).count()).isEqualTo(2);
      assertThat(datastore.createQuery(Car.class).find()).usingFieldByFieldElementComparator().contains(WL, HH);
   }

   @Test
   public void shouldReadHHEntitiesOnly() {
      addData();
      assertThat(carManager.hamburgCars()).usingFieldByFieldElementComparator().containsExactly(HH);
   }

   @Test
   public void shouldHaveIndexOnSign() {
      Iterable<Document> indexInfo = datastore.getDatabase().getCollection("cars").listIndexes();
      assertThat(indexInfo).extracting(dbo -> dbo.get("name")).containsExactlyInAnyOrder("_id_", "sign_1");

   }

   @Test
   public void shouldRegisterSizeMetrics() {
      await().atMost(10L, TimeUnit.SECONDS).untilAsserted(() -> {
         String metricsResult = LAZY_RULE.getRule().client().target("http://localhost:" + LAZY_RULE.getRule().getAdminPort())
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
         String metricsResult = LAZY_RULE.getRule().client().target("http://localhost:" + LAZY_RULE.getRule().getAdminPort())
               .path("/metrics/prometheus")
               .request("text/plain")
               .get(String.class);
         assertThat(metricsResult)
               .contains("mongodb_data_filesystem_used_bytes")
         ;
      });
   }


   private void addData() {
      carManager.store(HH);
      carManager.store(WL);
   }



}
