/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6530336 6537997 8008577 8174269 8333582
 * @library /java/text/testlib
 * @run junit/othervm Bug6530336
 */

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Bug6530336 {

    private static final Locale[] locales = Locale.getAvailableLocales();
    private static final TimeZone[] timezones = {
            TimeZone.getTimeZone("America/New_York"),
            TimeZone.getTimeZone("America/Denver"),
    };
    private static final TimeZone timezone_LA = TimeZone.getTimeZone("America/Los_Angeles");
    private static final String[] expected = {
            "Sun Jul 15 12:00:00 PDT 2007",
            "Sun Jul 15 14:00:00 PDT 2007",
    };
    private static final Date[] dates = new Date[2];

    @BeforeAll
    static void setup() {
        TimeZone.setDefault(timezone_LA);
    }

    @ParameterizedTest
    @FieldSource("locales")
    void test(Locale locale) {
        Assumptions.assumeTrue(TestUtils.usesGregorianCalendar(locale),
                locale + " does not use a Gregorian calendar");
        Locale.setDefault(locale);

        for (int j = 0; j < timezones.length; j++) {
            Calendar cal = Calendar.getInstance(timezones[j]);
            cal.set(2007, 6, 15, 15, 0, 0);
            dates[j] = cal.getTime();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");

        for (int j = 0; j < timezones.length; j++) {
            sdf.setTimeZone(timezones[j]);
            String date = sdf.format(dates[j]);
            // CLDR localizes GMT format into for some locales. Ignore those cases
            if (date.matches(".*GMT[\\s+-]\\D.*") ||
                    date.contains("UTC") ||
                    date.contains("TMG") || // Interlingue
                    date.contains("ߜ߭ߕߖ") || // N’Ko
                    date.contains("ꋧꃅꎕꏦꄮꈉ") || // Sichuan Yi, Nuosu
                    date.contains("گرینیچ")) { // Central Kurdish
                continue;
            }
            sdf.setTimeZone(timezone_LA);
            String date_LA = assertDoesNotThrow(() -> sdf.parse(date).toString());
            assertEquals(expected[j], date_LA,
                    "Got wrong Pacific time (%s) for (%s) in %s in %s.".formatted(date_LA, date, locale, timezones[j]));
        }
    }
}
