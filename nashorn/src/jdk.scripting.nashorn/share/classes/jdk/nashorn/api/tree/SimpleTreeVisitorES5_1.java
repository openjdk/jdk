/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * A simple implementation of the TreeVisitor for ECMAScript edition 5.1.
 *
 * <p>The visit methods corresponding to ES 5.1 language constructs walk the
 * "components" of the given tree by calling accept method passing the
 * current visitor and the additional parameter.
 *
 * <p>For constructs introduced in later versions, {@code visitUnknown}
 * is called instead which throws {@link UnknownTreeException}.
 *
 * <p> Methods in this class may be overridden subject to their
 * general contract.  Note that annotating methods in concrete
 * subclasses with {@link java.lang.Override @Override} will help
 * ensure that methods are overridden as intended.
 *
 * @param <R> the return type of this visitor's methods.  Use {@link
 *            Void} for visitors that do not need to return results.
 * @param <P> the type of the additional parameter to this visitor's
 *            methods.  Use {@code Void} for visitors that do not need an
 *            additional parameter.
 */
public class SimpleTreeVisitorES5_1<R, P> implements TreeVisitor<R, P> {
    @Override
    public R visitAssignment(AssignmentTree node, P r) {
        node.getVariable().accept(this, r);
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitCompoundAssignment(CompoundAssignmentTree node, P r) {
        node.getVariable().accept(this, r);
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitBinary(BinaryTree node, P r) {
        node.getLeftOperand().accept(this, r);
        node.getRightOperand().accept(this, r);
        return null;
    }

    @Override
    public R visitBlock(BlockTree node, P r) {
        node.getStatements().forEach((tree) -> {
            tree.accept(this, r);
        });
        return null;
    }

    @Override
    public R visitBreak(BreakTree node, P r) {
        return null;
    }

    @Override
    public R visitCase(CaseTree node, P r) {
        final Tree caseVal = node.getExpression();
        if (caseVal != null) {
            caseVal.accept(this, r);
        }

        node.getStatements().forEach((tree) -> {
            tree.accept(this, r);
        });
        return null;
    }

    @Override
    public R visitCatch(CatchTree node, P r) {
        final Tree cond = node.getCondition();
        if (cond != null) {
            cond.accept(this, r);
        }
        node.getParameter().accept(this, r);
        node.getBlock().accept(this, r);
        return null;
    }

    @Override
    public R visitConditionalExpression(ConditionalExpressionTree node, P r) {
        node.getCondition().accept(this, r);
        node.getTrueExpression().accept(this, r);
        node.getFalseExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitContinue(ContinueTree node, P r) {
        return null;
    }

    @Override
    public R visitDebugger(DebuggerTree node, P r) {
        return null;
    }

    @Override
    public R visitDoWhileLoop(DoWhileLoopTree node, P r) {
        node.getStatement().accept(this, r);
        node.getCondition().accept(this, r);
        return null;
    }

    @Override
    public R visitErroneous(ErroneousTree node, P r) {
        return null;
    }

    @Override
    public R visitExpressionStatement(ExpressionStatementTree node, P r) {
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitForLoop(ForLoopTree node, P r) {
        final Tree init = node.getInitializer();
        if (init != null) {
            init.accept(this, r);
        }

        final Tree cond = node.getCondition();
        if (cond != null) {
            cond.accept(this, r);
        }

        final Tree update = node.getUpdate();
        if (update != null) {
            update.accept(this, r);
        }

        node.getStatement().accept(this, r);
        return null;
    }

    @Override
    public R visitForInLoop(ForInLoopTree node, P r) {
        node.getVariable().accept(this, r);
        node.getExpression().accept(this, r);
        final StatementTree stat = node.getStatement();
        if (stat != null) {
            stat.accept(this, r);
        }
        return null;
    }

    @Override
    public R visitFunctionCall(FunctionCallTree node, P r) {
        node.getFunctionSelect().accept(this, r);
        node.getArguments().forEach((tree) -> {
            tree.accept(this, r);
        });
        return null;
    }

    @Override
    public R visitFunctionDeclaration(FunctionDeclarationTree node, P r) {
        node.getParameters().forEach((tree) -> {
            tree.accept(this, r);
        });
        node.getBody().accept(this, r);
        return null;
    }

    @Override
    public R visitFunctionExpression(FunctionExpressionTree node, P r) {
        node.getParameters().forEach((tree) -> {
            tree.accept(this, r);
        });
        node.getBody().accept(this, r);
        return null;
    }

    @Override
    public R visitIdentifier(IdentifierTree node, P r) {
        return null;
    }

    @Override
    public R visitIf(IfTree node, P r) {
        node.getCondition().accept(this, r);
        node.getThenStatement().accept(this, r);
        final Tree elseStat = node.getElseStatement();
        if (elseStat != null) {
            elseStat.accept(this, r);
        }
        return null;
    }

    @Override
    public R visitArrayAccess(ArrayAccessTree node, P r) {
        node.getExpression().accept(this, r);
        node.getIndex().accept(this, r);
        return null;
    }

    @Override
    public R visitArrayLiteral(ArrayLiteralTree node, P r) {
        node.getElements().stream().filter((tree) -> (tree != null)).forEach((tree) -> {
            tree.accept(this, r);
        });
        return null;
    }

    @Override
    public R visitLabeledStatement(LabeledStatementTree node, P r) {
        node.getStatement().accept(this, r);
        return null;
    }

    @Override
    public R visitLiteral(LiteralTree node, P r) {
        return null;
    }

    @Override
    public R visitParenthesized(ParenthesizedTree node, P r) {
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitReturn(ReturnTree node, P r) {
        final Tree retExpr = node.getExpression();
        if (retExpr != null) {
            retExpr.accept(this, r);
        }
        return null;
    }

    @Override
    public R visitMemberSelect(MemberSelectTree node, P r) {
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitNew(NewTree node, P r) {
        node.getConstructorExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitObjectLiteral(ObjectLiteralTree node, P r) {
        node.getProperties().forEach((tree) -> {
            tree.accept(this, r);
        });
        return null;
    }

    @Override
    public R visitProperty(PropertyTree node, P r) {
        FunctionExpressionTree getter = node.getGetter();
        if (getter != null) {
            getter.accept(this, r);
        }
        ExpressionTree key = node.getKey();
        if (key != null) {
            key.accept(this, r);
        }

        FunctionExpressionTree setter = node.getSetter();
        if (setter != null) {
            setter.accept(this, r);
        }

        ExpressionTree value = node.getValue();
        if (value != null) {
            value.accept(this, r);
        }
        return null;
    }

    @Override
    public R visitRegExpLiteral(RegExpLiteralTree node, P r) {
        return null;
    }

    @Override
    public R visitEmptyStatement(EmptyStatementTree node, P r) {
        return null;
    }

    @Override
    public R visitSwitch(SwitchTree node, P r) {
        node.getExpression().accept(this, r);
        node.getCases().forEach((tree) -> {
            tree.accept(this, r);
        });
        return null;
    }

    @Override
    public R visitThrow(ThrowTree node, P r) {
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitCompilationUnit(CompilationUnitTree node, P r) {
        node.getSourceElements().forEach((tree) -> {
            tree.accept(this, r);
        });
        return null;
    }

    @Override
    public R visitTry(TryTree node, P r) {
        node.getBlock().accept(this, r);
        node.getCatches().forEach((tree) -> {
            tree.accept(this, r);
        });

        final Tree finallyBlock = node.getFinallyBlock();
        if (finallyBlock != null) {
            finallyBlock.accept(this, r);
        }
        return null;
    }

    @Override
    public R visitInstanceOf(InstanceOfTree node, P r) {
        node.getType().accept(this, r);
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitUnary(UnaryTree node, P r) {
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitVariable(VariableTree node, P r) {
        if (node.getInitializer() != null) {
            node.getInitializer().accept(this, r);
        }
        return null;
    }

    @Override
    public R visitWhileLoop(WhileLoopTree node, P r) {
        node.getCondition().accept(this, r);
        node.getStatement().accept(this, r);
        return null;
    }

    @Override
    public R visitWith(WithTree node, P r) {
        node.getScope().accept(this, r);
        node.getStatement().accept(this, r);
        return null;
    }

    @Override
    public R visitUnknown(Tree node, P r) {
        // unknown in ECMAScript 5.1 edition
        throw new UnknownTreeException(node, r);
    }
}
