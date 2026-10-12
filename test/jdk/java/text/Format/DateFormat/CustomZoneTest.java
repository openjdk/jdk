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
 *      a canonical JDK TimeZone should defer to the metazone data in such cases.
 * @run junit CustomZoneTest
 */

import org.junit.jupiter.api.Test;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Any fixed standard strings correspond to CLDR data as of v48.2
public class CustomZoneTest {

    private static final String AMERICA_VANCOUVER = "America/Vancouver";
    private static final TimeZone JDK_AMERICA_VANCOUVER =
            TimeZone.getTimeZone(AMERICA_VANCOUVER);

    // Fixed raw offset (that mimics daylight savings for America/Vancouver)
    // but in actuality has no daylight savings rules and is named "America/Vancouver".
    private static final TimeZone CUSTOM_AMERICA_VANCOUVER =
            new SimpleTimeZone(-7 * 60 * 60 * 1000, AMERICA_VANCOUVER);

    private static final String[][] ZONE_STRINGS = new String[][] {{
            AMERICA_VANCOUVER,
            "CUSTOM_STANDARD",
            "CS",
            "CUSTOM_DAYLIGHT",
            "CD"
    }};

    // British Columbia (including Vancouver) permanently switched to dst on March 8, 2026.
    // Date in normal standard time range before the permanent transition.
    private static final Date STANDARD_VANCOUVER_DATE = Date.from(
            ZonedDateTime.of(2025, 12, 1, 0, 0, 0, 0,
                    ZoneId.of(AMERICA_VANCOUVER)).toInstant());
    // Date after the permanent transition. We expect all zones that take DST into account to format
    // this date using a daylight name.
    private static final Date DAYLIGHT_VANCOUVER_DATE = Date.from(
            ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0,
                    ZoneId.of(AMERICA_VANCOUVER)).toInstant());

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
        // TimeZone used by sdf has New_York rules with Vancouver ID.
        var modifiedJDKZone = TimeZone.getTimeZone("America/New_York");
        modifiedJDKZone.setID(AMERICA_VANCOUVER);
        format.setTimeZone(modifiedJDKZone);
        // If we did not check that the zone was canonical, SDF will grab
        // the explicit dst offset for Vancouver, and see if it equals the
        // dst offset for the zone. Since the dst offset for the zone is for New_York,
        // the match would fail and daylight would be incorrectly determined as false.
        assertEquals("Pacific Daylight Time", format.format(
                Date.from(Instant.parse("2020-07-01T12:00:00Z"))));
    }
}
