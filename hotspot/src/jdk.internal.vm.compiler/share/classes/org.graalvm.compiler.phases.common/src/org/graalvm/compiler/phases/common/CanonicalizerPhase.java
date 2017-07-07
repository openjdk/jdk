/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Graph.Mark;
import org.graalvm.compiler.graph.Graph.NodeEventListener;
import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.IndirectCanonicalization;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeWorkList;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.Canonicalizable.BinaryCommutative;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

public class CanonicalizerPhase extends BasePhase<PhaseContext> {

    private static final int MAX_ITERATION_PER_NODE = 10;
    private static final DebugCounter COUNTER_CANONICALIZED_NODES = Debug.counter("CanonicalizedNodes");
    private static final DebugCounter COUNTER_PROCESSED_NODES = Debug.counter("ProcessedNodes");
    private static final DebugCounter COUNTER_CANONICALIZATION_CONSIDERED_NODES = Debug.counter("CanonicalizationConsideredNodes");
    private static final DebugCounter COUNTER_INFER_STAMP_CALLED = Debug.counter("InferStampCalled");
    private static final DebugCounter COUNTER_STAMP_CHANGED = Debug.counter("StampChanged");
    private static final DebugCounter COUNTER_SIMPLIFICATION_CONSIDERED_NODES = Debug.counter("SimplificationConsideredNodes");
    private static final DebugCounter COUNTER_GLOBAL_VALUE_NUMBERING_HITS = Debug.counter("GlobalValueNumberingHits");

    private boolean canonicalizeReads = true;
    private boolean simplify = true;
    private final CustomCanonicalizer customCanonicalizer;

    public abstract static class CustomCanonicalizer {

        public Node canonicalize(Node node) {
            return node;
        }

        @SuppressWarnings("unused")
        public void simplify(Node node, SimplifierTool tool) {
        }
    }

    public CanonicalizerPhase() {
        this(null);
    }

    public CanonicalizerPhase(CustomCanonicalizer customCanonicalizer) {
        this.customCanonicalizer = customCanonicalizer;
    }

    public void disableReadCanonicalization() {
        canonicalizeReads = false;
    }

    public void disableSimplification() {
        simplify = false;
    }

    @Override
    public boolean checkContract() {
        /*
         * There are certain canonicalizations we make that heavily increase code size by e.g.
         * replacing a merge followed by a return of the merge's phi with returns in each
         * predecessor.
         */
        return false;
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        new Instance(context).run(graph);
    }

    /**
     * @param newNodesMark only the {@linkplain Graph#getNewNodes(Mark) new nodes} specified by this
     *            mark are processed
     */
    public void applyIncremental(StructuredGraph graph, PhaseContext context, Mark newNodesMark) {
        applyIncremental(graph, context, newNodesMark, true);
    }

    public void applyIncremental(StructuredGraph graph, PhaseContext context, Mark newNodesMark, boolean dumpGraph) {
        new Instance(context, newNodesMark).apply(graph, dumpGraph);
    }

    /**
     * @param workingSet the initial working set of nodes on which the canonicalizer works, should
     *            be an auto-grow node bitmap
     */
    public void applyIncremental(StructuredGraph graph, PhaseContext context, Iterable<? extends Node> workingSet) {
        applyIncremental(graph, context, workingSet, true);
    }

    public void applyIncremental(StructuredGraph graph, PhaseContext context, Iterable<? extends Node> workingSet, boolean dumpGraph) {
        new Instance(context, workingSet).apply(graph, dumpGraph);
    }

    public void applyIncremental(StructuredGraph graph, PhaseContext context, Iterable<? extends Node> workingSet, Mark newNodesMark) {
        applyIncremental(graph, context, workingSet, newNodesMark, true);
    }

    public void applyIncremental(StructuredGraph graph, PhaseContext context, Iterable<? extends Node> workingSet, Mark newNodesMark, boolean dumpGraph) {
        new Instance(context, workingSet, newNodesMark).apply(graph, dumpGraph);
    }

    private final class Instance extends Phase {

