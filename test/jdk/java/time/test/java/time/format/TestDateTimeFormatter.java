/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static java.time.format.DateTimeFormatter.*;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
import java.time.chrono.Chronology;
import java.time.chrono.ThaiBuddhistChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DecimalStyle;
import java.time.format.SignStyle;
import java.time.format.ResolverStyle;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test DateTimeFormatter.
 * @bug 8085887 8293146 8210336
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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

    Object[][] exceptionCases() {
        return new Object[][] {
                {LocalDateTime.of(2000, 1, 1, 1, 1), "z", "ZoneId"},
                {OffsetDateTime.of(2000, 1, 1, 3, 3, 3, 0, ZoneOffset.ofTotalSeconds(60)), "z", "ZoneId"},
        };
    }

    // Test cases that should throw an exception with a cogent message
    // containing the Type being queried and the Temporal being queried.
    @ParameterizedTest
    @MethodSource("exceptionCases")
    public void test_throws_message(Temporal temporal, String pattern, String queryName) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
        try {
            fmt.format(temporal);
            fail("Format using \"" + pattern + "\" with " +
                    temporal + " should have failed");
        } catch (DateTimeException dte) {
            String msg = dte.getMessage();
            // Verify message contains the type that is missing and the temporal value
            assertTrue(msg.contains(queryName),
                    String.format("\"%s\" missing from %s", queryName, msg));
            String s = temporal.toString();
            assertTrue(msg.contains(s),
                    String.format("\"%s\" missing from %s", s, msg));
        }

    }

    // Test cases that should throw an exception with a cogent message when missing the Chronology
    @Test
    public void test_throws_message_chrono() {
        Chronology chrono = ThaiBuddhistChronology.INSTANCE;
        DateTimeFormatter fmt = new DateTimeFormatterBuilder().appendZoneId().toFormatter()
                .withChronology(chrono);
        LocalTime now = LocalTime.now();
        try {
            fmt.format(now);
            fail("Format using appendZoneId() should have failed");
        } catch (DateTimeException dte) {
            String msg = dte.getMessage();
            // Verify message contains the type that is missing and the temporal value
            assertTrue(msg.contains("ZoneId"),
                    String.format("\"%s\" missing from %s", "ZoneId", msg));
            assertTrue(msg.contains(chrono.toString()),
                    String.format("\"%s\" missing from %s", chrono.toString(), msg));
        }

    }

    // Test cases that should throw an exception with a cogent message when missing the ZoneId
    @Test
    public void test_throws_message_zone() {
        ZoneId zone = ZoneId.of("Pacific/Honolulu");
        DateTimeFormatter fmt = new DateTimeFormatterBuilder().appendChronologyId().toFormatter()
                .withZone(zone);
        LocalTime now = LocalTime.now();
        try {
            fmt.format(now);
            fail("Format using appendChronologyId() should have failed");
        } catch (DateTimeException dte) {
            String msg = dte.getMessage();
            // Verify message contains the type that is missing and the temporal value
            assertTrue(msg.contains("Chronology"),
                    String.format("\"%s\" missing from %s", "Chronology", msg));
            assertTrue(msg.contains(zone.toString()),
                    String.format("\"%s\" missing from %s", zone.toString(), msg));
        }

    }

    private static final DateTimeFormatter STRICT_WEEK = DateTimeFormatter.ofPattern("YYYY-'W'ww-e")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Locale EGYPT = Locale.forLanguageTag("en-EG");
    Object[][] data_week53Dates() {
        return new Object[][] {
                // WeekFields[SUNDAY,1]
                {"2012-W53-1", Locale.US, null},
                {"2013-W53-1", Locale.US, null},
                {"2014-W53-1", Locale.US, null},
                {"2015-W53-1", Locale.US, null},
                {"2016-W53-1", Locale.US, LocalDate.of(2016, 12, 25)},
                {"2017-W53-1", Locale.US, null},
                {"2018-W53-1", Locale.US, null},
                {"2019-W53-1", Locale.US, null},
                {"2020-W53-1", Locale.US, null},
                {"2021-W53-1", Locale.US, null},
                {"2022-W53-1", Locale.US, LocalDate.of(2022, 12, 25)},

                // WeekFields[MONDAY,4]
                {"2012-W53-1", Locale.UK, null},
                {"2013-W53-1", Locale.UK, null},
                {"2014-W53-1", Locale.UK, null},
                {"2015-W53-1", Locale.UK, LocalDate.of(2015, 12, 28)},
                {"2016-W53-1", Locale.UK, null},
                {"2017-W53-1", Locale.UK, null},
                {"2018-W53-1", Locale.UK, null},
                {"2019-W53-1", Locale.UK, null},
                {"2020-W53-1", Locale.UK, LocalDate.of(2020, 12, 28)},
                {"2021-W53-1", Locale.UK, null},
                {"2022-W53-1", Locale.UK, null},

                // WeekFields[SATURDAY,1]
                {"2012-W53-1", EGYPT, null},
                {"2013-W53-1", EGYPT, null},
                {"2014-W53-1", EGYPT, null},
                {"2015-W53-1", EGYPT, null},
                {"2016-W53-1", EGYPT, LocalDate.of(2016, 12, 24)},
                {"2017-W53-1", EGYPT, null},
                {"2018-W53-1", EGYPT, null},
                {"2019-W53-1", EGYPT, null},
                {"2020-W53-1", EGYPT, null},
                {"2021-W53-1", EGYPT, LocalDate.of(2021, 12, 25)},
                {"2022-W53-1", EGYPT, null}
        };
    }

    @ParameterizedTest
    @MethodSource("data_week53Dates")
    public void test_week_53(String weekDate, Locale locale, LocalDate expected) {
        var f = STRICT_WEEK.withLocale(locale);
        if (expected != null) {
            assertEquals(expected, LocalDate.parse(weekDate, f));
        } else {
            assertThrows(DateTimeException.class, () -> LocalDate.parse(weekDate, f));
        }
    }

    private static Stream<Arguments> data_iso_short_offset_parse() {
        return Stream.of(
            Arguments.of("20260123-01", BASIC_ISO_DATE, "20260123-0100"),
            Arguments.of("20260123+00", BASIC_ISO_DATE, "20260123Z"),
            Arguments.of("20260123-00", BASIC_ISO_DATE, "20260123Z"),
            Arguments.of("2026-01-23-01", ISO_DATE, "2026-01-23-01:00"),
            Arguments.of("2026-01-23+00", ISO_DATE, "2026-01-23Z"),
            Arguments.of("2026-01-23-00", ISO_DATE, "2026-01-23Z"),
            Arguments.of("2026-01-23T11:30:59-01", ISO_DATE_TIME, "2026-01-23T11:30:59-01:00"),
            Arguments.of("2026-01-23T11:30:59+00", ISO_DATE_TIME, "2026-01-23T11:30:59Z"),
            Arguments.of("2026-01-23T11:30:59-00", ISO_DATE_TIME, "2026-01-23T11:30:59Z"),
            Arguments.of("11:30:59-01", ISO_TIME, "11:30:59-01:00"),
            Arguments.of("11:30:59+00", ISO_TIME, "11:30:59Z"),
            Arguments.of("11:30:59-00", ISO_TIME, "11:30:59Z"),
            Arguments.of("2026-01-23-01", ISO_OFFSET_DATE, "2026-01-23-01:00"),
            Arguments.of("2026-01-23+00", ISO_OFFSET_DATE, "2026-01-23Z"),
            Arguments.of("2026-01-23-00", ISO_OFFSET_DATE, "2026-01-23Z"),
            Arguments.of("2026-01-23T11:30:59-01", ISO_OFFSET_DATE_TIME, "2026-01-23T11:30:59-01:00"),
            Arguments.of("2026-01-23T11:30:59+00", ISO_OFFSET_DATE_TIME, "2026-01-23T11:30:59Z"),
            Arguments.of("2026-01-23T11:30:59-00", ISO_OFFSET_DATE_TIME, "2026-01-23T11:30:59Z"),
            Arguments.of("11:30:59-01", ISO_OFFSET_TIME, "11:30:59-01:00"),
            Arguments.of("11:30:59+00", ISO_OFFSET_TIME, "11:30:59Z"),
            Arguments.of("11:30:59-00", ISO_OFFSET_TIME, "11:30:59Z"),
            Arguments.of("2026-023-01", ISO_ORDINAL_DATE, "2026-023-01:00"),
            Arguments.of("2026-023+00", ISO_ORDINAL_DATE, "2026-023Z"),
            Arguments.of("2026-023-00", ISO_ORDINAL_DATE, "2026-023Z"),
            Arguments.of("2026-W04-5-01", ISO_WEEK_DATE, "2026-W04-5-01:00"),
            Arguments.of("2026-W04-5+00", ISO_WEEK_DATE, "2026-W04-5Z"),
            Arguments.of("2026-W04-5-00", ISO_WEEK_DATE, "2026-W04-5Z"),
            Arguments.of("2026-01-23T11:30:59-01", ISO_ZONED_DATE_TIME, "2026-01-23T11:30:59-01:00"),
            Arguments.of("2026-01-23T11:30:59+00", ISO_ZONED_DATE_TIME, "2026-01-23T11:30:59Z"),
            Arguments.of("2026-01-23T11:30:59-00", ISO_ZONED_DATE_TIME, "2026-01-23T11:30:59Z"),
            Arguments.of("2026-01-23T11:30:59-01", ISO_INSTANT, "2026-01-23T12:30:59Z"),
            Arguments.of("2026-01-23T11:30:59+00", ISO_INSTANT, "2026-01-23T11:30:59Z"),
            Arguments.of("2026-01-23T11:30:59-00", ISO_INSTANT, "2026-01-23T11:30:59Z")
        );
    }

    // Checks if predefined ISO formatters can parse hour-only offsets
    @ParameterizedTest
    @MethodSource("data_iso_short_offset_parse")
    public void test_iso_short_offset_parse(String text, DateTimeFormatter formatter, String expected) {
        assertEquals(expected, formatter.format(formatter.parse(text)));
    }
}
