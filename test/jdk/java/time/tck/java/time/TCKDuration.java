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

import static java.time.temporal.ChronoUnit.DAYS;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test Duration.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TCKDuration extends AbstractTCKTest {

    private static final long CYCLE_SECS = 146097L * 86400L;

    //-----------------------------------------------------------------------
    // constants
    //-----------------------------------------------------------------------
    @Test
    public void test_zero() {
        assertEquals(0L, Duration.ZERO.getSeconds());
        assertEquals(0, Duration.ZERO.getNano());
    }

    @Test
    public void test_min() {
        assertEquals(Long.MIN_VALUE, Duration.MIN.getSeconds());
        assertEquals(0, Duration.MIN.getNano());
        // no duration minimally less than MIN
        assertThrows(ArithmeticException.class, () -> Duration.MIN.minusNanos(1));
    }

    @Test
    public void test_max() {
        assertEquals(Long.MAX_VALUE, Duration.MAX.getSeconds());
        assertEquals(999_999_999, Duration.MAX.getNano());
        // no duration minimally greater than MAX
        assertThrows(ArithmeticException.class, () -> Duration.MAX.plusNanos(1));
    }

    @Test
    public void test_constant_properties() {
        assertTrue(Duration.MIN.compareTo(Duration.MIN) == 0);
        assertEquals(Duration.MIN, Duration.MIN);
        assertTrue(Duration.ZERO.compareTo(Duration.ZERO) == 0);
        assertEquals(Duration.ZERO, Duration.ZERO);
        assertTrue(Duration.MAX.compareTo(Duration.MAX) == 0);
        assertEquals(Duration.MAX, Duration.MAX);

        assertTrue(Duration.MIN.compareTo(Duration.ZERO) < 0);
        assertTrue(Duration.ZERO.compareTo(Duration.MIN) > 0);
        assertNotEquals(Duration.MIN, Duration.ZERO);

        assertTrue(Duration.ZERO.compareTo(Duration.MAX) < 0);
        assertTrue(Duration.MAX.compareTo(Duration.ZERO) > 0);
        assertNotEquals(Duration.MAX, Duration.ZERO);

        assertTrue(Duration.MIN.compareTo(Duration.MAX) < 0);
        assertTrue(Duration.MAX.compareTo(Duration.MIN) > 0);
        assertNotEquals(Duration.MAX, Duration.MIN);
    }

    //-----------------------------------------------------------------------
    // ofSeconds(long)
    //-----------------------------------------------------------------------
    @Test
    public void factory_seconds_long() {
        for (long i = -2; i <= 2; i++) {
            Duration t = Duration.ofSeconds(i);
            assertEquals(i, t.getSeconds());
            assertEquals(0, t.getNano());
        }
    }

    //-----------------------------------------------------------------------
    // ofSeconds(long,long)
    //-----------------------------------------------------------------------
    @Test
    public void factory_seconds_long_long() {
        for (long i = -2; i <= 2; i++) {
            for (int j = 0; j < 10; j++) {
                Duration t = Duration.ofSeconds(i, j);
                assertEquals(i, t.getSeconds());
                assertEquals(j, t.getNano());
            }
            for (int j = -10; j < 0; j++) {
                Duration t = Duration.ofSeconds(i, j);
                assertEquals(i - 1, t.getSeconds());
                assertEquals(j + 1000000000, t.getNano());
            }
            for (int j = 999999990; j < 1000000000; j++) {
                Duration t = Duration.ofSeconds(i, j);
                assertEquals(i, t.getSeconds());
                assertEquals(j, t.getNano());
            }
        }
    }

    @Test
    public void factory_seconds_long_long_nanosNegativeAdjusted() {
        Duration test = Duration.ofSeconds(2L, -1);
        assertEquals(1, test.getSeconds());
        assertEquals(999999999, test.getNano());
    }

    @Test
    public void factory_seconds_long_long_tooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> Duration.ofSeconds(Long.MAX_VALUE, 1000000000));
    }

    //-----------------------------------------------------------------------
    // ofMillis(long)
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
        Duration test = Duration.ofMillis(millis);
        assertEquals(expectedSeconds, test.getSeconds());
        assertEquals(expectedNanoOfSecond, test.getNano());
    }

    //-----------------------------------------------------------------------
    // ofNanos(long)
    //-----------------------------------------------------------------------
    @Test
    public void factory_nanos_nanos() {
        Duration test = Duration.ofNanos(1);
        assertEquals(0, test.getSeconds());
        assertEquals(1, test.getNano());
    }

    @Test
    public void factory_nanos_nanosSecs() {
        Duration test = Duration.ofNanos(1000000002);
        assertEquals(1, test.getSeconds());
        assertEquals(2, test.getNano());
    }

    @Test
    public void factory_nanos_negative() {
        Duration test = Duration.ofNanos(-2000000001);
        assertEquals(-3, test.getSeconds());
        assertEquals(999999999, test.getNano());
    }

    @Test
    public void factory_nanos_max() {
        Duration test = Duration.ofNanos(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE / 1000000000, test.getSeconds());
        assertEquals(Long.MAX_VALUE % 1000000000, test.getNano());
    }

    @Test
    public void factory_nanos_min() {
        Duration test = Duration.ofNanos(Long.MIN_VALUE);
        assertEquals(Long.MIN_VALUE / 1000000000 - 1, test.getSeconds());
        assertEquals(Long.MIN_VALUE % 1000000000 + 1000000000, test.getNano());
    }

    //-----------------------------------------------------------------------
    // ofMinutes()
    //-----------------------------------------------------------------------
    @Test
    public void factory_minutes() {
        Duration test = Duration.ofMinutes(2);
        assertEquals(120, test.getSeconds());
        assertEquals(0, test.getNano());
    }

    @Test
    public void factory_minutes_max() {
        Duration test = Duration.ofMinutes(Long.MAX_VALUE / 60);
        assertEquals((Long.MAX_VALUE / 60) * 60, test.getSeconds());
        assertEquals(0, test.getNano());
    }

    @Test
    public void factory_minutes_min() {
        Duration test = Duration.ofMinutes(Long.MIN_VALUE / 60);
        assertEquals((Long.MIN_VALUE / 60) * 60, test.getSeconds());
        assertEquals(0, test.getNano());
    }

    @Test
    public void factory_minutes_tooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> Duration.ofMinutes(Long.MAX_VALUE / 60 + 1));
    }

    @Test
    public void factory_minutes_tooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> Duration.ofMinutes(Long.MIN_VALUE / 60 - 1));
    }

    //-----------------------------------------------------------------------
    // ofHours()
    //-----------------------------------------------------------------------
    @Test
    public void factory_hours() {
        Duration test = Duration.ofHours(2);
        assertEquals(2 * 3600, test.getSeconds());
        assertEquals(0, test.getNano());
    }

    @Test
    public void factory_hours_max() {
        Duration test = Duration.ofHours(Long.MAX_VALUE / 3600);
        assertEquals((Long.MAX_VALUE / 3600) * 3600, test.getSeconds());
        assertEquals(0, test.getNano());
    }

    @Test
    public void factory_hours_min() {
        Duration test = Duration.ofHours(Long.MIN_VALUE / 3600);
        assertEquals((Long.MIN_VALUE / 3600) * 3600, test.getSeconds());
        assertEquals(0, test.getNano());
    }

    @Test
    public void factory_hours_tooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> Duration.ofHours(Long.MAX_VALUE / 3600 + 1));
    }

    @Test
    public void factory_hours_tooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> Duration.ofHours(Long.MIN_VALUE / 3600 - 1));
    }

    //-----------------------------------------------------------------------
    // ofDays()
    //-----------------------------------------------------------------------
    @Test
    public void factory_days() {
        Duration test = Duration.ofDays(2);
        assertEquals(2 * 86400, test.getSeconds());
        assertEquals(0, test.getNano());
    }

    @Test
    public void factory_days_max() {
        Duration test = Duration.ofDays(Long.MAX_VALUE / 86400);
        assertEquals((Long.MAX_VALUE / 86400) * 86400, test.getSeconds());
        assertEquals(0, test.getNano());
    }

    @Test
    public void factory_days_min() {
        Duration test = Duration.ofDays(Long.MIN_VALUE / 86400);
        assertEquals((Long.MIN_VALUE / 86400) * 86400, test.getSeconds());
        assertEquals(0, test.getNano());
    }

    @Test
    public void factory_days_tooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> Duration.ofDays(Long.MAX_VALUE / 86400 + 1));
    }

    @Test
    public void factory_days_tooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> Duration.ofDays(Long.MIN_VALUE / 86400 - 1));
    }

    //-----------------------------------------------------------------------
    // of(long,TemporalUnit)
    //-----------------------------------------------------------------------
    Object[][] provider_factory_of_longTemporalUnit() {
        return new Object[][] {
            {0, NANOS, 0, 0},
            {0, MICROS, 0, 0},
            {0, MILLIS, 0, 0},
            {0, SECONDS, 0, 0},
            {0, MINUTES, 0, 0},
            {0, HOURS, 0, 0},
            {0, HALF_DAYS, 0, 0},
            {0, DAYS, 0, 0},
            {1, NANOS, 0, 1},
            {1, MICROS, 0, 1000},
            {1, MILLIS, 0, 1000000},
            {1, SECONDS, 1, 0},
            {1, MINUTES, 60, 0},
            {1, HOURS, 3600, 0},
            {1, HALF_DAYS, 43200, 0},
            {1, DAYS, 86400, 0},
            {3, NANOS, 0, 3},
            {3, MICROS, 0, 3000},
            {3, MILLIS, 0, 3000000},
            {3, SECONDS, 3, 0},
            {3, MINUTES, 3 * 60, 0},
            {3, HOURS, 3 * 3600, 0},
            {3, HALF_DAYS, 3 * 43200, 0},
            {3, DAYS, 3 * 86400, 0},
            {-1, NANOS, -1, 999999999},
            {-1, MICROS, -1, 999999000},
            {-1, MILLIS, -1, 999000000},
            {-1, SECONDS, -1, 0},
            {-1, MINUTES, -60, 0},
            {-1, HOURS, -3600, 0},
            {-1, HALF_DAYS, -43200, 0},
            {-1, DAYS, -86400, 0},
            {-3, NANOS, -1, 999999997},
            {-3, MICROS, -1, 999997000},
            {-3, MILLIS, -1, 997000000},
            {-3, SECONDS, -3, 0},
            {-3, MINUTES, -3 * 60, 0},
            {-3, HOURS, -3 * 3600, 0},
            {-3, HALF_DAYS, -3 * 43200, 0},
            {-3, DAYS, -3 * 86400, 0},
            {Long.MAX_VALUE, NANOS, Long.MAX_VALUE / 1000000000, (int) (Long.MAX_VALUE % 1000000000)},
            {Long.MIN_VALUE, NANOS, Long.MIN_VALUE / 1000000000 - 1, (int) (Long.MIN_VALUE % 1000000000 + 1000000000)},
            {Long.MAX_VALUE, MICROS, Long.MAX_VALUE / 1000000, (int) ((Long.MAX_VALUE % 1000000) * 1000)},
            {Long.MIN_VALUE, MICROS, Long.MIN_VALUE / 1000000 - 1, (int) ((Long.MIN_VALUE % 1000000 + 1000000) * 1000)},
            {Long.MAX_VALUE, MILLIS, Long.MAX_VALUE / 1000, (int) ((Long.MAX_VALUE % 1000) * 1000000)},
            {Long.MIN_VALUE, MILLIS, Long.MIN_VALUE / 1000 - 1, (int) ((Long.MIN_VALUE % 1000 + 1000) * 1000000)},
            {Long.MAX_VALUE, SECONDS, Long.MAX_VALUE, 0},
            {Long.MIN_VALUE, SECONDS, Long.MIN_VALUE, 0},
            {Long.MAX_VALUE / 60, MINUTES, (Long.MAX_VALUE / 60) * 60, 0},
            {Long.MIN_VALUE / 60, MINUTES, (Long.MIN_VALUE / 60) * 60, 0},
            {Long.MAX_VALUE / 3600, HOURS, (Long.MAX_VALUE / 3600) * 3600, 0},
            {Long.MIN_VALUE / 3600, HOURS, (Long.MIN_VALUE / 3600) * 3600, 0},
            {Long.MAX_VALUE / 43200, HALF_DAYS, (Long.MAX_VALUE / 43200) * 43200, 0},
            {Long.MIN_VALUE / 43200, HALF_DAYS, (Long.MIN_VALUE / 43200) * 43200, 0},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_factory_of_longTemporalUnit")
    public void factory_of_longTemporalUnit(long amount, TemporalUnit unit, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.of(amount, unit);
        assertEquals(expectedSeconds, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    Object[][] provider_factory_of_longTemporalUnit_outOfRange() {
        return new Object[][] {
            {Long.MAX_VALUE / 60 + 1, MINUTES},
            {Long.MIN_VALUE / 60 - 1, MINUTES},
            {Long.MAX_VALUE / 3600 + 1, HOURS},
            {Long.MIN_VALUE / 3600 - 1, HOURS},
            {Long.MAX_VALUE / 43200 + 1, HALF_DAYS},
            {Long.MIN_VALUE / 43200 - 1, HALF_DAYS},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_factory_of_longTemporalUnit_outOfRange")
    public void factory_of_longTemporalUnit_outOfRange(long amount, TemporalUnit unit) {
        Assertions.assertThrows(ArithmeticException.class, () -> Duration.of(amount, unit));
    }

    @Test
    public void factory_of_longTemporalUnit_estimatedUnit() {
        Assertions.assertThrows(DateTimeException.class, () -> Duration.of(2, WEEKS));
    }

    @Test
    public void factory_of_longTemporalUnit_null() {
        Assertions.assertThrows(NullPointerException.class, () -> Duration.of(1, (TemporalUnit) null));
    }

    //-----------------------------------------------------------------------
    // from(TemporalAmount)
    //-----------------------------------------------------------------------
    @Test
    public void factory_from_TemporalAmount_Duration() {
        TemporalAmount amount = Duration.ofHours(3);
        assertEquals(Duration.ofHours(3), Duration.from(amount));
    }

    @Test
    public void factory_from_TemporalAmount_DaysNanos() {
        TemporalAmount amount = new TemporalAmount() {
            @Override
            public long get(TemporalUnit unit) {
                if (unit == DAYS) {
                    return 23;
                } else {
                    return 45;
                }
            }
            @Override
            public List<TemporalUnit> getUnits() {
                List<TemporalUnit> list = new ArrayList<>();
                list.add(DAYS);
                list.add(NANOS);
                return list;
            }
            @Override
            public Temporal addTo(Temporal temporal) {
                throw new UnsupportedOperationException();
            }
            @Override
            public Temporal subtractFrom(Temporal temporal) {
                throw new UnsupportedOperationException();
            }
        };
        Duration t = Duration.from(amount);
        assertEquals(23 * 86400, t.getSeconds());
        assertEquals(45, t.getNano());
    }

    @Test
    public void factory_from_TemporalAmount_Minutes_tooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            TemporalAmount amount = new TemporalAmount() {
                @Override
                public long get(TemporalUnit unit) {
                    return (Long.MAX_VALUE / 60) + 2;
                }
                @Override
                public List<TemporalUnit> getUnits() {
                    return Collections.<TemporalUnit>singletonList(MINUTES);
                }
                @Override
                public Temporal addTo(Temporal temporal) {
                    throw new UnsupportedOperationException();
                }
                @Override
                public Temporal subtractFrom(Temporal temporal) {
                    throw new UnsupportedOperationException();
                }
            };
            Duration.from(amount);
        });
    }

    @Test
    public void factory_from_TemporalAmount_Period() {
        Assertions.assertThrows(DateTimeException.class, () -> Duration.from(Period.ZERO));
    }

    @Test
    public void factory_from_TemporalAmount_null() {
        Assertions.assertThrows(NullPointerException.class, () -> Duration.from(null));
    }

    //-----------------------------------------------------------------------
    // parse(String)
    //-----------------------------------------------------------------------
    Object[][] data_parseSuccess() {
        return new Object[][] {
                {"PT0S", 0, 0},
                {"PT1S", 1, 0},
                {"PT12S", 12, 0},
                {"PT123456789S", 123456789, 0},
                {"PT" + Long.MAX_VALUE + "S", Long.MAX_VALUE, 0},

                {"PT+1S", 1, 0},
                {"PT+12S", 12, 0},
                {"PT+123456789S", 123456789, 0},
                {"PT+" + Long.MAX_VALUE + "S", Long.MAX_VALUE, 0},

                {"PT-1S", -1, 0},
                {"PT-12S", -12, 0},
                {"PT-123456789S", -123456789, 0},
                {"PT" + Long.MIN_VALUE + "S", Long.MIN_VALUE, 0},


                {"PT0.1S", 0, 100000000},
                {"PT1.1S", 1, 100000000},
                {"PT1.12S", 1, 120000000},
                {"PT1.123S", 1, 123000000},
                {"PT1.1234S", 1, 123400000},
                {"PT1.12345S", 1, 123450000},
                {"PT1.123456S", 1, 123456000},
                {"PT1.1234567S", 1, 123456700},
                {"PT1.12345678S", 1, 123456780},
                {"PT1.123456789S", 1, 123456789},

                {"PT-0.1S", -1, 1000000000 - 100000000},
                {"PT-1.1S", -2, 1000000000 - 100000000},
                {"PT-1.12S", -2, 1000000000 - 120000000},
                {"PT-1.123S", -2, 1000000000 - 123000000},
                {"PT-1.1234S", -2, 1000000000 - 123400000},
                {"PT-1.12345S", -2, 1000000000 - 123450000},
                {"PT-1.123456S", -2, 1000000000 - 123456000},
                {"PT-1.1234567S", -2, 1000000000 - 123456700},
                {"PT-1.12345678S", -2, 1000000000 - 123456780},
                {"PT-1.123456789S", -2, 1000000000 - 123456789},

                {"PT" + Long.MAX_VALUE + ".123456789S", Long.MAX_VALUE, 123456789},
                {"PT" + Long.MIN_VALUE + ".000000000S", Long.MIN_VALUE, 0},

                {"PT12M", 12 * 60, 0},
                {"PT12M0.35S", 12 * 60, 350000000},
                {"PT12M1.35S", 12 * 60 + 1, 350000000},
                {"PT12M-0.35S", 12 * 60 - 1, 1000000000 - 350000000},
                {"PT12M-1.35S", 12 * 60 - 2, 1000000000 - 350000000},

                {"PT12H", 12 * 3600, 0},
                {"PT12H0.35S", 12 * 3600, 350000000},
                {"PT12H1.35S", 12 * 3600 + 1, 350000000},
                {"PT12H-0.35S", 12 * 3600 - 1, 1000000000 - 350000000},
                {"PT12H-1.35S", 12 * 3600 - 2, 1000000000 - 350000000},

                {"P12D", 12 * 24 * 3600, 0},
                {"P12DT0.35S", 12 * 24 * 3600, 350000000},
                {"P12DT1.35S", 12 * 24 * 3600 + 1, 350000000},
                {"P12DT-0.35S", 12 * 24 * 3600 - 1, 1000000000 - 350000000},
                {"P12DT-1.35S", 12 * 24 * 3600 - 2, 1000000000 - 350000000},

                {"PT01S", 1, 0},
                {"PT001S", 1, 0},
                {"PT000S", 0, 0},
                {"PT+01S", 1, 0},
                {"PT-01S", -1, 0},

                {"PT1.S", 1, 0},
                {"PT+1.S", 1, 0},
                {"PT-1.S", -1, 0},

                {"P0D", 0, 0},
                {"P0DT0H", 0, 0},
                {"P0DT0M", 0, 0},
                {"P0DT0S", 0, 0},
                {"P0DT0H0S", 0, 0},
                {"P0DT0M0S", 0, 0},
                {"P0DT0H0M0S", 0, 0},

                {"P1D", 86400, 0},
                {"P1DT0H", 86400, 0},
                {"P1DT0M", 86400, 0},
                {"P1DT0S", 86400, 0},
                {"P1DT0H0S", 86400, 0},
                {"P1DT0M0S", 86400, 0},
                {"P1DT0H0M0S", 86400, 0},

                {"P3D", 86400 * 3, 0},
                {"P3DT2H", 86400 * 3 + 3600 * 2, 0},
                {"P3DT2M", 86400 * 3 + 60 * 2, 0},
                {"P3DT2S", 86400 * 3 + 2, 0},
                {"P3DT2H1S", 86400 * 3 + 3600 * 2 + 1, 0},
                {"P3DT2M1S", 86400 * 3 + 60 * 2 + 1, 0},
                {"P3DT2H1M1S", 86400 * 3 + 3600 * 2 + 60 + 1, 0},

                {"P-3D", -86400 * 3, 0},
                {"P-3DT2H", -86400 * 3 + 3600 * 2, 0},
                {"P-3DT2M", -86400 * 3 + 60 * 2, 0},
                {"P-3DT2S", -86400 * 3 + 2, 0},
                {"P-3DT2H1S", -86400 * 3 + 3600 * 2 + 1, 0},
                {"P-3DT2M1S", -86400 * 3 + 60 * 2 + 1, 0},
                {"P-3DT2H1M1S", -86400 * 3 + 3600 * 2 + 60 + 1, 0},

                {"P-3DT-2H", -86400 * 3 - 3600 * 2, 0},
                {"P-3DT-2M", -86400 * 3 - 60 * 2, 0},
                {"P-3DT-2S", -86400 * 3 - 2, 0},
                {"P-3DT-2H1S", -86400 * 3 - 3600 * 2 + 1, 0},
                {"P-3DT-2M1S", -86400 * 3 - 60 * 2 + 1, 0},
                {"P-3DT-2H1M1S", -86400 * 3 - 3600 * 2 + 60 + 1, 0},

                {"PT0H", 0, 0},
                {"PT0H0M", 0, 0},
                {"PT0H0S", 0, 0},
                {"PT0H0M0S", 0, 0},

                {"PT1H", 3600, 0},
                {"PT3H", 3600 * 3, 0},
                {"PT-1H", -3600, 0},
                {"PT-3H", -3600 * 3, 0},

                {"PT2H5M", 3600 * 2 + 60 * 5, 0},
                {"PT2H5S", 3600 * 2 + 5, 0},
                {"PT2H5M8S", 3600 * 2 + 60 * 5 + 8, 0},
                {"PT-2H5M", -3600 * 2 + 60 * 5, 0},
                {"PT-2H5S", -3600 * 2 + 5, 0},
                {"PT-2H5M8S", -3600 * 2 + 60 * 5 + 8, 0},
                {"PT-2H-5M", -3600 * 2 - 60 * 5, 0},
                {"PT-2H-5S", -3600 * 2 - 5, 0},
                {"PT-2H-5M8S", -3600 * 2 - 60 * 5 + 8, 0},
                {"PT-2H-5M-8S", -3600 * 2 - 60 * 5 - 8, 0},

                {"PT0M", 0, 0},
                {"PT1M", 60, 0},
                {"PT3M", 60 * 3, 0},
                {"PT-1M", -60, 0},
                {"PT-3M", -60 * 3, 0},
                {"P0DT3M", 60 * 3, 0},
                {"P0DT-3M", -60 * 3, 0},
        };
    }

    @ParameterizedTest
    @MethodSource("data_parseSuccess")
    public void factory_parse(String text, long expectedSeconds, int expectedNanoOfSecond) {
        Duration test = Duration.parse(text);
        assertEquals(expectedSeconds, test.getSeconds());
        assertEquals(expectedNanoOfSecond, test.getNano());
    }

    @ParameterizedTest
    @MethodSource("data_parseSuccess")
    public void factory_parse_plus(String text, long expectedSeconds, int expectedNanoOfSecond) {
        Duration test = Duration.parse("+" + text);
        assertEquals(expectedSeconds, test.getSeconds());
        assertEquals(expectedNanoOfSecond, test.getNano());
    }

    @ParameterizedTest
    @MethodSource("data_parseSuccess")
    public void factory_parse_minus(String text, long expectedSeconds, int expectedNanoOfSecond) {
        Duration test;
        try {
            test = Duration.parse("-" + text);
        } catch (DateTimeParseException ex) {
            assertEquals(true, expectedSeconds == Long.MIN_VALUE);
            return;
        }
        // not inside try/catch or it breaks test
        assertEquals(Duration.ofSeconds(expectedSeconds, expectedNanoOfSecond).negated(), test);
    }

    @ParameterizedTest
    @MethodSource("data_parseSuccess")
    public void factory_parse_comma(String text, long expectedSeconds, int expectedNanoOfSecond) {
        text = text.replace('.', ',');
        Duration test = Duration.parse(text);
        assertEquals(expectedSeconds, test.getSeconds());
        assertEquals(expectedNanoOfSecond, test.getNano());
    }

    @ParameterizedTest
    @MethodSource("data_parseSuccess")
    public void factory_parse_lowerCase(String text, long expectedSeconds, int expectedNanoOfSecond) {
        Duration test = Duration.parse(text.toLowerCase(Locale.ENGLISH));
        assertEquals(expectedSeconds, test.getSeconds());
        assertEquals(expectedNanoOfSecond, test.getNano());
    }

    Object[][] data_parseFailure() {
        return new Object[][] {
                {""},
                {"ABCDEF"},
                {" PT0S"},
                {"PT0S "},

                {"PTS"},
                {"AT0S"},
                {"PA0S"},
                {"PT0A"},

                {"P0Y"},
                {"P1Y"},
                {"P-2Y"},
                {"P0M"},
                {"P1M"},
                {"P-2M"},
                {"P3Y2D"},
                {"P3M2D"},
                {"P3W"},
                {"P-3W"},
                {"P2YT30S"},
                {"P2MT30S"},

                {"P1DT"},

                {"PT+S"},
                {"PT-S"},
                {"PT.S"},
                {"PTAS"},

                {"PT-.S"},
                {"PT+.S"},

                {"PT1ABC2S"},
                {"PT1.1ABC2S"},

                {"PT123456789123456789123456789S"},
                {"PT0.1234567891S"},

                {"PT2.-3"},
                {"PT-2.-3"},
                {"PT2.+3"},
                {"PT-2.+3"},
        };
    }

    @ParameterizedTest
    @MethodSource("data_parseFailure")
    public void factory_parseFailures(String text) {
        Assertions.assertThrows(DateTimeParseException.class, () -> Duration.parse(text));
    }

    @ParameterizedTest
    @MethodSource("data_parseFailure")
    public void factory_parseFailures_comma(String text) {
        var commaText = text.replace('.', ',');
        Assertions.assertThrows(DateTimeParseException.class, () -> Duration.parse(commaText));
    }

    @Test
    public void factory_parse_tooBig() {
        Assertions.assertThrows(DateTimeParseException.class, () -> Duration.parse("PT" + Long.MAX_VALUE + "1S"));
    }

    @Test
    public void factory_parse_tooBig_decimal() {
        Assertions.assertThrows(DateTimeParseException.class, () -> Duration.parse("PT" + Long.MAX_VALUE + "1.1S"));
    }

    @Test
    public void factory_parse_tooSmall() {
        Assertions.assertThrows(DateTimeParseException.class, () -> Duration.parse("PT" + Long.MIN_VALUE + "1S"));
    }

    @Test
    public void factory_parse_tooSmall_decimal() {
        Assertions.assertThrows(DateTimeParseException.class, () -> Duration.parse("PT" + Long.MIN_VALUE + ".1S"));
    }

    @Test
    public void factory_parse_nullText() {
        Assertions.assertThrows(NullPointerException.class, () -> Duration.parse(null));
    }

    //-----------------------------------------------------------------------
    // between()
    //-----------------------------------------------------------------------
    Object[][] data_durationBetweenInstant() {
        return new Object[][] {
                {0, 0, 0, 0, 0, 0},
                {3, 0, 7, 0, 4, 0},
                {7, 0, 3, 0, -4, 0},

                {3, 20, 7, 50, 4, 30},
                {3, 80, 7, 50, 3, 999999970},
                {3, 80, 7, 79, 3, 999999999},
                {3, 80, 7, 80, 4, 0},
                {3, 80, 7, 81, 4, 1},
        };
    }

    @ParameterizedTest
    @MethodSource("data_durationBetweenInstant")
    public void factory_between_TemporalTemporal_Instant(long secs1, int nanos1, long secs2, int nanos2, long expectedSeconds, int expectedNanoOfSecond) {
        Instant start = Instant.ofEpochSecond(secs1, nanos1);
        Instant end = Instant.ofEpochSecond(secs2, nanos2);
        Duration t = Duration.between(start, end);
        assertEquals(expectedSeconds, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @ParameterizedTest
    @MethodSource("data_durationBetweenInstant")
    public void factory_between_TemporalTemporal_Instant_negated(long secs1, int nanos1, long secs2, int nanos2, long expectedSeconds, int expectedNanoOfSecond) {
        Instant start = Instant.ofEpochSecond(secs1, nanos1);
        Instant end = Instant.ofEpochSecond(secs2, nanos2);
        assertEquals(Duration.between(start, end).negated(), Duration.between(end, start));
    }

    Object[][] data_durationBetweenLocalTime() {
        return new Object[][] {
                {LocalTime.of(11, 0, 30), LocalTime.of(11, 0, 45), 15L, 0},
                {LocalTime.of(11, 0, 30), LocalTime.of(11, 0, 25), -5L, 0},
        };
    }

    @ParameterizedTest
    @MethodSource("data_durationBetweenLocalTime")
    public void factory_between_TemporalTemporal_LT(LocalTime start, LocalTime end, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.between(start, end);
        assertEquals(expectedSeconds, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @ParameterizedTest
    @MethodSource("data_durationBetweenLocalTime")
    public void factory_between_TemporalTemporal_LT_negated(LocalTime start, LocalTime end, long expectedSeconds, int expectedNanoOfSecond) {
        assertEquals(Duration.between(start, end).negated(), Duration.between(end, start));
    }

    Object[][] data_durationBetweenLocalDateTime() {
        return new Object[][] {
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 565_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 30, 65_000_000), -2L, 500_000_000},
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 565_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), -1L, 500_000_000},
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 565_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 32, 65_000_000), 0L, 500_000_000},
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 565_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 33, 65_000_000), 1L, 500_000_000},
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 565_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 34, 65_000_000), 2L, 500_000_000},

                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 30, 565_000_000), -1L, 500_000_000},
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 31, 565_000_000), 0L, 500_000_000},
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 32, 565_000_000), 1L, 500_000_000},
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 33, 565_000_000), 2L, 500_000_000},
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 34, 565_000_000), 3L, 500_000_000},

                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 30, 65_000_000), -1L, 0},
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), 0L, 0},
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 32, 65_000_000), 1L, 0},
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 33, 65_000_000), 2L, 0},
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), LocalDateTime.of(2013, 3, 24, 0, 44, 34, 65_000_000), 3L, 0},

                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), LocalDateTime.of(2813, 3, 24, 0, 44, 30, 565_000_000), 2 * CYCLE_SECS - 1L, 500_000_000},
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), LocalDateTime.of(2813, 3, 24, 0, 44, 31, 565_000_000), 2 * CYCLE_SECS + 0L, 500_000_000},
                {LocalDateTime.of(2013, 3, 24, 0, 44, 31, 65_000_000), LocalDateTime.of(2813, 3, 24, 0, 44, 32, 565_000_000), 2 * CYCLE_SECS + 1L, 500_000_000},
        };
    }

    @ParameterizedTest
    @MethodSource("data_durationBetweenLocalDateTime")
    public void factory_between_TemporalTemporal_LDT(LocalDateTime start, LocalDateTime end, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.between(start, end);
        assertEquals(expectedSeconds, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @ParameterizedTest
    @MethodSource("data_durationBetweenLocalDateTime")
    public void factory_between_TemporalTemporal_LDT_negated(LocalDateTime start, LocalDateTime end, long expectedSeconds, int expectedNanoOfSecond) {
        assertEquals(Duration.between(start, end).negated(), Duration.between(end, start));
    }

    @Test
    public void factory_between_TemporalTemporal_mixedTypes() {
        Instant start = Instant.ofEpochSecond(1);
        ZonedDateTime end = Instant.ofEpochSecond(4).atZone(ZoneOffset.UTC);
        assertEquals(Duration.ofSeconds(3), Duration.between(start, end));
    }

    @Test
    public void factory_between_TemporalTemporal_invalidMixedTypes() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant start = Instant.ofEpochSecond(1);
            LocalDate end = LocalDate.of(2010, 6, 20);
            Duration.between(start, end);
        });
    }

    @Test
    public void factory_between__TemporalTemporal_startNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Instant end = Instant.ofEpochSecond(1);
            Duration.between(null, end);
        });
    }

    @Test
    public void factory_between__TemporalTemporal_endNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Instant start = Instant.ofEpochSecond(1);
            Duration.between(start, null);
        });
    }

    //-----------------------------------------------------------------------
    // isZero(), isPositive(), isPositiveOrZero(), isNegative(), isNegativeOrZero()
    //-----------------------------------------------------------------------
    @Test
    public void test_isZero() {
        assertEquals(true, Duration.ofNanos(0).isZero());
        assertEquals(true, Duration.ofSeconds(0).isZero());
        assertEquals(false, Duration.ofNanos(1).isZero());
        assertEquals(false, Duration.ofSeconds(1).isZero());
        assertEquals(false, Duration.ofSeconds(1, 1).isZero());
        assertEquals(false, Duration.ofNanos(-1).isZero());
        assertEquals(false, Duration.ofSeconds(-1).isZero());
        assertEquals(false, Duration.ofSeconds(-1, -1).isZero());
    }

    @Test
    public void test_isPositive() {
        assertEquals(false, Duration.ofNanos(0).isPositive());
        assertEquals(false, Duration.ofSeconds(0).isPositive());
        assertEquals(true, Duration.ofNanos(1).isPositive());
        assertEquals(true, Duration.ofSeconds(1).isPositive());
        assertEquals(true, Duration.ofSeconds(1, 1).isPositive());
        assertEquals(true, Duration.ofSeconds(Long.MAX_VALUE, 999_999_999).isPositive());
        assertEquals(false, Duration.ofNanos(-1).isPositive());
        assertEquals(false, Duration.ofSeconds(-1).isPositive());
        assertEquals(false, Duration.ofSeconds(-1, -1).isPositive());
        assertEquals(false, Duration.ofSeconds(Long.MIN_VALUE).isPositive());
    }

    @Test
    public void test_isNegative() {
        assertEquals(false, Duration.ofNanos(0).isNegative());
        assertEquals(false, Duration.ofSeconds(0).isNegative());
        assertEquals(false, Duration.ofNanos(1).isNegative());
        assertEquals(false, Duration.ofSeconds(1).isNegative());
        assertEquals(false, Duration.ofSeconds(1, 1).isNegative());
        assertEquals(false, Duration.ofSeconds(Long.MAX_VALUE, 999_999_999).isNegative());
        assertEquals(true, Duration.ofNanos(-1).isNegative());
        assertEquals(true, Duration.ofSeconds(-1).isNegative());
        assertEquals(true, Duration.ofSeconds(-1, -1).isNegative());
        assertEquals(true, Duration.ofSeconds(Long.MIN_VALUE).isNegative());
    }

    //-----------------------------------------------------------------------
    // plus()
    //-----------------------------------------------------------------------
    Object[][] provider_plus() {
        return new Object[][] {
            {Long.MIN_VALUE, 0, Long.MAX_VALUE, 0, -1, 0},

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

            {Long.MAX_VALUE, 0, Long.MIN_VALUE, 0, -1, 0},
       };
    }

    @ParameterizedTest
    @MethodSource("provider_plus")
    public void plus(long seconds, int nanos, long otherSeconds, int otherNanos, long expectedSeconds, int expectedNanoOfSecond) {
       Duration t = Duration.ofSeconds(seconds, nanos).plus(Duration.ofSeconds(otherSeconds, otherNanos));
       assertEquals(expectedSeconds, t.getSeconds());
       assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @Test
    public void plusOverflowTooBig() {
       Assertions.assertThrows(ArithmeticException.class, () -> {
           Duration t = Duration.ofSeconds(Long.MAX_VALUE, 999999999);
           t.plus(Duration.ofSeconds(0, 1));
        });
    }

    @Test
    public void plusOverflowTooSmall() {
       Assertions.assertThrows(ArithmeticException.class, () -> {
           Duration t = Duration.ofSeconds(Long.MIN_VALUE);
           t.plus(Duration.ofSeconds(-1, 999999999));
        });
    }

    //-----------------------------------------------------------------------
    @Test
    public void plus_longTemporalUnit_seconds() {
        Duration t = Duration.ofSeconds(1);
        t = t.plus(1, SECONDS);
        assertEquals(2, t.getSeconds());
        assertEquals(0, t.getNano());
     }

    @Test
    public void plus_longTemporalUnit_millis() {
        Duration t = Duration.ofSeconds(1);
        t = t.plus(1, MILLIS);
        assertEquals(1, t.getSeconds());
        assertEquals(1000000, t.getNano());
     }

    @Test
    public void plus_longTemporalUnit_micros() {
        Duration t = Duration.ofSeconds(1);
        t = t.plus(1, MICROS);
        assertEquals(1, t.getSeconds());
        assertEquals(1000, t.getNano());
     }

    @Test
    public void plus_longTemporalUnit_nanos() {
        Duration t = Duration.ofSeconds(1);
        t = t.plus(1, NANOS);
        assertEquals(1, t.getSeconds());
        assertEquals(1, t.getNano());
     }

    @Test
    public void plus_longTemporalUnit_null() {
       Assertions.assertThrows(NullPointerException.class, () -> {
           Duration t = Duration.ofSeconds(1);
           t.plus(1, (TemporalUnit) null);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_plusDays_long() {
        return new Object[][] {
            {0, 0, 0},
            {0, 1, 1},
            {0, -1, -1},
            {Long.MAX_VALUE/3600/24, 0, Long.MAX_VALUE/3600/24},
            {Long.MIN_VALUE/3600/24, 0, Long.MIN_VALUE/3600/24},
            {1, 0, 1},
            {1, 1, 2},
            {1, -1, 0},
            {1, Long.MIN_VALUE/3600/24, Long.MIN_VALUE/3600/24 + 1},
            {1, 0, 1},
            {1, 1, 2},
            {1, -1, 0},
            {-1, 0, -1},
            {-1, 1, 0},
            {-1, -1, -2},
            {-1, Long.MAX_VALUE/3600/24, Long.MAX_VALUE/3600/24 - 1},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_plusDays_long")
    public void plusDays_long(long days, long amount, long expectedDays) {
        Duration t = Duration.ofDays(days);
        t = t.plusDays(amount);
        assertEquals(expectedDays, t.toDays());
    }

    @Test
    public void plusDays_long_overflowTooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofDays(1);
            t.plusDays(Long.MAX_VALUE/3600/24);
        });
    }

    @Test
    public void plusDays_long_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofDays(-1);
            t.plusDays(Long.MIN_VALUE/3600/24);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_plusHours_long() {
        return new Object[][] {
            {0, 0, 0},
            {0, 1, 1},
            {0, -1, -1},
            {Long.MAX_VALUE/3600, 0, Long.MAX_VALUE/3600},
            {Long.MIN_VALUE/3600, 0, Long.MIN_VALUE/3600},
            {1, 0, 1},
            {1, 1, 2},
            {1, -1, 0},
            {1, Long.MIN_VALUE/3600, Long.MIN_VALUE/3600 + 1},
            {1, 0, 1},
            {1, 1, 2},
            {1, -1, 0},
            {-1, 0, -1},
            {-1, 1, 0},
            {-1, -1, -2},
            {-1, Long.MAX_VALUE/3600, Long.MAX_VALUE/3600 - 1},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_plusHours_long")
    public void plusHours_long(long hours, long amount, long expectedHours) {
        Duration t = Duration.ofHours(hours);
        t = t.plusHours(amount);
        assertEquals(expectedHours, t.toHours());
    }

    @Test
    public void plusHours_long_overflowTooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofHours(1);
            t.plusHours(Long.MAX_VALUE/3600);
        });
    }

    @Test
    public void plusHours_long_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofHours(-1);
            t.plusHours(Long.MIN_VALUE/3600);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_plusMinutes_long() {
        return new Object[][] {
            {0, 0, 0},
            {0, 1, 1},
            {0, -1, -1},
            {Long.MAX_VALUE/60, 0, Long.MAX_VALUE/60},
            {Long.MIN_VALUE/60, 0, Long.MIN_VALUE/60},
            {1, 0, 1},
            {1, 1, 2},
            {1, -1, 0},
            {1, Long.MIN_VALUE/60, Long.MIN_VALUE/60 + 1},
            {1, 0, 1},
            {1, 1, 2},
            {1, -1, 0},
            {-1, 0, -1},
            {-1, 1, 0},
            {-1, -1, -2},
            {-1, Long.MAX_VALUE/60, Long.MAX_VALUE/60 - 1},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_plusMinutes_long")
    public void plusMinutes_long(long minutes, long amount, long expectedMinutes) {
        Duration t = Duration.ofMinutes(minutes);
        t = t.plusMinutes(amount);
        assertEquals(expectedMinutes, t.toMinutes());
    }

    @Test
    public void plusMinutes_long_overflowTooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofMinutes(1);
            t.plusMinutes(Long.MAX_VALUE/60);
        });
    }

    @Test
    public void plusMinutes_long_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofMinutes(-1);
            t.plusMinutes(Long.MIN_VALUE/60);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_plusSeconds_long() {
        return new Object[][] {
            {0, 0, 0, 0, 0},
            {0, 0, 1, 1, 0},
            {0, 0, -1, -1, 0},
            {0, 0, Long.MAX_VALUE, Long.MAX_VALUE, 0},
            {0, 0, Long.MIN_VALUE, Long.MIN_VALUE, 0},
            {1, 0, 0, 1, 0},
            {1, 0, 1, 2, 0},
            {1, 0, -1, 0, 0},
            {1, 0, Long.MAX_VALUE - 1, Long.MAX_VALUE, 0},
            {1, 0, Long.MIN_VALUE, Long.MIN_VALUE + 1, 0},
            {1, 1, 0, 1, 1},
            {1, 1, 1, 2, 1},
            {1, 1, -1, 0, 1},
            {1, 1, Long.MAX_VALUE - 1, Long.MAX_VALUE, 1},
            {1, 1, Long.MIN_VALUE, Long.MIN_VALUE + 1, 1},
            {-1, 1, 0, -1, 1},
            {-1, 1, 1, 0, 1},
            {-1, 1, -1, -2, 1},
            {-1, 1, Long.MAX_VALUE, Long.MAX_VALUE - 1, 1},
            {-1, 1, Long.MIN_VALUE + 1, Long.MIN_VALUE, 1},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_plusSeconds_long")
    public void plusSeconds_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.ofSeconds(seconds, nanos);
        t = t.plusSeconds(amount);
        assertEquals(expectedSeconds, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @Test
    public void plusSeconds_long_overflowTooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofSeconds(1, 0);
            t.plusSeconds(Long.MAX_VALUE);
        });
    }

    @Test
    public void plusSeconds_long_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofSeconds(-1, 0);
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
        };
    }

    @ParameterizedTest
    @MethodSource("provider_plusMillis_long")
    public void plusMillis_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.ofSeconds(seconds, nanos);
        t = t.plusMillis(amount);
        assertEquals(expectedSeconds, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }
    @ParameterizedTest
    @MethodSource("provider_plusMillis_long")
    public void plusMillis_long_oneMore(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.ofSeconds(seconds + 1, nanos);
        t = t.plusMillis(amount);
        assertEquals(expectedSeconds + 1, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }
    @ParameterizedTest
    @MethodSource("provider_plusMillis_long")
    public void plusMillis_long_minusOneLess(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.ofSeconds(seconds - 1, nanos);
        t = t.plusMillis(amount);
        assertEquals(expectedSeconds - 1, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @Test
    public void plusMillis_long_max() {
        Duration t = Duration.ofSeconds(Long.MAX_VALUE, 998999999);
        t = t.plusMillis(1);
        assertEquals(Long.MAX_VALUE, t.getSeconds());
        assertEquals(999999999, t.getNano());
    }

    @Test
    public void plusMillis_long_overflowTooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofSeconds(Long.MAX_VALUE, 999000000);
            t.plusMillis(1);
        });
    }

    @Test
    public void plusMillis_long_min() {
        Duration t = Duration.ofSeconds(Long.MIN_VALUE, 1000000);
        t = t.plusMillis(-1);
        assertEquals(Long.MIN_VALUE, t.getSeconds());
        assertEquals(0, t.getNano());
    }

    @Test
    public void plusMillis_long_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofSeconds(Long.MIN_VALUE, 0);
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

            {Long.MAX_VALUE, 0, 999999999, Long.MAX_VALUE, 999999999},
            {Long.MAX_VALUE - 1, 0, 1999999999, Long.MAX_VALUE, 999999999},
            {Long.MIN_VALUE, 1, -1, Long.MIN_VALUE, 0},
            {Long.MIN_VALUE + 1, 1, -1000000001, Long.MIN_VALUE, 0},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_plusNanos_long")
    public void plusNanos_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.ofSeconds(seconds, nanos);
        t = t.plusNanos(amount);
        assertEquals(expectedSeconds, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @Test
    public void plusNanos_long_overflowTooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofSeconds(Long.MAX_VALUE, 999999999);
            t.plusNanos(1);
        });
    }

    @Test
    public void plusNanos_long_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofSeconds(Long.MIN_VALUE, 0);
            t.plusNanos(-1);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_minus() {
        return new Object[][] {
            {Long.MIN_VALUE, 0, Long.MIN_VALUE + 1, 0, -1, 0},

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

            {Long.MAX_VALUE, 0, Long.MAX_VALUE, 0, 0, 0},
       };
    }

    @ParameterizedTest
    @MethodSource("provider_minus")
    public void minus(long seconds, int nanos, long otherSeconds, int otherNanos, long expectedSeconds, int expectedNanoOfSecond) {
       Duration t = Duration.ofSeconds(seconds, nanos).minus(Duration.ofSeconds(otherSeconds, otherNanos));
       assertEquals(expectedSeconds, t.getSeconds());
       assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @Test
    public void minusOverflowTooSmall() {
       Assertions.assertThrows(ArithmeticException.class, () -> {
           Duration t = Duration.ofSeconds(Long.MIN_VALUE);
           t.minus(Duration.ofSeconds(0, 1));
        });
    }

    @Test
    public void minusOverflowTooBig() {
       Assertions.assertThrows(ArithmeticException.class, () -> {
           Duration t = Duration.ofSeconds(Long.MAX_VALUE, 999999999);
           t.minus(Duration.ofSeconds(-1, 999999999));
        });
    }

    //-----------------------------------------------------------------------
    @Test
    public void minus_longTemporalUnit_seconds() {
        Duration t = Duration.ofSeconds(1);
        t = t.minus(1, SECONDS);
        assertEquals(0, t.getSeconds());
        assertEquals(0, t.getNano());
     }

    @Test
    public void minus_longTemporalUnit_millis() {
        Duration t = Duration.ofSeconds(1);
        t = t.minus(1, MILLIS);
        assertEquals(0, t.getSeconds());
        assertEquals(999000000, t.getNano());
     }

    @Test
    public void minus_longTemporalUnit_micros() {
        Duration t = Duration.ofSeconds(1);
        t = t.minus(1, MICROS);
        assertEquals(0, t.getSeconds());
        assertEquals(999999000, t.getNano());
     }

    @Test
    public void minus_longTemporalUnit_nanos() {
        Duration t = Duration.ofSeconds(1);
        t = t.minus(1, NANOS);
        assertEquals(0, t.getSeconds());
        assertEquals(999999999, t.getNano());
     }

    @Test
    public void minus_longTemporalUnit_null() {
       Assertions.assertThrows(NullPointerException.class, () -> {
           Duration t = Duration.ofSeconds(1);
           t.minus(1, (TemporalUnit) null);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_minusDays_long() {
        return new Object[][] {
            {0, 0, 0},
            {0, 1, -1},
            {0, -1, 1},
            {Long.MAX_VALUE/3600/24, 0, Long.MAX_VALUE/3600/24},
            {Long.MIN_VALUE/3600/24, 0, Long.MIN_VALUE/3600/24},
            {1, 0, 1},
            {1, 1, 0},
            {1, -1, 2},
            {Long.MAX_VALUE/3600/24, 1, Long.MAX_VALUE/3600/24 - 1},
            {Long.MIN_VALUE/3600/24, -1, Long.MIN_VALUE/3600/24 + 1},
            {1, 0, 1},
            {1, 1, 0},
            {1, -1, 2},
            {-1, 0, -1},
            {-1, 1, -2},
            {-1, -1, 0},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_minusDays_long")
    public void minusDays_long(long days, long amount, long expectedDays) {
        Duration t = Duration.ofDays(days);
        t = t.minusDays(amount);
        assertEquals(expectedDays, t.toDays());
    }

    @Test
    public void minusDays_long_overflowTooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofDays(Long.MAX_VALUE/3600/24);
            t.minusDays(-1);
        });
    }

    @Test
    public void minusDays_long_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofDays(Long.MIN_VALUE/3600/24);
            t.minusDays(1);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_minusHours_long() {
        return new Object[][] {
            {0, 0, 0},
            {0, 1, -1},
            {0, -1, 1},
            {Long.MAX_VALUE/3600, 0, Long.MAX_VALUE/3600},
            {Long.MIN_VALUE/3600, 0, Long.MIN_VALUE/3600},
            {1, 0, 1},
            {1, 1, 0},
            {1, -1, 2},
            {Long.MAX_VALUE/3600, 1, Long.MAX_VALUE/3600 - 1},
            {Long.MIN_VALUE/3600, -1, Long.MIN_VALUE/3600 + 1},
            {1, 0, 1},
            {1, 1, 0},
            {1, -1, 2},
            {-1, 0, -1},
            {-1, 1, -2},
            {-1, -1, 0},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_minusHours_long")
    public void minusHours_long(long hours, long amount, long expectedHours) {
        Duration t = Duration.ofHours(hours);
        t = t.minusHours(amount);
        assertEquals(expectedHours, t.toHours());
    }

    @Test
    public void minusHours_long_overflowTooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofHours(Long.MAX_VALUE/3600);
            t.minusHours(-1);
        });
    }

    @Test
    public void minusHours_long_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofHours(Long.MIN_VALUE/3600);
            t.minusHours(1);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_minusminutes_long() {
        return new Object[][] {
            {0, 0, 0},
            {0, 1, -1},
            {0, -1, 1},
            {Long.MAX_VALUE/60, 0, Long.MAX_VALUE/60},
            {Long.MIN_VALUE/60, 0, Long.MIN_VALUE/60},
            {1, 0, 1},
            {1, 1, 0},
            {1, -1, 2},
            {Long.MAX_VALUE/60, 1, Long.MAX_VALUE/60 - 1},
            {Long.MIN_VALUE/60, -1, Long.MIN_VALUE/60 + 1},
            {1, 0, 1},
            {1, 1, 0},
            {1, -1, 2},
            {-1, 0, -1},
            {-1, 1, -2},
            {-1, -1, 0},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_minusminutes_long")
    public void minusMinutes_long(long minutes, long amount, long expectedMinutes) {
        Duration t = Duration.ofMinutes(minutes);
        t = t.minusMinutes(amount);
        assertEquals(expectedMinutes, t.toMinutes());
    }

    @Test
    public void minusMinutes_long_overflowTooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofMinutes(Long.MAX_VALUE/60);
            t.minusMinutes(-1);
        });
    }

    @Test
    public void minusMinutes_long_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofMinutes(Long.MIN_VALUE/60);
            t.minusMinutes(1);
        });
    }

    //-----------------------------------------------------------------------
    Object[][] provider_minusSeconds_long() {
        return new Object[][] {
            {0, 0, 0, 0, 0},
            {0, 0, 1, -1, 0},
            {0, 0, -1, 1, 0},
            {0, 0, Long.MAX_VALUE, -Long.MAX_VALUE, 0},
            {0, 0, Long.MIN_VALUE + 1, Long.MAX_VALUE, 0},
            {1, 0, 0, 1, 0},
            {1, 0, 1, 0, 0},
            {1, 0, -1, 2, 0},
            {1, 0, Long.MAX_VALUE - 1, -Long.MAX_VALUE + 2, 0},
            {1, 0, Long.MIN_VALUE + 2, Long.MAX_VALUE, 0},
            {1, 1, 0, 1, 1},
            {1, 1, 1, 0, 1},
            {1, 1, -1, 2, 1},
            {1, 1, Long.MAX_VALUE, -Long.MAX_VALUE + 1, 1},
            {1, 1, Long.MIN_VALUE + 2, Long.MAX_VALUE, 1},
            {-1, 1, 0, -1, 1},
            {-1, 1, 1, -2, 1},
            {-1, 1, -1, 0, 1},
            {-1, 1, Long.MAX_VALUE, Long.MIN_VALUE, 1},
            {-1, 1, Long.MIN_VALUE + 1, Long.MAX_VALUE - 1, 1},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_minusSeconds_long")
    public void minusSeconds_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.ofSeconds(seconds, nanos);
        t = t.minusSeconds(amount);
        assertEquals(expectedSeconds, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @Test
    public void minusSeconds_long_overflowTooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofSeconds(1, 0);
            t.minusSeconds(Long.MIN_VALUE + 1);
        });
    }

    @Test
    public void minusSeconds_long_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofSeconds(-2, 0);
            t.minusSeconds(Long.MAX_VALUE);
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
        };
    }

    @ParameterizedTest
    @MethodSource("provider_minusMillis_long")
    public void minusMillis_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.ofSeconds(seconds, nanos);
        t = t.minusMillis(amount);
        assertEquals(expectedSeconds, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }
    @ParameterizedTest
    @MethodSource("provider_minusMillis_long")
    public void minusMillis_long_oneMore(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.ofSeconds(seconds + 1, nanos);
        t = t.minusMillis(amount);
        assertEquals(expectedSeconds + 1, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }
    @ParameterizedTest
    @MethodSource("provider_minusMillis_long")
    public void minusMillis_long_minusOneLess(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.ofSeconds(seconds - 1, nanos);
        t = t.minusMillis(amount);
        assertEquals(expectedSeconds - 1, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @Test
    public void minusMillis_long_max() {
        Duration t = Duration.ofSeconds(Long.MAX_VALUE, 998999999);
        t = t.minusMillis(-1);
        assertEquals(Long.MAX_VALUE, t.getSeconds());
        assertEquals(999999999, t.getNano());
    }

    @Test
    public void minusMillis_long_overflowTooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofSeconds(Long.MAX_VALUE, 999000000);
            t.minusMillis(-1);
        });
    }

    @Test
    public void minusMillis_long_min() {
        Duration t = Duration.ofSeconds(Long.MIN_VALUE, 1000000);
        t = t.minusMillis(1);
        assertEquals(Long.MIN_VALUE, t.getSeconds());
        assertEquals(0, t.getNano());
    }

    @Test
    public void minusMillis_long_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofSeconds(Long.MIN_VALUE, 0);
            t.minusMillis(1);
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

            {Long.MAX_VALUE, 0, -999999999, Long.MAX_VALUE, 999999999},
            {Long.MAX_VALUE - 1, 0, -1999999999, Long.MAX_VALUE, 999999999},
            {Long.MIN_VALUE, 1, 1, Long.MIN_VALUE, 0},
            {Long.MIN_VALUE + 1, 1, 1000000001, Long.MIN_VALUE, 0},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_minusNanos_long")
    public void minusNanos_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.ofSeconds(seconds, nanos);
        t = t.minusNanos(amount);
        assertEquals(expectedSeconds, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    @Test
    public void minusNanos_long_overflowTooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofSeconds(Long.MAX_VALUE, 999999999);
            t.minusNanos(-1);
        });
    }

    @Test
    public void minusNanos_long_overflowTooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration t = Duration.ofSeconds(Long.MIN_VALUE, 0);
            t.minusNanos(1);
        });
    }

    //-----------------------------------------------------------------------
    // multipliedBy()
    //-----------------------------------------------------------------------
    Object[][] provider_multipliedBy() {
       return new Object[][] {
          {-4, 666666667, -3,   9, 999999999},
          {-4, 666666667, -2,   6, 666666666},
          {-4, 666666667, -1,   3, 333333333},
          {-4, 666666667,  0,   0,         0},
          {-4, 666666667,  1,  -4, 666666667},
          {-4, 666666667,  2,  -7, 333333334},
          {-4, 666666667,  3, -10, 000000001},

          {-3, 0, -3,  9, 0},
          {-3, 0, -2,  6, 0},
          {-3, 0, -1,  3, 0},
          {-3, 0,  0,  0, 0},
          {-3, 0,  1, -3, 0},
          {-3, 0,  2, -6, 0},
          {-3, 0,  3, -9, 0},

          {-2, 0, -3,  6, 0},
          {-2, 0, -2,  4, 0},
          {-2, 0, -1,  2, 0},
          {-2, 0,  0,  0, 0},
          {-2, 0,  1, -2, 0},
          {-2, 0,  2, -4, 0},
          {-2, 0,  3, -6, 0},

          {-1, 0, -3,  3, 0},
          {-1, 0, -2,  2, 0},
          {-1, 0, -1,  1, 0},
          {-1, 0,  0,  0, 0},
          {-1, 0,  1, -1, 0},
          {-1, 0,  2, -2, 0},
          {-1, 0,  3, -3, 0},

          {-1, 500000000, -3,  1, 500000000},
          {-1, 500000000, -2,  1,         0},
          {-1, 500000000, -1,  0, 500000000},
          {-1, 500000000,  0,  0,         0},
          {-1, 500000000,  1, -1, 500000000},
          {-1, 500000000,  2, -1,         0},
          {-1, 500000000,  3, -2, 500000000},

          {0, 0, -3, 0, 0},
          {0, 0, -2, 0, 0},
          {0, 0, -1, 0, 0},
          {0, 0,  0, 0, 0},
          {0, 0,  1, 0, 0},
          {0, 0,  2, 0, 0},
          {0, 0,  3, 0, 0},

          {0, 500000000, -3, -2, 500000000},
          {0, 500000000, -2, -1,         0},
          {0, 500000000, -1, -1, 500000000},
          {0, 500000000,  0,  0,         0},
          {0, 500000000,  1,  0, 500000000},
          {0, 500000000,  2,  1,         0},
          {0, 500000000,  3,  1, 500000000},

          {1, 0, -3, -3, 0},
          {1, 0, -2, -2, 0},
          {1, 0, -1, -1, 0},
          {1, 0,  0,  0, 0},
          {1, 0,  1,  1, 0},
          {1, 0,  2,  2, 0},
          {1, 0,  3,  3, 0},

          {2, 0, -3, -6, 0},
          {2, 0, -2, -4, 0},
          {2, 0, -1, -2, 0},
          {2, 0,  0,  0, 0},
          {2, 0,  1,  2, 0},
          {2, 0,  2,  4, 0},
          {2, 0,  3,  6, 0},

          {3, 0, -3, -9, 0},
          {3, 0, -2, -6, 0},
          {3, 0, -1, -3, 0},
          {3, 0,  0,  0, 0},
          {3, 0,  1,  3, 0},
          {3, 0,  2,  6, 0},
          {3, 0,  3,  9, 0},

          {3, 333333333, -3, -10, 000000001},
          {3, 333333333, -2,  -7, 333333334},
          {3, 333333333, -1,  -4, 666666667},
          {3, 333333333,  0,   0,         0},
          {3, 333333333,  1,   3, 333333333},
          {3, 333333333,  2,   6, 666666666},
          {3, 333333333,  3,   9, 999999999},
       };
    }

    @ParameterizedTest
    @MethodSource("provider_multipliedBy")
    public void multipliedBy(long seconds, int nanos, int multiplicand, long expectedSeconds, int expectedNanos) {
        Duration t = Duration.ofSeconds(seconds, nanos);
        t = t.multipliedBy(multiplicand);
        assertEquals(expectedSeconds, t.getSeconds());
        assertEquals(expectedNanos, t.getNano());
    }

    @Test
    public void multipliedBy_max() {
        Duration test = Duration.ofSeconds(1);
        assertEquals(Duration.ofSeconds(Long.MAX_VALUE), test.multipliedBy(Long.MAX_VALUE));
    }

    @Test
    public void multipliedBy_min() {
        Duration test = Duration.ofSeconds(1);
        assertEquals(Duration.ofSeconds(Long.MIN_VALUE), test.multipliedBy(Long.MIN_VALUE));
    }

    @Test
    public void multipliedBy_tooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration test = Duration.ofSeconds(1, 1);
            test.multipliedBy(Long.MAX_VALUE);
        });
    }

    @Test
    public void multipliedBy_tooBig_negative() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration test = Duration.ofSeconds(1, 1);
            test.multipliedBy(Long.MIN_VALUE);
        });
    }

    //-----------------------------------------------------------------------
    //  truncated(TemporalUnit)
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
                {Duration.ofSeconds(86400 + 3600 + 60 + 1, 123_456_789), NANOS, Duration.ofSeconds(86400 + 3600 + 60 + 1, 123_456_789)},
                {Duration.ofSeconds(86400 + 3600 + 60 + 1, 123_456_789), MICROS, Duration.ofSeconds(86400 + 3600 + 60 + 1, 123_456_000)},
                {Duration.ofSeconds(86400 + 3600 + 60 + 1, 123_456_789), MILLIS, Duration.ofSeconds(86400 + 3600 + 60 + 1, 1230_00_000)},
                {Duration.ofSeconds(86400 + 3600 + 60 + 1, 123_456_789), SECONDS, Duration.ofSeconds(86400 + 3600 + 60 + 1, 0)},
                {Duration.ofSeconds(86400 + 3600 + 60 + 1, 123_456_789), MINUTES, Duration.ofSeconds(86400 + 3600 + 60, 0)},
                {Duration.ofSeconds(86400 + 3600 + 60 + 1, 123_456_789), HOURS, Duration.ofSeconds(86400 + 3600, 0)},
                {Duration.ofSeconds(86400 + 3600 + 60 + 1, 123_456_789), DAYS, Duration.ofSeconds(86400, 0)},

                {Duration.ofSeconds(86400 + 3600 + 60 + 1, 123_456_789), NINETY_MINS, Duration.ofSeconds(86400 + 0, 0)},
                {Duration.ofSeconds(86400 + 7200 + 60 + 1, 123_456_789), NINETY_MINS, Duration.ofSeconds(86400 + 5400, 0)},
                {Duration.ofSeconds(86400 + 10800 + 60 + 1, 123_456_789), NINETY_MINS, Duration.ofSeconds(86400 + 10800, 0)},

                {Duration.ofSeconds(-86400 - 3600 - 60 - 1, 123_456_789), MINUTES, Duration.ofSeconds(-86400 - 3600 - 60, 0 )},
                {Duration.ofSeconds(-86400 - 3600 - 60 - 1, 123_456_789), MICROS, Duration.ofSeconds(-86400 - 3600 - 60 - 1, 123_457_000)},

                {Duration.ofSeconds(86400 + 3600 + 60 + 1, 0), SECONDS, Duration.ofSeconds(86400 + 3600 + 60 + 1, 0)},
                {Duration.ofSeconds(-86400 - 3600 - 120, 0), MINUTES, Duration.ofSeconds(-86400 - 3600 - 120, 0)},

                {Duration.ofSeconds(-1, 0), SECONDS, Duration.ofSeconds(-1, 0)},
                {Duration.ofSeconds(-1, 123_456_789), SECONDS, Duration.ofSeconds(0, 0)},
                {Duration.ofSeconds(-1, 123_456_789), NANOS, Duration.ofSeconds(0, -876_543_211)},
                {Duration.ofSeconds(0, 123_456_789), SECONDS, Duration.ofSeconds(0, 0)},
                {Duration.ofSeconds(0, 123_456_789), NANOS, Duration.ofSeconds(0, 123_456_789)},
        };
    }

    @ParameterizedTest
    @MethodSource("data_truncatedToValid")
    public void test_truncatedTo_valid(Duration input, TemporalUnit unit, Duration expected) {
        assertEquals(expected, input.truncatedTo(unit));
    }

    Object[][] data_truncatedToInvalid() {
        return new Object[][] {
                {Duration.ofSeconds(1, 123_456_789), NINETY_FIVE_MINS},
                {Duration.ofSeconds(1, 123_456_789), WEEKS},
                {Duration.ofSeconds(1, 123_456_789), MONTHS},
                {Duration.ofSeconds(1, 123_456_789), YEARS},
        };
    }

    @ParameterizedTest
    @MethodSource("data_truncatedToInvalid")
    public void test_truncatedTo_invalid(Duration input, TemporalUnit unit) {
        Assertions.assertThrows(DateTimeException.class, () -> input.truncatedTo(unit));
    }

    @Test
    public void test_truncatedTo_null() {
        Assertions.assertThrows(NullPointerException.class, () -> Duration.ofSeconds(1234).truncatedTo(null));
    }

    //-----------------------------------------------------------------------
    // dividedBy()
    //-----------------------------------------------------------------------
    Object[][] provider_dividedBy() {
       return new Object[][] {
          {-4, 666666667, -3,  1, 111111111},
          {-4, 666666667, -2,  1, 666666666},
          {-4, 666666667, -1,  3, 333333333},
          {-4, 666666667,  1, -4, 666666667},
          {-4, 666666667,  2, -2, 333333334},
          {-4, 666666667,  3, -2, 888888889},

          {-3, 0, -3,  1, 0},
          {-3, 0, -2,  1, 500000000},
          {-3, 0, -1,  3, 0},
          {-3, 0,  1, -3, 0},
          {-3, 0,  2, -2, 500000000},
          {-3, 0,  3, -1, 0},

          {-2, 0, -3,  0, 666666666},
          {-2, 0, -2,  1,         0},
          {-2, 0, -1,  2,         0},
          {-2, 0,  1, -2,         0},
          {-2, 0,  2, -1,         0},
          {-2, 0,  3, -1, 333333334},

          {-1, 0, -3,  0, 333333333},
          {-1, 0, -2,  0, 500000000},
          {-1, 0, -1,  1,         0},
          {-1, 0,  1, -1,         0},
          {-1, 0,  2, -1, 500000000},
          {-1, 0,  3, -1, 666666667},

          {-1, 500000000, -3,  0, 166666666},
          {-1, 500000000, -2,  0, 250000000},
          {-1, 500000000, -1,  0, 500000000},
          {-1, 500000000,  1, -1, 500000000},
          {-1, 500000000,  2, -1, 750000000},
          {-1, 500000000,  3, -1, 833333334},

          {0, 0, -3, 0, 0},
          {0, 0, -2, 0, 0},
          {0, 0, -1, 0, 0},
          {0, 0,  1, 0, 0},
          {0, 0,  2, 0, 0},
          {0, 0,  3, 0, 0},

          {0, 500000000, -3, -1, 833333334},
          {0, 500000000, -2, -1, 750000000},
          {0, 500000000, -1, -1, 500000000},
          {0, 500000000,  1,  0, 500000000},
          {0, 500000000,  2,  0, 250000000},
          {0, 500000000,  3,  0, 166666666},

          {1, 0, -3, -1, 666666667},
          {1, 0, -2, -1, 500000000},
          {1, 0, -1, -1,         0},
          {1, 0,  1,  1,         0},
          {1, 0,  2,  0, 500000000},
          {1, 0,  3,  0, 333333333},

          {2, 0, -3, -1, 333333334},
          {2, 0, -2, -1,         0},
          {2, 0, -1, -2,         0},
          {2, 0,  1,  2,         0},
          {2, 0,  2,  1,         0},
          {2, 0,  3,  0, 666666666},

          {3, 0, -3, -1,         0},
          {3, 0, -2, -2, 500000000},
          {3, 0, -1, -3,         0},
          {3, 0,  1,  3,         0},
          {3, 0,  2,  1, 500000000},
          {3, 0,  3,  1,         0},

          {3, 333333333, -3, -2, 888888889},
          {3, 333333333, -2, -2, 333333334},
          {3, 333333333, -1, -4, 666666667},
          {3, 333333333,  1,  3, 333333333},
          {3, 333333333,  2,  1, 666666666},
          {3, 333333333,  3,  1, 111111111},
       };
    }

    @ParameterizedTest
    @MethodSource("provider_dividedBy")
    public void dividedBy(long seconds, int nanos, int divisor, long expectedSeconds, int expectedNanos) {
        Duration t = Duration.ofSeconds(seconds, nanos);
        t = t.dividedBy(divisor);
        assertEquals(expectedSeconds, t.getSeconds());
        assertEquals(expectedNanos, t.getNano());
    }

    @ParameterizedTest
    @MethodSource("provider_dividedBy")
    public void dividedByZero(long seconds, int nanos, int divisor, long expectedSeconds, int expectedNanos) {
       Assertions.assertThrows(ArithmeticException.class, () -> {
           Duration t = Duration.ofSeconds(seconds, nanos);
           t.dividedBy(0);
            fail(t + " divided by zero did not throw ArithmeticException");
        });
    }

    @Test
    public void dividedBy_max() {
        Duration test = Duration.ofSeconds(Long.MAX_VALUE);
        assertEquals(Duration.ofSeconds(1), test.dividedBy(Long.MAX_VALUE));
    }

    //-----------------------------------------------------------------------
    // dividedbyDur()
    //-----------------------------------------------------------------------

    Object[][] provider_dividedByDur() {
        return new Object[][] {
            {Duration.ofSeconds(0, 0), Duration.ofSeconds(1, 0), 0},
            {Duration.ofSeconds(1, 0), Duration.ofSeconds(1, 0), 1},
            {Duration.ofSeconds(6, 0), Duration.ofSeconds(3, 0), 2},
            {Duration.ofSeconds(3, 0), Duration.ofSeconds(6, 0), 0},
            {Duration.ofSeconds(7, 0), Duration.ofSeconds(3, 0), 2},

            {Duration.ofSeconds(0, 333_333_333), Duration.ofSeconds(0, 333_333_333), 1},
            {Duration.ofSeconds(0, 666_666_666), Duration.ofSeconds(0, 333_333_333), 2},
            {Duration.ofSeconds(0, 333_333_333), Duration.ofSeconds(0, 666_666_666), 0},
            {Duration.ofSeconds(0, 777_777_777), Duration.ofSeconds(0, 333_333_333), 2},

            {Duration.ofSeconds(-7, 0), Duration.ofSeconds(3, 0), -2},
            {Duration.ofSeconds(0, 7), Duration.ofSeconds(0, -3), -2},
            {Duration.ofSeconds(0, -777_777_777), Duration.ofSeconds(0, 333_333_333), -2},

            {Duration.ofSeconds(432000L, -777_777_777L), Duration.ofSeconds(14400L, 333_333_333L), 29},
            {Duration.ofSeconds(-432000L, 777_777_777L), Duration.ofSeconds(14400L, 333_333_333L), -29},
            {Duration.ofSeconds(-432000L, -777_777_777L), Duration.ofSeconds(14400L, 333_333_333L), -29},
            {Duration.ofSeconds(-432000L, -777_777_777L), Duration.ofSeconds(14400L, -333_333_333L), -30},
            {Duration.ofSeconds(432000L, -777_777_777L), Duration.ofSeconds(-14400L, 333_333_333L), -30},
            {Duration.ofSeconds(432000L, -777_777_777L), Duration.ofSeconds(-14400L, -333_333_333L), -29},
            {Duration.ofSeconds(-432000L, -777_777_777L), Duration.ofSeconds(-14400L, -333_333_333L), 29},

            {Duration.ofSeconds(Long.MAX_VALUE, 0), Duration.ofSeconds(1, 0), Long.MAX_VALUE},
            {Duration.ofSeconds(Long.MAX_VALUE, 0), Duration.ofSeconds(Long.MAX_VALUE, 0), 1},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_dividedByDur")
    public void test_dividedByDur(Duration dividend, Duration divisor, long expected) {
        assertEquals(expected, dividend.dividedBy(divisor));
    }

    @Test
    public void test_dividedByDur_zero() {
       Assertions.assertThrows(ArithmeticException.class, () -> {
           Duration t = Duration.ofSeconds(1, 0);
           t.dividedBy(Duration.ZERO);
        });
    }

    @Test
    public void test_dividedByDur_null() {
       Assertions.assertThrows(NullPointerException.class, () -> {
           Duration t = Duration.ofSeconds(1, 0);
           t.dividedBy(null);
        });
    }

    @Test
    public void test_dividedByDur_overflow() {
       Assertions.assertThrows(ArithmeticException.class, () -> {
           Duration dur1 = Duration.ofSeconds(Long.MAX_VALUE, 0);
           Duration dur2 = Duration.ofNanos(1);
           dur1.dividedBy(dur2);
        });
    }

    //-----------------------------------------------------------------------
    // negated()
    //-----------------------------------------------------------------------
    @Test
    public void test_negated() {
        assertEquals(Duration.ofSeconds(0), Duration.ofSeconds(0).negated());
        assertEquals(Duration.ofSeconds(-12), Duration.ofSeconds(12).negated());
        assertEquals(Duration.ofSeconds(12), Duration.ofSeconds(-12).negated());
        assertEquals(Duration.ofSeconds(-12, -20), Duration.ofSeconds(12, 20).negated());
        assertEquals(Duration.ofSeconds(-12, 20), Duration.ofSeconds(12, -20).negated());
        assertEquals(Duration.ofSeconds(12, 20), Duration.ofSeconds(-12, -20).negated());
        assertEquals(Duration.ofSeconds(12, -20), Duration.ofSeconds(-12, 20).negated());
        assertEquals(Duration.ofSeconds(-Long.MAX_VALUE), Duration.ofSeconds(Long.MAX_VALUE).negated());
    }

    @Test
    public void test_negated_overflow() {
        Assertions.assertThrows(ArithmeticException.class, () -> Duration.ofSeconds(Long.MIN_VALUE).negated());
    }

    //-----------------------------------------------------------------------
    // abs()
    //-----------------------------------------------------------------------
    @Test
    public void test_abs() {
        assertEquals(Duration.ofSeconds(0), Duration.ofSeconds(0).abs());
        assertEquals(Duration.ofSeconds(12), Duration.ofSeconds(12).abs());
        assertEquals(Duration.ofSeconds(12), Duration.ofSeconds(-12).abs());
        assertEquals(Duration.ofSeconds(12, 20), Duration.ofSeconds(12, 20).abs());
        assertEquals(Duration.ofSeconds(12, -20), Duration.ofSeconds(12, -20).abs());
        assertEquals(Duration.ofSeconds(12, 20), Duration.ofSeconds(-12, -20).abs());
        assertEquals(Duration.ofSeconds(12, -20), Duration.ofSeconds(-12, 20).abs());
        assertEquals(Duration.ofSeconds(Long.MAX_VALUE), Duration.ofSeconds(Long.MAX_VALUE).abs());
    }

    @Test
    public void test_abs_overflow() {
        Assertions.assertThrows(ArithmeticException.class, () -> Duration.ofSeconds(Long.MIN_VALUE).abs());
    }

    //-----------------------------------------------------------------------
    // toNanos()
    //-----------------------------------------------------------------------
    @Test
    public void test_toNanos() {
        assertEquals(321123456789L, Duration.ofSeconds(321, 123456789).toNanos());
        assertEquals(9223372036854775807L, Duration.ofNanos(Long.MAX_VALUE).toNanos());
        assertEquals(-9223372036854775808L, Duration.ofNanos(Long.MIN_VALUE).toNanos());
    }

    @Test
    public void test_toNanos_max() {
        Duration test = Duration.ofSeconds(0, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, test.toNanos());
    }

    @Test
    public void test_toNanos_tooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration test = Duration.ofSeconds(0, Long.MAX_VALUE).plusNanos(1);
            test.toNanos();
        });
    }

    @Test
    public void test_toNanos_min() {
        Duration test = Duration.ofSeconds(0, Long.MIN_VALUE);
        assertEquals(Long.MIN_VALUE, test.toNanos());
    }

    @Test
    public void test_toNanos_tooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration test = Duration.ofSeconds(0, Long.MIN_VALUE).minusNanos(1);
            test.toNanos();
        });
    }

    //-----------------------------------------------------------------------
    // toMillis()
    //-----------------------------------------------------------------------
    @Test
    public void test_toMillis() {
        assertEquals(321000 + 123, Duration.ofSeconds(321, 123456789).toMillis());
        assertEquals(9223372036854775807L, Duration.ofMillis(Long.MAX_VALUE).toMillis());
        assertEquals(-9223372036854775808L, Duration.ofMillis(Long.MIN_VALUE).toMillis());
    }

    @Test
    public void test_toMillis_max() {
        Duration test = Duration.ofSeconds(Long.MAX_VALUE / 1000, (Long.MAX_VALUE % 1000) * 1000000);
        assertEquals(Long.MAX_VALUE, test.toMillis());
    }

    @Test
    public void test_toMillis_tooBig() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration test = Duration.ofSeconds(Long.MAX_VALUE / 1000, ((Long.MAX_VALUE % 1000) + 1) * 1000000);
            test.toMillis();
        });
    }

    @Test
    public void test_toMillis_min() {
        Duration test = Duration.ofSeconds(Long.MIN_VALUE / 1000, (Long.MIN_VALUE % 1000) * 1000000);
        assertEquals(Long.MIN_VALUE, test.toMillis());
    }

    @Test
    public void test_toMillis_tooSmall() {
        Assertions.assertThrows(ArithmeticException.class, () -> {
            Duration test = Duration.ofSeconds(Long.MIN_VALUE / 1000, ((Long.MIN_VALUE % 1000) - 1) * 1000000);
            test.toMillis();
        });
    }

    //-----------------------------------------------------------------------
    // toSeconds()
    //-----------------------------------------------------------------------
    Object[][] provider_toSeconds() {
        return new Object[][] {
            {Duration.ofSeconds(365 * 86400 + 5 * 3600 + 48 * 60 + 46, 123_456_789), 31556926L},
            {Duration.ofSeconds(-365 * 86400 - 5 * 3600 - 48 * 60 - 46, -123_456_789), -31556927L},
            {Duration.ofSeconds(-365 * 86400 - 5 * 3600 - 48 * 60 - 46, 123_456_789), -31556926L},
            {Duration.ofSeconds(0), 0L},
            {Duration.ofSeconds(0, 123_456_789), 0L},
            {Duration.ofSeconds(0, -123_456_789), -1L},
            {Duration.ofSeconds(Long.MAX_VALUE), 9223372036854775807L},
            {Duration.ofSeconds(Long.MIN_VALUE), -9223372036854775808L},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_toSeconds")
    public void test_toSeconds(Duration dur, long seconds) {
        assertEquals(seconds, dur.toSeconds());
    }

    //-----------------------------------------------------------------------
    // toDaysPart()
    //-----------------------------------------------------------------------
    Object[][] provider_toDaysPart() {
        return new Object[][] {
            {Duration.ofSeconds(365 * 86400 + 5 * 3600 + 48 * 60 + 46, 123_456_789), 365L},
            {Duration.ofSeconds(-365 * 86400 - 5 * 3600 - 48 * 60 - 46, -123_456_789), -365L},
            {Duration.ofSeconds(5 * 3600 + 48 * 60 + 46, 123_456_789), 0L},
            {Duration.ofDays(365), 365L},
            {Duration.ofHours(2), 0L},
            {Duration.ofHours(-2), 0L},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_toDaysPart")
    public void test_toDaysPart(Duration dur, long days) {
        assertEquals(days, dur.toDaysPart());
    }

    //-----------------------------------------------------------------------
    // toHoursPart()
    //-----------------------------------------------------------------------
    Object[][] provider_toHoursPart() {
        return new Object[][] {
            {Duration.ofSeconds(365 * 86400 + 5 * 3600 + 48 * 60 + 46, 123_456_789), 5},
            {Duration.ofSeconds(-365 * 86400 - 5 * 3600 - 48 * 60 - 46, -123_456_789), -5},
            {Duration.ofSeconds(48 * 60 + 46, 123_456_789), 0},
            {Duration.ofHours(2), 2},
            {Duration.ofHours(-2), -2},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_toHoursPart")
    public void test_toHoursPart(Duration dur, int hours) {
        assertEquals(hours, dur.toHoursPart());
    }

    //-----------------------------------------------------------------------
    // toMinutesPart()
    //-----------------------------------------------------------------------
    Object[][] provider_toMinutesPart() {
        return new Object[][] {
            {Duration.ofSeconds(365 * 86400 + 5 * 3600 + 48 * 60 + 46, 123_456_789), 48},
            {Duration.ofSeconds(-365 * 86400 - 5 * 3600 - 48 * 60 - 46, -123_456_789), -48},
            {Duration.ofSeconds(46, 123_456_789),0},
            {Duration.ofMinutes(48), 48},
            {Duration.ofHours(2), 0},
            {Duration.ofHours(-2),0},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_toMinutesPart")
    public void test_toMinutesPart(Duration dur, int minutes) {
        assertEquals(minutes, dur.toMinutesPart());
    }

    //-----------------------------------------------------------------------
    // toSecondsPart()
    //-----------------------------------------------------------------------
    Object[][] provider_toSecondsPart() {
        return new Object[][] {
            {Duration.ofSeconds(365 * 86400 + 5 * 3600 + 48 * 60 + 46, 123_456_789), 46},
            {Duration.ofSeconds(-365 * 86400 - 5 * 3600 - 48 * 60 - 46, -123_456_789), -47},
            {Duration.ofSeconds(0, 123_456_789), 0},
            {Duration.ofSeconds(46), 46},
            {Duration.ofHours(2), 0},
            {Duration.ofHours(-2), 0},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_toSecondsPart")
    public void test_toSecondsPart(Duration dur, int seconds) {
        assertEquals(seconds, dur.toSecondsPart());
    }

    //-----------------------------------------------------------------------
    // toMillisPart()
    //-----------------------------------------------------------------------
    Object[][] provider_toMillisPart() {
        return new Object[][] {
            {Duration.ofSeconds(365 * 86400 + 5 * 3600 + 48 * 60 + 46, 123_456_789), 123},
            {Duration.ofSeconds(-365 * 86400 - 5 * 3600 - 48 * 60 - 46, -123_456_789), 876},
            {Duration.ofSeconds(5 * 3600 + 48 * 60 + 46, 0), 0},
            {Duration.ofMillis(123), 123},
            {Duration.ofHours(2), 0},
            {Duration.ofHours(-2), 0},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_toMillisPart")
    public void test_toMillisPart(Duration dur, int millis) {
        assertEquals(millis, dur.toMillisPart());
    }

    //-----------------------------------------------------------------------
    // toNanosPart()
    //-----------------------------------------------------------------------
    Object[][] provider_toNanosPart() {
        return new Object[][] {
            {Duration.ofSeconds(365 * 86400 + 5 * 3600 + 48 * 60 + 46, 123_456_789), 123_456_789},
            {Duration.ofSeconds(-365 * 86400 - 5 * 3600 - 48 * 60 - 46, -123_456_789), 876_543_211},
            {Duration.ofSeconds(5 * 3600 + 48 * 60 + 46, 0), 0},
            {Duration.ofNanos(123_456_789), 123_456_789},
            {Duration.ofHours(2), 0},
            {Duration.ofHours(-2), 0},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_toNanosPart")
    public void test_toNanosPart(Duration dur, int nanos) {
        assertEquals(nanos, dur.toNanosPart());
    }

    //-----------------------------------------------------------------------
    // compareTo()
    //-----------------------------------------------------------------------
    @Test
    public void test_comparisons() {
        doTest_comparisons_Duration(
            Duration.ofSeconds(-2L, 0),
            Duration.ofSeconds(-2L, 999999998),
            Duration.ofSeconds(-2L, 999999999),
            Duration.ofSeconds(-1L, 0),
            Duration.ofSeconds(-1L, 1),
            Duration.ofSeconds(-1L, 999999998),
            Duration.ofSeconds(-1L, 999999999),
            Duration.ofSeconds(0L, 0),
            Duration.ofSeconds(0L, 1),
            Duration.ofSeconds(0L, 2),
            Duration.ofSeconds(0L, 999999999),
            Duration.ofSeconds(1L, 0),
            Duration.ofSeconds(2L, 0)
        );
    }

    void doTest_comparisons_Duration(Duration... durations) {
        for (int i = 0; i < durations.length; i++) {
            Duration a = durations[i];
            for (int j = 0; j < durations.length; j++) {
                Duration b = durations[j];
                if (i < j) {
                    assertEquals(true, a.compareTo(b)< 0, a + " <=> " + b);
                    assertEquals(false, a.equals(b), a + " <=> " + b);
                } else if (i > j) {
                    assertEquals(true, a.compareTo(b) > 0, a + " <=> " + b);
                    assertEquals(false, a.equals(b), a + " <=> " + b);
                } else {
                    assertEquals(0, a.compareTo(b), a + " <=> " + b);
                    assertEquals(true, a.equals(b), a + " <=> " + b);
                }
            }
        }
    }

    @Test
    public void test_compareTo_ObjectNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Duration a = Duration.ofSeconds(0L, 0);
            a.compareTo(null);
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void compareToNonDuration() {
       Assertions.assertThrows(ClassCastException.class, () -> {
           Comparable c = Duration.ofSeconds(0L);
           c.compareTo(new Object());
        });
    }

    //-----------------------------------------------------------------------
    // equals()
    //-----------------------------------------------------------------------
    @Test
    public void test_equals() {
        Duration test5a = Duration.ofSeconds(5L, 20);
        Duration test5b = Duration.ofSeconds(5L, 20);
        Duration test5n = Duration.ofSeconds(5L, 30);
        Duration test6 = Duration.ofSeconds(6L, 20);

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
        Duration test5 = Duration.ofSeconds(5L, 20);
        assertEquals(false, test5.equals(null));
    }

    @Test
    public void test_equals_otherClass() {
        Duration test5 = Duration.ofSeconds(5L, 20);
        assertEquals(false, test5.equals(""));
    }

    //-----------------------------------------------------------------------
    // hashCode()
    //-----------------------------------------------------------------------
    @Test
    public void test_hashCode() {
        Duration test5a = Duration.ofSeconds(5L, 20);
        Duration test5b = Duration.ofSeconds(5L, 20);
        Duration test5n = Duration.ofSeconds(5L, 30);
        Duration test6 = Duration.ofSeconds(6L, 20);

        assertEquals(true, test5a.hashCode() == test5a.hashCode());
        assertEquals(true, test5a.hashCode() == test5b.hashCode());
        assertEquals(true, test5b.hashCode() == test5b.hashCode());

        assertEquals(false, test5a.hashCode() == test5n.hashCode());
        assertEquals(false, test5a.hashCode() == test6.hashCode());
    }

    //-----------------------------------------------------------------------
    Object[][] provider_withNanos_int() {
        return new Object[][] {
            {0, 0, 0,           0, 0},
            {0, 0, 1,           0, 1},
            {0, 0, 999999999,   0, 999999999},

            {1, 0, 0,           1, 0},
            {1, 0, 1,           1, 1},
            {1, 0, 999999999,   1, 999999999},

            {-1, 0, 0,           -1, 0},
            {-1, 0, 1,           -1, 1},
            {-1, 0, 999999999,   -1, 999999999},

            {1, 999999999, 0,           1, 0},
            {1, 999999999, 1,           1, 1},
            {1, 999999998, 2,           1, 2},

            {Long.MAX_VALUE, 0, 999999999, Long.MAX_VALUE, 999999999},
            {Long.MIN_VALUE, 0, 999999999, Long.MIN_VALUE, 999999999},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_withNanos_int")
    public void withNanos_long(long seconds, int nanos, int amount, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.ofSeconds(seconds, nanos);
        t = t.withNanos(amount);
        assertEquals(expectedSeconds, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    //-----------------------------------------------------------------------
    Object[][] provider_withSeconds_long() {
        return new Object[][] {
            {0, 0, 0, 0, 0},
            {0, 0, 1, 1, 0},
            {0, 0, -1, -1, 0},
            {0, 0, Long.MAX_VALUE, Long.MAX_VALUE, 0},
            {0, 0, Long.MIN_VALUE, Long.MIN_VALUE, 0},

            {1, 0, 0, 0, 0},
            {1, 0, 2, 2, 0},
            {1, 0, -1, -1, 0},
            {1, 0, Long.MAX_VALUE, Long.MAX_VALUE, 0},
            {1, 0, Long.MIN_VALUE, Long.MIN_VALUE, 0},

            {-1, 1, 0, 0, 1},
            {-1, 1, 1, 1, 1},
            {-1, 1, -1, -1, 1},
            {-1, 1, Long.MAX_VALUE, Long.MAX_VALUE, 1},
            {-1, 1, Long.MIN_VALUE, Long.MIN_VALUE, 1},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_withSeconds_long")
    public void withSeconds_long(long seconds, int nanos, long amount, long expectedSeconds, int expectedNanoOfSecond) {
        Duration t = Duration.ofSeconds(seconds, nanos);
        t = t.withSeconds(amount);
        assertEquals(expectedSeconds, t.getSeconds());
        assertEquals(expectedNanoOfSecond, t.getNano());
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    Object[][] provider_toString() {
        return new Object[][] {
            {0, 0, "PT0S"},
            {0, 1, "PT0.000000001S"},
            {0, 10, "PT0.00000001S"},
            {0, 100, "PT0.0000001S"},
            {0, 1000, "PT0.000001S"},
            {0, 10000, "PT0.00001S"},
            {0, 100000, "PT0.0001S"},
            {0, 1000000, "PT0.001S"},
            {0, 10000000, "PT0.01S"},
            {0, 100000000, "PT0.1S"},
            {0, 120000000, "PT0.12S"},
            {0, 123000000, "PT0.123S"},
            {0, 123400000, "PT0.1234S"},
            {0, 123450000, "PT0.12345S"},
            {0, 123456000, "PT0.123456S"},
            {0, 123456700, "PT0.1234567S"},
            {0, 123456780, "PT0.12345678S"},
            {0, 123456789, "PT0.123456789S"},
            {1, 0, "PT1S"},
            {59, 0, "PT59S"},
            {60, 0, "PT1M"},
            {61, 0, "PT1M1S"},
            {3599, 0, "PT59M59S"},
            {3600, 0, "PT1H"},
            {3601, 0, "PT1H1S"},
            {3661, 0, "PT1H1M1S"},
            {86399, 0, "PT23H59M59S"},
            {86400, 0, "PT24H"},
            {59, 0, "PT59S"},
            {59, 0, "PT59S"},
            {-1, 0, "PT-1S"},
            {-1, 1000, "PT-0.999999S"},
            {-1, 900000000, "PT-0.1S"},
            {-60, 100_000_000, "PT-59.9S"},
            {-59, -900_000_000, "PT-59.9S"},
            {-60, -100_000_000, "PT-1M-0.1S"},
            {Long.MAX_VALUE, 0, "PT" + (Long.MAX_VALUE / 3600) + "H" +
                    ((Long.MAX_VALUE % 3600) / 60) + "M" + (Long.MAX_VALUE % 60) + "S"},
            {Long.MIN_VALUE, 0, "PT" + (Long.MIN_VALUE / 3600) + "H" +
                    ((Long.MIN_VALUE % 3600) / 60) + "M" + (Long.MIN_VALUE % 60) + "S"},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_toString")
    public void test_toString(long seconds, int nanos, String expected) {
        Duration t = Duration.ofSeconds(seconds, nanos);
        assertEquals(expected, t.toString());
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_duration_getUnits() {
        Duration duration = Duration.ofSeconds(5000, 1000);
        List<TemporalUnit> units = duration.getUnits();
        assertEquals(2, units.size(), "Period.getUnits length");
        assertTrue(units.contains(ChronoUnit.SECONDS), "Period.getUnits contains ChronoUnit.SECONDS");
        assertTrue(units.contains(ChronoUnit.NANOS), "contains ChronoUnit.NANOS");
    }

    @Test()
    public void test_getUnit() {
        Duration test = Duration.ofSeconds(2000, 1000);
        long seconds = test.get(ChronoUnit.SECONDS);
        assertEquals(2000, seconds, "duration.get(SECONDS)");
        long nanos = test.get(ChronoUnit.NANOS);
        assertEquals(1000, nanos, "duration.get(NANOS)");
    }

    Object[][] provider_factory_of_badTemporalUnit() {
        return new Object[][] {
            {0, MICROS},
            {0, MILLIS},
            {0, MINUTES},
            {0, HOURS},
            {0, HALF_DAYS},
            {0, DAYS},
            {0, ChronoUnit.MONTHS},
            {0, ChronoUnit.YEARS},
            {0, ChronoUnit.DECADES},
            {0, ChronoUnit.CENTURIES},
            {0, ChronoUnit.MILLENNIA},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_factory_of_badTemporalUnit")
    public void test_bad_getUnit(long amount, TemporalUnit unit) {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Duration t = Duration.of(amount, unit);
            t.get(unit);
        });
    }
}
