/*
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4626545 4696726
 * @summary Checks the inter containment relationships between NIO charsets
 * @modules jdk.charsets
 * @run junit CharsetContainmentTest
 */

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CharsetContainmentTest {

    /**
     * Test that the charsets in 'encodings' contain the charsets
     * inside 'contains'. Each value in 'encodings' is mapped to a String
     * array in 'contains'. For example, the value, "TIS-620" in 'encodings'
     * should contain "US-ASCII", "TIS-620".
     */
    @ParameterizedTest
    @MethodSource("charsets")
    public void interContainmentTest(String containerName, String containedName) {
        Charset container = Charset.forName(containerName);
        Charset contained = Charset.forName(containedName);
        assertTrue(container.contains(contained),
                String.format("Charset: %s does not contain: %s", containerName, containedName));
    }

    private static Stream<Arguments> charsets() {
        String[] encodings = {
                "US-ASCII", "UTF-16", "UTF-16BE", "UTF-16LE", "UTF-8",
                "windows-1252", "ISO-8859-1", "ISO-8859-2", "ISO-8859-3",
                "ISO-8859-4", "ISO-8859-5", "ISO-8859-6", "ISO-8859-7",
                "ISO-8859-8", "ISO-8859-9", "ISO-8859-13", "ISO-8859-15", "ISO-8859-16",
                "ISO-2022-JP", "ISO-2022-KR",
                // Temporarily remove ISO-2022-CN-* charsets until full encoder/decoder
                // support is added (4673614)
                // "x-ISO-2022-CN-CNS", "x-ISO-2022-CN-GB",
                "x-ISCII91", "GBK", "GB18030", "Big5",
                "x-EUC-TW", "GB2312", "EUC-KR", "x-Johab", "Big5-HKSCS",
                "x-MS950-HKSCS", "windows-1251", "windows-1253", "windows-1254",
                "windows-1255", "windows-1256", "windows-1257", "windows-1258",
                "x-mswin-936", "x-windows-949", "x-windows-950", "windows-31j",
                "Shift_JIS", "EUC-JP", "KOI8-R", "TIS-620"
                };

        String[][] contains = {
                {"US-ASCII"},
                encodings,
                encodings,
                encodings,
                encodings,
                {"US-ASCII", "windows-1252"},
                {"US-ASCII", "ISO-8859-1"},
                {"US-ASCII", "ISO-8859-2"},
                {"US-ASCII", "ISO-8859-3"},
                {"US-ASCII", "ISO-8859-4"},
                {"US-ASCII", "ISO-8859-5"},
                {"US-ASCII", "ISO-8859-6"},
                {"US-ASCII", "ISO-8859-7"},
                {"US-ASCII", "ISO-8859-8"},
                {"US-ASCII", "ISO-8859-9"},
                {"US-ASCII", "ISO-8859-13"},
                {"US-ASCII", "ISO-8859-15"},
                {"US-ASCII", "ISO-8859-16"},
                {"ISO-2022-JP"},
                {"ISO-2022-KR"},
                // Temporarily remove ISO-2022-CN-* charsets until full encoder/decoder
                // support is added (4673614)
                //{"x-ISO-2022-CN-CNS"},
                //{"x-ISO-2022-CN-GB"},
                {"US-ASCII", "x-ISCII91"},
                {"US-ASCII", "GBK"},
                encodings,
                {"US-ASCII", "Big5"},
                {"US-ASCII", "x-EUC-TW"},
                {"US-ASCII", "GB2312"},
                {"US-ASCII", "EUC-KR"},
                {"US-ASCII", "x-Johab"},
                {"US-ASCII", "Big5-HKSCS", "Big5"},
                {"US-ASCII", "x-MS950-HKSCS", "x-windows-950"},
                {"US-ASCII", "windows-1251"},
                {"US-ASCII", "windows-1253"},
                {"US-ASCII", "windows-1254"},
                {"US-ASCII", "windows-1255"},
                {"US-ASCII", "windows-1256"},
                {"US-ASCII", "windows-1257"},
                {"US-ASCII", "windows-1258"},
                {"US-ASCII", "x-mswin-936"},
                {"US-ASCII", "x-windows-949"},
                {"US-ASCII", "x-windows-950"},
                {"US-ASCII", "windows-31j"},
                {"US-ASCII", "Shift_JIS"},
                {"US-ASCII", "EUC-JP"},
                {"US-ASCII", "KOI8-R"},
                {"US-ASCII", "TIS-620"}};

        // Length of encodings and contains should always be equal
        if (encodings.length != contains.length) {
            throw new RuntimeException("Testing data is not set up correctly");
        }
        List<Arguments> charsetList = new ArrayList<Arguments>();
        for (int i = 0; i < encodings.length; i++) {
            for (int j = 0 ; j < contains[i].length; j++) {
                charsetList.add(Arguments.of(encodings[i], contains[i][j]));
            }
        }
        return charsetList.stream();
    }
}
