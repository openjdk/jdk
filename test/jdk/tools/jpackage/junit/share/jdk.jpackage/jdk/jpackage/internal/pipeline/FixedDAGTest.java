/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class FixedDAGTest {

    @Test
    public void testInvalidCtorArgs() {
        final var matrix1x2 = new BinaryMatrix(1, 2);
        final var nodes = FixedDAG.Nodes.<String>ofList(List.of(A, B));

        assertThrows(IllegalArgumentException.class, () -> new FixedDAG<>(matrix1x2, nodes));

        final var matrix3x3 = new BinaryMatrix(3, 3);
        assertThrows(IllegalArgumentException.class, () -> new FixedDAG<>(matrix3x3, nodes));
    }

    @Test
    public void testNodesToList() {
        final var nodes = FixedDAG.Nodes.<String>ofList(List.of(A, B));

        assertEquals(2, nodes.size());

        assertEquals(A, nodes.get(0));
        assertEquals(B, nodes.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> nodes.get(2));

        assertEquals(0, nodes.indexOf(A));
        assertEquals(1, nodes.indexOf(B));
        assertThrows(NoSuchElementException.class, () -> nodes.indexOf(C));

        final var copy = new ArrayList<String>();
        for (var n : nodes) {
            copy.add(n);
        }
        assertEquals(copy, List.of(A, B));
    }

    @ParameterizedTest
    @MethodSource
    public void testCyclic(List<DirectedEdge<String>> edges) {
        assertThrows(UnsupportedOperationException.class, () -> create(edges));
    }

    private static Stream<List<DirectedEdge<String>>> testCyclic() {
        return Stream.of(
                List.of(edge(A, B), edge(B, A)),

                List.of(edge(A, B), edge(B, C), edge(C, D), edge(D, A)),

                List.of(edge(A, B), edge(B, C), edge(C, D), edge(D, B)),

                // A <- B -> L
                // |    ^    |
                // |    |    |
                // + <- D <- +
                //      |
                //      + <- C
                List.of(edge(B, A), edge(D, B), edge(D, A), edge(C, D), edge(L, D), edge(B, L)),
                List.of(edge(C, D), edge(B, A), edge(D, B), edge(D, A), edge(L, D), edge(B, L))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testGetNoIncomingEdges(List<DirectedEdge<String>> edges, List<String> expectedNodes) {
        final var actualNodes = create(edges).getNoIncomingEdges();
        assertEquals(expectedNodes, actualNodes);
    }

    private static Stream<Object[]> testGetNoIncomingEdges() {
        return Stream.<Object[]>of(
                new Object[] { List.of(edge(A, B)), List.of(A) },
                new Object[] { List.of(edge(A, B), edge(A, B)), List.of(A) },
                new Object[] { List.of(edge(A, B), edge(D, B)), List.of(A, D) },
                new Object[] { List.of(edge(D, B), edge(A, B)), List.of(D, A) },

                new Object[] { List.of(edge(A, B), edge(C, D)), List.of(A, C) },

                // A <- B
                // ^    ^
                // |    |
                // + -- D <- L
                //      |
                //      + <- C
                new Object[] { List.of(edge(B, A), edge(D, B), edge(D, A), edge(C, D), edge(L, D)), List.of(C, L) },
                new Object[] { List.of(edge(B, A), edge(L, D), edge(D, B), edge(D, A), edge(C, D)), List.of(L, C) }
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testGetNoOutgoingEdges(List<DirectedEdge<String>> edges, List<String> expectedNodes) {
        final var actualNodes = create(edges).getNoOutgoingEdges();
        assertEquals(expectedNodes, actualNodes);
    }

    private static Stream<Object[]> testGetNoOutgoingEdges() {
        return Stream.<Object[]>of(
                new Object[] { List.of(edge(A, B)), List.of(B) },
                new Object[] { List.of(edge(A, B), edge(C, D)), List.of(B, D) }
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testGetTailsOf(String node, List<DirectedEdge<String>> edges, List<String> expectedNodes) {
        final var actualNodes = create(edges).getTailsOf(node);
        assertEquals(actualNodes, expectedNodes);

    }

    private static Stream<Object[]> testGetTailsOf() {
        return Stream.<Object[]>of(
                new Object[] { A, List.of(edge(A, B)), List.of() },
                new Object[] { B, List.of(edge(A, B)), List.of(A) },
                new Object[] { B, List.of(edge(A, B), edge(A, B)), List.of(A) },
                new Object[] { B, List.of(edge(A, B), edge(B, C)), List.of(A) },
                new Object[] { B, List.of(edge(A, B), edge(B, C), edge(C, D)), List.of(A) },
                new Object[] { B, List.of(edge(A, B), edge(A, C), edge(D, B)), List.of(A, D) },
                new Object[] { B, List.of(edge(D, B), edge(A, B), edge(A, C)), List.of(D, A) }
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testGetHeadsOf(String node, List<DirectedEdge<String>> edges, List<String> expectedNodes) {
        final var actualNodes = create(edges).getHeadsOf(node);
        assertEquals(actualNodes, expectedNodes);
    }

    private static Stream<Object[]> testGetHeadsOf() {
        return Stream.<Object[]>of(
                new Object[] { A, List.of(edge(A, B)), List.of(B) },
                new Object[] { B, List.of(edge(A, B)), List.of() },
                new Object[] { A, List.of(edge(A, B), edge(A, B)), List.of(B) },
                new Object[] { B, List.of(edge(A, B), edge(B, C)), List.of(C) },
                new Object[] { B, List.of(edge(A, B), edge(B, C), edge(C, D)), List.of(C) },
                new Object[] { D, List.of(edge(D, B), edge(D, A), edge(C, D)), List.of(B, A) },
                new Object[] { D, List.of(edge(D, A), edge(D, B), edge(C, D)), List.of(A, B) }
        );
    }

    @Test
    public void testSome() {
        // A <- B <--- D
        // ^    ^      |
        // |    |      |
        // +--- C -> L |
        // |    ^      |
        // |    |      |
        // +----+------+

        final var graphBuilder = FixedDAG.<String>build();

        graphBuilder.addEdge(edge(C, L));
        graphBuilder.addEdge(edge(D, B));
        graphBuilder.addEdge(edge(B, A));
        graphBuilder.addEdge(edge(D, A));
        graphBuilder.addEdge(edge(C, A));
        graphBuilder.addEdge(edge(D, C));
        graphBuilder.addEdge(edge(C, B));

        final var graph = graphBuilder.create();

        assertEquals(graph.getNoIncomingEdges(), List.of(D));
        assertEquals(graph.getNoOutgoingEdges(), List.of(L, A));

        assertEquals(graph.getHeadsOf(A), List.of());
        assertEquals(graph.getTailsOf(A), List.of(C, D, B));

        assertEquals(graph.getHeadsOf(B), List.of(A));
        assertEquals(graph.getTailsOf(B), List.of(C, D));

        assertEquals(graph.getHeadsOf(C), List.of(L, B, A));
        assertEquals(graph.getTailsOf(C), List.of(D));

        assertEquals(graph.getHeadsOf(D), List.of(C, B, A));
        assertEquals(graph.getTailsOf(D), List.of());

        assertEquals(graph.getHeadsOf(L), List.of());
        assertEquals(graph.getTailsOf(L), List.of(C));
    }

    @Test
    public void testSome2() {
        // B -> A <- C
        // ^         ^
        // |         |
        // +--- D ---+
        //      ^
        //      |
        // K -> N <- M <- P
        // ^         ^
        // |    O    |
        // |    ^    |
        // |    |    |
        // +--- L ---+
        //

        final var graphBuilder = FixedDAG.<String>build();

        graphBuilder.addEdge(edge(B, A));
        graphBuilder.addEdge(edge(C, A));
        graphBuilder.addEdge(edge(D, B));
        graphBuilder.addEdge(edge(D, C));
        graphBuilder.addEdge(edge(N, D));
        graphBuilder.addEdge(edge(M, N));
        graphBuilder.addEdge(edge(K, N));
        graphBuilder.addEdge(edge(L, K));
        graphBuilder.addEdge(edge(L, M));
        graphBuilder.addEdge(edge(P, M));
        graphBuilder.addEdge(edge(L, O));

        final var graph = graphBuilder.create();

        assertEquals(graph.getNoIncomingEdges(), List.of(L, P));
        assertEquals(graph.getNoOutgoingEdges(), List.of(A, O));

        assertEquals(graph.getHeadsOf(A), List.of());
        assertEquals(graph.getTailsOf(A), List.of(B, C));
        assertEquals(graph.getAllHeadsOf(A), List.of());
        assertEquals(graph.getAllTailsOf(A), List.of(B, C, D, N, M, K, L, P));

        assertEquals(graph.getHeadsOf(B), List.of(A));
        assertEquals(graph.getTailsOf(B), List.of(D));
        assertEquals(graph.getAllHeadsOf(B), List.of(A));
        assertEquals(graph.getAllTailsOf(B), List.of(D, N, M, K, L, P));

        assertEquals(graph.getHeadsOf(C), List.of(A));
        assertEquals(graph.getTailsOf(C), List.of(D));
        assertEquals(graph.getAllHeadsOf(C), List.of(A));
        assertEquals(graph.getAllTailsOf(C), List.of(D, N, M, K, L, P));

        assertEquals(graph.getHeadsOf(D), List.of(B, C));
        assertEquals(graph.getTailsOf(D), List.of(N));
        assertEquals(graph.getAllHeadsOf(D), List.of(B, A, C));
        assertEquals(graph.getAllTailsOf(D), List.of(N, M, K, L, P));

        assertEquals(graph.getHeadsOf(K), List.of(N));
        assertEquals(graph.getTailsOf(K), List.of(L));
        assertEquals(graph.getAllHeadsOf(K), List.of(B, A, C, D, N));
        assertEquals(graph.getAllTailsOf(K), List.of(L));

        assertEquals(graph.getHeadsOf(L), List.of(M, K, O));
        assertEquals(graph.getTailsOf(L), List.of());
        assertEquals(graph.getAllHeadsOf(L), List.of(B, A, C, D, N, M, K, O));
        assertEquals(graph.getAllTailsOf(L), List.of());

        assertEquals(graph.getHeadsOf(M), List.of(N));
        assertEquals(graph.getTailsOf(M), List.of(L, P));
        assertEquals(graph.getAllHeadsOf(M), List.of(B, A, C, D, N));
        assertEquals(graph.getAllTailsOf(M), List.of(L, P));

        assertEquals(graph.getHeadsOf(N), List.of(D));
        assertEquals(graph.getTailsOf(N), List.of(M, K));
        assertEquals(graph.getAllHeadsOf(N), List.of(B, A, C, D));
        assertEquals(graph.getAllTailsOf(N), List.of(M, K, L, P));

        assertEquals(graph.getHeadsOf(O), List.of());
        assertEquals(graph.getTailsOf(O), List.of(L));
        assertEquals(graph.getAllHeadsOf(O), List.of());
        assertEquals(graph.getAllTailsOf(O), List.of(L));

        assertEquals(graph.getHeadsOf(P), List.of(M));
        assertEquals(graph.getTailsOf(P), List.of());
        assertEquals(graph.getAllHeadsOf(P), List.of(B, A, C, D, N, M));
        assertEquals(graph.getAllTailsOf(P), List.of());
    }

    @ParameterizedTest
    @MethodSource
    public void testTopologicalSort(List<DirectedEdge<Integer>> edges, int[] expectedNodes) {

        final var nodes = edges.stream().map(edge -> {
            return Stream.of(edge.tail(), edge.head());
        }).flatMap(x -> x).sorted().distinct().toList();

        assertArrayEquals(expectedNodes, FixedDAG.create(edges, nodes).topologicalSort().stream().mapToInt(x -> x).toArray());
    }

    private static List<Object[]> testTopologicalSort() {
        return List.<Object[]>of(
                new Object[] { List.of(
                        edge(11, 15),
                        edge(12, 16),
                        edge(13, 17),
                        edge(14, 15),
                        edge(14, 16),
                        edge(14, 17),
                        edge(14, 18)), IntStream.rangeClosed(11, 18).toArray()
                },

                new Object[] { List.of(
                        edge(5, 2),
                        edge(5, 0),
                        edge(4, 0),
                        edge(2, 3),
                        edge(3, 1)), new int[] {4, 5, 0, 2, 3, 1}
                },

                new Object[] { List.of(
                        edge(0, 1),
                        edge(0, 4),
                        edge(0, 7),
                        edge(0, 14),
                        edge(1, 5),
                        edge(2, 3),
                        edge(2, 5),
                        edge(2, 6),
                        edge(3, 10),
                        edge(4, 5),
                        edge(4, 8),
                        edge(5, 9),
                        edge(6, 10),
                        edge(7, 8),
                        edge(7, 11),
                        edge(10, 13),
                        edge(11, 12),
                        edge(13, 16),
                        edge(14, 15)), IntStream.rangeClosed(0, 16).toArray()
                },

                new Object[] { List.of(
                        edge(0, 2),
                        edge(0, 4),
                        edge(1, 4),
                        edge(1, 5),
                        edge(2, 5),
                        edge(3, 2)), new int[] {0, 1, 3, 2, 4, 5}
                },

                new Object[] { List.of(
                        edge(0, 1),
                        edge(0, 2),
                        edge(1, 3),
                        edge(2, 3),
                        edge(3, 4)), IntStream.rangeClosed(0, 4).toArray()
                },

                new Object[] { List.of(
                        edge(1, 2),
                        edge(1, 3),
                        edge(2, 4),
                        edge(3, 4),
                        edge(4, 5)), IntStream.rangeClosed(1, 5).toArray()
                }
        );
    }

    @Test
    public void testEmptyBuilder() {
        assertThrows(IllegalArgumentException.class, FixedDAG.<String>build()::create);
    }

    @Test
    public void testSingleNodeBuilder() {
        final var graphBuilder = FixedDAG.<String>build();
        graphBuilder.addNode(A);
        assertNodesEquals(graphBuilder.create().nodes(), A);
    }

    @Test
    public void testIsolatedNodesBuilder() {
        final var graphBuilder = FixedDAG.<String>build();
        graphBuilder.addNode(A);
        graphBuilder.addNode(B);
        assertNodesEquals(graphBuilder.create().nodes(), A, B);
        assertEquals(graphBuilder.create().getNoOutgoingEdges(), List.of(A, B));
        assertEquals(graphBuilder.create().getNoIncomingEdges(), List.of(A, B));

        graphBuilder.addEdge(edge(A, C));
        assertNodesEquals(graphBuilder.create().nodes(), A, B, C);
        assertEquals(graphBuilder.create().getNoOutgoingEdges(), List.of(B, C));
        assertEquals(graphBuilder.create().getNoIncomingEdges(), List.of(A, B));
    }

    private static void assertNodesEquals(FixedDAG.Nodes<String> actual, String... expected) {
        assertEquals(List.of(expected), StreamSupport.stream(actual.spliterator(), false).toList());
    }

    private static FixedDAG<String> create(Collection<DirectedEdge<String>> edges) {
        final var graphBuilder = FixedDAG.<String>build();
        edges.forEach(graphBuilder::addEdge);
        return graphBuilder.create();
    }

    private static <T> DirectedEdge<T> edge(T tail, T head) {
        return DirectedEdge.create(tail, head);
    }

    private static final String A = "A";
    private static final String B = "B";
    private static final String C = "C";
    private static final String D = "D";
    private static final String K = "K";
    private static final String L = "L";
    private static final String M = "M";
    private static final String N = "N";
    private static final String O = "O";
    private static final String P = "P";
}
