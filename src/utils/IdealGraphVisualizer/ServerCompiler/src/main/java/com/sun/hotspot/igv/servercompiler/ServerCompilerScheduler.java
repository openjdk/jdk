/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.sun.hotspot.igv.servercompiler;

import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.dominators.Dominators;
import com.ibm.wala.util.graph.impl.SlowSparseNumberedGraph;
import com.sun.hotspot.igv.data.InputBlock;
import com.sun.hotspot.igv.data.InputEdge;
import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.data.services.Scheduler;
import java.util.*;
import java.util.function.Predicate;
import org.openide.ErrorManager;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Thomas Wuerthinger
 */
@ServiceProvider(service=Scheduler.class)
public class ServerCompilerScheduler implements Scheduler {

    private static class Node {

        public static final String WARNING_BLOCK_PROJECTION_WITH_MULTIPLE_SUCCS = "Block projection with multiple successors";
        public static final String WARNING_REGION_WITH_MULTIPLE_SUCCS = "Region with multiple successors";
        public static final String WARNING_PHI_INPUT_WITHOUT_REGION = "Phi input without associated region";
        public static final String WARNING_REGION_WITHOUT_CONTROL_INPUT = "Region without control input";
        public static final String WARNING_PHI_WITH_REGIONLESS_INPUTS = "Phi node with input nodes without associated region";
        public static final String WARNING_NOT_MARKED_WITH_BLOCK_START = "Region not marked with is_block_start";
        public static final String WARNING_CFG_AND_INPUT_TO_PHI = "CFG node is a phi input";
        public static final String WARNING_PHI_NON_DOMINATING_INPUTS = "Phi input that does not dominate the phi's input block";
        public static final String WARNING_CONTROL_UNREACHABLE_CFG = "Control-unreachable CFG node";
        public static final String WARNING_CFG_WITHOUT_SUCCESSORS = "CFG node without control successors";

        public InputNode inputNode;
        public Set<Node> succs = new HashSet<>();
        public List<Node> preds = new ArrayList<>();
        // Index of each predecessor.
        public List<Character> predIndices = new ArrayList<>();
        public List<String> warnings;
        public InputBlock block;
        public boolean isBlockProjection;
        public boolean isBlockStart;
        public boolean isCFG;
        public boolean isParm;
        public boolean isProj;
        public int rank; // Rank for local scheduling priority.

        // Empty constructor for creating dummy CFG nodes without associated
        // input nodes.
        public Node() {}

        public Node(InputNode n) {
            inputNode = n;
            String p = n.getProperties().get("is_block_proj");
            isBlockProjection = (p != null && p.equals("true"));
            p = n.getProperties().get("is_block_start");
            isBlockStart = (p != null && p.equals("true"));
            isParm = isParm(this);
            isProj = isProj(this);
            computeRank();
        }

        // Rank by local scheduling priority.
        private void computeRank() {
            if (isBlockStart || isOtherBlockStart(this)) {
                rank = 1;
            } else if (isPhi(this)) {
                rank = 2;
            } else if (isParm(this)) {
                rank = 3;
            } else if (isProj(this)) {
                rank = 4;
            } else if (!isControl(this)) { // Every other node except terminators.
                rank = 5;
            } else {
                rank = 6;
            }
        }

        public void addWarning(String msg) {
            if (warnings == null) {
                warnings = new ArrayList<>();
            }
            warnings.add(msg);
        }

        @Override
        public String toString() {
            if (inputNode == null) {
                return "(dummy node)";
            }
            return inputNode.getProperties().get("idx") + " " + inputNode.getProperties().get("name");
        }
    }
    private InputGraph graph;
    private Collection<Node> nodes;
    private Map<InputNode, Node> inputNodeToNode;
    private Vector<InputBlock> blocks;
    // CFG successors of each CFG node, excluding self edges.
    Map<Node, List<Node>> controlSuccs = new HashMap<>();
    // Nodes reachable in backward traversal from root.
    private Map<InputBlock, InputBlock> dominatorMap;
    private static final Comparator<InputEdge> edgeComparator = Comparator.comparingInt(InputEdge::getToIndex);

