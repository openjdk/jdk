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
 * Last Modified : 23,November,1998
 *
 * Purpose : Defines class ByteToCharISCII91.
 *
 *
 * Revision History
 * ======== =======
 *
 * Date        By            Description
 * ----        --            -----------
 *
 *
 */

/**
 * Converter class. Converts between Unicode encoding and ISCII91 encoding.
 * ISCII91 is the character encoding as defined in Indian Standard document
 * IS 13194:1991 ( Indian Script Code for Information Interchange ).
 *
 * @see sun.io.ByteToCharConverter
 */
public class ByteToCharISCII91 extends ByteToCharConverter {

    private static final char[] directMapTable = ISCII91.getDirectMapTable();

    private static final char NUKTA_CHAR = '\u093c';
    private static final char HALANT_CHAR = '\u094d';
    private static final char ZWNJ_CHAR = '\u200c';
    private static final char ZWJ_CHAR = '\u200d';
    private static final char INVALID_CHAR = '\uffff';

    private char contextChar = INVALID_CHAR;
    private boolean needFlushing = false;

/**
 * Converts ISCII91 characters to Unicode.
 * @see sun.io.ByteToCharConverter#convert
 */
    public int convert(byte input[], int inStart, int inEnd,
                        char output[], int outStart, int outEnd)
    throws ConversionBufferFullException, UnknownCharacterException {
        /*Rules:
         * 1)ATR,EXT,following character to be replaced with '\ufffd'
         * 2)Halant + Halant => '\u094d' (Virama) + '\u200c'(ZWNJ)
         * 3)Halant + Nukta => '\u094d' (Virama) + '\u200d'(ZWJ)
         */
        charOff = outStart;
        byteOff = inStart;
        while (byteOff < inEnd) {
            if (charOff >= outEnd) {
                throw new ConversionBufferFullException();
            }
            int index = input[byteOff++];
            index = ( index < 0 )? ( index + 255 ):index;
            char currentChar = directMapTable[index];

            // if the contextChar is either ATR || EXT set the output to '\ufffd'
            if(contextChar == '\ufffd') {
                output[charOff++] = '\ufffd';
                contextChar = INVALID_CHAR;
                needFlushing = false;
                continue;
            }

            switch(currentChar) {
            case '\u0901':
            case '\u0907':
            case '\u0908':
            case '\u090b':
            case '\u093f':
            case '\u0940':
            case '\u0943':
            case '\u0964':
                if(needFlushing) {
                    output[charOff++] = contextChar;
                    contextChar = currentChar;
                    continue;
                }
                contextChar = currentChar;
                needFlushing = true;
                continue;
            case NUKTA_CHAR:
                switch(contextChar) {
                case '\u0901':
                    output[charOff] = '\u0950';
                    break;
                case '\u0907':
                    output[charOff] = '\u090c';
                    break;
                case '\u0908':
                    output[charOff] = '\u0961';
                    break;
                case '\u090b':
                    output[charOff] = '\u0960';
                    break;
                case '\u093f':
                    output[charOff] = '\u0962';
                    break;
                case '\u0940':
                    output[charOff] = '\u0963';
                    break;
                case '\u0943':
                    output[charOff] = '\u0944';
                    break;
                case '\u0964':
                    output[charOff] = '\u093d';
                    break;
                case HALANT_CHAR:
                    if(needFlushing) {
                        output[charOff++] = contextChar;
                        contextChar = currentChar;
                        continue;
                    }
                    output[charOff] = ZWJ_CHAR;
                    break;
                default:
                    if(needFlushing) {
                        output[charOff++] = contextChar;
                        contextChar = currentChar;
                        continue;
                    }
                    output[charOff] = NUKTA_CHAR;
                }
                break;
            case HALANT_CHAR:
                if(needFlushing) {
                    output[charOff++] = contextChar;
                    contextChar = currentChar;
                    continue;
                }
                if(contextChar == HALANT_CHAR) {
                    output[charOff] = ZWNJ_CHAR;
                    break;
                }
                output[charOff] = HALANT_CHAR;
                break;
            case INVALID_CHAR:
                if(needFlushing) {
                    output[charOff++] = contextChar;
                    contextChar = currentChar;
                    continue;
                }
                if(subMode) {
                    output[charOff] = subChars[0];
                    break;
                } else {
                    contextChar = INVALID_CHAR;
                    throw new UnknownCharacterException();
                }
            default:
                if(needFlushing) {
                    output[charOff++] = contextChar;
                    contextChar = currentChar;
                    continue;
                }
                output[charOff] = currentChar;
                break;
        }//end switch

        contextChar = currentChar;
        needFlushing = false;
        charOff++;
        }//end while
        return charOff - outStart;
    } //convert()

/**
 * @see sun.io.ByteToCharConverter#flush
 */
    public  int flush( char[] output, int outStart, int outEnd )
    throws MalformedInputException, ConversionBufferFullException
    {
        int charsWritten = 0;
        //if the last char was not flushed, flush it!
        if(needFlushing) {
            output[outStart] = contextChar;
            charsWritten = 1;
        }
        contextChar = INVALID_CHAR;
        needFlushing = false;
        byteOff = charOff = 0;
        return charsWritten;
    }//flush()
/**
 * Returns the character set id for the conversion.
 */
    public String getCharacterEncoding()
    {
        return "ISCII91";
    }//getCharacterEncoding()
/**
 * @see sun.io.ByteToCharConverter#reset
 */
    public void reset()
    {
        byteOff = charOff = 0;
    }//reset()

}//end of class definition
