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

import static jdk.nashorn.internal.parser.TokenType.BIT_NOT;
import static jdk.nashorn.internal.parser.TokenType.CONVERT;
import static jdk.nashorn.internal.parser.TokenType.DECPOSTFIX;
import static jdk.nashorn.internal.parser.TokenType.INCPOSTFIX;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;

/**
 * UnaryNode nodes represent single operand operations.
 */
@Immutable
public final class UnaryNode extends Node implements Assignment<Node> {
    /** Right hand side argument. */
    private final Node rhs;

    /**
     * Constructor
     *
     * @param token  token
     * @param rhs    expression
     */
    public UnaryNode(final long token, final Node rhs) {
        this(token, Math.min(rhs.getStart(), Token.descPosition(token)), Math.max(Token.descPosition(token) + Token.descLength(token), rhs.getFinish()), rhs);
    }

    /**
     * Constructor
     *
     * @param token  token
     * @param start  start
     * @param finish finish
     * @param rhs    expression
     */
    public UnaryNode(final long token, final int start, final int finish, final Node rhs) {
        super(token, start, finish);
        this.rhs = rhs;
    }


    private UnaryNode(final UnaryNode unaryNode, final Node rhs) {
        super(unaryNode);
        this.rhs = rhs;
    }

    /**
     * Is this an assignment - i.e. that mutates something such as a++
     *
     * @return true if assignment
     */
    @Override
    public boolean isAssignment() {
        switch (tokenType()) {
        case DECPOSTFIX:
        case DECPREFIX:
        case INCPOSTFIX:
        case INCPREFIX:
            return true;
        default:
            return false;
        }
    }

    @Override
    public boolean isSelfModifying() {
        return isAssignment();
    }

    @Override
    public Type getWidestOperationType() {
        return isAssignment() ? Type.NUMBER : Type.OBJECT;
    }

    @Override
    public Node getAssignmentDest() {
        return isAssignment() ? rhs() : null;
    }

    @Override
    public Node setAssignmentDest(Node n) {
        return setRHS(n);
    }

    @Override
    public Node getAssignmentSource() {
        return getAssignmentDest();
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterUnaryNode(this)) {
            return visitor.leaveUnaryNode(setRHS(rhs.accept(visitor)));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        final TokenType type      = tokenType();
        final String    name      = type.getName();
        final boolean   isPostfix = type == DECPOSTFIX || type == INCPOSTFIX;
        final boolean   isConvert = type == CONVERT && getSymbol() != null;

        boolean rhsParen   = type.needsParens(rhs().tokenType(), false);
        int     convertPos = 0;

        if (isConvert) {
            convertPos = sb.length();
            sb.append("(");
            sb.append(getType());
            sb.append(")(");
        }

        if (!isPostfix && !isConvert) {
            if (name == null) {
                sb.append(type.name());
                rhsParen = true;
            } else {
                sb.append(name);

                if (type.ordinal() > BIT_NOT.ordinal()) {
                    sb.append(' ');
                }
            }
        }

        if (rhsParen) {
            sb.append('(');
        }
        rhs().toString(sb);
        if (rhsParen) {
            sb.append(')');
        }

        if (isPostfix) {
            sb.append(type == DECPOSTFIX ? "--" : "++");
        }

        if (isConvert) {
            // strip extra cast parenthesis which makes the printout harder to read
            final boolean endsWithParenthesis = sb.charAt(sb.length() - 1) == ')';
            if (!endsWithParenthesis) {
                sb.append(')');
            } else {
                sb.setCharAt(convertPos, ' ');
            }
        }
    }

    /**
     * Get the right hand side of this if it is inherited by a binary expression,
     * or just the expression itself if still Unary
     *
     * @see BinaryNode
     *
     * @return right hand side or expression node
     */
    public Node rhs() {
        return rhs;
    }

    /**
     * Reset the right hand side of this if it is inherited by a binary expression,
     * or just the expression itself if still Unary
     *
     * @see BinaryNode
     *
     * @param rhs right hand side or expression node
     * @return a node equivalent to this one except for the requested change.
     */
    public UnaryNode setRHS(final Node rhs) {
        if (this.rhs == rhs) {
            return this;
        }
        return new UnaryNode(this, rhs);
    }
}
