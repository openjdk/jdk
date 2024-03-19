/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

/*
 * @test
 * @bug 8077559 8321180
 * @summary Tests Compact String for maximum size strings
 * @requires os.maxMemory >= 8g & vm.bits == 64
 * @requires vm.flagless
 * @run junit/othervm -XX:+CompactStrings -Xmx8g MaxSizeUTF16String
 * @run junit/othervm -XX:-CompactStrings -Xmx8g MaxSizeUTF16String
 * @run junit/othervm -Xcomp -Xmx8g MaxSizeUTF16String
 */

public class MaxSizeUTF16String {

    private final static int MAX_UTF16_STRING_LENGTH = Integer.MAX_VALUE / 2;

    private final static String EXPECTED_OOME_MESSAGE = "UTF16 String size is";
    private final static String EXPECTED_VM_LIMIT_MESSAGE = "Requested array size exceeds VM limit";
    private final static String UNEXPECTED_JAVA_HEAP_SPACE = "Java heap space";

    // Create a large UTF-8 byte array with a single non-latin1 character
    private static byte[] generateUTF8Data(int byteSize) {
        byte[] nonAscii = "\u0100".getBytes(StandardCharsets.UTF_8);
        byte[] arr = new byte[byteSize];
        System.arraycopy(nonAscii, 0, arr, 0, nonAscii.length); // non-latin1 at start
        return arr;
    }

    // Create a large char array with a single non-latin1 character
    private static char[] generateCharData(int size) {
        char[] nonAscii = "\u0100".toCharArray();
        char[] arr = new char[size];
        System.arraycopy(nonAscii, 0, arr, 0, nonAscii.length); // non-latin1 at start
        return arr;
    }

    @Test
    public void testMaxUTF8() {
        // Overly large UTF-8 data with 1 non-latin1 char
        final byte[] large_utf8_bytes = generateUTF8Data(MAX_UTF16_STRING_LENGTH + 1);
        int[] sizes = new int[] {
                MAX_UTF16_STRING_LENGTH + 1,
                MAX_UTF16_STRING_LENGTH,
                MAX_UTF16_STRING_LENGTH - 1};
        for (int size : sizes) {
            System.err.println("Checking max UTF16 string len: " + size);
            try {
                // Use only part of the UTF-8 byte array
                new String(large_utf8_bytes, 0, size, StandardCharsets.UTF_8);
                if (size >= MAX_UTF16_STRING_LENGTH) {
                    fail("Expected OutOfMemoryError with message prefix: " + EXPECTED_OOME_MESSAGE);
                }
            } catch (OutOfMemoryError ex) {
                if (ex.getMessage().equals(UNEXPECTED_JAVA_HEAP_SPACE)) {
                    // Insufficient heap size
                    throw ex;
                }
                if (!ex.getMessage().startsWith(EXPECTED_OOME_MESSAGE) &&
                        !ex.getMessage().startsWith(EXPECTED_VM_LIMIT_MESSAGE)) {
                    fail("Failed: Not the OutOfMemoryError expected", ex);
                }
            }
        }
    }

    @Test
    public void testMaxCharArray() {
        // Overly large UTF-8 data with 1 non-latin1 char
        final char[] large_char_array = generateCharData(MAX_UTF16_STRING_LENGTH + 1);
        int[] sizes = new int[]{
                MAX_UTF16_STRING_LENGTH + 1,
                MAX_UTF16_STRING_LENGTH,
                MAX_UTF16_STRING_LENGTH - 1};
        for (int size : sizes) {
            System.err.println("Checking max UTF16 string len: " + size);
            try {
                // Large char array with 1 non-latin1 char
                new String(large_char_array, 0, size);
                if (size >= MAX_UTF16_STRING_LENGTH) {
                    fail("Expected OutOfMemoryError with message prefix: " + EXPECTED_OOME_MESSAGE);
                }
            } catch (OutOfMemoryError ex) {
                if (ex.getMessage().equals(UNEXPECTED_JAVA_HEAP_SPACE)) {
                    // Insufficient heap size
                    throw ex;
                }
                if (!ex.getMessage().startsWith(EXPECTED_OOME_MESSAGE) &&
                        !ex.getMessage().startsWith(EXPECTED_VM_LIMIT_MESSAGE)) {
                    throw new RuntimeException("Wrong exception message: " + ex.getMessage(), ex);
                }
            }
        }
    }
}
