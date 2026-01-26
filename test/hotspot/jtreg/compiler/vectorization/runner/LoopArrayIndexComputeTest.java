/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Vectorization test on loop array index computation
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.LoopArrayIndexComputeTest nAV_ySAC
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.LoopArrayIndexComputeTest yAV_ySAC
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.LoopArrayIndexComputeTest nAV_nSAC
 *
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.LoopArrayIndexComputeTest yAV_nSAC
 *
 * @requires (os.simpleArch == "x64") | (os.simpleArch == "aarch64")
 * @requires vm.compiler2.enabled
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

import java.util.Random;

public class LoopArrayIndexComputeTest extends VectorizationTestRunner {

    // We must pass the flags directly to the test-VM, and not the driver vm in the @run above.
    @Override
    protected String[] testVMFlags(String[] args) {
        return switch (args[0]) {
            case "nAV_ySAC" -> new String[]{"-XX:-AlignVector", "-XX:+UseAutoVectorizationSpeculativeAliasingChecks"};
            case "yAV_ySAC" -> new String[]{"-XX:+AlignVector", "-XX:+UseAutoVectorizationSpeculativeAliasingChecks"};
            case "nAV_nSAC" -> new String[]{"-XX:-AlignVector", "-XX:-UseAutoVectorizationSpeculativeAliasingChecks"};
            case "yAV_nSAC" -> new String[]{"-XX:+AlignVector", "-XX:-UseAutoVectorizationSpeculativeAliasingChecks"};
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        };
    }

    private static final int SIZE = 6543;

    private int[] ints;
    private short[] shorts;
    private char[] chars;
    private byte[] bytes;
    private boolean[] booleans;

    private int inv1;
    private int inv2;

    public LoopArrayIndexComputeTest() {
        ints = new int[SIZE];
        shorts = new short[SIZE];
        chars = new char[SIZE];
        bytes = new byte[SIZE];
        booleans = new boolean[SIZE];
        for (int i = 0; i < SIZE; i++) {
            ints[i] = 499 * i;
            shorts[i] = (short) (-13 * i + 5);
            chars[i] = (char) (i << 3);
            bytes[i] = (byte) (i >> 2 + 3);
            booleans[i] = (i % 5 == 0);
        }
        Random ran = new Random(10);
        inv1 = Math.abs(ran.nextInt() % 10) + 1;
        inv2 = Math.abs(ran.nextInt() % 10) + 1;
    }

