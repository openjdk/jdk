/*
 * Copyright (c) 1997, 2002, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @author Limin Shi
 */

public abstract class CharToByteDoubleByte extends CharToByteConverter {

    /*
     * 1st level index, provided by subclass
     */
    protected short index1[];

    /*
     * 2nd level index, provided by subclass
     */
    protected String  index2[];

    protected char highHalfZoneCode;

    public short[] getIndex1() {
        return index1;
    }

    public String[] getIndex2() {
        return index2;
    }

    public int flush(byte[] output, int outStart, int outEnd)
        throws MalformedInputException, ConversionBufferFullException
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
     * @throw UnsupportedCharacterException for any character
     * that cannot be converted to the external character set.
     */
    public int convert(char[] input, int inOff, int inEnd,
                       byte[] output, int outOff, int outEnd)
        throws MalformedInputException, UnknownCharacterException,
               ConversionBufferFullException
    {
        char    inputChar;                 // Input character to be converted
        byte[]  outputByte;                // Output byte written to output
        int     inputSize = 0;             // Size of input
        int     outputSize = 0;            // Size of output
        byte[]  tmpbuf = new byte[2];

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
            inputSize = 1;
            outputByte = tmpbuf;
            inputChar = input[charOff]; // Get the input character

            // Is this a high surrogate?
            if(inputChar >= '\uD800' && inputChar <= '\uDBFF') {
                // Is this the last character of the input?
                if (charOff + 1 >= inEnd) {
                    highHalfZoneCode = inputChar;
                    break;
                }

                // Is there a low surrogate following?
                inputChar = input[charOff + 1];
                if (inputChar >= '\uDC00' && inputChar <= '\uDFFF') {
                    // We have a valid surrogate pair.  Too bad we don't do
                    // surrogates.  Is substitution enabled?
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
            } else {
                outputSize = convSingleByte(inputChar, outputByte);
                if (outputSize == 0) { // DoubleByte
                    int ncode = getNative(inputChar);
                    if (ncode != 0 ) {
                        outputByte[0] = (byte) ((ncode & 0xff00) >> 8);
                        outputByte[1] = (byte) (ncode & 0xff);
                        outputSize = 2;
                    } else {
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
        return 2;
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
        byte[] outByte = new byte[2];

        if ((ch == (char) 0) || (convSingleByte(ch, outByte) != 0))
            return true;
        if (this.getNative(ch) != 0)
            return true;
        return false;
    }


    /*
     * Can be changed by subclass
     */
    protected int convSingleByte(char inputChar, byte[] outputByte) {
        if (inputChar < 0x80) {
            outputByte[0] = (byte)(inputChar & 0x7f);
            return 1;
        }
        return 0;
    }

    /*
     * Can be changed by subclass
     */
    protected int getNative(char ch) {
        int offset = index1[((ch & 0xff00) >> 8 )] << 8;
        return index2[offset >> 12].charAt((offset & 0xfff) + (ch & 0xff));
    }

}
