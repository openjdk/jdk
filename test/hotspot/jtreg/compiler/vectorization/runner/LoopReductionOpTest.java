/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
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
 * @summary Vectorization test on reduction operations
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.LoopReductionOpTest
 *
 * @requires (os.simpleArch == "x64") | (os.simpleArch == "aarch64")
 * @requires vm.compiler2.enabled & vm.flagless
 *
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

import java.util.Random;

public class LoopReductionOpTest extends VectorizationTestRunner {

    private static final int SIZE = 543;

    private int[] a;
    private int[] b;
    private int[] c;
    private double[] d;
    private float[] f;
    private long[] l;
    private int intInv;

    public LoopReductionOpTest() {
        a = new int[SIZE];
        b = new int[SIZE];
        c = new int[SIZE];
        d = new double[SIZE];
        f = new float[SIZE];
        l = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = -531 * i;
            b[i] = 2222 * i + 8;
            c[i] = 2147480000;
            d[i] = i * 2.5;
            f[i] = i * -(333.3f);
            l[i] = 444444444L * i;
        }
        Random ran = new Random(10001);
        intInv = ran.nextInt();
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse3", "true"},
        counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.ADD_REDUCTION_V, ">0"})
    public int reductionAddSumOfArray() {
        int res = 0;
        for (int i = 0; i < SIZE; i++) {
            res += (a[i] + b[i]);
        }
        return res;
    }

    @Test
    public int reductionAddIndex() {
        int res = 0;
        for (int i = 0; i < SIZE; i++) {
            res += i;
        }
        return res;
    }

    @Test
    // Note that this loop should be optimized to straight-line code.
    @IR(failOn = {IRNode.COUNTED_LOOP})
    public int reductionAddConstant() {
        int res = 0;
        for (int i = 0; i < SIZE; i++) {
            res += 222;
        }
        return res;
    }

    @Test
    public int reductionAddLoopInv() {
        int res = 0;
        for (int i = 0; i < SIZE; i++) {
            res += intInv;
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"sve", "true", "avx2", "true"},
        counts = {IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.ADD_REDUCTION_V, ">0"})
    public int reductionAddSumOfMultiple() {
        int res = 0;
        for (int i = 0; i < SIZE; i++) {
            res += (a[i] + b[i]) * i << 2;
        }
        return res;
    }

    @Test
    public int twoReductions() {
        int res1 = 0;
        int res2 = 0;
        for (int i = 0; i < SIZE; i++) {
            res1 += a[i];
            res2 += b[i];
        }
        return res1 * res2;
    }

    @Test
    public float twoReductionsSameElementSize() {
        int res1 = 0;
        float res2 = 0.0f;
        for (int i = 0; i < SIZE; i++) {
            res1 += a[i];
            res2 += f[i];
        }
        return res1 * res2;
    }

    @Test
    public double twoReductionsDifferentSizes1() {
        int res1 = 0;
        double res2 = 0.0;
        for (int i = 0; i < SIZE; i++) {
            res1 += a[i];
            res2 += d[i];
        }
        return res1 * res2;
    }

    @Test
    public double twoReductionsDifferentSizes2() {
        long res1 = 0L;
        float res2 = 0.0f;
        for (int i = 0; i < SIZE; i++) {
            res1 += l[i];
            res2 += f[i];
        }
        return res1 * res2;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    @IR(applyIfCPUFeatureOr = {"avx2", "true"},
        counts = {IRNode.ADD_REDUCTION_V, ">0"})
    public long reductionWithNonReductionDifferentSizes() {
        long res = 0L;
        int[] arr = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            arr[i] = a[i] + b[i];
            res += l[i];
        }
        return res;
    }

    @Test
    public long reductionLoopIndexSumDifferentSizes() {
        int intSum = 0;
        long longSum = 0L;
        for (int i = 0; i < SIZE; i++) {
            intSum += i;
            longSum += i;
        }
        return intSum + 2 * longSum;
    }
}
