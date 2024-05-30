/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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
 *
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.vectorization.TestVectorizationUnsafeNegativeScale
 *
 */

package compiler.vectorization;

import java.util.Arrays;
import jdk.internal.misc.Unsafe;
import compiler.lib.ir_framework.*;

public class TestVectorizationUnsafeNegativeScale {

    static Unsafe unsafe = Unsafe.getUnsafe();

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
    }

    static byte[] byteArray = new byte[1000];

    @Test
    @IR(counts = { IRNode.STORE_VECTOR , ">= 1"})
    private static void testByteArray(byte[] array, int start) {
        for (int i = start; i < array.length; i++) {
            final long idx = (long)array.length - (long)(i + 1);
            final long offset = Unsafe.ARRAY_BYTE_BASE_OFFSET + idx * Unsafe.ARRAY_BYTE_INDEX_SCALE;
            unsafe.putByte(array, offset, (byte) 0x42);
        }
    }

    @Run(test = "testByteArray")
    private static void testByteArrayRunner() {
        Arrays.fill(byteArray, (byte)0);
        testByteArray(byteArray, 0);
        for (int j = 0; j < byteArray.length; j++) {
            if (byteArray[j] != 0x42) {
                throw new RuntimeException("For index " + j + ": " + byteArray[j]);
            }
        }
    }
    
    static short[] shortArray = new short[1000];

    @Test
    @IR(counts = { IRNode.STORE_VECTOR , ">= 1"})
    private static void testShortArray(short[] array, int start) {
        for (int i = start; i < array.length; i++) {
            final long idx = (long)array.length - (long)(i + 1);
            final long offset = Unsafe.ARRAY_SHORT_BASE_OFFSET + idx * Unsafe.ARRAY_SHORT_INDEX_SCALE;
            unsafe.putShort(array, offset, (short) 0x42);
        }
    }

    @Run(test = "testShortArray")
    private static void testShortArrayRunner() {
        Arrays.fill(shortArray, (short)0);
        testShortArray(shortArray, 0);
        for (int j = 0; j < shortArray.length; j++) {
            if (shortArray[j] != 0x42) {
                throw new RuntimeException("For index " + j + ": " + shortArray[j]);
            }
        }
    }

    static int[] intArray = new int[1000];

    @Test
    @IR(counts = { IRNode.STORE_VECTOR , ">= 1"})
    private static void testIntArray(int[] array, int start) {
        for (int i = start; i < array.length; i++) {
            final long idx = (long)array.length - (long)(i + 1);
            final long offset = Unsafe.ARRAY_INT_BASE_OFFSET + idx * Unsafe.ARRAY_INT_INDEX_SCALE;
            unsafe.putInt(array, offset, (int) 0x42);
        }
    }

    @Run(test = "testIntArray")
    private static void testIntArrayRunner() {
        Arrays.fill(intArray, (int)0);
        testIntArray(intArray, 0);
        for (int j = 0; j < intArray.length; j++) {
            if (intArray[j] != 0x42) {
                throw new RuntimeException("For index " + j + ": " + intArray[j]);
            }
        }
    }

    static long[] longArray = new long[1000];

    @Test
    @IR(counts = { IRNode.STORE_VECTOR , ">= 1"})
    private static void testLongArray(long[] array, int start) {
        for (int i = start; i < array.length; i++) {
            final long idx = (long)array.length - (long)(i + 1);
            final long offset = Unsafe.ARRAY_LONG_BASE_OFFSET + idx * Unsafe.ARRAY_LONG_INDEX_SCALE;
            unsafe.putLong(array, offset, (long) 0x42);
        }
    }

    @Run(test = "testLongArray")
    private static void testLongArrayRunner() {
        Arrays.fill(longArray, (long)0);
        testLongArray(longArray, 0);
        for (int j = 0; j < longArray.length; j++) {
            if (longArray[j] != 0x42) {
                throw new RuntimeException("For index " + j + ": " + longArray[j]);
            }
        }
    }
}
    
