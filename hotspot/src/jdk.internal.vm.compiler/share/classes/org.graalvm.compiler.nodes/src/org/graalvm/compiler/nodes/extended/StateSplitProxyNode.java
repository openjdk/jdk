/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;

/**
 * This node provides a state split along with the functionality of {@link FixedValueAnchorNode}.
 * This is used to capture a state for deoptimization when a node has side effects which aren't
 * easily represented. The anchored value is usually part of the FrameState since this forces uses
 * of the value below this node so they will consume this frame state instead of an earlier one.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class StateSplitProxyNode extends FixedValueAnchorNode implements Canonicalizable, StateSplit {

    public static final NodeClass<StateSplitProxyNode> TYPE = NodeClass.create(StateSplitProxyNode.class);

    @OptionalInput(InputType.State) FrameState stateAfter;
    /**
     * Disallows elimination of this node until after the FrameState has been consumed.
     */
    private final boolean delayElimination;

    public StateSplitProxyNode(ValueNode object) {
        this(object, false);
    }

    public StateSplitProxyNode(ValueNode object, boolean delayElimination) {
        super(TYPE, object);
        this.delayElimination = delayElimination;
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

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (object.isConstant() && !delayElimination || stateAfter == null) {
            return object;
        }
        return this;
    }

}
