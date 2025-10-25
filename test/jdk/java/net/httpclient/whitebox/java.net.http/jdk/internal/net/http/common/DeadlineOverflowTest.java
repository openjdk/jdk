/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static java.time.temporal.ChronoUnit.NANOS;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DeadlineOverflowTest {

    @Test
    void test_DeadlineOf_InstantMin() {
        assertEquals(Instant.MIN, Deadline.of(Instant.MIN).asInstant());
    }

    @Test
    void test_DeadlineOf_InstantMax() {
        assertEquals(Instant.MAX, Deadline.of(Instant.MAX).asInstant());
    }

    @Test
    void test_plusNanos_min() {
        assertEquals(Deadline.MIN, Deadline.MIN.plusNanos(-1));
    }

    @Test
    void test_plusNanos_max() {
        assertEquals(Deadline.MAX, Deadline.MAX.plusNanos(1));
    }

    @Test
    void test_minus_min() {
        assertEquals(Deadline.MIN, Deadline.MIN.minus(Duration.ofNanos(1)));
    }

    @Test
    void test_minus_max() {
        assertEquals(Deadline.MAX, Deadline.MAX.minus(Duration.ofNanos(-1)));
    }

    @Test
    void test_plusAmount_min() {
        assertEquals(Deadline.MIN, Deadline.MIN.plus(-1, ChronoUnit.NANOS));
    }

    @Test
    void test_plusAmount_max() {
        assertEquals(Deadline.MAX, Deadline.MAX.plus(1, ChronoUnit.NANOS));
    }

    @Test
    void test_plusSeconds_min() {
        assertEquals(Deadline.MIN, Deadline.MIN.plusSeconds(-1));
    }

    @Test
    void test_plusSeconds_max() {
        assertEquals(Deadline.MAX, Deadline.MAX.plusSeconds(1));
    }

    @Test
    void test_plusMillis_min() {
        assertEquals(Deadline.MIN, Deadline.MIN.plusMillis(-1));
    }

    @Test
    void test_plusMillis_max() {
        assertEquals(Deadline.MAX, Deadline.MAX.plusMillis(1));
    }

    @Test
    void test_plusDuration_min() {
        assertEquals(Deadline.MIN, Deadline.MIN.plus(Duration.ofNanos(-1)));
    }

    @Test
    void test_plusDuration_max() {
        assertEquals(Deadline.MAX, Deadline.MAX.plus(Duration.ofNanos(1)));
    }

    @Test
    void test_until_min() {
        assertEquals(Long.MIN_VALUE, Deadline.MAX.until(Deadline.MIN, NANOS));
    }

    @Test
    void test_until_max() {
        assertEquals(Long.MAX_VALUE, Deadline.MIN.until(Deadline.MAX, NANOS));
    }

    @Test
    void test_between_min() {
        Duration delta = Duration.between(Instant.MAX, Instant.MIN);
        assertEquals(delta, Deadline.between(Deadline.MAX, Deadline.MIN));
    }

    @Test
    void test_between_max() {
        Duration delta = Duration.between(Instant.MIN, Instant.MAX);
        assertEquals(delta, Deadline.between(Deadline.MIN, Deadline.MAX));
    }

}
