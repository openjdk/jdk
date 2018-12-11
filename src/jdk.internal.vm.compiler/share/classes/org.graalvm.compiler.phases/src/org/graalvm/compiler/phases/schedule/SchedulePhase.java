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


package org.graalvm.compiler.phases.schedule;

import static jdk.internal.vm.compiler.collections.Equivalence.IDENTITY;
import static org.graalvm.compiler.core.common.GraalOptions.GuardPriorities;
import static org.graalvm.compiler.core.common.GraalOptions.OptScheduleOutOfLoops;
import static org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph.strictlyDominates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.graph.Graph.NodeEvent;
import org.graalvm.compiler.graph.Graph.NodeEventListener;
import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.NodeStack;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.KillingBeginNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StaticDeoptimizingNode;
import org.graalvm.compiler.nodes.StaticDeoptimizingNode.GuardPriority;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.VirtualState;
import org.graalvm.compiler.nodes.calc.ConvertNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.cfg.HIRLoop;
import org.graalvm.compiler.nodes.cfg.LocationSet;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.spi.ValueProxy;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.Phase;
import jdk.internal.vm.compiler.word.LocationIdentity;

public final class SchedulePhase extends Phase {

    public enum SchedulingStrategy {
        EARLIEST_WITH_GUARD_ORDER,
        EARLIEST,
        LATEST,
        LATEST_OUT_OF_LOOPS,
        FINAL_SCHEDULE;

        public boolean isEarliest() {
            return this == EARLIEST || this == EARLIEST_WITH_GUARD_ORDER;
        }

        public boolean isLatest() {
            return !isEarliest();
        }
    }

    private final SchedulingStrategy selectedStrategy;

    private final boolean immutableGraph;

    public SchedulePhase(OptionValues options) {
        this(false, options);
    }

    public SchedulePhase(boolean immutableGraph, OptionValues options) {
        this(OptScheduleOutOfLoops.getValue(options) ? SchedulingStrategy.LATEST_OUT_OF_LOOPS : SchedulingStrategy.LATEST, immutableGraph);
    }

    public SchedulePhase(SchedulingStrategy strategy) {
        this(strategy, false);
    }

    public SchedulePhase(SchedulingStrategy strategy, boolean immutableGraph) {
        this.selectedStrategy = strategy;
        this.immutableGraph = immutableGraph;
    }

    private NodeEventScope verifyImmutableGraph(StructuredGraph graph) {
        if (immutableGraph && Assertions.assertionsEnabled()) {
            return graph.trackNodeEvents(new NodeEventListener() {
                @Override
                public void changed(NodeEvent e, Node node) {
                    assert false : "graph changed: " + e + " on node " + node;
                }
            });
        } else {
            return null;
        }
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph) {
        try (NodeEventScope scope = verifyImmutableGraph(graph)) {
            Instance inst = new Instance();
            inst.run(graph, selectedStrategy, immutableGraph);
        }
    }

    public static void run(StructuredGraph graph, SchedulingStrategy strategy, ControlFlowGraph cfg) {
        Instance inst = new Instance(cfg);
        inst.run(graph, strategy, false);
    }

    public static class Instance {

        private static final double IMPLICIT_NULL_CHECK_OPPORTUNITY_PROBABILITY_FACTOR = 2;
        /**
         * Map from blocks to the nodes in each block.
         */
        protected ControlFlowGraph cfg;
        protected BlockMap<List<Node>> blockToNodesMap;
        protected NodeMap<Block> nodeToBlockMap;

        public Instance() {
            this(null);
        }

        public Instance(ControlFlowGraph cfg) {
            this.cfg = cfg;
        }

        @SuppressWarnings("try")
        public void run(StructuredGraph graph, SchedulingStrategy selectedStrategy, boolean immutableGraph) {
            // assert GraphOrder.assertNonCyclicGraph(graph);

            if (this.cfg == null) {
                this.cfg = ControlFlowGraph.compute(graph, true, true, true, false);
            }

            NodeMap<Block> currentNodeMap = graph.createNodeMap();
            NodeBitMap visited = graph.createNodeBitMap();
            BlockMap<List<Node>> earliestBlockToNodesMap = new BlockMap<>(cfg);
            this.nodeToBlockMap = currentNodeMap;
            this.blockToNodesMap = earliestBlockToNodesMap;

            scheduleEarliestIterative(earliestBlockToNodesMap, currentNodeMap, visited, graph, immutableGraph, selectedStrategy == SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER);

            if (!selectedStrategy.isEarliest()) {
                // For non-earliest schedules, we need to do a second pass.
                BlockMap<List<Node>> latestBlockToNodesMap = new BlockMap<>(cfg);
                for (Block b : cfg.getBlocks()) {
                    latestBlockToNodesMap.put(b, new ArrayList<>());
                }

                BlockMap<ArrayList<FloatingReadNode>> watchListMap = calcLatestBlocks(selectedStrategy, currentNodeMap, earliestBlockToNodesMap, visited, latestBlockToNodesMap, immutableGraph);
                sortNodesLatestWithinBlock(cfg, earliestBlockToNodesMap, latestBlockToNodesMap, currentNodeMap, watchListMap, visited);

                assert verifySchedule(cfg, latestBlockToNodesMap, currentNodeMap);
                assert (!Assertions.detailedAssertionsEnabled(graph.getOptions())) || MemoryScheduleVerification.check(cfg.getStartBlock(), latestBlockToNodesMap);

                this.blockToNodesMap = latestBlockToNodesMap;

            }
            cfg.setNodeToBlock(currentNodeMap);

            graph.setLastSchedule(new ScheduleResult(this.cfg, this.nodeToBlockMap, this.blockToNodesMap));
        }

