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
 * @bug 8324665
 * @summary Checks if SPACE_SEPARATOR are correctly parsed in lenient mode
 * @run junit LenientSpaceParsingTest
 */
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.stream.Stream;

public class LenientSpaceParsingTest {
    @MethodSource
    private static Stream<Arguments> strictSpaces() {
        // input, pattern
        return Stream.of(
                Arguments.of("00\u002000", "H\u0020m"),
                Arguments.of("00\u202f00", "H\u202fm"),
                Arguments.of("00\u00a000", "H\u00a0m"),
                Arguments.of("00\u0020\u202f\u0020\u00a000", "H\u0020\u202f\u0020\u00a0m")
        );
    }

    @MethodSource
    private static Stream<Arguments> lenientSpaces() {
        // input, pattern
        return Stream.of(
                Arguments.of("00\u002000", "H\u202fm"),
                Arguments.of("00\u202f00", "H\u0020m"),
                Arguments.of("00\u00a000", "H\u0020m"),
                Arguments.of("00\u002000", "H\u00a0m"),
                Arguments.of("00\u0020\u202f\u0020\u00a000", "H\u0020\u0020\u0020\u0020m"),
                Arguments.of("00\u0020\u202f\u0020\u00a000", "H\u202f\u00a0\u202f\u00a0m")
        );
    }

    @MethodSource
    private static Stream<Arguments> nonSpaces() {
        // input, pattern
        return Stream.of(
                Arguments.of("00a00", "H\u202fm"),
                Arguments.of("00a00", "H\u00a0m"),
                Arguments.of("00a00", "H\u0020m"),
                Arguments.of("00aa00", "H\u0020\u0020m"),
                Arguments.of("00aa00", "H\u00a0\u202fm")
        );
    }

    @ParameterizedTest
    @MethodSource({"strictSpaces", "lenientSpaces"})
    public void checkDateTimeFormatter_Lenient(String input, String pattern) {
        new DateTimeFormatterBuilder().parseLenient().appendPattern(pattern).toFormatter().parse(input);
    }

    @ParameterizedTest
    @MethodSource("nonSpaces")
    public void checkDateTimeFormatter_Lenient_Exception(String input, String pattern) {
        var dtf = new DateTimeFormatterBuilder().parseLenient().appendPattern(pattern).toFormatter();
        assertThrows(DateTimeParseException.class, () -> {
            dtf.parse(input);
        });
    }

    @ParameterizedTest
    @MethodSource("strictSpaces")
    public void checkDateTimeFormatter_Strict(String input, String pattern) {
        new DateTimeFormatterBuilder().parseStrict().appendPattern(pattern).toFormatter().parse(input);
    }

    @ParameterizedTest
    @MethodSource({"lenientSpaces", "nonSpaces"})
    public void checkDateTimeFormatter_Strict_Exception(String input, String pattern) {
        var dtf = new DateTimeFormatterBuilder().parseStrict().appendPattern(pattern).toFormatter();
        assertThrows(DateTimeParseException.class, () -> {
            dtf.parse(input);
        });
    }

    @ParameterizedTest
    @MethodSource({"strictSpaces", "lenientSpaces"})
    public void checkSimpleDateFormat_Lenient(String input, String pattern) throws ParseException {
        new SimpleDateFormat(pattern).parse(input);
    }

    @ParameterizedTest
    @MethodSource("nonSpaces")
    public void checkSimpleDateFormat_Lenient_Exception(String input, String pattern) {
        var sdf = new SimpleDateFormat(pattern);
        assertThrows(ParseException.class, () -> {
            sdf.parse(input);
        });
    }

    @ParameterizedTest
    @MethodSource("strictSpaces")
    public void checkSimpleDateFormat_Strict(String input, String pattern) throws ParseException {
        var sdf = new SimpleDateFormat(pattern);
        sdf.setLenient(false);
        sdf.parse(input);
    }

    @ParameterizedTest
    @MethodSource({"lenientSpaces", "nonSpaces"})
    public void checkSimpleDateFormat_Strict_Exception(String input, String pattern) {
        var sdf = new SimpleDateFormat(pattern);
        sdf.setLenient(false);
        assertThrows(ParseException.class, () -> {
            sdf.parse(input);
        });
    }
}
