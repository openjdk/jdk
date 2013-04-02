/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.nashorn.internal.parser.TokenKind.SPECIAL;
import static jdk.nashorn.internal.parser.TokenType.IDENT;

/**
 * Fast lookup of operators and keywords.
 *
 */
public final class TokenLookup {
    /**
     * Lookup table for tokens.
     */
    private static final TokenType[] table;

    /**
     * Table base character.
     */
    private static final int tableBase = ' ';

    /**
     * Table base character.
     */
    private static final int tableLimit = '~';

    /**
     * Table size.
     */
    private static final int tableLength = tableLimit - tableBase + 1;

    static {
        // Construct the table.
        table = new TokenType[tableLength];

        // For each token type.
        for (final TokenType tokenType : TokenType.getValues()) {
            // Get the name.
            final String name = tokenType.getName();

            // Filter tokens.
            if (name == null) {
                continue;
            }

            // Ignore null and special.
            if (tokenType.getKind() != SPECIAL) {
                // Get the first character of the name.
                final char first = name.charAt(0);
                // Translate that character into a table index.
                final int index = first - tableBase;
                assert index < tableLength : "Token name does not fit lookup table";

                // Get the length of the token so that the longest come first.
                final int length = tokenType.getLength();
                // Prepare for table insert.
                TokenType prev = null;
                TokenType next = table[index];

                // Find the right spot in the table.
                while(next != null && next.getLength() > length) {
                    prev = next;
                    next = next.getNext();
                }

                // Insert in table.
                tokenType.setNext(next);

                if (prev == null) {
                    table[index] = tokenType;
                } else {
                    prev.setNext(tokenType);
                }
            }
        }
    }

    private TokenLookup() {
    }

    /**
     * Lookup keyword.
     *
     * @param content parse content char array
     * @param position index of position to start looking
     * @param length   max length to scan
     *
     * @return token type for keyword
     */
    public static TokenType lookupKeyword(final char[] content, final int position, final int length) {
        assert table != null : "Token lookup table is not initialized";

        // First character of keyword.
        final char first = content[position];

        // Must be lower case character.
        if ('a' <= first && first <= 'z') {
            // Convert to table index.
            final int index = first - tableBase;
            // Get first bucket entry.
            TokenType tokenType = table[index];

            // Search bucket list.
            while (tokenType != null) {
                final int tokenLength = tokenType.getLength();

                // if we have a length match maybe a keyword.
                if (tokenLength == length) {
                    // Do an exact compare of string.
                    final String name = tokenType.getName();
                    int i;
                    for (i = 0; i < length; i++) {
                        if (content[position + i] != name.charAt(i)) {
                            break;
                        }
                    }

                    if (i == length) {
                        // Found a match.
                        return tokenType;
                    }
                } else if (tokenLength < length) {
                    // Rest of tokens are shorter.
                    break;
                }

                // Try next token.
                tokenType = tokenType.getNext();
            }
        }

        // Not found.
        return IDENT;
    }


    /**
     * Lookup operator.
     *
     * @param ch0 0th char in stream
     * @param ch1 1st char in stream
     * @param ch2 2nd char in stream
     * @param ch3 3rd char in stream
     *
     * @return the token type for the operator
     */
    public static TokenType lookupOperator(final char ch0, final char ch1, final char ch2, final char ch3) {
        assert table != null : "Token lookup table is not initialized";

        // Ignore keyword entries.
        if (tableBase < ch0 && ch0 <= tableLimit && !('a' <= ch0 && ch0 <= 'z')) {
            // Convert to index.
            final int index = ch0 - tableBase;
            // Get first bucket entry.
            TokenType tokenType = table[index];

            // Search bucket list.
            while (tokenType != null) {
                final String name = tokenType.getName();

                switch (name.length()) {
                case 1:
                    // One character entry.
                    return tokenType;
                case 2:
                    // Two character entry.
                    if (name.charAt(1) == ch1) {
                        return tokenType;
                    }
                    break;
                case 3:
                    // Three character entry.
                    if (name.charAt(1) == ch1 &&
                        name.charAt(2) == ch2) {
                        return tokenType;
                    }
                    break;
                case 4:
                    // Four character entry.
                    if (name.charAt(1) == ch1 &&
                        name.charAt(2) == ch2 &&
                        name.charAt(3) == ch3) {
                        return tokenType;
                    }
                    break;
                default:
                    break;
                }

                // Try next token.
                tokenType = tokenType.getNext();
            }
        }

        // Not found.
        return null;
    }
}