    public void buildBlocks() {

        // Initialize data structures.
        blocks = new Vector<>();
        Node root = findRoot();
        if (root == null) {
            return;
        }
        Stack<Node> stack = new Stack<>();
        Set<Node> visited = new HashSet<>();
        Map<InputBlock, Set<Node>> terminators = new HashMap<>();
        for (Node n : nodes) {
            if (n.isCFG) {
                List<Node> nControlSuccs = new ArrayList<>();
                for (Node s : n.succs) {
                    if (s.isCFG && s != n) {
                        nControlSuccs.add(s);
                    }
                }
                // Ensure that the block ordering is deterministic.
                nControlSuccs.sort(Comparator.comparingInt((Node a) -> a.inputNode.getId()));
                controlSuccs.put(n, nControlSuccs);
            }
        }
        stack.add(root);
        // Start from 1 to follow the style of compiler-generated CFGs.
        int blockCount = 1;
        InputBlock rootBlock = null;

        // Traverse the control-flow subgraph forwards, starting from the root.
        while (!stack.isEmpty()) {
            // Pop a node, mark it as visited, and create a new block.
            Node n = stack.pop();
            if (visited.contains(n)) {
                continue;
            }
            visited.add(n);
            InputBlock block = graph.addBlock(Integer.toString(blockCount));
            blocks.add(block);
            if (n == root) {
                rootBlock = block;
            }
            blockCount++;
            Set<Node> blockTerminators = new HashSet<>();
            // Move forwards until a terminator node is found, assigning all
            // visited nodes to the current block.
            while (true) {
                // Assign n to current block.
                n.block = block;
                if (controlSuccs.get(n).size() == 0) {
                    // No successors: end the block.
                    blockTerminators.add(n);
                    break;
                } else if (controlSuccs.get(n).size() == 1) {
                    // One successor: end the block if it is a block start node.
                    Node s = controlSuccs.get(n).iterator().next();
                    if (s.isBlockStart || isDummy(n)) {
                        // Block start or n is a dummy node: end the block. The
                        // second condition handles ill-formed graphs where join
                        // Region nodes are not marked as block starts.
                        blockTerminators.add(n);
                        stack.push(s);
                        break;
                    } else {
                        // Not a block start: keep filling the current block.
                        n = s;
                    }
                } else {
                    // Multiple successors: end the block.
                    for (Node s : controlSuccs.get(n)) {
                        if (s.isBlockProjection && s != root) {
                            // Assign block projections to the current block,
                            // and push their successors to the stack. In the
                            // normal case, we would expect control projections
                            // to have only one successor, but there are some
                            // intermediate graphs (e.g. 'Before RemoveUseless')
                            // where 'IfX' nodes flow both to 'Region' and
                            // (dead) 'Safepoint' nodes.
                            s.block = block;
                            blockTerminators.add(s);
                            List<Node> projSuccs = controlSuccs.get(s);
                            if (projSuccs.size() > 1) {
                                s.addWarning(Node.WARNING_BLOCK_PROJECTION_WITH_MULTIPLE_SUCCS);
                            }
                            // If s has only one CFG successor ss (regular
                            // case), there is a node pinned to s, and ss has
                            // multiple CFG predecessors, insert a block between
                            // s and ss. This is done by adding a dummy CFG node
                            // that has no correspondence in the input graph.
                            if (projSuccs.size() == 1 &&
                                s.succs.stream().anyMatch(ss -> pinnedNode(ss) == s) &&
                                projSuccs.get(0).preds.stream().filter(ssp -> ssp.isCFG).count() > 1) {
                                stack.push(insertDummyCFGNode(s, projSuccs.get(0)));
                                continue;
                            }
                            for (Node ps : projSuccs) {
                                stack.push(ps);
                            }
                        } else {
                            blockTerminators.add(n);
                            stack.push(s);
                        }
                    }
                    break;
                }
            }
            terminators.put(block, blockTerminators);
        }

        // Add block edges based on terminator successors. Note that a block
        // might have multiple terminators preceding the same successor block.
        for (Map.Entry<InputBlock, Set<Node>> terms : terminators.entrySet()) {
            // Unique set of terminator successors.
            Set<Node> uniqueSuccs = new HashSet<>();
            for (Node t : terms.getValue()) {
                for (Node s : controlSuccs.get(t)) {
                    if (s.block != rootBlock) {
                        uniqueSuccs.add(s);
                    }
                }
            }
            for (Node s : uniqueSuccs) {
                // Label the block edge with the short name of the corresponding
                // control projection, if any.
                String label = null;
                if (terms.getValue().size() > 1) {
                    for (Node t : terms.getValue()) {
                        if (s.preds.contains(t)) {
                            label = t.inputNode.getProperties().get("short_name");
                            break;
                        }
                    }
                }
                graph.addBlockEdge(terms.getKey(), s.block, label);
            }
        }

        // Fill the blocks.
        for (Node n : nodes) {
            InputBlock block = n.block;
            if (block != null) {
                block.addNode(n.inputNode.getId());
            }
        }
    }

