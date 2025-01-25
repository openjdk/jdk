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

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class ActionGraphTest {

    @Test
    public void testCyclicDependenciesSimple() {
        assertThrows(IllegalArgumentException.class, () -> addEdge(A, A));
    }

    @ParameterizedTest
    @MethodSource
    public void testCyclicDependencies(List<DirectedEdge<String>> edges, Set<String> expectedCycleNodes) {
        edges.forEach(this::addEdge);
        final var ex = assertThrows(CycleException.class, graph::topologicalSort);
        assertEquals(expectedCycleNodes, ex.getCycleNodes());
    }

    private static Stream<Object[]> testCyclicDependencies() {
        return Stream.<Object[]>of(
                // A <- B <- C
                // |
                // + <- C <- B
                new Object[] { List.of(edge(B, A), edge(C, B), edge(C, A), edge(B, C)), Set.of(A, B, C) },

                // A <- B <- D
                // |
                // + <- D <- C
                //      |
                //      + <- L <- B
                new Object[] { List.of(edge(B, A), edge(D, B), edge(D, A), edge(C, D), edge(L, D), edge(B, L)), Set.of(A, B, D, L) }
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testTopologicalSort(Comparator<String> sorter, List<DirectedEdge<String>> edges, List<String> expectedNodes) throws CycleException {
        edges.forEach(this::addEdge);
        final List<String> actualNodes;
        if (sorter != null) {
            actualNodes = graph.topologicalSort(sorter);
        } else {
            actualNodes = graph.topologicalSort();
        }
        assertEquals(expectedNodes, actualNodes);
    }

    private static Stream<Object[]> testTopologicalSort() {
        return Stream.<Object[]>of(
                // A <- B <- C
                // |
                // + <- D <- B <- K <- C
                //      |
                //      + <- K
                new Object[] { null, List.of(edge(B, A), edge(C, B), edge(D, A), edge(K, D), edge(B, D), edge(K, B), edge(C, K)), List.of(C, K, B, D, A) },

                // A <- B <- C <- K
                // |
                // + <- D <- B <- K
                //      |
                //      + <- K
                new Object[] { null, List.of(edge(B, A), edge(C, B), edge(D, A), edge(K, D), edge(B, D), edge(K, B), edge(K, C)), List.of(K, C, B, D, A) },

                // D <- C <- B <- A
                // |
                // + <- A
                new Object[] { null, List.of(edge(A, B), edge(B, C), edge(C, D), edge(A, D)), List.of(A, B, C, D) },

                new Object[] { Comparator.naturalOrder(), List.of(edge(A, L), edge(C, L), edge(B, L), edge(D, L)), List.of(A, B, C, D, L) },
                new Object[] { Comparator.reverseOrder(), List.of(edge(A, L), edge(C, L), edge(B, L), edge(D, L)), List.of(D, C, B, A, L) }
        );
    }

    private static DirectedEdge<String> edge(String from, String to) {
        return DirectedEdge.create(from, to);
    }

    private void addEdge(String from, String to) {
        graph.addEdge(from, to);
    }

    private void addEdge(DirectedEdge<String> edge) {
        graph.addEdge(edge.from(), edge.to());
    }

    private final ActionGraph<String> graph = new ActionGraph<>();

    private final static String A = "A";
    private final static String B = "B";
    private final static String C = "C";
    private final static String D = "D";
    private final static String K = "K";
    private final static String L = "L";
}
