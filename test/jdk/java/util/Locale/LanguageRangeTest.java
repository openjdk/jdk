/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8026766 8253321 8349883
 * @summary LanguageRange tests: toString(), hashCode()/equals(), checking
 *          for IAE on ill-formed ranges
 * @run junit LanguageRangeTest
 */

import static java.util.Locale.LanguageRange;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LanguageRangeTest {

    // 8349883: Test endpoints w/ ill-formed language range fail with IAE
    @ParameterizedTest
    @MethodSource("illegalRanges")
    public void illformedRangeTest(String range) {
        // static parses
        assertThrows(IllegalArgumentException.class,
                () -> Locale.LanguageRange.parse(range));
        assertThrows(IllegalArgumentException.class,
                () -> Locale.LanguageRange.parse(range, new HashMap<>()));
        // ctors
        assertThrows(IllegalArgumentException.class,
                () -> new Locale.LanguageRange(range));
        assertThrows(IllegalArgumentException.class,
                () -> new Locale.LanguageRange(range, Locale.LanguageRange.MIN_WEIGHT));
    }

    private static Stream<String> illegalRanges() {
        return Stream.of(
                // 8349883 offending range
                "-",
                // Other general ill-formed test cases
                "-foo",
                "foo-",
                "foo1",
                "foo-123456789",
                "*-*-",
                ""
        );
    }

    // 8253321: Ensure invoking hashCode does not affect equals result
    @Test
    public void hashCodeTest() {
        var range1 = new LanguageRange("en-GB", 0);
        var range2 = new LanguageRange("en-GB", 0);
        assertEquals(range1, range2);
        range1.hashCode();
        assertEquals(range1, range2);
        range2.hashCode();
        assertEquals(range1, range2);
    }

    // 8026766: toString() should hide weight if equal to MAX_WEIGHT (1.0)
    @ParameterizedTest
    @MethodSource("ranges")
    public void toStringTest(String range, double weight) {
        LanguageRange lr = new LanguageRange(range, weight);
        String expected = weight == 1.0
                ? range
                : range+";q="+weight;
        assertEquals(lr.toString(), expected);
    }

    private static Stream<Arguments> ranges() {
        return Stream.of(
                Arguments.of("ja", 1.0),
                Arguments.of("de", 0.5),
                Arguments.of("fr", 0.0)
        );
    }
}
