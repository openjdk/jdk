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

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
* <p>Internal reader class for ICU data file uprops.icu containing
* Unicode codepoint data.</p>
* <p>This class simply reads uprops.icu, authenticates that it is a valid
* ICU data file and split its contents up into blocks of data for use in
* <a href=UCharacterProperty.html>com.ibm.icu.impl.UCharacterProperty</a>.
* </p>
* <p>uprops.icu which is in big-endian format is jared together with this
* package.</p>
*
* Unicode character properties file format see
* (ICU4C)/source/tools/genprops/store.c
*
* @author Syn Wee Quek
* @since release 2.1, February 1st 2002
*/
final class UCharacterPropertyReader implements ICUBinary.Authenticate
{
    // public methods ----------------------------------------------------

    public boolean isDataVersionAcceptable(byte version[])
    {
        return version[0] == DATA_FORMAT_VERSION_[0]
               && version[2] == DATA_FORMAT_VERSION_[2]
               && version[3] == DATA_FORMAT_VERSION_[3];
    }

    // protected constructor ---------------------------------------------

    /**
    * <p>Protected constructor.</p>
    * @param inputStream ICU uprop.dat file input stream
    * @exception IOException throw if data file fails authentication
    */
    protected UCharacterPropertyReader(InputStream inputStream)
                                                        throws IOException
    {
        m_unicodeVersion_ = ICUBinary.readHeader(inputStream, DATA_FORMAT_ID_,
                                                 this);
        m_dataInputStream_ = new DataInputStream(inputStream);
    }

    // protected methods -------------------------------------------------

    /**
    * <p>Reads uprops.icu, parse it into blocks of data to be stored in
    * UCharacterProperty.</P
    * @param ucharppty UCharacterProperty instance
    * @exception IOException thrown when data reading fails
    */
    protected void read(UCharacterProperty ucharppty) throws IOException
    {
        // read the indexes
        int count = INDEX_SIZE_;
        m_propertyOffset_          = m_dataInputStream_.readInt();
        count --;
        m_exceptionOffset_         = m_dataInputStream_.readInt();
        count --;
        m_caseOffset_              = m_dataInputStream_.readInt();
        count --;
        m_additionalOffset_        = m_dataInputStream_.readInt();
        count --;
        m_additionalVectorsOffset_ = m_dataInputStream_.readInt();
        count --;
        m_additionalColumnsCount_  = m_dataInputStream_.readInt();
        count --;
        m_reservedOffset_          = m_dataInputStream_.readInt();
        count --;
        m_dataInputStream_.skipBytes(3 << 2);
        count -= 3;
        ucharppty.m_maxBlockScriptValue_ = m_dataInputStream_.readInt();
        count --; // 10
        ucharppty.m_maxJTGValue_ = m_dataInputStream_.readInt();
        count --; // 11
        m_dataInputStream_.skipBytes(count << 2);

        // read the trie index block
        // m_props_index_ in terms of ints
        ucharppty.m_trie_ = new CharTrie(m_dataInputStream_, null);

        // skip the 32 bit properties block
        int size = m_exceptionOffset_ - m_propertyOffset_;
        m_dataInputStream_.skipBytes(size * 4);

        // reads the 32 bit exceptions block
        size = m_caseOffset_ - m_exceptionOffset_;
        m_dataInputStream_.skipBytes(size * 4);

        // reads the 32 bit case block
        size = (m_additionalOffset_ - m_caseOffset_) << 1;
        m_dataInputStream_.skipBytes(size * 2);

        if(m_additionalColumnsCount_ > 0) {
            // reads the additional property block
            ucharppty.m_additionalTrie_ = new CharTrie(m_dataInputStream_, null);

            // additional properties
            size = m_reservedOffset_ - m_additionalVectorsOffset_;
            ucharppty.m_additionalVectors_ = new int[size];
            for (int i = 0; i < size; i ++) {
                ucharppty.m_additionalVectors_[i] = m_dataInputStream_.readInt();
            }
        }

        m_dataInputStream_.close();
        ucharppty.m_additionalColumnsCount_ = m_additionalColumnsCount_;
        ucharppty.m_unicodeVersion_ = VersionInfo.getInstance(
                         (int)m_unicodeVersion_[0], (int)m_unicodeVersion_[1],
                         (int)m_unicodeVersion_[2], (int)m_unicodeVersion_[3]);
    }

    // private variables -------------------------------------------------

    /**
    * Index size
    */
    private static final int INDEX_SIZE_ = 16;

    /**
    * ICU data file input stream
    */
    private DataInputStream m_dataInputStream_;

    /**
    * Offset information in the indexes.
    */
    private int m_propertyOffset_;
    private int m_exceptionOffset_;
    private int m_caseOffset_;
    private int m_additionalOffset_;
    private int m_additionalVectorsOffset_;
    private int m_additionalColumnsCount_;
    private int m_reservedOffset_;
    private byte m_unicodeVersion_[];

    /**
    * Data format "UPro".
    */
    private static final byte DATA_FORMAT_ID_[] = {(byte)0x55, (byte)0x50,
                                                    (byte)0x72, (byte)0x6F};
    /**
     * Format version; this code works with all versions with the same major
     * version number and the same Trie bit distribution.
     */
    private static final byte DATA_FORMAT_VERSION_[] = {(byte)0x5, (byte)0,
                                             (byte)Trie.INDEX_STAGE_1_SHIFT_,
                                             (byte)Trie.INDEX_STAGE_2_SHIFT_};
}
