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

public abstract class ByteToCharDBCS_ASCII extends ByteToCharConverter
{
    private boolean savedBytePresent;
    private byte    savedByte;

    protected String  singleByteToChar;
    protected boolean leadByte[];
    protected short   index1[];
    protected String  index2;
    protected int     mask1;
    protected int     mask2;
    protected int     shift;


    public ByteToCharDBCS_ASCII() {
        super();
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
        int inputSize;
        char    outputChar = '\uFFFD';

        charOff = outOff;
        byteOff = inOff;

        while(byteOff < inEnd)
        {
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

           if (byte1 < 0)
              byte1 += 256;

           if (!leadByte[byte1])
           {
              outputChar = singleByteToChar.charAt(byte1);
           } else {

              if (byteOff + inputSize >= inEnd) {
                savedByte = (byte)byte1;
                savedBytePresent = true;
                byteOff += inputSize;
                break;
              }

              byte2 = input[byteOff+inputSize];
              if (byte2 < 0)
                byte2 += 256;

              inputSize++;

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

        return charOff - outOff;
    }

    /**
     *  Resets the converter.
     */
    public void reset() {
       charOff = byteOff = 0;
       savedBytePresent = false;
    }
}
