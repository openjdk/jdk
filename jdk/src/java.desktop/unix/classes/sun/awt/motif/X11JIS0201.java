/*
 * Copyright (c) 1996, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import sun.nio.cs.*;
import sun.nio.cs.ext.JIS_X_0201;
import static sun.nio.cs.CharsetMapping.*;

public class X11JIS0201 extends Charset {

    private static Charset jis0201 = new JIS_X_0201();
    private static SingleByte.Encoder enc =
        (SingleByte.Encoder)jis0201.newEncoder();

    public X11JIS0201 () {
        super("X11JIS0201", null);
    }

    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }

    public CharsetDecoder newDecoder() {
        return jis0201.newDecoder();
    }

    public boolean contains(Charset cs) {
        return cs instanceof X11JIS0201;
    }

    private class Encoder extends CharsetEncoder {

        public Encoder(Charset cs) {
            super(cs, 1.0f, 1.0f);
        }

        public boolean canEncode(char c){
            if ((c >= 0xff61 && c <= 0xff9f)
                || c == 0x203e
                || c == 0xa5) {
                return true;
            }
            return false;
        }

        private Surrogate.Parser sgp;
        protected CoderResult encodeLoop(CharBuffer src, ByteBuffer dst) {
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
            try {
                while (sp < sl) {
                    char c = sa[sp];
                    int b = enc.encode(c);
                    if (b == UNMAPPABLE_ENCODING) {
                        if (Character.isSurrogate(c)) {
                            if (sgp == null)
                                sgp = new Surrogate.Parser();
                            if (sgp.parse(c, sa, sp, sl) >= 0)
                                return CoderResult.unmappableForLength(2);
                        }
                        return CoderResult.unmappableForLength(1);
                    }
                    da[dp++] = (byte)b;
                    sp++;
                }
                return cr;
            } finally {
                src.position(sp - src.arrayOffset());
                dst.position(dp - dst.arrayOffset());
            }
        }
    }
}
