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
/*
 *
 */

import java.text.*;
import java.util.*;
import static java.util.Calendar.*;
import sun.util.locale.provider.*;
import sun.util.resources.*;
import com.bar.CalendarNameProviderImpl;

/**
 * Test case for CalendarNameProvider.
 *
 * Test strategy:
 * com.bar.CalendarNameProviderImpl supports only ja_JP_kids locale. It returns
 * month names only in full-width digits, followed by "gatsu" in Hiragana if
 * it's a long style. The standalone styles are used because DateFormatSymbols
 * has precedence for the format styles.
 *
 * Calendar.getDisplayName(s) should be called with kids to get the month
 * names provided by com.bar.CalendarNameProviderImpl. Other display names
 * should be the same as what a Calendar constructed with ja_JP returns.
 */
public class CalendarNameProviderTest {

    public static void main(String[] s) {
        new CalendarNameProviderTest().test();
    }

    void test() {
        Locale kids = new Locale("ja", "JP", "kids"); // test provider's supported locale
        Calendar kcal = Calendar.getInstance(kids);
        Calendar jcal = Calendar.getInstance(Locale.JAPAN);

        // check month names and week day names
        Map<String, Integer> mapAllStyles = new HashMap<>();
        for (int style : new int[] { SHORT_STANDALONE, LONG_STANDALONE }) {
            // Check month names provided by com.bar.CalendarNameProviderImpl
            Map<String, Integer> map = new HashMap<>();
            for (int month = JANUARY; month <= DECEMBER; month++) {
                kcal.set(DAY_OF_MONTH, 1);
                kcal.set(MONTH, month);
                kcal.set(HOUR_OF_DAY, 12); // avoid any standard-daylight transitions...
                kcal.getTimeInMillis();
                String name = kcal.getDisplayName(MONTH, style, kids);
                checkResult("Month name",
                            name,
                            CalendarNameProviderImpl.toMonthName(kcal.get(MONTH) + 1, style));

                // Builds the map with name to its integer value.
                map.put(name, kcal.get(MONTH));
            }
            checkResult((style == SHORT_STANDALONE ? "Short" : "Long") + " month names map",
                        kcal.getDisplayNames(MONTH, style, kids), map);
            mapAllStyles.putAll(map);
            if (style == LONG_STANDALONE) {
                checkResult("Short and long month names map",
                            kcal.getDisplayNames(MONTH, ALL_STYLES, kids), mapAllStyles);
            }

            // Check week names: kcal and jcal should return the same names and maps.
            for (int dow = SUNDAY; dow <= SATURDAY; dow++) {
                kcal.set(DAY_OF_WEEK, dow);
                jcal.setTimeInMillis(kcal.getTimeInMillis());
                String name = kcal.getDisplayName(DAY_OF_WEEK, style, kids);
                checkResult("Day of week name", name,
                                                jcal.getDisplayName(DAY_OF_WEEK, style, Locale.JAPAN));
            }
            checkResult("Short day of week names", kcal.getDisplayNames(DAY_OF_WEEK, style, kids),
                                                   jcal.getDisplayNames(DAY_OF_WEEK, style, Locale.JAPAN));
        }

    }

    private <T> void checkResult(String msg, T got, T expected) {
        if (!expected.equals(got)) {
            String s = String.format("%s: got='%s', expected='%s'", msg, got, expected);
            throw new RuntimeException(s);
        }
    }
}
