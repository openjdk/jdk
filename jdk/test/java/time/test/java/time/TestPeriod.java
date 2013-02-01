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
package test.java.time;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HALF_DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MICROS;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.MONTHS;
import static java.time.temporal.ChronoUnit.NANOS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.time.temporal.ChronoUnit.YEARS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.Period;
import java.time.temporal.YearMonth;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test.
 */
@Test
public class TestPeriod extends AbstractTest {

    //-----------------------------------------------------------------------
    // basics
    //-----------------------------------------------------------------------
    public void test_interfaces() {
        assertTrue(Serializable.class.isAssignableFrom(Period.class));
    }

    @DataProvider(name="serialization")
    Object[][] data_serialization() {
        return new Object[][] {
            {Period.ZERO},
            {Period.of(0, DAYS)},
            {Period.of(1, DAYS)},
            {Period.of(1, 2, 3, 4, 5, 6)},
        };
    }

    @Test(dataProvider="serialization")
    public void test_serialization(Period period) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(period);
        oos.close();

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(
                baos.toByteArray()));
        if (period.isZero()) {
            assertSame(ois.readObject(), period);
        } else {
            assertEquals(ois.readObject(), period);
        }
    }

    @Test
    public void test_immutable() {
        assertImmutable(Period.class);
    }

    //-----------------------------------------------------------------------
    // factories
    //-----------------------------------------------------------------------
    public void factory_zeroSingleton() {
        assertSame(Period.ZERO, Period.ZERO);
        assertSame(Period.of(0, 0, 0, 0, 0, 0), Period.ZERO);
        assertSame(Period.of(0, 0, 0, 0, 0, 0, 0), Period.ZERO);
        assertSame(Period.ofDate(0, 0, 0), Period.ZERO);
        assertSame(Period.ofTime(0, 0, 0), Period.ZERO);
        assertSame(Period.ofTime(0, 0, 0, 0), Period.ZERO);
        assertSame(Period.of(0, YEARS), Period.ZERO);
        assertSame(Period.of(0, MONTHS), Period.ZERO);
        assertSame(Period.of(0, DAYS), Period.ZERO);
        assertSame(Period.of(0, HOURS), Period.ZERO);
        assertSame(Period.of(0, MINUTES), Period.ZERO);
        assertSame(Period.of(0, SECONDS), Period.ZERO);
        assertSame(Period.of(0, NANOS), Period.ZERO);
    }

    //-----------------------------------------------------------------------
    // of(PeriodProvider)
    //-----------------------------------------------------------------------
    public void factory_of_ints() {
        assertPeriod(Period.of(1, 2, 3, 4, 5, 6), 1, 2, 3, 4, 5, 6, 0);
        assertPeriod(Period.of(0, 2, 3, 4, 5, 6), 0, 2, 3, 4, 5, 6, 0);
        assertPeriod(Period.of(1, 0, 0, 0, 0, 0), 1, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(0, 0, 0, 0, 0, 0), 0, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(-1, -2, -3, -4, -5, -6), -1, -2, -3, -4, -5, -6, 0);
    }

    //-----------------------------------------------------------------------
    // ofDate
    //-----------------------------------------------------------------------
    public void factory_ofDate_ints() {
        assertPeriod(Period.ofDate(1, 2, 3), 1, 2, 3, 0, 0, 0, 0);
        assertPeriod(Period.ofDate(0, 2, 3), 0, 2, 3, 0, 0, 0, 0);
        assertPeriod(Period.ofDate(1, 0, 0), 1, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.ofDate(0, 0, 0), 0, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.ofDate(-1, -2, -3), -1, -2, -3, 0, 0, 0, 0);
    }

    //-----------------------------------------------------------------------
    // ofTime
    //-----------------------------------------------------------------------
    public void factory_ofTime_3ints() {
        assertPeriod(Period.ofTime(1, 2, 3), 0, 0, 0, 1, 2, 3, 0);
        assertPeriod(Period.ofTime(0, 2, 3), 0, 0, 0, 0, 2, 3, 0);
        assertPeriod(Period.ofTime(1, 0, 0), 0, 0, 0, 1, 0, 0, 0);
        assertPeriod(Period.ofTime(0, 0, 0), 0, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.ofTime(-1, -2, -3), 0, 0, 0, -1, -2, -3, 0);
    }

    public void factory_ofTime_4ints() {
        assertPeriod(Period.ofTime(1, 2, 3, 4), 0, 0, 0, 1, 2, 3, 4);
        assertPeriod(Period.ofTime(0, 2, 3, 4), 0, 0, 0, 0, 2, 3, 4);
        assertPeriod(Period.ofTime(1, 0, 0, 0), 0, 0, 0, 1, 0, 0, 0);
        assertPeriod(Period.ofTime(0, 0, 0, 0), 0, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.ofTime(-1, -2, -3, -4), 0, 0, 0, -1, -2, -3, -4);
    }

    //-----------------------------------------------------------------------
    // of one field
    //-----------------------------------------------------------------------
    public void test_factory_of_intPeriodUnit() {
        assertEquals(Period.of(1, YEARS), Period.of(1, YEARS));
        assertEquals(Period.of(2, MONTHS), Period.of(2, MONTHS));
        assertEquals(Period.of(3, DAYS), Period.of(3, DAYS));

        assertEquals(Period.of(1, HALF_DAYS), Period.of(12, HOURS));
        assertEquals(Period.of(Integer.MAX_VALUE / (3600 * 8), HOURS), Period.of(Integer.MAX_VALUE / (3600 * 8), HOURS));
        assertEquals(Period.of(-1, MINUTES), Period.of(-1, MINUTES));
        assertEquals(Period.of(-2, SECONDS), Period.of(-2, SECONDS));
        assertEquals(Period.of(Integer.MIN_VALUE, NANOS), Period.of(Integer.MIN_VALUE, NANOS));
        assertEquals(Period.of(2, MILLIS), Period.of(2000000, NANOS));
        assertEquals(Period.of(2, MICROS), Period.of(2000, NANOS));
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_factory_of_intPeriodUnit_null() {
        Period.of(1, null);
    }

    //-----------------------------------------------------------------------
    public void factory_years() {
        assertPeriod(Period.of(1, YEARS), 1, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(0, YEARS), 0, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(-1, YEARS), -1, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(Integer.MAX_VALUE, YEARS), Integer.MAX_VALUE, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(Integer.MIN_VALUE, YEARS), Integer.MIN_VALUE, 0, 0, 0, 0, 0, 0);
    }

    public void factory_months() {
        assertPeriod(Period.of(1, MONTHS), 0, 1, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(0, MONTHS), 0, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(-1, MONTHS), 0, -1, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(Integer.MAX_VALUE, MONTHS), 0, Integer.MAX_VALUE, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(Integer.MIN_VALUE, MONTHS), 0, Integer.MIN_VALUE, 0, 0, 0, 0, 0);
    }

    public void factory_days() {
        assertPeriod(Period.of(1, DAYS), 0, 0, 1, 0, 0, 0, 0);
        assertPeriod(Period.of(0, DAYS), 0, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(-1, DAYS), 0, 0, -1, 0, 0, 0, 0);
        assertPeriod(Period.of(Integer.MAX_VALUE, DAYS), 0, 0, Integer.MAX_VALUE, 0, 0, 0, 0);
        assertPeriod(Period.of(Integer.MIN_VALUE, DAYS), 0, 0, Integer.MIN_VALUE, 0, 0, 0, 0);
    }

    public void factory_hours() {
        assertPeriod(Period.of(1, HOURS), 0, 0, 0, 1, 0, 0, 0);
        assertPeriod(Period.of(0, HOURS), 0, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(-1, HOURS), 0, 0, 0, -1, 0, 0, 0);
        assertPeriod(Period.of(Integer.MAX_VALUE / (3600 * 8), HOURS), 0, 0, 0, Integer.MAX_VALUE / (3600 * 8), 0, 0, 0);
        assertPeriod(Period.of(Integer.MIN_VALUE / (3600 * 8), HOURS), 0, 0, 0, Integer.MIN_VALUE / (3600 * 8), 0, 0, 0);
    }

    public void factory_minutes() {
        assertPeriod(Period.of(1, MINUTES), 0, 0, 0, 0, 1, 0, 0);
        assertPeriod(Period.of(0, MINUTES), 0, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(-1, MINUTES), 0, 0, 0, 0, -1, 0, 0);
        int val = Integer.MAX_VALUE / (60 * 8);
        assertPeriod(Period.of(val, MINUTES), 0, 0, 0,
                        (int) (val / 60L),
                        (int) (val % 60),
                        0, 0);
        val = Integer.MIN_VALUE / (60 * 8);
        assertPeriod(Period.of(val, MINUTES), 0, 0, 0,
                        (int) (val / 60L),
                        (int) (val % 60),
                        0, 0);
    }

    public void factory_seconds() {
        assertPeriod(Period.of(1, SECONDS), 0, 0, 0, 0, 0, 1, 0);
        assertPeriod(Period.of(0, SECONDS), 0, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(-1, SECONDS), 0, 0, 0, 0, 0, -1, 0);
        assertPeriod(Period.of(Integer.MAX_VALUE, SECONDS), 0, 0, 0,
                        (int) (Integer.MAX_VALUE / 3600L),
                        (int) ((Integer.MAX_VALUE / 60L) % 60),
                        (int) (Integer.MAX_VALUE % 60),
                        0);
        assertPeriod(Period.of(Integer.MIN_VALUE, SECONDS), 0, 0, 0,
                        (int) (Integer.MIN_VALUE / 3600L),
                        (int) ((Integer.MIN_VALUE / 60L) % 60),
                        (int) (Integer.MIN_VALUE % 60),
                        0);
    }

    public void factory_nanos() {
        assertPeriod(Period.of(1, NANOS), 0, 0, 0, 0, 0, 0, 1);
        assertPeriod(Period.of(0, NANOS), 0, 0, 0, 0, 0, 0, 0);
        assertPeriod(Period.of(-1, NANOS), 0, 0, 0, 0, 0, 0, -1);
        assertPeriod(Period.of(Long.MAX_VALUE, NANOS), 0, 0, 0,
                        (int) (Long.MAX_VALUE / 3600_000_000_000L),
                        (int) ((Long.MAX_VALUE / 60_000_000_000L) % 60),
                        (int) ((Long.MAX_VALUE / 1_000_000_000L) % 60),
                        Long.MAX_VALUE % 1_000_000_000L);
        assertPeriod(Period.of(Long.MIN_VALUE, NANOS), 0, 0, 0,
                        (int) (Long.MIN_VALUE / 3600_000_000_000L),
                        (int) ((Long.MIN_VALUE / 60_000_000_000L) % 60),
                        (int) ((Long.MIN_VALUE / 1_000_000_000L) % 60),
                        Long.MIN_VALUE % 1_000_000_000L);
    }

    //-----------------------------------------------------------------------
    // of(Duration)
    //-----------------------------------------------------------------------
    public void factory_duration() {
        assertPeriod(Period.of(Duration.ofSeconds(2, 3)), 0, 0, 0, 0, 0, 2, 3);
        assertPeriod(Period.of(Duration.ofSeconds(59, 3)), 0, 0, 0, 0, 0, 59, 3);
        assertPeriod(Period.of(Duration.ofSeconds(60, 3)), 0, 0, 0, 0, 1, 0, 3);
        assertPeriod(Period.of(Duration.ofSeconds(61, 3)), 0, 0, 0, 0, 1, 1, 3);
        assertPeriod(Period.of(Duration.ofSeconds(3599, 3)), 0, 0, 0, 0, 59, 59, 3);
        assertPeriod(Period.of(Duration.ofSeconds(3600, 3)), 0, 0, 0, 1, 0, 0, 3);
    }

    public void factory_duration_negative() {
        assertPeriod(Period.of(Duration.ofSeconds(-2, 3)), 0, 0, 0, 0, 0, -1, -999999997);
        assertPeriod(Period.of(Duration.ofSeconds(-59, 3)), 0, 0, 0, 0, 0, -58, -999999997);
        assertPeriod(Period.of(Duration.ofSeconds(-60, 3)), 0, 0, 0, 0, 0, -59, -999999997);
        assertPeriod(Period.of(Duration.ofSeconds(-60, -3)), 0, 0, 0, 0, -1, 0, -3);

        assertPeriod(Period.of(Duration.ofSeconds(2, -3)), 0, 0, 0, 0, 0, 1, 999999997);
    }

    public void factory_duration_big() {
        Duration dur = Duration.ofSeconds(Integer.MAX_VALUE, 3);
        long secs = Integer.MAX_VALUE;
        assertPeriod(Period.of(dur), 0, 0, 0, (int) (secs / 3600), (int) ((secs % 3600) / 60), (int) (secs % 60), 3);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void factory_duration_null() {
        Period.of((Duration) null);
    }

    //-----------------------------------------------------------------------
    // between
    //-----------------------------------------------------------------------
    @DataProvider(name="betweenDates")
    Object[][] data_betweenDates() {
        return new Object[][] {
            {2010, 1, 1, 2010, 1, 1,  0, 0, 0},
            {2010, 1, 1, 2010, 1, 2,  0, 0, 1},
            {2010, 1, 1, 2010, 2, 1,  0, 1, 0},
            {2010, 1, 1, 2010, 2, 2,  0, 1, 1},
            {2010, 1, 1, 2011, 1, 1,  1, 0, 0},

            {2010, 6, 12, 2010, 1, 1,  0, -5, -11},
            {2010, 6, 12, 2010, 1, 2,  0, -5, -10},
            {2010, 6, 12, 2010, 2, 1,  0, -4, -11},
            {2010, 6, 12, 2010, 9, 24,  0, 3, 12},

            {2010, 6, 12, 2009, 1, 1,  -1, -5, -11},
            {2010, 6, 12, 2009, 1, 2,  -1, -5, -10},
            {2010, 6, 12, 2009, 2, 1,  -1, -4, -11},
            {2010, 6, 12, 2009, 9, 24,  0, -9, 12},

            {2010, 6, 12, 2008, 1, 1,  -2, -5, -11},
            {2010, 6, 12, 2008, 1, 2,  -2, -5, -10},
            {2010, 6, 12, 2008, 2, 1,  -2, -4, -11},
            {2010, 6, 12, 2008, 9, 24,  -1, -9, 12},
        };
    }

    @Test(dataProvider="betweenDates")
    public void factory_between_LocalDate(int y1, int m1, int d1, int y2, int m2, int d2, int ye, int me, int de) {
        LocalDate start = LocalDate.of(y1, m1, d1);
        LocalDate end = LocalDate.of(y2, m2, d2);
        Period test = Period.between(start, end);
        assertPeriod(test, ye, me, de, 0, 0, 0, 0);
        //assertEquals(start.plus(test), end);
    }

    @DataProvider(name="betweenTimes")
    Object[][] data_betweenTimes() {
        return new Object[][] {
            {12, 30, 40, 12, 30, 45,  0, 0, 5},
            {12, 30, 40, 12, 35, 40,  0, 5, 0},
            {12, 30, 40, 13, 30, 40,  1, 0, 0},

            {12, 30, 40, 12, 30, 35,  0, 0, -5},
            {12, 30, 40, 12, 25, 40,  0, -5, 0},
            {12, 30, 40, 11, 30, 40,  -1, 0, 0},
        };
    }

    @Test(dataProvider="betweenTimes")
    public void factory_between_LocalTime(int h1, int m1, int s1, int h2, int m2, int s2, int he, int me, int se) {
        LocalTime start = LocalTime.of(h1, m1, s1);
        LocalTime end = LocalTime.of(h2, m2, s2);
        Period test = Period.between(start, end);
        assertPeriod(test, 0, 0, 0, he, me, se, 0);
        //assertEquals(start.plus(test), end);
    }

    public void factory_between_YearMonth() {
        assertPeriod(Period.between(YearMonth.of(2012, 6), YearMonth.of(2013, 7)), 1, 1, 0, 0, 0, 0, 0);
        assertPeriod(Period.between(YearMonth.of(2012, 6), YearMonth.of(2013, 3)), 0, 9, 0, 0, 0, 0, 0);
        assertPeriod(Period.between(YearMonth.of(2012, 6), YearMonth.of(2011, 7)), 0, -11, 0, 0, 0, 0, 0);
    }

    public void factory_between_Month() {
        assertPeriod(Period.between(Month.FEBRUARY, Month.MAY), 0, 3, 0, 0, 0, 0, 0);
        assertPeriod(Period.between(Month.NOVEMBER, Month.MAY), 0, -6, 0, 0, 0, 0, 0);
    }

    //-----------------------------------------------------------------------
    // betweenISO
    //-----------------------------------------------------------------------
    @DataProvider(name="betweenISO")
    Object[][] data_betweenISO() {
        return new Object[][] {
            {2010, 1, 1, 2010, 1, 1, 0, 0, 0},
            {2010, 1, 1, 2010, 1, 2, 0, 0, 1},
            {2010, 1, 1, 2010, 1, 31, 0, 0, 30},
            {2010, 1, 1, 2010, 2, 1, 0, 1, 0},
            {2010, 1, 1, 2010, 2, 28, 0, 1, 27},
            {2010, 1, 1, 2010, 3, 1, 0, 2, 0},
            {2010, 1, 1, 2010, 12, 31, 0, 11, 30},
            {2010, 1, 1, 2011, 1, 1, 1, 0, 0},
            {2010, 1, 1, 2011, 12, 31, 1, 11, 30},
            {2010, 1, 1, 2012, 1, 1, 2, 0, 0},

            {2010, 1, 10, 2010, 1, 1, 0, 0, -9},
            {2010, 1, 10, 2010, 1, 2, 0, 0, -8},
            {2010, 1, 10, 2010, 1, 9, 0, 0, -1},
            {2010, 1, 10, 2010, 1, 10, 0, 0, 0},
            {2010, 1, 10, 2010, 1, 11, 0, 0, 1},
            {2010, 1, 10, 2010, 1, 31, 0, 0, 21},
            {2010, 1, 10, 2010, 2, 1, 0, 0, 22},
            {2010, 1, 10, 2010, 2, 9, 0, 0, 30},
            {2010, 1, 10, 2010, 2, 10, 0, 1, 0},
            {2010, 1, 10, 2010, 2, 28, 0, 1, 18},
            {2010, 1, 10, 2010, 3, 1, 0, 1, 19},
            {2010, 1, 10, 2010, 3, 9, 0, 1, 27},
            {2010, 1, 10, 2010, 3, 10, 0, 2, 0},
            {2010, 1, 10, 2010, 12, 31, 0, 11, 21},
            {2010, 1, 10, 2011, 1, 1, 0, 11, 22},
            {2010, 1, 10, 2011, 1, 9, 0, 11, 30},
            {2010, 1, 10, 2011, 1, 10, 1, 0, 0},

            {2010, 3, 30, 2011, 5, 1, 1, 1, 1},
            {2010, 4, 30, 2011, 5, 1, 1, 0, 1},

            {2010, 2, 28, 2012, 2, 27, 1, 11, 30},
            {2010, 2, 28, 2012, 2, 28, 2, 0, 0},
            {2010, 2, 28, 2012, 2, 29, 2, 0, 1},

            {2012, 2, 28, 2014, 2, 27, 1, 11, 30},
            {2012, 2, 28, 2014, 2, 28, 2, 0, 0},
            {2012, 2, 28, 2014, 3, 1, 2, 0, 1},

            {2012, 2, 29, 2014, 2, 28, 1, 11, 30},
            {2012, 2, 29, 2014, 3, 1, 2, 0, 1},
            {2012, 2, 29, 2014, 3, 2, 2, 0, 2},

            {2012, 2, 29, 2016, 2, 28, 3, 11, 30},
            {2012, 2, 29, 2016, 2, 29, 4, 0, 0},
            {2012, 2, 29, 2016, 3, 1, 4, 0, 1},

            {2010, 1, 1, 2009, 12, 31, 0, 0, -1},
            {2010, 1, 1, 2009, 12, 30, 0, 0, -2},
            {2010, 1, 1, 2009, 12, 2, 0, 0, -30},
            {2010, 1, 1, 2009, 12, 1, 0, -1, 0},
            {2010, 1, 1, 2009, 11, 30, 0, -1, -1},
            {2010, 1, 1, 2009, 11, 2, 0, -1, -29},
            {2010, 1, 1, 2009, 11, 1, 0, -2, 0},
            {2010, 1, 1, 2009, 1, 2, 0, -11, -30},
            {2010, 1, 1, 2009, 1, 1, -1, 0, 0},

            {2010, 1, 15, 2010, 1, 15, 0, 0, 0},
            {2010, 1, 15, 2010, 1, 14, 0, 0, -1},
            {2010, 1, 15, 2010, 1, 1, 0, 0, -14},
            {2010, 1, 15, 2009, 12, 31, 0, 0, -15},
            {2010, 1, 15, 2009, 12, 16, 0, 0, -30},
            {2010, 1, 15, 2009, 12, 15, 0, -1, 0},
            {2010, 1, 15, 2009, 12, 14, 0, -1, -1},

            {2010, 2, 28, 2009, 3, 1, 0, -11, -27},
            {2010, 2, 28, 2009, 2, 28, -1, 0, 0},
            {2010, 2, 28, 2009, 2, 27, -1, 0, -1},

            {2010, 2, 28, 2008, 2, 29, -1, -11, -28},
            {2010, 2, 28, 2008, 2, 28, -2, 0, 0},
            {2010, 2, 28, 2008, 2, 27, -2, 0, -1},

            {2012, 2, 29, 2009, 3, 1, -2, -11, -28},
            {2012, 2, 29, 2009, 2, 28, -3, 0, -1},
            {2012, 2, 29, 2009, 2, 27, -3, 0, -2},

            {2012, 2, 29, 2008, 3, 1, -3, -11, -28},
            {2012, 2, 29, 2008, 2, 29, -4, 0, 0},
            {2012, 2, 29, 2008, 2, 28, -4, 0, -1},
        };
    }

    @Test(dataProvider="betweenISO")
    public void factory_betweenISO_LocalDate(int y1, int m1, int d1, int y2, int m2, int d2, int ye, int me, int de) {
        LocalDate start = LocalDate.of(y1, m1, d1);
        LocalDate end = LocalDate.of(y2, m2, d2);
        Period test = Period.betweenISO(start, end);
        assertPeriod(test, ye, me, de, 0, 0, 0, 0);
        //assertEquals(start.plus(test), end);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void factory_betweenISO_LocalDate_nullFirst() {
        Period.betweenISO((LocalDate) null, LocalDate.of(2010, 1, 1));
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void factory_betweenISO_LocalDate_nullSecond() {
        Period.betweenISO(LocalDate.of(2010, 1, 1), (LocalDate) null);
    }

    //-------------------------------------------------------------------------
    @Test(expectedExceptions=NullPointerException.class)
    public void factory_betweenISO_LocalTime_nullFirst() {
        Period.betweenISO((LocalTime) null, LocalTime.of(12, 30));
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void factory_betweenISO_LocalTime_nullSecond() {
        Period.betweenISO(LocalTime.of(12, 30), (LocalTime) null);
    }

    //-----------------------------------------------------------------------
    // parse()
    //-----------------------------------------------------------------------
    @Test(dataProvider="toStringAndParse")
    public void test_parse(Period test, String expected) {
        assertEquals(test, Period.parse(expected));
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_parse_nullText() {
        Period.parse((String) null);
    }

    //-----------------------------------------------------------------------
    // isZero()
    //-----------------------------------------------------------------------
    public void test_isZero() {
        assertEquals(Period.of(1, 2, 3, 4, 5, 6, 7).isZero(), false);
        assertEquals(Period.of(1, 2, 3, 0, 0, 0, 0).isZero(), false);
        assertEquals(Period.of(0, 0, 0, 4, 5, 6, 7).isZero(), false);
        assertEquals(Period.of(1, 0, 0, 0, 0, 0, 0).isZero(), false);
        assertEquals(Period.of(0, 2, 0, 0, 0, 0, 0).isZero(), false);
        assertEquals(Period.of(0, 0, 3, 0, 0, 0, 0).isZero(), false);
        assertEquals(Period.of(0, 0, 0, 4, 0, 0, 0).isZero(), false);
        assertEquals(Period.of(0, 0, 0, 0, 5, 0, 0).isZero(), false);
        assertEquals(Period.of(0, 0, 0, 0, 0, 6, 0).isZero(), false);
        assertEquals(Period.of(0, 0, 0, 0, 0, 0, 7).isZero(), false);
        assertEquals(Period.of(0, 0, 0, 0, 0, 0).isZero(), true);
    }

    //-----------------------------------------------------------------------
    // isPositive()
    //-----------------------------------------------------------------------
    public void test_isPositive() {
        assertEquals(Period.of(1, 2, 3, 4, 5, 6, 7).isPositive(), true);
        assertEquals(Period.of(1, 2, 3, 0, 0, 0, 0).isPositive(), true);
        assertEquals(Period.of(0, 0, 0, 4, 5, 6, 7).isPositive(), true);
        assertEquals(Period.of(1, 0, 0, 0, 0, 0, 0).isPositive(), true);
        assertEquals(Period.of(0, 2, 0, 0, 0, 0, 0).isPositive(), true);
        assertEquals(Period.of(0, 0, 3, 0, 0, 0, 0).isPositive(), true);
        assertEquals(Period.of(0, 0, 0, 4, 0, 0, 0).isPositive(), true);
        assertEquals(Period.of(0, 0, 0, 0, 5, 0, 0).isPositive(), true);
        assertEquals(Period.of(0, 0, 0, 0, 0, 6, 0).isPositive(), true);
        assertEquals(Period.of(0, 0, 0, 0, 0, 0, 7).isPositive(), true);
        assertEquals(Period.of(-1, -2, -3, -4, -5, -6, -7).isPositive(), false);
        assertEquals(Period.of(-1, -2, 3, 4, -5, -6, -7).isPositive(), false);
        assertEquals(Period.of(-1, 0, 0, 0, 0, 0, 0).isPositive(), false);
        assertEquals(Period.of(0, -2, 0, 0, 0, 0, 0).isPositive(), false);
        assertEquals(Period.of(0, 0, -3, 0, 0, 0, 0).isPositive(), false);
        assertEquals(Period.of(0, 0, 0, -4, 0, 0, 0).isPositive(), false);
        assertEquals(Period.of(0, 0, 0, 0, -5, 0, 0).isPositive(), false);
        assertEquals(Period.of(0, 0, 0, 0, 0, -6, 0).isPositive(), false);
        assertEquals(Period.of(0, 0, 0, 0, 0, 0, -7).isPositive(), false);
        assertEquals(Period.of(0, 0, 0, 0, 0, 0).isPositive(), false);
    }

    //-----------------------------------------------------------------------
    // withYears()
    //-----------------------------------------------------------------------
    public void test_withYears() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.withYears(10), 10, 2, 3, 4, 5, 6, 7);
    }

    public void test_withYears_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.withYears(1), test);
    }

    public void test_withYears_toZero() {
        Period test = Period.of(1, YEARS);
        assertSame(test.withYears(0), Period.ZERO);
    }

    //-----------------------------------------------------------------------
    // withMonths()
    //-----------------------------------------------------------------------
    public void test_withMonths() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.withMonths(10), 1, 10, 3, 4, 5, 6, 7);
    }

    public void test_withMonths_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.withMonths(2), test);
    }

    public void test_withMonths_toZero() {
        Period test = Period.of(1, MONTHS);
        assertSame(test.withMonths(0), Period.ZERO);
    }

    //-----------------------------------------------------------------------
    // withDays()
    //-----------------------------------------------------------------------
    public void test_withDays() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.withDays(10), 1, 2, 10, 4, 5, 6, 7);
    }

    public void test_withDays_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.withDays(3), test);
    }

    public void test_withDays_toZero() {
        Period test = Period.of(1, DAYS);
        assertSame(test.withDays(0), Period.ZERO);
    }

    //-----------------------------------------------------------------------
    // withTimeNanos()
    //-----------------------------------------------------------------------
    public void test_withNanos() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.withTimeNanos(10), 1, 2, 3, 0, 0, 0, 10);
    }

    public void test_withNanos_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.withTimeNanos(((4 * 60 + 5) * 60 + 6) * 1_000_000_000L + 7), test);
    }

    public void test_withNanos_toZero() {
        Period test = Period.of(1, NANOS);
        assertSame(test.withTimeNanos(0), Period.ZERO);
    }



    //-----------------------------------------------------------------------
    // plusYears()
    //-----------------------------------------------------------------------
    public void test_plusYears() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.plus(10, YEARS), 11, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.plus(Period.of(10, YEARS)), 11, 2, 3, 4, 5, 6, 7);
    }

    public void test_plusYears_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.plus(0, YEARS), test);
        assertPeriod(test.plus(Period.of(0, YEARS)), 1, 2, 3, 4, 5, 6, 7);
    }

    public void test_plusYears_toZero() {
        Period test = Period.of(-1, YEARS);
        assertSame(test.plus(1, YEARS), Period.ZERO);
        assertSame(test.plus(Period.of(1, YEARS)), Period.ZERO);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_plusYears_overflowTooBig() {
        Period test = Period.of(Integer.MAX_VALUE, YEARS);
        test.plus(1, YEARS);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_plusYears_overflowTooSmall() {
        Period test = Period.of(Integer.MIN_VALUE, YEARS);
        test.plus(-1, YEARS);
    }

    //-----------------------------------------------------------------------
    // plusMonths()
    //-----------------------------------------------------------------------
    public void test_plusMonths() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.plus(10, MONTHS), 1, 12, 3, 4, 5, 6, 7);
        assertPeriod(test.plus(Period.of(10, MONTHS)), 1, 12, 3, 4, 5, 6, 7);
    }

    public void test_plusMonths_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.plus(0, MONTHS), test);
        assertEquals(test.plus(Period.of(0, MONTHS)), test);
    }

    public void test_plusMonths_toZero() {
        Period test = Period.of(-1, MONTHS);
        assertSame(test.plus(1, MONTHS), Period.ZERO);
        assertSame(test.plus(Period.of(1, MONTHS)), Period.ZERO);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_plusMonths_overflowTooBig() {
        Period test = Period.of(Integer.MAX_VALUE, MONTHS);
        test.plus(1, MONTHS);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_plusMonths_overflowTooSmall() {
        Period test = Period.of(Integer.MIN_VALUE, MONTHS);
        test.plus(-1, MONTHS);
    }

    //-----------------------------------------------------------------------
    // plusDays()
    //-----------------------------------------------------------------------
    public void test_plusDays() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.plus(10, DAYS), 1, 2, 13, 4, 5, 6, 7);
    }

    public void test_plusDays_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.plus(0, DAYS), test);
    }

    public void test_plusDays_toZero() {
        Period test = Period.of(-1, DAYS);
        assertSame(test.plus(1, DAYS), Period.ZERO);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_plusDays_overflowTooBig() {
        Period test = Period.of(Integer.MAX_VALUE, DAYS);
        test.plus(1, DAYS);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_plusDays_overflowTooSmall() {
        Period test = Period.of(Integer.MIN_VALUE, DAYS);
        test.plus(-1, DAYS);
    }

    //-----------------------------------------------------------------------
    // plusHours()
    //-----------------------------------------------------------------------
    public void test_plusHours() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.plus(10, HOURS), 1, 2, 3, 14, 5, 6, 7);
    }

    public void test_plusHours_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.plus(0, HOURS), test);
    }

    public void test_plusHours_toZero() {
        Period test = Period.of(-1, HOURS);
        assertSame(test.plus(1, HOURS), Period.ZERO);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_plusHours_overflowTooBig() {
        Period test = Period.of(Integer.MAX_VALUE, HOURS);
        test.plus(1, HOURS);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_plusHours_overflowTooSmall() {
        Period test = Period.of(Integer.MIN_VALUE, HOURS);
        test.plus(-1, HOURS);
    }

    //-----------------------------------------------------------------------
    // plusMinutes()
    //-----------------------------------------------------------------------
    public void test_plusMinutes() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.plus(10, MINUTES), 1, 2, 3, 4, 15, 6, 7);
    }

    public void test_plusMinutes_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.plus(0, MINUTES), test);
    }

    public void test_plusMinutes_toZero() {
        Period test = Period.of(-1, MINUTES);
        assertSame(test.plus(1, MINUTES), Period.ZERO);
    }

    //-----------------------------------------------------------------------
    // plusSeconds()
    //-----------------------------------------------------------------------
    public void test_plusSeconds() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.plus(10, SECONDS), 1, 2, 3, 4, 5, 16, 7);
    }

    public void test_plusSeconds_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.plus(0, SECONDS), test);
    }

    public void test_plusSeconds_toZero() {
        Period test = Period.of(-1, SECONDS);
        assertSame(test.plus(1, SECONDS), Period.ZERO);
    }

    //-----------------------------------------------------------------------
    // plusNanos()
    //-----------------------------------------------------------------------
    public void test_plusNanos() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.plus(10, NANOS), 1, 2, 3, 4, 5, 6, 17);
    }

    public void test_plusNanos_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.plus(0, NANOS), test);
    }

    public void test_plusNanos_toZero() {
        Period test = Period.of(-1, NANOS);
        assertSame(test.plus(1, NANOS), Period.ZERO);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_plusNanos_overflowTooBig() {
        Period test = Period.of(Long.MAX_VALUE, NANOS);
        test.plus(1, NANOS);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_plusNanos_overflowTooSmall() {
        Period test = Period.of(Long.MIN_VALUE, NANOS);
        test.plus(-1, NANOS);
    }

    //-----------------------------------------------------------------------
    // minusYears()
    //-----------------------------------------------------------------------
    public void test_minusYears() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.minus(10, YEARS), -9, 2, 3, 4, 5, 6, 7);
    }

    public void test_minusYears_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.minus(0, YEARS), test);
    }

    public void test_minusYears_toZero() {
        Period test = Period.of(1, YEARS);
        assertSame(test.minus(1, YEARS), Period.ZERO);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_minusYears_overflowTooBig() {
        Period test = Period.of(Integer.MAX_VALUE, YEARS);
        test.minus(-1, YEARS);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_minusYears_overflowTooSmall() {
        Period test = Period.of(Integer.MIN_VALUE, YEARS);
        test.minus(1, YEARS);
    }

    //-----------------------------------------------------------------------
    // minusMonths()
    //-----------------------------------------------------------------------
    public void test_minusMonths() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.minus(10, MONTHS), 1, -8, 3, 4, 5, 6, 7);
    }

    public void test_minusMonths_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.minus(0, MONTHS), test);
    }

    public void test_minusMonths_toZero() {
        Period test = Period.of(1, MONTHS);
        assertSame(test.minus(1, MONTHS), Period.ZERO);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_minusMonths_overflowTooBig() {
        Period test = Period.of(Integer.MAX_VALUE, MONTHS);
        test.minus(-1, MONTHS);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_minusMonths_overflowTooSmall() {
        Period test = Period.of(Integer.MIN_VALUE, MONTHS);
        test.minus(1, MONTHS);
    }

    //-----------------------------------------------------------------------
    // minusDays()
    //-----------------------------------------------------------------------
    public void test_minusDays() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.minus(10, DAYS), 1, 2, -7, 4, 5, 6, 7);
    }

    public void test_minusDays_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.minus(0, DAYS), test);
    }

    public void test_minusDays_toZero() {
        Period test = Period.of(1, DAYS);
        assertSame(test.minus(1, DAYS), Period.ZERO);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_minusDays_overflowTooBig() {
        Period test = Period.of(Integer.MAX_VALUE, DAYS);
        test.minus(-1, DAYS);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_minusDays_overflowTooSmall() {
        Period test = Period.of(Integer.MIN_VALUE, DAYS);
        test.minus(1, DAYS);
    }

    //-----------------------------------------------------------------------
    // minusHours()
    //-----------------------------------------------------------------------
    public void test_minusHours() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.minus(10, HOURS), 1, 2, 3, -5, -54, -53, -999999993);
        assertEquals(test.minus(10, HOURS).plus(10, HOURS), test);
    }

    public void test_minusHours_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.minus(0, HOURS), test);
    }

    public void test_minusHours_toZero() {
        Period test = Period.of(1, HOURS);
        assertSame(test.minus(1, HOURS), Period.ZERO);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_minusHours_overflowTooBig() {
        Period test = Period.of(Integer.MAX_VALUE, HOURS);
        test.minus(-1, HOURS);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_minusHours_overflowTooSmall() {
        Period test = Period.of(Integer.MIN_VALUE, HOURS);
        test.minus(1, HOURS);
    }

    //-----------------------------------------------------------------------
    // minusMinutes()
    //-----------------------------------------------------------------------
    public void test_minusMinutes() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.minus(10, MINUTES), 1, 2, 3, 3, 55, 6, 7);
        assertEquals(test.minus(10, MINUTES).plus(10, MINUTES), test);
    }

    public void test_minusMinutes_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.minus(0, MINUTES), test);
    }

    public void test_minusMinutes_toZero() {
        Period test = Period.of(1, MINUTES);
        assertSame(test.minus(1, MINUTES), Period.ZERO);
    }

    //-----------------------------------------------------------------------
    // minusSeconds()
    //-----------------------------------------------------------------------
    public void test_minusSeconds() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.minus(10, SECONDS), 1, 2, 3, 4, 4, 56, 7);
        assertEquals(test.minus(10, SECONDS).plus(10, SECONDS), test);
    }

    public void test_minusSeconds_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.minus(0, SECONDS), test);
    }

    public void test_minusSeconds_toZero() {
        Period test = Period.of(1, SECONDS);
        assertSame(test.minus(1, SECONDS), Period.ZERO);
    }

    //-----------------------------------------------------------------------
    // minusNanos()
    //-----------------------------------------------------------------------
    public void test_minusNanos() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.minus(10, NANOS), 1, 2, 3, 4, 5, 5, 999999997);
    }

    public void test_minusNanos_noChange() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.minus(0, NANOS), test);
    }

    public void test_minusNanos_toZero() {
        Period test = Period.of(1, NANOS);
        assertSame(test.minus(1, NANOS), Period.ZERO);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_minusNanos_overflowTooBig() {
        Period test = Period.of(Long.MAX_VALUE, NANOS);
        test.minus(-1, NANOS);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_minusNanos_overflowTooSmall() {
        Period test = Period.of(Long.MIN_VALUE, NANOS);
        test.minus(1, NANOS);
    }

    //-----------------------------------------------------------------------
    // multipliedBy()
    //-----------------------------------------------------------------------
    public void test_multipliedBy() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.multipliedBy(2), 2, 4, 6, 8, 10, 12, 14);
        assertPeriod(test.multipliedBy(-3), -3, -6, -9, -12, -15, -18, -21);
    }

    public void test_multipliedBy_zeroBase() {
        assertSame(Period.ZERO.multipliedBy(2), Period.ZERO);
    }

    public void test_multipliedBy_zero() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.multipliedBy(0), Period.ZERO);
    }

    public void test_multipliedBy_one() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertSame(test.multipliedBy(1), test);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_multipliedBy_overflowTooBig() {
        Period test = Period.of(Integer.MAX_VALUE / 2 + 1, YEARS);
        test.multipliedBy(2);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_multipliedBy_overflowTooSmall() {
        Period test = Period.of(Integer.MIN_VALUE / 2 - 1, YEARS);
        test.multipliedBy(2);
    }

    //-----------------------------------------------------------------------
    // negated()
    //-----------------------------------------------------------------------
    public void test_negated() {
        Period test = Period.of(1, 2, 3, 4, 5, 6, 7);
        assertPeriod(test.negated(), -1, -2, -3, -4, -5, -6, -7);
    }

    public void test_negated_zero() {
        assertSame(Period.ZERO.negated(), Period.ZERO);
    }

    public void test_negated_max() {
        assertPeriod(Period.of(Integer.MAX_VALUE, YEARS).negated(), -Integer.MAX_VALUE, 0, 0, 0, 0, 0, 0);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_negated_overflow() {
        Period.of(Integer.MIN_VALUE, YEARS).negated();
    }

    //-----------------------------------------------------------------------
    // normalizedHoursToDays()
    //-----------------------------------------------------------------------
    @DataProvider(name="normalizedHoursToDays")
    Object[][] data_normalizedHoursToDays() {
        return new Object[][] {
            {0, 0,  0, 0},
            {1, 0,  1, 0},
            {-1, 0,  -1, 0},

            {1, 1,  1, 1},
            {1, 23,  1, 23},
            {1, 24,  2, 0},
            {1, 25,  2, 1},

            {1, -1,  0, 23},
            {1, -23,  0, 1},
            {1, -24,  0, 0},
            {1, -25,  0, -1},
            {1, -47,  0, -23},
            {1, -48,  -1, 0},
            {1, -49,  -1, -1},

            {-1, 1,  0, -23},
            {-1, 23,  0, -1},
            {-1, 24,  0, 0},
            {-1, 25,  0, 1},
            {-1, 47,  0, 23},
            {-1, 48,  1, 0},
            {-1, 49,  1, 1},

            {-1, -1,  -1, -1},
            {-1, -23,  -1, -23},
            {-1, -24,  -2, 0},
            {-1, -25,  -2, -1},
        };
    }

    @Test(dataProvider="normalizedHoursToDays")
    public void test_normalizedHoursToDays(int inputDays, int inputHours, int expectedDays, int expectedHours) {
        assertPeriod(Period.of(0, 0, inputDays, inputHours, 0, 0, 0).normalizedHoursToDays(), 0, 0, expectedDays, expectedHours, 0, 0, 0);
    }

    @Test(dataProvider="normalizedHoursToDays")
    public void test_normalizedHoursToDays_yearsMonthsUnaffected(int inputDays, int inputHours, int expectedDays, int expectedHours) {
        assertPeriod(Period.of(12, 6, inputDays, inputHours, 0, 0, 0).normalizedHoursToDays(), 12, 6, expectedDays, expectedHours, 0, 0, 0);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_normalizedHoursToDays_min() {
        Period base = Period.of(0, 0, Integer.MIN_VALUE, -24, 0, 0, 0);
        base.normalizedHoursToDays();
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_normalizedHoursToDays_max() {
        Period base = Period.of(0, 0, Integer.MAX_VALUE, 24, 0, 0, 0);
        base.normalizedHoursToDays();
    }

    //-----------------------------------------------------------------------
    // normalizedDaysToHours()
    //-----------------------------------------------------------------------
    @DataProvider(name="normalizedDaysToHours")
    Object[][] data_normalizedDaysToHours() {
        return new Object[][] {
            {0, 0, 0,  0, 0},

            {1, 0, 0,  24, 0},
            {1, 1, 0,  25, 0},
            {1, 23, 0,  47, 0},
            {1, 24, 0,  48, 0},
            {1, 25, 0,  49, 0},
            {2, 23, 0,  71, 0},
            {2, 24, 0,  72, 0},
            {2, 25, 0,  73, 0},

            {1, 0, 0,  24, 0},
            {1, -1, 0,  23, 0},
            {1, -23, 0,  1, 0},
            {1, -24, 0,  0, 0},
            {1, -25, 0,  -1, 0},
            {2, -23, 0,  25, 0},
            {2, -24, 0,  24, 0},
            {2, -25, 0,  23, 0},

            {-1, 0, 0,  -24, 0},
            {-1, 1, 0,  -23, 0},
            {-1, 23, 0,  -1, 0},
            {-1, 24, 0,  0, 0},
            {-1, 25, 0,  1, 0},
            {-2, 23, 0,  -25, 0},
            {-2, 24, 0,  -24, 0},
            {-2, 25, 0,  -23, 0},

            {-1, 0, 0,  -24, 0},
            {-1, -1, 0,  -25, 0},
            {-1, -23, 0,  -47, 0},
            {-1, -24, 0,  -48, 0},
            {-1, -25, 0,  -49, 0},

            // minutes
            {1, -1, -1,  22, 59},
            {1, -23, -1,  0, 59},
            {1, -24, -1,  0, -1},
            {1, -25, -1,  -1, -1},
            {-1, 1, 1,  -22, -59},
            {-1, 23, 1,  0, -59},
            {-1, 24, 1,  0, 1},
            {-1, 25, 1,  1, 1},
        };
    }

    @Test(dataProvider="normalizedDaysToHours")
    public void test_normalizedDaysToHours(int inputDays, int inputHours, int inputMinutes, int expectedHours, int expectedMinutes) {
        assertPeriod(Period.of(0, 0, inputDays, inputHours, inputMinutes, 0).normalizedDaysToHours(), 0, 0, 0, expectedHours, expectedMinutes, 0, 0);
    }

    @Test(dataProvider="normalizedDaysToHours")
    public void test_normalizedDaysToHours_yearsMonthsUnaffected(int inputDays, int inputHours, int inputMinutes, int expectedHours, int expectedMinutes) {
        assertPeriod(Period.of(12, 6, inputDays, inputHours, inputMinutes, 0).normalizedDaysToHours(), 12, 6, 0, expectedHours, expectedMinutes, 0, 0);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_normalizedDaysToHours_min() {
        Period base = Period.of(0, 0, -1_000, -10_000_000, 0, 0, 0);
        base.normalizedDaysToHours();
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_normalizedDaysToHours_max() {
        Period base = Period.of(0, 0, 1_000, 10_000_000, 0, 0, 0);
        base.normalizedDaysToHours();
    }

    //-----------------------------------------------------------------------
    // normalizedMonthsISO()
    //-----------------------------------------------------------------------
    @DataProvider(name="normalizedMonthsISO")
    Object[][] data_normalizedMonthsISO() {
        return new Object[][] {
            {0, 0,  0, 0},
            {1, 0,  1, 0},
            {-1, 0,  -1, 0},

            {1, 1,  1, 1},
            {1, 2,  1, 2},
            {1, 11,  1, 11},
            {1, 12,  2, 0},
            {1, 13,  2, 1},
            {1, 23,  2, 11},
            {1, 24,  3, 0},
            {1, 25,  3, 1},

            {1, -1,  0, 11},
            {1, -2,  0, 10},
            {1, -11,  0, 1},
            {1, -12,  0, 0},
            {1, -13,  0, -1},
            {1, -23,  0, -11},
            {1, -24,  -1, 0},
            {1, -25,  -1, -1},
            {1, -35,  -1, -11},
            {1, -36,  -2, 0},
            {1, -37,  -2, -1},

            {-1, 1,  0, -11},
            {-1, 11,  0, -1},
            {-1, 12,  0, 0},
            {-1, 13,  0, 1},
            {-1, 23,  0, 11},
            {-1, 24,  1, 0},
            {-1, 25,  1, 1},

            {-1, -1,  -1, -1},
            {-1, -11,  -1, -11},
            {-1, -12,  -2, 0},
            {-1, -13,  -2, -1},
        };
    }

    @Test(dataProvider="normalizedMonthsISO")
    public void test_normalizedMonthsISO(int inputYears, int inputMonths, int expectedYears, int expectedMonths) {
        assertPeriod(Period.ofDate(inputYears, inputMonths, 0).normalizedMonthsISO(), expectedYears, expectedMonths, 0, 0, 0, 0, 0);
    }

    @Test(dataProvider="normalizedMonthsISO")
    public void test_normalizedMonthsISO_daysTimeUnaffected(int inputYears, int inputMonths, int expectedYears, int expectedMonths) {
        assertPeriod(Period.of(inputYears, inputMonths, 5, 12, 30, 0, 0).normalizedMonthsISO(), expectedYears, expectedMonths, 5, 12, 30, 0, 0);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_normalizedMonthsISO_min() {
        Period base = Period.ofDate(Integer.MIN_VALUE, -12, 0);
        base.normalizedMonthsISO();
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_normalizedMonthsISO_max() {
        Period base = Period.ofDate(Integer.MAX_VALUE, 12, 0);
        base.normalizedMonthsISO();
    }

    //-----------------------------------------------------------------------
    // addTo()
    //-----------------------------------------------------------------------
    @DataProvider(name="addTo")
    Object[][] data_addTo() {
        return new Object[][] {
            {pymd(0, 0, 0),  date(2012, 6, 30), date(2012, 6, 30)},

            {pymd(1, 0, 0),  date(2012, 6, 10), date(2013, 6, 10)},
            {pymd(0, 1, 0),  date(2012, 6, 10), date(2012, 7, 10)},
            {pymd(0, 0, 1),  date(2012, 6, 10), date(2012, 6, 11)},

            {pymd(-1, 0, 0),  date(2012, 6, 10), date(2011, 6, 10)},
            {pymd(0, -1, 0),  date(2012, 6, 10), date(2012, 5, 10)},
            {pymd(0, 0, -1),  date(2012, 6, 10), date(2012, 6, 9)},

            {pymd(1, 2, 3),  date(2012, 6, 27), date(2013, 8, 30)},
            {pymd(1, 2, 3),  date(2012, 6, 28), date(2013, 8, 31)},
            {pymd(1, 2, 3),  date(2012, 6, 29), date(2013, 9, 1)},
            {pymd(1, 2, 3),  date(2012, 6, 30), date(2013, 9, 2)},
            {pymd(1, 2, 3),  date(2012, 7, 1), date(2013, 9, 4)},

            {pymd(1, 0, 0),  date(2011, 2, 28), date(2012, 2, 28)},
            {pymd(4, 0, 0),  date(2011, 2, 28), date(2015, 2, 28)},
            {pymd(1, 0, 0),  date(2012, 2, 29), date(2013, 2, 28)},
            {pymd(4, 0, 0),  date(2012, 2, 29), date(2016, 2, 29)},

            {pymd(1, 1, 0),  date(2011, 1, 29), date(2012, 2, 29)},
            {pymd(1, 2, 0),  date(2012, 2, 29), date(2013, 4, 29)},
        };
    }

    @Test(dataProvider="addTo")
    public void test_addTo(Period period, LocalDate baseDate, LocalDate expected) {
        assertEquals(period.addTo(baseDate), expected);
    }

    @Test(dataProvider="addTo")
    public void test_addTo_usingLocalDatePlus(Period period, LocalDate baseDate, LocalDate expected) {
        assertEquals(baseDate.plus(period), expected);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_addTo_nullZero() {
        Period.ZERO.addTo(null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_addTo_nullNonZero() {
        Period.of(2, DAYS).addTo(null);
    }

    //-----------------------------------------------------------------------
    // subtractFrom()
    //-----------------------------------------------------------------------
    @DataProvider(name="subtractFrom")
    Object[][] data_subtractFrom() {
        return new Object[][] {
            {pymd(0, 0, 0),  date(2012, 6, 30), date(2012, 6, 30)},

            {pymd(1, 0, 0),  date(2012, 6, 10), date(2011, 6, 10)},
            {pymd(0, 1, 0),  date(2012, 6, 10), date(2012, 5, 10)},
            {pymd(0, 0, 1),  date(2012, 6, 10), date(2012, 6, 9)},

            {pymd(-1, 0, 0),  date(2012, 6, 10), date(2013, 6, 10)},
            {pymd(0, -1, 0),  date(2012, 6, 10), date(2012, 7, 10)},
            {pymd(0, 0, -1),  date(2012, 6, 10), date(2012, 6, 11)},

            {pymd(1, 2, 3),  date(2012, 8, 30), date(2011, 6, 27)},
            {pymd(1, 2, 3),  date(2012, 8, 31), date(2011, 6, 27)},
            {pymd(1, 2, 3),  date(2012, 9, 1), date(2011, 6, 28)},
            {pymd(1, 2, 3),  date(2012, 9, 2), date(2011, 6, 29)},
            {pymd(1, 2, 3),  date(2012, 9, 3), date(2011, 6, 30)},
            {pymd(1, 2, 3),  date(2012, 9, 4), date(2011, 7, 1)},

            {pymd(1, 0, 0),  date(2011, 2, 28), date(2010, 2, 28)},
            {pymd(4, 0, 0),  date(2011, 2, 28), date(2007, 2, 28)},
            {pymd(1, 0, 0),  date(2012, 2, 29), date(2011, 2, 28)},
            {pymd(4, 0, 0),  date(2012, 2, 29), date(2008, 2, 29)},

            {pymd(1, 1, 0),  date(2013, 3, 29), date(2012, 2, 29)},
            {pymd(1, 2, 0),  date(2012, 2, 29), date(2010, 12, 29)},
        };
    }

    @Test(dataProvider="subtractFrom")
    public void test_subtractFrom(Period period, LocalDate baseDate, LocalDate expected) {
        assertEquals(period.subtractFrom(baseDate), expected);
    }

    @Test(dataProvider="subtractFrom")
    public void test_subtractFrom_usingLocalDateMinus(Period period, LocalDate baseDate, LocalDate expected) {
        assertEquals(baseDate.minus(period), expected);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_subtractFrom_nullZero() {
        Period.ZERO.subtractFrom(null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_subtractFrom_nullNonZero() {
        Period.of(2, DAYS).subtractFrom(null);
    }

    //-----------------------------------------------------------------------
    // toDuration()
    //-----------------------------------------------------------------------
    public void test_toDuration() {
        assertEquals(Period.ZERO.toDuration(), Duration.of(0, SECONDS));
        assertEquals(Period.of(0, 0, 0, 4, 5, 6, 7).toDuration(), Duration.ofSeconds((4 * 60 + 5) * 60L + 6, 7));
    }

    public void test_toDuration_calculation() {
        assertEquals(Period.of(0, 0, 0, 2, 0, 0, 0).toDuration(), Duration.ofSeconds(2 * 3600));
        assertEquals(Period.of(0, 0, 0, 0, 2, 0, 0).toDuration(), Duration.of(120, SECONDS));
        assertEquals(Period.of(0, 0, 0, 0, 0, 2, 0).toDuration(), Duration.of(2, SECONDS));

        assertEquals(Period.of(0, 0, 0, 0, 0, 3, 1000000000L - 1).toDuration(), Duration.ofSeconds(3, 999999999));
        assertEquals(Period.of(0, 0, 0, 0, 0, 3, 1000000000L).toDuration(), Duration.ofSeconds(4, 0));
    }

    public void test_toDuration_negatives() {
        assertEquals(Period.of(0, 0, 0, 0, 0, 2, 1).toDuration(), Duration.ofSeconds(2, 1));
        assertEquals(Period.of(0, 0, 0, 0, 0, 2, -1).toDuration(), Duration.ofSeconds(1, 999999999));
        assertEquals(Period.of(0, 0, 0, 0, 0, -2, 1).toDuration(), Duration.ofSeconds(-2, 1));
        assertEquals(Period.of(0, 0, 0, 0, 0, -2, -1).toDuration(), Duration.ofSeconds(-3, 999999999));
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void test_toDuration_years() {
        Period.of(1, 0, 0, 4, 5, 6, 7).toDuration();
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void test_toDuration_months() {
        Period.of(0, 1, 0, 4, 5, 6, 7).toDuration();
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void test_toDuration_days() {
        Duration test = Period.of(0, 0, 1, 4, 5, 6, 7).toDuration();
        assertEquals(test, Duration.ofSeconds(101106, 7L));
    }

    //-----------------------------------------------------------------------
    // equals() / hashCode()
    //-----------------------------------------------------------------------
    public void test_equals() {
        assertEquals(Period.of(1, 0, 0, 0, 0, 0).equals(Period.of(1, YEARS)), true);
        assertEquals(Period.of(0, 1, 0, 0, 0, 0).equals(Period.of(1, MONTHS)), true);
        assertEquals(Period.of(0, 0, 1, 0, 0, 0).equals(Period.of(1, DAYS)), true);
        assertEquals(Period.of(0, 0, 0, 1, 0, 0).equals(Period.of(1, HOURS)), true);
        assertEquals(Period.of(0, 0, 0, 0, 1, 0).equals(Period.of(1, MINUTES)), true);
        assertEquals(Period.of(0, 0, 0, 0, 0, 1).equals(Period.of(1, SECONDS)), true);
        assertEquals(Period.of(1, 2, 3, 0, 0, 0).equals(Period.ofDate(1, 2, 3)), true);
        assertEquals(Period.of(0, 0, 0, 1, 2, 3).equals(Period.ofTime(1, 2, 3)), true);
        assertEquals(Period.of(1, 2, 3, 4, 5, 6).equals(Period.of(1, 2, 3, 4, 5, 6)), true);

        assertEquals(Period.of(1, YEARS).equals(Period.of(1, YEARS)), true);
        assertEquals(Period.of(1, YEARS).equals(Period.of(2, YEARS)), false);

        assertEquals(Period.of(1, MONTHS).equals(Period.of(1, MONTHS)), true);
        assertEquals(Period.of(1, MONTHS).equals(Period.of(2, MONTHS)), false);

        assertEquals(Period.of(1, DAYS).equals(Period.of(1, DAYS)), true);
        assertEquals(Period.of(1, DAYS).equals(Period.of(2, DAYS)), false);

        assertEquals(Period.of(1, HOURS).equals(Period.of(1, HOURS)), true);
        assertEquals(Period.of(1, HOURS).equals(Period.of(2, HOURS)), false);

        assertEquals(Period.of(1, MINUTES).equals(Period.of(1, MINUTES)), true);
        assertEquals(Period.of(1, MINUTES).equals(Period.of(2, MINUTES)), false);

        assertEquals(Period.of(1, SECONDS).equals(Period.of(1, SECONDS)), true);
        assertEquals(Period.of(1, SECONDS).equals(Period.of(2, SECONDS)), false);

        assertEquals(Period.ofDate(1, 2, 3).equals(Period.ofDate(1, 2, 3)), true);
        assertEquals(Period.ofDate(1, 2, 3).equals(Period.ofDate(0, 2, 3)), false);
        assertEquals(Period.ofDate(1, 2, 3).equals(Period.ofDate(1, 0, 3)), false);
        assertEquals(Period.ofDate(1, 2, 3).equals(Period.ofDate(1, 2, 0)), false);

        assertEquals(Period.ofTime(1, 2, 3).equals(Period.ofTime(1, 2, 3)), true);
        assertEquals(Period.ofTime(1, 2, 3).equals(Period.ofTime(0, 2, 3)), false);
        assertEquals(Period.ofTime(1, 2, 3).equals(Period.ofTime(1, 0, 3)), false);
        assertEquals(Period.ofTime(1, 2, 3).equals(Period.ofTime(1, 2, 0)), false);
    }

    public void test_equals_self() {
        Period test = Period.of(1, 2, 3, 4, 5, 6);
        assertEquals(test.equals(test), true);
    }

    public void test_equals_null() {
        Period test = Period.of(1, 2, 3, 4, 5, 6);
        assertEquals(test.equals(null), false);
    }

    public void test_equals_otherClass() {
        Period test = Period.of(1, 2, 3, 4, 5, 6);
        assertEquals(test.equals(""), false);
    }

    //-----------------------------------------------------------------------
    public void test_hashCode() {
        Period test5 = Period.of(5, DAYS);
        Period test6 = Period.of(6, DAYS);
        Period test5M = Period.of(5, MONTHS);
        Period test5Y = Period.of(5, YEARS);
        assertEquals(test5.hashCode() == test5.hashCode(), true);
        assertEquals(test5.hashCode() == test6.hashCode(), false);
        assertEquals(test5.hashCode() == test5M.hashCode(), false);
        assertEquals(test5.hashCode() == test5Y.hashCode(), false);
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @DataProvider(name="toStringAndParse")
    Object[][] data_toString() {
        return new Object[][] {
            {Period.ZERO, "PT0S"},
            {Period.of(0, DAYS), "PT0S"},
            {Period.of(1, YEARS), "P1Y"},
            {Period.of(1, MONTHS), "P1M"},
            {Period.of(1, DAYS), "P1D"},
            {Period.of(1, HOURS), "PT1H"},
            {Period.of(1, MINUTES), "PT1M"},
            {Period.of(1, SECONDS), "PT1S"},
            {Period.of(12, SECONDS), "PT12S"},
            {Period.of(123, SECONDS), "PT2M3S"},
            {Period.of(1234, SECONDS), "PT20M34S"},
            {Period.of(-1, SECONDS), "PT-1S"},
            {Period.of(-12, SECONDS), "PT-12S"},
            {Period.of(-123, SECONDS), "PT-2M-3S"},
            {Period.of(-1234, SECONDS), "PT-20M-34S"},
            {Period.of(1, 2, 3, 4, 5, 6), "P1Y2M3DT4H5M6S"},
            {Period.of(1, 2, 3, 4, 5, 6, 700000000), "P1Y2M3DT4H5M6.7S"},
            {Period.of(0, 0, 0, 0, 0, 0, 100000000), "PT0.1S"},
            {Period.of(0, 0, 0, 0, 0, 0, -100000000), "PT-0.1S"},
            {Period.of(0, 0, 0, 0, 0, 1, -900000000), "PT0.1S"},
            {Period.of(0, 0, 0, 0, 0, -1, 900000000), "PT-0.1S"},
            {Period.of(0, 0, 0, 0, 0, 1, 100000000), "PT1.1S"},
            {Period.of(0, 0, 0, 0, 0, 1, -100000000), "PT0.9S"},
            {Period.of(0, 0, 0, 0, 0, -1, 100000000), "PT-0.9S"},
            {Period.of(0, 0, 0, 0, 0, -1, -100000000), "PT-1.1S"},
            {Period.of(0, 0, 0, 0, 0, 0, 10000000), "PT0.01S"},
            {Period.of(0, 0, 0, 0, 0, 0, -10000000), "PT-0.01S"},
            {Period.of(0, 0, 0, 0, 0, 0, 1000000), "PT0.001S"},
            {Period.of(0, 0, 0, 0, 0, 0, -1000000), "PT-0.001S"},
            {Period.of(0, 0, 0, 0, 0, 0, 1000), "PT0.000001S"},
            {Period.of(0, 0, 0, 0, 0, 0, -1000), "PT-0.000001S"},
            {Period.of(0, 0, 0, 0, 0, 0, 1), "PT0.000000001S"},
            {Period.of(0, 0, 0, 0, 0, 0, -1), "PT-0.000000001S"},
        };
    }

    @Test(groups="tck", dataProvider="toStringAndParse")
    public void test_toString(Period input, String expected) {
        assertEquals(input.toString(), expected);
    }

    //-----------------------------------------------------------------------
    private void assertPeriod(Period test, int y, int mo, int d, int h, int mn, int s, long n) {
        assertEquals(test.getYears(), y, "years");
        assertEquals(test.getMonths(), mo, "months");
        assertEquals(test.getDays(), d, "days");
        assertEquals(test.getHours(), h, "hours");
        assertEquals(test.getMinutes(), mn, "mins");
        assertEquals(test.getSeconds(), s, "secs");
        assertEquals(test.getNanos(), n, "nanos");
        assertEquals(test.getTimeNanos(), (((h * 60L + mn) * 60 + s) * 1_000_000_000L + n), "total nanos");
    }

    private static Period pymd(int y, int m, int d) {
        return Period.ofDate(y, m, d);
    }

    private static LocalDate date(int y, int m, int d) {
        return LocalDate.of(y, m, d);
    }

}
