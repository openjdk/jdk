/*
 * Copyright 1996-2002 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.io.*;


/**
 * Convert arrays containing Unicode characters into arrays of bytes, using the
 * platform-default byte order.
 *
 * @author      Mark Reinhold
 */

public class CharToByteUnicode extends CharToByteConverter {

    static final char BYTE_ORDER_MARK = (char) 0xfeff;
    protected boolean usesMark = true;      /* A mark should be written */
    private boolean markWritten = false;  /* A mark has been written */

    static final int UNKNOWN = 0;
    static final int BIG = 1;
    static final int LITTLE = 2;
    protected int byteOrder = UNKNOWN;

    public CharToByteUnicode() {
        String enc = java.security.AccessController.doPrivileged(
           new sun.security.action.GetPropertyAction("sun.io.unicode.encoding",
                                                          "UnicodeBig"));
        if (enc.equals("UnicodeBig"))
            byteOrder = BIG;
        else if (enc.equals("UnicodeLittle"))
            byteOrder = LITTLE;
        else
            byteOrder = BIG;
    }

    public CharToByteUnicode(int byteOrder, boolean usesMark) {
        this.byteOrder = byteOrder;
        this.usesMark = usesMark;
    }

    public CharToByteUnicode(boolean usesMark) {
        this();
        this.usesMark = usesMark;
    }

    public String getCharacterEncoding() {
        switch (byteOrder) {
        case BIG:
            return usesMark ? "UnicodeBig" : "UnicodeBigUnmarked";
        case LITTLE:
            return usesMark ? "UnicodeLittle" : "UnicodeLittleUnmarked";
        default:
            return "UnicodeUnknown";
        }
    }

    public int convert(char in[], int inOff, int inEnd,
                       byte out[], int outOff, int outEnd)
        throws ConversionBufferFullException, MalformedInputException
    {
        charOff = inOff;
        byteOff = outOff;

        if (inOff >= inEnd)
            return 0;

        int inI = inOff,
            outI = outOff,
            outTop = outEnd - 2;

        if (usesMark && !markWritten) {
            if (outI > outTop)
                throw new ConversionBufferFullException();
            if (byteOrder == BIG) {
                out[outI++] = (byte) (BYTE_ORDER_MARK >> 8);
                out[outI++] = (byte) (BYTE_ORDER_MARK & 0xff);
            }
            else {
                out[outI++] = (byte) (BYTE_ORDER_MARK & 0xff);
                out[outI++] = (byte) (BYTE_ORDER_MARK >> 8);
            }
            markWritten = true;
        }

        if (byteOrder == BIG) {
            while (inI < inEnd) {
                if (outI > outTop) {
                    charOff = inI;
                    byteOff = outI;
                    throw new ConversionBufferFullException();
                }
                char c = in[inI++];
                out[outI++] = (byte) (c >> 8);
                out[outI++] = (byte) (c & 0xff);
            }
        }
        else {
            while (inI < inEnd) {
                if (outI > outTop) {
                    charOff = inI;
                    byteOff = outI;
                    throw new ConversionBufferFullException();
                }
                char c = in[inI++];
                out[outI++] = (byte) (c & 0xff);
                out[outI++] = (byte) (c >> 8);
            }
        }

        charOff = inI;
        byteOff = outI;
        return outI - outOff;
    }

    public int flush(byte in[], int inOff, int inEnd) {
        byteOff = charOff = 0;
        return 0;
    }

    public void reset () {
        byteOff = charOff = 0;
        markWritten = false;
    }

    public int getMaxBytesPerChar() {
        return 4;               /* To allow for writing the byte-order mark */
    }

}
