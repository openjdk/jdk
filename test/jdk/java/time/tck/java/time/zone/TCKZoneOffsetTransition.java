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
 * Copyright (c) 2010-2012, Stephen Colebourne & Michael Nascimento Santos
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
package tck.java.time.zone;

import static java.time.temporal.ChronoUnit.HOURS;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import tck.java.time.AbstractTCKTest;

/**
 * Test ZoneOffsetTransition.
 */
public class TCKZoneOffsetTransition extends AbstractTCKTest {

    private static final ZoneOffset OFFSET_0100 = ZoneOffset.ofHours(1);
    private static final ZoneOffset OFFSET_0200 = ZoneOffset.ofHours(2);
    private static final ZoneOffset OFFSET_0230 = ZoneOffset.ofHoursMinutes(2, 30);
    private static final ZoneOffset OFFSET_0300 = ZoneOffset.ofHours(3);
    private static final ZoneOffset OFFSET_0400 = ZoneOffset.ofHours(4);

    //-----------------------------------------------------------------------
    // factory
    //-----------------------------------------------------------------------
    @Test
    public void test_factory_nullTransition() {
        Assertions.assertThrows(NullPointerException.class, () -> ZoneOffsetTransition.of(null, OFFSET_0100, OFFSET_0200));
    }

    @Test
    public void test_factory_nullOffsetBefore() {
        Assertions.assertThrows(NullPointerException.class, () -> ZoneOffsetTransition.of(LocalDateTime.of(2010, 12, 3, 11, 30), null, OFFSET_0200));
    }

    @Test
    public void test_factory_nullOffsetAfter() {
        Assertions.assertThrows(NullPointerException.class, () -> ZoneOffsetTransition.of(LocalDateTime.of(2010, 12, 3, 11, 30), OFFSET_0200, null));
    }

