/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.memory.address.RawAddressNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.Phase;

public class AddressLoweringPhase extends Phase {

    public abstract static class AddressLowering {

        public abstract AddressNode lower(ValueNode address);

        public abstract AddressNode lower(ValueNode base, ValueNode offset);
    }

    private final AddressLowering lowering;

    public AddressLoweringPhase(AddressLowering lowering) {
        this.lowering = lowering;
        assert lowering != null;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            AddressNode lowered;
            if (node instanceof RawAddressNode) {
                RawAddressNode address = (RawAddressNode) node;
                lowered = lowering.lower(address.getAddress());
            } else if (node instanceof OffsetAddressNode) {
                OffsetAddressNode address = (OffsetAddressNode) node;
                lowered = lowering.lower(address.getBase(), address.getOffset());
            } else {
                continue;
            }
            node.replaceAtUsages(lowered);
            GraphUtil.killWithUnusedFloatingInputs(node);
        }
    }
}
