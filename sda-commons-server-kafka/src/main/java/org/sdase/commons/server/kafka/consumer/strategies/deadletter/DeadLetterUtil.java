package org.sdase.commons.server.kafka.consumer.strategies.deadletter;

import com.github.ftrossbach.club_topicana.core.ExpectedTopicConfiguration;
import io.dropwizard.Configuration;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.sdase.commons.server.kafka.KafkaBundle;
import org.sdase.commons.server.kafka.builder.ProducerRegistration;
import org.sdase.commons.server.kafka.config.ConsumerConfig;
import org.sdase.commons.server.kafka.config.ProducerConfig;
import org.sdase.commons.server.kafka.exception.ConfigurationException;

/**
 * Util class for creating the needed producer and consumer for the dead letter strategy
 */
public class DeadLetterUtil {
    private static final String RETRY_TOPIC_SUFFIX = "retry";
    private static final String DEAD_LETTER_TOPIC_SUFFIX = "deadletter";

    private DeadLetterUtil() {
    }

    /**
     *
     * @param bundle kafka bundle
     * @param sourceTopicConfiguration configuration of the source topic that will be used as default configuration if no explicit configuration for the topic is provided
     * @param sourceTopicConfigName source topic name that will be used as prefix for the topics needed in DLQ strategy
     * @return the producer registrytion for the retry producer
     */
    public static ProducerRegistration<byte[], byte[]> createRetryProducer(KafkaBundle<? extends Configuration> bundle, ExpectedTopicConfiguration sourceTopicConfiguration, String sourceTopicConfigName) {
        final ExpectedTopicConfiguration retryTopicConfiguration = getOrCreateDefaultDlqHandlingTopicConfiguration(bundle, sourceTopicConfiguration, sourceTopicConfigName, RETRY_TOPIC_SUFFIX);
        final ProducerConfig retryProducerConfig = ProducerConfig.builder()
            .withClientId(retryTopicConfiguration.getTopicName() + "-Producer")
            .build();

        return ProducerRegistration.<byte[], byte[]>builder()
            .forTopic(retryTopicConfiguration)
            .createTopicIfMissing()
            .checkTopicConfiguration()
            .withProducerConfig(retryProducerConfig)
            .withKeySerializer(new ByteArraySerializer())
            .withValueSerializer(new ByteArraySerializer())
            .build();
    }

    /**
     *
     * @param bundle kafka bundle
     * @param sourceTopicConfiguration configuration of the source topic that will be used as default configuration if no explicit configuration for the topic is provided
     * @param sourceTopicConfigName source topic name that will be used as prefix for the topics needed in DLQ strategy
     * @return the producer registration for the dlq producer
     */
    public static ProducerRegistration<byte[], byte[]> createDeadLetterProducer(KafkaBundle<? extends Configuration> bundle, ExpectedTopicConfiguration sourceTopicConfiguration, String sourceTopicConfigName) {
        final ExpectedTopicConfiguration deadLetterTopicConfiguration = getOrCreateDefaultDlqHandlingTopicConfiguration(bundle, sourceTopicConfiguration, sourceTopicConfigName, DEAD_LETTER_TOPIC_SUFFIX);
        final ProducerConfig deadLetterProducerConfig = ProducerConfig.builder()
            .withClientId(deadLetterTopicConfiguration.getTopicName() + "-Producer")
            .build();

        return ProducerRegistration.<byte[], byte[]>builder()
            .forTopic(deadLetterTopicConfiguration)
            .createTopicIfMissing()
            .checkTopicConfiguration()
            .withProducerConfig(deadLetterProducerConfig)
            .withKeySerializer(new ByteArraySerializer())
            .withValueSerializer(new ByteArraySerializer())
            .build();
    }

    /**
     *
     * @param bundle kafka bundle
     * @param sourceTopicConsumerConfigName configuration of the source topic that will be used as default configuration if no explicit configuration for the topic is provided
     * @return consumer config for dead letter consumer
     */
    public static ConsumerConfig getDeadLetterConsumerConfig(KafkaBundle<? extends Configuration> bundle, String sourceTopicConsumerConfigName) {
        final ConsumerConfig sourceConsumerConfiguration = bundle.getConsumerConfiguration(sourceTopicConsumerConfigName);

        try {
            final String deadLetterConsumerConfigName = sourceTopicConsumerConfigName + "-" + DEAD_LETTER_TOPIC_SUFFIX;

            return bundle.getConsumerConfiguration(deadLetterConsumerConfigName);
        } catch (ConfigurationException e) {
            return ConsumerConfig.builder()
                .withGroup(sourceConsumerConfiguration.getGroup()  + "-" + DEAD_LETTER_TOPIC_SUFFIX)
                .withClientId(sourceConsumerConfiguration.getClientId() + "-" + DEAD_LETTER_TOPIC_SUFFIX)
                .build();
        }
    }

    public static String getRetryTopicName(ExpectedTopicConfiguration sourceTopicConfiguration) {
        return createDlqHandlingTopicName(sourceTopicConfiguration, RETRY_TOPIC_SUFFIX);
    }

    public static String getDeadLetterTopicName(ExpectedTopicConfiguration sourceTopicConfiguration) {
        return createDlqHandlingTopicName(sourceTopicConfiguration, DEAD_LETTER_TOPIC_SUFFIX);
    }

    private static ExpectedTopicConfiguration getOrCreateDefaultDlqHandlingTopicConfiguration(KafkaBundle<? extends Configuration> bundle, ExpectedTopicConfiguration sourceTopicConfiguration, String sourceTopicConfigName, String dlqHandlingTopicSuffix) {
        try {
            final String retryConfigName = sourceTopicConfigName + "-" + dlqHandlingTopicSuffix;

            return bundle.getTopicConfiguration(retryConfigName);

        } catch (ConfigurationException e) {
            return new ExpectedTopicConfiguration
                .ExpectedTopicConfigurationBuilder(createDlqHandlingTopicName(sourceTopicConfiguration, dlqHandlingTopicSuffix))
                .withPartitionCount(sourceTopicConfiguration.getPartitions().count())
                .withReplicationFactor(sourceTopicConfiguration.getReplicationFactor().count())
                .build();
        }
    }

    private static String createDlqHandlingTopicName(ExpectedTopicConfiguration sourceTopicConfiguration, String dlqHandlingTopicSuffix) {
        return sourceTopicConfiguration.getTopicName() + "." + dlqHandlingTopicSuffix;
    }

}
