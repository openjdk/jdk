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
 * IR representation of an indexed access (brackets operator.)
 *
 */
public class IndexNode extends BaseNode implements TypeOverride<IndexNode> {
    /** Property ident. */
    private Node index;

    private boolean hasCallSiteType;

    /**
     * Constructors
     *
     * @param source  the source
     * @param token   token
     * @param finish  finish
     * @param base    base node for access
     * @param index   index for access
     */
    public IndexNode(final Source source, final long token, final int finish, final Node base, final Node index) {
        super(source, token, finish, base);

        this.index = index;
    }

    /**
     * Copy constructor
     *
     * @param indexNode source node
     */
    public IndexNode(final IndexNode indexNode) {
        this(indexNode, new CopyState());
    }

    private IndexNode(final IndexNode indexNode, final CopyState cs) {
        super(indexNode, cs);

        index = cs.existingOrCopy(indexNode.index);
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new IndexNode(this, cs);
    }

    @Override
    public boolean equals(final Object other) {
        if (!super.equals(other)) {
            return false;
        }
        return index.equals(((IndexNode)other).getIndex());
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ getIndex().hashCode();
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enterIndexNode(this) != null) {
            base = base.accept(visitor);
            index = index.accept(visitor);
            return visitor.leaveIndexNode(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        final boolean needsParen = tokenType().needsParens(base.tokenType(), true);

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

        sb.append('[');
        index.toString(sb);
        sb.append(']');
    }

    /**
     * Get the index expression for this IndexNode
     * @return the index
     */
    public Node getIndex() {
        return index;
    }

    /**
     * Reset the index expression for this IndexNode
     * @param index a new index expression
     */
    public void setIndex(final Node index) {
        this.index = index;
    }

    @Override
    public IndexNode setType(final Type type) {
        if (DEBUG_FIELDS && !Type.areEquivalent(getSymbol().getSymbolType(), type)) {
            ObjectClassGenerator.LOG.info(getClass().getName() + " " + this + " => " + type + " instead of " + getType());
        }
        hasCallSiteType = true;
        getSymbol().setTypeOverride(type);
        return this;
    }

    @Override
    public boolean canHaveCallSiteType() {
        return true; //carried by the symbol and always the same nodetype==symboltype
    }

}
