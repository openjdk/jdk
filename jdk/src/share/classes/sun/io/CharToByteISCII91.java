/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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

import sun.nio.cs.ext.ISCII91;

/*
 * Copyright (c) 1998 International Business Machines.
 * All Rights Reserved.
 *
 * Author : Sunanda Bera, C. Thirumalesh
 * Last Modified : 11,December,1998
 *
 * Purpose : Defines class CharToByteISCII91.
 *
 *
 * Revision History
 * ======== =======
 *
 * Date        By            Description
 * ----        --            -----------
 * March 29, 1999 John Raley Removed MalformedInputException; modified substitution logic
 *
 */

/**
 * Converter class. Converts between ISCII91 encoding and Unicode encoding.
 * ISCII91 is the character encoding as defined in Indian Standard document
 * IS 13194:1991 ( Indian Script Code for Information Interchange ).
 *
 * @see sun.io.CharToByteConverter
 */

/*
 * {jbr} I am not sure this class adheres to code converter conventions.
 * Need to investigate.
 * Might should recode as a subclass of CharToByteSingleByte.
 */

public class CharToByteISCII91 extends CharToByteConverter {

        private static final byte NO_CHAR = (byte)255;

        //private final static ISCII91 nioCoder = new ISCII91();
        private final static byte[] directMapTable = ISCII91.getEncoderMappingTable();

        private static final char NUKTA_CHAR = '\u093c';
        private static final char HALANT_CHAR = '\u094d';


/**
 * @return true for Devanagari and ASCII range and for the special characters
 *              Zero Width Joiner and Zero Width Non-Joiner
 * @see sun.io.CharToByteConverter#canConvert
 *
 */
        public boolean canConvert(char ch) {
        //check for Devanagari range,ZWJ,ZWNJ and ASCII range.
        return ((ch >= 0x0900 && ch <= 0x097f) || (ch == 0x200d || ch == 0x200c)
                                || (ch >= 0x0000 && ch <= 0x007f) );
        } //canConvert()
/**
 * Converts both Devanagari and ASCII range of characters.
 * @see sun.io.CharToByteConverter#convert
 */
    public int convert(char[] input, int inStart, int inEnd, byte[] output, int outStart, int outEnd) throws MalformedInputException, UnknownCharacterException, ConversionBufferFullException {

        charOff = inStart;
        byteOff = outStart;

        for (;charOff < inEnd; charOff++) {

            char inputChar = input[charOff];
            int index = Integer.MIN_VALUE;
            boolean isSurrogatePair = false;

            //check if input is in ASCII RANGE
            if (inputChar >= 0x0000 && inputChar <= 0x007f) {
                if (byteOff >= outEnd) {
                        throw new ConversionBufferFullException();
                }
                output[byteOff++] = (byte) inputChar;
                continue;
            }

            // if inputChar == ZWJ replace it with halant
            // if inputChar == ZWNJ replace it with Nukta
            if (inputChar == 0x200c) {
                inputChar = HALANT_CHAR;
            }
            else if (inputChar == 0x200d) {
                inputChar = NUKTA_CHAR;
            }

            if (inputChar >= 0x0900 && inputChar <= 0x097f) {
                index = ((int)(inputChar) - 0x0900)*2;
            }

            // If input char is a high surrogate, ensure that the following
            // char is a low surrogate.  If not, throw a MalformedInputException.
            // Leave index untouched so substitution or an UnknownCharacterException
            // will result.
            else if (inputChar >= 0xd800 && inputChar <= 0xdbff) {
                if (charOff < inEnd-1) {
                    char nextChar = input[charOff];
                    if (nextChar >= 0xdc00 && nextChar <= 0xdfff) {
                        charOff++;
                        isSurrogatePair = true;
                    }
                }
                if (!isSurrogatePair) {
                    badInputLength = 1;
                    throw new MalformedInputException();
                }
            }
            else if (inputChar >= 0xdc00 && inputChar <= 0xdfff) {
                badInputLength = 1;
                throw new MalformedInputException();
            }

            if (index == Integer.MIN_VALUE || directMapTable[index] == NO_CHAR) {
                if (subMode) {
                    if (byteOff + subBytes.length >= outEnd) {
                            throw new ConversionBufferFullException();
                    }
                    System.arraycopy(subBytes, 0, output, byteOff, subBytes.length);
                    byteOff += subBytes.length;
                } else {
                    badInputLength = isSurrogatePair? 2 : 1;
                    throw new UnknownCharacterException();
                }
            }
            else {
                if(byteOff >= outEnd) {
                    throw new ConversionBufferFullException();
                }
                output[byteOff++] = directMapTable[index++];
                if(directMapTable[index] != NO_CHAR) {
                    if(byteOff >= outEnd) {
                            throw new ConversionBufferFullException();
                    }
                    output[byteOff++] = directMapTable[index];
                }
            }

        } //end for

        return byteOff - outStart;
    } //end of routine convert.

/**
* @see sun.io.CharToByteConverter#flush
*/
        public int flush( byte[] output, int outStart, int outEnd )
        throws MalformedInputException, ConversionBufferFullException {
        byteOff = charOff = 0;
        return 0;
        }//flush()
/**
 * @return The character encoding as a String.
 */
        public String getCharacterEncoding() {
        return "ISCII91";
        }//getCharacterEncoding
/**
 * @see sun.io.CharToByteConverter#getMaxBytesPerChar
 */
        public int getMaxBytesPerChar() {
        return 2;
        }//getMaxBytesPerChar()
/**
 * @see sun.io.CharToByteConverter#reset
 */
        public void reset() {
        byteOff = charOff = 0;
        }
} //end of class definition
