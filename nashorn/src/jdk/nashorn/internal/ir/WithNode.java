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
 * IR representation for {@code with} statements.
 */
public class WithNode extends Node {
   /** This expression. */
    private Node expression;

    /** Statements. */
    private Block body;

    /**
     * Constructor
     *
     * @param source     the source
     * @param token      token
     * @param finish     finish
     * @param expression expression in parenthesis
     * @param body       with node body
     */
    public WithNode(final Source source, final long token, final int finish, final Node expression, final Block body) {
        super(source, token, finish);

        this.expression = expression;
        this.body       = body;
    }

    private WithNode(final WithNode withNode, final CopyState cs) {
        super(withNode);

        this.expression = cs.existingOrCopy(withNode.expression);
        this.body       = (Block)cs.existingOrCopy(withNode.body);
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new WithNode(this, cs);
    }

    /**
     * Assist in IR navigation.
     *
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enterWithNode(this) != null) {
            expression = expression.accept(visitor);
            body = (Block)body.accept(visitor);
            return visitor.leaveWithNode(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append("with (");
        expression.toString(sb);
        sb.append(')');
    }

    /**
     * Get the body of this WithNode
     * @return the body
     */
    public Block getBody() {
        return body;
    }

    /**
     * Reset the body of this with node
     * @param body new body
     */
    public void setBody(final Block body) {
        this.body = body;
    }

    /**
     * Get the expression of this WithNode
     * @return the expression
     */
    public Node getExpression() {
        return expression;
    }

    /**
     * Reset the expression of this with node
     * @param expression new expression
     */
    public void setExpression(final Node expression) {
        this.expression = expression;
    }
}

