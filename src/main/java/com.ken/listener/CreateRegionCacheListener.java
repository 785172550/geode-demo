package com.ken.listener;

import org.apache.geode.cache.*;
import org.apache.geode.cache.util.CacheListenerAdapter;
import org.apache.geode.internal.logging.LogService;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Properties;

public class CreateRegionCacheListener extends CacheListenerAdapter<String, RegionAttributes> implements Declarable {

  private static final Logger logger = LogService.getLogger();
  private Cache cache;

  @Override
  public void initialize(Cache cache, Properties properties) {
    this.cache = cache;
  }

  @Override
  public void afterCreate(EntryEvent<String, RegionAttributes> event) {
    createRegion(event.getKey(), event.getNewValue());
  }

  @Override
  public void afterRegionCreate(RegionEvent<String, RegionAttributes> event) {
    Region<String, RegionAttributes> region = event.getRegion();
    for (Map.Entry<String, RegionAttributes> entry : region.entrySet()) {
      createRegion(entry.getKey(), entry.getValue());
    }
  }

  private void createRegion(String regionName, RegionAttributes attributes) {
    logger.info("CreateRegionCacheListener creating region named: {} with attributes: {}", regionName, attributes);
    try {
      Region region = this.cache.createRegionFactory(attributes)
              .create(regionName);
      if (this.cache.getLogger().fineEnabled()) {
        this.cache.getLogger().fine("CreateRegionCacheListener created: "
                + region);
      }
      logger.info("CreateRegionCacheListener created: {}", region);
    } catch (RegionExistsException e) {/* ignore */}
  }
}