/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * $Id: Base64.java,v 1.4 2006/01/27 12:49:50 vj135062 Exp $
 * $Revision: 1.4 $
 * $Date: 2006/01/27 12:49:50 $
 */


package com.sun.xml.internal.messaging.saaj.util;


// Cut & paste from tomcat

/**
 * This class provides encode/decode for RFC 2045 Base64 as
 * defined by RFC 2045, N. Freed and N. Borenstein.
 * RFC 2045: Multipurpose Internet Mail Extensions (MIME)
 * Part One: Format of Internet Message Bodies. Reference
 * 1996 Available at: http://www.ietf.org/rfc/rfc2045.txt
 * This class is used by XML Schema binary format validation
 *
 * @author Jeffrey Rodriguez
 * @version $Revision: 1.4 $ $Date: 2006/01/27 12:49:50 $
 */
public final class Base64 {


    static private final int  BASELENGTH         = 255;
    static private final int  LOOKUPLENGTH       = 63;
    static private final int  TWENTYFOURBITGROUP = 24;
    static private final int  EIGHTBIT           = 8;
    static private final int  SIXTEENBIT         = 16;
    static private final int  SIXBIT             = 6;
    static private final int  FOURBYTE           = 4;


    static private final byte PAD               = ( byte ) '=';
    static private byte [] base64Alphabet       = new byte[BASELENGTH];
    static private byte [] lookUpBase64Alphabet = new byte[LOOKUPLENGTH];

    static {

        for (int i = 0; i<BASELENGTH; i++ ) {
            base64Alphabet[i] = -1;
        }
        for ( int i = 'Z'; i >= 'A'; i-- ) {
            base64Alphabet[i] = (byte) (i-'A');
        }
        for ( int i = 'z'; i>= 'a'; i--) {
            base64Alphabet[i] = (byte) ( i-'a' + 26);
        }

        for ( int i = '9'; i >= '0'; i--) {
            base64Alphabet[i] = (byte) (i-'0' + 52);
        }

        base64Alphabet['+']  = 62;
        base64Alphabet['/']  = 63;

       for (int i = 0; i<=25; i++ )
            lookUpBase64Alphabet[i] = (byte) ('A'+i );

        for (int i = 26,  j = 0; i<=51; i++, j++ )
            lookUpBase64Alphabet[i] = (byte) ('a'+ j );

        for (int i = 52,  j = 0; i<=61; i++, j++ )
            lookUpBase64Alphabet[i] = (byte) ('0' + j );

    }


    static boolean isBase64( byte octect ) {
        //shall we ignore white space? JEFF??
        return(octect == PAD || base64Alphabet[octect] != -1 );
    }


    static boolean isArrayByteBase64( byte[] arrayOctect ) {
        int length = arrayOctect.length;
        if ( length == 0 )
            return false;
        for ( int i=0; i < length; i++ ) {
            if ( Base64.isBase64( arrayOctect[i] ) == false)
                return false;
        }
        return true;
    }

    /**
     * Encodes hex octects into Base64
     *
     * @param binaryData Array containing binaryData
     * @return Encoded Base64 array
     */
    public static byte[] encode( byte[] binaryData ) {
        int      lengthDataBits    = binaryData.length*EIGHTBIT;
        int      fewerThan24bits   = lengthDataBits%TWENTYFOURBITGROUP;
        int      numberTriplets    = lengthDataBits/TWENTYFOURBITGROUP;
        byte     encodedData[]     = null;


        if ( fewerThan24bits != 0 ) //data not divisible by 24 bit
            encodedData = new byte[ (numberTriplets + 1 )*4  ];
        else // 16 or 8 bit
            encodedData = new byte[ numberTriplets*4 ];

        byte k=0, l=0, b1=0,b2=0,b3=0;

        int encodedIndex = 0;
        int dataIndex   = 0;
        int i           = 0;
        for ( i = 0; i<numberTriplets; i++ ) {

            dataIndex = i*3;
            b1 = binaryData[dataIndex];
            b2 = binaryData[dataIndex + 1];
            b3 = binaryData[dataIndex + 2];

            l  = (byte)(b2 & 0x0f);
            k  = (byte)(b1 & 0x03);

            encodedIndex = i*4;
            encodedData[encodedIndex]   = lookUpBase64Alphabet[ b1 >>2 ];
            encodedData[encodedIndex+1] = lookUpBase64Alphabet[(b2 >>4 ) |
( k<<4 )];
            encodedData[encodedIndex+2] = lookUpBase64Alphabet[ (l <<2 ) |
( b3>>6)];
            encodedData[encodedIndex+3] = lookUpBase64Alphabet[ b3 & 0x3f ];
        }

        // form integral number of 6-bit groups
        dataIndex    = i*3;
        encodedIndex = i*4;
        if (fewerThan24bits == EIGHTBIT ) {
            b1 = binaryData[dataIndex];
            k = (byte) ( b1 &0x03 );
            encodedData[encodedIndex]     = lookUpBase64Alphabet[ b1 >>2 ];
            encodedData[encodedIndex + 1] = lookUpBase64Alphabet[ k<<4 ];
            encodedData[encodedIndex + 2] = PAD;
            encodedData[encodedIndex + 3] = PAD;
        } else if ( fewerThan24bits == SIXTEENBIT ) {

            b1 = binaryData[dataIndex];
            b2 = binaryData[dataIndex +1 ];
            l = ( byte ) ( b2 &0x0f );
            k = ( byte ) ( b1 &0x03 );
            encodedData[encodedIndex]     = lookUpBase64Alphabet[ b1 >>2 ];
            encodedData[encodedIndex + 1] = lookUpBase64Alphabet[ (b2 >>4 )
| ( k<<4 )];
            encodedData[encodedIndex + 2] = lookUpBase64Alphabet[ l<<2 ];
            encodedData[encodedIndex + 3] = PAD;
        }
        return encodedData;
    }


