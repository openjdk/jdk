/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Vectorization test on combined operations
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @requires vm.compiler2.enabled
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.LoopCombinedOpTest nCOH_nAV
 *
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.LoopCombinedOpTest nCOH_yAV
 *
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.LoopCombinedOpTest yCOH_nAV
 *
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.LoopCombinedOpTest yCOH_yAV
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

import java.util.Random;

public class LoopCombinedOpTest extends VectorizationTestRunner {

    // We must pass the flags directly to the test-VM, and not the driver vm in the @run above.
    @Override
    protected String[] testVMFlags(String[] args) {
        return switch (args[0]) {
            case "nCOH_nAV" -> new String[]{"-XX:-UseCompactObjectHeaders", "-XX:-AlignVector"};
            case "nCOH_yAV" -> new String[]{"-XX:-UseCompactObjectHeaders", "-XX:+AlignVector"};
            case "yCOH_nAV" -> new String[]{"-XX:+UseCompactObjectHeaders", "-XX:-AlignVector"};
            case "yCOH_yAV" -> new String[]{"-XX:+UseCompactObjectHeaders", "-XX:+AlignVector"};
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        };
    }

    private static final int SIZE = 543;

    private int[] a;
    private int[] b;
    private int[] c;
    private int[] d;
    private long[] l1;
    private long[] l2;
    private short[] s1;
    private short[] s2;
    private int intInv;

