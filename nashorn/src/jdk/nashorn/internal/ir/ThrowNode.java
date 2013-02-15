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

import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation for THROW statements.
 */
public class ThrowNode extends Node {
    /** Exception expression. */
    private Node expression;

    /** Try chain. */
    @Ignore
    private final TryNode tryChain;

    /**
     * Constructor
     *
     * @param source     the source
     * @param token      token
     * @param finish     finish
     * @param expression expression to throw
     * @param tryChain   surrounding try chain
     */
    public ThrowNode(final Source source, final long token, final int finish, final Node expression, final TryNode tryChain) {
        super(source, token, finish);

        this.expression = expression;
        this.tryChain = tryChain;
        setIsTerminal(true);
    }

    private ThrowNode(final ThrowNode throwNode, final CopyState cs) {
        super(throwNode);

        this.expression = cs.existingOrCopy(throwNode.expression);
        this.tryChain = (TryNode)cs.existingOrSame(throwNode.tryChain);
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new ThrowNode(this, cs);
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enter(this) != null) {
            setExpression(expression.accept(visitor));
            return visitor.leave(this);
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        sb.append("throw ");

        if (expression != null) {
            expression.toString(sb);
        }
    }

    /**
     * Get the expression that is being thrown by this node
     * @return expression
     */
    public Node getExpression() {
        return expression;
    }

    /**
     * Reset the expression being thrown by this node
     * @param expression new expression
     */
    public void setExpression(final Node expression) {
        this.expression = expression;
    }

    /**
     * Get surrounding tryChain for this node
     * @return try chain
     */
    public TryNode getTryChain() {
        return tryChain;
    }
}
