/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.MapCursor;
import org.graalvm.compiler.graph.NodeStack;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractDeoptimizeNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

/**
 * This phase will make sure that the branch leading towards this deopt has 0.0 probability.
 *
 */
public class PropagateDeoptimizeProbabilityPhase extends BasePhase<PhaseContext> {

    @Override
    @SuppressWarnings("try")
    protected void run(final StructuredGraph graph, PhaseContext context) {
        assert !graph.hasValueProxies() : "ConvertDeoptimizeToGuardPhase always creates proxies";

        if (graph.hasNode(AbstractDeoptimizeNode.TYPE)) {

            NodeStack stack = new NodeStack();
            EconomicMap<ControlSplitNode, EconomicSet<AbstractBeginNode>> reachableSplits = EconomicMap.create();

            // Mark all control flow nodes that are post-dominated by a deoptimization.
            for (AbstractDeoptimizeNode d : graph.getNodes(AbstractDeoptimizeNode.TYPE)) {
                stack.push(AbstractBeginNode.prevBegin(d));
                while (!stack.isEmpty()) {
                    AbstractBeginNode beginNode = (AbstractBeginNode) stack.pop();
                    FixedNode fixedNode = (FixedNode) beginNode.predecessor();

                    if (fixedNode == null) {
                        // Can happen for start node.
                    } else if (fixedNode instanceof AbstractMergeNode) {
                        AbstractMergeNode mergeNode = (AbstractMergeNode) fixedNode;
                        for (AbstractEndNode end : mergeNode.forwardEnds()) {
                            AbstractBeginNode newBeginNode = AbstractBeginNode.prevBegin(end);
                            stack.push(newBeginNode);
                        }
                    } else if (fixedNode instanceof ControlSplitNode) {
                        ControlSplitNode controlSplitNode = (ControlSplitNode) fixedNode;
                        EconomicSet<AbstractBeginNode> reachableSuccessors = reachableSplits.get(controlSplitNode);
                        if (reachableSuccessors == null) {
                            reachableSuccessors = EconomicSet.create();
                            reachableSplits.put(controlSplitNode, reachableSuccessors);
                        }

                        if (controlSplitNode.getSuccessorCount() == reachableSuccessors.size() - 1) {
                            // All successors of this split lead to deopt, propagate reachability
                            // further upwards.
                            reachableSplits.removeKey(controlSplitNode);
                            stack.push(AbstractBeginNode.prevBegin((FixedNode) controlSplitNode.predecessor()));
                        } else {
                            reachableSuccessors.add(beginNode);
                        }
                    } else {
                        stack.push(AbstractBeginNode.prevBegin(fixedNode));
                    }
                }
            }

            // Make sure the probability on the path towards the deoptimization is 0.0.
            MapCursor<ControlSplitNode, EconomicSet<AbstractBeginNode>> entries = reachableSplits.getEntries();
            while (entries.advance()) {
                ControlSplitNode controlSplitNode = entries.getKey();
                EconomicSet<AbstractBeginNode> value = entries.getValue();
                for (AbstractBeginNode begin : value) {
                    double probability = controlSplitNode.probability(begin);
                    if (probability != 0.0) {
                        controlSplitNode.setProbability(begin, 0.0);
                    }
                }
            }
        }
    }
}
