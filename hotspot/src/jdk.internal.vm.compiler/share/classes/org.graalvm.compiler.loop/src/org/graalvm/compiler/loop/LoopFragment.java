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

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Graph.DuplicationReplacement;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.GuardPhiNode;
import org.graalvm.compiler.nodes.GuardProxyNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
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

public abstract class LoopFragment {

    private final LoopEx loop;
    private final LoopFragment original;
    protected NodeBitMap nodes;
    protected boolean nodesReady;
    private Map<Node, Node> duplicationMap;

    public LoopFragment(LoopEx loop) {
        this(loop, null);
        this.nodesReady = true;
    }

    public LoopFragment(LoopEx loop, LoopFragment original) {
        this.loop = loop;
        this.original = original;
        this.nodesReady = false;
    }

    public LoopEx loop() {
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
            NodeIterable<Node> nodesIterable = original().nodes();
            duplicationMap = graph().addDuplicates(nodesIterable, graph(), nodesIterable.count(), dr);
            finishDuplication();
            nodesReady = true;
        } else {
            // TODO (gd) apply fix ?
        }
    }

    protected static NodeBitMap computeNodes(Graph graph, Iterable<AbstractBeginNode> blocks) {
        return computeNodes(graph, blocks, Collections.emptyList());
    }

    protected static NodeBitMap computeNodes(Graph graph, Iterable<AbstractBeginNode> blocks, Iterable<LoopExitNode> earlyExits) {
        final NodeBitMap nodes = graph.createNodeBitMap();
        computeNodes(nodes, graph, blocks, earlyExits);
        return nodes;
    }

    protected static void computeNodes(NodeBitMap nodes, Graph graph, Iterable<AbstractBeginNode> blocks, Iterable<LoopExitNode> earlyExits) {
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
                nodes.mark(n);
            }
        }
        for (LoopExitNode earlyExit : earlyExits) {
            if (earlyExit.isDeleted()) {
                continue;
            }

            FrameState stateAfter = earlyExit.stateAfter();
            if (stateAfter != null) {
                stateAfter.applyToVirtual(node -> nodes.mark(node));
            }
            nodes.mark(earlyExit);
            for (ProxyNode proxy : earlyExit.proxies()) {
                nodes.mark(proxy);
            }
        }

        final NodeBitMap notloopNodes = graph.createNodeBitMap();
        for (AbstractBeginNode b : blocks) {
            if (b.isDeleted()) {
                continue;
            }

            for (Node n : b.getBlockNodes()) {
                if (n instanceof CommitAllocationNode) {
                    for (VirtualObjectNode obj : ((CommitAllocationNode) n).getVirtualObjects()) {
                        markFloating(obj, nodes, notloopNodes);
                    }
                }
                if (n instanceof MonitorEnterNode) {
                    markFloating(((MonitorEnterNode) n).getMonitorId(), nodes, notloopNodes);
                }
                for (Node usage : n.usages()) {
                    markFloating(usage, nodes, notloopNodes);
                }
            }
        }
    }

    private static boolean markFloating(Node n, NodeBitMap loopNodes, NodeBitMap notloopNodes) {
        if (loopNodes.isMarked(n)) {
            return true;
        }
        if (notloopNodes.isMarked(n)) {
            return false;
        }
        if (n instanceof FixedNode) {
            return false;
        }
        boolean mark = false;
        if (n instanceof PhiNode) {
            PhiNode phi = (PhiNode) n;
            mark = loopNodes.isMarked(phi.merge());
            if (mark) {
                loopNodes.mark(n);
            } else {
                notloopNodes.mark(n);
                return false;
            }
        }
        for (Node usage : n.usages()) {
            if (markFloating(usage, loopNodes, notloopNodes)) {
                mark = true;
            }
        }
        if (mark) {
            loopNodes.mark(n);
            return true;
        }
        notloopNodes.mark(n);
        return false;
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

    public static NodeIterable<LoopExitNode> toHirExits(final Iterable<Block> blocks) {
        return new NodeIterable<LoopExitNode>() {

            @Override
            public Iterator<LoopExitNode> iterator() {
                final Iterator<Block> it = blocks.iterator();
                return new Iterator<LoopExitNode>() {

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public LoopExitNode next() {
                        return (LoopExitNode) it.next().getBeginNode();
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
                        phi = graph.addWithoutUnique(new ValuePhiNode(vpn.stamp(), merge));
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
