/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.util;

/** <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class GraphUtils {

    /**
     * This class is a basic abstract class for representing a node.
     * A node is associated with a given data.
     */
    public static abstract class Node<D> {
        public final D data;

        public Node(D data) {
            this.data = data;
        }

        public abstract Iterable<? extends Node<D>> getDependencies();

        public abstract String printDependency(Node<D> to);

        @Override
        public String toString() {
            return data.toString();
        }
    }

    /**
     * This class specialized Node, by adding elements that are required in order
     * to perform Tarjan computation of strongly connected components.
     */
    public static abstract class TarjanNode<D> extends Node<D> implements Comparable<TarjanNode<D>> {
        int index = -1;
        int lowlink;
        boolean active;

        public TarjanNode(D data) {
            super(data);
        }

        public abstract Iterable<? extends TarjanNode<D>> getDependencies();

        public int compareTo(TarjanNode<D> o) {
            return (index < o.index) ? -1 : (index == o.index) ? 0 : 1;
        }
    }

    /**
     * Tarjan's algorithm to determine strongly connected components of a
     * directed graph in linear time. Works on TarjanNode.
     */
    public static <D, N extends TarjanNode<D>> List<? extends List<? extends N>> tarjan(Iterable<? extends N> nodes) {
        ListBuffer<List<N>> cycles = ListBuffer.lb();
        ListBuffer<N> stack = ListBuffer.lb();
        int index = 0;
        for (N node: nodes) {
            if (node.index == -1) {
                index += tarjan(node, index, stack, cycles);
            }
        }
        return cycles.toList();
    }

    private static <D, N extends TarjanNode<D>> int tarjan(N v, int index, ListBuffer<N> stack, ListBuffer<List<N>> cycles) {
        v.index = index;
        v.lowlink = index;
        index++;
        stack.prepend(v);
        v.active = true;
        for (TarjanNode<D> nd: v.getDependencies()) {
            @SuppressWarnings("unchecked")
            N n = (N)nd;
            if (n.index == -1) {
                tarjan(n, index, stack, cycles);
                v.lowlink = Math.min(v.lowlink, n.lowlink);
            } else if (stack.contains(n)) {
                v.lowlink = Math.min(v.lowlink, n.index);
            }
        }
        if (v.lowlink == v.index) {
            N n;
            ListBuffer<N> cycle = ListBuffer.lb();
            do {
                n = stack.remove();
                n.active = false;
                cycle.add(n);
            } while (n != v);
            cycles.add(cycle.toList());
        }
        return index;
    }

    /**
     * Debugging: dot representation of a set of connected nodes. The resulting
     * dot representation will use {@code Node.toString} to display node labels
     * and {@code Node.printDependency} to display edge labels. The resulting
     * representation is also customizable with a graph name and a header.
     */
    public static <D> String toDot(Iterable<? extends TarjanNode<D>> nodes, String name, String header) {
        StringBuilder buf = new StringBuilder();
        buf.append(String.format("digraph %s {\n", name));
        buf.append(String.format("label = \"%s\";\n", header));
        //dump nodes
        for (TarjanNode<D> n : nodes) {
            buf.append(String.format("%s [label = \"%s\"];\n", n.hashCode(), n.toString()));
        }
        //dump arcs
        for (TarjanNode<D> from : nodes) {
            for (TarjanNode<D> to : from.getDependencies()) {
                buf.append(String.format("%s -> %s [label = \" %s \"];\n",
                        from.hashCode(), to.hashCode(), from.printDependency(to)));
            }
        }
        buf.append("}\n");
        return buf.toString();
    }
}
