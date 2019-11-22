package org.sdase.commons.server.morphia.metrics;

import com.mongodb.MongoClient;
import io.dropwizard.lifecycle.Managed;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MongoDbMetricsInitializer implements Managed {

   private static final Logger LOG = LoggerFactory.getLogger(MongoDbMetricsInitializer.class);

   private MongoClient mongoClient;
   private String database;

   private ScheduledExecutorService executorService;

   private MongoDbStatsReader statsReader;

   private Gauge usedGauge;

   private Gauge sizeGauge;

   public MongoDbMetricsInitializer(MongoClient mongoClient, String database) {
      this.mongoClient = mongoClient;
      this.database = database;
      executorService = Executors.newSingleThreadScheduledExecutor();
   }

   @Override
   public void start() {
      statsReader = new MongoDbStatsReader(mongoClient, database);

      if (!hasConnection(statsReader)) {
         return;
      }

      usedGauge = new Gauge.Builder()
            .name("mongodb_data_filesystem_used_bytes")
            .help("The used bytes of the filesystem where the connected MongoDB stores data.")
            .register(CollectorRegistry.defaultRegistry);

      sizeGauge = new Gauge.Builder()
            .name("mongodb_data_filesystem_size_bytes")
            .help("The size in bytes of the filesystem where the connected MongoDB stores data.")
            .register(CollectorRegistry.defaultRegistry);

      executorService.scheduleWithFixedDelay(this::collect, 1L, 60L, TimeUnit.SECONDS);
   }

   @Override
   public void stop() {
      CollectorRegistry.defaultRegistry.unregister(usedGauge);
      CollectorRegistry.defaultRegistry.unregister(sizeGauge);
      executorService.shutdown();
   }

   private void collect() {
      try {
         MongoDbDataFilesystemStats filesystemStats = statsReader.readFilesystemStats();
         LOG.info("Database usage: {}", filesystemStats);
         usedGauge.set(filesystemStats.getBytesUsed());
         sizeGauge.set(filesystemStats.getBytesSize());
      }
      catch (Exception ignored) {
         LOG.warn("Failed to read MongoDB stats.");
      }
   }

   private boolean hasConnection(MongoDbStatsReader statsReader) {
      try {
         statsReader.readFilesystemStats();
      }
      catch (Exception e) {
         LOG.warn("Failed to read MongoDB stats, not initializing metrics.", e);
         return false;
      }
      return true;
   }
}
