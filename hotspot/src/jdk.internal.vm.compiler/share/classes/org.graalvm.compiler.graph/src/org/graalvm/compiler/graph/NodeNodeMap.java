/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class NodeNodeMap extends NodeMap<Node> implements Map<Node, Node> {

    public NodeNodeMap(Graph graph) {
        super(graph);
    }

    public NodeNodeMap(NodeNodeMap copyFrom) {
        super(copyFrom);
    }

    @Override
    public Node get(Object key) {
        return super.get((Node) key);
    }

    @Override
    public Node put(Node key, Node value) {
        Node oldValue = super.get(key);
        super.set(key, value);
        return oldValue;
    }

    @Override
    public Node remove(Object key) {
        throw new UnsupportedOperationException("Cannot remove keys from this map");
    }

    @Override
    public void putAll(Map<? extends Node, ? extends Node> m) {
        for (Entry<? extends Node, ? extends Node> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Set<Node> keySet() {
        HashSet<Node> entries = new HashSet<>();
        for (int i = 0; i < values.length; ++i) {
            Object v = values[i];
            if (v != null) {
                Node key = getKey(i);
                if (key != null) {
                    entries.add(key);
                }
            }
        }
        /*
         * The normal contract for entrySet is that modifications of the set are reflected in the
         * underlying data structure. For simplicity don't allow that but complain if someone tries
         * to use it that way.
         */
        return Collections.unmodifiableSet(entries);
    }

    @Override
    public Collection<Node> values() {
        ArrayList<Node> result = new ArrayList<>(this.size());
        for (int i = 0; i < values.length; ++i) {
            Object v = values[i];
            if (v != null) {
                result.add((Node) v);
            }
        }
        return result;
    }

    @Override
    public Set<Map.Entry<Node, Node>> entrySet() {
        HashSet<Map.Entry<Node, Node>> entries = new HashSet<>();
        for (Map.Entry<Node, Node> entry : entries()) {
            entries.add(entry);
        }
        /*
         * The normal contract for entrySet is that modifications of the set are reflected in the
         * underlying data structure. For simplicity don't allow that but complain if someone tries
         * to use it that way.
         */
        return Collections.unmodifiableSet(entries);
    }
}
