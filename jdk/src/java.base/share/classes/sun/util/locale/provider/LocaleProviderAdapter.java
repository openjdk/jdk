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

import java.security.AccessController;
import java.text.spi.BreakIteratorProvider;
import java.text.spi.CollatorProvider;
import java.text.spi.DateFormatProvider;
import java.text.spi.DateFormatSymbolsProvider;
import java.text.spi.DecimalFormatSymbolsProvider;
import java.text.spi.NumberFormatProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.spi.CalendarDataProvider;
import java.util.spi.CalendarNameProvider;
import java.util.spi.CurrencyNameProvider;
import java.util.spi.LocaleNameProvider;
import java.util.spi.LocaleServiceProvider;
import java.util.spi.TimeZoneNameProvider;
import sun.util.cldr.CLDRLocaleProviderAdapter;
import sun.util.spi.CalendarProvider;

/**
 * The LocaleProviderAdapter abstract class.
 *
 * @author Naoto Sato
 * @author Masayoshi Okutsu
 */
public abstract class LocaleProviderAdapter {
    /**
     * Adapter type.
     */
    public static enum Type {
        JRE("sun.util.resources", "sun.text.resources"),
        CLDR("sun.util.resources.cldr", "sun.text.resources.cldr"),
        SPI,
        HOST,
        FALLBACK("sun.util.resources", "sun.text.resources");

        private final String UTIL_RESOURCES_PACKAGE;
        private final String TEXT_RESOURCES_PACKAGE;

        private Type() {
            this(null, null);
        }

        private Type(String util, String text) {
            UTIL_RESOURCES_PACKAGE = util;
            TEXT_RESOURCES_PACKAGE = text;
        }

        public String getUtilResourcesPackage() {
            return UTIL_RESOURCES_PACKAGE;
        }

        public String getTextResourcesPackage() {
            return TEXT_RESOURCES_PACKAGE;
        }
    }

    /**
     * LocaleProviderAdapter preference list. The default list is intended
     * to behave the same manner in JDK7.
     */
    private static final List<Type> adapterPreference;

    /**
     * JRE Locale Data Adapter instance
     */
    private static LocaleProviderAdapter jreLocaleProviderAdapter = new JRELocaleProviderAdapter();

    /**
     * SPI Locale Data Adapter instance
     */
    private static LocaleProviderAdapter spiLocaleProviderAdapter = new SPILocaleProviderAdapter();

    /**
     * CLDR Locale Data Adapter instance, if any.
     */
    private static LocaleProviderAdapter cldrLocaleProviderAdapter = null;

    /**
     * HOST Locale Data Adapter instance, if any.
     */
    private static LocaleProviderAdapter hostLocaleProviderAdapter = null;

    /**
     * FALLBACK Locale Data Adapter instance. It's basically the same with JRE, but only kicks
     * in for the root locale.
     */
    private static LocaleProviderAdapter fallbackLocaleProviderAdapter = null;

    /**
     * Default fallback adapter type, which should return something meaningful in any case.
     * This is either JRE or FALLBACK.
     */
    static LocaleProviderAdapter.Type defaultLocaleProviderAdapter = null;

    /**
     * Adapter lookup cache.
     */
    private static ConcurrentMap<Class<? extends LocaleServiceProvider>, ConcurrentMap<Locale, LocaleProviderAdapter>>
        adapterCache = new ConcurrentHashMap<>();

    static {
        String order = AccessController.doPrivileged(
                           new sun.security.action.GetPropertyAction("java.locale.providers"));
        List<Type> typeList = new ArrayList<>();

        // Check user specified adapter preference
        if (order != null && order.length() != 0) {
            String[] types = order.split(",");
            for (String type : types) {
                try {
                    Type aType = Type.valueOf(type.trim().toUpperCase(Locale.ROOT));

                    // load adapter if necessary
                    switch (aType) {
                        case CLDR:
                            if (cldrLocaleProviderAdapter == null) {
                                cldrLocaleProviderAdapter = new CLDRLocaleProviderAdapter();
                            }
                            break;
                        case HOST:
                            if (hostLocaleProviderAdapter == null) {
                                hostLocaleProviderAdapter = new HostLocaleProviderAdapter();
                            }
                            break;
                    }
                    if (!typeList.contains(aType)) {
                        typeList.add(aType);
                    }
                } catch (IllegalArgumentException | UnsupportedOperationException e) {
                    // could be caused by the user specifying wrong
                    // provider name or format in the system property
                    LocaleServiceProviderPool.config(LocaleProviderAdapter.class, e.toString());
                }
            }
        }

        if (!typeList.isEmpty()) {
            if (!typeList.contains(Type.JRE)) {
                // Append FALLBACK as the last resort.
                fallbackLocaleProviderAdapter = new FallbackLocaleProviderAdapter();
                typeList.add(Type.FALLBACK);
                defaultLocaleProviderAdapter = Type.FALLBACK;
            } else {
                defaultLocaleProviderAdapter = Type.JRE;
            }
        } else {
            // Default preference list
            typeList.add(Type.JRE);
            typeList.add(Type.SPI);
            defaultLocaleProviderAdapter = Type.JRE;
        }

        adapterPreference = Collections.unmodifiableList(typeList);
    }

