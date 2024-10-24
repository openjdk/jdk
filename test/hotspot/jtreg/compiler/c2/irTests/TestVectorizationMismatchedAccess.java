/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
        if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) {
            throw new RuntimeException("fix test that was written for a little endian platform");
        }
        TestFramework.runWithFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
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
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"})
    // 32-bit: offsets are badly aligned (UNSAFE.ARRAY_BYTE_BASE_OFFSET is 4 byte aligned, but not 8 byte aligned).
    //         might get fixed with JDK-8325155.
    public static void testByteLong1a(byte[] dest, long[] src) {
        for (int i = 0; i < src.length; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i, src[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"})
    // 32-bit: address has ConvL2I for cast of long to address, not supported.
    public static void testByteLong1b(byte[] dest, long[] src) {
        for (int i = 0; i < src.length; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8L * i, src[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"})
    public static void testByteLong1c(byte[] dest, long[] src) {
        long base = 64; // make sure it is big enough and 8 byte aligned (required for 32-bit)
        for (int i = 0; i < src.length - 8; i++) {
            UNSAFE.putLongUnaligned(dest, base + 8 * i, src[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"})
    // 32-bit: address has ConvL2I for cast of long to address, not supported.
    public static void testByteLong1d(byte[] dest, long[] src) {
        long base = 64; // make sure it is big enough and 8 byte aligned (required for 32-bit)
        for (int i = 0; i < src.length - 8; i++) {
            UNSAFE.putLongUnaligned(dest, base + 8L * i, src[i]);
        }
    }

    @Run(test = {"testByteLong1a", "testByteLong1b", "testByteLong1c", "testByteLong1d"})
    public static void testByteLong1_runner() {
        runAndVerify(() -> testByteLong1a(byteArray, longArray), 0);
        runAndVerify(() -> testByteLong1b(byteArray, longArray), 0);
        testByteLong1c(byteArray, longArray);
        testByteLong1d(byteArray, longArray);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"})
    // 32-bit: offsets are badly aligned (UNSAFE.ARRAY_BYTE_BASE_OFFSET is 4 byte aligned, but not 8 byte aligned).
    //         might get fixed with JDK-8325155.
    public static void testByteLong2a(byte[] dest, long[] src) {
        for (int i = 1; i < src.length; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i - 1), src[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"})
    // 32-bit: address has ConvL2I for cast of long to address, not supported.
    public static void testByteLong2b(byte[] dest, long[] src) {
        for (int i = 1; i < src.length; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8L * (i - 1), src[i]);
        }
    }

    @Run(test = {"testByteLong2a", "testByteLong2b"})
    public static void testByteLong2_runner() {
        runAndVerify(() -> testByteLong2a(byteArray, longArray), -8);
        runAndVerify(() -> testByteLong2b(byteArray, longArray), -8);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"})
    // 32-bit: offsets are badly aligned (UNSAFE.ARRAY_BYTE_BASE_OFFSET is 4 byte aligned, but not 8 byte aligned).
    //         might get fixed with JDK-8325155.
    public static void testByteLong3a(byte[] dest, long[] src) {
        for (int i = 0; i < src.length - 1; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i + 1), src[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"})
    // 32-bit: address has ConvL2I for cast of long to address, not supported.
    public static void testByteLong3b(byte[] dest, long[] src) {
        for (int i = 0; i < src.length - 1; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8L * (i + 1), src[i]);
        }
    }

    @Run(test = {"testByteLong3a", "testByteLong3b"})
    public static void testByteLong3_runner() {
        runAndVerify(() -> testByteLong3a(byteArray, longArray), 8);
        runAndVerify(() -> testByteLong3b(byteArray, longArray), 8);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"})
    // 32-bit: offsets are badly aligned (UNSAFE.ARRAY_BYTE_BASE_OFFSET is 4 byte aligned, but not 8 byte aligned).
    //         might get fixed with JDK-8325155.
    // AlignVector cannot guarantee that invar is aligned.
    public static void testByteLong4a(byte[] dest, long[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, 8 * i + baseOffset, src[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"})
    // 32-bit: address has ConvL2I for cast of long to address, not supported.
    // AlignVector cannot guarantee that invar is aligned.
    public static void testByteLong4b(byte[] dest, long[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, 8L * i + baseOffset, src[i]);
        }
    }

    @Run(test = {"testByteLong4a", "testByteLong4b"})
    public static void testByteLong4_runner() {
        baseOffset = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
        runAndVerify(() -> testByteLong4a(byteArray, longArray, 0, size), 0);
        runAndVerify(() -> testByteLong4b(byteArray, longArray, 0, size), 0);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"})
    // 32-bit: offsets are badly aligned (UNSAFE.ARRAY_BYTE_BASE_OFFSET is 4 byte aligned, but not 8 byte aligned).
    //         might get fixed with JDK-8325155.
    public static void testByteLong5a(byte[] dest, long[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i + baseOffset), src[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"})
    // 32-bit: address has ConvL2I for cast of long to address, not supported.
    public static void testByteLong5b(byte[] dest, long[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8L * (i + baseOffset), src[i]);
        }
    }

    @Run(test = {"testByteLong5a", "testByteLong5b"})
    public static void testByteLong5_runner() {
        baseOffset = 1;
        runAndVerify(() -> testByteLong5a(byteArray, longArray, 0, size-1), 8);
        runAndVerify(() -> testByteLong5b(byteArray, longArray, 0, size-1), 8);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"})
    // 32-bit: offsets are badly aligned (UNSAFE.ARRAY_BYTE_BASE_OFFSET is 4 byte aligned, but not 8 byte aligned).
    //         might get fixed with JDK-8325155.
    public static void testByteByte1a(byte[] dest, byte[] src) {
        for (int i = 0; i < src.length / 8; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i, UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i));
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"})
    // 32-bit: address has ConvL2I for cast of long to address, not supported.
    public static void testByteByte1b(byte[] dest, byte[] src) {
        for (int i = 0; i < src.length / 8; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8L * i, UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8L * i));
        }
    }

    @Run(test = {"testByteByte1a", "testByteByte1b"})
    public static void testByteByte1_runner() {
        runAndVerify2(() -> testByteByte1a(byteArray, byteArray), 0);
        runAndVerify2(() -> testByteByte1b(byteArray, byteArray), 0);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"})
    // 32-bit: offsets are badly aligned (UNSAFE.ARRAY_BYTE_BASE_OFFSET is 4 byte aligned, but not 8 byte aligned).
    //         might get fixed with JDK-8325155.
    public static void testByteByte2a(byte[] dest, byte[] src) {
        for (int i = 1; i < src.length / 8; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i - 1), UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i));
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
        applyIfCPUFeatureOr = {"sse2", "true", "asimd", "true"},
        applyIfPlatform = {"64-bit", "true"})
    // 32-bit: address has ConvL2I for cast of long to address, not supported.
    public static void testByteByte2b(byte[] dest, byte[] src) {
        for (int i = 1; i < src.length / 8; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8L * (i - 1), UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8L * i));
        }
    }

    @Run(test = {"testByteByte2a", "testByteByte2b"})
    public static void testByteByte2_runner() {
        runAndVerify2(() -> testByteByte2a(byteArray, byteArray), -8);
        runAndVerify2(() -> testByteByte2b(byteArray, byteArray), -8);
    }

    @Test
    @IR(failOn = { IRNode.LOAD_VECTOR_L, IRNode.STORE_VECTOR })
    public static void testByteByte3a(byte[] dest, byte[] src) {
        for (int i = 0; i < src.length / 8 - 1; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i + 1), UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i));
        }
    }

    @Test
    @IR(failOn = { IRNode.LOAD_VECTOR_L, IRNode.STORE_VECTOR })
    public static void testByteByte3b(byte[] dest, byte[] src) {
        for (int i = 0; i < src.length / 8 - 1; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8L * (i + 1), UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8L * i));
        }
    }

    @Run(test = {"testByteByte3a", "testByteByte3b"})
    public static void testByteByte3_runner() {
        runAndVerify2(() -> testByteByte3a(byteArray, byteArray), 8);
        runAndVerify2(() -> testByteByte3b(byteArray, byteArray), 8);
    }

    @Test
    @IR(failOn = { IRNode.LOAD_VECTOR_L, IRNode.STORE_VECTOR })
    public static void testByteByte4a(byte[] dest, byte[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, 8 * i + baseOffset, UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i));
        }
    }

    @Test
    @IR(failOn = { IRNode.LOAD_VECTOR_L, IRNode.STORE_VECTOR })
    public static void testByteByte4b(byte[] dest, byte[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, 8L * i + baseOffset, UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8L * i));
        }
    }

    @Run(test = {"testByteByte4a", "testByteByte4b"})
    public static void testByteByte4_runner() {
        baseOffset = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
        runAndVerify2(() -> testByteByte4a(byteArray, byteArray, 0, size), 0);
        runAndVerify2(() -> testByteByte4b(byteArray, byteArray, 0, size), 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD_VECTOR_L, IRNode.STORE_VECTOR })
    public static void testByteByte5a(byte[] dest, byte[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * (i + baseOffset), UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8 * i));
        }
    }

    @Test
    @IR(failOn = { IRNode.LOAD_VECTOR_L, IRNode.STORE_VECTOR })
    public static void testByteByte5b(byte[] dest, byte[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8L * (i + baseOffset), UNSAFE.getLongUnaligned(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + 8L * i));
        }
    }

    @Run(test = {"testByteByte5a", "testByteByte5b"})
    public static void testByteByte5_runner() {
        baseOffset = 1;
        runAndVerify2(() -> testByteByte5a(byteArray, byteArray, 0, size-1), 8);
        runAndVerify2(() -> testByteByte5b(byteArray, byteArray, 0, size-1), 8);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, "=0", IRNode.STORE_VECTOR, "=0" }) // temporary
    // @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    // FAILS: adr is CastX2P(dest + 8 * (i + int_con))
    // See: JDK-8331576
    public static void testOffHeapLong1a(long dest, long[] src) {
        for (int i = 0; i < src.length; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8 * i, src[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, "=0", IRNode.STORE_VECTOR, "=0" }) // temporary
    // @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    // FAILS: adr is CastX2P(dest + 8L * (i + int_con))
    // See: JDK-8331576
    public static void testOffHeapLong1b(long dest, long[] src) {
        for (int i = 0; i < src.length; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8L * i, src[i]);
        }
    }

    @Run(test = {"testOffHeapLong1a", "testOffHeapLong1b"})
    public static void testOffHeapLong1_runner() {
        runAndVerify3(() -> testOffHeapLong1a(baseOffHeap, longArray), 0);
        runAndVerify3(() -> testOffHeapLong1b(baseOffHeap, longArray), 0);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, "=0", IRNode.STORE_VECTOR, "=0" }) // temporary
    // @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    // FAILS: adr is CastX2P
    // See: JDK-8331576
    public static void testOffHeapLong2a(long dest, long[] src) {
        for (int i = 1; i < src.length; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8 * (i - 1), src[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, "=0", IRNode.STORE_VECTOR, "=0" }) // temporary
    // @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    // FAILS: adr is CastX2P
    // See: JDK-8331576
    public static void testOffHeapLong2b(long dest, long[] src) {
        for (int i = 1; i < src.length; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8L * (i - 1), src[i]);
        }
    }

    @Run(test = {"testOffHeapLong2a", "testOffHeapLong2b"})
    public static void testOffHeapLong2_runner() {
        runAndVerify3(() -> testOffHeapLong2a(baseOffHeap, longArray), -8);
        runAndVerify3(() -> testOffHeapLong2b(baseOffHeap, longArray), -8);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, "=0", IRNode.STORE_VECTOR, "=0" }) // temporary
    // @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    // FAILS: adr is CastX2P
    // See: JDK-8331576
    public static void testOffHeapLong3a(long dest, long[] src) {
        for (int i = 0; i < src.length - 1; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8 * (i + 1), src[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, "=0", IRNode.STORE_VECTOR, "=0" }) // temporary
    // @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    // FAILS: adr is CastX2P
    // See: JDK-8331576
    public static void testOffHeapLong3b(long dest, long[] src) {
        for (int i = 0; i < src.length - 1; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8L * (i + 1), src[i]);
        }
    }

    @Run(test = {"testOffHeapLong3a", "testOffHeapLong3b"})
    public static void testOffHeapLong3_runner() {
        runAndVerify3(() -> testOffHeapLong3a(baseOffHeap, longArray), 8);
        runAndVerify3(() -> testOffHeapLong3b(baseOffHeap, longArray), 8);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, "=0", IRNode.STORE_VECTOR, "=0" }) // temporary
    // @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
    //     applyIf = {"AlignVector", "false"})
    // FAILS: adr is CastX2P
    // See: JDK-8331576
    // AlignVector cannot guarantee that invar is aligned.
    public static void testOffHeapLong4a(long dest, long[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8 * i + baseOffset, src[i]);
        }
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, "=0", IRNode.STORE_VECTOR, "=0" }) // temporary
    // @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" },
    //     applyIf = {"AlignVector", "false"})
    // FAILS: adr is CastX2P
    // See: JDK-8331576
    // AlignVector cannot guarantee that invar is aligned.
    public static void testOffHeapLong4b(long dest, long[] src, int start, int stop) {
        for (int i = start; i < stop; i++) {
            UNSAFE.putLongUnaligned(null, dest + 8L * i + baseOffset, src[i]);
        }
    }

    @Run(test = {"testOffHeapLong4a", "testOffHeapLong4b"})
    public static void testOffHeapLong4_runner() {
        baseOffset = 8;
        runAndVerify3(() -> testOffHeapLong4a(baseOffHeap, longArray, 0, size-1), 8);
        runAndVerify3(() -> testOffHeapLong4b(baseOffHeap, longArray, 0, size-1), 8);
    }
}
