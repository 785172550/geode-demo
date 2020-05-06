package com.ken.function;

import com.ken.transcation.Example;
import org.apache.geode.cache.*;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionContext;
import org.apache.geode.cache.execute.RegionFunctionContext;

import java.util.Properties;

public class IncrFuntion implements Function, Declarable {

  @Override
  public void initialize(Cache cache, Properties properties) {
  }

  @Override
  public boolean hasResult() {
    return true;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void execute(FunctionContext context) {
    RegionFunctionContext regionContext = (RegionFunctionContext) context;
    Region<String, Integer> region = regionContext.getDataSet();

    CacheTransactionManager txManager = regionContext.getCache().getCacheTransactionManager();

    boolean commitConflict = true;
    int newValue = 0;
    while (commitConflict) {
      try {
        commitConflict = false;
        txManager.begin();
        final int oldValue = region.get(Example.KEY);
        newValue = oldValue + 1;
        region.put(Example.KEY, newValue);
        txManager.commit();
      } catch (CommitConflictException conflict) {
        // retry transaction, as another request on this same key succeeded,
        // so this transaction attempt failed
        commitConflict = true;
      } finally {
        // All other exceptions will be handled by the caller; however,
        // any exception thrown by a method other than commit() needs
        // to do a rollback to avoid leaking the transaction state.
        if (txManager.exists()) {
          txManager.rollback();
        }
      }
    }
    context.getResultSender().lastResult(newValue);
  }

  @Override
  public String getId() {
    return IncrFuntion.class.getSimpleName();
  }

  /**
   * Returning true causes this function to execute on the server
   * that holds the primary bucket for the given key. It can save a
   * network hop from the secondary to the primary.
   */
  @Override
  public boolean optimizeForWrite() {
    return true;
  }
}
