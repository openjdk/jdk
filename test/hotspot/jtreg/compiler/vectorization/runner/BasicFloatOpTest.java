/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
 * @build sun.hotspot.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.BasicFloatOpTest
 *
 * @requires vm.compiler2.enabled & vm.flagless
 */

package compiler.vectorization.runner;

public class BasicFloatOpTest extends VectorizationTestRunner {

    private static final int SIZE = 2345;

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
    public float[] vectorNeg() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = -a[i];
        }
        return res;
    }

    @Test
    public float[] vectorAbs() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.abs(a[i]);
        }
        return res;
    }

    @Test
    public float[] vectorSqrt() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (float) Math.sqrt(a[i]);
        }
        return res;
    }

    @Test
    public float[] vectorAdd() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] + b[i];
        }
        return res;
    }

    @Test
    public float[] vectorSub() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] - b[i];
        }
        return res;
    }

    @Test
    public float[] vectorMul() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] * b[i];
        }
        return res;
    }

    @Test
    public float[] vectorDiv() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] / b[i];
        }
        return res;
    }

    @Test
    public float[] vectorMax() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.max(a[i], b[i]);
        }
        return res;
    }

    @Test
    public float[] vectorMin() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.min(a[i], b[i]);
        }
        return res;
    }

    @Test
    public float[] vectorMulAdd() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(a[i], b[i], c[i]);
        }
        return res;
    }

    @Test
    public float[] vectorMulSub1() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(-a[i], b[i], c[i]);
        }
        return res;
    }

    @Test
    public float[] vectorMulSub2() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(a[i], -b[i], c[i]);
        }
        return res;
    }

    @Test
    public float[] vectorNegateMulAdd1() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(-a[i], b[i], -c[i]);
        }
        return res;
    }

    @Test
    public float[] vectorNegateMulAdd2() {
        float[] res = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = Math.fma(a[i], -b[i], -c[i]);
        }
        return res;
    }

    @Test
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

