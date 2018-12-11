/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;

/**
 * Base class for {@link AbstractBeginNode}s that are associated with a frame state.
 *
 * TODO (dnsimon) this not needed until {@link AbstractBeginNode} no longer implements
 * {@link StateSplit} which is not possible until loop peeling works without requiring begin nodes
 * to have frames states.
 */
@NodeInfo
public abstract class BeginStateSplitNode extends AbstractBeginNode implements StateSplit {

    public static final NodeClass<BeginStateSplitNode> TYPE = NodeClass.create(BeginStateSplitNode.class);
    @OptionalInput(InputType.State) protected FrameState stateAfter;

    protected BeginStateSplitNode(NodeClass<? extends BeginStateSplitNode> c) {
        super(c);
    }

    protected BeginStateSplitNode(NodeClass<? extends BeginStateSplitNode> c, Stamp stamp) {
        super(c, stamp);
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    /**
     * A begin node has no side effect.
     */
    @Override
    public boolean hasSideEffect() {
        return false;
    }
}
