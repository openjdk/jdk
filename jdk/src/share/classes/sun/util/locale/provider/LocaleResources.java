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

/*
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 * The original version of this source code and documentation
 * is copyrighted and owned by Taligent, Inc., a wholly-owned
 * subsidiary of IBM. These materials are provided under terms
 * of a License Agreement between Taligent and Sun. This technology
 * is protected by multiple US and International patents.
 *
 * This notice and attribution to Taligent may not be removed.
 * Taligent is a registered trademark of Taligent, Inc.
 *
 */

package sun.util.locale.provider;

import java.text.MessageFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import sun.util.resources.OpenListResourceBundle;

/**
 * Central accessor to locale-dependent resources.
 *
 * @author Masayoshi Okutsu
 */
public class LocaleResources {

    private final LocaleProviderAdapter adapter;
    private final Locale locale;

    // Resource cache
    private ConcurrentMap<String, Object> cache = new ConcurrentHashMap<>();


    LocaleResources(LocaleProviderAdapter adapter, Locale locale) {
        this.adapter = adapter;
        this.locale = locale;
    }

    public OpenListResourceBundle getTimeZoneNames() {
        OpenListResourceBundle tznames = (OpenListResourceBundle) cache.get("TimeZoneNames");
        if (tznames == null) {
            tznames = adapter.getLocaleData().getTimeZoneNames(locale);
            OpenListResourceBundle olrb = (OpenListResourceBundle) cache.putIfAbsent("TimeZoneNames", tznames);
            if (olrb != null) {
                tznames = olrb;
            }
        }
        return tznames;
    }

    public String getDateTimePattern(int timeStyle, int dateStyle, Calendar cal) {
        String pattern;

        if (cal == null) {
            cal = Calendar.getInstance(locale);
        }
        String calType = cal.getCalendarType();
        String timePattern = null;
        String datePattern = null;
        if (timeStyle >= 0) {
            timePattern = getDateTimePattern("TimePatterns", timeStyle, calType);
        }
        if (dateStyle >= 0) {
            datePattern = getDateTimePattern("DatePatterns", dateStyle, calType);
        }
        if (timeStyle >= 0) {
            if (dateStyle >= 0) {
                String dateTimePattern = getDateTimePattern("DateTimePatterns", 0, calType);
                switch (dateTimePattern) {
                case "{1} {0}":
                    pattern = datePattern + " " + timePattern;
                    break;
                case "{0} {1}":
                    pattern = timePattern + " " + datePattern;
                    break;
                default:
                    pattern = MessageFormat.format(dateTimePattern, timePattern, datePattern);
                    break;
                }
            } else {
                pattern = timePattern;
            }
        } else if (dateStyle >= 0) {
            pattern = datePattern;
        } else {
            throw new IllegalArgumentException("No date or time style specified");
        }
        return pattern;
    }

    public String[] getNumberPatterns() {
        /* try the cache first */
        String[] numberPatterns = (String[]) cache.get("NumberPatterns");
        if (numberPatterns == null) { /* cache miss */
            ResourceBundle resource = adapter.getLocaleData().getNumberFormatData(locale);
            numberPatterns = resource.getStringArray("NumberPatterns");
            /* update cache */
            cache.put("NumberPatterns", numberPatterns);
        }
        return numberPatterns;
    }

    private String getDateTimePattern(String key, int styleIndex, String calendarType) {
        String resourceKey = "gregory".equals(calendarType) ? key : calendarType + "." + key;
        /* try the cache first */
        String[] patterns = (String[]) cache.get(resourceKey);
        if (patterns == null) { /* cache miss */
            ResourceBundle r = adapter.getLocaleData().getDateFormatData(locale);
            if (r.containsKey(resourceKey)) {
                patterns = r.getStringArray(resourceKey);
            } else {
                assert !resourceKey.equals(key);
                patterns = r.getStringArray(key);
            }
            /* update cache */
            cache.putIfAbsent(resourceKey, patterns);
        }
        return patterns[styleIndex];
    }
}
