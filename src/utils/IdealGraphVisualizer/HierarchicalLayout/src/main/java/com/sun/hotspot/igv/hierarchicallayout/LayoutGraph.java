/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.sun.hotspot.igv.hierarchicallayout.LayoutNode.NODE_POS_COMPARATOR;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Port;
import com.sun.hotspot.igv.layout.Vertex;
import java.util.*;
import java.util.stream.Collectors;


/**
 * The LayoutGraph class is responsible for organizing and arranging a graph's nodes and edges for visual display.
 * It takes a collection of nodes (Vertex) and connections between them (Link) and structures them into layers,
 * creating a hierarchical layout. The class handles complexities like edges that span multiple layers
 * by inserting temporary "dummy" nodes to maintain a clear hierarchy.
 * This organization helps ensure that when the graph is displayed, it is easy to understand and visually coherent,
 * making the relationships between nodes clear and straightforward.
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
    private final LinkedHashMap<Vertex, Set<Port>> outputPorts;
    private final LinkedHashMap<Port, Set<Link>> portLinks;

    // Layout Management: LayoutNodes and LayoutLayers
    private final LinkedHashMap<Vertex, LayoutNode> layoutNodes;
    private final List<LayoutNode> dummyNodes;
    private final List<LayoutLayer> layers;
    private boolean showSelfEdges = false;

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
        portLinks = new LinkedHashMap<>(links.size());
        inputPorts = new LinkedHashMap<>(links.size());
        outputPorts = new LinkedHashMap<>(links.size());

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

    public boolean showSelfEdges() {
        return showSelfEdges;
    }

    public void setShowSelfEdges(boolean showSelfEdges) {
        this.showSelfEdges = showSelfEdges;
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
     * Creates a new layer at the specified index in the layers list.
     * Adjusts the layer numbers of existing nodes in layers below the inserted layer.
     *
     * @param layerNr The index at which to insert the new layer.
     * @return The newly created LayoutLayer.
     */
    private LayoutLayer createNewLayer(int layerNr) {
        LayoutLayer layer = new LayoutLayer();
        layers.add(layerNr, layer);

        // update layer field in nodes below layerNr
        for (int l = layerNr + 1; l < getLayerCount(); l++) {
            for (LayoutNode layoutNode : getLayer(l)) {
                layoutNode.setLayer(l);
            }
        }
        return layer;
    }

    /**
     * Deletes the layer at the specified index.
     * Adjusts the layer numbers of existing nodes in layers below the deleted layer.
     *
     * @param layerNr The index of the layer to delete.
     */
    private void deleteLayer(int layerNr) {
        layers.remove(layerNr);

        // Update the layer field in nodes below the deleted layer
        for (int l = layerNr; l < getLayerCount(); l++) {
            for (LayoutNode layoutNode : getLayer(l)) {
                layoutNode.setLayer(l);
            }
        }
    }

    /**
     * Ensures that no neighboring nodes of the specified node are in the same layer.
     * If any neighbor is found in the specified layer, inserts a new layer to avoid conflicts.
     * Returns the adjusted layer number where the node can be safely inserted.
     *
     * @param node    The LayoutNode to check and possibly reposition.
     * @param layerNr The proposed layer number for the node.
     * @return The layer number where the node can be safely inserted after adjustments.
     */
    public int insertNewLayerIfNeeded(LayoutNode node, int layerNr) {
        for (Link inputLink : getInputLinks(node.getVertex())) {
            if (inputLink.getFrom().getVertex() == inputLink.getTo().getVertex()) continue;
            LayoutNode fromNode = getLayoutNode(inputLink.getFrom().getVertex());
            if (fromNode.getLayer() == layerNr) {
                moveExpandLayerDown(layerNr + 1);
                return layerNr + 1;
            }
        }
        for (Link outputLink : getOutputLinks(node.getVertex())) {
            if (outputLink.getFrom().getVertex() == outputLink.getTo().getVertex()) continue;
            LayoutNode toNode = getLayoutNode(outputLink.getTo().getVertex());
            if (toNode.getLayer() == layerNr) {
                moveExpandLayerDown(layerNr);
                return layerNr;
            }
        }
        return layerNr;

    }

    /**
     * Inserts a new layer at the specified index and adjusts nodes and edges accordingly.
     * Moves existing nodes and their successors down to accommodate the new layer.
     *
     * @param layerNr The index at which to insert the new layer.
     */
    private void moveExpandLayerDown(int layerNr) {
        LayoutLayer newLayer = createNewLayer(layerNr);

        if (layerNr == 0) return;
        LayoutLayer layerAbove = getLayer(layerNr - 1);

        for (LayoutNode fromNode : layerAbove) {
            int fromX = fromNode.getX();
            Map<Integer, List<LayoutEdge>> successorsByX = fromNode.groupSuccessorsByX();
            fromNode.clearSuccessors();

            for (Map.Entry<Integer, List<LayoutEdge>> entry : successorsByX.entrySet()) {
                Integer relativeFromX = entry.getKey();
                List<LayoutEdge> edges = entry.getValue();
                LayoutNode dummyNode = new LayoutNode();
                dummyNode.setX(fromX + relativeFromX);
                dummyNode.setLayer(layerNr);
                for (LayoutEdge edge : edges) {
                    dummyNode.addSuccessor(edge);
                }
                LayoutEdge dummyEdge = new LayoutEdge(fromNode, dummyNode, relativeFromX, 0, edges.get(0).getLink());
                if (edges.get(0).isReversed()) dummyEdge.reverse();

                fromNode.addSuccessor(dummyEdge);
                dummyNode.addPredecessor(dummyEdge);
                for (LayoutEdge edge : edges) {
                    edge.setFrom(dummyNode);
                }
                addDummyToLayer(dummyNode, layerNr);
            }
        }

        newLayer.sortNodesByX();
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
     * Retrieves the LayoutNode associated with the specified Vertex.
     *
     * @param vertex The vertex whose LayoutNode is to be retrieved.
     * @return The LayoutNode corresponding to the given vertex, or null if not found.
     */
    public LayoutNode getLayoutNode(Vertex vertex) {
        return layoutNodes.get(vertex);
    }

    /**
     * Adds a LayoutNode to the specified layer and registers it in the graph.
     *
     * @param node        The LayoutNode to add to the layer.
     * @param layerNumber The index of the layer to which the node will be added.
     */
    public void addNodeToLayer(LayoutNode node, int layerNumber) {
        assert !node.isDummy();
        node.setLayer(layerNumber);
        getLayer(layerNumber).add(node);
        if (!layoutNodes.containsKey(node.getVertex())) {
            layoutNodes.put(node.getVertex(), node);
        }
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
     * Retrieves all incoming links to the specified vertex.
     *
     * @param vertex The vertex whose incoming links are to be retrieved.
     * @return A set of links that are incoming to the vertex.
     */
    public List<Link> getInputLinks(Vertex vertex) {
        List<Link> inputLinks = new ArrayList<>();
        for (Port inputPort : inputPorts.getOrDefault(vertex, Collections.emptySet())) {
            inputLinks.addAll(portLinks.getOrDefault(inputPort, Collections.emptySet()));
        }
        return inputLinks;
    }

    /**
     * Retrieves all outgoing links from the specified vertex.
     *
     * @param vertex The vertex whose outgoing links are to be retrieved.
     * @return A set of links that are outgoing from the vertex.
     */
    public List<Link> getOutputLinks(Vertex vertex) {
        List<Link> outputLinks = new ArrayList<>();
        for (Port outputPort : outputPorts.getOrDefault(vertex, Collections.emptySet())) {
            outputLinks.addAll(portLinks.getOrDefault(outputPort, Collections.emptySet()));
        }
        return outputLinks;
    }

    public List<Link> getAllLinks(Vertex vertex) {
        List<Link> allLinks = new ArrayList<>();

        for (Port inputPort : inputPorts.getOrDefault(vertex, Collections.emptySet())) {
            allLinks.addAll(portLinks.getOrDefault(inputPort, Collections.emptySet()));
        }

        for (Port outputPort : outputPorts.getOrDefault(vertex, Collections.emptySet())) {
            allLinks.addAll(portLinks.getOrDefault(outputPort, Collections.emptySet()));
        }

        return allLinks;
    }

    /**
     * Removes the specified LayoutNode and all its connected edges from the graph.
     *
     * @param node The LayoutNode to remove along with its edges.
     */
    public void removeNodeAndEdges(LayoutNode node) {
        assert !node.isDummy();
        removeEdges(node); // a node can only be removed together with its edges
        int layer = node.getLayer();
        layers.get(layer).remove(node);
        layers.get(layer).updateNodeIndices();
        layoutNodes.remove(node.getVertex());
    }

    /**
     * Removes all edges connected to the specified LayoutNode.
     * Handles the removal of associated dummy nodes if they are no longer needed.
     * Updates the graph structure accordingly after node movement.
     *
     * @param node The LayoutNode whose connected edges are to be removed.
     */
    public void removeEdges(LayoutNode node) {
        assert !node.isDummy();
        for (Link link : getAllLinks(node.getVertex())) {
            removeEdge(link);
        }
    }

    public void removeEdge(Link link) {
        Vertex from = link.getFrom().getVertex();
        Vertex to = link.getTo().getVertex();
        LayoutNode toNode = getLayoutNode(to);
        LayoutNode fromNode = getLayoutNode(from);

        if (toNode.getLayer() < fromNode.getLayer()) {
            // Reversed edge
            toNode = fromNode;
        }

        // Remove preds-edges bottom up, starting at "to" node
        // Cannot start from "from" node since there might be joint edges
        List<LayoutEdge> toNodePredsEdges = List.copyOf(toNode.getPredecessors());
        for (LayoutEdge edge : toNodePredsEdges) {
            LayoutNode predNode = edge.getFrom();
            LayoutEdge edgeToRemove;

            if (edge.getLink() != null && edge.getLink().equals(link)) {
                toNode.removePredecessor(edge);
                edgeToRemove = edge;
            } else {
                // Wrong edge, look at next
                continue;
            }

            if (!predNode.isDummy() && predNode.getVertex().equals(from)) {
                // No dummy nodes inbetween 'from' and 'to' vertex
                predNode.removeSuccessor(edgeToRemove);
                break;
            } else {
                // Must remove edges between dummy nodes
                boolean found = true;
                LayoutNode succNode = toNode;
                while (predNode.isDummy() && found) {
                    found = false;

                    if (predNode.getSuccessors().size() <= 1 && predNode.getPredecessors().size() <= 1) {
                        // Dummy node used only for this link, remove if not already removed
                        assert predNode.isDummy();
                        int layer = predNode.getLayer();
                        layers.get(layer).remove(predNode);
                        layers.get(layer).updateNodeIndices();
                        dummyNodes.remove(predNode);
                    } else {
                        // anchor node, should not be removed
                        break;
                    }

                    if (predNode.getPredecessors().size() == 1) {
                        predNode.removeSuccessor(edgeToRemove);
                        succNode = predNode;
                        edgeToRemove = predNode.getPredecessors().get(0);
                        predNode = edgeToRemove.getFrom();
                        found = true;
                    }
                }

                predNode.removeSuccessor(edgeToRemove);
                succNode.removePredecessor(edgeToRemove);
            }
            break;
        }

        if (fromNode.getReversedLinkStartPoints().containsKey(link)) {
            fromNode.computeReversedLinkPoints(false);
        }
        if (toNode.getReversedLinkStartPoints().containsKey(link)) {
            toNode.computeReversedLinkPoints(false);
        }
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
     * Finds the layer closest to the given y-coordinate.
     *
     * @param y the y-coordinate to check
     * @return the index of the optimal layer, or -1 if no layers are found
     */
    public int findLayer(int y) {
        int optimalLayer = -1;
        int minDistance = Integer.MAX_VALUE;
        for (int l = 0; l < getLayerCount(); l++) {
            // Check if y is within this layer's bounds
            if (y >= getLayer(l).getTop() && y <= getLayer(l).getBottom()) {
                return l;
            }

            int distance = Math.abs(getLayer(l).getCenter() - y);
            if (distance < minDistance) {
                minDistance = distance;
                optimalLayer = l;
            }
        }
        return optimalLayer;
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
     * Optimizes routing of reversed (back) edges to reduce crossings.
     */
    public void optimizeBackEdgeCrossings() {
        for (LayoutNode node : getLayoutNodes()) {
            node.optimizeBackEdgeCrossing();
        }
    }

    /**
     * Removes empty layers from the graph.
     * Iteratively checks for and removes layers that contain only dummy nodes.
     */
    public void removeEmptyLayers() {
        int i = 0;
        while (i < getLayerCount()) {
            LayoutLayer layer = getLayer(i);
            if (layer.containsOnlyDummyNodes()) {
                removeEmptyLayer(i);
            } else {
                i++; // Move to the next layer only if no removal occurred
            }
        }
    }

    /**
     * Removes the layer at the specified index if it is empty or contains only dummy nodes.
     * Adjusts the positions of nodes and edges accordingly.
     *
     * @param layerNr The index of the layer to remove.
     */
    private void removeEmptyLayer(int layerNr) {
        LayoutLayer layer = getLayer(layerNr);
        if (!layer.containsOnlyDummyNodes()) return;

        for (LayoutNode dummyNode : layer) {
            if (dummyNode.getSuccessors().isEmpty()) {
                dummyNode.setLayer(layerNr + 1);
                getLayer(layerNr + 1).add(dummyNode);
                dummyNode.setX(dummyNode.calculateOptimalXFromPredecessors(true));
                getLayer(layerNr + 1).sortNodesByX();
                continue;
            } else if (dummyNode.getPredecessors().isEmpty()) {
                dummyNode.setLayer(layerNr - 1);
                dummyNode.setX(dummyNode.calculateOptimalXFromSuccessors(true));
                getLayer(layerNr - 1).add(dummyNode);
                getLayer(layerNr - 1).sortNodesByX();
                continue;
            }
            LayoutEdge layoutEdge = dummyNode.getPredecessors().get(0);

            // remove the layoutEdge
            LayoutNode fromNode = layoutEdge.getFrom();
            fromNode.removeSuccessor(layoutEdge);

            List<LayoutEdge> successorEdges = dummyNode.getSuccessors();
            for (LayoutEdge successorEdge : successorEdges) {
                successorEdge.setRelativeFromX(layoutEdge.getRelativeFromX());
                successorEdge.setFrom(fromNode);
                fromNode.addSuccessor(successorEdge);
            }
            dummyNode.clearPredecessors();
            dummyNode.clearSuccessors();
            dummyNodes.remove(dummyNode);
        }

        deleteLayer(layerNr);
    }

    /**
     * Repositions the specified LayoutNode horizontally within its layer to the new x-coordinate.
     * Ensures no overlap with adjacent nodes and maintains minimum spacing.
     *
     * @param layoutNode The LayoutNode to reposition.
     * @param newX       The new x-coordinate to set for the node.
     */
    private void repositionLayoutNodeX(LayoutNode layoutNode, int newX) {
        int currentX = layoutNode.getX();

        // Early exit if the desired position is the same as the current position
        if (newX == currentX) {
            return;
        }

        LayoutLayer layer = getLayer(layoutNode.getLayer());
        if (newX > currentX) {
            layer.tryShiftNodeRight(layoutNode, newX);
        } else {
            layer.tryShiftNodeLeft(layoutNode, newX);
        }
    }

    /**
     * Aligns the x-coordinate of a single dummy successor node for the given LayoutNode.
     * If the node has exactly one successor and that successor is a dummy node,
     * sets the dummy node's x-coordinate to align with the current node or the edge's starting point.
     *
     * @param node The LayoutNode whose dummy successor is to be aligned.
     */
    private void alignSingleSuccessorDummyNodeX(LayoutNode node) {
        // Retrieve the list of successor edges
        List<LayoutEdge> successors = node.getSuccessors();

        // Proceed only if there is exactly one successor
        if (successors.size() != 1) {
            return;
        }

        LayoutEdge successorEdge = successors.get(0);
        LayoutNode successorNode = successorEdge.getTo();

        // Proceed only if the successor node is a dummy node
        if (!successorNode.isDummy()) {
            return;
        }

        // Determine the target x-coordinate based on whether the current node is a dummy
        int targetX = node.isDummy() ? node.getX() : successorEdge.getStartX();

        // Align the successor dummy node to the target x-coordinate
        repositionLayoutNodeX(successorNode, targetX);
    }

    /**
     * Aligns the x-coordinates of dummy successor nodes within the specified layer.
     * Performs alignment in both forward and backward directions to ensure consistency.
     *
     * @param layer The LayoutLayer whose nodes' dummy successors need alignment.
     */
    private void alignLayerDummySuccessors(LayoutLayer layer) {
        // Forward pass: Align dummy successors from the first node to the last.
        for (LayoutNode node : layer) {
            alignSingleSuccessorDummyNodeX(node);
        }

        // Backward pass: Align dummy successors from the last node to the first.
        for (int i = layer.size() - 1; i >= 0; i--) {
            LayoutNode node = layer.get(i);
            alignSingleSuccessorDummyNodeX(node);
        }
    }

    /**
     * Straightens edges in the graph by aligning dummy nodes to reduce bends.
     * Processes all layers to align dummy successor nodes.
     */
    public void straightenEdges() {
        // Forward pass: Align dummy successors from the first layer to the last.
        for (int i = 0; i < getLayerCount(); i++) {
            alignLayerDummySuccessors(getLayer(i));
        }

        // Backward pass: Align dummy successors from the last layer to the first.
        for (int i = getLayerCount() - 1; i >= 0; i--) {
            alignLayerDummySuccessors(getLayer(i));
        }
    }

    /**
     * Updates the minimum X spacing for all layers in the graph.
     */
    public void updateLayerMinXSpacing() {
        for (LayoutLayer layer : this.getLayers()) {
            layer.updateMinXSpacing(false);
        }
    }

    /**
     * Calculates the optimal horizontal position (index) for the specified node within the given layer,
     * aiming to minimize the number of edge crossings.
     *
     * @param node    The node to position.
     * @param layerNr The index of the layer in which to position the node.
     * @return The optimal position index within the layer for the node.
     */
    private int optimalPosition(LayoutNode node, int layerNr) {
        getLayer(layerNr).sort(NODE_POS_COMPARATOR);
        int edgeCrossings = Integer.MAX_VALUE;
        int optimalPos = -1;

        // Try each possible position in the layerNr
        for (int i = 0; i < getLayer(layerNr).size() + 1; i++) {
            int xCoord;
            if (i == 0) {
                xCoord = getLayer(layerNr).get(i).getX() - node.getWidth() - 1;
            } else {
                xCoord = getLayer(layerNr).get(i - 1).getX() + getLayer(layerNr).get(i - 1).getWidth() + 1;
            }

            int currentCrossings = 0;

            if (0 <= layerNr - 1) {
                // For each link with an end point in vertex, check how many edges cross it
                for (LayoutEdge edge : node.getPredecessors()) {
                    if (edge.getFrom().getLayer() == layerNr - 1) {
                        int fromNodeXCoord = edge.getFromX();
                        int toNodeXCoord = xCoord;
                        if (!node.isDummy()) {
                            toNodeXCoord += edge.getRelativeToX();
                        }
                        for (LayoutNode n : getLayer(layerNr - 1)) {
                            for (LayoutEdge e : n.getSuccessors()) {
                                if (e.getTo() == null) {
                                    continue;
                                }
                                int compFromXCoord = e.getFromX();
                                int compToXCoord = e.getToX();
                                if ((fromNodeXCoord > compFromXCoord && toNodeXCoord < compToXCoord)
                                        || (fromNodeXCoord < compFromXCoord
                                        && toNodeXCoord > compToXCoord)) {
                                    currentCrossings += 1;
                                }
                            }
                        }
                    }
                }
            }
            // Edge crossings across current layerNr and layerNr below
            if (layerNr + 1 < getLayerCount()) {
                // For each link with an end point in vertex, check how many edges cross it
                for (LayoutEdge edge : node.getSuccessors()) {
                    if (edge.getTo().getLayer() == layerNr + 1) {
                        int toNodeXCoord = edge.getToX();
                        int fromNodeXCoord = xCoord;
                        if (!node.isDummy()) {
                            fromNodeXCoord += edge.getRelativeFromX();
                        }
                        for (LayoutNode n : getLayer(layerNr + 1)) {
                            for (LayoutEdge e : n.getPredecessors()) {
                                if (e.getFrom() == null) {
                                    continue;
                                }
                                int compFromXCoord = e.getFromX();
                                int compToXCoord = e.getToX();
                                if ((fromNodeXCoord > compFromXCoord && toNodeXCoord < compToXCoord)
                                        || (fromNodeXCoord < compFromXCoord
                                        && toNodeXCoord > compToXCoord)) {
                                    currentCrossings += 1;
                                }
                            }
                        }
                    }
                }
            }
            if (currentCrossings <= edgeCrossings) {
                edgeCrossings = currentCrossings;
                optimalPos = i;
            }
        }
        return optimalPos;
    }

    /**
     * Creates layout edges for the specified node and reverses edges as needed.
     * Reverses edges that go from lower to higher layers to maintain proper layering.
     *
     * @param node The LayoutNode for which to create and reverse edges.
     */
    public void createAndReverseLayoutEdges(LayoutNode node) {
        List<Link> nodeLinks = new ArrayList<>(getInputLinks(node.getVertex()));
        nodeLinks.addAll(getOutputLinks(node.getVertex()));
        nodeLinks.sort(LINK_COMPARATOR);

        List<LayoutNode> reversedLayoutNodes = new ArrayList<>();
        for (Link link : nodeLinks) {
            if (link.getFrom().getVertex() == link.getTo().getVertex()) continue;
            LayoutEdge layoutEdge = createLayoutEdge(link);

            LayoutNode fromNode = layoutEdge.getFrom();
            LayoutNode toNode = layoutEdge.getTo();

            if (fromNode.getLayer() > toNode.getLayer()) {
                HierarchicalLayoutManager.ReverseEdges.reverseEdge(layoutEdge);
                reversedLayoutNodes.add(fromNode);
                reversedLayoutNodes.add(toNode);
            }
        }

        // ReverseEdges
        for (LayoutNode layoutNode : reversedLayoutNodes) {
            layoutNode.computeReversedLinkPoints(false);
        }
    }

    /**
     * Inserts dummy nodes along the edges from predecessors of the specified node,
     * for edges that span more than one layer.
     *
     * @param layoutNode The node for which to create predecessor dummy nodes.
     */
    public void createDummiesForNodePredecessor(LayoutNode layoutNode) {
        for (LayoutEdge predEdge : layoutNode.getPredecessors()) {
            LayoutNode fromNode = predEdge.getFrom();
            LayoutNode toNode = predEdge.getTo();
            if (Math.abs(toNode.getLayer() - fromNode.getLayer()) <= 1) continue;

            boolean hasEdgeFromSamePort = false;
            LayoutEdge edgeFromSamePort = new LayoutEdge(fromNode, toNode, predEdge.getLink());
            if (predEdge.isReversed()) edgeFromSamePort.reverse();

            for (LayoutEdge succEdge : fromNode.getSuccessors()) {
                if (succEdge.getRelativeFromX() == predEdge.getRelativeFromX() && succEdge.getTo().isDummy()) {
                    edgeFromSamePort = succEdge;
                    hasEdgeFromSamePort = true;
                    break;
                }
            }

            if (hasEdgeFromSamePort) {
                LayoutEdge curEdge = edgeFromSamePort;
                boolean newEdge = true;
                while (curEdge.getTo().getLayer() < toNode.getLayer() - 1 && curEdge.getTo().isDummy() && newEdge) {
                    // Traverse down the chain of dummy nodes linking together the edges originating
                    // from the same port
                    newEdge = false;
                    if (curEdge.getTo().getSuccessors().size() == 1) {
                        curEdge = curEdge.getTo().getSuccessors().get(0);
                        newEdge = true;
                    } else {
                        for (LayoutEdge e : curEdge.getTo().getSuccessors()) {
                            if (e.getTo().isDummy()) {
                                curEdge = e;
                                newEdge = true;
                                break;
                            }
                        }
                    }
                }

                LayoutNode prevDummy;
                if (!curEdge.getTo().isDummy()) {
                    prevDummy = curEdge.getFrom();
                } else {
                    prevDummy = curEdge.getTo();
                }

                predEdge.setFrom(prevDummy);
                predEdge.setRelativeFromX(prevDummy.getWidth() / 2);
                fromNode.removeSuccessor(predEdge);
                prevDummy.addSuccessor(predEdge);
            }

            LayoutNode layoutNode1 = predEdge.getTo();
            if (predEdge.getTo().getLayer() - 1 > predEdge.getFrom().getLayer()) {
                LayoutEdge prevEdge = predEdge;
                for (int l = layoutNode1.getLayer() - 1; l > prevEdge.getFrom().getLayer(); l--) {
                    LayoutNode dummyNode = new LayoutNode();
                    dummyNode.addSuccessor(prevEdge);
                    LayoutEdge result = new LayoutEdge(prevEdge.getFrom(), dummyNode, prevEdge.getRelativeFromX(), 0, prevEdge.getLink());
                    if (prevEdge.isReversed()) result.reverse();
                    dummyNode.addPredecessor(result);
                    prevEdge.setRelativeFromX(0);
                    prevEdge.getFrom().removeSuccessor(prevEdge);
                    prevEdge.getFrom().addSuccessor(result);
                    prevEdge.setFrom(dummyNode);
                    dummyNode.setLayer(l);
                    List<LayoutNode> layerNodes = getLayer(l);
                    if (layerNodes.isEmpty()) {
                        dummyNode.setPos(0);
                    } else {
                        dummyNode.setPos(optimalPosition(dummyNode, l));
                    }
                    for (LayoutNode n : layerNodes) {
                        if (n.getPos() >= dummyNode.getPos()) {
                            n.setPos(n.getPos() + 1);
                        }
                    }
                    addDummyToLayer(dummyNode, l);
                    prevEdge = dummyNode.getPredecessors().get(0);
                }
            }
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

    /**
     * Adds edges connected to the specified node, including any necessary dummy nodes.
     * Handles edge reversal, dummy node insertion for both predecessors and successors,
     * and updates node positions accordingly.
     *
     * @param node           The LayoutNode to which edges will be added.
     * @param maxLayerLength The maximum number of layers an edge can span without splitting it
     */
    public void addEdges(LayoutNode node, int maxLayerLength) {
        assert !node.isDummy();
        createAndReverseLayoutEdges(node);
        createDummiesForNodeSuccessor(node, maxLayerLength);
        createDummiesForNodePredecessor(node);
        updatePositions();
    }
}
