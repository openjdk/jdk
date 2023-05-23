// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/**
 *******************************************************************************
 * Copyright (C) 2001-2016 International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package jdk.internal.icu.lang;

import java.util.BitSet;
import java.util.Locale;

import jdk.internal.icu.impl.UCharacterProperty;
import jdk.internal.icu.util.ULocale;

/**
 * Constants for ISO 15924 script codes, and related functions.
 *
 * <p>The current set of script code constants supports at least all scripts
 * that are encoded in the version of Unicode which ICU currently supports.
 * The names of the constants are usually derived from the
 * Unicode script property value aliases.
 * See UAX #24 Unicode Script Property (http://www.unicode.org/reports/tr24/)
 * and http://www.unicode.org/Public/UCD/latest/ucd/PropertyValueAliases.txt .
 *
 * <p>In addition, constants for many ISO 15924 script codes
 * are included, for use with language tags, CLDR data, and similar.
 * Some of those codes are not used in the Unicode Character Database (UCD).
 * For example, there are no characters that have a UCD script property value of
 * Hans or Hant. All Han ideographs have the Hani script property value in Unicode.
 *
 * <p>Private-use codes Qaaa..Qabx are not included, except as used in the UCD or in CLDR.
 *
 * <p>Starting with ICU 55, script codes are only added when their scripts
 * have been or will certainly be encoded in Unicode,
 * and have been assigned Unicode script property value aliases,
 * to ensure that their script names are stable and match the names of the constants.
 * Script codes like Latf and Aran that are not subject to separate encoding
 * may be added at any time.
 *
 * @stable ICU 2.4
 */
@SuppressWarnings("deprecation")
public final class UScript {
    /**
     * Invalid code
     * @stable ICU 2.4
     */
    public static final int INVALID_CODE = -1;
    /**
     * Common
     * @stable ICU 2.4
     */
    public static final int COMMON       =  0;  /* Zyyy */
    /**
     * Inherited
     * @stable ICU 2.4
     */
    public static final int INHERITED    =  1;  /* Zinh */ /* "Code for inherited script", for non-spacing combining marks; also Qaai */
    /**
     * Arabic
     * @stable ICU 2.4
     */
    public static final int ARABIC       =  2;  /* Arab */
    /**
     * Armenian
     * @stable ICU 2.4
     */
    public static final int ARMENIAN     =  3;  /* Armn */
    /**
     * Bengali
     * @stable ICU 2.4
     */
    public static final int BENGALI      =  4;  /* Beng */
    /**
     * Bopomofo
     * @stable ICU 2.4
     */
    public static final int BOPOMOFO     =  5;  /* Bopo */
    /**
     * Cherokee
     * @stable ICU 2.4
     */
    public static final int CHEROKEE     =  6;  /* Cher */
    /**
     * Coptic
     * @stable ICU 2.4
     */
    public static final int COPTIC       =  7;  /* Qaac */
    /**
     * Cyrillic
     * @stable ICU 2.4
     */
    public static final int CYRILLIC     =  8;  /* Cyrl (Cyrs) */
    /**
     * Deseret
     * @stable ICU 2.4
     */
    public static final int DESERET      =  9;  /* Dsrt */
    /**
     * Devanagari
     * @stable ICU 2.4
     */
    public static final int DEVANAGARI   = 10;  /* Deva */
    /**
     * Ethiopic
     * @stable ICU 2.4
     */
    public static final int ETHIOPIC     = 11;  /* Ethi */
    /**
     * Georgian
     * @stable ICU 2.4
     */
    public static final int GEORGIAN     = 12;  /* Geor (Geon; Geoa) */
    /**
     * Gothic
     * @stable ICU 2.4
     */
    public static final int GOTHIC       = 13;  /* Goth */
    /**
     * Greek
     * @stable ICU 2.4
     */
    public static final int GREEK        = 14;  /* Grek */
    /**
     * Gujarati
     * @stable ICU 2.4
     */
    public static final int GUJARATI     = 15;  /* Gujr */
    /**
     * Gurmukhi
     * @stable ICU 2.4
     */
    public static final int GURMUKHI     = 16;  /* Guru */
    /**
     * Han
     * @stable ICU 2.4
     */
    public static final int HAN          = 17;  /* Hani */
    /**
     * Hangul
     * @stable ICU 2.4
     */
    public static final int HANGUL       = 18;  /* Hang */
    /**
     * Hebrew
     * @stable ICU 2.4
     */
    public static final int HEBREW       = 19;  /* Hebr */
    /**
     * Hiragana
     * @stable ICU 2.4
     */
    public static final int HIRAGANA     = 20;  /* Hira */
    /**
     * Kannada
     * @stable ICU 2.4
     */
    public static final int KANNADA      = 21;  /* Knda */
    /**
     * Katakana
     * @stable ICU 2.4
     */
    public static final int KATAKANA     = 22;  /* Kana */
    /**
     * Khmer
     * @stable ICU 2.4
     */
    public static final int KHMER        = 23;  /* Khmr */
    /**
     * Lao
     * @stable ICU 2.4
     */
    public static final int LAO          = 24;  /* Laoo */
    /**
     * Latin
     * @stable ICU 2.4
     */
    public static final int LATIN        = 25;  /* Latn (Latf; Latg) */
    /**
     * Malayalam
     * @stable ICU 2.4
     */
    public static final int MALAYALAM    = 26;  /* Mlym */
    /**
     * Mangolian
     * @stable ICU 2.4
     */
    public static final int MONGOLIAN    = 27;  /* Mong */
    /**
     * Myammar
     * @stable ICU 2.4
     */
    public static final int MYANMAR      = 28;  /* Mymr */
    /**
     * Ogham
     * @stable ICU 2.4
     */
    public static final int OGHAM        = 29;  /* Ogam */
    /**
     * Old Itallic
     * @stable ICU 2.4
     */
    public static final int OLD_ITALIC   = 30;  /* Ital */
    /**
     * Oriya
     * @stable ICU 2.4
     */
    public static final int ORIYA        = 31;  /* Orya */
    /**
     * Runic
     * @stable ICU 2.4
     */
    public static final int RUNIC        = 32;  /* Runr */
    /**
     * Sinhala
     * @stable ICU 2.4
     */
    public static final int SINHALA      = 33;  /* Sinh */
    /**
     * Syriac
     * @stable ICU 2.4
     */
    public static final int SYRIAC       = 34;  /* Syrc (Syrj; Syrn; Syre) */
    /**
     * Tamil
     * @stable ICU 2.4
     */
    public static final int TAMIL        = 35;  /* Taml */
    /**
     * Telugu
     * @stable ICU 2.4
     */
    public static final int TELUGU       = 36;  /* Telu */
    /**
     * Thana
     * @stable ICU 2.4
     */
    public static final int THAANA       = 37;  /* Thaa */
    /**
     * Thai
     * @stable ICU 2.4
     */
    public static final int THAI         = 38;  /* Thai */
    /**
     * Tibetan
     * @stable ICU 2.4
     */
    public static final int TIBETAN      = 39;  /* Tibt */
    /**
     * Unified Canadian Aboriginal Symbols
     * @stable ICU 2.6
     */
    public static final int CANADIAN_ABORIGINAL = 40;  /* Cans */
    /**
     * Unified Canadian Aboriginal Symbols (alias)
     * @stable ICU 2.4
     */
    public static final int UCAS         = CANADIAN_ABORIGINAL;  /* Cans */
    /**
     * Yi syllables
     * @stable ICU 2.4
     */
    public static final int YI           = 41;  /* Yiii */
    /**
     * Tagalog
     * @stable ICU 2.4
     */
    public static final int TAGALOG      = 42;  /* Tglg */
    /**
     * Hanunooo
     * @stable ICU 2.4
     */
    public static final int HANUNOO      = 43;  /* Hano */
    /**
     * Buhid
     * @stable ICU 2.4
     */
    public static final int BUHID        = 44;  /* Buhd */
    /**
     * Tagbanwa
     * @stable ICU 2.4
     */
    public static final int TAGBANWA     = 45;  /* Tagb */
    /**
     * Braille
     * Script in Unicode 4
     * @stable ICU 2.6
     *
     */
    public static final int BRAILLE      = 46;  /* Brai */
    /**
     * Cypriot
     * Script in Unicode 4
     * @stable ICU 2.6
     *
     */
    public static final int CYPRIOT              = 47;  /* Cprt */
    /**
     * Limbu
     * Script in Unicode 4
     * @stable ICU 2.6
     *
     */
    public static final int LIMBU                = 48;  /* Limb */
    /**
     * Linear B
     * Script in Unicode 4
     * @stable ICU 2.6
     *
     */
    public static final int LINEAR_B     = 49;  /* Linb */
    /**
     * Osmanya
     * Script in Unicode 4
     * @stable ICU 2.6
     *
     */
    public static final int OSMANYA              = 50;  /* Osma */
    /**
     * Shavian
     * Script in Unicode 4
     * @stable ICU 2.6
     *
     */
    public static final int SHAVIAN              = 51;  /* Shaw */
    /**
     * Tai Le
     * Script in Unicode 4
     * @stable ICU 2.6
     *
     */
    public static final int TAI_LE               = 52;  /* Tale */
    /**
     * Ugaritic
     * Script in Unicode 4
     * @stable ICU 2.6
     *
     */
    public static final int UGARITIC     = 53;  /* Ugar */
    /**
     * Script in Unicode 4.0.1
     * @stable ICU 3.0
     */
    public static final int KATAKANA_OR_HIRAGANA = 54;  /*Hrkt */

