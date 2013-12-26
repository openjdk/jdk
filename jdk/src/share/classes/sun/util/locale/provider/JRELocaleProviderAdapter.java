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

import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.spi.BreakIteratorProvider;
import java.text.spi.CollatorProvider;
import java.text.spi.DateFormatProvider;
import java.text.spi.DateFormatSymbolsProvider;
import java.text.spi.DecimalFormatSymbolsProvider;
import java.text.spi.NumberFormatProvider;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.spi.CalendarDataProvider;
import java.util.spi.CalendarNameProvider;
import java.util.spi.CurrencyNameProvider;
import java.util.spi.LocaleNameProvider;
import java.util.spi.LocaleServiceProvider;
import java.util.spi.TimeZoneNameProvider;
import sun.util.resources.LocaleData;
import sun.util.spi.CalendarProvider;

/**
 * LocaleProviderAdapter implementation for the legacy JRE locale data.
 *
 * @author Naoto Sato
 * @author Masayoshi Okutsu
 */
public class JRELocaleProviderAdapter extends LocaleProviderAdapter implements ResourceBundleBasedAdapter {

    private static final String LOCALE_DATA_JAR_NAME = "localedata.jar";

    private final ConcurrentMap<String, Set<String>> langtagSets
        = new ConcurrentHashMap<>();

    private final ConcurrentMap<Locale, LocaleResources> localeResourcesMap
        = new ConcurrentHashMap<>();

    // LocaleData specific to this LocaleProviderAdapter.
    private volatile LocaleData localeData;

    /**
     * Returns the type of this LocaleProviderAdapter
     */
    @Override
    public LocaleProviderAdapter.Type getAdapterType() {
        return Type.JRE;
    }

    /**
     * Getter method for Locale Service Providers
     */
    @Override
    @SuppressWarnings("unchecked")
    public <P extends LocaleServiceProvider> P getLocaleServiceProvider(Class<P> c) {
        switch (c.getSimpleName()) {
        case "BreakIteratorProvider":
            return (P) getBreakIteratorProvider();
        case "CollatorProvider":
            return (P) getCollatorProvider();
        case "DateFormatProvider":
            return (P) getDateFormatProvider();
        case "DateFormatSymbolsProvider":
            return (P) getDateFormatSymbolsProvider();
        case "DecimalFormatSymbolsProvider":
            return (P) getDecimalFormatSymbolsProvider();
        case "NumberFormatProvider":
            return (P) getNumberFormatProvider();
        case "CurrencyNameProvider":
            return (P) getCurrencyNameProvider();
        case "LocaleNameProvider":
            return (P) getLocaleNameProvider();
        case "TimeZoneNameProvider":
            return (P) getTimeZoneNameProvider();
        case "CalendarDataProvider":
            return (P) getCalendarDataProvider();
        case "CalendarNameProvider":
            return (P) getCalendarNameProvider();
        case "CalendarProvider":
            return (P) getCalendarProvider();
        default:
            throw new InternalError("should not come down here");
        }
    }

    private volatile BreakIteratorProvider breakIteratorProvider = null;
    private volatile CollatorProvider collatorProvider = null;
    private volatile DateFormatProvider dateFormatProvider = null;
    private volatile DateFormatSymbolsProvider dateFormatSymbolsProvider = null;
    private volatile DecimalFormatSymbolsProvider decimalFormatSymbolsProvider = null;
    private volatile NumberFormatProvider numberFormatProvider = null;

    private volatile CurrencyNameProvider currencyNameProvider = null;
    private volatile LocaleNameProvider localeNameProvider = null;
    private volatile TimeZoneNameProvider timeZoneNameProvider = null;
    private volatile CalendarDataProvider calendarDataProvider = null;
    private volatile CalendarNameProvider calendarNameProvider = null;

    private volatile CalendarProvider calendarProvider = null;

    /*
     * Getter methods for java.text.spi.* providers
     */
    @Override
    public BreakIteratorProvider getBreakIteratorProvider() {
        if (breakIteratorProvider == null) {
            BreakIteratorProvider provider = new BreakIteratorProviderImpl(getAdapterType(),
                                                            getLanguageTagSet("FormatData"));
            synchronized (this) {
                if (breakIteratorProvider == null) {
                    breakIteratorProvider = provider;
                }
            }
        }
        return breakIteratorProvider;
    }

    @Override
    public CollatorProvider getCollatorProvider() {
        if (collatorProvider == null) {
            CollatorProvider provider = new CollatorProviderImpl(getAdapterType(),
                                                getLanguageTagSet("CollationData"));
            synchronized (this) {
                if (collatorProvider == null) {
                    collatorProvider = provider;
                }
            }
        }
        return collatorProvider;
    }

