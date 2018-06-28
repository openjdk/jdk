/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.loop;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;

import jdk.internal.vm.compiler.collections.EconomicMap;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Graph.DuplicationReplacement;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.GuardPhiNode;
import org.graalvm.compiler.nodes.GuardProxyNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.VirtualState;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.java.MonitorEnterNode;
import org.graalvm.compiler.nodes.spi.NodeWithState;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.TriState;

public abstract class LoopFragment {

    private final LoopEx loop;
    private final LoopFragment original;
    protected NodeBitMap nodes;
    protected boolean nodesReady;
    private EconomicMap<Node, Node> duplicationMap;

    public LoopFragment(LoopEx loop) {
        this(loop, null);
        this.nodesReady = true;
    }

    public LoopFragment(LoopEx loop, LoopFragment original) {
        this.loop = loop;
        this.original = original;
        this.nodesReady = false;
    }

    /**
     * Return the original LoopEx for this fragment. For duplicated fragments this returns null.
     */
    protected LoopEx loop() {
        return loop;
    }

    public abstract LoopFragment duplicate();

    public abstract void insertBefore(LoopEx l);

    public void disconnect() {
        // TODO (gd) possibly abstract
    }

    public boolean contains(Node n) {
        return nodes().isMarkedAndGrow(n);
    }

    @SuppressWarnings("unchecked")
    public <New extends Node, Old extends New> New getDuplicatedNode(Old n) {
        assert isDuplicate();
        return (New) duplicationMap.get(n);
    }

    protected <New extends Node, Old extends New> void putDuplicatedNode(Old oldNode, New newNode) {
        duplicationMap.put(oldNode, newNode);
    }

    /**
     * Gets the corresponding value in this fragment. Should be called on duplicate fragments with a
     * node from the original fragment as argument.
     *
     * @param b original value
     * @return corresponding value in the peel
     */
    protected abstract ValueNode prim(ValueNode b);

    public boolean isDuplicate() {
        return original != null;
    }

    public LoopFragment original() {
        return original;
    }

    public abstract NodeBitMap nodes();

    public StructuredGraph graph() {
        LoopEx l;
        if (isDuplicate()) {
            l = original().loop();
        } else {
            l = loop();
        }
        return l.loopBegin().graph();
    }

    protected abstract DuplicationReplacement getDuplicationReplacement();

    protected abstract void beforeDuplication();

    protected abstract void finishDuplication();

    protected void patchNodes(final DuplicationReplacement dataFix) {
        if (isDuplicate() && !nodesReady) {
            assert !original.isDuplicate();
            final DuplicationReplacement cfgFix = original().getDuplicationReplacement();
            DuplicationReplacement dr;
            if (cfgFix == null && dataFix != null) {
                dr = dataFix;
            } else if (cfgFix != null && dataFix == null) {
                dr = cfgFix;
            } else if (cfgFix != null && dataFix != null) {
                dr = new DuplicationReplacement() {

                    @Override
                    public Node replacement(Node o) {
                        Node r1 = dataFix.replacement(o);
                        if (r1 != o) {
                            assert cfgFix.replacement(o) == o;
                            return r1;
                        }
                        Node r2 = cfgFix.replacement(o);
                        if (r2 != o) {
                            return r2;
                        }
                        return o;
                    }
                };
            } else {
                dr = null;
            }
            beforeDuplication();
            NodeIterable<Node> nodesIterable = original().nodes();
            duplicationMap = graph().addDuplicates(nodesIterable, graph(), nodesIterable.count(), dr);
            finishDuplication();
            nodes = new NodeBitMap(graph());
            nodes.markAll(duplicationMap.getValues());
            nodesReady = true;
        } else {
            // TODO (gd) apply fix ?
        }
    }

    protected static NodeBitMap computeNodes(Graph graph, Iterable<AbstractBeginNode> blocks) {
        return computeNodes(graph, blocks, Collections.emptyList());
    }

    protected static NodeBitMap computeNodes(Graph graph, Iterable<AbstractBeginNode> blocks, Iterable<AbstractBeginNode> earlyExits) {
        final NodeBitMap nodes = graph.createNodeBitMap();
        computeNodes(nodes, graph, blocks, earlyExits);
        return nodes;
    }

