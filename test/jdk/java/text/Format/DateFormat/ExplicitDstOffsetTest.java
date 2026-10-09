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
 * @bug 8392995 8392991
 * @summary Ensure that a custom time zone does not look up CLDR metazone data via
 *      an explicit offset for daylight names during SimpleDateFormat formatting. Only
 *      a canonical JDK TimeZone should defer to the metazone data in such cases. Parsing
 *      should consult the explicit dstOffset of a zone when it exists.
 * @run junit ExplicitDstOffsetTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Any fixed standard strings correspond to CLDR data as of v48.2
public class ExplicitDstOffsetTest {

    private static final String AMERICA_VANCOUVER = "America/Vancouver";
    private static final String EUROPE_DUBLIN = "Europe/Dublin";
    private static final TimeZone JDK_AMERICA_VANCOUVER =
            TimeZone.getTimeZone(AMERICA_VANCOUVER);
    private static final TimeZone JDK_EUROPE_DUBLIN =
            TimeZone.getTimeZone(EUROPE_DUBLIN);

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

    // These instants are formatted/parsed under Europe/Dublin
    private static final List<Date> EXPLICIT_DST_OFFSET_DATES = List.of(
            // Historical daylight offset: +00:34:39
            Date.from(Instant.parse("1916-07-15T00:00:00Z")),
            // Formats to -> 1916-07-15 00:34:39 Greenwich Mean Time
            // -------------
            // Historical standard offset: −00:25:21
            Date.from(Instant.parse("1916-01-15T00:00:00Z")),
            // Formats to -> 1916-01-14 23:34:39 Greenwich Mean Time
            // -------------
            // Modern daylight offset: +01:00
            Date.from(Instant.parse("2026-04-04T23:00:00Z")),
            // Formats to -> 2026-04-05 00:00:00 Irish Standard Time
            // -------------
            // Modern standard offset: +00:00
            Date.from(Instant.parse("2026-12-05T00:00:00Z")),
            // Formats to -> 2026-12-05 00:00:00 Greenwich Mean Time
            //--------------
            // Test dates at the dst/std transition period
            // Dublin transitions on 10/25 at 2:00 AM
            // During daylight
            Date.from(Instant.parse("2026-10-25T00:30:00Z")),
            // Formats to 2026-10-25 01:30:00 Irish Standard Time
            // During standard
            Date.from(Instant.parse("2026-10-25T01:30:00Z"))
    );

    // Europe/Dublin uses explicit dstOffset. For such cases, the zone should be used to derive the offset
    // at the given date. When the date is during a transition period, then the matched name can be used
    // as a tiebreaker. This contrasts from normal parsing, which prefers using the matched name to determine
    // the offset.
    @ParameterizedTest
    @FieldSource("EXPLICIT_DST_OFFSET_DATES")
    void dstOffsetRoundTripParsingTest(Date before) throws ParseException {
        var format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss zzzz", Locale.US);
        format.setTimeZone(JDK_EUROPE_DUBLIN);
        String text = format.format(before);
        Date after = format.parse(text);
        assertEquals(before, after, "explicit dstOffset zone did not round trip correctly");
    }

    // These instants are formatted/parsed under Europe/Dublin
    private static final List<Arguments> EXPLICIT_DST_OFFSET_CONFLICTING_PATTERN_DATES = List.of(
            // Even though the text is in a transition period, which indicates the name should be
            // used to dictate std time, the explicit numeric offset should take precedence and produce the
            // resultant date in dst.
            Arguments.of("yyyy-MM-dd HH:mm:ss zzzz XXX",
                    "2026-10-25 01:30:00 Greenwich Mean Time +01:00",
                    Date.from(Instant.parse("2026-10-25T00:30:00Z"))),
            // Same as above, but in the other direction
            Arguments.of("yyyy-MM-dd HH:mm:ss XXX zzzz",
                    "2026-10-25 01:30:00 +01:00 Greenwich Mean Time",
                    Date.from(Instant.parse("2026-10-25T01:30:00Z")))
    );

    // Ensure that the new explicit dstOffset behavior during ambiguous transition period does not conflict
    // with a subsequent time zone pattern and vice versa. Latest should always win.
    @ParameterizedTest
    @FieldSource("EXPLICIT_DST_OFFSET_CONFLICTING_PATTERN_DATES")
    void dstOffsetConflictingZonesTest(String pattern, String text, Date date) throws ParseException {
        var format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(JDK_EUROPE_DUBLIN);
        var parsed = format.parse(text);
        assertEquals(date, parsed, "Pattern precedence incorrect for explicit dstOffset");
    }

    // Ensure that explicit dstOffset is taken into account even on century changes.
    @Test
    void dstOffsetCenturyTest() throws ParseException {
        var format = new SimpleDateFormat("yy-MM-dd HH:mm:ss zzzz", Locale.US);
        format.setTimeZone(JDK_EUROPE_DUBLIN);
        format.set2DigitYearStart(Date.from(Instant.parse("2026-10-28T00:00:00Z")));
        var before = Date.from(Instant.parse("2126-10-27T00:30:00Z"));
        var formatted = format.format(before);
        // Formats as -> 26-10-27 01:30:00 Irish Standard Time
        // When we parse it, 2026/10/27 is before the century start, so 100 years is incremented.
        // This moves the new date to 2126-10-27 01:30:00 Irish Standard Time. This lands
        // on a transition date, and we need to use the Irish Standard Time naming to determine
        // the appropriate offset. Otherwise, the calendar would default to the standard time,
        // and the date produced would be "2126-10-27T01:30:00Z"
        assertEquals(before, format.parse(formatted),
                "explicit dstOffset not taken into account for next century");
    }
}
