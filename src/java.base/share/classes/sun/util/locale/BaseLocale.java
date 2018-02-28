/*
 * Copyright (c) 2010, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.SoftReference;
import java.util.StringJoiner;

public final class BaseLocale {

    public static final String SEP = "_";

    private static final Cache CACHE = new Cache();

    private final String language;
    private final String script;
    private final String region;
    private final String variant;

    private volatile int hash;

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
    public static BaseLocale createInstance(String language, String region) {
        BaseLocale base = new BaseLocale(language, "", region, "", false);
        CACHE.put(new Key(base), base);
        return base;
    }

    public static BaseLocale getInstance(String language, String script,
                                         String region, String variant) {
        // JDK uses deprecated ISO639.1 language codes for he, yi and id
        if (language != null) {
            if (LocaleUtils.caseIgnoreMatch(language, "he")) {
                language = "iw";
            } else if (LocaleUtils.caseIgnoreMatch(language, "yi")) {
                language = "ji";
            } else if (LocaleUtils.caseIgnoreMatch(language, "id")) {
                language = "in";
            }
        }

        Key key = new Key(language, script, region, variant, false);
        BaseLocale baseLocale = CACHE.get(key);
        return baseLocale;
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
        if (!(obj instanceof BaseLocale)) {
            return false;
        }
        BaseLocale other = (BaseLocale)obj;
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
        /**
         * Keep a SoftReference to the Key data if normalized (actually used
         * as a cache key) and not initialized via the constant creation path.
         *
         * This allows us to avoid creating SoftReferences on lookup Keys
         * (which are short-lived) and for Locales created via
         * Locale#createConstant.
         */
        private final SoftReference<BaseLocale> holderRef;
        private final BaseLocale holder;

        private final boolean normalized;
        private final int hash;

        /**
         * Creates a Key. language and region must be normalized
         * (intern'ed in the proper case).
         */
        private Key(BaseLocale locale) {
            this.holder = locale;
            this.holderRef = null;
            this.normalized = true;
            String language = locale.getLanguage();
            String region = locale.getRegion();
            assert LocaleUtils.toLowerString(language).intern() == language
                    && LocaleUtils.toUpperString(region).intern() == region
                    && locale.getVariant() == ""
                    && locale.getScript() == "";

            int h = language.hashCode();
            if (region != "") {
                int len = region.length();
                for (int i = 0; i < len; i++) {
                    h = 31 * h + LocaleUtils.toLower(region.charAt(i));
                }
            }
            hash = h;
        }

        private Key(String language, String script, String region,
                    String variant, boolean normalize) {
            if (language == null) {
                language = "";
            }
            if (script == null) {
                script = "";
            }
            if (region == null) {
                region = "";
            }
            if (variant == null) {
                variant = "";
            }

            BaseLocale locale = new BaseLocale(language, script, region, variant, normalize);
            this.normalized = normalize;
            if (normalized) {
                this.holderRef = new SoftReference<>(locale);
                this.holder = null;
            } else {
                this.holderRef = null;
                this.holder = locale;
            }
            this.hash = hashCode(locale);
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
            return (holder == null) ? holderRef.get() : holder;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof Key && this.hash == ((Key)obj).hash) {
                BaseLocale other = ((Key) obj).getBaseLocale();
                BaseLocale locale = this.getBaseLocale();
                if (other != null && locale != null
                    && LocaleUtils.caseIgnoreMatch(other.getLanguage(), locale.getLanguage())
                    && LocaleUtils.caseIgnoreMatch(other.getScript(), locale.getScript())
                    && LocaleUtils.caseIgnoreMatch(other.getRegion(), locale.getRegion())
                    // variant is case sensitive in JDK!
                    && other.getVariant().equals(locale.getVariant())) {
                    return true;
                }
            }
            return false;
        }

        public static Key normalize(Key key) {
            if (key.normalized) {
                return key;
            }

            // Only normalized keys may be softly referencing the data holder
            assert (key.holder != null && key.holderRef == null);
            BaseLocale locale = key.holder;
            return new Key(locale.getLanguage(), locale.getScript(),
                    locale.getRegion(), locale.getVariant(), true);
        }
    }

    private static class Cache extends LocaleObjectCache<Key, BaseLocale> {

        public Cache() {
        }

        @Override
        protected Key normalizeKey(Key key) {
            return Key.normalize(key);
        }

        @Override
        protected BaseLocale createObject(Key key) {
            return Key.normalize(key).getBaseLocale();
        }
    }
}
