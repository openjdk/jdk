/*
 * Copyright (c) 1996, 2008, Oracle and/or its affiliates. All rights reserved.
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

import static sun.nio.cs.CharsetMapping.*;

/**
* A table driven conversion from char to byte for single byte
* character sets.  Tables will reside in the class CharToByteYYYYY,
* where YYYYY is a unique character set identifier

    < TBD: Tables are of the form... >

*
* @author Lloyd Honomichl
* @author Asmus Freytag
* @version 8/28/96
*/

public abstract class CharToByteSingleByte extends CharToByteConverter {

    /*
     * 1st level index, provided by subclass
     */
    protected char[] index1;

    /*
     * 2nd level index, provided by subclass
     */
    protected char[] index2;

    /*
     * Mask to isolate bits for 1st level index, from subclass
     */
    protected int   mask1;

    /*
     * Mask to isolate bits for 2nd level index, from subclass
     */
    protected int   mask2;

    /*
     * Shift to isolate bits for 1st level index, from subclass
     */
    protected int   shift;

    private char highHalfZoneCode;

    public char[] getIndex1() {
        return index1;
    }

    public char[] getIndex2() {
        return index2;
    }
    public int flush(byte[] output, int outStart, int outEnd)
        throws MalformedInputException
    {
        if (highHalfZoneCode != 0) {
            highHalfZoneCode = 0;
            badInputLength = 0;
            throw new MalformedInputException();
        }
        byteOff = charOff = 0;
        return 0;
    }

    /**
     * Converts characters to sequences of bytes.
     * Conversions that result in Exceptions can be restarted by calling
     * convert again, with appropriately modified parameters.
     * @return the characters written to output.
     * @param input char array containing text in Unicode
     * @param inStart offset in input array
     * @param inEnd offset of last byte to be converted
     * @param output byte array to receive conversion result
     * @param outStart starting offset
     * @param outEnd offset of last byte to be written to
     * @throw MalformedInputException for any sequence of chars that is
     * illegal in Unicode (principally unpaired surrogates
     * and \uFFFF or \uFFFE), including any partial surrogate pair
     * which occurs at the end of an input buffer.
     * @throw UnsupportedCharacterException for any character that
     * that cannot be converted to the external character set.
     */
    public int convert(char[] input, int inOff, int inEnd,
                       byte[] output, int outOff, int outEnd)
        throws MalformedInputException,
               UnknownCharacterException,
               ConversionBufferFullException
    {
        char    inputChar;          // Input character to be converted
        byte[]  outputByte;         // Output byte written to output
        int     inputSize;          // Size of input
        int     outputSize;         // Size of output

        byte[]  tmpArray = new byte[1];

        // Record beginning offsets
        charOff = inOff;
        byteOff = outOff;

        if (highHalfZoneCode != 0) {
            inputChar = highHalfZoneCode;
            highHalfZoneCode = 0;
            if (input[inOff] >= 0xdc00 && input[inOff] <= 0xdfff) {
                // This is legal UTF16 sequence.
                badInputLength = 1;
                throw new UnknownCharacterException();
            } else {
                // This is illegal UTF16 sequence.
                badInputLength = 0;
                throw new MalformedInputException();
            }
        }

        // Loop until we hit the end of the input
        while(charOff < inEnd) {

            outputByte = tmpArray;

            // Get the input character
            inputChar = input[charOff];

            // Default output size
            outputSize = 1;

            // Assume this is a simple character
            inputSize = 1;

            // Is this a high surrogate?
            if(inputChar >= '\uD800' && inputChar <= '\uDBFF') {
                // Is this the last character in the input?
                if (charOff + 1 >= inEnd) {
                    highHalfZoneCode = inputChar;
                    break;
                }

                // Is there a low surrogate following?
                inputChar = input[charOff + 1];
                if (inputChar >= '\uDC00' && inputChar <= '\uDFFF') {
                    // We have a valid surrogate pair.  Too bad we don't map
                    //  surrogates.  Is substitution enabled?
                    if (subMode) {
                        outputByte = subBytes;
                        outputSize = subBytes.length;
                        inputSize = 2;
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
            else if (inputChar >= '\uDC00' && inputChar <= '\uDFFF') {
                badInputLength = 1;
                throw new MalformedInputException();
            }

            // Not part of a surrogate, so look it up
            else {
                // Get output using two level lookup
                outputByte[0] = getNative(inputChar);

                // Might this character be unmappable?
                if (outputByte[0] == 0) {
                    // If outputByte is zero because the input was zero
                    //  then this character is actually mappable
                    if (input[charOff] != '\u0000') {
                        // We have an unmappable character
                        // Is substitution enabled?
                        if (subMode) {
                            outputByte = subBytes;
                            outputSize = subBytes.length;
                        } else {
                            badInputLength = 1;
                            throw new UnknownCharacterException();
                        }
                    }
                }
            }

            // If we don't have room for the output, throw an exception
            if (byteOff + outputSize > outEnd)
                throw new ConversionBufferFullException();

            // Put the byte in the output buffer
            for (int i = 0; i < outputSize; i++) {
                output[byteOff++] = outputByte[i];
            }
            charOff += inputSize;

        }

        // Return the length written to the output buffer
        return byteOff - outOff;
    }

    /**
     * the maximum number of bytes needed to hold a converted char
     * @returns the maximum number of bytes needed for a converted char
     */
    public int getMaxBytesPerChar() {
        return 1;
    }

    int encodeChar(char ch) {
        char index = index1[ch >> 8];
        if (index == UNMAPPABLE_ENCODING)
            return UNMAPPABLE_ENCODING;
        return index2[index + (ch & 0xff)];
    }

    public byte getNative(char inputChar) {
        int b = encodeChar(inputChar);
        if (b == UNMAPPABLE_ENCODING)
            return 0;
        return (byte)b;
    }

    /**
     *  Resets the converter.
     * Call this method to reset the converter to its initial state
     */
    public void reset() {
        byteOff = charOff = 0;
        highHalfZoneCode = 0;
    }

    /**
     * Return whether a character is mappable or not
     * @return true if a character is mappable
     */
    public boolean canConvert(char ch) {
        return encodeChar(ch) != UNMAPPABLE_ENCODING;
    }
}
