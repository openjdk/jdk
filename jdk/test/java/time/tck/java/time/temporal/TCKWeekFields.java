/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Copyright (c) 2008-2012, Stephen Colebourne & Michael Nascimento Santos
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
package tck.java.time.temporal;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.DAY_OF_YEAR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.TemporalField;
import java.time.temporal.ValueRange;
import java.time.temporal.WeekFields;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tck.java.time.AbstractTCKTest;

/**
 * Test WeekFields.
 */
@Test
public class TCKWeekFields extends AbstractTCKTest {

    @Test(dataProvider="weekFields")
    public void test_of_DayOfWeek_int_singleton(DayOfWeek firstDayOfWeek, int minDays) {
        WeekFields week = WeekFields.of(firstDayOfWeek, minDays);
        assertEquals(week.getFirstDayOfWeek(), firstDayOfWeek, "Incorrect firstDayOfWeek");
        assertEquals(week.getMinimalDaysInFirstWeek(), minDays, "Incorrect MinimalDaysInFirstWeek");
        assertSame(WeekFields.of(firstDayOfWeek, minDays), week);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_dayOfWeekField_simpleGet() {
        LocalDate date = LocalDate.of(2000, 1, 10);  // Known to be ISO Monday
        assertEquals(date.get(WeekFields.ISO.dayOfWeek()), 1);
        assertEquals(date.get(WeekFields.of(DayOfWeek.MONDAY, 1).dayOfWeek()), 1);
        assertEquals(date.get(WeekFields.of(DayOfWeek.MONDAY, 7).dayOfWeek()), 1);
        assertEquals(date.get(WeekFields.SUNDAY_START.dayOfWeek()), 2);
        assertEquals(date.get(WeekFields.of(DayOfWeek.SUNDAY, 1).dayOfWeek()), 2);
        assertEquals(date.get(WeekFields.of(DayOfWeek.SUNDAY, 7).dayOfWeek()), 2);
        assertEquals(date.get(WeekFields.of(DayOfWeek.SATURDAY, 1).dayOfWeek()), 3);
        assertEquals(date.get(WeekFields.of(DayOfWeek.FRIDAY, 1).dayOfWeek()), 4);
        assertEquals(date.get(WeekFields.of(DayOfWeek.TUESDAY, 1).dayOfWeek()), 7);
    }

    @Test
    public void test_dayOfWeekField_simpleSet() {
        LocalDate date = LocalDate.of(2000, 1, 10);  // Known to be ISO Monday
        assertEquals(date.with(WeekFields.ISO.dayOfWeek(), 2), LocalDate.of(2000, 1, 11));
        assertEquals(date.with(WeekFields.ISO.dayOfWeek(), 7), LocalDate.of(2000, 1, 16));

        assertEquals(date.with(WeekFields.SUNDAY_START.dayOfWeek(), 3), LocalDate.of(2000, 1, 11));
        assertEquals(date.with(WeekFields.SUNDAY_START.dayOfWeek(), 7), LocalDate.of(2000, 1, 15));

        assertEquals(date.with(WeekFields.of(DayOfWeek.SATURDAY, 1).dayOfWeek(), 4), LocalDate.of(2000, 1, 11));
        assertEquals(date.with(WeekFields.of(DayOfWeek.TUESDAY, 1).dayOfWeek(), 1), LocalDate.of(2000, 1, 4));
    }

    @Test(dataProvider="weekFields")
    public void test_dayOfWeekField(DayOfWeek firstDayOfWeek, int minDays) {
        LocalDate day = LocalDate.of(2000, 1, 10);  // Known to be ISO Monday
        WeekFields week = WeekFields.of(firstDayOfWeek, minDays);
        TemporalField f = week.dayOfWeek();
        //System.out.printf("  Week: %s; field: %s%n", week, f);

        for (int i = 1; i <= 7; i++) {
            //System.out.printf("  ISO Dow: %s, WeekDOW ordinal: %s%n", day.getDayOfWeek(), day.get(f));
            assertEquals(day.get(f), (7 + day.getDayOfWeek().getValue() - firstDayOfWeek.getValue()) % 7 + 1);
            day = day.plusDays(1);
        }
    }

    @Test(dataProvider="weekFields")
    public void test_weekOfMonthField(DayOfWeek firstDayOfWeek, int minDays) {
        LocalDate day = LocalDate.of(2012, 12, 31);  // Known to be ISO Monday
        WeekFields week = WeekFields.of(firstDayOfWeek, minDays);
        TemporalField dowField = week.dayOfWeek();
        TemporalField womField = week.weekOfMonth();
        //System.err.printf("%n  Week: %s; dowField: %s, domField: %s%n", week, dowField, womField);

        DayOfWeek isoDOW = day.getDayOfWeek();
        int dow = (7 + isoDOW.getValue() - firstDayOfWeek.getValue()) % 7 + 1;

        for (int i = 1; i <= 15; i++) {
            int actualDOW = day.get(dowField);
            int actualWOM = day.get(womField);

            // Verify that the combination of day of week and week of month can be used
            // to reconstruct the same date.
            LocalDate day1 = day.withDayOfMonth(1);
            int offset = - (day1.get(dowField) - 1);
            //System.err.printf("   refDay: %s%n", day1.plusDays(offset));
            int week1 = day1.get(womField);
            if (week1 == 0) {
                // week of the 1st is partial; start with first full week
                offset += 7;
            }
            //System.err.printf("   refDay2: %s, offset: %d, week1: %d%n", day1.plusDays(offset), offset, week1);
            offset += actualDOW - 1;
            //System.err.printf("   refDay3: %s%n", day1.plusDays(offset));
            offset += (actualWOM - 1) * 7;
            //System.err.printf("   refDay4: %s%n", day1.plusDays(offset));
            LocalDate result = day1.plusDays(offset);

            if (!day.equals(result)) {
                System.err.printf("FAIL ISO Dow: %s, offset: %s, actualDOW: %s, actualWOM: %s, expected: %s, result: %s%n",
                        day.getDayOfWeek(), offset, actualDOW, actualWOM, day, result);
            }
            assertEquals(result, day, "Incorrect dayOfWeek or weekOfMonth: "
                    + String.format("%s, ISO Dow: %s, offset: %s, actualDOW: %s, actualWOM: %s, expected: %s, result: %s%n",
                    week, day.getDayOfWeek(), offset, actualDOW, actualWOM, day, result));
            day = day.plusDays(1);
        }
    }

    @Test(dataProvider="weekFields")
    public void test_weekOfYearField(DayOfWeek firstDayOfWeek, int minDays) {
        LocalDate day = LocalDate.of(2012, 12, 31);  // Known to be ISO Monday
        WeekFields week = WeekFields.of(firstDayOfWeek, minDays);
        TemporalField dowField = week.dayOfWeek();
        TemporalField woyField = week.weekOfYear();
        //System.err.printf("%n  Year: %s; dowField: %s, woyField: %s%n", week, dowField, woyField);

        DayOfWeek isoDOW = day.getDayOfWeek();
        int dow = (7 + isoDOW.getValue() - firstDayOfWeek.getValue()) % 7 + 1;

        for (int i = 1; i <= 15; i++) {
            int actualDOW = day.get(dowField);
            int actualWOY = day.get(woyField);

            // Verify that the combination of day of week and week of month can be used
            // to reconstruct the same date.
            LocalDate day1 = day.withDayOfYear(1);
            int offset = - (day1.get(dowField) - 1);
            //System.err.printf("   refDay: %s%n", day1.plusDays(offset));
            int week1 = day1.get(woyField);
            if (week1 == 0) {
                // week of the 1st is partial; start with first full week
                offset += 7;
            }
            //System.err.printf("   refDay2: %s, offset: %d, week1: %d%n", day1.plusDays(offset), offset, week1);
            offset += actualDOW - 1;
            //System.err.printf("   refDay3: %s%n", day1.plusDays(offset));
            offset += (actualWOY - 1) * 7;
            //System.err.printf("   refDay4: %s%n", day1.plusDays(offset));
            LocalDate result = day1.plusDays(offset);


            if (!day.equals(result)) {
                System.err.printf("FAIL  ISO Dow: %s, offset: %s, actualDOW: %s, actualWOY: %s, expected: %s, result: %s%n",
                        day.getDayOfWeek(), offset, actualDOW, actualWOY, day, result);
            }
            assertEquals(result, day, "Incorrect dayOfWeek or weekOfYear "
                    + String.format("%s, ISO Dow: %s, offset: %s, actualDOW: %s, actualWOM: %s, expected: %s, result: %s%n",
                    week, day.getDayOfWeek(), offset, actualDOW, actualWOY, day, result));
            day = day.plusDays(1);
        }
    }

    @Test(dataProvider="weekFields")
    public void test_fieldRanges(DayOfWeek firstDayOfWeek, int minDays) {
        WeekFields weekDef = WeekFields.of(firstDayOfWeek, minDays);
        TemporalField womField = weekDef.weekOfMonth();
        TemporalField woyField = weekDef.weekOfYear();

        LocalDate day = LocalDate.of(2012, 11, 30);
        LocalDate endDay = LocalDate.of(2013, 1, 2);
        while (day.isBefore(endDay)) {
            LocalDate last = day.with(DAY_OF_MONTH, day.lengthOfMonth());
            int lastWOM = last.get(womField);
            LocalDate first = day.with(DAY_OF_MONTH, 1);
            int firstWOM = first.get(womField);
            ValueRange rangeWOM = day.range(womField);
            assertEquals(rangeWOM.getMinimum(), firstWOM,
                    "Range min should be same as WeekOfMonth for first day of month: "
                    + first + ", " + weekDef);
            assertEquals(rangeWOM.getMaximum(), lastWOM,
                    "Range max should be same as WeekOfMonth for last day of month: "
                    + last + ", " + weekDef);

            last = day.with(DAY_OF_YEAR, day.lengthOfYear());
            int lastWOY = last.get(woyField);
            first = day.with(DAY_OF_YEAR, 1);
            int firstWOY = first.get(woyField);
            ValueRange rangeWOY = day.range(woyField);
            assertEquals(rangeWOY.getMinimum(), firstWOY,
                    "Range min should be same as WeekOfYear for first day of Year: "
                    + day + ", " + weekDef);
            assertEquals(rangeWOY.getMaximum(), lastWOY,
                    "Range max should be same as WeekOfYear for last day of Year: "
                    + day + ", " + weekDef);

            day = day.plusDays(1);
        }
    }

    //-----------------------------------------------------------------------
    // withDayOfWeek()
    //-----------------------------------------------------------------------
    @Test(dataProvider="weekFields")
    public void test_withDayOfWeek(DayOfWeek firstDayOfWeek, int minDays) {
        LocalDate day = LocalDate.of(2012, 12, 15);  // Safely in the middle of a month
        WeekFields week = WeekFields.of(firstDayOfWeek, minDays);
        TemporalField dowField = week.dayOfWeek();
        TemporalField womField = week.weekOfMonth();
        TemporalField woyField = week.weekOfYear();

        int wom = day.get(womField);
        int woy = day.get(woyField);
        for (int dow = 1; dow <= 7; dow++) {
            LocalDate result = day.with(dowField, dow);
            if (result.get(dowField) != dow) {
                System.err.printf(" DOW actual: %d, expected: %d, week:%s%n",
                        result.get(dowField), dow, week);
            }
            if (result.get(womField) != wom) {
                System.err.printf(" WOM actual: %d, expected: %d, week:%s%n",
                        result.get(womField), wom, week);
            }
            if (result.get(woyField) != woy) {
                System.err.printf(" WOY actual: %d, expected: %d, week:%s%n",
                        result.get(woyField), woy, week);
            }
            assertEquals(result.get(dowField), dow, String.format("Incorrect new Day of week: %s", result));
            assertEquals(result.get(womField), wom, "Week of Month should not change");
            assertEquals(result.get(woyField), woy, "Week of Year should not change");
        }
    }

    //-----------------------------------------------------------------------
    @Test(dataProvider="weekFields")
    public void test_parse_resolve_localizedWom(DayOfWeek firstDayOfWeek, int minDays) {
        LocalDate date = LocalDate.of(2012, 12, 15);
        WeekFields week = WeekFields.of(firstDayOfWeek, minDays);
        TemporalField womField = week.weekOfMonth();

        for (int i = 1; i <= 60; i++) {
            // Test that with dayOfWeek and Week of month it computes the date
            DateTimeFormatter f = new DateTimeFormatterBuilder()
                    .appendValue(YEAR).appendLiteral('-')
                    .appendValue(MONTH_OF_YEAR).appendLiteral('-')
                    .appendValue(womField).appendLiteral('-')
                    .appendValue(DAY_OF_WEEK).toFormatter();
            String str = date.getYear() + "-" + date.getMonthValue() + "-" +
                    date.get(womField) + "-" + date.get(DAY_OF_WEEK);
            LocalDate parsed = LocalDate.parse(str, f);
            assertEquals(parsed, date, " ::" + str + "::" + i);

            date = date.plusDays(1);
        }
    }

    @Test(dataProvider="weekFields")
    public void test_parse_resolve_localizedWomDow(DayOfWeek firstDayOfWeek, int minDays) {
        LocalDate date = LocalDate.of(2012, 12, 15);
        WeekFields week = WeekFields.of(firstDayOfWeek, minDays);
        TemporalField dowField = week.dayOfWeek();
        TemporalField womField = week.weekOfMonth();

        for (int i = 1; i <= 15; i++) {
            // Test that with dayOfWeek and Week of month it computes the date
            DateTimeFormatter f = new DateTimeFormatterBuilder()
                    .appendValue(YEAR).appendLiteral('-')
                    .appendValue(MONTH_OF_YEAR).appendLiteral('-')
                    .appendValue(womField).appendLiteral('-')
                    .appendValue(dowField).toFormatter();
            String str = date.getYear() + "-" + date.getMonthValue() + "-" +
                    date.get(womField) + "-" + date.get(dowField);
            LocalDate parsed = LocalDate.parse(str, f);
            assertEquals(parsed, date, " :: " + str + " " + i);

            date = date.plusDays(1);
        }
    }

    @Test(dataProvider="weekFields")
    public void test_parse_resolve_localizedWoy(DayOfWeek firstDayOfWeek, int minDays) {
        LocalDate date = LocalDate.of(2012, 12, 15);
        WeekFields week = WeekFields.of(firstDayOfWeek, minDays);
        TemporalField woyField = week.weekOfYear();

        for (int i = 1; i <= 60; i++) {
            // Test that with dayOfWeek and Week of month it computes the date
            DateTimeFormatter f = new DateTimeFormatterBuilder()
                    .appendValue(YEAR).appendLiteral('-')
                    .appendValue(MONTH_OF_YEAR).appendLiteral('-')
                    .appendValue(woyField).appendLiteral('-')
                    .appendValue(DAY_OF_WEEK).toFormatter();
            String str = date.getYear() + "-" + date.getMonthValue() + "-" +
                    date.get(woyField) + "-" + date.get(DAY_OF_WEEK);
            LocalDate parsed = LocalDate.parse(str, f);
            assertEquals(parsed, date, " :: " + str + " " + i);

            date = date.plusDays(1);
        }
    }

    @Test(dataProvider="weekFields")
    public void test_parse_resolve_localizedWoyDow(DayOfWeek firstDayOfWeek, int minDays) {
        LocalDate date = LocalDate.of(2012, 12, 15);
        WeekFields week = WeekFields.of(firstDayOfWeek, minDays);
        TemporalField dowField = week.dayOfWeek();
        TemporalField woyField = week.weekOfYear();

        for (int i = 1; i <= 60; i++) {
            // Test that with dayOfWeek and Week of month it computes the date
            DateTimeFormatter f = new DateTimeFormatterBuilder()
                    .appendValue(YEAR).appendLiteral('-')
                    .appendValue(MONTH_OF_YEAR).appendLiteral('-')
                    .appendValue(woyField).appendLiteral('-')
                    .appendValue(dowField).toFormatter();
            String str = date.getYear() + "-" + date.getMonthValue() + "-" +
                    date.get(woyField) + "-" + date.get(dowField);
            LocalDate parsed = LocalDate.parse(str, f);
            assertEquals(parsed, date, " :: " + str + " " + i);

            date = date.plusDays(1);
        }
    }

    //-----------------------------------------------------------------------
    @Test(dataProvider="weekFields")
    public void test_serializable_singleton(DayOfWeek firstDayOfWeek, int minDays) throws IOException, ClassNotFoundException {
        WeekFields weekDef = WeekFields.of(firstDayOfWeek, minDays);
        assertSerializableSame(weekDef);  // spec state singleton
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="weekFields")
    Object[][] data_weekFields() {
        Object[][] objects = new Object[49][];
        int i = 0;
        for (DayOfWeek firstDayOfWeek : DayOfWeek.values()) {
            for (int minDays = 1; minDays <= 7; minDays++) {
                objects[i++] = new Object[] {firstDayOfWeek, minDays};
            }
        }
        return objects;
    }

}