        private final Mark newNodesMark;
        private final PhaseContext context;
        private final Iterable<? extends Node> initWorkingSet;

        private NodeWorkList workList;
        private Tool tool;

        private Instance(PhaseContext context) {
            this(context, null, null);
        }

        private Instance(PhaseContext context, Iterable<? extends Node> workingSet) {
            this(context, workingSet, null);
        }

        private Instance(PhaseContext context, Mark newNodesMark) {
            this(context, null, newNodesMark);
        }

        private Instance(PhaseContext context, Iterable<? extends Node> workingSet, Mark newNodesMark) {
            this.newNodesMark = newNodesMark;
            this.context = context;
            this.initWorkingSet = workingSet;
        }

        @Override
        protected void run(StructuredGraph graph) {
            boolean wholeGraph = newNodesMark == null || newNodesMark.isStart();
            if (initWorkingSet == null) {
                workList = graph.createIterativeNodeWorkList(wholeGraph, MAX_ITERATION_PER_NODE);
            } else {
                workList = graph.createIterativeNodeWorkList(false, MAX_ITERATION_PER_NODE);
                workList.addAll(initWorkingSet);
            }
            if (!wholeGraph) {
                workList.addAll(graph.getNewNodes(newNodesMark));
            }
            tool = new Tool(graph.getAssumptions());
            processWorkSet(graph);
        }

        @SuppressWarnings("try")
        private void processWorkSet(StructuredGraph graph) {
            NodeEventListener listener = new NodeEventListener() {

                @Override
                public void nodeAdded(Node node) {
                    workList.add(node);
                }

                @Override
                public void inputChanged(Node node) {
                    workList.add(node);
                    if (node instanceof IndirectCanonicalization) {
                        for (Node usage : node.usages()) {
                            workList.add(usage);
                        }
                    }
                }

                @Override
                public void usagesDroppedToZero(Node node) {
                    workList.add(node);
                }

            };
            try (NodeEventScope nes = graph.trackNodeEvents(listener)) {
                for (Node n : workList) {
                    boolean changed = processNode(n);
                    if (changed && Debug.isDumpEnabled(Debug.DETAILED_LOG_LEVEL)) {
                        Debug.dump(Debug.DETAILED_LOG_LEVEL, graph, "CanonicalizerPhase %s", n);
                    }
                }
            }
        }

        /**
         * @return true if the graph was changed.
         */
        private boolean processNode(Node node) {
            if (!node.isAlive()) {
                return false;
            }
            if (node instanceof FloatingNode && node.hasNoUsages()) {
                // Dead but on the worklist so simply kill it
                GraphUtil.killWithUnusedFloatingInputs(node);
                return false;
            }
            COUNTER_PROCESSED_NODES.increment();

            NodeClass<?> nodeClass = node.getNodeClass();
            if (tryGlobalValueNumbering(node, nodeClass)) {
                return true;
            }
            StructuredGraph graph = (StructuredGraph) node.graph();
            if (GraphUtil.tryKillUnused(node)) {
                return true;
            }
            if (tryCanonicalize(node, nodeClass)) {
                return true;
            }
            if (node instanceof ValueNode) {
                ValueNode valueNode = (ValueNode) node;
                boolean improvedStamp = tryInferStamp(valueNode);
                Constant constant = valueNode.stamp().asConstant();
                if (constant != null && !(node instanceof ConstantNode)) {
                    ConstantNode stampConstant = ConstantNode.forConstant(valueNode.stamp(), constant, context.getMetaAccess(), graph);
                    Debug.log("Canonicalizer: constant stamp replaces %1s with %1s", valueNode, stampConstant);
                    valueNode.replaceAtUsages(InputType.Value, stampConstant);
                    GraphUtil.tryKillUnused(valueNode);
                    return true;
                } else if (improvedStamp) {
                    // the improved stamp may enable additional canonicalization
                    if (tryCanonicalize(valueNode, nodeClass)) {
                        return true;
                    }
                    valueNode.usages().forEach(workList::add);
                }
            }
            return false;
        }

