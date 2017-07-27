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

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * A floating read of a value from memory specified in terms of an object base and an object
 * relative location. This node does not null check the object.
 */
@NodeInfo(nameTemplate = "Read#{p#location/s}", cycles = CYCLES_2, size = SIZE_1)
public final class FloatingReadNode extends FloatingAccessNode implements LIRLowerable, Canonicalizable {
    public static final NodeClass<FloatingReadNode> TYPE = NodeClass.create(FloatingReadNode.class);

    @OptionalInput(Memory) MemoryNode lastLocationAccess;

    public FloatingReadNode(AddressNode address, LocationIdentity location, MemoryNode lastLocationAccess, Stamp stamp) {
        this(address, location, lastLocationAccess, stamp, null, BarrierType.NONE);
    }

    public FloatingReadNode(AddressNode address, LocationIdentity location, MemoryNode lastLocationAccess, Stamp stamp, GuardingNode guard) {
        this(address, location, lastLocationAccess, stamp, guard, BarrierType.NONE);
    }

    public FloatingReadNode(AddressNode address, LocationIdentity location, MemoryNode lastLocationAccess, Stamp stamp, GuardingNode guard, BarrierType barrierType) {
        super(TYPE, address, location, stamp, guard, barrierType);
        this.lastLocationAccess = lastLocationAccess;
    }

    @Override
    public MemoryNode getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryNode newlla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(newlla));
        lastLocationAccess = newlla;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind readKind = gen.getLIRGeneratorTool().getLIRKind(stamp());
        gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitLoad(readKind, gen.operand(address), null));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (getAddress() instanceof OffsetAddressNode) {
            OffsetAddressNode objAddress = (OffsetAddressNode) getAddress();
            if (objAddress.getBase() instanceof PiNode) {
                PiNode piBase = (PiNode) objAddress.getBase();
                /*
                 * If the Pi and the read have the same guard or the read is unguarded, use the
                 * guard of the Pi along with the original value. This encourages a canonical form
                 * guarded reads.
                 */
                if (piBase.getGuard() == getGuard() || getGuard() == null) {
                    OffsetAddressNode newAddress = new OffsetAddressNode(piBase.getOriginalNode(), objAddress.getOffset());
                    return new FloatingReadNode(newAddress, getLocationIdentity(), getLastLocationAccess(), stamp(), getGuard() == null ? piBase.getGuard() : getGuard(), getBarrierType());
                }
            }
        }
        return ReadNode.canonicalizeRead(this, getAddress(), getLocationIdentity(), tool);
    }

    @SuppressWarnings("try")
    @Override
    public FixedAccessNode asFixedNode() {
        try (DebugCloseable position = withNodeSourcePosition()) {
            return graph().add(new ReadNode(getAddress(), getLocationIdentity(), stamp(), getGuard(), getBarrierType()));
        }
    }

    @Override
    public boolean verify() {
        MemoryNode lla = getLastLocationAccess();
        assert lla != null || getLocationIdentity().isImmutable() : "lastLocationAccess of " + this + " shouldn't be null for mutable location identity " + getLocationIdentity();
        return super.verify();
    }
}
