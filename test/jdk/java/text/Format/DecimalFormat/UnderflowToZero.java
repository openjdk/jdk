/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
* @bug 8174722
* @summary Tests underflow for rounding values < abs(0.1) in DecimalFormat
* @run junit UnderflowToZero
*/

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnderflowToZero {
    private static final List<RoundingMode> MODES;
    private static final String ERRMSG = "%f formatted with pattern %s and mode " +
            "%s gives %s";

    static {
        MODES = new ArrayList<>(Arrays.asList(RoundingMode.values()));
        MODES.remove(RoundingMode.UNNECESSARY);
    }

    @ParameterizedTest
    @MethodSource("numberAndPattern")
    public void testModes(double num, String pattern) {
        DecimalFormat df = new DecimalFormat();
        df.applyPattern(pattern);
        for (RoundingMode mode : MODES) {
            testFormat(num, pattern, mode, df);
        }
    }

    @Test
    public void testZero() {
        DecimalFormat df = new DecimalFormat();
        df.applyPattern("0.0");
        for (RoundingMode mode : MODES) {
            df.setRoundingMode(mode);
            String decimalFormatted = df.format(0.0000);
            assertEquals(decimalFormatted, "0.0");
        }
    }

    // Ensure formatting values less than abs(.1) adheres
    // to contracts of certain rounding modes
    private void testFormat(double smallNum, String pattern, RoundingMode mode,
                            DecimalFormat df) {
        validateData(smallNum, pattern);
        df.setRoundingMode(mode);
        df.applyPattern(pattern);
        String formattedNum = df.format(smallNum);
        long oneCount = formattedNum.chars().filter(ch -> ch == '1').count();
        String error = String.format(ERRMSG, smallNum, pattern, mode, formattedNum);
        if (shouldRound(smallNum, mode)) {
            // Expecting a single occurrence of 1 as the last digit
            assertEquals("1", formattedNum.substring(
                    formattedNum.length() - 1), error);
            assertEquals(1, oneCount, error);
        } else {
            // Expecting no occurrences of 1
            assertEquals("0", formattedNum.substring(
                    formattedNum.length() - 1), error);
            assertEquals(0, oneCount, error);
        }
    }

    private Boolean shouldRound(double smallNum, RoundingMode mode) {
        if (mode == RoundingMode.UP) {
            return true;
        } else if (mode == RoundingMode.CEILING && smallNum > 0) {
            return true;
        } else return mode == RoundingMode.FLOOR && smallNum < 0;
    }

    private void validateData(double number, String pattern) {
        // Sum 0s in pattern, exclude the integer 0
        long zeroCount = pattern.split("\\.")[1].length();
        if (countZeros(number) <= zeroCount) {
            throw new RuntimeException("Data is not in right format, " +
                    "see comments above method source");
        }
    }

    // Utility function to count the fractional zeros
    // of a number less than abs(1)
    private int countZeros(double num) {
        int zeros = 0;
        double number = Math.abs(num);
        while (number < 1) {
            zeros = zeros + 1;
            number = number * 10;
        }
        return zeros - 1;
    }

    // For the supplied data, the number must have more zeros between the decimal
    // and first non-zero digit than digits in the fractional portion of the pattern
    private static Stream<Arguments> numberAndPattern() {
        return Stream.of(
                Arguments.of(0.00001, "0.0"),
                Arguments.of(0.00001, "0.00"),
                Arguments.of(0.00001, "0.000"),

                Arguments.of(-0.00001, "0.0"),
                Arguments.of(-0.00001, "0.00"),
                Arguments.of(-0.00001, "0.000"),

                Arguments.of(0.00009, "0.0"),
                Arguments.of(0.00009, "0.00"),
                Arguments.of(0.00009, "0.000"),

                Arguments.of(-0.00009, "0.0"),
                Arguments.of(-0.00009, "0.00"),
                Arguments.of(-0.00009, "0.000"),

                Arguments.of(0.00004545, "0.0"),
                Arguments.of(0.00004545, "0.00"),
                Arguments.of(0.00004545, "0.000"),

                Arguments.of(-0.00004545, "0.0"),
                Arguments.of(-0.00004545, "0.00"),
                Arguments.of(-0.00004545, "0.000")
        );
    }
}
