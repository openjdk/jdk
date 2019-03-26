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



package org.graalvm.compiler.replacements.nodes.arithmetic;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;

import java.util.List;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public abstract class IntegerExactOverflowNode extends LogicNode implements Canonicalizable.Binary<ValueNode>, Simplifiable {
    public static final NodeClass<IntegerExactOverflowNode> TYPE = NodeClass.create(IntegerExactOverflowNode.class);
    @Input protected ValueNode x;
    @Input protected ValueNode y;

    public IntegerExactOverflowNode(NodeClass<? extends IntegerExactOverflowNode> c, ValueNode x, ValueNode y) {
        super(c);
        assert x != null && y != null;
        this.x = x;
        this.y = y;
    }

    @Override
    public ValueNode getX() {
        return x;
    }

    @Override
    public ValueNode getY() {
        return y;
    }

    /**
     * Make sure the overflow detection nodes have the same order of inputs as the exact arithmetic
     * nodes.
     *
     * @return the original node or another node with the same inputs, ignoring ordering.
     */
    @SuppressWarnings("deprecation")
    public LogicNode maybeCommuteInputs() {
        assert this instanceof BinaryCommutative;
        if (!y.isConstant() && (x.isConstant() || x.getId() > y.getId())) {
            ValueNode tmp = x;
            x = y;
            y = tmp;
            if (graph() != null) {
                // See if this node already exists
                LogicNode duplicate = graph().findDuplicate(this);
                if (duplicate != null) {
                    return duplicate;
                }
            }
        }
        return this;
    }

    protected abstract IntegerExactArithmeticSplitNode createSplit(Stamp splitStamp, AbstractBeginNode next, AbstractBeginNode overflow);

    protected abstract Class<? extends BinaryNode> getCoupledType();

    @Override
    public void simplify(SimplifierTool tool) {
        // Find all ifs that this node feeds into
        for (IfNode ifNode : usages().filter(IfNode.class).snapshot()) {
            // Replace the if with exact split
            AbstractBeginNode next = ifNode.falseSuccessor();
            AbstractBeginNode overflow = ifNode.trueSuccessor();
            ifNode.clearSuccessors();

            // Try to find corresponding exact nodes that could be combined with the split. They
            // would be directly
            // linked to the BeginNode of the false branch.
            List<? extends BinaryNode> coupledNodes = next.usages().filter(getCoupledType()).filter(n -> {
                BinaryNode exact = (BinaryNode) n;
                return exact.getX() == getX() && exact.getY() == getY();
            }).snapshot();

            Stamp splitStamp = x.stamp(NodeView.DEFAULT).unrestricted();
            if (!coupledNodes.isEmpty()) {
                splitStamp = coupledNodes.iterator().next().stamp(NodeView.DEFAULT);
            }
            IntegerExactArithmeticSplitNode split = graph().add(createSplit(splitStamp, next, overflow));
            ifNode.replaceAndDelete(split);

            coupledNodes.forEach(n -> n.replaceAndDelete(split));
        }
    }
}
