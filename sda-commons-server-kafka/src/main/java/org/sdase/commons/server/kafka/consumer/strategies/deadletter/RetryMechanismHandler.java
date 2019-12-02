package org.sdase.commons.server.kafka.consumer.strategies.deadletter;

import io.dropwizard.Configuration;
import org.apache.kafka.common.serialization.*;
import org.sdase.commons.server.kafka.KafkaBundle;
import org.sdase.commons.server.kafka.builder.*;
import org.sdase.commons.server.kafka.config.*;
import org.sdase.commons.server.kafka.consumer.*;
import org.sdase.commons.server.kafka.consumer.strategies.synccommit.SyncCommitMLS;
import org.sdase.commons.server.kafka.producer.KafkaMessageProducer;

/**
 * This class is used for the retry mechanism of the dead letter strategy. The first part of the strategy comes with an automated retry mechanism that will be executed one the strategy is initialized. It also comes with an admin task that is used to copy the messages from the dead letter topic back to the application topic
 */
public class RetryMechanismHandler {

    private KafkaMessageProducer retryProducer;

    /**
     *
     * @param retryTopicName name of the retry topic
     * @param sourceTopicName name of the source topic of the application
     * @param bundle kafka bundle
     * @param intervalMS the interval which is used for the automated message retry
     */
    public RetryMechanismHandler(String retryTopicName, String sourceTopicName, KafkaBundle<? extends Configuration> bundle, long intervalMS){

        final KafkaMessageProducer producerForRetry = (KafkaMessageProducer<byte[], byte[]>) bundle.registerProducer(createProducer(sourceTopicName));
        final RetryMechanism retryMechanism = new RetryMechanism(producerForRetry, intervalMS);

        bundle.createMessageListener(createConsumerForRetryTopic(retryTopicName, retryMechanism, retryMechanism));

        this.retryProducer = producerForRetry;
    }

    private MessageListenerRegistration<byte[], byte[]> createConsumerForRetryTopic(String sourceTopic, MessageHandler<byte[], byte[]> messageHandler, ErrorHandler<byte[], byte[]> errorHandler) {
        return MessageListenerRegistration.<byte[], byte[]>builder()
            .withDefaultListenerConfig()
            .forTopic(sourceTopic)
            .withConsumerConfig(ConsumerConfig.builder()
                .withGroup(sourceTopic + ".retryTopicConsumer")
                .build()
            )
            .withKeyDeserializer(new ByteArrayDeserializer())
            .withValueDeserializer(new ByteArrayDeserializer())
            .withListenerStrategy(new SyncCommitMLS<>(messageHandler, errorHandler))
            .build();
    }

    private ProducerRegistration<byte[], byte[]> createProducer(String topicName) {
        ProducerConfig retryProducerConfig = ProducerConfig.builder()
            .withClientId(topicName + "-Producer")
            .build();

        return ProducerRegistration.<byte[], byte[]>builder()
            .forTopic(topicName)
            .checkTopicConfiguration()
            .withProducerConfig(retryProducerConfig)
            .withKeySerializer(new ByteArraySerializer())
            .withValueSerializer(new ByteArraySerializer())
            .build();
    }

    public KafkaMessageProducer getRetryProducer(){return retryProducer;}
}
