package org.sdase.commons.server.kafka.consumer.strategies.deadletter;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.sdase.commons.server.kafka.consumer.ErrorHandler;
import org.sdase.commons.server.kafka.consumer.MessageHandler;
import org.sdase.commons.server.kafka.producer.KafkaMessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used for sending messages to a kafka topic with a specified delay. The message handler thread will sleep for a given time if message  timestamp is not smaller than current time - delay
 *
 *
 */
public class RetryMechanism implements ErrorHandler<byte[],byte[]>, MessageHandler<byte[],byte[]> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryMechanism.class);

    private final KafkaMessageProducer<byte[], byte[]> retryProducer;
    private final long retryIntervalMS;

    /**
     *
     * @param retryProducer producer used to send records back to application
     * @param retryIntervalMS interval for the delayed message delivery
     */
    public RetryMechanism(KafkaMessageProducer<byte[], byte[]> retryProducer, long retryIntervalMS) {
        this.retryProducer = retryProducer;
        this.retryIntervalMS = retryIntervalMS;
    }

    @Override
    public boolean handleError(ConsumerRecord<byte[], byte[]> consumerRecord, RuntimeException e, Consumer<byte[], byte[]> consumer) {
        consumer.pause(consumer.assignment());

        final long threadId = Thread.currentThread().getId();

        try {
            Thread.sleep(retryIntervalMS); 
        } catch (InterruptedException ex) { //NOSONAR
            LOGGER.error("Thread {} was interrupted", threadId, ex);
        }

        consumer.resume(consumer.paused());
        retryProducer.send(consumerRecord.key(), consumerRecord.value(), consumerRecord.headers());

        return true;
    }

    @Override
    public void handle(ConsumerRecord<byte[], byte[]> consumerRecord) {
        if (consumerRecord.timestamp() < System.currentTimeMillis() - retryIntervalMS) {
            retryProducer.send(consumerRecord.key(), consumerRecord.value(), consumerRecord.headers());
        } else {
            throw new DelayRetryException(retryIntervalMS);
        }
    }
}
