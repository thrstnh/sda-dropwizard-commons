package com.sdase.commons.server.kafka;

import com.github.ftrossbach.club_topicana.core.ExpectedTopicConfiguration;
import com.sdase.commons.server.kafka.builder.MessageHandlerRegistration;
import com.sdase.commons.server.kafka.builder.ProducerRegistration;
import com.sdase.commons.server.kafka.config.TopicConfig;
import com.sdase.commons.server.kafka.consumer.MessageListener;
import com.sdase.commons.server.kafka.dropwizard.AppConfiguration;
import com.sdase.commons.server.kafka.dropwizard.KafkaApplication;
import com.sdase.commons.server.kafka.exception.ConfigurationException;
import com.sdase.commons.server.kafka.producer.MessageProducer;
import com.sdase.commons.server.kafka.topicana.TopicConfigurationBuilder;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;

/**
 * checks that all public bundle methods can be called without any exception
 */
public class AppDisabledKafkaServerIT {

   @ClassRule
   public static final DropwizardAppRule<AppConfiguration> DROPWIZARD_APP_RULE = new DropwizardAppRule<>(
         KafkaApplication.class, ResourceHelpers.resourceFilePath("test-config-disabled.yml"));

   private List<String> results = Collections.synchronizedList(new ArrayList<>());

   private KafkaBundle<AppConfiguration> bundle;

   @Before
   public void before() {
      KafkaApplication app = DROPWIZARD_APP_RULE.getApplication();
      bundle = app.getKafkaBundle();
      results.clear();
   }

   @Test
   public void checkRegisterMessageHandler() throws ConfigurationException {
      List<MessageListener<String, String>> lc1 = bundle
            .registerMessageHandler(MessageHandlerRegistration
                  .<String, String>builder()
                  .withListenerConfig("lc1")
                  .forTopic("topic")
                  .checkTopicConfiguration()
                  .withDefaultConsumer()
                  .withValueDeserializer(new StringDeserializer())
                  .withHandler(record -> results.add(record.value()))
                  .build());

      assertTrue(lc1.isEmpty());
   }

   @Test
   public void checkRegisterProducerReturnsDummy() throws ConfigurationException {
      MessageProducer<Object, Object> producer = bundle.registerProducer(ProducerRegistration.builder().forTopic("Topic").createTopicIfMissing().withDefaultProducer().build());
      assertNull(producer.send("test", "test"));

   }

   @Test(expected = ConfigurationException.class)
   public void checkGetTopicConfiguration() throws ConfigurationException {
      ExpectedTopicConfiguration test = bundle.getTopicConfiguration("test");
   }





}
