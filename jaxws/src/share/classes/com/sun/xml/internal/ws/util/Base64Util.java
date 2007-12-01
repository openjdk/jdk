/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.ws.util;

/**
 *
 * @author jitu
 */
public class Base64Util {

    protected static final char encodeBase64[] = {
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
        'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
    };

    protected static final int decodeBase64[] = {
        /*'+'*/ 62,
        -1, -1, -1,
        /*'/'*/ 63,
        /*'0'*/ 52,
        /*'1'*/ 53,
        /*'2'*/ 54,
        /*'3'*/ 55,
        /*'4'*/ 56,
        /*'5'*/ 57,
        /*'6'*/ 58,
        /*'7'*/ 59,
        /*'8'*/ 60,
        /*'9'*/ 61,
        -1, -1, -1, -1, -1, -1, -1,
        /*'A'*/ 0,
        /*'B'*/ 1,
        /*'C'*/ 2,
        /*'D'*/ 3,
        /*'E'*/ 4,
        /*'F'*/ 5,
        /*'G'*/ 6,
        /*'H'*/ 7,
        /*'I'*/ 8,
        /*'J'*/ 9,
        /*'K'*/ 10,
        /*'L'*/ 11,
        /*'M'*/ 12,
        /*'N'*/ 13,
        /*'O'*/ 14,
        /*'P'*/ 15,
        /*'Q'*/ 16,
        /*'R'*/ 17,
        /*'S'*/ 18,
        /*'T'*/ 19,
        /*'U'*/ 20,
        /*'V'*/ 21,
        /*'W'*/ 22,
        /*'X'*/ 23,
        /*'Y'*/ 24,
        /*'Z'*/ 25,
        -1, -1, -1, -1, -1, -1,
        /*'a'*/ 26,
        /*'b'*/ 27,
        /*'c'*/ 28,
        /*'d'*/ 29,
        /*'e'*/ 30,
        /*'f'*/ 31,
        /*'g'*/ 32,
        /*'h'*/ 33,
        /*'i'*/ 34,
        /*'j'*/ 35,
        /*'k'*/ 36,
        /*'l'*/ 37,
        /*'m'*/ 38,
        /*'n'*/ 39,
        /*'o'*/ 40,
        /*'p'*/ 41,
        /*'q'*/ 42,
        /*'r'*/ 43,
        /*'s'*/ 44,
        /*'t'*/ 45,
        /*'u'*/ 46,
        /*'v'*/ 47,
        /*'w'*/ 48,
        /*'x'*/ 49,
        /*'y'*/ 50,
        /*'z'*/ 51
    };

    public static String encode(byte[] value)
        throws Exception {

        if (value == null) {
            return null;
        }
        if (value.length == 0) {
            return "";
        }
        int blockCount = value.length / 3;
        int partialBlockLength = value.length % 3;

        if (partialBlockLength != 0) {
            ++blockCount;
        }

        int encodedLength = blockCount * 4;
        StringBuffer encodedValue = new StringBuffer(encodedLength);

        int idx = 0;
        for (int i = 0; i < blockCount; ++i) {
            int b1 = value[idx++];
            int b2 = (idx < value.length) ? value[idx++] : 0;
            int b3 = (idx < value.length) ? value[idx++] : 0;

            if (b1 < 0) {
                b1 += 256;
            }
            if (b2 < 0) {
                b2 += 256;
            }
            if (b3 < 0) {
                b3 += 256;
            }

            char encodedChar;

            encodedChar = encodeBase64[b1 >> 2];
            encodedValue.append(encodedChar);

            encodedChar = encodeBase64[((b1 & 0x03) << 4) | (b2 >> 4)];
            encodedValue.append(encodedChar);

            encodedChar = encodeBase64[((b2 & 0x0f) << 2) | (b3 >> 6)];
            encodedValue.append(encodedChar);

            encodedChar = encodeBase64[b3 & 0x3f];
            encodedValue.append(encodedChar);
        }

        switch (partialBlockLength) {
            case 0 :
                // do nothing
                break;
            case 1 :
                encodedValue.setCharAt(encodedLength - 1, '=');
                encodedValue.setCharAt(encodedLength - 2, '=');
                break;
            case 2 :
                encodedValue.setCharAt(encodedLength - 1, '=');
                break;
        }

        return encodedValue.toString();
    }

}
