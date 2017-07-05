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
package org.graalvm.compiler.nodes.cfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.core.common.cfg.CFGVerifier;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;

public final class ControlFlowGraph implements AbstractControlFlowGraph<Block> {
    /**
     * Don't allow probability values to be become too small or too high as this makes frequency
     * calculations over- or underflow the range of a double. This commonly happens with infinite
     * loops within infinite loops. The value is chosen a bit lower than half the maximum exponent
     * supported by double. That way we can never overflow to infinity when multiplying two
     * probability values.
     */
    public static final double MIN_PROBABILITY = 0x1.0p-500;
    public static final double MAX_PROBABILITY = 1 / MIN_PROBABILITY;

    public final StructuredGraph graph;

    private NodeMap<Block> nodeToBlock;
    private Block[] reversePostOrder;
    private List<Loop<Block>> loops;

    public static ControlFlowGraph compute(StructuredGraph graph, boolean connectBlocks, boolean computeLoops, boolean computeDominators, boolean computePostdominators) {
        ControlFlowGraph cfg = new ControlFlowGraph(graph);
        cfg.identifyBlocks();
        cfg.computeProbabilities();

        if (computeLoops) {
            cfg.computeLoopInformation();
        }
        if (computeDominators) {
            cfg.computeDominators();
        }
        if (computePostdominators) {
            cfg.computePostdominators();
        }
        // there's not much to verify when connectBlocks == false
        assert !(connectBlocks || computeLoops || computeDominators || computePostdominators) || CFGVerifier.verify(cfg);
        return cfg;
    }

    private ControlFlowGraph(StructuredGraph graph) {
        this.graph = graph;
        this.nodeToBlock = graph.createNodeMap();
    }

    private void computeDominators() {
        assert reversePostOrder[0].getPredecessorCount() == 0 : "start block has no predecessor and therefore no dominator";
        Block[] blocks = reversePostOrder;
        for (int i = 1; i < blocks.length; i++) {
            Block block = blocks[i];
            assert block.getPredecessorCount() > 0;
            Block dominator = null;
            for (Block pred : block.getPredecessors()) {
                if (!pred.isLoopEnd()) {
                    dominator = ((dominator == null) ? pred : commonDominatorRaw(dominator, pred));
                }
            }
            // set dominator
            block.setDominator(dominator);
            if (dominator.getDominated().equals(Collections.emptyList())) {
                dominator.setDominated(new ArrayList<>());
            }
            dominator.getDominated().add(block);
        }
        calcDominatorRanges(getStartBlock(), reversePostOrder.length);
    }

    private static void calcDominatorRanges(Block block, int size) {
        Block[] stack = new Block[size];
        stack[0] = block;
        int tos = 0;
        int myNumber = 0;

        do {
            Block cur = stack[tos];
            List<Block> dominated = cur.getDominated();

            if (cur.getDominatorNumber() == -1) {
                cur.setDominatorNumber(myNumber);
                if (dominated.size() > 0) {
                    // Push children onto stack.
                    for (Block b : dominated) {
                        stack[++tos] = b;
                    }
                } else {
                    cur.setMaxChildDomNumber(myNumber);
                    --tos;
                }
                ++myNumber;
            } else {
                cur.setMaxChildDomNumber(dominated.get(0).getMaxChildDominatorNumber());
                --tos;
            }
        } while (tos >= 0);
    }

    private static Block commonDominatorRaw(Block a, Block b) {
        int aDomDepth = a.getDominatorDepth();
        int bDomDepth = b.getDominatorDepth();
        if (aDomDepth > bDomDepth) {
            return commonDominatorRawSameDepth(a.getDominator(aDomDepth - bDomDepth), b);
        } else {
            return commonDominatorRawSameDepth(a, b.getDominator(bDomDepth - aDomDepth));
        }
    }

    private static Block commonDominatorRawSameDepth(Block a, Block b) {
        Block iterA = a;
        Block iterB = b;
        while (iterA != iterB) {
            iterA = iterA.getDominator();
            iterB = iterB.getDominator();
        }
        return iterA;
    }

    @Override
    public Block[] getBlocks() {
        return reversePostOrder;
    }

    @Override
    public Block getStartBlock() {
        return reversePostOrder[0];
    }

    public Block[] reversePostOrder() {
        return reversePostOrder;
    }