    /**
     * Script in Unicode 4.1
     * @stable ICU 3.4
     */
    public static final int BUGINESE = 55;           /* Bugi */
    /**
     * Script in Unicode 4.1
     * @stable ICU 3.4
     */
    public static final int GLAGOLITIC = 56;         /* Glag */
    /**
     * Script in Unicode 4.1
     * @stable ICU 3.4
     */
    public static final int KHAROSHTHI = 57;         /* Khar */
    /**
     * Script in Unicode 4.1
     * @stable ICU 3.4
     */
    public static final int SYLOTI_NAGRI = 58;       /* Sylo */
    /**
     * Script in Unicode 4.1
     * @stable ICU 3.4
     */
    public static final int NEW_TAI_LUE = 59;        /* Talu */
    /**
     * Script in Unicode 4.1
     * @stable ICU 3.4
     */
    public static final int TIFINAGH = 60;           /* Tfng */
    /**
     * Script in Unicode 4.1
     * @stable ICU 3.4
     */
    public static final int OLD_PERSIAN = 61;        /* Xpeo */


    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int BALINESE                      = 62; /* Bali */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int BATAK                         = 63; /* Batk */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int BLISSYMBOLS                   = 64; /* Blis */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int BRAHMI                        = 65; /* Brah */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int CHAM                          = 66; /* Cham */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int CIRTH                         = 67; /* Cirt */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int OLD_CHURCH_SLAVONIC_CYRILLIC  = 68; /* Cyrs */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int DEMOTIC_EGYPTIAN              = 69; /* Egyd */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int HIERATIC_EGYPTIAN             = 70; /* Egyh */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int EGYPTIAN_HIEROGLYPHS          = 71; /* Egyp */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int KHUTSURI                      = 72; /* Geok */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int SIMPLIFIED_HAN                = 73; /* Hans */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int TRADITIONAL_HAN               = 74; /* Hant */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int PAHAWH_HMONG                  = 75; /* Hmng */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int OLD_HUNGARIAN                 = 76; /* Hung */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int HARAPPAN_INDUS                = 77; /* Inds */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int JAVANESE                      = 78; /* Java */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int KAYAH_LI                      = 79; /* Kali */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int LATIN_FRAKTUR                 = 80; /* Latf */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int LATIN_GAELIC                  = 81; /* Latg */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int LEPCHA                        = 82; /* Lepc */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int LINEAR_A                      = 83; /* Lina */
    /**
     * ISO 15924 script code
     * @stable ICU 4.6
     */
    public static final int MANDAIC                       = 84; /* Mand */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int MANDAEAN                      = MANDAIC;
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int MAYAN_HIEROGLYPHS             = 85; /* Maya */
    /**
     * ISO 15924 script code
     * @stable ICU 4.6
     */
    public static final int MEROITIC_HIEROGLYPHS          = 86; /* Mero */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int MEROITIC                      = MEROITIC_HIEROGLYPHS;
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int NKO                           = 87; /* Nkoo */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int ORKHON                        = 88; /* Orkh */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int OLD_PERMIC                    = 89; /* Perm */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int PHAGS_PA                      = 90; /* Phag */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int PHOENICIAN                    = 91; /* Phnx */
    /**
     * ISO 15924 script code
     * @stable ICU 52
     */
    public static final int MIAO                          = 92; /* Plrd */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int PHONETIC_POLLARD              = MIAO;
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int RONGORONGO                    = 93; /* Roro */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int SARATI                        = 94; /* Sara */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int ESTRANGELO_SYRIAC             = 95; /* Syre */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int WESTERN_SYRIAC                = 96; /* Syrj */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int EASTERN_SYRIAC                = 97; /* Syrn */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int TENGWAR                       = 98; /* Teng */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int VAI                           = 99; /* Vaii */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int VISIBLE_SPEECH                = 100;/* Visp */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int CUNEIFORM                     = 101;/* Xsux */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int UNWRITTEN_LANGUAGES           = 102;/* Zxxx */
    /**
     * ISO 15924 script code
     * @stable ICU 3.6
     */
    public static final int UNKNOWN                       = 103;/* Zzzz */ /* Unknown="Code for uncoded script", for unassigned code points */

