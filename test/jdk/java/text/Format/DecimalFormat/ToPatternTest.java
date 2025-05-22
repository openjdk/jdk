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
 * @bug 8326908
 * @summary Verify DecimalFormat::toPattern correctness.
 * @run junit ToPatternTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.DecimalFormat;
import java.util.stream.Stream;

public class ToPatternTest {

    // DecimalFormat constant
    private static final int DOUBLE_FRACTION_DIGITS = 340;

    // Ensure that toPattern() provides the correct amount of minimum
    // and maximum digits for integer/fraction.
    @ParameterizedTest
    @MethodSource("minMaxDigits")
    public void basicTest(int maxInt, int minInt, int maxFrac, int minFrac) {
        DecimalFormat dFmt = new DecimalFormat();

        dFmt.setMaximumIntegerDigits(maxInt);
        dFmt.setMinimumIntegerDigits(minInt);
        dFmt.setMaximumFractionDigits(maxFrac);
        dFmt.setMinimumFractionDigits(minFrac);

        // Non-localized separator always uses '.'
        String[] patterns = dFmt.toPattern().split("\\.");
        assertEquals(2, patterns.length,
                dFmt.toPattern() + " should be split into an integer/fraction portion");
        String integerPattern = patterns[0];
        String fractionPattern = patterns[1];

        // Count # and 0 explicitly (since there are grouping symbols)
        assertEquals(integerPattern.chars().filter(ch -> ch == '0').count() +
                integerPattern.chars().filter(ch -> ch == '#').count(),
                Math.max(dFmt.getGroupingSize(), dFmt.getMinimumIntegerDigits()) + 1);
        assertEquals(integerPattern.chars().filter(ch -> ch == '0').count(), dFmt.getMinimumIntegerDigits());
        assertEquals(fractionPattern.length(), dFmt.getMaximumFractionDigits());
        assertEquals(fractionPattern.chars().filter(ch -> ch == '0').count(), dFmt.getMinimumFractionDigits());
    }

    // General and edge cases for the min and max Integer/Fraction digits
    private static Stream<Arguments> minMaxDigits() {
        return Stream.of(
                Arguments.of(10, 5, 10, 5),
                Arguments.of(0, 0, 1, 1),
                Arguments.of(1, 1, 1, 1),
                Arguments.of(5, 5, 5, 5),
                Arguments.of(5, 10, 5, 10),
                Arguments.of(333, 27, 409, 3)
        );
    }

    // Ensure that a NegativePattern is explicitly produced when required.
    @Test
    public void negativeSubPatternTest() {
        DecimalFormat dFmt = new DecimalFormat();
        dFmt.setPositivePrefix("foo");
        dFmt.setPositiveSuffix("bar");
        dFmt.setNegativePrefix("baz");
        dFmt.setNegativeSuffix("qux");

        String[] patterns = dFmt.toPattern().split(";");
        assertEquals(2, patterns.length,
                "There should be a positivePattern and negativePattern");
        String positivePattern = patterns[0];
        String negativePattern = patterns[1];

        assertTrue(positivePattern.startsWith(dFmt.getPositivePrefix()));
        assertTrue(positivePattern.endsWith(dFmt.getPositiveSuffix()));
        assertTrue(negativePattern.startsWith(dFmt.getNegativePrefix()));
        assertTrue(negativePattern.endsWith(dFmt.getNegativeSuffix()));
    }

    // 8326908: Verify that an empty pattern DecimalFormat does not throw an
    // OutOfMemoryError when toPattern() is invoked. Behavioral change of
    // MAXIMUM_INTEGER_DIGITS replaced with DOUBLE_FRACTION_DIGITS for empty
    // pattern initialization.
    @Test
    public void emptyStringPatternTest() {
        DecimalFormat empty = new DecimalFormat("");
        // Verify new maximum fraction digits value
        assertEquals(DOUBLE_FRACTION_DIGITS, empty.getMaximumFractionDigits());
        // Verify no OOME for empty pattern
        assertDoesNotThrow(empty::toPattern);
        // Check toString for coverage, as it invokes toPattern
        assertDoesNotThrow(empty::toString);
    }

    // Verify that only the last grouping interval is used for the grouping size.
    @Test
    public void groupingSizeTest() {
        assertEquals(22,
                new DecimalFormat( "###,####,"+"#".repeat(22)).getGroupingSize());
    }
}
