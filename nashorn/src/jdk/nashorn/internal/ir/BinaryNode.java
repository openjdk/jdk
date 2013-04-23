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
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.Source;

/**
 * BinaryNode nodes represent two operand operations.
 */
public class BinaryNode extends UnaryNode {
    /** Left hand side argument. */
    private Node lhs;

    /**
     * Constructor
     *
     * @param source source code
     * @param token  token
     * @param lhs    left hand side
     * @param rhs    right hand side
     */
    public BinaryNode(final Source source, final long token, final Node lhs, final Node rhs) {
        super(source, token, rhs);

        start  = lhs.getStart();
        finish = rhs.getFinish();

        this.lhs = lhs;
    }

    /**
     * Copy constructor
     *
     * @param binaryNode the binary node
     * @param cs         copy state
     */
    protected BinaryNode(final BinaryNode binaryNode, final CopyState cs) {
        super(binaryNode, cs);
        lhs = cs.existingOrCopy(binaryNode.lhs);
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new BinaryNode(this, cs);
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
    public Node getAssignmentDest() {
        return isAssignment() ? lhs() : null;
    }

    @Override
    public Node setAssignmentDest(Node n) {
        return setLHS(n);
    }

    @Override
    public Node getAssignmentSource() {
        return rhs();
    }

    @Override
    public boolean equals(final Object other) {
        if (!super.equals(other)) {
            return false;
        }
        return lhs.equals(((BinaryNode)other).lhs());
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ lhs().hashCode();
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final NodeVisitor visitor) {
        if (visitor.enterBinaryNode(this) != null) {
            // TODO: good cause for a separate visitMembers: we could delegate to UnaryNode.visitMembers
            return visitor.leaveBinaryNode((BinaryNode)setLHS(lhs.accept(visitor)).setRHS(rhs().accept(visitor)));
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
    public Node lhs() {
        return lhs;
    }

    /**
     * Set the left hand side expression for this node
     * @param lhs new left hand side expression
     * @return a node equivalent to this one except for the requested change.
     */
    public BinaryNode setLHS(final Node lhs) {
        if(this.lhs == lhs) return this;
        final BinaryNode n = (BinaryNode)clone();
        n.lhs = lhs;
        return n;
    }
}
