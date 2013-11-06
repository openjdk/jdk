/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestHumongousAllocInitialMark
 * @bug 7168848
 * @summary G1: humongous object allocations should initiate marking cycles when necessary
 * @library /testlibrary
 */

import com.oracle.java.testlibrary.*;

public class TestHumongousAllocInitialMark {
    private static final int heapSize                       = 200; // MB
    private static final int heapRegionSize                 = 1;   // MB
    private static final int initiatingHeapOccupancyPercent = 50;  // %

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-XX:+UseG1GC",
            "-Xms" + heapSize + "m",
            "-Xmx" + heapSize + "m",
            "-XX:G1HeapRegionSize=" + heapRegionSize + "m",
            "-XX:InitiatingHeapOccupancyPercent=" + initiatingHeapOccupancyPercent,
            "-XX:+PrintGC",
            HumongousObjectAllocator.class.getName());

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("GC pause (G1 Humongous Allocation) (young) (initial-mark)");
        output.shouldNotContain("Full GC");
        output.shouldHaveExitValue(0);
    }

    static class HumongousObjectAllocator {
        private static byte[] dummy;

        public static void main(String [] args) throws Exception {
            // Make object size 75% of region size
            final int humongousObjectSize =
                (int)(heapRegionSize * 1024 * 1024 * 0.75);

            // Number of objects to allocate to go above IHOP
            final int humongousObjectAllocations =
                (int)((heapSize * initiatingHeapOccupancyPercent / 100.0) / heapRegionSize) + 1;

            // Allocate
            for (int i = 1; i <= humongousObjectAllocations; i++) {
                System.out.println("Allocating humongous object " + i + "/" + humongousObjectAllocations +
                                   " of size " + humongousObjectSize + " bytes");
                dummy = new byte[humongousObjectSize];
            }
        }
    }
}

