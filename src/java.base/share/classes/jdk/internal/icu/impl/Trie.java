// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 ******************************************************************************
 * Copyright (C) 1996-2015, International Business Machines Corporation and
 * others. All Rights Reserved.
 ******************************************************************************
 */

package jdk.internal.icu.impl;

import java.nio.ByteBuffer;
import java.util.Arrays;

import jdk.internal.icu.lang.UCharacter;
import jdk.internal.icu.text.UTF16;

/**
 * <p>A trie is a kind of compressed, serializable table of values
 * associated with Unicode code points (0..0x10ffff).</p>
 * <p>This class defines the basic structure of a trie and provides methods
 * to <b>retrieve the offsets to the actual data</b>.</p>
 * <p>Data will be the form of an array of basic types, char or int.</p>
 * <p>The actual data format will have to be specified by the user in the
 * inner static interface jdk.internal.icu.impl.Trie.DataManipulate.</p>
 * <p>This trie implementation is optimized for getting offset while walking
 * forward through a UTF-16 string.
 * Therefore, the simplest and fastest access macros are the
 * fromLead() and fromOffsetTrail() methods.
 * The fromBMP() method are a little more complicated; they get offsets even
 * for lead surrogate codepoints, while the fromLead() method get special
 * "folded" offsets for lead surrogate code units if there is relevant data
 * associated with them.
 * From such a folded offsets, an offset needs to be extracted to supply
 * to the fromOffsetTrail() methods.
 * To handle such supplementary codepoints, some offset information are kept
 * in the data.</p>
 * <p>Methods in jdk.internal.icu.impl.Trie.DataManipulate are called to retrieve
 * that offset from the folded value for the lead surrogate unit.</p>
 * <p>For examples of use, see jdk.internal.icu.impl.CharTrie or
 * jdk.internal.icu.impl.IntTrie.</p>
 * @author synwee
 * @see jdk.internal.icu.impl.CharTrie
 * @see jdk.internal.icu.impl.IntTrie
 * @since release 2.1, Jan 01 2002
 */
public abstract class Trie
{
    // public class declaration ----------------------------------------

    /**
    * Character data in com.ibm.impl.Trie have different user-specified format
    * for different purposes.
    * This interface specifies methods to be implemented in order for
    * com.ibm.impl.Trie, to surrogate offset information encapsulated within
    * the data.
    */
    public static interface DataManipulate
    {
        /**
        * Called by jdk.internal.icu.impl.Trie to extract from a lead surrogate's
        * data
        * the index array offset of the indexes for that lead surrogate.
        * @param value data value for a surrogate from the trie, including the
        *        folding offset
        * @return data offset or 0 if there is no data for the lead surrogate
        */
        public int getFoldingOffset(int value);
    }

    // default implementation
    private static class DefaultGetFoldingOffset implements DataManipulate {
        @Override
        public int getFoldingOffset(int value) {
            return value;
        }
    }

    // public methods --------------------------------------------------

    /**
     * Determines if this trie has a linear latin 1 array
     * @return true if this trie has a linear latin 1 array, false otherwise
     */
    public final boolean isLatin1Linear()
    {
        return m_isLatin1Linear_;
    }