    // ---------------- Linear Indexes ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.ADD_VI, ">0"})
    public int[] indexPlusConstant() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE / 2; i++) {
            res[i + 1] = ints[i + 1] + 999;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.MUL_VI, ">0"})
    public int[] indexMinusConstant() {
        int[] res = new int[SIZE];
        for (int i = SIZE / 2; i < SIZE; i++) {
            res[i - 49] = ints[i - 49] * i;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true"},
        counts = {IRNode.MUL_VI, ">0"})
    public int[] indexPlusInvariant() {
        int[] res = new int[SIZE];
        System.arraycopy(ints, 0, res, 0, SIZE);
        for (int i = 0; i < SIZE / 4; i++) {
            res[i + inv1] *= ints[i + inv1];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.MUL_VI, ">0"})
    public int[] indexMinusInvariant() {
        int[] res = new int[SIZE];
        System.arraycopy(ints, 0, res, 0, SIZE);
        for (int i = SIZE / 3; i < SIZE / 2; i++) {
            res[i - inv2] *= (ints[i - inv2] + (i >> 2));
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true"},
        counts = {IRNode.MUL_VI, ">0"})
    public int[] indexWithInvariantAndConstant() {
        int[] res = new int[SIZE];
        System.arraycopy(ints, 0, res, 0, SIZE);
        for (int i = 10; i < SIZE / 4; i++) {
            res[i + inv1 - 1] *= (ints[i + inv1 - 1] + 1);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.SUB_VI, ">0"})
    public int[] indexWithTwoInvariants() {
        int[] res = new int[SIZE];
        System.arraycopy(ints, 0, res, 0, SIZE);
        for (int i = 10; i < SIZE / 4; i++) {
            res[i + inv1 + inv2] -= ints[i + inv1 + inv2];
        }
        return res;
    }

    @Test
    // No true dependency in read-forward case.
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"AlignVector", "false"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] indexWithDifferentConstantsPos() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE / 4; i++) {
            res[i] = ints[i + 1];
        }
        return res;
    }

    @Test
    // Note that this case cannot be vectorized due to data dependence.
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"})
     // Speculative aliasing check -> never fails -> only predicate, no multiversioning.
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true", "AlignVector", "false"},
        phase = CompilePhase.PRINT_IDEAL,
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_I, ">0", // full vectorization
                  ".*multiversion.*", "= 0"})
    // JDK-8354303: could we prove statically that there is no aliasing?
    public int[] indexWithDifferentConstantsNeg() {
        int[] res = new int[SIZE];
        for (int i = 1; i < SIZE / 4; i++) {
            res[i] = ints[i - 1];
        }
        return res;
    }

    @Test
    // Note that this case cannot be vectorized due to data dependence.
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"})
    // Speculative aliasing check -> never fails -> only predicate, no multiversioning.
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true", "AlignVector", "false"},
        phase = CompilePhase.PRINT_IDEAL,
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_I, ">0", // full vectorization
                  ".*multiversion.*", "= 0"})
    // JDK-8354303: could we prove statically that there is no aliasing?
    public int[] indexWithDifferentInvariants() {
        int[] res = new int[SIZE];
        for (int i = SIZE / 4; i < SIZE / 2; i++) {
            res[i + inv1] = ints[i - inv2];
        }
        return res;
    }

    @Test
    public int indexWithDifferentConstantsLoadOnly() {
        int res1 = 0;
        int res2 = 0;
        for (int i = 0; i < SIZE / 4; i++) {
            res1 += ints[i + 2];
            res2 += ints[i + 15];
        }
        return res1 * res2;
    }

    @Test
    public int indexWithDifferentInvariantsLoadOnly() {
        int res1 = 0;
        int res2 = 0;
        for (int i = SIZE / 4; i < SIZE / 2; i++) {
            res1 += ints[i + inv1];
            res2 += ints[i - inv2];
        }
        return res1 * res2;
    }

    @Test
    public int[] scaledIndex() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE / 3; i++) {
            res[2 * i] = ints[2 * i];
        }
        return res;
    }

    @Test
    public int[] scaledIndexWithConstantOffset() {
        int[] res = new int[SIZE];
        System.arraycopy(ints, 0, res, 0, SIZE);
        for (int i = 0; i < SIZE / 4; i++) {
            res[2 * i + 3] *= ints[2 * i + 3];
        }
        return res;
    }

    @Test
    public int[] scaledIndexWithInvariantOffset() {
        int[] res = new int[SIZE];
        System.arraycopy(ints, 0, res, 0, SIZE);
        for (int i = 0; i < SIZE / 4; i++) {
            res[2 * i + inv1] *= ints[2 * i + inv1];
        }
        return res;
    }

    @Test
    // Note that this case cannot be vectorized due to data dependence.
    @IR(failOn = {IRNode.STORE_VECTOR})
    public int[] sameArrayWithDifferentIndex() {
        int[] res = new int[SIZE];
        System.arraycopy(ints, 0, res, 0, SIZE);
        for (int i = 1, j = 0; i < 100; i++, j++) {
            res[i] += res[j];
        }
        return res;
    }

    // ---------------- Subword Type Arrays ----------------

    @Test
    // No true dependency in read-forward case.
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"AlignVector", "false"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public short[] shortArrayWithDependencePos() {
        short[] res = new short[SIZE];
        System.arraycopy(shorts, 0, res, 0, SIZE);
        for (int i = 0; i < SIZE / 2; i++) {
            res[i] *= shorts[i + 1];
        }
        return res;
    }

    @Test
    // Note that this case cannot be vectorized due to data dependence.
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"})
    // Speculative aliasing check -> never fails -> only predicate, no multiversioning.
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true", "AlignVector", "false"},
        phase = CompilePhase.PRINT_IDEAL,
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.MUL_VS, ">0",
                  IRNode.LOAD_VECTOR_S, ">0", // full vectorization
                  ".*multiversion.*", "= 0"})
    // JDK-8354303: could we prove statically that there is no aliasing?
    public short[] shortArrayWithDependenceNeg() {
        short[] res = new short[SIZE];
        System.arraycopy(shorts, 0, res, 0, SIZE);
        for (int i = 1; i < SIZE / 2; i++) {
            res[i] *= shorts[i - 1];
        }
        return res;
    }

    @Test
    // No true dependency in read-forward case.
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"AlignVector", "false"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.MUL_VS, ">0"}) // expect maximum size
    public char[] charArrayWithDependencePos() {
        char[] res = new char[SIZE];
        System.arraycopy(chars, 0, res, 0, SIZE);
        for (int i = 0; i < SIZE / 2; i++) {
            res[i] *= chars[i + 2];
        }
        return res;
    }

    @Test
    // Data dependency at distance 2: restrict vector size to 2
    @IR(applyIfCPUFeatureOr = {"sse2", "true"},
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"},
        phase = CompilePhase.PRINT_IDEAL,
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.MUL_VS, IRNode.VECTOR_SIZE_2, ">0", // size 2 only
                  ".*multiversion.*", "= 0"})
    public char[] charArrayWithDependenceNeg() {
        char[] res = new char[SIZE];
        System.arraycopy(chars, 0, res, 0, SIZE);
        for (int i = 2; i < SIZE / 2; i++) {
            res[i] *= chars[i - 2];
        }
        return res;
    }

    @Test
    // No true dependency in read-forward case.
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"AlignVector", "false"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public byte[] byteArrayWithDependencePos() {
        byte[] res = new byte[SIZE];
        System.arraycopy(bytes, 0, res, 0, SIZE);
        for (int i = 0; i < SIZE / 2; i++) {
            res[i] += bytes[i + 3];
        }
        return res;
    }


    @Test
    // Note that this case cannot be vectorized due to data dependence.
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"},
        failOn = {IRNode.STORE_VECTOR})
    // Speculative aliasing check -> never fails -> only predicate, no multiversioning.
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true", "AlignVector", "false"},
        phase = CompilePhase.PRINT_IDEAL,
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.ADD_VB, ">0", // full vectorization
                  ".*multiversion.*", "= 0"})
    // JDK-8354303: could we prove statically that there is no aliasing?
    public byte[] byteArrayWithDependenceNeg() {
        byte[] res = new byte[SIZE];
        System.arraycopy(bytes, 0, res, 0, SIZE);
        for (int i = 3; i < SIZE / 2; i++) {
            res[i] += bytes[i - 3];
        }
        return res;
    }

    @Test
    // No true dependency in read-forward case.
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"AlignVector", "false"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public boolean[] booleanArrayWithDependencePos() {
        boolean[] res = new boolean[SIZE];
        System.arraycopy(booleans, 0, res, 0, SIZE);
        for (int i = 0; i < SIZE / 2; i++) {
            res[i] |= booleans[i + 4];
        }
        return res;
    }

    @Test
    // Data dependency at distance 4: restrict vector size to 4
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"},
        phase = CompilePhase.PRINT_IDEAL,
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.OR_VB, IRNode.VECTOR_SIZE_4, ">0", // size 4 only
                  ".*multiversion.*", "= 0"})
    // Speculative aliasing check -> never fails -> only predicate, no multiversioning.
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIfAnd = {"UseAutoVectorizationSpeculativeAliasingChecks", "true", "AlignVector", "false"},
        phase = CompilePhase.PRINT_IDEAL,
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.OR_VB, ">0", // full vectorization
                  ".*multiversion.*", "= 0"})
    // JDK-8354303: could we prove statically that there is no aliasing?
    public boolean[] booleanArrayWithDependenceNeg() {
        boolean[] res = new boolean[SIZE];
        System.arraycopy(booleans, 0, res, 0, SIZE);
        for (int i = 4; i < SIZE / 2; i++) {
            res[i] |= booleans[i - 4];
        }
        return res;
    }

    // ---------------- Multiple Operations ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] differentIndexWithDifferentTypes() {
        int[] res1 = new int[SIZE];
        short[] res2 = new short[SIZE];
        for (int i = 0; i < SIZE / 2; i++) {
            res1[i + 1] = ints[i + 1];
            res2[i + inv2] = shorts[i + inv2];
        }
        return res1;
    }

    @Test
    // Note that this case cannot be vectorized due to data dependence.
    @IR(failOn = {IRNode.STORE_VECTOR},
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "false"})
    // Speculative aliasing check -> never fails -> only predicate, no multiversioning.
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"UseAutoVectorizationSpeculativeAliasingChecks", "true"},
        phase = CompilePhase.PRINT_IDEAL,
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_I, ">0", // full vectorization
                  ".*multiversion.*", "= 0"})
    // JDK-8354303: could we prove statically that there is no aliasing?
    public int[] differentIndexWithSameType() {
        int[] res1 = new int[SIZE];
        int[] res2 = new int[SIZE];
        for (int i = 0; i < SIZE / 2; i++) {
            res1[i + 3] = ints[i + 3];
            res2[i + inv1] = ints[i + inv1];
        }
        return res2;
    }
}
