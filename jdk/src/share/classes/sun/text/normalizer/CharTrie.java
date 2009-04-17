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

import java.io.InputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * Trie implementation which stores data in char, 16 bits.
 * @author synwee
 * @see com.ibm.icu.impl.Trie
 * @since release 2.1, Jan 01 2002
 */

 // note that i need to handle the block calculations later, since chartrie
 // in icu4c uses the same index array.
public class CharTrie extends Trie
{
    // public constructors ---------------------------------------------

    /**
    * <p>Creates a new Trie with the settings for the trie data.</p>
    * <p>Unserialize the 32-bit-aligned input stream and use the data for the
    * trie.</p>
    * @param inputStream file input stream to a ICU data file, containing
    *                    the trie
    * @param dataManipulate object which provides methods to parse the char
    *                        data
    * @throws IOException thrown when data reading fails
    * @draft 2.1
    */
    public CharTrie(InputStream inputStream,
                    DataManipulate dataManipulate) throws IOException
    {
        super(inputStream, dataManipulate);

        if (!isCharTrie()) {
            throw new IllegalArgumentException(
                               "Data given does not belong to a char trie.");
        }
        m_friendAgent_ = new FriendAgent();
    }

    /**
     * Make a dummy CharTrie.
     * A dummy trie is an empty runtime trie, used when a real data trie cannot
     * be loaded.
     *
     * The trie always returns the initialValue,
     * or the leadUnitValue for lead surrogate code points.
     * The Latin-1 part is always set up to be linear.
     *
     * @param initialValue the initial value that is set for all code points
     * @param leadUnitValue the value for lead surrogate code _units_ that do not
     *                      have associated supplementary data
     * @param dataManipulate object which provides methods to parse the char data
     */
    public CharTrie(int initialValue, int leadUnitValue, DataManipulate dataManipulate) {
        super(new char[BMP_INDEX_LENGTH+SURROGATE_BLOCK_COUNT], HEADER_OPTIONS_LATIN1_IS_LINEAR_MASK_, dataManipulate);

        int dataLength, latin1Length, i, limit;
        char block;

        /* calculate the actual size of the dummy trie data */

        /* max(Latin-1, block 0) */
        dataLength=latin1Length= INDEX_STAGE_1_SHIFT_<=8 ? 256 : DATA_BLOCK_LENGTH;
        if(leadUnitValue!=initialValue) {
            dataLength+=DATA_BLOCK_LENGTH;
        }
        m_data_=new char[dataLength];
        m_dataLength_=dataLength;

        m_initialValue_=(char)initialValue;

        /* fill the index and data arrays */

        /* indexes are preset to 0 (block 0) */

        /* Latin-1 data */
        for(i=0; i<latin1Length; ++i) {
            m_data_[i]=(char)initialValue;
        }

        if(leadUnitValue!=initialValue) {
            /* indexes for lead surrogate code units to the block after Latin-1 */
            block=(char)(latin1Length>>INDEX_STAGE_2_SHIFT_);
            i=0xd800>>INDEX_STAGE_1_SHIFT_;
            limit=0xdc00>>INDEX_STAGE_1_SHIFT_;
            for(; i<limit; ++i) {
                m_index_[i]=block;
            }

            /* data for lead surrogate code units */
            limit=latin1Length+DATA_BLOCK_LENGTH;
            for(i=latin1Length; i<limit; ++i) {
                m_data_[i]=(char)leadUnitValue;
            }
        }

        m_friendAgent_ = new FriendAgent();
    }

    /**
     * Java friend implementation
     */
    public class FriendAgent
    {
        /**
         * Gives out the index array of the trie
         * @return index array of trie
         */
        public char[] getPrivateIndex()
        {
            return m_index_;
        }
        /**
         * Gives out the data array of the trie
         * @return data array of trie
         */
        public char[] getPrivateData()
        {
            return m_data_;
        }
        /**
         * Gives out the data offset in the trie
         * @return data offset in the trie
         */
        public int getPrivateInitialValue()
        {
            return m_initialValue_;
        }
    }

    // public methods --------------------------------------------------

    /**
     * Java friend implementation
     * To store the index and data array into the argument.
     * @param friend java friend UCharacterProperty object to store the array
     */
    public void putIndexData(UCharacterProperty friend)
    {
        friend.setIndexData(m_friendAgent_);
    }

