package com.ken.parittion;

import com.ken.domain.Trade;
import org.apache.geode.cache.Cache;
import org.apache.geode.cache.EntryOperation;
import org.apache.geode.cache.PartitionResolver;
import org.apache.geode.internal.logging.LogService;
import org.apache.logging.log4j.Logger;

import java.util.Properties;

// StringPrefixPartitionResolver
public class TradePartitionResolver implements PartitionResolver<String, Trade> {

  Cache cache;
  private static final Logger logger = LogService.getLogger();

  @Override
  public Object getRoutingObject(EntryOperation<String, Trade> opDetails) {
    String key = opDetails.getKey();
    String[] split = key.split(":");
    logger.info("key: {}, value: {}", opDetails.getKey(), opDetails.getNewValue());
    return split[1];
  }

  @Override
  public String getName() {
    return getClass().getName();
  }

  @Override
  public void initialize(Cache cache, Properties properties) {
    this.cache = cache;
  }

  @Override
  public void close() {

  }
}
