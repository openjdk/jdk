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
package tck.java.time.format;

import static org.testng.Assert.assertEquals;

import java.text.ParsePosition;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.Locale;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test DateTimeFormatterBuilder.appendZoneId().
 */
@Test
public class TCKZoneIdPrinterParser {

    private static final ZoneOffset OFFSET_UTC = ZoneOffset.UTC;
    private static final ZoneOffset OFFSET_P0123 = ZoneOffset.ofHoursMinutes(1, 23);
    private static final ZoneId EUROPE_PARIS = ZoneId.of("Europe/Paris");
    private static final ZoneId AMERICA_NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalDateTime DT_2012_06_30_12_30_40 = LocalDateTime.of(2012, 6, 30, 12, 30, 40);

    private DateTimeFormatterBuilder builder;
    private ParsePosition pos;

    @BeforeMethod
    public void setUp() {
        builder = new DateTimeFormatterBuilder();
        pos = new ParsePosition(0);
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="print")
    Object[][] data_print() {
        return new Object[][] {
                {DT_2012_06_30_12_30_40, EUROPE_PARIS, "Europe/Paris"},
                {DT_2012_06_30_12_30_40, AMERICA_NEW_YORK, "America/New_York"},
                {DT_2012_06_30_12_30_40, OFFSET_UTC, "Z"},
                {DT_2012_06_30_12_30_40, OFFSET_P0123, "+01:23"},
        };
    }

    @Test(dataProvider="print")
    public void test_print(LocalDateTime ldt, ZoneId zone, String expected) {
        ZonedDateTime zdt = ldt.atZone(zone);
        builder.appendZoneId();
        String output = builder.toFormatter().format(zdt);
        assertEquals(output, expected);
    }

    @Test(dataProvider="print")
    public void test_print_pattern_VV(LocalDateTime ldt, ZoneId zone, String expected) {
        ZonedDateTime zdt = ldt.atZone(zone);
        builder.appendPattern("VV");
        String output = builder.toFormatter().format(zdt);
        assertEquals(output, expected);
    }

