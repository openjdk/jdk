/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.util.cldr;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.spi.BreakIteratorProvider;
import java.text.spi.CollatorProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.spi.CalendarDataProvider;
import java.util.spi.CalendarNameProvider;
import java.util.spi.TimeZoneNameProvider;
import sun.util.locale.provider.JRELocaleProviderAdapter;
import sun.util.locale.provider.LocaleDataMetaInfo;
import sun.util.locale.provider.LocaleProviderAdapter;

/**
 * LocaleProviderAdapter implementation for the CLDR locale data.
 *
 * @author Masayoshi Okutsu
 * @author Naoto Sato
 */
public class CLDRLocaleProviderAdapter extends JRELocaleProviderAdapter {

    private static final CLDRBaseLocaleDataMetaInfo baseMetaInfo = new CLDRBaseLocaleDataMetaInfo();
    // Assumption: CLDR has only one non-Base module.
    private final LocaleDataMetaInfo nonBaseMetaInfo;

    // parent locales map
    private static final Map<Locale, Locale> parentLocalesMap;
    // cache to hold  locale to locale mapping for language aliases.
    private static final Map<Locale, Locale> langAliasesCache;
    // cache the available locales
    private static volatile Locale[] AVAILABLE_LOCALES;

    static {
        parentLocalesMap = new ConcurrentHashMap<>();
        langAliasesCache = new ConcurrentHashMap<>();
        // Assuming these locales do NOT have irregular parent locales.
        parentLocalesMap.put(Locale.ROOT, Locale.ROOT);
        parentLocalesMap.put(Locale.ENGLISH, Locale.ENGLISH);
        parentLocalesMap.put(Locale.US, Locale.US);
    }

    @SuppressWarnings("removal")
    public CLDRLocaleProviderAdapter() {
        LocaleDataMetaInfo nbmi;

        try {
            nbmi = AccessController.doPrivileged((PrivilegedExceptionAction<LocaleDataMetaInfo>) () -> {
                for (LocaleDataMetaInfo ldmi : ServiceLoader.loadInstalled(LocaleDataMetaInfo.class)) {
                    if (ldmi.getType() == Type.CLDR) {
                        return ldmi;
                    }
                }
                return null;
            });
        } catch (PrivilegedActionException pae) {
            throw new InternalError(pae.getCause());
        }

        nonBaseMetaInfo = nbmi;
    }

    /**
     * Returns the type of this LocaleProviderAdapter
     * @return the type of this
     */
    @Override
    public LocaleProviderAdapter.Type getAdapterType() {
        return LocaleProviderAdapter.Type.CLDR;
    }

    @Override
    public BreakIteratorProvider getBreakIteratorProvider() {
        return null;
    }

    @Override
    public CalendarDataProvider getCalendarDataProvider() {
        if (calendarDataProvider == null) {
            @SuppressWarnings("removal")
            CalendarDataProvider provider = AccessController.doPrivileged(
                (PrivilegedAction<CalendarDataProvider>) () ->
                    new CLDRCalendarDataProviderImpl(
                        getAdapterType(),
                        getLanguageTagSet("CalendarData")));

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
            @SuppressWarnings("removal")
            CalendarNameProvider provider = AccessController.doPrivileged(
                    (PrivilegedAction<CalendarNameProvider>) ()
                    -> new CLDRCalendarNameProviderImpl(
                            getAdapterType(),
                            getLanguageTagSet("FormatData")));

            synchronized (this) {
                if (calendarNameProvider == null) {
                    calendarNameProvider = provider;
                }
            }
        }
        return calendarNameProvider;
    }

    @Override
    public CollatorProvider getCollatorProvider() {
        return null;
    }

    @Override
    public TimeZoneNameProvider getTimeZoneNameProvider() {
        if (timeZoneNameProvider == null) {
            @SuppressWarnings("removal")
            TimeZoneNameProvider provider = AccessController.doPrivileged(
                (PrivilegedAction<TimeZoneNameProvider>) () ->
                    new CLDRTimeZoneNameProviderImpl(
                        getAdapterType(),
                        getLanguageTagSet("TimeZoneNames")));

            synchronized (this) {
                if (timeZoneNameProvider == null) {
                    timeZoneNameProvider = provider;
                }
            }
        }
        return timeZoneNameProvider;
    }

    @Override
    public Locale[] getAvailableLocales() {
        if (AVAILABLE_LOCALES == null) {
            AVAILABLE_LOCALES = createLanguageTagSet("AvailableLocales").stream()
                .map(Locale::forLanguageTag)
                .toArray(Locale[]::new);
        }
        return AVAILABLE_LOCALES;
    }

