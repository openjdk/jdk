/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.graph.Graph.isModificationCountsEnabled;

import java.util.Iterator;

import org.graalvm.compiler.graph.iterators.NodeIterable;

class NodeUsageIterable implements NodeIterable<Node> {

    private final Node node;

    NodeUsageIterable(Node node) {
        this.node = node;
    }

    @Override
    public NodeUsageIterator iterator() {
        if (isModificationCountsEnabled()) {
            return new NodeUsageWithModCountIterator(node);
        } else {
            return new NodeUsageIterator(node);
        }
    }

    @Override
    public Node first() {
        return node.usage0;
    }

    @Override
    public boolean isEmpty() {
        return node.usage0 == null;
    }

    @Override
    public boolean isNotEmpty() {
        return node.usage0 != null;
    }

    @Override
    public int count() {
        return node.getUsageCount();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Iterator<Node> iterator = iterator();
        boolean first = true;
        sb.append("usages=");
        sb.append('[');
        while (iterator.hasNext()) {
            Node input = iterator.next();
            if (!first) {
                sb.append(", ");
            }
            sb.append(input);
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }
}
