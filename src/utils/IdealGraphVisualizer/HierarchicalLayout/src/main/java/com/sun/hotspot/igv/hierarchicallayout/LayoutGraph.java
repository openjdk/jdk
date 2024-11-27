/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package com.sun.hotspot.igv.hierarchicallayout;

import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Port;
import com.sun.hotspot.igv.layout.Vertex;
import java.util.*;
import java.util.stream.Collectors;


/**
 *
 * @author Thomas Wuerthinger
 */
public class LayoutGraph {

    public static final Comparator<Link> LINK_COMPARATOR =
            Comparator.comparing((Link l) -> l.getFrom().getVertex())
                    .thenComparing(l -> l.getTo().getVertex())
                    .thenComparingInt(l -> l.getFrom().getRelativePosition().x)
                    .thenComparingInt(l -> l.getTo().getRelativePosition().x);

    // Registered Graph Components: Links, Vertices, and Port Mappings
    private final Set<Link> links;
    private final SortedSet<Vertex> vertices;
    private final LinkedHashMap<Vertex, Set<Port>> inputPorts;

    // Layout Management: LayoutNodes and LayoutLayers
    private final LinkedHashMap<Vertex, LayoutNode> layoutNodes;
    private final List<LayoutNode> dummyNodes;
    private final List<LayoutLayer> layers;

    /**
     * Constructs a new LayoutGraph using the provided collection of links and additional vertices.
     * Initializes the graph layout structure with the given links and includes any additional vertices.
     *
     * @param links              The collection of links that represent the edges of the graph.
     * @param additionalVertices The collection of additional vertices to be included in the graph.
     */
    public LayoutGraph(Collection<? extends Link> links, Collection<? extends Vertex> additionalVertices) {
        this.links = new HashSet<>(links);
        vertices = new TreeSet<>(additionalVertices);
        LinkedHashMap<Port, Set<Link>> portLinks = new LinkedHashMap<>(links.size());
        inputPorts = new LinkedHashMap<>(links.size());
        LinkedHashMap<Vertex, Set<Port>> outputPorts = new LinkedHashMap<>(links.size());

        for (Link link : links) {
            assert link.getFrom() != null;
            assert link.getTo() != null;
            Port fromPort = link.getFrom();
            Port toPort = link.getTo();
            Vertex fromVertex = fromPort.getVertex();
            Vertex toVertex = toPort.getVertex();

            vertices.add(fromVertex);
            vertices.add(toVertex);

            outputPorts.computeIfAbsent(fromVertex, k -> new HashSet<>()).add(fromPort);
            inputPorts.computeIfAbsent(toVertex, k -> new HashSet<>()).add(toPort);

            portLinks.computeIfAbsent(fromPort, k -> new HashSet<>()).add(link);
            portLinks.computeIfAbsent(toPort, k -> new HashSet<>()).add(link);
        }

        // cleanup
        layoutNodes = new LinkedHashMap<>();
        dummyNodes = new ArrayList<>();
        layers = new ArrayList<>();
    }

    public void clearLayout() {
        layoutNodes.clear();
        dummyNodes.clear();
        layers.clear();
    }

    /**
     * Initializes or resets the layout structures by clearing existing nodes, dummy nodes, and layers.
     * It then sets up the layout nodes for each vertex and creates layout edges based on the sorted links.
     */
    public void initializeLayout() {
        // Reset layout structures
        clearLayout();

        // Set up layout nodes for each vertex
        for (Vertex vertex : getVertices()) {
            createLayoutNode(vertex);
        }

        // Set up layout edges in a sorted order for reproducibility
        List<Link> sortedLinks = new ArrayList<>(links);
        sortedLinks.sort(LINK_COMPARATOR);
        for (Link link : sortedLinks) {
            createLayoutEdge(link);
        }
    }

    /**
     * Initializes the layers of the graph with the specified number of empty layers.
     *
     * @param layerCount The number of layers to initialize.
     */
    public void initLayers(int layerCount) {
        layers.clear();
        for (int i = 0; i < layerCount; i++) {
            layers.add(new LayoutLayer());
        }
    }

