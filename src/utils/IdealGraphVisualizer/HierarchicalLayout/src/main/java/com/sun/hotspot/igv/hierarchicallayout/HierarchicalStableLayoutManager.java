/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.sun.hotspot.igv.hierarchicallayout.LayoutManager.LAYER_OFFSET;
import static com.sun.hotspot.igv.hierarchicallayout.LayoutManager.NODE_OFFSET;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.Dimension;
import java.awt.Point;
import java.util.*;

public class HierarchicalStableLayoutManager {

    // Algorithm global data structures
    private Set<? extends Vertex> currentVertices;
    private Set<? extends Link> currentLinks;
    private Set<Link> reversedLinks;
    private List<LayoutNode> nodes;
    private final HashMap<Vertex, LayoutNode> vertexToLayoutNode;
    private HashMap<Integer, LayoutLayer> layers;

    private final HierarchicalLayoutManager manager;
    private HashMap<Vertex, VertexAction> vertexToAction;
    private List<VertexAction> vertexActions;
    private List<LinkAction> linkActions;
    private Set<? extends Vertex> oldVertices;
    private Set<? extends Link> oldLinks;
    private boolean shouldRedrawLayout = true;
    private boolean shouldRemoveEmptyLayers = true;
    private boolean cutEdges = false;

    public void doLayout(LayoutGraph layoutGraph) {
        boolean oldShouldRedrawLayout = shouldRedrawLayout;
        setShouldRedrawLayout(true);
        updateLayout(layoutGraph.getVertices(), layoutGraph.getLinks());
        setShouldRedrawLayout(oldShouldRedrawLayout);
    }


    enum Action {
        ADD,
        REMOVE
    }

    private static class VertexAction {
        public Vertex vertex;
        public List<LinkAction> linkActions = new LinkedList<>();
        public Action action;

        public VertexAction(Vertex vertex, Action action) {
            this.vertex = vertex;
            this.action = action;
        }
    }

    private static class LinkAction {
        public Link link;
        public Action action;

        public LinkAction(Link link, Action action) {
            this.link = link;
            this.action = action;
        }
    }

    public HierarchicalStableLayoutManager() {
        oldVertices = new HashSet<>();
        oldLinks = new HashSet<>();
        manager = new HierarchicalLayoutManager();
        vertexToLayoutNode = new HashMap<>();
        nodes = new ArrayList<>();
    }

    public void setCutEdges(boolean enable) {
        cutEdges = enable;
        manager.setCutEdges(enable);
    }

    public boolean getCutEdges() {
        return cutEdges;
    }

    private int calculateOptimalBoth(LayoutNode n) {
        if (n.getPredecessors().isEmpty() && n.getSuccessors().isEmpty()) {
            return n.getX();
        }

        int[] values = new int[n.getPredecessors().size() + n.getSuccessors().size()];
        int i = 0;

        for (LayoutEdge e : n.getPredecessors()) {
            values[i] = e.getFrom().getX() + e.getRelativeFromX() - e.getRelativeToX();
            i++;
        }

        for (LayoutEdge e : n.getSuccessors()) {
            values[i] = e.getTo().getX() + e.getRelativeToX() - e.getRelativeFromX();
            i++;
        }

        return median(values);
    }

    public static int median(int[] values) {
        Arrays.sort(values);
        if (values.length % 2 == 0) {
            return (values[values.length / 2 - 1] + values[values.length / 2]) / 2;
        } else {
            return values[values.length / 2];
        }
    }

    public static final Comparator<LayoutNode> nodeProcessingUpComparator = (n1, n2) -> {
        if (n1.getVertex() == null) {
            if (n2.getVertex() == null) {
                return 0;
            }
            return -1;
        }
        if (n2.getVertex() == null) {
            return 1;
        }
        return n1.getSuccessors().size() - n2.getSuccessors().size();
    };

    /**
     * Adjust the X-coordinates of the nodes in the given layer, as a new node has
     * been inserted at that layer
     */
    private void adjustXCoordinates(int layer) {
        List<LayoutNode> nodes = layers.get(layer);
        ArrayList<Integer> space = new ArrayList<>();
        List<LayoutNode> nodeProcessingOrder = new ArrayList<>();

        nodes.sort(Comparator.comparingInt(LayoutNode::getPos));

        int curX = 0;
        for (LayoutNode n : nodes) {
            space.add(curX);
            curX += n.getOuterWidth() + NODE_OFFSET;
            nodeProcessingOrder.add(n);
        }

        nodeProcessingOrder.sort(nodeProcessingUpComparator);
        HierarchicalLayoutManager.NodeRow r = new HierarchicalLayoutManager.NodeRow(space);
        for (LayoutNode n : nodeProcessingOrder) {
            int optimal = calculateOptimalBoth(n);
            r.insert(n, optimal);
        }
    }

    private void ensureNeighborEdgeConsistency() {
        for (LayoutNode n : nodes) {
            n.getSuccessorsRaw().removeIf(e -> !nodes.contains(e.getTo()));
            n.getPredecessorsRaw().removeIf(e -> !nodes.contains(e.getFrom()));
        }
    }

    private static final Comparator<VertexAction> vertexActionComparator = (a1, a2) -> {
        if (a1.action == Action.REMOVE) {
            if (a2.action == Action.REMOVE) {
                return a2.linkActions.size() - a1.linkActions.size();
            }
            return -1;
        }
        if (a2.action == Action.REMOVE) {
            return 1;
        }

        return a1.linkActions.size() - a2.linkActions.size();
    };

