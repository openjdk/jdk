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
import java.util.Queue;

public final class NodeFlood implements Iterable<Node> {

    private final NodeBitMap visited;
    private final Queue<Node> worklist;
    private int totalMarkedCount;

    public NodeFlood(Graph graph) {
        visited = graph.createNodeBitMap();
        worklist = new ArrayDeque<>();
    }

    public void add(Node node) {
        if (node != null && !visited.isMarked(node)) {
            visited.mark(node);
            worklist.add(node);
            totalMarkedCount++;
        }
    }

    public int getTotalMarkedCount() {
        return totalMarkedCount;
    }

    public void addAll(Iterable<? extends Node> nodes) {
        for (Node node : nodes) {
            this.add(node);
        }
    }

    public NodeBitMap getVisited() {
        return visited;
    }

    public boolean isMarked(Node node) {
        return visited.isMarked(node);
    }

    public boolean isNew(Node node) {
        return visited.isNew(node);
    }

    private static class QueueConsumingIterator implements Iterator<Node> {

        private final Queue<Node> queue;

        QueueConsumingIterator(Queue<Node> queue) {
            this.queue = queue;
        }

        @Override
        public boolean hasNext() {
            return !queue.isEmpty();
        }

        @Override
        public Node next() {
            return queue.remove();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Iterator<Node> iterator() {
        return new QueueConsumingIterator(worklist);
    }

    private static class UnmarkedNodeIterator implements Iterator<Node> {

        private final NodeBitMap visited;
        private Iterator<Node> nodes;
        private Node nextNode;

        UnmarkedNodeIterator(NodeBitMap visited, Iterator<Node> nodes) {
            this.visited = visited;
            this.nodes = nodes;
            forward();
        }

        private void forward() {
            do {
                if (!nodes.hasNext()) {
                    nextNode = null;
                    return;
                }
                nextNode = nodes.next();
            } while (visited.isMarked(nextNode));
        }

        @Override
        public boolean hasNext() {
            return nextNode != null;
        }

        @Override
        public Node next() {
            try {
                return nextNode;
            } finally {
                forward();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public Iterable<Node> unmarkedNodes() {
        return new Iterable<Node>() {

            @Override
            public Iterator<Node> iterator() {
                return new UnmarkedNodeIterator(visited, visited.graph().getNodes().iterator());
            }
        };
    }
}
