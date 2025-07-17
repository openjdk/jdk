/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Rivos Inc. All rights reserved.
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
 * @summary Vectorization test on basic double operations
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.BasicDoubleOpTest
 *
 * @requires (os.simpleArch == "x64") | (os.simpleArch == "aarch64") | (os.simpleArch == "riscv64")
 * @requires vm.compiler2.enabled
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;
import java.util.Random;

public class BasicDoubleOpTest extends VectorizationTestRunner {

    private static final int SIZE = 543;

    private double[] a;
    private double[] b;
    private double[] c;
    private double[] d;
    private double[] e;

    public BasicDoubleOpTest() {
        // Positive test values                       sign |   exponent | mantisa
        double smallPositive   = Double.longBitsToDouble(0<<63 | 0x03f << 52 | 0x30000f);
        double positive        = Double.longBitsToDouble(0<<63 | 0x07f << 52 | 0x30000f);
        double bigPositive     = Double.longBitsToDouble(0<<63 | 0x07f << 52 | 0x30100f);
        double biggerPositive  = Double.longBitsToDouble(0<<63 | 0x7fe << 52 | 0x30000f);
        double maxPositive     = Double.MAX_VALUE;

        // Special positive
        double nan1  = Double.longBitsToDouble(0<<63 | 0x7ff << 52 | 0x7fffff);
        double nan2  = Double.longBitsToDouble(0<<63 | 0x7ff << 52 | 0x30000f);
        double inf   = Double.longBitsToDouble(0<<63 | 0x7ff << 52);
        double zero  = 0.0;

        // Negative test values                       sign |   exponent | mantisa
        double smallNegative   = Double.longBitsToDouble(1<<63 | 0x003 << 52 | 0x30000f);
        double negative        = Double.longBitsToDouble(1<<63 | 0x783 << 52 | 0x30100f);
        double bigNegative     = Double.longBitsToDouble(1<<63 | 0x783 << 52 | 0x30000f);
        double biggerNegative  = Double.longBitsToDouble(1<<63 | 0x786 << 52 | 0x30000f);
        double maxNegative     = Double.longBitsToDouble(1<<63 | 0x7fe << 52 | 0x7fffff);

        // Special negative
        double nNan1  = Double.longBitsToDouble(1<<63 | 0x7ff << 52 | 0x7fffff);
        double nNan2  = Double.longBitsToDouble(1<<63 | 0x7ff << 52 | 0x30000f);
        double nInf   = Double.longBitsToDouble(1<<63 | 0x7ff << 52);
        double nZero  = -0.0;

        double[] numberList = new double[] {
            nInf, maxNegative, biggerNegative, bigNegative, negative, smallNegative, nZero,
            zero, smallPositive, positive, bigPositive, biggerPositive, maxPositive, inf,
            nan1, nan2, nNan1, nNan2
        };

        Random rnd = new Random(10);
        a = new double[SIZE];
        b = new double[SIZE];
        c = new double[SIZE];
        d = new double[SIZE];
        e = new double[SIZE];

        for (int i = 0; i < SIZE;) {
            for (int j = 0; j < numberList.length && i < SIZE; j++, i++) {
                for (int k = j; k < numberList.length && i < SIZE; k++, i++) {
                    if (rnd.nextBoolean()) {
                        d[i] = numberList[j];
                        e[i] = numberList[k];
                    } else {
                        d[i] = numberList[k];
                        e[i] = numberList[j];
                    }
                }
            }
        }

        for (int i = 0; i < SIZE; i++) {
            a[i] = 850.0 * i + 22222.22;
            b[i] = -12345.678;
            c[i] = -1.23456e7;
        }
    }

