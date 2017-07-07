/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.constopt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.constopt.ConstantTree.Flags;
import org.graalvm.compiler.lir.constopt.ConstantTree.NodeCost;

/**
 * Analyzes a {@link ConstantTree} and marks potential materialization positions.
 */
public final class ConstantTreeAnalyzer {
    private final ConstantTree tree;
    private final BitSet visited;

    @SuppressWarnings("try")
    public static NodeCost analyze(DebugContext debug, ConstantTree tree, AbstractBlockBase<?> startBlock) {
        try (DebugContext.Scope s = debug.scope("ConstantTreeAnalyzer")) {
            ConstantTreeAnalyzer analyzer = new ConstantTreeAnalyzer(tree);
            analyzer.analyzeBlocks(debug, startBlock);
            return tree.getCost(startBlock);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private ConstantTreeAnalyzer(ConstantTree tree) {
        this.tree = tree;
        this.visited = new BitSet(tree.size());
    }

    /**
     * Queues all relevant blocks for {@linkplain #process processing}.
     *
     * This is a worklist-style algorithm because a (more elegant) recursive implementation may
     * cause {@linkplain StackOverflowError stack overflows} on larger graphs.
     *
     * @param startBlock The start block of the dominator subtree.
     */
    @SuppressWarnings("try")
    private void analyzeBlocks(DebugContext debug, AbstractBlockBase<?> startBlock) {
        Deque<AbstractBlockBase<?>> worklist = new ArrayDeque<>();
        worklist.offerLast(startBlock);
        while (!worklist.isEmpty()) {
            AbstractBlockBase<?> block = worklist.pollLast();
            try (Indent i = debug.logAndIndent(DebugContext.VERBOSE_LEVEL, "analyze: %s", block)) {
                assert block != null : "worklist is empty!";
                assert isMarked(block) : "Block not part of the dominator tree: " + block;

                if (isLeafBlock(block)) {
                    debug.log(DebugContext.VERBOSE_LEVEL, "leaf block");
                    leafCost(block);
                    continue;
                }

                if (!visited.get(block.getId())) {
                    // if not yet visited (and not a leaf block) process all children first!
                    debug.log(DebugContext.VERBOSE_LEVEL, "not marked");
                    worklist.offerLast(block);
                    AbstractBlockBase<?> dominated = block.getFirstDominated();
                    while (dominated != null) {
                        filteredPush(debug, worklist, dominated);
                        dominated = dominated.getDominatedSibling();
                    }
                    visited.set(block.getId());
                } else {
                    debug.log(DebugContext.VERBOSE_LEVEL, "marked");
                    // otherwise, process block
                    process(block);
                }
            }
        }
    }

    /**
     * Calculates the cost of a {@code block}. It is assumed that all {@code children} have already
     * been {@linkplain #process processed}
     *
     * @param block The block to be processed.
     */
    private void process(AbstractBlockBase<?> block) {
        List<UseEntry> usages = new ArrayList<>();
        double bestCost = 0;
        int numMat = 0;

        // collect children costs
        AbstractBlockBase<?> child = block.getFirstDominated();
        while (child != null) {
            if (isMarked(child)) {
                NodeCost childCost = tree.getCost(child);
                assert childCost != null : "Child with null cost? block: " + child;
                usages.addAll(childCost.getUsages());
                numMat += childCost.getNumMaterializations();
                bestCost += childCost.getBestCost();
            }
            child = child.getDominatedSibling();
        }
        assert numMat > 0 : "No materialization? " + numMat;

        // choose block
        List<UseEntry> usagesBlock = tree.getUsages(block);
        double probabilityBlock = block.probability();

        if (!usagesBlock.isEmpty() || shouldMaterializerInCurrentBlock(probabilityBlock, bestCost, numMat)) {
            // mark current block as potential materialization position
            usages.addAll(usagesBlock);
            bestCost = probabilityBlock;
            numMat = 1;
            tree.set(Flags.CANDIDATE, block);
        } else {
            // stick with the current solution
        }

        NodeCost nodeCost = new NodeCost(bestCost, usages, numMat);
        tree.setCost(block, nodeCost);
    }

    /**
     * This is the cost function that decides whether a materialization should be inserted in the
     * current block.
     * <p>
     * Note that this function does not take into account if a materialization is required despite
     * the probabilities (e.g. there are usages in the current block).
     *
     * @param probabilityBlock Probability of the current block.
     * @param probabilityChildren Accumulated probability of the children.
     * @param numMat Number of materializations along the subtrees. We use {@code numMat - 1} to
     *            insert materializations as late as possible if the probabilities are the same.
     */
    private static boolean shouldMaterializerInCurrentBlock(double probabilityBlock, double probabilityChildren, int numMat) {
        return probabilityBlock * Math.pow(0.9, numMat - 1) < probabilityChildren;
    }

    private void filteredPush(DebugContext debug, Deque<AbstractBlockBase<?>> worklist, AbstractBlockBase<?> block) {
        if (isMarked(block)) {
            debug.log(DebugContext.VERBOSE_LEVEL, "adding %s to the worklist", block);
            worklist.offerLast(block);
        }
    }

    private void leafCost(AbstractBlockBase<?> block) {
        tree.set(Flags.CANDIDATE, block);
        tree.getOrInitCost(block);
    }

    private boolean isMarked(AbstractBlockBase<?> block) {
        return tree.isMarked(block);
    }

    private boolean isLeafBlock(AbstractBlockBase<?> block) {
        return tree.isLeafBlock(block);
    }

}
