/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4838107 8008577
 * @summary Confirm that DecimalFormat can format a number with a negative
 *          exponent number correctly. Tests also involve using a DecimalFormat
 *          with a custom pattern or a custom minus sign.
 * @run junit/othervm -Djava.locale.providers=COMPAT,SPI Bug4838107
 */

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * This bug is about exponential formatting. But I added test cases for:
 *   - Double and BigDecimal numbers which don't have exponent parts.
 *   - Long and BigInteger numbers which don't support exponential
 *     notation.
 * because there are few test cases for suffix and prefix.
 * And also, I added test cases to guarantee further formatting and
 * parsing using the same DecimalFormat instance will not change the
 * Number's value anymore.
 */
public class Bug4838107 {

    // Save JVM default Locale
    private static final Locale savedLocale = Locale.getDefault();

    // Set JVM default Locale to US
    @BeforeAll
    static void init() {
        Locale.setDefault(Locale.US);
    }

    // Restore the original JVM default locale
    @AfterAll
    static void tearDown() {
        Locale.setDefault(savedLocale);
    }

    // Check that negative exponent number recognized for doubles
    @ParameterizedTest
    @MethodSource("doubles")
    public void doubleTest(Number num, String str, DecimalFormat df) {
        test(num, str, df);
    }

    // Provides a double to be formatted, which is compared to the expected String.
    // Additionally, provides a DecimalFormat to do the formatting (can have a custom
    // pattern and minus sign). Given in the form (double, String, DecimalFormat).
    private static Stream<Arguments> doubles() {
        DecimalFormat defaultDf = new DecimalFormat();
        DecimalFormat customDf1 = getDecimalFormat("<P>#.###E00<S>", 'm');
        DecimalFormat customDf2 = getDecimalFormat("<P>#.###E00<S>;#.###E00", 'm');
        DecimalFormat customDf3 = getDecimalFormat("#.###E00;<P>#.###E00<S>", 'm');
        DecimalFormat customDf4 = getDecimalFormat("<P>#.###E00<S>;<p>-#.###E00<s>", 'm');
        return Stream.of(
                // Test with default pattern
                Arguments.of(1234D,    "1,234", defaultDf),
                Arguments.of(0.1234,  "0.123", defaultDf),    // rounded
                Arguments.of(-1234D,   "-1,234", defaultDf),
                Arguments.of(-0.1234, "-0.123", defaultDf),    // rounded
                Arguments.of(Double.POSITIVE_INFINITY, "\u221e", defaultDf),
                Arguments.of(Double.NEGATIVE_INFINITY, "-\u221e", defaultDf),
                Arguments.of(Double.NaN, "\ufffd", defaultDf), // without prefix and suffix
                Arguments.of(0.0,  "0", defaultDf),
                Arguments.of(-0.0, "-0", defaultDf),   // with the minus sign
                // Test with a pattern and the minus sign
                Arguments.of(1234D,    "<P>1.234E03<S>", customDf1),
                Arguments.of(0.1234,  "<P>1.234Em01<S>", customDf1),
                Arguments.of(-1234D,   "m<P>1.234E03<S>", customDf1),
                Arguments.of(-0.1234, "m<P>1.234Em01<S>", customDf1),
                Arguments.of(1234D,    "<P>1.234E03<S>", customDf2),
                Arguments.of(0.1234,  "<P>1.234Em01<S>", customDf2),
                Arguments.of(-1234D,   "1.234E03", customDf2),
                Arguments.of(-0.1234, "1.234Em01", customDf2),
                Arguments.of(1234D,    "1.234E03", customDf3),
                Arguments.of(0.1234,  "1.234Em01", customDf3),
                Arguments.of(-1234D,   "<P>1.234E03<S>", customDf3),
                Arguments.of(-0.1234, "<P>1.234Em01<S>", customDf3),
                Arguments.of(1234D,    "<P>1.234E03<S>", customDf4),
                Arguments.of(0.1234,  "<P>1.234Em01<S>", customDf4),
                Arguments.of(-1234D,   "<p>m1.234E03<s>", customDf4),
                Arguments.of(-0.1234, "<p>m1.234Em01<s>", customDf4),
                Arguments.of(Double.POSITIVE_INFINITY, "<P>\u221e<S>", customDf4),
                Arguments.of(Double.NEGATIVE_INFINITY, "<p>m\u221e<s>", customDf4),
                Arguments.of(Double.NaN, "\ufffd", customDf4), // without prefix and suffix
                Arguments.of(0.0,  "<P>0E00<S>", customDf4),
                Arguments.of(-0.0, "<p>m0E00<s>", customDf4) // with the minus sign
        );
    }

