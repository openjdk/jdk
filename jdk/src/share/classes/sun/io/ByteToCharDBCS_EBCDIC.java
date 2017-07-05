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

import sun.nio.cs.ext.DoubleByte;
import static sun.nio.cs.CharsetMapping.*;

public abstract class ByteToCharDBCS_EBCDIC extends ByteToCharConverter
{

    private static final int SBCS = 0;
    private static final int DBCS = 1;

    private static final int SO = 0x0e;
    private static final int SI = 0x0f;

    private int  currentState;
    private boolean savedBytePresent;
    private int savedByte;

    private DoubleByte.Decoder dec;

    public ByteToCharDBCS_EBCDIC(DoubleByte.Decoder dec) {
       super();
       currentState = SBCS;
       savedBytePresent = false;
       this.dec = dec;
    }

    char decodeSingle(int b) {
        return dec.decodeSingle(b);
    }

    char decodeDouble(int b1, int b2) {
        return dec.decodeDouble(b1, b2);
    }

    public int flush(char [] output, int outStart, int outEnd)
       throws MalformedInputException
    {

       if (savedBytePresent) {
           reset();
           badInputLength = 0;
           throw new MalformedInputException();
       }

       reset();
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
       int  inputSize;
       char outputChar = UNMAPPABLE_DECODING;

       charOff = outOff;
       byteOff = inOff;

       while(byteOff < inEnd) {
          int byte1, byte2;

          if (!savedBytePresent) {
            byte1 = input[byteOff] & 0xff;
            inputSize = 1;
          } else {
            byte1 = savedByte;
            savedBytePresent = false;
            inputSize = 0;
          }

          if (byte1 == SO) {

             // For SO characters - simply validate the state and if OK
             //    update the state and go to the next byte

             if (currentState != SBCS) {
                badInputLength = 1;
                throw new MalformedInputException();
             } else {
                currentState = DBCS;
                byteOff += inputSize;
             }
          }

          else
             if (byte1 == SI) {
                // For SI characters - simply validate the state and if OK
                //    update the state and go to the next byte

                if (currentState != DBCS) {
                   badInputLength = 1;
                   throw new MalformedInputException();
                } else {
                   currentState = SBCS;
                   byteOff+= inputSize;
                }
             } else {

                // Process the real data characters

                if (currentState == SBCS) {
                   outputChar = decodeSingle(byte1);
                } else {

                   // for a DBCS character - architecture dictates the
                   // valid range of 1st bytes

                   if (byte1 < 0x40 || byte1 > 0xfe) {
                      badInputLength = 1;
                      throw new MalformedInputException();
                   }

                   if (byteOff + inputSize >= inEnd) {
                      // We have been split in the middle if a character
                      // save the first byte for next time around

                      savedByte = byte1;
                      savedBytePresent = true;
                      byteOff += inputSize;
                      break;
                   }

                   byte2 = input[byteOff+inputSize] & 0xff;
                   inputSize++;

                   // validate the pair of bytes meet the architecture

                   if ((byte1 != 0x40 || byte2 != 0x40) &&
                      (byte2 < 0x41 || byte2 > 0xfe)) {
                      badInputLength = 2;
                      throw new MalformedInputException();
                   }

                   outputChar = decodeDouble(byte1, byte2);
                }

                if (outputChar == UNMAPPABLE_DECODING) {
                   if (subMode)
                      outputChar = subChars[0];
                   else {
                      badInputLength = inputSize;
                      throw new UnknownCharacterException();
                   }
                }

                if (charOff >= outEnd)
                   throw new ConversionBufferFullException();

                output[charOff++] = outputChar;
                byteOff += inputSize;
             }

       }

       return charOff - outOff;
    }


    /**
     *  Resets the converter.
     */
    public void reset() {
       charOff = byteOff = 0;
       currentState = SBCS;
       savedBytePresent = false;
    }
}
