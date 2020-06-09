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


package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import java.util.Collections;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.VirtualizableAllocation;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.virtual.VirtualBoxingNode;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This node represents the boxing of a primitive value. This corresponds to a call to the valueOf
 * methods in Integer, Long, etc.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_8, size = SIZE_8, allowedUsageTypes = {InputType.Memory, InputType.Value})
public abstract class BoxNode extends AbstractBoxingNode implements VirtualizableAllocation, Lowerable, Canonicalizable.Unary<ValueNode> {

    public static final NodeClass<BoxNode> TYPE = NodeClass.create(BoxNode.class);

    private BoxNode(ValueNode value, ResolvedJavaType resultType, JavaKind boxingKind) {
        this(TYPE, value, resultType, boxingKind);
    }

    private BoxNode(NodeClass<? extends BoxNode> c, ValueNode value, ResolvedJavaType resultType, JavaKind boxingKind) {
        super(c, value, boxingKind, StampFactory.objectNonNull(TypeReference.createExactTrusted(resultType)), new FieldLocationIdentity(getValueField(resultType)));
        this.value = value;
    }

    public static BoxNode create(ValueNode value, ResolvedJavaType resultType, JavaKind boxingKind) {
        if (boxingKind == JavaKind.Boolean) {
            return new PureBoxNode(value, resultType, boxingKind);
        }
        return new AllocatingBoxNode(value, resultType, boxingKind);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        return this;
    }

    protected VirtualBoxingNode createVirtualBoxingNode() {
        VirtualBoxingNode node = new VirtualBoxingNode(StampTool.typeOrNull(stamp(NodeView.DEFAULT)), boxingKind);
        node.setNodeSourcePosition(getNodeSourcePosition());
        return node;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(getValue());

        VirtualBoxingNode newVirtual = createVirtualBoxingNode();
        assert newVirtual.getFields().length == 1;

        tool.createVirtualObject(newVirtual, new ValueNode[]{alias}, Collections.<MonitorIdNode> emptyList(), false);
        tool.replaceWithVirtual(newVirtual);
    }

    @NodeInfo(cycles = NodeCycles.CYCLES_8, size = SIZE_8, allowedUsageTypes = {InputType.Memory, InputType.Value})
    private static class PureBoxNode extends BoxNode {
        public static final NodeClass<PureBoxNode> TYPE = NodeClass.create(PureBoxNode.class);

        protected PureBoxNode(ValueNode value, ResolvedJavaType resultType, JavaKind boxingKind) {
            super(TYPE, value, resultType, boxingKind);
        }

    }

    @NodeInfo(cycles = NodeCycles.CYCLES_8, size = SIZE_8, allowedUsageTypes = {InputType.Memory, InputType.Value})
    private static class AllocatingBoxNode extends BoxNode implements SingleMemoryKill {
        public static final NodeClass<AllocatingBoxNode> TYPE = NodeClass.create(AllocatingBoxNode.class);

        protected AllocatingBoxNode(ValueNode value, ResolvedJavaType resultType, JavaKind boxingKind) {
            super(TYPE, value, resultType, boxingKind);
        }

        @Override
        public LocationIdentity getLocationIdentity() {
            return LocationIdentity.INIT_LOCATION;
        }

        @Override
        public LocationIdentity getKilledLocationIdentity() {
            return getLocationIdentity();
        }

    }
}
