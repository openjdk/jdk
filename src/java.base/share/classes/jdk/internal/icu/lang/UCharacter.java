// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/**
 *******************************************************************************
 * Copyright (C) 1996-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package jdk.internal.icu.lang;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import jdk.internal.icu.impl.CaseMapImpl;
import jdk.internal.icu.impl.EmojiProps;
import jdk.internal.icu.impl.IllegalIcuArgumentException;
import jdk.internal.icu.impl.Trie2;
import jdk.internal.icu.impl.UBiDiProps;
import jdk.internal.icu.impl.UCaseProps;
import jdk.internal.icu.impl.UCharacterName;
import jdk.internal.icu.impl.UCharacterNameChoice;
import jdk.internal.icu.impl.UCharacterProperty;
import jdk.internal.icu.impl.UCharacterUtility;
import jdk.internal.icu.impl.UPropertyAliases;
import jdk.internal.icu.lang.UCharacterEnums.ECharacterCategory;
import jdk.internal.icu.lang.UCharacterEnums.ECharacterDirection;
import jdk.internal.icu.text.BreakIterator;
import jdk.internal.icu.text.Normalizer2;
import jdk.internal.icu.util.RangeValueIterator;
import jdk.internal.icu.util.ULocale;
import jdk.internal.icu.util.ValueIterator;
import jdk.internal.icu.util.VersionInfo;

/**
 * {@icuenhanced java.lang.Character}.{@icu _usage_}
 *
 * <p>The UCharacter class provides extensions to the {@link java.lang.Character} class.
 * These extensions provide support for more Unicode properties.
 * Each ICU release supports the latest version of Unicode available at that time.
 *
 * <p>For some time before Java 5 added support for supplementary Unicode code points,
 * The ICU UCharacter class and many other ICU classes already supported them.
 * Some UCharacter methods and constants were widened slightly differently than
 * how the Character class methods and constants were widened later.
 * In particular, {@link Character#MAX_VALUE} is still a char with the value U+FFFF,
 * while the {@link UCharacter#MAX_VALUE} is an int with the value U+10FFFF.
 *
 * <p>Code points are represented in these API using ints. While it would be
 * more convenient in Java to have a separate primitive datatype for them,
 * ints suffice in the meantime.
 *
 * <p>To use this class please add the jar file name icu4j.jar to the
 * class path, since it contains data files which supply the information used
 * by this file.<br>
 * E.g. In Windows <br>
 * <code>set CLASSPATH=%CLASSPATH%;$JAR_FILE_PATH/ucharacter.jar</code>.<br>
 * Otherwise, another method would be to copy the files uprops.dat and
 * unames.icu from the icu4j source subdirectory
 * <i>$ICU4J_SRC/src/jdk.internal.icu.impl.data</i> to your class directory
 * <i>$ICU4J_CLASS/jdk.internal.icu.impl.data</i>.
 *
 * <p>Aside from the additions for UTF-16 support, and the updated Unicode
 * properties, the main differences between UCharacter and Character are:
 * <ul>
 * <li> UCharacter is not designed to be a char wrapper and does not have
 *      APIs to which involves management of that single char.<br>
 *      These include:
 *      <ul>
 *        <li> char charValue(),
 *        <li> int compareTo(java.lang.Character, java.lang.Character), etc.
 *      </ul>
 * <li> UCharacter does not include Character APIs that are deprecated, nor
 *      does it include the Java-specific character information, such as
 *      boolean isJavaIdentifierPart(char ch).
 * <li> Character maps characters 'A' - 'Z' and 'a' - 'z' to the numeric
 *      values '10' - '35'. UCharacter also does this in digit and
 *      getNumericValue, to adhere to the java semantics of these
 *      methods.  New methods unicodeDigit, and
 *      getUnicodeNumericValue do not treat the above code points
 *      as having numeric values.  This is a semantic change from ICU4J 1.3.1.
 * </ul>
 * <p>
 * Further detail on differences can be determined using the program
 *        <a href=
 * "https://github.com/unicode-org/icu/blob/main/icu4j/main/tests/core/src/jdk.internal.icu/dev/test/lang/UCharacterCompare.java">
 *        jdk.internal.icu.dev.test.lang.UCharacterCompare</a>
 * <p>
 * In addition to Java compatibility functions, which calculate derived properties,
 * this API provides low-level access to the Unicode Character Database.
 * <p>
 * Unicode assigns each code point (not just assigned character) values for
 * many properties.
 * Most of them are simple boolean flags, or constants from a small enumerated list.
 * For some properties, values are strings or other relatively more complex types.
 * <p>
 * For more information see
 * <a href="http://www.unicode/org/ucd/">"About the Unicode Character Database"</a>
 * (http://www.unicode.org/ucd/)
 * and the <a href="https://unicode-org.github.io/icu/userguide/strings/properties">ICU
 * User Guide chapter on Properties</a>
 * (https://unicode-org.github.io/icu/userguide/strings/properties).
 * <p>
 * There are also functions that provide easy migration from C/POSIX functions
 * like isblank(). Their use is generally discouraged because the C/POSIX
 * standards do not define their semantics beyond the ASCII range, which means
 * that different implementations exhibit very different behavior.
 * Instead, Unicode properties should be used directly.
 * <p>
 * There are also only a few, broad C/POSIX character classes, and they tend
 * to be used for conflicting purposes. For example, the "isalpha()" class
 * is sometimes used to determine word boundaries, while a more sophisticated
 * approach would at least distinguish initial letters from continuation
 * characters (the latter including combining marks).
 * (In ICU, BreakIterator is the most sophisticated API for word boundaries.)
 * Another example: There is no "istitle()" class for titlecase characters.
 * <p>
 * ICU 3.4 and later provides API access for all twelve C/POSIX character classes.
 * ICU implements them according to the Standard Recommendations in
 * Annex C: Compatibility Properties of UTS #18 Unicode Regular Expressions
 * (http://www.unicode.org/reports/tr18/#Compatibility_Properties).
 * <p>
 * API access for C/POSIX character classes is as follows:
 * <pre>{@code
 * - alpha:     isUAlphabetic(c) or hasBinaryProperty(c, UProperty.ALPHABETIC)
 * - lower:     isULowercase(c) or hasBinaryProperty(c, UProperty.LOWERCASE)
 * - upper:     isUUppercase(c) or hasBinaryProperty(c, UProperty.UPPERCASE)
 * - punct:     ((1<<getType(c)) & ((1<<DASH_PUNCTUATION)|(1<<START_PUNCTUATION)|
 *               (1<<END_PUNCTUATION)|(1<<CONNECTOR_PUNCTUATION)|(1<<OTHER_PUNCTUATION)|
 *               (1<<INITIAL_PUNCTUATION)|(1<<FINAL_PUNCTUATION)))!=0
 * - digit:     isDigit(c) or getType(c)==DECIMAL_DIGIT_NUMBER
 * - xdigit:    hasBinaryProperty(c, UProperty.POSIX_XDIGIT)
 * - alnum:     hasBinaryProperty(c, UProperty.POSIX_ALNUM)
 * - space:     isUWhiteSpace(c) or hasBinaryProperty(c, UProperty.WHITE_SPACE)
 * - blank:     hasBinaryProperty(c, UProperty.POSIX_BLANK)
 * - cntrl:     getType(c)==CONTROL
 * - graph:     hasBinaryProperty(c, UProperty.POSIX_GRAPH)
 * - print:     hasBinaryProperty(c, UProperty.POSIX_PRINT)}</pre>
 * <p>
 * The C/POSIX character classes are also available in UnicodeSet patterns,
 * using patterns like [:graph:] or \p{graph}.
 *
 * <p>{@icunote} There are several ICU (and Java) whitespace functions.
 * Comparison:<ul>
 * <li> isUWhiteSpace=UCHAR_WHITE_SPACE: Unicode White_Space property;
 *       most of general categories "Z" (separators) + most whitespace ISO controls
 *       (including no-break spaces, but excluding IS1..IS4)
 * <li> isWhitespace: Java isWhitespace; Z + whitespace ISO controls but excluding no-break spaces
 * <li> isSpaceChar: just Z (including no-break spaces)</ul>
 *
 * <p>
 * This class is not subclassable.
 *
 * @author Syn Wee Quek
 * @stable ICU 2.1
 * @see jdk.internal.icu.lang.UCharacterEnums
 */
@SuppressWarnings("deprecation")
public final class UCharacter implements ECharacterCategory, ECharacterDirection
{
    /**
     * Lead surrogate bitmask
     */
    private static final int LEAD_SURROGATE_BITMASK = 0xFFFFFC00;

    /**
     * Trail surrogate bitmask
     */
    private static final int TRAIL_SURROGATE_BITMASK = 0xFFFFFC00;

    /**
     * Lead surrogate bits
     */
    private static final int LEAD_SURROGATE_BITS = 0xD800;

    /**
     * Trail surrogate bits
     */
    private static final int TRAIL_SURROGATE_BITS = 0xDC00;

    private static final int U16_SURROGATE_OFFSET = ((0xd800 << 10) + 0xdc00 - 0x10000);

    // public inner classes ----------------------------------------------

    /**
     * {@icuenhanced java.lang.Character.UnicodeBlock}.{@icu _usage_}
     *
     * A family of character subsets representing the character blocks in the
     * Unicode specification, generated from Unicode Data file Blocks.txt.
     * Character blocks generally define characters used for a specific script
     * or purpose. A character is contained by at most one Unicode block.
     *
     * {@icunote} All fields named XXX_ID are specific to ICU.
     *
     * @stable ICU 2.4
     */
    public static final class UnicodeBlock extends Character.Subset
    {
        // block id corresponding to icu4c -----------------------------------

        /**
         * @stable ICU 2.4
         */
        public static final int INVALID_CODE_ID = -1;
        /**
         * @stable ICU 2.4
         */
        public static final int BASIC_LATIN_ID = 1;
        /**
         * @stable ICU 2.4
         */
        public static final int LATIN_1_SUPPLEMENT_ID = 2;
        /**
         * @stable ICU 2.4
         */
        public static final int LATIN_EXTENDED_A_ID = 3;
        /**
         * @stable ICU 2.4
         */
        public static final int LATIN_EXTENDED_B_ID = 4;
        /**
         * @stable ICU 2.4
         */
        public static final int IPA_EXTENSIONS_ID = 5;
        /**
         * @stable ICU 2.4
         */
        public static final int SPACING_MODIFIER_LETTERS_ID = 6;
        /**
         * @stable ICU 2.4
         */
        public static final int COMBINING_DIACRITICAL_MARKS_ID = 7;
        /**
         * Unicode 3.2 renames this block to "Greek and Coptic".
         * @stable ICU 2.4
         */
        public static final int GREEK_ID = 8;
        /**
         * @stable ICU 2.4
         */
        public static final int CYRILLIC_ID = 9;
        /**
         * @stable ICU 2.4
         */
        public static final int ARMENIAN_ID = 10;
        /**
         * @stable ICU 2.4
         */
        public static final int HEBREW_ID = 11;
        /**
         * @stable ICU 2.4
         */
        public static final int ARABIC_ID = 12;
        /**
         * @stable ICU 2.4
         */
        public static final int SYRIAC_ID = 13;
        /**
         * @stable ICU 2.4
         */
        public static final int THAANA_ID = 14;
        /**
         * @stable ICU 2.4
         */
        public static final int DEVANAGARI_ID = 15;
        /**
         * @stable ICU 2.4
         */
        public static final int BENGALI_ID = 16;
        /**
         * @stable ICU 2.4
         */
        public static final int GURMUKHI_ID = 17;
        /**
         * @stable ICU 2.4
         */
        public static final int GUJARATI_ID = 18;
        /**
         * @stable ICU 2.4
         */
        public static final int ORIYA_ID = 19;
        /**
         * @stable ICU 2.4
         */
        public static final int TAMIL_ID = 20;
        /**
         * @stable ICU 2.4
         */
        public static final int TELUGU_ID = 21;
        /**
         * @stable ICU 2.4
         */
        public static final int KANNADA_ID = 22;
        /**
         * @stable ICU 2.4
         */
        public static final int MALAYALAM_ID = 23;
        /**
         * @stable ICU 2.4
         */
        public static final int SINHALA_ID = 24;
        /**
         * @stable ICU 2.4
         */
        public static final int THAI_ID = 25;
        /**
         * @stable ICU 2.4
         */
        public static final int LAO_ID = 26;
        /**
         * @stable ICU 2.4
         */
        public static final int TIBETAN_ID = 27;
        /**
         * @stable ICU 2.4
         */
        public static final int MYANMAR_ID = 28;
        /**
         * @stable ICU 2.4
         */
        public static final int GEORGIAN_ID = 29;
        /**
         * @stable ICU 2.4
         */
        public static final int HANGUL_JAMO_ID = 30;
        /**
         * @stable ICU 2.4
         */
        public static final int ETHIOPIC_ID = 31;
        /**
         * @stable ICU 2.4
         */
        public static final int CHEROKEE_ID = 32;
        /**
         * @stable ICU 2.4
         */
        public static final int UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_ID = 33;
        /**
         * @stable ICU 2.4
         */
        public static final int OGHAM_ID = 34;
        /**
         * @stable ICU 2.4
         */
        public static final int RUNIC_ID = 35;
        /**
         * @stable ICU 2.4
         */
        public static final int KHMER_ID = 36;
        /**
         * @stable ICU 2.4
         */
        public static final int MONGOLIAN_ID = 37;
        /**
         * @stable ICU 2.4
         */
        public static final int LATIN_EXTENDED_ADDITIONAL_ID = 38;
        /**
         * @stable ICU 2.4
         */
        public static final int GREEK_EXTENDED_ID = 39;
        /**
         * @stable ICU 2.4
         */
        public static final int GENERAL_PUNCTUATION_ID = 40;
        /**
         * @stable ICU 2.4
         */
        public static final int SUPERSCRIPTS_AND_SUBSCRIPTS_ID = 41;
        /**
         * @stable ICU 2.4
         */
        public static final int CURRENCY_SYMBOLS_ID = 42;
        /**
         * Unicode 3.2 renames this block to "Combining Diacritical Marks for
         * Symbols".
         * @stable ICU 2.4
         */
        public static final int COMBINING_MARKS_FOR_SYMBOLS_ID = 43;
        /**
         * @stable ICU 2.4
         */
        public static final int LETTERLIKE_SYMBOLS_ID = 44;
        /**
         * @stable ICU 2.4
         */
        public static final int NUMBER_FORMS_ID = 45;
        /**
         * @stable ICU 2.4
         */
        public static final int ARROWS_ID = 46;
        /**
         * @stable ICU 2.4
         */
        public static final int MATHEMATICAL_OPERATORS_ID = 47;
        /**
         * @stable ICU 2.4
         */
        public static final int MISCELLANEOUS_TECHNICAL_ID = 48;
        /**
         * @stable ICU 2.4
         */
        public static final int CONTROL_PICTURES_ID = 49;
        /**
         * @stable ICU 2.4
         */
        public static final int OPTICAL_CHARACTER_RECOGNITION_ID = 50;
        /**
         * @stable ICU 2.4
         */
        public static final int ENCLOSED_ALPHANUMERICS_ID = 51;
        /**
         * @stable ICU 2.4
         */
        public static final int BOX_DRAWING_ID = 52;
        /**
         * @stable ICU 2.4
         */
        public static final int BLOCK_ELEMENTS_ID = 53;
        /**
         * @stable ICU 2.4
         */
        public static final int GEOMETRIC_SHAPES_ID = 54;
        /**
         * @stable ICU 2.4
         */
        public static final int MISCELLANEOUS_SYMBOLS_ID = 55;
        /**
         * @stable ICU 2.4
         */
        public static final int DINGBATS_ID = 56;
        /**
         * @stable ICU 2.4
         */
        public static final int BRAILLE_PATTERNS_ID = 57;
        /**
         * @stable ICU 2.4
         */
        public static final int CJK_RADICALS_SUPPLEMENT_ID = 58;
        /**
         * @stable ICU 2.4
         */
        public static final int KANGXI_RADICALS_ID = 59;
        /**
         * @stable ICU 2.4
         */
        public static final int IDEOGRAPHIC_DESCRIPTION_CHARACTERS_ID = 60;
        /**
         * @stable ICU 2.4
         */
        public static final int CJK_SYMBOLS_AND_PUNCTUATION_ID = 61;
        /**
         * @stable ICU 2.4
         */
        public static final int HIRAGANA_ID = 62;
        /**
         * @stable ICU 2.4
         */
        public static final int KATAKANA_ID = 63;
        /**
         * @stable ICU 2.4
         */
        public static final int BOPOMOFO_ID = 64;
        /**
         * @stable ICU 2.4
         */
        public static final int HANGUL_COMPATIBILITY_JAMO_ID = 65;
        /**
         * @stable ICU 2.4
         */
        public static final int KANBUN_ID = 66;
        /**
         * @stable ICU 2.4
         */
        public static final int BOPOMOFO_EXTENDED_ID = 67;
        /**
         * @stable ICU 2.4
         */
        public static final int ENCLOSED_CJK_LETTERS_AND_MONTHS_ID = 68;
        /**
         * @stable ICU 2.4
         */
        public static final int CJK_COMPATIBILITY_ID = 69;
        /**
         * @stable ICU 2.4
         */
        public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A_ID = 70;
        /**
         * @stable ICU 2.4
         */
        public static final int CJK_UNIFIED_IDEOGRAPHS_ID = 71;
        /**
         * @stable ICU 2.4
         */
        public static final int YI_SYLLABLES_ID = 72;
        /**
         * @stable ICU 2.4
         */
        public static final int YI_RADICALS_ID = 73;
        /**
         * @stable ICU 2.4
         */
        public static final int HANGUL_SYLLABLES_ID = 74;
        /**
         * @stable ICU 2.4
         */
        public static final int HIGH_SURROGATES_ID = 75;
        /**
         * @stable ICU 2.4
         */
        public static final int HIGH_PRIVATE_USE_SURROGATES_ID = 76;
        /**
         * @stable ICU 2.4
         */
        public static final int LOW_SURROGATES_ID = 77;
        /**
         * Same as public static final int PRIVATE_USE.
         * Until Unicode 3.1.1; the corresponding block name was "Private Use";
         * and multiple code point ranges had this block.
         * Unicode 3.2 renames the block for the BMP PUA to "Private Use Area"
         * and adds separate blocks for the supplementary PUAs.
         * @stable ICU 2.4
         */
        public static final int PRIVATE_USE_AREA_ID = 78;
        /**
         * Same as public static final int PRIVATE_USE_AREA.
         * Until Unicode 3.1.1; the corresponding block name was "Private Use";
         * and multiple code point ranges had this block.
         * Unicode 3.2 renames the block for the BMP PUA to "Private Use Area"
         * and adds separate blocks for the supplementary PUAs.
         * @stable ICU 2.4
         */
        public static final int PRIVATE_USE_ID = PRIVATE_USE_AREA_ID;
        /**
         * @stable ICU 2.4
         */
        public static final int CJK_COMPATIBILITY_IDEOGRAPHS_ID = 79;
        /**
         * @stable ICU 2.4
         */
        public static final int ALPHABETIC_PRESENTATION_FORMS_ID = 80;
        /**
         * @stable ICU 2.4
         */
        public static final int ARABIC_PRESENTATION_FORMS_A_ID = 81;
        /**
         * @stable ICU 2.4
         */
        public static final int COMBINING_HALF_MARKS_ID = 82;
        /**
         * @stable ICU 2.4
         */
        public static final int CJK_COMPATIBILITY_FORMS_ID = 83;
        /**
         * @stable ICU 2.4
         */
        public static final int SMALL_FORM_VARIANTS_ID = 84;
        /**
         * @stable ICU 2.4
         */
        public static final int ARABIC_PRESENTATION_FORMS_B_ID = 85;
        /**
         * @stable ICU 2.4
         */
        public static final int SPECIALS_ID = 86;
        /**
         * @stable ICU 2.4
         */
        public static final int HALFWIDTH_AND_FULLWIDTH_FORMS_ID = 87;
        /**
         * @stable ICU 2.4
         */
        public static final int OLD_ITALIC_ID = 88;
        /**
         * @stable ICU 2.4
         */
        public static final int GOTHIC_ID = 89;
        /**
         * @stable ICU 2.4
         */
        public static final int DESERET_ID = 90;
        /**
         * @stable ICU 2.4
         */
        public static final int BYZANTINE_MUSICAL_SYMBOLS_ID = 91;
        /**
         * @stable ICU 2.4
         */
        public static final int MUSICAL_SYMBOLS_ID = 92;
        /**
         * @stable ICU 2.4
         */
        public static final int MATHEMATICAL_ALPHANUMERIC_SYMBOLS_ID = 93;
        /**
         * @stable ICU 2.4
         */
        public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B_ID = 94;
        /**
         * @stable ICU 2.4
         */
        public static final int
        CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT_ID = 95;
        /**
         * @stable ICU 2.4
         */
        public static final int TAGS_ID = 96;

        // New blocks in Unicode 3.2

        /**
         * Unicode 4.0.1 renames the "Cyrillic Supplementary" block to "Cyrillic Supplement".
         * @stable ICU 2.4
         */
        public static final int CYRILLIC_SUPPLEMENTARY_ID = 97;
        /**
         * Unicode 4.0.1 renames the "Cyrillic Supplementary" block to "Cyrillic Supplement".
         * @stable ICU 3.0
         */

        public static final int CYRILLIC_SUPPLEMENT_ID = 97;
        /**
         * @stable ICU 2.4
         */
        public static final int TAGALOG_ID = 98;
        /**
         * @stable ICU 2.4
         */
        public static final int HANUNOO_ID = 99;
        /**
         * @stable ICU 2.4
         */
        public static final int BUHID_ID = 100;
        /**
         * @stable ICU 2.4
         */
        public static final int TAGBANWA_ID = 101;
        /**
         * @stable ICU 2.4
         */
        public static final int MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A_ID = 102;
        /**
         * @stable ICU 2.4
         */
        public static final int SUPPLEMENTAL_ARROWS_A_ID = 103;
        /**
         * @stable ICU 2.4
         */
        public static final int SUPPLEMENTAL_ARROWS_B_ID = 104;
        /**
         * @stable ICU 2.4
         */
        public static final int MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B_ID = 105;
        /**
         * @stable ICU 2.4
         */
        public static final int SUPPLEMENTAL_MATHEMATICAL_OPERATORS_ID = 106;
        /**
         * @stable ICU 2.4
         */
        public static final int KATAKANA_PHONETIC_EXTENSIONS_ID = 107;
        /**
         * @stable ICU 2.4
         */
        public static final int VARIATION_SELECTORS_ID = 108;
        /**
         * @stable ICU 2.4
         */
        public static final int SUPPLEMENTARY_PRIVATE_USE_AREA_A_ID = 109;
        /**
         * @stable ICU 2.4
         */
        public static final int SUPPLEMENTARY_PRIVATE_USE_AREA_B_ID = 110;

        /**
         * @stable ICU 2.6
         */
        public static final int LIMBU_ID = 111; /*[1900]*/
        /**
         * @stable ICU 2.6
         */
        public static final int TAI_LE_ID = 112; /*[1950]*/
        /**
         * @stable ICU 2.6
         */
        public static final int KHMER_SYMBOLS_ID = 113; /*[19E0]*/
        /**
         * @stable ICU 2.6
         */
        public static final int PHONETIC_EXTENSIONS_ID = 114; /*[1D00]*/
        /**
         * @stable ICU 2.6
         */
        public static final int MISCELLANEOUS_SYMBOLS_AND_ARROWS_ID = 115; /*[2B00]*/
        /**
         * @stable ICU 2.6
         */
        public static final int YIJING_HEXAGRAM_SYMBOLS_ID = 116; /*[4DC0]*/
        /**
         * @stable ICU 2.6
         */
        public static final int LINEAR_B_SYLLABARY_ID = 117; /*[10000]*/
        /**
         * @stable ICU 2.6
         */
        public static final int LINEAR_B_IDEOGRAMS_ID = 118; /*[10080]*/
        /**
         * @stable ICU 2.6
         */
        public static final int AEGEAN_NUMBERS_ID = 119; /*[10100]*/
        /**
         * @stable ICU 2.6
         */
        public static final int UGARITIC_ID = 120; /*[10380]*/
        /**
         * @stable ICU 2.6
         */
        public static final int SHAVIAN_ID = 121; /*[10450]*/
        /**
         * @stable ICU 2.6
         */
        public static final int OSMANYA_ID = 122; /*[10480]*/
        /**
         * @stable ICU 2.6
         */
        public static final int CYPRIOT_SYLLABARY_ID = 123; /*[10800]*/
        /**
         * @stable ICU 2.6
         */
        public static final int TAI_XUAN_JING_SYMBOLS_ID = 124; /*[1D300]*/
        /**
         * @stable ICU 2.6
         */
        public static final int VARIATION_SELECTORS_SUPPLEMENT_ID = 125; /*[E0100]*/

        /* New blocks in Unicode 4.1 */

        /**
         * @stable ICU 3.4
         */
        public static final int ANCIENT_GREEK_MUSICAL_NOTATION_ID = 126; /*[1D200]*/

        /**
         * @stable ICU 3.4
         */
        public static final int ANCIENT_GREEK_NUMBERS_ID = 127; /*[10140]*/

        /**
         * @stable ICU 3.4
         */
        public static final int ARABIC_SUPPLEMENT_ID = 128; /*[0750]*/

        /**
         * @stable ICU 3.4
         */
        public static final int BUGINESE_ID = 129; /*[1A00]*/

        /**
         * @stable ICU 3.4
         */
        public static final int CJK_STROKES_ID = 130; /*[31C0]*/

        /**
         * @stable ICU 3.4
         */
        public static final int COMBINING_DIACRITICAL_MARKS_SUPPLEMENT_ID = 131; /*[1DC0]*/

        /**
         * @stable ICU 3.4
         */
        public static final int COPTIC_ID = 132; /*[2C80]*/

        /**
         * @stable ICU 3.4
         */
        public static final int ETHIOPIC_EXTENDED_ID = 133; /*[2D80]*/

        /**
         * @stable ICU 3.4
         */
        public static final int ETHIOPIC_SUPPLEMENT_ID = 134; /*[1380]*/

        /**
         * @stable ICU 3.4
         */
        public static final int GEORGIAN_SUPPLEMENT_ID = 135; /*[2D00]*/

        /**
         * @stable ICU 3.4
         */
        public static final int GLAGOLITIC_ID = 136; /*[2C00]*/

        /**
         * @stable ICU 3.4
         */
        public static final int KHAROSHTHI_ID = 137; /*[10A00]*/

        /**
         * @stable ICU 3.4
         */
        public static final int MODIFIER_TONE_LETTERS_ID = 138; /*[A700]*/

        /**
         * @stable ICU 3.4
         */
        public static final int NEW_TAI_LUE_ID = 139; /*[1980]*/

        /**
         * @stable ICU 3.4
         */
        public static final int OLD_PERSIAN_ID = 140; /*[103A0]*/

        /**
         * @stable ICU 3.4
         */
        public static final int PHONETIC_EXTENSIONS_SUPPLEMENT_ID = 141; /*[1D80]*/

        /**
         * @stable ICU 3.4
         */
        public static final int SUPPLEMENTAL_PUNCTUATION_ID = 142; /*[2E00]*/

        /**
         * @stable ICU 3.4
         */
        public static final int SYLOTI_NAGRI_ID = 143; /*[A800]*/

        /**
         * @stable ICU 3.4
         */
        public static final int TIFINAGH_ID = 144; /*[2D30]*/

        /**
         * @stable ICU 3.4
         */
        public static final int VERTICAL_FORMS_ID = 145; /*[FE10]*/

        /* New blocks in Unicode 5.0 */

        /**
         * @stable ICU 3.6
         */
        public static final int NKO_ID = 146; /*[07C0]*/
        /**
         * @stable ICU 3.6
         */
        public static final int BALINESE_ID = 147; /*[1B00]*/
        /**
         * @stable ICU 3.6
         */
        public static final int LATIN_EXTENDED_C_ID = 148; /*[2C60]*/
        /**
         * @stable ICU 3.6
         */
        public static final int LATIN_EXTENDED_D_ID = 149; /*[A720]*/
        /**
         * @stable ICU 3.6
         */
        public static final int PHAGS_PA_ID = 150; /*[A840]*/
        /**
         * @stable ICU 3.6
         */
        public static final int PHOENICIAN_ID = 151; /*[10900]*/
        /**
         * @stable ICU 3.6
         */
        public static final int CUNEIFORM_ID = 152; /*[12000]*/
        /**
         * @stable ICU 3.6
         */
        public static final int CUNEIFORM_NUMBERS_AND_PUNCTUATION_ID = 153; /*[12400]*/
        /**
         * @stable ICU 3.6
         */
        public static final int COUNTING_ROD_NUMERALS_ID = 154; /*[1D360]*/

        /**
         * @stable ICU 4.0
         */
        public static final int SUNDANESE_ID = 155; /* [1B80] */

        /**
         * @stable ICU 4.0
         */
        public static final int LEPCHA_ID = 156; /* [1C00] */

        /**
         * @stable ICU 4.0
         */
        public static final int OL_CHIKI_ID = 157; /* [1C50] */

        /**
         * @stable ICU 4.0
         */
        public static final int CYRILLIC_EXTENDED_A_ID = 158; /* [2DE0] */

        /**
         * @stable ICU 4.0
         */
        public static final int VAI_ID = 159; /* [A500] */

        /**
         * @stable ICU 4.0
         */
        public static final int CYRILLIC_EXTENDED_B_ID = 160; /* [A640] */

        /**
         * @stable ICU 4.0
         */
        public static final int SAURASHTRA_ID = 161; /* [A880] */

        /**
         * @stable ICU 4.0
         */
        public static final int KAYAH_LI_ID = 162; /* [A900] */

        /**
         * @stable ICU 4.0
         */
        public static final int REJANG_ID = 163; /* [A930] */

        /**
         * @stable ICU 4.0
         */
        public static final int CHAM_ID = 164; /* [AA00] */

        /**
         * @stable ICU 4.0
         */
        public static final int ANCIENT_SYMBOLS_ID = 165; /* [10190] */

        /**
         * @stable ICU 4.0
         */
        public static final int PHAISTOS_DISC_ID = 166; /* [101D0] */

        /**
         * @stable ICU 4.0
         */
        public static final int LYCIAN_ID = 167; /* [10280] */

        /**
         * @stable ICU 4.0
         */
        public static final int CARIAN_ID = 168; /* [102A0] */

