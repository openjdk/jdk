// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 2009-2013, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package jdk.internal.icu.impl;

import java.util.MissingResourceException;


/**
 * Utilities for mapping between old and new language, country, and other
 * locale ID related names.
 */
public class LocaleIDs {

    /**
     * Returns a list of all 2-letter country codes defined in ISO 3166.
     * Can be used to create Locales.
     * @stable ICU 3.0
     */
    public static String[] getISOCountries() {
        return _countries.clone();
    }

    /**
     * Returns a list of all 2-letter language codes defined in ISO 639
     * plus additional 3-letter codes determined to be useful for locale generation as
     * defined by Unicode CLDR.
     * Can be used to create Locales.
     * [NOTE:  ISO 639 is not a stable standard-- some languages' codes have changed.
     * The list this function returns includes both the new and the old codes for the
     * languages whose codes have changed.]
     * @stable ICU 3.0
     */
    public static String[] getISOLanguages() {
        return _languages.clone();
    }

    /**
     * Returns a three-letter abbreviation for the provided country.  If the provided
     * country is empty, returns the empty string.  Otherwise, returns
     * an uppercase ISO 3166 3-letter country code.
     * @exception MissingResourceException Throws MissingResourceException if the
     * three-letter country abbreviation is not available for this locale.
     * @stable ICU 3.0
     */
    public static String getISO3Country(String country){

        int offset = findIndex(_countries, country);
        if(offset>=0){
            return _countries3[offset];
        }else{
            offset = findIndex(_obsoleteCountries, country);
            if(offset>=0){
                return _obsoleteCountries3[offset];
            }
        }
        return "";
    }
    /**
     * Returns a three-letter abbreviation for the language.  If language is
     * empty, returns the empty string.  Otherwise, returns
     * a lowercase ISO 639-2/T language code.
     * The ISO 639-2 language codes can be found on-line at
     *   <a href="ftp://dkuug.dk/i18n/iso-639-2.txt"><code>ftp://dkuug.dk/i18n/iso-639-2.txt</code></a>
     * @exception MissingResourceException Throws MissingResourceException if the
     * three-letter language abbreviation is not available for this locale.
     * @stable ICU 3.0
     */
    public static String getISO3Language(String language) {

        int offset = findIndex(_languages, language);
        if(offset>=0){
            return _languages3[offset];
        } else {
            offset = findIndex(_obsoleteLanguages, language);
            if (offset >= 0) {
                return _obsoleteLanguages3[offset];
            }
        }
        return "";
    }

    public static String threeToTwoLetterLanguage(String lang) {

        /* convert 3 character code to 2 character code if possible *CWB*/
        int offset = findIndex(_languages3, lang);
        if (offset >= 0) {
            return _languages[offset];
        }

        offset = findIndex(_obsoleteLanguages3, lang);
        if (offset >= 0) {
            return _obsoleteLanguages[offset];
        }

        return null;
    }

    public static String threeToTwoLetterRegion(String region) {

        /* convert 3 character code to 2 character code if possible *CWB*/
        int offset = findIndex(_countries3, region);
        if (offset >= 0) {
            return _countries[offset];
        }

        offset = findIndex(_obsoleteCountries3, region);
        if (offset >= 0) {
            return _obsoleteCountries[offset];
        }

        return null;
    }

    /**
     * linear search of the string array. the arrays are unfortunately ordered by the
     * two-letter target code, not the three-letter search code, which seems backwards.
     */
    private static int findIndex(String[] array, String target){
        for (int i = 0; i < array.length; i++) {
            if (target.equals(array[i])) {
                return i;
            }
        }
        return -1;
    }


    /**
     * Tables used in normalizing portions of the id.
     */
    /* tables updated per http://lcweb.loc.gov/standards/iso639-2/
       to include the revisions up to 2001/7/27 *CWB*/
    /* Subsequent hand addition of selected languages */
    /* The 3 character codes are the terminology codes like RFC 3066.
       This is compatible with prior ICU codes */
    /* "in" "iw" "ji" "jw" & "sh" have been withdrawn but are still in
       the table but now at the end of the table because
       3 character codes are duplicates.  This avoids bad searches
       going from 3 to 2 character codes.*/
    /* The range qaa-qtz is reserved for local use. */

