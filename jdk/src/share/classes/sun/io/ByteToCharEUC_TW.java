/*
 * Copyright 1996-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

import sun.nio.cs.ext.EUC_TW;

/*
 * @author Limin Shi
 */
public class ByteToCharEUC_TW extends ByteToCharConverter
{
    private final byte G0 = 0;
    private final byte G1 = 1;
    private final byte G2 = 2;
    private final byte G3 = 3;
    private final byte G4 = 4;
    private final byte MSB = (byte) 0x80;
    private final byte SS2 = (byte) 0x8E;
    private final byte P2 = (byte) 0xA2;
    private final byte P3 = (byte) 0xA3;

    protected final char REPLACE_CHAR = '\uFFFD';

    private byte firstByte = 0, state = G0;
    public static String unicodeCNS2, unicodeCNS3;
    private static String unicodeCNS4, unicodeCNS5, unicodeCNS6;
    private static String unicodeCNS7, unicodeCNS15;

    private int cnsPlane = 0;
    private final static EUC_TW nioCoder = new EUC_TW();

    public static String unicodeCNS1 = nioCoder.getUnicodeCNS1();

        static String[] cnsChars = {
            unicodeCNS2 = nioCoder.getUnicodeCNS2(),
            unicodeCNS3 = nioCoder.getUnicodeCNS3(),
            unicodeCNS4 = nioCoder.getUnicodeCNS4(),
            unicodeCNS5 = nioCoder.getUnicodeCNS5(),
            unicodeCNS6 = nioCoder.getUnicodeCNS6(),
            unicodeCNS7 = nioCoder.getUnicodeCNS7(),
            unicodeCNS15 = nioCoder.getUnicodeCNS15()
            };

    public ByteToCharEUC_TW() {
    }

    public int flush(char[] output, int outStart, int outEnd)
        throws MalformedInputException
    {
        if (state != G0) {
            state = G0;
            firstByte = 0;
            badInputLength = 0;
            throw new MalformedInputException();
        }
        reset();
        return 0;
    }

    public void reset() {
        state = G0;
        firstByte = 0;
        byteOff = charOff = 0;
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
        char outputChar = (char) 0;

        byteOff = inOff;
        charOff = outOff;

        cnsPlane = 3;
        while (byteOff < inEnd) {
            if (charOff >= outEnd)
                throw new ConversionBufferFullException();

            switch (state) {
            case G0:
                if ( (input[byteOff] & MSB) == 0) {     // ASCII
                    outputChar = (char) input[byteOff];
                } else if (input[byteOff] == SS2) {     // Codeset 2
                    state = G2;
                } else {                                // Codeset 1
                    firstByte = input[byteOff];
                    state = G1;
                }
                break;
            case G1:
                inputSize = 2;
                if ( (input[byteOff] & MSB) != 0) {     // 2nd byte
                    cnsPlane = 1;
                    outputChar = convToUnicode(firstByte,
                                        input[byteOff], unicodeCNS1);
                } else {                                // Error
                    badInputLength = 1;
                    throw new MalformedInputException();
                }
                firstByte = 0;
                state = G0;
                break;
            case G2:
                cnsPlane = (input[byteOff] & (byte)0x0f);
                // Adjust String array index for plan 15
                cnsPlane = (cnsPlane == 15)? 8 : cnsPlane;

                if (cnsPlane < 15) {
                     state = G3;
                } else {
                    badInputLength = 2;
                    throw new MalformedInputException();
                }

                break;
            case G3:
                if ( (input[byteOff] & MSB) != 0) {     // 1st byte
                    firstByte = input[byteOff];
                    state = G4;
                } else {                                // Error
                    state = G0;
                    badInputLength = 2;
                    throw new MalformedInputException();
                }
                break;
            case G4:
                if ( (input[byteOff] & MSB) != 0) {     // 2nd byte
                        outputChar = convToUnicode(firstByte,
                                                   input[byteOff],
                                                   cnsChars[cnsPlane - 2]);
                } else {                                // Error
                    badInputLength = 3;
                    throw new MalformedInputException();
                }
                firstByte = 0;
                state = G0;
                break;
            }
            byteOff++;

            if (outputChar != (char) 0) {
                if (outputChar == REPLACE_CHAR) {
                    if (subMode)                // substitution enabled
                        outputChar = subChars[0];
                    else {
                        badInputLength = inputSize;
                        throw new UnknownCharacterException();
                    }
                }
                output[charOff++] = outputChar;
                outputChar = 0;
            }
        }

        return charOff - outOff;
    }


    /**
     * Return the character set ID
     */
    public String getCharacterEncoding() {
        return "EUC_TW";
    }

    protected char convToUnicode(byte byte1, byte byte2, String table)
    {
        int index;

        if ((byte1 & 0xff) < 0xa1 || (byte2 & 0xff) < 0xa1 ||
            (byte1 & 0xff) > 0xfe || (byte2 & 0xff) > 0xfe)
            return REPLACE_CHAR;
        index = (((byte1 & 0xff) - 0xa1) * 94)  + (byte2 & 0xff) - 0xa1;
        if (index < 0 || index >= table.length())
            return REPLACE_CHAR;

        // Planes 3 and above containing zero value lead byte
        // to accommodate surrogates for mappings which decode to a surrogate
        // pair

        if (this.cnsPlane >= 3)
           index = (index * 2) + 1;

        return table.charAt(index);
    }
}