        /**
         * @stable ICU 4.0
         */
        public static final int LYDIAN_ID = 169; /* [10920] */

        /**
         * @stable ICU 4.0
         */
        public static final int MAHJONG_TILES_ID = 170; /* [1F000] */

        /**
         * @stable ICU 4.0
         */
        public static final int DOMINO_TILES_ID = 171; /* [1F030] */

        /* New blocks in Unicode 5.2 */

        /** @stable ICU 4.4 */
        public static final int SAMARITAN_ID = 172; /*[0800]*/
        /** @stable ICU 4.4 */
        public static final int UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED_ID = 173; /*[18B0]*/
        /** @stable ICU 4.4 */
        public static final int TAI_THAM_ID = 174; /*[1A20]*/
        /** @stable ICU 4.4 */
        public static final int VEDIC_EXTENSIONS_ID = 175; /*[1CD0]*/
        /** @stable ICU 4.4 */
        public static final int LISU_ID = 176; /*[A4D0]*/
        /** @stable ICU 4.4 */
        public static final int BAMUM_ID = 177; /*[A6A0]*/
        /** @stable ICU 4.4 */
        public static final int COMMON_INDIC_NUMBER_FORMS_ID = 178; /*[A830]*/
        /** @stable ICU 4.4 */
        public static final int DEVANAGARI_EXTENDED_ID = 179; /*[A8E0]*/
        /** @stable ICU 4.4 */
        public static final int HANGUL_JAMO_EXTENDED_A_ID = 180; /*[A960]*/
        /** @stable ICU 4.4 */
        public static final int JAVANESE_ID = 181; /*[A980]*/
        /** @stable ICU 4.4 */
        public static final int MYANMAR_EXTENDED_A_ID = 182; /*[AA60]*/
        /** @stable ICU 4.4 */
        public static final int TAI_VIET_ID = 183; /*[AA80]*/
        /** @stable ICU 4.4 */
        public static final int MEETEI_MAYEK_ID = 184; /*[ABC0]*/
        /** @stable ICU 4.4 */
        public static final int HANGUL_JAMO_EXTENDED_B_ID = 185; /*[D7B0]*/
        /** @stable ICU 4.4 */
        public static final int IMPERIAL_ARAMAIC_ID = 186; /*[10840]*/
        /** @stable ICU 4.4 */
        public static final int OLD_SOUTH_ARABIAN_ID = 187; /*[10A60]*/
        /** @stable ICU 4.4 */
        public static final int AVESTAN_ID = 188; /*[10B00]*/
        /** @stable ICU 4.4 */
        public static final int INSCRIPTIONAL_PARTHIAN_ID = 189; /*[10B40]*/
        /** @stable ICU 4.4 */
        public static final int INSCRIPTIONAL_PAHLAVI_ID = 190; /*[10B60]*/
        /** @stable ICU 4.4 */
        public static final int OLD_TURKIC_ID = 191; /*[10C00]*/
        /** @stable ICU 4.4 */
        public static final int RUMI_NUMERAL_SYMBOLS_ID = 192; /*[10E60]*/
        /** @stable ICU 4.4 */
        public static final int KAITHI_ID = 193; /*[11080]*/
        /** @stable ICU 4.4 */
        public static final int EGYPTIAN_HIEROGLYPHS_ID = 194; /*[13000]*/
        /** @stable ICU 4.4 */
        public static final int ENCLOSED_ALPHANUMERIC_SUPPLEMENT_ID = 195; /*[1F100]*/
        /** @stable ICU 4.4 */
        public static final int ENCLOSED_IDEOGRAPHIC_SUPPLEMENT_ID = 196; /*[1F200]*/
        /** @stable ICU 4.4 */
        public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C_ID = 197; /*[2A700]*/

        /* New blocks in Unicode 6.0 */

        /** @stable ICU 4.6 */
        public static final int MANDAIC_ID = 198; /*[0840]*/
        /** @stable ICU 4.6 */
        public static final int BATAK_ID = 199; /*[1BC0]*/
        /** @stable ICU 4.6 */
        public static final int ETHIOPIC_EXTENDED_A_ID = 200; /*[AB00]*/
        /** @stable ICU 4.6 */
        public static final int BRAHMI_ID = 201; /*[11000]*/
        /** @stable ICU 4.6 */
        public static final int BAMUM_SUPPLEMENT_ID = 202; /*[16800]*/
        /** @stable ICU 4.6 */
        public static final int KANA_SUPPLEMENT_ID = 203; /*[1B000]*/
        /** @stable ICU 4.6 */
        public static final int PLAYING_CARDS_ID = 204; /*[1F0A0]*/
        /** @stable ICU 4.6 */
        public static final int MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS_ID = 205; /*[1F300]*/
        /** @stable ICU 4.6 */
        public static final int EMOTICONS_ID = 206; /*[1F600]*/
        /** @stable ICU 4.6 */
        public static final int TRANSPORT_AND_MAP_SYMBOLS_ID = 207; /*[1F680]*/
        /** @stable ICU 4.6 */
        public static final int ALCHEMICAL_SYMBOLS_ID = 208; /*[1F700]*/
        /** @stable ICU 4.6 */
        public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D_ID = 209; /*[2B740]*/

        /* New blocks in Unicode 6.1 */

        /** @stable ICU 49 */
        public static final int ARABIC_EXTENDED_A_ID = 210; /*[08A0]*/
        /** @stable ICU 49 */
        public static final int ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS_ID = 211; /*[1EE00]*/
        /** @stable ICU 49 */
        public static final int CHAKMA_ID = 212; /*[11100]*/
        /** @stable ICU 49 */
        public static final int MEETEI_MAYEK_EXTENSIONS_ID = 213; /*[AAE0]*/
        /** @stable ICU 49 */
        public static final int MEROITIC_CURSIVE_ID = 214; /*[109A0]*/
        /** @stable ICU 49 */
        public static final int MEROITIC_HIEROGLYPHS_ID = 215; /*[10980]*/
        /** @stable ICU 49 */
        public static final int MIAO_ID = 216; /*[16F00]*/
        /** @stable ICU 49 */
        public static final int SHARADA_ID = 217; /*[11180]*/
        /** @stable ICU 49 */
        public static final int SORA_SOMPENG_ID = 218; /*[110D0]*/
        /** @stable ICU 49 */
        public static final int SUNDANESE_SUPPLEMENT_ID = 219; /*[1CC0]*/
        /** @stable ICU 49 */
        public static final int TAKRI_ID = 220; /*[11680]*/

        /* New blocks in Unicode 7.0 */

        /** @stable ICU 54 */
        public static final int BASSA_VAH_ID = 221; /*[16AD0]*/
        /** @stable ICU 54 */
        public static final int CAUCASIAN_ALBANIAN_ID = 222; /*[10530]*/
        /** @stable ICU 54 */
        public static final int COPTIC_EPACT_NUMBERS_ID = 223; /*[102E0]*/
        /** @stable ICU 54 */
        public static final int COMBINING_DIACRITICAL_MARKS_EXTENDED_ID = 224; /*[1AB0]*/
        /** @stable ICU 54 */
        public static final int DUPLOYAN_ID = 225; /*[1BC00]*/
        /** @stable ICU 54 */
        public static final int ELBASAN_ID = 226; /*[10500]*/
        /** @stable ICU 54 */
        public static final int GEOMETRIC_SHAPES_EXTENDED_ID = 227; /*[1F780]*/
        /** @stable ICU 54 */
        public static final int GRANTHA_ID = 228; /*[11300]*/
        /** @stable ICU 54 */
        public static final int KHOJKI_ID = 229; /*[11200]*/
        /** @stable ICU 54 */
        public static final int KHUDAWADI_ID = 230; /*[112B0]*/
        /** @stable ICU 54 */
        public static final int LATIN_EXTENDED_E_ID = 231; /*[AB30]*/
        /** @stable ICU 54 */
        public static final int LINEAR_A_ID = 232; /*[10600]*/
        /** @stable ICU 54 */
        public static final int MAHAJANI_ID = 233; /*[11150]*/
        /** @stable ICU 54 */
        public static final int MANICHAEAN_ID = 234; /*[10AC0]*/
        /** @stable ICU 54 */
        public static final int MENDE_KIKAKUI_ID = 235; /*[1E800]*/
        /** @stable ICU 54 */
        public static final int MODI_ID = 236; /*[11600]*/
        /** @stable ICU 54 */
        public static final int MRO_ID = 237; /*[16A40]*/
        /** @stable ICU 54 */
        public static final int MYANMAR_EXTENDED_B_ID = 238; /*[A9E0]*/
        /** @stable ICU 54 */
        public static final int NABATAEAN_ID = 239; /*[10880]*/
        /** @stable ICU 54 */
        public static final int OLD_NORTH_ARABIAN_ID = 240; /*[10A80]*/
        /** @stable ICU 54 */
        public static final int OLD_PERMIC_ID = 241; /*[10350]*/
        /** @stable ICU 54 */
        public static final int ORNAMENTAL_DINGBATS_ID = 242; /*[1F650]*/
        /** @stable ICU 54 */
        public static final int PAHAWH_HMONG_ID = 243; /*[16B00]*/
        /** @stable ICU 54 */
        public static final int PALMYRENE_ID = 244; /*[10860]*/
        /** @stable ICU 54 */
        public static final int PAU_CIN_HAU_ID = 245; /*[11AC0]*/
        /** @stable ICU 54 */
        public static final int PSALTER_PAHLAVI_ID = 246; /*[10B80]*/
        /** @stable ICU 54 */
        public static final int SHORTHAND_FORMAT_CONTROLS_ID = 247; /*[1BCA0]*/
        /** @stable ICU 54 */
        public static final int SIDDHAM_ID = 248; /*[11580]*/
        /** @stable ICU 54 */
        public static final int SINHALA_ARCHAIC_NUMBERS_ID = 249; /*[111E0]*/
        /** @stable ICU 54 */
        public static final int SUPPLEMENTAL_ARROWS_C_ID = 250; /*[1F800]*/
        /** @stable ICU 54 */
        public static final int TIRHUTA_ID = 251; /*[11480]*/
        /** @stable ICU 54 */
        public static final int WARANG_CITI_ID = 252; /*[118A0]*/

        /* New blocks in Unicode 8.0 */

        /** @stable ICU 56 */
        public static final int AHOM_ID = 253; /*[11700]*/
        /** @stable ICU 56 */
        public static final int ANATOLIAN_HIEROGLYPHS_ID = 254; /*[14400]*/
        /** @stable ICU 56 */
        public static final int CHEROKEE_SUPPLEMENT_ID = 255; /*[AB70]*/
        /** @stable ICU 56 */
        public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E_ID = 256; /*[2B820]*/
        /** @stable ICU 56 */
        public static final int EARLY_DYNASTIC_CUNEIFORM_ID = 257; /*[12480]*/
        /** @stable ICU 56 */
        public static final int HATRAN_ID = 258; /*[108E0]*/
        /** @stable ICU 56 */
        public static final int MULTANI_ID = 259; /*[11280]*/
        /** @stable ICU 56 */
        public static final int OLD_HUNGARIAN_ID = 260; /*[10C80]*/
        /** @stable ICU 56 */
        public static final int SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS_ID = 261; /*[1F900]*/
        /** @stable ICU 56 */
        public static final int SUTTON_SIGNWRITING_ID = 262; /*[1D800]*/

        /* New blocks in Unicode 9.0 */

        /** @stable ICU 58 */
        public static final int ADLAM_ID = 263; /*[1E900]*/
        /** @stable ICU 58 */
        public static final int BHAIKSUKI_ID = 264; /*[11C00]*/
        /** @stable ICU 58 */
        public static final int CYRILLIC_EXTENDED_C_ID = 265; /*[1C80]*/
        /** @stable ICU 58 */
        public static final int GLAGOLITIC_SUPPLEMENT_ID = 266; /*[1E000]*/
        /** @stable ICU 58 */
        public static final int IDEOGRAPHIC_SYMBOLS_AND_PUNCTUATION_ID = 267; /*[16FE0]*/
        /** @stable ICU 58 */
        public static final int MARCHEN_ID = 268; /*[11C70]*/
        /** @stable ICU 58 */
        public static final int MONGOLIAN_SUPPLEMENT_ID = 269; /*[11660]*/
        /** @stable ICU 58 */
        public static final int NEWA_ID = 270; /*[11400]*/
        /** @stable ICU 58 */
        public static final int OSAGE_ID = 271; /*[104B0]*/
        /** @stable ICU 58 */
        public static final int TANGUT_ID = 272; /*[17000]*/
        /** @stable ICU 58 */
        public static final int TANGUT_COMPONENTS_ID = 273; /*[18800]*/

        // New blocks in Unicode 10.0

        /** @stable ICU 60 */
        public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F_ID = 274; /*[2CEB0]*/
        /** @stable ICU 60 */
        public static final int KANA_EXTENDED_A_ID = 275; /*[1B100]*/
        /** @stable ICU 60 */
        public static final int MASARAM_GONDI_ID = 276; /*[11D00]*/
        /** @stable ICU 60 */
        public static final int NUSHU_ID = 277; /*[1B170]*/
        /** @stable ICU 60 */
        public static final int SOYOMBO_ID = 278; /*[11A50]*/
        /** @stable ICU 60 */
        public static final int SYRIAC_SUPPLEMENT_ID = 279; /*[0860]*/
        /** @stable ICU 60 */
        public static final int ZANABAZAR_SQUARE_ID = 280; /*[11A00]*/

        // New blocks in Unicode 11.0

        /** @stable ICU 62 */
        public static final int CHESS_SYMBOLS_ID = 281; /*[1FA00]*/
        /** @stable ICU 62 */
        public static final int DOGRA_ID = 282; /*[11800]*/
        /** @stable ICU 62 */
        public static final int GEORGIAN_EXTENDED_ID = 283; /*[1C90]*/
        /** @stable ICU 62 */
        public static final int GUNJALA_GONDI_ID = 284; /*[11D60]*/
        /** @stable ICU 62 */
        public static final int HANIFI_ROHINGYA_ID = 285; /*[10D00]*/
        /** @stable ICU 62 */
        public static final int INDIC_SIYAQ_NUMBERS_ID = 286; /*[1EC70]*/
        /** @stable ICU 62 */
        public static final int MAKASAR_ID = 287; /*[11EE0]*/
        /** @stable ICU 62 */
        public static final int MAYAN_NUMERALS_ID = 288; /*[1D2E0]*/
        /** @stable ICU 62 */
        public static final int MEDEFAIDRIN_ID = 289; /*[16E40]*/
        /** @stable ICU 62 */
        public static final int OLD_SOGDIAN_ID = 290; /*[10F00]*/
        /** @stable ICU 62 */
        public static final int SOGDIAN_ID = 291; /*[10F30]*/

        // New blocks in Unicode 12.0

        /** @stable ICU 64 */
        public static final int EGYPTIAN_HIEROGLYPH_FORMAT_CONTROLS_ID = 292; /*[13430]*/
        /** @stable ICU 64 */
        public static final int ELYMAIC_ID = 293; /*[10FE0]*/
        /** @stable ICU 64 */
        public static final int NANDINAGARI_ID = 294; /*[119A0]*/
        /** @stable ICU 64 */
        public static final int NYIAKENG_PUACHUE_HMONG_ID = 295; /*[1E100]*/
        /** @stable ICU 64 */
        public static final int OTTOMAN_SIYAQ_NUMBERS_ID = 296; /*[1ED00]*/
        /** @stable ICU 64 */
        public static final int SMALL_KANA_EXTENSION_ID = 297; /*[1B130]*/
        /** @stable ICU 64 */
        public static final int SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A_ID = 298; /*[1FA70]*/
        /** @stable ICU 64 */
        public static final int TAMIL_SUPPLEMENT_ID = 299; /*[11FC0]*/
        /** @stable ICU 64 */
        public static final int WANCHO_ID = 300; /*[1E2C0]*/

        // New blocks in Unicode 13.0

        /** @stable ICU 66 */
        public static final int CHORASMIAN_ID = 301; /*[10FB0]*/
        /** @stable ICU 66 */
        public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_G_ID = 302; /*[30000]*/
        /** @stable ICU 66 */
        public static final int DIVES_AKURU_ID = 303; /*[11900]*/
        /** @stable ICU 66 */
        public static final int KHITAN_SMALL_SCRIPT_ID = 304; /*[18B00]*/
        /** @stable ICU 66 */
        public static final int LISU_SUPPLEMENT_ID = 305; /*[11FB0]*/
        /** @stable ICU 66 */
        public static final int SYMBOLS_FOR_LEGACY_COMPUTING_ID = 306; /*[1FB00]*/
        /** @stable ICU 66 */
        public static final int TANGUT_SUPPLEMENT_ID = 307; /*[18D00]*/
        /** @stable ICU 66 */
        public static final int YEZIDI_ID = 308; /*[10E80]*/

        // New blocks in Unicode 14.0

        /** @stable ICU 70 */
        public static final int ARABIC_EXTENDED_B_ID = 309; /*[0870]*/
        /** @stable ICU 70 */
        public static final int CYPRO_MINOAN_ID = 310; /*[12F90]*/
        /** @stable ICU 70 */
        public static final int ETHIOPIC_EXTENDED_B_ID = 311; /*[1E7E0]*/
        /** @stable ICU 70 */
        public static final int KANA_EXTENDED_B_ID = 312; /*[1AFF0]*/
        /** @stable ICU 70 */
        public static final int LATIN_EXTENDED_F_ID = 313; /*[10780]*/
        /** @stable ICU 70 */
        public static final int LATIN_EXTENDED_G_ID = 314; /*[1DF00]*/
        /** @stable ICU 70 */
        public static final int OLD_UYGHUR_ID = 315; /*[10F70]*/
        /** @stable ICU 70 */
        public static final int TANGSA_ID = 316; /*[16A70]*/
        /** @stable ICU 70 */
        public static final int TOTO_ID = 317; /*[1E290]*/
        /** @stable ICU 70 */
        public static final int UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED_A_ID = 318; /*[11AB0]*/
        /** @stable ICU 70 */
        public static final int VITHKUQI_ID = 319; /*[10570]*/
        /** @stable ICU 70 */
        public static final int ZNAMENNY_MUSICAL_NOTATION_ID = 320; /*[1CF00]*/

        // New blocks in Unicode 15.0

        /** @stable ICU 72 */
        public static final int ARABIC_EXTENDED_C_ID = 321; /*[10EC0]*/
        /** @stable ICU 72 */
        public static final int CJK_UNIFIED_IDEOGRAPHS_EXTENSION_H_ID = 322; /*[31350]*/
        /** @stable ICU 72 */
        public static final int CYRILLIC_EXTENDED_D_ID = 323; /*[1E030]*/
        /** @stable ICU 72 */
        public static final int DEVANAGARI_EXTENDED_A_ID = 324; /*[11B00]*/
        /** @stable ICU 72 */
        public static final int KAKTOVIK_NUMERALS_ID = 325; /*[1D2C0]*/
        /** @stable ICU 72 */
        public static final int KAWI_ID = 326; /*[11F00]*/
        /** @stable ICU 72 */
        public static final int NAG_MUNDARI_ID = 327; /*[1E4D0]*/

        /**
         * One more than the highest normal UnicodeBlock value.
         * The highest value is available via UCharacter.getIntPropertyMaxValue(UProperty.BLOCK).
         *
         * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
         */
        @Deprecated
        public static final int COUNT = 328;

        // blocks objects ---------------------------------------------------

