/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.spi.CalendarNameProvider;

/**
 * Concrete implementation of the  {@link java.util.spi.CalendarDataProvider
 * CalendarDataProvider} class for the JRE LocaleProviderAdapter.
 *
 * @author Masayoshi Okutsu
 * @author Naoto Sato
 */
public class CalendarNameProviderImpl extends CalendarNameProvider implements AvailableLanguageTags {
    private final LocaleProviderAdapter.Type type;
    private final Set<String> langtags;

    public CalendarNameProviderImpl(LocaleProviderAdapter.Type type, Set<String> langtags) {
        this.type = type;
        this.langtags = langtags;
    }

    @Override
    public String getDisplayName(String calendarType, int field, int value, int style, Locale locale) {
        return getDisplayNameImpl(calendarType, field, value, style, locale, false);
    }

    public String getJavaTimeDisplayName(String calendarType, int field, int value, int style, Locale locale) {
        return getDisplayNameImpl(calendarType, field, value, style, locale, true);
    }

    public String getDisplayNameImpl(String calendarType, int field, int value, int style, Locale locale, boolean javatime) {
        String name = null;
        String key = getResourceKey(calendarType, field, style, javatime);
        if (key != null) {
            LocaleResources lr = LocaleProviderAdapter.forType(type).getLocaleResources(locale);
            String[] strings = javatime ? lr.getJavaTimeNames(key) : lr.getCalendarNames(key);
            if (strings != null && strings.length > 0) {
                if (field == DAY_OF_WEEK || field == YEAR) {
                    --value;
                }
                if (value < 0 || value >= strings.length) {
                    return null;
                }
                name = strings[value];
                // If name is empty in standalone, try its `format' style.
                if (name.length() == 0
                        && (style == SHORT_STANDALONE || style == LONG_STANDALONE
                            || style == NARROW_STANDALONE)) {
                    name = getDisplayName(calendarType, field, value,
                                          getBaseStyle(style),
                                          locale);
                }
            }
        }
        return name;
    }

    private static int[] REST_OF_STYLES = {
        SHORT_STANDALONE, LONG_FORMAT, LONG_STANDALONE,
        NARROW_FORMAT, NARROW_STANDALONE
    };

    @Override
    public Map<String, Integer> getDisplayNames(String calendarType, int field, int style, Locale locale) {
        Map<String, Integer> names;
        if (style == ALL_STYLES) {
            names = getDisplayNamesImpl(calendarType, field, SHORT_FORMAT, locale, false);
            for (int st : REST_OF_STYLES) {
                names.putAll(getDisplayNamesImpl(calendarType, field, st, locale, false));
            }
        } else {
            // specific style
            names = getDisplayNamesImpl(calendarType, field, style, locale, false);
        }
        return names.isEmpty() ? null : names;
    }

    // NOTE: This method should be used ONLY BY JSR 310 classes.
    public Map<String, Integer> getJavaTimeDisplayNames(String calendarType, int field, int style, Locale locale) {
        Map<String, Integer> names;
        names = getDisplayNamesImpl(calendarType, field, style, locale, true);
        return names.isEmpty() ? null : names;
    }

    private Map<String, Integer> getDisplayNamesImpl(String calendarType, int field,
                                                     int style, Locale locale, boolean javatime) {
        String key = getResourceKey(calendarType, field, style, javatime);
        Map<String, Integer> map = new TreeMap<>(LengthBasedComparator.INSTANCE);
        if (key != null) {
            LocaleResources lr = LocaleProviderAdapter.forType(type).getLocaleResources(locale);
            String[] strings = javatime ? lr.getJavaTimeNames(key) : lr.getCalendarNames(key);
            if (strings != null) {
                if (!hasDuplicates(strings)) {
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
        }
        return map;
    }

    private int getBaseStyle(int style) {
        return style & ~(SHORT_STANDALONE - SHORT_FORMAT);
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

    @Override
    public Locale[] getAvailableLocales() {
        return LocaleProviderAdapter.toLocaleArray(langtags);
    }

    @Override
    public boolean isSupportedLocale(Locale locale) {
        if (Locale.ROOT.equals(locale)) {
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
            case "islamic":
            case "roc":
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

    private boolean hasDuplicates(String[] strings) {
        int len = strings.length;
        for (int i = 0; i < len - 1; i++) {
            String a = strings[i];
            if (a != null) {
                for (int j = i + 1; j < len; j++) {
                    if (a.equals(strings[j]))  {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String getResourceKey(String type, int field, int style, boolean javatime) {
        int baseStyle = getBaseStyle(style);
        boolean isStandalone = (style != baseStyle);

        if ("gregory".equals(type)) {
            type = null;
        }
        boolean isNarrow = (baseStyle == NARROW_FORMAT);
        StringBuilder key = new StringBuilder();
        // If javatime is true, use prefix "java.time.".
        if (javatime) {
            key.append("java.time.");
        }
        switch (field) {
        case ERA:
            if (type != null) {
                key.append(type).append('.');
            }
            if (isNarrow) {
                key.append("narrow.");
            } else {
                // JRE and CLDR use different resource key conventions
                // due to historical reasons. (JRE DateFormatSymbols.getEras returns
                // abbreviations while other getShort*() return abbreviations.)
                if (this.type == LocaleProviderAdapter.Type.JRE) {
                    if (javatime) {
                        if (baseStyle == LONG) {
                            key.append("long.");
                        }
                    }
                    if (baseStyle == SHORT) {
                        key.append("short.");
                    }
                } else { // this.type == LocaleProviderAdapter.Type.CLDR
                    if (baseStyle == LONG) {
                        key.append("long.");
                    }
                }
            }
            key.append("Eras");
            break;

        case YEAR:
            if (!isNarrow) {
                key.append(type).append(".FirstYear");
            }
            break;

        case MONTH:
            if ("islamic".equals(type)) {
                key.append(type).append('.');
            }
            if (isStandalone) {
                key.append("standalone.");
            }
            key.append("Month").append(toStyleName(baseStyle));
            break;

        case DAY_OF_WEEK:
            // support standalone narrow day names
            if (isStandalone && isNarrow) {
                key.append("standalone.");
            }
            key.append("Day").append(toStyleName(baseStyle));
            break;

        case AM_PM:
            if (isNarrow) {
                key.append("narrow.");
            }
            key.append("AmPmMarkers");
            break;
        }
        return key.length() > 0 ? key.toString() : null;
    }

    private String toStyleName(int baseStyle) {
        switch (baseStyle) {
        case SHORT:
            return "Abbreviations";
        case NARROW_FORMAT:
            return "Narrows";
        }
        return "Names";
    }
}
