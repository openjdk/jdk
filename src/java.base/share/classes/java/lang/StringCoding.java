/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.HotSpotIntrinsicCandidate;
import sun.nio.cs.HistoricallyNamedCharset;
import sun.nio.cs.ArrayDecoder;
import sun.nio.cs.ArrayEncoder;

import static java.lang.String.LATIN1;
import static java.lang.String.UTF16;
import static java.lang.String.COMPACT_STRINGS;
import static java.lang.Character.isSurrogate;
import static java.lang.Character.highSurrogate;
import static java.lang.Character.lowSurrogate;
import static java.lang.Character.isSupplementaryCodePoint;
import static java.lang.StringUTF16.putChar;

/**
 * Utility class for string encoding and decoding.
 */

class StringCoding {

    private StringCoding() { }

    /** The cached coders for each thread */
    private static final ThreadLocal<SoftReference<StringDecoder>> decoder =
        new ThreadLocal<>();
    private static final ThreadLocal<SoftReference<StringEncoder>> encoder =
        new ThreadLocal<>();

    private static final Charset ISO_8859_1 = sun.nio.cs.ISO_8859_1.INSTANCE;
    private static final Charset US_ASCII = sun.nio.cs.US_ASCII.INSTANCE;
    private static final Charset UTF_8 = sun.nio.cs.UTF_8.INSTANCE;

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

    private static int scale(int len, float expansionFactor) {
        // We need to perform double, not float, arithmetic; otherwise
        // we lose low order bits when len is larger than 2**24.
        return (int)(len * (double)expansionFactor);
    }

    private static Charset lookupCharset(String csn) {
        if (Charset.isSupported(csn)) {
            try {
                return Charset.forName(csn);
            } catch (UnsupportedCharsetException x) {
                throw new Error(x);
            }
        }
        return null;
    }

    static class Result {
        byte[] value;
        byte coder;

        Result with() {
            coder = COMPACT_STRINGS ? LATIN1 : UTF16;
            value = new byte[0];
            return this;
        }

        Result with(char[] val, int off, int len) {
            if (String.COMPACT_STRINGS) {
                byte[] bs = StringUTF16.compress(val, off, len);
                if (bs != null) {
                    value = bs;
                    coder = LATIN1;
                    return this;
                }
            }
            coder = UTF16;
            value = StringUTF16.toBytes(val, off, len);
            return this;
        }

        Result with(byte[] val, byte coder) {
            this.coder = coder;
            value = val;
            return this;
        }
    }

    @HotSpotIntrinsicCandidate
    public static boolean hasNegatives(byte[] ba, int off, int len) {
        for (int i = off; i < off + len; i++) {
            if (ba[i] < 0) {
                return true;
            }
        }
        return false;
    }

    // -- Decoding --
    static class StringDecoder {
        private final String requestedCharsetName;
        private final Charset cs;
        private final boolean isASCIICompatible;
        private final CharsetDecoder cd;
        protected final Result result;

        StringDecoder(Charset cs, String rcn) {
            this.requestedCharsetName = rcn;
            this.cs = cs;
            this.cd = cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
            this.result = new Result();
            this.isASCIICompatible = (cd instanceof ArrayDecoder) &&
                    ((ArrayDecoder)cd).isASCIICompatible();
        }

        String charsetName() {
            if (cs instanceof HistoricallyNamedCharset)
                return ((HistoricallyNamedCharset)cs).historicalName();
            return cs.name();
        }

        final String requestedCharsetName() {
            return requestedCharsetName;
        }

        Result decode(byte[] ba, int off, int len) {
            if (len == 0) {
                return result.with();
            }
            // fastpath for ascii compatible
            if (isASCIICompatible && !hasNegatives(ba, off, len)) {
                if (COMPACT_STRINGS) {
                    return result.with(Arrays.copyOfRange(ba, off, off + len),
                                      LATIN1);
                } else {
                    return result.with(StringLatin1.inflate(ba, off, len), UTF16);
                }
            }
            int en = scale(len, cd.maxCharsPerByte());
            char[] ca = new char[en];
            if (cd instanceof ArrayDecoder) {
                int clen = ((ArrayDecoder)cd).decode(ba, off, len, ca);
                return result.with(ca, 0, clen);
            }
            cd.reset();
            ByteBuffer bb = ByteBuffer.wrap(ba, off, len);
            CharBuffer cb = CharBuffer.wrap(ca);
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
            return result.with(ca, 0, cb.position());
        }
    }

