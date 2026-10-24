/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8354548
 * @modules jdk.localedata
 * @summary Tests DateTimeFormatter format/parse round trips; some locale
 *      may contain reserved characters, eg '#', which should correctly
 *      be escaped
 * @run junit DateTimeRoundTripTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class DateTimeRoundTripTest {
    private static Stream<Locale> availableLocales() {
        return Locale.availableLocales();
    }

    @ParameterizedTest
    @MethodSource("availableLocales")
    public void testDateTimeRoundTripTest(Locale locale) {
        Arrays.stream(FormatStyle.values()).forEach(style -> {
            assertDoesNotThrow(() ->
                Instant.parse("2018-07-16T23:58:59.000000200Z")
                    .atZone(ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ofLocalizedDate(style).withLocale(locale)));
        });
    }
}