    /**
     * Decodes Base64 data into octects
     *
     * @param binaryData Byte array containing Base64 data
     * @return Array containind decoded data.
     */
    public byte[] decode( byte[] base64Data ) {
        int      numberQuadruple    = base64Data.length/FOURBYTE;
        byte     decodedData[]      = null;
        byte     b1=0,b2=0,b3=0, b4=0, marker0=0, marker1=0;

        // Throw away anything not in base64Data
        // Adjust size

        int encodedIndex = 0;
        int dataIndex    = 0;
        decodedData      = new byte[ numberQuadruple*3 + 1 ];

        for (int i = 0; i<numberQuadruple; i++ ) {
            dataIndex = i*4;
            marker0   = base64Data[dataIndex +2];
            marker1   = base64Data[dataIndex +3];

            b1 = base64Alphabet[base64Data[dataIndex]];
            b2 = base64Alphabet[base64Data[dataIndex +1]];

            if ( marker0 != PAD && marker1 != PAD ) {     //No PAD e.g 3cQl
                b3 = base64Alphabet[ marker0 ];
                b4 = base64Alphabet[ marker1 ];

                decodedData[encodedIndex]   = (byte)(  b1 <<2 | b2>>4 ) ;
                decodedData[encodedIndex+1] = (byte)(((b2 & 0xf)<<4 ) |(
(b3>>2) & 0xf) );
                decodedData[encodedIndex+2] = (byte)( b3<<6 | b4 );
            } else if ( marker0 == PAD ) {               //Two PAD e.g. 3c[Pad][Pad]
                decodedData[encodedIndex]   = (byte)(  b1 <<2 | b2>>4 ) ;
                decodedData[encodedIndex+1] = (byte)((b2 & 0xf)<<4 );
                decodedData[encodedIndex+2] = (byte) 0;
            } else if ( marker1 == PAD ) {              //One PAD e.g. 3cQ[Pad]
                b3 = base64Alphabet[ marker0 ];

                decodedData[encodedIndex]   = (byte)(  b1 <<2 | b2>>4 );
                decodedData[encodedIndex+1] = (byte)(((b2 & 0xf)<<4 ) |(
(b3>>2) & 0xf) );
                decodedData[encodedIndex+2] = (byte)( b3<<6);
            }
            encodedIndex += 3;
        }
        return decodedData;

    }

    static final int base64[]= {
        64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
            64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
            64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 62, 64, 64, 64, 63,
            52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 64, 64, 64, 64, 64, 64,
            64,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14,
            15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 64, 64, 64, 64, 64,
            64, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
            41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 64, 64, 64, 64, 64,
            64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
            64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
            64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
            64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
            64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
            64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
            64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
            64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64
    };

    public static String base64Decode( String orig ) {
        char chars[]=orig.toCharArray();
        StringBuffer sb=new StringBuffer();
        int i=0;

        int shift = 0;   // # of excess bits stored in accum
        int acc = 0;

        for (i=0; i<chars.length; i++) {
            int v = base64[ chars[i] & 0xFF ];

            if ( v >= 64 ) {
                if( chars[i] != '=' )
                    System.out.println("Wrong char in base64: " + chars[i]);
            } else {
                acc= ( acc << 6 ) | v;
                shift += 6;
                if ( shift >= 8 ) {
                    shift -= 8;
                    sb.append( (char) ((acc >> shift) & 0xff));
                }
            }
        }
        return sb.toString();
    }


}
