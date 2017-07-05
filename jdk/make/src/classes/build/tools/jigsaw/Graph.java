/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.jigsaw;

import java.io.PrintStream;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

public class Graph<T> {
    private static boolean traceOn = Boolean.getBoolean("build.tools.module.trace");
    private final Set<T> nodes;
    private final Map<T, Set<T>> edges;
    private Graph(Set<T> nodes, Map<T, Set<T>> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    public Set<T> nodes() {
        return nodes;
    }

    public Map<T, Set<T>> edges() {
        return edges;
    }

    public Set<T> adjacentNodes(T u) {
        return edges.get(u);
    }

    /**
     * Returns a new Graph after transitive reduction
     */
    public Graph<T> reduce() {
        Graph.Builder<T> builder = new Builder<>();
        nodes.stream()
             .forEach(u -> {
                 builder.addNode(u);
                 edges.get(u).stream()
                         .filter(v -> !pathExists(u, v, false))
                         .forEach(v -> builder.addEdge(u, v));
             });
        return builder.build();
    }

    /**
     * Returns a new Graph after transitive reduction.  All edges in
     * the given g takes precedence over this graph.
     *
     * @throw IllegalArgumentException g must be a subgraph this graph
     */
    public Graph<T> reduce(Graph<T> g) {
        boolean subgraph = nodes.containsAll(g.nodes) && g.edges.keySet().stream()
               .allMatch(u -> adjacentNodes(u).containsAll(g.adjacentNodes(u)));
        if (!subgraph) {
            throw new IllegalArgumentException("the given argument is not a subgraph of this graph");
        }

        Graph.Builder<T> builder = new Builder<>();
        nodes.stream()
             .forEach(u -> {
                 builder.addNode(u);
                 // filter the edge if there exists a path from u to v in the given g
                 // or there exists another path from u to v in this graph
                 edges.get(u).stream()
                      .filter(v -> !g.pathExists(u, v) && !pathExists(u, v, false))
                      .forEach(v -> builder.addEdge(u, v));
             });

        // add the overlapped edges from this graph and the given g
        g.edges().keySet().stream()
                 .forEach(u -> g.adjacentNodes(u).stream()
                         .filter(v -> isAdjacent(u, v))
                         .forEach(v -> builder.addEdge(u, v)));
        return builder.build();
    }

    private boolean isAdjacent(T u, T v) {
        return edges.containsKey(u) && edges.get(u).contains(v);
    }

    private boolean pathExists(T u, T v) {
        return pathExists(u, v, true);
    }

    /**
     * Returns true if there exists a path from u to v in this graph.
     * If includeAdjacent is false, it returns true if there exists
     * another path from u to v of distance > 1
     */
    private boolean pathExists(T u, T v, boolean includeAdjacent) {
        if (!nodes.contains(u) || !nodes.contains(v)) {
            return false;
        }
        if (includeAdjacent && isAdjacent(u, v)) {
            return true;
        }
        Deque<T> stack = new LinkedList<>();
        Set<T> visited = new HashSet<>();
        stack.push(u);
        while (!stack.isEmpty()) {
            T node = stack.pop();
            if (node.equals(v)) {
                if (traceOn) {
                    System.out.format("Edge %s -> %s removed%n", u, v);
                }
                return true;
            }
            if (!visited.contains(node)) {
                visited.add(node);
                edges.get(node).stream()
                     .filter(e -> includeAdjacent || !node.equals(u) || !e.equals(v))
                     .forEach(e -> stack.push(e));
            }
        }
        assert !visited.contains(v);
        return false;
    }

    void printGraph(PrintStream out) {
        nodes.stream()
             .forEach(u -> adjacentNodes(u).stream()
                     .forEach(v -> out.format("%s -> %s%n", u, v)));
    }

    public static class Builder<T> {
        final Set<T> nodes = new HashSet<>();
        final Map<T, Set<T>> edges = new HashMap<>();
        public void addNode(T node) {
            if (nodes.contains(node)) {
                return;
            }
            nodes.add(node);
            edges.computeIfAbsent(node, _e -> new HashSet<>());
        }
        public void addEdge(T u, T v) {
            addNode(u);
            addNode(v);
            edges.get(u).add(v);
        }
        public Graph<T> build() {
            return new Graph<>(nodes, edges);
        }
    }
}
