/*
 * Copyright (c) 1997, Oracle and/or its affiliates. All rights reserved.
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

import sun.nio.cs.ext.DoubleByte;
import static sun.nio.cs.CharsetMapping.*;

public abstract class ByteToCharEUC2 extends ByteToCharConverter
{
    private final int G0 = 0;
    private final int G1 = 1;
    private final int SS2 =  0x8E;
    private final int SS3 =  0x8F;

    private int firstByte, state;

    private DoubleByte.Decoder dec;

    public ByteToCharEUC2(DoubleByte.Decoder dec) {
        super();
        state = G0;
        this.dec = dec;
    }

    char decodeSingle(int b) {
        return dec.decodeSingle(b);
    }

    char decodeDouble(int b1, int b2) {
        return dec.decodeDouble(b1, b2);
    }

    /**
      * flush out any residual data and reset the buffer state
      */
    public int flush(char[] output, int outStart, int outEnd)
       throws MalformedInputException
    {
       if (state != G0) {
          reset();
          badInputLength = 0;
          throw new MalformedInputException();
       }

       reset();
       return 0;
    }

    /**
     *  Resets the converter.
     */
    public void reset() {
       state = G0;
       charOff = byteOff = 0;
    }

    /**
     * Character conversion
     */
    public int convert(byte[] input, int inOff, int inEnd,
                       char[] output, int outOff, int outEnd)
        throws UnknownCharacterException, MalformedInputException,
               ConversionBufferFullException
    {
        int       byte1;
        char      outputChar = UNMAPPABLE_DECODING;
        byteOff = inOff;
        charOff = outOff;

        while (byteOff < inEnd) {
            byte1 = input[byteOff] & 0xff;
            switch (state) {
            case G0:
                if (byte1 == SS2 ||                // no general support
                    byte1 == SS3 ) {               //    for g2 or g3
                    badInputLength = 1;
                    throw new MalformedInputException();
                }
                if ( byte1 <= 0x9f )               // < 0x9f has its own table
                    outputChar = decodeSingle(byte1);
                else
                    if (byte1 < 0xa1 || byte1 > 0xfe) {  // byte within range?
                        badInputLength = 1;
                        throw new MalformedInputException();
                    } else {                       // G1 set first byte
                        firstByte = byte1;
                        state = G1;
                    }
                break;
            case G1:
                state = G0;
                if ( byte1 < 0xa1 || byte1 > 0xfe) {  // valid G1 set second byte
                    badInputLength = 1;
                    throw new MalformedInputException();
                }
                outputChar = decodeDouble(firstByte, byte1);
                break;
            }
            if (state == G0) {
                if (outputChar == UNMAPPABLE_DECODING) {
                    if (subMode)
                        outputChar = subChars[0];
                    else {
                        badInputLength = 1;
                        throw new UnknownCharacterException();
                    }
                }
                if (charOff >= outEnd)
                    throw new ConversionBufferFullException();
                output[charOff++] = outputChar;
            }
            byteOff++;
         }
         return charOff - outOff;
    }
}