    /**
     * Retrieves an unmodifiable list of dummy nodes in the graph.
     *
     * @return An unmodifiable list containing all dummy nodes in the graph.
     */
    public List<LayoutNode> getDummyNodes() {
        return Collections.unmodifiableList(dummyNodes);
    }

    /**
     * Retrieves a collection of all layout nodes in the graph.
     *
     * @return A collection containing all LayoutNodes.
     */
    public Collection<LayoutNode> getLayoutNodes() {
        return Collections.unmodifiableCollection(layoutNodes.values());
    }

    /**
     * Retrieves a combined list of all nodes in the graph,
     * including both layout nodes and dummy nodes.
     *
     * @return An unmodifiable list containing all nodes in the graph.
     */
    public List<LayoutNode> getAllNodes() {
        List<LayoutNode> allNodes = new ArrayList<>();
        allNodes.addAll(layoutNodes.values());
        allNodes.addAll(dummyNodes);
        return Collections.unmodifiableList(allNodes);
    }

    /**
     * Retrieves an unmodifiable list of all layers in the graph.
     *
     * @return An unmodifiable list containing all layers.
     */
    public List<LayoutLayer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    /**
     * Returns the total number of layers in the graph.
     *
     * @return The number of layers.
     */
    public int getLayerCount() {
        return layers.size();
    }

    /**
     * Adds a LayoutNode to the specified layer and registers it in the graph.
     *
     * @param node        The LayoutNode to add to the layer.
     * @param layerNumber The index of the layer to which the node will be added.
     */
    public void addDummyToLayer(LayoutNode node, int layerNumber) {
        assert node.isDummy();
        node.setLayer(layerNumber);
        getLayer(layerNumber).add(node);
        dummyNodes.add(node);
    }

    /**
     * Updates the positions of all nodes in each layer.
     * Should be called after changes to node positions or layer compositions.
     */
    public void updatePositions() {
        for (LayoutLayer layer : layers) {
            layer.updateNodeIndices();
        }
    }

    // Create and register LayoutNode
    public LayoutNode createLayoutNode(Vertex vertex) {
        if (!vertices.contains(vertex)) {
            throw new IllegalArgumentException("Vertex does not exist in the graph: " + vertex);
        }
        LayoutNode node = new LayoutNode(vertex);
        layoutNodes.put(vertex, node);
        return node;
    }

    /**
     * Creates a LayoutEdge based on the given Link and connects it to the corresponding LayoutNodes.
     *
     * @param link The Link representing the edge in the graph.
     * @return The newly created LayoutEdge.
     */
    public LayoutEdge createLayoutEdge(Link link) {
        LayoutEdge edge = new LayoutEdge(
                layoutNodes.get(link.getFrom().getVertex()),
                layoutNodes.get(link.getTo().getVertex()),
                link.getFrom().getRelativePosition().x,
                link.getTo().getRelativePosition().x,
                link);
        edge.getFrom().addSuccessor(edge);
        edge.getTo().addPredecessor(edge);
        return edge;
    }

    /**
     * Retrieves the set of all links (edges) in the graph.
     *
     * @return A set containing all links in the graph.
     */
    public Set<Link> getLinks() {
        return links;
    }

    /**
     * Retrieves the set of all vertices in the graph, sorted in natural order.
     *
     * @return A sorted set of all vertices in the graph.
     */
    public SortedSet<Vertex> getVertices() {
        return vertices;
    }

    /**
     * Checks whether the graph contains the specified vertex.
     *
     * @param vertex The vertex to check for presence in the graph.
     * @return True if the vertex is present, false otherwise.
     */
    public boolean containsVertex(Vertex vertex) {
        return vertices.contains(vertex);
    }

