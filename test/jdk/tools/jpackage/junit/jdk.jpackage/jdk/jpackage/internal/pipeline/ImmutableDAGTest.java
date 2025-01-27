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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class ImmutableDAGTest {

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
                // |    ^
                // |    |
                // + <- D <- L
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

        final var graphBuilder = ImmutableDAG.<String>build();

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

    private static ImmutableDAG<String> create(Collection<DirectedEdge<String>> edges) {
        final var graphBuilder = ImmutableDAG.<String>build();
        edges.forEach(graphBuilder::addEdge);
        return graphBuilder.create();
    }

    private static DirectedEdge<String> edge(String from, String to) {
        return DirectedEdge.create(from, to);
    }

    private final static String A = "A";
    private final static String B = "B";
    private final static String C = "C";
    private final static String D = "D";
    private final static String L = "L";
}
