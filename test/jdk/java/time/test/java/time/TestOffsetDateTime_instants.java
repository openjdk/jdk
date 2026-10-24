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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test OffsetDateTime creation.
 */
public class TestOffsetDateTime_instants {

    private static final ZoneOffset OFFSET_PONE = ZoneOffset.ofHours(1);
    private static final ZoneOffset OFFSET_MAX = ZoneOffset.ofHours(18);
    private static final ZoneOffset OFFSET_MIN = ZoneOffset.ofHours(-18);

    //-----------------------------------------------------------------------
    @Test
    public void factory_ofInstant_nullInstant() {
        Assertions.assertThrows(NullPointerException.class, () -> OffsetDateTime.ofInstant((Instant) null, OFFSET_PONE));
    }

    @Test
    public void factory_ofInstant_nullOffset() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            Instant instant = Instant.ofEpochSecond(0L);
            OffsetDateTime.ofInstant(instant, (ZoneOffset) null);
        });
    }

    @Test
    public void factory_ofInstant_allSecsInDay() {
        for (int i = 0; i < (24 * 60 * 60); i++) {
            Instant instant = Instant.ofEpochSecond(i);
            OffsetDateTime test = OffsetDateTime.ofInstant(instant, OFFSET_PONE);
            assertEquals(1970, test.getYear());
            assertEquals(Month.JANUARY, test.getMonth());
            assertEquals(1 + (i >= 23 * 60 * 60 ? 1 : 0), test.getDayOfMonth());
            assertEquals(((i / (60 * 60)) + 1) % 24, test.getHour());
            assertEquals((i / 60) % 60, test.getMinute());
            assertEquals(i % 60, test.getSecond());
        }
    }

    @Test
    public void factory_ofInstant_allDaysInCycle() {
        // sanity check using different algorithm
        OffsetDateTime expected = OffsetDateTime.of(LocalDate.of(1970, 1, 1), LocalTime.of(0, 0, 0, 0), ZoneOffset.UTC);
        for (long i = 0; i < 146097; i++) {
            Instant instant = Instant.ofEpochSecond(i * 24L * 60L * 60L);
            OffsetDateTime test = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
            assertEquals(expected, test);
            expected = expected.plusDays(1);
        }
    }

    @Test
    public void factory_ofInstant_history() {
        doTest_factory_ofInstant_all(-2820, 2820);
    }

    //-----------------------------------------------------------------------
    @Test
    public void factory_ofInstant_minYear() {
        doTest_factory_ofInstant_all(Year.MIN_VALUE, Year.MIN_VALUE + 420);
    }

    @Test
    public void factory_ofInstant_tooLow() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            long days_0000_to_1970 = (146097 * 5) - (30 * 365 + 7);
            int year = Year.MIN_VALUE - 1;
            long days = (year * 365L + (year / 4 - year / 100 + year / 400)) - days_0000_to_1970;
            Instant instant = Instant.ofEpochSecond(days * 24L * 60L * 60L);
            OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        });
    }

    @Test
    public void factory_ofInstant_maxYear() {
        doTest_factory_ofInstant_all(Year.MAX_VALUE - 420, Year.MAX_VALUE);
    }

    @Test
    public void factory_ofInstant_tooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            long days_0000_to_1970 = (146097 * 5) - (30 * 365 + 7);
            long year = Year.MAX_VALUE + 1L;
            long days = (year * 365L + (year / 4 - year / 100 + year / 400)) - days_0000_to_1970;
            Instant instant = Instant.ofEpochSecond(days * 24L * 60L * 60L);
            OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        });
    }

    //-----------------------------------------------------------------------
    @Test
    public void factory_ofInstant_minWithMinOffset() {
        long days_0000_to_1970 = (146097 * 5) - (30 * 365 + 7);
        int year = Year.MIN_VALUE;
        long days = (year * 365L + (year / 4 - year / 100 + year / 400)) - days_0000_to_1970;
        Instant instant = Instant.ofEpochSecond(days * 24L * 60L * 60L - OFFSET_MIN.getTotalSeconds());
        OffsetDateTime test = OffsetDateTime.ofInstant(instant, OFFSET_MIN);
        assertEquals(Year.MIN_VALUE, test.getYear());
        assertEquals(1, test.getMonth().getValue());
        assertEquals(1, test.getDayOfMonth());
        assertEquals(OFFSET_MIN, test.getOffset());
        assertEquals(0, test.getHour());
        assertEquals(0, test.getMinute());
        assertEquals(0, test.getSecond());
        assertEquals(0, test.getNano());
    }

    @Test
    public void factory_ofInstant_minWithMaxOffset() {
        long days_0000_to_1970 = (146097 * 5) - (30 * 365 + 7);
        int year = Year.MIN_VALUE;
        long days = (year * 365L + (year / 4 - year / 100 + year / 400)) - days_0000_to_1970;
        Instant instant = Instant.ofEpochSecond(days * 24L * 60L * 60L - OFFSET_MAX.getTotalSeconds());
        OffsetDateTime test = OffsetDateTime.ofInstant(instant, OFFSET_MAX);
        assertEquals(Year.MIN_VALUE, test.getYear());
        assertEquals(1, test.getMonth().getValue());
        assertEquals(1, test.getDayOfMonth());
        assertEquals(OFFSET_MAX, test.getOffset());
        assertEquals(0, test.getHour());
        assertEquals(0, test.getMinute());
        assertEquals(0, test.getSecond());
        assertEquals(0, test.getNano());
    }

    @Test
    public void factory_ofInstant_maxWithMinOffset() {
        long days_0000_to_1970 = (146097 * 5) - (30 * 365 + 7);
        int year = Year.MAX_VALUE;
        long days = (year * 365L + (year / 4 - year / 100 + year / 400)) + 365 - days_0000_to_1970;
        Instant instant = Instant.ofEpochSecond((days + 1) * 24L * 60L * 60L - 1 - OFFSET_MIN.getTotalSeconds());
        OffsetDateTime test = OffsetDateTime.ofInstant(instant, OFFSET_MIN);
        assertEquals(Year.MAX_VALUE, test.getYear());
        assertEquals(12, test.getMonth().getValue());
        assertEquals(31, test.getDayOfMonth());
        assertEquals(OFFSET_MIN, test.getOffset());
        assertEquals(23, test.getHour());
        assertEquals(59, test.getMinute());
        assertEquals(59, test.getSecond());
        assertEquals(0, test.getNano());
    }

    @Test
    public void factory_ofInstant_maxWithMaxOffset() {
        long days_0000_to_1970 = (146097 * 5) - (30 * 365 + 7);
        int year = Year.MAX_VALUE;
        long days = (year * 365L + (year / 4 - year / 100 + year / 400)) + 365 - days_0000_to_1970;
        Instant instant = Instant.ofEpochSecond((days + 1) * 24L * 60L * 60L - 1 - OFFSET_MAX.getTotalSeconds());
        OffsetDateTime test = OffsetDateTime.ofInstant(instant, OFFSET_MAX);
        assertEquals(Year.MAX_VALUE, test.getYear());
        assertEquals(12, test.getMonth().getValue());
        assertEquals(31, test.getDayOfMonth());
        assertEquals(OFFSET_MAX, test.getOffset());
        assertEquals(23, test.getHour());
        assertEquals(59, test.getMinute());
        assertEquals(59, test.getSecond());
        assertEquals(0, test.getNano());
    }

    //-----------------------------------------------------------------------
    @Test
    public void factory_ofInstant_maxInstantWithMaxOffset() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant instant = Instant.ofEpochSecond(Long.MAX_VALUE);
            OffsetDateTime.ofInstant(instant, OFFSET_MAX);
        });
    }

    @Test
    public void factory_ofInstant_maxInstantWithMinOffset() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            Instant instant = Instant.ofEpochSecond(Long.MAX_VALUE);
            OffsetDateTime.ofInstant(instant, OFFSET_MIN);
        });
    }

    //-----------------------------------------------------------------------
    private void doTest_factory_ofInstant_all(long minYear, long maxYear) {
        long days_0000_to_1970 = (146097 * 5) - (30 * 365 + 7);
        int minOffset = (minYear <= 0 ? 0 : 3);
        int maxOffset = (maxYear <= 0 ? 0 : 3);
        long minDays = (minYear * 365L + ((minYear + minOffset) / 4L - (minYear + minOffset) / 100L + (minYear + minOffset) / 400L)) - days_0000_to_1970;
        long maxDays = (maxYear * 365L + ((maxYear + maxOffset) / 4L - (maxYear + maxOffset) / 100L + (maxYear + maxOffset) / 400L)) + 365L - days_0000_to_1970;

        final LocalDate maxDate = LocalDate.of(Year.MAX_VALUE, 12, 31);
        OffsetDateTime expected = OffsetDateTime.of(LocalDate.of((int) minYear, 1, 1), LocalTime.of(0, 0, 0, 0), ZoneOffset.UTC);
        for (long i = minDays; i < maxDays; i++) {
            Instant instant = Instant.ofEpochSecond(i * 24L * 60L * 60L);
            try {
                OffsetDateTime test = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
                assertEquals(expected, test);
                if (expected.toLocalDate().equals(maxDate) == false) {
                    expected = expected.plusDays(1);
                }
            } catch (RuntimeException|Error ex) {
                System.out.println("Error: " + i + " " + expected);
                throw ex;
            }
        }
    }

    // for performance testing
    //    private void doTest_factory_ofInstant_all(int minYear, int maxYear) {
    //        long days_0000_to_1970 = (146097 * 5) - (30 * 365 + 7);
    //        int minOffset = (minYear <= 0 ? 0 : 3);
    //        int maxOffset = (maxYear <= 0 ? 0 : 3);
    //        long minDays = (long) (minYear * 365L + ((minYear + minOffset) / 4L - (minYear + minOffset) / 100L + (minYear + minOffset) / 400L)) - days_0000_to_1970;
    //        long maxDays = (long) (maxYear * 365L + ((maxYear + maxOffset) / 4L - (maxYear + maxOffset) / 100L + (maxYear + maxOffset) / 400L)) + 365L - days_0000_to_1970;
    //
    //        OffsetDateTime expected = OffsetDateTime.dateTime(minYear, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    //        Date cutover = new Date(Long.MIN_VALUE);
    //        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    //        cal.setGregorianChange(cutover);
    //        for (long i = minDays; i < maxDays; i++) {
    //            Instant instant = Instant.instant(i * 24L * 60L * 60L);
    //            try {
    //                cal.setTimeInMillis(instant.getEpochSecond() * 1000L);
    //                assertEquals(cal.get(GregorianCalendar.MONTH), expected.getMonth().getValue() - 1);
    //                assertEquals(cal.get(GregorianCalendar.DAY_OF_MONTH), expected.getDayOfMonth().getValue());
    //                expected = expected.plusDays(1);
    //            } catch (RuntimeException ex) {
    //                System.out.println("Error: " + i + " " + expected);
    //                throw ex;
    //            } catch (Error ex) {
    //                System.out.println("Error: " + i + " " + expected);
    //                throw ex;
    //            }
    //        }
    //    }

    //-----------------------------------------------------------------------
    @Test
    public void test_toInstant_19700101() {
        OffsetDateTime dt = OffsetDateTime.of(LocalDate.of(1970, 1, 1), LocalTime.of(0, 0, 0, 0), ZoneOffset.UTC);
        Instant test = dt.toInstant();
        assertEquals(0, test.getEpochSecond());
        assertEquals(0, test.getNano());
    }

    @Test
    public void test_toInstant_19700101_oneNano() {
        OffsetDateTime dt = OffsetDateTime.of(LocalDate.of(1970, 1, 1), LocalTime.of(0, 0, 0, 1), ZoneOffset.UTC);
        Instant test = dt.toInstant();
        assertEquals(0, test.getEpochSecond());
        assertEquals(1, test.getNano());
    }

    @Test
    public void test_toInstant_19700101_minusOneNano() {
        OffsetDateTime dt = OffsetDateTime.of(LocalDate.of(1969, 12, 31), LocalTime.of(23, 59, 59, 999999999), ZoneOffset.UTC);
        Instant test = dt.toInstant();
        assertEquals(-1, test.getEpochSecond());
        assertEquals(999999999, test.getNano());
    }

    @Test
    public void test_toInstant_19700102() {
        OffsetDateTime dt = OffsetDateTime.of(LocalDate.of(1970, 1, 2), LocalTime.of(0, 0, 0, 0), ZoneOffset.UTC);
        Instant test = dt.toInstant();
        assertEquals(24L * 60L * 60L, test.getEpochSecond());
        assertEquals(0, test.getNano());
    }

    @Test
    public void test_toInstant_19691231() {
        OffsetDateTime dt = OffsetDateTime.of(LocalDate.of(1969, 12, 31), LocalTime.of(0, 0, 0, 0), ZoneOffset.UTC);
        Instant test = dt.toInstant();
        assertEquals(-24L * 60L * 60L, test.getEpochSecond());
        assertEquals(0, test.getNano());
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_toEpochSecond_19700101() {
        OffsetDateTime dt = OffsetDateTime.of(LocalDate.of(1970, 1, 1), LocalTime.of(0, 0, 0, 0), ZoneOffset.UTC);
        assertEquals(0, dt.toEpochSecond());
    }

    @Test
    public void test_toEpochSecond_19700101_oneNano() {
        OffsetDateTime dt = OffsetDateTime.of(LocalDate.of(1970, 1, 1), LocalTime.of( 0, 0, 0, 1), ZoneOffset.UTC);
        assertEquals(0, dt.toEpochSecond());
    }

    @Test
    public void test_toEpochSecond_19700101_minusOneNano() {
        OffsetDateTime dt = OffsetDateTime.of(LocalDate.of(1969, 12, 31), LocalTime.of(23, 59, 59, 999999999), ZoneOffset.UTC);
        assertEquals(-1, dt.toEpochSecond());
    }

    @Test
    public void test_toEpochSecond_19700102() {
        OffsetDateTime dt = OffsetDateTime.of(LocalDate.of(1970, 1, 2), LocalTime.of(0, 0, 0, 0), ZoneOffset.UTC);
        assertEquals(24L * 60L * 60L, dt.toEpochSecond());
    }

    @Test
    public void test_toEpochSecond_19691231() {
        OffsetDateTime dt = OffsetDateTime.of(LocalDate.of(1969, 12, 31), LocalTime.of(0, 0, 0, 0), ZoneOffset.UTC);
        assertEquals(-24L * 60L * 60L, dt.toEpochSecond());
    }

}
