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
 * @summary Test vectorization of rotate byte
 * @run main/othervm -XX:-TieredCompilation -Xcomp -XX:CompileCommand=compileonly,TestRotateByteVector::testRotate* -Xbatch TestRotateByteVector
 */

public class TestRotateByteVector {
    private static final int ARRLEN = 512;
    private static final int ITERS = 50000;
    private static byte[] arr = new byte[ARRLEN];
    private static byte[] rolTest = new byte[ARRLEN];
    private static byte[] rorTest = new byte[ARRLEN];
    private static byte[] res = new byte[ARRLEN];

    public static void main(String[] args) {
        // init
        for (int i = 0; i < ARRLEN; i++) {
            arr[i] = (byte) i;
        }

        System.out.println("warmup");
        warmup();

        System.out.println("Testing rotate byte...");
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

    static void testRotateLeft(byte[] arr, byte[] test, int shift) {
        for (int i = 0; i < ARRLEN; i++) {
            test[i] = (byte) ((arr[i] << shift) | (arr[i] >>> -shift));
        }
    }

    static void testRotateRight(byte[] arr, byte[] test, int shift) {
        for (int i = 0; i < ARRLEN; i++) {
            test[i] = (byte) ((arr[i] >>> shift) | (arr[i] << -shift));
        }
    }

    static void rotateLeftRes(byte[] arr, byte[] res, int shift) {
        for (int i = 0; i < ARRLEN; i++) {
            res[i] = (byte) ((arr[i] << shift) | (arr[i] >>> -shift));
        }
    }

    static void rotateRightRes(byte[] arr, byte[] res, int shift) {
        for (int i = 0; i < ARRLEN; i++) {
            res[i] = (byte) ((arr[i] >>> shift) | (arr[i] << -shift));
        }
    }

    static void verify(byte[] test, byte[] res, int shift, String op) {
        for (int i = 0; i < ARRLEN; i++) {
            if (test[i] != res[i]) {
                throw new RuntimeException(op + " " + shift + " error: [" + arr[i] + "] expect " + res[i] + " but result is " + test[i]);
            }
        }
    }
}
