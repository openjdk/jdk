/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.util;

import java.lang.ref.SoftReference;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.spi.TimeZoneNameProvider;
import sun.util.calendar.ZoneInfo;
import sun.util.resources.LocaleData;
import sun.util.resources.OpenListResourceBundle;

/**
 * Utility class that deals with the localized time zone names
 */
public final class TimeZoneNameUtility {

    /**
     * cache to hold time zone resource bundles. Keyed by Locale
     */
    private static ConcurrentHashMap<Locale, SoftReference<OpenListResourceBundle>> cachedBundles =
        new ConcurrentHashMap<Locale, SoftReference<OpenListResourceBundle>>();

    /**
     * cache to hold time zone localized strings. Keyed by Locale
     */
    private static ConcurrentHashMap<Locale, SoftReference<String[][]>> cachedZoneData =
        new ConcurrentHashMap<Locale, SoftReference<String[][]>>();

    /**
     * get time zone localized strings. Enumerate all keys.
     */
    public static final String[][] getZoneStrings(Locale locale) {
        String[][] zones;
        SoftReference<String[][]> data = cachedZoneData.get(locale);

        if (data == null || ((zones = data.get()) == null)) {
            zones = loadZoneStrings(locale);
            data = new SoftReference<String[][]>(zones);
            cachedZoneData.put(locale, data);
        }

        return zones;
    }

    private static final String[][] loadZoneStrings(Locale locale) {
        List<String[]> zones = new LinkedList<String[]>();
        OpenListResourceBundle rb = getBundle(locale);
        Enumeration<String> keys = rb.getKeys();
        String[] names = null;

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
    public static final String[] retrieveDisplayNames(String id, Locale locale) {
        OpenListResourceBundle rb = getBundle(locale);
        return retrieveDisplayNames(rb, id, locale);
    }

    private static final String[] retrieveDisplayNames(OpenListResourceBundle rb,
                                                String id, Locale locale) {
        LocaleServiceProviderPool pool =
            LocaleServiceProviderPool.getPool(TimeZoneNameProvider.class);
        String[] names = null;

        // Check whether a provider can provide an implementation that's closer
        // to the requested locale than what the Java runtime itself can provide.
        if (pool.hasProviders()) {
            names = pool.getLocalizedObject(
                            TimeZoneNameGetter.INSTANCE,
                            locale, rb, id);
        }

        if (names == null) {
            try {
                names = rb.getStringArray(id);
            } catch (MissingResourceException mre) {
                // fall through
            }
        }

        return names;
    }

    private static final OpenListResourceBundle getBundle(Locale locale) {
        OpenListResourceBundle rb;
        SoftReference<OpenListResourceBundle> data = cachedBundles.get(locale);

        if (data == null || ((rb = data.get()) == null)) {
            rb = LocaleData.getTimeZoneNames(locale);
            data = new SoftReference<OpenListResourceBundle>(rb);
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

        public String[] getObject(TimeZoneNameProvider timeZoneNameProvider,
                                Locale locale,
                                String requestID,
                                Object... params) {
            assert params.length == 0;
            String[] names = null;
            String queryID = requestID;

            if (queryID.equals("GMT")) {
                names = buildZoneStrings(timeZoneNameProvider, locale, queryID);
            } else {
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
}
