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

import static java.time.temporal.ChronoField.ERA;
import static java.time.temporal.ChronoField.YEAR;
import static java.time.temporal.ChronoField.YEAR_OF_ERA;
import static java.time.temporal.ChronoUnit.CENTURIES;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.DECADES;
import static java.time.temporal.ChronoUnit.MILLENNIA;
import static java.time.temporal.ChronoUnit.MONTHS;
import static java.time.temporal.ChronoUnit.YEARS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.chrono.IsoChronology;
import java.time.chrono.IsoEra;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.JulianFields;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test Year.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TCKYear extends AbstractDateTimeTest {

    private static final Year TEST_2008 = Year.of(2008);

    @BeforeEach
    public void setUp() {
    }

    //-----------------------------------------------------------------------
    @Override
    protected List<TemporalAccessor> samples() {
        TemporalAccessor[] array = {TEST_2008, };
        return Arrays.asList(array);
    }

    @Override
    protected List<TemporalField> validFields() {
        TemporalField[] array = {
            YEAR_OF_ERA,
            YEAR,
            ERA,
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
    // now()
    //-----------------------------------------------------------------------
    @Test
    public void now() {
        Year expected = Year.now(Clock.systemDefaultZone());
        Year test = Year.now();
        for (int i = 0; i < 100; i++) {
            if (expected.equals(test)) {
                return;
            }
            expected = Year.now(Clock.systemDefaultZone());
            test = Year.now();
        }
        assertEquals(expected, test);
    }

    //-----------------------------------------------------------------------
    // now(ZoneId)
    //-----------------------------------------------------------------------
    @Test
    public void now_ZoneId_nullZoneId() {
        Assertions.assertThrows(NullPointerException.class, () -> Year.now((ZoneId) null));
    }

    @Test
    public void now_ZoneId() {
        ZoneId zone = ZoneId.of("UTC+01:02:03");
        Year expected = Year.now(Clock.system(zone));
        Year test = Year.now(zone);
        for (int i = 0; i < 100; i++) {
            if (expected.equals(test)) {
                return;
            }
            expected = Year.now(Clock.system(zone));
            test = Year.now(zone);
        }
        assertEquals(expected, test);
    }

    //-----------------------------------------------------------------------
    // now(Clock)
    //-----------------------------------------------------------------------
    @Test
    public void now_Clock() {
        Instant instant = OffsetDateTime.of(LocalDate.of(2010, 12, 31), LocalTime.of(0, 0), ZoneOffset.UTC).toInstant();
        Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
        Year test = Year.now(clock);
        assertEquals(2010, test.getValue());
    }

    @Test
    public void now_Clock_nullClock() {
        Assertions.assertThrows(NullPointerException.class, () -> Year.now((Clock) null));
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_factory_int_singleton() {
        for (int i = -4; i <= 2104; i++) {
            Year test = Year.of(i);
            assertEquals(i, test.getValue());
            assertEquals(test, Year.of(i));
        }
    }

    @Test
    public void test_factory_int_tooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> Year.of(Year.MIN_VALUE - 1));
    }

    @Test
    public void test_factory_int_tooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> Year.of(Year.MAX_VALUE + 1));
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_from_TemporalAccessor() {
        assertEquals(Year.of(2007), Year.from(LocalDate.of(2007, 7, 15)));
    }

    @Test
    public void test_from_TemporalAccessor_invalid_noDerive() {
        Assertions.assertThrows(DateTimeException.class, () -> Year.from(LocalTime.of(12, 30)));
    }

    @Test
    public void test_from_TemporalAccessor_null() {
        Assertions.assertThrows(NullPointerException.class, () -> Year.from((TemporalAccessor) null));
    }

    //-----------------------------------------------------------------------
    // parse()
    //-----------------------------------------------------------------------
    Object[][] provider_goodParseData() {
        return new Object[][] {
                {"9999", Year.of(9999)},
                {"2000", Year.of(2000)},

                {"0", Year.of(0)},
                {"00", Year.of(0)},
                {"000", Year.of(0)},
                {"0000", Year.of(0)},
                {"00000", Year.of(0)},
                {"+00000", Year.of(0)},
                {"-0", Year.of(0)},
                {"-00", Year.of(0)},
                {"-000", Year.of(0)},
                {"-0000", Year.of(0)},
                {"-00000", Year.of(0)},
                {"1", Year.of(1)},
                {"01", Year.of(1)},
                {"001", Year.of(1)},
                {"0001", Year.of(1)},
                {"00001", Year.of(1)},
                {"+00001", Year.of(1)},
                {"-1", Year.of(-1)},
                {"-01", Year.of(-1)},
                {"-001", Year.of(-1)},
                {"-0001", Year.of(-1)},
                {"-00001", Year.of(-1)},

                {"+12345678", Year.of(12345678)},
                {"+123456", Year.of(123456)},
                {"-1234", Year.of(-1234)},
                {"-12345678", Year.of(-12345678)},

                {"+" + Year.MAX_VALUE, Year.of(Year.MAX_VALUE)},
                {"" + Year.MIN_VALUE, Year.of(Year.MIN_VALUE)},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_goodParseData")
    public void factory_parse_success(String text, Year expected) {
        Year year = Year.parse(text);
        assertEquals(expected, year);
    }

    Object[][] provider_badParseData() {
        return new Object[][] {
                {"", 0},
                {"--01-0", 1},
                {"A01", 0},
                {"2009/12", 4},

                {"-0000-10", 5},
                {"-12345678901-10", 10},
                {"+1-10", 2},
                {"+12-10", 3},
                {"+123-10", 4},
                {"+1234-10", 5},
                {"12345-10", 5},
                {"+12345678901-10", 10},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_badParseData")
    public void factory_parse_fail(String text, int pos) {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            try {
                Year.parse(text);
                fail(String.format("Parse should have failed for %s at position %d", text, pos));
            } catch (DateTimeParseException ex) {
                assertEquals(text, ex.getParsedString());
                assertEquals(pos, ex.getErrorIndex());
                throw ex;
            }
        });
    }

    @Test
    public void factory_parse_nullText() {
        Assertions.assertThrows(NullPointerException.class, () -> Year.parse(null));
    }

    //-----------------------------------------------------------------------
    // parse(DateTimeFormatter)
    //-----------------------------------------------------------------------
    @Test
    public void factory_parse_formatter() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("y");
        Year test = Year.parse("2010", f);
        assertEquals(Year.of(2010), test);
    }

    @Test
    public void factory_parse_formatter_nullText() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("y");
            Year.parse((String) null, f);
        });
    }

    @Test
    public void factory_parse_formatter_nullFormatter() {
        Assertions.assertThrows(NullPointerException.class, () -> Year.parse("ANY", null));
    }

    //-----------------------------------------------------------------------
    // isSupported(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_isSupported_TemporalField() {
        assertEquals(false, TEST_2008.isSupported((TemporalField) null));
        assertEquals(false, TEST_2008.isSupported(ChronoField.NANO_OF_SECOND));
        assertEquals(false, TEST_2008.isSupported(ChronoField.NANO_OF_DAY));
        assertEquals(false, TEST_2008.isSupported(ChronoField.MICRO_OF_SECOND));
        assertEquals(false, TEST_2008.isSupported(ChronoField.MICRO_OF_DAY));
        assertEquals(false, TEST_2008.isSupported(ChronoField.MILLI_OF_SECOND));
        assertEquals(false, TEST_2008.isSupported(ChronoField.MILLI_OF_DAY));
        assertEquals(false, TEST_2008.isSupported(ChronoField.SECOND_OF_MINUTE));
        assertEquals(false, TEST_2008.isSupported(ChronoField.SECOND_OF_DAY));
        assertEquals(false, TEST_2008.isSupported(ChronoField.MINUTE_OF_HOUR));
        assertEquals(false, TEST_2008.isSupported(ChronoField.MINUTE_OF_DAY));
        assertEquals(false, TEST_2008.isSupported(ChronoField.HOUR_OF_AMPM));
        assertEquals(false, TEST_2008.isSupported(ChronoField.CLOCK_HOUR_OF_AMPM));
        assertEquals(false, TEST_2008.isSupported(ChronoField.HOUR_OF_DAY));
        assertEquals(false, TEST_2008.isSupported(ChronoField.CLOCK_HOUR_OF_DAY));
        assertEquals(false, TEST_2008.isSupported(ChronoField.AMPM_OF_DAY));
        assertEquals(false, TEST_2008.isSupported(ChronoField.DAY_OF_WEEK));
        assertEquals(false, TEST_2008.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH));
        assertEquals(false, TEST_2008.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR));
        assertEquals(false, TEST_2008.isSupported(ChronoField.DAY_OF_MONTH));
        assertEquals(false, TEST_2008.isSupported(ChronoField.DAY_OF_YEAR));
        assertEquals(false, TEST_2008.isSupported(ChronoField.EPOCH_DAY));
        assertEquals(false, TEST_2008.isSupported(ChronoField.ALIGNED_WEEK_OF_MONTH));
        assertEquals(false, TEST_2008.isSupported(ChronoField.ALIGNED_WEEK_OF_YEAR));
        assertEquals(false, TEST_2008.isSupported(ChronoField.MONTH_OF_YEAR));
        assertEquals(false, TEST_2008.isSupported(ChronoField.PROLEPTIC_MONTH));
        assertEquals(true, TEST_2008.isSupported(ChronoField.YEAR));
        assertEquals(true, TEST_2008.isSupported(ChronoField.YEAR_OF_ERA));
        assertEquals(true, TEST_2008.isSupported(ChronoField.ERA));
        assertEquals(false, TEST_2008.isSupported(ChronoField.INSTANT_SECONDS));
        assertEquals(false, TEST_2008.isSupported(ChronoField.OFFSET_SECONDS));
    }

    //-----------------------------------------------------------------------
    // isSupported(TemporalUnit)
    //-----------------------------------------------------------------------
    @Test
    public void test_isSupported_TemporalUnit() {
        assertEquals(false, TEST_2008.isSupported((TemporalUnit) null));
        assertEquals(false, TEST_2008.isSupported(ChronoUnit.NANOS));
        assertEquals(false, TEST_2008.isSupported(ChronoUnit.MICROS));
        assertEquals(false, TEST_2008.isSupported(ChronoUnit.MILLIS));
        assertEquals(false, TEST_2008.isSupported(ChronoUnit.SECONDS));
        assertEquals(false, TEST_2008.isSupported(ChronoUnit.MINUTES));
        assertEquals(false, TEST_2008.isSupported(ChronoUnit.HOURS));
        assertEquals(false, TEST_2008.isSupported(ChronoUnit.HALF_DAYS));
        assertEquals(false, TEST_2008.isSupported(ChronoUnit.DAYS));
        assertEquals(false, TEST_2008.isSupported(ChronoUnit.WEEKS));
        assertEquals(false, TEST_2008.isSupported(ChronoUnit.MONTHS));
        assertEquals(true, TEST_2008.isSupported(ChronoUnit.YEARS));
        assertEquals(true, TEST_2008.isSupported(ChronoUnit.DECADES));
        assertEquals(true, TEST_2008.isSupported(ChronoUnit.CENTURIES));
        assertEquals(true, TEST_2008.isSupported(ChronoUnit.MILLENNIA));
        assertEquals(true, TEST_2008.isSupported(ChronoUnit.ERAS));
        assertEquals(false, TEST_2008.isSupported(ChronoUnit.FOREVER));
    }

    //-----------------------------------------------------------------------
    // get(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_get_TemporalField() {
        assertEquals(2008, TEST_2008.get(ChronoField.YEAR));
        assertEquals(2008, TEST_2008.get(ChronoField.YEAR_OF_ERA));
        assertEquals(1, TEST_2008.get(ChronoField.ERA));
    }

    @Test
    public void test_getLong_TemporalField() {
        assertEquals(2008, TEST_2008.getLong(ChronoField.YEAR));
        assertEquals(2008, TEST_2008.getLong(ChronoField.YEAR_OF_ERA));
        assertEquals(1, TEST_2008.getLong(ChronoField.ERA));
    }

    //-----------------------------------------------------------------------
    // query(TemporalQuery)
    //-----------------------------------------------------------------------
    Object[][] data_query() {
        return new Object[][] {
                {TEST_2008, TemporalQueries.chronology(), IsoChronology.INSTANCE},
                {TEST_2008, TemporalQueries.zoneId(), null},
                {TEST_2008, TemporalQueries.precision(), ChronoUnit.YEARS},
                {TEST_2008, TemporalQueries.zone(), null},
                {TEST_2008, TemporalQueries.offset(), null},
                {TEST_2008, TemporalQueries.localDate(), null},
                {TEST_2008, TemporalQueries.localTime(), null},
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
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2008.query(null));
    }

    //-----------------------------------------------------------------------
    // isLeap()
    //-----------------------------------------------------------------------
    @Test
    public void test_isLeap() {
        assertEquals(false, Year.of(1999).isLeap());
        assertEquals(true, Year.of(2000).isLeap());
        assertEquals(false, Year.of(2001).isLeap());

        assertEquals(false, Year.of(2007).isLeap());
        assertEquals(true, Year.of(2008).isLeap());
        assertEquals(false, Year.of(2009).isLeap());
        assertEquals(false, Year.of(2010).isLeap());
        assertEquals(false, Year.of(2011).isLeap());
        assertEquals(true, Year.of(2012).isLeap());

        assertEquals(false, Year.of(2095).isLeap());
        assertEquals(true, Year.of(2096).isLeap());
        assertEquals(false, Year.of(2097).isLeap());
        assertEquals(false, Year.of(2098).isLeap());
        assertEquals(false, Year.of(2099).isLeap());
        assertEquals(false, Year.of(2100).isLeap());
        assertEquals(false, Year.of(2101).isLeap());
        assertEquals(false, Year.of(2102).isLeap());
        assertEquals(false, Year.of(2103).isLeap());
        assertEquals(true, Year.of(2104).isLeap());
        assertEquals(false, Year.of(2105).isLeap());

        assertEquals(false, Year.of(-500).isLeap());
        assertEquals(true, Year.of(-400).isLeap());
        assertEquals(false, Year.of(-300).isLeap());
        assertEquals(false, Year.of(-200).isLeap());
        assertEquals(false, Year.of(-100).isLeap());
        assertEquals(true, Year.of(0).isLeap());
        assertEquals(false, Year.of(100).isLeap());
        assertEquals(false, Year.of(200).isLeap());
        assertEquals(false, Year.of(300).isLeap());
        assertEquals(true, Year.of(400).isLeap());
        assertEquals(false, Year.of(500).isLeap());
    }

    //-----------------------------------------------------------------------
    // plus(Period)
    //-----------------------------------------------------------------------
    Object[][] data_plusValid() {
        return new Object[][] {
                {2012, Period.ofYears(0), 2012},
                {2012, Period.ofYears(1), 2013},
                {2012, Period.ofYears(2), 2014},
                {2012, Period.ofYears(-2), 2010},
        };
    }

    @ParameterizedTest
    @MethodSource("data_plusValid")
    public void test_plusValid(int year, TemporalAmount amount, int expected) {
        assertEquals(Year.of(expected), Year.of(year).plus(amount));
    }

    Object[][] data_plusInvalidUnit() {
        return new Object[][] {
                {Period.of(0, 1, 0)},
                {Period.of(0, 0, 1)},
                {Period.of(0, 1, 1)},
                {Period.of(1, 1, 1)},
                {Duration.ofDays(1)},
                {Duration.ofHours(1)},
                {Duration.ofMinutes(1)},
                {Duration.ofSeconds(1)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_plusInvalidUnit")
    public void test_plusInvalidUnit(TemporalAmount amount) {
        Assertions.assertThrows(UnsupportedTemporalTypeException.class, () -> TEST_2008.plus(amount));
    }

    @Test
    public void test_plus_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2008.plus(null));
    }

    //-----------------------------------------------------------------------
    // plusYears()
    //-----------------------------------------------------------------------
    @Test
    public void test_plusYears() {
        assertEquals(Year.of(2006), Year.of(2007).plusYears(-1));
        assertEquals(Year.of(2007), Year.of(2007).plusYears(0));
        assertEquals(Year.of(2008), Year.of(2007).plusYears(1));
        assertEquals(Year.of(2009), Year.of(2007).plusYears(2));

        assertEquals(Year.of(Year.MAX_VALUE), Year.of(Year.MAX_VALUE - 1).plusYears(1));
        assertEquals(Year.of(Year.MAX_VALUE), Year.of(Year.MAX_VALUE).plusYears(0));

        assertEquals(Year.of(Year.MIN_VALUE), Year.of(Year.MIN_VALUE + 1).plusYears(-1));
        assertEquals(Year.of(Year.MIN_VALUE), Year.of(Year.MIN_VALUE).plusYears(0));
    }

    @Test
    public void test_plusYear_zero_equals() {
        Year base = Year.of(2007);
        assertEquals(base, base.plusYears(0));
    }

    @Test
    public void test_plusYears_big() {
        long years = 20L + Year.MAX_VALUE;
        assertEquals(Year.of((int) (-40L + years)), Year.of(-40).plusYears(years));
    }

    @Test
    public void test_plusYears_max() {
        Assertions.assertThrows(DateTimeException.class, () -> Year.of(Year.MAX_VALUE).plusYears(1));
    }

    @Test
    public void test_plusYears_maxLots() {
        Assertions.assertThrows(DateTimeException.class, () -> Year.of(Year.MAX_VALUE).plusYears(1000));
    }

    @Test
    public void test_plusYears_min() {
        Assertions.assertThrows(DateTimeException.class, () -> Year.of(Year.MIN_VALUE).plusYears(-1));
    }

    @Test
    public void test_plusYears_minLots() {
        Assertions.assertThrows(DateTimeException.class, () -> Year.of(Year.MIN_VALUE).plusYears(-1000));
    }

    //-----------------------------------------------------------------------
    // plus(long, TemporalUnit)
    //-----------------------------------------------------------------------
    Object[][] data_plus_long_TemporalUnit() {
        return new Object[][] {
            {Year.of(1), 1, ChronoUnit.YEARS, Year.of(2), null},
            {Year.of(1), -12, ChronoUnit.YEARS, Year.of(-11), null},
            {Year.of(1), 0, ChronoUnit.YEARS, Year.of(1), null},
            {Year.of(999999999), 0, ChronoUnit.YEARS, Year.of(999999999), null},
            {Year.of(-999999999), 0, ChronoUnit.YEARS, Year.of(-999999999), null},
            {Year.of(0), -999999999, ChronoUnit.YEARS, Year.of(-999999999), null},
            {Year.of(0), 999999999, ChronoUnit.YEARS, Year.of(999999999), null},

            {Year.of(-1), 1, ChronoUnit.ERAS, Year.of(2), null},
            {Year.of(5), 1, ChronoUnit.CENTURIES, Year.of(105), null},
            {Year.of(5), 1, ChronoUnit.DECADES, Year.of(15), null},

            {Year.of(999999999), 1, ChronoUnit.YEARS, null, DateTimeException.class},
            {Year.of(-999999999), -1, ChronoUnit.YEARS, null, DateTimeException.class},

            {Year.of(1), 0, ChronoUnit.DAYS, null, DateTimeException.class},
            {Year.of(1), 0, ChronoUnit.WEEKS, null, DateTimeException.class},
            {Year.of(1), 0, ChronoUnit.MONTHS, null, DateTimeException.class},
        };
    }

    @ParameterizedTest
    @MethodSource("data_plus_long_TemporalUnit")
    public void test_plus_long_TemporalUnit(Year base, long amount, TemporalUnit unit, Year expectedYear, Class<?> expectedEx) {
        if (expectedEx == null) {
            assertEquals(expectedYear, base.plus(amount, unit));
        } else {
            try {
                base.plus(amount, unit);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    //-----------------------------------------------------------------------
    // minus(Period)
    //-----------------------------------------------------------------------
    Object[][] data_minusValid() {
        return new Object[][] {
                {2012, Period.ofYears(0), 2012},
                {2012, Period.ofYears(1), 2011},
                {2012, Period.ofYears(2), 2010},
                {2012, Period.ofYears(-2), 2014},
        };
    }

    @ParameterizedTest
    @MethodSource("data_minusValid")
    public void test_minusValid(int year, TemporalAmount amount, int expected) {
        assertEquals(Year.of(expected), Year.of(year).minus(amount));
    }

    Object[][] data_minusInvalidUnit() {
        return new Object[][] {
                {Period.of(0, 1, 0)},
                {Period.of(0, 0, 1)},
                {Period.of(0, 1, 1)},
                {Period.of(1, 1, 1)},
                {Duration.ofDays(1)},
                {Duration.ofHours(1)},
                {Duration.ofMinutes(1)},
                {Duration.ofSeconds(1)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_minusInvalidUnit")
    public void test_minusInvalidUnit(TemporalAmount amount) {
        Assertions.assertThrows(UnsupportedTemporalTypeException.class, () -> TEST_2008.minus(amount));
    }

    @Test
    public void test_minus_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2008.minus(null));
    }

    //-----------------------------------------------------------------------
    // minusYears()
    //-----------------------------------------------------------------------
    @Test
    public void test_minusYears() {
        assertEquals(Year.of(2008), Year.of(2007).minusYears(-1));
        assertEquals(Year.of(2007), Year.of(2007).minusYears(0));
        assertEquals(Year.of(2006), Year.of(2007).minusYears(1));
        assertEquals(Year.of(2005), Year.of(2007).minusYears(2));

        assertEquals(Year.of(Year.MAX_VALUE), Year.of(Year.MAX_VALUE - 1).minusYears(-1));
        assertEquals(Year.of(Year.MAX_VALUE), Year.of(Year.MAX_VALUE).minusYears(0));

        assertEquals(Year.of(Year.MIN_VALUE), Year.of(Year.MIN_VALUE + 1).minusYears(1));
        assertEquals(Year.of(Year.MIN_VALUE), Year.of(Year.MIN_VALUE).minusYears(0));
    }

    @Test
    public void test_minusYear_zero_equals() {
        Year base = Year.of(2007);
        assertEquals(base, base.minusYears(0));
    }

    @Test
    public void test_minusYears_big() {
        long years = 20L + Year.MAX_VALUE;
        assertEquals(Year.of((int) (40L - years)), Year.of(40).minusYears(years));
    }

    @Test
    public void test_minusYears_max() {
        Assertions.assertThrows(DateTimeException.class, () -> Year.of(Year.MAX_VALUE).minusYears(-1));
    }

    @Test
    public void test_minusYears_maxLots() {
        Assertions.assertThrows(DateTimeException.class, () -> Year.of(Year.MAX_VALUE).minusYears(-1000));
    }

    @Test
    public void test_minusYears_min() {
        Assertions.assertThrows(DateTimeException.class, () -> Year.of(Year.MIN_VALUE).minusYears(1));
    }

    @Test
    public void test_minusYears_minLots() {
        Assertions.assertThrows(DateTimeException.class, () -> Year.of(Year.MIN_VALUE).minusYears(1000));
    }

    //-----------------------------------------------------------------------
    // minus(long, TemporalUnit)
    //-----------------------------------------------------------------------
    Object[][] data_minus_long_TemporalUnit() {
        return new Object[][] {
            {Year.of(1), 1, ChronoUnit.YEARS, Year.of(0), null},
            {Year.of(1), -12, ChronoUnit.YEARS, Year.of(13), null},
            {Year.of(1), 0, ChronoUnit.YEARS, Year.of(1), null},
            {Year.of(999999999), 0, ChronoUnit.YEARS, Year.of(999999999), null},
            {Year.of(-999999999), 0, ChronoUnit.YEARS, Year.of(-999999999), null},
            {Year.of(0), -999999999, ChronoUnit.YEARS, Year.of(999999999), null},
            {Year.of(0), 999999999, ChronoUnit.YEARS, Year.of(-999999999), null},

            {Year.of(999999999), 1, ChronoUnit.ERAS, Year.of(-999999999 + 1), null},
            {Year.of(105), 1, ChronoUnit.CENTURIES, Year.of(5), null},
            {Year.of(15), 1, ChronoUnit.DECADES, Year.of(5), null},

            {Year.of(-999999999), 1, ChronoUnit.YEARS, null, DateTimeException.class},
            {Year.of(1), -999999999, ChronoUnit.YEARS, null, DateTimeException.class},

            {Year.of(1), 0, ChronoUnit.DAYS, null, DateTimeException.class},
            {Year.of(1), 0, ChronoUnit.WEEKS, null, DateTimeException.class},
            {Year.of(1), 0, ChronoUnit.MONTHS, null, DateTimeException.class},
        };
    }

    @ParameterizedTest
    @MethodSource("data_minus_long_TemporalUnit")
    public void test_minus_long_TemporalUnit(Year base, long amount, TemporalUnit unit, Year expectedYear, Class<?> expectedEx) {
        if (expectedEx == null) {
            assertEquals(expectedYear, base.minus(amount, unit));
        } else {
            try {
                Year result = base.minus(amount, unit);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    //-----------------------------------------------------------------------
    // adjustInto()
    //-----------------------------------------------------------------------
    @Test
    public void test_adjustDate() {
        LocalDate base = LocalDate.of(2007, 2, 12);
        for (int i = -4; i <= 2104; i++) {
            Temporal result = Year.of(i).adjustInto(base);
            assertEquals(LocalDate.of(i, 2, 12), result);
        }
    }

    @Test
    public void test_adjustDate_resolve() {
        Year test = Year.of(2011);
        assertEquals(LocalDate.of(2011, 2, 28), test.adjustInto(LocalDate.of(2012, 2, 29)));
    }

    @Test
    public void test_adjustDate_nullLocalDate() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Year test = Year.of(1);
            test.adjustInto((LocalDate) null);
        });
    }

    //-----------------------------------------------------------------------
    // with(TemporalAdjuster)
    //-----------------------------------------------------------------------
    @Test
    public void test_with_TemporalAdjuster() {
        Year base = Year.of(-10);
        for (int i = -4; i <= 2104; i++) {
            Temporal result = base.with(Year.of(i));
            assertEquals(Year.of(i), result);
        }
    }

    @Test
    public void test_with_BadTemporalAdjuster() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Year test = Year.of(1);
            test.with(LocalTime.of(18, 1, 2));
        });
    }

    //-----------------------------------------------------------------------
    // with(TemporalField, long)
    //-----------------------------------------------------------------------
    @Test
    public void test_with() {
        Year base = Year.of(5);
        Year result = base.with(ChronoField.ERA, 0);
        assertEquals(base.with(IsoEra.of(0)), result);

        int prolepticYear = IsoChronology.INSTANCE.prolepticYear(IsoEra.of(0), 5);
        assertEquals(0, result.get(ChronoField.ERA));
        assertEquals(prolepticYear, result.get(ChronoField.YEAR));
        assertEquals(5, result.get(ChronoField.YEAR_OF_ERA));

        result = base.with(ChronoField.YEAR, 10);
        assertEquals(base.get(ChronoField.ERA), result.get(ChronoField.ERA));
        assertEquals(10, result.get(ChronoField.YEAR));
        assertEquals(10, result.get(ChronoField.YEAR_OF_ERA));

        result = base.with(ChronoField.YEAR_OF_ERA, 20);
        assertEquals(base.get(ChronoField.ERA), result.get(ChronoField.ERA));
        assertEquals(20, result.get(ChronoField.YEAR));
        assertEquals(20, result.get(ChronoField.YEAR_OF_ERA));
    }

    //-----------------------------------------------------------------------
    // length()
    //-----------------------------------------------------------------------
    @Test
    public void test_length() {
        assertEquals(365, Year.of(1999).length());
        assertEquals(366, Year.of(2000).length());
        assertEquals(365, Year.of(2001).length());

        assertEquals(365, Year.of(2007).length());
        assertEquals(366, Year.of(2008).length());
        assertEquals(365, Year.of(2009).length());
        assertEquals(365, Year.of(2010).length());
        assertEquals(365, Year.of(2011).length());
        assertEquals(366, Year.of(2012).length());

        assertEquals(365, Year.of(2095).length());
        assertEquals(366, Year.of(2096).length());
        assertEquals(365, Year.of(2097).length());
        assertEquals(365, Year.of(2098).length());
        assertEquals(365, Year.of(2099).length());
        assertEquals(365, Year.of(2100).length());
        assertEquals(365, Year.of(2101).length());
        assertEquals(365, Year.of(2102).length());
        assertEquals(365, Year.of(2103).length());
        assertEquals(366, Year.of(2104).length());
        assertEquals(365, Year.of(2105).length());

        assertEquals(365, Year.of(-500).length());
        assertEquals(366, Year.of(-400).length());
        assertEquals(365, Year.of(-300).length());
        assertEquals(365, Year.of(-200).length());
        assertEquals(365, Year.of(-100).length());
        assertEquals(366, Year.of(0).length());
        assertEquals(365, Year.of(100).length());
        assertEquals(365, Year.of(200).length());
        assertEquals(365, Year.of(300).length());
        assertEquals(366, Year.of(400).length());
        assertEquals(365, Year.of(500).length());
    }

    //-----------------------------------------------------------------------
    // isValidMonthDay(MonthDay)
    //-----------------------------------------------------------------------
    Object[][] data_isValidMonthDay() {
        return new Object[][] {
                {Year.of(2007), MonthDay.of(6, 30), true},
                {Year.of(2008), MonthDay.of(2, 28), true},
                {Year.of(2008), MonthDay.of(2, 29), true},
                {Year.of(2009), MonthDay.of(2, 28), true},
                {Year.of(2009), MonthDay.of(2, 29), false},
                {Year.of(2009), null, false},
        };
    }

    @ParameterizedTest
    @MethodSource("data_isValidMonthDay")
    public void test_isValidMonthDay(Year year, MonthDay monthDay, boolean expected) {
        assertEquals(expected, year.isValidMonthDay(monthDay));
    }

    //-----------------------------------------------------------------------
    // until(Temporal, TemporalUnit)
    //-----------------------------------------------------------------------
    Object[][] data_periodUntilUnit() {
        return new Object[][] {
                {Year.of(2000), Year.of(-1), YEARS, -2001},
                {Year.of(2000), Year.of(0), YEARS, -2000},
                {Year.of(2000), Year.of(1), YEARS, -1999},
                {Year.of(2000), Year.of(1998), YEARS, -2},
                {Year.of(2000), Year.of(1999), YEARS, -1},
                {Year.of(2000), Year.of(2000), YEARS, 0},
                {Year.of(2000), Year.of(2001), YEARS, 1},
                {Year.of(2000), Year.of(2002), YEARS, 2},
                {Year.of(2000), Year.of(2246), YEARS, 246},

                {Year.of(2000), Year.of(-1), DECADES, -200},
                {Year.of(2000), Year.of(0), DECADES, -200},
                {Year.of(2000), Year.of(1), DECADES, -199},
                {Year.of(2000), Year.of(1989), DECADES, -1},
                {Year.of(2000), Year.of(1990), DECADES, -1},
                {Year.of(2000), Year.of(1991), DECADES, 0},
                {Year.of(2000), Year.of(2000), DECADES, 0},
                {Year.of(2000), Year.of(2009), DECADES, 0},
                {Year.of(2000), Year.of(2010), DECADES, 1},
                {Year.of(2000), Year.of(2011), DECADES, 1},

                {Year.of(2000), Year.of(-1), CENTURIES, -20},
                {Year.of(2000), Year.of(0), CENTURIES, -20},
                {Year.of(2000), Year.of(1), CENTURIES, -19},
                {Year.of(2000), Year.of(1899), CENTURIES, -1},
                {Year.of(2000), Year.of(1900), CENTURIES, -1},
                {Year.of(2000), Year.of(1901), CENTURIES, 0},
                {Year.of(2000), Year.of(2000), CENTURIES, 0},
                {Year.of(2000), Year.of(2099), CENTURIES, 0},
                {Year.of(2000), Year.of(2100), CENTURIES, 1},
                {Year.of(2000), Year.of(2101), CENTURIES, 1},

                {Year.of(2000), Year.of(-1), MILLENNIA, -2},
                {Year.of(2000), Year.of(0), MILLENNIA, -2},
                {Year.of(2000), Year.of(1), MILLENNIA, -1},
                {Year.of(2000), Year.of(999), MILLENNIA, -1},
                {Year.of(2000), Year.of(1000), MILLENNIA, -1},
                {Year.of(2000), Year.of(1001), MILLENNIA, 0},
                {Year.of(2000), Year.of(2000), MILLENNIA, 0},
                {Year.of(2000), Year.of(2999), MILLENNIA, 0},
                {Year.of(2000), Year.of(3000), MILLENNIA, 1},
                {Year.of(2000), Year.of(3001), MILLENNIA, 1},
        };
    }

    @ParameterizedTest
    @MethodSource("data_periodUntilUnit")
    public void test_until_TemporalUnit(Year year1, Year year2, TemporalUnit unit, long expected) {
        long amount = year1.until(year2, unit);
        assertEquals(expected, amount);
    }

    @ParameterizedTest
    @MethodSource("data_periodUntilUnit")
    public void test_until_TemporalUnit_negated(Year year1, Year year2, TemporalUnit unit, long expected) {
        long amount = year2.until(year1, unit);
        assertEquals(-expected, amount);
    }

    @ParameterizedTest
    @MethodSource("data_periodUntilUnit")
    public void test_until_TemporalUnit_between(Year year1, Year year2, TemporalUnit unit, long expected) {
        long amount = unit.between(year1, year2);
        assertEquals(expected, amount);
    }

    @Test
    public void test_until_convertedType() {
        Year start = Year.of(2010);
        YearMonth end = start.plusYears(2).atMonth(Month.APRIL);
        assertEquals(2, start.until(end, YEARS));
    }

    @Test
    public void test_until_invalidType() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Year start = Year.of(2010);
            start.until(LocalTime.of(11, 30), YEARS);
        });
    }

    @Test
    public void test_until_TemporalUnit_unsupportedUnit() {
        Assertions.assertThrows(UnsupportedTemporalTypeException.class, () -> TEST_2008.until(TEST_2008, MONTHS));
    }

    @Test
    public void test_until_TemporalUnit_nullEnd() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2008.until(null, DAYS));
    }

    @Test
    public void test_until_TemporalUnit_nullUnit() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2008.until(TEST_2008, null));
    }

    //-----------------------------------------------------------------------
    // format(DateTimeFormatter)
    //-----------------------------------------------------------------------
    @Test
    public void test_format_formatter() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("y");
        String t = Year.of(2010).format(f);
        assertEquals("2010", t);
    }

    @Test
    public void test_format_formatter_null() {
        Assertions.assertThrows(NullPointerException.class, () -> Year.of(2010).format(null));
    }

    //-----------------------------------------------------------------------
    // atMonth(Month)
    //-----------------------------------------------------------------------
    @Test
    public void test_atMonth() {
        Year test = Year.of(2008);
        assertEquals(YearMonth.of(2008, 6), test.atMonth(Month.JUNE));
    }

    @Test
    public void test_atMonth_nullMonth() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Year test = Year.of(2008);
            test.atMonth((Month) null);
        });
    }

    //-----------------------------------------------------------------------
    // atMonth(int)
    //-----------------------------------------------------------------------
    @Test
    public void test_atMonth_int() {
        Year test = Year.of(2008);
        assertEquals(YearMonth.of(2008, 6), test.atMonth(6));
    }

    @Test
    public void test_atMonth_int_invalidMonth() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Year test = Year.of(2008);
            test.atMonth(13);
        });
    }

    //-----------------------------------------------------------------------
    // atMonthDay(MonthDay)
    //-----------------------------------------------------------------------
    Object[][] data_atMonthDay() {
        return new Object[][] {
                {Year.of(2008), MonthDay.of(6, 30), LocalDate.of(2008, 6, 30)},
                {Year.of(2008), MonthDay.of(2, 29), LocalDate.of(2008, 2, 29)},
                {Year.of(2009), MonthDay.of(2, 29), LocalDate.of(2009, 2, 28)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_atMonthDay")
    public void test_atMonthDay(Year year, MonthDay monthDay, LocalDate expected) {
        assertEquals(expected, year.atMonthDay(monthDay));
    }

    @Test
    public void test_atMonthDay_nullMonthDay() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Year test = Year.of(2008);
            test.atMonthDay((MonthDay) null);
        });
    }

    //-----------------------------------------------------------------------
    // atDay(int)
    //-----------------------------------------------------------------------
    @Test
    public void test_atDay_notLeapYear() {
        Year test = Year.of(2007);
        LocalDate expected = LocalDate.of(2007, 1, 1);
        for (int i = 1; i <= 365; i++) {
            assertEquals(expected, test.atDay(i));
            expected = expected.plusDays(1);
        }
    }

    @Test
    public void test_atDay_notLeapYear_day366() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Year test = Year.of(2007);
            test.atDay(366);
        });
    }

    @Test
    public void test_atDay_leapYear() {
        Year test = Year.of(2008);
        LocalDate expected = LocalDate.of(2008, 1, 1);
        for (int i = 1; i <= 366; i++) {
            assertEquals(expected, test.atDay(i));
            expected = expected.plusDays(1);
        }
    }

    @Test
    public void test_atDay_day0() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Year test = Year.of(2007);
            test.atDay(0);
        });
    }

    @Test
    public void test_atDay_day367() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Year test = Year.of(2007);
            test.atDay(367);
        });
    }

    //-----------------------------------------------------------------------
    // compareTo()
    //-----------------------------------------------------------------------
    @Test
    public void test_compareTo() {
        for (int i = -4; i <= 2104; i++) {
            Year a = Year.of(i);
            for (int j = -4; j <= 2104; j++) {
                Year b = Year.of(j);
                if (i < j) {
                    assertEquals(true, a.compareTo(b) < 0);
                    assertEquals(true, b.compareTo(a) > 0);
                    assertEquals(false, a.isAfter(b));
                    assertEquals(true, a.isBefore(b));
                    assertEquals(true, b.isAfter(a));
                    assertEquals(false, b.isBefore(a));
                } else if (i > j) {
                    assertEquals(true, a.compareTo(b) > 0);
                    assertEquals(true, b.compareTo(a) < 0);
                    assertEquals(true, a.isAfter(b));
                    assertEquals(false, a.isBefore(b));
                    assertEquals(false, b.isAfter(a));
                    assertEquals(true, b.isBefore(a));
                } else {
                    assertEquals(0, a.compareTo(b));
                    assertEquals(0, b.compareTo(a));
                    assertEquals(false, a.isAfter(b));
                    assertEquals(false, a.isBefore(b));
                    assertEquals(false, b.isAfter(a));
                    assertEquals(false, b.isBefore(a));
                }
            }
        }
    }

    @Test
    public void test_compareTo_nullYear() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Year doy = null;
            Year test = Year.of(1);
            test.compareTo(doy);
        });
    }

    //-----------------------------------------------------------------------
    // equals() / hashCode()
    //-----------------------------------------------------------------------
    @Test
    public void test_equals() {
        for (int i = -4; i <= 2104; i++) {
            Year a = Year.of(i);
            for (int j = -4; j <= 2104; j++) {
                Year b = Year.of(j);
                assertEquals(i == j, a.equals(b));
                assertEquals(i == j, a.hashCode() == b.hashCode());
            }
        }
    }

    @Test
    public void test_equals_same() {
        Year test = Year.of(2011);
        assertEquals(true, test.equals(test));
    }

    @Test
    public void test_equals_nullYear() {
        Year doy = null;
        Year test = Year.of(1);
        assertEquals(false, test.equals(doy));
    }

    @Test
    public void test_equals_incorrectType() {
        Year test = Year.of(1);
        assertEquals(false, test.equals("Incorrect type"));
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @Test
    public void test_toString() {
        for (int i = -4; i <= 2104; i++) {
            Year a = Year.of(i);
            assertEquals("" + i, a.toString());
        }
    }

}