    /* This list MUST be in sorted order, and MUST contain the two-letter codes
    if one exists otherwise use the three letter code */
    private static final String[] _languages = {
        "aa",  "ab",  "ace", "ach", "ada", "ady", "ae",  "aeb",
        "af",  "afh", "agq", "ain", "ak",  "akk", "akz", "ale",
        "aln", "alt", "am",  "an",  "ang", "anp", "ar",  "arc",
        "arn", "aro", "arp", "arq", "ars", "arw", "ary", "arz", "as",
        "asa", "ase", "ast", "av",  "avk", "awa", "ay",  "az",
        "ba",  "bal", "ban", "bar", "bas", "bax", "bbc", "bbj",
        "be",  "bej", "bem", "bew", "bez", "bfd", "bfq", "bg",
        "bgc", "bgn", "bho", "bi",  "bik", "bin", "bjn", "bkm", "bla",
        "bm",  "bn",  "bo",  "bpy", "bqi", "br",  "bra", "brh",
        "brx", "bs",  "bss", "bua", "bug", "bum", "byn", "byv",
        "ca",  "cad", "car", "cay", "cch", "ccp", "ce",  "ceb", "cgg",
        "ch",  "chb", "chg", "chk", "chm", "chn", "cho", "chp",
        "chr", "chy", "ckb", "co",  "cop", "cps", "cr",  "crh",
        "cs",  "csb", "cu",  "cv",  "cy",
        "da",  "dak", "dar", "dav", "day", "de",  "del", "den",
        "dgr", "din", "dje", "doi", "dra", "dsb", "dua", "dum",
        "dv",  "dyo", "dyu", "dz",  "dzg",
        "ebu", "ee",  "efi", "egy", "eka", "el",  "elx", "en",
        "enm", "eo",  "es",  "et",  "eu",  "ewo",
        "fa",  "fan", "fat", "ff",  "fi",  "fil", "fiu", "fj",
        "fo",  "fon", "fr",  "frm", "fro", "frr", "frs", "fur",
        "fy",
        "ga",  "gaa", "gay", "gba", "gd",  "gem", "gez", "gil",
        "gl",  "gmh", "gn",  "goh", "gon", "gor", "got", "grb",
        "grc", "gsw", "gu",  "guz", "gv",  "gwi",
        "ha",  "hai", "haw", "he",  "hi",  "hil", "him", "hit",
        "hmn", "ho",  "hr",  "hsb", "ht",  "hu",  "hup", "hy",
        "hz",
        "ia",  "iba", "ibb", "id",  "ie",  "ig",  "ii",  "ijo",
        "ik",  "ilo", "inc", "ine", "inh", "io",  "ira", "iro",
        "is",  "it",  "iu",
        "ja",  "jbo", "jgo", "jmc", "jpr", "jrb", "jv",
        "ka",  "kaa", "kab", "kac", "kaj", "kam", "kar", "kaw",
        "kbd", "kbl", "kcg", "kde", "kea", "kfo", "kg",  "kha",
        "khi", "kho", "khq", "ki",  "kj",  "kk",  "kkj", "kl",
        "kln", "km",  "kmb", "kn",  "ko",  "kok", "kos", "kpe",
        "kr",  "krc", "krl", "kro", "kru", "ks",  "ksb", "ksf",
        "ksh", "ku",  "kum", "kut", "kv",  "kw",  "ky",
        "la",  "lad", "lag", "lah", "lam", "lb",  "lez", "lg",
        "li",  "lkt", "ln",  "lo",  "lol", "loz", "lt",  "lu",
        "lua", "lui", "lun", "luo", "lus", "luy", "lv",
        "mad", "maf", "mag", "mai", "mak", "man", "map", "mas",
        "mde", "mdf", "mdr", "men", "mer", "mfe", "mg",  "mga",
        "mgh", "mgo", "mh",  "mi",  "mic", "min", "mis", "mk",
        "mkh", "ml",  "mn",  "mnc", "mni", "mno", "moh",
        "mos", "mr",  "ms",  "mt",  "mua", "mul", "mun", "mus",
        "mwl", "mwr", "my",  "mye", "myn", "myv",
        "na",  "nah", "nai", "nap", "naq", "nb",  "nd",  "nds",
        "ne",  "new", "ng",  "nia", "nic", "niu", "nl",  "nmg",
        "nn",  "nnh", "no",  "nog", "non", "nqo", "nr",  "nso",
        "nub", "nus", "nv",  "nwc", "ny",  "nym", "nyn", "nyo",
        "nzi",
        "oc",  "oj",  "om",  "or",  "os",  "osa", "ota", "oto",
        "pa",  "paa", "pag", "pal", "pam", "pap", "pau", "peo",
        "phi", "phn", "pi",  "pl",  "pon", "pra", "pro", "ps",
        "pt",
        "qu",
        "raj", "rap", "rar", "rm",  "rn",  "ro",  "roa", "rof",
        "rom", "ru",  "rup", "rw",  "rwk",
        "sa",  "sad", "sah", "sai", "sal", "sam", "saq", "sas",
        "sat", "sba", "sbp", "sc",  "scn", "sco", "sd",  "se",
        "see", "seh", "sel", "sem", "ses", "sg",  "sga", "sgn",
        "shi", "shn", "shu", "si",  "sid", "sio", "sit",
        "sk",  "sl",  "sla", "sm",  "sma", "smi", "smj", "smn",
        "sms", "sn",  "snk", "so",  "sog", "son", "sq",  "sr",
        "srn", "srr", "ss",  "ssa", "ssy", "st",  "su",  "suk",
        "sus", "sux", "sv",  "sw",  "swb", "syc", "syr",
        "ta",  "tai", "te",  "tem", "teo", "ter", "tet", "tg",
        "th",  "ti",  "tig", "tiv", "tk",  "tkl", "tlh",
        "tli", "tmh", "tn",  "to",  "tog", "tpi", "tr",  "trv",
        "ts",  "tsi", "tt",  "tum", "tup", "tut", "tvl", "tw",
        "twq", "ty",  "tyv", "tzm",
        "udm", "ug",  "uga", "uk",  "umb", "und", "ur",  "uz",
        "vai", "ve",  "vi",  "vo",  "vot", "vun",
        "wa",  "wae", "wak", "wal", "war", "was", "wen", "wo",
        "xal", "xh",  "xog",
        "yao", "yap", "yav", "ybb", "yi",  "yo",  "ypk", "yue",
        "za",  "zap", "zbl", "zen", "zh",  "znd", "zu",  "zun",
        "zxx", "zza" };

