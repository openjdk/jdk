/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.nio.charset.Charset;
import java.util.Arrays;

import static java.lang.String.LATIN1;
import static java.lang.String.UTF16;
import static java.lang.String.COMPACT_STRINGS;
import static java.lang.Character.isSurrogate;
import static java.lang.Character.highSurrogate;
import static java.lang.Character.lowSurrogate;
import static java.lang.Character.isSupplementaryCodePoint;
import static java.lang.StringUTF16.putChar;

class StringDecoderUTF8 extends StringCoding.StringDecoder {

    StringDecoderUTF8(Charset cs, String rcn) {
        super(cs, rcn);
    }

    private static boolean isNotContinuation(int b) {
        return (b & 0xc0) != 0x80;
    }

    private static boolean isMalformed3(int b1, int b2, int b3) {
        return (b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
               (b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80;
    }

    private static boolean isMalformed3_2(int b1, int b2) {
        return (b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
               (b2 & 0xc0) != 0x80;
    }

    private static boolean isMalformed4(int b2, int b3, int b4) {
        return (b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80 ||
               (b4 & 0xc0) != 0x80;
    }

    private static boolean isMalformed4_2(int b1, int b2) {
        return (b1 == 0xf0 && (b2  < 0x90 || b2 > 0xbf)) ||
               (b1 == 0xf4 && (b2 & 0xf0) != 0x80) ||
               (b2 & 0xc0) != 0x80;
    }

    private static boolean isMalformed4_3(int b3) {
        return (b3 & 0xc0) != 0x80;
    }

    // for nb == 3/4
    private static int malformedN(byte[] src, int sp, int nb) {
        if (nb == 3) {
            int b1 = src[sp++];
            int b2 = src[sp++];    // no need to lookup b3
            return ((b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
                    isNotContinuation(b2)) ? 1 : 2;
        } else if (nb == 4) { // we don't care the speed here
            int b1 = src[sp++] & 0xff;
            int b2 = src[sp++] & 0xff;
            if (b1 > 0xf4 ||
                (b1 == 0xf0 && (b2 < 0x90 || b2 > 0xbf)) ||
                (b1 == 0xf4 && (b2 & 0xf0) != 0x80) ||
                isNotContinuation(b2))
                return 1;
            if (isNotContinuation(src[sp++]))
                return 2;
            return 3;
        }
        assert false;
        return -1;
    }

    private static char repl = '\ufffd';

    StringCoding.Result decode(byte[] src, int sp, int len) {
        return decode(src, sp, len, result);
    }

    static StringCoding.Result decode(byte[] src, int sp, int len,
                                      StringCoding.Result ret) {
        int sl = sp + len;
        byte[] dst = new byte[len];
        int dp = 0;
        if (COMPACT_STRINGS) {   // Latin1 only loop
            while (sp < sl) {
                int b1 = src[sp];
                if (b1 >= 0) {
                    dst[dp++] = (byte)b1;
                    sp++;
                    continue;
                }
                if ((b1 == (byte)0xc2 || b1 == (byte)0xc3) &&
                    sp + 1 < sl) {
                    int b2 = src[sp + 1];
                    if (!isNotContinuation(b2)) {
                        dst[dp++] = (byte)(((b1 << 6) ^ b2)^
                                           (((byte) 0xC0 << 6) ^
                                           ((byte) 0x80 << 0)));
                        sp += 2;
                        continue;
                    }
                }
                // anything not a latin1, including the repl
                // we have to go with the utf16
                break;
            }
            if (sp == sl) {
                if (dp != dst.length) {
                    dst = Arrays.copyOf(dst, dp);
                }
                return ret.with(dst, LATIN1);
            }
        }
        if (dp == 0) {
            dst = new byte[len << 1];
        } else {
            byte[] buf = new byte[len << 1];
            StringLatin1.inflate(dst, 0, buf, 0, dp);
            dst = buf;
        }
        while (sp < sl) {
            int b1 = src[sp++];
            if (b1 >= 0) {
                putChar(dst, dp++, (char) b1);
            } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                if (sp < sl) {
                    int b2 = src[sp++];
                    if (isNotContinuation(b2)) {
                        putChar(dst, dp++, repl);
                        sp--;
                    } else {
                        putChar(dst, dp++, (char)(((b1 << 6) ^ b2)^
                                                  (((byte) 0xC0 << 6) ^
                                                  ((byte) 0x80 << 0))));
                    }
                    continue;
                }
                putChar(dst, dp++, repl);
                break;
            } else if ((b1 >> 4) == -2) {
                if (sp + 1 < sl) {
                    int b2 = src[sp++];
                    int b3 = src[sp++];
                    if (isMalformed3(b1, b2, b3)) {
                        putChar(dst, dp++, repl);
                        sp -= 3;
                        sp += malformedN(src, sp, 3);
                    } else {
                        char c = (char)((b1 << 12) ^
                                        (b2 <<  6) ^
                                        (b3 ^
                                         (((byte) 0xE0 << 12) ^
                                         ((byte) 0x80 <<  6) ^
                                         ((byte) 0x80 <<  0))));
                        putChar(dst, dp++, isSurrogate(c) ?  repl : c);
                    }
                    continue;
                }
                if (sp  < sl && isMalformed3_2(b1, src[sp])) {
                    putChar(dst, dp++, repl);
                    continue;
                }
                putChar(dst, dp++, repl);
                break;
            } else if ((b1 >> 3) == -2) {
                if (sp + 2 < sl) {
                    int b2 = src[sp++];
                    int b3 = src[sp++];
                    int b4 = src[sp++];
                    int uc = ((b1 << 18) ^
                              (b2 << 12) ^
                              (b3 <<  6) ^
                              (b4 ^
                               (((byte) 0xF0 << 18) ^
                               ((byte) 0x80 << 12) ^
                               ((byte) 0x80 <<  6) ^
                               ((byte) 0x80 <<  0))));
                    if (isMalformed4(b2, b3, b4) ||
                        !isSupplementaryCodePoint(uc)) { // shortest form check
                        putChar(dst, dp++, repl);
                        sp -= 4;
                        sp += malformedN(src, sp, 4);
                    } else {
                        putChar(dst, dp++, highSurrogate(uc));
                        putChar(dst, dp++, lowSurrogate(uc));
                    }
                    continue;
                }
                b1 &= 0xff;
                if (b1 > 0xf4 ||
                    sp  < sl && isMalformed4_2(b1, src[sp] & 0xff)) {
                    putChar(dst, dp++, repl);
                    continue;
                }
                sp++;
                putChar(dst, dp++, repl);
                if (sp  < sl && isMalformed4_3(src[sp])) {
                    continue;
                }
                break;
            } else {
                putChar(dst, dp++, repl);
            }
        }
        if (dp != len) {
            dst = Arrays.copyOf(dst, dp << 1);
        }
        return ret.with(dst, UTF16);
    }
}
