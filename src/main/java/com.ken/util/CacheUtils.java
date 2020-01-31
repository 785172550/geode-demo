package com.ken.util;

import com.ken.security.AuthenticationUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.geode.cache.CacheListener;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.query.*;
import org.apache.geode.distributed.ConfigurationProperties;
import org.apache.geode.pdx.ReflectionBasedAutoSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

import static org.apache.geode.distributed.ConfigurationProperties.DURABLE_CLIENT_ID;
import static org.apache.geode.distributed.ConfigurationProperties.DURABLE_CLIENT_TIMEOUT;

public class CacheUtils implements AutoCloseable {
  private static Logger logger = LoggerFactory.getLogger(CacheUtils.class);

  private static int LOCATOR = 10334;
  private static ClientCache clientCache = null;
  private static String CLIENT_XML_FILE = "CLIENT_XML_FILE";
  public static String CLIENT_POOL = "clientPool";

  private static String clientFile = System.getProperty(CLIENT_XML_FILE, "client.xml");

  private static ClientCache createClientCache(String filePath) {
    if (StringUtils.isEmpty(filePath)) {
      filePath = clientFile;
    }
    Properties props = new Properties();
    props.setProperty("security-username", "superUser");
    props.setProperty("security-client-auth-init", AuthenticationUtil.class.getName());

    clientCache = new ClientCacheFactory(props)
            .set("log-level", "WARN")
            .set(ConfigurationProperties.CACHE_XML_FILE, filePath)
            .create();
    return clientCache;
  }

  /**
   * @Description: load clientFile for a given filePath @Param: String filePath
   * @return: ClientCache
   */
  public static ClientCache getOrCreateClient(String filePath) {
    if (clientCache != null) {
      return clientCache;
    }
    try {
      clientCache = ClientCacheFactory.getAnyInstance();
    } catch (Exception e) {
      clientCache = createClientCache(filePath);
    }
    return clientCache;
  }

  /**
   * @Description: use clientFile
   * @return: ClientCache
   */
  public static ClientCache getOrCreateClient() {
    if (clientCache != null) {
      return clientCache;
    }
    try {
      clientCache = ClientCacheFactory.getAnyInstance();
    } catch (Exception e) {
      clientCache = createClientCache(null);
    }
    return clientCache;
  }

  public static ClientCache plainClient() {
    clientCache = new ClientCacheFactory()
            .set("log-level", "WARN")
            .set(ConfigurationProperties.CACHE_XML_FILE, clientFile)
            .create();
    return clientCache;
  }

  public static ClientCache durableClient() {
    clientCache = new ClientCacheFactory()
            .set("log-level", "WARN")
            .set(ConfigurationProperties.CACHE_XML_FILE, clientFile)
            // Provide a unique identifier for this client's durable subscription message queue
            .set(DURABLE_CLIENT_ID, "1")
            // Provide a timeout in seconds for how long the server will wait for the client to reconnect.
            // If this property isn't set explicitly, it defaults to 300 seconds.
            .set(DURABLE_CLIENT_TIMEOUT, "200")
            // This is required so the client can register interest for all keys on this durable client
            .setPoolSubscriptionEnabled(true)
            .create();
    return clientCache;
  }

  /**
   * @Param: String regionName
   * @return: org.apache.geode.cache.Region
   */
  public static <K, V> Region<K, V> getRegion(final String regionName) {
    return getOrCreateClient().getRegion(regionName);
  }

  /**
   * @Param: String queryString, String poolName
   * @return: org.apache.geode.cache.query.SelectResults
   */
  public static SelectResults executeQuery(final String queryString, final String poolName) {
    logger.debug("oql: {}", queryString);
    SelectResults results = null;
    try {
      results = (SelectResults) getOrCreateClient().getQueryService(poolName).newQuery(queryString).execute();
    } catch (FunctionDomainException | TypeMismatchException | NameResolutionException
            | QueryInvocationTargetException e) {

      logger.error("Encounted Exception while executing query: {}", queryString, e);
    }
    return results;
  }

  /**
   * @Param: String queryString, String poolName, int retry
   * @return: org.apache.geode.cache.query.SelectResults
   */
  public static SelectResults executeQueryRetry(
          final String queryString, final String poolName, int retry) {
    logger.debug("oql: {}", queryString);
    int count = 0;
    while (count < retry) {
      try {
        SelectResults results =
                (SelectResults)
                        getOrCreateClient().getQueryService(poolName).newQuery(queryString).execute();
        if (results != null) {
          return results;
        }
      } catch (FunctionDomainException
              | TypeMismatchException
              | NameResolutionException
              | QueryInvocationTargetException e) {
        logger.info(
                String.format(
                        "Encounted Exception while executing query: %s , retry count: %s",
                        queryString, count),
                e);
        count++;
      }
    }
    return null;
  }

  public static CqQuery addCq(CqAttributes attributes, String cqName, String queryStr, String poolName)
          throws CqException, CqExistsException, RegionNotFoundException {

    CqQuery cqTracker = getOrCreateClient().getQueryService(poolName).newCq(cqName, queryStr, attributes);
    cqTracker.execute();
    return cqTracker;
  }


  /**
   * @Description: close client cache, keepAlive = false
   */
  @Override
  public void close() {
    clientCache.close(false);
    logger.info("clientCache closed");
  }

  // ---------------- currently no use case --------------------
  public static ClientCache createClientCache(int locatorPort, String... classPatterns) {
    if (locatorPort == 0) {
      locatorPort = LOCATOR;
    }
    clientCache = new ClientCacheFactory()
            .addPoolLocator("127.0.0.1", locatorPort)
            .set("log-level", "WARN")
            .setPdxSerializer(new ReflectionBasedAutoSerializer(false, classPatterns))
            .create();

    return clientCache;
  }

  public static <K, V> Region<K, V> createRegion(
          ClientCache cache, String regionName, CacheListener<K, V> listener) {
    Region<K, V> region;
    if (listener == null) {
      region = cache.<K, V>createClientRegionFactory(ClientRegionShortcut.PROXY).create(regionName);
    } else {
      region = cache.<K, V>createClientRegionFactory(ClientRegionShortcut.PROXY)
              .addCacheListener(listener)
              .create(regionName);
    }
    return region;
  }
}
