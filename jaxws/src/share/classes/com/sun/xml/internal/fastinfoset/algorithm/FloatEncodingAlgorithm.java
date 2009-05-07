/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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



public class FloatEncodingAlgorithm extends IEEE754FloatingPointEncodingAlgorithm {

    public final int getPrimtiveLengthFromOctetLength(int octetLength) throws EncodingAlgorithmException {
        if (octetLength % FLOAT_SIZE != 0) {
            throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().
                    getString("message.lengthNotMultipleOfFloat", new Object[]{Integer.valueOf(FLOAT_SIZE)}));
        }

        return octetLength / FLOAT_SIZE;
    }

    public int getOctetLengthFromPrimitiveLength(int primitiveLength) {
        return primitiveLength * FLOAT_SIZE;
    }

    public final Object decodeFromBytes(byte[] b, int start, int length) throws EncodingAlgorithmException {
        float[] data = new float[getPrimtiveLengthFromOctetLength(length)];
        decodeFromBytesToFloatArray(data, 0, b, start, length);

        return data;
    }

    public final Object decodeFromInputStream(InputStream s) throws IOException {
        return decodeFromInputStreamToFloatArray(s);
    }


    public void encodeToOutputStream(Object data, OutputStream s) throws IOException {
        if (!(data instanceof float[])) {
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().getString("message.dataNotFloat"));
        }

        final float[] fdata = (float[])data;

        encodeToOutputStreamFromFloatArray(fdata, s);
    }

    public final Object convertFromCharacters(char[] ch, int start, int length) {
        final CharBuffer cb = CharBuffer.wrap(ch, start, length);
        final List floatList = new ArrayList();

        matchWhiteSpaceDelimnatedWords(cb,
                new WordListener() {
            public void word(int start, int end) {
                String fStringValue = cb.subSequence(start, end).toString();
                floatList.add(Float.valueOf(fStringValue));
            }
        }
        );

        return generateArrayFromList(floatList);
    }

    public final void convertToCharacters(Object data, StringBuffer s) {
        if (!(data instanceof float[])) {
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().getString("message.dataNotFloat"));
        }

        final float[] fdata = (float[])data;

        convertToCharactersFromFloatArray(fdata, s);
    }


    public final void decodeFromBytesToFloatArray(float[] data, int fstart, byte[] b, int start, int length) {
        final int size = length / FLOAT_SIZE;
        for (int i = 0; i < size; i++) {
            final int bits = ((b[start++] & 0xFF) << 24) |
                    ((b[start++] & 0xFF) << 16) |
                    ((b[start++] & 0xFF) << 8) |
                    (b[start++] & 0xFF);
            data[fstart++] = Float.intBitsToFloat(bits);
        }
    }

    public final float[] decodeFromInputStreamToFloatArray(InputStream s) throws IOException {
        final List floatList = new ArrayList();
        final byte[] b = new byte[FLOAT_SIZE];

        while (true) {
            int n = s.read(b);
            if (n != 4) {
                if (n == -1) {
                    break;
                }

                while(n != 4) {
                    final int m = s.read(b, n, FLOAT_SIZE - n);
                    if (m == -1) {
                        throw new EOFException();
                    }
                    n += m;
                }
            }

            final int bits = ((b[0] & 0xFF) << 24) |
                    ((b[1] & 0xFF) << 16) |
                    ((b[2] & 0xFF) << 8) |
                    (b[3] & 0xFF);
            floatList.add(Float.valueOf(Float.intBitsToFloat(bits)));
        }

        return generateArrayFromList(floatList);
    }


    public final void encodeToOutputStreamFromFloatArray(float[] fdata, OutputStream s) throws IOException {
        for (int i = 0; i < fdata.length; i++) {
            final int bits = Float.floatToIntBits(fdata[i]);
            s.write((bits >>> 24) & 0xFF);
            s.write((bits >>> 16) & 0xFF);
            s.write((bits >>> 8) & 0xFF);
            s.write(bits & 0xFF);
        }
    }

    public final void encodeToBytes(Object array, int astart, int alength, byte[] b, int start) {
        encodeToBytesFromFloatArray((float[])array, astart, alength, b, start);
    }

    public final void encodeToBytesFromFloatArray(float[] fdata, int fstart, int flength, byte[] b, int start) {
        final int fend = fstart + flength;
        for (int i = fstart; i < fend; i++) {
            final int bits = Float.floatToIntBits(fdata[i]);
            b[start++] = (byte)((bits >>> 24) & 0xFF);
            b[start++] = (byte)((bits >>> 16) & 0xFF);
            b[start++] = (byte)((bits >>>  8) & 0xFF);
            b[start++] = (byte)(bits & 0xFF);
        }
    }


    public final void convertToCharactersFromFloatArray(float[] fdata, StringBuffer s) {
        final int end = fdata.length - 1;
        for (int i = 0; i <= end; i++) {
            s.append(Float.toString(fdata[i]));
            if (i != end) {
                s.append(' ');
            }
        }
    }


    public final float[] generateArrayFromList(List array) {
        float[] fdata = new float[array.size()];
        for (int i = 0; i < fdata.length; i++) {
            fdata[i] = ((Float)array.get(i)).floatValue();
        }

        return fdata;
    }

}
