package org.sdase.commons.server.kafka;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.*;
import org.junit.After;
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

import com.github.ftrossbach.club_topicana.core.MismatchedTopicConfigException;
import com.salesforce.kafka.test.KafkaBroker;
import com.salesforce.kafka.test.junit4.SharedKafkaTestResource;

import io.dropwizard.testing.junit.DropwizardAppRule;

public class KafkaConsumerWithDeadLetterIT extends KafkaBundleConsts {

	private static final SharedKafkaTestResource KAFKA = new SharedKafkaTestResource()
			.withBrokerProperty("auto.create.topics.enable", "false");

	static String topic = "processinErrorsShouldBeForwardedtoDeadLetterTopic";
	static String retryTopic = "processinErrorsShouldBeForwardedtoDeadLetterTopic.retry";
	static String deadLetterTopic = "processinErrorsShouldBeForwardedtoDeadLetterTopic.deadletter";

	private static final LazyRule<DropwizardAppRule<KafkaTestConfiguration>> DROPWIZARD_APP_RULE = new LazyRule<DropwizardAppRule<KafkaTestConfiguration>>(
			() -> {
				return DropwizardRuleHelper.dropwizardTestAppFrom(KafkaTestApplication.class)
						.withConfigFrom(KafkaTestConfiguration::new).withRandomPorts().withConfigurationModifier(c -> {
							KafkaConfiguration kafka = c.getKafka();
							kafka.setBrokers(KAFKA.getKafkaBrokers().stream().map(KafkaBroker::getConnectString)
									.collect(Collectors.toList()));
							kafka.getTopics().put(topic + retryTopic, TopicConfig.builder().name(retryTopic)
									.withPartitions(1).withReplicationFactor(1).build());
							kafka.getTopics().put(topic + deadLetterTopic, TopicConfig.builder().name(deadLetterTopic)
									.withPartitions(1).withReplicationFactor(1).build());
							kafka.getTopics().put(topic, TopicConfig.builder().name(topic)
									.withPartitions(1).withReplicationFactor(1).build());

						}).build();
			});

	@ClassRule
	public static final TestRule CHAIN = RuleChain.outerRule(KAFKA).around(DROPWIZARD_APP_RULE);

	private StringDeserializer deserializer = new StringDeserializer();

	private int numberExceptionThrown = 0;
	private List<String> results = Collections.synchronizedList(new ArrayList<>());

	private KafkaBundle<KafkaTestConfiguration> bundle = ((KafkaTestApplication) DROPWIZARD_APP_RULE.getRule()
			.getApplication()).kafkaBundle();

	@Before
	public void setup() throws InterruptedException {
		results.clear();
		// sleep needed, otherwise execution of multiple tests fail. Seems like in
		// between some time
		// for cleanup is needed.
		Thread.sleep(1000);
	}

	@After
	public void after() {
		Collection<String> topicsList = new ArrayList<String>();
		topicsList.add(topic);
		topicsList.add(retryTopic);
		topicsList.add(deadLetterTopic);

		KAFKA.getKafkaTestUtils().getAdminClient().deleteTopics(topicsList);
	}

