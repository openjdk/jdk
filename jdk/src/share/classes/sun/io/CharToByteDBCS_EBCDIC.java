/*
 * Copyright 1997-1999 Sun Microsystems, Inc.  All Rights Reserved.
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

import sun.nio.cs.Surrogate;
import sun.nio.cs.ext.DoubleByte;
import static sun.nio.cs.CharsetMapping.*;

public abstract class CharToByteDBCS_EBCDIC extends CharToByteConverter
{
    private static final int SBCS = 0;
    private static final int DBCS = 1;

    private static final byte SO = 0x0e;
    private static final byte SI = 0x0f;

    private int  currentState;
    private char highHalfZoneCode;
    private byte[] outputByte = new byte[2];

    private DoubleByte.Encoder enc;

    public CharToByteDBCS_EBCDIC(DoubleByte.Encoder enc) {
        super();
        highHalfZoneCode = 0;
        currentState = SBCS;
        this.enc = enc;
    }

    int encodeChar(char c) {
        return enc.encodeChar(c);
    }

    /**
      * flush out any residual data and reset the buffer state
      */
    public int flush(byte [] output, int outStart, int outEnd)
        throws MalformedInputException, ConversionBufferFullException
    {
        int bytesOut = 0;

        if (highHalfZoneCode != 0) {
            reset();
            badInputLength = 0;
            throw new MalformedInputException();
        }

        if (currentState == DBCS) {
          if (outStart >= outEnd)
            throw new ConversionBufferFullException();
          output[outStart] = SI;
          bytesOut++;
        }

        reset();
        return bytesOut;
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

        byteOff = outOff;
        charOff = inOff;

        while(charOff < inEnd) {

           int   index;
           int   theBytes;
           int   spaceNeeded;

           if (highHalfZoneCode == 0) {
              inputChar = input[charOff];
              inputSize = 1;
           } else {
              inputChar = highHalfZoneCode;
              inputSize = 0;
              highHalfZoneCode = 0;
           }

           // Is this a high surrogate?
           if (Character.isHighSurrogate(inputChar)) {
              // Is this the last character of the input?
              if (charOff + inputSize >= inEnd) {
                 highHalfZoneCode = inputChar;
                 charOff += inputSize;
                 break;
              }

              // Is there a low surrogate following?
              inputChar = input[charOff + inputSize];
              if (Character.isLowSurrogate(inputChar)) {
                 // We have a valid surrogate pair.  Too bad we don't do
                 // surrogates.  Is substitution enabled?
                 if (subMode) {
                    if (subBytes.length == 1) {
                       outputByte[0] = 0x00;
                       outputByte[1] = subBytes[0];
                    }
                    else {
                       outputByte[0] = subBytes[0];
                       outputByte[1] = subBytes[1];
                    }
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
           else if (Character.isLowSurrogate(inputChar)) {
               badInputLength = 1;
               throw new MalformedInputException();
           } else {

               // We have a valid character, get the bytes for it
               theBytes = encodeChar(inputChar);
               if (theBytes == UNMAPPABLE_ENCODING) {
                   // if there was no mapping - look for substitution characters
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
           }

           //Set the output buffer into the correct state

           if (currentState == DBCS && outputByte[0] == 0x00) {
              if (byteOff >= outEnd)
                 throw new ConversionBufferFullException();
              currentState = SBCS;
              output[byteOff++] = SI;
           } else
              if (currentState == SBCS && outputByte[0] != 0x00) {
                 if (byteOff >= outEnd) {
                    throw new ConversionBufferFullException();
                 }
                 currentState = DBCS;
                 output[byteOff++] = SO;
              }

           if (currentState == DBCS)
              spaceNeeded = 2;
           else
              spaceNeeded = 1;

           if (byteOff + spaceNeeded > outEnd) {
              throw new ConversionBufferFullException();
           }

           if (currentState == SBCS)
              output[byteOff++] = outputByte[1];
           else {
              output[byteOff++] = outputByte[0];
              output[byteOff++] = outputByte[1];
           }

           charOff += inputSize;
        }
        return byteOff - outOff;
    }



    /**
     * Resets converter to its initial state.
     */
    public void reset() {
       charOff = byteOff = 0;
       highHalfZoneCode = 0;
       currentState = SBCS;
    }


    /**
     * Returns the maximum number of bytes needed to convert a char.
     */
    public int getMaxBytesPerChar() {
       return 4;    //Fixed with bug 4199599 so tests would pass.
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
     * Returns true if the given character can be converted to the
     * target character encoding.
     */
    public boolean canConvert(char c) {
        return encodeChar(c) != UNMAPPABLE_ENCODING;
    }
}
