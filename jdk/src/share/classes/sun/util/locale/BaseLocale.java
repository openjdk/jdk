/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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


public final class BaseLocale {

    public static final String SEP = "_";

    private static final Cache CACHE = new Cache();
    public static final BaseLocale ROOT = BaseLocale.getInstance("", "", "", "");

    private String _language = "";
    private String _script = "";
    private String _region = "";
    private String _variant = "";

    private transient volatile int _hash = 0;

    private BaseLocale(String language, String script, String region, String variant) {
        if (language != null) {
            _language = AsciiUtil.toLowerString(language).intern();
        }
        if (script != null) {
            _script = AsciiUtil.toTitleString(script).intern();
        }
        if (region != null) {
            _region = AsciiUtil.toUpperString(region).intern();
        }
        if (variant != null) {
            _variant = variant.intern();
        }
    }

    public static BaseLocale getInstance(String language, String script, String region, String variant) {
        // JDK uses deprecated ISO639.1 language codes for he, yi and id
        if (AsciiUtil.caseIgnoreMatch(language, "he")) {
            language = "iw";
        } else if (AsciiUtil.caseIgnoreMatch(language, "yi")) {
            language = "ji";
        } else if (AsciiUtil.caseIgnoreMatch(language, "id")) {
            language = "in";
        }

        Key key = new Key(language, script, region, variant);
        BaseLocale baseLocale = CACHE.get(key);
        return baseLocale;
    }

    public String getLanguage() {
        return _language;
    }

    public String getScript() {
        return _script;
    }

    public String getRegion() {
        return _region;
    }

    public String getVariant() {
        return _variant;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BaseLocale)) {
            return false;
        }
        BaseLocale other = (BaseLocale)obj;
        return hashCode() == other.hashCode()
                && _language.equals(other._language)
                && _script.equals(other._script)
                && _region.equals(other._region)
                && _variant.equals(other._variant);
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        if (_language.length() > 0) {
            buf.append("language=");
            buf.append(_language);
        }
        if (_script.length() > 0) {
            if (buf.length() > 0) {
                buf.append(", ");
            }
            buf.append("script=");
            buf.append(_script);
        }
        if (_region.length() > 0) {
            if (buf.length() > 0) {
                buf.append(", ");
            }
            buf.append("region=");
            buf.append(_region);
        }
        if (_variant.length() > 0) {
            if (buf.length() > 0) {
                buf.append(", ");
            }
            buf.append("variant=");
            buf.append(_variant);
        }
        return buf.toString();
    }

    public int hashCode() {
        int h = _hash;
        if (h == 0) {
            // Generating a hash value from language, script, region and variant
            for (int i = 0; i < _language.length(); i++) {
                h = 31*h + _language.charAt(i);
            }
            for (int i = 0; i < _script.length(); i++) {
                h = 31*h + _script.charAt(i);
            }
            for (int i = 0; i < _region.length(); i++) {
                h = 31*h + _region.charAt(i);
            }
            for (int i = 0; i < _variant.length(); i++) {
                h = 31*h + _variant.charAt(i);
            }
            _hash = h;
        }
        return h;
    }

    private static class Key implements Comparable<Key> {
        private String _lang = "";
        private String _scrt = "";
        private String _regn = "";
        private String _vart = "";

        private volatile int _hash; // Default to 0

        public Key(String language, String script, String region, String variant) {
            if (language != null) {
                _lang = language;
            }
            if (script != null) {
                _scrt = script;
            }
            if (region != null) {
                _regn = region;
            }
            if (variant != null) {
                _vart = variant;
            }
        }

        public boolean equals(Object obj) {
            return (this == obj) ||
                    (obj instanceof Key)
                    && AsciiUtil.caseIgnoreMatch(((Key)obj)._lang, this._lang)
                    && AsciiUtil.caseIgnoreMatch(((Key)obj)._scrt, this._scrt)
                    && AsciiUtil.caseIgnoreMatch(((Key)obj)._regn, this._regn)
                    && ((Key)obj)._vart.equals(_vart); // variant is case sensitive in JDK!
        }

        public int compareTo(Key other) {
            int res = AsciiUtil.caseIgnoreCompare(this._lang, other._lang);
            if (res == 0) {
                res = AsciiUtil.caseIgnoreCompare(this._scrt, other._scrt);
                if (res == 0) {
                    res = AsciiUtil.caseIgnoreCompare(this._regn, other._regn);
                    if (res == 0) {
                        res = this._vart.compareTo(other._vart);
                    }
                }
            }
            return res;
        }

        public int hashCode() {
            int h = _hash;
            if (h == 0) {
                // Generating a hash value from language, script, region and variant
                for (int i = 0; i < _lang.length(); i++) {
                    h = 31*h + AsciiUtil.toLower(_lang.charAt(i));
                }
                for (int i = 0; i < _scrt.length(); i++) {
                    h = 31*h + AsciiUtil.toLower(_scrt.charAt(i));
                }
                for (int i = 0; i < _regn.length(); i++) {
                    h = 31*h + AsciiUtil.toLower(_regn.charAt(i));
                }
                for (int i = 0; i < _vart.length(); i++) {
                    h = 31*h + _vart.charAt(i);
                }
                _hash = h;
            }
            return h;
        }

        public static Key normalize(Key key) {
            String lang = AsciiUtil.toLowerString(key._lang).intern();
            String scrt = AsciiUtil.toTitleString(key._scrt).intern();
            String regn = AsciiUtil.toUpperString(key._regn).intern();
            String vart = key._vart.intern(); // preserve upper/lower cases

            return new Key(lang, scrt, regn, vart);
        }
    }

    private static class Cache extends LocaleObjectCache<Key, BaseLocale> {

        public Cache() {
        }

        protected Key normalizeKey(Key key) {
            return Key.normalize(key);
        }

        protected BaseLocale createObject(Key key) {
            return new BaseLocale(key._lang, key._scrt, key._regn, key._vart);
        }

    }
}
