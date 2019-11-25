package org.sdase.commons.server.kafka.consumer.strategies.deadletter;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * A simple helper that delegates all  calls to the wrapped deserializer given
 * in the constructor. Potential SerializationException exceptions are caught, logged and will be wrapped in
 * a DeadLetterDeserializerErrorOrValue including the byte array payload which caused the exception.
 * This helper can be used to workaround a known Kafka problem, see https://issues.apache.org/jira/browse/KAFKA-4740 and
 * is needed for DeadLetterMessageListener strategy to work accordingly by retrying to deliver the failed message.
 *
 * @param <T>
 *           class to deserialize
 */
public class DeadLetterNoSerializationErrorDeserializer<T> implements Deserializer<DeadLetterDeserializerErrorOrValue<T>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeadLetterNoSerializationErrorDeserializer.class);

  private final Deserializer<T> wrappedDeserializer;

  public DeadLetterNoSerializationErrorDeserializer(Deserializer<T> deserializer) {
    this.wrappedDeserializer = deserializer;
  }

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    wrappedDeserializer.configure(configs, isKey);
  }

  @Override
  public DeadLetterDeserializerErrorOrValue<T> deserialize(String topic, byte[] data) {
      try {
          return DeadLetterDeserializerErrorOrValue.value(wrappedDeserializer.deserialize(topic, data));
      } catch (SerializationException e) {
          LOGGER.error("Unable to deserialize record for topic {} due to exception", topic, e);
          return DeadLetterDeserializerErrorOrValue.error(data, e);
      }
  }

  @Override
  public void close() {
    wrappedDeserializer.close();
  }

}