    private static final String[] _replacementLanguages = {
        "id", "he", "yi", "jv", "ro", "sr", /* replacement language codes */
    };

    private static final String[] _obsoleteLanguages = {
        "in", "iw", "ji", "jw", "mo", "sh", /* obsolete language codes */
    };

    /* This list MUST contain a three-letter code for every two-letter code in the
    list above, and they MUST ne in the same order (i.e., the same language must
    be in the same place in both lists)! */
    private static final String[] _languages3 = {
        "aar", "abk", "ace", "ach", "ada", "ady", "ave", "aeb",
        "afr", "afh", "agq", "ain", "aka", "akk", "akz", "ale",
        "aln", "alt", "amh", "arg", "ang", "anp", "ara", "arc",
        "arn", "aro", "arp", "arq", "ars", "arw", "ary", "arz", "asm",
        "asa", "ase", "ast", "ava", "avk", "awa", "aym", "aze",
        "bak", "bal", "ban", "bar", "bas", "bax", "bbc", "bbj",
        "bel", "bej", "bem", "bew", "bez", "bfd", "bfq", "bul",
        "bgc", "bgn", "bho", "bis", "bik", "bin", "bjn", "bkm", "bla",
        "bam", "ben", "bod", "bpy", "bqi", "bre", "bra", "brh",
        "brx", "bos", "bss", "bua", "bug", "bum", "byn", "byv",
        "cat", "cad", "car", "cay", "cch", "ccp", "che", "ceb", "cgg",
        "cha", "chb", "chg", "chk", "chm", "chn", "cho", "chp",
        "chr", "chy", "ckb", "cos", "cop", "cps", "cre", "crh",
        "ces", "csb", "chu", "chv", "cym",
        "dan", "dak", "dar", "dav", "day", "deu", "del", "den",
        "dgr", "din", "dje", "doi", "dra", "dsb", "dua", "dum",
        "div", "dyo", "dyu", "dzo", "dzg",
        "ebu", "ewe", "efi", "egy", "eka", "ell", "elx", "eng",
        "enm", "epo", "spa", "est", "eus", "ewo",
        "fas", "fan", "fat", "ful", "fin", "fil", "fiu", "fij",
        "fao", "fon", "fra", "frm", "fro", "frr", "frs", "fur",
        "fry",
        "gle", "gaa", "gay", "gba", "gla", "gem", "gez", "gil",
        "glg", "gmh", "grn", "goh", "gon", "gor", "got", "grb",
        "grc", "gsw", "guj", "guz", "glv", "gwi",
        "hau", "hai", "haw", "heb", "hin", "hil", "him", "hit",
        "hmn", "hmo", "hrv", "hsb", "hat", "hun", "hup", "hye",
        "her",
        "ina", "iba", "ibb", "ind", "ile", "ibo", "iii", "ijo",
        "ipk", "ilo", "inc", "ine", "inh", "ido", "ira", "iro",
        "isl", "ita", "iku",
        "jpn", "jbo", "jgo", "jmc", "jpr", "jrb", "jav",
        "kat", "kaa", "kab", "kac", "kaj", "kam", "kar", "kaw",
        "kbd", "kbl", "kcg", "kde", "kea", "kfo", "kon", "kha",
        "khi", "kho", "khq", "kik", "kua", "kaz", "kkj", "kal",
        "kln", "khm", "kmb", "kan", "kor", "kok", "kos", "kpe",
        "kau", "krc", "krl", "kro", "kru", "kas", "ksb", "ksf",
        "ksh", "kur", "kum", "kut", "kom", "cor", "kir",
        "lat", "lad", "lag", "lah", "lam", "ltz", "lez", "lug",
        "lim", "lkt", "lin", "lao", "lol", "loz", "lit", "lub",
        "lua", "lui", "lun", "luo", "lus", "luy", "lav",
        "mad", "maf", "mag", "mai", "mak", "man", "map", "mas",
        "mde", "mdf", "mdr", "men", "mer", "mfe", "mlg", "mga",
        "mgh", "mgo", "mah", "mri", "mic", "min", "mis", "mkd",
        "mkh", "mal", "mon", "mnc", "mni", "mno", "moh",
        "mos", "mar", "msa", "mlt", "mua", "mul", "mun", "mus",
        "mwl", "mwr", "mya", "mye", "myn", "myv",
        "nau", "nah", "nai", "nap", "naq", "nob", "nde", "nds",
        "nep", "new", "ndo", "nia", "nic", "niu", "nld", "nmg",
        "nno", "nnh", "nor", "nog", "non", "nqo", "nbl", "nso",
        "nub", "nus", "nav", "nwc", "nya", "nym", "nyn", "nyo",
        "nzi",
        "oci", "oji", "orm", "ori", "oss", "osa", "ota", "oto",
        "pan", "paa", "pag", "pal", "pam", "pap", "pau", "peo",
        "phi", "phn", "pli", "pol", "pon", "pra", "pro", "pus",
        "por",
        "que",
        "raj", "rap", "rar", "roh", "run", "ron", "roa", "rof",
        "rom", "rus", "rup", "kin", "rwk",
        "san", "sad", "sah", "sai", "sal", "sam", "saq", "sas",
        "sat", "sba", "sbp", "srd", "scn", "sco", "snd", "sme",
        "see", "seh", "sel", "sem", "ses", "sag", "sga", "sgn",
        "shi", "shn", "shu", "sin", "sid", "sio", "sit",
        "slk", "slv", "sla", "smo", "sma", "smi", "smj", "smn",
        "sms", "sna", "snk", "som", "sog", "son", "sqi", "srp",
        "srn", "srr", "ssw", "ssa", "ssy", "sot", "sun", "suk",
        "sus", "sux", "swe", "swa", "swb", "syc", "syr",
        "tam", "tai", "tel", "tem", "teo", "ter", "tet", "tgk",
        "tha", "tir", "tig", "tiv", "tuk", "tkl", "tlh",
        "tli", "tmh", "tsn", "ton", "tog", "tpi", "tur", "trv",
        "tso", "tsi", "tat", "tum", "tup", "tut", "tvl", "twi",
        "twq", "tah", "tyv", "tzm",
        "udm", "uig", "uga", "ukr", "umb", "und", "urd", "uzb",
        "vai", "ven", "vie", "vol", "vot", "vun",
        "wln", "wae", "wak", "wal", "war", "was", "wen", "wol",
        "xal", "xho", "xog",
        "yao", "yap", "yav", "ybb", "yid", "yor", "ypk", "yue",
        "zha", "zap", "zbl", "zen", "zho", "znd", "zul", "zun",
        "zxx", "zza" };

