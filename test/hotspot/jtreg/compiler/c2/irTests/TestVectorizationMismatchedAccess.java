/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;
import jdk.internal.misc.Unsafe;
import java.util.Objects;
import java.util.Random;

/*
 * @test
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestVectorizationMismatchedAccess
 */

public class TestVectorizationMismatchedAccess {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
    }

    static int size = 1024;
    static byte[] byteArray = new byte[size * 8];
    static long[] longArray = new long[size];
    static long baseOffset = 0;
    static long baseOffHeap = UNSAFE.allocateMemory(size * 8);

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteLong1(byte[] dest, long[] src) {
        for (int i = 0; i < src.length; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i, src[i]);
        }
    }

    @Run(test = "testByteLong1")
    public static void testByteLong1_runner() {
        testByteLong1(byteArray, longArray);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteLong2(byte[] dest, long[] src) {
        for (int i = 1; i < src.length; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i - 1), src[i]);
        }
    }

    @Run(test = "testByteLong2")
    public static void testByteLong2_runner() {
        testByteLong2(byteArray, longArray);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteLong3(byte[] dest, long[] src) {
        for (int i = 0; i < src.length - 1; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i + 1), src[i]);
        }
    }

    @Run(test = "testByteLong3")
    public static void testByteLong3_runner() {
        testByteLong3(byteArray, longArray);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteLong4(byte[] dest, long[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, 8 * i + baseOffset, src[i]);
        }
    }

    @Run(test = "testByteLong4")
    public static void testByteLong4_runner() {
        baseOffset = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
        testByteLong4(byteArray, longArray, 1, size);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteLong5(byte[] dest, long[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i + baseOffset), src[i]);
        }
    }

    @Run(test = "testByteLong5")
    public static void testByteLong5_runner() {
        baseOffset = 1;
        testByteLong5(byteArray, longArray, 0, size-1);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteByte1(byte[] dest, byte[] src) {
        for (int i = 0; i < src.length / 8; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i, UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i));
        }
    }

    @Run(test = "testByteByte1")
    public static void testByteByte1_runner() {
        testByteByte1(byteArray, byteArray);
    }

    // It would be legal to vectorize this one but it's not currently
    @Test
    //@IR(counts = { IRNode.LOAD_VECTOR, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteByte2(byte[] dest, byte[] src) {
        for (int i = 1; i < src.length / 8; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i - 1), src[i]);
        }
    }

    @Run(test = "testByteByte2")
    public static void testByteByte2_runner() {
        testByteByte2(byteArray, byteArray);
    }

    @Test
    @IR(failOn = { IRNode.LOAD_VECTOR, IRNode.STORE_VECTOR })
    public static void testByteByte3(byte[] dest, byte[] src) {
        for (int i = 0; i < src.length / 8 - 1; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i + 1), src[i]);
        }
    }

    @Run(test = "testByteByte3")
    public static void testByteByte3_runner() {
        testByteByte3(byteArray, byteArray);
    }

    @Test
    @IR(failOn = { IRNode.LOAD_VECTOR, IRNode.STORE_VECTOR })
    public static void testByteByte4(byte[] dest, byte[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, 8 * i + baseOffset, src[i]);
        }
    }

    @Run(test = "testByteByte4")
    public static void testByteByte4_runner() {
        baseOffset = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
        testByteByte4(byteArray, byteArray, 1, size);
    }

    @Test
    @IR(failOn = { IRNode.LOAD_VECTOR, IRNode.STORE_VECTOR })
    public static void testByteByte5(byte[] dest, byte[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i + baseOffset), src[i]);
        }
    }

    @Run(test = "testByteByte5")
    public static void testByteByte5_runner() {
        baseOffset = 1;
        testByteByte5(byteArray, byteArray, 0, size-1);
    }
    
    @Test
    @IR(counts = { IRNode.LOAD_VECTOR, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testOffHeapLong1(long dest, long[] src) {
        for (int i = 0; i < src.length; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8 * i, src[i]);
        }
    }

    @Run(test = "testOffHeapLong1")
    public static void testOffHeapLong1_runner() {
        testOffHeapLong1(baseOffHeap, longArray);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testOffHeapLong2(long dest, long[] src) {
        for (int i = 1; i < src.length; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8 * (i - 1), src[i]);
        }
    }

    @Run(test = "testOffHeapLong2")
    public static void testOffHeapLong2_runner() {
        testOffHeapLong2(baseOffHeap, longArray);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testOffHeapLong3(long dest, long[] src) {
        for (int i = 0; i < src.length - 1; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8 * (i + 1), src[i]);
        }
    }

    @Run(test = "testOffHeapLong3")
    public static void testOffHeapLong3_runner() {
        testOffHeapLong3(baseOffHeap, longArray);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testOffHeapLong4(long dest, long[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8 * i + baseOffset, src[i]);
        }
    }

    @Run(test = "testOffHeapLong4")
    public static void testOffHeapLong4_runner() {
        baseOffset = 8;
        testOffHeapLong4(baseOffHeap, longArray, 1, size);
    }
}
