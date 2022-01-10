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
 * @summary Vectorization test on basic char operations
 * @library /test/lib /
 *
 * @build sun.hotspot.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.BasicCharOpTest
 *
 * @requires vm.compiler2.enabled & vm.flagless
 */

package compiler.vectorization.runner;

public class BasicCharOpTest extends VectorizationTestRunner {

    private static final int SIZE = 2345;

    private char[] a;
    private char[] b;
    private char[] c;

    public BasicCharOpTest() {
        a = new char[SIZE];
        b = new char[SIZE];
        c = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = (char) (20 * i);
            b[i] = (char) (i + 44444);
            c[i] = (char) 10000;
        }
    }

    // ---------------- Arithmetic ----------------
    @Test
    public char[] vectorNeg() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) -a[i];
        }
        return res;
    }

    @Test
    // Note that Math.abs() on unsigned subword types can NOT be vectorized
    // since all the values are non-negative according to the semantics.
    public char[] vectorAbs() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) Math.abs(a[i]);
        }
        return res;
    }

    @Test
    public char[] vectorAdd() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] + b[i]);
        }
        return res;
    }

    @Test
    public char[] vectorSub() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] - b[i]);
        }
        return res;
    }

    @Test
    public char[] vectorMul() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] * b[i]);
        }
        return res;
    }

    @Test
    public char[] vectorMulAdd() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (c[i] + a[i] * b[i]);
        }
        return res;
    }

    @Test
    public char[] vectorMulSub() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (c[i] - a[i] * b[i]);
        }
        return res;
    }

    // ---------------- Logic ----------------
    @Test
    public char[] vectorNot() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) ~a[i];
        }
        return res;
    }

    @Test
    public char[] vectorAnd() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] & b[i]);
        }
        return res;
    }

    @Test
    public char[] vectorOr() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] | b[i]);
        }
        return res;
    }

    @Test
    public char[] vectorXor() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] ^ b[i]);
        }
        return res;
    }

    // ---------------- Shift ----------------
    @Test
    public char[] vectorShiftLeft() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] << 3);
        }
        return res;
    }

    @Test
    public char[] vectorSignedShiftRight() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] >> 2);
        }
        return res;
    }

    @Test
    public char[] vectorUnsignedShiftRight() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) (a[i] >>> 5);
        }
        return res;
    }
}

