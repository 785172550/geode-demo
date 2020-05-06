package com.ken.function;

import com.ken.util.CacheUtils;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.cache.execute.ResultCollector;

public class FunctionTest {
  public static void main(String[] args) {
    ClientCache clientCache = CacheUtils.plainClient();

    ResultCollector rc = FunctionService.onMember("test")
//            .setArguments(params)

            .execute(IncrFuntion.class.getSimpleName());
    Object result = rc.getResult();
  }
}
