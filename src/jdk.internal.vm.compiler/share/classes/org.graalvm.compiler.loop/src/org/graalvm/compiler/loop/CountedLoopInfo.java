/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.loop;

import static org.graalvm.compiler.loop.MathUtil.add;
import static org.graalvm.compiler.loop.MathUtil.sub;
import static org.graalvm.compiler.loop.MathUtil.unsignedDivBefore;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.util.UnsignedLong;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.loop.InductionVariable.Direction;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.SpeculationLog;

public class CountedLoopInfo {

    private final LoopEx loop;
    private InductionVariable iv;
    private ValueNode end;
    private boolean oneOff;
    private AbstractBeginNode body;
    private IfNode ifNode;

    CountedLoopInfo(LoopEx loop, InductionVariable iv, IfNode ifNode, ValueNode end, boolean oneOff, AbstractBeginNode body) {
        this.loop = loop;
        this.iv = iv;
        this.end = end;
        this.oneOff = oneOff;
        this.body = body;
        this.ifNode = ifNode;
    }

    /**
     * Returns a node that computes the maximum trip count of this loop. That is the trip count of
     * this loop assuming it is not exited by an other exit than the {@linkplain #getLimitTest()
     * count check}.
     *
     * This count is exact if {@link #isExactTripCount()} returns true.
     *
     * THIS VALUE SHOULD BE TREATED AS UNSIGNED.
     */
    public ValueNode maxTripCountNode() {
        return maxTripCountNode(false);
    }

    /**
     * Returns a node that computes the maximum trip count of this loop. That is the trip count of
     * this loop assuming it is not exited by an other exit than the {@linkplain #getLimitTest()
     * count check}.
     *
     * This count is exact if {@link #isExactTripCount()} returns true.
     *
     * THIS VALUE SHOULD BE TREATED AS UNSIGNED.
     *
     * @param assumePositive if true the check that the loop is entered at all will be omitted.
     */
    public ValueNode maxTripCountNode(boolean assumePositive) {
        StructuredGraph graph = iv.valueNode().graph();
        Stamp stamp = iv.valueNode().stamp(NodeView.DEFAULT);

        ValueNode max;
        ValueNode min;
        ValueNode range;
        ValueNode absStride;
        if (iv.direction() == Direction.Up) {
            absStride = iv.strideNode();
            range = sub(graph, end, iv.initNode());
            max = end;
            min = iv.initNode();
        } else {
            assert iv.direction() == Direction.Down;
            absStride = graph.maybeAddOrUnique(NegateNode.create(iv.strideNode(), NodeView.DEFAULT));
            range = sub(graph, iv.initNode(), end);
            max = iv.initNode();
            min = end;
        }

        ConstantNode one = ConstantNode.forIntegerStamp(stamp, 1, graph);
        if (oneOff) {
            range = add(graph, range, one);
        }
        // round-away-from-zero divison: (range + stride -/+ 1) / stride
        ValueNode denominator = add(graph, range, sub(graph, absStride, one));
        ValueNode div = unsignedDivBefore(graph, loop.entryPoint(), denominator, absStride, null);

        if (assumePositive) {
            return div;
        }
        ConstantNode zero = ConstantNode.forIntegerStamp(stamp, 0, graph);
        return graph.unique(new ConditionalNode(graph.unique(new IntegerLessThanNode(max, min)), zero, div));
    }

    /**
     * @return true if the loop has constant bounds.
     */
    public boolean isConstantMaxTripCount() {
        return end instanceof ConstantNode && iv.isConstantInit() && iv.isConstantStride();
    }

    public UnsignedLong constantMaxTripCount() {
        assert isConstantMaxTripCount();
        return new UnsignedLong(rawConstantMaxTripCount());
    }

