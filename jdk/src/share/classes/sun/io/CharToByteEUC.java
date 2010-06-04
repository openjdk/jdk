/*
 * Copyright (c) 1997, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package sun.io;

public abstract class CharToByteEUC extends CharToByteConverter
{

    private char highHalfZoneCode;
    private byte[] outputByte;

    protected short  index1[];
    protected String index2;
    protected String index2a;
    protected String index2b;
    protected String index2c;
    protected int    mask1;
    protected int    mask2;
    protected int    shift;

    private byte[] workByte = new byte[4];

    /**
      * flush out any residual data and reset the buffer state
      */
    public int flush(byte [] output, int outStart, int outEnd)
        throws MalformedInputException, ConversionBufferFullException
    {

       if (highHalfZoneCode != 0) {
          reset();
          badInputLength = 0;
          throw new MalformedInputException();
       }

       reset();
       return 0;
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

           outputByte = workByte;

           int     index;
           int     theBytes;
           int     spaceNeeded;
           boolean allZeroes = true;
           int     i;


           if (highHalfZoneCode == 0) {
              inputChar = input[charOff];
              inputSize = 1;
           } else {
              inputChar = highHalfZoneCode;
              inputSize = 0;
              highHalfZoneCode = 0;
           }


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
                    outputByte = subBytes;
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

                 String theChars;
                 char   aChar;

                 // We have a valid character, get the bytes for it
                 index = index1[((inputChar & mask1) >> shift)] + (inputChar & mask2);

                 if (index < 7500)
                   theChars = index2;
                 else
                   if (index < 15000) {
                     index = index - 7500;
                     theChars = index2a;
                   }
                   else
                     if (index < 22500){
                       index = index - 15000;
                       theChars = index2b;
                     }
                     else {
                       index = index - 22500;
                       theChars = index2c;
                     }

                 aChar = theChars.charAt(2*index);
                 outputByte[0] = (byte)((aChar & 0xff00)>>8);
                 outputByte[1] = (byte)(aChar & 0x00ff);
                 aChar = theChars.charAt(2*index + 1);
                 outputByte[2] = (byte)((aChar & 0xff00)>>8);
                 outputByte[3] = (byte)(aChar & 0x00ff);
              }

           // if there was no mapping - look for substitution characters

           for (i = 0; i < outputByte.length; i++) {
             if (outputByte[i] != 0x00) {
               allZeroes = false;
               break;
             }
           }

           if (allZeroes && inputChar != '\u0000')
           {
              if (subMode) {
                 outputByte = subBytes;
              } else {
                badInputLength = 1;
                throw new UnknownCharacterException();
              }
           }

           int oindex = 0;
           for (spaceNeeded = outputByte.length; spaceNeeded > 1; spaceNeeded--){
             if (outputByte[oindex++] != 0x00 )
               break;
           }

           if (byteOff + spaceNeeded > outEnd)
              throw new ConversionBufferFullException();


           for (i = outputByte.length - spaceNeeded; i < outputByte.length; i++) {
              output[byteOff++] = outputByte[i];
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
    }

    /**
     * Returns the maximum number of bytes needed to convert a char.
     */
    public int getMaxBytesPerChar() {
        return 2;
    }


    /**
     * Returns true if the given character can be converted to the
     * target character encoding.
     */
    public boolean canConvert(char ch) {
       int    index;
       String theChars;

       index = index1[((ch & mask1) >> shift)] + (ch & mask2);

       if (index < 7500)
         theChars = index2;
       else
         if (index < 15000) {
           index = index - 7500;
           theChars = index2a;
         }
         else
           if (index < 22500){
             index = index - 15000;
             theChars = index2b;
           }
           else {
             index = index - 22500;
             theChars = index2c;
           }

       if (theChars.charAt(2*index) != '\u0000' ||
                    theChars.charAt(2*index + 1) != '\u0000')
         return (true);

       // only return true if input char was unicode null - all others are
       //     undefined
       return( ch == '\u0000');

    }

}
