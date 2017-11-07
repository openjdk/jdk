/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common.util;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.graalvm.compiler.graph.Graph.NodeEvent;
import org.graalvm.compiler.graph.Graph.NodeEventListener;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.IndirectCanonicalization;
import org.graalvm.util.Equivalence;
import org.graalvm.util.EconomicSet;

/**
 * A simple {@link NodeEventListener} implementation that accumulates event nodes in a
 * {@link HashSet}.
 */
public class HashSetNodeEventListener extends NodeEventListener {

    private final EconomicSet<Node> nodes;
    private final Set<NodeEvent> filter;

    /**
     * Creates a {@link NodeEventListener} that collects nodes from all events.
     */
    public HashSetNodeEventListener() {
        this.nodes = EconomicSet.create(Equivalence.IDENTITY);
        this.filter = EnumSet.allOf(NodeEvent.class);
    }

    /**
     * Creates a {@link NodeEventListener} that collects nodes from all events that match a given
     * filter.
     */
    public HashSetNodeEventListener(Set<NodeEvent> filter) {
        this.nodes = EconomicSet.create(Equivalence.IDENTITY);
        this.filter = filter;
    }

    /**
     * Excludes a given event from those for which nodes are collected.
     */
    public HashSetNodeEventListener exclude(NodeEvent e) {
        filter.remove(e);
        return this;
    }

    @Override
    public void changed(NodeEvent e, Node node) {
        if (filter.contains(e)) {
            nodes.add(node);
            if (node instanceof IndirectCanonicalization) {
                for (Node usage : node.usages()) {
                    nodes.add(usage);
                }
            }
        }
    }

    /**
     * Gets the set being used to accumulate the nodes communicated to this listener.
     */
    public EconomicSet<Node> getNodes() {
        return nodes;
    }
}
