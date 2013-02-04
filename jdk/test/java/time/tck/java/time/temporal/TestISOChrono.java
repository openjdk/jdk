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

import static java.time.temporal.ChronoField.ERA;
import static java.time.temporal.ChronoField.YEAR;
import static java.time.temporal.ChronoField.YEAR_OF_ERA;
import static java.time.temporal.ISOChrono.ERA_BCE;
import static java.time.temporal.ISOChrono.ERA_CE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.Chrono;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoLocalDate;
import java.time.temporal.Adjusters;
import java.time.calendar.HijrahChrono;
import java.time.temporal.Era;
import java.time.temporal.ISOChrono;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test.
 */
@Test
public class TestISOChrono {

    //-----------------------------------------------------------------------
    // Chrono.ofName("ISO")  Lookup by name
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_chrono_byName() {
        Chrono<ISOChrono> c = ISOChrono.INSTANCE;
        Chrono<?> test = Chrono.of("ISO");
        Assert.assertNotNull(test, "The ISO calendar could not be found byName");
        Assert.assertEquals(test.getId(), "ISO", "ID mismatch");
        Assert.assertEquals(test.getCalendarType(), "iso8601", "Type mismatch");
        Assert.assertEquals(test, c);
    }

    //-----------------------------------------------------------------------
    // Lookup by Singleton
    //-----------------------------------------------------------------------
    @Test(groups="tck")
    public void instanceNotNull() {
        assertNotNull(ISOChrono.INSTANCE);
    }

    //-----------------------------------------------------------------------
    // Era creation
    //-----------------------------------------------------------------------
    @Test(groups="tck")
    public void test_eraOf() {
        assertEquals(ISOChrono.INSTANCE.eraOf(0), ERA_BCE);
        assertEquals(ISOChrono.INSTANCE.eraOf(1), ERA_CE);
    }

    //-----------------------------------------------------------------------
    // creation, toLocalDate()
    //-----------------------------------------------------------------------
    @DataProvider(name="samples")
    Object[][] data_samples() {
        return new Object[][] {
            {ISOChrono.INSTANCE.date(1, 7, 8), LocalDate.of(1, 7, 8)},
            {ISOChrono.INSTANCE.date(1, 7, 20), LocalDate.of(1, 7, 20)},
            {ISOChrono.INSTANCE.date(1, 7, 21), LocalDate.of(1, 7, 21)},

            {ISOChrono.INSTANCE.date(2, 7, 8), LocalDate.of(2, 7, 8)},
            {ISOChrono.INSTANCE.date(3, 6, 27), LocalDate.of(3, 6, 27)},
            {ISOChrono.INSTANCE.date(3, 5, 23), LocalDate.of(3, 5, 23)},
            {ISOChrono.INSTANCE.date(4, 6, 16), LocalDate.of(4, 6, 16)},
            {ISOChrono.INSTANCE.date(4, 7, 3), LocalDate.of(4, 7, 3)},
            {ISOChrono.INSTANCE.date(4, 7, 4), LocalDate.of(4, 7, 4)},
            {ISOChrono.INSTANCE.date(5, 1, 1), LocalDate.of(5, 1, 1)},
            {ISOChrono.INSTANCE.date(1727, 3, 3), LocalDate.of(1727, 3, 3)},
            {ISOChrono.INSTANCE.date(1728, 10, 28), LocalDate.of(1728, 10, 28)},
            {ISOChrono.INSTANCE.date(2012, 10, 29), LocalDate.of(2012, 10, 29)},
        };
    }

    @Test(dataProvider="samples", groups={"tck"})
    public void test_toLocalDate(ChronoLocalDate<ISOChrono> isoDate, LocalDate iso) {
        assertEquals(LocalDate.from(isoDate), iso);
    }

    @Test(dataProvider="samples", groups={"tck"})
    public void test_fromCalendrical(ChronoLocalDate<ISOChrono> isoDate, LocalDate iso) {
        assertEquals(ISOChrono.INSTANCE.date(iso), isoDate);
    }

    @DataProvider(name="badDates")
    Object[][] data_badDates() {
        return new Object[][] {
            {2012, 0, 0},

            {2012, -1, 1},
            {2012, 0, 1},
            {2012, 14, 1},
            {2012, 15, 1},

            {2012, 1, -1},
            {2012, 1, 0},
            {2012, 1, 32},

            {2012, 12, -1},
            {2012, 12, 0},
            {2012, 12, 32},
        };
    }

    @Test(dataProvider="badDates", groups={"tck"}, expectedExceptions=DateTimeException.class)
    public void test_badDates(int year, int month, int dom) {
        ISOChrono.INSTANCE.date(year, month, dom);
    }

