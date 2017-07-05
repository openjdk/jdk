/*
 * Portions Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
/*
 *******************************************************************************
 * (C) Copyright IBM Corp. 1996-2005 - All Rights Reserved                     *
 *                                                                             *
 * The original version of this source code and documentation is copyrighted   *
 * and owned by IBM, These materials are provided under terms of a License     *
 * Agreement between IBM and Sun. This technology is protected by multiple     *
 * US and International patents. This notice and attribution to IBM may not    *
 * to removed.                                                                 *
 *******************************************************************************
 */

package sun.text.normalizer;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * <p>
 * The UCharacter class provides extensions to the
 * <a href=http://java.sun.com/j2se/1.3/docs/api/java/lang/Character.html>
 * java.lang.Character</a> class. These extensions provide support for
 * Unicode 3.2 properties and together with the <a href=../text/UTF16.html>UTF16</a>
 * class, provide support for supplementary characters (those with code
 * points above U+FFFF).
 * </p>
 * <p>
 * Code points are represented in these API using ints. While it would be
 * more convenient in Java to have a separate primitive datatype for them,
 * ints suffice in the meantime.
 * </p>
 * <p>
 * To use this class please add the jar file name icu4j.jar to the
 * class path, since it contains data files which supply the information used
 * by this file.<br>
 * E.g. In Windows <br>
 * <code>set CLASSPATH=%CLASSPATH%;$JAR_FILE_PATH/ucharacter.jar</code>.<br>
 * Otherwise, another method would be to copy the files uprops.dat and
 * unames.icu from the icu4j source subdirectory
 * <i>$ICU4J_SRC/src/com.ibm.icu.impl.data</i> to your class directory
 * <i>$ICU4J_CLASS/com.ibm.icu.impl.data</i>.
 * </p>
 * <p>
 * Aside from the additions for UTF-16 support, and the updated Unicode 3.1
 * properties, the main differences between UCharacter and Character are:
 * <ul>
 * <li> UCharacter is not designed to be a char wrapper and does not have
 *      APIs to which involves management of that single char.<br>
 *      These include:
 *      <ul>
 *        <li> char charValue(),
 *        <li> int compareTo(java.lang.Character, java.lang.Character), etc.
 *      </ul>
 * <li> UCharacter does not include Character APIs that are deprecated, not
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
 * Further detail differences can be determined from the program
 *        <a href = http://oss.software.ibm.com/developerworks/opensource/cvs/icu4j/~checkout~/icu4j/src/com/ibm/icu/dev/test/lang/UCharacterCompare.java>
 *        com.ibm.icu.dev.test.lang.UCharacterCompare</a>
 * </p>
 * <p>
 * This class is not subclassable
 * </p>
 * @author Syn Wee Quek
 * @stable ICU 2.1
 * @see com.ibm.icu.lang.UCharacterEnums
 */

