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
import java.nio.charset.CharacterCodingException;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException ;
import java.util.BitSet;
import java.util.Objects;
import java.util.function.IntPredicate;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.util.ImmutableBitSetPredicate;
import jdk.internal.util.StaticProperty;

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
    private static final JavaLangAccess jla = SharedSecrets.getJavaLangAccess();

    private static final long DONT_NEED_ENCODING_FLAGS_0;
    private static final long DONT_NEED_ENCODING_FLAGS_1;

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
        long flag0 = 0;
        flag0 |= 1L << ' '; // ASCII 32
        flag0 |= 1L << '*'; // ASCII 42
        flag0 |= 1L << '-'; // ASCII 25
        flag0 |= 1L << '.'; // ASCII 46

        // ASCII 48 - 57
        for (int i = '0'; i <= '9'; ++i) {
            flag0 |= 1L << i;
        }
        DONT_NEED_ENCODING_FLAGS_0 = flag0;

        long flags1 = 0;
        // ASCII 65 - 90
        for (int i = 'A'; i <= 'Z'; ++i) {
            flags1 |= 1L << (i - 64);
        }
        flags1 |= 1L << ('_' - 64); // ASCII 95
        // ASCII 97 - 122
        for (int i = 'a'; i <= 'z'; ++i) {
            flags1 |= 1L << (i - 64);
        }
        DONT_NEED_ENCODING_FLAGS_1 = flags1;

        DEFAULT_ENCODING_NAME = StaticProperty.fileEncoding();
    }

    /**
     * dotNeedEncoding
     */
    private static boolean dotNeedEncoding(int c) {
        int prefix = (c >>> 6);
        if (prefix > 1) {
            return false;
        }
        long flags = prefix == 0 ? DONT_NEED_ENCODING_FLAGS_0 : DONT_NEED_ENCODING_FLAGS_1;
        return (flags & (1L << (c & 0x3f))) != 0;
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

        int spaceCount = 0;
        int needEncodingCount = 0;
        boolean utf8 = charset == StandardCharsets.UTF_8;
        int ut8Length = 0;
        boolean surrogateError = false;
        for (int i = 0, length = s.length(); i < length; ++i) {
            char c = s.charAt(i);
            if (c < 0x80) {
                if (c == ' ') {
                    spaceCount++;
                }
                if (!dotNeedEncoding(c)) {
                    needEncodingCount++;
                }
            } else {
                needEncodingCount++;
                if (utf8) {
                    if (c < 0x800) {
                        // 2 bytes
                        ut8Length += 3;
                    } else if (Character.isSurrogate(c)) {
                        // Have a surrogate pair
                        if (Character.isHighSurrogate(c) && i + 1 < length) {
                            char d = s.charAt(i + 1);
                            if (Character.isLowSurrogate(d)) {
                                ut8Length += 8;
                                i++;
                                continue;
                            }
                        }
                        surrogateError = true;
                        ut8Length += 3;
                    } else {
                        // 3 bytes
                        ut8Length += 6;
                    }
                }
            }
        }

        if (needEncodingCount == 0 && spaceCount == 0) {
            // not need change
            return s;
        }

        if (utf8 & !surrogateError) {
            return encodeUTF8(s, needEncodingCount, ut8Length);
        }

        return encodeSlow(s, charset);
    }

    private static String encodeUTF8(String s, int needEncodingCount, int ut8Length) {
        byte[] buf = new byte[s.length() + needEncodingCount * 2 + ut8Length];

        int off = 0;
        for (int i = 0, length = s.length(); i < length; ++i) {
            char c = s.charAt(i);
            if (c < 0x80) {
                if (dotNeedEncoding(c)) {
                    buf[off++] = (byte) (c == ' ' ? '+' : c);
                } else {
                    putEncoded(buf, off, c);
                    off += 3;
                }
            } else if (c < 0x800) {
                // 2 bytes, 11 bits
                utf8Put2(buf, off, c);
                off += 6;
            } else if (Character.isSurrogate(c)) {
                // Have a surrogate pair and not handle surrogate error
                char d = s.charAt(i + 1);
                utf8PutSurrogatePair(
                        buf,
                        off,
                        Character.toCodePoint(c, d));
                off += 12;
                i++;
            } else {
                // 3 bytes
                utf8Put3(buf, off, c);
                off += 9;
            }
        }

        try {
            return jla.newStringNoRepl(buf, StandardCharsets.ISO_8859_1);
        } catch (CharacterCodingException cce) {
            throw new AssertionError(cce);
        }
    }

    private static void utf8PutSurrogatePair(byte[] buf, int off, int uc) {
        putEncoded(buf, off, 0xf0 | (uc >> 18));
        putEncoded(buf, off + 3, 0x80 | ((uc >> 12) & 0x3f));
        putEncoded(buf, off + 6, 0x80 | ((uc >> 6) & 0x3f));
        putEncoded(buf, off + 9, 0x80 | (uc & 0x3f));
    }

    private static void utf8Put2(byte[] buf, int off, int c) {
        putEncoded(buf, off, 0xc0 | (c >> 6));
        putEncoded(buf, off + 3, 0x80 | (c & 0x3f));
    }

    private static void utf8Put3(byte[] buf, int off, int c) {
        putEncoded(buf, off, 0xe0 | (c >> 12));
        putEncoded(buf, off + 3, 0x80 | ((c >> 6) & 0x3f));
        putEncoded(buf, off + 6, 0x80 | (c & 0x3f));
    }

    private static void putEncoded(byte[] buf, int off, int c) {
        buf[off] = '%';
        int n0 = (c >> 4) & 0xf;
        buf[off + 1] = (byte) (n0 + (n0 < 10 ? '0' : 'A' - 10));
        int n1 = c & 0xf;
        buf[off + 2] = (byte) (n1 + (n1 < 10 ? '0' : 'A' - 10));
    }

    private static String encodeSlow(String s, Charset charset) {
        boolean needToChange = false;
        StringBuilder out = new StringBuilder(s.length());
        CharArrayWriter charArrayWriter = new CharArrayWriter();

        for (int i = 0; i < s.length();) {
            int c = s.charAt(i);
            //System.out.println("Examining character: " + c);
            if (dotNeedEncoding(c)) {
                if (c == ' ') {
                    c = '+';
                    needToChange = true;
                }
                //System.out.println("Storing: " + c);
                out.append((char)c);
                i++;
            } else {
                // convert to external encoding before hex conversion
                do {
                    charArrayWriter.write(c);
                    /*
                     * If this character represents the start of a Unicode
                     * surrogate pair, then pass in two characters. It's not
                     * clear what should be done if a byte reserved in the
                     * surrogate pairs range occurs outside of a legal
                     * surrogate pair. For now, just treat it as if it were
                     * any other character.
                     */
                    if (c >= 0xD800 && c <= 0xDBFF) {
                        /*
                          System.out.println(Integer.toHexString(c)
                          + " is high surrogate");
                        */
                        if ( (i+1) < s.length()) {
                            int d = s.charAt(i+1);
                            /*
                              System.out.println("\tExamining "
                              + Integer.toHexString(d));
                            */
                            if (d >= 0xDC00 && d <= 0xDFFF) {
                                /*
                                  System.out.println("\t"
                                  + Integer.toHexString(d)
                                  + " is low surrogate");
                                */
                                charArrayWriter.write(d);
                                i++;
                            }
                        }
                    }
                    i++;
                } while (i < s.length() && !dotNeedEncoding((c = s.charAt(i))));

                charArrayWriter.flush();
                String str = charArrayWriter.toString();
                byte[] ba = str.getBytes(charset);
                for (byte b : ba) {
                    out.append('%');
                    char ch = Character.forDigit((b >> 4) & 0xF, 16);
                    // converting to use uppercase letter as part of
                    // the hex value if ch is a letter.
                    if (Character.isLetter(ch)) {
                        ch -= CASE_DIFF;
                    }
                    out.append(ch);
                    ch = Character.forDigit(b & 0xF, 16);
                    if (Character.isLetter(ch)) {
                        ch -= CASE_DIFF;
                    }
                    out.append(ch);
                }
                charArrayWriter.reset();
                needToChange = true;
            }
        }

        return (needToChange? out.toString() : s);
    }
}
