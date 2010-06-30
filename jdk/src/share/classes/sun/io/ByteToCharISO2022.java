/*
 * Copyright (c) 1997, 2001, Oracle and/or its affiliates. All rights reserved.
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
 * An algorithmic conversion from ISO 2022 to Unicode
 *
 * @author Tom Zhou
 */
public abstract class ByteToCharISO2022 extends ByteToCharConverter
{
    // Value to be filled by subclass
    protected String SODesignator[];
    protected String SS2Designator[] = null;
    protected String SS3Designator[] = null;

    protected ByteToCharConverter SOConverter[];
    protected ByteToCharConverter SS2Converter[] = null;
    protected ByteToCharConverter SS3Converter[] = null;

    private static final byte ISO_ESC = 0x1b;
    private static final byte ISO_SI = 0x0f;
    private static final byte ISO_SO = 0x0e;
    private static final byte ISO_SS2_7 = 0x4e;
    private static final byte ISO_SS3_7 = 0x4f;
    private static final byte MSB = (byte)0x80;
    private static final char REPLACE_CHAR = '\uFFFD';
    private static final byte maximumDesignatorLength = 3;

    private static final byte SOFlag = 0;
    private static final byte SS2Flag = 1;
    private static final byte SS3Flag = 2;
    private static final byte G0 = 0;
    private static final byte G1 = 1;

    private ByteToCharConverter tmpConverter[];

    private int curSODes, curSS2Des, curSS3Des;
    private boolean shiftout;

    private byte remainByte[] = new byte[10];
    private int remainIndex = -1;
    private byte state, firstByte;

    public void reset()
    {
        int i = 0;

        shiftout = false;
        state = G0;
        firstByte = 0;

        curSODes = 0;
        curSS2Des = 0;
        curSS3Des = 0;

        charOff = byteOff = 0;
        remainIndex = -1;

        for(i = 0; i < remainByte.length; i++)
            remainByte[i] = 0;
    }

    public int flush(char[] output, int outStart, int outEnd)
        throws MalformedInputException
    {
        int i;
        if (state != G0) {
            badInputLength = 0;
            throw new MalformedInputException();
        }
        reset();
        return 0;
    }

    private byte[] savetyGetSrc(byte[] input, int inOff, int inEnd, int nbytes)
    {
        int i;
        byte tmp[];

        if(inOff <= (inEnd-nbytes+1))
            tmp = new byte[nbytes];
        else
            tmp = new byte[inEnd-inOff];

        for(i = 0; i < tmp.length; i++)
            tmp[i] = input[inOff+i];
        return tmp;
    }

    private char getUnicode(byte byte1, byte byte2, byte shiftFlag)
    {
        byte1 |= MSB;
        byte2 |= MSB;

        byte[] tmpByte = {byte1,byte2};
        char[] tmpChar = new char[1];
        int     i = 0,
                tmpIndex = 0;

        switch(shiftFlag) {
        case SOFlag:
            tmpIndex = curSODes;
            tmpConverter = SOConverter;
            break;
        case SS2Flag:
            tmpIndex = curSS2Des;
            tmpConverter = SS2Converter;
            break;
        case SS3Flag:
            tmpIndex = curSS3Des;
            tmpConverter = SS3Converter;
            break;
        }

        for(i = 0; i < tmpConverter.length; i++) {
            if(tmpIndex == i) {
                try {
                    tmpConverter[i].convert(tmpByte, 0, 2, tmpChar, 0, 1);
                } catch (Exception e) {}
                return tmpChar[0];
            }
        }
        return REPLACE_CHAR;
    }

    public final int convert(byte[] input, int inOff, int inEnd,
                             char[] output, int outOff, int outEnd)
                             throws ConversionBufferFullException,
                                    MalformedInputException
    {
        int i;
        int DesignatorLength = 0;
        charOff  =  outOff;
        byteOff  =  inOff;

        // Loop until we hit the end of the input
        while (byteOff < inEnd) {
            // If we don't have room for the output, throw an exception
            if (charOff >= outEnd)
                throw new ConversionBufferFullException();
            if(remainIndex < 0) {
                remainByte[0] = input[byteOff];
                remainIndex = 0;
                byteOff++;
            }
            switch (remainByte[0]) {
            case ISO_SO:
                shiftout = true;
                if(remainIndex > 0)
                    System.arraycopy(remainByte, 1, remainByte, 0, remainIndex);
                remainIndex--;
                break;
            case ISO_SI:
                shiftout = false;
                if(remainIndex > 0)
                    System.arraycopy(remainByte, 1, remainByte, 0, remainIndex);
                remainIndex--;
                break;
             case ISO_ESC:
                byte tmp[] = savetyGetSrc(input, byteOff, inEnd,
                               (maximumDesignatorLength-remainIndex));
                System.arraycopy(tmp, 0, remainByte, remainIndex+1, tmp.length);
                remainIndex += tmp.length;
                byteOff += tmp.length;
                if(tmp.length<(maximumDesignatorLength-remainIndex))
                    break;
                String tmpString = new String(remainByte, 1, remainIndex);
                for (i = 0; i < SODesignator.length; i++) {
                    if(tmpString.indexOf(SODesignator[i]) == 0) {
                        curSODes = i;
                        DesignatorLength = SODesignator[i].length();
                        break;
                    }
                }

                if (DesignatorLength == 0 ) { // Designator not recognized
                   badInputLength = tmp.length;
                   throw new MalformedInputException();
                }

                if (i == SODesignator.length) {
                    for (i = 0; i < SS2Designator.length; i++) {
                        if(tmpString.indexOf(SS2Designator[i]) == 0) {
                            curSS2Des = i;
                            DesignatorLength = SS2Designator[i].length();
                            break;
                        }
                    }
                    if(i == SS2Designator.length) {
                        for(i = 0; i < SS3Designator.length; i++) {
                            if (tmpString.indexOf(SS3Designator[i]) == 0) {
                                curSS3Des = i;
                                DesignatorLength = SS3Designator[i].length();
                                break;
                            }
                        }
                        if (i == SS3Designator.length) {
                            switch(remainByte[1]) {
                            case ISO_SS2_7:
                                output[charOff] = getUnicode(remainByte[2],
                                                          remainByte[3],
                                                          SS2Flag);
                                charOff++;
                                DesignatorLength = 3;
                                break;
                            case ISO_SS3_7:
                                output[charOff] = getUnicode(remainByte[2],
                                                          remainByte[3],
                                                          SS3Flag);
                                charOff++;
                                DesignatorLength = 3;
                                break;
                            default:
                                DesignatorLength = 0;
                            }
                        }
                    }
                }
                if (remainIndex > DesignatorLength) {
                    for(i = 0; i < remainIndex-DesignatorLength; i++)
                        remainByte[i] = remainByte[DesignatorLength+1+i];
                    remainIndex = i-1;
                } else {
                    remainIndex = -1;
                }
                break;
            default:
                if (!shiftout) {
                    output[charOff] = (char)remainByte[0];
                    charOff++;
                } else {
                    switch (state) {
                    case G0:
                        firstByte = remainByte[0];
                        state = G1;
                        break;
                    case G1:
                        output[charOff] = getUnicode(firstByte, remainByte[0],
                                                  SOFlag);
                        charOff++;
                        state = G0;
                        break;
                    }
                }
                if (remainIndex > 0)
                    System.arraycopy(remainByte, 1, remainByte, 0, remainIndex);
                remainIndex--;
            }
        }
        return charOff - outOff;
    }
}
