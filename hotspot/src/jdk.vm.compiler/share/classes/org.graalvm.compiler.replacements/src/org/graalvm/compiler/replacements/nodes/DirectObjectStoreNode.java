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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_3;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.extended.UnsafeStoreNode;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;

import jdk.vm.ci.meta.JavaKind;

/**
 * A special purpose store node that differs from {@link UnsafeStoreNode} in that it is not a
 * {@link StateSplit} and does not include a write barrier. Note that contrary to the sound of the
 * name this node can be used for storing any kind.
 */
@NodeInfo(cycles = CYCLES_3, size = SIZE_1)
public final class DirectObjectStoreNode extends FixedWithNextNode implements Lowerable {

    public static final NodeClass<DirectObjectStoreNode> TYPE = NodeClass.create(DirectObjectStoreNode.class);
    @Input ValueNode object;
    @Input ValueNode value;
    @Input ValueNode offset;
    protected final int displacement;
    protected final LocationIdentity locationIdentity;
    protected final JavaKind storeKind;

    public DirectObjectStoreNode(ValueNode object, int displacement, ValueNode offset, ValueNode value, LocationIdentity locationIdentity, JavaKind storeKind) {
        super(TYPE, StampFactory.forVoid());
        this.object = object;
        this.value = value;
        this.offset = offset;
        this.displacement = displacement;
        this.locationIdentity = locationIdentity;
        this.storeKind = storeKind;
    }

    @NodeIntrinsic
    public static native void storeObject(Object obj, @ConstantNodeParameter int displacement, long offset, Object value, @ConstantNodeParameter LocationIdentity locationIdentity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeBoolean(Object obj, @ConstantNodeParameter int displacement, long offset, boolean value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeByte(Object obj, @ConstantNodeParameter int displacement, long offset, byte value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeChar(Object obj, @ConstantNodeParameter int displacement, long offset, char value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeShort(Object obj, @ConstantNodeParameter int displacement, long offset, short value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeInt(Object obj, @ConstantNodeParameter int displacement, long offset, int value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeLong(Object obj, @ConstantNodeParameter int displacement, long offset, long value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeFloat(Object obj, @ConstantNodeParameter int displacement, long offset, float value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @NodeIntrinsic
    public static native void storeDouble(Object obj, @ConstantNodeParameter int displacement, long offset, double value, @ConstantNodeParameter LocationIdentity locationIdenity,
                    @ConstantNodeParameter JavaKind storeKind);

    @Override
    public void lower(LoweringTool tool) {
        ValueNode off = graph().unique(new AddNode(offset, ConstantNode.forIntegerStamp(offset.stamp(), displacement, graph())));
        AddressNode address = graph().unique(new OffsetAddressNode(object, off));
        JavaWriteNode write = graph().add(new JavaWriteNode(storeKind, address, locationIdentity, value, BarrierType.NONE, storeKind == JavaKind.Object, false));
        graph().replaceFixedWithFixed(this, write);

        tool.getLowerer().lower(write, tool);
    }
}
