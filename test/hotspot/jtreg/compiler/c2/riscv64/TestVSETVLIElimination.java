/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
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
 * @summary Test RVV vector configuration propagation through extended basic blocks
 * @library /test/lib /
 * @requires vm.compiler2.enabled & os.arch == "riscv64" & vm.cpu.features ~= ".*rvv.*"
 * @modules jdk.incubator.vector
 * @run driver compiler.c2.riscv64.TestVSETVLIElimination
 */

package compiler.c2.riscv64;

import compiler.lib.ir_framework.CompLevel;
import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.DontInline;
import compiler.lib.ir_framework.ForceCompile;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Scenario;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;

import java.util.Arrays;

public class TestVSETVLIElimination {
    private static final String KEPT = "rvv_vsetvli=kept";
    private static final String ELIDED = "rvv_vsetvli=elided";
    private static final String PREDECESSOR = "B\\d+(?: \\(B\\d+\\))? ";
    private static final String SINGLE_PRED =
            "(?m)^[0-9a-f]+\\h+B\\d+:[^\\r\\n]*<- in\\( " + PREDECESSOR + "\\)[^\\r\\n]*\\R";
    private static final String JOIN =
            "(?m)^[0-9a-f]+\\h+B\\d+:[^\\r\\n]*<- in\\( " + PREDECESSOR + "(?:" + PREDECESSOR + ")+\\)[^\\r\\n]*\\R";
    // Do not let an earlier setup in the same block satisfy a cross-block assertion.
    private static final String BEFORE_SETUP =
            "(?:(?![^\\r\\n]*(?:\\bB\\d+:|rvv_vsetvli=))[^\\r\\n]*\\R)*";
    private static final String FIRST_STORE = BEFORE_SETUP + "[^\\r\\n]*storeV [^\\r\\n]*";
    // Connectors print after executable blocks; bind their identities with a lookahead.
    private static final String EMPTY_TWO_PRED_JOIN =
            "(?m)\\A(?=[\\s\\S]*^[0-9a-f]+\\h+(?<connector>B\\d+):\\h+#\\h+out\\( (?<successor>B\\d+) \\)"
            + " <- in\\( (?<first>B\\d+) (?<second>(?!\\k<first>\\b)B\\d+) \\)[^\\r\\n]*\\R\\h+# Empty connector block\\R)"
            + "[\\s\\S]*^[0-9a-f]+\\h+\\k<successor>:[^\\r\\n]*<- in\\( "
            + "(?=[^\\r\\n]*\\b\\k<first> \\(\\k<connector>\\))"
            + "(?=[^\\r\\n]*\\b\\k<second> \\(\\k<connector>\\))[^\\r\\n]*\\R";
    private static final int SENTINEL = 0x5a5a5a5a;
    private static final int[] A = {0x12345678, -77, 903, 0x76543210};
    private static final int[] B = {37, 0x1234, -51, 99};
    private static final int[] BEFORE = new int[4];
    private static final int[] LEFT = new int[4];
    private static final int[] RIGHT = new int[4];
    private static final int[] AFTER = new int[4];
    private static final int[] SMALL = new int[2];
    private static final byte[] BYTES = {7, -1, 19, -128, 33, 127, -59, 11, 65, -31, 97, 3, -11, 55, 23, -47};
    private static final byte[] BYTE_RESULT = new byte[16];
    private static final int[] IA = {0x12345678, -77, 903, 0x76543210};
    private static final int[] IB = {37, 0x1234, -51, 99};
    private static final int[] IR = new int[4];
    private static final float[] FA = {1.25f, -2.5f, 3.75f, -4.125f};
    private static final float[] FB = {-3.5f, 2.25f, 1.125f, -0.5f};
    private static final float[] FR = new float[4];
    private static final double[] DA = {1.125, -7.25};
    private static final double[] DB = {3.75, 0.625};
    private static final double[] DR = new double[2];
    private static int calls;

