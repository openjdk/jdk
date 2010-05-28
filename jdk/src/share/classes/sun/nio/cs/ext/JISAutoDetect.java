/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.cs.ext;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.MalformedInputException;
import sun.nio.cs.HistoricallyNamedCharset;
import java.security.AccessController;
import sun.security.action.GetPropertyAction;
import static java.lang.Character.UnicodeBlock;


public class JISAutoDetect
    extends Charset
    implements HistoricallyNamedCharset
{

    private final static int EUCJP_MASK       = 0x01;
    private final static int SJIS2B_MASK      = 0x02;
    private final static int SJIS1B_MASK      = 0x04;
    private final static int EUCJP_KANA1_MASK = 0x08;
    private final static int EUCJP_KANA2_MASK = 0x10;

    public JISAutoDetect() {
        super("x-JISAutoDetect", ExtendedCharsets.aliasesFor("x-JISAutoDetect"));
    }

    public boolean contains(Charset cs) {
        return ((cs.name().equals("US-ASCII"))
                || (cs instanceof SJIS)
                || (cs instanceof EUC_JP)
                || (cs instanceof ISO2022_JP));
    }

    public boolean canEncode() {
        return false;
    }

    public CharsetDecoder newDecoder() {
        return new Decoder(this);
    }

    public String historicalName() {
        return "JISAutoDetect";
    }

    public CharsetEncoder newEncoder() {
        throw new UnsupportedOperationException();
    }

    /**
     * accessor methods used to share byte masking tables
     * with the sun.io JISAutoDetect implementation
     */

    public byte[] getByteMask1() {
        return Decoder.maskTable1;
    }

    public byte[] getByteMask2() {
        return Decoder.maskTable2;
    }

    public final static boolean canBeSJIS1B(int mask) {
        return (mask & SJIS1B_MASK) != 0;
    }

    public final static boolean canBeEUCJP(int mask) {
        return (mask & EUCJP_MASK) != 0;
    }

    public final static boolean canBeEUCKana(int mask1, int mask2) {
        return ((mask1 & EUCJP_KANA1_MASK) != 0)
            && ((mask2 & EUCJP_KANA2_MASK) != 0);
    }

    // A heuristic algorithm for guessing if EUC-decoded text really
    // might be Japanese text.  Better heuristics are possible...
    private static boolean looksLikeJapanese(CharBuffer cb) {
        int hiragana = 0;       // Fullwidth Hiragana
        int katakana = 0;       // Halfwidth Katakana
        while (cb.hasRemaining()) {
            char c = cb.get();
            if (0x3040 <= c && c <= 0x309f && ++hiragana > 1) return true;
            if (0xff65 <= c && c <= 0xff9f && ++katakana > 1) return true;
        }
        return false;
    }

    private static class Decoder extends CharsetDecoder {

        private final static String SJISName = getSJISName();
        private final static String EUCJPName = getEUCJPName();
        private DelegatableDecoder detectedDecoder = null;

        public Decoder(Charset cs) {
            super(cs, 0.5f, 1.0f);
        }

        private static boolean isPlainASCII(byte b) {
            return b >= 0 && b != 0x1b;
        }

        private static void copyLeadingASCII(ByteBuffer src, CharBuffer dst) {
            int start = src.position();
            int limit = start + Math.min(src.remaining(), dst.remaining());
            int p;
            byte b;
            for (p = start; p < limit && isPlainASCII(b = src.get(p)); p++)
                dst.put((char)(b & 0xff));
            src.position(p);
        }

        private CoderResult decodeLoop(Charset cs,
                                       ByteBuffer src, CharBuffer dst) {
            detectedDecoder = (DelegatableDecoder) cs.newDecoder();
            return detectedDecoder.decodeLoop(src, dst);
        }

        protected CoderResult decodeLoop(ByteBuffer src, CharBuffer dst) {
            if (detectedDecoder == null) {
                copyLeadingASCII(src, dst);

                // All ASCII?
                if (! src.hasRemaining())
                    return CoderResult.UNDERFLOW;
                if (! dst.hasRemaining())
                    return CoderResult.OVERFLOW;

                // We need to perform double, not float, arithmetic; otherwise
                // we lose low order bits when src is larger than 2**24.
                int cbufsiz = (int)(src.limit() * (double)maxCharsPerByte());
                CharBuffer sandbox = CharBuffer.allocate(cbufsiz);

                // First try ISO-2022-JP, since there is no ambiguity
                Charset cs2022 = Charset.forName("ISO-2022-JP");
                DelegatableDecoder dd2022
                    = (DelegatableDecoder) cs2022.newDecoder();
                ByteBuffer src2022 = src.asReadOnlyBuffer();
                CoderResult res2022 = dd2022.decodeLoop(src2022, sandbox);
                if (! res2022.isError())
                    return decodeLoop(cs2022, src, dst);

                // We must choose between EUC and SJIS
                Charset csEUCJ = Charset.forName(EUCJPName);
                Charset csSJIS = Charset.forName(SJISName);

                DelegatableDecoder ddEUCJ
                    = (DelegatableDecoder) csEUCJ.newDecoder();
                ByteBuffer srcEUCJ = src.asReadOnlyBuffer();
                sandbox.clear();
                CoderResult resEUCJ = ddEUCJ.decodeLoop(srcEUCJ, sandbox);
                // If EUC decoding fails, must be SJIS
                if (resEUCJ.isError())
                    return decodeLoop(csSJIS, src, dst);

                DelegatableDecoder ddSJIS
                    = (DelegatableDecoder) csSJIS.newDecoder();
                ByteBuffer srcSJIS = src.asReadOnlyBuffer();
                CharBuffer sandboxSJIS = CharBuffer.allocate(cbufsiz);
                CoderResult resSJIS = ddSJIS.decodeLoop(srcSJIS, sandboxSJIS);
                // If SJIS decoding fails, must be EUC
                if (resSJIS.isError())
                    return decodeLoop(csEUCJ, src, dst);

                // From here on, we have some ambiguity, and must guess.

                // We prefer input that does not appear to end mid-character.
                if (srcEUCJ.position() > srcSJIS.position())
                    return decodeLoop(csEUCJ, src, dst);

                if (srcEUCJ.position() < srcSJIS.position())
                    return decodeLoop(csSJIS, src, dst);

                // end-of-input is after the first byte of the first char?
                if (src.position() == srcEUCJ.position())
                    return CoderResult.UNDERFLOW;

                // Use heuristic knowledge of typical Japanese text
                sandbox.flip();
                Charset guess = looksLikeJapanese(sandbox) ? csEUCJ : csSJIS;
                return decodeLoop(guess, src, dst);
            }

            return detectedDecoder.decodeLoop(src, dst);
        }

        protected void implReset() {
            detectedDecoder = null;
        }

        protected CoderResult implFlush(CharBuffer out) {
            if (detectedDecoder != null)
                return detectedDecoder.implFlush(out);
            else
                return super.implFlush(out);
        }

        public boolean isAutoDetecting() {
            return true;
        }

        public boolean isCharsetDetected() {
            return detectedDecoder != null;
        }

        public Charset detectedCharset() {
            if (detectedDecoder == null)
                throw new IllegalStateException("charset not yet detected");
            return ((CharsetDecoder) detectedDecoder).charset();
        }

        /**
         * Returned Shift_JIS Charset name is OS dependent
         */
        private static String getSJISName() {
            String osName = AccessController.doPrivileged(
                new GetPropertyAction("os.name"));
            if (osName.equals("Solaris") || osName.equals("SunOS"))
                return("PCK");
            else if (osName.startsWith("Windows"))
                return("windows-31J");
            else
                return("Shift_JIS");
        }

        /**
         * Returned EUC-JP Charset name is OS dependent
         */

        private static String getEUCJPName() {
            String osName = AccessController.doPrivileged(
                new GetPropertyAction("os.name"));
            if (osName.equals("Solaris") || osName.equals("SunOS"))
                return("x-eucjp-open");
            else
                return("EUC_JP");
        }

        // Mask tables - each entry indicates possibility of first or
        // second byte being SJIS or EUC_JP
        private static final byte maskTable1[] = {
            0, 0, 0, 0, // 0x00 - 0x03
            0, 0, 0, 0, // 0x04 - 0x07
            0, 0, 0, 0, // 0x08 - 0x0b
            0, 0, 0, 0, // 0x0c - 0x0f
            0, 0, 0, 0, // 0x10 - 0x13
            0, 0, 0, 0, // 0x14 - 0x17
            0, 0, 0, 0, // 0x18 - 0x1b
            0, 0, 0, 0, // 0x1c - 0x1f
            0, 0, 0, 0, // 0x20 - 0x23
            0, 0, 0, 0, // 0x24 - 0x27
            0, 0, 0, 0, // 0x28 - 0x2b
            0, 0, 0, 0, // 0x2c - 0x2f
            0, 0, 0, 0, // 0x30 - 0x33
            0, 0, 0, 0, // 0x34 - 0x37
            0, 0, 0, 0, // 0x38 - 0x3b
            0, 0, 0, 0, // 0x3c - 0x3f
            0, 0, 0, 0, // 0x40 - 0x43
            0, 0, 0, 0, // 0x44 - 0x47
            0, 0, 0, 0, // 0x48 - 0x4b
            0, 0, 0, 0, // 0x4c - 0x4f
            0, 0, 0, 0, // 0x50 - 0x53
            0, 0, 0, 0, // 0x54 - 0x57
            0, 0, 0, 0, // 0x58 - 0x5b
            0, 0, 0, 0, // 0x5c - 0x5f
            0, 0, 0, 0, // 0x60 - 0x63
            0, 0, 0, 0, // 0x64 - 0x67
            0, 0, 0, 0, // 0x68 - 0x6b
            0, 0, 0, 0, // 0x6c - 0x6f
            0, 0, 0, 0, // 0x70 - 0x73
            0, 0, 0, 0, // 0x74 - 0x77
            0, 0, 0, 0, // 0x78 - 0x7b
            0, 0, 0, 0, // 0x7c - 0x7f
            0, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK,   // 0x80 - 0x83
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x84 - 0x87
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x88 - 0x8b
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK,   // 0x8c - 0x8f
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x90 - 0x93
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x94 - 0x97
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x98 - 0x9b
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x9c - 0x9f
            0, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,  // 0xa0 - 0xa3
            SJIS1B_MASK|EUCJP_MASK|EUCJP_KANA1_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,    // 0xa4 - 0xa7
            SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,     // 0xa8 - 0xab
            SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,     // 0xac - 0xaf
            SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,     // 0xb0 - 0xb3
            SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,     // 0xb4 - 0xb7
            SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,     // 0xb8 - 0xbb
            SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,     // 0xbc - 0xbf
            SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,     // 0xc0 - 0xc3
            SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,     // 0xc4 - 0xc7
            SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,     // 0xc8 - 0xcb
            SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,     // 0xcc - 0xcf
            SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,     // 0xd0 - 0xd3
            SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,     // 0xd4 - 0xd7
            SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,     // 0xd8 - 0xdb
            SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK, SJIS1B_MASK|EUCJP_MASK,     // 0xdc - 0xdf
            SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK,     // 0xe0 - 0xe3
            SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK,     // 0xe4 - 0xe7
            SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK,     // 0xe8 - 0xeb
            SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK,     // 0xec - 0xef
            SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK,     // 0xf0 - 0xf3
            SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK,     // 0xf4 - 0xf7
            SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK,     // 0xf8 - 0xfb
            SJIS2B_MASK|EUCJP_MASK, EUCJP_MASK, EUCJP_MASK, 0   // 0xfc - 0xff
        };

        private static final byte maskTable2[] = {
            0, 0, 0, 0, // 0x00 - 0x03
            0, 0, 0, 0, // 0x04 - 0x07
            0, 0, 0, 0, // 0x08 - 0x0b
            0, 0, 0, 0, // 0x0c - 0x0f
            0, 0, 0, 0, // 0x10 - 0x13
            0, 0, 0, 0, // 0x14 - 0x17
            0, 0, 0, 0, // 0x18 - 0x1b
            0, 0, 0, 0, // 0x1c - 0x1f
            0, 0, 0, 0, // 0x20 - 0x23
            0, 0, 0, 0, // 0x24 - 0x27
            0, 0, 0, 0, // 0x28 - 0x2b
            0, 0, 0, 0, // 0x2c - 0x2f
            0, 0, 0, 0, // 0x30 - 0x33
            0, 0, 0, 0, // 0x34 - 0x37
            0, 0, 0, 0, // 0x38 - 0x3b
            0, 0, 0, 0, // 0x3c - 0x3f
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x40 - 0x43
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x44 - 0x47
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x48 - 0x4b
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x4c - 0x4f
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x50 - 0x53
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x54 - 0x57
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x58 - 0x5b
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x5c - 0x5f
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x60 - 0x63
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x64 - 0x67
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x68 - 0x6b
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x6c - 0x6f
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x70 - 0x73
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x74 - 0x77
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x78 - 0x7b
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, 0,   // 0x7c - 0x7f
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x80 - 0x83
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x84 - 0x87
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x88 - 0x8b
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x8c - 0x8f
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x90 - 0x93
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x94 - 0x97
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x98 - 0x9b
            SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, SJIS2B_MASK, // 0x9c - 0x9f
            SJIS2B_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xa0 - 0xa3
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xa4 - 0xa7
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xa8 - 0xab
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xac - 0xaf
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xb0 - 0xb3
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xb4 - 0xb7
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xb8 - 0xbb
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xbc - 0xbf
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xc0 - 0xc3
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xc4 - 0xc7
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xc8 - 0xcb
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xcc - 0xcf
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xd0 - 0xd3
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xd4 - 0xd7
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xd8 - 0xdb
            SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS1B_MASK|SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xdc - 0xdf
            SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xe0 - 0xe3
            SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xe4 - 0xe7
            SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xe8 - 0xeb
            SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xec - 0xef
            SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, SJIS2B_MASK|EUCJP_MASK|EUCJP_KANA2_MASK, // 0xf0 - 0xf3
            SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK,     // 0xf4 - 0xf7
            SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK, SJIS2B_MASK|EUCJP_MASK,     // 0xf8 - 0xfb
            SJIS2B_MASK|EUCJP_MASK, EUCJP_MASK, EUCJP_MASK, 0   // 0xfc - 0xff
        };
    }
}
