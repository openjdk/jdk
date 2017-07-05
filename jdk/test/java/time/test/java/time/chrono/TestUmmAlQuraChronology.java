/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package test.java.time.chrono;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.chrono.Chronology;
import java.time.chrono.HijrahChronology;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.ValueRange;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for the Umm alQura chronology and data
 */
@Test
public class TestUmmAlQuraChronology {

    @Test
    public void test_aliases() {
        HijrahChronology hc = (HijrahChronology) Chronology.of("Hijrah");
        assertEquals(hc, HijrahChronology.INSTANCE, "Alias for Hijrah-umalqura");
        hc = (HijrahChronology) Chronology.of("islamic");
        assertEquals(hc, HijrahChronology.INSTANCE, "Alias for Hijrah-umalqura");
    }

    //-----------------------------------------------------------------------
    // regular data factory for Umm alQura dates and the corresponding ISO dates
    //-----------------------------------------------------------------------
    @DataProvider(name = "UmmalQuraVsISODates")
    Object[][] data_of_ummalqura() {
        return new Object[][]{

            //{1318, 01, 01,   1900, 04, 30},
            //{1318, 01, 02,   1900, 05, 01},

            //{1318, 12, 29,   1901, 04, 18},
            //{1319, 01, 01,   1901, 04, 19},

            //{1433, 12, 29,   2012, 11, 14},
            //{1434, 01, 01,   2012, 11, 15},

            {1434, 02, 18,   2012, 12, 31},
            {1434, 02, 19,   2013, 01, 01},

            //{1502, 12, 30,   2079, 10, 25},
            // not in Umm alQura data {1503, 01, 01,   2079, 10, 26},

            // not in Umm alQura data {1503, 06, 28,   2080, 04, 18},
            // not in Umm alQura data ~/ws/Downloads
        };
    }

    @Test(dataProvider="UmmalQuraVsISODates")
        public void Test_UmmAlQuraDatesVsISO(int h_year, int h_month, int h_day, int iso_year, int iso_month, int iso_day) {
        HijrahDate hd = HijrahDate.of(h_year, h_month, h_day);
        LocalDate ld = LocalDate.of(iso_year, iso_month, iso_day);
        assertEquals(hd.toEpochDay(), ld.toEpochDay(), "Umm alQura date and ISO date should have same epochDay");
    }


    @Test
    public void Test_UmmAlQuraChronoRange() {
        HijrahChronology chrono = HijrahChronology.INSTANCE;
        ValueRange year = chrono.range(YEAR);
        assertEquals(year.getMinimum(), 1432, "Minimum year");
        assertEquals(year.getLargestMinimum(), 1432, "Largest minimum year");
        assertEquals(year.getMaximum(), 1435, "Largest year");
        assertEquals(year.getSmallestMaximum(), 1435, "Smallest Maximum year");

        ValueRange month = chrono.range(MONTH_OF_YEAR);
        assertEquals(month.getMinimum(), 1, "Minimum month");
        assertEquals(month.getLargestMinimum(), 1, "Largest minimum month");
        assertEquals(month.getMaximum(), 12, "Largest month");
        assertEquals(month.getSmallestMaximum(), 12, "Smallest Maximum month");

        ValueRange day = chrono.range(DAY_OF_MONTH);
        assertEquals(day.getMinimum(), 1, "Minimum day");
        assertEquals(day.getLargestMinimum(), 1, "Largest minimum day");
        assertEquals(day.getMaximum(), 30, "Largest day");
        assertEquals(day.getSmallestMaximum(), 29, "Smallest Maximum day");
    }

    //-----------------------------------------------------------------------
    // regular data factory for dates and the corresponding range values
    //-----------------------------------------------------------------------
    @DataProvider(name = "dates")
    Object[][] data_of_calendars() {
        return new Object[][]{
            {HijrahDate.of(1434, 5, 1), 1432, 1435, 1, 12, 1, 29, 30},
            {HijrahDate.of(1434, 6, 1), 1432, 1435, 1, 12, 1, 30, 30},
        };
    }

