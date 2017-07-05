/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.PluginException;

/**
 *
 * @author jdenise
 */
public class PluginOrderingGraph {

    static class Node {

        Plugin plugin;
        Set<Node> nexts = new HashSet<>();
        Set<Node> previous = new HashSet<>();

        @Override
        public String toString() {
            return plugin.getName();
        }
    }

    public static List<Plugin> sort(List<Plugin> plugs) {
        List<Plugin> plugins = new ArrayList<>();
        plugins.addAll(plugs);

        // for each plugin creates its graph.
        Map<Plugin, Node> graphs = buildGraphs(plugins);

        // At this points all individual graphs are connected
        // Check for cycles.
        for (Node n : graphs.values()) {
            checkCycle(n);
        }

        List<Plugin> orderedPlugins = new ArrayList<>();
        // Retrieve the current roots, add them to the result, remove them from
        while (!plugins.isEmpty()) {
            // Build the current set of graphs from the list of input plugins
            Map<Plugin, Node> currentGraphs = buildGraphs(plugins);
            // Retrieve the root nodes (no previous)
            List<Node> roots = getRoots(currentGraphs);
            for (Node n : roots) {
                // add them to the ordered list.
                orderedPlugins.add(n.plugin);
                // remove them from the input list.
                plugins.remove(n.plugin);
            }
        }
        return orderedPlugins;
    }

    // Create a grapth according to list of plugins.
    private static Map<Plugin, Node> buildGraphs(List<Plugin> plugins) {
        Map<String, Node> nodeStore = new HashMap<>();
        for (Plugin p : plugins) {
            Node newNode = new Node();
            newNode.plugin = p;
            nodeStore.put(p.getName(), newNode);
        }
        // for each plugin creates its graph.
        Map<Plugin, Node> graphs = new LinkedHashMap<>();
        for (Plugin p : plugins) {
            Node node = nodeStore.get(p.getName());
            for (String after : p.isAfter()) {
                Node previous = nodeStore.get(after);
                if (previous == null) {
                    continue;
                }
                node.previous.add(previous);
                previous.nexts.add(node);
            }
            for (String before : p.isBefore()) {
                Node next = nodeStore.get(before);
                if (next == null) {
                    continue;
                }
                node.nexts.add(next);
                next.previous.add(node);
            }
            graphs.put(p, node);

        }
        return graphs;
    }

    private static List<Node> getRoots(Map<Plugin, Node> graphs) {
        List<Node> ret = new ArrayList<>();
        for (Node n : graphs.values()) {
            if (n.previous.isEmpty()) {
                ret.add(n);
            }
        }
        return ret;
    }

    private static void checkCycle(Node root) {
        for (Node next : root.nexts) {
            Set<Node> path = new LinkedHashSet<>();
            path.add(root);
            checkCycle(next, root, path);
        }
    }

    private static void checkCycle(Node current, Node root, Set<Node> path) {
        path.add(current);
        if (current == root) {
            StringBuilder builder = new StringBuilder();
            for (Node p : path) {
                builder.append(p.plugin.getName()).append(">");
            }
            builder.append(root);
            throw new PluginException("Cycle detected for " + builder.toString()
                    + ". Please fix Plugin ordering (before, after constraints).");
        }

        for (Node n : current.nexts) {
            checkCycle(n, root, path);
        }
    }
}
