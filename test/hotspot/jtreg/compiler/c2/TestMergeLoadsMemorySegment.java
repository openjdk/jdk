/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Alibaba Group Holding Limited. All rights reserved.
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
 * or visit www.oracle.com if you need any additional information or have
 * any questions.
 */

package compiler.c2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.lang.foreign.*;

/*
 * @test id=byte-array
 * @bug 8345845
 * @key randomness
 * @summary Test MergeLoads optimization for MemorySegment
 * @library /test/lib /
 * @run main/othervm -XX:+UseUnalignedAccesses compiler.c2.TestMergeLoadsMemorySegment ByteArray
 */

/*
 * @test id=char-array
 * @bug 8345845
 * @key randomness
 * @summary Test MergeLoads optimization for MemorySegment
 * @library /test/lib /
 * @run main/othervm -XX:+UseUnalignedAccesses compiler.c2.TestMergeLoadsMemorySegment CharArray
 */

/*
 * @test id=short-array
 * @bug 8345845
 * @key randomness
 * @summary Test MergeLoads optimization for MemorySegment
 * @library /test/lib /
 * @run main/othervm -XX:+UseUnalignedAccesses compiler.c2.TestMergeLoadsMemorySegment ShortArray
 */

/*
 * @test id=int-array
 * @bug 8345845
 * @key randomness
 * @summary Test MergeLoads optimization for MemorySegment
 * @library /test/lib /
 * @run main/othervm -XX:+UseUnalignedAccesses compiler.c2.TestMergeLoadsMemorySegment IntArray
 */

/*
 * @test id=long-array
 * @bug 8345845
 * @key randomness
 * @summary Test MergeLoads optimization for MemorySegment
 * @library /test/lib /
 * @run main/othervm -XX:+UseUnalignedAccesses compiler.c2.TestMergeLoadsMemorySegment LongArray
 */

/*
 * @test id=byte-buffer
 * @bug 8345845
 * @key randomness
 * @summary Test MergeLoads optimization for MemorySegment
 * @library /test/lib /
 * @run main/othervm -XX:+UseUnalignedAccesses compiler.c2.TestMergeLoadsMemorySegment ByteBuffer
 */

/*
 * @test id=byte-buffer-direct
 * @bug 8345845
 * @key randomness
 * @summary Test MergeLoads optimization for MemorySegment
 * @library /test/lib /
 * @run main/othervm -XX:+UseUnalignedAccesses compiler.c2.TestMergeLoadsMemorySegment ByteBufferDirect
 */

/*
 * @test id=native
 * @bug 8345845
 * @key randomness
 * @summary Test MergeLoads optimization for MemorySegment
 * @library /test/lib /
 * @run main/othervm -XX:+UseUnalignedAccesses compiler.c2.TestMergeLoadsMemorySegment Native
 */

public class TestMergeLoadsMemorySegment {
    static final int BACKING_SIZE = 1024 * 8;
    static final Random RANDOM = new Random(0); // Fixed seed for reproducibility

    interface MemorySegmentProvider {
        MemorySegment newMemorySegment();
    }

    static MemorySegmentProvider provider;

    public static void main(String[] args) {
        String providerName = args[0];
        provider = switch (providerName) {
            case "ByteArray"        -> TestMergeLoadsMemorySegment::newMemorySegmentOfByteArray;
            case "CharArray"        -> TestMergeLoadsMemorySegment::newMemorySegmentOfCharArray;
            case "ShortArray"       -> TestMergeLoadsMemorySegment::newMemorySegmentOfShortArray;
            case "IntArray"         -> TestMergeLoadsMemorySegment::newMemorySegmentOfIntArray;
            case "LongArray"        -> TestMergeLoadsMemorySegment::newMemorySegmentOfLongArray;
            case "ByteBuffer"       -> TestMergeLoadsMemorySegment::newMemorySegmentOfByteBuffer;
            case "ByteBufferDirect" -> TestMergeLoadsMemorySegment::newMemorySegmentOfByteBufferDirect;
            case "Native"           -> TestMergeLoadsMemorySegment::newMemorySegmentOfNative;
            default -> throw new RuntimeException("Test argument not recognized: " + providerName);
        };

        runAllTests();
    }

