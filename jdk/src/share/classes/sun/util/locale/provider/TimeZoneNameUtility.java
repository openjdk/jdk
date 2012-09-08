/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.SoftReference;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.spi.TimeZoneNameProvider;
import sun.util.calendar.ZoneInfo;
import sun.util.resources.OpenListResourceBundle;

/**
 * Utility class that deals with the localized time zone names
 *
 * @author Naoto Sato
 */
public final class TimeZoneNameUtility {

    /**
     * cache to hold time zone resource bundles. Keyed by Locale
     */
    private static ConcurrentHashMap<Locale, SoftReference<OpenListResourceBundle>> cachedBundles =
        new ConcurrentHashMap<>();

    /**
     * cache to hold time zone localized strings. Keyed by Locale
     */
    private static ConcurrentHashMap<Locale, SoftReference<String[][]>> cachedZoneData =
        new ConcurrentHashMap<>();

    /**
     * get time zone localized strings. Enumerate all keys.
     */
    public static String[][] getZoneStrings(Locale locale) {
        String[][] zones;
        SoftReference<String[][]> data = cachedZoneData.get(locale);

        if (data == null || ((zones = data.get()) == null)) {
            zones = loadZoneStrings(locale);
            data = new SoftReference<>(zones);
            cachedZoneData.put(locale, data);
        }

        return zones;
    }

    private static String[][] loadZoneStrings(Locale locale) {
        List<String[]> zones = new LinkedList<>();
        OpenListResourceBundle rb = getBundle(locale);
        Enumeration<String> keys = rb.getKeys();
        String[] names;

        while(keys.hasMoreElements()) {
            String key = keys.nextElement();

            names = retrieveDisplayNames(rb, key, locale);
            if (names != null) {
                zones.add(names);
            }
        }

        String[][] zonesArray = new String[zones.size()][];
        return zones.toArray(zonesArray);
    }

    /**
     * Retrieve display names for a time zone ID.
     */
    public static String[] retrieveDisplayNames(String id, Locale locale) {
        OpenListResourceBundle rb = getBundle(locale);
        return retrieveDisplayNames(rb, id, locale);
    }

    private static String[] retrieveDisplayNames(OpenListResourceBundle rb,
                                                String id, Locale locale) {
        if (id == null || locale == null) {
            throw new NullPointerException();
        }

        LocaleServiceProviderPool pool =
            LocaleServiceProviderPool.getPool(TimeZoneNameProvider.class);
        return pool.getLocalizedObject(TimeZoneNameGetter.INSTANCE, locale, id);
    }

    private static OpenListResourceBundle getBundle(Locale locale) {
        OpenListResourceBundle rb;
        SoftReference<OpenListResourceBundle> data = cachedBundles.get(locale);

        if (data == null || ((rb = data.get()) == null)) {
            rb = LocaleProviderAdapter.forJRE().getLocaleData().getTimeZoneNames(locale);
            data = new SoftReference<>(rb);
            cachedBundles.put(locale, data);
        }

        return rb;
    }

    /**
     * Obtains a localized time zone strings from a TimeZoneNameProvider
     * implementation.
     */
    private static class TimeZoneNameGetter
        implements LocaleServiceProviderPool.LocalizedObjectGetter<TimeZoneNameProvider,
                                                                   String[]>{
        private static final TimeZoneNameGetter INSTANCE =
            new TimeZoneNameGetter();

        @Override
        public String[] getObject(TimeZoneNameProvider timeZoneNameProvider,
                                Locale locale,
                                String requestID,
                                Object... params) {
            assert params.length == 0;
            String queryID = requestID;

            // First, try to get names with the request ID
            String[] names = buildZoneStrings(timeZoneNameProvider, locale, requestID);

            if (names == null) {
                Map<String, String> aliases = ZoneInfo.getAliasTable();

                if (aliases != null) {
                    // Check whether this id is an alias, if so,
                    // look for the standard id.
                    if (aliases.containsKey(queryID)) {
                        String prevID = queryID;
                        while ((queryID = aliases.get(queryID)) != null) {
                            prevID = queryID;
                        }
                        queryID = prevID;
                    }

                    names = buildZoneStrings(timeZoneNameProvider, locale, queryID);

                    if (names == null) {
                        // There may be a case that a standard id has become an
                        // alias.  so, check the aliases backward.
                        names = examineAliases(timeZoneNameProvider, locale,
                                               queryID, aliases, aliases.entrySet());
                    }
                }
            }

            if (names != null) {
                names[0] = requestID;
            }

            return names;
        }

        private static String[] examineAliases(TimeZoneNameProvider tznp, Locale locale,
                                               String id,
                                               Map<String, String> aliases,
                                               Set<Map.Entry<String, String>> aliasesSet) {
            if (aliases.containsValue(id)) {
                for (Map.Entry<String, String> entry : aliasesSet) {
                    if (entry.getValue().equals(id)) {
                        String alias = entry.getKey();
                        String[] names = buildZoneStrings(tznp, locale, alias);
                        if (names != null) {
                            return names;
                        } else {
                            names = examineAliases(tznp, locale, alias, aliases, aliasesSet);
                            if (names != null) {
                                return names;
                            }
                        }
                    }
                }
            }

            return null;
        }

        private static String[] buildZoneStrings(TimeZoneNameProvider tznp,
                                    Locale locale, String id) {
            String[] names = new String[5];

            for (int i = 1; i <= 4; i ++) {
                names[i] = tznp.getDisplayName(id, i>=3, i%2, locale);
                if (i >= 3 && names[i] == null) {
                    names[i] = names[i-2];
                }
            }

            if (names[1] == null) {
                // this id seems not localized by this provider
                names = null;
            }

            return names;
        }
    }

    // No instantiation
    private TimeZoneNameUtility() {
    }
}
