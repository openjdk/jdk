/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.phases.common.inlining.walker;

import java.util.ArrayList;
import java.util.function.ToDoubleFunction;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeWorkList;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;

public class ComputeInliningRelevance {

    private static final double EPSILON = 1d / Integer.MAX_VALUE;
    private static final double UNINITIALIZED = -1D;

    private static final int EXPECTED_MIN_INVOKE_COUNT = 3;
    private static final int EXPECTED_INVOKE_RATIO = 20;
    private static final int EXPECTED_LOOP_COUNT = 3;

    private final StructuredGraph graph;
    private final ToDoubleFunction<FixedNode> nodeProbabilities;

    /**
     * Node relevances are pre-computed for all invokes if the graph contains loops. If there are no
     * loops, the computation happens lazily based on {@link #rootScope}.
     */
    private EconomicMap<FixedNode, Double> nodeRelevances;
    /**
     * This scope is non-null if (and only if) there are no loops in the graph. In this case, the
     * root scope is used to compute invoke relevances on the fly.
     */
    private Scope rootScope;

    public ComputeInliningRelevance(StructuredGraph graph, ToDoubleFunction<FixedNode> nodeProbabilities) {
        this.graph = graph;
        this.nodeProbabilities = nodeProbabilities;
    }

    /**
     * Initializes or updates the relevance computation. If there are no loops within the graph,
     * most computation happens lazily.
     */
    public void compute() {
        rootScope = null;
        if (!graph.hasLoops()) {
            // fast path for the frequent case of no loops
            rootScope = new Scope(graph.start(), null);
        } else {
            if (nodeRelevances == null) {
                nodeRelevances = EconomicMap.create(Equivalence.IDENTITY, EXPECTED_MIN_INVOKE_COUNT + InliningUtil.getNodeCount(graph) / EXPECTED_INVOKE_RATIO);
            }
            NodeWorkList workList = graph.createNodeWorkList();
            EconomicMap<LoopBeginNode, Scope> loops = EconomicMap.create(Equivalence.IDENTITY, EXPECTED_LOOP_COUNT);

            Scope topScope = new Scope(graph.start(), null);
            for (LoopBeginNode loopBegin : graph.getNodes(LoopBeginNode.TYPE)) {
                createLoopScope(loopBegin, loops, topScope);
            }

            topScope.process(workList);
            for (Scope scope : loops.getValues()) {
                scope.process(workList);
            }
        }
    }

    public double getRelevance(Invoke invoke) {
        if (rootScope != null) {
            return rootScope.computeInvokeRelevance(invoke);
        }
        assert nodeRelevances != null : "uninitialized relevance";
        return nodeRelevances.get(invoke.asNode());
    }

    /**
     * Determines the parent of the given loop and creates a {@link Scope} object for each one. This
     * method will call itself recursively if no {@link Scope} for the parent loop exists.
     */
    private Scope createLoopScope(LoopBeginNode loopBegin, EconomicMap<LoopBeginNode, Scope> loops, Scope topScope) {
        Scope scope = loops.get(loopBegin);
        if (scope == null) {
            final Scope parent;
            // look for the parent scope
            FixedNode current = loopBegin.forwardEnd();
            while (true) {
                if (current.predecessor() == null) {
                    if (current instanceof LoopBeginNode) {
                        // if we reach a LoopBeginNode then we're within this loop
                        parent = createLoopScope((LoopBeginNode) current, loops, topScope);
                        break;
                    } else if (current instanceof StartNode) {
                        // we're within the outermost scope
                        parent = topScope;
                        break;
                    } else {
                        assert current instanceof MergeNode : current;
                        // follow any path upwards - it doesn't matter which one
                        current = ((AbstractMergeNode) current).forwardEndAt(0);
                    }
                } else if (current instanceof LoopExitNode) {
                    // if we reach a loop exit then we follow this loop and have the same parent
                    parent = createLoopScope(((LoopExitNode) current).loopBegin(), loops, topScope).parent;
                    break;
                } else {
                    current = (FixedNode) current.predecessor();
                }
            }
            scope = new Scope(loopBegin, parent);
            loops.put(loopBegin, scope);
        }
        return scope;
    }

