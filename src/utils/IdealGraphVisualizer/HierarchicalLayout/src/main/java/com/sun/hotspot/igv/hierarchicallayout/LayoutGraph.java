/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.sun.hotspot.igv.hierarchicallayout.LayoutNode.NODE_POS_COMPARATOR;

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

    private final Set<? extends Link> links;
    private final SortedSet<Vertex> vertices;
    private final HashMap<Vertex, Set<Port>> inputPorts;
    private final HashMap<Vertex, Set<Port>> outputPorts;
    private final HashMap<Port, Set<Link>> portLinks;

    private final List<LayoutNode> dummyNodes;
    private final LinkedHashMap<Vertex, LayoutNode> vertexToLayoutNode;

    private List<LayoutLayer> layers;

    public LayoutGraph(Set<? extends Link> links) {
        this(links, new HashSet<>());
    }

    public void initLayers(int layerCount) {
        layers = new ArrayList<>(layerCount);
        for (int i = 0; i < layerCount; i++) {
            layers.add(new LayoutLayer());
        }
    }

    public List<LayoutNode> getDummyNodes() {
        return Collections.unmodifiableList(dummyNodes);
    }

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

    private void deleteLayer(int layerNr) {
        layers.remove(layerNr);

        // Update the layer field in nodes below the deleted layer
        for (int l = layerNr; l < getLayerCount(); l++) {
            for (LayoutNode layoutNode : getLayer(l)) {
                layoutNode.setLayer(l);
            }
        }
    }


    // check that NO neighbors of node are in a given layer
    // otherwise insert a new layer
    // return the layerNr where the node can now be safely inserted
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

    // inserts a new layer at layerNr
    // inserts dummy nodes acoring to layerNr - 1
    // moves the layer from previous layerNr to layerNr + 1
    private void moveExpandLayerDown(int layerNr) {
        LayoutLayer newLayer =  createNewLayer(layerNr);

        if (layerNr == 0) return;
        LayoutLayer layerAbove = getLayer(layerNr - 1);

        for (LayoutNode fromNode : layerAbove) {
            int fromX = fromNode.getX();
            Map<Integer, List<LayoutEdge>> successorsByX = fromNode.groupSuccessorsByX();
            fromNode.getSuccs().clear();

            for (Map.Entry<Integer, List<LayoutEdge>> entry : successorsByX.entrySet()) {
                Integer relativeFromX = entry.getKey();
                List<LayoutEdge> edges = entry.getValue();
                LayoutNode dummyNode = new LayoutNode();
                dummyNode.setX(fromX + relativeFromX);
                dummyNode.setLayer(layerNr);
                dummyNode.getSuccs().addAll(edges);
                LayoutEdge dummyEdge = new LayoutEdge(fromNode, dummyNode, relativeFromX, 0, edges.get(0).getLink());
                if (edges.get(0).isReversed()) dummyEdge.reverse();

                fromNode.getSuccs().add(dummyEdge);
                dummyNode.getPreds().add(dummyEdge);
                for (LayoutEdge edge : edges) {
                    edge.setFrom(dummyNode);
                }
                addNodeToLayer(dummyNode, layerNr);
            }
        }

        newLayer.sortNodesByXAndSetPositions();
    }

    public List<LayoutLayer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    public int getLayerCount() {
        return layers.size();
    }

    public Collection<LayoutNode> getLayoutNodes() {
        return vertexToLayoutNode.values();
    }

    public LayoutNode getLayoutNode(Vertex vertex) {
        return vertexToLayoutNode.get(vertex);
    }

    public LayoutGraph(Collection<? extends Link> links, Collection<? extends Vertex> additionalVertices) {
        this.links = new HashSet<>(links);

        vertices = new TreeSet<>(additionalVertices);
        portLinks = new HashMap<>(links.size());
        inputPorts = new HashMap<>(links.size());
        outputPorts = new HashMap<>(links.size());

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
        vertexToLayoutNode = new LinkedHashMap<>();
        dummyNodes = new ArrayList<>();


        // Set up nodes
        for (Vertex v : getVertices()) {
            LayoutNode node = new LayoutNode(v);
            vertexToLayoutNode.put(v, node);
        }

        // Set up edges
        List<Link> sortedLinks = new ArrayList<>(links);
        sortedLinks.sort(LINK_COMPARATOR);
        for (Link link : links) {
            createLayoutEdge(link);
        }
    }

    public void addNodeToLayer(LayoutNode node, int layerNumber) {
        node.setLayer(layerNumber);
        getLayer(layerNumber).add(node);

        // Register node in the appropriate collection based on its type
        registerNode(node);
    }

    private void registerNode(LayoutNode node) {
        if (node.isDummy()) {
            dummyNodes.add(node);
        } else {
            vertexToLayoutNode.put(node.getVertex(), node);
        }
    }


    public void removeNode(LayoutNode node) {
        int layer = node.getLayer();
        layers.get(layer).remove(node);
        layers.get(layer).updateLayerPositions();
        // Remove node from graph layout
        if (node.isDummy()) {
            dummyNodes.remove(node);
        } else {
            vertexToLayoutNode.remove(node.getVertex());
        }
    }

    public void updatePositions() {
        for (LayoutLayer layer : layers) {
            layer.updateLayerPositions();
        }
    }

    public LayoutEdge createLayoutEdge(Link link) {
        LayoutEdge edge = new LayoutEdge(
                vertexToLayoutNode.get(link.getFrom().getVertex()),
                vertexToLayoutNode.get(link.getTo().getVertex()),
                link.getFrom().getRelativePosition().x,
                link.getTo().getRelativePosition().x,
                link);
        edge.getFrom().getSuccs().add(edge);
        edge.getTo().getPreds().add(edge);
        return edge;
    }

    public Set<? extends Link> getLinks() {
        return links;
    }

    public SortedSet<Vertex> getVertices() {
        return vertices;
    }

    public boolean containsVertex(Vertex vertex) {
        return vertices.contains(vertex);
    }

    public Set<Vertex> findRootVertices() {
        return vertices.stream()
                .filter(v -> inputPorts.getOrDefault(v, Collections.emptySet()).isEmpty())
                .collect(Collectors.toSet());
    }

    public Set<Link> getInputLinks(Vertex vertex) {
        Set<Link> inputLinks = new HashSet<>();
        for (Port inputPort : inputPorts.getOrDefault(vertex, Collections.emptySet())) {
            inputLinks.addAll(portLinks.getOrDefault(inputPort, Collections.emptySet()));
        }
        return inputLinks;
    }

    public Set<Link> getOutputLinks(Vertex vertex) {
        Set<Link> outputLinks = new HashSet<>();
        for (Port outputPort : outputPorts.getOrDefault(vertex, Collections.emptySet())) {
            outputLinks.addAll(portLinks.getOrDefault(outputPort, Collections.emptySet()));
        }
        return outputLinks;
    }

    private Set<Link> getAllLinks(Vertex vertex) {
        Set<Link> allLinks = new HashSet<>();

        for (Port inputPort : inputPorts.getOrDefault(vertex, Collections.emptySet())) {
            allLinks.addAll(portLinks.getOrDefault(inputPort, Collections.emptySet()));
        }

        for (Port outputPort : outputPorts.getOrDefault(vertex, Collections.emptySet())) {
            allLinks.addAll(portLinks.getOrDefault(outputPort, Collections.emptySet()));
        }

        return allLinks;
    }

    private void removeEdges(LayoutNode movedNode) {
        for (Link inputLink : getAllLinks(movedNode.getVertex())) {
            Vertex from = inputLink.getFrom().getVertex();
            Vertex to = inputLink.getTo().getVertex();
            LayoutNode toNode = getLayoutNode(to);
            LayoutNode fromNode = getLayoutNode(from);

            if (toNode.getLayer() < fromNode.getLayer()) {
                // Reversed edge
                toNode = fromNode;
                toNode.getReversedLinkEndPoints().remove(inputLink);
                fromNode.getReversedLinkStartPoints().remove(inputLink);
            }

            // Remove preds-edges bottom up, starting at "to" node
            // Cannot start from "from" node since there might be joint edges
            List<LayoutEdge> toNodePredsEdges = List.copyOf(toNode.getPreds());
            for (LayoutEdge edge : toNodePredsEdges) {
                LayoutNode predNode = edge.getFrom();
                LayoutEdge edgeToRemove;

                if (edge.getLink() != null && edge.getLink().equals(inputLink)) {
                    toNode.getPreds().remove(edge);
                    edgeToRemove = edge;
                } else {
                    // Wrong edge, look at next
                    continue;
                }

                if (!predNode.isDummy() && predNode.getVertex().equals(from)) {
                    // No dummy nodes inbetween 'from' and 'to' vertex
                    predNode.getSuccs().remove(edgeToRemove);
                    break;
                } else {
                    // Must remove edges between dummy nodes
                    boolean found = true;
                    LayoutNode succNode = toNode;
                    while (predNode.isDummy() && found) {
                        found = false;

                        if (predNode.getSuccs().size() <= 1 && predNode.getPreds().size() <= 1) {
                            // Dummy node used only for this link, remove if not already removed
                            removeNode(predNode);
                        } else {
                            // anchor node, should not be removed
                            break;
                        }

                        if (predNode.getPreds().size() == 1) {
                            predNode.getSuccs().remove(edgeToRemove);
                            succNode = predNode;
                            edgeToRemove = predNode.getPreds().get(0);
                            predNode = edgeToRemove.getFrom();
                            found = true;
                        }
                    }

                    predNode.getSuccs().remove(edgeToRemove);
                    succNode.getPreds().remove(edgeToRemove);
                }
                break;
            }
        }

        // remove link connected to movedNode
        for (Link link : getLinks()) {
            if (link.getTo().getVertex() == movedNode.getVertex()) {
                link.setControlPoints(new ArrayList<>());
                movedNode.getReversedLinkStartPoints().remove(link);
            } else if (link.getFrom().getVertex() == movedNode.getVertex()) {
                link.setControlPoints(new ArrayList<>());
                movedNode.getReversedLinkEndPoints().remove(link);
            }
        }

        movedNode.initSize();
    }

    public void removeNodeAndEdges(LayoutNode node) {
        removeEdges(node);
        removeNode(node);
    }


    public LayoutLayer getLayer(int layerNr) {
        return layers.get(layerNr);
    }

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
            currentY += layer.calculateScalePaddedBottom();
        }
    }

    public void optimizeBackEdgeCrossings() {
        for (LayoutNode node : getLayoutNodes()) {
            if (node.getReversedLinkStartPoints().isEmpty() && node.getReversedLinkEndPoints().isEmpty()) continue;
            node.computeReversedLinkPoints();
        }
    }

    public void removeEmptyLayers() {
        int i = 0;
        while (i < getLayerCount()) {
            LayoutLayer layer = getLayer(i);
            if (layer.isDummyLayer()) {
                removeEmptyLayer(i);
            } else {
                i++; // Move to the next layer only if no removal occurred
            }
        }
    }

    private void removeEmptyLayer(int layerNr) {
        LayoutLayer layer = getLayer(layerNr);
        if (!layer.isDummyLayer()) return;

        for (LayoutNode dummyNode : layer) {
            if (dummyNode.getSuccs().isEmpty()) {
                dummyNode.setLayer(layerNr + 1);
                getLayer(layerNr + 1).add(dummyNode);
                dummyNode.setX(dummyNode.calculateOptimalPositionDown());
                getLayer(layerNr + 1).sortNodesByXAndSetPositions();
                continue;
            } else if (dummyNode.getPreds().isEmpty()) {
                dummyNode.setLayer(layerNr - 1);
                dummyNode.setX(dummyNode.calculateOptimalPositionUp());
                getLayer(layerNr - 1).add(dummyNode);
                getLayer(layerNr - 1).sortNodesByXAndSetPositions();
                continue;
            }
            LayoutEdge layoutEdge = dummyNode.getPreds().get(0);

            // remove the layoutEdge
            LayoutNode fromNode = layoutEdge.getFrom();
            fromNode.getSuccs().remove(layoutEdge);

            List<LayoutEdge> successorEdges = dummyNode.getSuccs();
            for (LayoutEdge successorEdge : successorEdges) {
                successorEdge.setRelativeFromX(layoutEdge.getRelativeFromX());
                successorEdge.setFrom(fromNode);
                fromNode.getSuccs().add(successorEdge);
            }
            dummyNode.getPreds().clear();
            dummyNode.getSuccs().clear();
            dummyNodes.remove(dummyNode);
        }

        deleteLayer(layerNr);
    }

    /**
     * Repositions the given LayoutNode to the specified x-coordinate within its layer,
     * ensuring no overlap with adjacent nodes and maintaining a minimum NODE_OFFSET distance.
     *
     * @param layoutNode  The LayoutNode to be repositioned.
     * @param newX        The desired new x-coordinate for the layoutNode.
     */
    private void repositionLayoutNodeX(LayoutNode layoutNode, int newX) {
        int currentX = layoutNode.getX();

        // Early exit if the desired position is the same as the current position
        if (newX == currentX) {
            return;
        }

        LayoutLayer layer = getLayer(layoutNode.getLayer());
        if (newX > currentX) {
            layer.attemptMoveRight(layoutNode, newX);
        } else {
            layer.attemptMoveLeft(layoutNode, newX);
        }
    }

    /**
     * Aligns the x-coordinate of a single dummy successor node for the given LayoutNode.
     * If the node has exactly one successor and that successor is a dummy node,
     * this method sets the dummy node's x-coordinate to either the node's x-coordinate
     * (if the node is a dummy) or to the starting x-coordinate of the connecting edge.
     *
     * @param node The LayoutNode whose single dummy successor needs to be aligned.
     */
    private void alignSingleSuccessorDummyNodeX(LayoutNode node) {
        // Retrieve the list of successor edges
        List<LayoutEdge> successors = node.getSuccs();

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
     * Aligns the x-coordinates of dummy successor nodes across all layers.
     * Performs alignment in both forward and backward directions for comprehensive coverage.
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

}
