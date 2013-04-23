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

import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * TernaryNode nodes represent three operand operations (?:).
 */
public class TernaryNode extends BinaryNode {
    /** Third argument. */
    private Node third;

    /**
     * Constructor
     *
     * @param source the source
     * @param token  token
     * @param lhs    left hand side node
     * @param rhs    right hand side node
     * @param third  third node
     */
    public TernaryNode(final Source source, final long token, final Node lhs, final Node rhs, final Node third) {
        super(source, token, lhs, rhs);

        this.finish = third.getFinish();
        this.third = third;
    }

    private TernaryNode(final TernaryNode ternaryNode, final CopyState cs) {
        super(ternaryNode, cs);

        this.third = cs.existingOrCopy(ternaryNode.third);
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new TernaryNode(this, cs);
    }

    @Override
    public boolean equals(final Object other) {
        if (!super.equals(other)) {
            return false;
        }
        return third.equals(((TernaryNode)other).third());
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ third().hashCode();
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enterTernaryNode(this) != null) {
            final Node newLhs = lhs().accept(visitor);
            final Node newRhs = rhs().accept(visitor);
            final Node newThird = third.accept(visitor);
            return visitor.leaveTernaryNode((TernaryNode)setThird(newThird).setLHS(newLhs).setRHS(newRhs));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        final boolean lhsParen   = tokenType().needsParens(lhs().tokenType(), true);
        final boolean rhsParen   = tokenType().needsParens(rhs().tokenType(), false);
        final boolean thirdParen = tokenType().needsParens(third().tokenType(), false);

        if (lhsParen) {
            sb.append('(');
        }
        lhs().toString(sb);
        if (lhsParen) {
            sb.append(')');
        }

        sb.append(" ? ");

        if (rhsParen) {
            sb.append('(');
        }
        rhs().toString(sb);
        if (rhsParen) {
            sb.append(')');
        }

        sb.append(" : ");

        if (thirdParen) {
            sb.append('(');
        }
        third().toString(sb);
        if (thirdParen) {
            sb.append(')');
        }
    }

    /**
     * Get the "third" node for this ternary expression, i.e. "z" in x ? y : z
     * @return a node
     */
    public Node third() {
        return third;
    }

    /**
     * Reset the "third" node for this ternary expression, i.e. "z" in x ? y : z
     * @param third a node
     * @return a node equivalent to this one except for the requested change.
     */
    public TernaryNode setThird(final Node third) {
        if(this.third == third) return this;
        final TernaryNode n = (TernaryNode)clone();
        n.third = third;
        return n;
    }
}
