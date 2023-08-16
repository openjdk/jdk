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
 * @summary Vectorization test on basic float operations
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.BasicFloatOpTest
 *
 * @requires (os.simpleArch == "x64") | (os.simpleArch == "aarch64")
 * @requires vm.compiler2.enabled & vm.flagless
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

public class BasicFloatOpTest extends VectorizationTestRunner {

    private static final int SIZE = 543;

    private float[] a;
    private float[] b;
    private float[] c;

    public BasicFloatOpTest() {
        a = new float[SIZE];
        b = new float[SIZE];
        c = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = 850.0f * i + 22222.22f;
            b[i] = -12345.678f;
            c[i] = -1.23456e7f;
        }
    }

    // ---------------- Arithmetic ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse", "true"},
        counts = {IRNode.NEG_VF, ">0"})
    public float[] vectorNeg() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = -a[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse", "true"},
        counts = {IRNode.ABS_VF, ">0"})
    public float[] vectorAbs() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.abs(a[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true"},
        counts = {IRNode.SQRT_VF, ">0"})
    public float[] vectorSqrt() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) Math.sqrt(a[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.ADD_VF, ">0"})
    public float[] vectorAdd() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] + b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.SUB_VF, ">0"})
    public float[] vectorSub() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] - b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.MUL_VF, ">0"})
    public float[] vectorMul() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] * b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.DIV_VF, ">0"})
    public float[] vectorDiv() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] / b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true"},
        counts = {IRNode.MAX_VF, ">0"})
    public float[] vectorMax() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.max(a[i], b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true"},
        counts = {IRNode.MIN_VF, ">0"})
    public float[] vectorMin() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.min(a[i], b[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"asimd", "true"},
        counts = {IRNode.FMA_VF, ">0", IRNode.VFMLA, ">0"})
    @IR(applyIfCPUFeatureAnd = {"fma", "true", "avx", "true"},
        counts = {IRNode.FMA_VF, ">0"})
    public float[] vectorMulAdd() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(a[i], b[i], c[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"asimd", "true"},
        counts = {IRNode.FMA_VF, ">0", IRNode.VFMLS, ">0"})
    @IR(applyIfCPUFeatureAnd = {"fma", "true", "avx", "true"},
        counts = {IRNode.FMA_VF, ">0"})
    public float[] vectorMulSub1() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(-a[i], b[i], c[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"asimd", "true"},
        counts = {IRNode.FMA_VF, ">0", IRNode.VFMLS, ">0"})
    @IR(applyIfCPUFeatureAnd = {"fma", "true", "avx", "true"},
        counts = {IRNode.FMA_VF, ">0"})
    public float[] vectorMulSub2() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(a[i], -b[i], c[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"asimd", "true"},
        counts = {IRNode.FMA_VF, ">0"})
    @IR(applyIfCPUFeature = {"sve", "true"},
        counts = {IRNode.VFNMLA, ">0"})
    @IR(applyIfCPUFeatureAnd = {"fma", "true", "avx", "true"},
        counts = {IRNode.FMA_VF, ">0"})
    public float[] vectorNegateMulAdd1() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(-a[i], b[i], -c[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"asimd", "true"},
        counts = {IRNode.FMA_VF, ">0"})
    @IR(applyIfCPUFeature = {"sve", "true"},
        counts = {IRNode.VFNMLA, ">0"})
    @IR(applyIfCPUFeatureAnd = {"fma", "true", "avx", "true"},
        counts = {IRNode.FMA_VF, ">0"})
    public float[] vectorNegateMulAdd2() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(a[i], -b[i], -c[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"asimd", "true"},
        counts = {IRNode.FMA_VF, ">0"})
    @IR(applyIfCPUFeatureAnd = {"fma", "true", "avx", "true"},
        counts = {IRNode.FMA_VF, ">0"})
    public float[] vectorNegateMulSub() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(a[i], b[i], -c[i]);
        }
        return res;
    }

    // ---------------- Reduction ----------------
    @Test
    public float reductionAdd() {
        float res = 0.0f;
        for (int i = 0; i < SIZE; i++) {
            res += a[i];
        }
        return res;
    }

    @Test
    public float reductionMax() {
        float res = Float.MIN_VALUE;
        for (int i = 0; i < SIZE; i++) {
            res = Math.max(res, a[i]);
        }
        return res;
    }

    @Test
    public float reductionMin() {
        float res = Float.MAX_VALUE;
        for (int i = 0; i < SIZE; i++) {
            res = Math.min(res, a[i]);
        }
        return res;
    }
}
