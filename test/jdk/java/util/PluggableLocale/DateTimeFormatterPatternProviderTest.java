/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8379973
 * @summary DateTimeFormatterPatternProvider tests
 * @library providersrc/javatimeprovider
 * @build javatime.DateTimeFormatterPatternProviderImpl
 * @run junit/othervm -Djava.locale.providers=SPI DateTimeFormatterPatternProviderTest
 */

import java.time.ZonedDateTime;
import java.time.chrono.Chronology;
import java.time.chrono.MinguoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DateTimeFormatterPatternProviderTest {

    private static Stream<Arguments> dateOrTime() {
        return Stream.of(
            Arguments.of(FormatStyle.FULL, "iso8601", Locale.UK),
            Arguments.of(FormatStyle.LONG, "japanese", Locale.JAPAN),
            Arguments.of(FormatStyle.MEDIUM, "roc", Locale.TAIWAN),
            Arguments.of(FormatStyle.SHORT, "islamic-umalqura", Locale.of("ar", "EG")));
    }

    private static Stream<Arguments> dateAndTime() {
        return Stream.of(
            Arguments.of(FormatStyle.SHORT, FormatStyle.FULL, "iso8601", Locale.UK),
            Arguments.of(FormatStyle.MEDIUM, FormatStyle.LONG, "japanese", Locale.JAPAN),
            Arguments.of(FormatStyle.LONG, FormatStyle.MEDIUM, "roc", Locale.TAIWAN),
            Arguments.of(FormatStyle.FULL, FormatStyle.SHORT, "islamic-umalqura", Locale.of("ar", "EG")));
    }

    // Tests for DateTimeFormatter methods

    @ParameterizedTest
    @MethodSource("dateOrTime")
    public void testOfLocalizedDate(FormatStyle dateStyle, String calType, Locale loc) {
        var formatted = DateTimeFormatter.ofLocalizedDate(dateStyle)
            .withLocale(loc)
            .withChronology(Chronology.of(calType))
            .format(ZonedDateTime.now());
        assertEquals("date style: " + dateStyle + ", timeStyle: null, calType: " + calType + ", loc: " + loc,
            formatted);
    }

    @ParameterizedTest
    @MethodSource("dateOrTime")
    public void testOfLocalizedTime(FormatStyle timeStyle, String calType, Locale loc) {
        var formatted = DateTimeFormatter.ofLocalizedTime(timeStyle)
            .withLocale(loc)
            .withChronology(Chronology.of(calType))
            .format(ZonedDateTime.now());
        assertEquals("date style: null, timeStyle: " + timeStyle + ", calType: " + calType + ", loc: " + loc,
            formatted);
    }

    @ParameterizedTest
    @MethodSource("dateOrTime")
    public void testOfLocalizedDateTime_1arg(FormatStyle dateTimeStyle, String calType, Locale loc) {
        var formatted = DateTimeFormatter.ofLocalizedDateTime(dateTimeStyle)
            .withLocale(loc)
            .withChronology(Chronology.of(calType))
            .format(ZonedDateTime.now());
        assertEquals("date style: " + dateTimeStyle + ", timeStyle: " + dateTimeStyle + ", calType: " + calType + ", loc: " + loc,
            formatted);
    }

    @ParameterizedTest
    @MethodSource("dateAndTime")
    public void testOfLocalizedDateTime_2args(FormatStyle dateStyle, FormatStyle timeStyle, String calType, Locale loc) {
        var formatted = DateTimeFormatter.ofLocalizedDateTime(dateStyle, timeStyle)
            .withLocale(loc)
            .withChronology(Chronology.of(calType))
            .format(ZonedDateTime.now());
        assertEquals("date style: " + dateStyle + ", timeStyle: " + timeStyle + ", calType: " + calType + ", loc: " + loc,
            formatted);
    }

    @Test
    public void testOfLocalizedPattern() {
        var formatted = DateTimeFormatter.ofLocalizedPattern("Bh")
            .withLocale(Locale.JAPAN)
            .withChronology(Chronology.of("japanese"))
            .format(ZonedDateTime.now());
        assertEquals("requestedTemplate: Bh, calType: japanese, loc: ja_JP", formatted);
    }

    // Tests for DateTimeFormatterBuilder methods

    @Test
    public void testAppendLocalized_1arg() {
        var formatted = new DateTimeFormatterBuilder()
            .appendLocalized("yMMMEd")
            .toFormatter(Locale.TAIWAN)
            .withChronology(Chronology.of("roc"))
            .format(ZonedDateTime.now());
        assertEquals("requestedTemplate: yMMMEd, calType: roc, loc: zh_TW", formatted);
    }

    @ParameterizedTest
    @MethodSource("dateAndTime")
    public void testAppendLocalized_2args(FormatStyle dateStyle, FormatStyle timeStyle, String calType, Locale loc) {
        var formatted = new DateTimeFormatterBuilder()
            .appendLocalized(dateStyle, timeStyle)
            .toFormatter(loc)
            .withChronology(Chronology.of(calType))
            .format(ZonedDateTime.now());
        assertEquals("date style: " + dateStyle + ", timeStyle: " + timeStyle + ", calType: " + calType + ", loc: " + loc,
            formatted);
    }

    @Test
    public void testGetLocalizedDateTimePattern_3args() {
        assertEquals("'requestedTemplate: yMMMEd, calType: roc, loc: zh_TW'",
            DateTimeFormatterBuilder.getLocalizedDateTimePattern("yMMMEd", MinguoChronology.INSTANCE, Locale.TAIWAN));
    }

    @ParameterizedTest
    @MethodSource("dateAndTime")
    public void testGetLocalizedDateTimePattern_4args(FormatStyle dateStyle, FormatStyle timeStyle, String calType, Locale loc) {
        assertEquals("'date style: " + dateStyle + ", timeStyle: " + timeStyle + ", calType: " + calType + ", loc: " + loc + "'",
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(dateStyle, timeStyle, Chronology.of(calType), loc));
    }
}
