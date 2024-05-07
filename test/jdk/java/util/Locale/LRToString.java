/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8026766
 * @summary Confirm that LanguageRange.toString() returns an expected result.
 * @run junit LRToString
 */

import java.util.Locale.LanguageRange;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LRToString {

    /**
     * This test ensures that the output of LanguageRange.toString()
     * returns an expected result, that is, the weight is hidden if it is
     * equal to 1.0.
     */
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
