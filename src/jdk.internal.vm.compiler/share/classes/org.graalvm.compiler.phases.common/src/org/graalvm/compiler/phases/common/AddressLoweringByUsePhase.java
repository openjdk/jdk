/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, Red Hat Inc. All rights reserved.
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

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PrefetchAllocateNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.memory.AbstractWriteNode;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.Phase;

import jdk.vm.ci.meta.JavaKind;

/**
 * Created by adinn on 09/05/17.
 */
public class AddressLoweringByUsePhase extends Phase {
    public abstract static class AddressLoweringByUse {

        public abstract AddressNode lower(ValueNode use, Stamp stamp, AddressNode address);

        public abstract AddressNode lower(AddressNode address);
    }

    private final AddressLoweringByUse lowering;

    public AddressLoweringByUsePhase(AddressLoweringByUse lowering) {
        this.lowering = lowering;
        assert lowering != null;
    }

    @Override
    protected void run(StructuredGraph graph) {
        // first replace address nodes hanging off known usages
        for (Node node : graph.getNodes()) {
            AddressNode address;
            AddressNode lowered;
            if (node instanceof ReadNode) {
                ReadNode readNode = (ReadNode) node;
                Stamp stamp = readNode.stamp(NodeView.DEFAULT);
                address = readNode.getAddress();
                lowered = lowering.lower(readNode, stamp, address);
            } else if (node instanceof JavaReadNode) {
                JavaReadNode javaReadNode = (JavaReadNode) node;
                Stamp stamp = javaReadNode.stamp(NodeView.DEFAULT);
                address = javaReadNode.getAddress();
                lowered = lowering.lower(javaReadNode, stamp, address);
            } else if (node instanceof FloatingReadNode) {
                FloatingReadNode floatingReadNode = (FloatingReadNode) node;
                Stamp stamp = floatingReadNode.stamp(NodeView.DEFAULT);
                address = floatingReadNode.getAddress();
                lowered = lowering.lower(floatingReadNode, stamp, address);
            } else if (node instanceof AbstractWriteNode) {
                AbstractWriteNode abstractWriteNode = (AbstractWriteNode) node;
                Stamp stamp = abstractWriteNode.value().stamp(NodeView.DEFAULT);
                address = abstractWriteNode.getAddress();
                lowered = lowering.lower(abstractWriteNode, stamp, address);
            } else if (node instanceof PrefetchAllocateNode) {
                PrefetchAllocateNode prefetchAllocateNode = (PrefetchAllocateNode) node;
                Stamp stamp = StampFactory.forKind(JavaKind.Object);
                address = (AddressNode) prefetchAllocateNode.inputs().first();
                lowered = lowering.lower(prefetchAllocateNode, stamp, address);
            } else {
                continue;
            }
            // the lowered address amy already be a replacement
            // in which case we want to use it not delete it!
            if (lowered != address) {
                // replace original with lowered at this usage only
                // n.b. lowered is added unique so repeat lowerings will elide
                node.replaceFirstInput(address, lowered);
                // if that was the last reference we can kill the old (dead) node
                if (address.hasNoUsages()) {
                    GraphUtil.killWithUnusedFloatingInputs(address);
                }
            }
        }

        // now replace any remaining unlowered address nodes
        for (Node node : graph.getNodes()) {
            AddressNode lowered;
            if (node instanceof OffsetAddressNode) {
                AddressNode address = (AddressNode) node;
                lowered = lowering.lower(address);
            } else {
                continue;
            }
            // will always be a new AddresNode
            node.replaceAtUsages(lowered);
            GraphUtil.killWithUnusedFloatingInputs(node);
        }
    }
}
