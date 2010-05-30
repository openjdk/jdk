/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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

/*
 */


package sun.io;
import sun.nio.cs.ext.GB18030;

public class CharToByteGB18030 extends CharToByteConverter
{

    private char highHalfZoneCode;
    boolean flushed = true;

    private final static int GB18030_SINGLE_BYTE = 1;
    private final static int GB18030_DOUBLE_BYTE = 2;
    private final static int GB18030_FOUR_BYTE = 3;
    private static short[] index1;
    private static String[] index2;
    private int currentState;

    public CharToByteGB18030() {
        GB18030 nioCoder = new GB18030();
        currentState = GB18030_DOUBLE_BYTE;
        subBytes = new byte[1];
        subBytes[0] = (byte)'?';
        index1 = nioCoder.getEncoderIndex1();
        index2 = nioCoder.getEncoderIndex2();
    }

    public int flush(byte[] output, int outStart, int outEnd)
        throws MalformedInputException
    {
        if (highHalfZoneCode != 0) {
            highHalfZoneCode = 0;
            badInputLength = 0;
            throw new MalformedInputException();
        }
        reset();
        flushed = true;
        return 0;
    }

    public void reset() {
        byteOff = charOff = 0;
        currentState = GB18030_DOUBLE_BYTE;
    }

    public boolean canConvert(char c) {
        // converts all but unpaired surrogates
        // and illegal chars, U+FFFE & U+FFFF

        if ((c >= 0xd800 && c <=0xdfff) || (c >= 0xfffe))
            return false;
        else
            return true;
    }

    /**
     * Character conversion
     */
    public int convert(char[] input, int inOff, int inEnd,
                       byte[] output, int outOff, int outEnd)
        throws UnknownCharacterException, MalformedInputException,
               ConversionBufferFullException
    {
        int linearDiffValue = 0;
        int hiByte = 0 , loByte = 0;  // low and high order bytes
        char inputChar;  // Input character to be converted
        charOff = inOff;
        byteOff = outOff;
        int inputSize;  // Size of the input
        int outputSize; // Size of the output

        flushed = false;

        if (highHalfZoneCode != 0) {
            if (input[inOff] >= 0xDC00 && input[inOff] <= 0xDFFF) {

                // This is legal UTF16 sequence, so shunt in the high
                // surrogate for conversion by convert() loop.

                char[] newBuf = new char[inEnd - inOff + 1];
                newBuf[0] = highHalfZoneCode;
                System.arraycopy(input, inOff, newBuf, 1, inEnd - inOff);
                charOff -= 1;
                input = newBuf;
                inOff = 0;
                inEnd = newBuf.length;
                highHalfZoneCode = 0;
            } else {
                // This is illegal UTF16 sequence.
                badInputLength = 0;
                throw new MalformedInputException();
            }
        }

        // Main encode loop

        while (charOff < inEnd) {
            inputChar = input[charOff++];

            if(inputChar >= '\uD800' && inputChar <= '\uDBFF') {
                // Is this the last character of the input?
                if (charOff + 1 > inEnd) {
                    highHalfZoneCode = inputChar;
                    break;
                }

                char previousChar = inputChar;
                inputChar = input[charOff];

                // Is there a low surrogate following?
                if (inputChar >= '\uDC00' && inputChar <= '\uDFFF') {
                    inputSize = 2;
                    charOff++;
                    linearDiffValue = ( previousChar - 0xD800) * 0x400 +
                                ( inputChar - 0xDC00) + 0x2E248;

                    currentState = GB18030_FOUR_BYTE;
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

            // Not part of a surrogate
            else if (inputChar >= 0x0000 && inputChar <= 0x007F) {
                if (byteOff >= outEnd) {
                   throw new ConversionBufferFullException();
                }
                currentState = GB18030_SINGLE_BYTE;
                output[byteOff++] = (byte) inputChar;
            }
            else if (inputChar <= 0xA4C6 || inputChar >= 0xE000) {
                int outByteVal = getGB18030(index1, index2, inputChar);

                if (outByteVal == 0xFFFD ) {
                    if (subMode) {
                        if (byteOff >= outEnd) {
                           throw new ConversionBufferFullException();
                        } else {
                            output[byteOff++] = subBytes[0];
                            continue;
                        }
                    } else {
                        badInputLength = 1;
                        throw new UnknownCharacterException();
                    }
                }

                hiByte = (outByteVal & 0xFF00) >> 8;
                loByte = (outByteVal & 0xFF);

                linearDiffValue = (hiByte - 0x20) * 256 + loByte;

                if (inputChar >= 0xE000 && inputChar < 0xF900)
                        linearDiffValue += 0x82BD;
                else if (inputChar >= 0xF900)
                        linearDiffValue += 0x93A9;

                if (hiByte > 0x80)
                     currentState = GB18030_DOUBLE_BYTE;
                else
                     currentState = GB18030_FOUR_BYTE;
            }
            else if (inputChar >= 0xA4C7 && inputChar <= 0xD7FF) {
                linearDiffValue = inputChar - 0x5543;
                currentState = GB18030_FOUR_BYTE;
            }
            else {
                badInputLength = 1;
                throw new MalformedInputException();
            }

            if (currentState == GB18030_SINGLE_BYTE)
                continue;

            if (currentState == GB18030_DOUBLE_BYTE) {
                if (byteOff + 2 > outEnd) {
                    throw new ConversionBufferFullException();
                }
                output[byteOff++] = (byte)hiByte;
                output[byteOff++] = (byte)loByte;
            }
            else { // Four Byte encoding
                if (byteOff + 4 > outEnd) {
                    throw new ConversionBufferFullException();
                }

                byte b1, b2, b3, b4;

                b4 = (byte)((linearDiffValue % 10) + 0x30);
                linearDiffValue /= 10;
                b3 = (byte)((linearDiffValue % 126) + 0x81);
                linearDiffValue /= 126;
                b2 = (byte)((linearDiffValue % 10) + 0x30);
                b1 = (byte)((linearDiffValue / 10) + 0x81);
                output[byteOff++] = b1;
                output[byteOff++] = b2;
                output[byteOff++] = b3;
                output[byteOff++] = b4;
            }
        }
        // Return number of bytes written to the output buffer.
        return byteOff - outOff;
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
        return "GB18030";
    }

    private int getGB18030(short[] outerIndex, String[] innerIndex, char ch) {
        int offset = outerIndex[((ch & 0xff00) >> 8 )] << 8;

        return innerIndex[offset >> 12].charAt((offset & 0xfff) + (ch & 0xff));
    }

}
