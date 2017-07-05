/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.phases.aot;

import static org.graalvm.compiler.nodes.ConstantNode.getConstantNodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.hotspot.nodes.aot.InitializeKlassNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.meta.Constant;

public class EliminateRedundantInitializationPhase extends BasePhase<PhaseContext> {
    /**
     * Find blocks with class initializing nodes for the class identified the by the constant node.
     * Return the map of a block to a list of initializing nodes in that block.
     *
     * @param cfg an instance of the {@link ControlFlowGraph}.
     * @param constant common input to the instances of {@link InitializeKlassNode}.
     * @return map of blocks to lists of initializing nodes.
     */
    private static HashMap<Block, ArrayList<Node>> findBlocksWithInitializers(ControlFlowGraph cfg, ConstantNode constant) {
        // node is ConstantNode representing a metaspace constant (a klass reference).
        // InitializeKlassNodes for the same class would share the same ConstantNode input.
        NodeIterable<?> initializers = constant.usages().filter(InitializeKlassNode.class);
        // Map the found nodes to blocks
        HashMap<Block, ArrayList<Node>> blockToInits = new HashMap<>();
        for (Node i : initializers) {
            Block b = cfg.blockFor(i);
            ArrayList<Node> initsInBlock = blockToInits.get(b);
            if (initsInBlock == null) {
                initsInBlock = new ArrayList<>();
            }
            initsInBlock.add(i);
            blockToInits.put(b, initsInBlock);
        }
        return blockToInits;
    }

    /**
     * Process the block-to-initializers map and produce a list of blocks that contain more than one
     * instance of {@link InitializeKlassNode}.
     *
     * @param blockToInits a map of blocks to lists of {@link InitializeKlassNode} instances.
     * @return list of blocks that contain multiple instances of {@link InitializeKlassNode}.
     */
    private static ArrayList<Block> findBlocksWithMultipleInitializers(HashMap<Block, ArrayList<Node>> blockToInits) {
        ArrayList<Block> result = new ArrayList<>();
        // Select the blocks from the blocksToInits map that have more than one InitializeKlassNode
        for (Entry<Block, ArrayList<Node>> e : blockToInits.entrySet()) {
            if (e.getValue().size() > 1) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    /**
     * Iterate through blocks with multiple instances of {@link InitializeKlassNode} and identify
     * redundant instances. Remove redundant instances from the block-to-list-of-initializer map.
     *
     * @param blockToInits a map of blocks to lists of {@link InitializeKlassNode} instances.
     * @param blocksWithMultipleInits a list of blocks that contain multiple instances of
     *            {@link InitializeKlassNode}.
     * @param constant common input to the instances of {@link InitializeKlassNode}.
     * @return list of {@link InitializeKlassNode} instances that can be removed.
     */
    private static ArrayList<Node> findRedundantLocalInitializers(HashMap<Block, ArrayList<Node>> blockToInits, ArrayList<Block> blocksWithMultipleInits, ConstantNode constant) {
        ArrayList<Node> result = new ArrayList<>();
        for (Block b : blocksWithMultipleInits) {
            // First initializer for our constant in the block
            InitializeKlassNode first = null;
            for (Node n : b.getNodes()) {
                if (n instanceof InitializeKlassNode) {
                    InitializeKlassNode i = (InitializeKlassNode) n;
                    if (i.value() == constant) {
                        if (first == null) {
                            // First instance of {@link InitializeKlassNode} stays.
                            first = i;
                        } else {
                            // All the following instances of {@link InitializeKlassNode} can be
                            // removed.
                            result.add(i);
                        }
                    }
                }
            }
            assert first != null;

            // Replace the entry in the initsInBlock map to contain just a single initializer
            ArrayList<Node> initsInBlock = new ArrayList<>();
            initsInBlock.add(first);
            blockToInits.put(b, initsInBlock);
        }
        return result;
    }

    /**
     * Find cases when one {@link InitializeKlassNode} instance dominates another. The dominated
     * instance can be removed.
     *
     * @param blockToInits a map of blocks to lists of {@link InitializeKlassNode} instances.
     * @return list of {@link InitializeKlassNode} instances that can be removed.
     */
    private static ArrayList<Node> findRedundantGlobalInitializers(HashMap<Block, ArrayList<Node>> blockToInits) {
        ArrayList<Node> result = new ArrayList<>();
        for (Entry<Block, ArrayList<Node>> e : blockToInits.entrySet()) {
            Block currentBlock = e.getKey();
            ArrayList<Node> nodesInCurrent = e.getValue();
            if (nodesInCurrent != null) { // if the list is null, the initializer has already been
                                          // eliminated.
                for (Block d : currentBlock.getDominated()) {
                    ArrayList<Node> nodesInDominated = blockToInits.get(d);
                    if (nodesInDominated != null) { // if the list is null, the initializer has
                                                    // already been eliminated.
                        assert nodesInDominated.size() == 1;
                        Node n = nodesInDominated.iterator().next();
                        result.add(n);
                        blockToInits.put(d, null);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Compute the list of redundant {@link InitializeKlassNode} instances that have the common
     * {@link ConstantNode}.
     *
     * @param cfg an instance of the {@link ControlFlowGraph}.
     * @param constant common input to the instances of {@link InitializeKlassNode}.
     * @return list of {@link InitializeKlassNode} instances that can be removed.
     */
    private static ArrayList<Node> processConstantNode(ControlFlowGraph cfg, ConstantNode constant) {
        HashMap<Block, ArrayList<Node>> blockToInits = findBlocksWithInitializers(cfg, constant);
        ArrayList<Block> blocksWithMultipleInits = findBlocksWithMultipleInitializers(blockToInits);
        ArrayList<Node> redundantInits = findRedundantLocalInitializers(blockToInits, blocksWithMultipleInits, constant);
        // At this point each block has at most one initializer for this constant
        if (blockToInits.size() > 1) {
            redundantInits.addAll(findRedundantGlobalInitializers(blockToInits));
        }
        return redundantInits;
    }

    /**
     * Find each {@link Invoke} that has a corresponding {@link InitializeKlassNode}. These
     * {@link InitializeKlassNode} are redundant and are removed.
     *
     */
    private static void removeInitsAtStaticCalls(StructuredGraph graph) {
        for (Invoke invoke : graph.getInvokes()) {
            if (invoke.classInit() != null) {
                Node classInit = invoke.classInit();
                classInit.replaceAtUsages(null);
                graph.removeFixed((FixedWithNextNode) classInit);
            }
        }
    }

    /**
     * Find {@link InitializeKlassNode} instances that can be removed because there is an existing
     * dominating initialization.
     *
     * @param graph the program graph.
     */
    private static void removeRedundantInits(StructuredGraph graph) {
        // Create cfg, we need blocks and dominators.
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, false, true, false);
        ArrayList<Node> redundantInits = new ArrayList<>();
        for (ConstantNode node : getConstantNodes(graph)) {
            Constant constant = node.asConstant();
            if (constant instanceof HotSpotMetaspaceConstant) {
                redundantInits.addAll(processConstantNode(cfg, node));
            }
        }
        // Remove redundant instances of {@link InitializeKlassNode} from the graph.
        for (Node n : redundantInits) {
            graph.removeFixed((FixedWithNextNode) n);
        }
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        removeInitsAtStaticCalls(graph);
        removeRedundantInits(graph);
    }
}
