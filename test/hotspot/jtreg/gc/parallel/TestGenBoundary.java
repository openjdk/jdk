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
 * @test TestGenBoundary
 * @bug 8386885
 * @summary Verify Parallel GC generation-boundary transitions with adaptive sizing
 * @requires vm.gc.Parallel
 * @requires vm.flagless
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver gc.parallel.TestGenBoundary
 */

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

import static jdk.test.lib.Asserts.assertEQ;
import static jdk.test.lib.Asserts.assertGT;
import static jdk.test.lib.Asserts.assertLT;

public class TestGenBoundary {
    private static final Pattern HEAP_STATE = Pattern.compile(
            "Young Gen: \\[(0x[0-9a-f]+), (0x[0-9a-f]+), (0x[0-9a-f]+)\\).*?"
            + "Old Gen: \\[(0x[0-9a-f]+), (0x[0-9a-f]+), (0x[0-9a-f]+)\\)",
            Pattern.DOTALL);

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-Xbootclasspath/a:.",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                "-XX:+UseParallelGC",
                "-XX:+UseAdaptiveSizePolicy",
                "-Xms72m",
                "-Xmx128m",
                "-XX:NewSize=64m",
                "-XX:MaxNewSize=64m",
                // Bypass delayed shrinking and retain no extra committed old-gen space.
                "-XX:MinHeapFreeRatio=0",
                "-XX:MaxHeapFreeRatio=0",
                "-XX:GCTimeRatio=1000000",
                "-Xlog:gc+heap=debug",
                DynamicBoundaryWorkload.class.getName());
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldHaveExitValue(0);

        String stdout = output.getStdout();

        // 1. The boundary moved right while young gen remained non-empty.
        List<HeapState> rightShift = states(stdout, "RIGHT_SHIFT");
        assertLT(rightShift.getFirst().boundary(), rightShift.getLast().boundary(),
                 "Full GC did not move the boundary right");
        assertGT(rightShift.getLast().youngReserved(), 0L,
                 "Right shift unexpectedly removed young gen");

        // 2. Old commitment shrank without moving the boundary left.
        List<HeapState> release = states(stdout, "RELEASE");
        assertBoundaryUnchanged(release, "Full GC moved the boundary after pressure disappeared");
        assertGT(release.getFirst().oldCommitted(), release.getLast().oldCommitted(),
                 "Old commitment did not decrease after pressure disappeared");

        // 3. Adaptive young GC moved the boundary left for more room.
        List<HeapState> youngGrowth = states(stdout, "YOUNG_GROWTH");
        assertGT(youngGrowth.getFirst().boundary(), youngGrowth.getLast().boundary(),
                 "Young GC did not move the boundary left");
        assertGT(youngGrowth.getLast().youngReserved(), 0L,
                 "Young gen is empty after young-GC growth");

        // 4. Maximum old pressure removed the young generation.
        List<HeapState> oldOnly = states(stdout, "OLD_ONLY");
        assertEQ(oldOnly.getLast().youngReserved(), 0L,
                 "Maximum old pressure did not enter old-only mode");

        // 5. Removing old pressure recreated the young generation.
        List<HeapState> recreate = states(stdout, "RECREATE");
        assertEQ(recreate.getFirst().youngReserved(), 0L,
                 "Young gen was recreated before old pressure disappeared");
        assertGT(recreate.getLast().youngReserved(), 0L,
                 "Full GC did not recreate young gen");
        assertGT(recreate.getFirst().boundary(), recreate.getLast().boundary(),
                 "Young-gen recreation did not move the boundary left");

        // 6. Further young GCs reached a stable, non-empty boundary.
        List<HeapState> stable = states(stdout, "STABLE");
        assertStableTail(stable, 6, "Young GCs did not reach a stable boundary");
        assertGT(stable.getLast().youngReserved(), 0L,
                 "Young gen disappeared during stable young GCs");
    }

    private static List<HeapState> states(String output, String phase) {
        String begin = "PHASE " + phase + " BEGIN";
        String end = "PHASE " + phase + " END";
        int beginIndex = output.indexOf(begin);
        int endIndex = output.indexOf(end, beginIndex);
        if (beginIndex < 0 || endIndex < 0) {
            throw new RuntimeException("Missing " + phase + " markers:\n" + output);
        }

        List<HeapState> states = new ArrayList<>();
        Matcher matcher = HEAP_STATE.matcher(output.substring(beginIndex, endIndex));
        while (matcher.find()) {
            HeapState state = new HeapState(address(matcher.group(1)),
                                            address(matcher.group(2)),
                                            address(matcher.group(3)),
                                            address(matcher.group(4)),
                                            address(matcher.group(5)),
                                            address(matcher.group(6)));
            state.verify();
            states.add(state);
        }
        if (states.isEmpty()) {
            throw new RuntimeException("No heap states in " + phase + " phase:\n" + output);
        }
        return states;
    }

    private static long address(String value) {
        return Long.parseUnsignedLong(value.substring(2), 16);
    }

    private static void assertBoundaryUnchanged(List<HeapState> states, String message) {
        long expected = states.getFirst().boundary();
        for (HeapState state : states) {
            assertEQ(state.boundary(), expected, message);
        }
    }

    private static void assertStableTail(List<HeapState> states, int count, String message) {
        if (states.size() < count) {
            throw new RuntimeException("Not enough heap states to verify stability: " + states.size());
        }
        List<HeapState> tail = states.subList(states.size() - count, states.size());
        long expected = tail.getFirst().boundary();
        for (HeapState state : tail) {
            assertEQ(state.boundary(), expected, message);
            assertGT(state.youngReserved(), 0L, "Young gen disappeared while stabilizing");
        }
    }

    private record HeapState(long youngLow, long youngCommittedHigh, long youngHigh,
                             long oldLow, long oldCommittedHigh, long oldHigh) {
        long boundary() {
            return youngLow;
        }

        long youngReserved() {
            return youngHigh - youngLow;
        }

        long oldCommitted() {
            return oldCommittedHigh - oldLow;
        }

        void verify() {
            assertEQ(oldHigh, youngLow, "Generation reservations are not adjacent");
            if (youngLow > youngCommittedHigh || youngCommittedHigh > youngHigh) {
                throw new RuntimeException("Invalid young-generation range: " + this);
            }
            if (oldLow > oldCommittedHigh || oldCommittedHigh > oldHigh) {
                throw new RuntimeException("Invalid old-generation range: " + this);
            }
        }
    }
}

