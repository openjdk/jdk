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

/*
 * @test
 * @bug 8391181
 * @summary Tests that memory state is killed appropriately while parsing.
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 * @requires vm.compiler1.enabled & vm.flagless
 * @run driver ${test.main.class}
 */

package compiler.c1;

import java.util.Set;
import jdk.internal.misc.Unsafe;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

public class TestMemoryEffects {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    public static void main(String[] args) {
        TestFramework tf = new TestFramework();
        tf.setDefaultWarmup(0); // All tests need -Xcomp.
        tf.addFlags("--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
        tf.addCrossProductScenarios(Set.of("", "--enable-preview", "-XX:+IgnoreUnrecognizedVMOptions -XX:+StressLoopInvariantCodeMotion"));
        tf.start();
    }

    // Getstatic and putstatic tests
    static class Victim {
        int field;
    }
    public static final Victim VICTIM = new Victim();

    @Run(test = { "testGetstatic", "testPutstatic" })
    static void runGetPutStatic(RunInfo info) {
        info.getTestNames()
            .stream()
            .forEach((String testName) -> {
            Integer res = 0;
            try {
                res = (Integer)info.getTest(testName).invoke(null);
            } catch (Exception e) { /* make javac happy */ }
            Asserts.assertEQ(42, res, "wrong result for " + testName + ":");
        });
    }

    @Test(compLevel = CompLevel.C1_SIMPLE)
    static int testGetstatic() {
        VICTIM.field = 666;
        int result = SideEffectingGetstatic.bait; // Triggers (*) by loading the class.
        result += VICTIM.field;
        return result;
    }

    @Test(compLevel = CompLevel.C1_SIMPLE)
    static int testPutstatic() {
        VICTIM.field = 1234;
        SideEffectingPutstatic.bait = 0xabc; // Triggers (**) by loading the class.
        return VICTIM.field;
    }

    // Volatile load test
    static class BoxWithVolatiles {
        int value;
        volatile int sync;
        volatile boolean start;
        volatile int next;
        volatile boolean stop;
    }

    static record VolatileTestResults(int before, int after, int value, int sink) {}

    @Run(test = { "testVolatileLoad", "testUnsafeVolatileLoad" })
    static void runVolatileLoad(RunInfo info) {
        info.getTestNames()
            .stream()
            .forEach((String testName) -> {
            BoxWithVolatiles box = new BoxWithVolatiles();
            Thread writer = new Thread(() -> {
                int counter = 1;
                box.start = true;
                int last = box.next;
                while (!box.stop) {
                    last = box.next;
                    box.sync = counter++;
                    box.value = 42;
                    // Make the non-volatile write above visible with a release-acquire handshake by
                    // writing and reading the same volatile field.
                    box.sync = counter++;
                    while (!box.stop && last == box.next) {
                        Thread.onSpinWait();
                    }
                }
            });

            writer.start();
            while (!box.start) {
                Thread.onSpinWait();
            }

            // Count number of successful handshakes where the value is wrong.
            // A conservative estimate for the test failing due to reordering
            // is 9/20. With 200 handshakes the probability of the test randomly
            // failing is about 4.39E-200.
            final int maxHandshakes = 200;
            int successfulHandshakes = 0;
            // Limit the number of attempts to check if the test is still working.
            final int maxAttempts = 50_000;
            int attempts = 1;
            try {
                for (; attempts <= maxAttempts && successfulHandshakes < maxHandshakes; attempts++) {
                    VolatileTestResults results = null;
                    try {
                        results = (VolatileTestResults)info.getTest(testName).invoke(null, box, 0xcafe);
                    } catch (Exception e) { /* make javac happy */ }
                    // The release-acquire handshake worked if before and after
                    // are different by 2 and both even.
                    final int steps = results.after - results.before;
                    if (Integer.compareUnsigned(steps, 2) == 0 &&
                        (results.before & 1) == 0 && (results.after & 1) == 0) {
                        if (results.value != 42) {
                            successfulHandshakes++;
                            continue;
                        }
                        // If we reach here, the test has passed.
                        box.stop = true;
                        writer.join();
                        return;
                    }
                }
                box.stop = true;
                writer.join();
            } catch (InterruptedException e) { /* make javac happy */ }
            if (attempts >= 50_000 && successfulHandshakes < 200) {
                throw new RuntimeException("The handshake for testVolatileLoad did not work.");
            } else {
                throw new RuntimeException("Wrong execution for " + testName);
            }
        });
    }

