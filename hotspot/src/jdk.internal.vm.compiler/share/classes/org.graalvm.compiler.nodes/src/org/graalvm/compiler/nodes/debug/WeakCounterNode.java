/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.debug;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

/**
 * This is a special version of the dynamic counter node that removes itself as soon as it's the
 * only usage of the associated node. This way it only increments the counter if the node is
 * actually executed.
 */
@NodeInfo
public final class WeakCounterNode extends DynamicCounterNode implements Simplifiable, Virtualizable {

    public static final NodeClass<WeakCounterNode> TYPE = NodeClass.create(WeakCounterNode.class);
    @Input ValueNode checkedValue;

    public WeakCounterNode(String group, String name, ValueNode increment, boolean addContext, ValueNode checkedValue) {
        super(TYPE, group, name, increment, addContext);
        this.checkedValue = checkedValue;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (checkedValue instanceof FloatingNode && checkedValue.getUsageCount() == 1) {
            tool.addToWorkList(checkedValue);
            graph().removeFixed(this);
        }
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(checkedValue);
        if (alias instanceof VirtualObjectNode) {
            tool.delete();
        }
    }

    public static void addCounterBefore(String group, String name, long increment, boolean addContext, ValueNode checkedValue, FixedNode position) {
        StructuredGraph graph = position.graph();
        WeakCounterNode counter = graph.add(new WeakCounterNode(name, group, ConstantNode.forLong(increment, graph), addContext, checkedValue));
        graph.addBeforeFixed(position, counter);
    }
}
