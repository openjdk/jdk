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
 * IR representation for executing bare expressions. Basically, an expression
 * node means "this code will be executed" and evaluating it results in
 * statements being added to the IR
 */
@Immutable
public final class ExecuteNode extends Statement {
    /** Expression to execute. */
    private final Node expression;

    /**
     * Constructor
     *
     * @param lineNumber line number
     * @param token      token
     * @param finish     finish
     * @param expression the expression to execute
     */
    public ExecuteNode(final int lineNumber, final long token, final int finish, final Node expression) {
        super(lineNumber, token, finish);
        this.expression = expression;
    }

    private ExecuteNode(final ExecuteNode executeNode, final Node expression) {
        super(executeNode);
        this.expression = expression;
    }

    @Override
    public boolean isTerminal() {
        return expression.isTerminal();
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterExecuteNode(this)) {
            return visitor.leaveExecuteNode(setExpression(expression.accept(visitor)));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        expression.toString(sb);
    }

    /**
     * Return the expression to be executed
     * @return the expression
     */
    public Node getExpression() {
        return expression;
    }

    /**
     * Reset the expression to be executed
     * @param expression the expression
     * @return new or same execute node
     */
    public ExecuteNode setExpression(final Node expression) {
        if (this.expression == expression) {
            return this;
        }
        return new ExecuteNode(this, expression);
    }
}
