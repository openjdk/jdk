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

import java.util.Arrays;
import java.util.Iterator;

import org.graalvm.compiler.graph.iterators.NodeIterable;

public final class NodeBitMap implements NodeIterable<Node> {
    private static final int SHIFT = 6;

    private long[] bits;
    private int nodeCount;
    private int counter;
    private final Graph graph;

    public NodeBitMap(Graph graph) {
        this.nodeCount = graph.nodeIdCount();
        this.bits = new long[sizeForNodeCount(nodeCount)];
        this.graph = graph;
    }

    private static int sizeForNodeCount(int nodeCount) {
        return (nodeCount + Long.SIZE - 1) >> SHIFT;
    }

    public int getCounter() {
        return counter;
    }

    private NodeBitMap(NodeBitMap other) {
        this.bits = other.bits.clone();
        this.nodeCount = other.nodeCount;
        this.graph = other.graph;
    }

    public Graph graph() {
        return graph;
    }

    public boolean isNew(Node node) {
        return node.id() >= nodeCount;
    }

    public boolean isMarked(Node node) {
        assert check(node, false);
        return isMarked(node.id());
    }

    public boolean checkAndMarkInc(Node node) {
        if (!isMarked(node)) {
            this.counter++;
            this.mark(node);
            return true;
        } else {
            return false;
        }
    }

    public boolean isMarked(int id) {
        return (bits[id >> SHIFT] & (1L << id)) != 0;
    }

    public boolean isMarkedAndGrow(Node node) {
        assert check(node, true);
        int id = node.id();
        checkGrow(id);
        return isMarked(id);
    }

    public void mark(Node node) {
        assert check(node, false);
        int id = node.id();
        bits[id >> SHIFT] |= (1L << id);
    }

    public void markAndGrow(Node node) {
        assert check(node, true);
        int id = node.id();
        checkGrow(id);
        bits[id >> SHIFT] |= (1L << id);
    }

    public void clear(Node node) {
        assert check(node, false);
        int id = node.id();
        bits[id >> SHIFT] &= ~(1L << id);
    }

    public void clearAndGrow(Node node) {
        assert check(node, true);
        int id = node.id();
        checkGrow(id);
        bits[id >> SHIFT] &= ~(1L << id);
    }

    private void checkGrow(int id) {
        if (id >= nodeCount) {
            if ((id >> SHIFT) >= bits.length) {
                grow();
            } else {
                nodeCount = id + 1;
            }
        }
    }

    public void clearAll() {
        Arrays.fill(bits, 0);
    }

    public void intersect(NodeBitMap other) {
        assert graph() == other.graph();
        int commonLength = Math.min(bits.length, other.bits.length);
        for (int i = commonLength; i < bits.length; i++) {
            bits[i] = 0;
        }
        for (int i = 0; i < commonLength; i++) {
            bits[i] &= other.bits[i];
        }
    }

    public void subtract(NodeBitMap other) {
        assert graph() == other.graph();
        int commonLength = Math.min(bits.length, other.bits.length);
        for (int i = 0; i < commonLength; i++) {
            bits[i] &= ~other.bits[i];
        }
    }

    public void union(NodeBitMap other) {
        assert graph() == other.graph();
        grow();
        if (bits.length < other.bits.length) {
            bits = Arrays.copyOf(bits, other.bits.length);
        }
        for (int i = 0; i < bits.length; i++) {
            bits[i] |= other.bits[i];
        }
    }

    public void grow() {
        nodeCount = Math.max(nodeCount, graph().nodeIdCount());
        int newLength = sizeForNodeCount(nodeCount);
        if (newLength > bits.length) {
            newLength = Math.max(newLength, (bits.length * 3 / 2) + 1);
            bits = Arrays.copyOf(bits, newLength);
        }
    }

    private boolean check(Node node, boolean grow) {
        assert node.graph() == graph() : "this node is not part of the graph: " + node;
        assert grow || !isNew(node) : "node was added to the graph after creating the node bitmap: " + node;
        assert node.isAlive() : "node is deleted!" + node;
        return true;
    }

    public <T extends Node> void markAll(Iterable<T> nodes) {
        for (Node node : nodes) {
            mark(node);
        }
    }

    private static class MarkedNodeIterator implements Iterator<Node> {

        private final NodeBitMap visited;
        private Iterator<Node> nodes;
        private Node nextNode;

        MarkedNodeIterator(NodeBitMap visited, Iterator<Node> nodes) {
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
                if (visited.isNew(nextNode)) {
                    nextNode = null;
                    return;
                }
            } while (!visited.isMarked(nextNode));
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

    @Override
    public Iterator<Node> iterator() {
        return new MarkedNodeIterator(NodeBitMap.this, graph().getNodes().iterator());
    }

    public NodeBitMap copy() {
        return new NodeBitMap(this);
    }

    @Override
    public int count() {
        int count = 0;
        for (long l : bits) {
            count += Long.bitCount(l);
        }
        return count;
    }

    @Override
    public boolean contains(Node node) {
        return isMarked(node);
    }

    @Override
    public String toString() {
        return snapshot().toString();
    }
}
