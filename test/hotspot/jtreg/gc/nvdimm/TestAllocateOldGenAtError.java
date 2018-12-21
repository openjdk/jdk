/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

/* @test TestAllocateOldGenAtError.java
 * @key gc
 * @summary Test to check correct handling of non-existent directory passed to AllocateOldGenAt option
 * @requires vm.gc=="null"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 */

import java.io.File;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

public class TestAllocateOldGenAtError {
  private static ArrayList<String> commonOpts;

  public static void main(String args[]) throws Exception {
    commonOpts = new ArrayList();

    String testVmOptsStr = System.getProperty("test.java.opts");
    if (!testVmOptsStr.isEmpty()) {
      String[] testVmOpts = testVmOptsStr.split(" ");
      Collections.addAll(commonOpts, testVmOpts);
    }
    String test_dir = System.getProperty("test.dir", ".");

    File f = null;
    do {
      f = new File(test_dir, UUID.randomUUID().toString());
    } while(f.exists());

    Collections.addAll(commonOpts, new String[] {"-XX:+UnlockExperimentalVMOptions",
                                                 "-XX:AllocateOldGenAt=" + f.getName(),
                                                 "-Xlog:gc+heap=info",
                                                 "-Xmx32m",
                                                 "-Xms32m",
                                                 "-version"});

    testG1();
  }

  private static void testG1() throws Exception {
    System.out.println("Testing G1 GC");

    OutputAnalyzer output = runTest("-XX:+UseG1GC");

    output.shouldContain("Could not initialize G1 heap");
    output.shouldContain("Error occurred during initialization of VM");
    output.shouldNotHaveExitValue(0);
  }

  private static OutputAnalyzer runTest(String... extraFlags) throws Exception {
    ArrayList<String> testOpts = new ArrayList();
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
    return output;
  }
}