    // Create a dummy CFG node (without correspondence in the input graph) and
    // insert it between p and s.
    private Node insertDummyCFGNode(Node p, Node s) {
        // Create in-between node.
        Node n = new Node();
        n.preds.add(p);
        n.succs.add(s);
        controlSuccs.put(n, Collections.singletonList(s));
        n.isCFG = true;
        // Update predecessor node p.
        p.succs.remove(s);
        p.succs.add(n);
        controlSuccs.put(p, Collections.singletonList(n));
        // Update successor node s.
        Collections.replaceAll(s.preds, p, n);
        return n;
    }

    private String getBlockName(InputNode n) {
        return n.getProperties().get("block");
    }

    @Override
    public Collection<InputBlock> schedule(InputGraph graph) {
        if (graph.getNodes().isEmpty()) {
            return Collections.emptyList();
        }

        if (graph.getBlocks().size() > 0) {
            Collection<InputNode> tmpNodes = new ArrayList<>(graph.getNodes());
            for (InputNode n : tmpNodes) {
                String block = getBlockName(n);
                if (graph.getBlock(n) == null) {
                    graph.getBlock(block).addNode(n.getId());
                    assert graph.getBlock(n) != null;
                }
            }
            return graph.getBlocks();
        } else {
            nodes = new ArrayList<>();
            inputNodeToNode = new HashMap<>(graph.getNodes().size());

            this.graph = graph;
            if (!hasCategoryInformation()) {
                ErrorManager.getDefault().log(ErrorManager.WARNING,
                    "Cannot find node category information in the input graph. " +
                    "The control-flow graph will not be approximated.");
                return null;
            }
            buildUpGraph();
            markCFGNodes();
            buildBlocks();
            schedulePinned();
            buildDominators();
            scheduleLatest();
            scheduleLocal();
            check();
            reportWarnings();

            return blocks;
        }
    }

    private void scheduleLocal() {
        // Leave only local predecessors and successors.
        for (InputBlock b : blocks) {
            for (InputNode in : b.getNodes()) {
                Node n = inputNodeToNode.get(in);
                Predicate<Node> excludePredecessors =
                    node -> isPhi(node) || node.isBlockStart;
                List<Node> localPreds = new ArrayList<>(n.preds.size());
                for (Node p : n.preds) {
                    if (p.block == b && p != n && !excludePredecessors.test(n)) {
                        localPreds.add(p);
                    }
                }
                n.preds = localPreds;
                Set<Node> localSuccs = new HashSet<>(n.succs.size());
                for (Node s : n.succs) {
                    if (s.block == b && s != n && !excludePredecessors.test(s)) {
                        localSuccs.add(s);
                    }
                }
                n.succs = localSuccs;
            }
        }
        // Schedule each block independently.
        for (InputBlock b : blocks) {
            List<Node> nodes = new ArrayList<>(b.getNodes().size());
            for (InputNode n : b.getNodes()) {
                nodes.add(inputNodeToNode.get(n));
            }
            List<InputNode> schedule = scheduleBlock(nodes);
            b.setNodes(schedule);
        }
    }

