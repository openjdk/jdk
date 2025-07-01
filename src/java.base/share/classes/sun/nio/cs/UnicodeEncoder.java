/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Alibaba Group Holding Limited. All Rights Reserved.
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

import java.nio.*;
import java.nio.charset.*;

/**
 * Base class for different flavors of UTF-16 encoders
 */
public abstract class UnicodeEncoder extends CharsetEncoder {
    protected static final char BYTE_ORDER_MARK = '\uFEFF';

    protected static final int BIG = 0;
    protected static final int LITTLE = 1;

    private int byteOrder;      /* Byte order in use */
    private boolean usesMark;   /* Write an initial BOM */
    private boolean needsMark;

    protected UnicodeEncoder(Charset cs, int bo, boolean m) {
        super(cs, 2.0f,
              // Four bytes max if you need a BOM
              m ? 4.0f : 2.0f,
              // Replacement depends upon byte order
              ((bo == BIG)
               ? new byte[] { (byte)0xff, (byte)0xfd }
               : new byte[] { (byte)0xfd, (byte)0xff }));
        usesMark = needsMark = m;
        byteOrder = bo;
    }

    private static void putChar(byte[] ba, int off, char c, boolean big) {
        if (big) {
            ba[off    ] = (byte)(c >> 8);
            ba[off + 1] = (byte)(c & 0xff);
        } else {
            ba[off    ] = (byte)(c & 0xff);
            ba[off + 1] = (byte)(c >> 8);
        }
    }

    private final Surrogate.Parser sgp = new Surrogate.Parser();

    protected CoderResult encodeLoop(CharBuffer src, ByteBuffer dst) {
        if (src.hasArray() && dst.hasArray()) {
            return encodeArrayLoop(src, dst);
        }
        return encodeBufferLoop(src, dst);
    }

    private CoderResult encodeArrayLoop(CharBuffer src, ByteBuffer dst) {
        char[] sa = src.array();
        int soff = src.arrayOffset();
        int sp = soff + src.position();
        int sl = soff + src.limit();

        byte[] da = dst.array();
        int doff = dst.arrayOffset();
        int dp = doff + dst.position();
        int dl = doff + dst.limit();

        boolean big = byteOrder == BIG;

        try {
            if (needsMark && sp < sl) {
                if (dl - dp < 2)
                    return CoderResult.OVERFLOW;
                putChar(da, dp, BYTE_ORDER_MARK, big);
                dp += 2;
                needsMark = false;
            }

            while (sp < sl) {
                char c = sa[sp];
                if (!Character.isSurrogate(c)) {
                    if (dl - dp < 2)
                        return CoderResult.OVERFLOW;
                    sp++;
                    putChar(da, dp, c, big);
                    dp += 2;
                    continue;
                }
                int d = sgp.parse(c, sa, sp, sl);
                if (d < 0)
                    return sgp.error();
                if (dl - dp < 4)
                    return CoderResult.OVERFLOW;
                sp += 2;
                putChar(da, dp    , Character.highSurrogate(d), big);
                putChar(da, dp + 2, Character.lowSurrogate(d) , big);
                dp += 4;
            }
            return CoderResult.UNDERFLOW;
        } finally {
            src.position(sp - soff);
            dst.position(dp - doff);
        }
    }

    private static char convEndian(boolean nativeOrder, char c) {
        return nativeOrder ? c : Character.reverseBytes(c);
    }

    private CoderResult encodeBufferLoop(CharBuffer src, ByteBuffer dst) {
        int mark = src.position();
        boolean nativeOrder = (byteOrder == BIG) == (dst.order() == ByteOrder.BIG_ENDIAN);

        if (needsMark && src.hasRemaining()) {
            if (dst.remaining() < 2)
                return CoderResult.OVERFLOW;
            dst.putChar(convEndian(nativeOrder, BYTE_ORDER_MARK));
            needsMark = false;
        }
        try {
            while (src.hasRemaining()) {
                char c = src.get();
                if (!Character.isSurrogate(c)) {
                    if (dst.remaining() < 2)
                        return CoderResult.OVERFLOW;
                    mark++;
                    dst.putChar(convEndian(nativeOrder, c));
                    continue;
                }
                int d = sgp.parse(c, src);
                if (d < 0)
                    return sgp.error();
                if (dst.remaining() < 4)
                    return CoderResult.OVERFLOW;
                mark += 2;
                dst.putChar(convEndian(nativeOrder, Character.highSurrogate(d)));
                dst.putChar(convEndian(nativeOrder, Character.lowSurrogate(d)));
            }
            return CoderResult.UNDERFLOW;
        } finally {
            src.position(mark);
        }
    }

    protected void implReset() {
        needsMark = usesMark;
    }

    public boolean canEncode(char c) {
        return ! Character.isSurrogate(c);
    }
}
