/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.memory.MemoryNode;
import org.graalvm.compiler.nodes.memory.MemoryPhiNode;
import jdk.internal.vm.compiler.word.LocationIdentity;

@NodeInfo(allowedUsageTypes = {InputType.Memory}, nameTemplate = "MemoryProxy({i#value})")
public final class MemoryProxyNode extends ProxyNode implements MemoryNode {

    public static final NodeClass<MemoryProxyNode> TYPE = NodeClass.create(MemoryProxyNode.class);
    @OptionalInput(InputType.Memory) MemoryNode value;
    protected final LocationIdentity locationIdentity;

    public MemoryProxyNode(MemoryNode value, LoopExitNode proxyPoint, LocationIdentity locationIdentity) {
        super(TYPE, StampFactory.forVoid(), proxyPoint);
        this.value = value;
        this.locationIdentity = locationIdentity;
    }

    public void setValue(MemoryNode newValue) {
        this.updateUsages(value.asNode(), newValue.asNode());
        this.value = newValue;
    }

    @Override
    public ValueNode value() {
        return (value == null ? null : value.asNode());
    }

    @Override
    public PhiNode createPhi(AbstractMergeNode merge) {
        return graph().addWithoutUnique(new MemoryPhiNode(merge, locationIdentity));
    }
}
