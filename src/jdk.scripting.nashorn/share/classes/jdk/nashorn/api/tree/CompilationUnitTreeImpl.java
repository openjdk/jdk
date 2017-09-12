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

import java.util.List;
import jdk.nashorn.internal.ir.FunctionNode;

final class CompilationUnitTreeImpl extends TreeImpl
    implements CompilationUnitTree {
    private final FunctionNode funcNode;
    private final List<? extends Tree> elements;
    private final ModuleTree module;

    CompilationUnitTreeImpl(final FunctionNode node,
            final List<? extends Tree> elements,
            final ModuleTree module) {
        super(node);
        this.funcNode = node;
        assert funcNode.getKind() == FunctionNode.Kind.SCRIPT ||
                funcNode.getKind() == FunctionNode.Kind.MODULE :
                "script or module function expected";
        this.elements = elements;
        this.module = module;
    }

    @Override
    public Tree.Kind getKind() {
        return Tree.Kind.COMPILATION_UNIT;
    }

    @Override
    public List<? extends Tree> getSourceElements() {
        return elements;
    }

    @Override
    public String getSourceName() {
        return funcNode.getSourceName();
    }

    @Override
    public boolean isStrict() {
        return funcNode.isStrict();
    }

    @Override
    public LineMap getLineMap() {
        return new LineMapImpl(funcNode.getSource());
    }

    @Override
    public ModuleTree getModule() {
        return module;
    }

    @Override
    public <R,D> R accept(final TreeVisitor<R,D> visitor, final D data) {
        return visitor.visitCompilationUnit(this, data);
    }
}