    public NodeMap<Block> getNodeToBlock() {
        return nodeToBlock;
    }

    public Block blockFor(Node node) {
        return nodeToBlock.get(node);
    }

    @Override
    public List<Loop<Block>> getLoops() {
        return loops;
    }

    private void identifyBlock(Block block) {
        FixedWithNextNode cur = block.getBeginNode();
        while (true) {
            assert !cur.isDeleted();
            assert nodeToBlock.get(cur) == null;
            nodeToBlock.set(cur, block);
            FixedNode next = cur.next();
            if (next instanceof AbstractBeginNode) {
                block.endNode = cur;
                return;
            } else if (next instanceof FixedWithNextNode) {
                cur = (FixedWithNextNode) next;
            } else {
                nodeToBlock.set(next, block);
                block.endNode = next;
                return;
            }
        }
    }

    /**
     * Identify and connect blocks (including loop backward edges). Predecessors need to be in the
     * order expected when iterating phi inputs.
     */
    private void identifyBlocks() {
        // Find all block headers.
        int numBlocks = 0;
        for (AbstractBeginNode begin : graph.getNodes(AbstractBeginNode.TYPE)) {
            Block block = new Block(begin);
            identifyBlock(block);
            numBlocks++;
        }

        // Compute reverse post order.
        int count = 0;
        NodeMap<Block> nodeMap = this.nodeToBlock;
        Block[] stack = new Block[numBlocks];
        int tos = 0;
        Block startBlock = blockFor(graph.start());
        stack[0] = startBlock;
        startBlock.setPredecessors(Block.EMPTY_ARRAY);
        do {
            Block block = stack[tos];
            int id = block.getId();
            if (id == BLOCK_ID_INITIAL) {
                // First time we see this block: push all successors.
                FixedNode last = block.getEndNode();
                if (last instanceof EndNode) {
                    EndNode endNode = (EndNode) last;
                    Block suxBlock = nodeMap.get(endNode.merge());
                    if (suxBlock.getId() == BLOCK_ID_INITIAL) {
                        stack[++tos] = suxBlock;
                    }
                    block.setSuccessors(new Block[]{suxBlock});
                } else if (last instanceof IfNode) {
                    IfNode ifNode = (IfNode) last;
                    Block trueSucc = nodeMap.get(ifNode.trueSuccessor());
                    stack[++tos] = trueSucc;
                    Block falseSucc = nodeMap.get(ifNode.falseSuccessor());
                    stack[++tos] = falseSucc;
                    block.setSuccessors(new Block[]{trueSucc, falseSucc});
                    Block[] ifPred = new Block[]{block};
                    trueSucc.setPredecessors(ifPred);
                    falseSucc.setPredecessors(ifPred);
                } else if (last instanceof LoopEndNode) {
                    LoopEndNode loopEndNode = (LoopEndNode) last;
                    block.setSuccessors(new Block[]{nodeMap.get(loopEndNode.loopBegin())});
                    // Nothing to do push onto the stack.
                } else {
                    assert !(last instanceof AbstractEndNode) : "Algorithm only supports EndNode and LoopEndNode.";
                    int startTos = tos;
                    Block[] ifPred = new Block[]{block};
                    for (Node suxNode : last.successors()) {
                        Block sux = nodeMap.get(suxNode);
                        stack[++tos] = sux;
                        sux.setPredecessors(ifPred);
                    }
                    int suxCount = tos - startTos;
                    Block[] successors = new Block[suxCount];
                    System.arraycopy(stack, startTos + 1, successors, 0, suxCount);
                    block.setSuccessors(successors);
                }
                block.setId(BLOCK_ID_VISITED);
                AbstractBeginNode beginNode = block.getBeginNode();
                if (beginNode instanceof LoopBeginNode) {
                    computeLoopPredecessors(nodeMap, block, (LoopBeginNode) beginNode);
                } else if (beginNode instanceof MergeNode) {
                    MergeNode mergeNode = (MergeNode) beginNode;
                    int forwardEndCount = mergeNode.forwardEndCount();
                    Block[] predecessors = new Block[forwardEndCount];
                    for (int i = 0; i < forwardEndCount; ++i) {
                        predecessors[i] = nodeMap.get(mergeNode.forwardEndAt(i));
                    }
                    block.setPredecessors(predecessors);
                }

            } else if (id == BLOCK_ID_VISITED) {
                // Second time we see this block: All successors have been processed, so add block
                // to result list. Can safely reuse the stack for this.
                --tos;
                count++;
                int index = numBlocks - count;
                stack[index] = block;
                block.setId(index);
            } else {
                throw GraalError.shouldNotReachHere();
            }
        } while (tos >= 0);

        // Compute reverse postorder and number blocks.
        assert count == numBlocks : "all blocks must be reachable";
        this.reversePostOrder = stack;
    }

