/*
 * Copyright (c) 2000, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.UnsupportedEncodingException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.Objects;

import jdk.internal.vm.annotation.IntrinsicCandidate;
import sun.nio.cs.HistoricallyNamedCharset;
import sun.nio.cs.ArrayEncoder;

import static java.lang.String.LATIN1;
import static java.lang.String.UTF16;
import static java.lang.StringUTF16.putChar;

/**
 * Utility class for string encoding and decoding.
 */

class StringCoding {

    private StringCoding() { }

    /** The cached coders for each thread */
    private static final ThreadLocal<SoftReference<StringEncoder>> encoder =
        new ThreadLocal<>();

    static final Charset ISO_8859_1 = sun.nio.cs.ISO_8859_1.INSTANCE;
    static final Charset US_ASCII = sun.nio.cs.US_ASCII.INSTANCE;
    static final Charset UTF_8 = sun.nio.cs.UTF_8.INSTANCE;

    static final char REPL = '\ufffd';

    private static <T> T deref(ThreadLocal<SoftReference<T>> tl) {
        SoftReference<T> sr = tl.get();
        if (sr == null)
            return null;
        return sr.get();
    }

    private static <T> void set(ThreadLocal<SoftReference<T>> tl, T ob) {
        tl.set(new SoftReference<>(ob));
    }

    // Trim the given byte array to the given length
    private static byte[] safeTrim(byte[] ba, int len, boolean isTrusted) {
        if (len == ba.length && (isTrusted || System.getSecurityManager() == null))
            return ba;
        else
            return Arrays.copyOf(ba, len);
    }

    static int scale(int len, float expansionFactor) {
        // We need to perform double, not float, arithmetic; otherwise
        // we lose low order bits when len is larger than 2**24.
        return (int)(len * (double)expansionFactor);
    }

    static Charset lookupCharset(String csn) throws UnsupportedEncodingException {
        Objects.requireNonNull(csn);
        try {
            return Charset.forName(csn);
        } catch (UnsupportedCharsetException | IllegalCharsetNameException x) {
            throw new UnsupportedEncodingException(csn);
        }
    }

    @IntrinsicCandidate
    public static boolean hasNegatives(byte[] ba, int off, int len) {
        for (int i = off; i < off + len; i++) {
            if (ba[i] < 0) {
                return true;
            }
        }
        return false;
    }

    // -- Encoding --
    private static class StringEncoder {
        private Charset cs;
        private CharsetEncoder ce;
        private final boolean isASCIICompatible;
        private final String requestedCharsetName;
        private final boolean isTrusted;

        private StringEncoder(Charset cs, String rcn) {
            this.requestedCharsetName = rcn;
            this.cs = cs;
            this.ce = cs.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
            this.isTrusted = (cs.getClass().getClassLoader0() == null);
            this.isASCIICompatible = (ce instanceof ArrayEncoder) &&
                    ((ArrayEncoder)ce).isASCIICompatible();
        }

        String charsetName() {
            if (cs instanceof HistoricallyNamedCharset)
                return ((HistoricallyNamedCharset)cs).historicalName();
            return cs.name();
        }

        final String requestedCharsetName() {
            return requestedCharsetName;
        }