    // MemorySegment providers
    static MemorySegment newMemorySegmentOfByteArray() {
        byte[] arr = new byte[BACKING_SIZE];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (byte)RANDOM.nextInt();
        }
        return MemorySegment.ofArray(arr);
    }

    static MemorySegment newMemorySegmentOfCharArray() {
        char[] arr = new char[BACKING_SIZE / 2];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (char)RANDOM.nextInt();
        }
        return MemorySegment.ofArray(arr);
    }

    static MemorySegment newMemorySegmentOfShortArray() {
        short[] arr = new short[BACKING_SIZE / 2];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (short)RANDOM.nextInt();
        }
        return MemorySegment.ofArray(arr);
    }

    static MemorySegment newMemorySegmentOfIntArray() {
        int[] arr = new int[BACKING_SIZE / 4];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = RANDOM.nextInt();
        }
        return MemorySegment.ofArray(arr);
    }

    static MemorySegment newMemorySegmentOfLongArray() {
        long[] arr = new long[BACKING_SIZE / 8];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = RANDOM.nextLong();
        }
        return MemorySegment.ofArray(arr);
    }

    static MemorySegment newMemorySegmentOfByteBuffer() {
        ByteBuffer buf = ByteBuffer.allocate(BACKING_SIZE);
        for (int i = 0; i < BACKING_SIZE; i++) {
            buf.put(i, (byte)RANDOM.nextInt());
        }
        return MemorySegment.ofBuffer(buf);
    }

    static MemorySegment newMemorySegmentOfByteBufferDirect() {
        ByteBuffer buf = ByteBuffer.allocateDirect(BACKING_SIZE);
        for (int i = 0; i < BACKING_SIZE; i++) {
            buf.put(i, (byte)RANDOM.nextInt());
        }
        return MemorySegment.ofBuffer(buf);
    }

    static MemorySegment newMemorySegmentOfNative() {
        MemorySegment seg = Arena.ofAuto().allocate(BACKING_SIZE, 8);
        for (int i = 0; i < BACKING_SIZE; i++) {
            seg.set(ValueLayout.JAVA_BYTE, i, (byte)RANDOM.nextInt());
        }
        return seg;
    }

    static void runAllTests() {
        int errors = 0;

        // Create MemorySegment once before the loop
        MemorySegment seg = provider.newMemorySegment();
        
        // Capture expected values before compilation
        int expected4LE = getExpectedIntLE(seg, 0);
        long expected8LE = getExpectedLongLE(seg, 0);
        int expected4BE = getExpectedIntBE(seg, 0);
        long expected8BE = getExpectedLongBE(seg, 0);

        // Warm up and test: 4-byte little-endian merge
        System.out.println("Test 1: 4-byte little-endian merge");
        int result4LE = 0;
        for (int i = 0; i < 100_000; i++) {
            result4LE = get4BytesLE(seg, 0);
        }
        if (result4LE != expected4LE) {
            System.out.println("ERROR: 4-byte LE mismatch: " + Integer.toHexString(result4LE) + " != " + Integer.toHexString(expected4LE));
            errors++;
        }

        // Warm up and test: 8-byte little-endian merge
        System.out.println("Test 2: 8-byte little-endian merge");
        long result8LE = 0;
        for (int i = 0; i < 100_000; i++) {
            result8LE = get8BytesLE(seg, 0);
        }
        if (result8LE != expected8LE) {
            System.out.println("ERROR: 8-byte LE mismatch: " + Long.toHexString(result8LE) + " != " + Long.toHexString(expected8LE));
            errors++;
        }

        // Warm up and test: 4-byte big-endian merge
        System.out.println("Test 3: 4-byte big-endian merge");
        int result4BE = 0;
        for (int i = 0; i < 100_000; i++) {
            result4BE = get4BytesBE(seg, 0);
        }
        if (result4BE != expected4BE) {
            System.out.println("ERROR: 4-byte BE mismatch: " + Integer.toHexString(result4BE) + " != " + Integer.toHexString(expected4BE));
            errors++;
        }

        // Warm up and test: 8-byte big-endian merge
        System.out.println("Test 4: 8-byte big-endian merge");
        long result8BE = 0;
        for (int i = 0; i < 100_000; i++) {
            result8BE = get8BytesBE(seg, 0);
        }
        if (result8BE != expected8BE) {
            System.out.println("ERROR: 8-byte BE mismatch: " + Long.toHexString(result8BE) + " != " + Long.toHexString(expected8BE));
            errors++;
        }

        if (errors > 0) {
            throw new RuntimeException("Test failed with " + errors + " errors");
        }
        System.out.println("PASSED");
    }

    // Manual byte merging (should be optimized by MergeLoads)
    static int get4BytesLE(MemorySegment seg, long offset) {
        return  (seg.get(ValueLayout.JAVA_BYTE, offset) & 0xff) |
               ((seg.get(ValueLayout.JAVA_BYTE, offset + 1) & 0xff) << 8) |
               ((seg.get(ValueLayout.JAVA_BYTE, offset + 2) & 0xff) << 16) |
               ((seg.get(ValueLayout.JAVA_BYTE, offset + 3) & 0xff) << 24);
    }

    static long get8BytesLE(MemorySegment seg, long offset) {
        return  ((long)(seg.get(ValueLayout.JAVA_BYTE, offset) & 0xff)) |
               (((long)(seg.get(ValueLayout.JAVA_BYTE, offset + 1) & 0xff)) << 8) |
               (((long)(seg.get(ValueLayout.JAVA_BYTE, offset + 2) & 0xff)) << 16) |
               (((long)(seg.get(ValueLayout.JAVA_BYTE, offset + 3) & 0xff)) << 24) |
               (((long)(seg.get(ValueLayout.JAVA_BYTE, offset + 4) & 0xff)) << 32) |
               (((long)(seg.get(ValueLayout.JAVA_BYTE, offset + 5) & 0xff)) << 40) |
               (((long)(seg.get(ValueLayout.JAVA_BYTE, offset + 6) & 0xff)) << 48) |
               (((long)(seg.get(ValueLayout.JAVA_BYTE, offset + 7) & 0xff)) << 56);
    }

    static int get4BytesBE(MemorySegment seg, long offset) {
        return  ((seg.get(ValueLayout.JAVA_BYTE, offset) & 0xff) << 24) |
               ((seg.get(ValueLayout.JAVA_BYTE, offset + 1) & 0xff) << 16) |
               ((seg.get(ValueLayout.JAVA_BYTE, offset + 2) & 0xff) << 8) |
                (seg.get(ValueLayout.JAVA_BYTE, offset + 3) & 0xff);
    }

    static long get8BytesBE(MemorySegment seg, long offset) {
        return  (((long)(seg.get(ValueLayout.JAVA_BYTE, offset) & 0xff)) << 56) |
                (((long)(seg.get(ValueLayout.JAVA_BYTE, offset + 1) & 0xff)) << 48) |
                (((long)(seg.get(ValueLayout.JAVA_BYTE, offset + 2) & 0xff)) << 40) |
                (((long)(seg.get(ValueLayout.JAVA_BYTE, offset + 3) & 0xff)) << 32) |
                (((long)(seg.get(ValueLayout.JAVA_BYTE, offset + 4) & 0xff)) << 24) |
                (((long)(seg.get(ValueLayout.JAVA_BYTE, offset + 5) & 0xff)) << 16) |
                (((long)(seg.get(ValueLayout.JAVA_BYTE, offset + 6) & 0xff)) << 8) |
                 ((long)(seg.get(ValueLayout.JAVA_BYTE, offset + 7) & 0xff));
    }

    // Direct MemorySegment access (expected results)
    static int getExpectedIntLE(MemorySegment seg, long offset) {
        return seg.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), offset);
    }

    static long getExpectedLongLE(MemorySegment seg, long offset) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), offset);
    }

    static int getExpectedIntBE(MemorySegment seg, long offset) {
        return seg.get(ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), offset);
    }

    static long getExpectedLongBE(MemorySegment seg, long offset) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), offset);
    }
}
