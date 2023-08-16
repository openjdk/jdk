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
import jdk.test.whitebox.WhiteBox;
import jdk.internal.misc.Unsafe;
import java.util.Random;
import java.util.Arrays;
import java.nio.ByteOrder;

/*
 * @test
 * @bug 8300258
 * @key randomness
 * @requires (os.simpleArch == "x64") | (os.simpleArch == "aarch64")
 * @summary C2: vectorization fails on simple ByteBuffer loop
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.c2.irTests.TestVectorizationMismatchedAccess
 */

public class TestVectorizationMismatchedAccess {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final Random RANDOM = Utils.getRandomInstance();
    private final static WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        Object alignVector = wb.getVMFlag("AlignVector");
        if (alignVector != null && !((Boolean)alignVector)) {
            if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) {
                throw new RuntimeException("fix test that was written for a little endian platform");
            }
            TestFramework.runWithFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
        }
    }

    static int size = 1024;
    static byte[] byteArray = new byte[size * 8];
    static long[] longArray = new long[size];
    static byte[] verifyByteArray = new byte[size * 8];
    static long[] verifyLongArray = new long[size];
    static long baseOffset = 0;
    static long baseOffHeap = UNSAFE.allocateMemory(size * 8);


    static {
        for (int i = 0; i < verifyByteArray.length; i++) {
            verifyByteArray[i] = (byte)RANDOM.nextInt(Byte.MAX_VALUE);
        }
        for (int i = 0; i < verifyLongArray.length; i++) {
            verifyLongArray[i] = 0;
            for (int j = 0; j < 8; j++) {
                verifyLongArray[i] = verifyLongArray[i] | (((long)verifyByteArray[8 * i + j]) << 8 * j);
            }
        }
    }

    static private void runAndVerify(Runnable test, int offset) {
        System.arraycopy(verifyLongArray, 0, longArray, 0, longArray.length);
        Arrays.fill(byteArray, (byte)0);
        test.run();
        int i;
        for (i = 0; i < Math.max(offset, 0); i++) {
            if (byteArray[i] != 0) {
                throw new RuntimeException("Incorrect result at " + i + " " + byteArray[i] + " != 0");
            }
        }
        for (; i < Math.min(byteArray.length + offset, byteArray.length); i++) {
            if (byteArray[i] != verifyByteArray[i - offset]) {
                throw new RuntimeException("Incorrect result at " + i + " " + byteArray[i] + " != " + verifyByteArray[i-offset]);
            }
        }
        for (; i < byteArray.length; i++) {
            if (byteArray[i] != 0) {
                throw new RuntimeException("Incorrect result at " + i + " " + byteArray[i] + " != 0");
            }
        }
    }

    static private void runAndVerify2(Runnable test, int offset) {
        System.arraycopy(verifyByteArray, 0, byteArray, 0, byteArray.length);
        test.run();
        int i;
        for (i = 0; i < Math.max(offset, 0); i++) {
            if (byteArray[i] != verifyByteArray[i]) {
                throw new RuntimeException("Incorrect result at " + i + " " + byteArray[i] + " != " + verifyByteArray[i]);
            }
        }
        for (; i < Math.min(byteArray.length + offset, byteArray.length); i++) {
            int val = offset > 0 ? verifyByteArray[(i-offset) % 8] : verifyByteArray[i-offset];
            if (byteArray[i] != val) {
                throw new RuntimeException("Incorrect result at " + i + " " + byteArray[i] + " != " + verifyByteArray[i-offset]);
            }
        }
        for (; i < byteArray.length; i++) {
            if (byteArray[i] != verifyByteArray[i]) {
                throw new RuntimeException("Incorrect result at " + i + " " + byteArray[i] + " != " + verifyByteArray[i]);
            }
        }
    }


    static private void runAndVerify3(Runnable test, int offset) {
        System.arraycopy(verifyLongArray, 0, longArray, 0, longArray.length);
        for (int i = 0; i < size * 8; i++) {
            UNSAFE.putByte(null, baseOffHeap + i, (byte)0);
        }
        test.run();
        int i;
        for (i = 0; i < Math.max(offset, 0); i++) {
            if (UNSAFE.getByte(null, baseOffHeap + i) != 0) {
                throw new RuntimeException("Incorrect result at " + i + " " + byteArray[i] + " != 0");
            }
        }
        for (; i < Math.min(size * 8 + offset, size * 8); i++) {
            if (UNSAFE.getByte(null, baseOffHeap + i) != verifyByteArray[i - offset]) {
                throw new RuntimeException("Incorrect result at " + i + " " + byteArray[i] + " != " + verifyByteArray[i-offset]);
            }
        }
        for (; i < byteArray.length; i++) {
            if (UNSAFE.getByte(null, baseOffHeap + i) != 0) {
                throw new RuntimeException("Incorrect result at " + i + " " + byteArray[i] + " != 0");
            }
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteLong1(byte[] dest, long[] src) {
        for (int i = 0; i < src.length; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i, src[i]);
        }
    }

    @Run(test = "testByteLong1")
    public static void testByteLong1_runner() {
        runAndVerify(() -> testByteLong1(byteArray, longArray), 0);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteLong2(byte[] dest, long[] src) {
        for (int i = 1; i < src.length; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i - 1), src[i]);
        }
    }

    @Run(test = "testByteLong2")
    public static void testByteLong2_runner() {
        runAndVerify(() -> testByteLong2(byteArray, longArray), -8);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteLong3(byte[] dest, long[] src) {
        for (int i = 0; i < src.length - 1; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i + 1), src[i]);
        }
    }

    @Run(test = "testByteLong3")
    public static void testByteLong3_runner() {
        runAndVerify(() -> testByteLong3(byteArray, longArray), 8);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteLong4(byte[] dest, long[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, 8 * i + baseOffset, src[i]);
        }
    }

    @Run(test = "testByteLong4")
    public static void testByteLong4_runner() {
        baseOffset = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
        runAndVerify(() -> testByteLong4(byteArray, longArray, 0, size), 0);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteLong5(byte[] dest, long[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i + baseOffset), src[i]);
        }
    }

    @Run(test = "testByteLong5")
    public static void testByteLong5_runner() {
        baseOffset = 1;
        runAndVerify(() -> testByteLong5(byteArray, longArray, 0, size-1), 8);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteByte1(byte[] dest, byte[] src) {
        for (int i = 0; i < src.length / 8; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i, UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i));
        }
    }

    @Run(test = "testByteByte1")
    public static void testByteByte1_runner() {
        runAndVerify2(() -> testByteByte1(byteArray, byteArray), 0);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteByte2(byte[] dest, byte[] src) {
        for (int i = 1; i < src.length / 8; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i - 1), UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i));
        }
    }

    @Run(test = "testByteByte2")
    public static void testByteByte2_runner() {
        runAndVerify2(() -> testByteByte2(byteArray, byteArray), -8);
    }

    @Test
    @IR(failOn = { IRNode.LOAD_VECTOR_L, IRNode.STORE_VECTOR })
    public static void testByteByte3(byte[] dest, byte[] src) {
        for (int i = 0; i < src.length / 8 - 1; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i + 1), UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i));
        }
    }

    @Run(test = "testByteByte3")
    public static void testByteByte3_runner() {
        runAndVerify2(() -> testByteByte3(byteArray, byteArray), 8);
    }

    @Test
    @IR(failOn = { IRNode.LOAD_VECTOR_L, IRNode.STORE_VECTOR })
    public static void testByteByte4(byte[] dest, byte[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, 8 * i + baseOffset, UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i));
        }
    }

    @Run(test = "testByteByte4")
    public static void testByteByte4_runner() {
        baseOffset = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
        runAndVerify2(() -> testByteByte4(byteArray, byteArray, 0, size), 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD_VECTOR_L, IRNode.STORE_VECTOR })
    public static void testByteByte5(byte[] dest, byte[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i + baseOffset), UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i));
        }
    }

    @Run(test = "testByteByte5")
    public static void testByteByte5_runner() {
        baseOffset = 1;
        runAndVerify2(() -> testByteByte5(byteArray, byteArray, 0, size-1), 8);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testOffHeapLong1(long dest, long[] src) {
        for (int i = 0; i < src.length; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8 * i, src[i]);
        }
    }

    @Run(test = "testOffHeapLong1")
    public static void testOffHeapLong1_runner() {
        runAndVerify3(() -> testOffHeapLong1(baseOffHeap, longArray), 0);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testOffHeapLong2(long dest, long[] src) {
        for (int i = 1; i < src.length; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8 * (i - 1), src[i]);
        }
    }

    @Run(test = "testOffHeapLong2")
    public static void testOffHeapLong2_runner() {
        runAndVerify3(() -> testOffHeapLong2(baseOffHeap, longArray), -8);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testOffHeapLong3(long dest, long[] src) {
        for (int i = 0; i < src.length - 1; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8 * (i + 1), src[i]);
        }
    }

    @Run(test = "testOffHeapLong3")
    public static void testOffHeapLong3_runner() {
        runAndVerify3(() -> testOffHeapLong3(baseOffHeap, longArray), 8);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testOffHeapLong4(long dest, long[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8 * i + baseOffset, src[i]);
        }
    }

    @Run(test = "testOffHeapLong4")
    public static void testOffHeapLong4_runner() {
        baseOffset = 8;
        runAndVerify3(() -> testOffHeapLong4(baseOffHeap, longArray, 0, size-1), 8);
    }
}
