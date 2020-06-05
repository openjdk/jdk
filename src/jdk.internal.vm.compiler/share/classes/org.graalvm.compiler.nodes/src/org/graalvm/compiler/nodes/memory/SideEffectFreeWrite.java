/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import jdk.internal.vm.compiler.word.LocationIdentity;

/**
 * This is a special form of write node that does not have a side effect to the interpreter, i.e.,
 * it does not modify memory that is visible to other threads or modifies state beyond what is
 * captured in {@link FrameState} nodes. Thus is should only be used with caution in suitable
 * scenarios.
 */
@NodeInfo(nameTemplate = "SideEffectFreeWrite#{p#location/s}")
public class SideEffectFreeWrite extends WriteNode {

    public static final NodeClass<SideEffectFreeWrite> TYPE = NodeClass.create(SideEffectFreeWrite.class);

    public SideEffectFreeWrite(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
        super(TYPE, address, location, value, barrierType);
    }

    public static WriteNode createWithoutSideEffect(AddressNode address, LocationIdentity location, ValueNode value) {
        return new SideEffectFreeWrite(address, location, value, BarrierType.NONE);
    }

    @Override
    public boolean hasSideEffect() {
        return false;
    }
}
