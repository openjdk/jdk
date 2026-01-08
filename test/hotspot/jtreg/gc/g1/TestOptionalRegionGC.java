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

/* @test
 * @bug 8352969
 * @summary Tests optional evacuation.
 * @requires vm.gc.G1
 * @requires vm.debug
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.g1.TestOptionalRegionGC
 */

package gc.g1;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestOptionalRegionGC {

  private static OutputAnalyzer run() throws Exception {
    return ProcessTools.executeLimitedTestJava(
      "-XX:+WhiteBoxAPI",
      "-Xbootclasspath/a:.",
      "-Xmx300M",
      "-Xms300M",
      "-XX:G1HeapRegionSize=1M",
      "-XX:+UseG1GC",
      "-XX:MaxTenuringThreshold=1",
      "-Xlog:gc+ergo+cset=trace",
      "-XX:+G1ForceOptionalEvacuation",
      "-XX:+VerifyAfterGC",
      TestOptionalRegionGC.Action.class.getName());
  }

  public static void main(String args[]) throws Exception {
    OutputAnalyzer out = run();
    out.shouldHaveExitValue(0);
    Pattern pattern = Pattern.compile("Prepared (\\d+) regions out of (\\d+) for optional evacuation");
    Matcher matcher = pattern.matcher(out.getOutput());
    Asserts.assertTrue(matcher.find());
    String selectedNum = matcher.group(1);
    String totalNum = matcher.group(2);
    Asserts.assertTrue(Objects.equals(selectedNum, totalNum), "Error info: " + selectedNum + ", " + totalNum);
  }

  public static class Action {
    private static final WhiteBox wb = WhiteBox.getWhiteBox();
    private static final int MIN_OBJECT_SIZE = 64 * 1024;
    private static final int MAX_OBJECT_SIZE = 120 * 1024;
    private static final int NUM_OBJECTS = 1200;

    public static void main(String [] args) throws Exception {
      // Remove garbage from VM initialization.
      wb.fullGC();
      Random rand = new Random(42);
      List<byte[]> objectList = new ArrayList<>();
      for (int i = 0; i < NUM_OBJECTS; i++) {
        int objSize = MIN_OBJECT_SIZE + rand.nextInt(MAX_OBJECT_SIZE - MIN_OBJECT_SIZE);
        byte[] obj = new byte[objSize];
        objectList.add(obj);
      }
      // Young GC promotes some objects to the old generation.
      wb.youngGC();
      // Clear certain references for mixed GC.
      for (int i = 0; i < NUM_OBJECTS; i+=2) {
        objectList.set(i, null);
      }
      wb.g1RunConcurrentGC();
      // Perform the "Prepare Mixed" GC.
      wb.youngGC();
      // Perform the "Mixed" GC.
      wb.youngGC();
    }
  }
}
