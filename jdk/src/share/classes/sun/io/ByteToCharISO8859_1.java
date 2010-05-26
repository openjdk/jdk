/*
 * Copyright (c) 1996, 1997, Oracle and/or its affiliates. All rights reserved.
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
* A algorithmic conversion from ISO 8859-1 to Unicode
*
* @author Lloyd Honomichl
* @author Asmus Freytag
*/
public class ByteToCharISO8859_1 extends ByteToCharConverter {

    // Return the character set id
    public String getCharacterEncoding()
    {
        return "ISO8859_1";
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

        throws ConversionBufferFullException
    {
        int bound = inOff + (outEnd - outOff);
        if (bound >= inEnd) {
             bound = inEnd;
        }
        int bytesWritten = inEnd - inOff;


        // Loop until we hit the end of the input
        try {
            while(inOff < bound) {
                output[outOff++] = (char) (0xff & input[inOff++]);
            }
        } finally {
            charOff = outOff;
            byteOff = inOff;
        }

        // If we don't have room for the output, throw an exception
        if (bound < inEnd)
            throw new ConversionBufferFullException();

        // Return the length written to the output buffer
        return bytesWritten;
    }

    /*
        Reset after finding bad input
    */
    public void reset() {
        byteOff = charOff = 0;
    }

}