    /**
     * A scope holds information for the contents of one loop or of the root of the method. It does
     * not include child loops, i.e., the iteration in {@link #process(NodeWorkList)} explicitly
     * excludes the nodes of child loops.
     */
    private class Scope {
        public final FixedNode start;
        public final Scope parent; // can be null for the outermost scope

        /**
         * The minimum probability along the most probable path in this scope. Computed lazily.
         */
        private double fastPathMinProbability = UNINITIALIZED;
        /**
         * A measure of how important this scope is within its parent scope. Computed lazily.
         */
        private double scopeRelevanceWithinParent = UNINITIALIZED;

        Scope(FixedNode start, Scope parent) {
            this.start = start;
            this.parent = parent;
        }

        @SuppressFBWarnings(value = "FE_FLOATING_POINT_EQUALITY", justification = "comparing against -1D is accurate")
        public double getFastPathMinProbability() {
            if (fastPathMinProbability == UNINITIALIZED) {
                fastPathMinProbability = Math.max(EPSILON, computeFastPathMinProbability(start));
            }
            return fastPathMinProbability;
        }

        /**
         * Computes the ratio between the probabilities of the current scope's entry point and the
         * parent scope's fastPathMinProbability.
         */
        @SuppressFBWarnings(value = "FE_FLOATING_POINT_EQUALITY", justification = "comparing against -1D is accurate")
        public double getScopeRelevanceWithinParent() {
            if (scopeRelevanceWithinParent == UNINITIALIZED) {
                if (start instanceof LoopBeginNode) {
                    assert parent != null;
                    double scopeEntryProbability = nodeProbabilities.applyAsDouble(((LoopBeginNode) start).forwardEnd());

                    scopeRelevanceWithinParent = scopeEntryProbability / parent.getFastPathMinProbability();
                } else {
                    scopeRelevanceWithinParent = 1D;
                }
            }
            return scopeRelevanceWithinParent;
        }

        /**
         * Processes all invokes in this scope by starting at the scope's start node and iterating
         * all fixed nodes. Child loops are skipped by going from loop entries directly to the loop
         * exits. Processing stops at loop exits of the current loop.
         */
        public void process(NodeWorkList workList) {
            assert !(start instanceof Invoke);
            workList.addAll(start.successors());

            for (Node current : workList) {
                assert current.isAlive();

                if (current instanceof Invoke) {
                    // process the invoke and queue its successors
                    nodeRelevances.put((FixedNode) current, computeInvokeRelevance((Invoke) current));
                    workList.addAll(current.successors());
                } else if (current instanceof LoopBeginNode) {
                    // skip child loops by advancing over the loop exits
                    ((LoopBeginNode) current).loopExits().forEach(exit -> workList.add(exit.next()));
                } else if (current instanceof LoopEndNode) {
                    // nothing to do
                } else if (current instanceof LoopExitNode) {
                    // nothing to do
                } else if (current instanceof FixedWithNextNode) {
                    workList.add(((FixedWithNextNode) current).next());
                } else if (current instanceof EndNode) {
                    workList.add(((EndNode) current).merge());
                } else if (current instanceof ControlSinkNode) {
                    // nothing to do
                } else if (current instanceof ControlSplitNode) {
                    workList.addAll(current.successors());
                } else {
                    assert false : current;
                }
            }
        }

        /**
         * The relevance of an invoke is the ratio between the invoke's probability and the current
         * scope's fastPathMinProbability, adjusted by scopeRelevanceWithinParent.
         */
        public double computeInvokeRelevance(Invoke invoke) {
            double invokeProbability = nodeProbabilities.applyAsDouble(invoke.asNode());
            assert !Double.isNaN(invokeProbability);

            double relevance = (invokeProbability / getFastPathMinProbability()) * Math.min(1.0, getScopeRelevanceWithinParent());
            assert !Double.isNaN(relevance) : invoke + ": " + relevance + " / " + invokeProbability + " / " + getFastPathMinProbability() + " / " + getScopeRelevanceWithinParent();
            return relevance;
        }
    }