class DynamicBoundaryWorkload {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static final int MB = 1024 * 1024;

    private static byte[] oldPressure;
    private static byte[] additionalPressure;
    private static List<byte[]> youngPressure;

    public static void main(String[] args) throws Exception {
        // 1. Move the boundary right while keeping young gen non-empty.
        phaseBegin("RIGHT_SHIFT");
        // The first allocation fits the original 64m old reservation. The
        // additional live data makes the explicit full GC borrow from young.
        oldPressure = new byte[52 * MB];
        additionalPressure = new byte[20 * MB];
        WB.fullGC();
        phaseEnd("RIGHT_SHIFT");

        // 2. Drop pressure; full GC should shrink old commitment without moving left.
        phaseBegin("RELEASE");
        oldPressure = null;
        additionalPressure = null;
        WB.fullGC();
        phaseEnd("RELEASE");

        // 3. Let adaptive young GC move the boundary left when it needs more room.
        phaseBegin("YOUNG_GROWTH");
        // Immediate collection makes the adaptive policy request a larger
        // young generation than the reduced reservation can satisfy.
        youngPressure = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            youngPressure.add(new byte[MB]);
        }
        WB.youngGC();
        phaseEnd("YOUNG_GROWTH");

        // 4. Force old-only mode with maximum old pressure.
        phaseBegin("OLD_ONLY");
        try {
            // Together with the live 8m, this pending allocation requires the
            // full heap as old reservation. The allocation itself may fail.
            oldPressure = new byte[120 * MB];
        } catch (OutOfMemoryError expected) {
        }
        phaseEnd("OLD_ONLY");

        // 5. Remove pressure; full GC should recreate young gen.
        phaseBegin("RECREATE");
        oldPressure = null;
        youngPressure = null;
        WB.fullGC();
        phaseEnd("RECREATE");

        WB.setUintVMFlag("GCTimeRatio", 0);
        if (WB.getUintVMFlag("GCTimeRatio") != 0) {
            throw new RuntimeException("Could not disable throughput-driven young growth");
        }
        // 6. Further young GCs should converge to a stable, non-empty boundary.
        phaseBegin("STABLE");
        for (int i = 0; i < 8; i++) {
            // Avoid asking for throughput-driven growth in an empty young gen.
            Thread.sleep(200);
            WB.youngGC();
        }
        phaseEnd("STABLE");
    }

    private static void phaseBegin(String phase) {
        System.out.println("PHASE " + phase + " BEGIN");
    }

    private static void phaseEnd(String phase) {
        System.out.println("PHASE " + phase + " END");
    }
}
