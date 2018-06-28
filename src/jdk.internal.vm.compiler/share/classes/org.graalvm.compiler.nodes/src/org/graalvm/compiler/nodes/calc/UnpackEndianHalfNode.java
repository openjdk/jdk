/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteOrder;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.JavaKind;

/**
 * Produces the platform dependent first or second half of a long or double value as an int.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_2)
public final class UnpackEndianHalfNode extends UnaryNode implements Lowerable {
    public static final NodeClass<UnpackEndianHalfNode> TYPE = NodeClass.create(UnpackEndianHalfNode.class);

    private final boolean firstHalf;

    protected UnpackEndianHalfNode(ValueNode value, boolean firstHalf) {
        super(TYPE, StampFactory.forKind(JavaKind.Int), value);
        assert value.getStackKind() == JavaKind.Double || value.getStackKind() == JavaKind.Long : "unexpected kind " + value.getStackKind();
        this.firstHalf = firstHalf;
    }

    @SuppressWarnings("unused")
    public static ValueNode create(ValueNode value, boolean firstHalf, NodeView view) {
        if (value.isConstant() && value.asConstant().isDefaultForKind()) {
            return ConstantNode.defaultForKind(JavaKind.Int);
        }
        return new UnpackEndianHalfNode(value, firstHalf);
    }

    public boolean isFirstHalf() {
        return firstHalf;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant() && forValue.asConstant().isDefaultForKind()) {
            return ConstantNode.defaultForKind(stamp.getStackKind());
        }
        return this;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public void lower(ByteOrder byteOrder) {
        ValueNode result = value;
        if (value.getStackKind() == JavaKind.Double) {
            result = graph().unique(new ReinterpretNode(JavaKind.Long, value));
        }
        if ((byteOrder == ByteOrder.BIG_ENDIAN) == firstHalf) {
            result = graph().unique(new UnsignedRightShiftNode(result, ConstantNode.forInt(32, graph())));
        }
        result = IntegerConvertNode.convert(result, StampFactory.forKind(JavaKind.Int), graph(), NodeView.DEFAULT);
        replaceAtUsagesAndDelete(result);
    }
}
