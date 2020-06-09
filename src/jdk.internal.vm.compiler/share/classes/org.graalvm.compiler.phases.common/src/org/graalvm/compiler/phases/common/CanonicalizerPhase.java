/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.phases.common.CanonicalizerPhase.CanonicalizerFeature.CFG_SIMPLIFICATION;
import static org.graalvm.compiler.phases.common.CanonicalizerPhase.CanonicalizerFeature.GVN;
import static org.graalvm.compiler.phases.common.CanonicalizerPhase.CanonicalizerFeature.READ_CANONICALIZATION;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.GraalGraphError;
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
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.CoreProvidersDelegate;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.Phase;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Constant;

public class CanonicalizerPhase extends BasePhase<CoreProviders> {

    public enum CanonicalizerFeature {
        READ_CANONICALIZATION,
        CFG_SIMPLIFICATION,
        GVN
    }

    private static final int MAX_ITERATION_PER_NODE = 10;
    private static final CounterKey COUNTER_CANONICALIZED_NODES = DebugContext.counter("CanonicalizedNodes");
    private static final CounterKey COUNTER_PROCESSED_NODES = DebugContext.counter("ProcessedNodes");
    private static final CounterKey COUNTER_CANONICALIZATION_CONSIDERED_NODES = DebugContext.counter("CanonicalizationConsideredNodes");
    private static final CounterKey COUNTER_INFER_STAMP_CALLED = DebugContext.counter("InferStampCalled");
    private static final CounterKey COUNTER_STAMP_CHANGED = DebugContext.counter("StampChanged");
    private static final CounterKey COUNTER_SIMPLIFICATION_CONSIDERED_NODES = DebugContext.counter("SimplificationConsideredNodes");
    private static final CounterKey COUNTER_GLOBAL_VALUE_NUMBERING_HITS = DebugContext.counter("GlobalValueNumberingHits");

    private final EnumSet<CanonicalizerFeature> features;
    private final CustomCanonicalization customCanonicalization;
    private final CustomSimplification customSimplification;

    public interface CustomCanonicalization {
        /**
         * @param node the node to be canonicalized
         * @return the same node if no action should be taken, {@code null} if the node should be
         *         deleted, or a new node that should replace the given node
         */
        Node canonicalize(Node node);
    }

    public interface CustomSimplification {
        /**
         * @param node the node to be simplified
         * @param tool utility available during the simplification process
         */
        void simplify(Node node, SimplifierTool tool);
    }

    protected CanonicalizerPhase(EnumSet<CanonicalizerFeature> features) {
        this(null, null, features);
    }

    protected CanonicalizerPhase() {
        this(null, null, EnumSet.allOf(CanonicalizerFeature.class));
    }

    protected CanonicalizerPhase(CustomCanonicalization customCanonicalization, CustomSimplification customSimplification) {
        this(customCanonicalization, customSimplification, EnumSet.allOf(CanonicalizerFeature.class));
    }

    protected CanonicalizerPhase(CustomCanonicalization customCanonicalization, CustomSimplification customSimplification, EnumSet<CanonicalizerFeature> features) {
        this.customCanonicalization = customCanonicalization;
        this.customSimplification = customSimplification;
        this.features = features;
    }

    public CanonicalizerPhase copyWithCustomCanonicalization(CustomCanonicalization newCanonicalization) {
        return new CanonicalizerPhase(newCanonicalization, customSimplification, features);
    }

    public CanonicalizerPhase copyWithCustomSimplification(CustomSimplification newSimplification) {
        return new CanonicalizerPhase(customCanonicalization, newSimplification, features);
    }

    public CanonicalizerPhase copyWithoutGVN() {
        EnumSet<CanonicalizerFeature> newFeatures = EnumSet.copyOf(features);
        newFeatures.remove(GVN);
        return new CanonicalizerPhase(customCanonicalization, customSimplification, newFeatures);
    }

    public CanonicalizerPhase copyWithoutSimplification() {
        EnumSet<CanonicalizerFeature> newFeatures = EnumSet.copyOf(features);
        newFeatures.remove(CFG_SIMPLIFICATION);
        return new CanonicalizerPhase(customCanonicalization, customSimplification, newFeatures);
    }

    public static CanonicalizerPhase create() {
        return new CanonicalizerPhase(null, null, EnumSet.allOf(CanonicalizerFeature.class));
    }