    protected static void computeNodes(NodeBitMap nodes, Graph graph, Iterable<AbstractBeginNode> blocks, Iterable<AbstractBeginNode> earlyExits) {
        for (AbstractBeginNode b : blocks) {
            if (b.isDeleted()) {
                continue;
            }

            for (Node n : b.getBlockNodes()) {
                if (n instanceof Invoke) {
                    nodes.mark(((Invoke) n).callTarget());
                }
                if (n instanceof NodeWithState) {
                    NodeWithState withState = (NodeWithState) n;
                    withState.states().forEach(state -> state.applyToVirtual(node -> nodes.mark(node)));
                }
                if (n instanceof AbstractMergeNode) {
                    // if a merge is in the loop, all of its phis are also in the loop
                    for (PhiNode phi : ((AbstractMergeNode) n).phis()) {
                        nodes.mark(phi);
                    }
                }
                nodes.mark(n);
            }
        }
        for (AbstractBeginNode earlyExit : earlyExits) {
            if (earlyExit.isDeleted()) {
                continue;
            }

            nodes.mark(earlyExit);

            if (earlyExit instanceof LoopExitNode) {
                LoopExitNode loopExit = (LoopExitNode) earlyExit;
                FrameState stateAfter = loopExit.stateAfter();
                if (stateAfter != null) {
                    stateAfter.applyToVirtual(node -> nodes.mark(node));
                }
                for (ProxyNode proxy : loopExit.proxies()) {
                    nodes.mark(proxy);
                }
            }
        }

        final NodeBitMap nonLoopNodes = graph.createNodeBitMap();
        Deque<WorkListEntry> worklist = new ArrayDeque<>();
        for (AbstractBeginNode b : blocks) {
            if (b.isDeleted()) {
                continue;
            }

            for (Node n : b.getBlockNodes()) {
                if (n instanceof CommitAllocationNode) {
                    for (VirtualObjectNode obj : ((CommitAllocationNode) n).getVirtualObjects()) {
                        markFloating(worklist, obj, nodes, nonLoopNodes);
                    }
                }
                if (n instanceof MonitorEnterNode) {
                    markFloating(worklist, ((MonitorEnterNode) n).getMonitorId(), nodes, nonLoopNodes);
                }
                if (n instanceof AbstractMergeNode) {
                    /*
                     * Since we already marked all phi nodes as being in the loop to break cycles,
                     * we also have to iterate over their usages here.
                     */
                    for (PhiNode phi : ((AbstractMergeNode) n).phis()) {
                        for (Node usage : phi.usages()) {
                            markFloating(worklist, usage, nodes, nonLoopNodes);
                        }
                    }
                }
                for (Node usage : n.usages()) {
                    markFloating(worklist, usage, nodes, nonLoopNodes);
                }
            }
        }
    }

    static class WorkListEntry {
        final Iterator<Node> usages;
        final Node n;
        boolean isLoopNode;

