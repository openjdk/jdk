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
package test.java.time.format;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.chrono.ThaiBuddhistChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DecimalStyle;
import java.time.format.SignStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.function.Function;

import org.testng.annotations.Test;

/**
 * Test DateTimeFormatter.
 */
@Test
public class TestDateTimeFormatter {

    @Test
    public void test_withLocale_same() throws Exception {
        DateTimeFormatter base =
            new DateTimeFormatterBuilder().appendLiteral("ONE")
                                          .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
                                          .toFormatter(Locale.ENGLISH)
                                          .withDecimalStyle(DecimalStyle.STANDARD);
        DateTimeFormatter test = base.withLocale(Locale.ENGLISH);
        assertSame(test, base);
    }

    @Test
    public void test_parse_errorMessage() throws Exception {
        assertGoodErrorDate(DayOfWeek::from, "DayOfWeek");
        assertGoodErrorDate(Month::from, "Month");
        assertGoodErrorDate(YearMonth::from, "YearMonth");
        assertGoodErrorDate(MonthDay::from, "MonthDay");
        assertGoodErrorDate(LocalDate::from, "LocalDate");
        assertGoodErrorDate(LocalTime::from, "LocalTime");
        assertGoodErrorDate(LocalDateTime::from, "LocalDateTime");
        assertGoodErrorDate(OffsetTime::from, "OffsetTime");
        assertGoodErrorDate(OffsetDateTime::from, "OffsetDateTime");
        assertGoodErrorDate(ZonedDateTime::from, "ZonedDateTime");
        assertGoodErrorDate(Instant::from, "Instant");
        assertGoodErrorDate(ZoneOffset::from, "ZoneOffset");
        assertGoodErrorDate(ZoneId::from, "ZoneId");
        assertGoodErrorDate(ThaiBuddhistChronology.INSTANCE::date, "");

        assertGoodErrorTime(DayOfWeek::from, "DayOfWeek");
        assertGoodErrorTime(Month::from, "Month");
        assertGoodErrorTime(Year::from, "Year");
        assertGoodErrorTime(YearMonth::from, "YearMonth");
        assertGoodErrorTime(MonthDay::from, "MonthDay");
        assertGoodErrorTime(LocalDate::from, "LocalDate");
        assertGoodErrorTime(LocalTime::from, "LocalTime");
        assertGoodErrorTime(LocalDateTime::from, "LocalDateTime");
        assertGoodErrorTime(OffsetTime::from, "OffsetTime");
        assertGoodErrorTime(OffsetDateTime::from, "OffsetDateTime");
        assertGoodErrorTime(ZonedDateTime::from, "ZonedDateTime");
        assertGoodErrorTime(Instant::from, "Instant");
        assertGoodErrorTime(ZoneOffset::from, "ZoneOffset");
        assertGoodErrorTime(ZoneId::from, "ZoneId");
        assertGoodErrorTime(ThaiBuddhistChronology.INSTANCE::date, "");
    }

    private void assertGoodErrorDate(Function<TemporalAccessor, Object> function, String expectedText) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-mm-dd");
        TemporalAccessor temporal = f.parse("2010-06-30");
        try {
            function.apply(temporal);
            fail("Should have failed");
        } catch (DateTimeException ex) {
            String msg = ex.getMessage();
            assertTrue(msg.contains(expectedText), msg);
            assertTrue(msg.contains("Year"), msg);
            assertTrue(msg.contains("MinuteOfHour"), msg);
            assertTrue(msg.contains("DayOfMonth"), msg);
        }
    }

    private void assertGoodErrorTime(Function<TemporalAccessor, Object> function, String expectedText) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:MM:ss");
        TemporalAccessor temporal = f.parse("11:30:56");
        try {
            function.apply(temporal);
            fail("Should have failed");
        } catch (DateTimeException ex) {
            String msg = ex.getMessage();
            assertTrue(msg.contains(expectedText), msg);
            assertTrue(msg.contains("HourOfDay"), msg);
            assertTrue(msg.contains("MonthOfYear"), msg);
            assertTrue(msg.contains("SecondOfMinute"), msg);
        }
    }

    @Test
    public void test_parsed_toString_resolvedTime() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm:ss");
        TemporalAccessor temporal = f.parse("11:30:56");
        String msg = temporal.toString();
        assertTrue(msg.contains("11:30:56"), msg);
    }

    @Test
    public void test_parsed_toString_resolvedDate() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        TemporalAccessor temporal = f.parse("2010-06-30");
        String msg = temporal.toString();
        assertTrue(msg.contains("2010-06-30"), msg);
    }

    @Test
    public void test_parsed_toString_resolvedDateTime() {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        TemporalAccessor temporal = f.parse("2010-06-30 11:30:56");
        String msg = temporal.toString();
        assertTrue(msg.contains("2010-06-30"), msg);
        assertTrue(msg.contains("11:30:56"), msg);
    }

}
