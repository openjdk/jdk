/*
 * Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.source.tree;

import jdk.internal.javac.PreviewFeature;

/**
 * A visitor of trees, in the style of the visitor design pattern.
 * Classes implementing this interface are used to operate
 * on a tree when the kind of tree is unknown at compile time.
 * When a visitor is passed to a tree's {@link Tree#accept
 * accept} method, the <code>visit<i>Xyz</i></code> method most applicable
 * to that tree is invoked.
 *
 * <p> Classes implementing this interface may or may not throw a
 * {@code NullPointerException} if the additional parameter {@code p}
 * is {@code null}; see documentation of the implementing class for
 * details.
 *
 * <p> <b>WARNING:</b> It is possible that methods will be added to
 * this interface to accommodate new, currently unknown, language
 * structures added to future versions of the Java programming
 * language.  Therefore, visitor classes directly implementing this
 * interface may be source incompatible with future versions of the
 * platform.
 *
 * @param <R> the return type of this visitor's methods.  Use {@link
 *            Void} for visitors that do not need to return results.
 * @param <P> the type of the additional parameter to this visitor's
 *            methods.  Use {@code Void} for visitors that do not need an
 *            additional parameter.
 *
 * @author Peter von der Ah&eacute;
 * @author Jonathan Gibbons
 *
 * @since 1.6
 */
public interface TreeVisitor<R,P> {
    /**
     * Visits an {@code AnnotatedTypeTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitAnnotatedType(AnnotatedTypeTree node, P p);

    /**
     * Visits an {@code AnnotatedTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitAnnotation(AnnotationTree node, P p);

    /**
     * Visits a {@code MethodInvocationTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitMethodInvocation(MethodInvocationTree node, P p);

    /**
     * Visits an {@code AssertTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitAssert(AssertTree node, P p);

    /**
     * Visits an {@code AssignmentTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitAssignment(AssignmentTree node, P p);

    /**
     * Visits a {@code CompoundAssignmentTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitCompoundAssignment(CompoundAssignmentTree node, P p);

    /**
     * Visits a {@code BinaryTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitBinary(BinaryTree node, P p);

    /**
     * Visits a {@code BlockTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitBlock(BlockTree node, P p);

    /**
     * Visits a {@code BreakTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitBreak(BreakTree node, P p);

    /**
     * Visits a {@code CaseTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitCase(CaseTree node, P p);

    /**
     * Visits a {@code CatchTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitCatch(CatchTree node, P p);

    /**
     * Visits a {@code ClassTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitClass(ClassTree node, P p);

    /**
     * Visits a {@code ConditionalExpressionTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitConditionalExpression(ConditionalExpressionTree node, P p);

    /**
     * Visits a {@code ContinueTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitContinue(ContinueTree node, P p);

    /**
     * Visits a {@code DoWhileTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitDoWhileLoop(DoWhileLoopTree node, P p);

    /**
     * Visits an {@code ErroneousTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitErroneous(ErroneousTree node, P p);

    /**
     * Visits an {@code ExpressionStatementTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitExpressionStatement(ExpressionStatementTree node, P p);

    /**
     * Visits an {@code EnhancedForLoopTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitEnhancedForLoop(EnhancedForLoopTree node, P p);

    /**
     * Visits a {@code ForLoopTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitForLoop(ForLoopTree node, P p);

    /**
     * Visits an {@code IdentifierTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitIdentifier(IdentifierTree node, P p);

    /**
     * Visits an {@code IfTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitIf(IfTree node, P p);

    /**
     * Visits an {@code ImportTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitImport(ImportTree node, P p);

    /**
     * Visits an {@code ArrayAccessTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitArrayAccess(ArrayAccessTree node, P p);

    /**
     * Visits a {@code LabeledStatementTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitLabeledStatement(LabeledStatementTree node, P p);

    /**
     * Visits a {@code LiteralTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitLiteral(LiteralTree node, P p);

    /**
     * Visits a StringTemplateTree node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     * @since 21
     */
    @PreviewFeature(feature=PreviewFeature.Feature.STRING_TEMPLATES, reflective=true)
    R visitStringTemplate(StringTemplateTree node, P p);

    /**
     * Visits a {@code AnyPatternTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     * @since 22
     */
    R visitAnyPattern(AnyPatternTree node, P p);

    /**
     * Visits a {@code BindingPatternTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     * @since 16
     */
    R visitBindingPattern(BindingPatternTree node, P p);

    /**
     * Visits a {@code DefaultCaseLabelTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     * @since 21
     */
    R visitDefaultCaseLabel(DefaultCaseLabelTree node, P p);

