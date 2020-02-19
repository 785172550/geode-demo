package com.ken.transcation;

import com.ken.util.CacheUtils;
import org.apache.geode.cache.CacheTransactionManager;
import org.apache.geode.cache.CommitConflictException;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;

public class Incrementer {
  final int id;
  private final ClientCache cache;
  private final Region<String, Integer> region;

  Incrementer(int id, ClientCache cache, Region<String, Integer> region) {
    this.id = id;
    this.cache = cache;
    this.region = region;
  }

  public static void main(String[] args) {
    // connect to the locator using default port 10334
    ClientCache cache = CacheUtils.plainClient();
    Region<String, Integer> region = CacheUtils.getRegion(Example.REGION_NAME);

    Incrementer incrementer = new Incrementer(Integer.parseInt(args[0]), cache, region);
    incrementer.incrementEntry();

    cache.close();
  }

  void incrementEntry() {
    CacheTransactionManager cacheTransactionManager = cache.getCacheTransactionManager();
    for (int i = 0; i < Example.INCREMENTS; ++i) {
      boolean incremented = false;
      while (!incremented) {
        try {
          cacheTransactionManager.begin();
          final Integer oldValue = region.get(Example.KEY);
          final Integer newValue = oldValue + 1;
          region.put(Example.KEY, newValue);
          cacheTransactionManager.commit();
          incremented = true;
        } catch (CommitConflictException cce) {
          // Do nothing.
        }
      }
    }
  }
}