        byte[] encode(byte coder, byte[] val) {
            // fastpath for ascii compatible
            if (coder == LATIN1 && isASCIICompatible &&
                !hasNegatives(val, 0, val.length)) {
                return Arrays.copyOf(val, val.length);
            }
            int len = val.length >> coder;  // assume LATIN1=0/UTF16=1;
            int en = scale(len, ce.maxBytesPerChar());
            byte[] ba = new byte[en];
            if (len == 0) {
                return ba;
            }
            if (ce instanceof ArrayEncoder) {
                int blen = (coder == LATIN1 ) ? ((ArrayEncoder)ce).encodeFromLatin1(val, 0, len, ba)
                                              : ((ArrayEncoder)ce).encodeFromUTF16(val, 0, len, ba);
                if (blen != -1) {
                    return safeTrim(ba, blen, isTrusted);
                }
            }
            char[] ca = (coder == LATIN1 ) ? StringLatin1.toChars(val)
                                           : StringUTF16.toChars(val);
            ce.reset();
            ByteBuffer bb = ByteBuffer.wrap(ba);
            CharBuffer cb = CharBuffer.wrap(ca, 0, len);
            try {
                CoderResult cr = ce.encode(cb, bb, true);
                if (!cr.isUnderflow())
                    cr.throwException();
                cr = ce.flush(bb);
                if (!cr.isUnderflow())
                    cr.throwException();
            } catch (CharacterCodingException x) {
                // Substitution is always enabled,
                // so this shouldn't happen
                throw new Error(x);
            }
            return safeTrim(ba, bb.position(), isTrusted);
        }
    }

    static byte[] encode(String csn, byte coder, byte[] val)
        throws UnsupportedEncodingException
    {
        StringEncoder se = deref(encoder);
        if ((se == null) || !(csn.equals(se.requestedCharsetName())
                              || csn.equals(se.charsetName()))) {
            Charset cs = lookupCharset(csn);
            if (cs == UTF_8) {
                return encodeUTF8(coder, val, true);
            }
            if (cs == ISO_8859_1) {
                return encode8859_1(coder, val);
            }
            if (cs == US_ASCII) {
                return encodeASCII(coder, val);
            }
            se = new StringEncoder(cs, csn);
            set(encoder, se);
        }
        return se.encode(coder, val);
    }

    static byte[] encode(Charset cs, byte coder, byte[] val) {
        if (cs == UTF_8) {
            return encodeUTF8(coder, val, true);
        }
        if (cs == ISO_8859_1) {
            return encode8859_1(coder, val);
        }
        if (cs == US_ASCII) {
            return encodeASCII(coder, val);
        }
        CharsetEncoder ce = cs.newEncoder();
        // fastpath for ascii compatible
        if (coder == LATIN1 && (((ce instanceof ArrayEncoder) &&
                                 ((ArrayEncoder)ce).isASCIICompatible() &&
                                 !hasNegatives(val, 0, val.length)))) {
            return Arrays.copyOf(val, val.length);
        }
        int len = val.length >> coder;  // assume LATIN1=0/UTF16=1;
        int en = scale(len, ce.maxBytesPerChar());
        byte[] ba = new byte[en];
        if (len == 0) {
            return ba;
        }
        ce.onMalformedInput(CodingErrorAction.REPLACE)
          .onUnmappableCharacter(CodingErrorAction.REPLACE)
          .reset();
        if (ce instanceof ArrayEncoder) {
            int blen = (coder == LATIN1 ) ? ((ArrayEncoder)ce).encodeFromLatin1(val, 0, len, ba)
                                          : ((ArrayEncoder)ce).encodeFromUTF16(val, 0, len, ba);
            if (blen != -1) {
                return safeTrim(ba, blen, true);
            }
        }
        boolean isTrusted = cs.getClass().getClassLoader0() == null ||
                            System.getSecurityManager() == null;
        char[] ca = (coder == LATIN1 ) ? StringLatin1.toChars(val)
                                       : StringUTF16.toChars(val);
        ByteBuffer bb = ByteBuffer.wrap(ba);
        CharBuffer cb = CharBuffer.wrap(ca, 0, len);
        try {
            CoderResult cr = ce.encode(cb, bb, true);
            if (!cr.isUnderflow())
                cr.throwException();
            cr = ce.flush(bb);
            if (!cr.isUnderflow())
                cr.throwException();
        } catch (CharacterCodingException x) {
            throw new Error(x);
        }
        return safeTrim(ba, bb.position(), isTrusted);
    }

