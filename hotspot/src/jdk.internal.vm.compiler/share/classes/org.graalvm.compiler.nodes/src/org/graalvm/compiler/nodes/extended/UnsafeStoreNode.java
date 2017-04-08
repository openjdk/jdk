/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.InputType.State;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_3;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.StoreFieldNode;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Store of a value at a location specified as an offset relative to an object. No null check is
 * performed before the store.
 */
@NodeInfo(cycles = CYCLES_3, size = SIZE_1)
public final class UnsafeStoreNode extends UnsafeAccessNode implements StateSplit, Lowerable, Virtualizable, MemoryCheckpoint.Single {

    public static final NodeClass<UnsafeStoreNode> TYPE = NodeClass.create(UnsafeStoreNode.class);
    @Input ValueNode value;
    @OptionalInput(State) FrameState stateAfter;

    public UnsafeStoreNode(ValueNode object, ValueNode offset, ValueNode value, JavaKind accessKind, LocationIdentity locationIdentity) {
        this(object, offset, value, accessKind, locationIdentity, null);
    }

    public UnsafeStoreNode(ValueNode object, ValueNode offset, ValueNode value, JavaKind accessKind, LocationIdentity locationIdentity, FrameState stateAfter) {
        super(TYPE, StampFactory.forVoid(), object, offset, accessKind, locationIdentity);
        this.value = value;
        this.stateAfter = stateAfter;
        assert accessKind != JavaKind.Void && accessKind != JavaKind.Illegal;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    public ValueNode value() {
        return value;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object());
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            ValueNode indexValue = tool.getAlias(offset());
            if (indexValue.isConstant()) {
                long off = indexValue.asJavaConstant().asLong();
                int entryIndex = virtual.entryIndexForOffset(off, accessKind());
                if (entryIndex != -1) {
                    JavaKind entryKind = virtual.entryKind(entryIndex);
                    ValueNode entry = tool.getEntry(virtual, entryIndex);
                    if (entry.getStackKind() == value.getStackKind() || entryKind == accessKind()) {
                        tool.setVirtualEntry(virtual, entryIndex, value(), true);
                        tool.delete();
                    } else {
                        if ((accessKind() == JavaKind.Long || accessKind() == JavaKind.Double) && entryKind == JavaKind.Int) {
                            int nextIndex = virtual.entryIndexForOffset(off + 4, entryKind);
                            if (nextIndex != -1) {
                                JavaKind nextKind = virtual.entryKind(nextIndex);
                                if (nextKind == JavaKind.Int) {
                                    tool.setVirtualEntry(virtual, entryIndex, value(), true);
                                    tool.setVirtualEntry(virtual, nextIndex, ConstantNode.forConstant(JavaConstant.forIllegal(), tool.getMetaAccessProvider(), graph()), true);
                                    tool.delete();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected ValueNode cloneAsFieldAccess(Assumptions assumptions, ResolvedJavaField field) {
        return new StoreFieldNode(object(), field, value(), stateAfter());
    }

    @Override
    protected ValueNode cloneAsArrayAccess(ValueNode location, LocationIdentity identity) {
        return new UnsafeStoreNode(object(), location, value, accessKind(), identity, stateAfter());
    }

    public FrameState getState() {
        return stateAfter;
    }
}
