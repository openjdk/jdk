/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test MessageFormatPattern ability to recognize and produce the
 *          appropriate FormatType and FormatStyle for DateTimeFormatter(ClassicFormat).
 * @run junit TemporalSubFormats
 */

import java.text.Format;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TemporalSubFormats {

    // Ensure the built-in FormatType and FormatStyles for dtFmt are as expected
    @Test
    public void applyPatternTest() {
        var mFmt = new MessageFormat(
                "{0,temporal,iso-local-date}{1,temporal,iso-local-time}" +
                        "{2,temporal,iso-local-date-time}{3,temporal,iso-offset-date-time}{4,temporal,iso-instant}");
        assertEquals(mFmt.getFormatsByArgumentIndex()[0], DateTimeFormatter.ISO_LOCAL_DATE.toFormat());
        assertEquals(mFmt.getFormatsByArgumentIndex()[1], DateTimeFormatter.ISO_LOCAL_TIME.toFormat());
        assertEquals(mFmt.getFormatsByArgumentIndex()[2], DateTimeFormatter.ISO_LOCAL_DATE_TIME.toFormat());
        assertEquals(mFmt.getFormatsByArgumentIndex()[3], DateTimeFormatter.ISO_OFFSET_DATE_TIME.toFormat());
        assertEquals(mFmt.getFormatsByArgumentIndex()[4], DateTimeFormatter.ISO_INSTANT.toFormat());
    }

    // Ensure that only the supported built-in FormatStyles or a
    // Subformat pattern are recognized
    @Test
    public void badApplyPatternTest() {
        // Not a supported FormatStyle
        assertThrows(IllegalArgumentException.class, () ->
                new MessageFormat("{0,number,iso-date"));
        // Bad Subformat pattern
        assertThrows(IllegalArgumentException.class, () ->
                new MessageFormat("{0,number,1"));
    }

    // Ensure the supported built-in FormatStyles can be recognized on toPattern()
    @Test
    public void toPatternTest() {
        var mFmt = new MessageFormat("{0}{1}{2}{3}{4}");
        mFmt.setFormatByArgumentIndex(0, DateTimeFormatter.ISO_LOCAL_DATE.toFormat());
        mFmt.setFormatByArgumentIndex(1, DateTimeFormatter.ISO_LOCAL_TIME.toFormat());
        mFmt.setFormatByArgumentIndex(2, DateTimeFormatter.ISO_LOCAL_DATE_TIME.toFormat());
        mFmt.setFormatByArgumentIndex(3, DateTimeFormatter.ISO_OFFSET_DATE_TIME.toFormat());
        mFmt.setFormatByArgumentIndex(4, DateTimeFormatter.ISO_INSTANT.toFormat());
        assertEquals("{0,temporal,iso-local-date}{1,temporal,iso-local-time}{2,temporal,iso-local-date-time}" +
                "{3,temporal,iso-offset-date-time}{4,temporal,iso-instant}", mFmt.toPattern());
    }

    // A dtFmt created with a Subformat pattern cannot be recognized
    // when toPattern is invoked
    @Test
    public void badToPatternTest() {
        var mFmt = new MessageFormat("{0}");
        // Non-recognizable DateTimeFormatter (ClassicFormat)
        mFmt.setFormatByArgumentIndex(0,
                DateTimeFormatter.ofPattern("yy").toFormat());
        // Default behavior of unrecognizable Formats is a FormatElement
        // in the form of { ArgumentIndex }
        assertEquals("{0}", mFmt.toPattern());
    }

    // Test that the dtFmt subformats format properly within the MessageFormat
    @ParameterizedTest
    @MethodSource
    public void formatTest(MessageFormat mFmt, Format cFmt) {
        // Variety of different date/time types to test formatting
        Object[] temporals = new Object[]{LocalDate.now(), LocalTime.now(),
                LocalDateTime.now(), OffsetDateTime.now(), Instant.now()};
        for (Object val : temporals) {
            // Wrap in Object array for MessageFormat
            Object[] wrappedVal = new Object[]{val};

            try {
                String mFmtted = mFmt.format(wrappedVal);
                // If current format can support the time object. Check equality of result
                assertEquals(mFmtted, "quux" + cFmt.format(val) + "quux");
            } catch (IllegalArgumentException ignored) {
                // Otherwise, ensure both throw IAE on unsupported field
                assertThrows(IllegalArgumentException.class, () -> cFmt.format(val));
            }
        }
    }

    // MessageFormats with patterns that contain the associated ClassicFormat
    private static Stream<Arguments> formatTest() {
        Locale loc = Locale.getDefault(Locale.Category.FORMAT);
        return Stream.of(
                // Built-in patterns
                Arguments.of(new MessageFormat("quux{0,temporal,iso-local-date}quux"),
                        DateTimeFormatter.ISO_LOCAL_DATE.toFormat()),
                Arguments.of(new MessageFormat("quux{0,temporal,iso-local-time}quux"),
                        DateTimeFormatter.ISO_LOCAL_TIME.toFormat()),
                Arguments.of(new MessageFormat("quux{0,temporal,iso-local-date-time}quux"),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME.toFormat()),
                Arguments.of(new MessageFormat("quux{0,temporal,iso-offset-date-time}quux"),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.toFormat()),
                Arguments.of(new MessageFormat("quux{0,temporal,iso-instant}quux"),
                        DateTimeFormatter.ISO_INSTANT.toFormat()),
                // Subformat Patterns
                Arguments.of(new MessageFormat("quux{0,temporal,MM/dd/yy}quux"),
                        DateTimeFormatter.ofPattern("MM/dd/yy", loc).toFormat()),
                Arguments.of(new MessageFormat("quux{0,temporal,HH:mm}quux"),
                        DateTimeFormatter.ofPattern("HH:mm", loc).toFormat())
        );
    }
}
