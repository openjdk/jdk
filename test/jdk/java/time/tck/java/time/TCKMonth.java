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

import static java.time.temporal.ChronoField.MONTH_OF_YEAR;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.chrono.IsoChronology;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.JulianFields;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test Month.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TCKMonth extends AbstractDateTimeTest {

    private static final int MAX_LENGTH = 12;

    //-----------------------------------------------------------------------
    @Override
    protected List<TemporalAccessor> samples() {
        TemporalAccessor[] array = {Month.JANUARY, Month.JUNE, Month.DECEMBER, };
        return Arrays.asList(array);
    }

    @Override
    protected List<TemporalField> validFields() {
        TemporalField[] array = {
            MONTH_OF_YEAR,
        };
        return Arrays.asList(array);
    }

    @Override
    protected List<TemporalField> invalidFields() {
        List<TemporalField> list = new ArrayList<>(Arrays.<TemporalField>asList(ChronoField.values()));
        list.removeAll(validFields());
        list.add(JulianFields.JULIAN_DAY);
        list.add(JulianFields.MODIFIED_JULIAN_DAY);
        list.add(JulianFields.RATA_DIE);
        return list;
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_factory_int_singleton() {
        for (int i = 1; i <= MAX_LENGTH; i++) {
            Month test = Month.of(i);
            assertEquals(i, test.getValue());
        }
    }

    @Test
    public void test_factory_int_tooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> Month.of(0));
    }

    @Test
    public void test_factory_int_tooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> Month.of(13));
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_factory_CalendricalObject() {
        assertEquals(Month.JUNE, Month.from(LocalDate.of(2011, 6, 6)));
    }

    @Test
    public void test_factory_CalendricalObject_invalid_noDerive() {
        Assertions.assertThrows(DateTimeException.class, () -> Month.from(LocalTime.of(12, 30)));
    }

    @Test
    public void test_factory_CalendricalObject_null() {
        Assertions.assertThrows(NullPointerException.class, () -> Month.from((TemporalAccessor) null));
    }

    //-----------------------------------------------------------------------
    // isSupported(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_isSupported_TemporalField() {
        assertEquals(false, Month.AUGUST.isSupported((TemporalField) null));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.NANO_OF_SECOND));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.NANO_OF_DAY));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.MICRO_OF_SECOND));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.MICRO_OF_DAY));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.MILLI_OF_SECOND));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.MILLI_OF_DAY));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.SECOND_OF_MINUTE));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.SECOND_OF_DAY));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.MINUTE_OF_HOUR));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.MINUTE_OF_DAY));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.HOUR_OF_AMPM));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.CLOCK_HOUR_OF_AMPM));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.HOUR_OF_DAY));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.CLOCK_HOUR_OF_DAY));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.AMPM_OF_DAY));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.DAY_OF_WEEK));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.DAY_OF_MONTH));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.DAY_OF_YEAR));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.EPOCH_DAY));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.ALIGNED_WEEK_OF_MONTH));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.ALIGNED_WEEK_OF_YEAR));
        assertEquals(true, Month.AUGUST.isSupported(ChronoField.MONTH_OF_YEAR));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.PROLEPTIC_MONTH));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.YEAR));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.YEAR_OF_ERA));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.ERA));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.INSTANT_SECONDS));
        assertEquals(false, Month.AUGUST.isSupported(ChronoField.OFFSET_SECONDS));
    }

    //-----------------------------------------------------------------------
    // get(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_get_TemporalField() {
        assertEquals(7, Month.JULY.get(ChronoField.MONTH_OF_YEAR));
    }

    @Test
    public void test_getLong_TemporalField() {
        assertEquals(7, Month.JULY.getLong(ChronoField.MONTH_OF_YEAR));
    }

    //-----------------------------------------------------------------------
    // query(TemporalQuery)
    //-----------------------------------------------------------------------
    Object[][] data_query() {
        return new Object[][] {
                {Month.JUNE, TemporalQueries.chronology(), IsoChronology.INSTANCE},
                {Month.JUNE, TemporalQueries.zoneId(), null},
                {Month.JUNE, TemporalQueries.precision(), ChronoUnit.MONTHS},
                {Month.JUNE, TemporalQueries.zone(), null},
                {Month.JUNE, TemporalQueries.offset(), null},
                {Month.JUNE, TemporalQueries.localDate(), null},
                {Month.JUNE, TemporalQueries.localTime(), null},
        };
    }

    @ParameterizedTest
    @MethodSource("data_query")
    public <T> void test_query(TemporalAccessor temporal, TemporalQuery<T> query, T expected) {
        assertEquals(expected, temporal.query(query));
    }

    @ParameterizedTest
    @MethodSource("data_query")
    public <T> void test_queryFrom(TemporalAccessor temporal, TemporalQuery<T> query, T expected) {
        assertEquals(expected, query.queryFrom(temporal));
    }

    @Test
    public void test_query_null() {
        Assertions.assertThrows(NullPointerException.class, () -> Month.JUNE.query(null));
    }

    //-----------------------------------------------------------------------
    // getText()
    //-----------------------------------------------------------------------
    @Test
    public void test_getText() {
        assertEquals("Jan", Month.JANUARY.getDisplayName(TextStyle.SHORT, Locale.US));
    }

    @Test
    public void test_getText_nullStyle() {
        Assertions.assertThrows(NullPointerException.class, () -> Month.JANUARY.getDisplayName(null, Locale.US));
    }

    @Test
    public void test_getText_nullLocale() {
        Assertions.assertThrows(NullPointerException.class, () -> Month.JANUARY.getDisplayName(TextStyle.FULL, null));
    }

    //-----------------------------------------------------------------------
    // plus(long), plus(long,unit)
    //-----------------------------------------------------------------------
    Object[][] data_plus() {
        return new Object[][] {
            {1, -13, 12},
            {1, -12, 1},
            {1, -11, 2},
            {1, -10, 3},
            {1, -9, 4},
            {1, -8, 5},
            {1, -7, 6},
            {1, -6, 7},
            {1, -5, 8},
            {1, -4, 9},
            {1, -3, 10},
            {1, -2, 11},
            {1, -1, 12},
            {1, 0, 1},
            {1, 1, 2},
            {1, 2, 3},
            {1, 3, 4},
            {1, 4, 5},
            {1, 5, 6},
            {1, 6, 7},
            {1, 7, 8},
            {1, 8, 9},
            {1, 9, 10},
            {1, 10, 11},
            {1, 11, 12},
            {1, 12, 1},
            {1, 13, 2},

            {1, 1, 2},
            {2, 1, 3},
            {3, 1, 4},
            {4, 1, 5},
            {5, 1, 6},
            {6, 1, 7},
            {7, 1, 8},
            {8, 1, 9},
            {9, 1, 10},
            {10, 1, 11},
            {11, 1, 12},
            {12, 1, 1},

            {1, -1, 12},
            {2, -1, 1},
            {3, -1, 2},
            {4, -1, 3},
            {5, -1, 4},
            {6, -1, 5},
            {7, -1, 6},
            {8, -1, 7},
            {9, -1, 8},
            {10, -1, 9},
            {11, -1, 10},
            {12, -1, 11},
        };
    }

    @ParameterizedTest
    @MethodSource("data_plus")
    public void test_plus_long(int base, long amount, int expected) {
        assertEquals(Month.of(expected), Month.of(base).plus(amount));
    }

    //-----------------------------------------------------------------------
    // minus(long), minus(long,unit)
    //-----------------------------------------------------------------------
    Object[][] data_minus() {
        return new Object[][] {
            {1, -13, 2},
            {1, -12, 1},
            {1, -11, 12},
            {1, -10, 11},
            {1, -9, 10},
            {1, -8, 9},
            {1, -7, 8},
            {1, -6, 7},
            {1, -5, 6},
            {1, -4, 5},
            {1, -3, 4},
            {1, -2, 3},
            {1, -1, 2},
            {1, 0, 1},
            {1, 1, 12},
            {1, 2, 11},
            {1, 3, 10},
            {1, 4, 9},
            {1, 5, 8},
            {1, 6, 7},
            {1, 7, 6},
            {1, 8, 5},
            {1, 9, 4},
            {1, 10, 3},
            {1, 11, 2},
            {1, 12, 1},
            {1, 13, 12},
        };
    }

    @ParameterizedTest
    @MethodSource("data_minus")
    public void test_minus_long(int base, long amount, int expected) {
        assertEquals(Month.of(expected), Month.of(base).minus(amount));
    }

    //-----------------------------------------------------------------------
    // length(boolean)
    //-----------------------------------------------------------------------
    @Test
    public void test_length_boolean_notLeapYear() {
        assertEquals(31, Month.JANUARY.length(false));
        assertEquals(28, Month.FEBRUARY.length(false));
        assertEquals(31, Month.MARCH.length(false));
        assertEquals(30, Month.APRIL.length(false));
        assertEquals(31, Month.MAY.length(false));
        assertEquals(30, Month.JUNE.length(false));
        assertEquals(31, Month.JULY.length(false));
        assertEquals(31, Month.AUGUST.length(false));
        assertEquals(30, Month.SEPTEMBER.length(false));
        assertEquals(31, Month.OCTOBER.length(false));
        assertEquals(30, Month.NOVEMBER.length(false));
        assertEquals(31, Month.DECEMBER.length(false));
    }

    @Test
    public void test_length_boolean_leapYear() {
        assertEquals(31, Month.JANUARY.length(true));
        assertEquals(29, Month.FEBRUARY.length(true));
        assertEquals(31, Month.MARCH.length(true));
        assertEquals(30, Month.APRIL.length(true));
        assertEquals(31, Month.MAY.length(true));
        assertEquals(30, Month.JUNE.length(true));
        assertEquals(31, Month.JULY.length(true));
        assertEquals(31, Month.AUGUST.length(true));
        assertEquals(30, Month.SEPTEMBER.length(true));
        assertEquals(31, Month.OCTOBER.length(true));
        assertEquals(30, Month.NOVEMBER.length(true));
        assertEquals(31, Month.DECEMBER.length(true));
    }

    //-----------------------------------------------------------------------
    // minLength()
    //-----------------------------------------------------------------------
    @Test
    public void test_minLength() {
        assertEquals(31, Month.JANUARY.minLength());
        assertEquals(28, Month.FEBRUARY.minLength());
        assertEquals(31, Month.MARCH.minLength());
        assertEquals(30, Month.APRIL.minLength());
        assertEquals(31, Month.MAY.minLength());
        assertEquals(30, Month.JUNE.minLength());
        assertEquals(31, Month.JULY.minLength());
        assertEquals(31, Month.AUGUST.minLength());
        assertEquals(30, Month.SEPTEMBER.minLength());
        assertEquals(31, Month.OCTOBER.minLength());
        assertEquals(30, Month.NOVEMBER.minLength());
        assertEquals(31, Month.DECEMBER.minLength());
    }

    //-----------------------------------------------------------------------
    // maxLength()
    //-----------------------------------------------------------------------
    @Test
    public void test_maxLength() {
        assertEquals(31, Month.JANUARY.maxLength());
        assertEquals(29, Month.FEBRUARY.maxLength());
        assertEquals(31, Month.MARCH.maxLength());
        assertEquals(30, Month.APRIL.maxLength());
        assertEquals(31, Month.MAY.maxLength());
        assertEquals(30, Month.JUNE.maxLength());
        assertEquals(31, Month.JULY.maxLength());
        assertEquals(31, Month.AUGUST.maxLength());
        assertEquals(30, Month.SEPTEMBER.maxLength());
        assertEquals(31, Month.OCTOBER.maxLength());
        assertEquals(30, Month.NOVEMBER.maxLength());
        assertEquals(31, Month.DECEMBER.maxLength());
    }

    //-----------------------------------------------------------------------
    // firstDayOfYear(boolean)
    //-----------------------------------------------------------------------
    @Test
    public void test_firstDayOfYear_notLeapYear() {
        assertEquals(1, Month.JANUARY.firstDayOfYear(false));
        assertEquals(1 + 31, Month.FEBRUARY.firstDayOfYear(false));
        assertEquals(1 + 31 + 28, Month.MARCH.firstDayOfYear(false));
        assertEquals(1 + 31 + 28 + 31, Month.APRIL.firstDayOfYear(false));
        assertEquals(1 + 31 + 28 + 31 + 30, Month.MAY.firstDayOfYear(false));
        assertEquals(1 + 31 + 28 + 31 + 30 + 31, Month.JUNE.firstDayOfYear(false));
        assertEquals(1 + 31 + 28 + 31 + 30 + 31 + 30, Month.JULY.firstDayOfYear(false));
        assertEquals(1 + 31 + 28 + 31 + 30 + 31 + 30 + 31, Month.AUGUST.firstDayOfYear(false));
        assertEquals(1 + 31 + 28 + 31 + 30 + 31 + 30 + 31 + 31, Month.SEPTEMBER.firstDayOfYear(false));
        assertEquals(1 + 31 + 28 + 31 + 30 + 31 + 30 + 31 + 31 + 30, Month.OCTOBER.firstDayOfYear(false));
        assertEquals(1 + 31 + 28 + 31 + 30 + 31 + 30 + 31 + 31 + 30 + 31, Month.NOVEMBER.firstDayOfYear(false));
        assertEquals(1 + 31 + 28 + 31 + 30 + 31 + 30 + 31 + 31 + 30 + 31 + 30, Month.DECEMBER.firstDayOfYear(false));
    }

    @Test
    public void test_firstDayOfYear_leapYear() {
        assertEquals(1, Month.JANUARY.firstDayOfYear(true));
        assertEquals(1 + 31, Month.FEBRUARY.firstDayOfYear(true));
        assertEquals(1 + 31 + 29, Month.MARCH.firstDayOfYear(true));
        assertEquals(1 + 31 + 29 + 31, Month.APRIL.firstDayOfYear(true));
        assertEquals(1 + 31 + 29 + 31 + 30, Month.MAY.firstDayOfYear(true));
        assertEquals(1 + 31 + 29 + 31 + 30 + 31, Month.JUNE.firstDayOfYear(true));
        assertEquals(1 + 31 + 29 + 31 + 30 + 31 + 30, Month.JULY.firstDayOfYear(true));
        assertEquals(1 + 31 + 29 + 31 + 30 + 31 + 30 + 31, Month.AUGUST.firstDayOfYear(true));
        assertEquals(1 + 31 + 29 + 31 + 30 + 31 + 30 + 31 + 31, Month.SEPTEMBER.firstDayOfYear(true));
        assertEquals(1 + 31 + 29 + 31 + 30 + 31 + 30 + 31 + 31 + 30, Month.OCTOBER.firstDayOfYear(true));
        assertEquals(1 + 31 + 29 + 31 + 30 + 31 + 30 + 31 + 31 + 30 + 31, Month.NOVEMBER.firstDayOfYear(true));
        assertEquals(1 + 31 + 29 + 31 + 30 + 31 + 30 + 31 + 31 + 30 + 31 + 30, Month.DECEMBER.firstDayOfYear(true));
    }

    //-----------------------------------------------------------------------
    // firstMonthOfQuarter()
    //-----------------------------------------------------------------------
    @Test
    public void test_firstMonthOfQuarter() {
        assertEquals(Month.JANUARY, Month.JANUARY.firstMonthOfQuarter());
        assertEquals(Month.JANUARY, Month.FEBRUARY.firstMonthOfQuarter());
        assertEquals(Month.JANUARY, Month.MARCH.firstMonthOfQuarter());
        assertEquals(Month.APRIL, Month.APRIL.firstMonthOfQuarter());
        assertEquals(Month.APRIL, Month.MAY.firstMonthOfQuarter());
        assertEquals(Month.APRIL, Month.JUNE.firstMonthOfQuarter());
        assertEquals(Month.JULY, Month.JULY.firstMonthOfQuarter());
        assertEquals(Month.JULY, Month.AUGUST.firstMonthOfQuarter());
        assertEquals(Month.JULY, Month.SEPTEMBER.firstMonthOfQuarter());
        assertEquals(Month.OCTOBER, Month.OCTOBER.firstMonthOfQuarter());
        assertEquals(Month.OCTOBER, Month.NOVEMBER.firstMonthOfQuarter());
        assertEquals(Month.OCTOBER, Month.DECEMBER.firstMonthOfQuarter());
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @Test
    public void test_toString() {
        assertEquals("JANUARY", Month.JANUARY.toString());
        assertEquals("FEBRUARY", Month.FEBRUARY.toString());
        assertEquals("MARCH", Month.MARCH.toString());
        assertEquals("APRIL", Month.APRIL.toString());
        assertEquals("MAY", Month.MAY.toString());
        assertEquals("JUNE", Month.JUNE.toString());
        assertEquals("JULY", Month.JULY.toString());
        assertEquals("AUGUST", Month.AUGUST.toString());
        assertEquals("SEPTEMBER", Month.SEPTEMBER.toString());
        assertEquals("OCTOBER", Month.OCTOBER.toString());
        assertEquals("NOVEMBER", Month.NOVEMBER.toString());
        assertEquals("DECEMBER", Month.DECEMBER.toString());
    }

    //-----------------------------------------------------------------------
    // generated methods
    //-----------------------------------------------------------------------
    @Test
    public void test_enum() {
        assertEquals(Month.JANUARY, Month.valueOf("JANUARY"));
        assertEquals(Month.JANUARY, Month.values()[0]);
    }

}
