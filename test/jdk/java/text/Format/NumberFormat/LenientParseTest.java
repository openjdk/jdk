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
 * @bug 8327640 8331485 8333456
 * @summary Test suite for NumberFormat parsing when lenient.
 * @run junit/othervm -Duser.language=en -Duser.country=US LenientParseTest
 * @run junit/othervm -Duser.language=ja -Duser.country=JP LenientParseTest
 * @run junit/othervm -Duser.language=zh -Duser.country=CN LenientParseTest
 * @run junit/othervm -Duser.language=tr -Duser.country=TR LenientParseTest
 * @run junit/othervm -Duser.language=de -Duser.country=DE LenientParseTest
 * @run junit/othervm -Duser.language=fr -Duser.country=FR LenientParseTest
 * @run junit/othervm -Duser.language=ar -Duser.country=AR LenientParseTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.text.CompactNumberFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Tests lenient parsing, this is done by testing the NumberFormat factory instances
// against a number of locales with different formatting conventions. The locales
// used all use a grouping size of 3. When lenient, parsing only fails
// if the prefix and/or suffix are not found, or the first character after the
// prefix is un-parseable. The tested locales all use groupingSize of 3.
public class LenientParseTest {

    // Used to retrieve the locale's expected symbols
    private static final DecimalFormatSymbols dfs =
            new DecimalFormatSymbols(Locale.getDefault());
    private static final DecimalFormat dFmt = (DecimalFormat)
            NumberFormat.getNumberInstance(Locale.getDefault());
    private static final DecimalFormat cFmt =
            (DecimalFormat) NumberFormat.getCurrencyInstance(Locale.getDefault());
    private static final DecimalFormat pFmt =
            (DecimalFormat) NumberFormat.getPercentInstance(Locale.getDefault());
    private static final CompactNumberFormat cmpctFmt =
            (CompactNumberFormat) NumberFormat.getCompactNumberInstance(Locale.getDefault(),
                    NumberFormat.Style.SHORT);

    // All NumberFormats should parse leniently (which is the default)
    static {
        // To effectively test compactNumberFormat, these should be set accordingly
        cmpctFmt.setParseIntegerOnly(false);
        cmpctFmt.setGroupingUsed(true);
    }

    // ---- NumberFormat tests ----
    // Test prefix/suffix behavior with a predefined DecimalFormat
    // Non-localized, only run once
    @ParameterizedTest
    @MethodSource("badParseStrings")
    @EnabledIfSystemProperty(named = "user.language", matches = "en")
    public void numFmtFailParseTest(String toParse, int expectedErrorIndex) {
        // Format with grouping size = 3, prefix = a, suffix = b
        DecimalFormat nonLocalizedDFmt = new DecimalFormat("a#,#00.00b");
        failParse(nonLocalizedDFmt, toParse, expectedErrorIndex);
    }

    // All input Strings should parse fully and return the expected value.
    // Expected index should be the length of the parse string, since it parses fully
    @ParameterizedTest
    @MethodSource("validFullParseStrings")
    public void numFmtSuccessFullParseTest(String toParse, double expectedValue) {
        assertEquals(expectedValue, successParse(dFmt, toParse, toParse.length()));
    }

    // All input Strings should parse partially and return expected value
    // with the expected final index
    @ParameterizedTest
    @MethodSource("validPartialParseStrings")
    public void numFmtSuccessPartialParseTest(String toParse, double expectedValue,
                                              int expectedIndex) {
        assertEquals(expectedValue, successParse(dFmt, toParse, expectedIndex));
    }

    // Parse partially due to no grouping
    @ParameterizedTest
    @MethodSource("noGroupingParseStrings")
    public void numFmtStrictGroupingNotUsed(String toParse, double expectedValue, int expectedIndex) {
        dFmt.setGroupingUsed(false);
        assertEquals(expectedValue, successParse(dFmt, toParse, expectedIndex));
        dFmt.setGroupingUsed(true);
    }

    // Parse partially due to integer only
    @ParameterizedTest
    @MethodSource("integerOnlyParseStrings")
    public void numFmtStrictIntegerOnlyUsed(String toParse, int expectedValue, int expectedIndex) {
        dFmt.setParseIntegerOnly(true);
        assertEquals(expectedValue, successParse(dFmt, toParse, expectedIndex));
        dFmt.setParseIntegerOnly(false);
    }

    @Test // Non-localized, only run once
    @EnabledIfSystemProperty(named = "user.language", matches = "en")
    public void badExponentParseNumberFormatTest() {
        // Some fmt, with an "E" exponent string
        DecimalFormat fmt = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
        // Upon non-numeric in exponent, parse will still successfully complete
        // but index should end on the last valid char in exponent
        assertEquals(1.23E45, successParse(fmt, "1.23E45.123", 7));
        assertEquals(1.23E45, successParse(fmt, "1.23E45.", 7));
        assertEquals(1.23E45, successParse(fmt, "1.23E45FOO3222", 7));
    }

