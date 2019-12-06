package org.sdase.commons.server.kafka;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import com.github.ftrossbach.club_topicana.core.MismatchedTopicConfigException;
import com.salesforce.kafka.test.KafkaBroker;
import com.salesforce.kafka.test.junit4.SharedKafkaTestResource;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.sdase.commons.server.kafka.builder.MessageListenerRegistration;
import org.sdase.commons.server.kafka.config.ConsumerConfig;
import org.sdase.commons.server.kafka.config.TopicConfig;
import org.sdase.commons.server.kafka.consumer.ErrorHandler;
import org.sdase.commons.server.kafka.consumer.MessageHandler;
import org.sdase.commons.server.kafka.consumer.MessageListener;
import org.sdase.commons.server.kafka.consumer.strategies.deadletter.DeadLetterMLS;
import org.sdase.commons.server.kafka.consumer.strategies.deadletter.DeadLetterNoSerializationErrorDeserializer;
import org.sdase.commons.server.kafka.consumer.strategies.retryprocessingerror.ProcessingErrorRetryException;
import org.sdase.commons.server.kafka.dropwizard.KafkaTestApplication;
import org.sdase.commons.server.kafka.dropwizard.KafkaTestConfiguration;
import org.sdase.commons.server.testing.DropwizardRuleHelper;
import org.sdase.commons.server.testing.LazyRule;

@SuppressWarnings("unchecked")
public class KafkaConsumerWithDeadLetterIT extends KafkaBundleConsts {

  private static final SharedKafkaTestResource KAFKA = new SharedKafkaTestResource()
      .withBrokerProperty("auto.create.topics.enable", "false");

  private static String topic = "processingErrorsShouldBeForwardedToDeadLetterTopic";
  private static String retryTopic = "processingErrorsShouldBeForwardedToDeadLetterTopic.retry";
  private static String deadLetterTopic = "processingErrorsShouldBeForwardedToDeadLetterTopic.deadletter";

  private static final LazyRule<DropwizardAppRule<KafkaTestConfiguration>> DROPWIZARD_APP_RULE = new LazyRule<>(
      () -> DropwizardRuleHelper
          .dropwizardTestAppFrom(KafkaTestApplication.class)
          .withConfigFrom(KafkaTestConfiguration::new)
          .withRandomPorts()
          .withConfigurationModifier(c -> {
            KafkaConfiguration kafka = c.getKafka();
            kafka
                .setBrokers(KAFKA
                    .getKafkaBrokers()
                    .stream()
                    .map(KafkaBroker::getConnectString)
                    .collect(Collectors.toList()));
            kafka
                .getTopics()
                .put(retryTopic,
                    TopicConfig.builder().name(retryTopic).build());
            kafka
                .getTopics()
                .put(deadLetterTopic,
                    TopicConfig.builder().name(deadLetterTopic).build());
            kafka
                .getTopics()
                .put(topic,
                    TopicConfig.builder().name(topic).build());

          })
          .build());
  @ClassRule
  public static final TestRule CHAIN = RuleChain.outerRule(KAFKA).around(DROPWIZARD_APP_RULE);

  private KafkaBundle<KafkaTestConfiguration> bundle = ((KafkaTestApplication) DROPWIZARD_APP_RULE
      .getRule()
      .getApplication()).kafkaBundle();
  private Deserializer keyDeserializer = new StringDeserializer();
  private Deserializer valueDeserializer = new IntegerDeserializer();

