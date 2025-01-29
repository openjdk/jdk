/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.pipeline;

import static java.util.stream.Collectors.toCollection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Immutable directed acyclic graph (DAG).
 *
 * @param edgeMatrix the edge matrix. [i,j] addresses an edge, where 'i' is the
 *                   index of the head node of the edge in the node container
 *                   and 'j' is the index of the tail node of the edge in the
 *                   node container
 * @param nodes      the node container
 */
record ImmutableDAG<T>(BinaryMatrix edgeMatrix, Nodes<T> nodes) {

    static <U> Builder<U> build() {
        return new Builder<>();
    }

    final static class Builder<U> {

        Builder<U> addEdge(U tail, U head) {
            return addEdge(DirectedEdge.create(tail, head));
        }

        Builder<U> addEdge(DirectedEdge<U> edge) {

            final var newTailNode = !nodes.contains(edge.tail());
            final var newHeadNode = !nodes.contains(edge.head());

            if (newHeadNode && !canAddEdgeToUnknownNode) {
                throw new UnsupportedOperationException("Can not add edge to unknown node");
            }

            if (newTailNode) {
                nodes.add(edge.tail());
            }

            if (newHeadNode) {
                nodes.add(edge.head());
            }

            edges.add(edge);
            return this;
        }

        Builder<U> canAddEdgeToUnknownNode(boolean v) {
            canAddEdgeToUnknownNode = v;
            return this;
        }

        ImmutableDAG<U> create() {
            final var nodesAdapter = Nodes.ofList(this.nodes);
            final var edgeMatrix = new BinaryMatrix(nodes.size());
            for (final var edge : edges) {
                final int row = nodes.indexOf(edge.tail());
                final int column = nodes.indexOf(edge.head());
                edgeMatrix.set(row, column);
            }

            if (isCyclic(edgeMatrix, null)) {
                throw new UnsupportedOperationException("Cyclic edges not allowed");
            }

            return new ImmutableDAG<>(edgeMatrix, nodesAdapter);
        }

        private boolean canAddEdgeToUnknownNode = true;
        private final List<U> nodes = new ArrayList<>();
        private final List<DirectedEdge<U>> edges = new ArrayList<>();
    }

    interface Nodes<U> extends Iterable<U> {
        int size();
        int indexOf(U node);
        U get(int index);


        static <V> Nodes<V> ofList(List<V> list) {
            return new Nodes<>() {

                @Override
                public int size() {
                    return list.size();
                }

                @Override
                public int indexOf(V node) {
                    final int index = list.indexOf(node);
                    if (index < 0) {
                        throw new NoSuchElementException();
                    }
                    return index;
                }

                @Override
                public V get(int index) {
                    return list.get(index);
                }

                @Override
                public Iterator<V> iterator() {
                    return list.iterator();
                }

            };
        }
    }

    ImmutableDAG {
        Objects.requireNonNull(nodes);

        Objects.requireNonNull(edgeMatrix);
        if (!edgeMatrix.isSquare()) {
            throw new IllegalArgumentException("Matrix must be square");
        }

        if (edgeMatrix.getColumnCount() != nodes.size()) {
            throw new IllegalArgumentException("Matrix must have number of columns equal to the number of nodes");
        }
    }

    /**
     * Returns topologically ordered nodes of this graph.
     * <p>
     * For every directed edge ("tail", "head") from "tail" to "head", "tail" comes before "head".
     *
     * @return topologically ordered nodes of this graph
     */
    List<T> topologicalSort() {
        final List<T> result = new ArrayList<>();
        isCyclic(edgeMatrix, index -> {
            result.add(nodes.get(index));
        });
        return result;
    }

    /**
     * Gets the list of nodes that are heads of the edges sharing the same tail,
     * which is the given node.
     * <p>
     * The returned list is ordered by the indexes of the nodes in the node
     * container of this graph.
     *
     * @param node a node
     * @return the list of nodes that are heads of the edges sharing the same tail,
     *         which is the given node
     *
     * @see Nodes
     */
    List<T> getHeadsOf(T node) {
        final int tail = nodes.indexOf(node);
        return getOutgoingEdges(tail, edgeMatrix).map(BinaryMatrix.Cursor::column).map(nodes::get).toList();
    }