    @Test
    public void test_factory_sameOffset() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> ZoneOffsetTransition.of(LocalDateTime.of(2010, 12, 3, 11, 30), OFFSET_0200, OFFSET_0200));
    }

    @Test
    public void test_factory_noNanos() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> ZoneOffsetTransition.of(LocalDateTime.of(2010, 12, 3, 11, 30, 0, 500), OFFSET_0200, OFFSET_0300));
    }

    //-----------------------------------------------------------------------
    // getters
    //-----------------------------------------------------------------------
    @Test
    public void test_getters_gap() throws Exception {
        LocalDateTime before = LocalDateTime.of(2010, 3, 31, 1, 0);
        LocalDateTime after = LocalDateTime.of(2010, 3, 31, 2, 0);
        ZoneOffsetTransition test = ZoneOffsetTransition.of(before, OFFSET_0200, OFFSET_0300);
        assertEquals(true, test.isGap());
        assertEquals(false, test.isOverlap());
        assertEquals(before, test.getDateTimeBefore());
        assertEquals(after, test.getDateTimeAfter());
        assertEquals(before.toInstant(OFFSET_0200), test.getInstant());
        assertEquals(OFFSET_0200, test.getOffsetBefore());
        assertEquals(OFFSET_0300, test.getOffsetAfter());
        assertEquals(Duration.of(1, HOURS), test.getDuration());
    }

    @Test
    public void test_getters_overlap() throws Exception {
        LocalDateTime before = LocalDateTime.of(2010, 10, 31, 1, 0);
        LocalDateTime after = LocalDateTime.of(2010, 10, 31, 0, 0);
        ZoneOffsetTransition test = ZoneOffsetTransition.of(before, OFFSET_0300, OFFSET_0200);
        assertEquals(false, test.isGap());
        assertEquals(true, test.isOverlap());
        assertEquals(before, test.getDateTimeBefore());
        assertEquals(after, test.getDateTimeAfter());
        assertEquals(before.toInstant(OFFSET_0300), test.getInstant());
        assertEquals(OFFSET_0300, test.getOffsetBefore());
        assertEquals(OFFSET_0200, test.getOffsetAfter());
        assertEquals(Duration.of(-1, HOURS), test.getDuration());
    }


    //-----------------------------------------------------------------------
    // isValidOffset()
    //-----------------------------------------------------------------------
    @Test
    public void test_isValidOffset_gap() {
        LocalDateTime ldt = LocalDateTime.of(2010, 3, 31, 1, 0);
        ZoneOffsetTransition test = ZoneOffsetTransition.of(ldt, OFFSET_0200, OFFSET_0300);
        assertEquals(false, test.isValidOffset(OFFSET_0100));
        assertEquals(false, test.isValidOffset(OFFSET_0200));
        assertEquals(false, test.isValidOffset(OFFSET_0230));
        assertEquals(false, test.isValidOffset(OFFSET_0300));
        assertEquals(false, test.isValidOffset(OFFSET_0400));
    }

    @Test
    public void test_isValidOffset_overlap() {
        LocalDateTime ldt = LocalDateTime.of(2010, 10, 31, 1, 0);
        ZoneOffsetTransition test = ZoneOffsetTransition.of(ldt, OFFSET_0300, OFFSET_0200);
        assertEquals(false, test.isValidOffset(OFFSET_0100));
        assertEquals(true, test.isValidOffset(OFFSET_0200));
        assertEquals(false, test.isValidOffset(OFFSET_0230));
        assertEquals(true, test.isValidOffset(OFFSET_0300));
        assertEquals(false, test.isValidOffset(OFFSET_0400));
    }

    //-----------------------------------------------------------------------
    // compareTo()
    //-----------------------------------------------------------------------
    @Test
    public void test_compareTo() {
        ZoneOffsetTransition a = ZoneOffsetTransition.of(
            LocalDateTime.ofEpochSecond(23875287L - 1, 0, OFFSET_0200), OFFSET_0200, OFFSET_0300);
        ZoneOffsetTransition b = ZoneOffsetTransition.of(
            LocalDateTime.ofEpochSecond(23875287L, 0, OFFSET_0300), OFFSET_0300, OFFSET_0200);
        ZoneOffsetTransition c = ZoneOffsetTransition.of(
            LocalDateTime.ofEpochSecond(23875287L + 1, 0, OFFSET_0100), OFFSET_0100,OFFSET_0400);

        assertEquals(true, a.compareTo(a) == 0);
        assertEquals(true, a.compareTo(b) < 0);
        assertEquals(true, a.compareTo(c) < 0);

        assertEquals(true, b.compareTo(a) > 0);
        assertEquals(true, b.compareTo(b) == 0);
        assertEquals(true, b.compareTo(c) < 0);

        assertEquals(true, c.compareTo(a) > 0);
        assertEquals(true, c.compareTo(b) > 0);
        assertEquals(true, c.compareTo(c) == 0);
    }

    @Test
    public void test_compareTo_sameInstant() {
        ZoneOffsetTransition a = ZoneOffsetTransition.of(
            LocalDateTime.ofEpochSecond(23875287L, 0, OFFSET_0200), OFFSET_0200, OFFSET_0300);
        ZoneOffsetTransition b = ZoneOffsetTransition.of(
            LocalDateTime.ofEpochSecond(23875287L, 0, OFFSET_0300), OFFSET_0300, OFFSET_0200);
        ZoneOffsetTransition c = ZoneOffsetTransition.of(
            LocalDateTime.ofEpochSecond(23875287L, 0, OFFSET_0100), OFFSET_0100, OFFSET_0400);

        assertEquals(true, a.compareTo(a) == 0);
        assertEquals(true, a.compareTo(b) == 0);
        assertEquals(true, a.compareTo(c) == 0);

        assertEquals(true, b.compareTo(a) == 0);
        assertEquals(true, b.compareTo(b) == 0);
        assertEquals(true, b.compareTo(c) == 0);

        assertEquals(true, c.compareTo(a) == 0);
        assertEquals(true, c.compareTo(b) == 0);
        assertEquals(true, c.compareTo(c) == 0);
    }

    //-----------------------------------------------------------------------
    // equals()
    //-----------------------------------------------------------------------
    @Test
    public void test_equals() {
        LocalDateTime ldtA = LocalDateTime.of(2010, 3, 31, 1, 0);
        ZoneOffsetTransition a1 = ZoneOffsetTransition.of(ldtA, OFFSET_0200, OFFSET_0300);
        ZoneOffsetTransition a2 = ZoneOffsetTransition.of(ldtA, OFFSET_0200, OFFSET_0300);
        LocalDateTime ldtB = LocalDateTime.of(2010, 10, 31, 1, 0);
        ZoneOffsetTransition b = ZoneOffsetTransition.of(ldtB, OFFSET_0300, OFFSET_0200);

        assertEquals(true, a1.equals(a1));
        assertEquals(true, a1.equals(a2));
        assertEquals(false, a1.equals(b));
        assertEquals(true, a2.equals(a1));
        assertEquals(true, a2.equals(a2));
        assertEquals(false, a2.equals(b));
        assertEquals(false, b.equals(a1));
        assertEquals(false, b.equals(a2));
        assertEquals(true, b.equals(b));

        assertEquals(false, a1.equals(""));
        assertEquals(false, a1.equals(null));
    }

    //-----------------------------------------------------------------------
    // hashCode()
    //-----------------------------------------------------------------------
    @Test
    public void test_hashCode_floatingWeek_gap_notEndOfDay() {
        LocalDateTime ldtA = LocalDateTime.of(2010, 3, 31, 1, 0);
        ZoneOffsetTransition a1 = ZoneOffsetTransition.of(ldtA, OFFSET_0200, OFFSET_0300);
        ZoneOffsetTransition a2 = ZoneOffsetTransition.of(ldtA, OFFSET_0200, OFFSET_0300);
        LocalDateTime ldtB = LocalDateTime.of(2010, 10, 31, 1, 0);
        ZoneOffsetTransition b = ZoneOffsetTransition.of(ldtB, OFFSET_0300, OFFSET_0200);

        assertEquals(a1.hashCode(), a1.hashCode());
        assertEquals(a2.hashCode(), a1.hashCode());
        assertEquals(b.hashCode(), b.hashCode());
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @Test
    public void test_toString_gap() {
        LocalDateTime ldt = LocalDateTime.of(2010, 3, 31, 1, 0);
        ZoneOffsetTransition test = ZoneOffsetTransition.of(ldt, OFFSET_0200, OFFSET_0300);
        assertEquals("Transition[Gap at 2010-03-31T01:00+02:00 to +03:00]", test.toString());
    }

    @Test
    public void test_toString_overlap() {
        LocalDateTime ldt = LocalDateTime.of(2010, 10, 31, 1, 0);
        ZoneOffsetTransition test = ZoneOffsetTransition.of(ldt, OFFSET_0300, OFFSET_0200);
        assertEquals("Transition[Overlap at 2010-10-31T01:00+03:00 to +02:00]", test.toString());
    }

}
