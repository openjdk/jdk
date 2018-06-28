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
import static org.graalvm.compiler.graph.Node.NOT_ITERABLE;
import static org.graalvm.compiler.graph.UnsafeAccess.UNSAFE;

import java.util.ArrayList;
import java.util.Iterator;

import org.graalvm.compiler.core.common.Fields;
import org.graalvm.compiler.core.common.FieldsScanner;
import org.graalvm.compiler.graph.NodeClass.EdgeInfo;

/**
 * Describes {@link Node} fields representing the set of inputs for the node or the set of the
 * node's successors.
 */
public abstract class Edges extends Fields {

    /**
     * Constants denoting whether a set of edges are inputs or successors.
     */
    public enum Type {
        Inputs,
        Successors;
    }

    private final int directCount;
    private final Type type;

    public Edges(Type type, int directCount, ArrayList<? extends FieldsScanner.FieldInfo> edges) {
        super(edges);
        this.type = type;
        this.directCount = directCount;
    }

    public static void translateInto(Edges edges, ArrayList<EdgeInfo> infos) {
        for (int index = 0; index < edges.getCount(); index++) {
            infos.add(new EdgeInfo(edges.offsets[index], edges.getName(index), edges.getType(index), edges.getDeclaringClass(index)));
        }
    }

    public static Node getNodeUnsafe(Node node, long offset) {
        return (Node) UNSAFE.getObject(node, offset);
    }

    @SuppressWarnings("unchecked")
    public static NodeList<Node> getNodeListUnsafe(Node node, long offset) {
        return (NodeList<Node>) UNSAFE.getObject(node, offset);
    }

    public static void putNodeUnsafe(Node node, long offset, Node value) {
        UNSAFE.putObject(node, offset, value);
    }

    public static void putNodeListUnsafe(Node node, long offset, NodeList<?> value) {
        UNSAFE.putObject(node, offset, value);
    }

    /**
     * Get the number of direct edges represented by this object. A direct edge goes directly to
     * another {@link Node}. An indirect edge goes via a {@link NodeList}.
     */
    public int getDirectCount() {
        return directCount;
    }

    /**
     * Gets the {@link Node} at the end point of a {@linkplain #getDirectCount() direct} edge.
     *
     * @param node one end point of the edge
     * @param index the index of a non-list the edge (must be less than {@link #getDirectCount()})
     * @return the Node at the other edge of the requested edge
     */
    public static Node getNode(Node node, long[] offsets, int index) {
        return getNodeUnsafe(node, offsets[index]);
    }

    /**
     * Gets the {@link NodeList} at the end point of a {@linkplain #getDirectCount() direct} edge.
     *
     * @param node one end point of the edge
     * @param index the index of a non-list the edge (must be equal to or greater than
     *            {@link #getDirectCount()})
     * @return the {@link NodeList} at the other edge of the requested edge
     */
    public static NodeList<Node> getNodeList(Node node, long[] offsets, int index) {
        return getNodeListUnsafe(node, offsets[index]);
    }

    /**
     * Clear edges in a given node. This is accomplished by setting {@linkplain #getDirectCount()
     * direct} edges to null and replacing the lists containing indirect edges with new lists. The
     * latter is important so that this method can be used to clear the edges of cloned nodes.
     *
     * @param node the node whose edges are to be cleared
     */
    public void clear(Node node) {
        final long[] curOffsets = this.offsets;
        final Type curType = this.type;
        int index = 0;
        int curDirectCount = getDirectCount();
        while (index < curDirectCount) {
            initializeNode(node, index++, null);
        }
        int curCount = getCount();
        while (index < curCount) {
            NodeList<Node> list = getNodeList(node, curOffsets, index);
            if (list != null) {
                int size = list.initialSize;
                NodeList<Node> newList = curType == Edges.Type.Inputs ? new NodeInputList<>(node, size) : new NodeSuccessorList<>(node, size);

                // replacing with a new list object is the expected behavior!
                initializeList(node, index, newList);
            }
            index++;
        }
    }

    /**
     * Initializes the list edges in a given node based on the size of the list edges in a prototype
     * node.
     *
     * @param node the node whose list edges are to be initialized
     * @param prototype the node whose list edge sizes are used when creating new edge lists
     */
    public void initializeLists(Node node, Node prototype) {
        int index = getDirectCount();
        final long[] curOffsets = this.offsets;
        final Edges.Type curType = this.type;
        while (index < getCount()) {
            NodeList<Node> list = getNodeList(prototype, curOffsets, index);
            if (list != null) {
                int size = list.initialSize;
                NodeList<Node> newList = curType == Edges.Type.Inputs ? new NodeInputList<>(node, size) : new NodeSuccessorList<>(node, size);
                initializeList(node, index, newList);
            }
            index++;
        }
    }