    @Test(dataProvider="dates")
    public void Test_UmmAlQuraRanges(HijrahDate date,
                        int minYear, int maxYear,
                        int minMonth, int maxMonth,
                        int minDay, int maxDay, int maxChronoDay) {
        // Check the chronology ranges
        HijrahChronology chrono = date.getChronology();
        ValueRange yearRange = chrono.range(YEAR);
        assertEquals(yearRange.getMinimum(), minYear, "Minimum year for Hijrah chronology");
        assertEquals(yearRange.getLargestMinimum(), minYear, "Largest minimum year for Hijrah chronology");
        assertEquals(yearRange.getMaximum(), maxYear, "Maximum year for Hijrah chronology");
        assertEquals(yearRange.getSmallestMaximum(), maxYear, "Smallest Maximum year for Hijrah chronology");

        ValueRange monthRange = chrono.range(MONTH_OF_YEAR);
        assertEquals(monthRange.getMinimum(), minMonth, "Minimum month for Hijrah chronology");
        assertEquals(monthRange.getMaximum(), maxMonth, "Maximum month for Hijrah chronology");

        ValueRange daysRange = chrono.range(DAY_OF_MONTH);
        assertEquals(daysRange.getMinimum(), minDay, "Minimum day for chronology");
        assertEquals(daysRange.getMaximum(), maxChronoDay, "Maximum day for Hijrah chronology");

        // Check the date ranges
        yearRange = date.range(YEAR);
        assertEquals(yearRange.getMinimum(), minYear, "Minimum year for Hijrah date");
        assertEquals(yearRange.getLargestMinimum(), minYear, "Largest minimum  year for Hijrah date");
        assertEquals(yearRange.getMaximum(), maxYear, "Maximum year for Hijrah date");
        assertEquals(yearRange.getSmallestMaximum(), maxYear, "Smallest maximum year for Hijrah date");

        monthRange = date.range(MONTH_OF_YEAR);
        assertEquals(monthRange.getMinimum(), minMonth, "Minimum month for HijrahDate");
        assertEquals(monthRange.getMaximum(), maxMonth, "Maximum month for HijrahDate");

        daysRange = date.range(DAY_OF_MONTH);
        assertEquals(daysRange.getMinimum(), minDay, "Minimum day for HijrahDate");
        assertEquals(daysRange.getMaximum(), maxDay, "Maximum day for HijrahDate");

    }

    @Test
    public void test_hijrahDateLimits() {
        HijrahChronology chrono = HijrahChronology.INSTANCE;
        ValueRange yearRange = chrono.range(YEAR);
        ValueRange monthRange = chrono.range(MONTH_OF_YEAR);
        ValueRange dayRange = chrono.range(DAY_OF_MONTH);

        HijrahDate xx = chrono.date(1434, 1, 1);
        HijrahDate minDate = chrono.date((int)yearRange.getLargestMinimum(),
                (int)monthRange.getMinimum(), (int)dayRange.getMinimum());
        try {
            HijrahDate before = minDate.minus(1, ChronoUnit.DAYS);
            fail("Exception did not occur, minDate: " + minDate + ".minus(1, DAYS) = " + before);

        } catch (DateTimeException ex) {
            // ignore, this exception was expected
        }

        HijrahDate maxDate = chrono.date((int)yearRange.getSmallestMaximum(),
                (int)monthRange.getMaximum(), 1);
        int monthLen = maxDate.lengthOfMonth();
        maxDate = maxDate.with(DAY_OF_MONTH, monthLen);
        try {
            HijrahDate after = maxDate.plus(1, ChronoUnit.DAYS);
            fail("Exception did not occur, maxDate: " + maxDate + ".plus(1, DAYS) = " + after);
        } catch (DateTimeException ex) {
            // ignore, this exception was expected
        }
    }

    @DataProvider(name="badDates")
    Object[][] data_badDates() {
        return new Object[][] {
            {1317, 12, 29},
            {1317, 12, 30},

            {1320, 1, 29 + 1},
            {1320, 2, 30 + 1},
            {1320, 3, 29 + 1},
            {1320, 4, 29 + 1},
            {1320, 5, 30 + 1},
            {1320, 6, 29 + 1},
            {1320, 7, 30 + 1},
            {1320, 8, 30 + 1},
            {1320, 9, 29 + 1},
            {1320, 10, 30 + 1},
            {1320, 11, 30 + 1},
            {1320, 12, 30 + 1},
        };
    }

    @Test(dataProvider="badDates", expectedExceptions=DateTimeException.class)
    public void test_badDates(int year, int month, int dom) {
        HijrahChronology.INSTANCE.date(year, month, dom);
    }

    void printRange(ValueRange range, Object obj, ChronoField field) {
        System.err.printf(" range: min: %d, max: %d; of: %s, field: %s%n", range.getMinimum(), range.getMaximum(), obj.toString(), field.toString());
    }
}