    // Check that negative exponent number recognized for longs
    @ParameterizedTest
    @MethodSource("longs")
    public void longTest(Number num, String str, DecimalFormat df) {
        test(num, str, df);
    }

    // Same as doubles() data provider, but with long values
    // Given in the form (long, String, DecimalFormat).
    private static Stream<Arguments> longs() {
        DecimalFormat defaultDf = new DecimalFormat();
        DecimalFormat customDf = getDecimalFormat(
                "<P>#,###<S>;<p>-#,###<s>", 'm');
        return Stream.of(
                // Test with default pattern
                Arguments.of(123456789L,  "123,456,789", defaultDf),
                Arguments.of(-123456789L, "-123,456,789", defaultDf),
                Arguments.of(0L, "0", defaultDf),
                Arguments.of(-0L, "0", defaultDf),
                // Test with a pattern and the minus sign
                Arguments.of(123456789L,  "<P>123,456,789<S>", customDf),
                Arguments.of(-123456789L, "<p>m123,456,789<s>", customDf),
                Arguments.of(0L, "<P>0<S>", customDf),
                Arguments.of(-0L, "<P>0<S>", customDf)
        );
    }

    // Check that negative exponent number recognized for bigDecimals
    @ParameterizedTest
    @MethodSource("bigDecimals")
    public void bigDecimalTest(Number num, String str, DecimalFormat df) {
        test(num, str, df);
    }

    // Same as doubles() data provider, but with BigDecimal values
    // Given in the form (BigDecimal, String, DecimalFormat).
    private static Stream<Arguments> bigDecimals() {
        DecimalFormat defaultDf = new DecimalFormat();
        DecimalFormat customDf = getDecimalFormat(
                "<P>#.####################E00<S>;<p>-#.####################E00<s>", 'm');
        return Stream.of(
                // Test with default pattern
                Arguments.of(new BigDecimal("123456789012345678901234567890"),
                        "123,456,789,012,345,678,901,234,567,890", defaultDf),
                Arguments.of(new BigDecimal("0.000000000123456789012345678901234567890"),
                        "0", defaultDf),
                Arguments.of(new BigDecimal("-123456789012345678901234567890"),
                        "-123,456,789,012,345,678,901,234,567,890", defaultDf),
                Arguments.of(new BigDecimal("-0.000000000123456789012345678901234567890"),
                        "-0", defaultDf),
                Arguments.of(new BigDecimal("0"), "0", defaultDf),
                Arguments.of(new BigDecimal("-0"), "0", defaultDf),
                // Test with a pattern and the minus sign
                Arguments.of(new BigDecimal("123456789012345678901234567890"),
                        "<P>1.23456789012345678901E29<S>", customDf),
                Arguments.of(new BigDecimal("0.000000000123456789012345678901234567890"),
                        "<P>1.23456789012345678901Em10<S>", customDf),
                Arguments.of(new BigDecimal("-123456789012345678901234567890"),
                        "<p>m1.23456789012345678901E29<s>", customDf),
                Arguments.of(new BigDecimal("-0.000000000123456789012345678901234567890"),
                        "<p>m1.23456789012345678901Em10<s>", customDf),
                Arguments.of(new BigDecimal("0"), "<P>0E00<S>", customDf),
                Arguments.of(new BigDecimal("-0"), "<P>0E00<S>", customDf)
        );
    }

