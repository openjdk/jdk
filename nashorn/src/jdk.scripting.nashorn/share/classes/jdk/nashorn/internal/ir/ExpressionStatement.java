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
import jdk.nashorn.internal.parser.TokenType;

/**
 * IR representation for executing bare expressions. Basically, an expression
 * node means "this code will be executed" and evaluating it results in
 * statements being added to the IR
 */
@Immutable
public final class ExpressionStatement extends Statement {
    private static final long serialVersionUID = 1L;

    /** Expression to execute. */
    private final Expression expression;
    private final TokenType destructuringDecl;

    /**
     * Constructor
     *
     * @param lineNumber line number
     * @param token      token
     * @param finish     finish
     * @param expression the expression to execute
     * @param destructuringDecl does this statement represent a destructuring declaration?
     */
    public ExpressionStatement(final int lineNumber, final long token, final int finish,
            final Expression expression, final TokenType destructuringDecl) {
        super(lineNumber, token, finish);
        this.expression = expression;
        this.destructuringDecl = destructuringDecl;
    }

    /**
     * Constructor
     *
     * @param lineNumber line number
     * @param token      token
     * @param finish     finish
     * @param expression the expression to execute
     */
    public ExpressionStatement(final int lineNumber, final long token, final int finish, final Expression expression) {
        this(lineNumber, token, finish, expression, null);
    }

    private ExpressionStatement(final ExpressionStatement expressionStatement, final Expression expression) {
        super(expressionStatement);
        this.expression = expression;
        this.destructuringDecl = null;
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterExpressionStatement(this)) {
            return visitor.leaveExpressionStatement(setExpression((Expression)expression.accept(visitor)));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printTypes) {
        expression.toString(sb, printTypes);
    }

    /**
     * Return the expression to be executed
     * @return the expression
     */
    public Expression getExpression() {
        return expression;
    }

    /**
     * Return declaration type if this expression statement is a destructuring declaration
     *
     * @return declaration type (LET, VAR, CONST) if destructuring declaration, null otherwise.
     */
    public TokenType destructuringDeclarationType() {
        return destructuringDecl;
    }

    /**
     * Reset the expression to be executed
     * @param expression the expression
     * @return new or same execute node
     */
    public ExpressionStatement setExpression(final Expression expression) {
        if (this.expression == expression) {
            return this;
        }
        return new ExpressionStatement(this, expression);
    }
}
