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
 * @summary Vectorization test on array index fill
 * @library /test/lib /
 *
 * @build sun.hotspot.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.ArrayIndexFillTest
 *
 * @requires vm.compiler2.enabled & vm.flagless
 */

package compiler.vectorization.runner;

public class ArrayIndexFillTest extends VectorizationTestRunner {

    private static final int SIZE = 2345;

    private int[] a;

    public ArrayIndexFillTest() {
        a = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = -5050 * i;
        }
    }

    @Test
    public byte[] fillByteArray() {
        byte[] res = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (byte) i;
        }
        return res;
    }

    @Test
    public short[] fillShortArray() {
        short[] res = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (short) i;
        }
        return res;
    }

    @Test
    public char[] fillCharArray() {
        char[] res = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = (char) i;
        }
        return res;
    }

    @Test
    public int[] fillIntArray() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = i;
        }
        return res;
    }

    @Test
    public long[] fillLongArray() {
        long[] res = new long[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = i;
        }
        return res;
    }

    @Test
    public short[] fillShortArrayWithShortIndex() {
        short[] res = new short[SIZE];
        for (short i = 0; i < SIZE; i++) {
            res[i] = i;
        }
        return res;
    }

    @Test
    public int[] fillMultipleArraysDifferentTypes1() {
        int[] res1 = new int[SIZE];
        short[] res2 = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = i;
            res2[i] = (short) i;
        }
        return res1;
    }

    @Test
    public char[] fillMultipleArraysDifferentTypes2() {
        int[] res1 = new int[SIZE];
        char[] res2 = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res1[i] = i;
            res2[i] = (char) i;
        }
        return res2;
    }

    @Test
    public int[] fillNonIndexValue() {
        int[] res = new int[SIZE];
        int val = 10000;
        for (int i = 0; i < SIZE; i++) {
            res[i] = val++;
        }
        return res;
    }
}

