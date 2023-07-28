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

/*
 * @test
 * @bug 8041488
 * @summary Tests for ListFormat class
 * @run junit TestListFormat
 */

import java.text.DateFormat;
import java.text.ListFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class TestListFormat {
    private static final List<String> SAMPLE1 = List.of("foo");
    private static final List<String> SAMPLE2 = List.of("foo", "bar");
    private static final List<String> SAMPLE3 = List.of("foo", "bar", "baz");
    private static final List<String> SAMPLE4 = List.of("foo", "bar", "baz", "qux");
    private static final String[] CUSTOM_PATTERNS_FULL = {
            "sbef {0} sbet {1}",
            "{0} mid {1}",
            "{0} ebet {1} eaft",
            "twobef {0} two {1} twoaft",
            "threebef {0} three {1} three {2} threeaft",
    };
    private static final String[] CUSTOM_PATTERNS_MINIMAL = {
            "sbef {0} sbet {1}",
            "{0} mid {1}",
            "{0} ebet {1} eaft",
            "",
            "",
    };
    private static final String[] CUSTOM_PATTERNS_IAE_LENGTH = {
            ""
    };
    private static final String[] CUSTOM_PATTERNS_IAE_START = {
            "{0}",
            "{0} mid {1}",
            "{0} ebet {1} eaft",
            "",
            "",
    };
    private static final String[] CUSTOM_PATTERNS_IAE_MIDDLE = {
            "{0} sbet {1}",
            "{0} {1} {2}",
            "{0} ebet {1} eaft",
            "",
            "",
    };
    private static final String[] CUSTOM_PATTERNS_IAE_END = {
            "{0} sbet {1}",
            "{0} mid {1}",
            "error {0} ebet {1}",
            "",
            "",
    };

    @Test
    void getAvailableLocales() {
        assertArrayEquals(DateFormat.getAvailableLocales(), ListFormat.getAvailableLocales());
    }

    @Test
    void getInstance_noArg() {
        assertEquals(ListFormat.getInstance(), ListFormat.getInstance(Locale.getDefault(Locale.Category.FORMAT), ListFormat.Type.STANDARD, ListFormat.Style.FULL));
    }

    static Arguments[] getInstance_1Arg() {
        return new Arguments[] {
                arguments(CUSTOM_PATTERNS_FULL, SAMPLE1, "foo"),
                arguments(CUSTOM_PATTERNS_FULL, SAMPLE2, "twobef foo two bar twoaft"),
                arguments(CUSTOM_PATTERNS_FULL, SAMPLE3, "threebef foo three bar three baz threeaft"),
                arguments(CUSTOM_PATTERNS_FULL, SAMPLE4, "sbef foo sbet bar mid baz ebet qux eaft"),
                arguments(CUSTOM_PATTERNS_MINIMAL, SAMPLE1, "foo"),
                arguments(CUSTOM_PATTERNS_MINIMAL, SAMPLE2, "sbef foo ebet bar eaft"),
                arguments(CUSTOM_PATTERNS_MINIMAL, SAMPLE3, "sbef foo sbet bar ebet baz eaft"),
                arguments(CUSTOM_PATTERNS_MINIMAL, SAMPLE4, "sbef foo sbet bar mid baz ebet qux eaft"),
        };
    }

    static Arguments[] getInstance_1Arg_IAE() {
        return new Arguments[] {
                arguments(CUSTOM_PATTERNS_IAE_LENGTH, "Pattern array length should be 5"),
                arguments(CUSTOM_PATTERNS_IAE_START, "start pattern is incorrect: {0}"),
                arguments(CUSTOM_PATTERNS_IAE_MIDDLE, "middle pattern is incorrect: {0} {1} {2}"),
                arguments(CUSTOM_PATTERNS_IAE_END, "end pattern is incorrect: error {0} ebet {1}"),
        };
    }

    static Arguments[] getInstance_3Arg() {
        return new Arguments[] {
                arguments(Locale.US, ListFormat.Type.STANDARD, ListFormat.Style.FULL,
                        "foo, bar, and baz", true),
                arguments(Locale.US, ListFormat.Type.OR, ListFormat.Style.FULL,
                        "foo, bar, or baz", true),
                arguments(Locale.US, ListFormat.Type.UNIT, ListFormat.Style.FULL,
                        "foo, bar, baz", true),
                arguments(Locale.US, ListFormat.Type.STANDARD, ListFormat.Style.SHORT,
                        "foo, bar, & baz", true),
                arguments(Locale.US, ListFormat.Type.OR, ListFormat.Style.SHORT,
                        "foo, bar, or baz", true),
                arguments(Locale.US, ListFormat.Type.UNIT, ListFormat.Style.SHORT,
                        "foo, bar, baz", true),
                arguments(Locale.US, ListFormat.Type.STANDARD, ListFormat.Style.NARROW,
                        "foo, bar, baz", true),
                arguments(Locale.US, ListFormat.Type.OR, ListFormat.Style.NARROW,
                        "foo, bar, or baz", true),
                arguments(Locale.US, ListFormat.Type.UNIT, ListFormat.Style.NARROW,
                        "foo bar baz", true),

                arguments(Locale.JAPAN, ListFormat.Type.STANDARD, ListFormat.Style.FULL,
                        "foo\u3001bar\u3001baz", true),
                arguments(Locale.JAPAN, ListFormat.Type.OR, ListFormat.Style.FULL,
                        "foo\u3001bar\u3001\u307e\u305f\u306fbaz", true),
                arguments(Locale.JAPAN, ListFormat.Type.UNIT, ListFormat.Style.FULL,
                        "foo bar baz", true),
                arguments(Locale.JAPAN, ListFormat.Type.STANDARD, ListFormat.Style.SHORT,
                        "foo\u3001bar\u3001baz", true),
                arguments(Locale.JAPAN, ListFormat.Type.OR, ListFormat.Style.SHORT,
                        "foo\u3001bar\u3001\u307e\u305f\u306fbaz", true),
                arguments(Locale.JAPAN, ListFormat.Type.UNIT, ListFormat.Style.SHORT,
                        "foo bar baz", true),
                arguments(Locale.JAPAN, ListFormat.Type.STANDARD, ListFormat.Style.NARROW,
                        "foo\u3001bar\u3001baz", true),
                arguments(Locale.JAPAN, ListFormat.Type.OR, ListFormat.Style.NARROW,
                        "foo\u3001bar\u3001\u307e\u305f\u306fbaz", true),
                arguments(Locale.JAPAN, ListFormat.Type.UNIT, ListFormat.Style.NARROW,
                        "foobarbaz", false), // no delimiter, impossible to parse/roundtrip
        };
    }

    @ParameterizedTest
    @MethodSource
    void getInstance_1Arg(String[] patterns, List<String> input, String expected) throws ParseException {
        var f = ListFormat.getInstance(patterns);
        compareResult(f, input, expected, true);
    }

    @ParameterizedTest
    @MethodSource
    void getInstance_1Arg_IAE(String[] invalidPatterns, String errorMsg) {
        Exception e = assertThrows(IllegalArgumentException.class,
                () -> ListFormat.getInstance(invalidPatterns));
        assertEquals(errorMsg, e.getMessage());
    }

    @ParameterizedTest
    @MethodSource
    void getInstance_3Arg(Locale l, ListFormat.Type type, ListFormat.Style style, String expected, boolean roundTrip) throws ParseException {
        var f = ListFormat.getInstance(l, type, style);
        compareResult(f, SAMPLE3, expected, roundTrip);
    }

    private static void compareResult(ListFormat f, List<String> input, String expected, boolean roundTrip) throws ParseException {
        var result = f.format(input);
        assertEquals(expected, result);
        if (roundTrip) {
            assertEquals(input, f.parse(result));
        }
    }
}

