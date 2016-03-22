/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Copyright (c) 2009-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package tck.java.time.format;

import static java.time.format.DateTimeFormatter.BASIC_ISO_DATE;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.OFFSET_SECONDS;
import static java.time.temporal.ChronoField.YEAR;
import static org.testng.Assert.assertEquals;

import java.text.ParsePosition;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test DateTimeFormatterBuilder.
 */
@Test
public class TCKDateTimeFormatterBuilder {

    private DateTimeFormatterBuilder builder;

    @BeforeMethod
    public void setUp() {
        builder = new DateTimeFormatterBuilder();
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_toFormatter_empty() throws Exception {
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.format(LocalDate.of(2012, 6, 30)), "");
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_parseDefaulting_entireDate() {
        DateTimeFormatter f = builder
            .parseDefaulting(YEAR, 2012).parseDefaulting(MONTH_OF_YEAR, 6)
            .parseDefaulting(DAY_OF_MONTH, 30).toFormatter();
        LocalDate parsed = f.parse("", LocalDate::from);  // blank string can be parsed
        assertEquals(parsed, LocalDate.of(2012, 6, 30));
    }

    @Test
    public void test_parseDefaulting_yearOptionalMonthOptionalDay() {
        DateTimeFormatter f = builder
                .appendValue(YEAR)
                .optionalStart().appendLiteral('-').appendValue(MONTH_OF_YEAR)
                .optionalStart().appendLiteral('-').appendValue(DAY_OF_MONTH)
                .optionalEnd().optionalEnd()
                .parseDefaulting(MONTH_OF_YEAR, 1)
                .parseDefaulting(DAY_OF_MONTH, 1).toFormatter();
        assertEquals(f.parse("2012", LocalDate::from), LocalDate.of(2012, 1, 1));
        assertEquals(f.parse("2012-6", LocalDate::from), LocalDate.of(2012, 6, 1));
        assertEquals(f.parse("2012-6-30", LocalDate::from), LocalDate.of(2012, 6, 30));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void test_parseDefaulting_null() {
        builder.parseDefaulting(null, 1);
    }

    //-----------------------------------------------------------------------
    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendValue_1arg_null() throws Exception {
        builder.appendValue(null);
    }

    //-----------------------------------------------------------------------
    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendValue_2arg_null() throws Exception {
        builder.appendValue(null, 3);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValue_2arg_widthTooSmall() throws Exception {
        builder.appendValue(DAY_OF_MONTH, 0);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValue_2arg_widthTooBig() throws Exception {
        builder.appendValue(DAY_OF_MONTH, 20);
    }

    //-----------------------------------------------------------------------
    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendValue_3arg_nullField() throws Exception {
        builder.appendValue(null, 2, 3, SignStyle.NORMAL);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValue_3arg_minWidthTooSmall() throws Exception {
        builder.appendValue(DAY_OF_MONTH, 0, 2, SignStyle.NORMAL);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValue_3arg_minWidthTooBig() throws Exception {
        builder.appendValue(DAY_OF_MONTH, 20, 2, SignStyle.NORMAL);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValue_3arg_maxWidthTooSmall() throws Exception {
        builder.appendValue(DAY_OF_MONTH, 2, 0, SignStyle.NORMAL);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValue_3arg_maxWidthTooBig() throws Exception {
        builder.appendValue(DAY_OF_MONTH, 2, 20, SignStyle.NORMAL);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValue_3arg_maxWidthMinWidth() throws Exception {
        builder.appendValue(DAY_OF_MONTH, 4, 2, SignStyle.NORMAL);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendValue_3arg_nullSignStyle() throws Exception {
        builder.appendValue(DAY_OF_MONTH, 2, 3, null);
    }

    //-----------------------------------------------------------------------
    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendValueReduced_int_nullField() throws Exception {
        builder.appendValueReduced(null, 2, 2, 2000);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValueReduced_int_minWidthTooSmall() throws Exception {
        builder.appendValueReduced(YEAR, 0, 2, 2000);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValueReduced_int_minWidthTooBig() throws Exception {
        builder.appendValueReduced(YEAR, 11, 2, 2000);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValueReduced_int_maxWidthTooSmall() throws Exception {
        builder.appendValueReduced(YEAR, 2, 0, 2000);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValueReduced_int_maxWidthTooBig() throws Exception {
        builder.appendValueReduced(YEAR, 2, 11, 2000);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValueReduced_int_maxWidthLessThanMin() throws Exception {
        builder.appendValueReduced(YEAR, 2, 1, 2000);
    }

    //-----------------------------------------------------------------------
    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendValueReduced_date_nullField() throws Exception {
        builder.appendValueReduced(null, 2, 2, LocalDate.of(2000, 1, 1));
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendValueReduced_date_nullDate() throws Exception {
        builder.appendValueReduced(YEAR, 2, 2, null);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValueReduced_date_minWidthTooSmall() throws Exception {
        builder.appendValueReduced(YEAR, 0, 2, LocalDate.of(2000, 1, 1));
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValueReduced_date_minWidthTooBig() throws Exception {
        builder.appendValueReduced(YEAR, 11, 2, LocalDate.of(2000, 1, 1));
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValueReduced_date_maxWidthTooSmall() throws Exception {
        builder.appendValueReduced(YEAR, 2, 0, LocalDate.of(2000, 1, 1));
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValueReduced_date_maxWidthTooBig() throws Exception {
        builder.appendValueReduced(YEAR, 2, 11, LocalDate.of(2000, 1, 1));
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendValueReduced_date_maxWidthLessThanMin() throws Exception {
        builder.appendValueReduced(YEAR, 2, 1, LocalDate.of(2000, 1, 1));
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendFraction_4arg_nullRule() throws Exception {
        builder.appendFraction(null, 1, 9, false);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendFraction_4arg_invalidRuleNotFixedSet() throws Exception {
        builder.appendFraction(DAY_OF_MONTH, 1, 9, false);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendFraction_4arg_minTooSmall() throws Exception {
        builder.appendFraction(MINUTE_OF_HOUR, -1, 9, false);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendFraction_4arg_minTooBig() throws Exception {
        builder.appendFraction(MINUTE_OF_HOUR, 10, 9, false);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendFraction_4arg_maxTooSmall() throws Exception {
        builder.appendFraction(MINUTE_OF_HOUR, 0, -1, false);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendFraction_4arg_maxTooBig() throws Exception {
        builder.appendFraction(MINUTE_OF_HOUR, 1, 10, false);
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_appendFraction_4arg_maxWidthMinWidth() throws Exception {
        builder.appendFraction(MINUTE_OF_HOUR, 9, 3, false);
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendText_1arg_null() throws Exception {
        builder.appendText(null);
    }

    //-----------------------------------------------------------------------
    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendText_2arg_nullRule() throws Exception {
        builder.appendText(null, TextStyle.SHORT);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendText_2arg_nullStyle() throws Exception {
        builder.appendText(MONTH_OF_YEAR, (TextStyle) null);
    }

    //-----------------------------------------------------------------------
    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendTextMap_nullRule() throws Exception {
        builder.appendText(null, new HashMap<>());
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendTextMap_nullStyle() throws Exception {
        builder.appendText(MONTH_OF_YEAR, (Map<Long, String>) null);
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @DataProvider(name="offsetPatterns")
    Object[][] data_offsetPatterns() {
        return new Object[][] {
                {"+HH", 2, 0, 0, "+02"},
                {"+HH", -2, 0, 0, "-02"},
                {"+HH", 2, 30, 0, "+02"},
                {"+HH", 2, 0, 45, "+02"},
                {"+HH", 2, 30, 45, "+02"},

                {"+HHmm", 2, 0, 0, "+02"},
                {"+HHmm", -2, 0, 0, "-02"},
                {"+HHmm", 2, 30, 0, "+0230"},
                {"+HHmm", 2, 0, 45, "+02"},
                {"+HHmm", 2, 30, 45, "+0230"},

                {"+HH:mm", 2, 0, 0, "+02"},
                {"+HH:mm", -2, 0, 0, "-02"},
                {"+HH:mm", 2, 30, 0, "+02:30"},
                {"+HH:mm", 2, 0, 45, "+02"},
                {"+HH:mm", 2, 30, 45, "+02:30"},

                {"+HHMM", 2, 0, 0, "+0200"},
                {"+HHMM", -2, 0, 0, "-0200"},
                {"+HHMM", 2, 30, 0, "+0230"},
                {"+HHMM", 2, 0, 45, "+0200"},
                {"+HHMM", 2, 30, 45, "+0230"},

                {"+HH:MM", 2, 0, 0, "+02:00"},
                {"+HH:MM", -2, 0, 0, "-02:00"},
                {"+HH:MM", 2, 30, 0, "+02:30"},
                {"+HH:MM", 2, 0, 45, "+02:00"},
                {"+HH:MM", 2, 30, 45, "+02:30"},

                {"+HHMMss", 2, 0, 0, "+0200"},
                {"+HHMMss", -2, 0, 0, "-0200"},
                {"+HHMMss", 2, 30, 0, "+0230"},
                {"+HHMMss", 2, 0, 45, "+020045"},
                {"+HHMMss", 2, 30, 45, "+023045"},

                {"+HH:MM:ss", 2, 0, 0, "+02:00"},
                {"+HH:MM:ss", -2, 0, 0, "-02:00"},
                {"+HH:MM:ss", 2, 30, 0, "+02:30"},
                {"+HH:MM:ss", 2, 0, 45, "+02:00:45"},
                {"+HH:MM:ss", 2, 30, 45, "+02:30:45"},

                {"+HHMMSS", 2, 0, 0, "+020000"},
                {"+HHMMSS", -2, 0, 0, "-020000"},
                {"+HHMMSS", 2, 30, 0, "+023000"},
                {"+HHMMSS", 2, 0, 45, "+020045"},
                {"+HHMMSS", 2, 30, 45, "+023045"},

                {"+HH:MM:SS", 2, 0, 0, "+02:00:00"},
                {"+HH:MM:SS", -2, 0, 0, "-02:00:00"},
                {"+HH:MM:SS", 2, 30, 0, "+02:30:00"},
                {"+HH:MM:SS", 2, 0, 45, "+02:00:45"},
                {"+HH:MM:SS", 2, 30, 45, "+02:30:45"},

                {"+HHmmss", 2, 0, 0, "+02"},
                {"+HHmmss", -2, 0, 0, "-02"},
                {"+HHmmss", 2, 30, 0, "+0230"},
                {"+HHmmss", 2, 0, 45, "+020045"},
                {"+HHmmss", 2, 30, 45, "+023045"},

                {"+HH:mm:ss", 2, 0, 0, "+02"},
                {"+HH:mm:ss", -2, 0, 0, "-02"},
                {"+HH:mm:ss", 2, 30, 0, "+02:30"},
                {"+HH:mm:ss", 2, 0, 45, "+02:00:45"},
                {"+HH:mm:ss", 2, 30, 45, "+02:30:45"},


        };
    }

    @Test(dataProvider="offsetPatterns")
    public void test_appendOffset_format(String pattern, int h, int m, int s, String expected) throws Exception {
        builder.appendOffset(pattern, "Z");
        DateTimeFormatter f = builder.toFormatter();
        ZoneOffset offset = ZoneOffset.ofHoursMinutesSeconds(h, m, s);
        assertEquals(f.format(offset), expected);
    }

    @Test(dataProvider="offsetPatterns")
    public void test_appendOffset_parse(String pattern, int h, int m, int s, String expected) throws Exception {
        builder.appendOffset(pattern, "Z");
        DateTimeFormatter f = builder.toFormatter();
        ZoneOffset parsed = f.parse(expected, ZoneOffset::from);
        assertEquals(f.format(parsed), expected);
    }

    @DataProvider(name="badOffsetPatterns")
    Object[][] data_badOffsetPatterns() {
        return new Object[][] {
            {"HH"},
            {"HHMM"},
            {"HH:MM"},
            {"HHMMss"},
            {"HH:MM:ss"},
            {"HHMMSS"},
            {"HH:MM:SS"},
            {"+H"},
            {"+HMM"},
            {"+HHM"},
            {"+A"},
        };
    }

    @Test(dataProvider="badOffsetPatterns", expectedExceptions=IllegalArgumentException.class)
    public void test_appendOffset_badPattern(String pattern) throws Exception {
        builder.appendOffset(pattern, "Z");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendOffset_3arg_nullText() throws Exception {
        builder.appendOffset("+HH:MM", null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendOffset_3arg_nullPattern() throws Exception {
        builder.appendOffset(null, "Z");
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendZoneText_1arg_nullText() throws Exception {
        builder.appendZoneText(null);
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @Test
    public void test_padNext_1arg() {
        builder.appendValue(MONTH_OF_YEAR).appendLiteral(':').padNext(2).appendValue(DAY_OF_MONTH);
        assertEquals(builder.toFormatter().format(LocalDate.of(2013, 2, 1)), "2: 1");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_padNext_1arg_invalidWidth() throws Exception {
        builder.padNext(0);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_padNext_2arg_dash() throws Exception {
        builder.appendValue(MONTH_OF_YEAR).appendLiteral(':').padNext(2, '-').appendValue(DAY_OF_MONTH);
        assertEquals(builder.toFormatter().format(LocalDate.of(2013, 2, 1)), "2:-1");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_padNext_2arg_invalidWidth() throws Exception {
        builder.padNext(0, '-');
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_padOptional() throws Exception {
        builder.appendValue(MONTH_OF_YEAR).appendLiteral(':')
                .padNext(5).optionalStart().appendValue(DAY_OF_MONTH).optionalEnd()
                .appendLiteral(':').appendValue(YEAR);
        assertEquals(builder.toFormatter().format(LocalDate.of(2013, 2, 1)), "2:    1:2013");
        assertEquals(builder.toFormatter().format(YearMonth.of(2013, 2)), "2:     :2013");
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @Test(expectedExceptions=IllegalStateException.class)
    public void test_optionalEnd_noStart() throws Exception {
        builder.optionalEnd();
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @DataProvider(name="validPatterns")
    Object[][] dataValid() {
        return new Object[][] {
            {"'a'"},
            {"''"},
            {"'!'"},
            {"!"},
            {"'#'"},

            {"'hello_people,][)('"},
            {"'hi'"},
            {"'yyyy'"},
            {"''''"},
            {"'o''clock'"},

            {"G"},
            {"GG"},
            {"GGG"},
            {"GGGG"},
            {"GGGGG"},

            {"y"},
            {"yy"},
            {"yyy"},
            {"yyyy"},
            {"yyyyy"},

            {"M"},
            {"MM"},
            {"MMM"},
            {"MMMM"},
            {"MMMMM"},

            {"L"},
            {"LL"},
            {"LLL"},
            {"LLLL"},
            {"LLLLL"},

            {"D"},
            {"DD"},
            {"DDD"},

            {"d"},
            {"dd"},

            {"F"},

            {"Q"},
            {"QQ"},
            {"QQQ"},
            {"QQQQ"},
            {"QQQQQ"},

            {"q"},
            {"qq"},
            {"qqq"},
            {"qqqq"},
            {"qqqqq"},

            {"E"},
            {"EE"},
            {"EEE"},
            {"EEEE"},
            {"EEEEE"},

            {"e"},
            {"ee"},
            {"eee"},
            {"eeee"},
            {"eeeee"},

            {"c"},
            {"ccc"},
            {"cccc"},
            {"ccccc"},

            {"a"},

            {"H"},
            {"HH"},

            {"K"},
            {"KK"},

            {"k"},
            {"kk"},

            {"h"},
            {"hh"},

            {"m"},
            {"mm"},

            {"s"},
            {"ss"},

            {"S"},
            {"SS"},
            {"SSS"},
            {"SSSSSSSSS"},

            {"A"},
            {"AA"},
            {"AAA"},

            {"n"},
            {"nn"},
            {"nnn"},

            {"N"},
            {"NN"},
            {"NNN"},

            {"z"},
            {"zz"},
            {"zzz"},
            {"zzzz"},

            {"VV"},

            {"Z"},
            {"ZZ"},
            {"ZZZ"},

            {"X"},
            {"XX"},
            {"XXX"},
            {"XXXX"},
            {"XXXXX"},

            {"x"},
            {"xx"},
            {"xxx"},
            {"xxxx"},
            {"xxxxx"},

            {"ppH"},
            {"pppDD"},

            {"yyyy[-MM[-dd"},
            {"yyyy[-MM[-dd]]"},
            {"yyyy[-MM[]-dd]"},

            {"yyyy-MM-dd'T'HH:mm:ss.SSS"},

            {"e"},
            {"w"},
            {"ww"},
            {"W"},
            {"W"},

        };
    }

    @Test(dataProvider="validPatterns")
    public void test_appendPattern_valid(String input) throws Exception {
        builder.appendPattern(input);  // test is for no error here
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="invalidPatterns")
    Object[][] dataInvalid() {
        return new Object[][] {
            {"'"},
            {"'hello"},
            {"'hel''lo"},
            {"'hello''"},
            {"{"},
            {"}"},
            {"{}"},
            {"#"},
            {"]"},
            {"yyyy]"},
            {"yyyy]MM"},
            {"yyyy[MM]]"},

            {"aa"},
            {"aaa"},
            {"aaaa"},
            {"aaaaa"},
            {"aaaaaa"},
            {"MMMMMM"},
            {"QQQQQQ"},
            {"qqqqqq"},
            {"EEEEEE"},
            {"eeeeee"},
            {"cc"},
            {"cccccc"},
            {"ddd"},
            {"DDDD"},
            {"FF"},
            {"FFF"},
            {"hhh"},
            {"HHH"},
            {"kkk"},
            {"KKK"},
            {"mmm"},
            {"sss"},
            {"OO"},
            {"OOO"},
            {"OOOOO"},
            {"XXXXXX"},
            {"zzzzz"},
            {"ZZZZZZ"},

            {"RO"},

            {"p"},
            {"pp"},
            {"p:"},

            {"f"},
            {"ff"},
            {"f:"},
            {"fy"},
            {"fa"},
            {"fM"},

            {"www"},
            {"WW"},
        };
    }

    @Test(dataProvider="invalidPatterns", expectedExceptions=IllegalArgumentException.class)
    public void test_appendPattern_invalid(String input) throws Exception {
        builder.appendPattern(input);  // test is for error here
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="patternPrint")
    Object[][] data_patternPrint() {
        return new Object[][] {
            {"Q", date(2012, 2, 10), "1"},
            {"QQ", date(2012, 2, 10), "01"},
            {"QQQ", date(2012, 2, 10), "Q1"},
            {"QQQQ", date(2012, 2, 10), "1st quarter"},
            {"QQQQQ", date(2012, 2, 10), "1"},
        };
    }

    @Test(dataProvider="patternPrint")
    public void test_appendPattern_patternPrint(String input, Temporal temporal, String expected) throws Exception {
        DateTimeFormatter f = builder.appendPattern(input).toFormatter(Locale.UK);
        String test = f.format(temporal);
        assertEquals(test, expected);
    }

    private static Temporal date(int y, int m, int d) {
        return LocalDate.of(y, m, d);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_adjacent_strict_firstFixedWidth() throws Exception {
        // succeeds because both number elements are fixed width
        DateTimeFormatter f = builder.appendValue(HOUR_OF_DAY, 2).appendValue(MINUTE_OF_HOUR, 2).appendLiteral('9').toFormatter(Locale.UK);
        ParsePosition pp = new ParsePosition(0);
        TemporalAccessor parsed = f.parseUnresolved("12309", pp);
        assertEquals(pp.getErrorIndex(), -1);
        assertEquals(pp.getIndex(), 5);
        assertEquals(parsed.getLong(HOUR_OF_DAY), 12L);
        assertEquals(parsed.getLong(MINUTE_OF_HOUR), 30L);
    }

    @Test
    public void test_adjacent_strict_firstVariableWidth_success() throws Exception {
        // succeeds greedily parsing variable width, then fixed width, to non-numeric Z
        DateTimeFormatter f = builder.appendValue(HOUR_OF_DAY).appendValue(MINUTE_OF_HOUR, 2).appendLiteral('Z').toFormatter(Locale.UK);
        ParsePosition pp = new ParsePosition(0);
        TemporalAccessor parsed = f.parseUnresolved("12309Z", pp);
        assertEquals(pp.getErrorIndex(), -1);
        assertEquals(pp.getIndex(), 6);
        assertEquals(parsed.getLong(HOUR_OF_DAY), 123L);
        assertEquals(parsed.getLong(MINUTE_OF_HOUR), 9L);
    }

    @Test
    public void test_adjacent_strict_firstVariableWidth_fails() throws Exception {
        // fails because literal is a number and variable width parse greedily absorbs it
        DateTimeFormatter f = builder.appendValue(HOUR_OF_DAY).appendValue(MINUTE_OF_HOUR, 2).appendLiteral('9').toFormatter(Locale.UK);
        ParsePosition pp = new ParsePosition(0);
        TemporalAccessor parsed = f.parseUnresolved("12309", pp);
        assertEquals(pp.getErrorIndex(), 5);
        assertEquals(parsed, null);
    }

    @Test
    public void test_adjacent_lenient() throws Exception {
        // succeeds because both number elements are fixed width even in lenient mode
        DateTimeFormatter f = builder.parseLenient().appendValue(HOUR_OF_DAY, 2).appendValue(MINUTE_OF_HOUR, 2).appendLiteral('9').toFormatter(Locale.UK);
        ParsePosition pp = new ParsePosition(0);
        TemporalAccessor parsed = f.parseUnresolved("12309", pp);
        assertEquals(pp.getErrorIndex(), -1);
        assertEquals(pp.getIndex(), 5);
        assertEquals(parsed.getLong(HOUR_OF_DAY), 12L);
        assertEquals(parsed.getLong(MINUTE_OF_HOUR), 30L);
    }

    @Test
    public void test_adjacent_lenient_firstVariableWidth_success() throws Exception {
        // succeeds greedily parsing variable width, then fixed width, to non-numeric Z
        DateTimeFormatter f = builder.parseLenient().appendValue(HOUR_OF_DAY).appendValue(MINUTE_OF_HOUR, 2).appendLiteral('Z').toFormatter(Locale.UK);
        ParsePosition pp = new ParsePosition(0);
        TemporalAccessor parsed = f.parseUnresolved("12309Z", pp);
        assertEquals(pp.getErrorIndex(), -1);
        assertEquals(pp.getIndex(), 6);
        assertEquals(parsed.getLong(HOUR_OF_DAY), 123L);
        assertEquals(parsed.getLong(MINUTE_OF_HOUR), 9L);
    }

    @Test
    public void test_adjacent_lenient_firstVariableWidth_fails() throws Exception {
        // fails because literal is a number and variable width parse greedily absorbs it
        DateTimeFormatter f = builder.parseLenient().appendValue(HOUR_OF_DAY).appendValue(MINUTE_OF_HOUR, 2).appendLiteral('9').toFormatter(Locale.UK);
        ParsePosition pp = new ParsePosition(0);
        TemporalAccessor parsed = f.parseUnresolved("12309", pp);
        assertEquals(pp.getErrorIndex(), 5);
        assertEquals(parsed, null);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_adjacent_strict_fractionFollows() throws Exception {
        // succeeds because hour/min are fixed width
        DateTimeFormatter f = builder.appendValue(HOUR_OF_DAY, 2).appendValue(MINUTE_OF_HOUR, 2).appendFraction(NANO_OF_SECOND, 0, 3, false).toFormatter(Locale.UK);
        ParsePosition pp = new ParsePosition(0);
        TemporalAccessor parsed = f.parseUnresolved("1230567", pp);
        assertEquals(pp.getErrorIndex(), -1);
        assertEquals(pp.getIndex(), 7);
        assertEquals(parsed.getLong(HOUR_OF_DAY), 12L);
        assertEquals(parsed.getLong(MINUTE_OF_HOUR), 30L);
        assertEquals(parsed.getLong(NANO_OF_SECOND), 567_000_000L);
    }

    @Test
    public void test_adjacent_strict_fractionFollows_2digit() throws Exception {
        // succeeds because hour/min are fixed width
        DateTimeFormatter f = builder.appendValue(HOUR_OF_DAY, 2).appendValue(MINUTE_OF_HOUR, 2).appendFraction(NANO_OF_SECOND, 0, 3, false).toFormatter(Locale.UK);
        ParsePosition pp = new ParsePosition(0);
        TemporalAccessor parsed = f.parseUnresolved("123056", pp);
        assertEquals(pp.getErrorIndex(), -1);
        assertEquals(pp.getIndex(), 6);
        assertEquals(parsed.getLong(HOUR_OF_DAY), 12L);
        assertEquals(parsed.getLong(MINUTE_OF_HOUR), 30L);
        assertEquals(parsed.getLong(NANO_OF_SECOND), 560_000_000L);
    }

    @Test
    public void test_adjacent_strict_fractionFollows_0digit() throws Exception {
        // succeeds because hour/min are fixed width
        DateTimeFormatter f = builder.appendValue(HOUR_OF_DAY, 2).appendValue(MINUTE_OF_HOUR, 2).appendFraction(NANO_OF_SECOND, 0, 3, false).toFormatter(Locale.UK);
        ParsePosition pp = new ParsePosition(0);
        TemporalAccessor parsed = f.parseUnresolved("1230", pp);
        assertEquals(pp.getErrorIndex(), -1);
        assertEquals(pp.getIndex(), 4);
        assertEquals(parsed.getLong(HOUR_OF_DAY), 12L);
        assertEquals(parsed.getLong(MINUTE_OF_HOUR), 30L);
    }

    @Test
    public void test_adjacent_lenient_fractionFollows() throws Exception {
        // succeeds because hour/min are fixed width
        DateTimeFormatter f = builder.parseLenient().appendValue(HOUR_OF_DAY, 2).appendValue(MINUTE_OF_HOUR, 2).appendFraction(NANO_OF_SECOND, 3, 3, false).toFormatter(Locale.UK);
        ParsePosition pp = new ParsePosition(0);
        TemporalAccessor parsed = f.parseUnresolved("1230567", pp);
        assertEquals(pp.getErrorIndex(), -1);
        assertEquals(pp.getIndex(), 7);
        assertEquals(parsed.getLong(HOUR_OF_DAY), 12L);
        assertEquals(parsed.getLong(MINUTE_OF_HOUR), 30L);
        assertEquals(parsed.getLong(NANO_OF_SECOND), 567_000_000L);
    }

    @Test
    public void test_adjacent_lenient_fractionFollows_2digit() throws Exception {
        // succeeds because hour/min are fixed width
        DateTimeFormatter f = builder.parseLenient().appendValue(HOUR_OF_DAY, 2).appendValue(MINUTE_OF_HOUR, 2).appendFraction(NANO_OF_SECOND, 3, 3, false).toFormatter(Locale.UK);
        ParsePosition pp = new ParsePosition(0);
        TemporalAccessor parsed = f.parseUnresolved("123056", pp);
        assertEquals(pp.getErrorIndex(), -1);
        assertEquals(pp.getIndex(), 6);
        assertEquals(parsed.getLong(HOUR_OF_DAY), 12L);
        assertEquals(parsed.getLong(MINUTE_OF_HOUR), 30L);
        assertEquals(parsed.getLong(NANO_OF_SECOND), 560_000_000L);
    }

    @Test
    public void test_adjacent_lenient_fractionFollows_0digit() throws Exception {
        // succeeds because hour/min are fixed width
        DateTimeFormatter f = builder.parseLenient().appendValue(HOUR_OF_DAY, 2).appendValue(MINUTE_OF_HOUR, 2).appendFraction(NANO_OF_SECOND, 3, 3, false).toFormatter(Locale.UK);
        ParsePosition pp = new ParsePosition(0);
        TemporalAccessor parsed = f.parseUnresolved("1230", pp);
        assertEquals(pp.getErrorIndex(), -1);
        assertEquals(pp.getIndex(), 4);
        assertEquals(parsed.getLong(HOUR_OF_DAY), 12L);
        assertEquals(parsed.getLong(MINUTE_OF_HOUR), 30L);
    }

    @DataProvider(name="lenientOffsetParseData")
    Object[][] data_lenient_offset_parse() {
        return new Object[][] {
            {"+HH", "+01", 3600},
            {"+HH", "+0101", 3660},
            {"+HH", "+010101", 3661},
            {"+HH", "+01", 3600},
            {"+HH", "+01:01", 3660},
            {"+HH", "+01:01:01", 3661},
            {"+HHmm", "+01", 3600},
            {"+HHmm", "+0101", 3660},
            {"+HHmm", "+010101", 3661},
            {"+HH:mm", "+01", 3600},
            {"+HH:mm", "+01:01", 3660},
            {"+HH:mm", "+01:01:01", 3661},
            {"+HHMM", "+01", 3600},
            {"+HHMM", "+0101", 3660},
            {"+HHMM", "+010101", 3661},
            {"+HH:MM", "+01", 3600},
            {"+HH:MM", "+01:01", 3660},
            {"+HH:MM", "+01:01:01", 3661},
            {"+HHMMss", "+01", 3600},
            {"+HHMMss", "+0101", 3660},
            {"+HHMMss", "+010101", 3661},
            {"+HH:MM:ss", "+01", 3600},
            {"+HH:MM:ss", "+01:01", 3660},
            {"+HH:MM:ss", "+01:01:01", 3661},
            {"+HHMMSS", "+01", 3600},
            {"+HHMMSS", "+0101", 3660},
            {"+HHMMSS", "+010101", 3661},
            {"+HH:MM:SS", "+01", 3600},
            {"+HH:MM:SS", "+01:01", 3660},
            {"+HH:MM:SS", "+01:01:01", 3661},
            {"+HHmmss", "+01", 3600},
            {"+HHmmss", "+0101", 3660},
            {"+HHmmss", "+010101", 3661},
            {"+HH:mm:ss", "+01", 3600},
            {"+HH:mm:ss", "+01:01", 3660},
            {"+HH:mm:ss", "+01:01:01", 3661},
        };
    }

    @Test(dataProvider="lenientOffsetParseData")
    public void test_lenient_offset_parse_1(String pattern, String offset, int offsetSeconds) {
        assertEquals(new DateTimeFormatterBuilder().parseLenient().appendOffset(pattern, "Z").toFormatter().parse(offset).get(OFFSET_SECONDS),
                     offsetSeconds);
    }

    @Test
    public void test_lenient_offset_parse_2() {
        assertEquals(new DateTimeFormatterBuilder().parseLenient().appendOffsetId().toFormatter().parse("+01").get(OFFSET_SECONDS),
                     3600);
    }

    @Test(expectedExceptions=DateTimeParseException.class)
    public void test_strict_appendOffsetId() {
        assertEquals(new DateTimeFormatterBuilder().appendOffsetId().toFormatter().parse("+01").get(OFFSET_SECONDS),
                     3600);
    }

    @Test(expectedExceptions=DateTimeParseException.class)
    public void test_strict_appendOffset_1() {
        assertEquals(new DateTimeFormatterBuilder().appendOffset("+HH:MM:ss", "Z").toFormatter().parse("+01").get(OFFSET_SECONDS),
                     3600);
    }

    @Test(expectedExceptions=DateTimeParseException.class)
    public void test_strict_appendOffset_2() {
        assertEquals(new DateTimeFormatterBuilder().appendOffset("+HHMMss", "Z").toFormatter().parse("+01").get(OFFSET_SECONDS),
                     3600);
    }

    @Test
    public void test_basic_iso_date() {
        assertEquals(BASIC_ISO_DATE.parse("20021231+01").get(OFFSET_SECONDS), 3600);
        assertEquals(BASIC_ISO_DATE.parse("20021231+0101").get(OFFSET_SECONDS), 3660);
    }

}
