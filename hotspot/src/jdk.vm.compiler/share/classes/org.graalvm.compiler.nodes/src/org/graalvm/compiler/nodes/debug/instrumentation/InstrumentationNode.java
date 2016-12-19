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
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.DeoptimizingFixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.AccessMonitorNode;
import org.graalvm.compiler.nodes.spi.VirtualizableAllocation;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

/**
 * The {@code InstrumentationNode} is a place holder of the instrumentation in the original graph.
 * It is generated at the ExtractInstrumentationPhase and substituted at the
 * InlineInstrumentationPhase. It maintains an instrumentation graph which contains the
 * instrumentation nodes in the original graph (between InstrumentationBeginNode and
 * InstrumentationEndNode). Any data dependency of the instrumentation nodes will be transformed
 * into an input to the InstrumentationNode.
 */
@NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public class InstrumentationNode extends DeoptimizingFixedWithNextNode implements VirtualizableAllocation {

    public static final NodeClass<InstrumentationNode> TYPE = NodeClass.create(InstrumentationNode.class);

    @OptionalInput(value = InputType.Unchecked) protected ValueNode target;
    @OptionalInput protected NodeInputList<ValueNode> weakDependencies;

    protected StructuredGraph instrumentationGraph;
    protected final boolean anchored;

    public InstrumentationNode(ValueNode target, boolean anchored) {
        this(target, anchored, 0, null);
    }

    private InstrumentationNode(ValueNode target, boolean anchored, int initialDependencySize, FrameState stateBefore) {
        super(TYPE, StampFactory.forVoid(), stateBefore);

        this.target = target;
        this.anchored = anchored;
        this.weakDependencies = new NodeInputList<>(this, initialDependencySize);
    }

    public ValueNode getTarget() {
        return target;
    }

    public boolean isAnchored() {
        return anchored;
    }

    public StructuredGraph getInstrumentationGraph() {
        return instrumentationGraph;
    }

    public void setInstrumentationGraph(StructuredGraph graph) {
        this.instrumentationGraph = graph;
    }

    public void addWeakDependency(ValueNode input) {
        weakDependencies.add(input);
    }

    public ValueNode getWeakDependency(int index) {
        return weakDependencies.get(index);
    }

    public NodeInputList<ValueNode> getWeakDependencies() {
        return weakDependencies;
    }

    /**
     * Clone the InstrumentationNode with the given new target. The weakDependencies will be
     * initialized with aliased nodes.
     */
    private InstrumentationNode cloneWithNewTarget(ValueNode newTarget, VirtualizerTool tool) {
        InstrumentationNode clone = new InstrumentationNode(newTarget, anchored, weakDependencies.size(), stateBefore);
        clone.instrumentationGraph = instrumentationGraph;
        for (int i = 0; i < weakDependencies.size(); i++) {
            ValueNode input = weakDependencies.get(i);
            if (!(input instanceof VirtualObjectNode)) {
                ValueNode alias = tool.getAlias(input);
                if (alias instanceof VirtualObjectNode) {
                    clone.weakDependencies.initialize(i, alias);
                    continue;
                }
            }
            clone.weakDependencies.initialize(i, input);
        }
        return clone;
    }

    private boolean hasAliasedWeakDependency(VirtualizerTool tool) {
        for (ValueNode input : weakDependencies) {
            if (!(input instanceof VirtualObjectNode)) {
                ValueNode alias = tool.getAlias(input);
                if (alias instanceof VirtualObjectNode) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        // InstrumentationNode allows non-materialized inputs. During the inlining of the
        // InstrumentationNode, non-materialized inputs will be replaced by null. The current
        // InstrumentationNode is replaced with a clone InstrumentationNode, such that the escape
        // analysis won't materialize non-materialized inputs at this point.
        InstrumentationNode replacee = null;
        if (target != null) {
            if (target instanceof AccessMonitorNode) {
                AccessMonitorNode monitor = (AccessMonitorNode) target;
                ValueNode alias = tool.getAlias(monitor.object());
                if (alias instanceof VirtualObjectNode) {
                    MonitorProxyNode proxy = new MonitorProxyNode(null, monitor.getMonitorId());
                    tool.addNode(proxy);
                    replacee = cloneWithNewTarget(proxy, tool);
                }
            } else if (!(target instanceof VirtualObjectNode)) {
                ValueNode alias = tool.getAlias(target);
                if (alias instanceof VirtualObjectNode) {
                    replacee = cloneWithNewTarget(alias, tool);
                }
            }
        }
        if (replacee == null && hasAliasedWeakDependency(tool)) {
            replacee = cloneWithNewTarget(target, tool);
        }
        // in case of modification, we replace with the clone
        if (replacee != null) {
            tool.addNode(replacee);
            tool.replaceWithValue(replacee);
        }
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

}
