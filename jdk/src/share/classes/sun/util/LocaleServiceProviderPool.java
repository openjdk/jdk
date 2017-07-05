/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

package sun.util;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Locale.Builder;
import java.util.Map;
import java.util.ResourceBundle.Control;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.spi.LocaleServiceProvider;

import sun.util.logging.PlatformLogger;
import sun.util.resources.LocaleData;
import sun.util.resources.OpenListResourceBundle;

/**
 * An instance of this class holds a set of the third party implementations of a particular
 * locale sensitive service, such as {@link java.util.spi.LocaleNameProvider}.
 *
 */
public final class LocaleServiceProviderPool {

    /**
     * A Map that holds singleton instances of this class.  Each instance holds a
     * set of provider implementations of a particular locale sensitive service.
     */
    private static Map<Class, LocaleServiceProviderPool> poolOfPools =
        new ConcurrentHashMap<Class, LocaleServiceProviderPool>();

    /**
     * A Set containing locale service providers that implement the
     * specified provider SPI
     */
    private Set<LocaleServiceProvider> providers =
        new LinkedHashSet<LocaleServiceProvider>();

    /**
     * A Map that retains Locale->provider mapping
     */
    private Map<Locale, LocaleServiceProvider> providersCache =
        new ConcurrentHashMap<Locale, LocaleServiceProvider>();

    /**
     * Available locales for this locale sensitive service.  This also contains
     * JRE's available locales
     */
    private Set<Locale> availableLocales = null;

    /**
     * Available locales within this JRE.  Currently this is declared as
     * static.  This could be non-static later, so that they could have
     * different sets for each locale sensitive services.
     */
    private static List<Locale> availableJRELocales = null;

    /**
     * Provider locales for this locale sensitive service.
     */
    private Set<Locale> providerLocales = null;

    /**
     * Special locale for ja_JP with Japanese calendar
     */
    private static Locale locale_ja_JP_JP = new Locale("ja", "JP", "JP");

    /**
     * Special locale for th_TH with Thai numbering system
     */
    private static Locale locale_th_TH_TH = new Locale("th", "TH", "TH");

    /**
     * A factory method that returns a singleton instance
     */
    public static LocaleServiceProviderPool getPool(Class<? extends LocaleServiceProvider> providerClass) {
        LocaleServiceProviderPool pool = poolOfPools.get(providerClass);
        if (pool == null) {
            LocaleServiceProviderPool newPool =
                new LocaleServiceProviderPool(providerClass);
            pool = poolOfPools.put(providerClass, newPool);
            if (pool == null) {
                pool = newPool;
            }
        }

        return pool;
    }

