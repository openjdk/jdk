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
package tck.java.time;

import static java.time.chrono.IsoEra.BCE;
import static java.time.chrono.IsoEra.CE;
import static java.time.temporal.ChronoField.ERA;
import static java.time.temporal.ChronoField.YEAR;
import static java.time.temporal.ChronoField.YEAR_OF_ERA;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.chrono.Chronology;
import java.time.chrono.Era;
import java.time.chrono.HijrahChronology;
import java.time.chrono.HijrahEra;
import java.time.chrono.IsoChronology;
import java.time.chrono.IsoEra;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestIsoChronology {

    //-----------------------------------------------------------------------
    // Chronology.ofName("ISO")  Lookup by name
    //-----------------------------------------------------------------------
    @Test
    public void test_chrono_byName() {
        Chronology c = IsoChronology.INSTANCE;
        Chronology test = Chronology.of("ISO");
        assertNotNull(test, "The ISO calendar could not be found byName");
        assertEquals("ISO", test.getId(), "ID mismatch");
        assertEquals("iso8601", test.getCalendarType(), "Type mismatch");
        assertEquals(c, test);
    }

    //-----------------------------------------------------------------------
    // Lookup by Singleton
    //-----------------------------------------------------------------------
    @Test
    public void instanceNotNull() {
        assertNotNull(IsoChronology.INSTANCE);
    }

    //-----------------------------------------------------------------------
    // Era creation
    //-----------------------------------------------------------------------
    @Test
    public void test_eraOf() {
        assertEquals(BCE, IsoChronology.INSTANCE.eraOf(0));
        assertEquals(CE, IsoChronology.INSTANCE.eraOf(1));
    }

    //-----------------------------------------------------------------------
    // creation, toLocalDate()
    //-----------------------------------------------------------------------
    Object[][] data_samples() {
        return new Object[][] {
            {IsoChronology.INSTANCE.date(1, 7, 8), LocalDate.of(1, 7, 8)},
            {IsoChronology.INSTANCE.date(1, 7, 20), LocalDate.of(1, 7, 20)},
            {IsoChronology.INSTANCE.date(1, 7, 21), LocalDate.of(1, 7, 21)},

            {IsoChronology.INSTANCE.date(2, 7, 8), LocalDate.of(2, 7, 8)},
            {IsoChronology.INSTANCE.date(3, 6, 27), LocalDate.of(3, 6, 27)},
            {IsoChronology.INSTANCE.date(3, 5, 23), LocalDate.of(3, 5, 23)},
            {IsoChronology.INSTANCE.date(4, 6, 16), LocalDate.of(4, 6, 16)},
            {IsoChronology.INSTANCE.date(4, 7, 3), LocalDate.of(4, 7, 3)},
            {IsoChronology.INSTANCE.date(4, 7, 4), LocalDate.of(4, 7, 4)},
            {IsoChronology.INSTANCE.date(5, 1, 1), LocalDate.of(5, 1, 1)},
            {IsoChronology.INSTANCE.date(1727, 3, 3), LocalDate.of(1727, 3, 3)},
            {IsoChronology.INSTANCE.date(1728, 10, 28), LocalDate.of(1728, 10, 28)},
            {IsoChronology.INSTANCE.date(2012, 10, 29), LocalDate.of(2012, 10, 29)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_samples")
    public void test_toLocalDate(LocalDate isoDate, LocalDate iso) {
        assertEquals(iso, LocalDate.from(isoDate));
    }

    @ParameterizedTest
    @MethodSource("data_samples")
    public void test_fromCalendrical(LocalDate isoDate, LocalDate iso) {
        assertEquals(isoDate, IsoChronology.INSTANCE.date(iso));
    }

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

    @ParameterizedTest
    @MethodSource("data_badDates")
    public void test_badDates(int year, int month, int dom) {
        Assertions.assertThrows(DateTimeException.class, () -> IsoChronology.INSTANCE.date(year, month, dom));
    }

    @Test
    public void test_date_withEra() {
        int year = 5;
        int month = 5;
        int dayOfMonth = 5;
        LocalDate test = IsoChronology.INSTANCE.date(IsoEra.BCE, year, month, dayOfMonth);
        assertEquals(IsoEra.BCE, test.getEra());
        assertEquals(year, test.get(ChronoField.YEAR_OF_ERA));
        assertEquals(month, test.get(ChronoField.MONTH_OF_YEAR));
        assertEquals(dayOfMonth, test.get(ChronoField.DAY_OF_MONTH));

        assertEquals(1 + (-1 * year), test.get(YEAR));
        assertEquals(0, test.get(ERA));
        assertEquals(year, test.get(YEAR_OF_ERA));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void test_date_withEra_withWrongEra() {
        Assertions.assertThrows(ClassCastException.class, () -> IsoChronology.INSTANCE.date((Era) HijrahEra.AH, 1, 1, 1));
    }

    //-----------------------------------------------------------------------
    // with(DateTimeAdjuster)
    //-----------------------------------------------------------------------
    @Test
    public void test_adjust1() {
        LocalDate base = IsoChronology.INSTANCE.date(1728, 10, 28);
        LocalDate test = base.with(TemporalAdjusters.lastDayOfMonth());
        assertEquals(IsoChronology.INSTANCE.date(1728, 10, 31), test);
    }

    @Test
    public void test_adjust2() {
        LocalDate base = IsoChronology.INSTANCE.date(1728, 12, 2);
        LocalDate test = base.with(TemporalAdjusters.lastDayOfMonth());
        assertEquals(IsoChronology.INSTANCE.date(1728, 12, 31), test);
    }

    //-----------------------------------------------------------------------
    // ISODate.with(Local*)
    //-----------------------------------------------------------------------
    @Test
    public void test_adjust_toLocalDate() {
        LocalDate isoDate = IsoChronology.INSTANCE.date(1726, 1, 4);
        LocalDate test = isoDate.with(LocalDate.of(2012, 7, 6));
        assertEquals(IsoChronology.INSTANCE.date(2012, 7, 6), test);
    }

    @Test
    public void test_adjust_toMonth() {
        LocalDate isoDate = IsoChronology.INSTANCE.date(1726, 1, 4);
        assertEquals(isoDate.with(Month.APRIL), IsoChronology.INSTANCE.date(1726, 4, 4));
    }

    //-----------------------------------------------------------------------
    // LocalDate.with(ISODate)
    //-----------------------------------------------------------------------
    @Test
    public void test_LocalDate_adjustToISODate() {
        LocalDate isoDate = IsoChronology.INSTANCE.date(1728, 10, 29);
        LocalDate test = LocalDate.MIN.with(isoDate);
        assertEquals(LocalDate.of(1728, 10, 29), test);
    }

    @Test
    public void test_LocalDateTime_adjustToISODate() {
        LocalDate isoDate = IsoChronology.INSTANCE.date(1728, 10, 29);
        LocalDateTime test = LocalDateTime.MIN.with(isoDate);
        assertEquals(LocalDateTime.of(1728, 10, 29, 0, 0), test);
    }

    //-----------------------------------------------------------------------
    // isLeapYear()
    //-----------------------------------------------------------------------
    Object[][] leapYearInformation() {
        return new Object[][] {
                {2000, true},
                {1996, true},
                {1600, true},

                {1900, false},
                {2100, false},
        };
    }

    @ParameterizedTest
    @MethodSource("leapYearInformation")
    public void test_isLeapYear(int year, boolean isLeapYear) {
        assertEquals(isLeapYear, IsoChronology.INSTANCE.isLeapYear(year));
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @Test
    public void test_now() {
        assertEquals(LocalDate.now(), LocalDate.from(IsoChronology.INSTANCE.dateNow()));
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    Object[][] data_toString() {
        return new Object[][] {
            {IsoChronology.INSTANCE.date(1, 1, 1), "0001-01-01"},
            {IsoChronology.INSTANCE.date(1728, 10, 28), "1728-10-28"},
            {IsoChronology.INSTANCE.date(1728, 10, 29), "1728-10-29"},
            {IsoChronology.INSTANCE.date(1727, 12, 5), "1727-12-05"},
            {IsoChronology.INSTANCE.date(1727, 12, 6), "1727-12-06"},
        };
    }

    @ParameterizedTest
    @MethodSource("data_toString")
    public void test_toString(LocalDate isoDate, String expected) {
        assertEquals(expected, isoDate.toString());
    }

    //-----------------------------------------------------------------------
    // equals()
    //-----------------------------------------------------------------------
    @Test
    public void test_equals_true() {
        assertTrue(IsoChronology.INSTANCE.equals(IsoChronology.INSTANCE));
    }

    @Test
    public void test_equals_false() {
        assertFalse(IsoChronology.INSTANCE.equals(HijrahChronology.INSTANCE));
    }

}
