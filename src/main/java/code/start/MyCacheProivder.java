package code.start;

import com.sun.jna.Platform;
import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.distributed.ServerLauncher;
import org.apache.geode.distributed.ServerLauncherCacheProvider;
import org.apache.geode.internal.cache.CacheConfig;
import org.apache.geode.internal.cache.MyCacheImpl;

import java.util.Properties;

public class MyCacheProivder implements ServerLauncherCacheProvider {
  @Override
  public Cache createCache(Properties gemfireProperties, ServerLauncher serverLauncher) {
    final CacheConfig cacheConfig = serverLauncher.getCacheConfig();
    final CacheFactory cacheFactory = new CacheFactory(gemfireProperties);
//    MyCacheImpl()

    if (cacheConfig.pdxPersistentUserSet) {
      cacheFactory.setPdxPersistent(cacheConfig.isPdxPersistent());
    }

    if (cacheConfig.pdxDiskStoreUserSet) {
      cacheFactory.setPdxDiskStore(cacheConfig.getPdxDiskStore());
    }

    if (cacheConfig.pdxIgnoreUnreadFieldsUserSet) {
      cacheFactory.setPdxIgnoreUnreadFields(cacheConfig.getPdxIgnoreUnreadFields());
    }

    if (cacheConfig.pdxReadSerializedUserSet) {
      cacheFactory.setPdxReadSerialized(cacheConfig.isPdxReadSerialized());
    }

    if (cacheConfig.pdxSerializerUserSet) {
      cacheFactory.setPdxSerializer(cacheConfig.getPdxSerializer());
    }

    return cacheFactory.create();
  }

}
