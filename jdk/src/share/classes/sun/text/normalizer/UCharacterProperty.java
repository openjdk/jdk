/*
 * Portions Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.text.BreakIterator;
import java.util.Locale;

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
* @draft 2.1
*/

public final class UCharacterProperty implements Trie.DataManipulate
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
    * Character property table
    */
    public int m_property_[];
    /**
    * Unicode version
    */
    public VersionInfo m_unicodeVersion_;
    /**
     * Exception indicator for uppercase type
     */
    public static final int EXC_UPPERCASE_ = 0;
    /**
     * Exception indicator for lowercase type
     */
    public static final int EXC_LOWERCASE_ = 1;
    /**
     * Exception indicator for titlecase type
     */
    public static final int EXC_TITLECASE_ = 2;
    /**
     * Exception indicator for digit type
     */
    public static final int EXC_UNUSED_ = 3;
    /**
     * Exception indicator for numeric type
     */
    public static final int EXC_NUMERIC_VALUE_ = 4;
    /**
     * Exception indicator for denominator type
     */
    public static final int EXC_DENOMINATOR_VALUE_ = 5;
    /**
     * Exception indicator for mirror type
     */
    public static final int EXC_MIRROR_MAPPING_ = 6;
    /**
     * Exception indicator for special casing type
     */
    public static final int EXC_SPECIAL_CASING_ = 7;
    /**
     * Exception indicator for case folding type
     */
    public static final int EXC_CASE_FOLDING_ = 8;
    /**
     * EXC_COMBINING_CLASS_ is not found in ICU.
     * Used to retrieve the combining class of the character in the exception
     * value
     */
    public static final int EXC_COMBINING_CLASS_ = 9;

    /**
    * Latin lowercase i
    */
    public static final char LATIN_SMALL_LETTER_I_ = 0x69;
    /**
    * Character type mask
    */
    public static final int TYPE_MASK = 0x1F;
    /**
    * Exception test mask
    */
    public static final int EXCEPTION_MASK = 0x20;

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
    * Called by com.ibm.icu.util.Trie to extract from a lead surrogate's
    * data the index array offset of the indexes for that lead surrogate.
    * @param value data value for a surrogate from the trie, including the
    *        folding offset
    * @return data offset or 0 if there is no data for the lead surrogate
    */
    public int getFoldingOffset(int value)
    {
        if ((value & SUPPLEMENTARY_FOLD_INDICATOR_MASK_) != 0) {
            return (value & SUPPLEMENTARY_FOLD_OFFSET_MASK_);
        }
        else {
            return 0;
        }
    }

    /**
    * Gets the property value at the index.
    * This is optimized.
    * Note this is alittle different from CharTrie the index m_trieData_
    * is never negative.
    * @param ch code point whose property value is to be retrieved
    * @return property value of code point
    */
    public int getProperty(int ch)
    {
        if (ch < UTF16.LEAD_SURROGATE_MIN_VALUE
            || (ch > UTF16.LEAD_SURROGATE_MAX_VALUE
                && ch < UTF16.SUPPLEMENTARY_MIN_VALUE)) {
            // BMP codepoint
            // optimized
            try {
                return m_property_[
                    m_trieData_[
                    (m_trieIndex_[ch >> Trie.INDEX_STAGE_1_SHIFT_]
                          << Trie.INDEX_STAGE_2_SHIFT_)
                    + (ch & Trie.INDEX_STAGE_3_MASK_)]];
            } catch (ArrayIndexOutOfBoundsException e) {
                return m_property_[m_trieInitialValue_];
            }
        }
        if (ch <= UTF16.LEAD_SURROGATE_MAX_VALUE) {
            return m_property_[
                    m_trieData_[
                    (m_trieIndex_[Trie.LEAD_INDEX_OFFSET_
                                  + (ch >> Trie.INDEX_STAGE_1_SHIFT_)]
                          << Trie.INDEX_STAGE_2_SHIFT_)
                    + (ch & Trie.INDEX_STAGE_3_MASK_)]];
        }
        // for optimization
        if (ch <= UTF16.CODEPOINT_MAX_VALUE) {
            // look at the construction of supplementary characters
            // trail forms the ends of it.
            return m_property_[m_trie_.getSurrogateValue(
                                          UTF16.getLeadSurrogate(ch),
                                          (char)(ch & Trie.SURROGATE_MASK_))];
        }
        // return m_dataOffset_ if there is an error, in this case we return
        // the default value: m_initialValue_
        // we cannot assume that m_initialValue_ is at offset 0
        // this is for optimization.
        return m_property_[m_trieInitialValue_];
        // return m_property_[m_trie_.getCodePointValue(ch)];
    }

    /**
    * Getting the signed numeric value of a character embedded in the property
    * argument
    * @param prop the character
    * @return signed numberic value
    */
    public static int getSignedValue(int prop)
    {
        return (prop >> VALUE_SHIFT_);
    }

    /**
    * Getting the exception index for argument property
    * @param prop character property
    * @return exception index
    */
    public static int getExceptionIndex(int prop)
    {
        return (prop >> VALUE_SHIFT_) & UNSIGNED_VALUE_MASK_AFTER_SHIFT_;
    }

    /**
    * Determines if the exception value passed in has the kind of information
    * which the indicator wants, e.g if the exception value contains the digit
    * value of the character
    * @param index exception index
    * @param indicator type indicator
    * @return true if type value exist
    */
    public boolean hasExceptionValue(int index, int indicator)
    {
        return (m_exception_[index] & (1 << indicator)) != 0;
    }

    /**
    * Gets the exception value at the index, assuming that data type is
    * available. Result is undefined if data is not available. Use
    * hasExceptionValue() to determine data's availability.
    * @param index
    * @param etype exception data type
    * @return exception data type value at index
    */
    public int getException(int index, int etype)
    {
        // contained in exception data
        if (etype == EXC_COMBINING_CLASS_) {
            return m_exception_[index];
        }
        // contained in the exception digit address
        index = addExceptionOffset(m_exception_[index], etype, ++ index);
        return m_exception_[index];
    }

    /**
    * Gets the folded case value at the index
    * @param index of the case value to be retrieved
    * @param count number of characters to retrieve
    * @param str string buffer to which to append the result
    */
    public void getFoldCase(int index, int count, StringBuffer str)
    {
        // first 2 chars are for the simple mappings
        index += 2;
        while (count > 0) {
            str.append(m_case_[index]);
            index ++;
            count --;
        }
    }

    /**
     * Gets the unicode additional properties.
     * C version getUnicodeProperties.
     * @param codepoint codepoint whose additional properties is to be
     *                  retrieved
     * @return unicode properties
     */
       public int getAdditional(int codepoint) {
           return m_additionalVectors_[m_additionalTrie_.getCodePointValue(codepoint)];
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
     * @draft ICU 2.1
     */
    public VersionInfo getAge(int codepoint)
    {
        int version = getAdditional(codepoint) >> AGE_SHIFT_;
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
    * @throws RuntimeException when data is missing or data has been corrupted
    */
    public static UCharacterProperty getInstance() throws RuntimeException
    {
        if (INSTANCE_ == null) {
            try {
                INSTANCE_ = new UCharacterProperty();
            }
            catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }
        return INSTANCE_;
    }

    /**
     * Checks if the argument c is to be treated as a white space in ICU
     * rules. Usually ICU rule white spaces are ignored unless quoted.
     * @param c codepoint to check
     * @return true if c is a ICU white space
     */
    public static boolean isRuleWhiteSpace(int c)
    {
        /* "white space" in the sense of ICU rule parsers
           This is a FIXED LIST that is NOT DEPENDENT ON UNICODE PROPERTIES.
           See UTR #31: http://www.unicode.org/reports/tr31/.
           U+0009..U+000D, U+0020, U+0085, U+200E..U+200F, and U+2028..U+2029
        */
        return (c >= 0x0009 && c <= 0x2029 &&
                (c <= 0x000D || c == 0x0020 || c == 0x0085 ||
                 c == 0x200E || c == 0x200F || c >= 0x2028));
    }

    // protected variables -----------------------------------------------

    /**
    * Case table
    */
    char m_case_[];

    /**
    * Exception property table
    */
    int m_exception_[];
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
    * This, from what i infer is the max size of the indicators used for the
    * exception values.
    * Number of bits in an 8-bit integer value
    */
    private static final int EXC_GROUP_ = 8;

    /**
    * Mask to get the group
    */
    private static final int EXC_GROUP_MASK_ = 255;

    /**
    * Mask to get the digit value in the exception result
    */
    private static final int EXC_DIGIT_MASK_ = 0xFFFF;

    /**
    * Offset table for data in exception block.<br>
    * Table formed by the number of bits used for the index, e.g. 0 = 0 bits,
    * 1 = 1 bits.
    */
    private static final byte FLAGS_OFFSET_[] =
    {
        0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4,
        1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
        1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
        1, 2, 2, 3, 2, 3, 3, 4, 2, 3, 3, 4, 3, 4, 4, 5,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
        2, 3, 3, 4, 3, 4, 4, 5, 3, 4, 4, 5, 4, 5, 5, 6,
        3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
        3, 4, 4, 5, 4, 5, 5, 6, 4, 5, 5, 6, 5, 6, 6, 7,
        4, 5, 5, 6, 5, 6, 6, 7, 5, 6, 6, 7, 6, 7, 7, 8
    };

    /**
    * Numeric value shift
    */
    private static final int VALUE_SHIFT_ = 20;

    /**
    * Mask to be applied after shifting to obtain an unsigned numeric value
    */
    private static final int UNSIGNED_VALUE_MASK_AFTER_SHIFT_ = 0x7FF;

    /**
     *
     */
    private static final int NUMERIC_TYPE_SHIFT = 12;

    /**
    * Folding indicator mask
    */
    private static final int SUPPLEMENTARY_FOLD_INDICATOR_MASK_ = 0x8000;

    /**
    * Folding offset mask
    */
    private static final int SUPPLEMENTARY_FOLD_OFFSET_MASK_ = 0x7FFF;

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

    /**
    * To get the last character out from a data type
    */
    private static final int LAST_CHAR_MASK_ = 0xFFFF;

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
    * @exception thrown when data reading fails or data corrupted
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

    /* Is followed by {case-ignorable}* cased  ? */
    /**
    * Getting the correct address for data in the exception value
    * @param evalue exception value
    * @param indicator type of data to retrieve
    * @param address current address to move from
    * @return the correct address
    */
    private int addExceptionOffset(int evalue, int indicator, int address)
    {
        int result = address;
        if (indicator >= EXC_GROUP_) {
        result += FLAGS_OFFSET_[evalue & EXC_GROUP_MASK_];
        evalue >>= EXC_GROUP_;
        indicator -= EXC_GROUP_;
        }
        int mask = (1 << indicator) - 1;
        result += FLAGS_OFFSET_[evalue & mask];
        return result;
    }

    private static final int TAB     = 0x0009;
    private static final int LF      = 0x000a;
    private static final int FF      = 0x000c;
    private static final int CR      = 0x000d;
    private static final int U_A     = 0x0041;
    private static final int U_Z     = 0x005a;
    private static final int U_a     = 0x0061;
    private static final int U_z     = 0x007a;
    private static final int DEL     = 0x007f;
    private static final int NL      = 0x0085;
    private static final int NBSP    = 0x00a0;
    private static final int CGJ     = 0x034f;
    private static final int FIGURESP= 0x2007;
    private static final int HAIRSP  = 0x200a;
    private static final int ZWNJ    = 0x200c;
    private static final int ZWJ     = 0x200d;
    private static final int RLM     = 0x200f;
    private static final int NNBSP   = 0x202f;
    private static final int WJ      = 0x2060;
    private static final int INHSWAP = 0x206a;
    private static final int NOMDIG  = 0x206f;
    private static final int ZWNBSP  = 0xfeff;

    public UnicodeSet addPropertyStarts(UnicodeSet set) {
        int c;

        /* add the start code point of each same-value range of each trie */
        //utrie_enum(&normTrie, NULL, _enumPropertyStartsRange, set);
        TrieIterator propsIter = new TrieIterator(m_trie_);
        RangeValueIterator.Element propsResult = new RangeValueIterator.Element();
          while(propsIter.next(propsResult)){
            set.add(propsResult.start);
        }
        //utrie_enum(&propsVectorsTrie, NULL, _enumPropertyStartsRange, set);
        TrieIterator propsVectorsIter = new TrieIterator(m_additionalTrie_);
        RangeValueIterator.Element propsVectorsResult = new RangeValueIterator.Element();
        while(propsVectorsIter.next(propsVectorsResult)){
            set.add(propsVectorsResult.start);
        }


        /* add code points with hardcoded properties, plus the ones following them */

        /* add for IS_THAT_CONTROL_SPACE() */
        set.add(TAB); /* range TAB..CR */
        set.add(CR+1);
        set.add(0x1c);
        set.add(0x1f+1);
        set.add(NL);
        set.add(NL+1);

        /* add for u_isIDIgnorable() what was not added above */
        set.add(DEL); /* range DEL..NBSP-1, NBSP added below */
        set.add(HAIRSP);
        set.add(RLM+1);
        set.add(INHSWAP);
        set.add(NOMDIG+1);
        set.add(ZWNBSP);
        set.add(ZWNBSP+1);

        /* add no-break spaces for u_isWhitespace() what was not added above */
        set.add(NBSP);
        set.add(NBSP+1);
        set.add(FIGURESP);
        set.add(FIGURESP+1);
        set.add(NNBSP);
        set.add(NNBSP+1);

        /* add for u_charDigitValue() */
        set.add(0x3007);
        set.add(0x3008);
        set.add(0x4e00);
        set.add(0x4e01);
        set.add(0x4e8c);
        set.add(0x4e8d);
        set.add(0x4e09);
        set.add(0x4e0a);
        set.add(0x56db);
        set.add(0x56dc);
        set.add(0x4e94);
        set.add(0x4e95);
        set.add(0x516d);
        set.add(0x516e);
        set.add(0x4e03);
        set.add(0x4e04);
        set.add(0x516b);
        set.add(0x516c);
        set.add(0x4e5d);
        set.add(0x4e5e);

        /* add for u_digit() */
        set.add(U_a);
        set.add(U_z+1);
        set.add(U_A);
        set.add(U_Z+1);

        /* add for UCHAR_DEFAULT_IGNORABLE_CODE_POINT what was not added above */
        set.add(WJ); /* range WJ..NOMDIG */
        set.add(0xfff0);
        set.add(0xfffb+1);
        set.add(0xe0000);
        set.add(0xe0fff+1);

        /* add for UCHAR_GRAPHEME_BASE and others */
        set.add(CGJ);
        set.add(CGJ+1);

        /* add for UCHAR_JOINING_TYPE */
        set.add(ZWNJ); /* range ZWNJ..ZWJ */
        set.add(ZWJ+1);

        /* add Jamo type boundaries for UCHAR_HANGUL_SYLLABLE_TYPE */
        set.add(0x1100);
        int value= UCharacter.HangulSyllableType.LEADING_JAMO;
        int value2;
        for(c=0x115a; c<=0x115f; ++c) {
            value2= UCharacter.getIntPropertyValue(c, UProperty.HANGUL_SYLLABLE_TYPE);
            if(value!=value2) {
                value=value2;
                set.add(c);
            }
        }

        set.add(0x1160);
        value=UCharacter.HangulSyllableType.VOWEL_JAMO;
        for(c=0x11a3; c<=0x11a7; ++c) {
            value2=UCharacter.getIntPropertyValue(c, UProperty.HANGUL_SYLLABLE_TYPE);
            if(value!=value2) {
                value=value2;
                set.add(c);
            }
        }

        set.add(0x11a8);
        value=UCharacter.HangulSyllableType.TRAILING_JAMO;
        for(c=0x11fa; c<=0x11ff; ++c) {
            value2=UCharacter.getIntPropertyValue(c, UProperty.HANGUL_SYLLABLE_TYPE);
            if(value!=value2) {
                value=value2;
                set.add(c);
            }
        }


        /*
         * Omit code points for u_charCellWidth() because
         * - it is deprecated and not a real Unicode property
         * - they are probably already set from the trie enumeration
         */

        /*
         * Omit code points with hardcoded specialcasing properties
         * because we do not build property UnicodeSets for them right now.
         */
        return set; // for chaining
    }
/*----------------------------------------------------------------
 * Inclusions list
 *----------------------------------------------------------------*/

    /*
     * Return a set of characters for property enumeration.
     * The set implicitly contains 0x110000 as well, which is one more than the highest
     * Unicode code point.
     *
     * This set is used as an ordered list - its code points are ordered, and
     * consecutive code points (in Unicode code point order) in the set define a range.
     * For each two consecutive characters (start, limit) in the set,
     * all of the UCD/normalization and related properties for
     * all code points start..limit-1 are all the same,
     * except for character names and ISO comments.
     *
     * All Unicode code points U+0000..U+10ffff are covered by these ranges.
     * The ranges define a partition of the Unicode code space.
     * ICU uses the inclusions set to enumerate properties for generating
     * UnicodeSets containing all code points that have a certain property value.
     *
     * The Inclusion List is generated from the UCD. It is generated
     * by enumerating the data tries, and code points for hardcoded properties
     * are added as well.
     *
     * --------------------------------------------------------------------------
     *
     * The following are ideas for getting properties-unique code point ranges,
     * with possible optimizations beyond the current implementation.
     * These optimizations would require more code and be more fragile.
     * The current implementation generates one single list (set) for all properties.
     *
     * To enumerate properties efficiently, one needs to know ranges of
     * repetitive values, so that the value of only each start code point
     * can be applied to the whole range.
     * This information is in principle available in the uprops.icu/unorm.icu data.
     *
     * There are two obstacles:
     *
     * 1. Some properties are computed from multiple data structures,
     *    making it necessary to get repetitive ranges by intersecting
     *    ranges from multiple tries.
     *
     * 2. It is not economical to write code for getting repetitive ranges
     *    that are precise for each of some 50 properties.
     *
     * Compromise ideas:
     *
     * - Get ranges per trie, not per individual property.
     *   Each range contains the same values for a whole group of properties.
     *   This would generate currently five range sets, two for uprops.icu tries
     *   and three for unorm.icu tries.
     *
     * - Combine sets of ranges for multiple tries to get sufficient sets
     *   for properties, e.g., the uprops.icu main and auxiliary tries
     *   for all non-normalization properties.
     *
     * Ideas for representing ranges and combining them:
     *
     * - A UnicodeSet could hold just the start code points of ranges.
     *   Multiple sets are easily combined by or-ing them together.
     *
     * - Alternatively, a UnicodeSet could hold each even-numbered range.
     *   All ranges could be enumerated by using each start code point
     *   (for the even-numbered ranges) as well as each limit (end+1) code point
     *   (for the odd-numbered ranges).
     *   It should be possible to combine two such sets by xor-ing them,
     *   but no more than two.
     *
     * The second way to represent ranges may(?!) yield smaller UnicodeSet arrays,
     * but the first one is certainly simpler and applicable for combining more than
     * two range sets.
     *
     * It is possible to combine all range sets for all uprops/unorm tries into one
     * set that can be used for all properties.
     * As an optimization, there could be less-combined range sets for certain
     * groups of properties.
     * The relationship of which less-combined range set to use for which property
     * depends on the implementation of the properties and must be hardcoded
     * - somewhat error-prone and higher maintenance but can be tested easily
     * by building property sets "the simple way" in test code.
     *
     * ---
     *
     * Do not use a UnicodeSet pattern because that causes infinite recursion;
     * UnicodeSet depends on the inclusions set.
     */
    public UnicodeSet getInclusions() {
        UnicodeSet set = new UnicodeSet();
        NormalizerImpl.addPropertyStarts(set);
        addPropertyStarts(set);
        return set;
    }

}
