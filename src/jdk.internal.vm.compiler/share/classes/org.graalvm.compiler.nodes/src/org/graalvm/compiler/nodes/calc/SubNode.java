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
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Sub;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.PrimitiveConstant;

@NodeInfo(shortName = "-")
public class SubNode extends BinaryArithmeticNode<Sub> implements NarrowableArithmeticNode {

    public static final NodeClass<SubNode> TYPE = NodeClass.create(SubNode.class);

    public SubNode(ValueNode x, ValueNode y) {
        this(TYPE, x, y);
    }

    protected SubNode(NodeClass<? extends SubNode> c, ValueNode x, ValueNode y) {
        super(c, ArithmeticOpTable::getSub, x, y);
    }

    public static ValueNode create(ValueNode x, ValueNode y, NodeView view) {
        BinaryOp<Sub> op = ArithmeticOpTable.forStamp(x.stamp(view)).getSub();
        Stamp stamp = op.foldStamp(x.stamp(view), y.stamp(view));
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, stamp, view);
        if (tryConstantFold != null) {
            return tryConstantFold;
        }
        return canonical(null, op, stamp, x, y, view);
    }

    private static ValueNode canonical(SubNode subNode, BinaryOp<Sub> op, Stamp stamp, ValueNode forX, ValueNode forY, NodeView view) {
        SubNode self = subNode;
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            Constant zero = op.getZero(forX.stamp(view));
            if (zero != null) {
                return ConstantNode.forPrimitive(stamp, zero);
            }
        }
        boolean associative = op.isAssociative();
        if (associative) {
            if (forX instanceof AddNode) {
                AddNode x = (AddNode) forX;
                if (x.getY() == forY) {
                    // (a + b) - b
                    return x.getX();
                }
                if (x.getX() == forY) {
                    // (a + b) - a
                    return x.getY();
                }
            } else if (forX instanceof SubNode) {
                SubNode x = (SubNode) forX;
                if (x.getX() == forY) {
                    // (a - b) - a
                    return NegateNode.create(x.getY(), view);
                }
            }
            if (forY instanceof AddNode) {
                AddNode y = (AddNode) forY;
                if (y.getX() == forX) {
                    // a - (a + b)
                    return NegateNode.create(y.getY(), view);
                }
                if (y.getY() == forX) {
                    // b - (a + b)
                    return NegateNode.create(y.getX(), view);
                }
            } else if (forY instanceof SubNode) {
                SubNode y = (SubNode) forY;
                if (y.getX() == forX) {
                    // a - (a - b)
                    return y.getY();
                }
            }
        }
        if (forY.isConstant()) {
            Constant c = forY.asConstant();
            if (op.isNeutral(c)) {
                return forX;
            }
            if (associative && self != null) {
                ValueNode reassociated = reassociate(self, ValueNode.isConstantPredicate(), forX, forY, view);
                if (reassociated != self) {
                    return reassociated;
                }
            }
            if (c instanceof PrimitiveConstant && ((PrimitiveConstant) c).getJavaKind().isNumericInteger()) {
                long i = ((PrimitiveConstant) c).asLong();
                if (i < 0 || ((IntegerStamp) StampFactory.forKind(forY.getStackKind())).contains(-i)) {
                    // Adding a negative is more friendly to the backend since adds are
                    // commutative, so prefer add when it fits.
                    return BinaryArithmeticNode.add(forX, ConstantNode.forIntegerStamp(stamp, -i), view);
                }
            }
        } else if (forX.isConstant()) {
            Constant c = forX.asConstant();
            if (ArithmeticOpTable.forStamp(stamp).getAdd().isNeutral(c)) {
                /*
                 * Note that for floating point numbers, + and - have different neutral elements. We
                 * have to test for the neutral element of +, because we are doing this
                 * transformation: 0 - x == (-x) + 0 == -x.
                 */
                return NegateNode.create(forY, view);
            }
            if (associative && self != null) {
                return reassociate(self, ValueNode.isConstantPredicate(), forX, forY, view);
            }
        }
        if (forY instanceof NegateNode) {
            return BinaryArithmeticNode.add(forX, ((NegateNode) forY).getValue(), view);
        }
        return self != null ? self : new SubNode(forX, forY);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode ret = super.canonical(tool, forX, forY);
        if (ret != this) {
            return ret;
        }

        BinaryOp<Sub> op = getOp(forX, forY);
        return canonical(this, op, stamp, forX, forY, view);
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitSub(nodeValueMap.operand(getX()), nodeValueMap.operand(getY()), false));
    }
}
