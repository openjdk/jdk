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

import static jdk.nashorn.internal.runtime.UnwarrantedOptimismException.INVALID_PROGRAM_POINT;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.TokenType;

/**
 * BinaryNode nodes represent two operand operations.
 */
@Immutable
public final class BinaryNode extends Expression implements Assignment<Expression>, Optimistic {
    /** Left hand side argument. */
    private final Expression lhs;

    private final Expression rhs;

    private final int programPoint;

    private final boolean isOptimistic;

    private final Type type;

    @Ignore
    private static final List<TokenType> CAN_OVERFLOW =
        Collections.unmodifiableList(
            Arrays.asList(new TokenType[] {
                TokenType.ADD,
                TokenType.DIV,
                TokenType.MOD,
                TokenType.MUL,
                TokenType.SUB,
                TokenType.ASSIGN_ADD,
                TokenType.ASSIGN_DIV,
                TokenType.ASSIGN_MOD,
                TokenType.ASSIGN_MUL,
                TokenType.ASSIGN_SUB
            }));

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
        this.programPoint = INVALID_PROGRAM_POINT;
        this.isOptimistic = false;
        this.type = null;
    }

    private BinaryNode(final BinaryNode binaryNode, final Expression lhs, final Expression rhs, final Type type, final int programPoint, final boolean isOptimistic) {
        super(binaryNode);
        this.lhs = lhs;
        this.rhs = rhs;
        this.programPoint = programPoint;
        this.isOptimistic = isOptimistic;
        this.type = type;
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
        case BIT_AND:
        case BIT_OR:
        case BIT_XOR:
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
            if (isComparison()) {
                return Type.BOOLEAN;
            }
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
    public boolean isLocal() {
        switch (tokenType()) {
        case SAR:
        case SHL:
        case SHR:
        case BIT_AND:
        case BIT_OR:
        case BIT_XOR:
        case ADD:
        case DIV:
        case MOD:
        case MUL:
        case SUB:
            return lhs.isLocal() && lhs.getType().isJSPrimitive()
                && rhs.isLocal() && rhs.getType().isJSPrimitive();
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
            return lhs instanceof IdentNode && lhs.isLocal() && lhs.getType().isJSPrimitive()
                    && rhs.isLocal() && rhs.getType().isJSPrimitive();
        case ASSIGN:
            return lhs instanceof IdentNode && lhs.isLocal() && rhs.isLocal();
        default:
            return false;
        }
    }

    @Override
    public void toString(final StringBuilder sb) {
        final TokenType tokenType = tokenType();

        final boolean lhsParen = tokenType.needsParens(lhs().tokenType(), true);
        final boolean rhsParen = tokenType.needsParens(rhs().tokenType(), false);

        if (lhsParen) {
            sb.append('(');
        }

        lhs().toString(sb);

        if (lhsParen) {
            sb.append(')');
        }

        sb.append(' ');

        switch (tokenType) {
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
            sb.append(tokenType.getName());
            break;
        }

        if (isOptimistic()) {
            sb.append(Node.OPT_IDENTIFIER);
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
        return new BinaryNode(this, lhs, rhs, type, programPoint, isOptimistic);
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
        return new BinaryNode(this, lhs, rhs, type, programPoint, isOptimistic);
    }

    @Override
    public int getProgramPoint() {
        return programPoint;
    }

    @Override
    public boolean canBeOptimistic() {
        return getMostOptimisticType() != getMostPessimisticType();
    }

    @Override
    public BinaryNode setProgramPoint(final int programPoint) {
        if (this.programPoint == programPoint) {
            return this;
        }
        return new BinaryNode(this, lhs, rhs, type, programPoint, isOptimistic);
    }

    @Override
    public BinaryNode setIsOptimistic(final boolean isOptimistic) {
        if (this.isOptimistic == isOptimistic) {
            return this;
        }
        assert isOptimistic;
        return new BinaryNode(this, lhs, rhs, type, programPoint, isOptimistic);
    }

    @Override
    public Type getMostOptimisticType() {
        if (CAN_OVERFLOW.contains(tokenType())) {
            return Type.widest(Type.INT, Type.widest(lhs.getType(), rhs.getType()));
        }
        return getMostPessimisticType();
    }

    @Override
    public Type getMostPessimisticType() {
        return getWidestOperationType();
    }

    @Override
    public boolean isOptimistic() {
        return isOptimistic;
    }

    @Override
    public Type getType() {
        return type == null ? super.getType() : type;
    }

    @Override
    public BinaryNode setType(TemporarySymbols ts, Type type) {
        if (this.type == type) {
            return this;
        }
        return new BinaryNode(this, lhs, rhs, type, programPoint, isOptimistic);
    }
}
