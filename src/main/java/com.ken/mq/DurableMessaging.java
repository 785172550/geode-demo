package com.ken.mq;

import com.ken.util.CacheUtils;
import org.apache.geode.cache.EntryEvent;
import org.apache.geode.cache.InterestResultPolicy;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientRegionFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.util.CacheListenerAdapter;

import java.util.concurrent.CountDownLatch;

public class DurableMessaging {

  private static final int numEvents = 10;
  private static final CountDownLatch waitForEventsLatch = new CountDownLatch(numEvents);

  public static void main(String[] args) throws InterruptedException {
    System.setProperty("log4j.configurationFile", "config/log4j2.xml");
    String regionName = "test-region";

    // init
    ClientCache clientCache = CacheUtils.durableClient();
    ClientRegionFactory<Integer, String> clientRegionFactory =
            clientCache.createClientRegionFactory(ClientRegionShortcut.PROXY);
    Region<Integer, String> testRegion =
            clientRegionFactory.addCacheListener(new ExampleCacheListener<>()).create(regionName);

    testRegion.registerInterestForAllKeys(InterestResultPolicy.DEFAULT, true);
    clientCache.close(true);

    // Create a second client to do puts with while the first client is disconnected
    ClientCache clientCacheTwo = CacheUtils.plainClient();
    ClientRegionFactory<Integer, String> clientTwoRegionFactory =
            clientCacheTwo.createClientRegionFactory(ClientRegionShortcut.PROXY);
    Region<Integer, String> exampleClientRegionTwo = clientTwoRegionFactory.create(regionName);

    for (int i = 0; i < numEvents; ++i) {
      exampleClientRegionTwo.put(i, "testValue" + i);
    }
    clientCacheTwo.close(false);

    // restart client one
    clientCache = CacheUtils.durableClient();
    clientRegionFactory = clientCache.createClientRegionFactory(ClientRegionShortcut.PROXY);
    testRegion = clientRegionFactory.addCacheListener(new ExampleCacheListener<>()).create(regionName);
    clientCache.readyForEvents();

    waitForEventsLatch.await();

  }

  public static class ExampleCacheListener<Integer, String>
          extends CacheListenerAdapter<Integer, String> {
    ExampleCacheListener() {
    }

    @Override
    public void afterCreate(EntryEvent<Integer, String> event) {
      System.out.println("Received create for key " + event.getKey() + " after durable client reconnection");
      waitForEventsLatch.countDown();
    }
  }
}


