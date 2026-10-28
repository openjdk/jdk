/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestHumongousFullGCLeak
 * @bug 8394730
 * @summary Compaction of humongous objects during Full GC should not leak card set memory.
 * @requires vm.gc.G1
 * @requires vm.flagless
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -Xmx64M -Xms64M -Xmn6M
 *                   -XX:MaxTenuringThreshold=0 -XX:+UseG1GC -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=summary
 *                   -XX:G1HeapRegionSize=1M gc.g1.TestHumongousFullGCLeak
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.dcmd.JMXExecutor;
import jdk.test.whitebox.WhiteBox;

import java.lang.ref.Reference;

public class TestHumongousFullGCLeak {
    private static final int M = 1024 * 1024;

    static int nmt() throws Exception {
        OutputAnalyzer output = new JMXExecutor().execute("VM.native_memory summary scale=1", true);

        String stdout = output.getStdout();
        int i = stdout.indexOf("GCCardSet");
        if (i < 0) {
            throw new Exception("GCCardSet category not found.");
        }
        int j = stdout.indexOf("#", i);
        // Example:
        // GCCardSet (reserved=8720, committed=8720) (malloc=8720 tag=GCCardSet #99)
        String gcCardSetLine = stdout.substring(i, stdout.indexOf(')', j) + 1).replaceAll("\\s+", " ").trim();
        System.out.println(gcCardSetLine);
        int mallocStart = gcCardSetLine.indexOf("malloc=") + 7;
        return Integer.parseInt(gcCardSetLine.substring(mallocStart, gcCardSetLine.indexOf(" ", mallocStart)));
    }

    public static void main(String[] args) throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();

        long regionSize = wb.g1RegionSize();
        // We want to create partially overlapping moves of humongous objects. We do that by allocating
        // regular objects that die in old gen, allowing the humongous object to move if we cause a
        // maximally compacting full gc.
        int mallocAtStart = nmt();
        boolean hadOverlappingMove = false;
        for (int j = 0; j < 20; j++) {
            Object live;
            Object humongous;
            // Take the 2nd round as reference value, the first is not warmed up.
            if (j == 2) {
                mallocAtStart = nmt();
            }
            wb.fullGC();
            // Something that gets promoted into old gen that is later reclaimed during the next round again.
            live = new Object();
            // Cause a young gc. That "live" object will be promoted due to MaxTenuringThreshold=0
            wb.youngGC();
            // Make sure that that object survives.
            Reference.reachabilityFence(live);
            // Allocate our humongous object that is moving.
            humongous = new int[7 * M / 4];
            long allocationAddress = wb.getObjectAddress(humongous);
            // Cause a maximal compaction gc.
            try {
                Object big = new byte[64 * M];
            } catch (OutOfMemoryError e) {
            }
            long displacement = allocationAddress - wb.getObjectAddress(humongous);
            long objectSize = wb.getObjectSize(humongous);
            long objectFootprint = ((objectSize + regionSize - 1) / regionSize) * regionSize;

            // Synchronize checking for overlap with starting the memory probe at iteration 2.
            if (j >= 2) {
                hadOverlappingMove |= displacement > 0 && displacement < objectFootprint;
            }
            System.out.println("Malloc: " + nmt());
        }
        int mallocAtEnd = nmt();
        Asserts.assertTrue(hadOverlappingMove, "No overlapping move");
        Asserts.assertLessThan((double)mallocAtEnd, mallocAtStart * 1.2);
    }
}
