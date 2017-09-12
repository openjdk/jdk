/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.BreakIterator;
import java.text.Collator;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.spi.BreakIteratorProvider;
import java.text.spi.CollatorProvider;
import java.text.spi.DateFormatProvider;
import java.text.spi.DateFormatSymbolsProvider;
import java.text.spi.DecimalFormatSymbolsProvider;
import java.text.spi.NumberFormatProvider;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.spi.CalendarDataProvider;
import java.util.spi.CalendarNameProvider;
import java.util.spi.CurrencyNameProvider;
import java.util.spi.LocaleNameProvider;
import java.util.spi.LocaleServiceProvider;
import java.util.spi.TimeZoneNameProvider;

/**
 * LocaleProviderAdapter implementation for the installed SPI implementations.
 *
 * @author Naoto Sato
 * @author Masayoshi Okutsu
 */
public class SPILocaleProviderAdapter extends AuxLocaleProviderAdapter {

    /**
     * Returns the type of this LocaleProviderAdapter
     */
    @Override
    public LocaleProviderAdapter.Type getAdapterType() {
        return LocaleProviderAdapter.Type.SPI;
    }

    @Override
    protected <P extends LocaleServiceProvider> P findInstalledProvider(final Class<P> c) {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<P>() {
                @Override
                @SuppressWarnings(value={"unchecked", "deprecation"})
                public P run() {
                    P delegate = null;

                    for (LocaleServiceProvider provider :
                             ServiceLoader.load(c, ClassLoader.getSystemClassLoader())) {
                        if (delegate == null) {
                            try {
                                delegate =
                                    (P) Class.forName(SPILocaleProviderAdapter.class.getCanonicalName() +
                                              "$" +
                                              c.getSimpleName() +
                                              "Delegate")
                                              .newInstance();
                            }  catch (ClassNotFoundException |
                                      InstantiationException |
                                      IllegalAccessException e) {
                                LocaleServiceProviderPool.config(SPILocaleProviderAdapter.class, e.toString());
                                return null;
                            }
                        }

                        ((Delegate)delegate).addImpl(provider);
                    }
                    return delegate;
                }
            });
        }  catch (PrivilegedActionException e) {
            LocaleServiceProviderPool.config(SPILocaleProviderAdapter.class, e.toString());
        }
        return null;
    }

    /*
     * Delegate interface. All the implementations have to have the class name
     * following "<provider class name>Delegate" convention.
     */
    interface Delegate<P extends LocaleServiceProvider> {
        public void addImpl(P impl);
        public P getImpl(Locale locale);
    }

    /*
     * Obtain the real SPI implementation, using locale fallback
     */
    private static <P extends LocaleServiceProvider> P getImpl(Map<Locale, P> map, Locale locale) {
        for (Locale l : LocaleServiceProviderPool.getLookupLocales(locale)) {
            P ret = map.get(l);
            if (ret != null) {
                return ret;
            }
        }
        return null;
    }

    /*
     * Delegates for the actual SPI implementations.
     */
    static class BreakIteratorProviderDelegate extends BreakIteratorProvider
                                        implements Delegate<BreakIteratorProvider> {
        private final ConcurrentMap<Locale, BreakIteratorProvider> map = new ConcurrentHashMap<>();

        @Override
        public void addImpl(BreakIteratorProvider impl) {
            for (Locale l : impl.getAvailableLocales()) {
                map.putIfAbsent(l, impl);
            }
        }

        @Override
        public BreakIteratorProvider getImpl(Locale locale) {
            return SPILocaleProviderAdapter.getImpl(map, locale);
        }

        @Override
        public Locale[] getAvailableLocales() {
            return map.keySet().toArray(new Locale[0]);
        }

        @Override
        public boolean isSupportedLocale(Locale locale) {
            return map.containsKey(locale);
        }

        @Override
        public BreakIterator getWordInstance(Locale locale) {
            BreakIteratorProvider bip = getImpl(locale);
            assert bip != null;
            return bip.getWordInstance(locale);
        }

        @Override
        public BreakIterator getLineInstance(Locale locale) {
            BreakIteratorProvider bip = getImpl(locale);
            assert bip != null;
            return bip.getLineInstance(locale);
        }

        @Override
        public BreakIterator getCharacterInstance(Locale locale) {
            BreakIteratorProvider bip = getImpl(locale);
            assert bip != null;
            return bip.getCharacterInstance(locale);
        }

        @Override
        public BreakIterator getSentenceInstance(Locale locale) {
            BreakIteratorProvider bip = getImpl(locale);
            assert bip != null;
            return bip.getSentenceInstance(locale);
        }

    }

    static class CollatorProviderDelegate extends CollatorProvider implements Delegate<CollatorProvider> {
        private final ConcurrentMap<Locale, CollatorProvider> map = new ConcurrentHashMap<>();

        @Override
        public void addImpl(CollatorProvider impl) {
            for (Locale l : impl.getAvailableLocales()) {
                map.putIfAbsent(l, impl);
            }
        }

        @Override
        public CollatorProvider getImpl(Locale locale) {
            return SPILocaleProviderAdapter.getImpl(map, locale);
        }

        @Override
        public Locale[] getAvailableLocales() {
            return map.keySet().toArray(new Locale[0]);
        }

        @Override
        public boolean isSupportedLocale(Locale locale) {
            return map.containsKey(locale);
        }

        @Override
        public Collator getInstance(Locale locale) {
            CollatorProvider cp = getImpl(locale);
            assert cp != null;
            return cp.getInstance(locale);
        }
    }

    static class DateFormatProviderDelegate extends DateFormatProvider
                                     implements Delegate<DateFormatProvider> {
        private final ConcurrentMap<Locale, DateFormatProvider> map = new ConcurrentHashMap<>();

        @Override
        public void addImpl(DateFormatProvider impl) {
            for (Locale l : impl.getAvailableLocales()) {
                map.putIfAbsent(l, impl);
            }
        }

        @Override
        public DateFormatProvider getImpl(Locale locale) {
            return SPILocaleProviderAdapter.getImpl(map, locale);
        }

        @Override
        public Locale[] getAvailableLocales() {
            return map.keySet().toArray(new Locale[0]);
        }

        @Override
        public boolean isSupportedLocale(Locale locale) {
            return map.containsKey(locale);
        }

        @Override
        public DateFormat getTimeInstance(int style, Locale locale) {
            DateFormatProvider dfp = getImpl(locale);
            assert dfp != null;
            return dfp.getTimeInstance(style, locale);
        }

        @Override
        public DateFormat getDateInstance(int style, Locale locale) {
            DateFormatProvider dfp = getImpl(locale);
            assert dfp != null;
            return dfp.getDateInstance(style, locale);
        }

        @Override
        public DateFormat getDateTimeInstance(int dateStyle, int timeStyle, Locale locale) {
            DateFormatProvider dfp = getImpl(locale);
            assert dfp != null;
            return dfp.getDateTimeInstance(dateStyle, timeStyle, locale);
        }
    }

    static class DateFormatSymbolsProviderDelegate extends DateFormatSymbolsProvider
                                            implements Delegate<DateFormatSymbolsProvider> {
        private final ConcurrentMap<Locale, DateFormatSymbolsProvider> map = new ConcurrentHashMap<>();

        @Override
        public void addImpl(DateFormatSymbolsProvider impl) {
            for (Locale l : impl.getAvailableLocales()) {
                map.putIfAbsent(l, impl);
            }
        }

        @Override
        public DateFormatSymbolsProvider getImpl(Locale locale) {
            return SPILocaleProviderAdapter.getImpl(map, locale);
        }

        @Override
        public Locale[] getAvailableLocales() {
            return map.keySet().toArray(new Locale[0]);
        }

        @Override
        public boolean isSupportedLocale(Locale locale) {
            return map.containsKey(locale);
        }

        @Override
        public DateFormatSymbols getInstance(Locale locale) {
            DateFormatSymbolsProvider dfsp = getImpl(locale);
            assert dfsp != null;
            return dfsp.getInstance(locale);
        }
    }

    static class DecimalFormatSymbolsProviderDelegate extends DecimalFormatSymbolsProvider
                                               implements Delegate<DecimalFormatSymbolsProvider> {
        private final ConcurrentMap<Locale, DecimalFormatSymbolsProvider> map = new ConcurrentHashMap<>();

        @Override
        public void addImpl(DecimalFormatSymbolsProvider impl) {
            for (Locale l : impl.getAvailableLocales()) {
                map.putIfAbsent(l, impl);
            }
        }

        @Override
        public DecimalFormatSymbolsProvider getImpl(Locale locale) {
            return SPILocaleProviderAdapter.getImpl(map, locale);
        }

        @Override
        public Locale[] getAvailableLocales() {
            return map.keySet().toArray(new Locale[0]);
        }

        @Override
        public boolean isSupportedLocale(Locale locale) {
            return map.containsKey(locale);
        }

        @Override
        public DecimalFormatSymbols getInstance(Locale locale) {
            DecimalFormatSymbolsProvider dfsp = getImpl(locale);
            assert dfsp != null;
            return dfsp.getInstance(locale);
        }
    }

    static class NumberFormatProviderDelegate extends NumberFormatProvider
                                       implements Delegate<NumberFormatProvider> {
        private final ConcurrentMap<Locale, NumberFormatProvider> map = new ConcurrentHashMap<>();

        @Override
        public void addImpl(NumberFormatProvider impl) {
            for (Locale l : impl.getAvailableLocales()) {
                map.putIfAbsent(l, impl);
            }
        }

        @Override
        public NumberFormatProvider getImpl(Locale locale) {
            return SPILocaleProviderAdapter.getImpl(map, locale);
        }

        @Override
        public Locale[] getAvailableLocales() {
            return map.keySet().toArray(new Locale[0]);
        }

        @Override
        public boolean isSupportedLocale(Locale locale) {
            return map.containsKey(locale);
        }

        @Override
        public NumberFormat getCurrencyInstance(Locale locale) {
            NumberFormatProvider nfp = getImpl(locale);
            assert nfp != null;
            return nfp.getCurrencyInstance(locale);
        }

        @Override
        public NumberFormat getIntegerInstance(Locale locale) {
            NumberFormatProvider nfp = getImpl(locale);
            assert nfp != null;
            return nfp.getIntegerInstance(locale);
        }

        @Override
        public NumberFormat getNumberInstance(Locale locale) {
            NumberFormatProvider nfp = getImpl(locale);
            assert nfp != null;
            return nfp.getNumberInstance(locale);
        }

        @Override
        public NumberFormat getPercentInstance(Locale locale) {
            NumberFormatProvider nfp = getImpl(locale);
            assert nfp != null;
            return nfp.getPercentInstance(locale);
        }
    }

    static class CalendarDataProviderDelegate extends CalendarDataProvider
                                       implements Delegate<CalendarDataProvider> {
        private final ConcurrentMap<Locale, CalendarDataProvider> map = new ConcurrentHashMap<>();

        @Override
        public void addImpl(CalendarDataProvider impl) {
            for (Locale l : impl.getAvailableLocales()) {
                map.putIfAbsent(l, impl);
            }
        }

        @Override
        public CalendarDataProvider getImpl(Locale locale) {
            return SPILocaleProviderAdapter.getImpl(map, locale);
        }

        @Override
        public Locale[] getAvailableLocales() {
            return map.keySet().toArray(new Locale[0]);
        }

        @Override
        public boolean isSupportedLocale(Locale locale) {
            return map.containsKey(locale);
        }

        @Override
        public int getFirstDayOfWeek(Locale locale) {
            CalendarDataProvider cdp = getImpl(locale);
            assert cdp != null;
            return cdp.getFirstDayOfWeek(locale);
        }

        @Override
        public int getMinimalDaysInFirstWeek(Locale locale) {
            CalendarDataProvider cdp = getImpl(locale);
            assert cdp != null;
            return cdp.getMinimalDaysInFirstWeek(locale);
        }
    }

    static class CalendarNameProviderDelegate extends CalendarNameProvider
                                       implements Delegate<CalendarNameProvider> {
        private final ConcurrentMap<Locale, CalendarNameProvider> map = new ConcurrentHashMap<>();

        @Override
        public void addImpl(CalendarNameProvider impl) {
            for (Locale l : impl.getAvailableLocales()) {
                map.putIfAbsent(l, impl);
            }
        }

        @Override
        public CalendarNameProvider getImpl(Locale locale) {
            return SPILocaleProviderAdapter.getImpl(map, locale);
        }

        @Override
        public Locale[] getAvailableLocales() {
            return map.keySet().toArray(new Locale[0]);
        }

        @Override
        public boolean isSupportedLocale(Locale locale) {
            return map.containsKey(locale);
        }

        @Override
        public String getDisplayName(String calendarType,
                                              int field, int value,
                                              int style, Locale locale) {
            CalendarNameProvider cdp = getImpl(locale);
            assert cdp != null;
            return cdp.getDisplayName(calendarType, field, value, style, locale);
        }

        @Override
        public Map<String, Integer> getDisplayNames(String calendarType,
                                                             int field, int style,
                                                             Locale locale) {
            CalendarNameProvider cdp = getImpl(locale);
            assert cdp != null;
            return cdp.getDisplayNames(calendarType, field, style, locale);
        }
    }

    static class CurrencyNameProviderDelegate extends CurrencyNameProvider
                                       implements Delegate<CurrencyNameProvider> {
        private final ConcurrentMap<Locale, CurrencyNameProvider> map = new ConcurrentHashMap<>();

        @Override
        public void addImpl(CurrencyNameProvider impl) {
            for (Locale l : impl.getAvailableLocales()) {
                map.putIfAbsent(l, impl);
            }
        }

        @Override
        public CurrencyNameProvider getImpl(Locale locale) {
            return SPILocaleProviderAdapter.getImpl(map, locale);
        }

        @Override
        public Locale[] getAvailableLocales() {
            return map.keySet().toArray(new Locale[0]);
        }

        @Override
        public boolean isSupportedLocale(Locale locale) {
            return map.containsKey(locale);
        }

        @Override
        public String getSymbol(String currencyCode, Locale locale) {
            CurrencyNameProvider cnp = getImpl(locale);
            assert cnp != null;
            return cnp.getSymbol(currencyCode, locale);
        }

        @Override
        public String getDisplayName(String currencyCode, Locale locale) {
            CurrencyNameProvider cnp = getImpl(locale);
            assert cnp != null;
            return cnp.getDisplayName(currencyCode, locale);
        }
    }

    static class LocaleNameProviderDelegate extends LocaleNameProvider
                                     implements Delegate<LocaleNameProvider> {
        private final ConcurrentMap<Locale, LocaleNameProvider> map = new ConcurrentHashMap<>();

        @Override
        public void addImpl(LocaleNameProvider impl) {
            for (Locale l : impl.getAvailableLocales()) {
                map.putIfAbsent(l, impl);
            }
        }

        @Override
        public LocaleNameProvider getImpl(Locale locale) {
            return SPILocaleProviderAdapter.getImpl(map, locale);
        }

        @Override
        public Locale[] getAvailableLocales() {
            return map.keySet().toArray(new Locale[0]);
        }

        @Override
        public boolean isSupportedLocale(Locale locale) {
            return map.containsKey(locale);
        }

        @Override
        public String getDisplayLanguage(String languageCode, Locale locale) {
            LocaleNameProvider lnp = getImpl(locale);
            assert lnp != null;
            return lnp.getDisplayLanguage(languageCode, locale);
        }

        @Override
        public String getDisplayScript(String scriptCode, Locale locale) {
            LocaleNameProvider lnp = getImpl(locale);
            assert lnp != null;
            return lnp.getDisplayScript(scriptCode, locale);
        }

        @Override
        public String getDisplayCountry(String countryCode, Locale locale) {
            LocaleNameProvider lnp = getImpl(locale);
            assert lnp != null;
            return lnp.getDisplayCountry(countryCode, locale);
        }

        @Override
        public String getDisplayVariant(String variant, Locale locale) {
            LocaleNameProvider lnp = getImpl(locale);
            assert lnp != null;
            return lnp.getDisplayVariant(variant, locale);
        }
    }

    static class TimeZoneNameProviderDelegate extends TimeZoneNameProvider
                                     implements Delegate<TimeZoneNameProvider> {
        private final ConcurrentMap<Locale, TimeZoneNameProvider> map = new ConcurrentHashMap<>();

        @Override
        public void addImpl(TimeZoneNameProvider impl) {
            for (Locale l : impl.getAvailableLocales()) {
                map.putIfAbsent(l, impl);
            }
        }

        @Override
        public TimeZoneNameProvider getImpl(Locale locale) {
            return SPILocaleProviderAdapter.getImpl(map, locale);
        }

        @Override
        public Locale[] getAvailableLocales() {
            return map.keySet().toArray(new Locale[0]);
        }

        @Override
        public boolean isSupportedLocale(Locale locale) {
            return map.containsKey(locale);
        }

        @Override
        public String getDisplayName(String ID, boolean daylight, int style, Locale locale) {
            TimeZoneNameProvider tznp = getImpl(locale);
            assert tznp != null;
            return tznp.getDisplayName(ID, daylight, style, locale);
        }

        @Override
        public String getGenericDisplayName(String ID, int style, Locale locale) {
            TimeZoneNameProvider tznp = getImpl(locale);
            assert tznp != null;
            return tznp.getGenericDisplayName(ID, style, locale);
        }
    }
}
