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
 * IR representation for {@code with} statements.
 */
@Immutable
public final class WithNode extends LexicalContextStatement {
   /** This expression. */
    private final Expression expression;

    /** Statements. */
    private final Block body;

    /**
     * Constructor
     *
     * @param lineNumber line number
     * @param token      token
     * @param finish     finish
     */
    public WithNode(final int lineNumber, final long token, final int finish) {
        super(lineNumber, token, finish);
        this.expression = null;
        this.body       = null;
    }

    private WithNode(final WithNode node, final Expression expression, final Block body) {
        super(node);
        this.expression = expression;
        this.body       = body;
    }

    /**
     * Assist in IR navigation.
     *
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final LexicalContext lc, final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterWithNode(this)) {
             return visitor.leaveWithNode(
                setExpression(lc, (Expression)expression.accept(visitor)).
                setBody(lc, (Block)body.accept(visitor)));
        }
        return this;
    }

    @Override
    public boolean isTerminal() {
        return body.isTerminal();
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
     * @param lc lexical context
     * @param body new body
     * @return new or same withnode
     */
    public WithNode setBody(final LexicalContext lc, final Block body) {
        if (this.body == body) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new WithNode(this, expression, body));
    }

    /**
     * Get the expression of this WithNode
     * @return the expression
     */
    public Expression getExpression() {
        return expression;
    }

    /**
     * Reset the expression of this with node
     * @param lc lexical context
     * @param expression new expression
     * @return new or same withnode
     */
    public WithNode setExpression(final LexicalContext lc, final Expression expression) {
        if (this.expression == expression) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new WithNode(this, expression, body));
    }
}

