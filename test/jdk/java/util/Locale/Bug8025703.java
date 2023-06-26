/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8025703
 * @summary Verify implementation for Locale matching.
 * @run junit Bug8025703
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Locale.LanguageRange;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Bug8025703 {

    /**
     * This test checks that parsing a range returns the expected
     * language priority list by matching the correct tag(s).
     */
    @ParameterizedTest
    @MethodSource("mappings")
    public void localeMatchingTest(String range1, String range2) {
        List<LanguageRange> actual = LanguageRange.parse(range1);
        ArrayList<LanguageRange> expected = new ArrayList<>();
        expected.add(new LanguageRange(range1, 1.0));
        expected.add(new LanguageRange(range2, 1.0));
        assertEquals(expected, actual, () -> getRangeAndWeights(expected, actual));
    }

    // Tags that map to each other
    private static Stream<Arguments> mappings() {
        return Stream.of(
                Arguments.of("ilw", "gal"),
                Arguments.of("meg", "cir"),
                Arguments.of("pcr", "adx"),
                Arguments.of("xia", "acn"),
                Arguments.of("yos", "zom")
        );
    }

    // Helper function to log differences
    private String getRangeAndWeights(ArrayList<LanguageRange> expected, List<LanguageRange> actual) {
       StringBuilder errOutput = new StringBuilder();
        for (LanguageRange lr : expected) {
            errOutput.append(String.format("%nExpected: " +
                    "range='%s', weight='%s'", lr.getRange(), lr.getWeight()));
        }
        for (LanguageRange lr : actual) {
            errOutput.append(String.format("%nActual: " +
                    "range='%s', weight='%s'", lr.getRange(), lr.getWeight()));
        }
       return errOutput.toString();
    }
}
