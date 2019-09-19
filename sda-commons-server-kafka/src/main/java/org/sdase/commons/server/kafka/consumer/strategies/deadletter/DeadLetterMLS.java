package org.sdase.commons.server.kafka.consumer.strategies.deadletter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.sdase.commons.server.kafka.KafkaBundle;
import org.sdase.commons.server.kafka.builder.ProducerRegistration;
import org.sdase.commons.server.kafka.config.ProducerConfig;
import org.sdase.commons.server.kafka.consumer.ErrorHandler;
import org.sdase.commons.server.kafka.consumer.KafkaHelper;
import org.sdase.commons.server.kafka.consumer.MessageHandler;
import org.sdase.commons.server.kafka.consumer.strategies.MessageListenerStrategy;
import org.sdase.commons.server.kafka.exception.ConfigurationException;
import org.sdase.commons.server.kafka.producer.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.Configuration;
import io.prometheus.client.SimpleTimer;

/**
 * {@link MessageListenerStrategy} Strategy that stores messages with processing
 * errors to a separate queue. It allows a configurable number of retries for a
 * failed message until it will be pushed to a third queue.
 */
public class DeadLetterMLS<K, V> extends MessageListenerStrategy<K, V> {

	private static final Logger LOGGER = LoggerFactory.getLogger(DeadLetterMLS.class);
	private final MessageHandler<K, V> handler;
	private final ErrorHandler<K, V> errorHandler;
	private String consumerName;

	private static final String RETRY_TOPIC_CONFIG_POSTFIX = "retryTopic";
	private static final String DEAD_LETTER_TOPIC_CONFIG_POSTFIX = "deadLetterTopic";

	/**
	 * Constructor for dead letter message listener strategy
	 * 
	 * @param handler         handler that processes the message
	 * @param bundle          kafka bundle
	 * @param sourceTopicName will be used as a prefix to find the configuration for
	 *                        the retry and dead letter topic. The configuration key
	 *                        of the retry topic needs to be sourceTopicName +
	 *                        "retryTopic". The configuration key of the dead letter
	 *                        topic needs to be sourceTopicName + "deadLetterTopic".
	 * 
	 */
	public DeadLetterMLS(MessageHandler<K, V> handler, KafkaBundle<? extends Configuration> bundle,
			String sourceTopicName, int maxNumberOfRetries) {
		this.handler = handler;

		// Producer for retry
		ProducerConfig retryProducerConfig = ProducerConfig.builder().withClientId(
				bundle.getTopicConfiguration(sourceTopicName + RETRY_TOPIC_CONFIG_POSTFIX).getTopicName() + "-Producer")
				.build();

		MessageProducer<byte[], byte[]> retryProducer = bundle.registerProducer(ProducerRegistration
				.<byte[], byte[]>builder()
				.forTopic(bundle.getTopicConfiguration(sourceTopicName + RETRY_TOPIC_CONFIG_POSTFIX))
				.checkTopicConfiguration().withProducerConfig(retryProducerConfig)
				.withKeySerializer(new ByteArraySerializer()).withValueSerializer(new ByteArraySerializer()).build());

		// producer without retry (final topic)
		ProducerConfig deadProducerConfig = ProducerConfig.builder()
				.withClientId(
						bundle.getTopicConfiguration(sourceTopicName + DEAD_LETTER_TOPIC_CONFIG_POSTFIX).getTopicName()
								+ "-Producer")
				.build();
		MessageProducer<byte[], byte[]> deadLetterProducer = bundle.registerProducer(ProducerRegistration
				.<byte[], byte[]>builder()
				.forTopic(bundle.getTopicConfiguration(sourceTopicName + DEAD_LETTER_TOPIC_CONFIG_POSTFIX))
				.checkTopicConfiguration().withProducerConfig(deadProducerConfig)
				.withKeySerializer(new ByteArraySerializer()).withValueSerializer(new ByteArraySerializer()).build());

		errorHandler = new DeadLetterErrorHandler<K, V>(retryProducer, deadLetterProducer, maxNumberOfRetries);
	}

	@Override
	public void processRecords(ConsumerRecords<K, V> records, KafkaConsumer<K, V> consumer) {
		if (consumerName == null) {
			consumerName = KafkaHelper.getClientId(consumer);
		}

		records.forEach(r -> {
			processRecord(r, consumer);
		});

//		for (TopicPartition partition : records.partitions()) {
//			processRecordsByPartition(records, consumer, partition);
//		}
	}

	private void processRecord(ConsumerRecord<K, V> record, KafkaConsumer<K, V> consumer) {

		LOGGER.debug("Handling message for {}", record.key());

		try {
			SimpleTimer timer = new SimpleTimer();
			handler.handle(record);

			// Prometheus
			double elapsedSeconds = timer.elapsedSeconds();
			consumerProcessedMsgHistogram.observe(elapsedSeconds, consumerName, record.topic());

			if (LOGGER.isTraceEnabled()) {
				LOGGER.trace("calculated duration {} for message consumed by {} from {}", elapsedSeconds, consumerName,
						record.topic());
			}

		} catch (RuntimeException e) {
			LOGGER.error("Error while handling record {} in message handler {}", record.key(), handler.getClass(), e);
			errorHandler.handleError(record, e, consumer);
		}

		consumer.commitSync();
	}

	private void processRecordsByPartition(ConsumerRecords<K, V> records, KafkaConsumer<K, V> consumer,
			TopicPartition partition) {
		List<ConsumerRecord<K, V>> partitionRecords = records.records(partition);
		OffsetAndMetadata lastCommitOffset = null;
		for (ConsumerRecord<K, V> record : partitionRecords) {
			LOGGER.debug("Handling message for {}", record.key());

			try {
				SimpleTimer timer = new SimpleTimer();
				handler.handle(record);
				// mark last successful processed record for commit
				lastCommitOffset = new OffsetAndMetadata(record.offset() + 1);

				// Prometheus
				double elapsedSeconds = timer.elapsedSeconds();
				consumerProcessedMsgHistogram.observe(elapsedSeconds, consumerName, record.topic());

				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("calculated duration {} for message consumed by {} from {}", elapsedSeconds,
							consumerName, record.topic());
				}

			} catch (RuntimeException e) {
				LOGGER.error("Error while handling record {} in message handler {}", record.key(), handler.getClass(),
						e);
				boolean shouldContinue = errorHandler.handleError(record, e, consumer);
			}
		}
		if (lastCommitOffset != null) {
			consumer.commitSync(Collections.singletonMap(partition, lastCommitOffset));
		}
	}

	@Override
	public void commitOnClose(KafkaConsumer<K, V> consumer) {
		try {
			consumer.commitSync();
		} catch (CommitFailedException e) {
			LOGGER.error("Commit failed", e);
		}
	}

	@Override
	public void verifyConsumerConfig(Map<String, String> config) {
		if (Boolean.valueOf(config.getOrDefault("enable.auto.commit", "false"))) {
			throw new ConfigurationException(
					"The strategy should not auto commit since the strategy does that manually. But property 'enable.auto.commit' in consumer config is set to 'true'");
		}
	}

}
