/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.org.jvnet.staxex;

/**
 * @author Kohsuke Kawaguchi
 */
class Base64Encoder {
    private static final char[] encodeMap = initEncodeMap();

    private static char[] initEncodeMap() {
        char[] map = new char[64];
        int i;
        for( i= 0; i<26; i++ )        map[i] = (char)('A'+i);
        for( i=26; i<52; i++ )        map[i] = (char)('a'+(i-26));
        for( i=52; i<62; i++ )        map[i] = (char)('0'+(i-52));
        map[62] = '+';
        map[63] = '/';

        return map;
    }

    public static char encode( int i ) {
        return encodeMap[i&0x3F];
    }

    public static byte encodeByte( int i ) {
        return (byte)encodeMap[i&0x3F];
    }

    public static String print(byte[] input, int offset, int len) {
        char[] buf = new char[((len+2)/3)*4];
        int ptr = print(input,offset,len,buf,0);
        assert ptr==buf.length;
        return new String(buf);
    }

    /**
     * Encodes a byte array into a char array by doing base64 encoding.
     *
     * The caller must supply a big enough buffer.
     *
     * @return
     *      the value of {@code ptr+((len+2)/3)*4}, which is the new offset
     *      in the output buffer where the further bytes should be placed.
     */
    public static int print(byte[] input, int offset, int len, char[] buf, int ptr) {
        for( int i=offset; i<len; i+=3 ) {
            switch( len-i ) {
            case 1:
                buf[ptr++] = encode(input[i]>>2);
                buf[ptr++] = encode(((input[i])&0x3)<<4);
                buf[ptr++] = '=';
                buf[ptr++] = '=';
                break;
            case 2:
                buf[ptr++] = encode(input[i]>>2);
                buf[ptr++] = encode(
                            ((input[i]&0x3)<<4) |
                            ((input[i+1]>>4)&0xF));
                buf[ptr++] = encode((input[i+1]&0xF)<<2);
                buf[ptr++] = '=';
                break;
            default:
                buf[ptr++] = encode(input[i]>>2);
                buf[ptr++] = encode(
                            ((input[i]&0x3)<<4) |
                            ((input[i+1]>>4)&0xF));
                buf[ptr++] = encode(
                            ((input[i+1]&0xF)<<2)|
                            ((input[i+2]>>6)&0x3));
                buf[ptr++] = encode(input[i+2]&0x3F);
                break;
            }
        }
        return ptr;
    }
}
