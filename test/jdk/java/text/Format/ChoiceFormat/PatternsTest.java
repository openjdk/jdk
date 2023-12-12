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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class PatternsTest {

    private static final String ERR1 =
            "Each interval must contain a number before a format";
    private static final String ERR2 =
            "Incorrect order of intervals, must be in ascending order";

    // Check that some valid patterns do not throw an exception. Check
    // them against the expected values they should be formatted as.
    @ParameterizedTest
    @MethodSource
    public void validPatternsTest(String pattern, String[] expectedValues) {
        var fmt = new ChoiceFormat(pattern);
        for (int i=1; i<=expectedValues.length; i++) {
            assertEquals(expectedValues[i-1], fmt.format(i),
                    String.format("ChoiceFormat formatted %s incorrectly:", i));
        }
    }

    // Valid patterns ranging from normal appearing to odd. These should not
    // throw an exception or discard any portions of the pattern.
    private static Arguments[] validPatternsTest() {
        return new Arguments[] {
                // Multi pattern with trailing empty string Format
                arguments("1#foo|2#bar|3#", new String[]{"foo", "bar", ""}),
                // Multi patten with trailing '|'
                arguments("1#foo|2#bar|", new String[]{"foo", "bar"}),
                // Using a '>' (not a Relation) within a Format
                arguments("1#foo|2#bar>", new String[]{"foo", "bar>"}),
                // Standard Multi Pattern
                arguments("1#foo|2#bar", new String[]{"foo", "bar"}),
                // Same numerical value Limits, different Relations
                arguments("1#foo|1<baz", new String[]{"foo", "baz"}),
                // Standard Single Pattern
                arguments("1#foo", new String[]{"foo"}),
                // Single pattern with empty string Format
                arguments("1#", new String[]{""})
        };
    }

    // Check that the incorrect pattern throws an IAE with the desired error msg
    // This also tests applyPattern, as the ChoiceFormat constructor calls applyPattern
    @ParameterizedTest
    @MethodSource
    public void invalidPatternsThrowsTest(String pattern, String errMsg) {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new ChoiceFormat(pattern));
        assertEquals(errMsg, ex.getMessage());
    }

    // Variety of patterns that break the ChoiceFormat pattern syntax and throw
    // an exception.
    private static Arguments[] invalidPatternsThrowsTest() {
        return new Arguments[] {
                arguments("#foo", ERR1), // No Limit
                arguments("0#foo|#|1#bar", ERR1), // Missing Relation in SubPattern
                arguments("#|", ERR1), // Missing Limit
                arguments("##|", ERR1), // Double Relations
                arguments("0#foo1#", ERR1), // SubPattern not separated by '|'
                arguments("0#foo#", ERR1), // Using a Relation in a format
                arguments("0#test|#", ERR1), // SubPattern missing Limit
                arguments("0#foo|3#bar|1#baz", ERR2), // Non-ascending Limits
        };
    }

    // Check that the incorrect pattern discards the trailing incorrect portion.
    // These incorrect patterns should ideally throw an exception, but for
    // behavioral compatibility reasons do not.
    @ParameterizedTest
    @MethodSource
    public void invalidPatternsDiscardedTest(String brokenPattern, String actualPattern) {
        var cf1 = new ChoiceFormat(brokenPattern);
        var cf2 = new ChoiceFormat(actualPattern);
        assertEquals(cf2, cf1,
                String.format("Expected %s, but got %s", cf2.toPattern(), cf1.toPattern()));
    }

    // Variety of incorrect patterns with the actual expected pattern
    // after discarding occurs.
    private static Arguments[] invalidPatternsDiscardedTest() {
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

    // Calling format() with empty limits and formats
    // throws an ArrayIndexOutOfBoundsException
    @Test
    public void emptyLimitsAndFormatsTest() {
        var cf1 = new ChoiceFormat("");
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> cf1.format(1));

        var cf2 = new ChoiceFormat(new double[]{}, new String[]{});
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> cf2.format(2));
    }
}
