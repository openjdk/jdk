/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.InputType.State;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_64;
import static jdk.internal.vm.compiler.word.LocationIdentity.any;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.memory.AbstractMemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.MemoryNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = SIZE_64)
public class BasicArrayCopyNode extends AbstractMemoryCheckpoint implements Virtualizable, MemoryCheckpoint.Single, MemoryAccess, Lowerable, DeoptimizingNode.DeoptDuring {

    public static final NodeClass<BasicArrayCopyNode> TYPE = NodeClass.create(BasicArrayCopyNode.class);

    static final int SRC_ARG = 0;
    static final int SRC_POS_ARG = 1;
    static final int DEST_ARG = 2;
    static final int DEST_POS_ARG = 3;
    static final int LENGTH_ARG = 4;

    @Input NodeInputList<ValueNode> args;

    @OptionalInput(State) FrameState stateDuring;

    @OptionalInput(Memory) protected MemoryNode lastLocationAccess;

    protected JavaKind elementKind;

    protected int bci;

    public BasicArrayCopyNode(NodeClass<? extends AbstractMemoryCheckpoint> type, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, JavaKind elementKind, int bci) {
        super(type, StampFactory.forKind(JavaKind.Void));
        this.bci = bci;
        this.args = new NodeInputList<>(this, new ValueNode[]{src, srcPos, dest, destPos, length});
        this.elementKind = elementKind != JavaKind.Illegal ? elementKind : null;
    }

    public BasicArrayCopyNode(NodeClass<? extends AbstractMemoryCheckpoint> type, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, JavaKind elementKind) {
        this(type, src, srcPos, dest, destPos, length, elementKind, BytecodeFrame.INVALID_FRAMESTATE_BCI);
    }

    public ValueNode getSource() {
        return args.get(SRC_ARG);
    }

    public ValueNode getSourcePosition() {
        return args.get(SRC_POS_ARG);
    }

    public ValueNode getDestination() {
        return args.get(DEST_ARG);
    }

    public ValueNode getDestinationPosition() {
        return args.get(DEST_POS_ARG);
    }

    public ValueNode getLength() {
        return args.get(LENGTH_ARG);
    }

    public int getBci() {
        return bci;
    }

    public JavaKind getElementKind() {
        return elementKind;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        if (elementKind != null) {
            return NamedLocationIdentity.getArrayLocation(elementKind);
        }
        return any();
    }

    @Override
    public MemoryNode getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryNode lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    private static boolean checkBounds(int position, int length, VirtualObjectNode virtualObject) {
        return position >= 0 && position + length <= virtualObject.entryCount();
    }

    private static boolean checkEntryTypes(int srcPos, int length, VirtualObjectNode src, ResolvedJavaType destComponentType, VirtualizerTool tool) {
        if (destComponentType.getJavaKind() == JavaKind.Object && !destComponentType.isJavaLangObject()) {
            for (int i = 0; i < length; i++) {
                ValueNode entry = tool.getEntry(src, srcPos + i);
                ResolvedJavaType type = StampTool.typeOrNull(entry);
                if (type == null || !destComponentType.isAssignableFrom(type)) {
                    return false;
                }
            }
        }
        return true;
    }

    /*
     * Returns true if this copy doesn't require store checks. Trivially true for primitive arrays.
     */
    public boolean isExact() {
        ResolvedJavaType srcType = StampTool.typeOrNull(getSource().stamp(NodeView.DEFAULT));
        ResolvedJavaType destType = StampTool.typeOrNull(getDestination().stamp(NodeView.DEFAULT));
        if (srcType == null || !srcType.isArray() || destType == null || !destType.isArray()) {
            return false;
        }
        if ((srcType.getComponentType().getJavaKind().isPrimitive() && destType.getComponentType().equals(srcType.getComponentType())) || getSource() == getDestination()) {
            return true;
        }

        if (StampTool.isExactType(getDestination().stamp(NodeView.DEFAULT))) {
            if (destType != null && destType.isAssignableFrom(srcType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode sourcePosition = tool.getAlias(getSourcePosition());
        ValueNode destinationPosition = tool.getAlias(getDestinationPosition());
        ValueNode replacedLength = tool.getAlias(getLength());

        if (sourcePosition.isConstant() && destinationPosition.isConstant() && replacedLength.isConstant()) {
            int srcPosInt = sourcePosition.asJavaConstant().asInt();
            int destPosInt = destinationPosition.asJavaConstant().asInt();
            int len = replacedLength.asJavaConstant().asInt();
            ValueNode destAlias = tool.getAlias(getDestination());

            if (destAlias instanceof VirtualArrayNode) {
                VirtualArrayNode destVirtual = (VirtualArrayNode) destAlias;
                if (len < 0 || !checkBounds(destPosInt, len, destVirtual)) {
                    return;
                }
                ValueNode srcAlias = tool.getAlias(getSource());

                if (srcAlias instanceof VirtualObjectNode) {
                    if (!(srcAlias instanceof VirtualArrayNode)) {
                        return;
                    }
                    VirtualArrayNode srcVirtual = (VirtualArrayNode) srcAlias;
                    if (destVirtual.componentType().getJavaKind() != srcVirtual.componentType().getJavaKind()) {
                        return;
                    }
                    if (!checkBounds(srcPosInt, len, srcVirtual)) {
                        return;
                    }
                    if (!checkEntryTypes(srcPosInt, len, srcVirtual, destVirtual.type().getComponentType(), tool)) {
                        return;
                    }
                    for (int i = 0; i < len; i++) {
                        tool.setVirtualEntry(destVirtual, destPosInt + i, tool.getEntry(srcVirtual, srcPosInt + i));
                    }
                    tool.delete();
                    DebugContext debug = getDebug();
                    if (debug.isLogEnabled()) {
                        debug.log("virtualized arraycopy(%s, %d, %s, %d, %d)", getSource(), srcPosInt, getDestination(), destPosInt, len);
                    }
                } else {
                    ResolvedJavaType sourceType = StampTool.typeOrNull(srcAlias);
                    if (sourceType == null || !sourceType.isArray()) {
                        return;
                    }
                    ResolvedJavaType sourceComponentType = sourceType.getComponentType();
                    ResolvedJavaType destComponentType = destVirtual.type().getComponentType();
                    if (!sourceComponentType.equals(destComponentType)) {
                        return;
                    }
                    for (int i = 0; i < len; i++) {
                        LoadIndexedNode load = new LoadIndexedNode(graph().getAssumptions(), srcAlias, ConstantNode.forInt(i + srcPosInt, graph()), null, destComponentType.getJavaKind());
                        load.setNodeSourcePosition(getNodeSourcePosition());
                        tool.addNode(load);
                        tool.setVirtualEntry(destVirtual, destPosInt + i, load);
                    }
                    tool.delete();
                }
            }
        }
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    public FrameState stateDuring() {
        return stateDuring;
    }

    @Override
    public void setStateDuring(FrameState stateDuring) {
        updateUsages(this.stateDuring, stateDuring);
        this.stateDuring = stateDuring;
    }

    @Override
    public void computeStateDuring(FrameState currentStateAfter) {
        FrameState newStateDuring = currentStateAfter.duplicateModifiedDuringCall(getBci(), asNode().getStackKind());
        setStateDuring(newStateDuring);
    }
}
