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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class InternalLocaleBuilder {

    private String _language = "";
    private String _script = "";
    private String _region = "";
    private String _variant = "";

    private static final CaseInsensitiveChar PRIVUSE_KEY = new CaseInsensitiveChar(LanguageTag.PRIVATEUSE.charAt(0));

    private HashMap<CaseInsensitiveChar, String> _extensions;
    private HashSet<CaseInsensitiveString> _uattributes;
    private HashMap<CaseInsensitiveString, String> _ukeywords;


    public InternalLocaleBuilder() {
    }

    public InternalLocaleBuilder setLanguage(String language) throws LocaleSyntaxException {
        if (language == null || language.length() == 0) {
            _language = "";
        } else {
            if (!LanguageTag.isLanguage(language)) {
                throw new LocaleSyntaxException("Ill-formed language: " + language, 0);
            }
            _language = language;
        }
        return this;
    }

    public InternalLocaleBuilder setScript(String script) throws LocaleSyntaxException {
        if (script == null || script.length() == 0) {
            _script = "";
        } else {
            if (!LanguageTag.isScript(script)) {
                throw new LocaleSyntaxException("Ill-formed script: " + script, 0);
            }
            _script = script;
        }
        return this;
    }

    public InternalLocaleBuilder setRegion(String region) throws LocaleSyntaxException {
        if (region == null || region.length() == 0) {
            _region = "";
        } else {
            if (!LanguageTag.isRegion(region)) {
                throw new LocaleSyntaxException("Ill-formed region: " + region, 0);
            }
            _region = region;
        }
        return this;
    }

    public InternalLocaleBuilder setVariant(String variant) throws LocaleSyntaxException {
        if (variant == null || variant.length() == 0) {
            _variant = "";
        } else {
            // normalize separators to "_"
            String var = variant.replaceAll(LanguageTag.SEP, BaseLocale.SEP);
            int errIdx = checkVariants(var, BaseLocale.SEP);
            if (errIdx != -1) {
                throw new LocaleSyntaxException("Ill-formed variant: " + variant, errIdx);
            }
            _variant = var;
        }
        return this;
    }

    public InternalLocaleBuilder addUnicodeLocaleAttribute(String attribute) throws LocaleSyntaxException {
        if (!UnicodeLocaleExtension.isAttribute(attribute)) {
            throw new LocaleSyntaxException("Ill-formed Unicode locale attribute: " + attribute);
        }
        // Use case insensitive string to prevent duplication
        if (_uattributes == null) {
            _uattributes = new HashSet<CaseInsensitiveString>(4);
        }
        _uattributes.add(new CaseInsensitiveString(attribute));
        return this;
    }

    public InternalLocaleBuilder removeUnicodeLocaleAttribute(String attribute) throws LocaleSyntaxException {
        if (attribute == null || !UnicodeLocaleExtension.isAttribute(attribute)) {
            throw new LocaleSyntaxException("Ill-formed Unicode locale attribute: " + attribute);
        }
        if (_uattributes != null) {
            _uattributes.remove(new CaseInsensitiveString(attribute));
        }
        return this;
    }

    public InternalLocaleBuilder setUnicodeLocaleKeyword(String key, String type) throws LocaleSyntaxException {
        if (!UnicodeLocaleExtension.isKey(key)) {
            throw new LocaleSyntaxException("Ill-formed Unicode locale keyword key: " + key);
        }

        CaseInsensitiveString cikey = new CaseInsensitiveString(key);
        if (type == null) {
            if (_ukeywords != null) {
                // null type is used for remove the key
                _ukeywords.remove(cikey);
            }
        } else {
            if (type.length() != 0) {
                // normalize separator to "-"
                String tp = type.replaceAll(BaseLocale.SEP, LanguageTag.SEP);
                // validate
                StringTokenIterator itr = new StringTokenIterator(tp, LanguageTag.SEP);
                while (!itr.isDone()) {
                    String s = itr.current();
                    if (!UnicodeLocaleExtension.isTypeSubtag(s)) {
                        throw new LocaleSyntaxException("Ill-formed Unicode locale keyword type: " + type, itr.currentStart());
                    }
                    itr.next();
                }
            }
            if (_ukeywords == null) {
                _ukeywords = new HashMap<CaseInsensitiveString, String>(4);
            }
            _ukeywords.put(cikey, type);
        }
        return this;
    }

    public InternalLocaleBuilder setExtension(char singleton, String value) throws LocaleSyntaxException {
        // validate key
        boolean isBcpPrivateuse = LanguageTag.isPrivateusePrefixChar(singleton);
        if (!isBcpPrivateuse && !LanguageTag.isExtensionSingletonChar(singleton)) {
            throw new LocaleSyntaxException("Ill-formed extension key: " + singleton);
        }

        boolean remove = (value == null || value.length() == 0);
        CaseInsensitiveChar key = new CaseInsensitiveChar(singleton);

        if (remove) {
            if (UnicodeLocaleExtension.isSingletonChar(key.value())) {
                // clear entire Unicode locale extension
                if (_uattributes != null) {
                    _uattributes.clear();
                }
                if (_ukeywords != null) {
                    _ukeywords.clear();
                }
            } else {
                if (_extensions != null && _extensions.containsKey(key)) {
                    _extensions.remove(key);
                }
            }
        } else {
            // validate value
            String val = value.replaceAll(BaseLocale.SEP, LanguageTag.SEP);
            StringTokenIterator itr = new StringTokenIterator(val, LanguageTag.SEP);
            while (!itr.isDone()) {
                String s = itr.current();
                boolean validSubtag;
                if (isBcpPrivateuse) {
                    validSubtag = LanguageTag.isPrivateuseSubtag(s);
                } else {
                    validSubtag = LanguageTag.isExtensionSubtag(s);
                }
                if (!validSubtag) {
                    throw new LocaleSyntaxException("Ill-formed extension value: " + s, itr.currentStart());
                }
                itr.next();
            }

            if (UnicodeLocaleExtension.isSingletonChar(key.value())) {
                setUnicodeLocaleExtension(val);
            } else {
                if (_extensions == null) {
                    _extensions = new HashMap<CaseInsensitiveChar, String>(4);
                }
                _extensions.put(key, val);
            }
        }
        return this;
    }

    /*
     * Set extension/private subtags in a single string representation
     */
    public InternalLocaleBuilder setExtensions(String subtags) throws LocaleSyntaxException {
        if (subtags == null || subtags.length() == 0) {
            clearExtensions();
            return this;
        }
        subtags = subtags.replaceAll(BaseLocale.SEP, LanguageTag.SEP);
        StringTokenIterator itr = new StringTokenIterator(subtags, LanguageTag.SEP);

        List<String> extensions = null;
        String privateuse = null;

        int parsed = 0;
        int start;

        // Make a list of extension subtags
        while (!itr.isDone()) {
            String s = itr.current();
            if (LanguageTag.isExtensionSingleton(s)) {
                start = itr.currentStart();
                String singleton = s;
                StringBuilder sb = new StringBuilder(singleton);

                itr.next();
                while (!itr.isDone()) {
                    s = itr.current();
                    if (LanguageTag.isExtensionSubtag(s)) {
                        sb.append(LanguageTag.SEP).append(s);
                        parsed = itr.currentEnd();
                    } else {
                        break;
                    }
                    itr.next();
                }

                if (parsed < start) {
                    throw new LocaleSyntaxException("Incomplete extension '" + singleton + "'", start);
                }

                if (extensions == null) {
                    extensions = new ArrayList<String>(4);
                }
                extensions.add(sb.toString());
            } else {
                break;
            }
        }
        if (!itr.isDone()) {
            String s = itr.current();
            if (LanguageTag.isPrivateusePrefix(s)) {
                start = itr.currentStart();
                StringBuilder sb = new StringBuilder(s);

                itr.next();
                while (!itr.isDone()) {
                    s = itr.current();
                    if (!LanguageTag.isPrivateuseSubtag(s)) {
                        break;
                    }
                    sb.append(LanguageTag.SEP).append(s);
                    parsed = itr.currentEnd();

                    itr.next();
                }
                if (parsed <= start) {
                    throw new LocaleSyntaxException("Incomplete privateuse:" + subtags.substring(start), start);
                } else {
                    privateuse = sb.toString();
                }
            }
        }

        if (!itr.isDone()) {
            throw new LocaleSyntaxException("Ill-formed extension subtags:" + subtags.substring(itr.currentStart()), itr.currentStart());
        }

        return setExtensions(extensions, privateuse);
    }

    /*
     * Set a list of BCP47 extensions and private use subtags
     * BCP47 extensions are already validated and well-formed, but may contain duplicates
     */
    private InternalLocaleBuilder setExtensions(List<String> bcpExtensions, String privateuse) {
        clearExtensions();

        if (bcpExtensions != null && bcpExtensions.size() > 0) {
            HashSet<CaseInsensitiveChar> processedExntensions = new HashSet<CaseInsensitiveChar>(bcpExtensions.size());
            for (String bcpExt : bcpExtensions) {
                CaseInsensitiveChar key = new CaseInsensitiveChar(bcpExt.charAt(0));
                // ignore duplicates
                if (!processedExntensions.contains(key)) {
                    // each extension string contains singleton, e.g. "a-abc-def"
                    if (UnicodeLocaleExtension.isSingletonChar(key.value())) {
                        setUnicodeLocaleExtension(bcpExt.substring(2));
                    } else {
                        if (_extensions == null) {
                            _extensions = new HashMap<CaseInsensitiveChar, String>(4);
                        }
                        _extensions.put(key, bcpExt.substring(2));
                    }
                }
            }
        }
        if (privateuse != null && privateuse.length() > 0) {
            // privateuse string contains prefix, e.g. "x-abc-def"
            if (_extensions == null) {
                _extensions = new HashMap<CaseInsensitiveChar, String>(1);
            }
            _extensions.put(new CaseInsensitiveChar(privateuse.charAt(0)), privateuse.substring(2));
        }

        return this;
    }

    /*
     * Reset Builder's internal state with the given language tag
     */
    public InternalLocaleBuilder setLanguageTag(LanguageTag langtag) {
        clear();
        if (langtag.getExtlangs().size() > 0) {
            _language = langtag.getExtlangs().get(0);
        } else {
            String language = langtag.getLanguage();
            if (!language.equals(LanguageTag.UNDETERMINED)) {
                _language = language;
            }
        }
        _script = langtag.getScript();
        _region = langtag.getRegion();

        List<String> bcpVariants = langtag.getVariants();
        if (bcpVariants.size() > 0) {
            StringBuilder var = new StringBuilder(bcpVariants.get(0));
            for (int i = 1; i < bcpVariants.size(); i++) {
                var.append(BaseLocale.SEP).append(bcpVariants.get(i));
            }
            _variant = var.toString();
        }

        setExtensions(langtag.getExtensions(), langtag.getPrivateuse());

        return this;
    }

    public InternalLocaleBuilder setLocale(BaseLocale base, LocaleExtensions extensions) throws LocaleSyntaxException {
        String language = base.getLanguage();
        String script = base.getScript();
        String region = base.getRegion();
        String variant = base.getVariant();

        // Special backward compatibility support

        // Exception 1 - ja_JP_JP
        if (language.equals("ja") && region.equals("JP") && variant.equals("JP")) {
            // When locale ja_JP_JP is created, ca-japanese is always there.
            // The builder ignores the variant "JP"
            assert("japanese".equals(extensions.getUnicodeLocaleType("ca")));
            variant = "";
        }
        // Exception 2 - th_TH_TH
        else if (language.equals("th") && region.equals("TH") && variant.equals("TH")) {
            // When locale th_TH_TH is created, nu-thai is always there.
            // The builder ignores the variant "TH"
            assert("thai".equals(extensions.getUnicodeLocaleType("nu")));
            variant = "";
        }
        // Exception 3 - no_NO_NY
        else if (language.equals("no") && region.equals("NO") && variant.equals("NY")) {
            // no_NO_NY is a valid locale and used by Java 6 or older versions.
            // The build ignores the variant "NY" and change the language to "nn".
            language = "nn";
            variant = "";
        }

        // Validate base locale fields before updating internal state.
        // LocaleExtensions always store validated/canonicalized values,
        // so no checks are necessary.
        if (language.length() > 0 && !LanguageTag.isLanguage(language)) {
            throw new LocaleSyntaxException("Ill-formed language: " + language);
        }

        if (script.length() > 0 && !LanguageTag.isScript(script)) {
            throw new LocaleSyntaxException("Ill-formed script: " + script);
        }

        if (region.length() > 0 && !LanguageTag.isRegion(region)) {
            throw new LocaleSyntaxException("Ill-formed region: " + region);
        }

        if (variant.length() > 0) {
            int errIdx = checkVariants(variant, BaseLocale.SEP);
            if (errIdx != -1) {
                throw new LocaleSyntaxException("Ill-formed variant: " + variant, errIdx);
            }
        }

        // The input locale is validated at this point.
        // Now, updating builder's internal fields.
        _language = language;
        _script = script;
        _region = region;
        _variant = variant;
        clearExtensions();

        Set<Character> extKeys = (extensions == null) ? null : extensions.getKeys();
        if (extKeys != null) {
            // map extensions back to builder's internal format
            for (Character key : extKeys) {
                Extension e = extensions.getExtension(key);
                if (e instanceof UnicodeLocaleExtension) {
                    UnicodeLocaleExtension ue = (UnicodeLocaleExtension)e;
                    for (String uatr : ue.getUnicodeLocaleAttributes()) {
                        if (_uattributes == null) {
                            _uattributes = new HashSet<CaseInsensitiveString>(4);
                        }
                        _uattributes.add(new CaseInsensitiveString(uatr));
                    }
                    for (String ukey : ue.getUnicodeLocaleKeys()) {
                        if (_ukeywords == null) {
                            _ukeywords = new HashMap<CaseInsensitiveString, String>(4);
                        }
                        _ukeywords.put(new CaseInsensitiveString(ukey), ue.getUnicodeLocaleType(ukey));
                    }
                } else {
                    if (_extensions == null) {
                        _extensions = new HashMap<CaseInsensitiveChar, String>(4);
                    }
                    _extensions.put(new CaseInsensitiveChar(key.charValue()), e.getValue());
                }
            }
        }
        return this;
    }

    public InternalLocaleBuilder clear() {
        _language = "";
        _script = "";
        _region = "";
        _variant = "";
        clearExtensions();
        return this;
    }

    public InternalLocaleBuilder clearExtensions() {
        if (_extensions != null) {
            _extensions.clear();
        }
        if (_uattributes != null) {
            _uattributes.clear();
        }
        if (_ukeywords != null) {
            _ukeywords.clear();
        }
        return this;
    }

    public BaseLocale getBaseLocale() {
        String language = _language;
        String script = _script;
        String region = _region;
        String variant = _variant;

        // Special private use subtag sequence identified by "lvariant" will be
        // interpreted as Java variant.
        if (_extensions != null) {
            String privuse = _extensions.get(PRIVUSE_KEY);
            if (privuse != null) {
                StringTokenIterator itr = new StringTokenIterator(privuse, LanguageTag.SEP);
                boolean sawPrefix = false;
                int privVarStart = -1;
                while (!itr.isDone()) {
                    if (sawPrefix) {
                        privVarStart = itr.currentStart();
                        break;
                    }
                    if (AsciiUtil.caseIgnoreMatch(itr.current(), LanguageTag.PRIVUSE_VARIANT_PREFIX)) {
                        sawPrefix = true;
                    }
                    itr.next();
                }
                if (privVarStart != -1) {
                    StringBuilder sb = new StringBuilder(variant);
                    if (sb.length() != 0) {
                        sb.append(BaseLocale.SEP);
                    }
                    sb.append(privuse.substring(privVarStart).replaceAll(LanguageTag.SEP, BaseLocale.SEP));
                    variant = sb.toString();
                }
            }
        }

        return BaseLocale.getInstance(language, script, region, variant);
    }

    public LocaleExtensions getLocaleExtensions() {
        if ((_extensions == null || _extensions.size() == 0)
                && (_uattributes == null || _uattributes.size() == 0)
                && (_ukeywords == null || _ukeywords.size() == 0)) {
            return LocaleExtensions.EMPTY_EXTENSIONS;
        }

        return new LocaleExtensions(_extensions, _uattributes, _ukeywords);
    }

    /*
     * Remove special private use subtag sequence identified by "lvariant"
     * and return the rest. Only used by LocaleExtensions
     */
    static String removePrivateuseVariant(String privuseVal) {
        StringTokenIterator itr = new StringTokenIterator(privuseVal, LanguageTag.SEP);

        // Note: privateuse value "abc-lvariant" is unchanged
        // because no subtags after "lvariant".

        int prefixStart = -1;
        boolean sawPrivuseVar = false;
        while (!itr.isDone()) {
            if (prefixStart != -1) {
                // Note: privateuse value "abc-lvariant" is unchanged
                // because no subtags after "lvariant".
                sawPrivuseVar = true;
                break;
            }
            if (AsciiUtil.caseIgnoreMatch(itr.current(), LanguageTag.PRIVUSE_VARIANT_PREFIX)) {
                prefixStart = itr.currentStart();
            }
            itr.next();
        }
        if (!sawPrivuseVar) {
            return privuseVal;
        }

        assert(prefixStart == 0 || prefixStart > 1);
        return (prefixStart == 0) ? null : privuseVal.substring(0, prefixStart -1);
    }

    /*
     * Check if the given variant subtags separated by the given
     * separator(s) are valid
     */
    private int checkVariants(String variants, String sep) {
        StringTokenIterator itr = new StringTokenIterator(variants, sep);
        while (!itr.isDone()) {
            String s = itr.current();
            if (!LanguageTag.isVariant(s)) {
                return itr.currentStart();
            }
            itr.next();
        }
        return -1;
    }

    /*
     * Private methods parsing Unicode Locale Extension subtags.
     * Duplicated attributes/keywords will be ignored.
     * The input must be a valid extension subtags (excluding singleton).
     */
    private void setUnicodeLocaleExtension(String subtags) {
        // wipe out existing attributes/keywords
        if (_uattributes != null) {
            _uattributes.clear();
        }
        if (_ukeywords != null) {
            _ukeywords.clear();
        }

        StringTokenIterator itr = new StringTokenIterator(subtags, LanguageTag.SEP);

        // parse attributes
        while (!itr.isDone()) {
            if (!UnicodeLocaleExtension.isAttribute(itr.current())) {
                break;
            }
            if (_uattributes == null) {
                _uattributes = new HashSet<CaseInsensitiveString>(4);
            }
            _uattributes.add(new CaseInsensitiveString(itr.current()));
            itr.next();
        }

        // parse keywords
        CaseInsensitiveString key = null;
        String type;
        int typeStart = -1;
        int typeEnd = -1;
        while (!itr.isDone()) {
            if (key != null) {
                if (UnicodeLocaleExtension.isKey(itr.current())) {
                    // next keyword - emit previous one
                    assert(typeStart == -1 || typeEnd != -1);
                    type = (typeStart == -1) ? "" : subtags.substring(typeStart, typeEnd);
                    if (_ukeywords == null) {
                        _ukeywords = new HashMap<CaseInsensitiveString, String>(4);
                    }
                    _ukeywords.put(key, type);

                    // reset keyword info
                    CaseInsensitiveString tmpKey = new CaseInsensitiveString(itr.current());
                    key = _ukeywords.containsKey(tmpKey) ? null : tmpKey;
                    typeStart = typeEnd = -1;
                } else {
                    if (typeStart == -1) {
                        typeStart = itr.currentStart();
                    }
                    typeEnd = itr.currentEnd();
                }
            } else if (UnicodeLocaleExtension.isKey(itr.current())) {
                // 1. first keyword or
                // 2. next keyword, but previous one was duplicate
                key = new CaseInsensitiveString(itr.current());
                if (_ukeywords != null && _ukeywords.containsKey(key)) {
                    // duplicate
                    key = null;
                }
            }

            if (!itr.hasNext()) {
                if (key != null) {
                    // last keyword
                    assert(typeStart == -1 || typeEnd != -1);
                    type = (typeStart == -1) ? "" : subtags.substring(typeStart, typeEnd);
                    if (_ukeywords == null) {
                        _ukeywords = new HashMap<CaseInsensitiveString, String>(4);
                    }
                    _ukeywords.put(key, type);
                }
                break;
            }

            itr.next();
        }
    }

    static class CaseInsensitiveString {
        private String _s;

        CaseInsensitiveString(String s) {
            _s = s;
        }

        public String value() {
            return _s;
        }

        public int hashCode() {
            return AsciiUtil.toLowerString(_s).hashCode();
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CaseInsensitiveString)) {
                return false;
            }
            return AsciiUtil.caseIgnoreMatch(_s, ((CaseInsensitiveString)obj).value());
        }
    }

    static class CaseInsensitiveChar {
        private char _c;

        CaseInsensitiveChar(char c) {
            _c = c;
        }

        public char value() {
            return _c;
        }

        public int hashCode() {
            return AsciiUtil.toLower(_c);
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof CaseInsensitiveChar)) {
                return false;
            }
            return _c ==  AsciiUtil.toLower(((CaseInsensitiveChar)obj).value());
        }

    }
}
