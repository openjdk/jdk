/*
 * Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.io;

import sun.nio.cs.ext.IBM933;

/**
* @author Malcolm Ayres
*/

public class CharToByteCp933 extends CharToByteConverter
{
    private static final char SBase = '\uAC00';
    private static final char LBase = '\u1100';
    private static final char VBase = '\u1161';
    private static final char TBase = '\u11A7';
    private static final int  VCount = 21;
    private static final int  TCount = 28;
    private static final byte G0 = 0;
    private static final byte G1 = 1;
    private static final byte G2 = 2;
    private static final byte G3 = 3;
    private byte   charState = G0;
    private char   l, v, t;

    private int    byteState;
    private byte[] outputByte;
    private static final int SBCS = 0;
    private static final int DBCS = 1;
    private static final byte SO = 0x0e;
    private static final byte SI = 0x0f;

    private char highHalfZoneCode;

    private short[] index1;
    private String index2;
    private String index2a;
    private int  mask1;
    private int  mask2;
    private int  shift;

    private final static IBM933 nioCoder = new IBM933();


    public CharToByteCp933() {
       super();
       byteState = doSBCS()?SBCS:DBCS;
       highHalfZoneCode = 0;
       outputByte = new byte[2];
       mask1 = 0xFFF8;
       mask2 = 0x0007;
       shift = 3;
       index1 = nioCoder.getEncoderIndex1();
       index2 = nioCoder.getEncoderIndex2();
       index2a = nioCoder.getEncoderIndex2a();
       subBytes = new byte[1];
       subBytes[0] = 0x6f;
    }

    /**
      * flush out any residual data and reset the buffer state
      */
    public int flush(byte[] output, int outStart, int outEnd)
        throws MalformedInputException,
               ConversionBufferFullException
    {
       int bytesOut;

       byteOff = outStart;

       if (highHalfZoneCode != 0) {
           reset();
           badInputLength = 0;
           throw new MalformedInputException();
       }

       if (charState != G0) {
           try {
              unicodeToBuffer(composeHangul() ,output, outEnd);
           }
           catch(UnknownCharacterException e) {
              reset();
              badInputLength = 0;
              throw new MalformedInputException();
           }
           charState = G0;
       }

       if (byteState == DBCS && doSBCS()) {
          if (byteOff >= outEnd)
             throw new ConversionBufferFullException();
          output[byteOff++] = SI;
          byteState = SBCS;
       }

       bytesOut = byteOff - outStart;

       reset();
       return bytesOut;
    }

    /**
     * Resets converter to its initial state.
     */
    public void reset() {
       byteState = doSBCS()?SBCS:DBCS;
       highHalfZoneCode = 0;
       charState = G0;
       charOff = byteOff = 0;
    }

    /**
     * Returns true if the given character can be converted to the
     * target character encoding.
     */
    public boolean canConvert(char ch) {
       return encodeHangul(ch) != -1;
    }

    /**
     * Sets the substitution bytes to use when the converter is in
     * substitution mode.  The given bytes should represent a valid
     * character in the target character encoding.
     */

    public void setSubstitutionBytes( byte[] newSubBytes )
       throws IllegalArgumentException
    {
       if( newSubBytes.length > 2 || newSubBytes.length == 0) {
           throw new IllegalArgumentException();
       }

       subBytes = new byte[ newSubBytes.length ];
       System.arraycopy( newSubBytes, 0, subBytes, 0, newSubBytes.length );

    }

    /**
     * Character conversion
     */

    public int convert(char[] input, int inOff, int inEnd,
                       byte[] output, int outOff, int outEnd)
        throws UnknownCharacterException, MalformedInputException,
               ConversionBufferFullException
    {
       char    inputChar;
       int     inputSize;

       charOff = inOff;
       byteOff = outOff;

       while (charOff < inEnd) {

          if (highHalfZoneCode == 0) {
             inputChar = input[charOff];
             inputSize = 1;
          } else {
             inputChar = highHalfZoneCode;
             inputSize = 0;
             highHalfZoneCode = 0;
          }

          switch (charState) {
          case G0:

             l = LBase;
             v = VBase;
             t = TBase;

             if ( isLeadingC(inputChar) ) {     // Leading Consonant
                l = inputChar;
                charState = G1;
                break;
             }

             if ( isVowel(inputChar) ) {        // Vowel
                v = inputChar;
                charState = G2;
                break;
             }

             if ( isTrailingC(inputChar) ) {    // Trailing Consonant
                t = inputChar;
                charState = G3;
                break;
             }

             break;

          case G1:
             if ( isLeadingC(inputChar) ) {     // Leading Consonant
                l = composeLL(l, inputChar);
                break;
             }

             if ( isVowel(inputChar) ) {        // Vowel
                v = inputChar;
                charState = G2;
                break;
             }

             if ( isTrailingC(inputChar) ) {    // Trailing Consonant
                t = inputChar;
                charState = G3;
                break;
             }

             unicodeToBuffer(composeHangul(), output, outEnd);

             charState = G0;
             break;

          case G2:
             if ( isLeadingC(inputChar) ) {     // Leading Consonant

                unicodeToBuffer(composeHangul(), output, outEnd);

                l = inputChar;
                v = VBase;
                t = TBase;
                charState = G1;
                break;
             }

             if ( isVowel(inputChar) ) {        // Vowel
                v = composeVV(l, inputChar);
                charState = G2;
                break;
             }

             if ( isTrailingC(inputChar) ) {    // Trailing Consonant
                t = inputChar;
                charState = G3;
                break;
             }

             unicodeToBuffer(composeHangul(), output, outEnd);

             charState = G0;

             break;

          case G3:
             if ( isTrailingC(inputChar) ) {    // Trailing Consonant
                t = composeTT(t, inputChar);
                charState = G3;
                break;
             }

             unicodeToBuffer(composeHangul(), output, outEnd);

             charState = G0;

             break;
          }

          if (charState != G0)
             charOff++;
          else {

             // Is this a high surrogate?
             if(inputChar >= '\ud800' && inputChar <= '\udbff') {
                // Is this the last character of the input?
                if (charOff + inputSize >= inEnd) {
                   highHalfZoneCode = inputChar;
                   charOff += inputSize;
                   break;
                }

                // Is there a low surrogate following?
                inputChar = input[charOff + inputSize];
                if (inputChar >= '\udc00' && inputChar <= '\udfff') {
                   // We have a valid surrogate pair.  Too bad we don't do
                   // surrogates.  Is substitution enabled?
                   if (subMode) {
                      if (subBytes.length == 1) {
                         outputByte[0] = 0x00;
                         outputByte[1] = subBytes[0];
                      } else {
                         outputByte[0] = subBytes[0];
                         outputByte[1] = subBytes[1];
                      }

                      bytesToBuffer(outputByte, output, outEnd);
                      inputSize++;
                   } else {
                      badInputLength = 2;
                      throw new UnknownCharacterException();
                   }
                } else {
                   // We have a malformed surrogate pair
                   badInputLength = 1;
                   throw new MalformedInputException();
                }
             }

               // Is this an unaccompanied low surrogate?
             else
                if (inputChar >= '\uDC00' && inputChar <= '\uDFFF') {
                   badInputLength = 1;
                   throw new MalformedInputException();
                } else {
                   unicodeToBuffer(inputChar, output, outEnd);
                }

             charOff += inputSize;

          }

       }

       return byteOff - outOff;

    }