    @Override
    public DateFormatProvider getDateFormatProvider() {
        if (dateFormatProvider == null) {
            DateFormatProvider provider = new DateFormatProviderImpl(getAdapterType(),
                                                    getLanguageTagSet("FormatData"));
            synchronized (this) {
                if (dateFormatProvider == null) {
                    dateFormatProvider = provider;
                }
            }
        }
        return dateFormatProvider;
    }

    @Override
    public DateFormatSymbolsProvider getDateFormatSymbolsProvider() {
        if (dateFormatSymbolsProvider == null) {
            DateFormatSymbolsProvider provider = new DateFormatSymbolsProviderImpl(getAdapterType(),
                                                                getLanguageTagSet("FormatData"));
            synchronized (this) {
                if (dateFormatSymbolsProvider == null) {
                    dateFormatSymbolsProvider = provider;
                }
            }
        }
        return dateFormatSymbolsProvider;
    }

    @Override
    public DecimalFormatSymbolsProvider getDecimalFormatSymbolsProvider() {
        if (decimalFormatSymbolsProvider == null) {
            DecimalFormatSymbolsProvider provider = new DecimalFormatSymbolsProviderImpl(getAdapterType(), getLanguageTagSet("FormatData"));
            synchronized (this) {
                if (decimalFormatSymbolsProvider == null) {
                    decimalFormatSymbolsProvider = provider;
                }
            }
        }
        return decimalFormatSymbolsProvider;
    }

    @Override
    public NumberFormatProvider getNumberFormatProvider() {
        if (numberFormatProvider == null) {
            NumberFormatProvider provider = new NumberFormatProviderImpl(getAdapterType(),
                                                        getLanguageTagSet("FormatData"));
            synchronized (this) {
                if (numberFormatProvider == null) {
                    numberFormatProvider = provider;
                }
            }
        }
        return numberFormatProvider;
    }

    /**
     * Getter methods for java.util.spi.* providers
     */
    @Override
    public CurrencyNameProvider getCurrencyNameProvider() {
        if (currencyNameProvider == null) {
            CurrencyNameProvider provider = new CurrencyNameProviderImpl(getAdapterType(),
                                            getLanguageTagSet("CurrencyNames"));
            synchronized (this) {
                if (currencyNameProvider == null) {
                    currencyNameProvider = provider;
                }
            }
        }
        return currencyNameProvider;
    }

    @Override
    public LocaleNameProvider getLocaleNameProvider() {
        if (localeNameProvider == null) {
            LocaleNameProvider provider = new LocaleNameProviderImpl(getAdapterType(),
                                                    getLanguageTagSet("LocaleNames"));
            synchronized (this) {
                if (localeNameProvider == null) {
                    localeNameProvider = provider;
                }
            }
        }
        return localeNameProvider;
    }

    @Override
    public TimeZoneNameProvider getTimeZoneNameProvider() {
        if (timeZoneNameProvider == null) {
            TimeZoneNameProvider provider = new TimeZoneNameProviderImpl(getAdapterType(),
                                                    getLanguageTagSet("TimeZoneNames"));
            synchronized (this) {
                if (timeZoneNameProvider == null) {
                    timeZoneNameProvider = provider;
                }
            }
        }
        return timeZoneNameProvider;
    }

    @Override
    public CalendarDataProvider getCalendarDataProvider() {
        if (calendarDataProvider == null) {
            CalendarDataProvider provider;
            provider = new CalendarDataProviderImpl(getAdapterType(),
                                                    getLanguageTagSet("CalendarData"));
            synchronized (this) {
                if (calendarDataProvider == null) {
                    calendarDataProvider = provider;
                }
            }
        }
        return calendarDataProvider;
    }

    @Override
    public CalendarNameProvider getCalendarNameProvider() {
        if (calendarNameProvider == null) {
            CalendarNameProvider provider;
            provider = new CalendarNameProviderImpl(getAdapterType(),
                                                    getLanguageTagSet("FormatData"));
            synchronized (this) {
                if (calendarNameProvider == null) {
                    calendarNameProvider = provider;
                }
            }
        }
        return calendarNameProvider;
    }

    /**
     * Getter methods for sun.util.spi.* providers
     */
    @Override
    public CalendarProvider getCalendarProvider() {
        if (calendarProvider == null) {
            CalendarProvider provider = new CalendarProviderImpl(getAdapterType(),
                                                    getLanguageTagSet("CalendarData"));
            synchronized (this) {
                if (calendarProvider == null) {
                    calendarProvider = provider;
                }
            }
        }
        return calendarProvider;
    }