    // ---- CurrencyFormat tests ----
    // All input Strings should pass and return expected value.
    @ParameterizedTest
    @MethodSource("currencyValidFullParseStrings")
    public void currFmtSuccessParseTest(String toParse, double expectedValue) {
        assertEquals(expectedValue, successParse(cFmt, toParse, toParse.length()));
    }

    // Strings may parse partially or fail. This is because the mapped
    // data may cause the error to occur before the suffix can be found, (if the locale
    // uses a suffix).
    @ParameterizedTest
    @MethodSource("currencyValidPartialParseStrings")
    public void currFmtParseTest(String toParse, double expectedValue,
                                 int expectedIndex) {
        if (cFmt.getPositiveSuffix().length() > 0) {
            // Since the error will occur before suffix is found, exception is thrown.
            failParse(cFmt, toParse, expectedIndex);
        } else {
            // Empty suffix, thus even if the error occurs, we have already found the
            // prefix, and simply parse partially
            assertEquals(expectedValue, successParse(cFmt, toParse, expectedIndex));
        }
    }

    // ---- PercentFormat tests ----
    // All input Strings should pass and return expected value.
    @ParameterizedTest
    @MethodSource("percentValidFullParseStrings")
    public void percentFmtSuccessParseTest(String toParse, double expectedValue) {
        assertEquals(expectedValue, successParse(pFmt, toParse, toParse.length()));
    }

    // Strings may parse partially or fail. This is because the mapped
    // data may cause the error to occur before the suffix can be found, (if the locale
    // uses a suffix).
    @ParameterizedTest
    @MethodSource("percentValidPartialParseStrings")
    public void percentFmtParseTest(String toParse, double expectedValue,
                                 int expectedIndex) {
        if (pFmt.getPositiveSuffix().length() > 0) {
            // Since the error will occur before suffix is found, exception is thrown.
            failParse(pFmt, toParse, expectedIndex);
        } else {
            // Empty suffix, thus even if the error occurs, we have already found the
            // prefix, and simply parse partially
            assertEquals(expectedValue, successParse(pFmt, toParse, expectedIndex));
        }
    }

    // ---- CompactNumberFormat tests ----
    // Can match to both the decimalFormat patterns and the compact patterns
    // Unlike the other tests, this test is only ran against the US Locale and
    // tests against data built with the thousands format (K).
    @ParameterizedTest
    @MethodSource("compactValidPartialParseStrings")
    @EnabledIfSystemProperty(named = "user.language", matches = "en")
    public void compactFmtFailParseTest(String toParse, double expectedValue, int expectedErrorIndex) {
        assertEquals(expectedValue, successParse(cmpctFmt, toParse, expectedErrorIndex));
    }


    @ParameterizedTest
    @MethodSource("compactValidFullParseStrings")
    @EnabledIfSystemProperty(named = "user.language", matches = "en")
    public void compactFmtSuccessParseTest(String toParse, double expectedValue) {
        assertEquals(expectedValue, successParse(cmpctFmt, toParse, toParse.length()));
    }

    // 8333456: Parse values with no compact suffix -> which allows parsing to iterate
    // position to the same value as string length which throws
    // StringIndexOutOfBoundsException upon charAt invocation
    @ParameterizedTest
    @MethodSource("compactValidNoSuffixParseStrings")
    @EnabledIfSystemProperty(named = "user.language", matches = "en")
    public void compactFmtSuccessParseIntOnlyTest(String toParse, double expectedValue) {
        cmpctFmt.setParseIntegerOnly(true);
        assertEquals(expectedValue, successParse(cmpctFmt, toParse, toParse.length()));
        cmpctFmt.setParseIntegerOnly(false);
    }

    // ---- Helper test methods ----

    // Method is used when a String should parse successfully. This does not indicate
    // that the entire String was used, however. The index and errorIndex values
    // should be as expected.
    private double successParse(NumberFormat fmt, String toParse, int expectedIndex) {
        Number parsedValue = assertDoesNotThrow(() -> fmt.parse(toParse));
        ParsePosition pp = new ParsePosition(0);
        assertDoesNotThrow(() -> fmt.parse(toParse, pp));
        assertEquals(-1, pp.getErrorIndex(),
                "ParsePosition ErrorIndex is not in correct location");
        assertEquals(expectedIndex, pp.getIndex(),
                "ParsePosition Index is not in correct location");
        return parsedValue.doubleValue();
    }

