/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.loop.MathUtil.mul;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.util.GraphUtil;

public class DerivedScaledInductionVariable extends DerivedInductionVariable {

    private final ValueNode scale;
    private final ValueNode value;

    public DerivedScaledInductionVariable(LoopEx loop, InductionVariable base, ValueNode scale, ValueNode value) {
        super(loop, base);
        this.scale = scale;
        this.value = value;
    }

    public DerivedScaledInductionVariable(LoopEx loop, InductionVariable base, NegateNode value) {
        super(loop, base);
        this.scale = ConstantNode.forIntegerStamp(value.stamp(NodeView.DEFAULT), -1, value.graph());
        this.value = value;
    }

    public ValueNode getScale() {
        return scale;
    }

    @Override
    public ValueNode valueNode() {
        return value;
    }

    @Override
    public Direction direction() {
        Direction baseDirection = base.direction();
        if (baseDirection == null) {
            return null;
        }
        Stamp stamp = scale.stamp(NodeView.DEFAULT);
        if (stamp instanceof IntegerStamp) {
            IntegerStamp integerStamp = (IntegerStamp) stamp;
            if (integerStamp.isStrictlyPositive()) {
                return baseDirection;
            } else if (integerStamp.isStrictlyNegative()) {
                return baseDirection.opposite();
            }
        }
        return null;
    }

    @Override
    public ValueNode initNode() {
        return mul(graph(), base.initNode(), scale);
    }

    @Override
    public ValueNode strideNode() {
        return mul(graph(), base.strideNode(), scale);
    }

    @Override
    public boolean isConstantInit() {
        return scale.isConstant() && base.isConstantInit();
    }

    @Override
    public boolean isConstantStride() {
        return scale.isConstant() && base.isConstantStride();
    }

    @Override
    public long constantInit() {
        return base.constantInit() * scale.asJavaConstant().asLong();
    }

    @Override
    public long constantStride() {
        return base.constantStride() * scale.asJavaConstant().asLong();
    }

    @Override
    public ValueNode extremumNode(boolean assumePositiveTripCount, Stamp stamp) {
        return mul(graph(), base.extremumNode(assumePositiveTripCount, stamp), IntegerConvertNode.convert(scale, stamp, graph(), NodeView.DEFAULT));
    }

    @Override
    public ValueNode exitValueNode() {
        return mul(graph(), base.exitValueNode(), scale);
    }

    @Override
    public boolean isConstantExtremum() {
        return scale.isConstant() && base.isConstantExtremum();
    }

    @Override
    public long constantExtremum() {
        return base.constantExtremum() * scale.asJavaConstant().asLong();
    }

    @Override
    public void deleteUnusedNodes() {
        GraphUtil.tryKillUnused(scale);
    }

    @Override
    public String toString() {
        return String.format("DerivedScaleInductionVariable base (%s) %s %s", base, value.getNodeClass().shortName(), scale);
    }
}
