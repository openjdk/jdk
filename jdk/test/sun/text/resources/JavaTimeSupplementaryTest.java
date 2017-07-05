/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * @test
 * @bug 8159943
 * @modules java.base/sun.util.locale.provider
 *          java.base/sun.util.resources
 *          jdk.localedata
 * @summary Test for checking consistency between CLDR and COMPAT locale data
 *          for java.time.
 */

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import sun.util.locale.provider.LocaleProviderAdapter.Type;
import sun.util.locale.provider.LocaleProviderAdapter;

public class JavaTimeSupplementaryTest {
    // COMPAT-to-CLDR locale mapping
    private static final Map<Locale, Locale> LOCALEMAP;
    static {
        LOCALEMAP = new HashMap<>();
        LOCALEMAP.put(new Locale("hi", "IN"), new Locale("hi"));
        LOCALEMAP.put(new Locale("no", "NO", "NY"), new Locale("nn", "NO"));
        LOCALEMAP.put(new Locale("no"), new Locale("nb"));
        LOCALEMAP.put(Locale.TAIWAN, Locale.forLanguageTag("zh-Hant"));
        LOCALEMAP.put(new Locale("zh", "HK"), Locale.forLanguageTag("zh-Hant-HK"));
        LOCALEMAP.put(new Locale("zh", "SG"), Locale.forLanguageTag("zh-Hans-SG"));
        LOCALEMAP.put(new Locale("sr", "BA"), Locale.forLanguageTag("sr-Cyrl-BA"));
    }

    private static final String[] PREFIXES = {
        "Quarter",
        "java.time.",
        "calendarname.",
        "field.",
        "islamic.",
        "roc.",
    };

    // All available locales for the COMPAT FormatData resource bundles
    static final List<Locale> COMPAT_LOCALES
        = Arrays.asList(LocaleProviderAdapter.forJRE()
                        .getDateFormatSymbolsProvider().getAvailableLocales());

    static int errors;

    public static void main(String... args) {
        for (Locale locale : COMPAT_LOCALES) {
            ResourceBundle compat
                = LocaleProviderAdapter.forJRE()
                      .getLocaleResources(locale).getJavaTimeFormatData();
            if (!compat.getLocale().equals(locale)) {
                continue;
            }
            Locale cldrLocale = toCldrLocale(locale);
            ResourceBundle cldr
                = LocaleProviderAdapter.forType(Type.CLDR)
                      .getLocaleResources(locale).getJavaTimeFormatData();
            if (!cldr.getLocale().equals(cldrLocale)) {
                continue;
            }
            compareResources(compat, cldr);
        }
        if (errors > 0) {
            throw new RuntimeException(errors + " failure(s)");
        }
    }

    private static Locale toCldrLocale(Locale compatLocale) {
        Locale loc = LOCALEMAP.get(compatLocale);
        return loc != null ? loc: compatLocale;
    }

    private static void compareResources(ResourceBundle compat, ResourceBundle cldr) {
        Set<String> supplementalKeys = getSupplementalKeys(compat);
        for (String key : supplementalKeys) {
            Object compatData = compat.getObject(key);
            String cldrKey = toCldrKey(key);
            Object cldrData = cldr.containsKey(cldrKey) ? cldr.getObject(cldrKey) : null;
            if (!Objects.deepEquals(compatData, cldrData)) {
                // OK if key is for the Buddhist or Japanese calendars which had been
                // supported before java.time, or if key is "java.time.short.Eras" due
                // to legacy era names.
                if (!(key.contains("buddhist") || key.contains("japanese")
                      || key.equals("java.time.short.Eras"))) {
                    errors++;
                    System.out.print("Failure: ");
                }
                System.out.println("diff: " + compat.getLocale().toLanguageTag() + "\n"
                                   + "  COMPAT: " + key + " -> " + toString(compatData) + "\n"
                                   + "    CLDR: " + cldrKey + " -> " + toString(cldrData));
            }
        }
    }

    private static Set<String> getSupplementalKeys(ResourceBundle rb) {
        // Collect keys starting with any of PREFIXES
        Set<String> keys = rb.keySet().stream()
            .filter(k -> Arrays.stream(PREFIXES).anyMatch(p -> k.startsWith(p)))
            .collect(Collectors.toCollection(TreeSet::new));
        return keys;
    }

    /**
     * Removes "java.time." prefix where it's unused in CLDR.
     */
    private static String toCldrKey(String key) {
        if (key.contains("short.Eras")) {
            key = key.replace("short.", "");
        }
        if (key.startsWith("java.time.") && key.endsWith(".Eras")) {
            return key.substring("java.time.".length());
        }
        return key;
    }

    private static String toString(Object data) {
        if (data instanceof String[]) {
            return Arrays.toString((String[]) data);
        }
        return data.toString();
    }
}