    /**
     * Copies edges from {@code fromNode} to {@code toNode}. The nodes are expected to be of the
     * exact same type.
     *
     * @param fromNode the node from which the edges should be copied.
     * @param toNode the node to which the edges should be copied.
     */
    public void copy(Node fromNode, Node toNode) {
        assert fromNode != toNode;
        assert fromNode.getNodeClass().getClazz() == toNode.getNodeClass().getClazz();
        int index = 0;
        final long[] curOffsets = this.offsets;
        final Type curType = this.type;
        int curDirectCount = getDirectCount();
        while (index < curDirectCount) {
            initializeNode(toNode, index, getNode(fromNode, curOffsets, index));
            index++;
        }
        int curCount = getCount();
        while (index < curCount) {
            NodeList<Node> list = getNodeList(toNode, curOffsets, index);
            NodeList<Node> fromList = getNodeList(fromNode, curOffsets, index);
            if (list == null || list == fromList) {
                list = curType == Edges.Type.Inputs ? new NodeInputList<>(toNode, fromList) : new NodeSuccessorList<>(toNode, fromList);
                initializeList(toNode, index, list);
            } else {
                list.copy(fromList);
            }
            index++;
        }
    }

    @Override
    public void set(Object node, int index, Object value) {
        throw new IllegalArgumentException("Cannot call set on " + this);
    }

    /**
     * Sets the value of a given edge without notifying the new and old nodes on the other end of
     * the edge of the change.
     *
     * @param node the node whose edge is to be updated
     * @param index the index of the edge (between 0 and {@link #getCount()})
     * @param value the node to be written to the edge
     */
    public void initializeNode(Node node, int index, Node value) {
        verifyUpdateValid(node, index, value);
        putNodeUnsafe(node, offsets[index], value);
    }

    public void initializeList(Node node, int index, NodeList<Node> value) {
        verifyUpdateValid(node, index, value);
        putNodeListUnsafe(node, offsets[index], value);
    }

    private void verifyUpdateValid(Node node, int index, Object newValue) {
        if (newValue != null && !getType(index).isAssignableFrom(newValue.getClass())) {
            throw new IllegalArgumentException("Can not assign " + newValue.getClass() + " to " + getType(index) + " in " + node);
        }
    }

    /**
     * Sets the value of a given edge and notifies the new and old nodes on the other end of the
     * edge of the change.
     *
     * @param node the node whose edge is to be updated
     * @param index the index of the edge (between 0 and {@link #getCount()})
     * @param value the node to be written to the edge
     */
    public void setNode(Node node, int index, Node value) {
        assert index < directCount;
        Node old = getNodeUnsafe(node, offsets[index]);
        initializeNode(node, index, value);
        update(node, old, value);
    }

    public abstract void update(Node node, Node oldValue, Node newValue);

    public boolean contains(Node node, Node value) {
        final long[] curOffsets = this.offsets;
        for (int i = 0; i < directCount; i++) {
            if (getNode(node, curOffsets, i) == value) {
                return true;
            }
        }
        for (int i = directCount; i < getCount(); i++) {
            NodeList<?> curList = getNodeList(node, curOffsets, i);
            if (curList != null && curList.contains(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * An iterator that will iterate over edges.
     *
     * An iterator of this type will not return null values, unless edges are modified concurrently.
     * Concurrent modifications are detected by an assertion on a best-effort basis.
     */
    private static class EdgesIterator implements Iterator<Position> {
        protected final Node node;
        protected final Edges edges;
        protected int index;
        protected int subIndex;
        protected boolean needsForward;
        protected final int directCount;
        protected final long[] offsets;

        /**
         * Creates an iterator that will iterate over some given edges in a given node.
         */
        EdgesIterator(Node node, Edges edges) {
            this.node = node;
            this.edges = edges;
            index = NOT_ITERABLE;
            subIndex = 0;
            needsForward = true;
            this.directCount = edges.getDirectCount();
            this.offsets = edges.getOffsets();
        }

        void forward() {
            needsForward = false;
            if (index < directCount) {
                index++;
                if (index < directCount) {
                    return;
                }
            } else {
                subIndex++;
            }
            if (index < edges.getCount()) {
                forwardNodeList();
            }
        }

        private void forwardNodeList() {
            do {
                NodeList<?> list = Edges.getNodeList(node, offsets, index);
                if (list != null) {
                    if (subIndex < list.size()) {
                        return;
                    }
                }
                subIndex = 0;
                index++;
            } while (index < edges.getCount());
        }

        @Override
        public boolean hasNext() {
            if (needsForward) {
                forward();
            }
            return index < edges.getCount();
        }

        @Override
        public Position next() {
            if (needsForward) {
                forward();
            }
            needsForward = true;
            if (index < directCount) {
                return new Position(edges, index, NOT_ITERABLE);
            } else {
                return new Position(edges, index, subIndex);
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class EdgesWithModCountIterator extends EdgesIterator {
        private final int modCount;

        private EdgesWithModCountIterator(Node node, Edges edges) {
            super(node, edges);
            assert isModificationCountsEnabled();
            this.modCount = node.modCount();
        }

        @Override
        public boolean hasNext() {
            try {
                return super.hasNext();
            } finally {
                assert modCount == node.modCount() : "must not be modified";
            }
        }

        @Override
        public Position next() {
            try {
                return super.next();
            } finally {
                assert modCount == node.modCount() : "must not be modified";
            }
        }
    }

    public Iterable<Position> getPositionsIterable(final Node node) {
        return new Iterable<Position>() {

            @Override
            public Iterator<Position> iterator() {
                if (isModificationCountsEnabled()) {
                    return new EdgesWithModCountIterator(node, Edges.this);
                } else {
                    return new EdgesIterator(node, Edges.this);
                }
            }
        };
    }

    public Type type() {
        return type;
    }
}