    private static void computeLoopPredecessors(NodeMap<Block> nodeMap, Block block, LoopBeginNode loopBeginNode) {
        int forwardEndCount = loopBeginNode.forwardEndCount();
        LoopEndNode[] loopEnds = loopBeginNode.orderedLoopEnds();
        Block[] predecessors = new Block[forwardEndCount + loopEnds.length];
        for (int i = 0; i < forwardEndCount; ++i) {
            predecessors[i] = nodeMap.get(loopBeginNode.forwardEndAt(i));
        }
        for (int i = 0; i < loopEnds.length; ++i) {
            predecessors[i + forwardEndCount] = nodeMap.get(loopEnds[i]);
        }
        block.setPredecessors(predecessors);
    }

    private void computeProbabilities() {

        for (Block block : reversePostOrder) {
            Block[] predecessors = block.getPredecessors();

            double probability;
            if (predecessors.length == 0) {
                probability = 1D;
            } else if (predecessors.length == 1) {
                Block pred = predecessors[0];
                probability = pred.probability;
                if (pred.getSuccessorCount() > 1) {
                    assert pred.getEndNode() instanceof ControlSplitNode;
                    ControlSplitNode controlSplit = (ControlSplitNode) pred.getEndNode();
                    probability *= controlSplit.probability(block.getBeginNode());
                }
            } else {
                probability = predecessors[0].probability;
                for (int i = 1; i < predecessors.length; ++i) {
                    probability += predecessors[i].probability;
                }

                if (block.getBeginNode() instanceof LoopBeginNode) {
                    LoopBeginNode loopBegin = (LoopBeginNode) block.getBeginNode();
                    probability *= loopBegin.loopFrequency();
                }
            }
            if (probability < MIN_PROBABILITY) {
                block.setProbability(MIN_PROBABILITY);
            } else if (probability > MAX_PROBABILITY) {
                block.setProbability(MAX_PROBABILITY);
            } else {
                block.setProbability(probability);
            }
        }

    }