    // Method is used when a String should fail parsing. Indicated by either a thrown
    // ParseException, or null is returned depending on which parse method is invoked.
    // errorIndex should be as expected.
    private void failParse(NumberFormat fmt, String toParse, int expectedErrorIndex) {
        ParsePosition pp = new ParsePosition(0);
        assertThrows(ParseException.class, () -> fmt.parse(toParse));
        assertNull(fmt.parse(toParse, pp));
        assertEquals(expectedErrorIndex, pp.getErrorIndex());
    }

    // ---- Data Providers ----

    // Strings that should fail when parsed leniently.
    // Given as Arguments<String, expectedErrorIndex>
    // Non-localized data. For reference, the pattern of nonLocalizedDFmt is
    // "a#,#00.00b"
    private static Stream<Arguments> badParseStrings() {
        return Stream.of(
                // No prefix
                Arguments.of("1,1b", 0),
                // No suffix
                Arguments.of("a1,11", 5),
                // Digit does not follow the last grouping separator
                // Current behavior fails on the grouping separator
                Arguments.of("a1,11,z", 5),
                // No suffix after grouping
                Arguments.of("a1,11,", 5),
                // No prefix and suffix
                Arguments.of("1,11", 0),
                // First character after prefix is un-parseable
                // Behavior is to expect error index at 0, not 1
                Arguments.of("ac1,11", 0));
    }

    // These data providers use US locale grouping and decimal separators
    // for readability, however, the data is tested against multiple locales
    // and is converted appropriately at runtime.

    // Strings that should parse successfully, and consume the entire String
    // Form of Arguments(parseString, expectedParsedNumber)
    private static Stream<Arguments> validFullParseStrings() {
        return Stream.of(
                // Many subsequent grouping symbols
                Arguments.of("1,,,1", 11d),
                Arguments.of("11,,,11,,,11", 111111d),
                // Bad grouping size (with decimal)
                Arguments.of("1,1.", 11d),
                Arguments.of("11,111,11.", 1111111d),
                // Improper grouping size (with decimal and digits after)
                Arguments.of("1,1.1", 11.1d),
                Arguments.of("1,11.1", 111.1d),
                Arguments.of("1,1111.1", 11111.1d),
                Arguments.of("11,111,11.1", 1111111.1d),
                // Starts with grouping symbol
                Arguments.of(",111,,1,1", 11111d),
                Arguments.of(",1", 1d),
                Arguments.of(",,1", 1d),
                // Leading Zeros (not digits)
                Arguments.of("000,1,1", 11d),
                Arguments.of("000,111,11,,1", 111111d),
                Arguments.of("0,000,1,,1,1", 111d),
                Arguments.of("1,234.00", 1234d),
                Arguments.of("1,234.0", 1234d),
                Arguments.of("1,234.", 1234d),
                Arguments.of("1,234.00123", 1234.00123d),
                Arguments.of("1,234.012", 1234.012d),
                Arguments.of("1,234.224", 1234.224d),
                Arguments.of("1", 1d),
                Arguments.of("10", 10d),
                Arguments.of("100", 100d),
                Arguments.of("1000", 1000d),
                Arguments.of("1,000", 1000d),
                Arguments.of("10,000", 10000d),
                Arguments.of("10000", 10000d),
                Arguments.of("100,000", 100000d),
                Arguments.of("1,000,000", 1000000d),
                Arguments.of("10,000,000", 10000000d))
                .map(args -> Arguments.of(
                        localizeText(String.valueOf(args.get()[0])), args.get()[1]));
    }

    // Strings that should parse successfully, but do not use the entire String
    // Form of Arguments(parseString, expectedParsedNumber, expectedIndex)
    private static Stream<Arguments> validPartialParseStrings() {
        return Stream.of(
                // End with grouping symbol
                Arguments.of("11,", 11d, 2),
                Arguments.of("11,,", 11d, 3),
                Arguments.of("11,,,", 11d, 4),
                // Random chars that aren't the expected symbols
                Arguments.of("1,1P111", 11d, 3),
                Arguments.of("1.1P111", 1.1d, 3),
                Arguments.of("1P,1111", 1d, 1),
                Arguments.of("1P.1111", 1d, 1),
                Arguments.of("1,1111P", 11111d, 6),
                // Grouping occurs after decimal separator)
                Arguments.of("1.11,11", 1.11d, 4),
                Arguments.of("1.,11,11", 1d, 2))
                .map(args -> Arguments.of(
                        localizeText(String.valueOf(args.get()[0])), args.get()[1], args.get()[2]));
    }

