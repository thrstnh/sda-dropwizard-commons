package org.sda.commons.server.jackson.hal;

public class InvalidHalLinkInvocationException extends RuntimeException {

  public InvalidHalLinkInvocationException(String message) {
    super(message);
  }
}