    /**
     * Computes the minimum probability along the most probable path within the scope. During
     * iteration, the method returns immediately once a loop exit is discovered.
     */
    private double computeFastPathMinProbability(FixedNode scopeStart) {
        ArrayList<FixedNode> pathBeginNodes = new ArrayList<>();
        pathBeginNodes.add(scopeStart);
        double minPathProbability = nodeProbabilities.applyAsDouble(scopeStart);
        boolean isLoopScope = scopeStart instanceof LoopBeginNode;

        do {
            Node current = pathBeginNodes.remove(pathBeginNodes.size() - 1);
            do {
                if (isLoopScope && current instanceof LoopExitNode && ((LoopBeginNode) scopeStart).loopExits().contains((LoopExitNode) current)) {
                    return minPathProbability;
                } else if (current instanceof LoopBeginNode && current != scopeStart) {
                    current = getMaxProbabilityLoopExit((LoopBeginNode) current, pathBeginNodes);
                    minPathProbability = getMinPathProbability((FixedNode) current, minPathProbability);
                } else if (current instanceof ControlSplitNode) {
                    current = getMaxProbabilitySux((ControlSplitNode) current, pathBeginNodes);
                    minPathProbability = getMinPathProbability((FixedNode) current, minPathProbability);
                } else {
                    assert current.successors().count() <= 1;
                    current = current.successors().first();
                }
            } while (current != null);
        } while (!pathBeginNodes.isEmpty());

        return minPathProbability;
    }

    private double getMinPathProbability(FixedNode current, double minPathProbability) {
        return current == null ? minPathProbability : Math.min(minPathProbability, nodeProbabilities.applyAsDouble(current));
    }

    /**
     * Returns the most probable successor. If multiple successors share the maximum probability,
     * one is returned and the others are enqueued in pathBeginNodes.
     */
    private static Node getMaxProbabilitySux(ControlSplitNode controlSplit, ArrayList<FixedNode> pathBeginNodes) {
        Node maxSux = null;
        double maxProbability = 0.0;
        int pathBeginCount = pathBeginNodes.size();

        for (Node sux : controlSplit.successors()) {
            double probability = controlSplit.probability((AbstractBeginNode) sux);
            if (probability > maxProbability) {
                maxProbability = probability;
                maxSux = sux;
                truncate(pathBeginNodes, pathBeginCount);
            } else if (probability == maxProbability) {
                pathBeginNodes.add((FixedNode) sux);
            }
        }

        return maxSux;
    }

    /**
     * Returns the most probable loop exit. If multiple successors share the maximum probability,
     * one is returned and the others are enqueued in pathBeginNodes.
     */
    private Node getMaxProbabilityLoopExit(LoopBeginNode loopBegin, ArrayList<FixedNode> pathBeginNodes) {
        Node maxSux = null;
        double maxProbability = 0.0;
        int pathBeginCount = pathBeginNodes.size();

        for (LoopExitNode sux : loopBegin.loopExits()) {
            double probability = nodeProbabilities.applyAsDouble(sux);
            if (probability > maxProbability) {
                maxProbability = probability;
                maxSux = sux;
                truncate(pathBeginNodes, pathBeginCount);
            } else if (probability == maxProbability) {
                pathBeginNodes.add(sux);
            }
        }

        return maxSux;
    }

    private static void truncate(ArrayList<FixedNode> pathBeginNodes, int pathBeginCount) {
        for (int i = pathBeginNodes.size() - pathBeginCount; i > 0; i--) {
            pathBeginNodes.remove(pathBeginNodes.size() - 1);
        }
    }
}
