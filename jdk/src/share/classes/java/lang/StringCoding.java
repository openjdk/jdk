/*
 * Copyright (c) 2000, 2011, Oracle and/or its affiliates. All rights reserved.
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
import sun.misc.MessageUtils;
import sun.nio.cs.HistoricallyNamedCharset;
import sun.nio.cs.ArrayDecoder;
import sun.nio.cs.ArrayEncoder;

/**
 * Utility class for string encoding and decoding.
 */

class StringCoding {

    private StringCoding() { }

    /** The cached coders for each thread */
    private final static ThreadLocal<SoftReference<StringDecoder>> decoder =
        new ThreadLocal<>();
    private final static ThreadLocal<SoftReference<StringEncoder>> encoder =
        new ThreadLocal<>();

    private static boolean warnUnsupportedCharset = true;

    private static <T> T deref(ThreadLocal<SoftReference<T>> tl) {
        SoftReference<T> sr = tl.get();
        if (sr == null)
            return null;
        return sr.get();
    }

    private static <T> void set(ThreadLocal<SoftReference<T>> tl, T ob) {
        tl.set(new SoftReference<T>(ob));
    }

    // Trim the given byte array to the given length
    //
    private static byte[] safeTrim(byte[] ba, int len, Charset cs, boolean isTrusted) {
        if (len == ba.length && (isTrusted || System.getSecurityManager() == null))
            return ba;
        else
            return Arrays.copyOf(ba, len);
    }

