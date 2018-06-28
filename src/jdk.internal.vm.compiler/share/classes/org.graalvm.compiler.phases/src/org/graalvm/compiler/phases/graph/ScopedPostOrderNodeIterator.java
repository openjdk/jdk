/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.phases.graph;

import java.util.ArrayDeque;
import java.util.Deque;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StructuredGraph;

public abstract class ScopedPostOrderNodeIterator {

    private final Deque<FixedNode> nodeQueue;
    private final NodeBitMap queuedNodes;
    private final Deque<FixedNode> scopes;

    protected FixedNode currentScopeStart;

    public ScopedPostOrderNodeIterator(StructuredGraph graph) {
        this.queuedNodes = graph.createNodeBitMap();
        this.nodeQueue = new ArrayDeque<>();
        this.scopes = getScopes(graph);
    }

    public void apply() {
        while (!scopes.isEmpty()) {
            queuedNodes.clearAll();
            this.currentScopeStart = scopes.pop();
            initializeScope();
            processScope();
        }
    }

    public void processScope() {
        FixedNode current;
        queue(currentScopeStart);

        while ((current = nextQueuedNode()) != null) {
            assert current.isAlive();

            if (current instanceof Invoke) {
                invoke((Invoke) current);
                queueSuccessors(current);
            } else if (current instanceof LoopBeginNode) {
                queueLoopBeginSuccessors((LoopBeginNode) current);
            } else if (current instanceof LoopExitNode) {
                queueLoopExitSuccessors((LoopExitNode) current);
            } else if (current instanceof LoopEndNode) {
                // nothing todo
            } else if (current instanceof AbstractMergeNode) {
                queueSuccessors(current);
            } else if (current instanceof FixedWithNextNode) {
                queueSuccessors(current);
            } else if (current instanceof EndNode) {
                queueMerge((EndNode) current);
            } else if (current instanceof ControlSinkNode) {
                // nothing todo
            } else if (current instanceof ControlSplitNode) {
                queueSuccessors(current);
            } else {
                assert false : current;
            }
        }
    }

    protected void queueLoopBeginSuccessors(LoopBeginNode node) {
        if (currentScopeStart == node) {
            queue(node.next());
        } else if (currentScopeStart instanceof LoopBeginNode) {
            // so we are currently processing loop A and found another loop B
            // -> queue all loop exits of B except those that also exit loop A
            for (LoopExitNode loopExit : node.loopExits()) {
                if (!((LoopBeginNode) currentScopeStart).loopExits().contains(loopExit)) {
                    queue(loopExit);
                }
            }
        } else {
            queue(node.loopExits());
        }
    }

    protected void queueLoopExitSuccessors(LoopExitNode node) {
        if (!(currentScopeStart instanceof LoopBeginNode) || !((LoopBeginNode) currentScopeStart).loopExits().contains(node)) {
            queueSuccessors(node);
        }
    }

    protected Deque<FixedNode> getScopes(StructuredGraph graph) {
        Deque<FixedNode> result = new ArrayDeque<>();
        result.push(graph.start());
        for (LoopBeginNode loopBegin : graph.getNodes(LoopBeginNode.TYPE)) {
            result.push(loopBegin);
        }
        return result;
    }

    private void queueSuccessors(FixedNode x) {
        queue(x.successors());
    }

    private void queue(NodeIterable<? extends Node> iter) {
        for (Node node : iter) {
            queue(node);
        }
    }

    private void queue(Node node) {
        if (node != null && !queuedNodes.isMarked(node)) {
            queuedNodes.mark(node);
            nodeQueue.addFirst((FixedNode) node);
        }
    }

    private FixedNode nextQueuedNode() {
        if (nodeQueue.isEmpty()) {
            return null;
        }

        FixedNode result = nodeQueue.removeFirst();
        assert queuedNodes.isMarked(result);
        return result;
    }

    private void queueMerge(AbstractEndNode end) {
        AbstractMergeNode merge = end.merge();
        if (!queuedNodes.isMarked(merge) && visitedAllEnds(merge)) {
            queue(merge);
        }
    }

    private boolean visitedAllEnds(AbstractMergeNode merge) {
        for (int i = 0; i < merge.forwardEndCount(); i++) {
            if (!queuedNodes.isMarked(merge.forwardEndAt(i))) {
                return false;
            }
        }
        return true;
    }

    protected abstract void initializeScope();

    protected abstract void invoke(Invoke invoke);
}
