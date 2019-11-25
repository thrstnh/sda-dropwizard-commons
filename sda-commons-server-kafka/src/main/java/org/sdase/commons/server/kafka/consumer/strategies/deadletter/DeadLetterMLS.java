package org.sdase.commons.server.kafka.consumer.strategies.deadletter;

import com.github.ftrossbach.club_topicana.core.ExpectedTopicConfiguration;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.prometheus.client.SimpleTimer;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.sdase.commons.server.kafka.KafkaBundle;
import org.sdase.commons.server.kafka.consumer.ErrorHandler;
import org.sdase.commons.server.kafka.consumer.KafkaHelper;
import org.sdase.commons.server.kafka.consumer.MessageHandler;
import org.sdase.commons.server.kafka.consumer.StopListenerException;
import org.sdase.commons.server.kafka.consumer.strategies.MessageListenerStrategy;
import org.sdase.commons.server.kafka.exception.ConfigurationException;
import org.sdase.commons.server.kafka.producer.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;

import static java.util.Objects.isNull;

/**
 * {@link MessageListenerStrategy Strategy} that stores messages with processing
 * errors to a separate topic. It allows a configurable number of retries for a
 * failed message until it will be pushed to a third topic (dead letter topic).
 *
 * Messages can be manually copied from the dead letter topic back to the original topic using
 * the {@link DeadLetterTask dead letter admin task} which will be automatically registered in
 * your application.
 *
 * To use the strategy the {@link DeadLetterNoSerializationErrorDeserializer} needs to be used as a wrapper for
 * key and value deserializer
 */
public class DeadLetterMLS<K extends Serializable, V extends Serializable> extends MessageListenerStrategy<DeadLetterDeserializerErrorOrValue<K>, DeadLetterDeserializerErrorOrValue<V>> { //NOSONAR

    private static final Logger LOGGER = LoggerFactory.getLogger(DeadLetterMLS.class);
    private static final String EXCEPTION = "Exception";
    private static final String RETRIES = "Retries";
    private final MessageHandler<K, V> handler;
    private final ErrorHandler<K, V> errorHandler;
    private String consumerName;

    private final MessageProducer<byte[], byte[]> retryProducer;
    private final MessageProducer<byte[], byte[]> deadLetterTopicProducer;
    private final int maxNumberOfRetries;

    /**
     *
     * @param environment environment of the dropwizard app that will be used to create an admin task
     * @param handler handler that processes the message
     * @param bundle kafka bundle
     * @param sourceTopicConfigName will be used as a prefix to find the configuration for
     * *                        the retry and dead letter topic.
     * @param maxNumberOfRetries number of automated retries before the message will end in dead letter queue
     * @param errorHandler error handler that will be used if the handler throws an exception
     */
    public DeadLetterMLS(Environment environment, MessageHandler<K, V> handler, KafkaBundle<? extends Configuration> bundle,
                         String sourceTopicConfigName, String sourceTopicConsumerConfigName, int maxNumberOfRetries, int retryIntervalMS, ErrorHandler<K, V> errorHandler) {
        this.handler = handler;

        final ExpectedTopicConfiguration sourceTopicConfiguration = bundle.getTopicConfiguration(sourceTopicConfigName);

        this.retryProducer = bundle.registerProducer(DeadLetterUtil.createRetryProducer(bundle, sourceTopicConfiguration, sourceTopicConfigName));
        this.deadLetterTopicProducer = bundle.registerProducer(DeadLetterUtil.createDeadLetterProducer(bundle, sourceTopicConfiguration, sourceTopicConfigName));
        this.maxNumberOfRetries = maxNumberOfRetries;
        this.errorHandler = errorHandler;

        final DeadLetterTopicHandling deadLetterTopicHandling = new DeadLetterTopicHandling(
            DeadLetterUtil.getRetryTopicName(sourceTopicConfiguration),
            sourceTopicConfiguration.getTopicName(),
            DeadLetterUtil.getDeadLetterTopicName(sourceTopicConfiguration),
            bundle,
            retryIntervalMS, sourceTopicConsumerConfigName
        );

        environment
            .admin()
            .addTask(deadLetterTopicHandling.getCopyTask());
    }

    @Override
    public void processRecords(ConsumerRecords<DeadLetterDeserializerErrorOrValue<K>, DeadLetterDeserializerErrorOrValue<V>> records, KafkaConsumer<DeadLetterDeserializerErrorOrValue<K>, DeadLetterDeserializerErrorOrValue<V>> consumer) {
        if (consumerName == null) {
            consumerName = KafkaHelper.getClientId(consumer);
        }

        records.forEach(r -> processRecord(r, consumer));
    }

