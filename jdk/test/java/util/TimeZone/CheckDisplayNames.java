/*
 * Copyright (c) 2002, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6405639 8008577
 * @summary Validate timezone display names in
 *          src/java.base/share/classes/sun/util/resources/TimeZoneNames.java.
 * @modules java.base/sun.util.resources
 * @compile -XDignore.symbol.file CheckDisplayNames.java
 * @run main/othervm -Djava.locale.providers=COMPAT,SPI CheckDisplayNames
 */

import java.util.*;
import sun.util.resources.TimeZoneNames;

/**
 * CheckDisplayNames checks all available time zones in the Java run
 * time environment and sees if those have their display names besides doing
 * some other test cases. It outputs time zones that don't have display names
 * if -source option is specified.
 * <blockquote>
 * <pre>
 *    Usage: java CheckDisplayNames [-source]
 *              -source ... produces source code for editing TimeZoneNames.java.
 * </pre>
 * </blockquote>
 */
public class CheckDisplayNames {

    private static boolean err = false;
    private static boolean src = false;

    private static Locale[] locales = Locale.getAvailableLocales();
    private static String[] zones = TimeZone.getAvailableIDs();

    private static String[] zones_118 = {
        "ACT",  "Australia/Darwin",
        "AET",  "Australia/Sydney",
        "AGT",  "America/Buenos_Aires",
        "ART",  "Africa/Cairo",
        "AST",  "America/Anchorage",
        "BET",  "America/Sao_Paulo",
        "BST",  "Asia/Dacca",
        "CAT",  "Africa/Harare",
        "CNT",  "America/St_Johns",
        "CST",  "America/Chicago",
        "CTT",  "Asia/Shanghai",
        "EAT",  "Africa/Addis_Ababa",
        "ECT",  "Europe/Paris",
//      "EET",  "Africa/Istanbul",
        "EST",  "America/New_York",
        "HST",  "Pacific/Honolulu",
        "IET",  "America/Indiana/Indianapolis",
//      Comment out for this test case fails as the result of L10N for hi_IN.
//      "IST",  "Asia/Calcutta",
        "JST",  "Asia/Tokyo",
//      "MET",  "Asia/Tehran",
        "MIT",  "Pacific/Apia",
        "MST",  "America/Denver",
        "NET",  "Asia/Yerevan",
        "NST",  "Pacific/Auckland",
        "PLT",  "Asia/Karachi",
        "PNT",  "America/Phoenix",
        "PRT",  "America/Puerto_Rico",
        "PST",  "America/Los_Angeles",
        "SST",  "Pacific/Guadalcanal",
        "VST",  "Asia/Saigon",
    };


    public static void main(String[] argv) {
        Locale reservedLocale = Locale.getDefault();
        try {
            if (argv.length == 1 && "-source".equals(argv[0])) {
                src = true;
            }

            testDisplayNames();
            testRAWoffsetAndDisplayNames();
            test118DisplayNames();

            if (err) {
                throw new RuntimeException(
                    "TimeZone display name validation failed.");
            } else {
                System.out.println(
                    "\nAll test passed.\nTotal number of valid TimeZone id is "
                    + zones.length);
            }
        } finally {
            // restore the reserved locale
            Locale.setDefault(reservedLocale);
        }

    }

    /*
     * Checks if each timezone ID has display names. If it doesn't and
     * "-source" option was specified, source code is generated.
     */
    private static void testDisplayNames() {
        System.out.println("Checking if each entry in TimeZoneNames is a valid TimeZone ID");

        Locale.setDefault(Locale.US);
        Enumeration data = new TimeZoneNames().getKeys();

        while (data.hasMoreElements()) {
            String name = (String)data.nextElement();
            String id = TimeZone.getTimeZone(name).getID();
            if (!name.equals(id)) {
                System.err.println("\t" + name + " doesn't seem to be a valid TimeZone ID.");
                err = true;
            }
        }

        System.out.println("Checking if each TimeZone ID has display names.");

        for (int i = 0; i < zones.length; i++) {
            String id = zones[i];

            if (id != null) {
                if (id.startsWith("Etc/GMT")) {
                    continue;
                }
                if (id.indexOf("Riyadh8") != -1) {
                    continue;
                }
                if (id.equals("GMT0")) {
                    continue;
                }
            }

            TimeZone tz = TimeZone.getTimeZone(id);
            String name = tz.getDisplayName();

            if (name == null || name.startsWith("GMT+") || name.startsWith("GMT-")) {
                if (src) {
                    System.out.println("\t    {\"" + tz.getID() + "\", " +
                                       "new String[] {\"Standard Time Name\", \"ST\",\n" +
                                       "\t\t\t\t\t\t\"Daylight Time Name\", \"DT\"}},");
                } else {
                    System.err.println("\t" + tz.getID() + " doesn't seem to have display names");
                    err = true;
                }
            }
        }
    }

