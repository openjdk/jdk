/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.graphio;

import java.util.Collection;
import java.util.Map;

/**
 * Interface that defines structure of a compiler graph. The structure of a graph is composed from
 * nodes with properties, the classes of individual nodes, and ports associated with each node that
 * may contain edges to other nodes. The structure of a graph is assumed to be immutable for the
 * time of {@link GraphOutput operations} on it.
 *
 * @param <G> the type of the (root node of a) graph
 * @param <N> the type of nodes
 * @param <C> the type of node classes
 * @param <P> the type of node ports
 */
public interface GraphStructure<G, N, C, P> {
    /**
     * Casts {@code obj} to graph, if possible. If the given object <code>obj</code> can be seen as
     * a graph or sub-graph of a graph, then return the properly typed instance. Otherwise return
     * <code>null</code>
     *
     * @param currentGraph the currently processed graph
     * @param obj an object to check and view as a graph
     * @return appropriate graph object or <code>null</code> if the object doesn't represent a graph
     */
    G graph(G currentGraph, Object obj);

    /**
     * Nodes of a graph. Each graph is composed from a fixed set of nodes. This method returns an
     * iterable which provides access to all of them - the number of nodes provided by the iterable
     * must match the number returned by {@link #nodesCount(java.lang.Object)} method.
     *
     * @see #nodesCount(java.lang.Object)
     * @param graph the graph to query for nodes
     * @return iterable with all the graph's nodes
     */
    Iterable<? extends N> nodes(G graph);

    /**
     * Number of nodes in a graph. The number must match the content returned by
     * {@link #nodes(java.lang.Object)} method.
     *
     * @param graph the graph to query
     * @return the number of nodes that will be returned by {@link #nodes(java.lang.Object)}
     */
    int nodesCount(G graph);

    /**
     * Id of {@code node}. Each node in the graph is uniquely identified by an integer value. If two
     * nodes have the same id, then they shall be <code>==</code> to each other.
     *
     * @param node the node to query for an id
     * @return the id of the node
     */
    int nodeId(N node);

    /**
     * Checks if there is a predecessor for a node.
     *
     * @param node the node to check
     * @return <code>true</code> if it has a predecessor, <code>false</code> otherwise
     */
    boolean nodeHasPredecessor(N node);

    /**
     * Collects node properties. Each node can be associated with additional properties identified
     * by their name. This method shall copy them into the provided map.
     *
     * @param graph the current graph
     * @param node the node to collect properties for
     * @param properties the map to put the properties to
     */
    void nodeProperties(G graph, N node, Map<String, ? super Object> properties);

    /**
     * Finds a node for {@code obj}, if possible. If the given object <code>obj</code> can be seen
     * as an instance of node return the properly typed instance of the node class. Otherwise return
     * <code>null</code>.
     *
     * @param obj an object to find node for
     * @return appropriate graph object or <code>null</code> if the object doesn't represent a node
     */
    N node(Object obj);

    /**
     * Finds a node class for {@code obj}, if possible. If the given object <code>obj</code> can be
     * seen as an instance of node class return the properly typed instance of the node class.
     * Otherwise return <code>null</code>.
     *
     * @param obj an object to find node class for
     * @return appropriate graph object or <code>null</code> if the object doesn't represent a node
     *         class
     */
    C nodeClass(Object obj);

    /**
     * Finds a node class for {@code node}.
     *
     * @param node an instance of node in this graph
     * @return the node's node class, never <code>null</code>
     */
    C classForNode(N node);

    /**
     * The template used to build the name of nodes of this class. The template may use references
     * to inputs (&#123;i#inputName&#125;) and its properties (&#123;p#propertyName&#125;).
     *
     * @param nodeClass the node class to find name template for
     * @return the string representing the template
     */
    String nameTemplate(C nodeClass);

    /**
     * Java class for a node class.
     *
     * @param nodeClass the node class
     * @return the {@link Class} or other type representation of the node class
     */
    Object nodeClassType(C nodeClass);

    /**
     * Input ports of a node class. Each node class has a fixed set of ports where individual edges
     * can attach to.
     *
     * @param nodeClass the node class
     * @return input ports for the node class
     */
    P portInputs(C nodeClass);

    /**
     * Output ports of a node class. Each node class has a fixed set of ports from where individual
     * edges can point to other nodes.
     *
     * @param nodeClass the node class
     * @return output ports for the node class
     */
    P portOutputs(C nodeClass);

    /**
     * The number of edges in a port. The protocol will then call methods
     * {@link #edgeDirect(java.lang.Object, int)}, {@link #edgeName(java.lang.Object, int)},
     * {@link #edgeType(java.lang.Object, int)} and
     * {@link #edgeNodes(java.lang.Object, java.lang.Object, java.lang.Object, int)} for indexes
     * from <code>0</code> to <code>portSize - 1</code>
     *
     * @param port the port
     * @return number of edges in this port
     */
    int portSize(P port);

    /**
     * Checks whether an edge is direct. Direct edge shall have exactly one
     * {@linkplain #edgeNodes(java.lang.Object, java.lang.Object, java.lang.Object, int) node} - it
     * is an error to return more than one for such an edge from the
     * {@linkplain #edgeNodes(java.lang.Object, java.lang.Object, java.lang.Object, int) method}.
     *
     * @param port the port
     * @param index index from <code>0</code> to {@link #portSize(java.lang.Object)} minus
     *            <code>1</code>
     * @return <code>true</code> if only one node can be returned from
     *         {@link #edgeNodes(java.lang.Object, java.lang.Object, java.lang.Object, int)} method
     */
    boolean edgeDirect(P port, int index);

    /**
     * The name of an edge.
     *
     * @param port the port
     * @param index index from <code>0</code> to {@link #portSize(java.lang.Object)} minus
     *            <code>1</code>
     * @return the name of the edge
     */
    String edgeName(P port, int index);

    /**
     * Type of an edge. The type must be a graph
     * <q>enum</q> - e.g. either real instance of {@link Enum} subclass, or something that the
     * {@link GraphOutput.Builder} can recognize as
     * <q>enum</q>.
     *
     * @param port
     * @param index index from <code>0</code> to {@link #portSize(java.lang.Object)} minus
     *            <code>1</code>
     * @return any {@link Enum} representing type of the edge
     */
    Object edgeType(P port, int index);

    /**
     * Nodes where the edges for a port lead to/from. This method is called for both
     * {@link #edgeDirect(java.lang.Object, int) direct/non-direct edges}. In case of a direct edge
     * the returned collection must have exactly one element.
     *
     * @param graph the graph
     * @param node the node in the graph
     * @param port port of the node class
     * @param index index from <code>0</code> to {@link #portSize(java.lang.Object)} minus
     *            <code>1</code>
     * @return <code>null</code> if there are no edges associated with given port or collection of
     *         nodes where to/from the edges lead to
     */
    Collection<? extends N> edgeNodes(G graph, N node, P port, int index);
}
