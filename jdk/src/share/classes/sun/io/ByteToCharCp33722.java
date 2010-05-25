/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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

import sun.nio.cs.ext.IBM33722;

/**
* @author Malcolm Ayres
*/
public class ByteToCharCp33722 extends ByteToCharConverter
{
    private final int G0 = 0;
    private final int G1 = 1;
    private final int G2 = 2;
    private final int G3 = 3;
    private final int G4 = 4;
    private final int SS2 =  0x8E;
    private final int SS3 =  0x8F;

    private int firstByte, state;
    private String byteToCharTable;
    private String mappingTableG1;
    private String mappingTableG2;
    private String mappingTableG3;

    private final static IBM33722 nioCoder = new IBM33722();

    public ByteToCharCp33722() {
        super();
        state = G0;
        byteToCharTable = nioCoder.getDecoderSingleByteMappings();
        mappingTableG1 = nioCoder.getDecoderMappingTableG1();
        mappingTableG2 = nioCoder.getDecoderMappingTableG2();
        mappingTableG3 = nioCoder.getDecoderMappingTableG3();
    }

    /**
      * Return the character set id
      */
    public String getCharacterEncoding()
    {
       return "Cp33722";
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
       char      outputChar = '\uFFFD';

       byteOff = inOff;
       charOff = outOff;

       while (byteOff < inEnd) {

          byte1 = input[byteOff];
          if (byte1 < 0)
            byte1 += 256;

          switch (state) {
             case G0:
                if (byte1 == SS2)                       // drop into G2 set
                   state = G2;
                else if (byte1 == SS3)                  // drop into G3 set
                   state = G3;
                else if ( byte1 <= 0x9f )               // valid single byte
                   outputChar = byteToCharTable.charAt(byte1);
                else if (byte1 < 0xa1 || byte1 > 0xfe) {  // valid G1 set 1st byte
                   badInputLength = 1;
                   throw new MalformedInputException();
                } else {
                   firstByte = byte1;                   // save the 1st byte
                   state = G1;
                }
                break;

             case G1:
                state = G0;
                if ( byte1 < 0xa1 || byte1 > 0xfe) {    // valid second byte for G1
                   badInputLength = 1;
                   throw new MalformedInputException();
                }
                outputChar = mappingTableG1.charAt(((firstByte - 0xa1) * 94)  + byte1 - 0xa1);
                break;

             case G2:
                state = G0;                             // valid first byte for G2
                if ( byte1 < 0xa1 || byte1 > 0xfe) {
                   badInputLength = 1;
                   throw new MalformedInputException();
                }
                outputChar = mappingTableG2.charAt(byte1 - 0xa1);
                break;

             case G3:
                if ( byte1 < 0xa1 || byte1 > 0xfe) {    // valid first byte for G3
                   state = G0;
                   badInputLength = 1;
                   throw new MalformedInputException();
                }
                firstByte = byte1;
                state = G4;
                break;

             case G4:
                state = G0;                             // valid second byte for G3
                if ( byte1 < 0xa1 || byte1 > 0xfe) {
                   badInputLength = 1;
                   throw new MalformedInputException();
                }
                outputChar = mappingTableG3.charAt(((firstByte - 0xa1) * 94)  + byte1 - 0xa1);
                break;

          }

          if (state == G0) {
             if (outputChar == '\uFFFD') {
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
