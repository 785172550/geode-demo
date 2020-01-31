package com.ken.cq;

import org.apache.geode.cache.Operation;
import org.apache.geode.cache.query.CqEvent;
import org.apache.geode.cache.query.CqListener;

public class CqEventListener implements CqListener {
  @Override
  public void onEvent(CqEvent aCqEvent) {
    System.out.println("------  event -----------");
    Operation queryOperation = aCqEvent.getQueryOperation();
    Object key = aCqEvent.getKey();
    Object newValue = aCqEvent.getNewValue();

    if (queryOperation.isExpiration()) {
      System.out.println("----  Expiration Operation : " + key + ":" + newValue);
    } else if (queryOperation.isUpdate()) {
      System.out.println("------- Updated Operation : " + key + ":" + newValue);
    } else if (queryOperation.isCreate()) {
      System.out.println("------- Created Operation : " + key + ":" + newValue);
    }
  }

  @Override
  public void onError(CqEvent aCqEvent) {

  }

  @Override
  public void close() {
    System.out.println("--- close ---");
  }
}
