/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.util.ReferencedKeySet;
import jdk.internal.util.StaticProperty;
import jdk.internal.vm.annotation.Stable;

import java.util.StringJoiner;
import java.util.function.UnaryOperator;

public final class BaseLocale {

    public static @Stable BaseLocale[] constantBaseLocales;
    public static final byte ROOT = 0,
            ENGLISH = 1,
            US = 2,
            FRENCH = 3,
            GERMAN = 4,
            ITALIAN = 5,
            JAPANESE = 6,
            KOREAN = 7,
            CHINESE = 8,
            SIMPLIFIED_CHINESE = 9,
            TRADITIONAL_CHINESE = 10,
            FRANCE = 11,
            GERMANY = 12,
            ITALY = 13,
            JAPAN = 14,
            KOREA = 15,
            UK = 16,
            CANADA = 17,
            CANADA_FRENCH = 18,
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

    // Interned BaseLocale cache
    private static final ReferencedKeySet<BaseLocale> CACHE =
            ReferencedKeySet.create(true, ReferencedKeySet.concurrentHashMapSupplier());

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

    private BaseLocale(String language, String script, String region, String variant) {
        this.language = language;
        this.script = script;
        this.region = region;
        this.variant = variant;
    }

    // Called for creating the Locale.* constants. No argument
    // validation is performed.
    private static BaseLocale createInstance(String language, String region) {
        return new BaseLocale(language, "", region, "");
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
                if (baseLocale.language.equals(language)
                        && baseLocale.region.equals(region)) {
                    return baseLocale;
                }
            }
        }

        // JDK uses deprecated ISO639.1 language codes for he, yi and id
        if (!language.isEmpty()) {
            language = convertOldISOCodes(language);
        }

        // Obtain the "interned" BaseLocale from the cache. The returned
        // "interned" instance can subsequently be used by the Locale
        // instance which guarantees the locale components are properly cased/interned.
        return CACHE.intern(new BaseLocale(language, script, region, variant),
                // Avoid lambdas since this may be on the bootstrap path in many locales
                INTERNER);
    }

    public static final UnaryOperator<BaseLocale> INTERNER = new UnaryOperator<>() {
        @Override
        public BaseLocale apply(BaseLocale b) {
            return new BaseLocale(
                    LocaleUtils.toLowerString(b.language).intern(),
                    LocaleUtils.toTitleString(b.script).intern(),
                    LocaleUtils.toUpperString(b.region).intern(),
                    b.variant.intern());
        }
    };

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
        if (obj instanceof BaseLocale other) {
            return LocaleUtils.caseIgnoreMatch(other.language, language)
                && LocaleUtils.caseIgnoreMatch(other.region, region)
                && LocaleUtils.caseIgnoreMatch(other.script, script)
                // variant is case sensitive in JDK!
                && other.variant.equals(variant);
        }
        return false;
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
            int len = language.length();
            for (int i = 0; i < len; i++) {
                h = 31*h + LocaleUtils.toLower(language.charAt(i));
            }
            len = script.length();
            for (int i = 0; i < len; i++) {
                h = 31*h + LocaleUtils.toLower(script.charAt(i));
            }
            len = region.length();
            for (int i = 0; i < len; i++) {
                h = 31*h + LocaleUtils.toLower(region.charAt(i));
            }
            len = variant.length();
            for (int i = 0; i < len; i++) {
                h = 31*h + variant.charAt(i);
            }
            if (h != 0) {
                hash = h;
            }
        }
        return h;
    }
}
