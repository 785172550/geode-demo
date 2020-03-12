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

public class MRFunction implements Function, Declarable {
  @Override
  public void execute(FunctionContext context) {
    RegionFunctionContext fc = (RegionFunctionContext) context;
    String sWhere = fc.getArguments().toString();
    Region<Object, Object> region = PartitionRegionHelper.getLocalDataForContext(fc);
    SelectResults<Object> query = null;
    try {
      query = region.query("");
    } catch (FunctionDomainException | TypeMismatchException | NameResolutionException | QueryInvocationTargetException e) {
      e.printStackTrace();
    }
    fc.getResultSender().sendResult(query);

  }

  @Override
  public void initialize(Cache cache, Properties properties) {

  }
}
