/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodes.StaticDeoptimizingNode.mergeActions;
import static org.graalvm.compiler.phases.common.ConditionalEliminationUtil.getOtherSafeStamp;
import static org.graalvm.compiler.phases.common.ConditionalEliminationUtil.getSafeStamp;
import static org.graalvm.compiler.phases.common.ConditionalEliminationUtil.rewireGuards;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import jdk.internal.vm.compiler.collections.MapCursor;
import jdk.internal.vm.compiler.collections.Pair;
import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp.And;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Or;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.NodeStack;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BinaryOpLogicNode;
import org.graalvm.compiler.nodes.ConditionAnchorNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.DeoptimizingGuard;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.UnaryOpLogicNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.TypeSwitchNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.NodeWithState;
import org.graalvm.compiler.nodes.spi.StampInverter;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.ConditionalEliminationUtil.GuardFolding;
import org.graalvm.compiler.phases.common.ConditionalEliminationUtil.GuardRewirer;
import org.graalvm.compiler.phases.common.ConditionalEliminationUtil.GuardedCondition;
import org.graalvm.compiler.phases.common.ConditionalEliminationUtil.InfoElement;
import org.graalvm.compiler.phases.common.ConditionalEliminationUtil.InfoElementProvider;
import org.graalvm.compiler.phases.common.ConditionalEliminationUtil.InputFilter;
import org.graalvm.compiler.phases.common.ConditionalEliminationUtil.Marks;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.SpeculationLog.Speculation;
import jdk.vm.ci.meta.TriState;

public class ConditionalEliminationPhase extends BasePhase<CoreProviders> {

    private static final CounterKey counterStampsRegistered = DebugContext.counter("StampsRegistered");
    private static final CounterKey counterIfsKilled = DebugContext.counter("CE_KilledIfs");
    private static final CounterKey counterPhiStampsImproved = DebugContext.counter("CE_ImprovedPhis");
    private final boolean fullSchedule;
    private final boolean moveGuards;

    public ConditionalEliminationPhase(boolean fullSchedule) {
        this(fullSchedule, true);
    }

