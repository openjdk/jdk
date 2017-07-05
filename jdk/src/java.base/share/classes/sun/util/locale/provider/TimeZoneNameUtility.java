/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.spi.TimeZoneNameProvider;
import sun.util.calendar.ZoneInfo;

/**
 * Utility class that deals with the localized time zone names
 *
 * @author Naoto Sato
 * @author Masayoshi Okutsu
 */
public final class TimeZoneNameUtility {

    /**
     * cache to hold time zone localized strings. Keyed by Locale
     */
    private static ConcurrentHashMap<Locale, SoftReference<String[][]>> cachedZoneData =
        new ConcurrentHashMap<>();

    /**
     * Cache for managing display names per timezone per locale
     * The structure is:
     *     Map(key=id, value=SoftReference(Map(key=locale, value=displaynames)))
     */
    private static final Map<String, SoftReference<Map<Locale, String[]>>> cachedDisplayNames =
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
        // If the provider is a TimeZoneNameProviderImpl, call its getZoneStrings
        // in order to avoid per-ID retrieval.
        LocaleProviderAdapter adapter = LocaleProviderAdapter.getAdapter(TimeZoneNameProvider.class, locale);
        TimeZoneNameProvider provider = adapter.getTimeZoneNameProvider();
        if (provider instanceof TimeZoneNameProviderImpl) {
            return ((TimeZoneNameProviderImpl)provider).getZoneStrings(locale);
        }

        // Performs per-ID retrieval.
        Set<String> zoneIDs = LocaleProviderAdapter.forJRE().getLocaleResources(locale).getZoneIDs();
        List<String[]> zones = new LinkedList<>();
        for (String key : zoneIDs) {
            String[] names = retrieveDisplayNamesImpl(key, locale);
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
        if (id == null || locale == null) {
            throw new NullPointerException();
        }
        return retrieveDisplayNamesImpl(id, locale);
    }

    /**
     * Retrieves a generic time zone display name for a time zone ID.
     *
     * @param id     time zone ID
     * @param style  TimeZone.LONG or TimeZone.SHORT
     * @param locale desired Locale
     * @return the requested generic time zone display name, or null if not found.
     */
    public static String retrieveGenericDisplayName(String id, int style, Locale locale) {
        LocaleServiceProviderPool pool =
            LocaleServiceProviderPool.getPool(TimeZoneNameProvider.class);
        return pool.getLocalizedObject(TimeZoneNameGetter.INSTANCE, locale, "generic", style, id);
    }

    /**
     * Retrieves a standard or daylight-saving time name for the given time zone ID.
     *
     * @param id       time zone ID
     * @param daylight true for a daylight saving time name, or false for a standard time name
     * @param style    TimeZone.LONG or TimeZone.SHORT
     * @param locale   desired Locale
     * @return the requested time zone name, or null if not found.
     */
    public static String retrieveDisplayName(String id, boolean daylight, int style, Locale locale) {
        LocaleServiceProviderPool pool =
            LocaleServiceProviderPool.getPool(TimeZoneNameProvider.class);
        return pool.getLocalizedObject(TimeZoneNameGetter.INSTANCE, locale, daylight ? "dst" : "std", style, id);
    }

    private static String[] retrieveDisplayNamesImpl(String id, Locale locale) {
        LocaleServiceProviderPool pool =
            LocaleServiceProviderPool.getPool(TimeZoneNameProvider.class);

        SoftReference<Map<Locale, String[]>> ref = cachedDisplayNames.get(id);
        if (ref != null) {
            Map<Locale, String[]> perLocale = ref.get();
            if (perLocale != null) {
                String[] names = perLocale.get(locale);
                if (names != null) {
                    return names;
                }
                names = pool.getLocalizedObject(TimeZoneNameArrayGetter.INSTANCE, locale, id);
                if (names != null) {
                    perLocale.put(locale, names);
                }
                return names;
            }
        }

        String[] names = pool.getLocalizedObject(TimeZoneNameArrayGetter.INSTANCE, locale, id);
        if (names != null) {
            Map<Locale, String[]> perLocale = new ConcurrentHashMap<>();
            perLocale.put(locale, names);
            ref = new SoftReference<>(perLocale);
            cachedDisplayNames.put(id, ref);
        }
        return names;
    }

