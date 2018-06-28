/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import java.util.Collections;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.spi.VirtualizableAllocation;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.VirtualInstanceNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = SIZE_8)
public abstract class BasicObjectCloneNode extends MacroStateSplitNode implements VirtualizableAllocation, ArrayLengthProvider {

    public static final NodeClass<BasicObjectCloneNode> TYPE = NodeClass.create(BasicObjectCloneNode.class);

    public BasicObjectCloneNode(NodeClass<? extends MacroNode> c, InvokeKind invokeKind, ResolvedJavaMethod targetMethod, int bci, StampPair returnStamp, ValueNode... arguments) {
        super(c, invokeKind, targetMethod, bci, returnStamp, arguments);
        updateStamp(computeStamp(getObject()));
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(stamp.improveWith(computeStamp(getObject())));
    }

    protected Stamp computeStamp(ValueNode object) {
        Stamp objectStamp = object.stamp(NodeView.DEFAULT);
        if (objectStamp instanceof ObjectStamp) {
            objectStamp = objectStamp.join(StampFactory.objectNonNull());
        }
        return objectStamp;
    }

    public ValueNode getObject() {
        return arguments.get(0);
    }

    /*
     * Looks at the given stamp and determines if it is an exact type (or can be assumed to be an
     * exact type) and if it is a cloneable type.
     *
     * If yes, then the exact type is returned, otherwise it returns null.
     */
    protected ResolvedJavaType getConcreteType(Stamp forStamp) {
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

    protected LoadFieldNode genLoadFieldNode(Assumptions assumptions, ValueNode originalAlias, ResolvedJavaField field) {
        return LoadFieldNode.create(assumptions, originalAlias, field);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
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
            if (type != null && !type.isArray()) {
                VirtualInstanceNode newVirtual = createVirtualInstanceNode(type, true);
                ResolvedJavaField[] fields = newVirtual.getFields();

                ValueNode[] state = new ValueNode[fields.length];
                final LoadFieldNode[] loads = new LoadFieldNode[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    state[i] = loads[i] = genLoadFieldNode(graph().getAssumptions(), originalAlias, fields[i]);
                    tool.addNode(loads[i]);
                }
                tool.createVirtualObject(newVirtual, state, Collections.<MonitorIdNode> emptyList(), false);
                tool.replaceWithVirtual(newVirtual);
            }
        }
    }

    protected VirtualInstanceNode createVirtualInstanceNode(ResolvedJavaType type, boolean hasIdentity) {
        return new VirtualInstanceNode(type, hasIdentity);
    }

    @Override
    public ValueNode findLength(ArrayLengthProvider.FindLengthMode mode) {
        return GraphUtil.arrayLength(getObject(), mode);
    }
}
