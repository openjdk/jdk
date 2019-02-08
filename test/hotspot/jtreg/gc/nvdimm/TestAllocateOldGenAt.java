/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package gc.nvdimm;

/* @test TestAllocateOldGenAt.java
 * @key gc
 * @summary Test to check allocation of Java Heap with AllocateOldGenAt option
 * @requires vm.gc=="null" & os.family != "aix"
 * @requires test.vm.gc.nvdimm
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run main gc.nvdimm.TestAllocateOldGenAt
 */

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import java.util.ArrayList;
import java.util.Collections;

public class TestAllocateOldGenAt {
  private static ArrayList<String> commonOpts;

  public static void main(String args[]) throws Exception {
    commonOpts = new ArrayList<>();

    String testVmOptsStr = System.getProperty("test.java.opts");
    if (!testVmOptsStr.isEmpty()) {
      String[] testVmOpts = testVmOptsStr.split(" ");
      Collections.addAll(commonOpts, testVmOpts);
    }
    String test_dir = System.getProperty("test.dir", ".");
    Collections.addAll(commonOpts, new String[] {"-XX:+UnlockExperimentalVMOptions",
                                                 "-XX:AllocateOldGenAt=" + test_dir,
                                                 "-Xmx32m",
                                                 "-Xms32m",
                                                 "-version"});

    runTest("-XX:+UseG1GC");
    runTest("-XX:+UseParallelOldGC -XX:-UseAdaptiveGCBoundary");
    runTest("-XX:+UseParallelOldGC -XX:+UseAdaptiveGCBoundary");
  }

  private static void runTest(String... extraFlags) throws Exception {
    ArrayList<String> testOpts = new ArrayList<>();
    Collections.addAll(testOpts, commonOpts.toArray(new String[commonOpts.size()]));
    Collections.addAll(testOpts, extraFlags);

    System.out.print("Testing:\n" + JDKToolFinder.getJDKTool("java"));
    for (int i = 0; i < testOpts.size(); i += 1) {
      System.out.print(" " + testOpts.get(i));
    }
    System.out.println();

    ProcessBuilder pb =
      ProcessTools.createJavaProcessBuilder(testOpts.toArray(new String[testOpts.size()]));
    OutputAnalyzer output = new OutputAnalyzer(pb.start());

    output.shouldHaveExitValue(0);

  }
}
