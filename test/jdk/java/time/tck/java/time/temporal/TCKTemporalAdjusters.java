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
 * Copyright (c) 2007-2012, Stephen Colebourne & Michael Nascimento Santos
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

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.Month.DECEMBER;
import static java.time.Month.JANUARY;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test TemporalAdjusters.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TCKTemporalAdjusters {

    //-----------------------------------------------------------------------
    // ofDateAdjuster()
    //-----------------------------------------------------------------------
    @Test
    public void factory_ofDateAdjuster() {
        TemporalAdjuster test = TemporalAdjusters.ofDateAdjuster(date -> date.plusDays(2));
        assertEquals(LocalDate.of(2012, 7, 2), LocalDate.of(2012, 6, 30).with(test));
    }

    @Test
    public void factory_ofDateAdjuster_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TemporalAdjusters.ofDateAdjuster(null));
    }


    //-----------------------------------------------------------------------
    // firstDayOfMonth()
    //-----------------------------------------------------------------------
    @Test
    public void factory_firstDayOfMonth() {
        assertNotNull(TemporalAdjusters.firstDayOfMonth());
    }

    @Test
    public void test_firstDayOfMonth_nonLeap() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(false); i++) {
                LocalDate date = date(2007, month, i);
                LocalDate test = (LocalDate) TemporalAdjusters.firstDayOfMonth().adjustInto(date);
                assertEquals(2007, test.getYear());
                assertEquals(month, test.getMonth());
                assertEquals(1, test.getDayOfMonth());
            }
        }
    }

    @Test
    public void test_firstDayOfMonth_leap() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(true); i++) {
                LocalDate date = date(2008, month, i);
                LocalDate test = (LocalDate) TemporalAdjusters.firstDayOfMonth().adjustInto(date);
                assertEquals(2008, test.getYear());
                assertEquals(month, test.getMonth());
                assertEquals(1, test.getDayOfMonth());
            }
        }
    }

    //-----------------------------------------------------------------------
    // lastDayOfMonth()
    //-----------------------------------------------------------------------
    @Test
    public void factory_lastDayOfMonth() {
        assertNotNull(TemporalAdjusters.lastDayOfMonth());
    }

    @Test
    public void test_lastDayOfMonth_nonLeap() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(false); i++) {
                LocalDate date = date(2007, month, i);
                LocalDate test = (LocalDate) TemporalAdjusters.lastDayOfMonth().adjustInto(date);
                assertEquals(2007, test.getYear());
                assertEquals(month, test.getMonth());
                assertEquals(month.length(false), test.getDayOfMonth());
            }
        }
    }

    @Test
    public void test_lastDayOfMonth_leap() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(true); i++) {
                LocalDate date = date(2008, month, i);
                LocalDate test = (LocalDate) TemporalAdjusters.lastDayOfMonth().adjustInto(date);
                assertEquals(2008, test.getYear());
                assertEquals(month, test.getMonth());
                assertEquals(month.length(true), test.getDayOfMonth());
            }
        }
    }

    //-----------------------------------------------------------------------
    // firstDayOfNextMonth()
    //-----------------------------------------------------------------------
    @Test
    public void factory_firstDayOfNextMonth() {
        assertNotNull(TemporalAdjusters.firstDayOfNextMonth());
    }

    @Test
    public void test_firstDayOfNextMonth_nonLeap() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(false); i++) {
                LocalDate date = date(2007, month, i);
                LocalDate test = (LocalDate) TemporalAdjusters.firstDayOfNextMonth().adjustInto(date);
                assertEquals(month == DECEMBER ? 2008 : 2007, test.getYear());
                assertEquals(month.plus(1), test.getMonth());
                assertEquals(1, test.getDayOfMonth());
            }
        }
    }

    @Test
    public void test_firstDayOfNextMonth_leap() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(true); i++) {
                LocalDate date = date(2008, month, i);
                LocalDate test = (LocalDate) TemporalAdjusters.firstDayOfNextMonth().adjustInto(date);
                assertEquals(month == DECEMBER ? 2009 : 2008, test.getYear());
                assertEquals(month.plus(1), test.getMonth());
                assertEquals(1, test.getDayOfMonth());
            }
        }
    }

    //-----------------------------------------------------------------------
    // firstDayOfYear()
    //-----------------------------------------------------------------------
    @Test
    public void factory_firstDayOfYear() {
        assertNotNull(TemporalAdjusters.firstDayOfYear());
    }

    @Test
    public void test_firstDayOfYear_nonLeap() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(false); i++) {
                LocalDate date = date(2007, month, i);
                LocalDate test = (LocalDate) TemporalAdjusters.firstDayOfYear().adjustInto(date);
                assertEquals(2007, test.getYear());
                assertEquals(Month.JANUARY, test.getMonth());
                assertEquals(1, test.getDayOfMonth());
            }
        }
    }

    @Test
    public void test_firstDayOfYear_leap() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(true); i++) {
                LocalDate date = date(2008, month, i);
                LocalDate test = (LocalDate) TemporalAdjusters.firstDayOfYear().adjustInto(date);
                assertEquals(2008, test.getYear());
                assertEquals(Month.JANUARY, test.getMonth());
                assertEquals(1, test.getDayOfMonth());
            }
        }
    }

    //-----------------------------------------------------------------------
    // lastDayOfYear()
    //-----------------------------------------------------------------------
    @Test
    public void factory_lastDayOfYear() {
        assertNotNull(TemporalAdjusters.lastDayOfYear());
    }

    @Test
    public void test_lastDayOfYear_nonLeap() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(false); i++) {
                LocalDate date = date(2007, month, i);
                LocalDate test = (LocalDate) TemporalAdjusters.lastDayOfYear().adjustInto(date);
                assertEquals(2007, test.getYear());
                assertEquals(Month.DECEMBER, test.getMonth());
                assertEquals(31, test.getDayOfMonth());
            }
        }
    }

    @Test
    public void test_lastDayOfYear_leap() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(true); i++) {
                LocalDate date = date(2008, month, i);
                LocalDate test = (LocalDate) TemporalAdjusters.lastDayOfYear().adjustInto(date);
                assertEquals(2008, test.getYear());
                assertEquals(Month.DECEMBER, test.getMonth());
                assertEquals(31, test.getDayOfMonth());
            }
        }
    }

    //-----------------------------------------------------------------------
    // firstDayOfNextYear()
    //-----------------------------------------------------------------------
    @Test
    public void factory_firstDayOfNextYear() {
        assertNotNull(TemporalAdjusters.firstDayOfNextYear());
    }

    @Test
    public void test_firstDayOfNextYear_nonLeap() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(false); i++) {
                LocalDate date = date(2007, month, i);
                LocalDate test = (LocalDate) TemporalAdjusters.firstDayOfNextYear().adjustInto(date);
                assertEquals(2008, test.getYear());
                assertEquals(JANUARY, test.getMonth());
                assertEquals(1, test.getDayOfMonth());
            }
        }
    }

    @Test
    public void test_firstDayOfNextYear_leap() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(true); i++) {
                LocalDate date = date(2008, month, i);
                LocalDate test = (LocalDate) TemporalAdjusters.firstDayOfNextYear().adjustInto(date);
                assertEquals(2009, test.getYear());
                assertEquals(JANUARY, test.getMonth());
                assertEquals(1, test.getDayOfMonth());
            }
        }
    }

    //-----------------------------------------------------------------------
    // dayOfWeekInMonth()
    //-----------------------------------------------------------------------
    @Test
    public void factory_dayOfWeekInMonth() {
        assertNotNull(TemporalAdjusters.dayOfWeekInMonth(1, MONDAY));
    }

    @Test
    public void factory_dayOfWeekInMonth_nullDayOfWeek() {
        Assertions.assertThrows(NullPointerException.class, () -> TemporalAdjusters.dayOfWeekInMonth(1, null));
    }

    Object[][] data_dayOfWeekInMonth_positive() {
        return new Object[][] {
            {2011, 1, TUESDAY, date(2011, 1, 4)},
            {2011, 2, TUESDAY, date(2011, 2, 1)},
            {2011, 3, TUESDAY, date(2011, 3, 1)},
            {2011, 4, TUESDAY, date(2011, 4, 5)},
            {2011, 5, TUESDAY, date(2011, 5, 3)},
            {2011, 6, TUESDAY, date(2011, 6, 7)},
            {2011, 7, TUESDAY, date(2011, 7, 5)},
            {2011, 8, TUESDAY, date(2011, 8, 2)},
            {2011, 9, TUESDAY, date(2011, 9, 6)},
            {2011, 10, TUESDAY, date(2011, 10, 4)},
            {2011, 11, TUESDAY, date(2011, 11, 1)},
            {2011, 12, TUESDAY, date(2011, 12, 6)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_dayOfWeekInMonth_positive")
    public void test_dayOfWeekInMonth_positive(int year, int month, DayOfWeek dow, LocalDate expected) {
        for (int ordinal = 1; ordinal <= 5; ordinal++) {
            for (int day = 1; day <= Month.of(month).length(false); day++) {
                LocalDate date = date(year, month, day);
                LocalDate test = (LocalDate) TemporalAdjusters.dayOfWeekInMonth(ordinal, dow).adjustInto(date);
                assertEquals(expected.plusWeeks(ordinal - 1), test);
            }
        }
    }

    Object[][] data_dayOfWeekInMonth_zero() {
        return new Object[][] {
            {2011, 1, TUESDAY, date(2010, 12, 28)},
            {2011, 2, TUESDAY, date(2011, 1, 25)},
            {2011, 3, TUESDAY, date(2011, 2, 22)},
            {2011, 4, TUESDAY, date(2011, 3, 29)},
            {2011, 5, TUESDAY, date(2011, 4, 26)},
            {2011, 6, TUESDAY, date(2011, 5, 31)},
            {2011, 7, TUESDAY, date(2011, 6, 28)},
            {2011, 8, TUESDAY, date(2011, 7, 26)},
            {2011, 9, TUESDAY, date(2011, 8, 30)},
            {2011, 10, TUESDAY, date(2011, 9, 27)},
            {2011, 11, TUESDAY, date(2011, 10, 25)},
            {2011, 12, TUESDAY, date(2011, 11, 29)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_dayOfWeekInMonth_zero")
    public void test_dayOfWeekInMonth_zero(int year, int month, DayOfWeek dow, LocalDate expected) {
        for (int day = 1; day <= Month.of(month).length(false); day++) {
            LocalDate date = date(year, month, day);
            LocalDate test = (LocalDate) TemporalAdjusters.dayOfWeekInMonth(0, dow).adjustInto(date);
            assertEquals(expected, test);
        }
    }

    Object[][] data_dayOfWeekInMonth_negative() {
        return new Object[][] {
            {2011, 1, TUESDAY, date(2011, 1, 25)},
            {2011, 2, TUESDAY, date(2011, 2, 22)},
            {2011, 3, TUESDAY, date(2011, 3, 29)},
            {2011, 4, TUESDAY, date(2011, 4, 26)},
            {2011, 5, TUESDAY, date(2011, 5, 31)},
            {2011, 6, TUESDAY, date(2011, 6, 28)},
            {2011, 7, TUESDAY, date(2011, 7, 26)},
            {2011, 8, TUESDAY, date(2011, 8, 30)},
            {2011, 9, TUESDAY, date(2011, 9, 27)},
            {2011, 10, TUESDAY, date(2011, 10, 25)},
            {2011, 11, TUESDAY, date(2011, 11, 29)},
            {2011, 12, TUESDAY, date(2011, 12, 27)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_dayOfWeekInMonth_negative")
    public void test_dayOfWeekInMonth_negative(int year, int month, DayOfWeek dow, LocalDate expected) {
        for (int ordinal = 0; ordinal < 5; ordinal++) {
            for (int day = 1; day <= Month.of(month).length(false); day++) {
                LocalDate date = date(year, month, day);
                LocalDate test = (LocalDate) TemporalAdjusters.dayOfWeekInMonth(-1 - ordinal, dow).adjustInto(date);
                assertEquals(expected.minusWeeks(ordinal), test);
            }
        }
    }

    //-----------------------------------------------------------------------
    // firstInMonth()
    //-----------------------------------------------------------------------
    @Test
    public void factory_firstInMonth() {
        assertNotNull(TemporalAdjusters.firstInMonth(MONDAY));
    }

    @Test
    public void factory_firstInMonth_nullDayOfWeek() {
        Assertions.assertThrows(NullPointerException.class, () -> TemporalAdjusters.firstInMonth(null));
    }

    @ParameterizedTest
    @MethodSource("data_dayOfWeekInMonth_positive")
    public void test_firstInMonth(int year, int month, DayOfWeek dow, LocalDate expected) {
        for (int day = 1; day <= Month.of(month).length(false); day++) {
            LocalDate date = date(year, month, day);
            LocalDate test = (LocalDate) TemporalAdjusters.firstInMonth(dow).adjustInto(date);
            assertEquals(expected, test, "day-of-month=" + day);
        }
    }

    //-----------------------------------------------------------------------
    // lastInMonth()
    //-----------------------------------------------------------------------
    @Test
    public void factory_lastInMonth() {
        assertNotNull(TemporalAdjusters.lastInMonth(MONDAY));
    }

    @Test
    public void factory_lastInMonth_nullDayOfWeek() {
        Assertions.assertThrows(NullPointerException.class, () -> TemporalAdjusters.lastInMonth(null));
    }

    @ParameterizedTest
    @MethodSource("data_dayOfWeekInMonth_negative")
    public void test_lastInMonth(int year, int month, DayOfWeek dow, LocalDate expected) {
        for (int day = 1; day <= Month.of(month).length(false); day++) {
            LocalDate date = date(year, month, day);
            LocalDate test = (LocalDate) TemporalAdjusters.lastInMonth(dow).adjustInto(date);
            assertEquals(expected, test, "day-of-month=" + day);
        }
    }

    //-----------------------------------------------------------------------
    // next()
    //-----------------------------------------------------------------------
    @Test
    public void factory_next() {
        assertNotNull(TemporalAdjusters.next(MONDAY));
    }

    @Test
    public void factory_next_nullDayOfWeek() {
        Assertions.assertThrows(NullPointerException.class, () -> TemporalAdjusters.next(null));
    }

    @Test
    public void test_next() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(false); i++) {
                LocalDate date = date(2007, month, i);

                for (DayOfWeek dow : DayOfWeek.values()) {
                    LocalDate test = (LocalDate) TemporalAdjusters.next(dow).adjustInto(date);

                    assertSame(test.getDayOfWeek(), dow, date + " " + test);

                    if (test.getYear() == 2007) {
                        int dayDiff = test.getDayOfYear() - date.getDayOfYear();
                        assertTrue(dayDiff > 0 && dayDiff < 8);
                    } else {
                        assertSame(month, Month.DECEMBER);
                        assertTrue(date.getDayOfMonth() > 24);
                        assertEquals(2008, test.getYear());
                        assertSame(test.getMonth(), Month.JANUARY);
                        assertTrue(test.getDayOfMonth() < 8);
                    }
                }
            }
        }
    }

    //-----------------------------------------------------------------------
    // nextOrSame()
    //-----------------------------------------------------------------------
    @Test
    public void factory_nextOrCurrent() {
        assertNotNull(TemporalAdjusters.nextOrSame(MONDAY));
    }

    @Test
    public void factory_nextOrCurrent_nullDayOfWeek() {
        Assertions.assertThrows(NullPointerException.class, () -> TemporalAdjusters.nextOrSame(null));
    }

    @Test
    public void test_nextOrCurrent() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(false); i++) {
                LocalDate date = date(2007, month, i);

                for (DayOfWeek dow : DayOfWeek.values()) {
                    LocalDate test = (LocalDate) TemporalAdjusters.nextOrSame(dow).adjustInto(date);

                    assertSame(test.getDayOfWeek(), dow);

                    if (test.getYear() == 2007) {
                        int dayDiff = test.getDayOfYear() - date.getDayOfYear();
                        assertTrue(dayDiff < 8);
                        assertEquals(date.getDayOfWeek() == dow, date.equals(test));
                    } else {
                        assertFalse(date.getDayOfWeek() == dow);
                        assertSame(month, Month.DECEMBER);
                        assertTrue(date.getDayOfMonth() > 24);
                        assertEquals(2008, test.getYear());
                        assertSame(test.getMonth(), Month.JANUARY);
                        assertTrue(test.getDayOfMonth() < 8);
                    }
                }
            }
        }
    }

    //-----------------------------------------------------------------------
    // previous()
    //-----------------------------------------------------------------------
    @Test
    public void factory_previous() {
        assertNotNull(TemporalAdjusters.previous(MONDAY));
    }

    @Test
    public void factory_previous_nullDayOfWeek() {
        Assertions.assertThrows(NullPointerException.class, () -> TemporalAdjusters.previous(null));
    }

    @Test
    public void test_previous() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(false); i++) {
                LocalDate date = date(2007, month, i);

                for (DayOfWeek dow : DayOfWeek.values()) {
                    LocalDate test = (LocalDate) TemporalAdjusters.previous(dow).adjustInto(date);

                    assertSame(test.getDayOfWeek(), dow, date + " " + test);

                    if (test.getYear() == 2007) {
                        int dayDiff = test.getDayOfYear() - date.getDayOfYear();
                        assertTrue(dayDiff < 0 && dayDiff > -8, dayDiff + " " + test);
                    } else {
                        assertSame(month, Month.JANUARY);
                        assertTrue(date.getDayOfMonth() < 8);
                        assertEquals(2006, test.getYear());
                        assertSame(test.getMonth(), Month.DECEMBER);
                        assertTrue(test.getDayOfMonth() > 24);
                    }
                }
            }
        }
    }

    //-----------------------------------------------------------------------
    // previousOrSame()
    //-----------------------------------------------------------------------
    @Test
    public void factory_previousOrCurrent() {
        assertNotNull(TemporalAdjusters.previousOrSame(MONDAY));
    }

    @Test
    public void factory_previousOrCurrent_nullDayOfWeek() {
        Assertions.assertThrows(NullPointerException.class, () -> TemporalAdjusters.previousOrSame(null));
    }

    @Test
    public void test_previousOrCurrent() {
        for (Month month : Month.values()) {
            for (int i = 1; i <= month.length(false); i++) {
                LocalDate date = date(2007, month, i);

                for (DayOfWeek dow : DayOfWeek.values()) {
                    LocalDate test = (LocalDate) TemporalAdjusters.previousOrSame(dow).adjustInto(date);

                    assertSame(test.getDayOfWeek(), dow);

                    if (test.getYear() == 2007) {
                        int dayDiff = test.getDayOfYear() - date.getDayOfYear();
                        assertTrue(dayDiff <= 0 && dayDiff > -7);
                        assertEquals(date.getDayOfWeek() == dow, date.equals(test));
                    } else {
                        assertFalse(date.getDayOfWeek() == dow);
                        assertSame(month, Month.JANUARY);
                        assertTrue(date.getDayOfMonth() < 7);
                        assertEquals(2006, test.getYear());
                        assertSame(test.getMonth(), Month.DECEMBER);
                        assertTrue(test.getDayOfMonth() > 25);
                    }
                }
            }
        }
    }

    private LocalDate date(int year, Month month, int day) {
        return LocalDate.of(year, month, day);
    }

    private LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

}
