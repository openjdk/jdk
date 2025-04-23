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
 * @bug 8353585
 * @summary Basic parse tests. Enforce regular behavior, no match, and multi match.
 * @run junit ParseTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.text.ChoiceFormat;
import java.text.ParsePosition;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParseTest {

    // Ensure that the parsed text produces the expected number
    // i.e. return limit corresponding to format matched
    @ParameterizedTest
    @MethodSource
    void parseTest(String pattern, String text, Double expected, int index) {
        var pp = new ParsePosition(index);
        var fmt = new ChoiceFormat(pattern);
        assertEquals(expected, fmt.parse(text, pp), "Incorrect limit returned");
        if (expected.equals(Double.NaN)) { // AKA failed parse
            assertEquals(index, pp.getErrorIndex(),
                    "Failed parse produced incorrect error index");
        } else {
            assertEquals(-1, pp.getErrorIndex(),
                    "Error index should remain -1 on match");
        }
    }

    private static Stream<Arguments> parseTest() {
        return Stream.of(
                Arguments.of("1#foo", "foo", Double.NaN, -1),
                Arguments.of("1#baz", "foo bar baz", Double.NaN, 20),
                Arguments.of("1#baz", "foo bar baz", 1d, 8),
                Arguments.of("1#baz", "foo baz quux", Double.NaN, 8),
                Arguments.of("1#a", "", Double.NaN, 0),
                Arguments.of("1#a", "a", 1d, 0),
                Arguments.of("1# ", " ", 1d, 0),
                Arguments.of("1#a|2#a", "a", 1d, 0),
                Arguments.of("1#a|2#aa", "aa", 2d, 0),
                Arguments.of("1#a|2#aa", "aabb", 2d, 0),
                Arguments.of("1#a|2#aa", "bbaa", Double.NaN, 0),
                Arguments.of("1#aa|2#aaa", "a", Double.NaN, 0)
        );
    }
}