	@Test
	public void DeadLetterShouldBeSentToDeadLetterTopic() {

		KAFKA.getKafkaTestUtils().createTopic(topic, 1, (short) 1);
		KAFKA.getKafkaTestUtils().createTopic(retryTopic, 1, (short) 1);
		KAFKA.getKafkaTestUtils().createTopic(deadLetterTopic, 1, (short) 1);

		AtomicInteger processingError = new AtomicInteger(0);
		List<Integer> testResults = Collections.synchronizedList(new ArrayList<Integer>());

		MessageHandler<String, Integer> handler = new MessageHandler<String, Integer>() {
			@Override
			public void handle(ConsumerRecord<String, Integer> record) {

				Integer value = record.value();
				if (value == 2 || value == 10) {
					processingError.incrementAndGet();
					throw new ProcessingErrorRetryException("processing error of record: " + record.key());
				}

				testResults.add(value);
			}
		};

		ErrorHandler<String, Integer> errorHandler = new ErrorHandler<String, Integer>() {
			@Override
			public boolean handleError(ConsumerRecord<String, Integer> record, RuntimeException e,
					Consumer<String, Integer> consumer) {
				throw new ProcessingErrorRetryException("seems that exception cannot be handled");
			}
		};


		Deserializer keyDeserializer = new StringDeserializer();
		Deserializer valueDeserializer = new IntegerDeserializer();

		List<MessageListener> listener = bundle.createMessageListener(
				MessageListenerRegistration.<String, Integer>builder().withDefaultListenerConfig().forTopic(topic)
						.withConsumerConfig(ConsumerConfig.<String, Integer>builder().withGroup("test")
								.addConfig("enable.auto.commit", "false").addConfig("max.poll.records", "5").build())
						.withValueDeserializer(new DeadLetterNoSerializationErrorDeserializer<>(valueDeserializer))
						.withKeyDeserializer(new DeadLetterNoSerializationErrorDeserializer<>(keyDeserializer))
						.withListenerStrategy(new DeadLetterMLS(DROPWIZARD_APP_RULE.getRule().getEnvironment(), handler, bundle, topic, topic, 4, 1000, errorHandler)).build());


		KafkaProducer<String, Integer> producer = KAFKA.getKafkaTestUtils().getKafkaProducer(StringSerializer.class,
				IntegerSerializer.class);
		IntStream.range(1, 4).forEach(
				e -> producer.send(new ProducerRecord<String, Integer>(topic, UUID.randomUUID().toString(), e)));
		System.out.println("test");
		await().atMost(20, SECONDS).until(() -> testResults.size() == 2);
		assertThat("There was at least 1 processing error", processingError.get(), greaterThanOrEqualTo(1));
		assertThat("There must be 18 results finally processed by consumer (except 2 and 10)", testResults.size(),
				equalTo(2));

		assertThat(testResults, containsInAnyOrder(1, 3));

		KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(topic);
		assertThat("There are two records in the dead letter topic",
				KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(retryTopic).size(), equalTo(4));

		listener.forEach(l -> {
			l.stopConsumer();
		});

	}
	/*
	@Test
	public void DeadLetterMessagesShouldContainHeaders() {
		KAFKA.getKafkaTestUtils().createTopic(topic, 1, (short) 1);
		KAFKA.getKafkaTestUtils().createTopic(retryTopic, 1, (short) 1);
		KAFKA.getKafkaTestUtils().createTopic(deadLetterTopic, 1, (short) 1);

		List<Integer> testResults = Collections.synchronizedList(new ArrayList<Integer>());

		MessageHandler<String, Integer> handler = new MessageHandler<String, Integer>() {
			@Override
			public void handle(ConsumerRecord<String, Integer> record) {
				if (record.value() == 1) {
					throw new ProcessingErrorRetryException("processing error of record: " + record.key());
				}
				testResults.add(record.value());
			}
		};

		ErrorHandler<String, Integer> errorHandler = new ErrorHandler<String, Integer>() {
			@Override
			public boolean handleError(ConsumerRecord<String, Integer> record, RuntimeException e,
					Consumer<String, Integer> consumer) {
				throw new ProcessingErrorRetryException("seems that exception cannot be handled");
			}
		};

		List<MessageListener> listener = bundle.createMessageListener(
				MessageListenerRegistration.<String, Integer>builder().withDefaultListenerConfig().forTopic(topic)
						.withConsumerConfig(ConsumerConfig.<String, Integer>builder().withGroup("test")
								.addConfig("enable.auto.commit", "false").addConfig("max.poll.records", "5").build())
						.withValueDeserializer(new IntegerDeserializer())
						//.withListenerStrategy(new DeadLetterMLS(handler, bundle, topic, 5, errorHandler)).build());
						.withListenerStrategy(new DeadLetterMLS(DROPWIZARD_APP_RULE.getRule().getEnvironment(), handler, bundle, topic, topic, 5, 1000, errorHandler)).build());

		KafkaProducer<String, Integer> producer = KAFKA.getKafkaTestUtils().getKafkaProducer(StringSerializer.class,
				IntegerSerializer.class);
		producer.send(new ProducerRecord<String, Integer>(topic, UUID.randomUUID().toString(), 1));
		producer.send(new ProducerRecord<String, Integer>(topic, UUID.randomUUID().toString(), 2));

		await().atMost(10, SECONDS).until(() -> testResults.size() == 1);

		Header[] consumeAllRecordsFromTopic = KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(retryTopic).get(0)
				.headers().toArray();
		KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(topic);

		assertThat("The Header of the Failes messages contains Key Exception", consumeAllRecordsFromTopic[0].key(),
				equalTo("Exception"));
		assertThat("The Header of the Failes messages contains Value object Exception",
				deserialize(consumeAllRecordsFromTopic[0].value()).getClass().getName(),
				equalTo("org.sdase.commons.server.kafka.consumer.strategies.retryprocessingerror.ProcessingErrorRetryException"));
		assertThat("The Header of the Failes messages contains Key Retries", consumeAllRecordsFromTopic[1].key(),
				equalTo("Retries"));

		assertThat("The Header of the Failes messages contains Value number of Retries",
				deserialize(consumeAllRecordsFromTopic[1].value()), equalTo(1));

		listener.forEach(l -> {
			l.stopConsumer();
		});
	}

	@Test
	public void AfterFiveRetriesTheMessageWillBeInADeadTopic() {
		KAFKA.getKafkaTestUtils().createTopic(topic, 1, (short) 1);
		KAFKA.getKafkaTestUtils().createTopic(retryTopic, 1, (short) 1);
		KAFKA.getKafkaTestUtils().createTopic(deadLetterTopic, 1, (short) 1);

		List<Integer> testResults = Collections.synchronizedList(new ArrayList<Integer>());

		MessageHandler<String, Integer> handler = new MessageHandler<String, Integer>() {
			@Override
			public void handle(ConsumerRecord<String, Integer> record) {
				if (record.value() == 1) {
					throw new ProcessingErrorRetryException("processing error of record: " + record.key());
				}
				testResults.add(record.value());
			}
		};

		ErrorHandler<String, Integer> errorHandler = new ErrorHandler<String, Integer>() {
			@Override
			public boolean handleError(ConsumerRecord<String, Integer> record, RuntimeException e,
					Consumer<String, Integer> consumer) {
				throw new ProcessingErrorRetryException("seems that exception cannot be handled");
			}
		};

		List<MessageListener> listener = bundle.createMessageListener(
				MessageListenerRegistration.<String, Integer>builder().withDefaultListenerConfig().forTopic(topic)
						.withConsumerConfig(ConsumerConfig.<String, Integer>builder().withGroup("test")
								.addConfig("enable.auto.commit", "false").addConfig("max.poll.records", "5").build())
						.withValueDeserializer(new IntegerDeserializer())
						//.withListenerStrategy(new DeadLetterMLS(handler, bundle, topic, 2, errorHandler)).build());
						.withListenerStrategy(new DeadLetterMLS(DROPWIZARD_APP_RULE.getRule().getEnvironment(), handler, bundle, "test", "test", 5, 1000, errorHandler)).build());

		KafkaProducer<String, Integer> producer = KAFKA.getKafkaTestUtils().getKafkaProducer(StringSerializer.class,
				IntegerSerializer.class);

		// first round
		producer.send(new ProducerRecord<String, Integer>(topic, UUID.randomUUID().toString(), 1));
		producer.send(new ProducerRecord<String, Integer>(topic, UUID.randomUUID().toString(), 2));
		await().atMost(60, SECONDS).until(() -> testResults.size() == 1);
		assertThat("there is 1 entry in dead letter topic",
				KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(retryTopic).size(), equalTo(1));
		assertThat("there is 1 entry in normal topic", testResults.size(), equalTo(1));
		assertThat("Dead topic is empty at the beginning",
				KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(deadLetterTopic).size(), equalTo(0));

		// second round
		ConsumerRecord<byte[], byte[]> consumerRecord = KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(retryTopic)
				.get(0);
		producer.send(new ProducerRecord<String, Integer>(topic, null, (String) deserialize(consumerRecord.key()),
				(int) deserialize(consumerRecord.value()), consumerRecord.headers()));
		producer.send(new ProducerRecord<String, Integer>(topic, UUID.randomUUID().toString(), 2));
		await().atMost(60, SECONDS).until(() -> testResults.size() == 2);
		assertThat("there is 2 entries in dead letter topic",
				KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(retryTopic).size(), equalTo(2));
		assertThat("there is 2 entries in normal topic", testResults.size(), equalTo(2));
		assertThat("Dead topic is empty at the beginning",
				KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(deadLetterTopic).size(), equalTo(0));

		// Third round
		consumerRecord = KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(retryTopic).get(1);
		producer.send(new ProducerRecord<String, Integer>(topic, null, (String) deserialize(consumerRecord.key()),
				(int) deserialize(consumerRecord.value()), consumerRecord.headers()));
		producer.send(new ProducerRecord<String, Integer>(topic, UUID.randomUUID().toString(), 2));
		await().atMost(60, SECONDS).until(() -> testResults.size() == 3);

		assertThat("there are still 2 entries in retry topic",
				KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(retryTopic).size(), equalTo(2));
		assertThat("there is 3 entries in normal topic", testResults.size(), equalTo(3));
		assertThat("The dead letter topic contains 1 message",
				KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(deadLetterTopic).size(), equalTo(1));

		listener.forEach(l -> {
			l.stopConsumer();
		});
	}

	@Test(expected = MismatchedTopicConfigException.class)
	public void testwithDeletedTopic() {

		List<Integer> testResults = Collections.synchronizedList(new ArrayList<Integer>());

		MessageHandler<String, Integer> handler = new MessageHandler<String, Integer>() {
			@Override
			public void handle(ConsumerRecord<String, Integer> record) {
				if (record.value() == 1) {
					throw new ProcessingErrorRetryException("processing error of record: " + record.key());
				}
				testResults.add(record.value());
			}
		};

		ErrorHandler<String, Integer> errorHandler = new ErrorHandler<String, Integer>() {
			@Override
			public boolean handleError(ConsumerRecord<String, Integer> record, RuntimeException e,
					Consumer<String, Integer> consumer) {
				throw new ProcessingErrorRetryException("seems that exception cannot be handled");
			}
		};

		List<MessageListener> listener = bundle.createMessageListener(
				MessageListenerRegistration.<String, Integer>builder().withDefaultListenerConfig().forTopic(topic)
						.withConsumerConfig(ConsumerConfig.<String, Integer>builder().withGroup("test")
								.addConfig("enable.auto.commit", "false").addConfig("max.poll.records", "5").build())
						.withValueDeserializer(new IntegerDeserializer())
						//.withListenerStrategy(new DeadLetterMLS(handler, bundle, topic, 5, errorHandler)).build());
						.withListenerStrategy(new DeadLetterMLS(DROPWIZARD_APP_RULE.getRule().getEnvironment(), handler, bundle, "test", "test", 5, 1000, errorHandler)).build());

		KafkaProducer<String, Integer> producer = KAFKA.getKafkaTestUtils().getKafkaProducer(StringSerializer.class,
				IntegerSerializer.class);

		producer.send(new ProducerRecord<String, Integer>(topic, UUID.randomUUID().toString(), 1));
		producer.send(new ProducerRecord<String, Integer>(topic, UUID.randomUUID().toString(), 2));
		await().atMost(60, SECONDS).until(() -> testResults.size() == 1);

		assertThat("there is 1 entry in dead letter topic",
				KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(retryTopic).size(), equalTo(1));

		listener.forEach(l -> {
			l.stopConsumer();
		});
	}

	@Test(expected = IllegalStateException.class)
	public void testErrorHandlerReturnsFalseStopsListemer() {
		KAFKA.getKafkaTestUtils().createTopic(topic, 1, (short) 1);
		KAFKA.getKafkaTestUtils().createTopic(retryTopic, 1, (short) 1);
		KAFKA.getKafkaTestUtils().createTopic(deadLetterTopic, 1, (short) 1);

		MessageHandler<String, Integer> handler = new MessageHandler<String, Integer>() {
			@Override
			public void handle(ConsumerRecord<String, Integer> record) {
				throw new ProcessingErrorRetryException("processing error of record: " + record.key());
			}
		};

		ErrorHandler<String, Integer> errorHandler = new ErrorHandler<String, Integer>() {
			@Override
			public boolean handleError(ConsumerRecord<String, Integer> record, RuntimeException e,
					Consumer<String, Integer> consumer) {
				return false;
			}
		};

		List<MessageListener> listener = bundle.createMessageListener(
				MessageListenerRegistration.<String, Integer>builder().withDefaultListenerConfig().forTopic(topic)
						.withConsumerConfig(ConsumerConfig.<String, Integer>builder().withGroup("test")
								.addConfig("enable.auto.commit", "false").addConfig("max.poll.records", "5").build())
						.withValueDeserializer(new IntegerDeserializer())
						//.withListenerStrategy(new DeadLetterMLS(handler, bundle, topic, 5, errorHandler)).build());
						.withListenerStrategy(new DeadLetterMLS(DROPWIZARD_APP_RULE.getRule().getEnvironment(), handler, bundle, "test", "test", 5, 1000, errorHandler)).build());

		KafkaProducer<String, Integer> producer = KAFKA.getKafkaTestUtils().getKafkaProducer(StringSerializer.class,
				IntegerSerializer.class);
		producer.send(new ProducerRecord<String, Integer>(topic, UUID.randomUUID().toString(), 1));

		// wait some time before trying to access consumer again since otherwise
		// problems for accessing the consumer from multiple threads at the same time
		// occur
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// consumer should be closed already, since the error handler returned false
		listener.forEach(l -> {
			l.getConsumer().poll(1000);
		});
	}

	@Test
	public void ErrorHandlerReturnsTrueAndNoDeadLetterHandlingWillBeInitiated() {

		KAFKA.getKafkaTestUtils().createTopic(topic, 1, (short) 1);
		KAFKA.getKafkaTestUtils().createTopic(retryTopic, 1, (short) 1);
		KAFKA.getKafkaTestUtils().createTopic(deadLetterTopic, 1, (short) 1);

		AtomicInteger processingError = new AtomicInteger(0);
		List<Integer> testResults = Collections.synchronizedList(new ArrayList<Integer>());

		MessageHandler<String, Integer> handler = new MessageHandler<String, Integer>() {
			@Override
			public void handle(ConsumerRecord<String, Integer> record) {

				Integer value = record.value();
				if (value == 2 || value == 10) {
					processingError.incrementAndGet();
					throw new ProcessingErrorRetryException("processing error of record: " + record.key());
				}

				testResults.add(value);
			}
		};

		ErrorHandler<String, Integer> errorHandler = new ErrorHandler<String, Integer>() {
			@Override
			public boolean handleError(ConsumerRecord<String, Integer> record, RuntimeException e,
					Consumer<String, Integer> consumer) {
				return true;
			}
		};

		List<MessageListener> listener = bundle.createMessageListener(
				MessageListenerRegistration.<String, Integer>builder().withDefaultListenerConfig().forTopic(topic)
						.withConsumerConfig(ConsumerConfig.<String, Integer>builder().withGroup("test")
								.addConfig("enable.auto.commit", "false").addConfig("max.poll.records", "5").build())
						.withValueDeserializer(new IntegerDeserializer())
						//.withListenerStrategy(new DeadLetterMLS(handler, bundle, topic, 5, errorHandler)).build());
						.withListenerStrategy(new DeadLetterMLS(DROPWIZARD_APP_RULE.getRule().getEnvironment(), handler, bundle, "test", "test", 5, 1000, errorHandler)).build());

		KafkaProducer<String, Integer> producer = KAFKA.getKafkaTestUtils().getKafkaProducer(StringSerializer.class,
				IntegerSerializer.class);
		IntStream.range(1, 21).forEach(
				e -> producer.send(new ProducerRecord<String, Integer>(topic, UUID.randomUUID().toString(), e)));

		await().atMost(10, SECONDS).until(() -> testResults.size() == 18);
		assertThat("There was at least 1 processing error", processingError.get(), greaterThanOrEqualTo(1));
		assertThat("There must be 18 results finally processed by consumer (excep 2 and 10)", testResults.size(),
				equalTo(18));

		assertThat(testResults, containsInAnyOrder(1, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20));

		KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(topic);
		assertThat(
				"There are no records in the dead letter topic (record 2 and 10 are lost in the test scenario, the error handler needs to take care of those records if he returns true",
				KAFKA.getKafkaTestUtils().consumeAllRecordsFromTopic(retryTopic).size(), equalTo(0));

		listener.forEach(l -> {
			l.stopConsumer();
		});

	}
*/
	private static Object deserialize(byte[] obj) {
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(obj);
			ObjectInput in = new ObjectInputStream(bis);
			return in.readObject();
		} catch (ClassNotFoundException e) {
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}
}
