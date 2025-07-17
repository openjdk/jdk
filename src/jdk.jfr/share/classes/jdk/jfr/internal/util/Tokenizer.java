/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.util;

import java.text.ParseException;

public final class Tokenizer implements AutoCloseable {
    private final String text;
    private final char[] separators;
    private int index;

    /**
     * Constructs a Tokenizer.
     *
     * @param text       text to tokenize
     * @param separators separator, for example ',' or ';'
     */
    public Tokenizer(String text, char... separators) {
        this.text = text;
        this.separators = separators;
    }

    /**
     * If the next token matches a string, it is consumed and {@code true} returned,
     * {@code false} otherwise.
     */
    public boolean accept(String match) {
        skipWhitespace();
        int t = 0;
        while (index + t < text.length() && t < match.length()) {
            char c = Character.toLowerCase(text.charAt(index + t));
            char d = Character.toLowerCase(match.charAt(t));
            if (d != c) {
                return false;
            }
            t++;
            if (isSeparator(c)) {
                break;
            }
        }
        if (t == match.length()) {
            index += match.length();
            return true;
        }
        return false;
    }

    /**
     * Similar to accept(String), but requires several tokens to match.
     */
    public boolean accept(String... matches) {
        int position = index;
        for (String s : matches) {
            if (!accept(s)) {
                index = position;
                return false;

            }
        }
        return true;
    }

    /**
     * Similar to accept(String...), but sufficient if one token matches.
     *
     * @param matches
     * @return
     */
    public boolean acceptAny(String... matches) {
        for (String match : matches) {
            if (accept(match)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return {@code true} if there are more tokens.
     */
    public boolean hasNext() {
        int k = index;
        while (k < text.length()) {
            char c = text.charAt(k);
            if (!Character.isWhitespace(c)) {
                return true;
            }
            k++;
        }
        return false;
    }

    /**
     * Throws exception if the next token doesn't match.
     */
    public void expect(String expected) throws ParseException {
        if (!accept(expected)) {
            throw new ParseException("Expected " + expected, index);
        }
    }

    /**
     * Return the next character without consuming it, or throw exception if
     * {@code EOF} is reached.
     */
    public char peekChar() throws ParseException {
        skipWhitespace();
        if (index < text.length()) {
            return text.charAt(index);
        }
        throw new ParseException("Unexpected EOF reached", index);
    }

    /**
     * Return the next token, or throw exception if {@code EOF} is reached.
     */
    public String next() throws ParseException {
        skipWhitespace();
        StringBuilder sb = new StringBuilder();
        while (index < text.length()) {
            char c = text.charAt(index);
            if (isQuoteCharacter(c)) {
                int p = findNext(c);
                String t = text.substring(index + 1, p);
                sb.append(t);
                index = p;
            } else {
                if (isSeparator(c)) {
                    if (sb.isEmpty()) {
                        index++;
                        return String.valueOf(c); // Inte helt optimalt
                    } else {
                        return sb.toString();
                    }
                }
                if (Character.isWhitespace(c)) {
                    return sb.toString();
                }
                sb.append(c);
            }
            index++;
        }
        if (sb.isEmpty()) {
            throw new ParseException("Unexpected EOF reached", index);
        }
        return sb.toString();
    }

    /**
     * Skips all character until {@code '\n'}
     */
    public void skipLine() {
        while (index < text.length()) {
            char c = text.charAt(index++);
            if (c == '\n') {
                return;
            }
        }
    }

    /**
     * Returns the current position in the text.
     */
    public int getPosition() {
        return index;
    }

    private void skipWhitespace() {
        while (index < text.length()) {
            char c = text.charAt(index);
            if (!Character.isWhitespace(c)) {
                return;
            }
            index++;
        }
    }

    private boolean isSeparator(char c) {
        for (char separator : separators) {
            if (c == separator) {
                return true;
            }
        }
        return false;
    }

    private int findNext(char c) throws ParseException {
        for (int p = index + 1; p < text.length(); p++) {
            if (c == text.charAt(p)) {
                return p;
            }
        }
        throw new ParseException("Could not find match " + c, index);
    }

    private boolean isQuoteCharacter(char c) {
        return c == '\'' || c == '"';
    }

    /**
     * Closes the Tokenizer.
     * <p>
     * Requires that all the tokens have been consumed.
     *
     * @throws ParseException if there are tokens left
     */
    @Override
    public void close() throws ParseException {
        skipWhitespace();
        if (index != text.length()) {
            throw new ParseException("Unexpected token '" + next() + "' found", getPosition());
        }
    }
}
