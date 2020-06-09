/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;

@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = SIZE_8)
public abstract class BasicObjectCloneNode extends MacroStateSplitNode implements ObjectClone {

    public static final NodeClass<BasicObjectCloneNode> TYPE = NodeClass.create(BasicObjectCloneNode.class);

    public BasicObjectCloneNode(NodeClass<? extends MacroNode> c, MacroParams p) {
        super(c, p);
        updateStamp(computeStamp(getObject()));
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(stamp.improveWith(computeStamp(getObject())));
    }

    @Override
    public ValueNode getObject() {
        return arguments.get(0);
    }
}
