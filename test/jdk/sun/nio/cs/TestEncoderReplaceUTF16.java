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

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import sun.nio.cs.ArrayEncoder;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8364365
 * @summary Verifies `CodingErrorAction.REPLACE` behaviour of all available
 *          character set encoders while encoding a UTF-16 character
 * @modules java.base/jdk.internal.access
 *          java.base/sun.nio.cs
 * @build TestEncoderReplaceLatin1
 * @run junit/timeout=40 TestEncoderReplaceUTF16
 * @run junit/timeout=40/othervm -XX:-CompactStrings TestEncoderReplaceUTF16
 */

class TestEncoderReplaceUTF16 {

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    /**
     * Character sets known to be absent of non-Latin-1 {@linkplain CoderResult#isUnmappable() unmappable} characters.
     */
    private static final Set<String> CHARSETS_WITHOUT_UNMAPPABLE = Set.of(
            "CESU-8",
            "EUC-JP",
            "GB18030",
            "ISO-2022-JP",
            "ISO-2022-JP-2",
            "ISO-2022-KR",
            "ISO-8859-1",
            "US-ASCII",
            "UTF-16",
            "UTF-16BE",
            "UTF-16LE",
            "UTF-32",
            "UTF-32BE",
            "UTF-32LE",
            "UTF-8",
            "x-euc-jp-linux",
            "x-EUC-TW",
            "x-eucJP-Open",
            "x-IBM29626C",
            "x-IBM33722",
            "x-IBM964",
            "x-ISCII91",
            "x-ISO-2022-CN-CNS",
            "x-ISO-2022-CN-GB",
            "x-MS932_0213",
            "x-SJIS_0213",
            "x-UTF-16LE-BOM",
            "X-UTF-32BE-BOM",
            "X-UTF-32LE-BOM",
            "x-windows-50220",
            "x-windows-50221",
            "x-windows-iso2022jp");

    @ParameterizedTest
    @MethodSource("TestEncoderReplaceLatin1#charsets")
    void testEncoderReplace(Charset charset) {

        // Create an encoder
        CharsetEncoder encoder = createEncoder(charset);
        if (encoder == null) {
            return;
        }

        // Find an unmappable character to test the `REPLACE` action.
        char[] unmappable = findUnmappableNonLatin1(encoder);
        if (unmappable == null) {
            return;
        }

        // Configure the `REPLACE` action
        byte[] unmappableUTF16Bytes = utf16Bytes(unmappable);
        byte[] replacement = TestEncoderReplaceLatin1.findCustomReplacement(encoder, unmappableUTF16Bytes);
        if (replacement == null) {
            return;
        }
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE).replaceWith(replacement);

        // Verify the replacement
        System.err.println("Verifying replacement... " + Map.of(
                "unmappable", TestEncoderReplaceLatin1.prettyPrintChars(unmappable),
                "unmappableUTF16Bytes", TestEncoderReplaceLatin1.prettyPrintBytes(unmappableUTF16Bytes),
                "replacement", TestEncoderReplaceLatin1.prettyPrintBytes(replacement)));
        TestEncoderReplaceLatin1.testCharsetEncoderReplace(encoder, unmappable, replacement);
        testArrayEncoderUTF16Replace(encoder, unmappableUTF16Bytes, replacement);

    }

    private static CharsetEncoder createEncoder(Charset charset) {
        try {
            return charset.newEncoder();
        } catch (UnsupportedOperationException _) {
            System.err.println("Could not create the character encoder!");
        }
        return null;
    }

    /**
     * Finds an {@linkplain CoderResult#isUnmappable() unmappable} non-Latin-1 {@code char[]} for the given encoder.
     */
    private static char[] findUnmappableNonLatin1(CharsetEncoder encoder) {

        // Fast-path for characters sets known to be absent of unmappable non-Latin-1 characters
        if (CHARSETS_WITHOUT_UNMAPPABLE.contains(encoder.charset().name())) {
            System.err.println("Character set is known to be absent of unmappable non-Latin-1 characters!");
            return null;
        }

        // Try to find a single-`char` unmappable
        for (int i = 0xFF; i <= 0xFFFF; i++) {
            char c = (char) i;
            // Skip the surrogate, as a single dangling surrogate `char` should
            // trigger a "malformed" error, instead of "unmappable"
            if (Character.isSurrogate(c)) {
                continue;
            }
            boolean unmappable = !encoder.canEncode(c);
            if (unmappable) {
                return new char[]{c};
            }
        }

        // Try to find a double-`char` (i.e., surrogate pair) unmappable
        int[] nonBmpRange = {0x10000, 0x10FFFF};
        for (int i = nonBmpRange[0]; i < nonBmpRange[1]; i++) {
            char[] cs = Character.toChars(i);
            if (!encoder.canEncode(new String(cs)))
                return cs;
        }

        System.err.println("Could not find an unmappable character!");
        return null;
    }

    private static byte[] utf16Bytes(char[] cs) {
        int sl = cs.length;
        byte[] sa = new byte[sl << 1];
        for (int i = 0; i < sl; i++) {
            JLA.uncheckedPutCharUTF16(sa, i, cs[i]);
        }
        return sa;
    }

    /**
     * Verifies {@linkplain CoderResult#isUnmappable() unmappable} character
     * {@linkplain CodingErrorAction#REPLACE replacement} using {@link
     * ArrayEncoder#encodeFromUTF16(byte[], int, int, byte[], int)
     * ArrayEncoder::encodeFromUTF16}.
     */
    private static void testArrayEncoderUTF16Replace(CharsetEncoder encoder, byte[] unmappableUTF16Bytes, byte[] replacement) {
        if (!(encoder instanceof ArrayEncoder arrayEncoder)) {
            System.err.println("Encoder is not of type `ArrayEncoder`, skipping the `ArrayEncoder::encodeFromUTF16` test.");
            return;
        }
        byte[] da = new byte[replacement.length];
        int dp = arrayEncoder.encodeFromUTF16(unmappableUTF16Bytes, 0, unmappableUTF16Bytes.length >>> 1, da, 0);
        assertTrue(dp == replacement.length && Arrays.equals(da, replacement), () -> {
            Object context = Map.of(
                    "dp", dp,
                    "da", TestEncoderReplaceLatin1.prettyPrintBytes(da),
                    "unmappableUTF16Bytes", TestEncoderReplaceLatin1.prettyPrintBytes(unmappableUTF16Bytes),
                    "replacement", TestEncoderReplaceLatin1.prettyPrintBytes(replacement));
            return "Unexpected `ArrayEncoder::encodeFromUTF16` output! " + context;
        });
    }

}