    // Check that negative exponent number recognized for bigIntegers
    @ParameterizedTest
    @MethodSource("bigIntegers")
    public void bigIntegerTest(Number num, String str, DecimalFormat df) {
        test(num, str, df);
    }

    // Same as doubles() data provider, but with BigInteger values
    // Given in the form (BigInteger, String, DecimalFormat).
    private static Stream<Arguments> bigIntegers() {
        DecimalFormat defaultDf = new DecimalFormat();
        DecimalFormat customDf = getDecimalFormat(
                "<P>#,###<S>;<p>-#,###<s>", 'm');
        return Stream.of(
                // Test with default pattern
                Arguments.of(new BigInteger("123456789012345678901234567890"),
                        "123,456,789,012,345,678,901,234,567,890", defaultDf),
                Arguments.of(new BigInteger("-123456789012345678901234567890"),
                        "-123,456,789,012,345,678,901,234,567,890", defaultDf),
                Arguments.of(new BigInteger("0"), "0", defaultDf),
                Arguments.of(new BigInteger("-0"), "0", defaultDf),
                // Test with a pattern and the minus sign
                Arguments.of(new BigInteger("123456789012345678901234567890"),
                        "<P>123,456,789,012,345,678,901,234,567,890<S>", customDf),
                Arguments.of(new BigInteger("-123456789012345678901234567890"),
                        "<p>m123,456,789,012,345,678,901,234,567,890<s>", customDf),
                Arguments.of(new BigInteger("0"), "<P>0<S>", customDf),
                Arguments.of(new BigInteger("-0"), "<P>0<S>", customDf)
        );
    }

    // Check that the formatted value is correct and also check that
    // it can be round-tripped via parse() and format()
    private static void test(Number num, String str, DecimalFormat df) {
        String formatted = df.format(num);
        assertEquals(str, formatted, String.format("DecimalFormat format(%s) " +
                "Error: number: %s, minus sign: %s", num.getClass().getName(), num, df.getDecimalFormatSymbols().getMinusSign()));

        if (num instanceof BigDecimal || num instanceof BigInteger) {
            df.setParseBigDecimal(true);
        }
        testRoundTrip(formatted, str, num, df);
    }

    // Test that a parsed value can be round-tripped via format() and parse()
    private static void testRoundTrip(String formatted, String str,
                                      Number num, DecimalFormat df) {
        Number parsed1 = null, parsed2 = null;
        try {
            parsed1 = df.parse(formatted);
            formatted = df.format(parsed1);
            parsed2 = df.parse(formatted);
            assertEquals(parsed2, parsed1, """
                            DecimalFormat round trip parse(%s) error:
                                original number: %s
                                parsed number: %s
                                (%s)
                                formatted number: %s
                                re-parsed number: %s
                                (%s)
                                minus sign: %s
                            """.formatted(num.getClass().getName(), str, parsed1, parsed1.getClass().getName(),
                                    formatted, parsed2, parsed2.getClass().getName(), df.getDecimalFormatSymbols().getMinusSign()));
        }
        catch (Exception e) {
            fail("""
                    DecimalFormat parse(%s) threw an Exception: %s
                        original number: %s
                        parsed number: %s
                        (%s)
                        formatted number: %s
                        re-parsed number: %s
                        (%s)
                        minus sign: %s
                    """.formatted(num.getClass().getName(), e.getMessage(), str, parsed1, parsed1.getClass().getName(),
                            formatted, parsed2, parsed2.getClass().getName(), df.getDecimalFormatSymbols().getMinusSign()));
        }
    }

    // Set up custom DecimalFormat with DecimalFormatSymbols
    private static DecimalFormat getDecimalFormat(String pattern, char minusSign) {
        DecimalFormat df = new DecimalFormat();
        DecimalFormatSymbols dfs = df.getDecimalFormatSymbols();
        df.applyPattern(pattern);
        dfs.setMinusSign(minusSign);
        df.setDecimalFormatSymbols(dfs);
        return df;
    }
}
