package code.start;

import org.apache.geode.distributed.LocatorLauncher;

import java.util.concurrent.TimeUnit;

/*
System Properties:
    Locator.forceLocatorDMType = true
    awt.toolkit = sun.awt.windows.WToolkit
    file.encoding = GBK
    file.encoding.pkg = sun.io
    file.separator = \
    gemfire.enable-cluster-configuration = true
    gemfire.launcher.registerSignalHandlers = true
    gemfire.load-cluster-configuration-from-dir = false
    gemfire.log-level = config


 */
public class MyEmbeddedLocator {
  public static void main(String[] args) {
    LocatorLauncher locatorLauncher = new LocatorLauncher.Builder()
            .setMemberName("locator1")
            .setPort(13489)
            .build();

    // start the Locator in-process
    locatorLauncher.start();

    // wait for Locator to start and be ready to accept member (client) connections
    locatorLauncher.waitOnStatusResponse(30, 5, TimeUnit.SECONDS);

    System.out.println("Locator successfully started");
  }
}
