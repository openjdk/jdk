/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.graph;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

public abstract class NodeWorkList implements Iterable<Node> {

    protected final Queue<Node> worklist;

    private NodeWorkList(Graph graph, boolean fill) {
        if (fill) {
            ArrayDeque<Node> deque = new ArrayDeque<>(graph.getNodeCount());
            for (Node node : graph.getNodes()) {
                deque.add(node);
            }
            worklist = deque;
        } else {
            worklist = new ArrayDeque<>();
        }
    }

    public void addAll(Iterable<? extends Node> nodes) {
        for (Node node : nodes) {
            if (node.isAlive()) {
                this.add(node);
            }
        }
    }

    public abstract void add(Node node);

    public abstract boolean contains(Node node);

    private abstract class QueueConsumingIterator implements Iterator<Node> {

        protected void dropDeleted() {
            while (!worklist.isEmpty() && worklist.peek().isDeleted()) {
                worklist.remove();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public static final class IterativeNodeWorkList extends NodeWorkList {

        private static final int EXPLICIT_BITMAP_THRESHOLD = 10;
        protected NodeBitMap inQueue;

        private int iterationLimit = Integer.MAX_VALUE;
        private Node firstNoChange;
        private Node lastPull;
        private Node lastChain;

        public IterativeNodeWorkList(Graph graph, boolean fill, int iterationLimitPerNode) {
            super(graph, fill);
            if (iterationLimitPerNode > 0) {
                iterationLimit = iterationLimitPerNode * graph.getNodeCount();
            }
        }

        @Override
        public Iterator<Node> iterator() {
            return new QueueConsumingIterator() {
                @Override
                public boolean hasNext() {
                    dropDeleted();
                    return iterationLimit > 0 && !worklist.isEmpty();
                }

                @Override
                public Node next() {
                    if (iterationLimit-- <= 0) {
                        throw new NoSuchElementException();
                    }
                    dropDeleted();
                    Node node = worklist.remove();
                    assert updateInfiniteWork(node);
                    if (inQueue != null) {
                        inQueue.clearAndGrow(node);
                    }
                    return node;
                }

                private boolean updateInfiniteWork(Node node) {
                    if (lastPull != lastChain) {
                        firstNoChange = null;
                    }
                    lastPull = node;
                    return true;
                }
            };
        }

        @Override
        public void add(Node node) {
            if (node != null) {
                if (inQueue == null && worklist.size() > EXPLICIT_BITMAP_THRESHOLD) {
                    inflateToBitMap(node.graph());
                }

                if (inQueue != null) {
                    if (inQueue.isMarkedAndGrow(node)) {
                        return;
                    }
                } else {
                    for (Node queuedNode : worklist) {
                        if (queuedNode == node) {
                            return;
                        }
                    }
                }
                assert checkInfiniteWork(node) : "Readded " + node;
                if (inQueue != null) {
                    inQueue.markAndGrow(node);
                }
                worklist.add(node);
            }
        }

        @Override
        public boolean contains(Node node) {
            if (inQueue != null) {
                return inQueue.isMarked(node);
            } else {
                for (Node queuedNode : worklist) {
                    if (queuedNode == node) {
                        return true;
                    }
                }
                return false;
            }
        }

        private boolean checkInfiniteWork(Node node) {
            if (lastPull == node && !node.hasNoUsages()) {
                if (firstNoChange == null) {
                    firstNoChange = node;
                    lastChain = node;
                } else if (node == firstNoChange) {
                    return false;
                } else {
                    lastChain = node;
                }
            } else {
                firstNoChange = null;
            }
            return true;
        }

        private void inflateToBitMap(Graph graph) {
            assert inQueue == null;
            inQueue = graph.createNodeBitMap();
            for (Node queuedNode : worklist) {
                if (queuedNode.isAlive()) {
                    inQueue.mark(queuedNode);
                }
            }
        }
    }

    public static final class SingletonNodeWorkList extends NodeWorkList {
        protected final NodeBitMap visited;

        public SingletonNodeWorkList(Graph graph) {
            super(graph, false);
            visited = graph.createNodeBitMap();
        }

        @Override
        public void add(Node node) {
            if (node != null) {
                if (!visited.isMarkedAndGrow(node)) {
                    visited.mark(node);
                    worklist.add(node);
                }
            }
        }

        @Override
        public boolean contains(Node node) {
            return visited.isMarked(node);
        }

        @Override
        public Iterator<Node> iterator() {
            return new QueueConsumingIterator() {
                @Override
                public boolean hasNext() {
                    dropDeleted();
                    return !worklist.isEmpty();
                }

                @Override
                public Node next() {
                    dropDeleted();
                    return worklist.remove();
                }
            };
        }
    }
}
