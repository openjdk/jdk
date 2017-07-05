/*
 * Copyright (c) 1997, 2000, Oracle and/or its affiliates. All rights reserved.
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
* @author Tom Zhou
*/

public abstract class CharToByteISO2022 extends CharToByteConverter
{
    private final byte ISO_ESC = 0x1b;
    private final byte ISO_SI = 0x0f;
    private final byte ISO_SO = 0x0e;
    private final byte ISO_SS2_7 = 0x4e;
    private final byte ISO_SS3_7 = 0x4f;
    private final byte SS2 = (byte)0x8e;
    private final byte P2 = (byte)0xA2;
    private final byte P3 = (byte)0xA3;
    private final byte MSB = (byte)0x80;

    protected final byte maximumDesignatorLength = 4;

    protected String SODesignator,
                     SS2Designator = null,
                     SS3Designator = null;

    protected CharToByteConverter codeConverter;

    private boolean shiftout = false;
    private boolean SODesDefined = false;
    private boolean SS2DesDefined = false;
    private boolean SS3DesDefined = false;

    private boolean newshiftout = false;
    private boolean newSODesDefined = false;
    private boolean newSS2DesDefined = false;
    private boolean newSS3DesDefined = false;

    public int flush(byte[] output, int outStart, int outEnd)
        throws MalformedInputException
    {
        reset();
        return 0;
    }

    public void reset() {
        shiftout = false;
        SODesDefined = false;
        SS2DesDefined = false;
        SS3DesDefined = false;
        byteOff = charOff = 0;
    }

    public boolean canConvert(char ch)
    {
        if (ch<0x80)
           return true;
        return codeConverter.canConvert(ch);
    }

    private int unicodeToNative(char unicode, byte ebyte[])
    {
        int     index = 0;
        byte    tmpByte[];
        byte    convByte[] = new byte[codeConverter.getMaxBytesPerChar()];
        char    convChar[] = {unicode};
        int     converted;

        try{
            converted = codeConverter.convert(convChar, 0, 1, convByte, 0,
                                        codeConverter.getMaxBytesPerChar());
        } catch(Exception e) {
            return -1;
        }

        if (converted == 2) {
            if (!SODesDefined) {
                newSODesDefined = true;
                ebyte[0] = ISO_ESC;
                tmpByte = SODesignator.getBytes();
                System.arraycopy(tmpByte,0,ebyte,1,tmpByte.length);
                index = tmpByte.length+1;
            }
            if (!shiftout) {
                newshiftout = true;
                ebyte[index++] = ISO_SO;
            }
            ebyte[index++] = (byte)(convByte[0]&0x7f);
            ebyte[index++] = (byte)(convByte[1]&0x7f);
        } else {
            if((convByte[0] == SS2)&&(convByte[1] == P2)) {
                if (!SS2DesDefined) {
                    newSS2DesDefined = true;
                    ebyte[0] = ISO_ESC;
                    tmpByte = SS2Designator.getBytes();
                    System.arraycopy(tmpByte,0,ebyte,1,tmpByte.length);
                    index = tmpByte.length+1;
                }
                ebyte[index++] = ISO_ESC;
                ebyte[index++] = ISO_SS2_7;
                ebyte[index++] = (byte)(convByte[2]&0x7f);
                ebyte[index++] = (byte)(convByte[3]&0x7f);
            }
            if((convByte[0] == SS2)&&(convByte[1] == 0xA3))
            {
                if(!SS3DesDefined){
                    newSS3DesDefined = true;
                    ebyte[0] = ISO_ESC;
                    tmpByte = SS3Designator.getBytes();
                    System.arraycopy(tmpByte,0,ebyte,1,tmpByte.length);
                    index = tmpByte.length+1;
                }
                ebyte[index++] = ISO_ESC;
                ebyte[index++] = ISO_SS3_7;
                ebyte[index++] = (byte)(convByte[2]&0x7f);
                ebyte[index++] = (byte)(convByte[3]&0x7f);
            }
        }
        return index;
    }

    /**
     * Character conversion
     */
    public int convert(char[] input, int inOff, int inEnd,
                       byte[] output, int outOff, int outEnd)
        throws UnknownCharacterException, MalformedInputException,
               ConversionBufferFullException
    {
        int outputSize;
        byte [] tmpbuf = new byte[this.getMaxBytesPerChar()];
        byte [] outputByte;

        byteOff = outOff;

        newshiftout = shiftout;
        newSODesDefined = SODesDefined;
        newSS2DesDefined = SS2DesDefined;
        newSS3DesDefined = SS3DesDefined;

        //Fixed 4122961 by bringing the charOff++ out to this
        // loop where it belongs, changing the loop from
        // while(){} to for(){}.
        for (charOff = inOff; charOff < inEnd; charOff++) {
            outputByte = tmpbuf;
            if (input[charOff] < 0x80) {        // ASCII
                if (shiftout){
                    newshiftout = false;
                    outputSize = 2;
                    outputByte[0] = ISO_SI;
                    outputByte[1] = (byte)(input[charOff] & 0x7f);
                } else {
                    outputSize = 1;
                    outputByte[0] = (byte)(input[charOff] & 0x7f);
                }
                if(input[charOff] == '\n'){
                    newSODesDefined = false;
                    newSS2DesDefined = false;
                    newSS3DesDefined = false;
                }
            } else {
                outputSize = unicodeToNative(input[charOff], outputByte);
            }

            if (outputSize == -1) {
                if (subMode) {
                    if(!newSODesDefined){
                        newSODesDefined = !newSODesDefined;
                        outputByte[0] = ISO_SO;
                        outputByte[1] = (byte)'?';
                        outputSize = 2;
                    } else {
                        outputByte = subBytes;
                        outputSize = subBytes.length;
                    }
                } else {
                    badInputLength = 1;
                    throw new UnknownCharacterException();
                }
            }

            if (outEnd - byteOff < outputSize)
                throw new ConversionBufferFullException();

            for (int i = 0; i < outputSize; i++)
                output[byteOff++] = outputByte[i];

            // Bug 4266772. Update statuses only when output buffer has been
            // updated. When ConversionBufferFullException() has been throwed
            // we want to keep old statuses for next call.
            shiftout = newshiftout;
            SODesDefined = newSODesDefined;
            SS2DesDefined = newSS2DesDefined;
            SS3DesDefined = newSS3DesDefined;
        }
        return byteOff - outOff;
    }
}
