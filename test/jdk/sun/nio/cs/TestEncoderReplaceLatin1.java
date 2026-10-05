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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import sun.nio.cs.ArrayEncoder;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8364365
 * @summary Verifies `CodingErrorAction.REPLACE` behaviour of all available
 *          character set encoders while encoding a Latin-1 character
 * @modules java.base/jdk.internal.access
 *          java.base/sun.nio.cs
 * @run junit TestEncoderReplaceLatin1
 */

class TestEncoderReplaceLatin1 {

    static Collection<Charset> charsets() {
        return Charset.availableCharsets().values();
    }

    @ParameterizedTest
    @MethodSource("charsets")
    void testEncoderReplace(Charset charset) {

        // Create an encoder
        CharsetEncoder encoder = createEncoder(charset);
        if (encoder == null) {
            return;
        }

        // Find an unmappable character to test the `REPLACE` action.
        char[] unmappable = findUnmappable(encoder);
        if (unmappable == null) {
            return;
        }

        // Configure the `REPLACE` action
        byte[] replacement = findCustomReplacement(encoder, new byte[]{(byte) unmappable[0]});
        if (replacement == null) {
            return;
        }
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE).replaceWith(replacement);

        // Verify the replacement
        System.err.println("Verifying replacement... " + Map.of(
                "unmappable", TestEncoderReplaceLatin1.prettyPrintChars(unmappable),
                "replacement", TestEncoderReplaceLatin1.prettyPrintBytes(replacement)));
        testCharsetEncoderReplace(encoder, unmappable, replacement);
        testArrayEncoderLatin1Replace(encoder, unmappable[0], replacement);

    }

    private static CharsetEncoder createEncoder(Charset charset) {
        try {
            return charset.newEncoder();
        } catch (UnsupportedOperationException _) {
            System.err.println("Could not create the character encoder!");
        }
        return null;
    }

    private static char[] findUnmappable(CharsetEncoder encoder) {
        char[] unmappable1 = {0};
        for (char c = 0; c < 0xFF; c++) {
            unmappable1[0] = c;
            boolean unmappable = !encoder.canEncode(c);
            if (unmappable) {
                return unmappable1;
            }
        }
        System.err.println("Could not find an unmappable character!");
        return null;
    }

    /**
     * Finds a {@linkplain CharsetEncoder#replacement() replacement} which is
     * different from the given unmappable and the default one.
     */
    static byte[] findCustomReplacement(CharsetEncoder encoder, byte[] unmappable) {

        // Obtain the default replacement
        byte[] replacementD = encoder.replacement();

        // Try to find a single-byte replacement
        byte[] replacement1 = {0};
        for (int i = 0; i < 0xFF; i++) {
            // Skip if the replacement is equal to the unmappable.
            // They need to be distinct to be able to determine whether the replacement has occurred.
            if (unmappable[0] == i) {
                continue;
            }
            replacement1[0] = (byte) i;
            // Skip the default value, since we're verifying if a custom one works
            if (replacement1[0] == replacementD[0]) {
                continue;
            }
            if (encoder.isLegalReplacement(replacement1)) {
                return replacement1;
            }
        }

        // Try to find a double-byte replacement
        byte[] replacement2 = {0, 0};
        for (int i = 0; i < 0xFF; i++) {
            // Skip if the replacement is equal to the unmappable.
            // They need to be distinct to be able to determine whether the replacement has occurred.
            if (unmappable[0] == i) {
                continue;
            }
            replacement2[0] = (byte) i;
            for (int j = 0; j < 0xFF; j++) {
                // Skip if the replacement is equal to the unmappable.
                // They need to be distinct to be able to determine whether the replacement has occurred.
                if (unmappable.length > 1 && unmappable[1] == j) {
                    continue;
                }
                replacement2[1] = (byte) j;
                // Skip the default value, since we're verifying if a custom one works
                if (replacementD.length > 1 && replacement2[1] == replacementD[1]) {
                    continue;
                }
                if (encoder.isLegalReplacement(replacement2)) {
                    return replacement2;
                }
            }
        }

        System.err.println("Could not find a replacement!");
        return null;

    }

    /**
     * Verifies {@linkplain CoderResult#isUnmappable() unmappable} character
     * {@linkplain CodingErrorAction#REPLACE replacement} using {@link
     * CharsetEncoder#encode(CharBuffer, ByteBuffer, boolean)
     * CharsetEncoder::encode}.
     */
    static void testCharsetEncoderReplace(CharsetEncoder encoder, char[] unmappable, byte[] replacement) {
        CharBuffer charBuffer = CharBuffer.wrap(unmappable);
        ByteBuffer byteBuffer = ByteBuffer.allocate(replacement.length);
        CoderResult coderResult = encoder.encode(charBuffer, byteBuffer, true);
        assertArrayEquals(replacement, byteBuffer.array(), () -> {
            Object context = Map.of(
                    "coderResult", coderResult,
                    "byteBuffer.position()", byteBuffer.position(),
                    "byteBuffer.array()", prettyPrintBytes(byteBuffer.array()),
                    "unmappable", prettyPrintChars(unmappable),
                    "replacement", prettyPrintBytes(replacement));
            return "Unexpected `CharsetEncoder::encode` output! " + context;
        });
    }

    /**
     * Verifies {@linkplain CoderResult#isUnmappable() unmappable} character
     * {@linkplain CodingErrorAction#REPLACE replacement} using {@link
     * ArrayEncoder#encodeFromLatin1(byte[], int, int, byte[], int)
     * ArrayEncoder::encodeFromLatin1}.
     */
    private static void testArrayEncoderLatin1Replace(CharsetEncoder encoder, char unmappable, byte[] replacement) {
        if (!(encoder instanceof ArrayEncoder arrayEncoder)) {
            System.err.println("Encoder is not of type `ArrayEncoder`, skipping the `ArrayEncoder::encodeFromLatin1` test.");
            return;
        }
        byte[] sa = {(byte) unmappable};
        byte[] da = new byte[replacement.length];
        int dp = arrayEncoder.encodeFromLatin1(sa, 0, 1, da, 0);
        assertTrue(dp == replacement.length && Arrays.equals(da, replacement), () -> {
            Object context = Map.of(
                    "dp", dp,
                    "da", prettyPrintBytes(da),
                    "sa", prettyPrintBytes(sa),
                    "unmappable", prettyPrintChars(new char[]{unmappable}),
                    "replacement", prettyPrintBytes(replacement));
            return "Unexpected `ArrayEncoder::encodeFromLatin1` output! " + context;
        });
    }

    static String prettyPrintChars(char[] cs) {
        return IntStream.range(0, cs.length)
                .mapToObj(i -> String.format("U+%04X", (int) cs[i]))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    static String prettyPrintBytes(byte[] bs) {
        return IntStream.range(0, bs.length)
                .mapToObj(i -> String.format("0x%02X", bs[i] & 0xFF))
                .collect(Collectors.joining(", ", "[", "]"));
    }

}
