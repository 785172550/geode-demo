# Geode Demo

### how to run
```
gradle task:

getTar(down load geode tgz)
installGeode(unzip)
cleanWorkingDir
start
stop

```

```
## simpleIndex uses default Lucene StandardAnalyzer
create lucene index --name=simpleIndex --region=EmployeeData --field=firstName,lastName

## analyzerIndex uses both the default StandardAnalyzer and the KeywordAnalyzer
create lucene index --name=analyzerIndex --region=EmployeeData --field=lastName,email --analyzer=DEFAULT,org.apache.lucene.analysis.core.KeywordAnalyzer

## nestedObjectIndex will index on nested objects or collection objects
create lucene index --name=nestedObjectIndex --region=EmployeeData --field=contacts.phoneNumbers --serializer=org.apache.geode.cache.lucene.FlatFormatSerializer

create region --name=example-region --type=PARTITION --enable-statistics=true



create region --name=EmployeeData --type=REPLICATE --skip-if-exists=true
create index --name=topLevelIndex --expression=name --region=/EmployeeData
create index --name=nestedIndex --expression=flight.airlineCode --region=/EmployeeData

```







---

源码梳理

```
pulse and rest-api based on jetty server

AgentUtil.findWarLocation():
distributed.internal.InternalLocator.startClusterManagementService -> geode-web-management
management.internal.ManagementAgent.loadWebApplications -> geode-pulse, geode-web
management.internal.RestAgent.startHttpService - > geode-web-api


org.apache.geode.internal.cache.GemFireCacheImpl
httpService = new HttpService(systemConfig.getHttpServiceBindAddress(), 
systemConfig.getHttpServicePort())

org.apache.geode.management.internal.web.controllers.ShellCommandsController


启动流程
GemFireCacheImpl
InternalCacheBuilder  
     InternalCache cache = existingCache(internalDistributedSystem::getCache, singletonCacheSupplier);
CacheFactory
Cache cache = provider.createCache -> new CacheFactory(gemfireProperties)
new ServerLauncher.Builder().start()


Launcher -> Gfsh.executeCommand

```