        /**
         * Array of UnicodeBlocks, for easy access in getInstance(int)
         */
        private final static UnicodeBlock BLOCKS_[] = new UnicodeBlock[COUNT];

        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock NO_BLOCK
        = new UnicodeBlock("NO_BLOCK", 0);

        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock BASIC_LATIN
        = new UnicodeBlock("BASIC_LATIN", BASIC_LATIN_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock LATIN_1_SUPPLEMENT
        = new UnicodeBlock("LATIN_1_SUPPLEMENT", LATIN_1_SUPPLEMENT_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock LATIN_EXTENDED_A
        = new UnicodeBlock("LATIN_EXTENDED_A", LATIN_EXTENDED_A_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock LATIN_EXTENDED_B
        = new UnicodeBlock("LATIN_EXTENDED_B", LATIN_EXTENDED_B_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock IPA_EXTENSIONS
        = new UnicodeBlock("IPA_EXTENSIONS", IPA_EXTENSIONS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock SPACING_MODIFIER_LETTERS
        = new UnicodeBlock("SPACING_MODIFIER_LETTERS", SPACING_MODIFIER_LETTERS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock COMBINING_DIACRITICAL_MARKS
        = new UnicodeBlock("COMBINING_DIACRITICAL_MARKS", COMBINING_DIACRITICAL_MARKS_ID);
        /**
         * Unicode 3.2 renames this block to "Greek and Coptic".
         * @stable ICU 2.4
         */
        public static final UnicodeBlock GREEK
        = new UnicodeBlock("GREEK", GREEK_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock CYRILLIC
        = new UnicodeBlock("CYRILLIC", CYRILLIC_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock ARMENIAN
        = new UnicodeBlock("ARMENIAN", ARMENIAN_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock HEBREW
        = new UnicodeBlock("HEBREW", HEBREW_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock ARABIC
        = new UnicodeBlock("ARABIC", ARABIC_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock SYRIAC
        = new UnicodeBlock("SYRIAC", SYRIAC_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock THAANA
        = new UnicodeBlock("THAANA", THAANA_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock DEVANAGARI
        = new UnicodeBlock("DEVANAGARI", DEVANAGARI_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock BENGALI
        = new UnicodeBlock("BENGALI", BENGALI_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock GURMUKHI
        = new UnicodeBlock("GURMUKHI", GURMUKHI_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock GUJARATI
        = new UnicodeBlock("GUJARATI", GUJARATI_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock ORIYA
        = new UnicodeBlock("ORIYA", ORIYA_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock TAMIL
        = new UnicodeBlock("TAMIL", TAMIL_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock TELUGU
        = new UnicodeBlock("TELUGU", TELUGU_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock KANNADA
        = new UnicodeBlock("KANNADA", KANNADA_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock MALAYALAM
        = new UnicodeBlock("MALAYALAM", MALAYALAM_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock SINHALA
        = new UnicodeBlock("SINHALA", SINHALA_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock THAI
        = new UnicodeBlock("THAI", THAI_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock LAO
        = new UnicodeBlock("LAO", LAO_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock TIBETAN
        = new UnicodeBlock("TIBETAN", TIBETAN_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock MYANMAR
        = new UnicodeBlock("MYANMAR", MYANMAR_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock GEORGIAN
        = new UnicodeBlock("GEORGIAN", GEORGIAN_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock HANGUL_JAMO
        = new UnicodeBlock("HANGUL_JAMO", HANGUL_JAMO_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock ETHIOPIC
        = new UnicodeBlock("ETHIOPIC", ETHIOPIC_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock CHEROKEE
        = new UnicodeBlock("CHEROKEE", CHEROKEE_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS
        = new UnicodeBlock("UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS",
                UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock OGHAM
        = new UnicodeBlock("OGHAM", OGHAM_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock RUNIC
        = new UnicodeBlock("RUNIC", RUNIC_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock KHMER
        = new UnicodeBlock("KHMER", KHMER_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock MONGOLIAN
        = new UnicodeBlock("MONGOLIAN", MONGOLIAN_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock LATIN_EXTENDED_ADDITIONAL
        = new UnicodeBlock("LATIN_EXTENDED_ADDITIONAL", LATIN_EXTENDED_ADDITIONAL_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock GREEK_EXTENDED
        = new UnicodeBlock("GREEK_EXTENDED", GREEK_EXTENDED_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock GENERAL_PUNCTUATION
        = new UnicodeBlock("GENERAL_PUNCTUATION", GENERAL_PUNCTUATION_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock SUPERSCRIPTS_AND_SUBSCRIPTS
        = new UnicodeBlock("SUPERSCRIPTS_AND_SUBSCRIPTS", SUPERSCRIPTS_AND_SUBSCRIPTS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock CURRENCY_SYMBOLS
        = new UnicodeBlock("CURRENCY_SYMBOLS", CURRENCY_SYMBOLS_ID);
        /**
         * Unicode 3.2 renames this block to "Combining Diacritical Marks for
         * Symbols".
         * @stable ICU 2.4
         */
        public static final UnicodeBlock COMBINING_MARKS_FOR_SYMBOLS
        = new UnicodeBlock("COMBINING_MARKS_FOR_SYMBOLS", COMBINING_MARKS_FOR_SYMBOLS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock LETTERLIKE_SYMBOLS
        = new UnicodeBlock("LETTERLIKE_SYMBOLS", LETTERLIKE_SYMBOLS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock NUMBER_FORMS
        = new UnicodeBlock("NUMBER_FORMS", NUMBER_FORMS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock ARROWS
        = new UnicodeBlock("ARROWS", ARROWS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock MATHEMATICAL_OPERATORS
        = new UnicodeBlock("MATHEMATICAL_OPERATORS", MATHEMATICAL_OPERATORS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock MISCELLANEOUS_TECHNICAL
        = new UnicodeBlock("MISCELLANEOUS_TECHNICAL", MISCELLANEOUS_TECHNICAL_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock CONTROL_PICTURES
        = new UnicodeBlock("CONTROL_PICTURES", CONTROL_PICTURES_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock OPTICAL_CHARACTER_RECOGNITION
        = new UnicodeBlock("OPTICAL_CHARACTER_RECOGNITION", OPTICAL_CHARACTER_RECOGNITION_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock ENCLOSED_ALPHANUMERICS
        = new UnicodeBlock("ENCLOSED_ALPHANUMERICS", ENCLOSED_ALPHANUMERICS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock BOX_DRAWING
        = new UnicodeBlock("BOX_DRAWING", BOX_DRAWING_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock BLOCK_ELEMENTS
        = new UnicodeBlock("BLOCK_ELEMENTS", BLOCK_ELEMENTS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock GEOMETRIC_SHAPES
        = new UnicodeBlock("GEOMETRIC_SHAPES", GEOMETRIC_SHAPES_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock MISCELLANEOUS_SYMBOLS
        = new UnicodeBlock("MISCELLANEOUS_SYMBOLS", MISCELLANEOUS_SYMBOLS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock DINGBATS
        = new UnicodeBlock("DINGBATS", DINGBATS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock BRAILLE_PATTERNS
        = new UnicodeBlock("BRAILLE_PATTERNS", BRAILLE_PATTERNS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock CJK_RADICALS_SUPPLEMENT
        = new UnicodeBlock("CJK_RADICALS_SUPPLEMENT", CJK_RADICALS_SUPPLEMENT_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock KANGXI_RADICALS
        = new UnicodeBlock("KANGXI_RADICALS", KANGXI_RADICALS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock IDEOGRAPHIC_DESCRIPTION_CHARACTERS
        = new UnicodeBlock("IDEOGRAPHIC_DESCRIPTION_CHARACTERS",
                IDEOGRAPHIC_DESCRIPTION_CHARACTERS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock CJK_SYMBOLS_AND_PUNCTUATION
        = new UnicodeBlock("CJK_SYMBOLS_AND_PUNCTUATION", CJK_SYMBOLS_AND_PUNCTUATION_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock HIRAGANA
        = new UnicodeBlock("HIRAGANA", HIRAGANA_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock KATAKANA
        = new UnicodeBlock("KATAKANA", KATAKANA_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock BOPOMOFO
        = new UnicodeBlock("BOPOMOFO", BOPOMOFO_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock HANGUL_COMPATIBILITY_JAMO
        = new UnicodeBlock("HANGUL_COMPATIBILITY_JAMO", HANGUL_COMPATIBILITY_JAMO_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock KANBUN
        = new UnicodeBlock("KANBUN", KANBUN_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock BOPOMOFO_EXTENDED
        = new UnicodeBlock("BOPOMOFO_EXTENDED", BOPOMOFO_EXTENDED_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock ENCLOSED_CJK_LETTERS_AND_MONTHS
        = new UnicodeBlock("ENCLOSED_CJK_LETTERS_AND_MONTHS",
                ENCLOSED_CJK_LETTERS_AND_MONTHS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock CJK_COMPATIBILITY
        = new UnicodeBlock("CJK_COMPATIBILITY", CJK_COMPATIBILITY_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
        = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A",
                CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS
        = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS", CJK_UNIFIED_IDEOGRAPHS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock YI_SYLLABLES
        = new UnicodeBlock("YI_SYLLABLES", YI_SYLLABLES_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock YI_RADICALS
        = new UnicodeBlock("YI_RADICALS", YI_RADICALS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock HANGUL_SYLLABLES
        = new UnicodeBlock("HANGUL_SYLLABLES", HANGUL_SYLLABLES_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock HIGH_SURROGATES
        = new UnicodeBlock("HIGH_SURROGATES", HIGH_SURROGATES_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock HIGH_PRIVATE_USE_SURROGATES
        = new UnicodeBlock("HIGH_PRIVATE_USE_SURROGATES", HIGH_PRIVATE_USE_SURROGATES_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock LOW_SURROGATES
        = new UnicodeBlock("LOW_SURROGATES", LOW_SURROGATES_ID);
        /**
         * Same as public static final int PRIVATE_USE.
         * Until Unicode 3.1.1; the corresponding block name was "Private Use";
         * and multiple code point ranges had this block.
         * Unicode 3.2 renames the block for the BMP PUA to "Private Use Area"
         * and adds separate blocks for the supplementary PUAs.
         * @stable ICU 2.4
         */
        public static final UnicodeBlock PRIVATE_USE_AREA
        = new UnicodeBlock("PRIVATE_USE_AREA",  78);
        /**
         * Same as public static final int PRIVATE_USE_AREA.
         * Until Unicode 3.1.1; the corresponding block name was "Private Use";
         * and multiple code point ranges had this block.
         * Unicode 3.2 renames the block for the BMP PUA to "Private Use Area"
         * and adds separate blocks for the supplementary PUAs.
         * @stable ICU 2.4
         */
        public static final UnicodeBlock PRIVATE_USE
        = PRIVATE_USE_AREA;
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock CJK_COMPATIBILITY_IDEOGRAPHS
        = new UnicodeBlock("CJK_COMPATIBILITY_IDEOGRAPHS", CJK_COMPATIBILITY_IDEOGRAPHS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock ALPHABETIC_PRESENTATION_FORMS
        = new UnicodeBlock("ALPHABETIC_PRESENTATION_FORMS", ALPHABETIC_PRESENTATION_FORMS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock ARABIC_PRESENTATION_FORMS_A
        = new UnicodeBlock("ARABIC_PRESENTATION_FORMS_A", ARABIC_PRESENTATION_FORMS_A_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock COMBINING_HALF_MARKS
        = new UnicodeBlock("COMBINING_HALF_MARKS", COMBINING_HALF_MARKS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock CJK_COMPATIBILITY_FORMS
        = new UnicodeBlock("CJK_COMPATIBILITY_FORMS", CJK_COMPATIBILITY_FORMS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock SMALL_FORM_VARIANTS
        = new UnicodeBlock("SMALL_FORM_VARIANTS", SMALL_FORM_VARIANTS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock ARABIC_PRESENTATION_FORMS_B
        = new UnicodeBlock("ARABIC_PRESENTATION_FORMS_B", ARABIC_PRESENTATION_FORMS_B_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock SPECIALS
        = new UnicodeBlock("SPECIALS", SPECIALS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock HALFWIDTH_AND_FULLWIDTH_FORMS
        = new UnicodeBlock("HALFWIDTH_AND_FULLWIDTH_FORMS", HALFWIDTH_AND_FULLWIDTH_FORMS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock OLD_ITALIC
        = new UnicodeBlock("OLD_ITALIC", OLD_ITALIC_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock GOTHIC
        = new UnicodeBlock("GOTHIC", GOTHIC_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock DESERET
        = new UnicodeBlock("DESERET", DESERET_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock BYZANTINE_MUSICAL_SYMBOLS
        = new UnicodeBlock("BYZANTINE_MUSICAL_SYMBOLS", BYZANTINE_MUSICAL_SYMBOLS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock MUSICAL_SYMBOLS
        = new UnicodeBlock("MUSICAL_SYMBOLS", MUSICAL_SYMBOLS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock MATHEMATICAL_ALPHANUMERIC_SYMBOLS
        = new UnicodeBlock("MATHEMATICAL_ALPHANUMERIC_SYMBOLS",
                MATHEMATICAL_ALPHANUMERIC_SYMBOLS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
        = new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B",
                CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock
        CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
        = new UnicodeBlock("CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT",
                CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock TAGS
        = new UnicodeBlock("TAGS", TAGS_ID);

        // New blocks in Unicode 3.2

        /**
         * Unicode 4.0.1 renames the "Cyrillic Supplementary" block to "Cyrillic Supplement".
         * @stable ICU 2.4
         */
        public static final UnicodeBlock CYRILLIC_SUPPLEMENTARY
        = new UnicodeBlock("CYRILLIC_SUPPLEMENTARY", CYRILLIC_SUPPLEMENTARY_ID);
        /**
         * Unicode 4.0.1 renames the "Cyrillic Supplementary" block to "Cyrillic Supplement".
         * @stable ICU 3.0
         */
        public static final UnicodeBlock CYRILLIC_SUPPLEMENT
        = new UnicodeBlock("CYRILLIC_SUPPLEMENT", CYRILLIC_SUPPLEMENT_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock TAGALOG
        = new UnicodeBlock("TAGALOG", TAGALOG_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock HANUNOO
        = new UnicodeBlock("HANUNOO", HANUNOO_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock BUHID
        = new UnicodeBlock("BUHID", BUHID_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock TAGBANWA
        = new UnicodeBlock("TAGBANWA", TAGBANWA_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A
        = new UnicodeBlock("MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A",
                MISCELLANEOUS_MATHEMATICAL_SYMBOLS_A_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock SUPPLEMENTAL_ARROWS_A
        = new UnicodeBlock("SUPPLEMENTAL_ARROWS_A", SUPPLEMENTAL_ARROWS_A_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock SUPPLEMENTAL_ARROWS_B
        = new UnicodeBlock("SUPPLEMENTAL_ARROWS_B", SUPPLEMENTAL_ARROWS_B_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B
        = new UnicodeBlock("MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B",
                MISCELLANEOUS_MATHEMATICAL_SYMBOLS_B_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock SUPPLEMENTAL_MATHEMATICAL_OPERATORS
        = new UnicodeBlock("SUPPLEMENTAL_MATHEMATICAL_OPERATORS",
                SUPPLEMENTAL_MATHEMATICAL_OPERATORS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock KATAKANA_PHONETIC_EXTENSIONS
        = new UnicodeBlock("KATAKANA_PHONETIC_EXTENSIONS", KATAKANA_PHONETIC_EXTENSIONS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock VARIATION_SELECTORS
        = new UnicodeBlock("VARIATION_SELECTORS", VARIATION_SELECTORS_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock SUPPLEMENTARY_PRIVATE_USE_AREA_A
        = new UnicodeBlock("SUPPLEMENTARY_PRIVATE_USE_AREA_A",
                SUPPLEMENTARY_PRIVATE_USE_AREA_A_ID);
        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock SUPPLEMENTARY_PRIVATE_USE_AREA_B
        = new UnicodeBlock("SUPPLEMENTARY_PRIVATE_USE_AREA_B",
                SUPPLEMENTARY_PRIVATE_USE_AREA_B_ID);

        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock LIMBU
        = new UnicodeBlock("LIMBU", LIMBU_ID);
        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock TAI_LE
        = new UnicodeBlock("TAI_LE", TAI_LE_ID);
        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock KHMER_SYMBOLS
        = new UnicodeBlock("KHMER_SYMBOLS", KHMER_SYMBOLS_ID);

        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock PHONETIC_EXTENSIONS
        = new UnicodeBlock("PHONETIC_EXTENSIONS", PHONETIC_EXTENSIONS_ID);

        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock MISCELLANEOUS_SYMBOLS_AND_ARROWS
        = new UnicodeBlock("MISCELLANEOUS_SYMBOLS_AND_ARROWS",
                MISCELLANEOUS_SYMBOLS_AND_ARROWS_ID);
        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock YIJING_HEXAGRAM_SYMBOLS
        = new UnicodeBlock("YIJING_HEXAGRAM_SYMBOLS", YIJING_HEXAGRAM_SYMBOLS_ID);
        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock LINEAR_B_SYLLABARY
        = new UnicodeBlock("LINEAR_B_SYLLABARY", LINEAR_B_SYLLABARY_ID);
        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock LINEAR_B_IDEOGRAMS
        = new UnicodeBlock("LINEAR_B_IDEOGRAMS", LINEAR_B_IDEOGRAMS_ID);
        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock AEGEAN_NUMBERS
        = new UnicodeBlock("AEGEAN_NUMBERS", AEGEAN_NUMBERS_ID);
        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock UGARITIC
        = new UnicodeBlock("UGARITIC", UGARITIC_ID);
        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock SHAVIAN
        = new UnicodeBlock("SHAVIAN", SHAVIAN_ID);
        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock OSMANYA
        = new UnicodeBlock("OSMANYA", OSMANYA_ID);
        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock CYPRIOT_SYLLABARY
        = new UnicodeBlock("CYPRIOT_SYLLABARY", CYPRIOT_SYLLABARY_ID);
        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock TAI_XUAN_JING_SYMBOLS
        = new UnicodeBlock("TAI_XUAN_JING_SYMBOLS", TAI_XUAN_JING_SYMBOLS_ID);

        /**
         * @stable ICU 2.6
         */
        public static final UnicodeBlock VARIATION_SELECTORS_SUPPLEMENT
        = new UnicodeBlock("VARIATION_SELECTORS_SUPPLEMENT", VARIATION_SELECTORS_SUPPLEMENT_ID);

        /* New blocks in Unicode 4.1 */

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock ANCIENT_GREEK_MUSICAL_NOTATION =
                new UnicodeBlock("ANCIENT_GREEK_MUSICAL_NOTATION",
                        ANCIENT_GREEK_MUSICAL_NOTATION_ID); /*[1D200]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock ANCIENT_GREEK_NUMBERS =
                new UnicodeBlock("ANCIENT_GREEK_NUMBERS", ANCIENT_GREEK_NUMBERS_ID); /*[10140]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock ARABIC_SUPPLEMENT =
                new UnicodeBlock("ARABIC_SUPPLEMENT", ARABIC_SUPPLEMENT_ID); /*[0750]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock BUGINESE =
                new UnicodeBlock("BUGINESE", BUGINESE_ID); /*[1A00]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock CJK_STROKES =
                new UnicodeBlock("CJK_STROKES", CJK_STROKES_ID); /*[31C0]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock COMBINING_DIACRITICAL_MARKS_SUPPLEMENT =
                new UnicodeBlock("COMBINING_DIACRITICAL_MARKS_SUPPLEMENT",
                        COMBINING_DIACRITICAL_MARKS_SUPPLEMENT_ID); /*[1DC0]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock COPTIC = new UnicodeBlock("COPTIC", COPTIC_ID); /*[2C80]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock ETHIOPIC_EXTENDED =
                new UnicodeBlock("ETHIOPIC_EXTENDED", ETHIOPIC_EXTENDED_ID); /*[2D80]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock ETHIOPIC_SUPPLEMENT =
                new UnicodeBlock("ETHIOPIC_SUPPLEMENT", ETHIOPIC_SUPPLEMENT_ID); /*[1380]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock GEORGIAN_SUPPLEMENT =
                new UnicodeBlock("GEORGIAN_SUPPLEMENT", GEORGIAN_SUPPLEMENT_ID); /*[2D00]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock GLAGOLITIC =
                new UnicodeBlock("GLAGOLITIC", GLAGOLITIC_ID); /*[2C00]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock KHAROSHTHI =
                new UnicodeBlock("KHAROSHTHI", KHAROSHTHI_ID); /*[10A00]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock MODIFIER_TONE_LETTERS =
                new UnicodeBlock("MODIFIER_TONE_LETTERS", MODIFIER_TONE_LETTERS_ID); /*[A700]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock NEW_TAI_LUE =
                new UnicodeBlock("NEW_TAI_LUE", NEW_TAI_LUE_ID); /*[1980]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock OLD_PERSIAN =
                new UnicodeBlock("OLD_PERSIAN", OLD_PERSIAN_ID); /*[103A0]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock PHONETIC_EXTENSIONS_SUPPLEMENT =
                new UnicodeBlock("PHONETIC_EXTENSIONS_SUPPLEMENT",
                        PHONETIC_EXTENSIONS_SUPPLEMENT_ID); /*[1D80]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock SUPPLEMENTAL_PUNCTUATION =
                new UnicodeBlock("SUPPLEMENTAL_PUNCTUATION", SUPPLEMENTAL_PUNCTUATION_ID); /*[2E00]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock SYLOTI_NAGRI =
                new UnicodeBlock("SYLOTI_NAGRI", SYLOTI_NAGRI_ID); /*[A800]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock TIFINAGH =
                new UnicodeBlock("TIFINAGH", TIFINAGH_ID); /*[2D30]*/

        /**
         * @stable ICU 3.4
         */
        public static final UnicodeBlock VERTICAL_FORMS =
                new UnicodeBlock("VERTICAL_FORMS", VERTICAL_FORMS_ID); /*[FE10]*/

        /**
         * @stable ICU 3.6
         */
        public static final UnicodeBlock NKO = new UnicodeBlock("NKO", NKO_ID); /*[07C0]*/
        /**
         * @stable ICU 3.6
         */
        public static final UnicodeBlock BALINESE =
                new UnicodeBlock("BALINESE", BALINESE_ID); /*[1B00]*/
        /**
         * @stable ICU 3.6
         */
        public static final UnicodeBlock LATIN_EXTENDED_C =
                new UnicodeBlock("LATIN_EXTENDED_C", LATIN_EXTENDED_C_ID); /*[2C60]*/
        /**
         * @stable ICU 3.6
         */
        public static final UnicodeBlock LATIN_EXTENDED_D =
                new UnicodeBlock("LATIN_EXTENDED_D", LATIN_EXTENDED_D_ID); /*[A720]*/
        /**
         * @stable ICU 3.6
         */
        public static final UnicodeBlock PHAGS_PA =
                new UnicodeBlock("PHAGS_PA", PHAGS_PA_ID); /*[A840]*/
        /**
         * @stable ICU 3.6
         */
        public static final UnicodeBlock PHOENICIAN =
                new UnicodeBlock("PHOENICIAN", PHOENICIAN_ID); /*[10900]*/
        /**
         * @stable ICU 3.6
         */
        public static final UnicodeBlock CUNEIFORM =
                new UnicodeBlock("CUNEIFORM", CUNEIFORM_ID); /*[12000]*/
        /**
         * @stable ICU 3.6
         */
        public static final UnicodeBlock CUNEIFORM_NUMBERS_AND_PUNCTUATION =
                new UnicodeBlock("CUNEIFORM_NUMBERS_AND_PUNCTUATION",
                        CUNEIFORM_NUMBERS_AND_PUNCTUATION_ID); /*[12400]*/
        /**
         * @stable ICU 3.6
         */
        public static final UnicodeBlock COUNTING_ROD_NUMERALS =
                new UnicodeBlock("COUNTING_ROD_NUMERALS", COUNTING_ROD_NUMERALS_ID); /*[1D360]*/

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock SUNDANESE =
                new UnicodeBlock("SUNDANESE", SUNDANESE_ID); /* [1B80] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock LEPCHA =
                new UnicodeBlock("LEPCHA", LEPCHA_ID); /* [1C00] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock OL_CHIKI =
                new UnicodeBlock("OL_CHIKI", OL_CHIKI_ID); /* [1C50] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock CYRILLIC_EXTENDED_A =
                new UnicodeBlock("CYRILLIC_EXTENDED_A", CYRILLIC_EXTENDED_A_ID); /* [2DE0] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock VAI = new UnicodeBlock("VAI", VAI_ID); /* [A500] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock CYRILLIC_EXTENDED_B =
                new UnicodeBlock("CYRILLIC_EXTENDED_B", CYRILLIC_EXTENDED_B_ID); /* [A640] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock SAURASHTRA =
                new UnicodeBlock("SAURASHTRA", SAURASHTRA_ID); /* [A880] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock KAYAH_LI =
                new UnicodeBlock("KAYAH_LI", KAYAH_LI_ID); /* [A900] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock REJANG =
                new UnicodeBlock("REJANG", REJANG_ID); /* [A930] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock CHAM =
                new UnicodeBlock("CHAM", CHAM_ID); /* [AA00] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock ANCIENT_SYMBOLS =
                new UnicodeBlock("ANCIENT_SYMBOLS", ANCIENT_SYMBOLS_ID); /* [10190] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock PHAISTOS_DISC =
                new UnicodeBlock("PHAISTOS_DISC", PHAISTOS_DISC_ID); /* [101D0] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock LYCIAN =
                new UnicodeBlock("LYCIAN", LYCIAN_ID); /* [10280] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock CARIAN =
                new UnicodeBlock("CARIAN", CARIAN_ID); /* [102A0] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock LYDIAN =
                new UnicodeBlock("LYDIAN", LYDIAN_ID); /* [10920] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock MAHJONG_TILES =
                new UnicodeBlock("MAHJONG_TILES", MAHJONG_TILES_ID); /* [1F000] */

        /**
         * @stable ICU 4.0
         */
        public static final UnicodeBlock DOMINO_TILES =
                new UnicodeBlock("DOMINO_TILES", DOMINO_TILES_ID); /* [1F030] */

        /* New blocks in Unicode 5.2 */

        /** @stable ICU 4.4 */
        public static final UnicodeBlock SAMARITAN =
                new UnicodeBlock("SAMARITAN", SAMARITAN_ID); /*[0800]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED =
                new UnicodeBlock("UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED",
                        UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED_ID); /*[18B0]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock TAI_THAM =
                new UnicodeBlock("TAI_THAM", TAI_THAM_ID); /*[1A20]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock VEDIC_EXTENSIONS =
                new UnicodeBlock("VEDIC_EXTENSIONS", VEDIC_EXTENSIONS_ID); /*[1CD0]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock LISU =
                new UnicodeBlock("LISU", LISU_ID); /*[A4D0]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock BAMUM =
                new UnicodeBlock("BAMUM", BAMUM_ID); /*[A6A0]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock COMMON_INDIC_NUMBER_FORMS =
                new UnicodeBlock("COMMON_INDIC_NUMBER_FORMS", COMMON_INDIC_NUMBER_FORMS_ID); /*[A830]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock DEVANAGARI_EXTENDED =
                new UnicodeBlock("DEVANAGARI_EXTENDED", DEVANAGARI_EXTENDED_ID); /*[A8E0]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock HANGUL_JAMO_EXTENDED_A =
                new UnicodeBlock("HANGUL_JAMO_EXTENDED_A", HANGUL_JAMO_EXTENDED_A_ID); /*[A960]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock JAVANESE =
                new UnicodeBlock("JAVANESE", JAVANESE_ID); /*[A980]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock MYANMAR_EXTENDED_A =
                new UnicodeBlock("MYANMAR_EXTENDED_A", MYANMAR_EXTENDED_A_ID); /*[AA60]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock TAI_VIET =
                new UnicodeBlock("TAI_VIET", TAI_VIET_ID); /*[AA80]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock MEETEI_MAYEK =
                new UnicodeBlock("MEETEI_MAYEK", MEETEI_MAYEK_ID); /*[ABC0]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock HANGUL_JAMO_EXTENDED_B =
                new UnicodeBlock("HANGUL_JAMO_EXTENDED_B", HANGUL_JAMO_EXTENDED_B_ID); /*[D7B0]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock IMPERIAL_ARAMAIC =
                new UnicodeBlock("IMPERIAL_ARAMAIC", IMPERIAL_ARAMAIC_ID); /*[10840]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock OLD_SOUTH_ARABIAN =
                new UnicodeBlock("OLD_SOUTH_ARABIAN", OLD_SOUTH_ARABIAN_ID); /*[10A60]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock AVESTAN =
                new UnicodeBlock("AVESTAN", AVESTAN_ID); /*[10B00]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock INSCRIPTIONAL_PARTHIAN =
                new UnicodeBlock("INSCRIPTIONAL_PARTHIAN", INSCRIPTIONAL_PARTHIAN_ID); /*[10B40]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock INSCRIPTIONAL_PAHLAVI =
                new UnicodeBlock("INSCRIPTIONAL_PAHLAVI", INSCRIPTIONAL_PAHLAVI_ID); /*[10B60]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock OLD_TURKIC =
                new UnicodeBlock("OLD_TURKIC", OLD_TURKIC_ID); /*[10C00]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock RUMI_NUMERAL_SYMBOLS =
                new UnicodeBlock("RUMI_NUMERAL_SYMBOLS", RUMI_NUMERAL_SYMBOLS_ID); /*[10E60]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock KAITHI =
                new UnicodeBlock("KAITHI", KAITHI_ID); /*[11080]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock EGYPTIAN_HIEROGLYPHS =
                new UnicodeBlock("EGYPTIAN_HIEROGLYPHS", EGYPTIAN_HIEROGLYPHS_ID); /*[13000]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock ENCLOSED_ALPHANUMERIC_SUPPLEMENT =
                new UnicodeBlock("ENCLOSED_ALPHANUMERIC_SUPPLEMENT",
                        ENCLOSED_ALPHANUMERIC_SUPPLEMENT_ID); /*[1F100]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock ENCLOSED_IDEOGRAPHIC_SUPPLEMENT =
                new UnicodeBlock("ENCLOSED_IDEOGRAPHIC_SUPPLEMENT",
                        ENCLOSED_IDEOGRAPHIC_SUPPLEMENT_ID); /*[1F200]*/
        /** @stable ICU 4.4 */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C =
                new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C",
                        CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C_ID); /*[2A700]*/

        /* New blocks in Unicode 6.0 */

        /** @stable ICU 4.6 */
        public static final UnicodeBlock MANDAIC =
                new UnicodeBlock("MANDAIC", MANDAIC_ID); /*[0840]*/
        /** @stable ICU 4.6 */
        public static final UnicodeBlock BATAK =
                new UnicodeBlock("BATAK", BATAK_ID); /*[1BC0]*/
        /** @stable ICU 4.6 */
        public static final UnicodeBlock ETHIOPIC_EXTENDED_A =
                new UnicodeBlock("ETHIOPIC_EXTENDED_A", ETHIOPIC_EXTENDED_A_ID); /*[AB00]*/
        /** @stable ICU 4.6 */
        public static final UnicodeBlock BRAHMI =
                new UnicodeBlock("BRAHMI", BRAHMI_ID); /*[11000]*/
        /** @stable ICU 4.6 */
        public static final UnicodeBlock BAMUM_SUPPLEMENT =
                new UnicodeBlock("BAMUM_SUPPLEMENT", BAMUM_SUPPLEMENT_ID); /*[16800]*/
        /** @stable ICU 4.6 */
        public static final UnicodeBlock KANA_SUPPLEMENT =
                new UnicodeBlock("KANA_SUPPLEMENT", KANA_SUPPLEMENT_ID); /*[1B000]*/
        /** @stable ICU 4.6 */
        public static final UnicodeBlock PLAYING_CARDS =
                new UnicodeBlock("PLAYING_CARDS", PLAYING_CARDS_ID); /*[1F0A0]*/
        /** @stable ICU 4.6 */
        public static final UnicodeBlock MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS =
                new UnicodeBlock("MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS",
                        MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS_ID); /*[1F300]*/
        /** @stable ICU 4.6 */
        public static final UnicodeBlock EMOTICONS =
                new UnicodeBlock("EMOTICONS", EMOTICONS_ID); /*[1F600]*/
        /** @stable ICU 4.6 */
        public static final UnicodeBlock TRANSPORT_AND_MAP_SYMBOLS =
                new UnicodeBlock("TRANSPORT_AND_MAP_SYMBOLS", TRANSPORT_AND_MAP_SYMBOLS_ID); /*[1F680]*/
        /** @stable ICU 4.6 */
        public static final UnicodeBlock ALCHEMICAL_SYMBOLS =
                new UnicodeBlock("ALCHEMICAL_SYMBOLS", ALCHEMICAL_SYMBOLS_ID); /*[1F700]*/
        /** @stable ICU 4.6 */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D =
                new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D",
                        CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D_ID); /*[2B740]*/

        /* New blocks in Unicode 6.1 */

        /** @stable ICU 49 */
        public static final UnicodeBlock ARABIC_EXTENDED_A =
                new UnicodeBlock("ARABIC_EXTENDED_A", ARABIC_EXTENDED_A_ID); /*[08A0]*/
        /** @stable ICU 49 */
        public static final UnicodeBlock ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS =
                new UnicodeBlock("ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS", ARABIC_MATHEMATICAL_ALPHABETIC_SYMBOLS_ID); /*[1EE00]*/
        /** @stable ICU 49 */
        public static final UnicodeBlock CHAKMA = new UnicodeBlock("CHAKMA", CHAKMA_ID); /*[11100]*/
        /** @stable ICU 49 */
        public static final UnicodeBlock MEETEI_MAYEK_EXTENSIONS =
                new UnicodeBlock("MEETEI_MAYEK_EXTENSIONS", MEETEI_MAYEK_EXTENSIONS_ID); /*[AAE0]*/
        /** @stable ICU 49 */
        public static final UnicodeBlock MEROITIC_CURSIVE =
                new UnicodeBlock("MEROITIC_CURSIVE", MEROITIC_CURSIVE_ID); /*[109A0]*/
        /** @stable ICU 49 */
        public static final UnicodeBlock MEROITIC_HIEROGLYPHS =
                new UnicodeBlock("MEROITIC_HIEROGLYPHS", MEROITIC_HIEROGLYPHS_ID); /*[10980]*/
        /** @stable ICU 49 */
        public static final UnicodeBlock MIAO = new UnicodeBlock("MIAO", MIAO_ID); /*[16F00]*/
        /** @stable ICU 49 */
        public static final UnicodeBlock SHARADA = new UnicodeBlock("SHARADA", SHARADA_ID); /*[11180]*/
        /** @stable ICU 49 */
        public static final UnicodeBlock SORA_SOMPENG =
                new UnicodeBlock("SORA_SOMPENG", SORA_SOMPENG_ID); /*[110D0]*/
        /** @stable ICU 49 */
        public static final UnicodeBlock SUNDANESE_SUPPLEMENT =
                new UnicodeBlock("SUNDANESE_SUPPLEMENT", SUNDANESE_SUPPLEMENT_ID); /*[1CC0]*/
        /** @stable ICU 49 */
        public static final UnicodeBlock TAKRI = new UnicodeBlock("TAKRI", TAKRI_ID); /*[11680]*/

        /* New blocks in Unicode 7.0 */

        /** @stable ICU 54 */
        public static final UnicodeBlock BASSA_VAH = new UnicodeBlock("BASSA_VAH", BASSA_VAH_ID); /*[16AD0]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock CAUCASIAN_ALBANIAN =
                new UnicodeBlock("CAUCASIAN_ALBANIAN", CAUCASIAN_ALBANIAN_ID); /*[10530]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock COPTIC_EPACT_NUMBERS =
                new UnicodeBlock("COPTIC_EPACT_NUMBERS", COPTIC_EPACT_NUMBERS_ID); /*[102E0]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock COMBINING_DIACRITICAL_MARKS_EXTENDED =
                new UnicodeBlock("COMBINING_DIACRITICAL_MARKS_EXTENDED", COMBINING_DIACRITICAL_MARKS_EXTENDED_ID); /*[1AB0]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock DUPLOYAN = new UnicodeBlock("DUPLOYAN", DUPLOYAN_ID); /*[1BC00]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock ELBASAN = new UnicodeBlock("ELBASAN", ELBASAN_ID); /*[10500]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock GEOMETRIC_SHAPES_EXTENDED =
                new UnicodeBlock("GEOMETRIC_SHAPES_EXTENDED", GEOMETRIC_SHAPES_EXTENDED_ID); /*[1F780]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock GRANTHA = new UnicodeBlock("GRANTHA", GRANTHA_ID); /*[11300]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock KHOJKI = new UnicodeBlock("KHOJKI", KHOJKI_ID); /*[11200]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock KHUDAWADI = new UnicodeBlock("KHUDAWADI", KHUDAWADI_ID); /*[112B0]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock LATIN_EXTENDED_E =
                new UnicodeBlock("LATIN_EXTENDED_E", LATIN_EXTENDED_E_ID); /*[AB30]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock LINEAR_A = new UnicodeBlock("LINEAR_A", LINEAR_A_ID); /*[10600]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock MAHAJANI = new UnicodeBlock("MAHAJANI", MAHAJANI_ID); /*[11150]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock MANICHAEAN = new UnicodeBlock("MANICHAEAN", MANICHAEAN_ID); /*[10AC0]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock MENDE_KIKAKUI =
                new UnicodeBlock("MENDE_KIKAKUI", MENDE_KIKAKUI_ID); /*[1E800]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock MODI = new UnicodeBlock("MODI", MODI_ID); /*[11600]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock MRO = new UnicodeBlock("MRO", MRO_ID); /*[16A40]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock MYANMAR_EXTENDED_B =
                new UnicodeBlock("MYANMAR_EXTENDED_B", MYANMAR_EXTENDED_B_ID); /*[A9E0]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock NABATAEAN = new UnicodeBlock("NABATAEAN", NABATAEAN_ID); /*[10880]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock OLD_NORTH_ARABIAN =
                new UnicodeBlock("OLD_NORTH_ARABIAN", OLD_NORTH_ARABIAN_ID); /*[10A80]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock OLD_PERMIC = new UnicodeBlock("OLD_PERMIC", OLD_PERMIC_ID); /*[10350]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock ORNAMENTAL_DINGBATS =
                new UnicodeBlock("ORNAMENTAL_DINGBATS", ORNAMENTAL_DINGBATS_ID); /*[1F650]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock PAHAWH_HMONG = new UnicodeBlock("PAHAWH_HMONG", PAHAWH_HMONG_ID); /*[16B00]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock PALMYRENE = new UnicodeBlock("PALMYRENE", PALMYRENE_ID); /*[10860]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock PAU_CIN_HAU = new UnicodeBlock("PAU_CIN_HAU", PAU_CIN_HAU_ID); /*[11AC0]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock PSALTER_PAHLAVI =
                new UnicodeBlock("PSALTER_PAHLAVI", PSALTER_PAHLAVI_ID); /*[10B80]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock SHORTHAND_FORMAT_CONTROLS =
                new UnicodeBlock("SHORTHAND_FORMAT_CONTROLS", SHORTHAND_FORMAT_CONTROLS_ID); /*[1BCA0]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock SIDDHAM = new UnicodeBlock("SIDDHAM", SIDDHAM_ID); /*[11580]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock SINHALA_ARCHAIC_NUMBERS =
                new UnicodeBlock("SINHALA_ARCHAIC_NUMBERS", SINHALA_ARCHAIC_NUMBERS_ID); /*[111E0]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock SUPPLEMENTAL_ARROWS_C =
                new UnicodeBlock("SUPPLEMENTAL_ARROWS_C", SUPPLEMENTAL_ARROWS_C_ID); /*[1F800]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock TIRHUTA = new UnicodeBlock("TIRHUTA", TIRHUTA_ID); /*[11480]*/
        /** @stable ICU 54 */
        public static final UnicodeBlock WARANG_CITI = new UnicodeBlock("WARANG_CITI", WARANG_CITI_ID); /*[118A0]*/

        /* New blocks in Unicode 8.0 */

        /** @stable ICU 56 */
        public static final UnicodeBlock AHOM = new UnicodeBlock("AHOM", AHOM_ID); /*[11700]*/
        /** @stable ICU 56 */
        public static final UnicodeBlock ANATOLIAN_HIEROGLYPHS =
                new UnicodeBlock("ANATOLIAN_HIEROGLYPHS", ANATOLIAN_HIEROGLYPHS_ID); /*[14400]*/
        /** @stable ICU 56 */
        public static final UnicodeBlock CHEROKEE_SUPPLEMENT =
                new UnicodeBlock("CHEROKEE_SUPPLEMENT", CHEROKEE_SUPPLEMENT_ID); /*[AB70]*/
        /** @stable ICU 56 */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E =
                new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E",
                        CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E_ID); /*[2B820]*/
        /** @stable ICU 56 */
        public static final UnicodeBlock EARLY_DYNASTIC_CUNEIFORM =
                new UnicodeBlock("EARLY_DYNASTIC_CUNEIFORM", EARLY_DYNASTIC_CUNEIFORM_ID); /*[12480]*/
        /** @stable ICU 56 */
        public static final UnicodeBlock HATRAN = new UnicodeBlock("HATRAN", HATRAN_ID); /*[108E0]*/
        /** @stable ICU 56 */
        public static final UnicodeBlock MULTANI = new UnicodeBlock("MULTANI", MULTANI_ID); /*[11280]*/
        /** @stable ICU 56 */
        public static final UnicodeBlock OLD_HUNGARIAN =
                new UnicodeBlock("OLD_HUNGARIAN", OLD_HUNGARIAN_ID); /*[10C80]*/
        /** @stable ICU 56 */
        public static final UnicodeBlock SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS =
                new UnicodeBlock("SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS",
                        SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS_ID); /*[1F900]*/
        /** @stable ICU 56 */
        public static final UnicodeBlock SUTTON_SIGNWRITING =
                new UnicodeBlock("SUTTON_SIGNWRITING", SUTTON_SIGNWRITING_ID); /*[1D800]*/

        /* New blocks in Unicode 9.0 */

        /** @stable ICU 58 */
        public static final UnicodeBlock ADLAM = new UnicodeBlock("ADLAM", ADLAM_ID); /*[1E900]*/
        /** @stable ICU 58 */
        public static final UnicodeBlock BHAIKSUKI = new UnicodeBlock("BHAIKSUKI", BHAIKSUKI_ID); /*[11C00]*/
        /** @stable ICU 58 */
        public static final UnicodeBlock CYRILLIC_EXTENDED_C =
                new UnicodeBlock("CYRILLIC_EXTENDED_C", CYRILLIC_EXTENDED_C_ID); /*[1C80]*/
        /** @stable ICU 58 */
        public static final UnicodeBlock GLAGOLITIC_SUPPLEMENT =
                new UnicodeBlock("GLAGOLITIC_SUPPLEMENT", GLAGOLITIC_SUPPLEMENT_ID); /*[1E000]*/
        /** @stable ICU 58 */
        public static final UnicodeBlock IDEOGRAPHIC_SYMBOLS_AND_PUNCTUATION =
                new UnicodeBlock("IDEOGRAPHIC_SYMBOLS_AND_PUNCTUATION", IDEOGRAPHIC_SYMBOLS_AND_PUNCTUATION_ID); /*[16FE0]*/
        /** @stable ICU 58 */
        public static final UnicodeBlock MARCHEN = new UnicodeBlock("MARCHEN", MARCHEN_ID); /*[11C70]*/
        /** @stable ICU 58 */
        public static final UnicodeBlock MONGOLIAN_SUPPLEMENT =
                new UnicodeBlock("MONGOLIAN_SUPPLEMENT", MONGOLIAN_SUPPLEMENT_ID); /*[11660]*/
        /** @stable ICU 58 */
        public static final UnicodeBlock NEWA = new UnicodeBlock("NEWA", NEWA_ID); /*[11400]*/
        /** @stable ICU 58 */
        public static final UnicodeBlock OSAGE = new UnicodeBlock("OSAGE", OSAGE_ID); /*[104B0]*/
        /** @stable ICU 58 */
        public static final UnicodeBlock TANGUT = new UnicodeBlock("TANGUT", TANGUT_ID); /*[17000]*/
        /** @stable ICU 58 */
        public static final UnicodeBlock TANGUT_COMPONENTS =
                new UnicodeBlock("TANGUT_COMPONENTS", TANGUT_COMPONENTS_ID); /*[18800]*/

        // New blocks in Unicode 10.0

        /** @stable ICU 60 */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F =
                new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F", CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F_ID); /*[2CEB0]*/
        /** @stable ICU 60 */
        public static final UnicodeBlock KANA_EXTENDED_A =
                new UnicodeBlock("KANA_EXTENDED_A", KANA_EXTENDED_A_ID); /*[1B100]*/
        /** @stable ICU 60 */
        public static final UnicodeBlock MASARAM_GONDI =
                new UnicodeBlock("MASARAM_GONDI", MASARAM_GONDI_ID); /*[11D00]*/
        /** @stable ICU 60 */
        public static final UnicodeBlock NUSHU = new UnicodeBlock("NUSHU", NUSHU_ID); /*[1B170]*/
        /** @stable ICU 60 */
        public static final UnicodeBlock SOYOMBO = new UnicodeBlock("SOYOMBO", SOYOMBO_ID); /*[11A50]*/
        /** @stable ICU 60 */
        public static final UnicodeBlock SYRIAC_SUPPLEMENT =
                new UnicodeBlock("SYRIAC_SUPPLEMENT", SYRIAC_SUPPLEMENT_ID); /*[0860]*/
        /** @stable ICU 60 */
        public static final UnicodeBlock ZANABAZAR_SQUARE =
                new UnicodeBlock("ZANABAZAR_SQUARE", ZANABAZAR_SQUARE_ID); /*[11A00]*/

        // New blocks in Unicode 11.0

        /** @stable ICU 62 */
        public static final UnicodeBlock CHESS_SYMBOLS =
                new UnicodeBlock("CHESS_SYMBOLS", CHESS_SYMBOLS_ID); /*[1FA00]*/
        /** @stable ICU 62 */
        public static final UnicodeBlock DOGRA = new UnicodeBlock("DOGRA", DOGRA_ID); /*[11800]*/
        /** @stable ICU 62 */
        public static final UnicodeBlock GEORGIAN_EXTENDED =
                new UnicodeBlock("GEORGIAN_EXTENDED", GEORGIAN_EXTENDED_ID); /*[1C90]*/
        /** @stable ICU 62 */
        public static final UnicodeBlock GUNJALA_GONDI =
                new UnicodeBlock("GUNJALA_GONDI", GUNJALA_GONDI_ID); /*[11D60]*/
        /** @stable ICU 62 */
        public static final UnicodeBlock HANIFI_ROHINGYA =
                new UnicodeBlock("HANIFI_ROHINGYA", HANIFI_ROHINGYA_ID); /*[10D00]*/
        /** @stable ICU 62 */
        public static final UnicodeBlock INDIC_SIYAQ_NUMBERS =
                new UnicodeBlock("INDIC_SIYAQ_NUMBERS", INDIC_SIYAQ_NUMBERS_ID); /*[1EC70]*/
        /** @stable ICU 62 */
        public static final UnicodeBlock MAKASAR = new UnicodeBlock("MAKASAR", MAKASAR_ID); /*[11EE0]*/
        /** @stable ICU 62 */
        public static final UnicodeBlock MAYAN_NUMERALS =
                new UnicodeBlock("MAYAN_NUMERALS", MAYAN_NUMERALS_ID); /*[1D2E0]*/
        /** @stable ICU 62 */
        public static final UnicodeBlock MEDEFAIDRIN =
                new UnicodeBlock("MEDEFAIDRIN", MEDEFAIDRIN_ID); /*[16E40]*/
        /** @stable ICU 62 */
        public static final UnicodeBlock OLD_SOGDIAN =
                new UnicodeBlock("OLD_SOGDIAN", OLD_SOGDIAN_ID); /*[10F00]*/
        /** @stable ICU 62 */
        public static final UnicodeBlock SOGDIAN = new UnicodeBlock("SOGDIAN", SOGDIAN_ID); /*[10F30]*/

        // New blocks in Unicode 12.0

        /** @stable ICU 64 */
        public static final UnicodeBlock EGYPTIAN_HIEROGLYPH_FORMAT_CONTROLS =
                new UnicodeBlock("EGYPTIAN_HIEROGLYPH_FORMAT_CONTROLS", EGYPTIAN_HIEROGLYPH_FORMAT_CONTROLS_ID); /*[13430]*/
        /** @stable ICU 64 */
        public static final UnicodeBlock ELYMAIC = new UnicodeBlock("ELYMAIC", ELYMAIC_ID); /*[10FE0]*/
        /** @stable ICU 64 */
        public static final UnicodeBlock NANDINAGARI =
                new UnicodeBlock("NANDINAGARI", NANDINAGARI_ID); /*[119A0]*/
        /** @stable ICU 64 */
        public static final UnicodeBlock NYIAKENG_PUACHUE_HMONG =
                new UnicodeBlock("NYIAKENG_PUACHUE_HMONG", NYIAKENG_PUACHUE_HMONG_ID); /*[1E100]*/
        /** @stable ICU 64 */
        public static final UnicodeBlock OTTOMAN_SIYAQ_NUMBERS =
                new UnicodeBlock("OTTOMAN_SIYAQ_NUMBERS", OTTOMAN_SIYAQ_NUMBERS_ID); /*[1ED00]*/
        /** @stable ICU 64 */
        public static final UnicodeBlock SMALL_KANA_EXTENSION =
                new UnicodeBlock("SMALL_KANA_EXTENSION", SMALL_KANA_EXTENSION_ID); /*[1B130]*/
        /** @stable ICU 64 */
        public static final UnicodeBlock SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A =
                new UnicodeBlock("SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A", SYMBOLS_AND_PICTOGRAPHS_EXTENDED_A_ID); /*[1FA70]*/
        /** @stable ICU 64 */
        public static final UnicodeBlock TAMIL_SUPPLEMENT =
                new UnicodeBlock("TAMIL_SUPPLEMENT", TAMIL_SUPPLEMENT_ID); /*[11FC0]*/
        /** @stable ICU 64 */
        public static final UnicodeBlock WANCHO = new UnicodeBlock("WANCHO", WANCHO_ID); /*[1E2C0]*/

        // New blocks in Unicode 13.0

        /** @stable ICU 66 */
        public static final UnicodeBlock CHORASMIAN =
                new UnicodeBlock("CHORASMIAN", CHORASMIAN_ID); /*[10FB0]*/
        /** @stable ICU 66 */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_G =
                new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_G",
                        CJK_UNIFIED_IDEOGRAPHS_EXTENSION_G_ID); /*[30000]*/
        /** @stable ICU 66 */
        public static final UnicodeBlock DIVES_AKURU =
                new UnicodeBlock("DIVES_AKURU", DIVES_AKURU_ID); /*[11900]*/
        /** @stable ICU 66 */
        public static final UnicodeBlock KHITAN_SMALL_SCRIPT =
                new UnicodeBlock("KHITAN_SMALL_SCRIPT", KHITAN_SMALL_SCRIPT_ID); /*[18B00]*/
        /** @stable ICU 66 */
        public static final UnicodeBlock LISU_SUPPLEMENT =
                new UnicodeBlock("LISU_SUPPLEMENT", LISU_SUPPLEMENT_ID); /*[11FB0]*/
        /** @stable ICU 66 */
        public static final UnicodeBlock SYMBOLS_FOR_LEGACY_COMPUTING =
                new UnicodeBlock("SYMBOLS_FOR_LEGACY_COMPUTING", SYMBOLS_FOR_LEGACY_COMPUTING_ID); /*[1FB00]*/
        /** @stable ICU 66 */
        public static final UnicodeBlock TANGUT_SUPPLEMENT =
                new UnicodeBlock("TANGUT_SUPPLEMENT", TANGUT_SUPPLEMENT_ID); /*[18D00]*/
        /** @stable ICU 66 */
        public static final UnicodeBlock YEZIDI = new UnicodeBlock("YEZIDI", YEZIDI_ID); /*[10E80]*/

        // New blocks in Unicode 14.0

        /** @stable ICU 70 */
        public static final UnicodeBlock ARABIC_EXTENDED_B =
                new UnicodeBlock("ARABIC_EXTENDED_B", ARABIC_EXTENDED_B_ID); /*[0870]*/
        /** @stable ICU 70 */
        public static final UnicodeBlock CYPRO_MINOAN =
                new UnicodeBlock("CYPRO_MINOAN", CYPRO_MINOAN_ID); /*[12F90]*/
        /** @stable ICU 70 */
        public static final UnicodeBlock ETHIOPIC_EXTENDED_B =
                new UnicodeBlock("ETHIOPIC_EXTENDED_B", ETHIOPIC_EXTENDED_B_ID); /*[1E7E0]*/
        /** @stable ICU 70 */
        public static final UnicodeBlock KANA_EXTENDED_B =
                new UnicodeBlock("KANA_EXTENDED_B", KANA_EXTENDED_B_ID); /*[1AFF0]*/
        /** @stable ICU 70 */
        public static final UnicodeBlock LATIN_EXTENDED_F =
                new UnicodeBlock("LATIN_EXTENDED_F", LATIN_EXTENDED_F_ID); /*[10780]*/
        /** @stable ICU 70 */
        public static final UnicodeBlock LATIN_EXTENDED_G =
                new UnicodeBlock("LATIN_EXTENDED_G", LATIN_EXTENDED_G_ID); /*[1DF00]*/
        /** @stable ICU 70 */
        public static final UnicodeBlock OLD_UYGHUR =
                new UnicodeBlock("OLD_UYGHUR", OLD_UYGHUR_ID); /*[10F70]*/
        /** @stable ICU 70 */
        public static final UnicodeBlock TANGSA = new UnicodeBlock("TANGSA", TANGSA_ID); /*[16A70]*/
        /** @stable ICU 70 */
        public static final UnicodeBlock TOTO = new UnicodeBlock("TOTO", TOTO_ID); /*[1E290]*/
        /** @stable ICU 70 */
        public static final UnicodeBlock UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED_A =
                new UnicodeBlock("UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED_A",
                        UNIFIED_CANADIAN_ABORIGINAL_SYLLABICS_EXTENDED_A_ID); /*[11AB0]*/
        /** @stable ICU 70 */
        public static final UnicodeBlock VITHKUQI =
                new UnicodeBlock("VITHKUQI", VITHKUQI_ID); /*[10570]*/
        /** @stable ICU 70 */
        public static final UnicodeBlock ZNAMENNY_MUSICAL_NOTATION =
                new UnicodeBlock("ZNAMENNY_MUSICAL_NOTATION",
                        ZNAMENNY_MUSICAL_NOTATION_ID); /*[1CF00]*/

        // New blocks in Unicode 15.0

        /** @stable ICU 72 */
        public static final UnicodeBlock ARABIC_EXTENDED_C =
                new UnicodeBlock("ARABIC_EXTENDED_C", ARABIC_EXTENDED_C_ID); /*[10EC0]*/
        /** @stable ICU 72 */
        public static final UnicodeBlock CJK_UNIFIED_IDEOGRAPHS_EXTENSION_H =
                new UnicodeBlock("CJK_UNIFIED_IDEOGRAPHS_EXTENSION_H",
                        CJK_UNIFIED_IDEOGRAPHS_EXTENSION_H_ID); /*[31350]*/
        /** @stable ICU 72 */
        public static final UnicodeBlock CYRILLIC_EXTENDED_D =
                new UnicodeBlock("CYRILLIC_EXTENDED_D", CYRILLIC_EXTENDED_D_ID); /*[1E030]*/
        /** @stable ICU 72 */
        public static final UnicodeBlock DEVANAGARI_EXTENDED_A =
                new UnicodeBlock("DEVANAGARI_EXTENDED_A", DEVANAGARI_EXTENDED_A_ID); /*[11B00]*/
        /** @stable ICU 72 */
        public static final UnicodeBlock KAKTOVIK_NUMERALS =
                new UnicodeBlock("KAKTOVIK_NUMERALS", KAKTOVIK_NUMERALS_ID); /*[1D2C0]*/
        /** @stable ICU 72 */
        public static final UnicodeBlock KAWI = new UnicodeBlock("KAWI", KAWI_ID); /*[11F00]*/
        /** @stable ICU 72 */
        public static final UnicodeBlock NAG_MUNDARI =
                new UnicodeBlock("NAG_MUNDARI", NAG_MUNDARI_ID); /*[1E4D0]*/

        /**
         * @stable ICU 2.4
         */
        public static final UnicodeBlock INVALID_CODE
        = new UnicodeBlock("INVALID_CODE", INVALID_CODE_ID);

        static {
            for (int blockId = 0; blockId < COUNT; ++blockId) {
                if (BLOCKS_[blockId] == null) {
                    throw new java.lang.IllegalStateException(
                            "UnicodeBlock.BLOCKS_[" + blockId + "] not initialized");
                }
            }
        }

        // public methods --------------------------------------------------

        /**
         * {@icu} Returns the only instance of the UnicodeBlock with the argument ID.
         * If no such ID exists, a INVALID_CODE UnicodeBlock will be returned.
         * @param id UnicodeBlock ID
         * @return the only instance of the UnicodeBlock with the argument ID
         *         if it exists, otherwise a INVALID_CODE UnicodeBlock will be
         *         returned.
         * @stable ICU 2.4
         */
        public static UnicodeBlock getInstance(int id)
        {
            if (id >= 0 && id < BLOCKS_.length) {
                return BLOCKS_[id];
            }
            return INVALID_CODE;
        }

        /**
         * Returns the Unicode allocation block that contains the code point,
         * or null if the code point is not a member of a defined block.
         * @param ch code point to be tested
         * @return the Unicode allocation block that contains the code point
         * @stable ICU 2.4
         */
        public static UnicodeBlock of(int ch)
        {
            if (ch > MAX_VALUE) {
                return INVALID_CODE;
            }

            return UnicodeBlock.getInstance(
                    UCharacterProperty.INSTANCE.getIntPropertyValue(ch, UProperty.BLOCK));
        }

        /**
         * Alternative to the {@link java.lang.Character.UnicodeBlock#forName(String)} method.
         * Returns the Unicode block with the given name. {@icunote} Unlike
         * {@link java.lang.Character.UnicodeBlock#forName(String)}, this only matches
         * against the official UCD name and the Java block name
         * (ignoring case).
         * @param blockName the name of the block to match
         * @return the UnicodeBlock with that name
         * @throws IllegalArgumentException if the blockName could not be matched
         * @stable ICU 3.0
         */
        public static final UnicodeBlock forName(String blockName) {
            Map<String, UnicodeBlock> m = null;
            if (mref != null) {
                m = mref.get();
            }
            if (m == null) {
                m = new HashMap<>(BLOCKS_.length);
                for (int i = 0; i < BLOCKS_.length; ++i) {
                    UnicodeBlock b = BLOCKS_[i];
                    String name = trimBlockName(
                            getPropertyValueName(UProperty.BLOCK, b.getID(),
                                    UProperty.NameChoice.LONG));
                    m.put(name, b);
                }
                mref = new SoftReference<>(m);
            }
            UnicodeBlock b = m.get(trimBlockName(blockName));
            if (b == null) {
                throw new IllegalArgumentException();
            }
            return b;
        }
        private static SoftReference<Map<String, UnicodeBlock>> mref;

        private static String trimBlockName(String name) {
            String upper = name.toUpperCase(Locale.ENGLISH);
            StringBuilder result = new StringBuilder(upper.length());
            for (int i = 0; i < upper.length(); i++) {
                char c = upper.charAt(i);
                if (c != ' ' && c != '_' && c != '-') {
                    result.append(c);
                }
            }
            return result.toString();
        }

        /**
         * {icu} Returns the type ID of this Unicode block
         * @return integer type ID of this Unicode block
         * @stable ICU 2.4
         */
        public int getID()
        {
            return m_id_;
        }

        // private data members ---------------------------------------------

        /**
         * Identification code for this UnicodeBlock
         */
        private int m_id_;

        // private constructor ----------------------------------------------

        /**
         * UnicodeBlock constructor
         * @param name name of this UnicodeBlock
         * @param id unique id of this UnicodeBlock
         * @exception NullPointerException if name is <code>null</code>
         */
        private UnicodeBlock(String name, int id)
        {
            super(name);
            m_id_ = id;
            if (id >= 0) {
                BLOCKS_[id] = this;
            }
        }
    }

    /**
     * East Asian Width constants.
     * @see UProperty#EAST_ASIAN_WIDTH
     * @see UCharacter#getIntPropertyValue
     * @stable ICU 2.4
     */
    public static interface EastAsianWidth
    {
        /**
         * @stable ICU 2.4
         */
        public static final int NEUTRAL = 0;
        /**
         * @stable ICU 2.4
         */
        public static final int AMBIGUOUS = 1;
        /**
         * @stable ICU 2.4
         */
        public static final int HALFWIDTH = 2;
        /**
         * @stable ICU 2.4
         */
        public static final int FULLWIDTH = 3;
        /**
         * @stable ICU 2.4
         */
        public static final int NARROW = 4;
        /**
         * @stable ICU 2.4
         */
        public static final int WIDE = 5;
        /**
         * One more than the highest normal EastAsianWidth value.
         * The highest value is available via UCharacter.getIntPropertyMaxValue(UProperty.EAST_ASIAN_WIDTH).
         *
         * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
         */
        @Deprecated
        public static final int COUNT = 6;
    }

    /**
     * Decomposition Type constants.
     * @see UProperty#DECOMPOSITION_TYPE
     * @stable ICU 2.4
     */
    public static interface DecompositionType
    {
        /**
         * @stable ICU 2.4
         */
        public static final int NONE = 0;
        /**
         * @stable ICU 2.4
         */
        public static final int CANONICAL = 1;
        /**
         * @stable ICU 2.4
         */
        public static final int COMPAT = 2;
        /**
         * @stable ICU 2.4
         */
        public static final int CIRCLE = 3;
        /**
         * @stable ICU 2.4
         */
        public static final int FINAL = 4;
        /**
         * @stable ICU 2.4
         */
        public static final int FONT = 5;
        /**
         * @stable ICU 2.4
         */
        public static final int FRACTION = 6;
        /**
         * @stable ICU 2.4
         */
        public static final int INITIAL = 7;
        /**
         * @stable ICU 2.4
         */
        public static final int ISOLATED = 8;
        /**
         * @stable ICU 2.4
         */
        public static final int MEDIAL = 9;
        /**
         * @stable ICU 2.4
         */
        public static final int NARROW = 10;
        /**
         * @stable ICU 2.4
         */
        public static final int NOBREAK = 11;
        /**
         * @stable ICU 2.4
         */
        public static final int SMALL = 12;
        /**
         * @stable ICU 2.4
         */
        public static final int SQUARE = 13;
        /**
         * @stable ICU 2.4
         */
        public static final int SUB = 14;
        /**
         * @stable ICU 2.4
         */
        public static final int SUPER = 15;
        /**
         * @stable ICU 2.4
         */
        public static final int VERTICAL = 16;
        /**
         * @stable ICU 2.4
         */
        public static final int WIDE = 17;
        /**
         * One more than the highest normal DecompositionType value.
         * The highest value is available via UCharacter.getIntPropertyMaxValue(UProperty.DECOMPOSITION_TYPE).
         *
         * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
         */
        @Deprecated
        public static final int COUNT = 18;
    }

    /**
     * Joining Type constants.
     * @see UProperty#JOINING_TYPE
     * @stable ICU 2.4
     */
    public static interface JoiningType
    {
        /**
         * @stable ICU 2.4
         */
        public static final int NON_JOINING = 0;
        /**
         * @stable ICU 2.4
         */
        public static final int JOIN_CAUSING = 1;
        /**
         * @stable ICU 2.4
         */
        public static final int DUAL_JOINING = 2;
        /**
         * @stable ICU 2.4
         */
        public static final int LEFT_JOINING = 3;
        /**
         * @stable ICU 2.4
         */
        public static final int RIGHT_JOINING = 4;
        /**
         * @stable ICU 2.4
         */
        public static final int TRANSPARENT = 5;
        /**
         * One more than the highest normal JoiningType value.
         * The highest value is available via UCharacter.getIntPropertyMaxValue(UProperty.JOINING_TYPE).
         *
         * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
         */
        @Deprecated
        public static final int COUNT = 6;
    }

    /**
     * Joining Group constants.
     * @see UProperty#JOINING_GROUP
     * @stable ICU 2.4
     */
    public static interface JoiningGroup
    {
        /**
         * @stable ICU 2.4
         */
        public static final int NO_JOINING_GROUP = 0;
        /**
         * @stable ICU 2.4
         */
        public static final int AIN = 1;
        /**
         * @stable ICU 2.4
         */
        public static final int ALAPH = 2;
        /**
         * @stable ICU 2.4
         */
        public static final int ALEF = 3;
        /**
         * @stable ICU 2.4
         */
        public static final int BEH = 4;
        /**
         * @stable ICU 2.4
         */
        public static final int BETH = 5;
        /**
         * @stable ICU 2.4
         */
        public static final int DAL = 6;
        /**
         * @stable ICU 2.4
         */
        public static final int DALATH_RISH = 7;
        /**
         * @stable ICU 2.4
         */
        public static final int E = 8;
        /**
         * @stable ICU 2.4
         */
        public static final int FEH = 9;
        /**
         * @stable ICU 2.4
         */
        public static final int FINAL_SEMKATH = 10;
        /**
         * @stable ICU 2.4
         */
        public static final int GAF = 11;
        /**
         * @stable ICU 2.4
         */
        public static final int GAMAL = 12;
        /**
         * @stable ICU 2.4
         */
        public static final int HAH = 13;
        /** @stable ICU 4.6 */
        public static final int TEH_MARBUTA_GOAL = 14;
        /**
         * @stable ICU 2.4
         */
        public static final int HAMZA_ON_HEH_GOAL = TEH_MARBUTA_GOAL;
        /**
         * @stable ICU 2.4
         */
        public static final int HE = 15;
        /**
         * @stable ICU 2.4
         */
        public static final int HEH = 16;
        /**
         * @stable ICU 2.4
         */
        public static final int HEH_GOAL = 17;
        /**
         * @stable ICU 2.4
         */
        public static final int HETH = 18;
        /**
         * @stable ICU 2.4
         */
        public static final int KAF = 19;
        /**
         * @stable ICU 2.4
         */
        public static final int KAPH = 20;
        /**
         * @stable ICU 2.4
         */
        public static final int KNOTTED_HEH = 21;
        /**
         * @stable ICU 2.4
         */
        public static final int LAM = 22;
        /**
         * @stable ICU 2.4
         */
        public static final int LAMADH = 23;
        /**
         * @stable ICU 2.4
         */
        public static final int MEEM = 24;
        /**
         * @stable ICU 2.4
         */
        public static final int MIM = 25;
        /**
         * @stable ICU 2.4
         */
        public static final int NOON = 26;
        /**
         * @stable ICU 2.4
         */
        public static final int NUN = 27;
        /**
         * @stable ICU 2.4
         */
        public static final int PE = 28;
        /**
         * @stable ICU 2.4
         */
        public static final int QAF = 29;
        /**
         * @stable ICU 2.4
         */
        public static final int QAPH = 30;
        /**
         * @stable ICU 2.4
         */
        public static final int REH = 31;
        /**
         * @stable ICU 2.4
         */
        public static final int REVERSED_PE = 32;
        /**
         * @stable ICU 2.4
         */
        public static final int SAD = 33;
        /**
         * @stable ICU 2.4
         */
        public static final int SADHE = 34;
        /**
         * @stable ICU 2.4
         */
        public static final int SEEN = 35;
        /**
         * @stable ICU 2.4
         */
        public static final int SEMKATH = 36;
        /**
         * @stable ICU 2.4
         */
        public static final int SHIN = 37;
        /**
         * @stable ICU 2.4
         */
        public static final int SWASH_KAF = 38;
        /**
         * @stable ICU 2.4
         */
        public static final int SYRIAC_WAW = 39;
        /**
         * @stable ICU 2.4
         */
        public static final int TAH = 40;
        /**
         * @stable ICU 2.4
         */
        public static final int TAW = 41;
        /**
         * @stable ICU 2.4
         */
        public static final int TEH_MARBUTA = 42;
        /**
         * @stable ICU 2.4
         */
        public static final int TETH = 43;
        /**
         * @stable ICU 2.4
         */
        public static final int WAW = 44;
        /**
         * @stable ICU 2.4
         */
        public static final int YEH = 45;
        /**
         * @stable ICU 2.4
         */
        public static final int YEH_BARREE = 46;
        /**
         * @stable ICU 2.4
         */
        public static final int YEH_WITH_TAIL = 47;
        /**
         * @stable ICU 2.4
         */
        public static final int YUDH = 48;
        /**
         * @stable ICU 2.4
         */
        public static final int YUDH_HE = 49;
        /**
         * @stable ICU 2.4
         */
        public static final int ZAIN = 50;
        /**
         * @stable ICU 2.6
         */
        public static final int FE = 51;
        /**
         * @stable ICU 2.6
         */
        public static final int KHAPH = 52;
        /**
         * @stable ICU 2.6
         */
        public static final int ZHAIN = 53;
        /**
         * @stable ICU 4.0
         */
        public static final int BURUSHASKI_YEH_BARREE = 54;
        /** @stable ICU 4.4 */
        public static final int FARSI_YEH = 55;
        /** @stable ICU 4.4 */
        public static final int NYA = 56;
        /** @stable ICU 49 */
        public static final int ROHINGYA_YEH = 57;

        /** @stable ICU 54 */
        public static final int MANICHAEAN_ALEPH = 58;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_AYIN = 59;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_BETH = 60;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_DALETH = 61;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_DHAMEDH = 62;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_FIVE = 63;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_GIMEL = 64;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_HETH = 65;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_HUNDRED = 66;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_KAPH = 67;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_LAMEDH = 68;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_MEM = 69;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_NUN = 70;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_ONE = 71;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_PE = 72;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_QOPH = 73;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_RESH = 74;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_SADHE = 75;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_SAMEKH = 76;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_TAW = 77;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_TEN = 78;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_TETH = 79;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_THAMEDH = 80;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_TWENTY = 81;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_WAW = 82;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_YODH = 83;
        /** @stable ICU 54 */
        public static final int MANICHAEAN_ZAYIN = 84;
        /** @stable ICU 54 */
        public static final int STRAIGHT_WAW = 85;

        /** @stable ICU 58 */
        public static final int AFRICAN_FEH = 86;
        /** @stable ICU 58 */
        public static final int AFRICAN_NOON = 87;
        /** @stable ICU 58 */
        public static final int AFRICAN_QAF = 88;

        /** @stable ICU 60 */
        public static final int MALAYALAM_BHA = 89;
        /** @stable ICU 60 */
        public static final int MALAYALAM_JA = 90;
        /** @stable ICU 60 */
        public static final int MALAYALAM_LLA = 91;
        /** @stable ICU 60 */
        public static final int MALAYALAM_LLLA = 92;
        /** @stable ICU 60 */
        public static final int MALAYALAM_NGA = 93;
        /** @stable ICU 60 */
        public static final int MALAYALAM_NNA = 94;
        /** @stable ICU 60 */
        public static final int MALAYALAM_NNNA = 95;
        /** @stable ICU 60 */
        public static final int MALAYALAM_NYA = 96;
        /** @stable ICU 60 */
        public static final int MALAYALAM_RA = 97;
        /** @stable ICU 60 */
        public static final int MALAYALAM_SSA = 98;
        /** @stable ICU 60 */
        public static final int MALAYALAM_TTA = 99;

        /** @stable ICU 62 */
        public static final int HANIFI_ROHINGYA_KINNA_YA = 100;
        /** @stable ICU 62 */
        public static final int HANIFI_ROHINGYA_PA = 101;

        /** @stable ICU 70 */
        public static final int THIN_YEH = 102;
        /** @stable ICU 70 */
        public static final int VERTICAL_TAIL = 103;

        /**
         * One more than the highest normal JoiningGroup value.
         * The highest value is available via UCharacter.getIntPropertyMaxValue(UProperty.JoiningGroup).
         *
         * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
         */
        @Deprecated
        public static final int COUNT = 104;
    }

    /**
     * Grapheme Cluster Break constants.
     * @see UProperty#GRAPHEME_CLUSTER_BREAK
     * @stable ICU 3.4
     */
    public static interface GraphemeClusterBreak {
        /**
         * @stable ICU 3.4
         */
        public static final int OTHER = 0;
        /**
         * @stable ICU 3.4
         */
        public static final int CONTROL = 1;
        /**
         * @stable ICU 3.4
         */
        public static final int CR = 2;
        /**
         * @stable ICU 3.4
         */
        public static final int EXTEND = 3;
        /**
         * @stable ICU 3.4
         */
        public static final int L = 4;
        /**
         * @stable ICU 3.4
         */
        public static final int LF = 5;
        /**
         * @stable ICU 3.4
         */
        public static final int LV = 6;
        /**
         * @stable ICU 3.4
         */
        public static final int LVT = 7;
        /**
         * @stable ICU 3.4
         */
        public static final int T = 8;
        /**
         * @stable ICU 3.4
         */
        public static final int V = 9;
        /**
         * @stable ICU 4.0
         */
        public static final int SPACING_MARK = 10;
        /**
         * @stable ICU 4.0
         */
        public static final int PREPEND = 11;
        /** @stable ICU 50 */
        public static final int REGIONAL_INDICATOR = 12;  /*[RI]*/ /* new in Unicode 6.2/ICU 50 */
        /** @stable ICU 58 */
        public static final int E_BASE = 13;          /*[EB]*/ /* from here on: new in Unicode 9.0/ICU 58 */
        /** @stable ICU 58 */
        public static final int E_BASE_GAZ = 14;      /*[EBG]*/
        /** @stable ICU 58 */
        public static final int E_MODIFIER = 15;      /*[EM]*/
        /** @stable ICU 58 */
        public static final int GLUE_AFTER_ZWJ = 16;  /*[GAZ]*/
        /** @stable ICU 58 */
        public static final int ZWJ = 17;             /*[ZWJ]*/

        /**
         * One more than the highest normal GraphemeClusterBreak value.
         * The highest value is available via UCharacter.getIntPropertyMaxValue(UProperty.GRAPHEME_CLUSTER_BREAK).
         *
         * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
         */
        @Deprecated
        public static final int COUNT = 18;
    }

    /**
     * Word Break constants.
     * @see UProperty#WORD_BREAK
     * @stable ICU 3.4
     */
    public static interface WordBreak {
        /**
         * @stable ICU 3.8
         */
        public static final int OTHER = 0;
        /**
         * @stable ICU 3.8
         */
        public static final int ALETTER = 1;
        /**
         * @stable ICU 3.8
         */
        public static final int FORMAT = 2;
        /**
         * @stable ICU 3.8
         */
        public static final int KATAKANA = 3;
        /**
         * @stable ICU 3.8
         */
        public static final int MIDLETTER = 4;
        /**
         * @stable ICU 3.8
         */
        public static final int MIDNUM = 5;
        /**
         * @stable ICU 3.8
         */
        public static final int NUMERIC = 6;
        /**
         * @stable ICU 3.8
         */
        public static final int EXTENDNUMLET = 7;
        /**
         * @stable ICU 4.0
         */
        public static final int CR = 8;
        /**
         * @stable ICU 4.0
         */
        public static final int EXTEND = 9;
        /**
         * @stable ICU 4.0
         */
        public static final int LF = 10;
        /**
         * @stable ICU 4.0
         */
        public static final int MIDNUMLET = 11;
        /**
         * @stable ICU 4.0
         */
        public static final int NEWLINE = 12;
        /** @stable ICU 50 */
        public static final int REGIONAL_INDICATOR = 13;  /*[RI]*/ /* new in Unicode 6.2/ICU 50 */
        /** @stable ICU 52 */
        public static final int HEBREW_LETTER = 14;    /*[HL]*/ /* from here on: new in Unicode 6.3/ICU 52 */
        /** @stable ICU 52 */
        public static final int SINGLE_QUOTE = 15;     /*[SQ]*/
        /** @stable ICU 52 */
        public static final int DOUBLE_QUOTE = 16;     /*[DQ]*/
        /** @stable ICU 58 */
        public static final int E_BASE = 17;           /*[EB]*/ /* from here on: new in Unicode 9.0/ICU 58 */
        /** @stable ICU 58 */
        public static final int E_BASE_GAZ = 18;       /*[EBG]*/
        /** @stable ICU 58 */
        public static final int E_MODIFIER = 19;       /*[EM]*/
        /** @stable ICU 58 */
        public static final int GLUE_AFTER_ZWJ = 20;   /*[GAZ]*/
        /** @stable ICU 58 */
        public static final int ZWJ = 21;              /*[ZWJ]*/
        /** @stable ICU 62 */
        public static final int WSEGSPACE = 22;        /*[WSEGSPACE]*/
        /**
         * One more than the highest normal WordBreak value.
         * The highest value is available via UCharacter.getIntPropertyMaxValue(UProperty.WORD_BREAK).
         *
         * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
         */
        @Deprecated
        public static final int COUNT = 23;
    }

    /**
     * Sentence Break constants.
     * @see UProperty#SENTENCE_BREAK
     * @stable ICU 3.4
     */
    public static interface SentenceBreak {
        /**
         * @stable ICU 3.8
         */
        public static final int OTHER = 0;
        /**
         * @stable ICU 3.8
         */
        public static final int ATERM = 1;
        /**
         * @stable ICU 3.8
         */
        public static final int CLOSE = 2;
        /**
         * @stable ICU 3.8
         */
        public static final int FORMAT = 3;
        /**
         * @stable ICU 3.8
         */
        public static final int LOWER = 4;
        /**
         * @stable ICU 3.8
         */
        public static final int NUMERIC = 5;
        /**
         * @stable ICU 3.8
         */
        public static final int OLETTER = 6;
        /**
         * @stable ICU 3.8
         */
        public static final int SEP = 7;
        /**
         * @stable ICU 3.8
         */
        public static final int SP = 8;
        /**
         * @stable ICU 3.8
         */
        public static final int STERM = 9;
        /**
         * @stable ICU 3.8
         */
        public static final int UPPER = 10;
        /**
         * @stable ICU 4.0
         */
        public static final int CR = 11;
        /**
         * @stable ICU 4.0
         */
        public static final int EXTEND = 12;
        /**
         * @stable ICU 4.0
         */
        public static final int LF = 13;
        /**
         * @stable ICU 4.0
         */
        public static final int SCONTINUE = 14;
        /**
         * One more than the highest normal SentenceBreak value.
         * The highest value is available via UCharacter.getIntPropertyMaxValue(UProperty.SENTENCE_BREAK).
         *
         * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
         */
        @Deprecated
        public static final int COUNT = 15;
    }

    /**
     * Line Break constants.
     * @see UProperty#LINE_BREAK
     * @stable ICU 2.4
     */
    public static interface LineBreak
    {
        /**
         * @stable ICU 2.4
         */
        public static final int UNKNOWN = 0;
        /**
         * @stable ICU 2.4
         */
        public static final int AMBIGUOUS = 1;
        /**
         * @stable ICU 2.4
         */
        public static final int ALPHABETIC = 2;
        /**
         * @stable ICU 2.4
         */
        public static final int BREAK_BOTH = 3;
        /**
         * @stable ICU 2.4
         */
        public static final int BREAK_AFTER = 4;
        /**
         * @stable ICU 2.4
         */
        public static final int BREAK_BEFORE = 5;
        /**
         * @stable ICU 2.4
         */
        public static final int MANDATORY_BREAK = 6;
        /**
         * @stable ICU 2.4
         */
        public static final int CONTINGENT_BREAK = 7;
        /**
         * @stable ICU 2.4
         */
        public static final int CLOSE_PUNCTUATION = 8;
        /**
         * @stable ICU 2.4
         */
        public static final int COMBINING_MARK = 9;
        /**
         * @stable ICU 2.4
         */
        public static final int CARRIAGE_RETURN = 10;
        /**
         * @stable ICU 2.4
         */
        public static final int EXCLAMATION = 11;
        /**
         * @stable ICU 2.4
         */
        public static final int GLUE = 12;
        /**
         * @stable ICU 2.4
         */
        public static final int HYPHEN = 13;
        /**
         * @stable ICU 2.4
         */
        public static final int IDEOGRAPHIC = 14;
        /**
         * @see #INSEPARABLE
         * @stable ICU 2.4
         */
        public static final int INSEPERABLE = 15;
        /**
         * Renamed from the misspelled "inseperable" in Unicode 4.0.1.
         * @stable ICU 3.0
         */
        public static final int INSEPARABLE = 15;
        /**
         * @stable ICU 2.4
         */
        public static final int INFIX_NUMERIC = 16;
        /**
         * @stable ICU 2.4
         */
        public static final int LINE_FEED = 17;
        /**
         * @stable ICU 2.4
         */
        public static final int NONSTARTER = 18;
        /**
         * @stable ICU 2.4
         */
        public static final int NUMERIC = 19;
        /**
         * @stable ICU 2.4
         */
        public static final int OPEN_PUNCTUATION = 20;
        /**
         * @stable ICU 2.4
         */
        public static final int POSTFIX_NUMERIC = 21;
        /**
         * @stable ICU 2.4
         */
        public static final int PREFIX_NUMERIC = 22;
        /**
         * @stable ICU 2.4
         */
        public static final int QUOTATION = 23;
        /**
         * @stable ICU 2.4
         */
        public static final int COMPLEX_CONTEXT = 24;
        /**
         * @stable ICU 2.4
         */
        public static final int SURROGATE = 25;
        /**
         * @stable ICU 2.4
         */
        public static final int SPACE = 26;
        /**
         * @stable ICU 2.4
         */
        public static final int BREAK_SYMBOLS = 27;
        /**
         * @stable ICU 2.4
         */
        public static final int ZWSPACE = 28;
        /**
         * @stable ICU 2.6
         */
        public static final int NEXT_LINE = 29;  /*[NL]*/ /* from here on: new in Unicode 4/ICU 2.6 */
        /**
         * @stable ICU 2.6
         */
        public static final int WORD_JOINER = 30;      /*[WJ]*/
        /**
         * @stable ICU 3.4
         */
        public static final int H2 = 31;  /* from here on: new in Unicode 4.1/ICU 3.4 */
        /**
         * @stable ICU 3.4
         */
        public static final int H3 = 32;
        /**
         * @stable ICU 3.4
         */
        public static final int JL = 33;
        /**
         * @stable ICU 3.4
         */
        public static final int JT = 34;
        /**
         * @stable ICU 3.4
         */
        public static final int JV = 35;
        /** @stable ICU 4.4 */
        public static final int CLOSE_PARENTHESIS = 36; /*[CP]*/ /* new in Unicode 5.2/ICU 4.4 */
        /** @stable ICU 49 */
        public static final int CONDITIONAL_JAPANESE_STARTER = 37;  /*[CJ]*/ /* new in Unicode 6.1/ICU 49 */
        /** @stable ICU 49 */
        public static final int HEBREW_LETTER = 38;  /*[HL]*/ /* new in Unicode 6.1/ICU 49 */
        /** @stable ICU 50 */
        public static final int REGIONAL_INDICATOR = 39;  /*[RI]*/ /* new in Unicode 6.2/ICU 50 */
        /** @stable ICU 58 */
        public static final int E_BASE = 40;  /*[EB]*/ /* from here on: new in Unicode 9.0/ICU 58 */
        /** @stable ICU 58 */
        public static final int E_MODIFIER = 41;  /*[EM]*/
        /** @stable ICU 58 */
        public static final int ZWJ = 42;  /*[ZWJ]*/
        /**
         * One more than the highest normal LineBreak value.
         * The highest value is available via UCharacter.getIntPropertyMaxValue(UProperty.LINE_BREAK).
         *
         * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
         */
        @Deprecated
        public static final int COUNT = 43;
    }

    /**
     * Numeric Type constants.
     * @see UProperty#NUMERIC_TYPE
     * @stable ICU 2.4
     */
    public static interface NumericType
    {
        /**
         * @stable ICU 2.4
         */
        public static final int NONE = 0;
        /**
         * @stable ICU 2.4
         */
        public static final int DECIMAL = 1;
        /**
         * @stable ICU 2.4
         */
        public static final int DIGIT = 2;
        /**
         * @stable ICU 2.4
         */
        public static final int NUMERIC = 3;
        /**
         * One more than the highest normal NumericType value.
         * The highest value is available via UCharacter.getIntPropertyMaxValue(UProperty.NUMERIC_TYPE).
         *
         * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
         */
        @Deprecated
        public static final int COUNT = 4;
    }

    /**
     * Hangul Syllable Type constants.
     *
     * @see UProperty#HANGUL_SYLLABLE_TYPE
     * @stable ICU 2.6
     */
    public static interface HangulSyllableType
    {
        /**
         * @stable ICU 2.6
         */
        public static final int NOT_APPLICABLE      = 0;   /*[NA]*/ /*See note !!*/
        /**
         * @stable ICU 2.6
         */
        public static final int LEADING_JAMO        = 1;   /*[L]*/
        /**
         * @stable ICU 2.6
         */
        public static final int VOWEL_JAMO          = 2;   /*[V]*/
        /**
         * @stable ICU 2.6
         */
        public static final int TRAILING_JAMO       = 3;   /*[T]*/
        /**
         * @stable ICU 2.6
         */
        public static final int LV_SYLLABLE         = 4;   /*[LV]*/
        /**
         * @stable ICU 2.6
         */
        public static final int LVT_SYLLABLE        = 5;   /*[LVT]*/
        /**
         * One more than the highest normal HangulSyllableType value.
         * The highest value is available via UCharacter.getIntPropertyMaxValue(UProperty.HANGUL_SYLLABLE_TYPE).
         *
         * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
         */
        @Deprecated
        public static final int COUNT               = 6;
    }

    /**
     * Bidi Paired Bracket Type constants.
     *
     * @see UProperty#BIDI_PAIRED_BRACKET_TYPE
     * @stable ICU 52
     */
    public static interface BidiPairedBracketType {
        /**
         * Not a paired bracket.
         * @stable ICU 52
         */
        public static final int NONE = 0;
        /**
         * Open paired bracket.
         * @stable ICU 52
         */
        public static final int OPEN = 1;
        /**
         * Close paired bracket.
         * @stable ICU 52
         */
        public static final int CLOSE = 2;
        /**
         * One more than the highest normal BidiPairedBracketType value.
         * The highest value is available via UCharacter.getIntPropertyMaxValue(UProperty.BIDI_PAIRED_BRACKET_TYPE).
         *
         * @deprecated ICU 58 The numeric value may change over time, see ICU ticket #12420.
         */
        @Deprecated
        public static final int COUNT = 3;
    }

    /**
     * Indic Positional Category constants.
     *
     * @see UProperty#INDIC_POSITIONAL_CATEGORY
     * @stable ICU 63
     */
    public static interface IndicPositionalCategory {
        /** @stable ICU 63 */
        public static final int NA = 0;
        /** @stable ICU 63 */
        public static final int BOTTOM = 1;
        /** @stable ICU 63 */
        public static final int BOTTOM_AND_LEFT = 2;
        /** @stable ICU 63 */
        public static final int BOTTOM_AND_RIGHT = 3;
        /** @stable ICU 63 */
        public static final int LEFT = 4;
        /** @stable ICU 63 */
        public static final int LEFT_AND_RIGHT = 5;
        /** @stable ICU 63 */
        public static final int OVERSTRUCK = 6;
        /** @stable ICU 63 */
        public static final int RIGHT = 7;
        /** @stable ICU 63 */
        public static final int TOP = 8;
        /** @stable ICU 63 */
        public static final int TOP_AND_BOTTOM = 9;
        /** @stable ICU 63 */
        public static final int TOP_AND_BOTTOM_AND_RIGHT = 10;
        /** @stable ICU 63 */
        public static final int TOP_AND_LEFT = 11;
        /** @stable ICU 63 */
        public static final int TOP_AND_LEFT_AND_RIGHT = 12;
        /** @stable ICU 63 */
        public static final int TOP_AND_RIGHT = 13;
        /** @stable ICU 63 */
        public static final int VISUAL_ORDER_LEFT = 14;
        /** @stable ICU 66 */
        public static final int TOP_AND_BOTTOM_AND_LEFT = 15;
    }

    /**
     * Indic Syllabic Category constants.
     *
     * @see UProperty#INDIC_SYLLABIC_CATEGORY
     * @stable ICU 63
     */
    public static interface IndicSyllabicCategory {
        /** @stable ICU 63 */
        public static final int OTHER = 0;
        /** @stable ICU 63 */
        public static final int AVAGRAHA = 1;
        /** @stable ICU 63 */
        public static final int BINDU = 2;
        /** @stable ICU 63 */
        public static final int BRAHMI_JOINING_NUMBER = 3;
        /** @stable ICU 63 */
        public static final int CANTILLATION_MARK = 4;
        /** @stable ICU 63 */
        public static final int CONSONANT = 5;
        /** @stable ICU 63 */
        public static final int CONSONANT_DEAD = 6;
        /** @stable ICU 63 */
        public static final int CONSONANT_FINAL = 7;
        /** @stable ICU 63 */
        public static final int CONSONANT_HEAD_LETTER = 8;
        /** @stable ICU 63 */
        public static final int CONSONANT_INITIAL_POSTFIXED = 9;
        /** @stable ICU 63 */
        public static final int CONSONANT_KILLER = 10;
        /** @stable ICU 63 */
        public static final int CONSONANT_MEDIAL = 11;
        /** @stable ICU 63 */
        public static final int CONSONANT_PLACEHOLDER = 12;
        /** @stable ICU 63 */
        public static final int CONSONANT_PRECEDING_REPHA = 13;
        /** @stable ICU 63 */
        public static final int CONSONANT_PREFIXED = 14;
        /** @stable ICU 63 */
        public static final int CONSONANT_SUBJOINED = 15;
        /** @stable ICU 63 */
        public static final int CONSONANT_SUCCEEDING_REPHA = 16;
        /** @stable ICU 63 */
        public static final int CONSONANT_WITH_STACKER = 17;
        /** @stable ICU 63 */
        public static final int GEMINATION_MARK = 18;
        /** @stable ICU 63 */
        public static final int INVISIBLE_STACKER = 19;
        /** @stable ICU 63 */
        public static final int JOINER = 20;
        /** @stable ICU 63 */
        public static final int MODIFYING_LETTER = 21;
        /** @stable ICU 63 */
        public static final int NON_JOINER = 22;
        /** @stable ICU 63 */
        public static final int NUKTA = 23;
        /** @stable ICU 63 */
        public static final int NUMBER = 24;
        /** @stable ICU 63 */
        public static final int NUMBER_JOINER = 25;
        /** @stable ICU 63 */
        public static final int PURE_KILLER = 26;
        /** @stable ICU 63 */
        public static final int REGISTER_SHIFTER = 27;
        /** @stable ICU 63 */
        public static final int SYLLABLE_MODIFIER = 28;
        /** @stable ICU 63 */
        public static final int TONE_LETTER = 29;
        /** @stable ICU 63 */
        public static final int TONE_MARK = 30;
        /** @stable ICU 63 */
        public static final int VIRAMA = 31;
        /** @stable ICU 63 */
        public static final int VISARGA = 32;
        /** @stable ICU 63 */
        public static final int VOWEL = 33;
        /** @stable ICU 63 */
        public static final int VOWEL_DEPENDENT = 34;
        /** @stable ICU 63 */
        public static final int VOWEL_INDEPENDENT = 35;
    }

    /**
     * Vertical Orientation constants.
     *
     * @see UProperty#VERTICAL_ORIENTATION
     * @stable ICU 63
     */
    public static interface VerticalOrientation {
        /** @stable ICU 63 */
        public static final int ROTATED = 0;
        /** @stable ICU 63 */
        public static final int TRANSFORMED_ROTATED = 1;
        /** @stable ICU 63 */
        public static final int TRANSFORMED_UPRIGHT = 2;
        /** @stable ICU 63 */
        public static final int UPRIGHT = 3;
    }

    // public data members -----------------------------------------------

    /**
     * The lowest Unicode code point value, constant 0.
     * Same as {@link Character#MIN_CODE_POINT}, same integer value as {@link Character#MIN_VALUE}.
     *
     * @stable ICU 2.1
     */
    public static final int MIN_VALUE = Character.MIN_CODE_POINT;

    /**
     * The highest Unicode code point value (scalar value), constant U+10FFFF (uses 21 bits).
     * Same as {@link Character#MAX_CODE_POINT}.
     *
     * <p>Up-to-date Unicode implementation of {@link Character#MAX_VALUE}
     * which is still a char with the value U+FFFF.
     *
     * @stable ICU 2.1
     */
    public static final int MAX_VALUE = Character.MAX_CODE_POINT;

    /**
     * The minimum value for Supplementary code points, constant U+10000.
     * Same as {@link Character#MIN_SUPPLEMENTARY_CODE_POINT}.
     *
     * @stable ICU 2.1
     */
    public static final int SUPPLEMENTARY_MIN_VALUE = Character.MIN_SUPPLEMENTARY_CODE_POINT;

    /**
     * Unicode value used when translating into Unicode encoding form and there
     * is no existing character.
     * @stable ICU 2.1
     */
    public static final int REPLACEMENT_CHAR = '\uFFFD';

    /**
     * Special value that is returned by getUnicodeNumericValue(int) when no
     * numeric value is defined for a code point.
     * @stable ICU 2.4
     * @see #getUnicodeNumericValue
     */
    public static final double NO_NUMERIC_VALUE = -123456789;

    /**
     * Compatibility constant for Java Character's MIN_RADIX.
     * @stable ICU 3.4
     */
    public static final int MIN_RADIX = java.lang.Character.MIN_RADIX;

    /**
     * Compatibility constant for Java Character's MAX_RADIX.
     * @stable ICU 3.4
     */
    public static final int MAX_RADIX = java.lang.Character.MAX_RADIX;

    /**
     * Do not lowercase non-initial parts of words when titlecasing.
     * Option bit for titlecasing APIs that take an options bit set.
     *
     * By default, titlecasing will titlecase the first cased character
     * of a word and lowercase all other characters.
     * With this option, the other characters will not be modified.
     *
     * @see #toTitleCase
     * @stable ICU 3.8
     */
    public static final int TITLECASE_NO_LOWERCASE = 0x100;

    /**
     * Do not adjust the titlecasing indexes from BreakIterator::next() indexes;
     * titlecase exactly the characters at breaks from the iterator.
     * Option bit for titlecasing APIs that take an options bit set.
     *
     * By default, titlecasing will take each break iterator index,
     * adjust it by looking for the next cased character, and titlecase that one.
     * Other characters are lowercased.
     *
     * This follows Unicode 4 &amp; 5 section 3.13 Default Case Operations:
     *
     * R3  toTitlecase(X): Find the word boundaries based on Unicode Standard Annex
     * #29, "Text Boundaries." Between each pair of word boundaries, find the first
     * cased character F. If F exists, map F to default_title(F); then map each
     * subsequent character C to default_lower(C).
     *
     * @see #toTitleCase
     * @see #TITLECASE_NO_LOWERCASE
     * @stable ICU 3.8
     */
    public static final int TITLECASE_NO_BREAK_ADJUSTMENT = 0x200;

    // public methods ----------------------------------------------------

    /**
     * Returnss the numeric value of a decimal digit code point.
     * <br>This method observes the semantics of
     * <code>java.lang.Character.digit()</code>.  Note that this
     * will return positive values for code points for which isDigit
     * returns false, just like java.lang.Character.
     * <br><em>Semantic Change:</em> In release 1.3.1 and
     * prior, this did not treat the European letters as having a
     * digit value, and also treated numeric letters and other numbers as
     * digits.
     * This has been changed to conform to the java semantics.
     * <br>A code point is a valid digit if and only if:
     * <ul>
     *   <li>ch is a decimal digit or one of the european letters, and
     *   <li>the value of ch is less than the specified radix.
     * </ul>
     * @param ch the code point to query
     * @param radix the radix
     * @return the numeric value represented by the code point in the
     * specified radix, or -1 if the code point is not a decimal digit
     * or if its value is too large for the radix
     * @stable ICU 2.1
     */
    public static int digit(int ch, int radix)
    {
        if (2 <= radix && radix <= 36) {
            int value = digit(ch);
            if (value < 0) {
                // ch is not a decimal digit, try latin letters
                value = UCharacterProperty.getEuropeanDigit(ch);
            }
            return (value < radix) ? value : -1;
        } else {
            return -1;  // invalid radix
        }
    }

    /**
     * Returnss the numeric value of a decimal digit code point.
     * <br>This is a convenience overload of <code>digit(int, int)</code>
     * that provides a decimal radix.
     * <br><em>Semantic Change:</em> In release 1.3.1 and prior, this
     * treated numeric letters and other numbers as digits.  This has
     * been changed to conform to the java semantics.
     * @param ch the code point to query
     * @return the numeric value represented by the code point,
     * or -1 if the code point is not a decimal digit or if its
     * value is too large for a decimal radix
     * @stable ICU 2.1
     */
    public static int digit(int ch)
    {
        return UCharacterProperty.INSTANCE.digit(ch);
    }

    /**
     * Returns the numeric value of the code point as a nonnegative
     * integer.
     * <br>If the code point does not have a numeric value, then -1 is returned.
     * <br>
     * If the code point has a numeric value that cannot be represented as a
     * nonnegative integer (for example, a fractional value), then -2 is
     * returned.
     * @param ch the code point to query
     * @return the numeric value of the code point, or -1 if it has no numeric
     * value, or -2 if it has a numeric value that cannot be represented as a
     * nonnegative integer
     * @stable ICU 2.1
     */
    public static int getNumericValue(int ch)
    {
        return UCharacterProperty.INSTANCE.getNumericValue(ch);
    }

    /**
     * {@icu} Returns the numeric value for a Unicode code point as defined in the
     * Unicode Character Database.
     * <p>A "double" return type is necessary because some numeric values are
     * fractions, negative, or too large for int.
     * <p>For characters without any numeric values in the Unicode Character
     * Database, this function will return NO_NUMERIC_VALUE.
     * Note: This is different from the Unicode Standard which specifies NaN as the default value.
     * <p><em>API Change:</em> In release 2.2 and prior, this API has a
     * return type int and returns -1 when the argument ch does not have a
     * corresponding numeric value. This has been changed to synch with ICU4C
     *
     * This corresponds to the ICU4C function u_getNumericValue.
     * @param ch Code point to get the numeric value for.
     * @return numeric value of ch, or NO_NUMERIC_VALUE if none is defined.
     * @stable ICU 2.4
     */
    public static double getUnicodeNumericValue(int ch)
    {
        return UCharacterProperty.INSTANCE.getUnicodeNumericValue(ch);
    }

    /**
     * Compatibility override of Java deprecated method.  This
     * method will always remain deprecated.
     * Same as java.lang.Character.isSpace().
     * @param ch the code point
     * @return true if the code point is a space character as
     * defined by java.lang.Character.isSpace.
     * @deprecated ICU 3.4 (Java)
     */
    @Deprecated
    public static boolean isSpace(int ch) {
        return ch <= 0x20 &&
                (ch == 0x20 || ch == 0x09 || ch == 0x0a || ch == 0x0c || ch == 0x0d);
    }

    /**
     * Returns a value indicating a code point's Unicode category.
     * Up-to-date Unicode implementation of java.lang.Character.getType()
     * except for the above mentioned code points that had their category
     * changed.<br>
     * Return results are constants from the interface
     * <a href=UCharacterCategory.html>UCharacterCategory</a><br>
     * <em>NOTE:</em> the UCharacterCategory values are <em>not</em> compatible with
     * those returned by java.lang.Character.getType.  UCharacterCategory values
     * match the ones used in ICU4C, while java.lang.Character type
     * values, though similar, skip the value 17.
     * @param ch code point whose type is to be determined
     * @return category which is a value of UCharacterCategory
     * @stable ICU 2.1
     */
    public static int getType(int ch)
    {
        return UCharacterProperty.INSTANCE.getType(ch);
    }

    /**
     * Determines if a code point has a defined meaning in the up-to-date
     * Unicode standard.
     * E.g. supplementary code points though allocated space are not defined in
     * Unicode yet.<br>
     * Up-to-date Unicode implementation of java.lang.Character.isDefined()
     * @param ch code point to be determined if it is defined in the most
     *        current version of Unicode
     * @return true if this code point is defined in unicode
     * @stable ICU 2.1
     */
    public static boolean isDefined(int ch)
    {
        return getType(ch) != 0;
    }

    /**
     * Determines if a code point is a Java digit.
     * <br>This method observes the semantics of
     * <code>java.lang.Character.isDigit()</code>. It returns true for decimal
     * digits only.
     * <br><em>Semantic Change:</em> In release 1.3.1 and prior, this treated
     * numeric letters and other numbers as digits.
     * This has been changed to conform to the java semantics.
     * @param ch code point to query
     * @return true if this code point is a digit
     * @stable ICU 2.1
     */
    public static boolean isDigit(int ch)
    {
        return getType(ch) == UCharacterCategory.DECIMAL_DIGIT_NUMBER;
    }

    /**
     * Determines if the specified code point is an ISO control character.
     * A code point is considered to be an ISO control character if it is in
     * the range &#92;u0000 through &#92;u001F or in the range &#92;u007F through
     * &#92;u009F.<br>
     * Up-to-date Unicode implementation of java.lang.Character.isISOControl()
     * @param ch code point to determine if it is an ISO control character
     * @return true if code point is a ISO control character
     * @stable ICU 2.1
     */
    public static boolean isISOControl(int ch)
    {
        return ch >= 0 && ch <= APPLICATION_PROGRAM_COMMAND_ &&
                ((ch <= UNIT_SEPARATOR_) || (ch >= DELETE_));
    }

    /**
     * Determines if the specified code point is a letter.
     * Up-to-date Unicode implementation of java.lang.Character.isLetter()
     * @param ch code point to determine if it is a letter
     * @return true if code point is a letter
     * @stable ICU 2.1
     */
    public static boolean isLetter(int ch)
    {
        // if props == 0, it will just fall through and return false
        return ((1 << getType(ch))
                & ((1 << UCharacterCategory.UPPERCASE_LETTER)
                        | (1 << UCharacterCategory.LOWERCASE_LETTER)
                        | (1 << UCharacterCategory.TITLECASE_LETTER)
                        | (1 << UCharacterCategory.MODIFIER_LETTER)
                        | (1 << UCharacterCategory.OTHER_LETTER))) != 0;
    }

    /**
     * Determines if the specified code point is a letter or digit.
     * {@icunote} This method, unlike java.lang.Character does not regard the ascii
     * characters 'A' - 'Z' and 'a' - 'z' as digits.
     * @param ch code point to determine if it is a letter or a digit
     * @return true if code point is a letter or a digit
     * @stable ICU 2.1
     */
    public static boolean isLetterOrDigit(int ch)
    {
        return ((1 << getType(ch))
                & ((1 << UCharacterCategory.UPPERCASE_LETTER)
                        | (1 << UCharacterCategory.LOWERCASE_LETTER)
                        | (1 << UCharacterCategory.TITLECASE_LETTER)
                        | (1 << UCharacterCategory.MODIFIER_LETTER)
                        | (1 << UCharacterCategory.OTHER_LETTER)
                        | (1 << UCharacterCategory.DECIMAL_DIGIT_NUMBER))) != 0;
    }

    /**
     * Compatibility override of Java deprecated method.  This
     * method will always remain deprecated.  Delegates to
     * java.lang.Character.isJavaIdentifierStart.
     * @param cp the code point
     * @return true if the code point can start a java identifier.
     * @deprecated ICU 3.4 (Java)
     */
    @Deprecated
    public static boolean isJavaLetter(int cp) {
        return isJavaIdentifierStart(cp);
    }

    /**
     * Compatibility override of Java deprecated method.  This
     * method will always remain deprecated.  Delegates to
     * java.lang.Character.isJavaIdentifierPart.
     * @param cp the code point
     * @return true if the code point can continue a java identifier.
     * @deprecated ICU 3.4 (Java)
     */
    @Deprecated
    public static boolean isJavaLetterOrDigit(int cp) {
        return isJavaIdentifierPart(cp);
    }

    /**
     * Compatibility override of Java method, delegates to
     * java.lang.Character.isJavaIdentifierStart.
     * @param cp the code point
     * @return true if the code point can start a java identifier.
     * @stable ICU 3.4
     */
    public static boolean isJavaIdentifierStart(int cp) {
        // note, downcast to char for jdk 1.4 compatibility
        return java.lang.Character.isJavaIdentifierStart((char)cp);
    }

    /**
     * Compatibility override of Java method, delegates to
     * java.lang.Character.isJavaIdentifierPart.
     * @param cp the code point
     * @return true if the code point can continue a java identifier.
     * @stable ICU 3.4
     */
    public static boolean isJavaIdentifierPart(int cp) {
        // note, downcast to char for jdk 1.4 compatibility
        return java.lang.Character.isJavaIdentifierPart((char)cp);
    }

    /**
     * Determines if the specified code point is a lowercase character.
     * UnicodeData only contains case mappings for code points where they are
     * one-to-one mappings; it also omits information about context-sensitive
     * case mappings.<br> For more information about Unicode case mapping
     * please refer to the
     * <a href=https://www.unicode.org/reports/tr21/>Technical report
     * #21</a>.<br>
     * Up-to-date Unicode implementation of java.lang.Character.isLowerCase()
     * @param ch code point to determine if it is in lowercase
     * @return true if code point is a lowercase character
     * @stable ICU 2.1
     */
    public static boolean isLowerCase(int ch)
    {
        // if props == 0, it will just fall through and return false
        return getType(ch) == UCharacterCategory.LOWERCASE_LETTER;
    }

    /**
     * Determines if the specified code point is a white space character.
     * A code point is considered to be an whitespace character if and only
     * if it satisfies one of the following criteria:
     * <ul>
     * <li> It is a Unicode Separator character (categories "Z" = "Zs" or "Zl" or "Zp"), but is not
     *      also a non-breaking space (&#92;u00A0 or &#92;u2007 or &#92;u202F).
     * <li> It is &#92;u0009, HORIZONTAL TABULATION.
     * <li> It is &#92;u000A, LINE FEED.
     * <li> It is &#92;u000B, VERTICAL TABULATION.
     * <li> It is &#92;u000C, FORM FEED.
     * <li> It is &#92;u000D, CARRIAGE RETURN.
     * <li> It is &#92;u001C, FILE SEPARATOR.
     * <li> It is &#92;u001D, GROUP SEPARATOR.
     * <li> It is &#92;u001E, RECORD SEPARATOR.
     * <li> It is &#92;u001F, UNIT SEPARATOR.
     * </ul>
     *
     * This API tries to sync with the semantics of Java's
     * java.lang.Character.isWhitespace(), but it may not return
     * the exact same results because of the Unicode version
     * difference.
     * <p>Note: Unicode 4.0.1 changed U+200B ZERO WIDTH SPACE from a Space Separator (Zs)
     * to a Format Control (Cf). Since then, isWhitespace(0x200b) returns false.
     * See http://www.unicode.org/versions/Unicode4.0.1/
     * @param ch code point to determine if it is a white space
     * @return true if the specified code point is a white space character
     * @stable ICU 2.1
     */
    public static boolean isWhitespace(int ch)
    {
        // exclude no-break spaces
        // if props == 0, it will just fall through and return false
        return ((1 << getType(ch)) &
                ((1 << UCharacterCategory.SPACE_SEPARATOR)
                        | (1 << UCharacterCategory.LINE_SEPARATOR)
                        | (1 << UCharacterCategory.PARAGRAPH_SEPARATOR))) != 0
                        && (ch != NO_BREAK_SPACE_) && (ch != FIGURE_SPACE_) && (ch != NARROW_NO_BREAK_SPACE_)
                        // TAB VT LF FF CR FS GS RS US NL are all control characters
                        // that are white spaces.
                        || (ch >= 0x9 && ch <= 0xd) || (ch >= 0x1c && ch <= 0x1f);
    }

    /**
     * Determines if the specified code point is a Unicode specified space
     * character, i.e. if code point is in the category Zs, Zl and Zp.
     * Up-to-date Unicode implementation of java.lang.Character.isSpaceChar().
     * @param ch code point to determine if it is a space
     * @return true if the specified code point is a space character
     * @stable ICU 2.1
     */
    public static boolean isSpaceChar(int ch)
    {
        // if props == 0, it will just fall through and return false
        return ((1 << getType(ch)) & ((1 << UCharacterCategory.SPACE_SEPARATOR)
                | (1 << UCharacterCategory.LINE_SEPARATOR)
                | (1 << UCharacterCategory.PARAGRAPH_SEPARATOR)))
                != 0;
    }

    /**
     * Determines if the specified code point is a titlecase character.
     * UnicodeData only contains case mappings for code points where they are
     * one-to-one mappings; it also omits information about context-sensitive
     * case mappings.<br>
     * For more information about Unicode case mapping please refer to the
     * <a href=https://www.unicode.org/reports/tr21/>
     * Technical report #21</a>.<br>
     * Up-to-date Unicode implementation of java.lang.Character.isTitleCase().
     * @param ch code point to determine if it is in title case
     * @return true if the specified code point is a titlecase character
     * @stable ICU 2.1
     */
    public static boolean isTitleCase(int ch)
    {
        // if props == 0, it will just fall through and return false
        return getType(ch) == UCharacterCategory.TITLECASE_LETTER;
    }

    /**
     * Determines if the specified character is permissible as a
     * non-initial character of an identifier
     * according to UAX #31 Unicode Identifier and Pattern Syntax.
     *
     * <p>Same as Unicode ID_Continue ({@link UProperty#ID_CONTINUE}).
     *
     * <p>Note that this differs from {@link java.lang.Character#isUnicodeIdentifierPart(char)}
     * which implements a different identifier profile.
     *
     * @param ch the code point to be tested
     * @return true if the code point may occur as a non-initial character of an identifier
     * @stable ICU 2.1
     */
    public static boolean isUnicodeIdentifierPart(int ch)
    {
        return hasBinaryProperty(ch, UProperty.ID_CONTINUE);  // single code point
    }

    /**
     * Determines if the specified character is permissible as the first character in an identifier
     * according to UAX #31 Unicode Identifier and Pattern Syntax.
     *
     * <p>Same as Unicode ID_Start ({@link UProperty#ID_START}).
     *
     * <p>Note that this differs from {@link java.lang.Character#isUnicodeIdentifierStart(char)}
     * which implements a different identifier profile.
     *
     * @param ch the code point to be tested
     * @return true if the code point may start an identifier
     * @stable ICU 2.1
     */
    public static boolean isUnicodeIdentifierStart(int ch)
    {
        return hasBinaryProperty(ch, UProperty.ID_START);  // single code point
    }

    /**
     * Determines if the specified code point should be regarded as an
     * ignorable character in a Java identifier.
     * A character is Java-identifier-ignorable if it has the general category
     * Cf Formatting Control, or it is a non-Java-whitespace ISO control:
     * U+0000..U+0008, U+000E..U+001B, U+007F..U+009F.<br>
     * Up-to-date Unicode implementation of
     * java.lang.Character.isIdentifierIgnorable().<br>
     * See <a href=https://www.unicode.org/reports/tr8/>UTR #8</a>.
     * <p>Note that Unicode just recommends to ignore Cf (format controls).
     * @param ch code point to be determined if it can be ignored in a Unicode
     *        identifier.
     * @return true if the code point is ignorable
     * @stable ICU 2.1
     */
    public static boolean isIdentifierIgnorable(int ch)
    {
        // see java.lang.Character.isIdentifierIgnorable() on range of
        // ignorable characters.
        if (ch <= 0x9f) {
            return isISOControl(ch)
                    && !((ch >= 0x9 && ch <= 0xd)
                            || (ch >= 0x1c && ch <= 0x1f));
        }
        return getType(ch) == UCharacterCategory.FORMAT;
    }

    /**
     * Determines if the specified code point is an uppercase character.
     * UnicodeData only contains case mappings for code point where they are
     * one-to-one mappings; it also omits information about context-sensitive
     * case mappings.<br>
     * For language specific case conversion behavior, use
     * toUpperCase(locale, str). <br>
     * For example, the case conversion for dot-less i and dotted I in Turkish,
     * or for final sigma in Greek.
     * For more information about Unicode case mapping please refer to the
     * <a href=https://www.unicode.org/reports/tr21/>
     * Technical report #21</a>.<br>
     * Up-to-date Unicode implementation of java.lang.Character.isUpperCase().
     * @param ch code point to determine if it is in uppercase
     * @return true if the code point is an uppercase character
     * @stable ICU 2.1
     */
    public static boolean isUpperCase(int ch)
    {
        // if props == 0, it will just fall through and return false
        return getType(ch) == UCharacterCategory.UPPERCASE_LETTER;
    }

    /**
     * The given code point is mapped to its lowercase equivalent; if the code
     * point has no lowercase equivalent, the code point itself is returned.
     * Up-to-date Unicode implementation of java.lang.Character.toLowerCase()
     *
     * <p>This function only returns the simple, single-code point case mapping.
     * Full case mappings should be used whenever possible because they produce
     * better results by working on whole strings.
     * They take into account the string context and the language and can map
     * to a result string with a different length as appropriate.
     * Full case mappings are applied by the case mapping functions
     * that take String parameters rather than code points (int).
     * See also the User Guide chapter on C/POSIX migration:
     * https://unicode-org.github.io/icu/userguide/icu/posix#case-mappings
     *
     * @param ch code point whose lowercase equivalent is to be retrieved
     * @return the lowercase equivalent code point
     * @stable ICU 2.1
     */
    public static int toLowerCase(int ch) {
        return UCaseProps.INSTANCE.tolower(ch);
    }

    /**
     * Converts argument code point and returns a String object representing
     * the code point's value in UTF-16 format.
     * The result is a string whose length is 1 for BMP code points, 2 for supplementary ones.
     *
     * <p>Up-to-date Unicode implementation of java.lang.Character.toString().
     *
     * @param ch code point
     * @return string representation of the code point, null if code point is not
     *         defined in unicode
     * @stable ICU 2.1
     */
    public static String toString(int ch)
    {
        if (ch < MIN_VALUE || ch > MAX_VALUE) {
            return null;
        }

        if (ch < SUPPLEMENTARY_MIN_VALUE) {
            return String.valueOf((char)ch);
        }

        return new String(Character.toChars(ch));
    }

    /**
     * Converts the code point argument to titlecase.
     * If no titlecase is available, the uppercase is returned. If no uppercase
     * is available, the code point itself is returned.
     * Up-to-date Unicode implementation of java.lang.Character.toTitleCase()
     *
     * <p>This function only returns the simple, single-code point case mapping.
     * Full case mappings should be used whenever possible because they produce
     * better results by working on whole strings.
     * They take into account the string context and the language and can map
     * to a result string with a different length as appropriate.
     * Full case mappings are applied by the case mapping functions
     * that take String parameters rather than code points (int).
     * See also the User Guide chapter on C/POSIX migration:
     * https://unicode-org.github.io/icu/userguide/icu/posix#case-mappings
     *
     * @param ch code point  whose title case is to be retrieved
     * @return titlecase code point
     * @stable ICU 2.1
     */
    public static int toTitleCase(int ch) {
        return UCaseProps.INSTANCE.totitle(ch);
    }

    /**
     * Converts the character argument to uppercase.
     * If no uppercase is available, the character itself is returned.
     * Up-to-date Unicode implementation of java.lang.Character.toUpperCase()
     *
     * <p>This function only returns the simple, single-code point case mapping.
     * Full case mappings should be used whenever possible because they produce
     * better results by working on whole strings.
     * They take into account the string context and the language and can map
     * to a result string with a different length as appropriate.
     * Full case mappings are applied by the case mapping functions
     * that take String parameters rather than code points (int).
     * See also the User Guide chapter on C/POSIX migration:
     * https://unicode-org.github.io/icu/userguide/icu/posix#case-mappings
     *
     * @param ch code point whose uppercase is to be retrieved
     * @return uppercase code point
     * @stable ICU 2.1
     */
    public static int toUpperCase(int ch) {
        return UCaseProps.INSTANCE.toupper(ch);
    }

    // extra methods not in java.lang.Character --------------------------

    /**
     * {@icu} Determines if the code point is a supplementary character.
     * A code point is a supplementary character if and only if it is greater
     * than <a href=#SUPPLEMENTARY_MIN_VALUE>SUPPLEMENTARY_MIN_VALUE</a>
     * @param ch code point to be determined if it is in the supplementary
     *        plane
     * @return true if code point is a supplementary character
     * @stable ICU 2.1
     */
    public static boolean isSupplementary(int ch)
    {
        return ch >= UCharacter.SUPPLEMENTARY_MIN_VALUE &&
                ch <= UCharacter.MAX_VALUE;
    }

    /**
     * {@icu} Determines if the code point is in the BMP plane.
     * @param ch code point to be determined if it is not a supplementary
     *        character
     * @return true if code point is not a supplementary character
     * @stable ICU 2.1
     */
    public static boolean isBMP(int ch)
    {
        return (ch >= 0 && ch <= LAST_CHAR_MASK_);
    }

    /**
     * {@icu} Determines whether the specified code point is a printable character
     * according to the Unicode standard.
     * @param ch code point to be determined if it is printable
     * @return true if the code point is a printable character
     * @stable ICU 2.1
     */
    public static boolean isPrintable(int ch)
    {
        int cat = getType(ch);
        // if props == 0, it will just fall through and return false
        return (cat != UCharacterCategory.UNASSIGNED &&
                cat != UCharacterCategory.CONTROL &&
                cat != UCharacterCategory.FORMAT &&
                cat != UCharacterCategory.PRIVATE_USE &&
                cat != UCharacterCategory.SURROGATE &&
                cat != UCharacterCategory.GENERAL_OTHER_TYPES);
    }

    /**
     * {@icu} Determines whether the specified code point is of base form.
     * A code point of base form does not graphically combine with preceding
     * characters, and is neither a control nor a format character.
     * @param ch code point to be determined if it is of base form
     * @return true if the code point is of base form
     * @stable ICU 2.1
     */
    public static boolean isBaseForm(int ch)
    {
        int cat = getType(ch);
        // if props == 0, it will just fall through and return false
        return cat == UCharacterCategory.DECIMAL_DIGIT_NUMBER ||
                cat == UCharacterCategory.OTHER_NUMBER ||
                cat == UCharacterCategory.LETTER_NUMBER ||
                cat == UCharacterCategory.UPPERCASE_LETTER ||
                cat == UCharacterCategory.LOWERCASE_LETTER ||
                cat == UCharacterCategory.TITLECASE_LETTER ||
                cat == UCharacterCategory.MODIFIER_LETTER ||
                cat == UCharacterCategory.OTHER_LETTER ||
                cat == UCharacterCategory.NON_SPACING_MARK ||
                cat == UCharacterCategory.ENCLOSING_MARK ||
                cat == UCharacterCategory.COMBINING_SPACING_MARK;
    }

    /**
     * {@icu} Returns the Bidirection property of a code point.
     * For example, 0x0041 (letter A) has the LEFT_TO_RIGHT directional
     * property.<br>
     * Result returned belongs to the interface
     * <a href=UCharacterDirection.html>UCharacterDirection</a>
     * @param ch the code point to be determined its direction
     * @return direction constant from UCharacterDirection.
     * @stable ICU 2.1
     */
    public static int getDirection(int ch)
    {
        return UBiDiProps.INSTANCE.getClass(ch);
    }

    /**
     * Determines whether the code point has the "mirrored" property.
     * This property is set for characters that are commonly used in
     * Right-To-Left contexts and need to be displayed with a "mirrored"
     * glyph.
     * @param ch code point whose mirror is to be determined
     * @return true if the code point has the "mirrored" property
     * @stable ICU 2.1
     */
    public static boolean isMirrored(int ch)
    {
        return UBiDiProps.INSTANCE.isMirrored(ch);
    }

    /**
     * {@icu} Maps the specified code point to a "mirror-image" code point.
     * For code points with the "mirrored" property, implementations sometimes
     * need a "poor man's" mapping to another code point such that the default
     * glyph may serve as the mirror-image of the default glyph of the
     * specified code point.<br>
     * This is useful for text conversion to and from codepages with visual
     * order, and for displays without glyph selection capabilities.
     * @param ch code point whose mirror is to be retrieved
     * @return another code point that may serve as a mirror-image substitute,
     *         or ch itself if there is no such mapping or ch does not have the
     *         "mirrored" property
     * @stable ICU 2.1
     */
    public static int getMirror(int ch)
    {
        return UBiDiProps.INSTANCE.getMirror(ch);
    }

    /**
     * {@icu} Maps the specified character to its paired bracket character.
     * For Bidi_Paired_Bracket_Type!=None, this is the same as getMirror(int).
     * Otherwise c itself is returned.
     * See http://www.unicode.org/reports/tr9/
     *
     * @param c the code point to be mapped
     * @return the paired bracket code point,
     *         or c itself if there is no such mapping
     *         (Bidi_Paired_Bracket_Type=None)
     *
     * @see UProperty#BIDI_PAIRED_BRACKET
     * @see UProperty#BIDI_PAIRED_BRACKET_TYPE
     * @see #getMirror(int)
     * @stable ICU 52
     */
    public static int getBidiPairedBracket(int c) {
        return UBiDiProps.INSTANCE.getPairedBracket(c);
    }

    /**
     * {@icu} Returns the combining class of the argument codepoint
     * @param ch code point whose combining is to be retrieved
     * @return the combining class of the codepoint
     * @stable ICU 2.1
     */
    public static int getCombiningClass(int ch)
    {
        return Normalizer2.getNFDInstance().getCombiningClass(ch);
    }

    /**
     * {@icu} A code point is illegal if and only if
     * <ul>
     * <li> Out of bounds, less than 0 or greater than UCharacter.MAX_VALUE
     * <li> A surrogate value, 0xD800 to 0xDFFF
     * <li> Not-a-character, having the form 0x xxFFFF or 0x xxFFFE
     * </ul>
     * Note: legal does not mean that it is assigned in this version of Unicode.
     * @param ch code point to determine if it is a legal code point by itself
     * @return true if and only if legal.
     * @stable ICU 2.1
     */
    public static boolean isLegal(int ch)
    {
        if (ch < MIN_VALUE) {
            return false;
        }
        if (ch < Character.MIN_SURROGATE) {
            return true;
        }
        if (ch <= Character.MAX_SURROGATE) {
            return false;
        }
        if (UCharacterUtility.isNonCharacter(ch)) {
            return false;
        }
        return (ch <= MAX_VALUE);
    }

    /**
     * {@icu} A string is legal iff all its code points are legal.
     * A code point is illegal if and only if
     * <ul>
     * <li> Out of bounds, less than 0 or greater than UCharacter.MAX_VALUE
     * <li> A surrogate value, 0xD800 to 0xDFFF
     * <li> Not-a-character, having the form 0x xxFFFF or 0x xxFFFE
     * </ul>
     * Note: legal does not mean that it is assigned in this version of Unicode.
     * @param str containing code points to examin
     * @return true if and only if legal.
     * @stable ICU 2.1
     */
    public static boolean isLegal(String str)
    {
        int size = str.length();
        int codepoint;
        for (int i = 0; i < size; i += Character.charCount(codepoint))
        {
            codepoint = str.codePointAt(i);
            if (!isLegal(codepoint)) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@icu} Returns the version of Unicode data used.
     * @return the unicode version number used
     * @stable ICU 2.1
     */
    public static VersionInfo getUnicodeVersion()
    {
        return UCharacterProperty.INSTANCE.m_unicodeVersion_;
    }

    /**
     * {@icu} Returns the most current Unicode name of the argument code point, or
     * null if the character is unassigned or outside the range
     * {@code UCharacter.MIN_VALUE} and {@code UCharacter.MAX_VALUE} or does not
     * have a name.
     * <br>
     * Note calling any methods related to code point names, e.g. {@code getName()}
     * incurs a one-time initialization cost to construct the name tables.
     * @param ch the code point for which to get the name
     * @return most current Unicode name
     * @stable ICU 2.1
     */
    public static String getName(int ch)
    {
        return UCharacterName.INSTANCE.getName(ch, UCharacterNameChoice.UNICODE_CHAR_NAME);
    }

    /**
     * {@icu} Returns the names for each of the characters in a string
     * @param s string to format
     * @param separator string to go between names
     * @return string of names
     * @stable ICU 3.8
     */
    public static String getName(String s, String separator) {
        if (s.length() == 1) { // handle common case
            return getName(s.charAt(0));
        }
        int cp;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i += Character.charCount(cp)) {
            cp = s.codePointAt(i);
            if (i != 0) sb.append(separator);
            sb.append(UCharacter.getName(cp));
        }
        return sb.toString();
    }

    /**
     * {@icu} Returns null.
     * Used to return the Unicode_1_Name property value which was of little practical value.
     * @param ch the code point for which to get the name
     * @return null
     * @deprecated ICU 49
     */
    @Deprecated
    public static String getName1_0(int ch)
    {
        return null;
    }

    /**
     * {@icu} Returns a name for a valid codepoint. Unlike, getName(int) and
     * getName1_0(int), this method will return a name even for codepoints that
     * are not assigned a name in UnicodeData.txt.
     *
     * <p>The names are returned in the following order.
     * <ul>
     * <li> Most current Unicode name if there is any
     * <li> Unicode 1.0 name if there is any
     * <li> Extended name in the form of
     *      "&lt;codepoint_type-codepoint_hex_digits&gt;". E.g., &lt;noncharacter-fffe&gt;
     * </ul>
     * Note calling any methods related to code point names, e.g. {@code getName()}
     * incurs a one-time initialization cost to construct the name tables.
     * @param ch the code point for which to get the name
     * @return a name for the argument codepoint
     * @stable ICU 2.6
     */
    public static String getExtendedName(int ch) {
        return UCharacterName.INSTANCE.getName(ch, UCharacterNameChoice.EXTENDED_CHAR_NAME);
    }

    /**
     * {@icu} Returns the corrected name from NameAliases.txt if there is one.
     * Returns null if the character is unassigned or outside the range
     * UCharacter.MIN_VALUE and UCharacter.MAX_VALUE or does not have a name.
     * <br>
     * Note calling any methods related to code point names, e.g. {@code getName()}
     * incurs a one-time initialization cost to construct the name tables.
     * @param ch the code point for which to get the name alias
     * @return Unicode name alias, or null
     * @stable ICU 4.4
     */
    public static String getNameAlias(int ch)
    {
        return UCharacterName.INSTANCE.getName(ch, UCharacterNameChoice.CHAR_NAME_ALIAS);
    }

    /**
     * {@icu} Returns null.
     * Used to return the ISO 10646 comment for a character.
     * The Unicode ISO_Comment property is deprecated and has no values.
     *
     * @param ch The code point for which to get the ISO comment.
     *           It must be the case that {@code 0 <= ch <= 0x10ffff}.
     * @return null
     * @deprecated ICU 49
     */
    @Deprecated
    public static String getISOComment(int ch)
    {
        return null;
    }

    /**
     * {@icu} <p>Finds a Unicode code point by its most current Unicode name and
     * return its code point value. All Unicode names are in uppercase.
     * Note calling any methods related to code point names, e.g. {@code getName()}
     * incurs a one-time initialization cost to construct the name tables.
     * @param name most current Unicode character name whose code point is to
     *        be returned
     * @return code point or -1 if name is not found
     * @stable ICU 2.1
     */
    public static int getCharFromName(String name){
        return UCharacterName.INSTANCE.getCharFromName(
                UCharacterNameChoice.UNICODE_CHAR_NAME, name);
    }

    /**
     * {@icu} Returns -1.
     * <p>Used to find a Unicode character by its version 1.0 Unicode name and return
     * its code point value.
     * @param name Unicode 1.0 code point name whose code point is to be
     *             returned
     * @return -1
     * @deprecated ICU 49
     * @see #getName1_0(int)
     */
    @Deprecated
    public static int getCharFromName1_0(String name){
        return -1;
    }

    /**
     * {@icu} <p>Find a Unicode character by either its name and return its code
     * point value. All Unicode names are in uppercase.
     * Extended names are all lowercase except for numbers and are contained
     * within angle brackets.
     * The names are searched in the following order
     * <ul>
     * <li> Most current Unicode name if there is any
     * <li> Unicode 1.0 name if there is any
     * <li> Extended name in the form of
     *      "&lt;codepoint_type-codepoint_hex_digits&gt;". E.g. &lt;noncharacter-FFFE&gt;
     * </ul>
     * Note calling any methods related to code point names, e.g. {@code getName()}
     * incurs a one-time initialization cost to construct the name tables.
     * @param name codepoint name
     * @return code point associated with the name or -1 if the name is not
     *         found.
     * @stable ICU 2.6
     */
    public static int getCharFromExtendedName(String name){
        return UCharacterName.INSTANCE.getCharFromName(
                UCharacterNameChoice.EXTENDED_CHAR_NAME, name);
    }

    /**
     * {@icu} <p>Find a Unicode character by its corrected name alias and return
     * its code point value. All Unicode names are in uppercase.
     * Note calling any methods related to code point names, e.g. {@code getName()}
     * incurs a one-time initialization cost to construct the name tables.
     * @param name Unicode name alias whose code point is to be returned
     * @return code point or -1 if name is not found
     * @stable ICU 4.4
     */
    public static int getCharFromNameAlias(String name){
        return UCharacterName.INSTANCE.getCharFromName(UCharacterNameChoice.CHAR_NAME_ALIAS, name);
    }

    /**
     * {@icu} Return the Unicode name for a given property, as given in the
     * Unicode database file PropertyAliases.txt.  Most properties
     * have more than one name.  The nameChoice determines which one
     * is returned.
     *
     * In addition, this function maps the property
     * UProperty.GENERAL_CATEGORY_MASK to the synthetic names "gcm" /
     * "General_Category_Mask".  These names are not in
     * PropertyAliases.txt.
     *
     * @param property UProperty selector.
     *
     * @param nameChoice UProperty.NameChoice selector for which name
     * to get.  All properties have a long name.  Most have a short
     * name, but some do not.  Unicode allows for additional names; if
     * present these will be returned by UProperty.NameChoice.LONG + i,
     * where i=1, 2,...
     *
     * @return a name, or null if Unicode explicitly defines no name
     * ("n/a") for a given property/nameChoice.  If a given nameChoice
     * throws an exception, then all larger values of nameChoice will
     * throw an exception.  If null is returned for a given
     * nameChoice, then other nameChoice values may return non-null
     * results.
     *
     * @exception IllegalArgumentException thrown if property or
     * nameChoice are invalid.
     *
     * @see UProperty
     * @see UProperty.NameChoice
     * @stable ICU 2.4
     */
    public static String getPropertyName(int property,
            int nameChoice) {
        return UPropertyAliases.INSTANCE.getPropertyName(property, nameChoice);
    }

    /**
     * {@icu} Return the UProperty selector for a given property name, as
     * specified in the Unicode database file PropertyAliases.txt.
     * Short, long, and any other variants are recognized.
     *
     * In addition, this function maps the synthetic names "gcm" /
     * "General_Category_Mask" to the property
     * UProperty.GENERAL_CATEGORY_MASK.  These names are not in
     * PropertyAliases.txt.
     *
     * @param propertyAlias the property name to be matched.  The name
     * is compared using "loose matching" as described in
     * PropertyAliases.txt.
     *
     * @return a UProperty enum.
     *
     * @exception IllegalArgumentException thrown if propertyAlias
     * is not recognized.
     *
     * @see UProperty
     * @stable ICU 2.4
     */
    public static int getPropertyEnum(CharSequence propertyAlias) {
        int propEnum = UPropertyAliases.INSTANCE.getPropertyEnum(propertyAlias);
        if (propEnum == UProperty.UNDEFINED) {
            throw new IllegalIcuArgumentException("Invalid name: " + propertyAlias);
        }
        return propEnum;
    }

    /**
     * {@icu} Return the Unicode name for a given property value, as given in
     * the Unicode database file PropertyValueAliases.txt.  Most
     * values have more than one name.  The nameChoice determines
     * which one is returned.
     *
     * Note: Some of the names in PropertyValueAliases.txt can only be
     * retrieved using UProperty.GENERAL_CATEGORY_MASK, not
     * UProperty.GENERAL_CATEGORY.  These include: "C" / "Other", "L" /
     * "Letter", "LC" / "Cased_Letter", "M" / "Mark", "N" / "Number", "P"
     * / "Punctuation", "S" / "Symbol", and "Z" / "Separator".
     *
     * @param property UProperty selector constant.
     * UProperty.INT_START &lt;= property &lt; UProperty.INT_LIMIT or
     * UProperty.BINARY_START &lt;= property &lt; UProperty.BINARY_LIMIT or
     * UProperty.MASK_START &lt; = property &lt; UProperty.MASK_LIMIT.
     * If out of range, null is returned.
     *
     * @param value selector for a value for the given property.  In
     * general, valid values range from 0 up to some maximum.  There
     * are a few exceptions: (1.) UProperty.BLOCK values begin at the
     * non-zero value BASIC_LATIN.getID().  (2.)
     * UProperty.CANONICAL_COMBINING_CLASS values are not contiguous
     * and range from 0..240.  (3.)  UProperty.GENERAL_CATEGORY_MASK values
     * are mask values produced by left-shifting 1 by
     * UCharacter.getType().  This allows grouped categories such as
     * [:L:] to be represented.  Mask values are non-contiguous.
     *
     * @param nameChoice UProperty.NameChoice selector for which name
     * to get.  All values have a long name.  Most have a short name,
     * but some do not.  Unicode allows for additional names; if
     * present these will be returned by UProperty.NameChoice.LONG + i,
     * where i=1, 2,...
     *
     * @return a name, or null if Unicode explicitly defines no name
     * ("n/a") for a given property/value/nameChoice.  If a given
     * nameChoice throws an exception, then all larger values of
     * nameChoice will throw an exception.  If null is returned for a
     * given nameChoice, then other nameChoice values may return
     * non-null results.
     *
     * @exception IllegalArgumentException thrown if property, value,
     * or nameChoice are invalid.
     *
     * @see UProperty
     * @see UProperty.NameChoice
     * @stable ICU 2.4
     */
    public static String getPropertyValueName(int property,
            int value,
            int nameChoice)
    {
        if ((property == UProperty.CANONICAL_COMBINING_CLASS
                || property == UProperty.LEAD_CANONICAL_COMBINING_CLASS
                || property == UProperty.TRAIL_CANONICAL_COMBINING_CLASS)
                && value >= UCharacter.getIntPropertyMinValue(
                        UProperty.CANONICAL_COMBINING_CLASS)
                        && value <= UCharacter.getIntPropertyMaxValue(
                                UProperty.CANONICAL_COMBINING_CLASS)
                                && nameChoice >= 0 && nameChoice < UProperty.NameChoice.COUNT) {
            // this is hard coded for the valid cc
            // because PropertyValueAliases.txt does not contain all of them
            try {
                return UPropertyAliases.INSTANCE.getPropertyValueName(property, value,
                        nameChoice);
            }
            catch (IllegalArgumentException e) {
                return null;
            }
        }
        return UPropertyAliases.INSTANCE.getPropertyValueName(property, value, nameChoice);
    }

    /**
     * {@icu} Return the property value integer for a given value name, as
     * specified in the Unicode database file PropertyValueAliases.txt.
     * Short, long, and any other variants are recognized.
     *
     * Note: Some of the names in PropertyValueAliases.txt will only be
     * recognized with UProperty.GENERAL_CATEGORY_MASK, not
     * UProperty.GENERAL_CATEGORY.  These include: "C" / "Other", "L" /
     * "Letter", "LC" / "Cased_Letter", "M" / "Mark", "N" / "Number", "P"
     * / "Punctuation", "S" / "Symbol", and "Z" / "Separator".
     *
     * @param property UProperty selector constant.
     * UProperty.INT_START &lt;= property &lt; UProperty.INT_LIMIT or
     * UProperty.BINARY_START &lt;= property &lt; UProperty.BINARY_LIMIT or
     * UProperty.MASK_START &lt; = property &lt; UProperty.MASK_LIMIT.
     * Only these properties can be enumerated.
     *
     * @param valueAlias the value name to be matched.  The name is
     * compared using "loose matching" as described in
     * PropertyValueAliases.txt.
     *
     * @return a value integer.  Note: UProperty.GENERAL_CATEGORY
     * values are mask values produced by left-shifting 1 by
     * UCharacter.getType().  This allows grouped categories such as
     * [:L:] to be represented.
     *
     * @see UProperty
     * @throws IllegalArgumentException if property is not a valid UProperty
     *         selector or valueAlias is not a value of this property
     * @stable ICU 2.4
     */
    public static int getPropertyValueEnum(int property, CharSequence valueAlias) {
        int propEnum = UPropertyAliases.INSTANCE.getPropertyValueEnum(property, valueAlias);
        if (propEnum == UProperty.UNDEFINED) {
            throw new IllegalIcuArgumentException("Invalid name: " + valueAlias);
        }
        return propEnum;
    }

    /**
     * Same as {@link #getPropertyValueEnum(int, CharSequence)}, except doesn't throw exception. Instead, returns UProperty.UNDEFINED.
     * @param property  Same as {@link #getPropertyValueEnum(int, CharSequence)}
     * @param valueAlias    Same as {@link #getPropertyValueEnum(int, CharSequence)}
     * @return returns UProperty.UNDEFINED if the value is not valid, otherwise the value.
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    public static int getPropertyValueEnumNoThrow(int property, CharSequence valueAlias) {
        return UPropertyAliases.INSTANCE.getPropertyValueEnumNoThrow(property, valueAlias);
    }


    /**
     * {@icu} Returns a code point corresponding to the two surrogate code units.
     *
     * @param lead the lead unit
     *        (In ICU 2.1-69 the type of both parameters was <code>char</code>.)
     * @param trail the trail unit
     * @return code point if lead and trail form a valid surrogate pair.
     * @exception IllegalArgumentException thrown when the code units do
     *            not form a valid surrogate pair
     * @stable ICU 70
     * @see #toCodePoint(int, int)
     */
    public static int getCodePoint(int lead, int trail)
    {
        if (isHighSurrogate(lead) && isLowSurrogate(trail)) {
            return toCodePoint(lead, trail);
        }
        throw new IllegalArgumentException("Not a valid surrogate pair");
    }

    /**
     * {@icu} Returns the code point corresponding to the BMP code point.
     *
     * @param char16 the BMP code point
     * @return code point if argument is a valid character.
     * @exception IllegalArgumentException thrown when char16 is not a valid
     *            code point
     * @stable ICU 2.1
     */
    public static int getCodePoint(char char16)
    {
        if (UCharacter.isLegal(char16)) {
            return char16;
        }
        throw new IllegalArgumentException("Illegal codepoint");
    }

    /**
     * Returns the uppercase version of the argument string.
     * Casing is dependent on the default locale and context-sensitive.
     * @param str source string to be performed on
     * @return uppercase version of the argument string
     * @stable ICU 2.1
     */
    public static String toUpperCase(String str)
    {
        return CaseMapImpl.toUpper(getDefaultCaseLocale(), 0, str);
    }

    /**
     * Returns the lowercase version of the argument string.
     * Casing is dependent on the default locale and context-sensitive
     * @param str source string to be performed on
     * @return lowercase version of the argument string
     * @stable ICU 2.1
     */
    public static String toLowerCase(String str)
    {
        return CaseMapImpl.toLower(getDefaultCaseLocale(), 0, str);
    }

    /**
     * <p>Returns the titlecase version of the argument string.
     * <p>Position for titlecasing is determined by the argument break
     * iterator, hence the user can customize his break iterator for
     * a specialized titlecasing. In this case only the forward iteration
     * needs to be implemented.
     * If the break iterator passed in is null, the default Unicode algorithm
     * will be used to determine the titlecase positions.
     *
     * <p>Only positions returned by the break iterator will be title cased,
     * character in between the positions will all be in lower case.
     * <p>Casing is dependent on the default locale and context-sensitive
     * @param str source string to be performed on
     * @param breakiter break iterator to determine the positions in which
     *        the character should be title cased.
     * @return titlecase version of the argument string
     * @stable ICU 2.6
     */
    public static String toTitleCase(String str, BreakIterator breakiter)
    {
        return toTitleCase(Locale.getDefault(), str, breakiter, 0);
    }

    private static int getDefaultCaseLocale() {
        return UCaseProps.getCaseLocale(Locale.getDefault());
    }

    private static int getCaseLocale(Locale locale) {
        if (locale == null) {
            locale = Locale.getDefault();
        }
        return UCaseProps.getCaseLocale(locale);
    }

    private static int getCaseLocale(ULocale locale) {
        if (locale == null) {
            locale = ULocale.getDefault();
        }
        return UCaseProps.getCaseLocale(locale);
    }

    /**
     * Returns the uppercase version of the argument string.
     * Casing is dependent on the argument locale and context-sensitive.
     * @param locale which string is to be converted in
     * @param str source string to be performed on
     * @return uppercase version of the argument string
     * @stable ICU 2.1
     */
    public static String toUpperCase(Locale locale, String str)
    {
        return CaseMapImpl.toUpper(getCaseLocale(locale), 0, str);
    }

    /**
     * Returns the uppercase version of the argument string.
     * Casing is dependent on the argument locale and context-sensitive.
     * @param locale which string is to be converted in
     * @param str source string to be performed on
     * @return uppercase version of the argument string
     * @stable ICU 3.2
     */
    public static String toUpperCase(ULocale locale, String str) {
        return CaseMapImpl.toUpper(getCaseLocale(locale), 0, str);
    }

    /**
     * Returns the lowercase version of the argument string.
     * Casing is dependent on the argument locale and context-sensitive
     * @param locale which string is to be converted in
     * @param str source string to be performed on
     * @return lowercase version of the argument string
     * @stable ICU 2.1
     */
    public static String toLowerCase(Locale locale, String str)
    {
        return CaseMapImpl.toLower(getCaseLocale(locale), 0, str);
    }

    /**
     * Returns the lowercase version of the argument string.
     * Casing is dependent on the argument locale and context-sensitive
     * @param locale which string is to be converted in
     * @param str source string to be performed on
     * @return lowercase version of the argument string
     * @stable ICU 3.2
     */
    public static String toLowerCase(ULocale locale, String str) {
        return CaseMapImpl.toLower(getCaseLocale(locale), 0, str);
    }

    /**
     * <p>Returns the titlecase version of the argument string.
     * <p>Position for titlecasing is determined by the argument break
     * iterator, hence the user can customize his break iterator for
     * a specialized titlecasing. In this case only the forward iteration
     * needs to be implemented.
     * If the break iterator passed in is null, the default Unicode algorithm
     * will be used to determine the titlecase positions.
     *
     * <p>Only positions returned by the break iterator will be title cased,
     * character in between the positions will all be in lower case.
     * <p>Casing is dependent on the argument locale and context-sensitive
     * @param locale which string is to be converted in
     * @param str source string to be performed on
     * @param breakiter break iterator to determine the positions in which
     *        the character should be title cased.
     * @return titlecase version of the argument string
     * @stable ICU 2.6
     */
    public static String toTitleCase(Locale locale, String str,
            BreakIterator breakiter)
    {
        return toTitleCase(locale, str, breakiter, 0);
    }

    /**
     * <p>Returns the titlecase version of the argument string.
     * <p>Position for titlecasing is determined by the argument break
     * iterator, hence the user can customize his break iterator for
     * a specialized titlecasing. In this case only the forward iteration
     * needs to be implemented.
     * If the break iterator passed in is null, the default Unicode algorithm
     * will be used to determine the titlecase positions.
     *
     * <p>Only positions returned by the break iterator will be title cased,
     * character in between the positions will all be in lower case.
     * <p>Casing is dependent on the argument locale and context-sensitive
     * @param locale which string is to be converted in
     * @param str source string to be performed on
     * @param titleIter break iterator to determine the positions in which
     *        the character should be title cased.
     * @return titlecase version of the argument string
     * @stable ICU 3.2
     */
    public static String toTitleCase(ULocale locale, String str,
            BreakIterator titleIter) {
        return toTitleCase(locale, str, titleIter, 0);
    }

    /**
     * <p>Returns the titlecase version of the argument string.
     * <p>Position for titlecasing is determined by the argument break
     * iterator, hence the user can customize his break iterator for
     * a specialized titlecasing. In this case only the forward iteration
     * needs to be implemented.
     * If the break iterator passed in is null, the default Unicode algorithm
     * will be used to determine the titlecase positions.
     *
     * <p>Only positions returned by the break iterator will be title cased,
     * character in between the positions will all be in lower case.
     * <p>Casing is dependent on the argument locale and context-sensitive
     * @param locale which string is to be converted in
     * @param str source string to be performed on
     * @param titleIter break iterator to determine the positions in which
     *        the character should be title cased.
     * @param options bit set to modify the titlecasing operation
     * @return titlecase version of the argument string
     * @stable ICU 3.8
     * @see #TITLECASE_NO_LOWERCASE
     * @see #TITLECASE_NO_BREAK_ADJUSTMENT
     */
    public static String toTitleCase(ULocale locale, String str,
            BreakIterator titleIter, int options) {
        if (titleIter == null && locale == null) {
            locale = ULocale.getDefault();
        }
        titleIter = CaseMapImpl.getTitleBreakIterator(locale, options, titleIter);
        titleIter.setText(str);
        return CaseMapImpl.toTitle(getCaseLocale(locale), options, titleIter, str);
    }

    /**
     * {@icu} <p>Returns the titlecase version of the argument string.
     * <p>Position for titlecasing is determined by the argument break
     * iterator, hence the user can customize his break iterator for
     * a specialized titlecasing. In this case only the forward iteration
     * needs to be implemented.
     * If the break iterator passed in is null, the default Unicode algorithm
     * will be used to determine the titlecase positions.
     *
     * <p>Only positions returned by the break iterator will be title cased,
     * character in between the positions will all be in lower case.
     * <p>Casing is dependent on the argument locale and context-sensitive
     * @param locale which string is to be converted in
     * @param str source string to be performed on
     * @param titleIter break iterator to determine the positions in which
     *        the character should be title cased.
     * @param options bit set to modify the titlecasing operation
     * @return titlecase version of the argument string
     * @see #TITLECASE_NO_LOWERCASE
     * @see #TITLECASE_NO_BREAK_ADJUSTMENT
     * @stable ICU 54
     */
    public static String toTitleCase(Locale locale, String str,
            BreakIterator titleIter,
            int options) {
        if (titleIter == null && locale == null) {
            locale = Locale.getDefault();
        }
        titleIter = CaseMapImpl.getTitleBreakIterator(locale, options, titleIter);
        titleIter.setText(str);
        return CaseMapImpl.toTitle(getCaseLocale(locale), options, titleIter, str);
    }

    /**
     * {@icu} The given character is mapped to its case folding equivalent according
     * to UnicodeData.txt and CaseFolding.txt; if the character has no case
     * folding equivalent, the character itself is returned.
     *
     * <p>This function only returns the simple, single-code point case mapping.
     * Full case mappings should be used whenever possible because they produce
     * better results by working on whole strings.
     * They can map to a result string with a different length as appropriate.
     * Full case mappings are applied by the case mapping functions
     * that take String parameters rather than code points (int).
     * See also the User Guide chapter on C/POSIX migration:
     * https://unicode-org.github.io/icu/userguide/icu/posix#case-mappings
     *
     * @param ch             the character to be converted
     * @param defaultmapping Indicates whether the default mappings defined in
     *                       CaseFolding.txt are to be used, otherwise the
     *                       mappings for dotted I and dotless i marked with
     *                       'T' in CaseFolding.txt are included.
     * @return               the case folding equivalent of the character, if
     *                       any; otherwise the character itself.
     * @see                  #foldCase(String, boolean)
     * @stable ICU 2.1
     */
    public static int foldCase(int ch, boolean defaultmapping) {
        return foldCase(ch, defaultmapping ? FOLD_CASE_DEFAULT : FOLD_CASE_EXCLUDE_SPECIAL_I);
    }

    /**
     * {@icu} The given string is mapped to its case folding equivalent according to
     * UnicodeData.txt and CaseFolding.txt; if any character has no case
     * folding equivalent, the character itself is returned.
     * "Full", multiple-code point case folding mappings are returned here.
     * For "simple" single-code point mappings use the API
     * foldCase(int ch, boolean defaultmapping).
     * @param str            the String to be converted
     * @param defaultmapping Indicates whether the default mappings defined in
     *                       CaseFolding.txt are to be used, otherwise the
     *                       mappings for dotted I and dotless i marked with
     *                       'T' in CaseFolding.txt are included.
     * @return               the case folding equivalent of the character, if
     *                       any; otherwise the character itself.
     * @see                  #foldCase(int, boolean)
     * @stable ICU 2.1
     */
    public static String foldCase(String str, boolean defaultmapping) {
        return foldCase(str, defaultmapping ? FOLD_CASE_DEFAULT : FOLD_CASE_EXCLUDE_SPECIAL_I);
    }

    /**
     * {@icu} Option value for case folding: use default mappings defined in
     * CaseFolding.txt.
     * @stable ICU 2.6
     */
    public static final int FOLD_CASE_DEFAULT    =      0x0000;
    /**
     * {@icu} Option value for case folding:
     * Use the modified set of mappings provided in CaseFolding.txt to handle dotted I
     * and dotless i appropriately for Turkic languages (tr, az).
     *
     * <p>Before Unicode 3.2, CaseFolding.txt contains mappings marked with 'I' that
     * are to be included for default mappings and
     * excluded for the Turkic-specific mappings.
     *
     * <p>Unicode 3.2 CaseFolding.txt instead contains mappings marked with 'T' that
     * are to be excluded for default mappings and
     * included for the Turkic-specific mappings.
     *
     * @stable ICU 2.6
     */
    public static final int FOLD_CASE_EXCLUDE_SPECIAL_I = 0x0001;

    /**
     * {@icu} The given character is mapped to its case folding equivalent according
     * to UnicodeData.txt and CaseFolding.txt; if the character has no case
     * folding equivalent, the character itself is returned.
     *
     * <p>This function only returns the simple, single-code point case mapping.
     * Full case mappings should be used whenever possible because they produce
     * better results by working on whole strings.
     * They can map to a result string with a different length as appropriate.
     * Full case mappings are applied by the case mapping functions
     * that take String parameters rather than code points (int).
     * See also the User Guide chapter on C/POSIX migration:
     * https://unicode-org.github.io/icu/userguide/icu/posix#case-mappings
     *
     * @param ch the character to be converted
     * @param options A bit set for special processing. Currently the recognised options
     * are FOLD_CASE_EXCLUDE_SPECIAL_I and FOLD_CASE_DEFAULT
     * @return the case folding equivalent of the character, if any; otherwise the
     * character itself.
     * @see #foldCase(String, boolean)
     * @stable ICU 2.6
     */
    public static int foldCase(int ch, int options) {
        return UCaseProps.INSTANCE.fold(ch, options);
    }

    /**
     * {@icu} The given string is mapped to its case folding equivalent according to
     * UnicodeData.txt and CaseFolding.txt; if any character has no case
     * folding equivalent, the character itself is returned.
     * "Full", multiple-code point case folding mappings are returned here.
     * For "simple" single-code point mappings use the API
     * foldCase(int ch, boolean defaultmapping).
     * @param str the String to be converted
     * @param options A bit set for special processing. Currently the recognised options
     *                are FOLD_CASE_EXCLUDE_SPECIAL_I and FOLD_CASE_DEFAULT
     * @return the case folding equivalent of the character, if any; otherwise the
     *         character itself.
     * @see #foldCase(int, boolean)
     * @stable ICU 2.6
     */
    public static final String foldCase(String str, int options) {
        return CaseMapImpl.fold(options, str);
    }

    /**
     * {@icu} Returns the numeric value of a Han character.
     *
     * <p>This returns the value of Han 'numeric' code points,
     * including those for zero, ten, hundred, thousand, ten thousand,
     * and hundred million.
     * This includes both the standard and 'checkwriting'
     * characters, the 'big circle' zero character, and the standard
     * zero character.
     *
     * <p>Note: The Unicode Standard has numeric values for more
     * Han characters recognized by this method
     * (see {@link #getNumericValue(int)} and the UCD file DerivedNumericValues.txt),
     * and a {@link jdk.internal.icu.text.NumberFormat} can be used with
     * a Chinese {@link jdk.internal.icu.text.NumberingSystem}.
     *
     * @param ch code point to query
     * @return value if it is a Han 'numeric character,' otherwise return -1.
     * @stable ICU 2.4
     */
    public static int getHanNumericValue(int ch)
    {
        switch(ch)
        {
        case IDEOGRAPHIC_NUMBER_ZERO_ :
        case CJK_IDEOGRAPH_COMPLEX_ZERO_ :
            return 0; // Han Zero
        case CJK_IDEOGRAPH_FIRST_ :
        case CJK_IDEOGRAPH_COMPLEX_ONE_ :
            return 1; // Han One
        case CJK_IDEOGRAPH_SECOND_ :
        case CJK_IDEOGRAPH_COMPLEX_TWO_ :
            return 2; // Han Two
        case CJK_IDEOGRAPH_THIRD_ :
        case CJK_IDEOGRAPH_COMPLEX_THREE_ :
            return 3; // Han Three
        case CJK_IDEOGRAPH_FOURTH_ :
        case CJK_IDEOGRAPH_COMPLEX_FOUR_ :
            return 4; // Han Four
        case CJK_IDEOGRAPH_FIFTH_ :
        case CJK_IDEOGRAPH_COMPLEX_FIVE_ :
            return 5; // Han Five
        case CJK_IDEOGRAPH_SIXTH_ :
        case CJK_IDEOGRAPH_COMPLEX_SIX_ :
            return 6; // Han Six
        case CJK_IDEOGRAPH_SEVENTH_ :
        case CJK_IDEOGRAPH_COMPLEX_SEVEN_ :
            return 7; // Han Seven
        case CJK_IDEOGRAPH_EIGHTH_ :
        case CJK_IDEOGRAPH_COMPLEX_EIGHT_ :
            return 8; // Han Eight
        case CJK_IDEOGRAPH_NINETH_ :
        case CJK_IDEOGRAPH_COMPLEX_NINE_ :
            return 9; // Han Nine
        case CJK_IDEOGRAPH_TEN_ :
        case CJK_IDEOGRAPH_COMPLEX_TEN_ :
            return 10;
        case CJK_IDEOGRAPH_HUNDRED_ :
        case CJK_IDEOGRAPH_COMPLEX_HUNDRED_ :
            return 100;
        case CJK_IDEOGRAPH_THOUSAND_ :
        case CJK_IDEOGRAPH_COMPLEX_THOUSAND_ :
            return 1000;
        case CJK_IDEOGRAPH_TEN_THOUSAND_ :
            return 10000;
        case CJK_IDEOGRAPH_HUNDRED_MILLION_ :
            return 100000000;
        }
        return -1; // no value
    }

    /**
     * {@icu} <p>Returns an iterator for character types, iterating over codepoints.
     * <p>Example of use:<br>
     * <pre>
     * RangeValueIterator iterator = UCharacter.getTypeIterator();
     * RangeValueIterator.Element element = new RangeValueIterator.Element();
     * while (iterator.next(element)) {
     *     System.out.println("Codepoint \\u" +
     *                        Integer.toHexString(element.start) +
     *                        " to codepoint \\u" +
     *                        Integer.toHexString(element.limit - 1) +
     *                        " has the character type " +
     *                        element.value);
     * }
     * </pre>
     * @return an iterator
     * @stable ICU 2.6
     */
    public static RangeValueIterator getTypeIterator()
    {
        return new UCharacterTypeIterator();
    }

    private static final class UCharacterTypeIterator implements RangeValueIterator {
        UCharacterTypeIterator() {
            reset();
        }

        // implements RangeValueIterator
        @Override
        public boolean next(Element element) {
            if(trieIterator.hasNext() && !(range=trieIterator.next()).leadSurrogate) {
                element.start=range.startCodePoint;
                element.limit=range.endCodePoint+1;
                element.value=range.value;
                return true;
            } else {
                return false;
            }
        }

        // implements RangeValueIterator
        @Override
        public void reset() {
            trieIterator=UCharacterProperty.INSTANCE.m_trie_.iterator(MASK_TYPE);
        }

        private Iterator<Trie2.Range> trieIterator;
        private Trie2.Range range;

        private static final class MaskType implements Trie2.ValueMapper {
            // Extracts the general category ("character type") from the trie value.
            @Override
            public int map(int value) {
                return value & UCharacterProperty.TYPE_MASK;
            }
        }
        private static final MaskType MASK_TYPE=new MaskType();
    }

    /**
     * {@icu} <p>Returns an iterator for character names, iterating over codepoints.
     * <p>This API only gets the iterator for the modern, most up-to-date
     * Unicode names. For older 1.0 Unicode names use get1_0NameIterator() or
     * for extended names use getExtendedNameIterator().
     * <p>Example of use:<br>
     * <pre>
     * ValueIterator iterator = UCharacter.getNameIterator();
     * ValueIterator.Element element = new ValueIterator.Element();
     * while (iterator.next(element)) {
     *     System.out.println("Codepoint \\u" +
     *                        Integer.toHexString(element.codepoint) +
     *                        " has the name " + (String)element.value);
     * }
     * </pre>
     * <p>The maximal range which the name iterator iterates is from
     * UCharacter.MIN_VALUE to UCharacter.MAX_VALUE.
     * @return an iterator
     * @stable ICU 2.6
     */
    public static ValueIterator getNameIterator(){
        return new UCharacterNameIterator(UCharacterName.INSTANCE,
                UCharacterNameChoice.UNICODE_CHAR_NAME);
    }

    /**
     * {@icu} Returns an empty iterator.
     * <p>Used to return an iterator for the older 1.0 Unicode character names, iterating over codepoints.
     * @return an empty iterator
     * @deprecated ICU 49
     * @see #getName1_0(int)
     */
    @Deprecated
    public static ValueIterator getName1_0Iterator(){
        return new DummyValueIterator();
    }

    private static final class DummyValueIterator implements ValueIterator {
        @Override
        public boolean next(Element element) { return false; }
        @Override
        public void reset() {}
        @Override
        public void setRange(int start, int limit) {}
    }

    /**
     * {@icu} <p>Returns an iterator for character names, iterating over codepoints.
     * <p>This API only gets the iterator for the extended names.
     * For modern, most up-to-date Unicode names use getNameIterator() or
     * for older 1.0 Unicode names use get1_0NameIterator().
     * <p>Example of use:<br>
     * <pre>
     * ValueIterator iterator = UCharacter.getExtendedNameIterator();
     * ValueIterator.Element element = new ValueIterator.Element();
     * while (iterator.next(element)) {
     *     System.out.println("Codepoint \\u" +
     *                        Integer.toHexString(element.codepoint) +
     *                        " has the name " + (String)element.value);
     * }
     * </pre>
     * <p>The maximal range which the name iterator iterates is from
     * @return an iterator
     * @stable ICU 2.6
     */
    public static ValueIterator getExtendedNameIterator(){
        return new UCharacterNameIterator(UCharacterName.INSTANCE,
                UCharacterNameChoice.EXTENDED_CHAR_NAME);
    }

    /**
     * {@icu} Returns the "age" of the code point.
     * <p>The "age" is the Unicode version when the code point was first
     * designated (as a non-character or for Private Use) or assigned a
     * character.
     * <p>This can be useful to avoid emitting code points to receiving
     * processes that do not accept newer characters.
     * <p>The data is from the UCD file DerivedAge.txt.
     * @param ch The code point.
     * @return the Unicode version number
     * @stable ICU 2.6
     */
    public static VersionInfo getAge(int ch)
    {
        if (ch < MIN_VALUE || ch > MAX_VALUE) {
            throw new IllegalArgumentException("Codepoint out of bounds");
        }
        return UCharacterProperty.INSTANCE.getAge(ch);
    }

    /**
     * {@icu} Check a binary Unicode property for a code point.
     * <p>Unicode, especially in version 3.2, defines many more properties
     * than the original set in UnicodeData.txt.
     * <p>This API is intended to reflect Unicode properties as defined in
     * the Unicode Character Database (UCD) and Unicode Technical Reports
     * (UTR).
     * <p>For details about the properties see
     * <a href=http://www.unicode.org/>http://www.unicode.org/</a>.
     * <p>For names of Unicode properties see the UCD file
     * PropertyAliases.txt.
     * <p>This API does not check the validity of the codepoint.
     * <p>Important: If ICU is built with UCD files from Unicode versions
     * below 3.2, then properties marked with "new" are not or
     * not fully available.
     * @param ch code point to test.
     * @param property selector constant from jdk.internal.icu.lang.UProperty,
     *        identifies which binary property to check.
     * @return true or false according to the binary Unicode property value
     *         for ch. Also false if property is out of bounds or if the
     *         Unicode version does not have data for the property at all, or
     *         not for this code point.
     * @see jdk.internal.icu.lang.UProperty
     * @see CharacterProperties#getBinaryPropertySet(int)
     * @stable ICU 2.6
     */
    public static boolean hasBinaryProperty(int ch, int property)
    {
        return UCharacterProperty.INSTANCE.hasBinaryProperty(ch, property);
    }

    /**
     * {@icu} Returns true if the property is true for the string.
     * Same as {@link #hasBinaryProperty(int, int)}
     * if the string contains exactly one code point.
     *
     * <p>Most properties apply only to single code points.
     * <a href="https://www.unicode.org/reports/tr51/#Emoji_Sets">UTS #51 Unicode Emoji</a>
     * defines several properties of strings.
     *
     * @param s String to test.
     * @param property UProperty selector constant, identifies which binary property to check.
     *        Must be BINARY_START&lt;=which&lt;BINARY_LIMIT.
     * @return true or false according to the binary Unicode property value for the string.
     *         Also false if <code>property</code> is out of bounds or if the Unicode version
     *         does not have data for the property at all.
     *
     * @see jdk.internal.icu.lang.UProperty
     * @see CharacterProperties#getBinaryPropertySet(int)
     * @stable ICU 70
     */
    public static boolean hasBinaryProperty(CharSequence s, int property) {
        int length = s.length();
        if (length == 1) {
            return hasBinaryProperty(s.charAt(0), property);  // single code point
        } else if (length == 2) {
            // first code point
            int c = Character.codePointAt(s, 0);
            if (Character.charCount(c) == length) {
                return hasBinaryProperty(c, property);  // single code point
            }
        }
        // Only call into EmojiProps for a relevant property,
        // so that we not unnecessarily try to load its data file.
        return UProperty.BASIC_EMOJI <= property && property <= UProperty.RGI_EMOJI &&
            EmojiProps.INSTANCE.hasBinaryProperty(s, property);
    }

    /**
     * {@icu} <p>Check if a code point has the Alphabetic Unicode property.
     * <p>Same as UCharacter.hasBinaryProperty(ch, UProperty.ALPHABETIC).
     * <p>Different from UCharacter.isLetter(ch)!
     * @stable ICU 2.6
     * @param ch codepoint to be tested
     */
    public static boolean isUAlphabetic(int ch)
    {
        return hasBinaryProperty(ch, UProperty.ALPHABETIC);
    }

    /**
     * {@icu} <p>Check if a code point has the Lowercase Unicode property.
     * <p>Same as UCharacter.hasBinaryProperty(ch, UProperty.LOWERCASE).
     * <p>This is different from UCharacter.isLowerCase(ch)!
     * @param ch codepoint to be tested
     * @stable ICU 2.6
     */
    public static boolean isULowercase(int ch)
    {
        return hasBinaryProperty(ch, UProperty.LOWERCASE);
    }

    /**
     * {@icu} <p>Check if a code point has the Uppercase Unicode property.
     * <p>Same as UCharacter.hasBinaryProperty(ch, UProperty.UPPERCASE).
     * <p>This is different from UCharacter.isUpperCase(ch)!
     * @param ch codepoint to be tested
     * @stable ICU 2.6
     */
    public static boolean isUUppercase(int ch)
    {
        return hasBinaryProperty(ch, UProperty.UPPERCASE);
    }

    /**
     * {@icu} <p>Check if a code point has the White_Space Unicode property.
     * <p>Same as UCharacter.hasBinaryProperty(ch, UProperty.WHITE_SPACE).
     * <p>This is different from both UCharacter.isSpace(ch) and
     * UCharacter.isWhitespace(ch)!
     * @param ch codepoint to be tested
     * @stable ICU 2.6
     */
    public static boolean isUWhiteSpace(int ch)
    {
        return hasBinaryProperty(ch, UProperty.WHITE_SPACE);
    }

    /**
     * {@icu} Returns the property value for a Unicode property type of a code point.
     * Also returns binary and mask property values.
     * <p>Unicode, especially in version 3.2, defines many more properties than
     * the original set in UnicodeData.txt.
     * <p>The properties APIs are intended to reflect Unicode properties as
     * defined in the Unicode Character Database (UCD) and Unicode Technical
     * Reports (UTR). For details about the properties see
     * http://www.unicode.org/.
     * <p>For names of Unicode properties see the UCD file PropertyAliases.txt.
     *
     * <pre>
     * Sample usage:
     * int ea = UCharacter.getIntPropertyValue(c, UProperty.EAST_ASIAN_WIDTH);
     * int ideo = UCharacter.getIntPropertyValue(c, UProperty.IDEOGRAPHIC);
     * boolean b = (ideo == 1) ? true : false;
     * </pre>
     * @param ch code point to test.
     * @param type UProperty selector constant, identifies which binary
     *        property to check. Must be
     *        UProperty.BINARY_START &lt;= type &lt; UProperty.BINARY_LIMIT or
     *        UProperty.INT_START &lt;= type &lt; UProperty.INT_LIMIT or
     *        UProperty.MASK_START &lt;= type &lt; UProperty.MASK_LIMIT.
     * @return numeric value that is directly the property value or,
     *         for enumerated properties, corresponds to the numeric value of
     *         the enumerated constant of the respective property value type
     *         ({@link ECharacterCategory}, {@link ECharacterDirection},
     *         {@link DecompositionType}, etc.).
     *         Returns 0 or 1 (for false / true) for binary Unicode properties.
     *         Returns a bit-mask for mask properties.
     *         Returns 0 if 'type' is out of bounds or if the Unicode version
     *         does not have data for the property at all, or not for this code
     *         point.
     * @see UProperty
     * @see #hasBinaryProperty
     * @see #getIntPropertyMinValue
     * @see #getIntPropertyMaxValue
     * @see CharacterProperties#getIntPropertyMap(int)
     * @see #getUnicodeVersion
     * @stable ICU 2.4
     */
    public static int getIntPropertyValue(int ch, int type)
    {
        return UCharacterProperty.INSTANCE.getIntPropertyValue(ch, type);
    }
    /**
     * {@icu} Returns a string version of the property value.
     * @param propertyEnum The property enum value.
     * @param codepoint The codepoint value.
     * @param nameChoice The choice of the name.
     * @return value as string
     * @internal
     * @deprecated This API is ICU internal only.
     */
    @Deprecated
    ///CLOVER:OFF
    public static String getStringPropertyValue(int propertyEnum, int codepoint, int nameChoice) {
        if ((propertyEnum >= UProperty.BINARY_START && propertyEnum < UProperty.BINARY_LIMIT) ||
                (propertyEnum >= UProperty.INT_START && propertyEnum < UProperty.INT_LIMIT)) {
            return getPropertyValueName(propertyEnum, getIntPropertyValue(codepoint, propertyEnum),
                    nameChoice);
        }
        if (propertyEnum == UProperty.NUMERIC_VALUE) {
            return String.valueOf(getUnicodeNumericValue(codepoint));
        }
        // otherwise must be string property
        switch (propertyEnum) {
        case UProperty.AGE: return getAge(codepoint).toString();
        case UProperty.ISO_COMMENT: return getISOComment(codepoint);
        case UProperty.BIDI_MIRRORING_GLYPH: return toString(getMirror(codepoint));
        case UProperty.CASE_FOLDING: return toString(foldCase(codepoint, true));
        case UProperty.LOWERCASE_MAPPING: return toString(toLowerCase(codepoint));
        case UProperty.NAME: return getName(codepoint);
        case UProperty.SIMPLE_CASE_FOLDING: return toString(foldCase(codepoint, true));
        case UProperty.SIMPLE_LOWERCASE_MAPPING: return toString(toLowerCase(codepoint));
        case UProperty.SIMPLE_TITLECASE_MAPPING: return toString(toTitleCase(codepoint));
        case UProperty.SIMPLE_UPPERCASE_MAPPING: return toString(toUpperCase(codepoint));
        case UProperty.TITLECASE_MAPPING: return toString(toTitleCase(codepoint));
        case UProperty.UNICODE_1_NAME: return getName1_0(codepoint);
        case UProperty.UPPERCASE_MAPPING: return toString(toUpperCase(codepoint));
        }
        throw new IllegalArgumentException("Illegal Property Enum");
    }
    ///CLOVER:ON

    /**
     * {@icu} Returns the minimum value for an integer/binary Unicode property type.
     * Can be used together with UCharacter.getIntPropertyMaxValue(int)
     * to allocate arrays of jdk.internal.icu.text.UnicodeSet or similar.
     * @param type UProperty selector constant, identifies which binary
     *        property to check. Must be
     *        UProperty.BINARY_START &lt;= type &lt; UProperty.BINARY_LIMIT or
     *        UProperty.INT_START &lt;= type &lt; UProperty.INT_LIMIT.
     * @return Minimum value returned by UCharacter.getIntPropertyValue(int)
     *         for a Unicode property. 0 if the property
     *         selector 'type' is out of range.
     * @see UProperty
     * @see #hasBinaryProperty
     * @see #getUnicodeVersion
     * @see #getIntPropertyMaxValue
     * @see #getIntPropertyValue
     * @stable ICU 2.4
     */
    public static int getIntPropertyMinValue(int type){

        return 0; // undefined; and: all other properties have a minimum value of 0
    }


    /**
     * {@icu} Returns the maximum value for an integer/binary Unicode property.
     * Can be used together with UCharacter.getIntPropertyMinValue(int)
     * to allocate arrays of jdk.internal.icu.text.UnicodeSet or similar.
     * Examples for min/max values (for Unicode 3.2):
     * <ul>
     * <li> UProperty.BIDI_CLASS:    0/18
     * (UCharacterDirection.LEFT_TO_RIGHT/UCharacterDirection.BOUNDARY_NEUTRAL)
     * <li> UProperty.SCRIPT:        0/45 (UScript.COMMON/UScript.TAGBANWA)
     * <li> UProperty.IDEOGRAPHIC:   0/1  (false/true)
     * </ul>
     * For undefined UProperty constant values, min/max values will be 0/-1.
     * @param type UProperty selector constant, identifies which binary
     *        property to check. Must be
     *        UProperty.BINARY_START &lt;= type &lt; UProperty.BINARY_LIMIT or
     *        UProperty.INT_START &lt;= type &lt; UProperty.INT_LIMIT.
     * @return Maximum value returned by u_getIntPropertyValue for a Unicode
     *         property. &lt;= 0 if the property selector 'type' is out of range.
     * @see UProperty
     * @see #hasBinaryProperty
     * @see #getUnicodeVersion
     * @see #getIntPropertyMaxValue
     * @see #getIntPropertyValue
     * @stable ICU 2.4
     */
    public static int getIntPropertyMaxValue(int type)
    {
        return UCharacterProperty.INSTANCE.getIntPropertyMaxValue(type);
    }

    /**
     * Provide the java.lang.Character forDigit API, for convenience.
     * @stable ICU 3.0
     */
    public static char forDigit(int digit, int radix) {
        return java.lang.Character.forDigit(digit, radix);
    }

    // JDK 1.5 API coverage

    /**
     * Constant U+D800, same as {@link Character#MIN_HIGH_SURROGATE}.
     *
     * @stable ICU 3.0
     */
    public static final char MIN_HIGH_SURROGATE = Character.MIN_HIGH_SURROGATE;

    /**
     * Constant U+DBFF, same as {@link Character#MAX_HIGH_SURROGATE}.
     *
     * @stable ICU 3.0
     */
    public static final char MAX_HIGH_SURROGATE = Character.MAX_HIGH_SURROGATE;

    /**
     * Constant U+DC00, same as {@link Character#MIN_LOW_SURROGATE}.
     *
     * @stable ICU 3.0
     */
    public static final char MIN_LOW_SURROGATE = Character.MIN_LOW_SURROGATE;

    /**
     * Constant U+DFFF, same as {@link Character#MAX_LOW_SURROGATE}.
     *
     * @stable ICU 3.0
     */
    public static final char MAX_LOW_SURROGATE = Character.MAX_LOW_SURROGATE;

    /**
     * Constant U+D800, same as {@link Character#MIN_SURROGATE}.
     *
     * @stable ICU 3.0
     */
    public static final char MIN_SURROGATE = Character.MIN_SURROGATE;

    /**
     * Constant U+DFFF, same as {@link Character#MAX_SURROGATE}.
     *
     * @stable ICU 3.0
     */
    public static final char MAX_SURROGATE = Character.MAX_SURROGATE;

    /**
     * Constant U+10000, same as {@link Character#MIN_SUPPLEMENTARY_CODE_POINT}.
     *
     * @stable ICU 3.0
     */
    public static final int MIN_SUPPLEMENTARY_CODE_POINT = Character.MIN_SUPPLEMENTARY_CODE_POINT;

    /**
     * Constant U+10FFFF, same as {@link Character#MAX_CODE_POINT}.
     *
     * @stable ICU 3.0
     */
    public static final int MAX_CODE_POINT = Character.MAX_CODE_POINT;

    /**
     * Constant U+0000, same as {@link Character#MIN_CODE_POINT}.
     *
     * @stable ICU 3.0
     */
    public static final int MIN_CODE_POINT = Character.MIN_CODE_POINT;

    /**
     * Equivalent to {@link Character#isValidCodePoint}.
     *
     * @param cp the code point to check
     * @return true if cp is a valid code point
     * @stable ICU 3.0
     */
    public static final boolean isValidCodePoint(int cp) {
        return cp >= 0 && cp <= MAX_CODE_POINT;
    }

    /**
     * Same as {@link Character#isSupplementaryCodePoint}.
     *
     * @param cp the code point to check
     * @return true if cp is a supplementary code point
     * @stable ICU 3.0
     */
    public static final boolean isSupplementaryCodePoint(int cp) {
        return Character.isSupplementaryCodePoint(cp);
    }

    /**
     * Same as {@link Character#isHighSurrogate},
     * except that the ICU version accepts <code>int</code> for code points.
     *
     * @param codePoint the code point to check
     *        (In ICU 3.0-69 the type of this parameter was <code>char</code>.)
     * @return true if codePoint is a high (lead) surrogate
     * @stable ICU 70
     */
    public static boolean isHighSurrogate(int codePoint) {
        return (codePoint & LEAD_SURROGATE_BITMASK) == LEAD_SURROGATE_BITS;
    }

    /**
     * Same as {@link Character#isLowSurrogate},
     * except that the ICU version accepts <code>int</code> for code points.
     *
     * @param codePoint the code point to check
     *        (In ICU 3.0-69 the type of this parameter was <code>char</code>.)
     * @return true if codePoint is a low (trail) surrogate
     * @stable ICU 70
     */
    public static boolean isLowSurrogate(int codePoint) {
        return (codePoint & TRAIL_SURROGATE_BITMASK) == TRAIL_SURROGATE_BITS;
    }

    /**
     * Same as {@link Character#isSurrogatePair},
     * except that the ICU version accepts <code>int</code> for code points.
     *
     * @param high the high (lead) unit
     *        (In ICU 3.0-69 the type of both parameters was <code>char</code>.)
     * @param low the low (trail) unit
     * @return true if high, low form a surrogate pair
     * @stable ICU 70
     */
    public static final boolean isSurrogatePair(int high, int low) {
        return isHighSurrogate(high) && isLowSurrogate(low);
    }

    /**
     * Same as {@link Character#charCount}.
     * Returns the number of chars needed to represent the code point (1 or 2).
     * This does not check the code point for validity.
     *
     * @param cp the code point to check
     * @return the number of chars needed to represent the code point
     * @stable ICU 3.0
     */
    public static int charCount(int cp) {
        return Character.charCount(cp);
    }

    /**
     * Same as {@link Character#toCodePoint},
     * except that the ICU version accepts <code>int</code> for code points.
     * Returns the code point represented by the two surrogate code units.
     * This does not check the surrogate pair for validity.
     *
     * @param high the high (lead) surrogate
     *        (In ICU 3.0-69 the type of both parameters was <code>char</code>.)
     * @param low the low (trail) surrogate
     * @return the code point formed by the surrogate pair
     * @stable ICU 70
     * @see #getCodePoint(int, int)
     */
    public static final int toCodePoint(int high, int low) {
        // see ICU4C U16_GET_SUPPLEMENTARY()
        return (high << 10) + low - U16_SURROGATE_OFFSET;
    }

    /**
     * Same as {@link Character#codePointAt(CharSequence, int)}.
     * Returns the code point at index.
     * This examines only the characters at index and index+1.
     *
     * @param seq the characters to check
     * @param index the index of the first or only char forming the code point
     * @return the code point at the index
     * @stable ICU 3.0
     */
    public static final int codePointAt(CharSequence seq, int index) {
        char c1 = seq.charAt(index++);
        if (isHighSurrogate(c1)) {
            if (index < seq.length()) {
                char c2 = seq.charAt(index);
                if (isLowSurrogate(c2)) {
                    return toCodePoint(c1, c2);
                }
            }
        }
        return c1;
    }

    /**
     * Same as {@link Character#codePointAt(char[], int)}.
     * Returns the code point at index.
     * This examines only the characters at index and index+1.
     *
     * @param text the characters to check
     * @param index the index of the first or only char forming the code point
     * @return the code point at the index
     * @stable ICU 3.0
     */
    public static final int codePointAt(char[] text, int index) {
        char c1 = text[index++];
        if (isHighSurrogate(c1)) {
            if (index < text.length) {
                char c2 = text[index];
                if (isLowSurrogate(c2)) {
                    return toCodePoint(c1, c2);
                }
            }
        }
        return c1;
    }

    /**
     * Same as {@link Character#codePointAt(char[], int, int)}.
     * Returns the code point at index.
     * This examines only the characters at index and index+1.
     *
     * @param text the characters to check
     * @param index the index of the first or only char forming the code point
     * @param limit the limit of the valid text
     * @return the code point at the index
     * @stable ICU 3.0
     */
    public static final int codePointAt(char[] text, int index, int limit) {
        if (index >= limit || limit > text.length) {
            throw new IndexOutOfBoundsException();
        }
        char c1 = text[index++];
        if (isHighSurrogate(c1)) {
            if (index < limit) {
                char c2 = text[index];
                if (isLowSurrogate(c2)) {
                    return toCodePoint(c1, c2);
                }
            }
        }
        return c1;
    }

    /**
     * Same as {@link Character#codePointBefore(CharSequence, int)}.
     * Return the code point before index.
     * This examines only the characters at index-1 and index-2.
     *
     * @param seq the characters to check
     * @param index the index after the last or only char forming the code point
     * @return the code point before the index
     * @stable ICU 3.0
     */
    public static final int codePointBefore(CharSequence seq, int index) {
        char c2 = seq.charAt(--index);
        if (isLowSurrogate(c2)) {
            if (index > 0) {
                char c1 = seq.charAt(--index);
                if (isHighSurrogate(c1)) {
                    return toCodePoint(c1, c2);
                }
            }
        }
        return c2;
    }

    /**
     * Same as {@link Character#codePointBefore(char[], int)}.
     * Returns the code point before index.
     * This examines only the characters at index-1 and index-2.
     *
     * @param text the characters to check
     * @param index the index after the last or only char forming the code point
     * @return the code point before the index
     * @stable ICU 3.0
     */
    public static final int codePointBefore(char[] text, int index) {
        char c2 = text[--index];
        if (isLowSurrogate(c2)) {
            if (index > 0) {
                char c1 = text[--index];
                if (isHighSurrogate(c1)) {
                    return toCodePoint(c1, c2);
                }
            }
        }
        return c2;
    }

    /**
     * Same as {@link Character#codePointBefore(char[], int, int)}.
     * Return the code point before index.
     * This examines only the characters at index-1 and index-2.
     *
     * @param text the characters to check
     * @param index the index after the last or only char forming the code point
     * @param limit the start of the valid text
     * @return the code point before the index
     * @stable ICU 3.0
     */
    public static final int codePointBefore(char[] text, int index, int limit) {
        if (index <= limit || limit < 0) {
            throw new IndexOutOfBoundsException();
        }
        char c2 = text[--index];
        if (isLowSurrogate(c2)) {
            if (index > limit) {
                char c1 = text[--index];
                if (isHighSurrogate(c1)) {
                    return toCodePoint(c1, c2);
                }
            }
        }
        return c2;
    }

    /**
     * Same as {@link Character#toChars(int, char[], int)}.
     * Writes the chars representing the
     * code point into the destination at the given index.
     *
     * @param cp the code point to convert
     * @param dst the destination array into which to put the char(s) representing the code point
     * @param dstIndex the index at which to put the first (or only) char
     * @return the count of the number of chars written (1 or 2)
     * @throws IllegalArgumentException if cp is not a valid code point
     * @stable ICU 3.0
     */
    public static final int toChars(int cp, char[] dst, int dstIndex) {
        return Character.toChars(cp, dst, dstIndex);
    }

    /**
     * Same as {@link Character#toChars(int)}.
     * Returns a char array representing the code point.
     *
     * @param cp the code point to convert
     * @return an array containing the char(s) representing the code point
     * @throws IllegalArgumentException if cp is not a valid code point
     * @stable ICU 3.0
     */
    public static final char[] toChars(int cp) {
        return Character.toChars(cp);
    }

    /**
     * Equivalent to the {@link Character#getDirectionality(char)} method, for
     * convenience. Returns a byte representing the directionality of the
     * character.
     *
     * {@icunote} Unlike {@link Character#getDirectionality(char)}, this returns
     * DIRECTIONALITY_LEFT_TO_RIGHT for undefined or out-of-bounds characters.
     *
     * {@icunote} The return value must be tested using the constants defined in {@link
     * UCharacterDirection} and its interface {@link
     * UCharacterEnums.ECharacterDirection} since the values are different from the ones
     * defined by <code>java.lang.Character</code>.
     * @param cp the code point to check
     * @return the directionality of the code point
     * @see #getDirection
     * @stable ICU 3.0
     */
    public static byte getDirectionality(int cp)
    {
        return (byte)getDirection(cp);
    }

    /**
     * Equivalent to the {@link Character#codePointCount(CharSequence, int, int)}
     * method, for convenience.  Counts the number of code points in the range
     * of text.
     * @param text the characters to check
     * @param start the start of the range
     * @param limit the limit of the range
     * @return the number of code points in the range
     * @stable ICU 3.0
     */
    public static int codePointCount(CharSequence text, int start, int limit) {
        if (start < 0 || limit < start || limit > text.length()) {
            throw new IndexOutOfBoundsException("start (" + start +
                    ") or limit (" + limit +
                    ") invalid or out of range 0, " + text.length());
        }

        int len = limit - start;
        while (limit > start) {
            char ch = text.charAt(--limit);
            while (ch >= MIN_LOW_SURROGATE && ch <= MAX_LOW_SURROGATE && limit > start) {
                ch = text.charAt(--limit);
                if (ch >= MIN_HIGH_SURROGATE && ch <= MAX_HIGH_SURROGATE) {
                    --len;
                    break;
                }
            }
        }
        return len;
    }

    /**
     * Equivalent to the {@link Character#codePointCount(char[], int, int)} method, for
     * convenience. Counts the number of code points in the range of text.
     * @param text the characters to check
     * @param start the start of the range
     * @param limit the limit of the range
     * @return the number of code points in the range
     * @stable ICU 3.0
     */
    public static int codePointCount(char[] text, int start, int limit) {
        if (start < 0 || limit < start || limit > text.length) {
            throw new IndexOutOfBoundsException("start (" + start +
                    ") or limit (" + limit +
                    ") invalid or out of range 0, " + text.length);
        }

        int len = limit - start;
        while (limit > start) {
            char ch = text[--limit];
            while (ch >= MIN_LOW_SURROGATE && ch <= MAX_LOW_SURROGATE && limit > start) {
                ch = text[--limit];
                if (ch >= MIN_HIGH_SURROGATE && ch <= MAX_HIGH_SURROGATE) {
                    --len;
                    break;
                }
            }
        }
        return len;
    }

    /**
     * Equivalent to the {@link Character#offsetByCodePoints(CharSequence, int, int)}
     * method, for convenience.  Adjusts the char index by a code point offset.
     * @param text the characters to check
     * @param index the index to adjust
     * @param codePointOffset the number of code points by which to offset the index
     * @return the adjusted index
     * @stable ICU 3.0
     */
    public static int offsetByCodePoints(CharSequence text, int index, int codePointOffset) {
        if (index < 0 || index > text.length()) {
            throw new IndexOutOfBoundsException("index ( " + index +
                    ") out of range 0, " + text.length());
        }

        if (codePointOffset < 0) {
            while (++codePointOffset <= 0) {
                char ch = text.charAt(--index);
                while (ch >= MIN_LOW_SURROGATE && ch <= MAX_LOW_SURROGATE && index > 0) {
                    ch = text.charAt(--index);
                    if (ch < MIN_HIGH_SURROGATE || ch > MAX_HIGH_SURROGATE) {
                        if (++codePointOffset > 0) {
                            return index+1;
                        }
                    }
                }
            }
        } else {
            int limit = text.length();
            while (--codePointOffset >= 0) {
                char ch = text.charAt(index++);
                while (ch >= MIN_HIGH_SURROGATE && ch <= MAX_HIGH_SURROGATE && index < limit) {
                    ch = text.charAt(index++);
                    if (ch < MIN_LOW_SURROGATE || ch > MAX_LOW_SURROGATE) {
                        if (--codePointOffset < 0) {
                            return index-1;
                        }
                    }
                }
            }
        }

        return index;
    }

    /**
     * Equivalent to the
     * {@link Character#offsetByCodePoints(char[], int, int, int, int)}
     * method, for convenience.  Adjusts the char index by a code point offset.
     * @param text the characters to check
     * @param start the start of the range to check
     * @param count the length of the range to check
     * @param index the index to adjust
     * @param codePointOffset the number of code points by which to offset the index
     * @return the adjusted index
     * @stable ICU 3.0
     */
    public static int offsetByCodePoints(char[] text, int start, int count, int index,
            int codePointOffset) {
        int limit = start + count;
        if (start < 0 || limit < start || limit > text.length || index < start || index > limit) {
            throw new IndexOutOfBoundsException("index ( " + index +
                    ") out of range " + start +
                    ", " + limit +
                    " in array 0, " + text.length);
        }

        if (codePointOffset < 0) {
            while (++codePointOffset <= 0) {
                char ch = text[--index];
                if (index < start) {
                    throw new IndexOutOfBoundsException("index ( " + index +
                            ") < start (" + start +
                            ")");
                }
                while (ch >= MIN_LOW_SURROGATE && ch <= MAX_LOW_SURROGATE && index > start) {
                    ch = text[--index];
                    if (ch < MIN_HIGH_SURROGATE || ch > MAX_HIGH_SURROGATE) {
                        if (++codePointOffset > 0) {
                            return index+1;
                        }
                    }
                }
            }
        } else {
            while (--codePointOffset >= 0) {
                char ch = text[index++];
                if (index > limit) {
                    throw new IndexOutOfBoundsException("index ( " + index +
                            ") > limit (" + limit +
                            ")");
                }
                while (ch >= MIN_HIGH_SURROGATE && ch <= MAX_HIGH_SURROGATE && index < limit) {
                    ch = text[index++];
                    if (ch < MIN_LOW_SURROGATE || ch > MAX_LOW_SURROGATE) {
                        if (--codePointOffset < 0) {
                            return index-1;
                        }
                    }
                }
            }
        }

        return index;
    }

    // private variables -------------------------------------------------

    /**
     * To get the last character out from a data type
     */
    private static final int LAST_CHAR_MASK_ = 0xFFFF;

    //    /**
    //     * To get the last byte out from a data type
    //     */
    //    private static final int LAST_BYTE_MASK_ = 0xFF;
    //
    //    /**
    //     * Shift 16 bits
    //     */
    //    private static final int SHIFT_16_ = 16;
    //
    //    /**
    //     * Shift 24 bits
    //     */
    //    private static final int SHIFT_24_ = 24;
    //
    //    /**
    //     * Decimal radix
    //     */
    //    private static final int DECIMAL_RADIX_ = 10;

    /**
     * No break space code point
     */
    private static final int NO_BREAK_SPACE_ = 0xA0;

    /**
     * Figure space code point
     */
    private static final int FIGURE_SPACE_ = 0x2007;

    /**
     * Narrow no break space code point
     */
    private static final int NARROW_NO_BREAK_SPACE_ = 0x202F;

    /**
     * Ideographic number zero code point
     */
    private static final int IDEOGRAPHIC_NUMBER_ZERO_ = 0x3007;

    /**
     * CJK Ideograph, First code point
     */
    private static final int CJK_IDEOGRAPH_FIRST_ = 0x4e00;

    /**
     * CJK Ideograph, Second code point
     */
    private static final int CJK_IDEOGRAPH_SECOND_ = 0x4e8c;

    /**
     * CJK Ideograph, Third code point
     */
    private static final int CJK_IDEOGRAPH_THIRD_ = 0x4e09;

    /**
     * CJK Ideograph, Fourth code point
     */
    private static final int CJK_IDEOGRAPH_FOURTH_ = 0x56db;

    /**
     * CJK Ideograph, FIFTH code point
     */
    private static final int CJK_IDEOGRAPH_FIFTH_ = 0x4e94;

    /**
     * CJK Ideograph, Sixth code point
     */
    private static final int CJK_IDEOGRAPH_SIXTH_ = 0x516d;

    /**
     * CJK Ideograph, Seventh code point
     */
    private static final int CJK_IDEOGRAPH_SEVENTH_ = 0x4e03;

    /**
     * CJK Ideograph, Eighth code point
     */
    private static final int CJK_IDEOGRAPH_EIGHTH_ = 0x516b;

    /**
     * CJK Ideograph, Nineth code point
     */
    private static final int CJK_IDEOGRAPH_NINETH_ = 0x4e5d;

    /**
     * Application Program command code point
     */
    private static final int APPLICATION_PROGRAM_COMMAND_ = 0x009F;

    /**
     * Unit separator code point
     */
    private static final int UNIT_SEPARATOR_ = 0x001F;

    /**
     * Delete code point
     */
    private static final int DELETE_ = 0x007F;

    /**
     * Han digit characters
     */
    private static final int CJK_IDEOGRAPH_COMPLEX_ZERO_     = 0x96f6;
    private static final int CJK_IDEOGRAPH_COMPLEX_ONE_      = 0x58f9;
    private static final int CJK_IDEOGRAPH_COMPLEX_TWO_      = 0x8cb3;
    private static final int CJK_IDEOGRAPH_COMPLEX_THREE_    = 0x53c3;
    private static final int CJK_IDEOGRAPH_COMPLEX_FOUR_     = 0x8086;
    private static final int CJK_IDEOGRAPH_COMPLEX_FIVE_     = 0x4f0d;
    private static final int CJK_IDEOGRAPH_COMPLEX_SIX_      = 0x9678;
    private static final int CJK_IDEOGRAPH_COMPLEX_SEVEN_    = 0x67d2;
    private static final int CJK_IDEOGRAPH_COMPLEX_EIGHT_    = 0x634c;
    private static final int CJK_IDEOGRAPH_COMPLEX_NINE_     = 0x7396;
    private static final int CJK_IDEOGRAPH_TEN_              = 0x5341;
    private static final int CJK_IDEOGRAPH_COMPLEX_TEN_      = 0x62fe;
    private static final int CJK_IDEOGRAPH_HUNDRED_          = 0x767e;
    private static final int CJK_IDEOGRAPH_COMPLEX_HUNDRED_  = 0x4f70;
    private static final int CJK_IDEOGRAPH_THOUSAND_         = 0x5343;
    private static final int CJK_IDEOGRAPH_COMPLEX_THOUSAND_ = 0x4edf;
    private static final int CJK_IDEOGRAPH_TEN_THOUSAND_     = 0x824c;
    private static final int CJK_IDEOGRAPH_HUNDRED_MILLION_  = 0x5104;

    // private constructor -----------------------------------------------
    ///CLOVER:OFF
    /**
     * Private constructor to prevent instantiation
     */
    private UCharacter()
    {
    }
    ///CLOVER:ON
}