    /**
     * Returns the singleton instance for each adapter type
     */
    public static LocaleProviderAdapter forType(Type type) {
        switch (type) {
        case JRE:
            return jreLocaleProviderAdapter;
        case CLDR:
            return cldrLocaleProviderAdapter;
        case SPI:
            return spiLocaleProviderAdapter;
        case HOST:
            return hostLocaleProviderAdapter;
        case FALLBACK:
            return fallbackLocaleProviderAdapter;
        default:
            throw new InternalError("unknown locale data adapter type");
        }
    }

    public static LocaleProviderAdapter forJRE() {
        return jreLocaleProviderAdapter;
    }

    public static LocaleProviderAdapter getResourceBundleBased() {
        for (Type type : getAdapterPreference()) {
            if (type == Type.JRE || type == Type.CLDR || type == Type.FALLBACK) {
                return forType(type);
            }
        }
        // Shouldn't happen.
        throw new InternalError();
    }
    /**
     * Returns the preference order of LocaleProviderAdapter.Type
     */
    public static List<Type> getAdapterPreference() {
        return adapterPreference;
    }

    /**
     * Returns a LocaleProviderAdapter for the given locale service provider that
     * best matches the given locale. This method returns the LocaleProviderAdapter
     * for JRE if none is found for the given locale.
     *
     * @param providerClass the class for the locale service provider
     * @param locale the desired locale.
     * @return a LocaleProviderAdapter
     */
    public static LocaleProviderAdapter getAdapter(Class<? extends LocaleServiceProvider> providerClass,
                                               Locale locale) {
        LocaleProviderAdapter adapter;

        // cache lookup
        ConcurrentMap<Locale, LocaleProviderAdapter> adapterMap = adapterCache.get(providerClass);
        if (adapterMap != null) {
            if ((adapter = adapterMap.get(locale)) != null) {
                return adapter;
            }
        } else {
            adapterMap = new ConcurrentHashMap<>();
            adapterCache.putIfAbsent(providerClass, adapterMap);
        }

        // Fast look-up for the given locale
        adapter = findAdapter(providerClass, locale);
        if (adapter != null) {
            adapterMap.putIfAbsent(locale, adapter);
            return adapter;
        }

        // Try finding an adapter in the normal candidate locales path of the given locale.
        List<Locale> lookupLocales = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_DEFAULT)
                                        .getCandidateLocales("", locale);
        for (Locale loc : lookupLocales) {
            if (loc.equals(locale)) {
                // We've already done with this loc.
                continue;
            }
            adapter = findAdapter(providerClass, loc);
            if (adapter != null) {
                adapterMap.putIfAbsent(locale, adapter);
                return adapter;
            }
        }

