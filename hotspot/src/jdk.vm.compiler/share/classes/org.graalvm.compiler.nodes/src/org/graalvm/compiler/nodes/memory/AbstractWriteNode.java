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
package org.graalvm.compiler.nodes.memory;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_3;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;

@NodeInfo(allowedUsageTypes = {InputType.Memory, InputType.Guard}, cycles = CYCLES_3, size = SIZE_1)
public abstract class AbstractWriteNode extends FixedAccessNode implements StateSplit, MemoryCheckpoint.Single, MemoryAccess, GuardingNode {

    public static final NodeClass<AbstractWriteNode> TYPE = NodeClass.create(AbstractWriteNode.class);
    @Input ValueNode value;
    @OptionalInput(InputType.State) FrameState stateAfter;
    @OptionalInput(InputType.Memory) Node lastLocationAccess;

    protected final boolean initialization;

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

    /**
     * Returns whether this write is the initialization of the written location. If it is true, the
     * old value of the memory location is either uninitialized or zero. If it is false, the memory
     * location is guaranteed to contain a valid value or zero.
     */
    public boolean isInitialization() {
        return initialization;
    }

    protected AbstractWriteNode(NodeClass<? extends AbstractWriteNode> c, AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
        this(c, address, location, value, barrierType, false);
    }

    protected AbstractWriteNode(NodeClass<? extends AbstractWriteNode> c, AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, boolean initialization) {
        super(c, address, location, StampFactory.forVoid(), barrierType);
        this.value = value;
        this.initialization = initialization;
    }

    protected AbstractWriteNode(NodeClass<? extends AbstractWriteNode> c, AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, GuardingNode guard,
                    boolean initialization) {
        super(c, address, location, StampFactory.forVoid(), guard, barrierType, false, null);
        this.value = value;
        this.initialization = initialization;
    }

    @Override
    public boolean isAllowedUsageType(InputType type) {
        return (type == InputType.Guard && getNullCheck()) ? true : super.isAllowedUsageType(type);
    }

    @Override
    public MemoryNode getLastLocationAccess() {
        return (MemoryNode) lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryNode lla) {
        Node newLla = ValueNodeUtil.asNode(lla);
        updateUsages(lastLocationAccess, newLla);
        lastLocationAccess = newLla;
    }
}
