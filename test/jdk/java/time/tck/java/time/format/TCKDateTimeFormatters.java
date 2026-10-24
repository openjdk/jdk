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
package tck.java.time.format;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.DAY_OF_YEAR;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.INSTANT_SECONDS;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.OFFSET_SECONDS;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.text.ParsePosition;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.chrono.Chronology;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.format.ResolverStyle;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test DateTimeFormatter.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TCKDateTimeFormatters {

    @BeforeEach
    public void setUp() {
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_format_nullTemporalAccessor() {
        Assertions.assertThrows(NullPointerException.class, () -> DateTimeFormatter.ISO_DATE.format((TemporalAccessor) null));
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @Test
    public void test_pattern_String() {
        DateTimeFormatter test = DateTimeFormatter.ofPattern("d MMM yyyy");
        Locale fmtLocale = Locale.getDefault(Locale.Category.FORMAT);
        assertEquals("30 " +
                Month.JUNE.getDisplayName(TextStyle.SHORT, fmtLocale) + " 2012", test.format(LocalDate.of(2012, 6, 30)));
        assertEquals(fmtLocale, test.getLocale(), "Locale.Category.FORMAT");
    }

    @Test
    public void test_pattern_String_invalid() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> DateTimeFormatter.ofPattern("p"));
    }

    @Test
    public void test_pattern_String_null() {
        Assertions.assertThrows(NullPointerException.class, () -> DateTimeFormatter.ofPattern(null));
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @Test
    public void test_pattern_StringLocale() {
        DateTimeFormatter test = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);
        assertEquals("30 Jun 2012", test.format(LocalDate.of(2012, 6, 30)));
        assertEquals(Locale.UK, test.getLocale());
    }

    @Test
    public void test_pattern_StringLocale_invalid() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> DateTimeFormatter.ofPattern("p", Locale.UK));
    }

    @Test
    public void test_pattern_StringLocale_nullPattern() {
        Assertions.assertThrows(NullPointerException.class, () -> DateTimeFormatter.ofPattern(null, Locale.UK));
    }

    @Test
    public void test_pattern_StringLocale_nullLocale() {
        Assertions.assertThrows(NullPointerException.class, () -> DateTimeFormatter.ofPattern("yyyy", null));
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @Test
    public void test_ofLocalizedDate_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).getChronology());
        assertEquals(null, DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).getZone());
        assertEquals(ResolverStyle.SMART, DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).getResolverStyle());
    }

    @Test
    public void test_ofLocalizedTime_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.ofLocalizedTime(FormatStyle.FULL).getChronology());
        assertEquals(null, DateTimeFormatter.ofLocalizedTime(FormatStyle.FULL).getZone());
        assertEquals(ResolverStyle.SMART, DateTimeFormatter.ofLocalizedTime(FormatStyle.FULL).getResolverStyle());
    }

    @Test
    public void test_ofLocalizedDateTime1_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).getChronology());
        assertEquals(null, DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).getZone());
        assertEquals(ResolverStyle.SMART, DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).getResolverStyle());
    }

    @Test
    public void test_ofLocalizedDateTime2_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.MEDIUM).getChronology());
        assertEquals(null, DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.MEDIUM).getZone());
        assertEquals(ResolverStyle.SMART, DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.MEDIUM).getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] provider_sample_isoLocalDate() {
        return new Object[][]{
                {2008, null, null, null, null, null, DateTimeException.class},
                {null, 6, null, null, null, null, DateTimeException.class},
                {null, null, 30, null, null, null, DateTimeException.class},
                {null, null, null, "+01:00", null, null, DateTimeException.class},
                {null, null, null, null, "Europe/Paris", null, DateTimeException.class},
                {2008, 6, null, null, null, null, DateTimeException.class},
                {null, 6, 30, null, null, null, DateTimeException.class},

                {2008, 6, 30, null, null,                   "2008-06-30", null},
                {2008, 6, 30, "+01:00", null,               "2008-06-30", null},
                {2008, 6, 30, "+01:00", "Europe/Paris",     "2008-06-30", null},
                {2008, 6, 30, null, "Europe/Paris",         "2008-06-30", null},

                {123456, 6, 30, null, null,                 "+123456-06-30", null},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoLocalDate")
    public void test_print_isoLocalDate(
            Integer year, Integer month, Integer day, String offsetId, String zoneId,
            String expected, Class<?> expectedEx) {
        TemporalAccessor test = buildAccessor(year, month, day, null, null, null, null, offsetId, zoneId);
        if (expectedEx == null) {
            assertEquals(expected, DateTimeFormatter.ISO_LOCAL_DATE.format(test));
        } else {
            try {
                DateTimeFormatter.ISO_LOCAL_DATE.format(test);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoLocalDate")
    public void test_parse_isoLocalDate(
            Integer year, Integer month, Integer day, String offsetId, String zoneId,
            String input, Class<?> invalid) {
        if (input != null) {
            Expected expected = createDate(year, month, day);
            // offset/zone not expected to be parsed
            assertParseMatch(DateTimeFormatter.ISO_LOCAL_DATE.parseUnresolved(input, new ParsePosition(0)), expected);
        }
    }

    @Test
    public void test_parse_isoLocalDate_999999999() {
        Expected expected = createDate(999999999, 8, 6);
        assertParseMatch(DateTimeFormatter.ISO_LOCAL_DATE.parseUnresolved("+999999999-08-06", new ParsePosition(0)), expected);
        assertEquals(LocalDate.of(999999999, 8, 6), LocalDate.parse("+999999999-08-06"));
    }

    @Test
    public void test_parse_isoLocalDate_1000000000() {
        Expected expected = createDate(1000000000, 8, 6);
        assertParseMatch(DateTimeFormatter.ISO_LOCAL_DATE.parseUnresolved("+1000000000-08-06", new ParsePosition(0)), expected);
    }

    @Test
    public void test_parse_isoLocalDate_1000000000_failedCreate() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.parse("+1000000000-08-06"));
    }

    @Test
    public void test_parse_isoLocalDate_M999999999() {
        Expected expected = createDate(-999999999, 8, 6);
        assertParseMatch(DateTimeFormatter.ISO_LOCAL_DATE.parseUnresolved("-999999999-08-06", new ParsePosition(0)), expected);
        assertEquals(LocalDate.of(-999999999, 8, 6), LocalDate.parse("-999999999-08-06"));
    }

    @Test
    public void test_parse_isoLocalDate_M1000000000() {
        Expected expected = createDate(-1000000000, 8, 6);
        assertParseMatch(DateTimeFormatter.ISO_LOCAL_DATE.parseUnresolved("-1000000000-08-06", new ParsePosition(0)), expected);
    }

    @Test
    public void test_parse_isoLocalDate_M1000000000_failedCreate() {
        Assertions.assertThrows(DateTimeException.class, () -> LocalDate.parse("-1000000000-08-06"));
    }

    @Test
    public void test_isoLocalDate_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.ISO_LOCAL_DATE.getChronology());
        assertEquals(null, DateTimeFormatter.ISO_LOCAL_DATE.getZone());
        assertEquals(ResolverStyle.STRICT, DateTimeFormatter.ISO_LOCAL_DATE.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] provider_sample_isoOffsetDate() {
        return new Object[][]{
                {2008, null, null, null, null, null, DateTimeException.class},
                {null, 6, null, null, null, null, DateTimeException.class},
                {null, null, 30, null, null, null, DateTimeException.class},
                {null, null, null, "+01:00", null, null, DateTimeException.class},
                {null, null, null, null, "Europe/Paris", null, DateTimeException.class},
                {2008, 6, null, null, null, null, DateTimeException.class},
                {null, 6, 30, null, null, null, DateTimeException.class},

                {2008, 6, 30, null, null,                   null, DateTimeException.class},
                {2008, 6, 30, "+01:00", null,               "2008-06-30+01:00", null},
                {2008, 6, 30, "+01:00", "Europe/Paris",     "2008-06-30+01:00", null},
                {2008, 6, 30, null, "Europe/Paris",         null, DateTimeException.class},

                {123456, 6, 30, "+01:00", null,             "+123456-06-30+01:00", null},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoOffsetDate")
    public void test_print_isoOffsetDate(
            Integer year, Integer month, Integer day, String offsetId, String zoneId,
            String expected, Class<?> expectedEx) {
        TemporalAccessor test = buildAccessor(year, month, day, null, null, null, null, offsetId, zoneId);
        if (expectedEx == null) {
            assertEquals(expected, DateTimeFormatter.ISO_OFFSET_DATE.format(test));
        } else {
            try {
                DateTimeFormatter.ISO_OFFSET_DATE.format(test);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoOffsetDate")
    public void test_parse_isoOffsetDate(
            Integer year, Integer month, Integer day, String offsetId, String zoneId,
            String input, Class<?> invalid) {
        if (input != null) {
            Expected expected = createDate(year, month, day);
            buildCalendrical(expected, offsetId, null);  // zone not expected to be parsed
            assertParseMatch(DateTimeFormatter.ISO_OFFSET_DATE.parseUnresolved(input, new ParsePosition(0)), expected);
        }
    }

    @Test
    public void test_isoOffsetDate_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.ISO_OFFSET_DATE.getChronology());
        assertEquals(null, DateTimeFormatter.ISO_OFFSET_DATE.getZone());
        assertEquals(ResolverStyle.STRICT, DateTimeFormatter.ISO_OFFSET_DATE.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] provider_sample_isoDate() {
        return new Object[][]{
                {2008, null, null, null, null, null, DateTimeException.class},
                {null, 6, null, null, null, null, DateTimeException.class},
                {null, null, 30, null, null, null, DateTimeException.class},
                {null, null, null, "+01:00", null, null, DateTimeException.class},
                {null, null, null, null, "Europe/Paris", null, DateTimeException.class},
                {2008, 6, null, null, null, null, DateTimeException.class},
                {null, 6, 30, null, null, null, DateTimeException.class},

                {2008, 6, 30, null, null,                   "2008-06-30", null},
                {2008, 6, 30, "+01:00", null,               "2008-06-30+01:00", null},
                {2008, 6, 30, "+01:00", "Europe/Paris",     "2008-06-30+01:00", null},
                {2008, 6, 30, null, "Europe/Paris",         "2008-06-30", null},

                {123456, 6, 30, "+01:00", "Europe/Paris",   "+123456-06-30+01:00", null},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoDate")
    public void test_print_isoDate(
            Integer year, Integer month, Integer day, String offsetId, String zoneId,
            String expected, Class<?> expectedEx) {
        TemporalAccessor test = buildAccessor(year, month, day, null, null, null, null, offsetId, zoneId);
        if (expectedEx == null) {
            assertEquals(expected, DateTimeFormatter.ISO_DATE.format(test));
        } else {
            try {
                DateTimeFormatter.ISO_DATE.format(test);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoDate")
    public void test_parse_isoDate(
            Integer year, Integer month, Integer day, String offsetId, String zoneId,
            String input, Class<?> invalid) {
        if (input != null) {
            Expected expected = createDate(year, month, day);
            if (offsetId != null) {
                expected.add(ZoneOffset.of(offsetId));
            }
            assertParseMatch(DateTimeFormatter.ISO_DATE.parseUnresolved(input, new ParsePosition(0)), expected);
        }
    }

    @Test
    public void test_isoDate_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.ISO_DATE.getChronology());
        assertEquals(null, DateTimeFormatter.ISO_DATE.getZone());
        assertEquals(ResolverStyle.STRICT, DateTimeFormatter.ISO_DATE.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] provider_sample_isoLocalTime() {
        return new Object[][]{
                {11, null, null, null, null, null, null, DateTimeException.class},
                {null, 5, null, null, null, null, null, DateTimeException.class},
                {null, null, 30, null, null, null, null, DateTimeException.class},
                {null, null, null, 1, null, null, null, DateTimeException.class},
                {null, null, null, null, "+01:00", null, null, DateTimeException.class},
                {null, null, null, null, null, "Europe/Paris", null, DateTimeException.class},

                {11, 5, null, null, null, null,     "11:05", null},
                {11, 5, 30, null, null, null,       "11:05:30", null},
                {11, 5, 30, 500000000, null, null,  "11:05:30.5", null},
                {11, 5, 30, 1, null, null,          "11:05:30.000000001", null},

                {11, 5, null, null, "+01:00", null,     "11:05", null},
                {11, 5, 30, null, "+01:00", null,       "11:05:30", null},
                {11, 5, 30, 500000000, "+01:00", null,  "11:05:30.5", null},
                {11, 5, 30, 1, "+01:00", null,          "11:05:30.000000001", null},

                {11, 5, null, null, "+01:00", "Europe/Paris",       "11:05", null},
                {11, 5, 30, null, "+01:00", "Europe/Paris",         "11:05:30", null},
                {11, 5, 30, 500000000, "+01:00", "Europe/Paris",    "11:05:30.5", null},
                {11, 5, 30, 1, "+01:00", "Europe/Paris",            "11:05:30.000000001", null},

                {11, 5, null, null, null, "Europe/Paris",       "11:05", null},
                {11, 5, 30, null, null, "Europe/Paris",         "11:05:30", null},
                {11, 5, 30, 500000000, null, "Europe/Paris",    "11:05:30.5", null},
                {11, 5, 30, 1, null, "Europe/Paris",            "11:05:30.000000001", null},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoLocalTime")
    public void test_print_isoLocalTime(
            Integer hour, Integer min, Integer sec, Integer nano, String offsetId, String zoneId,
            String expected, Class<?> expectedEx) {
        TemporalAccessor test = buildAccessor(null, null, null, hour, min, sec, nano, offsetId, zoneId);
        if (expectedEx == null) {
            assertEquals(expected, DateTimeFormatter.ISO_LOCAL_TIME.format(test));
        } else {
            try {
                DateTimeFormatter.ISO_LOCAL_TIME.format(test);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoLocalTime")
    public void test_parse_isoLocalTime(
            Integer hour, Integer min, Integer sec, Integer nano, String offsetId, String zoneId,
            String input, Class<?> invalid) {
        if (input != null) {
            Expected expected = createTime(hour, min, sec, nano);
            // offset/zone not expected to be parsed
            assertParseMatch(DateTimeFormatter.ISO_LOCAL_TIME.parseUnresolved(input, new ParsePosition(0)), expected);
        }
    }

    @Test
    public void test_isoLocalTime_basics() {
        assertEquals(null, DateTimeFormatter.ISO_LOCAL_TIME.getChronology());
        assertEquals(null, DateTimeFormatter.ISO_LOCAL_TIME.getZone());
        assertEquals(ResolverStyle.STRICT, DateTimeFormatter.ISO_LOCAL_TIME.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] provider_sample_isoOffsetTime() {
        return new Object[][]{
                {11, null, null, null, null, null, null, DateTimeException.class},
                {null, 5, null, null, null, null, null, DateTimeException.class},
                {null, null, 30, null, null, null, null, DateTimeException.class},
                {null, null, null, 1, null, null, null, DateTimeException.class},
                {null, null, null, null, "+01:00", null, null, DateTimeException.class},
                {null, null, null, null, null, "Europe/Paris", null, DateTimeException.class},

                {11, 5, null, null, null, null,     null, DateTimeException.class},
                {11, 5, 30, null, null, null,       null, DateTimeException.class},
                {11, 5, 30, 500000000, null, null,  null, DateTimeException.class},
                {11, 5, 30, 1, null, null,          null, DateTimeException.class},

                {11, 5, null, null, "+01:00", null,     "11:05+01:00", null},
                {11, 5, 30, null, "+01:00", null,       "11:05:30+01:00", null},
                {11, 5, 30, 500000000, "+01:00", null,  "11:05:30.5+01:00", null},
                {11, 5, 30, 1, "+01:00", null,          "11:05:30.000000001+01:00", null},

                {11, 5, null, null, "+01:00", "Europe/Paris",       "11:05+01:00", null},
                {11, 5, 30, null, "+01:00", "Europe/Paris",         "11:05:30+01:00", null},
                {11, 5, 30, 500000000, "+01:00", "Europe/Paris",    "11:05:30.5+01:00", null},
                {11, 5, 30, 1, "+01:00", "Europe/Paris",            "11:05:30.000000001+01:00", null},

                {11, 5, null, null, null, "Europe/Paris",       null, DateTimeException.class},
                {11, 5, 30, null, null, "Europe/Paris",         null, DateTimeException.class},
                {11, 5, 30, 500000000, null, "Europe/Paris",    null, DateTimeException.class},
                {11, 5, 30, 1, null, "Europe/Paris",            null, DateTimeException.class},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoOffsetTime")
    public void test_print_isoOffsetTime(
            Integer hour, Integer min, Integer sec, Integer nano, String offsetId, String zoneId,
            String expected, Class<?> expectedEx) {
        TemporalAccessor test = buildAccessor(null, null, null, hour, min, sec, nano, offsetId, zoneId);
        if (expectedEx == null) {
            assertEquals(expected, DateTimeFormatter.ISO_OFFSET_TIME.format(test));
        } else {
            try {
                DateTimeFormatter.ISO_OFFSET_TIME.format(test);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoOffsetTime")
    public void test_parse_isoOffsetTime(
            Integer hour, Integer min, Integer sec, Integer nano, String offsetId, String zoneId,
            String input, Class<?> invalid) {
        if (input != null) {
            Expected expected = createTime(hour, min, sec, nano);
            buildCalendrical(expected, offsetId, null);  // zoneId is not expected from parse
            assertParseMatch(DateTimeFormatter.ISO_OFFSET_TIME.parseUnresolved(input, new ParsePosition(0)), expected);
        }
    }

    @Test
    public void test_isoOffsetTime_basics() {
        assertEquals(null, DateTimeFormatter.ISO_OFFSET_TIME.getChronology());
        assertEquals(null, DateTimeFormatter.ISO_OFFSET_TIME.getZone());
        assertEquals(ResolverStyle.STRICT, DateTimeFormatter.ISO_OFFSET_TIME.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] provider_sample_isoTime() {
        return new Object[][]{
                {11, null, null, null, null, null, null, DateTimeException.class},
                {null, 5, null, null, null, null, null, DateTimeException.class},
                {null, null, 30, null, null, null, null, DateTimeException.class},
                {null, null, null, 1, null, null, null, DateTimeException.class},
                {null, null, null, null, "+01:00", null, null, DateTimeException.class},
                {null, null, null, null, null, "Europe/Paris", null, DateTimeException.class},

                {11, 5, null, null, null, null,     "11:05", null},
                {11, 5, 30, null, null, null,       "11:05:30", null},
                {11, 5, 30, 500000000, null, null,  "11:05:30.5", null},
                {11, 5, 30, 1, null, null,          "11:05:30.000000001", null},

                {11, 5, null, null, "+01:00", null,     "11:05+01:00", null},
                {11, 5, 30, null, "+01:00", null,       "11:05:30+01:00", null},
                {11, 5, 30, 500000000, "+01:00", null,  "11:05:30.5+01:00", null},
                {11, 5, 30, 1, "+01:00", null,          "11:05:30.000000001+01:00", null},

                {11, 5, null, null, "+01:00", "Europe/Paris",       "11:05+01:00", null},
                {11, 5, 30, null, "+01:00", "Europe/Paris",         "11:05:30+01:00", null},
                {11, 5, 30, 500000000, "+01:00", "Europe/Paris",    "11:05:30.5+01:00", null},
                {11, 5, 30, 1, "+01:00", "Europe/Paris",            "11:05:30.000000001+01:00", null},

                {11, 5, null, null, null, "Europe/Paris",       "11:05", null},
                {11, 5, 30, null, null, "Europe/Paris",         "11:05:30", null},
                {11, 5, 30, 500000000, null, "Europe/Paris",    "11:05:30.5", null},
                {11, 5, 30, 1, null, "Europe/Paris",            "11:05:30.000000001", null},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoTime")
    public void test_print_isoTime(
            Integer hour, Integer min, Integer sec, Integer nano, String offsetId, String zoneId,
            String expected, Class<?> expectedEx) {
        TemporalAccessor test = buildAccessor(null, null, null, hour, min, sec, nano, offsetId, zoneId);
        if (expectedEx == null) {
            assertEquals(expected, DateTimeFormatter.ISO_TIME.format(test));
        } else {
            try {
                DateTimeFormatter.ISO_TIME.format(test);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoTime")
    public void test_parse_isoTime(
            Integer hour, Integer min, Integer sec, Integer nano, String offsetId, String zoneId,
            String input, Class<?> invalid) {
        if (input != null) {
            Expected expected = createTime(hour, min, sec, nano);
            if (offsetId != null) {
                expected.add(ZoneOffset.of(offsetId));
            }
            assertParseMatch(DateTimeFormatter.ISO_TIME.parseUnresolved(input, new ParsePosition(0)), expected);
        }
    }

    @Test
    public void test_isoTime_basics() {
        assertEquals(null, DateTimeFormatter.ISO_TIME.getChronology());
        assertEquals(null, DateTimeFormatter.ISO_TIME.getZone());
        assertEquals(ResolverStyle.STRICT, DateTimeFormatter.ISO_TIME.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] provider_sample_isoLocalDateTime() {
        return new Object[][]{
                {2008, null, null, null, null, null, null, null, null, null, DateTimeException.class},
                {null, 6, null, null, null, null, null, null, null, null, DateTimeException.class},
                {null, null, 30, null, null, null, null, null, null, null, DateTimeException.class},
                {null, null, null, 11, null, null, null, null, null, null, DateTimeException.class},
                {null, null, null, null, 5, null, null, null, null, null, DateTimeException.class},
                {null, null, null, null, null, null, null, "+01:00", null, null, DateTimeException.class},
                {null, null, null, null, null, null, null, null, "Europe/Paris", null, DateTimeException.class},
                {2008, 6, 30, 11, null, null, null, null, null, null, DateTimeException.class},
                {2008, 6, 30, null, 5, null, null, null, null, null, DateTimeException.class},
                {2008, 6, null, 11, 5, null, null, null, null, null, DateTimeException.class},
                {2008, null, 30, 11, 5, null, null, null, null, null, DateTimeException.class},
                {null, 6, 30, 11, 5, null, null, null, null, null, DateTimeException.class},

                {2008, 6, 30, 11, 5, null, null, null, null,                    "2008-06-30T11:05", null},
                {2008, 6, 30, 11, 5, 30, null, null, null,                      "2008-06-30T11:05:30", null},
                {2008, 6, 30, 11, 5, 30, 500000000, null, null,                 "2008-06-30T11:05:30.5", null},
                {2008, 6, 30, 11, 5, 30, 1, null, null,                         "2008-06-30T11:05:30.000000001", null},

                {2008, 6, 30, 11, 5, null, null, "+01:00", null,                "2008-06-30T11:05", null},
                {2008, 6, 30, 11, 5, 30, null, "+01:00", null,                  "2008-06-30T11:05:30", null},
                {2008, 6, 30, 11, 5, 30, 500000000, "+01:00", null,             "2008-06-30T11:05:30.5", null},
                {2008, 6, 30, 11, 5, 30, 1, "+01:00", null,                     "2008-06-30T11:05:30.000000001", null},

                {2008, 6, 30, 11, 5, null, null, "+01:00", "Europe/Paris",      "2008-06-30T11:05", null},
                {2008, 6, 30, 11, 5, 30, null, "+01:00", "Europe/Paris",        "2008-06-30T11:05:30", null},
                {2008, 6, 30, 11, 5, 30, 500000000, "+01:00", "Europe/Paris",   "2008-06-30T11:05:30.5", null},
                {2008, 6, 30, 11, 5, 30, 1, "+01:00", "Europe/Paris",           "2008-06-30T11:05:30.000000001", null},

                {2008, 6, 30, 11, 5, null, null, null, "Europe/Paris",          "2008-06-30T11:05", null},
                {2008, 6, 30, 11, 5, 30, null, null, "Europe/Paris",            "2008-06-30T11:05:30", null},
                {2008, 6, 30, 11, 5, 30, 500000000, null, "Europe/Paris",       "2008-06-30T11:05:30.5", null},
                {2008, 6, 30, 11, 5, 30, 1, null, "Europe/Paris",               "2008-06-30T11:05:30.000000001", null},

                {123456, 6, 30, 11, 5, null, null, null, null,                  "+123456-06-30T11:05", null},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoLocalDateTime")
    public void test_print_isoLocalDateTime(
            Integer year, Integer month, Integer day,
            Integer hour, Integer min, Integer sec, Integer nano, String offsetId, String zoneId,
            String expected, Class<?> expectedEx) {
        TemporalAccessor test = buildAccessor(year, month, day, hour, min, sec, nano, offsetId, zoneId);
        if (expectedEx == null) {
            assertEquals(expected, DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(test));
        } else {
            try {
                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(test);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoLocalDateTime")
    public void test_parse_isoLocalDateTime(
            Integer year, Integer month, Integer day,
            Integer hour, Integer min, Integer sec, Integer nano, String offsetId, String zoneId,
            String input, Class<?> invalid) {
        if (input != null) {
            Expected expected = createDateTime(year, month, day, hour, min, sec, nano);
            assertParseMatch(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parseUnresolved(input, new ParsePosition(0)), expected);
        }
    }

    @Test
    public void test_isoLocalDateTime_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.ISO_LOCAL_DATE_TIME.getChronology());
        assertEquals(null, DateTimeFormatter.ISO_LOCAL_DATE_TIME.getZone());
        assertEquals(ResolverStyle.STRICT, DateTimeFormatter.ISO_LOCAL_DATE_TIME.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] provider_sample_isoOffsetDateTime() {
        return new Object[][]{
                {2008, null, null, null, null, null, null, null, null, null, DateTimeException.class},
                {null, 6, null, null, null, null, null, null, null, null, DateTimeException.class},
                {null, null, 30, null, null, null, null, null, null, null, DateTimeException.class},
                {null, null, null, 11, null, null, null, null, null, null, DateTimeException.class},
                {null, null, null, null, 5, null, null, null, null, null, DateTimeException.class},
                {null, null, null, null, null, null, null, "+01:00", null, null, DateTimeException.class},
                {null, null, null, null, null, null, null, null, "Europe/Paris", null, DateTimeException.class},
                {2008, 6, 30, 11, null, null, null, null, null, null, DateTimeException.class},
                {2008, 6, 30, null, 5, null, null, null, null, null, DateTimeException.class},
                {2008, 6, null, 11, 5, null, null, null, null, null, DateTimeException.class},
                {2008, null, 30, 11, 5, null, null, null, null, null, DateTimeException.class},
                {null, 6, 30, 11, 5, null, null, null, null, null, DateTimeException.class},

                {2008, 6, 30, 11, 5, null, null, null, null,                    null, DateTimeException.class},
                {2008, 6, 30, 11, 5, 30, null, null, null,                      null, DateTimeException.class},
                {2008, 6, 30, 11, 5, 30, 500000000, null, null,                 null, DateTimeException.class},
                {2008, 6, 30, 11, 5, 30, 1, null, null,                         null, DateTimeException.class},

                {2008, 6, 30, 11, 5, null, null, "+01:00", null,                "2008-06-30T11:05+01:00", null},
                {2008, 6, 30, 11, 5, 30, null, "+01:00", null,                  "2008-06-30T11:05:30+01:00", null},
                {2008, 6, 30, 11, 5, 30, 500000000, "+01:00", null,             "2008-06-30T11:05:30.5+01:00", null},
                {2008, 6, 30, 11, 5, 30, 1, "+01:00", null,                     "2008-06-30T11:05:30.000000001+01:00", null},

                {2008, 6, 30, 11, 5, null, null, "+01:00", "Europe/Paris",      "2008-06-30T11:05+01:00", null},
                {2008, 6, 30, 11, 5, 30, null, "+01:00", "Europe/Paris",        "2008-06-30T11:05:30+01:00", null},
                {2008, 6, 30, 11, 5, 30, 500000000, "+01:00", "Europe/Paris",   "2008-06-30T11:05:30.5+01:00", null},
                {2008, 6, 30, 11, 5, 30, 1, "+01:00", "Europe/Paris",           "2008-06-30T11:05:30.000000001+01:00", null},

                {2008, 6, 30, 11, 5, null, null, null, "Europe/Paris",          null, DateTimeException.class},
                {2008, 6, 30, 11, 5, 30, null, null, "Europe/Paris",            null, DateTimeException.class},
                {2008, 6, 30, 11, 5, 30, 500000000, null, "Europe/Paris",       null, DateTimeException.class},
                {2008, 6, 30, 11, 5, 30, 1, null, "Europe/Paris",               null, DateTimeException.class},

                {123456, 6, 30, 11, 5, null, null, "+01:00", null,              "+123456-06-30T11:05+01:00", null},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoOffsetDateTime")
    public void test_print_isoOffsetDateTime(
            Integer year, Integer month, Integer day,
            Integer hour, Integer min, Integer sec, Integer nano, String offsetId, String zoneId,
            String expected, Class<?> expectedEx) {
        TemporalAccessor test = buildAccessor(year, month, day, hour, min, sec, nano, offsetId, zoneId);
        if (expectedEx == null) {
            assertEquals(expected, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(test));
        } else {
            try {
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(test);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoOffsetDateTime")
    public void test_parse_isoOffsetDateTime(
            Integer year, Integer month, Integer day,
            Integer hour, Integer min, Integer sec, Integer nano, String offsetId, String zoneId,
            String input, Class<?> invalid) {
        if (input != null) {
            Expected expected = createDateTime(year, month, day, hour, min, sec, nano);
            buildCalendrical(expected, offsetId, null);  // zone not expected to be parsed
            assertParseMatch(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parseUnresolved(input, new ParsePosition(0)), expected);
        }
    }

    @Test
    public void test_isoOffsetDateTime_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.ISO_OFFSET_DATE_TIME.getChronology());
        assertEquals(null, DateTimeFormatter.ISO_OFFSET_DATE_TIME.getZone());
        assertEquals(ResolverStyle.STRICT, DateTimeFormatter.ISO_OFFSET_DATE_TIME.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] provider_sample_isoZonedDateTime() {
        return new Object[][]{
                {2008, null, null, null, null, null, null, null, null, null, DateTimeException.class},
                {null, 6, null, null, null, null, null, null, null, null, DateTimeException.class},
                {null, null, 30, null, null, null, null, null, null, null, DateTimeException.class},
                {null, null, null, 11, null, null, null, null, null, null, DateTimeException.class},
                {null, null, null, null, 5, null, null, null, null, null, DateTimeException.class},
                {null, null, null, null, null, null, null, "+01:00", null, null, DateTimeException.class},
                {null, null, null, null, null, null, null, null, "Europe/Paris", null, DateTimeException.class},
                {2008, 6, 30, 11, null, null, null, null, null, null, DateTimeException.class},
                {2008, 6, 30, null, 5, null, null, null, null, null, DateTimeException.class},
                {2008, 6, null, 11, 5, null, null, null, null, null, DateTimeException.class},
                {2008, null, 30, 11, 5, null, null, null, null, null, DateTimeException.class},
                {null, 6, 30, 11, 5, null, null, null, null, null, DateTimeException.class},

                {2008, 6, 30, 11, 5, null, null, null, null,                    null, DateTimeException.class},
                {2008, 6, 30, 11, 5, 30, null, null, null,                      null, DateTimeException.class},
                {2008, 6, 30, 11, 5, 30, 500000000, null, null,                 null, DateTimeException.class},
                {2008, 6, 30, 11, 5, 30, 1, null, null,                         null, DateTimeException.class},

                // allow OffsetDateTime (no harm comes of this AFAICT)
                {2008, 6, 30, 11, 5, null, null, "+01:00", null,                "2008-06-30T11:05+01:00", null},
                {2008, 6, 30, 11, 5, 30, null, "+01:00", null,                  "2008-06-30T11:05:30+01:00", null},
                {2008, 6, 30, 11, 5, 30, 500000000, "+01:00", null,             "2008-06-30T11:05:30.5+01:00", null},
                {2008, 6, 30, 11, 5, 30, 1, "+01:00", null,                     "2008-06-30T11:05:30.000000001+01:00", null},

                // ZonedDateTime with ZoneId of ZoneOffset
                {2008, 6, 30, 11, 5, null, null, "+01:00", "+01:00",            "2008-06-30T11:05+01:00", null},
                {2008, 6, 30, 11, 5, 30, null, "+01:00", "+01:00",              "2008-06-30T11:05:30+01:00", null},
                {2008, 6, 30, 11, 5, 30, 500000000, "+01:00", "+01:00",         "2008-06-30T11:05:30.5+01:00", null},
                {2008, 6, 30, 11, 5, 30, 1, "+01:00", "+01:00",                 "2008-06-30T11:05:30.000000001+01:00", null},

                // ZonedDateTime with ZoneId of ZoneRegion
                {2008, 6, 30, 11, 5, null, null, "+01:00", "Europe/Paris",      "2008-06-30T11:05+01:00[Europe/Paris]", null},
                {2008, 6, 30, 11, 5, 30, null, "+01:00", "Europe/Paris",        "2008-06-30T11:05:30+01:00[Europe/Paris]", null},
                {2008, 6, 30, 11, 5, 30, 500000000, "+01:00", "Europe/Paris",   "2008-06-30T11:05:30.5+01:00[Europe/Paris]", null},
                {2008, 6, 30, 11, 5, 30, 1, "+01:00", "Europe/Paris",           "2008-06-30T11:05:30.000000001+01:00[Europe/Paris]", null},

                // offset required
                {2008, 6, 30, 11, 5, null, null, null, "Europe/Paris",          null, DateTimeException.class},
                {2008, 6, 30, 11, 5, 30, null, null, "Europe/Paris",            null, DateTimeException.class},
                {2008, 6, 30, 11, 5, 30, 500000000, null, "Europe/Paris",       null, DateTimeException.class},
                {2008, 6, 30, 11, 5, 30, 1, null, "Europe/Paris",               null, DateTimeException.class},

                {123456, 6, 30, 11, 5, null, null, "+01:00", "Europe/Paris",    "+123456-06-30T11:05+01:00[Europe/Paris]", null},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoZonedDateTime")
    public void test_print_isoZonedDateTime(
            Integer year, Integer month, Integer day,
            Integer hour, Integer min, Integer sec, Integer nano, String offsetId, String zoneId,
            String expected, Class<?> expectedEx) {
        TemporalAccessor test = buildAccessor(year, month, day, hour, min, sec, nano, offsetId, zoneId);
        if (expectedEx == null) {
            assertEquals(expected, DateTimeFormatter.ISO_ZONED_DATE_TIME.format(test));
        } else {
            try {
                DateTimeFormatter.ISO_ZONED_DATE_TIME.format(test);
                fail(test.toString());
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoZonedDateTime")
    public void test_parse_isoZonedDateTime(
            Integer year, Integer month, Integer day,
            Integer hour, Integer min, Integer sec, Integer nano, String offsetId, String zoneId,
            String input, Class<?> invalid) {
        if (input != null) {
            Expected expected = createDateTime(year, month, day, hour, min, sec, nano);
            if (offsetId.equals(zoneId)) {
                buildCalendrical(expected, offsetId, null);
            } else {
                buildCalendrical(expected, offsetId, zoneId);
            }
            assertParseMatch(DateTimeFormatter.ISO_ZONED_DATE_TIME.parseUnresolved(input, new ParsePosition(0)), expected);
        }
    }

    @Test
    public void test_isoZonedDateTime_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.ISO_ZONED_DATE_TIME.getChronology());
        assertEquals(null, DateTimeFormatter.ISO_ZONED_DATE_TIME.getZone());
        assertEquals(ResolverStyle.STRICT, DateTimeFormatter.ISO_ZONED_DATE_TIME.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] provider_sample_isoDateTime() {
        return new Object[][]{
                {2008, null, null, null, null, null, null, null, null, null, DateTimeException.class},
                {null, 6, null, null, null, null, null, null, null, null, DateTimeException.class},
                {null, null, 30, null, null, null, null, null, null, null, DateTimeException.class},
                {null, null, null, 11, null, null, null, null, null, null, DateTimeException.class},
                {null, null, null, null, 5, null, null, null, null, null, DateTimeException.class},
                {null, null, null, null, null, null, null, "+01:00", null, null, DateTimeException.class},
                {null, null, null, null, null, null, null, null, "Europe/Paris", null, DateTimeException.class},
                {2008, 6, 30, 11, null, null, null, null, null, null, DateTimeException.class},
                {2008, 6, 30, null, 5, null, null, null, null, null, DateTimeException.class},
                {2008, 6, null, 11, 5, null, null, null, null, null, DateTimeException.class},
                {2008, null, 30, 11, 5, null, null, null, null, null, DateTimeException.class},
                {null, 6, 30, 11, 5, null, null, null, null, null, DateTimeException.class},

                {2008, 6, 30, 11, 5, null, null, null, null,                    "2008-06-30T11:05", null},
                {2008, 6, 30, 11, 5, 30, null, null, null,                      "2008-06-30T11:05:30", null},
                {2008, 6, 30, 11, 5, 30, 500000000, null, null,                 "2008-06-30T11:05:30.5", null},
                {2008, 6, 30, 11, 5, 30, 1, null, null,                         "2008-06-30T11:05:30.000000001", null},

                {2008, 6, 30, 11, 5, null, null, "+01:00", null,                "2008-06-30T11:05+01:00", null},
                {2008, 6, 30, 11, 5, 30, null, "+01:00", null,                  "2008-06-30T11:05:30+01:00", null},
                {2008, 6, 30, 11, 5, 30, 500000000, "+01:00", null,             "2008-06-30T11:05:30.5+01:00", null},
                {2008, 6, 30, 11, 5, 30, 1, "+01:00", null,                     "2008-06-30T11:05:30.000000001+01:00", null},

                {2008, 6, 30, 11, 5, null, null, "+01:00", "Europe/Paris",      "2008-06-30T11:05+01:00[Europe/Paris]", null},
                {2008, 6, 30, 11, 5, 30, null, "+01:00", "Europe/Paris",        "2008-06-30T11:05:30+01:00[Europe/Paris]", null},
                {2008, 6, 30, 11, 5, 30, 500000000, "+01:00", "Europe/Paris",   "2008-06-30T11:05:30.5+01:00[Europe/Paris]", null},
                {2008, 6, 30, 11, 5, 30, 1, "+01:00", "Europe/Paris",           "2008-06-30T11:05:30.000000001+01:00[Europe/Paris]", null},

                {2008, 6, 30, 11, 5, null, null, null, "Europe/Paris",          "2008-06-30T11:05", null},
                {2008, 6, 30, 11, 5, 30, null, null, "Europe/Paris",            "2008-06-30T11:05:30", null},
                {2008, 6, 30, 11, 5, 30, 500000000, null, "Europe/Paris",       "2008-06-30T11:05:30.5", null},
                {2008, 6, 30, 11, 5, 30, 1, null, "Europe/Paris",               "2008-06-30T11:05:30.000000001", null},

                {123456, 6, 30, 11, 5, null, null, null, null,                  "+123456-06-30T11:05", null},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoDateTime")
    public void test_print_isoDateTime(
            Integer year, Integer month, Integer day,
            Integer hour, Integer min, Integer sec, Integer nano, String offsetId, String zoneId,
            String expected, Class<?> expectedEx) {
        TemporalAccessor test = buildAccessor(year, month, day, hour, min, sec, nano, offsetId, zoneId);
        if (expectedEx == null) {
            assertEquals(expected, DateTimeFormatter.ISO_DATE_TIME.format(test));
        } else {
            try {
                DateTimeFormatter.ISO_DATE_TIME.format(test);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoDateTime")
    public void test_parse_isoDateTime(
            Integer year, Integer month, Integer day,
            Integer hour, Integer min, Integer sec, Integer nano, String offsetId, String zoneId,
            String input, Class<?> invalid) {
        if (input != null) {
            Expected expected = createDateTime(year, month, day, hour, min, sec, nano);
            if (offsetId != null) {
                expected.add(ZoneOffset.of(offsetId));
                if (zoneId != null) {
                    expected.zone = ZoneId.of(zoneId);
                }
            }
            assertParseMatch(DateTimeFormatter.ISO_DATE_TIME.parseUnresolved(input, new ParsePosition(0)), expected);
        }
    }

    @Test
    public void test_isoDateTime_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.ISO_DATE_TIME.getChronology());
        assertEquals(null, DateTimeFormatter.ISO_DATE_TIME.getZone());
        assertEquals(ResolverStyle.STRICT, DateTimeFormatter.ISO_DATE_TIME.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @Test
    public void test_print_isoOrdinalDate() {
        TemporalAccessor test = buildAccessor(LocalDateTime.of(2008, 6, 3, 11, 5, 30), null, null);
        assertEquals("2008-155", DateTimeFormatter.ISO_ORDINAL_DATE.format(test));
    }

    @Test
    public void test_print_isoOrdinalDate_offset() {
        TemporalAccessor test = buildAccessor(LocalDateTime.of(2008, 6, 3, 11, 5, 30), "Z", null);
        assertEquals("2008-155Z", DateTimeFormatter.ISO_ORDINAL_DATE.format(test));
    }

    @Test
    public void test_print_isoOrdinalDate_zoned() {
        TemporalAccessor test = buildAccessor(LocalDateTime.of(2008, 6, 3, 11, 5, 30), "+02:00", "Europe/Paris");
        assertEquals("2008-155+02:00", DateTimeFormatter.ISO_ORDINAL_DATE.format(test));
    }

    @Test
    public void test_print_isoOrdinalDate_zoned_largeYear() {
        TemporalAccessor test = buildAccessor(LocalDateTime.of(123456, 6, 3, 11, 5, 30), "Z", null);
        assertEquals("+123456-155Z", DateTimeFormatter.ISO_ORDINAL_DATE.format(test));
    }

    @Test
    public void test_print_isoOrdinalDate_fields() {
        // mock for testing that does not fully comply with TemporalAccessor contract
        TemporalAccessor test = new TemporalAccessor() {
            @Override
            public boolean isSupported(TemporalField field) {
                return field == YEAR || field == DAY_OF_YEAR;
            }
            @Override
            public long getLong(TemporalField field) {
                if (field == YEAR) {
                    return 2008;
                }
                if (field == DAY_OF_YEAR) {
                    return 231;
                }
                throw new DateTimeException("Unsupported");
            }
        };
        assertEquals("2008-231", DateTimeFormatter.ISO_ORDINAL_DATE.format(test));
    }

    @Test
    public void test_print_isoOrdinalDate_missingField() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            TemporalAccessor test = Year.of(2008);
            DateTimeFormatter.ISO_ORDINAL_DATE.format(test);
        });
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_parse_isoOrdinalDate() {
        Expected expected = new Expected(YEAR, 2008, DAY_OF_YEAR, 123);
        assertParseMatch(DateTimeFormatter.ISO_ORDINAL_DATE.parseUnresolved("2008-123", new ParsePosition(0)), expected);
    }

    @Test
    public void test_parse_isoOrdinalDate_largeYear() {
        Expected expected = new Expected(YEAR, 123456, DAY_OF_YEAR, 123);
        assertParseMatch(DateTimeFormatter.ISO_ORDINAL_DATE.parseUnresolved("+123456-123", new ParsePosition(0)), expected);
    }

    @Test
    public void test_isoOrdinalDate_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.ISO_ORDINAL_DATE.getChronology());
        assertEquals(null, DateTimeFormatter.ISO_ORDINAL_DATE.getZone());
        assertEquals(ResolverStyle.STRICT, DateTimeFormatter.ISO_ORDINAL_DATE.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @Test
    public void test_print_basicIsoDate() {
        TemporalAccessor test = buildAccessor(LocalDateTime.of(2008, 6, 3, 11, 5, 30), null, null);
        assertEquals("20080603", DateTimeFormatter.BASIC_ISO_DATE.format(test));
    }

    @Test
    public void test_print_basicIsoDate_offset() {
        TemporalAccessor test = buildAccessor(LocalDateTime.of(2008, 6, 3, 11, 5, 30), "Z", null);
        assertEquals("20080603Z", DateTimeFormatter.BASIC_ISO_DATE.format(test));
    }

    @Test
    public void test_print_basicIsoDate_zoned() {
        TemporalAccessor test = buildAccessor(LocalDateTime.of(2008, 6, 3, 11, 5, 30), "+02:00", "Europe/Paris");
        assertEquals("20080603+0200", DateTimeFormatter.BASIC_ISO_DATE.format(test));
    }

    @Test
    public void test_print_basicIsoDate_largeYear() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            TemporalAccessor test = buildAccessor(LocalDateTime.of(123456, 6, 3, 11, 5, 30), "Z", null);
            DateTimeFormatter.BASIC_ISO_DATE.format(test);
        });
    }

    @Test
    public void test_print_basicIsoDate_fields() {
        TemporalAccessor test = buildAccessor(LocalDate.of(2008, 6, 3), null, null);
        assertEquals("20080603", DateTimeFormatter.BASIC_ISO_DATE.format(test));
    }

    @Test
    public void test_print_basicIsoDate_missingField() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            TemporalAccessor test = YearMonth.of(2008, 6);
            DateTimeFormatter.BASIC_ISO_DATE.format(test);
        });
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_parse_basicIsoDate() {
        LocalDate expected = LocalDate.of(2008, 6, 3);
        assertEquals(expected, DateTimeFormatter.BASIC_ISO_DATE.parse("20080603", LocalDate::from));
    }

    @Test
    public void test_parse_basicIsoDate_largeYear() {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            try {
                LocalDate expected = LocalDate.of(123456, 6, 3);
                assertEquals(expected, DateTimeFormatter.BASIC_ISO_DATE.parse("+1234560603", LocalDate::from));
            } catch (DateTimeParseException ex) {
                assertEquals(0, ex.getErrorIndex());
                assertEquals("+1234560603", ex.getParsedString());
                throw ex;
            }
        });
    }

    @Test
    public void test_basicIsoDate_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.BASIC_ISO_DATE.getChronology());
        assertEquals(null, DateTimeFormatter.BASIC_ISO_DATE.getZone());
        assertEquals(ResolverStyle.STRICT, DateTimeFormatter.BASIC_ISO_DATE.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Iterator<Object[]> weekDate() {
        return new Iterator<Object[]>() {
            private ZonedDateTime date = ZonedDateTime.of(LocalDateTime.of(2003, 12, 29, 11, 5, 30), ZoneId.of("Europe/Paris"));
            private ZonedDateTime endDate = date.withYear(2005).withMonth(1).withDayOfMonth(2);
            private int week = 1;
            private int day = 1;

            public boolean hasNext() {
                return !date.isAfter(endDate);
            }
            public Object[] next() {
                StringBuilder sb = new StringBuilder("2004-W");
                if (week < 10) {
                    sb.append('0');
                }
                sb.append(week).append('-').append(day).append(date.getOffset());
                Object[] ret = new Object[] {date, sb.toString()};
                date = date.plusDays(1);
                day += 1;
                if (day == 8) {
                    day = 1;
                    week++;
                }
                return ret;
            }
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @ParameterizedTest
    @MethodSource("weekDate")
    public void test_print_isoWeekDate(TemporalAccessor test, String expected) {
        assertEquals(expected, DateTimeFormatter.ISO_WEEK_DATE.format(test));
    }

    @Test
    public void test_print_isoWeekDate_zoned_largeYear() {
        TemporalAccessor test = buildAccessor(LocalDateTime.of(123456, 6, 3, 11, 5, 30), "Z", null);
        assertEquals("+123456-W23-2Z", DateTimeFormatter.ISO_WEEK_DATE.format(test));
    }

    @Test
    public void test_print_isoWeekDate_fields() {
        TemporalAccessor test = buildAccessor(LocalDate.of(2004, 1, 27), null, null);
        assertEquals("2004-W05-2", DateTimeFormatter.ISO_WEEK_DATE.format(test));
    }

    @Test
    public void test_print_isoWeekDate_missingField() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            TemporalAccessor test = YearMonth.of(2008, 6);
            DateTimeFormatter.ISO_WEEK_DATE.format(test);
        });
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_parse_weekDate() {
        LocalDate expected = LocalDate.of(2004, 1, 28);
        assertEquals(expected, DateTimeFormatter.ISO_WEEK_DATE.parse("2004-W05-3", LocalDate::from));
    }

    @Test
    public void test_parse_weekDate_largeYear() {
        TemporalAccessor parsed = DateTimeFormatter.ISO_WEEK_DATE.parseUnresolved("+123456-W04-5", new ParsePosition(0));
        assertEquals(123456L, parsed.getLong(IsoFields.WEEK_BASED_YEAR));
        assertEquals(4L, parsed.getLong(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(5L, parsed.getLong(DAY_OF_WEEK));
    }

    @Test
    public void test_isoWeekDate_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.ISO_WEEK_DATE.getChronology());
        assertEquals(null, DateTimeFormatter.ISO_WEEK_DATE.getZone());
        assertEquals(ResolverStyle.STRICT, DateTimeFormatter.ISO_WEEK_DATE.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] provider_sample_isoInstant() {
        return new Object[][]{
                {0, 0, "1970-01-01T00:00:00Z", null},
                {0, null, "1970-01-01T00:00:00Z", null},
                {0, -1, null, DateTimeException.class},

                {-1, 0, "1969-12-31T23:59:59Z", null},
                {1, 0, "1970-01-01T00:00:01Z", null},
                {60, 0, "1970-01-01T00:01:00Z", null},
                {3600, 0, "1970-01-01T01:00:00Z", null},
                {86400, 0, "1970-01-02T00:00:00Z", null},

                {0, 1, "1970-01-01T00:00:00.000000001Z", null},
                {0, 2, "1970-01-01T00:00:00.000000002Z", null},
                {0, 10, "1970-01-01T00:00:00.000000010Z", null},
                {0, 100, "1970-01-01T00:00:00.000000100Z", null},
        };
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoInstant")
    public void test_print_isoInstant(
            long instantSecs, Integer nano, String expected, Class<?> expectedEx) {
        TemporalAccessor test = buildAccessorInstant(instantSecs, nano);
        if (expectedEx == null) {
            assertEquals(expected, DateTimeFormatter.ISO_INSTANT.format(test));
        } else {
            try {
                DateTimeFormatter.ISO_INSTANT.format(test);
                fail();
            } catch (Exception ex) {
                assertTrue(expectedEx.isInstance(ex));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provider_sample_isoInstant")
    public void test_parse_isoInstant(
            long instantSecs, Integer nano, String input, Class<?> invalid) {
        if (input != null) {
            TemporalAccessor parsed = DateTimeFormatter.ISO_INSTANT.parseUnresolved(input, new ParsePosition(0));
            assertEquals(instantSecs, parsed.getLong(INSTANT_SECONDS));
            assertEquals((nano == null ? 0 : nano), parsed.getLong(NANO_OF_SECOND));
        }
    }

    @Test
    public void test_isoInstant_basics() {
        assertEquals(null, DateTimeFormatter.ISO_INSTANT.getChronology());
        assertEquals(null, DateTimeFormatter.ISO_INSTANT.getZone());
        assertEquals(ResolverStyle.STRICT, DateTimeFormatter.ISO_INSTANT.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    Object[][] data_rfc() {
        return new Object[][] {
            {LocalDateTime.of(2008, 6, 3, 11, 5, 30), "Z", "Tue, 3 Jun 2008 11:05:30 GMT"},
            {LocalDateTime.of(2008, 6, 30, 11, 5, 30), "Z", "Mon, 30 Jun 2008 11:05:30 GMT"},
            {LocalDateTime.of(2008, 6, 3, 11, 5, 30), "+02:00", "Tue, 3 Jun 2008 11:05:30 +0200"},
            {LocalDateTime.of(2008, 6, 30, 11, 5, 30), "-03:00", "Mon, 30 Jun 2008 11:05:30 -0300"},
        };
    }

    @ParameterizedTest
    @MethodSource("data_rfc")
    public void test_print_rfc1123(LocalDateTime base, String offsetId, String expected) {
        TemporalAccessor test = buildAccessor(base, offsetId, null);
        assertEquals(expected, DateTimeFormatter.RFC_1123_DATE_TIME.format(test));
    }

    @ParameterizedTest
    @MethodSource("data_rfc")
    public void test_print_rfc1123_french(LocalDateTime base, String offsetId, String expected) {
        TemporalAccessor test = buildAccessor(base, offsetId, null);
        assertEquals(expected, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.FRENCH).format(test));
    }

    @Test
    public void test_print_rfc1123_missingField() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            TemporalAccessor test = YearMonth.of(2008, 6);
            DateTimeFormatter.RFC_1123_DATE_TIME.format(test);
        });
    }

    @Test
    public void test_rfc1123_basics() {
        assertEquals(IsoChronology.INSTANCE, DateTimeFormatter.RFC_1123_DATE_TIME.getChronology());
        assertEquals(null, DateTimeFormatter.RFC_1123_DATE_TIME.getZone());
        assertEquals(ResolverStyle.SMART, DateTimeFormatter.RFC_1123_DATE_TIME.getResolverStyle());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    private Expected createDate(Integer year, Integer month, Integer day) {
        Expected test = new Expected();
        if (year != null) {
            test.fieldValues.put(YEAR, (long) year);
        }
        if (month != null) {
            test.fieldValues.put(MONTH_OF_YEAR, (long) month);
        }
        if (day != null) {
            test.fieldValues.put(DAY_OF_MONTH, (long) day);
        }
        return test;
    }

    private Expected createTime(Integer hour, Integer min, Integer sec, Integer nano) {
        Expected test = new Expected();
        if (hour != null) {
            test.fieldValues.put(HOUR_OF_DAY, (long) hour);
        }
        if (min != null) {
            test.fieldValues.put(MINUTE_OF_HOUR, (long) min);
        }
        if (sec != null) {
            test.fieldValues.put(SECOND_OF_MINUTE, (long) sec);
        }
        if (nano != null) {
            test.fieldValues.put(NANO_OF_SECOND, (long) nano);
        }
        return test;
    }

    private Expected createDateTime(
            Integer year, Integer month, Integer day,
            Integer hour, Integer min, Integer sec, Integer nano) {
        Expected test = new Expected();
        if (year != null) {
            test.fieldValues.put(YEAR, (long) year);
        }
        if (month != null) {
            test.fieldValues.put(MONTH_OF_YEAR, (long) month);
        }
        if (day != null) {
            test.fieldValues.put(DAY_OF_MONTH, (long) day);
        }
        if (hour != null) {
            test.fieldValues.put(HOUR_OF_DAY, (long) hour);
        }
        if (min != null) {
            test.fieldValues.put(MINUTE_OF_HOUR, (long) min);
        }
        if (sec != null) {
            test.fieldValues.put(SECOND_OF_MINUTE, (long) sec);
        }
        if (nano != null) {
            test.fieldValues.put(NANO_OF_SECOND, (long) nano);
        }
        return test;
    }

    private TemporalAccessor buildAccessor(
                    Integer year, Integer month, Integer day,
                    Integer hour, Integer min, Integer sec, Integer nano,
                    String offsetId, String zoneId) {
        MockAccessor mock = new MockAccessor();
        if (year != null) {
            mock.fields.put(YEAR, (long) year);
        }
        if (month != null) {
            mock.fields.put(MONTH_OF_YEAR, (long) month);
        }
        if (day != null) {
            mock.fields.put(DAY_OF_MONTH, (long) day);
        }
        if (hour != null) {
            mock.fields.put(HOUR_OF_DAY, (long) hour);
        }
        if (min != null) {
            mock.fields.put(MINUTE_OF_HOUR, (long) min);
        }
        if (sec != null) {
            mock.fields.put(SECOND_OF_MINUTE, (long) sec);
        }
        if (nano != null) {
            mock.fields.put(NANO_OF_SECOND, (long) nano);
        }
        mock.setOffset(offsetId);
        mock.setZone(zoneId);
        return mock;
    }

    private TemporalAccessor buildAccessor(LocalDateTime base, String offsetId, String zoneId) {
        MockAccessor mock = new MockAccessor();
        mock.setFields(base);
        mock.setOffset(offsetId);
        mock.setZone(zoneId);
        return mock;
    }

    private TemporalAccessor buildAccessor(LocalDate base, String offsetId, String zoneId) {
        MockAccessor mock = new MockAccessor();
        mock.setFields(base);
        mock.setOffset(offsetId);
        mock.setZone(zoneId);
        return mock;
    }

    private TemporalAccessor buildAccessorInstant(long instantSecs, Integer nano) {
        MockAccessor mock = new MockAccessor();
        mock.fields.put(INSTANT_SECONDS, instantSecs);
        if (nano != null) {
            mock.fields.put(NANO_OF_SECOND, (long) nano);
        }
        return mock;
    }

    private void buildCalendrical(Expected expected, String offsetId, String zoneId) {
        if (offsetId != null) {
            expected.add(ZoneOffset.of(offsetId));
        }
        if (zoneId != null) {
            expected.zone = ZoneId.of(zoneId);
        }
    }

    private void assertParseMatch(TemporalAccessor parsed, Expected expected) {
        for (TemporalField field : expected.fieldValues.keySet()) {
            assertEquals(true, parsed.isSupported(field));
            parsed.getLong(field);
        }
        assertEquals(expected.chrono, parsed.query(TemporalQueries.chronology()));
        assertEquals(expected.zone, parsed.query(TemporalQueries.zoneId()));
    }

    //-------------------------------------------------------------------------
    static class MockAccessor implements TemporalAccessor {
        Map<TemporalField, Long> fields = new HashMap<>();
        ZoneId zoneId;

        void setFields(LocalDate dt) {
            if (dt != null) {
                fields.put(YEAR, (long) dt.getYear());
                fields.put(MONTH_OF_YEAR, (long) dt.getMonthValue());
                fields.put(DAY_OF_MONTH, (long) dt.getDayOfMonth());
                fields.put(DAY_OF_YEAR, (long) dt.getDayOfYear());
                fields.put(DAY_OF_WEEK, (long) dt.getDayOfWeek().getValue());
                fields.put(IsoFields.WEEK_BASED_YEAR, dt.getLong(IsoFields.WEEK_BASED_YEAR));
                fields.put(IsoFields.WEEK_OF_WEEK_BASED_YEAR, dt.getLong(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            }
        }

        void setFields(LocalDateTime dt) {
            if (dt != null) {
                fields.put(YEAR, (long) dt.getYear());
                fields.put(MONTH_OF_YEAR, (long) dt.getMonthValue());
                fields.put(DAY_OF_MONTH, (long) dt.getDayOfMonth());
                fields.put(DAY_OF_YEAR, (long) dt.getDayOfYear());
                fields.put(DAY_OF_WEEK, (long) dt.getDayOfWeek().getValue());
                fields.put(IsoFields.WEEK_BASED_YEAR, dt.getLong(IsoFields.WEEK_BASED_YEAR));
                fields.put(IsoFields.WEEK_OF_WEEK_BASED_YEAR, dt.getLong(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
                fields.put(HOUR_OF_DAY, (long) dt.getHour());
                fields.put(MINUTE_OF_HOUR, (long) dt.getMinute());
                fields.put(SECOND_OF_MINUTE, (long) dt.getSecond());
                fields.put(NANO_OF_SECOND, (long) dt.getNano());
            }
        }

        void setOffset(String offsetId) {
            if (offsetId != null) {
                this.fields.put(OFFSET_SECONDS, (long) ZoneOffset.of(offsetId).getTotalSeconds());
            }
        }

        void setZone(String zoneId) {
            if (zoneId != null) {
                this.zoneId = ZoneId.of(zoneId);
            }
        }

        @Override
        public boolean isSupported(TemporalField field) {
            return fields.containsKey(field);
        }

        @Override
        public long getLong(TemporalField field) {
            try {
                return fields.get(field);
            } catch (NullPointerException ex) {
                throw new DateTimeException("Field missing: " + field);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public <R> R query(TemporalQuery<R> query) {
            if (query == TemporalQueries.zoneId()) {
                return (R) zoneId;
            }
            return TemporalAccessor.super.query(query);
        }

        @Override
        public String toString() {
            return fields + (zoneId != null ? " " + zoneId : "");
        }
    }

    //-----------------------------------------------------------------------
    static class Expected {
        Map<TemporalField, Long> fieldValues = new HashMap<>();
        ZoneId zone;
        Chronology chrono;

        Expected() {
        }

        Expected(TemporalField field1, long value1, TemporalField field2, long value2) {
            fieldValues.put(field1, value1);
            fieldValues.put(field2, value2);
        }

        void add(ZoneOffset offset) {
            fieldValues.put(OFFSET_SECONDS, (long) offset.getTotalSeconds());
        }
    }
}
