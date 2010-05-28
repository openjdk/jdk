/*
 * Copyright (c) 1996, 2002, Oracle and/or its affiliates. All rights reserved.
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
 * A table driven conversion from byte to char for single byte  character sets.
 * The needed data tables will reside in a character set specific subclass.
 *
 * @author Lloyd Honomichl
 * @author Asmus Freytag
 */
public abstract class ByteToCharSingleByte extends ByteToCharConverter {

    /**
     * Mapping table. Values supplied by subclass
     */
    protected String byteToCharTable;

    public String getByteToCharTable() {
        return byteToCharTable;
    }

    public int flush(char[] output, int outStart, int outEnd) {
        byteOff = charOff = 0;
        return 0;
    }

    /**
     * Converts bytes to characters according to the selected character
     * encoding.
     * Maintains internal state, so that conversions that result in
     * exceptions can be restarted by calling convert again, with
     * appropriately modified parameters.
     * Call reset before converting input that is not a continuation of
     * the previous call.
     * @return the number of characters written to output.
     * @param input byte array containing text in character set
     * @param inStart offset in input array
     * @param inEnd offset of last byte to be converted
     * @param output character array to receive conversion result
     * @param outStart starting offset
     * @param outEnd offset of last character to be written to
     * @throw MalformedInputException for any sequence of bytes that is
     * illegal for the input character set, including any partial multi-byte
     * sequence which occurs at the end of an input buffer.
     * @throw UnsupportedCharacterException for any sequence of bytes that
     * contain a character not supported in the current conversion.
     * @throw BufferFullException whenever the output buffer is full
     * before the input is exhausted.
     * @see #reset
     */
    public int convert(byte[] input, int inOff, int inEnd,
                       char[] output, int outOff, int outEnd)
        throws UnknownCharacterException,
               MalformedInputException,
               ConversionBufferFullException
    {
        char    outputChar;
        int     byteIndex;

        charOff = outOff;
        byteOff = inOff;

        // Loop until we hit the end of the input
        while(byteOff < inEnd) {

            byteIndex = input[byteOff];

            /* old source
             *outputChar = byteToCharTable[input[byteOff] + 128];
             */
            // Lookup the output character
            outputChar = getUnicode(byteIndex);

            // Is the output unmappable?
            if (outputChar == '\uFFFD') {
                if (subMode) {
                    outputChar = subChars[0];
                } else {
                    badInputLength = 1;
                    throw new UnknownCharacterException();
                }
            }

            // If we don't have room for the output, throw an exception
            if (charOff >= outEnd)
                throw new ConversionBufferFullException();

            // Put the character in the output buffer
            output[charOff]= outputChar;
            charOff++;
            byteOff++;
        }

        // Return the length written to the output buffer
        return charOff-outOff;
    }

    protected char getUnicode(int byteIndex) {
        int n = byteIndex + 128;
        if (n >= byteToCharTable.length() || n < 0)
            return '\uFFFD';
        return byteToCharTable.charAt(n);
    }

    /**
     *  Resets the converter.
     *  Call this method to reset the converter to its initial state
     */
    public void reset() {
        byteOff = charOff = 0;
    }

}
