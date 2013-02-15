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

import static jdk.nashorn.internal.parser.TokenType.RETURN;
import static jdk.nashorn.internal.parser.TokenType.YIELD;

import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation for RETURN or YIELD statements.
 *
 */
public class ReturnNode extends Node {
    /** Optional expression. */
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
     * @param expression expression to return
     * @param tryChain   surrounding try chain.
     */
    public ReturnNode(final Source source, final long token, final int finish, final Node expression, final TryNode tryChain) {
        super(source, token, finish);

        this.expression = expression;
        this.tryChain   = tryChain;

        setIsTerminal(true);
    }

    private ReturnNode(final ReturnNode returnNode, final CopyState cs) {
        super(returnNode);

        this.expression = cs.existingOrCopy(returnNode.expression);
        this.tryChain   = (TryNode)cs.existingOrSame(returnNode.tryChain);
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new ReturnNode(this, cs);
    }

    /**
     * Return true if is a RETURN node.
     * @return true if is RETURN node.
     */
    public boolean isReturn() {
        return isTokenType(RETURN);
    }

    /**
     * Check if this return node has an expression
     * @return true if not a void return
     */
    public boolean hasExpression() {
        return expression != null;
    }

    /**
     * Return true if is a YIELD node.
     * @return TRUE if is YIELD node.
     */
    public boolean isYield() {
        return isTokenType(YIELD);
    }

    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enter(this) != null) {
            if (expression != null) {
                expression = expression.accept(visitor);
            }

            return visitor.leave(this);
        }

        return this;
    }


    @Override
    public void toString(final StringBuilder sb) {
        sb.append(isYield() ? "yield" : "return ");

        if (expression != null) {
            expression.toString(sb);
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof ReturnNode) {
            final ReturnNode otherReturn = (ReturnNode)other;
            if (hasExpression() != otherReturn.hasExpression()) {
                return false;
            } else if (hasExpression()) {
                return otherReturn.getExpression().equals(getExpression());
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 0x4711_17 ^ (expression == null ? 0 : expression.hashCode());
    }

    /**
     * Get the expression this node returns
     * @return return expression, or null if void return
     */
    public Node getExpression() {
        return expression;
    }

    /**
     * Reset the expression this node returns
     * @param expression new expression, or null if void return
     */
    public void setExpression(final Node expression) {
        this.expression = expression;
    }

    /**
     * Get the surrounding try chain for this return node
     * @return try chain
     */
    public TryNode getTryChain() {
        return tryChain;
    }
}
