/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import jdk.internal.vm.compiler.word.LocationIdentity;

@NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public class SnippetLowerableMemoryNode extends FixedWithNextNode implements Lowerable, MemoryAccess {
    public static final NodeClass<SnippetLowerableMemoryNode> TYPE = NodeClass.create(SnippetLowerableMemoryNode.class);

    public interface SnippetLowering {
        void lower(SnippetLowerableMemoryNode node, LoweringTool tool);
    }

    @Input protected NodeInputList<ValueNode> arguments;
    @OptionalInput(InputType.Memory) protected MemoryNode lastLocationAccess;
    private final LocationIdentity locationIdentity;
    SnippetLowering lowering;

    public SnippetLowerableMemoryNode(SnippetLowering lowering, LocationIdentity locationIdentity, Stamp stamp, ValueNode... arguments) {
        super(TYPE, stamp);
        this.arguments = new NodeInputList<>(this, arguments);
        this.lowering = lowering;
        this.locationIdentity = locationIdentity;
    }

    public ValueNode getArgument(int i) {
        return arguments.get(i);
    }

    public int getArgumentCount() {
        return arguments.size();
    }

    @Override
    public void lower(LoweringTool tool) {
        lowering.lower(this, tool);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public MemoryNode getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryNode lla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(lla));
        lastLocationAccess = lla;
    }
}
