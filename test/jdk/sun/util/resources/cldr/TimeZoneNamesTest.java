/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8181157 8202537
 * @modules jdk.localedata
 * @summary Checks CLDR time zone names are generated correctly at runtime
 * @run testng/othervm -Djava.locale.providers=CLDR TimeZoneNamesTest
 */

import static org.testng.Assert.assertEquals;

import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.TimeZone;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class TimeZoneNamesTest {

    @DataProvider(name="noResourceTZs")
    Object[][] data() {
        return new Object[][] {
            // tzid, locale, style, expected

            // This list is as of CLDR version 33, and should be examined
            // on the CLDR data upgrade.

            // no "metazone" zones
            {"Asia/Srednekolymsk",      Locale.US, "Srednekolymsk Time",
                                                    "SRET",
                                                    "Srednekolymsk Daylight Time",
                                                    "SREDT",
                                                    "Srednekolymsk Time",
                                                    "SRET"},
            {"Asia/Srednekolymsk",      Locale.FRANCE, "Srednekolymsk (heure standard)",
                                                    "UTC+11:00",
                                                    "Srednekolymsk (heure standard)",
                                                    "UTC+11:00",
                                                    "heure : Srednekolymsk",
                                                    "UTC+11:00"},
            {"America/Punta_Arenas",    Locale.US, "Punta Arenas Standard Time",
                                                    "GMT-03:00",
                                                    "Punta Arenas Standard Time",
                                                    "GMT-03:00",
                                                    "Punta Arenas Time",
                                                    "GMT-03:00"},
            {"America/Punta_Arenas",    Locale.FRANCE, "Punta Arenas (heure standard)",
                                                    "UTC\u221203:00",
                                                    "Punta Arenas (heure standard)",
                                                    "UTC\u221203:00",
                                                    "heure : Punta Arenas",
                                                    "UTC\u221203:00"},
            {"Asia/Famagusta",          Locale.US, "Eastern European Time",
                                                    "EET",
                                                    "Eastern European Summer Time",
                                                    "EEST",
                                                    "Eastern European Time",
                                                    "EET"},
            {"Asia/Famagusta",          Locale.FRANCE, "Famagouste (heure standard)",
                                                    "UTC+02:00",
                                                    "Famagouste (heure standard)",
                                                    "UTC+03:00",
                                                    "heure : Famagouste",
                                                    "UTC+02:00"},
            {"Europe/Astrakhan",        Locale.US, "Astrakhan Standard Time",
                                                    "GMT+04:00",
                                                    "Astrakhan Standard Time",
                                                    "GMT+04:00",
                                                    "Astrakhan Time",
                                                    "GMT+04:00"},
            {"Europe/Astrakhan",        Locale.FRANCE, "Astrakhan (heure standard)",
                                                    "UTC+04:00",
                                                    "Astrakhan (heure standard)",
                                                    "UTC+04:00",
                                                    "heure : Astrakhan",
                                                    "UTC+04:00"},
            {"Europe/Saratov",          Locale.US, "Saratov Standard Time",
                                                    "GMT+04:00",
                                                    "Saratov Standard Time",
                                                    "GMT+04:00",
                                                    "Saratov Time",
                                                    "GMT+04:00"},
            {"Europe/Saratov",          Locale.FRANCE, "Saratov (heure standard)",
                                                    "UTC+04:00",
                                                    "Saratov (heure standard)",
                                                    "UTC+04:00",
                                                    "heure : Saratov",
                                                    "UTC+04:00"},
            {"Europe/Ulyanovsk",        Locale.US, "Ulyanovsk Standard Time",
                                                    "GMT+04:00",
                                                    "Ulyanovsk Standard Time",
                                                    "GMT+04:00",
                                                    "Ulyanovsk Time",
                                                    "GMT+04:00"},
            {"Europe/Ulyanovsk",        Locale.FRANCE, "Oulianovsk (heure standard)",
                                                    "UTC+04:00",
                                                    "Oulianovsk (heure standard)",
                                                    "UTC+04:00",
                                                    "heure : Oulianovsk",
                                                    "UTC+04:00"},
            {"Pacific/Bougainville",    Locale.US, "Bougainville Standard Time",
                                                    "BST",
                                                    "Bougainville Daylight Time",
                                                    "BST",
                                                    "Bougainville Time",
                                                    "BT"},
            {"Pacific/Bougainville",    Locale.FRANCE, "Bougainville (heure standard)",
                                                    "UTC+11:00",
                                                    "Bougainville (heure standard)",
                                                    "UTC+11:00",
                                                    "heure : Bougainville",
                                                    "UTC+11:00"},

        };
    }


    @Test(dataProvider="noResourceTZs")
    public void test_tzNames(String tzid, Locale locale, String lstd, String sstd, String ldst, String sdst, String lgen, String sgen) {
        // Standard time
        assertEquals(TimeZone.getTimeZone(tzid).getDisplayName(false, TimeZone.LONG, locale), lstd);
        assertEquals(TimeZone.getTimeZone(tzid).getDisplayName(false, TimeZone.SHORT, locale), sstd);

        // daylight saving time
        assertEquals(TimeZone.getTimeZone(tzid).getDisplayName(true, TimeZone.LONG, locale), ldst);
        assertEquals(TimeZone.getTimeZone(tzid).getDisplayName(true, TimeZone.SHORT, locale), sdst);

        // generic name
        assertEquals(ZoneId.of(tzid).getDisplayName(TextStyle.FULL, locale), lgen);
        assertEquals(ZoneId.of(tzid).getDisplayName(TextStyle.SHORT, locale), sgen);
    }
}