    /*
     * Compares
     *   - raw DST offset
     *   - short display names in non-DST
     *   - short display names in DST
     *   - long display names in DST
     * of two timezones whose long display names in non-DST are same.
     * If one of these are different, there may be a bug.
     */
    private static void testRAWoffsetAndDisplayNames() {
        System.out.println("Checking if each entry in TimeZoneNames is a valid TimeZone ID");

        HashMap<String, TimeZone> map = new HashMap<String, TimeZone>();

        for (int i = 0; i < locales.length; i++) {
            map.clear();

            for (int j = 0; j < zones.length; j++) {
                TimeZone tz1 = TimeZone.getTimeZone(zones[j]);
                String name = tz1.getDisplayName(false, TimeZone.LONG, locales[i]);

                if (map.containsKey(name)) {
                    TimeZone tz2 = map.get(name);

                    int offset1 = tz1.getRawOffset();
                    int offset2 = tz2.getRawOffset();
                    if (offset1 != offset2) {
                        System.err.println("Two timezones which have the same long display name \"" +
                            name + "\" in non-DST have different DST offsets in " +
                            locales[i] + " locale.\n\tTimezone 1=" +
                            tz1.getID() + "(" + offset1 + ")\n\tTimezone 2=" +
                            tz2.getID() + "(" + offset2 + ")");
                    }

                    String name1 = tz1.getDisplayName(false, TimeZone.SHORT, locales[i]);
                    String name2 = tz2.getDisplayName(false, TimeZone.SHORT, locales[i]);
                    if (!(name1.equals("GMT") && name2.equals("GMT")) &&
                        !(name1.equals("CET") && name2.equals("MET")) &&
                        !(name1.equals("MET") && name2.equals("CET"))) {
                        if (!name1.equals(name2)) {
                            System.err.println("Two timezones which have the same short display name \"" +
                                name +
                                "\" in non-DST have different short display names in non-DST in " +
                                locales[i] + " locale.\n\tTimezone 1=" +
                                tz1.getID() + "(" + name1 + ")\n\tTimezone 2=" +
                                tz2.getID() + "(" + name2 + ")");
                        }

                        name1 = tz1.getDisplayName(true, TimeZone.SHORT, locales[i]);
                        name2 = tz2.getDisplayName(true, TimeZone.SHORT, locales[i]);
                        if (!name1.equals(name2)) {
                            System.err.println("Two timezones which have the same short display name \"" +
                            name +
                            "\" in non-DST have different short display names in DST in " +
                            locales[i] + " locale.\n\tTimezone 1=" +
                            tz1.getID() + "(" + name1 + ")\n\tTimezone 2=" +
                            tz2.getID() + "(" + name2 + ")");
                        }

                        name1 = tz1.getDisplayName(true, TimeZone.LONG, locales[i]);
                        name2 = tz2.getDisplayName(true, TimeZone.LONG, locales[i]);
                        if (!name1.equals(name2)) {
                            System.err.println("Two timezones which have the same long display name \"" +
                            name +
                            "\" in non-DST have different long display names in DST in " +
                            locales[i] + " locale.\n\tTimezone 1=" +
                            tz1.getID() + "(" + name1 + ")\n\tTimezone 2=" +
                            tz2.getID() + "(" + name2 + ")");
                        }
                    }
                } else {
                    map.put(name, tz1);
                }
            }
        }
    }

    /*
     * Compares three-letter timezones' display names with corresponding
     * "popular" timezones.
     */
    private static void test118DisplayNames() {
        System.out.println("Checking compatibility of Java 1.1.X's three-letter timezones");

        for (int i = 0; i < zones_118.length; i+=2) {
            String id_118 = zones_118[i];
            String id_later = zones_118[i+1];
            String zone_118, zone_later, localename;
            TimeZone tz_118 = TimeZone.getTimeZone(id_118);
            TimeZone tz_later = TimeZone.getTimeZone(id_later);

            for (int j = 0; j < locales.length; j++) {
                localename = locales[j].toString();
                zone_118 = tz_118.getDisplayName(false, TimeZone.SHORT, locales[j]);
                zone_later = tz_later.getDisplayName(false, TimeZone.SHORT, locales[j]);
                check(id_118, id_later, zone_118, zone_later, "short", "non-DST", localename);

                zone_118 = tz_118.getDisplayName(true, TimeZone.SHORT, locales[j]);
                zone_later = tz_later.getDisplayName(true, TimeZone.SHORT, locales[j]);
                check(id_118, id_later, zone_118, zone_later, "short", "DST", localename);

                zone_118 = tz_118.getDisplayName(false, TimeZone.LONG, locales[j]);
                zone_later = tz_later.getDisplayName(false, TimeZone.LONG, locales[j]);
                check(id_118, id_later, zone_118, zone_later, "long", "non-DST", localename);

                zone_118 = tz_118.getDisplayName(true, TimeZone.LONG, locales[j]);
                zone_later = tz_later.getDisplayName(true, TimeZone.LONG, locales[j]);
                check(id_118, id_later, zone_118, zone_later, "long", "DST", localename);
            }
        }
    }

    private static void check(String zoneID_118, String zoneID_later,
                              String zonename_118, String zonename_later,
                              String format, String dst, String loc) {
        if (!zonename_118.equals(zonename_later)) {
            System.err.println("JDK 118 TimeZone \"" + zoneID_118 +
                "\" has a different " + format +
                " display name from its equivalent timezone \"" +
                zoneID_later + "\" in " + dst + " in " + loc + " locale.");
            System.err.println("    Got: " + zonename_118 + ", Expected: " +
                zonename_later);
            err = true;
        }
    }

}
