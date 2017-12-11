/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;

/**
 * The {@code UnaryNode} class is the base of arithmetic and bit logic operations with exactly one
 * input.
 */
@NodeInfo(size = SIZE_1)
public abstract class UnaryNode extends FloatingNode implements Canonicalizable.Unary<ValueNode> {

    public static final NodeClass<UnaryNode> TYPE = NodeClass.create(UnaryNode.class);
    @Input protected ValueNode value;

    @Override
    public ValueNode getValue() {
        return value;
    }

    public void setValue(ValueNode value) {
        updateUsages(this.value, value);
        this.value = value;
    }

    /**
     * Creates a new UnaryNode instance.
     *
     * @param stamp the result type of this instruction
     * @param value the input instruction
     */
    protected UnaryNode(NodeClass<? extends UnaryNode> c, Stamp stamp, ValueNode value) {
        super(c, stamp);
        this.value = value;
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(foldStamp(value.stamp(NodeView.DEFAULT)));
    }

    /**
     * Compute an improved for this node using the passed in stamp. The stamp must be compatible
     * with the current value of {@link #value}. This code is used to provide the default
     * implementation of {@link #inferStamp()} and may be used by external optimizations.
     *
     * @param newStamp
     */
    public Stamp foldStamp(Stamp newStamp) {
        return stamp(NodeView.DEFAULT);
    }
}
