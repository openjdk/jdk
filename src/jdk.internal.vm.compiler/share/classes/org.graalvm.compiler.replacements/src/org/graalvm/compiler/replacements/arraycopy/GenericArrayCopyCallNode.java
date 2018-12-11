/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
//JaCoCo Exclude


package org.graalvm.compiler.replacements.arraycopy;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;
import static org.graalvm.compiler.replacements.arraycopy.ArrayCopyForeignCalls.GENERIC_ARRAYCOPY;

import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.GetObjectAddressNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.memory.AbstractMemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(allowedUsageTypes = {InputType.Memory, InputType.Value}, cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public final class GenericArrayCopyCallNode extends AbstractMemoryCheckpoint implements Lowerable, MemoryCheckpoint.Single {

    public static final NodeClass<GenericArrayCopyCallNode> TYPE = NodeClass.create(GenericArrayCopyCallNode.class);
    private final ForeignCallsProvider foreignCalls;
    @Input ValueNode src;
    @Input ValueNode srcPos;
    @Input ValueNode dest;
    @Input ValueNode destPos;
    @Input ValueNode length;

    private ForeignCallsProvider getForeignCalls() {
        return foreignCalls;
    }

    protected GenericArrayCopyCallNode(@InjectedNodeParameter ForeignCallsProvider foreignCalls, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.foreignCalls = foreignCalls;
        this.src = src;
        this.srcPos = srcPos;
        this.dest = dest;
        this.destPos = destPos;
        this.length = length;
    }

    public ValueNode getSource() {
        return src;
    }

    public ValueNode getDestination() {
        return dest;
    }

    public ValueNode getLength() {
        return length;
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage().areFrameStatesAtDeopts()) {
            StructuredGraph graph = graph();
            ValueNode srcAddr = objectAddress(getSource());
            ValueNode destAddr = objectAddress(getDestination());
            ForeignCallNode call = graph.add(new ForeignCallNode(getForeignCalls(), GENERIC_ARRAYCOPY, srcAddr, srcPos, destAddr, destPos, length));
            call.setStateAfter(stateAfter());
            graph.replaceFixedWithFixed(this, call);
        }
    }

    private ValueNode objectAddress(ValueNode obj) {
        GetObjectAddressNode result = graph().add(new GetObjectAddressNode(obj));
        graph().addBeforeFixed(this, result);
        return result;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return LocationIdentity.any();
    }

    @NodeIntrinsic
    public static native int genericArraycopy(Object src, int srcPos, Object dest, int destPos, int length);
}
