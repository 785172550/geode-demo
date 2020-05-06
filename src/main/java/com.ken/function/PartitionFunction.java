package com.ken.function;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.Declarable;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionContext;
import org.apache.geode.cache.execute.RegionFunctionContext;
import org.apache.geode.cache.partition.PartitionRegionHelper;
import org.apache.geode.cache.query.*;

import java.util.Properties;

public class PartitionFunction implements Function, Declarable {
  @Override
  public void execute(FunctionContext context) {
    RegionFunctionContext fc = (RegionFunctionContext) context;
    String whereClause = fc.getArguments().toString();
    Region<Object, Object> region = PartitionRegionHelper.getLocalDataForContext(fc);
    SelectResults<Object> query = null;
    try {
      query = region.query(whereClause);
    } catch (FunctionDomainException | TypeMismatchException | NameResolutionException | QueryInvocationTargetException e) {
      e.printStackTrace();
    }
    fc.getResultSender().sendResult(query);

  }

  @Override
  public void initialize(Cache cache, Properties properties) {

  }

  @Override
  public String getId() {
    return PartitionFunction.class.getSimpleName();
  }

  /**
   * Returning true causes this function to execute on the server
   * that holds the primary bucket for the given key. It can save a
   * network hop from the secondary to the primary.
   */
  @Override
  public boolean optimizeForWrite() {
    return false;
  }
}
