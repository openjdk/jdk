/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.java;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;

/**
 * The {@code ArrayLength} instruction gets the length of an array.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class ArrayLengthNode extends FixedWithNextNode implements Canonicalizable.Unary<ValueNode>, Lowerable, Virtualizable {

    public static final NodeClass<ArrayLengthNode> TYPE = NodeClass.create(ArrayLengthNode.class);
    @Input ValueNode array;

    public ValueNode array() {
        return array;
    }

    @Override
    public ValueNode getValue() {
        return array;
    }

    public ArrayLengthNode(ValueNode array) {
        super(TYPE, StampFactory.positiveInt());
        this.array = array;
    }

    public static ValueNode create(ValueNode forValue, ConstantReflectionProvider constantReflection) {
        if (forValue instanceof NewArrayNode) {
            NewArrayNode newArray = (NewArrayNode) forValue;
            return newArray.length();
        }

        ValueNode length = readArrayLengthConstant(forValue, constantReflection);
        if (length != null) {
            return length;
        }
        return new ArrayLengthNode(forValue);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode length = readArrayLength(forValue, tool.getConstantReflection());
        if (length != null) {
            return length;
        }
        return this;
    }

    /**
     * Gets the length of an array if possible.
     *
     * @return a node representing the length of {@code array} or null if it is not available
     */
    public static ValueNode readArrayLength(ValueNode originalArray, ConstantReflectionProvider constantReflection) {
        ValueNode length = GraphUtil.arrayLength(originalArray, ArrayLengthProvider.FindLengthMode.CANONICALIZE_READ);
        if (length != null) {
            return length;
        }
        return readArrayLengthConstant(originalArray, constantReflection);
    }

    private static ValueNode readArrayLengthConstant(ValueNode originalArray, ConstantReflectionProvider constantReflection) {
        ValueNode array = GraphUtil.unproxify(originalArray);
        if (constantReflection != null && array.isConstant() && !array.isNullConstant()) {
            JavaConstant constantValue = array.asJavaConstant();
            if (constantValue != null && constantValue.isNonNull()) {
                Integer constantLength = constantReflection.readArrayLength(constantValue);
                if (constantLength != null) {
                    return ConstantNode.forInt(constantLength);
                }
            }
        }
        return null;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @NodeIntrinsic
    public static native int arrayLength(Object array);

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(array());
        if (alias instanceof VirtualArrayNode) {
            VirtualArrayNode virtualArray = (VirtualArrayNode) alias;
            tool.replaceWithValue(ConstantNode.forInt(virtualArray.entryCount(), graph()));
        }
    }
}
