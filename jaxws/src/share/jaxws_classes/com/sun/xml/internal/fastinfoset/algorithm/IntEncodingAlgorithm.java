/*
 * Copyright (c) 2004, 2011, Oracle and/or its affiliates. All rights reserved.
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
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */

package com.sun.xml.internal.fastinfoset.algorithm;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmException;
import com.sun.xml.internal.fastinfoset.CommonResourceBundle;



public class IntEncodingAlgorithm extends IntegerEncodingAlgorithm {

    public final int getPrimtiveLengthFromOctetLength(int octetLength) throws EncodingAlgorithmException {
        if (octetLength % INT_SIZE != 0) {
            throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().
                    getString("message.lengthNotMultipleOfInt", new Object[]{Integer.valueOf(INT_SIZE)}));
        }

        return octetLength / INT_SIZE;
    }

    public int getOctetLengthFromPrimitiveLength(int primitiveLength) {
        return primitiveLength * INT_SIZE;
    }

    public final Object decodeFromBytes(byte[] b, int start, int length) throws EncodingAlgorithmException {
        int[] data = new int[getPrimtiveLengthFromOctetLength(length)];
        decodeFromBytesToIntArray(data, 0, b, start, length);

        return data;
    }

    public final Object decodeFromInputStream(InputStream s) throws IOException {
        return decodeFromInputStreamToIntArray(s);
    }


    public void encodeToOutputStream(Object data, OutputStream s) throws IOException {
        if (!(data instanceof int[])) {
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().getString("message.dataNotIntArray"));
        }

        final int[] idata = (int[])data;

        encodeToOutputStreamFromIntArray(idata, s);
    }


    public final Object convertFromCharacters(char[] ch, int start, int length) {
        final CharBuffer cb = CharBuffer.wrap(ch, start, length);
        final List integerList = new ArrayList();

        matchWhiteSpaceDelimnatedWords(cb,
                new WordListener() {
            public void word(int start, int end) {
                String iStringValue = cb.subSequence(start, end).toString();
                integerList.add(Integer.valueOf(iStringValue));
            }
        }
        );

        return generateArrayFromList(integerList);
    }

    public final void convertToCharacters(Object data, StringBuffer s) {
        if (!(data instanceof int[])) {
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().getString("message.dataNotIntArray"));
        }

        final int[] idata = (int[])data;

        convertToCharactersFromIntArray(idata, s);
    }


    public final void decodeFromBytesToIntArray(int[] idata, int istart, byte[] b, int start, int length) {
        final int size = length / INT_SIZE;
        for (int i = 0; i < size; i++) {
            idata[istart++] = ((b[start++] & 0xFF) << 24) |
                    ((b[start++] & 0xFF) << 16) |
                    ((b[start++] & 0xFF) << 8) |
                    (b[start++] & 0xFF);
        }
    }

    public final int[] decodeFromInputStreamToIntArray(InputStream s) throws IOException {
        final List integerList = new ArrayList();
        final byte[] b = new byte[INT_SIZE];

        while (true) {
            int n = s.read(b);
            if (n != 4) {
                if (n == -1) {
                    break;
                }

                while(n != 4) {
                    final int m = s.read(b, n, INT_SIZE - n);
                    if (m == -1) {
                        throw new EOFException();
                    }
                    n += m;
                }
            }

            final int i = ((b[0] & 0xFF) << 24) |
                    ((b[1] & 0xFF) << 16) |
                    ((b[2] & 0xFF) << 8) |
                    (b[3] & 0xFF);
            integerList.add(Integer.valueOf(i));
        }

        return generateArrayFromList(integerList);
    }


    public final void encodeToOutputStreamFromIntArray(int[] idata, OutputStream s) throws IOException {
        for (int i = 0; i < idata.length; i++) {
            final int bits = idata[i];
            s.write((bits >>> 24) & 0xFF);
            s.write((bits >>> 16) & 0xFF);
            s.write((bits >>> 8) & 0xFF);
            s.write(bits & 0xFF);
        }
    }

    public final void encodeToBytes(Object array, int astart, int alength, byte[] b, int start) {
        encodeToBytesFromIntArray((int[])array, astart, alength, b, start);
    }

    public final void encodeToBytesFromIntArray(int[] idata, int istart, int ilength, byte[] b, int start) {
        final int iend = istart + ilength;
        for (int i = istart; i < iend; i++) {
            final int bits = idata[i];
            b[start++] = (byte)((bits >>> 24) & 0xFF);
            b[start++] = (byte)((bits >>> 16) & 0xFF);
            b[start++] = (byte)((bits >>>  8) & 0xFF);
            b[start++] = (byte)(bits & 0xFF);
        }
    }


    public final void convertToCharactersFromIntArray(int[] idata, StringBuffer s) {
        final int end = idata.length - 1;
        for (int i = 0; i <= end; i++) {
            s.append(Integer.toString(idata[i]));
            if (i != end) {
                s.append(' ');
            }
        }
    }


    public final int[] generateArrayFromList(List array) {
        int[] idata = new int[array.size()];
        for (int i = 0; i < idata.length; i++) {
            idata[i] = ((Integer)array.get(i)).intValue();
        }

        return idata;
    }
}
