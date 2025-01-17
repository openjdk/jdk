/*
 * Copyright (c) 1995, 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.net;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.BitSet;
import java.util.Objects;
import java.util.HexFormat;
import java.util.function.IntPredicate;

import jdk.internal.util.ImmutableBitSetPredicate;

/**
 * Utility class for HTML form encoding. This class contains static methods
 * for converting a String to the <CODE>application/x-www-form-urlencoded</CODE> MIME
 * format. For more information about HTML form encoding, consult the HTML
 * <A HREF="http://www.w3.org/TR/html4/">specification</A>.
 *
 * <p>
 * When encoding a String, the following rules apply:
 *
 * <ul>
 * <li>The alphanumeric characters &quot;{@code a}&quot; through
 *     &quot;{@code z}&quot;, &quot;{@code A}&quot; through
 *     &quot;{@code Z}&quot; and &quot;{@code 0}&quot;
 *     through &quot;{@code 9}&quot; remain the same.
 * <li>The special characters &quot;{@code .}&quot;,
 *     &quot;{@code -}&quot;, &quot;{@code *}&quot;, and
 *     &quot;{@code _}&quot; remain the same.
 * <li>The space character &quot; &nbsp; &quot; is
 *     converted into a plus sign &quot;{@code +}&quot;.
 * <li>All other characters are unsafe and are first converted into
 *     one or more bytes using some encoding scheme. Then each byte is
 *     represented by the 3-character string
 *     &quot;<i>{@code %xy}</i>&quot;, where <i>xy</i> is the
 *     two-digit hexadecimal representation of the byte.
 *     The recommended encoding scheme to use is UTF-8. However,
 *     for compatibility reasons, if an encoding is not specified,
 *     then the default charset is used.
 * </ul>
 *
 * <p>
 * For example using UTF-8 as the encoding scheme the string &quot;The
 * string &#252;@foo-bar&quot; would get converted to
 * &quot;The+string+%C3%BC%40foo-bar&quot; because in UTF-8 the character
 * &#252; is encoded as two bytes C3 (hex) and BC (hex), and the
 * character @ is encoded as one byte 40 (hex).
 *
 * @spec https://www.w3.org/TR/html4 HTML 4.01 Specification
 * @see Charset#defaultCharset()
 *
 * @author  Herb Jellinek
 * @since   1.0
 */
public final class URLEncoder {
    private static final IntPredicate DONT_NEED_ENCODING;

    static {

        /* The list of characters that are not encoded has been
         * determined as follows:
         *
         * RFC 2396 states:
         * -----
         * Data characters that are allowed in a URI but do not have a
         * reserved purpose are called unreserved.  These include upper
         * and lower case letters, decimal digits, and a limited set of
         * punctuation marks and symbols.
         *
         * unreserved  = alphanum | mark
         *
         * mark        = "-" | "_" | "." | "!" | "~" | "*" | "'" | "(" | ")"
         *
         * Unreserved characters can be escaped without changing the
         * semantics of the URI, but this should not be done unless the
         * URI is being used in a context that does not allow the
         * unescaped character to appear.
         * -----
         *
         * It appears that both Netscape and Internet Explorer escape
         * all special characters from this list with the exception
         * of "-", "_", ".", "*". While it is not clear why they are
         * escaping the other characters, perhaps it is safest to
         * assume that there might be contexts in which the others
         * are unsafe if not escaped. Therefore, we will use the same
         * list. It is also noteworthy that this is consistent with
         * O'Reilly's "HTML: The Definitive Guide" (page 164).
         *
         * As a last note, Internet Explorer does not encode the "@"
         * character which is clearly not unreserved according to the
         * RFC. We are being consistent with the RFC in this matter,
         * as is Netscape.
         *
         */

        var bitSet = new BitSet(128);
        bitSet.set('a', 'z' + 1);
        bitSet.set('A', 'Z' + 1);
        bitSet.set('0', '9' + 1);
        bitSet.set(' '); /* encoding a space to a + is done
                                    * in the encode() method */
        bitSet.set('-');
        bitSet.set('_');
        bitSet.set('.');
        bitSet.set('*');

        DONT_NEED_ENCODING = ImmutableBitSetPredicate.of(bitSet);
    }

    /**
     * You can't call the constructor.
     */
    private URLEncoder() { }

    /**
     * Translates a string into {@code x-www-form-urlencoded}
     * format. This method uses the default charset
     * as the encoding scheme to obtain the bytes for unsafe characters.
     *
     * @param   s   {@code String} to be translated.
     * @deprecated The resulting string may vary depending on the
     *             default charset. Instead, use the encode(String,String)
     *             method to specify the encoding.
     * @return  the translated {@code String}.
     */
    @Deprecated
    public static String encode(String s) {
        return encode(s, Charset.defaultCharset());
    }

