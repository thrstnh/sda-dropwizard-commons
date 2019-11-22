package org.sdase.commons.server.morphia.metrics;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

class MongoDbStatsReader {

   private MongoDatabase database;

   MongoDbStatsReader(MongoClient mongoClient, String database) {
      this.database = mongoClient.getDatabase(database);
   }

   MongoDbDataFilesystemStats readFilesystemStats() {
      Document dbStats = database.runCommand(new Document("dbStats", 1));
      // in some cases we get doubles and in some cases we get ints, not sure why that happens
      Number used = dbStats.get("fsUsedSize", Number.class);
      Number size = dbStats.get("fsTotalSize", Number.class);
      return new MongoDbDataFilesystemStats(used.doubleValue(), size.doubleValue());
   }

}
