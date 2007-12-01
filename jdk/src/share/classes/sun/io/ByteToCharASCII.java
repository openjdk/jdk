/*
 * Copyright 1997 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * A algorithmic conversion from ASCII to Unicode
 *
 * @author Limin Shi
 */
public class ByteToCharASCII extends ByteToCharConverter {

    // Return the character set id
    public String getCharacterEncoding()
    {
        return "ASCII";
    }

    public int flush(char[] output, int outStart, int outEnd) {
        // This converter will not buffer any data.
        byteOff = charOff = 0;
        return 0;
    }

    /**
     * Algorithmic character conversion
     */
    public int convert(byte[] input, int inOff, int inEnd,
                       char[] output, int outOff, int outEnd)
        throws ConversionBufferFullException, UnknownCharacterException
    {
        byte    inputByte;

        charOff = outOff;
        byteOff = inOff;

        // Loop until we hit the end of the input
        while(byteOff < inEnd)
        {
            // If we don't have room for the output, throw an exception
            if (charOff >= outEnd)
                throw new ConversionBufferFullException();

            // Convert the input byte
            inputByte = input[byteOff++];

            if (inputByte >= 0)
                output[charOff++] = (char)inputByte;
            else {
                if (subMode)
                    output[charOff++] = '\uFFFD';       // Replace Char
                else {
                    badInputLength = 1;
                    throw new UnknownCharacterException();
                }
            }
        }

        // Return the length written to the output buffer
        return charOff-outOff;
    }

    /*
     *   Reset after finding bad input
     */
    public void reset() {
        byteOff = charOff = 0;
    }

}
