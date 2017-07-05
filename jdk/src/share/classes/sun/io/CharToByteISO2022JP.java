/*
 * Copyright (c) 1996, 1999, Oracle and/or its affiliates. All rights reserved.
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
import java.io.*;

public class CharToByteISO2022JP extends CharToByteJIS0208 {

    private static final int ASCII = 0;                 // ESC ( B
    private static final int JISX0201_1976 = 1;         // ESC ( J
    private static final int JISX0208_1978 = 2;         // ESC $ @
    private static final int JISX0208_1983 = 3;         // ESC $ B
    private static final int JISX0201_1976_KANA = 4;    // ESC ( I

    private char highHalfZoneCode;
    private boolean flushed = true;

    // JIS is state full encoding, so currentMode keep the
    // current codeset
    private int currentMode = ASCII;

    /**
     * Bytes for substitute for unmappable input.
     */
    // XXX: Assumes subBytes are ASCII string. Need to change Escape sequence
    // for other character sets.
    protected byte[] subBytesEscape = { (byte)0x1b, (byte)0x28, (byte)0x42 }; // ESC ( B
    protected int subBytesMode = ASCII;

    public int flush(byte[] output, int outStart, int outEnd)
        throws MalformedInputException, ConversionBufferFullException
    {
        if (highHalfZoneCode != 0) {
            highHalfZoneCode = 0;
            badInputLength = 0;
            throw new MalformedInputException();
        }

        if (!flushed && (currentMode != ASCII)) {
            if (outEnd - outStart < 3) {
                throw new ConversionBufferFullException();
            }
            output[outStart]     = (byte)0x1b;
            output[outStart + 1] = (byte)0x28;
            output[outStart + 2] = (byte)0x42;
            byteOff += 3;
            byteOff = charOff = 0;
            flushed = true;
            currentMode = ASCII;
            return 3;
        }
        return 0;
    }

    public int convert(char[] input, int inOff, int inEnd,
                       byte[] output, int outOff, int outEnd)
        throws MalformedInputException, UnknownCharacterException,
               ConversionBufferFullException

    {
        char    inputChar;          // Input character to be converted
        int     inputSize;          // Size of the input
        int     outputSize;         // Size of the output

        // Buffer for output bytes
        byte[]  tmpArray = new byte[6];
        byte[]  outputByte;

        flushed = false;

        // Make copies of input and output indexes
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

        // Loop until we run out of input
        while(charOff < inEnd) {
            outputByte = tmpArray;
            int newMode = currentMode; // Trace character mode changing

            // Get the input character
            inputChar = input[charOff];
            inputSize = 1;
            outputSize = 1;

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
                        if (currentMode != subBytesMode) {
                            System.arraycopy(subBytesEscape, 0, outputByte, 0,
                                             subBytesEscape.length);
                            outputSize = subBytesEscape.length;
                            System.arraycopy(subBytes, 0, outputByte,
                                             outputSize, subBytes.length);
                            outputSize += subBytes.length;
                            newMode = subBytesMode;
                        } else {
                            outputByte = subBytes;
                            outputSize = subBytes.length;
                        }
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
                // Not part of a surrogate

                // Does this map to the Roman range?
                if (inputChar <= '\u007F') {
                    if (currentMode != ASCII) {
                        outputByte[0] = (byte)0x1b;
                        outputByte[1] = (byte)0x28;
                        outputByte[2] = (byte)0x42;
                        outputByte[3] = (byte)inputChar;
                        outputSize = 4;
                        newMode = ASCII;
                    } else {
                        outputByte[0] = (byte)inputChar;
                        outputSize = 1;
                    }
                }
                // Is it a single byte kana?
                else if (inputChar >= 0xFF61 && inputChar <= 0xFF9F) {
                    if (currentMode != JISX0201_1976_KANA) {
                        outputByte[0] = (byte)0x1b;
                        outputByte[1] = (byte)0x28;
                        outputByte[2] = (byte)0x49;
                        outputByte[3] = (byte)(inputChar - 0xff40);
                        outputSize = 4;
                        newMode = JISX0201_1976_KANA;
                    } else {
                        outputByte[0] = (byte)(inputChar - 0xff40);
                        outputSize = 1;
                    }
                }
                // Is it a yen sign?
                else if (inputChar == '\u00A5') {
                    if (currentMode != JISX0201_1976) {
                        outputByte[0] = (byte)0x1b;
                        outputByte[1] = (byte)0x28;
                        outputByte[2] = (byte)0x4a;
                        outputByte[3] = (byte)0x5c;
                        outputSize = 4;
                        newMode = JISX0201_1976;
                    } else {
                        outputByte[0] = (byte)0x5C;
                        outputSize = 1;
                    }
                }
                // Is it a tilde?
                else if (inputChar == '\u203E')
                    {
                        if (currentMode != JISX0201_1976) {
                            outputByte[0] = (byte)0x1b;
                            outputByte[1] = (byte)0x28;
                            outputByte[2] = (byte)0x4a;
                            outputByte[3] = (byte)0x7e;
                            outputSize = 4;
                            newMode = JISX0201_1976;
                        } else {
                            outputByte[0] = (byte)0x7e;
                            outputSize = 1;
                        }
                    }
                // Is it a JIS-X-0208 character?
                else {
                    int index = getNative(inputChar);
                    if (index != 0) {
                        if (currentMode != JISX0208_1983) {
                            outputByte[0] = (byte)0x1b;
                            outputByte[1] = (byte)0x24;
                            outputByte[2] = (byte)0x42;
                            outputByte[3] = (byte)(index >> 8);
                            outputByte[4] = (byte)(index & 0xff);
                            outputSize = 5;
                            newMode = JISX0208_1983;
                        } else {
                            outputByte[0] = (byte)(index >> 8);
                            outputByte[1] = (byte)(index & 0xff);
                            outputSize = 2;
                        }
                    }
                    // It doesn't map to JIS-0208!
                    else {
                        if (subMode) {
                            if (currentMode != subBytesMode) {
                                System.arraycopy(subBytesEscape, 0, outputByte, 0,
                                                 subBytesEscape.length);
                                outputSize = subBytesEscape.length;
                                System.arraycopy(subBytes, 0, outputByte,
                                                 outputSize, subBytes.length);
                                outputSize += subBytes.length;
                                newMode = subBytesMode;
                            } else {
                                outputByte = subBytes;
                                outputSize = subBytes.length;
                            }
                        } else {
                            badInputLength = 1;
                            throw new UnknownCharacterException();
                        }
                    }
                }
            }

            // Is there room in the output buffer?
            // XXX: The code assumes output buffer can hold at least 5 bytes,
            // in this coverter case. However, there is no way for apps to
            // see how many bytes will be necessary for next call.
            // getMaxBytesPerChar() should be overriden in every subclass of
            // CharToByteConverter and reflect real value (5 for this).
            if (byteOff + outputSize > outEnd)
                throw new ConversionBufferFullException();

            // Put the output into the buffer
            for ( int i = 0 ; i < outputSize ; i++ )
                output[byteOff++] = outputByte[i];

            // Advance the input pointer
            charOff += inputSize;

            // We can successfuly output the characters, changes
            // current mode. Fix for 4251646.
            currentMode = newMode;
        }

        // return mode ASCII at the end
        if (currentMode != ASCII){
            if (byteOff + 3 > outEnd)
                throw new ConversionBufferFullException();

            output[byteOff++] = 0x1b;
            output[byteOff++] = 0x28;
            output[byteOff++] = 0x42;
            currentMode = ASCII;
        }

        // Return the length written to the output buffer
        return byteOff-outOff;
    }

    // Reset
    public void reset() {
        highHalfZoneCode = 0;
        byteOff = charOff = 0;
        currentMode = ASCII;
    }

    /**
     * returns the maximum number of bytes needed to convert a char
     */
    public int getMaxBytesPerChar() {
        return 8;
    }

    // Return the character set ID
    public String getCharacterEncoding() {
        return "ISO2022JP";
    }

}
