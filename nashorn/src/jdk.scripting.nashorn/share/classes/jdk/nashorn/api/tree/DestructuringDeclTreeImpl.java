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

import jdk.nashorn.internal.ir.ExpressionStatement;
import jdk.nashorn.internal.parser.TokenType;

// This implementation of VariableTree represents a destructuring declaration
final class DestructuringDeclTreeImpl extends StatementTreeImpl
        implements VariableTree {

    private final TokenType declType;
    private final ExpressionTree lhs;
    private final ExpressionTree init;

    DestructuringDeclTreeImpl(ExpressionStatement exprStat, final ExpressionTree lhs, final ExpressionTree init) {
        super(exprStat);
        assert exprStat.destructuringDeclarationType() != null : "expecting a destructuring decl. statement";

        this.declType = exprStat.destructuringDeclarationType();
        this.lhs = lhs;
        this.init = init;
    }

    @Override
    public Kind getKind() {
        return Kind.VARIABLE;
    }

    @Override
    public ExpressionTree getBinding() {
        return lhs;
    }

    @Override
    public ExpressionTree getInitializer() {
        return init;
    }

    @Override
    public boolean isConst() {
        return declType == TokenType.CONST;
    }

    @Override
    public boolean isLet() {
        return declType == TokenType.LET;
    }

    @Override
    public <R,D> R accept(final TreeVisitor<R,D> visitor, final D data) {
        return visitor.visitVariable(this, data);
    }
}
