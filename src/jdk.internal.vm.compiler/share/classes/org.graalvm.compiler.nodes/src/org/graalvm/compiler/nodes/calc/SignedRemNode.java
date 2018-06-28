/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.CodeUtil;

@NodeInfo(shortName = "%")
public class SignedRemNode extends IntegerDivRemNode implements LIRLowerable {

    public static final NodeClass<SignedRemNode> TYPE = NodeClass.create(SignedRemNode.class);

    protected SignedRemNode(ValueNode x, ValueNode y, GuardingNode zeroCheck) {
        this(TYPE, x, y, zeroCheck);
    }

    protected SignedRemNode(NodeClass<? extends SignedRemNode> c, ValueNode x, ValueNode y, GuardingNode zeroCheck) {
        super(c, IntegerStamp.OPS.getRem().foldStamp(x.stamp(NodeView.DEFAULT), y.stamp(NodeView.DEFAULT)), Op.REM, Type.SIGNED, x, y, zeroCheck);
    }

    public static ValueNode create(ValueNode x, ValueNode y, GuardingNode zeroCheck, NodeView view) {
        Stamp stamp = IntegerStamp.OPS.getRem().foldStamp(x.stamp(view), y.stamp(view));
        return canonical(null, x, y, zeroCheck, stamp, view);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(IntegerStamp.OPS.getRem().foldStamp(getX().stamp(NodeView.DEFAULT), getY().stamp(NodeView.DEFAULT)));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        return canonical(this, forX, forY, getZeroCheck(), stamp(view), view);
    }

    private static ValueNode canonical(SignedRemNode self, ValueNode forX, ValueNode forY, GuardingNode zeroCheck, Stamp stamp, NodeView view) {
        if (forX.isConstant() && forY.isConstant()) {
            long y = forY.asJavaConstant().asLong();
            if (y == 0) {
                /* This will trap, cannot canonicalize. */
                return self != null ? self : new SignedRemNode(forX, forY, zeroCheck);
            }
            return ConstantNode.forIntegerStamp(stamp, forX.asJavaConstant().asLong() % y);
        } else if (forY.isConstant() && forX.stamp(view) instanceof IntegerStamp && forY.stamp(view) instanceof IntegerStamp) {
            long constY = forY.asJavaConstant().asLong();
            IntegerStamp xStamp = (IntegerStamp) forX.stamp(view);
            IntegerStamp yStamp = (IntegerStamp) forY.stamp(view);
            if (constY < 0 && constY != CodeUtil.minValue(yStamp.getBits())) {
                Stamp newStamp = IntegerStamp.OPS.getRem().foldStamp(forX.stamp(view), forY.stamp(view));
                return canonical(null, forX, ConstantNode.forIntegerStamp(yStamp, -constY), zeroCheck, newStamp, view);
            }

            if (constY == 1) {
                return ConstantNode.forIntegerStamp(stamp, 0);
            } else if (CodeUtil.isPowerOf2(constY)) {
                if (xStamp.isPositive()) {
                    // x & (y - 1)
                    return new AndNode(forX, ConstantNode.forIntegerStamp(stamp, constY - 1));
                } else if (xStamp.isNegative()) {
                    // -((-x) & (y - 1))
                    return new NegateNode(new AndNode(new NegateNode(forX), ConstantNode.forIntegerStamp(stamp, constY - 1)));
                } else {
                    // x - ((x / y) << log2(y))
                    return SubNode.create(forX, LeftShiftNode.create(SignedDivNode.canonical(forX, constY, view), ConstantNode.forInt(CodeUtil.log2(constY)), view), view);
                }
            }
        }
        return self != null ? self : new SignedRemNode(forX, forY, zeroCheck);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitRem(gen.operand(getX()), gen.operand(getY()), gen.state(this)));
    }
}
