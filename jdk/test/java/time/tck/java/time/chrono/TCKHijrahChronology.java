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
package tck.java.time.chrono;

import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Period;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.Chronology;
import java.time.chrono.Era;
import java.time.chrono.HijrahChronology;
import java.time.chrono.HijrahDate;
import java.time.chrono.IsoChronology;
import java.time.chrono.MinguoChronology;
import java.time.chrono.MinguoDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjuster;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test.
 */
@Test
public class TCKHijrahChronology {

    //-----------------------------------------------------------------------
    // Chronology.ofName("Hijrah")  Lookup by name
    //-----------------------------------------------------------------------
    @Test
    public void test_chrono_byName() {
        Chronology c = HijrahChronology.INSTANCE;
        Chronology test = Chronology.of("Hijrah-umalqura");
        Assert.assertNotNull(test, "The Hijrah-umalqura calendar could not be found by name");
        Assert.assertEquals(test.getId(), "Hijrah-umalqura", "ID mismatch");
        Assert.assertEquals(test.getCalendarType(), "islamic-umalqura", "Type mismatch");
        Assert.assertEquals(test, c);
    }

    //-----------------------------------------------------------------------
    // creation, toLocalDate()
    //-----------------------------------------------------------------------
    @DataProvider(name="samples")
    Object[][] data_samples() {
        return new Object[][] {
            //{HijrahChronology.INSTANCE.date(1320, 1, 1), LocalDate.of(1902, 4, 9)},
            //{HijrahChronology.INSTANCE.date(1320, 1, 2), LocalDate.of(1902, 4, 10)},
            //{HijrahChronology.INSTANCE.date(1320, 1, 3), LocalDate.of(1902, 4, 11)},

            //{HijrahChronology.INSTANCE.date(1322, 1, 1), LocalDate.of(1904, 3, 18)},
            //{HijrahChronology.INSTANCE.date(1323, 1, 1), LocalDate.of(1905, 3, 7)},
            //{HijrahChronology.INSTANCE.date(1323, 12, 6), LocalDate.of(1906, 1, 30)},
            //{HijrahChronology.INSTANCE.date(1324, 1, 1), LocalDate.of(1906, 2, 24)},
            //{HijrahChronology.INSTANCE.date(1324, 7, 3), LocalDate.of(1906, 8, 23)},
            //{HijrahChronology.INSTANCE.date(1324, 7, 4), LocalDate.of(1906, 8, 24)},
            //{HijrahChronology.INSTANCE.date(1325, 1, 1), LocalDate.of(1907, 2, 13)},
            {HijrahChronology.INSTANCE.date(1434, 7, 1), LocalDate.of(2013, 5, 11)},

            //{HijrahChronology.INSTANCE.date(1500, 3, 3), LocalDate.of(2079, 1, 5)},
            //{HijrahChronology.INSTANCE.date(1500, 10, 28), LocalDate.of(2079, 8, 25)},
            //{HijrahChronology.INSTANCE.date(1500, 10, 29), LocalDate.of(2079, 8, 26)},
        };
    }

    @Test(dataProvider="samples")
    public void test_toLocalDate(ChronoLocalDate<?> hijrahDate, LocalDate iso) {
        assertEquals(LocalDate.from(hijrahDate), iso);
    }

    @Test(dataProvider="samples")
    public void test_fromCalendrical(ChronoLocalDate<?> hijrahDate, LocalDate iso) {
        assertEquals(HijrahChronology.INSTANCE.date(iso), hijrahDate);
    }

    @Test(dataProvider="samples")
    public void test_dayOfWeekEqualIsoDayOfWeek(ChronoLocalDate<?> hijrahDate, LocalDate iso) {
        assertEquals(hijrahDate.get(DAY_OF_WEEK), iso.get(DAY_OF_WEEK), "Hijrah day of week should be same as ISO day of week");
    }

    @DataProvider(name="badDates")
    Object[][] data_badDates() {
        return new Object[][] {
            {1434, 0, 0},

            {1434, -1, 1},
            {1434, 0, 1},
            {1434, 14, 1},
            {1434, 15, 1},

            {1434, 1, -1},
            {1434, 1, 0},
            {1434, 1, 32},

            {1434, 12, -1},
            {1434, 12, 0},
            {1434, 12, 32},
        };
    }

    @Test(dataProvider="badDates", expectedExceptions=DateTimeException.class)
    public void test_badDates(int year, int month, int dom) {
        HijrahChronology.INSTANCE.date(year, month, dom);
    }

    //-----------------------------------------------------------------------
    // Bad Era for Chronology.date(era,...) and Chronology.prolepticYear(Era,...)
    //-----------------------------------------------------------------------
    @Test
    public void test_InvalidEras() {
        // Verify that the eras from every other Chronology are invalid
        for (Chronology chrono : Chronology.getAvailableChronologies()) {
            if (chrono instanceof HijrahChronology) {
                continue;
            }
            List<Era> eras = chrono.eras();
            for (Era era : eras) {
                try {
                    ChronoLocalDate date = HijrahChronology.INSTANCE.date(era, 1, 1, 1);
                    fail("HijrahChronology.date did not throw ClassCastException for Era: " + era);
                } catch (ClassCastException cex) {
                    ; // ignore expected exception
                }

                /* TODO: Test for missing HijrahDate.of(Era, y, m, d) method.
                try {
                    @SuppressWarnings("unused")
                    HijrahDate jdate = HijrahDate.of(era, 1, 1, 1);
                    fail("HijrahDate.of did not throw ClassCastException for Era: " + era);
                } catch (ClassCastException cex) {
                    ; // ignore expected exception
                }
                */

                try {
                    @SuppressWarnings("unused")
                    int year = HijrahChronology.INSTANCE.prolepticYear(era, 1);
                    fail("HijrahChronology.prolepticYear did not throw ClassCastException for Era: " + era);
                } catch (ClassCastException cex) {
                    ; // ignore expected exception
                }
            }
        }
    }

