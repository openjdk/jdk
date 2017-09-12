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
    public R visitAssignment(final AssignmentTree node, final P r) {
        node.getVariable().accept(this, r);
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitCompoundAssignment(final CompoundAssignmentTree node, final P r) {
        node.getVariable().accept(this, r);
        node.getExpression().accept(this, r);
        return null;
    }

    /**
     * Visits a {@code ModuleTree} tree by calling {@code
     * visitUnknown}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of {@code visitUnknown}
     */
    @Override
    public R visitModule(final ModuleTree node, final P p) {
        return visitUnknown(node, p);
    }

    /**
     * Visits an {@code ExportEntryTree} tree by calling {@code
     * visitUnknown}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of {@code visitUnknown}
     */
    @Override
    public R visitExportEntry(final ExportEntryTree node, final P p) {
        return visitUnknown(node, p);
    }

    /**
     * Visits an {@code ImportEntryTree} tree by calling {@code
     * visitUnknown}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of {@code visitUnknown}
     */
    @Override
    public R visitImportEntry(final ImportEntryTree node, final P p) {
        return visitUnknown(node, p);
    }

    @Override
    public R visitBinary(final BinaryTree node, final P r) {
        node.getLeftOperand().accept(this, r);
        node.getRightOperand().accept(this, r);
        return null;
    }

    @Override
    public R visitBlock(final BlockTree node, final P r) {
        node.getStatements().forEach((tree) -> {
            tree.accept(this, r);
        });
        return null;
    }

    @Override
    public R visitBreak(final BreakTree node, final P r) {
        return null;
    }

    @Override
    public R visitCase(final CaseTree node, final P r) {
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
    public R visitCatch(final CatchTree node, final P r) {
        final Tree cond = node.getCondition();
        if (cond != null) {
            cond.accept(this, r);
        }
        node.getParameter().accept(this, r);
        node.getBlock().accept(this, r);
        return null;
    }

    /**
     * Visits a {@code ClassDeclarationTree} tree by calling {@code
     * visitUnknown}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of {@code visitUnknown}
     */
    @Override
    public R visitClassDeclaration(final ClassDeclarationTree node, final P p) {
        return visitUnknown(node, p);
    }

    /**
     * Visits a {@code ClassExpressionTree} tree by calling {@code
     * visitUnknown}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of {@code visitUnknown}
     */
    @Override
    public R visitClassExpression(final ClassExpressionTree node, final P p) {
        return visitUnknown(node, p);
    }

    @Override
    public R visitConditionalExpression(final ConditionalExpressionTree node, final P r) {
        node.getCondition().accept(this, r);
        node.getTrueExpression().accept(this, r);
        node.getFalseExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitContinue(final ContinueTree node, final P r) {
        return null;
    }

    @Override
    public R visitDebugger(final DebuggerTree node, final P r) {
        return null;
    }

    @Override
    public R visitDoWhileLoop(final DoWhileLoopTree node, final P r) {
        node.getStatement().accept(this, r);
        node.getCondition().accept(this, r);
        return null;
    }

    @Override
    public R visitErroneous(final ErroneousTree node, final P r) {
        return null;
    }

    @Override
    public R visitExpressionStatement(final ExpressionStatementTree node, final P r) {
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitForLoop(final ForLoopTree node, final P r) {
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
    public R visitForInLoop(final ForInLoopTree node, final P r) {
        node.getVariable().accept(this, r);
        node.getExpression().accept(this, r);
        final StatementTree stat = node.getStatement();
        if (stat != null) {
            stat.accept(this, r);
        }
        return null;
    }

    /**
     * Visits a {@code ForOfLoopTree} tree by calling {@code
     * visitUnknown}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of {@code visitUnknown}
     */
    @Override
    public R visitForOfLoop(final ForOfLoopTree node, final P p) {
        return visitUnknown(node, p);
    }

    @Override
    public R visitFunctionCall(final FunctionCallTree node, final P r) {
        node.getFunctionSelect().accept(this, r);
        node.getArguments().forEach((tree) -> {
            tree.accept(this, r);
        });
        return null;
    }

    @Override
    public R visitFunctionDeclaration(final FunctionDeclarationTree node, final P r) {
        node.getParameters().forEach((tree) -> {
            tree.accept(this, r);
        });
        node.getBody().accept(this, r);
        return null;
    }

    @Override
    public R visitFunctionExpression(final FunctionExpressionTree node, final P r) {
        node.getParameters().forEach((tree) -> {
            tree.accept(this, r);
        });
        node.getBody().accept(this, r);
        return null;
    }

    @Override
    public R visitIdentifier(final IdentifierTree node, final P r) {
        return null;
    }

    @Override
    public R visitIf(final IfTree node, final P r) {
        node.getCondition().accept(this, r);
        node.getThenStatement().accept(this, r);
        final Tree elseStat = node.getElseStatement();
        if (elseStat != null) {
            elseStat.accept(this, r);
        }
        return null;
    }

    @Override
    public R visitArrayAccess(final ArrayAccessTree node, final P r) {
        node.getExpression().accept(this, r);
        node.getIndex().accept(this, r);
        return null;
    }

    @Override
    public R visitArrayLiteral(final ArrayLiteralTree node, final P r) {
        node.getElements().stream().filter((tree) -> (tree != null)).forEach((tree) -> {
            tree.accept(this, r);
        });
        return null;
    }

    @Override
    public R visitLabeledStatement(final LabeledStatementTree node, final P r) {
        node.getStatement().accept(this, r);
        return null;
    }

    @Override
    public R visitLiteral(final LiteralTree node, final P r) {
        return null;
    }

    @Override
    public R visitParenthesized(final ParenthesizedTree node, final P r) {
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitReturn(final ReturnTree node, final P r) {
        final Tree retExpr = node.getExpression();
        if (retExpr != null) {
            retExpr.accept(this, r);
        }
        return null;
    }

    @Override
    public R visitMemberSelect(final MemberSelectTree node, final P r) {
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitNew(final NewTree node, final P r) {
        node.getConstructorExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitObjectLiteral(final ObjectLiteralTree node, final P r) {
        node.getProperties().forEach((tree) -> {
            tree.accept(this, r);
        });
        return null;
    }

    @Override
    public R visitProperty(final PropertyTree node, final P r) {
        final FunctionExpressionTree getter = node.getGetter();
        if (getter != null) {
            getter.accept(this, r);
        }
        final ExpressionTree key = node.getKey();
        if (key != null) {
            key.accept(this, r);
        }

        final FunctionExpressionTree setter = node.getSetter();
        if (setter != null) {
            setter.accept(this, r);
        }

        final ExpressionTree value = node.getValue();
        if (value != null) {
            value.accept(this, r);
        }
        return null;
    }

    @Override
    public R visitRegExpLiteral(final RegExpLiteralTree node, final P r) {
        return null;
    }

    /**
     * Visits a {@code TemplateLiteralTree} tree by calling {@code
     * visitUnknown}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of {@code visitUnknown}
     */
    @Override
    public R visitTemplateLiteral(final TemplateLiteralTree node, final P p) {
        return visitUnknown(node, p);
    }

    @Override
    public R visitEmptyStatement(final EmptyStatementTree node, final P r) {
        return null;
    }

    /**
     * Visits a {@code SpreadTree} tree by calling {@code
     * visitUnknown}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of {@code visitUnknown}
     */
    @Override
    public R visitSpread(final SpreadTree node, final P p) {
        return visitUnknown(node, p);
    }

    @Override
    public R visitSwitch(final SwitchTree node, final P r) {
        node.getExpression().accept(this, r);
        node.getCases().forEach((tree) -> {
            tree.accept(this, r);
        });
        return null;
    }

    @Override
    public R visitThrow(final ThrowTree node, final P r) {
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitCompilationUnit(final CompilationUnitTree node, final P r) {
        node.getSourceElements().forEach((tree) -> {
            tree.accept(this, r);
        });
        return null;
    }

    @Override
    public R visitTry(final TryTree node, final P r) {
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
    public R visitInstanceOf(final InstanceOfTree node, final P r) {
        node.getType().accept(this, r);
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitUnary(final UnaryTree node, final P r) {
        node.getExpression().accept(this, r);
        return null;
    }

    @Override
    public R visitVariable(final VariableTree node, final P r) {
        if (node.getInitializer() != null) {
            node.getInitializer().accept(this, r);
        }
        return null;
    }

    @Override
    public R visitWhileLoop(final WhileLoopTree node, final P r) {
        node.getCondition().accept(this, r);
        node.getStatement().accept(this, r);
        return null;
    }

    @Override
    public R visitWith(final WithTree node, final P r) {
        node.getScope().accept(this, r);
        node.getStatement().accept(this, r);
        return null;
    }

    /**
     * Visits a {@code YieldTree} tree by calling {@code
     * visitUnknown}.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of {@code visitUnknown}
     */
    @Override
    public R visitYield(final YieldTree node, final P p) {
        return visitUnknown(node, p);
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec The default implementation of this method in {@code
     * SimpleTreeVisitorES5_1} will always throw {@code
     * UnknownTypeException}. This behavior is not required of a
     * subclass.
     *
     * @param node  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return abnormal return by throwing exception always
     * @throws UnknownTreeException
     *  a visitor implementation may optionally throw this exception
     */
    @Override
    public R visitUnknown(final Tree node, final P p) {
        // unknown in ECMAScript 5.1 edition
        throw new UnknownTreeException(node, p);
    }
}
