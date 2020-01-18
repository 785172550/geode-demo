package com.ken.listener;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.Operation;
import org.apache.geode.cache.asyncqueue.AsyncEvent;
import org.apache.geode.cache.asyncqueue.AsyncEventListener;
import org.apache.geode.cache.wan.EventSequenceID;
import org.apache.geode.internal.logging.LogService;
import org.apache.geode.pdx.JSONFormatter;
import org.apache.geode.pdx.PdxInstance;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Properties;

// create async-event-queue --id=example-async-event-queue \
//        --parallel=false \
//        --enable-batch-conflation=false \
//        --batch-size=1 \
//        --batch-time-interval=0 \
//        --listener=com.ken.listener.MyAsyncEventListener

// list async-event-queues

// create region --name=incoming-region --type=REPLICATE --async-event-queue-id=example-async-event-queue
public class MyAsyncEventListener implements AsyncEventListener {
  private static final Logger logger = LogService.getLogger();

  @Override
  public boolean processEvents(List<AsyncEvent> events) {
    logger.info("Size of List<AsyncEvent> = {}", events.size());

    events.forEach(e -> {
      Object key = e.getKey();

      Operation operation = e.getOperation();
      boolean expiration = operation.isExpiration();
      boolean destroy = operation.isDestroy();

      EventSequenceID eventSequenceID = e.getEventSequenceID();
      boolean possibleDuplicate = e.getPossibleDuplicate();
      PdxInstance instance = (PdxInstance) e.getDeserializedValue();

//      Object object = instance.getObject();
//      String json = JSONFormatter.toJSON(instance);

    });
    return false;
  }

  @Override
  public void initialize(Cache cache, Properties properties) {

  }

  @Override
  public void close() {

  }
}
