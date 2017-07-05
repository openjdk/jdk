/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestDynamicNumberOfGCThreads
 * @bug 8017462
 * @summary Ensure that UseDynamicNumberOfGCThreads runs
 * @requires vm.gc=="null"
 * @key gc
 * @library /testlibrary
 */

import com.oracle.java.testlibrary.ProcessTools;
import com.oracle.java.testlibrary.OutputAnalyzer;

public class TestDynamicNumberOfGCThreads {
  public static void main(String[] args) throws Exception {

    testDynamicNumberOfGCThreads("UseConcMarkSweepGC");

    testDynamicNumberOfGCThreads("UseG1GC");

    testDynamicNumberOfGCThreads("UseParallelGC");
  }

  private static void verifyDynamicNumberOfGCThreads(OutputAnalyzer output) {
    output.shouldContain("new_active_workers");
    output.shouldHaveExitValue(0);
  }

  private static void testDynamicNumberOfGCThreads(String gcFlag) throws Exception {
    // UseDynamicNumberOfGCThreads and TraceDynamicGCThreads enabled
    ProcessBuilder pb_enabled =
      ProcessTools.createJavaProcessBuilder("-XX:+" + gcFlag, "-Xmx10M", "-XX:+PrintGCDetails",  "-XX:+UseDynamicNumberOfGCThreads", "-XX:+TraceDynamicGCThreads", GCTest.class.getName());
    verifyDynamicNumberOfGCThreads(new OutputAnalyzer(pb_enabled.start()));
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
}
