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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ByteBufferUtils {

    private ByteBufferUtils() {}

    public static void assertEquals(ByteBuffer expectedBuffer, ByteBuffer actualBuffer, String message) {
        assertEquals(bytes(expectedBuffer), bytes(actualBuffer), message);
    }

    public static void assertEquals(byte[] expectedBytes, ByteBuffer actualBuffer, String message) {
        assertEquals(expectedBytes, bytes(actualBuffer), message);
    }

    public static void assertEquals(byte[] expectedBytes, byte[] actualBytes, String message) {
        Objects.requireNonNull(expectedBytes);
        Objects.requireNonNull(actualBytes);
        int mismatchIndex = Arrays.mismatch(expectedBytes, actualBytes);
        if (mismatchIndex >= 0) {
            Byte expectedByte = mismatchIndex >= expectedBytes.length ? null : expectedBytes[mismatchIndex];
            Byte actualByte = mismatchIndex >= actualBytes.length ? null : actualBytes[mismatchIndex];
            String extendedMessage = String.format(
                    "%s" +
                            "array contents differ at index [%s], expected: <%s> but was: <%s>%n" +
                            "expected: %s%n" +
                            "actual:   %s%n",
                    message == null ? "" : (message + ": "),
                    mismatchIndex, expectedByte, actualByte,
                    prettyPrintBytes(expectedBytes),
                    prettyPrintBytes(actualBytes));
            throw new AssertionError(extendedMessage);
        }
    }

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        return bytes;
    }

    private static String prettyPrintBytes(byte[] bytes) {
        return IntStream.range(0, bytes.length)
                .mapToObj(i -> "" + bytes[i])
                .collect(Collectors.joining(", ", "[", "]"));
    }

    public static int findLengthExceedingMaxMemory() {
        long memoryLength = Runtime.getRuntime().maxMemory();
        double length = Math.ceil(1.5D * memoryLength);
        if (length < 1 || length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Bogus or excessive memory: " + memoryLength);
        }
        return (int) length;
    }

    public static byte[] byteArrayOfLength(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

}
