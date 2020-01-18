package com.ken;

import com.ken.domain.Country;
import com.ken.util.CacheUtils;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.query.SelectResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unchecked")
public class TestConnection {
  private static Logger logger = LoggerFactory.getLogger(TestConnection.class);

  public static void main(String[] args) {
    System.setProperty("log4j.configurationFile", "config/log4j2.xml");

    Country china = Country.builder()
            .name("china").capitol("beijing").currency("RMB").population(140).build();
    logger.info(china.toString());

    // init
    CacheUtils.plainClient();
    Region<String, Country> country = CacheUtils.getRegion("Country");
    country.put(china.getName(), china);

    SelectResults selectResults =
            CacheUtils.executeQuery("select e.firstName, e.lastName from /EmployeeData e", CacheUtils.CLIENT_POOL);
    logger.info("--- query result -----------");
    selectResults.asList().forEach(it -> logger.info(it.toString()));
  }
}
