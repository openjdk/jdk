/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.api.tree;

import jdk.nashorn.internal.ir.Node;

import jdk.nashorn.internal.parser.TokenType;

abstract class TreeImpl implements Tree {
    protected final Node node;

    TreeImpl(final Node node) {
        this.node = node;
    }

    @Override
    public long getStartPosition() {
        return node.getStart();
    }

    @Override
    public long getEndPosition() {
        return node.getFinish();
    }

    @Override
    public <R,D> R accept(final TreeVisitor<R,D> visitor, final D data) {
        return visitor.visitUnknown(this, data);
    }

    static Kind getOperator(final TokenType tt) {
        switch(tt) {
            case NEW:
                return Kind.NEW;
            case NOT:
                return Kind.LOGICAL_COMPLEMENT;
            case NE:
                return Kind.NOT_EQUAL_TO;
            case NE_STRICT:
                return Kind.STRICT_NOT_EQUAL_TO;
            case MOD:
                return Kind.REMAINDER;
            case ASSIGN_MOD:
                return Kind.REMAINDER_ASSIGNMENT;
            case BIT_AND:
                return Kind.AND;
            case AND:
                return Kind.CONDITIONAL_AND;
            case ASSIGN_BIT_AND:
                return Kind.AND_ASSIGNMENT;
            case MUL:
                return Kind.MULTIPLY;
            case ASSIGN_MUL:
                return Kind.MULTIPLY_ASSIGNMENT;
            case ADD:
                return Kind.PLUS;
            case INCPREFIX:
                return Kind.PREFIX_INCREMENT;
            case INCPOSTFIX:
                return Kind.POSTFIX_INCREMENT;
            case ASSIGN_ADD:
                return Kind.PLUS_ASSIGNMENT;
            case SUB:
                return Kind.MINUS;
            case DECPREFIX:
                return Kind.PREFIX_DECREMENT;
            case DECPOSTFIX:
                return Kind.POSTFIX_DECREMENT;
            case ASSIGN_SUB:
                return Kind.MINUS_ASSIGNMENT;
            case DIV:
                return Kind.DIVIDE;
            case ASSIGN_DIV:
                return Kind.DIVIDE_ASSIGNMENT;
            case LT:
                return Kind.LESS_THAN;
            case SHL:
                return Kind.LEFT_SHIFT;
            case ASSIGN_SHL:
                return Kind.LEFT_SHIFT_ASSIGNMENT;
            case LE:
                return Kind.LESS_THAN_EQUAL;
            case ASSIGN:
                return Kind.ASSIGNMENT;
            case EQ:
                return Kind.EQUAL_TO;
            case EQ_STRICT:
                return Kind.STRICT_EQUAL_TO;
            case GT:
                return Kind.GREATER_THAN;
            case GE:
                return Kind.GREATER_THAN_EQUAL;
            case SAR:
                return Kind.RIGHT_SHIFT;
            case ASSIGN_SAR:
                return Kind.RIGHT_SHIFT_ASSIGNMENT;
            case SHR:
                return Kind.UNSIGNED_RIGHT_SHIFT;
            case ASSIGN_SHR:
                return Kind.UNSIGNED_RIGHT_SHIFT_ASSIGNMENT;
            case TERNARY:
                return Kind.CONDITIONAL_EXPRESSION;
            case BIT_XOR:
                return Kind.XOR;
            case ASSIGN_BIT_XOR:
                return Kind.XOR_ASSIGNMENT;
            case BIT_OR:
                return Kind.OR;
            case ASSIGN_BIT_OR:
                return Kind.OR_ASSIGNMENT;
            case OR:
                return Kind.CONDITIONAL_OR;
            case BIT_NOT:
                return Kind.BITWISE_COMPLEMENT;
            case DELETE:
                return Kind.DELETE;
            case SPREAD_ARRAY:
            case SPREAD_ARGUMENT:
                return Kind.SPREAD;
            case TYPEOF:
                return Kind.TYPEOF;
            case VOID:
                return Kind.VOID;
            case YIELD:
                return Kind.YIELD;
            case IN:
                return Kind.IN;
            case INSTANCEOF:
                return Kind.INSTANCE_OF;
            case COMMARIGHT:
                return Kind.COMMA;
            default:
                throw new AssertionError("should not reach here: " + tt);
        }
    }
}