    /**
     * ISO 15924 script code
     * @stable ICU 3.8
     */
    public static final int CARIAN                        = 104;/* Cari */
    /**
     * ISO 15924 script code
     * @stable ICU 3.8
     */
    public static final int JAPANESE                      = 105;/* Jpan */
    /**
     * ISO 15924 script code
     * @stable ICU 3.8
     */
    public static final int LANNA                         = 106;/* Lana */
    /**
     * ISO 15924 script code
     * @stable ICU 3.8
     */
    public static final int LYCIAN                        = 107;/* Lyci */
    /**
     * ISO 15924 script code
     * @stable ICU 3.8
     */
    public static final int LYDIAN                        = 108;/* Lydi */
    /**
     * ISO 15924 script code
     * @stable ICU 3.8
     */
    public static final int OL_CHIKI                      = 109;/* Olck */
    /**
     * ISO 15924 script code
     * @stable ICU 3.8
     */
    public static final int REJANG                        = 110;/* Rjng */
    /**
     * ISO 15924 script code
     * @stable ICU 3.8
     */
    public static final int SAURASHTRA                    = 111;/* Saur */
    /**
     * ISO 15924 script code for Sutton SignWriting
     * @stable ICU 3.8
     */
    public static final int SIGN_WRITING                  = 112;/* Sgnw */
    /**
     * ISO 15924 script code
     * @stable ICU 3.8
     */
    public static final int SUNDANESE                     = 113;/* Sund */
    /**
     * ISO 15924 script code
     * @stable ICU 3.8
     */
    public static final int MOON                          = 114;/* Moon */
    /**
     * ISO 15924 script code
     * @stable ICU 3.8
     */
    public static final int MEITEI_MAYEK                  = 115;/* Mtei */

    /**
     * ISO 15924 script code
     * @stable ICU 4.0
     */
    public static final int IMPERIAL_ARAMAIC              = 116;/* Armi */

    /**
     * ISO 15924 script code
     * @stable ICU 4.0
     */
    public static final int AVESTAN                       = 117;/* Avst */

    /**
     * ISO 15924 script code
     * @stable ICU 4.0
     */
    public static final int CHAKMA                        = 118;/* Cakm */

    /**
     * ISO 15924 script code
     * @stable ICU 4.0
     */
    public static final int KOREAN                        = 119;/* Kore */

    /**
     * ISO 15924 script code
     * @stable ICU 4.0
     */
    public static final int KAITHI                        = 120;/* Kthi */

    /**
     * ISO 15924 script code
     * @stable ICU 4.0
     */
    public static final int MANICHAEAN                    = 121;/* Mani */

    /**
     * ISO 15924 script code
     * @stable ICU 4.0
     */
    public static final int INSCRIPTIONAL_PAHLAVI         = 122;/* Phli */

    /**
     * ISO 15924 script code
     * @stable ICU 4.0
     */
    public static final int PSALTER_PAHLAVI               = 123;/* Phlp */

    /**
     * ISO 15924 script code
     * @stable ICU 4.0
     */
    public static final int BOOK_PAHLAVI                  = 124;/* Phlv */

    /**
     * ISO 15924 script code
     * @stable ICU 4.0
     */
    public static final int INSCRIPTIONAL_PARTHIAN        = 125;/* Prti */

    /**
     * ISO 15924 script code
     * @stable ICU 4.0
     */
    public static final int SAMARITAN                     = 126;/* Samr */

    /**
     * ISO 15924 script code
     * @stable ICU 4.0
     */
    public static final int TAI_VIET                      = 127;/* Tavt */

    /**
     * ISO 15924 script code
     * @stable ICU 4.0
     */
    public static final int MATHEMATICAL_NOTATION         = 128;/* Zmth */

    /**
     * ISO 15924 script code
     * @stable ICU 4.0
     */
    public static final int SYMBOLS                       = 129;/* Zsym */

    /**
     * ISO 15924 script code
     * @stable ICU 4.4
     */
    public static final int BAMUM                         = 130;/* Bamu */
    /**
     * ISO 15924 script code
     * @stable ICU 4.4
     */
    public static final int LISU                          = 131;/* Lisu */
    /**
     * ISO 15924 script code
     * @stable ICU 4.4
     */
    public static final int NAKHI_GEBA                    = 132;/* Nkgb */
    /**
     * ISO 15924 script code
     * @stable ICU 4.4
     */
    public static final int OLD_SOUTH_ARABIAN             = 133;/* Sarb */

    /**
     * ISO 15924 script code
     * @stable ICU 4.6
     */
    public static final int BASSA_VAH                     = 134;/* Bass */
    /**
     * ISO 15924 script code
     * @stable ICU 54
     */
    public static final int DUPLOYAN                      = 135;/* Dupl */
    /**
     * Typo, use DUPLOYAN
     * @deprecated ICU 54
     */
    @Deprecated
    public static final int DUPLOYAN_SHORTAND             = DUPLOYAN;
    /**
     * ISO 15924 script code
     * @stable ICU 4.6
     */
    public static final int ELBASAN                       = 136;/* Elba */
    /**
     * ISO 15924 script code
     * @stable ICU 4.6
     */
    public static final int GRANTHA                       = 137;/* Gran */
    /**
     * ISO 15924 script code
     * @stable ICU 4.6
     */
    public static final int KPELLE                        = 138;/* Kpel */
    /**
     * ISO 15924 script code
     * @stable ICU 4.6
     */
    public static final int LOMA                          = 139;/* Loma */
    /**
     * Mende Kikakui
     * ISO 15924 script code
     * @stable ICU 4.6
     */
    public static final int MENDE                         = 140;/* Mend */
    /**
     * ISO 15924 script code
     * @stable ICU 4.6
     */
    public static final int MEROITIC_CURSIVE              = 141;/* Merc */
    /**
     * ISO 15924 script code
     * @stable ICU 4.6
     */
    public static final int OLD_NORTH_ARABIAN             = 142;/* Narb */
    /**
     * ISO 15924 script code
     * @stable ICU 4.6
     */
    public static final int NABATAEAN                     = 143;/* Nbat */
    /**
     * ISO 15924 script code
     * @stable ICU 4.6
     */
    public static final int PALMYRENE                     = 144;/* Palm */
    /**
     * ISO 15924 script code
     * @stable ICU 54
     */
    public static final int KHUDAWADI                     = 145;/* Sind */
    /**
     * ISO 15924 script code
     * @stable ICU 4.6
     */
    public static final int SINDHI = KHUDAWADI;
    /**
     * ISO 15924 script code
     * @stable ICU 4.6
     */
    public static final int WARANG_CITI                   = 146;/* Wara */

