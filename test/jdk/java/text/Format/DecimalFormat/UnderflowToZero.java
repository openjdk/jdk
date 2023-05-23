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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnderflowToZero {
    private static final RoundingMode[] MODES = {RoundingMode.DOWN,
            RoundingMode.HALF_EVEN, RoundingMode.HALF_UP, RoundingMode.HALF_DOWN,
            RoundingMode.FLOOR, RoundingMode.CEILING, RoundingMode.UP};
    private static final String ERRMSG = "%f formatted with pattern %s and mode " +
            "%s gives %s but %f formatted with the same pattern and mode gives %s";

    @ParameterizedTest
    @MethodSource("patternAndNumbers")
    public void testModes(double bigger, double smaller, String pattern) {
        DecimalFormat df = new DecimalFormat();
        df.applyPattern(pattern);
        for (RoundingMode mode : MODES) {
            testFormat(bigger, smaller, pattern, mode, df);
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
    // to contracts of rounding modes. To ensure this, we can compare
    // the fractional portion of the result to the fractional portion
    // of the same value + 1;
    private void testFormat(double bigger, double smaller, String pattern,
                            RoundingMode mode, DecimalFormat df) {
        df.setRoundingMode(mode);
        // Compare the fractional part of both numbers
        // Eg: Compare 1.(0001) to 0.(0001)
        String biggerFormatted = df.format(bigger).split("\\.")[1];
        String smallerFormatted = df.format(smaller).split("\\.")[1];
        assertEquals(biggerFormatted, smallerFormatted, String.format(ERRMSG, bigger, pattern,
                mode, df.format(bigger), smaller, df.format(smaller)));
    }

    private static Stream<Arguments> patternAndNumbers() {
        return Stream.of(
                Arguments.of(1.0001, 0.0001, "0.0"),
                Arguments.of(1.0001, 0.0001, "0.00"),
                Arguments.of(1.0001, 0.0001, "0.000"),

                Arguments.of(-1.0001, -0.0001, "0.0"),
                Arguments.of(-1.0001, -0.0001, "0.00"),
                Arguments.of(-1.0001, -0.0001, "0.000"),

                Arguments.of(1.0009, 0.0009, "0.0"),
                Arguments.of(1.0009, 0.0009, "0.00"),
                Arguments.of(1.0009, 0.0009, "0.000"),

                Arguments.of(-1.0009, -0.0009, "0.0"),
                Arguments.of(-1.0009, -0.0009, "0.00"),
                Arguments.of(-1.0009, -0.0009, "0.000"),

                Arguments.of(1.0004545, 0.0004545, "0.0"),
                Arguments.of(1.0004545, 0.0004545, "0.00"),
                Arguments.of(1.0004545, 0.0004545, "0.000"),

                Arguments.of(-1.0004545, -0.0004545, "0.0"),
                Arguments.of(-1.0004545, -0.0004545, "0.00"),
                Arguments.of(-1.0004545, -0.0004545, "0.000")
        );
    }
}
