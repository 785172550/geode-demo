package com.ken;

import com.ken.domain.Country;
import com.ken.util.CacheUtils;
import org.apache.geode.cache.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestConnection {
  private static Logger logger = LoggerFactory.getLogger(TestConnection.class);

  public static void main(String[] args) {
    System.setProperty("log4j.configurationFile", "config/log4j2.xml");

    Country china = Country.builder().name("china").capitol("beijing").currency("RMB").population(140).build();
    logger.info(china.toString());

    // init
    CacheUtils.plainClient();

    // test country
    Region<String, String> region = CacheUtils.getRegion("Test");

    region.put("test", "test value is 好多了");

//    country.put(china.getName(), china);
    region.keySetOnServer().forEach(k -> logger.info(region.get(k).toString()));

  }

}