    private static final String[] _obsoleteLanguages3 = {
        /* "in",  "iw",  "ji",  "jw",  "mo",  "sh", */
        "ind", "heb", "yid", "jaw", "ron", "srp"
    };

    /* ZR(ZAR) is now CD(COD) and FX(FXX) is PS(PSE) as per
       http://www.evertype.com/standards/iso3166/iso3166-1-en.html
       added new codes keeping the old ones for compatibility
       updated to include 1999/12/03 revisions *CWB*/

    /* RO(ROM) is now RO(ROU) according to
       http://www.iso.org/iso/en/prods-services/iso3166ma/03updates-on-iso-3166/nlv3e-rou.html
    */
    /* This list MUST be in sorted order, and MUST contain only two-letter codes! */
    private static final String[] _countries = {
        "AD",  "AE",  "AF",  "AG",  "AI",  "AL",  "AM",
        "AO",  "AQ",  "AR",  "AS",  "AT",  "AU",  "AW",  "AX",  "AZ",
        "BA",  "BB",  "BD",  "BE",  "BF",  "BG",  "BH",  "BI",
        "BJ",  "BL",  "BM",  "BN",  "BO",  "BQ",  "BR",  "BS",  "BT",  "BV",
        "BW",  "BY",  "BZ",  "CA",  "CC",  "CD",  "CF",  "CG",
        "CH",  "CI",  "CK",  "CL",  "CM",  "CN",  "CO",  "CR",
        "CU",  "CV",  "CW",  "CX",  "CY",  "CZ",  "DE",  "DG",  "DJ",  "DK",
        "DM",  "DO",  "DZ",  "EA",  "EC",  "EE",  "EG",  "EH",  "ER",
        "ES",  "ET",  "FI",  "FJ",  "FK",  "FM",  "FO",  "FR",
        "GA",  "GB",  "GD",  "GE",  "GF",  "GG",  "GH",  "GI",  "GL",
        "GM",  "GN",  "GP",  "GQ",  "GR",  "GS",  "GT",  "GU",
        "GW",  "GY",  "HK",  "HM",  "HN",  "HR",  "HT",  "HU",
        "IC",  "ID",  "IE",  "IL",  "IM",  "IN",  "IO",  "IQ",  "IR",  "IS",
        "IT",  "JE",  "JM",  "JO",  "JP",  "KE",  "KG",  "KH",  "KI",
        "KM",  "KN",  "KP",  "KR",  "KW",  "KY",  "KZ",  "LA",
        "LB",  "LC",  "LI",  "LK",  "LR",  "LS",  "LT",  "LU",
        "LV",  "LY",  "MA",  "MC",  "MD",  "ME",  "MF",  "MG",  "MH",  "MK",
        "ML",  "MM",  "MN",  "MO",  "MP",  "MQ",  "MR",  "MS",
        "MT",  "MU",  "MV",  "MW",  "MX",  "MY",  "MZ",  "NA",
        "NC",  "NE",  "NF",  "NG",  "NI",  "NL",  "NO",  "NP",
        "NR",  "NU",  "NZ",  "OM",  "PA",  "PE",  "PF",  "PG",
        "PH",  "PK",  "PL",  "PM",  "PN",  "PR",  "PS",  "PT",
        "PW",  "PY",  "QA",  "RE",  "RO",  "RS",  "RU",  "RW",  "SA",
        "SB",  "SC",  "SD",  "SE",  "SG",  "SH",  "SI",  "SJ",
        "SK",  "SL",  "SM",  "SN",  "SO",  "SR",  "SS",  "ST",  "SV",
        "SX",  "SY",  "SZ",  "TC",  "TD",  "TF",  "TG",  "TH",  "TJ",
        "TK",  "TL",  "TM",  "TN",  "TO",  "TR",  "TT",  "TV",
        "TW",  "TZ",  "UA",  "UG",  "UM",  "US",  "UY",  "UZ",
        "VA",  "VC",  "VE",  "VG",  "VI",  "VN",  "VU",  "WF",
        "WS",  "XK",  "YE",  "YT",  "ZA",  "ZM",  "ZW" };