    private static final Comparator<Node> schedulePriority = (n1, n2) -> {
        // Order by rank, then idx.
        int r1 = n1.rank, r2 = n2.rank;
        int o1, o2;
        if (r1 != r2) { // Different rank.
            o1 = r1;
            o2 = r2;
        } else { // Same rank, order by idx.
            o1 = Integer.parseInt(n1.inputNode.getProperties().get("idx"));
            o2 = Integer.parseInt(n2.inputNode.getProperties().get("idx"));
        }
        return Integer.compare(o1, o2);
    };

    private List<InputNode> scheduleBlock(Collection<Node> nodes) {
        LinkedHashSet<InputNode> schedule = new LinkedHashSet<InputNode>();

        // Initialize ready priority queue with nodes without predecessors.
        Queue<Node> ready = new PriorityQueue<>(schedulePriority);
        // Set of nodes that have been enqueued.
        Set<Node> visited = new HashSet<>(nodes.size());
        for (Node n : nodes) {
            if (n.preds.isEmpty()) {
                ready.add(n);
                visited.add(n);
            }
        }

        // Classic list scheduling algorithm.
        while (!ready.isEmpty()) {
            Node n = ready.remove();
            schedule.add(n.inputNode);

            // Add nodes that are now ready after scheduling n.
            for (Node s : n.succs) {
                if (visited.contains(s)) {
                    continue;
                }
                boolean allPredsScheduled = true;
                for (Node p : s.preds) {
                    if (!schedule.contains(p.inputNode)) {
                        allPredsScheduled = false;
                        break;
                    }
                }
                if (allPredsScheduled) {
                    ready.add(s);
                    visited.add(s);
                }
            }
        }
        assert(schedule.size() == nodes.size());
        return new ArrayList<InputNode>(schedule);
    }

    // Return latest block that dominates all successors of n, or null if any
    // successor is not yet scheduled.
    private InputBlock commonDominatorOfSuccessors(Node n, Set<Node> reachable) {
        InputBlock block = null;
        for (Node s : n.succs) {
            if (!reachable.contains(s)) {
                // Unreachable successors should not affect the schedule.
                continue;
            }
            if (s.block == null) {
                // Successor is not yet scheduled, wait for it.
                return null;
            } else if (isPhi(s)) {
                // Move inputs above their source blocks.
                boolean foundSourceBlock = false;
                for (InputBlock srcBlock : sourceBlocks(n, s)) {
                    foundSourceBlock = true;
                    block = getCommonDominator(block, srcBlock);
                }
                if (!foundSourceBlock) {
                    // Can happen due to inconsistent phi-region pairs.
                    block = s.block;
                    n.addWarning(Node.WARNING_PHI_INPUT_WITHOUT_REGION);
                }
            } else {
                // Common case, update current block to also dominate s.
                block = getCommonDominator(block, s.block);
            }
        }
        return block;
    }

    private void scheduleLatest() {

        // Mark all nodes reachable in backward traversal from root
        Set<Node> reachable = reachableNodes();
        // Schedule non-CFG, reachable nodes without block. CFG nodes should
        // have been all scheduled by buildBlocks, otherwise it means they are
        // control-unreachable and they should remain unscheduled.
        Set<Node> unscheduled = new HashSet<>();
        for (Node n : this.nodes) {
            if (n.block == null && reachable.contains(n) && !n.isCFG) {
                if (!n.isParm && !n.isProj) {
                    unscheduled.add(n);
                } else {
                    // Schedule Parm and Proj nodes in same block as parent
                    Node prev = n.preds.get(0);
                    InputBlock blk = prev.block;
                    if (blk != null) {
                        n.block = blk;
                        blk.addNode(n.inputNode.getId());
                    } else {
                        // Fallback in the case parent has no block
                        unscheduled.add(n);
                    }
                }
            }
        }

        while (unscheduled.size() > 0) {
            boolean progress = false;

            Set<Node> newUnscheduled = new HashSet<>();
            for (Node n : unscheduled) {
                InputBlock block = commonDominatorOfSuccessors(n, reachable);
                if (block != null) {
                    n.block = block;
                    block.addNode(n.inputNode.getId());
                    progress = true;
                } else {
                    newUnscheduled.add(n);
                }
            }

            unscheduled = newUnscheduled;

            if (!progress) {
                break;
            }
        }

    }

