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
package tck.java.time;

import static java.time.temporal.ChronoField.AMPM_OF_DAY;
import static java.time.temporal.ChronoField.CLOCK_HOUR_OF_AMPM;
import static java.time.temporal.ChronoField.CLOCK_HOUR_OF_DAY;
import static java.time.temporal.ChronoField.HOUR_OF_AMPM;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MICRO_OF_DAY;
import static java.time.temporal.ChronoField.MICRO_OF_SECOND;
import static java.time.temporal.ChronoField.MILLI_OF_DAY;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.NANO_OF_DAY;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_DAY;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.FOREVER;
import static java.time.temporal.ChronoUnit.HALF_DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MICROS;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.MONTHS;
import static java.time.temporal.ChronoUnit.NANOS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.time.temporal.ChronoUnit.WEEKS;
import static java.time.temporal.ChronoUnit.YEARS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.JulianFields;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.time.temporal.ValueRange;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test LocalTime.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TCKLocalTime extends AbstractDateTimeTest {

    private static final ZoneOffset OFFSET_PTWO = ZoneOffset.ofHours(2);
    private static final ZoneOffset OFFSET_MTWO = ZoneOffset.ofHours(-2);
    private static final ZoneId ZONE_PARIS = ZoneId.of("Europe/Paris");

    private LocalTime TEST_12_30_40_987654321;

    private static final TemporalUnit[] INVALID_UNITS;
    static {
        EnumSet<ChronoUnit> set = EnumSet.range(DAYS, FOREVER);
        INVALID_UNITS = set.toArray(new TemporalUnit[set.size()]);
    }

    @BeforeEach
    public void setUp() {
        TEST_12_30_40_987654321 = LocalTime.of(12, 30, 40, 987654321);
    }

    //-----------------------------------------------------------------------
    @Override
    protected List<TemporalAccessor> samples() {
        TemporalAccessor[] array = {TEST_12_30_40_987654321, LocalTime.MIN, LocalTime.MAX, LocalTime.MIDNIGHT, LocalTime.NOON};
        return Arrays.asList(array);
    }

    @Override
    protected List<TemporalField> validFields() {
        TemporalField[] array = {
            NANO_OF_SECOND,
            NANO_OF_DAY,
            MICRO_OF_SECOND,
            MICRO_OF_DAY,
            MILLI_OF_SECOND,
            MILLI_OF_DAY,
            SECOND_OF_MINUTE,
            SECOND_OF_DAY,
            MINUTE_OF_HOUR,
            MINUTE_OF_DAY,
            CLOCK_HOUR_OF_AMPM,
            HOUR_OF_AMPM,
            CLOCK_HOUR_OF_DAY,
            HOUR_OF_DAY,
            AMPM_OF_DAY,
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

    private void check(LocalTime test, int h, int m, int s, int n) {
        assertEquals(h, test.getHour());
        assertEquals(m, test.getMinute());
        assertEquals(s, test.getSecond());
        assertEquals(n, test.getNano());
        assertEquals(test, test);
        assertEquals(test.hashCode(), test.hashCode());
        assertEquals(test, LocalTime.of(h, m, s, n));
    }

    //-----------------------------------------------------------------------
    // constants
    //-----------------------------------------------------------------------
    @Test
    public void constant_MIDNIGHT() {
        check(LocalTime.MIDNIGHT, 0, 0, 0, 0);
    }

    @Test
    public void constant_MIDDAY() {
        check(LocalTime.NOON, 12, 0, 0, 0);
    }

    @Test
    public void constant_MIN() {
        check(LocalTime.MIN, 0, 0, 0, 0);
    }

    @Test
    public void constant_MAX() {
        check(LocalTime.MAX, 23, 59, 59, 999999999);
    }

    //-----------------------------------------------------------------------
    // now(ZoneId)
    //-----------------------------------------------------------------------
    @Test
    public void now_ZoneId_nullZoneId() {
        Assertions.assertThrows(NullPointerException.class, () -> LocalTime.now((ZoneId) null));
    }

    @Test
    public void now_ZoneId() {
        ZoneId zone = ZoneId.of("UTC+01:02:03");
        LocalTime expected = LocalTime.now(Clock.system(zone));
        LocalTime test = LocalTime.now(zone);
        assertEquals(Duration.ZERO, Duration.between(expected, test).truncatedTo(ChronoUnit.SECONDS));
    }

    //-----------------------------------------------------------------------
    // now(Clock)
    //-----------------------------------------------------------------------
    @Test
    public void now_Clock_nullClock() {
        Assertions.assertThrows(NullPointerException.class, () -> LocalTime.now((Clock) null));
    }

    @Test
    public void now_Clock_allSecsInDay() {
        for (int i = 0; i < (2 * 24 * 60 * 60); i++) {
            Instant instant = Instant.ofEpochSecond(i, 8);
            Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
            LocalTime test = LocalTime.now(clock);
            assertEquals((i / (60 * 60)) % 24, test.getHour());
            assertEquals((i / 60) % 60, test.getMinute());
            assertEquals(i % 60, test.getSecond());
            assertEquals(8, test.getNano());
        }
    }

    @Test
    public void now_Clock_beforeEpoch() {
        for (int i =-1; i >= -(24 * 60 * 60); i--) {
            Instant instant = Instant.ofEpochSecond(i, 8);
            Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
            LocalTime test = LocalTime.now(clock);
            assertEquals(((i + 24 * 60 * 60) / (60 * 60)) % 24, test.getHour());
            assertEquals(((i + 24 * 60 * 60) / 60) % 60, test.getMinute());
            assertEquals((i + 24 * 60 * 60) % 60, test.getSecond());
            assertEquals(8, test.getNano());
        }
    }

    //-----------------------------------------------------------------------
    @Test
    public void now_Clock_max() {
        Clock clock = Clock.fixed(Instant.MAX, ZoneOffset.UTC);
        LocalTime test = LocalTime.now(clock);
        assertEquals(23, test.getHour());
        assertEquals(59, test.getMinute());
        assertEquals(59, test.getSecond());
        assertEquals(999_999_999, test.getNano());
    }

    @Test
    public void now_Clock_min() {
        Clock clock = Clock.fixed(Instant.MIN, ZoneOffset.UTC);
        LocalTime test = LocalTime.now(clock);
        assertEquals(0, test.getHour());
        assertEquals(0, test.getMinute());
        assertEquals(0, test.getSecond());
        assertEquals(0, test.getNano());
    }

    //-----------------------------------------------------------------------
    // of() factories
    //-----------------------------------------------------------------------
    @Test
    public void factory_time_2ints() {
        LocalTime test = LocalTime.of(12, 30);
        check(test, 12, 30, 0, 0);
    }

    @Test
    public void factory_time_2ints_hourTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(-1, 0));
    }

    @Test
    public void factory_time_2ints_hourTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(24, 0));
    }

    @Test
    public void factory_time_2ints_minuteTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(0, -1));
    }

    @Test
    public void factory_time_2ints_minuteTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(0, 60));
    }

    //-----------------------------------------------------------------------
    @Test
    public void factory_time_3ints() {
        LocalTime test = LocalTime.of(12, 30, 40);
        check(test, 12, 30, 40, 0);
    }

    @Test
    public void factory_time_3ints_hourTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(-1, 0, 0));
    }

    @Test
    public void factory_time_3ints_hourTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(24, 0, 0));
    }

    @Test
    public void factory_time_3ints_minuteTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(0, -1, 0));
    }

    @Test
    public void factory_time_3ints_minuteTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(0, 60, 0));
    }

    @Test
    public void factory_time_3ints_secondTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(0, 0, -1));
    }

    @Test
    public void factory_time_3ints_secondTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(0, 0, 60));
    }

    //-----------------------------------------------------------------------
    @Test
    public void factory_time_4ints() {
        LocalTime test = LocalTime.of(12, 30, 40, 987654321);
        check(test, 12, 30, 40, 987654321);
        test = LocalTime.of(12, 0, 40, 987654321);
        check(test, 12, 0, 40, 987654321);
    }

    @Test
    public void factory_time_4ints_hourTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(-1, 0, 0, 0));
    }

    @Test
    public void factory_time_4ints_hourTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(24, 0, 0, 0));
    }

    @Test
    public void factory_time_4ints_minuteTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(0, -1, 0, 0));
    }

    @Test
    public void factory_time_4ints_minuteTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(0, 60, 0, 0));
    }

    @Test
    public void factory_time_4ints_secondTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(0, 0, -1, 0));
    }

    @Test
    public void factory_time_4ints_secondTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(0, 0, 60, 0));
    }

    @Test
    public void factory_time_4ints_nanoTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(0, 0, 0, -1));
    }

    @Test
    public void factory_time_4ints_nanoTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.of(0, 0, 0, 1000000000));
    }

     //-----------------------------------------------------------------------
     // ofInstant()
     //-----------------------------------------------------------------------
     Object[][] data_instantFactory() {
         return new Object[][] {
                 {Instant.ofEpochSecond(86400 + 3600 + 120 + 4, 500), ZONE_PARIS, LocalTime.of(2, 2, 4, 500)},
                 {Instant.ofEpochSecond(86400 + 3600 + 120 + 4, 500), OFFSET_MTWO, LocalTime.of(23, 2, 4, 500)},
                 {Instant.ofEpochSecond(-86400 + 4, 500), OFFSET_PTWO, LocalTime.of(2, 0, 4, 500)},
                 {OffsetDateTime.of(LocalDateTime.of(Year.MIN_VALUE, 1, 1, 0, 0), ZoneOffset.UTC).toInstant(),
                         ZoneOffset.UTC, LocalTime.MIN},
                 {OffsetDateTime.of(LocalDateTime.of(Year.MAX_VALUE, 12, 31, 23, 59, 59, 999_999_999), ZoneOffset.UTC).toInstant(),
                         ZoneOffset.UTC, LocalTime.MAX},
         };
     }

     @ParameterizedTest
    @MethodSource("data_instantFactory")
     public void factory_ofInstant(Instant instant, ZoneId zone, LocalTime expected) {
         LocalTime test = LocalTime.ofInstant(instant, zone);
         assertEquals(expected, test);
     }

     @Test
     public void factory_ofInstant_nullInstant() {
         Assertions.assertThrows(NullPointerException.class, () -> LocalTime.ofInstant((Instant) null, ZONE_PARIS));
     }

     @Test
     public void factory_ofInstant_nullZone() {
         Assertions.assertThrows(NullPointerException.class, () -> LocalTime.ofInstant(Instant.EPOCH, (ZoneId) null));
     }

    //-----------------------------------------------------------------------
    // ofSecondOfDay(long)
    //-----------------------------------------------------------------------
    @Test
    public void factory_ofSecondOfDay() {
        LocalTime localTime = LocalTime.ofSecondOfDay(2 * 60 * 60 + 17 * 60 + 23);
        check(localTime, 2, 17, 23, 0);
    }

    @Test
    public void factory_ofSecondOfDay_tooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.ofSecondOfDay(-1));
    }

    @Test
    public void factory_ofSecondOfDay_tooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.ofSecondOfDay(24 * 60 * 60));
    }

    //-----------------------------------------------------------------------
    // ofNanoOfDay(long)
    //-----------------------------------------------------------------------
    @Test
    public void factory_ofNanoOfDay() {
        LocalTime localTime = LocalTime.ofNanoOfDay(60 * 60 * 1000000000L + 17);
        check(localTime, 1, 0, 0, 17);
    }

    @Test
    public void factory_ofNanoOfDay_tooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.ofNanoOfDay(-1));
    }

    @Test
    public void factory_ofNanoOfDay_tooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.ofNanoOfDay(24 * 60 * 60 * 1000000000L));
    }

    //-----------------------------------------------------------------------
    // from()
    //-----------------------------------------------------------------------
    @Test
    public void factory_from_TemporalAccessor() {
        assertEquals(LocalTime.of(17, 30), LocalTime.from(LocalTime.of(17, 30)));
        assertEquals(LocalTime.of(17, 30), LocalTime.from(LocalDateTime.of(2012, 5, 1, 17, 30)));
    }

    @Test
    public void factory_from_TemporalAccessor_invalid_noDerive() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalTime.from(LocalDate.of(2007, 7, 15)));
    }

    @Test
    public void factory_from_TemporalAccessor_null() {
        Assertions.assertThrows(NullPointerException.class, () -> LocalTime.from((TemporalAccessor) null));
    }

    //-----------------------------------------------------------------------
    // parse()
    //-----------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("provider_sampleToString")
    public void factory_parse_validText(int h, int m, int s, int n, String parsable) {
        LocalTime t = LocalTime.parse(parsable);
        assertNotNull(t, parsable);
        assertEquals(h, t.getHour());
        assertEquals(m, t.getMinute());
        assertEquals(s, t.getSecond());
        assertEquals(n, t.getNano());
    }

    Object[][] provider_sampleBadParse() {
        return new Object[][]{
                {"00;00"},
                {"12-00"},
                {"-01:00"},
                {"00:00:00-09"},
                {"00:00:00,09"},
                {"00:00:abs"},
                {"11"},
                {"11:30+01:00"},
                {"11:30+01:00[Europe/Paris]"},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sampleBadParse")
    public void factory_parse_invalidText(String unparsable) {
        Assertions.assertThrows(DateTimeParseException.class, () -> LocalTime.parse(unparsable));
    }

    //-----------------------------------------------------------------------s
    @Test
    public void factory_parse_illegalHour() {
        Assertions.assertThrows(DateTimeParseException.class, () -> LocalTime.parse("25:00"));
    }

    @Test
    public void factory_parse_illegalMinute() {
        Assertions.assertThrows(DateTimeParseException.class, () -> LocalTime.parse("12:60"));
    }

    @Test
    public void factory_parse_illegalSecond() {
        Assertions.assertThrows(DateTimeParseException.class, () -> LocalTime.parse("12:12:60"));
    }

    //-----------------------------------------------------------------------s
    @Test
    public void factory_parse_nullTest() {
        Assertions.assertThrows(NullPointerException.class, () -> LocalTime.parse((String) null));
    }

    //-----------------------------------------------------------------------
    // parse(DateTimeFormatter)
    //-----------------------------------------------------------------------
    @Test
    public void factory_parse_formatter() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("H m s");
        LocalTime test = LocalTime.parse("14 30 40", f);
        assertEquals(LocalTime.of(14, 30, 40), test);
    }

    @Test
    public void factory_parse_formatter_nullText() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("H m s");
            LocalTime.parse((String) null, f);
        });
    }

    @Test
    public void factory_parse_formatter_nullFormatter() {
        Assertions.assertThrows(NullPointerException.class, () -> LocalTime.parse("ANY", null));
    }

    //-----------------------------------------------------------------------
    // isSupported(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_isSupported_TemporalField() {
        assertEquals(false, TEST_12_30_40_987654321.isSupported((TemporalField) null));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.NANO_OF_SECOND));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.NANO_OF_DAY));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.MICRO_OF_SECOND));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.MICRO_OF_DAY));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.MILLI_OF_SECOND));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.MILLI_OF_DAY));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.SECOND_OF_MINUTE));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.SECOND_OF_DAY));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.MINUTE_OF_HOUR));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.MINUTE_OF_DAY));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.HOUR_OF_AMPM));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.CLOCK_HOUR_OF_AMPM));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.HOUR_OF_DAY));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.CLOCK_HOUR_OF_DAY));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoField.AMPM_OF_DAY));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.DAY_OF_WEEK));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.DAY_OF_MONTH));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.DAY_OF_YEAR));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.EPOCH_DAY));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.ALIGNED_WEEK_OF_MONTH));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.ALIGNED_WEEK_OF_YEAR));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.MONTH_OF_YEAR));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.PROLEPTIC_MONTH));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.YEAR));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.YEAR_OF_ERA));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.ERA));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.INSTANT_SECONDS));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoField.OFFSET_SECONDS));
    }

    //-----------------------------------------------------------------------
    // isSupported(TemporalUnit)
    //-----------------------------------------------------------------------
    @Test
    public void test_isSupported_TemporalUnit() {
        assertEquals(false, TEST_12_30_40_987654321.isSupported((TemporalUnit) null));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoUnit.NANOS));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoUnit.MICROS));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoUnit.MILLIS));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoUnit.SECONDS));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoUnit.MINUTES));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoUnit.HOURS));
        assertEquals(true, TEST_12_30_40_987654321.isSupported(ChronoUnit.HALF_DAYS));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoUnit.DAYS));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoUnit.WEEKS));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoUnit.MONTHS));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoUnit.YEARS));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoUnit.DECADES));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoUnit.CENTURIES));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoUnit.MILLENNIA));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoUnit.ERAS));
        assertEquals(false, TEST_12_30_40_987654321.isSupported(ChronoUnit.FOREVER));
    }

    //-----------------------------------------------------------------------
    // get(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_get_TemporalField() {
        LocalTime test = TEST_12_30_40_987654321;
        assertEquals(12, test.get(ChronoField.HOUR_OF_DAY));
        assertEquals(30, test.get(ChronoField.MINUTE_OF_HOUR));
        assertEquals(40, test.get(ChronoField.SECOND_OF_MINUTE));
        assertEquals(987654321, test.get(ChronoField.NANO_OF_SECOND));

        assertEquals(12 * 3600 + 30 * 60 + 40, test.get(ChronoField.SECOND_OF_DAY));
        assertEquals(12 * 60 + 30, test.get(ChronoField.MINUTE_OF_DAY));
        assertEquals(0, test.get(ChronoField.HOUR_OF_AMPM));
        assertEquals(12, test.get(ChronoField.CLOCK_HOUR_OF_AMPM));
        assertEquals(12, test.get(ChronoField.CLOCK_HOUR_OF_DAY));
        assertEquals(1, test.get(ChronoField.AMPM_OF_DAY));
    }

    @Test
    public void test_getLong_TemporalField() {
        LocalTime test = TEST_12_30_40_987654321;
        assertEquals(12, test.getLong(ChronoField.HOUR_OF_DAY));
        assertEquals(30, test.getLong(ChronoField.MINUTE_OF_HOUR));
        assertEquals(40, test.getLong(ChronoField.SECOND_OF_MINUTE));
        assertEquals(987654321, test.getLong(ChronoField.NANO_OF_SECOND));

        assertEquals(12 * 3600 + 30 * 60 + 40, test.getLong(ChronoField.SECOND_OF_DAY));
        assertEquals(12 * 60 + 30, test.getLong(ChronoField.MINUTE_OF_DAY));
        assertEquals(0, test.getLong(ChronoField.HOUR_OF_AMPM));
        assertEquals(12, test.getLong(ChronoField.CLOCK_HOUR_OF_AMPM));
        assertEquals(12, test.getLong(ChronoField.CLOCK_HOUR_OF_DAY));
        assertEquals(1, test.getLong(ChronoField.AMPM_OF_DAY));
    }

    //-----------------------------------------------------------------------
    // query(TemporalQuery)
    //-----------------------------------------------------------------------
    Object[][] data_query() {
        return new Object[][] {
                {TEST_12_30_40_987654321, TemporalQueries.chronology(), null},
                {TEST_12_30_40_987654321, TemporalQueries.zoneId(), null},
                {TEST_12_30_40_987654321, TemporalQueries.precision(), ChronoUnit.NANOS},
                {TEST_12_30_40_987654321, TemporalQueries.zone(), null},
                {TEST_12_30_40_987654321, TemporalQueries.offset(), null},
                {TEST_12_30_40_987654321, TemporalQueries.localDate(), null},
                {TEST_12_30_40_987654321, TemporalQueries.localTime(), TEST_12_30_40_987654321},
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
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12_30_40_987654321.query(null));
    }

    //-----------------------------------------------------------------------
    // get*()
    //-----------------------------------------------------------------------
    Object[][] provider_sampleTimes() {
        return new Object[][] {
            {0, 0, 0, 0},
            {0, 0, 0, 1},
            {0, 0, 1, 0},
            {0, 0, 1, 1},
            {0, 1, 0, 0},
            {0, 1, 0, 1},
            {0, 1, 1, 0},
            {0, 1, 1, 1},
            {1, 0, 0, 0},
            {1, 0, 0, 1},
            {1, 0, 1, 0},
            {1, 0, 1, 1},
            {1, 1, 0, 0},
            {1, 1, 0, 1},
            {1, 1, 1, 0},
            {1, 1, 1, 1},
        };
    }

    //-----------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("provider_sampleTimes")
    public void test_get(int h, int m, int s, int ns) {
        LocalTime a = LocalTime.of(h, m, s, ns);
        assertEquals(h, a.getHour());
        assertEquals(m, a.getMinute());
        assertEquals(s, a.getSecond());
        assertEquals(ns, a.getNano());
    }

    //-----------------------------------------------------------------------
    // adjustInto(Temporal)
    //-----------------------------------------------------------------------
    Object[][] data_adjustInto() {
        return new Object[][]{
                {LocalTime.of(23, 5), LocalTime.of(4, 1, 1, 100), LocalTime.of(23, 5, 0, 0), null},
                {LocalTime.of(23, 5, 20), LocalTime.of(4, 1, 1, 100), LocalTime.of(23, 5, 20, 0), null},
                {LocalTime.of(23, 5, 20, 1000), LocalTime.of(4, 1, 1, 100), LocalTime.of(23, 5, 20, 1000), null},
                {LocalTime.of(23, 5, 20, 1000), LocalTime.MAX, LocalTime.of(23, 5, 20, 1000), null},
                {LocalTime.of(23, 5, 20, 1000), LocalTime.MIN, LocalTime.of(23, 5, 20, 1000), null},
                {LocalTime.of(23, 5, 20, 1000), LocalTime.NOON, LocalTime.of(23, 5, 20, 1000), null},
                {LocalTime.of(23, 5, 20, 1000), LocalTime.MIDNIGHT, LocalTime.of(23, 5, 20, 1000), null},
                {LocalTime.MAX, LocalTime.of(23, 5, 20, 1000), LocalTime.of(23, 59, 59, 999999999), null},
                {LocalTime.MIN, LocalTime.of(23, 5, 20, 1000), LocalTime.of(0, 0, 0), null},
                {LocalTime.NOON, LocalTime.of(23, 5, 20, 1000), LocalTime.of(12, 0, 0), null},
                {LocalTime.MIDNIGHT, LocalTime.of(23, 5, 20, 1000), LocalTime.of(0, 0, 0), null},

                {LocalTime.of(23, 5), LocalDateTime.of(2210, 2, 2, 1, 1), LocalDateTime.of(2210, 2, 2, 23, 5), null},
                {LocalTime.of(23, 5), OffsetTime.of(1, 1, 0, 0, OFFSET_PTWO), OffsetTime.of(23, 5, 0, 0, OFFSET_PTWO), null},
                {LocalTime.of(23, 5), OffsetDateTime.of(2210, 2, 2, 1, 1, 0, 0, OFFSET_PTWO), OffsetDateTime.of(2210, 2, 2, 23, 5, 0, 0, OFFSET_PTWO), null},
                {LocalTime.of(23, 5), ZonedDateTime.of(2210, 2, 2, 1, 1, 0, 0, ZONE_PARIS), ZonedDateTime.of(2210, 2, 2, 23, 5, 0, 0, ZONE_PARIS), null},

                {LocalTime.of(23, 5), LocalDate.of(2210, 2, 2), null, DateTimeException.class},
                {LocalTime.of(23, 5), null, null, NullPointerException.class},

        };
    }

    @ParameterizedTest
    @MethodSource("data_adjustInto")
    public void test_adjustInto(LocalTime test, Temporal temporal, Temporal expected, Class<?> expectedEx) {
        if (expectedEx == null) {
            Temporal result = test.adjustInto(temporal);
            assertEquals(expected, result);
        } else {
            try {
                Temporal result = test.adjustInto(temporal);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    //-----------------------------------------------------------------------
    // with(TemporalAdjuster)
    //-----------------------------------------------------------------------
    @Test
    public void test_with_adjustment() {
        final LocalTime sample = LocalTime.of(23, 5);
        TemporalAdjuster adjuster = new TemporalAdjuster() {
            @Override
            public Temporal adjustInto(Temporal dateTime) {
                return sample;
            }
        };
        assertEquals(sample, TEST_12_30_40_987654321.with(adjuster));
    }

    @Test
    public void test_with_adjustment_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12_30_40_987654321.with((TemporalAdjuster) null));
    }

    //-----------------------------------------------------------------------
    // with(TemporalField, long)
    //-----------------------------------------------------------------------
    private long[] testPoints(long max) {
        long[] points = new long[9];
        points[0] = 0;
        points[1] = 1;
        points[2] = 2;
        points[3] = max / 7;
        points[4] = (max / 7) * 2;
        points[5] = (max / 2);
        points[6] = (max / 7) * 6;;
        points[7] = max - 2;
        points[8] = max - 1;
        return points;
    }

    // Returns a {@code LocalTime} with the specified nano-of-second.
    // The hour, minute and second will be unchanged.
    @Test
    public void test_with_longTemporalField_nanoOfSecond() {
        for (long i : testPoints(1_000_000_000L)) {
            LocalTime test = TEST_12_30_40_987654321.with(NANO_OF_SECOND, i);
            assertEquals(i, test.get(NANO_OF_SECOND));
            assertEquals(TEST_12_30_40_987654321.get(HOUR_OF_DAY), test.get(HOUR_OF_DAY));
            assertEquals(TEST_12_30_40_987654321.get(MINUTE_OF_HOUR), test.get(MINUTE_OF_HOUR));
            assertEquals(TEST_12_30_40_987654321.get(SECOND_OF_MINUTE), test.get(SECOND_OF_MINUTE));
        }
    }

    // Returns a {@code LocalTime} with the specified nano-of-day.
    // This completely replaces the time and is equivalent to {@link #ofNanoOfDay(long)}.
    @Test
    public void test_with_longTemporalField_nanoOfDay() {
        for (long i : testPoints(86_400_000_000_000L)) {
            LocalTime test = TEST_12_30_40_987654321.with(NANO_OF_DAY, i);
            assertEquals(LocalTime.ofNanoOfDay(i), test);
        }
    }

    // Returns a {@code LocalTime} with the nano-of-second replaced by the specified
    // micro-of-second multiplied by 1,000.
    // The hour, minute and second will be unchanged.
    @Test
    public void test_with_longTemporalField_microOfSecond() {
        for (long i : testPoints(1_000_000L)) {
            LocalTime test = TEST_12_30_40_987654321.with(MICRO_OF_SECOND, i);
            assertEquals(i * 1_000, test.get(NANO_OF_SECOND));
            assertEquals(TEST_12_30_40_987654321.get(HOUR_OF_DAY), test.get(HOUR_OF_DAY));
            assertEquals(TEST_12_30_40_987654321.get(MINUTE_OF_HOUR), test.get(MINUTE_OF_HOUR));
            assertEquals(TEST_12_30_40_987654321.get(SECOND_OF_MINUTE), test.get(SECOND_OF_MINUTE));
        }
    }

    // Returns a {@code LocalTime} with the specified micro-of-day.
    // This completely replaces the time and is equivalent to using {@link #ofNanoOfDay(long)}
    // with the micro-of-day multiplied by 1,000.
    @Test
    public void test_with_longTemporalField_microOfDay() {
        for (long i : testPoints(86_400_000_000L)) {
            LocalTime test = TEST_12_30_40_987654321.with(MICRO_OF_DAY, i);
            assertEquals(LocalTime.ofNanoOfDay(i * 1000), test);
        }
    }

    // Returns a {@code LocalTime} with the nano-of-second replaced by the specified
    // milli-of-second multiplied by 1,000,000.
    // The hour, minute and second will be unchanged.
    @Test
    public void test_with_longTemporalField_milliOfSecond() {
        for (long i : testPoints(1_000L)) {
            LocalTime test = TEST_12_30_40_987654321.with(MILLI_OF_SECOND, i);
            assertEquals(i * 1_000_000, test.get(NANO_OF_SECOND));
            assertEquals(TEST_12_30_40_987654321.get(HOUR_OF_DAY), test.get(HOUR_OF_DAY));
            assertEquals(TEST_12_30_40_987654321.get(MINUTE_OF_HOUR), test.get(MINUTE_OF_HOUR));
            assertEquals(TEST_12_30_40_987654321.get(SECOND_OF_MINUTE), test.get(SECOND_OF_MINUTE));
        }
    }

    // Returns a {@code LocalTime} with the specified milli-of-day.
    // This completely replaces the time and is equivalent to using {@link #ofNanoOfDay(long)}
    // with the milli-of-day multiplied by 1,000,000.
    @Test
    public void test_with_longTemporalField_milliOfDay() {
        for (long i : testPoints(86_400_000L)) {
            LocalTime test = TEST_12_30_40_987654321.with(MILLI_OF_DAY, i);
            assertEquals(LocalTime.ofNanoOfDay(i * 1_000_000), test);
        }
    }

    // Returns a {@code LocalTime} with the specified second-of-minute.
    // The hour, minute and nano-of-second will be unchanged.
    @Test
    public void test_with_longTemporalField_secondOfMinute() {
        for (long i : testPoints(60L)) {
            LocalTime test = TEST_12_30_40_987654321.with(SECOND_OF_MINUTE, i);
            assertEquals(i, test.get(SECOND_OF_MINUTE));
            assertEquals(TEST_12_30_40_987654321.get(HOUR_OF_DAY), test.get(HOUR_OF_DAY));
            assertEquals(TEST_12_30_40_987654321.get(MINUTE_OF_HOUR), test.get(MINUTE_OF_HOUR));
            assertEquals(TEST_12_30_40_987654321.get(NANO_OF_SECOND), test.get(NANO_OF_SECOND));
        }
    }

    // Returns a {@code LocalTime} with the specified second-of-day.
    // The nano-of-second will be unchanged.
    @Test
    public void test_with_longTemporalField_secondOfDay() {
        for (long i : testPoints(24 * 60 * 60)) {
            LocalTime test = TEST_12_30_40_987654321.with(SECOND_OF_DAY, i);
            assertEquals(i, test.get(SECOND_OF_DAY));
            assertEquals(TEST_12_30_40_987654321.get(NANO_OF_SECOND), test.get(NANO_OF_SECOND));
        }
    }

    // Returns a {@code LocalTime} with the specified minute-of-hour.
    // The hour, second-of-minute and nano-of-second will be unchanged.
    @Test
    public void test_with_longTemporalField_minuteOfHour() {
        for (long i : testPoints(60)) {
            LocalTime test = TEST_12_30_40_987654321.with(MINUTE_OF_HOUR, i);
            assertEquals(i, test.get(MINUTE_OF_HOUR));
            assertEquals(TEST_12_30_40_987654321.get(HOUR_OF_DAY), test.get(HOUR_OF_DAY));
            assertEquals(TEST_12_30_40_987654321.get(SECOND_OF_MINUTE), test.get(SECOND_OF_MINUTE));
            assertEquals(TEST_12_30_40_987654321.get(NANO_OF_SECOND), test.get(NANO_OF_SECOND));
        }
    }

    // Returns a {@code LocalTime} with the specified minute-of-day.
    // The second-of-minute and nano-of-second will be unchanged.
    @Test
    public void test_with_longTemporalField_minuteOfDay() {
        for (long i : testPoints(24 * 60)) {
            LocalTime test = TEST_12_30_40_987654321.with(MINUTE_OF_DAY, i);
            assertEquals(i, test.get(MINUTE_OF_DAY));
            assertEquals(TEST_12_30_40_987654321.get(SECOND_OF_MINUTE), test.get(SECOND_OF_MINUTE));
            assertEquals(TEST_12_30_40_987654321.get(NANO_OF_SECOND), test.get(NANO_OF_SECOND));
        }
    }

    // Returns a {@code LocalTime} with the specified hour-of-am-pm.
    // The AM/PM, minute-of-hour, second-of-minute and nano-of-second will be unchanged.
    @Test
    public void test_with_longTemporalField_hourOfAmPm() {
        for (int i = 0; i < 12; i++) {
            LocalTime test = TEST_12_30_40_987654321.with(HOUR_OF_AMPM, i);
            assertEquals(i, test.get(HOUR_OF_AMPM));
            assertEquals(TEST_12_30_40_987654321.get(AMPM_OF_DAY), test.get(AMPM_OF_DAY));
            assertEquals(TEST_12_30_40_987654321.get(MINUTE_OF_HOUR), test.get(MINUTE_OF_HOUR));
            assertEquals(TEST_12_30_40_987654321.get(SECOND_OF_MINUTE), test.get(SECOND_OF_MINUTE));
            assertEquals(TEST_12_30_40_987654321.get(NANO_OF_SECOND), test.get(NANO_OF_SECOND));
        }
    }

    // Returns a {@code LocalTime} with the specified clock-hour-of-am-pm.
    // The AM/PM, minute-of-hour, second-of-minute and nano-of-second will be unchanged.
    @Test
    public void test_with_longTemporalField_clockHourOfAmPm() {
        for (int i = 1; i <= 12; i++) {
            LocalTime test = TEST_12_30_40_987654321.with(CLOCK_HOUR_OF_AMPM, i);
            assertEquals(i, test.get(CLOCK_HOUR_OF_AMPM));
            assertEquals(TEST_12_30_40_987654321.get(AMPM_OF_DAY), test.get(AMPM_OF_DAY));
            assertEquals(TEST_12_30_40_987654321.get(MINUTE_OF_HOUR), test.get(MINUTE_OF_HOUR));
            assertEquals(TEST_12_30_40_987654321.get(SECOND_OF_MINUTE), test.get(SECOND_OF_MINUTE));
            assertEquals(TEST_12_30_40_987654321.get(NANO_OF_SECOND), test.get(NANO_OF_SECOND));
        }
    }

    // Returns a {@code LocalTime} with the specified hour-of-day.
    // The minute-of-hour, second-of-minute and nano-of-second will be unchanged.
    @Test
    public void test_with_longTemporalField_hourOfDay() {
        for (int i = 0; i < 24; i++) {
            LocalTime test = TEST_12_30_40_987654321.with(HOUR_OF_DAY, i);
            assertEquals(i, test.get(HOUR_OF_DAY));
            assertEquals(TEST_12_30_40_987654321.get(MINUTE_OF_HOUR), test.get(MINUTE_OF_HOUR));
            assertEquals(TEST_12_30_40_987654321.get(SECOND_OF_MINUTE), test.get(SECOND_OF_MINUTE));
            assertEquals(TEST_12_30_40_987654321.get(NANO_OF_SECOND), test.get(NANO_OF_SECOND));
        }
    }

    // Returns a {@code LocalTime} with the specified clock-hour-of-day.
    // The minute-of-hour, second-of-minute and nano-of-second will be unchanged.
    @Test
    public void test_with_longTemporalField_clockHourOfDay() {
        for (int i = 1; i <= 24; i++) {
            LocalTime test = TEST_12_30_40_987654321.with(CLOCK_HOUR_OF_DAY, i);
            assertEquals(i, test.get(CLOCK_HOUR_OF_DAY));
            assertEquals(TEST_12_30_40_987654321.get(MINUTE_OF_HOUR), test.get(MINUTE_OF_HOUR));
            assertEquals(TEST_12_30_40_987654321.get(SECOND_OF_MINUTE), test.get(SECOND_OF_MINUTE));
            assertEquals(TEST_12_30_40_987654321.get(NANO_OF_SECOND), test.get(NANO_OF_SECOND));
        }
    }

    // Returns a {@code LocalTime} with the specified AM/PM.
    // The hour-of-am-pm, minute-of-hour, second-of-minute and nano-of-second will be unchanged.
    @Test
    public void test_with_longTemporalField_amPmOfDay() {
        for (int i = 0; i <= 1; i++) {
            LocalTime test = TEST_12_30_40_987654321.with(AMPM_OF_DAY, i);
            assertEquals(i, test.get(AMPM_OF_DAY));
            assertEquals(TEST_12_30_40_987654321.get(HOUR_OF_AMPM), test.get(HOUR_OF_AMPM));
            assertEquals(TEST_12_30_40_987654321.get(MINUTE_OF_HOUR), test.get(MINUTE_OF_HOUR));
            assertEquals(TEST_12_30_40_987654321.get(SECOND_OF_MINUTE), test.get(SECOND_OF_MINUTE));
            assertEquals(TEST_12_30_40_987654321.get(NANO_OF_SECOND), test.get(NANO_OF_SECOND));
        }
    }

    // The supported fields behave as follows...
    // In all cases, if the new value is outside the valid range of values for the field
    // then a {@code DateTimeException} will be thrown.
    Object[][] data_withTemporalField_outOfRange() {
        return new Object[][] {
                {NANO_OF_SECOND, time(0, 0, 0, 0), NANO_OF_SECOND.range().getMinimum() - 1},
                {NANO_OF_SECOND, time(0, 0, 0, 0), NANO_OF_SECOND.range().getMaximum() + 1},

                {NANO_OF_DAY, time(0, 0, 0, 0), NANO_OF_DAY.range().getMinimum() - 1},
                {NANO_OF_DAY, time(0, 0, 0, 0), NANO_OF_DAY.range().getMaximum() + 1},

                {MICRO_OF_SECOND, time(0, 0, 0, 0), MICRO_OF_SECOND.range().getMinimum() - 1},
                {MICRO_OF_SECOND, time(0, 0, 0, 0), MICRO_OF_SECOND.range().getMaximum() + 1},

                {MICRO_OF_DAY, time(0, 0, 0, 0), MICRO_OF_DAY.range().getMinimum() - 1},
                {MICRO_OF_DAY, time(0, 0, 0, 0), MICRO_OF_DAY.range().getMaximum() + 1},

                {MILLI_OF_SECOND, time(0, 0, 0, 0), MILLI_OF_SECOND.range().getMinimum() - 1},
                {MILLI_OF_SECOND, time(0, 0, 0, 0), MILLI_OF_SECOND.range().getMaximum() + 1},

                {MILLI_OF_DAY, time(0, 0, 0, 0), MILLI_OF_DAY.range().getMinimum() - 1},
                {MILLI_OF_DAY, time(0, 0, 0, 0), MILLI_OF_DAY.range().getMaximum() + 1},

                {SECOND_OF_MINUTE, time(0, 0, 0, 0), SECOND_OF_MINUTE.range().getMinimum() - 1},
                {SECOND_OF_MINUTE, time(0, 0, 0, 0), SECOND_OF_MINUTE.range().getMaximum() + 1},

                {SECOND_OF_DAY, time(0, 0, 0, 0), SECOND_OF_DAY.range().getMinimum() - 1},
                {SECOND_OF_DAY, time(0, 0, 0, 0), SECOND_OF_DAY.range().getMaximum() + 1},

                {MINUTE_OF_HOUR, time(0, 0, 0, 0), MINUTE_OF_HOUR.range().getMinimum() - 1},
                {MINUTE_OF_HOUR, time(0, 0, 0, 0), MINUTE_OF_HOUR.range().getMaximum() + 1},

                {MINUTE_OF_DAY, time(0, 0, 0, 0), MINUTE_OF_DAY.range().getMinimum() - 1},
                {MINUTE_OF_DAY, time(0, 0, 0, 0), MINUTE_OF_DAY.range().getMaximum() + 1},

                {HOUR_OF_AMPM, time(0, 0, 0, 0), HOUR_OF_AMPM.range().getMinimum() - 1},
                {HOUR_OF_AMPM, time(0, 0, 0, 0), HOUR_OF_AMPM.range().getMaximum() + 1},

                {CLOCK_HOUR_OF_AMPM, time(0, 0, 0, 0), CLOCK_HOUR_OF_AMPM.range().getMinimum() - 1},
                {CLOCK_HOUR_OF_AMPM, time(0, 0, 0, 0), CLOCK_HOUR_OF_AMPM.range().getMaximum() + 1},

                {HOUR_OF_DAY, time(0, 0, 0, 0), HOUR_OF_DAY.range().getMinimum() - 1},
                {HOUR_OF_DAY, time(0, 0, 0, 0), HOUR_OF_DAY.range().getMaximum() + 1},

                {CLOCK_HOUR_OF_DAY, time(0, 0, 0, 0), CLOCK_HOUR_OF_DAY.range().getMinimum() - 1},
                {CLOCK_HOUR_OF_DAY, time(0, 0, 0, 0), CLOCK_HOUR_OF_DAY.range().getMaximum() + 1},

                {AMPM_OF_DAY, time(0, 0, 0, 0), AMPM_OF_DAY.range().getMinimum() - 1},
                {AMPM_OF_DAY, time(0, 0, 0, 0), AMPM_OF_DAY.range().getMaximum() + 1},
        };
    }

    @ParameterizedTest
    @MethodSource("data_withTemporalField_outOfRange")
    public void test_with_longTemporalField_invalid(TemporalField field, LocalTime base, long newValue) {
        try {
            base.with(field, newValue);
            fail("Field should not be allowed " + field);
        } catch (DateTimeException ex) {
            // expected
        }
    }

    // All other {@code ChronoField} instances will throw an {@code UnsupportedTemporalTypeException}.
    @Test
    public void test_with_longTemporalField_otherChronoField() {
        Assertions.assertThrows(UnsupportedTemporalTypeException.class, () -> TEST_12_30_40_987654321.with(ChronoField.DAY_OF_MONTH, 1));
    }

    // If the field is not a {@code ChronoField}, then the result of this method
    // is obtained by invoking {@code TemporalField.adjustInto(Temporal, long)}
    // passing {@code this} as the argument.
    @Test
    public void test_with_longTemporalField_notChronoField() {
        final LocalTime result = LocalTime.of(12, 30);
        final LocalTime base = LocalTime.of(15, 45);
        TemporalField field = new TemporalField() {
            public ValueRange rangeRefinedBy(TemporalAccessor temporal) {
                throw new UnsupportedOperationException();
            }
            public ValueRange range() {
                return null;
            }
            public boolean isTimeBased() {
                throw new UnsupportedOperationException();
            }
            public boolean isSupportedBy(TemporalAccessor temporal) {
                throw new UnsupportedOperationException();
            }
            public boolean isDateBased() {
                throw new UnsupportedOperationException();
            }
            public TemporalUnit getRangeUnit() {
                throw new UnsupportedOperationException();
            }
            public long getFrom(TemporalAccessor temporal) {
                throw new UnsupportedOperationException();
            }
            public TemporalUnit getBaseUnit() {
                throw new UnsupportedOperationException();
            }
            public <R extends Temporal> R adjustInto(R temporal, long newValue) {
                assertEquals(base, temporal);
                assertEquals(12L, newValue);
                @SuppressWarnings("unchecked")
                R r = (R) result;
                return r;
            }
        };
        LocalTime test = base.with(field, 12L);
        assertSame(test, result);
    }

    @Test
    public void test_with_longTemporalField_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12_30_40_987654321.with((TemporalField) null, 1));
    }

    //-----------------------------------------------------------------------
    // withHour()
    //-----------------------------------------------------------------------
    @Test
    public void test_withHour_normal() {
        LocalTime t = TEST_12_30_40_987654321;
        for (int i = 0; i < 24; i++) {
            t = t.withHour(i);
            assertEquals(i, t.getHour());
        }
    }

    @Test
    public void test_withHour_noChange_equal() {
        LocalTime t = TEST_12_30_40_987654321.withHour(12);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_withHour_toMidnight_equal() {
        LocalTime t = LocalTime.of(1, 0).withHour(0);
        assertEquals(LocalTime.MIDNIGHT, t);
    }

    @Test
    public void test_withHour_toMidday_equal() {
        LocalTime t = LocalTime.of(1, 0).withHour(12);
        assertEquals(LocalTime.NOON, t);
    }

    @Test
    public void test_withHour_hourTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_12_30_40_987654321.withHour(-1));
    }

    @Test
    public void test_withHour_hourTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_12_30_40_987654321.withHour(24));
    }

    //-----------------------------------------------------------------------
    // withMinute()
    //-----------------------------------------------------------------------
    @Test
    public void test_withMinute_normal() {
        LocalTime t = TEST_12_30_40_987654321;
        for (int i = 0; i < 60; i++) {
            t = t.withMinute(i);
            assertEquals(i, t.getMinute());
        }
    }

    @Test
    public void test_withMinute_noChange_equal() {
        LocalTime t = TEST_12_30_40_987654321.withMinute(30);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_withMinute_toMidnight_equal() {
        LocalTime t = LocalTime.of(0, 1).withMinute(0);
        assertEquals(LocalTime.MIDNIGHT, t);
    }

    @Test
    public void test_withMinute_toMidday_equals() {
        LocalTime t = LocalTime.of(12, 1).withMinute(0);
        assertEquals(LocalTime.NOON, t);
    }

    @Test
    public void test_withMinute_minuteTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_12_30_40_987654321.withMinute(-1));
    }

    @Test
    public void test_withMinute_minuteTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_12_30_40_987654321.withMinute(60));
    }

    //-----------------------------------------------------------------------
    // withSecond()
    //-----------------------------------------------------------------------
    @Test
    public void test_withSecond_normal() {
        LocalTime t = TEST_12_30_40_987654321;
        for (int i = 0; i < 60; i++) {
            t = t.withSecond(i);
            assertEquals(i, t.getSecond());
        }
    }

    @Test
    public void test_withSecond_noChange_equal() {
        LocalTime t = TEST_12_30_40_987654321.withSecond(40);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_withSecond_toMidnight_equal() {
        LocalTime t = LocalTime.of(0, 0, 1).withSecond(0);
        assertEquals(LocalTime.MIDNIGHT, t);
    }

    @Test
    public void test_withSecond_toMidday_equal() {
        LocalTime t = LocalTime.of(12, 0, 1).withSecond(0);
        assertEquals(LocalTime.NOON, t);
    }

    @Test
    public void test_withSecond_secondTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_12_30_40_987654321.withSecond(-1));
    }

    @Test
    public void test_withSecond_secondTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_12_30_40_987654321.withSecond(60));
    }

    //-----------------------------------------------------------------------
    // withNano()
    //-----------------------------------------------------------------------
    @Test
    public void test_withNanoOfSecond_normal() {
        LocalTime t = TEST_12_30_40_987654321;
        t = t.withNano(1);
        assertEquals(1, t.getNano());
        t = t.withNano(10);
        assertEquals(10, t.getNano());
        t = t.withNano(100);
        assertEquals(100, t.getNano());
        t = t.withNano(999999999);
        assertEquals(999999999, t.getNano());
    }

    @Test
    public void test_withNanoOfSecond_noChange_equal() {
        LocalTime t = TEST_12_30_40_987654321.withNano(987654321);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_withNanoOfSecond_toMidnight_equal() {
        LocalTime t = LocalTime.of(0, 0, 0, 1).withNano(0);
        assertEquals(LocalTime.MIDNIGHT, t);
    }

    @Test
    public void test_withNanoOfSecond_toMidday_equal() {
        LocalTime t = LocalTime.of(12, 0, 0, 1).withNano(0);
        assertEquals(LocalTime.NOON, t);
    }

    @Test
    public void test_withNanoOfSecond_nanoTooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_12_30_40_987654321.withNano(-1));
    }

    @Test
    public void test_withNanoOfSecond_nanoTooHigh() {
        Assertions.assertThrows(DateTimeException.class, () -> TEST_12_30_40_987654321.withNano(1000000000));
    }

    //-----------------------------------------------------------------------
    // truncated(TemporalUnit)
    //-----------------------------------------------------------------------
    TemporalUnit NINETY_MINS = new TemporalUnit() {
        @Override
        public Duration getDuration() {
            return Duration.ofMinutes(90);
        }
        @Override
        public boolean isDurationEstimated() {
            return false;
        }
        @Override
        public boolean isDateBased() {
            return false;
        }
        @Override
        public boolean isTimeBased() {
            return true;
        }
        @Override
        public boolean isSupportedBy(Temporal temporal) {
            return false;
        }
        @Override
        public <R extends Temporal> R addTo(R temporal, long amount) {
            throw new UnsupportedOperationException();
        }
        @Override
        public long between(Temporal temporal1, Temporal temporal2) {
            throw new UnsupportedOperationException();
        }
        @Override
        public String toString() {
            return "NinetyMins";
        }
    };

    TemporalUnit NINETY_FIVE_MINS = new TemporalUnit() {
        @Override
        public Duration getDuration() {
            return Duration.ofMinutes(95);
        }
        @Override
        public boolean isDurationEstimated() {
            return false;
        }
        @Override
        public boolean isDateBased() {
            return false;
        }
        @Override
        public boolean isTimeBased() {
            return false;
        }
        @Override
        public boolean isSupportedBy(Temporal temporal) {
            return false;
        }
        @Override
        public <R extends Temporal> R addTo(R temporal, long amount) {
            throw new UnsupportedOperationException();
        }
        @Override
        public long between(Temporal temporal1, Temporal temporal2) {
            throw new UnsupportedOperationException();
        }
        @Override
        public String toString() {
            return "NinetyFiveMins";
        }
    };

    Object[][] data_truncatedToValid() {
        return new Object[][] {
            {LocalTime.of(1, 2, 3, 123_456_789), NANOS, LocalTime.of(1, 2, 3, 123_456_789)},
            {LocalTime.of(1, 2, 3, 123_456_789), MICROS, LocalTime.of(1, 2, 3, 123_456_000)},
            {LocalTime.of(1, 2, 3, 123_456_789), MILLIS, LocalTime.of(1, 2, 3, 1230_00_000)},
            {LocalTime.of(1, 2, 3, 123_456_789), SECONDS, LocalTime.of(1, 2, 3)},
            {LocalTime.of(1, 2, 3, 123_456_789), MINUTES, LocalTime.of(1, 2)},
            {LocalTime.of(1, 2, 3, 123_456_789), HOURS, LocalTime.of(1, 0)},
            {LocalTime.of(1, 2, 3, 123_456_789), DAYS, LocalTime.MIDNIGHT},

            {LocalTime.of(1, 1, 1, 123_456_789), NINETY_MINS, LocalTime.of(0, 0)},
            {LocalTime.of(2, 1, 1, 123_456_789), NINETY_MINS, LocalTime.of(1, 30)},
            {LocalTime.of(3, 1, 1, 123_456_789), NINETY_MINS, LocalTime.of(3, 0)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_truncatedToValid")
    public void test_truncatedTo_valid(LocalTime input, TemporalUnit unit, LocalTime expected) {
        assertEquals(expected, input.truncatedTo(unit));
    }

    Object[][] data_truncatedToInvalid() {
        return new Object[][] {
            {LocalTime.of(1, 2, 3, 123_456_789), NINETY_FIVE_MINS},
            {LocalTime.of(1, 2, 3, 123_456_789), WEEKS},
            {LocalTime.of(1, 2, 3, 123_456_789), MONTHS},
            {LocalTime.of(1, 2, 3, 123_456_789), YEARS},
        };
    }

    @ParameterizedTest
    @MethodSource("data_truncatedToInvalid")
    public void test_truncatedTo_invalid(LocalTime input, TemporalUnit unit) {
        Assertions.assertThrows(DateTimeException.class, () -> input.truncatedTo(unit));
    }

    @Test
    public void test_truncatedTo_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12_30_40_987654321.truncatedTo(null));
    }

    //-----------------------------------------------------------------------
    // plus(TemporalAmount)
    //-----------------------------------------------------------------------
    @Test
    public void test_plus_TemporalAmount_positiveHours() {
        TemporalAmount period = MockSimplePeriod.of(7, ChronoUnit.HOURS);
        LocalTime t = TEST_12_30_40_987654321.plus(period);
        assertEquals(LocalTime.of(19, 30, 40, 987654321), t);
    }

    @Test
    public void test_plus_TemporalAmount_negativeMinutes() {
        TemporalAmount period = MockSimplePeriod.of(-25, ChronoUnit.MINUTES);
        LocalTime t = TEST_12_30_40_987654321.plus(period);
        assertEquals(LocalTime.of(12, 5, 40, 987654321), t);
    }

    @Test
    public void test_plus_TemporalAmount_zero() {
        TemporalAmount period = Period.ZERO;
        LocalTime t = TEST_12_30_40_987654321.plus(period);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_plus_TemporalAmount_wrap() {
        TemporalAmount p = MockSimplePeriod.of(1, HOURS);
        LocalTime t = LocalTime.of(23, 30).plus(p);
        assertEquals(LocalTime.of(0, 30), t);
    }

    @Test
    public void test_plus_TemporalAmount_dateNotAllowed() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            TemporalAmount period = MockSimplePeriod.of(7, ChronoUnit.MONTHS);
            TEST_12_30_40_987654321.plus(period);
        });
    }

    @Test
    public void test_plus_TemporalAmount_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12_30_40_987654321.plus((TemporalAmount) null));
    }

    //-----------------------------------------------------------------------
    // plus(long,TemporalUnit)
    //-----------------------------------------------------------------------
    @Test
    public void test_plus_longTemporalUnit_positiveHours() {
        LocalTime t = TEST_12_30_40_987654321.plus(7, ChronoUnit.HOURS);
        assertEquals(LocalTime.of(19, 30, 40, 987654321), t);
    }

    @Test
    public void test_plus_longTemporalUnit_negativeMinutes() {
        LocalTime t = TEST_12_30_40_987654321.plus(-25, ChronoUnit.MINUTES);
        assertEquals(LocalTime.of(12, 5, 40, 987654321), t);
    }

    @Test
    public void test_plus_longTemporalUnit_zero() {
        LocalTime t = TEST_12_30_40_987654321.plus(0, ChronoUnit.MINUTES);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_plus_longTemporalUnit_invalidUnit() {
        for (TemporalUnit unit : INVALID_UNITS) {
            try {
                TEST_12_30_40_987654321.plus(1, unit);
                fail("Unit should not be allowed " + unit);
            } catch (DateTimeException ex) {
                // expected
            }
        }
    }

    @Test
    public void test_plus_longTemporalUnit_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12_30_40_987654321.plus(1, (TemporalUnit) null));
    }

    //-----------------------------------------------------------------------
    // plusHours()
    //-----------------------------------------------------------------------
    @Test
    public void test_plusHours_one() {
        LocalTime t = LocalTime.MIDNIGHT;
        for (int i = 0; i < 50; i++) {
            t = t.plusHours(1);
            assertEquals((i + 1) % 24, t.getHour());
        }
    }

    @Test
    public void test_plusHours_fromZero() {
        LocalTime base = LocalTime.MIDNIGHT;
        for (int i = -50; i < 50; i++) {
            LocalTime t = base.plusHours(i);
            assertEquals((i + 72) % 24, t.getHour());
        }
    }

    @Test
    public void test_plusHours_fromOne() {
        LocalTime base = LocalTime.of(1, 0);
        for (int i = -50; i < 50; i++) {
            LocalTime t = base.plusHours(i);
            assertEquals((1 + i + 72) % 24, t.getHour());
        }
    }

    @Test
    public void test_plusHours_noChange_equal() {
        LocalTime t = TEST_12_30_40_987654321.plusHours(0);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_plusHours_toMidnight_equal() {
        LocalTime t = LocalTime.of(23, 0).plusHours(1);
        assertEquals(LocalTime.MIDNIGHT, t);
    }

    @Test
    public void test_plusHours_toMidday_equal() {
        LocalTime t = LocalTime.of(11, 0).plusHours(1);
        assertEquals(LocalTime.NOON, t);
    }

    @Test
    public void test_plusHours_big() {
        LocalTime t = LocalTime.of(2, 30).plusHours(Long.MAX_VALUE);
        int hours = (int) (Long.MAX_VALUE % 24L);
        assertEquals(LocalTime.of(2, 30).plusHours(hours), t);
    }

    //-----------------------------------------------------------------------
    // plusMinutes()
    //-----------------------------------------------------------------------
    @Test
    public void test_plusMinutes_one() {
        LocalTime t = LocalTime.MIDNIGHT;
        int hour = 0;
        int min = 0;
        for (int i = 0; i < 70; i++) {
            t = t.plusMinutes(1);
            min++;
            if (min == 60) {
                hour++;
                min = 0;
            }
            assertEquals(hour, t.getHour());
            assertEquals(min, t.getMinute());
        }
    }

    @Test
    public void test_plusMinutes_fromZero() {
        LocalTime base = LocalTime.MIDNIGHT;
        int hour;
        int min;
        for (int i = -70; i < 70; i++) {
            LocalTime t = base.plusMinutes(i);
            if (i < -60) {
                hour = 22;
                min = i + 120;
            } else if (i < 0) {
                hour = 23;
                min = i + 60;
            } else if (i >= 60) {
                hour = 1;
                min = i - 60;
            } else {
                hour = 0;
                min = i;
            }
            assertEquals(hour, t.getHour());
            assertEquals(min, t.getMinute());
        }
    }

    @Test
    public void test_plusMinutes_noChange_equal() {
        LocalTime t = TEST_12_30_40_987654321.plusMinutes(0);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_plusMinutes_noChange_oneDay_equal() {
        LocalTime t = TEST_12_30_40_987654321.plusMinutes(24 * 60);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_plusMinutes_toMidnight_equal() {
        LocalTime t = LocalTime.of(23, 59).plusMinutes(1);
        assertEquals(LocalTime.MIDNIGHT, t);
    }

    @Test
    public void test_plusMinutes_toMidday_equal() {
        LocalTime t = LocalTime.of(11, 59).plusMinutes(1);
        assertEquals(LocalTime.NOON, t);
    }

    @Test
    public void test_plusMinutes_big() {
        LocalTime t = LocalTime.of(2, 30).plusMinutes(Long.MAX_VALUE);
        int mins = (int) (Long.MAX_VALUE % (24L * 60L));
        assertEquals(LocalTime.of(2, 30).plusMinutes(mins), t);
    }

    //-----------------------------------------------------------------------
    // plusSeconds()
    //-----------------------------------------------------------------------
    @Test
    public void test_plusSeconds_one() {
        LocalTime t = LocalTime.MIDNIGHT;
        int hour = 0;
        int min = 0;
        int sec = 0;
        for (int i = 0; i < 3700; i++) {
            t = t.plusSeconds(1);
            sec++;
            if (sec == 60) {
                min++;
                sec = 0;
            }
            if (min == 60) {
                hour++;
                min = 0;
            }
            assertEquals(hour, t.getHour());
            assertEquals(min, t.getMinute());
            assertEquals(sec, t.getSecond());
        }
    }

    Iterator<Object[]> plusSeconds_fromZero() {
        return new Iterator<Object[]>() {
            int delta = 30;
            int i = -3660;
            int hour = 22;
            int min = 59;
            int sec = 0;

            public boolean hasNext() {
                return i <= 3660;
            }

            public Object[] next() {
                final Object[] ret = new Object[] {i, hour, min, sec};
                i += delta;
                sec += delta;

                if (sec >= 60) {
                    min++;
                    sec -= 60;

                    if (min == 60) {
                        hour++;
                        min = 0;

                        if (hour == 24) {
                            hour = 0;
                        }
                    }
                }

                return ret;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @ParameterizedTest
    @MethodSource("plusSeconds_fromZero")
    public void test_plusSeconds_fromZero(int seconds, int hour, int min, int sec) {
        LocalTime base = LocalTime.MIDNIGHT;
        LocalTime t = base.plusSeconds(seconds);

        assertEquals(t.getHour(), hour);
        assertEquals(t.getMinute(), min);
        assertEquals(t.getSecond(), sec);
    }

    @Test
    public void test_plusSeconds_noChange_equal() {
        LocalTime t = TEST_12_30_40_987654321.plusSeconds(0);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_plusSeconds_noChange_oneDay_equal() {
        LocalTime t = TEST_12_30_40_987654321.plusSeconds(24 * 60 * 60);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_plusSeconds_toMidnight_equal() {
        LocalTime t = LocalTime.of(23, 59, 59).plusSeconds(1);
        assertEquals(LocalTime.MIDNIGHT, t);
    }

    @Test
    public void test_plusSeconds_toMidday_equal() {
        LocalTime t = LocalTime.of(11, 59, 59).plusSeconds(1);
        assertEquals(LocalTime.NOON, t);
    }

    //-----------------------------------------------------------------------
    // plusNanos()
    //-----------------------------------------------------------------------
    @Test
    public void test_plusNanos_halfABillion() {
        LocalTime t = LocalTime.MIDNIGHT;
        int hour = 0;
        int min = 0;
        int sec = 0;
        int nanos = 0;
        for (long i = 0; i < 3700 * 1000000000L; i+= 500000000) {
            t = t.plusNanos(500000000);
            nanos += 500000000;
            if (nanos == 1000000000) {
                sec++;
                nanos = 0;
            }
            if (sec == 60) {
                min++;
                sec = 0;
            }
            if (min == 60) {
                hour++;
                min = 0;
            }
            assertEquals(hour, t.getHour());
            assertEquals(min, t.getMinute());
            assertEquals(sec, t.getSecond());
            assertEquals(nanos, t.getNano());
        }
    }

    Iterator<Object[]> plusNanos_fromZero() {
        return new Iterator<Object[]>() {
            long delta = 7500000000L;
            long i = -3660 * 1000000000L;
            int hour = 22;
            int min = 59;
            int sec = 0;
            long nanos = 0;

            public boolean hasNext() {
                return i <= 3660 * 1000000000L;
            }

            public Object[] next() {
                final Object[] ret = new Object[] {i, hour, min, sec, (int)nanos};
                i += delta;
                nanos += delta;

                if (nanos >= 1000000000L) {
                    sec += nanos / 1000000000L;
                    nanos %= 1000000000L;

                    if (sec >= 60) {
                        min++;
                        sec %= 60;

                        if (min == 60) {
                            hour++;
                            min = 0;

                            if (hour == 24) {
                                hour = 0;
                            }
                        }
                    }
                }

                return ret;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @ParameterizedTest
    @MethodSource("plusNanos_fromZero")
    public void test_plusNanos_fromZero(long nanoseconds, int hour, int min, int sec, int nanos) {
        LocalTime base = LocalTime.MIDNIGHT;
        LocalTime t = base.plusNanos(nanoseconds);

        assertEquals(t.getHour(), hour);
        assertEquals(t.getMinute(), min);
        assertEquals(t.getSecond(), sec);
        assertEquals(t.getNano(), nanos);
    }

    @Test
    public void test_plusNanos_noChange_equal() {
        LocalTime t = TEST_12_30_40_987654321.plusNanos(0);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_plusNanos_noChange_oneDay_equal() {
        LocalTime t = TEST_12_30_40_987654321.plusNanos(24 * 60 * 60 * 1000000000L);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_plusNanos_toMidnight_equal() {
        LocalTime t = LocalTime.of(23, 59, 59, 999999999).plusNanos(1);
        assertEquals(LocalTime.MIDNIGHT, t);
    }

    @Test
    public void test_plusNanos_toMidday_equal() {
        LocalTime t = LocalTime.of(11, 59, 59, 999999999).plusNanos(1);
        assertEquals(LocalTime.NOON, t);
    }

    //-----------------------------------------------------------------------
    // minus(TemporalAmount)
    //-----------------------------------------------------------------------
    @Test
    public void test_minus_TemporalAmount_positiveHours() {
        TemporalAmount period = MockSimplePeriod.of(7, ChronoUnit.HOURS);
        LocalTime t = TEST_12_30_40_987654321.minus(period);
        assertEquals(LocalTime.of(5, 30, 40, 987654321), t);
    }

    @Test
    public void test_minus_TemporalAmount_negativeMinutes() {
        TemporalAmount period = MockSimplePeriod.of(-25, ChronoUnit.MINUTES);
        LocalTime t = TEST_12_30_40_987654321.minus(period);
        assertEquals(LocalTime.of(12, 55, 40, 987654321), t);
    }

    @Test
    public void test_minus_TemporalAmount_zero() {
        TemporalAmount period = Period.ZERO;
        LocalTime t = TEST_12_30_40_987654321.minus(period);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_minus_TemporalAmount_wrap() {
        TemporalAmount p = MockSimplePeriod.of(1, HOURS);
        LocalTime t = LocalTime.of(0, 30).minus(p);
        assertEquals(LocalTime.of(23, 30), t);
    }

    @Test
    public void test_minus_TemporalAmount_dateNotAllowed() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            TemporalAmount period = MockSimplePeriod.of(7, ChronoUnit.MONTHS);
            TEST_12_30_40_987654321.minus(period);
        });
    }

    @Test
    public void test_minus_TemporalAmount_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12_30_40_987654321.minus((TemporalAmount) null));
    }

    //-----------------------------------------------------------------------
    // minus(long,TemporalUnit)
    //-----------------------------------------------------------------------
    @Test
    public void test_minus_longTemporalUnit_positiveHours() {
        LocalTime t = TEST_12_30_40_987654321.minus(7, ChronoUnit.HOURS);
        assertEquals(LocalTime.of(5, 30, 40, 987654321), t);
    }

    @Test
    public void test_minus_longTemporalUnit_negativeMinutes() {
        LocalTime t = TEST_12_30_40_987654321.minus(-25, ChronoUnit.MINUTES);
        assertEquals(LocalTime.of(12, 55, 40, 987654321), t);
    }

    @Test
    public void test_minus_longTemporalUnit_zero() {
        LocalTime t = TEST_12_30_40_987654321.minus(0, ChronoUnit.MINUTES);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_minus_longTemporalUnit_invalidUnit() {
        for (TemporalUnit unit : INVALID_UNITS) {
            try {
                TEST_12_30_40_987654321.minus(1, unit);
                fail("Unit should not be allowed " + unit);
            } catch (DateTimeException ex) {
                // expected
            }
        }
    }

    @Test
    public void test_minus_longTemporalUnit_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12_30_40_987654321.minus(1, (TemporalUnit) null));
    }

    //-----------------------------------------------------------------------
    // minusHours()
    //-----------------------------------------------------------------------
    @Test
    public void test_minusHours_one() {
        LocalTime t = LocalTime.MIDNIGHT;
        for (int i = 0; i < 50; i++) {
            t = t.minusHours(1);
            assertEquals((((-i + 23) % 24) + 24) % 24, t.getHour(), String.valueOf(i));
        }
    }

    @Test
    public void test_minusHours_fromZero() {
        LocalTime base = LocalTime.MIDNIGHT;
        for (int i = -50; i < 50; i++) {
            LocalTime t = base.minusHours(i);
            assertEquals(((-i % 24) + 24) % 24, t.getHour());
        }
    }

    @Test
    public void test_minusHours_fromOne() {
        LocalTime base = LocalTime.of(1, 0);
        for (int i = -50; i < 50; i++) {
            LocalTime t = base.minusHours(i);
            assertEquals((1 + (-i % 24) + 24) % 24, t.getHour());
        }
    }

    @Test
    public void test_minusHours_noChange_equal() {
        LocalTime t = TEST_12_30_40_987654321.minusHours(0);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_minusHours_toMidnight_equal() {
        LocalTime t = LocalTime.of(1, 0).minusHours(1);
        assertEquals(LocalTime.MIDNIGHT, t);
    }

    @Test
    public void test_minusHours_toMidday_equal() {
        LocalTime t = LocalTime.of(13, 0).minusHours(1);
        assertEquals(LocalTime.NOON, t);
    }

    @Test
    public void test_minusHours_big() {
        LocalTime t = LocalTime.of(2, 30).minusHours(Long.MAX_VALUE);
        int hours = (int) (Long.MAX_VALUE % 24L);
        assertEquals(LocalTime.of(2, 30).minusHours(hours), t);
    }

    //-----------------------------------------------------------------------
    // minusMinutes()
    //-----------------------------------------------------------------------
    @Test
    public void test_minusMinutes_one() {
        LocalTime t = LocalTime.MIDNIGHT;
        int hour = 0;
        int min = 0;
        for (int i = 0; i < 70; i++) {
            t = t.minusMinutes(1);
            min--;
            if (min == -1) {
                hour--;
                min = 59;

                if (hour == -1) {
                    hour = 23;
                }
            }
            assertEquals(hour, t.getHour());
            assertEquals(min, t.getMinute());
        }
    }

    @Test
    public void test_minusMinutes_fromZero() {
        LocalTime base = LocalTime.MIDNIGHT;
        int hour = 22;
        int min = 49;
        for (int i = 70; i > -70; i--) {
            LocalTime t = base.minusMinutes(i);
            min++;

            if (min == 60) {
                hour++;
                min = 0;

                if (hour == 24) {
                    hour = 0;
                }
            }

            assertEquals(hour, t.getHour());
            assertEquals(min, t.getMinute());
        }
    }

    @Test
    public void test_minusMinutes_noChange_equal() {
        LocalTime t = TEST_12_30_40_987654321.minusMinutes(0);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_minusMinutes_noChange_oneDay_equal() {
        LocalTime t = TEST_12_30_40_987654321.minusMinutes(24 * 60);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_minusMinutes_toMidnight_equal() {
        LocalTime t = LocalTime.of(0, 1).minusMinutes(1);
        assertEquals(LocalTime.MIDNIGHT, t);
    }

    @Test
    public void test_minusMinutes_toMidday_equals() {
        LocalTime t = LocalTime.of(12, 1).minusMinutes(1);
        assertEquals(LocalTime.NOON, t);
    }

    @Test
    public void test_minusMinutes_big() {
        LocalTime t = LocalTime.of(2, 30).minusMinutes(Long.MAX_VALUE);
        int mins = (int) (Long.MAX_VALUE % (24L * 60L));
        assertEquals(LocalTime.of(2, 30).minusMinutes(mins), t);
    }

    //-----------------------------------------------------------------------
    // minusSeconds()
    //-----------------------------------------------------------------------
    @Test
    public void test_minusSeconds_one() {
        LocalTime t = LocalTime.MIDNIGHT;
        int hour = 0;
        int min = 0;
        int sec = 0;
        for (int i = 0; i < 3700; i++) {
            t = t.minusSeconds(1);
            sec--;
            if (sec == -1) {
                min--;
                sec = 59;

                if (min == -1) {
                    hour--;
                    min = 59;

                    if (hour == -1) {
                        hour = 23;
                    }
                }
            }
            assertEquals(hour, t.getHour());
            assertEquals(min, t.getMinute());
            assertEquals(sec, t.getSecond());
        }
    }

    Iterator<Object[]> minusSeconds_fromZero() {
        return new Iterator<Object[]>() {
            int delta = 30;
            int i = 3660;
            int hour = 22;
            int min = 59;
            int sec = 0;

            public boolean hasNext() {
                return i >= -3660;
            }

            public Object[] next() {
                final Object[] ret = new Object[] {i, hour, min, sec};
                i -= delta;
                sec += delta;

                if (sec >= 60) {
                    min++;
                    sec -= 60;

                    if (min == 60) {
                        hour++;
                        min = 0;

                        if (hour == 24) {
                            hour = 0;
                        }
                    }
                }

                return ret;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @ParameterizedTest
    @MethodSource("minusSeconds_fromZero")
    public void test_minusSeconds_fromZero(int seconds, int hour, int min, int sec) {
        LocalTime base = LocalTime.MIDNIGHT;
        LocalTime t = base.minusSeconds(seconds);

        assertEquals(hour, t.getHour());
        assertEquals(min, t.getMinute());
        assertEquals(sec, t.getSecond());
    }

    @Test
    public void test_minusSeconds_noChange_equal() {
        LocalTime t = TEST_12_30_40_987654321.minusSeconds(0);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_minusSeconds_noChange_oneDay_equal() {
        LocalTime t = TEST_12_30_40_987654321.minusSeconds(24 * 60 * 60);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_minusSeconds_toMidnight_equal() {
        LocalTime t = LocalTime.of(0, 0, 1).minusSeconds(1);
        assertEquals(LocalTime.MIDNIGHT, t);
    }

    @Test
    public void test_minusSeconds_toMidday_equal() {
        LocalTime t = LocalTime.of(12, 0, 1).minusSeconds(1);
        assertEquals(LocalTime.NOON, t);
    }

    @Test
    public void test_minusSeconds_big() {
        LocalTime t = LocalTime.of(2, 30).minusSeconds(Long.MAX_VALUE);
        int secs = (int) (Long.MAX_VALUE % (24L * 60L * 60L));
        assertEquals(LocalTime.of(2, 30).minusSeconds(secs), t);
    }

    //-----------------------------------------------------------------------
    // minusNanos()
    //-----------------------------------------------------------------------
    @Test
    public void test_minusNanos_halfABillion() {
        LocalTime t = LocalTime.MIDNIGHT;
        int hour = 0;
        int min = 0;
        int sec = 0;
        int nanos = 0;
        for (long i = 0; i < 3700 * 1000000000L; i+= 500000000) {
            t = t.minusNanos(500000000);
            nanos -= 500000000;

            if (nanos < 0) {
                sec--;
                nanos += 1000000000;

                if (sec == -1) {
                    min--;
                    sec += 60;

                    if (min == -1) {
                        hour--;
                        min += 60;

                        if (hour == -1) {
                            hour += 24;
                        }
                    }
                }
            }

            assertEquals(hour, t.getHour());
            assertEquals(min, t.getMinute());
            assertEquals(sec, t.getSecond());
            assertEquals(nanos, t.getNano());
        }
    }

    Iterator<Object[]> minusNanos_fromZero() {
        return new Iterator<Object[]>() {
            long delta = 7500000000L;
            long i = 3660 * 1000000000L;
            int hour = 22;
            int min = 59;
            int sec = 0;
            long nanos = 0;

            public boolean hasNext() {
                return i >= -3660 * 1000000000L;
            }

            public Object[] next() {
                final Object[] ret = new Object[] {i, hour, min, sec, (int)nanos};
                i -= delta;
                nanos += delta;

                if (nanos >= 1000000000L) {
                    sec += nanos / 1000000000L;
                    nanos %= 1000000000L;

                    if (sec >= 60) {
                        min++;
                        sec %= 60;

                        if (min == 60) {
                            hour++;
                            min = 0;

                            if (hour == 24) {
                                hour = 0;
                            }
                        }
                    }
                }

                return ret;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @ParameterizedTest
    @MethodSource("minusNanos_fromZero")
    public void test_minusNanos_fromZero(long nanoseconds, int hour, int min, int sec, int nanos) {
        LocalTime base = LocalTime.MIDNIGHT;
        LocalTime t = base.minusNanos(nanoseconds);

        assertEquals(t.getHour(), hour);
        assertEquals(t.getMinute(), min);
        assertEquals(t.getSecond(), sec);
        assertEquals(t.getNano(), nanos);
    }

    @Test
    public void test_minusNanos_noChange_equal() {
        LocalTime t = TEST_12_30_40_987654321.minusNanos(0);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_minusNanos_noChange_oneDay_equal() {
        LocalTime t = TEST_12_30_40_987654321.minusNanos(24 * 60 * 60 * 1000000000L);
        assertEquals(TEST_12_30_40_987654321, t);
    }

    @Test
    public void test_minusNanos_toMidnight_equal() {
        LocalTime t = LocalTime.of(0, 0, 0, 1).minusNanos(1);
        assertEquals(LocalTime.MIDNIGHT, t);
    }

    @Test
    public void test_minusNanos_toMidday_equal() {
        LocalTime t = LocalTime.of(12, 0, 0, 1).minusNanos(1);
        assertEquals(LocalTime.NOON, t);
    }

    //-----------------------------------------------------------------------
    // until(Temporal, TemporalUnit)
    //-----------------------------------------------------------------------
    Object[][] data_periodUntilUnit() {
        return new Object[][] {
                {time(0, 0, 0, 0), time(0, 0, 0, 0), NANOS, 0},
                {time(0, 0, 0, 0), time(0, 0, 0, 0), MICROS, 0},
                {time(0, 0, 0, 0), time(0, 0, 0, 0), MILLIS, 0},
                {time(0, 0, 0, 0), time(0, 0, 0, 0), SECONDS, 0},
                {time(0, 0, 0, 0), time(0, 0, 0, 0), MINUTES, 0},
                {time(0, 0, 0, 0), time(0, 0, 0, 0), HOURS, 0},
                {time(0, 0, 0, 0), time(0, 0, 0, 0), HALF_DAYS, 0},

                {time(0, 0, 0, 0), time(2, 0, 0, 0), NANOS, 2 * 3600 * 1_000_000_000L},
                {time(0, 0, 0, 0), time(2, 0, 0, 0), MICROS, 2 * 3600 * 1_000_000L},
                {time(0, 0, 0, 0), time(2, 0, 0, 0), MILLIS, 2 * 3600 * 1_000L},
                {time(0, 0, 0, 0), time(2, 0, 0, 0), SECONDS, 2 * 3600},
                {time(0, 0, 0, 0), time(2, 0, 0, 0), MINUTES, 2 * 60},
                {time(0, 0, 0, 0), time(2, 0, 0, 0), HOURS, 2},
                {time(0, 0, 0, 0), time(2, 0, 0, 0), HALF_DAYS, 0},

                {time(0, 0, 0, 0), time(14, 0, 0, 0), NANOS, 14 * 3600 * 1_000_000_000L},
                {time(0, 0, 0, 0), time(14, 0, 0, 0), MICROS, 14 * 3600 * 1_000_000L},
                {time(0, 0, 0, 0), time(14, 0, 0, 0), MILLIS, 14 * 3600 * 1_000L},
                {time(0, 0, 0, 0), time(14, 0, 0, 0), SECONDS, 14 * 3600},
                {time(0, 0, 0, 0), time(14, 0, 0, 0), MINUTES, 14 * 60},
                {time(0, 0, 0, 0), time(14, 0, 0, 0), HOURS, 14},
                {time(0, 0, 0, 0), time(14, 0, 0, 0), HALF_DAYS, 1},

                {time(0, 0, 0, 0), time(2, 30, 40, 1500), NANOS, (2 * 3600 + 30 * 60 + 40) * 1_000_000_000L + 1500},
                {time(0, 0, 0, 0), time(2, 30, 40, 1500), MICROS, (2 * 3600 + 30 * 60 + 40) * 1_000_000L + 1},
                {time(0, 0, 0, 0), time(2, 30, 40, 1500), MILLIS, (2 * 3600 + 30 * 60 + 40) * 1_000L},
                {time(0, 0, 0, 0), time(2, 30, 40, 1500), SECONDS, 2 * 3600 + 30 * 60 + 40},
                {time(0, 0, 0, 0), time(2, 30, 40, 1500), MINUTES, 2 * 60 + 30},
                {time(0, 0, 0, 0), time(2, 30, 40, 1500), HOURS, 2},
        };
    }

    @ParameterizedTest
    @MethodSource("data_periodUntilUnit")
    public void test_until_TemporalUnit(LocalTime time1, LocalTime time2, TemporalUnit unit, long expected) {
        long amount = time1.until(time2, unit);
        assertEquals(expected, amount);
    }

    @ParameterizedTest
    @MethodSource("data_periodUntilUnit")
    public void test_until_TemporalUnit_negated(LocalTime time1, LocalTime time2, TemporalUnit unit, long expected) {
        long amount = time2.until(time1, unit);
        assertEquals(-expected, amount);
    }

    @ParameterizedTest
    @MethodSource("data_periodUntilUnit")
    public void test_until_TemporalUnit_between(LocalTime time1, LocalTime time2, TemporalUnit unit, long expected) {
        long amount = unit.between(time1, time2);
        assertEquals(expected, amount);
    }

    @Test
    public void test_until_convertedType() {
        LocalTime start = LocalTime.of(11, 30);
        LocalDateTime end = start.plusSeconds(2).atDate(LocalDate.of(2010, 6, 30));
        assertEquals(2, start.until(end, SECONDS));
    }

    @Test
    public void test_until_invalidType() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            LocalTime start = LocalTime.of(11, 30);
            start.until(LocalDate.of(2010, 6, 30), SECONDS);
        });
    }

    @Test
    public void test_until_TemporalUnit_unsupportedUnit() {
        Assertions.assertThrows(UnsupportedTemporalTypeException.class, () -> TEST_12_30_40_987654321.until(TEST_12_30_40_987654321, DAYS));
    }

    @Test
    public void test_until_TemporalUnit_nullEnd() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12_30_40_987654321.until(null, HOURS));
    }

    @Test
    public void test_until_TemporalUnit_nullUnit() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12_30_40_987654321.until(TEST_12_30_40_987654321, null));
    }

    //-----------------------------------------------------------------------
    // format(DateTimeFormatter)
    //-----------------------------------------------------------------------
    @Test
    public void test_format_formatter() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("H m s");
        String t = LocalTime.of(11, 30, 45).format(f);
        assertEquals("11 30 45", t);
    }

    @Test
    public void test_format_formatter_null() {
        Assertions.assertThrows(NullPointerException.class, () -> LocalTime.of(11, 30, 45).format(null));
    }

    //-----------------------------------------------------------------------
    // atDate()
    //-----------------------------------------------------------------------
    @Test
    public void test_atDate() {
        LocalTime t = LocalTime.of(11, 30);
        assertEquals(LocalDateTime.of(2012, 6, 30, 11, 30), t.atDate(LocalDate.of(2012, 6, 30)));
    }

    @Test
    public void test_atDate_nullDate() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12_30_40_987654321.atDate((LocalDate) null));
    }

    //-----------------------------------------------------------------------
    // atOffset()
    //-----------------------------------------------------------------------
    @Test
    public void test_atOffset() {
        LocalTime t = LocalTime.of(11, 30);
        assertEquals(OffsetTime.of(LocalTime.of(11, 30), OFFSET_PTWO), t.atOffset(OFFSET_PTWO));
    }

    @Test
    public void test_atOffset_nullZoneOffset() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            LocalTime t = LocalTime.of(11, 30);
            t.atOffset((ZoneOffset) null);
        });
    }

    //-----------------------------------------------------------------------
    // toSecondOfDay()
    //-----------------------------------------------------------------------
    @Test
    public void test_toSecondOfDay() {
        LocalTime t = LocalTime.of(0, 0);
        for (int i = 0; i < 24 * 60 * 60; i++) {
            assertEquals(i, t.toSecondOfDay());
            t = t.plusSeconds(1);
        }
    }

    //-----------------------------------------------------------------------
    // toEpochSecond()
    //--------------------------------------------------------------------------
    Object[][] provider__toEpochSecond() {
        return new Object[][] {
        {LocalTime.of(0, 0).toEpochSecond(LocalDate.of(1970, 1, 1), OFFSET_PTWO), -7200L},
        {LocalTime.of(11, 30).toEpochSecond(LocalDate.of(1965, 12, 31), OFFSET_PTWO), -126282600L},
        {LocalTime.of(11, 30).toEpochSecond(LocalDate.of(1995, 5, 3), OFFSET_MTWO), 799507800L},
        {LocalTime.of(0, 0).toEpochSecond(LocalDate.of(1970, 1, 1), OFFSET_PTWO),
                Instant.ofEpochSecond(-7200).getEpochSecond()},
        {LocalTime.of(11, 30).toEpochSecond(LocalDate.of(1969, 12, 31), OFFSET_MTWO),
                Instant.ofEpochSecond(-37800L).getEpochSecond()},
        {LocalTime.of(11, 30).toEpochSecond(LocalDate.of(1970, 1, 1), OFFSET_PTWO),
                LocalDateTime.of(1970, 1, 1, 11, 30).toEpochSecond(OFFSET_PTWO)},
        };
    }

    @ParameterizedTest
    @MethodSource("provider__toEpochSecond")
    public void test_toEpochSecond(long actual, long expected) {
        assertEquals(expected, actual);
    }

    //-----------------------------------------------------------------------
    // toSecondOfDay_fromNanoOfDay_symmetry()
    //-----------------------------------------------------------------------
    @Test
    public void test_toSecondOfDay_fromNanoOfDay_symmetry() {
        LocalTime t = LocalTime.of(0, 0);
        for (int i = 0; i < 24 * 60 * 60; i++) {
            assertEquals(t, LocalTime.ofSecondOfDay(t.toSecondOfDay()));
            t = t.plusSeconds(1);
        }
    }

    //-----------------------------------------------------------------------
    // toNanoOfDay()
    //-----------------------------------------------------------------------
    @Test
    public void test_toNanoOfDay() {
        LocalTime t = LocalTime.of(0, 0);
        for (int i = 0; i < 1000000; i++) {
            assertEquals(i, t.toNanoOfDay());
            t = t.plusNanos(1);
        }
        t = LocalTime.of(0, 0);
        for (int i = 1; i <= 1000000; i++) {
            t = t.minusNanos(1);
            assertEquals(24 * 60 * 60 * 1000000000L - i, t.toNanoOfDay());
        }
    }

    @Test
    public void test_toNanoOfDay_fromNanoOfDay_symmetry() {
        LocalTime t = LocalTime.of(0, 0);
        for (int i = 0; i < 1000000; i++) {
            assertEquals(t, LocalTime.ofNanoOfDay(t.toNanoOfDay()));
            t = t.plusNanos(1);
        }
        t = LocalTime.of(0, 0);
        for (int i = 1; i <= 1000000; i++) {
            t = t.minusNanos(1);
            assertEquals(t, LocalTime.ofNanoOfDay(t.toNanoOfDay()));
        }
    }

    //-----------------------------------------------------------------------
    // compareTo()
    //-----------------------------------------------------------------------
    @Test
    public void test_comparisons() {
        doTest_comparisons_LocalTime(
            LocalTime.MIDNIGHT,
            LocalTime.of(0, 0, 0, 999999999),
            LocalTime.of(0, 0, 59, 0),
            LocalTime.of(0, 0, 59, 999999999),
            LocalTime.of(0, 59, 0, 0),
            LocalTime.of(0, 59, 0, 999999999),
            LocalTime.of(0, 59, 59, 0),
            LocalTime.of(0, 59, 59, 999999999),
            LocalTime.NOON,
            LocalTime.of(12, 0, 0, 999999999),
            LocalTime.of(12, 0, 59, 0),
            LocalTime.of(12, 0, 59, 999999999),
            LocalTime.of(12, 59, 0, 0),
            LocalTime.of(12, 59, 0, 999999999),
            LocalTime.of(12, 59, 59, 0),
            LocalTime.of(12, 59, 59, 999999999),
            LocalTime.of(23, 0, 0, 0),
            LocalTime.of(23, 0, 0, 999999999),
            LocalTime.of(23, 0, 59, 0),
            LocalTime.of(23, 0, 59, 999999999),
            LocalTime.of(23, 59, 0, 0),
            LocalTime.of(23, 59, 0, 999999999),
            LocalTime.of(23, 59, 59, 0),
            LocalTime.of(23, 59, 59, 999999999)
        );
    }

    void doTest_comparisons_LocalTime(LocalTime... localTimes) {
        for (int i = 0; i < localTimes.length; i++) {
            LocalTime a = localTimes[i];
            for (int j = 0; j < localTimes.length; j++) {
                LocalTime b = localTimes[j];
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
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12_30_40_987654321.compareTo(null));
    }

    @Test
    public void test_isBefore_ObjectNull() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12_30_40_987654321.isBefore(null));
    }

    @Test
    public void test_isAfter_ObjectNull() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12_30_40_987654321.isAfter(null));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void compareToNonLocalTime() {
       Assertions.assertThrows(ClassCastException.class, () -> {
           Comparable c = TEST_12_30_40_987654321;
           c.compareTo(new Object());
        });
    }

    //-----------------------------------------------------------------------
    // equals()
    //-----------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("provider_sampleTimes")
    public void test_equals_true(int h, int m, int s, int n) {
        LocalTime a = LocalTime.of(h, m, s, n);
        LocalTime b = LocalTime.of(h, m, s, n);
        assertEquals(true, a.equals(b));
    }
    @ParameterizedTest
    @MethodSource("provider_sampleTimes")
    public void test_equals_false_hour_differs(int h, int m, int s, int n) {
        LocalTime a = LocalTime.of(h, m, s, n);
        LocalTime b = LocalTime.of(h + 1, m, s, n);
        assertEquals(false, a.equals(b));
    }
    @ParameterizedTest
    @MethodSource("provider_sampleTimes")
    public void test_equals_false_minute_differs(int h, int m, int s, int n) {
        LocalTime a = LocalTime.of(h, m, s, n);
        LocalTime b = LocalTime.of(h, m + 1, s, n);
        assertEquals(false, a.equals(b));
    }
    @ParameterizedTest
    @MethodSource("provider_sampleTimes")
    public void test_equals_false_second_differs(int h, int m, int s, int n) {
        LocalTime a = LocalTime.of(h, m, s, n);
        LocalTime b = LocalTime.of(h, m, s + 1, n);
        assertEquals(false, a.equals(b));
    }
    @ParameterizedTest
    @MethodSource("provider_sampleTimes")
    public void test_equals_false_nano_differs(int h, int m, int s, int n) {
        LocalTime a = LocalTime.of(h, m, s, n);
        LocalTime b = LocalTime.of(h, m, s, n + 1);
        assertEquals(false, a.equals(b));
    }

    @Test
    public void test_equals_itself_true() {
        assertEquals(true, TEST_12_30_40_987654321.equals(TEST_12_30_40_987654321));
    }

    @Test
    public void test_equals_string_false() {
        assertEquals(false, TEST_12_30_40_987654321.equals("2007-07-15"));
    }

    @Test
    public void test_equals_null_false() {
        assertEquals(false, TEST_12_30_40_987654321.equals(null));
    }

    //-----------------------------------------------------------------------
    // hashCode()
    //-----------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("provider_sampleTimes")
    public void test_hashCode_same(int h, int m, int s, int n) {
        LocalTime a = LocalTime.of(h, m, s, n);
        LocalTime b = LocalTime.of(h, m, s, n);
        assertEquals(b.hashCode(), a.hashCode());
    }

    @ParameterizedTest
    @MethodSource("provider_sampleTimes")
    public void test_hashCode_hour_differs(int h, int m, int s, int n) {
        LocalTime a = LocalTime.of(h, m, s, n);
        LocalTime b = LocalTime.of(h + 1, m, s, n);
        assertEquals(false, a.hashCode() == b.hashCode());
    }

    @ParameterizedTest
    @MethodSource("provider_sampleTimes")
    public void test_hashCode_minute_differs(int h, int m, int s, int n) {
        LocalTime a = LocalTime.of(h, m, s, n);
        LocalTime b = LocalTime.of(h, m + 1, s, n);
        assertEquals(false, a.hashCode() == b.hashCode());
    }

    @ParameterizedTest
    @MethodSource("provider_sampleTimes")
    public void test_hashCode_second_differs(int h, int m, int s, int n) {
        LocalTime a = LocalTime.of(h, m, s, n);
        LocalTime b = LocalTime.of(h, m, s + 1, n);
        assertEquals(false, a.hashCode() == b.hashCode());
    }

    @ParameterizedTest
    @MethodSource("provider_sampleTimes")
    public void test_hashCode_nano_differs(int h, int m, int s, int n) {
        LocalTime a = LocalTime.of(h, m, s, n);
        LocalTime b = LocalTime.of(h, m, s, n + 1);
        assertEquals(false, a.hashCode() == b.hashCode());
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    Object[][] provider_sampleToString() {
        return new Object[][] {
            {0, 0, 0, 0, "00:00"},
            {1, 0, 0, 0, "01:00"},
            {23, 0, 0, 0, "23:00"},
            {0, 1, 0, 0, "00:01"},
            {12, 30, 0, 0, "12:30"},
            {23, 59, 0, 0, "23:59"},
            {0, 0, 1, 0, "00:00:01"},
            {0, 0, 59, 0, "00:00:59"},
            {0, 0, 0, 100000000, "00:00:00.100"},
            {0, 0, 0, 10000000, "00:00:00.010"},
            {0, 0, 0, 1000000, "00:00:00.001"},
            {0, 0, 0, 100000, "00:00:00.000100"},
            {0, 0, 0, 10000, "00:00:00.000010"},
            {0, 0, 0, 1000, "00:00:00.000001"},
            {0, 0, 0, 100, "00:00:00.000000100"},
            {0, 0, 0, 10, "00:00:00.000000010"},
            {0, 0, 0, 1, "00:00:00.000000001"},
            {0, 0, 0, 999999999, "00:00:00.999999999"},
            {0, 0, 0, 99999999, "00:00:00.099999999"},
            {0, 0, 0, 9999999, "00:00:00.009999999"},
            {0, 0, 0, 999999, "00:00:00.000999999"},
            {0, 0, 0, 99999, "00:00:00.000099999"},
            {0, 0, 0, 9999, "00:00:00.000009999"},
            {0, 0, 0, 999, "00:00:00.000000999"},
            {0, 0, 0, 99, "00:00:00.000000099"},
            {0, 0, 0, 9, "00:00:00.000000009"},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sampleToString")
    public void test_toString(int h, int m, int s, int n, String expected) {
        LocalTime t = LocalTime.of(h, m, s, n);
        String str = t.toString();
        assertEquals(expected, str);
    }

    private LocalTime time(int hour, int min, int sec, int nano) {
        return LocalTime.of(hour, min, sec, nano);
    }
}
