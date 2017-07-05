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

import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * IR representation of an indexed access (brackets operator.)
 */
@Immutable
public final class IndexNode extends BaseNode {
    /** Property index. */
    private final Expression index;

    /**
     * Constructors
     *
     * @param token   token
     * @param finish  finish
     * @param base    base node for access
     * @param index   index for access
     */
    public IndexNode(final long token, final int finish, final Expression base, final Expression index) {
        super(token, finish, base, false);
        this.index = index;
    }

    private IndexNode(final IndexNode indexNode, final Expression base, final Expression index, final boolean isFunction) {
        super(indexNode, base, isFunction);
        this.index = index;
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterIndexNode(this)) {
            return visitor.leaveIndexNode(
                setBase((Expression)base.accept(visitor)).
                setIndex((Expression)index.accept(visitor)));
        }
        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        final boolean needsParen = tokenType().needsParens(base.tokenType(), true);

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
    public Expression getIndex() {
        return index;
    }

    private IndexNode setBase(final Expression base) {
        if (this.base == base) {
            return this;
        }
        return new IndexNode(this, base, index, isFunction());
    }

    /**
     * Set the index expression for this node
     * @param index new index expression
     * @return a node equivalent to this one except for the requested change.
     */
    public IndexNode setIndex(Expression index) {
        if(this.index == index) {
            return this;
        }
        return new IndexNode(this, base, index, isFunction());
    }

    @Override
    public BaseNode setIsFunction() {
        if (isFunction()) {
            return this;
        }
        return new IndexNode(this, base, index, true);
    }

}