    // Recompute the input array of the given node, including empty slots.
    private Node[] inputArray(Node n) {
        Node[] inputs = new Node[Collections.max(n.predIndices) + 1];
        for (int i = 0; i < n.preds.size(); i++) {
            inputs[n.predIndices.get(i)] = n.preds.get(i);
        }
        return inputs;
    }

    // Find the blocks from which node 'in' flows into 'phi'.
    private Set<InputBlock> sourceBlocks(Node in, Node phi) {
        Node reg = phi.preds.get(0);
        assert (reg != null);
        // Reconstruct the positional input arrays of phi-region pairs.
        Node[] phiInputs = inputArray(phi);
        Node[] regInputs = inputArray(reg);

        Set<InputBlock> srcBlocks = new HashSet<>();
        for (int i = 0; i < Math.min(phiInputs.length, regInputs.length); i++) {
            if (phiInputs[i] == in) {
                if (regInputs[i] != null) {
                    if (regInputs[i].isCFG) {
                        srcBlocks.add(regInputs[i].block);
                    } else {
                        reg.addWarning(Node.WARNING_REGION_WITHOUT_CONTROL_INPUT);
                    }
                } else {
                    phi.addWarning(Node.WARNING_PHI_WITH_REGIONLESS_INPUTS);
                }
            }
        }
        return srcBlocks;
    }

    public InputBlock getCommonDominator(InputBlock ba, InputBlock bb) {
        if (ba == bb) {
            return ba;
        }
        if (ba == null) {
            return bb;
        }
        if (bb == null) {
            return ba;
        }
        Set<InputBlock> visited = new HashSet<>();
        while (ba != null) {
            visited.add(ba);
            ba = dominatorMap.get(ba);
        }

        while (bb != null) {
            if (visited.contains(bb)) {
                return bb;
            }
            bb = dominatorMap.get(bb);
        }

        assert false;
        return null;
    }

    // Schedule nodes pinned to region-like nodes in the same block. Schedule
    // nodes pinned to block projections s in their successor block ss.
    // buildBlocks() guarantees that s is the only predecessor of ss.
    public void schedulePinned() {
        Set<Node> reachable = reachableNodes();
        for (Node n : nodes) {
            if (!reachable.contains(n) ||
                n.block != null) {
                continue;
            }
            Node ctrlIn = pinnedNode(n);
            if (ctrlIn == null) {
                continue;
            }
            InputBlock block = ctrlIn.block;
            if (ctrlIn.isBlockProjection) {
                // Block projections should not have successors in their block:
                // if n is pinned to a block projection, push it downwards.
                if (controlSuccs.get(ctrlIn).size() == 1) {
                    block = controlSuccs.get(ctrlIn).get(0).block;
                }
            }
            if (block == null) {
                // This can happen for blockless CFG nodes that are not
                // control-reachable from the root even after connecting orphans
                // and widows (e.g. in disconnected control cycles).
                continue;
            }
            n.block = block;
            block.addNode(n.inputNode.getId());
        }
    }

    // Return the control node to which 'n' is pinned, or null if none.
    public Node pinnedNode(Node n) {
        if (n.preds.isEmpty()) {
            return null;
        }
        Node ctrlIn = n.preds.get(0);
        if (!isControl(ctrlIn)) {
            return null;
        }
        return ctrlIn;
    }

    public void buildDominators() {
        dominatorMap = new HashMap<>(graph.getBlocks().size());
        if (blocks.size() == 0) {
            return;
        }

        Graph<InputBlock> CFG = SlowSparseNumberedGraph.make();
        for (InputBlock b : blocks) {
            CFG.addNode(b);
        }
        for (InputBlock p : blocks) {
            for (InputBlock s : p.getSuccessors()) {
                CFG.addEdge(p, s);
            }
        }

        InputBlock root = findRoot().block;
        Dominators<InputBlock> D = Dominators.make(CFG, root);

        for (InputBlock b : blocks) {
            InputBlock idom = D.getIdom(b);
            if (idom == null && b != root) {
                // getCommonDominator expects a single root node.
                idom = root;
            }
            dominatorMap.put(b, idom);
        }
    }

