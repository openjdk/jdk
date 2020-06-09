/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Red Hat Inc. All rights reserved.
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
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.internal.vm.compiler.word.LocationIdentity;

@NodeInfo(nameTemplate = "VolatileRead#{p#location/s}", allowedUsageTypes = Memory, cycles = CYCLES_2, size = SIZE_1)
public class VolatileReadNode extends ReadNode implements SingleMemoryKill, Lowerable, Simplifiable {
    public static final NodeClass<VolatileReadNode> TYPE = NodeClass.create(VolatileReadNode.class);

    public VolatileReadNode(AddressNode address, LocationIdentity location, Stamp stamp, BarrierType barrierType) {
        super(TYPE, address, location, stamp, null, barrierType, false, null);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (lastLocationAccess != null && hasOnlyUsagesOfType(Memory)) {
            replaceAtUsages(lastLocationAccess.asNode(), Memory);
            assert hasNoUsages();
            graph().removeFixed(this);
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind readKind = gen.getLIRGeneratorTool().getLIRKind(getAccessStamp(NodeView.DEFAULT));
        gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitVolatileLoad(readKind, gen.operand(address), gen.state(this)));
    }

    @SuppressWarnings("try")
    @Override
    public FloatingAccessNode asFloatingNode() {
        throw new RuntimeException();
    }

    @Override
    public boolean canFloat() {
        return false;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public boolean canNullCheck() {
        return false;
    }

}