    /**
     * ISO 15924 script code
     * @stable ICU 4.8
     */
    public static final int AFAKA = 147;/* Afak */
    /**
     * ISO 15924 script code
     * @stable ICU 4.8
     */
    public static final int JURCHEN = 148;/* Jurc */
    /**
     * ISO 15924 script code
     * @stable ICU 4.8
     */
    public static final int MRO = 149;/* Mroo */
    /**
     * ISO 15924 script code
     * @stable ICU 4.8
     */
    public static final int NUSHU = 150;/* Nshu */
    /**
     * ISO 15924 script code
     * @stable ICU 4.8
     */
    public static final int SHARADA = 151;/* Shrd */
    /**
     * ISO 15924 script code
     * @stable ICU 4.8
     */
    public static final int SORA_SOMPENG = 152;/* Sora */
    /**
     * ISO 15924 script code
     * @stable ICU 4.8
     */
    public static final int TAKRI = 153;/* Takr */
    /**
     * ISO 15924 script code
     * @stable ICU 4.8
     */
    public static final int TANGUT = 154;/* Tang */
    /**
     * ISO 15924 script code
     * @stable ICU 4.8
     */
    public static final int WOLEAI = 155;/* Wole */

    /**
     * ISO 15924 script code
     * @stable ICU 49
     */
    public static final int ANATOLIAN_HIEROGLYPHS = 156;/* Hluw */
    /**
     * ISO 15924 script code
     * @stable ICU 49
     */
    public static final int KHOJKI = 157;/* Khoj */
    /**
     * ISO 15924 script code
     * @stable ICU 49
     */
    public static final int TIRHUTA = 158;/* Tirh */
    /**
     * ISO 15924 script code
     * @stable ICU 52
     */
    public static final int CAUCASIAN_ALBANIAN = 159; /* Aghb */
    /**
     * ISO 15924 script code
     * @stable ICU 52
     */
    public static final int MAHAJANI = 160; /* Mahj */

    /**
     * ISO 15924 script code
     * @stable ICU 54
     */
    public static final int AHOM = 161; /* Ahom */
    /**
     * ISO 15924 script code
     * @stable ICU 54
     */
    public static final int HATRAN = 162; /* Hatr */
    /**
     * ISO 15924 script code
     * @stable ICU 54
     */
    public static final int MODI = 163; /* Modi */
    /**
     * ISO 15924 script code
     * @stable ICU 54
     */
    public static final int MULTANI = 164; /* Mult */
    /**
     * ISO 15924 script code
     * @stable ICU 54
     */
    public static final int PAU_CIN_HAU = 165; /* Pauc */
    /**
     * ISO 15924 script code
     * @stable ICU 54
     */
    public static final int SIDDHAM = 166; /* Sidd */

    /**
     * ISO 15924 script code
     * @stable ICU 58
     */
    public static final int ADLAM = 167; /* Adlm */
    /**
     * ISO 15924 script code
     * @stable ICU 58
     */
    public static final int BHAIKSUKI = 168; /* Bhks */
    /**
     * ISO 15924 script code
     * @stable ICU 58
     */
    public static final int MARCHEN = 169; /* Marc */
    /**
     * ISO 15924 script code
     * @stable ICU 58
     */
    public static final int NEWA = 170; /* Newa */
    /**
     * ISO 15924 script code
     * @stable ICU 58
     */
    public static final int OSAGE = 171; /* Osge */

    /**
     * ISO 15924 script code
     * @stable ICU 58
     */
    public static final int HAN_WITH_BOPOMOFO = 172; /* Hanb */
    /**
     * ISO 15924 script code
     * @stable ICU 58
     */
    public static final int JAMO = 173; /* Jamo */
    /**
     * ISO 15924 script code
     * @stable ICU 58
     */
    public static final int SYMBOLS_EMOJI = 174; /* Zsye */

    /**
     * ISO 15924 script code
     * @stable ICU 60
     */
    public static final int MASARAM_GONDI = 175; /* Gonm */
    /**
     * ISO 15924 script code
     * @stable ICU 60
     */
    public static final int SOYOMBO = 176; /* Soyo */
    /**
     * ISO 15924 script code
     * @stable ICU 60
     */
    public static final int ZANABAZAR_SQUARE = 177; /* Zanb */

    /**
     * ISO 15924 script code
     * @stable ICU 62
     */
    public static final int DOGRA = 178; /* Dogr */
    /** @stable ICU 62 */
    public static final int GUNJALA_GONDI = 179; /* Gong */
    /** @stable ICU 62 */
    public static final int MAKASAR = 180; /* Maka */
    /** @stable ICU 62 */
    public static final int MEDEFAIDRIN = 181; /* Medf */
    /** @stable ICU 62 */
    public static final int HANIFI_ROHINGYA = 182; /* Rohg */
    /** @stable ICU 62 */
    public static final int SOGDIAN = 183; /* Sogd */
    /** @stable ICU 62 */
    public static final int OLD_SOGDIAN = 184; /* Sogo */

    /** @stable ICU 64 */
    public static final int ELYMAIC = 185; /* Elym */
    /** @stable ICU 64 */
    public static final int NYIAKENG_PUACHUE_HMONG = 186; /* Hmnp */
    /** @stable ICU 64 */
    public static final int NANDINAGARI = 187; /* Nand */
    /** @stable ICU 64 */
    public static final int WANCHO = 188; /* Wcho */

    /** @stable ICU 66 */
    public static final int CHORASMIAN = 189; /* Chrs */
    /** @stable ICU 66 */
    public static final int DIVES_AKURU = 190; /* Diak */
    /** @stable ICU 66 */
    public static final int KHITAN_SMALL_SCRIPT = 191; /* Kits */
    /** @stable ICU 66 */
    public static final int YEZIDI = 192; /* Yezi */

    /** @stable ICU 70 */
    public static final int CYPRO_MINOAN = 193; /* Cpmn */
    /** @stable ICU 70 */
    public static final int OLD_UYGHUR = 194; /* Ougr */
    /** @stable ICU 70 */
    public static final int TANGSA = 195; /* Tnsa */
    /** @stable ICU 70 */
    public static final int TOTO = 196; /* Toto */
    /** @stable ICU 70 */
    public static final int VITHKUQI = 197; /* Vith */

    /** @stable ICU 72 */
    public static final int KAWI = 198; /* Kawi */
    /** @stable ICU 72 */
    public static final int NAG_MUNDARI = 199; /* Nagm */

