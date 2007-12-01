/*
 * Copyright 2000-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.cs;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;


public class US_ASCII
    extends Charset
    implements HistoricallyNamedCharset
{

    public US_ASCII() {
        super("US-ASCII", StandardCharsets.aliases_US_ASCII);
    }

    public String historicalName() {
        return "ASCII";
    }

    public boolean contains(Charset cs) {
        return (cs instanceof US_ASCII);
    }

    public CharsetDecoder newDecoder() {
        return new Decoder(this);
    }

    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }

    private static class Decoder extends CharsetDecoder {

        private Decoder(Charset cs) {
            super(cs, 1.0f, 1.0f);
        }

        private CoderResult decodeArrayLoop(ByteBuffer src,
                                            CharBuffer dst)
        {
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
                    byte b = sa[sp];
                    if (b >= 0) {
                        if (dp >= dl)
                            return CoderResult.OVERFLOW;
                        da[dp++] = (char)b;
                        sp++;
                        continue;
                    }
                    return CoderResult.malformedForLength(1);
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(sp - src.arrayOffset());
                dst.position(dp - dst.arrayOffset());
            }
        }

        private CoderResult decodeBufferLoop(ByteBuffer src,
                                             CharBuffer dst)
        {
            int mark = src.position();
            try {
                while (src.hasRemaining()) {
                    byte b = src.get();
                    if (b >= 0) {
                        if (!dst.hasRemaining())
                            return CoderResult.OVERFLOW;
                        dst.put((char)b);
                        mark++;
                        continue;
                    }
                    return CoderResult.malformedForLength(1);
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(mark);
            }
        }

        protected CoderResult decodeLoop(ByteBuffer src,
                                         CharBuffer dst)
        {
            if (src.hasArray() && dst.hasArray())
                return decodeArrayLoop(src, dst);
            else
                return decodeBufferLoop(src, dst);
        }

    }

    private static class Encoder extends CharsetEncoder {

        private Encoder(Charset cs) {
            super(cs, 1.0f, 1.0f);
        }

        public boolean canEncode(char c) {
            return c < 0x80;
        }

        private final Surrogate.Parser sgp = new Surrogate.Parser();

        private CoderResult encodeArrayLoop(CharBuffer src,
                                            ByteBuffer dst)
        {
            char[] sa = src.array();
            int sp = src.arrayOffset() + src.position();
            int sl = src.arrayOffset() + src.limit();
            assert (sp <= sl);
            sp = (sp <= sl ? sp : sl);
            byte[] da = dst.array();
            int dp = dst.arrayOffset() + dst.position();
            int dl = dst.arrayOffset() + dst.limit();
            assert (dp <= dl);
            dp = (dp <= dl ? dp : dl);

            try {
                while (sp < sl) {
                    char c = sa[sp];
                    if (c < 0x80) {
                        if (dp >= dl)
                            return CoderResult.OVERFLOW;
                        da[dp] = (byte)c;
                        sp++; dp++;
                        continue;
                    }
                    if (sgp.parse(c, sa, sp, sl) < 0)
                        return sgp.error();
                    return sgp.unmappableResult();
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(sp - src.arrayOffset());
                dst.position(dp - dst.arrayOffset());
            }
        }

        private CoderResult encodeBufferLoop(CharBuffer src,
                                             ByteBuffer dst)
        {
            int mark = src.position();
            try {
                while (src.hasRemaining()) {
                    char c = src.get();
                    if (c < 0x80) {
                        if (!dst.hasRemaining())
                            return CoderResult.OVERFLOW;
                        dst.put((byte)c);
                        mark++;
                        continue;
                    }
                    if (sgp.parse(c, src) < 0)
                        return sgp.error();
                    return sgp.unmappableResult();
                }
                return CoderResult.UNDERFLOW;
            } finally {
                src.position(mark);
            }
        }

        protected CoderResult encodeLoop(CharBuffer src,
                                         ByteBuffer dst)
        {
            if (src.hasArray() && dst.hasArray())
                return encodeArrayLoop(src, dst);
            else
                return encodeBufferLoop(src, dst);
        }

    }

}