public final class UCharacter
{

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
         * @stable ICU 2.4
         */
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
         * @stable ICU 2.6
         */
        public static final int COUNT               = 6;
    }

    /**
     * [Sun] This interface moved from UCharacterEnums.java.
     *
     * 'Enum' for the CharacterCategory constants.  These constants are
     * compatible in name <b>but not in value</b> with those defined in
     * <code>java.lang.Character</code>.
     * @see UCharacterCategory
     * @draft ICU 3.0
     * @deprecated This is a draft API and might change in a future release of ICU.
     */
    public static interface ECharacterCategory
    {
        /**
         * Character type Lu
         * @stable ICU 2.1
         */
        public static final int UPPERCASE_LETTER        = 1;

        /**
         * Character type Lt
         * @stable ICU 2.1
         */
        public static final int TITLECASE_LETTER        = 3;

        /**
         * Character type Lo
         * @stable ICU 2.1
         */
        public static final int OTHER_LETTER            = 5;
    }

    // public data members -----------------------------------------------

    /**
     * The lowest Unicode code point value.
     * @stable ICU 2.1
     */
    public static final int MIN_VALUE = UTF16.CODEPOINT_MIN_VALUE;

    /**
     * The highest Unicode code point value (scalar value) according to the
     * Unicode Standard.
     * This is a 21-bit value (21 bits, rounded up).<br>
     * Up-to-date Unicode implementation of java.lang.Character.MIN_VALUE
     * @stable ICU 2.1
     */
    public static final int MAX_VALUE = UTF16.CODEPOINT_MAX_VALUE;

    /**
     * The minimum value for Supplementary code points
     * @stable ICU 2.1
     */
    public static final int SUPPLEMENTARY_MIN_VALUE =
        UTF16.SUPPLEMENTARY_MIN_VALUE;

    /**
     * Special value that is returned by getUnicodeNumericValue(int) when no
     * numeric value is defined for a code point.
     * @stable ICU 2.4
     * @see #getUnicodeNumericValue
     */
    public static final double NO_NUMERIC_VALUE = -123456789;

    // public methods ----------------------------------------------------

    /**
     * Retrieves the numeric value of a decimal digit code point.
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
        // when ch is out of bounds getProperty == 0
        int props = getProperty(ch);
        if (getNumericType(props) != NumericType.DECIMAL) {
            return (radix <= 10) ? -1 : getEuropeanDigit(ch);
        }
        // if props == 0, it will just fall through and return -1
        if (isNotExceptionIndicator(props)) {
        // not contained in exception data
            // getSignedValue is just shifting so we can check for the sign
            // first
            // Optimization
            // int result = UCharacterProperty.getSignedValue(props);
            // if (result >= 0) {
            //    return result;
            // }
            if (props >= 0) {
                return UCharacterProperty.getSignedValue(props);
            }
        }
        else {
            int index = UCharacterProperty.getExceptionIndex(props);
        if (PROPERTY_.hasExceptionValue(index,
                        UCharacterProperty.EXC_NUMERIC_VALUE_)) {
                int result = PROPERTY_.getException(index,
                            UCharacterProperty.EXC_NUMERIC_VALUE_);
                if (result >= 0) {
                    return result;
                }
            }
        }

        if (radix > 10) {
            int result = getEuropeanDigit(ch);
            if (result >= 0 && result < radix) {
                return result;
            }
        }
        return -1;
    }

    /**
     * <p>Get the numeric value for a Unicode code point as defined in the
     * Unicode Character Database.</p>
     * <p>A "double" return type is necessary because some numeric values are
     * fractions, negative, or too large for int.</p>
     * <p>For characters without any numeric values in the Unicode Character
     * Database, this function will return NO_NUMERIC_VALUE.</p>
     * <p><em>API Change:</em> In release 2.2 and prior, this API has a
     * return type int and returns -1 when the argument ch does not have a
     * corresponding numeric value. This has been changed to synch with ICU4C
     * </p>
     * This corresponds to the ICU4C function u_getNumericValue.
     * @param ch Code point to get the numeric value for.
     * @return numeric value of ch, or NO_NUMERIC_VALUE if none is defined.
     * @stable ICU 2.4
     */
    public static double getUnicodeNumericValue(int ch)
    {
        // equivalent to c version double u_getNumericValue(UChar32 c)
        int props = PROPERTY_.getProperty(ch);
        int numericType = getNumericType(props);
        if (numericType > NumericType.NONE && numericType < NumericType.COUNT) {
            if (isNotExceptionIndicator(props)) {
                return UCharacterProperty.getSignedValue(props);
            }
            else {
                int index = UCharacterProperty.getExceptionIndex(props);
                boolean nex = false;
                boolean dex = false;
                double numerator = 0;
                if (PROPERTY_.hasExceptionValue(index,
                        UCharacterProperty.EXC_NUMERIC_VALUE_)) {
                    int num = PROPERTY_.getException(index,
                             UCharacterProperty.EXC_NUMERIC_VALUE_);
                    // There are special values for huge numbers that are
                    // powers of ten. genprops/store.c documents:
                    // if numericValue = 0x7fffff00 + x then
                    // numericValue = 10 ^ x
                    if (num >= NUMERATOR_POWER_LIMIT_) {
                        num &= 0xff;
                        // 10^x without math.h
                        numerator = Math.pow(10, num);
                    }
                    else {
                        numerator = num;
                    }
                    nex = true;
                }
                double denominator = 0;
                if (PROPERTY_.hasExceptionValue(index,
                        UCharacterProperty.EXC_DENOMINATOR_VALUE_)) {
                    denominator = PROPERTY_.getException(index,
                             UCharacterProperty.EXC_DENOMINATOR_VALUE_);
                    // faster path not in c
                    if (numerator != 0) {
                        return numerator / denominator;
                    }
                    dex = true;
                }

                if (nex) {
                    if (dex) {
                        return numerator / denominator;
                    }
                    return numerator;
                }
                if (dex) {
                    return 1 / denominator;
                }
            }
        }
        return NO_NUMERIC_VALUE;
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
     * values, though similar, skip the value 17.</p>
     * @param ch code point whose type is to be determined
     * @return category which is a value of UCharacterCategory
     * @stable ICU 2.1
     */
    public static int getType(int ch)
    {
        return getProperty(ch) & UCharacterProperty.TYPE_MASK;
    }

    //// for StringPrep
    /**
     * Returns a code point corresponding to the two UTF16 characters.
     * @param lead the lead char
     * @param trail the trail char
     * @return code point if surrogate characters are valid.
     * @exception IllegalArgumentException thrown when argument characters do
     *            not form a valid codepoint
     * @stable ICU 2.1
     */
    public static int getCodePoint(char lead, char trail)
    {
        if (lead >= UTF16.LEAD_SURROGATE_MIN_VALUE &&
        lead <= UTF16.LEAD_SURROGATE_MAX_VALUE &&
            trail >= UTF16.TRAIL_SURROGATE_MIN_VALUE &&
        trail <= UTF16.TRAIL_SURROGATE_MAX_VALUE) {
            return UCharacterProperty.getRawSupplementary(lead, trail);
        }
        throw new IllegalArgumentException("Illegal surrogate characters");
    }

    //// for StringPrep
    /**
     * Returns the Bidirection property of a code point.
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
        // when ch is out of bounds getProperty == 0
        return (getProperty(ch) >> BIDI_SHIFT_) & BIDI_MASK_AFTER_SHIFT_;
    }

    /**
     * The given string is mapped to its case folding equivalent according to
     * UnicodeData.txt and CaseFolding.txt; if any character has no case
     * folding equivalent, the character itself is returned.
     * "Full", multiple-code point case folding mappings are returned here.
     * For "simple" single-code point mappings use the API
     * foldCase(int ch, boolean defaultmapping).
     * @param str            the String to be converted
     * @param defaultmapping Indicates if all mappings defined in
     *                       CaseFolding.txt is to be used, otherwise the
     *                       mappings for dotted I and dotless i marked with
     *                       'I' in CaseFolding.txt will be skipped.
     * @return               the case folding equivalent of the character, if
     *                       any; otherwise the character itself.
     * @see                  #foldCase(int, boolean)
     * @stable ICU 2.1
     */
    public static String foldCase(String str, boolean defaultmapping)
    {
        int          size   = str.length();
        StringBuffer result = new StringBuffer(size);
        int          offset  = 0;
        int          ch;

        // case mapping loop
        while (offset < size) {
            ch = UTF16.charAt(str, offset);
            offset += UTF16.getCharCount(ch);
            int props = PROPERTY_.getProperty(ch);
            if (isNotExceptionIndicator(props)) {
                int type = UCharacterProperty.TYPE_MASK & props;
                if (type == ECharacterCategory.UPPERCASE_LETTER ||
                    type == ECharacterCategory.TITLECASE_LETTER) {
                    ch += UCharacterProperty.getSignedValue(props);
                }
            }
            else {
                int index = UCharacterProperty.getExceptionIndex(props);
                if (PROPERTY_.hasExceptionValue(index,
                        UCharacterProperty.EXC_CASE_FOLDING_)) {
                    int exception = PROPERTY_.getException(index,
                               UCharacterProperty.EXC_CASE_FOLDING_);
                    if (exception != 0) {
                        PROPERTY_.getFoldCase(exception & LAST_CHAR_MASK_,
                          exception >> SHIFT_24_, result);
                    }
                    else {
                        // special case folding mappings, hardcoded
                        if (ch != 0x49 && ch != 0x130) {
                            // return ch itself because there is no special
                            // mapping for it
                            UTF16.append(result, ch);
                            continue;
                        }
                        if (defaultmapping) {
                            // default mappings
                            if (ch == 0x49) {
                                // 0049; C; 0069; # LATIN CAPITAL LETTER I
                                result.append(
                          UCharacterProperty.LATIN_SMALL_LETTER_I_);
                            }
                            else if (ch == 0x130) {
                                // 0130; F; 0069 0307;
                                // # LATIN CAPITAL LETTER I WITH DOT ABOVE
                                result.append(
                          UCharacterProperty.LATIN_SMALL_LETTER_I_);
                                result.append((char)0x307);
                            }
                        }
                        else {
                            // Turkic mappings
                            if (ch == 0x49) {
                                // 0049; T; 0131; # LATIN CAPITAL LETTER I
                                result.append((char)0x131);
                            }
                            else if (ch == 0x130) {
                                // 0130; T; 0069;
                                // # LATIN CAPITAL LETTER I WITH DOT ABOVE
                                result.append(
                          UCharacterProperty.LATIN_SMALL_LETTER_I_);
                            }
                        }
                    }
                    // do not fall through to the output of c
                    continue;
                }
                else {
                    if (PROPERTY_.hasExceptionValue(index,
                            UCharacterProperty.EXC_LOWERCASE_)) {
                        ch = PROPERTY_.getException(index,
                            UCharacterProperty.EXC_LOWERCASE_);
                    }
                }

            }

            // handle 1:1 code point mappings from UnicodeData.txt
            UTF16.append(result, ch);
        }

        return result.toString();
    }

    /**
     * <p>Get the "age" of the code point.</p>
     * <p>The "age" is the Unicode version when the code point was first
     * designated (as a non-character or for Private Use) or assigned a
     * character.
     * <p>This can be useful to avoid emitting code points to receiving
     * processes that do not accept newer characters.</p>
     * <p>The data is from the UCD file DerivedAge.txt.</p>
     * @param ch The code point.
     * @return the Unicode version number
     * @stable ICU 2.6
     */
    public static VersionInfo getAge(int ch)
    {
        if (ch < MIN_VALUE || ch > MAX_VALUE) {
        throw new IllegalArgumentException("Codepoint out of bounds");
        }
        return PROPERTY_.getAge(ch);
    }

    /**
     * <p>Gets the property value for an Unicode property type of a code point.
     * Also returns binary and mask property values.</p>
     * <p>Unicode, especially in version 3.2, defines many more properties than
     * the original set in UnicodeData.txt.</p>
     * <p>The properties APIs are intended to reflect Unicode properties as
     * defined in the Unicode Character Database (UCD) and Unicode Technical
     * Reports (UTR). For details about the properties see
     * http://www.unicode.org/.</p>
     * <p>For names of Unicode properties see the UCD file PropertyAliases.txt.
     * </p>
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
     *         the enumerated constant of the respective property value
     *         enumeration type (cast to enum type if necessary).
     *         Returns 0 or 1 (for false / true) for binary Unicode properties.
     *         Returns a bit-mask for mask properties.
     *         Returns 0 if 'type' is out of bounds or if the Unicode version
     *         does not have data for the property at all, or not for this code
     *         point.
     * @see UProperty
     * @see #hasBinaryProperty
     * @see #getIntPropertyMinValue
     * @see #getIntPropertyMaxValue
     * @see #getUnicodeVersion
     * @stable ICU 2.4
     */
    public static int getIntPropertyValue(int ch, int type)
    {
        /*
         * For Normalizer with Unicode 3.2, this method is called only for
         * HANGUL_SYLLABLE_TYPE in UnicodeSet.addPropertyStarts().
         */
        if (type == UProperty.HANGUL_SYLLABLE_TYPE) {
        /* purely algorithmic; hardcode known characters, check for assigned new ones */
        if(ch<NormalizerImpl.JAMO_L_BASE) {
            /* NA */
        } else if(ch<=0x11ff) {
            /* Jamo range */
            if(ch<=0x115f) {
            /* Jamo L range, HANGUL CHOSEONG ... */
            if(ch==0x115f || ch<=0x1159 || getType(ch)==ECharacterCategory.OTHER_LETTER) {
                return HangulSyllableType.LEADING_JAMO;
            }
            } else if(ch<=0x11a7) {
            /* Jamo V range, HANGUL JUNGSEONG ... */
            if(ch<=0x11a2 || getType(ch)==ECharacterCategory.OTHER_LETTER) {
                return HangulSyllableType.VOWEL_JAMO;
            }
            } else {
            /* Jamo T range */
            if(ch<=0x11f9 || getType(ch)==ECharacterCategory.OTHER_LETTER) {
                return HangulSyllableType.TRAILING_JAMO;
            }
            }
        } else if((ch-=NormalizerImpl.HANGUL_BASE)<0) {
            /* NA */
        } else if(ch<NormalizerImpl.HANGUL_COUNT) {
            /* Hangul syllable */
            return ch%NormalizerImpl.JAMO_T_COUNT==0 ? HangulSyllableType.LV_SYLLABLE : HangulSyllableType.LVT_SYLLABLE;
        }
        }
        return 0; /* NA */
    }

    // private variables -------------------------------------------------

    /**
     * Database storing the sets of character property
     */
    private static final UCharacterProperty PROPERTY_;
    /**
     * For optimization
     */
    private static final char[] PROPERTY_TRIE_INDEX_;
    private static final char[] PROPERTY_TRIE_DATA_;
    private static final int[] PROPERTY_DATA_;
    private static final int PROPERTY_INITIAL_VALUE_;

    // block to initialise character property database
    static
    {
        try
        {
        PROPERTY_ = UCharacterProperty.getInstance();
        PROPERTY_TRIE_INDEX_ = PROPERTY_.m_trieIndex_;
        PROPERTY_TRIE_DATA_ = PROPERTY_.m_trieData_;
        PROPERTY_DATA_ = PROPERTY_.m_property_;
        PROPERTY_INITIAL_VALUE_
            = PROPERTY_DATA_[PROPERTY_.m_trieInitialValue_];
        }
        catch (Exception e)
        {
        throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * To get the last character out from a data type
     */
    private static final int LAST_CHAR_MASK_ = 0xFFFF;

    /**
     * To get the last byte out from a data type
     */
//    private static final int LAST_BYTE_MASK_ = 0xFF;

    /**
     * Shift 16 bits
     */
//    private static final int SHIFT_16_ = 16;

    /**
     * Shift 24 bits
     */
    private static final int SHIFT_24_ = 24;

    /**
     * Shift to get numeric type
     */
    private static final int NUMERIC_TYPE_SHIFT_ = 12;
    /**
     * Mask to get numeric type
     */
    private static final int NUMERIC_TYPE_MASK_ = 0x7 << NUMERIC_TYPE_SHIFT_;
    /**
     * Shift to get bidi bits
     */
    private static final int BIDI_SHIFT_ = 6;

    /**
     * Mask to be applied after shifting to get bidi bits
     */
    private static final int BIDI_MASK_AFTER_SHIFT_ = 0x1F;

    /**
     * <p>Numerator power limit.
     * There are special values for huge numbers that are powers of ten.</p>
     * <p>c version genprops/store.c documents:
     * if numericValue = 0x7fffff00 + x then numericValue = 10 ^ x</p>
     */
    private static final int NUMERATOR_POWER_LIMIT_ = 0x7fffff00;
    /**
     * Integer properties mask and shift values for joining type.
     * Equivalent to icu4c UPROPS_JT_MASK.
     */
    private static final int JOINING_TYPE_MASK_ = 0x00003800;
    /**
     * Integer properties mask and shift values for joining type.
     * Equivalent to icu4c UPROPS_JT_SHIFT.
     */
    private static final int JOINING_TYPE_SHIFT_ = 11;
    /**
     * Integer properties mask and shift values for joining group.
     * Equivalent to icu4c UPROPS_JG_MASK.
     */
    private static final int JOINING_GROUP_MASK_ = 0x000007e0;
    /**
     * Integer properties mask and shift values for joining group.
     * Equivalent to icu4c UPROPS_JG_SHIFT.
     */
    private static final int JOINING_GROUP_SHIFT_ = 5;
    /**
     * Integer properties mask for decomposition type.
     * Equivalent to icu4c UPROPS_DT_MASK.
     */
    private static final int DECOMPOSITION_TYPE_MASK_ = 0x0000001f;
    /**
     * Integer properties mask and shift values for East Asian cell width.
     * Equivalent to icu4c UPROPS_EA_MASK
     */
    private static final int EAST_ASIAN_MASK_ = 0x00038000;
    /**
     * Integer properties mask and shift values for East Asian cell width.
     * Equivalent to icu4c UPROPS_EA_SHIFT
     */
    private static final int EAST_ASIAN_SHIFT_ = 15;

    /**
     * Integer properties mask and shift values for line breaks.
     * Equivalent to icu4c UPROPS_LB_MASK
     */
    private static final int LINE_BREAK_MASK_ = 0x007C0000;
    /**
     * Integer properties mask and shift values for line breaks.
     * Equivalent to icu4c UPROPS_LB_SHIFT
     */
    private static final int LINE_BREAK_SHIFT_ = 18;
    /**
     * Integer properties mask and shift values for blocks.
     * Equivalent to icu4c UPROPS_BLOCK_MASK
     */
    private static final int BLOCK_MASK_ = 0x00007f80;
    /**
     * Integer properties mask and shift values for blocks.
     * Equivalent to icu4c UPROPS_BLOCK_SHIFT
     */
    private static final int BLOCK_SHIFT_ = 7;
    /**
     * Integer properties mask and shift values for scripts.
     * Equivalent to icu4c UPROPS_SHIFT_MASK
     */
    private static final int SCRIPT_MASK_ = 0x0000007f;

    // private constructor -----------------------------------------------
    ///CLOVER:OFF
    /**
     * Private constructor to prevent instantiation
     */
    private UCharacter()
    {
    }
    ///CLOVER:ON
    // private methods ---------------------------------------------------

    /**
     * Getting the digit values of characters like 'A' - 'Z', normal,
     * half-width and full-width. This method assumes that the other digit
     * characters are checked by the calling method.
     * @param ch character to test
     * @return -1 if ch is not a character of the form 'A' - 'Z', otherwise
     *         its corresponding digit will be returned.
     */
    private static int getEuropeanDigit(int ch) {
        if ((ch > 0x7a && ch < 0xff21)
            || ch < 0x41 || (ch > 0x5a && ch < 0x61)
            || ch > 0xff5a || (ch > 0xff31 && ch < 0xff41)) {
            return -1;
        }
        if (ch <= 0x7a) {
            // ch >= 0x41 or ch < 0x61
            return ch + 10 - ((ch <= 0x5a) ? 0x41 : 0x61);
        }
        // ch >= 0xff21
        if (ch <= 0xff3a) {
            return ch + 10 - 0xff21;
        }
        // ch >= 0xff41 && ch <= 0xff5a
        return ch + 10 - 0xff41;
    }

    /**
     * Gets the numeric type of the property argument
     * @param props 32 bit property
     * @return the numeric type
     */
    private static int getNumericType(int props)
    {
        return (props & NUMERIC_TYPE_MASK_) >> NUMERIC_TYPE_SHIFT_;
    }

    /**
     * Checks if the property value has a exception indicator
     * @param props 32 bit property value
     * @return true if property does not have a exception indicator, false
     *          otherwise
     */
    private static boolean isNotExceptionIndicator(int props)
    {
    return (props & UCharacterProperty.EXCEPTION_MASK) == 0;
    }

    /**
     * Gets the property value at the index.
     * This is optimized.
     * Note this is alittle different from CharTrie the index m_trieData_
     * is never negative.
     * This is a duplicate of UCharacterProperty.getProperty. For optimization
     * purposes, this method calls the trie data directly instead of through
     * UCharacterProperty.getProperty.
     * @param ch code point whose property value is to be retrieved
     * @return property value of code point
     * @stable ICU 2.6
     */
    private static int getProperty(int ch)
    {
        if (ch < UTF16.LEAD_SURROGATE_MIN_VALUE
            || (ch > UTF16.LEAD_SURROGATE_MAX_VALUE
                && ch < UTF16.SUPPLEMENTARY_MIN_VALUE)) {
            // BMP codepoint
            try { // using try for < 0 ch is faster than using an if statement
                return PROPERTY_DATA_[
                      PROPERTY_TRIE_DATA_[
                              (PROPERTY_TRIE_INDEX_[ch >> 5] << 2)
                              + (ch & 0x1f)]];
            } catch (ArrayIndexOutOfBoundsException e) {
                return PROPERTY_INITIAL_VALUE_;
            }
        }
        if (ch <= UTF16.LEAD_SURROGATE_MAX_VALUE) {
            // surrogate
            return PROPERTY_DATA_[
                  PROPERTY_TRIE_DATA_[
                              (PROPERTY_TRIE_INDEX_[(0x2800 >> 5) + (ch >> 5)] << 2)
                              + (ch & 0x1f)]];
        }
        // for optimization
        if (ch <= UTF16.CODEPOINT_MAX_VALUE) {
            // look at the construction of supplementary characters
            // trail forms the ends of it.
            return PROPERTY_DATA_[PROPERTY_.m_trie_.getSurrogateValue(
                                      UTF16.getLeadSurrogate(ch),
                                      (char)(ch & 0x3ff))];
        }
        // return m_dataOffset_ if there is an error, in this case we return
        // the default value: m_initialValue_
        // we cannot assume that m_initialValue_ is at offset 0
        // this is for optimization.
        return PROPERTY_INITIAL_VALUE_;
    }
}
