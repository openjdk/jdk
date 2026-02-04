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

import static java.time.temporal.ChronoField.INSTANT_SECONDS;
import static java.time.temporal.ChronoField.MICRO_OF_SECOND;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoUnit.DAYS;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test Instant.
 *
 * @bug 8133022 8307466
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TCKInstant extends AbstractDateTimeTest {

    private static final long MIN_SECOND = Instant.MIN.getEpochSecond();
    private static final long MAX_SECOND = Instant.MAX.getEpochSecond();
    private static final ZoneId ZONE_PARIS = ZoneId.of("Europe/Paris");
    private static final ZoneOffset OFFSET_PTWO = ZoneOffset.ofHours(2);

    private Instant TEST_12345_123456789;

    @BeforeEach
    public void setUp() {
        TEST_12345_123456789 = Instant.ofEpochSecond(12345, 123456789);
    }

    //-----------------------------------------------------------------------
    @Override
    protected List<TemporalAccessor> samples() {
        TemporalAccessor[] array = {TEST_12345_123456789, Instant.MIN, Instant.MAX, Instant.EPOCH};
        return Arrays.asList(array);
    }

    @Override
    protected List<TemporalField> validFields() {
        TemporalField[] array = {
            NANO_OF_SECOND,
            MICRO_OF_SECOND,
            MILLI_OF_SECOND,
            INSTANT_SECONDS,
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
    private void check(Instant instant, long epochSecs, int nos) {
        assertEquals(epochSecs, instant.getEpochSecond());
        assertEquals(nos, instant.getNano());
        assertEquals(instant, instant);
        assertEquals(instant.hashCode(), instant.hashCode());
    }

    //-----------------------------------------------------------------------
    @Test
    public void constant_EPOCH() {
        check(Instant.EPOCH, 0, 0);
    }

    @Test
    public void constant_MIN() {
        check(Instant.MIN, -31557014167219200L, 0);
    }

    @Test
    public void constant_MAX() {
        check(Instant.MAX, 31556889864403199L, 999_999_999);
    }

    //-----------------------------------------------------------------------
    // now()
    //-----------------------------------------------------------------------
    @Test
    public void now() {
        long beforeMillis, instantMillis, afterMillis, diff;
        int retryRemaining = 5; // MAX_RETRY_COUNT
        do {
            beforeMillis = Instant.now(Clock.systemUTC()).toEpochMilli();
            instantMillis = Instant.now().toEpochMilli();
            afterMillis = Instant.now(Clock.systemUTC()).toEpochMilli();
            diff = instantMillis - beforeMillis;
            if (instantMillis < beforeMillis || instantMillis > afterMillis) {
                throw new RuntimeException(": Invalid instant: (~" + instantMillis + "ms)"
                        + " when systemUTC in millis is in ["
                        + beforeMillis + ", "
                        + afterMillis + "]");
            }
        } while (diff > 100 && --retryRemaining > 0);  // retry if diff more than 0.1 sec
        assertTrue(retryRemaining > 0);
    }

    //-----------------------------------------------------------------------
    // now(Clock)
    //-----------------------------------------------------------------------
    @Test
    public void now_Clock_nullClock() {
        Assertions.assertThrows(NullPointerException.class, () -> Instant.now(null));
    }

    @Test
    public void now_Clock_allSecsInDay_utc() {
        for (int i = 0; i < (2 * 24 * 60 * 60); i++) {
            Instant expected = Instant.ofEpochSecond(i).plusNanos(123456789L);
            Clock clock = Clock.fixed(expected, ZoneOffset.UTC);
            Instant test = Instant.now(clock);
            assertEquals(expected, test);
        }
    }

    @Test
    public void now_Clock_allSecsInDay_beforeEpoch() {
        for (int i =-1; i >= -(24 * 60 * 60); i--) {
            Instant expected = Instant.ofEpochSecond(i).plusNanos(123456789L);
            Clock clock = Clock.fixed(expected, ZoneOffset.UTC);
            Instant test = Instant.now(clock);
            assertEquals(expected, test);
        }
    }

    //-----------------------------------------------------------------------
    // ofEpochSecond(long)
    //-----------------------------------------------------------------------
    @Test
    public void factory_seconds_long() {
        for (long i = -2; i <= 2; i++) {
            Instant t = Instant.ofEpochSecond(i);
            assertEquals(i, t.getEpochSecond());
            assertEquals(0, t.getNano());
        }
    }

    //-----------------------------------------------------------------------
    // ofEpochSecond(long,long)
    //-----------------------------------------------------------------------
    @Test
    public void factory_seconds_long_long() {
        for (long i = -2; i <= 2; i++) {
            for (int j = 0; j < 10; j++) {
                Instant t = Instant.ofEpochSecond(i, j);
                assertEquals(i, t.getEpochSecond());
                assertEquals(j, t.getNano());
            }
            for (int j = -10; j < 0; j++) {
                Instant t = Instant.ofEpochSecond(i, j);
                assertEquals(i - 1, t.getEpochSecond());
                assertEquals(j + 1000000000, t.getNano());
            }
            for (int j = 999999990; j < 1000000000; j++) {
                Instant t = Instant.ofEpochSecond(i, j);
                assertEquals(i, t.getEpochSecond());
                assertEquals(j, t.getNano());
            }
        }
    }

    @Test
    public void factory_seconds_long_long_nanosNegativeAdjusted() {
        Instant test = Instant.ofEpochSecond(2L, -1);
        assertEquals(1, test.getEpochSecond());
        assertEquals(999999999, test.getNano());
    }

    @Test
    public void factory_seconds_long_long_tooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> Instant.ofEpochSecond(MAX_SECOND, 1000000000));
    }

    @Test
    public void factory_seconds_long_long_tooBigBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> Instant.ofEpochSecond(Long.MAX_VALUE, Long.MAX_VALUE));
    }

    //-----------------------------------------------------------------------
    // ofEpochMilli(long)
    //-----------------------------------------------------------------------
    Object[][] provider_factory_millis_long() {
        return new Object[][] {
                {0, 0, 0},
                {1, 0, 1000000},
                {2, 0, 2000000},
                {999, 0, 999000000},
                {1000, 1, 0},
                {1001, 1, 1000000},
                {-1, -1, 999000000},
                {-2, -1, 998000000},
                {-999, -1, 1000000},
                {-1000, -1, 0},
                {-1001, -2, 999000000},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_factory_millis_long")
    public void factory_millis_long(long millis, long expectedSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.ofEpochMilli(millis);
        assertEquals(expectedSeconds, t.getEpochSecond());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    //-----------------------------------------------------------------------
    // parse(String)
    //-----------------------------------------------------------------------
    // see also parse tests under toString()
    Object[][] provider_factory_parse() {
        return new Object[][] {
                {"1970-01-01T00:00:00Z", 0, 0},
                {"1970-01-01t00:00:00Z", 0, 0},
                {"1970-01-01T00:00:00z", 0, 0},
                {"1970-01-01T00:00:00.0Z", 0, 0},
                {"1970-01-01T00:00:00.000000000Z", 0, 0},

                {"1970-01-01T00:00:00.000000001Z", 0, 1},
                {"1970-01-01T00:00:00.100000000Z", 0, 100000000},
                {"1970-01-01T00:00:01Z", 1, 0},
                {"1970-01-01T00:01:00Z", 60, 0},
                {"1970-01-01T00:01:01Z", 61, 0},
                {"1970-01-01T00:01:01.000000001Z", 61, 1},
                {"1970-01-01T01:00:00.000000000Z", 3600, 0},
                {"1970-01-01T01:01:01.000000001Z", 3661, 1},
                {"1970-01-02T01:01:01.100000000Z", 90061, 100000000},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_factory_parse")
    public void factory_parse(String text, long expectedEpochSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.parse(text);
        assertEquals(expectedEpochSeconds, t.getEpochSecond());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @ParameterizedTest
    @MethodSource("provider_factory_parse")
    public void factory_parseLowercase(String text, long expectedEpochSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.parse(text.toLowerCase(Locale.ENGLISH));
        assertEquals(expectedEpochSeconds, t.getEpochSecond());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

// TODO: should comma be accepted?
//    @Test(dataProvider="Parse")
//    public void factory_parse_comma(String text, long expectedEpochSeconds, int expectedNanoOfSecond) {
//        text = text.replace('.', ',');
//        Instant t = Instant.parse(text);
//        assertEquals(t.getEpochSecond(), expectedEpochSeconds);
//        assertEquals(t.getNano(), expectedNanoOfSecond);
//    }

    Object[][] provider_factory_parseFailures() {
        return new Object[][] {
                {""},
                {"Z"},
                {"1970-01-01T00:00:00"},
                {"1970-01-01T00:00:0Z"},
                {"1970-01-01T00:0:00Z"},
                {"1970-01-01T0:00:00Z"},
                {"1970-01-01T00:00:00.0000000000Z"},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_factory_parseFailures")
    public void factory_parseFailures(String text) {
        Assertions.assertThrows(DateTimeParseException.class, () -> Instant.parse(text));
    }

    @ParameterizedTest
    @MethodSource("provider_factory_parseFailures")
    public void factory_parseFailures_comma(String text) {
        var commaText = text.replace('.', ',');
        Assertions.assertThrows(DateTimeParseException.class, () -> Instant.parse(commaText));
    }

    @Test
    public void factory_parse_nullText() {
        Assertions.assertThrows(NullPointerException.class, () -> Instant.parse(null));
    }

    //-----------------------------------------------------------------------
    // get(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_get_TemporalField() {
        Instant test = TEST_12345_123456789;
        assertEquals(123456789, test.get(ChronoField.NANO_OF_SECOND));
        assertEquals(123456, test.get(ChronoField.MICRO_OF_SECOND));
        assertEquals(123, test.get(ChronoField.MILLI_OF_SECOND));
    }

    @Test
    public void test_getLong_TemporalField() {
        Instant test = TEST_12345_123456789;
        assertEquals(123456789, test.getLong(ChronoField.NANO_OF_SECOND));
        assertEquals(123456, test.getLong(ChronoField.MICRO_OF_SECOND));
        assertEquals(123, test.getLong(ChronoField.MILLI_OF_SECOND));
        assertEquals(12345, test.getLong(ChronoField.INSTANT_SECONDS));
    }

    //-----------------------------------------------------------------------
    // query(TemporalQuery)
    //-----------------------------------------------------------------------
    Object[][] data_query() {
        return new Object[][] {
                {TEST_12345_123456789, TemporalQueries.chronology(), null},
                {TEST_12345_123456789, TemporalQueries.zoneId(), null},
                {TEST_12345_123456789, TemporalQueries.precision(), NANOS},
                {TEST_12345_123456789, TemporalQueries.zone(), null},
                {TEST_12345_123456789, TemporalQueries.offset(), null},
                {TEST_12345_123456789, TemporalQueries.localDate(), null},
                {TEST_12345_123456789, TemporalQueries.localTime(), null},
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
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12345_123456789.query(null));
    }

    //-----------------------------------------------------------------------
    // adjustInto(Temporal)
    //-----------------------------------------------------------------------
    Object[][] data_adjustInto() {
        return new Object[][]{
                {Instant.ofEpochSecond(10, 200), Instant.ofEpochSecond(20), Instant.ofEpochSecond(10, 200), null},
                {Instant.ofEpochSecond(10, -200), Instant.now(), Instant.ofEpochSecond(10, -200), null},
                {Instant.ofEpochSecond(-10), Instant.EPOCH, Instant.ofEpochSecond(-10), null},
                {Instant.ofEpochSecond(10), Instant.MIN, Instant.ofEpochSecond(10), null},
                {Instant.ofEpochSecond(10), Instant.MAX, Instant.ofEpochSecond(10), null},

                {Instant.ofEpochSecond(10, 200), LocalDateTime.of(1970, 1, 1, 0, 0, 20).toInstant(ZoneOffset.UTC), Instant.ofEpochSecond(10, 200), null},
                {Instant.ofEpochSecond(10, 200), OffsetDateTime.of(1970, 1, 1, 0, 0, 20, 10, ZoneOffset.UTC), OffsetDateTime.of(1970, 1, 1, 0, 0, 10, 200, ZoneOffset.UTC), null},
                {Instant.ofEpochSecond(10, 200), OffsetDateTime.of(1970, 1, 1, 0, 0, 20, 10, OFFSET_PTWO), OffsetDateTime.of(1970, 1, 1, 2, 0, 10, 200, OFFSET_PTWO), null},
                {Instant.ofEpochSecond(10, 200), ZonedDateTime.of(1970, 1, 1, 0, 0, 20, 10, ZONE_PARIS), ZonedDateTime.of(1970, 1, 1, 1, 0, 10, 200, ZONE_PARIS), null},

                {Instant.ofEpochSecond(10, 200), LocalDateTime.of(1970, 1, 1, 0, 0, 20), null, DateTimeException.class},
                {Instant.ofEpochSecond(10, 200), null, null, NullPointerException.class},

        };
    }

    @ParameterizedTest
    @MethodSource("data_adjustInto")
    public void test_adjustInto(Instant test, Temporal temporal, Temporal expected, Class<?> expectedEx) {
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
    Object[][] data_with() {
        return new Object[][]{
                {Instant.ofEpochSecond(10, 200), Instant.ofEpochSecond(20), Instant.ofEpochSecond(20), null},
                {Instant.ofEpochSecond(10), Instant.ofEpochSecond(20, -100), Instant.ofEpochSecond(20, -100), null},
                {Instant.ofEpochSecond(-10), Instant.EPOCH, Instant.ofEpochSecond(0), null},
                {Instant.ofEpochSecond(10), Instant.MIN, Instant.MIN, null},
                {Instant.ofEpochSecond(10), Instant.MAX, Instant.MAX, null},

                {Instant.ofEpochSecond(10, 200), LocalDateTime.of(1970, 1, 1, 0, 0, 20).toInstant(ZoneOffset.UTC), Instant.ofEpochSecond(20), null},

                {Instant.ofEpochSecond(10, 200), LocalDateTime.of(1970, 1, 1, 0, 0, 20), null, DateTimeException.class},
                {Instant.ofEpochSecond(10, 200), null, null, NullPointerException.class},

        };
    }


    @ParameterizedTest
    @MethodSource("data_with")
    public void test_with_temporalAdjuster(Instant test, TemporalAdjuster adjuster, Instant expected, Class<?> expectedEx) {
        if (expectedEx == null) {
            Instant result = test.with(adjuster);
            assertEquals(expected, result);
        } else {
            try {
                Instant result = test.with(adjuster);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    //-----------------------------------------------------------------------
    // with(TemporalField, long)
    //-----------------------------------------------------------------------
    Object[][] data_with_longTemporalField() {
        return new Object[][]{
                {Instant.ofEpochSecond(10, 200), ChronoField.INSTANT_SECONDS, 100, Instant.ofEpochSecond(100, 200), null},
                {Instant.ofEpochSecond(10, 200), ChronoField.INSTANT_SECONDS, 0, Instant.ofEpochSecond(0, 200), null},
                {Instant.ofEpochSecond(10, 200), ChronoField.INSTANT_SECONDS, -100, Instant.ofEpochSecond(-100, 200), null},
                {Instant.ofEpochSecond(10, 200), ChronoField.NANO_OF_SECOND, 100, Instant.ofEpochSecond(10, 100), null},
                {Instant.ofEpochSecond(10, 200), ChronoField.NANO_OF_SECOND, 0, Instant.ofEpochSecond(10), null},
                {Instant.ofEpochSecond(10, 200), ChronoField.MICRO_OF_SECOND, 100, Instant.ofEpochSecond(10, 100*1000), null},
                {Instant.ofEpochSecond(10, 200), ChronoField.MICRO_OF_SECOND, 0, Instant.ofEpochSecond(10), null},
                {Instant.ofEpochSecond(10, 200), ChronoField.MILLI_OF_SECOND, 100, Instant.ofEpochSecond(10, 100*1000*1000), null},
                {Instant.ofEpochSecond(10, 200), ChronoField.MILLI_OF_SECOND, 0, Instant.ofEpochSecond(10), null},

                {Instant.ofEpochSecond(10, 200), ChronoField.NANO_OF_SECOND, 1000000000L, null, DateTimeException.class},
                {Instant.ofEpochSecond(10, 200), ChronoField.MICRO_OF_SECOND, 1000000, null, DateTimeException.class},
                {Instant.ofEpochSecond(10, 200), ChronoField.MILLI_OF_SECOND, 1000, null, DateTimeException.class},

                {Instant.ofEpochSecond(10, 200), ChronoField.SECOND_OF_MINUTE, 1, null, DateTimeException.class},
                {Instant.ofEpochSecond(10, 200), ChronoField.SECOND_OF_DAY, 1, null, DateTimeException.class},
                {Instant.ofEpochSecond(10, 200), ChronoField.OFFSET_SECONDS, 1, null, DateTimeException.class},
                {Instant.ofEpochSecond(10, 200), ChronoField.NANO_OF_DAY, 1, null, DateTimeException.class},
                {Instant.ofEpochSecond(10, 200), ChronoField.MINUTE_OF_HOUR, 1, null, DateTimeException.class},
                {Instant.ofEpochSecond(10, 200), ChronoField.MINUTE_OF_DAY, 1, null, DateTimeException.class},
                {Instant.ofEpochSecond(10, 200), ChronoField.MILLI_OF_DAY, 1, null, DateTimeException.class},
                {Instant.ofEpochSecond(10, 200), ChronoField.MICRO_OF_DAY, 1, null, DateTimeException.class},


        };
    }

    @ParameterizedTest
    @MethodSource("data_with_longTemporalField")
    public void test_with_longTemporalField(Instant test, TemporalField field, long value, Instant expected, Class<?> expectedEx) {
        if (expectedEx == null) {
            Instant result = test.with(field, value);
            assertEquals(expected, result);
        } else {
            try {
                Instant result = test.with(field, value);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
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
                {Instant.ofEpochSecond(86400 + 3600 + 60 + 1, 123_456_789), NANOS, Instant.ofEpochSecond(86400 + 3600 + 60 + 1, 123_456_789)},
                {Instant.ofEpochSecond(86400 + 3600 + 60 + 1, 123_456_789), MICROS, Instant.ofEpochSecond(86400 + 3600 + 60 + 1, 123_456_000)},
                {Instant.ofEpochSecond(86400 + 3600 + 60 + 1, 123_456_789), MILLIS, Instant.ofEpochSecond(86400 + 3600 + 60 + 1, 1230_00_000)},
                {Instant.ofEpochSecond(86400 + 3600 + 60 + 1, 123_456_789), SECONDS, Instant.ofEpochSecond(86400 + 3600 + 60 + 1, 0)},
                {Instant.ofEpochSecond(86400 + 3600 + 60 + 1, 123_456_789), MINUTES, Instant.ofEpochSecond(86400 + 3600 + 60, 0)},
                {Instant.ofEpochSecond(86400 + 3600 + 60 + 1, 123_456_789), HOURS, Instant.ofEpochSecond(86400 + 3600, 0)},
                {Instant.ofEpochSecond(86400 + 3600 + 60 + 1, 123_456_789), DAYS, Instant.ofEpochSecond(86400, 0)},

                {Instant.ofEpochSecond(86400 + 3600 + 60 + 1, 123_456_789), NINETY_MINS, Instant.ofEpochSecond(86400 + 0, 0)},
                {Instant.ofEpochSecond(86400 + 7200 + 60 + 1, 123_456_789), NINETY_MINS, Instant.ofEpochSecond(86400 + 5400, 0)},
                {Instant.ofEpochSecond(86400 + 10800 + 60 + 1, 123_456_789), NINETY_MINS, Instant.ofEpochSecond(86400 + 10800, 0)},

                {Instant.ofEpochSecond(-86400 - 3600 - 60 - 1, 123_456_789), MINUTES, Instant.ofEpochSecond(-86400 - 3600 - 120, 0 )},
                {Instant.ofEpochSecond(-86400 - 3600 - 60 - 1, 123_456_789), MICROS, Instant.ofEpochSecond(-86400 - 3600 - 60 - 1, 123_456_000)},

                {Instant.ofEpochSecond(86400 + 3600 + 60 + 1, 0), SECONDS, Instant.ofEpochSecond(86400 + 3600 + 60 + 1, 0)},
                {Instant.ofEpochSecond(-86400 - 3600 - 120, 0), MINUTES, Instant.ofEpochSecond(-86400 - 3600 - 120, 0)},
        };
    }
    @ParameterizedTest
    @MethodSource("data_truncatedToValid")
    public void test_truncatedTo_valid(Instant input, TemporalUnit unit, Instant expected) {
        assertEquals(expected, input.truncatedTo(unit));
    }

    Object[][] data_truncatedToInvalid() {
        return new Object[][] {
                {Instant.ofEpochSecond(1, 123_456_789), NINETY_FIVE_MINS},
                {Instant.ofEpochSecond(1, 123_456_789), WEEKS},
                {Instant.ofEpochSecond(1, 123_456_789), MONTHS},
                {Instant.ofEpochSecond(1, 123_456_789), YEARS},
        };
    }

    @ParameterizedTest
    @MethodSource("data_truncatedToInvalid")
    public void test_truncatedTo_invalid(Instant input, TemporalUnit unit) {
        Assertions.assertThrows(DateTimeException.class, () -> input.truncatedTo(unit));
    }

    @Test
    public void test_truncatedTo_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12345_123456789.truncatedTo(null));
    }

    //-----------------------------------------------------------------------
    // plus(TemporalAmount)
    //-----------------------------------------------------------------------
    Object[][] data_plusTemporalAmount() {
        return new Object[][] {
                {DAYS, MockSimplePeriod.of(1, DAYS), 86401, 0},
                {HOURS, MockSimplePeriod.of(2, HOURS), 7201, 0},
                {MINUTES, MockSimplePeriod.of(4, MINUTES), 241, 0},
                {SECONDS, MockSimplePeriod.of(5, SECONDS), 6, 0},
                {NANOS, MockSimplePeriod.of(6, NANOS), 1, 6},
                {DAYS, MockSimplePeriod.of(10, DAYS), 864001, 0},
                {HOURS, MockSimplePeriod.of(11, HOURS), 39601, 0},
                {MINUTES, MockSimplePeriod.of(12, MINUTES), 721, 0},
                {SECONDS, MockSimplePeriod.of(13, SECONDS), 14, 0},
                {NANOS, MockSimplePeriod.of(14, NANOS), 1, 14},
                {SECONDS, Duration.ofSeconds(20, 40), 21, 40},
                {NANOS, Duration.ofSeconds(30, 300), 31, 300},
        };
    }

    @ParameterizedTest
    @MethodSource("data_plusTemporalAmount")
    public void test_plusTemporalAmount(TemporalUnit unit, TemporalAmount amount, int seconds, int nanos) {
        Instant inst = Instant.ofEpochMilli(1000);
        Instant actual = inst.plus(amount);
        Instant expected = Instant.ofEpochSecond(seconds, nanos);
        assertEquals(expected, actual, "plus(TemporalAmount) failed");
    }

    Object[][] data_badPlusTemporalAmount() {
        return new Object[][] {
                {MockSimplePeriod.of(2, YEARS)},
                {MockSimplePeriod.of(2, MONTHS)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_badPlusTemporalAmount")
    public void test_badPlusTemporalAmount(TemporalAmount amount) {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant inst = Instant.ofEpochMilli(1000);
            inst.plus(amount);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_plus() {
        return new Object[][] {
                {MIN_SECOND, 0, -MIN_SECOND, 0, 0, 0},

                {MIN_SECOND, 0, 1, 0, MIN_SECOND + 1, 0},
                {MIN_SECOND, 0, 0, 500, MIN_SECOND, 500},
                {MIN_SECOND, 0, 0, 1000000000, MIN_SECOND + 1, 0},

                {MIN_SECOND + 1, 0, -1, 0, MIN_SECOND, 0},
                {MIN_SECOND + 1, 0, 0, -500, MIN_SECOND, 999999500},
                {MIN_SECOND + 1, 0, 0, -1000000000, MIN_SECOND, 0},

                {-4, 666666667, -4, 666666667, -7, 333333334},
                {-4, 666666667, -3,         0, -7, 666666667},
                {-4, 666666667, -2,         0, -6, 666666667},
                {-4, 666666667, -1,         0, -5, 666666667},
                {-4, 666666667, -1, 333333334, -4,         1},
                {-4, 666666667, -1, 666666667, -4, 333333334},
                {-4, 666666667, -1, 999999999, -4, 666666666},
                {-4, 666666667,  0,         0, -4, 666666667},
                {-4, 666666667,  0,         1, -4, 666666668},
                {-4, 666666667,  0, 333333333, -3,         0},
                {-4, 666666667,  0, 666666666, -3, 333333333},
                {-4, 666666667,  1,         0, -3, 666666667},
                {-4, 666666667,  2,         0, -2, 666666667},
                {-4, 666666667,  3,         0, -1, 666666667},
                {-4, 666666667,  3, 333333333,  0,         0},

                {-3, 0, -4, 666666667, -7, 666666667},
                {-3, 0, -3,         0, -6,         0},
                {-3, 0, -2,         0, -5,         0},
                {-3, 0, -1,         0, -4,         0},
                {-3, 0, -1, 333333334, -4, 333333334},
                {-3, 0, -1, 666666667, -4, 666666667},
                {-3, 0, -1, 999999999, -4, 999999999},
                {-3, 0,  0,         0, -3,         0},
                {-3, 0,  0,         1, -3,         1},
                {-3, 0,  0, 333333333, -3, 333333333},
                {-3, 0,  0, 666666666, -3, 666666666},
                {-3, 0,  1,         0, -2,         0},
                {-3, 0,  2,         0, -1,         0},
                {-3, 0,  3,         0,  0,         0},
                {-3, 0,  3, 333333333,  0, 333333333},

                {-2, 0, -4, 666666667, -6, 666666667},
                {-2, 0, -3,         0, -5,         0},
                {-2, 0, -2,         0, -4,         0},
                {-2, 0, -1,         0, -3,         0},
                {-2, 0, -1, 333333334, -3, 333333334},
                {-2, 0, -1, 666666667, -3, 666666667},
                {-2, 0, -1, 999999999, -3, 999999999},
                {-2, 0,  0,         0, -2,         0},
                {-2, 0,  0,         1, -2,         1},
                {-2, 0,  0, 333333333, -2, 333333333},
                {-2, 0,  0, 666666666, -2, 666666666},
                {-2, 0,  1,         0, -1,         0},
                {-2, 0,  2,         0,  0,         0},
                {-2, 0,  3,         0,  1,         0},
                {-2, 0,  3, 333333333,  1, 333333333},

                {-1, 0, -4, 666666667, -5, 666666667},
                {-1, 0, -3,         0, -4,         0},
                {-1, 0, -2,         0, -3,         0},
                {-1, 0, -1,         0, -2,         0},
                {-1, 0, -1, 333333334, -2, 333333334},
                {-1, 0, -1, 666666667, -2, 666666667},
                {-1, 0, -1, 999999999, -2, 999999999},
                {-1, 0,  0,         0, -1,         0},
                {-1, 0,  0,         1, -1,         1},
                {-1, 0,  0, 333333333, -1, 333333333},
                {-1, 0,  0, 666666666, -1, 666666666},
                {-1, 0,  1,         0,  0,         0},
                {-1, 0,  2,         0,  1,         0},
                {-1, 0,  3,         0,  2,         0},
                {-1, 0,  3, 333333333,  2, 333333333},

                {-1, 666666667, -4, 666666667, -4, 333333334},
                {-1, 666666667, -3,         0, -4, 666666667},
                {-1, 666666667, -2,         0, -3, 666666667},
                {-1, 666666667, -1,         0, -2, 666666667},
                {-1, 666666667, -1, 333333334, -1,         1},
                {-1, 666666667, -1, 666666667, -1, 333333334},
                {-1, 666666667, -1, 999999999, -1, 666666666},
                {-1, 666666667,  0,         0, -1, 666666667},
                {-1, 666666667,  0,         1, -1, 666666668},
                {-1, 666666667,  0, 333333333,  0,         0},
                {-1, 666666667,  0, 666666666,  0, 333333333},
                {-1, 666666667,  1,         0,  0, 666666667},
                {-1, 666666667,  2,         0,  1, 666666667},
                {-1, 666666667,  3,         0,  2, 666666667},
                {-1, 666666667,  3, 333333333,  3,         0},

                {0, 0, -4, 666666667, -4, 666666667},
                {0, 0, -3,         0, -3,         0},
                {0, 0, -2,         0, -2,         0},
                {0, 0, -1,         0, -1,         0},
                {0, 0, -1, 333333334, -1, 333333334},
                {0, 0, -1, 666666667, -1, 666666667},
                {0, 0, -1, 999999999, -1, 999999999},
                {0, 0,  0,         0,  0,         0},
                {0, 0,  0,         1,  0,         1},
                {0, 0,  0, 333333333,  0, 333333333},
                {0, 0,  0, 666666666,  0, 666666666},
                {0, 0,  1,         0,  1,         0},
                {0, 0,  2,         0,  2,         0},
                {0, 0,  3,         0,  3,         0},
                {0, 0,  3, 333333333,  3, 333333333},

                {0, 333333333, -4, 666666667, -3,         0},
                {0, 333333333, -3,         0, -3, 333333333},
                {0, 333333333, -2,         0, -2, 333333333},
                {0, 333333333, -1,         0, -1, 333333333},
                {0, 333333333, -1, 333333334, -1, 666666667},
                {0, 333333333, -1, 666666667,  0,         0},
                {0, 333333333, -1, 999999999,  0, 333333332},
                {0, 333333333,  0,         0,  0, 333333333},
                {0, 333333333,  0,         1,  0, 333333334},
                {0, 333333333,  0, 333333333,  0, 666666666},
                {0, 333333333,  0, 666666666,  0, 999999999},
                {0, 333333333,  1,         0,  1, 333333333},
                {0, 333333333,  2,         0,  2, 333333333},
                {0, 333333333,  3,         0,  3, 333333333},
                {0, 333333333,  3, 333333333,  3, 666666666},

                {1, 0, -4, 666666667, -3, 666666667},
                {1, 0, -3,         0, -2,         0},
                {1, 0, -2,         0, -1,         0},
                {1, 0, -1,         0,  0,         0},
                {1, 0, -1, 333333334,  0, 333333334},
                {1, 0, -1, 666666667,  0, 666666667},
                {1, 0, -1, 999999999,  0, 999999999},
                {1, 0,  0,         0,  1,         0},
                {1, 0,  0,         1,  1,         1},
                {1, 0,  0, 333333333,  1, 333333333},
                {1, 0,  0, 666666666,  1, 666666666},
                {1, 0,  1,         0,  2,         0},
                {1, 0,  2,         0,  3,         0},
                {1, 0,  3,         0,  4,         0},
                {1, 0,  3, 333333333,  4, 333333333},

                {2, 0, -4, 666666667, -2, 666666667},
                {2, 0, -3,         0, -1,         0},
                {2, 0, -2,         0,  0,         0},
                {2, 0, -1,         0,  1,         0},
                {2, 0, -1, 333333334,  1, 333333334},
                {2, 0, -1, 666666667,  1, 666666667},
                {2, 0, -1, 999999999,  1, 999999999},
                {2, 0,  0,         0,  2,         0},
                {2, 0,  0,         1,  2,         1},
                {2, 0,  0, 333333333,  2, 333333333},
                {2, 0,  0, 666666666,  2, 666666666},
                {2, 0,  1,         0,  3,         0},
                {2, 0,  2,         0,  4,         0},
                {2, 0,  3,         0,  5,         0},
                {2, 0,  3, 333333333,  5, 333333333},

                {3, 0, -4, 666666667, -1, 666666667},
                {3, 0, -3,         0,  0,         0},
                {3, 0, -2,         0,  1,         0},
                {3, 0, -1,         0,  2,         0},
                {3, 0, -1, 333333334,  2, 333333334},
                {3, 0, -1, 666666667,  2, 666666667},
                {3, 0, -1, 999999999,  2, 999999999},
                {3, 0,  0,         0,  3,         0},
                {3, 0,  0,         1,  3,         1},
                {3, 0,  0, 333333333,  3, 333333333},
                {3, 0,  0, 666666666,  3, 666666666},
                {3, 0,  1,         0,  4,         0},
                {3, 0,  2,         0,  5,         0},
                {3, 0,  3,         0,  6,         0},
                {3, 0,  3, 333333333,  6, 333333333},

                {3, 333333333, -4, 666666667,  0,         0},
                {3, 333333333, -3,         0,  0, 333333333},
                {3, 333333333, -2,         0,  1, 333333333},
                {3, 333333333, -1,         0,  2, 333333333},
                {3, 333333333, -1, 333333334,  2, 666666667},
                {3, 333333333, -1, 666666667,  3,         0},
                {3, 333333333, -1, 999999999,  3, 333333332},
                {3, 333333333,  0,         0,  3, 333333333},
                {3, 333333333,  0,         1,  3, 333333334},
                {3, 333333333,  0, 333333333,  3, 666666666},
                {3, 333333333,  0, 666666666,  3, 999999999},
                {3, 333333333,  1,         0,  4, 333333333},
                {3, 333333333,  2,         0,  5, 333333333},
                {3, 333333333,  3,         0,  6, 333333333},
                {3, 333333333,  3, 333333333,  6, 666666666},

                {MAX_SECOND - 1, 0, 1, 0, MAX_SECOND, 0},
                {MAX_SECOND - 1, 0, 0, 500, MAX_SECOND - 1, 500},
                {MAX_SECOND - 1, 0, 0, 1000000000, MAX_SECOND, 0},

                {MAX_SECOND, 0, -1, 0, MAX_SECOND - 1, 0},
                {MAX_SECOND, 0, 0, -500, MAX_SECOND - 1, 999999500},
                {MAX_SECOND, 0, 0, -1000000000, MAX_SECOND - 1, 0},

                {MAX_SECOND, 0, -MAX_SECOND, 0, 0, 0},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_plus")
    public void plus_Duration(long seconds, int nanos, long otherSeconds, int otherNanos, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds, nanos).plus(Duration.ofSeconds(otherSeconds, otherNanos));
        assertEquals(expectedSeconds, i.getEpochSecond());
        assertEquals(expectedNanoOfSecond, i.getNano());
    }

    @Test
    public void plus_Duration_overflowTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant i = Instant.ofEpochSecond(MAX_SECOND, 999999999);
            i.plus(Duration.ofSeconds(0, 1));
        });
    }

    @Test
    public void plus_Duration_overflowTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant i = Instant.ofEpochSecond(MIN_SECOND);
            i.plus(Duration.ofSeconds(-1, 999999999));
        });
    }

    //-----------------------------------------------------------------------a
    @ParameterizedTest
    @MethodSource("provider_plus")
    public void plus_longTemporalUnit(long seconds, int nanos, long otherSeconds, int otherNanos, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds, nanos).plus(otherSeconds, SECONDS).plus(otherNanos, NANOS);
        assertEquals(expectedSeconds, i.getEpochSecond());
        assertEquals(expectedNanoOfSecond, i.getNano());
    }

    @Test
    public void plus_longTemporalUnit_overflowTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant i = Instant.ofEpochSecond(MAX_SECOND, 999999999);
            i.plus(1, NANOS);
        });
    }

    @Test
    public void plus_longTemporalUnit_overflowTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant i = Instant.ofEpochSecond(MIN_SECOND);
            i.plus(999999999, NANOS);
            i.plus(-1, SECONDS);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_plusSeconds_long() {
        return new Object[][] {
                {0, 0, 0, 0, 0},
                {0, 0, 1, 1, 0},
                {0, 0, -1, -1, 0},
                {0, 0, MAX_SECOND, MAX_SECOND, 0},
                {0, 0, MIN_SECOND, MIN_SECOND, 0},
                {1, 0, 0, 1, 0},
                {1, 0, 1, 2, 0},
                {1, 0, -1, 0, 0},
                {1, 0, MAX_SECOND - 1, MAX_SECOND, 0},
                {1, 0, MIN_SECOND, MIN_SECOND + 1, 0},
                {1, 1, 0, 1, 1},
                {1, 1, 1, 2, 1},
                {1, 1, -1, 0, 1},
                {1, 1, MAX_SECOND - 1, MAX_SECOND, 1},
                {1, 1, MIN_SECOND, MIN_SECOND + 1, 1},
                {-1, 1, 0, -1, 1},
                {-1, 1, 1, 0, 1},
                {-1, 1, -1, -2, 1},
                {-1, 1, MAX_SECOND, MAX_SECOND - 1, 1},
                {-1, 1, MIN_SECOND + 1, MIN_SECOND, 1},

                {MAX_SECOND, 2, -MAX_SECOND, 0, 2},
                {MIN_SECOND, 2, -MIN_SECOND, 0, 2},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_plusSeconds_long")
    public void plusSeconds_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.ofEpochSecond(seconds, nanos);
        t = t.plusSeconds(amount);
        assertEquals(expectedSeconds, t.getEpochSecond());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @Test
    public void plusSeconds_long_overflowTooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Instant t = Instant.ofEpochSecond(1, 0);
            t.plusSeconds(Long.MAX_VALUE);
        });
    }

    @Test
    public void plusSeconds_long_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Instant t = Instant.ofEpochSecond(-1, 0);
            t.plusSeconds(Long.MIN_VALUE);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_plusMillis_long() {
        return new Object[][] {
                {0, 0, 0,       0, 0},
                {0, 0, 1,       0, 1000000},
                {0, 0, 999,     0, 999000000},
                {0, 0, 1000,    1, 0},
                {0, 0, 1001,    1, 1000000},
                {0, 0, 1999,    1, 999000000},
                {0, 0, 2000,    2, 0},
                {0, 0, -1,      -1, 999000000},
                {0, 0, -999,    -1, 1000000},
                {0, 0, -1000,   -1, 0},
                {0, 0, -1001,   -2, 999000000},
                {0, 0, -1999,   -2, 1000000},

                {0, 1, 0,       0, 1},
                {0, 1, 1,       0, 1000001},
                {0, 1, 998,     0, 998000001},
                {0, 1, 999,     0, 999000001},
                {0, 1, 1000,    1, 1},
                {0, 1, 1998,    1, 998000001},
                {0, 1, 1999,    1, 999000001},
                {0, 1, 2000,    2, 1},
                {0, 1, -1,      -1, 999000001},
                {0, 1, -2,      -1, 998000001},
                {0, 1, -1000,   -1, 1},
                {0, 1, -1001,   -2, 999000001},

                {0, 1000000, 0,       0, 1000000},
                {0, 1000000, 1,       0, 2000000},
                {0, 1000000, 998,     0, 999000000},
                {0, 1000000, 999,     1, 0},
                {0, 1000000, 1000,    1, 1000000},
                {0, 1000000, 1998,    1, 999000000},
                {0, 1000000, 1999,    2, 0},
                {0, 1000000, 2000,    2, 1000000},
                {0, 1000000, -1,      0, 0},
                {0, 1000000, -2,      -1, 999000000},
                {0, 1000000, -999,    -1, 2000000},
                {0, 1000000, -1000,   -1, 1000000},
                {0, 1000000, -1001,   -1, 0},
                {0, 1000000, -1002,   -2, 999000000},

                {0, 999999999, 0,     0, 999999999},
                {0, 999999999, 1,     1, 999999},
                {0, 999999999, 999,   1, 998999999},
                {0, 999999999, 1000,  1, 999999999},
                {0, 999999999, 1001,  2, 999999},
                {0, 999999999, -1,    0, 998999999},
                {0, 999999999, -1000, -1, 999999999},
                {0, 999999999, -1001, -1, 998999999},

                {0, 0, Long.MAX_VALUE, Long.MAX_VALUE / 1000, (int) (Long.MAX_VALUE % 1000) * 1000000},
                {0, 0, Long.MIN_VALUE, Long.MIN_VALUE / 1000 - 1, (int) (Long.MIN_VALUE % 1000) * 1000000 + 1000000000},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_plusMillis_long")
    public void plusMillis_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.ofEpochSecond(seconds, nanos);
        t = t.plusMillis(amount);
        assertEquals(expectedSeconds, t.getEpochSecond());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }
    @ParameterizedTest
    @MethodSource("provider_plusMillis_long")
    public void plusMillis_long_oneMore(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.ofEpochSecond(seconds + 1, nanos);
        t = t.plusMillis(amount);
        assertEquals(expectedSeconds + 1, t.getEpochSecond());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }
    @ParameterizedTest
    @MethodSource("provider_plusMillis_long")
    public void plusMillis_long_minusOneLess(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.ofEpochSecond(seconds - 1, nanos);
        t = t.plusMillis(amount);
        assertEquals(expectedSeconds - 1, t.getEpochSecond());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @Test
    public void plusMillis_long_max() {
        Instant t = Instant.ofEpochSecond(MAX_SECOND, 998999999);
        t = t.plusMillis(1);
        assertEquals(MAX_SECOND, t.getEpochSecond());
        assertEquals(999999999, t.getNano());
    }

    @Test
    public void plusMillis_long_overflowTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant t = Instant.ofEpochSecond(MAX_SECOND, 999000000);
            t.plusMillis(1);
        });
    }

    @Test
    public void plusMillis_long_min() {
        Instant t = Instant.ofEpochSecond(MIN_SECOND, 1000000);
        t = t.plusMillis(-1);
        assertEquals(MIN_SECOND, t.getEpochSecond());
        assertEquals(0, t.getNano());
    }

    @Test
    public void plusMillis_long_overflowTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant t = Instant.ofEpochSecond(MIN_SECOND, 0);
            t.plusMillis(-1);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_plusNanos_long() {
        return new Object[][] {
                {0, 0, 0,           0, 0},
                {0, 0, 1,           0, 1},
                {0, 0, 999999999,   0, 999999999},
                {0, 0, 1000000000,  1, 0},
                {0, 0, 1000000001,  1, 1},
                {0, 0, 1999999999,  1, 999999999},
                {0, 0, 2000000000,  2, 0},
                {0, 0, -1,          -1, 999999999},
                {0, 0, -999999999,  -1, 1},
                {0, 0, -1000000000, -1, 0},
                {0, 0, -1000000001, -2, 999999999},
                {0, 0, -1999999999, -2, 1},

                {1, 0, 0,           1, 0},
                {1, 0, 1,           1, 1},
                {1, 0, 999999999,   1, 999999999},
                {1, 0, 1000000000,  2, 0},
                {1, 0, 1000000001,  2, 1},
                {1, 0, 1999999999,  2, 999999999},
                {1, 0, 2000000000,  3, 0},
                {1, 0, -1,          0, 999999999},
                {1, 0, -999999999,  0, 1},
                {1, 0, -1000000000, 0, 0},
                {1, 0, -1000000001, -1, 999999999},
                {1, 0, -1999999999, -1, 1},

                {-1, 0, 0,           -1, 0},
                {-1, 0, 1,           -1, 1},
                {-1, 0, 999999999,   -1, 999999999},
                {-1, 0, 1000000000,  0, 0},
                {-1, 0, 1000000001,  0, 1},
                {-1, 0, 1999999999,  0, 999999999},
                {-1, 0, 2000000000,  1, 0},
                {-1, 0, -1,          -2, 999999999},
                {-1, 0, -999999999,  -2, 1},
                {-1, 0, -1000000000, -2, 0},
                {-1, 0, -1000000001, -3, 999999999},
                {-1, 0, -1999999999, -3, 1},

                {1, 1, 0,           1, 1},
                {1, 1, 1,           1, 2},
                {1, 1, 999999998,   1, 999999999},
                {1, 1, 999999999,   2, 0},
                {1, 1, 1000000000,  2, 1},
                {1, 1, 1999999998,  2, 999999999},
                {1, 1, 1999999999,  3, 0},
                {1, 1, 2000000000,  3, 1},
                {1, 1, -1,          1, 0},
                {1, 1, -2,          0, 999999999},
                {1, 1, -1000000000, 0, 1},
                {1, 1, -1000000001, 0, 0},
                {1, 1, -1000000002, -1, 999999999},
                {1, 1, -2000000000, -1, 1},

                {1, 999999999, 0,           1, 999999999},
                {1, 999999999, 1,           2, 0},
                {1, 999999999, 999999999,   2, 999999998},
                {1, 999999999, 1000000000,  2, 999999999},
                {1, 999999999, 1000000001,  3, 0},
                {1, 999999999, -1,          1, 999999998},
                {1, 999999999, -1000000000, 0, 999999999},
                {1, 999999999, -1000000001, 0, 999999998},
                {1, 999999999, -1999999999, 0, 0},
                {1, 999999999, -2000000000, -1, 999999999},

                {MAX_SECOND, 0, 999999999, MAX_SECOND, 999999999},
                {MAX_SECOND - 1, 0, 1999999999, MAX_SECOND, 999999999},
                {MIN_SECOND, 1, -1, MIN_SECOND, 0},
                {MIN_SECOND + 1, 1, -1000000001, MIN_SECOND, 0},

                {0, 0, MAX_SECOND, MAX_SECOND / 1000000000, (int) (MAX_SECOND % 1000000000)},
                {0, 0, MIN_SECOND, MIN_SECOND / 1000000000 - 1, (int) (MIN_SECOND % 1000000000) + 1000000000},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_plusNanos_long")
    public void plusNanos_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.ofEpochSecond(seconds, nanos);
        t = t.plusNanos(amount);
        assertEquals(expectedSeconds, t.getEpochSecond());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @Test
    public void plusNanos_long_overflowTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant t = Instant.ofEpochSecond(MAX_SECOND, 999999999);
            t.plusNanos(1);
        });
    }

    @Test
    public void plusNanos_long_overflowTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant t = Instant.ofEpochSecond(MIN_SECOND, 0);
            t.plusNanos(-1);
        });
    }

    Object[][] provider_plusSaturating() {
        return new Object[][]{
                // 1. {edge or constant instants} x {edge or constant durations}
                {Instant.MIN, Duration.ofSeconds(Long.MIN_VALUE, 0), Optional.of(Instant.MIN)},
                {Instant.MIN, Duration.ofSeconds(Long.MIN_VALUE, 0), Optional.of(Instant.MIN)},
                {Instant.MIN, Duration.ZERO, Optional.empty()},
                {Instant.MIN, Duration.ofSeconds(Long.MAX_VALUE, 999_999_999), Optional.of(Instant.MAX)},
                {Instant.EPOCH, Duration.ofSeconds(Long.MIN_VALUE, 0), Optional.of(Instant.MIN)},
                {Instant.EPOCH, Duration.ZERO, Optional.empty()},
                {Instant.EPOCH, Duration.ofSeconds(Long.MAX_VALUE, 999_999_999), Optional.of(Instant.MAX)},
                {Instant.MAX, Duration.ofSeconds(Long.MIN_VALUE, 0), Optional.of(Instant.MIN)},
                {Instant.MAX, Duration.ZERO, Optional.empty()},
                {Instant.MAX, Duration.ofSeconds(Long.MAX_VALUE, 999_999_999), Optional.of(Instant.MAX)},
                // 2. {edge or constant instants} x {normal durations}
                {Instant.MIN, Duration.ofDays(-32), Optional.of(Instant.MIN)},
                {Instant.MIN, Duration.ofDays(32), Optional.empty()},
                {Instant.EPOCH, Duration.ofDays(-32), Optional.empty()},
                {Instant.EPOCH, Duration.ofDays(32), Optional.empty()},
                {Instant.MAX, Duration.ofDays(-32), Optional.empty()},
                {Instant.MAX, Duration.ofDays(32), Optional.of(Instant.MAX)},
                // 3. {normal instants with both positive and negative epoch seconds} x {edge or constant durations}
                {Instant.parse("1950-01-01T00:00:00Z"), Duration.ofSeconds(Long.MIN_VALUE, 0), Optional.of(Instant.MIN)},
                {Instant.parse("1950-01-01T00:00:00Z"), Duration.ZERO, Optional.empty()},
                {Instant.parse("1950-01-01T00:00:00Z"), Duration.ofSeconds(Long.MAX_VALUE, 999_999_999), Optional.of(Instant.MAX)},
                {Instant.parse("1990-01-01T00:00:00Z"), Duration.ofSeconds(Long.MIN_VALUE, 0), Optional.of(Instant.MIN)},
                {Instant.parse("1990-01-01T00:00:00Z"), Duration.ZERO, Optional.empty()},
                {Instant.parse("1990-01-01T00:00:00Z"), Duration.ofSeconds(Long.MAX_VALUE, 999_999_999), Optional.of(Instant.MAX)},
                // 4. {normal instants with both positive and negative epoch seconds} x {normal durations}
                {Instant.parse("1950-01-01T00:00:00Z"), Duration.ofDays(-32), Optional.empty()},
                {Instant.parse("1950-01-01T00:00:00Z"), Duration.ofDays(32), Optional.empty()},
                {Instant.parse("1990-01-01T00:00:00Z"), Duration.ofDays(-32), Optional.empty()},
                {Instant.parse("1990-01-01T00:00:00Z"), Duration.ofDays(32), Optional.empty()},
                // 5. instant boundary
                {Instant.MIN, Duration.between(Instant.MIN, Instant.MAX), Optional.of(Instant.MAX)},
                {Instant.EPOCH, Duration.between(Instant.EPOCH, Instant.MAX), Optional.of(Instant.MAX)},
                {Instant.EPOCH, Duration.between(Instant.EPOCH, Instant.MIN), Optional.of(Instant.MIN)},
                {Instant.MAX, Duration.between(Instant.MAX, Instant.MIN), Optional.of(Instant.MIN)}
        };
    }

    @ParameterizedTest
    @MethodSource("provider_plusSaturating")
    public void plusSaturating(Instant i, Duration d, Optional<Instant> value) {
        var actual = i.plusSaturating(d);
        try {
            assertEquals(i.plus(d), actual);
            // If `value` is present, perform an additional check. It may be
            // important to ensure that not only does the result of `plusSaturating`
            // match that of `plus`, but that it also matches our expectation.
            // Because if it doesn’t, then the test isn’t testing what we think
            // it is, and needs to be fixed.
            value.ifPresent(instant -> assertEquals(instant, actual));
        } catch (DateTimeException /* instant overflow */
                 | ArithmeticException /* long overflow */ e) {
            if (value.isEmpty()) {
                throw new AssertionError();
            }
            assertEquals(value.get(), actual);
        }
    }

    Object[][] provider_plusSaturating_null() {
        return new Object[][]{
                {Instant.MIN},
                {Instant.EPOCH},
                {Instant.MAX},
                // any non-random but also non-special instant
                {Instant.parse("2025-10-13T20:47:50.369955Z")},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_plusSaturating_null")
    public void test_plusSaturating_null(Instant i) {
        Assertions.assertThrows(NullPointerException.class, () -> i.plusSaturating(null));
    }

    //-----------------------------------------------------------------------
    Object[][] provider_minus() {
        return new Object[][] {
                {MIN_SECOND, 0, MIN_SECOND, 0, 0, 0},

                {MIN_SECOND, 0, -1, 0, MIN_SECOND + 1, 0},
                {MIN_SECOND, 0, 0, -500, MIN_SECOND, 500},
                {MIN_SECOND, 0, 0, -1000000000, MIN_SECOND + 1, 0},

                {MIN_SECOND + 1, 0, 1, 0, MIN_SECOND, 0},
                {MIN_SECOND + 1, 0, 0, 500, MIN_SECOND, 999999500},
                {MIN_SECOND + 1, 0, 0, 1000000000, MIN_SECOND, 0},

                {-4, 666666667, -4, 666666667,  0,         0},
                {-4, 666666667, -3,         0, -1, 666666667},
                {-4, 666666667, -2,         0, -2, 666666667},
                {-4, 666666667, -1,         0, -3, 666666667},
                {-4, 666666667, -1, 333333334, -3, 333333333},
                {-4, 666666667, -1, 666666667, -3,         0},
                {-4, 666666667, -1, 999999999, -4, 666666668},
                {-4, 666666667,  0,         0, -4, 666666667},
                {-4, 666666667,  0,         1, -4, 666666666},
                {-4, 666666667,  0, 333333333, -4, 333333334},
                {-4, 666666667,  0, 666666666, -4,         1},
                {-4, 666666667,  1,         0, -5, 666666667},
                {-4, 666666667,  2,         0, -6, 666666667},
                {-4, 666666667,  3,         0, -7, 666666667},
                {-4, 666666667,  3, 333333333, -7, 333333334},

                {-3, 0, -4, 666666667,  0, 333333333},
                {-3, 0, -3,         0,  0,         0},
                {-3, 0, -2,         0, -1,         0},
                {-3, 0, -1,         0, -2,         0},
                {-3, 0, -1, 333333334, -3, 666666666},
                {-3, 0, -1, 666666667, -3, 333333333},
                {-3, 0, -1, 999999999, -3,         1},
                {-3, 0,  0,         0, -3,         0},
                {-3, 0,  0,         1, -4, 999999999},
                {-3, 0,  0, 333333333, -4, 666666667},
                {-3, 0,  0, 666666666, -4, 333333334},
                {-3, 0,  1,         0, -4,         0},
                {-3, 0,  2,         0, -5,         0},
                {-3, 0,  3,         0, -6,         0},
                {-3, 0,  3, 333333333, -7, 666666667},

                {-2, 0, -4, 666666667,  1, 333333333},
                {-2, 0, -3,         0,  1,         0},
                {-2, 0, -2,         0,  0,         0},
                {-2, 0, -1,         0, -1,         0},
                {-2, 0, -1, 333333334, -2, 666666666},
                {-2, 0, -1, 666666667, -2, 333333333},
                {-2, 0, -1, 999999999, -2,         1},
                {-2, 0,  0,         0, -2,         0},
                {-2, 0,  0,         1, -3, 999999999},
                {-2, 0,  0, 333333333, -3, 666666667},
                {-2, 0,  0, 666666666, -3, 333333334},
                {-2, 0,  1,         0, -3,         0},
                {-2, 0,  2,         0, -4,         0},
                {-2, 0,  3,         0, -5,         0},
                {-2, 0,  3, 333333333, -6, 666666667},

                {-1, 0, -4, 666666667,  2, 333333333},
                {-1, 0, -3,         0,  2,         0},
                {-1, 0, -2,         0,  1,         0},
                {-1, 0, -1,         0,  0,         0},
                {-1, 0, -1, 333333334, -1, 666666666},
                {-1, 0, -1, 666666667, -1, 333333333},
                {-1, 0, -1, 999999999, -1,         1},
                {-1, 0,  0,         0, -1,         0},
                {-1, 0,  0,         1, -2, 999999999},
                {-1, 0,  0, 333333333, -2, 666666667},
                {-1, 0,  0, 666666666, -2, 333333334},
                {-1, 0,  1,         0, -2,         0},
                {-1, 0,  2,         0, -3,         0},
                {-1, 0,  3,         0, -4,         0},
                {-1, 0,  3, 333333333, -5, 666666667},

                {-1, 666666667, -4, 666666667,  3,         0},
                {-1, 666666667, -3,         0,  2, 666666667},
                {-1, 666666667, -2,         0,  1, 666666667},
                {-1, 666666667, -1,         0,  0, 666666667},
                {-1, 666666667, -1, 333333334,  0, 333333333},
                {-1, 666666667, -1, 666666667,  0,         0},
                {-1, 666666667, -1, 999999999, -1, 666666668},
                {-1, 666666667,  0,         0, -1, 666666667},
                {-1, 666666667,  0,         1, -1, 666666666},
                {-1, 666666667,  0, 333333333, -1, 333333334},
                {-1, 666666667,  0, 666666666, -1,         1},
                {-1, 666666667,  1,         0, -2, 666666667},
                {-1, 666666667,  2,         0, -3, 666666667},
                {-1, 666666667,  3,         0, -4, 666666667},
                {-1, 666666667,  3, 333333333, -4, 333333334},

                {0, 0, -4, 666666667,  3, 333333333},
                {0, 0, -3,         0,  3,         0},
                {0, 0, -2,         0,  2,         0},
                {0, 0, -1,         0,  1,         0},
                {0, 0, -1, 333333334,  0, 666666666},
                {0, 0, -1, 666666667,  0, 333333333},
                {0, 0, -1, 999999999,  0,         1},
                {0, 0,  0,         0,  0,         0},
                {0, 0,  0,         1, -1, 999999999},
                {0, 0,  0, 333333333, -1, 666666667},
                {0, 0,  0, 666666666, -1, 333333334},
                {0, 0,  1,         0, -1,         0},
                {0, 0,  2,         0, -2,         0},
                {0, 0,  3,         0, -3,         0},
                {0, 0,  3, 333333333, -4, 666666667},

                {0, 333333333, -4, 666666667,  3, 666666666},
                {0, 333333333, -3,         0,  3, 333333333},
                {0, 333333333, -2,         0,  2, 333333333},
                {0, 333333333, -1,         0,  1, 333333333},
                {0, 333333333, -1, 333333334,  0, 999999999},
                {0, 333333333, -1, 666666667,  0, 666666666},
                {0, 333333333, -1, 999999999,  0, 333333334},
                {0, 333333333,  0,         0,  0, 333333333},
                {0, 333333333,  0,         1,  0, 333333332},
                {0, 333333333,  0, 333333333,  0,         0},
                {0, 333333333,  0, 666666666, -1, 666666667},
                {0, 333333333,  1,         0, -1, 333333333},
                {0, 333333333,  2,         0, -2, 333333333},
                {0, 333333333,  3,         0, -3, 333333333},
                {0, 333333333,  3, 333333333, -3,         0},

                {1, 0, -4, 666666667,  4, 333333333},
                {1, 0, -3,         0,  4,         0},
                {1, 0, -2,         0,  3,         0},
                {1, 0, -1,         0,  2,         0},
                {1, 0, -1, 333333334,  1, 666666666},
                {1, 0, -1, 666666667,  1, 333333333},
                {1, 0, -1, 999999999,  1,         1},
                {1, 0,  0,         0,  1,         0},
                {1, 0,  0,         1,  0, 999999999},
                {1, 0,  0, 333333333,  0, 666666667},
                {1, 0,  0, 666666666,  0, 333333334},
                {1, 0,  1,         0,  0,         0},
                {1, 0,  2,         0, -1,         0},
                {1, 0,  3,         0, -2,         0},
                {1, 0,  3, 333333333, -3, 666666667},

                {2, 0, -4, 666666667,  5, 333333333},
                {2, 0, -3,         0,  5,         0},
                {2, 0, -2,         0,  4,         0},
                {2, 0, -1,         0,  3,         0},
                {2, 0, -1, 333333334,  2, 666666666},
                {2, 0, -1, 666666667,  2, 333333333},
                {2, 0, -1, 999999999,  2,         1},
                {2, 0,  0,         0,  2,         0},
                {2, 0,  0,         1,  1, 999999999},
                {2, 0,  0, 333333333,  1, 666666667},
                {2, 0,  0, 666666666,  1, 333333334},
                {2, 0,  1,         0,  1,         0},
                {2, 0,  2,         0,  0,         0},
                {2, 0,  3,         0, -1,         0},
                {2, 0,  3, 333333333, -2, 666666667},

                {3, 0, -4, 666666667,  6, 333333333},
                {3, 0, -3,         0,  6,         0},
                {3, 0, -2,         0,  5,         0},
                {3, 0, -1,         0,  4,         0},
                {3, 0, -1, 333333334,  3, 666666666},
                {3, 0, -1, 666666667,  3, 333333333},
                {3, 0, -1, 999999999,  3,         1},
                {3, 0,  0,         0,  3,         0},
                {3, 0,  0,         1,  2, 999999999},
                {3, 0,  0, 333333333,  2, 666666667},
                {3, 0,  0, 666666666,  2, 333333334},
                {3, 0,  1,         0,  2,         0},
                {3, 0,  2,         0,  1,         0},
                {3, 0,  3,         0,  0,         0},
                {3, 0,  3, 333333333, -1, 666666667},

                {3, 333333333, -4, 666666667,  6, 666666666},
                {3, 333333333, -3,         0,  6, 333333333},
                {3, 333333333, -2,         0,  5, 333333333},
                {3, 333333333, -1,         0,  4, 333333333},
                {3, 333333333, -1, 333333334,  3, 999999999},
                {3, 333333333, -1, 666666667,  3, 666666666},
                {3, 333333333, -1, 999999999,  3, 333333334},
                {3, 333333333,  0,         0,  3, 333333333},
                {3, 333333333,  0,         1,  3, 333333332},
                {3, 333333333,  0, 333333333,  3,         0},
                {3, 333333333,  0, 666666666,  2, 666666667},
                {3, 333333333,  1,         0,  2, 333333333},
                {3, 333333333,  2,         0,  1, 333333333},
                {3, 333333333,  3,         0,  0, 333333333},
                {3, 333333333,  3, 333333333,  0,         0},

                {MAX_SECOND - 1, 0, -1, 0, MAX_SECOND, 0},
                {MAX_SECOND - 1, 0, 0, -500, MAX_SECOND - 1, 500},
                {MAX_SECOND - 1, 0, 0, -1000000000, MAX_SECOND, 0},

                {MAX_SECOND, 0, 1, 0, MAX_SECOND - 1, 0},
                {MAX_SECOND, 0, 0, 500, MAX_SECOND - 1, 999999500},
                {MAX_SECOND, 0, 0, 1000000000, MAX_SECOND - 1, 0},

                {MAX_SECOND, 0, MAX_SECOND, 0, 0, 0},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_minus")
    public void minus_Duration(long seconds, int nanos, long otherSeconds, int otherNanos, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds, nanos).minus(Duration.ofSeconds(otherSeconds, otherNanos));
        assertEquals(expectedSeconds, i.getEpochSecond());
        assertEquals(expectedNanoOfSecond, i.getNano());
    }

    @Test
    public void minus_Duration_overflowTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant i = Instant.ofEpochSecond(MIN_SECOND);
            i.minus(Duration.ofSeconds(0, 1));
        });
    }

    @Test
    public void minus_Duration_overflowTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant i = Instant.ofEpochSecond(MAX_SECOND, 999999999);
            i.minus(Duration.ofSeconds(-1, 999999999));
        });
    }

    //-----------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("provider_minus")
    public void minus_longTemporalUnit(long seconds, int nanos, long otherSeconds, int otherNanos, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds, nanos).minus(otherSeconds, SECONDS).minus(otherNanos, NANOS);
        assertEquals(expectedSeconds, i.getEpochSecond());
        assertEquals(expectedNanoOfSecond, i.getNano());
    }

    @Test
    public void minus_longTemporalUnit_overflowTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant i = Instant.ofEpochSecond(MIN_SECOND);
            i.minus(1, NANOS);
        });
    }

    @Test
    public void minus_longTemporalUnit_overflowTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant i = Instant.ofEpochSecond(MAX_SECOND, 999999999);
            i.minus(999999999, NANOS);
            i.minus(-1, SECONDS);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_minusSeconds_long() {
        return new Object[][] {
                {0, 0, 0, 0, 0},
                {0, 0, 1, -1, 0},
                {0, 0, -1, 1, 0},
                {0, 0, -MIN_SECOND, MIN_SECOND, 0},
                {1, 0, 0, 1, 0},
                {1, 0, 1, 0, 0},
                {1, 0, -1, 2, 0},
                {1, 0, -MIN_SECOND + 1, MIN_SECOND, 0},
                {1, 1, 0, 1, 1},
                {1, 1, 1, 0, 1},
                {1, 1, -1, 2, 1},
                {1, 1, -MIN_SECOND, MIN_SECOND + 1, 1},
                {1, 1, -MIN_SECOND + 1, MIN_SECOND, 1},
                {-1, 1, 0, -1, 1},
                {-1, 1, 1, -2, 1},
                {-1, 1, -1, 0, 1},
                {-1, 1, -MAX_SECOND, MAX_SECOND - 1, 1},
                {-1, 1, -(MAX_SECOND + 1), MAX_SECOND, 1},

                {MIN_SECOND, 2, MIN_SECOND, 0, 2},
                {MIN_SECOND + 1, 2, MIN_SECOND, 1, 2},
                {MAX_SECOND - 1, 2, MAX_SECOND, -1, 2},
                {MAX_SECOND, 2, MAX_SECOND, 0, 2},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_minusSeconds_long")
    public void minusSeconds_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds, nanos);
        i = i.minusSeconds(amount);
        assertEquals(expectedSeconds, i.getEpochSecond());
        assertEquals(expectedNanoOfSecond, i.getNano());
    }

    @Test
    public void minusSeconds_long_overflowTooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Instant i = Instant.ofEpochSecond(1, 0);
            i.minusSeconds(Long.MIN_VALUE + 1);
        });
    }

    @Test
    public void minusSeconds_long_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Instant i = Instant.ofEpochSecond(-2, 0);
            i.minusSeconds(Long.MAX_VALUE);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_minusMillis_long() {
        return new Object[][] {
                {0, 0, 0,       0, 0},
                {0, 0, 1,      -1, 999000000},
                {0, 0, 999,    -1, 1000000},
                {0, 0, 1000,   -1, 0},
                {0, 0, 1001,   -2, 999000000},
                {0, 0, 1999,   -2, 1000000},
                {0, 0, 2000,   -2, 0},
                {0, 0, -1,      0, 1000000},
                {0, 0, -999,    0, 999000000},
                {0, 0, -1000,   1, 0},
                {0, 0, -1001,   1, 1000000},
                {0, 0, -1999,   1, 999000000},

                {0, 1, 0,       0, 1},
                {0, 1, 1,      -1, 999000001},
                {0, 1, 998,    -1, 2000001},
                {0, 1, 999,    -1, 1000001},
                {0, 1, 1000,   -1, 1},
                {0, 1, 1998,   -2, 2000001},
                {0, 1, 1999,   -2, 1000001},
                {0, 1, 2000,   -2, 1},
                {0, 1, -1,      0, 1000001},
                {0, 1, -2,      0, 2000001},
                {0, 1, -1000,   1, 1},
                {0, 1, -1001,   1, 1000001},

                {0, 1000000, 0,       0, 1000000},
                {0, 1000000, 1,       0, 0},
                {0, 1000000, 998,    -1, 3000000},
                {0, 1000000, 999,    -1, 2000000},
                {0, 1000000, 1000,   -1, 1000000},
                {0, 1000000, 1998,   -2, 3000000},
                {0, 1000000, 1999,   -2, 2000000},
                {0, 1000000, 2000,   -2, 1000000},
                {0, 1000000, -1,      0, 2000000},
                {0, 1000000, -2,      0, 3000000},
                {0, 1000000, -999,    1, 0},
                {0, 1000000, -1000,   1, 1000000},
                {0, 1000000, -1001,   1, 2000000},
                {0, 1000000, -1002,   1, 3000000},

                {0, 999999999, 0,     0, 999999999},
                {0, 999999999, 1,     0, 998999999},
                {0, 999999999, 999,   0, 999999},
                {0, 999999999, 1000, -1, 999999999},
                {0, 999999999, 1001, -1, 998999999},
                {0, 999999999, -1,    1, 999999},
                {0, 999999999, -1000, 1, 999999999},
                {0, 999999999, -1001, 2, 999999},

                {0, 0, Long.MAX_VALUE, -(Long.MAX_VALUE / 1000) - 1, (int) -(Long.MAX_VALUE % 1000) * 1000000 + 1000000000},
                {0, 0, Long.MIN_VALUE, -(Long.MIN_VALUE / 1000), (int) -(Long.MIN_VALUE % 1000) * 1000000},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_minusMillis_long")
    public void minusMillis_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds, nanos);
        i = i.minusMillis(amount);
        assertEquals(expectedSeconds, i.getEpochSecond());
        assertEquals(expectedNanoOfSecond, i.getNano());
    }

    @ParameterizedTest
    @MethodSource("provider_minusMillis_long")
    public void minusMillis_long_oneMore(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds + 1, nanos);
        i = i.minusMillis(amount);
        assertEquals(expectedSeconds + 1, i.getEpochSecond());
        assertEquals(expectedNanoOfSecond, i.getNano());
    }

    @ParameterizedTest
    @MethodSource("provider_minusMillis_long")
    public void minusMillis_long_minusOneLess(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds - 1, nanos);
        i = i.minusMillis(amount);
        assertEquals(expectedSeconds - 1, i.getEpochSecond());
        assertEquals(expectedNanoOfSecond, i.getNano());
    }

    @Test
    public void minusMillis_long_max() {
        Instant i = Instant.ofEpochSecond(MAX_SECOND, 998999999);
        i = i.minusMillis(-1);
        assertEquals(MAX_SECOND, i.getEpochSecond());
        assertEquals(999999999, i.getNano());
    }

    @Test
    public void minusMillis_long_overflowTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant i = Instant.ofEpochSecond(MAX_SECOND, 999000000);
            i.minusMillis(-1);
        });
    }

    @Test
    public void minusMillis_long_min() {
        Instant i = Instant.ofEpochSecond(MIN_SECOND, 1000000);
        i = i.minusMillis(1);
        assertEquals(MIN_SECOND, i.getEpochSecond());
        assertEquals(0, i.getNano());
    }

    @Test
    public void minusMillis_long_overflowTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant i = Instant.ofEpochSecond(MIN_SECOND, 0);
            i.minusMillis(1);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_minusNanos_long() {
        return new Object[][] {
                {0, 0, 0,           0, 0},
                {0, 0, 1,          -1, 999999999},
                {0, 0, 999999999,  -1, 1},
                {0, 0, 1000000000, -1, 0},
                {0, 0, 1000000001, -2, 999999999},
                {0, 0, 1999999999, -2, 1},
                {0, 0, 2000000000, -2, 0},
                {0, 0, -1,          0, 1},
                {0, 0, -999999999,  0, 999999999},
                {0, 0, -1000000000, 1, 0},
                {0, 0, -1000000001, 1, 1},
                {0, 0, -1999999999, 1, 999999999},

                {1, 0, 0,            1, 0},
                {1, 0, 1,            0, 999999999},
                {1, 0, 999999999,    0, 1},
                {1, 0, 1000000000,   0, 0},
                {1, 0, 1000000001,  -1, 999999999},
                {1, 0, 1999999999,  -1, 1},
                {1, 0, 2000000000,  -1, 0},
                {1, 0, -1,           1, 1},
                {1, 0, -999999999,   1, 999999999},
                {1, 0, -1000000000,  2, 0},
                {1, 0, -1000000001,  2, 1},
                {1, 0, -1999999999,  2, 999999999},

                {-1, 0, 0,           -1, 0},
                {-1, 0, 1,           -2, 999999999},
                {-1, 0, 999999999,   -2, 1},
                {-1, 0, 1000000000,  -2, 0},
                {-1, 0, 1000000001,  -3, 999999999},
                {-1, 0, 1999999999,  -3, 1},
                {-1, 0, 2000000000,  -3, 0},
                {-1, 0, -1,          -1, 1},
                {-1, 0, -999999999,  -1, 999999999},
                {-1, 0, -1000000000,  0, 0},
                {-1, 0, -1000000001,  0, 1},
                {-1, 0, -1999999999,  0, 999999999},

                {1, 1, 0,           1, 1},
                {1, 1, 1,           1, 0},
                {1, 1, 999999998,   0, 3},
                {1, 1, 999999999,   0, 2},
                {1, 1, 1000000000,  0, 1},
                {1, 1, 1999999998, -1, 3},
                {1, 1, 1999999999, -1, 2},
                {1, 1, 2000000000, -1, 1},
                {1, 1, -1,          1, 2},
                {1, 1, -2,          1, 3},
                {1, 1, -1000000000, 2, 1},
                {1, 1, -1000000001, 2, 2},
                {1, 1, -1000000002, 2, 3},
                {1, 1, -2000000000, 3, 1},

                {1, 999999999, 0,           1, 999999999},
                {1, 999999999, 1,           1, 999999998},
                {1, 999999999, 999999999,   1, 0},
                {1, 999999999, 1000000000,  0, 999999999},
                {1, 999999999, 1000000001,  0, 999999998},
                {1, 999999999, -1,          2, 0},
                {1, 999999999, -1000000000, 2, 999999999},
                {1, 999999999, -1000000001, 3, 0},
                {1, 999999999, -1999999999, 3, 999999998},
                {1, 999999999, -2000000000, 3, 999999999},

                {MAX_SECOND, 0, -999999999, MAX_SECOND, 999999999},
                {MAX_SECOND - 1, 0, -1999999999, MAX_SECOND, 999999999},
                {MIN_SECOND, 1, 1, MIN_SECOND, 0},
                {MIN_SECOND + 1, 1, 1000000001, MIN_SECOND, 0},

                {0, 0, Long.MAX_VALUE, -(Long.MAX_VALUE / 1000000000) - 1, (int) -(Long.MAX_VALUE % 1000000000) + 1000000000},
                {0, 0, Long.MIN_VALUE, -(Long.MIN_VALUE / 1000000000), (int) -(Long.MIN_VALUE % 1000000000)},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_minusNanos_long")
    public void minusNanos_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds, nanos);
        i = i.minusNanos(amount);
        assertEquals(expectedSeconds, i.getEpochSecond());
        assertEquals(expectedNanoOfSecond, i.getNano());
    }

    @Test
    public void minusNanos_long_overflowTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant i = Instant.ofEpochSecond(MAX_SECOND, 999999999);
            i.minusNanos(-1);
        });
    }

    @Test
    public void minusNanos_long_overflowTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant i = Instant.ofEpochSecond(MIN_SECOND, 0);
            i.minusNanos(1);
        });
    }

    //-----------------------------------------------------------------------
    // until(Temporal, TemporalUnit)
    //-----------------------------------------------------------------------
    Object[][] data_periodUntilUnit() {
        return new Object[][] {
                {5, 650, -1, 650, SECONDS, -6},
                {5, 650, 0, 650, SECONDS, -5},
                {5, 650, 3, 650, SECONDS, -2},
                {5, 650, 4, 650, SECONDS, -1},
                {5, 650, 5, 650, SECONDS, 0},
                {5, 650, 6, 650, SECONDS, 1},
                {5, 650, 7, 650, SECONDS, 2},

                {5, 650, -1, 0, SECONDS, -6},
                {5, 650, 0, 0, SECONDS, -5},
                {5, 650, 3, 0, SECONDS, -2},
                {5, 650, 4, 0, SECONDS, -1},
                {5, 650, 5, 0, SECONDS, 0},
                {5, 650, 6, 0, SECONDS, 0},
                {5, 650, 7, 0, SECONDS, 1},

                {5, 650, -1, 950, SECONDS, -5},
                {5, 650, 0, 950, SECONDS, -4},
                {5, 650, 3, 950, SECONDS, -1},
                {5, 650, 4, 950, SECONDS, 0},
                {5, 650, 5, 950, SECONDS, 0},
                {5, 650, 6, 950, SECONDS, 1},
                {5, 650, 7, 950, SECONDS, 2},

                {5, 650, -1, 50, SECONDS, -6},
                {5, 650, 0, 50, SECONDS, -5},
                {5, 650, 4, 50, SECONDS, -1},
                {5, 650, 5, 50, SECONDS, 0},
                {5, 650, 6, 50, SECONDS, 0},
                {5, 650, 7, 50, SECONDS, 1},
                {5, 650, 8, 50, SECONDS, 2},

                {5, 650_000_000, 5, 650_999_666, MILLIS, 0L},
                {5, 999_777, 6, 0, MILLIS, 999L},
                {5, 999_888, 6, 1000000, MILLIS, 1000L},

                {5, 650_000_000, 3, 650_000_000, MILLIS, -2_000L},
                {5, 650_000_000, 4, 650_000_000, MILLIS, -1_000L},
                {5, 650_000_000, 5, 650_000_000, MILLIS, 0},
                {5, 650_000_000, 6, 650_000_000, MILLIS, 1_000L},
                {5, 650_000_000, 7, 650_000_000, MILLIS, 2_000L},

                {5, 650_000_000, 3, 0, MILLIS, -2_650L},
                {5, 650_000_000, 4, 0, MILLIS, -1_650L},
                {5, 650_000_000, 5, 0, MILLIS, -650L},
                {5, 650_000_000, 6, 0, MILLIS, 350L},
                {5, 650_000_000, 7, 0, MILLIS, 1_350L},

                {5, 650_000_000, -1, 950_000_000, MILLIS, -5_700L},
                {5, 650_000_000, 0, 950_000_000, MILLIS, -4_700L},
                {5, 650_000_000, 3, 950_000_000, MILLIS, -1_700L},
                {5, 650_000_000, 4, 950_000_000, MILLIS, -700L},
                {5, 650_000_000, 5, 950_000_000, MILLIS, 300L},
                {5, 650_000_000, 6, 950_000_000, MILLIS, 1_300L},
                {5, 650_000_000, 7, 950_000_000, MILLIS, 2_300L},

                {5, 650_000_000, 4, 50_000_000, MILLIS, -1_600L},
                {5, 650_000_000, 5, 50_000_000, MILLIS, -600L},
                {5, 650_000_000, 6, 50_000_000, MILLIS, 400L},
                {5, 650_000_000, 7, 50_000_000, MILLIS, 1_400L},
                {5, 650_000_000, 8, 50_000_000, MILLIS, 2_400L},

                {5, 650_000_000, 5, 650_999_000, MICROS, 999L},
                {0, 999_000, 1, 0, MICROS, 999_001L},
                {0, 999_000, 1, 1000000, MICROS, 1000_001L},

                {5, 650_000_000, 3, 650_000_000, MICROS, -2_000_000L},
                {5, 650_000_000, 4, 650_000_000, MICROS, -1_000_000L},
                {5, 650_000_000, 5, 650_000_000, MICROS, 0},
                {5, 650_000_000, 6, 650_000_000, MICROS, 1_000_000L},
                {5, 650_000_000, 7, 650_000_000, MICROS, 2_000_000L},

                {5, 650_000_000, 3, 0, MICROS, -2_650_000L},
                {5, 650_000_000, 4, 0, MICROS, -1_650_000L},
                {5, 650_000_000, 5, 0, MICROS, -650_000L},
                {5, 650_000_000, 6, 0, MICROS, 350_000L},
                {5, 650_000_000, 7, 0, MICROS, 1_350_000L},

                {5, 650_000_000, -1, 950_000_000, MICROS, -5_700_000L},
                {5, 650_000_000, 0, 950_000_000, MICROS, -4_700_000L},
                {5, 650_000_000, 3, 950_000_000, MICROS, -1_700_000L},
                {5, 650_000_000, 4, 950_000_000, MICROS, -700_000L},
                {5, 650_000_000, 5, 950_000_000, MICROS, 300_000L},
                {5, 650_000_000, 6, 950_000_000, MICROS, 1_300_000L},
                {5, 650_000_000, 7, 950_000_000, MICROS, 2_300_000L},

                {5, 650_000_000, 4, 50_000_000, MICROS, -1_600_000L},
                {5, 650_000_000, 5, 50_000_000, MICROS, -600_000L},
                {5, 650_000_000, 6, 50_000_000, MICROS, 400_000L},
                {5, 650_000_000, 7, 50_000_000, MICROS, 1_400_000L},
                {5, 650_000_000, 8, 50_000_000, MICROS, 2_400_000L},

                {5, 650_000_000, -1, 650_000_000, NANOS, -6_000_000_000L},
                {5, 650_000_000, 0, 650_000_000, NANOS, -5_000_000_000L},
                {5, 650_000_000, 3, 650_000_000, NANOS, -2_000_000_000L},
                {5, 650_000_000, 4, 650_000_000, NANOS, -1_000_000_000L},
                {5, 650_000_000, 5, 650_000_000, NANOS, 0},
                {5, 650_000_000, 6, 650_000_000, NANOS, 1_000_000_000L},
                {5, 650_000_000, 7, 650_000_000, NANOS, 2_000_000_000L},

                {5, 650_000_000, -1, 0, NANOS, -6_650_000_000L},
                {5, 650_000_000, 0, 0, NANOS, -5_650_000_000L},
                {5, 650_000_000, 3, 0, NANOS, -2_650_000_000L},
                {5, 650_000_000, 4, 0, NANOS, -1_650_000_000L},
                {5, 650_000_000, 5, 0, NANOS, -650_000_000L},
                {5, 650_000_000, 6, 0, NANOS, 350_000_000L},
                {5, 650_000_000, 7, 0, NANOS, 1_350_000_000L},

                {5, 650_000_000, -1, 950_000_000, NANOS, -5_700_000_000L},
                {5, 650_000_000, 0, 950_000_000, NANOS, -4_700_000_000L},
                {5, 650_000_000, 3, 950_000_000, NANOS, -1_700_000_000L},
                {5, 650_000_000, 4, 950_000_000, NANOS, -700_000_000L},
                {5, 650_000_000, 5, 950_000_000, NANOS, 300_000_000L},
                {5, 650_000_000, 6, 950_000_000, NANOS, 1_300_000_000L},
                {5, 650_000_000, 7, 950_000_000, NANOS, 2_300_000_000L},

                {5, 650_000_000, -1, 50_000_000, NANOS, -6_600_000_000L},
                {5, 650_000_000, 0, 50_000_000, NANOS, -5_600_000_000L},
                {5, 650_000_000, 4, 50_000_000, NANOS, -1_600_000_000L},
                {5, 650_000_000, 5, 50_000_000, NANOS, -600_000_000L},
                {5, 650_000_000, 6, 50_000_000, NANOS, 400_000_000L},
                {5, 650_000_000, 7, 50_000_000, NANOS, 1_400_000_000L},
                {5, 650_000_000, 8, 50_000_000, NANOS, 2_400_000_000L},

                {0, 0, -60, 0, MINUTES, -1L},
                {0, 0, -1, 999_999_999, MINUTES, 0L},
                {0, 0, 59, 0, MINUTES, 0L},
                {0, 0, 59, 999_999_999, MINUTES, 0L},
                {0, 0, 60, 0, MINUTES, 1L},
                {0, 0, 61, 0, MINUTES, 1L},

                {0, 0, -3600, 0, HOURS, -1L},
                {0, 0, -1, 999_999_999, HOURS, 0L},
                {0, 0, 3599, 0, HOURS, 0L},
                {0, 0, 3599, 999_999_999, HOURS, 0L},
                {0, 0, 3600, 0, HOURS, 1L},
                {0, 0, 3601, 0, HOURS, 1L},

                {0, 0, -86400, 0, DAYS, -1L},
                {0, 0, -1, 999_999_999, DAYS, 0L},
                {0, 0, 86399, 0, DAYS, 0L},
                {0, 0, 86399, 999_999_999, DAYS, 0L},
                {0, 0, 86400, 0, DAYS, 1L},
                {0, 0, 86401, 0, DAYS, 1L},
        };
    }

    @ParameterizedTest
    @MethodSource("data_periodUntilUnit")
    public void test_until_TemporalUnit(long seconds1, int nanos1, long seconds2, long nanos2, TemporalUnit unit, long expected) {
        Instant i1 = Instant.ofEpochSecond(seconds1, nanos1);
        Instant i2 = Instant.ofEpochSecond(seconds2, nanos2);
        long amount = i1.until(i2, unit);
        assertEquals(expected, amount);
    }

    @ParameterizedTest
    @MethodSource("data_periodUntilUnit")
    public void test_until_TemporalUnit_negated(long seconds1, int nanos1, long seconds2, long nanos2, TemporalUnit unit, long expected) {
        Instant i1 = Instant.ofEpochSecond(seconds1, nanos1);
        Instant i2 = Instant.ofEpochSecond(seconds2, nanos2);
        long amount = i2.until(i1, unit);
        assertEquals(-expected, amount);
    }

    @ParameterizedTest
    @MethodSource("data_periodUntilUnit")
    public void test_until_TemporalUnit_between(long seconds1, int nanos1, long seconds2, long nanos2, TemporalUnit unit, long expected) {
        Instant i1 = Instant.ofEpochSecond(seconds1, nanos1);
        Instant i2 = Instant.ofEpochSecond(seconds2, nanos2);
        long amount = unit.between(i1, i2);
        assertEquals(expected, amount);
    }

    @Test
    public void test_until_convertedType() {
        Instant start = Instant.ofEpochSecond(12, 3000);
        OffsetDateTime end = start.plusSeconds(2).atOffset(ZoneOffset.ofHours(2));
        assertEquals(2, start.until(end, SECONDS));
    }

    @Test
    public void test_until_invalidType() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant start = Instant.ofEpochSecond(12, 3000);
            start.until(LocalTime.of(11, 30), SECONDS);
        });
    }

    @Test
    public void test_until_TemporalUnit_unsupportedUnit() {
        Assertions.assertThrows(UnsupportedTemporalTypeException.class, () -> TEST_12345_123456789.until(TEST_12345_123456789, MONTHS));
    }

    @Test
    public void test_until_TemporalUnit_nullEnd() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12345_123456789.until(null, HOURS));
    }

    @Test
    public void test_until_TemporalUnit_nullUnit() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12345_123456789.until(TEST_12345_123456789, null));
    }

    //-----------------------------------------------------------------------
    // atOffset()
    //-----------------------------------------------------------------------
    @Test
    public void test_atOffset() {
        for (int i = 0; i < (24 * 60 * 60); i++) {
            Instant instant = Instant.ofEpochSecond(i);
            OffsetDateTime test = instant.atOffset(ZoneOffset.ofHours(1));
            assertEquals(1970, test.getYear());
            assertEquals(1, test.getMonthValue());
            assertEquals(1 + (i >= 23 * 60 * 60 ? 1 : 0), test.getDayOfMonth());
            assertEquals(((i / (60 * 60)) + 1) % 24, test.getHour());
            assertEquals((i / 60) % 60, test.getMinute());
            assertEquals(i % 60, test.getSecond());
        }
    }

    @Test
    public void test_atOffset_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12345_123456789.atOffset(null));
    }

    //-----------------------------------------------------------------------
    // atZone()
    //-----------------------------------------------------------------------
    @Test
    public void test_atZone() {
        for (int i = 0; i < (24 * 60 * 60); i++) {
            Instant instant = Instant.ofEpochSecond(i);
            ZonedDateTime test = instant.atZone(ZoneOffset.ofHours(1));
            assertEquals(1970, test.getYear());
            assertEquals(1, test.getMonthValue());
            assertEquals(1 + (i >= 23 * 60 * 60 ? 1 : 0), test.getDayOfMonth());
            assertEquals(((i / (60 * 60)) + 1) % 24, test.getHour());
            assertEquals((i / 60) % 60, test.getMinute());
            assertEquals(i % 60, test.getSecond());
        }
    }

    @Test
    public void test_atZone_null() {
        Assertions.assertThrows(NullPointerException.class, () -> TEST_12345_123456789.atZone(null));
    }

    //-----------------------------------------------------------------------
    // toEpochMilli()
    //-----------------------------------------------------------------------
    @Test
    public void test_toEpochMilli() {
        assertEquals(1001L, Instant.ofEpochSecond(1L, 1000000).toEpochMilli());
        assertEquals(1002L, Instant.ofEpochSecond(1L, 2000000).toEpochMilli());
        assertEquals(1000L, Instant.ofEpochSecond(1L, 567).toEpochMilli());
        assertEquals((Long.MAX_VALUE / 1000) * 1000, Instant.ofEpochSecond(Long.MAX_VALUE / 1000).toEpochMilli());
        assertEquals((Long.MIN_VALUE / 1000) * 1000, Instant.ofEpochSecond(Long.MIN_VALUE / 1000).toEpochMilli());
        assertEquals(-1L, Instant.ofEpochSecond(0L, -1000000).toEpochMilli());
        assertEquals(1, Instant.ofEpochSecond(0L, 1000000).toEpochMilli());
        assertEquals(0, Instant.ofEpochSecond(0L, 999999).toEpochMilli());
        assertEquals(0, Instant.ofEpochSecond(0L, 1).toEpochMilli());
        assertEquals(0, Instant.ofEpochSecond(0L, 0).toEpochMilli());
        assertEquals(-1L, Instant.ofEpochSecond(0L, -1).toEpochMilli());
        assertEquals(-1L, Instant.ofEpochSecond(0L, -999999).toEpochMilli());
        assertEquals(-1L, Instant.ofEpochSecond(0L, -1000000).toEpochMilli());
        assertEquals(-2L, Instant.ofEpochSecond(0L, -1000001).toEpochMilli());
    }

    @Test
    public void test_toEpochMilli_tooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> Instant.ofEpochSecond(Long.MAX_VALUE / 1000 + 1).toEpochMilli());
    }

    @Test
    public void test_toEpochMilli_tooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> Instant.ofEpochSecond(Long.MIN_VALUE / 1000 - 1).toEpochMilli());
    }

    @Test
    public void test_toEpochMillis_overflow() {
        Assertions.assertThrows(ArithmeticException.class, () -> Instant.ofEpochSecond(Long.MAX_VALUE / 1000, 809_000_000).toEpochMilli());
    }

    @Test
    public void test_toEpochMillis_overflow2() {
        Assertions.assertThrows(ArithmeticException.class, () -> Instant.ofEpochSecond(-9223372036854776L, 1).toEpochMilli());
    }

    //-----------------------------------------------------------------------
    // compareTo()
    //-----------------------------------------------------------------------
    @Test
    public void test_comparisons() {
        doTest_comparisons_Instant(
                Instant.ofEpochSecond(-2L, 0),
                Instant.ofEpochSecond(-2L, 999999998),
                Instant.ofEpochSecond(-2L, 999999999),
                Instant.ofEpochSecond(-1L, 0),
                Instant.ofEpochSecond(-1L, 1),
                Instant.ofEpochSecond(-1L, 999999998),
                Instant.ofEpochSecond(-1L, 999999999),
                Instant.ofEpochSecond(0L, 0),
                Instant.ofEpochSecond(0L, 1),
                Instant.ofEpochSecond(0L, 2),
                Instant.ofEpochSecond(0L, 999999999),
                Instant.ofEpochSecond(1L, 0),
                Instant.ofEpochSecond(2L, 0)
        );
    }

    void doTest_comparisons_Instant(Instant... instants) {
        for (int i = 0; i < instants.length; i++) {
            Instant a = instants[i];
            for (int j = 0; j < instants.length; j++) {
                Instant b = instants[j];
                if (i < j) {
                    assertEquals(true, a.compareTo(b) < 0, a + " <=> " + b);
                    assertEquals(true, a.isBefore(b), a + " <=> " + b);
                    assertEquals(false, a.isAfter(b), a + " <=> " + b);
                    assertEquals(false, a.equals(b), a + " <=> " + b);
                } else if (i > j) {
                    assertEquals(true, a.compareTo(b) > 0, a + " <=> " + b);
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
        Assertions.assertThrows(NullPointerException.class, () -> {
            Instant a = Instant.ofEpochSecond(0L, 0);
            a.compareTo(null);
        });
    }

    @Test
    public void test_isBefore_ObjectNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Instant a = Instant.ofEpochSecond(0L, 0);
            a.isBefore(null);
        });
    }

    @Test
    public void test_isAfter_ObjectNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Instant a = Instant.ofEpochSecond(0L, 0);
            a.isAfter(null);
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    public void compareToNonInstant() {
        Assertions.assertThrows(ClassCastException.class, () -> {
            Comparable c = Instant.ofEpochSecond(0L);
            c.compareTo(new Object());
        });
    }

    //-----------------------------------------------------------------------
    // equals()
    //-----------------------------------------------------------------------
    @Test
    public void test_equals() {
        Instant test5a = Instant.ofEpochSecond(5L, 20);
        Instant test5b = Instant.ofEpochSecond(5L, 20);
        Instant test5n = Instant.ofEpochSecond(5L, 30);
        Instant test6 = Instant.ofEpochSecond(6L, 20);

        assertEquals(true, test5a.equals(test5a));
        assertEquals(true, test5a.equals(test5b));
        assertEquals(false, test5a.equals(test5n));
        assertEquals(false, test5a.equals(test6));

        assertEquals(true, test5b.equals(test5a));
        assertEquals(true, test5b.equals(test5b));
        assertEquals(false, test5b.equals(test5n));
        assertEquals(false, test5b.equals(test6));

        assertEquals(false, test5n.equals(test5a));
        assertEquals(false, test5n.equals(test5b));
        assertEquals(true, test5n.equals(test5n));
        assertEquals(false, test5n.equals(test6));

        assertEquals(false, test6.equals(test5a));
        assertEquals(false, test6.equals(test5b));
        assertEquals(false, test6.equals(test5n));
        assertEquals(true, test6.equals(test6));
    }

    @Test
    public void test_equals_null() {
        Instant test5 = Instant.ofEpochSecond(5L, 20);
        assertEquals(false, test5.equals(null));
    }

    @Test
    public void test_equals_otherClass() {
        Instant test5 = Instant.ofEpochSecond(5L, 20);
        assertEquals(false, test5.equals(""));
    }

    //-----------------------------------------------------------------------
    // hashCode()
    //-----------------------------------------------------------------------
    @Test
    public void test_hashCode() {
        Instant test5a = Instant.ofEpochSecond(5L, 20);
        Instant test5b = Instant.ofEpochSecond(5L, 20);
        Instant test5n = Instant.ofEpochSecond(5L, 30);
        Instant test6 = Instant.ofEpochSecond(6L, 20);

        assertEquals(true, test5a.hashCode() == test5a.hashCode());
        assertEquals(true, test5a.hashCode() == test5b.hashCode());
        assertEquals(true, test5b.hashCode() == test5b.hashCode());

        assertEquals(false, test5a.hashCode() == test5n.hashCode());
        assertEquals(false, test5a.hashCode() == test6.hashCode());
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    Object[][] data_toString() {
        return new Object[][] {
                {Instant.ofEpochSecond(65L, 567), "1970-01-01T00:01:05.000000567Z"},
                {Instant.ofEpochSecond(65L, 560), "1970-01-01T00:01:05.000000560Z"},
                {Instant.ofEpochSecond(65L, 560000), "1970-01-01T00:01:05.000560Z"},
                {Instant.ofEpochSecond(65L, 560000000), "1970-01-01T00:01:05.560Z"},

                {Instant.ofEpochSecond(1, 0), "1970-01-01T00:00:01Z"},
                {Instant.ofEpochSecond(60, 0), "1970-01-01T00:01:00Z"},
                {Instant.ofEpochSecond(3600, 0), "1970-01-01T01:00:00Z"},
                {Instant.ofEpochSecond(-1, 0), "1969-12-31T23:59:59Z"},

                {LocalDateTime.of(0, 1, 2, 0, 0).toInstant(ZoneOffset.UTC), "0000-01-02T00:00:00Z"},
                {LocalDateTime.of(0, 1, 1, 12, 30).toInstant(ZoneOffset.UTC), "0000-01-01T12:30:00Z"},
                {LocalDateTime.of(0, 1, 1, 0, 0, 0, 1).toInstant(ZoneOffset.UTC), "0000-01-01T00:00:00.000000001Z"},
                {LocalDateTime.of(0, 1, 1, 0, 0).toInstant(ZoneOffset.UTC), "0000-01-01T00:00:00Z"},

                {LocalDateTime.of(-1, 12, 31, 23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC), "-0001-12-31T23:59:59.999999999Z"},
                {LocalDateTime.of(-1, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "-0001-12-31T12:30:00Z"},
                {LocalDateTime.of(-1, 12, 30, 12, 30).toInstant(ZoneOffset.UTC), "-0001-12-30T12:30:00Z"},

                {LocalDateTime.of(-9999, 1, 2, 12, 30).toInstant(ZoneOffset.UTC), "-9999-01-02T12:30:00Z"},
                {LocalDateTime.of(-9999, 1, 1, 12, 30).toInstant(ZoneOffset.UTC), "-9999-01-01T12:30:00Z"},
                {LocalDateTime.of(-9999, 1, 1, 0, 0).toInstant(ZoneOffset.UTC), "-9999-01-01T00:00:00Z"},

                {LocalDateTime.of(-10000, 12, 31, 23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC), "-10000-12-31T23:59:59.999999999Z"},
                {LocalDateTime.of(-10000, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "-10000-12-31T12:30:00Z"},
                {LocalDateTime.of(-10000, 12, 30, 12, 30).toInstant(ZoneOffset.UTC), "-10000-12-30T12:30:00Z"},
                {LocalDateTime.of(-15000, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "-15000-12-31T12:30:00Z"},

                {LocalDateTime.of(-19999, 1, 2, 12, 30).toInstant(ZoneOffset.UTC), "-19999-01-02T12:30:00Z"},
                {LocalDateTime.of(-19999, 1, 1, 12, 30).toInstant(ZoneOffset.UTC), "-19999-01-01T12:30:00Z"},
                {LocalDateTime.of(-19999, 1, 1, 0, 0).toInstant(ZoneOffset.UTC), "-19999-01-01T00:00:00Z"},

                {LocalDateTime.of(-20000, 12, 31, 23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC), "-20000-12-31T23:59:59.999999999Z"},
                {LocalDateTime.of(-20000, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "-20000-12-31T12:30:00Z"},
                {LocalDateTime.of(-20000, 12, 30, 12, 30).toInstant(ZoneOffset.UTC), "-20000-12-30T12:30:00Z"},
                {LocalDateTime.of(-25000, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "-25000-12-31T12:30:00Z"},

                {LocalDateTime.of(9999, 12, 30, 12, 30).toInstant(ZoneOffset.UTC), "9999-12-30T12:30:00Z"},
                {LocalDateTime.of(9999, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "9999-12-31T12:30:00Z"},
                {LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC), "9999-12-31T23:59:59.999999999Z"},

                {LocalDateTime.of(10000, 1, 1, 0, 0).toInstant(ZoneOffset.UTC), "+10000-01-01T00:00:00Z"},
                {LocalDateTime.of(10000, 1, 1, 12, 30).toInstant(ZoneOffset.UTC), "+10000-01-01T12:30:00Z"},
                {LocalDateTime.of(10000, 1, 2, 12, 30).toInstant(ZoneOffset.UTC), "+10000-01-02T12:30:00Z"},
                {LocalDateTime.of(15000, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "+15000-12-31T12:30:00Z"},

                {LocalDateTime.of(19999, 12, 30, 12, 30).toInstant(ZoneOffset.UTC), "+19999-12-30T12:30:00Z"},
                {LocalDateTime.of(19999, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "+19999-12-31T12:30:00Z"},
                {LocalDateTime.of(19999, 12, 31, 23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC), "+19999-12-31T23:59:59.999999999Z"},

                {LocalDateTime.of(20000, 1, 1, 0, 0).toInstant(ZoneOffset.UTC), "+20000-01-01T00:00:00Z"},
                {LocalDateTime.of(20000, 1, 1, 12, 30).toInstant(ZoneOffset.UTC), "+20000-01-01T12:30:00Z"},
                {LocalDateTime.of(20000, 1, 2, 12, 30).toInstant(ZoneOffset.UTC), "+20000-01-02T12:30:00Z"},
                {LocalDateTime.of(25000, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "+25000-12-31T12:30:00Z"},

                {LocalDateTime.of(-999_999_999, 1, 1, 12, 30).toInstant(ZoneOffset.UTC).minus(1, DAYS), "-1000000000-12-31T12:30:00Z"},
                {LocalDateTime.of(999_999_999, 12, 31, 12, 30).toInstant(ZoneOffset.UTC).plus(1, DAYS), "+1000000000-01-01T12:30:00Z"},

                {Instant.MIN, "-1000000000-01-01T00:00:00Z"},
                {Instant.MAX, "+1000000000-12-31T23:59:59.999999999Z"},
        };
    }

    @ParameterizedTest
    @MethodSource("data_toString")
    public void test_toString(Instant instant, String expected) {
        assertEquals(expected, instant.toString());
    }

    @ParameterizedTest
    @MethodSource("data_toString")
    public void test_parse(Instant instant, String text) {
        assertEquals(instant, Instant.parse(text));
    }

    @ParameterizedTest
    @MethodSource("data_toString")
    public void test_parseLowercase(Instant instant, String text) {
        assertEquals(instant, Instant.parse(text.toLowerCase(Locale.ENGLISH)));
    }

}
