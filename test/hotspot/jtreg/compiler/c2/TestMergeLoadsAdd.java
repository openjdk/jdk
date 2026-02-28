/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8345485
 * @summary Test MergeLoads with Add operator (unsigned loads only)
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,compiler.c2.TestMergeLoadsAdd::* -XX:+OptimizeFill -XX:+UnlockDiagnosticVMOptions -XX:+UseUnalignedAccesses compiler.c2.TestMergeLoadsAdd
 */

package compiler.c2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TestMergeLoadsAdd {

    // Test merging 2 unsigned bytes with Add (should work - LoadUB)
    // Uses + instead of | for combining bytes
    public static int mergeTwoBytesWithAdd(byte[] arr) {
        // Use bitwise AND to ensure unsigned values (0-255)
        // Then use + to combine (equivalent to | when no overlap)
        return (arr[0] & 0xFF) + ((arr[1] & 0xFF) << 8);
    }

    // Test merging 4 unsigned bytes with Add
    public static int mergeFourBytesWithAdd(byte[] arr) {
        return (arr[0] & 0xFF) +
               ((arr[1] & 0xFF) << 8) +
               ((arr[2] & 0xFF) << 16) +
               ((arr[3] & 0xFF) << 24);
    }

    // Test merging 2 unsigned shorts with Add
    public static int mergeTwoShortsWithAdd(short[] arr) {
        // Use bitwise AND to ensure unsigned values (0-65535)
        return (arr[0] & 0xFFFF) + ((arr[1] & 0xFFFF) << 16);
    }

    // Test using ByteBuffer with little-endian order and + operator
    public static int mergeWithAddByteBuffer(ByteBuffer buf) {
        buf.order(ByteOrder.LITTLE_ENDIAN);
        // These use getUnsignedByte which uses LoadUB
        int b0 = buf.get() & 0xFF;
        int b1 = buf.get() & 0xFF;
        int b2 = buf.get() & 0xFF;
        int b3 = buf.get() & 0xFF;
        return b0 + (b1 << 8) + (b2 << 16) + (b3 << 24);
    }

    // Reference implementation using |
    public static int mergeWithOr(byte[] arr) {
        return (arr[0] & 0xFF) |
               ((arr[1] & 0xFF) << 8) |
               ((arr[2] & 0xFF) << 16) |
               ((arr[3] & 0xFF) << 24);
    }

    public static void main(String[] args) {
        byte[] testArr = new byte[]{0x12, 0x34, 0x56, 0x78, (byte)0x9A, (byte)0xBC, (byte)0xDE, (byte)0xF0};
        short[] testShorts = new short[]{0x1234, 0x5678};

        // Warmup
        for (int i = 0; i < 10000; i++) {
            mergeTwoBytesWithAdd(testArr);
            mergeFourBytesWithAdd(testArr);
            mergeTwoShortsWithAdd(testShorts);
            mergeWithOr(testArr);
        }

        // Test 2-byte merge with Add
        int result2 = mergeTwoBytesWithAdd(testArr);
        int expected2 = 0x3412;  // little-endian: 0x12 + (0x34 << 8)
        if (result2 != expected2) {
            throw new RuntimeException("2-byte Add merge failed: got " + Integer.toHexString(result2) +
                                       ", expected " + Integer.toHexString(expected2));
        }

        // Test 4-byte merge with Add
        int result4 = mergeFourBytesWithAdd(testArr);
        int expected4 = 0x78563412;  // little-endian
        if (result4 != expected4) {
            throw new RuntimeException("4-byte Add merge failed: got " + Integer.toHexString(result4) +
                                       ", expected " + Integer.toHexString(expected4));
        }

        // Test that Add gives same result as Or
        int resultAdd = mergeFourBytesWithAdd(testArr);
        int resultOr = mergeWithOr(testArr);
        if (resultAdd != resultOr) {
            throw new RuntimeException("Add and Or results differ: Add=" + Integer.toHexString(resultAdd) +
                                       ", Or=" + Integer.toHexString(resultOr));
        }

        // Test 2-short merge with Add
        int resultShorts = mergeTwoShortsWithAdd(testShorts);
        int expectedShorts = 0x56781234;  // little-endian
        if (resultShorts != expectedShorts) {
            throw new RuntimeException("2-short Add merge failed: got " + Integer.toHexString(resultShorts) +
                                       ", expected " + Integer.toHexString(expectedShorts));
        }

        // Test with ByteBuffer
        ByteBuffer buf = ByteBuffer.wrap(testArr);
        int resultBuf = mergeWithAddByteBuffer(buf);
        if (resultBuf != expected4) {
            throw new RuntimeException("ByteBuffer Add merge failed: got " + Integer.toHexString(resultBuf) +
                                       ", expected " + Integer.toHexString(expected4));
        }

        System.out.println("All tests passed!");
    }
}
