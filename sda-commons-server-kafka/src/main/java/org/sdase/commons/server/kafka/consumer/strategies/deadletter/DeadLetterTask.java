package org.sdase.commons.server.kafka.consumer.strategies.deadletter;

import com.google.common.collect.ImmutableMultimap;
import io.dropwizard.Configuration;
import io.dropwizard.servlets.tasks.Task;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.sdase.commons.server.kafka.KafkaBundle;
import org.sdase.commons.server.kafka.producer.KafkaMessageProducer;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.Random;

public class DeadLetterTask extends Task {

    private static final int CONSUMER_POLL_TIMEOUT = 5000;

    private final KafkaBundle<? extends Configuration> bundle;
    private final Deserializer<byte[]> keyDeserializer;
    private final Deserializer<byte[]> valueDeserializer;
    private final String sourceTopicConsumerConfigName;
    private final String deadLetterTopic;
    private final KafkaMessageProducer<byte[], byte[]> retryProducer;

    public DeadLetterTask(KafkaBundle<? extends Configuration> bundle, String sourceTopicConsumerConfigName, String deadLetterTopic, KafkaMessageProducer<byte[], byte[]> retryProducer){
        super("deadLetterResend/" + deadLetterTopic);

        this.bundle = bundle;
        this.keyDeserializer = new ByteArrayDeserializer();
        this.valueDeserializer = new ByteArrayDeserializer();
        this.sourceTopicConsumerConfigName = sourceTopicConsumerConfigName;
        this.deadLetterTopic = deadLetterTopic;
        this.retryProducer = retryProducer;
    }

    @Override
    public void execute(ImmutableMultimap<String, String> parameters, PrintWriter printWriter) throws Exception {

        final KafkaConsumer consumer = bundle.createConsumer(keyDeserializer, valueDeserializer,
            DeadLetterUtil.getDeadLetterConsumerConfig(bundle, sourceTopicConsumerConfigName),
            new Random().nextInt()
        );
        consumer.subscribe(Collections.singletonList(deadLetterTopic));

        boolean continueReading = true;

        while (continueReading){
            final ConsumerRecords<byte[], byte[]> records = consumer.poll(CONSUMER_POLL_TIMEOUT);

            if (records.isEmpty()) {
                continueReading = false;
                consumer.close();
            }

            //notice: header will be removed since retry information is not interesting any longer
            records.forEach(record -> retryProducer.send(record.key(), record.value()));
        }
    }
}
