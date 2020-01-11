/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
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
