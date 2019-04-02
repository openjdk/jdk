/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.GraalOptions.OptEliminateGuards;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;
import static org.graalvm.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_ENTER;
import static org.graalvm.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_ENTER_ALWAYS_REACHED;
import static org.graalvm.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_LEAVE;
import static org.graalvm.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_PROCESS;
import static org.graalvm.compiler.phases.common.LoweringPhase.ProcessBlockState.ST_PROCESS_ALWAYS_REACHED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph.Mark;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.extended.AnchoringNode;
import org.graalvm.compiler.nodes.extended.GuardedNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

/**
 * Processes all {@link Lowerable} nodes to do their lowering.
 */
public class LoweringPhase extends BasePhase<PhaseContext> {

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static final class DummyGuardHandle extends ValueNode implements GuardedNode {
        public static final NodeClass<DummyGuardHandle> TYPE = NodeClass.create(DummyGuardHandle.class);
        @Input(InputType.Guard) GuardingNode guard;

        protected DummyGuardHandle(GuardingNode guard) {
            super(TYPE, StampFactory.forVoid());
            this.guard = guard;
        }

        @Override
        public GuardingNode getGuard() {
            return guard;
        }

        @Override
        public void setGuard(GuardingNode guard) {
            updateUsagesInterface(this.guard, guard);
            this.guard = guard;
        }

        @Override
        public ValueNode asNode() {
            return this;
        }
    }

    @Override
    public boolean checkContract() {
        return false;
    }

    final class LoweringToolImpl implements LoweringTool {

        private final PhaseContext context;
        private final NodeBitMap activeGuards;
        private AnchoringNode guardAnchor;
        private FixedWithNextNode lastFixedNode;

        LoweringToolImpl(PhaseContext context, AnchoringNode guardAnchor, NodeBitMap activeGuards, FixedWithNextNode lastFixedNode) {
            this.context = context;
            this.guardAnchor = guardAnchor;
            this.activeGuards = activeGuards;
            this.lastFixedNode = lastFixedNode;
        }

        @Override
        public LoweringStage getLoweringStage() {
            return loweringStage;
        }

        @Override
        public CoreProviders getProviders() {
            return context;
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
        public MetaAccessProvider getMetaAccess() {
            return context.getMetaAccess();
        }

        @Override
        public LoweringProvider getLowerer() {
            return context.getLowerer();
        }

        @Override
        public Replacements getReplacements() {
            return context.getReplacements();
        }

        public ForeignCallsProvider getForeignCalls() {
            return context.getForeignCalls();
        }

        @Override
        public AnchoringNode getCurrentGuardAnchor() {
            return guardAnchor;
        }

        @Override
        public GuardingNode createGuard(FixedNode before, LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action) {
            return createGuard(before, condition, deoptReason, action, SpeculationLog.NO_SPECULATION, false, null);
        }

        @Override
        public StampProvider getStampProvider() {
            return context.getStampProvider();
        }

        @Override
        public GuardingNode createGuard(FixedNode before, LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, Speculation speculation, boolean negated,
                        NodeSourcePosition noDeoptSucccessorPosition) {
            StructuredGraph graph = before.graph();
            if (OptEliminateGuards.getValue(graph.getOptions())) {
                for (Node usage : condition.usages()) {
                    if (!activeGuards.isNew(usage) && activeGuards.isMarked(usage) && ((GuardNode) usage).isNegated() == negated) {
                        return (GuardNode) usage;
                    }
                }
            }
            if (!condition.graph().getGuardsStage().allowsFloatingGuards()) {
                FixedGuardNode fixedGuard = graph.add(new FixedGuardNode(condition, deoptReason, action, speculation, negated, noDeoptSucccessorPosition));
                graph.addBeforeFixed(before, fixedGuard);
                DummyGuardHandle handle = graph.add(new DummyGuardHandle(fixedGuard));
                fixedGuard.lower(this);
                GuardingNode result = handle.getGuard();
                handle.safeDelete();
                return result;
            } else {
                GuardNode newGuard = graph.unique(new GuardNode(condition, guardAnchor, deoptReason, action, negated, speculation, noDeoptSucccessorPosition));
                if (OptEliminateGuards.getValue(graph.getOptions())) {
                    activeGuards.markAndGrow(newGuard);
                }
                return newGuard;
            }
        }

        @Override
        public FixedWithNextNode lastFixedNode() {
            return lastFixedNode;
        }

        private void setLastFixedNode(FixedWithNextNode n) {
            assert n.isAlive() : n;
            lastFixedNode = n;
        }
    }