    /**
     * Gets the list of nodes that are tails of the edges sharing the same head,
     * which is the given node.
     * <p>
     * The returned list is ordered by the indexes of the nodes in the node
     * container of this graph.
     *
     * @param node a node
     * @return the list of nodes that are tails of the edges sharing the same head,
     *         which is the given node
     *
     * @see Nodes
     */
    List<T> getTailsOf(T node) {
        final int head = nodes.indexOf(node);
        return getIncomingEdges(head, edgeMatrix).map(BinaryMatrix.Cursor::row).map(nodes::get).toList();
    }

    /**
     * Get the list of nodes without incoming edges.
     * <p>
     * A node without incoming edges is a node that is not a head of any of the edges in the graph.
     * <p>
     * The returned list is ordered by the indexes of the nodes in the node
     * container of this graph.
     *
     * @return the list of nodes without incoming edges
     */
    List<T> getNoIncomingEdges() {
        return getNoIncomingEdges(edgeMatrix).mapToObj(nodes::get).toList();
    }

    /**
     * Get the list of nodes without outgoing edges.
     * <p>
     * A node without outgoing edges is a node that is not a tail of any of the edges in the graph.
     * <p>
     * The returned list is ordered by the indexes of the nodes in the node
     * container of this graph.
     *
     * @return the list of nodes without outgoing edges
     */
    List<T> getNoOutgoingEdges() {
        return getNoOutgoingEdges(edgeMatrix).mapToObj(nodes::get).toList();
    }

    private static Stream<BinaryMatrix.Cursor> getOutgoingEdges(int node, BinaryMatrix edgeMatrix) {
        return edgeMatrix.getRowAsStream(node).filter(BinaryMatrix.Cursor::value);
    }

    private static Stream<BinaryMatrix.Cursor> getIncomingEdges(int node, BinaryMatrix edgeMatrix) {
        return edgeMatrix.getColumnAsStream(node).filter(BinaryMatrix.Cursor::value);
    }

    private static IntStream getNoIncomingEdges(BinaryMatrix edgeMatrix) {
        return IntStream.range(0, edgeMatrix.getColumnCount()).filter(column -> {
            return getIncomingEdges(column, edgeMatrix).findAny().isEmpty();
        });
    }

    private static IntStream getNoOutgoingEdges(BinaryMatrix edgeMatrix) {
        return IntStream.range(0, edgeMatrix.getRowCount()).filter(row -> {
            return getOutgoingEdges(row, edgeMatrix).findAny().isEmpty();
        });
    }

    private static boolean isCyclic(BinaryMatrix edgeMatrix, Consumer<Integer> topologicalOrderAccumulator) {

        final var edgeMatrixCopy = new BinaryMatrix(edgeMatrix);

        // Use Kahn's algorithm from https://en.wikipedia.org/wiki/Topological_sorting#Kahn's_algorithm to find cyclic edges
        // Variable names picked from the algorithm pseudo-code.

        // Nodes with no incoming edges.
        List<Integer> S = getNoIncomingEdges(edgeMatrix).mapToObj(Integer::valueOf).collect(toCollection(ArrayList::new));

        for (var i = 0; i != S.size(); ++i) {
            final var n = S.get(i);

            if (topologicalOrderAccumulator != null) {
                topologicalOrderAccumulator.accept(n);
            }

            for (final var e : getOutgoingEdges(n, edgeMatrixCopy).toList()) {
                e.value(false); // remove the edge

                final var m = e.column();

                if (getIncomingEdges(m, edgeMatrixCopy).findAny().isEmpty()) {
                    // No incoming edges to 'm' node.
                    if (topologicalOrderAccumulator != null) {
                        if (i > 0) {
                            S = S.subList(i, S.size());
                            i = 0;
                        }

                        final var insertAtIndex = Math.abs((Collections.binarySearch(S, m) + 1));
                        if (insertAtIndex == 0) {
                            S.set(0, m);
                            i = -1;
                        } else {
                            S.add(insertAtIndex, m);
                        }
                    } else {
                        S.add(m);
                    }
                }
            }
        }

        return !edgeMatrixCopy.isEmpty();
    }
}
