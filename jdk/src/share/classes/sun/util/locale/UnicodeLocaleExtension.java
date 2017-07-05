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

import java.util.Collections;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class UnicodeLocaleExtension extends Extension {
    public static final char SINGLETON = 'u';

    private static final SortedSet<String> EMPTY_SORTED_SET = new TreeSet<String>();
    private static final SortedMap<String, String> EMPTY_SORTED_MAP = new TreeMap<String, String>();

    private SortedSet<String> _attributes = EMPTY_SORTED_SET;
    private SortedMap<String, String> _keywords = EMPTY_SORTED_MAP;

    public static final UnicodeLocaleExtension CA_JAPANESE;
    public static final UnicodeLocaleExtension NU_THAI;

    static {
        CA_JAPANESE = new UnicodeLocaleExtension();
        CA_JAPANESE._keywords = new TreeMap<String, String>();
        CA_JAPANESE._keywords.put("ca", "japanese");
        CA_JAPANESE._value = "ca-japanese";

        NU_THAI = new UnicodeLocaleExtension();
        NU_THAI._keywords = new TreeMap<String, String>();
        NU_THAI._keywords.put("nu", "thai");
        NU_THAI._value = "nu-thai";
    }

    private UnicodeLocaleExtension() {
        super(SINGLETON);
    }

    UnicodeLocaleExtension(SortedSet<String> attributes, SortedMap<String, String> keywords) {
        this();
        if (attributes != null && attributes.size() > 0) {
            _attributes = attributes;
        }
        if (keywords != null && keywords.size() > 0) {
            _keywords = keywords;
        }

        if (_attributes.size() > 0 || _keywords.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (String attribute : _attributes) {
                sb.append(LanguageTag.SEP).append(attribute);
            }
            for (Entry<String, String> keyword : _keywords.entrySet()) {
                String key = keyword.getKey();
                String value = keyword.getValue();

                sb.append(LanguageTag.SEP).append(key);
                if (value.length() > 0) {
                    sb.append(LanguageTag.SEP).append(value);
                }
            }
            _value = sb.substring(1);   // skip leading '-'
        }
    }

    public Set<String> getUnicodeLocaleAttributes() {
        return Collections.unmodifiableSet(_attributes);
    }

    public Set<String> getUnicodeLocaleKeys() {
        return Collections.unmodifiableSet(_keywords.keySet());
    }

    public String getUnicodeLocaleType(String unicodeLocaleKey) {
        return _keywords.get(unicodeLocaleKey);
    }

    public static boolean isSingletonChar(char c) {
        return (SINGLETON == AsciiUtil.toLower(c));
    }

    public static boolean isAttribute(String s) {
        // 3*8alphanum
        return (s.length() >= 3) && (s.length() <= 8) && AsciiUtil.isAlphaNumericString(s);
    }

    public static boolean isKey(String s) {
        // 2alphanum
        return (s.length() == 2) && AsciiUtil.isAlphaNumericString(s);
    }

    public static boolean isTypeSubtag(String s) {
        // 3*8alphanum
        return (s.length() >= 3) && (s.length() <= 8) && AsciiUtil.isAlphaNumericString(s);
    }
}