    @SuppressWarnings("deprecation") // record.checksum() is deprecated as of Kafka 0.11.0
    private void processRecord(ConsumerRecord<DeadLetterDeserializerErrorOrValue<K>, DeadLetterDeserializerErrorOrValue<V>> record, KafkaConsumer<DeadLetterDeserializerErrorOrValue<K>, DeadLetterDeserializerErrorOrValue<V>> consumer) {
        boolean shouldContinue = true;
        try {
            if (record.key().hasValue() && record.value().hasValue()) {
                final SimpleTimer timer = new SimpleTimer();

                handler.handle(new ConsumerRecord<>(record.topic(), record.partition(),record.offset(),
                    record.timestamp(), record.timestampType(), record.checksum(), record.serializedKeySize(), //NOSONAR
                    record.serializedValueSize(),
                    record.key().getValue(), record.value().getValue(), record.headers()
                ));

                // Prometheus
                final double elapsedSeconds = timer.elapsedSeconds();
                consumerProcessedMsgHistogram.observe(elapsedSeconds, consumerName, record.topic());

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("calculated duration {} for message consumed by {} from {}", elapsedSeconds, consumerName, record.topic());
                }
            } else {
                shouldContinue = handleDeserializationException(record);
            }
        } catch (RuntimeException e) {
            shouldContinue = handleError(record, e);
        } finally {
            if (shouldContinue)
                consumer.commitSync();
            else
                throw new StopListenerException(new RuntimeException("Listener Stopped because error handler returned false")); //NOSONAR
        }
    }

    private boolean handleDeserializationException(ConsumerRecord<DeadLetterDeserializerErrorOrValue<K>, DeadLetterDeserializerErrorOrValue<V>> record){
        if (record.key().hasError()){
            return handleError(record, record.key().getError().getException());
        }
        return handleError(record, record.value().getError().getException());
    }

    @SuppressWarnings("deprecation") // record.checksum() is deprecated as of Kafka 0.11.0
    private boolean handleError(ConsumerRecord<DeadLetterDeserializerErrorOrValue<K>, DeadLetterDeserializerErrorOrValue<V>> record, RuntimeException e) {
        try {
            final K recordVKey = record.key().hasValue() ? null : record.key().getValue();
            final V recordValue = record.value().hasValue() ? null : record.value().getValue();

            return errorHandler.handleError(
                new ConsumerRecord<>(
                    record.topic(), record.partition(),record.offset(),
                    record.timestamp(), record.timestampType(), record.checksum(), record.serializedKeySize(), //NOSONAR
                    record.serializedValueSize(),
                    recordVKey, recordValue, record.headers()),
                e,
                null
            );
        } catch (Exception innerException) {
            deadLetterHandling(createByteArrayRecord(record), e);
        }
        return true;
    }

    private void deadLetterHandling(ConsumerRecord<byte[], byte[]> record, Exception e) {
        final int executedNumberOfRetries = getRetryInformationFromRecord(record) + 1;

        Headers headersList = new RecordHeaders();
        headersList.add(EXCEPTION, serialize(e));
        headersList.add(RETRIES, serialize(executedNumberOfRetries));

        if (executedNumberOfRetries <= maxNumberOfRetries) {
            retryProducer.send(record.key(), record.value(), headersList);
        } else {
            deadLetterTopicProducer.send(record.key(), record.value(), headersList);
        }
    }

    @Override
    public void commitOnClose(KafkaConsumer<DeadLetterDeserializerErrorOrValue<K>, DeadLetterDeserializerErrorOrValue<V>> consumer) {
        try {
            consumer.commitSync();
        } catch (CommitFailedException e) {
            LOGGER.error("Commit failed", e);
        }
    }

    @Override
    public void verifyConsumerConfig(Map<String, String> config) {
        if (Boolean.parseBoolean(config.getOrDefault("enable.auto.commit", "false"))) {
            throw new ConfigurationException(
                "The strategy should not auto commit since the strategy does that manually. But property 'enable.auto.commit' in consumer config is set to 'true'");
        }
    }

    private int getRetryInformationFromRecord(ConsumerRecord<byte[], byte[]> record) {
        for (final Header pair : record.headers()) {
            if (pair.key().equalsIgnoreCase("retries") && pair.value() != null) {
                Object deserializedValue = deserialize(pair.value());
                if (deserializedValue instanceof Integer)
                    return (int) deserializedValue;
            }
        }

        return 0;
    }

    private static byte[] serialize(Serializable obj) {
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final ObjectOutputStream os = new ObjectOutputStream(out);

            os.writeObject(obj);

            return out.toByteArray();
        } catch (IOException e) {
            LOGGER.error("Serialization failed. ", e);
            return null; // NOSONAR
        }
    }

    private static Object deserialize(byte[] obj) {
        try {
            final ByteArrayInputStream bis = new ByteArrayInputStream(obj);
            final ObjectInputStream in = new ObjectInputStream(bis);

            return in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.error("Deserialization failed.", e);
            return null;
        }
    }

    @SuppressWarnings("deprecation") // record.checksum() is deprecated as of Kafka 0.11.0
    private ConsumerRecord<byte[], byte[]> createByteArrayRecord(ConsumerRecord<DeadLetterDeserializerErrorOrValue<K>, DeadLetterDeserializerErrorOrValue<V>> record) {

        final byte[] keyPayload = getByteArrayPayload(record.key());
        final byte[] valuePayload = getByteArrayPayload(record.value());

        return new ConsumerRecord<>(record.topic(), record.partition(),record.offset(), record.timestamp(),
            record.timestampType(), record.checksum(), record.serializedKeySize(), record.serializedValueSize(), //NOSONAR
            keyPayload, valuePayload, record.headers()
        );
    }

    private <T extends Serializable> byte[] getByteArrayPayload(DeadLetterDeserializerErrorOrValue<T> recordTupleValue) {
        if (isNull(recordTupleValue) || (isNull(recordTupleValue.getValue()) && isNull(recordTupleValue.getError()))) {
            return null; // NOSONAR
        }

        return recordTupleValue.hasValue()
            ? serialize(recordTupleValue.getValue())
            : recordTupleValue.getError().getErrorPayload();
    }

}