    //-----------------------------------------------------------------------
    // with(WithAdjuster)
    //-----------------------------------------------------------------------
    @Test
    public void test_adjust1() {
        ChronoLocalDate<?> base = HijrahChronology.INSTANCE.date(1434, 5, 15);
        ChronoLocalDate<?> test = base.with(TemporalAdjuster.lastDayOfMonth());
        assertEquals(test, HijrahChronology.INSTANCE.date(1434, 5, 29));
    }

    @Test
    public void test_adjust2() {
        ChronoLocalDate<?> base = HijrahChronology.INSTANCE.date(1434, 6, 2);
        ChronoLocalDate<?> test = base.with(TemporalAdjuster.lastDayOfMonth());
        assertEquals(test, HijrahChronology.INSTANCE.date(1434, 6, 30));
    }

    //-----------------------------------------------------------------------
    // HijrahDate.with(Local*)
    //-----------------------------------------------------------------------
    @Test
    public void test_adjust_toLocalDate() {
        ChronoLocalDate<?> hijrahDate = HijrahChronology.INSTANCE.date(1435, 1, 4);
        ChronoLocalDate<?> test = hijrahDate.with(LocalDate.of(2012, 7, 6));
        assertEquals(test, HijrahChronology.INSTANCE.date(1433, 8, 16));
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void test_adjust_toMonth() {
        ChronoLocalDate<?> hijrahDate = HijrahChronology.INSTANCE.date(1435, 1, 4);
        hijrahDate.with(Month.APRIL);
    }

    //-----------------------------------------------------------------------
    // LocalDate.with(HijrahDate)
    //-----------------------------------------------------------------------
    @Test
    public void test_LocalDate_adjustToHijrahDate() {
        ChronoLocalDate<?> hijrahDate = HijrahChronology.INSTANCE.date(1434, 5, 15);
        LocalDate test = LocalDate.MIN.with(hijrahDate);
        assertEquals(test, LocalDate.of(2013, 3, 27));
    }

    @Test
    public void test_LocalDateTime_adjustToHijrahDate() {
        ChronoLocalDate<?> hijrahDate = HijrahChronology.INSTANCE.date(1435, 5, 15);
        LocalDateTime test = LocalDateTime.MIN.with(hijrahDate);
        assertEquals(test, LocalDateTime.of(2014, 3, 16, 0, 0));
    }

    //-----------------------------------------------------------------------
    // PeriodUntil()
    //-----------------------------------------------------------------------
    @Test
    public void test_periodUntilDate() {
        HijrahDate mdate1 = HijrahDate.of(1434, 1, 1);
        HijrahDate mdate2 = HijrahDate.of(1435, 2, 2);
        Period period = mdate1.periodUntil(mdate2);
        assertEquals(period, Period.of(1, 1, 1));
    }

    @Test
    public void test_periodUntilUnit() {
        HijrahDate mdate1 = HijrahDate.of(1434, 1, 1);
        HijrahDate mdate2 = HijrahDate.of(1435, 2, 2);
        long months = mdate1.periodUntil(mdate2, ChronoUnit.MONTHS);
        assertEquals(months, 13);
    }

    @Test
    public void test_periodUntilDiffChrono() {
        HijrahDate mdate1 = HijrahDate.of(1434, 1, 1);
        HijrahDate mdate2 = HijrahDate.of(1435, 2, 2);
        MinguoDate ldate2 = MinguoChronology.INSTANCE.date(mdate2);
        Period period = mdate1.periodUntil(ldate2);
        assertEquals(period, Period.of(1, 1, 1));
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @DataProvider(name="toString")
    Object[][] data_toString() {
        return new Object[][] {
            //{HijrahChronology.INSTANCE.date(1320, 1, 1), "Hijrah AH 1320-01-01"},
            //{HijrahChronology.INSTANCE.date(1500, 10, 28), "Hijrah AH 1500-10-28"},
            //{HijrahChronology.INSTANCE.date(1500, 10, 29), "Hijrah AH 1500-10-29"},
            {HijrahChronology.INSTANCE.date(1434, 12, 5), "Hijrah-umalqura AH 1434-12-05"},
            {HijrahChronology.INSTANCE.date(1434, 12, 6), "Hijrah-umalqura AH 1434-12-06"},
        };
    }

    @Test(dataProvider="toString")
    public void test_toString(ChronoLocalDate<?> hijrahDate, String expected) {
        assertEquals(hijrahDate.toString(), expected);
    }

    //-----------------------------------------------------------------------
    // equals()
    //-----------------------------------------------------------------------
    @Test
    public void test_equals_true() {
        assertTrue(HijrahChronology.INSTANCE.equals(HijrahChronology.INSTANCE));
    }

    @Test
    public void test_equals_false() {
        assertFalse(HijrahChronology.INSTANCE.equals(IsoChronology.INSTANCE));
    }

}