    private void computeLoopInformation() {
        loops = new ArrayList<>();
        if (graph.hasLoops()) {
            Block[] stack = new Block[this.reversePostOrder.length];
            for (Block block : reversePostOrder) {
                AbstractBeginNode beginNode = block.getBeginNode();
                if (beginNode instanceof LoopBeginNode) {
                    Loop<Block> loop = new HIRLoop(block.getLoop(), loops.size(), block);
                    loops.add(loop);
                    block.loop = loop;
                    loop.getBlocks().add(block);

                    LoopBeginNode loopBegin = (LoopBeginNode) beginNode;
                    for (LoopEndNode end : loopBegin.loopEnds()) {
                        Block endBlock = nodeToBlock.get(end);
                        computeLoopBlocks(endBlock, loop, stack, true);
                    }

                    if (graph.getGuardsStage() != GuardsStage.AFTER_FSA) {
                        for (LoopExitNode exit : loopBegin.loopExits()) {
                            Block exitBlock = nodeToBlock.get(exit);
                            assert exitBlock.getPredecessorCount() == 1;
                            computeLoopBlocks(exitBlock.getFirstPredecessor(), loop, stack, true);
                            loop.getExits().add(exitBlock);
                        }

                        // The following loop can add new blocks to the end of the loop's block
                        // list.
                        int size = loop.getBlocks().size();
                        for (int i = 0; i < size; ++i) {
                            Block b = loop.getBlocks().get(i);
                            for (Block sux : b.getSuccessors()) {
                                if (sux.loop != loop) {
                                    AbstractBeginNode begin = sux.getBeginNode();
                                    if (!(begin instanceof LoopExitNode && ((LoopExitNode) begin).loopBegin() == loopBegin)) {
                                        Debug.log(Debug.VERBOSE_LOG_LEVEL, "Unexpected loop exit with %s, including whole branch in the loop", sux);
                                        computeLoopBlocks(sux, loop, stack, false);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /*
         * Compute the loop exit blocks after FSA.
         */
        if (graph.getGuardsStage() == GuardsStage.AFTER_FSA) {
            for (Block b : reversePostOrder) {
                if (b.getLoop() != null) {
                    for (Block succ : b.getSuccessors()) {
                        // if the loop of the succ is a different one (or none)
                        if (b.getLoop() != succ.getLoop()) {
                            // and the succ loop is not a child loop of the curr one
                            if (succ.getLoop() == null) {
                                // we might exit multiple loops if b.loops is not a loop at depth 0
                                Loop<Block> curr = b.getLoop();
                                while (curr != null) {
                                    curr.getExits().add(succ);
                                    curr = curr.getParent();
                                }
                            } else {
                                /*
                                 * succ also has a loop, might be a child loop
                                 *
                                 * if it is a child loop we do not exit a loop. if it is a loop
                                 * different than b.loop and not a child loop it must be a parent
                                 * loop, thus we exit all loops between b.loop and succ.loop
                                 *
                                 * if we exit multiple loops immediately after each other the
                                 * bytecode parser might generate loop exit nodes after another and
                                 * the CFG will identify them as separate blocks, we just take the
                                 * first one and exit all loops at this one
                                 */
                                if (succ.getLoop().getParent() != b.getLoop()) {
                                    assert succ.getLoop().getDepth() < b.getLoop().getDepth();
                                    // b.loop must not be a transitive parent of succ.loop
                                    assert !Loop.transitiveParentLoop(succ.getLoop(), b.getLoop());
                                    Loop<Block> curr = b.getLoop();
                                    while (curr != null && curr != succ.getLoop()) {
                                        curr.getExits().add(succ);
                                        curr = curr.getParent();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    private static void computeLoopBlocks(Block start, Loop<Block> loop, Block[] stack, boolean usePred) {
        if (start.loop != loop) {
            start.loop = loop;
            stack[0] = start;
            loop.getBlocks().add(start);
            int tos = 0;
            do {
                Block block = stack[tos--];

                // Add predecessors or successors to the loop.
                for (Block b : (usePred ? block.getPredecessors() : block.getSuccessors())) {
                    if (b.loop != loop) {
                        stack[++tos] = b;
                        b.loop = loop;
                        loop.getBlocks().add(b);
                    }
                }
            } while (tos >= 0);
        }
    }

    public void computePostdominators() {

        Block[] reversePostOrderTmp = this.reversePostOrder;

        outer: for (int j = reversePostOrderTmp.length - 1; j >= 0; --j) {
            Block block = reversePostOrderTmp[j];
            if (block.isLoopEnd()) {
                // We do not want the loop header registered as the postdominator of the loop end.
                continue;
            }
            if (block.getSuccessorCount() == 0) {
                // No successors => no postdominator.
                continue;
            }
            Block firstSucc = block.getSuccessors()[0];
            if (block.getSuccessorCount() == 1) {
                block.postdominator = firstSucc;
                continue;
            }
            Block postdominator = firstSucc;
            for (Block sux : block.getSuccessors()) {
                postdominator = commonPostdominator(postdominator, sux);
                if (postdominator == null) {
                    // There is a dead end => no postdominator available.
                    continue outer;
                }
            }
            assert !Arrays.asList(block.getSuccessors()).contains(postdominator) : "Block " + block + " has a wrong post dominator: " + postdominator;
            block.postdominator = postdominator;
        }
    }

    private static Block commonPostdominator(Block a, Block b) {
        Block iterA = a;
        Block iterB = b;
        while (iterA != iterB) {
            if (iterA.getId() < iterB.getId()) {
                iterA = iterA.getPostdominator();
                if (iterA == null) {
                    return null;
                }
            } else {
                assert iterB.getId() < iterA.getId();
                iterB = iterB.getPostdominator();
                if (iterB == null) {
                    return null;
                }
            }
        }
        return iterA;
    }

    public void setNodeToBlock(NodeMap<Block> nodeMap) {
        this.nodeToBlock = nodeMap;
    }
}
