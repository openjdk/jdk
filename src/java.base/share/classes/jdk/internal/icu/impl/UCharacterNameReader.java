// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2015, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package jdk.internal.icu.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
* <p>Internal reader class for ICU data file uname.dat containing
* Unicode codepoint name data.</p>
* <p>This class simply reads unames.icu, authenticates that it is a valid
* ICU data file and split its contents up into blocks of data for use in
* <a href=UCharacterName.html>jdk.internal.icu.impl.UCharacterName</a>.
* </p>
* <p>unames.icu which is in big-endian format is jared together with this
* package.</p>
* @author Syn Wee Quek
* @since release 2.1, February 1st 2002
*/

final class UCharacterNameReader implements ICUBinary.Authenticate
{
    // public methods ----------------------------------------------------

    @Override
    public boolean isDataVersionAcceptable(byte version[])
    {
        return version[0] == 1;
    }

    // protected constructor ---------------------------------------------

    /**
    * <p>Protected constructor.</p>
    * @param bytes ICU uprop.dat file buffer
    * @exception IOException throw if data file fails authentication
    */
    protected UCharacterNameReader(ByteBuffer bytes) throws IOException
    {
        ICUBinary.readHeader(bytes, DATA_FORMAT_ID_, this);
        m_byteBuffer_ = bytes;
    }

    // protected methods -------------------------------------------------

    /**
    * Read and break up the stream of data passed in as arguments
    * and fills up UCharacterName.
    * If unsuccessful false will be returned.
    * @param data instance of datablock
    * @exception IOException thrown when there's a data error.
    */
    protected void read(UCharacterName data) throws IOException
    {
        // reading index
        m_tokenstringindex_ = m_byteBuffer_.getInt();
        m_groupindex_       = m_byteBuffer_.getInt();
        m_groupstringindex_ = m_byteBuffer_.getInt();
        m_algnamesindex_    = m_byteBuffer_.getInt();

        // reading tokens
        int count = m_byteBuffer_.getChar();
        char token[] = ICUBinary.getChars(m_byteBuffer_, count, 0);
        int size = m_groupindex_ - m_tokenstringindex_;
        byte tokenstr[] = new byte[size];
        m_byteBuffer_.get(tokenstr);
        data.setToken(token, tokenstr);

        // reading the group information records
        count = m_byteBuffer_.getChar();
        data.setGroupCountSize(count, GROUP_INFO_SIZE_);
        count *= GROUP_INFO_SIZE_;
        char group[] = ICUBinary.getChars(m_byteBuffer_, count, 0);

        size = m_algnamesindex_ - m_groupstringindex_;
        byte groupstring[] = new byte[size];
        m_byteBuffer_.get(groupstring);

        data.setGroup(group, groupstring);

        count = m_byteBuffer_.getInt();
        UCharacterName.AlgorithmName alg[] =
                                 new UCharacterName.AlgorithmName[count];

        for (int i = 0; i < count; i ++)
        {
            UCharacterName.AlgorithmName an = readAlg();
            if (an == null) {
                throw new IOException("unames.icu read error: Algorithmic names creation error");
            }
            alg[i] = an;
        }
        data.setAlgorithm(alg);
    }

    /**
    * <p>Checking the file for the correct format.</p>
    * @param dataformatid
    * @param dataformatversion
    * @return true if the file format version is correct
    */
    ///CLOVER:OFF
    protected boolean authenticate(byte dataformatid[],
                                   byte dataformatversion[])
    {
        return Arrays.equals(
                ICUBinary.getVersionByteArrayFromCompactInt(DATA_FORMAT_ID_),
                dataformatid) &&
               isDataVersionAcceptable(dataformatversion);
    }
    ///CLOVER:ON

    // private variables -------------------------------------------------

    /**
    * Byte buffer for names
    */
    private ByteBuffer m_byteBuffer_;
    /**
    * Size of the group information block in number of char
    */
    private static final int GROUP_INFO_SIZE_ = 3;

    /**
    * Index of the offset information
    */
    private int m_tokenstringindex_;
    private int m_groupindex_;
    private int m_groupstringindex_;
    private int m_algnamesindex_;

    /**
    * Size of an algorithmic name information group
    * start code point size + end code point size + type size + variant size +
    * size of data size
    */
    private static final int ALG_INFO_SIZE_ = 12;

    /**
    * File format id that this class understands.
    */
    private static final int DATA_FORMAT_ID_ = 0x756E616D;

    // private methods ---------------------------------------------------

    /**
    * Reads an individual record of AlgorithmNames
    * @return an instance of AlgorithNames if read is successful otherwise null
    * @exception IOException thrown when file read error occurs or data is corrupted
    */
    private UCharacterName.AlgorithmName readAlg() throws IOException
    {
        UCharacterName.AlgorithmName result =
                                       new UCharacterName.AlgorithmName();
        int rangestart = m_byteBuffer_.getInt();
        int rangeend   = m_byteBuffer_.getInt();
        byte type      = m_byteBuffer_.get();
        byte variant   = m_byteBuffer_.get();
        if (!result.setInfo(rangestart, rangeend, type, variant)) {
            return null;
        }

        int size = m_byteBuffer_.getChar();
        if (type == UCharacterName.AlgorithmName.TYPE_1_)
        {
            char factor[] = ICUBinary.getChars(m_byteBuffer_, variant, 0);

            result.setFactor(factor);
            size -= (variant << 1);
        }

        StringBuilder prefix = new StringBuilder();
        char c = (char)(m_byteBuffer_.get() & 0x00FF);
        while (c != 0)
        {
            prefix.append(c);
            c = (char)(m_byteBuffer_.get() & 0x00FF);
        }

        result.setPrefix(prefix.toString());

        size -= (ALG_INFO_SIZE_ + prefix.length() + 1);

        if (size > 0)
        {
            byte string[] = new byte[size];
            m_byteBuffer_.get(string);
            result.setFactorString(string);
        }
        return result;
    }
}
