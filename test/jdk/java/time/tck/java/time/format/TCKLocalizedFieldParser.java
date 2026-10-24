/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * Copyright (c) 2010-2012, Stephen Colebourne & Michael Nascimento Santos
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

import static java.time.temporal.ChronoField.YEAR_OF_ERA;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.ParsePosition;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import test.java.time.format.AbstractTestPrinterParser;

/**
 * Test TCKLocalizedFieldParser.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TCKLocalizedFieldParser extends AbstractTestPrinterParser {
    public static final WeekFields WEEKDEF = WeekFields.of(Locale.US);
    public static final TemporalField WEEK_BASED_YEAR = WEEKDEF.weekBasedYear();
    public static final TemporalField WEEK_OF_WEEK_BASED_YEAR = WEEKDEF.weekOfWeekBasedYear();
    public static final TemporalField DAY_OF_WEEK = WEEKDEF.dayOfWeek();
    //-----------------------------------------------------------------------
    Object[][] provider_fieldPatterns() {
        return new Object[][] {
            {"e", "6", 0, 1, 6},
            {"ee", "06", 0, 2, 6},
            {"c",  "6", 0, 1 , 6},
            {"W",  "3", 0, 1, 3},
            {"w",  "29", 0, 2, 29},
            {"ww", "29", 0, 2, 29},
            {"Y", "2013", 0, 4, 2013},
            {"YY", "13", 0, 2, 2013},
            {"YYYY", "2013", 0, 4, 2013},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_fieldPatterns")
    public void test_parse_textField(String pattern, String text, int pos, int expectedPos, long expectedValue) {
        WeekFields weekDef = WeekFields.of(locale);
        TemporalField field = null;
        switch(pattern.charAt(0)) {
            case 'c' :
            case 'e' :
                field = weekDef.dayOfWeek();
                break;
            case 'w':
                field = weekDef.weekOfWeekBasedYear();
                break;
            case 'W':
                field = weekDef.weekOfMonth();
                break;
            case 'Y':
                field = weekDef.weekBasedYear();
                break;
            default:
                throw new IllegalStateException("bad format letter from pattern");
        }
        ParsePosition ppos = new ParsePosition(pos);
        DateTimeFormatterBuilder b
                = new DateTimeFormatterBuilder().appendPattern(pattern);
        DateTimeFormatter dtf = b.toFormatter(locale);
        TemporalAccessor parsed = dtf.parseUnresolved(text, ppos);
        if (ppos.getErrorIndex() != -1) {
            assertEquals(expectedPos, ppos.getErrorIndex());
        } else {
            assertEquals(expectedPos, ppos.getIndex(), "Incorrect ending parse position");
            long value = parsed.getLong(field);
            assertEquals(expectedValue, value, "Value incorrect for " + field);
        }
    }

    //-----------------------------------------------------------------------
    Object[][] provider_patternLocalDate() {
        return new Object[][] {
            {"e W M y",  "1 1 1 2012", 0, 10, LocalDate.of(2012, 1, 1)},
            {"e W M y",  "1 2 1 2012", 0, 10, LocalDate.of(2012, 1, 8)},
            {"e W M y",  "2 2 1 2012", 0, 10, LocalDate.of(2012, 1, 9)},
            {"e W M y",  "3 2 1 2012", 0, 10, LocalDate.of(2012, 1, 10)},
            {"e W M y",  "1 3 1 2012", 0, 10, LocalDate.of(2012, 1, 15)},
            {"e W M y",  "2 3 1 2012", 0, 10, LocalDate.of(2012, 1, 16)},
            {"e W M y",  "6 2 1 2012", 0, 10, LocalDate.of(2012, 1, 13)},
            {"e W M y",  "6 2 7 2012", 0, 10, LocalDate.of(2012, 7, 13)},
            {"'Date: 'y-MM', day-of-week: 'e', week-of-month: 'W",
                "Date: 2012-07, day-of-week: 6, week-of-month: 3", 0, 47, LocalDate.of(2012, 7, 20)},
        };
    }

   @ParameterizedTest
    @MethodSource("provider_patternLocalDate")
    public void test_parse_textLocalDate(String pattern, String text, int pos, int expectedPos, LocalDate expectedValue) {
        ParsePosition ppos = new ParsePosition(pos);
        DateTimeFormatterBuilder b = new DateTimeFormatterBuilder().appendPattern(pattern);
        DateTimeFormatter dtf = b.toFormatter(locale);
        TemporalAccessor parsed = dtf.parseUnresolved(text, ppos);
        if (ppos.getErrorIndex() != -1) {
            assertEquals(expectedPos, ppos.getErrorIndex());
        } else {
            assertEquals(expectedPos, ppos.getIndex(), "Incorrect ending parse position");
            assertEquals(true, parsed.isSupported(YEAR_OF_ERA));
            assertEquals(true, parsed.isSupported(WeekFields.of(locale).dayOfWeek()));
            assertEquals(true, parsed.isSupported(WeekFields.of(locale).weekOfMonth()) ||
                    parsed.isSupported(WeekFields.of(locale).weekOfYear()));
            // ensure combination resolves into a date
            LocalDate result = LocalDate.parse(text, dtf);
            assertEquals(expectedValue, result, "LocalDate incorrect for " + pattern);
        }
    }

    //-----------------------------------------------------------------------
    Object[][] provider_patternLocalWeekBasedYearDate() {
        return new Object[][] {
            //{"w Y",  "29 2012", 0, 7, LocalDate.of(2012, 7, 20)},  // Default lenient dayOfWeek not supported
            {"e w Y",  "6 29 2012", 0, 9, LocalDate.of(2012, 7, 20)},
            {"'Date: 'Y', day-of-week: 'e', week-of-year: 'w",
                "Date: 2012, day-of-week: 6, week-of-year: 29", 0, 44, LocalDate.of(2012, 7, 20)},
            {"Y-w-e",  "2008-01-1", 0, 9, LocalDate.of(2007, 12, 30)},
            {"Y-w-e",  "2008-52-1", 0, 9, LocalDate.of(2008, 12, 21)},
            {"Y-w-e",  "2008-52-7", 0, 9, LocalDate.of(2008, 12, 27)},
            {"Y-w-e",  "2009-01-1", 0, 9, LocalDate.of(2008, 12, 28)},
            {"Y-w-e",  "2009-01-4", 0, 9, LocalDate.of(2008, 12, 31)},
            {"Y-w-e",  "2009-01-5", 0, 9, LocalDate.of(2009, 1, 1)},
       };
    }

   @ParameterizedTest
    @MethodSource("provider_patternLocalWeekBasedYearDate")
    public void test_parse_WeekBasedYear(String pattern, String text, int pos, int expectedPos, LocalDate expectedValue) {
        ParsePosition ppos = new ParsePosition(pos);
        DateTimeFormatterBuilder b = new DateTimeFormatterBuilder().appendPattern(pattern);
        DateTimeFormatter dtf = b.toFormatter(locale);
        TemporalAccessor parsed = dtf.parseUnresolved(text, ppos);
        if (ppos.getErrorIndex() != -1) {
            assertEquals(expectedPos, ppos.getErrorIndex());
        } else {
            WeekFields weekDef = WeekFields.of(locale);
            assertEquals(expectedPos, ppos.getIndex(), "Incorrect ending parse position");
            assertEquals(pattern.indexOf('e') >= 0, parsed.isSupported(weekDef.dayOfWeek()));
            assertEquals(pattern.indexOf('w') >= 0, parsed.isSupported(weekDef.weekOfWeekBasedYear()));
            assertEquals(pattern.indexOf('Y') >= 0, parsed.isSupported(weekDef.weekBasedYear()));
            // ensure combination resolves into a date
            LocalDate result = LocalDate.parse(text, dtf);
            assertEquals(expectedValue, result, "LocalDate incorrect for " + pattern + ", weekDef: " + weekDef);
        }
    }

    //-----------------------------------------------------------------------
    Object[][] provider_adjacentValuePatterns1() {
        return new Object[][] {
                {"YYww", WEEK_BASED_YEAR, WEEK_OF_WEEK_BASED_YEAR, "1612", 2016, 12},
                {"YYYYww", WEEK_BASED_YEAR, WEEK_OF_WEEK_BASED_YEAR, "201612", 2016, 12},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_adjacentValuePatterns1")
    public void test_adjacentValuePatterns1(String pattern, TemporalField field1, TemporalField field2,
            String text, int expected1, int expected2) {
        DateTimeFormatter df = new DateTimeFormatterBuilder()
                .appendPattern(pattern).toFormatter(Locale.US);
        ParsePosition ppos = new ParsePosition(0);
        TemporalAccessor parsed = df.parseUnresolved(text, ppos);
        assertEquals(expected1, parsed.get(field1));
        assertEquals(expected2, parsed.get(field2));
    }

    Object[][] provider_adjacentValuePatterns2() {
        return new Object[][] {
                {"YYYYwwc", WEEK_BASED_YEAR, WEEK_OF_WEEK_BASED_YEAR, DAY_OF_WEEK,
                        "2016121", 2016, 12, 1},
                {"YYYYwwee", WEEK_BASED_YEAR, WEEK_OF_WEEK_BASED_YEAR, DAY_OF_WEEK,
                        "20161201", 2016, 12, 1},
                {"YYYYwwe", WEEK_BASED_YEAR, WEEK_OF_WEEK_BASED_YEAR, DAY_OF_WEEK,
                        "2016121", 2016, 12, 1},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_adjacentValuePatterns2")
    public void test_adjacentValuePatterns2(String pattern, TemporalField field1, TemporalField field2,
            TemporalField field3, String text, int expected1, int expected2, int expected3) {
        DateTimeFormatter df = new DateTimeFormatterBuilder()
                .appendPattern(pattern).toFormatter(Locale.US);
        ParsePosition ppos = new ParsePosition(0);
        TemporalAccessor parsed = df.parseUnresolved(text, ppos);
        assertEquals(expected1, parsed.get(field1));
        assertEquals(expected2, parsed.get(field2));
        assertEquals(expected3, parsed.get(field3));
    }

    @Test
    public void test_adjacentValuePatterns3() {
        String pattern = "yyyyMMddwwc";
        String text =  "20120720296";
        DateTimeFormatter df = new DateTimeFormatterBuilder()
                .appendPattern(pattern).toFormatter(Locale.US);
        ParsePosition ppos = new ParsePosition(0);
        TemporalAccessor parsed = df.parseUnresolved(text, ppos);
        assertEquals(6, parsed.get(DAY_OF_WEEK));
        assertEquals(29, parsed.get(WEEK_OF_WEEK_BASED_YEAR));
        LocalDate result = LocalDate.parse(text, df);
        LocalDate expectedValue = LocalDate.of(2012, 07, 20);
        assertEquals(expectedValue, result, "LocalDate incorrect for " + pattern);
    }

    Object[][] provider_invalidPatterns() {
        return new Object[][] {
            {"W", "01"},
            {"c", "01"},
            {"e", "01"},
            {"yyyyMMddwwc", "201207202906"}, //  1 extra digit in the input
        };
    }

    @ParameterizedTest
    @MethodSource("provider_invalidPatterns")
    public void test_invalidPatterns(String pattern, String value) {
        Assertions.assertThrows(DateTimeParseException.class, () -> DateTimeFormatter.ofPattern(pattern).parse(value));
    }
}