    /**
     * Finds all root vertices in the graph (vertices with no incoming links).
     *
     * @return A set of root vertices.
     */
    public Set<Vertex> findRootVertices() {
        return vertices.stream()
                .filter(v -> inputPorts.getOrDefault(v, Collections.emptySet()).isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves the LayoutLayer at the specified index.
     *
     * @param layerNr The index of the layer to retrieve.
     * @return The LayoutLayer at the specified index.
     */
    public LayoutLayer getLayer(int layerNr) {
        return layers.get(layerNr);
    }

    /**
     * Positions the layers vertically, calculating their heights and setting their positions.
     * Centers the nodes within each layer vertically.
     */
    public void positionLayers() {
        int currentY = 0;
        for (LayoutLayer layer : getLayers()) {
            layer.setTop(currentY);

            // Calculate the maximum layer height and set it for the layer
            int maxLayerHeight = layer.calculateMaxLayerHeight();
            layer.setHeight(maxLayerHeight);

            // Center nodes vertically within the layer
            layer.centerNodesVertically();

            // Update currentY to account for the padded bottom of this layer
            currentY += layer.calculatePaddedHeight();
        }
    }

    /**
     * Inserts dummy nodes along the edges to successors of the specified node,
     * for edges that span more than one layer.
     * Can limit the maximum length of layers an edge spans using maxLayerLength.
     *
     * @param layoutNode     The node for which to create successor dummy nodes.
     * @param maxLayerLength The maximum number of layers an edge can span without splitting it
     */
    public void createDummiesForNodeSuccessor(LayoutNode layoutNode, int maxLayerLength) {
        LinkedHashMap<Integer, List<LayoutEdge>> portsToUnprocessedEdges = new LinkedHashMap<>();
        ArrayList<LayoutEdge> succs = new ArrayList<>(layoutNode.getSuccessors());
        LinkedHashMap<Integer, LayoutNode> portToTopNode = new LinkedHashMap<>();
        LinkedHashMap<Integer, LinkedHashMap<Integer, LayoutNode>> portToBottomNodeMapping = new LinkedHashMap<>();
        for (LayoutEdge succEdge : succs) {
            int startPort = succEdge.getRelativeFromX();
            LayoutNode fromNode = succEdge.getFrom();
            LayoutNode toNode = succEdge.getTo();

            // edge is longer than one layer => needs dummy nodes
            if (fromNode.getLayer() != toNode.getLayer() - 1) {
                // the edge needs to be cut
                if (maxLayerLength != -1 && toNode.getLayer() - fromNode.getLayer() > maxLayerLength) {
                    // remove the succEdge before replacing it
                    toNode.removePredecessor(succEdge);
                    fromNode.removeSuccessor(succEdge);

                    LayoutNode topCutNode = portToTopNode.get(startPort);
                    if (topCutNode == null) {
                        topCutNode = new LayoutNode();
                        topCutNode.setLayer(fromNode.getLayer() + 1);
                        addDummyToLayer(topCutNode, topCutNode.getLayer());
                        portToTopNode.put(startPort, topCutNode);
                        portToBottomNodeMapping.put(startPort, new LinkedHashMap<>());
                    }
                    LayoutEdge edgeToTopCut = new LayoutEdge(fromNode, topCutNode, succEdge.getRelativeFromX(), topCutNode.getWidth() / 2, succEdge.getLink());
                    if (succEdge.isReversed()) edgeToTopCut.reverse();
                    fromNode.addSuccessor(edgeToTopCut);
                    topCutNode.addPredecessor(edgeToTopCut);

                    LinkedHashMap<Integer, LayoutNode> layerToBottomNode = portToBottomNodeMapping.get(startPort);
                    LayoutNode bottomCutNode = layerToBottomNode.get(toNode.getLayer());
                    if (bottomCutNode == null) {
                        bottomCutNode = new LayoutNode();
                        bottomCutNode.setLayer(toNode.getLayer() - 1);
                        addDummyToLayer(bottomCutNode, bottomCutNode.getLayer());
                        layerToBottomNode.put(toNode.getLayer(), bottomCutNode);
                    }
                    LayoutEdge bottomEdge = new LayoutEdge(bottomCutNode, toNode, bottomCutNode.getWidth() / 2, succEdge.getRelativeToX(), succEdge.getLink());
                    if (succEdge.isReversed()) bottomEdge.reverse();
                    toNode.addPredecessor(bottomEdge);
                    bottomCutNode.addSuccessor(bottomEdge);

                } else { // the edge is not cut, but needs dummy nodes
                    portsToUnprocessedEdges.putIfAbsent(startPort, new ArrayList<>());
                    portsToUnprocessedEdges.get(startPort).add(succEdge);
                }
            }
        }

        for (Map.Entry<Integer, List<LayoutEdge>> portToUnprocessedEdges : portsToUnprocessedEdges.entrySet()) {
            Integer startPort = portToUnprocessedEdges.getKey();
            List<LayoutEdge> unprocessedEdges = portToUnprocessedEdges.getValue();
            unprocessedEdges.sort(Comparator.comparingInt(e -> e.getTo().getLayer()));

            if (unprocessedEdges.size() == 1) {
                // process a single edge
                LayoutEdge singleEdge = unprocessedEdges.get(0);
                LayoutNode fromNode = singleEdge.getFrom();
                if (singleEdge.getTo().getLayer() > fromNode.getLayer() + 1) {
                    LayoutEdge previousEdge = singleEdge;
                    for (int i = fromNode.getLayer() + 1; i < previousEdge.getTo().getLayer(); i++) {
                        LayoutNode dummyNode = new LayoutNode();
                        dummyNode.setLayer(i);
                        dummyNode.addPredecessor(previousEdge);
                        addDummyToLayer(dummyNode, dummyNode.getLayer());
                        LayoutEdge dummyEdge = new LayoutEdge(dummyNode, previousEdge.getTo(), dummyNode.getWidth() / 2, previousEdge.getRelativeToX(), singleEdge.getLink());
                        if (previousEdge.isReversed()) dummyEdge.reverse();
                        dummyNode.addSuccessor(dummyEdge);
                        previousEdge.setRelativeToX(dummyNode.getWidth() / 2);
                        previousEdge.getTo().removePredecessor(previousEdge);
                        previousEdge.getTo().addPredecessor(dummyEdge);
                        previousEdge.setTo(dummyNode);
                        previousEdge = dummyEdge;
                    }
                }
            } else {
                int lastLayer = unprocessedEdges.get(unprocessedEdges.size() - 1).getTo().getLayer();
                int dummyCnt = lastLayer - layoutNode.getLayer() - 1;
                LayoutEdge[] newDummyEdges = new LayoutEdge[dummyCnt];
                LayoutNode[] newDummyNodes = new LayoutNode[dummyCnt];

                newDummyNodes[0] = new LayoutNode();
                newDummyNodes[0].setLayer(layoutNode.getLayer() + 1);
                newDummyEdges[0] = new LayoutEdge(layoutNode, newDummyNodes[0], startPort, newDummyNodes[0].getWidth() / 2, null);
                newDummyNodes[0].addPredecessor(newDummyEdges[0]);
                layoutNode.addSuccessor(newDummyEdges[0]);
                for (int j = 1; j < dummyCnt; j++) {
                    newDummyNodes[j] = new LayoutNode();
                    newDummyNodes[j].setLayer(layoutNode.getLayer() + j + 1);
                    newDummyEdges[j] = new LayoutEdge(newDummyNodes[j - 1], newDummyNodes[j], null);
                    newDummyNodes[j].addPredecessor(newDummyEdges[j]);
                    newDummyNodes[j - 1].addSuccessor(newDummyEdges[j]);
                }
                for (LayoutEdge unprocessedEdge : unprocessedEdges) {
                    LayoutNode anchorNode = newDummyNodes[unprocessedEdge.getTo().getLayer() - layoutNode.getLayer() - 2];
                    anchorNode.addSuccessor(unprocessedEdge);
                    unprocessedEdge.setFrom(anchorNode);
                    unprocessedEdge.setRelativeFromX(anchorNode.getWidth() / 2);
                    layoutNode.removeSuccessor(unprocessedEdge);
                }
                for (LayoutNode dummyNode : newDummyNodes) {
                    addDummyToLayer(dummyNode, dummyNode.getLayer());
                }
            }
        }
    }
}
