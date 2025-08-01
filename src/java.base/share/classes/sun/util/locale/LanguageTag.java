/*
 * Copyright (c) 2010, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * Copyright (C) 2010, International Business Machines Corporation and         *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package sun.util.locale;

import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

// List fields are unmodifiable
public record LanguageTag(String language, String script, String region, String privateuse,
                          List<String> extlangs, List<String> variants, List<String> extensions) {

    public static final String SEP = "-";
    public static final String PRIVATEUSE = "x";
    public static final String UNDETERMINED = "und";
    public static final String PRIVUSE_VARIANT_PREFIX = "lvariant";
    private static final String EMPTY_SUBTAG = "";
    private static final List<String> EMPTY_SUBTAGS = List.of();

    // Map contains legacy language tags and its preferred mappings from
    // http://www.ietf.org/rfc/rfc5646.txt
    // Keys are lower-case strings.
    private static final Map<String, String[]> LEGACY;

    static {
        // grandfathered = irregular           ; non-redundant tags registered
        //               / regular             ; during the RFC 3066 era
        //
        // irregular     = "en-GB-oed"         ; irregular tags do not match
        //               / "i-ami"             ; the 'langtag' production and
        //               / "i-bnn"             ; would not otherwise be
        //               / "i-default"         ; considered 'well-formed'
        //               / "i-enochian"        ; These tags are all valid,
        //               / "i-hak"             ; but most are deprecated
        //               / "i-klingon"         ; in favor of more modern
        //               / "i-lux"             ; subtags or subtag
        //               / "i-mingo"           ; combination
        //               / "i-navajo"
        //               / "i-pwn"
        //               / "i-tao"
        //               / "i-tay"
        //               / "i-tsu"
        //               / "sgn-BE-FR"
        //               / "sgn-BE-NL"
        //               / "sgn-CH-DE"
        //
        // regular       = "art-lojban"        ; these tags match the 'langtag'
        //               / "cel-gaulish"       ; production, but their subtags
        //               / "no-bok"            ; are not extended language
        //               / "no-nyn"            ; or variant subtags: their meaning
        //               / "zh-guoyu"          ; is defined by their registration
        //               / "zh-hakka"          ; and all of these are deprecated
        //               / "zh-min"            ; in favor of a more modern
        //               / "zh-min-nan"        ; subtag or sequence of subtags
        //               / "zh-xiang"

        final String[][] entries = {
          //{"tag",         "preferred"},
            {"art-lojban",  "jbo"},
            {"cel-gaulish", "xtg-x-cel-gaulish"},   // fallback
            {"en-GB-oed",   "en-GB-x-oed"},         // fallback
            {"i-ami",       "ami"},
            {"i-bnn",       "bnn"},
            {"i-default",   "en-x-i-default"},      // fallback
            {"i-enochian",  "und-x-i-enochian"},    // fallback
            {"i-hak",       "hak"},
            {"i-klingon",   "tlh"},
            {"i-lux",       "lb"},
            {"i-mingo",     "see-x-i-mingo"},       // fallback
            {"i-navajo",    "nv"},
            {"i-pwn",       "pwn"},
            {"i-tao",       "tao"},
            {"i-tay",       "tay"},
            {"i-tsu",       "tsu"},
            {"no-bok",      "nb"},
            {"no-nyn",      "nn"},
            {"sgn-BE-FR",   "sfb"},
            {"sgn-BE-NL",   "vgt"},
            {"sgn-CH-DE",   "sgg"},
            {"zh-guoyu",    "cmn"},
            {"zh-hakka",    "hak"},
            {"zh-min",      "nan-x-zh-min"},        // fallback
            {"zh-min-nan",  "nan"},
            {"zh-xiang",    "hsn"},
        };
        LEGACY = HashMap.newHashMap(entries.length);
        for (String[] e : entries) {
            LEGACY.put(LocaleUtils.toLowerString(e[0]), e);
        }
    }

    /*
     * BNF in RFC5646
     *
     * Language-Tag  = langtag             ; normal language tags
     *               / privateuse          ; private use tag
     *               / grandfathered       ; grandfathered tags
     *
     *
     * langtag       = language
     *                 ["-" script]
     *                 ["-" region]
     *                 *("-" variant)
     *                 *("-" extension)
     *                 ["-" privateuse]
     *
     * language      = 2*3ALPHA            ; shortest ISO 639 code
     *                 ["-" extlang]       ; sometimes followed by
     *                                     ; extended language subtags
     *               / 4ALPHA              ; or reserved for future use
     *               / 5*8ALPHA            ; or registered language subtag
     *
     * extlang       = 3ALPHA              ; selected ISO 639 codes
     *                 *2("-" 3ALPHA)      ; permanently reserved
     *
     * script        = 4ALPHA              ; ISO 15924 code
     *
     * region        = 2ALPHA              ; ISO 3166-1 code
     *               / 3DIGIT              ; UN M.49 code
     *
     * variant       = 5*8alphanum         ; registered variants
     *               / (DIGIT 3alphanum)
     *
     * extension     = singleton 1*("-" (2*8alphanum))
     *
     *                                     ; Single alphanumerics
     *                                     ; "x" reserved for private use
     * singleton     = DIGIT               ; 0 - 9
     *               / %x41-57             ; A - W
     *               / %x59-5A             ; Y - Z
     *               / %x61-77             ; a - w
     *               / %x79-7A             ; y - z
     *
     * privateuse    = "x" 1*("-" (1*8alphanum))
     *
     */
    public static LanguageTag parse(String languageTag, ParsePosition pp,
                                    boolean lenient) {
        StringTokenIterator itr;
        var errorMsg = new StringBuilder();

        // Check if the tag is a legacy language tag
        String[] gfmap = LEGACY.get(LocaleUtils.toLowerString(languageTag));
        if (gfmap != null) {
            // use preferred mapping
            itr = new StringTokenIterator(gfmap[1], SEP);
        } else {
            itr = new StringTokenIterator(languageTag, SEP);
        }

        String language = parseLanguage(itr, pp);
        List<String> extlangs;
        String script;
        String region;
        List<String> variants;
        List<String> extensions;
        // langtag must start with either language or privateuse
        if (!language.isEmpty()) {
            extlangs = parseExtlangs(itr, pp);
            script = parseScript(itr, pp);
            region = parseRegion(itr, pp);
            variants = parseVariants(itr, pp);
            extensions = parseExtensions(itr, pp, errorMsg);
        } else {
            extlangs = EMPTY_SUBTAGS;
            script = EMPTY_SUBTAG;
            region = EMPTY_SUBTAG;
            variants = EMPTY_SUBTAGS;
            extensions = EMPTY_SUBTAGS;
        }
        String privateuse = parsePrivateuse(itr, pp, errorMsg);

        if (!itr.isDone() && pp.getErrorIndex() == -1) {
            String s = itr.current();
            pp.setErrorIndex(itr.currentStart());
            if (s.isEmpty()) {
                errorMsg.append("Empty subtag");
            } else {
                errorMsg.append("Invalid subtag: ").append(s);
            }
        }

        if (!lenient && pp.getErrorIndex() != -1) {
            throw new IllformedLocaleException(errorMsg.toString(), pp.getErrorIndex());
        }

        return new LanguageTag(language, script, region, privateuse, extlangs, variants, extensions);
    }

    //
    // Language subtag parsers
    //

    private static String parseLanguage(StringTokenIterator itr, ParsePosition pp) {
        if (itr.isDone() || pp.getErrorIndex() != -1) {
            return EMPTY_SUBTAG;
        }

        String s = itr.current();
        if (isLanguage(s)) {
            pp.setIndex(itr.currentEnd());
            itr.next();
            return s;
        }

        return EMPTY_SUBTAG;
    }

    private static List<String> parseExtlangs(StringTokenIterator itr, ParsePosition pp) {
        if (itr.isDone() || pp.getErrorIndex() != -1) {
            return EMPTY_SUBTAGS;
        }
        List<String> extlangs = null;
        while (!itr.isDone()) {
            String s = itr.current();
            if (!isExtlang(s)) {
                break;
            }
            if (extlangs == null) {
                extlangs = new ArrayList<>(3);
            }
            extlangs.add(s);
            pp.setIndex(itr.currentEnd());
            itr.next();
            if (extlangs.size() == 3) {
                // Maximum 3 extlangs
                break;
            }
        }
        return extlangs == null ? EMPTY_SUBTAGS :
                Collections.unmodifiableList(extlangs);
    }

    private static String parseScript(StringTokenIterator itr, ParsePosition pp) {
        if (itr.isDone() || pp.getErrorIndex() != -1) {
            return EMPTY_SUBTAG;
        }

        String s = itr.current();
        if (isScript(s)) {
            pp.setIndex(itr.currentEnd());
            itr.next();
            return s;
        }

        return EMPTY_SUBTAG;
    }

    private static String parseRegion(StringTokenIterator itr, ParsePosition pp) {
        if (itr.isDone() || pp.getErrorIndex() != -1) {
            return EMPTY_SUBTAG;
        }
        String s = itr.current();
        if (isRegion(s)) {
            pp.setIndex(itr.currentEnd());
            itr.next();
            return s;
        }

        return EMPTY_SUBTAG;
    }

    private static List<String> parseVariants(StringTokenIterator itr, ParsePosition pp) {
        if (itr.isDone() || pp.getErrorIndex() != -1) {
            return EMPTY_SUBTAGS;
        }
        List<String> variants = null;

        while (!itr.isDone()) {
            String s = itr.current();
            if (!isVariant(s)) {
                break;
            }
            if (variants == null) {
                variants = new ArrayList<>(3);
            }
            variants.add(s);
            pp.setIndex(itr.currentEnd());
            itr.next();
        }

        return variants == null ? EMPTY_SUBTAGS :
                Collections.unmodifiableList(variants);
    }

    private static List<String> parseExtensions(StringTokenIterator itr, ParsePosition pp,
                                    StringBuilder err) {
        if (itr.isDone() || pp.getErrorIndex() != -1) {
            return EMPTY_SUBTAGS;
        }
        List<String> extensions = null;

        while (!itr.isDone()) {
            String s = itr.current();
            if (isExtensionSingleton(s)) {
                int start = itr.currentStart();
                String singleton = s;
                StringBuilder sb = new StringBuilder(singleton);

                itr.next();
                while (!itr.isDone()) {
                    s = itr.current();
                    if (isExtensionSubtag(s)) {
                        sb.append(SEP).append(s);
                        pp.setIndex(itr.currentEnd());
                    } else {
                        break;
                    }
                    itr.next();
                }

                if (pp.getIndex() <= start) {
                    pp.setErrorIndex(start);
                    err.append("Incomplete extension '").append(singleton).append("'");
                    break;
                }

                if (extensions == null) {
                    extensions = new ArrayList<>(4);
                }
                extensions.add(sb.toString());
            } else {
                break;
            }
        }
        return extensions == null ? EMPTY_SUBTAGS :
                Collections.unmodifiableList(extensions);
    }

    private static String parsePrivateuse(StringTokenIterator itr, ParsePosition pp,
                                    StringBuilder err) {
        if (itr.isDone() || pp.getErrorIndex() != -1) {
            return EMPTY_SUBTAG;
        }

        String s = itr.current();
        if (isPrivateusePrefix(s)) {
            int start = itr.currentStart();
            StringBuilder sb = new StringBuilder(s);

            itr.next();
            while (!itr.isDone()) {
                s = itr.current();
                if (!isPrivateuseSubtag(s)) {
                    break;
                }
                sb.append(SEP).append(s);
                pp.setIndex(itr.currentEnd());

                itr.next();
            }

            if (pp.getIndex() <= start) {
                // need at least 1 private subtag
                pp.setErrorIndex(start);
                err.append("Incomplete privateuse");
            } else {
                return sb.toString();
            }
        }

        return EMPTY_SUBTAG;
    }

    public static String caseFoldTag(String tag) {
        parse(tag, new ParsePosition(0), false);

        // Legacy tags
        String potentialLegacy = tag.toLowerCase(Locale.ROOT);
        if (LEGACY.containsKey(potentialLegacy)) {
            return LEGACY.get(potentialLegacy)[0];
        }
        // Non-legacy tags
        StringBuilder bldr = new StringBuilder(tag.length());
        String[] subtags = tag.split("-");
        boolean privateFound = false;
        boolean singletonFound = false;
        boolean privUseVarFound = false;
        for (int i = 0; i < subtags.length; i++) {
            String subtag = subtags[i];
            if (privUseVarFound) {
                bldr.append(subtag);
            } else if (i > 0 && isVariant(subtag) && !singletonFound && !privateFound) {
                bldr.append(subtag);
            } else if (i > 0 && isRegion(subtag) && !singletonFound && !privateFound) {
                bldr.append(canonicalizeRegion(subtag));
            } else if (i > 0 && isScript(subtag) && !singletonFound && !privateFound) {
                bldr.append(canonicalizeScript(subtag));
            // If subtag is not 2 letter, 4 letter, or variant
            // under the right conditions, then it should be lower-case
            } else {
                if (isPrivateusePrefix(subtag)) {
                    privateFound = true;
                } else if (isExtensionSingleton(subtag)) {
                    singletonFound = true;
                } else if (subtag.equals(PRIVUSE_VARIANT_PREFIX)) {
                    privUseVarFound = true;
                }
                bldr.append(subtag.toLowerCase(Locale.ROOT));
            }
            if (i != subtags.length-1) {
                bldr.append("-");
            }
        }
        return bldr.substring(0);
    }

    public static LanguageTag parseLocale(BaseLocale baseLocale, LocaleExtensions localeExtensions) {

        String language = EMPTY_SUBTAG;
        String script = EMPTY_SUBTAG;
        String region = EMPTY_SUBTAG;

        String baseLanguage = baseLocale.getLanguage();
        String baseScript = baseLocale.getScript();
        String baseRegion = baseLocale.getRegion();
        String baseVariant = baseLocale.getVariant();

        boolean hasSubtag = false;

        String privuseVar = null;   // store ill-formed variant subtags

        if (isLanguage(baseLanguage)) {
            // Convert a deprecated language code to its new code
            baseLanguage = switch (baseLanguage) {
                case "iw" -> "he";
                case "ji" -> "yi";
                case "in" -> "id";
                default -> baseLanguage;
            };
            language = baseLanguage;
        }

        if (isScript(baseScript)) {
            script = canonicalizeScript(baseScript);
            hasSubtag = true;
        }

        if (isRegion(baseRegion)) {
            region = canonicalizeRegion(baseRegion);
            hasSubtag = true;
        }

        // Special handling for no_NO_NY - use nn_NO for language tag
        if (language.equals("no") && region.equals("NO") && baseVariant.equals("NY")) {
            language = "nn";
            baseVariant = EMPTY_SUBTAG;
        }

        List<String> variants = null;
        if (!baseVariant.isEmpty()) {
            StringTokenIterator varitr = new StringTokenIterator(baseVariant, BaseLocale.SEP);
            while (!varitr.isDone()) {
                String var = varitr.current();
                if (!isVariant(var)) {
                    break;
                }
                if (variants == null) {
                    variants = new ArrayList<>();
                }
                variants.add(var);  // Do not canonicalize!
                varitr.next();
            }
            if (variants != null) {
                hasSubtag = true;
            }
            if (!varitr.isDone()) {
                // ill-formed variant subtags
                StringJoiner sj = new StringJoiner(SEP);
                while (!varitr.isDone()) {
                    String prvv = varitr.current();
                    if (!isPrivateuseSubtag(prvv)) {
                        // cannot use private use subtag - truncated
                        break;
                    }
                    sj.add(prvv);
                    varitr.next();
                }
                if (sj.length() > 0) {
                    privuseVar = sj.toString();
                }
            }
        }

        List<String> extensions = null;
        String privateuse = null;

        if (localeExtensions != null) {
            Set<Character> locextKeys = localeExtensions.getKeys();
            for (Character locextKey : locextKeys) {
                Extension ext = localeExtensions.getExtension(locextKey);
                if (isPrivateusePrefixChar(locextKey)) {
                    privateuse = ext.getValue();
                } else {
                    if (extensions == null) {
                        extensions = new ArrayList<>();
                    }
                    extensions.add(locextKey.toString() + SEP + ext.getValue());
                }
            }
        }

        if (extensions != null) {
            hasSubtag = true;
        }

        // append ill-formed variant subtags to private use
        if (privuseVar != null) {
            if (privateuse == null) {
                privateuse = PRIVUSE_VARIANT_PREFIX + SEP + privuseVar;
            } else {
                privateuse = privateuse + SEP + PRIVUSE_VARIANT_PREFIX
                             + SEP + privuseVar.replace(BaseLocale.SEP, SEP);
            }
        }

        if (language.isEmpty() && (hasSubtag || privateuse == null)) {
            // use lang "und" when 1) no language is available AND
            // 2) any of other subtags other than private use are available or
            // no private use tag is available
            language = UNDETERMINED;
        }

        privateuse = privateuse == null ? EMPTY_SUBTAG : privateuse;
        extensions = extensions == null ? EMPTY_SUBTAGS :
                Collections.unmodifiableList(extensions);
        variants = variants == null ? EMPTY_SUBTAGS :
                Collections.unmodifiableList(variants);

        // extlangs always empty for locale parse
        return new LanguageTag(language, script, region, privateuse, EMPTY_SUBTAGS, variants, extensions);
    }

    //
    // Language subtag syntax checking methods
    //

    public static boolean isLanguage(String s) {
        // language      = 2*3ALPHA            ; shortest ISO 639 code
        //                 ["-" extlang]       ; sometimes followed by
        //                                     ;   extended language subtags
        //               / 4ALPHA              ; or reserved for future use
        //               / 5*8ALPHA            ; or registered language subtag
        int len = s.length();
        return (len >= 2) && (len <= 8) && LocaleUtils.isAlphaString(s);
    }

    public static boolean isExtlang(String s) {
        // extlang       = 3ALPHA              ; selected ISO 639 codes
        //                 *2("-" 3ALPHA)      ; permanently reserved
        return (s.length() == 3) && LocaleUtils.isAlphaString(s);
    }

    public static boolean isScript(String s) {
        // script        = 4ALPHA              ; ISO 15924 code
        return (s.length() == 4) && LocaleUtils.isAlphaString(s);
    }

    public static boolean isRegion(String s) {
        // region        = 2ALPHA              ; ISO 3166-1 code
        //               / 3DIGIT              ; UN M.49 code
        return ((s.length() == 2) && LocaleUtils.isAlphaString(s))
                || ((s.length() == 3) && LocaleUtils.isNumericString(s));
    }

    public static boolean isVariant(String s) {
        // variant       = 5*8alphanum         ; registered variants
        //               / (DIGIT 3alphanum)
        int len = s.length();
        if (len >= 5 && len <= 8) {
            return LocaleUtils.isAlphaNumericString(s);
        }
        if (len == 4) {
            return LocaleUtils.isNumeric(s.charAt(0))
                    && LocaleUtils.isAlphaNumeric(s.charAt(1))
                    && LocaleUtils.isAlphaNumeric(s.charAt(2))
                    && LocaleUtils.isAlphaNumeric(s.charAt(3));
        }
        return false;
    }

    public static boolean isExtensionSingleton(String s) {
        // singleton     = DIGIT               ; 0 - 9
        //               / %x41-57             ; A - W
        //               / %x59-5A             ; Y - Z
        //               / %x61-77             ; a - w
        //               / %x79-7A             ; y - z

        return (s.length() == 1)
                && LocaleUtils.isAlphaString(s)
                && !LocaleUtils.caseIgnoreMatch(PRIVATEUSE, s);
    }

    public static boolean isExtensionSingletonChar(char c) {
        return isExtensionSingleton(String.valueOf(c));
    }

    public static boolean isExtensionSubtag(String s) {
        // extension     = singleton 1*("-" (2*8alphanum))
        int len = s.length();
        return (len >= 2) && (len <= 8) && LocaleUtils.isAlphaNumericString(s);
    }

    public static boolean isPrivateusePrefix(String s) {
        // privateuse    = "x" 1*("-" (1*8alphanum))
        return (s.length() == 1)
                && LocaleUtils.caseIgnoreMatch(PRIVATEUSE, s);
    }

    public static boolean isPrivateusePrefixChar(char c) {
        return (LocaleUtils.caseIgnoreMatch(PRIVATEUSE, String.valueOf(c)));
    }

    public static boolean isPrivateuseSubtag(String s) {
        // privateuse    = "x" 1*("-" (1*8alphanum))
        int len = s.length();
        return (len >= 1) && (len <= 8) && LocaleUtils.isAlphaNumericString(s);
    }

    //
    // Language subtag canonicalization methods
    //

    public static String canonicalizeLanguage(String s) {
        return LocaleUtils.toLowerString(s);
    }

    public static String canonicalizeExtlang(String s) {
        return LocaleUtils.toLowerString(s);
    }

    public static String canonicalizeScript(String s) {
        return LocaleUtils.toTitleString(s);
    }

    public static String canonicalizeRegion(String s) {
        return LocaleUtils.toUpperString(s);
    }

    public static String canonicalizeVariant(String s) {
        return LocaleUtils.toLowerString(s);
    }

    public static String canonicalizeExtension(String s) {
        return LocaleUtils.toLowerString(s);
    }

    public static String canonicalizeExtensionSingleton(String s) {
        return LocaleUtils.toLowerString(s);
    }

    public static String canonicalizeExtensionSubtag(String s) {
        return LocaleUtils.toLowerString(s);
    }

    public static String canonicalizePrivateuse(String s) {
        return LocaleUtils.toLowerString(s);
    }

    public static String canonicalizePrivateuseSubtag(String s) {
        return LocaleUtils.toLowerString(s);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (!language.isEmpty()) {
            sb.append(language);

            for (String extlang : extlangs) {
                sb.append(SEP).append(extlang);
            }

            if (!script.isEmpty()) {
                sb.append(SEP).append(script);
            }

            if (!region.isEmpty()) {
                sb.append(SEP).append(region);
            }

            for (String variant : variants) {
                sb.append(SEP).append(variant);
            }

            for (String extension : extensions) {
                sb.append(SEP).append(extension);
            }
        }
        if (!privateuse.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(SEP);
            }
            sb.append(privateuse);
        }

        return sb.toString();
    }
}
