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
package tck.java.time;

import static java.time.temporal.ChronoField.OFFSET_SECONDS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.JulianFields;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQueries;
import java.time.temporal.TemporalQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test ZoneOffset.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TCKZoneOffset extends AbstractDateTimeTest {

    //-----------------------------------------------------------------------
    @Override
    protected List<TemporalAccessor> samples() {
        TemporalAccessor[] array = {ZoneOffset.ofHours(1), ZoneOffset.ofHoursMinutesSeconds(-5, -6, -30) };
        return Arrays.asList(array);
    }

    @Override
    protected List<TemporalField> validFields() {
        TemporalField[] array = {
            OFFSET_SECONDS,
        };
        return Arrays.asList(array);
    }

    @Override
    protected List<TemporalField> invalidFields() {
        List<TemporalField> list = new ArrayList<>(Arrays.<TemporalField>asList(ChronoField.values()));
        list.removeAll(validFields());
        list.add(JulianFields.JULIAN_DAY);
        list.add(JulianFields.MODIFIED_JULIAN_DAY);
        list.add(JulianFields.RATA_DIE);
        return list;
    }

    //-----------------------------------------------------------------------
    // constants
    //-----------------------------------------------------------------------
    @Test
    public void test_constant_UTC() {
        ZoneOffset test = ZoneOffset.UTC;
        doTestOffset(test, 0, 0, 0);
    }

    @Test
    public void test_constant_MIN() {
        ZoneOffset test = ZoneOffset.MIN;
        doTestOffset(test, -18, 0, 0);
    }

    @Test
    public void test_constant_MAX() {
        ZoneOffset test = ZoneOffset.MAX;
        doTestOffset(test, 18, 0, 0);
    }

    //-----------------------------------------------------------------------
    // of(String)
    //-----------------------------------------------------------------------
    @Test
    public void test_factory_string_UTC() {
        String[] values = new String[] {
            "Z", "+0",
            "+00","+0000","+00:00","+000000","+00:00:00",
            "-00","-0000","-00:00","-000000","-00:00:00",
        };
        for (int i = 0; i < values.length; i++) {
            ZoneOffset test = ZoneOffset.of(values[i]);
            assertSame(test, ZoneOffset.UTC);
        }
    }

    @Test
    public void test_factory_string_invalid() {
        String[] values = new String[] {
            "","A","B","C","D","E","F","G","H","I","J","K","L","M",
            "N","O","P","Q","R","S","T","U","V","W","X","Y","ZZ",
            "0", "+0:00","+00:0","+0:0",
            "+000","+00000",
            "+0:00:00","+00:0:00","+00:00:0","+0:0:0","+0:0:00","+00:0:0","+0:00:0",
            "1", "+01_00","+01;00","+01@00","+01:AA",
            "+19","+19:00","+18:01","+18:00:01","+1801","+180001",
            "-0:00","-00:0","-0:0",
            "-000","-00000",
            "-0:00:00","-00:0:00","-00:00:0","-0:0:0","-0:0:00","-00:0:0","-0:00:0",
            "-19","-19:00","-18:01","-18:00:01","-1801","-180001",
            "-01_00","-01;00","-01@00","-01:AA",
            "@01:00",
        };
        for (int i = 0; i < values.length; i++) {
            try {
                ZoneOffset.of(values[i]);
                fail("Should have failed:" + values[i]);
            } catch (DateTimeException ex) {
                // expected
            }
        }
    }

    @Test
    public void test_factory_string_null() {
        Assertions.assertThrows(NullPointerException.class, () -> ZoneOffset.of((String) null));
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_factory_string_singleDigitHours() {
        for (int i = -9; i <= 9; i++) {
            String str = (i < 0 ? "-" : "+") + Math.abs(i);
            ZoneOffset test = ZoneOffset.of(str);
            doTestOffset(test, i, 0, 0);
        }
    }

    @Test
    public void test_factory_string_hours() {
        for (int i = -18; i <= 18; i++) {
            String str = (i < 0 ? "-" : "+") + Integer.toString(Math.abs(i) + 100).substring(1);
            ZoneOffset test = ZoneOffset.of(str);
            doTestOffset(test, i, 0, 0);
        }
    }

    @Test
    public void test_factory_string_hours_minutes_noColon() {
        for (int i = -17; i <= 17; i++) {
            for (int j = -59; j <= 59; j++) {
                if ((i < 0 && j <= 0) || (i > 0 && j >= 0) || i == 0) {
                    String str = (i < 0 || j < 0 ? "-" : "+") +
                        Integer.toString(Math.abs(i) + 100).substring(1) +
                        Integer.toString(Math.abs(j) + 100).substring(1);
                    ZoneOffset test = ZoneOffset.of(str);
                    doTestOffset(test, i, j, 0);
                }
            }
        }
        ZoneOffset test1 = ZoneOffset.of("-1800");
        doTestOffset(test1, -18, 0, 0);
        ZoneOffset test2 = ZoneOffset.of("+1800");
        doTestOffset(test2, 18, 0, 0);
    }

    @Test
    public void test_factory_string_hours_minutes_colon() {
        for (int i = -17; i <= 17; i++) {
            for (int j = -59; j <= 59; j++) {
                if ((i < 0 && j <= 0) || (i > 0 && j >= 0) || i == 0) {
                    String str = (i < 0 || j < 0 ? "-" : "+") +
                        Integer.toString(Math.abs(i) + 100).substring(1) + ":" +
                        Integer.toString(Math.abs(j) + 100).substring(1);
                    ZoneOffset test = ZoneOffset.of(str);
                    doTestOffset(test, i, j, 0);
                }
            }
        }
        ZoneOffset test1 = ZoneOffset.of("-18:00");
        doTestOffset(test1, -18, 0, 0);
        ZoneOffset test2 = ZoneOffset.of("+18:00");
        doTestOffset(test2, 18, 0, 0);
    }

    @Test
    public void test_factory_string_hours_minutes_seconds_noColon() {
        for (int i = -17; i <= 17; i++) {
            for (int j = -59; j <= 59; j++) {
                for (int k = -59; k <= 59; k++) {
                    if ((i < 0 && j <= 0 && k <= 0) || (i > 0 && j >= 0 && k >= 0) ||
                            (i == 0 && ((j < 0 && k <= 0) || (j > 0 && k >= 0) || j == 0))) {
                        String str = (i < 0 || j < 0 || k < 0 ? "-" : "+") +
                            Integer.toString(Math.abs(i) + 100).substring(1) +
                            Integer.toString(Math.abs(j) + 100).substring(1) +
                            Integer.toString(Math.abs(k) + 100).substring(1);
                        ZoneOffset test = ZoneOffset.of(str);
                        doTestOffset(test, i, j, k);
                    }
                }
            }
        }
        ZoneOffset test1 = ZoneOffset.of("-180000");
        doTestOffset(test1, -18, 0, 0);
        ZoneOffset test2 = ZoneOffset.of("+180000");
        doTestOffset(test2, 18, 0, 0);
    }

    @Test
    public void test_factory_string_hours_minutes_seconds_colon() {
        for (int i = -17; i <= 17; i++) {
            for (int j = -59; j <= 59; j++) {
                for (int k = -59; k <= 59; k++) {
                    if ((i < 0 && j <= 0 && k <= 0) || (i > 0 && j >= 0 && k >= 0) ||
                            (i == 0 && ((j < 0 && k <= 0) || (j > 0 && k >= 0) || j == 0))) {
                        String str = (i < 0 || j < 0 || k < 0 ? "-" : "+") +
                            Integer.toString(Math.abs(i) + 100).substring(1) + ":" +
                            Integer.toString(Math.abs(j) + 100).substring(1) + ":" +
                            Integer.toString(Math.abs(k) + 100).substring(1);
                        ZoneOffset test = ZoneOffset.of(str);
                        doTestOffset(test, i, j, k);
                    }
                }
            }
        }
        ZoneOffset test1 = ZoneOffset.of("-18:00:00");
        doTestOffset(test1, -18, 0, 0);
        ZoneOffset test2 = ZoneOffset.of("+18:00:00");
        doTestOffset(test2, 18, 0, 0);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_factory_int_hours() {
        for (int i = -18; i <= 18; i++) {
            ZoneOffset test = ZoneOffset.ofHours(i);
            doTestOffset(test, i, 0, 0);
        }
    }

    @Test
    public void test_factory_int_hours_tooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHours(19));
    }

    @Test
    public void test_factory_int_hours_tooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHours(-19));
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_factory_int_hours_minutes() {
        for (int i = -17; i <= 17; i++) {
            for (int j = -59; j <= 59; j++) {
                if ((i < 0 && j <= 0) || (i > 0 && j >= 0) || i == 0) {
                    ZoneOffset test = ZoneOffset.ofHoursMinutes(i, j);
                    doTestOffset(test, i, j, 0);
                }
            }
        }
        ZoneOffset test1 = ZoneOffset.ofHoursMinutes(-18, 0);
        doTestOffset(test1, -18, 0, 0);
        ZoneOffset test2 = ZoneOffset.ofHoursMinutes(18, 0);
        doTestOffset(test2, 18, 0, 0);
    }

    @Test
    public void test_factory_int_hours_minutes_tooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutes(19, 0));
    }

    @Test
    public void test_factory_int_hours_minutes_tooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutes(-19, 0));
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_factory_int_hours_minutes_seconds() {
        for (int i = -17; i <= 17; i++) {
            for (int j = -59; j <= 59; j++) {
                for (int k = -59; k <= 59; k++) {
                    if ((i < 0 && j <= 0 && k <= 0) || (i > 0 && j >= 0 && k >= 0) ||
                            (i == 0 && ((j < 0 && k <= 0) || (j > 0 && k >= 0) || j == 0))) {
                        ZoneOffset test = ZoneOffset.ofHoursMinutesSeconds(i, j, k);
                        doTestOffset(test, i, j, k);
                    }
                }
            }
        }
        ZoneOffset test1 = ZoneOffset.ofHoursMinutesSeconds(-18, 0, 0);
        doTestOffset(test1, -18, 0, 0);
        ZoneOffset test2 = ZoneOffset.ofHoursMinutesSeconds(18, 0, 0);
        doTestOffset(test2, 18, 0, 0);
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_plusHoursMinusMinutes() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(1, -1, 0));
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_plusHoursMinusSeconds() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(1, 0, -1));
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_minusHoursPlusMinutes() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(-1, 1, 0));
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_minusHoursPlusSeconds() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(-1, 0, 1));
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_zeroHoursMinusMinutesPlusSeconds() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(0, -1, 1));
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_zeroHoursPlusMinutesMinusSeconds() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(0, 1, -1));
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_minutesTooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(0, 60, 0));
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_minutesTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(0, -60, 0));
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_secondsTooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(0, 0, 60));
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_secondsTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(0, 0, 60));
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_hoursTooBig() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(19, 0, 0));
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_hoursTooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(-19, 0, 0));
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_minutesMinValue() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(0, Integer.MIN_VALUE, -1));
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_secondsMinValue() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(0, 0, Integer.MIN_VALUE));
    }

    @Test
    public void test_factory_int_hours_minutes_seconds_minutesAndSecondsMinValue() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofHoursMinutesSeconds(0, Integer.MIN_VALUE, Integer.MIN_VALUE));
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_factory_ofTotalSeconds() {
        assertEquals(ZoneOffset.ofHoursMinutesSeconds(1, 0, 1), ZoneOffset.ofTotalSeconds(60 * 60 + 1));
        assertEquals(ZoneOffset.ofHours(18), ZoneOffset.ofTotalSeconds(18 * 60 * 60));
        assertEquals(ZoneOffset.ofHours(-18), ZoneOffset.ofTotalSeconds(-18 * 60 * 60));
    }

    @Test
    public void test_factory_ofTotalSeconds_tooLarge() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofTotalSeconds(18 * 60 * 60 + 1));
    }

    @Test
    public void test_factory_ofTotalSeconds_tooSmall() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofTotalSeconds(-18 * 60 * 60 - 1));
    }

    @Test
    public void test_factory_ofTotalSeconds_minValue() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.ofTotalSeconds(Integer.MIN_VALUE));
    }

    //-----------------------------------------------------------------------
    // from()
    //-----------------------------------------------------------------------
    @Test
    public void test_factory_CalendricalObject() {
        assertEquals(ZoneOffset.ofHours(2), ZoneOffset.from(ZonedDateTime.of(LocalDateTime.of(LocalDate.of(2007, 7, 15),
                LocalTime.of(17, 30)), ZoneOffset.ofHours(2))));
    }

    @Test
    public void test_factory_CalendricalObject_invalid_noDerive() {
        Assertions.assertThrows(DateTimeException.class, () -> ZoneOffset.from(LocalTime.of(12, 30)));
    }

    @Test
    public void test_factory_CalendricalObject_null() {
        Assertions.assertThrows(NullPointerException.class, () -> ZoneOffset.from((TemporalAccessor) null));
    }

    //-----------------------------------------------------------------------
    // getTotalSeconds()
    //-----------------------------------------------------------------------
    @Test
    public void test_getTotalSeconds() {
        ZoneOffset offset = ZoneOffset.ofTotalSeconds(60 * 60 + 1);
        assertEquals(60 * 60 + 1, offset.getTotalSeconds());
    }

    //-----------------------------------------------------------------------
    // getId()
    //-----------------------------------------------------------------------
    @Test
    public void test_getId() {
        ZoneOffset offset = ZoneOffset.ofHoursMinutesSeconds(1, 0, 0);
        assertEquals("+01:00", offset.getId());
        offset = ZoneOffset.ofHoursMinutesSeconds(1, 2, 3);
        assertEquals("+01:02:03", offset.getId());
        offset = ZoneOffset.UTC;
        assertEquals("Z", offset.getId());
    }

    //-----------------------------------------------------------------------
    // getRules()
    //-----------------------------------------------------------------------
    @Test
    public void test_getRules() {
        ZoneOffset offset = ZoneOffset.ofHoursMinutesSeconds(1, 2, 3);
        assertEquals(true, offset.getRules().isFixedOffset());
        assertEquals(offset, offset.getRules().getOffset((Instant) null));
        assertEquals(Duration.ZERO, offset.getRules().getDaylightSavings((Instant) null));
        assertEquals(offset, offset.getRules().getStandardOffset((Instant) null));
        assertEquals(null, offset.getRules().nextTransition((Instant) null));
        assertEquals(null, offset.getRules().previousTransition((Instant) null));

        assertEquals(true, offset.getRules().isValidOffset((LocalDateTime) null, offset));
        assertEquals(false, offset.getRules().isValidOffset((LocalDateTime) null, ZoneOffset.UTC));
        assertEquals(false, offset.getRules().isValidOffset((LocalDateTime) null, null));
        assertEquals(offset, offset.getRules().getOffset((LocalDateTime) null));
        assertEquals(Arrays.asList(offset), offset.getRules().getValidOffsets((LocalDateTime) null));
        assertEquals(null, offset.getRules().getTransition((LocalDateTime) null));
        assertEquals(0, offset.getRules().getTransitions().size());
        assertEquals(0, offset.getRules().getTransitionRules().size());
    }

    //-----------------------------------------------------------------------
    // get(TemporalField)
    //-----------------------------------------------------------------------
    @Test
    public void test_get_TemporalField() {
        assertEquals(0, ZoneOffset.UTC.get(OFFSET_SECONDS));
        assertEquals(-7200, ZoneOffset.ofHours(-2).get(OFFSET_SECONDS));
        assertEquals(65, ZoneOffset.ofHoursMinutesSeconds(0, 1, 5).get(OFFSET_SECONDS));
    }

    @Test
    public void test_getLong_TemporalField() {
        assertEquals(0, ZoneOffset.UTC.getLong(OFFSET_SECONDS));
        assertEquals(-7200, ZoneOffset.ofHours(-2).getLong(OFFSET_SECONDS));
        assertEquals(65, ZoneOffset.ofHoursMinutesSeconds(0, 1, 5).getLong(OFFSET_SECONDS));
    }

    //-----------------------------------------------------------------------
    // query(TemporalQuery)
    //-----------------------------------------------------------------------
    Object[][] data_query() {
        return new Object[][] {
                {ZoneOffset.UTC, TemporalQueries.chronology(), null},
                {ZoneOffset.UTC, TemporalQueries.zoneId(), null},
                {ZoneOffset.UTC, TemporalQueries.precision(), null},
                {ZoneOffset.UTC, TemporalQueries.zone(), ZoneOffset.UTC},
                {ZoneOffset.UTC, TemporalQueries.offset(), ZoneOffset.UTC},
                {ZoneOffset.UTC, TemporalQueries.localDate(), null},
                {ZoneOffset.UTC, TemporalQueries.localTime(), null},
        };
    }

    @ParameterizedTest
    @MethodSource("data_query")
    public <T> void test_query(TemporalAccessor temporal, TemporalQuery<T> query, T expected) {
        assertEquals(expected, temporal.query(query));
    }

    @ParameterizedTest
    @MethodSource("data_query")
    public <T> void test_queryFrom(TemporalAccessor temporal, TemporalQuery<T> query, T expected) {
        assertEquals(expected, query.queryFrom(temporal));
    }

    @Test
    public void test_query_null() {
        Assertions.assertThrows(NullPointerException.class, () -> ZoneOffset.UTC.query(null));
    }

    //-----------------------------------------------------------------------
    // compareTo()
    //-----------------------------------------------------------------------
    @Test
    public void test_compareTo() {
        ZoneOffset offset1 = ZoneOffset.ofHoursMinutesSeconds(1, 2, 3);
        ZoneOffset offset2 = ZoneOffset.ofHoursMinutesSeconds(2, 3, 4);
        assertTrue(offset1.compareTo(offset2) > 0);
        assertTrue(offset2.compareTo(offset1) < 0);
        assertTrue(offset1.compareTo(offset1) == 0);
        assertTrue(offset2.compareTo(offset2) == 0);
    }

    //-----------------------------------------------------------------------
    // equals() / hashCode()
    //-----------------------------------------------------------------------
    @Test
    public void test_equals() {
        ZoneOffset offset1 = ZoneOffset.ofHoursMinutesSeconds(1, 2, 3);
        ZoneOffset offset2 = ZoneOffset.ofHoursMinutesSeconds(2, 3, 4);
        ZoneOffset offset2b = ZoneOffset.ofHoursMinutesSeconds(2, 3, 4);
        assertEquals(false, offset1.equals(offset2));
        assertEquals(false, offset2.equals(offset1));

        assertEquals(true, offset1.equals(offset1));
        assertEquals(true, offset2.equals(offset2));
        assertEquals(true, offset2.equals(offset2b));

        assertEquals(true, offset1.hashCode() == offset1.hashCode());
        assertEquals(true, offset2.hashCode() == offset2.hashCode());
        assertEquals(true, offset2.hashCode() == offset2b.hashCode());
    }

    //-----------------------------------------------------------------------
    // adjustInto()
    //-----------------------------------------------------------------------
    @Test
    public void test_adjustInto_ZonedDateTime() {
        ZoneOffset base = ZoneOffset.ofHoursMinutesSeconds(1, 1, 1);
        for (String zoneId : ZoneId.getAvailableZoneIds()) {
            //Do not change offset of ZonedDateTime after adjustInto()
            ZonedDateTime zonedDateTime_target = ZonedDateTime.of(LocalDate.of(1909, 2, 2), LocalTime.of(10, 10, 10), ZoneId.of(zoneId));
            ZonedDateTime zonedDateTime_result = (ZonedDateTime)(base.adjustInto(zonedDateTime_target));
            assertEquals(zonedDateTime_result.getOffset(), zonedDateTime_target.getOffset());

            OffsetDateTime offsetDateTime_target = zonedDateTime_target.toOffsetDateTime();
            OffsetDateTime offsetDateTime_result = (OffsetDateTime)(base.adjustInto(offsetDateTime_target));
            assertEquals(offsetDateTime_result.getOffset(), base);
        }
    }

    @Test
    public void test_adjustInto_OffsetDateTime() {
        ZoneOffset base = ZoneOffset.ofHoursMinutesSeconds(1, 1, 1);
        for (int i=-18; i<=18; i++) {
            OffsetDateTime offsetDateTime_target = OffsetDateTime.of(LocalDate.of(1909, 2, 2), LocalTime.of(10, 10, 10), ZoneOffset.ofHours(i));
            OffsetDateTime offsetDateTime_result = (OffsetDateTime)base.adjustInto(offsetDateTime_target);
            assertEquals(offsetDateTime_result.getOffset(), base);

            //Do not change offset of ZonedDateTime after adjustInto()
            ZonedDateTime zonedDateTime_target = offsetDateTime_target.toZonedDateTime();
            ZonedDateTime zonedDateTime_result = (ZonedDateTime)(base.adjustInto(zonedDateTime_target));
            assertEquals(zonedDateTime_result.getOffset(), zonedDateTime_target.getOffset());
        }
    }

    @Test
    public void test_adjustInto_dateOnly() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            ZoneOffset base = ZoneOffset.ofHoursMinutesSeconds(1, 1, 1);
            base.adjustInto((LocalDate.of(1909, 2, 2)));
        });
    }

    //-----------------------------------------------------------------------
    // toString()
    //-----------------------------------------------------------------------
    @Test
    public void test_toString() {
        ZoneOffset offset = ZoneOffset.ofHoursMinutesSeconds(1, 0, 0);
        assertEquals("+01:00", offset.toString());
        offset = ZoneOffset.ofHoursMinutesSeconds(1, 2, 3);
        assertEquals("+01:02:03", offset.toString());
        offset = ZoneOffset.UTC;
        assertEquals("Z", offset.toString());
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    private void doTestOffset(ZoneOffset offset, int hours, int minutes, int seconds) {
        assertEquals(hours * 60 * 60 + minutes * 60 + seconds, offset.getTotalSeconds());
        final String id;
        if (hours == 0 && minutes == 0 && seconds == 0) {
            id = "Z";
        } else {
            String str = (hours < 0 || minutes < 0 || seconds < 0) ? "-" : "+";
            str += Integer.toString(Math.abs(hours) + 100).substring(1);
            str += ":";
            str += Integer.toString(Math.abs(minutes) + 100).substring(1);
            if (seconds != 0) {
                str += ":";
                str += Integer.toString(Math.abs(seconds) + 100).substring(1);
            }
            id = str;
        }
        assertEquals(id, offset.getId());
        assertEquals(ZoneOffset.ofHoursMinutesSeconds(hours, minutes, seconds), offset);
        if (seconds == 0) {
            assertEquals(ZoneOffset.ofHoursMinutes(hours, minutes), offset);
            if (minutes == 0) {
                assertEquals(ZoneOffset.ofHours(hours), offset);
            }
        }
        assertEquals(offset, ZoneOffset.of(id));
        assertEquals(id, offset.toString());
    }

}
