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
 * @test TestDeferredRSUpdate
 * @bug 8040977 8052170
 * @summary Ensure that running with -XX:-G1DeferredRSUpdate does not crash the VM
 * @key gc
 * @library /testlibrary
 */

import com.oracle.java.testlibrary.ProcessTools;
import com.oracle.java.testlibrary.OutputAnalyzer;

public class TestDeferredRSUpdate {
  public static void main(String[] args) throws Exception {
    GCTest.main(args);

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                                              "-Xmx10M",
                                                              "-XX:+PrintGCDetails",
                                                              // G1DeferredRSUpdate is a develop option, but we cannot limit execution of this test to only debug VMs.
                                                              "-XX:+IgnoreUnrecognizedVMOptions",
                                                              "-XX:-G1DeferredRSUpdate",
                                                              GCTest.class.getName());

    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    output.shouldHaveExitValue(0);
  }

  static class GCTest {
    private static Object[] garbage = new Object[32];

    public static void main(String [] args) {
      System.out.println("Creating garbage");
      // Create 128MB of garbage. This should result in at least one minor GC, with
      // some objects copied to old gen. As references from old to young are installed,
      // the crash due to the use before initialize occurs.
      Object prev = null;
      Object prevPrev = null;
      for (int i = 0; i < 1024; i++) {
        Object[] next = new Object[32 * 1024];
        next[0] = prev;
        next[1] = prevPrev;

        Object[] cur = (Object[]) garbage[i % garbage.length];
        if (cur != null) {
          cur[0] = null;
          cur[1] = null;
        }
        garbage[i % garbage.length] = next;

        prevPrev = prev;
        prev = next;
      }
      System.out.println("Done");
    }
  }
}