    /**
     * Compute the raw value of the trip count for this loop. THIS IS AN UNSIGNED VALUE;
     */
    private long rawConstantMaxTripCount() {
        assert iv.direction() != null;
        long endValue = end.asJavaConstant().asLong();
        long initValue = iv.constantInit();
        long range;
        long absStride;
        if (iv.direction() == Direction.Up) {
            if (endValue < initValue) {
                return 0;
            }
            range = endValue - iv.constantInit();
            absStride = iv.constantStride();
        } else {
            if (initValue < endValue) {
                return 0;
            }
            range = iv.constantInit() - endValue;
            absStride = -iv.constantStride();
        }
        if (oneOff) {
            range += 1;
        }
        long denominator = range + absStride - 1;
        return Long.divideUnsigned(denominator, absStride);
    }

    public boolean isExactTripCount() {
        return loop.loopBegin().loopExits().count() == 1;
    }

    public ValueNode exactTripCountNode() {
        assert isExactTripCount();
        return maxTripCountNode();
    }

    public boolean isConstantExactTripCount() {
        assert isExactTripCount();
        return isConstantMaxTripCount();
    }

    public UnsignedLong constantExactTripCount() {
        assert isExactTripCount();
        return constantMaxTripCount();
    }

    @Override
    public String toString() {
        return "iv=" + iv + " until " + end + (oneOff ? iv.direction() == Direction.Up ? "+1" : "-1" : "");
    }

    public ValueNode getLimit() {
        return end;
    }

    public IfNode getLimitTest() {
        return ifNode;
    }

    public ValueNode getStart() {
        return iv.initNode();
    }

    public boolean isLimitIncluded() {
        return oneOff;
    }

    public AbstractBeginNode getBody() {
        return body;
    }

    public Direction getDirection() {
        return iv.direction();
    }

    public InductionVariable getCounter() {
        return iv;
    }

    public GuardingNode getOverFlowGuard() {
        return loop.loopBegin().getOverflowGuard();
    }

    @SuppressWarnings("try")
    public GuardingNode createOverFlowGuard() {
        GuardingNode overflowGuard = getOverFlowGuard();
        if (overflowGuard != null) {
            return overflowGuard;
        }
        try (DebugCloseable position = loop.loopBegin().withNodeSourcePosition()) {
            IntegerStamp stamp = (IntegerStamp) iv.valueNode().stamp(NodeView.DEFAULT);
            StructuredGraph graph = iv.valueNode().graph();
            CompareNode cond; // we use a negated guard with a < condition to achieve a >=
            ConstantNode one = ConstantNode.forIntegerStamp(stamp, 1, graph);
            if (iv.direction() == Direction.Up) {
                ValueNode v1 = sub(graph, ConstantNode.forIntegerStamp(stamp, CodeUtil.maxValue(stamp.getBits()), graph), sub(graph, iv.strideNode(), one));
                if (oneOff) {
                    v1 = sub(graph, v1, one);
                }
                cond = graph.unique(new IntegerLessThanNode(v1, end));
            } else {
                assert iv.direction() == Direction.Down;
                ValueNode v1 = add(graph, ConstantNode.forIntegerStamp(stamp, CodeUtil.minValue(stamp.getBits()), graph), sub(graph, one, iv.strideNode()));
                if (oneOff) {
                    v1 = add(graph, v1, one);
                }
                cond = graph.unique(new IntegerLessThanNode(end, v1));
            }
            assert graph.getGuardsStage().allowsFloatingGuards();
            overflowGuard = graph.unique(new GuardNode(cond, AbstractBeginNode.prevBegin(loop.entryPoint()), DeoptimizationReason.LoopLimitCheck, DeoptimizationAction.InvalidateRecompile, true,
                            SpeculationLog.NO_SPECULATION, null)); // TODO gd: use speculation
            loop.loopBegin().setOverflowGuard(overflowGuard);
            return overflowGuard;
        }
    }

    public IntegerStamp getStamp() {
        return (IntegerStamp) iv.valueNode().stamp(NodeView.DEFAULT);
    }
}
