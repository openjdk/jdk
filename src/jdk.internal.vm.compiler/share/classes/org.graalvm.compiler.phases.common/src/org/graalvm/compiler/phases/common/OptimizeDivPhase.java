/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.phases.common;

import jdk.internal.vm.compiler.collections.Pair;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.IntegerMulHighNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SignedRemNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.phases.Phase;

import jdk.vm.ci.code.CodeUtil;

public class OptimizeDivPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        for (IntegerDivRemNode rem : graph.getNodes().filter(IntegerDivRemNode.class)) {
            if (rem instanceof SignedRemNode && divByNonZeroConstant(rem)) {
                optimizeRem(rem);
            }
        }
        for (IntegerDivRemNode div : graph.getNodes().filter(IntegerDivRemNode.class)) {
            if (div instanceof SignedDivNode && divByNonZeroConstant(div)) {
                optimizeSignedDiv((SignedDivNode) div);
            }
        }
    }

    @Override
    public float codeSizeIncrease() {
        return 5.0f;
    }

    protected static boolean divByNonZeroConstant(IntegerDivRemNode divRemNode) {
        return divRemNode.getY().isConstant() && divRemNode.getY().asJavaConstant().asLong() != 0;
    }

    protected final void optimizeRem(IntegerDivRemNode rem) {
        assert rem.getOp() == IntegerDivRemNode.Op.REM;
        // Java spec 15.17.3.: (a/b)*b+(a%b) == a
        // so a%b == a-(a/b)*b
        StructuredGraph graph = rem.graph();
        ValueNode div = findDivForRem(rem);
        ValueNode mul = BinaryArithmeticNode.mul(graph, div, rem.getY(), NodeView.DEFAULT);
        ValueNode result = BinaryArithmeticNode.sub(graph, rem.getX(), mul, NodeView.DEFAULT);
        graph.replaceFixedWithFloating(rem, result);
    }

    private ValueNode findDivForRem(IntegerDivRemNode rem) {
        if (rem.next() instanceof IntegerDivRemNode) {
            IntegerDivRemNode div = (IntegerDivRemNode) rem.next();
            if (div.getOp() == IntegerDivRemNode.Op.DIV && div.getType() == rem.getType() && div.getX() == rem.getX() && div.getY() == rem.getY()) {
                return div;
            }
        }
        if (rem.predecessor() instanceof IntegerDivRemNode) {
            IntegerDivRemNode div = (IntegerDivRemNode) rem.predecessor();
            if (div.getOp() == IntegerDivRemNode.Op.DIV && div.getType() == rem.getType() && div.getX() == rem.getX() && div.getY() == rem.getY()) {
                return div;
            }
        }

        // not found, create a new one (will be optimized away later)
        ValueNode div = rem.graph().addOrUniqueWithInputs(createDiv(rem));
        if (div instanceof FixedNode) {
            rem.graph().addAfterFixed(rem, (FixedNode) div);
        }
        return div;
    }

    protected ValueNode createDiv(IntegerDivRemNode rem) {
        assert rem instanceof SignedRemNode;
        return SignedDivNode.create(rem.getX(), rem.getY(), rem.getZeroCheck(), NodeView.DEFAULT);
    }

    protected static void optimizeSignedDiv(SignedDivNode div) {
        ValueNode forX = div.getX();
        long c = div.getY().asJavaConstant().asLong();
        assert c != 1 && c != -1 && c != 0;

        IntegerStamp dividendStamp = (IntegerStamp) forX.stamp(NodeView.DEFAULT);
        int bitSize = dividendStamp.getBits();
        Pair<Long, Integer> nums = magicDivideConstants(c, bitSize);
        long magicNum = nums.getLeft().longValue();
        int shiftNum = nums.getRight().intValue();
        assert shiftNum >= 0;
        ConstantNode m = ConstantNode.forLong(magicNum);

        ValueNode value;
        if (bitSize == 32) {
            value = new MulNode(new SignExtendNode(forX, 64), m);
            if ((c > 0 && magicNum < 0) || (c < 0 && magicNum > 0)) {
                // Get upper 32-bits of the result
                value = NarrowNode.create(new RightShiftNode(value, ConstantNode.forInt(32)), 32, NodeView.DEFAULT);
                if (c > 0) {
                    value = BinaryArithmeticNode.add(value, forX, NodeView.DEFAULT);
                } else {
                    value = BinaryArithmeticNode.sub(value, forX, NodeView.DEFAULT);
                }
                if (shiftNum > 0) {
                    value = new RightShiftNode(value, ConstantNode.forInt(shiftNum));
                }
            } else {
                value = new RightShiftNode(value, ConstantNode.forInt(32 + shiftNum));
                value = new NarrowNode(value, Integer.SIZE);
            }
        } else {
            assert bitSize == 64;
            value = new IntegerMulHighNode(forX, m);
            if (c > 0 && magicNum < 0) {
                value = BinaryArithmeticNode.add(value, forX, NodeView.DEFAULT);
            } else if (c < 0 && magicNum > 0) {
                value = BinaryArithmeticNode.sub(value, forX, NodeView.DEFAULT);
            }
            if (shiftNum > 0) {
                value = new RightShiftNode(value, ConstantNode.forInt(shiftNum));
            }
        }

        if (c < 0) {
            ConstantNode s = ConstantNode.forInt(bitSize - 1);
            ValueNode sign = UnsignedRightShiftNode.create(value, s, NodeView.DEFAULT);
            value = BinaryArithmeticNode.add(value, sign, NodeView.DEFAULT);
        } else if (dividendStamp.canBeNegative()) {
            ConstantNode s = ConstantNode.forInt(bitSize - 1);
            ValueNode sign = UnsignedRightShiftNode.create(forX, s, NodeView.DEFAULT);
            value = BinaryArithmeticNode.add(value, sign, NodeView.DEFAULT);
        }

        StructuredGraph graph = div.graph();
        graph.replaceFixed(div, graph.addOrUniqueWithInputs(value));
    }

    /**
     * Borrowed from Hacker's Delight by Henry S. Warren, Jr. Figure 10-1.
     */
    private static Pair<Long, Integer> magicDivideConstants(long divisor, int size) {
        final long twoW = 1L << (size - 1);                // 2 ^ (size - 1).
        long t = twoW + (divisor >>> 63);
        long ad = Math.abs(divisor);
        long anc = t - 1 - Long.remainderUnsigned(t, ad);  // Absolute value of nc.
        long q1 = Long.divideUnsigned(twoW, anc);          // Init. q1 = 2**p/|nc|.
        long r1 = Long.remainderUnsigned(twoW, anc);       // Init. r1 = rem(2**p, |nc|).
        long q2 = Long.divideUnsigned(twoW, ad);           // Init. q2 = 2**p/|d|.
        long r2 = Long.remainderUnsigned(twoW, ad);        // Init. r2 = rem(2**p, |d|).
        long delta;

        int p = size - 1;                                  // Init. p.
        do {
            p = p + 1;
            q1 = 2 * q1;                                   // Update q1 = 2**p/|nc|.
            r1 = 2 * r1;                                   // Update r1 = rem(2**p, |nc|).
            if (Long.compareUnsigned(r1, anc) >= 0) {      // Must be an unsigned comparison.
                q1 = q1 + 1;
                r1 = r1 - anc;
            }
            q2 = 2 * q2;                                   // Update q2 = 2**p/|d|.
            r2 = 2 * r2;                                   // Update r2 = rem(2**p, |d|).
            if (Long.compareUnsigned(r2, ad) >= 0) {       // Must be an unsigned comparison.
                q2 = q2 + 1;
                r2 = r2 - ad;
            }
            delta = ad - r2;
        } while (Long.compareUnsigned(q1, delta) < 0 || (q1 == delta && r1 == 0));

        long magic = CodeUtil.signExtend(q2 + 1, size);
        if (divisor < 0) {
            magic = -magic;
        }
        return Pair.create(magic, p - size);
    }

}