    // Trim the given char array to the given length
    //
    private static char[] safeTrim(char[] ca, int len,
                                   Charset cs, boolean isTrusted) {
        if (len == ca.length && (isTrusted || System.getSecurityManager() == null))
            return ca;
        else
            return Arrays.copyOf(ca, len);
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


    // -- Decoding --
    private static class StringDecoder {
        private final String requestedCharsetName;
        private final Charset cs;
        private final CharsetDecoder cd;
        private final boolean isTrusted;

        private StringDecoder(Charset cs, String rcn) {
            this.requestedCharsetName = rcn;
            this.cs = cs;
            this.cd = cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
            this.isTrusted = (cs.getClass().getClassLoader0() == null);
        }

        String charsetName() {
            if (cs instanceof HistoricallyNamedCharset)
                return ((HistoricallyNamedCharset)cs).historicalName();
            return cs.name();
        }

        final String requestedCharsetName() {
            return requestedCharsetName;
        }

        char[] decode(byte[] ba, int off, int len) {
            int en = scale(len, cd.maxCharsPerByte());
            char[] ca = new char[en];
            if (len == 0)
                return ca;
            if (cd instanceof ArrayDecoder) {
                int clen = ((ArrayDecoder)cd).decode(ba, off, len, ca);
                return safeTrim(ca, clen, cs, isTrusted);
            } else {
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
                return safeTrim(ca, cb.position(), cs, isTrusted);
            }
        }
    }

    static char[] decode(String charsetName, byte[] ba, int off, int len)
        throws UnsupportedEncodingException
    {
        StringDecoder sd = deref(decoder);
        String csn = (charsetName == null) ? "ISO-8859-1" : charsetName;
        if ((sd == null) || !(csn.equals(sd.requestedCharsetName())
                              || csn.equals(sd.charsetName()))) {
            sd = null;
            try {
                Charset cs = lookupCharset(csn);
                if (cs != null)
                    sd = new StringDecoder(cs, csn);
            } catch (IllegalCharsetNameException x) {}
            if (sd == null)
                throw new UnsupportedEncodingException(csn);
            set(decoder, sd);
        }
        return sd.decode(ba, off, len);
    }

    static char[] decode(Charset cs, byte[] ba, int off, int len) {
        // (1)We never cache the "external" cs, the only benefit of creating
        // an additional StringDe/Encoder object to wrap it is to share the
        // de/encode() method. These SD/E objects are short-lifed, the young-gen
        // gc should be able to take care of them well. But the best approash
        // is still not to generate them if not really necessary.
        // (2)The defensive copy of the input byte/char[] has a big performance
        // impact, as well as the outgoing result byte/char[]. Need to do the
        // optimization check of (sm==null && classLoader0==null) for both.
        // (3)getClass().getClassLoader0() is expensive
        // (4)There might be a timing gap in isTrusted setting. getClassLoader0()
        // is only chcked (and then isTrusted gets set) when (SM==null). It is
        // possible that the SM==null for now but then SM is NOT null later
        // when safeTrim() is invoked...the "safe" way to do is to redundant
        // check (... && (isTrusted || SM == null || getClassLoader0())) in trim
        // but it then can be argued that the SM is null when the opertaion
        // is started...
        CharsetDecoder cd = cs.newDecoder();
        int en = scale(len, cd.maxCharsPerByte());
        char[] ca = new char[en];
        if (len == 0)
            return ca;
        boolean isTrusted = false;
        if (System.getSecurityManager() != null) {
            if (!(isTrusted = (cs.getClass().getClassLoader0() == null))) {
                ba =  Arrays.copyOfRange(ba, off, off + len);
                off = 0;
            }
        }
        cd.onMalformedInput(CodingErrorAction.REPLACE)
          .onUnmappableCharacter(CodingErrorAction.REPLACE)
          .reset();
        if (cd instanceof ArrayDecoder) {
            int clen = ((ArrayDecoder)cd).decode(ba, off, len, ca);
            return safeTrim(ca, clen, cs, isTrusted);
        } else {
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
            return safeTrim(ca, cb.position(), cs, isTrusted);
        }
    }

    static char[] decode(byte[] ba, int off, int len) {
        String csn = Charset.defaultCharset().name();
        try {
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
        private final String requestedCharsetName;
        private final boolean isTrusted;

        private StringEncoder(Charset cs, String rcn) {
            this.requestedCharsetName = rcn;
            this.cs = cs;
            this.ce = cs.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
            this.isTrusted = (cs.getClass().getClassLoader0() == null);
        }

        String charsetName() {
            if (cs instanceof HistoricallyNamedCharset)
                return ((HistoricallyNamedCharset)cs).historicalName();
            return cs.name();
        }

        final String requestedCharsetName() {
            return requestedCharsetName;
        }

        byte[] encode(char[] ca, int off, int len) {
            int en = scale(len, ce.maxBytesPerChar());
            byte[] ba = new byte[en];
            if (len == 0)
                return ba;
            if (ce instanceof ArrayEncoder) {
                int blen = ((ArrayEncoder)ce).encode(ca, off, len, ba);
                return safeTrim(ba, blen, cs, isTrusted);
            } else {
                ce.reset();
                ByteBuffer bb = ByteBuffer.wrap(ba);
                CharBuffer cb = CharBuffer.wrap(ca, off, len);
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
                return safeTrim(ba, bb.position(), cs, isTrusted);
            }
        }
    }

    static byte[] encode(String charsetName, char[] ca, int off, int len)
        throws UnsupportedEncodingException
    {
        StringEncoder se = deref(encoder);
        String csn = (charsetName == null) ? "ISO-8859-1" : charsetName;
        if ((se == null) || !(csn.equals(se.requestedCharsetName())
                              || csn.equals(se.charsetName()))) {
            se = null;
            try {
                Charset cs = lookupCharset(csn);
                if (cs != null)
                    se = new StringEncoder(cs, csn);
            } catch (IllegalCharsetNameException x) {}
            if (se == null)
                throw new UnsupportedEncodingException (csn);
            set(encoder, se);
        }
        return se.encode(ca, off, len);
    }

    static byte[] encode(Charset cs, char[] ca, int off, int len) {
        CharsetEncoder ce = cs.newEncoder();
        int en = scale(len, ce.maxBytesPerChar());
        byte[] ba = new byte[en];
        if (len == 0)
            return ba;
        boolean isTrusted = false;
        if (System.getSecurityManager() != null) {
            if (!(isTrusted = (cs.getClass().getClassLoader0() == null))) {
                ca =  Arrays.copyOfRange(ca, off, off + len);
                off = 0;
            }
        }
        ce.onMalformedInput(CodingErrorAction.REPLACE)
          .onUnmappableCharacter(CodingErrorAction.REPLACE)
          .reset();
        if (ce instanceof ArrayEncoder) {
            int blen = ((ArrayEncoder)ce).encode(ca, off, len, ba);
            return safeTrim(ba, blen, cs, isTrusted);
        } else {
            ByteBuffer bb = ByteBuffer.wrap(ba);
            CharBuffer cb = CharBuffer.wrap(ca, off, len);
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
            return safeTrim(ba, bb.position(), cs, isTrusted);
        }
    }

    static byte[] encode(char[] ca, int off, int len) {
        String csn = Charset.defaultCharset().name();
        try {
            return encode(csn, ca, off, len);
        } catch (UnsupportedEncodingException x) {
            warnUnsupportedCharset(csn);
        }
        try {
            return encode("ISO-8859-1", ca, off, len);
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
