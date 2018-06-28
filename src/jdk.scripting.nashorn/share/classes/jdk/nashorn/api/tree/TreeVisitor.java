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

/**
 * A visitor of trees, in the style of the visitor design pattern.
 * Classes implementing this interface are used to operate
 * on a tree when the kind of tree is unknown at compile time.
 * When a visitor is passed to an tree's {@link Tree#accept
 * accept} method, the <code>visit<i>Xyz</i></code> method most applicable
 * to that tree is invoked.
 *
 * <p> Classes implementing this interface may or may not throw a
 * {@code NullPointerException} if the additional parameter {@code p}
 * is {@code null}; see documentation of the implementing class for
 * details.
 *
 * <p> <b>WARNING:</b> It is possible that methods will be added to
 this interface to accommodate new, currently unknown, language
 structures added to future versions of the ECMAScript programming
 language. When new visit methods are added for new Tree subtypes,
 default method bodies will be introduced which will call visitUnknown
 method as a fallback.
 *
 * @deprecated Nashorn JavaScript script engine and APIs, and the jjs tool
 * are deprecated with the intent to remove them in a future release.
 *
 * @param <R> the return type of this visitor's methods.  Use {@link
 *            Void} for visitors that do not need to return results.
 * @param <P> the type of the additional parameter to this visitor's
 *            methods.  Use {@code Void} for visitors that do not need an
 *            additional parameter.
 *
 * @since 9
 */
@Deprecated(since="11", forRemoval=true)
public interface TreeVisitor<R,P> {
    /**
     * Visit assignment tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitAssignment(AssignmentTree node, P p);

    /**
     * Visit compound assignment tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitCompoundAssignment(CompoundAssignmentTree node, P p);

    /**
     * Visit binary expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitBinary(BinaryTree node, P p);

    /**
     * Visit block statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitBlock(BlockTree node, P p);

    /**
     * Visit break statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitBreak(BreakTree node, P p);

    /**
     * Visit case statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitCase(CaseTree node, P p);

    /**
     * Visit catch block statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitCatch(CatchTree node, P p);

    /**
     * Visit class statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitClassDeclaration(ClassDeclarationTree node, P p);

    /**
     * Visit class expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitClassExpression(ClassExpressionTree node, P p);

    /**
     * Visit conditional expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitConditionalExpression(ConditionalExpressionTree node, P p);

    /**
     * Visit continue statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitContinue(ContinueTree node, P p);

    /**
     * Visit debugger statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitDebugger(DebuggerTree node, P p);

    /**
     * Visit do-while statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitDoWhileLoop(DoWhileLoopTree node, P p);

    /**
     * Visit error expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitErroneous(ErroneousTree node, P p);

    /**
     * Visit expression statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitExpressionStatement(ExpressionStatementTree node, P p);

    /**
     * Visit 'for' statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitForLoop(ForLoopTree node, P p);

    /**
     * Visit for..in statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitForInLoop(ForInLoopTree node, P p);

    /**
     * Visit for..of statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitForOfLoop(ForOfLoopTree node, P p);

    /**
     * Visit function call expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitFunctionCall(FunctionCallTree node, P p);

    /**
     * Visit function declaration tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitFunctionDeclaration(FunctionDeclarationTree node, P p);

    /**
     * Visit function expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitFunctionExpression(FunctionExpressionTree node, P p);

    /**
     * Visit identifier tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitIdentifier(IdentifierTree node, P p);

    /**
     * Visit 'if' statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitIf(IfTree node, P p);

    /**
     * Visit array access expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitArrayAccess(ArrayAccessTree node, P p);

    /**
     * Visit array literal expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitArrayLiteral(ArrayLiteralTree node, P p);

    /**
     * Visit labeled statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitLabeledStatement(LabeledStatementTree node, P p);

    /**
     * Visit literal expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitLiteral(LiteralTree node, P p);

    /**
     * Visit parenthesized expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitParenthesized(ParenthesizedTree node, P p);

    /**
     * Visit return statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitReturn(ReturnTree node, P p);

    /**
     * Visit member select expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitMemberSelect(MemberSelectTree node, P p);

    /**
     * Visit 'new' expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitNew(NewTree node, P p);

    /**
     * Visit object literal tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitObjectLiteral(ObjectLiteralTree node, P p);

    /**
     * Visit a property of an object literal expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitProperty(PropertyTree node, P p);

    /**
     * Visit regular expression literal tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitRegExpLiteral(RegExpLiteralTree node, P p);

    /**
     * Visit template literal tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitTemplateLiteral(TemplateLiteralTree node, P p);

    /**
     * Visit an empty statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitEmptyStatement(EmptyStatementTree node, P p);

    /**
     * Visit 'spread' expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitSpread(SpreadTree node, P p);

    /**
     * Visit 'switch' statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitSwitch(SwitchTree node, P p);

    /**
     * Visit 'throw' expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitThrow(ThrowTree node, P p);

    /**
     * Visit compilation unit tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitCompilationUnit(CompilationUnitTree node, P p);

    /**
     * Visit Module tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitModule(ModuleTree node, P p);

    /**
     * Visit Module ExportEntry tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitExportEntry(ExportEntryTree node, P p);

    /**
     * Visit Module ImportEntry tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitImportEntry(ImportEntryTree node, P p);

    /**
     * Visit 'try' statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitTry(TryTree node, P p);

    /**
     * Visit 'instanceof' expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitInstanceOf(InstanceOfTree node, P p);

    /**
     * Visit unary expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitUnary(UnaryTree node, P p);

    /**
     * Visit variable declaration tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitVariable(VariableTree node, P p);

    /**
     * Visit 'while' statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitWhileLoop(WhileLoopTree node, P p);

    /**
     * Visit 'with' statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitWith(WithTree node, P p);

    /**
     * Visit 'yield' expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitYield(YieldTree node, P p);

    /**
     * Visit unknown expression/statement tree. This fallback will be
     * called if new Tree subtypes are introduced in future. A specific
     * implementation may throw {{@linkplain UnknownTreeException unknown tree exception}
     * if the visitor implementation was for an older language version.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    R visitUnknown(Tree node, P p);
}
