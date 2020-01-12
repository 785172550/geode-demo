package com.ken.test;

import org.apache.geode.management.internal.cli.Launcher;

import java.util.Arrays;
import java.util.List;

public class Tester {
  String LAUNCHER = "org.apache.geode.management.internal.cli.Launcher";

  public static void main(String[] args) {

//    String[] ag = Arrays.<String>asList("connect").toArray();
    List<String> cmd = Arrays.asList("connect","list members");
    Launcher.main((String[]) cmd.toArray());
  }
}