    public LoopCombinedOpTest() {
        a = new int[SIZE];
        b = new int[SIZE];
        c = new int[SIZE];
        d = new int[SIZE];
        l1 = new long[SIZE];
        l2 = new long[SIZE];
        s1 = new short[SIZE];
        s2 = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = -654321 * i;
            b[i] =  123456 * i;
            c[i] = -998877 * i;
            d[i] =  778899 * i;
            l1[i] = 5000000000L * i;
            l2[i] = -600000000L * i;
            s1[i] = (short) (3 * i);
            s2[i] = (short) (-2 * i);
        }
        Random ran = new Random(999);
        intInv = ran.nextInt();
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_I, "> 0"})
    public int[] opWithConstant() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] + 1234567890;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true", "rvv", "true"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_I, "> 0"})
    public int[] opWithLoopInvariant() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = b[i] * intInv;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true", "rvv", "true"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_I, "> 0"})
    public int[] opWithConstantAndLoopInvariant() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = c[i] * (intInv & 0xfff);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_I, "> 0"})
    public int[] multipleOps() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] & b[i] + c[i] & d[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true", "rvv", "true"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_I, "> 0"})
    public int[] multipleOpsWithMultipleConstants() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] * 12345678 + 87654321 + b[i] & 0xffff - c[i] * d[i] * 2;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true", "rvv", "true"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_I, "> 0"})
    // With sse2, the MulI does not vectorize. This means we have vectorized stores
    // to res1, but scalar loads from res1. The store-to-load-forwarding failure
    // detection catches this and rejects vectorization.
    public int[] multipleStores() {
        int[] res1 = new int[SIZE];
        int[] res2 = new int[SIZE];
        int[] res3 = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = a[i] & b[i];
            res2[i] = c[i] | d[i];
            res3[i] = res1[i] * res2[i];
        }
        return res3;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true", "rvv", "true"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_I, "> 0"})
    public int[] multipleStoresWithCommonSubExpression() {
        int[] res1 = new int[SIZE];
        int[] res2 = new int[SIZE];
        int[] res3 = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = a[i] * b[i];
            res2[i] = c[i] * d[i];
            res3[i] = res1[i] + res2[i];
        }
        return res3;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.LOAD_VECTOR_I, "> 0"})
    public int[] multipleOpsWith2DifferentTypes() {
        short[] res1 = new short[SIZE];
        int[] res2 = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = (short) (s1[i] + s2[i]);
            res2[i] = a[i] + b[i];
            // We have a mix of int and short loads/stores.
            // With UseCompactObjectHeaders and AlignVector,
            // we must 8-byte align all vector loads/stores.
            //
            // int:
            // adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 4*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            // If UseCompactObjectHeaders=false: iter % 2 = 0
            // If UseCompactObjectHeaders=true:  iter % 2 = 1
            //
            // byte:
            // adr = base + UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            // If UseCompactObjectHeaders=false: iter % 8 = 0
            // If UseCompactObjectHeaders=true:  iter % 8 = 4
            //
            // -> we cannot align both if UseCompactObjectHeaders=true.
        }
        return res2;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_ANY, "> 0",
                  IRNode.LOAD_VECTOR_L,                         "> 0"})
    public long[] multipleOpsWith3DifferentTypes() {
        short[] res1 = new short[SIZE];
        int[] res2 = new int[SIZE];
        long[] res3 = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = (short) (s1[i] + s2[i]);
            res2[i] = a[i] + b[i];
            res3[i] = l1[i] + l2[i];
            // We have a mix of int and short loads/stores.
            // With UseCompactObjectHeaders and AlignVector,
            // we must 8-byte align all vector loads/stores.
            //
            // int:
            // adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 4*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            // If UseCompactObjectHeaders=false: iter % 2 = 0
            // If UseCompactObjectHeaders=true:  iter % 2 = 1
            //
            // byte:
            // adr = base + UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            // If UseCompactObjectHeaders=false: iter % 8 = 0
            // If UseCompactObjectHeaders=true:  iter % 8 = 4
            //
            // -> we cannot align both if UseCompactObjectHeaders=true.
        }
        return res3;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_S, IRNode.VECTOR_SIZE_ANY, "> 0",
                  IRNode.LOAD_VECTOR_L,                         "> 0"})
    public long[] multipleOpsWith2NonAdjacentTypes() {
        short[] res1 = new short[SIZE];
        long[] res2 = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = (short) (s1[i] + s2[i]);
            res2[i] = l1[i] + l2[i];
        }
        return res2;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.LOAD_VECTOR_I, "> 0"})
    public int[] multipleOpsWith2DifferentTypesAndConstant() {
        short[] res1 = new short[SIZE];
        int[] res2 = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = (short) (s1[i] + s2[i]);
            res2[i] = a[i] + 88888888;;
            // We have a mix of int and short loads/stores.
            // With UseCompactObjectHeaders and AlignVector,
            // we must 8-byte align all vector loads/stores.
            //
            // int:
            // adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 4*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            // If UseCompactObjectHeaders=false: iter % 2 = 0
            // If UseCompactObjectHeaders=true:  iter % 2 = 1
            //
            // byte:
            // adr = base + UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            // If UseCompactObjectHeaders=false: iter % 8 = 0
            // If UseCompactObjectHeaders=true:  iter % 8 = 4
            //
            // -> we cannot align both if UseCompactObjectHeaders=true.
        }
        return res2;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true", "rvv", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.LOAD_VECTOR_I, "> 0"})
    public int[] multipleOpsWith2DifferentTypesAndInvariant() {
        short[] res1 = new short[SIZE];
        int[] res2 = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = (short) (s1[i] + s2[i]);
            res2[i] = a[i] * intInv;
            // We have a mix of int and short loads/stores.
            // With UseCompactObjectHeaders and AlignVector,
            // we must 8-byte align all vector loads/stores.
            //
            // int:
            // adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 4*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            // If UseCompactObjectHeaders=false: iter % 2 = 0
            // If UseCompactObjectHeaders=true:  iter % 2 = 1
            //
            // byte:
            // adr = base + UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            // If UseCompactObjectHeaders=false: iter % 8 = 0
            // If UseCompactObjectHeaders=true:  iter % 8 = 4
            //
            // -> we cannot align both if UseCompactObjectHeaders=true.
        }
        return res2;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true", "rvv", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.LOAD_VECTOR_I, "> 0"})
    public int[] multipleOpsWith2DifferentTypesAndComplexExpression() {
        short[] res1 = new short[SIZE];
        int[] res2 = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = (short) (s1[i] + s2[i]);
            res2[i] = a[i] * (b[i] + intInv * c[i] & 0xfffffa);
            // same argument as in multipleOpsWith2DifferentTypesAndInvariant.
        }
        return res2;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse3", "true", "rvv", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_S, "> 0",
                  IRNode.LOAD_VECTOR_I, "> 0"})
    public int[] multipleOpsWith2DifferentTypesAndSharedOp() {
        int i = 0, sum = 0;
        int[] res1 = new int[SIZE];
        short[] res2 = new short[SIZE];
        while (++i < SIZE) {
            sum += (res1[i]--);
            res2[i]++;
            // We have a mix of int and short loads/stores.
            // With UseCompactObjectHeaders and AlignVector,
            // we must 8-byte align all vector loads/stores.
            //
            // int:
            // adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 4*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            // If UseCompactObjectHeaders=false: iter % 2 = 0
            // If UseCompactObjectHeaders=true:  iter % 2 = 1
            //
            // byte:
            // adr = base + UNSAFE.ARRAY_BYTE_BASE_OFFSET + 1*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            // If UseCompactObjectHeaders=false: iter % 8 = 0
            // If UseCompactObjectHeaders=true:  iter % 8 = 4
            //
            // -> we cannot align both if UseCompactObjectHeaders=true.
        }
        return res1;
    }

    @Test
    // POPULATE_INDEX seems to mess with vectorization, see JDK-8332878.
    public int[] fillIndexPlusStride() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = i + 1;
        }
        return res;
    }

    @Test
    // POPULATE_INDEX seems to mess with vectorization, see JDK-8332878.
    public int[] addArrayWithIndex() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] + i;
        }
        return res;
    }

    @Test
    // POPULATE_INDEX seems to mess with vectorization, see JDK-8332878.
    public short[] multiplyAddShortIndex() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (i * i + i);
        }
        return res;
    }

    @Test
    // POPULATE_INDEX seems to mess with vectorization, see JDK-8332878.
    public int[] multiplyBySumOfIndexAndInvariant() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] * (i + 10 + intInv);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true", "rvv", "true"},
        applyIfOr = { "UseCompactObjectHeaders", "false", "AlignVector", "false"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] manuallyUnrolledStride2() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE - 1; i += 2) {
            res[i] = a[i] * b[i];
            res[i + 1] = a[i + 1] * b[i + 1];
            // Hand-unrolling can mess with alignment!
            //
            // With UseCompactObjectHeaders and AlignVector,
            // we must 8-byte align all vector loads/stores.
            //
            // adr = base + UNSAFE.ARRAY_INT_BASE_OFFSET + 8*iter
            //              = 16 (or 12 if UseCompactObjectHeaders=true)
            // If UseCompactObjectHeaders=false: 16 divisible by 8 -> vectorize
            // If UseCompactObjectHeaders=true:  12 not divisibly by 8 -> not vectorize
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse4.1", "true", "rvv", "true"},
        counts = {IRNode.STORE_VECTOR, ">0",
                  IRNode.LOAD_VECTOR_I, "> 0"})
    public int partialVectorizableLoop() {
        int[] res = new int[SIZE];
        int k = 9;
        for (int i = 0; i < SIZE / 2; i++) {
            res[i] = a[i] * b[i];
            k = 3 * k + 1;
        }
        return k;
    }
}
