/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package test.java.time;

import static java.time.temporal.ChronoUnit.SECONDS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/**
 * Test instant source.
 */
public class TestInstantSource {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Test
    public void test_system() {
        // main tests for Clock.currentInstant() are in TestClock_System
        var test = InstantSource.system();
        assertSame(test.withZone(ZoneOffset.UTC), Clock.systemUTC());
        assertEquals(Clock.system(PARIS), test.withZone(PARIS));
        var millis = System.currentTimeMillis();
        var testMillis = test.millis();
        var testInstantMillis = test.instant().toEpochMilli();
        assertTrue(Math.abs(testMillis - millis) < 1000);
        assertTrue(Math.abs(testInstantMillis - millis) < 1000);
        assertSame(test, InstantSource.system());
        assertEquals(InstantSource.system().hashCode(), test.hashCode());
        assertEquals("SystemInstantSource", test.toString());
    }

    @Test
    public void test_tick() {
        var millis = 257265861691L;
        var instant = Instant.ofEpochMilli(millis);
        var duration = Duration.ofSeconds(1);
        var test = InstantSource.tick(InstantSource.fixed(instant), duration);
        assertEquals(Clock.tick(Clock.fixed(instant, ZoneOffset.UTC), duration), test.withZone(ZoneOffset.UTC));
        assertEquals(Clock.tick(Clock.fixed(instant, PARIS), duration), test.withZone(PARIS));
        assertEquals((millis / 1000) * 1000, test.millis());
        assertEquals(instant.truncatedTo(SECONDS), test.instant());
        assertEquals(InstantSource.tick(InstantSource.fixed(instant), duration), test);
        assertEquals(InstantSource.tick(InstantSource.fixed(instant), duration).hashCode(), test.hashCode());
    }

    @Test
    public void test_fixed() {
        var millis = 257265861691L;
        var instant = Instant.ofEpochMilli(millis);
        var test = InstantSource.fixed(instant);
        assertEquals(Clock.fixed(instant, ZoneOffset.UTC), test.withZone(ZoneOffset.UTC));
        assertEquals(Clock.fixed(instant, PARIS), test.withZone(PARIS));
        assertEquals(millis, test.millis());
        assertEquals(instant, test.instant());
        assertEquals(InstantSource.fixed(instant), test);
        assertEquals(InstantSource.fixed(instant).hashCode(), test.hashCode());
    }

    @Test
    public void test_offset() {
        var millis = 257265861691L;
        var instant = Instant.ofEpochMilli(millis);
        var duration = Duration.ofSeconds(120);
        var test = InstantSource.offset(InstantSource.fixed(instant), duration);
        assertEquals(Clock.offset(Clock.fixed(instant, ZoneOffset.UTC), duration), test.withZone(ZoneOffset.UTC));
        assertEquals(Clock.offset(Clock.fixed(instant, PARIS), duration), test.withZone(PARIS));
        assertEquals(millis + 120_000, test.millis());
        assertEquals(instant.plusSeconds(120), test.instant());
        assertEquals(InstantSource.offset(InstantSource.fixed(instant), duration), test);
        assertEquals(InstantSource.offset(InstantSource.fixed(instant), duration).hashCode(), test.hashCode());
    }

    static class MockInstantSource implements InstantSource {
        static final Instant FIXED = Instant.now();

        @Override
        public Instant instant() {
            return FIXED;
        }
    }

    @Test
    public void test_mock() {
        var test = new MockInstantSource();
        assertEquals(ZoneOffset.UTC, test.withZone(ZoneOffset.UTC).getZone());
        assertEquals(PARIS, test.withZone(PARIS).getZone());
        assertEquals(PARIS, test.withZone(ZoneOffset.UTC).withZone(PARIS).getZone());
        assertEquals(MockInstantSource.FIXED.toEpochMilli(), test.millis());
        assertEquals(MockInstantSource.FIXED, test.instant());
        assertEquals(test.withZone(ZoneOffset.UTC), test.withZone(ZoneOffset.UTC));
        assertEquals(test.withZone(ZoneOffset.UTC).hashCode(), test.withZone(ZoneOffset.UTC).hashCode());
    }

}
