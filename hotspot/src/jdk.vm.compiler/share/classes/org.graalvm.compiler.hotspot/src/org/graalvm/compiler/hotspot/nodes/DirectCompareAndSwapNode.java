/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Association;
import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_30;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.HotSpotNodeLIRBuilder;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.CompareAndSwapNode;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode.Address;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.word.Word;

/**
 * A special purpose store node that differs from {@link CompareAndSwapNode} in that it is not a
 * {@link StateSplit} and it {@linkplain #compareAndSwap(Address, Word, Word, LocationIdentity)}
 * returns either the expected value or the compared against value instead of a boolean.
 */
@NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_30, size = SIZE_8)
public final class DirectCompareAndSwapNode extends FixedWithNextNode implements LIRLowerable, MemoryCheckpoint.Single {

    public static final NodeClass<DirectCompareAndSwapNode> TYPE = NodeClass.create(DirectCompareAndSwapNode.class);
    @Input(Association) AddressNode address;
    @Input ValueNode expectedValue;
    @Input ValueNode newValue;

    protected final LocationIdentity locationIdentity;

    public DirectCompareAndSwapNode(ValueNode address, ValueNode expected, ValueNode newValue, LocationIdentity locationIdentity) {
        super(TYPE, expected.stamp());
        this.address = (AddressNode) address;
        this.expectedValue = expected;
        this.newValue = newValue;
        this.locationIdentity = locationIdentity;
    }

    public AddressNode getAddress() {
        return address;
    }

    public ValueNode expectedValue() {
        return expectedValue;
    }

    public ValueNode newValue() {
        return newValue;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        ((HotSpotNodeLIRBuilder) gen).visitDirectCompareAndSwap(this);
    }

    /**
     * Compares an expected value with the actual value in a location denoted by an address. Iff
     * they are same, {@code newValue} is placed into the location and the {@code expectedValue} is
     * returned. Otherwise, the actual value is returned. All of the above is performed in one
     * atomic hardware transaction.
     *
     * @param address the address to be atomically tested and updated
     * @param expectedValue if this value is currently in the field, perform the swap
     * @param newValue the new value to put into the field
     * @return either {@code expectedValue} or the actual value
     */
    @NodeIntrinsic
    public static native Word compareAndSwap(Address address, Word expectedValue, Word newValue, @ConstantNodeParameter LocationIdentity locationIdentity);
}
