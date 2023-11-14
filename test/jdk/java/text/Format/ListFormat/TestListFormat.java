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
 * @bug 8041488 8316974 8318569 8306116
 * @summary Tests for ListFormat class
 * @run junit TestListFormat
 */

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ListFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    private static final String[] CUSTOM_PATTERNS_IAE_TWO = {
            "sbef {0} sbet {1}",
            "{0} mid {1}",
            "{0} ebet {1} eaft",
            "{1}error{0}",
            "",
    };
    private static final String[] CUSTOM_PATTERNS_IAE_THREE = {
            "sbef {0} sbet {1}",
            "{0} mid {1}",
            "{0} ebet {1} eaft",
            "",
            "{0}error{1}",
    };
    private static final String[] CUSTOM_PATTERNS_IAE_NULL = {
            null,
            null,
            null,
            null,
            null,
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
                arguments(new String[1], "Pattern array length should be 5"),
                arguments(new String[6], "Pattern array length should be 5"),
                arguments(CUSTOM_PATTERNS_IAE_START, "start pattern is incorrect: {0}"),
                arguments(CUSTOM_PATTERNS_IAE_MIDDLE, "middle pattern is incorrect: {0} {1} {2}"),
                arguments(CUSTOM_PATTERNS_IAE_END, "end pattern is incorrect: error {0} ebet {1}"),
                arguments(CUSTOM_PATTERNS_IAE_TWO, "pattern for two is incorrect: {1}error{0}"),
                arguments(CUSTOM_PATTERNS_IAE_THREE, "pattern for three is incorrect: {0}error{1}"),
                arguments(CUSTOM_PATTERNS_IAE_NULL, "patterns array contains one or more null elements"),
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
                        "foo\u3001bar\u3001baz", true),
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

    static Arguments[] parseObject_parsePos() {
        return new Arguments[] {
                arguments(CUSTOM_PATTERNS_FULL, SAMPLE1),
                arguments(CUSTOM_PATTERNS_FULL, SAMPLE2),
                arguments(CUSTOM_PATTERNS_FULL, SAMPLE3),
                arguments(CUSTOM_PATTERNS_FULL, SAMPLE4),
                arguments(CUSTOM_PATTERNS_MINIMAL, SAMPLE1),
                arguments(CUSTOM_PATTERNS_MINIMAL, SAMPLE2),
                arguments(CUSTOM_PATTERNS_MINIMAL, SAMPLE3),
                arguments(CUSTOM_PATTERNS_MINIMAL, SAMPLE4),
        };
    }

    static Arguments[] getInstance_3Arg_InheritPatterns() {
        return new Arguments[] {
                arguments(ListFormat.Type.STANDARD, ListFormat.Style.FULL),
                arguments(ListFormat.Type.STANDARD, ListFormat.Style.SHORT),
                arguments(ListFormat.Type.STANDARD, ListFormat.Style.NARROW),
                arguments(ListFormat.Type.OR, ListFormat.Style.FULL),
                arguments(ListFormat.Type.OR, ListFormat.Style.SHORT),
                arguments(ListFormat.Type.OR, ListFormat.Style.NARROW),
                arguments(ListFormat.Type.UNIT, ListFormat.Style.FULL),
                arguments(ListFormat.Type.UNIT, ListFormat.Style.SHORT),
                arguments(ListFormat.Type.UNIT, ListFormat.Style.NARROW),
        };
    }

    static Arguments[] getLocale_localeDependent() {
        return new Arguments[] {
                arguments(Locale.ROOT),
                arguments(Locale.US),
                arguments(Locale.GERMANY),
                arguments(Locale.JAPAN),
                arguments(Locale.SIMPLIFIED_CHINESE),
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
        var ex = assertThrows(IllegalArgumentException.class,
                () -> ListFormat.getInstance(invalidPatterns));
        assertEquals(errorMsg, ex.getMessage());
    }

    @ParameterizedTest
    @MethodSource
    void getInstance_3Arg(Locale l, ListFormat.Type type, ListFormat.Style style, String expected, boolean roundTrip) throws ParseException {
        var f = ListFormat.getInstance(l, type, style);
        compareResult(f, SAMPLE3, expected, roundTrip);
    }

    @Test
    void getLocale_invariant() {
        var f = ListFormat.getInstance(CUSTOM_PATTERNS_FULL);
        assertEquals(Locale.ROOT, f.getLocale());
    }

    @Test
    void getLocale_default() {
        var f = ListFormat.getInstance();
        assertEquals(Locale.getDefault(Locale.Category.FORMAT), f.getLocale());
    }

    @ParameterizedTest
    @MethodSource
    void getLocale_localeDependent(Locale l) {
        var f = ListFormat.getInstance(l, ListFormat.Type.STANDARD, ListFormat.Style.FULL);
        assertEquals(l, f.getLocale());
    }

    @Test
    void getPatterns_immutability() {
        var f = ListFormat.getInstance(CUSTOM_PATTERNS_FULL);
        var p = f.getPatterns();
        p[0] = null;
        assertArrayEquals(CUSTOM_PATTERNS_FULL, f.getPatterns());
    }

    @Test
    void format_3Arg() {
        var f = ListFormat.getInstance();
        // Ensures it accepts both List and []
        assertEquals(f.format(SAMPLE4, new StringBuffer(), null).toString(),
                f.format(SAMPLE4.toArray(), new StringBuffer(), null).toString());

        // Tests NPE
        assertThrows(NullPointerException.class,
                () -> f.format(null, new StringBuffer(), new FieldPosition(0)));
        assertThrows(NullPointerException.class,
                () -> f.format(new Object(), null, new FieldPosition(0)));

        // Tests IAE
        var ex = assertThrows(IllegalArgumentException.class,
                () -> f.format(new Object(), new StringBuffer(), null));
        assertEquals("The object to format should be a List<Object> or an Object[]", ex.getMessage());
    }

    @Test
    void formatToCharacterIterator() {
        var f = ListFormat.getInstance();
        // Ensures it accepts both List and []
        assertEquals(f.formatToCharacterIterator(SAMPLE4).toString(),
                f.formatToCharacterIterator(SAMPLE4.toArray()).toString());

        // Tests NPE
        assertThrows(NullPointerException.class,
                () -> f.formatToCharacterIterator(null));

        // Tests IAE
        var ex = assertThrows(IllegalArgumentException.class,
                () -> f.formatToCharacterIterator(new Object()));
        assertEquals("The arguments should be a List<Object> or an Object[]", ex.getMessage());
    }

    @Test
    void format_emptyInput() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> ListFormat.getInstance().format(List.of()));
        assertEquals("There should at least be one input string", ex.getMessage());
    }

    @ParameterizedTest
    @MethodSource
    void parseObject_parsePos(String[] patterns, List<String> input) {
        var prefix = "prefix";
        var f = ListFormat.getInstance(patterns);
        var testStr = prefix + f.format(input);

        var pp = new ParsePosition(prefix.length());
        var parsed = f.parseObject(testStr, pp);
        assertEquals(input, parsed, pp.toString());
        assertEquals(new ParsePosition(testStr.length()), pp);

        pp.setIndex(0);
        parsed = f.parseObject(testStr, pp);
        assertNotEquals(input, parsed);
        assertEquals(-1, pp.getErrorIndex());

        pp.setIndex(prefix.length() + 1);
        parsed = f.parseObject(testStr, pp);
        assertNotEquals(input, parsed);
        assertEquals(-1, pp.getErrorIndex());
    }

    @ParameterizedTest
    @MethodSource
    void getInstance_3Arg_InheritPatterns(ListFormat.Type type, ListFormat.Style style) {
        // No IAE should be thrown for all locales. Some locales in CLDR
        // have partial patterns (start, middle, end) in it. Lacking ones
        // should be inherited from parent locales.
        Locale.availableLocales().forEach(l -> ListFormat.getInstance(l, type, style));
    }
    @Test
    void getInstance_3Arg_InheritanceValidation() {
        // Tests if inheritance works as expected.
        // World English ("en-001") has non-Oxford-comma pattern for "end", while
        // English ("en") has Oxford-comma "end" pattern. Thus missing "standard"/"middle"
        // should be inherited from "en", but "end" should stay non-Oxford for "en-001"
        // Note that this test depends on a particular version of CLDR data.
        var world = Locale.forLanguageTag("en-001");
        assertEquals("""
            ListFormat [locale: "%s", start: "{0}, {1}", middle: "{0}, {1}", end: "{0} and {1}", two: "{0} and {1}", three: "{0}, {1} and {2}"]
            """.formatted(world.getDisplayName()),
            ListFormat.getInstance(world, ListFormat.Type.STANDARD, ListFormat.Style.FULL).toString());
    }

    private static void compareResult(ListFormat f, List<String> input, String expected, boolean roundTrip) throws ParseException {
        var result = f.format(input);
        assertEquals(expected, result);
        if (roundTrip) {
            assertEquals(input, f.parse(result));
        }
    }
}
