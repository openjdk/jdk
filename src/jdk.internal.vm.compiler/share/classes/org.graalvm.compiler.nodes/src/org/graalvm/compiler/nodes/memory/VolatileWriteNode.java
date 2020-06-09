/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.internal.vm.compiler.word.LocationIdentity;

@NodeInfo(nameTemplate = "VolatileWrite#{p#location/s}")
public class VolatileWriteNode extends WriteNode implements Lowerable {
    public static final NodeClass<VolatileWriteNode> TYPE = NodeClass.create(VolatileWriteNode.class);

    public VolatileWriteNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
        super(TYPE, address, location, value, barrierType);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind writeKind = gen.getLIRGeneratorTool().getLIRKind(value().stamp(NodeView.DEFAULT));
        gen.getLIRGeneratorTool().getArithmetic().emitVolatileStore(writeKind, gen.operand(address), gen.operand(value()), gen.state(this));
    }

    @Override
    public boolean canNullCheck() {
        return false;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        return this;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

}
