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
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * IR representation for RETURN or YIELD statements.
 */
@Immutable
public class ReturnNode extends Statement {
    /** Optional expression. */
    private final Node expression;

    /**
     * Constructor
     *
     * @param lineNumber line number
     * @param token      token
     * @param finish     finish
     * @param expression expression to return
     */
    public ReturnNode(final int lineNumber, final long token, final int finish, final Node expression) {
        super(lineNumber, token, finish);
        this.expression = expression;
    }

    private ReturnNode(final ReturnNode returnNode, final Node expression) {
        super(returnNode);
        this.expression = expression;
    }

    @Override
    public boolean isTerminal() {
        return true;
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
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterReturnNode(this)) {
            if (expression != null) {
                return visitor.leaveReturnNode(setExpression(expression.accept(visitor)));
            }
            return visitor.leaveReturnNode(this);
        }

        return this;
    }


    @Override
    public void toString(final StringBuilder sb) {
        sb.append(isYield() ? "yield" : "return");
        if (expression != null) {
            sb.append(' ');
            expression.toString(sb);
        }
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
     * @return new or same return node
     */
    public ReturnNode setExpression(final Node expression) {
        if (this.expression == expression) {
            return this;
        }
        return new ReturnNode(this, expression);
    }

}
