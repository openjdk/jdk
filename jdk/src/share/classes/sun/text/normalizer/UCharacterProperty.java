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

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.MissingResourceException;

/**
* <p>Internal class used for Unicode character property database.</p>
* <p>This classes store binary data read from uprops.icu.
* It does not have the capability to parse the data into more high-level
* information. It only returns bytes of information when required.</p>
* <p>Due to the form most commonly used for retrieval, array of char is used
* to store the binary data.</p>
* <p>UCharacterPropertyDB also contains information on accessing indexes to
* significant points in the binary data.</p>
* <p>Responsibility for molding the binary data into more meaning form lies on
* <a href=UCharacter.html>UCharacter</a>.</p>
* @author Syn Wee Quek
* @since release 2.1, february 1st 2002
*/

public final class UCharacterProperty
{
    // public data members -----------------------------------------------

    /**
    * Trie data
    */
    public CharTrie m_trie_;
    /**
     * Optimization
     * CharTrie index array
     */
    public char[] m_trieIndex_;
    /**
     * Optimization
     * CharTrie data array
     */
    public char[] m_trieData_;
    /**
     * Optimization
     * CharTrie data offset
     */
    public int m_trieInitialValue_;
    /**
    * Unicode version
    */
    public VersionInfo m_unicodeVersion_;

    // uprops.h enum UPropertySource --------------------------------------- ***

    /** From uchar.c/uprops.icu properties vectors trie */
    public static final int SRC_PROPSVEC=2;
    /** One more than the highest UPropertySource (SRC_) constant. */
    public static final int SRC_COUNT=9;

    // public methods ----------------------------------------------------

    /**
     * Java friends implementation
     */
    public void setIndexData(CharTrie.FriendAgent friendagent)
    {
        m_trieIndex_ = friendagent.getPrivateIndex();
        m_trieData_ = friendagent.getPrivateData();
        m_trieInitialValue_ = friendagent.getPrivateInitialValue();
    }

    /**
    * Gets the property value at the index.
    * This is optimized.
    * Note this is alittle different from CharTrie the index m_trieData_
    * is never negative.
    * @param ch code point whose property value is to be retrieved
    * @return property value of code point
    */
    public final int getProperty(int ch)
    {
        if (ch < UTF16.LEAD_SURROGATE_MIN_VALUE
            || (ch > UTF16.LEAD_SURROGATE_MAX_VALUE
                && ch < UTF16.SUPPLEMENTARY_MIN_VALUE)) {
            // BMP codepoint 0000..D7FF or DC00..FFFF
            // optimized
            try { // using try for ch < 0 is faster than using an if statement
                return m_trieData_[
                    (m_trieIndex_[ch >> Trie.INDEX_STAGE_1_SHIFT_]
                          << Trie.INDEX_STAGE_2_SHIFT_)
                    + (ch & Trie.INDEX_STAGE_3_MASK_)];
            } catch (ArrayIndexOutOfBoundsException e) {
                return m_trieInitialValue_;
            }
        }
        if (ch <= UTF16.LEAD_SURROGATE_MAX_VALUE) {
            // lead surrogate D800..DBFF
            return m_trieData_[
                    (m_trieIndex_[Trie.LEAD_INDEX_OFFSET_
                                  + (ch >> Trie.INDEX_STAGE_1_SHIFT_)]
                          << Trie.INDEX_STAGE_2_SHIFT_)
                    + (ch & Trie.INDEX_STAGE_3_MASK_)];
        }
        if (ch <= UTF16.CODEPOINT_MAX_VALUE) {
            // supplementary code point 10000..10FFFF
            // look at the construction of supplementary characters
            // trail forms the ends of it.
            return m_trie_.getSurrogateValue(
                                          UTF16.getLeadSurrogate(ch),
                                          (char)(ch & Trie.SURROGATE_MASK_));
        }
        // ch is out of bounds
        // return m_dataOffset_ if there is an error, in this case we return
        // the default value: m_initialValue_
        // we cannot assume that m_initialValue_ is at offset 0
        // this is for optimization.
        return m_trieInitialValue_;

        // this all is an inlined form of return m_trie_.getCodePointValue(ch);
    }

    /**
    * Getting the unsigned numeric value of a character embedded in the property
    * argument
    * @param prop the character
    * @return unsigned numberic value
    */
    public static int getUnsignedValue(int prop)
    {
        return (prop >> VALUE_SHIFT_) & UNSIGNED_VALUE_MASK_AFTER_SHIFT_;
    }

    /**
     * Gets the unicode additional properties.
     * C version getUnicodeProperties.
     * @param codepoint codepoint whose additional properties is to be
     *                  retrieved
     * @param column
     * @return unicode properties
     */
       public int getAdditional(int codepoint, int column) {
        if (column == -1) {
            return getProperty(codepoint);
        }
           if (column < 0 || column >= m_additionalColumnsCount_) {
           return 0;
       }
       return m_additionalVectors_[
                     m_additionalTrie_.getCodePointValue(codepoint) + column];
       }

       /**
     * <p>Get the "age" of the code point.</p>
     * <p>The "age" is the Unicode version when the code point was first
     * designated (as a non-character or for Private Use) or assigned a
     * character.</p>
     * <p>This can be useful to avoid emitting code points to receiving
     * processes that do not accept newer characters.</p>
     * <p>The data is from the UCD file DerivedAge.txt.</p>
     * <p>This API does not check the validity of the codepoint.</p>
     * @param codepoint The code point.
     * @return the Unicode version number
     */
    public VersionInfo getAge(int codepoint)
    {
        int version = getAdditional(codepoint, 0) >> AGE_SHIFT_;
        return VersionInfo.getInstance(
                           (version >> FIRST_NIBBLE_SHIFT_) & LAST_NIBBLE_MASK_,
                           version & LAST_NIBBLE_MASK_, 0, 0);
    }