    private final CanonicalizerPhase canonicalizer;
    private final LoweringTool.LoweringStage loweringStage;

    public LoweringPhase(CanonicalizerPhase canonicalizer, LoweringTool.LoweringStage loweringStage) {
        this.canonicalizer = canonicalizer;
        this.loweringStage = loweringStage;
    }

    @Override
    protected boolean shouldDumpBeforeAtBasicLevel() {
        return loweringStage == LoweringTool.StandardLoweringStage.HIGH_TIER;
    }

    /**
     * Checks that second lowering of a given graph did not introduce any new nodes.
     *
     * @param graph a graph that was just {@linkplain #lower lowered}
     * @throws AssertionError if the check fails
     */
    private boolean checkPostLowering(StructuredGraph graph, PhaseContext context) {
        Mark expectedMark = graph.getMark();
        lower(graph, context, LoweringMode.VERIFY_LOWERING);
        Mark mark = graph.getMark();
        assert mark.equals(expectedMark) : graph + ": a second round in the current lowering phase introduced these new nodes: " + graph.getNewNodes(expectedMark).snapshot();
        return true;
    }

    @Override
    protected void run(final StructuredGraph graph, PhaseContext context) {
        lower(graph, context, LoweringMode.LOWERING);
        assert checkPostLowering(graph, context);
    }

    private void lower(StructuredGraph graph, PhaseContext context, LoweringMode mode) {
        IncrementalCanonicalizerPhase<PhaseContext> incrementalCanonicalizer = new IncrementalCanonicalizerPhase<>(canonicalizer);
        incrementalCanonicalizer.appendPhase(new Round(context, mode, graph.getOptions()));
        incrementalCanonicalizer.apply(graph, context);
        assert graph.verify();
    }

    /**
     * Checks that lowering of a given node did not introduce any new {@link Lowerable} nodes that
     * could be lowered in the current {@link LoweringPhase}. Such nodes must be recursively lowered
     * as part of lowering {@code node}.
     *
     * @param node a node that was just lowered
     * @param preLoweringMark the graph mark before {@code node} was lowered
     * @param unscheduledUsages set of {@code node}'s usages that were unscheduled before it was
     *            lowered
     * @throws AssertionError if the check fails
     */
    private static boolean checkPostNodeLowering(Node node, LoweringToolImpl loweringTool, Mark preLoweringMark, Collection<Node> unscheduledUsages) {
        StructuredGraph graph = (StructuredGraph) node.graph();
        Mark postLoweringMark = graph.getMark();
        NodeIterable<Node> newNodesAfterLowering = graph.getNewNodes(preLoweringMark);
        if (node instanceof FloatingNode) {
            if (!unscheduledUsages.isEmpty()) {
                for (Node n : newNodesAfterLowering) {
                    assert !(n instanceof FixedNode) : node.graph() + ": cannot lower floatable node " + node + " as it introduces fixed node(s) but has the following unscheduled usages: " +
                                    unscheduledUsages;
                }
            }
        }
        for (Node n : newNodesAfterLowering) {
            if (n instanceof Lowerable) {
                ((Lowerable) n).lower(loweringTool);
                Mark mark = graph.getMark();
                assert postLoweringMark.equals(mark) : graph + ": lowering of " + node + " produced lowerable " + n + " that should have been recursively lowered as it introduces these new nodes: " +
                                graph.getNewNodes(postLoweringMark).snapshot();
            }
            if (graph.isAfterFloatingReadPhase() && n instanceof MemoryCheckpoint && !(node instanceof MemoryCheckpoint) && !(node instanceof ControlSinkNode)) {
                /*
                 * The lowering introduced a MemoryCheckpoint but the current node isn't a
                 * checkpoint. This is only OK if the locations involved don't affect the memory
                 * graph or if the new kill location doesn't connect into the existing graph.
                 */
                boolean isAny = false;
                if (n instanceof MemoryCheckpoint.Single) {
                    isAny = ((MemoryCheckpoint.Single) n).getLocationIdentity().isAny();
                } else {
                    for (LocationIdentity ident : ((MemoryCheckpoint.Multi) n).getLocationIdentities()) {
                        if (ident.isAny()) {
                            isAny = true;
                        }
                    }
                }
                if (isAny && n instanceof FixedWithNextNode) {
                    /*
                     * Check if the next kill location leads directly to a ControlSinkNode in the
                     * new part of the graph. This is a fairly conservative test that could be made
                     * more general if required.
                     */
                    FixedWithNextNode cur = (FixedWithNextNode) n;
                    while (cur != null && graph.isNew(preLoweringMark, cur)) {
                        if (cur.next() instanceof ControlSinkNode) {
                            isAny = false;
                            break;
                        }
                        if (cur.next() instanceof FixedWithNextNode) {
                            cur = (FixedWithNextNode) cur.next();
                        } else {
                            break;
                        }
                    }
                }
                assert !isAny : node + " " + n;
            }
        }
        return true;
    }

