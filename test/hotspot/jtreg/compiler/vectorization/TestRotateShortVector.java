/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, Loongson Technology. All rights reserved.
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

/**
 * @test
 * @bug 8261022
 * @summary Test vectorization of rotate short
 * @run main/othervm -XX:-TieredCompilation -Xcomp -XX:CompileCommand=compileonly,TestRotateShortVector::testRotate* -Xbatch TestRotateShortVector
 */

public class TestRotateShortVector {
    private static final int ARRLEN = 512;
    private static final int ITERS = 50000;
    private static short[] arr = new short[ARRLEN];
    private static short[] rolTest = new short[ARRLEN];
    private static short[] rorTest = new short[ARRLEN];
    private static short[] res = new short[ARRLEN];

    public static void main(String[] args) {
        // init
        for (int i = 0; i < ARRLEN; i++) {
            arr[i] = (short) i;
        }

        System.out.println("warmup");
        warmup();

        System.out.println("Testing rotate short...");
        test();

        System.out.println("PASSED");
    }

    static void warmup() {
        for (int i = 0; i < ITERS; i++) {
            testRotateLeft(arr, rolTest, i);
            testRotateRight(arr, rorTest, i);
        }
    }

    static void test() {
        for (int shift = 0; shift <= 512; shift++) {
            testRotateLeft(arr, rolTest, shift);
            rotateLeftRes(arr, res, shift);
            verify(rolTest, res, shift, "rol");

            testRotateRight(arr, rorTest, shift);
            rotateRightRes(arr, res, shift);
            verify(rorTest, res, shift, "ror");
        }
    }

    static void testRotateLeft(short[] arr, short[] test, int shift) {
        for (int i = 0; i < ARRLEN; i++) {
            test[i] = (short) ((arr[i] << shift) | (arr[i] >>> -shift));
        }
    }

    static void testRotateRight(short[] arr, short[] test, int shift) {
        for (int i = 0; i < ARRLEN; i++) {
            test[i] = (short) ((arr[i] >>> shift) | (arr[i] << -shift));
        }
    }

    static void rotateLeftRes(short[] arr, short[] res, int shift) {
        for (int i = 0; i < ARRLEN; i++) {
            res[i] = (short) ((arr[i] << shift) | (arr[i] >>> -shift));
        }
    }

    static void rotateRightRes(short[] arr, short[] res, int shift) {
        for (int i = 0; i < ARRLEN; i++) {
            res[i] = (short) ((arr[i] >>> shift) | (arr[i] << -shift));
        }
    }

    static void verify(short[] test, short[] res, int shift, String op) {
        for (int i = 0; i < ARRLEN; i++) {
            if (test[i] != res[i]) {
                throw new RuntimeException(op + " " + shift + " error: [" + arr[i] + "] expect " + res[i] + " but result is " + test[i]);
            }
        }
    }
}
