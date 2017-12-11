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
import static org.graalvm.compiler.loop.MathUtil.mul;
import static org.graalvm.compiler.loop.MathUtil.sub;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.calc.SubNode;

public class BasicInductionVariable extends InductionVariable {

    private final ValuePhiNode phi;
    private final ValueNode init;
    private ValueNode rawStride;
    private BinaryArithmeticNode<?> op;

    public BasicInductionVariable(LoopEx loop, ValuePhiNode phi, ValueNode init, ValueNode rawStride, BinaryArithmeticNode<?> op) {
        super(loop);
        this.phi = phi;
        this.init = init;
        this.rawStride = rawStride;
        this.op = op;
    }

    @Override
    public StructuredGraph graph() {
        return phi.graph();
    }

    public BinaryArithmeticNode<?> getOp() {
        return op;
    }

    public void setOP(BinaryArithmeticNode<?> newOp) {
        rawStride = newOp.getY();
        op = newOp;
    }

    @Override
    public Direction direction() {
        Stamp stamp = rawStride.stamp(NodeView.DEFAULT);
        if (stamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) stamp;
            Direction dir = null;
            if (integerStamp.isStrictlyPositive()) {
                dir = Direction.Up;
            } else if (integerStamp.isStrictlyNegative()) {
                dir = Direction.Down;
            }
            if (dir != null) {
                if (op instanceof AddNode) {
                    return dir;
                } else {
                    assert op instanceof SubNode;
                    return dir.opposite();
                }
            }
        }
        return null;
    }

    @Override
    public ValuePhiNode valueNode() {
        return phi;
    }

    @Override
    public ValueNode initNode() {
        return init;
    }

    @Override
    public ValueNode strideNode() {
        if (op instanceof AddNode) {
            return rawStride;
        }
        if (op instanceof SubNode) {
            return graph().unique(new NegateNode(rawStride));
        }
        throw GraalError.shouldNotReachHere();
    }

    @Override
    public boolean isConstantInit() {
        return init.isConstant();
    }

    @Override
    public boolean isConstantStride() {
        return rawStride.isConstant();
    }

    @Override
    public long constantInit() {
        return init.asJavaConstant().asLong();
    }

    @Override
    public long constantStride() {
        if (op instanceof AddNode) {
            return rawStride.asJavaConstant().asLong();
        }
        if (op instanceof SubNode) {
            return -rawStride.asJavaConstant().asLong();
        }
        throw GraalError.shouldNotReachHere();
    }

    @Override
    public ValueNode extremumNode(boolean assumePositiveTripCount, Stamp stamp) {
        Stamp fromStamp = phi.stamp(NodeView.DEFAULT);
        StructuredGraph graph = graph();
        ValueNode stride = strideNode();
        ValueNode initNode = this.initNode();
        if (!fromStamp.isCompatible(stamp)) {
            stride = IntegerConvertNode.convert(stride, stamp, graph(), NodeView.DEFAULT);
            initNode = IntegerConvertNode.convert(initNode, stamp, graph(), NodeView.DEFAULT);
        }
        ValueNode maxTripCount = loop.counted().maxTripCountNode(assumePositiveTripCount);
        if (!maxTripCount.stamp(NodeView.DEFAULT).isCompatible(stamp)) {
            maxTripCount = IntegerConvertNode.convert(maxTripCount, stamp, graph(), NodeView.DEFAULT);
        }
        return add(graph, mul(graph, stride, sub(graph, maxTripCount, ConstantNode.forIntegerStamp(stamp, 1, graph))), initNode);
    }

    @Override
    public ValueNode exitValueNode() {
        Stamp stamp = phi.stamp(NodeView.DEFAULT);
        ValueNode maxTripCount = loop.counted().maxTripCountNode(false);
        if (!maxTripCount.stamp(NodeView.DEFAULT).isCompatible(stamp)) {
            maxTripCount = IntegerConvertNode.convert(maxTripCount, stamp, graph(), NodeView.DEFAULT);
        }
        return add(graph(), mul(graph(), strideNode(), maxTripCount), initNode());
    }

    @Override
    public boolean isConstantExtremum() {
        return isConstantInit() && isConstantStride() && loop.counted().isConstantMaxTripCount();
    }

    @Override
    public long constantExtremum() {
        return constantStride() * (loop.counted().constantMaxTripCount() - 1) + constantInit();
    }

    @Override
    public void deleteUnusedNodes() {
    }

    @Override
    public String toString() {
        return String.format("BasicInductionVariable %s %s %s %s", initNode(), phi, op.getNodeClass().shortName(), strideNode());
    }
}
