/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertFalse;

/*
 * @test
 * @bug 8007799 8176379 8331342
 * @summary Test that getting a Mime encoder where line length rounds down to 0
 *          produces a non line separated encoder (RFC 4648). Ensure correctness
 *          of encoded data with the retrieved encoder.
 * @run junit Base64GetEncoderTest
 */

public class Base64GetEncoderTest {

    // Test data that contains a short and a long byte array
    private static final byte[][] TEST_INPUT =
            {"foo".getBytes(US_ASCII), "quux".repeat(21).getBytes(US_ASCII)};
    private static Base64.Encoder encoder;

    // Retrieved encoder should not have line separators
    @ParameterizedTest
    @MethodSource("roundsToZeroOrSmaller")
    public void getMimeEncoderTest(int lineLength) throws IOException {
        encoder = Base64.getMimeEncoder(lineLength, "$$$".getBytes(US_ASCII));
        // Test correctness of encoder
        for (byte[] data : TEST_INPUT) {
            encodeToStringTest(data);
            wrapTest(data);
        }
    }

    // Line lengths that when rounded down to the nearest multiple of 4,
    // should all produce lineLength <= 0
    private static int[] roundsToZeroOrSmaller() {
        return new int[]{-4, -3, -2, -1, 0, 1, 2, 3};
    }

    // Ensure correctness of the Encoder by testing Encoder.wrap
    private static void wrapTest(byte[] inputData)
            throws IOException {
        ByteArrayOutputStream encodingStream = new ByteArrayOutputStream();
        OutputStream encoding = encoder.wrap(encodingStream);
        encoding.write(inputData);
        encoding.close();
        String base64EncodedString = encodingStream.toString(US_ASCII);
        assertFalse(base64EncodedString.contains("$$$"),
                failMessage("Encoder.wrap()",  base64EncodedString, inputData));
    }

    // Ensure correctness of the Encoder by testing Encoder.encodeToString
    private static void encodeToStringTest(byte[] inputData) {
        String base64EncodedString = encoder.encodeToString(inputData);
        assertFalse(base64EncodedString.contains("$$$"),
                failMessage("Encoder.encodeToString()", base64EncodedString, inputData));
    }

    // Utility to produce a helpful error message
    private static String failMessage(String methodName, String encodedString, byte[] bytesIn) {
        return "\n%s incorrectly produced the String: \"%s\"\n".formatted(methodName, encodedString) +
                "which has line separators. The input String was: \"%s\"\n".formatted(new String(bytesIn, US_ASCII)) +
                "Ensure that getMimeEncoder() returned the correct encoder";
    }
}
