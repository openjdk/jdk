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


package org.graalvm.compiler.replacements.arraycopy;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Interface for all nodes that implement the {@link System#arraycopy} operation. Provides
 * implementations for most operations concerning arraycopy.
 */
public interface ArrayCopy extends Virtualizable, SingleMemoryKill, MemoryAccess, Lowerable, DeoptimizingNode.DeoptDuring {

    int SRC_ARG = 0;
    int SRC_POS_ARG = 1;
    int DEST_ARG = 2;
    int DEST_POS_ARG = 3;
    int LENGTH_ARG = 4;

    NodeInputList<ValueNode> args();

    default ValueNode getSource() {
        return args().get(SRC_ARG);
    }

    default ValueNode getSourcePosition() {
        return args().get(SRC_POS_ARG);
    }

    default ValueNode getDestination() {
        return args().get(DEST_ARG);
    }

    default ValueNode getDestinationPosition() {
        return args().get(DEST_POS_ARG);
    }

    default ValueNode getLength() {
        return args().get(LENGTH_ARG);
    }

    int getBci();

    JavaKind getElementKind();

    default boolean checkBounds(int position, int length, VirtualObjectNode virtualObject) {
        assert length >= 0;
        return position >= 0 && position <= virtualObject.entryCount() - length;
    }

    default boolean checkEntryTypes(int srcPos, int length, VirtualObjectNode src, ResolvedJavaType destComponentType, VirtualizerTool tool) {
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
    default boolean isExact() {
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
    default void virtualize(VirtualizerTool tool) {
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
                    if (srcVirtual == destVirtual && srcPosInt < destPosInt) {
                        // must copy backwards to avoid losing elements
                        for (int i = len - 1; i >= 0; i--) {
                            tool.setVirtualEntry(destVirtual, destPosInt + i, tool.getEntry(srcVirtual, srcPosInt + i));
                        }
                    } else {
                        for (int i = 0; i < len; i++) {
                            tool.setVirtualEntry(destVirtual, destPosInt + i, tool.getEntry(srcVirtual, srcPosInt + i));
                        }
                    }
                    tool.delete();
                    DebugContext debug = this.asNode().getDebug();
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
                        LoadIndexedNode load = new LoadIndexedNode(this.asNode().graph().getAssumptions(), srcAlias, ConstantNode.forInt(i + srcPosInt, this.asNode().graph()), null,
                                        destComponentType.getJavaKind());
                        load.setNodeSourcePosition(this.asNode().getNodeSourcePosition());
                        tool.addNode(load);
                        tool.setVirtualEntry(destVirtual, destPosInt + i, load);
                    }
                    tool.delete();
                }
            }
        }
    }

    @Override
    default boolean canDeoptimize() {
        return true;
    }

    @Override
    default boolean hasSideEffect() {
        return !getKilledLocationIdentity().isInit();
    }

    @Override
    default void computeStateDuring(FrameState currentStateAfter) {
        FrameState newStateDuring = currentStateAfter.duplicateModifiedDuringCall(getBci(), asNode().getStackKind());
        setStateDuring(newStateDuring);
    }

    static JavaKind selectComponentKind(ArrayCopy arraycopy) {
        ResolvedJavaType srcType = StampTool.typeOrNull(arraycopy.getSource().stamp(NodeView.DEFAULT));
        ResolvedJavaType destType = StampTool.typeOrNull(arraycopy.getDestination().stamp(NodeView.DEFAULT));

        if (srcType == null || !srcType.isArray() || destType == null || !destType.isArray()) {
            return null;
        }
        if (!destType.getComponentType().isAssignableFrom(srcType.getComponentType())) {
            return null;
        }
        if (!arraycopy.isExact()) {
            return null;
        }
        return srcType.getComponentType().getJavaKind();
    }
}
