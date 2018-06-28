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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_2;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable.BinaryCommutative;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.BinaryOpLogicNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.TriState;

/**
 * This node will perform a "test" operation on its arguments. Its result is equivalent to the
 * expression "(x &amp; y) == 0", meaning that it will return true if (and only if) no bit is set in
 * both x and y.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_2)
public final class IntegerTestNode extends BinaryOpLogicNode implements BinaryCommutative<ValueNode> {
    public static final NodeClass<IntegerTestNode> TYPE = NodeClass.create(IntegerTestNode.class);

    public IntegerTestNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        if (forX.isConstant() && forY.isConstant()) {
            return LogicConstantNode.forBoolean((forX.asJavaConstant().asLong() & forY.asJavaConstant().asLong()) == 0);
        }
        if (forX.stamp(view) instanceof IntegerStamp && forY.stamp(view) instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) forX.stamp(view);
            IntegerStamp yStamp = (IntegerStamp) forY.stamp(view);
            if ((xStamp.upMask() & yStamp.upMask()) == 0) {
                return LogicConstantNode.tautology();
            } else if ((xStamp.downMask() & yStamp.downMask()) != 0) {
                return LogicConstantNode.contradiction();
            }
        }
        return this;
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated, Stamp xStamp, Stamp yStamp) {
        return getSucceedingStamp(negated, xStamp, yStamp);
    }

    private static Stamp getSucceedingStamp(boolean negated, Stamp xStampGeneric, Stamp otherStampGeneric) {
        if (xStampGeneric instanceof IntegerStamp && otherStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            IntegerStamp otherStamp = (IntegerStamp) otherStampGeneric;
            if (negated) {
                if (Long.bitCount(otherStamp.upMask()) == 1) {
                    long newDownMask = xStamp.downMask() | otherStamp.upMask();
                    if (xStamp.downMask() != newDownMask) {
                        return IntegerStamp.stampForMask(xStamp.getBits(), newDownMask, xStamp.upMask()).join(xStamp);
                    }
                }
            } else {
                long restrictedUpMask = ((~otherStamp.downMask()) & xStamp.upMask());
                if (xStamp.upMask() != restrictedUpMask) {
                    return IntegerStamp.stampForMask(xStamp.getBits(), xStamp.downMask(), restrictedUpMask).join(xStamp);
                }
            }
        }
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated, Stamp xStamp, Stamp yStamp) {
        return getSucceedingStamp(negated, yStamp, xStamp);
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        if (xStampGeneric instanceof IntegerStamp && yStampGeneric instanceof IntegerStamp) {
            IntegerStamp xStamp = (IntegerStamp) xStampGeneric;
            IntegerStamp yStamp = (IntegerStamp) yStampGeneric;
            if ((xStamp.upMask() & yStamp.upMask()) == 0) {
                return TriState.TRUE;
            } else if ((xStamp.downMask() & yStamp.downMask()) != 0) {
                return TriState.FALSE;
            }
        }
        return TriState.UNKNOWN;
    }
}
