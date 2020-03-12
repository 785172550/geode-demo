package com.ken.transaction;

import com.ken.domain.Country;
import com.ken.domain.School;
import com.ken.util.CacheUtils;
import org.apache.geode.cache.CacheTransactionManager;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;

/**
 * --J=-Dgemfire.ALLOW_PERSISTENT_TRANSACTIONS=true
 */
public class TransactionTest {

  public static void main(String[] args) {
    System.setProperty("log4j.configurationFile", "config/log4j2.xml");
    // init
    ClientCache clientCache = CacheUtils.plainClient();

    School school_addr = School.builder().address("school_addr").area(1).name("No. 1").build();
    Country china = Country.builder().name("china").capitol("beijing").currency("RMB").population(140)
            .schoolAdd(school_addr).build();

    Region<String, Object> region = CacheUtils.getRegion("Country");
    CacheTransactionManager cacheTransactionManager = clientCache.getCacheTransactionManager();
    cacheTransactionManager.begin();

    Country country = (Country) region.get("china");
    String name1 = country.getCapitol();
    System.out.println(name1);
    System.out.println("------------------");

    // get again
    String name = country.getCapitol();
    System.out.println(name);

    // second time request
    Country country1 = (Country) region.get("china");
    System.out.println(country1.getCapitol());

    country.setCapitol("china");
    region.put(country.getName(), country);
    cacheTransactionManager.commit();

  }
}
