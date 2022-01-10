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
 * @summary Vectorization test on basic short operations
 * @library /test/lib /
 *
 * @build sun.hotspot.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.BasicShortOpTest
 *
 * @requires vm.compiler2.enabled & vm.flagless
 */

package compiler.vectorization.runner;

public class BasicShortOpTest extends VectorizationTestRunner {

    private static final int SIZE = 2345;

    private short[] a;
    private short[] b;
    private short[] c;

    public BasicShortOpTest() {
        a = new short[SIZE];
        b = new short[SIZE];
        c = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = (short) (-12 * i);
            b[i] = (short) (9 * i + 8888);
            c[i] = (short) -32323;
        }
    }

    // ---------------- Arithmetic ----------------
    @Test
    public short[] vectorNeg() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) -a[i];
        }
        return res;
    }

    @Test
    public short[] vectorAbs() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) Math.abs(a[i]);
        }
        return res;
    }

    @Test
    public short[] vectorAdd() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] + b[i]);
        }
        return res;
    }

    @Test
    public short[] vectorSub() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] - b[i]);
        }
        return res;
    }

    @Test
    public short[] vectorMul() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] * b[i]);
        }
        return res;
    }

    @Test
    public short[] vectorMulAdd() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (c[i] + a[i] * b[i]);
        }
        return res;
    }

    @Test
    public short[] vectorMulSub() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (c[i] - a[i] * b[i]);
        }
        return res;
    }

    // ---------------- Logic ----------------
    @Test
    public short[] vectorNot() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) ~a[i];
        }
        return res;
    }

    @Test
    public short[] vectorAnd() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] & b[i]);
        }
        return res;
    }

    @Test
    public short[] vectorOr() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] | b[i]);
        }
        return res;
    }

    @Test
    public short[] vectorXor() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] ^ b[i]);
        }
        return res;
    }

    // ---------------- Shift ----------------
    @Test
    public short[] vectorShiftLeft() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] << 3);
        }
        return res;
    }

    @Test
    public short[] vectorSignedShiftRight() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] >> 2);
        }
        return res;
    }

    @Test
    // Note that unsigned shift right on subword signed integer types can
    // not be vectorized since the sign extension bits would be lost.
    public short[] vectorUnsignedShiftRight() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) (a[i] >>> 5);
        }
        return res;
    }
}

