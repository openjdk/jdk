/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8177552 8392400
 * @summary Checks the rounding of formatted number in compact number formatting
 * @run junit/othervm TestCNFRounding
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestCNFRounding {

    private static final List<RoundingMode> MODES = List.of(
            RoundingMode.HALF_EVEN,
            RoundingMode.HALF_UP,
            RoundingMode.HALF_DOWN,
            RoundingMode.UP,
            RoundingMode.DOWN,
            RoundingMode.CEILING,
            RoundingMode.FLOOR);

    private static final List<Arguments> BIG_NUMBER_ROUNDING_ARGS = List.of(
            // Basic BigDecimal input
            Arguments.of(BigDecimal.valueOf(21_534_567.20), "21.53M", 2, RoundingMode.HALF_UP),
            Arguments.of(BigDecimal.valueOf(-21_534_567.20), "-21.53M", 2, RoundingMode.HALF_UP),
            // RoundingMode UP/DOWN
            Arguments.of(BigDecimal.valueOf(21_534_567.20), "21.54M", 2, RoundingMode.UP),
            Arguments.of(BigDecimal.valueOf(21_534_567.20), "21.53M", 2, RoundingMode.DOWN),
            // Values that border between two patterns depending on maximum fraction digits allowed
            Arguments.of(BigDecimal.valueOf(999_951), "999.95K", 2, RoundingMode.HALF_UP),
            Arguments.of(BigDecimal.valueOf(-999_951), "-999.95K", 2, RoundingMode.HALF_UP),
            Arguments.of(BigDecimal.valueOf(999_951), "1M", 1, RoundingMode.HALF_UP),
            Arguments.of(BigDecimal.valueOf(-999_951), "-1M", 1, RoundingMode.HALF_UP),
            // Negative CEILING/FLOOR values
            Arguments.of(BigDecimal.valueOf(-999_999), "-999.99K", 2, RoundingMode.CEILING),
            Arguments.of(BigDecimal.valueOf(-999_999), "-1M", 2, RoundingMode.FLOOR),
            // BigInteger path that forces BigDecimal division.
            // Need to supply a BI whose underlying value exceeds what 64 bit long supports and whose
            // division result would produce a fraction. LONG.MAX/MIN_VALUE +- 1 works.
            Arguments.of(new BigInteger("9223372036854775808"), "9223372.04T", 2, RoundingMode.HALF_UP),
            Arguments.of(new BigInteger("-9223372036854775809"), "-9223372.04T", 2, RoundingMode.HALF_UP)
    );

    Object[][] roundingData() {
        return new Object[][]{
            // Number, half_even, half_up, half_down, up, down, ceiling, floor
            {5500, new String[]{"6K", "6K", "5K", "6K", "5K", "6K", "5K"}},
            {2500, new String[]{"2K", "3K", "2K", "3K", "2K", "3K", "2K"}},
            {1600, new String[]{"2K", "2K", "2K", "2K", "1K", "2K", "1K"}},
            {1100, new String[]{"1K", "1K", "1K", "2K", "1K", "2K", "1K"}},
            {1000, new String[]{"1K", "1K", "1K", "1K", "1K", "1K", "1K"}},
            {-1000, new String[]{"-1K", "-1K", "-1K", "-1K", "-1K", "-1K", "-1K"}},
            {-1100, new String[]{"-1K", "-1K", "-1K", "-2K", "-1K", "-1K", "-2K"}},
            {-1600, new String[]{"-2K", "-2K", "-2K", "-2K", "-1K", "-1K", "-2K"}},
            {-2500, new String[]{"-2K", "-3K", "-2K", "-3K", "-2K", "-2K", "-3K"}},
            {-5500, new String[]{"-6K", "-6K", "-5K", "-6K", "-5K", "-5K", "-6K"}},
            {5501, new String[]{"6K", "6K", "6K", "6K", "5K", "6K", "5K"}},
            {-5501, new String[]{"-6K", "-6K", "-6K", "-6K", "-5K", "-5K", "-6K"}},
            {1001, new String[]{"1K", "1K", "1K", "2K", "1K", "2K", "1K"}},
            {-1001, new String[]{"-1K", "-1K", "-1K", "-2K", "-1K", "-1K", "-2K"}},
            {4501, new String[]{"5K", "5K", "5K", "5K", "4K", "5K", "4K"}},
            {-4501, new String[]{"-5K", "-5K", "-5K", "-5K", "-4K", "-4K", "-5K"}},
            {4500, new String[]{"4K", "5K", "4K", "5K", "4K", "5K", "4K"}},
            {-4500, new String[]{"-4K", "-5K", "-4K", "-5K", "-4K", "-4K", "-5K"}},};
    }

    Object[][] roundingFract() {
        return new Object[][]{
            // Number, half_even, half_up, half_down, up, down, ceiling, floor
            {5550, new String[]{"5.5K", "5.5K", "5.5K", "5.6K", "5.5K", "5.6K", "5.5K"}},
            {2550, new String[]{"2.5K", "2.5K", "2.5K", "2.6K", "2.5K", "2.6K", "2.5K"}},
            {1660, new String[]{"1.7K", "1.7K", "1.7K", "1.7K", "1.6K", "1.7K", "1.6K"}},
            {1110, new String[]{"1.1K", "1.1K", "1.1K", "1.2K", "1.1K", "1.2K", "1.1K"}},
            {1000, new String[]{"1.0K", "1.0K", "1.0K", "1.0K", "1.0K", "1.0K", "1.0K"}},
            {-1000, new String[]{"-1.0K", "-1.0K", "-1.0K", "-1.0K", "-1.0K", "-1.0K", "-1.0K"}},
            {-1110, new String[]{"-1.1K", "-1.1K", "-1.1K", "-1.2K", "-1.1K", "-1.1K", "-1.2K"}},
            {-1660, new String[]{"-1.7K", "-1.7K", "-1.7K", "-1.7K", "-1.6K", "-1.6K", "-1.7K"}},
            {-2550, new String[]{"-2.5K", "-2.5K", "-2.5K", "-2.6K", "-2.5K", "-2.5K", "-2.6K"}},
            {-5550, new String[]{"-5.5K", "-5.5K", "-5.5K", "-5.6K", "-5.5K", "-5.5K", "-5.6K"}},
            {5551, new String[]{"5.6K", "5.6K", "5.6K", "5.6K", "5.5K", "5.6K", "5.5K"}},
            {-5551, new String[]{"-5.6K", "-5.6K", "-5.6K", "-5.6K", "-5.5K", "-5.5K", "-5.6K"}},
            {1001, new String[]{"1.0K", "1.0K", "1.0K", "1.1K", "1.0K", "1.1K", "1.0K"}},
            {-1001, new String[]{"-1.0K", "-1.0K", "-1.0K", "-1.1K", "-1.0K", "-1.0K", "-1.1K"}},
            {4551, new String[]{"4.6K", "4.6K", "4.6K", "4.6K", "4.5K", "4.6K", "4.5K"}},
            {-4551, new String[]{"-4.6K", "-4.6K", "-4.6K", "-4.6K", "-4.5K", "-4.5K", "-4.6K"}},
            {4500, new String[]{"4.5K", "4.5K", "4.5K", "4.5K", "4.5K", "4.5K", "4.5K"}},
            {-4500, new String[]{"-4.5K", "-4.5K", "-4.5K", "-4.5K", "-4.5K", "-4.5K", "-4.5K"}},};
    }

    Object[][] rounding2Fract() {
        return new Object[][]{
            // Number, half_even, half_up, half_down
            {1115, new String[]{"1.11K", "1.11K", "1.11K"}},
            {1125, new String[]{"1.12K", "1.13K", "1.12K"}},
            {1135, new String[]{"1.14K", "1.14K", "1.14K"}},
            {3115, new String[]{"3.12K", "3.12K", "3.12K"}},
            {3125, new String[]{"3.12K", "3.13K", "3.12K"}},
            {3135, new String[]{"3.13K", "3.13K", "3.13K"}},
            {6865, new String[]{"6.87K", "6.87K", "6.87K"}},
            {6875, new String[]{"6.88K", "6.88K", "6.87K"}},
            {6885, new String[]{"6.88K", "6.88K", "6.88K"}},
            {3124, new String[]{"3.12K", "3.12K", "3.12K"}},
            {3126, new String[]{"3.13K", "3.13K", "3.13K"}},
            {3128, new String[]{"3.13K", "3.13K", "3.13K"}},
            {6864, new String[]{"6.86K", "6.86K", "6.86K"}},
            {6865, new String[]{"6.87K", "6.87K", "6.87K"}},
            {6868, new String[]{"6.87K", "6.87K", "6.87K"}},
            {4685, new String[]{"4.68K", "4.68K", "4.68K"}},
            {4687, new String[]{"4.69K", "4.69K", "4.69K"}},
            {4686, new String[]{"4.69K", "4.69K", "4.69K"}},};
    }

    @Test
    void testNullMode() {
        assertThrows(NullPointerException.class, () -> {
            NumberFormat fmt = NumberFormat
                    .getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
            fmt.setRoundingMode(null);
        });
    }

    @Test
    void testDefaultRoundingMode() {
        NumberFormat fmt = NumberFormat
                .getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
        assertEquals(RoundingMode.HALF_EVEN, fmt.getRoundingMode(),
                "Default RoundingMode should be " + RoundingMode.HALF_EVEN);
    }

    @ParameterizedTest
    @MethodSource("roundingData")
    void testRounding(Object number, String[] expected) {
        for (int index = 0; index < MODES.size(); index++) {
            testRoundingMode(number, expected[index], 0, MODES.get(index));
        }
    }

    @ParameterizedTest
    @MethodSource("roundingFract")
    void testRoundingFract(Object number, String[] expected) {
        for (int index = 0; index < MODES.size(); index++) {
            testRoundingMode(number, expected[index], 1, MODES.get(index));
        }
    }

    @ParameterizedTest
    @MethodSource("rounding2Fract")
    void testRounding2Fract(Object number, String[] expected) {
        List<RoundingMode> rModes = List.of(RoundingMode.HALF_EVEN,
                RoundingMode.HALF_UP, RoundingMode.HALF_DOWN);
        for (int index = 0; index < rModes.size(); index++) {
            testRoundingMode(number, expected[index], 2, rModes.get(index));
        }
    }

    // 8392400 exposes that the minimum fractional digits is applied to the result of
    // the division operation which is calculated when applying the compact pattern.
    // This causes premature rounding, and produces an incorrect value.
    @ParameterizedTest
    @FieldSource("BIG_NUMBER_ROUNDING_ARGS")
    void testBigNumberRounding(Number num, String expected, int maxFrac, RoundingMode rm) {
        var fmt = NumberFormat.getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
        fmt.setMaximumFractionDigits(maxFrac);
        fmt.setRoundingMode(rm);
        assertEquals(expected, fmt.format(num));
    }

    private void testRoundingMode(Object number, String expected,
            int fraction, RoundingMode rounding) {
        NumberFormat fmt = NumberFormat
                .getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);
        fmt.setRoundingMode(rounding);
        assertEquals(rounding, fmt.getRoundingMode(),
                "RoundingMode set is not returned by getRoundingMode");

        fmt.setMinimumFractionDigits(fraction);
        String result = fmt.format(number);
        assertEquals(expected, result, "Incorrect formatting of number "
                + number + " using rounding mode: " + rounding);
    }

}
