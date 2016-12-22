/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.JavaKind;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerator;
import org.graalvm.compiler.lir.StandardOp.SaveRegistersOp;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * Saves all allocatable registers.
 */
@NodeInfo(allowedUsageTypes = {InputType.Memory})
public final class SaveAllRegistersNode extends FixedWithNextNode implements LIRLowerable, MemoryCheckpoint.Single {

    public static final NodeClass<SaveAllRegistersNode> TYPE = NodeClass.create(SaveAllRegistersNode.class);
    protected SaveRegistersOp saveRegistersOp;

    public SaveAllRegistersNode() {
        super(TYPE, StampFactory.forKind(JavaKind.Long));
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        saveRegistersOp = ((HotSpotLIRGenerator) gen.getLIRGeneratorTool()).emitSaveAllRegisters();
    }

    /**
     * @return the map from registers to the stack locations in they are saved
     */
    public SaveRegistersOp getSaveRegistersOp() {
        assert saveRegistersOp != null : "saved registers op has not yet been created";
        return saveRegistersOp;
    }

    /**
     * @return a token that couples this node to an {@link UncommonTrapCallNode} so that the latter
     *         has access to the {@linkplain SaveRegistersOp#getMap register save map}
     */
    @NodeIntrinsic
    public static native long saveAllRegisters();

    @Override
    public LocationIdentity getLocationIdentity() {
        return LocationIdentity.any();
    }
}
