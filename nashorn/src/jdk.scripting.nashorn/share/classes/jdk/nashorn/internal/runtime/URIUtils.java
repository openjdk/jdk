/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.runtime.ECMAErrors.uriError;

/**
 * URI handling global functions. ECMA 15.1.3 URI Handling Function Properties
 *
 */
public final class URIUtils {

    private URIUtils() {
    }

    static String encodeURI(final Object self, final String string) {
        return encode(self, string, false);
    }

    static String encodeURIComponent(final Object self, final String string) {
        return encode(self, string, true);
    }

    static String decodeURI(final Object self, final String string) {
        return decode(self, string, false);
    }

    static String decodeURIComponent(final Object self, final String string) {
        return decode(self, string, true);
    }

    // abstract encode function
    private static String encode(final Object self, final String string, final boolean component) {
        if (string.isEmpty()) {
            return string;
        }

        final int len = string.length();
        final StringBuilder sb = new StringBuilder();

        for (int k = 0; k < len; k++) {
            final char C = string.charAt(k);
            if (isUnescaped(C, component)) {
                sb.append(C);
                continue;
            }

            if (C >= 0xDC00 && C <= 0xDFFF) {
                return error(string, k);
            }

            int V;
            if (C < 0xD800 || C > 0xDBFF) {
                V = C;
            } else {
                k++;
                if (k == len) {
                    return error(string, k);
                }

                final char kChar = string.charAt(k);
                if (kChar < 0xDC00 || kChar > 0xDFFF) {
                    return error(string, k);
                }
                V = ((C - 0xD800) * 0x400 + (kChar - 0xDC00) + 0x10000);
            }

            try {
                sb.append(toHexEscape(V));
            } catch (final Exception e) {
                throw uriError(e, "bad.uri", string, Integer.toString(k));
            }
        }

        return sb.toString();
    }

    // abstract decode function
    private static String decode(final Object self, final String string, final boolean component) {
        if (string.isEmpty()) {
            return string;
        }

        final int           len = string.length();
        final StringBuilder sb  = new StringBuilder();

        for (int k = 0; k < len; k++) {
            final char ch = string.charAt(k);
            if (ch != '%') {
                sb.append(ch);
                continue;
            }
            final int start = k;
            if (k + 2 >= len) {
                return error(string, k);
            }

            int B = toHexByte(string.charAt(k + 1), string.charAt(k + 2));
            if (B < 0) {
                return error(string, k + 1);
            }

            k += 2;
            char C;
            // Most significant bit is zero
            if ((B & 0x80) == 0) {
                C = (char) B;
                if (!component && URI_RESERVED.indexOf(C) >= 0) {
                    for (int j = start; j <= k; j++) {
                        sb.append(string.charAt(j));
                    }
                } else {
                    sb.append(C);
                }
            } else {
                // n is utf8 length, V is codepoint and minV is lower bound
                int n, V, minV;

                if ((B & 0xC0) == 0x80) {
                    // 10xxxxxx - illegal first byte
                    return error(string, k);
                } else if ((B & 0x20) == 0) {
                    // 110xxxxx 10xxxxxx
                    n = 2;
                    V = B & 0x1F;
                    minV = 0x80;
                } else if ((B & 0x10) == 0) {
                    // 1110xxxx 10xxxxxx 10xxxxxx
                    n = 3;
                    V = B & 0x0F;
                    minV = 0x800;
                } else if ((B & 0x08) == 0) {
                    // 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                    n = 4;
                    V = B & 0x07;
                    minV = 0x10000;
                } else if ((B & 0x04) == 0) {
                    // 111110xx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
                    n = 5;
                    V =  B & 0x03;
                    minV = 0x200000;
                } else if ((B & 0x02) == 0) {
                    // 1111110x 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
                    n = 6;
                    V = B & 0x01;
                    minV = 0x4000000;
                } else {
                    return error(string, k);
                }

                // check bound for sufficient chars
                if (k + (3*(n-1)) >= len) {
                    return error(string, k);
                }

                for (int j = 1; j < n; j++) {
                    k++;
                    if (string.charAt(k) != '%') {
                        return error(string, k);
                    }

                    B = toHexByte(string.charAt(k + 1), string.charAt(k + 2));
                    if (B < 0 || (B & 0xC0) != 0x80) {
                        return error(string, k + 1);
                    }

                    V = (V << 6) | (B & 0x3F);
                    k += 2;
                }

                // Check for overlongs and invalid codepoints.
                // The high and low surrogate halves used by UTF-16
                // (U+D800 through U+DFFF) are not legal Unicode values.
                if ((V < minV) || (V >= 0xD800 && V <= 0xDFFF)) {
                    V = Integer.MAX_VALUE;
                }

                if (V < 0x10000) {
                    C = (char) V;
                    if (!component && URI_RESERVED.indexOf(C) >= 0) {
                        for (int j = start; j != k; j++) {
                            sb.append(string.charAt(j));
                        }
                    } else {
                        sb.append(C);
                    }
                } else { // V >= 0x10000
                    if (V > 0x10FFFF) {
                        return error(string, k);
                    }
                    final int L = ((V - 0x10000) & 0x3FF) + 0xDC00;
                    final int H = (((V - 0x10000) >> 10) & 0x3FF) + 0xD800;
                    sb.append((char) H);
                    sb.append((char) L);
                }
            }
        }

        return sb.toString();
    }

