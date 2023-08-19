/*
 * Copyright (c) 1995, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.io.CharArrayWriter;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException ;
import java.util.BitSet;
import java.util.Objects;

import jdk.internal.util.StaticProperty;
import jdk.internal.vm.annotation.Stable;

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
public class URLEncoder {

    @Stable
    private static final boolean[] DONT_NEED_ENCODING = new boolean[128];
    private static final int CASE_DIFF = ('a' - 'A');
    private static final String DEFAULT_ENCODING_NAME;

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

         for (int i = 'a'; i <= 'z'; i++) {
             DONT_NEED_ENCODING[i] = true;
         }
         for (int i = 'A'; i <= 'Z'; i++) {
             DONT_NEED_ENCODING[i] = true;
         }
         for (int i = '0'; i <= '9'; i++) {
             DONT_NEED_ENCODING[i] = true;
         }
         DONT_NEED_ENCODING[' '] = true;
         DONT_NEED_ENCODING['-'] = true;
         // encoding a space to a + is done in the encode() method
         DONT_NEED_ENCODING['_'] = true; 
         DONT_NEED_ENCODING['.'] = true;
         DONT_NEED_ENCODING['*'] = true;

        DEFAULT_ENCODING_NAME = StaticProperty.fileEncoding();
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

        String str = null;

        try {
            str = encode(s, DEFAULT_ENCODING_NAME);
        } catch (UnsupportedEncodingException e) {
            // The system should always have the default charset
        }

        return str;
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

    /**
     * Translates a string into {@code application/x-www-form-urlencoded}
     * format using a specific {@linkplain Charset Charset}.
     * This method uses the supplied charset to obtain the bytes for unsafe
     * characters.
     * <p>
     * <em><strong>Note:</strong> The <a href=
     * "http://www.w3.org/TR/html40/appendix/notes.html#non-ascii-chars">
     * World Wide Web Consortium Recommendation</a> states that
     * UTF-8 should be used. Not doing so may introduce incompatibilities.</em>
     *
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
            if (c >= 128 || !DONT_NEED_ENCODING[c] || c == ' ') {
                break;
            }
        }
        if (i == s.length()) {
            return s;
        }

        if (charset == StandardCharsets.UTF_8) {
            return encodeUTF8(s, i);
        } else {
            return encodeSlow(s, charset, i);
        }
    }

    private static void encodeByte(StringBuilder out, byte b) {
        out.append('%');

        int n0 = (b >> 4) & 0xF;
        if (n0 < 10) {
            out.append((char) ('0' + n0));
        } else {
            out.append((char) ('A' - 10 + n0));
        }

        int n1 = b & 0xF;
        if (n1 < 10) {
            out.append((char) ('0' + n1));
        } else {
            out.append((char) ('A' - 10 + n1));
        }
    }

    private static String encodeUTF8(String s, int suffixOffset) {
        StringBuilder out = new StringBuilder(s.length() << 1);
        if (suffixOffset > 0) {
            out.append(s, 0, suffixOffset);
        }

        for (int i = suffixOffset; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                if (DONT_NEED_ENCODING[c]) {
                    if (c == ' ') {
                        c = '+';
                    }
                    out.append(c);
                } else {
                    encodeByte(out, (byte) c);
                }
            } else if (c < 0x800) {
                encodeByte(out, (byte) (0xc0 | (c >> 6)));
                encodeByte(out, (byte) (0x80 | (c & 0x3f)));
            } else if (Character.isHighSurrogate(c)) {
                if (i < s.length() - 1) {
                    char d = s.charAt(i + 1);
                    if (Character.isLowSurrogate(d)) {
                        int uc = Character.toCodePoint(c, d);
                        encodeByte(out, (byte) (0xf0 | ((uc >> 18))));
                        encodeByte(out, (byte) (0x80 | ((uc >> 12) & 0x3f)));
                        encodeByte(out, (byte) (0x80 | ((uc >> 6) & 0x3f)));
                        encodeByte(out, (byte) (0x80 | (uc & 0x3f)));
                        i++;
                        continue;
                    }
                }

                // Replace unmappable characters
                encodeByte(out, (byte) '?');
            } else {
                encodeByte(out, (byte) (0xe0 | ((c >> 12))));
                encodeByte(out, (byte) (0x80 | ((c >> 6) & 0x3f)));
                encodeByte(out, (byte) (0x80 | (c & 0x3f)));
            }
        }

        return out.toString();
    }

    private static String encodeSlow(String s, Charset charset, int suffixOffset) {
        StringBuilder out = new StringBuilder(s.length() << 1);
        CharArrayWriter charArrayWriter = new CharArrayWriter();
        if (suffixOffset > 0) {
            out.append(s, 0, suffixOffset);
        }

        for (int i = suffixOffset; i < s.length(); ) {
            char c = s.charAt(i);
            if (c < 128 && DONT_NEED_ENCODING[c]) {
                if (c == ' ') {
                    c = '+';
                }
                out.append(c);
                i++;
            } else {
                // convert to external encoding before hex conversion
                do {
                    charArrayWriter.write(c);
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
                                charArrayWriter.write(d);
                                i++;
                            }
                        }
                    }
                    i++;
                } while (i < s.length() && (c = s.charAt(i)) < 128 && !DONT_NEED_ENCODING[c]);

                charArrayWriter.flush();
                String str = charArrayWriter.toString();
                byte[] ba = str.getBytes(charset);
                for (byte b : ba) {
                    encodeByte(out, b);
                }
                charArrayWriter.reset();
            }
        }

        return out.toString();
    }
}
