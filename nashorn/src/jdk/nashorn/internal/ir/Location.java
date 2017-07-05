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

package jdk.nashorn.internal.ir;

import java.util.Objects;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.Source;

/**
 * Used to locate an entity back to it's source file.
 *
 */

public class Location implements Cloneable {
    /** Source of entity. */
    private final Source source;

    /** Token descriptor. */
    private final long token;

    /**
     * Constructor
     *
     * @param source the source
     * @param token  token
     */
    public Location(final Source source, final long token) {
        this.source = source;
        this.token = token;
    }

    /**
     * Copy constructor
     *
     * @param location source node
     */
    protected Location(final Location location) {
        this.source = location.source;
        this.token = location.token;
    }

    @Override
    protected Object clone() {
        try {
            return super.clone();
        } catch(CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (other == null) {
            return false;
        }

        if (other.getClass() != this.getClass()) {
            return false;
        }

        final Location loc = (Location)other;
        return token == loc.token && Objects.equals(source, loc.source);
    }

    @Override
    public int hashCode() {
        return Token.hashCode(token) ^ Objects.hashCode(source);
    }

    /**
     * Return token position from a token descriptor.
     *
     * @return Start position of the token in the source.
     */
    public int position() {
        return Token.descPosition(token);
    }

    /**
     * Return token length from a token descriptor.
     *
     * @return Length of the token.
     */
    public int length() {
        return Token.descLength(token);
    }

    /**
     * Return token tokenType from a token descriptor.
     *
     * @return Type of token.
     */
    public TokenType tokenType() {
        return Token.descType(token);
    }

    /**
     * Test token tokenType.
     *
     * @param type a type to check this token against
     * @return true if token types match.
     */
    public boolean isTokenType(final TokenType type) {
        return Token.descType(token) == type;
    }

    /**
     * Get the source for this location
     * @return the source
     */
    public Source getSource() {
        return source;
    }

    /**
     * Get the token for this location
     * @return the token
     */
    public long getToken() {
        return token;
    }
}
