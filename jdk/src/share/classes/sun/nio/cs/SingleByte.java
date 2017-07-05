/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.cs;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.Arrays;
import static sun.nio.cs.CharsetMapping.*;

public class SingleByte
{
    private static final CoderResult withResult(CoderResult cr,
                                                Buffer src, int sp,
                                                Buffer dst, int dp)
    {
        src.position(sp - src.arrayOffset());
        dst.position(dp - dst.arrayOffset());
        return cr;
    }

    final public static class Decoder extends CharsetDecoder
                                      implements ArrayDecoder {
        private final char[] b2c;

        public Decoder(Charset cs, char[] b2c) {
            super(cs, 1.0f, 1.0f);
            this.b2c = b2c;
        }

        private CoderResult decodeArrayLoop(ByteBuffer src, CharBuffer dst) {
            byte[] sa = src.array();
            int sp = src.arrayOffset() + src.position();
            int sl = src.arrayOffset() + src.limit();

            char[] da = dst.array();
            int dp = dst.arrayOffset() + dst.position();
            int dl = dst.arrayOffset() + dst.limit();

            CoderResult cr = CoderResult.UNDERFLOW;
            if ((dl - dp) < (sl - sp)) {
                sl = sp + (dl - dp);
                cr = CoderResult.OVERFLOW;
            }

            while (sp < sl) {
                char c = decode(sa[sp]);
                if (c == UNMAPPABLE_DECODING) {
                    return withResult(CoderResult.unmappableForLength(1),
                               src, sp, dst, dp);
                }
                da[dp++] = c;
                sp++;
            }
            return withResult(cr, src, sp, dst, dp);
        }

        private CoderResult decodeBufferLoop(ByteBuffer src, CharBuffer dst) {
            int mark = src.position();
            try {
                while (src.hasRemaining()) {
                    char c = decode(src.get());
                    if (c == UNMAPPABLE_DECODING)
                        return CoderResult.unmappableForLength(1);
                    if (!dst.hasRemaining())
                        return CoderResult.OVERFLOW;
                    dst.put(c);
                    mark++;
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(mark);
            }
        }

        protected CoderResult decodeLoop(ByteBuffer src, CharBuffer dst) {
            if (src.hasArray() && dst.hasArray())
                return decodeArrayLoop(src, dst);
            else
                return decodeBufferLoop(src, dst);
        }

        private final char decode(int b) {
            return b2c[b + 128];
        }

        private char repl = '\uFFFD';
        protected void implReplaceWith(String newReplacement) {
            repl = newReplacement.charAt(0);
        }

        public int decode(byte[] src, int sp, int len, char[] dst) {
            if (len > dst.length)
                len = dst.length;
            int dp = 0;
            while (dp < len) {
                dst[dp] = decode(src[sp++]);
                if (dst[dp] == UNMAPPABLE_DECODING) {
                    dst[dp] = repl;
                }
                dp++;
            }
            return dp;
        }
    }

    final public static class Encoder extends CharsetEncoder
                                      implements ArrayEncoder {
        private Surrogate.Parser sgp;
        private final char[] c2b;
        private final char[] c2bIndex;

        public Encoder(Charset cs, char[] c2b, char[] c2bIndex) {
            super(cs, 1.0f, 1.0f);
            this.c2b = c2b;
            this.c2bIndex = c2bIndex;
        }

        public boolean canEncode(char c) {
            return encode(c) != UNMAPPABLE_ENCODING;
        }

        public boolean isLegalReplacement(byte[] repl) {
            return ((repl.length == 1 && repl[0] == (byte)'?') ||
                    super.isLegalReplacement(repl));
        }

        private CoderResult encodeArrayLoop(CharBuffer src, ByteBuffer dst) {
            char[] sa = src.array();
            int sp = src.arrayOffset() + src.position();
            int sl = src.arrayOffset() + src.limit();

            byte[] da = dst.array();
            int dp = dst.arrayOffset() + dst.position();
            int dl = dst.arrayOffset() + dst.limit();

            CoderResult cr = CoderResult.UNDERFLOW;
            if ((dl - dp) < (sl - sp)) {
                sl = sp + (dl - dp);
                cr = CoderResult.OVERFLOW;
            }

            while (sp < sl) {
                char c = sa[sp];
                int b = encode(c);
                if (b == UNMAPPABLE_ENCODING) {
                    if (Character.isSurrogate(c)) {
                        if (sgp == null)
                            sgp = new Surrogate.Parser();
                        if (sgp.parse(c, sa, sp, sl) < 0)
                            return withResult(sgp.error(), src, sp, dst, dp);
                        return withResult(sgp.unmappableResult(), src, sp, dst, dp);
                    }
                    return withResult(CoderResult.unmappableForLength(1),
                               src, sp, dst, dp);
                }
                da[dp++] = (byte)b;
                sp++;
            }
            return withResult(cr, src, sp, dst, dp);
        }

        private CoderResult encodeBufferLoop(CharBuffer src, ByteBuffer dst) {
            int mark = src.position();
            try {
                while (src.hasRemaining()) {
                    char c = src.get();
                    int b = encode(c);
                    if (b == UNMAPPABLE_ENCODING) {
                        if (Character.isSurrogate(c)) {
                            if (sgp == null)
                                sgp = new Surrogate.Parser();
                            if (sgp.parse(c, src) < 0)
                                return sgp.error();
                            return sgp.unmappableResult();
                        }
                        return CoderResult.unmappableForLength(1);
                    }
                    if (!dst.hasRemaining())
                        return CoderResult.OVERFLOW;
                    dst.put((byte)b);
                    mark++;
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(mark);
            }
        }

        protected CoderResult encodeLoop(CharBuffer src, ByteBuffer dst) {
            if (src.hasArray() && dst.hasArray())
                return encodeArrayLoop(src, dst);
            else
                return encodeBufferLoop(src, dst);
        }

        private final int encode(char ch) {
            char index = c2bIndex[ch >> 8];
            if (index == UNMAPPABLE_ENCODING)
                return UNMAPPABLE_ENCODING;
            return c2b[index + (ch & 0xff)];
        }

        private byte repl = (byte)'?';
        protected void implReplaceWith(byte[] newReplacement) {
            repl = newReplacement[0];
        }

        public int encode(char[] src, int sp, int len, byte[] dst) {
            int dp = 0;
            int sl = sp + Math.min(len, dst.length);
            while (sp < sl) {
                char c = src[sp++];
                int b = encode(c);
                if (b != UNMAPPABLE_ENCODING) {
                    dst[dp++] = (byte)b;
                    continue;
                }
                if (Character.isHighSurrogate(c) && sp < sl &&
                    Character.isLowSurrogate(src[sp])) {
                    if (len > dst.length) {
                        sl++;
                        len--;
                    }
                    sp++;
                }
                dst[dp++] = repl;
            }
            return dp;
        }
    }

    // init the c2b and c2bIndex tables from b2c.
    public static void initC2B(char[] b2c, char[] c2bNR,
                               char[] c2b, char[] c2bIndex) {
        for (int i = 0; i < c2bIndex.length; i++)
            c2bIndex[i] = UNMAPPABLE_ENCODING;
        for (int i = 0; i < c2b.length; i++)
            c2b[i] = UNMAPPABLE_ENCODING;
        int off = 0;
        for (int i = 0; i < b2c.length; i++) {
            char c = b2c[i];
            if (c == UNMAPPABLE_DECODING)
                continue;
            int index = (c >> 8);
            if (c2bIndex[index] == UNMAPPABLE_ENCODING) {
                c2bIndex[index] = (char)off;
                off += 0x100;
            }
            index = c2bIndex[index] + (c & 0xff);
            c2b[index] = (char)((i>=0x80)?(i-0x80):(i+0x80));
        }
        if (c2bNR != null) {
            // c-->b nr entries
            int i = 0;
            while (i < c2bNR.length) {
                char b = c2bNR[i++];
                char c = c2bNR[i++];
                int index = (c >> 8);
                if (c2bIndex[index] == UNMAPPABLE_ENCODING) {
                    c2bIndex[index] = (char)off;
                    off += 0x100;
                }
                index = c2bIndex[index] + (c & 0xff);
                c2b[index] = b;
            }
        }
    }
}
