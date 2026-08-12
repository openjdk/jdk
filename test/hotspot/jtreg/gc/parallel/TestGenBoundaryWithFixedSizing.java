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

package gc.parallel;

/*
 * @test TestGenBoundaryWithFixedSizing
 * @bug 8386885
 * @summary Verify that fixed young sizing recovers reservation borrowed by old gen
 * @requires vm.gc.Parallel
 * @requires vm.flagless
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.parallel.TestGenBoundaryWithFixedSizing
 */

import java.lang.ref.Reference;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class TestGenBoundaryWithFixedSizing {
    private static final long BYTES_PER_K = 1024;
    private static final Pattern RECOVERY = Pattern.compile(
            "Young generation reservation after full GC: (\\d+)K -> (\\d+)K");

    public static void main(String[] args) throws Exception {
        // The 128m heap and 64m maximum young generation leave at most 64m for
        // old gen before it must borrow reservation from young gen.
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-Xbootclasspath/a:.",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                "-XX:+UseParallelGC",
                "-XX:-UseAdaptiveSizePolicy",
                "-Xms64m",
                "-Xmx128m",
                "-XX:NewSize=" + GenBoundaryWithFixedSizingWorkload.NEW_SIZE,
                "-XX:MaxNewSize=64m",
                "-Xlog:gc+heap=debug",
                GenBoundaryWithFixedSizingWorkload.class.getName());
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldHaveExitValue(0);

        Matcher matcher = RECOVERY.matcher(output.getStdout());
        while (matcher.find()) {
            long before = Long.parseLong(matcher.group(1));
            long after = Long.parseLong(matcher.group(2));
            if (before * BYTES_PER_K < GenBoundaryWithFixedSizingWorkload.NEW_SIZE
                    && after * BYTES_PER_K >= GenBoundaryWithFixedSizingWorkload.NEW_SIZE) {
                return;
            }
        }
        throw new RuntimeException("Young generation reservation did not recover:\n" + output.getOutput());
    }
}

class GenBoundaryWithFixedSizingWorkload {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final int MB = 1024 * 1024;
    private static final int PRESSURE_OBJECTS = 72;
    private static final int RETAINED_OBJECTS = 8;
    static final long NEW_SIZE = 48L * MB;

    public static void main(String[] args) {
        // A low-live full GC while young gen still owns its full MaxNewSize
        // reservation exercises the zero-shift boundary path.
        WB.fullGC();

        List<byte[]> live = new ArrayList<>();
        // More than 64m of live data forces old gen to borrow young reservation.
        for (int i = 0; i < PRESSURE_OBJECTS; i++) {
            live.add(new byte[MB]);
        }
        WB.fullGC();
        long heapCommittedBeforeRecovery = heapCommitted();

        // Drop old-gen pressure. Full GC should restore at least the fixed 48m young size.
        live.subList(RETAINED_OBJECTS, live.size()).clear();
        WB.fullGC();
        assertYoungCommitted();
        assertHeapCommittedAtLeast(heapCommittedBeforeRecovery);

        // Recovery must remain stable across another full GC.
        WB.fullGC();
        assertYoungCommitted();
        assertHeapCommittedAtLeast(heapCommittedBeforeRecovery);
        Reference.reachabilityFence(live);
    }

    private static long heapCommitted() {
        return oldCommitted() + youngCommitted();
    }

    private static void assertHeapCommittedAtLeast(long expected) {
        long actual = heapCommitted();
        if (actual < expected) {
            throw new RuntimeException("Heap commitment shrank with adaptive sizing disabled: "
                    + actual + " < " + expected);
        }
    }

    private static void assertYoungCommitted() {
        long youngCommitted = youngCommitted();
        if (youngCommitted < NEW_SIZE) {
            throw new RuntimeException("Young generation commitment did not recover: "
                    + youngCommitted + " < " + NEW_SIZE);
        }
    }

    private static long youngCommitted() {
        long edenCommitted = -1;
        long survivorCommitted = -1;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getName().contains("Eden")) {
                edenCommitted = pool.getUsage().getCommitted();
            } else if (pool.getName().contains("Survivor")) {
                survivorCommitted = pool.getUsage().getCommitted();
            }
        }
        if (edenCommitted < 0 || survivorCommitted < 0) {
            throw new RuntimeException("Parallel young-generation memory pools not found");
        }
        // The MXBean exposes one survivor pool, but both from and to spaces are committed.
        return edenCommitted + 2 * survivorCommitted;
    }

    private static long oldCommitted() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getName().contains("Old")) {
                return pool.getUsage().getCommitted();
            }
        }
        throw new RuntimeException("Parallel old-generation memory pool not found");
    }
}
