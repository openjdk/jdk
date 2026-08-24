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
 * @summary Verify that fixed young sizing preserves and recovers its maximum reservation
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
    private static final Pattern YOUNG_GEN = Pattern.compile(
            "Young Gen: \\[(0x[0-9a-f]+), (0x[0-9a-f]+), (0x[0-9a-f]+)\\)");

    public static void main(String[] args) throws Exception {
        // The 128m heap and 64m maximum young reservation leave 64m for old gen
        // before it must borrow reservation from young gen.
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
                "-Xbootclasspath/a:.",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                "-XX:+UseParallelGC",
                "-XX:-UseAdaptiveSizePolicy",
                "-Xms64m",
                "-Xmx128m",
                "-XX:NewSize=" + GenBoundaryWithFixedSizingWorkload.NEW_SIZE,
                "-XX:MaxNewSize=" + GenBoundaryWithFixedSizingWorkload.MAX_NEW_SIZE,
                "-XX:MarkSweepDeadRatio=100",
                "-Xlog:gc+heap=debug",
                GenBoundaryWithFixedSizingWorkload.class.getName());
        output.shouldHaveExitValue(0);

        String stdout = output.getStdout();

        // 1. A low live set gives old gen no reason to borrow from young.
        assertReservation(stdout, "LOW_LIVE", GenBoundaryWithFixedSizingWorkload.MAX_NEW_SIZE);

        // 2. Speculative promotion headroom must not consume young reservation.
        assertReservation(stdout, "PROMOTION_HEADROOM", GenBoundaryWithFixedSizingWorkload.MAX_NEW_SIZE);

        // 3. Optional retained dead space must not consume young reservation.
        assertReservation(stdout, "RETAINED_DEAD", GenBoundaryWithFixedSizingWorkload.MAX_NEW_SIZE);

        // 4. Genuine old pressure must borrow some, but not all, young reservation.
        List<Long> borrowed = reservations(stdout, "BORROW");
        if (borrowed.getLast() <= 0
         || borrowed.getLast() >= GenBoundaryWithFixedSizingWorkload.MAX_NEW_SIZE) {
            throw new RuntimeException("Old pressure did not partially borrow young reservation:\n"
                    + output.getOutput());
        }

        // 5. Removing old pressure must restore the full reservation.
        List<Long> recovery = reservations(stdout, "RECOVER");
        if (recovery.getFirst() >= GenBoundaryWithFixedSizingWorkload.MAX_NEW_SIZE
         || recovery.getLast() != GenBoundaryWithFixedSizingWorkload.MAX_NEW_SIZE) {
            throw new RuntimeException("Young reservation did not recover to MaxNewSize:\n" + output.getOutput());
        }

        // 6. Another full GC without renewed pressure must leave the boundary stable.
        assertReservation(stdout, "STABLE", GenBoundaryWithFixedSizingWorkload.MAX_NEW_SIZE);
    }

    private static void assertReservation(String output, String phase, long expected) {
        List<Long> reservations = reservations(output, phase);
        if (reservations.getLast() != expected) {
            throw new RuntimeException("Unexpected young reservation in " + phase + ": "
                    + reservations.getLast() + " != " + expected + "\n" + output);
        }
    }

    // GC heap logs describe young gen as [reserved-low, committed-high, reserved-high):
    //   PSYoungGen ... Young Gen: [0x00000007fc000000, 0x00000007ff000000, 0x0000000800000000)
    // Subtracting the first address from the third gives the reservation size.
    // The first and last entries within a phase are the pre- and post-GC states.
    private static List<Long> reservations(String output, String phase) {
        String begin = "PHASE " + phase + " BEGIN";
        String end = "PHASE " + phase + " END";
        int beginIndex = output.indexOf(begin);
        int endIndex = output.indexOf(end, beginIndex);
        if (beginIndex < 0 || endIndex < 0) {
            throw new RuntimeException("Missing " + phase + " markers:\n" + output);
        }

        List<Long> reservations = new ArrayList<>();
        Matcher matcher = YOUNG_GEN.matcher(output.substring(beginIndex, endIndex));
        while (matcher.find()) {
            long low = Long.parseUnsignedLong(matcher.group(1).substring(2), 16);
            long high = Long.parseUnsignedLong(matcher.group(3).substring(2), 16);
            reservations.add(high - low);
        }
        if (reservations.isEmpty()) {
            throw new RuntimeException("No young-generation states in " + phase + ":\n" + output);
        }
        return reservations;
    }
}

