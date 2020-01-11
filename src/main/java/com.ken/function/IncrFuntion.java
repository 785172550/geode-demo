package com.ken.function;

import com.ken.transcation.Example;
import org.apache.geode.cache.CacheTransactionManager;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionContext;
import org.apache.geode.cache.execute.RegionFunctionContext;

public class IncrFuntion implements Function {
  @Override
  public boolean hasResult() {
    return true;
  }

  @Override
  public void execute(FunctionContext context) {
    RegionFunctionContext regionContext = (RegionFunctionContext) context;
    Region<String, Integer> region = regionContext.getDataSet();

    CacheTransactionManager cacheTransactionManager = regionContext.getCache().getCacheTransactionManager();
    cacheTransactionManager.begin();
    final Integer oldValue = region.get(Example.KEY);
    final Integer newValue = oldValue + 1;
    region.put(Example.KEY, newValue);
    cacheTransactionManager.commit();

    context.getResultSender().lastResult(newValue);
  }

  @Override
  public String getId() {
    return IncrFuntion.class.getCanonicalName();
  }
}