    public static CanonicalizerPhase createWithoutReadCanonicalization() {
        return new CanonicalizerPhase(EnumSet.complementOf(EnumSet.of(READ_CANONICALIZATION)));
    }

    public static CanonicalizerPhase createWithoutGVN() {
        return new CanonicalizerPhase(EnumSet.complementOf(EnumSet.of(GVN)));
    }

    public static CanonicalizerPhase createWithoutCFGSimplification() {
        return new CanonicalizerPhase(EnumSet.complementOf(EnumSet.of(CFG_SIMPLIFICATION)));
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
    protected void run(StructuredGraph graph, CoreProviders context) {
        new Instance(context).run(graph);
    }

    /**
     * @param newNodesMark only the {@linkplain Graph#getNewNodes(Mark) new nodes} specified by this
     *            mark are processed
     */
    public void applyIncremental(StructuredGraph graph, CoreProviders context, Mark newNodesMark) {
        applyIncremental(graph, context, newNodesMark, true);
    }

    public void applyIncremental(StructuredGraph graph, CoreProviders context, Mark newNodesMark, boolean dumpGraph) {
        new Instance(context, newNodesMark).apply(graph, dumpGraph);
    }

    /**
     * @param workingSet the initial working set of nodes on which the canonicalizer works, should
     *            be an auto-grow node bitmap
     */
    public void applyIncremental(StructuredGraph graph, CoreProviders context, Iterable<? extends Node> workingSet) {
        applyIncremental(graph, context, workingSet, true);
    }

    public void applyIncremental(StructuredGraph graph, CoreProviders context, Iterable<? extends Node> workingSet, boolean dumpGraph) {
        new Instance(context, workingSet).apply(graph, dumpGraph);
    }

    public void applyIncremental(StructuredGraph graph, CoreProviders context, Iterable<? extends Node> workingSet, Mark newNodesMark) {
        applyIncremental(graph, context, workingSet, newNodesMark, true);
    }

    public void applyIncremental(StructuredGraph graph, CoreProviders context, Iterable<? extends Node> workingSet, Mark newNodesMark, boolean dumpGraph) {
        new Instance(context, workingSet, newNodesMark).apply(graph, dumpGraph);
    }

    public NodeView getNodeView() {
        return NodeView.DEFAULT;
    }

    private final class Instance extends Phase {

        private final Mark newNodesMark;
        private final CoreProviders context;
        private final Iterable<? extends Node> initWorkingSet;

        private NodeWorkList workList;
        private Tool tool;
        private DebugContext debug;

        private Instance(CoreProviders context) {
            this(context, null, null);
        }

        private Instance(CoreProviders context, Iterable<? extends Node> workingSet) {
            this(context, workingSet, null);
        }

        private Instance(CoreProviders context, Mark newNodesMark) {
            this(context, null, newNodesMark);
        }

        private Instance(CoreProviders context, Iterable<? extends Node> workingSet, Mark newNodesMark) {
            this.newNodesMark = newNodesMark;
            this.context = context;
            this.initWorkingSet = workingSet;
        }

        @Override
        public boolean checkContract() {
            return false;
        }

        @Override
        protected void run(StructuredGraph graph) {
            this.debug = graph.getDebug();
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

            tool = new Tool(graph.getAssumptions(), graph.getOptions());
            processWorkSet(graph);
        }

        @SuppressWarnings("try")
        private int processWorkSet(StructuredGraph graph) {
            int sum = 0;
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

                    if (node instanceof AbstractBeginNode) {
                        AbstractBeginNode abstractBeginNode = (AbstractBeginNode) node;
                        if (abstractBeginNode.predecessor() != null) {
                            workList.add(abstractBeginNode.predecessor());
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
                    if (changed && debug.isDumpEnabled(DebugContext.DETAILED_LEVEL)) {
                        debug.dump(DebugContext.DETAILED_LEVEL, graph, "CanonicalizerPhase %s", n);
                    }
                    ++sum;
                }
            }
            return sum;
        }

        /**
         * @return true if the graph was changed.
         */
        private boolean processNode(Node node) {
            if (!node.isAlive()) {
                return false;
            }
            COUNTER_PROCESSED_NODES.increment(debug);
            if (GraphUtil.tryKillUnused(node)) {
                return true;
            }
            NodeClass<?> nodeClass = node.getNodeClass();
            StructuredGraph graph = (StructuredGraph) node.graph();
            if (tryCanonicalize(node, nodeClass)) {
                return true;
            }
            if (features.contains(GVN) && tryGlobalValueNumbering(node, nodeClass)) {
                return true;
            }
            if (node instanceof ValueNode) {
                ValueNode valueNode = (ValueNode) node;
                boolean improvedStamp = tryInferStamp(valueNode);
                Constant constant = valueNode.stamp(NodeView.DEFAULT).asConstant();
                if (constant != null && !(node instanceof ConstantNode)) {
                    ConstantNode stampConstant = ConstantNode.forConstant(valueNode.stamp(NodeView.DEFAULT), constant, context.getMetaAccess(), graph);
                    debug.log("Canonicalizer: constant stamp replaces %1s with %1s", valueNode, stampConstant);
                    valueNode.replaceAtUsages(stampConstant, InputType.Value);
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
                    COUNTER_GLOBAL_VALUE_NUMBERING_HITS.increment(debug);
                    debug.log("GVN applied and new node is %1s", newNode);
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
            try (DebugCloseable position = node.withNodeSourcePosition(); DebugContext.Scope scope = debug.withContext(node)) {
                if (nodeClass.isCanonicalizable()) {
                    COUNTER_CANONICALIZATION_CONSIDERED_NODES.increment(debug);
                    Node canonical = node;
                    try (AutoCloseable verify = getCanonicalizeableContractAssertion(node)) {
                        if (customCanonicalization != null) {
                            canonical = customCanonicalization.canonicalize(node);
                        }
                        if (canonical == node) {
                            canonical = ((Canonicalizable) node).canonical(tool);
                            if (canonical == node && nodeClass.isCommutative()) {
                                canonical = ((BinaryCommutative<?>) node).maybeCommuteInputs();
                            }
                        }
                    } catch (Throwable e) {
                        throw new GraalGraphError(e).addContext(node);
                    }
                    if (performReplacement(node, canonical)) {
                        return true;
                    }
                }

                if (features.contains(CFG_SIMPLIFICATION) && nodeClass.isSimplifiable()) {
                    debug.log(DebugContext.VERBOSE_LEVEL, "Canonicalizer: simplifying %s", node);
                    COUNTER_SIMPLIFICATION_CONSIDERED_NODES.increment(debug);
                    if (customSimplification != null) {
                        customSimplification.simplify(node, tool);
                    }
                    if (node.isAlive()) {
                        node.simplify(tool);
                        if (node.isDeleted()) {
                            debug.log("Canonicalizer: simplified %s", node);
                        }
                    }
                    return node.isDeleted();
                }
                return false;
            } catch (Throwable throwable) {
                throw debug.handle(throwable);
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
                debug.log(DebugContext.VERBOSE_LEVEL, "Canonicalizer: work on %1s", node);
                return false;
            } else {
                Node canonical = newCanonical;
                debug.log("Canonicalizer: replacing %1s with %1s", node, canonical);
                COUNTER_CANONICALIZED_NODES.increment(debug);
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
                    GraphUtil.killWithUnusedFloatingInputs(node, true);
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
                COUNTER_INFER_STAMP_CALLED.increment(debug);
                if (node.inferStamp()) {
                    COUNTER_STAMP_CHANGED.increment(debug);
                    for (Node usage : node.usages()) {
                        workList.add(usage);
                    }
                    return true;
                }
            }
            return false;
        }

        private final class Tool extends CoreProvidersDelegate implements SimplifierTool, NodeView {

            private final Assumptions assumptions;
            private final OptionValues options;
            private NodeView nodeView;

            Tool(Assumptions assumptions, OptionValues options) {
                super(context);
                this.assumptions = assumptions;
                this.options = options;
                this.nodeView = getNodeView();
            }

            @Override
            public void deleteBranch(Node branch) {
                FixedNode fixedBranch = (FixedNode) branch;
                fixedBranch.predecessor().replaceFirstSuccessor(fixedBranch, null);
                GraphUtil.killCFG(fixedBranch);
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
                return features.contains(READ_CANONICALIZATION);
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
            public Integer smallestCompareWidth() {
                return context.getLowerer().smallestCompareWidth();
            }

            @Override
            public OptionValues getOptions() {
                return options;
            }

            @Override
            public Stamp stamp(ValueNode node) {
                return nodeView.stamp(node);
            }
        }
    }

    public boolean getCanonicalizeReads() {
        return features.contains(READ_CANONICALIZATION);
    }

}
