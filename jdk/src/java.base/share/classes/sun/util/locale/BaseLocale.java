/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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

    // This method must be called only when creating the Locale.* constants.
    private BaseLocale(String language, String region) {
        this.language = language;
        this.script = "";
        this.region = region;
        this.variant = "";
    }

    private BaseLocale(String language, String script, String region, String variant) {
        this.language = (language != null) ? LocaleUtils.toLowerString(language).intern() : "";
        this.script = (script != null) ? LocaleUtils.toTitleString(script).intern() : "";
        this.region = (region != null) ? LocaleUtils.toUpperString(region).intern() : "";
        this.variant = (variant != null) ? variant.intern() : "";
    }

    // Called for creating the Locale.* constants. No argument
    // validation is performed.
    public static BaseLocale createInstance(String language, String region) {
        BaseLocale base = new BaseLocale(language, region);
        CACHE.put(new Key(language, region), base);
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

        Key key = new Key(language, script, region, variant);
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
        if (language.length() > 0) {
            sj.add("language=" + language);
        }
        if (script.length() > 0) {
            sj.add("script=" + script);
        }
        if (region.length() > 0) {
            sj.add("region=" + region);
        }
        if (variant.length() > 0) {
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
        private final SoftReference<String> lang;
        private final SoftReference<String> scrt;
        private final SoftReference<String> regn;
        private final SoftReference<String> vart;
        private final boolean normalized;
        private final int hash;

        /**
         * Creates a Key. language and region must be normalized
         * (intern'ed in the proper case).
         */
        private Key(String language, String region) {
            assert language.intern() == language
                   && region.intern() == region;

            lang = new SoftReference<>(language);
            scrt = new SoftReference<>("");
            regn = new SoftReference<>(region);
            vart = new SoftReference<>("");
            this.normalized = true;

            int h = language.hashCode();
            if (region != "") {
                int len = region.length();
                for (int i = 0; i < len; i++) {
                    h = 31 * h + LocaleUtils.toLower(region.charAt(i));
                }
            }
            hash = h;
        }

        public Key(String language, String script, String region, String variant) {
            this(language, script, region, variant, false);
        }

        private Key(String language, String script, String region,
                    String variant, boolean normalized) {
            int h = 0;
            if (language != null) {
                lang = new SoftReference<>(language);
                int len = language.length();
                for (int i = 0; i < len; i++) {
                    h = 31*h + LocaleUtils.toLower(language.charAt(i));
                }
            } else {
                lang = new SoftReference<>("");
            }
            if (script != null) {
                scrt = new SoftReference<>(script);
                int len = script.length();
                for (int i = 0; i < len; i++) {
                    h = 31*h + LocaleUtils.toLower(script.charAt(i));
                }
            } else {
                scrt = new SoftReference<>("");
            }
            if (region != null) {
                regn = new SoftReference<>(region);
                int len = region.length();
                for (int i = 0; i < len; i++) {
                    h = 31*h + LocaleUtils.toLower(region.charAt(i));
                }
            } else {
                regn = new SoftReference<>("");
            }
            if (variant != null) {
                vart = new SoftReference<>(variant);
                int len = variant.length();
                for (int i = 0; i < len; i++) {
                    h = 31*h + variant.charAt(i);
                }
            } else {
                vart = new SoftReference<>("");
            }
            hash = h;
            this.normalized = normalized;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj instanceof Key && this.hash == ((Key)obj).hash) {
                String tl = this.lang.get();
                String ol = ((Key)obj).lang.get();
                if (tl != null && ol != null &&
                    LocaleUtils.caseIgnoreMatch(ol, tl)) {
                    String ts = this.scrt.get();
                    String os = ((Key)obj).scrt.get();
                    if (ts != null && os != null &&
                        LocaleUtils.caseIgnoreMatch(os, ts)) {
                        String tr = this.regn.get();
                        String or = ((Key)obj).regn.get();
                        if (tr != null && or != null &&
                            LocaleUtils.caseIgnoreMatch(or, tr)) {
                            String tv = this.vart.get();
                            String ov = ((Key)obj).vart.get();
                            return (ov != null && ov.equals(tv));
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        public static Key normalize(Key key) {
            if (key.normalized) {
                return key;
            }

            String lang = LocaleUtils.toLowerString(key.lang.get()).intern();
            String scrt = LocaleUtils.toTitleString(key.scrt.get()).intern();
            String regn = LocaleUtils.toUpperString(key.regn.get()).intern();
            String vart = key.vart.get().intern(); // preserve upper/lower cases

            return new Key(lang, scrt, regn, vart, true);
        }
    }

    private static class Cache extends LocaleObjectCache<Key, BaseLocale> {

        public Cache() {
        }

        @Override
        protected Key normalizeKey(Key key) {
            assert key.lang.get() != null &&
                   key.scrt.get() != null &&
                   key.regn.get() != null &&
                   key.vart.get() != null;

            return Key.normalize(key);
        }

        @Override
        protected BaseLocale createObject(Key key) {
            return new BaseLocale(key.lang.get(), key.scrt.get(),
                                  key.regn.get(), key.vart.get());
        }
    }
}