    /**
     * One more than the highest normal UScript code.
     * The highest value is available via UCharacter.getIntPropertyMaxValue(UProperty.SCRIPT).
     *
     * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
     */
    @Deprecated
    public static final int CODE_LIMIT   = 200;

    private static int[] getCodesFromLocale(ULocale locale) {
        // Multi-script languages, equivalent to the LocaleScript data
        // that we used to load from locale resource bundles.
        String lang = locale.getLanguage();
        if(lang.equals("ja")) {
            return new int[] { UScript.KATAKANA, UScript.HIRAGANA, UScript.HAN };
        }
        if(lang.equals("ko")) {
            return new int[] { UScript.HANGUL, UScript.HAN };
        }
        String script = locale.getScript();
        if(lang.equals("zh") && script.equals("Hant")) {
            return new int[] { UScript.HAN, UScript.BOPOMOFO };
        }
        // Explicit script code.
        if(script.length() != 0) {
            int scriptCode = UScript.getCodeFromName(script);
            if(scriptCode != UScript.INVALID_CODE) {
                if(scriptCode == UScript.SIMPLIFIED_HAN || scriptCode == UScript.TRADITIONAL_HAN) {
                    scriptCode = UScript.HAN;
                }
                return new int[] { scriptCode };
            }
        }
        return null;
    }

    /**
     * Helper function to find the code from locale.
     * @param locale The locale.
     */
    private static int[] findCodeFromLocale(ULocale locale) {
        int[] result = getCodesFromLocale(locale);
        if(result != null) {
            return result;
        }
        ULocale likely = ULocale.addLikelySubtags(locale);
        return getCodesFromLocale(likely);
    }

    /**
     * Gets a script codes associated with the given locale or ISO 15924 abbreviation or name.
     * Returns MALAYAM given "Malayam" OR "Mlym".
     * Returns LATIN given "en" OR "en_US"
     * @param locale Locale
     * @return The script codes array. null if the the code cannot be found.
     * @stable ICU 2.4
     */
    public static final int[] getCode(Locale locale){
        return findCodeFromLocale(ULocale.forLocale(locale));
    }
    /**
     * Gets a script codes associated with the given locale or ISO 15924 abbreviation or name.
     * Returns MALAYAM given "Malayam" OR "Mlym".
     * Returns LATIN given "en" OR "en_US"
     * @param locale ULocale
     * @return The script codes array. null if the the code cannot be found.
     * @stable ICU 3.0
     */
    public static final int[] getCode(ULocale locale){
        return findCodeFromLocale(locale);
    }
    /**
     * Gets the script codes associated with the given locale or ISO 15924 abbreviation or name.
     * Returns MALAYAM given "Malayam" OR "Mlym".
     * Returns LATIN given "en" OR "en_US"
     *
     * <p>Note: To search by short or long script alias only, use
     * {@link #getCodeFromName(String)} instead.
     * That does a fast lookup with no access of the locale data.
     *
     * @param nameOrAbbrOrLocale name of the script or ISO 15924 code or locale
     * @return The script codes array. null if the the code cannot be found.
     * @stable ICU 2.4
     */
    public static final int[] getCode(String nameOrAbbrOrLocale) {
        boolean triedCode = false;
        int lastSepPos = nameOrAbbrOrLocale.indexOf('_');
        if (lastSepPos < 0) {
            lastSepPos = nameOrAbbrOrLocale.indexOf('-');
        }
        // Favor interpretation of nameOrAbbrOrLocale as a script alias if either
        // 1. nameOrAbbrOrLocale does not contain -/_. Handles Han, Mro, Nko, etc.
        // 2. The last instance of -/_ is at offset 3, and the portion after that is
        //    longer than 4 characters (i.e. not a script or region code). This handles
        //    Old_Hungarian, Old_Italic, etc. ("old" is a valid language code)
        // 3. The last instance of -/_ is at offset 7, and the portion after that is
        //    3 characters. This handles New_Tai_Lue ("new" is a valid language code).
        if ( lastSepPos < 0
                || (lastSepPos == 3 && nameOrAbbrOrLocale.length() > 8)
                || (lastSepPos == 7 && nameOrAbbrOrLocale.length() == 11) ) {
            int propNum = UCharacter.getPropertyValueEnumNoThrow(UProperty.SCRIPT, nameOrAbbrOrLocale);
            if (propNum != UProperty.UNDEFINED) {
                return new int[] {propNum};
            }
            triedCode = true;
        }
        int[] scripts = findCodeFromLocale(new ULocale(nameOrAbbrOrLocale));
        if (scripts != null) {
            return scripts;
        }
        if (!triedCode) {
            int propNum = UCharacter.getPropertyValueEnumNoThrow(UProperty.SCRIPT, nameOrAbbrOrLocale);
            if (propNum != UProperty.UNDEFINED) {
                return new int[] {propNum};
            }
        }
        return null;
    }

    /**
     * Returns the script code associated with the given Unicode script property alias
     * (name or abbreviation).
     * Short aliases are ISO 15924 script codes.
     * Returns MALAYAM given "Malayam" OR "Mlym".
     *
     * @param nameOrAbbr name of the script or ISO 15924 code
     * @return The script code value, or INVALID_CODE if the code cannot be found.
     * @stable ICU 54
     */
    public static final int getCodeFromName(String nameOrAbbr) {
        int propNum = UCharacter.getPropertyValueEnumNoThrow(UProperty.SCRIPT, nameOrAbbr);
        return propNum == UProperty.UNDEFINED ? INVALID_CODE : propNum;
    }

    /**
     * Gets the script code associated with the given codepoint.
     * Returns UScript.MALAYAM given 0x0D02
     * @param codepoint UChar32 codepoint
     * @return The script code
     * @stable ICU 2.4
     */
    public static final int getScript(int codepoint){
        if (codepoint >= UCharacter.MIN_VALUE & codepoint <= UCharacter.MAX_VALUE) {
            int scriptX=UCharacterProperty.INSTANCE.getAdditional(codepoint, 0)&UCharacterProperty.SCRIPT_X_MASK;
            int codeOrIndex=UCharacterProperty.mergeScriptCodeOrIndex(scriptX);
            if(scriptX<UCharacterProperty.SCRIPT_X_WITH_COMMON) {
                return codeOrIndex;
            } else if(scriptX<UCharacterProperty.SCRIPT_X_WITH_INHERITED) {
                return UScript.COMMON;
            } else if(scriptX<UCharacterProperty.SCRIPT_X_WITH_OTHER) {
                return UScript.INHERITED;
            } else {
                return UCharacterProperty.INSTANCE.m_scriptExtensions_[codeOrIndex];
            }
        }else{
            throw new IllegalArgumentException(Integer.toString(codepoint));
        }
    }

