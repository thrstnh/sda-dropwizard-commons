package org.sdase.commons.server.kafka.consumer.strategies.deadletter;

import java.util.Arrays;
import java.util.Objects;

public final class DeadLetterDeserializerError {

    private final RuntimeException exception;
    private final byte[] errorPayload;

    public DeadLetterDeserializerError(RuntimeException exception, byte[] errorPayload) {
        this.exception = exception;
        this.errorPayload = errorPayload;
    }

    public static DeadLetterDeserializerErrorBuilder builder() {
        return new DeadLetterDeserializerErrorBuilder();
    }

    public RuntimeException getException() {
        return this.exception;
    }

    public byte[] getErrorPayload() {
        return this.errorPayload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DeadLetterDeserializerError that = (DeadLetterDeserializerError) o;
        return Objects.equals(exception, that.exception) &&
            Arrays.equals(errorPayload, that.errorPayload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(exception);
        result = 31 * result + Arrays.hashCode(errorPayload);
        return result;
    }

    public String toString() {
        return "DeadLetterDeserializerError(exception=" + this.getException() + ", errorPayload=" + Arrays.toString(this.getErrorPayload()) + ")";
    }

    public static class DeadLetterDeserializerErrorBuilder {
        private RuntimeException exception;
        private byte[] errorPayload;

        DeadLetterDeserializerErrorBuilder() {
        }

        public DeadLetterDeserializerError.DeadLetterDeserializerErrorBuilder exception(RuntimeException exception) {
            this.exception = exception;
            return this;
        }

        public DeadLetterDeserializerError.DeadLetterDeserializerErrorBuilder errorPayload(byte[] errorPayload) {
            this.errorPayload = errorPayload;
            return this;
        }

        public DeadLetterDeserializerError build() {
            return new DeadLetterDeserializerError(exception, errorPayload);
        }

        public String toString() {
            return "DeadLetterDeserializerError.DeadLetterDeserializerErrorBuilder(exception=" + this.exception + ", errorPayload=" + Arrays.toString(this.errorPayload) + ")";
        }
    }
}