        @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE", justification = "false positive found by findbugs")
        private BlockMap<ArrayList<FloatingReadNode>> calcLatestBlocks(SchedulingStrategy strategy, NodeMap<Block> currentNodeMap, BlockMap<List<Node>> earliestBlockToNodesMap, NodeBitMap visited,
                        BlockMap<List<Node>> latestBlockToNodesMap, boolean immutableGraph) {
            BlockMap<ArrayList<FloatingReadNode>> watchListMap = new BlockMap<>(cfg);
            Block[] reversePostOrder = cfg.reversePostOrder();
            for (int j = reversePostOrder.length - 1; j >= 0; --j) {
                Block currentBlock = reversePostOrder[j];
                List<Node> blockToNodes = earliestBlockToNodesMap.get(currentBlock);
                LocationSet killed = null;
                int previousIndex = blockToNodes.size();
                for (int i = blockToNodes.size() - 1; i >= 0; --i) {
                    Node currentNode = blockToNodes.get(i);
                    assert currentNodeMap.get(currentNode) == currentBlock;
                    assert !(currentNode instanceof PhiNode) && !(currentNode instanceof ProxyNode);
                    assert visited.isMarked(currentNode);
                    if (currentNode instanceof FixedNode) {
                        // For these nodes, the earliest is at the same time the latest block.
                    } else {
                        Block latestBlock = null;

                        LocationIdentity constrainingLocation = null;
                        if (currentNode instanceof FloatingReadNode) {
                            // We are scheduling a floating read node => check memory
                            // anti-dependencies.
                            FloatingReadNode floatingReadNode = (FloatingReadNode) currentNode;
                            LocationIdentity location = floatingReadNode.getLocationIdentity();
                            if (location.isMutable()) {
                                // Location can be killed.
                                constrainingLocation = location;
                                if (currentBlock.canKill(location)) {
                                    if (killed == null) {
                                        killed = new LocationSet();
                                    }
                                    fillKillSet(killed, blockToNodes.subList(i + 1, previousIndex));
                                    previousIndex = i;
                                    if (killed.contains(location)) {
                                        // Earliest block kills location => we need to stay within
                                        // earliest block.
                                        latestBlock = currentBlock;
                                    }
                                }
                            }
                        }

                        if (latestBlock == null) {
                            // We are not constraint within earliest block => calculate optimized
                            // schedule.
                            calcLatestBlock(currentBlock, strategy, currentNode, currentNodeMap, constrainingLocation, watchListMap, latestBlockToNodesMap, visited, immutableGraph);
                        } else {
                            selectLatestBlock(currentNode, currentBlock, latestBlock, currentNodeMap, watchListMap, constrainingLocation, latestBlockToNodesMap);
                        }
                    }
                }
            }
            return watchListMap;
        }

        protected static void selectLatestBlock(Node currentNode, Block currentBlock, Block latestBlock, NodeMap<Block> currentNodeMap, BlockMap<ArrayList<FloatingReadNode>> watchListMap,
                        LocationIdentity constrainingLocation, BlockMap<List<Node>> latestBlockToNodesMap) {

            assert checkLatestEarliestRelation(currentNode, currentBlock, latestBlock);
            if (currentBlock != latestBlock) {

                currentNodeMap.setAndGrow(currentNode, latestBlock);

                if (constrainingLocation != null && latestBlock.canKill(constrainingLocation)) {
                    if (watchListMap.get(latestBlock) == null) {
                        watchListMap.put(latestBlock, new ArrayList<>());
                    }
                    watchListMap.get(latestBlock).add((FloatingReadNode) currentNode);
                }
            }

            latestBlockToNodesMap.get(latestBlock).add(currentNode);
        }

        private static boolean checkLatestEarliestRelation(Node currentNode, Block earliestBlock, Block latestBlock) {
            assert AbstractControlFlowGraph.dominates(earliestBlock, latestBlock) || (currentNode instanceof VirtualState && latestBlock == earliestBlock.getDominator()) : String.format(
                            "%s %s (%s) %s (%s)", currentNode, earliestBlock, earliestBlock.getBeginNode(), latestBlock, latestBlock.getBeginNode());
            return true;
        }

        private static boolean verifySchedule(ControlFlowGraph cfg, BlockMap<List<Node>> blockToNodesMap, NodeMap<Block> nodeMap) {
            for (Block b : cfg.getBlocks()) {
                List<Node> nodes = blockToNodesMap.get(b);
                for (Node n : nodes) {
                    assert n.isAlive();
                    assert nodeMap.get(n) == b;
                    StructuredGraph g = (StructuredGraph) n.graph();
                    if (g.hasLoops() && g.getGuardsStage() == GuardsStage.AFTER_FSA && n instanceof DeoptimizeNode) {
                        assert b.getLoopDepth() == 0 : n;
                    }
                }
            }
            return true;
        }

