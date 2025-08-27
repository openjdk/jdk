/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8202088 8207152 8217609 8219890 8358819
 * @summary Test the localized Japanese calendar names, such as
 *      the Reiwa Era names (May 1st. 2019-), or the Gan-nen text
 * @modules jdk.localedata
 * @run junit JapaneseCalendarNameTest
 */

import static java.util.Calendar.*;
import static java.util.Locale.*;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JapaneseCalendarNameTest {
    private static final Calendar c = new Calendar.Builder()
            .setCalendarType("japanese")
            .setFields(ERA, 5, YEAR, 1, MONTH, MAY, DAY_OF_MONTH, 1)
            .build();
    private static final Locale JAJPJP = Locale.of("ja", "JP", "JP");
    private static final Locale JCAL = Locale.forLanguageTag("ja-u-ca-japanese");

    private static Stream<Arguments> reiwaEraNames() {
        return Stream.of(
            // type, locale, name
            Arguments.of(LONG, JAPAN, "令和"),
            Arguments.of(LONG, US, "Reiwa"),
            Arguments.of(LONG, CHINA, "令和"),
            Arguments.of(SHORT, JAPAN, "令和"),
            Arguments.of(SHORT, US, "Reiwa"),
            Arguments.of(SHORT, CHINA, "令和")
        );
    }

    @ParameterizedTest
    @MethodSource("reiwaEraNames")
    void testReiwaEraName(int type, Locale locale, String expected) {
        assertEquals(expected, c.getDisplayName(ERA, type, locale));
    }

    private static Stream<Arguments> gannen() {
        return Stream.of(
            // format,
            // formatted text
            Arguments.of(DateFormat.getDateInstance(DateFormat.FULL, JAJPJP),
                "令和元年5月1日水曜日"),
            Arguments.of(DateFormat.getDateInstance(DateFormat.FULL, JCAL),
                "令和元年5月1日水曜日"),
            Arguments.of(DateFormat.getDateInstance(DateFormat.LONG, JAJPJP),
                "令和元年5月1日"),
            Arguments.of(DateFormat.getDateInstance(DateFormat.LONG, JCAL),
                "令和元年5月1日"),
            Arguments.of(DateFormat.getDateInstance(DateFormat.MEDIUM, JAJPJP),
                "令和1年5月1日"),
            Arguments.of(DateFormat.getDateInstance(DateFormat.MEDIUM, JCAL),
                "令和1年5月1日"),
            Arguments.of(DateFormat.getDateInstance(DateFormat.SHORT, JAJPJP),
                "令和1/5/1"),
            Arguments.of(DateFormat.getDateInstance(DateFormat.SHORT, JCAL),
                "令和1/5/1")
        );
    }

    @ParameterizedTest
    @MethodSource("gannen")
    void testGannenFormat(DateFormat df, String expected) {
        assertEquals(expected, df.format(c.getTime()));
    }

    @ParameterizedTest
    @MethodSource("gannen")
    void testGannenParse(DateFormat df, String formatted) throws ParseException {
        assertEquals(c.getTime(), df.parse(formatted));
    }
}
