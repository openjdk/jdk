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
 * @test TestG1TraceEagerReclaimHumongousObjects
 * @bug 8058801 8048179
 * @summary Ensure that the output for a G1TraceEagerReclaimHumongousObjects
 * includes the expected necessary messages.
 * @key gc
 * @library /testlibrary
 */

import com.oracle.java.testlibrary.ProcessTools;
import com.oracle.java.testlibrary.OutputAnalyzer;
import java.util.LinkedList;

public class TestG1TraceEagerReclaimHumongousObjects {
  public static void main(String[] args) throws Exception {
    testGCLogs();
    testHumongousObjectGCLogs();
  }

  private static void testGCLogs() throws Exception {

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                               "-Xms128M",
                                               "-Xmx128M",
                                               "-Xmn16M",
                                               "-XX:G1HeapRegionSize=1M",
                                               "-XX:+PrintGC",
                                               "-XX:+UnlockExperimentalVMOptions",
                                               "-XX:G1LogLevel=finest",
                                               "-XX:+G1TraceEagerReclaimHumongousObjects",
                                               GCTest.class.getName());

    OutputAnalyzer output = new OutputAnalyzer(pb.start());

    // As G1EagerReclaimHumongousObjects is set(default), below logs should be displayed.
    // And GCTest doesn't have humongous objects, so values should be zero.
    output.shouldContain("[Humongous Reclaim");
    output.shouldContain("[Humongous Total: 0]");
    output.shouldContain("[Humongous Candidate: 0]");
    output.shouldContain("[Humongous Reclaimed: 0]");

    output.shouldHaveExitValue(0);
  }

  private static void testHumongousObjectGCLogs() throws Exception {
    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-XX:+UseG1GC",
                                               "-Xms128M",
                                               "-Xmx128M",
                                               "-Xmn16M",
                                               "-XX:G1HeapRegionSize=1M",
                                               "-XX:+PrintGC",
                                               "-XX:+UnlockExperimentalVMOptions",
                                               "-XX:G1LogLevel=finest",
                                               "-XX:+G1TraceEagerReclaimHumongousObjects",
                                               GCWithHumongousObjectTest.class.getName());

    OutputAnalyzer output = new OutputAnalyzer(pb.start());

    // As G1ReclaimDeadHumongousObjectsAtYoungGC is set(default), below logs should be displayed.
    output.shouldContain("[Humongous Reclaim");
    output.shouldContain("[Humongous Total");
    output.shouldContain("[Humongous Candidate");
    output.shouldContain("[Humongous Reclaimed");

    // As G1TraceReclaimDeadHumongousObjectsAtYoungGC is set and GCWithHumongousObjectTest has humongous objects,
    // these logs should be displayed.
    output.shouldContain("Live humongous");
    output.shouldContain("Dead humongous region");
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

  static class GCWithHumongousObjectTest {

    public static final int M = 1024*1024;
    public static LinkedList<Object> garbageList = new LinkedList<Object>();
    // A large object referenced by a static.
    static int[] filler = new int[10 * M];

    public static void genGarbage() {
      for (int i = 0; i < 32*1024; i++) {
        garbageList.add(new int[100]);
      }
      garbageList.clear();
    }

    public static void main(String[] args) {

      int[] large = new int[M];
      Object ref = large;

      System.out.println("Creating garbage");
      for (int i = 0; i < 100; i++) {
        // A large object that will be reclaimed eagerly.
        large = new int[6*M];
        genGarbage();
        // Make sure that the compiler cannot completely remove
        // the allocation of the large object until here.
        System.out.println(large);
      }

      // Keep the reference to the first object alive.
      System.out.println(ref);
      System.out.println("Done");
    }
  }
}