    @Test(compLevel = CompLevel.C1_SIMPLE)
    static VolatileTestResults testVolatileLoad(BoxWithVolatiles box, int delay) {
        // Start another round in the writer.
        box.next++;
        box.value = 0; // Establish fact in C1 MemoryBuffer.
        // The test depends on the write above not getting reordered below the read below. C1 emits instructions
        // inside a block in source order, so this works. C2, however, moves anything not nailed down, which is
        // why this test is flagless and only run on C1.
        int before = box.sync; // This volatile read must kill the memory state.

        // This test method must be a single block as C1 kills its memory state at every block boundary.
        // To make the write of the writer thread visible, we delay with a number of dependent computations.
        delay = delay * 1461234786 + 1;
        delay = delay / 234 + 1;
        delay = delay * 1461234786 + 2;
        delay = delay / 234 + 2;
        delay = delay * 1461234786 + 3;
        delay = delay / 234 + 3;
        delay = delay * 1461234786 + 4;
        delay = delay / 234 + 4;
        delay = delay * 1461234786 + 5;
        delay = delay / 234 + 5;
        delay = delay * 1461234786 + 6;
        delay = delay / 234 + 6;
        delay = delay * 1461234786 + 7;
        delay = delay / 234 + 7;
        delay = delay * 1461234786 + 8;
        delay = delay / 234 + 8;
        delay = delay * 1461234786 + 9;
        delay = delay / 234 + 9;
        delay = delay * 1461234786 + 10;
        delay = delay / 234 + 10;
        delay = delay * 1461234786 + 11;
        delay = delay / 234 + 11;
        delay = delay * 1461234786 + 12;
        delay = delay / 234 + 12;
        delay = delay * 1461234786 + 13;
        delay = delay / 234 + 13;
        delay = delay * 1461234786 + 14;
        delay = delay / 234 + 14;
        delay = delay * 1461234786 + 15;
        delay = delay / 234 + 15;
        delay = delay * 1461234786 + 16;
        delay = delay / 234 + 16;
        delay = delay * 1461234786 + 17;
        delay = delay / 234 + 17;
        delay = delay * 1461234786 + 18;
        delay = delay / 234 + 18;
        delay = delay * 1461234786 + 19;
        delay = delay / 234 + 19;
        delay = delay * 1461234786 + 20;
        delay = delay / 234 + 20;
        delay = delay * 1461234786 + 21;
        delay = delay / 234 + 21;
        delay = delay * 1461234786 + 22;
        delay = delay / 234 + 22;
        delay = delay * 1461234786 + 23;
        delay = delay / 234 + 23;
        delay = delay * 1461234786 + 24;
        delay = delay / 234 + 24;
        delay = delay * 1461234786 + 25;
        delay = delay / 234 + 25;
        delay = delay * 1461234786 + 26;
        delay = delay / 234 + 26;
        delay = delay * 1461234786 + 27;
        delay = delay / 234 + 27;
        delay = delay * 1461234786 + 28;
        delay = delay / 234 + 28;
        delay = delay * 1461234786 + 29;
        delay = delay / 234 + 29;
        delay = delay * 1461234786 + 30;
        delay = delay / 234 + 30;
        delay = delay * 1461234786 + 31;
        delay = delay / 234 + 31;
        delay = delay * 1461234786 + 32;
        delay = delay / 234 + 32;
        delay = delay * 1461234786 + 33;
        delay = delay / 234 + 33;
        delay = delay * 1461234786 + 34;
        delay = delay / 234 + 34;
        delay = delay * 1461234786 + 35;
        delay = delay / 234 + 35;
        delay = delay * 1461234786 + 36;
        delay = delay / 234 + 36;
        delay = delay * 1461234786 + 37;
        delay = delay / 234 + 37;
        delay = delay * 1461234786 + 38;
        delay = delay / 234 + 38;
        delay = delay * 1461234786 + 39;
        delay = delay / 234 + 39;
        delay = delay * 1461234786 + 40;
        delay = delay / 234 + 40;
        delay = delay * 1461234786 + 41;
        delay = delay / 234 + 41;
        delay = delay * 1461234786 + 42;
        delay = delay / 234 + 42;
        delay = delay * 1461234786 + 43;
        delay = delay / 234 + 43;
        delay = delay * 1461234786 + 44;
        delay = delay / 234 + 44;
        delay = delay * 1461234786 + 45;
        delay = delay / 234 + 45;
        delay = delay * 1461234786 + 46;
        delay = delay / 234 + 46;
        delay = delay * 1461234786 + 47;
        delay = delay / 234 + 47;
        delay = delay * 1461234786 + 48;
        delay = delay / 234 + 48;
        delay = delay * 1461234786 + 49;
        delay = delay / 234 + 49;
        delay = delay * 1461234786 + 50;
        delay = delay / 234 + 50;
        delay = delay * 1461234786 + 51;
        delay = delay / 234 + 51;
        delay = delay * 1461234786 + 52;
        delay = delay / 234 + 52;
        delay = delay * 1461234786 + 53;
        delay = delay / 234 + 53;
        delay = delay * 1461234786 + 54;
        delay = delay / 234 + 54;
        delay = delay * 1461234786 + 55;
        delay = delay / 234 + 55;
        delay = delay * 1461234786 + 56;
        delay = delay / 234 + 56;
        delay = delay * 1461234786 + 57;
        delay = delay / 234 + 57;
        delay = delay * 1461234786 + 58;
        delay = delay / 234 + 58;
        delay = delay * 1461234786 + 59;
        delay = delay / 234 + 59;
        delay = delay * 1461234786 + 60;
        delay = delay / 234 + 60;
        delay = delay * 1461234786 + 61;
        delay = delay / 234 + 61;
        delay = delay * 1461234786 + 62;
        delay = delay / 234 + 62;
        delay = delay * 1461234786 + 63;
        delay = delay / 234 + 63;
        delay = delay * 1461234786 + 64;
        delay = delay / 234 + 64;
        delay = delay * 1461234786 + 65;
        delay = delay / 234 + 65;
        delay = delay * 1461234786 + 66;
        delay = delay / 234 + 66;
        delay = delay * 1461234786 + 67;
        delay = delay / 234 + 67;
        delay = delay * 1461234786 + 68;
        delay = delay / 234 + 68;
        delay = delay * 1461234786 + 69;
        delay = delay / 234 + 69;
        delay = delay * 1461234786 + 70;
        delay = delay / 234 + 70;
        delay = delay * 1461234786 + 71;
        delay = delay / 234 + 71;
        delay = delay * 1461234786 + 72;
        delay = delay / 234 + 72;
        delay = delay * 1461234786 + 73;
        delay = delay / 234 + 73;
        delay = delay * 1461234786 + 74;
        delay = delay / 234 + 74;
        delay = delay * 1461234786 + 75;
        delay = delay / 234 + 75;
        delay = delay * 1461234786 + 76;
        delay = delay / 234 + 76;
        delay = delay * 1461234786 + 77;
        delay = delay / 234 + 77;
        delay = delay * 1461234786 + 78;
        delay = delay / 234 + 78;
        delay = delay * 1461234786 + 79;
        delay = delay / 234 + 79;
        delay = delay * 1461234786 + 80;
        delay = delay / 234 + 80;
        delay = delay * 1461234786 + 81;
        delay = delay / 234 + 81;
        delay = delay * 1461234786 + 82;
        delay = delay / 234 + 82;
        delay = delay * 1461234786 + 83;
        delay = delay / 234 + 83;
        delay = delay * 1461234786 + 84;
        delay = delay / 234 + 84;
        delay = delay * 1461234786 + 85;
        delay = delay / 234 + 85;
        delay = delay * 1461234786 + 86;
        delay = delay / 234 + 86;
        delay = delay * 1461234786 + 87;
        delay = delay / 234 + 87;
        delay = delay * 1461234786 + 88;
        delay = delay / 234 + 88;
        delay = delay * 1461234786 + 89;
        delay = delay / 234 + 89;
        delay = delay * 1461234786 + 90;
        delay = delay / 234 + 90;
        delay = delay * 1461234786 + 91;
        delay = delay / 234 + 91;
        delay = delay * 1461234786 + 92;
        delay = delay / 234 + 92;
        delay = delay * 1461234786 + 93;
        delay = delay / 234 + 93;
        delay = delay * 1461234786 + 94;
        delay = delay / 234 + 94;
        delay = delay * 1461234786 + 95;
        delay = delay / 234 + 95;
        delay = delay * 1461234786 + 96;
        delay = delay / 234 + 96;
        delay = delay * 1461234786 + 97;
        delay = delay / 234 + 97;
        delay = delay * 1461234786 + 98;
        delay = delay / 234 + 98;
        delay = delay * 1461234786 + 99;
        delay = delay / 234 + 99;
        delay = delay * 1461234786 + 100;
        delay = delay / 234 + 100;
        // Enough dilly-dallying, let's get down to business.

        // The following volatile load, which has acquire semantics, must make the write to box.value
        // from the writer thread visible.
        int after = box.sync;
        // If C1 does not kill the memory state for volatile loads, this might be folded to 0 due to
        // the first line of this test.
        int result = box.value;

        return new VolatileTestResults(before, after, result, delay);
    }

