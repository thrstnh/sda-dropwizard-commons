package org.sdase.commons.server.kafka.consumer.strategies.deadletter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.sdase.commons.server.kafka.consumer.ErrorHandler;
import org.sdase.commons.server.kafka.producer.MessageProducer;

public class DeadLetterErrorHandler<K, V> implements ErrorHandler<K, V> {

	private MessageProducer<byte[], byte[]> retryProducer;
	private MessageProducer<byte[], byte[]> deadLetterTopicProducer;
	private int maxNumberOfRetries;

	public DeadLetterErrorHandler(MessageProducer<byte[], byte[]> retryProducer,
			MessageProducer<byte[], byte[]> deadLetterTopicProducer, int maxNumberOfRetries) {
		this.retryProducer = retryProducer;
		this.deadLetterTopicProducer = deadLetterTopicProducer;
		this.maxNumberOfRetries = maxNumberOfRetries;
	}

	@Override
	public boolean handleError(ConsumerRecord<K, V> record, RuntimeException e, Consumer<K, V> consumer) {
		int executedNumberOfRetries = getRetryInformationFromRecord(record) + 1;

		Headers headersList = new RecordHeaders();
		headersList.add("Exception", serialize(e));
		headersList.add("Retries", serialize(executedNumberOfRetries));

		if (executedNumberOfRetries <= maxNumberOfRetries) {
			retryProducer.send(serialize(record.key()), serialize(record.value()), headersList);
		} else {
			deadLetterTopicProducer.send(serialize(record.key()), serialize(record.value()), headersList);
		}

		return true;
	}

	private int getRetryInformationFromRecord(ConsumerRecord<K, V> record) {
		for (Header pair : record.headers()) {
			if (pair.key().equalsIgnoreCase("retries")) {
				if (pair.value() != null) {
					Object deserializedValue = deserialize(pair.value());
					if (deserializedValue instanceof Integer)
						return (int) deserializedValue;
				}
			}
		}

		return 0;
	}

	private static byte[] serialize(Object obj) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ObjectOutputStream os = new ObjectOutputStream(out);
			os.writeObject(obj);
			return out.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

	private static Object deserialize(byte[] obj) {
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(obj);
			ObjectInput in = new ObjectInputStream(bis);
			return in.readObject();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
