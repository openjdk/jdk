/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8302850
 * @summary Tests that an array clone call that has been compiled with C1
 *          handles null values correctly.
 * @run main/othervm -XX:-UseOnStackReplacement -XX:-BackgroundCompilation -XX:TieredStopAtLevel=1
 *                   -XX:CompileOnly=compiler.c1.TestNullArrayClone::testClone* -XX:+UnlockExperimentalVMOptions
 *                   compiler.c1.TestNullArrayClone
 */
package compiler.c1;

import java.util.concurrent.ThreadLocalRandom;

public class TestNullArrayClone {
    static final int ITER = 2000; // ~ Tier3CompileThreshold
    static final int ARRAY_SIZE = 999;

    public static void main(String[] args) {
        testInts();
        testLongs();
        testBytes();
    }

    private static void testInts() {
        final int[] arr = new int[ARRAY_SIZE];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = ThreadLocalRandom.current().nextInt();
        }

        for (int i = 0; i < ITER; i++) {
            int[] result = testClonePrimitiveInt(arr);
            if (result.length != arr.length) {
                throw new RuntimeException("Unexpected clone length: source array length " + arr.length + " != clone array length " + result.length);
            }
            for (int j = 0; j < arr.length; j++) {
                if (result[j] != arr[j]) {
                    throw new RuntimeException("Unexpected result: " + result[j] + " != " + j);
                }
            }
        }

        try {
            testClonePrimitiveInt(null);
            throw new RuntimeException("Expected NullPointerException to be thrown");
        } catch (NullPointerException e) {
        }
    }

    private static void testLongs() {
        final long[] arr = new long[ARRAY_SIZE];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = ThreadLocalRandom.current().nextLong();
        }

        for (int i = 0; i < ITER; i++) {
            long[] result = testClonePrimitiveLong(arr);
            if (result.length != arr.length) {
                throw new RuntimeException("Unexpected clone length: source array length " + arr.length + " != clone array length " + result.length);
            }
            for (int j = 0; j < arr.length; j++) {
                if (result[j] != arr[j]) {
                    throw new RuntimeException("Unexpected result: " + result[j] + " != " + j);
                }
            }
        }

        try {
            testClonePrimitiveLong(null);
            throw new RuntimeException("Expected NullPointerException to be thrown");
        } catch (NullPointerException e) {
        }
    }

    private static void testBytes() {
        final byte[] arr = new byte[ARRAY_SIZE];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (byte) ThreadLocalRandom.current().nextInt();
        }

        for (int i = 0; i < ITER; i++) {
            byte[] result = testClonePrimitiveBytes(arr);
            if (result.length != arr.length) {
                throw new RuntimeException("Unexpected clone length: source array length " + arr.length + " != clone array length " + result.length);
            }
            for (int j = 0; j < arr.length; j++) {
                if (result[j] != arr[j]) {
                    throw new RuntimeException("Unexpected result: " + result[j] + " != " + j);
                }
            }
        }

        try {
            testClonePrimitiveBytes(null);
            throw new RuntimeException("Expected NullPointerException to be thrown");
        } catch (NullPointerException e) {
        }
    }

    static int[] testClonePrimitiveInt(int[] ints) {
        return ints.clone();
    }

    static long[] testClonePrimitiveLong(long[] longs) {
        return longs.clone();
    }

    static byte[] testClonePrimitiveBytes(byte[] bytes) {
        return bytes.clone();
    }
}
