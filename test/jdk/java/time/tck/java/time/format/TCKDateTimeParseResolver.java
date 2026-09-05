/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.time.format.ResolverStyle.LENIENT;
import static java.time.format.ResolverStyle.SMART;
import static java.time.format.ResolverStyle.STRICT;
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
import static java.time.temporal.ChronoField.INSTANT_SECONDS;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.ChronoLocalDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.time.chrono.IsoChronology;
import java.time.chrono.MinguoChronology;
import java.time.chrono.MinguoDate;
import java.time.chrono.ThaiBuddhistChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.IsoFields;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalUnit;
import java.time.temporal.ValueRange;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test parse resolving.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TCKDateTimeParseResolver {
    // TODO: tests with weird TenporalField implementations
    // TODO: tests with non-ISO chronologies

    private static final ZoneId EUROPE_ATHENS = ZoneId.of("Europe/Athens");
    private static final ZoneId EUROPE_PARIS = ZoneId.of("Europe/Paris");

    //-----------------------------------------------------------------------
    Object[][] data_resolveOneNoChange() {
        return new Object[][]{
                {YEAR, 2012},
                {MONTH_OF_YEAR, 8},
                {DAY_OF_MONTH, 7},
                {DAY_OF_YEAR, 6},
                {DAY_OF_WEEK, 5},
        };
    }

    @ParameterizedTest
    @MethodSource("data_resolveOneNoChange")
    public void test_resolveOneNoChange(TemporalField field1, long value1) {
        String str = Long.toString(value1);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(field1).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(null, accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        assertEquals(true, accessor.isSupported(field1));
        assertEquals(value1, accessor.getLong(field1));
    }

    //-----------------------------------------------------------------------
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

    @ParameterizedTest
    @MethodSource("data_resolveTwoNoChange")
    public void test_resolveTwoNoChange(TemporalField field1, long value1, TemporalField field2, long value2) {
        String str = value1 + " " + value2;
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(field1).appendLiteral(' ')
                .appendValue(field2).toFormatter();
        TemporalAccessor accessor = f.parse(str);

        assertEquals(null, accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        assertEquals(true, accessor.isSupported(field1));
        assertEquals(true, accessor.isSupported(field2));
        assertEquals(value1, accessor.getLong(field1));
        assertEquals(value2, accessor.getLong(field2));
    }

    //-----------------------------------------------------------------------
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

    @ParameterizedTest
    @MethodSource("data_resolveThreeNoChange")
    public void test_resolveThreeNoChange(TemporalField field1, long value1, TemporalField field2, long value2, TemporalField field3, long value3) {
        String str = value1 + " " + value2 + " " + value3;
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(field1).appendLiteral(' ')
                .appendValue(field2).appendLiteral(' ')
                .appendValue(field3).toFormatter();
        TemporalAccessor accessor = f.parse(str);

        assertEquals(null, accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        assertEquals(true, accessor.isSupported(field1));
        assertEquals(true, accessor.isSupported(field2));
        assertEquals(true, accessor.isSupported(field3));
        assertEquals(value1, accessor.getLong(field1));
        assertEquals(value2, accessor.getLong(field2));
        assertEquals(value3, accessor.getLong(field3));
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
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

    @ParameterizedTest
    @MethodSource("data_resolveOneToField")
    public void test_resolveOneToField(TemporalField field1, long value1,
                                       TemporalField expectedField1, Long expectedValue1,
                                       TemporalField expectedField2, Long expectedValue2) {
        String str = Long.toString(value1);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(field1).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(null, accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        if (expectedField1 != null) {
            assertEquals(true, accessor.isSupported(expectedField1));
            assertEquals(expectedValue1.longValue(), accessor.getLong(expectedField1));
        }
        if (expectedField2 != null) {
            assertEquals(true, accessor.isSupported(expectedField2));
            assertEquals(expectedValue2.longValue(), accessor.getLong(expectedField2));
        }
    }

    //-----------------------------------------------------------------------
    Object[][] data_resolveOneToDate() {
        return new Object[][]{
                {EPOCH_DAY, 32, LocalDate.of(1970, 2, 2)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_resolveOneToDate")
    public void test_resolveOneToDate(TemporalField field1, long value1, LocalDate expectedDate) {
        String str = Long.toString(value1);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(field1).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(expectedDate, accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
    }

    //-----------------------------------------------------------------------
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

    @ParameterizedTest
    @MethodSource("data_resolveOneToTime")
    public void test_resolveOneToTime(TemporalField field1, long value1, LocalTime expectedTime) {
        String str = Long.toString(value1);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(field1).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(null, accessor.query(TemporalQueries.localDate()));
        assertEquals(expectedTime, accessor.query(TemporalQueries.localTime()));
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
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

    @ParameterizedTest
    @MethodSource("data_resolveTwoToField")
    public void test_resolveTwoToField(TemporalField field1, long value1,
                                       TemporalField field2, long value2,
                                       TemporalField expectedField1, Long expectedValue1,
                                       TemporalField expectedField2, Long expectedValue2) {
        String str = value1 + " " + value2;
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(field1).appendLiteral(' ')
                .appendValue(field2).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(null, accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        if (expectedField1 != null) {
            assertEquals(true, accessor.isSupported(expectedField1));
            assertEquals(expectedValue1.longValue(), accessor.getLong(expectedField1));
        }
        if (expectedField2 != null) {
            assertEquals(true, accessor.isSupported(expectedField2));
            assertEquals(expectedValue2.longValue(), accessor.getLong(expectedField2));
        }
    }

    //-----------------------------------------------------------------------
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

    @ParameterizedTest
    @MethodSource("data_resolveTwoToDate")
    public void test_resolveTwoToDate(TemporalField field1, long value1,
                                      TemporalField field2, long value2,
                                      LocalDate expectedDate) {
        String str = value1 + " " + value2;
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(field1).appendLiteral(' ')
                .appendValue(field2).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(expectedDate, accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
    }

    //-----------------------------------------------------------------------
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

    @ParameterizedTest
    @MethodSource("data_resolveTwoToTime")
    public void test_resolveTwoToTime(TemporalField field1, long value1,
                                TemporalField field2, long value2,
                                LocalTime expectedTime) {
        String str = value1 + " " + value2;
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(field1).appendLiteral(' ')
                .appendValue(field2).toFormatter();

        TemporalAccessor accessor = f.parse(str);
        assertEquals(null, accessor.query(TemporalQueries.localDate()));
        assertEquals(expectedTime, accessor.query(TemporalQueries.localTime()));
    }

    //-----------------------------------------------------------------------
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

    @ParameterizedTest
    @MethodSource("data_resolveThreeToDate")
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
        assertEquals(expectedDate, accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
    }

    //-----------------------------------------------------------------------
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

    @ParameterizedTest
    @MethodSource("data_resolveFourToDate")
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
        assertEquals(expectedDate, accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
    }

    //-----------------------------------------------------------------------
    Object[][] data_resolveFourToTime() {
        return new Object[][]{
                // merge
                {null, 0, 0, 0, 0, LocalTime.of(0, 0, 0, 0), Period.ZERO},
                {null, 1, 0, 0, 0, LocalTime.of(1, 0, 0, 0), Period.ZERO},
                {null, 0, 2, 0, 0, LocalTime.of(0, 2, 0, 0), Period.ZERO},
                {null, 0, 0, 3, 0, LocalTime.of(0, 0, 3, 0), Period.ZERO},
                {null, 0, 0, 0, 4, LocalTime.of(0, 0, 0, 4), Period.ZERO},
                {null, 1, 2, 3, 4, LocalTime.of(1, 2, 3, 4), Period.ZERO},
                {null, 23, 59, 59, 123456789, LocalTime.of(23, 59, 59, 123456789), Period.ZERO},

                {ResolverStyle.STRICT, 14, 59, 60, 123456789, null, null},
                {ResolverStyle.SMART, 14, 59, 60, 123456789, null, null},
                {ResolverStyle.LENIENT, 14, 59, 60, 123456789, LocalTime.of(15, 0, 0, 123456789), Period.ZERO},

                {ResolverStyle.STRICT, 23, 59, 60, 123456789, null, null},
                {ResolverStyle.SMART, 23, 59, 60, 123456789, null, null},
                {ResolverStyle.LENIENT, 23, 59, 60, 123456789, LocalTime.of(0, 0, 0, 123456789), Period.ofDays(1)},

                {ResolverStyle.STRICT, 24, 0, 0, 0, null, null},
                {ResolverStyle.SMART, 24, 0, 0, 0, LocalTime.of(0, 0, 0, 0), Period.ofDays(1)},
                {ResolverStyle.LENIENT, 24, 0, 0, 0, LocalTime.of(0, 0, 0, 0), Period.ofDays(1)},

                {ResolverStyle.STRICT, 24, 1, 0, 0, null, null},
                {ResolverStyle.SMART, 24, 1, 0, 0, null, null},
                {ResolverStyle.LENIENT, 24, 1, 0, 0, LocalTime.of(0, 1, 0, 0), Period.ofDays(1)},

                {ResolverStyle.STRICT, 25, 0, 0, 0, null, null},
                {ResolverStyle.SMART, 25, 0, 0, 0, null, null},
                {ResolverStyle.LENIENT, 25, 0, 0, 0, LocalTime.of(1, 0, 0, 0), Period.ofDays(1)},

                {ResolverStyle.STRICT, 49, 2, 3, 4, null, null},
                {ResolverStyle.SMART, 49, 2, 3, 4, null, null},
                {ResolverStyle.LENIENT, 49, 2, 3, 4, LocalTime.of(1, 2, 3, 4), Period.ofDays(2)},

                {ResolverStyle.STRICT, -1, 2, 3, 4, null, null},
                {ResolverStyle.SMART, -1, 2, 3, 4, null, null},
                {ResolverStyle.LENIENT, -1, 2, 3, 4, LocalTime.of(23, 2, 3, 4), Period.ofDays(-1)},

                {ResolverStyle.STRICT, -6, 2, 3, 4, null, null},
                {ResolverStyle.SMART, -6, 2, 3, 4, null, null},
                {ResolverStyle.LENIENT, -6, 2, 3, 4, LocalTime.of(18, 2, 3, 4), Period.ofDays(-1)},

                {ResolverStyle.STRICT, 25, 61, 61, 1_123456789, null, null},
                {ResolverStyle.SMART, 25, 61, 61, 1_123456789, null, null},
                {ResolverStyle.LENIENT, 25, 61, 61, 1_123456789, LocalTime.of(2, 2, 2, 123456789), Period.ofDays(1)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_resolveFourToTime")
    public void test_resolveFourToTime(ResolverStyle style,
                       long hour, long min, long sec, long nano, LocalTime expectedTime, Period excessPeriod) {
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .parseDefaulting(HOUR_OF_DAY, hour)
                .parseDefaulting(MINUTE_OF_HOUR, min)
                .parseDefaulting(SECOND_OF_MINUTE, sec)
                .parseDefaulting(NANO_OF_SECOND, nano).toFormatter();

        ResolverStyle[] styles = (style != null ? new ResolverStyle[] {style} : ResolverStyle.values());
        for (ResolverStyle s : styles) {
            if (expectedTime != null) {
                TemporalAccessor accessor = f.withResolverStyle(s).parse("");
                assertEquals(null, accessor.query(TemporalQueries.localDate()), "ResolverStyle: " + s);
                assertEquals(expectedTime, accessor.query(TemporalQueries.localTime()), "ResolverStyle: " + s);
                assertEquals(excessPeriod, accessor.query(DateTimeFormatter.parsedExcessDays()), "ResolverStyle: " + s);
            } else {
                try {
                    f.withResolverStyle(style).parse("");
                    fail();
                } catch (DateTimeParseException ex) {
                    // expected
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("data_resolveFourToTime")
    public void test_resolveThreeToTime(ResolverStyle style,
                                       long hour, long min, long sec, long nano, LocalTime expectedTime, Period excessPeriod) {
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .parseDefaulting(HOUR_OF_DAY, hour)
                .parseDefaulting(MINUTE_OF_HOUR, min)
                .parseDefaulting(SECOND_OF_MINUTE, sec).toFormatter();

        ResolverStyle[] styles = (style != null ? new ResolverStyle[] {style} : ResolverStyle.values());
        for (ResolverStyle s : styles) {
            if (expectedTime != null) {
                TemporalAccessor accessor = f.withResolverStyle(s).parse("");
                assertEquals(null, accessor.query(TemporalQueries.localDate()), "ResolverStyle: " + s);
                assertEquals(expectedTime.minusNanos(nano), accessor.query(TemporalQueries.localTime()), "ResolverStyle: " + s);
                assertEquals(excessPeriod, accessor.query(DateTimeFormatter.parsedExcessDays()), "ResolverStyle: " + s);
            } else {
                try {
                    f.withResolverStyle(style).parse("");
                    fail();
                } catch (DateTimeParseException ex) {
                    // expected
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("data_resolveFourToTime")
    public void test_resolveFourToDateTime(ResolverStyle style,
                       long hour, long min, long sec, long nano, LocalTime expectedTime, Period excessPeriod) {
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .parseDefaulting(YEAR, 2012).parseDefaulting(MONTH_OF_YEAR, 6).parseDefaulting(DAY_OF_MONTH, 30)
                .parseDefaulting(HOUR_OF_DAY, hour)
                .parseDefaulting(MINUTE_OF_HOUR, min)
                .parseDefaulting(SECOND_OF_MINUTE, sec)
                .parseDefaulting(NANO_OF_SECOND, nano).toFormatter();

        ResolverStyle[] styles = (style != null ? new ResolverStyle[] {style} : ResolverStyle.values());
        if (expectedTime != null && excessPeriod != null) {
            LocalDate expectedDate = LocalDate.of(2012, 6, 30).plus(excessPeriod);
            for (ResolverStyle s : styles) {
                TemporalAccessor accessor = f.withResolverStyle(s).parse("");
                assertEquals(expectedDate, accessor.query(TemporalQueries.localDate()), "ResolverStyle: " + s);
                assertEquals(expectedTime, accessor.query(TemporalQueries.localTime()), "ResolverStyle: " + s);
                assertEquals(Period.ZERO, accessor.query(DateTimeFormatter.parsedExcessDays()), "ResolverStyle: " + s);
            }
        }
    }

    //-----------------------------------------------------------------------
    Object[][] data_resolveSecondOfDay() {
        return new Object[][]{
                {STRICT, 0, 0, 0},
                {STRICT, 1, 1, 0},
                {STRICT, 86399, 86399, 0},
                {STRICT, -1, null, 0},
                {STRICT, 86400, null, 0},

                {SMART, 0, 0, 0},
                {SMART, 1, 1, 0},
                {SMART, 86399, 86399, 0},
                {SMART, -1, null, 0},
                {SMART, 86400, null, 0},

                {LENIENT, 0, 0, 0},
                {LENIENT, 1, 1, 0},
                {LENIENT, 86399, 86399, 0},
                {LENIENT, -1, 86399, -1},
                {LENIENT, 86400, 0, 1},
        };
    }

    @ParameterizedTest
    @MethodSource("data_resolveSecondOfDay")
    public void test_resolveSecondOfDay(ResolverStyle style, long value, Integer expectedSecond, int expectedDays) {
        String str = Long.toString(value);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(SECOND_OF_DAY).toFormatter();

        if (expectedSecond != null) {
            TemporalAccessor accessor = f.withResolverStyle(style).parse(str);
            assertEquals(null, accessor.query(TemporalQueries.localDate()));
            assertEquals(LocalTime.ofSecondOfDay(expectedSecond), accessor.query(TemporalQueries.localTime()));
            assertEquals(Period.ofDays(expectedDays), accessor.query(DateTimeFormatter.parsedExcessDays()));
        } else {
            try {
                f.withResolverStyle(style).parse(str);
                fail();
            } catch (DateTimeParseException ex) {
                // expected
            }
        }
    }

    //-----------------------------------------------------------------------
    Object[][] data_resolveMinuteOfDay() {
        return new Object[][]{
                {STRICT, 0, 0, 0},
                {STRICT, 1, 1, 0},
                {STRICT, 1439, 1439, 0},
                {STRICT, -1, null, 0},
                {STRICT, 1440, null, 0},

                {SMART, 0, 0, 0},
                {SMART, 1, 1, 0},
                {SMART, 1439, 1439, 0},
                {SMART, -1, null, 0},
                {SMART, 1440, null, 0},

                {LENIENT, 0, 0, 0},
                {LENIENT, 1, 1, 0},
                {LENIENT, 1439, 1439, 0},
                {LENIENT, -1, 1439, -1},
                {LENIENT, 1440, 0, 1},
        };
    }

    @ParameterizedTest
    @MethodSource("data_resolveMinuteOfDay")
    public void test_resolveMinuteOfDay(ResolverStyle style, long value, Integer expectedMinute, int expectedDays) {
        String str = Long.toString(value);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(MINUTE_OF_DAY).toFormatter();

        if (expectedMinute != null) {
            TemporalAccessor accessor = f.withResolverStyle(style).parse(str);
            assertEquals(null, accessor.query(TemporalQueries.localDate()));
            assertEquals(LocalTime.ofSecondOfDay(expectedMinute * 60), accessor.query(TemporalQueries.localTime()));
            assertEquals(Period.ofDays(expectedDays), accessor.query(DateTimeFormatter.parsedExcessDays()));
        } else {
            try {
                f.withResolverStyle(style).parse(str);
                fail();
            } catch (DateTimeParseException ex) {
                // expected
            }
        }
    }

    //-----------------------------------------------------------------------
    Object[][] data_resolveClockHourOfDay() {
        return new Object[][]{
                {STRICT, 1, 1, 0},
                {STRICT, 24, 0, 0},
                {STRICT, 0, null, 0},
                {STRICT, -1, null, 0},
                {STRICT, 25, null, 0},

                {SMART, 1, 1, 0},
                {SMART, 24, 0, 0},
                {SMART, 0, 0, 0},
                {SMART, -1, null, 0},
                {SMART, 25, null, 0},

                {LENIENT, 1, 1, 0},
                {LENIENT, 24, 0, 0},
                {LENIENT, 0, 0, 0},
                {LENIENT, -1, 23, -1},
                {LENIENT, 25, 1, 1},
        };
    }

    @ParameterizedTest
    @MethodSource("data_resolveClockHourOfDay")
    public void test_resolveClockHourOfDay(ResolverStyle style, long value, Integer expectedHour, int expectedDays) {
        String str = Long.toString(value);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(CLOCK_HOUR_OF_DAY).toFormatter();

        if (expectedHour != null) {
            TemporalAccessor accessor = f.withResolverStyle(style).parse(str);
            assertEquals(null, accessor.query(TemporalQueries.localDate()));
            assertEquals(LocalTime.of(expectedHour, 0), accessor.query(TemporalQueries.localTime()));
            assertEquals(Period.ofDays(expectedDays), accessor.query(DateTimeFormatter.parsedExcessDays()));
        } else {
            try {
                f.withResolverStyle(style).parse(str);
                fail();
            } catch (DateTimeParseException ex) {
                // expected
            }
        }
    }

    //-----------------------------------------------------------------------
    Object[][] data_resolveClockHourOfAmPm() {
        return new Object[][]{
                {STRICT, 1, 1},
                {STRICT, 12, 0},
                {STRICT, 0, null},
                {STRICT, -1, null},
                {STRICT, 13, null},

                {SMART, 1, 1},
                {SMART, 12, 0},
                {SMART, 0, 0},
                {SMART, -1, null},
                {SMART, 13, null},

                {LENIENT, 1, 1},
                {LENIENT, 12, 0},
                {LENIENT, 0, 0},
                {LENIENT, -1, -1},
                {LENIENT, 13, 13},
        };
    }

    @ParameterizedTest
    @MethodSource("data_resolveClockHourOfAmPm")
    public void test_resolveClockHourOfAmPm(ResolverStyle style, long value, Integer expectedValue) {
        String str = Long.toString(value);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(CLOCK_HOUR_OF_AMPM).toFormatter();

        if (expectedValue != null) {
            TemporalAccessor accessor = f.withResolverStyle(style).parse(str);
            assertEquals(null, accessor.query(TemporalQueries.localDate()));
            assertEquals(null, accessor.query(TemporalQueries.localTime()));
            assertEquals(false, accessor.isSupported(CLOCK_HOUR_OF_AMPM));
            assertEquals(true, accessor.isSupported(HOUR_OF_AMPM));
            assertEquals(expectedValue.longValue(), accessor.getLong(HOUR_OF_AMPM));
        } else {
            try {
                f.withResolverStyle(style).parse(str);
                fail();
            } catch (DateTimeParseException ex) {
                // expected
            }
        }
    }

    //-----------------------------------------------------------------------
    Object[][] data_resolveAmPm() {
        return new Object[][]{
                {STRICT, 0, null, 0},
                {STRICT, 1, null, 1},
                {STRICT, -1, null, null},
                {STRICT, 2, null, null},

                {SMART, 0, LocalTime.of(6, 0), 0},
                {SMART, 1, LocalTime.of(18, 0), 1},
                {SMART, -1, null, null},
                {SMART, 2, null, null},

                {LENIENT, 0, LocalTime.of(6, 0), 0},
                {LENIENT, 1, LocalTime.of(18, 0), 1},
                {LENIENT, -1, LocalTime.of(18, 0), 1},
                {LENIENT, 2, LocalTime.of(6, 0), 0},
        };
    }

    @ParameterizedTest
    @MethodSource("data_resolveAmPm")
    public void test_resolveAmPm(ResolverStyle style, long value, LocalTime expectedTime, Integer expectedValue) {
        String str = Long.toString(value);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(AMPM_OF_DAY).toFormatter();

        if (expectedValue != null) {
            TemporalAccessor accessor = f.withResolverStyle(style).parse(str);
            assertEquals(null, accessor.query(TemporalQueries.localDate()));
            assertEquals(expectedTime, accessor.query(TemporalQueries.localTime()));
            assertEquals(true, accessor.isSupported(AMPM_OF_DAY));
            assertEquals(expectedValue.longValue(), accessor.getLong(AMPM_OF_DAY));
        } else {
            try {
                f.withResolverStyle(style).parse(str);
                fail();
            } catch (DateTimeParseException ex) {
                // expected
            }
        }
    }

    //-----------------------------------------------------------------------
    // SPEC: DateTimeFormatter.withChronology()
    @Test
    public void test_withChronology_noOverride() {
        DateTimeFormatter f = new DateTimeFormatterBuilder().parseDefaulting(EPOCH_DAY, 2).toFormatter();
        TemporalAccessor accessor = f.parse("");
        assertEquals(LocalDate.of(1970, 1, 3), accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        assertEquals(IsoChronology.INSTANCE, accessor.query(TemporalQueries.chronology()));
    }

    @Test
    public void test_withChronology_override() {
        DateTimeFormatter f = new DateTimeFormatterBuilder().parseDefaulting(EPOCH_DAY, 2).toFormatter();
        f = f.withChronology(MinguoChronology.INSTANCE);
        TemporalAccessor accessor = f.parse("");
        assertEquals(LocalDate.of(1970, 1, 3), accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        assertEquals(MinguoChronology.INSTANCE, accessor.query(TemporalQueries.chronology()));
    }

    @Test
    public void test_withChronology_parsedChronology_noOverride() {
        DateTimeFormatter f = new DateTimeFormatterBuilder().parseDefaulting(EPOCH_DAY, 2).appendChronologyId().toFormatter();
        TemporalAccessor accessor = f.parse("ThaiBuddhist");
        assertEquals(LocalDate.of(1970, 1, 3), accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        assertEquals(ThaiBuddhistChronology.INSTANCE, accessor.query(TemporalQueries.chronology()));
    }

    @Test
    public void test_withChronology_parsedChronology_override() {
        DateTimeFormatter f = new DateTimeFormatterBuilder().parseDefaulting(EPOCH_DAY, 2).appendChronologyId().toFormatter();
        f = f.withChronology(MinguoChronology.INSTANCE);
        TemporalAccessor accessor = f.parse("ThaiBuddhist");
        assertEquals(LocalDate.of(1970, 1, 3), accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        assertEquals(ThaiBuddhistChronology.INSTANCE, accessor.query(TemporalQueries.chronology()));
    }

    //-----------------------------------------------------------------------
    // SPEC: DateTimeFormatter.withZone()
    @Test
    public void test_withZone_noOverride() {
        DateTimeFormatter f = new DateTimeFormatterBuilder().parseDefaulting(EPOCH_DAY, 2).toFormatter();
        TemporalAccessor accessor = f.parse("");
        assertEquals(LocalDate.of(1970, 1, 3), accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        assertEquals(null, accessor.query(TemporalQueries.zoneId()));
    }

    @Test
    public void test_withZone_override() {
        DateTimeFormatter f = new DateTimeFormatterBuilder().parseDefaulting(EPOCH_DAY, 2).toFormatter();
        f = f.withZone(EUROPE_ATHENS);
        TemporalAccessor accessor = f.parse("");
        assertEquals(LocalDate.of(1970, 1, 3), accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        assertEquals(EUROPE_ATHENS, accessor.query(TemporalQueries.zoneId()));
    }

    @Test
    public void test_withZone_parsedZone_noOverride() {
        DateTimeFormatter f = new DateTimeFormatterBuilder().parseDefaulting(EPOCH_DAY, 2).appendZoneId().toFormatter();
        TemporalAccessor accessor = f.parse("Europe/Paris");
        assertEquals(LocalDate.of(1970, 1, 3), accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        assertEquals(EUROPE_PARIS, accessor.query(TemporalQueries.zoneId()));
    }

    @Test
    public void test_withZone_parsedZone_override() {
        DateTimeFormatter f = new DateTimeFormatterBuilder().parseDefaulting(EPOCH_DAY, 2).appendZoneId().toFormatter();
        f = f.withZone(EUROPE_ATHENS);
        TemporalAccessor accessor = f.parse("Europe/Paris");
        assertEquals(LocalDate.of(1970, 1, 3), accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        assertEquals(EUROPE_PARIS, accessor.query(TemporalQueries.zoneId()));
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_fieldResolvesToLocalTime() {
        LocalTime lt = LocalTime.of(12, 30, 40);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(lt)).toFormatter();
        TemporalAccessor accessor = f.parse("1234567890");
        assertEquals(null, accessor.query(TemporalQueries.localDate()));
        assertEquals(lt, accessor.query(TemporalQueries.localTime()));
    }

    //-------------------------------------------------------------------------
    @Test
    public void test_fieldResolvesToChronoLocalDate_noOverrideChrono_matches() {
        LocalDate ldt = LocalDate.of(2010, 6, 30);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(ldt)).toFormatter();
        TemporalAccessor accessor = f.parse("1234567890");
        assertEquals(LocalDate.of(2010, 6, 30), accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        assertEquals(IsoChronology.INSTANCE, accessor.query(TemporalQueries.chronology()));
    }

    @Test
    public void test_fieldResolvesToChronoLocalDate_overrideChrono_matches() {
        MinguoDate mdt = MinguoDate.of(100, 6, 30);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(mdt)).toFormatter();
        f = f.withChronology(MinguoChronology.INSTANCE);
        TemporalAccessor accessor = f.parse("1234567890");
        assertEquals(LocalDate.from(mdt), accessor.query(TemporalQueries.localDate()));
        assertEquals(null, accessor.query(TemporalQueries.localTime()));
        assertEquals(MinguoChronology.INSTANCE, accessor.query(TemporalQueries.chronology()));
    }

    @Test
    public void test_fieldResolvesToChronoLocalDate_noOverrideChrono_wrongChrono() {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            ChronoLocalDate cld = ThaiBuddhistChronology.INSTANCE.dateNow();
            DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(cld)).toFormatter();
            f.parse("1234567890");
        });
    }

    @Test
    public void test_fieldResolvesToChronoLocalDate_overrideChrono_wrongChrono() {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            ChronoLocalDate cld = ThaiBuddhistChronology.INSTANCE.dateNow();
            DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(cld)).toFormatter();
            f = f.withChronology(MinguoChronology.INSTANCE);
            f.parse("1234567890");
        });
    }

    //-------------------------------------------------------------------------
    @Test
    public void test_fieldResolvesToChronoLocalDateTime_noOverrideChrono_matches() {
        LocalDateTime ldt = LocalDateTime.of(2010, 6, 30, 12, 30);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(ldt)).toFormatter();
        TemporalAccessor accessor = f.parse("1234567890");
        assertEquals(LocalDate.of(2010, 6, 30), accessor.query(TemporalQueries.localDate()));
        assertEquals(LocalTime.of(12, 30), accessor.query(TemporalQueries.localTime()));
        assertEquals(IsoChronology.INSTANCE, accessor.query(TemporalQueries.chronology()));
    }

    @Test
    public void test_fieldResolvesToChronoLocalDateTime_overrideChrono_matches() {
        MinguoDate mdt = MinguoDate.of(100, 6, 30);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(mdt.atTime(LocalTime.NOON))).toFormatter();
        f = f.withChronology(MinguoChronology.INSTANCE);
        TemporalAccessor accessor = f.parse("1234567890");
        assertEquals(LocalDate.from(mdt), accessor.query(TemporalQueries.localDate()));
        assertEquals(LocalTime.NOON, accessor.query(TemporalQueries.localTime()));
        assertEquals(MinguoChronology.INSTANCE, accessor.query(TemporalQueries.chronology()));
    }

    @Test
    public void test_fieldResolvesToChronoLocalDateTime_noOverrideChrono_wrongChrono() {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            ChronoLocalDateTime<?> cldt = ThaiBuddhistChronology.INSTANCE.dateNow().atTime(LocalTime.NOON);
            DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(cldt)).toFormatter();
            f.parse("1234567890");
        });
    }

    @Test
    public void test_fieldResolvesToChronoLocalDateTime_overrideChrono_wrongChrono() {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            ChronoLocalDateTime<?> cldt = ThaiBuddhistChronology.INSTANCE.dateNow().atTime(LocalTime.NOON);
            DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(cldt)).toFormatter();
            f = f.withChronology(MinguoChronology.INSTANCE);
            f.parse("1234567890");
        });
    }

    //-------------------------------------------------------------------------
    @Test
    public void test_fieldResolvesToChronoZonedDateTime_noOverrideChrono_matches() {
        ZonedDateTime zdt = ZonedDateTime.of(2010, 6, 30, 12, 30, 0, 0, EUROPE_PARIS);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(zdt)).toFormatter();
        TemporalAccessor accessor = f.parse("1234567890");
        assertEquals(LocalDate.of(2010, 6, 30), accessor.query(TemporalQueries.localDate()));
        assertEquals(LocalTime.of(12, 30), accessor.query(TemporalQueries.localTime()));
        assertEquals(IsoChronology.INSTANCE, accessor.query(TemporalQueries.chronology()));
        assertEquals(EUROPE_PARIS, accessor.query(TemporalQueries.zoneId()));
    }

    @Test
    public void test_fieldResolvesToChronoZonedDateTime_overrideChrono_matches() {
        MinguoDate mdt = MinguoDate.of(100, 6, 30);
        ChronoZonedDateTime<MinguoDate> mzdt = mdt.atTime(LocalTime.NOON).atZone(EUROPE_PARIS);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(mzdt)).toFormatter();
        f = f.withChronology(MinguoChronology.INSTANCE);
        TemporalAccessor accessor = f.parse("1234567890");
        assertEquals(LocalDate.from(mdt), accessor.query(TemporalQueries.localDate()));
        assertEquals(LocalTime.NOON, accessor.query(TemporalQueries.localTime()));
        assertEquals(MinguoChronology.INSTANCE, accessor.query(TemporalQueries.chronology()));
        assertEquals(EUROPE_PARIS, accessor.query(TemporalQueries.zoneId()));
    }

    @Test
    public void test_fieldResolvesToChronoZonedDateTime_noOverrideChrono_wrongChrono() {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            ChronoZonedDateTime<?> cldt = ThaiBuddhistChronology.INSTANCE.dateNow().atTime(LocalTime.NOON).atZone(EUROPE_PARIS);
            DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(cldt)).toFormatter();
            f.parse("1234567890");
        });
    }

    @Test
    public void test_fieldResolvesToChronoZonedDateTime_overrideChrono_wrongChrono() {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            ChronoZonedDateTime<?> cldt = ThaiBuddhistChronology.INSTANCE.dateNow().atTime(LocalTime.NOON).atZone(EUROPE_PARIS);
            DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(cldt)).toFormatter();
            f = f.withChronology(MinguoChronology.INSTANCE);
            f.parse("1234567890");
        });
    }

    @Test
    public void test_fieldResolvesToChronoZonedDateTime_overrideZone_matches() {
        ZonedDateTime zdt = ZonedDateTime.of(2010, 6, 30, 12, 30, 0, 0, EUROPE_PARIS);
        DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(zdt)).toFormatter();
        f = f.withZone(EUROPE_PARIS);
        assertEquals(zdt, f.parse("1234567890", ZonedDateTime::from));
    }

    @Test
    public void test_fieldResolvesToChronoZonedDateTime_overrideZone_wrongZone() {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            ZonedDateTime zdt = ZonedDateTime.of(2010, 6, 30, 12, 30, 0, 0, EUROPE_PARIS);
            DateTimeFormatter f = new DateTimeFormatterBuilder().appendValue(new ResolvingField(zdt)).toFormatter();
            f = f.withZone(ZoneId.of("Europe/London"));
            f.parse("1234567890");
        });
    }

    //-------------------------------------------------------------------------
    private static class ResolvingField implements TemporalField {
        private final TemporalAccessor resolvedValue;
        ResolvingField(TemporalAccessor resolvedValue) {
            this.resolvedValue = resolvedValue;
        }
        @Override
        public TemporalUnit getBaseUnit() {
            throw new UnsupportedOperationException();
        }
        @Override
        public TemporalUnit getRangeUnit() {
            throw new UnsupportedOperationException();
        }
        @Override
        public ValueRange range() {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean isDateBased() {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean isTimeBased() {
            throw new UnsupportedOperationException();
        }
        @Override
        public boolean isSupportedBy(TemporalAccessor temporal) {
            throw new UnsupportedOperationException();
        }
        @Override
        public ValueRange rangeRefinedBy(TemporalAccessor temporal) {
            throw new UnsupportedOperationException();
        }
        @Override
        public long getFrom(TemporalAccessor temporal) {
            throw new UnsupportedOperationException();
        }
        @Override
        public <R extends Temporal> R adjustInto(R temporal, long newValue) {
            throw new UnsupportedOperationException();
        }
        @Override
        public TemporalAccessor resolve(
                Map<TemporalField, Long> fieldValues, TemporalAccessor partialTemporal, ResolverStyle resolverStyle) {
            fieldValues.remove(this);
            return resolvedValue;
        }
    };

    //-------------------------------------------------------------------------
    // SPEC: ChronoField.INSTANT_SECONDS
    @Test
    public void test_parse_fromField_InstantSeconds() {
        DateTimeFormatter fmt = new DateTimeFormatterBuilder()
            .appendValue(INSTANT_SECONDS).toFormatter();
        TemporalAccessor acc = fmt.parse("86402");
        Instant expected = Instant.ofEpochSecond(86402);
        assertEquals(true, acc.isSupported(INSTANT_SECONDS));
        assertEquals(true, acc.isSupported(NANO_OF_SECOND));
        assertEquals(true, acc.isSupported(MICRO_OF_SECOND));
        assertEquals(true, acc.isSupported(MILLI_OF_SECOND));
        assertEquals(86402L, acc.getLong(INSTANT_SECONDS));
        assertEquals(0L, acc.getLong(NANO_OF_SECOND));
        assertEquals(0L, acc.getLong(MICRO_OF_SECOND));
        assertEquals(0L, acc.getLong(MILLI_OF_SECOND));
        assertEquals(expected, Instant.from(acc));
    }

    @Test
    public void test_parse_fromField_InstantSeconds_NanoOfSecond() {
        DateTimeFormatter fmt = new DateTimeFormatterBuilder()
            .appendValue(INSTANT_SECONDS).appendLiteral('.').appendValue(NANO_OF_SECOND).toFormatter();
        TemporalAccessor acc = fmt.parse("86402.123456789");
        Instant expected = Instant.ofEpochSecond(86402, 123456789);
        assertEquals(true, acc.isSupported(INSTANT_SECONDS));
        assertEquals(true, acc.isSupported(NANO_OF_SECOND));
        assertEquals(true, acc.isSupported(MICRO_OF_SECOND));
        assertEquals(true, acc.isSupported(MILLI_OF_SECOND));
        assertEquals(86402L, acc.getLong(INSTANT_SECONDS));
        assertEquals(123456789L, acc.getLong(NANO_OF_SECOND));
        assertEquals(123456L, acc.getLong(MICRO_OF_SECOND));
        assertEquals(123L, acc.getLong(MILLI_OF_SECOND));
        assertEquals(expected, Instant.from(acc));
    }

    // SPEC: ChronoField.SECOND_OF_DAY
    @Test
    public void test_parse_fromField_SecondOfDay() {
        DateTimeFormatter fmt = new DateTimeFormatterBuilder()
            .appendValue(SECOND_OF_DAY).toFormatter();
        TemporalAccessor acc = fmt.parse("864");
        assertEquals(true, acc.isSupported(SECOND_OF_DAY));
        assertEquals(true, acc.isSupported(NANO_OF_SECOND));
        assertEquals(true, acc.isSupported(MICRO_OF_SECOND));
        assertEquals(true, acc.isSupported(MILLI_OF_SECOND));
        assertEquals(864L, acc.getLong(SECOND_OF_DAY));
        assertEquals(0L, acc.getLong(NANO_OF_SECOND));
        assertEquals(0L, acc.getLong(MICRO_OF_SECOND));
        assertEquals(0L, acc.getLong(MILLI_OF_SECOND));
    }

    @Test
    public void test_parse_fromField_SecondOfDay_NanoOfSecond() {
        DateTimeFormatter fmt = new DateTimeFormatterBuilder()
            .appendValue(SECOND_OF_DAY).appendLiteral('.').appendValue(NANO_OF_SECOND).toFormatter();
        TemporalAccessor acc = fmt.parse("864.123456789");
        assertEquals(true, acc.isSupported(SECOND_OF_DAY));
        assertEquals(true, acc.isSupported(NANO_OF_SECOND));
        assertEquals(true, acc.isSupported(MICRO_OF_SECOND));
        assertEquals(true, acc.isSupported(MILLI_OF_SECOND));
        assertEquals(864L, acc.getLong(SECOND_OF_DAY));
        assertEquals(123456789L, acc.getLong(NANO_OF_SECOND));
        assertEquals(123456L, acc.getLong(MICRO_OF_SECOND));
        assertEquals(123L, acc.getLong(MILLI_OF_SECOND));
    }

    // SPEC: ChronoField.SECOND_OF_MINUTE
    @Test
    public void test_parse_fromField_SecondOfMinute() {
        DateTimeFormatter fmt = new DateTimeFormatterBuilder()
            .appendValue(SECOND_OF_MINUTE).toFormatter();
        TemporalAccessor acc = fmt.parse("32");
        assertEquals(true, acc.isSupported(SECOND_OF_MINUTE));
        assertEquals(true, acc.isSupported(NANO_OF_SECOND));
        assertEquals(true, acc.isSupported(MICRO_OF_SECOND));
        assertEquals(true, acc.isSupported(MILLI_OF_SECOND));
        assertEquals(32L, acc.getLong(SECOND_OF_MINUTE));
        assertEquals(0L, acc.getLong(NANO_OF_SECOND));
        assertEquals(0L, acc.getLong(MICRO_OF_SECOND));
        assertEquals(0L, acc.getLong(MILLI_OF_SECOND));
    }

    @Test
    public void test_parse_fromField_SecondOfMinute_NanoOfSecond() {
        DateTimeFormatter fmt = new DateTimeFormatterBuilder()
            .appendValue(SECOND_OF_MINUTE).appendLiteral('.').appendValue(NANO_OF_SECOND).toFormatter();
        TemporalAccessor acc = fmt.parse("32.123456789");
        assertEquals(true, acc.isSupported(SECOND_OF_MINUTE));
        assertEquals(true, acc.isSupported(NANO_OF_SECOND));
        assertEquals(true, acc.isSupported(MICRO_OF_SECOND));
        assertEquals(true, acc.isSupported(MILLI_OF_SECOND));
        assertEquals(32L, acc.getLong(SECOND_OF_MINUTE));
        assertEquals(123456789L, acc.getLong(NANO_OF_SECOND));
        assertEquals(123456L, acc.getLong(MICRO_OF_SECOND));
        assertEquals(123L, acc.getLong(MILLI_OF_SECOND));
    }

}
