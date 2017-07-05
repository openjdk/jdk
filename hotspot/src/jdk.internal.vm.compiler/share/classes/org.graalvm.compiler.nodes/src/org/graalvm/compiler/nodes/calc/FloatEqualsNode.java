/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable.BinaryCommutative;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.TriState;

@NodeInfo(shortName = "==", cycles = NodeCycles.CYCLES_3)
public final class FloatEqualsNode extends CompareNode implements BinaryCommutative<ValueNode> {
    public static final NodeClass<FloatEqualsNode> TYPE = NodeClass.create(FloatEqualsNode.class);

    public FloatEqualsNode(ValueNode x, ValueNode y) {
        super(TYPE, Condition.EQ, false, x, y);
        assert x.stamp() instanceof FloatStamp && y.stamp() instanceof FloatStamp : x.stamp() + " " + y.stamp();
        assert x.stamp().isCompatible(y.stamp());
    }

    public static LogicNode create(ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection) {
        LogicNode result = CompareNode.tryConstantFold(Condition.EQ, x, y, constantReflection, false);
        if (result != null) {
            return result;
        } else {
            return new FloatEqualsNode(x, y).maybeCommuteInputs();
        }
    }

    @Override
    public boolean isIdentityComparison() {
        FloatStamp xStamp = (FloatStamp) x.stamp();
        FloatStamp yStamp = (FloatStamp) y.stamp();
        /*
         * If both stamps have at most one 0.0 and it's the same 0.0 then this is an identity
         * comparison. FloatStamp isn't careful about tracking the presence of -0.0 so assume that
         * anything that includes 0.0 might include -0.0. So if either one is non-zero then it's an
         * identity comparison.
         */
        return (!xStamp.contains(0.0) || !yStamp.contains(0.0));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        ValueNode result = super.canonical(tool, forX, forY);
        if (result != this) {
            return result;
        }
        Stamp xStampGeneric = forX.stamp();
        Stamp yStampGeneric = forY.stamp();
        if (xStampGeneric instanceof FloatStamp && yStampGeneric instanceof FloatStamp) {
            FloatStamp xStamp = (FloatStamp) xStampGeneric;
            FloatStamp yStamp = (FloatStamp) yStampGeneric;
            if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY) && xStamp.isNonNaN() && yStamp.isNonNaN()) {
                return LogicConstantNode.tautology();
            } else if (xStamp.alwaysDistinct(yStamp)) {
                return LogicConstantNode.contradiction();
            }
        }
        return this;
    }

    @Override
    protected CompareNode duplicateModified(ValueNode newX, ValueNode newY) {
        if (newX.stamp() instanceof FloatStamp && newY.stamp() instanceof FloatStamp) {
            return new FloatEqualsNode(newX, newY);
        } else if (newX.stamp() instanceof IntegerStamp && newY.stamp() instanceof IntegerStamp) {
            return new IntegerEqualsNode(newX, newY);
        }
        throw GraalError.shouldNotReachHere();
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated) {
        if (!negated) {
            return getX().stamp().join(getY().stamp());
        }
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated) {
        if (!negated) {
            return getX().stamp().join(getY().stamp());
        }
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        if (xStampGeneric instanceof FloatStamp && yStampGeneric instanceof FloatStamp) {
            FloatStamp xStamp = (FloatStamp) xStampGeneric;
            FloatStamp yStamp = (FloatStamp) yStampGeneric;
            if (xStamp.alwaysDistinct(yStamp)) {
                return TriState.FALSE;
            } else if (xStamp.neverDistinct(yStamp)) {
                return TriState.TRUE;
            }
        }
        return TriState.UNKNOWN;
    }
}
