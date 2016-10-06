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

import java.util.List;

/**
 * A simple implementation of the TreeVisitor for ECMAScript edition 6.
 *
 * <p>The visit methods corresponding to ES 6 language constructs walk the
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
public class SimpleTreeVisitorES6<R, P> extends SimpleTreeVisitorES5_1<R, P> {
    @Override
    public R visitCompilationUnit(final CompilationUnitTree node, final P r) {
        final ModuleTree mod = node.getModule();
        if (mod != null) {
            mod.accept(this, r);
        }
        return super.visitCompilationUnit(node, r);
    }

    /**
     * Visit Module tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    @Override
    public R visitModule(final ModuleTree node, final P p) {
        node.getImportEntries().forEach(e -> visitImportEntry(e, p));
        node.getLocalExportEntries().forEach(e -> visitExportEntry(e, p));
        node.getIndirectExportEntries().forEach(e -> visitExportEntry(e, p));
        node.getStarExportEntries().forEach(e -> visitExportEntry(e, p));
        return null;
    }

    /**
     * Visit Module ExportEntry tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    @Override
    public R visitExportEntry(final ExportEntryTree node, final P p) {
        return null;
    }

    /**
     * Visit Module ImportEntry tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    @Override
    public R visitImportEntry(final ImportEntryTree node, final P p) {
        return null;
    }

   /**
    * Visit class statement tree.
    *
    * @param node node being visited
    * @param p extra parameter passed to the visitor
    * @return value from the visitor
    */
    @Override
    public R visitClassDeclaration(final ClassDeclarationTree node, final P p) {
        node.getName().accept(this, p);
        final ExpressionTree heritage = node.getClassHeritage();
        if (heritage != null) {
            heritage.accept(this, p);
        }
        final PropertyTree constructor = node.getConstructor();
        if (constructor != null) {
            constructor.accept(this, p);
        }
        final List<? extends PropertyTree> elements = node.getClassElements();
        if (elements != null) {
            for (final PropertyTree prop : elements) {
                prop.accept(this, p);
            }
        }

        return null;
    }

    /**
     * Visit class expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    @Override
    public R visitClassExpression(final ClassExpressionTree node, final P p) {
        node.getName().accept(this, p);
        final ExpressionTree heritage = node.getClassHeritage();
        if (heritage != null) {
            heritage.accept(this, p);
        }
        final PropertyTree constructor = node.getConstructor();
        if (constructor != null) {
            constructor.accept(this, p);
        }
        final List<? extends PropertyTree> elements = node.getClassElements();
        if (elements != null) {
            for (final PropertyTree prop : elements) {
                prop.accept(this, p);
            }
        }

        return null;
    }

    /**
     * Visit for..of statement tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    @Override
    public R visitForOfLoop(final ForOfLoopTree node, final P p) {
        node.getVariable().accept(this, p);
        node.getExpression().accept(this, p);
        final StatementTree stat = node.getStatement();
        if (stat != null) {
            stat.accept(this, p);
        }
        return null;
    }

    /**
     * Visit 'yield' expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    @Override
    public R visitYield(final YieldTree node, final P p) {
        node.getExpression().accept(this, p);
        return null;
    }

    /**
     * Visit 'spread' expression tree.
     *
     * @param node node being visited
     * @param p extra parameter passed to the visitor
     * @return value from the visitor
     */
    @Override
    public R visitSpread(final SpreadTree node, final P p) {
        node.getExpression().accept(this, p);
        return null;
    }

   /**
    * Visit template literal tree.
    *
    * @param node node being visited
    * @param p extra parameter passed to the visitor
    * @return value from the visitor
    */
    @Override
    public R visitTemplateLiteral(final TemplateLiteralTree node, final P p) {
        final List<? extends ExpressionTree> expressions = node.getExpressions();
        for (final ExpressionTree expr : expressions) {
            expr.accept(this, p);
        }
        return null;
    }

    @Override
    public R visitVariable(final VariableTree node, final P r) {
        final ExpressionTree expr = node.getBinding();
        if (expr != null) {
            expr.accept(this, r);
        }
        super.visitVariable(node, r);
        return null;
    }
}
