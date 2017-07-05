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

/**
 * IR representation of a property access (period operator.)
 */
@Immutable
public final class AccessNode extends BaseNode {
    /** Property ident. */
    private final IdentNode property;

    /**
     * Constructor
     *
     * @param token     token
     * @param finish    finish
     * @param base      base node
     * @param property  property
     */
    public AccessNode(final long token, final int finish, final Node base, final IdentNode property) {
        super(token, finish, base, false, false);
        this.property = property.setIsPropertyName();
    }

    private AccessNode(final AccessNode accessNode, final Node base, final IdentNode property, final boolean isFunction, final boolean hasCallSiteType) {
        super(accessNode, base, isFunction, hasCallSiteType);
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
                setBase(base.accept(visitor)).
                setProperty((IdentNode)property.accept(visitor)));
        }
        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        final boolean needsParen = tokenType().needsParens(getBase().tokenType(), true);

        if (hasCallSiteType()) {
            sb.append('{');
            final String desc = getType().getDescriptor();
            sb.append(desc.charAt(desc.length() - 1) == ';' ? "O" : getType().getDescriptor());
            sb.append('}');
        }

        if (needsParen) {
            sb.append('(');
        }

        base.toString(sb);

        if (needsParen) {
            sb.append(')');
        }
        sb.append('.');

        sb.append(property.getName());
    }

    /**
     * Get the property
     *
     * @return the property IdentNode
     */
    public IdentNode getProperty() {
        return property;
    }

    private AccessNode setBase(final Node base) {
        if (this.base == base) {
            return this;
        }
        return new AccessNode(this, base, property, isFunction(), hasCallSiteType());
    }

    private AccessNode setProperty(final IdentNode property) {
        if (this.property == property) {
            return this;
        }
        return new AccessNode(this, base, property, isFunction(), hasCallSiteType());
    }

    @Override
    public AccessNode setType(final TemporarySymbols ts, final LexicalContext lc, final Type type) {
        logTypeChange(type);
        final AccessNode newAccessNode = (AccessNode)setSymbol(lc, getSymbol().setTypeOverrideShared(type, ts));
        return new AccessNode(newAccessNode, base, property.setType(ts, lc, type), isFunction(), hasCallSiteType());
    }

    @Override
    public BaseNode setIsFunction() {
        if (isFunction()) {
            return this;
        }
        return new AccessNode(this, base, property, true, hasCallSiteType());
    }

}
