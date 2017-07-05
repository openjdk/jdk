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
import static java.time.temporal.ChronoUnit.NANOS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.JulianFields;
import java.time.temporal.Queries;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test Instant.
 */
@Test
public class TCKInstant extends AbstractDateTimeTest {

    private static final long MIN_SECOND = Instant.MIN.getEpochSecond();
    private static final long MAX_SECOND = Instant.MAX.getEpochSecond();

    private Instant TEST_12345_123456789;

    @BeforeMethod
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
    @Test
    public void test_serialization() throws Exception {
        assertSerializable(Instant.ofEpochMilli(134l));
    }

    @Test
    public void test_serialization_format() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos) ) {
            dos.writeByte(2);
            dos.writeLong(654321);
            dos.writeInt(123456789);
        }
        byte[] bytes = baos.toByteArray();
        assertSerializedBySer(Instant.ofEpochSecond(654321, 123456789), bytes);
    }

    //-----------------------------------------------------------------------
    private void check(Instant instant, long epochSecs, int nos) {
        assertEquals(instant.getEpochSecond(), epochSecs);
        assertEquals(instant.getNano(), nos);
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
        Instant expected = Instant.now(Clock.systemUTC());
        Instant test = Instant.now();
        long diff = Math.abs(test.toEpochMilli() - expected.toEpochMilli());
        assertTrue(diff < 100);  // less than 0.1 secs
    }

    //-----------------------------------------------------------------------
    // now(Clock)
    //-----------------------------------------------------------------------
    @Test(expectedExceptions=NullPointerException.class)
    public void now_Clock_nullClock() {
        Instant.now(null);
    }

    @Test
    public void now_Clock_allSecsInDay_utc() {
        for (int i = 0; i < (2 * 24 * 60 * 60); i++) {
            Instant expected = Instant.ofEpochSecond(i).plusNanos(123456789L);
            Clock clock = Clock.fixed(expected, ZoneOffset.UTC);
            Instant test = Instant.now(clock);
            assertEquals(test, expected);
        }
    }

    @Test
    public void now_Clock_allSecsInDay_beforeEpoch() {
        for (int i =-1; i >= -(24 * 60 * 60); i--) {
            Instant expected = Instant.ofEpochSecond(i).plusNanos(123456789L);
            Clock clock = Clock.fixed(expected, ZoneOffset.UTC);
            Instant test = Instant.now(clock);
            assertEquals(test, expected);
        }
    }

    //-----------------------------------------------------------------------
    // ofEpochSecond(long)
    //-----------------------------------------------------------------------
    @Test
    public void factory_seconds_long() {
        for (long i = -2; i <= 2; i++) {
            Instant t = Instant.ofEpochSecond(i);
            assertEquals(t.getEpochSecond(), i);
            assertEquals(t.getNano(), 0);
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
                assertEquals(t.getEpochSecond(), i);
                assertEquals(t.getNano(), j);
            }
            for (int j = -10; j < 0; j++) {
                Instant t = Instant.ofEpochSecond(i, j);
                assertEquals(t.getEpochSecond(), i - 1);
                assertEquals(t.getNano(), j + 1000000000);
            }
            for (int j = 999999990; j < 1000000000; j++) {
                Instant t = Instant.ofEpochSecond(i, j);
                assertEquals(t.getEpochSecond(), i);
                assertEquals(t.getNano(), j);
            }
        }
    }

    @Test
    public void factory_seconds_long_long_nanosNegativeAdjusted() {
        Instant test = Instant.ofEpochSecond(2L, -1);
        assertEquals(test.getEpochSecond(), 1);
        assertEquals(test.getNano(), 999999999);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void factory_seconds_long_long_tooBig() {
        Instant.ofEpochSecond(MAX_SECOND, 1000000000);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void factory_seconds_long_long_tooBigBig() {
        Instant.ofEpochSecond(Long.MAX_VALUE, Long.MAX_VALUE);
    }

    //-----------------------------------------------------------------------
    // ofEpochMilli(long)
    //-----------------------------------------------------------------------
    @DataProvider(name="MillisInstantNoNanos")
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

    @Test(dataProvider="MillisInstantNoNanos")
    public void factory_millis_long(long millis, long expectedSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.ofEpochMilli(millis);
        assertEquals(t.getEpochSecond(), expectedSeconds);
        assertEquals(t.getNano(), expectedNanoOfSecond);
    }

    //-----------------------------------------------------------------------
    // parse(String)
    //-----------------------------------------------------------------------
    // see also parse tests under toString()
    @DataProvider(name="Parse")
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

    @Test(dataProvider="Parse")
    public void factory_parse(String text, long expectedEpochSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.parse(text);
        assertEquals(t.getEpochSecond(), expectedEpochSeconds);
        assertEquals(t.getNano(), expectedNanoOfSecond);
    }

    @Test(dataProvider="Parse")
    public void factory_parseLowercase(String text, long expectedEpochSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.parse(text.toLowerCase(Locale.ENGLISH));
        assertEquals(t.getEpochSecond(), expectedEpochSeconds);
        assertEquals(t.getNano(), expectedNanoOfSecond);
    }

// TODO: should comma be accepted?
//    @Test(dataProvider="Parse")
//    public void factory_parse_comma(String text, long expectedEpochSeconds, int expectedNanoOfSecond) {
//        text = text.replace('.', ',');
//        Instant t = Instant.parse(text);
//        assertEquals(t.getEpochSecond(), expectedEpochSeconds);
//        assertEquals(t.getNano(), expectedNanoOfSecond);
//    }

    @DataProvider(name="ParseFailures")
    Object[][] provider_factory_parseFailures() {
        return new Object[][] {
                {""},
                {"Z"},
                {"1970-01-01T00:00:00"},
                {"1970-01-01T00:00:0Z"},
                {"1970-01-01T00:00:00.0000000000Z"},
        };
    }

    @Test(dataProvider="ParseFailures", expectedExceptions=DateTimeParseException.class)
    public void factory_parseFailures(String text) {
        Instant.parse(text);
    }

    @Test(dataProvider="ParseFailures", expectedExceptions=DateTimeParseException.class)
    public void factory_parseFailures_comma(String text) {
        text = text.replace('.', ',');
        Instant.parse(text);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void factory_parse_nullText() {
        Instant.parse(null);
    }

    //-----------------------------------------------------------------------
    // get(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_get_TemporalField() {
        Instant test = TEST_12345_123456789;
        assertEquals(test.get(ChronoField.NANO_OF_SECOND), 123456789);
        assertEquals(test.get(ChronoField.MICRO_OF_SECOND), 123456);
        assertEquals(test.get(ChronoField.MILLI_OF_SECOND), 123);
    }

    @Test
    public void test_getLong_TemporalField() {
        Instant test = TEST_12345_123456789;
        assertEquals(test.getLong(ChronoField.NANO_OF_SECOND), 123456789);
        assertEquals(test.getLong(ChronoField.MICRO_OF_SECOND), 123456);
        assertEquals(test.getLong(ChronoField.MILLI_OF_SECOND), 123);
        assertEquals(test.getLong(ChronoField.INSTANT_SECONDS), 12345);
    }

    //-----------------------------------------------------------------------
    // query(TemporalQuery)
    //-----------------------------------------------------------------------
    @Test
    public void test_query_chrono() {
        assertEquals(TEST_12345_123456789.query(Queries.chrono()), null);
        assertEquals(Queries.chrono().queryFrom(TEST_12345_123456789), null);
    }

    @Test
    public void test_query_zoneId() {
        assertEquals(TEST_12345_123456789.query(Queries.zoneId()), null);
        assertEquals(Queries.zoneId().queryFrom(TEST_12345_123456789), null);
    }

    @Test
    public void test_query_precision() {
        assertEquals(TEST_12345_123456789.query(Queries.precision()), NANOS);
        assertEquals(Queries.precision().queryFrom(TEST_12345_123456789), NANOS);
    }

    @Test
    public void test_query_offset() {
        assertEquals(TEST_12345_123456789.query(Queries.offset()), null);
        assertEquals(Queries.offset().queryFrom(TEST_12345_123456789), null);
    }

    @Test
    public void test_query_zone() {
        assertEquals(TEST_12345_123456789.query(Queries.zone()), null);
        assertEquals(Queries.zone().queryFrom(TEST_12345_123456789), null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_query_null() {
        TEST_12345_123456789.query(null);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="Plus")
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

    @Test(dataProvider="Plus")
    public void plus_Duration(long seconds, int nanos, long otherSeconds, int otherNanos, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds, nanos).plus(Duration.ofSeconds(otherSeconds, otherNanos));
        assertEquals(i.getEpochSecond(), expectedSeconds);
        assertEquals(i.getNano(), expectedNanoOfSecond);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void plus_Duration_overflowTooBig() {
        Instant i = Instant.ofEpochSecond(MAX_SECOND, 999999999);
        i.plus(Duration.ofSeconds(0, 1));
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void plus_Duration_overflowTooSmall() {
        Instant i = Instant.ofEpochSecond(MIN_SECOND);
        i.plus(Duration.ofSeconds(-1, 999999999));
    }

    //-----------------------------------------------------------------------a
    @Test(dataProvider="Plus")
    public void plus_longTemporalUnit(long seconds, int nanos, long otherSeconds, int otherNanos, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds, nanos).plus(otherSeconds, SECONDS).plus(otherNanos, NANOS);
        assertEquals(i.getEpochSecond(), expectedSeconds);
        assertEquals(i.getNano(), expectedNanoOfSecond);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void plus_longTemporalUnit_overflowTooBig() {
        Instant i = Instant.ofEpochSecond(MAX_SECOND, 999999999);
        i.plus(1, NANOS);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void plus_longTemporalUnit_overflowTooSmall() {
        Instant i = Instant.ofEpochSecond(MIN_SECOND);
        i.plus(999999999, NANOS);
        i.plus(-1, SECONDS);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="PlusSeconds")
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

    @Test(dataProvider="PlusSeconds")
    public void plusSeconds_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.ofEpochSecond(seconds, nanos);
        t = t.plusSeconds(amount);
        assertEquals(t.getEpochSecond(), expectedSeconds);
        assertEquals(t.getNano(), expectedNanoOfSecond);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void plusSeconds_long_overflowTooBig() {
        Instant t = Instant.ofEpochSecond(1, 0);
        t.plusSeconds(Long.MAX_VALUE);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void plusSeconds_long_overflowTooSmall() {
        Instant t = Instant.ofEpochSecond(-1, 0);
        t.plusSeconds(Long.MIN_VALUE);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="PlusMillis")
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

    @Test(dataProvider="PlusMillis")
    public void plusMillis_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.ofEpochSecond(seconds, nanos);
        t = t.plusMillis(amount);
        assertEquals(t.getEpochSecond(), expectedSeconds);
        assertEquals(t.getNano(), expectedNanoOfSecond);
    }
    @Test(dataProvider="PlusMillis")
    public void plusMillis_long_oneMore(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.ofEpochSecond(seconds + 1, nanos);
        t = t.plusMillis(amount);
        assertEquals(t.getEpochSecond(), expectedSeconds + 1);
        assertEquals(t.getNano(), expectedNanoOfSecond);
    }
    @Test(dataProvider="PlusMillis")
    public void plusMillis_long_minusOneLess(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.ofEpochSecond(seconds - 1, nanos);
        t = t.plusMillis(amount);
        assertEquals(t.getEpochSecond(), expectedSeconds - 1);
        assertEquals(t.getNano(), expectedNanoOfSecond);
    }

    @Test
    public void plusMillis_long_max() {
        Instant t = Instant.ofEpochSecond(MAX_SECOND, 998999999);
        t = t.plusMillis(1);
        assertEquals(t.getEpochSecond(), MAX_SECOND);
        assertEquals(t.getNano(), 999999999);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void plusMillis_long_overflowTooBig() {
        Instant t = Instant.ofEpochSecond(MAX_SECOND, 999000000);
        t.plusMillis(1);
    }

    @Test
    public void plusMillis_long_min() {
        Instant t = Instant.ofEpochSecond(MIN_SECOND, 1000000);
        t = t.plusMillis(-1);
        assertEquals(t.getEpochSecond(), MIN_SECOND);
        assertEquals(t.getNano(), 0);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void plusMillis_long_overflowTooSmall() {
        Instant t = Instant.ofEpochSecond(MIN_SECOND, 0);
        t.plusMillis(-1);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="PlusNanos")
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

    @Test(dataProvider="PlusNanos")
    public void plusNanos_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant t = Instant.ofEpochSecond(seconds, nanos);
        t = t.plusNanos(amount);
        assertEquals(t.getEpochSecond(), expectedSeconds);
        assertEquals(t.getNano(), expectedNanoOfSecond);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void plusNanos_long_overflowTooBig() {
        Instant t = Instant.ofEpochSecond(MAX_SECOND, 999999999);
        t.plusNanos(1);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void plusNanos_long_overflowTooSmall() {
        Instant t = Instant.ofEpochSecond(MIN_SECOND, 0);
        t.plusNanos(-1);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="Minus")
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

    @Test(dataProvider="Minus")
    public void minus_Duration(long seconds, int nanos, long otherSeconds, int otherNanos, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds, nanos).minus(Duration.ofSeconds(otherSeconds, otherNanos));
        assertEquals(i.getEpochSecond(), expectedSeconds);
        assertEquals(i.getNano(), expectedNanoOfSecond);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void minus_Duration_overflowTooSmall() {
        Instant i = Instant.ofEpochSecond(MIN_SECOND);
        i.minus(Duration.ofSeconds(0, 1));
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void minus_Duration_overflowTooBig() {
        Instant i = Instant.ofEpochSecond(MAX_SECOND, 999999999);
        i.minus(Duration.ofSeconds(-1, 999999999));
    }

    //-----------------------------------------------------------------------
    @Test(dataProvider="Minus")
    public void minus_longTemporalUnit(long seconds, int nanos, long otherSeconds, int otherNanos, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds, nanos).minus(otherSeconds, SECONDS).minus(otherNanos, NANOS);
        assertEquals(i.getEpochSecond(), expectedSeconds);
        assertEquals(i.getNano(), expectedNanoOfSecond);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void minus_longTemporalUnit_overflowTooSmall() {
        Instant i = Instant.ofEpochSecond(MIN_SECOND);
        i.minus(1, NANOS);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void minus_longTemporalUnit_overflowTooBig() {
        Instant i = Instant.ofEpochSecond(MAX_SECOND, 999999999);
        i.minus(999999999, NANOS);
        i.minus(-1, SECONDS);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="MinusSeconds")
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

    @Test(dataProvider="MinusSeconds")
    public void minusSeconds_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds, nanos);
        i = i.minusSeconds(amount);
        assertEquals(i.getEpochSecond(), expectedSeconds);
        assertEquals(i.getNano(), expectedNanoOfSecond);
    }

    @Test(expectedExceptions = {ArithmeticException.class})
    public void minusSeconds_long_overflowTooBig() {
        Instant i = Instant.ofEpochSecond(1, 0);
        i.minusSeconds(Long.MIN_VALUE + 1);
    }

    @Test(expectedExceptions = {ArithmeticException.class})
    public void minusSeconds_long_overflowTooSmall() {
        Instant i = Instant.ofEpochSecond(-2, 0);
        i.minusSeconds(Long.MAX_VALUE);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="MinusMillis")
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

    @Test(dataProvider="MinusMillis")
    public void minusMillis_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds, nanos);
        i = i.minusMillis(amount);
        assertEquals(i.getEpochSecond(), expectedSeconds);
        assertEquals(i.getNano(), expectedNanoOfSecond);
    }

    @Test(dataProvider="MinusMillis")
    public void minusMillis_long_oneMore(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds + 1, nanos);
        i = i.minusMillis(amount);
        assertEquals(i.getEpochSecond(), expectedSeconds + 1);
        assertEquals(i.getNano(), expectedNanoOfSecond);
    }

    @Test(dataProvider="MinusMillis")
    public void minusMillis_long_minusOneLess(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds - 1, nanos);
        i = i.minusMillis(amount);
        assertEquals(i.getEpochSecond(), expectedSeconds - 1);
        assertEquals(i.getNano(), expectedNanoOfSecond);
    }

    @Test
    public void minusMillis_long_max() {
        Instant i = Instant.ofEpochSecond(MAX_SECOND, 998999999);
        i = i.minusMillis(-1);
        assertEquals(i.getEpochSecond(), MAX_SECOND);
        assertEquals(i.getNano(), 999999999);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void minusMillis_long_overflowTooBig() {
        Instant i = Instant.ofEpochSecond(MAX_SECOND, 999000000);
        i.minusMillis(-1);
    }

    @Test
    public void minusMillis_long_min() {
        Instant i = Instant.ofEpochSecond(MIN_SECOND, 1000000);
        i = i.minusMillis(1);
        assertEquals(i.getEpochSecond(), MIN_SECOND);
        assertEquals(i.getNano(), 0);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void minusMillis_long_overflowTooSmall() {
        Instant i = Instant.ofEpochSecond(MIN_SECOND, 0);
        i.minusMillis(1);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="MinusNanos")
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

    @Test(dataProvider="MinusNanos")
    public void minusNanos_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Instant i = Instant.ofEpochSecond(seconds, nanos);
        i = i.minusNanos(amount);
        assertEquals(i.getEpochSecond(), expectedSeconds);
        assertEquals(i.getNano(), expectedNanoOfSecond);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void minusNanos_long_overflowTooBig() {
        Instant i = Instant.ofEpochSecond(MAX_SECOND, 999999999);
        i.minusNanos(-1);
    }

    @Test(expectedExceptions=DateTimeException.class)
    public void minusNanos_long_overflowTooSmall() {
        Instant i = Instant.ofEpochSecond(MIN_SECOND, 0);
        i.minusNanos(1);
    }

    //-----------------------------------------------------------------------
    // toEpochMilli()
    //-----------------------------------------------------------------------
    @Test
    public void test_toEpochMilli() {
        assertEquals(Instant.ofEpochSecond(1L, 1000000).toEpochMilli(), 1001L);
        assertEquals(Instant.ofEpochSecond(1L, 2000000).toEpochMilli(), 1002L);
        assertEquals(Instant.ofEpochSecond(1L, 567).toEpochMilli(), 1000L);
        assertEquals(Instant.ofEpochSecond(Long.MAX_VALUE / 1000).toEpochMilli(), (Long.MAX_VALUE / 1000) * 1000);
        assertEquals(Instant.ofEpochSecond(Long.MIN_VALUE / 1000).toEpochMilli(), (Long.MIN_VALUE / 1000) * 1000);
        assertEquals(Instant.ofEpochSecond(0L, -1000000).toEpochMilli(), -1L);
        assertEquals(Instant.ofEpochSecond(0L, 1000000).toEpochMilli(), 1);
        assertEquals(Instant.ofEpochSecond(0L, 999999).toEpochMilli(), 0);
        assertEquals(Instant.ofEpochSecond(0L, 1).toEpochMilli(), 0);
        assertEquals(Instant.ofEpochSecond(0L, 0).toEpochMilli(), 0);
        assertEquals(Instant.ofEpochSecond(0L, -1).toEpochMilli(), -1L);
        assertEquals(Instant.ofEpochSecond(0L, -999999).toEpochMilli(), -1L);
        assertEquals(Instant.ofEpochSecond(0L, -1000000).toEpochMilli(), -1L);
        assertEquals(Instant.ofEpochSecond(0L, -1000001).toEpochMilli(), -2L);
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_toEpochMilli_tooBig() {
        Instant.ofEpochSecond(Long.MAX_VALUE / 1000 + 1).toEpochMilli();
    }

    @Test(expectedExceptions=ArithmeticException.class)
    public void test_toEpochMilli_tooSmall() {
        Instant.ofEpochSecond(Long.MIN_VALUE / 1000 - 1).toEpochMilli();
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
                    assertEquals(a.compareTo(b) < 0, true, a + " <=> " + b);
                    assertEquals(a.isBefore(b), true, a + " <=> " + b);
                    assertEquals(a.isAfter(b), false, a + " <=> " + b);
                    assertEquals(a.equals(b), false, a + " <=> " + b);
                } else if (i > j) {
                    assertEquals(a.compareTo(b) > 0, true, a + " <=> " + b);
                    assertEquals(a.isBefore(b), false, a + " <=> " + b);
                    assertEquals(a.isAfter(b), true, a + " <=> " + b);
                    assertEquals(a.equals(b), false, a + " <=> " + b);
                } else {
                    assertEquals(a.compareTo(b), 0, a + " <=> " + b);
                    assertEquals(a.isBefore(b), false, a + " <=> " + b);
                    assertEquals(a.isAfter(b), false, a + " <=> " + b);
                    assertEquals(a.equals(b), true, a + " <=> " + b);
                }
            }
        }
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_compareTo_ObjectNull() {
        Instant a = Instant.ofEpochSecond(0L, 0);
        a.compareTo(null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_isBefore_ObjectNull() {
        Instant a = Instant.ofEpochSecond(0L, 0);
        a.isBefore(null);
    }

    @Test(expectedExceptions=NullPointerException.class)
    public void test_isAfter_ObjectNull() {
        Instant a = Instant.ofEpochSecond(0L, 0);
        a.isAfter(null);
    }

    @Test(expectedExceptions=ClassCastException.class)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void compareToNonInstant() {
        Comparable c = Instant.ofEpochSecond(0L);
        c.compareTo(new Object());
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

        assertEquals(test5a.equals(test5a), true);
        assertEquals(test5a.equals(test5b), true);
        assertEquals(test5a.equals(test5n), false);
        assertEquals(test5a.equals(test6), false);

        assertEquals(test5b.equals(test5a), true);
        assertEquals(test5b.equals(test5b), true);
        assertEquals(test5b.equals(test5n), false);
        assertEquals(test5b.equals(test6), false);

        assertEquals(test5n.equals(test5a), false);
        assertEquals(test5n.equals(test5b), false);
        assertEquals(test5n.equals(test5n), true);
        assertEquals(test5n.equals(test6), false);

        assertEquals(test6.equals(test5a), false);
        assertEquals(test6.equals(test5b), false);
        assertEquals(test6.equals(test5n), false);
        assertEquals(test6.equals(test6), true);
    }

    @Test
    public void test_equals_null() {
        Instant test5 = Instant.ofEpochSecond(5L, 20);
        assertEquals(test5.equals(null), false);
    }

    @Test
    public void test_equals_otherClass() {
        Instant test5 = Instant.ofEpochSecond(5L, 20);
        assertEquals(test5.equals(""), false);
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

        assertEquals(test5a.hashCode() == test5a.hashCode(), true);
        assertEquals(test5a.hashCode() == test5b.hashCode(), true);
        assertEquals(test5b.hashCode() == test5b.hashCode(), true);

        assertEquals(test5a.hashCode() == test5n.hashCode(), false);
        assertEquals(test5a.hashCode() == test6.hashCode(), false);
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @DataProvider(name="toStringParse")
    Object[][] data_toString() {
        return new Object[][] {
                {Instant.ofEpochSecond(65L, 567), "1970-01-01T00:01:05.000000567Z"},
                {Instant.ofEpochSecond(1, 0), "1970-01-01T00:00:01Z"},
                {Instant.ofEpochSecond(60, 0), "1970-01-01T00:01Z"},
                {Instant.ofEpochSecond(3600, 0), "1970-01-01T01:00Z"},
                {Instant.ofEpochSecond(-1, 0), "1969-12-31T23:59:59Z"},

                {LocalDateTime.of(0, 1, 2, 0, 0).toInstant(ZoneOffset.UTC), "0000-01-02T00:00Z"},
                {LocalDateTime.of(0, 1, 1, 12, 30).toInstant(ZoneOffset.UTC), "0000-01-01T12:30Z"},
                {LocalDateTime.of(0, 1, 1, 0, 0, 0, 1).toInstant(ZoneOffset.UTC), "0000-01-01T00:00:00.000000001Z"},
                {LocalDateTime.of(0, 1, 1, 0, 0).toInstant(ZoneOffset.UTC), "0000-01-01T00:00Z"},

                {LocalDateTime.of(-1, 12, 31, 23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC), "-0001-12-31T23:59:59.999999999Z"},
                {LocalDateTime.of(-1, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "-0001-12-31T12:30Z"},
                {LocalDateTime.of(-1, 12, 30, 12, 30).toInstant(ZoneOffset.UTC), "-0001-12-30T12:30Z"},

                {LocalDateTime.of(-9999, 1, 2, 12, 30).toInstant(ZoneOffset.UTC), "-9999-01-02T12:30Z"},
                {LocalDateTime.of(-9999, 1, 1, 12, 30).toInstant(ZoneOffset.UTC), "-9999-01-01T12:30Z"},
                {LocalDateTime.of(-9999, 1, 1, 0, 0).toInstant(ZoneOffset.UTC), "-9999-01-01T00:00Z"},

                {LocalDateTime.of(-10000, 12, 31, 23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC), "-10000-12-31T23:59:59.999999999Z"},
                {LocalDateTime.of(-10000, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "-10000-12-31T12:30Z"},
                {LocalDateTime.of(-10000, 12, 30, 12, 30).toInstant(ZoneOffset.UTC), "-10000-12-30T12:30Z"},
                {LocalDateTime.of(-15000, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "-15000-12-31T12:30Z"},

                {LocalDateTime.of(-19999, 1, 2, 12, 30).toInstant(ZoneOffset.UTC), "-19999-01-02T12:30Z"},
                {LocalDateTime.of(-19999, 1, 1, 12, 30).toInstant(ZoneOffset.UTC), "-19999-01-01T12:30Z"},
                {LocalDateTime.of(-19999, 1, 1, 0, 0).toInstant(ZoneOffset.UTC), "-19999-01-01T00:00Z"},

                {LocalDateTime.of(-20000, 12, 31, 23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC), "-20000-12-31T23:59:59.999999999Z"},
                {LocalDateTime.of(-20000, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "-20000-12-31T12:30Z"},
                {LocalDateTime.of(-20000, 12, 30, 12, 30).toInstant(ZoneOffset.UTC), "-20000-12-30T12:30Z"},
                {LocalDateTime.of(-25000, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "-25000-12-31T12:30Z"},

                {LocalDateTime.of(9999, 12, 30, 12, 30).toInstant(ZoneOffset.UTC), "9999-12-30T12:30Z"},
                {LocalDateTime.of(9999, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "9999-12-31T12:30Z"},
                {LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC), "9999-12-31T23:59:59.999999999Z"},

                {LocalDateTime.of(10000, 1, 1, 0, 0).toInstant(ZoneOffset.UTC), "+10000-01-01T00:00Z"},
                {LocalDateTime.of(10000, 1, 1, 12, 30).toInstant(ZoneOffset.UTC), "+10000-01-01T12:30Z"},
                {LocalDateTime.of(10000, 1, 2, 12, 30).toInstant(ZoneOffset.UTC), "+10000-01-02T12:30Z"},
                {LocalDateTime.of(15000, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "+15000-12-31T12:30Z"},

                {LocalDateTime.of(19999, 12, 30, 12, 30).toInstant(ZoneOffset.UTC), "+19999-12-30T12:30Z"},
                {LocalDateTime.of(19999, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "+19999-12-31T12:30Z"},
                {LocalDateTime.of(19999, 12, 31, 23, 59, 59, 999_999_999).toInstant(ZoneOffset.UTC), "+19999-12-31T23:59:59.999999999Z"},

                {LocalDateTime.of(20000, 1, 1, 0, 0).toInstant(ZoneOffset.UTC), "+20000-01-01T00:00Z"},
                {LocalDateTime.of(20000, 1, 1, 12, 30).toInstant(ZoneOffset.UTC), "+20000-01-01T12:30Z"},
                {LocalDateTime.of(20000, 1, 2, 12, 30).toInstant(ZoneOffset.UTC), "+20000-01-02T12:30Z"},
                {LocalDateTime.of(25000, 12, 31, 12, 30).toInstant(ZoneOffset.UTC), "+25000-12-31T12:30Z"},

                {LocalDateTime.of(-999_999_999, 1, 1, 12, 30).toInstant(ZoneOffset.UTC).minus(1, DAYS), "-1000000000-12-31T12:30Z"},
                {LocalDateTime.of(999_999_999, 12, 31, 12, 30).toInstant(ZoneOffset.UTC).plus(1, DAYS), "+1000000000-01-01T12:30Z"},

                {Instant.MIN, "-1000000000-01-01T00:00Z"},
                {Instant.MAX, "+1000000000-12-31T23:59:59.999999999Z"},
        };
    }

    @Test(dataProvider="toStringParse")
    public void test_toString(Instant instant, String expected) {
        assertEquals(instant.toString(), expected);
    }

    @Test(dataProvider="toStringParse")
    public void test_parse(Instant instant, String text) {
        assertEquals(Instant.parse(text), instant);
    }

    @Test(dataProvider="toStringParse")
    public void test_parseLowercase(Instant instant, String text) {
        assertEquals(Instant.parse(text.toLowerCase(Locale.ENGLISH)), instant);
    }

}
