/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

/* @test TestAllocateOldGenAtError.java
 * @key gc
 * @summary Test to check correct handling of non-existent directory passed to AllocateOldGenAt option
 * @requires vm.gc=="null" & os.family != "aix"
 * @requires test.vm.gc.nvdimm
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run driver gc.nvdimm.TestAllocateOldGenAtError
 */

import java.io.File;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

public class TestAllocateOldGenAtError {
  private static String[] commonFlags;

  public static void main(String args[]) throws Exception {
    String test_dir = System.getProperty("test.dir", ".");

    File f = null;
    do {
      f = new File(test_dir, UUID.randomUUID().toString());
    } while(f.exists());

    commonFlags = new String[] {
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:AllocateOldGenAt=" + f.getName(),
        "-Xlog:gc+heap=info",
        "-Xmx32m",
        "-Xms32m",
        "-version"};

    testG1();
    testParallelOld();
  }

  private static void testG1() throws Exception {
    System.out.println("Testing G1 GC");

    OutputAnalyzer output = runTest("-XX:+UseG1GC");

    output.shouldContain("Could not initialize G1 heap");
    output.shouldContain("Error occurred during initialization of VM");
    output.shouldNotHaveExitValue(0);

  }

  private static void testParallelOld() throws Exception {
    System.out.println("Testing Parallel GC");

    OutputAnalyzer output = runTest("-XX:+UseParallelGC");

    output.shouldContain("Error occurred during initialization of VM");
    output.shouldNotHaveExitValue(0);
  }

  private static OutputAnalyzer runTest(String... extraFlags) throws Exception {
    ArrayList<String> flags = new ArrayList<>();
    Collections.addAll(flags, commonFlags);
    Collections.addAll(flags, extraFlags);

    ProcessBuilder pb = ProcessTools.createTestJvm(flags);
    OutputAnalyzer output = new OutputAnalyzer(pb.start());
    return output;
  }
}
