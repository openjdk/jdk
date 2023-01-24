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

import java.text.ListFormat;
import java.text.ParseException;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class TestListFormat {
    private static final Object[] SAMPLE2 = {"afo", "ika"};
    private static final Object[] SAMPLE3 = {"afo", "ika", "uni"};
    private static final Object[] SAMPLE4 = {"afo", "ika", "uni", "tako"};
    private static final String[] CUSTOM_PATTERNS = {
            "sbef {0} sbet {1}",
            "{0} mid {1}",
            "{0} ebet {1} eaft",
            "{0} two {1}",
            "{0} three {1} three {2}",
    };

    static Arguments[] getInstance_1Arg() {
        return new Arguments[] {
                arguments(SAMPLE2, "afo two ika"),
                arguments(SAMPLE3, "afo three ika three uni"),
                arguments(SAMPLE4, "sbef afo sbet ika mid uni ebet tako eaft"),
        };
    }

    static Arguments[] getInstance_3Arg() {
        return new Arguments[] {
                arguments(Locale.US, ListFormat.Type.STANDARD, ListFormat.Style.FULL,
                        "afo, ika, and uni", false),
                arguments(Locale.US, ListFormat.Type.OR, ListFormat.Style.FULL,
                        "afo, ika, or uni", false),
                arguments(Locale.US, ListFormat.Type.UNIT, ListFormat.Style.FULL,
                        "afo, ika, uni", false),
                arguments(Locale.US, ListFormat.Type.STANDARD, ListFormat.Style.SHORT,
                        "afo, ika, & uni", false),
                arguments(Locale.US, ListFormat.Type.OR, ListFormat.Style.SHORT,
                        "afo, ika, or uni", false),
                arguments(Locale.US, ListFormat.Type.UNIT, ListFormat.Style.SHORT,
                        "afo, ika, uni", false),
                arguments(Locale.US, ListFormat.Type.STANDARD, ListFormat.Style.NARROW,
                        "afo, ika, uni", false),
                arguments(Locale.US, ListFormat.Type.OR, ListFormat.Style.NARROW,
                        "afo, ika, or uni", false),
                arguments(Locale.US, ListFormat.Type.UNIT, ListFormat.Style.NARROW,
                        "afo ika uni", false),

                arguments(Locale.JAPAN, ListFormat.Type.STANDARD, ListFormat.Style.FULL,
                        "afo\u3001ika\u3001uni", false),
                arguments(Locale.JAPAN, ListFormat.Type.OR, ListFormat.Style.FULL,
                        "afo\u3001ika\u3001\u307e\u305f\u306funi", false),
                arguments(Locale.JAPAN, ListFormat.Type.UNIT, ListFormat.Style.FULL,
                        "afo ika uni", false),
                arguments(Locale.JAPAN, ListFormat.Type.STANDARD, ListFormat.Style.SHORT,
                        "afo\u3001ika\u3001uni", false),
                arguments(Locale.JAPAN, ListFormat.Type.OR, ListFormat.Style.SHORT,
                        "afo\u3001ika\u3001\u307e\u305f\u306funi", false),
                arguments(Locale.JAPAN, ListFormat.Type.UNIT, ListFormat.Style.SHORT,
                        "afo ika uni", false),
                arguments(Locale.JAPAN, ListFormat.Type.STANDARD, ListFormat.Style.NARROW,
                        "afo\u3001ika\u3001uni", false),
                arguments(Locale.JAPAN, ListFormat.Type.OR, ListFormat.Style.NARROW,
                        "afo\u3001ika\u3001\u307e\u305f\u306funi", false),
                arguments(Locale.JAPAN, ListFormat.Type.UNIT, ListFormat.Style.NARROW,
                        "afoikauni", true), // no delimiter
        };
    }

    @Test
    void getInstance_noArg() {
        assertEquals(ListFormat.getInstance(), ListFormat.getInstance(Locale.getDefault(Locale.Category.FORMAT), ListFormat.Type.STANDARD, ListFormat.Style.FULL));
    }

    @ParameterizedTest
    @MethodSource
    void getInstance_1Arg(Object[] input, String expected) throws ParseException {
        var f = ListFormat.getInstance(CUSTOM_PATTERNS);
        compareResult(f, input, expected, true);
    }

    @ParameterizedTest
    @MethodSource
    void getInstance_3Arg(Locale l, ListFormat.Type type, ListFormat.Style style, String expected, boolean roundTrip) throws ParseException {
        var f = ListFormat.getInstance(l, type, style);
        compareResult(f, SAMPLE3, expected, roundTrip);
    }

    private static void compareResult(ListFormat f, Object[] input, String expected, boolean roundTrip) throws ParseException {
        var result = f.format(input);
        assertEquals(expected, result);
        if (!roundTrip) {
            if (f.parseObject(result) instanceof Object[] ra) {
                assertArrayEquals(input, ra);
            } else {
                fail();
            }
        }
    }
}

