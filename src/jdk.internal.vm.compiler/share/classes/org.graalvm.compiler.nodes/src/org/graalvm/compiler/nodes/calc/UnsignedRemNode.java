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

@NodeInfo(shortName = "|%|")
public class UnsignedRemNode extends IntegerDivRemNode implements LIRLowerable {

    public static final NodeClass<UnsignedRemNode> TYPE = NodeClass.create(UnsignedRemNode.class);

    public UnsignedRemNode(ValueNode x, ValueNode y, GuardingNode zeroCheck) {
        this(TYPE, x, y, zeroCheck);
    }

    protected UnsignedRemNode(NodeClass<? extends UnsignedRemNode> c, ValueNode x, ValueNode y, GuardingNode zeroCheck) {
        super(c, x.stamp(NodeView.DEFAULT).unrestricted(), Op.REM, Type.UNSIGNED, x, y, zeroCheck);
    }

    public static ValueNode create(ValueNode x, ValueNode y, GuardingNode zeroCheck, NodeView view) {
        Stamp stamp = x.stamp(view).unrestricted();
        return canonical(null, x, y, zeroCheck, stamp, view);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        return canonical(this, forX, forY, getZeroCheck(), stamp(view), view);
    }

    @SuppressWarnings("unused")
    public static ValueNode canonical(UnsignedRemNode self, ValueNode forX, ValueNode forY, GuardingNode zeroCheck, Stamp stamp, NodeView view) {
        int bits = ((IntegerStamp) stamp).getBits();
        if (forX.isConstant() && forY.isConstant()) {
            long yConst = CodeUtil.zeroExtend(forY.asJavaConstant().asLong(), bits);
            if (yConst == 0) {
                /* This will trap, cannot canonicalize. */
                return self != null ? self : new UnsignedRemNode(forX, forY, zeroCheck);
            }
            return ConstantNode.forIntegerStamp(stamp, Long.remainderUnsigned(CodeUtil.zeroExtend(forX.asJavaConstant().asLong(), bits), yConst));
        } else if (forY.isConstant()) {
            long c = CodeUtil.zeroExtend(forY.asJavaConstant().asLong(), bits);
            if (c == 1) {
                return ConstantNode.forIntegerStamp(stamp, 0);
            } else if (CodeUtil.isPowerOf2(c)) {
                return new AndNode(forX, ConstantNode.forIntegerStamp(stamp, c - 1));
            }
        }
        return self != null ? self : new UnsignedRemNode(forX, forY, zeroCheck);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitURem(gen.operand(getX()), gen.operand(getY()), gen.state(this)));
    }
}