    /**
     * Do the Script_Extensions of code point c contain script sc?
     * If c does not have explicit Script_Extensions, then this tests whether
     * c has the Script property value sc.
     *
     * <p>Some characters are commonly used in multiple scripts.
     * For more information, see UAX #24: http://www.unicode.org/reports/tr24/.
     *
     * @param c code point
     * @param sc script code
     * @return true if sc is in Script_Extensions(c)
     * @stable ICU 49
     */
    public static final boolean hasScript(int c, int sc) {
        int scriptX=UCharacterProperty.INSTANCE.getAdditional(c, 0)&UCharacterProperty.SCRIPT_X_MASK;
        int codeOrIndex=UCharacterProperty.mergeScriptCodeOrIndex(scriptX);
        if(scriptX<UCharacterProperty.SCRIPT_X_WITH_COMMON) {
            return sc==codeOrIndex;
        }

        char[] scriptExtensions=UCharacterProperty.INSTANCE.m_scriptExtensions_;
        int scx=codeOrIndex;  // index into scriptExtensions
        if(scriptX>=UCharacterProperty.SCRIPT_X_WITH_OTHER) {
            scx=scriptExtensions[scx+1];
        }
        if(sc>0x7fff) {
            // Guard against bogus input that would
            // make us go past the Script_Extensions terminator.
            return false;
        }
        while(sc>scriptExtensions[scx]) {
            ++scx;
        }
        return sc==(scriptExtensions[scx]&0x7fff);
    }

    /**
     * Sets code point c's Script_Extensions as script code integers into the output BitSet.
     * <ul>
     * <li>If c does have Script_Extensions, then the return value is
     * the negative number of Script_Extensions codes (= -set.cardinality());
     * in this case, the Script property value
     * (normally Common or Inherited) is not included in the set.
     * <li>If c does not have Script_Extensions, then the one Script code is put into the set
     * and also returned.
     * <li>If c is not a valid code point, then the one {@link #UNKNOWN} code is put into the set
     * and also returned.
     * </ul>
     * In other words, if the return value is non-negative, it is c's single Script code
     * and the set contains exactly this Script code.
     * If the return value is -n, then the set contains c's n&gt;=2 Script_Extensions script codes.
     *
     * <p>Some characters are commonly used in multiple scripts.
     * For more information, see UAX #24: http://www.unicode.org/reports/tr24/.
     *
     * @param c code point
     * @param set set of script code integers; will be cleared, then bits are set
     *            corresponding to c's Script_Extensions
     * @return negative number of script codes in c's Script_Extensions,
     *         or the non-negative single Script value
     * @stable ICU 49
     */
    public static final int getScriptExtensions(int c, BitSet set) {
        set.clear();
        int scriptX=UCharacterProperty.INSTANCE.getAdditional(c, 0)&UCharacterProperty.SCRIPT_X_MASK;
        int codeOrIndex=UCharacterProperty.mergeScriptCodeOrIndex(scriptX);
        if(scriptX<UCharacterProperty.SCRIPT_X_WITH_COMMON) {
            set.set(codeOrIndex);
            return codeOrIndex;
        }

        char[] scriptExtensions=UCharacterProperty.INSTANCE.m_scriptExtensions_;
        int scx=codeOrIndex;  // index into scriptExtensions
        if(scriptX>=UCharacterProperty.SCRIPT_X_WITH_OTHER) {
            scx=scriptExtensions[scx+1];
        }
        int length=0;
        int sx;
        do {
            sx=scriptExtensions[scx++];
            set.set(sx&0x7fff);
            ++length;
        } while(sx<0x8000);
        // length==set.cardinality()
        return -length;
    }

    /**
     * Returns the long Unicode script name, if there is one.
     * Otherwise returns the 4-letter ISO 15924 script code.
     * Returns "Malayam" given MALAYALAM.
     *
     * @param scriptCode int script code
     * @return long script name as given in PropertyValueAliases.txt, or the 4-letter code
     * @throws IllegalArgumentException if the script code is not valid
     * @stable ICU 2.4
     */
    public static final String getName(int scriptCode){
        return UCharacter.getPropertyValueName(UProperty.SCRIPT,
                scriptCode,
                UProperty.NameChoice.LONG);
    }

    /**
     * Returns the 4-letter ISO 15924 script code,
     * which is the same as the short Unicode script name if Unicode has names for the script.
     * Returns "Mlym" given MALAYALAM.
     *
     * @param scriptCode int script code
     * @return short script name (4-letter code)
     * @throws IllegalArgumentException if the script code is not valid
     * @stable ICU 2.4
     */
    public static final String getShortName(int scriptCode){
        return UCharacter.getPropertyValueName(UProperty.SCRIPT,
                scriptCode,
                UProperty.NameChoice.SHORT);
    }

    /**
     * Script metadata (script properties).
     * See http://unicode.org/cldr/trac/browser/trunk/common/properties/scriptMetadata.txt
     */
    private static final class ScriptMetadata {
        // 0 = NOT_ENCODED, no sample character, default false script properties.
        // Bits 20.. 0: sample character

        // Bits 23..21: usage
        private static final int UNKNOWN = 1 << 21;
        private static final int EXCLUSION = 2 << 21;
        private static final int LIMITED_USE = 3 << 21;
        // vate static final int ASPIRATIONAL = 4 << 21; -- not used any more since Unicode 10
        private static final int RECOMMENDED = 5 << 21;

        // Bits 31..24: Single-bit flags
        private static final int RTL = 1 << 24;
        private static final int LB_LETTERS = 1 << 25;
        private static final int CASED = 1 << 26;

