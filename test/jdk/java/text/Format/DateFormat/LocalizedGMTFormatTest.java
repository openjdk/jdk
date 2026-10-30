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
 * @bug 8384532
 * @summary Verifies parsing of localized GMT offsets in SimpleDateFormat
 * @modules jdk.localedata
 * @run junit LocalizedGMTFormatTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class LocalizedGMTFormatTest{
    // Assumes that CLDR has localized GMT format for the language "Irish Gaelic"
    private static final Locale LOCALE_GA = Locale.of("ga");

    @ParameterizedTest
    @CsvSource({
            "MAG+01:00, -3600000",
            "MAG, 0",
            "MAG-01:00,  3600000",
            "mag+01:00, -3600000",
            "mag, 0",
            "mag-01:00,  3600000",
    })
    void testParse(String text, long expectedMillis) {
        for (String pattern : List.of("z", "zzzz", "Z")) {
            var format = new SimpleDateFormat(pattern, LOCALE_GA);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            var pos = new ParsePosition(0);
            String message = pattern + ": " + text;

            assertEquals(new Date(expectedMillis), format.parse(text, pos), message);
            assertEquals(text.length(), pos.getIndex(), message);
        }
    }
}