    private char composeHangul() {
       int lIndex, vIndex, tIndex;

       lIndex = l - LBase;
       vIndex = v - VBase;
       tIndex = t - TBase;

       return (char)((lIndex * VCount + vIndex) * TCount + tIndex + SBase);
    }

    private char composeLL(char l1, char l2) {
       return l2;
    }

    private char composeVV(char v1, char v2) {
       return v2;
    }

    private char composeTT(char t1, char t2) {
       return t2;
    }

    private boolean isLeadingC(char c) {
       return (c >= LBase && c <= '\u1159');
    }

    private boolean isVowel(char c) {
       return (c >= VBase && c <= '\u11a2');
    }

    private boolean isTrailingC(char c) {
       return (c >= TBase && c <= '\u11f9');
    }

    /**
     * returns the maximum number of bytes needed to convert a char
     */
    public int getMaxBytesPerChar() {
       return 4;
    }

    /**
     * Return the character set ID
     */
    public String getCharacterEncoding() {
       return "Cp933";
    }

    /**
     * private function to add the bytes to the output buffer
     */
    private void bytesToBuffer(byte[] theBytes, byte[] output, int outEnd)
        throws ConversionBufferFullException,
               UnknownCharacterException {

       int spaceNeeded;

       // Set the output buffer into the correct state

       if (byteState == DBCS && theBytes[0] == 0x00) {
          if (byteOff >= outEnd)
             throw new ConversionBufferFullException();
          byteState = SBCS;
          output[byteOff++] = SI;
       } else
          if (byteState == SBCS && theBytes[0] != 0x00) {
             if (byteOff >= outEnd)
                throw new ConversionBufferFullException();
             byteState = DBCS;
             output[byteOff++] = SO;
          }


       // ensure sufficient space for the bytes(s)

       if (byteState == DBCS)
          spaceNeeded = 2;
       else
          spaceNeeded = 1;

       if (byteOff + spaceNeeded > outEnd)
          throw new ConversionBufferFullException();

       // move the data into the buffer

       if (byteState == SBCS)
          output[byteOff++] = theBytes[1];
       else {
          output[byteOff++] = theBytes[0];
          output[byteOff++] = theBytes[1];
       }
    }

    // return -1 for unmappable character
    protected int encodeHangul(char unicode) {
        int theBytes;
        int index;
        index = index1[((unicode & mask1) >> shift)] + (unicode & mask2);
        if (index < 15000)
            theBytes = (int)(index2.charAt(index));
        else
            theBytes = (int)(index2a.charAt(index-15000));

       // The input char is undefined if theBytes is 0 and the char is NOT unicode 0
        if (theBytes == 0 && unicode != '\u0000')
            return -1;
        return theBytes;
    }

    /**
     * private function to add a unicode character to the output buffer
     */
    private void unicodeToBuffer(char unicode, byte[] output, int outEnd)
        throws ConversionBufferFullException,
               UnknownCharacterException {

       // first we convert the unicode to its byte representation
       int theBytes = encodeHangul(unicode);

       // if the unicode was not mappable - look for the substitution bytes
       if (theBytes == -1) {
          if (subMode) {
             if (subBytes.length == 1) {
                outputByte[0] = 0x00;
                outputByte[1] = subBytes[0];
             } else {
                outputByte[0] = subBytes[0];
                outputByte[1] = subBytes[1];
             }
          } else {
             badInputLength = 1;
             throw new UnknownCharacterException();
          }
       } else {
           outputByte[0] = (byte)((theBytes & 0x0000ff00)>>8);
           outputByte[1] = (byte)(theBytes & 0x000000ff);
       }

       // now put the bytes in the buffer
       bytesToBuffer(outputByte, output, outEnd);
    }

    //Methods below are for subclass Cp834
    protected boolean doSBCS() {
        return true;
    }
}
