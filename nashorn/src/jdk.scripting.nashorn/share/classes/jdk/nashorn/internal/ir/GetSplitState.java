/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.ir;

import jdk.nashorn.internal.codegen.CompilerConstants;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Scope;

/**
 * Synthetic AST node that represents loading of the scope object and invocation of the {@link Scope#getSplitState()}
 * method on it. It has no JavaScript source representation and only occurs in synthetic functions created by
 * the split-into-functions transformation.
 */
public final class GetSplitState extends Expression {
    private static final long serialVersionUID = 1L;

    /** The sole instance of this AST node. */
    public final static GetSplitState INSTANCE = new GetSplitState();

    private GetSplitState() {
        super(NO_TOKEN, NO_FINISH);
    }

    @Override
    public Type getType() {
        return Type.INT;
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        return visitor.enterGetSplitState(this) ? visitor.leaveGetSplitState(this) : this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        if (printType) {
            sb.append("{I}");
        }
        sb.append(CompilerConstants.SCOPE.symbolName()).append('.').append(Scope.GET_SPLIT_STATE.name()).append("()");
    }

    private Object readResolve() {
        return INSTANCE;
    }
}
