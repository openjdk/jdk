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
 * @bug 8392995
 * @summary Ensure that a custom time zone does not look up CLDR metazone data via
 *      an explicit offset for daylight names during SimpleDateFormat formatting. Only
 *      a clean standard JDK TimeZone should defer to the metazone data in such cases.
 * @run junit CustomZoneTest
 */

import org.junit.jupiter.api.Test;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Any fixed standard strings correspond to CLDR data as of v48.2
public class CustomZoneTest {

    private final TimeZone JDK_AMERICA_VANCOUVER =
            TimeZone.getTimeZone("America/Vancouver");

    // Fixed raw offset (that mimics daylight savings for America/Vancouver)
    // but in actuality has no daylight savings rules and is named "America/Vancouver".
    private final TimeZone CUSTOM_AMERICA_VANCOUVER =
            new SimpleTimeZone(-7 * 60 * 60 * 1000, "America/Vancouver");

    private final String[][] ZONE_STRINGS = new String[][] {{
            "America/Vancouver",
            "CUSTOM_STANDARD",
            "CS",
            "CUSTOM_DAYLIGHT",
            "CD"
    }};

    // Date in normal standard time range before the permanent transition
    private final Date STANDARD_VANCOUVER_DATE = Date.from(
            ZonedDateTime.of(2025, 12, 1, 0, 0, 0, 0,
                    ZoneId.of("America/Vancouver")).toInstant());
    // Date after the permanent transition
    private final Date DAYLIGHT_VANCOUVER_DATE = Date.from(
            ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0,
                    ZoneId.of("America/Vancouver")).toInstant());

    // Custom zone with custom strings. Custom zone has no DST, and all formatted
    // names should reflect the custom name -> "CUSTOM_STANDARD".
    @Test
    void customZoneWithStringsTest() {
        DateFormatSymbols symbols = new DateFormatSymbols(Locale.US);
        symbols.setZoneStrings(ZONE_STRINGS);
        SimpleDateFormat format = new SimpleDateFormat("zzzz", symbols);
        format.setTimeZone(CUSTOM_AMERICA_VANCOUVER);
        assertEquals("CUSTOM_STANDARD", format.format(STANDARD_VANCOUVER_DATE));
        assertEquals("CUSTOM_STANDARD", format.format(DAYLIGHT_VANCOUVER_DATE));
    }

    // Custom zone without custom strings. Custom zone has no DST, and all formatted
    // names should reflect the standard name -> "Pacific Standard Time".
    @Test
    void customZoneWithoutStringsTest() {
        DateFormatSymbols symbols = new DateFormatSymbols(Locale.US);
        SimpleDateFormat format = new SimpleDateFormat("zzzz", symbols);
        format.setTimeZone(CUSTOM_AMERICA_VANCOUVER);
        assertEquals("Pacific Standard Time", format.format(STANDARD_VANCOUVER_DATE));
        assertEquals("Pacific Standard Time", format.format(DAYLIGHT_VANCOUVER_DATE));
    }

    // Standard JDK zone without custom strings. Expect the default naming for pre/post transition.
    @Test
    void standardZoneWithoutStringsTest() {
        DateFormatSymbols symbols = new DateFormatSymbols(Locale.US);
        SimpleDateFormat format = new SimpleDateFormat("zzzz", symbols);
        format.setTimeZone(JDK_AMERICA_VANCOUVER);
        assertEquals("Pacific Standard Time", format.format(STANDARD_VANCOUVER_DATE));
        assertEquals("Pacific Daylight Time", format.format(DAYLIGHT_VANCOUVER_DATE));
    }

    // Standard JDK zone with custom strings. Expect the correct custom naming for pre/post transition.
    @Test
    void standardZoneWithStringsTest() {
        DateFormatSymbols symbols = new DateFormatSymbols(Locale.US);
        symbols.setZoneStrings(ZONE_STRINGS);
        SimpleDateFormat format = new SimpleDateFormat("zzzz", symbols);
        format.setTimeZone(JDK_AMERICA_VANCOUVER);
        assertEquals("CUSTOM_STANDARD", format.format(STANDARD_VANCOUVER_DATE));
        assertEquals("CUSTOM_DAYLIGHT", format.format(DAYLIGHT_VANCOUVER_DATE));
    }

    // Standard JDK zone that has been modified. Since it is modified, it should not
    // determine daylight time by deferring to the CLDR metazone data.
    @Test
    void modifiedStandardZoneTest() {
        DateFormatSymbols symbols = new DateFormatSymbols(Locale.US);
        SimpleDateFormat format = new SimpleDateFormat("zzzz", symbols);
        // Grab a standard tz for America/New_York, set the ID to America/Vancouver.
        var modifiedJDKZone = TimeZone.getTimeZone("America/New_York");
        modifiedJDKZone.setID("America/Vancouver");
        format.setTimeZone(modifiedJDKZone);
        // If the metazone mapping was used, it would map the non-matching
        // New York offset to the America/Vancouver ID, determining standard time and print Pacific
        // Standard Time. If it correctly does not use the metazone mapping, it should simply
        // use the underlying DST offset to determine it is in daylight time.
        assertEquals("Pacific Daylight Time", format.format(Date.from(
                ZonedDateTime.of(2020, 7, 1, 0, 0, 0, 0,
                        ZoneId.of("America/New_York")).toInstant())));
    }
}
