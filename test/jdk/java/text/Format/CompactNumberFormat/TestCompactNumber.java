/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8177552 8217721 8222756 8295372 8306116 8319990 8338690 8363972
 * @summary Checks the functioning of compact number format
 * @modules jdk.localedata
 * @run junit/othervm TestCompactNumber
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.FieldPosition;
import java.text.Format;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestCompactNumber {

    private static final NumberFormat FORMAT_DZ_LONG = NumberFormat
            .getCompactNumberInstance(Locale.of("dz"), NumberFormat.Style.LONG);

    private static final NumberFormat FORMAT_EN_US_SHORT = NumberFormat
            .getCompactNumberInstance(Locale.US, NumberFormat.Style.SHORT);

    private static final NumberFormat FORMAT_EN_LONG = NumberFormat
            .getCompactNumberInstance(Locale.ENGLISH, NumberFormat.Style.LONG);

    private static final NumberFormat FORMAT_HI_IN_LONG = NumberFormat
            .getCompactNumberInstance(Locale.of("hi", "IN"), NumberFormat.Style.LONG);

    private static final NumberFormat FORMAT_JA_JP_SHORT = NumberFormat
            .getCompactNumberInstance(Locale.JAPAN, NumberFormat.Style.SHORT);

    private static final NumberFormat FORMAT_IT_SHORT = NumberFormat
            .getCompactNumberInstance(Locale.ITALIAN, NumberFormat.Style.SHORT);

    private static final NumberFormat FORMAT_IT_LONG = NumberFormat
            .getCompactNumberInstance(Locale.ITALIAN, NumberFormat.Style.LONG);

    private static final NumberFormat FORMAT_CA_LONG = NumberFormat
            .getCompactNumberInstance(Locale.of("ca"), NumberFormat.Style.LONG);

    private static final NumberFormat FORMAT_AS_LONG = NumberFormat
            .getCompactNumberInstance(Locale.of("as"), NumberFormat.Style.LONG);

    private static final NumberFormat FORMAT_BRX_SHORT = NumberFormat
            .getCompactNumberInstance(Locale.of("brx"), NumberFormat.Style.SHORT);

    private static final NumberFormat FORMAT_SW_LONG = NumberFormat
            .getCompactNumberInstance(Locale.of("sw"), NumberFormat.Style.LONG);

    private static final NumberFormat FORMAT_SE_SHORT = NumberFormat
            .getCompactNumberInstance(Locale.of("se"), NumberFormat.Style.SHORT);

    private static final NumberFormat FORMAT_DE_LONG = NumberFormat
            .getCompactNumberInstance(Locale.GERMAN, NumberFormat.Style.LONG);

    private static final NumberFormat FORMAT_SL_LONG = NumberFormat
            .getCompactNumberInstance(Locale.of("sl"), NumberFormat.Style.LONG);

    private static final NumberFormat FORMAT_ES_LONG_FD1 = NumberFormat
            .getCompactNumberInstance(Locale.of("es"), NumberFormat.Style.LONG);
    private static final NumberFormat FORMAT_DE_LONG_FD2 = NumberFormat
            .getCompactNumberInstance(Locale.GERMAN, NumberFormat.Style.LONG);
    private static final NumberFormat FORMAT_IT_LONG_FD3 = NumberFormat
            .getCompactNumberInstance(Locale.ITALIAN, NumberFormat.Style.LONG);
    private static final NumberFormat FORMAT_PT_LONG_FD4 = NumberFormat
            .getCompactNumberInstance(Locale.of("pt"), NumberFormat.Style.LONG);

    private static final NumberFormat FORMAT_PL_LONG = NumberFormat
            .getCompactNumberInstance(Locale.of("pl"), NumberFormat.Style.LONG);

    private static final NumberFormat FORMAT_FR_LONG = NumberFormat
            .getCompactNumberInstance(Locale.FRENCH, NumberFormat.Style.LONG);

    static {
        FORMAT_ES_LONG_FD1.setMaximumFractionDigits(1);
        FORMAT_DE_LONG_FD2.setMaximumFractionDigits(2);
        FORMAT_IT_LONG_FD3.setMaximumFractionDigits(3);
        FORMAT_PT_LONG_FD4.setMaximumFractionDigits(4);
    }

    Object[][] compactFormatData() {
        return new Object[][]{
            // compact number format instance, number to format, formatted output
            {FORMAT_DZ_LONG, 1000.09, "སྟོང་ཕ"
                + "ྲག ༡"},
            {FORMAT_DZ_LONG, -999.99, "-སྟོང་ཕ"
                + "ྲག ༡"},
            {FORMAT_DZ_LONG, -0.0, "-༠"},
            {FORMAT_DZ_LONG, 3000L, "སྟོང་ཕ"
                + "ྲག ༣"},
            {FORMAT_DZ_LONG, new BigInteger("12345678901234567890"), "ད"
                + "ུང་ཕྱུར་ས"
                + "་ཡ་ ༡༢༣༤༥༧"},
            // negative
            {FORMAT_DZ_LONG, new BigInteger("-12345678901234567890"), "-ད"
                + "ུང་ཕྱུར་ས"
                + "་ཡ་ ༡༢༣༤༥༧"},
            {FORMAT_DZ_LONG, new BigDecimal("12345678901234567890.89"), "ད"
                + "ུང་ཕྱུར་ས"
                + "་ཡ་ ༡༢༣༤༥༧"},
            {FORMAT_DZ_LONG, new BigDecimal("-12345678901234567890.89"), "-ད"
                + "ུང་ཕྱུར་ས"
                + "་ཡ་ ༡༢༣༤༥༧"},
            // Zeros
            {FORMAT_EN_US_SHORT, 0, "0"},
            {FORMAT_EN_US_SHORT, 0.0, "0"},
            {FORMAT_EN_US_SHORT, -0.0, "-0"},
            // Less than 1000 no suffix
            {FORMAT_EN_US_SHORT, 499, "499"},
            // Boundary number
            {FORMAT_EN_US_SHORT, 1000.0, "1K"},
            // Long
            {FORMAT_EN_US_SHORT, 3000L, "3K"},
            {FORMAT_EN_US_SHORT, 30000L, "30K"},
            {FORMAT_EN_US_SHORT, 300000L, "300K"},
            {FORMAT_EN_US_SHORT, 3000000L, "3M"},
            {FORMAT_EN_US_SHORT, 30000000L, "30M"},
            {FORMAT_EN_US_SHORT, 300000000L, "300M"},
            {FORMAT_EN_US_SHORT, 3000000000L, "3B"},
            {FORMAT_EN_US_SHORT, 30000000000L, "30B"},
            {FORMAT_EN_US_SHORT, 300000000000L, "300B"},
            {FORMAT_EN_US_SHORT, 3000000000000L, "3T"},
            {FORMAT_EN_US_SHORT, 30000000000000L, "30T"},
            {FORMAT_EN_US_SHORT, 300000000000000L, "300T"},
            {FORMAT_EN_US_SHORT, 3000000000000000L, "3000T"},
            // Negatives
            {FORMAT_EN_US_SHORT, -3000L, "-3K"},
            {FORMAT_EN_US_SHORT, -30000L, "-30K"},
            {FORMAT_EN_US_SHORT, -300000L, "-300K"},
            {FORMAT_EN_US_SHORT, -3000000L, "-3M"},
            {FORMAT_EN_US_SHORT, -30000000L, "-30M"},
            {FORMAT_EN_US_SHORT, -300000000L, "-300M"},
            {FORMAT_EN_US_SHORT, -3000000000L, "-3B"},
            {FORMAT_EN_US_SHORT, -30000000000L, "-30B"},
            {FORMAT_EN_US_SHORT, -300000000000L, "-300B"},
            {FORMAT_EN_US_SHORT, -3000000000000L, "-3T"},
            {FORMAT_EN_US_SHORT, -30000000000000L, "-30T"},
            {FORMAT_EN_US_SHORT, -300000000000000L, "-300T"},
            {FORMAT_EN_US_SHORT, -3000000000000000L, "-3000T"},
            // Double
            {FORMAT_EN_US_SHORT, 3000.0, "3K"},
            {FORMAT_EN_US_SHORT, 30000.0, "30K"},
            {FORMAT_EN_US_SHORT, 300000.0, "300K"},
            {FORMAT_EN_US_SHORT, 3000000.0, "3M"},
            {FORMAT_EN_US_SHORT, 30000000.0, "30M"},
            {FORMAT_EN_US_SHORT, 300000000.0, "300M"},
            {FORMAT_EN_US_SHORT, 3000000000.0, "3B"},
            {FORMAT_EN_US_SHORT, 30000000000.0, "30B"},
            {FORMAT_EN_US_SHORT, 300000000000.0, "300B"},
            {FORMAT_EN_US_SHORT, 3000000000000.0, "3T"},
            {FORMAT_EN_US_SHORT, 30000000000000.0, "30T"},
            {FORMAT_EN_US_SHORT, 300000000000000.0, "300T"},
            {FORMAT_EN_US_SHORT, 3000000000000000.0, "3000T"},
            // Negatives
            {FORMAT_EN_US_SHORT, -3000.0, "-3K"},
            {FORMAT_EN_US_SHORT, -30000.0, "-30K"},
            {FORMAT_EN_US_SHORT, -300000.0, "-300K"},
            {FORMAT_EN_US_SHORT, -3000000.0, "-3M"},
            {FORMAT_EN_US_SHORT, -30000000.0, "-30M"},
            {FORMAT_EN_US_SHORT, -300000000.0, "-300M"},
            {FORMAT_EN_US_SHORT, -3000000000.0, "-3B"},
            {FORMAT_EN_US_SHORT, -30000000000.0, "-30B"},
            {FORMAT_EN_US_SHORT, -300000000000.0, "-300B"},
            {FORMAT_EN_US_SHORT, -3000000000000.0, "-3T"},
            {FORMAT_EN_US_SHORT, -30000000000000.0, "-30T"},
            {FORMAT_EN_US_SHORT, -300000000000000.0, "-300T"},
            {FORMAT_EN_US_SHORT, -3000000000000000.0, "-3000T"},
            // BigInteger
            {FORMAT_EN_US_SHORT, new BigInteger("12345678901234567890"),
                "12345679T"},
            {FORMAT_EN_US_SHORT, new BigInteger("-12345678901234567890"),
                "-12345679T"},
            //BigDecimal
            {FORMAT_EN_US_SHORT, new BigDecimal("12345678901234567890.89"),
                "12345679T"},
            {FORMAT_EN_US_SHORT, new BigDecimal("-12345678901234567890.89"),
                "-12345679T"},
            {FORMAT_EN_US_SHORT, new BigDecimal("12345678901234567890123466767.89"),
                "12345678901234568T"},
            {FORMAT_EN_US_SHORT, new BigDecimal(
                "12345678901234567890878732267863209.89"),
                "12345678901234567890879T"},
            // number as exponent
            {FORMAT_EN_US_SHORT, 9.78313E+3, "10K"},
            // Less than 1000 no suffix
            {FORMAT_EN_LONG, 999, "999"},
            // Round the value and then format
            {FORMAT_EN_LONG, 999.99, "1 thousand"},
            // 10 thousand
            {FORMAT_EN_LONG, 99000, "99 thousand"},
            // Long path
            {FORMAT_EN_LONG, 330000, "330 thousand"},
            // Double path
            {FORMAT_EN_LONG, 3000.90, "3 thousand"},
            // BigInteger path
            {FORMAT_EN_LONG, new BigInteger("12345678901234567890"),
                "12345679 trillion"},
            //BigDecimal path
            {FORMAT_EN_LONG, new BigDecimal("12345678901234567890.89"),
                "12345679 trillion"},
            // Less than 1000 no suffix
            {FORMAT_HI_IN_LONG, -999, "-999"},
            // Round the value with 0 fraction digits and format it
            {FORMAT_HI_IN_LONG, -999.99, "-1 हज़ार"},
            // 10 thousand
            {FORMAT_HI_IN_LONG, 99000, "99 हज़ार"},
            // Long path
            {FORMAT_HI_IN_LONG, 330000, "3 लाख"},
            // Double path
            {FORMAT_HI_IN_LONG, 3000.90, "3 हज़ार"},
            // BigInteger path
            {FORMAT_HI_IN_LONG, new BigInteger("12345678901234567890"),
                "123456789 खरब"},
            // BigDecimal path
            {FORMAT_HI_IN_LONG, new BigDecimal("12345678901234567890.89"),
                "123456789 खरब"},
            // 1000 does not have any suffix in "ja" locale
            {FORMAT_JA_JP_SHORT, -999.99, "-1,000"},
            // 0-9999 does not have any suffix
            {FORMAT_JA_JP_SHORT, 9999, "9,999"},
            // 99000/10000 => 9.9万 rounded to 10万
            {FORMAT_JA_JP_SHORT, 99000, "10万"},
            // Negative
            {FORMAT_JA_JP_SHORT, -99000, "-10万"},
            // Long path
            {FORMAT_JA_JP_SHORT, 330000, "33万"},
            // Double path
            {FORMAT_JA_JP_SHORT, 3000.90, "3,001"},
            // BigInteger path
            {FORMAT_JA_JP_SHORT, new BigInteger("12345678901234567890"),
                "1235京"},
            // BigDecimal path
            {FORMAT_JA_JP_SHORT, new BigDecimal("12345678901234567890.89"),
                "1235京"},
            // less than 1000 no suffix
            {FORMAT_IT_SHORT, 499, "499"},
            // Boundary number
            {FORMAT_IT_SHORT, 1000, "1K"},
            // Long path
            {FORMAT_IT_SHORT, 3000000L, "3 Mln"},
            // Double path
            {FORMAT_IT_SHORT, 3000000.0, "3 Mln"},
            // BigInteger path
            {FORMAT_IT_SHORT, new BigInteger("12345678901234567890"),
                "12345679 Bln"},
            // BigDecimal path
            {FORMAT_IT_SHORT, new BigDecimal("12345678901234567890.89"),
                "12345679 Bln"},
            {FORMAT_CA_LONG, 999, "999"},
            {FORMAT_CA_LONG, 999.99, "1 miler"},
            {FORMAT_CA_LONG, 99000, "99 milers"},
            {FORMAT_CA_LONG, 330000, "330 milers"},
            {FORMAT_CA_LONG, 3000.90, "3 milers"},
            {FORMAT_CA_LONG, 1000000, "1 milió"},
            {FORMAT_CA_LONG, new BigInteger("12345678901234567890"),
                "12345679 bilions"},
            {FORMAT_CA_LONG, new BigDecimal("12345678901234567890.89"),
                "12345679 bilions"},
            {FORMAT_AS_LONG, 5000.0, "৫ হাজাৰ"},
            {FORMAT_AS_LONG, 50000.0, "৫০ হাজাৰ"},
            {FORMAT_AS_LONG, 500000.0, "৫ লাখ"},
            {FORMAT_AS_LONG, 5000000.0, "৫ নিযুত"},
            {FORMAT_AS_LONG, 50000000.0, "৫০ নিযুত"},
            {FORMAT_AS_LONG, 500000000.0, "৫০০ নিযুত"},
            {FORMAT_AS_LONG, 5000000000.0, "৫ শত কোটি"},
            {FORMAT_AS_LONG, 50000000000.0, "৫০ শত কোটি"},
            {FORMAT_AS_LONG, 500000000000.0, "৫০০ শত কোটি"},
            {FORMAT_AS_LONG, 5000000000000.0, "৫ শত পৰাৰ্দ্ধ"},
            {FORMAT_AS_LONG, 50000000000000.0, "৫০ শত পৰাৰ্দ্ধ"},
            {FORMAT_AS_LONG, 500000000000000.0, "৫০০ শত পৰাৰ্দ্ধ"},
            {FORMAT_AS_LONG, 5000000000000000.0, "৫০০০ শত পৰাৰ্দ্ধ"},
            {FORMAT_AS_LONG, new BigInteger("12345678901234567890"),
                "১২৩৪৫৬৭৯ শত পৰাৰ্দ্ধ"},
            {FORMAT_AS_LONG, new BigDecimal("12345678901234567890123466767.89"),
                "১২৩৪৫৬৭৮৯০১২৩৪৫৬৮ শত পৰাৰ্দ্ধ"},
            {FORMAT_BRX_SHORT, 999, "999"},
            {FORMAT_BRX_SHORT, 999.99, "1के"},
            {FORMAT_BRX_SHORT, 99000, "99के"},
            {FORMAT_BRX_SHORT, 330000, "330के"},
            {FORMAT_BRX_SHORT, 3000.90, "3के"},
            {FORMAT_BRX_SHORT, 1000000, "1एम"},
            {FORMAT_BRX_SHORT, new BigInteger("12345678901234567890"),
                    "12345679ति"},
            {FORMAT_BRX_SHORT, new BigDecimal("12345678901234567890.89"),
                    "12345679ति"},
            // Less than 1000 no suffix
            {FORMAT_SW_LONG, 499, "499"},
            // Boundary number
            {FORMAT_SW_LONG, 1000, "elfu 1"},
            // Long path
            {FORMAT_SW_LONG, 3000000L, "milioni 3"},
            // Long path, negative
            {FORMAT_SW_LONG, -3000000L, "milioni -3"},
            // Double path
            {FORMAT_SW_LONG, 3000000.0, "milioni 3"},
            // Double path, negative
            {FORMAT_SW_LONG, -3000000.0, "milioni -3"},
            // BigInteger path
            {FORMAT_SW_LONG, new BigInteger("12345678901234567890"),
                "trilioni 12345679"},
            // BigDecimal path
            {FORMAT_SW_LONG, new BigDecimal("12345678901234567890.89"),
                "trilioni 12345679"},
            // Positives
            // No compact form
            {FORMAT_SE_SHORT, 999, "999"},
            // Long
            {FORMAT_SE_SHORT, 8000000L, "8 mn"},
            // Double
            {FORMAT_SE_SHORT, 8000.98, "8 dt"},
            // Big integer
            {FORMAT_SE_SHORT, new BigInteger("12345678901234567890"), "12345679 bn"},
            // Big decimal
            {FORMAT_SE_SHORT, new BigDecimal("12345678901234567890.98"), "12345679 bn"},
            // Negatives
            // No compact form
            {FORMAT_SE_SHORT, -999, "−999"},
            // Long
            {FORMAT_SE_SHORT, -8000000L, "−8 mn"},
            // Double
            {FORMAT_SE_SHORT, -8000.98, "−8 dt"},
            // BigInteger
            {FORMAT_SE_SHORT, new BigInteger("-12345678901234567890"), "−12345679 bn"},
            // BigDecimal
            {FORMAT_SE_SHORT, new BigDecimal("-12345678901234567890.98"), "−12345679 bn"},

            // Plurals
            // DE: one:i = 1 and v = 0
            {FORMAT_DE_LONG, 1_000_000, "1 Million"},
            {FORMAT_DE_LONG, 2_000_000, "2 Millionen"},
            // SL: one:v = 0 and i % 100 = 1
            //     two:v = 0 and i % 100 = 2
            //     few:v = 0 and i % 100 = 3..4 or v != 0
            {FORMAT_SL_LONG, 1_000_000, "1 milijon"},
            {FORMAT_SL_LONG, 2_000_000, "2 milijona"},
            {FORMAT_SL_LONG, 3_000_000, "3 milijoni"},
            {FORMAT_SL_LONG, 5_000_000, "5 milijonov"},
            // Fractional plurals
            {FORMAT_ES_LONG_FD1, 1_234_500, "1,2 millones"},
            {FORMAT_DE_LONG_FD2, 1_234_500, "1,23 Millionen"},
            {FORMAT_IT_LONG_FD3, 1_234_500, "1,234 milioni"},
            {FORMAT_PT_LONG_FD4, 1_234_500, "1,2345 milhões"},

            // 8338690
            {FORMAT_PL_LONG, 5_000, "5 tysięcy"},
            {FORMAT_PL_LONG, 4_949, "5 tysięcy"},
            {FORMAT_FR_LONG, 1_949, "2 mille"},
            {FORMAT_IT_LONG, 1_949, "2 mila"},
        };
    }

    Object[][] compactParseData() {
        return new Object[][]{
                // compact number format instance, string to parse, parsed number, return type
                {FORMAT_DZ_LONG, "སྟོང་ཕྲ"
                        + "ག ༡", 1000L, Long.class},
                {FORMAT_DZ_LONG, "-སྟོང་ཕྲ"
                        + "ག ༣", -3000L, Long.class},
                {FORMAT_DZ_LONG, "དུང་ཕྱུར"
                        + "་ས་ཡ་ ༡"
                        + "༢༣༤༥༧", 1.23457E19, Double.class},
                {FORMAT_DZ_LONG, "-དུང་ཕྱུར"
                        + "་ས་ཡ་ ༡"
                        + "༢༣༤༥༧", -1.23457E19, Double.class},
                {FORMAT_EN_US_SHORT, "-0.0", -0.0, Double.class},
                {FORMAT_EN_US_SHORT, "-0", -0.0, Double.class},
                {FORMAT_EN_US_SHORT, "0", 0L, Long.class},
                {FORMAT_EN_US_SHORT, "499", 499L, Long.class},
                {FORMAT_EN_US_SHORT, "-499", -499L, Long.class},
                {FORMAT_EN_US_SHORT, "499.89", 499.89, Double.class},
                {FORMAT_EN_US_SHORT, "-499.89", -499.89, Double.class},
                {FORMAT_EN_US_SHORT, "1K", 1000L, Long.class},
                {FORMAT_EN_US_SHORT, "-1K", -1000L, Long.class},
                {FORMAT_EN_US_SHORT, "3K", 3000L, Long.class},
                {FORMAT_EN_US_SHORT, "17K", 17000L, Long.class},
                {FORMAT_EN_US_SHORT, "-17K", -17000L, Long.class},
                {FORMAT_EN_US_SHORT, "-3K", -3000L, Long.class},
                {FORMAT_EN_US_SHORT, "12345678901234567890", 1.2345678901234567E19, Double.class},
                {FORMAT_EN_US_SHORT, "12345679T", 1.2345679E19, Double.class},
                {FORMAT_EN_US_SHORT, "-12345679T", -1.2345679E19, Double.class},
                {FORMAT_EN_US_SHORT, "599.01K", 599010L, Long.class},
                {FORMAT_EN_US_SHORT, "-599.01K", -599010L, Long.class},
                {FORMAT_EN_US_SHORT, "599444444.90T", 5.994444449E20, Double.class},
                {FORMAT_EN_US_SHORT, "-599444444.90T", -5.994444449E20, Double.class},
                {FORMAT_EN_US_SHORT, "123456789012345.5678K", 123456789012345568L, Long.class},
                {FORMAT_EN_US_SHORT, "17.000K", 17000L, Long.class},
                {FORMAT_EN_US_SHORT, "123.56678K", 123566.78000, Double.class},
                {FORMAT_EN_US_SHORT, "-123.56678K", -123566.78000, Double.class},
                {FORMAT_EN_LONG, "999", 999L, Long.class},
                {FORMAT_EN_LONG, "1 thousand", 1000L, Long.class},
                {FORMAT_EN_LONG, "3 thousand", 3000L, Long.class},
                {FORMAT_EN_LONG, "12345679 trillion", 1.2345679E19, Double.class},
                {FORMAT_HI_IN_LONG, "999", 999L, Long.class},
                {FORMAT_HI_IN_LONG, "-999", -999L, Long.class},
                {FORMAT_HI_IN_LONG, "1 हज़ार", 1000L, Long.class},
                {FORMAT_HI_IN_LONG, "-1 हज़ार", -1000L, Long.class},
                {FORMAT_HI_IN_LONG, "3 हज़ार", 3000L, Long.class},
                {FORMAT_HI_IN_LONG, "12345679 खरब", 1234567900000000000L, Long.class},
                {FORMAT_HI_IN_LONG, "-12345679 खरब", -1234567900000000000L, Long.class},
                {FORMAT_JA_JP_SHORT, "-99", -99L, Long.class},
                {FORMAT_JA_JP_SHORT, "1万", 10000L, Long.class},
                {FORMAT_JA_JP_SHORT, "30万", 300000L, Long.class},
                {FORMAT_JA_JP_SHORT, "-30万", -300000L, Long.class},
                {FORMAT_JA_JP_SHORT, "12345679兆", 1.2345679E19, Double.class},
                {FORMAT_JA_JP_SHORT, "-12345679兆", -1.2345679E19, Double.class},
                {FORMAT_IT_SHORT, "-99", -99L, Long.class},
                {FORMAT_IT_SHORT, "1 Mln", 1000000L, Long.class},
                {FORMAT_IT_SHORT, "30 Mln", 30000000L, Long.class},
                {FORMAT_IT_SHORT, "-30 Mln", -30000000L, Long.class},
                {FORMAT_IT_SHORT, "12345679 Bln", 1.2345679E19, Double.class},
                {FORMAT_IT_SHORT, "-12345679 Bln", -1.2345679E19, Double.class},
                {FORMAT_SW_LONG, "-0.0", -0.0, Double.class},
                {FORMAT_SW_LONG, "499", 499L, Long.class},
                {FORMAT_SW_LONG, "elfu 1", 1000L, Long.class},
                {FORMAT_SW_LONG, "elfu 3", 3000L, Long.class},
                {FORMAT_SW_LONG, "elfu 17", 17000L, Long.class},
                {FORMAT_SW_LONG, "elfu -3", -3000L, Long.class},
                {FORMAT_SW_LONG, "499", 499L, Long.class},
                {FORMAT_SW_LONG, "-499", -499L, Long.class},
                {FORMAT_SW_LONG, "elfu 1", 1000L, Long.class},
                {FORMAT_SW_LONG, "elfu 3", 3000L, Long.class},
                {FORMAT_SW_LONG, "elfu -3", -3000L, Long.class},
                {FORMAT_SW_LONG, "elfu 17", 17000L, Long.class},
                {FORMAT_SW_LONG, "trilioni 12345679", 1.2345679E19, Double.class},
                {FORMAT_SW_LONG, "trilioni -12345679", -1.2345679E19, Double.class},
                {FORMAT_SW_LONG, "elfu 599.01", 599010L, Long.class},
                {FORMAT_SW_LONG, "elfu -599.01", -599010L, Long.class},
                {FORMAT_SE_SHORT, "999", 999L, Long.class},
                {FORMAT_SE_SHORT, "8 mn", 8000000L, Long.class},
                {FORMAT_SE_SHORT, "8 dt", 8000L, Long.class},
                {FORMAT_SE_SHORT, "12345679 bn", 1.2345679E19, Double.class},
                {FORMAT_SE_SHORT, "12345679,89 bn", 1.2345679890000001E19, Double.class},
                {FORMAT_SE_SHORT, "\u2212999", -999L, Long.class},
                {FORMAT_SE_SHORT, "\u22128\u00a0mn", -8000000L, Long.class},
                // lenient parsing. Hyphen-minus should match the localized minus sign
                {FORMAT_SE_SHORT, "−8 mn", -8000000L, Long.class},
                {FORMAT_SE_SHORT, "\u22128 dt", -8000L, Long.class},
                {FORMAT_SE_SHORT, "\u221212345679 bn", -1.2345679E19, Double.class},
                {FORMAT_SE_SHORT, "\u221212345679,89 bn", -1.2345679890000001E19, Double.class},

                // Plurals
                // DE: one:i = 1 and v = 0
                {FORMAT_DE_LONG, "1 Million",   1_000_000L, Long.class},
                {FORMAT_DE_LONG, "2 Millionen", 2_000_000L, Long.class},
                // SL: one:v = 0 and i % 100 = 1
                //     two:v = 0 and i % 100 = 2
                //     few:v = 0 and i % 100 = 3..4 or v != 0
                {FORMAT_SL_LONG, "1 milijon",   1_000_000L, Long.class},
                {FORMAT_SL_LONG, "2 milijona",  2_000_000L, Long.class},
                {FORMAT_SL_LONG, "3 milijoni",  3_000_000L, Long.class},
                {FORMAT_SL_LONG, "5 milijonov", 5_000_000L, Long.class},
                // Fractional plurals
                {FORMAT_ES_LONG_FD1, "1,2 millones", 1_200_000L, Long.class},
                {FORMAT_DE_LONG_FD2, "1,23 Millionen", 1_230_000L, Long.class},
                {FORMAT_IT_LONG_FD3, "1,234 milioni", 1_234_000L, Long.class},
                {FORMAT_PT_LONG_FD4, "1,2345 milhões", 1_234_500L, Long.class},
                // 8338690
                {FORMAT_PL_LONG, "5 tysięcy", 5_000L, Long.class},
                {FORMAT_FR_LONG, "2 mille", 2_000L, Long.class},
                {FORMAT_IT_LONG, "2 mila", 2_000L, Long.class},
        };
    }

    Object[][] exceptionParseData() {
        return new Object[][]{
            // compact number instance, string to parse, null (no o/p; must throw exception)
            // no number
            {FORMAT_DZ_LONG, "སྟོང་ཕྲ"
                + "ག", null},
            // Invalid prefix
            {FORMAT_DZ_LONG, "-སྟོང,་ཕྲ"
                + "ག ༣", null},
            // Invalid prefix for en_US
            {FORMAT_EN_US_SHORT, "K12,347", null},
            // Invalid prefix for ja_JP
            {FORMAT_JA_JP_SHORT, "万1", null},
        };
    }

    Object[][] invalidParseData() {
        return new Object[][]{
            // compact number instance, string to parse, parsed number
            // Prefix and suffix do not match
            {FORMAT_DZ_LONG, "སྟོང་ཕྲ"
                + "ག ༡ KM", 1000L},
            // Exponents are unparseable
            {FORMAT_EN_US_SHORT, "-1.05E4K", -1.05},
            // Default instance does not allow grouping
            {FORMAT_EN_US_SHORT, "12,347", 12L},
            // Take partial suffix "K" as 1000 for en_US_SHORT patterns
            {FORMAT_EN_US_SHORT, "12KM", 12000L},
            // Invalid suffix
            {FORMAT_HI_IN_LONG, "-1  क.", -1L},

            // invalid plurals
            {FORMAT_DE_LONG, "2 Million", 2L},
            {FORMAT_SL_LONG, "2 milijon", 2L},
            {FORMAT_SL_LONG, "2 milijone", 2L},
            {FORMAT_SL_LONG, "2 milijonv", 2L},
            {FORMAT_SL_LONG, "5 milijona", 5L},
            {FORMAT_SL_LONG, "3 milijon", 3L},
            {FORMAT_SL_LONG, "3 milijonv", 3L},
            {FORMAT_SL_LONG, "5 milijon", 5L},
            {FORMAT_SL_LONG, "5 milijona", 5L},
            {FORMAT_SL_LONG, "5 milijone", 5L},
            // 8338690
            {FORMAT_PL_LONG, "5 tysiące", 5L},
            {FORMAT_FR_LONG, "2 millier", 2L},
            {FORMAT_IT_LONG, "2 mille", 2L},
        };
    }

    Object[][] formatFieldPositionData() {
        return new Object[][]{
            //compact number instance, number to format, field, start position, end position, formatted string
            {FORMAT_DZ_LONG, -3500, NumberFormat.Field.SIGN, 0, 1, "-སྟོང་ཕྲག ༤"},
            {FORMAT_DZ_LONG, 3500, NumberFormat.Field.INTEGER, 9, 10, "སྟོང་ཕྲག ༤"},
            {FORMAT_DZ_LONG, -3500, NumberFormat.Field.INTEGER, 10, 11, "-སྟོང་ཕྲག ༤"},
            {FORMAT_DZ_LONG, 999, NumberFormat.Field.INTEGER, 0, 3, "༩༩༩"},
            {FORMAT_DZ_LONG, -999, NumberFormat.Field.INTEGER, 1, 4, "-༩༩༩"},
            {FORMAT_DZ_LONG, 3500, NumberFormat.Field.PREFIX, 0, 9, "སྟོང་ཕྲག ༤"},
            {FORMAT_DZ_LONG, -3500, NumberFormat.Field.PREFIX, 0, 10, "-སྟོང་ཕྲག ༤"},
            {FORMAT_DZ_LONG, 999, NumberFormat.Field.PREFIX, 0, 0, "༩༩༩"},
            {FORMAT_EN_US_SHORT, -3500, NumberFormat.Field.SIGN, 0, 1, "-4K"},
            {FORMAT_EN_US_SHORT, 3500, NumberFormat.Field.INTEGER, 0, 1, "4K"},
            {FORMAT_EN_US_SHORT, 14900000067L, NumberFormat.Field.INTEGER, 0, 2, "15B"},
            {FORMAT_EN_US_SHORT, -1000, NumberFormat.Field.PREFIX, 0, 1, "-1K"},
            {FORMAT_EN_US_SHORT, 3500, NumberFormat.Field.SUFFIX, 1, 2, "4K"},
            {FORMAT_EN_US_SHORT, 14900000067L, NumberFormat.Field.SUFFIX, 2, 3, "15B"},
            {FORMAT_EN_LONG, 3500, NumberFormat.Field.INTEGER, 0, 1, "4 thousand"},
            {FORMAT_EN_LONG, 14900000067L, NumberFormat.Field.INTEGER, 0, 2, "15 billion"},
            {FORMAT_EN_LONG, 3500, NumberFormat.Field.SUFFIX, 1, 10, "4 thousand"},
            {FORMAT_EN_LONG, 14900000067L, NumberFormat.Field.SUFFIX, 2, 10, "15 billion"},
            {FORMAT_JA_JP_SHORT, 14900000067L, NumberFormat.Field.INTEGER, 0, 3, "149億"},
            {FORMAT_JA_JP_SHORT, -999.99, NumberFormat.Field.INTEGER, 1, 6, "-1,000"},
            {FORMAT_JA_JP_SHORT, 14900000067L, NumberFormat.Field.SUFFIX, 3, 4, "149億"},
            {FORMAT_JA_JP_SHORT, -999.99, NumberFormat.Field.SUFFIX, 0, 0, "-1,000"},
            {FORMAT_JA_JP_SHORT, -999.99, NumberFormat.Field.SIGN, 0, 1, "-1,000"},
            {FORMAT_HI_IN_LONG, -14900000067L, NumberFormat.Field.SIGN, 0, 1,
                "-15 अरब"},
            {FORMAT_HI_IN_LONG, 3500, NumberFormat.Field.INTEGER, 0, 1,
                "4 हज़ार"},
            {FORMAT_HI_IN_LONG, 14900000067L, NumberFormat.Field.INTEGER, 0, 2,
                "15 अरब"},
            {FORMAT_HI_IN_LONG, 3500, NumberFormat.Field.SUFFIX, 1, 7,
                "4 हज़ार"},
            {FORMAT_HI_IN_LONG, 14900000067L, NumberFormat.Field.SUFFIX, 2, 6,
                "15 अरब"},
            {FORMAT_SE_SHORT, 8000000L, NumberFormat.Field.SUFFIX, 1, 4, "8 mn"},
            {FORMAT_SE_SHORT, 8000.98, NumberFormat.Field.SUFFIX, 1, 4, "8 dt"},
            {FORMAT_SE_SHORT, new BigInteger("12345678901234567890"), NumberFormat.Field.SUFFIX, 8, 11, "12345679 bn"},
            {FORMAT_SE_SHORT, new BigDecimal("12345678901234567890.98"), NumberFormat.Field.SUFFIX, 8, 11, "12345679 bn"},
            {FORMAT_SE_SHORT, -8000000L, NumberFormat.Field.INTEGER, 1, 2, "−8 mn"},
            {FORMAT_SE_SHORT, -8000.98, NumberFormat.Field.SIGN, 0, 1, "−8 dt"},
            {FORMAT_SE_SHORT, new BigDecimal("-48982865901234567890.98"), NumberFormat.Field.INTEGER, 1, 9, "−48982866 bn"},};
    }

    Object[][] varParsePosition() {
        return new Object[][]{
                // compact number instance, parse string, parsed number,
                // start position, end position, error index
                {FORMAT_DZ_LONG, "སྟོང་ཕྲ"
                        + "ག ༡ KM", 1000L, 0, 10, -1},
                // Invalid prefix returns null
                {FORMAT_DZ_LONG, "Number is: -སྟོང,་ཕྲ"
                        + "ག ༣", null, 11, 11, 11},
                // Returns null
                {FORMAT_DZ_LONG, "སྟོང་ཕྲ"
                        + "ག", null, 0, 0, 0},
                {FORMAT_EN_US_SHORT, "Exponent: -1.05E4K", -1.05, 10, 15, -1},
                // Default instance does not allow grouping
                {FORMAT_EN_US_SHORT, "12,347", 12L, 0, 2, -1},
                // Invalid suffix "KM" for en_US_SHORT patterns
                {FORMAT_EN_US_SHORT, "12KM", 12000L, 0, 3, -1},
                // Invalid suffix
                {FORMAT_HI_IN_LONG, "-1  क.", -1L, 0, 2, -1},
                {FORMAT_EN_LONG, "Number is: 12345679 trillion",
                        1.2345679E19, 11, 28, -1},
                {FORMAT_EN_LONG, "Number is: -12345679 trillion",
                        -1.2345679E19, 11, 29, -1},
                {FORMAT_EN_LONG, "parse 12 thousand and four", 12000L, 6, 17, -1},};
    }

    @Test
    void testInstanceCreation() {
        Stream.of(NumberFormat.getAvailableLocales()).forEach(l -> NumberFormat
                .getCompactNumberInstance(l, NumberFormat.Style.SHORT).format(10000));
        Stream.of(NumberFormat.getAvailableLocales()).forEach(l -> NumberFormat
                .getCompactNumberInstance(l, NumberFormat.Style.LONG).format(10000));
    }

    @Test
    void testFormatWithNullParam() {
        assertThrows(IllegalArgumentException.class, () -> {
            FORMAT_EN_US_SHORT.format(null);
        });
    }

    @ParameterizedTest
    @MethodSource("compactFormatData")
    void testFormat(NumberFormat cnf, Object number,
            String expected) {
        CompactFormatAndParseHelper.testFormat(cnf, number, expected);
    }

    @ParameterizedTest
    @MethodSource("compactParseData")
    void testParse(NumberFormat cnf, String parseString,
            Number expected, Class<? extends Number> returnType) throws ParseException {
        CompactFormatAndParseHelper.testParse(cnf, parseString, expected, null, returnType);
    }

    @ParameterizedTest
    @MethodSource("compactParseData")
    void testParsePosition(NumberFormat cnf, String parseString,
            Number expected, Class<? extends Number> returnType) throws ParseException {
        ParsePosition pos = new ParsePosition(0);
        CompactFormatAndParseHelper.testParse(cnf, parseString, expected, pos, returnType);
        assertEquals(parseString.length(), pos.getIndex());
        assertEquals(-1, pos.getErrorIndex());
    }

    @ParameterizedTest
    @MethodSource("varParsePosition")
    void testVarParsePosition(NumberFormat cnf, String parseString,
            Number expected, int startPosition, int indexPosition,
            int errPosition) throws ParseException {
        ParsePosition pos = new ParsePosition(startPosition);
        CompactFormatAndParseHelper.testParse(cnf, parseString, expected, pos, null);
        assertEquals(indexPosition, pos.getIndex());
        assertEquals(errPosition, pos.getErrorIndex());
    }

    @ParameterizedTest
    @MethodSource("exceptionParseData")
    void throwsParseException(NumberFormat cnf, String parseString,
            Number expected) {
        assertThrows(ParseException.class, () -> CompactFormatAndParseHelper.testParse(cnf, parseString, expected, null, null));
    }

    @ParameterizedTest
    @MethodSource("invalidParseData")
    void testInvalidParse(NumberFormat cnf, String parseString,
            Number expected) throws ParseException {
        CompactFormatAndParseHelper.testParse(cnf, parseString, expected, null, null);
    }

    @ParameterizedTest
    @MethodSource("formatFieldPositionData")
    void testFormatWithFieldPosition(NumberFormat nf,
            Object number, Format.Field field, int posStartExpected,
            int posEndExpected, String expected) {
        FieldPosition pos = new FieldPosition(field);
        StringBuffer buf = new StringBuffer();
        StringBuffer result = nf.format(number, buf, pos);
        assertEquals(expected, result.toString(), "Incorrect formatting of the number '"
                + number + "'");
        assertEquals(posStartExpected, pos.getBeginIndex(), "Incorrect start position"
                + " while formatting the number '" + number + "', for the field " + field);
        assertEquals(posEndExpected, pos.getEndIndex(), "Incorrect end position"
                + " while formatting the number '" + number + "', for the field " + field);
    }

}
