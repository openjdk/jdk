/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.match;

import static org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph.dominates;
import static org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph.strictlyDominates;
import static org.graalvm.compiler.debug.DebugOptions.LogVerbose;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.core.match.MatchPattern.Result;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

/**
 * Container for state captured during a match.
 */
public class MatchContext {
    private static final CounterKey MatchContextSuccessDifferentBlocks = DebugContext.counter("MatchContextSuccessDifferentBlocks");

    private final Node root;

    private final MatchStatement rule;
    private final StructuredGraph.ScheduleResult schedule;

    private EconomicMap<String, NamedNode> namedNodes;

    /**
     * A node consumed by a match. Keeps track of whether side effects can be ignored.
     */
    static final class ConsumedNode {
        final Node node;
        final boolean ignoresSideEffects;

        ConsumedNode(Node node, boolean ignoresSideEffects) {
            this.node = node;
            this.ignoresSideEffects = ignoresSideEffects;
        }
    }

    /**
     * The collection of nodes consumed by this match.
     */
    static final class ConsumedNodes implements Iterable<ConsumedNode> {
        private ArrayList<ConsumedNode> nodes;

        ConsumedNodes() {
            this.nodes = null;
        }

        public void add(Node node, boolean ignoresSideEffects) {
            if (nodes == null) {
                nodes = new ArrayList<>(2);
            }
            nodes.add(new ConsumedNode(node, ignoresSideEffects));
        }

        public boolean contains(Node node) {
            for (ConsumedNode c : nodes) {
                if (c.node == node) {
                    return true;
                }
            }
            return false;
        }

