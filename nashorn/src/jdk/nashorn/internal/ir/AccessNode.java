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

import static jdk.nashorn.internal.codegen.ObjectClassGenerator.DEBUG_FIELDS;

import jdk.nashorn.internal.codegen.ObjectClassGenerator;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation of a property access (period operator.)
 *
 */
public class AccessNode extends BaseNode implements TypeOverride<AccessNode> {
    /** Property ident. */
    private IdentNode property;

    /** Does this node have a type override */
    private boolean hasCallSiteType;

    /**
     * Constructor
     *
     * @param source    source code
     * @param token     token
     * @param finish    finish
     * @param base      base node
     * @param property  property
     */
    public AccessNode(final Source source, final long token, final int finish, final Node base, final IdentNode property) {
        super(source, token, finish, base);

        this.start    = base.getStart();
        this.property = property.setIsPropertyName();
    }

    /**
     * Copy constructor
     *
     * @param accessNode  source node
     */
    public AccessNode(final AccessNode accessNode) {
        this(accessNode, new CopyState());
    }

    /**
     * Internal copy constructor
     *
     * @param accessNode  source node
     * @param cs          copy state
     */
    protected AccessNode(final AccessNode accessNode, final CopyState cs) {
        super(accessNode, cs);
        this.property = (IdentNode)cs.existingOrCopy(accessNode.getProperty());
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new AccessNode(this, cs);
    }

    @Override
    public boolean equals(final Object other) {
        if (!super.equals(other)) {
            return false;
        }
        final AccessNode accessNode = (AccessNode)other;
        return property.equals(accessNode.getProperty());
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ property.hashCode();
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enterAccessNode(this) != null) {
            base = base.accept(visitor);
            property = (IdentNode)property.accept(visitor);
            return visitor.leaveAccessNode(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        final boolean needsParen = tokenType().needsParens(getBase().tokenType(), true);

        if (hasCallSiteType) {
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

    @Override
    public AccessNode setType(final Type type) {
        if (DEBUG_FIELDS && !Type.areEquivalent(getSymbol().getSymbolType(), type)) {
            ObjectClassGenerator.LOG.info(getClass().getName() + " " + this + " => " + type + " instead of " + getType());
        }
        property = property.setType(type);
        getSymbol().setTypeOverride(type); //always a temp so this is fine.
        hasCallSiteType = true;
        return this;
    }

    @Override
    public boolean canHaveCallSiteType() {
        return true; //carried by the symbol and always the same nodetype==symboltype
    }
}
