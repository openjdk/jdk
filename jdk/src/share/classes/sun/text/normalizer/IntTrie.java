/*
 * Portions Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.io.InputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Trie implementation which stores data in int, 32 bits.
 * @author synwee
 * @see com.ibm.icu.impl.Trie
 * @since release 2.1, Jan 01 2002
 */
public class IntTrie extends Trie
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
    public IntTrie(InputStream inputStream, DataManipulate datamanipulate)
                                                    throws IOException
    {
        super(inputStream, datamanipulate);
        if (!isIntTrie()) {
            throw new IllegalArgumentException(
                               "Data given does not belong to a int trie.");
        }
    }

    // public methods --------------------------------------------------

    /**
    * Gets the value associated with the codepoint.
    * If no value is associated with the codepoint, a default value will be
    * returned.
    * @param ch codepoint
    * @return offset to data
    * @draft 2.1
    */
    public final int getCodePointValue(int ch)
    {
        int offset = getCodePointOffset(ch);
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
    public final int getLeadValue(char ch)
    {
        return m_data_[getLeadOffset(ch)];
    }

    /**
    * Get a value from a folding offset (from the value of a lead surrogate)
    * and a trail surrogate.
    * @param leadvalue the value of a lead surrogate that contains the
    *        folding offset
    * @param trail surrogate
    * @return trie data value associated with the trail character
    * @draft 2.1
    */
    public final int getTrailValue(int leadvalue, char trail)
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
        super.unserialize(inputStream);
        // one used for initial value
        m_data_               = new int[m_dataLength_];
        DataInputStream input = new DataInputStream(inputStream);
        for (int i = 0; i < m_dataLength_; i ++) {
            m_data_[i] = input.readInt();
        }
        m_initialValue_ = m_data_[0];
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
    * For use internally in TrieIterator
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

    // package private methods -----------------------------------------

    /**
     * Internal constructor for builder use
     * @param index the index array to be slotted into this trie
     * @param data the data array to be slotted into this trie
     * @param initialvalue the initial value for this trie
     * @param options trie options to use
     * @param datamanipulate folding implementation
     */
    IntTrie(char index[], int data[], int initialvalue, int options,
            DataManipulate datamanipulate)
    {
        super(index, options, datamanipulate);
        m_data_ = data;
        m_dataLength_ = m_data_.length;
        m_initialValue_ = initialvalue;
    }

    // private data members --------------------------------------------

    /**
    * Default value
    */
    private int m_initialValue_;
    /**
    * Array of char data
    */
    private int m_data_[];
}
