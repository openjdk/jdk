/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4266589 8031145 8164791 8316696 8368001
 * @summary NumberFormat round trip testing of parsing and formatting.
 *      This test checks 4 factory instances per locale against ~20 numeric inputs.
 *      Samples ~1/4 of the available locales provided by NumberFormat.
 * @key randomness
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run junit NumberRoundTrip
 */

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import jdk.test.lib.RandomFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * This class tests the round-trip behavior of NumberFormat, DecimalFormat, and DigitList.
 * Round-trip behavior is tested by taking a numeric value and formatting it, then
 * parsing the resulting string, and comparing this result with the original value.
 * Two tests are applied:  String preservation, and numeric preservation.  String
 * preservation is exact; numeric preservation is not.  However, numeric preservation
 * should extend to the few least-significant bits.
 */
public class NumberRoundTrip {

    private static final Random RND = RandomFactory.getRandom();

    @ParameterizedTest
    @MethodSource
    void testNumberFormatRoundTrip(NumberFormat fmt) {
        fmt.setMaximumFractionDigits(Integer.MAX_VALUE);
        Stream.concat(numbers.stream(), randomNumbers())
                .forEach(num -> test(fmt, num));
    }

    private void test(NumberFormat fmt, Number num) {
        String originalFormatted = fmt.format(num);
        Number parsedNum = Assertions.assertDoesNotThrow(() -> fmt.parse(originalFormatted),
                "Failed parse(format(%s))".formatted(num));
        String parsedFormatted = fmt.format(parsedNum);
        var equal = originalFormatted.equals(parsedFormatted);
        // Try BigDecimal parsing, if not equal
        if (!equal) {
            var df = Assertions.assertInstanceOf(DecimalFormat.class, fmt);
            df.setParseBigDecimal(true);
            parsedNum = Assertions.assertDoesNotThrow(() -> fmt.parse(originalFormatted),
                    "Failed BigDecimal parse(format(%s))".formatted(num));
            parsedFormatted = fmt.format(parsedNum);
            df.setParseBigDecimal(false);
            Assertions.assertEquals(originalFormatted, parsedFormatted,
                    "Failed to round-trip format(parse(format(%s)))".formatted(num));
        }
        // Numeric mismatch to the amount of 1e-14 is tolerable
        var error = proportionalError(num, parsedNum);
        Assertions.assertFalse(error > 1e-14,
                "Round tripping %s caused numeric error: %s".formatted(num, error));
    }

    // Regular, number, currency, and percent instance per locale
    private static Stream<Arguments> testNumberFormatRoundTrip() {
        return Stream.concat(
                // Default Locale
                Stream.of(
                        Arguments.of(NumberFormat.getInstance()),
                        Arguments.of(NumberFormat.getNumberInstance()),
                        Arguments.of(NumberFormat.getCurrencyInstance()),
                        Arguments.of(NumberFormat.getPercentInstance())),
                // ~1000 locales returned from provider.
                // Too expensive to test all locales, so sample a reasonable amount
                Arrays.stream(NumberFormat.getAvailableLocales())
                        .filter(_ -> RND.nextDouble() < .25)
                        .flatMap(loc -> Stream.of(
                        Arguments.of(NumberFormat.getInstance(loc)),
                        Arguments.of(NumberFormat.getNumberInstance(loc)),
                        Arguments.of(NumberFormat.getCurrencyInstance(loc)),
                        Arguments.of(NumberFormat.getPercentInstance(loc)))
                )
        );
    }

    // Fixed set of numbers to test each locale against
    private static final List<Number> numbers = List.of(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            500,
            0,
            5555555555555555L,
            55555555555555555L,
            9223372036854775807L,
            9223372036854775808.0,
            -9223372036854775808L,
            -9223372036854775809.0
    );

    // Compute fresh batch of random numbers per locale
    private Stream<Number> randomNumbers() {
        return Stream.of(
                randomDouble(1),
                randomDouble(10000),
                Math.floor(randomDouble(10000)),
                randomDouble(1e50),
                randomDouble(1e-50),
                randomDouble(1e100),
                // The use of double d such that isInfinite(100d) causes the
                // numeric test to fail with percent formats (bug 4266589).
                // Largest double s.t. 100d < Inf: d=1.7976931348623156E306
                randomDouble(1e306),
                randomDouble(1e-323),
                randomDouble(1e-100)
        );
    }

    // Return a random value from -range..+range.
    private static double randomDouble(double range) {
        return RND.nextDouble(-range, range);
    }

    private static double proportionalError(Number a, Number b) {
        double aa = a.doubleValue(), bb = b.doubleValue();
        double error = aa - bb;
        if (aa != 0 && bb != 0) error /= aa;
        return Math.abs(error);
    }
}
