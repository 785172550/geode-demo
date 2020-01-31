package com.ken.listener;

import com.ken.domain.Student;
import org.apache.geode.cache.*;
import org.apache.geode.cache.util.CacheListenerAdapter;
import org.apache.geode.internal.logging.LogService;
import org.apache.logging.log4j.Logger;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DestroyListener extends CacheListenerAdapter<Integer, Student> implements Declarable {
  private static final Logger logger = LogService.getLogger();
  private Cache cache;
  private final ExecutorService exService = Executors.newSingleThreadExecutor();

  @Override
  public void initialize(Cache cache, Properties properties) {
    this.cache = cache;
  }

  @Override
  public void afterDestroy(EntryEvent<Integer, Student> event) {

    Integer key = event.getKey();
    Student oldValue = event.getOldValue();
    Student newValue = event.getNewValue();
    logger.info("key: {}, old value {}, new value {}", key, oldValue, newValue);

    exService.submit(() -> {
      // migrate to history region
      RegionService regionService = event.getRegion().getRegionService();
      Region<Integer, Student> history_student = regionService.getRegion("History_Student");
      history_student.put(key, oldValue);
    });
  }
}
