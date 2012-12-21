/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
    private Source source;
    // token responsible for this exception
    private long token;
    // if this is traslated as ECMA error, which type should be used?
    private JSErrorType errorType;

    /**
     * Constructor
     *
     * @param msg exception message for this parser error.
     */
    public ParserException(final String msg) {
        this(msg, null, -1, -1, -1);
    }

    /**
     * Constructor
     *
     * @param msg      exception message
     * @param source   source from which this exception originates
     * @param line     line number of exception
     * @param column   column number of exception
     * @param token    token from which this exception originates
     *
     */
    public ParserException(final String msg, final Source source, final int line, final int column, final long token) {
        super(msg);
        setSource(source);
        if (source != null) {
            setFileName(source.getName());
        }
        setLineNumber(line);
        setColumnNumber(column);
        setToken(token);
        setErrorType(JSErrorType.SYNTAX_ERROR);
    }

    /**
     * Get the {@code Source} of this {@code ParserException}
     * @return source
     */
    public Source getSource() {
        return source;
    }

    /**
     * Set the {@code Source} of this {@code ParserException}
     * @param source script source
     */
    public void setSource(Source source) {
        this.source = source;
    }

    /**
     * Get the token responsible for this {@code ParserException}
     * @return token
     */
    public long getToken() {
        return token;
    }

    /**
     * Set the errand token of this {@code ParserException}
     * @param token token responsible for this ParserException
     */
    public void setToken(final long token) {
        this.token = token;
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
     * Set the {@code JSErrorType} of this {@code ParserException}
     * @param errorType error type
     */
    public void setErrorType(final JSErrorType errorType) {
        this.errorType = errorType;
    }

    /**
     * Throw this {@code ParserException} as one of the 7 native JavaScript errors
     * @param global global scope object
     */
    public void throwAsEcmaException(final ScriptObject global) {
        ECMAErrors.throwAsEcmaException(global, this);
    }
}

