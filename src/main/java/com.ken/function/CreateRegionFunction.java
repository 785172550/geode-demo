package com.ken.function;

import com.ken.listener.CreateRegionCacheListener;
import org.apache.geode.cache.*;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionContext;

import java.util.Properties;

public class CreateRegionFunction implements Function, Declarable {

  private Cache cache;
  private Region<String, RegionAttributes> regionAttributesMetadataRegion;
  private static final String REGION_ATTRIBUTES_METADATA_REGION = "_regionAttributesMetadata";

  public enum Status {SUCCESSFUL, UNSUCCESSFUL, ALREADY_EXISTS}

  @Override
  public void initialize(Cache cache, Properties properties) {
    this.cache = cache;
    this.regionAttributesMetadataRegion = createRegionAttributesMetadataRegion();
  }

  @SuppressWarnings("unchecked")
  public void execute(FunctionContext context) {
    Object[] arguments = (Object[]) context.getArguments();
    String regionName = (String) arguments[0];
    RegionAttributes attributes = (RegionAttributes) arguments[1];

    // Create or retrieve region
    Status status = createOrRetrieveRegion(regionName, attributes);

    // Return status
    context.getResultSender().lastResult(status);
  }

  private Status createOrRetrieveRegion(String regionName,
                                        RegionAttributes attributes) {
    Status status = Status.SUCCESSFUL;
    Region region = this.cache.getRegion(regionName);

    if (region == null) {
      // Put the attributes into the metadata region. The afterCreate call will
      // actually create the region.
      this.regionAttributesMetadataRegion.put(regionName, attributes);

      // Retrieve the region after creating it
      region = this.cache.getRegion(regionName);
      if (region == null) {
        status = Status.UNSUCCESSFUL;
      }

    } else {
      status = Status.ALREADY_EXISTS;
    }
    return status;
  }

  private Region<String, RegionAttributes> createRegionAttributesMetadataRegion() {
    Region<String, RegionAttributes> metaRegion = this.cache.getRegion(REGION_ATTRIBUTES_METADATA_REGION);
    if (metaRegion == null) {
      RegionFactory<String, RegionAttributes> factory = this.cache.createRegionFactory();
      factory.setDataPolicy(DataPolicy.REPLICATE);
      factory.setScope(Scope.DISTRIBUTED_ACK);

      CreateRegionCacheListener regionCacheListener = new CreateRegionCacheListener();
      regionCacheListener.initialize(cache, new Properties());
      factory.addCacheListener(regionCacheListener);
      metaRegion = factory.create(REGION_ATTRIBUTES_METADATA_REGION);
    }
    return metaRegion;
  }

  @Override
  public String getId() {
    return CreateRegionFunction.class.getSimpleName();
  }

  public boolean optimizeForWrite() {
    return false;
  }

  public boolean isHA() {
    return true;
  }

  public boolean hasResult() {
    return true;
  }
}