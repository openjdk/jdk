/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
package test.java.time.temporal;

import static java.time.temporal.ChronoField.DAY_OF_WEEK;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.chrono.HijrahDate;
import java.time.chrono.ThaiBuddhistDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalField;
import java.time.temporal.ValueRange;
import java.time.temporal.WeekFields;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestIsoWeekFields {

    Object[][] data_Fields() {
        return new Object[][] {
                {IsoFields.WEEK_OF_WEEK_BASED_YEAR, IsoFields.WEEK_BASED_YEAR},
                {WeekFields.ISO.weekOfWeekBasedYear(), WeekFields.ISO.weekBasedYear()},
        };
    }

    //-----------------------------------------------------------------------
    // WEEK_OF_WEEK_BASED_YEAR
    //-----------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("data_Fields")
    public void test_WOWBY_basics(TemporalField weekField, TemporalField yearField) {
        assertEquals(true, weekField.isDateBased());
        assertEquals(false, weekField.isTimeBased());
        assertEquals(ChronoUnit.WEEKS, weekField.getBaseUnit());
        assertEquals(IsoFields.WEEK_BASED_YEARS, weekField.getRangeUnit());
    }

    @ParameterizedTest
    @MethodSource("data_Fields")
    public void test_WOWBY_isSupportedBy(TemporalField weekField, TemporalField yearField) {
        assertEquals(false, weekField.isSupportedBy(LocalTime.NOON));
        assertEquals(false, weekField.isSupportedBy(MonthDay.of(2, 1)));
        assertEquals(true, weekField.isSupportedBy(LocalDate.MIN));
        assertEquals(true, weekField.isSupportedBy(OffsetDateTime.MAX));
        assertEquals(true, weekField.isSupportedBy(ThaiBuddhistDate.now()));
    }

    @Test
    public void test_WOWBY_isSupportedBy_fieldsDiffer() {
        assertEquals(false, IsoFields.WEEK_OF_WEEK_BASED_YEAR.isSupportedBy(HijrahDate.now()));
        assertEquals(true, WeekFields.ISO.weekOfWeekBasedYear().isSupportedBy(HijrahDate.now()));
    }

    @ParameterizedTest
    @MethodSource("data_Fields")
    public void test_WOWBY_range(TemporalField weekField, TemporalField yearField) {
        assertEquals(ValueRange.of(1, 52, 53), weekField.range());
    }

    @ParameterizedTest
    @MethodSource("data_Fields")
    public void test_WOWBY_rangeRefinedBy(TemporalField weekField, TemporalField yearField) {
        assertEquals(ValueRange.of(1, 52), weekField.rangeRefinedBy(LocalDate.of(2012, 12, 31)));
        assertEquals(ValueRange.of(1, 52), weekField.rangeRefinedBy(LocalDate.of(2013, 12, 29)));
        assertEquals(ValueRange.of(1, 52), weekField.rangeRefinedBy(LocalDate.of(2013, 12, 30)));
        assertEquals(ValueRange.of(1, 52), weekField.rangeRefinedBy(LocalDate.of(2014, 12, 28)));
        assertEquals(ValueRange.of(1, 53), weekField.rangeRefinedBy(LocalDate.of(2014, 12, 29)));
        assertEquals(ValueRange.of(1, 53), weekField.rangeRefinedBy(LocalDate.of(2016, 1, 3)));
        assertEquals(ValueRange.of(1, 52), weekField.rangeRefinedBy(LocalDate.of(2016, 1, 4)));
    }

    //-----------------------------------------------------------------------
    // WEEK_BASED_YEAR
    //-----------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("data_Fields")
    public void test_WBY_basics(TemporalField weekField, TemporalField yearField) {
        assertEquals(true, yearField.isDateBased());
        assertEquals(false, yearField.isTimeBased());
        assertEquals(IsoFields.WEEK_BASED_YEARS, yearField.getBaseUnit());
        assertEquals(ChronoUnit.FOREVER, yearField.getRangeUnit());
    }

    @ParameterizedTest
    @MethodSource("data_Fields")
    public void test_WBY_isSupportedBy(TemporalField weekField, TemporalField yearField) {
        assertEquals(false, yearField.isSupportedBy(LocalTime.NOON));
        assertEquals(false, yearField.isSupportedBy(MonthDay.of(2, 1)));
        assertEquals(true, yearField.isSupportedBy(LocalDate.MIN));
        assertEquals(true, yearField.isSupportedBy(OffsetDateTime.MAX));
        assertEquals(true, yearField.isSupportedBy(ThaiBuddhistDate.now()));
    }

    @Test
    public void test_WBY_isSupportedBy_ISO() {
        assertEquals(false, IsoFields.WEEK_BASED_YEAR.isSupportedBy(HijrahDate.now()));
    }

    @Test
    public void test_Unit_isSupportedBy_ISO() {
        assertEquals(true, IsoFields.WEEK_BASED_YEARS.isSupportedBy(LocalDate.now()));
        assertEquals(true, IsoFields.WEEK_BASED_YEARS.isSupportedBy(ThaiBuddhistDate.now()));
        assertEquals(false, IsoFields.WEEK_BASED_YEARS.isSupportedBy(HijrahDate.now()));
        assertEquals(true, IsoFields.QUARTER_YEARS.isSupportedBy(LocalDate.now()));
        assertEquals(true, IsoFields.QUARTER_YEARS.isSupportedBy(ThaiBuddhistDate.now()));
        assertEquals(false, IsoFields.QUARTER_YEARS.isSupportedBy(HijrahDate.now()));
    }

    @ParameterizedTest
    @MethodSource("data_Fields")
    public void test_WBY_range(TemporalField weekField, TemporalField yearField) {
        assertEquals(ValueRange.of(Year.MIN_VALUE, Year.MAX_VALUE), yearField.range());
    }

    @ParameterizedTest
    @MethodSource("data_Fields")
    public void test_WBY_rangeRefinedBy(TemporalField weekField, TemporalField yearField) {
        assertEquals(ValueRange.of(Year.MIN_VALUE, Year.MAX_VALUE), yearField.rangeRefinedBy(LocalDate.of(2012, 12, 31)));
    }

    //-----------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("data_Fields")
    public void test_getFrom(TemporalField weekField, TemporalField yearField) {
        // tests every day from 2011 to 2016 inclusive
        LocalDate date = LocalDate.of(2011, 1, 3);
        int wby = 2011;
        int week = 1;
        int dow = 1;
        for (int i = 1; i <= ((52 + 52 + 52 + 52 + 53 + 52) * 7); i++) {
            assertEquals(wby, yearField.getFrom(date));
            assertEquals(week, weekField.getFrom(date));
            assertEquals(dow, DAY_OF_WEEK.getFrom(date));
            if (dow == 7) {
                dow = 1;
                week++;
            } else {
                dow++;
            }
            if (week > wbyLen(wby)) {
                week = 1;
                wby++;
            }
            date = date.plusDays(1);
        }
        assertEquals(2017, yearField.getFrom(date));
        assertEquals(1, weekField.getFrom(date));
        assertEquals(1, DAY_OF_WEEK.getFrom(date));
    }

    @ParameterizedTest
    @MethodSource("data_Fields")
    public void test_adjustInto_dow(TemporalField weekField, TemporalField yearField) {
        // tests every day from 2012 to 2016 inclusive
        LocalDate date = LocalDate.of(2012, 1, 2);
        int wby = 2012;
        int week = 1;
        int dow = 1;
        for (int i = 1; i <= ((52 + 52 + 52 + 53 + 52) * 7); i++) {
            for (int j = 1; j <= 7; j++) {
                LocalDate adjusted = DAY_OF_WEEK.adjustInto(date, j);
                assertEquals(j, adjusted.get(DAY_OF_WEEK));
                assertEquals(week, adjusted.get(weekField));
                assertEquals(wby, adjusted.get(yearField));
            }
            if (dow == 7) {
                dow = 1;
                week++;
            } else {
                dow++;
            }
            if (week > wbyLen(wby)) {
                week = 1;
                wby++;
            }
            date = date.plusDays(1);
        }
    }

    @ParameterizedTest
    @MethodSource("data_Fields")
    public void test_adjustInto_week(TemporalField weekField, TemporalField yearField) {
        // tests every day from 2012 to 2016 inclusive
        LocalDate date = LocalDate.of(2012, 1, 2);
        int wby = 2012;
        int week = 1;
        int dow = 1;
        for (int i = 1; i <= ((52 + 52 + 52 + 53 + 52) * 7); i++) {
            int weeksInYear = (wby == 2015 ? 53 : 52);
            for (int j = 1; j <= weeksInYear; j++) {
                LocalDate adjusted = weekField.adjustInto(date, j);
                assertEquals(j, adjusted.get(weekField));
                assertEquals(dow, adjusted.get(DAY_OF_WEEK));
                assertEquals(wby, adjusted.get(yearField));
            }
            if (dow == 7) {
                dow = 1;
                week++;
            } else {
                dow++;
            }
            if (week > wbyLen(wby)) {
                week = 1;
                wby++;
            }
            date = date.plusDays(1);
        }
    }

    @ParameterizedTest
    @MethodSource("data_Fields")
    public void test_adjustInto_wby(TemporalField weekField, TemporalField yearField) {
        // tests every day from 2012 to 2016 inclusive
        LocalDate date = LocalDate.of(2012, 1, 2);
        int wby = 2012;
        int week = 1;
        int dow = 1;
        for (int i = 1; i <= ((52 + 52 + 52 + 53 + 52) * 7); i++) {
            for (int j = 2004; j <= 2015; j++) {
                LocalDate adjusted = yearField.adjustInto(date, j);
                assertEquals(j, adjusted.get(yearField));
                assertEquals(dow, adjusted.get(DAY_OF_WEEK));
                assertEquals((week == 53 && wbyLen(j) == 52 ? 52 : week), adjusted.get(weekField), "" + date + " " + adjusted);
            }
            if (dow == 7) {
                dow = 1;
                week++;
            } else {
                dow++;
            }
            if (week > wbyLen(wby)) {
                week = 1;
                wby++;
            }
            date = date.plusDays(1);
        }
    }

    @ParameterizedTest
    @MethodSource("data_Fields")
    public void test_addTo_weekBasedYears(TemporalField weekField, TemporalField yearField) {
        // tests every day from 2012 to 2016 inclusive
        LocalDate date = LocalDate.of(2012, 1, 2);
        int wby = 2012;
        int week = 1;
        int dow = 1;
        for (int i = 1; i <= ((52 + 52 + 52 + 53 + 52) * 7); i++) {
            for (int j = -5; j <= 5; j++) {
                LocalDate adjusted = IsoFields.WEEK_BASED_YEARS.addTo(date, j);
                assertEquals(wby + j, adjusted.get(yearField));
                assertEquals(dow, adjusted.get(DAY_OF_WEEK));
                assertEquals((week == 53 && wbyLen(wby + j) == 52 ? 52 : week), adjusted.get(weekField), "" + date + " " + adjusted);
            }
            if (dow == 7) {
                dow = 1;
                week++;
            } else {
                dow++;
            }
            if (week > wbyLen(wby)) {
                week = 1;
                wby++;
            }
            date = date.plusDays(1);
        }
    }

    @Test
    public void test_ISOSingleton() {
        assertTrue(WeekFields.ISO == WeekFields.of(DayOfWeek.MONDAY, 4));
    }

    private int wbyLen(int wby) {
        return (wby == 2004 || wby == 2009 || wby == 2015 || wby == 2020 ? 53 : 52);
    }

}
