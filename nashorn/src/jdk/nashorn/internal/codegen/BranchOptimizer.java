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

package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.codegen.Condition.EQ;
import static jdk.nashorn.internal.codegen.Condition.GE;
import static jdk.nashorn.internal.codegen.Condition.GT;
import static jdk.nashorn.internal.codegen.Condition.LE;
import static jdk.nashorn.internal.codegen.Condition.LT;
import static jdk.nashorn.internal.codegen.Condition.NE;

import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.JoinPredecessorExpression;
import jdk.nashorn.internal.ir.LocalVariableConversion;
import jdk.nashorn.internal.ir.UnaryNode;

/**
 * Branch optimizer for CodeGenerator. Given a jump condition this helper
 * class attempts to simplify the control flow
 */
final class BranchOptimizer {

    private final CodeGenerator codegen;
    private final MethodEmitter method;

    BranchOptimizer(final CodeGenerator codegen, final MethodEmitter method) {
        this.codegen = codegen;
        this.method  = method;
    }

    void execute(final Expression node, final Label label, final boolean state) {
        branchOptimizer(node, label, state);
    }

    private void branchOptimizer(final UnaryNode unaryNode, final Label label, final boolean state) {
        final Expression rhs = unaryNode.getExpression();

        switch (unaryNode.tokenType()) {
        case NOT:
            branchOptimizer(rhs, label, !state);
            return;
        default:
            if (unaryNode.getType().isBoolean()) {
                branchOptimizer(rhs, label, state);
                return;
            }
            break;
        }

        loadTestAndJump(unaryNode, label, state);
    }

    private void branchOptimizer(final BinaryNode binaryNode, final Label label, final boolean state) {
        final Expression lhs = binaryNode.lhs();
        final Expression rhs = binaryNode.rhs();

        switch (binaryNode.tokenType()) {
        case AND:
            if (state) {
                final Label skip = new Label("skip");
                optimizeLogicalOperand(lhs, skip,  false, false);
                optimizeLogicalOperand(rhs, label, true,  true);
                method.label(skip);
            } else {
                optimizeLogicalOperand(lhs, label, false, false);
                optimizeLogicalOperand(rhs, label, false, true);
            }
            return;

        case OR:
            if (state) {
                optimizeLogicalOperand(lhs, label, true, false);
                optimizeLogicalOperand(rhs, label, true, true);
            } else {
                final Label skip = new Label("skip");
                optimizeLogicalOperand(lhs, skip,  true,  false);
                optimizeLogicalOperand(rhs, label, false, true);
                method.label(skip);
            }
            return;

        case EQ:
        case EQ_STRICT:
            codegen.loadBinaryOperands(binaryNode);
            method.conditionalJump(state ? EQ : NE, true, label);
            return;

        case NE:
        case NE_STRICT:
            codegen.loadBinaryOperands(binaryNode);
            method.conditionalJump(state ? NE : EQ, true, label);
            return;

        case GE:
            codegen.loadBinaryOperands(binaryNode);
            method.conditionalJump(state ? GE : LT, false, label);
            return;

        case GT:
            codegen.loadBinaryOperands(binaryNode);
            method.conditionalJump(state ? GT : LE, false, label);
            return;

        case LE:
            codegen.loadBinaryOperands(binaryNode);
            method.conditionalJump(state ? LE : GT, true, label);
            return;

        case LT:
            codegen.loadBinaryOperands(binaryNode);
            method.conditionalJump(state ? LT : GE, true, label);
            return;

        default:
            break;
        }

        loadTestAndJump(binaryNode, label, state);
    }

    private void optimizeLogicalOperand(final Expression expr, final Label label, final boolean state, final boolean isRhs) {
        final JoinPredecessorExpression jpexpr = (JoinPredecessorExpression)expr;
        if(LocalVariableConversion.hasLiveConversion(jpexpr)) {
            final Label after = new Label("after");
            branchOptimizer(jpexpr.getExpression(), after, !state);
            method.beforeJoinPoint(jpexpr);
            method._goto(label);
            method.label(after);
            if(isRhs) {
                method.beforeJoinPoint(jpexpr);
            }
        } else {
            branchOptimizer(jpexpr.getExpression(), label, state);
        }
    }
    private void branchOptimizer(final Expression node, final Label label, final boolean state) {
        if (node instanceof BinaryNode) {
            branchOptimizer((BinaryNode)node, label, state);
            return;
        }

        if (node instanceof UnaryNode) {
            branchOptimizer((UnaryNode)node, label, state);
            return;
        }

        loadTestAndJump(node, label, state);
    }

    private void loadTestAndJump(final Expression node, final Label label, final boolean state) {
        codegen.loadExpressionAsBoolean(node);
        if (state) {
            method.ifne(label);
        } else {
            method.ifeq(label);
        }
    }
}
