package com.ken.handler;

import org.apache.geode.cache.*;

import java.util.Properties;

public class ExpirationHandler implements CustomExpiry, Declarable {
  private Cache cache;
  private int numOfDay = 0;
  private int numOfSec = 0;
  private ExpirationAttributes CUSTOM_EXPIRY;

  @Override
  public ExpirationAttributes getExpiry(Region.Entry entry) {
    return CUSTOM_EXPIRY;
  }

  @Override
  public void close() {

  }

  @Override
  public void initialize(Cache cache, Properties properties) {
    this.cache = cache;
    if (properties.get("numOfDay") != null) {
      numOfDay = Integer.parseInt(properties.getProperty("numOfDay"));
      CUSTOM_EXPIRY = new ExpirationAttributes(numOfDay * 60 * 60 * 25, ExpirationAction.DESTROY);
    } else if (properties.get("numOfSec") != null) {
      numOfSec = Integer.parseInt(properties.getProperty("numOfSec"));
      CUSTOM_EXPIRY = new ExpirationAttributes(numOfSec, ExpirationAction.DESTROY);
    } else {
      // default 120 sec to expiration
      CUSTOM_EXPIRY = new ExpirationAttributes(120, ExpirationAction.DESTROY);
    }
  }
}
