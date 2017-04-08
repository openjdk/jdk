/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.debug.instrumentation;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.java.RawMonitorEnterNode;

/**
 * The {@code MonitorProxyNode} represents the InstrumentationNode's target when the original
 * MonitorEnterNode is invalid. Such situation occurs when the escape analysis removes the
 * MonitorEnterNode and aggregates the monitor logic in a CommitAllocationNode.
 */
@NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public class MonitorProxyNode extends FloatingNode {

    public static final NodeClass<MonitorProxyNode> TYPE = NodeClass.create(MonitorProxyNode.class);

    @OptionalInput(value = InputType.Unchecked) protected ValueNode object;
    @OptionalInput(value = InputType.Association) protected MonitorIdNode monitorId;

    public MonitorProxyNode(ValueNode object, MonitorIdNode monitorId) {
        super(TYPE, StampFactory.forVoid());
        this.object = object;
        this.monitorId = monitorId;
    }

    public ValueNode object() {
        return object;
    }

    public MonitorIdNode getMonitorId() {
        return monitorId;
    }

    /**
     * @return the first RawMonitorEnterNode that shares the same MonitorIdNode and the same lock
     *         object with this MonitorProxyNode.
     */
    public RawMonitorEnterNode findFirstMatch() {
        for (RawMonitorEnterNode monitorEnter : monitorId.usages().filter(RawMonitorEnterNode.class)) {
            if (monitorEnter.object() == object) {
                return monitorEnter;
            }
        }
        return null;
    }

}
