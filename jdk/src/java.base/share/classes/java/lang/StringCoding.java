/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import jdk.internal.HotSpotIntrinsicCandidate;
import sun.misc.MessageUtils;
import sun.nio.cs.HistoricallyNamedCharset;
import sun.nio.cs.ArrayDecoder;
import sun.nio.cs.ArrayEncoder;

import static java.lang.String.LATIN1;
import static java.lang.String.UTF16;
import static java.lang.String.COMPACT_STRINGS;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

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

    private static boolean warnUnsupportedCharset = true;

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
    //
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

    private static void warnUnsupportedCharset(String csn) {
        if (warnUnsupportedCharset) {
            // Use sun.misc.MessageUtils rather than the Logging API or
            // System.err since this method may be called during VM
            // initialization before either is available.
            MessageUtils.err("WARNING: Default charset " + csn +
                             " not supported, using ISO-8859-1 instead");
            warnUnsupportedCharset = false;
        }
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
    private static boolean hasNegatives(byte[] ba, int off, int len) {
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

    private static class StringDecoder8859_1 extends StringDecoder {
        StringDecoder8859_1(Charset cs, String rcn) {
            super(cs, rcn);
        }
        Result decode(byte[] ba, int off, int len) {
            if (COMPACT_STRINGS) {
                return result.with(Arrays.copyOfRange(ba, off, off + len), LATIN1);
            } else {
                return result.with(StringLatin1.inflate(ba, off, len), UTF16);
            }
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
                        sd = new StringDecoderUTF8(cs, csn);
                    } else if (cs == ISO_8859_1) {
                        sd = new StringDecoder8859_1(cs, csn);
                    } else {
                        sd = new StringDecoder(cs, csn);
                    }
                }
            } catch (IllegalCharsetNameException x) {}
            if (sd == null)
                throw new UnsupportedEncodingException(csn);
            set(decoder, sd);
        }
        return sd.decode(ba, off, len);
    }

    static Result decode(Charset cs, byte[] ba, int off, int len) {
        // (1)We never cache the "external" cs, the only benefit of creating
        // an additional StringDe/Encoder object to wrap it is to share the
        // de/encode() method. These SD/E objects are short-lived, the young-gen
        // gc should be able to take care of them well. But the best approach
        // is still not to generate them if not really necessary.
        // (2)The defensive copy of the input byte/char[] has a big performance
        // impact, as well as the outgoing result byte/char[]. Need to do the
        // optimization check of (sm==null && classLoader0==null) for both.
        // (3)getClass().getClassLoader0() is expensive
        // (4)There might be a timing gap in isTrusted setting. getClassLoader0()
        // is only checked (and then isTrusted gets set) when (SM==null). It is
        // possible that the SM==null for now but then SM is NOT null later
        // when safeTrim() is invoked...the "safe" way to do is to redundant
        // check (... && (isTrusted || SM == null || getClassLoader0())) in trim
        // but it then can be argued that the SM is null when the operation
        // is started...
        if (cs == UTF_8) {
            return StringDecoderUTF8.decode(ba, off, len, new Result());
        }
        CharsetDecoder cd = cs.newDecoder();
        // ascii fastpath
        if (cs == ISO_8859_1 || ((cd instanceof ArrayDecoder) &&
                                 ((ArrayDecoder)cd).isASCIICompatible() &&
                                 !hasNegatives(ba, off, len))) {
             if (COMPACT_STRINGS) {
                 return new Result().with(Arrays.copyOfRange(ba, off, off + len),
                                          LATIN1);
             } else {
                 return new Result().with(StringLatin1.inflate(ba, off, len), UTF16);
             }
        }
        int en = scale(len, cd.maxCharsPerByte());
        if (len == 0) {
            return new Result().with();
        }
        if (System.getSecurityManager() != null &&
            cs.getClass().getClassLoader0() != null) {
            ba =  Arrays.copyOfRange(ba, off, off + len);
            off = 0;
        }
        cd.onMalformedInput(CodingErrorAction.REPLACE)
          .onUnmappableCharacter(CodingErrorAction.REPLACE)
          .reset();

        char[] ca = new char[en];
        if (cd instanceof ArrayDecoder) {
            int clen = ((ArrayDecoder)cd).decode(ba, off, len, ca);
            return new Result().with(ca, 0, clen);
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
        String csn = Charset.defaultCharset().name();
        try {
            // use charset name decode() variant which provides caching.
            return decode(csn, ba, off, len);
        } catch (UnsupportedEncodingException x) {
            warnUnsupportedCharset(csn);
        }
        try {
            return decode("ISO-8859-1", ba, off, len);
        } catch (UnsupportedEncodingException x) {
            // If this code is hit during VM initialization, MessageUtils is
            // the only way we will be able to get any kind of error message.
            MessageUtils.err("ISO-8859-1 charset not available: "
                             + x.toString());
            // If we can not find ISO-8859-1 (a required encoding) then things
            // are seriously wrong with the installation.
            System.exit(1);
            return null;
        }
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
                if (!isTrusted) {
                    val = Arrays.copyOf(val, val.length);
                }
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

    static byte[] encode8859_1(byte coder, byte[] val) {
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

    static byte[] encodeASCII(byte coder, byte[] val) {
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

   static byte[] encodeUTF8(byte coder, byte[] val) {
        int dp = 0;
        byte[] dst;
        if (coder == LATIN1) {
            dst = new byte[val.length << 1];
            for (int sp = 0; sp < val.length; sp++) {
                byte c = val[sp];
                if (c < 0) {
                    dst[dp++] = (byte)(0xc0 | ((c & 0xff) >> 6));
                    dst[dp++] = (byte)(0x80 | (c & 0x3f));
                } else {
                    dst[dp++] = c;
                }
            }
        } else {
            int sp = 0;
            int sl = val.length >> 1;
            dst = new byte[sl * 3];
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
                        dst[dp++] = '?';
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
        }
        if (dp == dst.length) {
            return dst;
        }
        return Arrays.copyOf(dst, dp);
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
                        return encodeUTF8(coder, val);
                    } else if (cs == ISO_8859_1) {
                        return encode8859_1(coder, val);
                    } else if (cs == US_ASCII) {
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
            return encodeUTF8(coder, val);
        } else if (cs == ISO_8859_1) {
            return encode8859_1(coder, val);
        } else if (cs == US_ASCII) {
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
        boolean isTrusted = System.getSecurityManager() == null ||
                            cs.getClass().getClassLoader0() == null;
        ce.onMalformedInput(CodingErrorAction.REPLACE)
          .onUnmappableCharacter(CodingErrorAction.REPLACE)
          .reset();
        if (ce instanceof ArrayEncoder) {
            if (!isTrusted) {
                val = Arrays.copyOf(val, val.length);
            }
            int blen = (coder == LATIN1 ) ? ((ArrayEncoder)ce).encodeFromLatin1(val, 0, len, ba)
                                          : ((ArrayEncoder)ce).encodeFromUTF16(val, 0, len, ba);
            if (blen != -1) {
                return safeTrim(ba, blen, isTrusted);
            }
        }
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
        String csn = Charset.defaultCharset().name();
        try {
            // use charset name encode() variant which provides caching.
            return encode(csn, coder, val);
        } catch (UnsupportedEncodingException x) {
            warnUnsupportedCharset(csn);
        }
        try {
            return encode("ISO-8859-1", coder, val);
        } catch (UnsupportedEncodingException x) {
            // If this code is hit during VM initialization, MessageUtils is
            // the only way we will be able to get any kind of error message.
            MessageUtils.err("ISO-8859-1 charset not available: "
                             + x.toString());
            // If we can not find ISO-8859-1 (a required encoding) then things
            // are seriously wrong with the installation.
            System.exit(1);
            return null;
        }
    }
}
