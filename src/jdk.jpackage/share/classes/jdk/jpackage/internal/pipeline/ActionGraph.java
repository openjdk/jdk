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

import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A directed acyclic (supposedly) graph.
 *
 * @see https://en.wikipedia.org/wiki/Directed_acyclic_graph
 */
final class ActionGraph<T> {

    void addEdge(T from, T to) {
        edges.add(DirectedEdge.create(from, to));
    }

    // TBD: Implement transient reduction

    Set<T> getNodeDependencies(T node) {
        return DirectedEdgeUtils.getEdgesTo(node, edges).stream().map(DirectedEdge::from).collect(toSet());
    }

    List<T> topologicalSort() throws CycleException {
        return topologicalSort(Optional.empty());
    }

    List<T> topologicalSort(Comparator<T> sorter) throws CycleException {
        return topologicalSort(Optional.of(sorter));
    }

    private List<T> topologicalSort(Optional<Comparator<T>> sorter) throws CycleException {
        final Set<DirectedEdge<T>> edgesCopy = new HashSet<>(edges);

        // Kahn's algorithm from https://en.wikipedia.org/wiki/Topological_sorting#Kahn's_algorithm
        // Variable names picked from the algorithm pseudo-code.

        // Empty list that will contain the sorted elements.
        final List<T> L = new ArrayList<>();

        if (sorter.isEmpty()) {
            // Set of all nodes with no incoming edge.
            var S = DirectedEdgeUtils.getNoIncomingEdgeNodes(edgesCopy, HashSet::new);
            do {
                final var newS = new HashSet<T>();
                for (final var n : S) {
                    kahlSortIteration(edgesCopy, n, newS, L);
                }
                S = newS;
            } while(!S.isEmpty());
        } else {
            // Set of all nodes with no incoming edge.
            final var S = DirectedEdgeUtils.getNoIncomingEdgeNodes(edgesCopy, () -> new TreeSet<>(sorter.orElseThrow()));
            while (!S.isEmpty()) {
                final var n = S.removeFirst();
                kahlSortIteration(edgesCopy, n, S, L);
            }
        }

        if (!edgesCopy.isEmpty()) {
            // Graph has at least one cycle.
            throw new CycleException(DirectedEdgeUtils.getNodes(edgesCopy));
        } else {
            // A topologically sorted order.
            return Collections.unmodifiableList(L);
        }
    }

    private static <U> void kahlSortIteration(Set<DirectedEdge<U>> edges, U n, Set<U> S, List<U> L) {
        L.add(n);
        for (final var e : DirectedEdgeUtils.getEdgesFrom(n, edges)) {
            edges.remove(e);
            final var m = e.to();
            if (DirectedEdgeUtils.getEdgesTo(m, edges).isEmpty()) {
                S.add(m);
            }
        }
    }

    final Set<DirectedEdge<T>> edges = new HashSet<>();
}
