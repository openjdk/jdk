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
package tck.java.time.format;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.DAY_OF_YEAR;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.text.Format;
import java.text.ParseException;
import java.text.ParsePosition;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.time.chrono.Chronology;
import java.time.chrono.IsoChronology;
import java.time.chrono.ThaiBuddhistChronology;
import java.time.chrono.ThaiBuddhistDate;
import java.time.format.DecimalStyle;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQuery;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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
public class TCKDateTimeFormatter {

    private static final ZoneOffset OFFSET_PONE = ZoneOffset.ofHours(1);
    private static final ZoneOffset OFFSET_PTHREE = ZoneOffset.ofHours(3);
    private static final ZoneId ZONE_PARIS = ZoneId.of("Europe/Paris");

    private static final DateTimeFormatter BASIC_FORMATTER = DateTimeFormatter.ofPattern("'ONE'd");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("'ONE'yyyy MM dd");

    private DateTimeFormatter fmt;

    @BeforeEach
    public void setUp() {
        fmt = new DateTimeFormatterBuilder().appendLiteral("ONE")
                                            .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
                                            .toFormatter();
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_withLocale() {
        DateTimeFormatter base = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
        DateTimeFormatter test = base.withLocale(Locale.GERMAN);
        assertEquals(Locale.GERMAN, test.getLocale());
    }

    @Test
    public void test_withLocale_null() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter base = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            base.withLocale((Locale) null);
        });
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_withChronology() {
        DateTimeFormatter test = fmt;
        assertEquals(null, test.getChronology());
        test = test.withChronology(IsoChronology.INSTANCE);
        assertEquals(IsoChronology.INSTANCE, test.getChronology());
        test = test.withChronology(null);
        assertEquals(null, test.getChronology());
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_withZone() {
        DateTimeFormatter test = fmt;
        assertEquals(null, test.getZone());
        test = test.withZone(ZoneId.of("Europe/Paris"));
        assertEquals(ZoneId.of("Europe/Paris"), test.getZone());
        test = test.withZone(ZoneOffset.UTC);
        assertEquals(ZoneOffset.UTC, test.getZone());
        test = test.withZone(null);
        assertEquals(null, test.getZone());
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_resolverFields_selectOneDateResolveYMD() throws Exception {
        DateTimeFormatter base = new DateTimeFormatterBuilder()
                .appendValue(YEAR).appendLiteral('-').appendValue(MONTH_OF_YEAR).appendLiteral('-')
                .appendValue(DAY_OF_MONTH).appendLiteral('-').appendValue(DAY_OF_YEAR).toFormatter();
        DateTimeFormatter f = base.withResolverFields(YEAR, MONTH_OF_YEAR, DAY_OF_MONTH);
        try {
            base.parse("2012-6-30-321", LocalDate::from);  // wrong day-of-year
            fail();
        } catch (DateTimeException ex) {
            // expected, fails as it produces two different dates
        }
        LocalDate parsed = f.parse("2012-6-30-321", LocalDate::from);  // ignored day-of-year
        assertEquals(LocalDate.of(2012, 6, 30), parsed);
    }

    @Test
    public void test_resolverFields_selectOneDateResolveYD() throws Exception {
        DateTimeFormatter base = new DateTimeFormatterBuilder()
                .appendValue(YEAR).appendLiteral('-').appendValue(MONTH_OF_YEAR).appendLiteral('-')
                .appendValue(DAY_OF_MONTH).appendLiteral('-').appendValue(DAY_OF_YEAR).toFormatter();
        DateTimeFormatter f = base.withResolverFields(YEAR, DAY_OF_YEAR);
        Set<TemporalField> expected = new HashSet<>(Arrays.asList(YEAR, DAY_OF_YEAR));
        assertEquals(expected, f.getResolverFields(), "ResolveFields: " + f.getResolverFields());
        try {
            base.parse("2012-6-30-321", LocalDate::from);  // wrong month/day-of-month
            fail();
        } catch (DateTimeException ex) {
            // expected, fails as it produces two different dates
        }
        LocalDate parsed = f.parse("2012-6-30-321", LocalDate::from);  // ignored month/day-of-month
        assertEquals(LocalDate.of(2012, 11, 16), parsed);
    }

    @Test
    public void test_resolverFields_ignoreCrossCheck() throws Exception {
        DateTimeFormatter base = new DateTimeFormatterBuilder()
                .appendValue(YEAR).appendLiteral('-').appendValue(DAY_OF_YEAR).appendLiteral('-')
                .appendValue(DAY_OF_WEEK).toFormatter();
        DateTimeFormatter f = base.withResolverFields(YEAR, DAY_OF_YEAR);
        try {
            base.parse("2012-321-1", LocalDate::from);  // wrong day-of-week
            fail();
        } catch (DateTimeException ex) {
            // expected, should fail in cross-check of day-of-week
        }
        LocalDate parsed = f.parse("2012-321-1", LocalDate::from);  // ignored wrong day-of-week
        assertEquals(LocalDate.of(2012, 11, 16), parsed);
    }

    @Test
    public void test_resolverFields_emptyList() throws Exception {
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(YEAR).toFormatter().withResolverFields();
        TemporalAccessor parsed = f.parse("2012");
        assertEquals(false, parsed.isSupported(YEAR));  // not in the list of resolverFields
    }

    @Test
    public void test_resolverFields_listOfOneMatching() throws Exception {
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(YEAR).toFormatter().withResolverFields(YEAR);
        TemporalAccessor parsed = f.parse("2012");
        assertEquals(true, parsed.isSupported(YEAR));
    }

    @Test
    public void test_resolverFields_listOfOneNotMatching() throws Exception {
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(YEAR).toFormatter().withResolverFields(MONTH_OF_YEAR);
        TemporalAccessor parsed = f.parse("2012");
        assertEquals(false, parsed.isSupported(YEAR));  // not in the list of resolverFields
        assertEquals(false, parsed.isSupported(MONTH_OF_YEAR));
    }

    @Test
    public void test_resolverFields_listOfOneNull() throws Exception {
        DateTimeFormatter f = new DateTimeFormatterBuilder()
                .appendValue(YEAR).toFormatter().withResolverFields((TemporalField) null);
        TemporalAccessor parsed = f.parse("2012");
        assertEquals(false, parsed.isSupported(YEAR));  // not in the list of resolverFields
    }

    @Test
    public void test_resolverFields_Array_null() throws Exception {
        DateTimeFormatter f = DateTimeFormatter.ISO_DATE.withResolverFields(MONTH_OF_YEAR);
        assertEquals(1, f.getResolverFields().size());
        f = f.withResolverFields((TemporalField[]) null);
        assertEquals(null, f.getResolverFields());
    }

    @Test
    public void test_resolverFields_Set_null() throws Exception {
        DateTimeFormatter f = DateTimeFormatter.ISO_DATE.withResolverFields(MONTH_OF_YEAR);
        assertEquals(1, f.getResolverFields().size());
        f = f.withResolverFields((Set<TemporalField>) null);
        assertEquals(null, f.getResolverFields());
    }

    //-----------------------------------------------------------------------
    // format
    //-----------------------------------------------------------------------
    Object[][] data_format_withZone_withChronology() {
        YearMonth ym = YearMonth.of(2008, 6);
        LocalDate ld = LocalDate.of(2008, 6, 30);
        LocalTime lt = LocalTime.of(11, 30);
        LocalDateTime ldt = LocalDateTime.of(2008, 6, 30, 11, 30);
        OffsetTime ot = OffsetTime.of(LocalTime.of(11, 30), OFFSET_PONE);
        OffsetDateTime odt = OffsetDateTime.of(LocalDateTime.of(2008, 6, 30, 11, 30), OFFSET_PONE);
        ZonedDateTime zdt = ZonedDateTime.of(LocalDateTime.of(2008, 6, 30, 11, 30), ZONE_PARIS);
        ChronoZonedDateTime<ThaiBuddhistDate> thaiZdt = ThaiBuddhistChronology.INSTANCE.zonedDateTime(zdt);
        Instant instant = Instant.ofEpochSecond(3600);
        return new Object[][] {
                {null, null, DayOfWeek.MONDAY, "::::"},
                {null, null, ym, "2008::::ISO"},
                {null, null, ld, "2008::::ISO"},
                {null, null, lt, ":11:::"},
                {null, null, ldt, "2008:11:::ISO"},
                {null, null, ot, ":11:+01:00::"},
                {null, null, odt, "2008:11:+01:00::ISO"},
                {null, null, zdt, "2008:11:+02:00:Europe/Paris:ISO"},
                {null, null, instant, "::::"},

                {IsoChronology.INSTANCE, null, DayOfWeek.MONDAY, "::::ISO"},
                {IsoChronology.INSTANCE, null, ym, "2008::::ISO"},
                {IsoChronology.INSTANCE, null, ld, "2008::::ISO"},
                {IsoChronology.INSTANCE, null, lt, ":11:::ISO"},
                {IsoChronology.INSTANCE, null, ldt, "2008:11:::ISO"},
                {IsoChronology.INSTANCE, null, ot, ":11:+01:00::ISO"},
                {IsoChronology.INSTANCE, null, odt, "2008:11:+01:00::ISO"},
                {IsoChronology.INSTANCE, null, zdt, "2008:11:+02:00:Europe/Paris:ISO"},
                {IsoChronology.INSTANCE, null, instant, "::::ISO"},

                {null, ZONE_PARIS, DayOfWeek.MONDAY, ":::Europe/Paris:"},
                {null, ZONE_PARIS, ym, "2008:::Europe/Paris:ISO"},
                {null, ZONE_PARIS, ld, "2008:::Europe/Paris:ISO"},
                {null, ZONE_PARIS, lt, ":11::Europe/Paris:"},
                {null, ZONE_PARIS, ldt, "2008:11::Europe/Paris:ISO"},
                {null, ZONE_PARIS, ot, ":11:+01:00:Europe/Paris:"},
                {null, ZONE_PARIS, odt, "2008:12:+02:00:Europe/Paris:ISO"},
                {null, ZONE_PARIS, zdt, "2008:11:+02:00:Europe/Paris:ISO"},
                {null, ZONE_PARIS, instant, "1970:02:+01:00:Europe/Paris:ISO"},

                {null, OFFSET_PTHREE, DayOfWeek.MONDAY, ":::+03:00:"},
                {null, OFFSET_PTHREE, ym, "2008:::+03:00:ISO"},
                {null, OFFSET_PTHREE, ld, "2008:::+03:00:ISO"},
                {null, OFFSET_PTHREE, lt, ":11::+03:00:"},
                {null, OFFSET_PTHREE, ldt, "2008:11::+03:00:ISO"},
                {null, OFFSET_PTHREE, ot, null},  // offset and zone clash
                {null, OFFSET_PTHREE, odt, "2008:13:+03:00:+03:00:ISO"},
                {null, OFFSET_PTHREE, zdt, "2008:12:+03:00:+03:00:ISO"},
                {null, OFFSET_PTHREE, instant, "1970:04:+03:00:+03:00:ISO"},

                {ThaiBuddhistChronology.INSTANCE, null, DayOfWeek.MONDAY, null},  // not a complete date
                {ThaiBuddhistChronology.INSTANCE, null, ym, null},  // not a complete date
                {ThaiBuddhistChronology.INSTANCE, null, ld, "2551::::ThaiBuddhist"},
                {ThaiBuddhistChronology.INSTANCE, null, lt, ":11:::ThaiBuddhist"},
                {ThaiBuddhistChronology.INSTANCE, null, ldt, "2551:11:::ThaiBuddhist"},
                {ThaiBuddhistChronology.INSTANCE, null, ot, ":11:+01:00::ThaiBuddhist"},
                {ThaiBuddhistChronology.INSTANCE, null, odt, "2551:11:+01:00::ThaiBuddhist"},
                {ThaiBuddhistChronology.INSTANCE, null, zdt, "2551:11:+02:00:Europe/Paris:ThaiBuddhist"},
                {ThaiBuddhistChronology.INSTANCE, null, instant, "::::ThaiBuddhist"},

                {ThaiBuddhistChronology.INSTANCE, null, DayOfWeek.MONDAY, null},  // not a complete date
                {ThaiBuddhistChronology.INSTANCE, ZONE_PARIS, ym, null},  // not a complete date
                {ThaiBuddhistChronology.INSTANCE, ZONE_PARIS, ld, "2551:::Europe/Paris:ThaiBuddhist"},
                {ThaiBuddhistChronology.INSTANCE, ZONE_PARIS, lt, ":11::Europe/Paris:ThaiBuddhist"},
                {ThaiBuddhistChronology.INSTANCE, ZONE_PARIS, ldt, "2551:11::Europe/Paris:ThaiBuddhist"},
                {ThaiBuddhistChronology.INSTANCE, ZONE_PARIS, ot, ":11:+01:00:Europe/Paris:ThaiBuddhist"},
                {ThaiBuddhistChronology.INSTANCE, ZONE_PARIS, odt, "2551:12:+02:00:Europe/Paris:ThaiBuddhist"},
                {ThaiBuddhistChronology.INSTANCE, ZONE_PARIS, zdt, "2551:11:+02:00:Europe/Paris:ThaiBuddhist"},
                {ThaiBuddhistChronology.INSTANCE, ZONE_PARIS, instant, "2513:02:+01:00:Europe/Paris:ThaiBuddhist"},

                {null, ZONE_PARIS, thaiZdt, "2551:11:+02:00:Europe/Paris:ThaiBuddhist"},
                {ThaiBuddhistChronology.INSTANCE, ZONE_PARIS, thaiZdt, "2551:11:+02:00:Europe/Paris:ThaiBuddhist"},
                {IsoChronology.INSTANCE, ZONE_PARIS, thaiZdt, "2008:11:+02:00:Europe/Paris:ISO"},
        };
    }

    @ParameterizedTest
    @MethodSource("data_format_withZone_withChronology")
    public void test_format_withZone_withChronology(Chronology overrideChrono, ZoneId overrideZone, TemporalAccessor temporal, String expected) {
        DateTimeFormatter test = new DateTimeFormatterBuilder()
                .optionalStart().appendValue(YEAR, 4).optionalEnd()
                .appendLiteral(':').optionalStart().appendValue(HOUR_OF_DAY, 2).optionalEnd()
                .appendLiteral(':').optionalStart().appendOffsetId().optionalEnd()
                .appendLiteral(':').optionalStart().appendZoneId().optionalEnd()
                .appendLiteral(':').optionalStart().appendChronologyId().optionalEnd()
                .toFormatter(Locale.ENGLISH)
                .withChronology(overrideChrono).withZone(overrideZone);
        if (expected != null) {
            String result = test.format(temporal);
            assertEquals(expected, result);
        } else {
            try {
                test.format(temporal);
                fail("Formatting should have failed");
            } catch (DateTimeException ex) {
                // expected
            }
        }
    }

    @Test
    public void test_format_withChronology_nonChronoFieldMapLink() {
        TemporalAccessor temporal = new TemporalAccessor() {
            @Override
            public boolean isSupported(TemporalField field) {
                return field == IsoFields.WEEK_BASED_YEAR;
            }
            @Override
            public long getLong(TemporalField field) {
                if (field == IsoFields.WEEK_BASED_YEAR) {
                    return 2345;
                }
                throw new UnsupportedTemporalTypeException("Unsupported field: " + field);
            }
        };
        DateTimeFormatter test = new DateTimeFormatterBuilder()
                .appendValue(IsoFields.WEEK_BASED_YEAR, 4)
                .toFormatter(Locale.ENGLISH)
                .withChronology(IsoChronology.INSTANCE);
        String result = test.format(temporal);
        assertEquals("2345", result);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_format_TemporalAccessor_simple() {
        DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
        String result = test.format(LocalDate.of(2008, 6, 30));
        assertEquals("ONE30", result);
    }

    @Test
    public void test_format_TemporalAccessor_noSuchField() {
        Assertions.assertThrows(DateTimeException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            test.format(LocalTime.of(11, 30));
        });
    }

    @Test
    public void test_format_TemporalAccessor_null() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            test.format((TemporalAccessor) null);
        });
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_print_TemporalAppendable() throws Exception {
        DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
        StringBuilder buf = new StringBuilder();
        test.formatTo(LocalDate.of(2008, 6, 30), buf);
        assertEquals("ONE30", buf.toString());
    }

    @Test
    public void test_print_TemporalAppendable_noSuchField() throws Exception {
        Assertions.assertThrows(DateTimeException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            StringBuilder buf = new StringBuilder();
            test.formatTo(LocalTime.of(11, 30), buf);
        });
    }

    @Test
    public void test_print_TemporalAppendable_nullTemporal() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            StringBuilder buf = new StringBuilder();
            test.formatTo((TemporalAccessor) null, buf);
        });
    }

    @Test
    public void test_print_TemporalAppendable_nullAppendable() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            test.formatTo(LocalDate.of(2008, 6, 30), (Appendable) null);
        });
    }

    //-----------------------------------------------------------------------
    // parse(CharSequence)
    //-----------------------------------------------------------------------
    @Test
    public void test_parse_CharSequence() {
        DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
        TemporalAccessor result = test.parse("ONE30");
        assertEquals(true, result.isSupported(DAY_OF_MONTH));
        assertEquals(30L, result.getLong(DAY_OF_MONTH));
        assertEquals(false, result.isSupported(HOUR_OF_DAY));
    }

    @Test
    public void test_parse_CharSequence_resolved() {
        DateTimeFormatter test = DateTimeFormatter.ISO_DATE;
        TemporalAccessor result = test.parse("2012-06-30");
        assertEquals(true, result.isSupported(YEAR));
        assertEquals(true, result.isSupported(MONTH_OF_YEAR));
        assertEquals(true, result.isSupported(DAY_OF_MONTH));
        assertEquals(false, result.isSupported(HOUR_OF_DAY));
        assertEquals(2012L, result.getLong(YEAR));
        assertEquals(6L, result.getLong(MONTH_OF_YEAR));
        assertEquals(30L, result.getLong(DAY_OF_MONTH));
        assertEquals(LocalDate.of(2012, 6, 30), result.query(LocalDate::from));
    }

    @Test
    public void test_parse_CharSequence_null() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            test.parse((String) null);
        });
    }

    //-----------------------------------------------------------------------
    // parse(CharSequence)
    //-----------------------------------------------------------------------
    @Test
    public void test_parse_CharSequence_ParsePosition() {
        DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
        ParsePosition pos = new ParsePosition(3);
        TemporalAccessor result = test.parse("XXXONE30XXX", pos);
        assertEquals(8, pos.getIndex());
        assertEquals(-1, pos.getErrorIndex());
        assertEquals(true, result.isSupported(DAY_OF_MONTH));
        assertEquals(30L, result.getLong(DAY_OF_MONTH));
        assertEquals(false, result.isSupported(HOUR_OF_DAY));
    }

    @Test
    public void test_parse_CharSequence_ParsePosition_resolved() {
        DateTimeFormatter test = DateTimeFormatter.ISO_DATE;
        ParsePosition pos = new ParsePosition(3);
        TemporalAccessor result = test.parse("XXX2012-06-30XXX", pos);
        assertEquals(13, pos.getIndex());
        assertEquals(-1, pos.getErrorIndex());
        assertEquals(true, result.isSupported(YEAR));
        assertEquals(true, result.isSupported(MONTH_OF_YEAR));
        assertEquals(true, result.isSupported(DAY_OF_MONTH));
        assertEquals(false, result.isSupported(HOUR_OF_DAY));
        assertEquals(2012L, result.getLong(YEAR));
        assertEquals(6L, result.getLong(MONTH_OF_YEAR));
        assertEquals(30L, result.getLong(DAY_OF_MONTH));
        assertEquals(LocalDate.of(2012, 6, 30), result.query(LocalDate::from));
    }

    @Test
    public void test_parse_CharSequence_ParsePosition_parseError() {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            DateTimeFormatter test = DateTimeFormatter.ISO_DATE;
            ParsePosition pos = new ParsePosition(3);
            try {
                test.parse("XXX2012XXX", pos);
                fail();
            } catch (DateTimeParseException ex) {
                assertEquals(7, ex.getErrorIndex());
                throw ex;
            }
        });
    }

    @Test
    public void test_parse_CharSequence_ParsePosition_indexTooBig() {
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> {
            DateTimeFormatter test = DateTimeFormatter.ISO_DATE;
            test.parse("Text", new ParsePosition(5));
        });
    }

    @Test
    public void test_parse_CharSequence_ParsePosition_nullText() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            test.parse((CharSequence) null, new ParsePosition(0));
        });
    }

    @Test
    public void test_parse_CharSequence_ParsePosition_nullParsePosition() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            test.parse("Text", (ParsePosition) null);
        });
    }

    //-----------------------------------------------------------------------
    // parse(Query)
    //-----------------------------------------------------------------------
    @Test
    public void test_parse_Query_String() throws Exception {
        LocalDate result = DATE_FORMATTER.parse("ONE2012 07 27", LocalDate::from);
        assertEquals(LocalDate.of(2012, 7, 27), result);
    }

    @Test
    public void test_parse_Query_CharSequence() throws Exception {
        LocalDate result = DATE_FORMATTER.parse(new StringBuilder("ONE2012 07 27"), LocalDate::from);
        assertEquals(LocalDate.of(2012, 7, 27), result);
    }

    @Test
    public void test_parse_Query_String_parseError() throws Exception {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            try {
                DATE_FORMATTER.parse("ONE2012 07 XX", LocalDate::from);
            } catch (DateTimeParseException ex) {
                assertEquals(true, ex.getMessage().contains("could not be parsed"));
                assertEquals(true, ex.getMessage().contains("ONE2012 07 XX"));
                assertEquals("ONE2012 07 XX", ex.getParsedString());
                assertEquals(11, ex.getErrorIndex());
                throw ex;
            }
        });
    }

    @Test
    public void test_parse_Query_String_parseErrorLongText() throws Exception {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            try {
                DATE_FORMATTER.parse("ONEXXX67890123456789012345678901234567890123456789012345678901234567890123456789", LocalDate::from);
            } catch (DateTimeParseException ex) {
                assertEquals(true, ex.getMessage().contains("could not be parsed"));
                assertEquals(true, ex.getMessage().contains("ONEXXX6789012345678901234567890123456789012345678901234567890123..."));
                assertEquals("ONEXXX67890123456789012345678901234567890123456789012345678901234567890123456789", ex.getParsedString());
                assertEquals(3, ex.getErrorIndex());
                throw ex;
            }
        });
    }

    @Test
    public void test_parse_Query_String_parseIncomplete() throws Exception {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            try {
                DATE_FORMATTER.parse("ONE2012 07 27SomethingElse", LocalDate::from);
            } catch (DateTimeParseException ex) {
                assertEquals(true, ex.getMessage().contains("could not be parsed"));
                assertEquals(true, ex.getMessage().contains("ONE2012 07 27SomethingElse"));
                assertEquals("ONE2012 07 27SomethingElse", ex.getParsedString());
                assertEquals(13, ex.getErrorIndex());
                throw ex;
            }
        });
    }

    @Test
    public void test_parse_Query_String_nullText() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> DATE_FORMATTER.parse((String) null, LocalDate::from));
    }

    @Test
    public void test_parse_Query_String_nullRule() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            test.parse("30", (TemporalQuery<?>) null);
        });
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_parseBest_firstOption() throws Exception {
        DateTimeFormatter test = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[XXX]");
        TemporalAccessor result = test.parseBest("2011-06-30 12:30+03:00", ZonedDateTime::from, LocalDateTime::from);
        LocalDateTime ldt = LocalDateTime.of(2011, 6, 30, 12, 30);
        assertEquals(ZonedDateTime.of(ldt, ZoneOffset.ofHours(3)), result);
    }

    @Test
    public void test_parseBest_secondOption() throws Exception {
        DateTimeFormatter test = DateTimeFormatter.ofPattern("yyyy-MM-dd[ HH:mm[XXX]]");
        TemporalAccessor result = test.parseBest("2011-06-30", ZonedDateTime::from, LocalDate::from);
        assertEquals(LocalDate.of(2011, 6, 30), result);
    }

    @Test
    public void test_parseBest_String_parseError() throws Exception {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            DateTimeFormatter test = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[XXX]");
            try {
                test.parseBest("2011-06-XX", ZonedDateTime::from, LocalDateTime::from);
            } catch (DateTimeParseException ex) {
                assertEquals(true, ex.getMessage().contains("could not be parsed"));
                assertEquals(true, ex.getMessage().contains("XX"));
                assertEquals("2011-06-XX", ex.getParsedString());
                assertEquals(8, ex.getErrorIndex());
                throw ex;
            }
        });
    }

    @Test
    public void test_parseBest_String_parseErrorLongText() throws Exception {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            try {
                test.parseBest("ONEXXX67890123456789012345678901234567890123456789012345678901234567890123456789", ZonedDateTime::from, LocalDate::from);
            } catch (DateTimeParseException ex) {
                assertEquals(true, ex.getMessage().contains("could not be parsed"));
                assertEquals(true, ex.getMessage().contains("ONEXXX6789012345678901234567890123456789012345678901234567890123..."));
                assertEquals("ONEXXX67890123456789012345678901234567890123456789012345678901234567890123456789", ex.getParsedString());
                assertEquals(3, ex.getErrorIndex());
                throw ex;
            }
        });
    }

    @Test
    public void test_parseBest_String_parseIncomplete() throws Exception {
        Assertions.assertThrows(DateTimeParseException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            try {
                test.parseBest("ONE30SomethingElse", ZonedDateTime::from, LocalDate::from);
            } catch (DateTimeParseException ex) {
                assertEquals(true, ex.getMessage().contains("could not be parsed"));
                assertEquals(true, ex.getMessage().contains("ONE30SomethingElse"));
                assertEquals("ONE30SomethingElse", ex.getParsedString());
                assertEquals(5, ex.getErrorIndex());
                throw ex;
            }
        });
    }

    @Test
    public void test_parseBest_String_nullText() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            test.parseBest((String) null, ZonedDateTime::from, LocalDate::from);
        });
    }

    @Test
    public void test_parseBest_String_nullRules() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            test.parseBest("30", (TemporalQuery<?>[]) null);
        });
    }

    @Test
    public void test_parseBest_String_zeroRules() throws Exception {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            test.parseBest("30", new TemporalQuery<?>[0]);
        });
    }

    @Test
    public void test_parseBest_String_oneRule() throws Exception {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            test.parseBest("30", LocalDate::from);
        });
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_parseUnresolved_StringParsePosition() {
        DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
        ParsePosition pos = new ParsePosition(0);
        TemporalAccessor result = test.parseUnresolved("ONE30XXX", pos);
        assertEquals(5, pos.getIndex());
        assertEquals(-1, pos.getErrorIndex());
        assertEquals(30L, result.getLong(DAY_OF_MONTH));
    }

    @Test
    public void test_parseUnresolved_StringParsePosition_parseError() {
        DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
        ParsePosition pos = new ParsePosition(0);
        TemporalAccessor result = test.parseUnresolved("ONEXXX", pos);
        assertEquals(0, pos.getIndex());
        assertEquals(3, pos.getErrorIndex());
        assertEquals(null, result);
    }

    @Test
    public void test_parseUnresolved_StringParsePosition_duplicateFieldSameValue() {
        DateTimeFormatter test = new DateTimeFormatterBuilder()
                .appendValue(MONTH_OF_YEAR).appendLiteral('-').appendValue(MONTH_OF_YEAR).toFormatter();
        ParsePosition pos = new ParsePosition(3);
        TemporalAccessor result = test.parseUnresolved("XXX6-6", pos);
        assertEquals(6, pos.getIndex());
        assertEquals(-1, pos.getErrorIndex());
        assertEquals(6, result.getLong(MONTH_OF_YEAR));
    }

    @Test
    public void test_parseUnresolved_StringParsePosition_duplicateFieldDifferentValue() {
        DateTimeFormatter test = new DateTimeFormatterBuilder()
                .appendValue(MONTH_OF_YEAR).appendLiteral('-').appendValue(MONTH_OF_YEAR).toFormatter();
        ParsePosition pos = new ParsePosition(3);
        TemporalAccessor result = test.parseUnresolved("XXX6-7", pos);
        assertEquals(3, pos.getIndex());
        assertEquals(5, pos.getErrorIndex());
        assertEquals(null, result);
    }

    @Test
    public void test_parseUnresolved_StringParsePosition_nullString() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            ParsePosition pos = new ParsePosition(0);
            test.parseUnresolved((String) null, pos);
        });
    }

    @Test
    public void test_parseUnresolved_StringParsePosition_nullParsePosition() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            test.parseUnresolved("ONE30", (ParsePosition) null);
        });
    }

    @Test
    public void test_parseUnresolved_StringParsePosition_invalidPosition() {
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            ParsePosition pos = new ParsePosition(6);
            test.parseUnresolved("ONE30", pos);
        });
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @Test
    public void test_toFormat_format() throws Exception {
        DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
        Format format = test.toFormat();
        String result = format.format(LocalDate.of(2008, 6, 30));
        assertEquals("ONE30", result);
    }

    @Test
    public void test_toFormat_format_null() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            Format format = test.toFormat();
            format.format(null);
        });
    }

    @Test
    public void test_toFormat_format_notTemporal() throws Exception {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            Format format = test.toFormat();
            format.format("Not a Temporal");
        });
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_toFormat_parseObject_String() throws Exception {
        DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
        Format format = test.toFormat();
        TemporalAccessor result = (TemporalAccessor) format.parseObject("ONE30");
        assertEquals(true, result.isSupported(DAY_OF_MONTH));
        assertEquals(30L, result.getLong(DAY_OF_MONTH));
    }

    @Test
    public void test_toFormat_parseObject_String_parseError() throws Exception {
        Assertions.assertThrows(ParseException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            Format format = test.toFormat();
            try {
                format.parseObject("ONEXXX");
            } catch (ParseException ex) {
                assertEquals(true, ex.getMessage().contains("ONEXXX"));
                assertEquals(3, ex.getErrorOffset());
                throw ex;
            }
        });
    }

    @Test
    public void test_toFormat_parseObject_String_parseErrorLongText() throws Exception {
        Assertions.assertThrows(ParseException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            Format format = test.toFormat();
            try {
                format.parseObject("ONEXXX67890123456789012345678901234567890123456789012345678901234567890123456789");
            } catch (DateTimeParseException ex) {
                assertEquals(true, ex.getMessage().contains("ONEXXX6789012345678901234567890123456789012345678901234567890123..."));
                assertEquals("ONEXXX67890123456789012345678901234567890123456789012345678901234567890123456789", ex.getParsedString());
                assertEquals(3, ex.getErrorIndex());
                throw ex;
            }
        });
    }

    @Test
    public void test_toFormat_parseObject_String_null() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> {
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            Format format = test.toFormat();
            format.parseObject((String) null);
        });
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_toFormat_parseObject_StringParsePosition() throws Exception {
        DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
        Format format = test.toFormat();
        ParsePosition pos = new ParsePosition(0);
        TemporalAccessor result = (TemporalAccessor) format.parseObject("ONE30XXX", pos);
        assertEquals(5, pos.getIndex());
        assertEquals(-1, pos.getErrorIndex());
        assertEquals(true, result.isSupported(DAY_OF_MONTH));
        assertEquals(30L, result.getLong(DAY_OF_MONTH));
    }

    @Test
    public void test_toFormat_parseObject_StringParsePosition_parseError() throws Exception {
        DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
        Format format = test.toFormat();
        ParsePosition pos = new ParsePosition(0);
        TemporalAccessor result = (TemporalAccessor) format.parseObject("ONEXXX", pos);
        assertEquals(0, pos.getIndex());  // TODO: is this right?
        assertEquals(3, pos.getErrorIndex());
        assertEquals(null, result);
    }

    @Test
    public void test_toFormat_parseObject_StringParsePosition_nullString() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> {
            // SimpleDateFormat has this behavior
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            Format format = test.toFormat();
            ParsePosition pos = new ParsePosition(0);
            format.parseObject((String) null, pos);
        });
    }

    @Test
    public void test_toFormat_parseObject_StringParsePosition_nullParsePosition() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> {
            // SimpleDateFormat has this behavior
            DateTimeFormatter test = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
            Format format = test.toFormat();
            format.parseObject("ONE30", (ParsePosition) null);
        });
    }

    @Test
    public void test_toFormat_parseObject_StringParsePosition_invalidPosition_tooBig() throws Exception {
        // SimpleDateFormat has this behavior
        DateTimeFormatter dtf = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
        ParsePosition pos = new ParsePosition(6);
        Format test = dtf.toFormat();
        assertNull(test.parseObject("ONE30", pos));
        assertTrue(pos.getErrorIndex() >= 0);
    }

    @Test
    public void test_toFormat_parseObject_StringParsePosition_invalidPosition_tooSmall() throws Exception {
        // SimpleDateFormat throws StringIndexOutOfBoundException
        DateTimeFormatter dtf = fmt.withLocale(Locale.ENGLISH).withDecimalStyle(DecimalStyle.STANDARD);
        ParsePosition pos = new ParsePosition(-1);
        Format test = dtf.toFormat();
        assertNull(test.parseObject("ONE30", pos));
        assertTrue(pos.getErrorIndex() >= 0);
    }

    //-----------------------------------------------------------------------
    @Test
    public void test_toFormat_Query_format() throws Exception {
        Format format = BASIC_FORMATTER.toFormat();
        String result = format.format(LocalDate.of(2008, 6, 30));
        assertEquals("ONE30", result);
    }

    @Test
    public void test_toFormat_Query_parseObject_String() throws Exception {
        Format format = DATE_FORMATTER.toFormat(LocalDate::from);
        LocalDate result = (LocalDate) format.parseObject("ONE2012 07 27");
        assertEquals(LocalDate.of(2012, 7, 27), result);
    }

    @Test
    public void test_toFormat_parseObject_StringParsePosition_dateTimeError() throws Exception {
        Assertions.assertThrows(ParseException.class, () -> {
            Format format = DATE_FORMATTER.toFormat(LocalDate::from);
            format.parseObject("ONE2012 07 32");
        });
    }

    @Test
    public void test_toFormat_Query() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> BASIC_FORMATTER.toFormat(null));
    }

}
