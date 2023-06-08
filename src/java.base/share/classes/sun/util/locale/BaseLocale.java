/*
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *******************************************************************************
 * Copyright (C) 2009-2010, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package sun.util.locale;

import jdk.internal.misc.CDS;
import jdk.internal.util.StaticProperty;
import jdk.internal.vm.annotation.Stable;

import java.util.Map;
import java.util.StringJoiner;
import java.util.WeakHashMap;

public final class BaseLocale {

    public static @Stable BaseLocale[] constantBaseLocales;
    public static final byte ENGLISH = 0,
            FRENCH = 1,
            GERMAN = 2,
            ITALIAN = 3,
            JAPANESE = 4,
            KOREAN = 5,
            CHINESE = 6,
            SIMPLIFIED_CHINESE = 7,
            TRADITIONAL_CHINESE = 8,
            FRANCE = 9,
            GERMANY = 10,
            ITALY = 11,
            JAPAN = 12,
            KOREA = 13,
            UK = 14,
            US = 15,
            CANADA = 16,
            CANADA_FRENCH = 17,
            ROOT = 18,
            NUM_CONSTANTS = 19;
    static {
        CDS.initializeFromArchive(BaseLocale.class);
        BaseLocale[] baseLocales = constantBaseLocales;
        if (baseLocales == null) {
            baseLocales = new BaseLocale[NUM_CONSTANTS];
            baseLocales[ENGLISH] = createInstance("en", "");
            baseLocales[FRENCH] = createInstance("fr", "");
            baseLocales[GERMAN] = createInstance("de", "");
            baseLocales[ITALIAN] = createInstance("it", "");
            baseLocales[JAPANESE] = createInstance("ja", "");
            baseLocales[KOREAN] = createInstance("ko", "");
            baseLocales[CHINESE] = createInstance("zh", "");
            baseLocales[SIMPLIFIED_CHINESE] = createInstance("zh", "CN");
            baseLocales[TRADITIONAL_CHINESE] = createInstance("zh", "TW");
            baseLocales[FRANCE] = createInstance("fr", "FR");
            baseLocales[GERMANY] = createInstance("de", "DE");
            baseLocales[ITALY] = createInstance("it", "IT");
            baseLocales[JAPAN] = createInstance("ja", "JP");
            baseLocales[KOREA] = createInstance("ko", "KR");
            baseLocales[UK] = createInstance("en", "GB");
            baseLocales[US] = createInstance("en", "US");
            baseLocales[CANADA] = createInstance("en", "CA");
            baseLocales[CANADA_FRENCH] = createInstance("fr", "CA");
            baseLocales[ROOT] = createInstance("", "");
            constantBaseLocales = baseLocales;
        }
    }

    public static final String SEP = "_";

    private final String language;
    private final String script;
    private final String region;
    private final String variant;

    private @Stable int hash;

    /**
     * Boolean for the old ISO language code compatibility.
     * The system property "java.locale.useOldISOCodes" is not security sensitive,
     * so no need to ensure privileged access here.
     */
    private static final boolean OLD_ISO_CODES = StaticProperty.javaLocaleUseOldISOCodes()
            .equalsIgnoreCase("true");

    // This method must be called with normalize = false only when creating the
    // Locale.* constants and non-normalized BaseLocale$Keys used for lookup.
    private BaseLocale(String language, String script, String region, String variant,
                       boolean normalize) {
        if (normalize) {
            this.language = LocaleUtils.toLowerString(language).intern();
            this.script = LocaleUtils.toTitleString(script).intern();
            this.region = LocaleUtils.toUpperString(region).intern();
            this.variant = variant.intern();
        } else {
            this.language = language;
            this.script = script;
            this.region = region;
            this.variant = variant;
        }
    }

    // Called for creating the Locale.* constants. No argument
    // validation is performed.
    private static BaseLocale createInstance(String language, String region) {
        return new BaseLocale(language, "", region, "", false);
    }

    public static BaseLocale getInstance(String language, String script,
                                         String region, String variant) {

        if (script == null) {
            script = "";
        }
        if (region == null) {
            region = "";
        }
        if (language == null) {
            language = "";
        }
        if (variant == null) {
            variant = "";
        }

        // Non-allocating for most uses
        language = LocaleUtils.toLowerString(language);
        region = LocaleUtils.toUpperString(region);

        // Check for constant base locales first
        if (script.isEmpty() && variant.isEmpty()) {
            for (BaseLocale baseLocale : constantBaseLocales) {
                if (baseLocale.getLanguage().equals(language)
                        && baseLocale.getRegion().equals(region)) {
                    return baseLocale;
                }
            }
        }

        // JDK uses deprecated ISO639.1 language codes for he, yi and id
        if (!language.isEmpty()) {
            language = convertOldISOCodes(language);
        }

        Key key = new Key(language, script, region, variant);
        return CACHE.computeIfAbsent(key, (k) -> {
            var base = k.getBaseLocale();
            return new BaseLocale(base.getLanguage(), base.getScript(), base.getRegion(), base.getVariant(), true);
        });
    }

    public static String convertOldISOCodes(String language) {
        return switch (language) {
            case "he", "iw" -> OLD_ISO_CODES ? "iw" : "he";
            case "id", "in" -> OLD_ISO_CODES ? "in" : "id";
            case "yi", "ji" -> OLD_ISO_CODES ? "ji" : "yi";
            default -> language;
        };
    }

    public String getLanguage() {
        return language;
    }

    public String getScript() {
        return script;
    }

    public String getRegion() {
        return region;
    }

    public String getVariant() {
        return variant;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BaseLocale other)) {
            return false;
        }
        return language == other.language
               && script == other.script
               && region == other.region
               && variant == other.variant;
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner(", ");
        if (!language.isEmpty()) {
            sj.add("language=" + language);
        }
        if (!script.isEmpty()) {
            sj.add("script=" + script);
        }
        if (!region.isEmpty()) {
            sj.add("region=" + region);
        }
        if (!variant.isEmpty()) {
            sj.add("variant=" + variant);
        }
        return sj.toString();
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0) {
            // Generating a hash value from language, script, region and variant
            h = language.hashCode();
            h = 31 * h + script.hashCode();
            h = 31 * h + region.hashCode();
            h = 31 * h + variant.hashCode();
            if (h != 0) {
                hash = h;
            }
        }
        return h;
    }

    private static final class Key {
        private final BaseLocale base;
        private final int hash;

        private Key(String language, String script, String region,
                    String variant) {
            base = new BaseLocale(language, script, region, variant, false);
            hash = hashCode(base);
        }

        public int hashCode() {
            return hash;
        }

        private int hashCode(BaseLocale locale) {
            int h = 0;
            String lang = locale.getLanguage();
            int len = lang.length();
            for (int i = 0; i < len; i++) {
                h = 31*h + LocaleUtils.toLower(lang.charAt(i));
            }
            String scrt = locale.getScript();
            len = scrt.length();
            for (int i = 0; i < len; i++) {
                h = 31*h + LocaleUtils.toLower(scrt.charAt(i));
            }
            String regn = locale.getRegion();
            len = regn.length();
            for (int i = 0; i < len; i++) {
                h = 31*h + LocaleUtils.toLower(regn.charAt(i));
            }
            String vart = locale.getVariant();
            len = vart.length();
            for (int i = 0; i < len; i++) {
                h = 31*h + vart.charAt(i);
            }
            return h;
        }

        private BaseLocale getBaseLocale() {
            return base;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof Key o && this.hash == o.hash) {
                BaseLocale other = o.getBaseLocale();
                BaseLocale locale = this.getBaseLocale();
                return other != null && locale != null
                    && LocaleUtils.caseIgnoreMatch(other.getLanguage(), locale.getLanguage())
                    && LocaleUtils.caseIgnoreMatch(other.getScript(), locale.getScript())
                    && LocaleUtils.caseIgnoreMatch(other.getRegion(), locale.getRegion())
                    // variant is case sensitive in JDK!
                    && other.getVariant().equals(locale.getVariant());
            }
            return false;
        }
    }

    private static final Map<Key, BaseLocale> CACHE = new WeakHashMap<>();
}
