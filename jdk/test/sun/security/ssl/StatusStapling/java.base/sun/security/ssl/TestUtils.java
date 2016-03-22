/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

public class TestUtils {

    // private constructor to prevent instantiation for this utility class
    private TestUtils() {
        throw new AssertionError();
    }

    public static void runTests(Map<String, TestCase> testList) {
        int testNo = 0;
        int numberFailed = 0;
        Map.Entry<Boolean, String> result;

        System.out.println("============ Tests ============");
        for (String testName : testList.keySet()) {
            System.out.println("Test " + ++testNo + ": " + testName);
            result = testList.get(testName).runTest();
            System.out.print("Result: " + (result.getKey() ? "PASS" : "FAIL"));
            System.out.println(" " +
                    (result.getValue() != null ? result.getValue() : ""));
            System.out.println("-------------------------------------------");
            if (!result.getKey()) {
                numberFailed++;
            }
        }

        System.out.println("End Results: " + (testList.size() - numberFailed) +
                " Passed" + ", " + numberFailed + " Failed.");
        if (numberFailed > 0) {
            throw new RuntimeException(
                    "One or more tests failed, see test output for details");
        }
    }

    public static void dumpBytes(byte[] data) {
        dumpBytes(ByteBuffer.wrap(data));
    }

    public static void dumpBytes(ByteBuffer data) {
        int i = 0;

        data.mark();
        while (data.hasRemaining()) {
            if (i % 16 == 0 && i != 0) {
                System.out.print("\n");
            }
            System.out.print(String.format("%02X ", data.get()));
            i++;
        }
        System.out.print("\n");
        data.reset();
    }

    public static void valueCheck(byte[] array1, byte[] array2) {
        if (!Arrays.equals(array1, array2)) {
            throw new RuntimeException("Array mismatch");
        }
    }

    // Compares a range of bytes at specific offsets in each array
    public static void valueCheck(byte[] array1, byte[] array2, int skip1,
            int skip2, int length) {
        ByteBuffer buf1 = ByteBuffer.wrap(array1);
        ByteBuffer buf2 = ByteBuffer.wrap(array2);

        // Skip past however many bytes are requested in both buffers
        buf1.position(skip1);
        buf2.position(skip2);

        // Reset the limits on each buffer to account for the length
        buf1.limit(buf1.position() + length);
        buf2.limit(buf2.position() + length);

        if (!buf1.equals(buf2)) {
            throw new RuntimeException("Array range mismatch");
        }
    }

    // Concatenate 1 or more arrays
    public static byte[] gatherBuffers(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] ar : arrays) {
            totalLength += ar != null ? ar.length : 0;
        }

        byte[] resultBuf = new byte[totalLength];
        int offset = 0;
        for (byte[] ar : arrays) {
            if (ar != null) {
                System.arraycopy(ar, 0, resultBuf, offset, ar.length);
                offset += ar.length;
            }
        }
        return resultBuf;
    }
}
