/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @summary Stress test that reaches the process limit for thread count, or time limit.
 * @requires os.family != "aix"
 * @key stress
 * @library /test/lib
 * @run main/othervm -Xmx1g ThreadCountLimit
 */

/**
 * @test
 * @summary Stress test that reaches the process limit for thread count, or time limit.
 * @requires os.family == "aix"
 * @key stress
 * @library /test/lib
 * @run main/othervm -Xmx1g -XX:MaxExpectedDataSegmentSize=16g ThreadCountLimit
 */

import java.util.concurrent.CountDownLatch;
import java.util.ArrayList;

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ThreadCountLimit {

  static final int TIME_LIMIT_MS = 5000; // Create as many threads as possible in 5 sec

  static class Worker extends Thread {
    private final CountDownLatch startSignal;

    Worker(CountDownLatch startSignal) {
      this.startSignal = startSignal;
    }

    @Override
    public void run() {
      try {
        startSignal.await();
      } catch (InterruptedException e) {
        throw new Error("Unexpected", e);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      // Called from the driver process so exec a new JVM on Linux.
      if (Platform.isLinux()) {
        // On Linux this test sometimes hits the limit for the maximum number of memory mappings,
        // which leads to various other failure modes. Run this test with a limit on how many
        // threads the process is allowed to create, so we hit that limit first. What we want is
        // for another "limit" processes to be available, but ulimit doesn't work that way and
        // if there are already many running processes we could fail to even start the JVM properly.
        // So we loop increasing the limit until we get a successful run. This is not foolproof.
        int pLimit = 4096;
        final String ULIMIT_CMD = "ulimit -u ";
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(ThreadCountLimit.class.getName());
        String javaCmd = ProcessTools.getCommandLine(pb);
        for (int i = 1; i <= 10; i++) {
            // Relaunch the test with args.length > 0, and the ulimit set
            String cmd = ULIMIT_CMD + Integer.toString(pLimit * i) + " && " + javaCmd + " dummy";
            System.out.println("Trying: bash -c " + cmd);
            OutputAnalyzer oa = ProcessTools.executeCommand("bash", "-c", cmd);
            int exitValue = oa.getExitValue();
            switch (exitValue) {
              case 0: System.out.println("Success!"); return;
              case 1: System.out.println("Retry ..."); continue;
              default: oa.shouldHaveExitValue(0); // generate error report
            }
        }
        throw new Error("Failed to perform a successful run!");
      } else {
        // Not Linux so run directly.
        test();
      }
    } else {
      // This is the exec'd process so run directly.
      test();
    }
  }

  static void test() {
    CountDownLatch startSignal = new CountDownLatch(1);
    ArrayList<Worker> workers = new ArrayList<Worker>();

    boolean reachedNativeOOM = false;

    // This is dangerous loop: it depletes system resources,
    // so doing additional things there that may end up allocating
    // Java/native memory risks failing the VM prematurely.
    // Avoid doing unnecessary calls, printouts, etc.

    int count = 0;
    long start = System.currentTimeMillis();
    try {
      while (true) {
        Worker w = new Worker(startSignal);
        w.start();
        workers.add(w);
        count++;

        long end = System.currentTimeMillis();
        if ((end - start) > TIME_LIMIT_MS) {
          // Windows always gets here, but we also get here if
          // ulimit is set high enough.
          break;
        }
      }
    } catch (OutOfMemoryError e) {
      if (e.getMessage().contains("unable to create native thread")) {
        // Linux, macOS path if we hit ulimit
        reachedNativeOOM = true;
      } else {
        throw e;
      }
    }

    startSignal.countDown();

    try {
      for (Worker w : workers) {
        w.join();
      }
    } catch (InterruptedException e) {
      throw new Error("Unexpected", e);
    }

    // Now that all threads have joined, we are away from dangerous
    // VM state and have enough memory to perform any other things.
    if (reachedNativeOOM) {
      System.out.println("INFO: reached this process thread count limit with " +
                         count + " threads created");
    } else {
      System.out.println("INFO: reached the time limit " + TIME_LIMIT_MS +
                         " ms, with " + count + " threads created");
    }
  }
}
