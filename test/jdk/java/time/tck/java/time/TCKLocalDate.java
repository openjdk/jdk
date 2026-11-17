/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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
package tck.java.time;

import static java.time.temporal.ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH;
import static java.time.temporal.ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR;
import static java.time.temporal.ChronoField.ALIGNED_WEEK_OF_MONTH;
import static java.time.temporal.ChronoField.ALIGNED_WEEK_OF_YEAR;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.DAY_OF_YEAR;
import static java.time.temporal.ChronoField.EPOCH_DAY;
import static java.time.temporal.ChronoField.ERA;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.PROLEPTIC_MONTH;
import static java.time.temporal.ChronoField.YEAR;
import static java.time.temporal.ChronoField.YEAR_OF_ERA;
import static java.time.temporal.ChronoUnit.CENTURIES;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.DECADES;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MILLENNIA;
import static java.time.temporal.ChronoUnit.MONTHS;
import static java.time.temporal.ChronoUnit.WEEKS;
import static java.time.temporal.ChronoUnit.YEARS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.chrono.IsoChronology;
import java.time.chrono.IsoEra;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.JulianFields;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import test.java.time.MockSimplePeriod;
import test.java.time.temporal.MockFieldNoValue;

/**
 * Test LocalDate.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TCKLocalDate extends AbstractDateTimeTest {

    private static final ZoneOffset OFFSET_PONE = ZoneOffset.ofHours(1);
    private static final ZoneOffset OFFSET_PTWO = ZoneOffset.ofHours(2);
    private static final ZoneOffset OFFSET_MTWO = ZoneOffset.ofHours(-2);
    private static final ZoneId ZONE_PARIS = ZoneId.of("Europe/Paris");
    private static final ZoneId ZONE_GAZA = ZoneId.of("Asia/Gaza");

    private LocalDate TEST_2007_07_15;
    private long MAX_VALID_EPOCHDAYS;
    private long MIN_VALID_EPOCHDAYS;
    private LocalDate MAX_DATE;
    private LocalDate MIN_DATE;
    private Instant MAX_INSTANT;
    private Instant MIN_INSTANT;

    @BeforeEach
    public void setUp() {
        TEST_2007_07_15 = LocalDate.of(2007, 7, 15);

        LocalDate max = LocalDate.MAX;
        LocalDate min = LocalDate.MIN;
        MAX_VALID_EPOCHDAYS = max.toEpochDay();
        MIN_VALID_EPOCHDAYS = min.toEpochDay();
        MAX_DATE = max;
        MIN_DATE = min;
        MAX_INSTANT = max.atStartOfDay(ZoneOffset.UTC).toInstant();
        MIN_INSTANT = min.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    //-----------------------------------------------------------------------
    @Override
    protected List<TemporalAccessor> samples() {
        TemporalAccessor[] array = {LocalDate.of(2007, 7, 15), LocalDate.MAX, LocalDate.MIN, };
        return Arrays.asList(array);
    }

    @Override
    protected List<TemporalField> validFields() {
        TemporalField[] array = {
            DAY_OF_WEEK,
            ALIGNED_DAY_OF_WEEK_IN_MONTH,
            ALIGNED_DAY_OF_WEEK_IN_YEAR,
            DAY_OF_MONTH,
            DAY_OF_YEAR,
            EPOCH_DAY,
            ALIGNED_WEEK_OF_MONTH,
            ALIGNED_WEEK_OF_YEAR,
            MONTH_OF_YEAR,
            PROLEPTIC_MONTH,
            YEAR_OF_ERA,
            YEAR,
            ERA,
            JulianFields.JULIAN_DAY,
            JulianFields.MODIFIED_JULIAN_DAY,
            JulianFields.RATA_DIE,
        };
        return Arrays.asList(array);
    }

    @Override
    protected List<TemporalField> invalidFields() {
        List<TemporalField> list = new ArrayList<>(Arrays.<TemporalField>asList(ChronoField.values()));
        list.removeAll(validFields());
        return list;
    }

    //-----------------------------------------------------------------------
    private void check(LocalDate test, int y, int m, int d) {
        assertEquals(y, test.getYear());
        assertEquals(m, test.getMonth().getValue());
        assertEquals(d, test.getDayOfMonth());
        assertEquals(test, test);
        assertEquals(test.hashCode(), test.hashCode());
        assertEquals(test, LocalDate.of(y, m, d));
    }

    //-----------------------------------------------------------------------
    // constants
    //-----------------------------------------------------------------------
    @Test
    public void constant_MIN() {
        check(LocalDate.MIN, Year.MIN_VALUE, 1, 1);
    }

    @Test
    public void constant_MAX() {
        check(LocalDate.MAX, Year.MAX_VALUE, 12, 31);
    }

    //-----------------------------------------------------------------------
    // now()
    //-----------------------------------------------------------------------
    @Test
    public void now() {
        LocalDate expected = LocalDate.now(Clock.systemDefaultZone());
        LocalDate test = LocalDate.now();
        for (int i = 0; i < 100; i++) {
            if (expected.equals(test)) {
                return;
            }
            expected = LocalDate.now(Clock.systemDefaultZone());
            test = LocalDate.now();
        }
        assertEquals(expected, test);
    }

    //-----------------------------------------------------------------------
    // now(ZoneId)
    //-----------------------------------------------------------------------
    @Test
    public void now_ZoneId_nullZoneId() {
        Assertions.assertThrows(NullPointerException.class, () -> LocalDate.now((ZoneId) null));
    }

    @Test
    public void now_ZoneId() {
        ZoneId zone = ZoneId.of("UTC+01:02:03");
        LocalDate expected = LocalDate.now(Clock.system(zone));
        LocalDate test = LocalDate.now(zone);
        for (int i = 0; i < 100; i++) {
            if (expected.equals(test)) {
                return;
            }
            expected = LocalDate.now(Clock.system(zone));
            test = LocalDate.now(zone);
        }
        assertEquals(expected, test);
    }

    //-----------------------------------------------------------------------
    // now(Clock)
    //-----------------------------------------------------------------------
    @Test
    public void now_Clock_nullClock() {
        Assertions.assertThrows(NullPointerException.class, () -> LocalDate.now((Clock) null));
    }

    @Test
    public void now_Clock_allSecsInDay_utc() {
        for (int i = 0; i < (2 * 24 * 60 * 60); i++) {
            Instant instant = Instant.ofEpochSecond(i);
            Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
            LocalDate test = LocalDate.now(clock);
            assertEquals(1970, test.getYear());
            assertEquals(Month.JANUARY, test.getMonth());
            assertEquals((i < 24 * 60 * 60 ? 1 : 2), test.getDayOfMonth());
        }
    }

    @Test
    public void now_Clock_allSecsInDay_offset() {
        for (int i = 0; i < (2 * 24 * 60 * 60); i++) {
            Instant instant = Instant.ofEpochSecond(i);
            Clock clock = Clock.fixed(instant.minusSeconds(OFFSET_PONE.getTotalSeconds()), OFFSET_PONE);
            LocalDate test = LocalDate.now(clock);
            assertEquals(1970, test.getYear());
            assertEquals(Month.JANUARY, test.getMonth());
            assertEquals((i < 24 * 60 * 60) ? 1 : 2, test.getDayOfMonth());
        }
    }

    @Test
    public void now_Clock_allSecsInDay_beforeEpoch() {
        for (int i =-1; i >= -(2 * 24 * 60 * 60); i--) {
            Instant instant = Instant.ofEpochSecond(i);
            Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
            LocalDate test = LocalDate.now(clock);
            assertEquals(1969, test.getYear());
            assertEquals(Month.DECEMBER, test.getMonth());
            assertEquals((i >= -24 * 60 * 60 ? 31 : 30), test.getDayOfMonth());
        }
    }

    //-----------------------------------------------------------------------
    @Test
    public void now_Clock_maxYear() {
        Clock clock = Clock.fixed(MAX_INSTANT, ZoneOffset.UTC);
        LocalDate test = LocalDate.now(clock);
        assertEquals(MAX_DATE, test);
    }

    @Test
    public void now_Clock_tooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Clock clock = Clock.fixed(MAX_INSTANT.plusSeconds(24 * 60 * 60), ZoneOffset.UTC);
            LocalDate.now(clock);
        });
    }

    @Test
    public void now_Clock_minYear() {
        Clock clock = Clock.fixed(MIN_INSTANT, ZoneOffset.UTC);
        LocalDate test = LocalDate.now(clock);
        assertEquals(MIN_DATE, test);
    }

    @Test
    public void now_Clock_tooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Clock clock = Clock.fixed(MIN_INSTANT.minusNanos(1), ZoneOffset.UTC);
            LocalDate.now(clock);
        });
    }

    //-----------------------------------------------------------------------
    // of() factories
    //-----------------------------------------------------------------------
    @Test
    public void factory_of_intsMonth() {
        assertEquals(LocalDate.of(2007, Month.JULY, 15), TEST_2007_07_15);
    }

    @Test
    public void factory_of_intsMonth_29febNonLeap() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(2007, Month.FEBRUARY, 29));
    }

    @Test
    public void factory_of_intsMonth_31apr() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(2007, Month.APRIL, 31));
    }

    @Test
    public void factory_of_intsMonth_dayTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(2007, Month.JANUARY, 0));
    }

    @Test
    public void factory_of_intsMonth_dayTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(2007, Month.JANUARY, 32));
    }

    @Test
    public void factory_of_intsMonth_nullMonth() {
        Assertions.assertThrows(NullPointerException.class, () -> LocalDate.of(2007, null, 30));
    }

    @Test
    public void factory_of_intsMonth_yearTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Integer.MIN_VALUE, Month.JANUARY, 1));
    }

    //-----------------------------------------------------------------------
    @Test
    public void factory_of_ints() {
        check(TEST_2007_07_15, 2007, 7, 15);
    }

    @Test
    public void factory_of_ints_29febNonLeap() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(2007, 2, 29));
    }

    @Test
    public void factory_of_ints_31apr() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(2007, 4, 31));
    }

    @Test
    public void factory_of_ints_dayTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(2007, 1, 0));
    }

    @Test
    public void factory_of_ints_dayTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(2007, 1, 32));
    }

    @Test
    public void factory_of_ints_monthTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(2007, 0, 1));
    }

    @Test
    public void factory_of_ints_monthTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(2007, 13, 1));
    }

    @Test
    public void factory_of_ints_yearTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Integer.MIN_VALUE, 1, 1));
    }

    //-----------------------------------------------------------------------
    @Test
    public void factory_ofYearDay_ints_nonLeap() {
        LocalDate date = LocalDate.of(2007, 1, 1);
        for (int i = 1; i < 365; i++) {
            assertEquals(date, LocalDate.ofYearDay(2007, i));
            date = next(date);
        }
    }

    @Test
    public void factory_ofYearDay_ints_leap() {
        LocalDate date = LocalDate.of(2008, 1, 1);
        for (int i = 1; i < 366; i++) {
            assertEquals(date, LocalDate.ofYearDay(2008, i));
            date = next(date);
        }
    }

    @Test
    public void factory_ofYearDay_ints_366nonLeap() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.ofYearDay(2007, 366));
    }

    @Test
    public void factory_ofYearDay_ints_dayTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.ofYearDay(2007, 0));
    }

    @Test
    public void factory_ofYearDay_ints_dayTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.ofYearDay(2007, 367));
    }

    @Test
    public void factory_ofYearDay_ints_yearTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.ofYearDay(Integer.MIN_VALUE, 1));
    }

    //-----------------------------------------------------------------------
    // Since plusDays/minusDays actually depends on MJDays, it cannot be used for testing
    private LocalDate next(LocalDate date) {
        int newDayOfMonth = date.getDayOfMonth() + 1;
        if (newDayOfMonth <= date.getMonth().length(isIsoLeap(date.getYear()))) {
            return date.withDayOfMonth(newDayOfMonth);
        }
        date = date.withDayOfMonth(1);
        if (date.getMonth() == Month.DECEMBER) {
            date = date.withYear(date.getYear() + 1);
        }
        return date.with(date.getMonth().plus(1));
    }

    private LocalDate previous(LocalDate date) {
        int newDayOfMonth = date.getDayOfMonth() - 1;
        if (newDayOfMonth > 0) {
            return date.withDayOfMonth(newDayOfMonth);
        }
        date = date.with(date.getMonth().minus(1));
        if (date.getMonth() == Month.DECEMBER) {
            date = date.withYear(date.getYear() - 1);
        }
        return date.withDayOfMonth(date.getMonth().length(isIsoLeap(date.getYear())));
    }

     //-----------------------------------------------------------------------
     // ofInstant()
     //-----------------------------------------------------------------------
     Object[][] data_instantFactory() {
         return new Object[][] {
                 {Instant.ofEpochSecond(86400 + 3600 + 120 + 4, 500), ZONE_PARIS, LocalDate.of(1970, 1, 2)},
                 {Instant.ofEpochSecond(86400 + 3600 + 120 + 4, 500), OFFSET_MTWO, LocalDate.of(1970, 1, 1)},
                 {Instant.ofEpochSecond(-86400 + 4, 500), OFFSET_PTWO, LocalDate.of(1969, 12, 31)},
                 {OffsetDateTime.of(LocalDateTime.of(Year.MIN_VALUE, 1, 1, 0, 0), ZoneOffset.UTC).toInstant(),
                         ZoneOffset.UTC, LocalDate.MIN},
                 {OffsetDateTime.of(LocalDateTime.of(Year.MAX_VALUE, 12, 31, 23, 59, 59, 999_999_999), ZoneOffset.UTC).toInstant(),
                         ZoneOffset.UTC, LocalDate.MAX},
         };
     }

     @ParameterizedTest
    @MethodSource("data_instantFactory")
     public void factory_ofInstant(Instant instant, ZoneId zone, LocalDate expected) {
         LocalDate test = LocalDate.ofInstant(instant, zone);
         assertEquals(expected, test);
     }

     @Test
     public void factory_ofInstant_instantTooBig() {
         Assertions.assertThrows(DateTimeException.class, () -> LocalDate.ofInstant(Instant.MAX, OFFSET_PONE));
     }

     @Test
     public void factory_ofInstant_instantTooSmall() {
         Assertions.assertThrows(DateTimeException.class, () -> LocalDate.ofInstant(Instant.MIN, OFFSET_PONE));
     }

     @Test
     public void factory_ofInstant_nullInstant() {
         Assertions.assertThrows(NullPointerException.class, () -> LocalDate.ofInstant((Instant) null, ZONE_GAZA));
     }

     @Test
     public void factory_ofInstant_nullZone() {
         Assertions.assertThrows(NullPointerException.class, () -> LocalDate.ofInstant(Instant.EPOCH, (ZoneId) null));
     }

    //-----------------------------------------------------------------------
    // ofEpochDay()
    //-----------------------------------------------------------------------
    @Test
    public void factory_ofEpochDay() {
        long date_0000_01_01 = -678941 - 40587;
        assertEquals(LocalDate.of(1970, 1, 1), LocalDate.ofEpochDay(0));
        assertEquals(LocalDate.of(0, 1, 1), LocalDate.ofEpochDay(date_0000_01_01));
        assertEquals(LocalDate.of(-1, 12, 31), LocalDate.ofEpochDay(date_0000_01_01 - 1));
        assertEquals(LocalDate.of(Year.MAX_VALUE, 12, 31), LocalDate.ofEpochDay(MAX_VALID_EPOCHDAYS));
        assertEquals(LocalDate.of(Year.MIN_VALUE, 1, 1), LocalDate.ofEpochDay(MIN_VALID_EPOCHDAYS));

        LocalDate test = LocalDate.of(0, 1, 1);
        for (long i = date_0000_01_01; i < 700000; i++) {
            assertEquals(test, LocalDate.ofEpochDay(i));
            test = next(test);
        }
        test = LocalDate.of(0, 1, 1);
        for (long i = date_0000_01_01; i > -2000000; i--) {
            assertEquals(test, LocalDate.ofEpochDay(i));
            test = previous(test);
        }
    }

    @Test
    public void factory_ofEpochDay_aboveMax() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.ofEpochDay(MAX_VALID_EPOCHDAYS + 1));
    }

    @Test
    public void factory_ofEpochDay_belowMin() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.ofEpochDay(MIN_VALID_EPOCHDAYS - 1));
    }

    //-----------------------------------------------------------------------
    // from()
    //-----------------------------------------------------------------------
    @Test
    public void test_from_TemporalAccessor() {
        assertEquals(LocalDate.of(2007, 7, 15), LocalDate.from(LocalDate.of(2007, 7, 15)));
        assertEquals(LocalDate.of(2007, 7, 15), LocalDate.from(LocalDateTime.of(2007, 7, 15, 12, 30)));
    }

    @Test
    public void test_from_TemporalAccessor_invalid_noDerive() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.from(LocalTime.of(12, 30)));
    }

    @Test
    public void test_from_TemporalAccessor_null() {
        Assertions.assertThrows(NullPointerException.class, () -> LocalDate.from((TemporalAccessor) null));
    }

    //-----------------------------------------------------------------------
    // parse()
    //-----------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("provider_sampleToString")
    public void factory_parse_validText(int y, int m, int d, String parsable) {
        LocalDate t = LocalDate.parse(parsable);
        assertNotNull(t, parsable);
        assertEquals(y, t.getYear(), parsable);
        assertEquals(m, t.getMonth().getValue(), parsable);
        assertEquals(d, t.getDayOfMonth(), parsable);
    }

    Object[][] provider_sampleBadParse() {
        return new Object[][]{
                {"2008/07/05"},
                {"10000-01-01"},
                {"2008-1-1"},
                {"2008--01"},
                {"ABCD-02-01"},
                {"2008-AB-01"},
                {"2008-02-AB"},
                {"-0000-02-01"},
                {"2008-02-01Z"},
                {"2008-02-01+01:00"},
                {"2008-02-01+01:00[Europe/Paris]"},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sampleBadParse")
    public void factory_parse_invalidText(String unparsable) {
        Assertions.assertThrows(DateTimeParseException.class, () -> LocalDate.parse(unparsable));
    }

    @Test
    public void factory_parse_illegalValue() {
        Assertions.assertThrows(DateTimeParseException.class, () -> LocalDate.parse("2008-06-32"));
    }

    @Test
    public void factory_parse_invalidValue() {
        Assertions.assertThrows(DateTimeParseException.class, () -> LocalDate.parse("2008-06-31"));
    }

    @Test
    public void factory_parse_nullText() {
        Assertions.assertThrows(NullPointerException.class, () -> LocalDate.parse((String) null));
    }

    //-----------------------------------------------------------------------
    // parse(DateTimeFormatter)
    //-----------------------------------------------------------------------
    @Test
    public void factory_parse_formatter() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("y M d");
        LocalDate test = LocalDate.parse("2010 12 3", f);
        assertEquals(LocalDate.of(2010, 12, 3), test);
    }

    @Test
    public void factory_parse_formatter_nullText() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("y M d");
            LocalDate.parse((String) null, f);
        });
    }

    @Test
    public void factory_parse_formatter_nullFormatter() {
        Assertions.assertThrows(NullPointerException.class, () -> LocalDate.parse("ANY", null));
    }

    //-----------------------------------------------------------------------
    // isSupported(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_isSupported_TemporalField() {
        assertEquals(false, TEST_2007_07_15.isSupported((TemporalField) null));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.NANO_OF_SECOND));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.NANO_OF_DAY));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.MICRO_OF_SECOND));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.MICRO_OF_DAY));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.MILLI_OF_SECOND));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.MILLI_OF_DAY));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.SECOND_OF_MINUTE));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.SECOND_OF_DAY));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.MINUTE_OF_HOUR));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.MINUTE_OF_DAY));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.HOUR_OF_AMPM));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.CLOCK_HOUR_OF_AMPM));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.HOUR_OF_DAY));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.CLOCK_HOUR_OF_DAY));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.AMPM_OF_DAY));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoField.DAY_OF_WEEK));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoField.DAY_OF_MONTH));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoField.DAY_OF_YEAR));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoField.EPOCH_DAY));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoField.ALIGNED_WEEK_OF_MONTH));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoField.ALIGNED_WEEK_OF_YEAR));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoField.MONTH_OF_YEAR));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoField.PROLEPTIC_MONTH));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoField.YEAR));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoField.YEAR_OF_ERA));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoField.ERA));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.INSTANT_SECONDS));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoField.OFFSET_SECONDS));
    }

    //-----------------------------------------------------------------------
    // isSupported(TemporalUnit)
    //-----------------------------------------------------------------------
    @Test
    public void test_isSupported_TemporalUnit() {
        assertEquals(false, TEST_2007_07_15.isSupported((TemporalUnit) null));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoUnit.NANOS));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoUnit.MICROS));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoUnit.MILLIS));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoUnit.SECONDS));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoUnit.MINUTES));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoUnit.HOURS));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoUnit.HALF_DAYS));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoUnit.DAYS));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoUnit.WEEKS));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoUnit.MONTHS));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoUnit.YEARS));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoUnit.DECADES));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoUnit.CENTURIES));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoUnit.MILLENNIA));
        assertEquals(true, TEST_2007_07_15.isSupported(ChronoUnit.ERAS));
        assertEquals(false, TEST_2007_07_15.isSupported(ChronoUnit.FOREVER));
    }

    //-----------------------------------------------------------------------
    // get(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_get_TemporalField() {
        LocalDate test = LocalDate.of(2008, 6, 30);
        assertEquals(2008, test.get(YEAR));
        assertEquals(6, test.get(MONTH_OF_YEAR));
        assertEquals(2008, test.get(YEAR_OF_ERA));
        assertEquals(1, test.get(ERA));
        assertEquals(30, test.get(DAY_OF_MONTH));
        assertEquals(1, test.get(DAY_OF_WEEK));
        assertEquals(182, test.get(DAY_OF_YEAR));
    }

    @Test
    public void test_getLong_TemporalField() {
        LocalDate test = LocalDate.of(2008, 6, 30);
        assertEquals(2008, test.getLong(YEAR));
        assertEquals(6, test.getLong(MONTH_OF_YEAR));
        assertEquals(2008, test.getLong(YEAR_OF_ERA));
        assertEquals(1, test.getLong(ERA));
        assertEquals(2008 * 12 + 6 - 1, test.getLong(PROLEPTIC_MONTH));
        assertEquals(30, test.getLong(DAY_OF_MONTH));
        assertEquals(1, test.getLong(DAY_OF_WEEK));
        assertEquals(182, test.getLong(DAY_OF_YEAR));
    }

    //-----------------------------------------------------------------------
    // query(TemporalQuery)
    //-----------------------------------------------------------------------
    Object[][] data_query() {
        return new Object[][] {
                {TEST_2007_07_15, TemporalQueries.chronology(), IsoChronology.INSTANCE},
                {TEST_2007_07_15, TemporalQueries.zoneId(), null},
                {TEST_2007_07_15, TemporalQueries.precision(), ChronoUnit.DAYS},
                {TEST_2007_07_15, TemporalQueries.zone(), null},
                {TEST_2007_07_15, TemporalQueries.offset(), null},
                {TEST_2007_07_15, TemporalQueries.localDate(), TEST_2007_07_15},
                {TEST_2007_07_15, TemporalQueries.localTime(), null},
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
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2007_07_15.query(null));
    }

    //-----------------------------------------------------------------------
    // get*()
    //-----------------------------------------------------------------------
    Object[][] provider_sampleDates() {
        return new Object[][] {
            {2008, 7, 5},
            {2007, 7, 5},
            {2006, 7, 5},
            {2005, 7, 5},
            {2004, 1, 1},
            {-1, 1, 2},
        };
    }

    //-----------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("provider_sampleDates")
    public void test_get(int y, int m, int d) {
        LocalDate a = LocalDate.of(y, m, d);
        assertEquals(y, a.getYear());
        assertEquals(Month.of(m), a.getMonth());
        assertEquals(d, a.getDayOfMonth());
    }

    @ParameterizedTest
    @MethodSource("provider_sampleDates")
    public void test_getDOY(int y, int m, int d) {
        LocalDate a = LocalDate.of(y, m, d);
        int total = 0;
        for (int i = 1; i < m; i++) {
            total += Month.of(i).length(isIsoLeap(y));
        }
        int doy = total + d;
        assertEquals(doy, a.getDayOfYear());
    }

    @Test
    public void test_getDayOfWeek() {
        DayOfWeek dow = DayOfWeek.MONDAY;
        for (Month month : Month.values()) {
            int length = month.length(false);
            for (int i = 1; i <= length; i++) {
                LocalDate d = LocalDate.of(2007, month, i);
                assertSame(d.getDayOfWeek(), dow);
                dow = dow.plus(1);
            }
        }
    }

    //-----------------------------------------------------------------------
    // isLeapYear()
    //-----------------------------------------------------------------------
    @Test
    public void test_isLeapYear() {
        assertEquals(false, LocalDate.of(1999, 1, 1).isLeapYear());
        assertEquals(true, LocalDate.of(2000, 1, 1).isLeapYear());
        assertEquals(false, LocalDate.of(2001, 1, 1).isLeapYear());
        assertEquals(false, LocalDate.of(2002, 1, 1).isLeapYear());
        assertEquals(false, LocalDate.of(2003, 1, 1).isLeapYear());
        assertEquals(true, LocalDate.of(2004, 1, 1).isLeapYear());
        assertEquals(false, LocalDate.of(2005, 1, 1).isLeapYear());

        assertEquals(false, LocalDate.of(1500, 1, 1).isLeapYear());
        assertEquals(true, LocalDate.of(1600, 1, 1).isLeapYear());
        assertEquals(false, LocalDate.of(1700, 1, 1).isLeapYear());
        assertEquals(false, LocalDate.of(1800, 1, 1).isLeapYear());
        assertEquals(false, LocalDate.of(1900, 1, 1).isLeapYear());
    }

    //-----------------------------------------------------------------------
    // lengthOfMonth()
    //-----------------------------------------------------------------------
    @Test
    public void test_lengthOfMonth_notLeapYear() {
        assertEquals(31, LocalDate.of(2007, 1, 1).lengthOfMonth());
        assertEquals(28, LocalDate.of(2007, 2, 1).lengthOfMonth());
        assertEquals(31, LocalDate.of(2007, 3, 1).lengthOfMonth());
        assertEquals(30, LocalDate.of(2007, 4, 1).lengthOfMonth());
        assertEquals(31, LocalDate.of(2007, 5, 1).lengthOfMonth());
        assertEquals(30, LocalDate.of(2007, 6, 1).lengthOfMonth());
        assertEquals(31, LocalDate.of(2007, 7, 1).lengthOfMonth());
        assertEquals(31, LocalDate.of(2007, 8, 1).lengthOfMonth());
        assertEquals(30, LocalDate.of(2007, 9, 1).lengthOfMonth());
        assertEquals(31, LocalDate.of(2007, 10, 1).lengthOfMonth());
        assertEquals(30, LocalDate.of(2007, 11, 1).lengthOfMonth());
        assertEquals(31, LocalDate.of(2007, 12, 1).lengthOfMonth());
    }

    @Test
    public void test_lengthOfMonth_leapYear() {
        assertEquals(31, LocalDate.of(2008, 1, 1).lengthOfMonth());
        assertEquals(29, LocalDate.of(2008, 2, 1).lengthOfMonth());
        assertEquals(31, LocalDate.of(2008, 3, 1).lengthOfMonth());
        assertEquals(30, LocalDate.of(2008, 4, 1).lengthOfMonth());
        assertEquals(31, LocalDate.of(2008, 5, 1).lengthOfMonth());
        assertEquals(30, LocalDate.of(2008, 6, 1).lengthOfMonth());
        assertEquals(31, LocalDate.of(2008, 7, 1).lengthOfMonth());
        assertEquals(31, LocalDate.of(2008, 8, 1).lengthOfMonth());
        assertEquals(30, LocalDate.of(2008, 9, 1).lengthOfMonth());
        assertEquals(31, LocalDate.of(2008, 10, 1).lengthOfMonth());
        assertEquals(30, LocalDate.of(2008, 11, 1).lengthOfMonth());
        assertEquals(31, LocalDate.of(2008, 12, 1).lengthOfMonth());
    }

    //-----------------------------------------------------------------------
    // lengthOfYear()
    //-----------------------------------------------------------------------
    @Test
    public void test_lengthOfYear() {
        assertEquals(365, LocalDate.of(2007, 1, 1).lengthOfYear());
        assertEquals(366, LocalDate.of(2008, 1, 1).lengthOfYear());
    }

    //-----------------------------------------------------------------------
    // with()
    //-----------------------------------------------------------------------
    @Test
    public void test_with_adjustment() {
        final LocalDate sample = LocalDate.of(2012, 3, 4);
        TemporalAdjuster adjuster = new TemporalAdjuster() {
            @Override
            public Temporal adjustInto(Temporal dateTime) {
                return sample;
            }
        };
        assertEquals(sample, TEST_2007_07_15.with(adjuster));
    }

    @Test
    public void test_with_adjustment_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2007_07_15.with((TemporalAdjuster) null));
    }

    //-----------------------------------------------------------------------
    // with(TemporalField,long)
    //-----------------------------------------------------------------------
    @Test
    public void test_with_TemporalField_long_normal() {
        LocalDate t = TEST_2007_07_15.with(YEAR, 2008);
        assertEquals(LocalDate.of(2008, 7, 15), t);
    }

    @Test
    public void test_with_TemporalField_long_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2007_07_15.with((TemporalField) null, 1));
    }

    @Test
    public void test_with_TemporalField_long_invalidField() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_2007_07_15.with(MockFieldNoValue.INSTANCE, 1));
    }

    @Test
    public void test_with_TemporalField_long_timeField() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_2007_07_15.with(ChronoField.AMPM_OF_DAY, 1));
    }

    @Test
    public void test_with_TemporalField_long_invalidValue() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_2007_07_15.with(ChronoField.DAY_OF_WEEK, -1));
    }

    //-----------------------------------------------------------------------
    // withYear()
    //-----------------------------------------------------------------------
    @Test
    public void test_withYear_int_normal() {
        LocalDate t = TEST_2007_07_15.withYear(2008);
        assertEquals(LocalDate.of(2008, 7, 15), t);
    }

    @Test
    public void test_withYear_int_invalid() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_2007_07_15.withYear(Year.MIN_VALUE - 1));
    }

    @Test
    public void test_withYear_int_adjustDay() {
        LocalDate t = LocalDate.of(2008, 2, 29).withYear(2007);
        LocalDate expected = LocalDate.of(2007, 2, 28);
        assertEquals(expected, t);
    }

    //-----------------------------------------------------------------------
    // withMonth()
    //-----------------------------------------------------------------------
    @Test
    public void test_withMonth_int_normal() {
        LocalDate t = TEST_2007_07_15.withMonth(1);
        assertEquals(LocalDate.of(2007, 1, 15), t);
    }

    @Test
    public void test_withMonth_int_invalid() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_2007_07_15.withMonth(13));
    }

    @Test
    public void test_withMonth_int_adjustDay() {
        LocalDate t = LocalDate.of(2007, 12, 31).withMonth(11);
        LocalDate expected = LocalDate.of(2007, 11, 30);
        assertEquals(expected, t);
    }

    //-----------------------------------------------------------------------
    // withDayOfMonth()
    //-----------------------------------------------------------------------
    @Test
    public void test_withDayOfMonth_normal() {
        LocalDate t = TEST_2007_07_15.withDayOfMonth(1);
        assertEquals(LocalDate.of(2007, 7, 1), t);
    }

    @Test
    public void test_withDayOfMonth_illegal() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_2007_07_15.withDayOfMonth(32));
    }

    @Test
    public void test_withDayOfMonth_invalid() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(2007, 11, 30).withDayOfMonth(31));
    }

    //-----------------------------------------------------------------------
    // withDayOfYear(int)
    //-----------------------------------------------------------------------
    @Test
    public void test_withDayOfYear_normal() {
        LocalDate t = TEST_2007_07_15.withDayOfYear(33);
        assertEquals(LocalDate.of(2007, 2, 2), t);
    }

    @Test
    public void test_withDayOfYear_illegal() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_2007_07_15.withDayOfYear(367));
    }

    @Test
    public void test_withDayOfYear_invalid() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_2007_07_15.withDayOfYear(366));
    }

    //-----------------------------------------------------------------------
    // plus(Period)
    //-----------------------------------------------------------------------
    @Test
    public void test_plus_Period_positiveMonths() {
        MockSimplePeriod period = MockSimplePeriod.of(7, ChronoUnit.MONTHS);
        LocalDate t = TEST_2007_07_15.plus(period);
        assertEquals(LocalDate.of(2008, 2, 15), t);
    }

    @Test
    public void test_plus_Period_negativeDays() {
        MockSimplePeriod period = MockSimplePeriod.of(-25, ChronoUnit.DAYS);
        LocalDate t = TEST_2007_07_15.plus(period);
        assertEquals(LocalDate.of(2007, 6, 20), t);
    }

    @Test
    public void test_plus_Period_timeNotAllowed() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            MockSimplePeriod period = MockSimplePeriod.of(7, ChronoUnit.HOURS);
            TEST_2007_07_15.plus(period);
        });
    }

    @Test
    public void test_plus_Period_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2007_07_15.plus((MockSimplePeriod) null));
    }

    @Test
    public void test_plus_Period_invalidTooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            MockSimplePeriod period = MockSimplePeriod.of(1, ChronoUnit.YEARS);
            LocalDate.of(Year.MAX_VALUE, 1, 1).plus(period);
        });
    }

    @Test
    public void test_plus_Period_invalidTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            MockSimplePeriod period = MockSimplePeriod.of(-1, ChronoUnit.YEARS);
            LocalDate.of(Year.MIN_VALUE, 1, 1).plus(period);
        });
    }

    //-----------------------------------------------------------------------
    // plus(long,TemporalUnit)
    //-----------------------------------------------------------------------
    @Test
    public void test_plus_longTemporalUnit_positiveMonths() {
        LocalDate t = TEST_2007_07_15.plus(7, ChronoUnit.MONTHS);
        assertEquals(LocalDate.of(2008, 2, 15), t);
    }

    @Test
    public void test_plus_longTemporalUnit_negativeDays() {
        LocalDate t = TEST_2007_07_15.plus(-25, ChronoUnit.DAYS);
        assertEquals(LocalDate.of(2007, 6, 20), t);
    }

    @Test
    public void test_plus_longTemporalUnit_timeNotAllowed() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_2007_07_15.plus(7, ChronoUnit.HOURS));
    }

    @Test
    public void test_plus_longTemporalUnit_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2007_07_15.plus(1, (TemporalUnit) null));
    }

    @Test
    public void test_plus_longTemporalUnit_invalidTooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MAX_VALUE, 1, 1).plus(1, ChronoUnit.YEARS));
    }

    @Test
    public void test_plus_longTemporalUnit_invalidTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MIN_VALUE, 1, 1).plus(-1, ChronoUnit.YEARS));
    }

    //-----------------------------------------------------------------------
    // plusYears()
    //-----------------------------------------------------------------------
    @Test
    public void test_plusYears_long_normal() {
        LocalDate t = TEST_2007_07_15.plusYears(1);
        assertEquals(LocalDate.of(2008, 7, 15), t);
    }

    @Test
    public void test_plusYears_long_negative() {
        LocalDate t = TEST_2007_07_15.plusYears(-1);
        assertEquals(LocalDate.of(2006, 7, 15), t);
    }

    @Test
    public void test_plusYears_long_adjustDay() {
        LocalDate t = LocalDate.of(2008, 2, 29).plusYears(1);
        LocalDate expected = LocalDate.of(2009, 2, 28);
        assertEquals(expected, t);
    }

    @Test
    public void test_plusYears_long_big() {
        long years = 20L + Year.MAX_VALUE;
        LocalDate test = LocalDate.of(-40, 6, 1).plusYears(years);
        assertEquals(LocalDate.of((int) (-40L + years), 6, 1), test);
    }

    @Test
    public void test_plusYears_long_invalidTooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate test = LocalDate.of(Year.MAX_VALUE, 6, 1);
            test.plusYears(1);
        });
    }

    @Test
    public void test_plusYears_long_invalidTooLargeMaxAddMax() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate test = LocalDate.of(Year.MAX_VALUE, 12, 1);
            test.plusYears(Long.MAX_VALUE);
        });
    }

    @Test
    public void test_plusYears_long_invalidTooLargeMaxAddMin() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate test = LocalDate.of(Year.MAX_VALUE, 12, 1);
            test.plusYears(Long.MIN_VALUE);
        });
    }

    @Test
    public void test_plusYears_long_invalidTooSmall_validInt() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MIN_VALUE, 1, 1).plusYears(-1));
    }

    @Test
    public void test_plusYears_long_invalidTooSmall_invalidInt() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MIN_VALUE, 1, 1).plusYears(-10));
    }

    //-----------------------------------------------------------------------
    // plusMonths()
    //-----------------------------------------------------------------------
    @Test
    public void test_plusMonths_long_normal() {
        LocalDate t = TEST_2007_07_15.plusMonths(1);
        assertEquals(LocalDate.of(2007, 8, 15), t);
    }

    @Test
    public void test_plusMonths_long_overYears() {
        LocalDate t = TEST_2007_07_15.plusMonths(25);
        assertEquals(LocalDate.of(2009, 8, 15), t);
    }

    @Test
    public void test_plusMonths_long_negative() {
        LocalDate t = TEST_2007_07_15.plusMonths(-1);
        assertEquals(LocalDate.of(2007, 6, 15), t);
    }

    @Test
    public void test_plusMonths_long_negativeAcrossYear() {
        LocalDate t = TEST_2007_07_15.plusMonths(-7);
        assertEquals(LocalDate.of(2006, 12, 15), t);
    }

    @Test
    public void test_plusMonths_long_negativeOverYears() {
        LocalDate t = TEST_2007_07_15.plusMonths(-31);
        assertEquals(LocalDate.of(2004, 12, 15), t);
    }

    @Test
    public void test_plusMonths_long_adjustDayFromLeapYear() {
        LocalDate t = LocalDate.of(2008, 2, 29).plusMonths(12);
        LocalDate expected = LocalDate.of(2009, 2, 28);
        assertEquals(expected, t);
    }

    @Test
    public void test_plusMonths_long_adjustDayFromMonthLength() {
        LocalDate t = LocalDate.of(2007, 3, 31).plusMonths(1);
        LocalDate expected = LocalDate.of(2007, 4, 30);
        assertEquals(expected, t);
    }

    @Test
    public void test_plusMonths_long_big() {
        long months = 20L + Integer.MAX_VALUE;
        LocalDate test = LocalDate.of(-40, 6, 1).plusMonths(months);
        assertEquals(LocalDate.of((int) (-40L + months / 12), 6 + (int) (months % 12), 1), test);
    }

    @Test
    public void test_plusMonths_long_invalidTooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MAX_VALUE, 12, 1).plusMonths(1));
    }

    @Test
    public void test_plusMonths_long_invalidTooLargeMaxAddMax() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate test = LocalDate.of(Year.MAX_VALUE, 12, 1);
            test.plusMonths(Long.MAX_VALUE);
        });
    }

    @Test
    public void test_plusMonths_long_invalidTooLargeMaxAddMin() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate test = LocalDate.of(Year.MAX_VALUE, 12, 1);
            test.plusMonths(Long.MIN_VALUE);
        });
    }

    @Test
    public void test_plusMonths_long_invalidTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MIN_VALUE, 1, 1).plusMonths(-1));
    }

    @Test
    public void test_plusWeeks_normal() {
        LocalDate t = TEST_2007_07_15.plusWeeks(1);
        assertEquals(LocalDate.of(2007, 7, 22), t);
    }

    @Test
    public void test_plusWeeks_overMonths() {
        LocalDate t = TEST_2007_07_15.plusWeeks(9);
        assertEquals(LocalDate.of(2007, 9, 16), t);
    }

    @Test
    public void test_plusWeeks_overYears() {
        LocalDate t = LocalDate.of(2006, 7, 16).plusWeeks(52);
        assertEquals(TEST_2007_07_15, t);
    }

    @Test
    public void test_plusWeeks_overLeapYears() {
        LocalDate t = TEST_2007_07_15.plusYears(-1).plusWeeks(104);
        assertEquals(LocalDate.of(2008, 7, 12), t);
    }

    @Test
    public void test_plusWeeks_negative() {
        LocalDate t = TEST_2007_07_15.plusWeeks(-1);
        assertEquals(LocalDate.of(2007, 7, 8), t);
    }

    @Test
    public void test_plusWeeks_negativeAcrossYear() {
        LocalDate t = TEST_2007_07_15.plusWeeks(-28);
        assertEquals(LocalDate.of(2006, 12, 31), t);
    }

    @Test
    public void test_plusWeeks_negativeOverYears() {
        LocalDate t = TEST_2007_07_15.plusWeeks(-104);
        assertEquals(LocalDate.of(2005, 7, 17), t);
    }

    @Test
    public void test_plusWeeks_maximum() {
        LocalDate t = LocalDate.of(Year.MAX_VALUE, 12, 24).plusWeeks(1);
        LocalDate expected = LocalDate.of(Year.MAX_VALUE, 12, 31);
        assertEquals(expected, t);
    }

    @Test
    public void test_plusWeeks_minimum() {
        LocalDate t = LocalDate.of(Year.MIN_VALUE, 1, 8).plusWeeks(-1);
        LocalDate expected = LocalDate.of(Year.MIN_VALUE, 1, 1);
        assertEquals(expected, t);
    }

    @Test
    public void test_plusWeeks_invalidTooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MAX_VALUE, 12, 25).plusWeeks(1));
    }

    @Test
    public void test_plusWeeks_invalidTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MIN_VALUE, 1, 7).plusWeeks(-1));
    }

    @Test
    public void test_plusWeeks_invalidMaxMinusMax() {
        Assertions.assertThrows(ArithmeticException.class, () -> LocalDate.of(Year.MAX_VALUE, 12, 25).plusWeeks(Long.MAX_VALUE));
    }

    @Test
    public void test_plusWeeks_invalidMaxMinusMin() {
        Assertions.assertThrows(ArithmeticException.class, () -> LocalDate.of(Year.MAX_VALUE, 12, 25).plusWeeks(Long.MIN_VALUE));
    }
    //-----------------------------------------------------------------------
    Object[][] provider_plusDays() {
        return new Object[][] {
                {LocalDate.of(2007, 7, 15), 1, LocalDate.of(2007, 7, 16)},
                {LocalDate.of(2007, 7, 15), 17, LocalDate.of(2007, 8, 1)},
                {LocalDate.of(2007, 12, 31), 1, LocalDate.of(2008, 1, 1)},
                {LocalDate.of(2007, 1, 1), 58, LocalDate.of(2007, 2, 28)},
                {LocalDate.of(2007, 1, 1), 59, LocalDate.of(2007, 3, 1)},
                {LocalDate.of(2008, 1, 1), 60, LocalDate.of(2008, 3, 1)},
                {LocalDate.of(2007, 2, 1), 27, LocalDate.of(2007, 2, 28)},
                {LocalDate.of(2007, 2, 1), 28, LocalDate.of(2007, 3, 1)},
                {LocalDate.of(2007, 1, 1), 29, LocalDate.of(2007, 1, 30)},
                {LocalDate.of(2007, 1, 1), 30, LocalDate.of(2007, 1, 31)},
                {LocalDate.of(2007, 1, 15), 13, LocalDate.of(2007, 1, 28)},
                {LocalDate.of(2007, 1, 15), 14, LocalDate.of(2007, 1, 29)},
                {LocalDate.of(2007, 1, 15), 15, LocalDate.of(2007, 1, 30)},
                {LocalDate.of(2007, 1, 15), 16, LocalDate.of(2007, 1, 31)},
                {LocalDate.of(2007, 2, 15), 13, LocalDate.of(2007, 2, 28)},
                {LocalDate.of(2007, 2, 15), 14, LocalDate.of(2007, 3, 1)},
                {LocalDate.of(2007, 2, 15), 15, LocalDate.of(2007, 3, 2)},
                {LocalDate.of(2007, 2, 15), 16, LocalDate.of(2007, 3, 3)},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_plusDays")
    public void test_plusDays_normal(LocalDate input, int amountsToAdd, LocalDate expected) {
        LocalDate actual = input.plusDays(amountsToAdd);
        assertEquals(expected, actual);
     }

    @Test
    public void test_plusDays_overMonths() {
        LocalDate t = TEST_2007_07_15.plusDays(62);
        assertEquals(LocalDate.of(2007, 9, 15), t);
    }

    @Test
    public void test_plusDays_overYears() {
        LocalDate t = LocalDate.of(2006, 7, 14).plusDays(366);
        assertEquals(TEST_2007_07_15, t);
    }

    @Test
    public void test_plusDays_overLeapYears() {
        LocalDate t = TEST_2007_07_15.plusYears(-1).plusDays(365 + 366);
        assertEquals(LocalDate.of(2008, 7, 15), t);
    }

    @Test
    public void test_plusDays_negative() {
        LocalDate t = TEST_2007_07_15.plusDays(-1);
        assertEquals(LocalDate.of(2007, 7, 14), t);
    }

    @Test
    public void test_plusDays_negativeAcrossYear() {
        LocalDate t = TEST_2007_07_15.plusDays(-196);
        assertEquals(LocalDate.of(2006, 12, 31), t);
    }

    @Test
    public void test_plusDays_negativeOverYears() {
        LocalDate t = TEST_2007_07_15.plusDays(-730);
        assertEquals(LocalDate.of(2005, 7, 15), t);
    }

    @Test
    public void test_plusDays_maximum() {
        LocalDate t = LocalDate.of(Year.MAX_VALUE, 12, 30).plusDays(1);
        LocalDate expected = LocalDate.of(Year.MAX_VALUE, 12, 31);
        assertEquals(expected, t);
    }

    @Test
    public void test_plusDays_minimum() {
        LocalDate t = LocalDate.of(Year.MIN_VALUE, 1, 2).plusDays(-1);
        LocalDate expected = LocalDate.of(Year.MIN_VALUE, 1, 1);
        assertEquals(expected, t);
    }

    @Test
    public void test_plusDays_invalidTooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MAX_VALUE, 12, 31).plusDays(1));
    }

    @Test
    public void test_plusDays_invalidTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MIN_VALUE, 1, 1).plusDays(-1));
    }

    @Test
    public void test_plusDays_overflowTooLarge() {
        Assertions.assertThrows(ArithmeticException.class, () -> LocalDate.of(Year.MAX_VALUE, 12, 31).plusDays(Long.MAX_VALUE));
    }

    @Test
    public void test_plusDays_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> LocalDate.of(Year.MIN_VALUE, 1, 1).plusDays(Long.MIN_VALUE));
    }

    //-----------------------------------------------------------------------
    // minus(Period)
    //-----------------------------------------------------------------------
    @Test
    public void test_minus_Period_positiveMonths() {
        MockSimplePeriod period = MockSimplePeriod.of(7, ChronoUnit.MONTHS);
        LocalDate t = TEST_2007_07_15.minus(period);
        assertEquals(LocalDate.of(2006, 12, 15), t);
    }

    @Test
    public void test_minus_Period_negativeDays() {
        MockSimplePeriod period = MockSimplePeriod.of(-25, ChronoUnit.DAYS);
        LocalDate t = TEST_2007_07_15.minus(period);
        assertEquals(LocalDate.of(2007, 8, 9), t);
    }

    @Test
    public void test_minus_Period_timeNotAllowed() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            MockSimplePeriod period = MockSimplePeriod.of(7, ChronoUnit.HOURS);
            TEST_2007_07_15.minus(period);
        });
    }

    @Test
    public void test_minus_Period_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2007_07_15.minus((MockSimplePeriod) null));
    }

    @Test
    public void test_minus_Period_invalidTooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            MockSimplePeriod period = MockSimplePeriod.of(-1, ChronoUnit.YEARS);
            LocalDate.of(Year.MAX_VALUE, 1, 1).minus(period);
        });
    }

    @Test
    public void test_minus_Period_invalidTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            MockSimplePeriod period = MockSimplePeriod.of(1, ChronoUnit.YEARS);
            LocalDate.of(Year.MIN_VALUE, 1, 1).minus(period);
        });
    }

    //-----------------------------------------------------------------------
    // minus(long,TemporalUnit)
    //-----------------------------------------------------------------------
    @Test
    public void test_minus_longTemporalUnit_positiveMonths() {
        LocalDate t = TEST_2007_07_15.minus(7, ChronoUnit.MONTHS);
        assertEquals(LocalDate.of(2006, 12, 15), t);
    }

    @Test
    public void test_minus_longTemporalUnit_negativeDays() {
        LocalDate t = TEST_2007_07_15.minus(-25, ChronoUnit.DAYS);
        assertEquals(LocalDate.of(2007, 8, 9), t);
    }

    @Test
    public void test_minus_longTemporalUnit_timeNotAllowed() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_2007_07_15.minus(7, ChronoUnit.HOURS));
    }

    @Test
    public void test_minus_longTemporalUnit_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2007_07_15.minus(1, (TemporalUnit) null));
    }

    @Test
    public void test_minus_longTemporalUnit_invalidTooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MAX_VALUE, 1, 1).minus(-1, ChronoUnit.YEARS));
    }

    @Test
    public void test_minus_longTemporalUnit_invalidTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MIN_VALUE, 1, 1).minus(1, ChronoUnit.YEARS));
    }

    //-----------------------------------------------------------------------
    // minusYears()
    //-----------------------------------------------------------------------
    @Test
    public void test_minusYears_long_normal() {
        LocalDate t = TEST_2007_07_15.minusYears(1);
        assertEquals(LocalDate.of(2006, 7, 15), t);
    }

    @Test
    public void test_minusYears_long_negative() {
        LocalDate t = TEST_2007_07_15.minusYears(-1);
        assertEquals(LocalDate.of(2008, 7, 15), t);
    }

    @Test
    public void test_minusYears_long_adjustDay() {
        LocalDate t = LocalDate.of(2008, 2, 29).minusYears(1);
        LocalDate expected = LocalDate.of(2007, 2, 28);
        assertEquals(expected, t);
    }

    @Test
    public void test_minusYears_long_big() {
        long years = 20L + Year.MAX_VALUE;
        LocalDate test = LocalDate.of(40, 6, 1).minusYears(years);
        assertEquals(LocalDate.of((int) (40L - years), 6, 1), test);
    }

    @Test
    public void test_minusYears_long_invalidTooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate test = LocalDate.of(Year.MAX_VALUE, 6, 1);
            test.minusYears(-1);
        });
    }

    @Test
    public void test_minusYears_long_invalidTooLargeMaxAddMax() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate test = LocalDate.of(Year.MAX_VALUE, 12, 1);
            test.minusYears(Long.MAX_VALUE);
        });
    }

    @Test
    public void test_minusYears_long_invalidTooLargeMaxAddMin() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate test = LocalDate.of(Year.MAX_VALUE, 12, 1);
            test.minusYears(Long.MIN_VALUE);
        });
    }

    @Test
    public void test_minusYears_long_invalidTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MIN_VALUE, 1, 1).minusYears(1));
    }

    //-----------------------------------------------------------------------
    // minusMonths()
    //-----------------------------------------------------------------------
    @Test
    public void test_minusMonths_long_normal() {
        LocalDate t = TEST_2007_07_15.minusMonths(1);
        assertEquals(LocalDate.of(2007, 6, 15), t);
    }

    @Test
    public void test_minusMonths_long_overYears() {
        LocalDate t = TEST_2007_07_15.minusMonths(25);
        assertEquals(LocalDate.of(2005, 6, 15), t);
    }

    @Test
    public void test_minusMonths_long_negative() {
        LocalDate t = TEST_2007_07_15.minusMonths(-1);
        assertEquals(LocalDate.of(2007, 8, 15), t);
    }

    @Test
    public void test_minusMonths_long_negativeAcrossYear() {
        LocalDate t = TEST_2007_07_15.minusMonths(-7);
        assertEquals(LocalDate.of(2008, 2, 15), t);
    }

    @Test
    public void test_minusMonths_long_negativeOverYears() {
        LocalDate t = TEST_2007_07_15.minusMonths(-31);
        assertEquals(LocalDate.of(2010, 2, 15), t);
    }

    @Test
    public void test_minusMonths_long_adjustDayFromLeapYear() {
        LocalDate t = LocalDate.of(2008, 2, 29).minusMonths(12);
        LocalDate expected = LocalDate.of(2007, 2, 28);
        assertEquals(expected, t);
    }

    @Test
    public void test_minusMonths_long_adjustDayFromMonthLength() {
        LocalDate t = LocalDate.of(2007, 3, 31).minusMonths(1);
        LocalDate expected = LocalDate.of(2007, 2, 28);
        assertEquals(expected, t);
    }

    @Test
    public void test_minusMonths_long_big() {
        long months = 20L + Integer.MAX_VALUE;
        LocalDate test = LocalDate.of(40, 6, 1).minusMonths(months);
        assertEquals(LocalDate.of((int) (40L - months / 12), 6 - (int) (months % 12), 1), test);
    }

    @Test
    public void test_minusMonths_long_invalidTooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MAX_VALUE, 12, 1).minusMonths(-1));
    }

    @Test
    public void test_minusMonths_long_invalidTooLargeMaxAddMax() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate test = LocalDate.of(Year.MAX_VALUE, 12, 1);
            test.minusMonths(Long.MAX_VALUE);
        });
    }

    @Test
    public void test_minusMonths_long_invalidTooLargeMaxAddMin() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate test = LocalDate.of(Year.MAX_VALUE, 12, 1);
            test.minusMonths(Long.MIN_VALUE);
        });
    }

    @Test
    public void test_minusMonths_long_invalidTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MIN_VALUE, 1, 1).minusMonths(1));
    }

    @Test
    public void test_minusWeeks_normal() {
        LocalDate t = TEST_2007_07_15.minusWeeks(1);
        assertEquals(LocalDate.of(2007, 7, 8), t);
    }

    @Test
    public void test_minusWeeks_overMonths() {
        LocalDate t = TEST_2007_07_15.minusWeeks(9);
        assertEquals(LocalDate.of(2007, 5, 13), t);
    }

    @Test
    public void test_minusWeeks_overYears() {
        LocalDate t = LocalDate.of(2008, 7, 13).minusWeeks(52);
        assertEquals(TEST_2007_07_15, t);
    }

    @Test
    public void test_minusWeeks_overLeapYears() {
        LocalDate t = TEST_2007_07_15.minusYears(-1).minusWeeks(104);
        assertEquals(LocalDate.of(2006, 7, 18), t);
    }

    @Test
    public void test_minusWeeks_negative() {
        LocalDate t = TEST_2007_07_15.minusWeeks(-1);
        assertEquals(LocalDate.of(2007, 7, 22), t);
    }

    @Test
    public void test_minusWeeks_negativeAcrossYear() {
        LocalDate t = TEST_2007_07_15.minusWeeks(-28);
        assertEquals(LocalDate.of(2008, 1, 27), t);
    }

    @Test
    public void test_minusWeeks_negativeOverYears() {
        LocalDate t = TEST_2007_07_15.minusWeeks(-104);
        assertEquals(LocalDate.of(2009, 7, 12), t);
    }

    @Test
    public void test_minusWeeks_maximum() {
        LocalDate t = LocalDate.of(Year.MAX_VALUE, 12, 24).minusWeeks(-1);
        LocalDate expected = LocalDate.of(Year.MAX_VALUE, 12, 31);
        assertEquals(expected, t);
    }

    @Test
    public void test_minusWeeks_minimum() {
        LocalDate t = LocalDate.of(Year.MIN_VALUE, 1, 8).minusWeeks(1);
        LocalDate expected = LocalDate.of(Year.MIN_VALUE, 1, 1);
        assertEquals(expected, t);
    }

    @Test
    public void test_minusWeeks_invalidTooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MAX_VALUE, 12, 25).minusWeeks(-1));
    }

    @Test
    public void test_minusWeeks_invalidTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MIN_VALUE, 1, 7).minusWeeks(1));
    }

    @Test
    public void test_minusWeeks_invalidMaxMinusMax() {
        Assertions.assertThrows(ArithmeticException.class, () -> LocalDate.of(Year.MAX_VALUE, 12, 25).minusWeeks(Long.MAX_VALUE));
    }

    @Test
    public void test_minusWeeks_invalidMaxMinusMin() {
        Assertions.assertThrows(ArithmeticException.class, () -> LocalDate.of(Year.MAX_VALUE, 12, 25).minusWeeks(Long.MIN_VALUE));
    }

    @Test
    public void test_minusDays_normal() {
        LocalDate t = TEST_2007_07_15.minusDays(1);
        assertEquals(LocalDate.of(2007, 7, 14), t);
    }

    @Test
    public void test_minusDays_overMonths() {
        LocalDate t = TEST_2007_07_15.minusDays(62);
        assertEquals(LocalDate.of(2007, 5, 14), t);
    }

    @Test
    public void test_minusDays_overYears() {
        LocalDate t = LocalDate.of(2008, 7, 16).minusDays(367);
        assertEquals(TEST_2007_07_15, t);
    }

    @Test
    public void test_minusDays_overLeapYears() {
        LocalDate t = TEST_2007_07_15.plusYears(2).minusDays(365 + 366);
        assertEquals(TEST_2007_07_15, t);
    }

    @Test
    public void test_minusDays_negative() {
        LocalDate t = TEST_2007_07_15.minusDays(-1);
        assertEquals(LocalDate.of(2007, 7, 16), t);
    }

    @Test
    public void test_minusDays_negativeAcrossYear() {
        LocalDate t = TEST_2007_07_15.minusDays(-169);
        assertEquals(LocalDate.of(2007, 12, 31), t);
    }

    @Test
    public void test_minusDays_negativeOverYears() {
        LocalDate t = TEST_2007_07_15.minusDays(-731);
        assertEquals(LocalDate.of(2009, 7, 15), t);
    }

    @Test
    public void test_minusDays_maximum() {
        LocalDate t = LocalDate.of(Year.MAX_VALUE, 12, 30).minusDays(-1);
        LocalDate expected = LocalDate.of(Year.MAX_VALUE, 12, 31);
        assertEquals(expected, t);
    }

    @Test
    public void test_minusDays_minimum() {
        LocalDate t = LocalDate.of(Year.MIN_VALUE, 1, 2).minusDays(1);
        LocalDate expected = LocalDate.of(Year.MIN_VALUE, 1, 1);
        assertEquals(expected, t);
    }

    @Test
    public void test_minusDays_invalidTooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MAX_VALUE, 12, 31).minusDays(-1));
    }

    @Test
    public void test_minusDays_invalidTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.of(Year.MIN_VALUE, 1, 1).minusDays(1));
    }

    @Test
    public void test_minusDays_overflowTooLarge() {
        Assertions.assertThrows(ArithmeticException.class, () -> LocalDate.of(Year.MAX_VALUE, 12, 31).minusDays(Long.MIN_VALUE));
    }

    @Test
    public void test_minusDays_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> LocalDate.of(Year.MIN_VALUE, 1, 1).minusDays(Long.MAX_VALUE));
    }

    //-----------------------------------------------------------------------
    // until(Temporal, TemporalUnit)
    //-----------------------------------------------------------------------
    Object[][] data_periodUntilUnit() {
        return new Object[][] {
                {date(2000, 1, 1), date(2000, 1, 1), DAYS, 0},
                {date(2000, 1, 1), date(2000, 1, 1), WEEKS, 0},
                {date(2000, 1, 1), date(2000, 1, 1), MONTHS, 0},
                {date(2000, 1, 1), date(2000, 1, 1), YEARS, 0},
                {date(2000, 1, 1), date(2000, 1, 1), DECADES, 0},
                {date(2000, 1, 1), date(2000, 1, 1), CENTURIES, 0},
                {date(2000, 1, 1), date(2000, 1, 1), MILLENNIA, 0},

                {date(2000, 1, 15), date(2000, 2, 14), DAYS, 30},
                {date(2000, 1, 15), date(2000, 2, 15), DAYS, 31},
                {date(2000, 1, 15), date(2000, 2, 16), DAYS, 32},

                {date(2000, 1, 15), date(2000, 2, 17), WEEKS, 4},
                {date(2000, 1, 15), date(2000, 2, 18), WEEKS, 4},
                {date(2000, 1, 15), date(2000, 2, 19), WEEKS, 5},
                {date(2000, 1, 15), date(2000, 2, 20), WEEKS, 5},

                {date(2000, 1, 15), date(2000, 2, 14), MONTHS, 0},
                {date(2000, 1, 15), date(2000, 2, 15), MONTHS, 1},
                {date(2000, 1, 15), date(2000, 2, 16), MONTHS, 1},
                {date(2000, 1, 15), date(2000, 3, 14), MONTHS, 1},
                {date(2000, 1, 15), date(2000, 3, 15), MONTHS, 2},
                {date(2000, 1, 15), date(2000, 3, 16), MONTHS, 2},

                {date(2000, 1, 15), date(2001, 1, 14), YEARS, 0},
                {date(2000, 1, 15), date(2001, 1, 15), YEARS, 1},
                {date(2000, 1, 15), date(2001, 1, 16), YEARS, 1},
                {date(2000, 1, 15), date(2004, 1, 14), YEARS, 3},
                {date(2000, 1, 15), date(2004, 1, 15), YEARS, 4},
                {date(2000, 1, 15), date(2004, 1, 16), YEARS, 4},

                {date(2000, 1, 15), date(2010, 1, 14), DECADES, 0},
                {date(2000, 1, 15), date(2010, 1, 15), DECADES, 1},

                {date(2000, 1, 15), date(2100, 1, 14), CENTURIES, 0},
                {date(2000, 1, 15), date(2100, 1, 15), CENTURIES, 1},

                {date(2000, 1, 15), date(3000, 1, 14), MILLENNIA, 0},
                {date(2000, 1, 15), date(3000, 1, 15), MILLENNIA, 1},
        };
    }

    @ParameterizedTest
    @MethodSource("data_periodUntilUnit")
    public void test_until_TemporalUnit(LocalDate date1, LocalDate date2, TemporalUnit unit, long expected) {
        long amount = date1.until(date2, unit);
        assertEquals(expected, amount);
    }

    @ParameterizedTest
    @MethodSource("data_periodUntilUnit")
    public void test_until_TemporalUnit_negated(LocalDate date1, LocalDate date2, TemporalUnit unit, long expected) {
        long amount = date2.until(date1, unit);
        assertEquals(-expected, amount);
    }

    @ParameterizedTest
    @MethodSource("data_periodUntilUnit")
    public void test_until_TemporalUnit_between(LocalDate date1, LocalDate date2, TemporalUnit unit, long expected) {
        long amount = unit.between(date1, date2);
        assertEquals(expected, amount);
    }

    @Test
    public void test_until_convertedType() {
        LocalDate start = LocalDate.of(2010, 6, 30);
        OffsetDateTime end = start.plusDays(2).atStartOfDay().atOffset(OFFSET_PONE);
        assertEquals(2, start.until(end, DAYS));
    }

    @Test
    public void test_until_invalidType() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate start = LocalDate.of(2010, 6, 30);
            start.until(LocalTime.of(11, 30), DAYS);
        });
    }

    @Test
    public void test_until_TemporalUnit_unsupportedUnit() {
        Assertions.assertThrows(UnsupportedTemporalTypeException.class, () -> TEST_2007_07_15.until(TEST_2007_07_15, HOURS));
    }

    @Test
    public void test_until_TemporalUnit_nullEnd() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2007_07_15.until(null, DAYS));
    }

    @Test
    public void test_until_TemporalUnit_nullUnit() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2007_07_15.until(TEST_2007_07_15, null));
    }

    //-----------------------------------------------------------------------
    // until(ChronoLocalDate)
    //-----------------------------------------------------------------------
    Object[][] data_periodUntil() {
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

    @ParameterizedTest
    @MethodSource("data_periodUntil")
    public void test_periodUntil_LocalDate(int y1, int m1, int d1, int y2, int m2, int d2, int ye, int me, int de) {
        LocalDate start = LocalDate.of(y1, m1, d1);
        LocalDate end = LocalDate.of(y2, m2, d2);
        Period test = start.until(end);
        assertEquals(ye, test.getYears());
        assertEquals(me, test.getMonths());
        assertEquals(de, test.getDays());
    }

    @Test
    public void test_periodUntil_LocalDate_max() {
        int years = Math.toIntExact((long) Year.MAX_VALUE - (long) Year.MIN_VALUE);
        assertEquals(Period.of(years, 11, 30), LocalDate.MIN.until(LocalDate.MAX));
    }

    @Test
    public void test_periodUntil_LocalDate_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2007_07_15.until(null));
    }

    //-----------------------------------------------------------------------
    // format(DateTimeFormatter)
    //-----------------------------------------------------------------------
    @Test
    public void test_format_formatter() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("y M d");
        String t = LocalDate.of(2010, 12, 3).format(f);
        assertEquals("2010 12 3", t);
    }

    @Test
    public void test_format_formatter_null() {
        Assertions.assertThrows(NullPointerException.class, () -> LocalDate.of(2010, 12, 3).format(null));
    }

    //-----------------------------------------------------------------------
    // atTime()
    //-----------------------------------------------------------------------
    @Test
    public void test_atTime_LocalTime() {
        LocalDate t = LocalDate.of(2008, 6, 30);
        assertEquals(LocalDateTime.of(2008, 6, 30, 11, 30), t.atTime(LocalTime.of(11, 30)));
    }

    @Test
    public void test_atTime_LocalTime_null() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime((LocalTime) null);
        });
    }

    //-------------------------------------------------------------------------
    @Test
    public void test_atTime_int_int() {
        LocalDate t = LocalDate.of(2008, 6, 30);
        assertEquals(LocalDateTime.of(2008, 6, 30, 11, 30), t.atTime(11, 30));
    }

    @Test
    public void test_atTime_int_int_hourTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(-1, 30);
        });
    }

    @Test
    public void test_atTime_int_int_hourTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(24, 30);
        });
    }

    @Test
    public void test_atTime_int_int_minuteTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(11, -1);
        });
    }

    @Test
    public void test_atTime_int_int_minuteTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(11, 60);
        });
    }

    @Test
    public void test_atTime_int_int_int() {
        LocalDate t = LocalDate.of(2008, 6, 30);
        assertEquals(LocalDateTime.of(2008, 6, 30, 11, 30, 40), t.atTime(11, 30, 40));
    }

    @Test
    public void test_atTime_int_int_int_hourTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(-1, 30, 40);
        });
    }

    @Test
    public void test_atTime_int_int_int_hourTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(24, 30, 40);
        });
    }

    @Test
    public void test_atTime_int_int_int_minuteTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(11, -1, 40);
        });
    }

    @Test
    public void test_atTime_int_int_int_minuteTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(11, 60, 40);
        });
    }

    @Test
    public void test_atTime_int_int_int_secondTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(11, 30, -1);
        });
    }

    @Test
    public void test_atTime_int_int_int_secondTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(11, 30, 60);
        });
    }

    @Test
    public void test_atTime_int_int_int_int() {
        LocalDate t = LocalDate.of(2008, 6, 30);
        assertEquals(LocalDateTime.of(2008, 6, 30, 11, 30, 40, 50), t.atTime(11, 30, 40, 50));
    }

    @Test
    public void test_atTime_int_int_int_int_hourTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(-1, 30, 40, 50);
        });
    }

    @Test
    public void test_atTime_int_int_int_int_hourTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(24, 30, 40, 50);
        });
    }

    @Test
    public void test_atTime_int_int_int_int_minuteTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(11, -1, 40, 50);
        });
    }

    @Test
    public void test_atTime_int_int_int_int_minuteTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(11, 60, 40, 50);
        });
    }

    @Test
    public void test_atTime_int_int_int_int_secondTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(11, 30, -1, 50);
        });
    }

    @Test
    public void test_atTime_int_int_int_int_secondTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(11, 30, 60, 50);
        });
    }

    @Test
    public void test_atTime_int_int_int_int_nanoTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(11, 30, 40, -1);
        });
    }

    @Test
    public void test_atTime_int_int_int_int_nanoTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime(11, 30, 40, 1000000000);
        });
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_atTime_OffsetTime() {
        LocalDate t = LocalDate.of(2008, 6, 30);
        assertEquals(OffsetDateTime.of(2008, 6, 30, 11, 30, 0, 0, OFFSET_PONE), t.atTime(OffsetTime.of(11, 30, 0, 0, OFFSET_PONE)));
    }

    @Test
    public void test_atTime_OffsetTime_null() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atTime((OffsetTime) null);
        });
    }

    //-----------------------------------------------------------------------
    // atStartOfDay()
    //-----------------------------------------------------------------------
    Object[][] data_atStartOfDay() {
        return new Object[][] {
                {LocalDate.of(2008, 6, 30), LocalDateTime.of(2008, 6, 30, 0, 0)},
                {LocalDate.of(-12, 6, 30), LocalDateTime.of(-12, 6, 30, 0, 0)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_atStartOfDay")
    public void test_atStartOfDay(LocalDate test, LocalDateTime expected) {
        assertEquals(expected, test.atStartOfDay());
    }

    //-----------------------------------------------------------------------
    // atStartOfDay(ZoneId)
    //-----------------------------------------------------------------------
    Object[][] data_atStartOfDayZoneId() {
        return new Object[][] {
                {LocalDate.of(2008, 6, 30), ZONE_PARIS, ZonedDateTime.of(LocalDateTime.of(2008, 6, 30, 0, 0), ZONE_PARIS)},
                {LocalDate.of(2008, 6, 30), OFFSET_PONE, ZonedDateTime.of(LocalDateTime.of(2008, 6, 30, 0, 0), OFFSET_PONE)},
                {LocalDate.of(2007, 4, 1), ZONE_GAZA, ZonedDateTime.of(LocalDateTime.of(2007, 4, 1, 1, 0), ZONE_GAZA)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_atStartOfDayZoneId")
    public void test_atStartOfDay_ZoneId(LocalDate test, ZoneId zone, ZonedDateTime expected) {
        assertEquals(expected, test.atStartOfDay(zone));
    }

    @Test
    public void test_atStartOfDay_ZoneId_null() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            LocalDate t = LocalDate.of(2008, 6, 30);
            t.atStartOfDay((ZoneId) null);
        });
    }

    //-----------------------------------------------------------------------
    // toEpochDay()
    //-----------------------------------------------------------------------
    @Test
    public void test_toEpochDay() {
        long date_0000_01_01 = -678941 - 40587;

        LocalDate test = LocalDate.of(0, 1, 1);
        for (long i = date_0000_01_01; i < 700000; i++) {
            assertEquals(i, test.toEpochDay());
            test = next(test);
        }
        test = LocalDate.of(0, 1, 1);
        for (long i = date_0000_01_01; i > -2000000; i--) {
            assertEquals(i, test.toEpochDay());
            test = previous(test);
        }

        assertEquals(-40587, LocalDate.of(1858, 11, 17).toEpochDay());
        assertEquals(-678575 - 40587, LocalDate.of(1, 1, 1).toEpochDay());
        assertEquals(49987 - 40587, LocalDate.of(1995, 9, 27).toEpochDay());
        assertEquals(0, LocalDate.of(1970, 1, 1).toEpochDay());
        assertEquals(-678942 - 40587, LocalDate.of(-1, 12, 31).toEpochDay());
    }

    //-----------------------------------------------------------------------
    // toEpochSecond
    //-----------------------------------------------------------------------
    Object[][] provider_toEpochSecond() {
        return new Object[][] {
            {LocalDate.of(1858, 11, 17).toEpochSecond(LocalTime.MIDNIGHT, OFFSET_PONE), -3506720400L},
            {LocalDate.of(1, 1, 1).toEpochSecond(LocalTime.NOON, OFFSET_PONE), -62135557200L},
            {LocalDate.of(1995, 9, 27).toEpochSecond(LocalTime.of(5, 30), OFFSET_PTWO), 812172600L},
            {LocalDate.of(1970, 1, 1).toEpochSecond(LocalTime.MIDNIGHT, OFFSET_MTWO), 7200L},
            {LocalDate.of(-1, 12, 31).toEpochSecond(LocalTime.NOON, OFFSET_PONE), -62167266000L},
            {LocalDate.of(1, 1, 1).toEpochSecond(LocalTime.MIDNIGHT, OFFSET_PONE),
                    Instant.ofEpochSecond(-62135600400L).getEpochSecond()},
            {LocalDate.of(1995, 9, 27).toEpochSecond(LocalTime.NOON, OFFSET_PTWO),
                    Instant.ofEpochSecond(812196000L).getEpochSecond()},
            {LocalDate.of(1995, 9, 27).toEpochSecond(LocalTime.of(5, 30), OFFSET_MTWO),
                    LocalDateTime.of(1995, 9, 27, 5, 30).toEpochSecond(OFFSET_MTWO)},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_toEpochSecond")
    public void test_toEpochSecond(long actual, long expected) {
        assertEquals(expected, actual);
    }

    //-----------------------------------------------------------------------
    // compareTo()
    //-----------------------------------------------------------------------
    @Test
    public void test_comparisons() {
        doTest_comparisons_LocalDate(
            LocalDate.of(Year.MIN_VALUE, 1, 1),
            LocalDate.of(Year.MIN_VALUE, 12, 31),
            LocalDate.of(-1, 1, 1),
            LocalDate.of(-1, 12, 31),
            LocalDate.of(0, 1, 1),
            LocalDate.of(0, 12, 31),
            LocalDate.of(1, 1, 1),
            LocalDate.of(1, 12, 31),
            LocalDate.of(2006, 1, 1),
            LocalDate.of(2006, 12, 31),
            LocalDate.of(2007, 1, 1),
            LocalDate.of(2007, 12, 31),
            LocalDate.of(2008, 1, 1),
            LocalDate.of(2008, 2, 29),
            LocalDate.of(2008, 12, 31),
            LocalDate.of(Year.MAX_VALUE, 1, 1),
            LocalDate.of(Year.MAX_VALUE, 12, 31)
        );
    }

    void doTest_comparisons_LocalDate(LocalDate... localDates) {
        for (int i = 0; i < localDates.length; i++) {
            LocalDate a = localDates[i];
            for (int j = 0; j < localDates.length; j++) {
                LocalDate b = localDates[j];
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
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2007_07_15.compareTo(null));
    }

    @Test
    public void test_isBefore() {
        assertTrue(TEST_2007_07_15.isBefore(LocalDate.of(2007, 07, 16)));
        assertFalse(TEST_2007_07_15.isBefore(LocalDate.of(2007, 07, 14)));
        assertFalse(TEST_2007_07_15.isBefore(TEST_2007_07_15));
    }

    @Test
    public void test_isBefore_ObjectNull() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2007_07_15.isBefore(null));
    }

    @Test
    public void test_isAfter_ObjectNull() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_2007_07_15.isAfter(null));
    }

    @Test
    public void test_isAfter() {
        assertTrue(TEST_2007_07_15.isAfter(LocalDate.of(2007, 07, 14)));
        assertFalse(TEST_2007_07_15.isAfter(LocalDate.of(2007, 07, 16)));
        assertFalse(TEST_2007_07_15.isAfter(TEST_2007_07_15));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void compareToNonLocalDate() {
       Assertions.assertThrows(ClassCastException.class, () -> {
           Comparable c = TEST_2007_07_15;
           c.compareTo(new Object());
        });
    }

    //-----------------------------------------------------------------------
    // equals()
    //-----------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("provider_sampleDates")
    public void test_equals_true(int y, int m, int d) {
        LocalDate a = LocalDate.of(y, m, d);
        LocalDate b = LocalDate.of(y, m, d);
        assertEquals(true, a.equals(b));
    }
    @ParameterizedTest
    @MethodSource("provider_sampleDates")
    public void test_equals_false_year_differs(int y, int m, int d) {
        LocalDate a = LocalDate.of(y, m, d);
        LocalDate b = LocalDate.of(y + 1, m, d);
        assertEquals(false, a.equals(b));
    }
    @ParameterizedTest
    @MethodSource("provider_sampleDates")
    public void test_equals_false_month_differs(int y, int m, int d) {
        LocalDate a = LocalDate.of(y, m, d);
        LocalDate b = LocalDate.of(y, m + 1, d);
        assertEquals(false, a.equals(b));
    }
    @ParameterizedTest
    @MethodSource("provider_sampleDates")
    public void test_equals_false_day_differs(int y, int m, int d) {
        LocalDate a = LocalDate.of(y, m, d);
        LocalDate b = LocalDate.of(y, m, d + 1);
        assertEquals(false, a.equals(b));
    }

    @Test
    public void test_equals_itself_true() {
        assertEquals(true, TEST_2007_07_15.equals(TEST_2007_07_15));
    }

    @Test
    public void test_equals_string_false() {
        assertEquals(false, TEST_2007_07_15.equals("2007-07-15"));
    }

    @Test
    public void test_equals_null_false() {
        assertEquals(false, TEST_2007_07_15.equals(null));
    }

    //-----------------------------------------------------------------------
    // hashCode()
    //-----------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("provider_sampleDates")
    public void test_hashCode(int y, int m, int d) {
        LocalDate a = LocalDate.of(y, m, d);
        assertEquals(a.hashCode(), a.hashCode());
        LocalDate b = LocalDate.of(y, m, d);
        assertEquals(b.hashCode(), a.hashCode());
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    Object[][] provider_sampleToString() {
        return new Object[][] {
            {2008, 7, 5, "2008-07-05"},
            {2007, 12, 31, "2007-12-31"},
            {999, 12, 31, "0999-12-31"},
            {-1, 1, 2, "-0001-01-02"},
            {9999, 12, 31, "9999-12-31"},
            {-9999, 12, 31, "-9999-12-31"},
            {10000, 1, 1, "+10000-01-01"},
            {-10000, 1, 1, "-10000-01-01"},
            {12345678, 1, 1, "+12345678-01-01"},
            {-12345678, 1, 1, "-12345678-01-01"},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sampleToString")
    public void test_toString(int y, int m, int d, String expected) {
        LocalDate t = LocalDate.of(y, m, d);
        String str = t.toString();
        assertEquals(expected, str);
    }

    private LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    //-----------------------------------------------------------------
    // getEra()
    // ----------------------------------------------------------------
    @Test
    public void test_getEra() {
        IsoEra isoEra = LocalDate.MAX.getEra();
        assertSame(isoEra,IsoEra.CE);
        assertSame(LocalDate.MIN.getEra(),IsoEra.BCE);
    }

    //-----------------------------------------------------------------
    // datesUntil()
    // ----------------------------------------------------------------
    @Test
    public void test_datesUntil() {
        assertEquals(Arrays.asList(date(2015, 9, 29),
                        date(2015, 9, 30), date(2015, 10, 1), date(2015, 10, 2)), date(2015, 9, 29).datesUntil(date(2015, 10, 3)).collect(
                                Collectors.toList()));
        assertEquals(Arrays.asList(date(2015, 9, 29),
                date(2015, 10, 1)), date(2015, 9, 29).datesUntil(date(2015, 10, 3), Period.ofDays(2))
                        .collect(Collectors.toList()));
        assertEquals(Arrays.asList(date(2015, 1, 31),
                date(2015, 2, 28), date(2015, 3, 31), date(2015, 4, 30),
                date(2015, 5, 31)), date(2015, 1, 31).datesUntil(date(2015, 6, 1), Period.ofMonths(1))
                        .collect(Collectors.toList()));
    }

    @Test
    public void test_datesUntil_nullEnd() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            LocalDate date = date(2015, 1, 31);
            date.datesUntil(null);
        });
    }

    @Test
    public void test_datesUntil_nullEndStep() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            LocalDate date = date(2015, 1, 31);
            date.datesUntil(null, Period.ofDays(1));
        });
    }

    @Test
    public void test_datesUntil_nullStep() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            LocalDate date = date(2015, 1, 31);
            date.datesUntil(date, null);
        });
    }

    @Test
    public void test_datesUntil_endBeforeStart() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> date(2015, 1, 31).datesUntil(date(2015, 1, 30)));
    }

    @Test
    public void test_datesUntil_endBeforeStartPositiveStep() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> date(2015, 1, 31).datesUntil(date(2015, 1, 30), Period.of(1, 0, 0)));
    }

    @Test
    public void test_datesUntil_endAfterStartNegativeStep() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> date(2015, 1, 30).datesUntil(date(2015, 1, 31), Period.of(0, -1, -1)));
    }

    @Test
    public void test_datesUntil_zeroStep() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            LocalDate date = date(2015, 1, 31);
            date.datesUntil(date, Period.ZERO);
        });
    }

    @Test
    public void test_datesUntil_oppositeSign() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            LocalDate date = date(2015, 1, 31);
            date.datesUntil(date, Period.of(1, 0, -1));
        });
    }

    @Test
    public void test_datesUntil_oppositeSign2() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            LocalDate date = date(2015, 1, 31);
            date.datesUntil(date, Period.of(0, -1, 1));
        });
    }

    public Object[][] provider_datesUntil() {
        return new Object[][] {
                {MIN_DATE, MIN_DATE},
                {MIN_DATE, MAX_DATE},
                {MAX_DATE, MAX_DATE},
                {date(2015,10,1), date(2015,10,2)},
                {date(2015,10,1), date(2015,11,1)},
                {date(2015,10,31), date(2015,11,1)},
                {date(2015,10,1), MAX_DATE},
                {MIN_DATE, date(2015,10,1)}
        };
    }

    @ParameterizedTest
    @MethodSource("provider_datesUntil")
    public void test_datesUntil_count(LocalDate start, LocalDate end) {
        assertEquals(start.until(end, ChronoUnit.DAYS), start.datesUntil(end).count());
        assertEquals(start.until(end, ChronoUnit.DAYS), start.datesUntil(end, Period.ofDays(1)).count());
    }

    public Object[][] provider_datesUntil_steps() {
        List<Object[]> data = new ArrayList<>(Arrays.asList(new Object[][] {
            {MIN_DATE, MAX_DATE, Period.ofYears(Year.MAX_VALUE)},
            {MIN_DATE, MAX_DATE, Period.ofDays(2)},
            {MIN_DATE, MAX_DATE, Period.of(1,2,3)},
            {MIN_DATE, MAX_DATE, Period.of(1,2,1000000)},
            {MIN_DATE, MAX_DATE, Period.of(1,1000000,3)},
            {MIN_DATE, MAX_DATE, Period.of(1000000,2,3)},
            {MIN_DATE, MIN_DATE.plusMonths(1), Period.ofMonths(1)},
            {MIN_DATE, date(Year.MIN_VALUE, 2, 2), Period.ofMonths(1)},
            {MIN_DATE, date(Year.MIN_VALUE, 8, 9), Period.of(0, 1, 1)},
            {MIN_DATE, MAX_DATE.minusYears(1), Period.ofYears(Year.MAX_VALUE)},
            {MAX_DATE.minusMonths(1), MAX_DATE, Period.ofMonths(1)},
            {date(Year.MAX_VALUE, 2, 20), MAX_DATE, Period.of(0, 1, 1)},
            {date(2015,1,1), date(2016,1,1), Period.ofYears(1)},
            {date(2015,1,1), date(2016,1,1), Period.ofDays(365)},
            {date(2015,1,1), date(2016,1,1), Period.ofDays(366)},
            {date(2015,1,1), date(2016,1,1), Period.ofDays(4)},
            {date(2015,1,1), date(2016,1,1), Period.of(0,1,2)},
            {date(2015,1,1), date(2016,1,1), Period.ofMonths(1)},
            {date(2015,1,1), date(2016,1,1), Period.ofMonths(12)},
            {date(2015,1,1), date(2016,1,2), Period.ofMonths(12)},
            {date(2015,1,1), date(2016,1,1), Period.of(0, 11, 30)},
            {date(2015,1,1), date(2015,12,31), Period.of(0, 11, 30)},
            {date(2015,1,31), date(2015,12,31), Period.ofMonths(2)},
            {date(2015,1,31), date(2015,12,1), Period.ofMonths(2)},
            {date(2015,1,31), date(2015,11,30), Period.ofMonths(2)},
            {date(2015,1,31), date(2030,11,30), Period.of(1,30,365)},
            {date(2015,1,31), date(2043,1,31), Period.of(4,0,0)},
            {date(2015,1,31), date(2043,2,1), Period.of(4,0,0)},
            {date(2015,1,31), date(2043,1,31), Period.of(3,11,30)},
            {date(2015,1,31), date(2043,2,1), Period.of(3,11,30)},
            {date(2015,1,31), date(2043,1,31), Period.of(0,0,1460)},
            {date(2015,1,31), date(2043,1,31), Period.of(0,0,1461)},
            {date(2015,1,31), date(2043,2,1), Period.of(0,0,1461)},
            {date(2015,1,31), MAX_DATE, Period.of(10,100,1000)},
            {date(2015,1,31), MAX_DATE, Period.of(1000000,10000,100000)},
            {date(2015,1,31), MAX_DATE, Period.ofDays(10000000)},
            {date(2015,1,31), MAX_DATE, Period.ofDays(Integer.MAX_VALUE)},
            {date(2015,1,31), MAX_DATE, Period.ofMonths(Integer.MAX_VALUE)},
            {date(2015,1,31), MAX_DATE, Period.ofYears(Integer.MAX_VALUE)}
        }));
        LocalDate start = date(2014, 1, 15);
        LocalDate end = date(2015, 3, 4);
        for (int months : new int[] { 0, 1, 2, 3, 5, 7, 12, 13 }) {
            for (int days : new int[] { 0, 1, 2, 3, 5, 10, 17, 27, 28, 29, 30, 31, 32, 57, 58, 59,
                    60, 61, 62, 70, 80, 90 }) {
                if (months > 0 || days > 0)
                    data.add(new Object[] { start, end, Period.of(0, months, days) });
            }
        }
        for (int days = 27; days < 100; days++) {
            data.add(new Object[] { start, start.plusDays(days), Period.ofMonths(1) });
        }
        return data.toArray(new Object[data.size()][]);
    }

    @ParameterizedTest
    @MethodSource("provider_datesUntil_steps")
    public void test_datesUntil_step(LocalDate start, LocalDate end, Period step) {
        assertEquals(0, start.datesUntil(start, step).count());
        long count = start.datesUntil(end, step).count();
        assertTrue(count > 0);
        // the last value must be before the end date
        assertTrue(start.plusMonths(step.toTotalMonths()*(count-1)).plusDays(step.getDays()*(count-1)).isBefore(end));
        try {
            // the next after the last value must be either invalid or not before the end date
            assertFalse(start.plusMonths(step.toTotalMonths()*count).plusDays(step.getDays()*count).isBefore(end));
        } catch (ArithmeticException | DateTimeException e) {
            // ignore: possible overflow for the next value is ok
        }
        if(count < 1000) {
            assertTrue(start.datesUntil(end, step).allMatch(date -> !date.isBefore(start) && date.isBefore(end)));
            List<LocalDate> list = new ArrayList<>();
            for(long i=0; i<count; i++) {
                list.add(start.plusMonths(step.toTotalMonths()*i).plusDays(step.getDays()*i));
            }
            assertEquals(list, start.datesUntil(end, step).collect(Collectors.toList()));
        }

        // swap end and start and negate the Period
        count = end.datesUntil(start, step.negated()).count();
        assertTrue(count > 0);
        // the last value must be after the start date
        assertTrue(end.minusMonths(step.toTotalMonths()*(count-1)).minusDays(step.getDays()*(count-1)).isAfter(start));
        try {
            // the next after the last value must be either invalid or not after the start date
            assertFalse(end.minusMonths(step.toTotalMonths()*count).minusDays(step.getDays()*count).isAfter(start));
        } catch (ArithmeticException | DateTimeException e) {
            // ignore: possible overflow for the next value is ok
        }
        if(count < 1000) {
            assertTrue(end.datesUntil(start, step.negated()).allMatch(date -> date.isAfter(start) && !date.isAfter(end)));
            List<LocalDate> list = new ArrayList<>();
            for(long i=0; i<count; i++) {
                list.add(end.minusMonths(step.toTotalMonths()*i).minusDays(step.getDays()*i));
            }
            assertEquals(list, end.datesUntil(start, step.negated()).collect(Collectors.toList()));
        }
    }

    @Test
    public void test_datesUntil_staticType() {
        // Test the types of the Stream and elements of the stream
        LocalDate date = date(2015, 2, 10);
        Stream<LocalDate> stream = date.datesUntil(date.plusDays(5));
        long sum = stream.mapToInt(LocalDate::getDayOfMonth).sum();
        assertEquals(60, sum, "sum of 10, 11, 12, 13, 14 is wrong");
    }
}
