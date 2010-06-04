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

public class ByteToCharGB18030 extends ByteToCharGB18030DB {

    private static final int GB18030_SINGLE_BYTE = 1;
    private static final int GB18030_DOUBLE_BYTE = 2;
    private static final int GB18030_FOUR_BYTE = 3;
    private static short[] decoderIndex1;
    private static String[] decoderIndex2;

    private int currentState;
    private int savedSize;
    private byte[] savedBytes;

    public ByteToCharGB18030() {
        super();
        GB18030 nioCoder = new GB18030();
        savedBytes = new byte[3];
        currentState = GB18030_DOUBLE_BYTE;
        decoderIndex1 = nioCoder.getDecoderIndex1();
        decoderIndex2 = nioCoder.getDecoderIndex2();
        savedSize = 0;
    }

    public short[] getOuter() {
        return(index1);
    }

    public String[] getInner() {
        return(index2);
    }

    public short[] getDBIndex1() {
        return(super.index1);
    }

    public String[] getDBIndex2() {
        return(super.index2);
    }

    public int flush(char [] output, int outStart, int outEnd)
        throws MalformedInputException
    {
        if (savedSize != 0) {
            savedSize = 0;
            currentState = GB18030_DOUBLE_BYTE;
            badInputLength = 0;
            throw new MalformedInputException();
        }
        byteOff = charOff = 0;
        return 0;
    }


