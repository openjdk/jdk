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

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.JulianFields;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test DayOfWeek.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TCKDayOfWeek extends AbstractDateTimeTest {

    @BeforeEach
    public void setUp() {
    }

    //-----------------------------------------------------------------------
    @Override
    protected List<TemporalAccessor> samples() {
        TemporalAccessor[] array = {MONDAY, WEDNESDAY, SUNDAY, };
        return Arrays.asList(array);
    }

    @Override
    protected List<TemporalField> validFields() {
        TemporalField[] array = {
            DAY_OF_WEEK,
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
        for (int i = 1; i <= 7; i++) {
            DayOfWeek test = DayOfWeek.of(i);
            assertEquals(i, test.getValue());
            assertSame(DayOfWeek.of(i), test);
        }
    }

    @Test
    public void test_factory_int_valueTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> DayOfWeek.of(0));
    }

    @Test
    public void test_factory_int_valueTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> DayOfWeek.of(8));
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_factory_CalendricalObject() {
        assertEquals(DayOfWeek.MONDAY, DayOfWeek.from(LocalDate.of(2011, 6, 6)));
    }

    @Test
    public void test_factory_CalendricalObject_invalid_noDerive() {
        Assertions.assertThrows(DateTimeException.class, () -> DayOfWeek.from(LocalTime.of(12, 30)));
    }

    @Test
    public void test_factory_CalendricalObject_null() {
        Assertions.assertThrows(NullPointerException.class, () -> DayOfWeek.from((TemporalAccessor) null));
    }

    //-----------------------------------------------------------------------
    // isSupported(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_isSupported_TemporalField() {
        assertEquals(false, DayOfWeek.THURSDAY.isSupported((TemporalField) null));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.NANO_OF_SECOND));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.NANO_OF_DAY));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.MICRO_OF_SECOND));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.MICRO_OF_DAY));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.MILLI_OF_SECOND));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.MILLI_OF_DAY));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.SECOND_OF_MINUTE));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.SECOND_OF_DAY));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.MINUTE_OF_HOUR));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.MINUTE_OF_DAY));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.HOUR_OF_AMPM));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.CLOCK_HOUR_OF_AMPM));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.HOUR_OF_DAY));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.CLOCK_HOUR_OF_DAY));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.AMPM_OF_DAY));
        assertEquals(true, DayOfWeek.THURSDAY.isSupported(ChronoField.DAY_OF_WEEK));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.DAY_OF_MONTH));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.DAY_OF_YEAR));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.EPOCH_DAY));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.ALIGNED_WEEK_OF_MONTH));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.ALIGNED_WEEK_OF_YEAR));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.MONTH_OF_YEAR));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.PROLEPTIC_MONTH));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.YEAR));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.YEAR_OF_ERA));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.ERA));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.INSTANT_SECONDS));
        assertEquals(false, DayOfWeek.THURSDAY.isSupported(ChronoField.OFFSET_SECONDS));
    }

    //-----------------------------------------------------------------------
    // get(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_get_TemporalField() {
        assertEquals(3, DayOfWeek.WEDNESDAY.getLong(ChronoField.DAY_OF_WEEK));
    }

    @Test
    public void test_getLong_TemporalField() {
        assertEquals(3, DayOfWeek.WEDNESDAY.getLong(ChronoField.DAY_OF_WEEK));
    }

    //-----------------------------------------------------------------------
    // query(TemporalQuery)
    //-----------------------------------------------------------------------
    Object[][] data_query() {
        return new Object[][] {
                {DayOfWeek.FRIDAY, TemporalQueries.chronology(), null},
                {DayOfWeek.FRIDAY, TemporalQueries.zoneId(), null},
                {DayOfWeek.FRIDAY, TemporalQueries.precision(), ChronoUnit.DAYS},
                {DayOfWeek.FRIDAY, TemporalQueries.zone(), null},
                {DayOfWeek.FRIDAY, TemporalQueries.offset(), null},
                {DayOfWeek.FRIDAY, TemporalQueries.localDate(), null},
                {DayOfWeek.FRIDAY, TemporalQueries.localTime(), null},
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
        Assertions.assertThrows(NullPointerException.class, () -> DayOfWeek.FRIDAY.query(null));
    }

    //-----------------------------------------------------------------------
    // getText()
    //-----------------------------------------------------------------------
    @Test
    public void test_getText() {
        assertEquals("Mon", DayOfWeek.MONDAY.getDisplayName(TextStyle.SHORT, Locale.US));
    }

    @Test
    public void test_getText_nullStyle() {
        Assertions.assertThrows(NullPointerException.class, () -> DayOfWeek.MONDAY.getDisplayName(null, Locale.US));
    }

    @Test
    public void test_getText_nullLocale() {
        Assertions.assertThrows(NullPointerException.class, () -> DayOfWeek.MONDAY.getDisplayName(TextStyle.FULL, null));
    }

    //-----------------------------------------------------------------------
    // plus(long), plus(long,unit)
    //-----------------------------------------------------------------------
    Object[][] data_plus() {
        return new Object[][] {
            {1, -8, 7},
            {1, -7, 1},
            {1, -6, 2},
            {1, -5, 3},
            {1, -4, 4},
            {1, -3, 5},
            {1, -2, 6},
            {1, -1, 7},
            {1, 0, 1},
            {1, 1, 2},
            {1, 2, 3},
            {1, 3, 4},
            {1, 4, 5},
            {1, 5, 6},
            {1, 6, 7},
            {1, 7, 1},
            {1, 8, 2},

            {1, 1, 2},
            {2, 1, 3},
            {3, 1, 4},
            {4, 1, 5},
            {5, 1, 6},
            {6, 1, 7},
            {7, 1, 1},

            {1, -1, 7},
            {2, -1, 1},
            {3, -1, 2},
            {4, -1, 3},
            {5, -1, 4},
            {6, -1, 5},
            {7, -1, 6},
        };
    }

    @ParameterizedTest
    @MethodSource("data_plus")
    public void test_plus_long(int base, long amount, int expected) {
        assertEquals(DayOfWeek.of(expected), DayOfWeek.of(base).plus(amount));
    }

    //-----------------------------------------------------------------------
    // minus(long), minus(long,unit)
    //-----------------------------------------------------------------------
    Object[][] data_minus() {
        return new Object[][] {
            {1, -8, 2},
            {1, -7, 1},
            {1, -6, 7},
            {1, -5, 6},
            {1, -4, 5},
            {1, -3, 4},
            {1, -2, 3},
            {1, -1, 2},
            {1, 0, 1},
            {1, 1, 7},
            {1, 2, 6},
            {1, 3, 5},
            {1, 4, 4},
            {1, 5, 3},
            {1, 6, 2},
            {1, 7, 1},
            {1, 8, 7},
        };
    }

    @ParameterizedTest
    @MethodSource("data_minus")
    public void test_minus_long(int base, long amount, int expected) {
        assertEquals(DayOfWeek.of(expected), DayOfWeek.of(base).minus(amount));
    }

    //-----------------------------------------------------------------------
    // adjustInto()
    //-----------------------------------------------------------------------
    @Test
    public void test_adjustInto() {
        assertEquals(LocalDate.of(2012, 8, 27), DayOfWeek.MONDAY.adjustInto(LocalDate.of(2012, 9, 2)));
        assertEquals(LocalDate.of(2012, 9, 3), DayOfWeek.MONDAY.adjustInto(LocalDate.of(2012, 9, 3)));
        assertEquals(LocalDate.of(2012, 9, 3), DayOfWeek.MONDAY.adjustInto(LocalDate.of(2012, 9, 4)));
        assertEquals(LocalDate.of(2012, 9, 10), DayOfWeek.MONDAY.adjustInto(LocalDate.of(2012, 9, 10)));
        assertEquals(LocalDate.of(2012, 9, 10), DayOfWeek.MONDAY.adjustInto(LocalDate.of(2012, 9, 11)));
    }

    @Test
    public void test_adjustInto_null() {
        Assertions.assertThrows(NullPointerException.class, () -> DayOfWeek.MONDAY.adjustInto((Temporal) null));
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @Test
    public void test_toString() {
        assertEquals("MONDAY", DayOfWeek.MONDAY.toString());
        assertEquals("TUESDAY", DayOfWeek.TUESDAY.toString());
        assertEquals("WEDNESDAY", DayOfWeek.WEDNESDAY.toString());
        assertEquals("THURSDAY", DayOfWeek.THURSDAY.toString());
        assertEquals("FRIDAY", DayOfWeek.FRIDAY.toString());
        assertEquals("SATURDAY", DayOfWeek.SATURDAY.toString());
        assertEquals("SUNDAY", DayOfWeek.SUNDAY.toString());
    }

    //-----------------------------------------------------------------------
    // generated methods
    //-----------------------------------------------------------------------
    @Test
    public void test_enum() {
        assertEquals(DayOfWeek.MONDAY, DayOfWeek.valueOf("MONDAY"));
        assertEquals(DayOfWeek.MONDAY, DayOfWeek.values()[0]);
    }

}
