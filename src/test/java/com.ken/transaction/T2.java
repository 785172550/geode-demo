package com.ken.transaction;

import com.ken.domain.Country;
import com.ken.util.CacheUtils;
import org.apache.geode.cache.CacheTransactionManager;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;

public class T2 {

  public static void main(String[] args) {
    System.setProperty("log4j.configurationFile", "config/log4j2.xml");
    // init
    ClientCache clientCache = CacheUtils.plainClient();
    Region<String, Object> region = CacheUtils.getRegion("Country");
    CacheTransactionManager cacheTransactionManager = clientCache.getCacheTransactionManager();
//    cacheTransactionManager.begin();
    Country country = (Country) region.get("china");

//    country.setCapitol("new tow");
    System.out.println(country.getCapitol());
//    region.put(country.getName(), country);

    System.out.println("update ------- ");
//    cacheTransactionManager.commit();
  }
}
