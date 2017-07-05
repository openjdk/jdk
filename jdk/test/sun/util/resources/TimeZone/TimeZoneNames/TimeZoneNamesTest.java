/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8025051
 * @summary Test time zone names across all locales
 * @run main TimeZoneNamesTest
 */

import sun.util.locale.provider.TimeZoneNameUtility;
import java.util.TimeZone;
import java.util.Locale;
import java.util.Properties;
import java.io.IOException;
import java.io.FileInputStream;

public class TimeZoneNamesTest {
    // name type to test. Possible: long, short.
    static String requestedTestType = "long";
    // test Standard/DST (false) or Generic (true) TZ names
    static boolean testGeneric = false;

    public static void testGenericTZName( Locale locale, String timezoneName,
                                          int nameType, String expectedName ) throws RuntimeException {
        if (testGeneric) {
            String genericName = TimeZoneNameUtility.retrieveGenericDisplayName(timezoneName, nameType, locale);
            //Check for equality
            if (!genericName.equals(expectedName))
                throw new RuntimeException( "Time zone ("+timezoneName+") name is incorrect for locale \""+locale.getDisplayName()
                                            +"\" nameType: Generic"+"("+nameType+") Should be: " +expectedName+" Observed: "+genericName);
        }
    }

    public static void testTZName( Locale locale, String timezoneName, boolean isDaylight,
                                   int nameType, String expectedName ) throws RuntimeException {
        if (!testGeneric) {
            //Construct time zone objects
            TimeZone zone = TimeZone.getTimeZone(timezoneName);
            //Get name from JDK
            String name = zone.getDisplayName(isDaylight, nameType, locale);
            //Check for equality
            if (!name.equals(expectedName))
                throw new RuntimeException( "Time zone ("+timezoneName+") name is incorrect for locale: \""+locale.getDisplayName()
                                            +"\" nameType:"+requestedTestType+" DST:"+isDaylight+" Should be: " +expectedName+" Observed: "+name);
        }
    }

    public static boolean testPropertyEntry( Locale locale, String entry, String value ) {
        boolean result = true;
        String[] params = entry.split("\\.");
        if (params.length != 3) {
            System.out.println("Incorrect property file entry="+entry+" "+params.length);
            result = false;
        } else {
            boolean isDaylight = true;
            int nameType = TimeZone.LONG;

            if (params[2].equals("short"))
                nameType = TimeZone.SHORT;

            if (params[1].equals("standard"))
                isDaylight = false;

            // Names with non-requested tz name type are ignored
            if (requestedTestType.equals(params[2])) {
                try {
                    if (params[1].equals("generic"))
                        testGenericTZName( locale, params[0], nameType, value );
                    else
                        testTZName( locale, params[0], isDaylight, nameType, value );
                } catch (RuntimeException e) {
                    System.out.println( "Test FAILED: "+e );
                    result = false;
                }
            }
        }
        return result;
    }

    public static boolean testPropertyFile( String propFile, String shortName, Locale locale ) throws RuntimeException {
        boolean result = true;
        Properties property = new Properties();
        try {
            property.load( new FileInputStream(propFile) );
        } catch (IOException e) {
            throw new RuntimeException("Property file "+propFile+" is not found", e);
        }
        for (String key: property.stringPropertyNames()) {
            result &= testPropertyEntry(locale, key, property.getProperty(key) );
        }
        return result;
    }

    // Locale to test, file with names data, test long/short names, test generic names (true/false)
    static Object[][] testTargets = {
        { Locale.ROOT,"TimeZoneNames.properties","long",false},
        { Locale.ROOT,"TimeZoneNames_short.properties","short",false},
        { Locale.ROOT,"TimeZoneNames.properties","long",true},
        { Locale.ROOT,"TimeZoneNames_short.properties","short",true},

        { new Locale("de"),"TimeZoneNames_de.properties","long",false},
        { new Locale("de"),"TimeZoneNames_de_short.properties","short",false},
        { new Locale("de"),"TimeZoneNames_de.properties","long",true},
        { new Locale("de"),"TimeZoneNames_de_short.properties","short",true},

        { new Locale("es"),"TimeZoneNames_es.properties","long",false},
        { new Locale("es"),"TimeZoneNames_es_short.properties","short",false},
        { new Locale("es"),"TimeZoneNames_es.properties","long",true},
        { new Locale("es"),"TimeZoneNames_es_short.properties","short",true},

        { new Locale("fr"),"TimeZoneNames_fr.properties","long",false},
        { new Locale("fr"),"TimeZoneNames_fr_short.properties","short",false},
        { new Locale("fr"),"TimeZoneNames_fr.properties","long",true},
        { new Locale("fr"),"TimeZoneNames_fr_short.properties","short",true},

        { new Locale("it"),"TimeZoneNames_it.properties","long",false},
        { new Locale("it"),"TimeZoneNames_it_short.properties","short",false},
        { new Locale("it"),"TimeZoneNames_it.properties","long",true},
        { new Locale("it"),"TimeZoneNames_it_short.properties","short",true},

        { new Locale("ja"),"TimeZoneNames_ja.properties","long",false},
        { new Locale("ja"),"TimeZoneNames_ja_short.properties","short",false},
        { new Locale("ja"),"TimeZoneNames_ja.properties","long",true},
        { new Locale("ja"),"TimeZoneNames_ja_short.properties","short",true},

        { new Locale("ko"),"TimeZoneNames_ko.properties","long",false},
        { new Locale("ko"),"TimeZoneNames_ko_short.properties","short",false},
        { new Locale("ko"),"TimeZoneNames_ko.properties","long",true},
        { new Locale("ko"),"TimeZoneNames_ko_short.properties","short",true},

        { new Locale("pt","BR"),"TimeZoneNames_pt_BR.properties","long",false},
        { new Locale("pt","BR"),"TimeZoneNames_pt_BR_short.properties","short",false},
        { new Locale("pt","BR"),"TimeZoneNames_pt_BR.properties","long",true},
        { new Locale("pt","BR"),"TimeZoneNames_pt_BR_short.properties","short",true},

        { new Locale("sv"),"TimeZoneNames_sv.properties","long",false},
        { new Locale("sv"),"TimeZoneNames_sv_short.properties","short",false},
        { new Locale("sv"),"TimeZoneNames_sv.properties","long",true},
        { new Locale("sv"),"TimeZoneNames_sv_short.properties","short",true},

        { new Locale("zh","CN"),"TimeZoneNames_zh_CN.properties","long",false},
        { new Locale("zh","CN"),"TimeZoneNames_zh_CN_short.properties","short",false},
        { new Locale("zh","CN"),"TimeZoneNames_zh_CN.properties","long",true},
        { new Locale("zh","CN"),"TimeZoneNames_zh_CN_short.properties","short",true},

        { new Locale("zh","TW"),"TimeZoneNames_zh_TW.properties","long",false},
        { new Locale("zh","TW"),"TimeZoneNames_zh_TW_short.properties","short",false},
        { new Locale("zh","TW"),"TimeZoneNames_zh_TW.properties","long",true},
        { new Locale("zh","TW"),"TimeZoneNames_zh_TW_short.properties","short",true}
    };

    public static void main(String[] args) {
        boolean result = true;

        for (Object [] test: testTargets) {
            Locale testLocale = (Locale) test[0];
            String testFile = (String) test[1];
            requestedTestType = (String) test[2];
            testGeneric = (Boolean) test[3];
            result &= testPropertyFile( System.getProperty("test.src",".")+"/"+testFile, testFile, testLocale);
        }
        if (!result)
            throw new RuntimeException("Some time zones has unexpected names. Please, check test output.");
    }
}
