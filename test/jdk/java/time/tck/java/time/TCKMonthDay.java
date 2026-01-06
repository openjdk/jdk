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

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.JulianFields;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test MonthDay.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TCKMonthDay extends AbstractDateTimeTest {

    private MonthDay TEST_07_15;

    @BeforeEach
    public void setUp() {
        TEST_07_15 = MonthDay.of(7, 15);
    }

    //-----------------------------------------------------------------------
    @Override
    protected List<TemporalAccessor> samples() {
        TemporalAccessor[] array = {TEST_07_15, };
        return Arrays.asList(array);
    }

    @Override
    protected List<TemporalField> validFields() {
        TemporalField[] array = {
            DAY_OF_MONTH,
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
    void check(MonthDay test, int m, int d) {
        assertEquals(m, test.getMonth().getValue());
        assertEquals(d, test.getDayOfMonth());
    }

    //-----------------------------------------------------------------------
    // now()
    //-----------------------------------------------------------------------
    @Test
    public void now() {
        MonthDay expected = MonthDay.now(Clock.systemDefaultZone());
        MonthDay test = MonthDay.now();
        for (int i = 0; i < 100; i++) {
            if (expected.equals(test)) {
                return;
            }
            expected = MonthDay.now(Clock.systemDefaultZone());
            test = MonthDay.now();
        }
        assertEquals(expected, test);
    }

    //-----------------------------------------------------------------------
    // now(ZoneId)
    //-----------------------------------------------------------------------
    @Test
    public void now_ZoneId_nullZoneId() {
        Assertions.assertThrows(NullPointerException.class, () -> MonthDay.now((ZoneId) null));
    }

    @Test
    public void now_ZoneId() {
        ZoneId zone = ZoneId.of("UTC+01:02:03");
        MonthDay expected = MonthDay.now(Clock.system(zone));
        MonthDay test = MonthDay.now(zone);
        for (int i = 0; i < 100; i++) {
            if (expected.equals(test)) {
                return;
            }
            expected = MonthDay.now(Clock.system(zone));
            test = MonthDay.now(zone);
        }
        assertEquals(expected, test);
    }

    //-----------------------------------------------------------------------
    // now(Clock)
    //-----------------------------------------------------------------------
    @Test
    public void now_Clock() {
        Instant instant = LocalDateTime.of(2010, 12, 31, 0, 0).toInstant(ZoneOffset.UTC);
        Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
        MonthDay test = MonthDay.now(clock);
        assertEquals(Month.DECEMBER, test.getMonth());
        assertEquals(31, test.getDayOfMonth());
    }

    @Test
    public void now_Clock_nullClock() {
        Assertions.assertThrows(NullPointerException.class, () -> MonthDay.now((Clock) null));
    }

    //-----------------------------------------------------------------------
    @Test
    public void factory_intMonth() {
        assertEquals(MonthDay.of(Month.JULY, 15), TEST_07_15);
    }

    @Test
    public void test_factory_intMonth_dayTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> MonthDay.of(Month.JANUARY, 0));
    }

    @Test
    public void test_factory_intMonth_dayTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> MonthDay.of(Month.JANUARY, 32));
    }

    @Test
    public void factory_intMonth_nullMonth() {
        Assertions.assertThrows(NullPointerException.class, () -> MonthDay.of(null, 15));
    }

    //-----------------------------------------------------------------------
    @Test
    public void factory_ints() {
        check(TEST_07_15, 7, 15);
    }

    @Test
    public void test_factory_ints_dayTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> MonthDay.of(1, 0));
    }

    @Test
    public void test_factory_ints_dayTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> MonthDay.of(1, 32));
    }


    @Test
    public void test_factory_ints_monthTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> MonthDay.of(0, 1));
    }

    @Test
    public void test_factory_ints_monthTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> MonthDay.of(13, 1));
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_factory_CalendricalObject() {
        assertEquals(TEST_07_15, MonthDay.from(LocalDate.of(2007, 7, 15)));
    }

    @Test
    public void test_factory_CalendricalObject_invalid_noDerive() {
        Assertions.assertThrows(DateTimeException.class, () -> MonthDay.from(LocalTime.of(12, 30)));
    }

    @Test
    public void test_factory_CalendricalObject_null() {
        Assertions.assertThrows(NullPointerException.class, () -> MonthDay.from((TemporalAccessor) null));
    }

    //-----------------------------------------------------------------------
    // parse()
    //-----------------------------------------------------------------------
    Object[][] provider_goodParseData() {
        return new Object[][] {
                {"--01-01", MonthDay.of(1, 1)},
                {"--01-31", MonthDay.of(1, 31)},
                {"--02-01", MonthDay.of(2, 1)},
                {"--02-29", MonthDay.of(2, 29)},
                {"--03-01", MonthDay.of(3, 1)},
                {"--03-31", MonthDay.of(3, 31)},
                {"--04-01", MonthDay.of(4, 1)},
                {"--04-30", MonthDay.of(4, 30)},
                {"--05-01", MonthDay.of(5, 1)},
                {"--05-31", MonthDay.of(5, 31)},
                {"--06-01", MonthDay.of(6, 1)},
                {"--06-30", MonthDay.of(6, 30)},
                {"--07-01", MonthDay.of(7, 1)},
                {"--07-31", MonthDay.of(7, 31)},
                {"--08-01", MonthDay.of(8, 1)},
                {"--08-31", MonthDay.of(8, 31)},
                {"--09-01", MonthDay.of(9, 1)},
                {"--09-30", MonthDay.of(9, 30)},
                {"--10-01", MonthDay.of(10, 1)},
                {"--10-31", MonthDay.of(10, 31)},
                {"--11-01", MonthDay.of(11, 1)},
                {"--11-30", MonthDay.of(11, 30)},
                {"--12-01", MonthDay.of(12, 1)},
                {"--12-31", MonthDay.of(12, 31)},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_goodParseData")
    public void factory_parse_success(String text, MonthDay expected) {
        MonthDay monthDay = MonthDay.parse(text);
        assertEquals(expected, monthDay);
    }

    //-----------------------------------------------------------------------
    Object[][] provider_badParseData() {
        return new Object[][] {
                {"", 0},
                {"-00", 0},
                {"--FEB-23", 2},
                {"--01-0", 5},
                {"--01-3A", 5},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_badParseData")
    public void factory_parse_fail(String text, int pos) {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            try {
                MonthDay.parse(text);
                fail(String.format("Parse should have failed for %s at position %d", text, pos));
            } catch (DateTimeParseException ex) {
                assertEquals(text, ex.getParsedString());
                assertEquals(pos, ex.getErrorIndex());
                throw ex;
            }
        });
    }

    //-----------------------------------------------------------------------
    @Test
    public void factory_parse_illegalValue_Day() {
        Assertions.assertThrows(DateTimeParseException.class, () -> MonthDay.parse("--06-32"));
    }

    @Test
    public void factory_parse_invalidValue_Day() {
        Assertions.assertThrows(DateTimeParseException.class, () -> MonthDay.parse("--06-31"));
    }

    @Test
    public void factory_parse_illegalValue_Month() {
        Assertions.assertThrows(DateTimeParseException.class, () -> MonthDay.parse("--13-25"));
    }

    @Test
    public void factory_parse_nullText() {
        Assertions.assertThrows(NullPointerException.class, () -> MonthDay.parse(null));
    }

    //-----------------------------------------------------------------------
    // parse(DateTimeFormatter)
    //-----------------------------------------------------------------------
    @Test
    public void factory_parse_formatter() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("M d");
        MonthDay test = MonthDay.parse("12 3", f);
        assertEquals(MonthDay.of(12, 3), test);
    }

    @Test
    public void factory_parse_formatter_nullText() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("M d");
            MonthDay.parse((String) null, f);
        });
    }

    @Test
    public void factory_parse_formatter_nullFormatter() {
        Assertions.assertThrows(NullPointerException.class, () -> MonthDay.parse("ANY", null));
    }

    //-----------------------------------------------------------------------
    // isSupported(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_isSupported_TemporalField() {
        assertEquals(false, TEST_07_15.isSupported((TemporalField) null));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.NANO_OF_SECOND));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.NANO_OF_DAY));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.MICRO_OF_SECOND));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.MICRO_OF_DAY));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.MILLI_OF_SECOND));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.MILLI_OF_DAY));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.SECOND_OF_MINUTE));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.SECOND_OF_DAY));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.MINUTE_OF_HOUR));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.MINUTE_OF_DAY));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.HOUR_OF_AMPM));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.CLOCK_HOUR_OF_AMPM));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.HOUR_OF_DAY));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.CLOCK_HOUR_OF_DAY));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.AMPM_OF_DAY));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.DAY_OF_WEEK));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR));
        assertEquals(true, TEST_07_15.isSupported(ChronoField.DAY_OF_MONTH));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.DAY_OF_YEAR));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.EPOCH_DAY));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.ALIGNED_WEEK_OF_MONTH));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.ALIGNED_WEEK_OF_YEAR));
        assertEquals(true, TEST_07_15.isSupported(ChronoField.MONTH_OF_YEAR));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.PROLEPTIC_MONTH));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.YEAR));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.YEAR_OF_ERA));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.ERA));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.INSTANT_SECONDS));
        assertEquals(false, TEST_07_15.isSupported(ChronoField.OFFSET_SECONDS));
    }

    //-----------------------------------------------------------------------
    // get(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_get_TemporalField() {
        assertEquals(15, TEST_07_15.get(ChronoField.DAY_OF_MONTH));
        assertEquals(7, TEST_07_15.get(ChronoField.MONTH_OF_YEAR));
    }

    @Test
    public void test_getLong_TemporalField() {
        assertEquals(15, TEST_07_15.getLong(ChronoField.DAY_OF_MONTH));
        assertEquals(7, TEST_07_15.getLong(ChronoField.MONTH_OF_YEAR));
    }

    //-----------------------------------------------------------------------
    // query(TemporalQuery)
    //-----------------------------------------------------------------------
    Object[][] data_query() {
        return new Object[][] {
                {TEST_07_15, TemporalQueries.chronology(), IsoChronology.INSTANCE},
                {TEST_07_15, TemporalQueries.zoneId(), null},
                {TEST_07_15, TemporalQueries.precision(), null},
                {TEST_07_15, TemporalQueries.zone(), null},
                {TEST_07_15, TemporalQueries.offset(), null},
                {TEST_07_15, TemporalQueries.localDate(), null},
                {TEST_07_15, TemporalQueries.localTime(), null},
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
        Assertions.assertThrows(NullPointerException.class, () -> TEST_07_15.query(null));
    }

    //-----------------------------------------------------------------------
    // get*()
    //-----------------------------------------------------------------------
    Object[][] provider_sampleDates() {
        return new Object[][] {
            {1, 1},
            {1, 31},
            {2, 1},
            {2, 28},
            {2, 29},
            {7, 4},
            {7, 5},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sampleDates")
    public void test_get(int m, int d) {
        MonthDay a = MonthDay.of(m, d);
        assertEquals(Month.of(m), a.getMonth());
        assertEquals(m, a.getMonthValue());
        assertEquals(d, a.getDayOfMonth());
    }

    //-----------------------------------------------------------------------
    // with(Month)
    //-----------------------------------------------------------------------
    @Test
    public void test_with_Month() {
        assertEquals(MonthDay.of(1, 30), MonthDay.of(6, 30).with(Month.JANUARY));
    }

    @Test
    public void test_with_Month_adjustToValid() {
        assertEquals(MonthDay.of(6, 30), MonthDay.of(7, 31).with(Month.JUNE));
    }

    @Test
    public void test_with_Month_adjustToValidFeb() {
        assertEquals(MonthDay.of(2, 29), MonthDay.of(7, 31).with(Month.FEBRUARY));
    }

    @Test
    public void test_with_Month_noChangeEqual() {
        MonthDay test = MonthDay.of(6, 30);
        assertEquals(test, test.with(Month.JUNE));
    }

    @Test
    public void test_with_Month_null() {
        Assertions.assertThrows(NullPointerException.class, () -> MonthDay.of(6, 30).with((Month) null));
    }

    //-----------------------------------------------------------------------
    // withMonth()
    //-----------------------------------------------------------------------
    @Test
    public void test_withMonth() {
        assertEquals(MonthDay.of(1, 30), MonthDay.of(6, 30).withMonth(1));
    }

    @Test
    public void test_withMonth_adjustToValid() {
        assertEquals(MonthDay.of(6, 30), MonthDay.of(7, 31).withMonth(6));
    }

    @Test
    public void test_withMonth_adjustToValidFeb() {
        assertEquals(MonthDay.of(2, 29), MonthDay.of(7, 31).withMonth(2));
    }

    @Test
    public void test_withMonth_int_noChangeEqual() {
        MonthDay test = MonthDay.of(6, 30);
        assertEquals(test, test.withMonth(6));
    }

    @Test
    public void test_withMonth_tooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> MonthDay.of(6, 30).withMonth(0));
    }

    @Test
    public void test_withMonth_tooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> MonthDay.of(6, 30).withMonth(13));
    }

    //-----------------------------------------------------------------------
    // withDayOfMonth()
    //-----------------------------------------------------------------------
    @Test
    public void test_withDayOfMonth() {
        assertEquals(MonthDay.of(6, 1), MonthDay.of(6, 30).withDayOfMonth(1));
    }

    @Test
    public void test_withDayOfMonth_invalid() {
        Assertions.assertThrows(DateTimeException.class, () -> MonthDay.of(6, 30).withDayOfMonth(31));
    }

    @Test
    public void test_withDayOfMonth_adjustToValidFeb() {
        assertEquals(MonthDay.of(2, 29), MonthDay.of(2, 1).withDayOfMonth(29));
    }

    @Test
    public void test_withDayOfMonth_noChangeEqual() {
        MonthDay test = MonthDay.of(6, 30);
        assertEquals(test, test.withDayOfMonth(30));
    }

    @Test
    public void test_withDayOfMonth_tooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> MonthDay.of(6, 30).withDayOfMonth(0));
    }

    @Test
    public void test_withDayOfMonth_tooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> MonthDay.of(6, 30).withDayOfMonth(32));
    }

    //-----------------------------------------------------------------------
    // adjustInto()
    //-----------------------------------------------------------------------
    @Test
    public void test_adjustDate() {
        MonthDay test = MonthDay.of(6, 30);
        LocalDate date = LocalDate.of(2007, 1, 1);
        assertEquals(LocalDate.of(2007, 6, 30), test.adjustInto(date));
    }

    @Test
    public void test_adjustDate_resolve() {
        MonthDay test = MonthDay.of(2, 29);
        LocalDate date = LocalDate.of(2007, 6, 30);
        assertEquals(LocalDate.of(2007, 2, 28), test.adjustInto(date));
    }

    @Test
    public void test_adjustDate_equal() {
        MonthDay test = MonthDay.of(6, 30);
        LocalDate date = LocalDate.of(2007, 6, 30);
        assertEquals(date, test.adjustInto(date));
    }

    @Test
    public void test_adjustDate_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_07_15.adjustInto((LocalDate) null));
    }

    //-----------------------------------------------------------------------
    // isValidYear(int)
    //-----------------------------------------------------------------------
    @Test
    public void test_isValidYear_june() {
        MonthDay test = MonthDay.of(6, 30);
        assertEquals(true, test.isValidYear(2007));
    }

    @Test
    public void test_isValidYear_febNonLeap() {
        MonthDay test = MonthDay.of(2, 29);
        assertEquals(false, test.isValidYear(2007));
    }

    @Test
    public void test_isValidYear_febLeap() {
        MonthDay test = MonthDay.of(2, 29);
        assertEquals(true, test.isValidYear(2008));
    }

    //-----------------------------------------------------------------------
    // format(DateTimeFormatter)
    //-----------------------------------------------------------------------
    @Test
    public void test_format_formatter() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("M d");
        String t = MonthDay.of(12, 3).format(f);
        assertEquals("12 3", t);
    }

    @Test
    public void test_format_formatter_null() {
        Assertions.assertThrows(NullPointerException.class, () -> MonthDay.of(12, 3).format(null));
    }

    //-----------------------------------------------------------------------
    // atYear(int)
    //-----------------------------------------------------------------------
    @Test
    public void test_atYear_int() {
        MonthDay test = MonthDay.of(6, 30);
        assertEquals(LocalDate.of(2008, 6, 30), test.atYear(2008));
    }

    @Test
    public void test_atYear_int_leapYearAdjust() {
        MonthDay test = MonthDay.of(2, 29);
        assertEquals(LocalDate.of(2005, 2, 28), test.atYear(2005));
    }

    @Test
    public void test_atYear_int_invalidYear() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            MonthDay test = MonthDay.of(6, 30);
            test.atYear(Integer.MIN_VALUE);
        });
    }

    //-----------------------------------------------------------------------
    // compareTo()
    //-----------------------------------------------------------------------
    @Test
    public void test_comparisons() {
        doTest_comparisons_MonthDay(
            MonthDay.of(1, 1),
            MonthDay.of(1, 31),
            MonthDay.of(2, 1),
            MonthDay.of(2, 29),
            MonthDay.of(3, 1),
            MonthDay.of(12, 31)
        );
    }

    void doTest_comparisons_MonthDay(MonthDay... localDates) {
        for (int i = 0; i < localDates.length; i++) {
            MonthDay a = localDates[i];
            for (int j = 0; j < localDates.length; j++) {
                MonthDay b = localDates[j];
                if (i < j) {
                    assertTrue(a.compareTo(b) < 0, a + " <=> " + b);
                    assertEquals(true, a.isBefore(b), a + " <=> " + b);
                    assertEquals(false, a.isAfter(b), a + " <=> " + b);
                    assertEquals(false, a.equals(b), a + " <=> " + b);
                } else if (i > j) {
                    assertTrue(a.compareTo(b) > 0, a + " <=> " + b);
                    assertEquals(false, a.isBefore(b), a + " <=> " + b);
                    assertEquals(true, a.isAfter(b), a + " <=> " + b);
                    assertEquals(false, a.equals(b), a + " <=> " + b);
                } else {
                    assertEquals(0, a.compareTo(b), a + " <=> " + b);
                    assertEquals(false, a.isBefore(b), a + " <=> " + b);
                    assertEquals(false, a.isAfter(b), a + " <=> " + b);
                    assertEquals(true, a.equals(b), a + " <=> " + b);
                }
            }
        }
    }

    @Test
    public void test_compareTo_ObjectNull() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_07_15.compareTo(null));
    }

    @Test
    public void test_isBefore_ObjectNull() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_07_15.isBefore(null));
    }

    @Test
    public void test_isAfter_ObjectNull() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_07_15.isAfter(null));
    }

    //-----------------------------------------------------------------------
    // equals()
    //-----------------------------------------------------------------------
    @Test
    public void test_equals() {
        MonthDay a = MonthDay.of(1, 1);
        MonthDay b = MonthDay.of(1, 1);
        MonthDay c = MonthDay.of(2, 1);
        MonthDay d = MonthDay.of(1, 2);

        assertEquals(true, a.equals(a));
        assertEquals(true, a.equals(b));
        assertEquals(false, a.equals(c));
        assertEquals(false, a.equals(d));

        assertEquals(true, b.equals(a));
        assertEquals(true, b.equals(b));
        assertEquals(false, b.equals(c));
        assertEquals(false, b.equals(d));

        assertEquals(false, c.equals(a));
        assertEquals(false, c.equals(b));
        assertEquals(true, c.equals(c));
        assertEquals(false, c.equals(d));

        assertEquals(false, d.equals(a));
        assertEquals(false, d.equals(b));
        assertEquals(false, d.equals(c));
        assertEquals(true, d.equals(d));
    }

    @Test
    public void test_equals_itself_true() {
        assertEquals(true, TEST_07_15.equals(TEST_07_15));
    }

    @Test
    public void test_equals_string_false() {
        assertEquals(false, TEST_07_15.equals("2007-07-15"));
    }

    @Test
    public void test_equals_null_false() {
        assertEquals(false, TEST_07_15.equals(null));
    }

    //-----------------------------------------------------------------------
    // hashCode()
    //-----------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("provider_sampleDates")
    public void test_hashCode(int m, int d) {
        MonthDay a = MonthDay.of(m, d);
        assertEquals(a.hashCode(), a.hashCode());
        MonthDay b = MonthDay.of(m, d);
        assertEquals(b.hashCode(), a.hashCode());
    }

    @Test
    public void test_hashCode_unique() {
        int leapYear = 2008;
        Set<Integer> uniques = new HashSet<Integer>(366);
        for (int i = 1; i <= 12; i++) {
            for (int j = 1; j <= 31; j++) {
                if (YearMonth.of(leapYear, i).isValidDay(j)) {
                    assertTrue(uniques.add(MonthDay.of(i, j).hashCode()));
                }
            }
        }
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    Object[][] provider_sampleToString() {
        return new Object[][] {
            {7, 5, "--07-05"},
            {12, 31, "--12-31"},
            {1, 2, "--01-02"},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sampleToString")
    public void test_toString(int m, int d, String expected) {
        MonthDay test = MonthDay.of(m, d);
        String str = test.toString();
        assertEquals(expected, str);
    }

}
