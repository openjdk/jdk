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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class DirectedEdgeUtilsTest {

    @ParameterizedTest
    @MethodSource
    public void testGetNodes(List<DirectedEdge<String>> edges, Set<String> expectedNodes) {
        final var actualNodes = DirectedEdgeUtils.getNodes(edges);

        assertEquals(expectedNodes, actualNodes);

        assertEquals(expectedNodes, DirectedEdgeUtils.getNodes(edges, HashSet::new));
        assertEquals(expectedNodes, DirectedEdgeUtils.getNodes(edges, LinkedHashSet::new));
    }

    private static Stream<Object[]> testGetNodes() {
        return Stream.<Object[]>of(
                new Object[] { List.of(edge(A, B)), Set.of(A, B) },
                new Object[] { List.of(edge(A, B), edge(A, B)), Set.of(A, B) },
                new Object[] { List.of(edge(A, B), edge(B, A)), Set.of(A, B) },
                new Object[] { List.of(edge(A, B), edge(D, B)), Set.of(A, B, D) }
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testGetNoIncomingEdgeNodes(List<DirectedEdge<String>> edges, Set<String> expectedNodes) {
        final var actualNodes = DirectedEdgeUtils.getNoIncomingEdgeNodes(edges);

        assertEquals(expectedNodes, actualNodes);

        assertEquals(expectedNodes, DirectedEdgeUtils.getNoIncomingEdgeNodes(edges, HashSet::new));
        assertEquals(expectedNodes, DirectedEdgeUtils.getNoIncomingEdgeNodes(edges, LinkedHashSet::new));
    }

    private static Stream<Object[]> testGetNoIncomingEdgeNodes() {
        return Stream.<Object[]>of(
                new Object[] { List.of(edge(A, B)), Set.of(A) },
                new Object[] { List.of(edge(A, B), edge(A, B)), Set.of(A) },
                new Object[] { List.of(edge(A, B), edge(B, A)), Set.of() },
                new Object[] { List.of(edge(A, B), edge(D, B)), Set.of(A, D) },
                // A <- B <- D
                // |
                // + <- D <- C
                //      |
                //      + <- L <- B
                new Object[] { List.of(edge(B, A), edge(D, B), edge(D, A), edge(C, D), edge(L, D), edge(B, L)), Set.of(C) }
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testGetEdgesTo(String node, List<DirectedEdge<String>> edges, List<DirectedEdge<String>> expectedEdges) {
        final var actualEdges = DirectedEdgeUtils.getEdgesTo(node, edges);

        if (expectedEdges == SAME_LIST) {
            expectedEdges = edges;
        }

        assertEqualsSorted(expectedEdges, actualEdges);

        assertEqualsSorted(expectedEdges, DirectedEdgeUtils.getEdgesTo(node, edges, ArrayList::new));
        assertEqualsSorted(new LinkedHashSet<>(expectedEdges), DirectedEdgeUtils.getEdgesTo(node, edges, HashSet::new));
    }

    private static Stream<Object[]> testGetEdgesTo() {
        return Stream.<Object[]>of(
                new Object[] { A, List.of(edge(A, B)), List.of() },
                new Object[] { B, List.of(edge(A, B)), SAME_LIST },
                new Object[] { B, List.of(edge(A, B), edge(A, B)), SAME_LIST },
                new Object[] { B, List.of(edge(A, B), edge(B, C)), List.of(edge(A, B)) },
                new Object[] { B, List.of(edge(A, B), edge(B, C), edge(C, D)), List.of(edge(A, B)) },
                new Object[] { B, List.of(edge(A, B), edge(A, C), edge(D, B)), List.of(edge(A, B), edge(D, B)) }
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testGetEdgesFrom(String node, List<DirectedEdge<String>> edges, List<DirectedEdge<String>> expectedEdges) {
        final var actualEdges = DirectedEdgeUtils.getEdgesFrom(node, edges);

        if (expectedEdges == SAME_LIST) {
            expectedEdges = edges;
        }

        assertEqualsSorted(expectedEdges, actualEdges);

        assertEqualsSorted(expectedEdges, DirectedEdgeUtils.getEdgesFrom(node, edges, ArrayList::new));
        assertEqualsSorted(new LinkedHashSet<>(expectedEdges), DirectedEdgeUtils.getEdgesFrom(node, edges, HashSet::new));
    }

    private static Stream<Object[]> testGetEdgesFrom() {
        return Stream.<Object[]>of(
                new Object[] { A, List.of(edge(A, B)), SAME_LIST },
                new Object[] { B, List.of(edge(A, B)), List.of() },
                new Object[] { A, List.of(edge(A, B), edge(A, B)), SAME_LIST },
                new Object[] { B, List.of(edge(A, B), edge(B, C)), List.of(edge(B, C)) },
                new Object[] { B, List.of(edge(A, B), edge(B, C), edge(C, D)), List.of(edge(B, C)) },
                new Object[] { D, List.of(edge(D, B), edge(D, A), edge(C, D)), List.of(edge(D, B), edge(D, A)) }
        );
    }

    private static DirectedEdge<String> edge(String from, String to) {
        return DirectedEdge.create(from, to);
    }

    private static void assertEqualsSorted(Collection<DirectedEdge<String>> expected, Collection<DirectedEdge<String>> actual) {
        assertEquals(expected.stream().sorted(EDGE_COMPARATOR).toList(), actual.stream().sorted(EDGE_COMPARATOR).toList());
    }

    private final static List<?> SAME_LIST = new ArrayList<>();

    private final static Comparator<DirectedEdge<String>> EDGE_COMPARATOR =
            Comparator.<DirectedEdge<String>, String>comparing(DirectedEdge::from).thenComparing(DirectedEdge::to);

    private final static String A = "A";
    private final static String B = "B";
    private final static String C = "C";
    private final static String D = "D";
    private final static String L = "L";
}
