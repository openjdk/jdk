/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.util.ArraysSupport;
import sun.nio.cs.UTF_8;

/**
 * Utility class for ZIP file entry name and comment decoding and encoding.
 * <p>
 * The {@code ZipCoder} for UTF-8 charset is thread safe, {@code ZipCoder}
 * for other charsets require external synchronization.
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

    /**
     * Constants representing the three possible return values for
     * {@link #compare(String, byte[], int, int, boolean)} when
     * this method compares a lookup name to a string encoded in the
     * CEN byte array.
     */
    static final byte
        /*
         * The lookup string is exactly equal
         * to the encoded string.
         */
        EXACT_MATCH = 0,
        /*
         * The lookup string and the encoded string differs only
         * by the encoded string having a trailing '/' character.
         */
        DIRECTORY_MATCH = 1,
        /*
         * The lookup string and the encoded string do not match.
         * (They are neither an exact match or a directory match.)
         */
        NO_MATCH = 2;

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

    boolean isUTF8() {
        return false;
    }

    // Hash code functions for ZipFile entry names. We generate the hash as-if
    // we first decoded the byte sequence to a String, then appended '/' if no
    // trailing slash was found, then called String.hashCode(). This
    // normalization ensures we can simplify and speed up lookups.
    //
    // Does encoding error checking and hashing in a single pass for efficiency.
    // On an error, this function will throw CharacterCodingException while the
    // UTF8ZipCoder override will throw IllegalArgumentException, so we declare
    // throws Exception to keep things simple.
    int checkedHash(byte[] a, int off, int len) throws Exception {
        if (len == 0) {
            return 0;
        }

        int h = 0;
        // cb will be a newly allocated CharBuffer with pos == 0,
        // arrayOffset == 0, backed by an array.
        CharBuffer cb = decoder().decode(ByteBuffer.wrap(a, off, len));
        int limit = cb.limit();
        char[] decoded = cb.array();
        for (int i = 0; i < limit; i++) {
            h = 31 * h + decoded[i];
        }
        if (limit > 0 && decoded[limit - 1] != '/') {
            h = 31 * h + '/';
        }
        return h;
    }

    // Hash function equivalent of checkedHash for String inputs
    static int hash(String name) {
        int hsh = name.hashCode();
        int len = name.length();
        if (len > 0 && name.charAt(len - 1) != '/') {
            hsh = hsh * 31 + '/';
        }
        return hsh;
    }

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

    /**
     * {@return the {@link Charset} used by this {@code ZipCoder}}
     */
    final Charset charset() {
        return this.cs;
    }

    private CharsetEncoder encoder() {
        if (enc == null) {
            enc = cs.newEncoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
        return enc;
    }

    /**
     * This method is used by ZipFile.Source.getEntryPos when comparing the
     * name being looked up to candidate names encoded in the CEN byte
     * array.
     *
     * Since ZipCode.getEntry supports looking up a "dir/" entry by
     * the name "dir", this method can optionally distinguish an
     * exact match from a partial "directory match" (where names only
     * differ by the encoded name having an additional trailing '/')
     *
     * The return values of this method are as follows:
     *
     * If the lookup name is exactly equal to the encoded string, return
     * {@link EXACT_MATCH}.
     *
     * If the parameter {@code matchDirectory} is {@code true} and the
     * two strings differ only by the encoded string having an extra
     * trailing '/' character, then return {@link DIRECTORY_MATCH}.
     *
     * Otherwise, return {@link NO_MATCH}
     *
     * While a general implementation will need to decode bytes into a
     * String for comparison, this can be avoided if the String coder
     * and this ZipCoder are known to encode strings to the same bytes.
     *
     * @param str The lookup string to compare with the encoded string.
     * @param b The byte array holding the encoded string
     * @param off The offset into the array where the encoded string starts
     * @param len The length of the encoded string in bytes
     * @param matchDirectory If {@code true} and the strings do not match exactly,
     *                      a directory match will also be tested
     *
     */
    byte compare(String str, byte[] b, int off, int len, boolean matchDirectory) {
        String decoded = toString(b, off, len);
        if (decoded.startsWith(str)) {
            if (decoded.length() == str.length()) {
                return EXACT_MATCH;
            } else if (matchDirectory
                && decoded.length() == str.length() + 1
                && decoded.endsWith("/") ) {
                return DIRECTORY_MATCH;
            }
        }
        return NO_MATCH;
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
        String toString(byte[] ba, int off, int length) {
            return JLA.newStringUTF8NoRepl(ba, off, length);
        }

        @Override
        byte[] getBytes(String s) {
            return JLA.getBytesUTF8NoRepl(s);
        }

        @Override
        int checkedHash(byte[] a, int off, int len) throws Exception {
            if (len == 0) {
                return 0;
            }
            int end = off + len;
            int asciiLen = JLA.countPositives(a, off, len);
            if (asciiLen != len) {
                // Non-ASCII, fall back to decoding a String
                // We avoid using decoder() here since the UTF8ZipCoder is
                // shared and that decoder is not thread safe.
                // We use the JLA.newStringUTF8NoRepl variant to throw
                // exceptions eagerly when opening ZipFiles
                return hash(JLA.newStringUTF8NoRepl(a, off, len));
            }
            int h = ArraysSupport.hashCodeOfUnsigned(a, off, len, 0);
            if (a[end - 1] != '/') {
                h = 31 * h + '/';
            }
            return h;
        }

        private boolean hasTrailingSlash(byte[] a, int end) {
            return end > 0 && a[end - 1] == '/';
        }

        @Override
        byte compare(String str, byte[] b, int off, int len, boolean matchDirectory) {
            try {
                byte[] encoded = JLA.uncheckedGetBytesNoRepl(str, UTF_8.INSTANCE);
                int mismatch = Arrays.mismatch(encoded, 0, encoded.length, b, off, off+len);
                if (mismatch == -1) {
                    return EXACT_MATCH;
                } else if (matchDirectory && len == mismatch + 1 && hasTrailingSlash(b, off + len)) {
                    return DIRECTORY_MATCH;
                } else {
                    return NO_MATCH;
                }
            } catch (CharacterCodingException e) {
                return NO_MATCH;
            }
        }
    }
}
