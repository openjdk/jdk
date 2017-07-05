/*
 * Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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



public class DoubleEncodingAlgorithm extends IEEE754FloatingPointEncodingAlgorithm {

    public final int getPrimtiveLengthFromOctetLength(int octetLength) throws EncodingAlgorithmException {
        if (octetLength % DOUBLE_SIZE != 0) {
            throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().
                    getString("message.lengthIsNotMultipleOfDouble", new Object[]{Integer.valueOf(DOUBLE_SIZE)}));
        }

        return octetLength / DOUBLE_SIZE;
    }

    public int getOctetLengthFromPrimitiveLength(int primitiveLength) {
        return primitiveLength * DOUBLE_SIZE;
    }

    public final Object decodeFromBytes(byte[] b, int start, int length) throws EncodingAlgorithmException {
        double[] data = new double[getPrimtiveLengthFromOctetLength(length)];
        decodeFromBytesToDoubleArray(data, 0, b, start, length);

        return data;
    }

    public final Object decodeFromInputStream(InputStream s) throws IOException {
        return decodeFromInputStreamToDoubleArray(s);
    }


    public void encodeToOutputStream(Object data, OutputStream s) throws IOException {
        if (!(data instanceof double[])) {
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().getString("message.dataNotDouble"));
        }

        final double[] fdata = (double[])data;

        encodeToOutputStreamFromDoubleArray(fdata, s);
    }

    public final Object convertFromCharacters(char[] ch, int start, int length) {
        final CharBuffer cb = CharBuffer.wrap(ch, start, length);
        final List doubleList = new ArrayList();

        matchWhiteSpaceDelimnatedWords(cb,
                new WordListener() {
            public void word(int start, int end) {
                String fStringValue = cb.subSequence(start, end).toString();
                doubleList.add(Double.valueOf(fStringValue));
            }
        }
        );

        return generateArrayFromList(doubleList);
    }

    public final void convertToCharacters(Object data, StringBuffer s) {
        if (!(data instanceof double[])) {
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().getString("message.dataNotDouble"));
        }

        final double[] fdata = (double[])data;

        convertToCharactersFromDoubleArray(fdata, s);
    }


    public final void decodeFromBytesToDoubleArray(double[] data, int fstart, byte[] b, int start, int length) {
        final int size = length / DOUBLE_SIZE;
        for (int i = 0; i < size; i++) {
            final long bits =
                    ((long)(b[start++] & 0xFF) << 56) |
                    ((long)(b[start++] & 0xFF) << 48) |
                    ((long)(b[start++] & 0xFF) << 40) |
                    ((long)(b[start++] & 0xFF) << 32) |
                    ((long)(b[start++] & 0xFF) << 24) |
                    ((long)(b[start++] & 0xFF) << 16) |
                    ((long)(b[start++] & 0xFF) << 8) |
                    (long)(b[start++] & 0xFF);
            data[fstart++] = Double.longBitsToDouble(bits);
        }
    }

    public final double[] decodeFromInputStreamToDoubleArray(InputStream s) throws IOException {
        final List doubleList = new ArrayList();
        final byte[] b = new byte[DOUBLE_SIZE];

        while (true) {
            int n = s.read(b);
            if (n != DOUBLE_SIZE) {
                if (n == -1) {
                    break;
                }

                while(n != DOUBLE_SIZE) {
                    final int m = s.read(b, n, DOUBLE_SIZE - n);
                    if (m == -1) {
                        throw new EOFException();
                    }
                    n += m;
                }
            }

            final long bits =
                    ((long)(b[0] & 0xFF) << 56) |
                    ((long)(b[1] & 0xFF) << 48) |
                    ((long)(b[2] & 0xFF) << 40) |
                    ((long)(b[3] & 0xFF) << 32) |
                    ((b[4] & 0xFF) << 24) |
                    ((b[5] & 0xFF) << 16) |
                    ((b[6] & 0xFF) << 8) |
                    (b[7] & 0xFF);

            doubleList.add(Double.valueOf(Double.longBitsToDouble(bits)));
        }

        return generateArrayFromList(doubleList);
    }


    public final void encodeToOutputStreamFromDoubleArray(double[] fdata, OutputStream s) throws IOException {
        for (int i = 0; i < fdata.length; i++) {
            final long bits = Double.doubleToLongBits(fdata[i]);
            s.write((int)((bits >>> 56) & 0xFF));
            s.write((int)((bits >>> 48) & 0xFF));
            s.write((int)((bits >>> 40) & 0xFF));
            s.write((int)((bits >>> 32) & 0xFF));
            s.write((int)((bits >>> 24) & 0xFF));
            s.write((int)((bits >>> 16) & 0xFF));
            s.write((int)((bits >>>  8) & 0xFF));
            s.write((int)(bits & 0xFF));
        }
    }

    public final void encodeToBytes(Object array, int astart, int alength, byte[] b, int start) {
        encodeToBytesFromDoubleArray((double[])array, astart, alength, b, start);
    }

    public final void encodeToBytesFromDoubleArray(double[] fdata, int fstart, int flength, byte[] b, int start) {
        final int fend = fstart + flength;
        for (int i = fstart; i < fend; i++) {
            final long bits = Double.doubleToLongBits(fdata[i]);
            b[start++] = (byte)((bits >>> 56) & 0xFF);
            b[start++] = (byte)((bits >>> 48) & 0xFF);
            b[start++] = (byte)((bits >>> 40) & 0xFF);
            b[start++] = (byte)((bits >>> 32) & 0xFF);
            b[start++] = (byte)((bits >>> 24) & 0xFF);
            b[start++] = (byte)((bits >>> 16) & 0xFF);
            b[start++] = (byte)((bits >>>  8) & 0xFF);
            b[start++] = (byte)(bits & 0xFF);
        }
    }


    public final void convertToCharactersFromDoubleArray(double[] fdata, StringBuffer s) {
        final int end = fdata.length - 1;
        for (int i = 0; i <= end; i++) {
            s.append(Double.toString(fdata[i]));
            if (i != end) {
                s.append(' ');
            }
        }
    }


    public final double[] generateArrayFromList(List array) {
        double[] fdata = new double[array.size()];
        for (int i = 0; i < fdata.length; i++) {
            fdata[i] = ((Double)array.get(i)).doubleValue();
        }

        return fdata;
    }

}
