/*
 * Copyright (c) 1996, 2005, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.CharBuffer;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import sun.nio.cs.ext.EUC_CN;

public class X11GB2312_OLD extends Charset {
    public X11GB2312_OLD () {
        super("X11GB2312-OLD", null);
    }
    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }
    public CharsetDecoder newDecoder() {
        return new Decoder(this);
    }

    public boolean contains(Charset cs) {
        return cs instanceof X11GB2312_OLD;
    }

    private class Encoder extends EUC_CN_OLD.Encoder {
        public Encoder(Charset cs) {
            super(cs);
        }

        public boolean canEncode(char c) {
            if (c <= 0x7F) {
                return false;
            }
            return super.canEncode(c);
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
                    if (c <= '\u007f')
                        return CoderResult.unmappableForLength(1);
                    int ncode = encodeDouble(c);
                    if (ncode != 0 && c != '\u0000' ) {
                        da[dp++] = (byte) ((ncode  >> 8) & 0x7f);
                        da[dp++] = (byte) (ncode & 0x7f);
                        sp++;
                        continue;
                    }
                    return CoderResult.unmappableForLength(1);
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(sp - src.arrayOffset());
                dst.position(dp - dst.arrayOffset());
            }
        }
        public boolean isLegalReplacement(byte[] repl) {
            return true;
        }
    }

    private class Decoder extends EUC_CN_OLD.Decoder {
        public Decoder(Charset cs) {
            super(cs);
        }

        protected CoderResult decodeLoop(ByteBuffer src, CharBuffer dst) {
            byte[] sa = src.array();
            int sp = src.arrayOffset() + src.position();
            int sl = src.arrayOffset() + src.limit();
            assert (sp <= sl);
            sp = (sp <= sl ? sp : sl);
            char[] da = dst.array();
            int dp = dst.arrayOffset() + dst.position();
            int dl = dst.arrayOffset() + dst.limit();
            assert (dp <= dl);
            dp = (dp <= dl ? dp : dl);

            try {
                while (sp < sl) {
                    if ( sl - sp < 2) {
                        return CoderResult.UNDERFLOW;
                    }
                    int b1 = sa[sp] & 0xFF | 0x80;
                    int b2 = sa[sp + 1] & 0xFF | 0x80;
                    char c = decodeDouble(b1, b2);
                    if (c == replacement().charAt(0)) {
                        return CoderResult.unmappableForLength(2);
                    }
                    if (dl - dp < 1)
                        return CoderResult.OVERFLOW;
                    da[dp++] = c;
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