    private enum LoweringMode {
        LOWERING,
        VERIFY_LOWERING
    }

    private final class Round extends Phase {

        private final PhaseContext context;
        private final LoweringMode mode;
        private ScheduleResult schedule;
        private final SchedulePhase schedulePhase;

        private Round(PhaseContext context, LoweringMode mode, OptionValues options) {
            this.context = context;
            this.mode = mode;

            /*
             * In VERIFY_LOWERING, we want to verify whether the lowering itself changes the graph.
             * Make sure we're not detecting spurious changes because the SchedulePhase modifies the
             * graph.
             */
            boolean immutableSchedule = mode == LoweringMode.VERIFY_LOWERING;

            this.schedulePhase = new SchedulePhase(immutableSchedule, options);
        }

        @Override
        protected CharSequence getName() {
            switch (mode) {
                case LOWERING:
                    return "LoweringRound";
                case VERIFY_LOWERING:
                    return "VerifyLoweringRound";
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }

        @Override
        public boolean checkContract() {
            /*
             * lowering with snippets cannot be fully built in the node costs of all high level
             * nodes
             */
            return false;
        }

        @Override
        public void run(StructuredGraph graph) {
            schedulePhase.apply(graph, false);
            schedule = graph.getLastSchedule();
            schedule.getCFG().computePostdominators();
            Block startBlock = schedule.getCFG().getStartBlock();
            ProcessFrame rootFrame = new ProcessFrame(startBlock, graph.createNodeBitMap(), startBlock.getBeginNode(), null);
            LoweringPhase.processBlock(rootFrame);
        }

        private class ProcessFrame extends Frame<ProcessFrame> {
            private final NodeBitMap activeGuards;
            private AnchoringNode anchor;

            ProcessFrame(Block block, NodeBitMap activeGuards, AnchoringNode anchor, ProcessFrame parent) {
                super(block, parent);
                this.activeGuards = activeGuards;
                this.anchor = anchor;
            }

            @Override
            public void preprocess() {
                this.anchor = Round.this.process(block, activeGuards, anchor);
            }

            @Override
            public ProcessFrame enter(Block b) {
                return new ProcessFrame(b, activeGuards, b.getBeginNode(), this);
            }

            @Override
            public Frame<?> enterAlwaysReached(Block b) {
                AnchoringNode newAnchor = anchor;
                if (parent != null && b.getLoop() != parent.block.getLoop() && !b.isLoopHeader()) {
                    // We are exiting a loop => cannot reuse the anchor without inserting loop
                    // proxies.
                    newAnchor = b.getBeginNode();
                }
                return new ProcessFrame(b, activeGuards, newAnchor, this);
            }

            @Override
            public void postprocess() {
                if (anchor == block.getBeginNode() && OptEliminateGuards.getValue(activeGuards.graph().getOptions())) {
                    for (GuardNode guard : anchor.asNode().usages().filter(GuardNode.class)) {
                        if (activeGuards.isMarkedAndGrow(guard)) {
                            activeGuards.clear(guard);
                        }
                    }
                }
            }

        }

        @SuppressWarnings("try")
        private AnchoringNode process(final Block b, final NodeBitMap activeGuards, final AnchoringNode startAnchor) {

            final LoweringToolImpl loweringTool = new LoweringToolImpl(context, startAnchor, activeGuards, b.getBeginNode());

            // Lower the instructions of this block.
            List<Node> nodes = schedule.nodesFor(b);
            for (Node node : nodes) {

                if (node.isDeleted()) {
                    // This case can happen when previous lowerings deleted nodes.
                    continue;
                }

                // Cache the next node to be able to reconstruct the previous of the next node
                // after lowering.
                FixedNode nextNode = null;
                if (node instanceof FixedWithNextNode) {
                    nextNode = ((FixedWithNextNode) node).next();
                } else {
                    nextNode = loweringTool.lastFixedNode().next();
                }

                if (node instanceof Lowerable) {
                    Collection<Node> unscheduledUsages = null;
                    assert (unscheduledUsages = getUnscheduledUsages(node)) != null;
                    Mark preLoweringMark = node.graph().getMark();
                    try (DebugCloseable s = node.graph().withNodeSourcePosition(node)) {
                        ((Lowerable) node).lower(loweringTool);
                    }
                    if (loweringTool.guardAnchor.asNode().isDeleted()) {
                        // TODO nextNode could be deleted but this is not currently supported
                        assert nextNode.isAlive();
                        loweringTool.guardAnchor = AbstractBeginNode.prevBegin(nextNode);
                    }
                    assert checkPostNodeLowering(node, loweringTool, preLoweringMark, unscheduledUsages);
                }

                if (!nextNode.isAlive()) {
                    // can happen when the rest of the block is killed by lowering
                    // (e.g. by an unconditional deopt)
                    break;
                } else {
                    Node nextLastFixed = nextNode.predecessor();
                    if (!(nextLastFixed instanceof FixedWithNextNode)) {
                        // insert begin node, to have a valid last fixed for next lowerable node.
                        // This is about lowering a FixedWithNextNode to a control split while this
                        // FixedWithNextNode is followed by some kind of BeginNode.
                        // For example the when a FixedGuard followed by a loop exit is lowered to a
                        // control-split + deopt.
                        AbstractBeginNode begin = node.graph().add(new BeginNode());
                        nextLastFixed.replaceFirstSuccessor(nextNode, begin);
                        begin.setNext(nextNode);
                        nextLastFixed = begin;
                    }
                    loweringTool.setLastFixedNode((FixedWithNextNode) nextLastFixed);
                }
            }
            return loweringTool.getCurrentGuardAnchor();
        }

        /**
         * Gets all usages of a floating, lowerable node that are unscheduled.
         * <p>
         * Given that the lowering of such nodes may introduce fixed nodes, they must be lowered in
         * the context of a usage that dominates all other usages. The fixed nodes resulting from
         * lowering are attached to the fixed node context of the dominating usage. This ensures the
         * post-lowering graph still has a valid schedule.
         *
         * @param node a {@link Lowerable} node
         */
        private Collection<Node> getUnscheduledUsages(Node node) {
            List<Node> unscheduledUsages = new ArrayList<>();
            if (node instanceof FloatingNode) {
                for (Node usage : node.usages()) {
                    if (usage instanceof ValueNode && !(usage instanceof PhiNode) && !(usage instanceof ProxyNode)) {
                        if (schedule.getCFG().getNodeToBlock().isNew(usage) || schedule.getCFG().blockFor(usage) == null) {
                            unscheduledUsages.add(usage);
                        }
                    }
                }
            }
            return unscheduledUsages;
        }
    }

