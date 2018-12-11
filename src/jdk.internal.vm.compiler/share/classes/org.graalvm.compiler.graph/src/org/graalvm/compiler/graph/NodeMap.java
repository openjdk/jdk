/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Iterator;
import java.util.function.BiFunction;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.MapCursor;

public class NodeMap<T> extends NodeIdAccessor implements EconomicMap<Node, T> {

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

    @Override
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

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException("isEmpty() is not supported for performance reasons");
    }

    @Override
    public boolean containsKey(Node node) {
        if (node.graph() == graph()) {
            return get(node) != null;
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
        set(node, value);
    }

    /**
     * @param i
     * @return Return the key for the entry at index {@code i}
     */
    protected Node getKey(int i) {
        return graph.getNode(i);
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException("size() is not supported for performance reasons");
    }

    public int capacity() {
        return values.length;
    }

    public boolean isNew(Node node) {
        return getNodeId(node) >= capacity();
    }

    private boolean check(Node node) {
        assert node.graph() == graph : String.format("%s is not part of the graph", node);
        assert !isNew(node) : "this node was added to the graph after creating the node map : " + node;
        return true;
    }

    @Override
    public void clear() {
        Arrays.fill(values, null);
    }

    @Override
    public Iterable<Node> getKeys() {
        return new Iterable<Node>() {

            @Override
            public Iterator<Node> iterator() {
                return new Iterator<Node>() {

                    int i = 0;

                    @Override
                    public boolean hasNext() {
                        forward();
                        return i < NodeMap.this.values.length;
                    }

                    @Override
                    public Node next() {
                        final int pos = i;
                        final Node key = NodeMap.this.getKey(pos);
                        i++;
                        forward();
                        return key;
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
    public MapCursor<Node, T> getEntries() {
        return new MapCursor<Node, T>() {

            int current = -1;

            @Override
            public boolean advance() {
                current++;
                while (current < NodeMap.this.values.length && (NodeMap.this.values[current] == null || NodeMap.this.getKey(current) == null)) {
                    current++;
                }
                return current < NodeMap.this.values.length;
            }

            @Override
            public Node getKey() {
                return NodeMap.this.getKey(current);
            }

            @SuppressWarnings("unchecked")
            @Override
            public T getValue() {
                return (T) NodeMap.this.values[current];
            }

            @Override
            public void remove() {
                assert NodeMap.this.values[current] != null;
                NodeMap.this.values[current] = null;
            }
        };
    }

    @Override
    public Iterable<T> getValues() {
        return new Iterable<T>() {

            @Override
            public Iterator<T> iterator() {
                return new Iterator<T>() {

                    int i = 0;

                    @Override
                    public boolean hasNext() {
                        forward();
                        return i < NodeMap.this.values.length;
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public T next() {
                        final int pos = i;
                        final T value = (T) NodeMap.this.values[pos];
                        i++;
                        forward();
                        return value;
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
        MapCursor<Node, T> i = getEntries();
        if (!i.advance()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        while (true) {
            Node key = i.getKey();
            T value = i.getValue();
            sb.append(key);
            sb.append('=');
            sb.append(value);
            if (!i.advance()) {
                return sb.append('}').toString();
            }
            sb.append(',').append(' ');
        }
    }

    @Override
    public T put(Node key, T value) {
        T result = get(key);
        set(key, value);
        return result;
    }

    @Override
    public T removeKey(Node key) {
        return put(key, null);
    }

    @Override
    public void replaceAll(BiFunction<? super Node, ? super T, ? extends T> function) {
        for (Node n : getKeys()) {
            put(n, function.apply(n, get(n)));
        }
    }
}
