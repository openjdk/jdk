
/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.cs.ext;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

// EBCDIC DBCS-only Korean
public class IBM834
    extends Charset
{
    public IBM834() {
        super("x-IBM834", ExtendedCharsets.aliasesFor("x-IBM834"));
    }

    public boolean contains(Charset cs) {
        return (cs instanceof IBM834);
    }

    public CharsetDecoder newDecoder() {
        return new Decoder(this);
    }

    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }

    protected static class Decoder extends DBCS_ONLY_IBM_EBCDIC_Decoder {
        public Decoder(Charset cs) {
            super(cs);
            super.mask1 = 0xFFF0;
            super.mask2 = 0x000F;
            super.shift = 4;
            super.index1 = IBM933.getDecoderIndex1();
            super.index2 = IBM933.getDecoderIndex2();
        }
    }

    protected static class Encoder extends IBM933.Encoder {
        public Encoder(Charset cs) {
            super(cs, new byte[] {(byte)0xfe, (byte)0xfe}, false);
        }

        protected CoderResult implFlush(ByteBuffer out) {
            implReset();
            return CoderResult.UNDERFLOW;
        }

        protected byte[] encodeHangul(char ch) {
            byte[] bytes = super.encodeHangul(ch);
            if (bytes.length == 0) {
                // Cp834 has 6 additional non-roundtrip char->bytes
                // mappings, see#6379808
                if (ch == '\u00b7') {
                    return new byte[] {(byte)0x41, (byte)0x43 };
                } else if (ch == '\u00ad') {
                    return new byte[] {(byte)0x41, (byte)0x48 };
                } else if (ch == '\u2015') {
                    return new byte[] {(byte)0x41, (byte)0x49 };
                } else if (ch == '\u223c') {
                    return new byte[] {(byte)0x42, (byte)0xa1 };
                } else if (ch == '\uff5e') {
                    return new byte[] {(byte)0x49, (byte)0x54 };
                } else if (ch == '\u2299') {
                    return new byte[] {(byte)0x49, (byte)0x6f };
                }
            } else if (bytes[0] == 0) {
                return EMPTYBA;
            }
            return bytes;
        }

        public boolean canEncode(char ch) {
            return encodeHangul(ch).length != 0;
        }

        public boolean isLegalReplacement(byte[] repl) {
            if (repl.length == 2 &&
                repl[0] == (byte)0xfe && repl[1] == (byte)0xfe)
                return true;
            return super.isLegalReplacement(repl);
        }

    }
}
