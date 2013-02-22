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

import jdk.nashorn.internal.parser.Lexer;
import jdk.nashorn.internal.runtime.ParserException;

/**
 * Factory class for regular expressions. This class creates instances of {@link DefaultRegExp}.
 */
public class RegExpFactory {


    private final static RegExpFactory instance = new RegExpFactory();

    /**
     * Creates a Regular expression from the given {@code pattern} and {@code flags} strings.
     *
     * @param pattern RegExp pattern string
     * @param flags RegExp flags string
     * @throws ParserException if flags is invalid or pattern string has syntax error.
     */
    protected RegExp compile(final String pattern, final String flags) throws ParserException {
        return new DefaultRegExp(pattern, flags);
    }

    /**
     * Replace a regexp token as suitable for regexp instances created by this factory.
     *
     * @param str a regular expression token
     * @return the replacement token
     */
    protected String replaceToken(final String str) {
        switch (str) {
            case "\\s":
                return "[" + Lexer.getWhitespaceRegExp() + "]";
            case "\\S":
                return "[^" + Lexer.getWhitespaceRegExp() + "]";
            case "[^]":
                return "[\\s\\S]";
            default:
                return str;
        }
    }

    /**
     * Compile a regexp with the given {@code source} and {@code flags}.
     *
     * @param pattern RegExp pattern string
     * @param flags  flag string
     *
     * @throws ParserException if invalid source or flags
     */
    public static RegExp create(final String pattern, final String flags) {
        return instance.compile(pattern,  flags);
    }

    /**
     * Replace a regexp token as needed by the currently installed factory instance.
     *
     * @param token a regexp token
     * @return the replacement token
     */
    public static String replace(final String token) {
        return instance.replaceToken(token);
    }

    /**
     * Validate a regexp with the given {@code source} and {@code flags}.
     *
     * @param pattern RegExp pattern string
     * @param flags  flag string
     *
     * @throws ParserException if invalid source or flags
     */
    // @SuppressWarnings({"unused"})
    public static void validate(final String pattern, final String flags) throws ParserException {
        instance.compile(pattern, flags);
    }
}
