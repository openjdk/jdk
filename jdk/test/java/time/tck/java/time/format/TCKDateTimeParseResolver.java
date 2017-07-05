/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Copyright (c) 2008-2013, Stephen Colebourne & Michael Nascimento Santos
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

import static java.time.temporal.ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH;
import static java.time.temporal.ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR;
import static java.time.temporal.ChronoField.ALIGNED_WEEK_OF_MONTH;
import static java.time.temporal.ChronoField.ALIGNED_WEEK_OF_YEAR;
import static java.time.temporal.ChronoField.AMPM_OF_DAY;
import static java.time.temporal.ChronoField.CLOCK_HOUR_OF_AMPM;
import static java.time.temporal.ChronoField.CLOCK_HOUR_OF_DAY;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.DAY_OF_YEAR;
import static java.time.temporal.ChronoField.EPOCH_DAY;
import static java.time.temporal.ChronoField.ERA;
import static java.time.temporal.ChronoField.HOUR_OF_AMPM;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MICRO_OF_DAY;
import static java.time.temporal.ChronoField.MICRO_OF_SECOND;
import static java.time.temporal.ChronoField.MILLI_OF_DAY;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.NANO_OF_DAY;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.PROLEPTIC_MONTH;
import static java.time.temporal.ChronoField.SECOND_OF_DAY;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;
import static java.time.temporal.ChronoField.YEAR_OF_ERA;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQuery;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test parse resolving.
 */
@Test
public class TCKDateTimeParseResolver {
    // TODO: tests with weird TenporalField implementations
    // TODO: tests with non-ISO chronologies

    //-----------------------------------------------------------------------
    @DataProvider(name="resolveOneNoChange")
    Object[][] data_resolveOneNoChange() {
        return new Object[][]{
                {YEAR, 2012},
                {MONTH_OF_YEAR, 8},
                {DAY_OF_MONTH, 7},
                {DAY_OF_YEAR, 6},
                {DAY_OF_WEEK, 5},
        };
    }

    @Test(dataProvider="resolveOneNoChange")
    public void test_resolveOneNoChange(TemporalField field1, long value1) {
        String str = Long.toString(value1);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(field1).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(accessor.query(TemporalQuery.localDate()), null);
        assertEquals(accessor.query(TemporalQuery.localTime()), null);
        assertEquals(accessor.isSupported(field1), true);
        assertEquals(accessor.getLong(field1), value1);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="resolveTwoNoChange")
    Object[][] data_resolveTwoNoChange() {
        return new Object[][]{
                {YEAR, 2012, MONTH_OF_YEAR, 5},
                {YEAR, 2012, DAY_OF_MONTH, 5},
                {YEAR, 2012, DAY_OF_WEEK, 5},
                {YEAR, 2012, ALIGNED_WEEK_OF_YEAR, 5},
                {YEAR, 2012, ALIGNED_WEEK_OF_MONTH, 5},
                {YEAR, 2012, IsoFields.QUARTER_OF_YEAR, 3},
                {YEAR, 2012, MINUTE_OF_HOUR, 5},
                {YEAR, 2012, SECOND_OF_MINUTE, 5},
                {YEAR, 2012, NANO_OF_SECOND, 5},

                {MONTH_OF_YEAR, 5, DAY_OF_MONTH, 5},
                {MONTH_OF_YEAR, 5, DAY_OF_WEEK, 5},
                {MONTH_OF_YEAR, 5, ALIGNED_WEEK_OF_YEAR, 5},
                {MONTH_OF_YEAR, 5, ALIGNED_WEEK_OF_MONTH, 5},
                {MONTH_OF_YEAR, 3, IsoFields.QUARTER_OF_YEAR, 5},
                {MONTH_OF_YEAR, 5, MINUTE_OF_HOUR, 5},
                {MONTH_OF_YEAR, 5, SECOND_OF_MINUTE, 5},
                {MONTH_OF_YEAR, 5, NANO_OF_SECOND, 5},
        };
    }