        WorkListEntry(Node n, NodeBitMap loopNodes) {
            this.n = n;
            this.usages = n.usages().iterator();
            this.isLoopNode = loopNodes.isMarked(n);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof WorkListEntry)) {
                return false;
            }
            WorkListEntry other = (WorkListEntry) obj;
            return this.n == other.n;
        }

        @Override
        public int hashCode() {
            return n.hashCode();
        }
    }

    static TriState isLoopNode(Node n, NodeBitMap loopNodes, NodeBitMap nonLoopNodes) {
        if (loopNodes.isMarked(n)) {
            return TriState.TRUE;
        }
        if (nonLoopNodes.isMarked(n)) {
            return TriState.FALSE;
        }
        if (n instanceof FixedNode || n instanceof PhiNode) {
            // phi nodes are treated the same as fixed nodes in this algorithm to break cycles
            return TriState.FALSE;
        }
        return TriState.UNKNOWN;
    }

    private static void pushWorkList(Deque<WorkListEntry> workList, Node node, NodeBitMap loopNodes) {
        WorkListEntry entry = new WorkListEntry(node, loopNodes);
        assert !workList.contains(entry) : "node " + node + " added to worklist twice";
        workList.push(entry);
    }

    private static void markFloating(Deque<WorkListEntry> workList, Node start, NodeBitMap loopNodes, NodeBitMap nonLoopNodes) {
        if (isLoopNode(start, loopNodes, nonLoopNodes).isKnown()) {
            return;
        }
        pushWorkList(workList, start, loopNodes);
        while (!workList.isEmpty()) {
            WorkListEntry currentEntry = workList.peek();
            if (currentEntry.usages.hasNext()) {
                Node current = currentEntry.usages.next();
                TriState result = isLoopNode(current, loopNodes, nonLoopNodes);
                if (result.isKnown()) {
                    if (result.toBoolean()) {
                        currentEntry.isLoopNode = true;
                    }
                } else {
                    pushWorkList(workList, current, loopNodes);
                }
            } else {
                workList.pop();
                boolean isLoopNode = currentEntry.isLoopNode;
                Node current = currentEntry.n;
                if (!isLoopNode && current instanceof GuardNode) {
                    /*
                     * (gd) this is only OK if we are not going to make loop transforms based on
                     * this
                     */
                    assert !((GuardNode) current).graph().hasValueProxies();
                    isLoopNode = true;
                }
                if (isLoopNode) {
                    loopNodes.mark(current);
                    for (WorkListEntry e : workList) {
                        e.isLoopNode = true;
                    }
                } else {
                    nonLoopNodes.mark(current);
                }
            }
        }
    }

    public static NodeIterable<AbstractBeginNode> toHirBlocks(final Iterable<Block> blocks) {
        return new NodeIterable<AbstractBeginNode>() {

            @Override
            public Iterator<AbstractBeginNode> iterator() {
                final Iterator<Block> it = blocks.iterator();
                return new Iterator<AbstractBeginNode>() {

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public AbstractBeginNode next() {
                        return it.next().getBeginNode();
                    }

                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }
                };
            }

        };
    }

    public static NodeIterable<AbstractBeginNode> toHirExits(final Iterable<Block> blocks) {
        return new NodeIterable<AbstractBeginNode>() {

            @Override
            public Iterator<AbstractBeginNode> iterator() {
                final Iterator<Block> it = blocks.iterator();
                return new Iterator<AbstractBeginNode>() {

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    /**
                     * Return the true LoopExitNode for this loop or the BeginNode for the block.
                     */
                    @Override
                    public AbstractBeginNode next() {
                        Block next = it.next();
                        LoopExitNode exit = next.getLoopExit();
                        if (exit != null) {
                            return exit;
                        }
                        return next.getBeginNode();
                    }

                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }
                };
            }

        };
    }

    /**
     * Merges the early exits (i.e. loop exits) that were duplicated as part of this fragment, with
     * the original fragment's exits.
     */
    protected void mergeEarlyExits() {
        assert isDuplicate();
        StructuredGraph graph = graph();
        for (AbstractBeginNode earlyExit : LoopFragment.toHirBlocks(original().loop().loop().getExits())) {
            LoopExitNode loopEarlyExit = (LoopExitNode) earlyExit;
            FixedNode next = loopEarlyExit.next();
            if (loopEarlyExit.isDeleted() || !this.original().contains(loopEarlyExit)) {
                continue;
            }
            AbstractBeginNode newEarlyExit = getDuplicatedNode(loopEarlyExit);
            if (newEarlyExit == null) {
                continue;
            }
            MergeNode merge = graph.add(new MergeNode());
            EndNode originalEnd = graph.add(new EndNode());
            EndNode newEnd = graph.add(new EndNode());
            merge.addForwardEnd(originalEnd);
            merge.addForwardEnd(newEnd);
            loopEarlyExit.setNext(originalEnd);
            newEarlyExit.setNext(newEnd);
            merge.setNext(next);

            FrameState exitState = loopEarlyExit.stateAfter();
            if (exitState != null) {
                FrameState originalExitState = exitState;
                exitState = exitState.duplicateWithVirtualState();
                loopEarlyExit.setStateAfter(exitState);
                merge.setStateAfter(originalExitState);
                /*
                 * Using the old exit's state as the merge's state is necessary because some of the
                 * VirtualState nodes contained in the old exit's state may be shared by other
                 * dominated VirtualStates. Those dominated virtual states need to see the
                 * proxy->phi update that are applied below.
                 *
                 * We now update the original fragment's nodes accordingly:
                 */
                originalExitState.applyToVirtual(node -> original.nodes.clearAndGrow(node));
                exitState.applyToVirtual(node -> original.nodes.markAndGrow(node));
            }
            FrameState finalExitState = exitState;

            for (Node anchored : loopEarlyExit.anchored().snapshot()) {
                anchored.replaceFirstInput(loopEarlyExit, merge);
            }

            boolean newEarlyExitIsLoopExit = newEarlyExit instanceof LoopExitNode;
            for (ProxyNode vpn : loopEarlyExit.proxies().snapshot()) {
                if (vpn.hasNoUsages()) {
                    continue;
                }
                if (vpn.value() == null) {
                    assert vpn instanceof GuardProxyNode;
                    vpn.replaceAtUsages(null);
                    continue;
                }
                final ValueNode replaceWith;
                ValueNode newVpn = prim(newEarlyExitIsLoopExit ? vpn : vpn.value());
                if (newVpn != null) {
                    PhiNode phi;
                    if (vpn instanceof ValueProxyNode) {
                        phi = graph.addWithoutUnique(new ValuePhiNode(vpn.stamp(NodeView.DEFAULT), merge));
                    } else if (vpn instanceof GuardProxyNode) {
                        phi = graph.addWithoutUnique(new GuardPhiNode(merge));
                    } else {
                        throw GraalError.shouldNotReachHere();
                    }
                    phi.addInput(vpn);
                    phi.addInput(newVpn);
                    replaceWith = phi;
                } else {
                    replaceWith = vpn.value();
                }
                vpn.replaceAtMatchingUsages(replaceWith, usage -> {
                    if (merge.isPhiAtMerge(usage)) {
                        return false;
                    }
                    if (usage instanceof VirtualState) {
                        VirtualState stateUsage = (VirtualState) usage;
                        if (finalExitState != null && finalExitState.isPartOfThisState(stateUsage)) {
                            return false;
                        }
                    }
                    return true;
                });
            }
        }
    }
}
