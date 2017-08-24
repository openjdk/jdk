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

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map.Entry;

public class NodeMap<T> extends NodeIdAccessor {

    private static final int MIN_REALLOC_SIZE = 16;

    protected Object[] values;

    public NodeMap(Graph graph) {
        super(graph);
        this.values = new Object[graph.nodeIdCount()];
    }

    public NodeMap(NodeMap<T> copyFrom) {
        super(copyFrom.graph);
        this.values = Arrays.copyOf(copyFrom.values, copyFrom.values.length);
    }

    @SuppressWarnings("unchecked")
    public T get(Node node) {
        assert check(node);
        return (T) values[getNodeId(node)];
    }

    @SuppressWarnings("unchecked")
    public T getAndGrow(Node node) {
        checkAndGrow(node);
        return (T) values[getNodeId(node)];
    }

    private void checkAndGrow(Node node) {
        if (isNew(node)) {
            this.values = Arrays.copyOf(values, Math.max(MIN_REALLOC_SIZE, graph.nodeIdCount() * 3 / 2));
        }
        assert check(node);
    }

    public boolean isEmpty() {
        return !entries().iterator().hasNext();
    }

    public boolean containsKey(Object key) {
        if (key instanceof Node) {
            Node node = (Node) key;
            if (node.graph() == graph()) {
                return get(node) != null;
            }
        }
        return false;
    }

    public boolean containsValue(Object value) {
        for (Object o : values) {
            if (o == value) {
                return true;
            }
        }
        return false;
    }

    public Graph graph() {
        return graph;
    }

    public void set(Node node, T value) {
        assert check(node);
        values[getNodeId(node)] = value;
    }

    public void setAndGrow(Node node, T value) {
        checkAndGrow(node);
        values[getNodeId(node)] = value;
    }

    /**
     * @param i
     * @return Return the key for the entry at index {@code i}
     */
    protected Node getKey(int i) {
        return graph.getNode(i);
    }

    public int size() {
        return values.length;
    }

    public boolean isNew(Node node) {
        return getNodeId(node) >= size();
    }

    private boolean check(Node node) {
        assert node.graph() == graph : String.format("%s is not part of the graph", node);
        assert !isNew(node) : "this node was added to the graph after creating the node map : " + node;
        return true;
    }

    public void clear() {
        Arrays.fill(values, null);
    }

    public Iterable<Entry<Node, T>> entries() {
        return new Iterable<Entry<Node, T>>() {

            @Override
            public Iterator<Entry<Node, T>> iterator() {
                return new Iterator<Entry<Node, T>>() {

                    int i = 0;

                    @Override
                    public boolean hasNext() {
                        forward();
                        return i < NodeMap.this.values.length;
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public Entry<Node, T> next() {
                        final int pos = i;
                        Node key = NodeMap.this.getKey(pos);
                        T value = (T) NodeMap.this.values[pos];
                        i++;
                        forward();
                        return new SimpleEntry<Node, T>(key, value) {

                            private static final long serialVersionUID = 7813842391085737738L;

                            @Override
                            public T setValue(T v) {
                                T oldv = super.setValue(v);
                                NodeMap.this.values[pos] = v;
                                return oldv;
                            }
                        };
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    private void forward() {
                        while (i < NodeMap.this.values.length && (NodeMap.this.getKey(i) == null || NodeMap.this.values[i] == null)) {
                            i++;
                        }
                    }
                };
            }
        };
    }

    @Override
    public String toString() {
        Iterator<Entry<Node, T>> i = entries().iterator();
        if (!i.hasNext()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        while (true) {
            Entry<Node, T> e = i.next();
            Node key = e.getKey();
            T value = e.getValue();
            sb.append(key);
            sb.append('=');
            sb.append(value);
            if (!i.hasNext()) {
                return sb.append('}').toString();
            }
            sb.append(',').append(' ');
        }
    }
}
