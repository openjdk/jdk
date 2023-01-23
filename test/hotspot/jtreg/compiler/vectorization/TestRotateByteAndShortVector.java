/*
 * Copyright (c) 2022, Loongson Technology Co. Ltd. All rights reserved.
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
 * @bug 8286847
 * @key randomness
 * @summary Test vectorization of rotate byte and short
 * @library /test/lib
 * @run main/othervm -XX:-TieredCompilation -XX:CompileCommand=compileonly,TestRotateByteAndShortVector::testRotate* -Xbatch TestRotateByteAndShortVector
 */

import java.util.Random;
import jdk.test.lib.Utils;

public class TestRotateByteAndShortVector {
    private static final Random random = Utils.getRandomInstance();
    private static final int ARRLEN = 512;
    private static final int ITERS = 11000;

    private static byte[] arrByte = new byte[ARRLEN];
    private static byte[] rolByte = new byte[ARRLEN];
    private static byte[] rorByte = new byte[ARRLEN];
    private static byte   resByte = 0;

    private static short[] arrShort = new short[ARRLEN];
    private static short[] rolShort = new short[ARRLEN];
    private static short[] rorShort = new short[ARRLEN];
    private static short   resShort = 0;

    public static void main(String[] args) {
        System.out.println("warmup");
        warmup();

        System.out.println("Testing...");
        runRotateLeftByteTest();
        runRotateRightByteTest();
        runRotateLeftShortTest();
        runRotateRightShortTest();

        System.out.println("PASSED");
    }

    static void warmup() {
        random.nextBytes(arrByte);
        randomShorts();
        for (int i = 0; i < ITERS; i++) {
            testRotateLeftByte(rolByte, arrByte, i);
            testRotateRightByte(rorByte, arrByte, i);
            testRotateLeftShort(rolShort, arrShort, i);
            testRotateRightShort(rorShort, arrShort, i);
        }
    }

    static void randomShorts() {
        for (int i = 0; i < ARRLEN; i++) {
            arrShort[i] = (short) random.nextInt();
        }
    }

    static void runRotateLeftByteTest() {
        for (int shift = 0; shift < 64; shift++) {
            random.nextBytes(arrByte);
            testRotateLeftByte(rolByte, arrByte, shift);
            for (int i = 0; i < ARRLEN; i++) {
                resByte = (byte) ((arrByte[i] << shift) | (arrByte[i] >>> -shift));
                if (rolByte[i] != resByte) {
                    throw new RuntimeException("rol value = " + arrByte[i] + ", shift = " + shift + ", error: " + "expect " + resByte + " but result is " + rolByte[i]);
                }
            }
        }
    }

    static void runRotateRightByteTest() {
        for (int shift = 0; shift < 64; shift++) {
            random.nextBytes(arrByte);
            testRotateRightByte(rorByte, arrByte, shift);
            for (int i = 0; i < ARRLEN; i++) {
                resByte = (byte) ((arrByte[i] >>> shift) | (arrByte[i] << -shift));
                if (rorByte[i] != resByte) {
                    throw new RuntimeException("ror value = " + arrByte[i] + ", shift = " + shift + ", error: " + "expect " + resByte + " but result is " + rorByte[i]);
                }
            }
        }
    }

    static void runRotateLeftShortTest() {
        for (int shift = 0; shift < 64; shift++) {
            randomShorts();
            testRotateLeftShort(rolShort, arrShort, shift);
            for (int i = 0; i < ARRLEN; i++) {
                resShort = (short) ((arrShort[i] << shift) | (arrShort[i] >>> -shift));
                if (rolShort[i] != resShort) {
                    throw new RuntimeException("rol value = " + arrShort[i] + ", shift = " + shift + ", error: " + "expect " + resShort + " but result is " + rolShort[i]);
                }
            }
        }
    }

    static void runRotateRightShortTest() {
        for (int shift = 0; shift < 64; shift++) {
            randomShorts();
            testRotateRightShort(rorShort, arrShort, shift);
            for (int i = 0; i < ARRLEN; i++) {
                resShort = (short) ((arrShort[i] >>> shift) | (arrShort[i] << -shift));
                if (rorShort[i] != resShort) {
                    throw new RuntimeException("ror value = " + arrShort[i] + ", shift = " + shift + ", error: " + "expect " + resShort + " but result is " + rorShort[i]);
                }
            }
        }
    }

    static void testRotateLeftByte(byte[] test, byte[] arr, int shift) {
        for (int i = 0; i < ARRLEN; i++) {
            test[i] = (byte) ((arr[i] << shift) | (arr[i] >>> -shift));
        }
    }

    static void testRotateRightByte(byte[] test, byte[] arr, int shift) {
        for (int i = 0; i < ARRLEN; i++) {
            test[i] = (byte) ((arr[i] >>> shift) | (arr[i] << -shift));
        }
    }

    static void testRotateLeftShort(short[] test, short[] arr, int shift) {
        for (int i = 0; i < ARRLEN; i++) {
            test[i] = (short) ((arr[i] << shift) | (arr[i] >>> -shift));
        }
    }

    static void testRotateRightShort(short[] test, short[] arr, int shift) {
        for (int i = 0; i < ARRLEN; i++) {
            test[i] = (short) ((arr[i] >>> shift) | (arr[i] << -shift));
        }
    }
}