    public ConditionalEliminationPhase(boolean fullSchedule, boolean moveGuards) {
        this.fullSchedule = fullSchedule;
        this.moveGuards = moveGuards;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        try (DebugContext.Scope s = graph.getDebug().scope("DominatorConditionalElimination")) {
            BlockMap<List<Node>> blockToNodes = null;
            NodeMap<Block> nodeToBlock = null;
            ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, true, true);
            if (fullSchedule) {
                if (moveGuards) {
                    cfg.visitDominatorTree(new MoveGuardsUpwards(), graph.hasValueProxies());
                }
                try (DebugContext.Scope scheduleScope = graph.getDebug().scope(SchedulePhase.class)) {
                    SchedulePhase.run(graph, SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER, cfg);
                } catch (Throwable t) {
                    throw graph.getDebug().handle(t);
                }
                ScheduleResult r = graph.getLastSchedule();
                blockToNodes = r.getBlockToNodesMap();
                nodeToBlock = r.getNodeToBlockMap();
            } else {
                nodeToBlock = cfg.getNodeToBlock();
                blockToNodes = getBlockToNodes(cfg);
            }
            ControlFlowGraph.RecursiveVisitor<?> visitor = createVisitor(graph, cfg, blockToNodes, nodeToBlock, context);
            cfg.visitDominatorTree(visitor, graph.hasValueProxies());
        }
    }

    protected BlockMap<List<Node>> getBlockToNodes(@SuppressWarnings("unused") ControlFlowGraph cfg) {
        return null;
    }

    protected ControlFlowGraph.RecursiveVisitor<?> createVisitor(StructuredGraph graph, @SuppressWarnings("unused") ControlFlowGraph cfg, BlockMap<List<Node>> blockToNodes,
                    NodeMap<Block> nodeToBlock, CoreProviders context) {
        return new Instance(graph, blockToNodes, nodeToBlock, context);
    }

    public static class MoveGuardsUpwards implements ControlFlowGraph.RecursiveVisitor<Block> {

        Block anchorBlock;

        @Override
        @SuppressWarnings("try")
        public Block enter(Block b) {
            Block oldAnchorBlock = anchorBlock;
            if (b.getDominator() == null || b.getDominator().getPostdominator() != b) {
                // New anchor.
                anchorBlock = b;
            }

            AbstractBeginNode beginNode = b.getBeginNode();
            if (beginNode instanceof AbstractMergeNode && anchorBlock != b) {
                AbstractMergeNode mergeNode = (AbstractMergeNode) beginNode;
                mergeNode.replaceAtUsages(anchorBlock.getBeginNode(), InputType.Anchor, InputType.Guard);
                assert mergeNode.anchored().isEmpty();
            }

            FixedNode endNode = b.getEndNode();
            if (endNode instanceof IfNode) {
                IfNode node = (IfNode) endNode;

                // Check if we can move guards upwards.
                AbstractBeginNode trueSuccessor = node.trueSuccessor();
                AbstractBeginNode falseSuccessor = node.falseSuccessor();

                EconomicMap<LogicNode, GuardNode> trueGuards = EconomicMap.create(Equivalence.IDENTITY);
                for (GuardNode guard : trueSuccessor.guards()) {
                    LogicNode condition = guard.getCondition();
                    if (condition.hasMoreThanOneUsage()) {
                        trueGuards.put(condition, guard);
                    }
                }

                if (!trueGuards.isEmpty()) {
                    for (GuardNode guard : falseSuccessor.guards().snapshot()) {
                        GuardNode otherGuard = trueGuards.get(guard.getCondition());
                        if (otherGuard != null && guard.isNegated() == otherGuard.isNegated()) {
                            Speculation speculation = otherGuard.getSpeculation();
                            if (speculation == null) {
                                speculation = guard.getSpeculation();
                            } else if (guard.getSpeculation() != null && guard.getSpeculation() != speculation) {
                                // Cannot optimize due to different speculations.
                                continue;
                            }
                            try (DebugCloseable closeable = guard.withNodeSourcePosition()) {
                                GuardNode newlyCreatedGuard = new GuardNode(guard.getCondition(), anchorBlock.getBeginNode(), guard.getReason(), guard.getAction(), guard.isNegated(), speculation,
                                                guard.getNoDeoptSuccessorPosition());
                                GuardNode newGuard = node.graph().unique(newlyCreatedGuard);
                                if (otherGuard.isAlive()) {
                                    if (trueSuccessor instanceof LoopExitNode && beginNode.graph().hasValueProxies()) {
                                        otherGuard.replaceAndDelete(ProxyNode.forGuard(newGuard, (LoopExitNode) trueSuccessor));
                                    } else {
                                        otherGuard.replaceAndDelete(newGuard);
                                    }
                                }
                                if (falseSuccessor instanceof LoopExitNode && beginNode.graph().hasValueProxies()) {
                                    guard.replaceAndDelete(ProxyNode.forGuard(newGuard, (LoopExitNode) falseSuccessor));
                                } else {
                                    guard.replaceAndDelete(newGuard);
                                }
                            }
                        }
                    }
                }
            }
            return oldAnchorBlock;
        }

        @Override
        public void exit(Block b, Block value) {
            anchorBlock = value;
        }

    }

    private static final class PhiInfoElement {

        private EconomicMap<EndNode, InfoElement> infoElements;

        public void set(EndNode end, InfoElement infoElement) {
            if (infoElements == null) {
                infoElements = EconomicMap.create(Equivalence.IDENTITY);
            }
            infoElements.put(end, infoElement);
        }

        public InfoElement get(EndNode end) {
            if (infoElements == null) {
                return null;
            }
            return infoElements.get(end);
        }
    }

    public static class Instance implements ControlFlowGraph.RecursiveVisitor<Marks> {
        protected final NodeMap<InfoElement> map;
        protected final BlockMap<List<Node>> blockToNodes;
        protected final NodeMap<Block> nodeToBlock;
        protected final CanonicalizerTool tool;
        protected final NodeStack undoOperations;
        protected final StructuredGraph graph;
        protected final DebugContext debug;
        protected final EconomicMap<MergeNode, EconomicMap<ValuePhiNode, PhiInfoElement>> mergeMaps;
        private final InfoElementProvider infoElementProvider;
        private final GuardFolding guardFolding;

        protected final ArrayDeque<GuardedCondition> conditions;

        /**
         * Tests which may be eliminated because post dominating tests to prove a broader condition.
         */
        private Deque<DeoptimizingGuard> pendingTests;

        public Instance(StructuredGraph graph, BlockMap<List<Node>> blockToNodes, NodeMap<Block> nodeToBlock, CoreProviders context) {
            this.graph = graph;
            this.debug = graph.getDebug();
            this.blockToNodes = blockToNodes;
            this.nodeToBlock = nodeToBlock;
            this.undoOperations = new NodeStack();
            this.map = graph.createNodeMap();
            this.pendingTests = new ArrayDeque<>();
            this.conditions = new ArrayDeque<>();
            tool = GraphUtil.getDefaultSimplifier(context, false, graph.getAssumptions(), graph.getOptions());
            mergeMaps = EconomicMap.create(Equivalence.IDENTITY);
            infoElementProvider = new InfoElementProvider() {

                @Override
                public InfoElement infoElements(ValueNode value) {
                    return getInfoElements(value);
                }
            };
            guardFolding = new GuardFolding() {

                @Override
                public boolean foldGuard(DeoptimizingGuard thisGuard, ValueNode original, Stamp newStamp, GuardRewirer rewireGuardFunction) {
                    return foldPendingTest(thisGuard, original, newStamp, rewireGuardFunction);
                }
            };
        }

        protected void processConditionAnchor(ConditionAnchorNode node) {
            tryProveGuardCondition(null, node.condition(), (guard, result, guardedValueStamp, newInput) -> {
                if (result != node.isNegated()) {
                    node.replaceAtUsages(guard.asNode());
                    GraphUtil.unlinkFixedNode(node);
                    GraphUtil.killWithUnusedFloatingInputs(node);
                } else {
                    ValueAnchorNode valueAnchor = node.graph().add(new ValueAnchorNode(null));
                    node.replaceAtUsages(valueAnchor);
                    node.graph().replaceFixedWithFixed(node, valueAnchor);
                }
                return true;
            });
        }

        protected void processGuard(GuardNode node) {
            if (!tryProveGuardCondition(node, node.getCondition(), (guard, result, guardedValueStamp, newInput) -> {
                if (result != node.isNegated()) {
                    node.replaceAndDelete(guard.asNode());
                    if (guard instanceof DeoptimizingGuard && !((DeoptimizingGuard) guard).isNegated()) {
                        rebuildPiNodes((DeoptimizingGuard) guard);
                    }
                } else {
                    AbstractBeginNode beginNode = (AbstractBeginNode) node.getAnchor();

                    if (beginNode.next() instanceof DeoptimizeNode) {
                        // This branch is already dead.
                    } else {
                        /*
                         * Don't kill this branch immediately because `killCFG` can have complex
                         * implications in the presence of loops: it might replace or delete nodes
                         * in other branches or even above the kill point. Instead of killing
                         * immediately, just leave the graph in a state that is easy to simplify by
                         * a subsequent canonicalizer phase.
                         */
                        FixedGuardNode deopt = new FixedGuardNode(LogicConstantNode.forBoolean(result, node.graph()), node.getReason(), node.getAction(), node.getSpeculation(), node.isNegated(),
                                        node.getNodeSourcePosition());
                        graph.addAfterFixed(beginNode, node.graph().add(deopt));
                    }
                }
                return true;
            })) {
                registerNewCondition(node.getCondition(), node.isNegated(), node);
            }
        }

        protected void processFixedGuard(FixedGuardNode node) {
            if (!tryProveGuardCondition(node, node.condition(), (guard, result, guardedValueStamp, newInput) -> {
                if (result != node.isNegated()) {
                    node.replaceAtUsages(guard.asNode());
                    GraphUtil.unlinkFixedNode(node);
                    GraphUtil.killWithUnusedFloatingInputs(node);
                    if (guard instanceof DeoptimizingGuard && !((DeoptimizingGuard) guard).isNegated()) {
                        rebuildPiNodes((DeoptimizingGuard) guard);
                    }
                } else {
                    node.setCondition(LogicConstantNode.forBoolean(result, node.graph()), node.isNegated());
                    // Don't kill this branch immediately, see `processGuard`.
                }

                debug.log("Kill fixed guard %s", node);
                return true;
            })) {
                registerNewCondition(node.condition(), node.isNegated(), node);
            }
        }

        private void rebuildPiNodes(DeoptimizingGuard guard) {
            LogicNode newCondition = guard.getCondition();
            if (newCondition instanceof InstanceOfNode) {
                InstanceOfNode inst = (InstanceOfNode) newCondition;
                ValueNode originalValue = GraphUtil.skipPi(inst.getValue());
                PiNode pi = null;
                // Ensure that any Pi that's weaker than what the instanceof proves is
                // replaced by one derived from the instanceof itself.
                for (PiNode existing : guard.asNode().usages().filter(PiNode.class).snapshot()) {
                    if (!existing.isAlive()) {
                        continue;
                    }
                    if (originalValue != GraphUtil.skipPi(existing.object())) {
                        // Somehow these are unrelated values so leave it alone
                        continue;
                    }
                    // If the pi has a weaker stamp or the same stamp but a different input
                    // then replace it.
                    boolean strongerStamp = !existing.piStamp().join(inst.getCheckedStamp()).equals(inst.getCheckedStamp());
                    boolean differentCheckedStamp = !existing.piStamp().equals(inst.getCheckedStamp());
                    boolean differentObject = existing.object() != inst.getValue();
                    if (!strongerStamp && (differentCheckedStamp || differentObject)) {
                        if (pi == null) {
                            pi = graph.unique(new PiNode(inst.getValue(), inst.getCheckedStamp(), (ValueNode) guard));
                        }
                        if (!pi.stamp(NodeView.DEFAULT).join(existing.stamp(NodeView.DEFAULT)).equals(pi.stamp(NodeView.DEFAULT))) {
                            /*
                             * With a code sequence like null check, type check, null check of type
                             * checked value, CE will use the first null check to prove the second
                             * null check so the graph ends up a Pi guarded by the first null check
                             * but consuming the output Pi from the type check check. In this case
                             * we should still canonicalize the checked stamp for consistency.
                             */
                            if (differentCheckedStamp) {
                                PiNode alternatePi = graph.unique(new PiNode(existing.object(), inst.getCheckedStamp(), (ValueNode) guard));
                                /*
                                 * If the resulting stamp is as good or better then do the
                                 * replacement. However when interface types are involved it's
                                 * possible that improving the checked stamp merges types which
                                 * appear unrelated so there's we must skip the replacement.
                                 */
                                if (alternatePi.stamp(NodeView.DEFAULT).join(existing.stamp(NodeView.DEFAULT)).equals(alternatePi.stamp(NodeView.DEFAULT))) {
                                    existing.replaceAndDelete(alternatePi);
                                }
                            }
                            continue;
                        }
                        existing.replaceAndDelete(pi);
                    }
                }
            }
        }

        protected void processIf(IfNode node) {
            tryProveGuardCondition(null, node.condition(), (guard, result, guardedValueStamp, newInput) -> {
                node.setCondition(LogicConstantNode.forBoolean(result, node.graph()));
                AbstractBeginNode survivingSuccessor = node.getSuccessor(result);
                survivingSuccessor.replaceAtUsages(guard.asNode(), InputType.Guard);
                // Don't kill the other branch immediately, see `processGuard`.
                counterIfsKilled.increment(debug);
                return true;
            });
        }

        @Override
        public Marks enter(Block block) {
            int infoElementsMark = undoOperations.size();
            int conditionsMark = conditions.size();
            debug.log("[Pre Processing block %s]", block);
            // For now conservatively collect guards only within the same block.
            pendingTests.clear();
            processNodes(block);
            return new Marks(infoElementsMark, conditionsMark);
        }

        protected void processNodes(Block block) {
            if (blockToNodes != null) {
                for (Node n : blockToNodes.get(block)) {
                    if (n.isAlive()) {
                        processNode(n);
                    }
                }
            } else {
                processBlock(block);
            }
        }

        private void processBlock(Block block) {
            FixedNode n = block.getBeginNode();
            FixedNode endNode = block.getEndNode();
            debug.log("[Processing block %s]", block);
            while (n != endNode) {
                if (n.isDeleted() || endNode.isDeleted()) {
                    // This branch was deleted!
                    return;
                }
                FixedNode next = ((FixedWithNextNode) n).next();
                processNode(n);
                n = next;
            }
            if (endNode.isAlive()) {
                processNode(endNode);
            }
        }

        @SuppressWarnings("try")
        protected void processNode(Node node) {
            try (DebugCloseable closeable = node.withNodeSourcePosition()) {
                if (node instanceof NodeWithState && !(node instanceof GuardingNode)) {
                    pendingTests.clear();
                }

                if (node instanceof MergeNode) {
                    introducePisForPhis((MergeNode) node);
                }

                if (node instanceof AbstractBeginNode) {
                    if (node instanceof LoopExitNode && graph.hasValueProxies()) {
                        // Condition must not be used down this path.
                        return;
                    }
                    processAbstractBegin((AbstractBeginNode) node);
                } else if (node instanceof FixedGuardNode) {
                    processFixedGuard((FixedGuardNode) node);
                } else if (node instanceof GuardNode) {
                    processGuard((GuardNode) node);
                } else if (node instanceof ConditionAnchorNode) {
                    processConditionAnchor((ConditionAnchorNode) node);
                } else if (node instanceof IfNode) {
                    processIf((IfNode) node);
                } else if (node instanceof EndNode) {
                    processEnd((EndNode) node);
                }
            }
        }

        protected void introducePisForPhis(MergeNode merge) {
            EconomicMap<ValuePhiNode, PhiInfoElement> mergeMap = this.mergeMaps.get(merge);
            if (mergeMap != null) {
                MapCursor<ValuePhiNode, PhiInfoElement> entries = mergeMap.getEntries();
                while (entries.advance()) {
                    ValuePhiNode phi = entries.getKey();
                    assert phi.isAlive() || phi.isDeleted();
                    /*
                     * Phi might have been killed already via a conditional elimination in another
                     * branch.
                     */
                    if (phi.isDeleted()) {
                        continue;
                    }
                    PhiInfoElement phiInfoElements = entries.getValue();
                    Stamp bestPossibleStamp = null;
                    for (int i = 0; i < phi.valueCount(); ++i) {
                        ValueNode valueAt = phi.valueAt(i);
                        Stamp curBestStamp = valueAt.stamp(NodeView.DEFAULT);
                        InfoElement infoElement = phiInfoElements.get(merge.forwardEndAt(i));
                        if (infoElement != null) {
                            curBestStamp = curBestStamp.join(infoElement.getStamp());
                        }

                        if (bestPossibleStamp == null) {
                            bestPossibleStamp = curBestStamp;
                        } else {
                            bestPossibleStamp = bestPossibleStamp.meet(curBestStamp);
                        }
                    }

                    Stamp oldStamp = phi.stamp(NodeView.DEFAULT);
                    if (oldStamp.tryImproveWith(bestPossibleStamp) != null) {

                        // Need to be careful to not run into stamp update cycles with the iterative
                        // canonicalization.
                        boolean allow = false;
                        if (bestPossibleStamp instanceof ObjectStamp) {
                            // Always allow object stamps.
                            allow = true;
                        } else if (bestPossibleStamp instanceof IntegerStamp) {
                            IntegerStamp integerStamp = (IntegerStamp) bestPossibleStamp;
                            IntegerStamp oldIntegerStamp = (IntegerStamp) oldStamp;
                            if (integerStamp.isPositive() != oldIntegerStamp.isPositive()) {
                                allow = true;
                            } else if (integerStamp.isNegative() != oldIntegerStamp.isNegative()) {
                                allow = true;
                            } else if (integerStamp.isStrictlyPositive() != oldIntegerStamp.isStrictlyPositive()) {
                                allow = true;
                            } else if (integerStamp.isStrictlyNegative() != oldIntegerStamp.isStrictlyNegative()) {
                                allow = true;
                            } else if (integerStamp.asConstant() != null) {
                                allow = true;
                            } else if (oldStamp.isUnrestricted()) {
                                allow = true;
                            }
                        } else {
                            // Fortify: Suppress Null Dereference false positive
                            assert bestPossibleStamp != null;
                            allow = (bestPossibleStamp.asConstant() != null);
                        }

                        if (allow) {
                            ValuePhiNode newPhi = graph.addWithoutUnique(new ValuePhiNode(bestPossibleStamp, merge));
                            for (int i = 0; i < phi.valueCount(); ++i) {
                                ValueNode valueAt = phi.valueAt(i);
                                if (bestPossibleStamp.meet(valueAt.stamp(NodeView.DEFAULT)).equals(bestPossibleStamp)) {
                                    // Pi not required here.
                                } else {
                                    InfoElement infoElement = phiInfoElements.get(merge.forwardEndAt(i));
                                    assert infoElement != null;
                                    Stamp curBestStamp = infoElement.getStamp();
                                    ValueNode input = infoElement.getProxifiedInput();
                                    if (input == null) {
                                        input = valueAt;
                                    }
                                    valueAt = graph.maybeAddOrUnique(PiNode.create(input, curBestStamp, (ValueNode) infoElement.getGuard()));
                                }
                                newPhi.addInput(valueAt);
                            }
                            counterPhiStampsImproved.increment(debug);
                            phi.replaceAtUsagesAndDelete(newPhi);
                        }
                    }
                }
            }
        }

        protected void processEnd(EndNode end) {
            AbstractMergeNode abstractMerge = end.merge();
            if (abstractMerge instanceof MergeNode) {
                MergeNode merge = (MergeNode) abstractMerge;

                EconomicMap<ValuePhiNode, PhiInfoElement> mergeMap = this.mergeMaps.get(merge);
                for (ValuePhiNode phi : merge.valuePhis()) {
                    ValueNode valueAt = phi.valueAt(end);
                    InfoElement infoElement = this.getInfoElements(valueAt);
                    while (infoElement != null) {
                        Stamp newStamp = infoElement.getStamp();
                        if (phi.stamp(NodeView.DEFAULT).tryImproveWith(newStamp) != null) {
                            if (mergeMap == null) {
                                mergeMap = EconomicMap.create(Equivalence.IDENTITY);
                                mergeMaps.put(merge, mergeMap);
                            }

                            PhiInfoElement phiInfoElement = mergeMap.get(phi);
                            if (phiInfoElement == null) {
                                phiInfoElement = new PhiInfoElement();
                                mergeMap.put(phi, phiInfoElement);
                            }

                            phiInfoElement.set(end, infoElement);
                            break;
                        }
                        infoElement = nextElement(infoElement);
                    }
                }
            }
        }

        protected void registerNewCondition(LogicNode condition, boolean negated, GuardingNode guard) {
            if (condition instanceof UnaryOpLogicNode) {
                UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) condition;
                ValueNode value = unaryLogicNode.getValue();
                if (maybeMultipleUsages(value)) {
                    // getSucceedingStampForValue doesn't take the (potentially a Pi Node) input
                    // stamp into account, so it can be safely propagated.
                    Stamp newStamp = unaryLogicNode.getSucceedingStampForValue(negated);
                    registerNewStamp(value, newStamp, guard, true);
                }
            } else if (condition instanceof BinaryOpLogicNode) {
                BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) condition;
                ValueNode x = binaryOpLogicNode.getX();
                ValueNode y = binaryOpLogicNode.getY();
                if (!x.isConstant() && maybeMultipleUsages(x)) {
                    Stamp newStampX = binaryOpLogicNode.getSucceedingStampForX(negated, getSafeStamp(x), getOtherSafeStamp(y));
                    registerNewStamp(x, newStampX, guard);
                }

                if (!y.isConstant() && maybeMultipleUsages(y)) {
                    Stamp newStampY = binaryOpLogicNode.getSucceedingStampForY(negated, getOtherSafeStamp(x), getSafeStamp(y));
                    registerNewStamp(y, newStampY, guard);
                }

                if (condition instanceof IntegerEqualsNode && guard instanceof DeoptimizingGuard && !negated) {
                    if (y.isConstant() && x instanceof AndNode) {
                        AndNode and = (AndNode) x;
                        ValueNode andX = and.getX();
                        if (and.getY() == y && maybeMultipleUsages(andX)) {
                            /*
                             * This 'and' proves something about some of the bits in and.getX().
                             * It's equivalent to or'ing in the mask value since those values are
                             * known to be set.
                             */
                            BinaryOp<Or> op = ArithmeticOpTable.forStamp(x.stamp(NodeView.DEFAULT)).getOr();
                            IntegerStamp newStampX = (IntegerStamp) op.foldStamp(getSafeStamp(andX), getOtherSafeStamp(y));
                            registerNewStamp(andX, newStampX, guard);
                        }
                    }
                }
            }
            if (guard instanceof DeoptimizingGuard) {
                assert ((DeoptimizingGuard) guard).getCondition() == condition;
                pendingTests.push((DeoptimizingGuard) guard);
            }
            registerCondition(condition, negated, guard);
        }

        /**
         * Recursively try to fold stamps within this expression using information from
         * {@link #getInfoElements(ValueNode)}. It's only safe to use constants and one
         * {@link InfoElement} otherwise more than one guard would be required.
         *
         * @param node
         * @return the pair of the @{link InfoElement} used and the stamp produced for the whole
         *         expression
         */
        Pair<InfoElement, Stamp> recursiveFoldStampFromInfo(Node node) {
            return ConditionalEliminationUtil.recursiveFoldStamp(infoElementProvider, node);
        }

        /**
         * Look for a preceding guard whose condition is implied by {@code thisGuard}. If we find
         * one, try to move this guard just above that preceding guard so that we can fold it:
         *
         * <pre>
         *     guard(C1); // preceding guard
         *     ...
         *     guard(C2); // thisGuard
         * </pre>
         *
         * If C2 => C1, transform to:
         *
         * <pre>
         *     guard(C2);
         *     ...
         * </pre>
         */
        protected boolean foldPendingTest(DeoptimizingGuard thisGuard, ValueNode original, Stamp newStamp, GuardRewirer rewireGuardFunction) {
            for (DeoptimizingGuard pendingGuard : pendingTests) {
                LogicNode pendingCondition = pendingGuard.getCondition();
                TriState result = TriState.UNKNOWN;
                if (pendingCondition instanceof UnaryOpLogicNode) {
                    UnaryOpLogicNode unaryLogicNode = (UnaryOpLogicNode) pendingCondition;
                    if (unaryLogicNode.getValue() == original) {
                        result = unaryLogicNode.tryFold(newStamp);
                    }
                } else if (pendingCondition instanceof BinaryOpLogicNode) {
                    BinaryOpLogicNode binaryOpLogicNode = (BinaryOpLogicNode) pendingCondition;
                    ValueNode x = binaryOpLogicNode.getX();
                    ValueNode y = binaryOpLogicNode.getY();
                    if (x == original) {
                        result = binaryOpLogicNode.tryFold(newStamp, getOtherSafeStamp(y));
                    } else if (y == original) {
                        result = binaryOpLogicNode.tryFold(getOtherSafeStamp(x), newStamp);
                    } else if (binaryOpLogicNode instanceof IntegerEqualsNode && y.isConstant() && x instanceof AndNode) {
                        AndNode and = (AndNode) x;
                        if (and.getY() == y && and.getX() == original) {
                            BinaryOp<And> andOp = ArithmeticOpTable.forStamp(newStamp).getAnd();
                            result = binaryOpLogicNode.tryFold(andOp.foldStamp(newStamp, getOtherSafeStamp(y)), getOtherSafeStamp(y));
                        }
                    }
                }
                if (result.isKnown()) {
                    /*
                     * The test case be folded using the information available but the test can only
                     * be moved up if we're sure there's no schedule dependence.
                     */
                    if (canScheduleAbove(thisGuard.getCondition(), pendingGuard.asNode(), original) && foldGuard(thisGuard, pendingGuard, result.toBoolean(), newStamp, rewireGuardFunction)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean canScheduleAbove(Node n, Node target, ValueNode knownToBeAbove) {
            Block targetBlock = nodeToBlock.get(target);
            Block testBlock = nodeToBlock.get(n);
            if (targetBlock != null && testBlock != null) {
                if (targetBlock == testBlock) {
                    for (Node fixed : blockToNodes.get(targetBlock)) {
                        if (fixed == n) {
                            return true;
                        } else if (fixed == target) {
                            break;
                        }
                    }
                } else if (AbstractControlFlowGraph.dominates(testBlock, targetBlock)) {
                    return true;
                }
            }
            InputFilter v = new InputFilter(knownToBeAbove);
            n.applyInputs(v);
            return v.ok;
        }

        protected boolean foldGuard(DeoptimizingGuard thisGuard, DeoptimizingGuard otherGuard, boolean outcome, Stamp guardedValueStamp, GuardRewirer rewireGuardFunction) {
            DeoptimizationAction action = mergeActions(otherGuard.getAction(), thisGuard.getAction());
            if (action != null && otherGuard.getSpeculation() == thisGuard.getSpeculation()) {
                LogicNode condition = (LogicNode) thisGuard.getCondition().copyWithInputs();
                /*
                 * We have ...; guard(C1); guard(C2);...
                 *
                 * Where the first guard is `otherGuard` and the second one `thisGuard`.
                 *
                 * Depending on `outcome`, we have C2 => C1 or C2 => !C1.
                 *
                 * - If C2 => C1, `mustDeopt` below is false and we transform to ...; guard(C2); ...
                 *
                 * - If C2 => !C1, `mustDeopt` is true and we transform to ..; guard(C1); deopt;
                 */
                // for the second case, the action of the deopt is copied from there:
                thisGuard.setAction(action);
                GuardRewirer rewirer = (guard, result, innerGuardedValueStamp, newInput) -> {
                    // `result` is `outcome`, `guard` is `otherGuard`
                    boolean mustDeopt = result == otherGuard.isNegated();
                    if (rewireGuardFunction.rewire(guard, mustDeopt == thisGuard.isNegated(), innerGuardedValueStamp, newInput)) {
                        if (!mustDeopt) {
                            otherGuard.setCondition(condition, thisGuard.isNegated());
                            otherGuard.setAction(action);
                            otherGuard.setReason(thisGuard.getReason());
                        }
                        return true;
                    }
                    condition.safeDelete();
                    return false;
                };
                // Move the later test up
                return rewireGuards(otherGuard, outcome, null, guardedValueStamp, rewirer);
            }
            return false;
        }

        protected boolean tryProveGuardCondition(DeoptimizingGuard thisGuard, LogicNode node, GuardRewirer rewireGuardFunction) {
            return ConditionalEliminationUtil.tryProveGuardCondition(infoElementProvider, conditions, guardFolding, thisGuard, node, rewireGuardFunction);
        }

        protected void registerCondition(LogicNode condition, boolean negated, GuardingNode guard) {
            if (condition.hasMoreThanOneUsage()) {
                registerNewStamp(condition, negated ? StampFactory.contradiction() : StampFactory.tautology(), guard);
            }
            conditions.push(new GuardedCondition(guard, condition, negated));
        }

        protected InfoElement getInfoElements(ValueNode proxiedValue) {
            if (proxiedValue == null) {
                return null;
            }
            InfoElement infoElement = map.getAndGrow(proxiedValue);
            if (infoElement == null) {
                infoElement = map.getAndGrow(GraphUtil.skipPi(proxiedValue));
            }
            return infoElement;
        }

        private InfoElement nextElement(InfoElement current) {
            InfoElement parent = current.getParent();
            if (parent != null) {
                return parent;
            } else {
                ValueNode proxifiedInput = current.getProxifiedInput();
                if (proxifiedInput instanceof PiNode) {
                    PiNode piNode = (PiNode) proxifiedInput;
                    return getInfoElements(piNode.getOriginalNode());
                }
            }
            return null;
        }

        protected void registerNewStamp(ValueNode maybeProxiedValue, Stamp newStamp, GuardingNode guard) {
            registerNewStamp(maybeProxiedValue, newStamp, guard, false);
        }

        protected void registerNewStamp(ValueNode maybeProxiedValue, Stamp newStamp, GuardingNode guard, boolean propagateThroughPis) {
            assert maybeProxiedValue != null;
            assert guard != null;

            if (newStamp == null || newStamp.isUnrestricted()) {
                return;
            }

            ValueNode value = maybeProxiedValue;
            Stamp stamp = newStamp;

            while (stamp != null && value != null) {
                ValueNode proxiedValue = null;
                if (value instanceof PiNode) {
                    proxiedValue = value;
                }
                counterStampsRegistered.increment(debug);
                debug.log("\t Saving stamp for node %s stamp %s guarded by %s", value, stamp, guard);
                assert value instanceof LogicNode || stamp.isCompatible(value.stamp(NodeView.DEFAULT)) : stamp + " vs. " + value.stamp(NodeView.DEFAULT) + " (" + value + ")";
                map.setAndGrow(value, new InfoElement(stamp, guard, proxiedValue, map.getAndGrow(value)));
                undoOperations.push(value);
                if (propagateThroughPis && value instanceof PiNode) {
                    PiNode piNode = (PiNode) value;
                    value = piNode.getOriginalNode();
                } else if (value instanceof StampInverter) {
                    StampInverter stampInverter = (StampInverter) value;
                    value = stampInverter.getValue();
                    stamp = stampInverter.invertStamp(stamp);
                } else {
                    break;
                }
            }
        }

        protected void processAbstractBegin(AbstractBeginNode beginNode) {
            Node predecessor = beginNode.predecessor();
            if (predecessor instanceof IfNode) {
                IfNode ifNode = (IfNode) predecessor;
                boolean negated = (ifNode.falseSuccessor() == beginNode);
                LogicNode condition = ifNode.condition();
                registerNewCondition(condition, negated, beginNode);
            } else if (predecessor instanceof TypeSwitchNode) {
                TypeSwitchNode typeSwitch = (TypeSwitchNode) predecessor;
                processTypeSwitch(beginNode, typeSwitch);
            } else if (predecessor instanceof IntegerSwitchNode) {
                IntegerSwitchNode integerSwitchNode = (IntegerSwitchNode) predecessor;
                processIntegerSwitch(beginNode, integerSwitchNode);
            }
        }

        private static boolean maybeMultipleUsages(ValueNode value) {
            if (value.hasMoreThanOneUsage()) {
                return true;
            } else {
                return value instanceof ProxyNode ||
                                value instanceof PiNode ||
                                value instanceof StampInverter;
            }
        }

        protected void processIntegerSwitch(AbstractBeginNode beginNode, IntegerSwitchNode integerSwitchNode) {
            ValueNode value = integerSwitchNode.value();
            if (maybeMultipleUsages(value)) {
                Stamp stamp = integerSwitchNode.getValueStampForSuccessor(beginNode);
                if (stamp != null) {
                    registerNewStamp(value, stamp, beginNode);
                }
            }
        }

        protected void processTypeSwitch(AbstractBeginNode beginNode, TypeSwitchNode typeSwitch) {
            ValueNode hub = typeSwitch.value();
            if (hub instanceof LoadHubNode) {
                LoadHubNode loadHub = (LoadHubNode) hub;
                ValueNode value = loadHub.getValue();
                if (maybeMultipleUsages(value)) {
                    Stamp stamp = typeSwitch.getValueStampForSuccessor(beginNode);
                    if (stamp != null) {
                        registerNewStamp(value, stamp, beginNode);
                    }
                }
            }
        }

        @Override
        public void exit(Block b, Marks marks) {
            int infoElementsMark = marks.infoElementOperations;
            while (undoOperations.size() > infoElementsMark) {
                Node node = undoOperations.pop();
                if (node.isAlive()) {
                    map.set(node, map.get(node).getParent());
                }
            }

            int conditionsMark = marks.conditions;
            while (conditions.size() > conditionsMark) {
                conditions.pop();
            }
        }
    }

    @Override
    public float codeSizeIncrease() {
        return 1.5f;
    }
}
