/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;
import org.graalvm.util.EconomicMap;

import jdk.vm.ci.code.BytecodeFrame;

/**
 * This phase transfers {@link FrameState} nodes from {@link StateSplit} nodes to
 * {@link DeoptimizingNode DeoptimizingNodes}.
 *
 * This allow to enter the {@link GuardsStage#AFTER_FSA AFTER_FSA} stage of the graph where no new
 * node that may cause deoptimization can be introduced anymore.
 * <p>
 * This Phase processes the graph in post order, assigning the {@link FrameState} from the last
 * {@link StateSplit} node to {@link DeoptimizingNode DeoptimizingNodes}.
 */
public class FrameStateAssignmentPhase extends Phase {

    private static class FrameStateAssignmentClosure extends NodeIteratorClosure<FrameState> {

        @Override
        protected FrameState processNode(FixedNode node, FrameState previousState) {
            FrameState currentState = previousState;
            if (node instanceof DeoptimizingNode.DeoptBefore) {
                DeoptimizingNode.DeoptBefore deopt = (DeoptimizingNode.DeoptBefore) node;
                if (deopt.canDeoptimize() && deopt.stateBefore() == null) {
                    GraalError.guarantee(currentState != null, "no FrameState at DeoptimizingNode %s", deopt);
                    deopt.setStateBefore(currentState);
                }
            }

            if (node instanceof StateSplit) {
                StateSplit stateSplit = (StateSplit) node;
                FrameState stateAfter = stateSplit.stateAfter();
                if (stateAfter != null) {
                    if (stateAfter.bci == BytecodeFrame.INVALID_FRAMESTATE_BCI) {
                        currentState = null;
                    } else {
                        currentState = stateAfter;
                    }
                    stateSplit.setStateAfter(null);
                }
            }

            if (node instanceof DeoptimizingNode.DeoptDuring) {
                DeoptimizingNode.DeoptDuring deopt = (DeoptimizingNode.DeoptDuring) node;
                if (deopt.canDeoptimize()) {
                    GraalError.guarantee(currentState != null, "no FrameState at DeoptimizingNode %s", deopt);
                    deopt.computeStateDuring(currentState);
                }
            }

            if (node instanceof DeoptimizingNode.DeoptAfter) {
                DeoptimizingNode.DeoptAfter deopt = (DeoptimizingNode.DeoptAfter) node;
                if (deopt.canDeoptimize() && deopt.stateAfter() == null) {
                    GraalError.guarantee(currentState != null, "no FrameState at DeoptimizingNode %s", deopt);
                    deopt.setStateAfter(currentState);
                }
            }

            return currentState;
        }

        @Override
        protected FrameState merge(AbstractMergeNode merge, List<FrameState> states) {
            FrameState singleFrameState = singleFrameState(states);
            return singleFrameState == null ? merge.stateAfter() : singleFrameState;
        }

        @Override
        protected FrameState afterSplit(AbstractBeginNode node, FrameState oldState) {
            return oldState;
        }

        @Override
        protected EconomicMap<LoopExitNode, FrameState> processLoop(LoopBeginNode loop, FrameState initialState) {
            return ReentrantNodeIterator.processLoop(this, loop, initialState).exitStates;
        }
    }

    @Override
    protected void run(StructuredGraph graph) {
        assert !graph.getGuardsStage().allowsFloatingGuards() && !hasFloatingDeopts(graph);
        if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
            ReentrantNodeIterator.apply(new FrameStateAssignmentClosure(), graph.start(), null);
            graph.setGuardsStage(GuardsStage.AFTER_FSA);
            graph.getNodes(FrameState.TYPE).filter(state -> state.hasNoUsages()).forEach(GraphUtil::killWithUnusedFloatingInputs);
        }
    }

    private static boolean hasFloatingDeopts(StructuredGraph graph) {
        for (Node n : graph.getNodes()) {
            if (n instanceof DeoptimizingNode && GraphUtil.isFloatingNode(n)) {
                DeoptimizingNode deoptimizingNode = (DeoptimizingNode) n;
                if (deoptimizingNode.canDeoptimize()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static FrameState singleFrameState(List<FrameState> states) {
        FrameState singleState = states.get(0);
        for (int i = 1; i < states.size(); ++i) {
            if (states.get(i) != singleState) {
                return null;
            }
        }
        if (singleState != null && singleState.bci != BytecodeFrame.INVALID_FRAMESTATE_BCI) {
            return singleState;
        }
        return null;
    }

    @Override
    public boolean checkContract() {
        // TODO GR-1409
        return false;
    }
}
