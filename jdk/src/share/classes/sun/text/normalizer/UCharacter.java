/*
 * Portions Copyright 2005-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * (C) Copyright IBM Corp. and others, 1996-2009 - All Rights Reserved         *
 *                                                                             *
 * The original version of this source code and documentation is copyrighted   *
 * and owned by IBM, These materials are provided under terms of a License     *
 * Agreement between IBM and Sun. This technology is protected by multiple     *
 * US and International patents. This notice and attribution to IBM may not    *
 * to removed.                                                                 *
 *******************************************************************************
 */

package sun.text.normalizer;

import java.io.IOException;
import java.util.MissingResourceException;

/**
 * <p>
 * The UCharacter class provides extensions to the
 * <a href="http://java.sun.com/j2se/1.5/docs/api/java/lang/Character.html">
 * java.lang.Character</a> class. These extensions provide support for
 * more Unicode properties and together with the <a href=../text/UTF16.html>UTF16</a>
 * class, provide support for supplementary characters (those with code
 * points above U+FFFF).
 * Each ICU release supports the latest version of Unicode available at that time.
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
 * Aside from the additions for UTF-16 support, and the updated Unicode
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
 * Further detail differences can be determined from the program
 *        <a href="http://source.icu-project.org/repos/icu/icu4j/trunk/src/com/ibm/icu/dev/test/lang/UCharacterCompare.java">
 *        com.ibm.icu.dev.test.lang.UCharacterCompare</a>
 * </p>
 * <p>
 * In addition to Java compatibility functions, which calculate derived properties,
 * this API provides low-level access to the Unicode Character Database.
 * </p>
 * <p>
 * Unicode assigns each code point (not just assigned character) values for
 * many properties.
 * Most of them are simple boolean flags, or constants from a small enumerated list.
 * For some properties, values are strings or other relatively more complex types.
 * </p>
 * <p>
 * For more information see
 * "About the Unicode Character Database" (http://www.unicode.org/ucd/)
 * and the ICU User Guide chapter on Properties (http://www.icu-project.org/userguide/properties.html).
 * </p>
 * <p>
 * There are also functions that provide easy migration from C/POSIX functions
 * like isblank(). Their use is generally discouraged because the C/POSIX
 * standards do not define their semantics beyond the ASCII range, which means
 * that different implementations exhibit very different behavior.
 * Instead, Unicode properties should be used directly.
 * </p>
 * <p>
 * There are also only a few, broad C/POSIX character classes, and they tend
 * to be used for conflicting purposes. For example, the "isalpha()" class
 * is sometimes used to determine word boundaries, while a more sophisticated
 * approach would at least distinguish initial letters from continuation
 * characters (the latter including combining marks).
 * (In ICU, BreakIterator is the most sophisticated API for word boundaries.)
 * Another example: There is no "istitle()" class for titlecase characters.
 * </p>
 * <p>
 * ICU 3.4 and later provides API access for all twelve C/POSIX character classes.
 * ICU implements them according to the Standard Recommendations in
 * Annex C: Compatibility Properties of UTS #18 Unicode Regular Expressions
 * (http://www.unicode.org/reports/tr18/#Compatibility_Properties).
 * </p>
 * <p>
 * API access for C/POSIX character classes is as follows:
 * - alpha:     isUAlphabetic(c) or hasBinaryProperty(c, UProperty.ALPHABETIC)
 * - lower:     isULowercase(c) or hasBinaryProperty(c, UProperty.LOWERCASE)
 * - upper:     isUUppercase(c) or hasBinaryProperty(c, UProperty.UPPERCASE)
 * - punct:     ((1<<getType(c)) & ((1<<DASH_PUNCTUATION)|(1<<START_PUNCTUATION)|(1<<END_PUNCTUATION)|(1<<CONNECTOR_PUNCTUATION)|(1<<OTHER_PUNCTUATION)|(1<<INITIAL_PUNCTUATION)|(1<<FINAL_PUNCTUATION)))!=0
 * - digit:     isDigit(c) or getType(c)==DECIMAL_DIGIT_NUMBER
 * - xdigit:    hasBinaryProperty(c, UProperty.POSIX_XDIGIT)
 * - alnum:     hasBinaryProperty(c, UProperty.POSIX_ALNUM)
 * - space:     isUWhiteSpace(c) or hasBinaryProperty(c, UProperty.WHITE_SPACE)
 * - blank:     hasBinaryProperty(c, UProperty.POSIX_BLANK)
 * - cntrl:     getType(c)==CONTROL
 * - graph:     hasBinaryProperty(c, UProperty.POSIX_GRAPH)
 * - print:     hasBinaryProperty(c, UProperty.POSIX_PRINT)
 * </p>
 * <p>
 * The C/POSIX character classes are also available in UnicodeSet patterns,
 * using patterns like [:graph:] or \p{graph}.
 * </p>
 * <p>
 * Note: There are several ICU (and Java) whitespace functions.
 * Comparison:
 * - isUWhiteSpace=UCHAR_WHITE_SPACE: Unicode White_Space property;
 *       most of general categories "Z" (separators) + most whitespace ISO controls
 *       (including no-break spaces, but excluding IS1..IS4 and ZWSP)
 * - isWhitespace: Java isWhitespace; Z + whitespace ISO controls but excluding no-break spaces
 * - isSpaceChar: just Z (including no-break spaces)
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
        public static final int DECIMAL = 1;
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
        int value;
        if (getNumericType(props) == NumericType.DECIMAL) {
            value = UCharacterProperty.getUnsignedValue(props);
        } else {
            value = getEuropeanDigit(ch);
        }
        return (0 <= value && value < radix) ? value : -1;
    }

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
        return gBdp.getClass(ch);
    }

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
        if (UTF16.isLeadSurrogate(lead) && UTF16.isTrailSurrogate(trail)) {
            return UCharacterProperty.getRawSupplementary(lead, trail);
        }
        throw new IllegalArgumentException("Illegal surrogate characters");
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
    private static final int PROPERTY_INITIAL_VALUE_;

    private static final UBiDiProps gBdp;

    // block to initialise character property database
    static
    {
        try
        {
            PROPERTY_ = UCharacterProperty.getInstance();
            PROPERTY_TRIE_INDEX_ = PROPERTY_.m_trieIndex_;
            PROPERTY_TRIE_DATA_ = PROPERTY_.m_trieData_;
            PROPERTY_INITIAL_VALUE_ = PROPERTY_.m_trieInitialValue_;
        }
        catch (Exception e)
        {
            throw new MissingResourceException(e.getMessage(),"","");
        }

        UBiDiProps bdp;
        try {
            bdp=UBiDiProps.getSingleton();
        } catch(IOException e) {
            bdp=UBiDiProps.getDummy();
        }
        gBdp=bdp;
    }

    /**
     * Shift to get numeric type
     */
    private static final int NUMERIC_TYPE_SHIFT_ = 5;
    /**
     * Mask to get numeric type
     */
    private static final int NUMERIC_TYPE_MASK_ = 0x7 << NUMERIC_TYPE_SHIFT_;

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
    private static final int getProperty(int ch)
    {
        if (ch < UTF16.LEAD_SURROGATE_MIN_VALUE
            || (ch > UTF16.LEAD_SURROGATE_MAX_VALUE
                && ch < UTF16.SUPPLEMENTARY_MIN_VALUE)) {
            // BMP codepoint 0000..D7FF or DC00..FFFF
            try { // using try for ch < 0 is faster than using an if statement
                return PROPERTY_TRIE_DATA_[
                              (PROPERTY_TRIE_INDEX_[ch >> 5] << 2)
                              + (ch & 0x1f)];
            } catch (ArrayIndexOutOfBoundsException e) {
                return PROPERTY_INITIAL_VALUE_;
            }
        }
        if (ch <= UTF16.LEAD_SURROGATE_MAX_VALUE) {
            // lead surrogate D800..DBFF
            return PROPERTY_TRIE_DATA_[
                              (PROPERTY_TRIE_INDEX_[(0x2800 >> 5) + (ch >> 5)] << 2)
                              + (ch & 0x1f)];
        }
        // for optimization
        if (ch <= UTF16.CODEPOINT_MAX_VALUE) {
            // supplementary code point 10000..10FFFF
            // look at the construction of supplementary characters
            // trail forms the ends of it.
            return PROPERTY_.m_trie_.getSurrogateValue(
                                      UTF16.getLeadSurrogate(ch),
                                      (char)(ch & 0x3ff));
        }
        // return m_dataOffset_ if there is an error, in this case we return
        // the default value: m_initialValue_
        // we cannot assume that m_initialValue_ is at offset 0
        // this is for optimization.
        return PROPERTY_INITIAL_VALUE_;
    }

}
