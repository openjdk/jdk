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
 * @bug 6801704
 * @summary Test the expected behavior for a wide range of patterns (both
 *          correct and incorrect). This test documents the behavior of incorrect
 *          ChoiceFormat patterns either throwing an exception, or discarding
 *          the incorrect portion of a pattern.
 * @run junit PatternsTest
 */

import java.text.ChoiceFormat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class PatternsTest {

    private static final String err1 =
            "Each interval must contain a number before a format";
    private static final String err2 =
            "Incorrect order of intervals, must be in ascending order";

    // Check that some valid patterns do not throw an exception
    @ParameterizedTest
    @MethodSource("validPatterns")
    public void validPatternsTest(String pattern) {
        assertDoesNotThrow( ()-> new ChoiceFormat(pattern),
                "Valid pattern threw an exception");
    }

    // Valid patterns ranging from normal appearing to odd
    private static String[] validPatterns() {
        return new String[] {
                "1#foo|2#foo|", // Trailing '|'
                "1#foo|1<baz", // Same numerical value Limits, different Relations
                "1#foo|2#bar>", // Using a '>' (not a Relation) within a Format
                "1#|2<|3#", // Format can be an empty string
                "1#foo", // normal pattern
                "1#foo|2#bar", // normal multi pattern
                "1#" // normal pattern with empty string Format
        };
    }

    // Check that the incorrect pattern throws an IAE with the desired error msg
    // This also tests applyPattern, as the ChoiceFormat constructor calls applyPattern
    @ParameterizedTest
    @MethodSource
    public void invalidPatternsThrows(String pattern, String errMsg) {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new ChoiceFormat(pattern));
        assertEquals(errMsg, ex.getMessage());
    }

    // Variety of patterns that break the ChoiceFormat pattern syntax and throw
    // an exception.
    private static Arguments[] invalidPatternsThrows() {
        return new Arguments[] {
                arguments("#foo", err1), // No Limit
                arguments("0#foo|#|1#bar", err1), // Missing Relation in SubPattern
                arguments("#|", err1), // Missing Limit
                arguments("##|", err1), // Double Relations
                arguments("0#foo1#", err1), // SubPattern not separated by '|'
                arguments("0#foo#", err1), // Using a Relation in a format
                arguments("0#test|#", err1), // SubPattern missing Limit
                arguments("0#foo|3#bar|1#baz", err2), // Non-ascending Limits
        };
    }

    // Check that the incorrect pattern discards the trailing incorrect portion
    // These incorrect patterns should ideally throw an exception, but for
    // behavioral compatibility reasons do not.
    @ParameterizedTest
    @MethodSource
    public void invalidPatternsDiscarded(String brokenPattern, String actualPattern) {
        var cf1 = new ChoiceFormat(brokenPattern);
        var cf2 = new ChoiceFormat(actualPattern);
        assertEquals(cf2, cf1,
                String.format("Expected %s, but got %s", cf2.toPattern(), cf1.toPattern()));
        if (actualPattern.isEmpty()) {
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> cf1.format(1));
        }
    }

    // Variety of incorrect patterns with the actual expected pattern
    private static Arguments[] invalidPatternsDiscarded() {
        return new Arguments[] {
                // Incomplete SubPattern at the end of the Pattern
                arguments("0#foo|1#bar|baz", "0#foo|1#bar"),

                // --- These throw an ArrayIndexOutOfBoundsException
                // when attempting to format with them ---
                // SubPattern with only a Limit (which is interpreted as a Format)
                arguments("0", ""),
                // SubPattern with only a Format
                arguments("foo", ""),
                // empty string
                arguments("", "")
        };
    }
}
