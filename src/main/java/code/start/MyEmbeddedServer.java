package code.start;

import org.apache.geode.distributed.ServerLauncher;

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
            .set("jmx-manager", "true")
            .set("jmx-manager-start", "true")
            .build();
    serverLauncher.start();

    System.out.println("Cache server successfully started");
  }
}