        public static Block checkKillsBetween(Block earliestBlock, Block latestBlock, LocationIdentity location) {
            assert strictlyDominates(earliestBlock, latestBlock);
            Block current = latestBlock.getDominator();

            // Collect dominator chain that needs checking.
            List<Block> dominatorChain = new ArrayList<>();
            dominatorChain.add(latestBlock);
            while (current != earliestBlock) {
                // Current is an intermediate dominator between earliestBlock and latestBlock.
                assert strictlyDominates(earliestBlock, current) && strictlyDominates(current, latestBlock);
                if (current.canKill(location)) {
                    dominatorChain.clear();
                }
                dominatorChain.add(current);
                current = current.getDominator();
            }

            // The first element of dominatorChain now contains the latest possible block.
            assert dominatorChain.size() >= 1;
            assert dominatorChain.get(dominatorChain.size() - 1).getDominator() == earliestBlock;

            Block lastBlock = earliestBlock;
            for (int i = dominatorChain.size() - 1; i >= 0; --i) {
                Block currentBlock = dominatorChain.get(i);
                if (currentBlock.getLoopDepth() > lastBlock.getLoopDepth()) {
                    // We are entering a loop boundary. The new loops must not kill the location for
                    // the crossing to be safe.
                    if (currentBlock.getLoop() != null && ((HIRLoop) currentBlock.getLoop()).canKill(location)) {
                        break;
                    }
                }

                if (currentBlock.canKillBetweenThisAndDominator(location)) {
                    break;
                }
                lastBlock = currentBlock;
            }

            if (lastBlock.getBeginNode() instanceof KillingBeginNode) {
                LocationIdentity locationIdentity = ((KillingBeginNode) lastBlock.getBeginNode()).getLocationIdentity();
                if ((locationIdentity.isAny() || locationIdentity.equals(location)) && lastBlock != earliestBlock) {
                    // The begin of this block kills the location, so we *have* to schedule the node
                    // in the dominating block.
                    lastBlock = lastBlock.getDominator();
                }
            }

            return lastBlock;
        }

        private static void fillKillSet(LocationSet killed, List<Node> subList) {
            if (!killed.isAny()) {
                for (Node n : subList) {
                    // Check if this node kills a node in the watch list.
                    if (n instanceof MemoryCheckpoint.Single) {
                        LocationIdentity identity = ((MemoryCheckpoint.Single) n).getLocationIdentity();
                        killed.add(identity);
                        if (killed.isAny()) {
                            return;
                        }
                    } else if (n instanceof MemoryCheckpoint.Multi) {
                        for (LocationIdentity identity : ((MemoryCheckpoint.Multi) n).getLocationIdentities()) {
                            killed.add(identity);
                            if (killed.isAny()) {
                                return;
                            }
                        }
                    }
                }
            }
        }

        private static void sortNodesLatestWithinBlock(ControlFlowGraph cfg, BlockMap<List<Node>> earliestBlockToNodesMap, BlockMap<List<Node>> latestBlockToNodesMap, NodeMap<Block> currentNodeMap,
                        BlockMap<ArrayList<FloatingReadNode>> watchListMap, NodeBitMap visited) {
            for (Block b : cfg.getBlocks()) {
                sortNodesLatestWithinBlock(b, earliestBlockToNodesMap, latestBlockToNodesMap, currentNodeMap, watchListMap, visited);
            }
        }

        private static void sortNodesLatestWithinBlock(Block b, BlockMap<List<Node>> earliestBlockToNodesMap, BlockMap<List<Node>> latestBlockToNodesMap, NodeMap<Block> nodeMap,
                        BlockMap<ArrayList<FloatingReadNode>> watchListMap, NodeBitMap unprocessed) {
            List<Node> earliestSorting = earliestBlockToNodesMap.get(b);
            ArrayList<Node> result = new ArrayList<>(earliestSorting.size());
            ArrayList<FloatingReadNode> watchList = null;
            if (watchListMap != null) {
                watchList = watchListMap.get(b);
                assert watchList == null || !b.getKillLocations().isEmpty();
            }
            AbstractBeginNode beginNode = b.getBeginNode();
            if (beginNode instanceof LoopExitNode) {
                LoopExitNode loopExitNode = (LoopExitNode) beginNode;
                for (ProxyNode proxy : loopExitNode.proxies()) {
                    unprocessed.clear(proxy);
                    ValueNode value = proxy.value();
                    // if multiple proxies reference the same value, schedule the value of a
                    // proxy once
                    if (value != null && nodeMap.get(value) == b && unprocessed.isMarked(value)) {
                        sortIntoList(value, b, result, nodeMap, unprocessed, null);
                    }
                }
            }
            FixedNode endNode = b.getEndNode();
            FixedNode fixedEndNode = null;
            if (isFixedEnd(endNode)) {
                // Only if the end node is either a control split or an end node, we need to force
                // it to be the last node in the schedule.
                fixedEndNode = endNode;
            }
            for (Node n : earliestSorting) {
                if (n != fixedEndNode) {
                    if (n instanceof FixedNode) {
                        assert nodeMap.get(n) == b;
                        checkWatchList(b, nodeMap, unprocessed, result, watchList, n);
                        sortIntoList(n, b, result, nodeMap, unprocessed, null);
                    } else if (nodeMap.get(n) == b && n instanceof FloatingReadNode) {
                        FloatingReadNode floatingReadNode = (FloatingReadNode) n;
                        if (isImplicitNullOpportunity(floatingReadNode, b)) {
                            // Schedule at the beginning of the block.
                            sortIntoList(floatingReadNode, b, result, nodeMap, unprocessed, null);
                        } else {
                            LocationIdentity location = floatingReadNode.getLocationIdentity();
                            if (b.canKill(location)) {
                                // This read can be killed in this block, add to watch list.
                                if (watchList == null) {
                                    watchList = new ArrayList<>();
                                }
                                watchList.add(floatingReadNode);
                            }
                        }
                    }
                }
            }

            for (Node n : latestBlockToNodesMap.get(b)) {
                assert nodeMap.get(n) == b : n;
                assert !(n instanceof FixedNode);
                if (unprocessed.isMarked(n)) {
                    sortIntoList(n, b, result, nodeMap, unprocessed, fixedEndNode);
                }
            }

            if (endNode != null && unprocessed.isMarked(endNode)) {
                sortIntoList(endNode, b, result, nodeMap, unprocessed, null);
            }

            latestBlockToNodesMap.put(b, result);
        }

