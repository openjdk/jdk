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
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * A wrapper for an expression that is in a position to be a join predecessor.
 */
public class JoinPredecessorExpression extends Expression implements JoinPredecessor {
    private static final long serialVersionUID = 1L;

    private final Expression expression;
    private final LocalVariableConversion conversion;

    /**
     * A no-arg constructor does not wrap any expression on its own, but can be used as a place to contain a local
     * variable conversion in a place where an expression can otherwise stand.
     */
    public JoinPredecessorExpression() {
        this(null);
    }

    /**
     * A constructor for wrapping an expression and making it a join predecessor. Typically used on true and false
     * subexpressions of the ternary node as well as on the operands of short-circuiting logical expressions {@code &&}
     * and {@code ||}.
     * @param expression the expression to wrap
     */
    public JoinPredecessorExpression(final Expression expression) {
        this(expression, null);
    }

    private JoinPredecessorExpression(final Expression expression, final LocalVariableConversion conversion) {
        super(expression == null ? 0L : expression.getToken(), expression == null ? 0 : expression.getStart(), expression == null ? 0 : expression.getFinish());
        this.expression = expression;
        this.conversion = conversion;
    }

    @Override
    public JoinPredecessor setLocalVariableConversion(final LexicalContext lc, final LocalVariableConversion conversion) {
        if(conversion == this.conversion) {
            return this;
        }
        return new JoinPredecessorExpression(expression, conversion);
    }

    @Override
    public Type getType() {
        return expression.getType();
    }

    @Override
    public boolean isAlwaysFalse() {
        return expression != null && expression.isAlwaysFalse();
    }

    @Override
    public boolean isAlwaysTrue() {
        return expression != null && expression.isAlwaysTrue();
    }

    /**
     * Returns the underlying expression.
     * @return the underlying expression.
     */
    public Expression getExpression() {
        return expression;
    }

    /**
     * Sets the underlying expression.
     * @param expression the new underlying expression
     * @return this or modified join predecessor expression object.
     */
    public JoinPredecessorExpression setExpression(final Expression expression) {
        if(expression == this.expression) {
            return this;
        }
        return new JoinPredecessorExpression(expression, conversion);
    }

    @Override
    public LocalVariableConversion getLocalVariableConversion() {
        return conversion;
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if(visitor.enterJoinPredecessorExpression(this)) {
            final Expression expr = getExpression();
            return visitor.leaveJoinPredecessorExpression(expr == null ? this : setExpression((Expression)expr.accept(visitor)));
        }
        return this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        if(expression != null) {
            expression.toString(sb, printType);
        }
        if(conversion != null) {
            conversion.toString(sb);
        }
    }

}
