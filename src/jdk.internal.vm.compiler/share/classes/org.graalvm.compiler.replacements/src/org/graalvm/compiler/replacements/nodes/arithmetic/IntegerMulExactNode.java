/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_4;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.extended.GuardedNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Node representing an exact integer multiplication that will throw an {@link ArithmeticException}
 * in case the addition would overflow the 32 bit range.
 */
@NodeInfo(cycles = CYCLES_4, cyclesRationale = "mul+cmp", size = SIZE_2)
public final class IntegerMulExactNode extends MulNode implements GuardedNode, IntegerExactArithmeticNode {
    public static final NodeClass<IntegerMulExactNode> TYPE = NodeClass.create(IntegerMulExactNode.class);

    @Input(InputType.Guard) protected GuardingNode guard;

    public IntegerMulExactNode(ValueNode x, ValueNode y, GuardingNode guard) {
        super(TYPE, x, y);
        setStamp(x.stamp(NodeView.DEFAULT).unrestricted());
        assert x.stamp(NodeView.DEFAULT).isCompatible(y.stamp(NodeView.DEFAULT)) && x.stamp(NodeView.DEFAULT) instanceof IntegerStamp;
        this.guard = guard;
    }

    @Override
    public boolean inferStamp() {
        /*
         * Note: it is not allowed to use the foldStamp method of the regular mul node as we do not
         * know the result stamp of this node if we do not know whether we may deopt. If we know we
         * can never overflow we will replace this node with its non overflow checking counterpart
         * anyway.
         */
        return false;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (forX.isConstant() && !forY.isConstant()) {
            return new IntegerMulExactNode(forY, forX, guard).canonical(tool);
        }
        if (forX.isConstant() && forY.isConstant()) {
            return canonicalXYconstant(forX, forY);
        } else if (forY.isConstant()) {
            long c = forY.asJavaConstant().asLong();
            if (c == 1) {
                return forX;
            }
            if (c == 0) {
                return ConstantNode.forIntegerStamp(stamp(NodeView.DEFAULT), 0);
            }
        }
        if (!IntegerStamp.multiplicationCanOverflow((IntegerStamp) x.stamp(NodeView.DEFAULT), (IntegerStamp) y.stamp(NodeView.DEFAULT))) {
            return new MulNode(x, y).canonical(tool);
        }
        return this;
    }

    private ValueNode canonicalXYconstant(ValueNode forX, ValueNode forY) {
        JavaConstant xConst = forX.asJavaConstant();
        JavaConstant yConst = forY.asJavaConstant();
        assert xConst.getJavaKind() == yConst.getJavaKind();
        try {
            if (xConst.getJavaKind() == JavaKind.Int) {
                return ConstantNode.forInt(Math.multiplyExact(xConst.asInt(), yConst.asInt()));
            } else {
                assert xConst.getJavaKind() == JavaKind.Long;
                return ConstantNode.forLong(Math.multiplyExact(xConst.asLong(), yConst.asLong()));
            }
        } catch (ArithmeticException ex) {
            // The operation will result in an overflow exception, so do not canonicalize.
        }
        return this;
    }

    @Override
    public GuardingNode getGuard() {
        return guard;
    }

    @Override
    public void setGuard(GuardingNode guard) {
        updateUsagesInterface(this.guard, guard);
        this.guard = guard;
    }
}