    private static Locale applyAliases(Locale loc) {
        return langAliasesCache.computeIfAbsent(loc, l -> {
            var alias = baseMetaInfo.getLanguageAliasMap().get(l.toLanguageTag());
            return alias != null ? Locale.forLanguageTag(alias) : l;
        });
    }

    @Override
    protected Set<String> createLanguageTagSet(String category) {
        // Assume all categories support the same set as AvailableLocales
        // in CLDR adapter.
        category = "AvailableLocales";

        // Directly call Base tags, as we know it's in the base module.
        String supportedLocaleString = baseMetaInfo.availableLanguageTags(category);
        String nonBaseTags = null;

        if (nonBaseMetaInfo != null) {
            nonBaseTags = nonBaseMetaInfo.availableLanguageTags(category);
        }
        if (nonBaseTags != null) {
            if (supportedLocaleString != null) {
                supportedLocaleString += " " + nonBaseTags;
            } else {
                supportedLocaleString = nonBaseTags;
            }
        }
        return supportedLocaleString != null ?
                Set.of(supportedLocaleString.split("\s+")) :
                Collections.emptySet();
    }

    // Implementation of ResourceBundleBasedAdapter
    @Override
    public List<Locale> getCandidateLocales(String baseName, Locale locale) {
        List<Locale> candidates = super.getCandidateLocales(baseName, applyAliases(locale));
        return applyParentLocales(baseName, candidates);
    }

    private static final Locale NB = Locale.forLanguageTag("nb");
    private static final Locale NO = Locale.forLanguageTag("no");
    private List<Locale> applyParentLocales(String baseName, List<Locale> candidates) {
        // check irregular parents
        for (int i = 0; i < candidates.size(); i++) {
            Locale l = candidates.get(i);
            if (!l.equals(Locale.ROOT)) {
                Locale p = getParentLocale(l);
                if (p != null &&
                    !candidates.get(i+1).equals(p)) {
                    List<Locale> applied = candidates.subList(0, i+1);
                    // Tweak for Norwegian locales, CLDR switched the canonical form of
                    // Norwegian Bokmal language code from "nb" to "no" in CLDR 39
                    // (https://unicode-org.atlassian.net/browse/CLDR-2698)
                    if (p.equals(NB) || p.equals(NO)) {
                        applied.add(NO);
                        applied.add(Locale.ROOT);
                    } else {
                        applied.addAll(applyParentLocales(baseName, super.getCandidateLocales(baseName, p)));
                    }
                    return applied;
                }
            }
        }

        return candidates;
    }

    private static Locale getParentLocale(Locale locale) {
        Locale parent = parentLocalesMap.get(locale);

        if (parent == null) {
            String tag = locale.toLanguageTag();
            for (Map.Entry<Locale, String[]> entry : baseMetaInfo.parentLocales().entrySet()) {
                if (Arrays.binarySearch(entry.getValue(), tag) >= 0) {
                    parent = entry.getKey();
                    break;
                }
            }

            if (parent == null) {
                // check nonlikelyScript locales
                if (CLDRBaseLocaleDataMetaInfo.nonlikelyScript && locale.getCountry().isEmpty()) {
                    var lang = " " + locale.getLanguage() + " ";
                    var script= locale.getScript();
                    if (!script.isEmpty()) {
                        parent = baseMetaInfo.likelyScriptMap().entrySet().stream()
                            .filter(e -> e.getValue().contains(lang))
                            .findAny()
                            .map(Map.Entry::getKey)
                            .map(likely -> likely.equals(script) ? null : Locale.ROOT)
                            .orElse(null);
                    }
                }
            }

            // no parent found
            if (parent == null) {
                parent = locale; // non existent marker
            }
            parentLocalesMap.putIfAbsent(locale, parent);
        }

        if (locale.equals(parent)) {
            // means no irregular parent.
            parent = null;
        }

        return parent;
    }

    /**
     * This method returns equivalent CLDR supported locale
     * for no, no-NO locales so that COMPAT locales do not precede
     * those locales during ResourceBundle search path, also if an alias exists for a locale,
     * it returns equivalent locale, e.g for zh_HK it returns zh_Hant-HK.
     */
    private static Locale getEquivalentLoc(Locale locale) {
        return switch (locale.toString()) {
            case "no", "no_NO" -> Locale.forLanguageTag("nb");
            default -> applyAliases(locale);
        };
    }

    @Override
    public boolean isSupportedProviderLocale(Locale locale, Set<String> langtags) {
        return Locale.ROOT.equals(locale)
                || langtags.contains(locale.stripExtensions().toLanguageTag())
                || langtags.contains(getEquivalentLoc(locale).toLanguageTag());
    }

    /**
     * Returns the canonical ID for the given ID
     */
    public Optional<String> canonicalTZID(String id) {
        return Optional.ofNullable(baseMetaInfo.tzCanonicalIDs().get(id));
    }
}
