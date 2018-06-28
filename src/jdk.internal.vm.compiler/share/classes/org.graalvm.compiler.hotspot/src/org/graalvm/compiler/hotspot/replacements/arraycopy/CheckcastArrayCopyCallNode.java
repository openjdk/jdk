/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.replacements.arraycopy;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.hotspot.nodes.GetObjectAddressNode;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.memory.AbstractMemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(allowedUsageTypes = {InputType.Memory, InputType.Value}, cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public final class CheckcastArrayCopyCallNode extends AbstractMemoryCheckpoint implements Lowerable, MemoryCheckpoint.Single {

    public static final NodeClass<CheckcastArrayCopyCallNode> TYPE = NodeClass.create(CheckcastArrayCopyCallNode.class);
    @Input ValueNode src;
    @Input ValueNode srcPos;
    @Input ValueNode dest;
    @Input ValueNode destPos;
    @Input ValueNode length;
    @Input ValueNode destElemKlass;
    @Input ValueNode superCheckOffset;

    protected final boolean uninit;

    protected final HotSpotGraalRuntimeProvider runtime;

    protected CheckcastArrayCopyCallNode(@InjectedNodeParameter HotSpotGraalRuntimeProvider runtime, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length,
                    ValueNode superCheckOffset, ValueNode destElemKlass, boolean uninit) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.src = src;
        this.srcPos = srcPos;
        this.dest = dest;
        this.destPos = destPos;
        this.length = length;
        this.superCheckOffset = superCheckOffset;
        this.destElemKlass = destElemKlass;
        this.uninit = uninit;
        this.runtime = runtime;
    }

    public ValueNode getSource() {
        return src;
    }

    public ValueNode getSourcePosition() {
        return srcPos;
    }

    public ValueNode getDestination() {
        return dest;
    }

    public ValueNode getDestinationPosition() {
        return destPos;
    }

    public ValueNode getLength() {
        return length;
    }

    public boolean isUninit() {
        return uninit;
    }

    private ValueNode computeBase(LoweringTool tool, ValueNode base, ValueNode pos) {
        FixedWithNextNode basePtr = graph().add(new GetObjectAddressNode(base));
        graph().addBeforeFixed(this, basePtr);

        int shift = CodeUtil.log2(tool.getMetaAccess().getArrayIndexScale(JavaKind.Object));
        ValueNode extendedPos = IntegerConvertNode.convert(pos, StampFactory.forKind(runtime.getTarget().wordJavaKind), graph(), NodeView.DEFAULT);
        ValueNode scaledIndex = graph().unique(new LeftShiftNode(extendedPos, ConstantNode.forInt(shift, graph())));
        ValueNode offset = graph().unique(
                        new AddNode(scaledIndex,
                                        ConstantNode.forIntegerBits(PrimitiveStamp.getBits(scaledIndex.stamp(NodeView.DEFAULT)), tool.getMetaAccess().getArrayBaseOffset(JavaKind.Object), graph())));
        return graph().unique(new OffsetAddressNode(basePtr, offset));
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage().areFrameStatesAtDeopts()) {
            ForeignCallDescriptor desc = HotSpotHostForeignCallsProvider.lookupCheckcastArraycopyDescriptor(isUninit());
            StructuredGraph graph = graph();
            ValueNode srcAddr = computeBase(tool, getSource(), getSourcePosition());
            ValueNode destAddr = computeBase(tool, getDestination(), getDestinationPosition());
            ValueNode len = getLength();
            if (len.stamp(NodeView.DEFAULT).getStackKind() != runtime.getTarget().wordJavaKind) {
                len = IntegerConvertNode.convert(len, StampFactory.forKind(runtime.getTarget().wordJavaKind), graph(), NodeView.DEFAULT);
            }
            ForeignCallNode call = graph.add(new ForeignCallNode(runtime.getHostBackend().getForeignCalls(), desc, srcAddr, destAddr, len, superCheckOffset, destElemKlass));
            call.setStateAfter(stateAfter());
            graph.replaceFixedWithFixed(this, call);
        }
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        /*
         * Because of restrictions that the memory graph of snippets matches the original node,
         * pretend that we kill any.
         */
        return LocationIdentity.any();
    }

    @NodeIntrinsic
    public static native int checkcastArraycopy(Object src, int srcPos, Object dest, int destPos, int length, Word superCheckOffset, Object destElemKlass, @ConstantNodeParameter boolean uninit);
}