    static Result decode(String charsetName, byte[] ba, int off, int len)
        throws UnsupportedEncodingException
    {
        StringDecoder sd = deref(decoder);
        String csn = (charsetName == null) ? "ISO-8859-1" : charsetName;
        if ((sd == null) || !(csn.equals(sd.requestedCharsetName())
                              || csn.equals(sd.charsetName()))) {
            sd = null;
            try {
                Charset cs = lookupCharset(csn);
                if (cs != null) {
                    if (cs == UTF_8) {
                        return decodeUTF8(ba, off, len, true);
                    }
                    if (cs == ISO_8859_1) {
                        return decodeLatin1(ba, off, len);
                    }
                    if (cs == US_ASCII) {
                        return decodeASCII(ba, off, len);
                    }
                    sd = new StringDecoder(cs, csn);
                }
            } catch (IllegalCharsetNameException x) {}
            if (sd == null)
                throw new UnsupportedEncodingException(csn);
            set(decoder, sd);
        }
        return sd.decode(ba, off, len);
    }

    static Result decode(Charset cs, byte[] ba, int off, int len) {
        if (cs == UTF_8) {
            return decodeUTF8(ba, off, len, true);
        }
        if (cs == ISO_8859_1) {
            return decodeLatin1(ba, off, len);
        }
        if (cs == US_ASCII) {
            return decodeASCII(ba, off, len);
        }

        // (1)We never cache the "external" cs, the only benefit of creating
        // an additional StringDe/Encoder object to wrap it is to share the
        // de/encode() method. These SD/E objects are short-lived, the young-gen
        // gc should be able to take care of them well. But the best approach
        // is still not to generate them if not really necessary.
        // (2)The defensive copy of the input byte/char[] has a big performance
        // impact, as well as the outgoing result byte/char[]. Need to do the
        // optimization check of (sm==null && classLoader0==null) for both.
        // (3)There might be a timing gap in isTrusted setting. getClassLoader0()
        // is only checked (and then isTrusted gets set) when (SM==null). It is
        // possible that the SM==null for now but then SM is NOT null later
        // when safeTrim() is invoked...the "safe" way to do is to redundant
        // check (... && (isTrusted || SM == null || getClassLoader0())) in trim
        // but it then can be argued that the SM is null when the operation
        // is started...
        CharsetDecoder cd = cs.newDecoder();
        // ascii fastpath
        if ((cd instanceof ArrayDecoder) &&
            ((ArrayDecoder)cd).isASCIICompatible() && !hasNegatives(ba, off, len)) {
            return decodeLatin1(ba, off, len);
        }
        int en = scale(len, cd.maxCharsPerByte());
        if (len == 0) {
            return new Result().with();
        }
        cd.onMalformedInput(CodingErrorAction.REPLACE)
          .onUnmappableCharacter(CodingErrorAction.REPLACE)
          .reset();
        char[] ca = new char[en];
        if (cd instanceof ArrayDecoder) {
            int clen = ((ArrayDecoder)cd).decode(ba, off, len, ca);
            return new Result().with(ca, 0, clen);
        }
        if (cs.getClass().getClassLoader0() != null &&
            System.getSecurityManager() != null) {
            ba = Arrays.copyOfRange(ba, off, off + len);
            off = 0;
        }
        ByteBuffer bb = ByteBuffer.wrap(ba, off, len);
        CharBuffer cb = CharBuffer.wrap(ca);
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
        return new Result().with(ca, 0, cb.position());
    }

