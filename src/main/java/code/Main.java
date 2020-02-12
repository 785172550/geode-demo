package code;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
  public static void main(String[] args) {
    log.info("App class path:\n{}", System.getProperty("java.class.path").replace(";", "\n"));
    log.info("main test");
  }
}
