/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.nodes;

import java.util.Collections;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.spi.VirtualizableAllocation;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public interface ObjectClone extends StateSplit, VirtualizableAllocation, ArrayLengthProvider {

    ValueNode getObject();

    int bci();

    default Stamp computeStamp(ValueNode object) {
        Stamp objectStamp = object.stamp(NodeView.DEFAULT);
        if (objectStamp instanceof ObjectStamp) {
            objectStamp = objectStamp.join(StampFactory.objectNonNull());
        }
        return objectStamp;
    }

    /*
     * Looks at the given stamp and determines if it is an exact type (or can be assumed to be an
     * exact type) and if it is a cloneable type.
     *
     * If yes, then the exact type is returned, otherwise it returns null.
     */
    default ResolvedJavaType getConcreteType(Stamp forStamp) {
        if (!(forStamp instanceof ObjectStamp)) {
            return null;
        }
        ObjectStamp objectStamp = (ObjectStamp) forStamp;
        if (objectStamp.type() == null) {
            return null;
        } else if (objectStamp.isExactType()) {
            return objectStamp.type().isCloneableWithAllocation() ? objectStamp.type() : null;
        } else if (objectStamp.type().isArray()) {
            return objectStamp.type();
        }
        return null;
    }

    default LoadFieldNode genLoadFieldNode(Assumptions assumptions, ValueNode originalAlias, ResolvedJavaField field) {
        return LoadFieldNode.create(assumptions, originalAlias, field);
    }

    default LoadIndexedNode genLoadIndexedNode(Assumptions assumptions, ValueNode originalAlias, ValueNode index, JavaKind elementKind) {
        return new LoadIndexedNode(assumptions, originalAlias, index, null, elementKind);
    }

    @Override
    default void virtualize(VirtualizerTool tool) {
        ValueNode originalAlias = tool.getAlias(getObject());
        if (originalAlias instanceof VirtualObjectNode) {
            VirtualObjectNode originalVirtual = (VirtualObjectNode) originalAlias;
            if (originalVirtual.type().isCloneableWithAllocation()) {
                ValueNode[] newEntryState = new ValueNode[originalVirtual.entryCount()];
                for (int i = 0; i < newEntryState.length; i++) {
                    newEntryState[i] = tool.getEntry(originalVirtual, i);
                }
                VirtualObjectNode newVirtual = originalVirtual.duplicate();
                tool.createVirtualObject(newVirtual, newEntryState, Collections.<MonitorIdNode> emptyList(), false);
                tool.replaceWithVirtual(newVirtual);
            }
        } else {
            ResolvedJavaType type = getConcreteType(originalAlias.stamp(NodeView.DEFAULT));
            if (type == null) {
                return;
            }
            if (!type.isArray()) {
                VirtualInstanceNode newVirtual = new VirtualInstanceNode(type, true);
                ResolvedJavaField[] fields = newVirtual.getFields();

                ValueNode[] state = new ValueNode[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    LoadFieldNode load = genLoadFieldNode(asNode().graph().getAssumptions(), originalAlias, fields[i]);
                    state[i] = load;
                    tool.addNode(load);
                }
                tool.createVirtualObject(newVirtual, state, Collections.<MonitorIdNode> emptyList(), false);
                tool.replaceWithVirtual(newVirtual);
            } else {
                ValueNode length = findLength(FindLengthMode.SEARCH_ONLY, tool.getConstantReflection());
                if (length == null) {
                    return;
                }
                ValueNode lengthAlias = tool.getAlias(length);
                if (!lengthAlias.isConstant()) {
                    return;
                }
                int constantLength = lengthAlias.asJavaConstant().asInt();
                if (constantLength >= 0 && constantLength <= tool.getMaximumEntryCount()) {
                    ValueNode[] state = new ValueNode[constantLength];
                    ResolvedJavaType componentType = type.getComponentType();
                    for (int i = 0; i < constantLength; i++) {
                        ConstantNode index = ConstantNode.forInt(i);
                        LoadIndexedNode load = genLoadIndexedNode(asNode().graph().getAssumptions(), originalAlias, index, componentType.getJavaKind());
                        state[i] = load;
                        tool.addNode(index);
                        tool.addNode(load);
                    }
                    VirtualObjectNode virtualObject = new VirtualArrayNode(componentType, constantLength);
                    tool.createVirtualObject(virtualObject, state, Collections.<MonitorIdNode> emptyList(), false);
                    tool.replaceWithVirtual(virtualObject);
                }
            }
        }
    }

    @Override
    default ValueNode findLength(FindLengthMode mode, ConstantReflectionProvider constantReflection) {
        return GraphUtil.arrayLength(getObject(), mode, constantReflection);
    }
}