    private static boolean isOtherBlockStart(Node n) {
        return hasName(n, "CountedLoopEnd");
    }

    private static boolean isPhi(Node n) {
        return hasName(n, "Phi");
    }

    private static boolean isProj(Node n) {
        return hasName(n, "Proj") || hasName(n, "MachProj");
    }

    private static boolean isParm(Node n) {
        return hasName(n, "Parm");
    }

    private static boolean isRegion(Node n) {
        return hasName(n, "Region");
    }

    private static boolean hasName(Node n, String name) {
        String nodeName = n.inputNode.getProperties().get("name");
        if (nodeName == null) {
            return false;
        }
        return nodeName.equals(name);
    }

    private static boolean isControl(Node n) {
        return n.inputNode.getProperties().get("category").equals("control");
    }

    private static boolean isDummy(Node n) {
        return n.inputNode == null;
    }

    // Whether b1 dominates b2. Used only for checking the schedule.
    private boolean dominates(InputBlock b1, InputBlock b2) {
        InputBlock bi = b2;
        do {
            if (bi.equals(b1)) {
                return true;
            }
            bi = dominatorMap.get(bi);
        } while (bi != null);
        return false;
    }

    private Node findRoot() {
        Node minNode = null;
        Node alternativeRoot = null;

        for (Node node : nodes) {
            if (hasName(node, "Root")) {
                return node;
            }

            if (alternativeRoot == null && node.preds.isEmpty()) {
                alternativeRoot = node;
            }

            if (minNode == null || node.inputNode.getId() < minNode.inputNode.getId()) {
                minNode = node;
            }
        }

        if (alternativeRoot != null) {
            return alternativeRoot;
        } else {
            return minNode;
        }
    }