    //-----------------------------------------------------------------------
    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_print_pattern_V1rejected() {
        builder.appendPattern("V");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_print_pattern_V3rejected() {
        builder.appendPattern("VVV");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_print_pattern_V4rejected() {
        builder.appendPattern("VVVV");
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void test_print_pattern_V5rejected() {
        builder.appendPattern("VVVVV");
    }

    //-----------------------------------------------------------------------
    @DataProvider(name="parseSuccess")
    Object[][] data_parseSuccess() {
        return new Object[][] {
                {"Z", 1, -1, ZoneOffset.UTC},
                {"UTC", 3, -1, ZoneOffset.UTC},
                {"UT", 2, -1, ZoneOffset.UTC},
                {"GMT", 3, -1, ZoneOffset.UTC},
                {"UTC0", 4, -1, ZoneOffset.UTC},
                {"UT0", 3, -1, ZoneOffset.UTC},
                {"GMT0", 4, -1, ZoneOffset.UTC},

                {"+00:00", 6, -1, ZoneOffset.UTC},
                {"UTC+00:00", 9, -1, ZoneOffset.UTC},
                {"UT+00:00", 8, -1, ZoneOffset.UTC},
                {"GMT+00:00", 9, -1, ZoneOffset.UTC},
                {"-00:00", 6, -1, ZoneOffset.UTC},
                {"UTC-00:00", 9, -1, ZoneOffset.UTC},
                {"UT-00:00", 8, -1, ZoneOffset.UTC},
                {"GMT-00:00", 9, -1, ZoneOffset.UTC},

                {"+01:30", 6, -1, ZoneOffset.ofHoursMinutes(1, 30)},
                {"UTC+01:30", 9, -1, ZoneOffset.ofHoursMinutes(1, 30)},
                {"UT+02:30", 8, -1, ZoneOffset.ofHoursMinutes(2, 30)},
                {"GMT+03:30", 9, -1, ZoneOffset.ofHoursMinutes(3, 30)},
                {"-01:30", 6, -1, ZoneOffset.ofHoursMinutes(-1, -30)},
                {"UTC-01:30", 9, -1, ZoneOffset.ofHoursMinutes(-1, -30)},
                {"UT-02:30", 8, -1, ZoneOffset.ofHoursMinutes(-2, -30)},
                {"GMT-03:30", 9, -1, ZoneOffset.ofHoursMinutes(-3, -30)},

                // fallback to UTC
                {"UTC-01:WW", 3, -1, ZoneOffset.UTC},
                {"UT-02:WW", 2, -1, ZoneOffset.UTC},
                {"GMT-03:WW", 3, -1, ZoneOffset.UTC},
                {"Z0", 1, -1, ZoneOffset.UTC},
                {"UTC1", 3, -1, ZoneOffset.UTC},

                // Z not parsed as zero
                {"UTCZ", 3, -1, ZoneOffset.UTC},
                {"UTZ", 2, -1, ZoneOffset.UTC},
                {"GMTZ", 3, -1, ZoneOffset.UTC},

                // fail to parse
                {"", 0, 0, null},
                {"A", 0, 0, null},
                {"UZ", 0, 0, null},
                {"GMA", 0, 0, null},
                {"0", 0, 0, null},
                {"+", 0, 0, null},
                {"-", 0, 0, null},

                // zone IDs
                {"Europe/London", 13, -1, ZoneId.of("Europe/London")},
                {"America/New_York", 16, -1, ZoneId.of("America/New_York")},
                {"America/Bogusville", 0, 0, null},
        };
    }

    @Test(dataProvider="parseSuccess")
    public void test_parseSuccess_plain(String text, int expectedIndex, int expectedErrorIndex, ZoneId expected) {
        builder.appendZoneId();
        TemporalAccessor parsed = builder.toFormatter().parseUnresolved(text, pos);
        assertEquals(pos.getErrorIndex(), expectedErrorIndex);
        assertEquals(pos.getIndex(), expectedIndex);
        if (expected != null) {
            assertEquals(parsed.query(TemporalQuery.zoneId()), expected);
            assertEquals(parsed.query(TemporalQuery.offset()), null);
            assertEquals(parsed.query(TemporalQuery.zone()), expected);
        } else {
            assertEquals(parsed, null);
        }
    }

    @Test(dataProvider="parseSuccess")
    public void test_parseSuccess_prefix(String text, int expectedIndex, int expectedErrorIndex, ZoneId expected) {
        builder.appendZoneId();
        pos.setIndex(3);
        TemporalAccessor parsed = builder.toFormatter().parseUnresolved("XXX" + text, pos);
        assertEquals(pos.getErrorIndex(), expectedErrorIndex >= 0  ? expectedErrorIndex + 3 : expectedErrorIndex);
        assertEquals(pos.getIndex(), expectedIndex + 3);
        if (expected != null) {
            assertEquals(parsed.query(TemporalQuery.zoneId()), expected);
            assertEquals(parsed.query(TemporalQuery.offset()), null);
            assertEquals(parsed.query(TemporalQuery.zone()), expected);
        } else {
            assertEquals(parsed, null);
        }
    }

    @Test(dataProvider="parseSuccess")
    public void test_parseSuccess_suffix(String text, int expectedIndex, int expectedErrorIndex, ZoneId expected) {
        builder.appendZoneId();
        TemporalAccessor parsed = builder.toFormatter().parseUnresolved(text + "XXX", pos);
        assertEquals(pos.getErrorIndex(), expectedErrorIndex);
        assertEquals(pos.getIndex(), expectedIndex);
        if (expected != null) {
            assertEquals(parsed.query(TemporalQuery.zoneId()), expected);
            assertEquals(parsed.query(TemporalQuery.offset()), null);
            assertEquals(parsed.query(TemporalQuery.zone()), expected);
        } else {
            assertEquals(parsed, null);
        }
    }

    @Test(dataProvider="parseSuccess")
    public void test_parseSuccess_caseSensitive(String text, int expectedIndex, int expectedErrorIndex, ZoneId expected) {
        builder.parseCaseSensitive().appendZoneId();
        TemporalAccessor parsed = builder.toFormatter().parseUnresolved(text.toLowerCase(Locale.ENGLISH), pos);
        if (text.matches("[^A-Z]*[A-Z].*")) {  // if input has letters
            assertEquals(pos.getErrorIndex() >= 0, true);
            assertEquals(pos.getIndex(), 0);
            assertEquals(parsed, null);
        } else {
            // case sensitive made no difference
            assertEquals(pos.getIndex(), expectedIndex);
            assertEquals(pos.getErrorIndex(), expectedErrorIndex);
            if (expected != null) {
                assertEquals(parsed.query(TemporalQuery.zoneId()), expected);
                assertEquals(parsed.query(TemporalQuery.offset()), null);
                assertEquals(parsed.query(TemporalQuery.zone()), expected);
            } else {
                assertEquals(parsed, null);
            }
        }
    }

    @Test(dataProvider="parseSuccess")
    public void test_parseSuccess_caseInsensitive(String text, int expectedIndex, int expectedErrorIndex, ZoneId expected) {
        builder.parseCaseInsensitive().appendZoneId();
        TemporalAccessor parsed = builder.toFormatter().parseUnresolved(text.toLowerCase(Locale.ENGLISH), pos);
        assertEquals(pos.getErrorIndex(), expectedErrorIndex);
        assertEquals(pos.getIndex(), expectedIndex);
        if (expected != null) {
            assertEquals(parsed.query(TemporalQuery.zoneId()), expected);
            assertEquals(parsed.query(TemporalQuery.offset()), null);
            assertEquals(parsed.query(TemporalQuery.zone()), expected);
        } else {
            assertEquals(parsed, null);
        }
    }

}
