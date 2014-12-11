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

import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;

/**
 * IR representation of a property access (period operator.)
 */
@Immutable
public final class AccessNode extends BaseNode {
    private static final long serialVersionUID = 1L;

    /** Property name. */
    private final String property;

    /**
     * Constructor
     *
     * @param token     token
     * @param finish    finish
     * @param base      base node
     * @param property  property
     */
    public AccessNode(final long token, final int finish, final Expression base, final String property) {
        super(token, finish, base, false);
        this.property = property;
    }

    private AccessNode(final AccessNode accessNode, final Expression base, final String property, final boolean isFunction, final Type type, final int id) {
        super(accessNode, base, isFunction, type, id);
        this.property = property;
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterAccessNode(this)) {
            return visitor.leaveAccessNode(
                setBase((Expression)base.accept(visitor)));
        }
        return this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        final boolean needsParen = tokenType().needsParens(getBase().tokenType(), true);

        if (printType) {
            optimisticTypeToString(sb);
        }

        if (needsParen) {
            sb.append('(');
        }

        base.toString(sb, printType);

        if (needsParen) {
            sb.append(')');
        }
        sb.append('.');

        sb.append(property);
    }

    /**
     * Get the property name
     *
     * @return the property name
     */
    public String getProperty() {
        return property;
    }

    /**
     * Return true if this node represents an index operation normally represented as {@link IndexNode}.
     * @return true if an index access.
     */
    public boolean isIndex() {
        return Token.descType(getToken()) == TokenType.LBRACKET;
    }

    private AccessNode setBase(final Expression base) {
        if (this.base == base) {
            return this;
        }
        return new AccessNode(this, base, property, isFunction(), type, programPoint);
    }

    @Override
    public AccessNode setType(final Type type) {
        if (this.type == type) {
            return this;
        }
        return new AccessNode(this, base, property, isFunction(), type, programPoint);
    }

    @Override
    public AccessNode setProgramPoint(final int programPoint) {
        if (this.programPoint == programPoint) {
            return this;
        }
        return new AccessNode(this, base, property, isFunction(), type, programPoint);
    }

    @Override
    public AccessNode setIsFunction() {
        if (isFunction()) {
            return this;
        }
        return new AccessNode(this, base, property, true, type, programPoint);
    }
}