        private static void checkWatchList(Block b, NodeMap<Block> nodeMap, NodeBitMap unprocessed, ArrayList<Node> result, ArrayList<FloatingReadNode> watchList, Node n) {
            if (watchList != null && !watchList.isEmpty()) {
                // Check if this node kills a node in the watch list.
                if (n instanceof MemoryCheckpoint.Single) {
                    LocationIdentity identity = ((MemoryCheckpoint.Single) n).getLocationIdentity();
                    checkWatchList(watchList, identity, b, result, nodeMap, unprocessed);
                } else if (n instanceof MemoryCheckpoint.Multi) {
                    for (LocationIdentity identity : ((MemoryCheckpoint.Multi) n).getLocationIdentities()) {
                        checkWatchList(watchList, identity, b, result, nodeMap, unprocessed);
                    }
                }
            }
        }

        private static void checkWatchList(ArrayList<FloatingReadNode> watchList, LocationIdentity identity, Block b, ArrayList<Node> result, NodeMap<Block> nodeMap, NodeBitMap unprocessed) {
            if (identity.isImmutable()) {
                // Nothing to do. This can happen for an initialization write.
            } else if (identity.isAny()) {
                for (FloatingReadNode r : watchList) {
                    if (unprocessed.isMarked(r)) {
                        sortIntoList(r, b, result, nodeMap, unprocessed, null);
                    }
                }
                watchList.clear();
            } else {
                int index = 0;
                while (index < watchList.size()) {
                    FloatingReadNode r = watchList.get(index);
                    LocationIdentity locationIdentity = r.getLocationIdentity();
                    assert locationIdentity.isMutable();
                    if (unprocessed.isMarked(r)) {
                        if (identity.overlaps(locationIdentity)) {
                            sortIntoList(r, b, result, nodeMap, unprocessed, null);
                        } else {
                            ++index;
                            continue;
                        }
                    }
                    int lastIndex = watchList.size() - 1;
                    watchList.set(index, watchList.get(lastIndex));
                    watchList.remove(lastIndex);
                }
            }
        }

        private static void sortIntoList(Node n, Block b, ArrayList<Node> result, NodeMap<Block> nodeMap, NodeBitMap unprocessed, Node excludeNode) {
            assert unprocessed.isMarked(n) : n;
            assert nodeMap.get(n) == b;

            if (n instanceof PhiNode) {
                return;
            }

            unprocessed.clear(n);

            for (Node input : n.inputs()) {
                if (nodeMap.get(input) == b && unprocessed.isMarked(input) && input != excludeNode) {
                    sortIntoList(input, b, result, nodeMap, unprocessed, excludeNode);
                }
            }

            if (n instanceof ProxyNode) {
                // Skip proxy nodes.
            } else {
                result.add(n);
            }

        }

        protected void calcLatestBlock(Block earliestBlock, SchedulingStrategy strategy, Node currentNode, NodeMap<Block> currentNodeMap, LocationIdentity constrainingLocation,
                        BlockMap<ArrayList<FloatingReadNode>> watchListMap, BlockMap<List<Node>> latestBlockToNodesMap, NodeBitMap visited, boolean immutableGraph) {
            Block latestBlock = null;
            if (!currentNode.hasUsages()) {
                assert currentNode instanceof GuardNode;
                latestBlock = earliestBlock;
            } else {
                assert currentNode.hasUsages();
                for (Node usage : currentNode.usages()) {
                    if (immutableGraph && !visited.contains(usage)) {
                        /*
                         * Normally, dead nodes are deleted by the scheduler before we reach this
                         * point. Only when the scheduler is asked to not modify a graph, we can see
                         * dead nodes here.
                         */
                        continue;
                    }
                    latestBlock = calcBlockForUsage(currentNode, usage, latestBlock, currentNodeMap);
                }

                assert latestBlock != null : currentNode;

                if (strategy == SchedulingStrategy.FINAL_SCHEDULE || strategy == SchedulingStrategy.LATEST_OUT_OF_LOOPS) {
                    Block currentBlock = latestBlock;
                    while (currentBlock.getLoopDepth() > earliestBlock.getLoopDepth() && currentBlock != earliestBlock.getDominator()) {
                        Block previousCurrentBlock = currentBlock;
                        currentBlock = currentBlock.getDominator();
                        if (previousCurrentBlock.isLoopHeader()) {
                            if (currentBlock.getRelativeFrequency() < latestBlock.getRelativeFrequency() || ((StructuredGraph) currentNode.graph()).hasValueProxies()) {
                                // Only assign new latest block if frequency is actually lower or if
                                // loop proxies would be required otherwise.
                                latestBlock = currentBlock;
                            }
                        }
                    }
                }

                if (latestBlock != earliestBlock && latestBlock != earliestBlock.getDominator() && constrainingLocation != null) {
                    latestBlock = checkKillsBetween(earliestBlock, latestBlock, constrainingLocation);
                }
            }

            if (latestBlock != earliestBlock && currentNode instanceof FloatingReadNode) {

                FloatingReadNode floatingReadNode = (FloatingReadNode) currentNode;
                if (isImplicitNullOpportunity(floatingReadNode, earliestBlock) &&
                                earliestBlock.getRelativeFrequency() < latestBlock.getRelativeFrequency() * IMPLICIT_NULL_CHECK_OPPORTUNITY_PROBABILITY_FACTOR) {
                    latestBlock = earliestBlock;
                }
            }

            selectLatestBlock(currentNode, earliestBlock, latestBlock, currentNodeMap, watchListMap, constrainingLocation, latestBlockToNodesMap);
        }

