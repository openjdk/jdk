/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestHumongousConcurrentStartUndo
 * @summary Tests an alternating sequence of Concurrent Mark and Concurrent Undo
 * cycles.
 * reclaim heap occupancy falls below the IHOP value.
 * @requires vm.gc.G1
 * @library /test/lib /testlibrary /
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *             sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   gc.g1.TestHumongousConcurrentStartUndo
 */

import gc.testlibrary.Helpers;

import sun.hotspot.WhiteBox;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class TestHumongousConcurrentStartUndo {
    // Heap sizes < 224 MB are increased to 224 MB if vm_page_size == 64K to
    // fulfill alignment constraints.
    private static final int HeapSize                       = 224; // MB
    private static final int HeapRegionSize                 = 1;   // MB
    private static final int InitiatingHeapOccupancyPercent = 50;  // %
    private static final int YoungSize                      = HeapSize / 8;

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xbootclasspath/a:.",
            "-XX:+UseG1GC",
            "-Xms" + HeapSize + "m",
            "-Xmx" + HeapSize + "m",
            "-Xmn" + YoungSize + "m",
            "-XX:G1HeapRegionSize=" + HeapRegionSize + "m",
            "-XX:InitiatingHeapOccupancyPercent=" + InitiatingHeapOccupancyPercent,
            "-XX:-G1UseAdaptiveIHOP",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI",
            "-Xlog:gc*",
            EdenObjectAllocatorWithHumongousAllocation.class.getName());

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Pause Young (Concurrent Start) (G1 Humongous Allocation)");
        output.shouldContain("Concurrent Undo Cycle");
        output.shouldContain("Concurrent Mark Cycle");
        output.shouldHaveExitValue(0);
        System.out.println(output.getStdout());
    }

    static class EdenObjectAllocatorWithHumongousAllocation {
        private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

        private static void allocateHumongous(int num, int objSize, Queue keeper) {
            for (int i = 1; i <= num; i++) {
                if (i % 10 == 0) {
                    System.out.println("Allocating humongous object " + i + "/" + num +
                                       " of size " + objSize + " bytes");
                }
                byte[] e = new byte[objSize];
                if (!keeper.offer(e)) {
                    keeper.remove();
                    keeper.offer(e);
                }
            }
        }

        public static void main(String [] args) throws Exception {
            final int M = 1024 * 1024;
            // Make humongous object size 75% of region size
            final int humongousObjectSize =
                (int)(HeapRegionSize * M * 0.75);

            // Number of objects to allocate to go above IHOP
            final int humongousObjectAllocations =
                (int)(((HeapSize - YoungSize) * 80 / 100.0) / HeapRegionSize);

            ArrayBlockingQueue a;
            for (int iterate = 0; iterate < 3; iterate++) {
                // Start from an "empty" heap.
                WHITE_BOX.fullGC();
                // The queue only holds one element, so only one humongous object
                // will be reachable and the concurrent operation should be undone.
                a = new ArrayBlockingQueue(1);
                allocateHumongous(humongousObjectAllocations, humongousObjectSize, a);
                Helpers.waitTillCMCFinished(WHITE_BOX, 1);
                a = null;

                // Start from an "empty" heap.
                WHITE_BOX.fullGC();
                // The queue only holds all elements, so all humongous object
                // will be reachable and the concurrent operation should be a regular mark.
                a = new ArrayBlockingQueue(humongousObjectAllocations);
                allocateHumongous(humongousObjectAllocations, humongousObjectSize, a);
                Helpers.waitTillCMCFinished(WHITE_BOX, 1);
                a = null;

                allocateHumongous(1, humongousObjectSize, new ArrayBlockingQueue(1));
                Helpers.waitTillCMCFinished(WHITE_BOX, 1);
            }
        }
    }
}