        private static final int SCRIPT_PROPS[] = {
            // Begin copy-paste output from
            // tools/trunk/unicode/py/parsescriptmetadata.py
            // or from icu/trunk/source/common/uscript_props.cpp
            0x0040 | RECOMMENDED,  // Zyyy
            0x0308 | RECOMMENDED,  // Zinh
            0x0628 | RECOMMENDED | RTL,  // Arab
            0x0531 | RECOMMENDED | CASED,  // Armn
            0x0995 | RECOMMENDED,  // Beng
            0x3105 | RECOMMENDED | LB_LETTERS,  // Bopo
            0x13C4 | LIMITED_USE | CASED,  // Cher
            0x03E2 | EXCLUSION | CASED,  // Copt
            0x042F | RECOMMENDED | CASED,  // Cyrl
            0x10414 | EXCLUSION | CASED,  // Dsrt
            0x0905 | RECOMMENDED,  // Deva
            0x12A0 | RECOMMENDED,  // Ethi
            0x10D3 | RECOMMENDED,  // Geor
            0x10330 | EXCLUSION,  // Goth
            0x03A9 | RECOMMENDED | CASED,  // Grek
            0x0A95 | RECOMMENDED,  // Gujr
            0x0A15 | RECOMMENDED,  // Guru
            0x5B57 | RECOMMENDED | LB_LETTERS,  // Hani
            0xAC00 | RECOMMENDED,  // Hang
            0x05D0 | RECOMMENDED | RTL,  // Hebr
            0x304B | RECOMMENDED | LB_LETTERS,  // Hira
            0x0C95 | RECOMMENDED,  // Knda
            0x30AB | RECOMMENDED | LB_LETTERS,  // Kana
            0x1780 | RECOMMENDED | LB_LETTERS,  // Khmr
            0x0EA5 | RECOMMENDED | LB_LETTERS,  // Laoo
            0x004C | RECOMMENDED | CASED,  // Latn
            0x0D15 | RECOMMENDED,  // Mlym
            0x1826 | EXCLUSION,  // Mong
            0x1000 | RECOMMENDED | LB_LETTERS,  // Mymr
            0x168F | EXCLUSION,  // Ogam
            0x10300 | EXCLUSION,  // Ital
            0x0B15 | RECOMMENDED,  // Orya
            0x16A0 | EXCLUSION,  // Runr
            0x0D85 | RECOMMENDED,  // Sinh
            0x0710 | LIMITED_USE | RTL,  // Syrc
            0x0B95 | RECOMMENDED,  // Taml
            0x0C15 | RECOMMENDED,  // Telu
            0x078C | RECOMMENDED | RTL,  // Thaa
            0x0E17 | RECOMMENDED | LB_LETTERS,  // Thai
            0x0F40 | RECOMMENDED,  // Tibt
            0x14C0 | LIMITED_USE,  // Cans
            0xA288 | LIMITED_USE | LB_LETTERS,  // Yiii
            0x1703 | EXCLUSION,  // Tglg
            0x1723 | EXCLUSION,  // Hano
            0x1743 | EXCLUSION,  // Buhd
            0x1763 | EXCLUSION,  // Tagb
            0x280E | UNKNOWN,  // Brai
            0x10800 | EXCLUSION | RTL,  // Cprt
            0x1900 | LIMITED_USE,  // Limb
            0x10000 | EXCLUSION,  // Linb
            0x10480 | EXCLUSION,  // Osma
            0x10450 | EXCLUSION,  // Shaw
            0x1950 | LIMITED_USE | LB_LETTERS,  // Tale
            0x10380 | EXCLUSION,  // Ugar
            0,
            0x1A00 | EXCLUSION,  // Bugi
            0x2C00 | EXCLUSION | CASED,  // Glag
            0x10A00 | EXCLUSION | RTL,  // Khar
            0xA800 | LIMITED_USE,  // Sylo
            0x1980 | LIMITED_USE | LB_LETTERS,  // Talu
            0x2D30 | LIMITED_USE,  // Tfng
            0x103A0 | EXCLUSION,  // Xpeo
            0x1B05 | LIMITED_USE,  // Bali
            0x1BC0 | LIMITED_USE,  // Batk
            0,
            0x11005 | EXCLUSION,  // Brah
            0xAA00 | LIMITED_USE,  // Cham
            0,
            0,
            0,
            0,
            0x13153 | EXCLUSION,  // Egyp
            0,
            0x5B57 | RECOMMENDED | LB_LETTERS,  // Hans
            0x5B57 | RECOMMENDED | LB_LETTERS,  // Hant
            0x16B1C | EXCLUSION,  // Hmng
            0x10CA1 | EXCLUSION | RTL | CASED,  // Hung
            0,
            0xA984 | LIMITED_USE,  // Java
            0xA90A | LIMITED_USE,  // Kali
            0,
            0,
            0x1C00 | LIMITED_USE,  // Lepc
            0x10647 | EXCLUSION,  // Lina
            0x0840 | LIMITED_USE | RTL,  // Mand
            0,
            0x10980 | EXCLUSION | RTL,  // Mero
            0x07CA | LIMITED_USE | RTL,  // Nkoo
            0x10C00 | EXCLUSION | RTL,  // Orkh
            0x1036B | EXCLUSION,  // Perm
            0xA840 | EXCLUSION,  // Phag
            0x10900 | EXCLUSION | RTL,  // Phnx
            0x16F00 | LIMITED_USE,  // Plrd
            0,
            0,
            0,
            0,
            0,
            0,
            0xA549 | LIMITED_USE,  // Vaii
            0,
            0x12000 | EXCLUSION,  // Xsux
            0,
            0xFDD0 | UNKNOWN,  // Zzzz
            0x102A0 | EXCLUSION,  // Cari
            0x304B | RECOMMENDED | LB_LETTERS,  // Jpan
            0x1A20 | LIMITED_USE | LB_LETTERS,  // Lana
            0x10280 | EXCLUSION,  // Lyci
            0x10920 | EXCLUSION | RTL,  // Lydi
            0x1C5A | LIMITED_USE,  // Olck
            0xA930 | EXCLUSION,  // Rjng
            0xA882 | LIMITED_USE,  // Saur
            0x1D850 | EXCLUSION,  // Sgnw
            0x1B83 | LIMITED_USE,  // Sund
            0,
            0xABC0 | LIMITED_USE,  // Mtei
            0x10840 | EXCLUSION | RTL,  // Armi
            0x10B00 | EXCLUSION | RTL,  // Avst
            0x11103 | LIMITED_USE,  // Cakm
            0xAC00 | RECOMMENDED,  // Kore
            0x11083 | EXCLUSION,  // Kthi
            0x10AD8 | EXCLUSION | RTL,  // Mani
            0x10B60 | EXCLUSION | RTL,  // Phli
            0x10B8F | EXCLUSION | RTL,  // Phlp
            0,
            0x10B40 | EXCLUSION | RTL,  // Prti
            0x0800 | EXCLUSION | RTL,  // Samr
            0xAA80 | LIMITED_USE | LB_LETTERS,  // Tavt
            0,
            0,
            0xA6A0 | LIMITED_USE,  // Bamu
            0xA4D0 | LIMITED_USE,  // Lisu
            0,
            0x10A60 | EXCLUSION | RTL,  // Sarb
            0x16AE6 | EXCLUSION,  // Bass
            0x1BC20 | EXCLUSION,  // Dupl
            0x10500 | EXCLUSION,  // Elba
            0x11315 | EXCLUSION,  // Gran
            0,
            0,
            0x1E802 | EXCLUSION | RTL,  // Mend
            0x109A0 | EXCLUSION | RTL,  // Merc
            0x10A95 | EXCLUSION | RTL,  // Narb
            0x10896 | EXCLUSION | RTL,  // Nbat
            0x10873 | EXCLUSION | RTL,  // Palm
            0x112BE | EXCLUSION,  // Sind
            0x118B4 | EXCLUSION | CASED,  // Wara
            0,
            0,
            0x16A4F | EXCLUSION,  // Mroo
            0x1B1C4 | EXCLUSION | LB_LETTERS,  // Nshu
            0x11183 | EXCLUSION,  // Shrd
            0x110D0 | EXCLUSION,  // Sora
            0x11680 | EXCLUSION,  // Takr
            0x18229 | EXCLUSION | LB_LETTERS,  // Tang
            0,
            0x14400 | EXCLUSION,  // Hluw
            0x11208 | EXCLUSION,  // Khoj
            0x11484 | EXCLUSION,  // Tirh
            0x10537 | EXCLUSION,  // Aghb
            0x11152 | EXCLUSION,  // Mahj
            0x11717 | EXCLUSION | LB_LETTERS,  // Ahom
            0x108F4 | EXCLUSION | RTL,  // Hatr
            0x1160E | EXCLUSION,  // Modi
            0x1128F | EXCLUSION,  // Mult
            0x11AC0 | EXCLUSION,  // Pauc
            0x1158E | EXCLUSION,  // Sidd
            0x1E909 | LIMITED_USE | RTL | CASED,  // Adlm
            0x11C0E | EXCLUSION,  // Bhks
            0x11C72 | EXCLUSION,  // Marc
            0x11412 | LIMITED_USE,  // Newa
            0x104B5 | LIMITED_USE | CASED,  // Osge
            0x5B57 | RECOMMENDED | LB_LETTERS,  // Hanb
            0x1112 | RECOMMENDED,  // Jamo
            0,
            0x11D10 | EXCLUSION,  // Gonm
            0x11A5C | EXCLUSION,  // Soyo
            0x11A0B | EXCLUSION,  // Zanb
            0x1180B | EXCLUSION,  // Dogr
            0x11D71 | LIMITED_USE,  // Gong
            0x11EE5 | EXCLUSION,  // Maka
            0x16E40 | EXCLUSION | CASED,  // Medf
            0x10D12 | LIMITED_USE | RTL,  // Rohg
            0x10F42 | EXCLUSION | RTL,  // Sogd
            0x10F19 | EXCLUSION | RTL,  // Sogo
            0x10FF1 | EXCLUSION | RTL,  // Elym
            0x1E108 | LIMITED_USE,  // Hmnp
            0x119CE | EXCLUSION,  // Nand
            0x1E2E1 | LIMITED_USE,  // Wcho
            0x10FBF | EXCLUSION | RTL,  // Chrs
            0x1190C | EXCLUSION,  // Diak
            0x18C65 | EXCLUSION | LB_LETTERS,  // Kits
            0x10E88 | EXCLUSION | RTL,  // Yezi
            0x12FE5 | EXCLUSION,  // Cpmn
            0x10F7C | EXCLUSION | RTL,  // Ougr
            0x16ABC | EXCLUSION,  // Tnsa
            0x1E290 | EXCLUSION,  // Toto
            0x10582 | EXCLUSION | CASED,  // Vith
            0x11F1B | EXCLUSION | LB_LETTERS,  // Kawi
            0x1E4E6 | EXCLUSION,  // Nagm
            // End copy-paste from parsescriptmetadata.py
        };

