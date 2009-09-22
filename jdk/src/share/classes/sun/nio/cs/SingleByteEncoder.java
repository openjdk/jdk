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

/*
 */

package sun.nio.cs;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;
import sun.nio.cs.Surrogate;


public abstract class SingleByteEncoder
    extends CharsetEncoder
{

    private final short index1[];
    private final String index2;
    private final int mask1;
    private final int mask2;
    private final int shift;

    private final Surrogate.Parser sgp = new Surrogate.Parser();

    protected SingleByteEncoder(Charset cs,
                                short[] index1, String index2,
                                int mask1, int mask2, int shift)
    {
        super(cs, 1.0f, 1.0f);
        this.index1 = index1;
        this.index2 = index2;
        this.mask1 = mask1;
        this.mask2 = mask2;
        this.shift = shift;
    }

    public boolean canEncode(char c) {
        char testEncode = index2.charAt(index1[(c & mask1) >> shift]
                                        + (c & mask2));
        return testEncode != '\u0000' || c == '\u0000';
    }

    private CoderResult encodeArrayLoop(CharBuffer src, ByteBuffer dst) {
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
                if (Character.isSurrogate(c)) {
                    if (sgp.parse(c, sa, sp, sl) < 0)
                        return sgp.error();
                    return sgp.unmappableResult();
                }
                if (c >= '\uFFFE')
                    return CoderResult.unmappableForLength(1);
                if (dl - dp < 1)
                    return CoderResult.OVERFLOW;

                char e = index2.charAt(index1[(c & mask1) >> shift]
                                       + (c & mask2));

                // If output byte is zero because input char is zero
                // then character is mappable, o.w. fail
                if (e == '\u0000' && c != '\u0000')
                    return CoderResult.unmappableForLength(1);

                sp++;
                da[dp++] = (byte)e;
            }
            return CoderResult.UNDERFLOW;
        } finally {
            src.position(sp - src.arrayOffset());
            dst.position(dp - dst.arrayOffset());
        }
    }

    private CoderResult encodeBufferLoop(CharBuffer src, ByteBuffer dst) {
        int mark = src.position();
        try {
            while (src.hasRemaining()) {
                char c = src.get();
                if (Character.isSurrogate(c)) {
                    if (sgp.parse(c, src) < 0)
                        return sgp.error();
                    return sgp.unmappableResult();
                }
                if (c >= '\uFFFE')
                    return CoderResult.unmappableForLength(1);
                if (!dst.hasRemaining())
                    return CoderResult.OVERFLOW;

                char e = index2.charAt(index1[(c & mask1) >> shift]
                                       + (c & mask2));

                // If output byte is zero because input char is zero
                // then character is mappable, o.w. fail
                if (e == '\u0000' && c != '\u0000')
                    return CoderResult.unmappableForLength(1);

                mark++;
                dst.put((byte)e);
            }
            return CoderResult.UNDERFLOW;
        } finally {
            src.position(mark);
        }
    }

    protected CoderResult encodeLoop(CharBuffer src, ByteBuffer dst) {
        if (true && src.hasArray() && dst.hasArray())
            return encodeArrayLoop(src, dst);
        else
            return encodeBufferLoop(src, dst);
    }

    public byte encode(char inputChar) {
        return (byte)index2.charAt(index1[(inputChar & mask1) >> shift] +
                (inputChar & mask2));
    }
}