    /**
     * Translates a string into {@code application/x-www-form-urlencoded}
     * format using a specific encoding scheme.
     * <p>
     * This method behaves the same as {@linkplain #encode(String s, Charset charset)}
     * except that it will {@linkplain Charset#forName look up the charset}
     * using the given encoding name.
     *
     * @param   s   {@code String} to be translated.
     * @param   enc   The name of a supported
     *    <a href="../lang/package-summary.html#charenc">character
     *    encoding</a>.
     * @return  the translated {@code String}.
     * @throws  UnsupportedEncodingException
     *             If the named encoding is not supported
     * @see URLDecoder#decode(java.lang.String, java.lang.String)
     * @since 1.4
     */
    public static String encode(String s, String enc)
        throws UnsupportedEncodingException {
        if (enc == null) {
            throw new NullPointerException("charsetName");
        }

        try {
            Charset charset = Charset.forName(enc);
            return encode(s, charset);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new UnsupportedEncodingException(enc);
        }
    }

    private static final int ENCODING_CHUNK_SIZE = 8;

    /**
     * Translates a string into {@code application/x-www-form-urlencoded}
     * format using a specific {@linkplain Charset Charset}.
     * This method uses the supplied charset to obtain the bytes for unsafe
     * characters.
     * <p>
     * If the input string is malformed, or if the input cannot be mapped
     * to a valid byte sequence in the given {@code Charset}, then the
     * erroneous input will be replaced with the {@code Charset}'s
     * {@linkplain CharsetEncoder##cae replacement values}.
     *
     * @apiNote The <a href=
     * "http://www.w3.org/TR/html40/appendix/notes.html#non-ascii-chars">
     * World Wide Web Consortium Recommendation</a> states that
     * UTF-8 should be used. Not doing so may introduce incompatibilities.
     * @param   s   {@code String} to be translated.
     * @param charset the given charset
     * @return  the translated {@code String}.
     * @throws NullPointerException if {@code s} or {@code charset} is {@code null}.
     * @spec https://www.w3.org/TR/html4 HTML 4.01 Specification
     * @see URLDecoder#decode(java.lang.String, Charset)
     * @since 10
     */
    public static String encode(String s, Charset charset) {
        Objects.requireNonNull(charset, "charset");

        int i;
        for (i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!DONT_NEED_ENCODING.test(c) || c == ' ') {
                break;
            }
        }
        if (i == s.length()) {
            return s;
        }

        StringBuilder out = new StringBuilder(s.length() << 1);
        if (i > 0) {
            out.append(s, 0, i);
        }

        CharsetEncoder ce = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        CharBuffer cb = CharBuffer.allocate(ENCODING_CHUNK_SIZE);
        ByteBuffer bb = ByteBuffer.allocate((int)(ENCODING_CHUNK_SIZE * ce.maxBytesPerChar()));

        while (i < s.length()) {
            char c = s.charAt(i);
            if (DONT_NEED_ENCODING.test(c)) {
                if (c == ' ') {
                    c = '+';
                }
                out.append(c);
                i++;
            } else {
                // convert to external encoding before hex conversion
                do {
                    cb.put(c);
                    /*
                     * If this character represents the start of a Unicode
                     * surrogate pair, then pass in two characters. It's not
                     * clear what should be done if a byte reserved in the
                     * surrogate pairs range occurs outside a legal
                     * surrogate pair. For now, just treat it as if it were
                     * any other character.
                     */
                    if (Character.isHighSurrogate(c)) {
                        if ((i + 1) < s.length()) {
                            char d = s.charAt(i + 1);
                            if (Character.isLowSurrogate(d)) {
                                cb.put(d);
                                i++;
                            }
                        }
                    }
                    // Limit to ENCODING_CHUNK_SIZE - 1 so that we can always fit in
                    // a surrogate pair on the next iteration
                    if (cb.position() >= ENCODING_CHUNK_SIZE - 1) {
                        flushToStringBuilder(out, ce, cb, bb, false);
                    }
                    i++;
                } while (i < s.length() && !DONT_NEED_ENCODING.test((c = s.charAt(i))));
                flushToStringBuilder(out, ce, cb, bb, true);
            }
        }
        return out.toString();
    }

    /**
     * Encodes input chars in {@code cb} and appends the byte values in an escaped
     * format ({@code "%XX"}) to {@code out}. The temporary byte buffer, {@code bb},
     * must be able to accept {@code cb.position() * ce.maxBytesPerChar()} bytes.
     *
     * @param out the StringBuilder to output encoded and escaped bytes to
     * @param ce charset encoder. Will be reset if endOfInput is true
     * @param cb input buffer, will be cleared
     * @param bb output buffer, will be cleared
     * @param endOfInput true if this is the last flush for an encoding chunk,
     *                  to all bytes in ce is flushed to out and reset
     */
    private static void flushToStringBuilder(StringBuilder out,
                                             CharsetEncoder ce,
                                             CharBuffer cb,
                                             ByteBuffer bb,
                                             boolean endOfInput) {
        cb.flip();
        try {
            CoderResult cr = ce.encode(cb, bb, endOfInput);
            if (!cr.isUnderflow())
                cr.throwException();
            if (endOfInput) {
                cr = ce.flush(bb);
                if (!cr.isUnderflow())
                    cr.throwException();
                ce.reset();
            }
        } catch (CharacterCodingException x) {
            throw new Error(x); // Can't happen
        }
        HexFormat hex = HexFormat.of().withUpperCase();
        byte[] bytes = bb.array();
        int len = bb.position();
        for (int i = 0; i < len; i++) {
            out.append('%');
            hex.toHexDigits(out, bytes[i]);
        }
        cb.clear();
        bb.clear();
    }
}
