/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/* @test TestObjectAlignmentCardSize.java
 * @summary Test to check correct handling of ObjectAlignmentInBytes and GCCardSizeInBytes combinations
 * @requires vm.gc != "Z"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run driver gc.TestObjectAlignmentCardSize
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestObjectAlignmentCardSize {
  private static void runTest(int objectAlignment, int cardSize, boolean shouldSucceed) throws Exception {
    OutputAnalyzer output = ProcessTools.executeTestJava(
        "-XX:ObjectAlignmentInBytes=" + objectAlignment,
        "-XX:GCCardSizeInBytes=" + cardSize,
        "-Xmx32m",
        "-Xms32m",
        "-version");

    System.out.println("Output:\n" + output.getOutput());

    if (shouldSucceed) {
      output.shouldHaveExitValue(0);
    } else {
      output.shouldContain("Invalid combination of GCCardSizeInBytes and ObjectAlignmentInBytes");
      output.shouldNotHaveExitValue(0);
    }
  }

  public static void main(String[] args) throws Exception {
    runTest(8, 512, true);
    runTest(128, 128, true);
    runTest(256, 128, false);
    runTest(256, 256, true);
    runTest(256, 512, true);
  }
}
