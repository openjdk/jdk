// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009-2010, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package jdk.internal.icu.impl.locale;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import jdk.internal.icu.impl.locale.InternalLocaleBuilder.CaseInsensitiveChar;
import jdk.internal.icu.impl.locale.InternalLocaleBuilder.CaseInsensitiveString;


public class LocaleExtensions {

    private SortedMap<Character, Extension> _map;
    private String _id;

    private static final SortedMap<Character, Extension> EMPTY_MAP =
        Collections.unmodifiableSortedMap(new TreeMap<Character, Extension>());

    public static final LocaleExtensions EMPTY_EXTENSIONS;
    public static final LocaleExtensions CALENDAR_JAPANESE;
    public static final LocaleExtensions NUMBER_THAI;

    static {
        EMPTY_EXTENSIONS = new LocaleExtensions();
        EMPTY_EXTENSIONS._id = "";
        EMPTY_EXTENSIONS._map = EMPTY_MAP;

        CALENDAR_JAPANESE = new LocaleExtensions();
        CALENDAR_JAPANESE._id = "u-ca-japanese";
        CALENDAR_JAPANESE._map = new TreeMap<Character, Extension>();
        CALENDAR_JAPANESE._map.put(Character.valueOf(UnicodeLocaleExtension.SINGLETON), UnicodeLocaleExtension.CA_JAPANESE);

        NUMBER_THAI = new LocaleExtensions();
        NUMBER_THAI._id = "u-nu-thai";
        NUMBER_THAI._map = new TreeMap<Character, Extension>();
        NUMBER_THAI._map.put(Character.valueOf(UnicodeLocaleExtension.SINGLETON), UnicodeLocaleExtension.NU_THAI);
    }

    private LocaleExtensions() {
    }

    /*
     * Package local constructor, only used by InternalLocaleBuilder.
     */
    LocaleExtensions(Map<CaseInsensitiveChar, String> extensions,
            Set<CaseInsensitiveString> uattributes, Map<CaseInsensitiveString, String> ukeywords) {
        boolean hasExtension = (extensions != null && extensions.size() > 0);
        boolean hasUAttributes = (uattributes != null && uattributes.size() > 0);
        boolean hasUKeywords = (ukeywords != null && ukeywords.size() > 0);

        if (!hasExtension && !hasUAttributes && !hasUKeywords) {
            _map = EMPTY_MAP;
            _id = "";
            return;
        }

        // Build extension map
        _map = new TreeMap<Character, Extension>();
        if (hasExtension) {
            for (Entry<CaseInsensitiveChar, String> ext : extensions.entrySet()) {
                char key = AsciiUtil.toLower(ext.getKey().value());
                String value = ext.getValue();

                if (LanguageTag.isPrivateusePrefixChar(key)) {
                    // we need to exclude special variant in privuateuse, e.g. "x-abc-lvariant-DEF"
                    value = InternalLocaleBuilder.removePrivateuseVariant(value);
                    if (value == null) {
                        continue;
                    }
                }

                Extension e = new Extension(key, AsciiUtil.toLowerString(value));
                _map.put(Character.valueOf(key), e);
            }
        }

        if (hasUAttributes || hasUKeywords) {
            TreeSet<String> uaset = null;
            TreeMap<String, String> ukmap = null;

            if (hasUAttributes) {
                uaset = new TreeSet<String>();
                for (CaseInsensitiveString cis : uattributes) {
                    uaset.add(AsciiUtil.toLowerString(cis.value()));
                }
            }

            if (hasUKeywords) {
                ukmap = new TreeMap<String, String>();
                for (Entry<CaseInsensitiveString, String> kwd : ukeywords.entrySet()) {
                    String key = AsciiUtil.toLowerString(kwd.getKey().value());
                    String type = AsciiUtil.toLowerString(kwd.getValue());
                    ukmap.put(key, type);
                }
            }

            UnicodeLocaleExtension ule = new UnicodeLocaleExtension(uaset, ukmap);
            _map.put(Character.valueOf(UnicodeLocaleExtension.SINGLETON), ule);
        }

        if (_map.size() == 0) {
            // this could happen when only privuateuse with special variant
            _map = EMPTY_MAP;
            _id = "";
        } else {
            _id = toID(_map);
        }
    }

    public Set<Character> getKeys() {
        return Collections.unmodifiableSet(_map.keySet());
    }

    public Extension getExtension(Character key) {
        return _map.get(Character.valueOf(AsciiUtil.toLower(key.charValue())));
    }

    public String getExtensionValue(Character key) {
        Extension ext = _map.get(Character.valueOf(AsciiUtil.toLower(key.charValue())));
        if (ext == null) {
            return null;
        }
        return ext.getValue();
    }

    public Set<String> getUnicodeLocaleAttributes() {
        Extension ext = _map.get(Character.valueOf(UnicodeLocaleExtension.SINGLETON));
        if (ext == null) {
            return Collections.emptySet();
        }
        assert (ext instanceof UnicodeLocaleExtension);
        return ((UnicodeLocaleExtension)ext).getUnicodeLocaleAttributes();
    }

    public Set<String> getUnicodeLocaleKeys() {
        Extension ext = _map.get(Character.valueOf(UnicodeLocaleExtension.SINGLETON));
        if (ext == null) {
            return Collections.emptySet();
        }
        assert (ext instanceof UnicodeLocaleExtension);
        return ((UnicodeLocaleExtension)ext).getUnicodeLocaleKeys();
    }

    public String getUnicodeLocaleType(String unicodeLocaleKey) {
        Extension ext = _map.get(Character.valueOf(UnicodeLocaleExtension.SINGLETON));
        if (ext == null) {
            return null;
        }
        assert (ext instanceof UnicodeLocaleExtension);
        return ((UnicodeLocaleExtension)ext).getUnicodeLocaleType(AsciiUtil.toLowerString(unicodeLocaleKey));
    }

    public boolean isEmpty() {
        return _map.isEmpty();
    }

    public static boolean isValidKey(char c) {
        return LanguageTag.isExtensionSingletonChar(c) || LanguageTag.isPrivateusePrefixChar(c);
    }

    public static boolean isValidUnicodeLocaleKey(String ukey) {
        return UnicodeLocaleExtension.isKey(ukey);
    }

    private static String toID(SortedMap<Character, Extension> map) {
        StringBuilder buf = new StringBuilder();
        Extension privuse = null;
        for (Entry<Character, Extension> entry : map.entrySet()) {
            char singleton = entry.getKey().charValue();
            Extension extension = entry.getValue();
            if (LanguageTag.isPrivateusePrefixChar(singleton)) {
                privuse = extension;
            } else {
                if (buf.length() > 0) {
                    buf.append(LanguageTag.SEP);
                }
                buf.append(extension);
            }
        }
        if (privuse != null) {
            if (buf.length() > 0) {
                buf.append(LanguageTag.SEP);
            }
            buf.append(privuse);
        }
        return buf.toString();
    }


    @Override
    public String toString() {
        return _id;
    }

    public String getID() {
        return _id;
    }

    @Override
    public int hashCode() {
        return _id.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LocaleExtensions)) {
            return false;
        }
        return this._id.equals(((LocaleExtensions)other)._id);
    }
}
