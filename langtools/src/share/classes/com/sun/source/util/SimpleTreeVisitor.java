/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.source.util;

import com.sun.source.tree.*;

/**
 * A simple visitor for tree nodes.
 *
 * @author Peter von der Ah&eacute;
 * @since 1.6
 */
public class SimpleTreeVisitor <R,P> implements TreeVisitor<R,P> {
    protected final R DEFAULT_VALUE;

    protected SimpleTreeVisitor() {
        DEFAULT_VALUE = null;
    }

    protected SimpleTreeVisitor(R defaultValue) {
        DEFAULT_VALUE = defaultValue;
    }

    protected R defaultAction(Tree node, P p) {
        return DEFAULT_VALUE;
    }

    public final R visit(Tree node, P p) {
        return (node == null) ? null : node.accept(this, p);
    }

    public final R visit(Iterable<? extends Tree> nodes, P p) {
        R r = null;
        if (nodes != null)
            for (Tree node : nodes)
                r = visit(node, p);
        return r;
    }

    public R visitCompilationUnit(CompilationUnitTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitImport(ImportTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitClass(ClassTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitMethod(MethodTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitVariable(VariableTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitEmptyStatement(EmptyStatementTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitBlock(BlockTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitDoWhileLoop(DoWhileLoopTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitWhileLoop(WhileLoopTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitForLoop(ForLoopTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitEnhancedForLoop(EnhancedForLoopTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitLabeledStatement(LabeledStatementTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitSwitch(SwitchTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitCase(CaseTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitSynchronized(SynchronizedTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitTry(TryTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitCatch(CatchTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitConditionalExpression(ConditionalExpressionTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitIf(IfTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitExpressionStatement(ExpressionStatementTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitBreak(BreakTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitContinue(ContinueTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitReturn(ReturnTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitThrow(ThrowTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitAssert(AssertTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitMethodInvocation(MethodInvocationTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitNewClass(NewClassTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitNewArray(NewArrayTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitLambdaExpression(LambdaExpressionTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitParenthesized(ParenthesizedTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitAssignment(AssignmentTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitCompoundAssignment(CompoundAssignmentTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitUnary(UnaryTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitBinary(BinaryTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitTypeCast(TypeCastTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitInstanceOf(InstanceOfTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitArrayAccess(ArrayAccessTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitMemberSelect(MemberSelectTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitMemberReference(MemberReferenceTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitIdentifier(IdentifierTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitLiteral(LiteralTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitPrimitiveType(PrimitiveTypeTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitArrayType(ArrayTypeTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitParameterizedType(ParameterizedTypeTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitUnionType(UnionTypeTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitIntersectionType(IntersectionTypeTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitTypeParameter(TypeParameterTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitWildcard(WildcardTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitModifiers(ModifiersTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitAnnotation(AnnotationTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitAnnotatedType(AnnotatedTypeTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitErroneous(ErroneousTree node, P p) {
        return defaultAction(node, p);
    }

    public R visitOther(Tree node, P p) {
        return defaultAction(node, p);
    }
}