/**
 * Exercises dynamic generation-boundary changes with adaptive sizing disabled.
 * The child VM has a 128m maximum heap, a 48m initial young commitment, a 64m
 * maximum young reservation, and aggressive dead-space retention.
 *
 * The scenario:
 * 1. Run a low-live full GC without moving the generation boundary.
 * 2. Build a live set below the old-generation partition and verify that
 *    promotion headroom does not consume the young reservation.
 * 3. Replace part of the live set and verify that retained dead space does not
 *    consume the young reservation.
 * 4. Increase the live set until old gen must borrow from young.
 * 5. Remove that pressure and verify that the young reservation recovers.
 * 6. Run another full GC and verify that the recovered boundary remains stable.
 *
 * The driver reads phase-delimited heap logs to verify reservation changes,
 * while MXBeans verify non-adaptive commitment behavior.
 */
class GenBoundaryWithFixedSizingWorkload {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final int MB = 1024 * 1024;
    private static final int TARGET_PRESERVING_OBJECTS = 56;
    private static final int REPLACED_OBJECTS = 24;
    private static final int PRESSURE_OBJECTS = 88;
    private static final int RETAINED_OBJECTS = 8;
    static final long NEW_SIZE = 48L * MB;
    static final long MAX_NEW_SIZE = 64L * MB;

    public static void main(String[] args) {
        // 1. A low-live full GC while young gen still owns its full MaxNewSize
        // reservation exercises the zero-shift boundary path.
        phaseBegin("LOW_LIVE");
        WB.fullGC();
        phaseEnd("LOW_LIVE");

        List<byte[]> live = new ArrayList<>();
        // 2. Speculative promotion headroom must not consume the maximum reservation.
        for (int i = 0; i < TARGET_PRESERVING_OBJECTS; i++) {
            live.add(new byte[MB]);
        }
        phaseBegin("PROMOTION_HEADROOM");
        WB.fullGC();
        phaseEnd("PROMOTION_HEADROOM");
        assertYoungCommitted();

        // 3. Retained dead space must not consume the maximum reservation either.
        live.subList(0, REPLACED_OBJECTS).clear();
        for (int i = 0; i < REPLACED_OBJECTS; i++) {
            live.add(new byte[MB]);
        }
        phaseBegin("RETAINED_DEAD");
        WB.fullGC();
        phaseEnd("RETAINED_DEAD");
        assertYoungCommitted();

        // 4. Actual old-gen pressure may borrow from the maximum reservation.
        for (int i = TARGET_PRESERVING_OBJECTS; i < PRESSURE_OBJECTS; i++) {
            live.add(new byte[MB]);
        }
        phaseBegin("BORROW");
        WB.fullGC();
        phaseEnd("BORROW");
        long heapCommittedBeforeRecovery = heapCommitted();

        // 5. Drop old-gen pressure. Full GC should restore the maximum reservation.
        live.subList(RETAINED_OBJECTS, live.size()).clear();
        phaseBegin("RECOVER");
        WB.fullGC();
        phaseEnd("RECOVER");
        assertYoungCommitted();
        assertHeapCommittedAtLeast(heapCommittedBeforeRecovery);

        // 6. Recovery must remain stable across another full GC.
        phaseBegin("STABLE");
        WB.fullGC();
        phaseEnd("STABLE");
        assertYoungCommitted();
        assertHeapCommittedAtLeast(heapCommittedBeforeRecovery);
        Reference.reachabilityFence(live);
    }

    // Delimit explicit full GCs so the driver can associate heap logs with each scenario.
    private static void phaseBegin(String phase) {
        System.out.println("PHASE " + phase + " BEGIN");
    }

    private static void phaseEnd(String phase) {
        System.out.println("PHASE " + phase + " END");
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