    /**
     * Character conversion
     */
    public int convert(byte[] input, int inOff, int inEnd,
                       char[] output, int outOff, int outEnd)
        throws UnknownCharacterException, MalformedInputException,
               ConversionBufferFullException
    {
        int inputSize = 0;
        char outputChar = '\uFFFD';
        // readOff keeps the actual buffer's pointer.
        // byteOff keeps original buffer's pointer.
        int readOff = byteOff = inOff;

        if (savedSize != 0) {
            // Filter illegal bytes when they are detected in saved
            // partial input from a previous conversion attempt.
            if (((savedBytes[0] & 0xFF) < 0x81 || savedBytes[0] > 0xFE) ||
                 (savedSize > 1 &&
                 (savedBytes[1] & 0xFF) < 0x30 ) ||
                 (savedSize > 2 &&
                 ((savedBytes[2] & 0xFF) < 0x81 ||
                 (savedBytes[2] & 0xFF) > 0xFE ))) {
                    badInputLength = 0;
                    throw new MalformedInputException();
            }

            byte[] newBuf = new byte[inEnd - inOff + savedSize];
            for (int i = 0; i < savedSize; i++) {
                newBuf[i] = savedBytes[i];
            }
            System.arraycopy(input, inOff, newBuf, savedSize, inEnd - inOff);
            byteOff -= savedSize;
            input = newBuf;
            inOff = 0;
            inEnd = newBuf.length;
            savedSize = 0;
        }

        charOff = outOff;
        readOff = inOff;

        while(readOff < inEnd) {
            int byte1 = 0 , byte2 = 0, byte3 = 0, byte4 = 0;

            // Is there room in the output buffer for the result?
            if (charOff >= outEnd) {
                throw new ConversionBufferFullException();
            }

            // Get the input byte
            byte1 = input[readOff++] & 0xFF;
            inputSize = 1;

            if ((byte1 & (byte)0x80) == 0){ // US-ASCII range
                outputChar = (char)byte1;
                currentState = GB18030_SINGLE_BYTE;
            }

            else if (byte1 < 0x81 || byte1 > 0xfe) {
                if (subMode)
                    outputChar = subChars[0];
                else {
                    badInputLength = 1;
                    throw new UnknownCharacterException();
                }
            }
            else {
                // Either 2 or 4 byte sequence follows
                // If an underrun is detected save for later
                // replay.

                if (readOff + inputSize > inEnd) {
                    savedBytes[0]=(byte)byte1;
                    savedSize = 1;
                    break;
                }

                byte2 = input[readOff++] & 0xFF;
                inputSize = 2;

                if (byte2 < 0x30) {
                    badInputLength = 1;
                    throw new MalformedInputException();
                }
                else if (byte2 >= 0x30 && byte2 <= 0x39) {
                    currentState = GB18030_FOUR_BYTE;
                    inputSize = 4;

                    if (readOff + 2 > inEnd) {
                        if (readOff + 1 > inEnd) {
                            savedBytes[0] = (byte)byte1;
                            savedBytes[1] = (byte)byte2;
                            savedSize = 2;
                        }
                        else {
                            savedBytes[0] = (byte)byte1;
                            savedBytes[1] = (byte)byte2;
                            savedBytes[2] = input[readOff++];
                            savedSize = 3;
                        }
                        break;
                    }
                    byte3 = input[readOff++] & 0xFF;
                    if (byte3 < 0x81 || byte3 > 0xfe) {
                        badInputLength = 3;
                        throw new MalformedInputException();
                    }

                    byte4 = input[readOff++] & 0xFF;

                    if (byte4 < 0x30 || byte4 > 0x39) {
                        badInputLength = 4;
                        throw new MalformedInputException();
                    }
                }
                else if (byte2 == 0x7f || byte2 == 0xff ||
                        (byte2 < 0x40 )) {
                   badInputLength = 2;
                   throw new MalformedInputException();
                }
                else
                    currentState = GB18030_DOUBLE_BYTE;
            }

            switch (currentState){
                case GB18030_SINGLE_BYTE:
                    output[charOff++] = (char)(byte1);
                    break;
                case GB18030_DOUBLE_BYTE:
                    output[charOff++] = super.getUnicode(byte1, byte2);
                    break;
                case GB18030_FOUR_BYTE:
                    int offset = (((byte1 - 0x81) * 10 +
                                   (byte2 - 0x30)) * 126 +
                                    byte3 - 0x81) * 10 + byte4 - 0x30;
                    int hiByte = (offset >>8) & 0xFF;
                    int lowByte = (offset & 0xFF);

                // Mixture of table lookups and algorithmic calculation
                // of character values.

                // BMP Ranges

                if (offset <= 0x4A62)
                    output[charOff++] = getChar(offset);
                else if (offset > 0x4A62 && offset <= 0x82BC)
                    output[charOff++] = (char) (offset + 0x5543);
                else if (offset >= 0x82BD && offset <= 0x830D)
                    output[charOff++] = getChar(offset);
                else if (offset >= 0x830D && offset <= 0x93A8)
                    output[charOff++] = (char) (offset + 0x6557);
                else if (offset >= 0x93A9 && offset <= 0x99FB)
                    output[charOff++] = getChar(offset);
                // Supplemental UCS planes handled via surrogates
                else if (offset >= 0x2E248 && offset < 0x12E248) {
                    if (offset >= 0x12E248) {
                        if (subMode)
                           return subChars[0];
                        else {
                           badInputLength = 4;
                           throw new UnknownCharacterException();
                        }
                    }

                    if (charOff +2 > outEnd) {
                        throw new ConversionBufferFullException();
                    }
                    offset -= 0x1e248;
                    char highSurr = (char) ((offset - 0x10000) / 0x400 + 0xD800);
                    char lowSurr = (char) ((offset - 0x10000) % 0x400 + 0xDC00);
                    output[charOff++] = highSurr;
                    output[charOff++] = lowSurr;
                }
                else {
                    badInputLength = 4;
                    throw new MalformedInputException();
                    }
                break;
              }
        byteOff += inputSize;
        }

        byteOff += savedSize;
        return charOff - outOff;
    }

    public void reset() {
        byteOff = charOff = 0;
        currentState = GB18030_DOUBLE_BYTE;
        savedSize = 0;
    }

    public String getCharacterEncoding() {
        return "GB18030";
    }

    private char getChar(int offset) throws UnknownCharacterException {
        int byte1 = (offset >>8) & 0xFF;
        int byte2 = (offset & 0xFF);
        int start = 0, end = 0xFF;

        if (((byte1 < 0) || (byte1 > getOuter().length))
             || ((byte2 < start) || (byte2 > end))) {
                if (subMode)
                   return subChars[0];
                else {
                   badInputLength = 1;
                   throw new UnknownCharacterException();
                }
        }

        int n = (decoderIndex1[byte1] & 0xf) * (end - start + 1) + (byte2 - start);
        return decoderIndex2[decoderIndex1[byte1] >> 4].charAt(n);
    }
}
