// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009-2010, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */

package jdk.internal.icu.impl.locale;


public final class BaseLocale {

    private static final boolean JDKIMPL = false;

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
            if (JDKIMPL) {
                // preserve upper/lower cases
                _variant = variant.intern();
            } else {
                _variant = AsciiUtil.toUpperString(variant).intern();
            }
        }
    }

    public static BaseLocale getInstance(String language, String script, String region, String variant) {
        if (JDKIMPL) {
            // JDK uses deprecated ISO639.1 language codes for he, yi and id
            if (AsciiUtil.caseIgnoreMatch(language, "he")) {
                language = "iw";
            } else if (AsciiUtil.caseIgnoreMatch(language, "yi")) {
                language = "ji";
            } else if (AsciiUtil.caseIgnoreMatch(language, "id")) {
                language = "in";
            }
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

    @Override
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

    @Override
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

    @Override
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

        @Override
        public boolean equals(Object obj) {
            if (JDKIMPL) {
                return (this == obj) ||
                        (obj instanceof Key)
                        && AsciiUtil.caseIgnoreMatch(((Key)obj)._lang, this._lang)
                        && AsciiUtil.caseIgnoreMatch(((Key)obj)._scrt, this._scrt)
                        && AsciiUtil.caseIgnoreMatch(((Key)obj)._regn, this._regn)
                        && ((Key)obj)._vart.equals(_vart); // variant is case sensitive in JDK!
            }
            return (this == obj) ||
                    (obj instanceof Key)
                    && AsciiUtil.caseIgnoreMatch(((Key)obj)._lang, this._lang)
                    && AsciiUtil.caseIgnoreMatch(((Key)obj)._scrt, this._scrt)
                    && AsciiUtil.caseIgnoreMatch(((Key)obj)._regn, this._regn)
                    && AsciiUtil.caseIgnoreMatch(((Key)obj)._vart, this._vart);
        }

        @Override
        public int compareTo(Key other) {
            int res = AsciiUtil.caseIgnoreCompare(this._lang, other._lang);
            if (res == 0) {
                res = AsciiUtil.caseIgnoreCompare(this._scrt, other._scrt);
                if (res == 0) {
                    res = AsciiUtil.caseIgnoreCompare(this._regn, other._regn);
                    if (res == 0) {
                        if (JDKIMPL) {
                            res = this._vart.compareTo(other._vart);
                        } else {
                            res = AsciiUtil.caseIgnoreCompare(this._vart, other._vart);
                        }
                    }
                }
            }
            return res;
        }

        @Override
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
                    if (JDKIMPL) {
                        h = 31*h + _vart.charAt(i);
                    } else {
                        h = 31*h + AsciiUtil.toLower(_vart.charAt(i));
                    }
                }
                _hash = h;
            }
            return h;
        }

        public static Key normalize(Key key) {
            String lang = AsciiUtil.toLowerString(key._lang).intern();
            String scrt = AsciiUtil.toTitleString(key._scrt).intern();
            String regn = AsciiUtil.toUpperString(key._regn).intern();
            String vart;
            if (JDKIMPL) {
                // preserve upper/lower cases
                vart = key._vart.intern();
            } else {
                vart = AsciiUtil.toUpperString(key._vart).intern();
            }
            return new Key(lang, scrt, regn, vart);
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
            return new BaseLocale(key._lang, key._scrt, key._regn, key._vart);
        }

    }
}
