package org.sdase.commons.server.kafka.consumer.strategies.deadletter;

import io.dropwizard.Configuration;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.sdase.commons.server.kafka.KafkaBundle;
import org.sdase.commons.server.kafka.builder.MessageListenerRegistration;
import org.sdase.commons.server.kafka.builder.ProducerRegistration;
import org.sdase.commons.server.kafka.config.ConsumerConfig;
import org.sdase.commons.server.kafka.config.ProducerConfig;
import org.sdase.commons.server.kafka.consumer.ErrorHandler;
import org.sdase.commons.server.kafka.consumer.MessageHandler;
import org.sdase.commons.server.kafka.consumer.strategies.synccommit.SyncCommitMLS;
import org.sdase.commons.server.kafka.producer.KafkaMessageProducer;

/**
 * This class is used for the retry mechanism of the dead letter strategy. The first part of the strategy comes with an automated retry mechanism that will be executed one the strategy is initialized. It also comes with an admin task that is used to copy the messages from the dead letter topic back to the application topic
 */
public class DeadLetterTopicHandling {

    private final DeadLetterTask copyTask;

    /**
     *
     * @param retryTopicName name of the retry topic
     * @param sourceTopicName name of the source topic of the application
     * @param deadLetterTopicName name of the dead letter topic
     * @param bundle kafka bundle
     * @param intervalMS the interval which is used for the automated message retry
     */
    public DeadLetterTopicHandling(String retryTopicName, String sourceTopicName, String deadLetterTopicName, KafkaBundle<? extends Configuration> bundle, long intervalMS, String sourceTopicConsumerConfigName){

        final KafkaMessageProducer retryProducer = (KafkaMessageProducer<byte[], byte[]>) bundle.registerProducer(createProducer(sourceTopicName));
        final RetryMechanism retryMechanism = new RetryMechanism(retryProducer, intervalMS);

        bundle.createMessageListener(createConsumerForRetryTopic(retryTopicName, retryMechanism, retryMechanism));

        copyTask = new DeadLetterTask(bundle, sourceTopicConsumerConfigName, deadLetterTopicName, retryProducer);
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

    public DeadLetterTask getCopyTask(){
        return copyTask;
    }

}
