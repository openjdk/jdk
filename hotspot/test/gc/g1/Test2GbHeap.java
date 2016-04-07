/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test Test2GbHeap
 * @bug 8031686
 * @summary Regression test to ensure we can start G1 with 2gb heap.
 * @key gc
 * @key regression
 * @library /testlibrary
 * @modules java.base/sun.misc
 *          java.management
 */

import java.util.ArrayList;

import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.ProcessTools;

public class Test2GbHeap {
  public static void main(String[] args) throws Exception {
    ArrayList<String> testArguments = new ArrayList<String>();

    testArguments.add("-XX:+UseG1GC");
    testArguments.add("-Xmx2g");
    testArguments.add("-version");

    ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(testArguments.toArray(new String[0]));

    OutputAnalyzer output = new OutputAnalyzer(pb.start());

    // Avoid failing test for setups not supported.
    if (output.getOutput().contains("Could not reserve enough space for 2097152KB object heap")) {
      // Will fail on machines with too little memory (and Windows 32-bit VM), ignore such failures.
      output.shouldHaveExitValue(1);
    } else if (output.getOutput().contains("-XX:+UseG1GC not supported in this VM")) {
      // G1 is not supported on embedded, ignore such failures.
      output.shouldHaveExitValue(1);
    } else {
      // Normally everything should be fine.
      output.shouldHaveExitValue(0);
    }
  }
}