    /**
    * Forms a supplementary code point from the argument character<br>
    * Note this is for internal use hence no checks for the validity of the
    * surrogate characters are done
    * @param lead lead surrogate character
    * @param trail trailing surrogate character
    * @return code point of the supplementary character
    */
    public static int getRawSupplementary(char lead, char trail)
    {
        return (lead << LEAD_SURROGATE_SHIFT_) + trail + SURROGATE_OFFSET_;
    }

    /**
    * Loads the property data and initialize the UCharacterProperty instance.
    * @throws MissingResourceException when data is missing or data has been corrupted
    */
    public static UCharacterProperty getInstance()
    {
        if(INSTANCE_ == null) {
            try {
                INSTANCE_ = new UCharacterProperty();
            }
            catch (Exception e) {
                throw new MissingResourceException(e.getMessage(),"","");
            }
        }
        return INSTANCE_;
    }

    /**
     * Checks if the argument c is to be treated as a white space in ICU
     * rules. Usually ICU rule white spaces are ignored unless quoted.
     * Equivalent to test for Pattern_White_Space Unicode property.
     * Stable set of characters, won't change.
     * See UAX #31 Identifier and Pattern Syntax: http://www.unicode.org/reports/tr31/
     * @param c codepoint to check
     * @return true if c is a ICU white space
     */
    public static boolean isRuleWhiteSpace(int c)
    {
        /* "white space" in the sense of ICU rule parsers
           This is a FIXED LIST that is NOT DEPENDENT ON UNICODE PROPERTIES.
           See UAX #31 Identifier and Pattern Syntax: http://www.unicode.org/reports/tr31/
           U+0009..U+000D, U+0020, U+0085, U+200E..U+200F, and U+2028..U+2029
           Equivalent to test for Pattern_White_Space Unicode property.
        */
        return (c >= 0x0009 && c <= 0x2029 &&
                (c <= 0x000D || c == 0x0020 || c == 0x0085 ||
                 c == 0x200E || c == 0x200F || c >= 0x2028));
    }

    // protected variables -----------------------------------------------

    /**
     * Extra property trie
     */
    CharTrie m_additionalTrie_;
    /**
     * Extra property vectors, 1st column for age and second for binary
     * properties.
     */
    int m_additionalVectors_[];
    /**
     * Number of additional columns
     */
    int m_additionalColumnsCount_;
    /**
     * Maximum values for block, bits used as in vector word
     * 0
     */
    int m_maxBlockScriptValue_;
    /**
     * Maximum values for script, bits used as in vector word
     * 0
     */
     int m_maxJTGValue_;

    // private variables -------------------------------------------------

      /**
     * UnicodeData.txt property object
     */
    private static UCharacterProperty INSTANCE_ = null;

    /**
    * Default name of the datafile
    */
    private static final String DATA_FILE_NAME_ = "/sun/text/resources/uprops.icu";

    /**
    * Default buffer size of datafile
    */
    private static final int DATA_BUFFER_SIZE_ = 25000;

    /**
    * Numeric value shift
    */
    private static final int VALUE_SHIFT_ = 8;

    /**
    * Mask to be applied after shifting to obtain an unsigned numeric value
    */
    private static final int UNSIGNED_VALUE_MASK_AFTER_SHIFT_ = 0xFF;

    /**
    * Shift value for lead surrogate to form a supplementary character.
    */
    private static final int LEAD_SURROGATE_SHIFT_ = 10;
    /**
    * Offset to add to combined surrogate pair to avoid msking.
    */
    private static final int SURROGATE_OFFSET_ =
                           UTF16.SUPPLEMENTARY_MIN_VALUE -
                           (UTF16.SURROGATE_MIN_VALUE <<
                           LEAD_SURROGATE_SHIFT_) -
                           UTF16.TRAIL_SURROGATE_MIN_VALUE;

    // additional properties ----------------------------------------------

    /**
     * First nibble shift
     */
    private static final int FIRST_NIBBLE_SHIFT_ = 0x4;
    /**
     * Second nibble mask
     */
    private static final int LAST_NIBBLE_MASK_ = 0xF;
    /**
     * Age value shift
     */
    private static final int AGE_SHIFT_ = 24;

    // private constructors --------------------------------------------------

    /**
    * Constructor
    * @exception IOException thrown when data reading fails or data corrupted
    */
    private UCharacterProperty() throws IOException
    {
        // jar access
        InputStream is = ICUData.getRequiredStream(DATA_FILE_NAME_);
        BufferedInputStream b = new BufferedInputStream(is, DATA_BUFFER_SIZE_);
        UCharacterPropertyReader reader = new UCharacterPropertyReader(b);
        reader.read(this);
        b.close();

        m_trie_.putIndexData(this);
    }

    public void upropsvec_addPropertyStarts(UnicodeSet set) {
        /* add the start code point of each same-value range of the properties vectors trie */
        if(m_additionalColumnsCount_>0) {
            /* if m_additionalColumnsCount_==0 then the properties vectors trie may not be there at all */
            TrieIterator propsVectorsIter = new TrieIterator(m_additionalTrie_);
            RangeValueIterator.Element propsVectorsResult = new RangeValueIterator.Element();
            while(propsVectorsIter.next(propsVectorsResult)){
                set.add(propsVectorsResult.start);
            }
        }
    }

}
