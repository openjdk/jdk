/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * If the comparison is undecided (one of the inputs is NaN), the result is 1 if isUnorderedLess is
 * false and -1 if isUnorderedLess is true.
 */
@NodeInfo
public final class FloatNormalizeCompareNode extends AbstractNormalizeCompareNode {
    public static final NodeClass<FloatNormalizeCompareNode> TYPE = NodeClass.create(FloatNormalizeCompareNode.class);
    protected final boolean isUnorderedLess;

    public FloatNormalizeCompareNode(ValueNode x, ValueNode y, JavaKind kind, boolean isUnorderedLess) {
        super(TYPE, kind, x, y);
        this.isUnorderedLess = isUnorderedLess;
    }

    public static ValueNode create(ValueNode x, ValueNode y, boolean isUnorderedLess, JavaKind kind, ConstantReflectionProvider constantReflection) {
        ValueNode result = tryConstantFold(x, y, isUnorderedLess, false, kind, constantReflection);
        if (result != null) {
            return result;
        }

        return new FloatNormalizeCompareNode(x, y, kind, isUnorderedLess);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode result = tryConstantFold(x, y, isUnorderedLess, false, stamp(view).getStackKind(), tool.getConstantReflection());
        if (result != null) {
            return result;
        }
        return this;
    }

    public boolean isUnorderedLess() {
        return isUnorderedLess;
    }

    @Override
    public LogicNode createEqualComparison() {
        return FloatEqualsNode.create(x, y, NodeView.DEFAULT);
    }

    @Override
    public LogicNode createEqualComparison(ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth, NodeView view) {
        return FloatEqualsNode.create(constantReflection, metaAccess, options, smallestCompareWidth, x, y, NodeView.DEFAULT);
    }

    @Override
    public LogicNode createLowerComparison(boolean swapInputs) {
        ValueNode a = swapInputs ? y : x;
        ValueNode b = swapInputs ? x : y;
        return FloatLessThanNode.create(a, b, isUnorderedLess() ^ swapInputs, NodeView.DEFAULT);
    }

    @Override
    public LogicNode createLowerComparison(boolean swapInputs, ConstantReflectionProvider constantReflection, MetaAccessProvider metaAccess, OptionValues options, Integer smallestCompareWidth,
                    NodeView view) {
        ValueNode a = swapInputs ? y : x;
        ValueNode b = swapInputs ? x : y;
        return FloatLessThanNode.create(constantReflection, metaAccess, options, smallestCompareWidth, a, b, isUnorderedLess() ^ swapInputs, NodeView.DEFAULT);
    }
}