    @Test(compLevel = CompLevel.C1_SIMPLE)
    static VolatileTestResults testUnsafeVolatileLoad(BoxWithVolatiles box, int delay) throws NoSuchFieldException {
        long syncOffset = UNSAFE.objectFieldOffset(BoxWithVolatiles.class.getDeclaredField("sync"));

        // Start another round in the writer.
        box.next++;
        box.value = 0; // Establish fact in C1 MemoryBuffer.
        // The test depends on the write above not getting reordered below the read below. C1 emits instructions
        // inside a block in source order, so this works. C2, however, moves anything not nailed down, which is
        // why this test is flagless and only run on C1.
        int before = UNSAFE.getIntVolatile(box, syncOffset); // This volatile read must kill the memory state.

        // This test method must be a single block as C1 kills its memory state at every block boundary.
        // To make the write of the writer thread visible, we delay with a number of dependent computations.
        delay = delay * 1461234786 + 1;
        delay = delay / 234 + 1;
        delay = delay * 1461234786 + 2;
        delay = delay / 234 + 2;
        delay = delay * 1461234786 + 3;
        delay = delay / 234 + 3;
        delay = delay * 1461234786 + 4;
        delay = delay / 234 + 4;
        delay = delay * 1461234786 + 5;
        delay = delay / 234 + 5;
        delay = delay * 1461234786 + 6;
        delay = delay / 234 + 6;
        delay = delay * 1461234786 + 7;
        delay = delay / 234 + 7;
        delay = delay * 1461234786 + 8;
        delay = delay / 234 + 8;
        delay = delay * 1461234786 + 9;
        delay = delay / 234 + 9;
        delay = delay * 1461234786 + 10;
        delay = delay / 234 + 10;
        delay = delay * 1461234786 + 11;
        delay = delay / 234 + 11;
        delay = delay * 1461234786 + 12;
        delay = delay / 234 + 12;
        delay = delay * 1461234786 + 13;
        delay = delay / 234 + 13;
        delay = delay * 1461234786 + 14;
        delay = delay / 234 + 14;
        delay = delay * 1461234786 + 15;
        delay = delay / 234 + 15;
        delay = delay * 1461234786 + 16;
        delay = delay / 234 + 16;
        delay = delay * 1461234786 + 17;
        delay = delay / 234 + 17;
        delay = delay * 1461234786 + 18;
        delay = delay / 234 + 18;
        delay = delay * 1461234786 + 19;
        delay = delay / 234 + 19;
        delay = delay * 1461234786 + 20;
        delay = delay / 234 + 20;
        delay = delay * 1461234786 + 21;
        delay = delay / 234 + 21;
        delay = delay * 1461234786 + 22;
        delay = delay / 234 + 22;
        delay = delay * 1461234786 + 23;
        delay = delay / 234 + 23;
        delay = delay * 1461234786 + 24;
        delay = delay / 234 + 24;
        delay = delay * 1461234786 + 25;
        delay = delay / 234 + 25;
        delay = delay * 1461234786 + 26;
        delay = delay / 234 + 26;
        delay = delay * 1461234786 + 27;
        delay = delay / 234 + 27;
        delay = delay * 1461234786 + 28;
        delay = delay / 234 + 28;
        delay = delay * 1461234786 + 29;
        delay = delay / 234 + 29;
        delay = delay * 1461234786 + 30;
        delay = delay / 234 + 30;
        delay = delay * 1461234786 + 31;
        delay = delay / 234 + 31;
        delay = delay * 1461234786 + 32;
        delay = delay / 234 + 32;
        delay = delay * 1461234786 + 33;
        delay = delay / 234 + 33;
        delay = delay * 1461234786 + 34;
        delay = delay / 234 + 34;
        delay = delay * 1461234786 + 35;
        delay = delay / 234 + 35;
        delay = delay * 1461234786 + 36;
        delay = delay / 234 + 36;
        delay = delay * 1461234786 + 37;
        delay = delay / 234 + 37;
        delay = delay * 1461234786 + 38;
        delay = delay / 234 + 38;
        delay = delay * 1461234786 + 39;
        delay = delay / 234 + 39;
        delay = delay * 1461234786 + 40;
        delay = delay / 234 + 40;
        delay = delay * 1461234786 + 41;
        delay = delay / 234 + 41;
        delay = delay * 1461234786 + 42;
        delay = delay / 234 + 42;
        delay = delay * 1461234786 + 43;
        delay = delay / 234 + 43;
        delay = delay * 1461234786 + 44;
        delay = delay / 234 + 44;
        delay = delay * 1461234786 + 45;
        delay = delay / 234 + 45;
        delay = delay * 1461234786 + 46;
        delay = delay / 234 + 46;
        delay = delay * 1461234786 + 47;
        delay = delay / 234 + 47;
        delay = delay * 1461234786 + 48;
        delay = delay / 234 + 48;
        delay = delay * 1461234786 + 49;
        delay = delay / 234 + 49;
        delay = delay * 1461234786 + 50;
        delay = delay / 234 + 50;
        delay = delay * 1461234786 + 51;
        delay = delay / 234 + 51;
        delay = delay * 1461234786 + 52;
        delay = delay / 234 + 52;
        delay = delay * 1461234786 + 53;
        delay = delay / 234 + 53;
        delay = delay * 1461234786 + 54;
        delay = delay / 234 + 54;
        delay = delay * 1461234786 + 55;
        delay = delay / 234 + 55;
        delay = delay * 1461234786 + 56;
        delay = delay / 234 + 56;
        delay = delay * 1461234786 + 57;
        delay = delay / 234 + 57;
        delay = delay * 1461234786 + 58;
        delay = delay / 234 + 58;
        delay = delay * 1461234786 + 59;
        delay = delay / 234 + 59;
        delay = delay * 1461234786 + 60;
        delay = delay / 234 + 60;
        delay = delay * 1461234786 + 61;
        delay = delay / 234 + 61;
        delay = delay * 1461234786 + 62;
        delay = delay / 234 + 62;
        delay = delay * 1461234786 + 63;
        delay = delay / 234 + 63;
        delay = delay * 1461234786 + 64;
        delay = delay / 234 + 64;
        delay = delay * 1461234786 + 65;
        delay = delay / 234 + 65;
        delay = delay * 1461234786 + 66;
        delay = delay / 234 + 66;
        delay = delay * 1461234786 + 67;
        delay = delay / 234 + 67;
        delay = delay * 1461234786 + 68;
        delay = delay / 234 + 68;
        delay = delay * 1461234786 + 69;
        delay = delay / 234 + 69;
        delay = delay * 1461234786 + 70;
        delay = delay / 234 + 70;
        delay = delay * 1461234786 + 71;
        delay = delay / 234 + 71;
        delay = delay * 1461234786 + 72;
        delay = delay / 234 + 72;
        delay = delay * 1461234786 + 73;
        delay = delay / 234 + 73;
        delay = delay * 1461234786 + 74;
        delay = delay / 234 + 74;
        delay = delay * 1461234786 + 75;
        delay = delay / 234 + 75;
        delay = delay * 1461234786 + 76;
        delay = delay / 234 + 76;
        delay = delay * 1461234786 + 77;
        delay = delay / 234 + 77;
        delay = delay * 1461234786 + 78;
        delay = delay / 234 + 78;
        delay = delay * 1461234786 + 79;
        delay = delay / 234 + 79;
        delay = delay * 1461234786 + 80;
        delay = delay / 234 + 80;
        delay = delay * 1461234786 + 81;
        delay = delay / 234 + 81;
        delay = delay * 1461234786 + 82;
        delay = delay / 234 + 82;
        delay = delay * 1461234786 + 83;
        delay = delay / 234 + 83;
        delay = delay * 1461234786 + 84;
        delay = delay / 234 + 84;
        delay = delay * 1461234786 + 85;
        delay = delay / 234 + 85;
        delay = delay * 1461234786 + 86;
        delay = delay / 234 + 86;
        delay = delay * 1461234786 + 87;
        delay = delay / 234 + 87;
        delay = delay * 1461234786 + 88;
        delay = delay / 234 + 88;
        delay = delay * 1461234786 + 89;
        delay = delay / 234 + 89;
        delay = delay * 1461234786 + 90;
        delay = delay / 234 + 90;
        delay = delay * 1461234786 + 91;
        delay = delay / 234 + 91;
        delay = delay * 1461234786 + 92;
        delay = delay / 234 + 92;
        delay = delay * 1461234786 + 93;
        delay = delay / 234 + 93;
        delay = delay * 1461234786 + 94;
        delay = delay / 234 + 94;
        delay = delay * 1461234786 + 95;
        delay = delay / 234 + 95;
        delay = delay * 1461234786 + 96;
        delay = delay / 234 + 96;
        delay = delay * 1461234786 + 97;
        delay = delay / 234 + 97;
        delay = delay * 1461234786 + 98;
        delay = delay / 234 + 98;
        delay = delay * 1461234786 + 99;
        delay = delay / 234 + 99;
        delay = delay * 1461234786 + 100;
        delay = delay / 234 + 100;
        // Enough dilly-dallying, let's get down to business.

        // The following volatile load, which has acquire semantics, must make the write to box.value
        // from the writer thread visible.
        int after = UNSAFE.getIntVolatile(box, syncOffset);
        // If C1 does not kill the memory state for volatile loads, this might be folded to 0 due to
        // the first line of this test.
        int result = box.value;

        return new VolatileTestResults(before, after, result, delay);
    }
}

class SideEffectingGetstatic {
    public static int bait = 0xcafe;
    static {
        TestMemoryEffects.VICTIM.field = 42 - SideEffectingGetstatic.bait; // (*)
    }
}

class SideEffectingPutstatic {
    public static int bait = 0xbaba;
    static {
        TestMemoryEffects.VICTIM.field = 42; // (**)
    }
}