    @Test(groups="tck")
    public void test_date_withEra() {
        int year = 5;
        int month = 5;
        int dayOfMonth = 5;
        ChronoLocalDate<ISOChrono> test = ISOChrono.INSTANCE.date(ERA_BCE, year, month, dayOfMonth);
        assertEquals(test.getEra(), ERA_BCE);
        assertEquals(test.get(ChronoField.YEAR_OF_ERA), year);
        assertEquals(test.get(ChronoField.MONTH_OF_YEAR), month);
        assertEquals(test.get(ChronoField.DAY_OF_MONTH), dayOfMonth);

        assertEquals(test.get(YEAR), 1 + (-1 * year));
        assertEquals(test.get(ERA), 0);
        assertEquals(test.get(YEAR_OF_ERA), year);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test(expectedExceptions=DateTimeException.class, groups="tck")
    public void test_date_withEra_withWrongEra() {
        ISOChrono.INSTANCE.date((Era) HijrahChrono.ERA_AH, 1, 1, 1);
    }

    //-----------------------------------------------------------------------
    // with(DateTimeAdjuster)
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_adjust1() {
        ChronoLocalDate<ISOChrono> base = ISOChrono.INSTANCE.date(1728, 10, 28);
        ChronoLocalDate<ISOChrono> test = base.with(Adjusters.lastDayOfMonth());
        assertEquals(test, ISOChrono.INSTANCE.date(1728, 10, 31));
    }

    @Test(groups={"tck"})
    public void test_adjust2() {
        ChronoLocalDate<ISOChrono> base = ISOChrono.INSTANCE.date(1728, 12, 2);
        ChronoLocalDate<ISOChrono> test = base.with(Adjusters.lastDayOfMonth());
        assertEquals(test, ISOChrono.INSTANCE.date(1728, 12, 31));
    }

    //-----------------------------------------------------------------------
    // ISODate.with(Local*)
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_adjust_toLocalDate() {
        ChronoLocalDate<ISOChrono> isoDate = ISOChrono.INSTANCE.date(1726, 1, 4);
        ChronoLocalDate<ISOChrono> test = isoDate.with(LocalDate.of(2012, 7, 6));
        assertEquals(test, ISOChrono.INSTANCE.date(2012, 7, 6));
    }

    @Test(groups={"tck"})
    public void test_adjust_toMonth() {
        ChronoLocalDate<ISOChrono> isoDate = ISOChrono.INSTANCE.date(1726, 1, 4);
        assertEquals(ISOChrono.INSTANCE.date(1726, 4, 4), isoDate.with(Month.APRIL));
    }

    //-----------------------------------------------------------------------
    // LocalDate.with(ISODate)
    //-----------------------------------------------------------------------
    @Test(groups={"tck"})
    public void test_LocalDate_adjustToISODate() {
        ChronoLocalDate<ISOChrono> isoDate = ISOChrono.INSTANCE.date(1728, 10, 29);
        LocalDate test = LocalDate.MIN.with(isoDate);
        assertEquals(test, LocalDate.of(1728, 10, 29));
    }

    @Test(groups={"tck"})
    public void test_LocalDateTime_adjustToISODate() {
        ChronoLocalDate<ISOChrono> isoDate = ISOChrono.INSTANCE.date(1728, 10, 29);
        LocalDateTime test = LocalDateTime.MIN.with(isoDate);
        assertEquals(test, LocalDateTime.of(1728, 10, 29, 0, 0));
    }

    //-----------------------------------------------------------------------
    // isLeapYear()
    //-----------------------------------------------------------------------
    @DataProvider(name="leapYears")
    Object[][] leapYearInformation() {
        return new Object[][] {
                {2000, true},
                {1996, true},
                {1600, true},

                {1900, false},
                {2100, false},
        };
    }

    @Test(dataProvider="leapYears", groups="tck")
    public void test_isLeapYear(int year, boolean isLeapYear) {
        assertEquals(ISOChrono.INSTANCE.isLeapYear(year), isLeapYear);
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @Test(groups="tck")
    public void test_now() {
        assertEquals(LocalDate.from(ISOChrono.INSTANCE.dateNow()), LocalDate.now());
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @DataProvider(name="toString")
    Object[][] data_toString() {
        return new Object[][] {
            {ISOChrono.INSTANCE.date(1, 1, 1), "0001-01-01"},
            {ISOChrono.INSTANCE.date(1728, 10, 28), "1728-10-28"},
            {ISOChrono.INSTANCE.date(1728, 10, 29), "1728-10-29"},
            {ISOChrono.INSTANCE.date(1727, 12, 5), "1727-12-05"},
            {ISOChrono.INSTANCE.date(1727, 12, 6), "1727-12-06"},
        };
    }

    @Test(dataProvider="toString", groups={"tck"})
    public void test_toString(ChronoLocalDate<ISOChrono> isoDate, String expected) {
        assertEquals(isoDate.toString(), expected);
    }

    //-----------------------------------------------------------------------
    // equals()
    //-----------------------------------------------------------------------
    @Test(groups="tck")
    public void test_equals_true() {
        assertTrue(ISOChrono.INSTANCE.equals(ISOChrono.INSTANCE));
    }

    @Test(groups="tck")
    public void test_equals_false() {
        assertFalse(ISOChrono.INSTANCE.equals(HijrahChrono.INSTANCE));
    }

}
