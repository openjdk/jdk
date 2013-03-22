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
 * IR representation for executing bare expressions. Basically, an expression
 * node means "this code will be executed" and evaluating it results in
 * statements being added to the IR
 */
public class ExecuteNode extends Node {
    /** Expression to execute. */
    private Node expression;

    /**
     * Constructor
     *
     * @param source     the source
     * @param token      token
     * @param finish     finish
     * @param expression the expression to execute
     */
    public ExecuteNode(final Source source, final long token, final int finish, final Node expression) {
        super(source, token, finish);
        this.expression = expression;
    }

    /**
     * Constructor
     *
     * @param expression an expression to wrap, from which source, tokens and finish are also inherited
     */
    public ExecuteNode(final Node expression) {
        super(expression.getSource(), expression.getToken(), expression.getFinish());
        this.expression = expression;
    }

    private ExecuteNode(final ExecuteNode executeNode, final CopyState cs) {
        super(executeNode);
        this.expression = cs.existingOrCopy(executeNode.expression);
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new ExecuteNode(this, cs);
    }

    @Override
    public boolean equals(final Object other) {
        if (!super.equals(other)) {
            return false;
        }
        return expression.equals(((ExecuteNode)other).getExpression());
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ expression.hashCode();
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enterExecuteNode(this) != null) {
            setExpression(expression.accept(visitor));
            return visitor.leaveExecuteNode(this);
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
     */
    public void setExpression(final Node expression) {
        this.expression = expression;
    }
}