    /**
     * Checks if the argument Trie has the same data as this Trie.
     * Attributes are checked but not the index data.
     * @param other Trie to check
     * @return true if the argument Trie has the same data as this Trie, false
     *         otherwise
     */
    ///CLOVER:OFF
    @Override
    public boolean equals(Object other)
    {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Trie)) {
            return false;
        }
        Trie othertrie = (Trie)other;
        return m_isLatin1Linear_ == othertrie.m_isLatin1Linear_
               && m_options_ == othertrie.m_options_
               && m_dataLength_ == othertrie.m_dataLength_
               && Arrays.equals(m_index_, othertrie.m_index_);
    }

    @Override
    public int hashCode() {
        assert false : "hashCode not designed";
        return 42;
    }
    ///CLOVER:ON

    /**
     * Gets the serialized data file size of the Trie. This is used during
     * trie data reading for size checking purposes.
     * @return size size of serialized trie data file in terms of the number
     *              of bytes
     */
    public int getSerializedDataSize()
    {
        // includes signature, option, dataoffset and datalength output
        int result = (4 << 2);
        result += (m_dataOffset_ << 1);
        if (isCharTrie()) {
            result += (m_dataLength_ << 1);
        }
        else if (isIntTrie()) {
            result += (m_dataLength_ << 2);
        }
        return result;
    }

    // protected constructor -------------------------------------------

    /**
     * Trie constructor for CharTrie use.
     * @param bytes data of an ICU data file, containing the trie
     * @param dataManipulate object containing the information to parse the
     *                       trie data
     */
    protected Trie(ByteBuffer bytes, DataManipulate  dataManipulate)
    {
        // Magic number to authenticate the data.
        int signature = bytes.getInt();
        m_options_    = bytes.getInt();

        if (!checkHeader(signature)) {
            throw new IllegalArgumentException("ICU data file error: Trie header authentication failed, please check if you have the most updated ICU data file");
        }

        if(dataManipulate != null) {
            m_dataManipulate_ = dataManipulate;
        } else {
            m_dataManipulate_ = new DefaultGetFoldingOffset();
        }
        m_isLatin1Linear_ = (m_options_ &
                             HEADER_OPTIONS_LATIN1_IS_LINEAR_MASK_) != 0;
        m_dataOffset_     = bytes.getInt();
        m_dataLength_     = bytes.getInt();
        unserialize(bytes);
    }

    /**
    * Trie constructor
    * @param index array to be used for index
    * @param options used by the trie
    * @param dataManipulate object containing the information to parse the
    *                       trie data
    */
    protected Trie(char index[], int options, DataManipulate dataManipulate)
    {
        m_options_ = options;
        if(dataManipulate != null) {
            m_dataManipulate_ = dataManipulate;
        } else {
            m_dataManipulate_ = new DefaultGetFoldingOffset();
        }
        m_isLatin1Linear_ = (m_options_ &
                             HEADER_OPTIONS_LATIN1_IS_LINEAR_MASK_) != 0;
        m_index_ = index;
        m_dataOffset_ = m_index_.length;
    }


    // protected data members ------------------------------------------

    /**
    * Lead surrogate code points' index displacement in the index array.
    * 0x10000-0xd800=0x2800
    * 0x2800 >> INDEX_STAGE_1_SHIFT_
    */
    protected static final int LEAD_INDEX_OFFSET_ = 0x2800 >> 5;
    /**
    * Shift size for shifting right the input index. 1..9
    */
    protected static final int INDEX_STAGE_1_SHIFT_ = 5;
    /**
    * Shift size for shifting left the index array values.
    * Increases possible data size with 16-bit index values at the cost
    * of compactability.
    * This requires blocks of stage 2 data to be aligned by
    * DATA_GRANULARITY.
    * 0..INDEX_STAGE_1_SHIFT
    */
    protected static final int INDEX_STAGE_2_SHIFT_ = 2;
    /**
     * Number of data values in a stage 2 (data array) block.
     */
    protected static final int DATA_BLOCK_LENGTH=1<<INDEX_STAGE_1_SHIFT_;
    /**
    * Mask for getting the lower bits from the input index.
    * DATA_BLOCK_LENGTH - 1.
    */
    protected static final int INDEX_STAGE_3_MASK_ = DATA_BLOCK_LENGTH - 1;
    /** Number of bits of a trail surrogate that are used in index table lookups. */
    protected static final int SURROGATE_BLOCK_BITS=10-INDEX_STAGE_1_SHIFT_;
    /**
     * Number of index (stage 1) entries per lead surrogate.
     * Same as number of index entries for 1024 trail surrogates,
     * ==0x400>>INDEX_STAGE_1_SHIFT_
     */
    protected static final int SURROGATE_BLOCK_COUNT=(1<<SURROGATE_BLOCK_BITS);
    /** Length of the BMP portion of the index (stage 1) array. */
    protected static final int BMP_INDEX_LENGTH=0x10000>>INDEX_STAGE_1_SHIFT_;
    /**
    * Surrogate mask to use when shifting offset to retrieve supplementary
    * values
    */
    protected static final int SURROGATE_MASK_ = 0x3FF;
    /**
    * Index or UTF16 characters
    */
    protected char m_index_[];
    /**
    * Internal TrieValue which handles the parsing of the data value.
    * This class is to be implemented by the user
    */
    protected DataManipulate m_dataManipulate_;
    /**
    * Start index of the data portion of the trie. CharTrie combines
    * index and data into a char array, so this is used to indicate the
    * initial offset to the data portion.
    * Note this index always points to the initial value.
    */
    protected int m_dataOffset_;
    /**
    * Length of the data array
    */
    protected int m_dataLength_;

    // protected methods -----------------------------------------------

    /**
    * Gets the offset to the data which the surrogate pair points to.
    * @param lead lead surrogate
    * @param trail trailing surrogate
    * @return offset to data
    */
    protected abstract int getSurrogateOffset(char lead, char trail);

    /**
    * Gets the value at the argument index
    * @param index value at index will be retrieved
    * @return 32 bit value
    */
    protected abstract int getValue(int index);

    /**
    * Gets the default initial value
    * @return 32 bit value
    */
    protected abstract int getInitialValue();

    /**
    * Gets the offset to the data which the index ch after variable offset
    * points to.
    * Note for locating a non-supplementary character data offset, calling
    * <p>
    * getRawOffset(0, ch);
    * </p>
    * will do. Otherwise if it is a supplementary character formed by
    * surrogates lead and trail. Then we would have to call getRawOffset()
    * with getFoldingIndexOffset(). See getSurrogateOffset().
    * @param offset index offset which ch is to start from
    * @param ch index to be used after offset
    * @return offset to the data
    */
    protected final int getRawOffset(int offset, char ch)
    {
        return (m_index_[offset + (ch >> INDEX_STAGE_1_SHIFT_)]
                << INDEX_STAGE_2_SHIFT_)
                + (ch & INDEX_STAGE_3_MASK_);
    }

    /**
    * Gets the offset to data which the BMP character points to
    * Treats a lead surrogate as a normal code point.
    * @param ch BMP character
    * @return offset to data
    */
    protected final int getBMPOffset(char ch)
    {
        return (ch >= UTF16.LEAD_SURROGATE_MIN_VALUE
                && ch <= UTF16.LEAD_SURROGATE_MAX_VALUE)
                ? getRawOffset(LEAD_INDEX_OFFSET_, ch)
                : getRawOffset(0, ch);
                // using a getRawOffset(ch) makes no diff
    }

    /**
    * Gets the offset to the data which this lead surrogate character points
    * to.
    * Data at the returned offset may contain folding offset information for
    * the next trailing surrogate character.
    * @param ch lead surrogate character
    * @return offset to data
    */
    protected final int getLeadOffset(char ch)
    {
       return getRawOffset(0, ch);
    }

    /**
    * Internal trie getter from a code point.
    * Could be faster(?) but longer with
    *   if((c32)<=0xd7ff) { (result)=_TRIE_GET_RAW(trie, data, 0, c32); }
    * Gets the offset to data which the codepoint points to
    * @param ch codepoint
    * @return offset to data
    */
    protected final int getCodePointOffset(int ch)
    {
        // if ((ch >> 16) == 0) slower
        if (ch < 0) {
            return -1;
        } else if (ch < UTF16.LEAD_SURROGATE_MIN_VALUE) {
            // fastpath for the part of the BMP below surrogates (D800) where getRawOffset() works
            return getRawOffset(0, (char)ch);
        } else if (ch < UTF16.SUPPLEMENTARY_MIN_VALUE) {
            // BMP codepoint
            return getBMPOffset((char)ch);
        } else if (ch <= UCharacter.MAX_VALUE) {
            // look at the construction of supplementary characters
            // trail forms the ends of it.
            return getSurrogateOffset(UTF16.getLeadSurrogate(ch),
                                      (char)(ch & SURROGATE_MASK_));
        } else {
            // return -1 if there is an error, in this case we return
            return -1;
        }
    }

    /**
     * <p>Parses the byte buffer and creates the trie index with it.</p>
     * <p>The position of the input ByteBuffer must be right after the trie header.</p>
     * <p>This is overwritten by the child classes.
     * @param bytes buffer containing trie data
     */
    protected void unserialize(ByteBuffer bytes)
    {
        m_index_ = ICUBinary.getChars(bytes, m_dataOffset_, 0);
    }

    /**
    * Determines if this is a 32 bit trie
    * @return true if options specifies this is a 32 bit trie
    */
    protected final boolean isIntTrie()
    {
        return (m_options_ & HEADER_OPTIONS_DATA_IS_32_BIT_) != 0;
    }

    /**
    * Determines if this is a 16 bit trie
    * @return true if this is a 16 bit trie
    */
    protected final boolean isCharTrie()
    {
        return (m_options_ & HEADER_OPTIONS_DATA_IS_32_BIT_) == 0;
    }

    // private data members --------------------------------------------

    //  struct UTrieHeader {
    //      int32_t   signature;
    //      int32_t   options  (a bit field)
    //      int32_t   indexLength
    //      int32_t   dataLength

    /**
    * Size of Trie header in bytes
    */
    protected static final int HEADER_LENGTH_ = 4 * 4;
    /**
    * Latin 1 option mask
    */
    protected static final int HEADER_OPTIONS_LATIN1_IS_LINEAR_MASK_ = 0x200;
    /**
    * Constant number to authenticate the byte block
    */
    protected static final int HEADER_SIGNATURE_ = 0x54726965;
    /**
    * Header option formatting
    */
    private static final int HEADER_OPTIONS_SHIFT_MASK_ = 0xF;
    protected static final int HEADER_OPTIONS_INDEX_SHIFT_ = 4;
    protected static final int HEADER_OPTIONS_DATA_IS_32_BIT_ = 0x100;

    /**
    * Flag indicator for Latin quick access data block
    */
    private boolean m_isLatin1Linear_;

    /**
    * <p>Trie options field.</p>
    * <p>options bit field:<br>
    * 9  1 = Latin-1 data is stored linearly at data + DATA_BLOCK_LENGTH<br>
    * 8  0 = 16-bit data, 1=32-bit data<br>
    * 7..4  INDEX_STAGE_1_SHIFT   // 0..INDEX_STAGE_2_SHIFT<br>
    * 3..0  INDEX_STAGE_2_SHIFT   // 1..9<br>
    */
    private int m_options_;

    // private methods ---------------------------------------------------

    /**
    * Authenticates raw data header.
    * Checking the header information, signature and options.
    * @param signature This contains the options and type of a Trie
    * @return true if the header is authenticated valid
    */
    private final boolean checkHeader(int signature)
    {
        // check the signature
        // Trie in big-endian US-ASCII (0x54726965).
        // Magic number to authenticate the data.
        if (signature != HEADER_SIGNATURE_) {
            return false;
        }

        if ((m_options_ & HEADER_OPTIONS_SHIFT_MASK_) !=
                                                    INDEX_STAGE_1_SHIFT_ ||
            ((m_options_ >> HEADER_OPTIONS_INDEX_SHIFT_) &
                                                HEADER_OPTIONS_SHIFT_MASK_)
                                                 != INDEX_STAGE_2_SHIFT_) {
            return false;
        }
        return true;
    }
}
