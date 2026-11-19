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
 * Copyright (c) 2008-2012 Stephen Colebourne & Michael Nascimento Santos
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test tick clock.
 */
public class TCKClock_Tick extends AbstractTCKTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final ZonedDateTime ZDT = LocalDateTime.of(2008, 6, 30, 11, 30, 10, 500).atZone(ZoneOffset.ofHours(2));

    //-----------------------------------------------------------------------
    @Test
    public void test_tick_ClockDuration_250millis() {
        for (int i = 0; i < 1000; i++) {
            Clock test = Clock.tick(Clock.fixed(ZDT.withNano(i * 1000_000).toInstant(), PARIS), Duration.ofMillis(250));
            assertEquals(ZDT.withNano((i / 250) * 250_000_000).toInstant(), test.instant());
            assertEquals(PARIS, test.getZone());
        }
    }

    @Test
    public void test_tick_ClockDuration_250micros() {
        for (int i = 0; i < 1000; i++) {
            Clock test = Clock.tick(Clock.fixed(ZDT.withNano(i * 1000).toInstant(), PARIS), Duration.ofNanos(250_000));
            assertEquals(ZDT.withNano((i / 250) * 250_000).toInstant(), test.instant());
            assertEquals(PARIS, test.getZone());
        }
    }

    @Test
    public void test_tick_ClockDuration_20nanos() {
        for (int i = 0; i < 1000; i++) {
            Clock test = Clock.tick(Clock.fixed(ZDT.withNano(i).toInstant(), PARIS), Duration.ofNanos(20));
            assertEquals(ZDT.withNano((i / 20) * 20).toInstant(), test.instant());
            assertEquals(PARIS, test.getZone());
        }
    }

    @Test
    public void test_tick_ClockDuration_zeroDuration() {
        Clock underlying = Clock.system(PARIS);
        Clock test = Clock.tick(underlying, Duration.ZERO);
        assertSame(test, underlying);  // spec says same
    }

    @Test
    public void test_tick_ClockDuration_1nsDuration() {
        Clock underlying = Clock.system(PARIS);
        Clock test = Clock.tick(underlying, Duration.ofNanos(1));
        assertSame(test, underlying);  // spec says same
    }

    @Test
    public void test_tick_ClockDuration_maxDuration() {
        Assertions.assertThrows(ArithmeticException.class, () -> Clock.tick(Clock.systemUTC(), Duration.ofSeconds(Long.MAX_VALUE)));
    }

    @Test
    public void test_tick_ClockDuration_subMilliNotDivisible_123ns() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Clock.tick(Clock.systemUTC(), Duration.ofSeconds(0, 123)));
    }

    @Test
    public void test_tick_ClockDuration_subMilliNotDivisible_999ns() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Clock.tick(Clock.systemUTC(), Duration.ofSeconds(0, 999)));
    }

    @Test
    public void test_tick_ClockDuration_subMilliNotDivisible_999_999_999ns() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Clock.tick(Clock.systemUTC(), Duration.ofSeconds(0, 999_999_999)));
    }

    @Test
    public void test_tick_ClockDuration_negative1ns() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Clock.tick(Clock.systemUTC(), Duration.ofSeconds(0, -1)));
    }

    @Test
    public void test_tick_ClockDuration_negative1s() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> Clock.tick(Clock.systemUTC(), Duration.ofSeconds(-1)));
    }

    @Test
    public void test_tick_ClockDuration_nullClock() {
        Assertions.assertThrows(NullPointerException.class, () -> Clock.tick(null, Duration.ZERO));
    }

    @Test
    public void test_tick_ClockDuration_nullDuration() {
        Assertions.assertThrows(NullPointerException.class, () -> Clock.tick(Clock.systemUTC(), null));
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_tickMillis_ZoneId() throws Exception {
        Clock test = Clock.tickMillis(PARIS);
        assertEquals(PARIS, test.getZone());
        assertEquals(0, test.instant().getNano() % 1000_000);
    }

    @Test
    public void test_tickMillis_ZoneId_nullZoneId() {
        Assertions.assertThrows(NullPointerException.class, () -> Clock.tickMillis(null));
    }
    //-----------------------------------------------------------------------
    @Test
    public void test_tickSeconds_ZoneId() throws Exception {
        Clock test = Clock.tickSeconds(PARIS);
        assertEquals(PARIS, test.getZone());
        assertEquals(0, test.instant().getNano());
        Thread.sleep(100);
        assertEquals(0, test.instant().getNano());
    }

    @Test
    public void test_tickSeconds_ZoneId_nullZoneId() {
        Assertions.assertThrows(NullPointerException.class, () -> Clock.tickSeconds(null));
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_tickMinutes_ZoneId() {
        Clock test = Clock.tickMinutes(PARIS);
        assertEquals(PARIS, test.getZone());
        Instant instant = test.instant();
        assertEquals(0, instant.getEpochSecond() % 60);
        assertEquals(0, instant.getNano());
    }

    @Test
    public void test_tickMinutes_ZoneId_nullZoneId() {
        Assertions.assertThrows(NullPointerException.class, () -> Clock.tickMinutes(null));
    }

    //-------------------------------------------------------------------------
    @Test
    public void test_withZone() {
        Clock test = Clock.tick(Clock.system(PARIS), Duration.ofMillis(500));
        Clock changed = test.withZone(MOSCOW);
        assertEquals(PARIS, test.getZone());
        assertEquals(MOSCOW, changed.getZone());
    }

    @Test
    public void test_withZone_equal() {
        Clock test = Clock.tick(Clock.system(PARIS), Duration.ofMillis(500));
        Clock changed = test.withZone(PARIS);
        assertEquals(changed, test);
    }

    @Test
    public void test_withZone_null() {
        Assertions.assertThrows(NullPointerException.class, () -> Clock.tick(Clock.system(PARIS), Duration.ofMillis(500)).withZone(null));
    }

    //-----------------------------------------------------------------------
    @Test
    public void test__equals() {
        Clock a = Clock.tick(Clock.system(PARIS), Duration.ofMillis(500));
        Clock b = Clock.tick(Clock.system(PARIS), Duration.ofMillis(500));
        assertEquals(true, a.equals(a));
        assertEquals(true, a.equals(b));
        assertEquals(true, b.equals(a));
        assertEquals(true, b.equals(b));

        Clock c = Clock.tick(Clock.system(MOSCOW), Duration.ofMillis(500));
        assertEquals(false, a.equals(c));

        Clock d = Clock.tick(Clock.system(PARIS), Duration.ofMillis(499));
        assertEquals(false, a.equals(d));

        assertEquals(false, a.equals(null));
        assertEquals(false, a.equals("other type"));
        assertEquals(false, a.equals(Clock.systemUTC()));
    }

    @Test
    public void test_hashCode() {
        Clock a = Clock.tick(Clock.system(PARIS), Duration.ofMillis(500));
        Clock b = Clock.tick(Clock.system(PARIS), Duration.ofMillis(500));
        assertEquals(a.hashCode(), a.hashCode());
        assertEquals(b.hashCode(), a.hashCode());

        Clock c = Clock.tick(Clock.system(MOSCOW), Duration.ofMillis(500));
        assertEquals(false, a.hashCode() == c.hashCode());

        Clock d = Clock.tick(Clock.system(PARIS), Duration.ofMillis(499));
        assertEquals(false, a.hashCode() == d.hashCode());
    }
}
