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
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Add;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable.BinaryCommutative;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

@NodeInfo(shortName = "+")
public class AddNode extends BinaryArithmeticNode<Add> implements NarrowableArithmeticNode, BinaryCommutative<ValueNode> {

    public static final NodeClass<AddNode> TYPE = NodeClass.create(AddNode.class);

    public AddNode(ValueNode x, ValueNode y) {
        this(TYPE, x, y);
    }

    protected AddNode(NodeClass<? extends AddNode> c, ValueNode x, ValueNode y) {
        super(c, ArithmeticOpTable::getAdd, x, y);
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        BinaryOp<Add> op = ArithmeticOpTable.forStamp(x.stamp(view)).getAdd();
        Stamp stamp = op.foldStamp(x.stamp(view), y.stamp(view));
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp, view);
        if (tryConstantFold != null) {
            return tryConstantFold;
        }
        if (x.isConstant() && !y.isConstant()) {
            return canonical(null, op, y, x, view);
        } else {
            return canonical(null, op, x, y, view);
        }
    }

    private static ValueNode canonical(AddNode addNode, BinaryOp<Add> op, ValueNode forX, ValueNode forY, NodeView view) {
        AddNode self = addNode;
        boolean associative = op.isAssociative();
        if (associative) {
            if (forX instanceof SubNode) {
                SubNode sub = (SubNode) forX;
                if (sub.getY() == forY) {
                    // (a - b) + b
                    return sub.getX();
                }
            }
            if (forY instanceof SubNode) {
                SubNode sub = (SubNode) forY;
                if (sub.getY() == forX) {
                    // b + (a - b)
                    return sub.getX();
                }
            }
        }
        if (forY.isConstant()) {
            Constant c = forY.asConstant();
            if (op.isNeutral(c)) {
                return forX;
            }
            if (associative && self != null) {
                // canonicalize expressions like "(a + 1) + 2"
                ValueNode reassociated = reassociate(self, ValueNode.isConstantPredicate(), forX, forY, view);
                if (reassociated != self) {
                    return reassociated;
                }
            }

            // Attempt to optimize the pattern of an extend node between two add nodes.
            if (c instanceof JavaConstant && (forX instanceof SignExtendNode || forX instanceof ZeroExtendNode)) {
                IntegerConvertNode<?, ?> integerConvertNode = (IntegerConvertNode<?, ?>) forX;
                ValueNode valueNode = integerConvertNode.getValue();
                long constant = ((JavaConstant) c).asLong();
                if (valueNode instanceof AddNode) {
                    AddNode addBeforeExtend = (AddNode) valueNode;
                    if (addBeforeExtend.getY().isConstant()) {
                        // There is a second add before the extend node that also has a constant as
                        // second operand. Therefore there will be canonicalizations triggered if we
                        // can move the add above the extension. For this we need to check whether
                        // the result of the addition is the same before the extension (which can be
                        // either zero extend or sign extend).
                        IntegerStamp beforeExtendStamp = (IntegerStamp) addBeforeExtend.stamp(view);
                        int bits = beforeExtendStamp.getBits();
                        if (constant >= CodeUtil.minValue(bits) && constant <= CodeUtil.maxValue(bits)) {
                            IntegerStamp narrowConstantStamp = IntegerStamp.create(bits, constant, constant);

                            if (!IntegerStamp.addCanOverflow(narrowConstantStamp, beforeExtendStamp)) {
                                ConstantNode constantNode = ConstantNode.forIntegerStamp(narrowConstantStamp, constant);
                                if (forX instanceof SignExtendNode) {
                                    return SignExtendNode.create(AddNode.create(addBeforeExtend, constantNode, view), integerConvertNode.getResultBits(), view);
                                } else {
                                    assert forX instanceof ZeroExtendNode;

                                    // Must check to not cross zero with the new add.
                                    boolean crossesZeroPoint = true;
                                    if (constant > 0) {
                                        if (beforeExtendStamp.lowerBound() >= 0 || beforeExtendStamp.upperBound() < -constant) {
                                            // We are good here.
                                            crossesZeroPoint = false;
                                        }
                                    } else {
                                        if (beforeExtendStamp.lowerBound() >= -constant || beforeExtendStamp.upperBound() < 0) {
                                            // We are good here as well.
                                            crossesZeroPoint = false;
                                        }
                                    }
                                    if (!crossesZeroPoint) {
                                        return ZeroExtendNode.create(AddNode.create(addBeforeExtend, constantNode, view), integerConvertNode.getResultBits(), view);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (forX instanceof NegateNode) {
            return BinaryArithmeticNode.sub(forY, ((NegateNode) forX).getValue(), view);
        } else if (forY instanceof NegateNode) {
            return BinaryArithmeticNode.sub(forX, ((NegateNode) forY).getValue(), view);
        }
        if (self == null) {
            self = (AddNode) new AddNode(forX, forY).maybeCommuteInputs();
        }
        return self;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        if (forX.isConstant() && !forY.isConstant()) {
            // we try to swap and canonicalize
            ValueNode improvement = canonical(tool, forY, forX);
            if (improvement != this) {
                return improvement;
            }
            // if this fails we only swap
            return new AddNode(forY, forX);
        }
        BinaryOp<Add> op = getOp(forX, forY);
        NodeView view = NodeView.from(tool);
        return canonical(this, op, forX, forY, view);
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        Value op1 = nodeValueMap.operand(getX());
        assert op1 != null : getX() + ", this=" + this;
        Value op2 = nodeValueMap.operand(getY());
        if (shouldSwapInputs(nodeValueMap)) {
            Value tmp = op1;
            op1 = op2;
            op2 = tmp;
        }
        nodeValueMap.setResult(this, gen.emitAdd(op1, op2, false));
    }
}
