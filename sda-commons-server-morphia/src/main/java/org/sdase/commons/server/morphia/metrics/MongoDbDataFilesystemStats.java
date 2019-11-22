package org.sdase.commons.server.morphia.metrics;

class MongoDbDataFilesystemStats {

   private double bytesUsed;

   private double bytesSize;

   MongoDbDataFilesystemStats(double bytesUsed, double bytesSize) {
      this.bytesUsed = bytesUsed;
      this.bytesSize = bytesSize;
   }

   double getBytesUsed() {
      return bytesUsed;
   }

   double getBytesSize() {
      return bytesSize;
   }

   @Override
   public String toString() {
      return "MongoDbDataFilesystemStats{" +
            "bytesUsed=" + bytesUsed +
            ", bytesSize=" + bytesSize +
            '}';
   }
}