    @Test(dataProvider="resolveTwoNoChange")
    public void test_resolveTwoNoChange(TemporalField field1, long value1, TemporalField field2, long value2) {
        String str = value1 + " " + value2;
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(field1).appendLiteral(' ')
                .appendValue(field2).toFormatter();
        TemporalAccessor accessor = f.parse(str);

        assertEquals(accessor.query(TemporalQuery.localDate()), null);
        assertEquals(accessor.query(TemporalQuery.localTime()), null);
        assertEquals(accessor.isSupported(field1), true);
        assertEquals(accessor.isSupported(field2), true);
        assertEquals(accessor.getLong(field1), value1);
        assertEquals(accessor.getLong(field2), value2);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="resolveThreeNoChange")
    Object[][] data_resolveThreeNoChange() {
        return new Object[][]{
                {YEAR, 2012, MONTH_OF_YEAR, 5, DAY_OF_WEEK, 5},
                {YEAR, 2012, ALIGNED_WEEK_OF_YEAR, 5, DAY_OF_MONTH, 5},
                {YEAR, 2012, ALIGNED_WEEK_OF_MONTH, 5, DAY_OF_MONTH, 5},
                {YEAR, 2012, MONTH_OF_YEAR, 5, DAY_OF_WEEK, 5},
                {ERA, 1, MONTH_OF_YEAR, 5, DAY_OF_MONTH, 5},
                {MONTH_OF_YEAR, 1, DAY_OF_MONTH, 5, IsoFields.QUARTER_OF_YEAR, 3},
                {HOUR_OF_DAY, 1, SECOND_OF_MINUTE, 5, NANO_OF_SECOND, 5},
                {MINUTE_OF_HOUR, 1, SECOND_OF_MINUTE, 5, NANO_OF_SECOND, 5},
        };
    }