        public boolean tryGlobalValueNumbering(Node node, NodeClass<?> nodeClass) {
            if (nodeClass.valueNumberable()) {
                Node newNode = node.graph().findDuplicate(node);
                if (newNode != null) {
                    assert !(node instanceof FixedNode || newNode instanceof FixedNode);
                    node.replaceAtUsagesAndDelete(newNode);
                    COUNTER_GLOBAL_VALUE_NUMBERING_HITS.increment();
                    Debug.log("GVN applied and new node is %1s", newNode);
                    return true;
                }
            }
            return false;
        }

        private AutoCloseable getCanonicalizeableContractAssertion(Node node) {
            boolean needsAssertion = false;
            assert (needsAssertion = true) == true;
            if (needsAssertion) {
                Mark mark = node.graph().getMark();
                return () -> {
                    assert mark.equals(node.graph().getMark()) : "new node created while canonicalizing " + node.getClass().getSimpleName() + " " + node + ": " +
                                    node.graph().getNewNodes(mark).snapshot();
                };
            } else {
                return null;
            }
        }

        @SuppressWarnings("try")
        public boolean tryCanonicalize(final Node node, NodeClass<?> nodeClass) {
            try (DebugCloseable position = node.withNodeSourcePosition()) {
                if (customCanonicalizer != null) {
                    Node canonical = customCanonicalizer.canonicalize(node);
                    if (performReplacement(node, canonical)) {
                        return true;
                    } else {
                        customCanonicalizer.simplify(node, tool);
                        if (node.isDeleted()) {
                            return true;
                        }
                    }
                }
                if (nodeClass.isCanonicalizable()) {
                    COUNTER_CANONICALIZATION_CONSIDERED_NODES.increment();
                    Node canonical;
                    try (AutoCloseable verify = getCanonicalizeableContractAssertion(node)) {
                        canonical = ((Canonicalizable) node).canonical(tool);
                        if (canonical == node && nodeClass.isCommutative()) {
                            canonical = ((BinaryCommutative<?>) node).maybeCommuteInputs();
                        }
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                    if (performReplacement(node, canonical)) {
                        return true;
                    }
                }

                if (nodeClass.isSimplifiable() && simplify) {
                    Debug.log(Debug.VERBOSE_LOG_LEVEL, "Canonicalizer: simplifying %s", node);
                    COUNTER_SIMPLIFICATION_CONSIDERED_NODES.increment();
                    node.simplify(tool);
                    return node.isDeleted();
                }
                return false;
            }
        }

// @formatter:off
//     cases:                                           original node:
//                                         |Floating|Fixed-unconnected|Fixed-connected|
//                                         --------------------------------------------
//                                     null|   1    |        X        |       3       |
//                                         --------------------------------------------
//                                 Floating|   2    |        X        |       4       |
//       canonical node:                   --------------------------------------------
//                        Fixed-unconnected|   X    |        X        |       5       |
//                                         --------------------------------------------
//                          Fixed-connected|   2    |        X        |       6       |
//                                         --------------------------------------------
//                              ControlSink|   X    |        X        |       7       |
//                                         --------------------------------------------
//       X: must not happen (checked with assertions)
// @formatter:on
        private boolean performReplacement(final Node node, Node newCanonical) {
            if (newCanonical == node) {
                Debug.log(Debug.VERBOSE_LOG_LEVEL, "Canonicalizer: work on %1s", node);
                return false;
            } else {
                Node canonical = newCanonical;
                Debug.log("Canonicalizer: replacing %1s with %1s", node, canonical);
                COUNTER_CANONICALIZED_NODES.increment();
                StructuredGraph graph = (StructuredGraph) node.graph();
                if (canonical != null && !canonical.isAlive()) {
                    assert !canonical.isDeleted();
                    canonical = graph.addOrUniqueWithInputs(canonical);
                }
                if (node instanceof FloatingNode) {
                    assert canonical == null || !(canonical instanceof FixedNode) ||
                                    (canonical.predecessor() != null || canonical instanceof StartNode || canonical instanceof AbstractMergeNode) : node +
                                                    " -> " + canonical + " : replacement should be floating or fixed and connected";
                    node.replaceAtUsages(canonical);
                    GraphUtil.killWithUnusedFloatingInputs(node);
                } else {
                    assert node instanceof FixedNode && node.predecessor() != null : node + " -> " + canonical + " : node should be fixed & connected (" + node.predecessor() + ")";
                    FixedNode fixed = (FixedNode) node;
                    if (canonical instanceof ControlSinkNode) {
                        // case 7
                        fixed.replaceAtPredecessor(canonical);
                        GraphUtil.killCFG(fixed);
                        return true;
                    } else {
                        assert fixed instanceof FixedWithNextNode;
                        FixedWithNextNode fixedWithNext = (FixedWithNextNode) fixed;
                        // When removing a fixed node, new canonicalization
                        // opportunities for its successor may arise
                        assert fixedWithNext.next() != null;
                        tool.addToWorkList(fixedWithNext.next());
                        if (canonical == null) {
                            // case 3
                            node.replaceAtUsages(null);
                            GraphUtil.removeFixedWithUnusedInputs(fixedWithNext);
                        } else if (canonical instanceof FloatingNode) {
                            // case 4
                            graph.replaceFixedWithFloating(fixedWithNext, (FloatingNode) canonical);
                        } else {
                            assert canonical instanceof FixedNode;
                            if (canonical.predecessor() == null) {
                                assert !canonical.cfgSuccessors().iterator().hasNext() : "replacement " + canonical + " shouldn't have successors";
                                // case 5
                                graph.replaceFixedWithFixed(fixedWithNext, (FixedWithNextNode) canonical);
                            } else {
                                assert canonical.cfgSuccessors().iterator().hasNext() : "replacement " + canonical + " should have successors";
                                // case 6
                                node.replaceAtUsages(canonical);
                                GraphUtil.removeFixedWithUnusedInputs(fixedWithNext);
                            }
                        }
                    }
                }
                return true;
            }
        }

        /**
         * Calls {@link ValueNode#inferStamp()} on the node and, if it returns true (which means
         * that the stamp has changed), re-queues the node's usages. If the stamp has changed then
         * this method also checks if the stamp now describes a constant integer value, in which
         * case the node is replaced with a constant.
         */
        private boolean tryInferStamp(ValueNode node) {
            if (node.isAlive()) {
                COUNTER_INFER_STAMP_CALLED.increment();
                if (node.inferStamp()) {
                    COUNTER_STAMP_CHANGED.increment();
                    for (Node usage : node.usages()) {
                        workList.add(usage);
                    }
                    return true;
                }
            }
            return false;
        }

        private final class Tool implements SimplifierTool {

            private final Assumptions assumptions;

            Tool(Assumptions assumptions) {
                this.assumptions = assumptions;
            }

            @Override
            public void deleteBranch(Node branch) {
                FixedNode fixedBranch = (FixedNode) branch;
                fixedBranch.predecessor().replaceFirstSuccessor(fixedBranch, null);
                GraphUtil.killCFG(fixedBranch, this);
            }

            @Override
            public MetaAccessProvider getMetaAccess() {
                return context.getMetaAccess();
            }

            @Override
            public ConstantReflectionProvider getConstantReflection() {
                return context.getConstantReflection();
            }

            @Override
            public ConstantFieldProvider getConstantFieldProvider() {
                return context.getConstantFieldProvider();
            }

            @Override
            public void addToWorkList(Node node) {
                workList.add(node);
            }

            @Override
            public void addToWorkList(Iterable<? extends Node> nodes) {
                workList.addAll(nodes);
            }

            @Override
            public void removeIfUnused(Node node) {
                GraphUtil.tryKillUnused(node);
            }

            @Override
            public boolean canonicalizeReads() {
                return canonicalizeReads;
            }

            @Override
            public boolean allUsagesAvailable() {
                return true;
            }

            @Override
            public Assumptions getAssumptions() {
                return assumptions;
            }

            @Override
            public boolean supportSubwordCompare(int bits) {
                return context.getLowerer().supportSubwordCompare(bits);
            }
        }
    }

    public boolean getCanonicalizeReads() {
        return canonicalizeReads;
    }

}
