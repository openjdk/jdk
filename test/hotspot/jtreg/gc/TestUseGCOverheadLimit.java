/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package gc;

/*
 * @test id=Parallel
 * @requires vm.gc.Parallel
 * @requires !vm.debug
 * @summary Verifies that the UseGCOverheadLimit functionality works in Parallel GC.
 * @library /test/lib
 * @run driver gc.TestUseGCOverheadLimit Parallel
 */

/*
 * @test id=G1
 * @requires vm.gc.G1
 * @requires !vm.debug
 * @summary Verifies that the UseGCOverheadLimit functionality works in G1 GC.
 * @library /test/lib
 * @run driver gc.TestUseGCOverheadLimit G1
 */

import java.util.Arrays;
import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestUseGCOverheadLimit {
  public static void main(String args[]) throws Exception {
    String[] parallelArgs = {
      "-XX:+UseParallelGC",
      "-XX:NewSize=122m",
      "-XX:SurvivorRatio=99",
      "-XX:GCHeapFreeLimit=10"
    };
    String[] g1Args = {
      "-XX:+UseG1GC",
      "-XX:GCHeapFreeLimit=5"
    };

    String[] selectedArgs = args[0].equals("G1") ? g1Args : parallelArgs;

    final String[] commonArgs = {
      "-XX:-UseCompactObjectHeaders", // Object sizes are calculated such that the heap is tight.
      "-XX:ParallelGCThreads=1",      // Make GCs take longer.
      "-XX:+UseGCOverheadLimit",
      "-Xlog:gc=debug",
      "-XX:GCTimeLimit=80",           // Ease the CPU requirement.
      "-Xmx128m",
      Allocating.class.getName()
    };

    String[] vmArgs = Stream.concat(Arrays.stream(selectedArgs), Arrays.stream(commonArgs)).toArray(String[]::new);
    OutputAnalyzer output = ProcessTools.executeLimitedTestJava(vmArgs);
    output.shouldNotHaveExitValue(0);

    System.out.println(output.getStdout());

    output.stdoutShouldContain("GC Overhead Limit exceeded too often (5).");
  }

  static class Allocating {
    public static void main(String[] args) {
      Object[] cache = new Object[1024 * 1024 * 2];

      // Allocate random objects, keeping around data, causing garbage
      // collections.
      for (int i = 0; i < 1024* 1024 * 30; i++) {
        Object[] obj = new Object[10];
        cache[i % cache.length] = obj;
      }

      System.out.println(cache);
    }
  }
}
