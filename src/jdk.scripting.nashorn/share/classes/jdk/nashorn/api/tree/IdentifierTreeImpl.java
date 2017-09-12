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

import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.Module;

final class IdentifierTreeImpl extends ExpressionTreeImpl implements IdentifierTree {
    private final String name;

    IdentifierTreeImpl(final IdentNode node) {
        super(node);
        this.name = node.getName();
    }

    @Override
    public Kind getKind() {
        return Kind.IDENTIFIER;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isRestParameter() {
        return ((IdentNode)node).isRestParameter();
    }

    @Override
    public boolean isSuper() {
        final IdentNode ident = (IdentNode)node;
        return ident.isDirectSuper() || "super".equals(ident.getName());
    }

    @Override
    public boolean isThis() {
        return "this".equals(((IdentNode)node).getName());
    }

    @Override
    public boolean isStar() {
        return Module.STAR_NAME.equals(((IdentNode)node).getName());
    }

    @Override
    public boolean isDefault() {
        return Module.DEFAULT_NAME.equals(((IdentNode)node).getName());
    }

    @Override
    public boolean isStarDefaultStar() {
        return Module.DEFAULT_EXPORT_BINDING_NAME.equals(((IdentNode)node).getName());
    }

    @Override
    public <R,D> R accept(final TreeVisitor<R,D> visitor, final D data) {
        return visitor.visitIdentifier(this, data);
    }
}