    static Result decode(byte[] ba, int off, int len) {
        Charset cs = Charset.defaultCharset();
        if (cs == UTF_8) {
            return decodeUTF8(ba, off, len, true);
        }
        if (cs == ISO_8859_1) {
            return decodeLatin1(ba, off, len);
        }
        if (cs == US_ASCII) {
            return decodeASCII(ba, off, len);
        }
        StringDecoder sd = deref(decoder);
        if (sd == null || !cs.name().equals(sd.cs.name())) {
            sd = new StringDecoder(cs, cs.name());
            set(decoder, sd);
        }
        return sd.decode(ba, off, len);
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

    static byte[] encode(String charsetName, byte coder, byte[] val)
        throws UnsupportedEncodingException
    {
        StringEncoder se = deref(encoder);
        String csn = (charsetName == null) ? "ISO-8859-1" : charsetName;
        if ((se == null) || !(csn.equals(se.requestedCharsetName())
                              || csn.equals(se.charsetName()))) {
            se = null;
            try {
                Charset cs = lookupCharset(csn);
                if (cs != null) {
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
                }
            } catch (IllegalCharsetNameException x) {}
            if (se == null) {
                throw new UnsupportedEncodingException (csn);
            }
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

     /* The cached Result for each thread */
    private static final ThreadLocal<StringCoding.Result>
        resultCached = new ThreadLocal<>() {
            protected StringCoding.Result initialValue() {
                return new StringCoding.Result();
            }};

    ////////////////////////// ascii //////////////////////////////

    private static Result decodeASCII(byte[] ba, int off, int len) {
        Result result = resultCached.get();
        if (COMPACT_STRINGS && !hasNegatives(ba, off, len)) {
            return result.with(Arrays.copyOfRange(ba, off, off + len),
                               LATIN1);
        }
        byte[] dst = new byte[len<<1];
        int dp = 0;
        while (dp < len) {
            int b = ba[off++];
            putChar(dst, dp++, (b >= 0) ? (char)b : repl);
        }
        return result.with(dst, UTF16);
    }

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

    ////////////////////////// latin1/8859_1 ///////////////////////////

    private static Result decodeLatin1(byte[] ba, int off, int len) {
       Result result = resultCached.get();
       if (COMPACT_STRINGS) {
           return result.with(Arrays.copyOfRange(ba, off, off + len), LATIN1);
       } else {
           return result.with(StringLatin1.inflate(ba, off, len), UTF16);
       }
    }

    @HotSpotIntrinsicCandidate
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

    private static boolean isNotContinuation(int b) {
        return (b & 0xc0) != 0x80;
    }

    private static boolean isMalformed3(int b1, int b2, int b3) {
        return (b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
               (b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80;
    }

    private static boolean isMalformed3_2(int b1, int b2) {
        return (b1 == (byte)0xe0 && (b2 & 0xe0) == 0x80) ||
               (b2 & 0xc0) != 0x80;
    }

    private static boolean isMalformed4(int b2, int b3, int b4) {
        return (b2 & 0xc0) != 0x80 || (b3 & 0xc0) != 0x80 ||
               (b4 & 0xc0) != 0x80;
    }

    private static boolean isMalformed4_2(int b1, int b2) {
        return (b1 == 0xf0 && (b2  < 0x90 || b2 > 0xbf)) ||
               (b1 == 0xf4 && (b2 & 0xf0) != 0x80) ||
               (b2 & 0xc0) != 0x80;
    }

    private static boolean isMalformed4_3(int b3) {
        return (b3 & 0xc0) != 0x80;
    }

    // for nb == 3/4
    private static int malformedN(byte[] src, int sp, int nb) {
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

    private static void throwMalformed(int off, int nb) {
        String msg = "malformed input off : " + off + ", length : " + nb;
        throw new IllegalArgumentException(msg, new MalformedInputException(nb));
    }

    private static void throwMalformed(byte[] val) {
        int dp = 0;
        while (dp < val.length && val[dp] >=0) { dp++; }
        throwMalformed(dp, 1);
    }

    private static void throwUnmappable(int off, int nb) {
        String msg = "malformed input off : " + off + ", length : " + nb;
        throw new IllegalArgumentException(msg, new UnmappableCharacterException(nb));
    }

    private static void throwUnmappable(byte[] val) {
        int dp = 0;
        while (dp < val.length && val[dp] >=0) { dp++; }
        throwUnmappable(dp, 1);
    }

    private static char repl = '\ufffd';

    private static Result decodeUTF8(byte[] src, int sp, int len, boolean doReplace) {
        // ascii-bais, which has a relative impact to the non-ascii-only bytes
        if (COMPACT_STRINGS && !hasNegatives(src, sp, len))
            return resultCached.get().with(Arrays.copyOfRange(src, sp, sp + len),
                                           LATIN1);
        return decodeUTF8_0(src, sp, len, doReplace);
    }

    private static Result decodeUTF8_0(byte[] src, int sp, int len, boolean doReplace) {
        Result ret = resultCached.get();

        int sl = sp + len;
        int dp = 0;
        byte[] dst = new byte[len];

        if (COMPACT_STRINGS) {
            while (sp < sl) {
                int b1 = src[sp];
                if (b1 >= 0) {
                    dst[dp++] = (byte)b1;
                    sp++;
                    continue;
                }
                if ((b1 == (byte)0xc2 || b1 == (byte)0xc3) &&
                    sp + 1 < sl) {
                    int b2 = src[sp + 1];
                    if (!isNotContinuation(b2)) {
                        dst[dp++] = (byte)(((b1 << 6) ^ b2)^
                                           (((byte) 0xC0 << 6) ^
                                           ((byte) 0x80 << 0)));
                        sp += 2;
                        continue;
                    }
                }
                // anything not a latin1, including the repl
                // we have to go with the utf16
                break;
            }
            if (sp == sl) {
                if (dp != dst.length) {
                    dst = Arrays.copyOf(dst, dp);
                }
                return ret.with(dst, LATIN1);
            }
        }
        if (dp == 0) {
            dst = new byte[len << 1];
        } else {
            byte[] buf = new byte[len << 1];
            StringLatin1.inflate(dst, 0, buf, 0, dp);
            dst = buf;
        }
        while (sp < sl) {
            int b1 = src[sp++];
            if (b1 >= 0) {
                putChar(dst, dp++, (char) b1);
            } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                if (sp < sl) {
                    int b2 = src[sp++];
                    if (isNotContinuation(b2)) {
                        if (!doReplace) {
                            throwMalformed(sp - 1, 1);
                        }
                        putChar(dst, dp++, repl);
                        sp--;
                    } else {
                        putChar(dst, dp++, (char)(((b1 << 6) ^ b2)^
                                                  (((byte) 0xC0 << 6) ^
                                                  ((byte) 0x80 << 0))));
                    }
                    continue;
                }
                if (!doReplace) {
                    throwMalformed(sp, 1);  // underflow()
                }
                putChar(dst, dp++, repl);
                break;
            } else if ((b1 >> 4) == -2) {
                if (sp + 1 < sl) {
                    int b2 = src[sp++];
                    int b3 = src[sp++];
                    if (isMalformed3(b1, b2, b3)) {
                        if (!doReplace) {
                            throwMalformed(sp - 3, 3);
                        }
                        putChar(dst, dp++, repl);
                        sp -= 3;
                        sp += malformedN(src, sp, 3);
                    } else {
                        char c = (char)((b1 << 12) ^
                                        (b2 <<  6) ^
                                        (b3 ^
                                         (((byte) 0xE0 << 12) ^
                                         ((byte) 0x80 <<  6) ^
                                         ((byte) 0x80 <<  0))));
                        if (isSurrogate(c)) {
                            if (!doReplace) {
                                throwMalformed(sp - 3, 3);
                            }
                            putChar(dst, dp++, repl);
                        } else {
                            putChar(dst, dp++, c);
                        }
                    }
                    continue;
                }
                if (sp  < sl && isMalformed3_2(b1, src[sp])) {
                    if (!doReplace) {
                        throwMalformed(sp - 1, 2);
                    }
                    putChar(dst, dp++, repl);
                    continue;
                }
                if (!doReplace){
                    throwMalformed(sp, 1);
                }
                putChar(dst, dp++, repl);
                break;
            } else if ((b1 >> 3) == -2) {
                if (sp + 2 < sl) {
                    int b2 = src[sp++];
                    int b3 = src[sp++];
                    int b4 = src[sp++];
                    int uc = ((b1 << 18) ^
                              (b2 << 12) ^
                              (b3 <<  6) ^
                              (b4 ^
                               (((byte) 0xF0 << 18) ^
                               ((byte) 0x80 << 12) ^
                               ((byte) 0x80 <<  6) ^
                               ((byte) 0x80 <<  0))));
                    if (isMalformed4(b2, b3, b4) ||
                        !isSupplementaryCodePoint(uc)) { // shortest form check
                        if (!doReplace) {
                            throwMalformed(sp - 4, 4);
                        }
                        putChar(dst, dp++, repl);
                        sp -= 4;
                        sp += malformedN(src, sp, 4);
                    } else {
                        putChar(dst, dp++, highSurrogate(uc));
                        putChar(dst, dp++, lowSurrogate(uc));
                    }
                    continue;
                }
                b1 &= 0xff;
                if (b1 > 0xf4 ||
                    sp  < sl && isMalformed4_2(b1, src[sp] & 0xff)) {
                    if (!doReplace) {
                        throwMalformed(sp - 1, 1);  // or 2
                    }
                    putChar(dst, dp++, repl);
                    continue;
                }
                if (!doReplace) {
                    throwMalformed(sp - 1, 1);
                }
                sp++;
                putChar(dst, dp++, repl);
                if (sp  < sl && isMalformed4_3(src[sp])) {
                    continue;
                }
                break;
            } else {
                if (!doReplace) {
                    throwMalformed(sp - 1, 1);
                }
                putChar(dst, dp++, repl);
            }
        }
        if (dp != len) {
            dst = Arrays.copyOf(dst, dp << 1);
        }
        return ret.with(dst, UTF16);
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

    ////////////////////// for j.u.z.ZipCoder //////////////////////////

    /*
     * Throws iae, instead of replacing, if malformed or unmappable.
     */
    static String newStringUTF8NoRepl(byte[] src, int off, int len) {
        if (COMPACT_STRINGS && !hasNegatives(src, off, len))
            return new String(Arrays.copyOfRange(src, off, off + len), LATIN1);
        Result ret = decodeUTF8_0(src, off, len, false);
        return new String(ret.value, ret.coder);
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

    private static String newStringLatin1(byte[] src) {
        if (COMPACT_STRINGS)
           return new String(src, LATIN1);
        return new String(StringLatin1.inflate(src, 0, src.length), UTF16);
    }

    static String newStringNoRepl(byte[] src, Charset cs) throws CharacterCodingException {
        try {
            return newStringNoRepl1(src, cs);
        } catch (IllegalArgumentException e) {
            //newStringNoRepl1 throws IAE with MalformedInputException or CCE as the cause
            Throwable cause = e.getCause();
            if (cause instanceof MalformedInputException) {
                throw (MalformedInputException)cause;
            }
            throw (CharacterCodingException)cause;
        }
    }

    static String newStringNoRepl1(byte[] src, Charset cs) {
        if (cs == UTF_8) {
            if (COMPACT_STRINGS && isASCII(src))
                return new String(src, LATIN1);
            Result ret = decodeUTF8_0(src, 0, src.length, false);
            return new String(ret.value, ret.coder);
        }
        if (cs == ISO_8859_1) {
            return newStringLatin1(src);
        }
        if (cs == US_ASCII) {
            if (isASCII(src)) {
                return newStringLatin1(src);
            } else {
                throwMalformed(src);
            }
        }

        CharsetDecoder cd = cs.newDecoder();
        // ascii fastpath
        if ((cd instanceof ArrayDecoder) &&
            ((ArrayDecoder)cd).isASCIICompatible() && isASCII(src)) {
            return newStringLatin1(src);
        }
        int len = src.length;
        if (len == 0) {
            return "";
        }
        int en = scale(len, cd.maxCharsPerByte());
        char[] ca = new char[en];
        if (cs.getClass().getClassLoader0() != null &&
            System.getSecurityManager() != null) {
            src = Arrays.copyOf(src, len);
        }
        ByteBuffer bb = ByteBuffer.wrap(src);
        CharBuffer cb = CharBuffer.wrap(ca);
        try {
            CoderResult cr = cd.decode(bb, cb, true);
            if (!cr.isUnderflow())
                cr.throwException();
            cr = cd.flush(cb);
            if (!cr.isUnderflow())
                cr.throwException();
        } catch (CharacterCodingException x) {
            throw new IllegalArgumentException(x);  // todo
        }
        Result ret = resultCached.get().with(ca, 0, cb.position());
        return new String(ret.value, ret.coder);
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
