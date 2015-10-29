/*
 * Copyright (c) 2010, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.parser;

import static jdk.nashorn.internal.parser.TokenKind.LITERAL;

import jdk.nashorn.internal.runtime.Source;

/**
 * Basic parse/lex unit.
 *
 */
public class Token {

    private Token() {
    }

    /**
     * Create a compact form of token information.
     * @param type     Type of token.
     * @param position Start position of the token in the source.
     * @param length   Length of the token.
     * @return Token descriptor.
     */
    public static long toDesc(final TokenType type, final int position, final int length) {
        return (long)position << 32 |
               (long)length   << 8  |
               type.ordinal();
    }

    /**
     * Extract token position from a token descriptor.
     * @param token Token descriptor.
     * @return Start position of the token in the source.
     */
    public static int descPosition(final long token) {
        return (int)(token >>> 32);
    }

    /**
     * Normally returns the token itself, except in case of string tokens
     * which report their position past their opening delimiter and thus
     * need to have position and length adjusted.
     *
     * @param token Token descriptor.
     * @return same or adjusted token.
     */
    public static long withDelimiter(final long token) {
        final TokenType tokenType = Token.descType(token);
        switch(tokenType) {
            case STRING:
            case ESCSTRING:
            case EXECSTRING:
            case TEMPLATE:
            case TEMPLATE_TAIL: {
                final int start = Token.descPosition(token) - 1;
                final int len = Token.descLength(token) + 2;
                return toDesc(tokenType, start, len);
            }
            case TEMPLATE_HEAD:
            case TEMPLATE_MIDDLE: {
                final int start = Token.descPosition(token) - 1;
                final int len = Token.descLength(token) + 3;
                return toDesc(tokenType, start, len);
            }
            default: {
                return token;
            }
        }
    }

    /**
     * Extract token length from a token descriptor.
     * @param token Token descriptor.
     * @return Length of the token.
     */
    public static int descLength(final long token) {
        return (int)token >>> 8;
    }

    /**
     * Extract token type from a token descriptor.
     * @param token Token descriptor.
     * @return Type of token.
     */
    public static TokenType descType(final long token) {
        return TokenType.getValues()[(int)token & 0xff];
    }

    /**
     * Change the token to use a new type.
     *
     * @param token   The original token.
     * @param newType The new token type.
     * @return The recast token.
     */
    public static long recast(final long token, final TokenType newType) {
        return token & ~0xFFL | newType.ordinal();
    }

    /**
     * Return a string representation of a token.
     * @param source  Token source.
     * @param token   Token descriptor.
     * @param verbose True to include details.
     * @return String representation.
     */
    public static String toString(final Source source, final long token, final boolean verbose) {
        final TokenType type = Token.descType(token);
        String result;

        if (source != null && type.getKind() == LITERAL) {
            result = source.getString(token);
        } else {
            result = type.getNameOrType();
        }

        if (verbose) {
            final int position = Token.descPosition(token);
            final int length = Token.descLength(token);
            result += " (" + position + ", " + length + ")";
        }

        return result;
    }

    /**
     * String conversion of token
     *
     * @param source the source
     * @param token  the token
     *
     * @return token as string
     */
    public static String toString(final Source source, final long token) {
        return Token.toString(source, token, false);
    }

    /**
     * String conversion of token - version without source given
     *
     * @param token  the token
     *
     * @return token as string
     */
    public static String toString(final long token) {
        return Token.toString(null, token, false);
    }

    /**
     * Static hash code computation function token
     *
     * @param token a token
     *
     * @return hash code for token
     */
    public static int hashCode(final long token) {
        return (int)(token ^ token >>> 32);
    }

}
