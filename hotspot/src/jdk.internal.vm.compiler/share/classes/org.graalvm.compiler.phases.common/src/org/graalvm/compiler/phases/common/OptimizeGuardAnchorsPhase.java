/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.AnchoringNode;
import org.graalvm.compiler.phases.Phase;

public class OptimizeGuardAnchorsPhase extends Phase {
    private static final DebugCounter counterGuardsAnchorOptimized = Debug.counter("GuardsAnchorOptimized");
    private static final DebugCounter counterGuardsOptimizedAtSplit = Debug.counter("GuardsOptimizedAtSplit");

    public static class LazyCFG extends LazyValue<ControlFlowGraph> {
        public LazyCFG(StructuredGraph graph) {
            super(() -> ControlFlowGraph.compute(graph, true, false, true, true));
        }
    }

    @Override
    protected void run(StructuredGraph graph) {
        if (!graph.getGuardsStage().allowsFloatingGuards()) {
            return;
        }
        LazyCFG cfg = new LazyCFG(graph);
        for (AbstractBeginNode begin : graph.getNodes(AbstractBeginNode.TYPE)) {
            if (!(begin instanceof StartNode || begin.predecessor() instanceof ControlSplitNode)) {
                NodeIterable<GuardNode> guards = begin.guards();
                if (guards.isNotEmpty()) {
                    AbstractBeginNode newAnchor = computeOptimalAnchor(cfg.get(), begin);
                    // newAnchor == begin is possible because postdominator computation assumes that
                    // loops never end
                    if (newAnchor != begin) {
                        for (GuardNode guard : guards.snapshot()) {
                            guard.setAnchor(newAnchor);
                        }
                        counterGuardsAnchorOptimized.increment();
                    }
                }
            }
        }
        for (ControlSplitNode controlSplit : graph.getNodes(ControlSplitNode.TYPE)) {
            optimizeAtControlSplit(controlSplit, cfg);
        }
    }

    public static AbstractBeginNode getOptimalAnchor(LazyCFG cfg, AbstractBeginNode begin) {
        if (begin instanceof StartNode || begin.predecessor() instanceof ControlSplitNode) {
            return begin;
        }
        return computeOptimalAnchor(cfg.get(), begin);
    }

    private static AbstractBeginNode computeOptimalAnchor(ControlFlowGraph cfg, AbstractBeginNode begin) {
        Block anchor = cfg.blockFor(begin);
        while (anchor.getDominator() != null && anchor.getDominator().getPostdominator() == anchor) {
            anchor = anchor.getDominator();
        }
        return anchor.getBeginNode();
    }

    private static void optimizeAtControlSplit(ControlSplitNode controlSplit, LazyCFG cfg) {
        AbstractBeginNode successor = findMinimumUsagesSuccessor(controlSplit);
        int successorCount = controlSplit.successors().count();
        for (GuardNode guard : successor.guards().snapshot()) {
            if (guard.isDeleted() || guard.getCondition().getUsageCount() < successorCount) {
                continue;
            }
            List<GuardNode> otherGuards = new ArrayList<>(successorCount - 1);
            HashSet<Node> successorsWithoutGuards = new HashSet<>(controlSplit.successors().count());
            controlSplit.successors().snapshotTo(successorsWithoutGuards);
            successorsWithoutGuards.remove(guard.getAnchor());
            for (GuardNode conditonGuard : guard.getCondition().usages().filter(GuardNode.class)) {
                if (conditonGuard != guard) {
                    AnchoringNode conditionGuardAnchor = conditonGuard.getAnchor();
                    if (conditionGuardAnchor.asNode().predecessor() == controlSplit && compatibleGuards(guard, conditonGuard)) {
                        otherGuards.add(conditonGuard);
                        successorsWithoutGuards.remove(conditionGuardAnchor);
                    }
                }
            }

            if (successorsWithoutGuards.isEmpty()) {
                assert otherGuards.size() >= successorCount - 1;
                AbstractBeginNode anchor = computeOptimalAnchor(cfg.get(), AbstractBeginNode.prevBegin(controlSplit));
                GuardNode newGuard = controlSplit.graph().unique(new GuardNode(guard.getCondition(), anchor, guard.getReason(), guard.getAction(), guard.isNegated(), guard.getSpeculation()));
                for (GuardNode otherGuard : otherGuards) {
                    otherGuard.replaceAndDelete(newGuard);
                }
                guard.replaceAndDelete(newGuard);
                counterGuardsOptimizedAtSplit.increment();
            }
            otherGuards.clear();
        }
    }

    private static boolean compatibleGuards(GuardNode guard, GuardNode conditonGuard) {
        return conditonGuard.isNegated() == guard.isNegated() && conditonGuard.getAction() == guard.getAction() && conditonGuard.getReason() == guard.getReason() &&
                        conditonGuard.getSpeculation().equals(guard.getSpeculation());
    }

    private static AbstractBeginNode findMinimumUsagesSuccessor(ControlSplitNode controlSplit) {
        Iterator<Node> successors = controlSplit.successors().iterator();
        AbstractBeginNode min = (AbstractBeginNode) successors.next();
        int minUsages = min.getUsageCount();
        while (successors.hasNext()) {
            AbstractBeginNode successor = (AbstractBeginNode) successors.next();
            int count = successor.getUsageCount();
            if (count < minUsages) {
                minUsages = count;
                min = successor;
            }
        }
        return min;
    }
}
