/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4396708
 * @summary Test URL encoder and decoder on a string that contains
 * surrogate pairs.
 * @run junit SurrogatePairs
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/*
 * Surrogate pairs are two character Unicode sequences where the first
 * character lies in the range [d800, dbff] and the second character lies
 * in the range [dc00, dfff]. They are used as an escaping mechanism to add
 * 1M more characters to Unicode.
 */
public class SurrogatePairs {

    public static String[][] arguments() {
        return new String[][] {
                {"\uD800\uDC00", "%F0%90%80%80"},
                {"\uD800\uDFFF", "%F0%90%8F%BF"},
                {"\uDBFF\uDC00", "%F4%8F%B0%80"},
                {"\uDBFF\uDFFF", "%F4%8F%BF%BF"},
                {"1\uDBFF\uDC00", "1%F4%8F%B0%80"},
                {"@\uDBFF\uDC00", "%40%F4%8F%B0%80"},
                {"\uDBFF\uDC001", "%F4%8F%B0%801"},
                {"\uDBFF\uDC00@", "%F4%8F%B0%80%40"},
                {"\u0101\uDBFF\uDC00", "%C4%81%F4%8F%B0%80"},
                {"\uDBFF\uDC00\u0101", "%F4%8F%B0%80%C4%81"},
                {"\uDE0A\uD83D", "%3F%3F"},
                {"1\uDE0A\uD83D", "1%3F%3F"},
                {"@\uDE0A\uD83D", "%40%3F%3F"},
                {"1@1\uDE0A\uD800\uDC00 \uD83D", "1%401%3F%F0%90%80%80+%3F"}
        };
    }

    @ParameterizedTest
    @MethodSource("arguments")
    public void test(String str, String correctEncoding) {
        String encoded = URLEncoder.encode(str, UTF_8);
        assertEquals(correctEncoding, encoded, () ->
                "str=%s, expected=%s, actual=%s"
                        .formatted(escape(str), escape(correctEncoding), escape(encoded)));

        // Map unmappable characters to '?'
        String cleanStr = new String(str.getBytes(UTF_8), UTF_8);
        String decoded = URLDecoder.decode(encoded, UTF_8);
        assertEquals(cleanStr, decoded, () ->
                "expected=%s, actual=%s".formatted(escape(str), escape(decoded)));
    }

    private static String escape(String s) {
        return s.chars().mapToObj(c -> String.format("\\u%04x", c))
                .collect(Collectors.joining());
    }
}
