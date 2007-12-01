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

public abstract class ByteToCharDBCS_EBCDIC extends ByteToCharConverter
{

    private static final int SBCS = 0;
    private static final int DBCS = 1;

    private static final int SO = 0x0e;
    private static final int SI = 0x0f;

    private int  currentState;
    private boolean savedBytePresent;
    private byte savedByte;

    protected String singleByteToChar;
    protected short index1[];
    protected String index2;
    protected int   mask1;
    protected int   mask2;
    protected int   shift;


    public ByteToCharDBCS_EBCDIC() {
       super();
       currentState = SBCS;
       savedBytePresent = false;
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
       char outputChar = '\uFFFD';

       charOff = outOff;
       byteOff = inOff;

       while(byteOff < inEnd) {
          int byte1, byte2;
          int v;

          if (!savedBytePresent) {
            byte1 = input[byteOff];
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

                if (byte1 < 0)
                   byte1 += 256;

                if (currentState == SBCS) {
                   outputChar = singleByteToChar.charAt(byte1);
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

                      savedByte = (byte)byte1;
                      savedBytePresent = true;
                      byteOff += inputSize;
                      break;
                   }

                   byte2 = input[byteOff+inputSize];
                   if (byte2 < 0)
                      byte2 += 256;

                   inputSize++;

                   // validate the pair of bytes meet the architecture

                   if ((byte1 != 0x40 || byte2 != 0x40) &&
                      (byte2 < 0x41 || byte2 > 0xfe)) {
                      badInputLength = 2;
                      throw new MalformedInputException();
                   }

                   // Lookup in the two level index
                   v = byte1 * 256 + byte2;
                   outputChar = index2.charAt(index1[((v & mask1) >> shift)] + (v & mask2));
                }

                if (outputChar == '\uFFFD') {
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
