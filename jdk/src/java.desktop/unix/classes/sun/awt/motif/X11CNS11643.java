/*
 * Copyright (c) 2001, 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.motif;

import java.nio.CharBuffer;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import sun.nio.cs.ext.EUC_TW;

public abstract class X11CNS11643 extends Charset {
    private final int plane;
    public X11CNS11643 (int plane, String name) {
        super(name, null);
        switch (plane) {
        case 1:
            this.plane = 0; // CS1
            break;
        case 2:
        case 3:
            this.plane = plane;
            break;
        default:
            throw new IllegalArgumentException
                ("Only planes 1, 2, and 3 supported");
        }
    }

    public CharsetEncoder newEncoder() {
        return new Encoder(this, plane);
    }

    public CharsetDecoder newDecoder() {
        return new Decoder(this, plane);
    }

    public boolean contains(Charset cs) {
        return cs instanceof X11CNS11643;
    }

    private class Encoder extends EUC_TW.Encoder {
        private int plane;
        public Encoder(Charset cs, int plane) {
            super(cs);
            this.plane = plane;
        }

        private byte[] bb = new byte[4];
        public boolean canEncode(char c) {
            if (c <= 0x7F) {
                return false;
            }
            int nb = toEUC(c, bb);
            if (nb == -1)
                return false;
            int p = 0;
            if (nb == 4)
                p = (bb[1] & 0xff) - 0xa0;
            return (p == plane);
        }

        public boolean isLegalReplacement(byte[] repl) {
            return true;
        }

        protected CoderResult encodeLoop(CharBuffer src, ByteBuffer dst) {
            char[] sa = src.array();
            int sp = src.arrayOffset() + src.position();
            int sl = src.arrayOffset() + src.limit();
            byte[] da = dst.array();
            int dp = dst.arrayOffset() + dst.position();
            int dl = dst.arrayOffset() + dst.limit();

            try {
                while (sp < sl) {
                    char c = sa[sp];
                    if ( c > '\u007f'&& c < '\uFFFE') {
                        int nb = toEUC(c, bb);
                        if (nb != -1) {
                            int p = 0;
                            if (nb == 4)
                                p = (bb[1] & 0xff) - 0xa0;
                            if (p == plane) {
                                if (dl - dp < 2)
                                    return CoderResult.OVERFLOW;
                                if (nb == 2) {
                                    da[dp++] = (byte)(bb[0] & 0x7f);
                                    da[dp++] = (byte)(bb[1] & 0x7f);
                                } else {
                                    da[dp++] = (byte)(bb[2] & 0x7f);
                                    da[dp++] = (byte)(bb[3] & 0x7f);
                                }
                                sp++;
                                continue;
                            }
                        }
                    }
                    return CoderResult.unmappableForLength(1);
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(sp - src.arrayOffset());
                dst.position(dp - dst.arrayOffset());
            }
        }
    }

    private class Decoder extends EUC_TW.Decoder {
        int plane;
        private String table;
        protected Decoder(Charset cs, int plane) {
            super(cs);
            if (plane == 0)
                this.plane = plane;
            else if (plane == 2 || plane == 3)
                this.plane = plane - 1;
            else
                throw new IllegalArgumentException
                    ("Only planes 1, 2, and 3 supported");
        }

        //we only work on array backed buffer.
        protected CoderResult decodeLoop(ByteBuffer src, CharBuffer dst) {
            byte[] sa = src.array();
            int sp = src.arrayOffset() + src.position();
            int sl = src.arrayOffset() + src.limit();

            char[] da = dst.array();
            int dp = dst.arrayOffset() + dst.position();
            int dl = dst.arrayOffset() + dst.limit();

            try {
                while (sp < sl) {
                    if ( sl - sp < 2) {
                        return CoderResult.UNDERFLOW;
                    }
                    int b1 = (sa[sp] & 0xff) | 0x80;
                    int b2 = (sa[sp + 1] & 0xff) | 0x80;
                    char[] cc = toUnicode(b1, b2, plane);
                    // plane3 has non-bmp characters(added), x11cnsp3
                    // however does not support them
                    if (cc == null || cc.length == 2)
                        return CoderResult.unmappableForLength(2);
                    if (dl - dp < 1)
                        return CoderResult.OVERFLOW;
                    da[dp++] = cc[0];
                    sp +=2;
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(sp - src.arrayOffset());
                dst.position(dp - dst.arrayOffset());
            }
        }
    }
}
