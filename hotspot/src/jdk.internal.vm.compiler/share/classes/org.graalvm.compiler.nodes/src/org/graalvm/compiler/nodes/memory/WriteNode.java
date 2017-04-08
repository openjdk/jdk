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

import static org.graalvm.compiler.nodeinfo.InputType.Guard;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode.Address;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;

/**
 * Writes a given {@linkplain #value() value} a {@linkplain FixedAccessNode memory location}.
 */
@NodeInfo(nameTemplate = "Write#{p#location/s}")
public class WriteNode extends AbstractWriteNode implements LIRLowerable, Simplifiable, Virtualizable {

    public static final NodeClass<WriteNode> TYPE = NodeClass.create(WriteNode.class);

    @OptionalInput(Guard) protected GuardingNode storeCheckGuard;

    protected WriteNode(ValueNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
        this((AddressNode) address, location, value, barrierType);
    }

    public WriteNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
        super(TYPE, address, location, value, barrierType);
    }

    public WriteNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, boolean initialization) {
        super(TYPE, address, location, value, barrierType, initialization);
    }

    public WriteNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, GuardingNode guard, boolean initialization) {
        this(TYPE, address, location, value, barrierType, guard, initialization);
    }

    protected WriteNode(NodeClass<? extends WriteNode> c, AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, GuardingNode guard, boolean initialization) {
        super(c, address, location, value, barrierType, guard, initialization);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind writeKind = gen.getLIRGeneratorTool().getLIRKind(value().stamp());
        gen.getLIRGeneratorTool().getArithmetic().emitStore(writeKind, gen.operand(address), gen.operand(value()), gen.state(this));
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (getAddress() instanceof OffsetAddressNode) {
            OffsetAddressNode objAddress = (OffsetAddressNode) getAddress();
            if (objAddress.getBase() instanceof PiNode && ((PiNode) objAddress.getBase()).getGuard() == getGuard()) {
                OffsetAddressNode newAddress = graph().unique(new OffsetAddressNode(((PiNode) objAddress.getBase()).getOriginalNode(), objAddress.getOffset()));
                setAddress(newAddress);
                tool.addToWorkList(newAddress);
            }
        }
    }

    @NodeIntrinsic
    public static native void writeMemory(Address address, @ConstantNodeParameter LocationIdentity location, Object value, @ConstantNodeParameter BarrierType barrierType);

    @Override
    public void virtualize(VirtualizerTool tool) {
        throw GraalError.shouldNotReachHere("unexpected WriteNode before PEA");
    }

    @Override
    public boolean canNullCheck() {
        return true;
    }

    public void setStoreCheckGuard(GuardingNode newStoreCheckGuard) {
        updateUsages((Node) this.storeCheckGuard, (Node) newStoreCheckGuard);
        this.storeCheckGuard = newStoreCheckGuard;
    }
}
