/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package sun.util.locale.provider;

import static java.util.Calendar.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.spi.CalendarDataProvider;

/**
 * Concrete implementation of the  {@link java.util.spi.CalendarDataProvider
 * CalendarDataProvider} class for the JRE LocaleProviderAdapter.
 *
 * @author Masayoshi Okutsu
 * @author Naoto Sato
 */
public class CalendarDataProviderImpl extends CalendarDataProvider implements AvailableLanguageTags {
    private final LocaleProviderAdapter.Type type;
    private final Set<String> langtags;

    public CalendarDataProviderImpl(LocaleProviderAdapter.Type type, Set<String> langtags) {
        this.type = type;
        this.langtags = langtags;
    }

    @Override
    public int getFirstDayOfWeek(Locale locale) {
        return getIntData(CalendarDataUtility.FIRST_DAY_OF_WEEK, locale);
    }

    @Override
    public int getMinimalDaysInFirstWeek(Locale locale) {
        return getIntData(CalendarDataUtility.MINIMAL_DAYS_IN_FIRST_WEEK, locale);
    }

    @Override
    public String getDisplayName(String calendarType, int field, int value, int style, Locale locale) {
        String name = null;
        String key = getKey(calendarType, field, style);
        if (key != null) {
            ResourceBundle rb = LocaleProviderAdapter.forType(type).getLocaleData().getDateFormatData(locale);
            if (rb.containsKey(key)) {
                String[] strings = rb.getStringArray(key);
                if (strings.length > 0) {
                    if (field == DAY_OF_WEEK || field == YEAR) {
                        --value;
                    }
                    name = strings[value];
                    // If name is empty in standalone, try its `format' style.
                    if (name.length() == 0
                            && (style == SHORT_STANDALONE || style == LONG_STANDALONE)) {
                        name = getDisplayName(calendarType, field, value,
                                              style == SHORT_STANDALONE ? SHORT_FORMAT : LONG_FORMAT,
                                              locale);
                    }
                }
            }
        }
        return name;
    }

    @Override
    public Map<String, Integer> getDisplayNames(String calendarType, int field, int style, Locale locale) {
        Map<String, Integer> names;
        if (style == ALL_STYLES) {
            names = getDisplayNamesImpl(calendarType, field, SHORT_FORMAT, locale);
            if (field != AM_PM) {
                for (int st : new int[] { SHORT_STANDALONE, LONG_FORMAT, LONG_STANDALONE }) {
                    names.putAll(getDisplayNamesImpl(calendarType, field, st, locale));
                }
            }
        } else {
            // specific style
            names = getDisplayNamesImpl(calendarType, field, style, locale);
        }
        return names.isEmpty() ? null : names;
    }

    private Map<String, Integer> getDisplayNamesImpl(String calendarType, int field,
                                                     int style, Locale locale) {
        String key = getKey(calendarType, field, style);
        Map<String, Integer> map = new HashMap<>();
        if (key != null) {
            ResourceBundle rb = LocaleProviderAdapter.forType(type).getLocaleData().getDateFormatData(locale);
            if (rb.containsKey(key)) {
                String[] strings = rb.getStringArray(key);
                if (field == YEAR) {
                    if (strings.length > 0) {
                        map.put(strings[0], 1);
                    }
                } else {
                    int base = (field == DAY_OF_WEEK) ? 1 : 0;
                    for (int i = 0; i < strings.length; i++) {
                        String name = strings[i];
                        // Ignore any empty string (some standalone month names
                        // are not defined)
                        if (name.length() == 0) {
                            continue;
                        }
                        map.put(name, base + i);
                    }
                }
            }
        }
        return map;
    }

    @Override
    public Locale[] getAvailableLocales() {
        return LocaleProviderAdapter.toLocaleArray(langtags);
    }

    @Override
    public boolean isSupportedLocale(Locale locale) {
        if (locale == Locale.ROOT) {
            return true;
        }
        String calendarType = null;
        if (locale.hasExtensions()) {
            calendarType = locale.getUnicodeLocaleType("ca");
            locale = locale.stripExtensions();
        }

        if (calendarType != null) {
            switch (calendarType) {
            case "buddhist":
            case "japanese":
            case "gregory":
                break;
            default:
                // Unknown calendar type
                return false;
            }
        }
        if (langtags.contains(locale.toLanguageTag())) {
            return true;
        }
        if (type == LocaleProviderAdapter.Type.JRE) {
            String oldname = locale.toString().replace('_', '-');
            return langtags.contains(oldname);
        }
        return false;
    }

    @Override
    public Set<String> getAvailableLanguageTags() {
        return langtags;
    }

    private int getIntData(String key, Locale locale) {
        ResourceBundle rb = LocaleProviderAdapter.forType(type).getLocaleData().getCalendarData(locale);
        if (rb.containsKey(key)) {
            String firstday = rb.getString(key);
            return Integer.parseInt(firstday);
        }
        // Note that the base bundle of CLDR doesn't have the Calendar week parameters.
        return 0;
    }

    private String getKey(String type, int field, int style) {
        boolean standalone = (style & 0x8000) != 0;
        style &= ~0x8000;

        if ("gregory".equals(type)) {
            type = null;
        }

        StringBuilder key = new StringBuilder();
        switch (field) {
        case ERA:
            if (type != null) {
                key.append(type).append('.');
            }
            if (style == SHORT) {
                key.append("short.");
            }
            key.append("Eras");
            break;

        case YEAR:
            key.append(type).append(".FirstYear");
            break;

        case MONTH:
            if (standalone) {
                key.append("standalone.");
            }
            key.append(style == SHORT ? "MonthAbbreviations" : "MonthNames");
            break;

        case DAY_OF_WEEK:
            key.append(style == SHORT ? "DayAbbreviations" : "DayNames");
            break;

        case AM_PM:
            key.append("AmPmMarkers");
            break;
        }
        return key.length() > 0 ? key.toString() : null;
    }
}
