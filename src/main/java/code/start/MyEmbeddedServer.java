package code.start;

import org.apache.geode.distributed.ConfigurationProperties;
import org.apache.geode.distributed.ServerLauncher;
import org.apache.geode.management.internal.cli.i18n.CliStrings;

/*
System Properties:
    awt.toolkit = sun.awt.windows.WToolkit
    file.encoding = GBK
    file.encoding.pkg = sun.io
    file.separator = \
    gemfire.QueryService.allowUntrustedMethodInvocation = true
    gemfire.bind-address = 127.0.0.1
    gemfire.cache-xml-file = C:\Users\kenneth\Documents\git\geode-demo\config\cacheSchema.xml
    gemfire.enable-time-statistics = true
    gemfire.http-service-port = 8888
    gemfire.launcher.registerSignalHandlers = true
    gemfire.locators = 127.0.0.1[10334]
    gemfire.log-level = config
    gemfire.start-dev-rest-api = true
    gemfire.statistic-archive-file = lucene1.gfs
    gemfire.use-cluster-configuration = true

 */
public class MyEmbeddedServer {
  public static void main(String[] args) {
    ServerLauncher serverLauncher = new ServerLauncher.Builder()
            .setMemberName("server1")
            .setServerPort(40405)
            .set(ConfigurationProperties.LOCATORS, "localhost[10334]")
            .set(ConfigurationProperties.JMX_MANAGER, "true")
            .set(ConfigurationProperties.JMX_MANAGER_START, "true")
            .set(ConfigurationProperties.CACHE_XML_FILE, "config/cacheSheam.xml")
            .set(ConfigurationProperties.HTTP_SERVICE_PORT, "8888")
            .set(CliStrings.START_SERVER__REST_API, "true")
            .build();
    serverLauncher.start();

    System.out.println("Cache server successfully started");
  }
}
