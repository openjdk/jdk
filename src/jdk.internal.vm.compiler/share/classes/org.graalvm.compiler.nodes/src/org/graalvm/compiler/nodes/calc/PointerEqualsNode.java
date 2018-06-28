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

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable.BinaryCommutative;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.LoadMethodNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

@NodeInfo(shortName = "==")
public class PointerEqualsNode extends CompareNode implements BinaryCommutative<ValueNode> {

    public static final NodeClass<PointerEqualsNode> TYPE = NodeClass.create(PointerEqualsNode.class);
    private static final PointerEqualsOp OP = new PointerEqualsOp();

    public PointerEqualsNode(ValueNode x, ValueNode y) {
        this(TYPE, x, y);
    }

    public static LogicNode create(ValueNode x, ValueNode y, NodeView view) {
        LogicNode result = findSynonym(x, y, view);
        if (result != null) {
            return result;
        }
        return new PointerEqualsNode(x, y);
    }

    protected PointerEqualsNode(NodeClass<? extends PointerEqualsNode> c, ValueNode x, ValueNode y) {
        super(c, CanonicalCondition.EQ, false, x, y);
        assert x.stamp(NodeView.DEFAULT) instanceof AbstractPointerStamp;
        assert y.stamp(NodeView.DEFAULT) instanceof AbstractPointerStamp;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode value = OP.canonical(tool.getConstantReflection(), tool.getMetaAccess(), tool.getOptions(), tool.smallestCompareWidth(), CanonicalCondition.EQ, false, forX, forY, view);
        if (value != null) {
            return value;
        }
        return this;
    }

    public static class PointerEqualsOp extends CompareOp {

        /**
         * Determines if this is a comparison used to determine whether dispatching on a receiver
         * could select a certain method and if so, returns {@code true} if the answer is guaranteed
         * to be false. Otherwise, returns {@code false}.
         */
        private static boolean isAlwaysFailingVirtualDispatchTest(CanonicalCondition condition, ValueNode forX, ValueNode forY) {
            if (forY.isConstant()) {
                if (forX instanceof LoadMethodNode && condition == CanonicalCondition.EQ) {
                    LoadMethodNode lm = ((LoadMethodNode) forX);
                    if (lm.getMethod().getEncoding().equals(forY.asConstant())) {
                        if (lm.getHub() instanceof LoadHubNode) {
                            ValueNode object = ((LoadHubNode) lm.getHub()).getValue();
                            ResolvedJavaType type = StampTool.typeOrNull(object);
                            ResolvedJavaType declaringClass = lm.getMethod().getDeclaringClass();
                            if (type != null && !type.equals(declaringClass) && declaringClass.isAssignableFrom(type)) {
                                ResolvedJavaMethod override = type.resolveMethod(lm.getMethod(), lm.getCallerType());
                                if (override != null && !override.equals(lm.getMethod())) {
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
        public LogicNode canonical(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth, CanonicalCondition condition,
                        boolean unorderedIsTrue, ValueNode forX, ValueNode forY, NodeView view) {
            LogicNode result = findSynonym(forX, forY, view);
            if (result != null) {
                return result;
            }
            if (isAlwaysFailingVirtualDispatchTest(condition, forX, forY)) {
                return LogicConstantNode.contradiction();
            }
            return super.canonical(constantReflection, metaAccess, options, smallestCompareWidth, condition, unorderedIsTrue, forX, forY, view);
        }

        @Override
        protected CompareNode duplicateModified(ValueNode newX, ValueNode newY, boolean unorderedIsTrue, NodeView view) {
            return new PointerEqualsNode(newX, newY);
        }
    }

    public static LogicNode findSynonym(ValueNode forX, ValueNode forY, NodeView view) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return LogicConstantNode.tautology();
        } else if (forX.stamp(view).alwaysDistinct(forY.stamp(view))) {
            return LogicConstantNode.contradiction();
        } else if (((AbstractPointerStamp) forX.stamp(view)).alwaysNull()) {
            return IsNullNode.create(forY);
        } else if (((AbstractPointerStamp) forY.stamp(view)).alwaysNull()) {
            return IsNullNode.create(forX);
        } else {
            return null;
        }
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated, Stamp xStamp, Stamp yStamp) {
        if (!negated) {
            Stamp newStamp = xStamp.join(yStamp);
            if (!newStamp.equals(xStamp)) {
                return newStamp;
            }
        }
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated, Stamp xStamp, Stamp yStamp) {
        if (!negated) {
            Stamp newStamp = yStamp.join(xStamp);
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
