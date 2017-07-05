/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import static java.util.GregorianCalendar.*;

public class NarrowNamesTest {
    private static final Locale US = Locale.US;
    private static final Locale JAJPJP = new Locale("ja", "JP", "JP");
    private static final Locale THTH = new Locale("th", "TH");

    private static final String RESET_INDEX = "RESET_INDEX";

    private static int errors = 0;

    // This test is locale data-dependent.
    public static void main(String[] args) {
        test(US, ERA, "B",
             ERA, BC, YEAR, 1);
        test(US, ERA, "A",
             ERA, AD, YEAR, 2012);
        test(US, DAY_OF_WEEK, "S",
             YEAR, 2012, MONTH, DECEMBER, DAY_OF_MONTH, 23);
        test(US, AM_PM, "a",
             HOUR_OF_DAY, 10);
        test(US, AM_PM, "p",
             HOUR_OF_DAY, 23);
        test(JAJPJP, DAY_OF_WEEK, "\u65e5",
             YEAR, 24, MONTH, DECEMBER, DAY_OF_MONTH, 23);
        test(THTH, MONTH, NARROW_STANDALONE, "\u0e18.\u0e04.",
             YEAR, 2555, MONTH, DECEMBER, DAY_OF_MONTH, 5);
        test(THTH, DAY_OF_WEEK, "\u0e1e",
             YEAR, 2555, MONTH, DECEMBER, DAY_OF_MONTH, 5);

        testMap(US, DAY_OF_WEEK, ALL_STYLES, // shouldn't include any narrow names
                "", // 1-based indexing for DAY_OF_WEEK
                "Sunday",    // Sunday
                "Monday",    // Monday
                "Tuesday",   // Tuesday
                "Wednesday", // Wednesday
                "Thursday",  // Thursday
                "Friday",    // Friday
                "Saturday",  // Saturday
                RESET_INDEX,
                "", // 1-based indexing for DAY_OF_WEEK
                "Sun",       // abb Sunday
                "Mon",       // abb Monday
                "Tue",       // abb Tuesday
                "Wed",       // abb Wednesday
                "Thu",       // abb Thursday
                "Fri",       // abb Friday
                "Sat"        // abb Saturday
                );
        testMap(US, DAY_OF_WEEK, NARROW_FORMAT); // expect null
        testMap(US, AM_PM, ALL_STYLES,
                "AM", "PM",
                RESET_INDEX,
                "a", "p");
        testMap(JAJPJP, DAY_OF_WEEK, NARROW_STANDALONE); // expect null
        testMap(JAJPJP, DAY_OF_WEEK, NARROW_FORMAT,
                "", // 1-based indexing for DAY_OF_WEEK
                "\u65e5",
                "\u6708",
                "\u706b",
                "\u6c34",
                "\u6728",
                "\u91d1",
                "\u571f");
        testMap(THTH, MONTH, NARROW_FORMAT); // expect null
        testMap(THTH, MONTH, NARROW_STANDALONE,
                "\u0e21.\u0e04.",
                "\u0e01.\u0e1e.",
                "\u0e21\u0e35.\u0e04.",
                "\u0e40\u0e21.\u0e22.",
                "\u0e1e.\u0e04.",
                "\u0e21\u0e34.\u0e22.",
                "\u0e01.\u0e04.",
                "\u0e2a.\u0e04.",
                "\u0e01.\u0e22.",
                "\u0e15.\u0e04.",
                "\u0e1e.\u0e22.",
                "\u0e18.\u0e04.");

        if (errors != 0) {
            throw new RuntimeException("test failed");
        }
    }

    private static void test(Locale locale, int field, String expected, int... data) {
        test(locale, field, NARROW_FORMAT, expected, data);
    }

    private static void test(Locale locale, int field, int style, String expected, int... fieldValuePairs) {
        Calendar cal = Calendar.getInstance(locale);
        cal.clear();
        for (int i = 0; i < fieldValuePairs.length;) {
            int f = fieldValuePairs[i++];
            int v = fieldValuePairs[i++];
            cal.set(f, v);
        }
        String got = cal.getDisplayName(field, style, locale);
        if (!expected.equals(got)) {
            System.err.printf("test: locale=%s, field=%d, value=%d, style=%d, got=\"%s\", expected=\"%s\"%n",
                              locale, field, cal.get(field), style, got, expected);
            errors++;
        }
    }

    private static void testMap(Locale locale, int field, int style, String... expected) {
        Map<String, Integer> expectedMap = null;
        if (expected.length > 0) {
            expectedMap = new TreeMap<>(LengthBasedComparator.INSTANCE);
            int index = 0;
            for (int i = 0; i < expected.length; i++) {
                if (expected[i].isEmpty()) {
                    index++;
                    continue;
                }
                if (expected[i] == RESET_INDEX) {
                    index = 0;
                    continue;
                }
                expectedMap.put(expected[i], index++);
            }
        }
        Calendar cal = Calendar.getInstance(locale);
        Map<String, Integer> got = cal.getDisplayNames(field, style, locale);
        if (!(expectedMap == null && got == null)
            && !expectedMap.equals(got)) {
            System.err.printf("testMap: locale=%s, field=%d, style=%d, expected=%s, got=%s%n",
                              locale, field, style, expectedMap, got);
            errors++;
        }
    }

    /**
     * Comparator implementation for TreeMap which iterates keys from longest
     * to shortest.
     */
    private static class LengthBasedComparator implements Comparator<String> {
        private static final LengthBasedComparator INSTANCE = new LengthBasedComparator();

        private LengthBasedComparator() {
        }

        @Override
        public int compare(String o1, String o2) {
            int n = o2.length() - o1.length();
            return (n == 0) ? o1.compareTo(o2) : n;
        }
    }
}
