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

import jdk.nashorn.internal.ir.PropertyNode;

final class PropertyTreeImpl extends TreeImpl implements PropertyTree  {
    private final ExpressionTree key;
    private final ExpressionTree value;
    private final FunctionExpressionTree getter;
    private final FunctionExpressionTree setter;
    private final boolean isStatic, isComputed;

    PropertyTreeImpl(final PropertyNode node,
            final ExpressionTree key,
            final ExpressionTree value,
            final FunctionExpressionTree getter,
            final FunctionExpressionTree setter) {
        super(node);
        this.key    = key;
        this.value  = value;
        this.getter = getter;
        this.setter = setter;
        this.isStatic = node.isStatic();
        this.isComputed = node.isComputed();
    }

    @Override
    public Kind getKind() {
        return Kind.PROPERTY;
    }

    @Override
    public ExpressionTree getKey() {
        return key;
    }

    @Override
    public ExpressionTree getValue() {
        return value;
    }

    @Override
    public FunctionExpressionTree getGetter() {
        return getter;
    }

    @Override
    public FunctionExpressionTree getSetter() {
        return setter;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }

    @Override
    public boolean isComputed() {
        return isComputed;
    }

    @Override
    public <R,D> R accept(final TreeVisitor<R,D> visitor, final D data) {
        return visitor.visitProperty(this, data);
    }
}
