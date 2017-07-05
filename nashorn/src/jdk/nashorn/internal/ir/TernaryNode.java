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
public final class TernaryNode extends Expression {
    private final Expression test;

    private final Expression trueExpr;

    /** Third argument. */
    private final Expression falseExpr;

    /**
     * Constructor
     *
     * @param token     token
     * @param test      test expression
     * @param trueExpr  expression evaluated when test evaluates to true
     * @param falseExpr expression evaluated when test evaluates to true
     */
    public TernaryNode(final long token, final Expression test, final Expression trueExpr, final Expression falseExpr) {
        super(token, falseExpr.getFinish());
        this.test = test;
        this.trueExpr = trueExpr;
        this.falseExpr = falseExpr;
    }

    private TernaryNode(final TernaryNode ternaryNode, final Expression test, final Expression trueExpr, final Expression falseExpr) {
        super(ternaryNode);
        this.test = test;
        this.trueExpr = trueExpr;
        this.falseExpr = falseExpr;
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterTernaryNode(this)) {
            final Expression newTest = (Expression)getTest().accept(visitor);
            final Expression newTrueExpr = (Expression)getTrueExpression().accept(visitor);
            final Expression newFalseExpr = (Expression)falseExpr.accept(visitor);
            return visitor.leaveTernaryNode(setTest(newTest).setTrueExpression(newTrueExpr).setFalseExpression1(newFalseExpr));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        final boolean testParen  = tokenType().needsParens(getTest().tokenType(), true);
        final boolean trueParen  = tokenType().needsParens(getTrueExpression().tokenType(), false);
        final boolean falseParen = tokenType().needsParens(getFalseExpression().tokenType(), false);

        if (testParen) {
            sb.append('(');
        }
        getTest().toString(sb);
        if (testParen) {
            sb.append(')');
        }

        sb.append(" ? ");

        if (trueParen) {
            sb.append('(');
        }
        getTrueExpression().toString(sb);
        if (trueParen) {
            sb.append(')');
        }

        sb.append(" : ");

        if (falseParen) {
            sb.append('(');
        }
        getFalseExpression().toString(sb);
        if (falseParen) {
            sb.append(')');
        }
    }

    @Override
    public boolean isLocal() {
        return getTest().isLocal()
                && getTrueExpression().isLocal()
                && getFalseExpression().isLocal();
    }

    /**
     * Get the test expression for this ternary expression, i.e. "x" in x ? y : z
     * @return the test expression
     */
    public Expression getTest() {
        return test;
    }

    /**
     * Get the true expression for this ternary expression, i.e. "y" in x ? y : z
     * @return the true expression
     */
    public Expression getTrueExpression() {
        return trueExpr;
    }

    /**
     * Get the false expression for this ternary expression, i.e. "z" in x ? y : z
     * @return the false expression
     */
    public Expression getFalseExpression() {
        return falseExpr;
    }

    /**
     * Set the test expression for this node
     * @param test new test expression
     * @return a node equivalent to this one except for the requested change.
     */
    public TernaryNode setTest(final Expression test) {
        if (this.test == test) {
            return this;
        }
        return new TernaryNode(this, test, trueExpr, falseExpr);
    }

    /**
     * Set the true expression for this node
     * @param trueExpr new true expression
     * @return a node equivalent to this one except for the requested change.
     */
    public TernaryNode setTrueExpression(final Expression trueExpr) {
        if (this.trueExpr == trueExpr) {
            return this;
        }
        return new TernaryNode(this, test, trueExpr, falseExpr);
    }

    /**
     * Set the false expression for this node
     * @param falseExpr new false expression
     * @return a node equivalent to this one except for the requested change.
     */
    public TernaryNode setFalseExpression1(final Expression falseExpr) {
        if (this.falseExpr == falseExpr) {
            return this;
        }
        return new TernaryNode(this, test, trueExpr, falseExpr);
    }
}
