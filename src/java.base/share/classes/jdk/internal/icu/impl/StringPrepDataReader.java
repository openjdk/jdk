// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 ******************************************************************************
 * Copyright (C) 2003-2015, International Business Machines Corporation and
 * others. All Rights Reserved.
 ******************************************************************************
 *
 * Created on May 2, 2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */

package jdk.internal.icu.impl;

import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * @author ram
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public final class StringPrepDataReader implements ICUBinary.Authenticate {
    private final static boolean debug = ICUDebug.enabled("NormalizerDataReader");

   /**
    * <p>private constructor.</p>
    * @param bytes ICU StringPrep data file buffer
    * @exception IOException throw if data file fails authentication
    */
    public StringPrepDataReader(ByteBuffer bytes)
                                        throws IOException{
        if(debug) System.out.println("Bytes in buffer " + bytes.remaining());

        byteBuffer = bytes;
        unicodeVersion = ICUBinary.readHeader(byteBuffer, DATA_FORMAT_ID, this);

        if(debug) System.out.println("Bytes left in byteBuffer " + byteBuffer.remaining());
    }

    public char[] read(int length) throws IOException{
        //Read the extra data
        return ICUBinary.getChars(byteBuffer, length, 0);
    }

    @Override
    public boolean isDataVersionAcceptable(byte version[]){
        return version[0] == DATA_FORMAT_VERSION[0]
               && version[2] == DATA_FORMAT_VERSION[2]
               && version[3] == DATA_FORMAT_VERSION[3];
    }
    public int[] readIndexes(int length)throws IOException{
        int[] indexes = new int[length];
        //Read the indexes
        for (int i = 0; i <length ; i++) {
             indexes[i] = byteBuffer.getInt();
        }
        return indexes;
    }

    public byte[] getUnicodeVersion(){
        return ICUBinary.getVersionByteArrayFromCompactInt(unicodeVersion);
    }
    // private data members -------------------------------------------------


    /**
    * ICU data file input stream
    */
    private ByteBuffer byteBuffer;
    private int unicodeVersion;
    /**
    * File format version that this class understands.
    * No guarantees are made if a older version is used
    * see store.c of gennorm for more information and values
    */
    ///* dataFormat="SPRP" 0x53, 0x50, 0x52, 0x50  */
    private static final int DATA_FORMAT_ID = 0x53505250;
    private static final byte DATA_FORMAT_VERSION[] = {(byte)0x3, (byte)0x2,
                                                        (byte)0x5, (byte)0x2};
}
