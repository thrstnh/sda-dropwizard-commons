package org.sdase.commons.server.kafka.consumer.strategies.deadletter;

public class DelayRetryException extends RuntimeException {

    public DelayRetryException(long intervalMS){
        super("Message delivery will be delayed for: " + intervalMS);
    }

}
