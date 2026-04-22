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
 * @bug 8331485
 * @summary Ensure correctness when parsing large (+/-) exponent values that
 *          exceed Integer.MAX_VALUE and Long.MAX_VALUE.
 * @run junit/othervm --add-opens java.base/java.text=ALL-UNNAMED LargeExponentsTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

// We prevent odd results when parsing large exponent values by ensuring
// that we properly handle overflow in the implementation of DigitList
public class LargeExponentsTest {

    // Exponent symbol is 'E'
    private static final NumberFormat FMT = NumberFormat.getInstance(Locale.US);

    // Check that the parsed value and parse position index are both equal to the expected values.
    // We are mainly checking that an exponent > Integer.MAX_VALUE no longer
    // parses to 0 and that an exponent > Long.MAX_VALUE no longer parses to the mantissa.
    @ParameterizedTest
    @MethodSource({"largeExponentValues", "smallExponentValues", "bugReportValues", "edgeCases"})
    public void overflowTest(String parseString, Double expectedValue) throws ParseException {
        checkParse(parseString, expectedValue);
        checkParseWithPP(parseString, expectedValue);
    }

    // A separate white-box test to avoid the memory consumption of testing cases
    // when the String is near Integer.MAX_LENGTH
    @ParameterizedTest
    @MethodSource
    public void largeDecimalAtExponentTest(int expected, int decimalAt, long expVal)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        DecimalFormat df = new DecimalFormat();
        Method m = df.getClass().getDeclaredMethod(
                "shiftDecimalAt", int.class, long.class);
        m.setAccessible(true);
        assertEquals(expected, m.invoke(df, decimalAt, expVal));
    }

    // Cases where we can test behavior when the String is near Integer.MAX_LENGTH
    private static Stream<Arguments> largeDecimalAtExponentTest() {
        return Stream.of(
                // Equivalent to testing Arguments.of("0."+"0".repeat(Integer.MAX_VALUE-20)+"1"+"E2147483650", 1.0E22)
                // This is an absurdly long decimal string with length close to Integer.MAX_VALUE
                // where the decimal position correctly negates the exponent value, even if it exceeds Integer.MAX_VALUE
                Arguments.of(23, -(Integer.MAX_VALUE-20), 3L+Integer.MAX_VALUE),
                Arguments.of(-23, Integer.MAX_VALUE-20, -(3L+Integer.MAX_VALUE)),
                Arguments.of(Integer.MIN_VALUE, -(Integer.MAX_VALUE-20), -(3L+Integer.MAX_VALUE)),
                Arguments.of(Integer.MAX_VALUE, Integer.MAX_VALUE-20, 3L+Integer.MAX_VALUE),
                Arguments.of(Integer.MAX_VALUE, -(Integer.MAX_VALUE-20), Long.MAX_VALUE),
                Arguments.of(Integer.MIN_VALUE, Integer.MAX_VALUE-20, Long.MIN_VALUE)
        );
    }

    // Checks the parse(String, ParsePosition) method
    public void checkParse(String parseString, Double expectedValue) {
        ParsePosition pp = new ParsePosition(0);
        Number actualValue = FMT.parse(parseString, pp);
        assertEquals(expectedValue, (double)actualValue);
        assertEquals(parseString.length(), pp.getIndex());
    }

    // Checks the parse(String) method
    public void checkParseWithPP(String parseString, Double expectedValue)
            throws ParseException {
        Number actualValue = FMT.parse(parseString);
        assertEquals(expectedValue, (double)actualValue);
    }

    // Generate large enough exponents that should all be parsed as infinity
    // when positive. This includes exponents that exceed Long.MAX_VALUE
    private static List<Arguments> largeExponentValues() {
        return createExponentValues(false);
    }

    // Same as previous provider but for negative exponent values, so expecting
    // a parsed value of 0.
    private static List<Arguments> smallExponentValues() {
        return createExponentValues(true);
    }

    // Programmatically generate some large parse values that are expected
    // to be parsed as infinity or 0
    private static List<Arguments> createExponentValues(boolean negative) {
        List<Arguments> args = new ArrayList<>();
        // Start with a base value that should be parsed as infinity
        String baseValue = "12234.123E1100";
        // Continuously add to the String until we trigger the overflow condition
        for (int i = 0; i < 100; i++) {
            StringBuilder bldr = new StringBuilder();
            // Add to exponent
            bldr.append(baseValue).append("1".repeat(i));
            // Add to mantissa
            bldr.insert(0, "1".repeat(i));
            args.add(Arguments.of(
                    // Prepend "-" to exponent if negative
                    negative ? bldr.insert(bldr.indexOf("E")+1, "-").toString() : bldr.toString(),
                    // Expect 0 if negative, else infinity
                    negative ? 0.0 : Double.POSITIVE_INFINITY));
        }
        return args;
    }

    // The provided values are all from the JBS issue
    // These contain exponents that exceed Integer.MAX_VALUE, but not Long.MAX_VALUE
    private static Stream<Arguments> bugReportValues() {
        return Stream.of(
                Arguments.of("0.123E1", 1.23),
                Arguments.of("0.123E309", 1.23E308),
                Arguments.of("0.123E310", Double.POSITIVE_INFINITY),
                Arguments.of("0.123E2147483647", Double.POSITIVE_INFINITY),
                Arguments.of("0.123E2147483648", Double.POSITIVE_INFINITY),
                Arguments.of("0.0123E2147483648", Double.POSITIVE_INFINITY),
                Arguments.of("0.0123E2147483649", Double.POSITIVE_INFINITY),
                Arguments.of("1.23E2147483646", Double.POSITIVE_INFINITY),
                Arguments.of("1.23E2147483647", Double.POSITIVE_INFINITY),
                Arguments.of("0.123E4294967296", Double.POSITIVE_INFINITY),
                Arguments.of("0.123E-322", 9.9E-324),
                Arguments.of("0.123E-323", 0.0),
                Arguments.of("0.123E-2147483647", 0.0),
                Arguments.of("0.123E-2147483648", 0.0),
                Arguments.of("0.123E-2147483649", 0.0)
        );
    }

    // Some other edge case values to ensure parse correctness
    private static Stream<Arguments> edgeCases() {
        return Stream.of(
                // Exponent itself does not cause underflow, but decimalAt adjustment
                // based off mantissa should. decimalAt(-1) + exponent(Integer.MIN_VALUE) = underflow
                Arguments.of("0.0123E-2147483648", 0.0),
                // 0 exponent
                Arguments.of("1.23E0", 1.23),
                // Leading zeroes
                Arguments.of("1.23E0000123", 1.23E123),
                // Leading zeroes - Past Long.MAX_VALUE length
                Arguments.of("1.23E00000000000000000000000000000000000000000123", 1.23E123),
                // Trailing zeroes
                Arguments.of("1.23E100", 1.23E100),
                // Long.MAX_VALUE length
                Arguments.of("1.23E1234567891234567800", Double.POSITIVE_INFINITY),
                // Long.MAX_VALUE with trailing zeroes
                Arguments.of("1.23E9223372036854775807000", Double.POSITIVE_INFINITY),
                // Long.MIN_VALUE
                Arguments.of("1.23E-9223372036854775808", 0.0),
                // Exponent value smaller than Long.MIN_VALUE
                Arguments.of("1.23E-9223372036854775809", 0.0),
                // Exponent value equal to Long.MAX_VALUE
                Arguments.of("1.23E9223372036854775807", Double.POSITIVE_INFINITY),
                // Exponent value larger than Long.MAX_VALUE
                Arguments.of("1.23E9223372036854775808", Double.POSITIVE_INFINITY)
        );
    }
}
