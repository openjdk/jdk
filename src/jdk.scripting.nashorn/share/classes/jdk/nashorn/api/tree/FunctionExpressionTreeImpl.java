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
import jdk.nashorn.internal.ir.FunctionNode;

final class FunctionExpressionTreeImpl extends ExpressionTreeImpl
    implements FunctionExpressionTree {
    private final FunctionNode funcNode;
    private final IdentifierTree funcName;
    private final List<? extends ExpressionTree> params;
    private final Tree body;

    FunctionExpressionTreeImpl(final FunctionNode node,
            final List<? extends ExpressionTree> params,
            final BlockTree body) {
        super(node);
        funcNode = node;
        assert !funcNode.isDeclared() || funcNode.isAnonymous() : "function expression expected";

        final FunctionNode.Kind kind = node.getKind();
        if (node.isAnonymous() || kind == FunctionNode.Kind.GETTER || kind == FunctionNode.Kind.SETTER) {
            funcName = null;
        } else {
            funcName = new IdentifierTreeImpl(node.getIdent());
        }

        this.params = params;
        if (node.getFlag(FunctionNode.HAS_EXPRESSION_BODY)) {
            final StatementTree first = body.getStatements().get(0);
            assert first instanceof ReturnTree : "consise func. expression should have a return statement";
            this.body = ((ReturnTree)first).getExpression();
        } else {
            this.body = body;
        }
    }

    @Override
    public Tree.Kind getKind() {
        return Tree.Kind.FUNCTION_EXPRESSION;
    }

    @Override
    public IdentifierTree getName() {
        return funcName;
    }

    @Override
    public List<? extends ExpressionTree> getParameters() {
        return params;
    }

    @Override
    public Tree getBody() {
        return body;
    }

    @Override
    public boolean isStrict() {
        return funcNode.isStrict();
    }

    @Override
    public boolean isArrow() {
        return funcNode.getKind() == FunctionNode.Kind.ARROW;
    }

    @Override
    public boolean isGenerator() {
        return funcNode.getKind() == FunctionNode.Kind.GENERATOR;
    }

    @Override
    public <R,D> R accept(final TreeVisitor<R,D> visitor, final D data) {
        return visitor.visitFunctionExpression(this, data);
    }
}