    private static int hexDigit(final char ch) {
        final char chu = Character.toUpperCase(ch);
        if (chu >= '0' && chu <= '9') {
            return (chu - '0');
        } else if (chu >= 'A' && chu <= 'F') {
            return (chu - 'A' + 10);
        } else {
            return -1;
        }
    }

    private static int toHexByte(final char ch1, final char ch2) {
        final int i1 = hexDigit(ch1);
        final int i2 = hexDigit(ch2);
        if (i1 >= 0 && i2 >= 0) {
            return (i1 << 4) | i2;
        }
        return -1;
    }

    private static String toHexEscape(final int u0) {
        int u = u0;
        int len;
        final byte[] b = new byte[6];

        if (u <= 0x7f) {
            b[0] = (byte) u;
            len = 1;
        } else {
            // > 0x7ff -> length 2
            // > 0xffff -> length 3
            // and so on. each new length is an additional 5 bits from the
            // original 11
            // the final mask is 8-len zeros in the low part.
            len = 2;
            for (int mask = u >>> 11; mask != 0; mask >>>= 5) {
                len++;
            }
            for (int i = len - 1; i > 0; i--) {
                b[i] = (byte) (0x80 | (u & 0x3f));
                u >>>= 6; // 64 bits per octet.
            }

            b[0] = (byte) (~((1 << (8 - len)) - 1) | u);
        }

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append('%');
            if ((b[i] & 0xff) < 0x10) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(b[i] & 0xff).toUpperCase());
        }

        return sb.toString();
    }

    private static String error(final String string, final int index) {
        throw uriError("bad.uri", string, Integer.toString(index));
    }

    // 'uriEscaped' except for alphanumeric chars
    private static final String URI_UNESCAPED_NONALPHANUMERIC = "-_.!~*'()";
    // 'uriReserved' + '#'
    private static final String URI_RESERVED = ";/?:@&=+$,#";

    private static boolean isUnescaped(final char ch, final boolean component) {
        if (('A' <= ch && ch <= 'Z') || ('a' <= ch && ch <= 'z')
                || ('0' <= ch && ch <= '9')) {
            return true;
        }

        if (URI_UNESCAPED_NONALPHANUMERIC.indexOf(ch) >= 0) {
            return true;
        }

        if (!component) {
            return URI_RESERVED.indexOf(ch) >= 0;
        }

        return false;
    }
}
