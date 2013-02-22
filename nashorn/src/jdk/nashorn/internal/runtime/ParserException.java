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

import jdk.nashorn.api.scripting.NashornException;
import jdk.nashorn.internal.parser.Token;

/**
 * ECMAScript parser exceptions.
 */
@SuppressWarnings("serial")
public final class ParserException extends NashornException {
    // Source from which this ParserException originated
    private final Source source;
    // token responsible for this exception
    private final long token;
    // if this is traslated as ECMA error, which type should be used?
    private final JSErrorType errorType;

    /**
     * Constructor
     *
     * @param msg exception message for this parser error.
     */
    public ParserException(final String msg) {
        this(JSErrorType.SYNTAX_ERROR, msg, null, -1, -1, -1);
    }

    /**
     * Constructor
     *
     * @param errorType error type
     * @param msg       exception message
     * @param source    source from which this exception originates
     * @param line      line number of exception
     * @param column    column number of exception
     * @param token     token from which this exception originates
     *
     */
    public ParserException(final JSErrorType errorType, final String msg, final Source source, final int line, final int column, final long token) {
        super(msg, source != null ? source.getName() : null, line, column);
        this.source = source;
        this.token = token;
        this.errorType = errorType;
    }

    /**
     * Get the {@code Source} of this {@code ParserException}
     * @return source
     */
    public Source getSource() {
        return source;
    }

    /**
     * Get the token responsible for this {@code ParserException}
     * @return token
     */
    public long getToken() {
        return token;
    }

    /**
     * Get token position within source where the error originated.
     * @return token position if available, else -1
     */
    public int getPosition() {
        return Token.descPosition(token);
    }

    /**
     * Get the {@code JSErrorType} of this {@code ParserException}
     * @return error type
     */
    public JSErrorType getErrorType() {
        return errorType;
    }

    /**
     * Throw this {@code ParserException} as one of the 7 native JavaScript errors
     */
    public void throwAsEcmaException() {
        throw ECMAErrors.asEcmaException(this);
    }

    /**
     * Throw this {@code ParserException} as one of the 7 native JavaScript errors
     * @param global global scope object
     */
    public void throwAsEcmaException(final ScriptObject global) {
        throw ECMAErrors.asEcmaException(global, this);
    }
}

