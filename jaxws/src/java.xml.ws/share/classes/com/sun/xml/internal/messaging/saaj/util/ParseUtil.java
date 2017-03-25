/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.messaging.saaj.util;


// Cut&paste from sun.net.www.ParseUtil: decode, unescape

public class ParseUtil {
    /**
     * Un-escape and return the character at position i in string s.
     */
    private static char unescape(String s, int i) {
        return (char) Integer.parseInt(s.substring(i+1,i+3),16);
    }

    /**
     * Returns a new String constructed from the specified String by replacing
     * the URL escape sequences and UTF8 encoding with the characters they
     * represent.
     *
     * @param s string
     *
     * @return decoded string
     */
    public static String decode(String s) {
        StringBuilder sb = new StringBuilder();

        int i=0;
        while (i<s.length()) {
            char c = s.charAt(i);
            char c2, c3;

            if (c != '%') {
                i++;
            } else {
                try {
                    c = unescape(s, i);
                    i += 3;

                    if ((c & 0x80) != 0) {
                        switch (c >> 4) {
                            case 0xC: case 0xD:
                                c2 = unescape(s, i);
                                i += 3;
                                c = (char)(((c & 0x1f) << 6) | (c2 & 0x3f));
                                break;

                            case 0xE:
                                c2 = unescape(s, i);
                                i += 3;
                                c3 = unescape(s, i);
                                i += 3;
                                c = (char)(((c & 0x0f) << 12) |
                                           ((c2 & 0x3f) << 6) |
                                            (c3 & 0x3f));
                                break;

                            default:
                                throw new IllegalArgumentException();
                        }
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException();
                }
            }

            sb.append(c);
        }

        return sb.toString();
    }


}
