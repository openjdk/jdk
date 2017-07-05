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

import java.util.ArrayList;
import java.util.List;
import java.nio.CharBuffer;
import com.sun.xml.internal.org.jvnet.fastinfoset.EncodingAlgorithmException;
import com.sun.xml.internal.fastinfoset.CommonResourceBundle;

public class UUIDEncodingAlgorithm extends LongEncodingAlgorithm {

    public final int getPrimtiveLengthFromOctetLength(int octetLength) throws EncodingAlgorithmException {
        if (octetLength % (LONG_SIZE * 2) != 0) {
            throw new EncodingAlgorithmException(CommonResourceBundle.getInstance().
                    getString("message.lengthNotMultipleOfUUID",new Object[]{Integer.valueOf(LONG_SIZE * 2)}));
        }

        return octetLength / LONG_SIZE;
    }

    public final Object convertFromCharacters(char[] ch, int start, int length) {
        final CharBuffer cb = CharBuffer.wrap(ch, start, length);
        final List longList = new ArrayList();

        matchWhiteSpaceDelimnatedWords(cb,
                new WordListener() {
            public void word(int start, int end) {
                String uuidValue = cb.subSequence(start, end).toString();
                fromUUIDString(uuidValue);
                longList.add(Long.valueOf(_msb));
                longList.add(Long.valueOf(_lsb));
            }
        }
        );

        return generateArrayFromList(longList);
    }

    public final void convertToCharacters(Object data, StringBuffer s) {
        if (!(data instanceof long[])) {
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().getString("message.dataNotLongArray"));
        }

        final long[] ldata = (long[])data;

        final int end = ldata.length - 1;
        for (int i = 0; i <= end; i += 2) {
            s.append(toUUIDString(ldata[i], ldata[i + 1]));
            if (i != end) {
                s.append(' ');
            }
        }
    }


    private long _msb;
    private long _lsb;

    final void fromUUIDString(String name) {
        String[] components = name.split("-");
        if (components.length != 5)
            throw new IllegalArgumentException(CommonResourceBundle.getInstance().
                    getString("message.invalidUUID", new Object[]{name}));

        for (int i=0; i<5; i++)
            components[i] = "0x"+components[i];

        _msb = Long.parseLong(components[0], 16);
        _msb <<= 16;
        _msb |= Long.parseLong(components[1], 16);
        _msb <<= 16;
        _msb |= Long.parseLong(components[2], 16);

        _lsb = Long.parseLong(components[3], 16);
        _lsb <<= 48;
        _lsb |= Long.parseLong(components[4], 16);
    }

    final String toUUIDString(long msb, long lsb) {
        return (digits(msb >> 32, 8) + "-" +
                digits(msb >> 16, 4) + "-" +
                digits(msb, 4) + "-" +
                digits(lsb >> 48, 4) + "-" +
                digits(lsb, 12));
    }

    final String digits(long val, int digits) {
        long hi = 1L << (digits * 4);
        return Long.toHexString(hi | (val & (hi - 1))).substring(1);
    }

}