    static byte[] encode(byte coder, byte[] val) {
        Charset cs = Charset.defaultCharset();
        if (cs == UTF_8) {
            return encodeUTF8(coder, val, true);
        }
        if (cs == ISO_8859_1) {
            return encode8859_1(coder, val);
        }
        if (cs == US_ASCII) {
            return encodeASCII(coder, val);
        }
        StringEncoder se = deref(encoder);
        if (se == null || !cs.name().equals(se.cs.name())) {
            se = new StringEncoder(cs, cs.name());
            set(encoder, se);
        }
        return se.encode(coder, val);
    }

    /**
     *  Print a message directly to stderr, bypassing all character conversion
     *  methods.
     *  @param msg  message to print
     */
    private static native void err(String msg);

    private static byte[] encodeASCII(byte coder, byte[] val) {
        if (coder == LATIN1) {
            byte[] dst = new byte[val.length];
            for (int i = 0; i < val.length; i++) {
                if (val[i] < 0) {
                    dst[i] = '?';
                } else {
                    dst[i] = val[i];
                }
            }
            return dst;
        }
        int len = val.length >> 1;
        byte[] dst = new byte[len];
        int dp = 0;
        for (int i = 0; i < len; i++) {
            char c = StringUTF16.getChar(val, i);
            if (c < 0x80) {
                dst[dp++] = (byte)c;
                continue;
            }
            if (Character.isHighSurrogate(c) && i + 1 < len &&
                Character.isLowSurrogate(StringUTF16.getChar(val, i + 1))) {
                i++;
            }
            dst[dp++] = '?';
        }
        if (len == dp) {
            return dst;
        }
        return Arrays.copyOf(dst, dp);
    }

    @IntrinsicCandidate
    private static int implEncodeISOArray(byte[] sa, int sp,
                                          byte[] da, int dp, int len) {
        int i = 0;
        for (; i < len; i++) {
            char c = StringUTF16.getChar(sa, sp++);
            if (c > '\u00FF')
                break;
            da[dp++] = (byte)c;
        }
        return i;
    }

    private static byte[] encode8859_1(byte coder, byte[] val) {
        return encode8859_1(coder, val, true);
    }

    private static byte[] encode8859_1(byte coder, byte[] val, boolean doReplace) {
        if (coder == LATIN1) {
            return Arrays.copyOf(val, val.length);
        }
        int len = val.length >> 1;
        byte[] dst = new byte[len];
        int dp = 0;
        int sp = 0;
        int sl = len;
        while (sp < sl) {
            int ret = implEncodeISOArray(val, sp, dst, dp, len);
            sp = sp + ret;
            dp = dp + ret;
            if (ret != len) {
                if (!doReplace) {
                    throwUnmappable(sp, 1);
                }
                char c = StringUTF16.getChar(val, sp++);
                if (Character.isHighSurrogate(c) && sp < sl &&
                    Character.isLowSurrogate(StringUTF16.getChar(val, sp))) {
                    sp++;
                }
                dst[dp++] = '?';
                len = sl - sp;
            }
        }
        if (dp == dst.length) {
            return dst;
        }
        return Arrays.copyOf(dst, dp);
    }

    //////////////////////////////// utf8 ////////////////////////////////////

    static boolean isNotContinuation(int b) {
        return (b & 0xc0) != 0x80;
    }