    // Test data input for when parse integer only is true
    // Form of Arguments(parseString, expectedParsedNumber, expectedIndex)
    private static Stream<Arguments> integerOnlyParseStrings() {
        return Stream.of(
                Arguments.of("1234.1234", 1234, 4),
                Arguments.of("1234.12", 1234, 4),
                Arguments.of("1234.1a", 1234, 4),
                Arguments.of("1234.", 1234, 4))
                .map(args -> Arguments.of(
                        localizeText(String.valueOf(args.get()[0])), args.get()[1], args.get()[2]));
    }

    // Test data input for when no grouping is true
    // Form of Arguments(parseString, expectedParsedNumber, expectedIndex)
    private static Stream<Arguments> noGroupingParseStrings() {
        return Stream.of(
                Arguments.of("12,34", 12d, 2),
                Arguments.of("1234,", 1234d, 4),
                Arguments.of("123,456.789", 123d, 3))
                .map(args -> Arguments.of(
                        localizeText(String.valueOf(args.get()[0])), args.get()[1], args.get()[2]));
    }

    // Mappers for respective data providers to adjust values accordingly
    // Localized percent prefix/suffix is added, with appropriate expected values
    // adjusted. Expected parsed number should be divided by 100.
    private static Stream<Arguments> percentValidPartialParseStrings() {
        return validPartialParseStrings().map(args ->
                Arguments.of(pFmt.getPositivePrefix() + args.get()[0] + pFmt.getPositiveSuffix(),
                        (double) args.get()[1] / 100, (int) args.get()[2] + pFmt.getPositivePrefix().length())
        );
    }

    private static Stream<Arguments> percentValidFullParseStrings() {
        return validFullParseStrings().map(args -> Arguments.of(
                pFmt.getPositivePrefix() + args.get()[0] + pFmt.getPositiveSuffix(),
                (double) args.get()[1] / 100)
        );
    }

    // Mappers for respective data providers to adjust values accordingly
    // Localized percent prefix/suffix is added, with appropriate expected values
    // adjusted. Separators replaced for monetary versions.
    private static Stream<Arguments> currencyValidPartialParseStrings() {
        return validPartialParseStrings().map(args -> Arguments.of(
                cFmt.getPositivePrefix() + String.valueOf(args.get()[0])
                        .replace(dfs.getGroupingSeparator(), dfs.getMonetaryGroupingSeparator())
                        .replace(dfs.getDecimalSeparator(), dfs.getMonetaryDecimalSeparator())
                        + cFmt.getPositiveSuffix(),
                args.get()[1], (int) args.get()[2] + cFmt.getPositivePrefix().length())
        );
    }

    private static Stream<Arguments> currencyValidFullParseStrings() {
        return validFullParseStrings().map(args -> Arguments.of(
                cFmt.getPositivePrefix() + String.valueOf(args.get()[0])
                        .replace(dfs.getGroupingSeparator(), dfs.getMonetaryGroupingSeparator())
                        .replace(dfs.getDecimalSeparator(), dfs.getMonetaryDecimalSeparator())
                        + cFmt.getPositiveSuffix(),
                args.get()[1])
        );
    }

    // Compact Pattern Data Provider provides test input for both DecimalFormat patterns
    // and the compact patterns. As there is no method to retrieve compact patterns,
    // thus test only against US English locale, and use a hard coded K - 1000
    private static Stream<Arguments> compactValidPartialParseStrings() {
        return Stream.concat(validPartialParseStrings().map(args -> Arguments.of(args.get()[0],
                args.get()[1], args.get()[2])), validPartialParseStrings().map(args -> Arguments.of(args.get()[0] + "K",
                args.get()[1], args.get()[2]))
        );
    }

    private static Stream<Arguments> compactValidFullParseStrings() {
        return Stream.concat(validFullParseStrings().map(args -> Arguments.of(args.get()[0],
                args.get()[1])), validFullParseStrings().map(args -> Arguments.of(args.get()[0] + "K",
                (double)args.get()[1] * 1000.0))
        );
    }

    // No compact suffixes
    private static Stream<Arguments> compactValidNoSuffixParseStrings() {
        return Stream.of(
                Arguments.of("5", 5),
                Arguments.of("50", 50),
                Arguments.of("50.", 50),
                Arguments.of("5,000", 5000),
                Arguments.of("5,000.", 5000),
                Arguments.of("5,000.00", 5000)
        );
    }

    // Replace the grouping and decimal separators with localized variants
    // Used during localization of data
    private static String localizeText(String text) {
        // As this is a single pass conversion, this is safe for multiple replacement,
        // even if a ',' could be a decimal separator for a locale.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ',') {
                sb.append(dfs.getGroupingSeparator());
            } else if (c == '.') {
                sb.append(dfs.getDecimalSeparator());
            } else if (c == '0') {
                sb.append(dfs.getZeroDigit());
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
