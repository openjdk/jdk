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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.TriState;

@NodeInfo(shortName = "<", cycles = CYCLES_2)
public final class FloatLessThanNode extends CompareNode {
    public static final NodeClass<FloatLessThanNode> TYPE = NodeClass.create(FloatLessThanNode.class);
    private static final FloatLessThanOp OP = new FloatLessThanOp();

    public FloatLessThanNode(ValueNode x, ValueNode y, boolean unorderedIsTrue) {
        super(TYPE, CanonicalCondition.LT, unorderedIsTrue, x, y);
        assert x.stamp(NodeView.DEFAULT) instanceof FloatStamp && y.stamp(NodeView.DEFAULT) instanceof FloatStamp;
        assert x.stamp(NodeView.DEFAULT).isCompatible(y.stamp(NodeView.DEFAULT));
    }

    public static LogicNode create(ValueNode x, ValueNode y, boolean unorderedIsTrue, NodeView view) {
        LogicNode result = CompareNode.tryConstantFoldPrimitive(CanonicalCondition.LT, x, y, unorderedIsTrue, view);
        if (result != null) {
            return result;
        }
        return new FloatLessThanNode(x, y, unorderedIsTrue);
    }

    public static LogicNode create(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                    ValueNode x, ValueNode y, boolean unorderedIsTrue, NodeView view) {
        LogicNode result = OP.canonical(constantReflection, metaAccess, options, smallestCompareWidth, CanonicalCondition.LT, unorderedIsTrue, x, y, view);
        if (result != null) {
            return result;
        }
        return create(x, y, unorderedIsTrue, view);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode value = OP.canonical(tool.getConstantReflection(), tool.getMetaAccess(), tool.getOptions(), tool.smallestCompareWidth(), CanonicalCondition.LT, unorderedIsTrue, forX, forY, view);
        if (value != null) {
            return value;
        }
        return this;
    }

    public static class FloatLessThanOp extends CompareOp {

        @Override
        public LogicNode canonical(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth, CanonicalCondition condition,
                        boolean unorderedIsTrue, ValueNode forX, ValueNode forY, NodeView view) {
            LogicNode result = super.canonical(constantReflection, metaAccess, options, smallestCompareWidth, condition, unorderedIsTrue, forX, forY, view);
            if (result != null) {
                return result;
            }
            if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY) && !unorderedIsTrue) {
                return LogicConstantNode.contradiction();
            }
            return null;
        }

        @Override
        protected CompareNode duplicateModified(ValueNode newX, ValueNode newY, boolean unorderedIsTrue, NodeView view) {
            if (newX.stamp(NodeView.DEFAULT) instanceof FloatStamp && newY.stamp(NodeView.DEFAULT) instanceof FloatStamp) {
                return new FloatLessThanNode(newX, newY, unorderedIsTrue);
            } else if (newX.stamp(NodeView.DEFAULT) instanceof IntegerStamp && newY.stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                return new IntegerLessThanNode(newX, newY);
            }
            throw GraalError.shouldNotReachHere();
        }
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated, Stamp xStamp, Stamp yStamp) {
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated, Stamp xStamp, Stamp yStamp) {
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStampGeneric, Stamp yStampGeneric) {
        return TriState.UNKNOWN;
    }
}
