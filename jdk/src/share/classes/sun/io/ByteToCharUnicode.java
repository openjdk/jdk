/*
 * Copyright (c) 1996, 1999, Oracle and/or its affiliates. All rights reserved.
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
import java.io.*;


/**
 * Convert byte arrays containing Unicode characters into arrays of actual
 * Unicode characters.  This class may be used directly, in which case it
 * expects the input byte array to begin with a byte-order mark, or it may be
 * subclassed in order to preset the byte order and mark behavior.
 *
 * <p> Whether or not a mark is expected, if a mark that does not match the
 * established byte order is later discovered then a
 * <tt>MalformedInputException</tt> will be thrown by the <tt>convert</tt>
 * method.  If a correct mark is seen later in the input stream, it is passed
 * through as a character.
 *
 * @see ByteToCharUnicodeLittle
 * @see ByteToCharUnicodeLittleUnmarked
 * @see ByteToCharUnicodeBig
 * @see ByteToCharUnicodeBigUnmarked
 *
 * @author      Mark Reinhold
 */

public class ByteToCharUnicode extends ByteToCharConverter {

    static final char BYTE_ORDER_MARK = (char) 0xfeff;
    static final char REVERSED_MARK = (char) 0xfffe;

    static final int AUTO = 0;
    static final int BIG = 1;
    static final int LITTLE = 2;

    int originalByteOrder;      /* Byte order specified at creation */
    int byteOrder;              /* Byte order in use */
    boolean usesMark;           /* Look for a mark and interpret it */

    /**
     * Creates a Unicode byte-to-char converter that expects the first pair of
     * input bytes to be a byte-order mark, which will be interpreted and
     * discarded.  If the first pair of bytes is not such a mark then a
     * <tt>MalformedInputException</tt> will be thrown by the convert method.
     */
    public ByteToCharUnicode() {
        originalByteOrder = byteOrder = AUTO;
        usesMark = true;
    }

    /**
     * Creates a Unicode byte-to-char converter that uses the given byte order
     * and may or may not insist upon an initial byte-order mark.
     */
    protected ByteToCharUnicode(int bo, boolean m) {
        originalByteOrder = byteOrder = bo;
        usesMark = m;
    }

    public String getCharacterEncoding() {
        switch (originalByteOrder) {
        case BIG:
            return usesMark ? "UnicodeBig" : "UnicodeBigUnmarked";
        case LITTLE:
            return usesMark ? "UnicodeLittle" : "UnicodeLittleUnmarked";
        default:
            return "Unicode";
        }
    }

    boolean started = false;
    int leftOverByte;
    boolean leftOver = false;

    public int convert(byte[] in, int inOff, int inEnd,
                       char[] out, int outOff, int outEnd)
        throws ConversionBufferFullException, MalformedInputException
    {
        byteOff = inOff;
        charOff = outOff;

        if (inOff >= inEnd)
            return 0;

        int b1, b2;
        int bc = 0;
        int inI = inOff, outI = outOff;

        if (leftOver) {
            b1 = leftOverByte & 0xff;
            leftOver = false;
        }
        else {
            b1 = in[inI++] & 0xff;
        }
        bc = 1;

        if (usesMark && !started) {     /* Read initial byte-order mark */
            if (inI < inEnd) {
                b2 = in[inI++] & 0xff;
                bc = 2;

                char c = (char) ((b1 << 8) | b2);
                int bo = AUTO;

                if (c == BYTE_ORDER_MARK)
                    bo = BIG;
                else if (c == REVERSED_MARK)
                    bo = LITTLE;

                if (byteOrder == AUTO) {
                    if (bo == AUTO) {
                        badInputLength = bc;
                        throw new
                            MalformedInputException("Missing byte-order mark");
                    }
                    byteOrder = bo;
                    if (inI < inEnd) {
                        b1 = in[inI++] & 0xff;
                        bc = 1;
                    }
                }
                else if (bo == AUTO) {
                    inI--;
                    bc = 1;
                }
                else if (byteOrder == bo) {
                    if (inI < inEnd) {
                        b1 = in[inI++] & 0xff;
                        bc = 1;
                    }
                }
                else {
                    badInputLength = bc;
                    throw new
                        MalformedInputException("Incorrect byte-order mark");
                }

                started = true;
            }
        }

        /* Loop invariant: (b1 contains the next input byte) && (bc == 1) */
        while (inI < inEnd) {
            b2 = in[inI++] & 0xff;
            bc = 2;

            char c;
            if (byteOrder == BIG)
                c = (char) ((b1 << 8) | b2);
            else
                c = (char) ((b2 << 8) | b1);

            if (c == REVERSED_MARK)
                throw new
                    MalformedInputException("Reversed byte-order mark");

            if (outI >= outEnd)
                throw new ConversionBufferFullException();
            out[outI++] = c;
            byteOff = inI;
            charOff = outI;

            if (inI < inEnd) {
                b1 = in[inI++] & 0xff;
                bc = 1;
            }
        }

        if (bc == 1) {
            leftOverByte = b1;
            byteOff = inI;
            leftOver = true;
        }

        return outI - outOff;
    }

    public void reset() {
        leftOver = false;
        byteOff = charOff = 0;
        started = false;
        byteOrder = originalByteOrder;
    }

    public int flush(char buf[], int off, int len)
        throws MalformedInputException
    {
        if (leftOver) {
            reset();
            throw new MalformedInputException();
        }
        byteOff = charOff = 0;
        return 0;
    }

}
