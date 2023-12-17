/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package gc.g1;

/*
 * @test
 * @bug 8297186
 * @requires vm.gc.G1
 * @library /test/lib
 * @run driver gc.g1.TestOneEdenRegionAfterGC
 * @summary Test that on a very small heap g1 with very little data (smaller than region size)
 *          will use at least one eden region after gc to avoid full gcs.
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestOneEdenRegionAfterGC {
  private static long YoungGenSize = 32 * 1024 * 1024;

  private static OutputAnalyzer run() throws Exception {
    ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
      "-Xbootclasspath/a:.",
      "-Xmn" + YoungGenSize,
      "-Xmx512M",
      "-Xms512M",
      "-XX:G1HeapRegionSize=32M",
      "-XX:+UseG1GC",
      "-Xlog:gc,gc+ergo*=trace",
      TestOneEdenRegionAfterGC.Allocate.class.getName(),
      "" + YoungGenSize);
    return new OutputAnalyzer(pb.start());
  }

  public static void main(String args[]) throws Exception {
    OutputAnalyzer out = run();

    out.shouldMatch(".*Pause Young \\(Normal\\).*");
    out.shouldNotMatch(".*Pause Full.*");
  }

  public static class Allocate {
    public static Object dummy;

    public static void main(String [] args) throws Exception {
      if (args.length != 1) {
        throw new IllegalArgumentException("Usage: <YoungGenSize>");
      }

      long youngGenSize = Long.parseLong(args[0]);
      triggerYoungGCs(youngGenSize);
    }

    public static void triggerYoungGCs(long youngGenSize) {
      long approxAllocSize = 32 * 1024;
      long numAllocations  = 2 * youngGenSize / approxAllocSize;

      for (long i = 0; i < numAllocations; i++) {
        dummy = new byte[(int)approxAllocSize];
      }
    }
  }
}
