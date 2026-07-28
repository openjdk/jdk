/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8181157 8202537 8234347 8236548 8261279 8322647 8174269 8346948
 *      8354548 8381379 8382020 8384043 8371842
 * @modules jdk.localedata
 * @summary Checks CLDR time zone names are generated correctly at
 * either build or runtime
 * @run junit TimeZoneNamesTest
 */

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TimeZoneNamesTest {

    private static Object[][] sampleTZs() {
        return new Object[][] {
            // tzid, locale, style, expected

            // This list is as of CLDR version 48, and should be examined
            // on the CLDR data upgrade.

            // no "metazone" zones (some of them were assigned metazones
            // over time, thus they are not "generated" per se
            {"Asia/Srednekolymsk", Locale.US,
                "Magadan Standard Time",
                "GMT+11:00",
                "Magadan Summer Time",
                "GMT+12:00",
                "Magadan Time",
                "GMT+11:00"},
            {"Asia/Srednekolymsk", Locale.FRANCE,
                "heure normale de Magadan",
                "UTC+11:00",
                "heure d’été de Magadan",
                "UTC+12:00",
                "heure de Magadan",
                "UTC+11:00"},
            {"America/Punta_Arenas", Locale.US,
                "Punta Arenas Standard Time",
                "GMT-03:00",
                "Punta Arenas Daylight Time",
                "GMT-02:00",
                "Punta Arenas Time",
                "GMT-03:00"},
            {"America/Punta_Arenas", Locale.FRANCE,
                "Punta Arenas (heure standard)",
                "UTC−03:00",
                "Punta Arenas (heure d’été)",
                "UTC−02:00",
                "heure : Punta Arenas",
                "UTC−03:00"},
            {"Asia/Famagusta", Locale.US,
                "Eastern European Standard Time",
                "EET",
                "Eastern European Summer Time",
                "EEST",
                "Eastern European Time",
                "EET"},
            {"Asia/Famagusta", Locale.FRANCE,
                "heure normale d’Europe de l’Est",
                "EET",
                "heure d’été d’Europe de l’Est",
                "EEST",
                "heure d’Europe de l’Est",
                "EET"},
            {"Europe/Astrakhan", Locale.US,
                "Samara Standard Time",
                "GMT+04:00",
                "Samara Summer Time",
                "GMT+05:00",
                "Samara Time",
                "GMT+04:00"},
            {"Europe/Astrakhan", Locale.FRANCE,
                "heure normale de Samara",
                "UTC+04:00",
                "heure d’été de Samara",
                "UTC+05:00",
                "heure de Samara",
                "UTC+04:00"},
            {"Europe/Saratov", Locale.US,
                "Samara Standard Time",
                "GMT+04:00",
                "Samara Summer Time",
                "GMT+05:00",
                "Samara Time",
                "GMT+04:00"},
            {"Europe/Saratov", Locale.FRANCE,
                "heure normale de Samara",
                "UTC+04:00",
                "heure d’été de Samara",
                "UTC+05:00",
                "heure de Samara",
                "UTC+04:00"},
            {"Europe/Ulyanovsk", Locale.US,
                "Samara Standard Time",
                "GMT+04:00",
                "Samara Summer Time",
                "GMT+05:00",
                "Samara Time",
                "GMT+04:00"},
            {"Europe/Ulyanovsk", Locale.FRANCE,
                "heure normale de Samara",
                "UTC+04:00",
                "heure d’été de Samara",
                "UTC+05:00",
                "heure de Samara",
                "UTC+04:00"},
            {"Pacific/Bougainville", Locale.US,
                "Bougainville Standard Time",
                "GMT+11:00",
                "Bougainville Daylight Time",
                "GMT+11:00",
                "Bougainville Time",
                "GMT+11:00"},
            {"Pacific/Bougainville", Locale.FRANCE,
                "Bougainville (heure standard)",
                "UTC+11:00",
                "Bougainville (heure d’été)",
                "UTC+11:00",
                "heure : Bougainville",
                "UTC+11:00"},
            {"Europe/Istanbul", Locale.US,
                "Türkiye Standard Time",
                "GMT+03:00",
                "Türkiye Summer Time",
                "GMT+04:00",
                "Türkiye Time",
                "GMT+03:00"},
            {"Europe/Istanbul", Locale.FRANCE,
                "heure normale de Turquie",
                "UTC+03:00",
                "heure avancée de Turquie",
                "UTC+04:00",
                "heure de Turquie",
                "UTC+03:00"},
            {"Asia/Istanbul", Locale.US,
                "Türkiye Standard Time",
                "GMT+03:00",
                "Türkiye Summer Time",
                "GMT+04:00",
                "Türkiye Time",
                "GMT+03:00"},
            {"Asia/Istanbul", Locale.FRANCE,
                "heure normale de Turquie",
                "UTC+03:00",
                "heure avancée de Turquie",
                "UTC+04:00",
                "heure de Turquie",
                "UTC+03:00"},
            {"Turkey", Locale.US,
                "Türkiye Standard Time",
                "GMT+03:00",
                "Türkiye Summer Time",
                "GMT+04:00",
                "Türkiye Time",
                "GMT+03:00"},
            {"Turkey", Locale.FRANCE,
                "heure normale de Turquie",
                "UTC+03:00",
                "heure avancée de Turquie",
                "UTC+04:00",
                "heure de Turquie",
                "UTC+03:00"},

            // Short names derived from TZDB at build time
            {"Europe/Lisbon", Locale.US,
                "Western European Standard Time",
                "WET",
                "Western European Summer Time",
                "WEST",
                "Western European Time",
                "WET"},
            {"Atlantic/Azores", Locale.US,
                "Azores Standard Time",
                "GMT-01:00",
                "Azores Summer Time",
                "GMT",
                "Azores Time",
                "GMT-01:00"},
            {"Australia/Perth", Locale.US,
                "Australian Western Standard Time",
                "AWST",
                "Australian Western Daylight Time",
                "AWDT",
                "Australian Western Time",
                "AWT"},
            {"Africa/Harare", Locale.US,
                "Central Africa Time",
                "CAT",
                "Harare Daylight Time",
                "CAT",
                "Harare Time",
                "CAT"},
            {"Europe/Dublin", Locale.US,
                "Greenwich Mean Time",
                "GMT",
                "Irish Standard Time",
                "IST",
                "Dublin Time",
                "GMT"},
            {"Pacific/Gambier", Locale.US,
                "Gambier Time",
                "GMT-09:00",
                "Gambier Daylight Time",
                "GMT-09:00",
                "Gambier Time",
                "GMT-09:00"},
            {"America/New_York", Locale.US,
                "Eastern Standard Time",
                "EST",
                "Eastern Daylight Time",
                "EDT",
                "Eastern Time",
                "ET"},
            {"America/New_York", Locale.GERMAN,
                "Nordamerikanische Ostküsten-Normalzeit",
                "EST",
                "Nordamerikanische Ostküsten-Sommerzeit",
                "EDT",
                "Nordamerikanische Ostküstenzeit",
                "ET"},
            {"America/New_York", Locale.JAPANESE,
                "米国東部標準時",
                "EST",
                "米国東部夏時間",
                "EDT",
                "米国東部時間",
                "ET"},
            {"America/New_York", Locale.of("ru"),
                "Восточная Америка, стандартное время",
                "EST",
                "Восточная Америка, летнее время",
                "EDT",
                "Восточная Америка",
                "ET"},

            // Hawaii/Aleutian
            //
            // Note that CLDR v48 only contains the standard names in "Hawaii"
            // metazone. Other long names are synthesized, and short names are
            // from TZDB. "America/Adak" reflects the "Hawaii_Aleutian" metazone
            // names.
            {"Pacific/Honolulu", Locale.US,
                "Hawaii-Aleutian Standard Time",
                "HST",
                "Honolulu Daylight Time",
                "HST",
                "Honolulu Time",
                "HST"},
            {"America/Adak", Locale.US,
                "Hawaii-Aleutian Standard Time",
                "HAST",
                "Hawaii-Aleutian Daylight Time",
                "HADT",
                "Hawaii-Aleutian Time",
                "HAT"},
        };
    }

    private static Stream<Arguments> explicitDstOffsets() {
        return Stream.of(
            Arguments.of(ZonedDateTime.of(2026, 4, 5, 0, 0, 0, 0, ZoneId.of("Europe/Dublin")), "Irish Standard Time"),
            Arguments.of(ZonedDateTime.of(2026, 12, 5, 0, 0, 0, 0, ZoneId.of("Europe/Dublin")), "Greenwich Mean Time"),
            Arguments.of(ZonedDateTime.of(2026, 4, 5, 0, 0, 0, 0, ZoneId.of("Eire")), "Irish Standard Time"),
            Arguments.of(ZonedDateTime.of(2026, 12, 5, 0, 0, 0, 0, ZoneId.of("Eire")), "Greenwich Mean Time"),
            Arguments.of(ZonedDateTime.of(2026, 4, 5, 0, 0, 0, 0, ZoneId.of("America/Vancouver")), "Pacific Daylight Time"),
            Arguments.of(ZonedDateTime.of(2026, 12, 5, 0, 0, 0, 0, ZoneId.of("America/Vancouver")), "Pacific Daylight Time")
        );
    }

    @ParameterizedTest
    @MethodSource("sampleTZs")
    public void test_tzNames(String tzid, Locale locale, String lstd, String sstd, String ldst, String sdst, String lgen, String sgen) {
        // Standard time
        assertEquals(lstd, TimeZone.getTimeZone(tzid).getDisplayName(false, TimeZone.LONG, locale));
        assertEquals(sstd, TimeZone.getTimeZone(tzid).getDisplayName(false, TimeZone.SHORT, locale));

        // daylight saving time
        assertEquals(ldst, TimeZone.getTimeZone(tzid).getDisplayName(true, TimeZone.LONG, locale));
        assertEquals(sdst, TimeZone.getTimeZone(tzid).getDisplayName(true, TimeZone.SHORT, locale));

        // generic name
        assertEquals(lgen, ZoneId.of(tzid).getDisplayName(TextStyle.FULL, locale));
        assertEquals(sgen, ZoneId.of(tzid).getDisplayName(TextStyle.SHORT, locale));
    }

    // Make sure getZoneStrings() returns non-empty string array
    @Test
    public void test_getZoneStrings() {
        assertFalse(
            Locale.availableLocales()
                .limit(30)
                .peek(l -> System.out.println("Locale: " + l))
                .map(l -> DateFormatSymbols.getInstance(l).getZoneStrings())
                .flatMap(Arrays::stream)
                .peek(names -> System.out.println("    tz: " + names[0]))
                .flatMap(Arrays::stream)
                .anyMatch(name -> Objects.isNull(name) || name.isEmpty()),
            "getZoneStrings() returned array containing non-empty string element(s)");
    }

    // Explicit metazone dst offset test. As of CLDR v48, only Europe/Dublin utilizes
    // this attribute, but will be used for America/Vancouver once CLDR adopts the
    // explicit offset for that zone, which warrants the test data modification.
    @ParameterizedTest
    @MethodSource("explicitDstOffsets")
    public void test_ExplicitMetazoneOffsets(ZonedDateTime zdt, String expected) {
        // java.time
        assertEquals(expected, DateTimeFormatter.ofPattern("zzzz").format(zdt));

        // java.text/util
        var sdf = new SimpleDateFormat("zzzz");
        sdf.setTimeZone(TimeZone.getTimeZone(zdt.getZone()));
        assertEquals(expected, sdf.format(Date.from(zdt.toInstant())));
    }
}
