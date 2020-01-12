package org.apache.geode.internal.cache;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.lang3.StringUtils;
import org.apache.geode.*;
import org.apache.geode.admin.internal.SystemMemberCacheEventProcessor;
import org.apache.geode.annotations.internal.MakeNotStatic;
import org.apache.geode.annotations.internal.MutableForTesting;
import org.apache.geode.cache.TimeoutException;
import org.apache.geode.cache.*;
import org.apache.geode.cache.asyncqueue.AsyncEventQueue;
import org.apache.geode.cache.asyncqueue.AsyncEventQueueFactory;
import org.apache.geode.cache.asyncqueue.internal.AsyncEventQueueFactoryImpl;
import org.apache.geode.cache.asyncqueue.internal.AsyncEventQueueImpl;
import org.apache.geode.cache.client.*;
import org.apache.geode.cache.client.internal.ClientMetadataService;
import org.apache.geode.cache.client.internal.ClientRegionFactoryImpl;
import org.apache.geode.cache.client.internal.InternalClientCache;
import org.apache.geode.cache.client.internal.PoolImpl;
import org.apache.geode.cache.control.ResourceManager;
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.cache.query.QueryService;
import org.apache.geode.cache.query.internal.DefaultQueryService;
import org.apache.geode.cache.query.internal.InternalQueryService;
import org.apache.geode.cache.query.internal.QueryMonitor;
import org.apache.geode.cache.query.internal.cq.CqService;
import org.apache.geode.cache.query.internal.cq.CqServiceProvider;
import org.apache.geode.cache.server.CacheServer;
import org.apache.geode.cache.snapshot.CacheSnapshotService;
import org.apache.geode.cache.util.GatewayConflictResolver;
import org.apache.geode.cache.wan.GatewayReceiver;
import org.apache.geode.cache.wan.GatewayReceiverFactory;
import org.apache.geode.cache.wan.GatewaySender;
import org.apache.geode.cache.wan.GatewaySenderFactory;
import org.apache.geode.distributed.*;
import org.apache.geode.distributed.internal.*;
import org.apache.geode.distributed.internal.locks.DLockService;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.i18n.LogWriterI18n;
import org.apache.geode.internal.*;
import org.apache.geode.internal.cache.LocalRegion.InitializationLevel;
import org.apache.geode.internal.cache.backup.BackupService;
import org.apache.geode.internal.cache.control.InternalResourceManager;
import org.apache.geode.internal.cache.control.InternalResourceManager.ResourceType;
import org.apache.geode.internal.cache.control.ResourceAdvisor;
import org.apache.geode.internal.cache.event.EventTrackerExpiryTask;
import org.apache.geode.internal.cache.eviction.HeapEvictor;
import org.apache.geode.internal.cache.eviction.OffHeapEvictor;
import org.apache.geode.internal.cache.execute.util.FindRestEnabledServersFunction;
import org.apache.geode.internal.cache.extension.Extensible;
import org.apache.geode.internal.cache.extension.ExtensionPoint;
import org.apache.geode.internal.cache.extension.SimpleExtensionPoint;
import org.apache.geode.internal.cache.ha.HARegionQueue;
import org.apache.geode.internal.cache.locks.TXLockService;
import org.apache.geode.internal.cache.partitioned.RedundancyAlreadyMetException;
import org.apache.geode.internal.cache.persistence.PersistentMemberID;
import org.apache.geode.internal.cache.persistence.PersistentMemberManager;
import org.apache.geode.internal.cache.snapshot.CacheSnapshotServiceImpl;
import org.apache.geode.internal.cache.tier.Acceptor;
import org.apache.geode.internal.cache.tier.sockets.*;
import org.apache.geode.internal.cache.wan.AbstractGatewaySender;
import org.apache.geode.internal.cache.wan.GatewaySenderAdvisor;
import org.apache.geode.internal.cache.wan.GatewaySenderQueueEntrySynchronizationListener;
import org.apache.geode.internal.cache.wan.WANServiceProvider;
import org.apache.geode.internal.cache.wan.parallel.ParallelGatewaySenderQueue;
import org.apache.geode.internal.cache.xmlcache.*;
import org.apache.geode.internal.concurrent.ConcurrentHashSet;
import org.apache.geode.internal.config.ClusterConfigurationNotAvailableException;
import org.apache.geode.internal.jndi.JNDIInvoker;
import org.apache.geode.internal.jta.TransactionManagerImpl;
import org.apache.geode.internal.lang.ThrowableUtils;
import org.apache.geode.internal.logging.InternalLogWriter;
import org.apache.geode.internal.logging.LogService;
import org.apache.geode.internal.logging.LoggingExecutors;
import org.apache.geode.internal.logging.LoggingThread;
import org.apache.geode.internal.monitoring.ThreadsMonitoring;
import org.apache.geode.internal.net.SSLConfigurationFactory;
import org.apache.geode.internal.net.SocketCreator;
import org.apache.geode.internal.offheap.MemoryAllocator;
import org.apache.geode.internal.security.SecurableCommunicationChannel;
import org.apache.geode.internal.security.SecurityService;
import org.apache.geode.internal.security.SecurityServiceFactory;
import org.apache.geode.internal.sequencelog.SequenceLoggerImpl;
import org.apache.geode.internal.shared.StringPrintWriter;
import org.apache.geode.internal.tcp.ConnectionTable;
import org.apache.geode.internal.util.BlobHelper;
import org.apache.geode.internal.util.concurrent.FutureResult;
import org.apache.geode.lang.Identifiable;
import org.apache.geode.management.internal.JmxManagerAdvisee;
import org.apache.geode.management.internal.JmxManagerAdvisor;
import org.apache.geode.management.internal.RestAgent;
import org.apache.geode.management.internal.beans.ManagementListener;
import org.apache.geode.management.internal.configuration.domain.Configuration;
import org.apache.geode.management.internal.configuration.messages.ConfigurationResponse;
import org.apache.geode.pdx.*;
import org.apache.geode.pdx.internal.*;
import org.apache.logging.log4j.Logger;

import javax.naming.Context;
import javax.transaction.TransactionManager;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;
import static org.apache.geode.distributed.internal.InternalDistributedSystem.getAnyInstance;

