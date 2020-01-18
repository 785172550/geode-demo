package com.ken.test;

import org.apache.geode.management.internal.cli.Launcher;

import java.util.Arrays;
import java.util.List;

public class Tester {
  String LAUNCHER = "org.apache.geode.management.internal.cli.Launcher";

  public static void main(String[] args) {

    List<String> cmd = Arrays.asList("-e connect", "-e list members");
    Launcher.main((String[]) cmd.toArray());

//    Stream<String> paths = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator));
//    paths.forEach(System.out::println);
  }
}
