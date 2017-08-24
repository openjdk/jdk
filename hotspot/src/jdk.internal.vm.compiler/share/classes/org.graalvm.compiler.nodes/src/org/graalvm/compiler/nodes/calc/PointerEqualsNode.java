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

import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable.BinaryCommutative;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.LoadMethodNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

@NodeInfo(shortName = "==")
public class PointerEqualsNode extends CompareNode implements BinaryCommutative<ValueNode> {

    public static final NodeClass<PointerEqualsNode> TYPE = NodeClass.create(PointerEqualsNode.class);

    public PointerEqualsNode(ValueNode x, ValueNode y) {
        this(TYPE, x, y);
    }

    public static LogicNode create(ValueNode x, ValueNode y) {
        LogicNode result = findSynonym(x, y);
        if (result != null) {
            return result;
        }
        return new PointerEqualsNode(x, y);
    }

    protected PointerEqualsNode(NodeClass<? extends PointerEqualsNode> c, ValueNode x, ValueNode y) {
        super(c, Condition.EQ, false, x, y);
        assert x.stamp() instanceof AbstractPointerStamp;
        assert y.stamp() instanceof AbstractPointerStamp;
    }

    /**
     * Determines if this is a comparison used to determine whether dispatching on a receiver could
     * select a certain method and if so, returns {@code true} if the answer is guaranteed to be
     * false. Otherwise, returns {@code false}.
     */
    private boolean isAlwaysFailingVirtualDispatchTest(ValueNode forX, ValueNode forY) {
        if (forY.isConstant()) {
            if (forX instanceof LoadMethodNode && condition == Condition.EQ) {
                LoadMethodNode lm = ((LoadMethodNode) forX);
                if (lm.getMethod().getEncoding().equals(forY.asConstant())) {
                    if (lm.getHub() instanceof LoadHubNode) {
                        ValueNode object = ((LoadHubNode) lm.getHub()).getValue();
                        ResolvedJavaType type = StampTool.typeOrNull(object);
                        ResolvedJavaType declaringClass = lm.getMethod().getDeclaringClass();
                        if (type != null && !type.equals(declaringClass) && declaringClass.isAssignableFrom(type)) {
                            ResolvedJavaMethod override = type.resolveMethod(lm.getMethod(), lm.getCallerType());
                            if (override != null && override != lm.getMethod()) {
                                assert declaringClass.isAssignableFrom(override.getDeclaringClass());
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        LogicNode result = findSynonym(forX, forY);
        if (result != null) {
            return result;
        }
        if (isAlwaysFailingVirtualDispatchTest(forX, forY)) {
            return LogicConstantNode.contradiction();
        }
        return super.canonical(tool, forX, forY);
    }

    public static LogicNode findSynonym(ValueNode forX, ValueNode forY) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return LogicConstantNode.tautology();
        } else if (forX.stamp().alwaysDistinct(forY.stamp())) {
            return LogicConstantNode.contradiction();
        } else if (((AbstractPointerStamp) forX.stamp()).alwaysNull()) {
            return IsNullNode.create(forY);
        } else if (((AbstractPointerStamp) forY.stamp()).alwaysNull()) {
            return IsNullNode.create(forX);
        } else {
            return null;
        }
    }

    @Override
    protected CompareNode duplicateModified(ValueNode newX, ValueNode newY) {
        return new PointerEqualsNode(newX, newY);
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated) {
        if (!negated) {
            Stamp xStamp = getX().stamp();
            Stamp newStamp = xStamp.join(getY().stamp());
            if (!newStamp.equals(xStamp)) {
                return newStamp;
            }
        }
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated) {
        if (!negated) {
            Stamp yStamp = getY().stamp();
            Stamp newStamp = yStamp.join(getX().stamp());
            if (!newStamp.equals(yStamp)) {
                return newStamp;
            }
        }
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        if (xStampGeneric instanceof ObjectStamp && yStampGeneric instanceof ObjectStamp) {
            ObjectStamp xStamp = (ObjectStamp) xStampGeneric;
            ObjectStamp yStamp = (ObjectStamp) yStampGeneric;
            if (xStamp.alwaysDistinct(yStamp)) {
                return TriState.FALSE;
            } else if (xStamp.neverDistinct(yStamp)) {
                return TriState.TRUE;
            }
        }
        return TriState.UNKNOWN;
    }
}
