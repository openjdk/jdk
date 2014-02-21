/*
 * Copyright (c) 2000, 2014, Oracle and/or its affiliates. All rights reserved.
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

package javax.imageio.spi;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * A node in a directed graph.  In addition to an arbitrary
 * <code>Object</code> containing user data associated with the node,
 * each node maintains a <code>Set</code>s of nodes which are pointed
 * to by the current node (available from <code>getOutNodes</code>).
 * The in-degree of the node (that is, number of nodes that point to
 * the current node) may be queried.
 *
 */
class DigraphNode<E> implements Cloneable, Serializable {
    private static final long serialVersionUID = 5308261378582246841L;

    /** The data associated with this node. */
    protected E data;

    /**
     * A <code>Set</code> of neighboring nodes pointed to by this
     * node.
     */
    protected Set<DigraphNode<E>> outNodes = new HashSet<>();

    /** The in-degree of the node. */
    protected int inDegree = 0;

    /**
     * A <code>Set</code> of neighboring nodes that point to this
     * node.
     */
    private Set<DigraphNode<E>> inNodes = new HashSet<>();

    public DigraphNode(E data) {
        this.data = data;
    }

    /** Returns the <code>Object</code> referenced by this node. */
    public E getData() {
        return data;
    }

    /**
     * Returns an <code>Iterator</code> containing the nodes pointed
     * to by this node.
     */
    public Iterator<DigraphNode<E>> getOutNodes() {
        return outNodes.iterator();
    }

    /**
     * Adds a directed edge to the graph.  The outNodes list of this
     * node is updated and the in-degree of the other node is incremented.
     *
     * @param node a <code>DigraphNode</code>.
     *
     * @return <code>true</code> if the node was not previously the
     * target of an edge.
     */
    public boolean addEdge(DigraphNode<E> node) {
        if (outNodes.contains(node)) {
            return false;
        }

        outNodes.add(node);
        node.inNodes.add(this);
        node.incrementInDegree();
        return true;
    }

    /**
     * Returns <code>true</code> if an edge exists between this node
     * and the given node.
     *
     * @param node a <code>DigraphNode</code>.
     *
     * @return <code>true</code> if the node is the target of an edge.
     */
    public boolean hasEdge(DigraphNode<E> node) {
        return outNodes.contains(node);
    }

    /**
     * Removes a directed edge from the graph.  The outNodes list of this
     * node is updated and the in-degree of the other node is decremented.
     *
     * @return <code>true</code> if the node was previously the target
     * of an edge.
     */
    public boolean removeEdge(DigraphNode<E> node) {
        if (!outNodes.contains(node)) {
            return false;
        }

        outNodes.remove(node);
        node.inNodes.remove(this);
        node.decrementInDegree();
        return true;
    }

    /**
     * Removes this node from the graph, updating neighboring nodes
     * appropriately.
     */
    public void dispose() {
        @SuppressWarnings("unchecked")
        DigraphNode<E>[] inNodesArray = (DigraphNode<E>[])inNodes.toArray();
        for(int i=0; i<inNodesArray.length; i++) {
            DigraphNode<E> node = inNodesArray[i];
            node.removeEdge(this);
        }

        @SuppressWarnings("unchecked")
        DigraphNode<E>[] outNodesArray = (DigraphNode<E>[])outNodes.toArray();
        for(int i=0; i<outNodesArray.length; i++) {
            DigraphNode<E> node = outNodesArray[i];
            removeEdge(node);
        }
    }

    /** Returns the in-degree of this node. */
    public int getInDegree() {
        return inDegree;
    }

    /** Increments the in-degree of this node. */
    private void incrementInDegree() {
        ++inDegree;
    }

    /** Decrements the in-degree of this node. */
    private void decrementInDegree() {
        --inDegree;
    }
}
