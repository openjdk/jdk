/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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

package java.util.zip;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;

import sun.nio.cs.UTF_8;

/**
 * Utility class for zipfile name and comment decoding and encoding
 */
class ZipCoder {

    private static final jdk.internal.access.JavaLangAccess JLA =
        jdk.internal.access.SharedSecrets.getJavaLangAccess();

    // Encoding/decoding is stateless, so make it singleton.
    static final UTF8ZipCoder UTF8 = new UTF8ZipCoder(UTF_8.INSTANCE);

    public static ZipCoder get(Charset charset) {
        if (charset == UTF_8.INSTANCE) {
            return UTF8;
        }
        return new ZipCoder(charset);
    }

    void checkEncoding(byte[] a, int pos, int nlen) throws ZipException {
        try {
            toString(a, pos, nlen);
        } catch(Exception e) {
            throw new ZipException("invalid CEN header (bad entry name)");
        }
    }

    String toString(byte[] ba, int off, int length) {
        try {
            return decoder().decode(ByteBuffer.wrap(ba, off, length)).toString();
        } catch (CharacterCodingException x) {
            throw new IllegalArgumentException(x);
        }
    }

    String toString(byte[] ba, int length) {
        return toString(ba, 0, length);
    }

    String toString(byte[] ba) {
        return toString(ba, 0, ba.length);
    }

    byte[] getBytes(String s) {
        try {
            ByteBuffer bb = encoder().encode(CharBuffer.wrap(s));
            int pos = bb.position();
            int limit = bb.limit();
            if (bb.hasArray() && pos == 0 && limit == bb.capacity()) {
                return bb.array();
            }
            byte[] bytes = new byte[bb.limit() - bb.position()];
            bb.get(bytes);
            return bytes;
        } catch (CharacterCodingException x) {
            throw new IllegalArgumentException(x);
        }
    }

    static String toStringUTF8(byte[] ba, int len) {
        return UTF8.toString(ba, 0, len);
    }

    static String toStringUTF8(byte[] ba, int off, int len) {
        return UTF8.toString(ba, off, len);
    }

    boolean isUTF8() {
        return false;
    }

    // Hash code functions for ZipFile entry names. We generate the hash as-if
    // we first decoded the byte sequence to a String, then appended '/' if no
    // trailing slash was found, then called String.hashCode(). This
    // normalization ensures we can simplify and speed up lookups.
    int normalizedHash(byte[] a, int off, int len) {
        if (len == 0) {
            return 0;
        }
        return normalizedHashDecode(0, a, off, off + len);
    }

    // Matching normalized hash code function for Strings
    static int normalizedHash(String name) {
        int hsh = name.hashCode();
        int len = name.length();
        if (len > 0 && name.charAt(len - 1) != '/') {
            hsh = hsh * 31 + '/';
        }
        return hsh;
    }

    boolean hasTrailingSlash(byte[] a, int end) {
        byte[] slashBytes = slashBytes();
        return end >= slashBytes.length &&
            Arrays.mismatch(a, end - slashBytes.length, end, slashBytes, 0, slashBytes.length) == -1;
    }

    // Implements normalizedHash by decoding byte[] to char[] and then computing
    // the hash. This is a slow-path used for non-UTF8 charsets and also when
    // aborting the ASCII fast-path in the UTF8 implementation, so {@code h}
    // might be a partially calculated hash code
    int normalizedHashDecode(int h, byte[] a, int off, int end) {
        try {
            // cb will be a newly allocated CharBuffer with pos == 0,
            // arrayOffset == 0, backed by an array.
            CharBuffer cb = decoder().decode(ByteBuffer.wrap(a, off, end - off));
            int limit = cb.limit();
            char[] decoded = cb.array();
            for (int i = 0; i < limit; i++) {
                h = 31 * h + decoded[i];
            }
            if (limit > 0 && decoded[limit - 1] != '/') {
                h = 31 * h + '/';
            }
        } catch (CharacterCodingException cce) {
            // Ignore - return the hash code generated so far.
        }
        return h;
    }

    private byte[] slashBytes;
    private final Charset cs;
    protected CharsetDecoder dec;
    private CharsetEncoder enc;

    private ZipCoder(Charset cs) {
        this.cs = cs;
    }

    protected CharsetDecoder decoder() {
        if (dec == null) {
            dec = cs.newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
        return dec;
    }

    private CharsetEncoder encoder() {
        if (enc == null) {
            enc = cs.newEncoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
        return enc;
    }

    // This method produces an array with the bytes that will correspond to a
    // trailing '/' in the chosen character encoding.
    //
    // While in most charsets a trailing slash will be encoded as the byte
    // value of '/', this does not hold in the general case. E.g., in charsets
    // such as UTF-16 and UTF-32 it will be represented by a sequence of 2 or 4
    // bytes, respectively.
    private byte[] slashBytes() {
        if (slashBytes == null) {
            // Take into account charsets that produce a BOM, e.g., UTF-16
            byte[] slash = "/".getBytes(cs);
            byte[] doubleSlash = "//".getBytes(cs);
            slashBytes = Arrays.copyOfRange(doubleSlash, slash.length, doubleSlash.length);
        }
        return slashBytes;
    }

    static final class UTF8ZipCoder extends ZipCoder {

        private UTF8ZipCoder(Charset utf8) {
            super(utf8);
        }

        @Override
        boolean isUTF8() {
            return true;
        }

        @Override
        void checkEncoding(byte[] a, int pos, int len) throws ZipException {
            try {
                int end = pos + len;
                while (pos < end) {
                    // ASCII fast-path: When checking that a range of bytes is
                    // valid UTF-8, we can avoid some allocation by skipping
                    // past bytes in the 0-127 range
                    if (a[pos] < 0) {
                        ZipCoder.toStringUTF8(a, pos, end - pos);
                        break;
                    }
                    pos++;
                }
            } catch(Exception e) {
                throw new ZipException("invalid CEN header (bad entry name)");
            }
        }

        @Override
        String toString(byte[] ba, int off, int length) {
            return JLA.newStringUTF8NoRepl(ba, off, length);
        }

        @Override
        byte[] getBytes(String s) {
            return JLA.getBytesUTF8NoRepl(s);
        }

        @Override
        int normalizedHash(byte[] a, int off, int len) {
            if (len == 0) {
                return 0;
            }

            int end = off + len;
            int h = 0;
            while (off < end) {
                byte b = a[off];
                if (b < 0) {
                    // Non-ASCII, fall back to decoding a String
                    // We avoid using decoder() here since the UTF8ZipCoder is
                    // shared and that decoder is not thread safe.
                    // We also avoid the JLA.newStringUTF8NoRepl variant at
                    // this point to avoid throwing exceptions eagerly when
                    // opening ZipFiles (exceptions are expected when accessing
                    // malformed entries.)
                    return normalizedHash(new String(a, end - len, len, UTF_8.INSTANCE));
                } else {
                    h = 31 * h + b;
                    off++;
                }
            }

            if (a[end - 1] != '/') {
                h = 31 * h + '/';
            }
            return h;
        }

        @Override
        boolean hasTrailingSlash(byte[] a, int end) {
            return end > 0 && a[end - 1] == '/';
        }
    }
}
