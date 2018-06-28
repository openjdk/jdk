/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.ShiftOp.Shl;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(shortName = "<<")
public final class LeftShiftNode extends ShiftNode<Shl> {

    public static final NodeClass<LeftShiftNode> TYPE = NodeClass.create(LeftShiftNode.class);

    public LeftShiftNode(ValueNode x, ValueNode y) {
        super(TYPE, ArithmeticOpTable::getShl, x, y);
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        ArithmeticOpTable.ShiftOp<Shl> op = ArithmeticOpTable.forStamp(x.stamp(view)).getShl();
        Stamp stamp = op.foldStamp(x.stamp(view), (IntegerStamp) y.stamp(view));
        ValueNode value = ShiftNode.canonical(op, stamp, x, y, view);
        if (value != null) {
            return value;
        }

        return canonical(null, op, stamp, x, y);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        return canonical(this, getArithmeticOp(), stamp(NodeView.DEFAULT), forX, forY);
    }

    private static ValueNode canonical(LeftShiftNode leftShiftNode, ArithmeticOpTable.ShiftOp<Shl> op, Stamp stamp, ValueNode forX, ValueNode forY) {
        LeftShiftNode self = leftShiftNode;
        if (forY.isConstant()) {
            int amount = forY.asJavaConstant().asInt();
            int originalAmount = amount;
            int mask = op.getShiftAmountMask(stamp);
            amount &= mask;
            if (amount == 0) {
                return forX;
            }
            if (forX instanceof ShiftNode) {
                ShiftNode<?> other = (ShiftNode<?>) forX;
                if (other.getY().isConstant()) {
                    int otherAmount = other.getY().asJavaConstant().asInt() & mask;
                    if (other instanceof LeftShiftNode) {
                        int total = amount + otherAmount;
                        if (total != (total & mask)) {
                            return ConstantNode.forIntegerKind(stamp.getStackKind(), 0);
                        }
                        return new LeftShiftNode(other.getX(), ConstantNode.forInt(total));
                    } else if ((other instanceof RightShiftNode || other instanceof UnsignedRightShiftNode) && otherAmount == amount) {
                        if (stamp.getStackKind() == JavaKind.Long) {
                            return new AndNode(other.getX(), ConstantNode.forLong(-1L << amount));
                        } else {
                            assert stamp.getStackKind() == JavaKind.Int;
                            return new AndNode(other.getX(), ConstantNode.forInt(-1 << amount));
                        }
                    }
                }
            }
            if (originalAmount != amount) {
                return new LeftShiftNode(forX, ConstantNode.forInt(amount));
            }
        }
        if (self == null) {
            self = new LeftShiftNode(forX, forY);
        }
        return self;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitShl(nodeValueMap.operand(getX()), nodeValueMap.operand(getY())));
    }
}