    /**
    * Gets the value associated with the codepoint.
    * If no value is associated with the codepoint, a default value will be
    * returned.
    * @param ch codepoint
    * @return offset to data
    * @draft 2.1
    */
    public final char getCodePointValue(int ch)
    {
        int offset;

        // fastpath for U+0000..U+D7FF
        if(0 <= ch && ch < UTF16.LEAD_SURROGATE_MIN_VALUE) {
            // copy of getRawOffset()
            offset = (m_index_[ch >> INDEX_STAGE_1_SHIFT_] << INDEX_STAGE_2_SHIFT_)
                    + (ch & INDEX_STAGE_3_MASK_);
            return m_data_[offset];
        }

        // handle U+D800..U+10FFFF
        offset = getCodePointOffset(ch);

        // return -1 if there is an error, in this case we return the default
        // value: m_initialValue_
        return (offset >= 0) ? m_data_[offset] : m_initialValue_;
    }

    /**
    * Gets the value to the data which this lead surrogate character points
    * to.
    * Returned data may contain folding offset information for the next
    * trailing surrogate character.
    * This method does not guarantee correct results for trail surrogates.
    * @param ch lead surrogate character
    * @return data value
    * @draft 2.1
    */
    public final char getLeadValue(char ch)
    {
       return m_data_[getLeadOffset(ch)];
    }

    /**
    * Get the value associated with a pair of surrogates.
    * @param lead a lead surrogate
    * @param trail a trail surrogate
    * @draft 2.1
    */
    public final char getSurrogateValue(char lead, char trail)
    {
        int offset = getSurrogateOffset(lead, trail);
        if (offset > 0) {
            return m_data_[offset];
        }
        return m_initialValue_;
    }

    /**
    * <p>Get a value from a folding offset (from the value of a lead surrogate)
    * and a trail surrogate.</p>
    * <p>If the
    * @param leadvalue value associated with the lead surrogate which contains
    *        the folding offset
    * @param trail surrogate
    * @return trie data value associated with the trail character
    * @draft 2.1
    */
    public final char getTrailValue(int leadvalue, char trail)
    {
        if (m_dataManipulate_ == null) {
            throw new NullPointerException(
                             "The field DataManipulate in this Trie is null");
        }
        int offset = m_dataManipulate_.getFoldingOffset(leadvalue);
        if (offset > 0) {
            return m_data_[getRawOffset(offset,
                                        (char)(trail & SURROGATE_MASK_))];
        }
        return m_initialValue_;
    }

    // protected methods -----------------------------------------------

    /**
    * <p>Parses the input stream and stores its trie content into a index and
    * data array</p>
    * @param inputStream data input stream containing trie data
    * @exception IOException thrown when data reading fails
    */
    protected final void unserialize(InputStream inputStream)
                                                throws IOException
    {
        DataInputStream input = new DataInputStream(inputStream);
        int indexDataLength = m_dataOffset_ + m_dataLength_;
        m_index_ = new char[indexDataLength];
        for (int i = 0; i < indexDataLength; i ++) {
            m_index_[i] = input.readChar();
        }
        m_data_           = m_index_;
        m_initialValue_   = m_data_[m_dataOffset_];
    }

    /**
    * Gets the offset to the data which the surrogate pair points to.
    * @param lead lead surrogate
    * @param trail trailing surrogate
    * @return offset to data
    * @draft 2.1
    */
    protected final int getSurrogateOffset(char lead, char trail)
    {
        if (m_dataManipulate_ == null) {
            throw new NullPointerException(
                             "The field DataManipulate in this Trie is null");
        }

        // get fold position for the next trail surrogate
        int offset = m_dataManipulate_.getFoldingOffset(getLeadValue(lead));

        // get the real data from the folded lead/trail units
        if (offset > 0) {
            return getRawOffset(offset, (char)(trail & SURROGATE_MASK_));
        }

        // return -1 if there is an error, in this case we return the default
        // value: m_initialValue_
        return -1;
    }

    /**
    * Gets the value at the argument index.
    * For use internally in TrieIterator.
    * @param index value at index will be retrieved
    * @return 32 bit value
    * @see com.ibm.icu.impl.TrieIterator
    * @draft 2.1
    */
    protected final int getValue(int index)
    {
        return m_data_[index];
    }

    /**
    * Gets the default initial value
    * @return 32 bit value
    * @draft 2.1
    */
    protected final int getInitialValue()
    {
        return m_initialValue_;
    }

    // private data members --------------------------------------------

    /**
    * Default value
    */
    private char m_initialValue_;
    /**
    * Array of char data
    */
    private char m_data_[];
    /**
     * Agent for friends
     */
    private FriendAgent m_friendAgent_;
}