    /**
     * Visits a {@code ConstantCaseLabelTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     * @since 21
     */
    R visitConstantCaseLabel(ConstantCaseLabelTree node, P p);

    /**
     * Visits a {@code PatternCaseLabelTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     * @since 21
     */
    R visitPatternCaseLabel(PatternCaseLabelTree node, P p);

    /**
     * Visits a {@code DeconstructionPatternTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     * @since 21
     */
    R visitDeconstructionPattern(DeconstructionPatternTree node, P p);

    /**
     * Visits a {@code MethodTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitMethod(MethodTree node, P p);

    /**
     * Visits a {@code ModifiersTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitModifiers(ModifiersTree node, P p);

    /**
     * Visits a {@code NewArrayTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitNewArray(NewArrayTree node, P p);

    /**
     * Visits a {@code NewClassTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitNewClass(NewClassTree node, P p);

    /**
     * Visits a {@code LambdaExpressionTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitLambdaExpression(LambdaExpressionTree node, P p);

    /**
     * Visits a {@code PackageTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitPackage(PackageTree node, P p);

    /**
     * Visits a {@code ParenthesizedTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitParenthesized(ParenthesizedTree node, P p);

    /**
     * Visits a {@code ReturnTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitReturn(ReturnTree node, P p);

    /**
     * Visits a {@code MemberSelectTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitMemberSelect(MemberSelectTree node, P p);

    /**
     * Visits a {@code MemberReferenceTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitMemberReference(MemberReferenceTree node, P p);

    /**
     * Visits an {@code EmptyStatementTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitEmptyStatement(EmptyStatementTree node, P p);

    /**
     * Visits a {@code SwitchTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitSwitch(SwitchTree node, P p);

    /**
     * Visits a {@code SwitchExpressionTree} node.
     *
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     * @since 12
     */
    R visitSwitchExpression(SwitchExpressionTree node, P p);

    /**
     * Visits a {@code SynchronizedTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitSynchronized(SynchronizedTree node, P p);

    /**
     * Visits a {@code ThrowTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitThrow(ThrowTree node, P p);

    /**
     * Visits a {@code CompilationUnitTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitCompilationUnit(CompilationUnitTree node, P p);

    /**
     * Visits a {@code TryTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitTry(TryTree node, P p);

    /**
     * Visits a {@code ParameterizedTypeTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitParameterizedType(ParameterizedTypeTree node, P p);

    /**
     * Visits a {@code UnionTypeTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitUnionType(UnionTypeTree node, P p);

    /**
     * Visits an {@code IntersectionTypeTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitIntersectionType(IntersectionTypeTree node, P p);

    /**
     * Visits an {@code ArrayTypeTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitArrayType(ArrayTypeTree node, P p);

    /**
     * Visits a {@code TypeCastTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitTypeCast(TypeCastTree node, P p);

    /**
     * Visits a {@code PrimitiveTypeTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitPrimitiveType(PrimitiveTypeTree node, P p);

    /**
     * Visits a {@code TypeParameterTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitTypeParameter(TypeParameterTree node, P p);

    /**
     * Visits an {@code InstanceOfTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitInstanceOf(InstanceOfTree node, P p);

    /**
     * Visits a {@code UnaryTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitUnary(UnaryTree node, P p);

    /**
     * Visits a {@code VariableTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitVariable(VariableTree node, P p);

    /**
     * Visits a {@code WhileLoopTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitWhileLoop(WhileLoopTree node, P p);

    /**
     * Visits a {@code WildcardTypeTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitWildcard(WildcardTree node, P p);

    /**
     * Visits a {@code ModuleTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitModule(ModuleTree node, P p);

    /**
     * Visits an {@code ExportsTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitExports(ExportsTree node, P p);

    /**
     * Visits an {@code OpensTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitOpens(OpensTree node, P p);

    /**
     * Visits a {@code ProvidesTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitProvides(ProvidesTree node, P p);

    /**
     * Visits a {@code RequiresTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitRequires(RequiresTree node, P p);

    /**
     * Visits a {@code UsesTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitUses(UsesTree node, P p);

    /**
     * Visits an unknown type of {@code Tree} node.
     * This can occur if the language evolves and new kinds
     * of nodes are added to the {@code Tree} hierarchy.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     */
    R visitOther(Tree node, P p);

    /**
     * Visits a {@code YieldTree} node.
     * @param node the node being visited
     * @param p a parameter value
     * @return a result value
     * @since 13
     */
    R visitYield(YieldTree node, P p);
}
