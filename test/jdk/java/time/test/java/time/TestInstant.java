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
package test.java.time;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test Instant.
 * @bug 8273369 8331202 8364752
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestInstant extends AbstractTest {

    @Test
    public void test_immutable() {
        assertImmutable(Instant.class);
    }

    private Object[][] provider_sampleEpochMillis() {
        return new Object[][] {
            {"Long.MAX_VALUE", Long.MAX_VALUE},
            {"Long.MAX_VALUE-1", Long.MAX_VALUE - 1},
            {"1", 1L},
            {"0", 0L},
            {"-1", -1L},
            {"Long.MIN_VALUE+1", Long.MIN_VALUE + 1},
            {"Long.MIN_VALUE", Long.MIN_VALUE}
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sampleEpochMillis")
    public void test_epochMillis(String name, long millis) {
        Instant t1 = Instant.ofEpochMilli(millis);
        long m = t1.toEpochMilli();
        assertEquals(m, millis, name);
    }

    /**
     * Checks whether Instant.until() returning microseconds does not throw
     * an ArithmeticException for Instants apart for more than Long.MAX_VALUE
     * nanoseconds.
     */
    @Test
    public void test_microsUntil() {
        var nanoMax = Instant.EPOCH.plusNanos(Long.MAX_VALUE);
        var totalMicros = Instant.EPOCH.until(nanoMax, ChronoUnit.MICROS);
        var plusOneMicro = Instant.EPOCH.until(nanoMax.plusNanos(1000), ChronoUnit.MICROS);
        assertEquals(1L, plusOneMicro - totalMicros);
    }

    /**
     * Checks whether Instant.until() returning milliseconds does not throw
     * an ArithmeticException for very large/small Instants
     */
    @Test
    public void test_millisUntil() {
        assertEquals(1000L, Instant.MIN.until(Instant.MIN.plusSeconds(1), ChronoUnit.MILLIS));
        assertEquals(1000L, Instant.MAX.plusSeconds(-1).until(Instant.MAX, ChronoUnit.MILLIS));
    }

    private Object[][] provider_until_1arg() {
        Instant t1 = Instant.ofEpochSecond(0, 10);
        Instant t2 = Instant.ofEpochSecond(10, -20);
        return new Object[][] {
            {t1, t2},
            {t2, t1},
            {Instant.MIN, Instant.MAX},
            {Instant.MAX, Instant.MIN},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_until_1arg")
    public void test_until_1arg(Instant start, Instant end) {
        Duration result = start.until(end);
        Duration expected = Duration.ofSeconds(end.getEpochSecond() - start.getEpochSecond(),
                end.getNano() - start.getNano());
        assertEquals(expected, result);
        expected = Duration.between(start, end);
        assertEquals(expected, result);
    }

    @Test
    public void test_until_1arg_NPE() {
        assertThrows(NullPointerException.class, () -> Instant.now().until(null));
    }

    private Object[][] valid_instants() {
        var I1 = OffsetDateTime.of(2017, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+02")).toInstant();
        var I2 = OffsetDateTime.of(2017, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+02:02")).toInstant();
        var I3 = OffsetDateTime.of(2017, 1, 1, 0, 0, 0, 0, ZoneOffset.of("+02:02:02")).toInstant();
        var I4 = OffsetDateTime.of(2017, 1, 1, 0, 0, 0, 0, ZoneOffset.of("Z")).toInstant();
        return new Object[][] {
            {"2017-01-01T00:00:00.000+02", I1},
            {"2017-01-01T00:00:00.000+0200", I1},
            {"2017-01-01T00:00:00.000+02:00", I1},
            {"2017-01-01T00:00:00.000+020000", I1},
            {"2017-01-01T00:00:00.000+02:00:00", I1},

            {"2017-01-01T00:00:00.000+0202", I2},
            {"2017-01-01T00:00:00.000+02:02", I2},

            {"2017-01-01T00:00:00.000+020202", I3},
            {"2017-01-01T00:00:00.000+02:02:02", I3},

            {"2017-01-01T00:00:00.000Z", I4},
        };
    }

    @ParameterizedTest
    @MethodSource("valid_instants")
    public void test_parse_valid(String instant, Instant expected) {
        assertEquals(expected, Instant.parse(instant));
    }

    private Object[][] invalid_instants() {
        return new Object[][] {
            {"2017-01-01T00:00:00.000"},
            {"2017-01-01T00:00:00.000+0"},
            {"2017-01-01T00:00:00.000+0:"},
            {"2017-01-01T00:00:00.000+02:"},
            {"2017-01-01T00:00:00.000+020"},
            {"2017-01-01T00:00:00.000+02:0"},
            {"2017-01-01T00:00:00.000+02:0:"},
            {"2017-01-01T00:00:00.000+02:00:"},
            {"2017-01-01T00:00:00.000+02:000"},
            {"2017-01-01T00:00:00.000+02:00:0"},
            {"2017-01-01T00:00:00.000+02:00:0:"},
            {"2017-01-01T00:00:00.000+0200000"},
            {"2017-01-01T00:00:00.000+02:00:000"},
            {"2017-01-01T00:00:00.000+02:00:00:"},
            {"2017-01-01T00:00:00.000UTC"},
        };
    }

    @ParameterizedTest
    @MethodSource("invalid_instants")
    public void test_parse_invalid(String instant) {
        assertThrows(DateTimeParseException.class, () -> Instant.parse(instant));
    }
}
