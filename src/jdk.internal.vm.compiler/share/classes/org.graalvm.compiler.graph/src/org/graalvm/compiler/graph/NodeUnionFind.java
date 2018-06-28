/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Union-find data structure for {@link Node Nodes}.
 *
 * All operations have an accumulated worst-case complexity of O(a(n)), where a(n) is the inverse of
 * the Ackermann function A(n,n).
 */
public class NodeUnionFind extends NodeIdAccessor {

    private int[] rank;
    private int[] parent;

    /**
     * Create a new union-find data structure for a {@link Graph}. Initially, all nodes are in their
     * own equivalence set.
     */
    public NodeUnionFind(Graph graph) {
        super(graph);
        rank = new int[graph.nodeIdCount()];
        parent = new int[graph.nodeIdCount()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
    }

    /**
     * Merge the equivalence sets of two nodes.
     *
     * After calling this function, find(a) == find(b).
     */
    public void union(Node a, Node b) {
        union(getNodeId(a), getNodeId(b));
    }

    /**
     * Get a representative element of the equivalence set of a node.
     *
     * This function returns the same representative element for all members of the same equivalence
     * set, i.e., find(a) == find(b) if and only if a and b are in the same set.
     */
    public Node find(Node a) {
        int id = find(getNodeId(a));
        return graph.getNode(id);
    }

    public boolean equiv(Node a, Node b) {
        return find(getNodeId(a)) == find(getNodeId(b));
    }

    private void union(int a, int b) {
        int aRoot = find(a);
        int bRoot = find(b);
        if (aRoot != bRoot) {
            if (rank[aRoot] < rank[bRoot]) {
                parent[aRoot] = bRoot;
            } else {
                parent[bRoot] = aRoot;
                if (rank[aRoot] == rank[bRoot]) {
                    rank[aRoot]++;
                }
            }
        }
    }

    private int find(int a) {
        int ret = a;
        while (ret != parent[ret]) {
            parent[ret] = parent[parent[ret]];
            ret = parent[ret];
        }
        return ret;
    }
}