        private static final int getScriptProps(int script) {
            if (0 <= script && script < SCRIPT_PROPS.length) {
                return SCRIPT_PROPS[script];
            } else {
                return 0;
            }
        }
    }

    /**
     * Script usage constants.
     * See UAX #31 Unicode Identifier and Pattern Syntax.
     * http://www.unicode.org/reports/tr31/#Table_Candidate_Characters_for_Exclusion_from_Identifiers
     *
     * @stable ICU 51
     */
    public enum ScriptUsage {
        /**
         * Not encoded in Unicode.
         * @stable ICU 51
         */
        NOT_ENCODED,
        /**
         * Unknown script usage.
         * @stable ICU 51
         */
        UNKNOWN,
        /**
         * Candidate for Exclusion from Identifiers.
         * @stable ICU 51
         */
        EXCLUDED,
        /**
         * Limited Use script.
         * @stable ICU 51
         */
        LIMITED_USE,
        /**
         * Aspirational Use script.
         * @stable ICU 51
         */
        ASPIRATIONAL,
        /**
         * Recommended script.
         * @stable ICU 51
         */
        RECOMMENDED
    }
    private static final ScriptUsage[] usageValues = ScriptUsage.values();

    /**
     * Returns the script sample character string.
     * This string normally consists of one code point but might be longer.
     * The string is empty if the script is not encoded.
     *
     * @param script script code
     * @return the sample character string
     * @stable ICU 51
     */
    public static final String getSampleString(int script) {
        int sampleChar = ScriptMetadata.getScriptProps(script) & 0x1fffff;
        if(sampleChar != 0) {
            return new StringBuilder().appendCodePoint(sampleChar).toString();
        }
        return "";
    }

    /**
     * Returns the script usage according to UAX #31 Unicode Identifier and Pattern Syntax.
     * Returns {@link ScriptUsage#NOT_ENCODED} if the script is not encoded in Unicode.
     *
     * @param script script code
     * @return script usage
     * @see ScriptUsage
     * @stable ICU 51
     */
    public static final ScriptUsage getUsage(int script) {
        return usageValues[(ScriptMetadata.getScriptProps(script) >> 21) & 7];
    }

    /**
     * Returns true if the script is written right-to-left.
     * For example, Arab and Hebr.
     *
     * @param script script code
     * @return true if the script is right-to-left
     * @stable ICU 51
     */
    public static final boolean isRightToLeft(int script) {
        return (ScriptMetadata.getScriptProps(script) & ScriptMetadata.RTL) != 0;
    }

    /**
     * Returns true if the script allows line breaks between letters (excluding hyphenation).
     * Such a script typically requires dictionary-based line breaking.
     * For example, Hani and Thai.
     *
     * @param script script code
     * @return true if the script allows line breaks between letters
     * @stable ICU 51
     */
    public static final boolean breaksBetweenLetters(int script) {
        return (ScriptMetadata.getScriptProps(script) & ScriptMetadata.LB_LETTERS) != 0;
    }

    /**
     * Returns true if in modern (or most recent) usage of the script case distinctions are customary.
     * For example, Latn and Cyrl.
     *
     * @param script script code
     * @return true if the script is cased
     * @stable ICU 51
     */
    public static final boolean isCased(int script) {
        return (ScriptMetadata.getScriptProps(script) & ScriptMetadata.CASED) != 0;
    }

    ///CLOVER:OFF
    /**
     *  Private Constructor. Never default construct
     */
    private UScript(){}
    ///CLOVER:ON
}
