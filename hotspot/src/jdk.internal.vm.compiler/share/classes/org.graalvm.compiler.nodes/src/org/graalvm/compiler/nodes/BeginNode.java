/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class BeginNode extends AbstractBeginNode implements Simplifiable {

    public static final NodeClass<BeginNode> TYPE = NodeClass.create(BeginNode.class);

    public BeginNode() {
        super(TYPE, StampFactory.forVoid());
    }

    public BeginNode(Stamp stamp) {
        super(TYPE, stamp);
    }

    public void trySimplify() {
        FixedNode prev = (FixedNode) this.predecessor();
        if (prev instanceof ControlSplitNode) {
            // This begin node is necessary.
        } else {
            // This begin node can be removed and all guards moved up to the preceding begin node.
            prepareDelete();
            graph().removeFixed(this);
        }
    }

    @Override
    public void simplify(SimplifierTool tool) {
        FixedNode prev = (FixedNode) this.predecessor();
        if (prev == null) {
            // This is the start node.
        } else if (prev instanceof ControlSplitNode) {
            // This begin node is necessary.
        } else {
            // This begin node can be removed and all guards moved up to the preceding begin node.
            prepareDelete();
            tool.addToWorkList(next());
            graph().removeFixed(this);
        }
    }

    public static AbstractBeginNode begin(FixedNode with) {
        if (with instanceof AbstractBeginNode) {
            return (AbstractBeginNode) with;
        }
        BeginNode begin = with.graph().add(new BeginNode());
        begin.setNext(with);
        return begin;
    }
}
