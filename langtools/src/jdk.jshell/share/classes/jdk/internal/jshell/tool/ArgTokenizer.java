/*
 * Copyright (c) 1995, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jshell.tool;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Parse command arguments, derived from StreamTokenizer by
 * @author  James Gosling
 */
class ArgTokenizer {

    private final String str;
    private final int length;
    private int next = 0;
    private char buf[] = new char[20];
    private int mark;

    private final byte ctype[] = new byte[256];
    private static final byte CT_ALPHA = 0;
    private static final byte CT_WHITESPACE = 1;
    private static final byte CT_QUOTE = 8;

    private String sval;
    private boolean isQuoted = false;

    ArgTokenizer(String arg) {
        this.str = arg;
        this.length = arg.length();
        quoteChar('"');
        quoteChar('\'');
        whitespaceChars(0x09, 0x0D);
        whitespaceChars(0x1C, 0x20);
        whitespaceChars(0x85, 0x85);
        whitespaceChars(0xA0, 0xA0);
    }

    String next() {
        nextToken();
        return sval;
    }

    String[] next(String... strings) {
        return next(Arrays.stream(strings));
    }

    String[] next(Stream<String> stream) {
        nextToken();
        if (sval == null) {
            return null;
        }
        String[] matches = stream
                .filter(s -> s.startsWith(sval))
                .toArray(size -> new String[size]);
        return matches;
    }

    String val() {
        return sval;
    }

    boolean isQuoted() {
        return isQuoted;
    }

    String whole() {
        return str;
    }

    void mark() {
        mark = next;
    }

    void rewind() {
        next = mark;
    }

    /**
     * Reads a single character.
     *
     * @return The character read, or -1 if the end of the stream has been
     * reached
     */
    private int read() {
        if (next >= length) {
            return -1;
        }
        return str.charAt(next++);
    }

    /**
     * Specifies that all characters <i>c</i> in the range
     * <code>low&nbsp;&lt;=&nbsp;<i>c</i>&nbsp;&lt;=&nbsp;high</code>
     * are white space characters. White space characters serve only to
     * separate tokens in the input stream.
     *
     * <p>Any other attribute settings for the characters in the specified
     * range are cleared.
     *
     * @param   low   the low end of the range.
     * @param   hi    the high end of the range.
     */
    private void whitespaceChars(int low, int hi) {
        if (low < 0)
            low = 0;
        if (hi >= ctype.length)
            hi = ctype.length - 1;
        while (low <= hi)
            ctype[low++] = CT_WHITESPACE;
    }

    /**
     * Specifies that matching pairs of this character delimit string
     * constants in this tokenizer.
     * <p>
     * If a string quote character is encountered, then a string is
     * recognized, consisting of all characters after (but not including)
     * the string quote character, up to (but not including) the next
     * occurrence of that same string quote character, or a line
     * terminator, or end of file. The usual escape sequences such as
     * {@code "\u005Cn"} and {@code "\u005Ct"} are recognized and
     * converted to single characters as the string is parsed.
     *
     * <p>Any other attribute settings for the specified character are cleared.
     *
     * @param   ch   the character.
     */
    private void quoteChar(int ch) {
        if (ch >= 0 && ch < ctype.length)
            ctype[ch] = CT_QUOTE;
    }

    private int unicode2ctype(int c) {
        switch (c) {
            case 0x1680:
            case 0x180E:
            case 0x200A:
            case 0x202F:
            case 0x205F:
            case 0x3000:
                return CT_WHITESPACE;
            default:
                return CT_ALPHA;
        }
    }

    /**
     * Parses the next token of this tokenizer.
     */
    public void nextToken() {
        byte ct[] = ctype;
        int c;
        int lctype;
        sval = null;
        isQuoted = false;

        do {
            c = read();
            if (c < 0) {
                return;
            }
            lctype = (c < 256) ? ct[c] : unicode2ctype(c);
        } while (lctype == CT_WHITESPACE);

        if (lctype == CT_ALPHA) {
            int i = 0;
            do {
                if (i >= buf.length) {
                    buf = Arrays.copyOf(buf, buf.length * 2);
                }
                buf[i++] = (char) c;
                c = read();
                lctype = c < 0 ? CT_WHITESPACE : (c < 256)? ct[c] : unicode2ctype(c);
            } while (lctype == CT_ALPHA);
            if (c >= 0) --next; // push last back
            sval = String.copyValueOf(buf, 0, i);
            return;
        }

        if (lctype == CT_QUOTE) {
            int quote = c;
            int i = 0;
            /* Invariants (because \Octal needs a lookahead):
             *   (i)  c contains char value
             *   (ii) d contains the lookahead
             */
            int d = read();
            while (d >= 0 && d != quote) {
                if (d == '\\') {
                    c = read();
                    int first = c;   /* To allow \377, but not \477 */
                    if (c >= '0' && c <= '7') {
                        c = c - '0';
                        int c2 = read();
                        if ('0' <= c2 && c2 <= '7') {
                            c = (c << 3) + (c2 - '0');
                            c2 = read();
                            if ('0' <= c2 && c2 <= '7' && first <= '3') {
                                c = (c << 3) + (c2 - '0');
                                d = read();
                            } else
                                d = c2;
                        } else
                          d = c2;
                    } else {
                        switch (c) {
                        case 'a':
                            c = 0x7;
                            break;
                        case 'b':
                            c = '\b';
                            break;
                        case 'f':
                            c = 0xC;
                            break;
                        case 'n':
                            c = '\n';
                            break;
                        case 'r':
                            c = '\r';
                            break;
                        case 't':
                            c = '\t';
                            break;
                        case 'v':
                            c = 0xB;
                            break;
                        }
                        d = read();
                    }
                } else {
                    c = d;
                    d = read();
                }
                if (i >= buf.length) {
                    buf = Arrays.copyOf(buf, buf.length * 2);
                }
                buf[i++] = (char)c;
            }

            if (d == quote) {
                isQuoted = true;
            }
            sval = String.copyValueOf(buf, 0, i);
        }
    }
}