    /**
     * The sole constructor.
     *
     * @param c class of the locale sensitive service
     */
    private LocaleServiceProviderPool (final Class<? extends LocaleServiceProvider> c) {
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                public Object run() {
                    for (LocaleServiceProvider provider : ServiceLoader.loadInstalled(c)) {
                        providers.add(provider);
                    }
                    return null;
                }
            });
        }  catch (PrivilegedActionException e) {
            config(e.toString());
        }
    }

    private static void config(String message) {
        PlatformLogger logger = PlatformLogger.getLogger("sun.util.LocaleServiceProviderPool");
        logger.config(message);
    }

    /**
     * Lazy loaded set of available locales.
     * Loading all locales is a very long operation.
     */
    private static class AllAvailableLocales {
        /**
         * Available locales for all locale sensitive services.
         * This also contains JRE's available locales
         */
        static final Locale[] allAvailableLocales;

        static {
            Class[] providerClasses = {
                java.text.spi.BreakIteratorProvider.class,
                java.text.spi.CollatorProvider.class,
                java.text.spi.DateFormatProvider.class,
                java.text.spi.DateFormatSymbolsProvider.class,
                java.text.spi.DecimalFormatSymbolsProvider.class,
                java.text.spi.NumberFormatProvider.class,
                java.util.spi.CurrencyNameProvider.class,
                java.util.spi.LocaleNameProvider.class,
                java.util.spi.TimeZoneNameProvider.class };

            // Normalize locales for look up
            Locale[] allLocales = LocaleData.getAvailableLocales();
            Set<Locale> all = new HashSet<Locale>(allLocales.length);
            for (Locale locale : allLocales) {
                all.add(getLookupLocale(locale));
            }

            for (Class providerClass : providerClasses) {
                LocaleServiceProviderPool pool =
                    LocaleServiceProviderPool.getPool(providerClass);
                all.addAll(pool.getProviderLocales());
            }

            allAvailableLocales = all.toArray(new Locale[0]);
        }
    }

    /**
     * Returns an array of available locales for all the provider classes.
     * This array is a merged array of all the locales that are provided by each
     * provider, including the JRE.
     *
     * @return an array of the available locales for all provider classes
     */
    public static Locale[] getAllAvailableLocales() {
        return AllAvailableLocales.allAvailableLocales.clone();
    }

    /**
     * Returns an array of available locales.  This array is a
     * merged array of all the locales that are provided by each
     * provider, including the JRE.
     *
     * @return an array of the available locales
     */
    public synchronized Locale[] getAvailableLocales() {
        if (availableLocales == null) {
            availableLocales = new HashSet<Locale>(getJRELocales());
            if (hasProviders()) {
                availableLocales.addAll(getProviderLocales());
            }
        }
        Locale[] tmp = new Locale[availableLocales.size()];
        availableLocales.toArray(tmp);
        return tmp;
    }

    /**
     * Returns an array of available locales (already normalized
     * for service lookup) from providers.
     * Note that this method does not return a defensive copy.
     *
     * @return list of the provider locales
     */
    private synchronized Set<Locale> getProviderLocales() {
        if (providerLocales == null) {
            providerLocales = new HashSet<Locale>();
            if (hasProviders()) {
                for (LocaleServiceProvider lsp : providers) {
                    Locale[] locales = lsp.getAvailableLocales();
                    for (Locale locale: locales) {
                        providerLocales.add(getLookupLocale(locale));
                    }
                }
            }
        }
        return providerLocales;
    }

    /**
     * Returns whether any provider for this locale sensitive
     * service is available or not.
     *
     * @return true if any provider is available
     */
    public boolean hasProviders() {
        return !providers.isEmpty();
    }

    /**
     * Returns an array of available locales (already normalized for
     * service lookup) supported by the JRE.
     * Note that this method does not return a defensive copy.
     *
     * @return list of the available JRE locales
     */
    private synchronized List<Locale> getJRELocales() {
        if (availableJRELocales == null) {
            Locale[] allLocales = LocaleData.getAvailableLocales();
            availableJRELocales = new ArrayList<Locale>(allLocales.length);
            for (Locale locale : allLocales) {
                availableJRELocales.add(getLookupLocale(locale));
            }
        }
        return availableJRELocales;
    }

    /**
     * Returns whether the given locale is supported by the JRE.
     *
     * @param locale the locale to test.
     * @return true, if the locale is supported by the JRE. false
     *     otherwise.
     */
    private boolean isJRESupported(Locale locale) {
        List<Locale> locales = getJRELocales();
        return locales.contains(getLookupLocale(locale));
    }

    /**
     * Returns the provider's localized object for the specified
     * locale.
     *
     * @param getter an object on which getObject() method
     *     is called to obtain the provider's instance.
     * @param locale the given locale that is used as the starting one
     * @param params provider specific parameters
     * @return provider's instance, or null.
     */
    public <P, S> S getLocalizedObject(LocalizedObjectGetter<P, S> getter,
                                     Locale locale,
                                     Object... params) {
        return getLocalizedObjectImpl(getter, locale, true, null, null, null, params);
    }

    /**
     * Returns the provider's localized name for the specified
     * locale.
     *
     * @param getter an object on which getObject() method
     *     is called to obtain the provider's instance.
     * @param locale the given locale that is used as the starting one
     * @param bundle JRE resource bundle that contains
     *     the localized names, or null for localized objects.
     * @param key the key string if bundle is supplied, otherwise null.
     * @param params provider specific parameters
     * @return provider's instance, or null.
     */
    public <P, S> S getLocalizedObject(LocalizedObjectGetter<P, S> getter,
                                     Locale locale,
                                     OpenListResourceBundle bundle,
                                     String key,
                                     Object... params) {
        return getLocalizedObjectImpl(getter, locale, false, null, bundle, key, params);
    }

    /**
     * Returns the provider's localized name for the specified
     * locale.
     *
     * @param getter an object on which getObject() method
     *     is called to obtain the provider's instance.
     * @param locale the given locale that is used as the starting one
     * @param bundleKey JRE specific bundle key. e.g., "USD" is for currency
           symbol and "usd" is for currency display name in the JRE bundle.
     * @param bundle JRE resource bundle that contains
     *     the localized names, or null for localized objects.
     * @param key the key string if bundle is supplied, otherwise null.
     * @param params provider specific parameters
     * @return provider's instance, or null.
     */
    public <P, S> S getLocalizedObject(LocalizedObjectGetter<P, S> getter,
                                     Locale locale,
                                     String bundleKey,
                                     OpenListResourceBundle bundle,
                                     String key,
                                     Object... params) {
        return getLocalizedObjectImpl(getter, locale, false, bundleKey, bundle, key, params);
    }

    private <P, S> S getLocalizedObjectImpl(LocalizedObjectGetter<P, S> getter,
                                     Locale locale,
                                     boolean isObjectProvider,
                                     String bundleKey,
                                     OpenListResourceBundle bundle,
                                     String key,
                                     Object... params) {
        if (hasProviders()) {
            if (bundleKey == null) {
                bundleKey = key;
            }
            Locale bundleLocale = (bundle != null ? bundle.getLocale() : null);
            List<Locale> lookupLocales = getLookupLocales(locale);
            P lsp;
            S providersObj = null;

            // check whether a provider has an implementation that's closer
            // to the requested locale than the bundle we've found (for
            // localized names), or Java runtime's supported locale
            // (for localized objects)
            Set<Locale> provLoc = getProviderLocales();
            for (int i = 0; i < lookupLocales.size(); i++) {
                Locale current = lookupLocales.get(i);
                if (bundleLocale != null) {
                    if (current.equals(bundleLocale)) {
                        break;
                    }
                } else {
                    if (isJRESupported(current)) {
                        break;
                    }
                }
                if (provLoc.contains(current)) {
                    lsp = (P)findProvider(current);
                    if (lsp != null) {
                        providersObj = getter.getObject(lsp, locale, key, params);
                        if (providersObj != null) {
                            return providersObj;
                        } else if (isObjectProvider) {
                            config(
                                "A locale sensitive service provider returned null for a localized objects,  which should not happen.  provider: " + lsp + " locale: " + locale);
                        }
                    }
                }
            }

            // look up the JRE bundle and its parent chain.  Only
            // providers for localized names are checked hereafter.
            while (bundle != null) {
                bundleLocale = bundle.getLocale();

                if (bundle.handleGetKeys().contains(bundleKey)) {
                    // JRE has it.
                    return null;
                } else {
                    lsp = (P)findProvider(bundleLocale);
                    if (lsp != null) {
                        providersObj = getter.getObject(lsp, locale, key, params);
                        if (providersObj != null) {
                            return providersObj;
                        }
                    }
                }

                // try parent bundle
                bundle = bundle.getParent();
            }
        }

        // not found.
        return null;
    }

    /**
     * Returns a locale service provider instance that supports
     * the specified locale.
     *
     * @param locale the given locale
     * @return the provider, or null if there is
     *     no provider available.
     */
    private LocaleServiceProvider findProvider(Locale locale) {
        if (!hasProviders()) {
            return null;
        }

        if (providersCache.containsKey(locale)) {
            LocaleServiceProvider provider = providersCache.get(locale);
            if (provider != NullProvider.INSTANCE) {
                return provider;
            }
        } else {
            for (LocaleServiceProvider lsp : providers) {
                Locale[] locales = lsp.getAvailableLocales();
                for (Locale available: locales) {
                    // normalize
                    available = getLookupLocale(available);
                    if (locale.equals(available)) {
                        LocaleServiceProvider providerInCache =
                            providersCache.put(locale, lsp);
                        return (providerInCache != null ?
                                providerInCache :
                                lsp);
                    }
                }
            }
            providersCache.put(locale, NullProvider.INSTANCE);
        }
        return null;
    }

    /**
     * Returns a list of candidate locales for service look up.
     * @param locale the input locale
     * @return the list of candiate locales for the given locale
     */
    private static List<Locale> getLookupLocales(Locale locale) {
        // Note: We currently use the default implementation of
        // ResourceBundle.Control.getCandidateLocales. The result
        // returned by getCandidateLocales are already normalized
        // (no extensions) for service look up.
        List<Locale> lookupLocales = new Control(){}.getCandidateLocales("", locale);
        return lookupLocales;
    }

    /**
     * Returns an instance of Locale used for service look up.
     * The result Locale has no extensions except for ja_JP_JP
     * and th_TH_TH
     *
     * @param locale the locale
     * @return the locale used for service look up
     */
    private static Locale getLookupLocale(Locale locale) {
        Locale lookupLocale = locale;
        Set<Character> extensions = locale.getExtensionKeys();
        if (!extensions.isEmpty()
                && !locale.equals(locale_ja_JP_JP)
                && !locale.equals(locale_th_TH_TH)) {
            // remove extensions
            Builder locbld = new Builder();
            try {
                locbld.setLocale(locale);
                locbld.clearExtensions();
                lookupLocale = locbld.build();
            } catch (IllformedLocaleException e) {
                // A Locale with non-empty extensions
                // should have well-formed fields except
                // for ja_JP_JP and th_TH_TH. Therefore,
                // it should never enter in this catch clause.
                config("A locale(" + locale + ") has non-empty extensions, but has illformed fields.");

                // Fallback - script field will be lost.
                lookupLocale = new Locale(locale.getLanguage(), locale.getCountry(), locale.getVariant());
            }
        }
        return lookupLocale;
    }

    /**
     * A dummy locale service provider that indicates there is no
     * provider available
     */
    private static class NullProvider extends LocaleServiceProvider {
        private static final NullProvider INSTANCE = new NullProvider();

        public Locale[] getAvailableLocales() {
            throw new RuntimeException("Should not get called.");
        }
    }

    /**
     * An interface to get a localized object for each locale sensitve
     * service class.
     */
    public interface LocalizedObjectGetter<P, S> {
        /**
         * Returns an object from the provider
         *
         * @param lsp the provider
         * @param locale the locale
         * @param key key string to localize, or null if the provider is not
         *     a name provider
         * @param params provider specific params
         * @return localized object from the provider
         */
        public S getObject(P lsp,
                                Locale locale,
                                String key,
                                Object... params);
    }
}