        public ConsumedNode find(Node node) {
            for (ConsumedNode c : nodes) {
                if (c.node == node) {
                    return c;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            Node[] arr = new Node[nodes.size()];
            int i = 0;
            for (ConsumedNode c : nodes) {
                arr[i++] = c.node;
            }
            return Arrays.toString(arr);
        }

        @Override
        public Iterator<ConsumedNode> iterator() {
            return nodes.iterator();
        }
    }

    private ConsumedNodes consumed = new ConsumedNodes();

    private Block rootBlock;
    private int rootIndex;

    /**
     * Block and index in the block at which the match should be emitted. Differs from
     * rootBlock/rootIndex for (ZeroExtend Read=access) for instance: match should be emitted where
     * the Read is.
     */
    private int emitIndex;
    private Block emitBlock;

    private final NodeLIRBuilder builder;

    private static class NamedNode {
        final Class<? extends Node> type;
        final Node value;

        NamedNode(Class<? extends Node> type, Node value) {
            this.type = type;
            this.value = value;
        }
    }

    public MatchContext(NodeLIRBuilder builder, MatchStatement rule, int index, Node node, Block rootBlock, StructuredGraph.ScheduleResult schedule) {
        this.builder = builder;
        this.rule = rule;
        this.root = node;
        assert index == schedule.getBlockToNodesMap().get(rootBlock).indexOf(node);
        this.schedule = schedule;
        // The root should be the last index since all the inputs must be scheduled before it.
        this.rootBlock = rootBlock;
        rootIndex = index;
    }

    public Node getRoot() {
        return root;
    }

    public Result captureNamedValue(String name, Class<? extends Node> type, Node value) {
        if (namedNodes == null) {
            namedNodes = EconomicMap.create(Equivalence.DEFAULT);
        }
        NamedNode current = namedNodes.get(name);
        if (current == null) {
            current = new NamedNode(type, value);
            namedNodes.put(name, current);
            return Result.OK;
        } else {
            if (current.value != value || current.type != type) {
                return Result.namedValueMismatch(value, rule.getPattern());
            }
            return Result.OK;
        }
    }

    public Result validate() {
        Result result = findEarlyPosition();
        if (result != Result.OK) {
            return result;
        }
        findLatePosition();
        assert emitIndex == rootIndex || consumed.find(root).ignoresSideEffects;
        return verifyInputs();
    }

    private Result findEarlyPosition() {
        int startIndexSideEffect = -1;
        int endIndexSideEffect = -1;
        final NodeMap<Block> nodeToBlockMap = schedule.getNodeToBlockMap();
        final BlockMap<List<Node>> blockToNodesMap = schedule.getBlockToNodesMap();

        // Nodes affected by side effects must be in the same block
        for (ConsumedNode cn : consumed) {
            if (!cn.ignoresSideEffects) {
                Block b = nodeToBlockMap.get(cn.node);
                if (emitBlock == null) {
                    emitBlock = b;
                    startIndexSideEffect = endIndexSideEffect = blockToNodesMap.get(b).indexOf(cn.node);
                } else if (emitBlock == b) {
                    int index = blockToNodesMap.get(b).indexOf(cn.node);
                    startIndexSideEffect = Math.min(startIndexSideEffect, index);
                    endIndexSideEffect = Math.max(endIndexSideEffect, index);
                } else {
                    logFailedMatch("nodes affected by side effects in different blocks %s", cn.node);
                    return Result.notInBlock(cn.node, rule.getPattern());
                }
            }
        }
        if (emitBlock != null) {
            // There must be no side effects between nodes that are affected by side effects
            assert startIndexSideEffect != -1 && endIndexSideEffect != -1;
            final List<Node> nodes = blockToNodesMap.get(emitBlock);
            for (int i = startIndexSideEffect; i <= endIndexSideEffect; i++) {
                Node node = nodes.get(i);
                if (!sideEffectFree(node) && !consumed.contains(node)) {
                    logFailedMatch("unexpected side effect %s", node);
                    return Result.notSafe(node, rule.getPattern());
                }
            }
            // early position is at the node affected by side effects the closest to the root
            emitIndex = endIndexSideEffect;
        } else {
            // Nodes not affected by side effect: early position is at the root
            emitBlock = nodeToBlockMap.get(root);
            emitIndex = rootIndex;
        }
        return Result.OK;
    }

    private static boolean sideEffectFree(Node node) {
        // The order of evaluation of these nodes controlled by data dependence so they
        // don't interfere with this match.
        return node instanceof VirtualObjectNode || node instanceof FloatingNode;
    }

    private void findLatePosition() {
        // If emit position is at a node affected by side effects that's followed by side effect
        // free nodes, the node can be emitted later. This helps when the match has inputs that are
        // late in the block.
        int index = rootIndex;
        if (emitBlock != rootBlock) {
            index = schedule.getBlockToNodesMap().get(emitBlock).size() - 1;
        }
        final List<Node> emitBlockNodes = schedule.getBlockToNodesMap().get(emitBlock);
        for (int i = emitIndex + 1; i <= index; i++) {
            Node node = emitBlockNodes.get(i);
            ConsumedNode cn = consumed.find(node);
            if (cn == null) {
                if (!sideEffectFree(node)) {
                    return;
                }
            } else {
                assert cn.ignoresSideEffects;
                emitIndex = i;
            }
        }
    }

    private Result verifyInputs() {
        DebugContext debug = root.getDebug();
        if (emitBlock != rootBlock) {
            assert consumed.find(root).ignoresSideEffects;
            Result result = verifyInputsDifferentBlock(root);
            if (result == Result.OK) {
                MatchContextSuccessDifferentBlocks.increment(debug);
            }
            return result;
        }
        // We are going to emit the match at emitIndex. We need to make sure nodes of the match
        // between emitIndex and rootIndex don't have inputs after position emitIndex that would
        // make emitIndex an illegal position.
        final List<Node> nodes = schedule.getBlockToNodesMap().get(rootBlock);
        for (int i = emitIndex + 1; i <= rootIndex; i++) {
            Node node = nodes.get(i);
            ConsumedNode cn = consumed.find(node);
            if (cn != null) {
                assert cn.ignoresSideEffects;
                for (Node in : node.inputs()) {
                    if (!consumed.contains(in)) {
                        for (int j = emitIndex + 1; j < i; j++) {
                            if (nodes.get(j) == in) {
                                logFailedMatch("Earliest position in block is too late %s", in);
                                assert consumed.find(root).ignoresSideEffects;
                                assert verifyInputsDifferentBlock(root) != Result.OK;
                                return Result.tooLate(node, rule.getPattern());
                            }
                        }
                    }
                }
            }
        }
        assert verifyInputsDifferentBlock(root) == Result.OK;
        return Result.OK;
    }

    private Result verifyInputsDifferentBlock(Node node) {
        // Is there an input that's not part of the match that's after the emit position?
        for (Node in : node.inputs()) {
            if (in instanceof PhiNode) {
                Block b = schedule.getNodeToBlockMap().get(((PhiNode) in).merge());
                if (dominates(b, emitBlock)) {
                    continue;
                }
            } else {
                Block b = schedule.getNodeToBlockMap().get(in);
                if (strictlyDominates(b, emitBlock) || (b == emitBlock && schedule.getBlockToNodesMap().get(emitBlock).indexOf(in) <= emitIndex)) {
                    continue;
                }
            }
            ConsumedNode cn = consumed.find(in);
            if (cn == null) {
                logFailedMatch("Earliest position in block is too late %s", in);
                return Result.tooLate(node, rule.getPattern());
            }
            assert cn.ignoresSideEffects;
            Result res = verifyInputsDifferentBlock(in);
            if (res != Result.OK) {
                return res;
            }
        }
        return Result.OK;
    }

    private void logFailedMatch(String s, Node node) {
        if (LogVerbose.getValue(root.getOptions())) {
            DebugContext debug = root.getDebug();
            debug.log(s, node);
            int startIndex = emitIndex;
            if (emitBlock != rootBlock) {
                int endIndex = schedule.getBlockToNodesMap().get(emitBlock).size() - 1;
                final List<Node> emitBlockNodes = schedule.getBlockToNodesMap().get(emitBlock);
                debug.log("%s:", emitBlock);
                for (int j = startIndex; j <= endIndex; j++) {
                    Node theNode = emitBlockNodes.get(j);
                    debug.log("%s(%s) %1s", consumed.contains(theNode) ? "*" : " ", theNode.getUsageCount(), theNode);
                }
                startIndex = 0;
            }
            debug.log("%s:", rootBlock);
            final List<Node> nodes = schedule.getBlockToNodesMap().get(rootBlock);
            for (int j = startIndex; j <= rootIndex; j++) {
                Node theNode = nodes.get(j);
                debug.log("%s(%s) %1s", consumed.contains(theNode) ? "*" : " ", theNode.getUsageCount(), theNode);
            }
        }
    }

    /**
     * Mark the interior nodes with INTERIOR_MATCH and set the Value of the root to be the result.
     * During final LIR generation it will be evaluated to produce the actual LIR value.
     *
     * @param result
     */
    public void setResult(ComplexMatchResult result) {
        ComplexMatchValue value = new ComplexMatchValue(result);
        Node emitNode = schedule.getBlockToNodesMap().get(emitBlock).get(emitIndex);
        DebugContext debug = root.getDebug();
        if (debug.isLogEnabled()) {
            debug.log("matched %s %s%s", rule.getName(), rule.getPattern(), emitIndex != rootIndex ? " skipping side effects" : "");
            debug.log("with nodes %s", rule.formatMatch(root));
        }
        for (ConsumedNode cn : consumed) {
            if (cn.node == root || cn.node == emitNode) {
                continue;
            }
            // All the interior nodes should be skipped during the normal doRoot calls in
            // NodeLIRBuilder so mark them as interior matches. The root of the match will get a
            // closure which will be evaluated to produce the final LIR.
            builder.setMatchResult(cn.node, ComplexMatchValue.INTERIOR_MATCH);
        }
        builder.setMatchResult(emitNode, value);
        if (root != emitNode) {
            // Match is not emitted at the position of root in the block but the uses of root needs
            // the result of the match so add a ComplexMatchValue that will simply return the result
            // of the actual match above.
            builder.setMatchResult(root, new ComplexMatchValue(gen -> gen.operand(emitNode)));
        }
    }

    /**
     * Mark a node as consumed by the match. Consumed nodes will never be evaluated.
     *
     * @return Result.OK if the node can be safely consumed.
     */
    public Result consume(Node node, boolean ignoresSideEffects, boolean atRoot) {
        if (atRoot) {
            consumed.add(node, ignoresSideEffects);
            return Result.OK;
        }
        assert MatchPattern.isSingleValueUser(node) : "should have already been checked";

        if (builder.hasOperand(node)) {
            return Result.alreadyUsed(node, rule.getPattern());
        }

        consumed.add(node, ignoresSideEffects);
        return Result.OK;
    }

    /**
     * Return the named node. It's an error if the
     *
     * @param name the name of a node in the match rule
     * @return the matched node
     * @throws GraalError is the named node doesn't exist.
     */
    public Node namedNode(String name) {
        if (namedNodes != null) {
            NamedNode value = namedNodes.get(name);
            if (value != null) {
                return value.value;
            }
        }
        throw new GraalError("missing node %s", name);
    }

    @Override
    public String toString() {
        return String.format("%s %s (%s/%d, %s/%d) consumed %s", rule, root, rootBlock, rootIndex, emitBlock, emitIndex, consumed);
    }
}
