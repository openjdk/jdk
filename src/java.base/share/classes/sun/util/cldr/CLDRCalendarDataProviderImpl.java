/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.util.cldr;

import static sun.util.locale.provider.LocaleProviderAdapter.Type;

import java.util.Arrays;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sun.text.resources.DayPeriodRules;
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.CalendarDataProviderImpl;
import sun.util.locale.provider.CalendarDataUtility;

/**
 * Concrete implementation of the
 * {@link java.util.spi.CalendarDataProvider CalendarDataProvider} class
 * for the CLDR LocaleProviderAdapter.
 *
 * @author Naoto Sato
 */
public class CLDRCalendarDataProviderImpl extends CalendarDataProviderImpl {

    private static Map<String, Integer> firstDay = new ConcurrentHashMap<>();
    private static Map<String, Integer> minDays = new ConcurrentHashMap<>();
    private static Pattern RULE = Pattern.compile("(?<type>[a-z12]+):(?<from>\\d{2}):00-(?<to>\\d{2})");

    public CLDRCalendarDataProviderImpl(Type type, Set<String> langtags) {
        super(type, langtags);
    }

    @Override
    public int getFirstDayOfWeek(Locale locale) {
        return findValue(CalendarDataUtility.FIRST_DAY_OF_WEEK, locale);
    }

    @Override
    public int getMinimalDaysInFirstWeek(Locale locale) {
        return findValue(CalendarDataUtility.MINIMAL_DAYS_IN_FIRST_WEEK, locale);
    }

    @Override
    public int getFlexibleDayPeriod(Locale locale, int hour) {
        // refactor for performance
        String lang = locale.getLanguage();
        String type = Arrays.stream(DayPeriodRules.rulesArray)
                .filter(ra -> ra[0].equals(lang))
                .map(ra -> {
                    return Arrays.stream(ra[1].split(";"))
                            .map(period -> {
                                Matcher m = RULE.matcher(period);
                                if (m.find()) {
                                    int from = Integer.parseInt(m.group("from"));
                                    int to = Integer.parseInt(m.group("to"));
                                    if (from < to) {
                                        if (from <= hour && to > hour) {
                                            return m.group("type");
                                        }
                                    } else {
                                        if (from <= hour || to > hour) {
                                            return m.group("type");
                                        }
                                    }
                                }
                                return null;
                            })
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse("");
                })
                .findFirst()
                .orElse("");
        switch (type) {
            case "am": return 0;
            case "pm": return 1;
            case "midnight": return 2;
            case "noon": return 3;
            case "morning1": return 4;
            case "morning2": return 5;
            case "afternoon1": return 6;
            case "afternoon2": return 7;
            case "evening1": return 8;
            case "evening2": return 9;
            case "night1": return 10;
            case "night2": return 11;
            default: throw new InternalError("invalid day period type");
        }
    }

    /**
     * Finds the requested integer value for the locale.
     * Each resource consists of the following:
     *
     *    (n: cc1 cc2 ... ccx;)*
     *
     * where 'n' is the integer for the following region codes, terminated by
     * a ';'.
     *
     */
    private static int findValue(String key, Locale locale) {
        Map<String, Integer> map = CalendarDataUtility.FIRST_DAY_OF_WEEK.equals(key) ?
            firstDay : minDays;
        String region = locale.getCountry();

        if (region.isEmpty()) {
            // Use "US" as default
            region = "US";
        }

        Integer val = map.get(region);
        if (val == null) {
            String valStr =
                LocaleProviderAdapter.forType(Type.CLDR).getLocaleResources(Locale.ROOT)
                   .getCalendarData(key);
            val = retrieveInteger(valStr, region)
                .orElse(retrieveInteger(valStr, "001").orElse(0));
            map.putIfAbsent(region, val);
        }

        return val;
    }

    private static Optional<Integer> retrieveInteger(String src, String region) {
        int regionIndex = src.indexOf(region);
        if (regionIndex >= 0) {
            int start = src.lastIndexOf(';', regionIndex) + 1;
            return Optional.of(Integer.parseInt(src, start, src.indexOf(':', start), 10));
        }
        return Optional.empty();
    }
}
