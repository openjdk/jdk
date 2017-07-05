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
package org.graalvm.compiler.phases.common.instrumentation;

import static org.graalvm.compiler.core.common.CompilationIdentifier.INVALID_COMPILATION_ID;

import java.util.Collections;
import java.util.Map;

import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.NodeFlood;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractLocalNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.debug.instrumentation.InstrumentationBeginNode;
import org.graalvm.compiler.nodes.debug.instrumentation.InstrumentationEndNode;
import org.graalvm.compiler.nodes.debug.instrumentation.InstrumentationNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.EscapeObjectState;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;

/**
 * The {@code ExtractInstrumentationPhase} extracts the instrumentation (whose boundary are
 * indicated by instrumentationBegin and instrumentationEnd), and insert an
 * {@link InstrumentationNode} in the graph to take place of the instrumentation.
 */
public class ExtractInstrumentationPhase extends BasePhase<HighTierContext> {

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        for (InstrumentationBeginNode begin : graph.getNodes().filter(InstrumentationBeginNode.class)) {
            Instrumentation instrumentation = new Instrumentation(begin);
            if (begin.isAnchored() || begin.getTarget() != null) {
                // we create InstrumentationNode when the instrumentation is anchored (when 0 is
                // passed to instrumentationBegin), or when the instrumentation is associated with
                // some target.
                InstrumentationNode instrumentationNode = graph.addWithoutUnique(new InstrumentationNode(begin.getTarget(), begin.isAnchored()));
                graph.addBeforeFixed(begin, instrumentationNode);
                FrameState currentState = begin.stateAfter();
                FrameState newState = graph.addWithoutUnique(new FrameState(currentState.outerFrameState(), currentState.getCode(), currentState.bci, 0, 0,
                                0, currentState.rethrowException(), currentState.duringCall(), null,
                                Collections.<EscapeObjectState> emptyList()));
                instrumentationNode.setStateBefore(newState);

                StructuredGraph instrumentationGraph = instrumentation.genInstrumentationGraph(graph, instrumentationNode);
                new DeadCodeEliminationPhase().apply(instrumentationGraph, false);
                instrumentationNode.setInstrumentationGraph(instrumentationGraph);
                Debug.dump(Debug.INFO_LOG_LEVEL, instrumentationGraph, "After extracted instrumentation at %s", instrumentation);
            }
            instrumentation.unlink();
        }
    }

    /**
     * This class denotes the instrumentation code being detached from the graph.
     */
    private static class Instrumentation {

        private InstrumentationBeginNode begin;
        private InstrumentationEndNode end;
        private NodeBitMap nodes;

        Instrumentation(InstrumentationBeginNode begin) {
            this.begin = begin;

            // travel along the control flow for the paired InstrumentationEndNode
            NodeFlood cfgFlood = begin.graph().createNodeFlood();
            cfgFlood.add(begin.next());
            for (Node current : cfgFlood) {
                if (current instanceof InstrumentationEndNode) {
                    this.end = (InstrumentationEndNode) current;
                } else if (current instanceof LoopEndNode) {
                    // do nothing
                } else if (current instanceof AbstractEndNode) {
                    cfgFlood.add(((AbstractEndNode) current).merge());
                } else {
                    cfgFlood.addAll(current.successors());
                }
            }

            if (this.end == null) {
                // this may be caused by DeoptimizationReason.Unresolved
                throw GraalError.shouldNotReachHere("could not find invocation to instrumentationEnd()");
            }

            // all FloatingNodes (except AbstractLocalNodes), which the FixedNodes in the
            // instrumentation depend on, are included in the instrumentation if they are not used
            // by other nodes in the graph
            NodeBitMap cfgNodes = cfgFlood.getVisited();
            NodeFlood dfgFlood = begin.graph().createNodeFlood();
            dfgFlood.addAll(cfgNodes);
            dfgFlood.add(begin.stateAfter());
            for (Node current : dfgFlood) {
                for (Position pos : current.inputPositions()) {
                    Node input = pos.get(current);
                    if (pos.getInputType() == InputType.Value) {
                        if (current instanceof FrameState) {
                            // don't include value input for the FrameState
                            continue;
                        }
                        if (!(input instanceof FloatingNode)) {
                            // we only consider FloatingNode for this input type
                            continue;
                        }
                        if (input instanceof AbstractLocalNode) {
                            // AbstractLocalNode is invalid in the instrumentation sub-graph
                            continue;
                        }
                        if (shouldIncludeValueInput((FloatingNode) input, cfgNodes)) {
                            dfgFlood.add(input);
                        }
                    } else {
                        dfgFlood.add(input);
                    }
                }
            }
            this.nodes = dfgFlood.getVisited();
        }

        /**
         * Copy the instrumentation nodes into a separate graph. During the copying, this method
         * updates the input of the given InstrumentationNode. Hence, it is essential that the given
         * InstrumentationNode is alive.
         */
        StructuredGraph genInstrumentationGraph(StructuredGraph oldGraph, InstrumentationNode instrumentationNode) {
            StructuredGraph instrumentationGraph = new StructuredGraph(AllowAssumptions.YES, INVALID_COMPILATION_ID);
            Map<Node, Node> replacements = Node.newMap();
            int index = 0; // for ParameterNode index
            for (Node current : nodes) {
                // mark any input that is not included in the instrumentation a weak dependency
                for (Node input : current.inputs()) {
                    if (input instanceof ValueNode) {
                        ValueNode valueNode = (ValueNode) input;
                        if (!nodes.isMarked(input) && !replacements.containsKey(input)) {
                            // create a ParameterNode in case the input is not within the
                            // instrumentation
                            ParameterNode parameter = new ParameterNode(index++, StampPair.createSingle(valueNode.stamp()));
                            instrumentationGraph.addWithoutUnique(parameter);
                            instrumentationNode.addWeakDependency(valueNode);
                            replacements.put(input, parameter);
                        }
                    }
                }
            }
            replacements = instrumentationGraph.addDuplicates(nodes, oldGraph, nodes.count(), replacements);
            instrumentationGraph.start().setNext((FixedNode) replacements.get(begin.next()));
            instrumentationGraph.start().setStateAfter((FrameState) replacements.get(begin.stateAfter()));
            replacements.get(end).replaceAtPredecessor(instrumentationGraph.addWithoutUnique(new ReturnNode(null)));
            return instrumentationGraph;
        }

        /**
         * @return true if the given FloatingNode does not contain any FixedNode input of types
         *         other than InputType.Value.
         */
        private static boolean shouldIncludeValueInput(FloatingNode node, NodeBitMap cfgNodes) {
            for (Position pos : node.inputPositions()) {
                if (pos.getInputType() == InputType.Value) {
                    continue;
                }
                Node input = pos.get(node);
                if (input instanceof FixedNode && !cfgNodes.isMarked(input)) {
                    return false;
                }
            }
            return true;
        }

        void unlink() {
            FixedNode next = end.next();
            end.setNext(null);
            begin.replaceAtPredecessor(next);
            GraphUtil.killCFG(begin);
        }

    }

    @Override
    public boolean checkContract() {
        return false;
    }

}
