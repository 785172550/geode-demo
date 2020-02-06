package com.ken;

import com.ken.util.CacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.geode.cache.query.SelectResults;

@SuppressWarnings("unchecked")
@Slf4j
public class LuceneIndexTest {
  public static void main(String[] args) {
    // test lucene index
    SelectResults selectResults =
            CacheUtils.executeQuery("select e.firstName, e.lastName from /EmployeeData e", CacheUtils.CLIENT_POOL);
    log.info("--- query result -----------");
    selectResults.asList().forEach(it -> log.info(it.toString()));
  }
}