        // returns the adapter for FALLBACK as the last resort
        adapterMap.putIfAbsent(locale, fallbackLocaleProviderAdapter);
        return fallbackLocaleProviderAdapter;
    }

    private static LocaleProviderAdapter findAdapter(Class<? extends LocaleServiceProvider> providerClass,
                                                 Locale locale) {
        for (Type type : getAdapterPreference()) {
            LocaleProviderAdapter adapter = forType(type);
            LocaleServiceProvider provider = adapter.getLocaleServiceProvider(providerClass);
            if (provider != null) {
                if (provider.isSupportedLocale(locale)) {
                    return adapter;
                }
            }
        }
        return null;
    }

    /**
     * A utility method for implementing the default LocaleServiceProvider.isSupportedLocale
     * for the JRE, CLDR, and FALLBACK adapters.
     */
    static boolean isSupportedLocale(Locale locale, LocaleProviderAdapter.Type type, Set<String> langtags) {
        assert type == Type.JRE || type == Type.CLDR || type == Type.FALLBACK;
        if (Locale.ROOT.equals(locale)) {
            return true;
        }

        if (type == Type.FALLBACK) {
            // no other locales except ROOT are supported for FALLBACK
            return false;
        }

        locale = locale.stripExtensions();
        if (langtags.contains(locale.toLanguageTag())) {
            return true;
        }
        if (type == Type.JRE) {
            String oldname = locale.toString().replace('_', '-');
            return langtags.contains(oldname) ||
                   "ja-JP-JP".equals(oldname) ||
                   "th-TH-TH".equals(oldname) ||
                   "no-NO-NY".equals(oldname);
        }
        return false;
    }

    public static Locale[] toLocaleArray(Set<String> tags) {
        Locale[] locs = new Locale[tags.size() + 1];
        int index = 0;
        locs[index++] = Locale.ROOT;
        for (String tag : tags) {
            switch (tag) {
            case "ja-JP-JP":
                locs[index++] = JRELocaleConstants.JA_JP_JP;
                break;
            case "th-TH-TH":
                locs[index++] = JRELocaleConstants.TH_TH_TH;
                break;
            default:
                locs[index++] = Locale.forLanguageTag(tag);
                break;
            }
        }
        return locs;
    }

    /**
     * Returns the type of this LocaleProviderAdapter
     */
    public abstract LocaleProviderAdapter.Type getAdapterType();

    /**
     * Getter method for Locale Service Providers.
     */
    public abstract <P extends LocaleServiceProvider> P getLocaleServiceProvider(Class<P> c);

    /**
     * Returns a BreakIteratorProvider for this LocaleProviderAdapter, or null if no
     * BreakIteratorProvider is available.
     *
     * @return a BreakIteratorProvider
     */
    public abstract BreakIteratorProvider getBreakIteratorProvider();

    /**
     * Returns a ollatorProvider for this LocaleProviderAdapter, or null if no
     * ollatorProvider is available.
     *
     * @return a ollatorProvider
     */
    public abstract CollatorProvider getCollatorProvider();

    /**
     * Returns a DateFormatProvider for this LocaleProviderAdapter, or null if no
     * DateFormatProvider is available.
     *
     * @return a DateFormatProvider
     */
    public abstract DateFormatProvider getDateFormatProvider();

    /**
     * Returns a DateFormatSymbolsProvider for this LocaleProviderAdapter, or null if no
     * DateFormatSymbolsProvider is available.
     *
     * @return a DateFormatSymbolsProvider
     */
    public abstract DateFormatSymbolsProvider getDateFormatSymbolsProvider();

    /**
     * Returns a DecimalFormatSymbolsProvider for this LocaleProviderAdapter, or null if no
     * DecimalFormatSymbolsProvider is available.
     *
     * @return a DecimalFormatSymbolsProvider
     */
    public abstract DecimalFormatSymbolsProvider getDecimalFormatSymbolsProvider();

    /**
     * Returns a NumberFormatProvider for this LocaleProviderAdapter, or null if no
     * NumberFormatProvider is available.
     *
     * @return a NumberFormatProvider
     */
    public abstract NumberFormatProvider getNumberFormatProvider();

    /*
     * Getter methods for java.util.spi.* providers
     */

    /**
     * Returns a CurrencyNameProvider for this LocaleProviderAdapter, or null if no
     * CurrencyNameProvider is available.
     *
     * @return a CurrencyNameProvider
     */
    public abstract CurrencyNameProvider getCurrencyNameProvider();

    /**
     * Returns a LocaleNameProvider for this LocaleProviderAdapter, or null if no
     * LocaleNameProvider is available.
     *
     * @return a LocaleNameProvider
     */
    public abstract LocaleNameProvider getLocaleNameProvider();

    /**
     * Returns a TimeZoneNameProvider for this LocaleProviderAdapter, or null if no
     * TimeZoneNameProvider is available.
     *
     * @return a TimeZoneNameProvider
     */
    public abstract TimeZoneNameProvider getTimeZoneNameProvider();

    /**
     * Returns a CalendarDataProvider for this LocaleProviderAdapter, or null if no
     * CalendarDataProvider is available.
     *
     * @return a CalendarDataProvider
     */
    public abstract CalendarDataProvider getCalendarDataProvider();

    /**
     * Returns a CalendarNameProvider for this LocaleProviderAdapter, or null if no
     * CalendarNameProvider is available.
     *
     * @return a CalendarNameProvider
     */
    public abstract CalendarNameProvider getCalendarNameProvider();

    /**
     * Returns a CalendarProvider for this LocaleProviderAdapter, or null if no
     * CalendarProvider is available.
     *
     * @return a CalendarProvider
     */
    public abstract CalendarProvider getCalendarProvider();

    public abstract LocaleResources getLocaleResources(Locale locale);

    public abstract Locale[] getAvailableLocales();
}
