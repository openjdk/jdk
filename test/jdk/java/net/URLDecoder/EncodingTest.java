/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8183743
 * @summary Test to verify the new overload method with Charset functions the
 * same as the existing method that takes a charset name.
 * @run junit ${test.main.class}
 */
public class EncodingTest {
    public static enum ParameterType {
        STRING,
        CHARSET
    }

    public static Object[][] getParameters() {
        return new Object[][]{
            {ParameterType.STRING},
            {ParameterType.CHARSET}
        };
    }

    public static Object[][] getDecodeParameters() {
        return new Object[][]{
            {"The string \u00FC@foo-bar"},
            // the string from javadoc example

            {""}, // an empty string

            {"x"}, // a string of length 1

            {"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-.*"},
            // the string of characters should remain the same

            {charactersRange('\u0000', '\u007F')},
            // a string of characters from 0 to 127

            {charactersRange('\u0080', '\u00FF')},
            // a string of characters from 128 to 255

            {"\u0100 \u0101 \u0555 \u07FD \u07FF"},
            // a string of Unicode values can be expressed as 2 bytes

            {"\u8000 \u8001 \uA000 \uFFFD \uFFFF"}, // a string of Unicode values can be expressed as 3 bytes
        };
    }

    /**
     * Verifies that IAE is thrown when decoding an invalid string using the
     * existing method or the new overload method.
     *
     * @param type the type of the argument, e.g a String charset name or
     * charset
     */
    @ParameterizedTest
    @MethodSource("getParameters")
    public void testIllegalArgument(ParameterType type) throws Exception {
        String encoded = URLEncoder.encode("http://www.xyz.com/find?key=\u0100\u0101",
                StandardCharsets.UTF_8.name());
        String illegal = "%" + encoded;
        Executable decoded = type == ParameterType.STRING
                ? () -> URLDecoder.decode(illegal, StandardCharsets.UTF_8.name())
                : () -> URLDecoder.decode(illegal, StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, decoded);
    }

    /**
     * Verifies that the returned values of decoding with the existing
     * and the overload methods match.
     *
     * @param s the string to be encoded and then decoded with both existing
     * and the overload methods.
     * @throws Exception if failed
     */
    @ParameterizedTest
    @MethodSource("getDecodeParameters")
    public void decode(String s) throws Exception {
        String encoded = URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        String returned1 = URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
        String returned2 = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        assertEquals(returned2, returned1);
    }

    private static String charactersRange(char c1, char c2) {
        StringBuilder sb = new StringBuilder(c2 - c1);
        for (char c = c1; c < c2; c++) {
            sb.append(c);
        }

        return sb.toString();
    }
}