    // Find all nodes reachable in backward traversal from root.
    private Set<Node> reachableNodes() {
        Node root = findRoot();
        assert root != null : "No root found!";
        Set<Node> reachable = new HashSet<>();
        reachable.add(root);
        Stack<Node> stack = new Stack<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node cur = stack.pop();
            for (Node n : cur.preds) {
                if (!reachable.contains(n)) {
                    reachable.add(n);
                    stack.push(n);
                }
            }
        }
        return reachable;
    }

    public boolean hasCategoryInformation() {
        for (InputNode n : graph.getNodes()) {
            if (n.getProperties().get("category") == null) {
                return false;
            }
        }
        return true;
    }

    public void buildUpGraph() {

        for (InputNode n : graph.getNodes()) {
            Node node = new Node(n);
            nodes.add(node);
            inputNodeToNode.put(n, node);
        }

        Map<Integer, List<InputEdge>> edgeMap = new HashMap<>(graph.getEdges().size());
        for (InputEdge e : graph.getEdges()) {

            int to = e.getTo();
            if (!edgeMap.containsKey(to)) {
                edgeMap.put(to, new ArrayList<>());
            }


            List<InputEdge> list = edgeMap.get(to);
            list.add(e);
        }


        for (Integer i : edgeMap.keySet()) {

            List<InputEdge> list = edgeMap.get(i);
            list.sort(edgeComparator);

            int to = i;
            InputNode toInputNode = graph.getNode(to);
            Node toNode = inputNodeToNode.get(toInputNode);
            for (InputEdge e : list) {
                assert to == e.getTo();
                int from = e.getFrom();
                InputNode fromInputNode = graph.getNode(from);
                Node fromNode = inputNodeToNode.get(fromInputNode);
                fromNode.succs.add(toNode);
                toNode.preds.add(fromNode);
                toNode.predIndices.add(e.getToIndex());
            }
        }
    }

    // Mark nodes that form the CFG (same as shown by the 'Show control flow
    // only' filter, plus the Root node).
    public void markCFGNodes() {
        for (Node n : nodes) {
            String category = n.inputNode.getProperties().get("category");
            if (category.equals("control") || category.equals("mixed")) {
                // Example: If, IfTrue, CallStaticJava.
                n.isCFG = true;
            } else if (n.inputNode.getProperties().get("type").equals("bottom") &&
                       n.preds.size() > 0 &&
                       n.succs.size() > 0 &&
                       n.succs.stream().findFirst().get().inputNode.getProperties().get("name") == "Root" &&
                       n.preds.get(0) != null &&
                       n.preds.get(0).inputNode.getProperties()
                       .get("category").equals("control")) {
                // Example: Halt, Return, Rethrow.
                // The root-as-successor check disallows machine nodes such as prefetchAlloc and rep_stos.
                n.isCFG = true;
            } else if (n.isBlockStart || n.isBlockProjection) {
                // Example: Root.
                n.isCFG = true;
            } else {
                n.isCFG = false;
            }
        }
    }

    // Check invariants in the input graph and in the output schedule, and add
    // warnings to nodes where the invariants do not hold.
    public void check() {
        Set<Node> reachable = reachableNodes();
        for (Node n : nodes) {
            // Check that region nodes are well-formed.
            if (isRegion(n) && !n.isBlockStart) {
                n.addWarning(Node.WARNING_NOT_MARKED_WITH_BLOCK_START);
            }
            if (isRegion(n) && controlSuccs.get(n).size() > 1) {
                n.addWarning(Node.WARNING_REGION_WITH_MULTIPLE_SUCCS);
            }
            if (n.isCFG && controlSuccs.get(n).isEmpty()) {
                n.addWarning(Node.WARNING_CFG_WITHOUT_SUCCESSORS);
            }
            if (n.isCFG && n.block == null) {
                n.addWarning(Node.WARNING_CONTROL_UNREACHABLE_CFG);
            }
            if (!isPhi(n)) {
                continue;
            }
            if (!reachable.contains(n)) { // Dead phi.
                continue;
            }
            // Check that phi nodes and their inputs are well-formed.
            for (int i = 1; i < n.preds.size(); i++) {
                Node in = n.preds.get(i);
                if (in.isCFG) {
                    // This can happen for nodes misclassified as CFG, for
                    // example x64's 'rep_stos'.
                    in.addWarning(Node.WARNING_CFG_AND_INPUT_TO_PHI);
                    continue;
                }
                for (InputBlock b : sourceBlocks(in, n)) {
                    if (!dominates(graph.getBlock(in.inputNode), b)) {
                        in.addWarning(Node.WARNING_PHI_NON_DOMINATING_INPUTS);
                    }
                }
            }
        }
    }

    // Report potential and actual innacuracies in the schedule approximation.
    // IGV tries to warn, rather than crashing, for robustness: an inaccuracy in
    // the schedule approximation or an inconsistency in the input graph should
    // not disable all IGV functionality, and debugging inconsistent graphs is a
    // key use case of IGV. Warns are reported visually for each node (if the
    // corresponding filter is active) and textually in the IGV log.
    public void reportWarnings() {
        Map<String, Set<Node>> nodesPerWarning = new HashMap<>();
        for (Node n : nodes) {
            if (n.warnings == null || n.warnings.isEmpty()) {
                continue;
            }
            for (String warning : n.warnings) {
                if (!nodesPerWarning.containsKey(warning)) {
                    nodesPerWarning.put(warning, new HashSet<>());
                }
                nodesPerWarning.get(warning).add(n);
            }
            // Attach warnings to each node as a property to be shown in the
            // graph views.
            String nodeWarnings = String.join("; ", n.warnings);
            n.inputNode.getProperties().setProperty("warnings", nodeWarnings);
        }
        // Report warnings textually.
        for (Map.Entry<String, Set<Node>> entry : nodesPerWarning.entrySet()) {
            String warning = entry.getKey();
            Set<Node> nodes = entry.getValue();
            String nodeList = nodes.toString().replace("[", "(").replace("]", ")");
            String message = warning + " " + nodeList;
            ErrorManager.getDefault().log(ErrorManager.WARNING, message);
        }
    }

}
