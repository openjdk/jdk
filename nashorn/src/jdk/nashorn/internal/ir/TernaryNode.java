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
 * TernaryNode nodes represent three operand operations (?:).
 */
@Immutable
public final class TernaryNode extends Node {
    private final Node lhs;

    private final Node rhs;

    /** Third argument. */
    private final Node third;

    /**
     * Constructor
     *
     * @param token  token
     * @param lhs    left hand side node
     * @param rhs    right hand side node
     * @param third  third node
     */
    public TernaryNode(final long token, final Node lhs, final Node rhs, final Node third) {
        super(token, third.getFinish());
        this.lhs = lhs;
        this.rhs = rhs;
        this.third = third;
    }

    private TernaryNode(final TernaryNode ternaryNode, final Node lhs, final Node rhs, final Node third) {
        super(ternaryNode);
        this.lhs = lhs;
        this.rhs = rhs;
        this.third = third;
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterTernaryNode(this)) {
            final Node newLhs = lhs().accept(visitor);
            final Node newRhs = rhs().accept(visitor);
            final Node newThird = third.accept(visitor);
            return visitor.leaveTernaryNode(setThird(newThird).setLHS(newLhs).setRHS(newRhs));
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
     * Get the lhs node for this ternary expression, i.e. "x" in x ? y : z
     * @return a node
     */
    public Node lhs() {
        return lhs;
    }

    /**
     * Get the rhs node for this ternary expression, i.e. "y" in x ? y : z
     * @return a node
     */
    public Node rhs() {
        return rhs;
    }

    /**
     * Get the "third" node for this ternary expression, i.e. "z" in x ? y : z
     * @return a node
     */
    public Node third() {
        return third;
    }

    /**
     * Set the left hand side expression for this node
     * @param lhs new left hand side expression
     * @return a node equivalent to this one except for the requested change.
     */
    public TernaryNode setLHS(final Node lhs) {
        if (this.lhs == lhs) {
            return this;
        }
        return new TernaryNode(this, lhs, rhs, third);
    }

    /**
     * Set the right hand side expression for this node
     * @param rhs new left hand side expression
     * @return a node equivalent to this one except for the requested change.
     */
    public TernaryNode setRHS(final Node rhs) {
        if (this.rhs == rhs) {
            return this;
        }
        return new TernaryNode(this, lhs, rhs, third);
    }

    /**
     * Reset the "third" node for this ternary expression, i.e. "z" in x ? y : z
     * @param third a node
     * @return a node equivalent to this one except for the requested change.
     */
    public TernaryNode setThird(final Node third) {
        if (this.third == third) {
            return this;
        }
        return new TernaryNode(this, lhs, rhs, third);
    }
}
