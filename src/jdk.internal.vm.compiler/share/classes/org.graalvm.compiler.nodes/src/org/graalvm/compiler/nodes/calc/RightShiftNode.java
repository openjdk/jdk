/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.ShiftOp.Shr;
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

import jdk.vm.ci.code.CodeUtil;

@NodeInfo(shortName = ">>")
public final class RightShiftNode extends ShiftNode<Shr> {

    public static final NodeClass<RightShiftNode> TYPE = NodeClass.create(RightShiftNode.class);

    public RightShiftNode(ValueNode x, ValueNode y) {
        super(TYPE, ArithmeticOpTable::getShr, x, y);
    }

    public static ValueNode create(ValueNode x, int y, NodeView view) {
        if (y == 0) {
            return x;
        }
        return create(x, ConstantNode.forInt(y), view);
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        ArithmeticOpTable.ShiftOp<Shr> op = ArithmeticOpTable.forStamp(x.stamp(view)).getShr();
        Stamp stamp = op.foldStamp(x.stamp(view), (IntegerStamp) y.stamp(view));
        ValueNode value = ShiftNode.canonical(op, stamp, x, y, view);
        if (value != null) {
            return value;
        }

        return canonical(null, op, stamp, x, y, view);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        return canonical(this, getArithmeticOp(), stamp(view), forX, forY, view);
    }

    private static ValueNode canonical(RightShiftNode rightShiftNode, ArithmeticOpTable.ShiftOp<Shr> op, Stamp stamp, ValueNode forX, ValueNode forY, NodeView view) {
        RightShiftNode self = rightShiftNode;
        if (forX.stamp(view) instanceof IntegerStamp && ((IntegerStamp) forX.stamp(view)).isPositive()) {
            return new UnsignedRightShiftNode(forX, forY);
        }

        if (forY.isConstant()) {
            int amount = forY.asJavaConstant().asInt();
            int originalAmout = amount;
            int mask = op.getShiftAmountMask(stamp);
            amount &= mask;
            if (amount == 0) {
                return forX;
            }
            if (forX instanceof ShiftNode) {
                ShiftNode<?> other = (ShiftNode<?>) forX;
                if (other.getY().isConstant()) {
                    int otherAmount = other.getY().asJavaConstant().asInt() & mask;
                    if (other instanceof RightShiftNode) {
                        int total = amount + otherAmount;
                        if (total != (total & mask)) {
                            assert other.getX().stamp(view) instanceof IntegerStamp;
                            IntegerStamp istamp = (IntegerStamp) other.getX().stamp(view);

                            if (istamp.isPositive()) {
                                return ConstantNode.forIntegerKind(stamp.getStackKind(), 0);
                            }
                            if (istamp.isStrictlyNegative()) {
                                return ConstantNode.forIntegerKind(stamp.getStackKind(), -1L);
                            }

                            /*
                             * if we cannot replace both shifts with a constant, replace them by a
                             * full shift for this kind
                             */
                            assert total >= mask;
                            return new RightShiftNode(other.getX(), ConstantNode.forInt(mask));
                        }
                        return new RightShiftNode(other.getX(), ConstantNode.forInt(total));
                    }
                }
            }
            if (originalAmout != amount) {
                return new RightShiftNode(forX, ConstantNode.forInt(amount));
            }
        }
        if (self == null) {
            self = new RightShiftNode(forX, forY);
        }
        return self;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitShr(nodeValueMap.operand(getX()), nodeValueMap.operand(getY())));
    }

    @Override
    public boolean isNarrowable(int resultBits) {
        if (super.isNarrowable(resultBits)) {
            /*
             * For signed right shifts, the narrow can be done before the shift if the cut off bits
             * are all equal to the sign bit of the input. That's equivalent to the condition that
             * the input is in the signed range of the narrow type.
             */
            IntegerStamp inputStamp = (IntegerStamp) getX().stamp(NodeView.DEFAULT);
            return CodeUtil.minValue(resultBits) <= inputStamp.lowerBound() && inputStamp.upperBound() <= CodeUtil.maxValue(resultBits);
        } else {
            return false;
        }
    }
}