    static boolean isMalformed3(int b1, int b2, int b3) {
        return (b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
               (b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80;
    }

    static boolean isMalformed3_2(int b1, int b2) {
        return (b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
               (b2 & 0xc0) != 0x80;
    }

    static boolean isMalformed4(int b2, int b3, int b4) {
        return (b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80 ||
               (b4 & 0xc0) != 0x80;
    }

    static boolean isMalformed4_2(int b1, int b2) {
        return (b1 == 0xf0 && (b2  < 0x90 || b2 > 0xbf)) ||
               (b1 == 0xf4 && (b2 & 0xf0) != 0x80) ||
               (b2 & 0xc0) != 0x80;
    }

    static boolean isMalformed4_3(int b3) {
        return (b3 & 0xc0) != 0x80;
    }

    static char decode2(int b1, int b2) {
        return (char)(((b1 << 6) ^ b2)^
                (((byte) 0xC0 << 6) ^
                ((byte) 0x80 << 0)));
    }

    static char decode3(int b1, int b2, int b3) {
        return (char)((b1 << 12) ^
                        (b2 <<  6) ^
                        (b3 ^
                         (((byte) 0xE0 << 12) ^
                          ((byte) 0x80 <<  6) ^
                          ((byte) 0x80 <<  0))));
    }

    static int decode4(int b1, int b2, int b3, int b4) {
        return ((b1 << 18) ^
                (b2 << 12) ^
                (b3 <<  6) ^
                (b4 ^
                 (((byte) 0xF0 << 18) ^
                  ((byte) 0x80 << 12) ^
                  ((byte) 0x80 <<  6) ^
                  ((byte) 0x80 <<  0))));
    }

    static int decodeUTF8_UTF16(byte[] bytes, int offset, int sl, byte[] dst, int dp, boolean doReplace) {
        while (offset < sl) {
            int b1 = bytes[offset++];
            if (b1 >= 0) {
                putChar(dst, dp++, (char) b1);
            } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                if (offset < sl) {
                    int b2 = bytes[offset++];
                    if (StringCoding.isNotContinuation(b2)) {
                        if (!doReplace) {
                            throwMalformed(offset - 1, 1);
                        }
                        putChar(dst, dp++, REPL);
                        offset--;
                    } else {
                        putChar(dst, dp++, decode2(b1, b2));
                    }
                    continue;
                }
                if (!doReplace) {
                    throwMalformed(offset, 1);  // underflow()
                }
                putChar(dst, dp++, REPL);
                break;
            } else if ((b1 >> 4) == -2) {
                if (offset + 1 < sl) {
                    int b2 = bytes[offset++];
                    int b3 = bytes[offset++];
                    if (isMalformed3(b1, b2, b3)) {
                        if (!doReplace) {
                            throwMalformed(offset - 3, 3);
                        }
                        putChar(dst, dp++, REPL);
                        offset -= 3;
                        offset += malformedN(bytes, offset, 3);
                    } else {
                        char c = decode3(b1, b2, b3);
                        if (Character.isSurrogate(c)) {
                            if (!doReplace) {
                                throwMalformed(offset - 3, 3);
                            }
                            putChar(dst, dp++, REPL);
                        } else {
                            putChar(dst, dp++, c);
                        }
                    }
                    continue;
                }
                if (offset < sl && isMalformed3_2(b1, bytes[offset])) {
                    if (!doReplace) {
                        throwMalformed(offset - 1, 2);
                    }
                    putChar(dst, dp++, REPL);
                    continue;
                }
                if (!doReplace) {
                    throwMalformed(offset, 1);
                }
                putChar(dst, dp++, REPL);
                break;
            } else if ((b1 >> 3) == -2) {
                if (offset + 2 < sl) {
                    int b2 = bytes[offset++];
                    int b3 = bytes[offset++];
                    int b4 = bytes[offset++];
                    int uc = decode4(b1, b2, b3, b4);
                    if (isMalformed4(b2, b3, b4) ||
                            !Character.isSupplementaryCodePoint(uc)) { // shortest form check
                        if (!doReplace) {
                            throwMalformed(offset - 4, 4);
                        }
                        putChar(dst, dp++, REPL);
                        offset -= 4;
                        offset += StringCoding.malformedN(bytes, offset, 4);
                    } else {
                        putChar(dst, dp++, Character.highSurrogate(uc));
                        putChar(dst, dp++, Character.lowSurrogate(uc));
                    }
                    continue;
                }
                b1 &= 0xff;
                if (b1 > 0xf4 ||
                        offset  < sl && StringCoding.isMalformed4_2(b1, bytes[offset] & 0xff)) {
                    if (!doReplace) {
                        throwMalformed(offset - 1, 1);  // or 2
                    }
                    putChar(dst, dp++, REPL);
                    continue;
                }
                if (!doReplace) {
                    throwMalformed(offset - 1, 1);
                }
                offset++;
                putChar(dst, dp++, REPL);
                if (offset < sl && StringCoding.isMalformed4_3(bytes[offset])) {
                    continue;
                }
                break;
            } else {
                if (!doReplace) {
                    throwMalformed(offset - 1, 1);
                }
                putChar(dst, dp++, REPL);
            }
        }
        return dp;
    }

    static int decodeWithDecoder(CharsetDecoder cd, char[] dst, byte[] src, int offset, int length) {
        ByteBuffer bb = ByteBuffer.wrap(src, offset, length);
        CharBuffer cb = CharBuffer.wrap(dst, 0, dst.length);
        try {
            CoderResult cr = cd.decode(bb, cb, true);
            if (!cr.isUnderflow())
                cr.throwException();
            cr = cd.flush(cb);
            if (!cr.isUnderflow())
                cr.throwException();
        } catch (CharacterCodingException x) {
            // Substitution is always enabled,
            // so this shouldn't happen
            throw new Error(x);
        }
        return cb.position();
    }

    // for nb == 3/4
    static int malformedN(byte[] src, int sp, int nb) {
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

    static void throwMalformed(int off, int nb) {
        String msg = "malformed input off : " + off + ", length : " + nb;
        throw new IllegalArgumentException(msg, new MalformedInputException(nb));
    }

    static void throwMalformed(byte[] val) {
        int dp = 0;
        while (dp < val.length && val[dp] >=0) { dp++; }
        throwMalformed(dp, 1);
    }

    static void throwUnmappable(int off, int nb) {
        String msg = "malformed input off : " + off + ", length : " + nb;
        throw new IllegalArgumentException(msg, new UnmappableCharacterException(nb));
    }

    static void throwUnmappable(byte[] val) {
        int dp = 0;
        while (dp < val.length && val[dp] >=0) { dp++; }
        throwUnmappable(dp, 1);
    }

    private static byte[] encodeUTF8(byte coder, byte[] val, boolean doReplace) {
        if (coder == UTF16)
            return encodeUTF8_UTF16(val, doReplace);

        if (!hasNegatives(val, 0, val.length))
            return Arrays.copyOf(val, val.length);

        int dp = 0;
        byte[] dst = new byte[val.length << 1];
        for (int sp = 0; sp < val.length; sp++) {
            byte c = val[sp];
            if (c < 0) {
                dst[dp++] = (byte)(0xc0 | ((c & 0xff) >> 6));
                dst[dp++] = (byte)(0x80 | (c & 0x3f));
            } else {
                dst[dp++] = c;
            }
        }
        if (dp == dst.length)
            return dst;
        return Arrays.copyOf(dst, dp);
    }

    private static byte[] encodeUTF8_UTF16(byte[] val, boolean doReplace) {
        int dp = 0;
        int sp = 0;
        int sl = val.length >> 1;
        byte[] dst = new byte[sl * 3];
        char c;
        while (sp < sl && (c = StringUTF16.getChar(val, sp)) < '\u0080') {
            // ascii fast loop;
            dst[dp++] = (byte)c;
            sp++;
        }
        while (sp < sl) {
            c = StringUTF16.getChar(val, sp++);
            if (c < 0x80) {
                dst[dp++] = (byte)c;
            } else if (c < 0x800) {
                dst[dp++] = (byte)(0xc0 | (c >> 6));
                dst[dp++] = (byte)(0x80 | (c & 0x3f));
            } else if (Character.isSurrogate(c)) {
                int uc = -1;
                char c2;
                if (Character.isHighSurrogate(c) && sp < sl &&
                    Character.isLowSurrogate(c2 = StringUTF16.getChar(val, sp))) {
                    uc = Character.toCodePoint(c, c2);
                }
                if (uc < 0) {
                    if (doReplace) {
                        dst[dp++] = '?';
                    } else {
                        throwUnmappable(sp - 1, 1); // or 2, does not matter here
                    }
                } else {
                    dst[dp++] = (byte)(0xf0 | ((uc >> 18)));
                    dst[dp++] = (byte)(0x80 | ((uc >> 12) & 0x3f));
                    dst[dp++] = (byte)(0x80 | ((uc >>  6) & 0x3f));
                    dst[dp++] = (byte)(0x80 | (uc & 0x3f));
                    sp++;  // 2 chars
                }
            } else {
                // 3 bytes, 16 bits
                dst[dp++] = (byte)(0xe0 | ((c >> 12)));
                dst[dp++] = (byte)(0x80 | ((c >>  6) & 0x3f));
                dst[dp++] = (byte)(0x80 | (c & 0x3f));
            }
        }
        if (dp == dst.length) {
            return dst;
        }
        return Arrays.copyOf(dst, dp);
    }

    /*
     * Throws iae, instead of replacing, if unmappable.
     */
    static byte[] getBytesUTF8NoRepl(String s) {
        return encodeUTF8(s.coder(), s.value(), false);
    }

    ////////////////////// for j.n.f.Files //////////////////////////

    private static boolean isASCII(byte[] src) {
        return !hasNegatives(src, 0, src.length);
    }

    /*
     * Throws CCE, instead of replacing, if unmappable.
     */
    static byte[] getBytesNoRepl(String s, Charset cs) throws CharacterCodingException {
        try {
            return getBytesNoRepl1(s, cs);
        } catch (IllegalArgumentException e) {
            //getBytesNoRepl1 throws IAE with UnmappableCharacterException or CCE as the cause
            Throwable cause = e.getCause();
            if (cause instanceof UnmappableCharacterException) {
                throw (UnmappableCharacterException)cause;
            }
            throw (CharacterCodingException)cause;
        }
    }

    static byte[] getBytesNoRepl1(String s, Charset cs) {
        byte[] val = s.value();
        byte coder = s.coder();
        if (cs == UTF_8) {
            if (coder == LATIN1 && isASCII(val)) {
                return val;
            }
            return encodeUTF8(coder, val, false);
        }
        if (cs == ISO_8859_1) {
            if (coder == LATIN1) {
                return val;
            }
            return encode8859_1(coder, val, false);
        }
        if (cs == US_ASCII) {
            if (coder == LATIN1) {
                if (isASCII(val)) {
                    return val;
                } else {
                    throwUnmappable(val);
                }
            }
        }
        CharsetEncoder ce = cs.newEncoder();
        // fastpath for ascii compatible
        if (coder == LATIN1 && (((ce instanceof ArrayEncoder) &&
                                 ((ArrayEncoder)ce).isASCIICompatible() &&
                                 isASCII(val)))) {
            return val;
        }
        int len = val.length >> coder;  // assume LATIN1=0/UTF16=1;
        int en = scale(len, ce.maxBytesPerChar());
        byte[] ba = new byte[en];
        if (len == 0) {
            return ba;
        }
        if (ce instanceof ArrayEncoder) {
            int blen = (coder == LATIN1 ) ? ((ArrayEncoder)ce).encodeFromLatin1(val, 0, len, ba)
                                          : ((ArrayEncoder)ce).encodeFromUTF16(val, 0, len, ba);
            if (blen != -1) {
                return safeTrim(ba, blen, true);
            }
        }
        boolean isTrusted = cs.getClass().getClassLoader0() == null ||
                            System.getSecurityManager() == null;
        char[] ca = (coder == LATIN1 ) ? StringLatin1.toChars(val)
                                       : StringUTF16.toChars(val);
        ByteBuffer bb = ByteBuffer.wrap(ba);
        CharBuffer cb = CharBuffer.wrap(ca, 0, len);
        try {
            CoderResult cr = ce.encode(cb, bb, true);
            if (!cr.isUnderflow())
                cr.throwException();
            cr = ce.flush(bb);
            if (!cr.isUnderflow())
                cr.throwException();
        } catch (CharacterCodingException x) {
            throw new IllegalArgumentException(x);
        }
        return safeTrim(ba, bb.position(), isTrusted);
    }
}