    enum ProcessBlockState {
        ST_ENTER,
        ST_PROCESS,
        ST_ENTER_ALWAYS_REACHED,
        ST_LEAVE,
        ST_PROCESS_ALWAYS_REACHED;
    }

    /**
     * This state-machine resembles the following recursion:
     *
     * <pre>
     * void processBlock(Block block) {
     *     preprocess();
     *     // Process always reached block first.
     *     Block alwaysReachedBlock = block.getPostdominator();
     *     if (alwaysReachedBlock != null &amp;&amp; alwaysReachedBlock.getDominator() == block) {
     *         processBlock(alwaysReachedBlock);
     *     }
     *
     *     // Now go for the other dominators.
     *     for (Block dominated : block.getDominated()) {
     *         if (dominated != alwaysReachedBlock) {
     *             assert dominated.getDominator() == block;
     *             processBlock(dominated);
     *         }
     *     }
     *     postprocess();
     * }
     * </pre>
     *
     * This is necessary, as the recursive implementation quickly exceed the stack depth on SPARC.
     *
     * @param rootFrame contains the starting block.
     */
    public static void processBlock(final Frame<?> rootFrame) {
        ProcessBlockState state = ST_PROCESS;
        Frame<?> f = rootFrame;
        while (f != null) {
            ProcessBlockState nextState;
            if (state == ST_PROCESS || state == ST_PROCESS_ALWAYS_REACHED) {
                f.preprocess();
                nextState = state == ST_PROCESS_ALWAYS_REACHED ? ST_ENTER : ST_ENTER_ALWAYS_REACHED;
            } else if (state == ST_ENTER_ALWAYS_REACHED) {
                if (f.alwaysReachedBlock != null && f.alwaysReachedBlock.getDominator() == f.block) {
                    f = f.enterAlwaysReached(f.alwaysReachedBlock);
                    nextState = ST_PROCESS;
                } else {
                    nextState = ST_ENTER;
                }
            } else if (state == ST_ENTER) {
                if (f.dominated != null) {
                    Block n = f.dominated;
                    f.dominated = n.getDominatedSibling();
                    if (n == f.alwaysReachedBlock) {
                        if (f.dominated != null) {
                            n = f.dominated;
                            f.dominated = n.getDominatedSibling();
                        } else {
                            n = null;
                        }
                    }
                    if (n == null) {
                        nextState = ST_LEAVE;
                    } else {
                        f = f.enter(n);
                        assert f.block.getDominator() == f.parent.block;
                        nextState = ST_PROCESS;
                    }
                } else {
                    nextState = ST_LEAVE;
                }
            } else if (state == ST_LEAVE) {
                f.postprocess();
                f = f.parent;
                nextState = ST_ENTER;
            } else {
                throw GraalError.shouldNotReachHere();
            }
            state = nextState;
        }
    }