        private static boolean isImplicitNullOpportunity(FloatingReadNode floatingReadNode, Block block) {

            Node pred = block.getBeginNode().predecessor();
            if (pred instanceof IfNode) {
                IfNode ifNode = (IfNode) pred;
                if (ifNode.condition() instanceof IsNullNode) {
                    IsNullNode isNullNode = (IsNullNode) ifNode.condition();
                    if (getUnproxifiedUncompressed(floatingReadNode.getAddress().getBase()) == getUnproxifiedUncompressed(isNullNode.getValue())) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static Node getUnproxifiedUncompressed(Node node) {
            Node result = node;
            while (true) {
                if (result instanceof ValueProxy) {
                    ValueProxy valueProxy = (ValueProxy) result;
                    result = valueProxy.getOriginalNode();
                } else if (result instanceof ConvertNode) {
                    ConvertNode convertNode = (ConvertNode) result;
                    if (convertNode.mayNullCheckSkipConversion()) {
                        result = convertNode.getValue();
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            return result;
        }

        private static Block calcBlockForUsage(Node node, Node usage, Block startBlock, NodeMap<Block> currentNodeMap) {
            assert !(node instanceof PhiNode);
            Block currentBlock = startBlock;
            if (usage instanceof PhiNode) {
                // An input to a PhiNode is used at the end of the predecessor block that
                // corresponds to the PhiNode input. One PhiNode can use an input multiple times.
                PhiNode phi = (PhiNode) usage;
                AbstractMergeNode merge = phi.merge();
                Block mergeBlock = currentNodeMap.get(merge);
                for (int i = 0; i < phi.valueCount(); ++i) {
                    if (phi.valueAt(i) == node) {
                        Block otherBlock = mergeBlock.getPredecessors()[i];
                        currentBlock = AbstractControlFlowGraph.commonDominatorTyped(currentBlock, otherBlock);
                    }
                }
            } else if (usage instanceof AbstractBeginNode) {
                AbstractBeginNode abstractBeginNode = (AbstractBeginNode) usage;
                if (abstractBeginNode instanceof StartNode) {
                    currentBlock = AbstractControlFlowGraph.commonDominatorTyped(currentBlock, currentNodeMap.get(abstractBeginNode));
                } else {
                    Block otherBlock = currentNodeMap.get(abstractBeginNode).getDominator();
                    currentBlock = AbstractControlFlowGraph.commonDominatorTyped(currentBlock, otherBlock);
                }
            } else {
                // All other types of usages: Put the input into the same block as the usage.
                Block otherBlock = currentNodeMap.get(usage);
                if (usage instanceof ProxyNode) {
                    ProxyNode proxyNode = (ProxyNode) usage;
                    otherBlock = currentNodeMap.get(proxyNode.proxyPoint());

                }
                currentBlock = AbstractControlFlowGraph.commonDominatorTyped(currentBlock, otherBlock);
            }
            return currentBlock;
        }

        /**
         * Micro block that is allocated for each fixed node and captures all floating nodes that
         * need to be scheduled immediately after the corresponding fixed node.
         */
        private static class MicroBlock {
            private final int id;
            private int nodeCount;
            private NodeEntry head;
            private NodeEntry tail;

            MicroBlock(int id) {
                this.id = id;
            }

            /**
             * Adds a new floating node into the micro block.
             */
            public void add(Node node) {
                assert !(node instanceof FixedNode) : node;
                NodeEntry newTail = new NodeEntry(node);
                if (tail == null) {
                    tail = head = newTail;
                } else {
                    tail.next = newTail;
                    tail = newTail;
                }
                nodeCount++;
            }

            /**
             * Number of nodes in this micro block.
             */
            public int getNodeCount() {
                assert getActualNodeCount() == nodeCount : getActualNodeCount() + " != " + nodeCount;
                return nodeCount;
            }

            private int getActualNodeCount() {
                int count = 0;
                for (NodeEntry e = head; e != null; e = e.next) {
                    count++;
                }
                return count;
            }

            /**
             * The id of the micro block, with a block always associated with a lower id than its
             * successors.
             */
            public int getId() {
                return id;
            }

            /**
             * First node of the linked list of nodes of this micro block.
             */
            public NodeEntry getFirstNode() {
                return head;
            }

            /**
             * Takes all nodes in this micro blocks and prepends them to the nodes of the given
             * parameter.
             *
             * @param newBlock the new block for the nodes
             */
            public void prependChildrenTo(MicroBlock newBlock) {
                if (tail != null) {
                    assert head != null;
                    tail.next = newBlock.head;
                    newBlock.head = head;
                    head = tail = null;
                    newBlock.nodeCount += nodeCount;
                    nodeCount = 0;
                }
            }

            @Override
            public String toString() {
                return String.format("MicroBlock[id=%d]", id);
            }

            @Override
            public int hashCode() {
                return id;
            }
        }

        /**
         * Entry in the linked list of nodes.
         */
        private static class NodeEntry {
            private final Node node;
            private NodeEntry next;

            NodeEntry(Node node) {
                this.node = node;
                this.next = null;
            }

            public NodeEntry getNext() {
                return next;
            }

            public Node getNode() {
                return node;
            }
        }

        private void scheduleEarliestIterative(BlockMap<List<Node>> blockToNodes, NodeMap<Block> nodeToBlock, NodeBitMap visited, StructuredGraph graph, boolean immutableGraph,
                        boolean withGuardOrder) {

            NodeMap<MicroBlock> entries = graph.createNodeMap();
            NodeStack stack = new NodeStack();

            // Initialize with fixed nodes.
            MicroBlock startBlock = null;
            int nextId = 1;
            for (Block b : cfg.reversePostOrder()) {
                for (FixedNode current : b.getBeginNode().getBlockNodes()) {
                    MicroBlock microBlock = new MicroBlock(nextId++);
                    entries.set(current, microBlock);
                    boolean isNew = visited.checkAndMarkInc(current);
                    assert isNew;
                    if (startBlock == null) {
                        startBlock = microBlock;
                    }
                }
            }

            if (graph.getGuardsStage().allowsFloatingGuards() && graph.getNodes(GuardNode.TYPE).isNotEmpty()) {
                // Now process guards.
                if (GuardPriorities.getValue(graph.getOptions()) && withGuardOrder) {
                    EnumMap<GuardPriority, List<GuardNode>> guardsByPriority = new EnumMap<>(GuardPriority.class);
                    for (GuardNode guard : graph.getNodes(GuardNode.TYPE)) {
                        guardsByPriority.computeIfAbsent(guard.computePriority(), p -> new ArrayList<>()).add(guard);
                    }
                    // `EnumMap.values` returns values in "natural" key order
                    for (List<GuardNode> guards : guardsByPriority.values()) {
                        processNodes(visited, entries, stack, startBlock, guards);
                    }
                    GuardOrder.resortGuards(graph, entries, stack);
                } else {
                    processNodes(visited, entries, stack, startBlock, graph.getNodes(GuardNode.TYPE));
                }
            } else {
                assert graph.getNodes(GuardNode.TYPE).isEmpty();
            }

            // Now process inputs of fixed nodes.
            for (Block b : cfg.reversePostOrder()) {
                for (FixedNode current : b.getBeginNode().getBlockNodes()) {
                    processNodes(visited, entries, stack, startBlock, current.inputs());
                }
            }

            if (visited.getCounter() < graph.getNodeCount()) {
                // Visit back input edges of loop phis.
                boolean changed;
                boolean unmarkedPhi;
                do {
                    changed = false;
                    unmarkedPhi = false;
                    for (LoopBeginNode loopBegin : graph.getNodes(LoopBeginNode.TYPE)) {
                        for (PhiNode phi : loopBegin.phis()) {
                            if (visited.isMarked(phi)) {
                                for (int i = 0; i < loopBegin.getLoopEndCount(); ++i) {
                                    Node node = phi.valueAt(i + loopBegin.forwardEndCount());
                                    if (node != null && entries.get(node) == null) {
                                        changed = true;
                                        processStack(node, startBlock, entries, visited, stack);
                                    }
                                }
                            } else {
                                unmarkedPhi = true;
                            }
                        }
                    }

                    /*
                     * the processing of one loop phi could have marked a previously checked loop
                     * phi, therefore this needs to be iterative.
                     */
                } while (unmarkedPhi && changed);
            }

            // Check for dead nodes.
            if (!immutableGraph && visited.getCounter() < graph.getNodeCount()) {
                for (Node n : graph.getNodes()) {
                    if (!visited.isMarked(n)) {
                        n.clearInputs();
                        n.markDeleted();
                    }
                }
            }

            for (Block b : cfg.reversePostOrder()) {
                FixedNode fixedNode = b.getEndNode();
                if (fixedNode instanceof ControlSplitNode) {
                    ControlSplitNode controlSplitNode = (ControlSplitNode) fixedNode;
                    MicroBlock endBlock = entries.get(fixedNode);
                    AbstractBeginNode primarySuccessor = controlSplitNode.getPrimarySuccessor();
                    if (primarySuccessor != null) {
                        endBlock.prependChildrenTo(entries.get(primarySuccessor));
                    } else {
                        assert endBlock.tail == null;
                    }
                }
            }

            // Create lists for each block
            for (Block b : cfg.reversePostOrder()) {
                // Count nodes in block
                int totalCount = 0;
                for (FixedNode current : b.getBeginNode().getBlockNodes()) {
                    MicroBlock microBlock = entries.get(current);
                    totalCount += microBlock.getNodeCount() + 1;
                }

                // Initialize with begin node, it is always the first node.
                ArrayList<Node> nodes = new ArrayList<>(totalCount);
                blockToNodes.put(b, nodes);

                for (FixedNode current : b.getBeginNode().getBlockNodes()) {
                    MicroBlock microBlock = entries.get(current);
                    nodeToBlock.set(current, b);
                    nodes.add(current);
                    NodeEntry next = microBlock.getFirstNode();
                    while (next != null) {
                        Node nextNode = next.getNode();
                        nodeToBlock.set(nextNode, b);
                        nodes.add(nextNode);
                        next = next.getNext();
                    }
                }
            }

            assert (!Assertions.detailedAssertionsEnabled(cfg.graph.getOptions())) || MemoryScheduleVerification.check(cfg.getStartBlock(), blockToNodes);
        }

        private static void processNodes(NodeBitMap visited, NodeMap<MicroBlock> entries, NodeStack stack, MicroBlock startBlock, Iterable<? extends Node> nodes) {
            for (Node node : nodes) {
                if (entries.get(node) == null) {
                    processStack(node, startBlock, entries, visited, stack);
                }
            }
        }

        private static void processStackPhi(NodeStack stack, PhiNode phiNode, NodeMap<MicroBlock> nodeToBlock, NodeBitMap visited) {
            stack.pop();
            if (visited.checkAndMarkInc(phiNode)) {
                MicroBlock mergeBlock = nodeToBlock.get(phiNode.merge());
                assert mergeBlock != null : phiNode;
                nodeToBlock.set(phiNode, mergeBlock);
                AbstractMergeNode merge = phiNode.merge();
                for (int i = 0; i < merge.forwardEndCount(); ++i) {
                    Node input = phiNode.valueAt(i);
                    if (input != null && nodeToBlock.get(input) == null) {
                        stack.push(input);
                    }
                }
            }
        }

        private static void processStackProxy(NodeStack stack, ProxyNode proxyNode, NodeMap<MicroBlock> nodeToBlock, NodeBitMap visited) {
            stack.pop();
            if (visited.checkAndMarkInc(proxyNode)) {
                nodeToBlock.set(proxyNode, nodeToBlock.get(proxyNode.proxyPoint()));
                Node input = proxyNode.value();
                if (input != null && nodeToBlock.get(input) == null) {
                    stack.push(input);
                }
            }
        }

        private static void processStack(Node first, MicroBlock startBlock, NodeMap<MicroBlock> nodeToMicroBlock, NodeBitMap visited, NodeStack stack) {
            assert stack.isEmpty();
            assert !visited.isMarked(first);
            stack.push(first);
            Node current = first;
            while (true) {
                if (current instanceof PhiNode) {
                    processStackPhi(stack, (PhiNode) current, nodeToMicroBlock, visited);
                } else if (current instanceof ProxyNode) {
                    processStackProxy(stack, (ProxyNode) current, nodeToMicroBlock, visited);
                } else {
                    MicroBlock currentBlock = nodeToMicroBlock.get(current);
                    if (currentBlock == null) {
                        MicroBlock earliestBlock = processInputs(nodeToMicroBlock, stack, startBlock, current);
                        if (earliestBlock == null) {
                            // We need to delay until inputs are processed.
                        } else {
                            // Can immediately process and pop.
                            stack.pop();
                            visited.checkAndMarkInc(current);
                            nodeToMicroBlock.set(current, earliestBlock);
                            earliestBlock.add(current);
                        }
                    } else {
                        stack.pop();
                    }
                }

                if (stack.isEmpty()) {
                    break;
                }
                current = stack.peek();
            }
        }

        private static class GuardOrder {
            /**
             * After an earliest schedule, this will re-sort guards to honor their
             * {@linkplain StaticDeoptimizingNode#computePriority() priority}.
             *
             * Note that this only changes the order of nodes within {@linkplain MicroBlock
             * micro-blocks}, nodes will not be moved from one micro-block to another.
             */
            private static void resortGuards(StructuredGraph graph, NodeMap<MicroBlock> entries, NodeStack stack) {
                assert stack.isEmpty();
                EconomicSet<MicroBlock> blocksWithGuards = EconomicSet.create(IDENTITY);
                for (GuardNode guard : graph.getNodes(GuardNode.TYPE)) {
                    MicroBlock block = entries.get(guard);
                    assert block != null : guard + "should already be scheduled to a micro-block";
                    blocksWithGuards.add(block);
                }
                assert !blocksWithGuards.isEmpty();
                NodeMap<GuardPriority> priorities = graph.createNodeMap();
                NodeBitMap blockNodes = graph.createNodeBitMap();
                for (MicroBlock block : blocksWithGuards) {
                    MicroBlock newBlock = resortGuards(block, stack, blockNodes, priorities);
                    assert stack.isEmpty();
                    assert blockNodes.isEmpty();
                    if (newBlock != null) {
                        assert block.getNodeCount() == newBlock.getNodeCount();
                        block.head = newBlock.head;
                        block.tail = newBlock.tail;
                    }
                }
            }

            /**
             * This resorts guards within one micro-block.
             *
             * {@code stack}, {@code blockNodes} and {@code priorities} are just temporary
             * data-structures which are allocated once by the callers of this method. They should
             * be in their "initial"/"empty" state when calling this method and when it returns.
             */
            private static MicroBlock resortGuards(MicroBlock block, NodeStack stack, NodeBitMap blockNodes, NodeMap<GuardPriority> priorities) {
                if (!propagatePriority(block, stack, priorities, blockNodes)) {
                    return null;
                }

                Function<GuardNode, GuardPriority> transitiveGuardPriorityGetter = priorities::get;
                Comparator<GuardNode> globalGuardPriorityComparator = Comparator.comparing(transitiveGuardPriorityGetter).thenComparing(GuardNode::computePriority).thenComparingInt(Node::hashCode);

                SortedSet<GuardNode> availableGuards = new TreeSet<>(globalGuardPriorityComparator);
                MicroBlock newBlock = new MicroBlock(block.getId());

                NodeBitMap sorted = blockNodes;
                sorted.invert();

                for (NodeEntry e = block.head; e != null; e = e.next) {
                    checkIfAvailable(e.node, stack, sorted, newBlock, availableGuards, false);
                }
                do {
                    while (!stack.isEmpty()) {
                        checkIfAvailable(stack.pop(), stack, sorted, newBlock, availableGuards, true);
                    }
                    Iterator<GuardNode> iterator = availableGuards.iterator();
                    if (iterator.hasNext()) {
                        addNodeToResort(iterator.next(), stack, sorted, newBlock, true);
                        iterator.remove();
                    }
                } while (!stack.isEmpty() || !availableGuards.isEmpty());

                blockNodes.clearAll();
                return newBlock;
            }

            /**
             * This checks if {@code n} can be scheduled, if it is the case, it schedules it now by
             * calling {@link #addNodeToResort(Node, NodeStack, NodeBitMap, MicroBlock, boolean)}.
             */
            private static void checkIfAvailable(Node n, NodeStack stack, NodeBitMap sorted, Instance.MicroBlock newBlock, SortedSet<GuardNode> availableGuardNodes, boolean pushUsages) {
                if (sorted.isMarked(n)) {
                    return;
                }
                for (Node in : n.inputs()) {
                    if (!sorted.isMarked(in)) {
                        return;
                    }
                }
                if (n instanceof GuardNode) {
                    availableGuardNodes.add((GuardNode) n);
                } else {
                    addNodeToResort(n, stack, sorted, newBlock, pushUsages);
                }
            }

            /**
             * Add a node to the re-sorted micro-block. This also pushes nodes that need to be
             * (re-)examined on the stack.
             */
            private static void addNodeToResort(Node n, NodeStack stack, NodeBitMap sorted, MicroBlock newBlock, boolean pushUsages) {
                sorted.mark(n);
                newBlock.add(n);
                if (pushUsages) {
                    for (Node u : n.usages()) {
                        if (!sorted.isMarked(u)) {
                            stack.push(u);
                        }
                    }
                }
            }

            /**
             * This fills in a map of transitive priorities ({@code priorities}). It also marks the
             * nodes from this micro-block in {@code blockNodes}.
             *
             * The transitive priority of a guard is the highest of its priority and the priority of
             * the guards that depend on it (transitively).
             *
             * This method returns {@code false} if no re-ordering is necessary in this micro-block.
             */
            private static boolean propagatePriority(MicroBlock block, NodeStack stack, NodeMap<GuardPriority> priorities, NodeBitMap blockNodes) {
                assert stack.isEmpty();
                assert blockNodes.isEmpty();
                GuardPriority lowestPriority = GuardPriority.highest();
                for (NodeEntry e = block.head; e != null; e = e.next) {
                    blockNodes.mark(e.node);
                    if (e.node instanceof GuardNode) {
                        GuardNode guard = (GuardNode) e.node;
                        GuardPriority priority = guard.computePriority();
                        if (lowestPriority != null) {
                            if (priority.isLowerPriorityThan(lowestPriority)) {
                                lowestPriority = priority;
                            } else if (priority.isHigherPriorityThan(lowestPriority)) {
                                lowestPriority = null;
                            }
                        }
                        stack.push(guard);
                        priorities.set(guard, priority);
                    }
                }
                if (lowestPriority != null) {
                    stack.clear();
                    blockNodes.clearAll();
                    return false;
                }

                do {
                    Node current = stack.pop();
                    assert blockNodes.isMarked(current);
                    GuardPriority priority = priorities.get(current);
                    for (Node input : current.inputs()) {
                        if (!blockNodes.isMarked(input)) {
                            continue;
                        }
                        GuardPriority inputPriority = priorities.get(input);
                        if (inputPriority == null || inputPriority.isLowerPriorityThan(priority)) {
                            priorities.set(input, priority);
                            stack.push(input);
                        }
                    }
                } while (!stack.isEmpty());
                return true;
            }
        }

        /**
         * Processes the inputs of given block. Pushes unprocessed inputs onto the stack. Returns
         * null if there were still unprocessed inputs, otherwise returns the earliest block given
         * node can be scheduled in.
         */
        private static MicroBlock processInputs(NodeMap<MicroBlock> nodeToBlock, NodeStack stack, MicroBlock startBlock, Node current) {
            if (current.getNodeClass().isLeafNode()) {
                return startBlock;
            }

            MicroBlock earliestBlock = startBlock;
            for (Node input : current.inputs()) {
                MicroBlock inputBlock = nodeToBlock.get(input);
                if (inputBlock == null) {
                    earliestBlock = null;
                    stack.push(input);
                } else if (earliestBlock != null && inputBlock.getId() > earliestBlock.getId()) {
                    earliestBlock = inputBlock;
                }
            }
            return earliestBlock;
        }

        private static boolean isFixedEnd(FixedNode endNode) {
            return endNode instanceof ControlSplitNode || endNode instanceof ControlSinkNode || endNode instanceof AbstractEndNode;
        }

        public String printScheduleHelper(String desc) {
            Formatter buf = new Formatter();
            buf.format("=== %s / %s ===%n", getCFG().getStartBlock().getBeginNode().graph(), desc);
            for (Block b : getCFG().getBlocks()) {
                buf.format("==== b: %s (loopDepth: %s). ", b, b.getLoopDepth());
                buf.format("dom: %s. ", b.getDominator());
                buf.format("preds: %s. ", Arrays.toString(b.getPredecessors()));
                buf.format("succs: %s ====%n", Arrays.toString(b.getSuccessors()));

                if (blockToNodesMap.get(b) != null) {
                    for (Node n : nodesFor(b)) {
                        printNode(n);
                    }
                } else {
                    for (Node n : b.getNodes()) {
                        printNode(n);
                    }
                }
            }
            buf.format("%n");
            return buf.toString();
        }

        private static void printNode(Node n) {
            Formatter buf = new Formatter();
            buf.format("%s", n);
            if (n instanceof MemoryCheckpoint.Single) {
                buf.format(" // kills %s", ((MemoryCheckpoint.Single) n).getLocationIdentity());
            } else if (n instanceof MemoryCheckpoint.Multi) {
                buf.format(" // kills ");
                for (LocationIdentity locid : ((MemoryCheckpoint.Multi) n).getLocationIdentities()) {
                    buf.format("%s, ", locid);
                }
            } else if (n instanceof FloatingReadNode) {
                FloatingReadNode frn = (FloatingReadNode) n;
                buf.format(" // from %s", frn.getLocationIdentity());
                buf.format(", lastAccess: %s", frn.getLastLocationAccess());
                buf.format(", address: %s", frn.getAddress());
            } else if (n instanceof GuardNode) {
                buf.format(", anchor: %s", ((GuardNode) n).getAnchor());
            }
            n.getDebug().log("%s", buf);
        }

        public ControlFlowGraph getCFG() {
            return cfg;
        }

        /**
         * Gets the nodes in a given block.
         */
        public List<Node> nodesFor(Block block) {
            return blockToNodesMap.get(block);
        }
    }

}
