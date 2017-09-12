/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package test.java.time.format;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.text.ParsePosition;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.chrono.Chronology;
import java.time.chrono.IsoChronology;
import java.time.chrono.JapaneseChronology;
import java.time.chrono.MinguoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
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
public class TestDateTimeFormatterBuilder {

    private DateTimeFormatterBuilder builder;

    @BeforeMethod
    public void setUp() {
        builder = new DateTimeFormatterBuilder();
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_toFormatter_empty() throws Exception {
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "");
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_parseCaseSensitive() throws Exception {
        builder.parseCaseSensitive();
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "ParseCaseSensitive(true)");
    }

    @Test
    public void test_parseCaseInsensitive() throws Exception {
        builder.parseCaseInsensitive();
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "ParseCaseSensitive(false)");
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_parseStrict() throws Exception {
        builder.parseStrict();
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "ParseStrict(true)");
    }

    @Test
    public void test_parseLenient() throws Exception {
        builder.parseLenient();
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "ParseStrict(false)");
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_appendValue_1arg() throws Exception {
        builder.appendValue(DAY_OF_MONTH);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(DayOfMonth)");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendValue_1arg_null() throws Exception {
        builder.appendValue(null);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_appendValue_2arg() throws Exception {
        builder.appendValue(DAY_OF_MONTH, 3);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(DayOfMonth,3)");
    }

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
    @Test
    public void test_appendValue_3arg() throws Exception {
        builder.appendValue(DAY_OF_MONTH, 2, 3, SignStyle.NORMAL);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(DayOfMonth,2,3,NORMAL)");
    }

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
    @Test
    public void test_appendValue_subsequent2_parse3() throws Exception {
        builder.appendValue(MONTH_OF_YEAR, 1, 2, SignStyle.NORMAL).appendValue(DAY_OF_MONTH, 2);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(MonthOfYear,1,2,NORMAL)Value(DayOfMonth,2)");
        TemporalAccessor parsed = f.parseUnresolved("123", new ParsePosition(0));
        assertEquals(parsed.getLong(MONTH_OF_YEAR), 1L);
        assertEquals(parsed.getLong(DAY_OF_MONTH), 23L);
    }

    @Test
    public void test_appendValue_subsequent2_parse4() throws Exception {
        builder.appendValue(MONTH_OF_YEAR, 1, 2, SignStyle.NORMAL).appendValue(DAY_OF_MONTH, 2);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(MonthOfYear,1,2,NORMAL)Value(DayOfMonth,2)");
        TemporalAccessor parsed = f.parseUnresolved("0123", new ParsePosition(0));
        assertEquals(parsed.getLong(MONTH_OF_YEAR), 1L);
        assertEquals(parsed.getLong(DAY_OF_MONTH), 23L);
    }

    @Test
    public void test_appendValue_subsequent2_parse5() throws Exception {
        builder.appendValue(MONTH_OF_YEAR, 1, 2, SignStyle.NORMAL).appendValue(DAY_OF_MONTH, 2).appendLiteral('4');
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(MonthOfYear,1,2,NORMAL)Value(DayOfMonth,2)'4'");
        TemporalAccessor parsed = f.parseUnresolved("01234", new ParsePosition(0));
        assertEquals(parsed.getLong(MONTH_OF_YEAR), 1L);
        assertEquals(parsed.getLong(DAY_OF_MONTH), 23L);
    }

    @Test
    public void test_appendValue_subsequent3_parse6() throws Exception {
        builder
            .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendValue(MONTH_OF_YEAR, 2)
            .appendValue(DAY_OF_MONTH, 2);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(Year,4,10,EXCEEDS_PAD)Value(MonthOfYear,2)Value(DayOfMonth,2)");
        TemporalAccessor parsed = f.parseUnresolved("20090630", new ParsePosition(0));
        assertEquals(parsed.getLong(YEAR), 2009L);
        assertEquals(parsed.getLong(MONTH_OF_YEAR), 6L);
        assertEquals(parsed.getLong(DAY_OF_MONTH), 30L);
    }

    //-----------------------------------------------------------------------
    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendValueReduced_null() throws Exception {
        builder.appendValueReduced(null, 2, 2, 2000);
    }

    @Test
    public void test_appendValueReduced() throws Exception {
        builder.appendValueReduced(YEAR, 2, 2, 2000);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "ReducedValue(Year,2,2,2000)");
        TemporalAccessor parsed = f.parseUnresolved("12", new ParsePosition(0));
        assertEquals(parsed.getLong(YEAR), 2012L);
    }

    @Test
    public void test_appendValueReduced_subsequent_parse() throws Exception {
        builder.appendValue(MONTH_OF_YEAR, 1, 2, SignStyle.NORMAL).appendValueReduced(YEAR, 2, 2, 2000);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(MonthOfYear,1,2,NORMAL)ReducedValue(Year,2,2,2000)");
        ParsePosition ppos = new ParsePosition(0);
        TemporalAccessor parsed = f.parseUnresolved("123", ppos);
        assertNotNull(parsed, "Parse failed: " + ppos.toString());
        assertEquals(parsed.getLong(MONTH_OF_YEAR), 1L);
        assertEquals(parsed.getLong(YEAR), 2023L);
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @Test
    public void test_appendFraction_4arg() throws Exception {
        builder.appendFraction(MINUTE_OF_HOUR, 1, 9, false);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Fraction(MinuteOfHour,1,9)");
    }

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
    @Test
    public void test_appendText_1arg() throws Exception {
        builder.appendText(MONTH_OF_YEAR);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Text(MonthOfYear)");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendText_1arg_null() throws Exception {
        builder.appendText(null);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_appendText_2arg() throws Exception {
        builder.appendText(MONTH_OF_YEAR, TextStyle.SHORT);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Text(MonthOfYear,SHORT)");
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendText_2arg_nullRule() throws Exception {
        builder.appendText(null, TextStyle.SHORT);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendText_2arg_nullStyle() throws Exception {
        builder.appendText(MONTH_OF_YEAR, (TextStyle) null);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_appendTextMap() throws Exception {
        Map<Long, String> map = new HashMap<>();
        map.put(1L, "JNY");
        map.put(2L, "FBY");
        map.put(3L, "MCH");
        map.put(4L, "APL");
        map.put(5L, "MAY");
        map.put(6L, "JUN");
        map.put(7L, "JLY");
        map.put(8L, "AGT");
        map.put(9L, "SPT");
        map.put(10L, "OBR");
        map.put(11L, "NVR");
        map.put(12L, "DBR");
        builder.appendText(MONTH_OF_YEAR, map);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Text(MonthOfYear)");  // TODO: toString should be different?
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendTextMap_nullRule() throws Exception {
        builder.appendText(null, new HashMap<Long, String>());
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_appendTextMap_nullStyle() throws Exception {
        builder.appendText(MONTH_OF_YEAR, (Map<Long, String>) null);
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @Test
    public void test_appendOffsetId() throws Exception {
        builder.appendOffsetId();
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Offset(+HH:MM:ss,'Z')");
    }

    @DataProvider(name="offsetPatterns")
    Object[][] data_offsetPatterns() {
        return new Object[][] {
                {"+HH", 2, 0, 0, "+02"},
                {"+HH", -2, 0, 0, "-02"},
                {"+HH", 2, 30, 0, "+02"},
                {"+HH", 2, 0, 45, "+02"},
                {"+HH", 2, 30, 45, "+02"},

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
        ZoneOffset offset = ZoneOffset.ofHoursMinutesSeconds(h, m, s);
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
    @Test
    public void test_appendZoneId() throws Exception {
        builder.appendZoneId();
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "ZoneId()");
    }

    @Test
    public void test_appendZoneText_1arg() throws Exception {
        builder.appendZoneText(TextStyle.FULL);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "ZoneText(FULL)");
    }

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
    @Test
    public void test_optionalStart_noEnd() throws Exception {
        builder.appendValue(MONTH_OF_YEAR).optionalStart().appendValue(DAY_OF_MONTH).appendValue(DAY_OF_WEEK);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(MonthOfYear)[Value(DayOfMonth)Value(DayOfWeek)]");
    }

    @Test
    public void test_optionalStart2_noEnd() throws Exception {
        builder.appendValue(MONTH_OF_YEAR).optionalStart().appendValue(DAY_OF_MONTH).optionalStart().appendValue(DAY_OF_WEEK);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(MonthOfYear)[Value(DayOfMonth)[Value(DayOfWeek)]]");
    }

    @Test
    public void test_optionalStart_doubleStart() throws Exception {
        builder.appendValue(MONTH_OF_YEAR).optionalStart().optionalStart().appendValue(DAY_OF_MONTH);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(MonthOfYear)[[Value(DayOfMonth)]]");
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_optionalEnd() throws Exception {
        builder.appendValue(MONTH_OF_YEAR).optionalStart().appendValue(DAY_OF_MONTH).optionalEnd().appendValue(DAY_OF_WEEK);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(MonthOfYear)[Value(DayOfMonth)]Value(DayOfWeek)");
    }

    @Test
    public void test_optionalEnd2() throws Exception {
        builder.appendValue(MONTH_OF_YEAR).optionalStart().appendValue(DAY_OF_MONTH)
            .optionalStart().appendValue(DAY_OF_WEEK).optionalEnd().appendValue(DAY_OF_MONTH).optionalEnd();
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(MonthOfYear)[Value(DayOfMonth)[Value(DayOfWeek)]Value(DayOfMonth)]");
    }

    @Test
    public void test_optionalEnd_doubleStartSingleEnd() throws Exception {
        builder.appendValue(MONTH_OF_YEAR).optionalStart().optionalStart().appendValue(DAY_OF_MONTH).optionalEnd();
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(MonthOfYear)[[Value(DayOfMonth)]]");
    }

    @Test
    public void test_optionalEnd_doubleStartDoubleEnd() throws Exception {
        builder.appendValue(MONTH_OF_YEAR).optionalStart().optionalStart().appendValue(DAY_OF_MONTH).optionalEnd().optionalEnd();
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(MonthOfYear)[[Value(DayOfMonth)]]");
    }

    @Test
    public void test_optionalStartEnd_immediateStartEnd() throws Exception {
        builder.appendValue(MONTH_OF_YEAR).optionalStart().optionalEnd().appendValue(DAY_OF_MONTH);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), "Value(MonthOfYear)Value(DayOfMonth)");
    }

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
            {"'a'", "'a'"},
            {"''", "''"},
            {"'!'", "'!'"},
            {"!", "'!'"},

            {"'hello_people,][)('", "'hello_people,][)('"},
            {"'hi'", "'hi'"},
            {"'yyyy'", "'yyyy'"},
            {"''''", "''"},
            {"'o''clock'", "'o''clock'"},

            {"G", "Text(Era,SHORT)"},
            {"GG", "Text(Era,SHORT)"},
            {"GGG", "Text(Era,SHORT)"},
            {"GGGG", "Text(Era)"},
            {"GGGGG", "Text(Era,NARROW)"},

            {"u", "Value(Year)"},
            {"uu", "ReducedValue(Year,2,2,2000-01-01)"},
            {"uuu", "Value(Year,3,19,NORMAL)"},
            {"uuuu", "Value(Year,4,19,EXCEEDS_PAD)"},
            {"uuuuu", "Value(Year,5,19,EXCEEDS_PAD)"},

            {"y", "Value(YearOfEra)"},
            {"yy", "ReducedValue(YearOfEra,2,2,2000-01-01)"},
            {"yyy", "Value(YearOfEra,3,19,NORMAL)"},
            {"yyyy", "Value(YearOfEra,4,19,EXCEEDS_PAD)"},
            {"yyyyy", "Value(YearOfEra,5,19,EXCEEDS_PAD)"},

            {"Y", "Localized(WeekBasedYear)"},
            {"YY", "Localized(ReducedValue(WeekBasedYear,2,2,2000-01-01))"},
            {"YYY", "Localized(WeekBasedYear,3,19,NORMAL)"},
            {"YYYY", "Localized(WeekBasedYear,4,19,EXCEEDS_PAD)"},
            {"YYYYY", "Localized(WeekBasedYear,5,19,EXCEEDS_PAD)"},

            {"M", "Value(MonthOfYear)"},
            {"MM", "Value(MonthOfYear,2)"},
            {"MMM", "Text(MonthOfYear,SHORT)"},
            {"MMMM", "Text(MonthOfYear)"},
            {"MMMMM", "Text(MonthOfYear,NARROW)"},

            {"L", "Value(MonthOfYear)"},
            {"LL", "Value(MonthOfYear,2)"},
            {"LLL", "Text(MonthOfYear,SHORT_STANDALONE)"},
            {"LLLL", "Text(MonthOfYear,FULL_STANDALONE)"},
            {"LLLLL", "Text(MonthOfYear,NARROW_STANDALONE)"},

            {"D", "Value(DayOfYear)"},
            {"DD", "Value(DayOfYear,2,3,NOT_NEGATIVE)"},
            {"DDD", "Value(DayOfYear,3)"},

            {"d", "Value(DayOfMonth)"},
            {"dd", "Value(DayOfMonth,2)"},

            {"F", "Value(AlignedDayOfWeekInMonth)"},

            {"Q", "Value(QuarterOfYear)"},
            {"QQ", "Value(QuarterOfYear,2)"},
            {"QQQ", "Text(QuarterOfYear,SHORT)"},
            {"QQQQ", "Text(QuarterOfYear)"},
            {"QQQQQ", "Text(QuarterOfYear,NARROW)"},

            {"q", "Value(QuarterOfYear)"},
            {"qq", "Value(QuarterOfYear,2)"},
            {"qqq", "Text(QuarterOfYear,SHORT_STANDALONE)"},
            {"qqqq", "Text(QuarterOfYear,FULL_STANDALONE)"},
            {"qqqqq", "Text(QuarterOfYear,NARROW_STANDALONE)"},

            {"E", "Text(DayOfWeek,SHORT)"},
            {"EE", "Text(DayOfWeek,SHORT)"},
            {"EEE", "Text(DayOfWeek,SHORT)"},
            {"EEEE", "Text(DayOfWeek)"},
            {"EEEEE", "Text(DayOfWeek,NARROW)"},

            {"e", "Localized(DayOfWeek,1)"},
            {"ee", "Localized(DayOfWeek,2)"},
            {"eee", "Text(DayOfWeek,SHORT)"},
            {"eeee", "Text(DayOfWeek)"},
            {"eeeee", "Text(DayOfWeek,NARROW)"},

            {"c", "Localized(DayOfWeek,1)"},
            {"ccc", "Text(DayOfWeek,SHORT_STANDALONE)"},
            {"cccc", "Text(DayOfWeek,FULL_STANDALONE)"},
            {"ccccc", "Text(DayOfWeek,NARROW_STANDALONE)"},

            {"a", "Text(AmPmOfDay,SHORT)"},

            {"H", "Value(HourOfDay)"},
            {"HH", "Value(HourOfDay,2)"},

            {"K", "Value(HourOfAmPm)"},
            {"KK", "Value(HourOfAmPm,2)"},

            {"k", "Value(ClockHourOfDay)"},
            {"kk", "Value(ClockHourOfDay,2)"},

            {"h", "Value(ClockHourOfAmPm)"},
            {"hh", "Value(ClockHourOfAmPm,2)"},

            {"m", "Value(MinuteOfHour)"},
            {"mm", "Value(MinuteOfHour,2)"},

            {"s", "Value(SecondOfMinute)"},
            {"ss", "Value(SecondOfMinute,2)"},

            {"S", "Fraction(NanoOfSecond,1,1)"},
            {"SS", "Fraction(NanoOfSecond,2,2)"},
            {"SSS", "Fraction(NanoOfSecond,3,3)"},
            {"SSSSSSSSS", "Fraction(NanoOfSecond,9,9)"},

            {"A", "Value(MilliOfDay,1,19,NOT_NEGATIVE)"},
            {"AA", "Value(MilliOfDay,2,19,NOT_NEGATIVE)"},
            {"AAA", "Value(MilliOfDay,3,19,NOT_NEGATIVE)"},

            {"n", "Value(NanoOfSecond,1,19,NOT_NEGATIVE)"},
            {"nn", "Value(NanoOfSecond,2,19,NOT_NEGATIVE)"},
            {"nnn", "Value(NanoOfSecond,3,19,NOT_NEGATIVE)"},

            {"N", "Value(NanoOfDay,1,19,NOT_NEGATIVE)"},
            {"NN", "Value(NanoOfDay,2,19,NOT_NEGATIVE)"},
            {"NNN", "Value(NanoOfDay,3,19,NOT_NEGATIVE)"},

            {"z", "ZoneText(SHORT)"},
            {"zz", "ZoneText(SHORT)"},
            {"zzz", "ZoneText(SHORT)"},
            {"zzzz", "ZoneText(FULL)"},

            {"VV", "ZoneId()"},

            {"Z", "Offset(+HHMM,'+0000')"},  // SimpleDateFormat
            {"ZZ", "Offset(+HHMM,'+0000')"},  // SimpleDateFormat
            {"ZZZ", "Offset(+HHMM,'+0000')"},  // SimpleDateFormat

            {"X", "Offset(+HHmm,'Z')"},  // LDML/almost SimpleDateFormat
            {"XX", "Offset(+HHMM,'Z')"},  // LDML/SimpleDateFormat
            {"XXX", "Offset(+HH:MM,'Z')"},  // LDML/SimpleDateFormat
            {"XXXX", "Offset(+HHMMss,'Z')"},  // LDML
            {"XXXXX", "Offset(+HH:MM:ss,'Z')"},  // LDML

            {"x", "Offset(+HHmm,'+00')"},  // LDML
            {"xx", "Offset(+HHMM,'+0000')"},  // LDML
            {"xxx", "Offset(+HH:MM,'+00:00')"},  // LDML
            {"xxxx", "Offset(+HHMMss,'+0000')"},  // LDML
            {"xxxxx", "Offset(+HH:MM:ss,'+00:00')"},  // LDML

            {"ppH", "Pad(Value(HourOfDay),2)"},
            {"pppDD", "Pad(Value(DayOfYear,2,3,NOT_NEGATIVE),3)"},

            {"yyyy[-MM[-dd", "Value(YearOfEra,4,19,EXCEEDS_PAD)['-'Value(MonthOfYear,2)['-'Value(DayOfMonth,2)]]"},
            {"yyyy[-MM[-dd]]", "Value(YearOfEra,4,19,EXCEEDS_PAD)['-'Value(MonthOfYear,2)['-'Value(DayOfMonth,2)]]"},
            {"yyyy[-MM[]-dd]", "Value(YearOfEra,4,19,EXCEEDS_PAD)['-'Value(MonthOfYear,2)'-'Value(DayOfMonth,2)]"},

            {"yyyy-MM-dd'T'HH:mm:ss.SSS", "Value(YearOfEra,4,19,EXCEEDS_PAD)'-'Value(MonthOfYear,2)'-'Value(DayOfMonth,2)" +
                "'T'Value(HourOfDay,2)':'Value(MinuteOfHour,2)':'Value(SecondOfMinute,2)'.'Fraction(NanoOfSecond,3,3)"},

            {"w", "Localized(WeekOfWeekBasedYear,1)"},
            {"ww", "Localized(WeekOfWeekBasedYear,2)"},
            {"W", "Localized(WeekOfMonth,1)"},
        };
    }

    @Test(dataProvider="validPatterns")
    public void test_appendPattern_valid(String input, String expected) throws Exception {
        builder.appendPattern(input);
        DateTimeFormatter f = builder.toFormatter();
        assertEquals(f.toString(), expected);
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
            {"LLLLLL"},
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
            {"ZZZZZZ"},
            {"zzzzz"},
            {"V"},
            {"VVV"},
            {"VVVV"},
            {"VVVVV"},

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
        try {
            builder.appendPattern(input);
        } catch (IllegalArgumentException ex) {
            throw ex;
        }
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="localePatterns")
    Object[][] localizedDateTimePatterns() {
        return new Object[][] {
            {FormatStyle.FULL, FormatStyle.FULL, IsoChronology.INSTANCE, Locale.US, "EEEE, MMMM d, y 'at' h:mm:ss a zzzz"},
            {FormatStyle.LONG, FormatStyle.LONG, IsoChronology.INSTANCE, Locale.US, "MMMM d, y 'at' h:mm:ss a z"},
            {FormatStyle.MEDIUM, FormatStyle.MEDIUM, IsoChronology.INSTANCE, Locale.US, "MMM d, y, h:mm:ss a"},
            {FormatStyle.SHORT, FormatStyle.SHORT, IsoChronology.INSTANCE, Locale.US, "M/d/yy, h:mm a"},
            {FormatStyle.FULL, null, IsoChronology.INSTANCE, Locale.US, "EEEE, MMMM d, y"},
            {FormatStyle.LONG, null, IsoChronology.INSTANCE, Locale.US, "MMMM d, y"},
            {FormatStyle.MEDIUM, null, IsoChronology.INSTANCE, Locale.US, "MMM d, y"},
            {FormatStyle.SHORT, null, IsoChronology.INSTANCE, Locale.US, "M/d/yy"},
            {null, FormatStyle.FULL, IsoChronology.INSTANCE, Locale.US, "h:mm:ss a zzzz"},
            {null, FormatStyle.LONG, IsoChronology.INSTANCE, Locale.US, "h:mm:ss a z"},
            {null, FormatStyle.MEDIUM, IsoChronology.INSTANCE, Locale.US, "h:mm:ss a"},
            {null, FormatStyle.SHORT, IsoChronology.INSTANCE, Locale.US, "h:mm a"},
        };
    }

    @Test(dataProvider="localePatterns")
    public void test_getLocalizedDateTimePattern(FormatStyle dateStyle, FormatStyle timeStyle,
            Chronology chrono, Locale locale, String expected) {
        String actual = DateTimeFormatterBuilder.getLocalizedDateTimePattern(dateStyle, timeStyle, chrono, locale);
        assertEquals(actual, expected, "Pattern " + convertNonAscii(actual));
    }

    @Test(expectedExceptions=java.lang.IllegalArgumentException.class)
    public void test_getLocalizedDateTimePatternIAE() {
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, null, IsoChronology.INSTANCE, Locale.US);
    }

    @Test(expectedExceptions=java.lang.NullPointerException.class)
    public void test_getLocalizedChronoNPE() {
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, FormatStyle.SHORT, null, Locale.US);
    }

    @Test(expectedExceptions=java.lang.NullPointerException.class)
    public void test_getLocalizedLocaleNPE() {
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, FormatStyle.SHORT, IsoChronology.INSTANCE, null);
    }

    /**
     * Returns a string that includes non-ascii characters after expanding
     * the non-ascii characters to their Java language \\uxxxx form.
     * @param input an input string
     * @return the encoded string.
     */
    private String convertNonAscii(String input) {
        StringBuilder sb = new StringBuilder(input.length() * 6);
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch < 255) {
                sb.append(ch);
            } else {
                sb.append("\\u");
                sb.append(Integer.toHexString(ch));
            }
        }
        return sb.toString();
    }

    private static Temporal date(int y, int m, int d) {
        return LocalDate.of(y, m, d);
    }
}
