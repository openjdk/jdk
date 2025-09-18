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
 * @bug 8367754
 * @summary Verify fast path before performing heap resize.
 * @requires vm.gc.G1
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.g1.TestHeapResizeAfterGC
 */

package gc.g1;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TestHeapResizeAfterGC {

  private static OutputAnalyzer run(String maxHeap, String minHeap, String regionSize) throws Exception {
    return ProcessTools.executeLimitedTestJava(
      "-XX:+UnlockDiagnosticVMOptions",
      "-XX:+WhiteBoxAPI",
      "-Xbootclasspath/a:.",
      "-Xmx" + maxHeap,
      "-Xms" + minHeap,
      "-XX:G1HeapRegionSize=" + regionSize,
      "-XX:+UseG1GC",
      "-XX:MaxTenuringThreshold=1",
      "-XX:MinHeapFreeRatio=65",
      "-Xlog:gc+ergo+heap=trace",
      TestHeapResizeAfterGC.Action.class.getName());
    }

  public static void main(String args[]) throws Exception {
    String resizeLogAfterYoungGC = "Heap resize triggers";
    String resizeLogAfterFullGC = "Heap resize. Attempt heap";
    String skipResizeLogAfterYoungGC = "Skip heap resize after young";
    String skipResizeLogAfterFullGC = "Skip heap resize after full";

    // Normal path.
    OutputAnalyzer out = run("120M","50M","1M");
    out.shouldHaveExitValue(0);
    out.shouldNotContain(skipResizeLogAfterYoungGC);
    out.shouldNotContain(skipResizeLogAfterFullGC);
    out.shouldContain(resizeLogAfterYoungGC);
    out.shouldContain(resizeLogAfterFullGC);

    // Fast path, MaxHeapSize(103M) and MinHeapSize(101M) will be equal
    // after alignment.
    out = run("103M","101M","4M");
    out.shouldHaveExitValue(0);
    out.shouldContain(skipResizeLogAfterYoungGC);
    out.shouldContain(skipResizeLogAfterFullGC);
    out.shouldNotContain(resizeLogAfterYoungGC);
    out.shouldNotContain(resizeLogAfterFullGC);
  }

  public static class Action {
    private static final WhiteBox wb = WhiteBox.getWhiteBox();
    private static final int OBJECT_SIZE = 1024;
    private static final int NUM_OBJECTS = 20_000;
    public static void main(String [] args) throws Exception {
      // Remove garbage from VM initialization.
      wb.fullGC();
      List<byte[]> objectList = new ArrayList<>();
      for (int i = 0; i < NUM_OBJECTS; i++) {
        byte[] obj = new byte[OBJECT_SIZE];
        objectList.add(obj);
      }
      wb.youngGC();
      wb.fullGC();
    }
  }
}
