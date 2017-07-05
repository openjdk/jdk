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

package jdk.nashorn.internal.runtime.regexp;

import java.util.regex.MatchResult;
import jdk.nashorn.internal.runtime.BitVector;
import jdk.nashorn.internal.runtime.ECMAErrors;
import jdk.nashorn.internal.runtime.ParserException;

/**
 * This is the base class for representing a parsed regular expression.
 *
 * Instances of this class are created by a {@link RegExpFactory}.
 */
public abstract class RegExp {

    /** Pattern string. */
    private final String source;

    /** Global search flag for this regexp.*/
    private boolean global;

    /** Case insensitive flag for this regexp */
    private boolean ignoreCase;

    /** Multi-line flag for this regexp */
    private boolean multiline;

    /** BitVector that keeps track of groups in negative lookahead */
    protected BitVector groupsInNegativeLookahead;

    /**
     * Constructor.
     *
     * @param source the source string
     * @param flags the flags string
     */
    protected RegExp(final String source, final String flags) {
        this.source = source.length() == 0 ? "(?:)" : source;
        for (int i = 0; i < flags.length(); i++) {
            final char ch = flags.charAt(i);
            switch (ch) {
            case 'g':
                if (this.global) {
                    throwParserException("repeated.flag", "g");
                }
                this.global = true;
                break;
            case 'i':
                if (this.ignoreCase) {
                    throwParserException("repeated.flag", "i");
                }
                this.ignoreCase = true;
                break;
            case 'm':
                if (this.multiline) {
                    throwParserException("repeated.flag", "m");
                }
                this.multiline = true;
                break;
            default:
                throwParserException("unsupported.flag", Character.toString(ch));
            }
        }
    }

    /**
     * Get the source pattern of this regular expression.
     *
     * @return the source string
     */
    public String getSource() {
        return source;
    }

    /**
     * Set the global flag of this regular expression to {@code global}.
     *
     * @param global the new global flag
     */
    public void setGlobal(final boolean global) {
        this.global = global;
    }

    /**
     * Get the global flag of this regular expression.
     *
     * @return the global flag
     */
    public boolean isGlobal() {
        return global;
    }

    /**
     * Get the ignore-case flag of this regular expression.
     *
     * @return the ignore-case flag
     */
    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    /**
     * Get the multiline flag of this regular expression.
     *
     * @return the multiline flag
     */
    public boolean isMultiline() {
        return multiline;
    }

    /**
     * Get a bitset indicating which of the groups in this regular expression are inside a negative lookahead.
     *
     * @return the groups-in-negative-lookahead bitset
     */
    public BitVector getGroupsInNegativeLookahead() {
        return groupsInNegativeLookahead;
    }

    /**
     * Match this regular expression against {@code str}, starting at index {@code start}
     * and return a {@link MatchResult} with the result.
     *
     * @param str the string
     * @return the matcher
     */
    public abstract RegExpMatcher match(String str);

    /**
     * Throw a regexp parser exception.
     *
     * @param key the message key
     * @param str string argument
     * @throws jdk.nashorn.internal.runtime.ParserException unconditionally
     */
    protected static void throwParserException(final String key, final String str) throws ParserException {
        throw new ParserException(ECMAErrors.getMessage("parser.error.regex." + key, str));
    }
}
