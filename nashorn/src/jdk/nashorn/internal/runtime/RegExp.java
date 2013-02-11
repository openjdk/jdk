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

package jdk.nashorn.internal.runtime;

import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.MULTILINE;
import static java.util.regex.Pattern.UNICODE_CASE;

import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * This class is used to represent a parsed regular expression. Accepts input
 * pattern string and flagString. This is used by AbstractParser to validate
 * RegExp literals as well as by NativeRegExp to parse RegExp constructor arguments.
 */
public final class RegExp {
    /** Pattern string. */
    private final String input;

    /** Global search flag for this regexp.*/
    private boolean global;

    /** Case insensitive flag for this regexp */
    private boolean ignoreCase;

    /** Multi-line flag for this regexp */
    private boolean multiline;

    /** Java regexp pattern to use for match. We compile to one of these */
    private Pattern pattern;

    /** BitVector that keeps track of groups in negative lookahead */
    private BitVector groupsInNegativeLookahead;

    /**
     * Creates RegExpLiteral object from given input and flagString.
     *
     * @param input RegExp pattern string
     * @param flagString RegExp flags
     * @throws ParserException if flagString is invalid or input string has syntax error.
     */
    public RegExp(final String input, final String flagString) throws ParserException {
        this.input = input;
        final HashSet<Character> usedFlags = new HashSet<>();
        int flags = 0;

        for (final char ch : flagString.toCharArray()) {
            if (usedFlags.contains(ch)) {
                throwParserException("repeated.flag", Character.toString(ch));
            }

            switch (ch) {
            case 'g':
                this.global = true;
                usedFlags.add(ch);
                break;
            case 'i':
                this.ignoreCase = true;
                flags |= CASE_INSENSITIVE | UNICODE_CASE;
                usedFlags.add(ch);
                break;
            case 'm':
                this.multiline = true;
                flags |= MULTILINE;
                usedFlags.add(ch);
                break;
            default:
                throwParserException("unsupported.flag", Character.toString(ch));
            }
        }

        try {
            RegExpScanner parsed;

            try {
                parsed = RegExpScanner.scan(input);
            } catch (final PatternSyntaxException e) {
                // refine the exception with a better syntax error, if this
                // passes, just rethrow what we have
                Pattern.compile(input, flags);
                throw e;
            }

            if (parsed != null) {
                this.pattern = Pattern.compile(parsed.getJavaPattern(), flags);
                this.groupsInNegativeLookahead = parsed.getGroupsInNegativeLookahead();
            }
        } catch (final PatternSyntaxException e2) {
            throwParserException("syntax", e2.getMessage());
        }

    }

    /**
     * @return the input
     */
    public String getInput() {
        return input;
    }

    /**
     * @return the global
     */
    public boolean isGlobal() {
        return global;
    }

    /**
     * @return the ignoreCase
     */
    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    /**
     * @return the multiline
     */
    public boolean isMultiline() {
        return multiline;
    }

    /**
     * @return the pattern
     */
    public Pattern getPattern() {
        return pattern;
    }

    /**
     * @return the groupsInNegativeLookahead
     */
    public BitVector getGroupsInNegativeLookahead() {
        return groupsInNegativeLookahead;
    }

    /**
     * Validation method for RegExp input and flagString - we don't care about the RegExp object
     *
     * @param input        regexp input
     * @param flagString   flag string
     *
     * @throws ParserException if invalid regexp and flags
     */
    @SuppressWarnings({"unused", "ResultOfObjectAllocationIgnored"})
    public static void validate(final String input, final String flagString) throws ParserException {
        new RegExp(input, flagString);
    }

    private static void throwParserException(final String key, final String str) throws ParserException {
        throw new ParserException(ECMAErrors.getMessage("parser.error.regex." + key, str));
    }
}