    private void generateActions() {
        HashSet<Link> oldLinks = new HashSet<>(this.oldLinks);

        HashSet<Vertex> addedVertices = new HashSet<>(currentVertices);
        addedVertices.removeAll(oldVertices);

        HashSet<Vertex> removedVertices = new HashSet<>(oldVertices);
        removedVertices.removeAll(currentVertices);

        HashSet<Link> addedLinks = new HashSet<>(currentLinks);
        HashSet<Link> removedLinks = new HashSet<>(oldLinks);
        for (Link link1 : currentLinks) {
            for (Link link2 : oldLinks) {
                if (link1.equals(link2)) {
                    addedLinks.remove(link1);
                    removedLinks.remove(link2);
                    break;
                }
            }
        }

        for (Vertex v : addedVertices) {
            VertexAction a = new VertexAction(v, Action.ADD);
            vertexActions.add(a);
            vertexToAction.put(v, a);
        }

        for (Vertex v : removedVertices) {
            VertexAction a = new VertexAction(v, Action.REMOVE);
            vertexActions.add(a);
            vertexToAction.put(v, a);
        }

        for (Link l : addedLinks) {
            Vertex to = l.getTo().getVertex();
            Vertex from = l.getFrom().getVertex();
            LinkAction a = new LinkAction(l, Action.ADD);

            if (addedVertices.contains(to)) {
                vertexToAction.get(to).linkActions.add(a);
            }
            if (addedVertices.contains(from)) {
                vertexToAction.get(from).linkActions.add(a);
            }
            if (!addedVertices.contains(to) && !addedVertices.contains(from)) {
                linkActions.add(a);
            }
        }

        for (Link l : removedLinks) {
            Vertex to = l.getTo().getVertex();
            Vertex from = l.getFrom().getVertex();
            LinkAction a = new LinkAction(l, Action.REMOVE);

            if (removedVertices.contains(to)) {
                vertexToAction.get(to).linkActions.add(a);
            }
            if (removedVertices.contains(from)) {
                vertexToAction.get(from).linkActions.add(a);
            }
            if (!removedVertices.contains(to) && !removedVertices.contains(from)) {
                linkActions.add(a);
            }
        }

        vertexActions.sort(vertexActionComparator);
    }

    private void findInitialReversedLinks() {
        for (Link link : oldLinks) {
            for (Link l : currentLinks) {
                if (l.equals(link)) {
                    if (vertexToLayoutNode.get(l.getFrom().getVertex()).getLayer() > vertexToLayoutNode
                            .get(l.getTo().getVertex()).getLayer()) {
                        // Link is reversed
                        reversedLinks.add(l);
                        updateReversedLinkPositions(l);
                    }
                }
            }
        }
    }

    private void updateReversedLinkPositions(Link link) {
        LayoutNode fromNode = vertexToLayoutNode.get(link.getFrom().getVertex());
        LayoutNode toNode = vertexToLayoutNode.get(link.getTo().getVertex());
        // Correct direction, is reversed link
        assert fromNode != null && toNode != null;
        assert nodes.contains(fromNode) && nodes.contains(toNode);
        assert fromNode.getLayer() > toNode.getLayer();
        assert reversedLinks.contains(link);

        updateNodeWithReversedEdges(fromNode);
        updateNodeWithReversedEdges(toNode);
    }

    private void updateNodeWithReversedEdges(LayoutNode node) {
        node.computeReversedLinkPoints(false);
    }

    /**
     * Indicate that the layout should be redrawn with a static algorithm
     */
    public void setShouldRedrawLayout(boolean shouldRedrawLayout) {
        this.shouldRedrawLayout = shouldRedrawLayout;
    }

    public void updateLayout(Set<? extends Vertex> vertices, Set<? extends Link> links) {
        currentVertices = vertices;
        currentLinks = links;
        reversedLinks = new HashSet<>();
        vertexActions = new LinkedList<>();
        linkActions = new LinkedList<>();
        vertexToAction = new HashMap<>();

        new ProcessInput().run();

        if (shouldRedrawLayout) {
            // If the layout is too messy it should be redrawn using the static algorithm,
            // currently HierarchicalLayoutManager
            manager.doLayout(new LayoutGraph(links, vertices));
            nodes = new ArrayList<>(manager.getNodes());
            shouldRedrawLayout = false;
        } else {
            generateActions();

            new BuildDatastructures().run();

            findInitialReversedLinks();

            // Only apply updates if there are any
            if (!linkActions.isEmpty() || !vertexActions.isEmpty()) {
                new ApplyActionUpdates().run();
            }

            new AssignYCoordinates().run();

            new WriteResult().run();
        }

        oldVertices = new HashSet<>(currentVertices);
        oldLinks = new HashSet<>(currentLinks);
    }

    private class ProcessInput {
        public void removeDuplicateLinks() {
            HashSet<Link> links = new HashSet<>();
            for (Link link : currentLinks) {
                if (link.getTo().getVertex().equals(link.getFrom().getVertex())) {
                    // self-edge
                    continue;
                }
                links.add(link);
            }
            currentLinks = links;
        }

        private void run() {
            removeDuplicateLinks();
        }
    }

    private class BuildDatastructures {

        // In case there are changes in the node size, its layer must be updated
        Set<Integer> layersToUpdate = new HashSet<>();

