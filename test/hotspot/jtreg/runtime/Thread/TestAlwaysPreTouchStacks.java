/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CyclicBarrier;

import static jdk.test.lib.Platform.isLinux;
import static jdk.test.lib.Platform.isWindows;

/*
 * @test id=preTouchTest
 * @summary Test AlwaysPreTouchThreadStacks
 * @requires os.family != "aix"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestAlwaysPreTouchStacks preTouch
 */

public class TestAlwaysPreTouchStacks {

    // We will create a bunch of large-stacked threads to make a significant imprint on combined thread stack size
    final static int MB = 1024 * 1024;
    static int memoryCeilingMB = 128;
    static int threadStackSizeMB = 8;
    static int numThreads = memoryCeilingMB / threadStackSizeMB;
    static long min_stack_usage_with_pretouch = 1 * MB;
    static long max_stack_usage_with_pretouch = (long)(0.75 * threadStackSizeMB * MB);

    static CyclicBarrier gate = new CyclicBarrier(numThreads + 1);

    static private final Thread createTestThread(int num) {
        Thread t = new Thread(null,
                () -> {
                    System.out.println("Alive: " + num);
                    try {
                        // report aliveness, then sleep until VM death
                        gate.await();
                        for (;;) {
                            Thread.sleep(1000);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                "TestThread-" + num, threadStackSizeMB * MB);
        // Let test threads run as daemons to ensure that they are still running and
        // that their stacks are still allocated when the JVM shuts down and the final
        // NMT report is printed.
        t.setDaemon(true);
        return t;
    }

    private static long runPreTouchTest(boolean preTouch) throws Exception {
      long reserved = 0L, committed = 0L;
      ArrayList<String> vmArgs = new ArrayList<>();
      Collections.addAll(vmArgs,
              "-XX:+UnlockDiagnosticVMOptions",
              "-Xmx100M",
              "-XX:NativeMemoryTracking=summary", "-XX:+PrintNMTStatistics");
      if (preTouch){
          vmArgs.add("-XX:+AlwaysPreTouchStacks");
      }
      if (System.getProperty("os.name").contains("Linux")) {
          vmArgs.add("-XX:-UseMadvPopulateWrite");
      }
      Collections.addAll(vmArgs, "TestAlwaysPreTouchStacks", "test");
      ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(vmArgs);
      OutputAnalyzer output = new OutputAnalyzer(pb.start());

      output.shouldHaveExitValue(0);

      for (int i = 0; i < numThreads; i++) {
          output.shouldContain("Alive: " + i);
      }

      // If using -XX:+AlwaysPreTouchStacks, we want to see, in the final NMT printout,
      // a committed thread stack size very close to reserved stack size. Like this:
      // -                    Thread (reserved=10332400KB, committed=10284360KB)
      //                      (thread #10021)
      //                      (stack: reserved=10301560KB, committed=10253520KB)   <<<<
      //
      // ... without -XX:+AlwaysPreTouchStacks, the committed/reserved ratio for thread stacks should be
      // a lot lower, e.g.:
      // -                    Thread (reserved=10332400KB, committed=331828KB)
      //                      (thread #10021)
      //                      (stack: reserved=10301560KB, committed=300988KB)  <<<

      output.shouldMatch("- *Thread.*reserved.*committed");
      output.reportDiagnosticSummary();
      Pattern pat = Pattern.compile(".*stack: reserved=(\\d+), committed=(\\d+).*");
      boolean foundLine = false;
      for (String line : output.asLines()) {
        Matcher m = pat.matcher(line);
        if (m.matches()) {
          reserved = Long.parseLong(m.group(1));
          committed = Long.parseLong(m.group(2));
          System.out.println(">>>>> " + line + ": " + reserved + " - " + committed);
          // Added sanity tests: we expect our test threads to be still alive when NMT prints its final
          // report, so their stacks should dominate the NMT-reported total stack size.
          long max_reserved = memoryCeilingMB * 3 * MB;
          long min_reserved = memoryCeilingMB * MB;
          if (reserved >= max_reserved || reserved < min_reserved) {
              throw new RuntimeException("Total reserved stack sizes outside of our expectations (" + reserved +
                                          ", expected " + min_reserved + ".." + max_reserved + ")");
          }
          foundLine = true;
          break;
        }
      }
      if (!foundLine) {
          throw new RuntimeException("Did not find expected NMT output");
      }
      return committed;
    }

    public static void main(String[] args) throws Exception {

        if (args.length == 1 && args[0].equals("test")) {

            ArrayList<Thread> threads = new ArrayList<>();

            // Add a bunch of large-stacked threads to make a significant imprint on combined thread stack size
            for (int i = 0; i < numThreads; i++) {
                threads.add(createTestThread(i));
            }

            // Start test threads.
            threads.forEach(Thread::start);

            gate.await();

            // Stop VM. VM will run PrintNMTStatistics before exiting, and the still-running daemon threads
            // should show up with fully - or almost fully - committed thread stacks.

        } else {
          long pretouch_committed = runPreTouchTest(true);
          long no_pretouch_committed = runPreTouchTest(false);
          if (pretouch_committed == 0 || no_pretouch_committed == 0) {
            throw new RuntimeException("Could not run with PreTouch flag.");
          }
          long expected_delta = numThreads * (max_stack_usage_with_pretouch - min_stack_usage_with_pretouch);
          long actual_delta = pretouch_committed - no_pretouch_committed;
          if (((double)pretouch_committed) / ((double)no_pretouch_committed) < 1.20) {
            if (pretouch_committed <= (no_pretouch_committed + expected_delta)) {
              throw new RuntimeException("Expected a higher amount of committed with pretouch stacks" +
                                        " PreTouch amount: " + pretouch_committed +
                                        " NoPreTouch amount: " + no_pretouch_committed +
                                        " Expected delta: " + expected_delta);
            }
            if (actual_delta < expected_delta) {
              throw new RuntimeException("Expected a higher delta between stack committed of with and without pretouch." +
                                        " Expected: " + expected_delta + " Actual: " + actual_delta);
            }
          }
      }
    }

}
