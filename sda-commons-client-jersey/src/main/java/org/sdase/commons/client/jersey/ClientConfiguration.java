package org.sdase.commons.client.jersey;

/**
 * Configuration of the default timeouts used by the clients.
 */
public class ClientConfiguration {

   /**
    * The default timeout to wait until a connection is established. 500ms should be suitable for all communication in
    * the platform. Clients that request information from external services may extend this timeout if foreign services
    * are usually slow.
    */
   public static final int DEFAULT_CONNECTION_TIMEOUT_MS = 500;

   /**
    * The default timeout to wait for data in an established connection. 2 seconds is used as a trade between "fail
    * fast" and "better return late than no result". The timeout may be changed according to the use case considering
    * how long a user is willing to wait and how long backend operations need.
    */
   public static final int DEFAULT_READ_TIMEOUT_MS = 2_000;


   /**
    * The timeout to wait until a connection is established.
    */
   private int connectTimeoutMs = DEFAULT_CONNECTION_TIMEOUT_MS;

   /**
    * The timeout to wait for data in an established connection.
    */
   private int readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;


   public int getConnectTimeoutMs() {
      return connectTimeoutMs;
   }

   public ClientConfiguration setConnectTimeoutMs(int connectTimeoutMs) {
      this.connectTimeoutMs = connectTimeoutMs;
      return this;
   }

   public int getReadTimeoutMs() {
      return readTimeoutMs;
   }

   public ClientConfiguration setReadTimeoutMs(int readTimeoutMs) {
      this.readTimeoutMs = readTimeoutMs;
      return this;
   }
}