    /**
     * Obtains a localized time zone strings from a TimeZoneNameProvider
     * implementation.
     */
    private static class TimeZoneNameArrayGetter
        implements LocaleServiceProviderPool.LocalizedObjectGetter<TimeZoneNameProvider,
                                                                   String[]>{
        private static final TimeZoneNameArrayGetter INSTANCE =
            new TimeZoneNameArrayGetter();

        @Override
        public String[] getObject(TimeZoneNameProvider timeZoneNameProvider,
                                  Locale locale,
                                  String requestID,
                                  Object... params) {
            assert params.length == 0;

            // First, try to get names with the request ID
            String[] names = buildZoneStrings(timeZoneNameProvider, locale, requestID);

            if (names == null) {
                Map<String, String> aliases = ZoneInfo.getAliasTable();

                if (aliases != null) {
                    // Check whether this id is an alias, if so,
                    // look for the standard id.
                    String canonicalID = aliases.get(requestID);
                    if (canonicalID != null) {
                        names = buildZoneStrings(timeZoneNameProvider, locale, canonicalID);
                    }
                    if (names == null) {
                        // There may be a case that a standard id has become an
                        // alias.  so, check the aliases backward.
                        names = examineAliases(timeZoneNameProvider, locale,
                                   canonicalID == null ? requestID : canonicalID, aliases);
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
                                               Map<String, String> aliases) {
            if (aliases.containsValue(id)) {
                for (Map.Entry<String, String> entry : aliases.entrySet()) {
                    if (entry.getValue().equals(id)) {
                        String alias = entry.getKey();
                        String[] names = buildZoneStrings(tznp, locale, alias);
                        if (names != null) {
                            return names;
                        }
                        names = examineAliases(tznp, locale, alias, aliases);
                        if (names != null) {
                            return names;
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

                if (names[i] == null) {
                    switch (i) {
                    case 1:
                        // this id seems not localized by this provider
                        return null;
                    case 2:
                    case 4:
                        // If the display name for SHORT is not supplied,
                        // copy the LONG name.
                        names[i] = names[i-1];
                        break;
                    case 3:
                        // If the display name for DST is not supplied,
                        // copy the "standard" name.
                        names[3] = names[1];
                        break;
                }
            }
            }

            return names;
        }
    }

    private static class TimeZoneNameGetter
        implements LocaleServiceProviderPool.LocalizedObjectGetter<TimeZoneNameProvider,
                                                                   String> {
        private static final TimeZoneNameGetter INSTANCE =
            new TimeZoneNameGetter();

        @Override
        public String getObject(TimeZoneNameProvider timeZoneNameProvider,
                                Locale locale,
                                String requestID,
                                Object... params) {
            assert params.length == 2;
            int style = (int) params[0];
            String tzid = (String) params[1];
            String value = getName(timeZoneNameProvider, locale, requestID, style, tzid);
            if (value == null) {
                Map<String, String> aliases = ZoneInfo.getAliasTable();
                if (aliases != null) {
                    String canonicalID = aliases.get(tzid);
                    if (canonicalID != null) {
                        value = getName(timeZoneNameProvider, locale, requestID, style, canonicalID);
                    }
                    if (value == null) {
                        value = examineAliases(timeZoneNameProvider, locale, requestID,
                                     canonicalID != null ? canonicalID : tzid, style, aliases);
                    }
                }
            }

            return value;
        }

        private static String examineAliases(TimeZoneNameProvider tznp, Locale locale,
                                             String requestID, String tzid, int style,
                                             Map<String, String> aliases) {
            if (aliases.containsValue(tzid)) {
                for (Map.Entry<String, String> entry : aliases.entrySet()) {
                    if (entry.getValue().equals(tzid)) {
                        String alias = entry.getKey();
                        String name = getName(tznp, locale, requestID, style, alias);
                        if (name != null) {
                            return name;
                        }
                        name = examineAliases(tznp, locale, requestID, alias, style, aliases);
                        if (name != null) {
                            return name;
                        }
                    }
                }
            }
            return null;
        }

        private static String getName(TimeZoneNameProvider timeZoneNameProvider,
                                      Locale locale, String requestID, int style, String tzid) {
            String value = null;
            switch (requestID) {
            case "std":
                value = timeZoneNameProvider.getDisplayName(tzid, false, style, locale);
                break;
            case "dst":
                value = timeZoneNameProvider.getDisplayName(tzid, true, style, locale);
                break;
            case "generic":
                value = timeZoneNameProvider.getGenericDisplayName(tzid, style, locale);
                break;
            }
            return value;
        }
    }

    // No instantiation
    private TimeZoneNameUtility() {
    }
}
