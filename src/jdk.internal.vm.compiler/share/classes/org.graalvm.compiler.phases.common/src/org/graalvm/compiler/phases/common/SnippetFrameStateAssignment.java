/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.MapCursor;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator.LoopInfo;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator;
import org.graalvm.compiler.phases.graph.ReentrantNodeIterator.NodeIteratorClosure;

/**
 * Utility class for snippet lowering.
 *
 * Certain nodes in Graal IR can be lowered to a (sub) graph of nodes by a process called snippet
 * lowering. For details see {@linkplain Snippet}.
 *
 * For example, a snippet lowering can create a merge node without a frame state. Any deoptimization
 * point dominated by this merge will be missing frame state information since we cannot decide
 * which frame state to use for the deoptimization point. See {@link GraphUtil#mayRemoveSplit} for
 * more details. The same applies for loop exit nodes.
 *
 * This utility determines which frame state can be assigned to each node in a snippet graph.
 *
 * During lowering a node is replaced with the snippet which means there are only 2 possible states
 * that can be used inside the snippet graph: the before frame state of the snippet lowered node and
 * the after state. Generally, if a side-effect is happening inside a snippet all code after that
 * particular side-effect must not deopt to the before state but only to the after state. All code
 * before the side-effect is allowed to use the before state
 */
public class SnippetFrameStateAssignment {

    /**
     * Possible states to be used inside a snippet.
     */
    public enum NodeStateAssignment {
        /**
         * The frame state before the snippet replacee.
         */
        BEFORE_BCI,
        /**
         * The frame state after the snippet replacee.
         */
        AFTER_BCI,
        /**
         * An invalid state setup (e.g. multiple subsequent effects inside a snippet)for a
         * side-effecting node inside a snippet.
         */
        INVALID
    }

    /**
     * The iterator below visits a compiler graph in reverse post order and, based on the
     * side-effecting nodes, decides which state can be used after snippet lowering for a merge or
     * loop exit node.
     */
    public static class SnippetFrameStateAssignmentClosure extends NodeIteratorClosure<NodeStateAssignment> {

        private final NodeMap<NodeStateAssignment> stateMapping;

        public NodeMap<NodeStateAssignment> getStateMapping() {
            return stateMapping;
        }

        /**
         * Debugging flag to run the phase again on error to find specific invalid nodes.
         */
        private static final boolean RUN_WITH_LOG_ON_ERROR = false;

        public boolean verify() {
            MapCursor<Node, NodeStateAssignment> stateAssignments = stateMapping.getEntries();
            while (stateAssignments.advance()) {
                Node nodeWithState = stateAssignments.getKey();
                NodeStateAssignment fsRequirements = stateAssignments.getValue();
                switch (fsRequirements) {
                    case INVALID:
                        if (RUN_WITH_LOG_ON_ERROR) {
                            ReentrantNodeIterator.apply(new SnippetFrameStateAssignmentClosure((StructuredGraph) nodeWithState.graph(), true),
                                            ((StructuredGraph) nodeWithState.graph()).start(), NodeStateAssignment.BEFORE_BCI);
                        }
                        throw GraalError.shouldNotReachHere(
                                        "Invalid snippet replacing a node before FS assignment with node " + nodeWithState + " for graph " + nodeWithState.graph() + " other assignments=" +
                                                        stateMapping);
                    default:
                        break;
                }
            }
            return true;
        }

        /**
         * Flag to enable logging of invalid state assignments during processing.
         */
        private final boolean logOnInvalid;

        public SnippetFrameStateAssignmentClosure(StructuredGraph graph) {
            this(graph, false);
        }

        public SnippetFrameStateAssignmentClosure(StructuredGraph graph, boolean logOnInvalid) {
            stateMapping = new NodeMap<>(graph);
            this.logOnInvalid = logOnInvalid;
        }

        @Override
        protected NodeStateAssignment processNode(FixedNode node, NodeStateAssignment stateAssignment) {
            NodeStateAssignment nextStateAssignment = stateAssignment;
            if (node instanceof LoopExitNode) {
                stateMapping.put(node, stateAssignment);
            }
            if (node instanceof StateSplit && ((StateSplit) node).hasSideEffect() && !(node instanceof StartNode || node instanceof AbstractMergeNode)) {
                if (stateAssignment == NodeStateAssignment.BEFORE_BCI) {
                    nextStateAssignment = NodeStateAssignment.AFTER_BCI;
                } else {
                    if (logOnInvalid) {
                        node.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "Node %s creating invalid assignment", node);
                    }
                    nextStateAssignment = NodeStateAssignment.INVALID;
                }
            }
            return nextStateAssignment;
        }

        @Override
        protected NodeStateAssignment merge(AbstractMergeNode merge, List<NodeStateAssignment> states) {
            /*
             * The state at a merge is either the before or after state, but if multiple effects
             * exist preceding a merge we must have a merged state, and this state can only differ
             * in its return values. If such a snippet is encountered the subsequent logic will
             * assign an invalid state to the merge.
             */
            int beforeCount = 0;
            int afterCount = 0;
            int invalidCount = 0;

            for (int i = 0; i < states.size(); i++) {
                if (states.get(i) == NodeStateAssignment.BEFORE_BCI) {
                    beforeCount++;
                } else if (states.get(i) == NodeStateAssignment.AFTER_BCI) {
                    afterCount++;
                } else {
                    invalidCount++;
                }
            }
            if (invalidCount > 0) {
                stateMapping.put(merge, NodeStateAssignment.INVALID);
                return NodeStateAssignment.INVALID;
            } else {
                if (afterCount == 0) {
                    // only before states
                    assert beforeCount == states.size();
                    stateMapping.put(merge, NodeStateAssignment.BEFORE_BCI);
                    return NodeStateAssignment.BEFORE_BCI;
                } else {
                    // some before, and at least one after
                    assert afterCount > 0;
                    stateMapping.put(merge, NodeStateAssignment.AFTER_BCI);
                    return NodeStateAssignment.AFTER_BCI;
                }
            }

        }

        @Override
        protected NodeStateAssignment afterSplit(AbstractBeginNode node, NodeStateAssignment oldState) {
            return oldState;
        }

        @Override
        protected EconomicMap<LoopExitNode, NodeStateAssignment> processLoop(LoopBeginNode loop, NodeStateAssignment initialState) {
            LoopInfo<NodeStateAssignment> loopInfo = ReentrantNodeIterator.processLoop(this, loop, initialState);
            int afterCount = 0;
            int invalidCount = 0;
            for (LoopEndNode loopEnd : loop.loopEnds()) {
                if (loopInfo.endStates.get(loopEnd) == NodeStateAssignment.INVALID) {
                    invalidCount++;
                } else if (loopInfo.endStates.get(loopEnd) == NodeStateAssignment.AFTER_BCI) {
                    afterCount++;
                }
            }
            if (invalidCount > 0) {
                stateMapping.put(loop, NodeStateAssignment.INVALID);
            } else {
                if (afterCount > 0) {
                    stateMapping.put(loop, NodeStateAssignment.AFTER_BCI);
                } else {
                    stateMapping.put(loop, NodeStateAssignment.BEFORE_BCI);
                }
            }
            return loopInfo.exitStates;
        }

    }
}
