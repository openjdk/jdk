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
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.TokenType;

/**
 * BinaryNode nodes represent two operand operations.
 */
@Immutable
public final class BinaryNode extends Expression implements Assignment<Expression> {
    /** Left hand side argument. */
    private final Expression lhs;

    private final Expression rhs;

    /**
     * Constructor
     *
     * @param token  token
     * @param lhs    left hand side
     * @param rhs    right hand side
     */
    public BinaryNode(final long token, final Expression lhs, final Expression rhs) {
        super(token, lhs.getStart(), rhs.getFinish());
        this.lhs   = lhs;
        this.rhs   = rhs;
    }

    private BinaryNode(final BinaryNode binaryNode, final Expression lhs, final Expression rhs) {
        super(binaryNode);
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public boolean isComparison() {
        switch (tokenType()) {
        case EQ:
        case EQ_STRICT:
        case NE:
        case NE_STRICT:
        case LE:
        case LT:
        case GE:
        case GT:
            return true;
        default:
            return false;
        }
    }

    /**
     * Return the widest possible type for this operation. This is used for compile time
     * static type inference
     *
     * @return Type
     */
    @Override
    public Type getWidestOperationType() {
        switch (tokenType()) {
        case SHR:
        case ASSIGN_SHR:
            return Type.LONG;
        case ASSIGN_SAR:
        case ASSIGN_SHL:
        case ASSIGN_BIT_AND:
        case ASSIGN_BIT_OR:
        case ASSIGN_BIT_XOR:
        case SAR:
        case SHL:
            return Type.INT;
        case DIV:
        case MOD:
        case MUL:
        case SUB:
        case ASSIGN_DIV:
        case ASSIGN_MOD:
        case ASSIGN_MUL:
        case ASSIGN_SUB:
            return Type.NUMBER;
        default:
            return Type.OBJECT;
        }
    }

    /**
     * Check if this node is an assigment
     *
     * @return true if this node assigns a value
     */
    @Override
    public boolean isAssignment() {
        switch (tokenType()) {
        case ASSIGN:
        case ASSIGN_ADD:
        case ASSIGN_BIT_AND:
        case ASSIGN_BIT_OR:
        case ASSIGN_BIT_XOR:
        case ASSIGN_DIV:
        case ASSIGN_MOD:
        case ASSIGN_MUL:
        case ASSIGN_SAR:
        case ASSIGN_SHL:
        case ASSIGN_SHR:
        case ASSIGN_SUB:
           return true;
        default:
           return false;
        }
    }

    @Override
    public boolean isSelfModifying() {
        return isAssignment() && tokenType() != TokenType.ASSIGN;
    }

    @Override
    public Expression getAssignmentDest() {
        return isAssignment() ? lhs() : null;
    }

    @Override
    public BinaryNode setAssignmentDest(Expression n) {
        return setLHS(n);
    }

    @Override
    public Expression getAssignmentSource() {
        return rhs();
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterBinaryNode(this)) {
            return visitor.leaveBinaryNode(setLHS((Expression)lhs.accept(visitor)).setRHS((Expression)rhs.accept(visitor)));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb) {
        final TokenType type = tokenType();

        final boolean lhsParen = type.needsParens(lhs().tokenType(), true);
        final boolean rhsParen = type.needsParens(rhs().tokenType(), false);

        if (lhsParen) {
            sb.append('(');
        }

        lhs().toString(sb);

        if (lhsParen) {
            sb.append(')');
        }

        sb.append(' ');

        switch (type) {
        case COMMALEFT:
            sb.append(",<");
            break;
        case COMMARIGHT:
            sb.append(",>");
            break;
        case INCPREFIX:
        case DECPREFIX:
            sb.append("++");
            break;
        default:
            sb.append(type.getName());
            break;
        }

        sb.append(' ');

        if (rhsParen) {
            sb.append('(');
        }
        rhs().toString(sb);
        if (rhsParen) {
            sb.append(')');
        }
    }

    /**
     * Get the left hand side expression for this node
     * @return the left hand side expression
     */
    public Expression lhs() {
        return lhs;
    }

    /**
     * Get the right hand side expression for this node
     * @return the left hand side expression
     */
    public Expression rhs() {
        return rhs;
    }

    /**
     * Set the left hand side expression for this node
     * @param lhs new left hand side expression
     * @return a node equivalent to this one except for the requested change.
     */
    public BinaryNode setLHS(final Expression lhs) {
        if (this.lhs == lhs) {
            return this;
        }
        return new BinaryNode(this, lhs, rhs);
    }

    /**
     * Set the right hand side expression for this node
     * @param rhs new left hand side expression
     * @return a node equivalent to this one except for the requested change.
     */
    public BinaryNode setRHS(final Expression rhs) {
        if (this.rhs == rhs) {
            return this;
        }
        return new BinaryNode(this, lhs, rhs);
    }

}
