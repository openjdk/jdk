/*
 * Copyright (c) 2001, 2006, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.jmx.snmp.internal;

import com.sun.jmx.snmp.SnmpDefinitions;
/**
 * Utility class used to deal with various data representations.
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 * @since 1.5
 */
public class SnmpTools implements SnmpDefinitions {

    /**
     * Translates a binary representation in an ASCII one. The returned string is an hexadecimal string starting with 0x.
     * @param data Binary to translate.
     * @return Translated binary.
     */
    static public String binary2ascii(byte[] data, int length)
    {
        if(data == null) return null;
        final int size = (length * 2) + 2;
        byte[] asciiData = new byte[size];
        asciiData[0] = (byte) '0';
        asciiData[1] = (byte) 'x';
        for (int i=0; i < length; i++) {
            int j = i*2;
            int v = (data[i] & 0xf0);
            v = v >> 4;
            if (v < 10)
                asciiData[j+2] = (byte) ('0' + v);
            else
                asciiData[j+2] = (byte) ('A' + (v - 10));
            v = ((data[i] & 0xf));
            if (v < 10)
                asciiData[j+1+2] = (byte) ('0' + v);
            else
                asciiData[j+1+2] = (byte) ('A' + (v - 10));
        }
        return new String(asciiData);
    }

    /**
     * Translates a binary representation in an ASCII one. The returned string is an hexadecimal string starting with 0x.
     * @param data Binary to translate.
     * @return Translated binary.
     */
    static public String binary2ascii(byte[] data)
    {
        return binary2ascii(data, data.length);
    }
    /**
     * Translates a stringified representation in a binary one. The passed string is an hexadecimal one starting with 0x.
     * @param str String to translate.
     * @return Translated string.
     */
    static public byte[] ascii2binary(String str) {
        if(str == null) return null;
        String val = str.substring(2);

        int size = val.length();
        byte []buf = new byte[size/2];
        byte []p = val.getBytes();

        for(int i = 0; i < (size / 2); i++)
        {
            int j = i * 2;
            byte v = 0;
            if (p[j] >= '0' && p[j] <= '9') {
                v = (byte) ((p[j] - '0') << 4);
            }
            else if (p[j] >= 'a' && p[j] <= 'f') {
                v = (byte) ((p[j] - 'a' + 10) << 4);
            }
            else if (p[j] >= 'A' && p[j] <= 'F') {
                v = (byte) ((p[j] - 'A' + 10) << 4);
            }
            else
                throw new Error("BAD format :" + str);

            if (p[j+1] >= '0' && p[j+1] <= '9') {
                //System.out.println("ascii : " + p[j+1]);
                v += (p[j+1] - '0');
                //System.out.println("binary : " + v);
            }
            else if (p[j+1] >= 'a' && p[j+1] <= 'f') {
                //System.out.println("ascii : " + p[j+1]);
                v += (p[j+1] - 'a' + 10);
                //System.out.println("binary : " + v+1);
            }
            else if (p[j+1] >= 'A' && p[j+1] <= 'F') {
                //System.out.println("ascii : " + p[j+1]);
                v += (p[j+1] - 'A' + 10);
                //System.out.println("binary : " + v);
            }
            else
                throw new Error("BAD format :" + str);

            buf[i] = v;
        }
        return buf;
    }
}