    @Override
    public LocaleResources getLocaleResources(Locale locale) {
        LocaleResources lr = localeResourcesMap.get(locale);
        if (lr == null) {
            lr = new LocaleResources(this, locale);
            LocaleResources lrc = localeResourcesMap.putIfAbsent(locale, lr);
            if (lrc != null) {
                lr = lrc;
            }
        }
        return lr;
    }

    // ResourceBundleBasedAdapter method implementation
    @Override
    public LocaleData getLocaleData() {
        if (localeData == null) {
            synchronized (this) {
                if (localeData == null) {
                    localeData = new LocaleData(getAdapterType());
                }
            }
        }
        return localeData;
    }

    /**
     * Returns a list of the installed locales. Currently, this simply returns
     * the list of locales for which a sun.text.resources.FormatData bundle
     * exists. This bundle family happens to be the one with the broadest
     * locale coverage in the JRE.
     */
    @Override
    public Locale[] getAvailableLocales() {
        return AvailableJRELocales.localeList.clone();
    }

    public Set<String> getLanguageTagSet(String category) {
        Set<String> tagset = langtagSets.get(category);
        if (tagset == null) {
            tagset = createLanguageTagSet(category);
            Set<String> ts = langtagSets.putIfAbsent(category, tagset);
            if (ts != null) {
                tagset = ts;
            }
        }
        return tagset;
    }

    protected Set<String> createLanguageTagSet(String category) {
        String supportedLocaleString = LocaleDataMetaInfo.getSupportedLocaleString(category);
        Set<String> tagset = new HashSet<>();
        StringTokenizer tokens = new StringTokenizer(supportedLocaleString);
        while (tokens.hasMoreTokens()) {
            String token = tokens.nextToken();
            if (token.equals("|")) {
                if (isNonENLangSupported()) {
                    continue;
                }
                break;
            }
            tagset.add(token);
        }

        return tagset;
    }

    /**
     * Lazy load available locales.
     */
    private static class AvailableJRELocales {
        private static final Locale[] localeList = createAvailableLocales();
        private AvailableJRELocales() {
        }
    }

    private static Locale[] createAvailableLocales() {
        /*
         * Gets the locale string list from LocaleDataMetaInfo class and then
         * contructs the Locale array and a set of language tags based on the
         * locale string returned above.
         */
        String supportedLocaleString = LocaleDataMetaInfo.getSupportedLocaleString("AvailableLocales");

        if (supportedLocaleString.length() == 0) {
            throw new InternalError("No available locales for JRE");
        }

        /*
         * Look for "|" and construct a new locale string list.
         */
        int barIndex = supportedLocaleString.indexOf('|');
        StringTokenizer localeStringTokenizer;
        if (isNonENLangSupported()) {
            localeStringTokenizer = new StringTokenizer(supportedLocaleString.substring(0, barIndex)
                    + supportedLocaleString.substring(barIndex + 1));
        } else {
            localeStringTokenizer = new StringTokenizer(supportedLocaleString.substring(0, barIndex));
        }

        int length = localeStringTokenizer.countTokens();
        Locale[] locales = new Locale[length + 1];
        locales[0] = Locale.ROOT;
        for (int i = 1; i <= length; i++) {
            String currentToken = localeStringTokenizer.nextToken();
            switch (currentToken) {
                case "ja-JP-JP":
                    locales[i] = JRELocaleConstants.JA_JP_JP;
                    break;
                case "no-NO-NY":
                    locales[i] = JRELocaleConstants.NO_NO_NY;
                    break;
                case "th-TH-TH":
                    locales[i] = JRELocaleConstants.TH_TH_TH;
                    break;
                default:
                    locales[i] = Locale.forLanguageTag(currentToken);
            }
        }
        return locales;
    }

    private static volatile Boolean isNonENSupported = null;

    /*
     * Returns true if the non EN resources jar file exists in jre
     * extension directory. @returns true if the jar file is there. Otherwise,
     * returns false.
     */
    private static boolean isNonENLangSupported() {
        if (isNonENSupported == null) {
            synchronized (JRELocaleProviderAdapter.class) {
                if (isNonENSupported == null) {
                    final String sep = File.separator;
                    String localeDataJar =
                            java.security.AccessController.doPrivileged(
                            new sun.security.action.GetPropertyAction("java.home"))
                            + sep + "lib" + sep + "ext" + sep + LOCALE_DATA_JAR_NAME;

                    /*
                     * Peek at the installed extension directory to see if
                     * localedata.jar is installed or not.
                     */
                    final File f = new File(localeDataJar);
                    isNonENSupported =
                        AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                            @Override
                            public Boolean run() {
                                return f.exists();
                            }
                        });
               }
            }
        }
        return isNonENSupported;
    }
}
