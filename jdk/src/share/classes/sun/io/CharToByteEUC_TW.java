/*
 * Copyright 1996-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

public class CharToByteEUC_TW extends CharToByteConverter
{
    private final byte MSB = (byte)0x80;
    private final byte SS2 = (byte) 0x8E;
    private final byte P2 = (byte) 0xA2;
    private final byte P3 = (byte) 0xA3;

    private final static EUC_TW nioCoder = new EUC_TW();

    private static String uniTab1 = nioCoder.getUniTab1();
    private static String uniTab2 = nioCoder.getUniTab2();
    private static String uniTab3 = nioCoder.getUniTab3();
    private static String cnsTab1 = nioCoder.getCNSTab1();
    private static String cnsTab2 = nioCoder.getCNSTab2();
    private static String cnsTab3 = nioCoder.getCNSTab3();

    public int flush(byte[] output, int outStart, int outEnd)
        throws MalformedInputException
    {
        reset();
        return 0;
    }

    public void reset() {
        byteOff = charOff = 0;
    }

    public boolean canConvert(char ch){
        if (((0xFF00 & ch) != 0) && (getNative(ch) != -1)){
            return true;
        }
        return false;
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
        byte [] tmpbuf = new byte[4];
        byte [] outputByte;

        byteOff = outOff;

        //Fixed 4122961 by bringing the charOff++ out to this
        // loop where it belongs, changing the loop from
        // while(){} to for(){}.
        for (charOff = inOff; charOff < inEnd; charOff++) {
            outputByte = tmpbuf;
            if ( input[charOff] < 0x80) {       // ASCII
                outputSize = 1;
                outputByte[0] = (byte)(input[charOff] & 0x7f);
            } else {
                outputSize = unicodeToEUC(input[charOff], outputByte);
            }

            if (outputSize == -1) {
                if (subMode) {
                    outputByte = subBytes;
                    outputSize = subBytes.length;
                } else {
                    badInputLength = 1;
                    throw new UnknownCharacterException();
                }
            }

            if (outEnd - byteOff < outputSize)
                throw new ConversionBufferFullException();

            for (int i = 0; i < outputSize; i++)
                output[byteOff++] = outputByte[i];
        }

        return byteOff - outOff;

    }


    /**
     * returns the maximum number of bytes needed to convert a char
     */
    public int getMaxBytesPerChar() {
        return 4;
    }


    /**
     * Return the character set ID
     */
    public String getCharacterEncoding() {
        return "EUC_TW";
    }


    protected int getNative(char unicode) {
        int  i,
             cns;       // 2 chars in CNS table make 1 CNS code

        if (unicode < UniTab2[0]) {
            if ((i = searchTab(unicode, UniTab1)) == -1)
                return -1;
            cns = (CNSTab1[2*i] << 16) + CNSTab1[2*i+1];
            return cns;
        } else  if (unicode < UniTab3[0]) {
            if ((i = searchTab(unicode, UniTab2)) == -1)
                return -1;
            cns = (CNSTab2[2*i] << 16) + CNSTab2[2*i+1];
            return cns;
        } else {
            if ((i = searchTab(unicode, UniTab3)) == -1)
                return -1;
            cns = (CNSTab3[2*i] << 16) + CNSTab3[2*i+1];
            return cns;
        }
    }

    protected int searchTab(char code, char [] table) {
        int     i = 0, l, h;

        for (l = 0, h = table.length - 1; l < h; ) {
                if (table[l] == code) {
                        i = l;
                        break;
                }
                if (table[h] == code) {
                        i = h;
                        break;
                }
                i = (l + h) / 2;
                if (table[i] == code)
                        break;
                if (table[i] < code)
                        l = i + 1;
                else    h = i - 1;
        }
        if (code == table[i]) {
            return i;
        } else {
            return -1;
        }
    }


    private int unicodeToEUC(char unicode, byte ebyte[]) {
        int cns = getNative(unicode);

        if ((cns >> 16) == 0x01) {      // Plane 1
            ebyte[0] = (byte) (((cns & 0xff00) >> 8) | MSB);
            ebyte[1] = (byte) ((cns & 0xff) | MSB);
            return 2;
        }

        byte cnsPlane = (byte)(cns >> 16);
        if (cnsPlane >= (byte)0x02) {   // Plane 2
            ebyte[0] = SS2;
            ebyte[1] = (byte) (cnsPlane | (byte)0xA0);
            ebyte[2] = (byte) (((cns & 0xff00) >> 8) | MSB);
            ebyte[3] = (byte) ((cns & 0xff) | MSB);
            return 4;
        }

        return -1;
    }

    protected int unicodeToEUC(char unicode) {
        if (unicode <= 0x7F) { // ASCII falls into EUC_TW CS0
            return unicode;
        }

        int cns = getNative(unicode);
        int plane = cns >> 16;
        int euc = (cns & 0x0000FFFF) | 0x00008080;

        if (plane == 1) {
            return euc;
        } else if (plane == 2) {
            return ((SS2 << 24) & 0xFF000000) | ((P2 << 16) & 0x00FF0000) |
                euc;
        } else if (plane == 3) {
            return ((SS2 << 24) & 0xFF000000) | ((P3 << 16) & 0x00FF0000) |
                euc;
        }

        return -1;
    }

    private char [] UniTab1 = uniTab1.toCharArray();
    private char [] UniTab2 = uniTab2.toCharArray();
    private char [] UniTab3 = uniTab3.toCharArray();
    private char [] CNSTab1 = cnsTab1.toCharArray();
    private char [] CNSTab2 = cnsTab2.toCharArray();
    private char [] CNSTab3 = cnsTab3.toCharArray();
}
