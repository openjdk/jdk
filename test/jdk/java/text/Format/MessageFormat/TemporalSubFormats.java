/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8318761
 * @summary Test MessageFormatPattern ability to recognize the appropriate
 *          FormatType and FormatStyle for DateTimeFormatter(ClassicFormat).
 *          This includes the types dtf_time, dtf_date, dtf_datetime,
 *          and the DateTimeFormatter predefined formatters.
 * @run junit TemporalSubFormats
 */

import java.text.Format;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TemporalSubFormats {

    // Check that applying the built-in DateTimeFormatter types returns the
    // correct Format and formats properly. Patterns are case-insensitive
    @ParameterizedTest
    @MethodSource("preDefinedTypes")
    public void preDefinedPatternsTest(String pattern, Format fmt) {
        var mFmt = new MessageFormat("quux{0,"+pattern+"}quux");
        Object[] temporals = new Object[]{LocalDate.now(), LocalTime.now(),
                ZonedDateTime.now(), LocalDateTime.now(), OffsetDateTime.now(), Instant.now()};
        for (Object val : temporals) {
            // Wrap in Object array for MessageFormat
            Object[] wrappedVal = new Object[]{val};

            try {
                String mFmtted = mFmt.format(wrappedVal);
                // If current format can support the time object. Check equality of result
                assertEquals(mFmtted, "quux"+fmt.format(val)+"quux");
            } catch (IllegalArgumentException ignored) {
                // Otherwise, ensure both throw IAE on unsupported field
                assertThrows(IllegalArgumentException.class, () -> fmt.format(val));
            }
        }
    }

    // Provides String patterns and the associated (standalone) FormatType
    // Values should be case-insensitive
    private static Stream<Arguments> preDefinedTypes() {
        return Stream.of(
            Arguments.of("BASIC_ISO_DATE", DateTimeFormatter.BASIC_ISO_DATE.toFormat()),
            Arguments.of("ISO_LOCAL_DATE", DateTimeFormatter.ISO_LOCAL_DATE.toFormat()),
            Arguments.of("ISO_OFFSET_DATE", DateTimeFormatter.ISO_OFFSET_DATE.toFormat()),
            Arguments.of("ISO_DATE", DateTimeFormatter.ISO_DATE.toFormat()),
            Arguments.of("iso_local_time", DateTimeFormatter.ISO_LOCAL_TIME.toFormat()),
            Arguments.of("ISO_OFFSET_TIME", DateTimeFormatter.ISO_OFFSET_TIME.toFormat()),
            Arguments.of("iso_time", DateTimeFormatter.ISO_TIME.toFormat()),
            Arguments.of("ISO_LOCAL_DATE_TIME", DateTimeFormatter.ISO_LOCAL_DATE_TIME.toFormat()),
            Arguments.of("ISO_OFFSET_DATE_TIME", DateTimeFormatter.ISO_OFFSET_DATE_TIME.toFormat()),
            Arguments.of("ISO_ZONED_DATE_TIME", DateTimeFormatter.ISO_ZONED_DATE_TIME.toFormat()),
            Arguments.of("ISO_DATE_TIME", DateTimeFormatter.ISO_DATE_TIME.toFormat()),
            Arguments.of("ISO_ORDINAL_DATE", DateTimeFormatter.ISO_ORDINAL_DATE.toFormat()),
            Arguments.of("iso_week_date", DateTimeFormatter.ISO_WEEK_DATE.toFormat()),
            Arguments.of("ISO_INSTANT", DateTimeFormatter.ISO_INSTANT.toFormat()),
            Arguments.of("RFC_1123_DATE_TIME", DateTimeFormatter.RFC_1123_DATE_TIME.toFormat())
        );
    }

    // Check that the appropriate FormatType/Style combo returns correct Format
    // Unlike the other pattern tests, the formatted output is used to check
    // equality, as DateTimeFormatter does not implement equals()
    @ParameterizedTest
    @MethodSource("styles")
    public void applyPatternTest(String style, FormatStyle fStyle) {
        var time = ZonedDateTime.now();
        var date = LocalDate.now();

        // Test dtf_date
        var dFmt = new MessageFormat("{0,dtf_date"+style+"}");
        assertEquals(DateTimeFormatter.ofLocalizedDate(fStyle).withLocale(
                dFmt.getLocale()).toFormat().format(date),
                dFmt.getFormatsByArgumentIndex()[0].format(date));

        // Test dtf_time
        var tFmt = new MessageFormat("{0,dtf_time"+style+"}");
        assertEquals(DateTimeFormatter.ofLocalizedTime(fStyle).withLocale(
                tFmt.getLocale()).toFormat().format(time),
                tFmt.getFormatsByArgumentIndex()[0].format(time));

        // Test dtf_datetime
        var dtFmt = new MessageFormat("{0,dtf_datetime"+style+"}");
        assertEquals(DateTimeFormatter.ofLocalizedDateTime(fStyle).withLocale(
                        dtFmt.getLocale()).toFormat().format(time),
                dtFmt.getFormatsByArgumentIndex()[0].format(time));
    }

    // Provides String patterns and the associated FormatStyle
    private static Stream<Arguments> styles() {
        return Stream.of(
                Arguments.of("", FormatStyle.MEDIUM),
                Arguments.of(",short", FormatStyle.SHORT),
                Arguments.of(",medium", FormatStyle.MEDIUM),
                Arguments.of(",long", FormatStyle.LONG),
                Arguments.of(",full", FormatStyle.FULL)
        );
    }

    // Test that a proper Format from a SubformatPattern can be reproduced
    @Test
    public void subformatPatternTest() {
        // SubformatPattern invokes the same method for both dtf_date,
        // dtf_time, and dtf_datetime
        var pattern = "d MMM uuuu";
        var date = LocalDate.now();

        // Test dtf_date
        var dFmt = new MessageFormat("{0,dtf_date,"+pattern+"}");
        assertEquals(DateTimeFormatter.ofPattern(pattern,dFmt.getLocale()).toFormat().format(date),
                dFmt.getFormatsByArgumentIndex()[0].format(date));

        // Test dtf_time
        var tFmt = new MessageFormat("{0,dtf_time,"+pattern+"}");
        assertEquals(DateTimeFormatter.ofPattern(pattern,tFmt.getLocale()).toFormat().format(date),
                tFmt.getFormatsByArgumentIndex()[0].format(date));

        // Test dtf_datetime
        var dtFmt = new MessageFormat("{0,dtf_datetime,"+pattern+"}");
        assertEquals(DateTimeFormatter.ofPattern(pattern,dtFmt.getLocale()).toFormat().format(date),
                dtFmt.getFormatsByArgumentIndex()[0].format(date));
    }

    // Ensure that only the supported built-in FormatStyles or a
    // valid SubformatPattern are recognized
    @Test
    public void badApplyPatternTest() {
        // Not a supported FormatStyle: throws the underlying IAE from DTF
        // as it is interpreted as a subformatPattern
        IllegalArgumentException exc = assertThrows(IllegalArgumentException.class, () ->
                new MessageFormat("{0,dtf_date,longer}"));
        assertEquals("Unknown pattern letter: l", exc.getMessage());

        // Not a legal SubformatPattern: throws the underlying IAE from DTF
        exc = assertThrows(IllegalArgumentException.class, () ->
                new MessageFormat("{0,dtf_date,VVV}"));
        assertEquals("Pattern letter count must be 2: V", exc.getMessage());

        // Pre-defined ISO style does not exist and should be ignored
        assertDoesNotThrow(() -> new MessageFormat("{0,BASIC_ISO_DATE,foo}"),
                "Style on a pre-defined DTF should be ignored, instead of throwing an exception");
    }

    // DateTimeFormatters cannot be recognized when toPattern() is invoked
    // Default behavior of unrecognizable Formats is a FormatElement
    // in the form of { ArgumentIndex }
    @Test
    public void nonRecognizableToPatternTest() {
        // Check SubformatPattern
        var validPattern = "yy";
        var mFmt = new MessageFormat("{0}");
        mFmt.setFormatByArgumentIndex(0, DateTimeFormatter.ofPattern(validPattern).toFormat());
        assertEquals("{0}", mFmt.toPattern());

        // Check pre-defined styles
        var dFmt = new MessageFormat("{0,dtf_date,long}");
        assertEquals("{0}", dFmt.toPattern());
        var tFmt = new MessageFormat("{0,dtf_time,long}");
        assertEquals("{0}", tFmt.toPattern());
        var dtFmt = new MessageFormat("{0,dtf_datetime,long}");
        assertEquals("{0}", dtFmt.toPattern());
    }
}