        /**
         * Update the vertex and link object references to the current vertices and
         * links, resetting any temporary changes caused by previous layout
         */
        private void updateNodeObjects() {
            for (LayoutNode node : nodes) {
                if (node.getVertex() != null) {
                    for (Vertex vertex : currentVertices) {
                        if (vertex.equals(node.getVertex())) {
                            Dimension size = vertex.getSize();
                            if (node.getOuterWidth() < (int) size.getWidth()) {
                                layersToUpdate.add(node.getLayer());
                            }
                            node.initSize();
                            node.setVertex(vertex);
                        }
                    }
                    vertexToLayoutNode.put(node.getVertex(), node);
                } else {
                    node.initSize();
                }
                for (LayoutEdge edge : node.getPredecessors()) {
                    if (edge.getLink() != null) {
                        for (Link link : currentLinks) {
                            if (link.equals(edge.getLink())) {
                                edge.setLink(link);
                                if (link.getTo().getVertex().equals(edge.getFrom().getVertex())) {
                                    // reversed link
                                    edge.setRelativeFromX(link.getTo().getRelativePosition().x);
                                    edge.setRelativeToX(link.getFrom().getRelativePosition().x);
                                } else {
                                    edge.setRelativeFromX(link.getFrom().getRelativePosition().x);
                                    edge.setRelativeToX(link.getTo().getRelativePosition().x);
                                }
                                break;
                            }
                        }
                    }
                }
                for (LayoutEdge edge : node.getSuccessors()) {
                    if (edge.getLink() != null) {
                        for (Link link : currentLinks) {
                            if (link.equals(edge.getLink())) {
                                edge.setLink(link);
                                if (link.getTo().getVertex().equals(edge.getFrom().getVertex())) {
                                    // reversed link
                                    edge.setRelativeFromX(link.getTo().getRelativePosition().x);
                                    edge.setRelativeToX(link.getFrom().getRelativePosition().x);
                                } else {
                                    edge.setRelativeFromX(link.getFrom().getRelativePosition().x);
                                    edge.setRelativeToX(link.getTo().getRelativePosition().x);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }

        /**
         * Store the nodes that each layer contains
         */
        private void storeNodeLayers() {
            layers = new HashMap<>();
            for (LayoutNode node : nodes) {
                if (!layers.containsKey(node.getLayer())) {
                    layers.put(node.getLayer(), new LayoutLayer());
                }
                layers.get(node.getLayer()).add(node);
            }
            for (int i = 0; i < layers.keySet().size(); i++) {
                if (!layers.containsKey(i)) {
                    layers.put(i, new LayoutLayer());
                }
            }
        }

        private void updateLayersXCoords() {
            for (Integer i : layersToUpdate) {
                adjustXCoordinates(i);
            }
        }

        private void run() {
            updateNodeObjects();
            storeNodeLayers();
            updateLayersXCoords();
        }
    }

    private class ApplyActionUpdates {
        /**
         * Find the optimal position within the given layer to insert the given node.
         * The optimum is given by the least amount of edge crossings.
         */
        private int optimalPosition(LayoutNode node, int layer) {
            assert layers.containsKey(layer);

            List<LayoutNode> layerNodes = layers.get(layer);
            layerNodes.sort(Comparator.comparingInt(LayoutNode::getPos));
            int edgeCrossings = Integer.MAX_VALUE;
            int optimalPos = -1;

            // Try each possible position in the layer
            for (int i = 0; i < layerNodes.size() + 1; i++) {
                int xCoord;
                if (i == 0) {
                    xCoord = layerNodes.get(i).getX() - node.getOuterWidth() - 1;
                } else {
                    xCoord = layerNodes.get(i - 1).getX() + layerNodes.get(i - 1).getOuterWidth() + 1;
                }

                int currentCrossings = 0;

                if (layers.containsKey(layer - 1)) {
                    List<LayoutNode> predNodes = layers.get(layer - 1);
                    // For each link with an end point in vertex, check how many edges cross it
                    for (LayoutEdge edge : node.getPredecessors()) {
                        if (edge.getFrom().getLayer() == layer - 1) {
                            int fromNodeXCoord = edge.getFrom().getX();
                            if (edge.getFrom().getVertex() != null) {
                                fromNodeXCoord += edge.getRelativeFromX();
                            }
                            int toNodeXCoord = xCoord;
                            if (node.getVertex() != null) {
                                toNodeXCoord += edge.getRelativeToX();
                            }
                            for (LayoutNode n : predNodes) {
                                for (LayoutEdge e : n.getSuccessors()) {
                                    if (e.getTo() == null) {
                                        continue;
                                    }
                                    int compFromXCoord = e.getFrom().getX();
                                    if (e.getFrom().getVertex() != null) {
                                        compFromXCoord += e.getRelativeFromX();
                                    }
                                    int compToXCoord = e.getTo().getX();
                                    if (e.getTo().getVertex() != null) {
                                        compToXCoord += e.getRelativeToX();
                                    }
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
                // Edge crossings across current layer and layer below
                if (layers.containsKey(layer + 1)) {
                    List<LayoutNode> succsNodes = layers.get(layer + 1);
                    // For each link with an end point in vertex, check how many edges cross it
                    for (LayoutEdge edge : node.getSuccessors()) {
                        if (edge.getTo().getLayer() == layer + 1) {
                            int toNodeXCoord = edge.getTo().getX();
                            if (edge.getTo().getVertex() != null) {
                                toNodeXCoord += edge.getRelativeToX();
                            }
                            int fromNodeXCoord = xCoord;
                            if (node.getVertex() != null) {
                                fromNodeXCoord += edge.getRelativeFromX();
                            }
                            for (LayoutNode n : succsNodes) {
                                for (LayoutEdge e : n.getPredecessors()) {
                                    if (e.getFrom() == null) {
                                        continue;
                                    }
                                    int compFromXCoord = e.getFrom().getX();
                                    if (e.getFrom().getVertex() != null) {
                                        compFromXCoord += e.getRelativeFromX();
                                    }
                                    int compToXCoord = e.getTo().getX();
                                    if (e.getTo().getVertex() != null) {
                                        compToXCoord += e.getRelativeToX();
                                    }
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
            assert optimalPos != -1;
            return optimalPos;
        }

        /**
         * Insert node at the assigned layer, updating the positions of the nodes within
         * the layer
         */
        private void insertNode(LayoutNode node, int layer) {
            assert layers.containsKey(layer) || layer == 0;

            node.setLayer(layer);
            LayoutLayer layerNodes = layers.getOrDefault(layer, new LayoutLayer());

            if (layerNodes.isEmpty()) {
                node.setPos(0);
            } else {
                node.setPos(optimalPosition(node, layer));
            }

            for (LayoutNode n : layerNodes) {
                if (n.getPos() >= node.getPos()) {
                    n.setPos(n.getPos() + 1);
                }
            }
            layerNodes.add(node);
            layers.put(layer, layerNodes);

            if (!nodes.contains(node)) {
                nodes.add(node);
            }
            if (node.getVertex() != null) {
                vertexToLayoutNode.put(node.getVertex(), node);
            }

            adjustXCoordinates(layer);
        }

        private void processSingleEdge(LayoutEdge e) {
            LayoutNode n = e.getTo();
            if (e.getTo().getLayer() - 1 > e.getFrom().getLayer()) {
                LayoutEdge last = e;
                for (int i = n.getLayer() - 1; i > last.getFrom().getLayer(); i--) {
                    last = addBetween(last, i);
                }
            }
        }

        private LayoutEdge addBetween(LayoutEdge e, int layer) {
            LayoutNode n = new LayoutNode();
            n.addSuccessor(e);
            LayoutEdge result = new LayoutEdge(e.getFrom(), n, e.getRelativeFromX(), n.getOuterWidth() / 2, e.getLink());
            n.addPredecessor(result);
            e.setRelativeFromX(n.getOuterWidth() / 2);
            e.getFrom().removeSuccessor(e);
            e.getFrom().addSuccessor(result);
            e.setFrom(n);
            insertNode(n, layer);
            return result;
        }

        private void insertDummyNodes(LayoutEdge edge) {
            LayoutNode from = edge.getFrom();
            LayoutNode to = edge.getTo();

            boolean hasEdgeFromSamePort = false;
            LayoutEdge edgeFromSamePort = null;

            for (LayoutEdge e : edge.getFrom().getSuccessors()) {
                if (e.getRelativeFromX() == edge.getRelativeFromX() && e.getTo().getVertex() == null) {
                    edgeFromSamePort = e;
                    hasEdgeFromSamePort = true;
                    break;
                }
            }

            if (!hasEdgeFromSamePort) {
                processSingleEdge(edge);
            } else {
                LayoutEdge curEdge = edgeFromSamePort;
                boolean newEdge = true;
                while (curEdge.getTo().getLayer() < to.getLayer() - 1 && curEdge.getTo().getVertex() == null && newEdge) {
                    // Traverse down the chain of dummy nodes linking together the edges originating
                    // from the same port
                    newEdge = false;
                    if (curEdge.getTo().getSuccessors().size() == 1) {
                        curEdge = curEdge.getTo().getSuccessors().get(0);
                        newEdge = true;
                    } else {
                        for (LayoutEdge e : curEdge.getTo().getSuccessors()) {
                            if (e.getTo().getVertex() == null) {
                                curEdge = e;
                                newEdge = true;
                                break;
                            }
                        }
                    }
                }

                LayoutNode prevDummy;
                if (curEdge.getTo().getVertex() != null) {
                    prevDummy = curEdge.getFrom();
                } else {
                    prevDummy = curEdge.getTo();
                }

                edge.setFrom(prevDummy);
                edge.setRelativeFromX(prevDummy.getOuterWidth() / 2);
                from.removeSuccessor(edge);
                prevDummy.addSuccessor(edge);
                processSingleEdge(edge);
            }
        }

        private boolean canMoveNodeUp(LayoutNode node) {
            if (node.getLayer() == 0) {
                return false;
            }
            int newLayer = node.getLayer() - 1;
            for (LayoutEdge e : node.getPredecessors()) {
                if (e.getFrom().getVertex() != null && e.getFrom().getLayer() == newLayer) {
                    return false;
                }
            }
            return true;
        }

        private boolean canMoveNodeDown(LayoutNode node) {
            if (node.getLayer() == layers.keySet().size() - 1) {
                return false;
            }
            int newLayer = node.getLayer() + 1;
            for (LayoutEdge e : node.getSuccessors()) {
                if (e.getTo().getVertex() != null && e.getTo().getLayer() == newLayer) {
                    return false;
                }
            }
            return true;
        }

        private void moveNodeUp(LayoutNode node) {
            assert canMoveNodeUp(node);

            List<LayoutEdge> previousPredEdges = List.copyOf(node.getPredecessors());
            for (LayoutEdge edge : previousPredEdges) {
                LayoutNode predNode = edge.getFrom();
                assert predNode.getVertex() == null;
                for (LayoutEdge e : predNode.getPredecessors()) {
                    e.setTo(edge.getTo());
                    e.setRelativeToX(edge.getRelativeToX());
                    node.addPredecessor(e);
                    node.removePredecessor(edge);
                }
                removeNodeWithoutRemovingLayer(predNode);
            }

            removeNodeWithoutRemovingLayer(node);
            insertNode(node, node.getLayer() - 1);

            for (LayoutEdge edge : List.copyOf(node.getSuccessors())) {
                processSingleEdge(edge);
            }
        }

        private void moveNodeDown(LayoutNode node) {
            assert canMoveNodeDown(node);

            List<LayoutEdge> previousSuccEdges = List.copyOf(node.getSuccessors());
            for (LayoutEdge edge : previousSuccEdges) {
                LayoutNode succNode = edge.getTo();
                assert succNode.getVertex() == null;
                for (LayoutEdge e : succNode.getSuccessors()) {
                    e.setFrom(edge.getFrom());
                    e.setRelativeFromX(edge.getRelativeFromX());
                    node.addSuccessor(e);
                    node.removeSuccessor(edge);
                }
                removeNodeWithoutRemovingLayer(succNode);
            }

            removeNodeWithoutRemovingLayer(node);
            insertNode(node, node.getLayer() + 1);

            for (LayoutEdge edge : List.copyOf(node.getPredecessors())) {
                processSingleEdge(edge);
            }
        }

        private void handleNeighborNodesOnSameLayer(LayoutNode from, LayoutNode to) {
            if (canMoveNodeDown(to)) {
                moveNodeDown(to);
            } else if (canMoveNodeUp(from)) {
                moveNodeUp(from);
            } else {
                expandNewLayerBeneath(to);
            }

            ensureNeighborEdgeConsistency();
        }

        /**
         * Create a new layer at node.layer + 1 and move the given node there. Adjust
         * remaining layers numbers
         */
        private void expandNewLayerBeneath(LayoutNode node) {
            int layer = node.getLayer() + 1;

            // Move all necessary layers down one step
            for (int i = layers.size() - 1; i >= layer; i--) {
                LayoutLayer list = layers.get(i);
                for (LayoutNode n : list) {
                    n.setLayer(i + 1);
                }
                layers.remove(i);
                layers.put(i + 1, list);
            }

            // Create new empty layer
            LayoutLayer l = new LayoutLayer();
            layers.put(layer, l);

            assert layers.get(layer).isEmpty();
            for (LayoutNode n : nodes) {
                assert n.getLayer() != layer;
            }

            // Add dummy nodes for edges going across new layer. One for each port on the
            // nodes that has outgoing edges
            List<LayoutNode> predLayer = List.copyOf(layers.get(layer - 1));
            for (LayoutNode n : predLayer) {
                HashMap<Integer, List<LayoutEdge>> portHashes = new HashMap<>();

                for (LayoutEdge e : n.getSuccessors()) {
                    if (!portHashes.containsKey(e.getRelativeFromX())) {
                        portHashes.put(e.getRelativeFromX(), new ArrayList<>());
                    }
                    portHashes.get(e.getRelativeFromX()).add(e);
                }

                for (Integer i : portHashes.keySet()) {
                    List<LayoutEdge> edges = portHashes.get(i);

                    LayoutNode dummy = new LayoutNode();

                    LayoutEdge newEdge = new LayoutEdge(n, dummy, i, dummy.getOuterWidth() / 2, edges.get(0).getLink());
                    n.addSuccessor(newEdge);
                    dummy.addPredecessor(newEdge);

                    for (LayoutEdge e : edges) {
                        e.setFrom(dummy);
                        e.setRelativeFromX(dummy.getOuterWidth() / 2);
                        n.removeSuccessor(e);
                        dummy.addSuccessor(e);
                        assert e.getTo().getLayer() == layer + 1;
                    }

                    insertNode(dummy, layer);
                }
            }

            // Move node to new layer
            moveNodeDown(node);
            assert layers.get(layer).contains(node);
            assert node.getLayer() == layer;
        }

        private void applyAddLinkAction(Link l) {
            Vertex from = l.getFrom().getVertex();
            Vertex to = l.getTo().getVertex();
            LayoutNode fromNode = vertexToLayoutNode.get(from);
            LayoutNode toNode = vertexToLayoutNode.get(to);

            if (!nodes.contains(fromNode) || !nodes.contains(toNode) || to.equals(from)) {
                return;
            }

            if (toNode.getLayer() == fromNode.getLayer()) {
                handleNeighborNodesOnSameLayer(fromNode, toNode);
            }

            LayoutEdge edge = new LayoutEdge(fromNode, toNode, l.getFrom().getRelativePosition().x, l.getTo().getRelativePosition().x, l);
            boolean reversedLink = fromNode.getLayer() > toNode.getLayer();
            if (reversedLink) {
                // Reversed link
                reversedLinks.add(l);

                LayoutNode temp = fromNode;
                fromNode = toNode;
                toNode = temp;

                int oldRelativeFrom = edge.getRelativeFromX();
                int oldRelativeTo = edge.getRelativeToX();

                edge.setFrom(fromNode);
                edge.setTo(toNode);
                edge.setRelativeFromX(oldRelativeTo);
                edge.setRelativeToX(oldRelativeFrom);
            }

            fromNode.addSuccessor(edge);
            toNode.addPredecessor(edge);

            if (reversedLink) {
                updateReversedLinkPositions(l);
            }

            if (fromNode.getLayer() != toNode.getLayer() - 1) {
                // Edge span multiple layers - must insert dummy nodes
                insertDummyNodes(edge);
            }

            ensureNeighborEdgeConsistency();
        }

        /**
         * Calculate which layer the given vertex should be inserted at to minimize
         * reversed edges and edge lengths
         * If there are multiple options, choose the bottom-most layer
         *
         * @return the optimal layer to insert the given vertex
         */
        private int optimalLayer(Vertex vertex, List<Link> links) {
            if (vertex.isRoot()) {
                return 0;
            } else if (layers.keySet().isEmpty()) {
                return 0;
            }

            int reversedEdges = Integer.MAX_VALUE;
            int totalEdgeLength = Integer.MAX_VALUE;
            int neighborsOnSameLayer = Integer.MAX_VALUE;
            int layer = -1;
            for (int i = 0; i < layers.keySet().size(); i++) {
                int curReversedEdges = 0;
                int curTotalEdgeLength = 0;
                int curNeighborsOnSameLayer = 0;
                for (Link link : links) {
                    LayoutNode fromNode = vertexToLayoutNode.get(link.getFrom().getVertex());
                    LayoutNode toNode = vertexToLayoutNode.get(link.getTo().getVertex());
                    if (link.getTo().getVertex().equals(vertex) && fromNode != null) {
                        if (fromNode.getLayer() > i) {
                            curReversedEdges += 1;
                        } else if (fromNode.getLayer() == i) {
                            curNeighborsOnSameLayer += 1;
                        }
                        curTotalEdgeLength += Math.abs(fromNode.getLayer() - i);
                    }
                    if (link.getFrom().getVertex().equals(vertex) && toNode != null) {
                        if (toNode.getLayer() < i) {
                            curReversedEdges += 1;
                        } else if (toNode.getLayer() == i) {
                            curNeighborsOnSameLayer += 1;
                        }
                        curTotalEdgeLength += Math.abs(i - toNode.getLayer());
                    }
                }

                curReversedEdges *= 10000;
                curNeighborsOnSameLayer *= 2;

                if (curReversedEdges + curTotalEdgeLength + curNeighborsOnSameLayer <= reversedEdges + totalEdgeLength
                        + neighborsOnSameLayer) {
                    totalEdgeLength = curTotalEdgeLength;
                    reversedEdges = curReversedEdges;
                    neighborsOnSameLayer = curNeighborsOnSameLayer;
                    layer = i;
                }
            }

            assert layer != -1;
            return layer;
        }

        private void applyAddVertexAction(VertexAction action) {
            LayoutNode node = new LayoutNode(action.vertex);
            List<Link> links = new ArrayList<>();
            for (LinkAction a : action.linkActions) {
                links.add(a.link);
            }
            int layer = optimalLayer(action.vertex, links);

            // Temporarily add the links so that the node insertion accounts for edge
            // crossings
            for (Link l : links) {
                if (l.getTo().getVertex().equals(action.vertex)
                        && nodes.contains(vertexToLayoutNode.get(l.getFrom().getVertex()))) {
                    LayoutEdge e = new LayoutEdge(vertexToLayoutNode.get(l.getFrom().getVertex()), node, l.getFrom().getRelativePosition().x, l.getTo().getRelativePosition().x, null);
                    node.addPredecessor(e);
                } else if (l.getFrom().getVertex().equals(action.vertex)
                        && nodes.contains(vertexToLayoutNode.get(l.getTo().getVertex()))) {
                    LayoutEdge e = new LayoutEdge(node, vertexToLayoutNode.get(l.getTo().getVertex()), l.getFrom().getRelativePosition().x, l.getTo().getRelativePosition().x, null);
                    node.addSuccessor(e);
                }
            }
            insertNode(node, layer);
            node.clearSuccessors();
            node.clearPredecessors();

            // Add associated edges
            for (LinkAction a : action.linkActions) {
                if (a.action == Action.ADD) {
                    applyAddLinkAction(a.link);
                }
            }
        }

        private void applyRemoveLinkAction(Link l) {
            Vertex from = l.getFrom().getVertex();
            Vertex to = l.getTo().getVertex();
            LayoutNode toNode = vertexToLayoutNode.get(to);
            LayoutNode fromNode = vertexToLayoutNode.get(from);

            if (toNode.getLayer() < fromNode.getLayer()) {
                // Reversed edge
                toNode = fromNode;
                reversedLinks.remove(l);
            }

            // Remove preds-edges bottom up, starting at "to" node
            // Cannot start from "from" node since there might be joint edges
            List<LayoutEdge> toNodePredsEdges = List.copyOf(toNode.getPredecessors());
            for (LayoutEdge edge : toNodePredsEdges) {
                LayoutNode n = edge.getFrom();
                LayoutEdge edgeToRemove;

                if (edge.getLink() != null && edge.getLink().equals(l)) {
                    toNode.removePredecessor(edge);
                    edgeToRemove = edge;
                } else {
                    // Wrong edge, look at next
                    continue;
                }

                if (n.getVertex() != null && n.getVertex().equals(from)) {
                    // No dummy nodes inbetween 'from' and 'to' vertex
                    n.removeSuccessor(edgeToRemove);
                    break;
                } else {
                    // Must remove edges between dummy nodes
                    boolean found = true;
                    LayoutNode prev = toNode;
                    while (n.getVertex() == null && found) {
                        found = false;

                        if (n.getSuccessors().size() <= 1 && n.getPredecessors().size() <= 1) {
                            // Dummy node used only for this link, remove if not already removed
                            if (nodes.contains(n)) {
                                removeNode(n);
                            }
                        } else {
                            // anchor node, should not be removed
                            break;
                        }

                        if (n.getPredecessors().size() == 1) {
                            n.removeSuccessor(edgeToRemove);
                            prev = n;
                            edgeToRemove = n.getPredecessors().get(0);
                            n = edgeToRemove.getFrom();
                            found = true;
                        }
                    }

                    n.removeSuccessor(edgeToRemove);
                    prev.removePredecessor(edgeToRemove);
                }
                break;
            }

            ensureNeighborEdgeConsistency();
        }

        private void removeNodeWithoutRemovingLayer(LayoutNode node) {
            boolean prevShouldRemoveEmptyLayers = shouldRemoveEmptyLayers;
            shouldRemoveEmptyLayers = false;
            removeNode(node);
            shouldRemoveEmptyLayers = prevShouldRemoveEmptyLayers;
        }

        private void removeNode(LayoutNode node) {
            if (!nodes.contains(node)) {
                return;
            }
            int layer = node.getLayer();
            int pos = node.getPos();
            LayoutLayer remainingLayerNodes = layers.get(layer);
            assert remainingLayerNodes.contains(node);
            remainingLayerNodes.remove(node);

            // Update position of remaining nodes on the same layer
            boolean onlyDummiesLeft = true;
            for (LayoutNode n : remainingLayerNodes) {
                if (n.getPos() > pos) {
                    n.setPos(n.getPos() - 1);
                }
                if (n.getVertex() != null || n.getPredecessors().size() > 1) {
                    onlyDummiesLeft = false;
                }
            }

            if (onlyDummiesLeft && shouldRemoveEmptyLayers) {
                layers.remove(layer);
                for (int i = layer + 1; i <= layers.size(); i++) {
                    LayoutLayer list = layers.get(i);
                    layers.remove(i);
                    layers.put(i - 1, list);
                    for (LayoutNode n : list) {
                        n.setLayer(n.getLayer() - 1);
                    }
                }
                for (LayoutNode n : remainingLayerNodes) {
                    if (n.getPredecessors().size() == 1) {
                        LayoutEdge predEdge = n.getPredecessors().get(0);
                        LayoutNode fromNode = predEdge.getFrom();
                        fromNode.removeSuccessor(predEdge);
                        for (LayoutEdge e : n.getSuccessors()) {
                            e.setFrom(fromNode);
                            e.setRelativeFromX(predEdge.getRelativeFromX());
                            fromNode.addSuccessor(e);
                        }
                    }
                    nodes.remove(n);
                }
            } else {
                layers.put(layer, remainingLayerNodes);
                adjustXCoordinates(layer);
            }

            // Remove node from graph layout
            nodes.remove(node);
        }

        private void applyRemoveVertexAction(VertexAction action) {
            LayoutNode node = vertexToLayoutNode.get(action.vertex);

            assert nodes.contains(node);

            // Remove associated edges
            for (LinkAction a : action.linkActions) {
                if (a.action == Action.REMOVE) {
                    applyRemoveLinkAction(a.link);
                }
            }

            removeNode(node);
        }

        void run() {
            for (LinkAction action : linkActions) {
                if (action.action == Action.REMOVE) {
                    applyRemoveLinkAction(action.link);
                }
            }

            for (VertexAction action : vertexActions) {
                switch (action.action) {
                    case REMOVE:
                        applyRemoveVertexAction(action);
                        break;
                    case ADD:
                        applyAddVertexAction(action);
                        break;
                    default:
                        assert false : "Invalid update action";
                }
            }

            for (LinkAction action : linkActions) {
                if (action.action == Action.ADD) {
                    applyAddLinkAction(action.link);
                }
            }

            // Add or remove wrongful links
            Set<Link> layoutedLinks = new HashSet<>();
            Set<LayoutNode> layoutedNodes = new HashSet<>();
            for (LayoutNode n : nodes) {
                for (LayoutEdge e : n.getPredecessors()) {
                    if (e.getLink() != null) {
                        layoutedLinks.add(e.getLink());
                    }
                }
                if (n.getVertex() != null) {
                    layoutedNodes.add(n);
                }
            }
            Set<Link> superfluousLinks = new HashSet<>(layoutedLinks);
            superfluousLinks.removeAll(currentLinks);
            for (Link l : superfluousLinks) {
                applyRemoveLinkAction(l);
                layoutedLinks.remove(l);
            }
            Set<Link> missingLinks = new HashSet<>(currentLinks);
            missingLinks.removeAll(layoutedLinks);
            for (Link l : missingLinks) {
                applyAddLinkAction(l);
                layoutedLinks.add(l);
            }
            assert currentVertices.size() == layoutedNodes.size();
            assert currentLinks.size() == layoutedLinks.size();
        }
    }

    private class AssignYCoordinates {
        void run() {
            int currentY = 0;
            for (int i = 0; i < layers.size(); i++) {
                LayoutLayer layer = layers.get(i);
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
    }

    private class WriteResult {

        private HashMap<Link, List<Point>> computeLinkPositions() {
            HashMap<Link, List<Point>> linkToSplitEndPoints = new HashMap<>();
            HashMap<Link, List<Point>> linkPositions = new HashMap<>();

            for (LayoutNode layoutNode : nodes) {
                if (layoutNode.isDummy()) continue;
                for (LayoutEdge predEdge : layoutNode.getPredecessors()) {
                    LayoutNode fromNode = predEdge.getFrom();
                    LayoutNode toNode = predEdge.getTo();

                    ArrayList<Point> linkPoints = new ArrayList<>();
                    // input edge stub
                    linkPoints.add(new Point(predEdge.getEndX(), predEdge.getEndY()));
                    linkPoints.add(new Point(predEdge.getEndX(), layers.get(toNode.getLayer()).getTop() - LAYER_OFFSET));

                    LayoutEdge curEdge = predEdge;
                    while (fromNode.isDummy() && fromNode.hasPredecessors()) {
                        linkPoints.add(new Point(fromNode.getCenterX(), layers.get(fromNode.getLayer()).getBottom() + LAYER_OFFSET));
                        linkPoints.add(new Point(fromNode.getCenterX(), layers.get(fromNode.getLayer()).getTop() - LAYER_OFFSET));
                        curEdge = fromNode.getPredecessors().get(0);
                        fromNode = curEdge.getFrom();
                    }
                    linkPoints.add(new Point(curEdge.getStartX(), layers.get(fromNode.getLayer()).getBottom() + LAYER_OFFSET));
                    // output edge stub
                    linkPoints.add(new Point(curEdge.getStartX(), curEdge.getStartY()));

                    if (predEdge.isReversed()) {
                        for (Point relativeEnd : toNode.getReversedLinkEndPoints().get(predEdge.getLink())) {
                            Point endPoint = new Point(toNode.getLeft() + relativeEnd.x, toNode.getTop() + relativeEnd.y);
                            linkPoints.add(0, endPoint);
                        }

                        if (!fromNode.isDummy()) {
                            if (fromNode.getReversedLinkStartPoints().containsKey(predEdge.getLink())) {
                                for (Point relativeStart : fromNode.getReversedLinkStartPoints().get(predEdge.getLink())) {
                                    Point startPoint = new Point(fromNode.getLeft() + relativeStart.x, fromNode.getTop() + relativeStart.y);
                                    linkPoints.add(startPoint);
                                }
                            }
                        }
                    } else {
                        Collections.reverse(linkPoints);
                    }

                    if (fromNode.isDummy()) {
                        if (predEdge.isReversed()) {
                            Collections.reverse(linkPoints);
                        }
                        linkToSplitEndPoints.put(predEdge.getLink(), linkPoints);

                    } else {
                        linkPositions.put(predEdge.getLink(), linkPoints);
                    }
                }
            }

            for (LayoutNode layoutNode : nodes) {
                if (layoutNode.isDummy()) continue;
                for (LayoutEdge succEdge : layoutNode.getSuccessors()) {
                    if (succEdge.getLink() == null) continue;

                    LayoutNode fromNode = succEdge.getFrom();
                    LayoutNode toNode = succEdge.getTo();

                    ArrayList<Point> linkPoints = new ArrayList<>();
                    linkPoints.add(new Point(succEdge.getStartX(), fromNode.getBottom()));
                    linkPoints.add(new Point(succEdge.getStartX(), layers.get(fromNode.getLayer()).getBottom() + LAYER_OFFSET));

                    LayoutEdge curEdge = succEdge;
                    while (toNode.isDummy() && toNode.hasSuccessors()) {
                        linkPoints.add(new Point(toNode.getCenterX(), layers.get(toNode.getLayer()).getTop() - LAYER_OFFSET));
                        linkPoints.add(new Point(toNode.getCenterX(), layers.get(toNode.getLayer()).getBottom() + LAYER_OFFSET));
                        curEdge = toNode.getSuccessors().get(0);
                        toNode = curEdge.getTo();
                    }
                    linkPoints.add(new Point(curEdge.getEndX(), layers.get(toNode.getLayer()).getTop() - LAYER_OFFSET));
                    linkPoints.add(new Point(curEdge.getEndX(), toNode.getTop()));

                    if (succEdge.isReversed()) {
                        Collections.reverse(linkPoints);

                        if (fromNode.getReversedLinkStartPoints().containsKey(succEdge.getLink())) {
                            for (Point relativeStart : fromNode.getReversedLinkStartPoints().get(succEdge.getLink())) {
                                Point startPoint = new Point(fromNode.getLeft() + relativeStart.x, fromNode.getTop() + relativeStart.y);
                                linkPoints.add(startPoint);
                            }
                        }

                        if (!toNode.isDummy()) {
                            if (toNode.getReversedLinkEndPoints().containsKey(succEdge.getLink())) {
                                for (Point relativeEnd : toNode.getReversedLinkEndPoints().get(succEdge.getLink())) {
                                    Point endPoint = new Point(toNode.getLeft() + relativeEnd.x, toNode.getTop() + relativeEnd.y);
                                    linkPoints.add(0, endPoint);
                                }
                            }
                        }
                    }

                    if (linkToSplitEndPoints.containsKey(succEdge.getLink())) {
                        if (succEdge.isReversed()) {
                            Collections.reverse(linkPoints);
                        }
                        linkPoints.add(null);
                        linkPoints.addAll(linkToSplitEndPoints.get(succEdge.getLink()));
                        if (succEdge.isReversed()) {
                            Collections.reverse(linkPoints);
                        }
                    }
                    linkPositions.put(succEdge.getLink(), linkPoints);
                }
            }

            return linkPositions;
        }


        void run() {
            HashMap<Vertex, Point> vertexPositions = new HashMap<>();
            HashMap<Link, List<Point>> linkPositions = computeLinkPositions();


            for (LayoutNode n : nodes) {
                if (n.getVertex() != null) {
                    assert !vertexPositions.containsKey(n.getVertex());
                    vertexPositions.put(n.getVertex(), new Point(n.getLeft(), n.getTop()));
                }
            }

            // Ensure all edges are drawn
            assert currentLinks.size() <= linkPositions.keySet().size();

            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            for (Vertex v : vertexPositions.keySet()) {
                Point p = vertexPositions.get(v);
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
            }

            for (Link l : linkPositions.keySet()) {
                List<Point> points = linkPositions.get(l);
                for (Point p : points) {
                    if (p != null) {
                        minX = Math.min(minX, p.x);
                        minY = Math.min(minY, p.y);
                    }
                }
            }

            for (Vertex v : vertexPositions.keySet()) {
                Point p = vertexPositions.get(v);
                p.x -= minX;
                p.y -= minY;
                v.setPosition(p);
            }

            for (Link l : linkPositions.keySet()) {
                List<Point> points = linkPositions.get(l);
                for (Point p : points) {
                    if (p != null) {
                        p.x -= minX;
                        p.y -= minY;
                    }
                }
                l.setControlPoints(points);
            }
        }
    }
}
