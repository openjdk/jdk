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
import static org.graalvm.compiler.loop.MathUtil.divBefore;
import static org.graalvm.compiler.loop.MathUtil.sub;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
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
import org.graalvm.compiler.nodes.extended.GuardingNode;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;

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

    public ValueNode maxTripCountNode() {
        return maxTripCountNode(false);
    }

    public ValueNode maxTripCountNode(boolean assumePositive) {
        StructuredGraph graph = iv.valueNode().graph();
        Stamp stamp = iv.valueNode().stamp(NodeView.DEFAULT);
        ValueNode range = sub(graph, end, iv.initNode());

        ValueNode oneDirection;
        if (iv.direction() == Direction.Up) {
            oneDirection = ConstantNode.forIntegerStamp(stamp, 1, graph);
        } else {
            assert iv.direction() == Direction.Down;
            oneDirection = ConstantNode.forIntegerStamp(stamp, -1, graph);
        }
        if (oneOff) {
            range = add(graph, range, oneDirection);
        }
        // round-away-from-zero divison: (range + stride -/+ 1) / stride
        ValueNode denominator = range;
        if (!oneDirection.stamp(NodeView.DEFAULT).equals(iv.strideNode().stamp(NodeView.DEFAULT))) {
            ValueNode subedRanged = sub(graph, range, oneDirection);
            denominator = add(graph, subedRanged, iv.strideNode());
        }
        ValueNode div = divBefore(graph, loop.entryPoint(), denominator, iv.strideNode());

        if (assumePositive) {
            return div;
        }
        ConstantNode zero = ConstantNode.forIntegerStamp(stamp, 0, graph);
        return graph.unique(new ConditionalNode(graph.unique(new IntegerLessThanNode(zero, div)), div, zero));
    }

    /**
     * @return true if the loop has constant bounds and the trip count is representable as a
     *         positive integer.
     */
    public boolean isConstantMaxTripCount() {
        /*
         * It's possible that the iteration range is too large to treat this as constant because it
         * will overflow.
         */
        return (hasConstantBounds() && rawConstantMaxTripCount() >= 0);
    }

    /**
     * @return true if the bounds on the iteration space are all constants.
     */
    public boolean hasConstantBounds() {
        return end instanceof ConstantNode && iv.isConstantInit() && iv.isConstantStride();
    }

    public long constantMaxTripCount() {
        assert isConstantMaxTripCount();
        return rawConstantMaxTripCount();
    }

    /**
     * Compute the raw value of the trip count for this loop. Since we don't have unsigned values
     * this may be outside representable positive values.
     */
    protected long rawConstantMaxTripCount() {
        assert iv.direction() != null;
        long off = oneOff ? iv.direction() == Direction.Up ? 1 : -1 : 0;
        long endValue = ((ConstantNode) end).asJavaConstant().asLong();
        try {
            // If no overflow occurs then negative values represent a trip count of 0
            long max = Math.subtractExact(Math.addExact(endValue, off), iv.constantInit()) / iv.constantStride();
            return Math.max(0, max);
        } catch (ArithmeticException e) {
            /*
             * The computation overflowed to return a negative value. It's possible some
             * optimization could handle this value as an unsigned and produce the right answer but
             * we hide this value by default.
             */
            return -1;
        }
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

    public long constantExactTripCount() {
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

    public GuardingNode createOverFlowGuard() {
        GuardingNode overflowGuard = getOverFlowGuard();
        if (overflowGuard != null) {
            return overflowGuard;
        }
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
                        JavaConstant.NULL_POINTER)); // TODO gd: use speculation
        loop.loopBegin().setOverflowGuard(overflowGuard);
        return overflowGuard;
    }

    public IntegerStamp getStamp() {
        return (IntegerStamp) iv.valueNode().stamp(NodeView.DEFAULT);
    }
}