  private static Object deserialize(byte[] obj) {
    try {
      ByteArrayInputStream bis = new ByteArrayInputStream(obj);
      ObjectInput in = new ObjectInputStream(bis);
      return in.readObject();
    } catch (ClassNotFoundException e) {
      return null;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  private ConsumerConfig getConsumerConfig() {
    return ConsumerConfig
        .builder()
        .withGroup("test")
        .addConfig("enable.auto.commit", "false")
        .addConfig("max.poll.records", "5")
        .build();
  }

  private KafkaProducer<String, Integer> getKafkaProducer() {
    return KAFKA.getKafkaTestUtils()
        .getKafkaProducer(StringSerializer.class, IntegerSerializer.class);
  }


  @Before
  public void setup() throws InterruptedException, ExecutionException {
    // refresh topics by first delete when existing and then recreate
    deleteTopics();

    // sleep to wait until all topics are deleted. Otherwise tests are flanky
    Thread.sleep(5000); // NOSONAR
  }

  private void deleteTopics() throws InterruptedException, ExecutionException {
    Collection<String> topicsList = new ArrayList<>();
    topicsList.add(topic);
    topicsList.add(retryTopic);
    topicsList.add(deadLetterTopic);

    try (AdminClient client = KAFKA.getKafkaTestUtils().getAdminClient()) {
      Set<String> existingTopic = client.listTopics().names().get();
      List<String> topicsToDelete = topicsList.stream().filter(existingTopic::contains)
          .collect(Collectors.toList());
      client.deleteTopics(topicsToDelete).all().get();
    }
  }

  private void createTopics() throws InterruptedException, ExecutionException {
    Collection<NewTopic> newTopicsList = new ArrayList<>();
    newTopicsList.add(new NewTopic(topic, 1, (short) 1));
    newTopicsList.add(new NewTopic(retryTopic, 1, (short) 1));
    newTopicsList.add(new NewTopic(deadLetterTopic, 1, (short) 1));

    try (AdminClient client = KAFKA.getKafkaTestUtils().getAdminClient()) {
      CreateTopicsResult result = client.createTopics(newTopicsList);
      result.all().get();
    }
  }

  @Test
  public void deadLetterShouldBeSentToDeadLetterTopic()
      throws ExecutionException, InterruptedException {
    createTopics();

    AtomicInteger processingError = new AtomicInteger(0);
    List<Integer> testResults = Collections.synchronizedList(new ArrayList<>());

    MessageHandler<String, Integer> handler = record -> {
      Integer value = record.value();
      if (value == 2 || value == 10) {
        processingError.incrementAndGet();
        throw new ProcessingErrorRetryException("processing error of record: " + record.key());
      }
      testResults.add(value);
    };

    ErrorHandler<String, Integer> errorHandler = (record, e, consumer) -> {
      throw new ProcessingErrorRetryException("seems that exception cannot be handled");
    };

    List<MessageListener> listener = bundle
        .createMessageListener(MessageListenerRegistration
            .<String, Integer>builder()
            .withDefaultListenerConfig()
            .forTopic(topic)
            .withConsumerConfig(getConsumerConfig())
            .withValueDeserializer(
                new DeadLetterNoSerializationErrorDeserializer<>(valueDeserializer))
            .withKeyDeserializer(new DeadLetterNoSerializationErrorDeserializer<>(keyDeserializer))
            .withListenerStrategy(
                new DeadLetterMLS(DROPWIZARD_APP_RULE.getRule().getEnvironment(), handler,
                    errorHandler, bundle, topic, topic, 4, 1000))
            .build());

    try (KafkaProducer<String, Integer> producer = getKafkaProducer()) {
      IntStream
          .range(1, 4)
          .forEach(
              e -> producer.send(new ProducerRecord<>(topic, UUID.randomUUID().toString(), e)));
    }
    await().atMost(20, SECONDS).until(() -> testResults.size() == 2);
    assertThat("There was at least 1 processing error", processingError.get(),
        greaterThanOrEqualTo(1));
    assertThat("There must be 18 results finally processed by consumer (except 2 and 10)",
        testResults.size(),
        equalTo(2));
    assertThat(testResults, containsInAnyOrder(1, 3));
    assertThat("There are records in the retry topic",
        KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(retryTopic).size(), equalTo(4));
    assertThat("There are two records in the retry topic",
        KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(deadLetterTopic).size(), equalTo(1));

    listener.forEach(MessageListener::stopConsumer);
  }

  @Test
  public void DeadLetterMessagesShouldContainHeaders()
      throws ExecutionException, InterruptedException {
    createTopics();
    List<Integer> testResults = Collections.synchronizedList(new ArrayList<>());

    MessageHandler<String, Integer> handler = record -> {
      // no infinite retry, since the
      if (record.value() == 1) {
        throw new ProcessingErrorRetryException("processing error of record: " + record.key());
      }
      testResults.add(record.value());
    };

    ErrorHandler<String, Integer> errorHandler = (record, e, consumer) -> {
      throw new ProcessingErrorRetryException("seems that exception cannot be handled");
    };

    List<MessageListener> listener = bundle
        .createMessageListener(MessageListenerRegistration
            .<String, Integer>builder()
            .withDefaultListenerConfig()
            .forTopic(topic)
            .withConsumerConfig(getConsumerConfig())
            .withValueDeserializer(
                new DeadLetterNoSerializationErrorDeserializer<>(valueDeserializer))
            .withKeyDeserializer(new DeadLetterNoSerializationErrorDeserializer<>(keyDeserializer))
            .withListenerStrategy(
                new DeadLetterMLS(DROPWIZARD_APP_RULE.getRule().getEnvironment(), handler,
                    errorHandler, bundle, topic, topic, 4, 3000))
            .build());

    try (KafkaProducer<String, Integer> producer = getKafkaProducer()) {
      producer.send(new ProducerRecord<>(topic, UUID.randomUUID().toString(), 1));
      producer.send(new ProducerRecord<>(topic, UUID.randomUUID().toString(), 2));
    }

    await().atMost(15, SECONDS).until(() -> testResults.size() == 1);

    Header[] headers = KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(retryTopic).get(0)
        .headers().toArray();

    assertThat("The Header of the failed messages contains Key Exception", headers[0].key(),
        equalTo("Exception"));
    assertThat("The Header of the failed messages contains Value object Exception",
        Objects.requireNonNull(deserialize(headers[0].value())).getClass().getName(), equalTo(
            "org.sdase.commons.server.kafka.consumer.strategies.retryprocessingerror.ProcessingErrorRetryException"));
    assertThat("The Header of the failed messages contains Key Retries", headers[1].key(),
        equalTo("Retries"));

    assertThat("The Header of the failed messages contains Value number of Retries",
        deserialize(headers[1].value()),
        equalTo(1));

    listener.forEach(MessageListener::stopConsumer);
  }

  @Test(expected = MismatchedTopicConfigException.class)
  public void testWithDeletedTopic() {

    List<Integer> testResults = Collections.synchronizedList(new ArrayList<>());

    MessageHandler<String, Integer> handler = record -> {
      if (record.value() == 1) {
        throw new ProcessingErrorRetryException("processing error of record: " + record.key());
      }
      testResults.add(record.value());
    };

    ErrorHandler<String, Integer> errorHandler = (record, e, consumer) -> {
      throw new ProcessingErrorRetryException("seems that exception cannot be handled");
    };

    List<MessageListener> listener = bundle
        .createMessageListener(MessageListenerRegistration
            .<String, Integer>builder()
            .withDefaultListenerConfig()
            .forTopic(topic)
            .withConsumerConfig(getConsumerConfig())
            .withValueDeserializer(
                new DeadLetterNoSerializationErrorDeserializer<>(valueDeserializer))
            .withKeyDeserializer(new DeadLetterNoSerializationErrorDeserializer<>(keyDeserializer))
            .withListenerStrategy(
                new DeadLetterMLS(DROPWIZARD_APP_RULE.getRule().getEnvironment(), handler,
                    errorHandler, bundle, topic, topic, 4, 1000))
            .build());

    try (KafkaProducer<String, Integer> producer = getKafkaProducer()) {
      producer.send(new ProducerRecord<>(topic, UUID.randomUUID().toString(), 1));
      producer.send(new ProducerRecord<>(topic, UUID.randomUUID().toString(), 2));
    }
    await().atMost(60, SECONDS).until(() -> testResults.size() == 1);

    assertThat("there is 1 entry in dead letter topic",
        KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(retryTopic).size(), equalTo(1));

    listener.forEach(MessageListener::stopConsumer);
  }


  @Test
  public void testWithDefaultErrorHandler() throws ExecutionException, InterruptedException {
    createTopics();

    AtomicInteger processingError = new AtomicInteger(0);
    List<Integer> testResults = Collections.synchronizedList(new ArrayList<>());

    MessageHandler<String, Integer> handler = record -> {
      Integer value = record.value();
      if (value == 2 || value == 10) {
        processingError.incrementAndGet();
        throw new ProcessingErrorRetryException("processing error of record: " + record.key());
      }

      testResults.add(value);
    };

    List<MessageListener> listener = bundle
        .createMessageListener(MessageListenerRegistration
            .<String, Integer>builder()
            .withDefaultListenerConfig()
            .forTopic(topic)
            .withConsumerConfig(getConsumerConfig())
            .withValueDeserializer(
                new DeadLetterNoSerializationErrorDeserializer<>(valueDeserializer))
            .withKeyDeserializer(new DeadLetterNoSerializationErrorDeserializer<>(keyDeserializer))
            .withListenerStrategy(
                new DeadLetterMLS(DROPWIZARD_APP_RULE.getRule().getEnvironment(), handler,
                    bundle, topic, topic, 4, 1000))
            .build());

    try (KafkaProducer<String, Integer> producer = getKafkaProducer()) {
      IntStream
          .range(1, 21)
          .forEach(
              e -> producer.send(new ProducerRecord<>(topic, UUID.randomUUID().toString(), e)));
    }

    await().atMost(10, SECONDS).until(() -> testResults.size() == 18);
    assertThat("There was at least 1 processing error", processingError.get(),
        greaterThanOrEqualTo(1));
    assertThat("There must be 18 results finally processed by consumer (excep 2 and 10)",
        testResults.size(),
        equalTo(18));

    assertThat(testResults,
        containsInAnyOrder(1, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20));

    assertThat("There are 8 records in the dead letter topic, i.e. four tries for 2 records",
        KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(retryTopic).size(), equalTo(8));

    listener.forEach(MessageListener::stopConsumer);

  }

  @Test
  public void testErrorHandlerReturnsFalseStopsListener()
      throws ExecutionException, InterruptedException {
    createTopics();

    MessageHandler<String, Integer> handler = record -> {
      throw new ProcessingErrorRetryException("processing error of record: " + record.key());
    };

    ErrorHandler<String, Integer> errorHandler = (record, e, consumer) -> false;

    List<MessageListener> listener = bundle
        .createMessageListener(MessageListenerRegistration
            .<String, Integer>builder()
            .withDefaultListenerConfig()
            .forTopic(topic)
            .withConsumerConfig(getConsumerConfig())
            .withValueDeserializer(
                new DeadLetterNoSerializationErrorDeserializer<>(valueDeserializer))
            .withKeyDeserializer(new DeadLetterNoSerializationErrorDeserializer<>(keyDeserializer))
            .withListenerStrategy(
                new DeadLetterMLS(DROPWIZARD_APP_RULE.getRule().getEnvironment(), handler,
                    errorHandler, bundle, topic, topic, 4, 1000))
            .build());

    try (KafkaProducer<String, Integer> producer = getKafkaProducer()) {
      producer.send(new ProducerRecord<>(topic, UUID.randomUUID().toString(), 1));
    }

    await().untilAsserted(() -> {
      boolean allClosed = listener.stream().allMatch(l -> {
        try {
          l.getConsumer().poll(1);
          // consumer throws IllegalStateException if it is already closed
        } catch (IllegalStateException e) {
          return true;
        } catch (Exception e) {
          return false;
        }
        return false;
      });
      assertThat("allClosed", allClosed);
    });
  }

  @Test
  public void errorHandlerReturnsTrueAndNoDeadLetterHandlingWillBeInitiated()
      throws ExecutionException, InterruptedException {
    createTopics();
    AtomicInteger processingError = new AtomicInteger(0);
    List<Integer> testResults = Collections.synchronizedList(new ArrayList<>());

    MessageHandler<String, Integer> handler = record -> {

      Integer value = record.value();
      if (value == 2 || value == 10) {
        processingError.incrementAndGet();
        throw new ProcessingErrorRetryException("processing error of record: " + record.key());
      }

      testResults.add(value);
    };

    ErrorHandler<String, Integer> errorHandler = (record, e, consumer) -> true;

    List<MessageListener> listener = bundle
        .createMessageListener(MessageListenerRegistration
            .<String, Integer>builder()
            .withDefaultListenerConfig()
            .forTopic(topic)
            .withConsumerConfig(getConsumerConfig())
            .withValueDeserializer(
                new DeadLetterNoSerializationErrorDeserializer<>(valueDeserializer))
            .withKeyDeserializer(new DeadLetterNoSerializationErrorDeserializer<>(keyDeserializer))
            .withListenerStrategy(
                new DeadLetterMLS(DROPWIZARD_APP_RULE.getRule().getEnvironment(), handler,
                    errorHandler, bundle, topic, topic, 4, 1000))
            .build());

    try (KafkaProducer<String, Integer> producer = getKafkaProducer()) {
      IntStream
          .range(1, 21)
          .forEach(
              e -> producer.send(new ProducerRecord<>(topic, UUID.randomUUID().toString(), e)));
    }

    await().atMost(10, SECONDS).until(() -> testResults.size() == 18);
    assertThat("There was at least 1 processing error", processingError.get(),
        greaterThanOrEqualTo(1));
    assertThat("There must be 18 results finally processed by consumer (excep 2 and 10)",
        testResults.size(),
        equalTo(18));

    assertThat(testResults,
        containsInAnyOrder(1, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20));

    assertThat(
        "There are no records in the dead letter topic (record 2 and 10 are lost in the test scenario, the error handler needs to take care of those records if he returns true",
        KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(retryTopic).size(), equalTo(0));

    listener.forEach(MessageListener::stopConsumer);
  }

}
