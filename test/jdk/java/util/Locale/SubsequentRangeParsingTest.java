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
 * @bug 8166994
 * @summary Checks the subsequent call to parse the same language ranges
 *          which must generate the same list of language ranges
 *          i.e. the priority list containing equivalents, as in the
 *          first call
 * @run junit SubsequentRangeParsingTest
 */

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SubsequentRangeParsingTest {

    /*
     * Checks that consecutive calls to parse the same language ranges
     * generate the same list of language ranges.
     */
    @ParameterizedTest
    @MethodSource("ranges")
    public void parseConsistencyTest(List<String> list, String ranges) {
        // consecutive call to check the language range parse consistency
        testParseConsistency(list, ranges);
        testParseConsistency(list, ranges);
    }

    // Ensure that parsing the ranges returns the expected list.
    private static void testParseConsistency(List<String> list, String ranges) {
        List<String> priorityList = parseRanges(ranges);
        assertEquals(list, priorityList, "Failed to parse the language range:");
    }

    private static List<String> parseRanges(String s) {
        return Locale.LanguageRange.parse(s).stream()
                .map(Locale.LanguageRange::getRange)
                .collect(Collectors.toList());
    }

    // Ranges that have multiple equivalents and single equivalents.
    private static Stream<Arguments> ranges() {
        return Stream.of(
                Arguments.of(Arrays.asList("ccq-aa", "ybd-aa", "rki-aa"),
                        "ccq-aa"),
                Arguments.of(Arrays.asList("gfx-xz", "oun-xz", "mwj-xz",
                        "vaj-xz", "taj-xy", "tsf-xy"), "gfx-xz, taj-xy")
        );
    }
}
