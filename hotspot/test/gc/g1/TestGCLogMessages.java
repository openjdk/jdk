/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test TestPrintGCDetails
 * @bug 8035406 8027295 8035398 8019342
 * @summary Ensure that the PrintGCDetails output for a minor GC with G1
 * includes the expected necessary messages.
 * @key gc
 * @library /testlibrary
 */

import com.oracle.java.testlibrary.ProcessTools;
import com.oracle.java.testlibrary.OutputAnalyzer;

public class TestGCLogMessages {
  public static void main(String[] args) throws Exception {
    testNormalLogs();
    testWithToSpaceExhaustionLogs();
  }

  private static void testNormalLogs() throws Exception {

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                                              "-Xmx10M",
                                                              GCTest.class.getName());

    OutputAnalyzer output = new OutputAnalyzer(pb.start());

    output.shouldNotContain("[Redirty Cards");
    output.shouldNotContain("[Parallel Redirty");
    output.shouldNotContain("[Redirtied Cards");
    output.shouldNotContain("[Code Root Purge");
    output.shouldNotContain("[String Dedup Fixup");
    output.shouldNotContain("[Young Free CSet");
    output.shouldNotContain("[Non-Young Free CSet");
    output.shouldHaveExitValue(0);

    pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                               "-XX:+UseStringDeduplication",
                                               "-Xmx10M",
                                               "-XX:+PrintGCDetails",
                                               GCTest.class.getName());

    output = new OutputAnalyzer(pb.start());

    output.shouldContain("[Redirty Cards");
    output.shouldNotContain("[Parallel Redirty");
    output.shouldNotContain("[Redirtied Cards");
    output.shouldContain("[Code Root Purge");
    output.shouldContain("[String Dedup Fixup");
    output.shouldNotContain("[Young Free CSet");
    output.shouldNotContain("[Non-Young Free CSet");
    output.shouldHaveExitValue(0);

    pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                               "-XX:+UseStringDeduplication",
                                               "-Xmx10M",
                                               "-XX:+PrintGCDetails",
                                               "-XX:+UnlockExperimentalVMOptions",
                                               "-XX:G1LogLevel=finest",
                                               GCTest.class.getName());

    output = new OutputAnalyzer(pb.start());

    output.shouldContain("[Redirty Cards");
    output.shouldContain("[Parallel Redirty");
    output.shouldContain("[Redirtied Cards");
    output.shouldContain("[Code Root Purge");
    output.shouldContain("[String Dedup Fixup");
    output.shouldContain("[Young Free CSet");
    output.shouldContain("[Non-Young Free CSet");

    // also check evacuation failure messages once
    output.shouldNotContain("[Evacuation Failure");
    output.shouldNotContain("[Recalculate Used");
    output.shouldNotContain("[Remove Self Forwards");
    output.shouldNotContain("[Restore RemSet");
    output.shouldHaveExitValue(0);
  }

  private static void testWithToSpaceExhaustionLogs() throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                               "-Xmx10M",
                                               "-Xmn5M",
                                               "-XX:+PrintGCDetails",
                                               GCTestWithToSpaceExhaustion.class.getName());

    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldContain("[Evacuation Failure");
    output.shouldNotContain("[Recalculate Used");
    output.shouldNotContain("[Remove Self Forwards");
    output.shouldNotContain("[Restore RemSet");
    output.shouldHaveExitValue(0);

    pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                               "-Xmx10M",
                                               "-Xmn5M",
                                               "-XX:+PrintGCDetails",
                                               "-XX:+UnlockExperimentalVMOptions",
                                               "-XX:G1LogLevel=finest",
                                               GCTestWithToSpaceExhaustion.class.getName());

    output = new OutputAnalyzer(pb.start());
    output.shouldContain("[Evacuation Failure");
    output.shouldContain("[Recalculate Used");
    output.shouldContain("[Remove Self Forwards");
    output.shouldContain("[Restore RemSet");
    output.shouldHaveExitValue(0);
  }

  static class GCTest {
    private static byte[] garbage;
    public static void main(String [] args) {
      System.out.println("Creating garbage");
      // create 128MB of garbage. This should result in at least one GC
      for (int i = 0; i < 1024; i++) {
        garbage = new byte[128 * 1024];
      }
      System.out.println("Done");
    }
  }

  static class GCTestWithToSpaceExhaustion {
    private static byte[] garbage;
    private static byte[] largeObject;
    public static void main(String [] args) {
      largeObject = new byte[5*1024*1024];
      System.out.println("Creating garbage");
      // create 128MB of garbage. This should result in at least one GC,
      // some of them with to-space exhaustion.
      for (int i = 0; i < 1024; i++) {
        garbage = new byte[128 * 1024];
      }
      System.out.println("Done");
    }
  }
}