    // ---------------- Arithmetic ----------------
    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.NEG_VD, ">0"})
    public double[] vectorNeg() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = -a[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.ABS_VD, ">0"})
    public double[] vectorAbs() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.abs(a[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"},
        counts = {IRNode.SQRT_VD, ">0"})
    public double[] vectorSqrt() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.sqrt(a[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true"},
        counts = {IRNode.ROUND_DOUBLE_MODE_V, ">0"})
    public double[] vectorCeil() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.ceil(a[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true"},
        counts = {IRNode.ROUND_DOUBLE_MODE_V, ">0"})
    public double[] vectorFloor() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.floor(a[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true"},
        counts = {IRNode.ROUND_DOUBLE_MODE_V, ">0"})
    public double[] vectorRint() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.rint(a[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.ADD_VD, ">0"})
    public double[] vectorAdd() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] + b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.SUB_VD, ">0"})
    public double[] vectorSub() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] - b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.MUL_VD, ">0"})
    public double[] vectorMul() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] * b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.DIV_VD, ">0"})
    public double[] vectorDiv() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] / b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"},
        counts = {IRNode.MAX_VD, ">0"})
    public double[] vectorMax() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.max(d[i], e[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"},
        counts = {IRNode.MAX_VD, "0"})
    public double[] vectorMax_8322090() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.max(d[i], d[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"},
        counts = {IRNode.MIN_VD, ">0"})
    public double[] vectorMin() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.min(d[i], e[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"asimd", "true"},
        counts = {IRNode.FMA_VD, ">0", IRNode.VFMLA, ">0"})
    @IR(applyIfCPUFeatureAnd = {"fma", "true", "avx", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    @IR(applyIfCPUFeature = {"rvv", "true"}, applyIf = {"UseFMA", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    public double[] vectorMulAdd() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(a[i], b[i], c[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"asimd", "true"},
        counts = {IRNode.FMA_VD, ">0", IRNode.VFMLS, ">0"})
    @IR(applyIfCPUFeatureAnd = {"fma", "true", "avx", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    @IR(applyIfCPUFeature = {"rvv", "true"}, applyIf = {"UseFMA", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    public double[] vectorMulSub1() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(-a[i], b[i], c[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"asimd", "true"},
        counts = {IRNode.FMA_VD, ">0", IRNode.VFMLS, ">0"})
    @IR(applyIfCPUFeatureAnd = {"fma", "true", "avx", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    @IR(applyIfCPUFeature = {"rvv", "true"}, applyIf = {"UseFMA", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    public double[] vectorMulSub2() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(a[i], -b[i], c[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"asimd", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    @IR(applyIfCPUFeature = {"sve", "true"},
        counts = {IRNode.VFNMLA, ">0"})
    @IR(applyIfCPUFeatureAnd = {"fma", "true", "avx", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    @IR(applyIfCPUFeature = {"rvv", "true"}, applyIf = {"UseFMA", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    public double[] vectorNegateMulAdd1() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(-a[i], b[i], -c[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"asimd", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    @IR(applyIfCPUFeature = {"sve", "true"},
        counts = {IRNode.VFNMLA, ">0"})
    @IR(applyIfCPUFeatureAnd = {"fma", "true", "avx", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    @IR(applyIfCPUFeature = {"rvv", "true"}, applyIf = {"UseFMA", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    public double[] vectorNegateMulAdd2() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(a[i], -b[i], -c[i]);
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"asimd", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    @IR(applyIfCPUFeatureAnd = {"fma", "true", "avx", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    @IR(applyIfCPUFeature = {"rvv", "true"}, applyIf = {"UseFMA", "true"},
        counts = {IRNode.FMA_VD, ">0"})
    public double[] vectorNegateMulSub() {
        double[] res = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(a[i], b[i], -c[i]);
        }
        return res;
    }

    // ---------------- Reduction ----------------
    @Test
    public double reductionAdd() {
        double res = 0.0;
        for (int i = 0; i < SIZE; i++) {
            res += a[i];
        }
        return res;
    }

    @Test
    public double reductionMax() {
        double res = Double.MIN_VALUE;
        for (int i = 0; i < SIZE; i++) {
            res = Math.max(res, a[i]);
        }
        return res;
    }

    @Test
    public double reductionMin() {
        double res = Double.MAX_VALUE;
        for (int i = 0; i < SIZE; i++) {
            res = Math.min(res, a[i]);
        }
        return res;
    }
}