    public static void main(String[] args) {
        TestFramework framework = new TestFramework()
                .addFlags("-XX:-TieredCompilation", "-XX:+UnlockDiagnosticVMOptions", "-XX:+VSETVLIElimination",
                          "--add-modules=jdk.incubator.vector", "-XX:LoopMaxUnroll=0",
                          "-XX:+UnlockExperimentalVMOptions",
                          "-XX:CompileCommand=blackhole,compiler.c2.riscv64.TestVSETVLIElimination::blackhole")
                .addScenarios(new Scenario(0, "-XX:+BlockLayoutByFrequency"),
                              new Scenario(1, "-XX:-BlockLayoutByFrequency"));
        if (Platform.isDebugBuild()) {
            framework.addFlags("-XX:+Verbose");
        }
        framework.start();
    }

    @Test
    @IR(counts = {KEPT, ">= 1", ELIDED, ">= 1", "storeV [^\\r\\n]*" + ELIDED, ">= 1"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static void testInt() {
        IntVector a = IntVector.fromArray(IntVector.SPECIES_128, IA, 0);
        IntVector b = IntVector.fromArray(IntVector.SPECIES_128, IB, 0);
        a.add(b).mul(b).lanewise(VectorOperators.XOR, a).sub(b).and(a).or(b).intoArray(IR, 0);
    }

    @Run(test = "testInt")
    public static void runInt() {
        testInt();
        for (int i = 0; i < IR.length; i++) {
            Asserts.assertEQ(IR[i], (((((IA[i] + IB[i]) * IB[i]) ^ IA[i]) - IB[i]) & IA[i]) | IB[i]);
        }
    }

    @Test
    @IR(counts = {KEPT, ">= 1", ELIDED, ">= 1", "storeV [^\\r\\n]*" + ELIDED, ">= 1"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static void testFloat() {
        FloatVector a = FloatVector.fromArray(FloatVector.SPECIES_128, FA, 0);
        FloatVector b = FloatVector.fromArray(FloatVector.SPECIES_128, FB, 0);
        a.add(b).mul(b).sub(a).intoArray(FR, 0);
    }

    @Run(test = "testFloat")
    public static void runFloat() {
        testFloat();
        for (int i = 0; i < FR.length; i++) {
            Asserts.assertEQ(FR[i], (FA[i] + FB[i]) * FB[i] - FA[i]);
        }
    }

    @Test
    @IR(counts = {KEPT, ">= 1", ELIDED, ">= 1", "storeV [^\\r\\n]*" + ELIDED, ">= 1"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static void testDouble() {
        DoubleVector a = DoubleVector.fromArray(DoubleVector.SPECIES_128, DA, 0);
        DoubleVector b = DoubleVector.fromArray(DoubleVector.SPECIES_128, DB, 0);
        a.add(b).mul(b).sub(a).intoArray(DR, 0);
    }

    @Run(test = "testDouble")
    public static void runDouble() {
        testDouble();
        for (int i = 0; i < DR.length; i++) {
            Asserts.assertEQ(DR[i], (DA[i] + DB[i]) * DB[i] - DA[i]);
        }
    }

    @Test
    @IR(counts = {SINGLE_PRED + FIRST_STORE + ELIDED, ">= 2"}, phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static void testNestedBranches(boolean first, boolean second) {
        IntVector value = IntVector.fromArray(IntVector.SPECIES_128, A, 0)
                .add(IntVector.fromArray(IntVector.SPECIES_128, B, 0));
        value.intoArray(BEFORE, 0);
        if (first) {
            value.intoArray(LEFT, 0);
            if (second) {
                value.intoArray(RIGHT, 0);
            }
        }
    }

    @Run(test = "testNestedBranches")
    public static void runNestedBranches() {
        for (boolean first : new boolean[] {false, true}) {
            for (boolean second : new boolean[] {false, true}) {
                Arrays.fill(LEFT, SENTINEL);
                Arrays.fill(RIGHT, SENTINEL);
                testNestedBranches(first, second);
                for (int i = 0; i < A.length; i++) {
                    int sum = A[i] + B[i];
                    Asserts.assertEQ(BEFORE[i], sum);
                    Asserts.assertEQ(LEFT[i], first ? sum : SENTINEL);
                    Asserts.assertEQ(RIGHT[i], first && second ? sum : SENTINEL);
                }
            }
        }
    }

    @Test
    @IR(counts = {JOIN + FIRST_STORE + KEPT, ">= 1"},
        failOn = {JOIN + FIRST_STORE + ELIDED}, phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static void testMatchingJoin(boolean left) {
        IntVector value = IntVector.fromArray(IntVector.SPECIES_128, A, 0);
        value.intoArray(BEFORE, 0);
        if (left) {
            value.intoArray(LEFT, 0);
        } else {
            value.intoArray(RIGHT, 0);
        }
        value.intoArray(AFTER, 0);
    }

    @Run(test = "testMatchingJoin")
    public static void runMatchingJoin() {
        for (boolean left : new boolean[] {false, true}) {
            Arrays.fill(LEFT, SENTINEL);
            Arrays.fill(RIGHT, SENTINEL);
            testMatchingJoin(left);
            for (int i = 0; i < A.length; i++) {
                Asserts.assertEQ(BEFORE[i], A[i]);
                Asserts.assertEQ(LEFT[i], left ? A[i] : SENTINEL);
                Asserts.assertEQ(RIGHT[i], left ? SENTINEL : A[i]);
                Asserts.assertEQ(AFTER[i], A[i]);
            }
        }
    }

    // The zero-code blackhole preserves the inner join without making its block nonempty.
    public static void blackhole() {
    }

    @Test
    @IR(counts = {EMPTY_TWO_PRED_JOIN + FIRST_STORE + KEPT, "1"},
        failOn = {EMPTY_TWO_PRED_JOIN + FIRST_STORE + ELIDED}, phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static void testEmptyTwoPredJoin(boolean outer, boolean small) {
        IntVector value = IntVector.fromArray(IntVector.SPECIES_128, A, 0);
        value.intoArray(BEFORE, 0);
        if (outer) {
            if (small) {
                IntVector.fromArray(IntVector.SPECIES_64, B, 0).intoArray(RIGHT, 0);
            } else {
                value.intoArray(RIGHT, 0);
            }
            blackhole();
        }
        value.intoArray(AFTER, 0);
    }

    @Run(test = "testEmptyTwoPredJoin")
    public static void runEmptyTwoPredJoin() {
        for (boolean outer : new boolean[] {false, true}) {
            for (boolean small : new boolean[] {false, true}) {
                Arrays.fill(BEFORE, SENTINEL);
                Arrays.fill(RIGHT, SENTINEL);
                Arrays.fill(AFTER, SENTINEL);
                testEmptyTwoPredJoin(outer, small);
                for (int i = 0; i < A.length; i++) {
                    int expected = SENTINEL;
                    if (outer) {
                        expected = small ? (i < SMALL.length ? B[i] : SENTINEL) : A[i];
                    }
                    Asserts.assertEQ(BEFORE[i], A[i]);
                    Asserts.assertEQ(RIGHT[i], expected);
                    Asserts.assertEQ(AFTER[i], A[i]);
                }
            }
        }
    }

    @Test
    @IR(counts = {SINGLE_PRED + FIRST_STORE + ELIDED, ">= 1", JOIN + FIRST_STORE + KEPT, ">= 1"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static void testSiblingSEW(boolean bytes) {
        IntVector value = IntVector.fromArray(IntVector.SPECIES_128, A, 0);
        value.intoArray(BEFORE, 0);
        if (bytes) {
            ByteVector.fromArray(ByteVector.SPECIES_128, BYTES, 0).intoArray(BYTE_RESULT, 0);
        } else {
            value.intoArray(RIGHT, 0);
        }
        value.intoArray(AFTER, 0);
    }

    @Run(test = "testSiblingSEW")
    public static void runSiblingSEW() {
        for (boolean bytes : new boolean[] {false, true}) {
            Arrays.fill(BYTE_RESULT, (byte) SENTINEL);
            Arrays.fill(RIGHT, SENTINEL);
            testSiblingSEW(bytes);
            for (int i = 0; i < A.length; i++) {
                Asserts.assertEQ(BEFORE[i], A[i]);
                Asserts.assertEQ(RIGHT[i], bytes ? SENTINEL : A[i]);
                Asserts.assertEQ(AFTER[i], A[i]);
            }
            for (int i = 0; i < BYTES.length; i++) {
                Asserts.assertEQ(BYTE_RESULT[i], bytes ? BYTES[i] : (byte) SENTINEL);
            }
        }
    }

    @Test
    @IR(counts = {SINGLE_PRED + FIRST_STORE + ELIDED, ">= 1", JOIN + FIRST_STORE + KEPT, ">= 1"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static void testSiblingVL(boolean small) {
        IntVector value = IntVector.fromArray(IntVector.SPECIES_128, A, 0);
        value.intoArray(BEFORE, 0);
        if (small) {
            IntVector.fromArray(IntVector.SPECIES_64, B, 0).intoArray(SMALL, 0);
        } else {
            value.intoArray(RIGHT, 0);
        }
        value.intoArray(AFTER, 0);
    }

    @Run(test = "testSiblingVL")
    public static void runSiblingVL() {
        for (boolean small : new boolean[] {false, true}) {
            Arrays.fill(SMALL, SENTINEL);
            Arrays.fill(RIGHT, SENTINEL);
            testSiblingVL(small);
            for (int i = 0; i < A.length; i++) {
                Asserts.assertEQ(BEFORE[i], A[i]);
                Asserts.assertEQ(RIGHT[i], small ? SENTINEL : A[i]);
                Asserts.assertEQ(AFTER[i], A[i]);
            }
            for (int i = 0; i < SMALL.length; i++) {
                Asserts.assertEQ(SMALL[i], small ? B[i] : SENTINEL);
            }
        }
    }

    @DontInline
    @ForceCompile(CompLevel.C2)
    public static void clobber() {
        calls++;
        ByteVector.fromArray(ByteVector.SPECIES_128, BYTES, 0).add((byte) 3).intoArray(BYTE_RESULT, 0);
        for (int i = 0; i < B.length; i++) {
            LEFT[i] = B[i] + calls;
        }
    }

    @Test
    @IR(counts = {"CALL,[^\\r\\n]*::clobber", "1",
                  "# Block is sole successor of call\\R" + BEFORE_SETUP + "[^\\r\\n]*loadV [^\\r\\n]*" + KEPT, ">= 1"},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static void testCall(boolean call) {
        IntVector.fromArray(IntVector.SPECIES_128, A, 0).intoArray(BEFORE, 0);
        if (call) {
            clobber();
            IntVector.fromArray(IntVector.SPECIES_128, LEFT, 0).intoArray(AFTER, 0);
        }
    }

    @Run(test = "testCall")
    public static void runCall() {
        for (boolean call : new boolean[] {false, true}) {
            Arrays.fill(AFTER, SENTINEL);
            int previousCalls = calls;
            testCall(call);
            Asserts.assertEQ(calls, previousCalls + (call ? 1 : 0));
            for (int i = 0; i < A.length; i++) {
                Asserts.assertEQ(BEFORE[i], A[i]);
                Asserts.assertEQ(AFTER[i], call ? B[i] + calls : SENTINEL);
            }
        }
    }

    @Test
    @IR(counts = {JOIN + BEFORE_SETUP + "[^\\r\\n]*vadd [^\\r\\n]*" + KEPT, ">= 1"},
        failOn = {JOIN + BEFORE_SETUP + "[^\\r\\n]*vadd [^\\r\\n]*" + ELIDED},
        phase = CompilePhase.PRINT_OPTO_ASSEMBLY)
    public static void testLoop(int iterations) {
        IntVector value = IntVector.fromArray(IntVector.SPECIES_128, A, 0);
        IntVector increment = IntVector.fromArray(IntVector.SPECIES_128, B, 0);
        value.intoArray(BEFORE, 0);
        for (int i = 0; i < iterations; i++) {
            value = value.add(increment);
            value.intoArray(AFTER, 0);
        }
    }

    @Run(test = "testLoop")
    public static void runLoop() {
        for (int iterations : new int[] {0, 1, 2, 5, 17}) {
            Arrays.fill(AFTER, SENTINEL);
            testLoop(iterations);
            for (int i = 0; i < A.length; i++) {
                Asserts.assertEQ(BEFORE[i], A[i]);
                Asserts.assertEQ(AFTER[i], iterations == 0 ? SENTINEL : A[i] + iterations * B[i]);
            }
        }
    }
}
