package com.ken;

import com.ken.domain.Country;
import com.ken.domain.School;
import com.ken.util.CacheUtils;
import org.apache.geode.cache.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*

IMPORT com.ken.domain.School;
select slist from /Country c, c.schools slist TYPE School;

 */
public class TestConnection {
  private static Logger logger = LoggerFactory.getLogger(TestConnection.class);

  public static void main(String[] args) {
    System.setProperty("log4j.configurationFile", "config/log4j2.xml");

    School school_addr = School.builder().address("school_addr").area(1).name("No. 1").build();
    Country china = Country.builder().name("china").capitol("beijing").currency("RMB").population(140)
            .schoolAdd(school_addr).build();
    logger.info(china.toString());

    // init
    CacheUtils.plainClient();

    // test country
    Region<String, Country> region = CacheUtils.getRegion("Country");

//    region.put("test", "test value is 好多了");
//    region.get("dd");

    region.put(china.getName(), china);
    region.keySetOnServer().forEach(k -> logger.info(region.get(k).toString()));

  }

}