    @Test(dataProvider="resolveThreeNoChange")
    public void test_resolveThreeNoChange(TemporalField field1, long value1, TemporalField field2, long value2, TemporalField field3, long value3) {
        String str = value1 + " " + value2 + " " + value3;
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(field1).appendLiteral(' ')
                .appendValue(field2).appendLiteral(' ')
                .appendValue(field3).toFormatter();
        TemporalAccessor accessor = f.parse(str);

        assertEquals(accessor.query(TemporalQuery.localDate()), null);
        assertEquals(accessor.query(TemporalQuery.localTime()), null);
        assertEquals(accessor.isSupported(field1), true);
        assertEquals(accessor.isSupported(field2), true);
        assertEquals(accessor.isSupported(field3), true);
        assertEquals(accessor.getLong(field1), value1);
        assertEquals(accessor.getLong(field2), value2);
        assertEquals(accessor.getLong(field3), value3);
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @DataProvider(name="resolveOneToField")
    Object[][] data_resolveOneToField() {
        return new Object[][]{
                {YEAR_OF_ERA, 2012, YEAR, 2012L, null, null},
                {PROLEPTIC_MONTH, 2012 * 12L + (3 - 1), YEAR, 2012L, MONTH_OF_YEAR, 3L},

                {CLOCK_HOUR_OF_AMPM, 8, HOUR_OF_AMPM, 8L, null, null},
                {CLOCK_HOUR_OF_AMPM, 12, HOUR_OF_AMPM, 0L, null, null},
                {MICRO_OF_SECOND, 12, NANO_OF_SECOND, 12_000L, null, null},
                {MILLI_OF_SECOND, 12, NANO_OF_SECOND, 12_000_000L, null, null},
        };
    }

    @Test(dataProvider="resolveOneToField")
    public void test_resolveOneToField(TemporalField field1, long value1,
                                       TemporalField expectedField1, Long expectedValue1,
                                       TemporalField expectedField2, Long expectedValue2) {
        String str = Long.toString(value1);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(field1).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(accessor.query(TemporalQuery.localDate()), null);
        assertEquals(accessor.query(TemporalQuery.localTime()), null);
        if (expectedField1 != null) {
            assertEquals(accessor.isSupported(expectedField1), true);
            assertEquals(accessor.getLong(expectedField1), expectedValue1.longValue());
        }
        if (expectedField2 != null) {
            assertEquals(accessor.isSupported(expectedField2), true);
            assertEquals(accessor.getLong(expectedField2), expectedValue2.longValue());
        }
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="resolveOneToDate")
    Object[][] data_resolveOneToDate() {
        return new Object[][]{
                {EPOCH_DAY, 32, LocalDate.of(1970, 2, 2)},
        };
    }

    @Test(dataProvider="resolveOneToDate")
    public void test_resolveOneToDate(TemporalField field1, long value1, LocalDate expectedDate) {
        String str = Long.toString(value1);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(field1).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(accessor.query(TemporalQuery.localDate()), expectedDate);
        assertEquals(accessor.query(TemporalQuery.localTime()), null);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="resolveOneToTime")
    Object[][] data_resolveOneToTime() {
        return new Object[][]{
                {HOUR_OF_DAY, 8, LocalTime.of(8, 0)},
                {CLOCK_HOUR_OF_DAY, 8, LocalTime.of(8, 0)},
                {CLOCK_HOUR_OF_DAY, 24, LocalTime.of(0, 0)},
                {MINUTE_OF_DAY, 650, LocalTime.of(10, 50)},
                {SECOND_OF_DAY, 3600 + 650, LocalTime.of(1, 10, 50)},
                {MILLI_OF_DAY, (3600 + 650) * 1_000L + 2, LocalTime.of(1, 10, 50, 2_000_000)},
                {MICRO_OF_DAY, (3600 + 650) * 1_000_000L + 2, LocalTime.of(1, 10, 50, 2_000)},
                {NANO_OF_DAY, (3600 + 650) * 1_000_000_000L + 2, LocalTime.of(1, 10, 50, 2)},
        };
    }

    @Test(dataProvider="resolveOneToTime")
    public void test_resolveOneToTime(TemporalField field1, long value1, LocalTime expectedTime) {
        String str = Long.toString(value1);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(field1).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(accessor.query(TemporalQuery.localDate()), null);
        assertEquals(accessor.query(TemporalQuery.localTime()), expectedTime);
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @DataProvider(name="resolveTwoToField")
    Object[][] data_resolveTwoToField() {
        return new Object[][]{
                // cross-check
                {PROLEPTIC_MONTH, 2012 * 12L + (3 - 1), YEAR, 2012, YEAR, 2012L, MONTH_OF_YEAR, 3L},
                {PROLEPTIC_MONTH, 2012 * 12L + (3 - 1), YEAR_OF_ERA, 2012, YEAR, 2012L, MONTH_OF_YEAR, 3L},
                {PROLEPTIC_MONTH, 2012 * 12L + (3 - 1), ERA, 1, YEAR, 2012L, MONTH_OF_YEAR, 3L},
                {PROLEPTIC_MONTH, (3 - 1), YEAR, 0, YEAR, 0L, MONTH_OF_YEAR, 3L},
                {PROLEPTIC_MONTH, (3 - 1), YEAR_OF_ERA, 1, YEAR, 0L, MONTH_OF_YEAR, 3L},
                {PROLEPTIC_MONTH, (3 - 1), ERA, 0, YEAR, 0L, MONTH_OF_YEAR, 3L},
        };
    }

    @Test(dataProvider="resolveTwoToField")
    public void test_resolveTwoToField(TemporalField field1, long value1,
                                       TemporalField field2, long value2,
                                       TemporalField expectedField1, Long expectedValue1,
                                       TemporalField expectedField2, Long expectedValue2) {
        String str = value1 + " " + value2;
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(field1).appendLiteral(' ')
                .appendValue(field2).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(accessor.query(TemporalQuery.localDate()), null);
        assertEquals(accessor.query(TemporalQuery.localTime()), null);
        if (expectedField1 != null) {
            assertEquals(accessor.isSupported(expectedField1), true);
            assertEquals(accessor.getLong(expectedField1), expectedValue1.longValue());
        }
        if (expectedField2 != null) {
            assertEquals(accessor.isSupported(expectedField2), true);
            assertEquals(accessor.getLong(expectedField2), expectedValue2.longValue());
        }
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="resolveTwoToDate")
    Object[][] data_resolveTwoToDate() {
        return new Object[][]{
                // merge
                {YEAR, 2012, DAY_OF_YEAR, 32, LocalDate.of(2012, 2, 1)},
                {YEAR_OF_ERA, 2012, DAY_OF_YEAR, 32, LocalDate.of(2012, 2, 1)},

                // merge
                {PROLEPTIC_MONTH, 2012 * 12 + (2 - 1), DAY_OF_MONTH, 25, LocalDate.of(2012, 2, 25)},
                {PROLEPTIC_MONTH, 2012 * 12 + (2 - 1), DAY_OF_YEAR, 56, LocalDate.of(2012, 2, 25)},

                // cross-check
                {EPOCH_DAY, 32, ERA, 1, LocalDate.of(1970, 2, 2)},
                {EPOCH_DAY, -146097 * 5L, ERA, 0, LocalDate.of(1970 - (400 * 5), 1, 1)},
                {EPOCH_DAY, 32, YEAR, 1970, LocalDate.of(1970, 2, 2)},
                {EPOCH_DAY, -146097 * 5L, YEAR, 1970 - (400 * 5), LocalDate.of(1970 - (400 * 5), 1, 1)},
                {EPOCH_DAY, 32, YEAR_OF_ERA, 1970, LocalDate.of(1970, 2, 2)},
                {EPOCH_DAY, -146097 * 5L, YEAR_OF_ERA, 1 - (1970 - (400 * 5)), LocalDate.of(1970 - (400 * 5), 1, 1)},
                {EPOCH_DAY, 32, MONTH_OF_YEAR, 2, LocalDate.of(1970, 2, 2)},
                {EPOCH_DAY, 32, DAY_OF_YEAR, 33, LocalDate.of(1970, 2, 2)},
                {EPOCH_DAY, 32, DAY_OF_MONTH, 2, LocalDate.of(1970, 2, 2)},
                {EPOCH_DAY, 32, DAY_OF_WEEK, 1, LocalDate.of(1970, 2, 2)},
        };
    }

    @Test(dataProvider="resolveTwoToDate")
    public void test_resolveTwoToDate(TemporalField field1, long value1,
                                      TemporalField field2, long value2,
                                      LocalDate expectedDate) {
        String str = value1 + " " + value2;
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(field1).appendLiteral(' ')
                .appendValue(field2).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(accessor.query(TemporalQuery.localDate()), expectedDate);
        assertEquals(accessor.query(TemporalQuery.localTime()), null);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="resolveTwoToTime")
    Object[][] data_resolveTwoToTime() {
        return new Object[][]{
                // merge
                {HOUR_OF_DAY, 8, MINUTE_OF_HOUR, 6, LocalTime.of(8, 6)},

                // merge
                {AMPM_OF_DAY, 0, HOUR_OF_AMPM, 5, LocalTime.of(5, 0)},
                {AMPM_OF_DAY, 1, HOUR_OF_AMPM, 5, LocalTime.of(17, 0)},
                {AMPM_OF_DAY, 0, CLOCK_HOUR_OF_AMPM, 5, LocalTime.of(5, 0)},
                {AMPM_OF_DAY, 1, CLOCK_HOUR_OF_AMPM, 5, LocalTime.of(17, 0)},
                {AMPM_OF_DAY, 0, HOUR_OF_DAY, 5, LocalTime.of(5, 0)},
                {AMPM_OF_DAY, 1, HOUR_OF_DAY, 17, LocalTime.of(17, 0)},
                {AMPM_OF_DAY, 0, CLOCK_HOUR_OF_DAY, 5, LocalTime.of(5, 0)},
                {AMPM_OF_DAY, 1, CLOCK_HOUR_OF_DAY, 17, LocalTime.of(17, 0)},

                // merge
                {CLOCK_HOUR_OF_DAY, 8, MINUTE_OF_HOUR, 6, LocalTime.of(8, 6)},
                {CLOCK_HOUR_OF_DAY, 24, MINUTE_OF_HOUR, 6, LocalTime.of(0, 6)},
                // cross-check
                {CLOCK_HOUR_OF_DAY, 8, HOUR_OF_DAY, 8, LocalTime.of(8, 0)},
                {CLOCK_HOUR_OF_DAY, 8, CLOCK_HOUR_OF_AMPM, 8, LocalTime.of(8, 0)},
                {CLOCK_HOUR_OF_DAY, 20, CLOCK_HOUR_OF_AMPM, 8, LocalTime.of(20, 0)},
                {CLOCK_HOUR_OF_DAY, 8, AMPM_OF_DAY, 0, LocalTime.of(8, 0)},
                {CLOCK_HOUR_OF_DAY, 20, AMPM_OF_DAY, 1, LocalTime.of(20, 0)},

                // merge
                {MINUTE_OF_DAY, 650, SECOND_OF_MINUTE, 8, LocalTime.of(10, 50, 8)},
                // cross-check
                {MINUTE_OF_DAY, 650, HOUR_OF_DAY, 10, LocalTime.of(10, 50)},
                {MINUTE_OF_DAY, 650, CLOCK_HOUR_OF_DAY, 10, LocalTime.of(10, 50)},
                {MINUTE_OF_DAY, 650, CLOCK_HOUR_OF_AMPM, 10, LocalTime.of(10, 50)},
                {MINUTE_OF_DAY, 650, AMPM_OF_DAY, 0, LocalTime.of(10, 50)},
                {MINUTE_OF_DAY, 650, MINUTE_OF_HOUR, 50, LocalTime.of(10, 50)},

                // merge
                {SECOND_OF_DAY, 3600 + 650, MILLI_OF_SECOND, 2, LocalTime.of(1, 10, 50, 2_000_000)},
                {SECOND_OF_DAY, 3600 + 650, MICRO_OF_SECOND, 2, LocalTime.of(1, 10, 50, 2_000)},
                {SECOND_OF_DAY, 3600 + 650, NANO_OF_SECOND, 2, LocalTime.of(1, 10, 50, 2)},
                // cross-check
                {SECOND_OF_DAY, 3600 + 650, HOUR_OF_DAY, 1, LocalTime.of(1, 10, 50)},
                {SECOND_OF_DAY, 3600 + 650, MINUTE_OF_HOUR, 10, LocalTime.of(1, 10, 50)},
                {SECOND_OF_DAY, 3600 + 650, SECOND_OF_MINUTE, 50, LocalTime.of(1, 10, 50)},

                // merge
                {MILLI_OF_DAY, (3600 + 650) * 1000L + 2, MICRO_OF_SECOND, 2_004, LocalTime.of(1, 10, 50, 2_004_000)},
                {MILLI_OF_DAY, (3600 + 650) * 1000L + 2, NANO_OF_SECOND, 2_000_004, LocalTime.of(1, 10, 50, 2_000_004)},
                // cross-check
                {MILLI_OF_DAY, (3600 + 650) * 1000L + 2, HOUR_OF_DAY, 1, LocalTime.of(1, 10, 50, 2_000_000)},
                {MILLI_OF_DAY, (3600 + 650) * 1000L + 2, MINUTE_OF_HOUR, 10, LocalTime.of(1, 10, 50, 2_000_000)},
                {MILLI_OF_DAY, (3600 + 650) * 1000L + 2, SECOND_OF_MINUTE, 50, LocalTime.of(1, 10, 50, 2_000_000)},
                {MILLI_OF_DAY, (3600 + 650) * 1000L + 2, MILLI_OF_SECOND, 2, LocalTime.of(1, 10, 50, 2_000_000)},

                // merge
                {MICRO_OF_DAY, (3600 + 650) * 1000_000L + 2, NANO_OF_SECOND, 2_004, LocalTime.of(1, 10, 50, 2_004)},
                // cross-check
                {MICRO_OF_DAY, (3600 + 650) * 1000_000L + 2, HOUR_OF_DAY, 1, LocalTime.of(1, 10, 50, 2_000)},
                {MICRO_OF_DAY, (3600 + 650) * 1000_000L + 2, MINUTE_OF_HOUR, 10, LocalTime.of(1, 10, 50, 2_000)},
                {MICRO_OF_DAY, (3600 + 650) * 1000_000L + 2, SECOND_OF_MINUTE, 50, LocalTime.of(1, 10, 50, 2_000)},
                {MICRO_OF_DAY, (3600 + 650) * 1000_000L + 2, MILLI_OF_SECOND, 0, LocalTime.of(1, 10, 50, 2_000)},
                {MICRO_OF_DAY, (3600 + 650) * 1000_000L + 2, MICRO_OF_SECOND, 2, LocalTime.of(1, 10, 50, 2_000)},

                // cross-check
                {NANO_OF_DAY, (3600 + 650) * 1000_000_000L + 2, HOUR_OF_DAY, 1, LocalTime.of(1, 10, 50, 2)},
                {NANO_OF_DAY, (3600 + 650) * 1000_000_000L + 2, MINUTE_OF_HOUR, 10, LocalTime.of(1, 10, 50, 2)},
                {NANO_OF_DAY, (3600 + 650) * 1000_000_000L + 2, SECOND_OF_MINUTE, 50, LocalTime.of(1, 10, 50, 2)},
                {NANO_OF_DAY, (3600 + 650) * 1000_000_000L + 2, MILLI_OF_SECOND, 0, LocalTime.of(1, 10, 50, 2)},
                {NANO_OF_DAY, (3600 + 650) * 1000_000_000L + 2, MICRO_OF_SECOND, 0, LocalTime.of(1, 10, 50, 2)},
                {NANO_OF_DAY, (3600 + 650) * 1000_000_000L + 2, NANO_OF_SECOND, 2, LocalTime.of(1, 10, 50, 2)},
        };
    }

    @Test(dataProvider="resolveTwoToTime")
    public void test_resolveTwoToTime(TemporalField field1, long value1,
                                TemporalField field2, long value2,
                                LocalTime expectedTime) {
        String str = value1 + " " + value2;
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(field1).appendLiteral(' ')
                .appendValue(field2).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(accessor.query(TemporalQuery.localDate()), null);
        assertEquals(accessor.query(TemporalQuery.localTime()), expectedTime);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="resolveThreeToDate")
    Object[][] data_resolveThreeToDate() {
        return new Object[][]{
                // merge
                {YEAR, 2012, MONTH_OF_YEAR, 2, DAY_OF_MONTH, 1, LocalDate.of(2012, 2, 1)},
                {YEAR, 2012, ALIGNED_WEEK_OF_YEAR, 5, ALIGNED_DAY_OF_WEEK_IN_YEAR, 4, LocalDate.of(2012, 2, 1)},
                {YEAR, 2012, ALIGNED_WEEK_OF_YEAR, 5, DAY_OF_WEEK, 3, LocalDate.of(2012, 2, 1)},

                // cross-check
                {YEAR, 2012, DAY_OF_YEAR, 32, DAY_OF_MONTH, 1, LocalDate.of(2012, 2, 1)},
                {YEAR_OF_ERA, 2012, DAY_OF_YEAR, 32, DAY_OF_MONTH, 1, LocalDate.of(2012, 2, 1)},
                {YEAR, 2012, DAY_OF_YEAR, 32, DAY_OF_WEEK, 3, LocalDate.of(2012, 2, 1)},
                {PROLEPTIC_MONTH, 2012 * 12 + (2 - 1), DAY_OF_MONTH, 25, DAY_OF_WEEK, 6, LocalDate.of(2012, 2, 25)},
        };
    }

    @Test(dataProvider="resolveThreeToDate")
    public void test_resolveThreeToDate(TemporalField field1, long value1,
                                      TemporalField field2, long value2,
                                      TemporalField field3, long value3,
                                      LocalDate expectedDate) {
        String str = value1 + " " + value2 + " " + value3;
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(field1).appendLiteral(' ')
                .appendValue(field2).appendLiteral(' ')
                .appendValue(field3).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(accessor.query(TemporalQuery.localDate()), expectedDate);
        assertEquals(accessor.query(TemporalQuery.localTime()), null);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="resolveFourToDate")
    Object[][] data_resolveFourToDate() {
        return new Object[][]{
                // merge
                {YEAR, 2012, MONTH_OF_YEAR, 2, ALIGNED_WEEK_OF_MONTH, 1, ALIGNED_DAY_OF_WEEK_IN_MONTH, 1, LocalDate.of(2012, 2, 1)},
                {YEAR, 2012, MONTH_OF_YEAR, 2, ALIGNED_WEEK_OF_MONTH, 1, DAY_OF_WEEK, 3, LocalDate.of(2012, 2, 1)},

                // cross-check
                {YEAR, 2012, MONTH_OF_YEAR, 2, DAY_OF_MONTH, 1, DAY_OF_WEEK, 3, LocalDate.of(2012, 2, 1)},
                {YEAR, 2012, ALIGNED_WEEK_OF_YEAR, 5, ALIGNED_DAY_OF_WEEK_IN_YEAR, 4, DAY_OF_WEEK, 3, LocalDate.of(2012, 2, 1)},
                {YEAR, 2012, ALIGNED_WEEK_OF_YEAR, 5, DAY_OF_WEEK, 3, DAY_OF_MONTH, 1, LocalDate.of(2012, 2, 1)},
        };
    }

    @Test(dataProvider="resolveFourToDate")
    public void test_resolveFourToDate(TemporalField field1, long value1,
                                        TemporalField field2, long value2,
                                        TemporalField field3, long value3,
                                        TemporalField field4, long value4,
                                        LocalDate expectedDate) {
        String str = value1 + " " + value2 + " " + value3 + " " + value4;
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(field1).appendLiteral(' ')
                .appendValue(field2).appendLiteral(' ')
                .appendValue(field3).appendLiteral(' ')
                .appendValue(field4).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(accessor.query(TemporalQuery.localDate()), expectedDate);
        assertEquals(accessor.query(TemporalQuery.localTime()), null);
    }

}
