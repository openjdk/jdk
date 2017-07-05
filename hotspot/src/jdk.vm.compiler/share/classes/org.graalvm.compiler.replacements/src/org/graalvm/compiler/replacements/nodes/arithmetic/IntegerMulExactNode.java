/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Node representing an exact integer multiplication that will throw an {@link ArithmeticException}
 * in case the addition would overflow the 32 bit range.
 */
@NodeInfo(cycles = CYCLES_4, cyclesRationale = "mul+cmp", size = SIZE_2)
public final class IntegerMulExactNode extends MulNode implements IntegerExactArithmeticNode {
    public static final NodeClass<IntegerMulExactNode> TYPE = NodeClass.create(IntegerMulExactNode.class);

    public IntegerMulExactNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y);
        setStamp(x.stamp().unrestricted());
        assert x.stamp().isCompatible(y.stamp()) && x.stamp() instanceof IntegerStamp;
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
            return new IntegerMulExactNode(forY, forX);
        }
        if (forX.isConstant()) {
            return canonicalXconstant(forX, forY);
        } else if (forY.isConstant()) {
            long c = forY.asJavaConstant().asLong();
            if (c == 1) {
                return forX;
            }
            if (c == 0) {
                return ConstantNode.forIntegerStamp(stamp(), 0);
            }
        }
        if (!mayOverFlow((IntegerStamp) x.stamp(), (IntegerStamp) y.stamp())) {
            return new MulNode(x, y).canonical(tool);
        }
        return this;
    }

    private static boolean mayOverFlow(IntegerStamp a, IntegerStamp b) {
        // see IntegerStamp#foldStamp for details
        assert a.getBits() == b.getBits();
        if (a.upMask() == 0) {
            return false;
        } else if (b.upMask() == 0) {
            return false;
        }
        if (a.isUnrestricted()) {
            return true;
        }
        if (b.isUnrestricted()) {
            return true;
        }
        int bits = a.getBits();
        // Checkstyle: stop
        long minN_a = a.lowerBound();
        long maxN_a = Math.min(0, a.upperBound());
        long minP_a = Math.max(0, a.lowerBound());
        long maxP_a = a.upperBound();

        long minN_b = b.lowerBound();
        long maxN_b = Math.min(0, b.upperBound());
        long minP_b = Math.max(0, b.lowerBound());
        long maxP_b = b.upperBound();
        // Checkstyle: resume

        boolean mayOverflow = false;
        if (a.canBePositive()) {
            if (b.canBePositive()) {
                mayOverflow |= IntegerStamp.multiplicationOverflows(maxP_a, maxP_b, bits);
                mayOverflow |= IntegerStamp.multiplicationOverflows(minP_a, minP_b, bits);
            }
            if (b.canBeNegative()) {
                mayOverflow |= IntegerStamp.multiplicationOverflows(minP_a, maxN_b, bits);
                mayOverflow |= IntegerStamp.multiplicationOverflows(maxP_a, minN_b, bits);

            }
        }
        if (a.canBeNegative()) {
            if (b.canBePositive()) {
                mayOverflow |= IntegerStamp.multiplicationOverflows(maxN_a, minP_b, bits);
                mayOverflow |= IntegerStamp.multiplicationOverflows(minN_a, maxP_b, bits);
            }
            if (b.canBeNegative()) {
                mayOverflow |= IntegerStamp.multiplicationOverflows(minN_a, minN_b, bits);
                mayOverflow |= IntegerStamp.multiplicationOverflows(maxN_a, maxN_b, bits);
            }
        }
        return mayOverflow;
    }

    private ValueNode canonicalXconstant(ValueNode forX, ValueNode forY) {
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
    public IntegerExactArithmeticSplitNode createSplit(AbstractBeginNode next, AbstractBeginNode deopt) {
        return graph().add(new IntegerMulExactSplitNode(stamp(), getX(), getY(), next, deopt));
    }

    @Override
    public void lower(LoweringTool tool) {
        IntegerExactArithmeticSplitNode.lower(tool, this);
    }
}
