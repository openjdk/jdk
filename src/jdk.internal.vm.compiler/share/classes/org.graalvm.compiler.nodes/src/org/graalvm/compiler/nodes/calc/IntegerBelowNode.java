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

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LogicNegationNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.TriState;

@NodeInfo(shortName = "|<|")
public final class IntegerBelowNode extends IntegerLowerThanNode {
    public static final NodeClass<IntegerBelowNode> TYPE = NodeClass.create(IntegerBelowNode.class);
    private static final BelowOp OP = new BelowOp();

    public IntegerBelowNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y, OP);
        assert x.stamp(NodeView.DEFAULT) instanceof IntegerStamp;
        assert y.stamp(NodeView.DEFAULT) instanceof IntegerStamp;
    }

    public static LogicNode create(ValueNode x, ValueNode y, NodeView view) {
        return OP.create(x, y, view);
    }

    public static LogicNode create(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth, ValueNode x, ValueNode y,
                    NodeView view) {
        LogicNode value = OP.canonical(constantReflection, metaAccess, options, smallestCompareWidth, OP.getCondition(), false, x, y, view);
        if (value != null) {
            return value;
        }
        return create(x, y, view);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode value = OP.canonical(tool.getConstantReflection(), tool.getMetaAccess(), tool.getOptions(), tool.smallestCompareWidth(), OP.getCondition(), false, forX, forY, view);
        if (value != null) {
            return value;
        }
        return this;
    }

    public static class BelowOp extends LowerOp {
        @Override
        protected CompareNode duplicateModified(ValueNode newX, ValueNode newY, boolean unorderedIsTrue, NodeView view) {
            assert newX.stamp(NodeView.DEFAULT) instanceof IntegerStamp && newY.stamp(NodeView.DEFAULT) instanceof IntegerStamp;
            return new IntegerBelowNode(newX, newY);
        }

        @Override
        protected LogicNode findSynonym(ValueNode forX, ValueNode forY, NodeView view) {
            LogicNode result = super.findSynonym(forX, forY, view);
            if (result != null) {
                return result;
            }
            if (forX.stamp(view) instanceof IntegerStamp) {
                assert forY.stamp(view) instanceof IntegerStamp;
                int bits = ((IntegerStamp) forX.stamp(view)).getBits();
                assert ((IntegerStamp) forY.stamp(view)).getBits() == bits;
                LogicNode logic = canonicalizeRangeFlip(forX, forY, bits, false, view);
                if (logic != null) {
                    return logic;
                }
            }
            return null;
        }

        @Override
        protected long upperBound(IntegerStamp stamp) {
            return stamp.unsignedUpperBound();
        }

        @Override
        protected long lowerBound(IntegerStamp stamp) {
            return stamp.unsignedLowerBound();
        }

        @Override
        protected int compare(long a, long b) {
            return Long.compareUnsigned(a, b);
        }

        @Override
        protected long min(long a, long b) {
            return NumUtil.minUnsigned(a, b);
        }

        @Override
        protected long max(long a, long b) {
            return NumUtil.maxUnsigned(a, b);
        }

        @Override
        protected long cast(long a, int bits) {
            return CodeUtil.zeroExtend(a, bits);
        }

        @Override
        protected long minValue(int bits) {
            return 0;
        }

        @Override
        protected long maxValue(int bits) {
            return NumUtil.maxValueUnsigned(bits);
        }

        @Override
        protected IntegerStamp forInteger(int bits, long min, long max) {
            return StampFactory.forUnsignedInteger(bits, min, max);
        }

        @Override
        protected CanonicalCondition getCondition() {
            return CanonicalCondition.BT;
        }

        @Override
        protected IntegerLowerThanNode createNode(ValueNode x, ValueNode y) {
            return new IntegerBelowNode(x, y);
        }
    }

    @Override
    public TriState implies(boolean thisNegated, LogicNode other) {
        if (other instanceof LogicNegationNode) {
            // Unwrap negations.
            TriState result = implies(thisNegated, ((LogicNegationNode) other).getValue());
            if (result.isKnown()) {
                return TriState.get(!result.toBoolean());
            }
        }
        if (!thisNegated) {
            if (other instanceof IntegerLessThanNode) {
                IntegerLessThanNode integerLessThanNode = (IntegerLessThanNode) other;
                IntegerStamp stampL = (IntegerStamp) this.getY().stamp(NodeView.DEFAULT);
                // if L >= 0:
                if (stampL.isPositive()) { // L >= 0
                    if (this.getX() == integerLessThanNode.getX()) {
                        // x |<| L implies x < L
                        if (this.getY() == integerLessThanNode.getY()) {
                            return TriState.TRUE;
                        }
                        // x |<| L implies !(x < 0)
                        if (integerLessThanNode.getY().isConstant() &&
                                        IntegerStamp.OPS.getAdd().isNeutral(integerLessThanNode.getY().asConstant())) {
                            return TriState.FALSE;
                        }
                    }
                }
            }
        }
        return super.implies(thisNegated, other);
    }
}