    private static final String[] _deprecatedCountries = {
        "AN", "BU", "CS", "DD", "DY", "FX", "HV", "NH", "RH", "SU", "TP", "UK", "VD", "YD", "YU", "ZR" /* deprecated country list */
    };

    private static final String[] _replacementCountries = {
    /*  "AN", "BU", "CS", "DD", "DY", "FX", "HV", "NH", "RH", "SU", "TP", "UK", "VD", "YD", "YU", "ZR" */
        "CW", "MM", "RS", "DE", "BJ", "FR", "BF", "VU", "ZW", "RU", "TL", "GB", "VN", "YE", "RS", "CD"  /* replacement country codes */
    };

    /* this table is used for three letter codes */
    private static final String[] _obsoleteCountries = {
        "AN",  "BU", "CS", "FX", "RO", "SU", "TP", "YD", "YU", "ZR",   /* obsolete country codes */
    };

    /* This list MUST contain a three-letter code for every two-letter code in
    the above list, and they MUST be listed in the same order! */
    private static final String[] _countries3 = {
        /*  "AD",  "AE",  "AF",  "AG",  "AI",  "AL",  "AM",      */
        "AND", "ARE", "AFG", "ATG", "AIA", "ALB", "ARM",
    /*  "AO",  "AQ",  "AR",  "AS",  "AT",  "AU",  "AW",  "AX",  "AZ",     */
        "AGO", "ATA", "ARG", "ASM", "AUT", "AUS", "ABW", "ALA", "AZE",
    /*  "BA",  "BB",  "BD",  "BE",  "BF",  "BG",  "BH",  "BI",     */
        "BIH", "BRB", "BGD", "BEL", "BFA", "BGR", "BHR", "BDI",
    /*  "BJ",  "BL",  "BM",  "BN",  "BO",  "BQ",  "BR",  "BS",  "BT",  "BV",     */
        "BEN", "BLM", "BMU", "BRN", "BOL", "BES", "BRA", "BHS", "BTN", "BVT",
    /*  "BW",  "BY",  "BZ",  "CA",  "CC",  "CD",  "CF",  "CG",     */
        "BWA", "BLR", "BLZ", "CAN", "CCK", "COD", "CAF", "COG",
    /*  "CH",  "CI",  "CK",  "CL",  "CM",  "CN",  "CO",  "CR",     */
        "CHE", "CIV", "COK", "CHL", "CMR", "CHN", "COL", "CRI",
    /*  "CU",  "CV",  "CW",  "CX",  "CY",  "CZ",  "DE",  "DG",  "DJ",  "DK",     */
        "CUB", "CPV", "CUW", "CXR", "CYP", "CZE", "DEU", "DGA", "DJI", "DNK",
    /*  "DM",  "DO",  "DZ",  "EA",  "EC",  "EE",  "EG",  "EH",  "ER",     */
        "DMA", "DOM", "DZA", "XEA", "ECU", "EST", "EGY", "ESH", "ERI",
    /*  "ES",  "ET",  "FI",  "FJ",  "FK",  "FM",  "FO",  "FR",     */
        "ESP", "ETH", "FIN", "FJI", "FLK", "FSM", "FRO", "FRA",
    /*  "GA",  "GB",  "GD",  "GE",  "GF",  "GG",  "GH",  "GI",  "GL",     */
        "GAB", "GBR", "GRD", "GEO", "GUF", "GGY", "GHA", "GIB", "GRL",
    /*  "GM",  "GN",  "GP",  "GQ",  "GR",  "GS",  "GT",  "GU",     */
        "GMB", "GIN", "GLP", "GNQ", "GRC", "SGS", "GTM", "GUM",
    /*  "GW",  "GY",  "HK",  "HM",  "HN",  "HR",  "HT",  "HU",     */
        "GNB", "GUY", "HKG", "HMD", "HND", "HRV", "HTI", "HUN",
    /*  "IC",  "ID",  "IE",  "IL",  "IM",  "IN",  "IO",  "IQ",  "IR",  "IS" */
        "XIC", "IDN", "IRL", "ISR", "IMN", "IND", "IOT", "IRQ", "IRN", "ISL",
    /*  "IT",  "JE",  "JM",  "JO",  "JP",  "KE",  "KG",  "KH",  "KI",     */
        "ITA", "JEY", "JAM", "JOR", "JPN", "KEN", "KGZ", "KHM", "KIR",
    /*  "KM",  "KN",  "KP",  "KR",  "KW",  "KY",  "KZ",  "LA",     */
        "COM", "KNA", "PRK", "KOR", "KWT", "CYM", "KAZ", "LAO",
    /*  "LB",  "LC",  "LI",  "LK",  "LR",  "LS",  "LT",  "LU",     */
        "LBN", "LCA", "LIE", "LKA", "LBR", "LSO", "LTU", "LUX",
    /*  "LV",  "LY",  "MA",  "MC",  "MD",  "ME",  "MF",  "MG",  "MH",  "MK",     */
        "LVA", "LBY", "MAR", "MCO", "MDA", "MNE", "MAF", "MDG", "MHL", "MKD",
    /*  "ML",  "MM",  "MN",  "MO",  "MP",  "MQ",  "MR",  "MS",     */
        "MLI", "MMR", "MNG", "MAC", "MNP", "MTQ", "MRT", "MSR",
    /*  "MT",  "MU",  "MV",  "MW",  "MX",  "MY",  "MZ",  "NA",     */
        "MLT", "MUS", "MDV", "MWI", "MEX", "MYS", "MOZ", "NAM",
    /*  "NC",  "NE",  "NF",  "NG",  "NI",  "NL",  "NO",  "NP",     */
        "NCL", "NER", "NFK", "NGA", "NIC", "NLD", "NOR", "NPL",
    /*  "NR",  "NU",  "NZ",  "OM",  "PA",  "PE",  "PF",  "PG",     */
        "NRU", "NIU", "NZL", "OMN", "PAN", "PER", "PYF", "PNG",
    /*  "PH",  "PK",  "PL",  "PM",  "PN",  "PR",  "PS",  "PT",     */
        "PHL", "PAK", "POL", "SPM", "PCN", "PRI", "PSE", "PRT",
    /*  "PW",  "PY",  "QA",  "RE",  "RO",  "RS",  "RU",  "RW",  "SA",     */
        "PLW", "PRY", "QAT", "REU", "ROU", "SRB", "RUS", "RWA", "SAU",
    /*  "SB",  "SC",  "SD",  "SE",  "SG",  "SH",  "SI",  "SJ",     */
        "SLB", "SYC", "SDN", "SWE", "SGP", "SHN", "SVN", "SJM",
    /*  "SK",  "SL",  "SM",  "SN",  "SO",  "SR",  "SS",  "ST",  "SV",     */
        "SVK", "SLE", "SMR", "SEN", "SOM", "SUR", "SSD", "STP", "SLV",
    /*  "SX",  "SY",  "SZ",  "TC",  "TD",  "TF",  "TG",  "TH",  "TJ",     */
        "SXM", "SYR", "SWZ", "TCA", "TCD", "ATF", "TGO", "THA", "TJK",
    /*  "TK",  "TL",  "TM",  "TN",  "TO",  "TR",  "TT",  "TV",     */
        "TKL", "TLS", "TKM", "TUN", "TON", "TUR", "TTO", "TUV",
    /*  "TW",  "TZ",  "UA",  "UG",  "UM",  "US",  "UY",  "UZ",     */
        "TWN", "TZA", "UKR", "UGA", "UMI", "USA", "URY", "UZB",
    /*  "VA",  "VC",  "VE",  "VG",  "VI",  "VN",  "VU",  "WF",     */
        "VAT", "VCT", "VEN", "VGB", "VIR", "VNM", "VUT", "WLF",
    /*  "WS",  "XK",  "YE",  "YT",  "ZA",  "ZM",  "ZW",          */
        "WSM", "XKK", "YEM", "MYT", "ZAF", "ZMB", "ZWE" };

    private static final String[] _obsoleteCountries3 = {
    /*  "AN",  "BU",  "CS",  "FX",  "RO",  "SU",  "TP",  "YD",  "YU",  "ZR" */
        "ANT", "BUR", "SCG", "FXX", "ROM", "SUN", "TMP", "YMD", "YUG", "ZAR",
    };


    public static String getCurrentCountryID(String oldID){
        int offset = findIndex(_deprecatedCountries, oldID);
        if (offset >= 0) {
            return _replacementCountries[offset];
        }
        return oldID;
    }

    public static String getCurrentLanguageID(String oldID){
        int offset = findIndex(_obsoleteLanguages, oldID);
        if (offset >= 0) {
            return _replacementLanguages[offset];
        }
        return oldID;
    }


}
