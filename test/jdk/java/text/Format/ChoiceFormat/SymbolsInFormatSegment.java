/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6285888
 * @summary Ensure ChoiceFormat supports "#", "<", "≤" within
 *          the format segment of a ChoiceFormat String pattern
 * @run junit SymbolsInFormatSegment
 */

import java.text.ChoiceFormat;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * These tests would previously throw IAEs on all input as ChoiceFormat would parse
 * the relational symbol syntactically (when not needed). With the associated change
 * set, ChoiceFormat knows to not treat any subsequent relational symbols as
 * syntactically significant unless a '|' has been parsed.
 */
public class SymbolsInFormatSegment {

    // Test a variety of patterns with relational symbols in the Format segment
    @ParameterizedTest
    @MethodSource("patternsWithSymbols")
    public void allowInConstructor(String pattern, String expected, int limit) {
        var cf = new ChoiceFormat(pattern);
        assertEquals(expected, cf.format(limit));
    }

    // Same as previous test, but check the applyPattern method
    @ParameterizedTest
    @MethodSource("patternsWithSymbols")
    public void allowInApplyPattern(String pattern, String expected, int limit) {
        var cf = new ChoiceFormat("");
        cf.applyPattern(pattern);
        assertEquals(expected, cf.format(limit));
    }

    private static Stream<Arguments> patternsWithSymbols() {
        return Stream.of(
                // CSR example
                Arguments.of("1#The code is #7281", "The code is #7281", 1),
                // Other examples
                Arguments.of("1#<", "<", 1),
                Arguments.of("1#foo<", "foo<", 1),
                Arguments.of("1<foo\u2264", "foo\u2264", 1),
                Arguments.of("1\u2264foo#", "foo#", 1),
                Arguments.of("1#foo<#\u2264|2\u2264baz<#\u2264", "baz<#\u2264", 100),
                Arguments.of("1#foo<#\u2264|2#baz<#\u2264|3<bar##\u2264", "bar##\u2264", 100),
                Arguments.of("1#foo<#\u2264|2#baz<#\u2264|3\u2264bar##\u2264", "bar##\u2264", 100)
        );
    }
}
