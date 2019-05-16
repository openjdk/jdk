/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.nodes.calc;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;

/**
 * The {@code TernaryNode} class is the base of arithmetic and logic operations with three inputs.
 */
@NodeInfo
public abstract class TernaryNode extends FloatingNode implements Canonicalizable.Ternary<ValueNode> {

    public static final NodeClass<TernaryNode> TYPE = NodeClass.create(TernaryNode.class);
    @Input protected ValueNode x;
    @Input protected ValueNode y;
    @Input protected ValueNode z;

    @Override
    public ValueNode getX() {
        return x;
    }

    @Override
    public ValueNode getY() {
        return y;
    }

    @Override
    public ValueNode getZ() {
        return z;
    }

    public void setX(ValueNode x) {
        updateUsages(this.x, x);
        this.x = x;
    }

    public void setY(ValueNode y) {
        updateUsages(this.y, y);
        this.y = y;
    }

    public void setZ(ValueNode z) {
        updateUsages(this.z, z);
        this.z = z;
    }

    /**
     * Creates a new TernaryNode instance.
     *
     * @param stamp the result type of this instruction
     * @param x the first input instruction
     * @param y the second input instruction
     * @param z the second input instruction
     */
    protected TernaryNode(NodeClass<? extends TernaryNode> c, Stamp stamp, ValueNode x, ValueNode y, ValueNode z) {
        super(c, stamp);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(foldStamp(getX().stamp(NodeView.DEFAULT), getY().stamp(NodeView.DEFAULT), getZ().stamp(NodeView.DEFAULT)));
    }

    /**
     * Compute an improved stamp for this node using the passed in stamps. The stamps must be
     * compatible with the current values of {@link #x}, {@link #y} and {@link #z}. This code is
     * used to provide the default implementation of {@link #inferStamp()} and may be used by
     * external optimizations.
     *
     * @param stampX
     * @param stampY
     * @param stampZ
     */
    public abstract Stamp foldStamp(Stamp stampX, Stamp stampY, Stamp stampZ);
}