    public static void processBlockBounded(final Frame<?> rootFrame) {
        ProcessBlockState state = ST_PROCESS;
        Frame<?> f = rootFrame;
        while (f != null) {
            ProcessBlockState nextState;
            if (state == ST_PROCESS || state == ST_PROCESS_ALWAYS_REACHED) {
                f.preprocess();
                nextState = state == ST_PROCESS_ALWAYS_REACHED ? ST_ENTER : ST_ENTER_ALWAYS_REACHED;
            } else if (state == ST_ENTER_ALWAYS_REACHED) {
                if (f.alwaysReachedBlock != null && f.alwaysReachedBlock.getDominator() == f.block) {
                    Frame<?> continueRecur = f.enterAlwaysReached(f.alwaysReachedBlock);
                    if (continueRecur == null) {
                        // stop recursion here
                        f.postprocess();
                        f = f.parent;
                        state = ST_ENTER;
                        continue;
                    }
                    f = continueRecur;
                    nextState = ST_PROCESS;
                } else {
                    nextState = ST_ENTER;
                }
            } else if (state == ST_ENTER) {
                if (f.dominated != null) {
                    Block n = f.dominated;
                    f.dominated = n.getDominatedSibling();
                    if (n == f.alwaysReachedBlock) {
                        if (f.dominated != null) {
                            n = f.dominated;
                            f.dominated = n.getDominatedSibling();
                        } else {
                            n = null;
                        }
                    }
                    if (n == null) {
                        nextState = ST_LEAVE;
                    } else {
                        Frame<?> continueRecur = f.enter(n);
                        if (continueRecur == null) {
                            // stop recursion here
                            f.postprocess();
                            f = f.parent;
                            state = ST_ENTER;
                            continue;
                        }
                        f = continueRecur;
                        nextState = ST_PROCESS;
                    }
                } else {
                    nextState = ST_LEAVE;
                }
            } else if (state == ST_LEAVE) {
                f.postprocess();
                f = f.parent;
                nextState = ST_ENTER;
            } else {
                throw GraalError.shouldNotReachHere();
            }
            state = nextState;
        }
    }

    public abstract static class Frame<T extends Frame<?>> {
        protected final Block block;
        final T parent;
        Block dominated;
        final Block alwaysReachedBlock;

        public Frame(Block block, T parent) {
            this.block = block;
            this.alwaysReachedBlock = block.getPostdominator();
            this.dominated = block.getFirstDominated();
            this.parent = parent;
        }

        public Frame<?> enterAlwaysReached(Block b) {
            return enter(b);
        }

        public abstract Frame<?> enter(Block b);

        public abstract void preprocess();

        public abstract void postprocess();
    }

}