public class MyCacheImpl implements InternalCache, InternalClientCache, HasCachePerfStats,
        DistributionAdvisee, CacheTime {
  private static final Logger logger = LogService.getLogger();

  /** The default number of seconds to wait for a distributed lock */
  public static final int DEFAULT_LOCK_TIMEOUT =
          Integer.getInteger(DistributionConfig.GEMFIRE_PREFIX + "Cache.defaultLockTimeout", 60);

  /**
   * The default duration (in seconds) of a lease on a distributed lock
   */
  public static final int DEFAULT_LOCK_LEASE =
          Integer.getInteger(DistributionConfig.GEMFIRE_PREFIX + "Cache.defaultLockLease", 120);

  /** The default "copy on read" attribute value */
  public static final boolean DEFAULT_COPY_ON_READ = false;

  /**
   * getcachefor
   * The default amount of time to wait for a {@code netSearch} to complete
   */
  public static final int DEFAULT_SEARCH_TIMEOUT =
          Integer.getInteger(DistributionConfig.GEMFIRE_PREFIX + "Cache.defaultSearchTimeout", 300);

  /**
   * The {@code CacheLifecycleListener} s that have been registered in this VM
   */
  @MakeNotStatic
  private static final Set<CacheLifecycleListener> cacheLifecycleListeners =
          new CopyOnWriteArraySet<>();

  /**
   * Name of the default pool.
   */
  public static final String DEFAULT_POOL_NAME = "DEFAULT";


  /**
   * The number of threads that the QueryMonitor will use to mark queries as cancelled
   * (see QueryMonitor class for reasons why a query might be cancelled).
   * That processing is very efficient, so we don't foresee needing to raise this above 1.
   */
  private static final int QUERY_MONITOR_THREAD_POOL_SIZE = 1;

  /**
   * If true then when a delta is applied the size of the entry value will be recalculated. If false
   * (the default) then the size of the entry value is unchanged by a delta application. Not a final
   * so that tests can change this value.
   *
   * TODO: move or static or encapsulate with interface methods
   *
   * @since GemFire h****** 6.1.2.9
   */
  @MutableForTesting
  static boolean DELTAS_RECALCULATE_SIZE =
          Boolean.getBoolean(DistributionConfig.GEMFIRE_PREFIX + "DELTAS_RECALCULATE_SIZE");

  private static final int EVENT_QUEUE_LIMIT =
          Integer.getInteger(DistributionConfig.GEMFIRE_PREFIX + "Cache.EVENT_QUEUE_LIMIT", 4096);

  static final int EVENT_THREAD_LIMIT =
          Integer.getInteger(DistributionConfig.GEMFIRE_PREFIX + "Cache.EVENT_THREAD_LIMIT", 16);

  /**
   * System property to limit the max query-execution time. By default its turned off (-1), the time
   * is set in milliseconds.
   */
  @MutableForTesting
  public static int MAX_QUERY_EXECUTION_TIME =
          Integer.getInteger(DistributionConfig.GEMFIRE_PREFIX + "Cache.MAX_QUERY_EXECUTION_TIME", -1);

  /**
   * System property to disable query monitor even if resource manager is in use
   */
  private final boolean queryMonitorDisabledForLowMem = Boolean
          .getBoolean(DistributionConfig.GEMFIRE_PREFIX + "Cache.DISABLE_QUERY_MONITOR_FOR_LOW_MEMORY");

  /**
   * Property set to true if resource manager heap percentage is set and query monitor is required
   */
  @MakeNotStatic
  private static boolean queryMonitorRequiredForResourceManager = false;

  /** time in milliseconds */
  private static final int FIVE_HOURS = 5 * 60 * 60 * 1000;

  private static final Pattern DOUBLE_BACKSLASH = Pattern.compile("\\\\");

  private volatile ConfigurationResponse configurationResponse;

  private final InternalDistributedSystem system;

  private final DistributionManager dm;

  private final Map<String, InternalRegion> rootRegions;

  /**
   * True if this cache is being created by a ClientCacheFactory.
   */
  private final boolean isClient;

  private PoolFactory poolFactory;

  /**
   * It is not final to allow cache.xml parsing to set it.
   */
  private Pool defaultPool;

  private final ConcurrentMap<String, InternalRegion> pathToRegion = new ConcurrentHashMap<>();

  private volatile boolean isInitialized;

  volatile boolean isClosing = false; // used in Stopper inner class

  /** Amount of time (in seconds) to wait for a distributed lock */
  private int lockTimeout = DEFAULT_LOCK_TIMEOUT;

  /** Amount of time a lease of a distributed lock lasts */
  private int lockLease = DEFAULT_LOCK_LEASE;

  /** Amount of time to wait for a {@code netSearch} to complete */
  private int searchTimeout = DEFAULT_SEARCH_TIMEOUT;

  private final CachePerfStats cachePerfStats;

  /** Date on which this instances was created */
  private final Date creationDate;

  /** thread pool for event dispatching */
  private final ExecutorService eventThreadPool;

  /**
   * the list of all cache servers. CopyOnWriteArrayList is used to allow concurrent add, remove and
   * retrieval operations. It is assumed that the traversal operations on cache servers list vastly
   * outnumber the mutative operations such as add, remove.
   */
  private final List<InternalCacheServer> allCacheServers = new CopyOnWriteArrayList<>();
  /**
   * Unmodifiable view of "allCacheServers".
   */
  private final List<CacheServer> unmodifiableAllCacheServers =
          Collections.unmodifiableList(allCacheServers);

  /**
   * Controls updates to the list of all gateway senders
   *
   * @see #allGatewaySenders
   */
  private final Object allGatewaySendersLock = new Object();

  /**
   * the set of all gateway senders. It may be fetched safely (for enumeration), but updates must by
   * synchronized via {@link #allGatewaySendersLock}
   */
  private volatile Set<GatewaySender> allGatewaySenders = Collections.emptySet();

  /**
   * The list of all async event queues added to the cache. CopyOnWriteArrayList is used to allow
   * concurrent add, remove and retrieval operations.
   */
  private final Set<AsyncEventQueue> allVisibleAsyncEventQueues = new CopyOnWriteArraySet<>();

  /**
   * The list of all async event queues added to the cache. CopyOnWriteArrayList is used to allow
   * concurrent add, remove and retrieval operations.
   */
  private final Set<AsyncEventQueue> allAsyncEventQueues = new CopyOnWriteArraySet<>();

  private final AtomicReference<GatewayReceiver> gatewayReceiver = new AtomicReference<>();

  private final AtomicReference<InternalCacheServer> gatewayReceiverServer =
          new AtomicReference<>();

  /**
   * PartitionedRegion instances (for required-events notification
   */
  private final Set<PartitionedRegion> partitionedRegions = new HashSet<>();

  /**
   * Fix for 42051 This is a map of regions that are in the process of being destroyed. We could
   * potentially leave the regions in the pathToRegion map, but that would entail too many changes
   * at this point in the release. We need to know which regions are being destroyed so that a
   * profile exchange can get the persistent id of the destroying region and know not to persist
   * that ID if it receives it as part of the persistent view.
   */
  private final ConcurrentMap<String, DistributedRegion> regionsInDestroy =
          new ConcurrentHashMap<>();

  private final Object allGatewayHubsLock = new Object();

  /**
   * conflict resolver for WAN, if any
   *
   * GuardedBy {@link #allGatewayHubsLock}
   */
  private GatewayConflictResolver gatewayConflictResolver;

  /** Is this is "server" cache? */
  private boolean isServer = false;

  /** transaction manager for this cache */
  private final TXManagerImpl transactionManager;

  private RestAgent restAgent;

  private boolean isRESTServiceRunning = false;

  /** Copy on Read feature for all read operations e.g. get */
  private volatile boolean copyOnRead = DEFAULT_COPY_ON_READ;

  /** The named region attributes registered with this cache. */
  private final Map<String, RegionAttributes<?, ?>> namedRegionAttributes =
          Collections.synchronizedMap(new HashMap<>());

  /**
   * if this cache was forced to close due to a forced-disconnect, we retain a
   * ForcedDisconnectException that can be used as the cause
   */
  private boolean forcedDisconnect;

  /**
   * if this cache was forced to close due to a forced-disconnect or system failure, this keeps
   * track of the reason
   */
  volatile Throwable disconnectCause; // used in Stopper inner class

  /** context where this cache was created -- for debugging, really... */
  private Exception creationStack = null;

  /**
   * a system timer task for cleaning up old bridge thread event entries
   */
  private final EventTrackerExpiryTask recordedEventSweeper;

  private final TombstoneService tombstoneService;

  /**
   * DistributedLockService for PartitionedRegions. Remains null until the first PartitionedRegion
   * is created. Destroyed by GemFireCache when closing the cache. Protected by synchronization on
   * this GemFireCache.
   *
   * GuardedBy prLockServiceLock
   */
  private DistributedLockService prLockService;

  /**
   * lock used to access prLockService
   */
  private final Object prLockServiceLock = new Object();

  /**
   * DistributedLockService for GatewaySenders. Remains null until the first GatewaySender is
   * created. Destroyed by GemFireCache when closing the cache.
   *
   * GuardedBy gatewayLockServiceLock
   */
  private volatile DistributedLockService gatewayLockService;

  /**
   * Lock used to access gatewayLockService
   */
  private final Object gatewayLockServiceLock = new Object();

  private final InternalResourceManager resourceManager;

  private final BackupService backupService;

  private HeapEvictor heapEvictor = null;

  private OffHeapEvictor offHeapEvictor = null;

  private final Object heapEvictorLock = new Object();

  private final Object offHeapEvictorLock = new Object();

  private ResourceEventsListener resourceEventsListener;

  /**
   * Enabled when CacheExistsException issues arise in debugging
   *
   * @see #creationStack
   */
  private static final boolean DEBUG_CREATION_STACK = false;

  private volatile QueryMonitor queryMonitor;

  private final Object queryMonitorLock = new Object();

  private final PersistentMemberManager persistentMemberManager;

  private final ClientMetadataService clientMetadataService;

  private final AtomicBoolean isShutDownAll = new AtomicBoolean();
  private final CountDownLatch shutDownAllFinished = new CountDownLatch(1);

  private final ResourceAdvisor resourceAdvisor;
  private final JmxManagerAdvisor jmxAdvisor;

  private final int serialNumber;

  private final TXEntryStateFactory txEntryStateFactory;

  private final CacheConfig cacheConfig;

  private final DiskStoreMonitor diskMonitor;

  /**
   * Stores the properties used to initialize declarables.
   */
  private final Map<Declarable, Properties> declarablePropertiesMap = new ConcurrentHashMap<>();

  /** {@link PropertyResolver} to resolve ${} type property strings */
  private final PropertyResolver resolver;

  private static final boolean XML_PARAMETERIZATION_ENABLED =
          !Boolean.getBoolean(DistributionConfig.GEMFIRE_PREFIX + "xml.parameterization.disabled");

  /**
   * {@link ExtensionPoint} support.
   *
   * @since GemFire 8.1
   */
  private final SimpleExtensionPoint<Cache> extensionPoint = new SimpleExtensionPoint<>(this, this);

  private final CqService cqService;

  private final Set<RegionListener> regionListeners = new ConcurrentHashSet<>();

  private final Map<Class<? extends CacheService>, CacheService> services = new HashMap<>();

  private final SecurityService securityService;

  private final Set<RegionEntrySynchronizationListener> synchronizationListeners =
          new ConcurrentHashSet<>();

  private final ClusterConfigurationLoader ccLoader = new ClusterConfigurationLoader();

  private Optional<HttpService> httpService = Optional.ofNullable(null);

  private final MeterRegistry meterRegistry;
  private final Set<MeterRegistry> meterSubregistries;

  static {
    // this works around jdk bug 6427854, reported in ticket #44434
    String propertyName = "sun.nio.ch.bugLevel";
    String value = System.getProperty(propertyName);
    if (value == null) {
      System.setProperty(propertyName, "");
    }
  }

  /**
   * Invokes mlockall(). Locks all pages mapped into the address space of the calling process. This
   * includes the pages of the code, data and stack segment, as well as shared libraries, user space
   * kernel data, shared memory, and memory-mapped files. All mapped pages are guaranteed to be
   * resident in RAM when the call returns successfully; the pages are guaranteed to stay in RAM
   * until later unlocked.
   *
   * @param flags MCL_CURRENT 1 - Lock all pages which are currently mapped into the address space
   *        of the process.
   *
   *        MCL_FUTURE 2 - Lock all pages which will become mapped into the address space of the
   *        process in the future. These could be for instance new pages required by a growing heap
   *        and stack as well as new memory mapped files or shared memory regions.
   *
   * @return 0 if success, non-zero if error and errno set
   */
  private static native int mlockall(int flags);

  public static void lockMemory() {
    try {
      Native.register(Platform.C_LIBRARY_NAME);
      int result = mlockall(1);
      if (result == 0) {
        return;
      }
    } catch (Throwable t) {
      throw new IllegalStateException("Error trying to lock memory", t);
    }

    int lastError = Native.getLastError();
    String message = "mlockall failed: " + lastError;
    if (lastError == 1 || lastError == 12) { // EPERM || ENOMEM
      message = "Unable to lock memory due to insufficient free space or privileges.  "
              + "Please check the RLIMIT_MEMLOCK soft resource limit (ulimit -l) and "
              + "increase the available memory if needed";
    }
    throw new IllegalStateException(message);
  }

  /**
   * This is for debugging cache-open issues (esp. {@link CacheExistsException})
   */
  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append("GemFireCache[");
    sb.append("id = ").append(System.identityHashCode(this));
    sb.append("; isClosing = ").append(isClosing);
    sb.append("; isShutDownAll = ").append(isCacheAtShutdownAll());
    sb.append("; created = ").append(creationDate);
    sb.append("; server = ").append(isServer);
    sb.append("; copyOnRead = ").append(copyOnRead);
    sb.append("; lockLease = ").append(lockLease);
    sb.append("; lockTimeout = ").append(lockTimeout);
    if (creationStack != null) {
      // TODO: eliminate anonymous inner class and maybe move this to ExceptionUtils
      sb.append(System.lineSeparator()).append("Creation context:").append(System.lineSeparator());
      OutputStream os = new OutputStream() {
        @Override
        public void write(int i) {
          sb.append((char) i);
        }
      };
      PrintStream ps = new PrintStream(os);
      creationStack.printStackTrace(ps);
    }
    sb.append("]");
    return sb.toString();
  }

  /** Map of Futures used to track Regions that are being reinitialized */
  private final ConcurrentMap<String, FutureResult<InternalRegion>> reinitializingRegions =
          new ConcurrentHashMap<>();

  /**
   * Returns the last created instance of GemFireCache
   *
   * @deprecated use DM.getCache instead
   */
  @Deprecated
  public static MyCacheImpl getInstance() {
    InternalDistributedSystem system = InternalDistributedSystem.getAnyInstance();
    if (system == null) {
      return null;
    }
    MyCacheImpl cache = (MyCacheImpl) system.getCache();
    if (cache == null) {
      return null;
    }

    if (cache.isClosing) {
      return null;
    }

    return cache;

  }

  /**
   * Returns an existing instance. If a cache does not exist throws a cache closed exception.
   *
   * @return the existing cache
   * @throws CacheClosedException if an existing cache can not be found.
   * @deprecated use DM.getExistingCache instead.
   */
  @Deprecated
  public static MyCacheImpl getExisting() {
    final MyCacheImpl result = getInstance();
    if (result != null && !result.isClosing) {
      return result;
    }
    if (result != null) {
      throw result.getCacheClosedException(
              "The cache has been closed.");
    }
    throw new CacheClosedException(
            "A cache has not yet been created.");
  }

  /**
   * Returns an existing instance. If a cache does not exist throws an exception.
   *
   * @param reason the reason an existing cache is being requested.
   * @return the existing cache
   * @throws CacheClosedException if an existing cache can not be found.
   * @deprecated use DM.getExistingCache instead.
   */
  @Deprecated
  public static MyCacheImpl getExisting(String reason) {
    MyCacheImpl result = getInstance();
    if (result == null) {
      throw new CacheClosedException(reason);
    }
    return result;
  }

  /**
   * Pdx is allowed to obtain the cache even while it is being closed
   *
   * @deprecated Rather than fishing for a cache with this static method, use a cache that is passed
   *             in to your method.
   */
  @Deprecated
  public static MyCacheImpl getForPdx(String reason) {

    InternalDistributedSystem system = getAnyInstance();
    if (system == null) {
      throw new CacheClosedException(reason);
    }
    MyCacheImpl cache = (MyCacheImpl) system.getCache();
    if (cache == null) {
      throw new CacheClosedException(reason);
    }

    return cache;
  }

  /**
   * Creates a new instance of GemFireCache and populates it according to the {@code cache.xml}, if
   * appropriate.
   *
   * Currently only unit tests set the typeRegistry parameter to a non-null value
   */
  public MyCacheImpl(boolean isClient, PoolFactory poolFactory,
                   InternalDistributedSystem internalDistributedSystem, CacheConfig cacheConfig,
                   boolean useAsyncEventListeners, TypeRegistry typeRegistry, MeterRegistry meterRegistry,
                   Set<MeterRegistry> meterSubregistries) {
    this.isClient = isClient;
    this.poolFactory = poolFactory;
    this.cacheConfig = cacheConfig; // do early for bug 43213
    pdxRegistry = typeRegistry;
    this.meterRegistry = meterRegistry;
    this.meterSubregistries = meterSubregistries;

    // Synchronized to prevent a new cache from being created
    // before an old one has finished closing
    synchronized (MyCacheImpl.class) {

      // start JTA transaction manager within this synchronized block
      // to prevent race with cache close. fixes bug 43987
      JNDIInvoker.mapTransactions(internalDistributedSystem);
      system = internalDistributedSystem;
      dm = system.getDistributionManager();

      if (!isClient) {
        configurationResponse = requestSharedConfiguration();

        // apply the cluster's properties configuration and initialize security using that
        // configuration
        ccLoader.applyClusterPropertiesConfiguration(configurationResponse,
                system.getConfig());

        securityService =
                SecurityServiceFactory.create(system.getConfig().getSecurityProps(), cacheConfig);
        system.setSecurityService(securityService);
      } else {
        // create a no-op security service for client
        securityService = SecurityServiceFactory.create();
      }

      DistributionConfig systemConfig = internalDistributedSystem.getConfig();
      if (!this.isClient && PoolManager.getAll().isEmpty()) {
        // We only support management on members of a distributed system
        // Should do this: if (!getSystem().isLoner()) {
        // but it causes quickstart.CqClientTest to hang
        boolean disableJmx = systemConfig.getDisableJmx();
        if (disableJmx) {
          logger.info("Running with JMX disabled.");
        } else {
          resourceEventsListener = new ManagementListener(system);
          system.addResourceListener(resourceEventsListener);
          if (system.isLoner()) {
            system.getInternalLogWriter()
                    .info("Running in local mode since no locators were specified.");
          }
        }

      } else {
        logger.info("Running in client mode");
        resourceEventsListener = null;
      }

      // Don't let admin-only VMs create Cache's just yet.
      if (dm.getDMType() == ClusterDistributionManager.ADMIN_ONLY_DM_TYPE) {
        throw new IllegalStateException(
                "Cannot create a Cache in an admin-only VM.");
      }

      rootRegions = new HashMap<>();

      cqService = CqServiceProvider.create(this);

      // Create the CacheStatistics
      CachePerfStats.enableClockStats = system.getConfig().getEnableTimeStatistics();
      cachePerfStats = new CachePerfStats(internalDistributedSystem.getStatisticsManager());

      transactionManager = new TXManagerImpl(cachePerfStats, this);
      dm.addMembershipListener(transactionManager);

      creationDate = new Date();

      persistentMemberManager = new PersistentMemberManager();

      if (useAsyncEventListeners) {
        eventThreadPool = LoggingExecutors.newThreadPoolWithFixedFeed("Message Event Thread",
                command -> {
                  ConnectionTable.threadWantsSharedResources();
                  command.run();
                }, EVENT_THREAD_LIMIT, cachePerfStats.getEventPoolHelper(), 1000,
                getThreadMonitorObj(),
                EVENT_QUEUE_LIMIT);
      } else {
        eventThreadPool = null;
      }

      // Initialize the advisor here, but wait to exchange profiles until cache is fully built
      resourceAdvisor = ResourceAdvisor.createResourceAdvisor(this);

      // Initialize the advisor here, but wait to exchange profiles until cache is fully built
      jmxAdvisor = JmxManagerAdvisor
              .createJmxManagerAdvisor(new JmxManagerAdvisee(getCacheForProcessingClientRequests()));

      resourceManager = InternalResourceManager.createResourceManager(this);
      serialNumber = DistributionAdvisor.createSerialNumber();

      getInternalResourceManager().addResourceListener(ResourceType.HEAP_MEMORY, getHeapEvictor());

      /*
       * Only bother creating an off-heap evictor if we have off-heap memory enabled.
       */
      if (null != getOffHeapStore()) {
        getInternalResourceManager().addResourceListener(ResourceType.OFFHEAP_MEMORY,
                getOffHeapEvictor());
      }

      recordedEventSweeper = createEventTrackerExpiryTask();
      tombstoneService = TombstoneService.initialize(this);

      TypeRegistry.init();
      basicSetPdxSerializer(this.cacheConfig.getPdxSerializer());
      TypeRegistry.open();

      if (!isClient()) {
        // Initialize the QRM thread frequency to default (1 second )to prevent spill
        // over from previous Cache , as the interval is stored in a static
        // volatile field.
        HARegionQueue.setMessageSyncInterval(HARegionQueue.DEFAULT_MESSAGE_SYNC_INTERVAL);
      }
      FunctionService.registerFunction(new PRContainsValueFunction());
      expirationScheduler = new ExpirationScheduler(system);

      // uncomment following line when debugging CacheExistsException
      if (DEBUG_CREATION_STACK) {
        creationStack = new Exception(
                String.format("Created GemFireCache %s", toString()));
      }

      txEntryStateFactory = TXEntryState.getFactory();
      if (XML_PARAMETERIZATION_ENABLED) {
        // If product properties file is available replace properties from there
        Properties userProps = system.getConfig().getUserDefinedProps();
        if (userProps != null && !userProps.isEmpty()) {
          resolver = new CacheXmlPropertyResolver(false,
                  PropertyResolver.NO_SYSTEM_PROPERTIES_OVERRIDE, userProps);
        } else {
          resolver = new CacheXmlPropertyResolver(false,
                  PropertyResolver.NO_SYSTEM_PROPERTIES_OVERRIDE, null);
        }
      } else {
        resolver = null;
      }

      SystemFailure.signalCacheCreate();

      diskMonitor = new DiskStoreMonitor(systemConfig.getLogFile());

      addRegionEntrySynchronizationListener(new GatewaySenderQueueEntrySynchronizationListener());
      backupService = new BackupService(this);
      if (!this.isClient) {
        if (systemConfig.getHttpServicePort() == 0) {
          logger.info("HttpService is disabled with http-serivce-port = 0");
          httpService = Optional.empty();
        } else {
          try {
            httpService = Optional.of(new HttpService(systemConfig.getHttpServiceBindAddress(),
                    systemConfig.getHttpServicePort(), SSLConfigurationFactory
                    .getSSLConfigForComponent(systemConfig, SecurableCommunicationChannel.WEB)));
          } catch (Throwable ex) {
            logger.warn("Could not enable HttpService: {}", ex.getMessage());
          }
        }
      }
    } // synchronized

    clientMetadataService = new ClientMetadataService(this);
  }

  @Override
  public void throwCacheExistsException() {
    throw new CacheExistsException(this, String.format("%s: An open cache already exists.", this),
            creationStack);
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  /** generate XML for the cache before shutting down due to forced disconnect */
  public void saveCacheXmlForReconnect() {
    // there are two versions of this method so it can be unit-tested
    boolean sharedConfigEnabled =
            getDistributionManager().getConfig().getUseSharedConfiguration();

    if (!Boolean.getBoolean(DistributionConfig.GEMFIRE_PREFIX + "autoReconnect-useCacheXMLFile")
            && !sharedConfigEnabled) {
      try {
        logger.info("generating XML to rebuild the cache after reconnect completes");
        StringPrintWriter pw = new StringPrintWriter();
        CacheXmlGenerator.generate((Cache) this, pw, false);
        String cacheXML = pw.toString();
        getCacheConfig().setCacheXMLDescription(cacheXML);
        logger.info("XML generation completed: {}", cacheXML);
      } catch (CancelException e) {
        logger.info("Unable to generate XML description for reconnect of cache due to exception",
                e);
      }
    } else if (sharedConfigEnabled && !getCacheServers().isEmpty()) {
      // we need to retain a cache-server description if this JVM was started by gfsh
      List<CacheServerCreation> list = new ArrayList<>(getCacheServers().size());
      for (final Object o : getCacheServers()) {
        CacheServerImpl cs = (CacheServerImpl) o;
        if (cs.isDefaultServer()) {
          CacheServerCreation bsc = new CacheServerCreation(this, cs);
          list.add(bsc);
        }
      }
      getCacheConfig().setCacheServerCreation(list);
      logger.info("CacheServer configuration saved");
    }
  }

  @Override
  public Set<MeterRegistry> getMeterSubregistries() {
    return meterSubregistries;
  }

  @Override
  public Optional<HttpService> getHttpService() {
    return httpService;
  }

  @Override
  public void reLoadClusterConfiguration() throws IOException, ClassNotFoundException {
    configurationResponse = requestSharedConfiguration();
    if (configurationResponse != null) {
      ccLoader.deployJarsReceivedFromClusterConfiguration(configurationResponse);
      ccLoader.applyClusterPropertiesConfiguration(configurationResponse,
              system.getConfig());
      ccLoader.applyClusterXmlConfiguration(this, configurationResponse,
              system.getConfig().getGroups());
      initializeDeclarativeCache();
    }
  }

  /**
   * Initialize the EventTracker's timer task. This is stored for tracking and shutdown purposes
   */
  private EventTrackerExpiryTask createEventTrackerExpiryTask() {
    long lifetimeInMillis =
            Long.getLong(DistributionConfig.GEMFIRE_PREFIX + "messageTrackingTimeout",
                    PoolFactory.DEFAULT_SUBSCRIPTION_MESSAGE_TRACKING_TIMEOUT / 3);
    EventTrackerExpiryTask task = new EventTrackerExpiryTask(lifetimeInMillis);
    getCCPTimer().scheduleAtFixedRate(task, lifetimeInMillis, lifetimeInMillis);
    return task;
  }

  @Override
  public SecurityService getSecurityService() {
    return securityService;
  }

  @Override
  public boolean isRESTServiceRunning() {
    return isRESTServiceRunning;
  }

  @Override
  public void setRESTServiceRunning(boolean isRESTServiceRunning) {
    this.isRESTServiceRunning = isRESTServiceRunning;
  }

  /**
   * Used by Hydra tests to get handle of Rest Agent
   *
   */
  @Override
  public RestAgent getRestAgent() {
    return restAgent;
  }

  /**
   * Request the shared configuration from the locator(s) which have the Cluster config service
   * running
   */
  ConfigurationResponse requestSharedConfiguration() {
    final DistributionConfig config = system.getConfig();

    if (!(dm instanceof ClusterDistributionManager)) {
      return null;
    }

    // do nothing if this vm is/has locator or this is a client
    if (dm.getDMType() == ClusterDistributionManager.LOCATOR_DM_TYPE || isClient
            || Locator.getLocator() != null) {
      return null;
    }

    // can't simply return null if server is not using shared configuration, since we need to find
    // out if the locator is running in secure mode or not, if yes, then we need to throw an
    // exception if server is not using cluster config.

    Map<InternalDistributedMember, Collection<String>> locatorsWithClusterConfig =
            getDistributionManager().getAllHostedLocatorsWithSharedConfiguration();

    // If there are no locators with Shared configuration, that means the system has been started
    // without shared configuration then do not make requests to the locators.
    if (locatorsWithClusterConfig.isEmpty()) {
      logger.info("No locator(s) found with cluster configuration service");
      return null;
    }

    try {
      ConfigurationResponse response = ccLoader.requestConfigurationFromLocators(
              system.getConfig().getGroups(), locatorsWithClusterConfig.keySet());

      // log the configuration received from the locator
      logger.info("Received cluster configuration from the locator");
      logger.info(response.describeConfig());

      Configuration clusterConfig =
              response.getRequestedConfiguration().get(ConfigurationPersistenceService.CLUSTER_CONFIG);
      Properties clusterSecProperties =
              clusterConfig == null ? new Properties() : clusterConfig.getGemfireProperties();

      // If not using shared configuration, return null or throw an exception is locator is secured
      if (!config.getUseSharedConfiguration()) {
        if (clusterSecProperties.containsKey(ConfigurationProperties.SECURITY_MANAGER)) {
          throw new GemFireConfigException(
                  "A server must use cluster configuration when joining a secured cluster.");
        } else {
          logger.info(
                  "The cache has been created with use-cluster-configuration=false. It will not receive any cluster configuration");
          return null;
        }
      }

      Properties serverSecProperties = config.getSecurityProps();
      // check for possible mis-configuration
      if (isMisConfigured(clusterSecProperties, serverSecProperties,
              ConfigurationProperties.SECURITY_MANAGER)
              || isMisConfigured(clusterSecProperties, serverSecProperties,
              ConfigurationProperties.SECURITY_POST_PROCESSOR)) {
        throw new GemFireConfigException(
                "A server cannot specify its own security-manager or security-post-processor when using cluster configuration");
      }
      return response;

    } catch (ClusterConfigurationNotAvailableException e) {
      throw new GemFireConfigException(
              "cluster configuration service not available", e);
    } catch (UnknownHostException e) {
      throw new GemFireConfigException(e.getLocalizedMessage(), e);
    }
  }

  /**
   * When called, clusterProps and serverProps and key could not be null
   */
  static boolean isMisConfigured(Properties clusterProps, Properties serverProps, String key) {
    String clusterPropValue = clusterProps.getProperty(key);
    String serverPropValue = serverProps.getProperty(key);

    // if this server prop is not specified, this is always OK.
    if (StringUtils.isBlank(serverPropValue))
      return false;

    // server props is not blank, but cluster props is blank, NOT OK.
    if (StringUtils.isBlank(clusterPropValue))
      return true;

    // at this point check for equality
    return !clusterPropValue.equals(serverPropValue);
  }

  /**
   * Used by unit tests to force cache creation to use a test generated cache.xml
   */
  @MutableForTesting
  public static File testCacheXml = null;

  /**
   * @return true if cache is created using a ClientCacheFactory
   * @see #hasPool()
   */
  @Override
  public boolean isClient() {
    return isClient;
  }

  /**
   * Method to check for GemFire client. In addition to checking for ClientCacheFactory, this method
   * checks for any defined pools.
   *
   * @return true if the cache has pools declared
   */
  @Override
  public boolean hasPool() {
    return isClient || !getAllPools().isEmpty();
  }

  private static Collection<Pool> getAllPools() {
    Collection<Pool> pools = PoolManagerImpl.getPMI().getMap().values();
    for (Iterator<Pool> itr = pools.iterator(); itr.hasNext();) {
      PoolImpl pool = (PoolImpl) itr.next();
      if (pool.isUsedByGateway()) {
        itr.remove();
      }
    }
    return pools;
  }

  /**
   * May return null (even on a client).
   */
  @Override
  public synchronized Pool getDefaultPool() {
    if (defaultPool == null) {
      determineDefaultPool();
    }
    return defaultPool;
  }

  /**
   * Perform initialization, solve the early escaped reference problem by putting publishing
   * references to this instance in this method (vs. the constructor).
   */
  @Override
  public void initialize() {
    for (CacheLifecycleListener listener : cacheLifecycleListeners) {
      listener.cacheCreated(this);
    }

    if (isClient()) {
      initializeClientRegionShortcuts(this);
    } else {
      initializeRegionShortcuts(this);
    }

    // set ClassPathLoader and then deploy cluster config jars
    ClassPathLoader.setLatestToDefault(system.getConfig().getDeployWorkingDir());

    try {
      ccLoader.deployJarsReceivedFromClusterConfiguration(configurationResponse);
    } catch (IOException | ClassNotFoundException e) {
      throw new GemFireConfigException(
              "Exception while deploying the jars received as a part of cluster Configuration",
              e);
    }

    SystemMemberCacheEventProcessor.send(this, Operation.CACHE_CREATE);
    resourceAdvisor.initializationGate();

    // Register function that we need to execute to fetch available REST service endpoints in DS
    FunctionService.registerFunction(new FindRestEnabledServersFunction());

    // moved this after initializeDeclarativeCache because in the future
    // distributed system creation will not happen until we have read
    // cache.xml file.
    // For now this needs to happen before cache.xml otherwise
    // we will not be ready for all the events that cache.xml
    // processing can deliver (region creation, etc.).
    // This call may need to be moved inside initializeDeclarativeCache.
    jmxAdvisor.initializationGate(); // Entry to GemFire Management service

    // this starts up the ManagementService, register and federate the internal beans
    system.handleResourceEvent(ResourceEvent.CACHE_CREATE, this);

    initializeServices();

    boolean completedCacheXml = false;
    try {
      if (!isClient) {
        applyJarAndXmlFromClusterConfig();
      }
      initializeDeclarativeCache();
      completedCacheXml = true;
    } catch (RuntimeException e) {
      logger.error("Cache initialization for {} failed because: {}", this, e); // fix GEODE-3038
      throw e;
    } finally {
      if (!completedCacheXml) {
        // so initializeDeclarativeCache threw an exception
        try {
          close(); // fix for bug 34041
        } catch (Throwable ignore) {
          // I don't want init to throw an exception that came from the close.
          // I want it to throw the original exception that came from initializeDeclarativeCache.
        }
        configurationResponse = null;
      }
    }

    startColocatedJmxManagerLocator();

    startRestAgentServer(this);

    isInitialized = true;
  }

  void applyJarAndXmlFromClusterConfig() {
    if (configurationResponse == null) {
      // Deploy all the jars from the deploy working dir.
      ClassPathLoader.getLatest().getJarDeployer().loadPreviouslyDeployedJarsFromDisk();
    }
    ccLoader.applyClusterXmlConfiguration(this, configurationResponse,
            system.getConfig().getGroups());
  }

  /**
   * Initialize any services that provided as extensions to the cache using the service loader
   * mechanism.
   */
  private void initializeServices() {
    ServiceLoader<CacheService> loader = ServiceLoader.load(CacheService.class);
    for (CacheService service : loader) {
      service.init(this);
      services.put(service.getInterface(), service);
      system.handleResourceEvent(ResourceEvent.CACHE_SERVICE_CREATE, service);
      logger.info("Initialized cache service {}", service.getClass().getName());
    }
  }

  private boolean isServerNode() {
    return system.getDistributedMember()
            .getVmKind() != ClusterDistributionManager.LOCATOR_DM_TYPE
            && system.getDistributedMember()
            .getVmKind() != ClusterDistributionManager.ADMIN_ONLY_DM_TYPE
            && !isClient();
  }

  private void startRestAgentServer(MyCacheImpl cache) {
    if (system.getConfig().getStartDevRestApi() && isServerNode()) {
      restAgent = new RestAgent(system.getConfig(), securityService);
      restAgent.start(cache);
    } else {
      restAgent = null;
    }
  }



  @Override
  public URL getCacheXmlURL() {
    if (getMyId().getVmKind() == ClusterDistributionManager.LOCATOR_DM_TYPE) {
      return null;
    }
    File xmlFile = testCacheXml;
    if (xmlFile == null) {
      xmlFile = system.getConfig().getCacheXmlFile();
    }
    if (xmlFile.getName().isEmpty()) {
      return null;
    }

    URL url;
    if (!xmlFile.exists() || !xmlFile.isFile()) {
      // do a resource search
      String resource = xmlFile.getPath();
      resource = DOUBLE_BACKSLASH.matcher(resource).replaceAll("/");
      if (resource.length() > 1 && resource.startsWith("/")) {
        resource = resource.substring(1);
      }
      url = ClassPathLoader.getLatest().getResource(getClass(), resource);
    } else {
      try {
        url = xmlFile.toURL();
      } catch (MalformedURLException ex) {
        throw new CacheXmlException(
                String.format("Could not convert XML file %s to an URL.",
                        xmlFile),
                ex);
      }
    }
    if (url == null) {
      File defaultFile = DistributionConfig.DEFAULT_CACHE_XML_FILE;
      if (!xmlFile.equals(defaultFile)) {
        if (!xmlFile.exists()) {
          throw new CacheXmlException(
                  String.format("Declarative Cache XML file/resource %s does not exist.",
                          xmlFile));
        } else {
          throw new CacheXmlException(
                  String.format("Declarative XML file %s is not a file.",
                          xmlFile));
        }
      }
    }

    return url;
  }

  /**
   * Initializes the contents of this {@code Cache} according to the declarative caching XML file
   * specified by the given {@code DistributedSystem}. Note that this operation cannot be performed
   * in the constructor because creating regions in the cache, etc. uses the cache itself (which
   * isn't initialized until the constructor returns).
   *
   * @throws CacheXmlException If something goes wrong while parsing the declarative caching XML
   *         file.
   * @throws TimeoutException If a {@link Region#put(Object, Object)}times out while initializing
   *         the cache.
   * @throws CacheWriterException If a {@code CacheWriterException} is thrown while initializing the
   *         cache.
   * @throws RegionExistsException If the declarative caching XML file describes a region that
   *         already exists (including the root region).
   * @throws GatewayException If a {@code GatewayException} is thrown while initializing the cache.
   *
   * @see #loadCacheXml
   */
  private void initializeDeclarativeCache()
          throws TimeoutException, CacheWriterException, GatewayException, RegionExistsException {
    URL url = getCacheXmlURL();
    String cacheXmlDescription = cacheConfig.getCacheXMLDescription();
    if (url == null && cacheXmlDescription == null) {
      initializePdxRegistry();
      readyDynamicRegionFactory();
      return; // nothing needs to be done
    }

    InputStream stream = null;
    try {
      logCacheXML(url, cacheXmlDescription);
      if (cacheXmlDescription != null) {
        if (logger.isTraceEnabled()) {
          logger.trace("initializing cache with generated XML: {}", cacheXmlDescription);
        }
        stream = new StringBufferInputStream(cacheXmlDescription);
      } else {
        stream = url.openStream();
      }
      loadCacheXml(stream);

    } catch (IOException ex) {
      throw new CacheXmlException(
              String.format("While opening Cache XML %s the following error occurred %s",
                      url.toString(), ex));

    } catch (CacheXmlException ex) {
      throw new CacheXmlException(String.format("While reading Cache XML %s. %s",
              url, ex.getMessage()), ex.getCause());

    } finally {
      closeQuietly(stream);
    }
  }

  private static void logCacheXML(URL url, String cacheXmlDescription) {
    if (cacheXmlDescription == null) {
      StringBuilder sb = new StringBuilder();
      BufferedReader br = null;
      try {
        final String lineSeparator = System.getProperty("line.separator");
        br = new BufferedReader(new InputStreamReader(url.openStream()));
        String line = br.readLine();
        while (line != null) {
          if (!line.isEmpty()) {
            sb.append(lineSeparator).append(line);
          }
          line = br.readLine();
        }
      } catch (IOException ignore) {
      } finally {
        closeQuietly(br);
      }
      logger.info("Initializing cache using {}:{}",
              new Object[] {url.toString(), sb.toString()});
    } else {
      logger.info(
              "Initializing cache using {}:{}",
              new Object[] {"generated description from old cache", cacheXmlDescription});
    }
  }

  @Override
  public synchronized void initializePdxRegistry() {
    if (pdxRegistry == null) {
      // The member with locator is initialized with a NullTypePdxRegistration
      if (getMyId().getVmKind() == ClusterDistributionManager.LOCATOR_DM_TYPE) {
        pdxRegistry = new TypeRegistry(this, true);
      } else {
        pdxRegistry = new TypeRegistry(this, false);
      }
      pdxRegistry.initialize();
    }
  }

  /**
   * Call to make this vm's dynamic region factory ready. Public so it can be called from
   * CacheCreation during xml processing
   */
  @Override
  public void readyDynamicRegionFactory() {
    try {
      ((DynamicRegionFactoryImpl) DynamicRegionFactory.get()).internalInit(this);
    } catch (CacheException ce) {
      throw new GemFireCacheException(
              "dynamic region initialization failed",
              ce);
    }
  }

  /**
   * create diskStore factory with default attributes
   *
   * @since GemFire prPersistSprint2
   */
  @Override
  public DiskStoreFactory createDiskStoreFactory() {
    return new DiskStoreFactoryImpl(this);
  }

  /**
   * create diskStore factory with predefined attributes
   *
   * @since GemFire prPersistSprint2
   */
  @Override
  public DiskStoreFactory createDiskStoreFactory(DiskStoreAttributes attrs) {
    return new DiskStoreFactoryImpl(this, attrs);
  }

  class Stopper extends CancelCriterion {

    @Override
    public String cancelInProgress() {
      String reason = getDistributedSystem().getCancelCriterion().cancelInProgress();
      if (reason != null) {
        return reason;
      }
      if (disconnectCause != null) {
        return disconnectCause.getMessage();
      }
      if (isClosing) {
        return "The cache is closed."; // this + ": closed";
      }
      return null;
    }

    @Override
    public RuntimeException generateCancelledException(Throwable throwable) {
      String reason = cancelInProgress();
      if (reason == null) {
        return null;
      }
      RuntimeException result =
              getDistributedSystem().getCancelCriterion().generateCancelledException(throwable);
      if (result != null) {
        return result;
      }
      if (disconnectCause == null) {
        // No root cause, specify the one given and be done with it.
        return new CacheClosedException(reason, throwable);
      }

      if (throwable == null) {
        // Caller did not specify any root cause, so just use our own.
        return new CacheClosedException(reason, disconnectCause);
      }

      // Attempt to stick rootCause at tail end of the exception chain.
      try {
        ThrowableUtils.setRootCause(throwable, disconnectCause);
        return new CacheClosedException(reason, throwable);
      } catch (IllegalStateException ignore) {
        // Bug 39496 (JRockit related) Give up. The following
        // error is not entirely sane but gives the correct general picture.
        return new CacheClosedException(reason, disconnectCause);
      }
    }
  }

  private final MyCacheImpl.Stopper stopper = new MyCacheImpl.Stopper();

  @Override
  public CancelCriterion getCancelCriterion() {
    return stopper;
  }

  /** return true if the cache was closed due to being shunned by other members */
  @Override
  public boolean forcedDisconnect() {
    return forcedDisconnect || system.forcedDisconnect();
  }

  /** return a CacheClosedException with the given reason */
  @Override
  public CacheClosedException getCacheClosedException(String reason) {
    return getCacheClosedException(reason, null);
  }

  /** return a CacheClosedException with the given reason and cause */
  @Override
  public CacheClosedException getCacheClosedException(String reason, Throwable cause) {
    CacheClosedException result;
    if (cause != null) {
      result = new CacheClosedException(reason, cause);
    } else if (disconnectCause != null) {
      result = new CacheClosedException(reason, disconnectCause);
    } else {
      result = new CacheClosedException(reason);
    }
    return result;
  }

  /** if the cache was forcibly closed this exception will reflect the cause */
  @Override
  public Throwable getDisconnectCause() {
    return disconnectCause;
  }

  /**
   * Set to true during a cache close if user requested durable subscriptions to be kept.
   *
   * @since GemFire 5.7
   */
  private boolean keepAlive;

  /**
   * Returns true if durable subscriptions (registrations and queries) should be preserved.
   *
   * @since GemFire 5.7
   */
  @Override
  public boolean keepDurableSubscriptionsAlive() {
    return keepAlive;
  }

  /**
   * break any potential circularity in {@link #loadEmergencyClasses()}
   */
  @MakeNotStatic
  private static volatile boolean emergencyClassesLoaded = false;

  /**
   * Ensure that all the necessary classes for closing the cache are loaded
   *
   * @see SystemFailure#loadEmergencyClasses()
   */
  public static void loadEmergencyClasses() {
    if (emergencyClassesLoaded)
      return;
    emergencyClassesLoaded = true;
    InternalDistributedSystem.loadEmergencyClasses();
    AcceptorImpl.loadEmergencyClasses();
    PoolManagerImpl.loadEmergencyClasses();
  }

  /**
   * Close the distributed system, cache servers, and gateways. Clears the rootRegions and
   * partitionedRegions map. Marks the cache as closed.
   *
   * @see SystemFailure#emergencyClose()
   */
  public static void emergencyClose() {
    MyCacheImpl cache = getInstance();
    if (cache == null) {
      return;
    }

    // leave the PdxSerializer set if we have one to prevent 43412

    // Shut down messaging first
    InternalDistributedSystem ids = cache.system;
    if (ids != null) {
      ids.emergencyClose();
    }

    cache.disconnectCause = SystemFailure.getFailure();
    cache.isClosing = true;

    for (InternalCacheServer cacheServer : cache.allCacheServers) {
      Acceptor acceptor = cacheServer.getAcceptor();
      if (acceptor != null) {
        acceptor.emergencyClose();
      }
    }

    InternalCacheServer receiverServer = cache.gatewayReceiverServer.get();
    Acceptor acceptor = receiverServer.getAcceptor();
    if (acceptor != null) {
      acceptor.emergencyClose();
    }

    PoolManagerImpl.emergencyClose();

    // rootRegions is intentionally *not* synchronized. The
    // implementation of clear() does not currently allocate objects.
    cache.rootRegions.clear();

    // partitionedRegions is intentionally *not* synchronized, The
    // implementation of clear() does not currently allocate objects.
    cache.partitionedRegions.clear();
  }

  @Override
  public boolean isCacheAtShutdownAll() {
    return isShutDownAll.get();
  }

  /**
   * Number of threads used to close PRs in shutdownAll. By default is the number of PRs in the
   * cache
   */
  private static final int shutdownAllPoolSize =
          Integer.getInteger(DistributionConfig.GEMFIRE_PREFIX + "SHUTDOWN_ALL_POOL_SIZE", -1);

  private void shutdownSubTreeGracefully(Map<String, PartitionedRegion> prSubMap) {
    for (final PartitionedRegion pr : prSubMap.values()) {
      shutDownOnePRGracefully(pr);
    }
  }

  @Override
  public void shutDownAll() {
    if (LocalRegion.ISSUE_CALLBACKS_TO_CACHE_OBSERVER) {
      try {
        CacheObserverHolder.getInstance().beforeShutdownAll();
      } finally {
        LocalRegion.ISSUE_CALLBACKS_TO_CACHE_OBSERVER = false;
      }
    }
    if (!isShutDownAll.compareAndSet(false, true)) {
      // it's already doing shutdown by another thread
      try {
        shutDownAllFinished.await();
      } catch (InterruptedException ignore) {
        logger.debug(
                "Shutdown all interrupted while waiting for another thread to do the shutDownAll");
        Thread.currentThread().interrupt();
      }
      return;
    }
    synchronized (MyCacheImpl.class) {
      try {
        boolean testIGE = Boolean.getBoolean("TestInternalGemFireError");

        if (testIGE) {
          throw new InternalGemFireError(
                  "unexpected exception");
        }

        // bug 44031 requires multithread shutDownAll should be grouped
        // by root region. However, shutDownAllDuringRecovery.conf test revealed that
        // we have to close colocated child regions first.
        // Now check all the PR, if anyone has colocate-with attribute, sort all the
        // PRs by colocation relationship and close them sequentially, otherwise still
        // group them by root region.
        SortedMap<String, Map<String, PartitionedRegion>> prTrees = getPRTrees();
        if (prTrees.size() > 1 && shutdownAllPoolSize != 1) {
          ExecutorService es = getShutdownAllExecutorService(prTrees.size());
          for (final Map<String, PartitionedRegion> prSubMap : prTrees.values()) {
            es.execute(() -> {
              ConnectionTable.threadWantsSharedResources();
              shutdownSubTreeGracefully(prSubMap);
            });
          } // for each root
          es.shutdown();
          try {
            es.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
          } catch (InterruptedException ignore) {
            logger
                    .debug("Shutdown all interrupted while waiting for PRs to be shutdown gracefully.");
          }

        } else {
          for (final Map<String, PartitionedRegion> prSubMap : prTrees.values()) {
            shutdownSubTreeGracefully(prSubMap);
          }
        }

        close("Shut down all members", null, false, true);
      } finally {
        shutDownAllFinished.countDown();
      }
    }
  }

  private ExecutorService getShutdownAllExecutorService(int size) {
    return LoggingExecutors
            .newFixedThreadPool("ShutdownAll-", true,
                    shutdownAllPoolSize == -1 ? size : shutdownAllPoolSize);
  }

  private void shutDownOnePRGracefully(PartitionedRegion partitionedRegion) {
    boolean acquiredLock = false;
    try {
      partitionedRegion.acquireDestroyLock();
      acquiredLock = true;

      synchronized (partitionedRegion.getRedundancyProvider()) {
        if (partitionedRegion.isDataStore() && partitionedRegion.getDataStore() != null
                && partitionedRegion.getDataPolicy() == DataPolicy.PERSISTENT_PARTITION) {
          int numBuckets = partitionedRegion.getTotalNumberOfBuckets();
          @SuppressWarnings("unchecked")
          Map<InternalDistributedMember, PersistentMemberID>[] bucketMaps = new Map[numBuckets];
          PartitionedRegionDataStore dataStore = partitionedRegion.getDataStore();

          // lock all the primary buckets
          Set<Map.Entry<Integer, BucketRegion>> bucketEntries = dataStore.getAllLocalBuckets();
          for (Map.Entry e : bucketEntries) {
            BucketRegion bucket = (BucketRegion) e.getValue();
            if (bucket == null || bucket.isDestroyed) {
              // bucket region could be destroyed in race condition
              continue;
            }
            bucket.getBucketAdvisor().tryLockIfPrimary();

            // get map <InternalDistributedMember, persistentID> for this bucket's
            // remote members
            bucketMaps[bucket.getId()] =
                    bucket.getBucketAdvisor().adviseInitializedPersistentMembers();
            if (logger.isDebugEnabled()) {
              logger.debug("shutDownAll: PR {}: initialized persistent members for {}:{}",
                      partitionedRegion.getName(), bucket.getId(), bucketMaps[bucket.getId()]);
            }
          }
          if (logger.isDebugEnabled()) {
            logger.debug("shutDownAll: All buckets for PR {} are locked.",
                    partitionedRegion.getName());
          }

          // send lock profile update to other members
          partitionedRegion.setShutDownAllStatus(PartitionedRegion.PRIMARY_BUCKETS_LOCKED);
          new UpdateAttributesProcessor(partitionedRegion).distribute(false);
          partitionedRegion.getRegionAdvisor()
                  .waitForProfileStatus(PartitionedRegion.PRIMARY_BUCKETS_LOCKED);
          if (logger.isDebugEnabled()) {
            logger.debug("shutDownAll: PR {}: all bucketLock profiles received.",
                    partitionedRegion.getName());
          }

          // if async write, do flush
          if (!partitionedRegion.getAttributes().isDiskSynchronous()) {
            // several PRs might share the same diskStore, we will only flush once
            // even flush is called several times.
            partitionedRegion.getDiskStore().forceFlush();
            // send flush profile update to other members
            partitionedRegion.setShutDownAllStatus(PartitionedRegion.DISK_STORE_FLUSHED);
            new UpdateAttributesProcessor(partitionedRegion).distribute(false);
            partitionedRegion.getRegionAdvisor()
                    .waitForProfileStatus(PartitionedRegion.DISK_STORE_FLUSHED);
            if (logger.isDebugEnabled()) {
              logger.debug("shutDownAll: PR {}: all flush profiles received.",
                      partitionedRegion.getName());
            }
          } // async write

          // persist other members to OFFLINE_EQUAL for each bucket region
          // iterate through all the bucketMaps and exclude the items whose
          // idm is no longer online
          Set<InternalDistributedMember> membersToPersistOfflineEqual =
                  partitionedRegion.getRegionAdvisor().adviseDataStore();
          for (Map.Entry e : bucketEntries) {
            BucketRegion bucket = (BucketRegion) e.getValue();
            if (bucket == null || bucket.isDestroyed) {
              // bucket region could be destroyed in race condition
              continue;
            }
            Map<InternalDistributedMember, PersistentMemberID> persistMap =
                    getSubMapForLiveMembers(membersToPersistOfflineEqual, bucketMaps[bucket.getId()]);
            if (persistMap != null) {
              bucket.getPersistenceAdvisor().persistMembersOfflineAndEqual(persistMap);
              if (logger.isDebugEnabled()) {
                logger.debug("shutDownAll: PR {}: persisting bucket {}:{}",
                        partitionedRegion.getName(), bucket.getId(), persistMap);
              }
            }
          }

          // send persisted profile update to other members, let all members to persist
          // before close the region
          partitionedRegion.setShutDownAllStatus(PartitionedRegion.OFFLINE_EQUAL_PERSISTED);
          new UpdateAttributesProcessor(partitionedRegion).distribute(false);
          partitionedRegion.getRegionAdvisor()
                  .waitForProfileStatus(PartitionedRegion.OFFLINE_EQUAL_PERSISTED);
          if (logger.isDebugEnabled()) {
            logger.debug("shutDownAll: PR {}: all offline_equal profiles received.",
                    partitionedRegion.getName());
          }
        } // dataStore

        // after done all steps for buckets, close partitionedRegion
        // close accessor directly
        RegionEventImpl event = new RegionEventImpl(partitionedRegion, Operation.REGION_CLOSE, null,
                false, getMyId(), true);
        try {
          // not to acquire lock
          partitionedRegion.basicDestroyRegion(event, false, false, true);
        } catch (CacheWriterException e) {
          // not possible with local operation, CacheWriter not called
          throw new Error(
                  "CacheWriterException should not be thrown in localDestroyRegion",
                  e);
        } catch (TimeoutException e) {
          // not possible with local operation, no distributed locks possible
          throw new Error(
                  "TimeoutException should not be thrown in localDestroyRegion",
                  e);
        }
      } // synchronized
    } catch (CacheClosedException cce) {
      logger.debug("Encounter CacheClosedException when shutDownAll is closing PR: {}:{}",
              partitionedRegion.getFullPath(), cce.getMessage());
    } catch (CancelException ce) {
      logger.debug("Encounter CancelException when shutDownAll is closing PR: {}:{}",
              partitionedRegion.getFullPath(), ce.getMessage());
    } catch (RegionDestroyedException rde) {
      logger.debug("Encounter CacheDestroyedException when shutDownAll is closing PR: {}:{}",
              partitionedRegion.getFullPath(), rde.getMessage());
    } finally {
      if (acquiredLock) {
        partitionedRegion.releaseDestroyLock();
      }
    }
  }

  private static Map<InternalDistributedMember, PersistentMemberID> getSubMapForLiveMembers(
          Set<InternalDistributedMember> membersToPersistOfflineEqual,
          Map<InternalDistributedMember, PersistentMemberID> bucketMap) {
    if (bucketMap == null) {
      return null;
    }
    Map<InternalDistributedMember, PersistentMemberID> persistMap = new HashMap<>();
    for (InternalDistributedMember member : membersToPersistOfflineEqual) {
      if (bucketMap.containsKey(member)) {
        persistMap.put(member, bucketMap.get(member));
      }
    }
    return persistMap;
  }

  @Override
  public void close() {
    close(false);
  }

  @Override
  public void close(String reason, boolean keepAlive, boolean keepDS) {
    close(reason, null, keepAlive, keepDS);
  }

  @Override
  public void close(boolean keepAlive) {
    close("Normal disconnect", null, keepAlive, false);
  }

  @Override
  public void close(String reason, Throwable optionalCause) {
    close(reason, optionalCause, false, false);
  }

  /**
   * Gets or lazily creates the PartitionedRegion distributed lock service. This call will
   * synchronize on this GemFireCache.
   *
   * @return the PartitionedRegion distributed lock service
   */
  @Override
  public DistributedLockService getPartitionedRegionLockService() {
    synchronized (prLockServiceLock) {
      stopper.checkCancelInProgress(null);
      if (prLockService == null) {
        try {
          prLockService =
                  DLockService.create(PartitionedRegionHelper.PARTITION_LOCK_SERVICE_NAME,
                          getInternalDistributedSystem(), true /* distributed */,
                          true /* destroyOnDisconnect */, true /* automateFreeResources */);
        } catch (IllegalArgumentException e) {
          prLockService = DistributedLockService
                  .getServiceNamed(PartitionedRegionHelper.PARTITION_LOCK_SERVICE_NAME);
          if (prLockService == null) {
            throw e; // PARTITION_LOCK_SERVICE_NAME must be illegal!
          }
        }
      }
      return prLockService;
    }
  }

  /**
   * Gets or lazily creates the GatewaySender distributed lock service.
   *
   * @return the GatewaySender distributed lock service
   */
  @Override
  public DistributedLockService getGatewaySenderLockService() {
    if (gatewayLockService == null) {
      synchronized (gatewayLockServiceLock) {
        stopper.checkCancelInProgress(null);
        if (gatewayLockService == null) {
          try {
            gatewayLockService = DLockService.create(AbstractGatewaySender.LOCK_SERVICE_NAME,
                    getInternalDistributedSystem(), true /* distributed */,
                    true /* destroyOnDisconnect */, true /* automateFreeResources */);
          } catch (IllegalArgumentException e) {
            gatewayLockService =
                    DistributedLockService.getServiceNamed(AbstractGatewaySender.LOCK_SERVICE_NAME);
            if (gatewayLockService == null) {
              throw e; // AbstractGatewaySender.LOCK_SERVICE_NAME must be illegal!
            }
          }
        }
      }
    }
    return gatewayLockService;
  }

  /**
   * Destroys the PartitionedRegion distributed lock service when closing the cache. Caller must be
   * synchronized on this GemFireCache.
   */
  private void destroyPartitionedRegionLockService() {
    try {
      DistributedLockService.destroy(PartitionedRegionHelper.PARTITION_LOCK_SERVICE_NAME);
    } catch (IllegalArgumentException ignore) {
      // DistributedSystem.disconnect may have already destroyed the DLS
    }
  }

  /**
   * Destroys the GatewaySender distributed lock service when closing the cache. Caller must be
   * synchronized on this GemFireCache.
   */
  private void destroyGatewaySenderLockService() {
    if (DistributedLockService.getServiceNamed(AbstractGatewaySender.LOCK_SERVICE_NAME) != null) {
      try {
        DistributedLockService.destroy(AbstractGatewaySender.LOCK_SERVICE_NAME);
      } catch (IllegalArgumentException ignore) {
        // DistributedSystem.disconnect may have already destroyed the DLS
      }
    }
  }

  public HeapEvictor getHeapEvictor() {
    synchronized (heapEvictorLock) {
      stopper.checkCancelInProgress(null);
      if (heapEvictor == null) {
        heapEvictor = new HeapEvictor(this);
      }
      return heapEvictor;
    }
  }

  public OffHeapEvictor getOffHeapEvictor() {
    synchronized (offHeapEvictorLock) {
      stopper.checkCancelInProgress(null);
      if (offHeapEvictor == null) {
        offHeapEvictor = new OffHeapEvictor(this);
      }
      return offHeapEvictor;
    }
  }

  /** Used by test to inject an evictor */
  void setOffHeapEvictor(OffHeapEvictor evictor) {
    offHeapEvictor = evictor;
  }

  /** Used by test to inject an evictor */
  void setHeapEvictor(HeapEvictor evictor) {
    heapEvictor = evictor;
  }

  @Override
  public PersistentMemberManager getPersistentMemberManager() {
    return persistentMemberManager;
  }

  @Override
  public ClientMetadataService getClientMetadataService() {
    stopper.checkCancelInProgress(null);

    return clientMetadataService;
  }

  private final boolean DISABLE_DISCONNECT_DS_ON_CACHE_CLOSE = Boolean
          .getBoolean(DistributionConfig.GEMFIRE_PREFIX + "DISABLE_DISCONNECT_DS_ON_CACHE_CLOSE");

  @Override
  public void close(String reason, Throwable systemFailureCause, boolean keepAlive,
                    boolean keepDS) {
    securityService.close();

    if (isClosed()) {
      return;
    }

    if (!keepDS && systemFailureCause == null // normal cache close
            && (isReconnecting() || system.getReconnectedSystem() != null)) {
      logger.debug(
              "Cache is shutting down distributed system connection.  "
                      + "isReconnecting={}  reconnectedSystem={} keepAlive={} keepDS={}",
              isReconnecting(), system.getReconnectedSystem(), keepAlive, keepDS);

      system.stopReconnectingNoDisconnect();
      if (system.getReconnectedSystem() != null) {
        system.getReconnectedSystem().disconnect();
      }
      return;
    }

    final boolean isDebugEnabled = logger.isDebugEnabled();

    synchronized (MyCacheImpl.class) {
      // fix for bug 36512 "GemFireCache.close is not thread safe"
      // ALL CODE FOR CLOSE SHOULD NOW BE UNDER STATIC SYNCHRONIZATION
      // OF synchronized (GemFireCache.class) {
      // static synchronization is necessary due to static resources
      if (isClosed()) {
        return;
      }

      /*
       * First close the ManagementService as it uses a lot of infra which will be closed by
       * cache.close()
       */
      system.handleResourceEvent(ResourceEvent.CACHE_REMOVE, this);
      if (resourceEventsListener != null) {
        system.removeResourceListener(resourceEventsListener);
        resourceEventsListener = null;
      }

      if (systemFailureCause != null) {
        forcedDisconnect = systemFailureCause instanceof ForcedDisconnectException;
        if (forcedDisconnect) {
          disconnectCause = new ForcedDisconnectException(reason);
        } else {
          disconnectCause = systemFailureCause;
        }
      }

      this.keepAlive = keepAlive;
      isClosing = true;
      logger.info("{}: Now closing.", this);

      // we don't clear the prID map if there is a system failure. Other
      // threads may be hung trying to communicate with the map locked
      if (systemFailureCause == null) {
        PartitionedRegion.clearPRIdMap();
      }
      TXStateProxy tx = null;
      try {

        if (transactionManager != null) {
          tx = transactionManager.pauseTransaction();
        }

        // do this before closing regions
        resourceManager.close();

        try {
          resourceAdvisor.close();
        } catch (CancelException ignore) {
          // ignore
        }
        try {
          jmxAdvisor.close();
        } catch (CancelException ignore) {
          // ignore
        }

        for (GatewaySender sender : allGatewaySenders) {
          try {
            sender.stop();
            GatewaySenderAdvisor advisor = ((AbstractGatewaySender) sender).getSenderAdvisor();
            if (advisor != null) {
              if (isDebugEnabled) {
                logger.debug("Stopping the GatewaySender advisor");
              }
              advisor.close();
            }
          } catch (CancelException ignore) {
          }
        }

        destroyGatewaySenderLockService();

        if (eventThreadPool != null) {
          if (isDebugEnabled) {
            logger.debug("{}: stopping event thread pool...", this);
          }
          eventThreadPool.shutdown();
        }

        /*
         * IMPORTANT: any operation during shut down that can time out (create a CancelException)
         * must be inside of this try block. If all else fails, we *must* ensure that the cache gets
         * closed!
         */
        try {
          stopServers();

          stopServices();

          httpService.ifPresent(HttpService::stop);

          // no need to track PR instances since we won't create any more
          // cacheServers or gatewayHubs
          if (isDebugEnabled) {
            logger.debug("{}: clearing partitioned regions...", this);
          }
          synchronized (partitionedRegions) {
            int prSize = -partitionedRegions.size();
            partitionedRegions.clear();
            getCachePerfStats().incPartitionedRegions(prSize);
          }

          prepareDiskStoresForClose();

          List<InternalRegion> rootRegionValues;
          synchronized (rootRegions) {
            rootRegionValues = new ArrayList<>(rootRegions.values());
          }
          {
            final Operation op;
            if (forcedDisconnect) {
              op = Operation.FORCED_DISCONNECT;
            } else if (isReconnecting()) {
              op = Operation.CACHE_RECONNECT;
            } else {
              op = Operation.CACHE_CLOSE;
            }

            InternalRegion prRoot = null;

            for (InternalRegion lr : rootRegionValues) {
              if (isDebugEnabled) {
                logger.debug("{}: processing region {}", this, lr.getFullPath());
              }
              if (PartitionedRegionHelper.PR_ROOT_REGION_NAME.equals(lr.getName())) {
                prRoot = lr;
              } else {
                if (lr.getName().contains(ParallelGatewaySenderQueue.QSTRING)) {
                  continue; // this region will be closed internally by parent region
                }
                if (isDebugEnabled) {
                  logger.debug("{}: closing region {}...", this, lr.getFullPath());
                }
                try {
                  lr.handleCacheClose(op);
                } catch (RuntimeException e) {
                  if (isDebugEnabled || !forcedDisconnect) {
                    logger.warn(String.format("%s: error closing region %s",
                            this, lr.getFullPath()), e);
                  }
                }
              }
            } // for

            try {
              if (isDebugEnabled) {
                logger.debug("{}: finishing partitioned region close...", this);
              }
              PartitionedRegion.afterRegionsClosedByCacheClose(this);
              if (prRoot != null) {
                // do the PR meta root region last
                prRoot.handleCacheClose(op);
              }
            } catch (CancelException e) {
              logger.warn(String.format("%s: error in last stage of PartitionedRegion cache close",
                      this),
                      e);
            }
            destroyPartitionedRegionLockService();
          }

          closeDiskStores();
          diskMonitor.close();

          // Close the CqService Handle.
          try {
            if (isDebugEnabled) {
              logger.debug("{}: closing CQ service...", this);
            }
            cqService.close();
          } catch (RuntimeException ignore) {
            logger.info("Failed to get the CqService, to close during cache close (1).");
          }

          PoolManager.close(keepAlive);

          if (isDebugEnabled) {
            logger.debug("{}: notifying admins of close...", this);
          }
          try {
            SystemMemberCacheEventProcessor.send(this, Operation.CACHE_CLOSE);
          } catch (CancelException ignore) {
            if (logger.isDebugEnabled()) {
              logger.debug("Ignored cancellation while notifying admins");
            }
          }

          if (isDebugEnabled) {
            logger.debug("{}: stopping destroyed entries processor...", this);
          }
          tombstoneService.stop();

          // NOTICE: the CloseCache message is the *last* message you can send!
          DistributionManager distributionManager = null;
          try {
            distributionManager = system.getDistributionManager();
            distributionManager.removeMembershipListener(transactionManager);
          } catch (CancelException ignore) {
            // distributionManager = null;
          }

          if (distributionManager != null) { // Send CacheClosedMessage (and NOTHING ELSE) here
            if (isDebugEnabled) {
              logger.debug("{}: sending CloseCache to peers...", this);
            }
            Set<? extends DistributedMember> otherMembers =
                    distributionManager.getOtherDistributionManagerIds();
            ReplyProcessor21 processor = new ReplyProcessor21(system, otherMembers);
            CloseCacheMessage msg = new CloseCacheMessage();
            msg.setRecipients(otherMembers);
            msg.setProcessorId(processor.getProcessorId());
            distributionManager.putOutgoing(msg);
            try {
              processor.waitForReplies();
            } catch (InterruptedException ignore) {
              // Thread.currentThread().interrupt(); // TODO ??? should we reset this bit later?
              // Keep going, make best effort to shut down.
            } catch (ReplyException ignore) {
              // keep going
            }
            // set closed state after telling others and getting responses
            // to avoid complications with others still in the process of
            // sending messages
          }
          // NO MORE Distributed Messaging AFTER THIS POINT!!!!

          ClientMetadataService cms = clientMetadataService;
          if (cms != null) {
            cms.close();
          }
          closeHeapEvictor();
          closeOffHeapEvictor();
        } catch (CancelException ignore) {
          // make sure the disk stores get closed
          closeDiskStores();
          // NO DISTRIBUTED MESSAGING CAN BE DONE HERE!
        }

        // Close the CqService Handle.
        try {
          cqService.close();
        } catch (RuntimeException ignore) {
          logger.info("Failed to get the CqService, to close during cache close (2).");
        }

        cachePerfStats.close();
        TXLockService.destroyServices();
        getEventTrackerTask().cancel();

        synchronized (ccpTimerMutex) {
          if (ccpTimer != null) {
            ccpTimer.cancel();
          }
        }

        expirationScheduler.cancel();

        // Stop QueryMonitor if running.
        if (queryMonitor != null) {
          queryMonitor.stopMonitoring();
        }

      } finally {
        // NO DISTRIBUTED MESSAGING CAN BE DONE HERE!
        if (transactionManager != null) {
          transactionManager.close();
        }
        ((DynamicRegionFactoryImpl) DynamicRegionFactory.get()).close();
        if (transactionManager != null) {
          transactionManager.unpauseTransaction(tx);
        }
        TXCommitMessage.getTracker().clearForCacheClose();
      }
      // Added to close the TransactionManager's cleanup thread
      TransactionManagerImpl.refresh();

      if (!keepDS) {
        // keepDS is used by ShutdownAll. It will override DISABLE_DISCONNECT_DS_ON_CACHE_CLOSE
        if (!DISABLE_DISCONNECT_DS_ON_CACHE_CLOSE) {
          system.disconnect();
        }
      }
      TypeRegistry.close();
      // do this late to prevent 43412
      TypeRegistry.setPdxSerializer(null);

      for (CacheLifecycleListener listener : cacheLifecycleListeners) {
        listener.cacheClosed(this);
      }
      // Fix for #49856
      SequenceLoggerImpl.signalCacheClose();
      SystemFailure.signalCacheClose();

    } // static synchronization on GemFireCache.class

  }

  private void stopServices() {
    for (CacheService service : services.values()) {
      try {
        service.close();
      } catch (Throwable t) {
        logger.warn("Error stopping service " + service, t);
      }
    }
  }

  private void closeOffHeapEvictor() {
    OffHeapEvictor evictor = offHeapEvictor;
    if (evictor != null) {
      evictor.close();
    }
  }

  private void closeHeapEvictor() {
    HeapEvictor evictor = heapEvictor;
    if (evictor != null) {
      evictor.close();
    }
  }

  @Override
  public boolean isReconnecting() {
    return system.isReconnecting();
  }

  @Override
  public boolean waitUntilReconnected(long time, TimeUnit units) throws InterruptedException {
    try {
      boolean systemReconnected = system.waitUntilReconnected(time, units);
      if (!systemReconnected) {
        return false;
      }
      MyCacheImpl cache = getInstance();
      return cache != null && cache.isInitialized();
    } catch (CancelException e) {
      throw new CacheClosedException("Cache could not be recreated", e);
    }
  }

  @Override
  public void stopReconnecting() {
    system.stopReconnecting();
  }

  @Override
  public Cache getReconnectedCache() {
    MyCacheImpl cache = MyCacheImpl.getInstance();
    if (cache == this || cache != null && !cache.isInitialized()) {
      cache = null;
    }
    return cache;
  }

  private void prepareDiskStoresForClose() {
    String pdxDSName = TypeRegistry.getPdxDiskStoreName(this);
    DiskStoreImpl pdxDiskStore = null;
    for (DiskStoreImpl dsi : diskStores.values()) {
      if (dsi.getName().equals(pdxDSName)) {
        pdxDiskStore = dsi;
      } else {
        dsi.prepareForClose();
      }
    }
    if (pdxDiskStore != null) {
      pdxDiskStore.prepareForClose();
    }
  }

  private final ConcurrentMap<String, DiskStoreImpl> diskStores = new ConcurrentHashMap<>();

  private final ConcurrentMap<String, DiskStoreImpl> regionOwnedDiskStores =
          new ConcurrentHashMap<>();

  @Override
  public void addDiskStore(DiskStoreImpl dsi) {
    diskStores.put(dsi.getName(), dsi);
    if (!dsi.isOffline()) {
      diskMonitor.addDiskStore(dsi);
    }
  }

  @Override
  public void removeDiskStore(DiskStoreImpl diskStore) {
    diskStores.remove(diskStore.getName());
    regionOwnedDiskStores.remove(diskStore.getName());
    // Added for M&M
    if (!diskStore.getOwnedByRegion())
      system.handleResourceEvent(ResourceEvent.DISKSTORE_REMOVE, diskStore);
  }

  @Override
  public void addRegionOwnedDiskStore(DiskStoreImpl dsi) {
    regionOwnedDiskStores.put(dsi.getName(), dsi);
    if (!dsi.isOffline()) {
      diskMonitor.addDiskStore(dsi);
    }
  }

  @Override
  public void closeDiskStores() {
    Iterator<DiskStoreImpl> it = diskStores.values().iterator();
    while (it.hasNext()) {
      try {
        DiskStoreImpl dsi = it.next();
        if (logger.isDebugEnabled()) {
          logger.debug("closing {}", dsi);
        }
        dsi.close();
        // Added for M&M
        system.handleResourceEvent(ResourceEvent.DISKSTORE_REMOVE, dsi);
      } catch (RuntimeException e) {
        logger.fatal("Cache close caught an exception during disk store close", e);
      }
      it.remove();
    }
  }

  /**
   * Used by unit tests to allow them to change the default disk store name.
   */
  public static void setDefaultDiskStoreName(String dsName) {
    defaultDiskStoreName = dsName;
  }

  public static String getDefaultDiskStoreName() {
    return defaultDiskStoreName;
  }

  // TODO: remove static from defaultDiskStoreName and move methods to InternalCache
  @MakeNotStatic
  private static String defaultDiskStoreName = DiskStoreFactory.DEFAULT_DISK_STORE_NAME;

  @Override
  public DiskStoreImpl getOrCreateDefaultDiskStore() {
    DiskStoreImpl result = (DiskStoreImpl) findDiskStore(null);
    if (result == null) {
      synchronized (this) {
        result = (DiskStoreImpl) findDiskStore(null);
        if (result == null) {
          result = (DiskStoreImpl) createDiskStoreFactory().create(defaultDiskStoreName);
        }
      }
    }
    return result;
  }

  /**
   * Returns the DiskStore by name
   *
   * @since GemFire prPersistSprint2
   */
  @Override
  public DiskStore findDiskStore(String name) {
    if (name == null) {
      name = defaultDiskStoreName;
    }
    return diskStores.get(name);
  }

  /**
   * Returns the DiskStore list
   *
   * @since GemFire prPersistSprint2
   */
  @Override
  public Collection<DiskStore> listDiskStores() {
    return Collections.unmodifiableCollection(diskStores.values());
  }

  @Override
  public Collection<DiskStore> listDiskStoresIncludingRegionOwned() {
    Collection<DiskStore> allDiskStores = new HashSet<>();
    allDiskStores.addAll(diskStores.values());
    allDiskStores.addAll(regionOwnedDiskStores.values());
    return allDiskStores;
  }

  private void stopServers() {
    final boolean isDebugEnabled = logger.isDebugEnabled();
    if (isDebugEnabled) {
      logger.debug("{}: stopping cache servers...", this);
    }

    boolean stoppedCacheServer = false;

    for (InternalCacheServer cacheServer : allCacheServers) {
      if (isDebugEnabled) {
        logger.debug("stopping bridge {}", cacheServer);
      }
      try {
        cacheServer.stop();
      } catch (CancelException e) {
        if (isDebugEnabled) {
          logger.debug("Ignored cache closure while closing bridge {}", cacheServer, e);
        }
      }
      allCacheServers.remove(cacheServer);
      stoppedCacheServer = true;
    }

    InternalCacheServer receiverServer = gatewayReceiverServer.getAndSet(null);
    if (receiverServer != null) {
      if (isDebugEnabled) {
        logger.debug("stopping gateway receiver server {}", receiverServer);
      }
      try {
        receiverServer.stop();
      } catch (CancelException e) {
        if (isDebugEnabled) {
          logger.debug("Ignored cache closure while closing gateway receiver server {}",
                  receiverServer, e);
        }
      }
      stoppedCacheServer = true;
    }

    if (stoppedCacheServer) {
      // now that all the cache servers have stopped empty the static pool of commBuffers it might
      // have used.
      ServerConnection.emptyCommBufferPool();
    }

    // stop HA services if they had been started
    if (isDebugEnabled) {
      logger.debug("{}: stopping HA services...", this);
    }
    try {
      HARegionQueue.stopHAServices();
    } catch (CancelException e) {
      if (isDebugEnabled) {
        logger.debug("Ignored cache closure while closing HA services", e);
      }
    }

    if (isDebugEnabled) {
      logger.debug("{}: stopping client health monitor...", this);
    }
    try {
      ClientHealthMonitor.shutdownInstance();
    } catch (CancelException e) {
      if (isDebugEnabled) {
        logger.debug("Ignored cache closure while closing client health monitor", e);
      }
    }

    // Reset the unique id counter for durable clients.
    // If a durable client stops/starts its cache, it needs
    // to maintain the same unique id.
    ClientProxyMembershipID.resetUniqueIdCounter();
  }

  @Override
  public DistributedSystem getDistributedSystem() {
    return system;
  }

  @Override
  public InternalDistributedSystem getInternalDistributedSystem() {
    return system;
  }

  /**
   * Returns the member id of my distributed system
   *
   * @since GemFire 5.0
   */
  @Override
  public InternalDistributedMember getMyId() {
    return system.getDistributedMember();
  }

  @Override
  public Set<DistributedMember> getMembers() {
    return Collections
            .unmodifiableSet(dm.getOtherNormalDistributionManagerIds());
  }

  @Override
  public Set<DistributedMember> getAdminMembers() {
    return asDistributedMemberSet(dm.getAdminMemberSet());
  }

  @SuppressWarnings("unchecked")
  private Set<DistributedMember> asDistributedMemberSet(
          Set<InternalDistributedMember> internalDistributedMembers) {
    return (Set) internalDistributedMembers;
  }

  @Override
  public Set<DistributedMember> getMembers(Region region) {
    if (region instanceof DistributedRegion) {
      DistributedRegion distributedRegion = (DistributedRegion) region;
      return asDistributedMemberSet(distributedRegion.getDistributionAdvisor().adviseCacheOp());
    } else if (region instanceof PartitionedRegion) {
      PartitionedRegion partitionedRegion = (PartitionedRegion) region;
      return asDistributedMemberSet(partitionedRegion.getRegionAdvisor().adviseAllPRNodes());
    } else {
      return Collections.emptySet();
    }
  }

  @Override
  public Set<InetSocketAddress> getCurrentServers() {
    Map<String, Pool> pools = PoolManager.getAll();
    Set<InetSocketAddress> result = null;
    for (Pool pool : pools.values()) {
      PoolImpl poolImpl = (PoolImpl) pool;
      for (ServerLocation serverLocation : poolImpl.getCurrentServers()) {
        if (result == null) {
          result = new HashSet<>();
        }
        result.add(new InetSocketAddress(serverLocation.getHostName(), serverLocation.getPort()));
      }
    }
    if (result == null) {
      return Collections.emptySet();
    } else {
      return result;
    }
  }

  @Override
  public LogWriter getLogger() {
    return system.getLogWriter();
  }

  @Override
  public LogWriter getSecurityLogger() {
    return system.getSecurityLogWriter();
  }

  @Override
  public LogWriterI18n getLoggerI18n() {
    return system.getInternalLogWriter();
  }

  @Override
  public LogWriterI18n getSecurityLoggerI18n() {
    return system.getSecurityInternalLogWriter();
  }

  @Override
  public InternalLogWriter getInternalLogWriter() {
    return system.getInternalLogWriter();
  }

  @Override
  public InternalLogWriter getSecurityInternalLogWriter() {
    return system.getSecurityInternalLogWriter();
  }

  /**
   * get the threadId/sequenceId sweeper task for this cache
   *
   * @return the sweeper task
   */
  @Override
  public EventTrackerExpiryTask getEventTrackerTask() {
    return recordedEventSweeper;
  }

  @Override
  public CachePerfStats getCachePerfStats() {
    return cachePerfStats;
  }

  @Override
  public String getName() {
    return system.getName();
  }

  /**
   * Get the list of all instances of properties for Declarables with the given class name.
   *
   * @param className Class name of the declarable
   * @return List of all instances of properties found for the given declarable
   */
  @Override
  public List<Properties> getDeclarableProperties(final String className) {
    List<Properties> propertiesList = new ArrayList<>();
    synchronized (declarablePropertiesMap) {
      for (Map.Entry<Declarable, Properties> entry : declarablePropertiesMap.entrySet()) {
        if (entry.getKey().getClass().getName().equals(className)) {
          propertiesList.add(entry.getValue());
        }
      }
    }
    return propertiesList;
  }

  /**
   * Get the properties for the given declarable.
   *
   * @param declarable The declarable
   * @return Properties found for the given declarable
   */
  @Override
  public Properties getDeclarableProperties(final Declarable declarable) {
    return declarablePropertiesMap.get(declarable);
  }

  /**
   * Returns the number of seconds that have elapsed since the Cache was created.
   *
   * @since GemFire 3.5
   */
  @Override
  public int getUpTime() {
    return (int) (System.currentTimeMillis() - creationDate.getTime()) / 1000;
  }

  /**
   * All entry and region operations should be using this time rather than
   * System.currentTimeMillis(). Specially all version stamps/tags must be populated with this
   * timestamp.
   *
   * @return distributed cache time.
   */
  @Override
  public long cacheTimeMillis() {
    if (system != null) {
      return system.getClock().cacheTimeMillis();
    } else {
      return System.currentTimeMillis();
    }
  }

  @Override
  public <K, V> Region<K, V> createVMRegion(String name, RegionAttributes<K, V> aRegionAttributes)
          throws RegionExistsException, TimeoutException {
    return createRegion(name, aRegionAttributes);
  }

  private PoolFactory createDefaultPF() {
    PoolFactory defaultPoolFactory = PoolManager.createFactory();
    try {
      String localHostName = SocketCreator.getHostName(SocketCreator.getLocalHost());
      defaultPoolFactory.addServer(localHostName, CacheServer.DEFAULT_PORT);
    } catch (UnknownHostException ex) {
      throw new IllegalStateException("Could not determine local host name", ex);
    }
    return defaultPoolFactory;
  }

  private Pool findFirstCompatiblePool(Map<String, Pool> pools) {
    // act as if the default pool was configured
    // and see if we can find an existing one that is compatible
    PoolFactoryImpl pfi = (PoolFactoryImpl) createDefaultPF();
    for (Pool p : pools.values()) {
      if (((PoolImpl) p).isCompatible(pfi.getPoolAttributes())) {
        return p;
      }
    }
    return null;
  }

  private void addLocalHostAsServer(PoolFactory poolFactory) {
    PoolFactoryImpl poolFactoryImpl = (PoolFactoryImpl) poolFactory;
    if (poolFactoryImpl.getPoolAttributes().locators.isEmpty()
            && poolFactoryImpl.getPoolAttributes().servers.isEmpty()) {
      try {
        String localHostName = SocketCreator.getHostName(SocketCreator.getLocalHost());
        poolFactoryImpl.addServer(localHostName, CacheServer.DEFAULT_PORT);
      } catch (UnknownHostException ex) {
        throw new IllegalStateException("Could not determine local host name", ex);
      }
    }
  }

  /**
   * Used to set the default pool on a new GemFireCache.
   */
  @Override
  public synchronized void determineDefaultPool() {
    if (!isClient()) {
      throw new UnsupportedOperationException();
    }
    PoolFactory defaultPoolFactory = poolFactory;

    Pool pool = null;
    // create the pool if it does not already exist
    if (defaultPoolFactory == null) {
      Map<String, Pool> pools = PoolManager.getAll();
      if (pools.isEmpty()) {
        defaultPoolFactory = createDefaultPF();
      } else if (pools.size() == 1) {
        // otherwise use a singleton.
        pool = pools.values().iterator().next();
      } else {
        pool = findFirstCompatiblePool(pools);
        if (pool == null) {
          // if pool is still null then we will not have a default pool for this ClientCache
          defaultPool = null;
          return;
        }
      }
    } else {
      addLocalHostAsServer(defaultPoolFactory);

      // look for a pool that already exists that is compatible with
      // our PoolFactory.
      // If we don't find one we will create a new one that meets our needs.
      Map<String, Pool> pools = PoolManager.getAll();
      for (Pool p : pools.values()) {
        if (((PoolImpl) p)
                .isCompatible(((PoolFactoryImpl) defaultPoolFactory).getPoolAttributes())) {
          pool = p;
          break;
        }
      }
    }
    if (pool == null) {
      // create our pool with a unique name
      String poolName = DEFAULT_POOL_NAME;
      int count = 1;
      Map<String, Pool> pools = PoolManager.getAll();
      while (pools.containsKey(poolName)) {
        poolName = DEFAULT_POOL_NAME + count;
        count++;
      }
      pool = defaultPoolFactory.create(poolName);
    }
    defaultPool = pool;
  }

  /**
   * Determine whether the specified pool factory matches the pool factory used by this cache.
   *
   * @param poolFactory Prospective pool factory.
   * @throws IllegalStateException When the specified pool factory does not match.
   */
  @Override
  public void validatePoolFactory(PoolFactory poolFactory) {
    // If the specified pool factory is null, by definition there is no pool factory to validate.
    if (poolFactory != null && !Objects.equals(this.poolFactory, poolFactory)) {
      throw new IllegalStateException("Existing cache's default pool was not compatible");
    }
  }

  @Override
  public <K, V> Region<K, V> createRegion(String name, RegionAttributes<K, V> aRegionAttributes)
          throws RegionExistsException, TimeoutException {
    throwIfClient();
    return basicCreateRegion(name, aRegionAttributes);
  }

  @Override
  public <K, V> Region<K, V> basicCreateRegion(String name, RegionAttributes<K, V> attrs)
          throws RegionExistsException, TimeoutException {
    try {
      InternalRegionArguments ira = new InternalRegionArguments().setDestroyLockFlag(true)
              .setRecreateFlag(false).setSnapshotInputStream(null).setImageTarget(null);

      if (attrs instanceof UserSpecifiedRegionAttributes) {
        ira.setIndexes(((UserSpecifiedRegionAttributes) attrs).getIndexes());
      }
      return createVMRegion(name, attrs, ira);
    } catch (IOException | ClassNotFoundException e) {
      // only if loading snapshot, not here
      throw new InternalGemFireError(
              "unexpected exception", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static <K, V> Region<K, V> uncheckedRegion(Region region) {
    return region;
  }

  @Override
  public <K, V> Region<K, V> createVMRegion(String name, RegionAttributes<K, V> p_attrs,
                                            InternalRegionArguments internalRegionArgs)
          throws RegionExistsException, TimeoutException, IOException, ClassNotFoundException {

    if (getMyId().getVmKind() == ClusterDistributionManager.LOCATOR_DM_TYPE) {
      if (!internalRegionArgs.isUsedForMetaRegion()
              && internalRegionArgs.getInternalMetaRegion() == null) {
        throw new IllegalStateException("Regions can not be created in a locator.");
      }
    }
    stopper.checkCancelInProgress(null);
    RegionNameValidation.validate(name, internalRegionArgs);
    RegionAttributes<K, V> attrs = p_attrs;
    attrs = invokeRegionBefore(null, name, attrs, internalRegionArgs);
    if (attrs == null) {
      throw new IllegalArgumentException(
              "Attributes must not be null");
    }

    InternalRegion region;
    final InputStream snapshotInputStream = internalRegionArgs.getSnapshotInputStream();
    InternalDistributedMember imageTarget = internalRegionArgs.getImageTarget();
    final boolean recreate = internalRegionArgs.getRecreateFlag();

    final boolean isPartitionedRegion = attrs.getPartitionAttributes() != null;
    final boolean isReInitCreate = snapshotInputStream != null || imageTarget != null || recreate;

    try {
      for (;;) {
        getCancelCriterion().checkCancelInProgress(null);

        Future<InternalRegion> future = null;
        synchronized (rootRegions) {
          region = rootRegions.get(name);
          if (region != null) {
            throw new RegionExistsException(region);
          }
          // check for case where a root region is being reinitialized and we
          // didn't
          // find a region, i.e. the new region is about to be created

          if (!isReInitCreate) { // fix bug 33523
            String fullPath = Region.SEPARATOR + name;
            future = reinitializingRegions.get(fullPath);
          }
          if (future == null) {
            if (internalRegionArgs.getInternalMetaRegion() != null) {
              region = internalRegionArgs.getInternalMetaRegion();
            } else if (isPartitionedRegion) {
              region = new PartitionedRegion(name, attrs, null, this, internalRegionArgs);
            } else {
              // Abstract region depends on the default pool existing so lazily initialize it
              // if necessary.
              if (Objects.equals(attrs.getPoolName(), DEFAULT_POOL_NAME)) {
                determineDefaultPool();
              }
              if (attrs.getScope().isLocal()) {
                region = new LocalRegion(name, attrs, null, this, internalRegionArgs);
              } else {
                region = new DistributedRegion(name, attrs, null, this, internalRegionArgs);
              }
            }

            rootRegions.put(name, region);
            if (isReInitCreate) {
              regionReinitialized(region);
            }
            break;
          }
        } // synchronized

        boolean interrupted = Thread.interrupted();
        try { // future != null
          throw new RegionExistsException(future.get());
        } catch (InterruptedException ignore) {
          interrupted = true;
        } catch (ExecutionException e) {
          throw new Error("unexpected exception",
                  e);
        } catch (CancellationException e) {
          // future was cancelled
          if (logger.isTraceEnabled()) {
            logger.trace("future cancelled", e);
          }
        } finally {
          if (interrupted) {
            Thread.currentThread().interrupt();
          }
        }
      } // for

      boolean success = false;
      try {
        setRegionByPath(region.getFullPath(), region);
        region.preInitialize();
        region.initialize(snapshotInputStream, imageTarget, internalRegionArgs);
        success = true;
      } catch (CancelException | RedundancyAlreadyMetException e) {
        // don't print a call stack
        throw e;
      } catch (RuntimeException validationException) {
        logger.warn(String.format("Initialization failed for Region %s", region.getFullPath()),
                validationException);
        throw validationException;
      } finally {
        if (!success) {
          try {
            // do this before removing the region from
            // the root set to fix bug 41982.
            region.cleanupFailedInitialization();
          } catch (VirtualMachineError e) {
            SystemFailure.initiateFailure(e);
            throw e;
          } catch (Throwable t) {
            SystemFailure.checkFailure();
            stopper.checkCancelInProgress(t);

            // bug #44672 - log the failure but don't override the original exception
            logger.warn(String.format("Initialization failed for Region %s",
                    region.getFullPath()),
                    t);

          } finally {
            // clean up if initialize fails for any reason
            setRegionByPath(region.getFullPath(), null);
            synchronized (rootRegions) {
              Region rootRegion = rootRegions.get(name);
              if (rootRegion == region) {
                rootRegions.remove(name);
              }
            } // synchronized
          }
        } // success
      }

      region.postCreateRegion();
    } catch (RegionExistsException ex) {
      // outside of sync make sure region is initialized to fix bug 37563
      InternalRegion internalRegion = (InternalRegion) ex.getRegion();
      internalRegion.waitOnInitialization(); // don't give out ref until initialized
      throw ex;
    }

    invokeRegionAfter(region);

    // Added for M&M . Putting the callback here to avoid creating RegionMBean in case of Exception
    if (!region.isInternalRegion()) {
      system.handleResourceEvent(ResourceEvent.REGION_CREATE, region);
    }

    return uncheckedRegion(region);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> RegionAttributes<K, V> uncheckedRegionAttributes(RegionAttributes region) {
    return region;
  }

  @Override
  public <K, V> RegionAttributes<K, V> invokeRegionBefore(InternalRegion parent, String name,
                                                          RegionAttributes<K, V> attrs, InternalRegionArguments internalRegionArgs) {
    for (RegionListener listener : regionListeners) {
      attrs =
              uncheckedRegionAttributes(listener.beforeCreate(parent, name, attrs, internalRegionArgs));
    }
    return attrs;
  }

  @Override
  public void invokeRegionAfter(InternalRegion region) {
    for (RegionListener listener : regionListeners) {
      listener.afterCreate(region);
    }
  }

  @Override
  public void invokeBeforeDestroyed(InternalRegion region) {
    for (RegionListener listener : regionListeners) {
      listener.beforeDestroyed(region);
    }
  }

  @Override
  public void invokeCleanupFailedInitialization(InternalRegion region) {
    for (RegionListener listener : regionListeners) {
      listener.cleanupFailedInitialization(region);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Region getRegion(String path) {
    return getRegion(path, false);
  }

  /**
   * returns a set of all current regions in the cache, including buckets
   *
   * @since GemFire 6.0
   */
  @Override
  public Set<InternalRegion> getAllRegions() {
    Set<InternalRegion> result = new HashSet<>();
    synchronized (rootRegions) {
      for (Region region : rootRegions.values()) {
        if (region instanceof PartitionedRegion) {
          PartitionedRegion partitionedRegion = (PartitionedRegion) region;
          PartitionedRegionDataStore dataStore = partitionedRegion.getDataStore();
          if (dataStore != null) {
            Set<Map.Entry<Integer, BucketRegion>> bucketEntries =
                    partitionedRegion.getDataStore().getAllLocalBuckets();
            for (Map.Entry entry : bucketEntries) {
              result.add((InternalRegion) entry.getValue());
            }
          }
        } else if (region instanceof InternalRegion) {
          InternalRegion internalRegion = (InternalRegion) region;
          result.add(internalRegion);
          result.addAll(internalRegion.basicSubregions(true));
        }
      }
    }
    return result;
  }

  @Override
  public Set<InternalRegion> getApplicationRegions() {
    Set<InternalRegion> result = new HashSet<>();
    synchronized (rootRegions) {
      for (Object region : rootRegions.values()) {
        InternalRegion internalRegion = (InternalRegion) region;
        if (internalRegion.isInternalRegion()) {
          continue; // Skip internal regions
        }
        result.add(internalRegion);
        result.addAll(internalRegion.basicSubregions(true));
      }
    }
    return result;
  }

  @Override
  public boolean hasPersistentRegion() {
    synchronized (rootRegions) {
      for (InternalRegion region : rootRegions.values()) {
        if (region.getDataPolicy().withPersistence()) {
          return true;
        }
        for (InternalRegion subRegion : region.basicSubregions(true)) {
          if (subRegion.getDataPolicy().withPersistence()) {
            return true;
          }
        }
      }
      return false;
    }
  }

  @Override
  public void setRegionByPath(String path, InternalRegion r) {
    if (r == null) {
      pathToRegion.remove(path);
    } else {
      pathToRegion.put(path, r);
    }
  }

  /**
   * @throws IllegalArgumentException if path is not valid
   */
  private static void validatePath(String path) {
    if (path == null) {
      throw new IllegalArgumentException(
              "path cannot be null");
    }
    if (path.isEmpty()) {
      throw new IllegalArgumentException(
              "path cannot be empty");
    }
    if (path.equals(Region.SEPARATOR)) {
      throw new IllegalArgumentException(
              String.format("path cannot be ' %s '", Region.SEPARATOR));
    }
  }

  @Override
  public InternalRegion getRegionByPath(String path) {
    validatePath(path); // fix for bug 34892

    // do this before checking the pathToRegion map
    InternalRegion result = getReinitializingRegion(path);
    if (result != null) {
      return result;
    }
    return pathToRegion.get(path);
  }

  @Override
  public InternalRegion getRegionByPathForProcessing(String path) {
    InternalRegion result = getRegionByPath(path);
    if (result == null) {
      stopper.checkCancelInProgress(null);
      final InitializationLevel oldLevel =
              LocalRegion.setThreadInitLevelRequirement(InitializationLevel.ANY_INIT); // go through
      // initialization latches
      try {
        String[] pathParts = parsePath(path);
        InternalRegion rootRegion;
        synchronized (rootRegions) {
          rootRegion = rootRegions.get(pathParts[0]);
          if (rootRegion == null)
            return null;
        }
        if (logger.isDebugEnabled()) {
          logger.debug("GemFireCache.getRegion, calling getSubregion on rootRegion({}): {}",
                  pathParts[0], pathParts[1]);
        }
        result = (InternalRegion) rootRegion.getSubregion(pathParts[1], true);
      } finally {
        LocalRegion.setThreadInitLevelRequirement(oldLevel);
      }
    }
    return result;
  }

  /**
   * @param returnDestroyedRegion if true, okay to return a destroyed region
   */
  @Override
  public Region getRegion(String path, boolean returnDestroyedRegion) {
    stopper.checkCancelInProgress(null);

    InternalRegion result = getRegionByPath(path);
    // Do not waitOnInitialization() for PR
    if (result != null) {
      result.waitOnInitialization();
      if (!returnDestroyedRegion && result.isDestroyed()) {
        stopper.checkCancelInProgress(null);
        return null;
      } else {
        return result;
      }
    }

    String[] pathParts = parsePath(path);
    InternalRegion rootRegion;
    synchronized (rootRegions) {
      rootRegion = rootRegions.get(pathParts[0]);
      if (rootRegion == null) {
        if (logger.isDebugEnabled()) {
          logger.debug("GemFireCache.getRegion, no region found for {}", pathParts[0]);
        }
        stopper.checkCancelInProgress(null);
        return null;
      }
      if (!returnDestroyedRegion && rootRegion.isDestroyed()) {
        stopper.checkCancelInProgress(null);
        return null;
      }
    }
    if (logger.isDebugEnabled()) {
      logger.debug("GemFireCache.getRegion, calling getSubregion on rootRegion({}): {}",
              pathParts[0], pathParts[1]);
    }
    return rootRegion.getSubregion(pathParts[1], returnDestroyedRegion);
  }

  /** Return true if this region is initializing */
  @Override
  public boolean isGlobalRegionInitializing(String fullPath) {
    stopper.checkCancelInProgress(null);
    final InitializationLevel oldLevel =
            LocalRegion.setThreadInitLevelRequirement(InitializationLevel.ANY_INIT); // go through
    // initialization latches
    try {
      return isGlobalRegionInitializing((InternalRegion) getRegion(fullPath));
    } finally {
      LocalRegion.setThreadInitLevelRequirement(oldLevel);
    }
  }

  /** Return true if this region is initializing */
  private boolean isGlobalRegionInitializing(InternalRegion region) {
    boolean result = region != null && region.getScope().isGlobal() && !region.isInitialized();
    if (result) {
      if (logger.isDebugEnabled()) {
        logger.debug("GemFireCache.isGlobalRegionInitializing ({})", region.getFullPath());
      }
    }
    return result;
  }

  @Override
  public Set<Region<?, ?>> rootRegions() {
    return rootRegions(false);
  }

  @Override
  public Set<Region<?, ?>> rootRegions(boolean includePRAdminRegions) {
    return rootRegions(includePRAdminRegions, true);
  }

  private Set<Region<?, ?>> rootRegions(boolean includePRAdminRegions, boolean waitForInit) {
    stopper.checkCancelInProgress(null);
    Set<Region<?, ?>> regions = new HashSet<>();
    synchronized (rootRegions) {
      for (InternalRegion region : rootRegions.values()) {
        // If this is an internal meta-region, don't return it to end user
        if (region.isSecret() || region.isUsedForMetaRegion()
                || !includePRAdminRegions && (region.isUsedForPartitionedRegionAdmin()
                || region.isUsedForPartitionedRegionBucket())) {
          continue; // Skip administrative PartitionedRegions
        }
        regions.add(region);
      }
    }
    if (waitForInit) {
      for (Iterator<Region<?, ?>> iterator = regions.iterator(); iterator.hasNext();) {
        InternalRegion region = (InternalRegion) iterator.next();
        if (!region.checkForInitialization()) {
          iterator.remove();
        }
      }
    }
    return Collections.unmodifiableSet(regions);
  }

  /**
   * Called by notifier when a client goes away
   *
   * @since GemFire 5.7
   */
  @Override
  public void cleanupForClient(CacheClientNotifier ccn, ClientProxyMembershipID client) {
    try {
      if (isClosed()) {
        return;
      }
      for (Object region : rootRegions(false, false)) {
        InternalRegion internalRegion = (InternalRegion) region;
        internalRegion.cleanupForClient(ccn, client);
      }
    } catch (DistributedSystemDisconnectedException ignore) {
    }
  }

  private boolean isInitialized() {
    return isInitialized;
  }

  @Override
  public boolean isClosed() {
    return isClosing;
  }

  @Override
  public int getLockTimeout() {
    return lockTimeout;
  }

  @Override
  public void setLockTimeout(int seconds) {
    throwIfClient();
    stopper.checkCancelInProgress(null);
    lockTimeout = seconds;
  }

  @Override
  public int getLockLease() {
    return lockLease;
  }

  @Override
  public void setLockLease(int seconds) {
    throwIfClient();
    stopper.checkCancelInProgress(null);
    lockLease = seconds;
  }

  @Override
  public int getSearchTimeout() {
    return searchTimeout;
  }

  @Override
  public void setSearchTimeout(int seconds) {
    throwIfClient();
    stopper.checkCancelInProgress(null);
    searchTimeout = seconds;
  }

  @Override
  public int getMessageSyncInterval() {
    return HARegionQueue.getMessageSyncInterval();
  }

  @Override
  public void setMessageSyncInterval(int seconds) {
    throwIfClient();
    stopper.checkCancelInProgress(null);
    if (seconds < 0) {
      throw new IllegalArgumentException(
              "The 'messageSyncInterval' property for cache cannot be negative");
    }
    HARegionQueue.setMessageSyncInterval(seconds);
  }

  /**
   * Get a reference to a Region that is reinitializing, or null if that Region is not
   * reinitializing or this thread is interrupted. If a reinitializing region is found, then this
   * method blocks until reinitialization is complete and then returns the region.
   */
  @Override
  public InternalRegion getReinitializingRegion(String fullPath) {
    Future<InternalRegion> future = reinitializingRegions.get(fullPath);
    if (future == null) {
      return null;
    }
    try {
      InternalRegion region = future.get();
      region.waitOnInitialization();
      if (logger.isDebugEnabled()) {
        logger.debug("Returning manifested future for: {}", fullPath);
      }
      return region;
    } catch (InterruptedException ignore) {
      Thread.currentThread().interrupt();
      return null;
    } catch (ExecutionException e) {
      throw new Error("unexpected exception", e);
    } catch (CancellationException ignore) {
      // future was cancelled
      logger.debug("future cancelled, returning null");
      return null;
    }
  }

  /**
   * Register the specified region name as reinitializing, creating and adding a Future for it to
   * the map.
   *
   * @throws IllegalStateException if there is already a region by that name registered.
   */
  @Override
  public void regionReinitializing(String fullPath) {
    Object old = reinitializingRegions.putIfAbsent(fullPath, new FutureResult<>(stopper));
    if (old != null) {
      throw new IllegalStateException(
              String.format("Found an existing reinitalizing region named %s",
                      fullPath));
    }
  }

  /**
   * Set the reinitialized region and unregister it as reinitializing.
   *
   * @throws IllegalStateException if there is no region by that name registered as reinitializing.
   */
  @Override
  public void regionReinitialized(Region region) {
    String regionName = region.getFullPath();
    FutureResult<InternalRegion> future = reinitializingRegions.get(regionName);
    if (future == null) {
      throw new IllegalStateException(
              String.format("Could not find a reinitializing region named %s",
                      regionName));
    }
    future.set((InternalRegion) region);
    unregisterReinitializingRegion(regionName);
  }

  /**
   * Clear a reinitializing region, e.g. reinitialization failed.
   *
   * @throws IllegalStateException if cannot find reinitializing region registered by that name.
   */
  @Override
  public void unregisterReinitializingRegion(String fullPath) {
    reinitializingRegions.remove(fullPath);
  }

  /**
   * Returns true if get should give a copy; false if a reference.
   *
   * @since GemFire 4.0
   */
  @Override
  public boolean isCopyOnRead() {
    return copyOnRead;
  }

  /**
   * Implementation of {@link Cache#setCopyOnRead}
   *
   * @since GemFire 4.0
   */
  @Override
  public void setCopyOnRead(boolean copyOnRead) {
    this.copyOnRead = copyOnRead;
  }

  /**
   * Implementation of {@link Cache#getCopyOnRead}
   *
   * @since GemFire 4.0
   */
  @Override
  public boolean getCopyOnRead() {
    return copyOnRead;
  }

  /**
   * Remove the specified root region
   *
   * @param rootRgn the region to be removed
   * @return true if root region was removed, false if not found
   */
  @Override
  public boolean removeRoot(InternalRegion rootRgn) {
    synchronized (rootRegions) {
      String regionName = rootRgn.getName();
      InternalRegion found = rootRegions.get(regionName);
      if (found == rootRgn) {
        InternalRegion previous = rootRegions.remove(regionName);
        Assert.assertTrue(previous == rootRgn);
        return true;
      } else
        return false;
    }
  }

  /**
   * @return array of two Strings, the root name and the relative path from root. If there is no
   *         relative path from root, then String[1] will be an empty string
   */
  static String[] parsePath(String path) {
    validatePath(path);
    String[] result = new String[2];
    result[1] = "";
    // strip off root name from path
    int slashIndex = path.indexOf(Region.SEPARATOR_CHAR);
    if (slashIndex == 0) {
      path = path.substring(1);
      slashIndex = path.indexOf(Region.SEPARATOR_CHAR);
    }
    result[0] = path;
    if (slashIndex > 0) {
      result[0] = path.substring(0, slashIndex);
      result[1] = path.substring(slashIndex + 1);
    }
    return result;
  }

  /**
   * Makes note of a {@code CacheLifecycleListener}
   */
  public static void addCacheLifecycleListener(CacheLifecycleListener listener) {
    synchronized (MyCacheImpl.class) {
      cacheLifecycleListeners.add(listener);
    }
  }

  /**
   * Removes a {@code CacheLifecycleListener}
   *
   * @return Whether or not the listener was removed
   */
  public static boolean removeCacheLifecycleListener(CacheLifecycleListener listener) {
    synchronized (MyCacheImpl.class) {
      return cacheLifecycleListeners.remove(listener);
    }
  }

  @Override
  public void addRegionListener(RegionListener regionListener) {
    regionListeners.add(regionListener);
  }

  @Override
  public void removeRegionListener(RegionListener regionListener) {
    regionListeners.remove(regionListener);
  }

  @Override
  public Set<RegionListener> getRegionListeners() {
    return Collections.unmodifiableSet(regionListeners);
  }

  @Override
  public <T extends CacheService> T getService(Class<T> clazz) {
    return clazz.cast(services.get(clazz));
  }

  @Override
  public Collection<CacheService> getServices() {
    return Collections.unmodifiableCollection(services.values());
  }

  /**
   * Creates the single instance of the Transaction Manager for this cache. Returns the existing one
   * upon request.
   *
   * @return the CacheTransactionManager instance.
   *
   * @since GemFire 4.0
   */
  @Override
  public CacheTransactionManager getCacheTransactionManager() {
    return transactionManager;
  }

  /**
   * GuardedBy {@link #ccpTimerMutex}
   *
   * @see CacheClientProxy
   */
  private SystemTimer ccpTimer;

  /**
   * @see #ccpTimer
   */
  private final Object ccpTimerMutex = new Object();

  /**
   * Get cache-wide CacheClientProxy SystemTimer
   *
   * @return the timer, lazily created
   */
  @Override
  public SystemTimer getCCPTimer() {
    synchronized (ccpTimerMutex) {
      if (ccpTimer != null) {
        return ccpTimer;
      }
      ccpTimer = new SystemTimer(getDistributedSystem(), true);
      if (isClosing) {
        ccpTimer.cancel(); // poison it, don't throw.
      }
      return ccpTimer;
    }
  }

  /**
   * For use by unit tests to inject a mocked ccpTimer
   */
  void setCCPTimer(SystemTimer ccpTimer) {
    this.ccpTimer = ccpTimer;
  }

  static final int PURGE_INTERVAL = 1000;

  private int cancelCount = 0;

  /**
   * Does a periodic purge of the CCPTimer to prevent a large number of cancelled tasks from
   * building up in it. See GEODE-2485.
   */
  @Override
  public void purgeCCPTimer() {
    synchronized (ccpTimerMutex) {
      if (ccpTimer != null) {
        cancelCount++;
        if (cancelCount == PURGE_INTERVAL) {
          cancelCount = 0;
          ccpTimer.timerPurge();
        }
      }
    }
  }

  private final ExpirationScheduler expirationScheduler;

  /**
   * Get cache-wide ExpirationScheduler
   *
   * @return the scheduler, lazily created
   */
  @Override
  public ExpirationScheduler getExpirationScheduler() {
    return expirationScheduler;
  }

  @Override
  public TXManagerImpl getTXMgr() {
    return transactionManager;
  }

  /**
   * Returns the {@code Executor} (thread pool) that is used to execute cache event listeners.
   * Returns {@code null} if no pool exists.
   *
   * @since GemFire 3.5
   */
  @Override
  public Executor getEventThreadPool() {
    return eventThreadPool;
  }

  @Override
  public CacheServer addCacheServer() {
    throwIfClient();
    stopper.checkCancelInProgress(null);

    InternalCacheServer server = new ServerBuilder(this, securityService).createServer();
    allCacheServers.add(server);

    sendAddCacheServerProfileMessage();
    return server;
  }

  @Override
  public boolean removeCacheServer(CacheServer cacheServer) {
    boolean removed = allCacheServers.remove(cacheServer);
    sendRemoveCacheServerProfileMessage();
    return removed;
  }

  @Override
  public void addGatewaySender(GatewaySender sender) {
    throwIfClient();

    stopper.checkCancelInProgress(null);

    synchronized (allGatewaySendersLock) {
      if (!allGatewaySenders.contains(sender)) {
        new UpdateAttributesProcessor((DistributionAdvisee) sender).distribute(true);
        Set<GatewaySender> newSenders = new HashSet<>(allGatewaySenders.size() + 1);
        if (!allGatewaySenders.isEmpty()) {
          newSenders.addAll(allGatewaySenders);
        }
        newSenders.add(sender);
        allGatewaySenders = Collections.unmodifiableSet(newSenders);
      } else {
        throw new IllegalStateException(
                String.format("A GatewaySender with id %s is already defined in this cache.",
                        sender.getId()));
      }
    }

    synchronized (rootRegions) {
      Set<InternalRegion> applicationRegions = getApplicationRegions();
      for (InternalRegion region : applicationRegions) {
        Set<String> senders = region.getAllGatewaySenderIds();
        if (senders.contains(sender.getId()) && !sender.isParallel()) {
          region.senderCreated();
        }
      }
    }

    if (!sender.isParallel()) {
      Region dynamicMetaRegion = getRegion(DynamicRegionFactory.DYNAMIC_REGION_LIST_NAME);
      if (dynamicMetaRegion == null) {
        if (logger.isDebugEnabled()) {
          logger.debug(" The dynamic region is null. ");
        }
      } else {
        dynamicMetaRegion.getAttributesMutator().addGatewaySenderId(sender.getId());
      }
    }
    if (!(sender.getRemoteDSId() < 0)) {
      system.handleResourceEvent(ResourceEvent.GATEWAYSENDER_CREATE, sender);
    }
  }

  @Override
  public void removeGatewaySender(GatewaySender sender) {
    throwIfClient();

    stopper.checkCancelInProgress(null);

    synchronized (allGatewaySendersLock) {
      if (allGatewaySenders.contains(sender)) {
        new UpdateAttributesProcessor((DistributionAdvisee) sender, true).distribute(true);
        Set<GatewaySender> newSenders = new HashSet<>(allGatewaySenders.size() - 1);
        if (!allGatewaySenders.isEmpty()) {
          newSenders.addAll(allGatewaySenders);
        }
        newSenders.remove(sender);
        allGatewaySenders = Collections.unmodifiableSet(newSenders);
      }
    }
    if (!(sender.getRemoteDSId() < 0)) {
      system.handleResourceEvent(ResourceEvent.GATEWAYSENDER_REMOVE, sender);
    }
  }

  @Override
  public InternalCacheServer addGatewayReceiverServer(GatewayReceiver receiver) {
    throwIfClient();
    stopper.checkCancelInProgress(null);

    requireNonNull(receiver, "GatewayReceiver must be supplied to add a server endpoint.");
    requireNonNull(gatewayReceiver.get(),
            "GatewayReceiver must be added before adding a server endpoint.");

    InternalCacheServer receiverServer = new ServerBuilder(this, securityService)
            .forGatewayReceiver(receiver).createServer();
    gatewayReceiverServer.set(receiverServer);

    sendAddCacheServerProfileMessage();
    return receiverServer;
  }

  @Override
  public boolean removeGatewayReceiverServer(InternalCacheServer receiverServer) {
    boolean removed = gatewayReceiverServer.compareAndSet(receiverServer, null);
    sendRemoveCacheServerProfileMessage();
    return removed;
  }

  @Override
  public void addGatewayReceiver(GatewayReceiver receiver) {
    throwIfClient();
    stopper.checkCancelInProgress(null);
    requireNonNull(receiver, "GatewayReceiver must be supplied.");
    gatewayReceiver.set(receiver);
  }

  @Override
  public void removeGatewayReceiver(GatewayReceiver receiver) {
    throwIfClient();
    stopper.checkCancelInProgress(null);
    gatewayReceiver.set(null);
  }

  @Override
  public void addAsyncEventQueue(AsyncEventQueueImpl asyncQueue) {
    allAsyncEventQueues.add(asyncQueue);
    if (!asyncQueue.isMetaQueue()) {
      allVisibleAsyncEventQueues.add(asyncQueue);
    }
    system.handleResourceEvent(ResourceEvent.ASYNCEVENTQUEUE_CREATE, asyncQueue);
  }

  /**
   * Returns List of GatewaySender (excluding the senders for internal use)
   *
   * @return List List of GatewaySender objects
   */
  @Override
  public Set<GatewaySender> getGatewaySenders() {
    Set<GatewaySender> senders = new HashSet<>();
    for (GatewaySender sender : allGatewaySenders) {
      if (!((AbstractGatewaySender) sender).isForInternalUse()) {
        senders.add(sender);
      }
    }
    return senders;
  }

  /**
   * Returns List of all GatewaySenders (including the senders for internal use)
   *
   * @return List List of GatewaySender objects
   */
  @Override
  public Set<GatewaySender> getAllGatewaySenders() {
    return allGatewaySenders;
  }

  @Override
  public GatewaySender getGatewaySender(String id) {
    for (GatewaySender sender : allGatewaySenders) {
      if (sender.getId().equals(id)) {
        return sender;
      }
    }
    return null;
  }

  @Override
  public Set<GatewayReceiver> getGatewayReceivers() {
    GatewayReceiver receiver = gatewayReceiver.get();
    if (receiver == null) {
      return Collections.emptySet();
    }
    return Collections.singleton(receiver);
  }

  @Override
  public Set<AsyncEventQueue> getAsyncEventQueues() {
    return getAsyncEventQueues(true);
  }

  @Override
  public Set<AsyncEventQueue> getAsyncEventQueues(boolean visibleOnly) {
    return visibleOnly ? allVisibleAsyncEventQueues : allAsyncEventQueues;
  }

  @Override
  public AsyncEventQueue getAsyncEventQueue(String id) {
    for (AsyncEventQueue asyncEventQueue : allAsyncEventQueues) {
      if (asyncEventQueue.getId().equals(id)) {
        return asyncEventQueue;
      }
    }
    return null;
  }

  @Override
  public void removeAsyncEventQueue(AsyncEventQueue asyncQueue) {
    throwIfClient();
    // first remove the gateway sender of the queue
    if (asyncQueue instanceof AsyncEventQueueImpl) {
      removeGatewaySender(((AsyncEventQueueImpl) asyncQueue).getSender());
    }
    // using gateway senders lock since async queue uses a gateway sender
    synchronized (allGatewaySendersLock) {
      allAsyncEventQueues.remove(asyncQueue);
      allVisibleAsyncEventQueues.remove(asyncQueue);
    }
    system.handleResourceEvent(ResourceEvent.ASYNCEVENTQUEUE_REMOVE, asyncQueue);
  }

  /** get the conflict resolver for WAN */
  @Override
  public GatewayConflictResolver getGatewayConflictResolver() {
    synchronized (allGatewayHubsLock) {
      return gatewayConflictResolver;
    }
  }

  /** set the conflict resolver for WAN */
  @Override
  public void setGatewayConflictResolver(GatewayConflictResolver resolver) {
    synchronized (allGatewayHubsLock) {
      gatewayConflictResolver = resolver;
    }
  }

  @Override
  public List<CacheServer> getCacheServers() {
    return unmodifiableAllCacheServers;
  }

  @Override
  public List<InternalCacheServer> getCacheServersAndGatewayReceiver() {
    List<InternalCacheServer> allServers = new ArrayList<>(allCacheServers);

    InternalCacheServer receiverServer = gatewayReceiverServer.get();
    if (receiverServer != null) {
      allServers.add(receiverServer);
    }

    return Collections.unmodifiableList(allServers);
  }

  /**
   * add a partitioned region to the set of tracked partitioned regions. This is used to notify the
   * regions when this cache requires, or does not require notification of all region/entry events.
   */
  @Override
  public void addPartitionedRegion(PartitionedRegion region) {
    synchronized (partitionedRegions) {
      if (region.isDestroyed()) {
        if (logger.isDebugEnabled()) {
          logger.debug("GemFireCache#addPartitionedRegion did not add destroyed {}", region);
        }
        return;
      }
      if (partitionedRegions.add(region)) {
        getCachePerfStats().incPartitionedRegions(1);
      }
    }
  }

  /**
   * Returns a set of all current partitioned regions for test hook.
   */
  @Override
  public Set<PartitionedRegion> getPartitionedRegions() {
    synchronized (partitionedRegions) {
      return new HashSet<>(partitionedRegions);
    }
  }

  private SortedMap<String, Map<String, PartitionedRegion>> getPRTrees() {
    // prTree will save a sublist of PRs who are under the same root
    SortedMap<String, PartitionedRegion> prMap = getPartitionedRegionMap();
    boolean hasColocatedRegion = false;
    for (PartitionedRegion pr : prMap.values()) {
      List<PartitionedRegion> childList = ColocationHelper.getColocatedChildRegions(pr);
      if (childList != null && !childList.isEmpty()) {
        hasColocatedRegion = true;
        break;
      }
    }

    TreeMap<String, Map<String, PartitionedRegion>> prTrees = new TreeMap<>();
    if (hasColocatedRegion) {
      Map<String, PartitionedRegion> orderedPrMap = orderByColocation(prMap);
      prTrees.put("ROOT", orderedPrMap);
    } else {
      for (PartitionedRegion pr : prMap.values()) {
        String rootName = pr.getRoot().getName();
        Map<String, PartitionedRegion> prSubMap =
                prTrees.computeIfAbsent(rootName, k -> new TreeMap<>());
        prSubMap.put(pr.getFullPath(), pr);
      }
    }

    return prTrees;
  }

  private SortedMap<String, PartitionedRegion> getPartitionedRegionMap() {
    SortedMap<String, PartitionedRegion> prMap = new TreeMap<>();
    for (Map.Entry<String, InternalRegion> entry : pathToRegion.entrySet()) {
      String regionName = entry.getKey();
      InternalRegion region = entry.getValue();

      // Don't wait for non partitioned regions
      if (!(region instanceof PartitionedRegion)) {
        continue;
      }
      // Do a getRegion to ensure that we wait for the partitioned region
      // to finish initialization
      try {
        Region pr = getRegion(regionName);
        if (pr instanceof PartitionedRegion) {
          prMap.put(regionName, (PartitionedRegion) pr);
        }
      } catch (CancelException ignore) {
        // if some region throws cancel exception during initialization,
        // then no need to shutDownAll them gracefully
      }
    }

    return prMap;
  }

  private Map<String, PartitionedRegion> orderByColocation(Map<String, PartitionedRegion> prMap) {
    LinkedHashMap<String, PartitionedRegion> orderedPrMap = new LinkedHashMap<>();
    for (PartitionedRegion pr : prMap.values()) {
      addColocatedChildRecursively(orderedPrMap, pr);
    }
    return orderedPrMap;
  }

  private void addColocatedChildRecursively(LinkedHashMap<String, PartitionedRegion> prMap,
                                            PartitionedRegion pr) {
    for (PartitionedRegion colocatedRegion : ColocationHelper.getColocatedChildRegions(pr)) {
      addColocatedChildRecursively(prMap, colocatedRegion);
    }
    prMap.put(pr.getFullPath(), pr);
  }

  /**
   * check to see if any cache components require notification from a partitioned region.
   * Notification adds to the messaging a PR must do on each put/destroy/invalidate operation and
   * should be kept to a minimum
   *
   * @param region the partitioned region
   * @return true if the region should deliver all of its events to this cache
   */
  @Override
  public boolean requiresNotificationFromPR(PartitionedRegion region) {
    boolean hasSerialSenders = hasSerialSenders(region);

    if (!hasSerialSenders) {
      for (InternalCacheServer server : allCacheServers) {
        if (!server.getNotifyBySubscription()) {
          hasSerialSenders = true;
          break;
        }
      }
    }

    if (!hasSerialSenders) {
      InternalCacheServer receiverServer = gatewayReceiverServer.get();
      if (receiverServer != null && !receiverServer.getNotifyBySubscription()) {
        hasSerialSenders = true;
      }
    }

    return hasSerialSenders;
  }

  private boolean hasSerialSenders(PartitionedRegion region) {
    boolean hasSenders = false;
    Set<String> senders = region.getAllGatewaySenderIds();
    for (String sender : senders) {
      GatewaySender gatewaySender = getGatewaySender(sender);
      if (gatewaySender != null && !gatewaySender.isParallel()) {
        hasSenders = true;
        break;
      }
    }
    return hasSenders;
  }

  /**
   * remove a partitioned region from the set of tracked instances.
   *
   * @see #addPartitionedRegion(PartitionedRegion)
   */
  @Override
  public void removePartitionedRegion(PartitionedRegion region) {
    synchronized (partitionedRegions) {
      if (partitionedRegions.remove(region)) {
        getCachePerfStats().incPartitionedRegions(-1);
      }
    }
  }

  @Override
  public void setIsServer(boolean isServer) {
    throwIfClient();
    stopper.checkCancelInProgress(null);

    this.isServer = isServer;
  }

  @Override
  public boolean isServer() {
    if (isClient()) {
      return false;
    }
    stopper.checkCancelInProgress(null);

    return isServer || !allCacheServers.isEmpty();
  }

  @Override
  public InternalQueryService getQueryService() {
    if (!isClient()) {
      return new DefaultQueryService(this);
    }
    Pool defaultPool = getDefaultPool();
    if (defaultPool == null) {
      throw new IllegalStateException(
              "Client cache does not have a default pool. Use getQueryService(String poolName) instead.");
    }
    return (InternalQueryService) defaultPool.getQueryService();
  }

  @Override
  public JSONFormatter getJsonFormatter() {
    // only ProxyCache implementation needs a JSONFormatter that has reference to userAttributes
    return new JSONFormatter();
  }

  @Override
  public QueryService getLocalQueryService() {
    return new DefaultQueryService(this);
  }

  /**
   * @return Context jndi context associated with the Cache.
   * @since GemFire 4.0
   */
  @Override
  public Context getJNDIContext() {
    return JNDIInvoker.getJNDIContext();
  }

  /**
   * @return JTA TransactionManager associated with the Cache.
   * @since GemFire 4.0
   */
  @Override
  public TransactionManager getJTATransactionManager() {
    return JNDIInvoker.getTransactionManager();
  }

  /**
   * return the cq/interest information for a given region name, creating one if it doesn't exist
   */
  @Override
  public FilterProfile getFilterProfile(String regionName) {
    InternalRegion r = (InternalRegion) getRegion(regionName, true);
    if (r != null) {
      return r.getFilterProfile();
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static <K, V> Map<String, RegionAttributes<K, V>> uncheckedCast(
          Map<String, RegionAttributes<?, ?>> namedRegionAttributes) {
    return (Map) namedRegionAttributes;
  }

  @Override
  public <K, V> RegionAttributes<K, V> getRegionAttributes(String id) {
    return MyCacheImpl.<K, V>uncheckedCast(namedRegionAttributes).get(id);
  }

  @Override
  public <K, V> void setRegionAttributes(String id, RegionAttributes<K, V> attrs) {
    if (attrs == null) {
      namedRegionAttributes.remove(id);
    } else {
      namedRegionAttributes.put(id, attrs);
    }
  }

  @Override
  public <K, V> Map<String, RegionAttributes<K, V>> listRegionAttributes() {
    return Collections.unmodifiableMap(uncheckedCast(namedRegionAttributes));
  }

  private static final ThreadLocal<MyCacheImpl> xmlCache = new ThreadLocal<>();

  @Override
  public void loadCacheXml(InputStream is)
          throws TimeoutException, CacheWriterException, GatewayException, RegionExistsException {
    // make this cache available to callbacks being initialized during xml create
    final MyCacheImpl oldValue = xmlCache.get();
    xmlCache.set(this);

    Reader reader = null;
    Writer stringWriter = null;
    OutputStreamWriter writer = null;

    try {
      CacheXmlParser xml;

      if (XML_PARAMETERIZATION_ENABLED) {
        char[] buffer = new char[1024];
        reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.ISO_8859_1));
        stringWriter = new StringWriter();

        int numChars;
        while ((numChars = reader.read(buffer)) != -1) {
          stringWriter.write(buffer, 0, numChars);
        }

        /*
         * Now replace all replaceable system properties here using {@code PropertyResolver}
         */
        String replacedXmlString = resolver.processUnresolvableString(stringWriter.toString());
        /*
         * Turn the string back into the default encoding so that the XML parser can work correctly
         * in the presence of an "encoding" attribute in the XML prolog.
         */
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer = new OutputStreamWriter(baos, StandardCharsets.ISO_8859_1);
        writer.write(replacedXmlString);
        writer.flush();

        xml = CacheXmlParser.parse(new ByteArrayInputStream(baos.toByteArray()));
      } else {
        xml = CacheXmlParser.parse(is);
      }
      xml.create(this);
    } catch (IOException e) {
      throw new CacheXmlException(
              "Input Stream could not be read for system property substitutions.", e);
    } finally {
      xmlCache.set(oldValue);
      closeQuietly(reader);
      closeQuietly(stringWriter);
      closeQuietly(writer);
    }
  }

  private static void closeQuietly(Closeable closeable) {
    try {
      if (closeable != null) {
        closeable.close();
      }
    } catch (IOException ignore) {
    }
  }

  @Override
  public void readyForEvents() {
    if (isClient()) {
      // If a durable client has been configured...
      if (Objects.nonNull(system) && Objects.nonNull(system.getConfig())
              && !Objects.equals(DistributionConfig.DEFAULT_DURABLE_CLIENT_ID,
              Objects.toString(system.getConfig().getDurableClientId(),
                      DistributionConfig.DEFAULT_DURABLE_CLIENT_ID))) {
        // Ensure that there is a pool to use for readyForEvents().
        if (Objects.isNull(defaultPool)) {
          determineDefaultPool();
        }
      }
    }
    PoolManagerImpl.readyForEvents(system, false);
  }

  private List<File> backupFiles = Collections.emptyList();

  @Override
  public ResourceManager getResourceManager() {
    return getInternalResourceManager(true);
  }

  @Override
  public InternalResourceManager getInternalResourceManager() {
    return getInternalResourceManager(true);
  }

  @Override
  public InternalResourceManager getInternalResourceManager(boolean checkCancellationInProgress) {
    if (checkCancellationInProgress) {
      stopper.checkCancelInProgress(null);
    }
    return resourceManager;
  }

  @Override
  public void setBackupFiles(List<File> backups) {
    backupFiles = backups;
  }

  @Override
  public List<File> getBackupFiles() {
    return Collections.unmodifiableList(backupFiles);
  }

  @Override
  public BackupService getBackupService() {
    return backupService;
  }

  // TODO make this a simple int guarded by riWaiters and get rid of the double-check
  private final AtomicInteger registerInterestsInProgress = new AtomicInteger();

  private final List<MyCacheImpl.SimpleWaiter> riWaiters = new ArrayList<>();

  // never changes but is currently only initialized in constructor by unit tests
  private TypeRegistry pdxRegistry;

  /**
   * update stats for completion of a registerInterest operation
   */
  @Override
  public void registerInterestCompleted() {
    // Don't do a cancellation check, it's just a moot point, that's all
    if (isClosing) {
      return; // just get out, all of the SimpleWaiters will die of their own accord
    }
    int numInProgress = registerInterestsInProgress.decrementAndGet();
    if (logger.isDebugEnabled()) {
      logger.debug("registerInterestCompleted: new value = {}", numInProgress);
    }
    if (numInProgress == 0) {
      synchronized (riWaiters) {
        // TODO: get rid of double-check
        numInProgress = registerInterestsInProgress.get();
        if (numInProgress == 0) { // all clear
          if (logger.isDebugEnabled()) {
            logger.debug("registerInterestCompleted: Signalling end of register-interest");
          }
          for (MyCacheImpl.SimpleWaiter sw : riWaiters) {
            sw.doNotify();
          }
          riWaiters.clear();
        } // all clear
      } // synchronized
    }
  }

  @Override
  public void registerInterestStarted() {
    // Don't do a cancellation check, it's just a moot point, that's all
    int newVal = registerInterestsInProgress.incrementAndGet();
    if (logger.isDebugEnabled()) {
      logger.debug("registerInterestsStarted: new count = {}", newVal);
    }
  }

  /**
   * Blocks until no register interests are in progress.
   */
  @Override
  public void waitForRegisterInterestsInProgress() {
    // In *this* particular context, let the caller know that
    // its cache has been cancelled. doWait below would do that as
    // well, so this is just an early out.
    getCancelCriterion().checkCancelInProgress(null);

    int count = registerInterestsInProgress.get();
    if (count > 0) {
      MyCacheImpl.SimpleWaiter simpleWaiter = null;
      synchronized (riWaiters) {
        // TODO double-check
        count = registerInterestsInProgress.get();
        if (count > 0) {
          if (logger.isDebugEnabled()) {
            logger.debug("waitForRegisterInterestsInProgress: count ={}", count);
          }
          simpleWaiter = new MyCacheImpl.SimpleWaiter();
          riWaiters.add(simpleWaiter);
        }
      } // synchronized
      if (simpleWaiter != null) {
        simpleWaiter.doWait();
      }
    }
  }

  @Override
  @SuppressWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
  public void setQueryMonitorRequiredForResourceManager(boolean required) {
    queryMonitorRequiredForResourceManager = required;
  }

  @Override
  public boolean isQueryMonitorDisabledForLowMemory() {
    return queryMonitorDisabledForLowMem;
  }

  /**
   * Returns the QueryMonitor instance based on system property MAX_QUERY_EXECUTION_TIME.
   *
   * @since GemFire 6.0
   */
  @Override
  public QueryMonitor getQueryMonitor() {
    // Check to see if monitor is required if ResourceManager critical heap percentage is set
    // or whether we override it with the system variable;
    boolean monitorRequired =
            !queryMonitorDisabledForLowMem && queryMonitorRequiredForResourceManager;
    // Added for DUnit test purpose, which turns-on and off the this.testMaxQueryExecutionTime.
    if (!(MAX_QUERY_EXECUTION_TIME > 0 || monitorRequired)) {
      // if this.testMaxQueryExecutionTime is set, send the QueryMonitor.
      // Else send null, so that the QueryMonitor is turned-off.
      return null;
    }

    // Return the QueryMonitor service if MAX_QUERY_EXECUTION_TIME is set or it is required by the
    // ResourceManager and not overridden by system property.
    if (queryMonitor == null) {
      synchronized (queryMonitorLock) {
        if (queryMonitor == null) {
          int maxTime = MAX_QUERY_EXECUTION_TIME;

          if (monitorRequired && maxTime < 0) {
            // this means that the resource manager is being used and we need to monitor query
            // memory usage
            // If no max execution time has been set, then we will default to five hours
            maxTime = FIVE_HOURS;
          }

          queryMonitor =
                  new QueryMonitor((ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(
                          QUERY_MONITOR_THREAD_POOL_SIZE,
                          (runnable) -> new LoggingThread("QueryMonitor Thread", runnable)),
                          this,
                          maxTime);
          if (logger.isDebugEnabled()) {
            logger.debug("QueryMonitor thread started.");
          }
        }
      }
    }
    return queryMonitor;
  }

  /**
   * Simple class to allow waiters for register interest. Has at most one thread that ever calls
   * wait.
   *
   * @since GemFire 5.7
   */
  private class SimpleWaiter {
    private boolean notified;

    SimpleWaiter() {}

    void doWait() {
      synchronized (this) {
        while (!notified) {
          getCancelCriterion().checkCancelInProgress(null);
          boolean interrupted = Thread.interrupted();
          try {
            wait(1000);
          } catch (InterruptedException ignore) {
            interrupted = true;
          } finally {
            if (interrupted) {
              Thread.currentThread().interrupt();
            }
          }
        }
      }
    }

    void doNotify() {
      synchronized (this) {
        notified = true;
        notifyAll();
      }
    }
  }

  private void sendAddCacheServerProfileMessage() {
    Set<InternalDistributedMember> otherMembers = dm.getOtherDistributionManagerIds();
    AddCacheServerProfileMessage message = new AddCacheServerProfileMessage();
    message.operateOnLocalCache(this);
    if (!otherMembers.isEmpty()) {
      if (logger.isDebugEnabled()) {
        logger.debug("Sending add cache server profile message to other members.");
      }
      ReplyProcessor21 replyProcessor = new ReplyProcessor21(dm, otherMembers);
      message.setRecipients(otherMembers);
      message.processorId = replyProcessor.getProcessorId();
      dm.putOutgoing(message);

      // Wait for replies.
      try {
        replyProcessor.waitForReplies();
      } catch (InterruptedException ignore) {
        Thread.currentThread().interrupt();
      }
    }
  }


  private void sendRemoveCacheServerProfileMessage() {
    Set<InternalDistributedMember> otherMembers = dm.getOtherDistributionManagerIds();
    RemoveCacheServerProfileMessage message = new RemoveCacheServerProfileMessage();
    message.operateOnLocalCache(this);

    // Remove this while loop when we release GEODE 2.0
    // This block prevents sending a message to old members that do not know about
    // the RemoveCacheServerProfileMessage
    otherMembers.removeIf(member -> Version.GEODE_1_5_0.compareTo(member.getVersionObject()) > 0);

    if (!otherMembers.isEmpty()) {
      if (logger.isDebugEnabled()) {
        logger.debug("Sending remove cache server profile message to other members.");
      }
      ReplyProcessor21 replyProcessor = new ReplyProcessor21(dm, otherMembers);
      message.setRecipients(otherMembers);
      message.processorId = replyProcessor.getProcessorId();
      dm.putOutgoing(message);

      // Wait for replies.
      try {
        replyProcessor.waitForReplies();
      } catch (InterruptedException ignore) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public TXManagerImpl getTxManager() {
    return transactionManager;
  }

  /**
   * @since GemFire 6.5
   */
  @Override
  public <K, V> RegionFactory<K, V> createRegionFactory(RegionShortcut shortcut) {
    throwIfClient();
    return new RegionFactoryImpl<>(this, shortcut);
  }

  /**
   * @since GemFire 6.5
   */
  @Override
  public <K, V> RegionFactory<K, V> createRegionFactory() {
    throwIfClient();
    return new RegionFactoryImpl<>(this);
  }

  /**
   * @since GemFire 6.5
   */
  @Override
  public <K, V> RegionFactory<K, V> createRegionFactory(String regionAttributesId) {
    throwIfClient();
    return new RegionFactoryImpl<>(this, regionAttributesId);
  }

  /**
   * @since GemFire 6.5
   */
  @Override
  public <K, V> RegionFactory<K, V> createRegionFactory(RegionAttributes<K, V> regionAttributes) {
    throwIfClient();
    return new RegionFactoryImpl<>(this, regionAttributes);
  }

  /**
   * @since GemFire 6.5
   */
  @Override
  public <K, V> ClientRegionFactory<K, V> createClientRegionFactory(ClientRegionShortcut shortcut) {
    return new ClientRegionFactoryImpl<>(this, shortcut);
  }

  @Override
  public <K, V> ClientRegionFactory<K, V> createClientRegionFactory(String regionAttributesId) {
    return new ClientRegionFactoryImpl<>(this, regionAttributesId);
  }

  /**
   * @since GemFire 6.5
   */
  @Override
  public QueryService getQueryService(String poolName) {
    Pool pool = PoolManager.find(poolName);
    if (pool == null) {
      throw new IllegalStateException("Could not find a pool named " + poolName);
    } else {
      return pool.getQueryService();
    }
  }

  @Override
  public RegionService createAuthenticatedView(Properties userSecurityProperties) {
    Pool pool = getDefaultPool();
    if (pool == null) {
      throw new IllegalStateException("This cache does not have a default pool");
    }
    return createAuthenticatedCacheView(pool, userSecurityProperties);
  }

  @Override
  public RegionService createAuthenticatedView(Properties userSecurityProperties, String poolName) {
    Pool pool = PoolManager.find(poolName);
    if (pool == null) {
      throw new IllegalStateException("Pool " + poolName + " does not exist");
    }
    return createAuthenticatedCacheView(pool, userSecurityProperties);
  }

  private static RegionService createAuthenticatedCacheView(Pool pool, Properties properties) {
    if (pool.getMultiuserAuthentication()) {
      return ((PoolImpl) pool).createAuthenticatedCacheView(properties);
    } else {
      throw new IllegalStateException(
              "The pool " + pool.getName() + " did not have multiuser-authentication set to true");
    }
  }

  public static void initializeRegionShortcuts(Cache cache) {
    for (RegionShortcut shortcut : RegionShortcut.values()) {
      switch (shortcut) {
        case PARTITION: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PARTITION);
          PartitionAttributesFactory paf = new PartitionAttributesFactory();
          af.setPartitionAttributes(paf.create());
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case PARTITION_REDUNDANT: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PARTITION);
          PartitionAttributesFactory paf = new PartitionAttributesFactory();
          paf.setRedundantCopies(1);
          af.setPartitionAttributes(paf.create());
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case PARTITION_PERSISTENT: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PERSISTENT_PARTITION);
          PartitionAttributesFactory paf = new PartitionAttributesFactory();
          af.setPartitionAttributes(paf.create());
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case PARTITION_REDUNDANT_PERSISTENT: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PERSISTENT_PARTITION);
          PartitionAttributesFactory paf = new PartitionAttributesFactory();
          paf.setRedundantCopies(1);
          af.setPartitionAttributes(paf.create());
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case PARTITION_OVERFLOW: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PARTITION);
          PartitionAttributesFactory paf = new PartitionAttributesFactory();
          af.setPartitionAttributes(paf.create());
          af.setEvictionAttributes(
                  EvictionAttributes.createLRUHeapAttributes(null, EvictionAction.OVERFLOW_TO_DISK));
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case PARTITION_REDUNDANT_OVERFLOW: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PARTITION);
          PartitionAttributesFactory paf = new PartitionAttributesFactory();
          paf.setRedundantCopies(1);
          af.setPartitionAttributes(paf.create());
          af.setEvictionAttributes(
                  EvictionAttributes.createLRUHeapAttributes(null, EvictionAction.OVERFLOW_TO_DISK));
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case PARTITION_PERSISTENT_OVERFLOW: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PERSISTENT_PARTITION);
          PartitionAttributesFactory paf = new PartitionAttributesFactory();
          af.setPartitionAttributes(paf.create());
          af.setEvictionAttributes(
                  EvictionAttributes.createLRUHeapAttributes(null, EvictionAction.OVERFLOW_TO_DISK));
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case PARTITION_REDUNDANT_PERSISTENT_OVERFLOW: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PERSISTENT_PARTITION);
          PartitionAttributesFactory paf = new PartitionAttributesFactory();
          paf.setRedundantCopies(1);
          af.setPartitionAttributes(paf.create());
          af.setEvictionAttributes(
                  EvictionAttributes.createLRUHeapAttributes(null, EvictionAction.OVERFLOW_TO_DISK));
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case PARTITION_HEAP_LRU: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PARTITION);
          PartitionAttributesFactory paf = new PartitionAttributesFactory();
          af.setPartitionAttributes(paf.create());
          af.setEvictionAttributes(EvictionAttributes.createLRUHeapAttributes());
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case PARTITION_REDUNDANT_HEAP_LRU: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PARTITION);
          PartitionAttributesFactory paf = new PartitionAttributesFactory();
          paf.setRedundantCopies(1);
          af.setPartitionAttributes(paf.create());
          af.setEvictionAttributes(EvictionAttributes.createLRUHeapAttributes());
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case REPLICATE: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.REPLICATE);
          af.setScope(Scope.DISTRIBUTED_ACK);
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case REPLICATE_PERSISTENT: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PERSISTENT_REPLICATE);
          af.setScope(Scope.DISTRIBUTED_ACK);
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case REPLICATE_OVERFLOW: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.REPLICATE);
          af.setScope(Scope.DISTRIBUTED_ACK);
          af.setEvictionAttributes(
                  EvictionAttributes.createLRUHeapAttributes(null, EvictionAction.OVERFLOW_TO_DISK));
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case REPLICATE_PERSISTENT_OVERFLOW: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PERSISTENT_REPLICATE);
          af.setScope(Scope.DISTRIBUTED_ACK);
          af.setEvictionAttributes(
                  EvictionAttributes.createLRUHeapAttributes(null, EvictionAction.OVERFLOW_TO_DISK));
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case REPLICATE_HEAP_LRU: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.REPLICATE);
          af.setScope(Scope.DISTRIBUTED_ACK);
          af.setEvictionAttributes(EvictionAttributes.createLRUHeapAttributes());
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case LOCAL: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.NORMAL);
          af.setScope(Scope.LOCAL);
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case LOCAL_PERSISTENT: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PERSISTENT_REPLICATE);
          af.setScope(Scope.LOCAL);
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case LOCAL_HEAP_LRU: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.NORMAL);
          af.setScope(Scope.LOCAL);
          af.setEvictionAttributes(EvictionAttributes.createLRUHeapAttributes());
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case LOCAL_OVERFLOW: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.NORMAL);
          af.setScope(Scope.LOCAL);
          af.setEvictionAttributes(
                  EvictionAttributes.createLRUHeapAttributes(null, EvictionAction.OVERFLOW_TO_DISK));
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case LOCAL_PERSISTENT_OVERFLOW: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PERSISTENT_REPLICATE);
          af.setScope(Scope.LOCAL);
          af.setEvictionAttributes(
                  EvictionAttributes.createLRUHeapAttributes(null, EvictionAction.OVERFLOW_TO_DISK));
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case PARTITION_PROXY: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PARTITION);
          PartitionAttributesFactory paf = new PartitionAttributesFactory();
          paf.setLocalMaxMemory(0);
          af.setPartitionAttributes(paf.create());
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case PARTITION_PROXY_REDUNDANT: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PARTITION);
          PartitionAttributesFactory paf = new PartitionAttributesFactory();
          paf.setLocalMaxMemory(0);
          paf.setRedundantCopies(1);
          af.setPartitionAttributes(paf.create());
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case REPLICATE_PROXY: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.EMPTY);
          af.setScope(Scope.DISTRIBUTED_ACK);
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        default:
          throw new IllegalStateException("unhandled enum " + shortcut);
      }
    }
  }

  public static void initializeClientRegionShortcuts(Cache cache) {
    for (ClientRegionShortcut shortcut : ClientRegionShortcut.values()) {
      switch (shortcut) {
        case LOCAL: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.NORMAL);
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case LOCAL_PERSISTENT: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PERSISTENT_REPLICATE);
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case LOCAL_HEAP_LRU: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.NORMAL);
          af.setEvictionAttributes(EvictionAttributes.createLRUHeapAttributes());
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case LOCAL_OVERFLOW: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.NORMAL);
          af.setEvictionAttributes(
                  EvictionAttributes.createLRUHeapAttributes(null, EvictionAction.OVERFLOW_TO_DISK));
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case LOCAL_PERSISTENT_OVERFLOW: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.PERSISTENT_REPLICATE);
          af.setEvictionAttributes(
                  EvictionAttributes.createLRUHeapAttributes(null, EvictionAction.OVERFLOW_TO_DISK));
          cache.setRegionAttributes(shortcut.toString(), af.create());
          break;
        }
        case PROXY: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.EMPTY);
          UserSpecifiedRegionAttributes<?, ?> attributes =
                  (UserSpecifiedRegionAttributes) af.create();
          attributes.requiresPoolName = true;
          cache.setRegionAttributes(shortcut.toString(), attributes);
          break;
        }
        case CACHING_PROXY: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.NORMAL);
          UserSpecifiedRegionAttributes<?, ?> attributes =
                  (UserSpecifiedRegionAttributes) af.create();
          attributes.requiresPoolName = true;
          cache.setRegionAttributes(shortcut.toString(), attributes);
          break;
        }
        case CACHING_PROXY_HEAP_LRU: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.NORMAL);
          af.setEvictionAttributes(EvictionAttributes.createLRUHeapAttributes());
          UserSpecifiedRegionAttributes<?, ?> attributes =
                  (UserSpecifiedRegionAttributes) af.create();
          attributes.requiresPoolName = true;
          cache.setRegionAttributes(shortcut.toString(), attributes);
          break;
        }
        case CACHING_PROXY_OVERFLOW: {
          AttributesFactory<?, ?> af = new AttributesFactory();
          af.setDataPolicy(DataPolicy.NORMAL);
          af.setEvictionAttributes(
                  EvictionAttributes.createLRUHeapAttributes(null, EvictionAction.OVERFLOW_TO_DISK));
          UserSpecifiedRegionAttributes<?, ?> attributes =
                  (UserSpecifiedRegionAttributes) af.create();
          attributes.requiresPoolName = true;
          cache.setRegionAttributes(shortcut.toString(), attributes);
          break;
        }
        default:
          throw new IllegalStateException("unhandled enum " + shortcut);
      }
    }
  }

  @Override
  public void beginDestroy(String path, DistributedRegion region) {
    regionsInDestroy.putIfAbsent(path, region);
  }

  @Override
  public void endDestroy(String path, DistributedRegion region) {
    regionsInDestroy.remove(path, region);
  }

  @Override
  public DistributedRegion getRegionInDestroy(String path) {
    return regionsInDestroy.get(path);
  }

  @Override
  public TombstoneService getTombstoneService() {
    return tombstoneService;
  }

  @Override
  public TypeRegistry getPdxRegistry() {
    return pdxRegistry;
  }

  @Override
  public boolean getPdxReadSerialized() {
    return cacheConfig.pdxReadSerialized;
  }

  @Override
  public PdxSerializer getPdxSerializer() {
    return cacheConfig.pdxSerializer;
  }

  @Override
  public String getPdxDiskStore() {
    return cacheConfig.pdxDiskStore;
  }

  @Override
  public boolean getPdxPersistent() {
    return cacheConfig.pdxPersistent;
  }

  @Override
  public boolean getPdxIgnoreUnreadFields() {
    return cacheConfig.pdxIgnoreUnreadFields;
  }

  /**
   * Returns true if any of the GemFire services prefers PdxInstance. And application has not
   * requested getObject() on the PdxInstance.
   */
  @Override
  public boolean getPdxReadSerializedByAnyGemFireServices() {
    TypeRegistry pdxRegistry = getPdxRegistry();
    boolean pdxReadSerializedOverriden = false;
    if (pdxRegistry != null) {
      pdxReadSerializedOverriden = pdxRegistry.getPdxReadSerializedOverride();
    }

    return (getPdxReadSerialized() || pdxReadSerializedOverriden)
            && PdxInstanceImpl.getPdxReadSerialized();
  }

  @Override
  public CacheConfig getCacheConfig() {
    return cacheConfig;
  }

  @Override
  public DistributionManager getDistributionManager() {
    return dm;
  }

  @Override
  public GatewaySenderFactory createGatewaySenderFactory() {
    return WANServiceProvider.createGatewaySenderFactory(this);
  }

  @Override
  public GatewayReceiverFactory createGatewayReceiverFactory() {
    return WANServiceProvider.createGatewayReceiverFactory(this);
  }

  @Override
  public AsyncEventQueueFactory createAsyncEventQueueFactory() {
    return new AsyncEventQueueFactoryImpl(this);
  }

  @Override
  public DistributionAdvisor getDistributionAdvisor() {
    return getResourceAdvisor();
  }

  @Override
  public ResourceAdvisor getResourceAdvisor() {
    return resourceAdvisor;
  }

  @Override
  public DistributionAdvisor.Profile getProfile() {
    return resourceAdvisor.createProfile();
  }

  @Override
  public DistributionAdvisee getParentAdvisee() {
    return null;
  }

  @Override
  public InternalDistributedSystem getSystem() {
    return system;
  }

  @Override
  public String getFullPath() {
    return "ResourceManager";
  }

  @Override
  public void fillInProfile(DistributionAdvisor.Profile profile) {
    resourceManager.fillInProfile(profile);
  }

  @Override
  public int getSerialNumber() {
    return serialNumber;
  }

  @Override
  public TXEntryStateFactory getTXEntryStateFactory() {
    return txEntryStateFactory;
  }

  // test hook
  public void setPdxSerializer(PdxSerializer serializer) {
    cacheConfig.setPdxSerializer(serializer);
    basicSetPdxSerializer(serializer);
  }

  private void basicSetPdxSerializer(PdxSerializer serializer) {
    TypeRegistry.setPdxSerializer(serializer);
    if (serializer instanceof ReflectionBasedAutoSerializer) {
      AutoSerializableManager autoSerializableManager =
              (AutoSerializableManager) ((ReflectionBasedAutoSerializer) serializer).getManager();
      if (autoSerializableManager != null) {
        autoSerializableManager.setRegionService(this);
      }
    }
  }

  @Override
  public void setReadSerializedForCurrentThread(boolean value) {
    PdxInstanceImpl.setPdxReadSerialized(value);
    setPdxReadSerializedOverride(value);
  }

  // test hook
  @Override
  public void setReadSerializedForTest(boolean value) {
    cacheConfig.setPdxReadSerialized(value);
  }

  @Override
  public void setDeclarativeCacheConfig(CacheConfig cacheConfig) {
    this.cacheConfig.setDeclarativeConfig(cacheConfig);
    basicSetPdxSerializer(this.cacheConfig.getPdxSerializer());
  }

  /**
   * Add to the map of declarable properties. Any properties that exactly match existing properties
   * for a class in the list will be discarded (no duplicate Properties allowed).
   *
   * @param mapOfNewDeclarableProps Map of the declarable properties to add
   */
  @Override
  public void addDeclarableProperties(final Map<Declarable, Properties> mapOfNewDeclarableProps) {
    synchronized (declarablePropertiesMap) {
      for (Map.Entry<Declarable, Properties> newEntry : mapOfNewDeclarableProps.entrySet()) {
        // Find and remove a Declarable from the map if an "equal" version is already stored
        Class<? extends Declarable> clazz = newEntry.getKey().getClass();

        Declarable matchingDeclarable = null;
        for (Map.Entry<Declarable, Properties> oldEntry : declarablePropertiesMap.entrySet()) {

          BiPredicate<Declarable, Declarable> isKeyIdentifiableAndSameIdPredicate =
                  (Declarable oldKey, Declarable newKey) -> newKey instanceof Identifiable
                          && ((Identifiable) oldKey).getId().equals(((Identifiable) newKey).getId());

          Supplier<Boolean> isKeyClassSame =
                  () -> clazz.getName().equals(oldEntry.getKey().getClass().getName());
          Supplier<Boolean> isValueEqual = () -> newEntry.getValue().equals(oldEntry.getValue());
          Supplier<Boolean> isKeyIdentifiableAndSameId =
                  () -> isKeyIdentifiableAndSameIdPredicate.test(oldEntry.getKey(), newEntry.getKey());

          if (isKeyClassSame.get() && (isValueEqual.get() || isKeyIdentifiableAndSameId.get())) {
            matchingDeclarable = oldEntry.getKey();
            break;
          }
        }
        if (matchingDeclarable != null) {
          declarablePropertiesMap.remove(matchingDeclarable);
        }

        // Now add the new/replacement properties to the map
        declarablePropertiesMap.put(newEntry.getKey(), newEntry.getValue());
      }
    }
  }

  private Declarable initializer;

  private Properties initializerProps;

  @Override
  public Declarable getInitializer() {
    return initializer;
  }

  @Override
  public Properties getInitializerProps() {
    return initializerProps;
  }

  @Override
  public void setInitializer(Declarable initializer, Properties initializerProps) {
    this.initializer = initializer;
    this.initializerProps = initializerProps;
  }

  @Override
  public PdxInstanceFactory createPdxInstanceFactory(String className) {
    return PdxInstanceFactoryImpl.newCreator(className, true, this);
  }

  @Override
  public PdxInstanceFactory createPdxInstanceFactory(String className, boolean expectDomainClass) {
    return PdxInstanceFactoryImpl.newCreator(className, expectDomainClass, this);
  }

  @Override
  public PdxInstance createPdxEnum(String className, String enumName, int enumOrdinal) {
    return PdxInstanceFactoryImpl.createPdxEnum(className, enumName, enumOrdinal, this);
  }

  @Override
  public JmxManagerAdvisor getJmxManagerAdvisor() {
    return jmxAdvisor;
  }

  @Override
  public CacheSnapshotService getSnapshotService() {
    return new CacheSnapshotServiceImpl(this);
  }

  private void startColocatedJmxManagerLocator() {
    InternalLocator loc = InternalLocator.getLocator();
    if (loc != null) {
      loc.startJmxManagerLocationService(this);
    }
  }

  @Override
  public MemoryAllocator getOffHeapStore() {
    return getSystem().getOffHeapStore();
  }

  @Override
  public DiskStoreMonitor getDiskStoreMonitor() {
    return diskMonitor;
  }

  /**
   * @see Extensible#getExtensionPoint()
   * @since GemFire 8.1
   */
  @Override
  public ExtensionPoint<Cache> getExtensionPoint() {
    return extensionPoint;
  }

  @Override
  public CqService getCqService() {
    return cqService;
  }

  private void addRegionEntrySynchronizationListener(RegionEntrySynchronizationListener listener) {
    synchronizationListeners.add(listener);
  }

  @Override
  public void invokeRegionEntrySynchronizationListenersAfterSynchronization(
          InternalDistributedMember sender, InternalRegion region,
          List<InitialImageOperation.Entry> entriesToSynchronize) {
    for (RegionEntrySynchronizationListener listener : synchronizationListeners) {
      try {
        listener.afterSynchronization(sender, region, entriesToSynchronize);
      } catch (Throwable t) {
        logger.warn(String.format(
                "Caught the following exception attempting to synchronize events from member=%s; regionPath=%s; entriesToSynchronize=%s:",
                sender, region.getFullPath(), entriesToSynchronize), t);
      }
    }
  }

  @Override
  public Object convertPdxInstanceIfNeeded(Object obj, boolean preferCD) {
    Object result = obj;
    if (obj instanceof InternalPdxInstance) {
      InternalPdxInstance pdxInstance = (InternalPdxInstance) obj;
      if (preferCD) {
        try {
          result = new PreferBytesCachedDeserializable(pdxInstance.toBytes());
        } catch (IOException ignore) {
          // Could not convert pdx to bytes here; it will be tried again later
          // and an exception will be thrown there.
        }
      } else if (!getPdxReadSerialized()) {
        result = pdxInstance.getObject();
      }
    }
    return result;
  }

  @Override
  public Boolean getPdxReadSerializedOverride() {
    TypeRegistry pdxRegistry = getPdxRegistry();
    if (pdxRegistry != null) {
      return pdxRegistry.getPdxReadSerializedOverride();
    }
    return false;
  }

  @Override
  public void setPdxReadSerializedOverride(boolean pdxReadSerialized) {
    TypeRegistry pdxRegistry = getPdxRegistry();
    if (pdxRegistry != null) {
      pdxRegistry.setPdxReadSerializedOverride(pdxReadSerialized);
    }
  }

  @Override
  public void registerPdxMetaData(Object instance) {
    try {
      byte[] blob = BlobHelper.serializeToBlob(instance);
      if (blob.length == 0 || blob[0] != DSCODE.PDX.toByte()) {
        throw new SerializationException("The instance is not PDX serializable");
      }
    } catch (IOException e) {
      throw new SerializationException("Serialization failed", e);
    }
  }

  private void throwIfClient() {
    if (isClient()) {
      throw new UnsupportedOperationException("operation is not supported on a client cache");
    }
  }

  private final InternalCacheForClientAccess cacheForClients =
          new InternalCacheForClientAccess(this);

  @Override
  public InternalCacheForClientAccess getCacheForProcessingClientRequests() {
    return cacheForClients;
  }

  private ThreadsMonitoring getThreadMonitorObj() {
    if (dm != null) {
      return dm.getThreadMonitoring();
    } else {
      return null;
    }
  }

}