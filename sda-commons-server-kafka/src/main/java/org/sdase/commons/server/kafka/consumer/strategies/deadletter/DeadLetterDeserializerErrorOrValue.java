package org.sdase.commons.server.kafka.consumer.strategies.deadletter;

import java.util.Objects;

import static java.util.Objects.nonNull;

public final class DeadLetterDeserializerErrorOrValue<T> {

    private final DeadLetterDeserializerError error;
    private final T value;

    public DeadLetterDeserializerErrorOrValue(DeadLetterDeserializerError error, T value) {
        this.error = error;
        this.value = value;
    }

    public static <T> DeadLetterDeserializerErrorOrValueBuilder<T> builder() {
        return new DeadLetterDeserializerErrorOrValueBuilder<T>();
    }

    public static <T> DeadLetterDeserializerErrorOrValue<T> error(byte[] errorPayload, RuntimeException exception) {
        return DeadLetterDeserializerErrorOrValue.<T> builder()
            .error(DeadLetterDeserializerError.builder()
                .errorPayload(errorPayload)
                .exception(exception)
                .build()
            )
            .build();
    }

    public static <T> DeadLetterDeserializerErrorOrValue<T> value(T value) {
        return DeadLetterDeserializerErrorOrValue.<T> builder()
            .value(value)
            .build();
    }

    public DeadLetterDeserializerError getError() {
        return this.error;
    }

    public T getValue() {
        return this.value;
    }

    public boolean hasError() {
        return nonNull(error);
    }

    public boolean hasValue() {
        return nonNull(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DeadLetterDeserializerErrorOrValue<?> that = (DeadLetterDeserializerErrorOrValue<?>) o;
        return Objects.equals(error, that.error) &&
            Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(error, value);
    }

    public String toString() {
        return "DeadLetterDeserializerErrorOrValue(error=" + this.getError() + ", value=" + this.getValue() + ")";
    }

    public static class DeadLetterDeserializerErrorOrValueBuilder<T> {
        private DeadLetterDeserializerError error;
        private T value;

        DeadLetterDeserializerErrorOrValueBuilder() {
        }

        public DeadLetterDeserializerErrorOrValue.DeadLetterDeserializerErrorOrValueBuilder<T> error(DeadLetterDeserializerError error) {
            this.error = error;
            return this;
        }

        public DeadLetterDeserializerErrorOrValue.DeadLetterDeserializerErrorOrValueBuilder<T> value(T value) {
            this.value = value;
            return this;
        }

        public DeadLetterDeserializerErrorOrValue<T> build() {
            return new DeadLetterDeserializerErrorOrValue<T>(error, value);
        }

        public String toString() {
            return "DeadLetterDeserializerErrorOrValue.DeadLetterDeserializerErrorOrValueBuilder(error=" + this.error + ", value=" + this.value + ")";
        }
    }
}
