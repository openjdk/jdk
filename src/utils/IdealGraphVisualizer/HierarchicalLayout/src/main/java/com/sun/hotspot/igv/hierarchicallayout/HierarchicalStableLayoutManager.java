/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.layout.LayoutGraph;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Vertex;
import com.sun.hotspot.igv.util.Statistics;
import java.awt.Dimension;
import java.awt.Point;
import java.util.*;

public class HierarchicalStableLayoutManager {

    public static final int DUMMY_HEIGHT = 1;
    public static final int DUMMY_WIDTH = 1;
    public static final int X_OFFSET = 8;
    public static final int LAYER_OFFSET = 8;
    // Algorithm global data structures
    private HashSet<? extends Vertex> currentVertices;
    private HashSet<? extends Link> currentLinks;
    private Set<Link> reversedLinks;
    private List<LayoutNode> nodes;
    private final HashMap<Vertex, LayoutNode> vertexToLayoutNode;
    private HashMap<Link, List<Point>> reversedLinkStartPoints;
    private HashMap<Link, List<Point>> reversedLinkEndPoints;
    private HashMap<Integer, List<LayoutNode>> layers;

    private final HierarchicalLayoutManager manager;
    private HashMap<Vertex, VertexAction> vertexToAction;
    private List<VertexAction> vertexActions;
    private List<LinkAction> linkActions;
    private HashSet<? extends Vertex> oldVertices;
    private HashSet<? extends Link> oldLinks;
    private boolean shouldRedrawLayout = true;
    private boolean shouldRemoveEmptyLayers = true;

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
        manager = new HierarchicalLayoutManager(HierarchicalLayoutManager.Combine.SAME_OUTPUTS);
        vertexToLayoutNode = new HashMap<>();
        nodes = new ArrayList<>();
    }

    private int calculateOptimalBoth(LayoutNode n) {
        if (n.preds.isEmpty() && n.succs.isEmpty()) {
            return n.x;
        }

        int[] values = new int[n.preds.size() + n.succs.size()];
        int i = 0;

        for (LayoutEdge e : n.preds) {
            values[i] = e.from.x + e.relativeFrom - e.relativeTo;
            i++;
        }

        for (LayoutEdge e : n.succs) {
            values[i] = e.to.x + e.relativeTo - e.relativeFrom;
            i++;
        }

        return Statistics.median(values);
    }

    /**
     * Adjust the X-coordinates of the nodes in the given layer, as a new node has
     * been inserted at that layer
     */
    private void adjustXCoordinates(int layer) {
        List<LayoutNode> nodes = layers.get(layer);
        ArrayList<Integer> space = new ArrayList<>();
        List<LayoutNode> nodeProcessingOrder = new ArrayList<>();

        nodes.sort(HierarchicalLayoutManager.nodePositionComparator);

        int curX = 0;
        for (LayoutNode n : nodes) {
            space.add(curX);
            curX += n.width + X_OFFSET;
            nodeProcessingOrder.add(n);
        }

        nodeProcessingOrder.sort(HierarchicalLayoutManager.nodeProcessingUpComparator);
        HierarchicalLayoutManager.NodeRow r = new HierarchicalLayoutManager.NodeRow(space);
        for (LayoutNode n : nodeProcessingOrder) {
            int optimal = calculateOptimalBoth(n);
            r.insert(n, optimal);
        }
    }

    private void ensureNeighborEdgeConsistency() {
        for (LayoutNode n : nodes) {
            n.succs.removeIf(e -> !nodes.contains(e.to));
            n.preds.removeIf(e -> !nodes.contains(e.from));
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
                    if (vertexToLayoutNode.get(l.getFrom().getVertex()).layer > vertexToLayoutNode
                            .get(l.getTo().getVertex()).layer) {
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
        assert fromNode.layer > toNode.layer;
        assert reversedLinks.contains(link);

        updateNodeWithReversedEdges(fromNode);
        updateNodeWithReversedEdges(toNode);
    }

    private void updateNodeWithReversedEdges(LayoutNode node) {
        // Reset node data in case there were previous reversed edges
        node.width = (int) node.vertex.getSize().getWidth();
        node.height = (int) node.vertex.getSize().getHeight();
        node.yOffset = 0;
        node.bottomYOffset = 0;
        node.xOffset = 0;
        node.inOffsets.clear();
        node.outOffsets.clear();

        SortedSet<Integer> reversedDown = new TreeSet<>();

        // Reset relativeFrom for all succ edges
        for (LayoutEdge e : node.succs) {
            if (e.link == null) {
                continue;
            }
            e.relativeFrom = e.link.getFrom().getRelativePosition().x;
            if (reversedLinks.contains(e.link)) {
                e.relativeFrom = e.link.getTo().getRelativePosition().x;
                reversedDown.add(e.relativeFrom);
            }
        }

        // Whether the node has non-self reversed edges going downwards.
        // If so, reversed edges going upwards are drawn to the left.
        boolean hasReversedDown = !reversedDown.isEmpty();

        SortedSet<Integer> reversedUp;
        if (hasReversedDown) {
            reversedUp = new TreeSet<>();
        } else {
            reversedUp = new TreeSet<>(Collections.reverseOrder());
        }

        // Reset relativeTo for all pred edges
        for (LayoutEdge e : node.preds) {
            if (e.link == null) {
                continue;
            }
            e.relativeTo = e.link.getTo().getRelativePosition().x;
            if (reversedLinks.contains(e.link)) {
                e.relativeTo = e.link.getFrom().getRelativePosition().x;
                reversedUp.add(e.relativeTo);
            }
        }

        final int offset = X_OFFSET + DUMMY_WIDTH;

        int curY = 0;
        int curWidth = node.width + reversedDown.size() * offset;
        for (int pos : reversedDown) {
            ArrayList<LayoutEdge> reversedSuccs = new ArrayList<>();
            for (LayoutEdge e : node.succs) {
                if (e.relativeFrom == pos && reversedLinks.contains(e.link)) {
                    reversedSuccs.add(e);
                    e.relativeFrom = curWidth;
                }
            }

            ArrayList<Point> startPoints = new ArrayList<>();
            startPoints.add(new Point(curWidth, curY));
            startPoints.add(new Point(pos, curY));
            startPoints.add(new Point(pos, reversedDown.size() * offset));
            for (LayoutEdge e : reversedSuccs) {
                reversedLinkStartPoints.put(e.link, startPoints);
            }

            node.inOffsets.put(pos, -curY);
            curY += offset;
            node.height += offset;
            node.yOffset += offset;
            curWidth -= offset;
        }

        int widthFactor = reversedDown.size();
        node.width += widthFactor * offset;

        int curX = 0;
        int minX = 0;
        if (hasReversedDown) {
            minX = -offset * reversedUp.size();
        }

        int oldNodeHeight = node.height;
        for (int pos : reversedUp) {
            ArrayList<LayoutEdge> reversedPreds = new ArrayList<>();
            for (LayoutEdge e : node.preds) {
                if (e.relativeTo == pos && reversedLinks.contains(e.link)) {
                    if (hasReversedDown) {
                        e.relativeTo = curX - offset;
                    } else {
                        e.relativeTo = node.width + offset;
                    }

                    reversedPreds.add(e);
                }
            }
            node.height += offset;
            ArrayList<Point> endPoints = new ArrayList<>();

            node.width += offset;
            if (hasReversedDown) {
                curX -= offset;
                endPoints.add(new Point(curX, node.height));
            } else {
                curX += offset;
                endPoints.add(new Point(node.width, node.height));
            }

            node.outOffsets.put(pos - minX, curX);
            curX += offset;
            node.bottomYOffset += offset;

            endPoints.add(new Point(pos, node.height));
            endPoints.add(new Point(pos, oldNodeHeight));
            for (LayoutEdge e : reversedPreds) {
                reversedLinkEndPoints.put(e.link, endPoints);
            }
        }

        if (minX < 0) {
            for (LayoutEdge e : node.preds) {
                e.relativeTo -= minX;
            }

            for (LayoutEdge e : node.succs) {
                e.relativeFrom -= minX;
            }

            node.xOffset = -minX;
            node.width -= minX;
        }
    }

    /**
     * Indicate that the layout should be redrawn with a static algorithm
     */
    public void setShouldRedrawLayout(boolean shouldRedrawLayout) {
        this.shouldRedrawLayout = shouldRedrawLayout;
    }

    public void updateLayout(HashSet<? extends Vertex> vertices, HashSet<? extends Link> links) {
        currentVertices = vertices;
        currentLinks = links;
        reversedLinks = new HashSet<>();
        reversedLinkStartPoints = new HashMap<>();
        reversedLinkEndPoints = new HashMap<>();
        vertexActions = new LinkedList<>();
        linkActions = new LinkedList<>();
        vertexToAction = new HashMap<>();

        new ProcessInput().run();

        if (shouldRedrawLayout) {
            // If the layout is too messy it should be redrawn using the static algorithm,
            // currently HierarchicalLayoutManager
            manager.doLayout(new LayoutGraph(links, vertices));
            nodes = manager.getNodes();
            shouldRedrawLayout = false;
        } else {
            generateActions();

            new BuildDatastructure().run();

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

    private class BuildDatastructure {

        // In case there are changes in the node size, its layer must be updated
        Set<Integer> layersToUpdate = new HashSet<>();

        /**
         * Update the vertex and link object references to the current vertices and
         * links, resetting any temporary changes caused by previous layout
         */
        private void updateNodeObjects() {
            for (LayoutNode node : nodes) {
                if (node.vertex != null) {
                    for (Vertex vertex : currentVertices) {
                        if (vertex.equals(node.vertex)) {
                            Dimension size = vertex.getSize();
                            if (node.width < (int) size.getWidth()) {
                                layersToUpdate.add(node.layer);
                            }
                            node.width = (int) size.getWidth();
                            node.height = (int) size.getHeight();
                            node.vertex = vertex;
                        }
                    }
                    vertexToLayoutNode.put(node.vertex, node);
                } else {
                    node.height = DUMMY_HEIGHT;
                    node.width = DUMMY_WIDTH;
                }
                for (LayoutEdge edge : node.preds) {
                    if (edge.link != null) {
                        for (Link link : currentLinks) {
                            if (link.equals(edge.link)) {
                                edge.link = link;
                                if (link.getTo().getVertex().equals(edge.from.vertex)) {
                                    // reversed link
                                    edge.relativeFrom = link.getTo().getRelativePosition().x;
                                    edge.relativeTo = link.getFrom().getRelativePosition().x;
                                } else {
                                    edge.relativeFrom = link.getFrom().getRelativePosition().x;
                                    edge.relativeTo = link.getTo().getRelativePosition().x;
                                }
                                break;
                            }
                        }
                    }
                }
                for (LayoutEdge edge : node.succs) {
                    if (edge.link != null) {
                        for (Link link : currentLinks) {
                            if (link.equals(edge.link)) {
                                edge.link = link;
                                if (link.getTo().getVertex().equals(edge.from.vertex)) {
                                    // reversed link
                                    edge.relativeFrom = link.getTo().getRelativePosition().x;
                                    edge.relativeTo = link.getFrom().getRelativePosition().x;
                                } else {
                                    edge.relativeFrom = link.getFrom().getRelativePosition().x;
                                    edge.relativeTo = link.getTo().getRelativePosition().x;
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
                if (!layers.containsKey(node.layer)) {
                    layers.put(node.layer, new ArrayList<>());
                }
                layers.get(node.layer).add(node);
            }
            for (int i = 0; i < layers.keySet().size(); i++) {
                if (!layers.containsKey(i)) {
                    layers.put(i, new ArrayList<>());
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
            layerNodes.sort(HierarchicalLayoutManager.nodePositionComparator);
            int edgeCrossings = Integer.MAX_VALUE;
            int optimalPos = -1;

            // Try each possible position in the layer
            for (int i = 0; i < layerNodes.size() + 1; i++) {
                int xCoord;
                if (i == 0) {
                    xCoord = layerNodes.get(i).x - node.width - 1;
                } else {
                    xCoord = layerNodes.get(i - 1).x + layerNodes.get(i - 1).width + 1;
                }

                int currentCrossings = 0;

                if (layers.containsKey(layer - 1)) {
                    List<LayoutNode> predNodes = layers.get(layer - 1);
                    // For each link with an end point in vertex, check how many edges cross it
                    for (LayoutEdge edge : node.preds) {
                        if (edge.from.layer == layer - 1) {
                            int fromNodeXCoord = edge.from.x;
                            if (edge.from.vertex != null) {
                                fromNodeXCoord += edge.relativeFrom;
                            }
                            int toNodeXCoord = xCoord;
                            if (node.vertex != null) {
                                toNodeXCoord += edge.relativeTo;
                            }
                            for (LayoutNode n : predNodes) {
                                for (LayoutEdge e : n.succs) {
                                    if (e.to == null) {
                                        continue;
                                    }
                                    int compFromXCoord = e.from.x;
                                    if (e.from.vertex != null) {
                                        compFromXCoord += e.relativeFrom;
                                    }
                                    int compToXCoord = e.to.x;
                                    if (e.to.vertex != null) {
                                        compToXCoord += e.relativeTo;
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
                    for (LayoutEdge edge : node.succs) {
                        if (edge.to.layer == layer + 1) {
                            int toNodeXCoord = edge.to.x;
                            if (edge.to.vertex != null) {
                                toNodeXCoord += edge.relativeTo;
                            }
                            int fromNodeXCoord = xCoord;
                            if (node.vertex != null) {
                                fromNodeXCoord += edge.relativeFrom;
                            }
                            for (LayoutNode n : succsNodes) {
                                for (LayoutEdge e : n.preds) {
                                    if (e.from == null) {
                                        continue;
                                    }
                                    int compFromXCoord = e.from.x;
                                    if (e.from.vertex != null) {
                                        compFromXCoord += e.relativeFrom;
                                    }
                                    int compToXCoord = e.to.x;
                                    if (e.to.vertex != null) {
                                        compToXCoord += e.relativeTo;
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

            node.layer = layer;
            List<LayoutNode> layerNodes = layers.getOrDefault(layer, new ArrayList<LayoutNode>());

            if (layerNodes.isEmpty()) {
                node.pos = 0;
            } else {
                node.pos = optimalPosition(node, layer);
            }

            for (LayoutNode n : layerNodes) {
                if (n.pos >= node.pos) {
                    n.pos += 1;
                }
            }
            layerNodes.add(node);
            layers.put(layer, layerNodes);

            if (!nodes.contains(node)) {
                nodes.add(node);
            }
            if (node.vertex != null) {
                vertexToLayoutNode.put(node.vertex, node);
            }

            adjustXCoordinates(layer);
        }

        private void processSingleEdge(LayoutEdge e) {
            LayoutNode n = e.to;
            if (e.to.layer - 1 > e.from.layer) {
                LayoutEdge last = e;
                for (int i = n.layer - 1; i > last.from.layer; i--) {
                    last = addBetween(last, i);
                }
            }
        }

        private LayoutEdge addBetween(LayoutEdge e, int layer) {
            LayoutNode n = new LayoutNode();
            n.width = DUMMY_WIDTH;
            n.height = DUMMY_HEIGHT;
            n.succs.add(e);
            LayoutEdge result = new LayoutEdge();
            result.vip = e.vip;
            n.preds.add(result);
            result.to = n;
            result.relativeTo = n.width / 2;
            result.from = e.from;
            result.relativeFrom = e.relativeFrom;
            result.link = e.link;
            e.relativeFrom = n.width / 2;
            e.from.succs.remove(e);
            e.from.succs.add(result);
            e.from = n;
            insertNode(n, layer);
            return result;
        }

        private void insertDummyNodes(LayoutEdge edge) {
            LayoutNode from = edge.from;
            LayoutNode to = edge.to;

            boolean hasEdgeFromSamePort = false;
            LayoutEdge edgeFromSamePort = new LayoutEdge();

            for (LayoutEdge e : edge.from.succs) {
                if (e.relativeFrom == edge.relativeFrom && e.to.vertex == null) {
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
                while (curEdge.to.layer < to.layer - 1 && curEdge.to.vertex == null && newEdge) {
                    // Traverse down the chain of dummy nodes linking together the edges originating
                    // from the same port
                    newEdge = false;
                    if (curEdge.to.succs.size() == 1) {
                        curEdge = curEdge.to.succs.get(0);
                        newEdge = true;
                    } else {
                        for (LayoutEdge e : curEdge.to.succs) {
                            if (e.to.vertex == null) {
                                curEdge = e;
                                newEdge = true;
                                break;
                            }
                        }
                    }
                }

                LayoutNode prevDummy;
                if (curEdge.to.vertex != null) {
                    prevDummy = curEdge.from;
                } else {
                    prevDummy = curEdge.to;
                }

                edge.from = prevDummy;
                edge.relativeFrom = prevDummy.width / 2;
                from.succs.remove(edge);
                prevDummy.succs.add(edge);
                processSingleEdge(edge);
            }
        }

        private boolean canMoveNodeUp(LayoutNode node) {
            if (node.layer == 0) {
                return false;
            }
            int newLayer = node.layer - 1;
            for (LayoutEdge e : node.preds) {
                if (e.from.vertex != null && e.from.layer == newLayer) {
                    return false;
                }
            }
            return true;
        }

        private boolean canMoveNodeDown(LayoutNode node) {
            if (node.layer == layers.keySet().size() - 1) {
                return false;
            }
            int newLayer = node.layer + 1;
            for (LayoutEdge e : node.succs) {
                if (e.to.vertex != null && e.to.layer == newLayer) {
                    return false;
                }
            }
            return true;
        }

        private void moveNodeUp(LayoutNode node) {
            assert canMoveNodeUp(node);

            List<LayoutEdge> previousPredEdges = List.copyOf(node.preds);
            for (LayoutEdge edge : previousPredEdges) {
                LayoutNode predNode = edge.from;
                assert predNode.vertex == null;
                for (LayoutEdge e : predNode.preds) {
                    e.to = edge.to;
                    e.relativeTo = edge.relativeTo;
                    node.preds.add(e);
                    node.preds.remove(edge);
                }
                removeNodeWithoutRemovingLayer(predNode);
            }

            removeNodeWithoutRemovingLayer(node);
            insertNode(node, node.layer - 1);

            for (LayoutEdge edge : List.copyOf(node.succs)) {
                processSingleEdge(edge);
            }
        }

        private void moveNodeDown(LayoutNode node) {
            assert canMoveNodeDown(node);

            List<LayoutEdge> previousSuccEdges = List.copyOf(node.succs);
            for (LayoutEdge edge : previousSuccEdges) {
                LayoutNode succNode = edge.to;
                assert succNode.vertex == null;
                for (LayoutEdge e : succNode.succs) {
                    e.from = edge.from;
                    e.relativeFrom = edge.relativeFrom;
                    node.succs.add(e);
                    node.succs.remove(edge);
                }
                removeNodeWithoutRemovingLayer(succNode);
            }

            removeNodeWithoutRemovingLayer(node);
            insertNode(node, node.layer + 1);

            for (LayoutEdge edge : List.copyOf(node.preds)) {
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
            int layer = node.layer + 1;

            // Move all necessary layers down one step
            for (int i = layers.size() - 1; i >= layer; i--) {
                List<LayoutNode> list = layers.get(i);
                for (LayoutNode n : list) {
                    n.layer = i + 1;
                }
                layers.remove(i);
                layers.put(i + 1, list);
            }

            // Create new empty layer
            List<LayoutNode> l = new ArrayList<>();
            layers.put(layer, l);

            assert layers.get(layer).isEmpty();
            for (LayoutNode n : nodes) {
                assert n.layer != layer;
            }

            // Add dummy nodes for edges going across new layer. One for each port on the
            // nodes that has outgoing edges
            List<LayoutNode> predLayer = List.copyOf(layers.get(layer - 1));
            for (LayoutNode n : predLayer) {
                HashMap<Integer, List<LayoutEdge>> portHashes = new HashMap<>();

                for (LayoutEdge e : n.succs) {
                    if (!portHashes.containsKey(e.relativeFrom)) {
                        portHashes.put(e.relativeFrom, new ArrayList<>());
                    }
                    portHashes.get(e.relativeFrom).add(e);
                }

                for (Integer i : portHashes.keySet()) {
                    List<LayoutEdge> edges = portHashes.get(i);

                    LayoutNode dummy = new LayoutNode();
                    dummy.width = DUMMY_WIDTH;
                    dummy.height = DUMMY_HEIGHT;

                    LayoutEdge newEdge = new LayoutEdge();
                    newEdge.from = n;
                    newEdge.relativeFrom = i;
                    newEdge.to = dummy;
                    newEdge.relativeTo = dummy.width / 2;
                    newEdge.link = edges.get(0).link; // issue?
                    n.succs.add(newEdge);
                    dummy.preds.add(newEdge);

                    for (LayoutEdge e : edges) {
                        e.from = dummy;
                        e.relativeFrom = dummy.width / 2;
                        n.succs.remove(e);
                        dummy.succs.add(e);
                        assert e.to.layer == layer + 1;
                    }

                    insertNode(dummy, layer);
                }
            }

            // Move node to new layer
            moveNodeDown(node);
            assert layers.get(layer).contains(node);
            assert node.layer == layer;
        }

        private void applyAddLinkAction(Link l) {
            Vertex from = l.getFrom().getVertex();
            Vertex to = l.getTo().getVertex();
            LayoutNode fromNode = vertexToLayoutNode.get(from);
            LayoutNode toNode = vertexToLayoutNode.get(to);

            if (!nodes.contains(fromNode) || !nodes.contains(toNode) || to.equals(from)) {
                return;
            }

            if (toNode.layer == fromNode.layer) {
                handleNeighborNodesOnSameLayer(fromNode, toNode);
            }

            LayoutEdge edge = new LayoutEdge();
            edge.link = l;
            edge.from = fromNode;
            edge.relativeFrom = l.getFrom().getRelativePosition().x;
            edge.to = toNode;
            edge.relativeTo = l.getTo().getRelativePosition().x;

            boolean reversedLink = fromNode.layer > toNode.layer;
            if (reversedLink) {
                // Reversed link
                reversedLinks.add(l);

                LayoutNode temp = fromNode;
                fromNode = toNode;
                toNode = temp;

                int oldRelativeFrom = edge.relativeFrom;
                int oldRelativeTo = edge.relativeTo;

                edge.from = fromNode;
                edge.to = toNode;
                edge.relativeFrom = oldRelativeTo;
                edge.relativeTo = oldRelativeFrom;
            }

            fromNode.succs.add(edge);
            toNode.preds.add(edge);

            if (reversedLink) {
                updateReversedLinkPositions(l);
            }

            if (fromNode.layer != toNode.layer - 1) {
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
                        if (fromNode.layer > i) {
                            curReversedEdges += 1;
                        } else if (fromNode.layer == i) {
                            curNeighborsOnSameLayer += 1;
                        }
                        curTotalEdgeLength += Math.abs(fromNode.layer - i);
                    }
                    if (link.getFrom().getVertex().equals(vertex) && toNode != null) {
                        if (toNode.layer < i) {
                            curReversedEdges += 1;
                        } else if (toNode.layer == i) {
                            curNeighborsOnSameLayer += 1;
                        }
                        curTotalEdgeLength += Math.abs(i - toNode.layer);
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
            LayoutNode node = new LayoutNode();
            Dimension size = action.vertex.getSize();
            node.width = (int) size.getWidth();
            node.height = (int) size.getHeight();
            node.vertex = action.vertex;

            List<Link> links = new ArrayList<>();
            for (LinkAction a : action.linkActions) {
                links.add(a.link);
            }
            int layer = optimalLayer(action.vertex, links);

            // Temporarily add the links so that the node insertion accounts for edge
            // crossings
            for (Link l : links) {
                LayoutEdge e = new LayoutEdge();
                if (l.getTo().getVertex().equals(action.vertex)
                        && nodes.contains(vertexToLayoutNode.get(l.getFrom().getVertex()))) {
                    e.to = node;
                    e.from = vertexToLayoutNode.get(l.getFrom().getVertex());
                    e.relativeFrom = l.getFrom().getRelativePosition().x;
                    e.relativeTo = l.getTo().getRelativePosition().x;
                    node.preds.add(e);
                } else if (l.getFrom().getVertex().equals(action.vertex)
                        && nodes.contains(vertexToLayoutNode.get(l.getTo().getVertex()))) {
                    e.from = node;
                    e.to = vertexToLayoutNode.get(l.getTo().getVertex());
                    e.relativeFrom = l.getFrom().getRelativePosition().x;
                    e.relativeTo = l.getTo().getRelativePosition().x;
                    node.succs.add(e);
                }
            }
            insertNode(node, layer);
            node.succs.clear();
            node.preds.clear();

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

            if (toNode.layer < fromNode.layer) {
                // Reversed edge
                LayoutNode temp = toNode;
                toNode = fromNode;
                fromNode = temp;

                reversedLinks.remove(l);
                reversedLinkEndPoints.remove(l);
                reversedLinkStartPoints.remove(l);
            }

            // Remove preds-edges bottom up, starting at "to" node
            // Cannot start from "from" node since there might be joint edges
            List<LayoutEdge> toNodePredsEdges = List.copyOf(toNode.preds);
            for (LayoutEdge edge : toNodePredsEdges) {
                LayoutNode n = edge.from;
                LayoutEdge edgeToRemove;

                if (edge.link != null && edge.link.equals(l)) {
                    toNode.preds.remove(edge);
                    edgeToRemove = edge;
                } else {
                    // Wrong edge, look at next
                    continue;
                }

                if (n.vertex != null && n.vertex.equals(from)) {
                    // No dummy nodes inbetween 'from' and 'to' vertex
                    n.succs.remove(edgeToRemove);
                    break;
                } else {
                    // Must remove edges between dummy nodes
                    boolean found = true;
                    LayoutNode prev = toNode;
                    while (n.vertex == null && found) {
                        found = false;

                        if (n.succs.size() <= 1 && n.preds.size() <= 1) {
                            // Dummy node used only for this link, remove if not already removed
                            if (nodes.contains(n)) {
                                removeNode(n);
                            }
                        } else {
                            // anchor node, should not be removed
                            break;
                        }

                        if (n.preds.size() == 1) {
                            n.succs.remove(edgeToRemove);
                            prev = n;
                            edgeToRemove = n.preds.get(0);
                            n = edgeToRemove.from;
                            found = true;
                        }
                    }

                    n.succs.remove(edgeToRemove);
                    prev.preds.remove(edgeToRemove);
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
            int layer = node.layer;
            int pos = node.pos;
            List<LayoutNode> remainingLayerNodes = layers.get(layer);
            assert remainingLayerNodes.contains(node);
            remainingLayerNodes.remove(node);

            // Update position of remaining nodes on the same layer
            boolean onlyDummiesLeft = true;
            for (LayoutNode n : remainingLayerNodes) {
                if (n.pos > pos) {
                    n.pos -= 1;
                }
                if (n.vertex != null || n.preds.size() > 1) {
                    onlyDummiesLeft = false;
                }
            }

            if (onlyDummiesLeft && shouldRemoveEmptyLayers) {
                layers.remove(layer);
                for (int i = layer + 1; i <= layers.size(); i++) {
                    List<LayoutNode> list = layers.get(i);
                    layers.remove(i);
                    layers.put(i - 1, list);
                    for (LayoutNode n : list) {
                        n.layer -= 1;
                    }
                }
                for (LayoutNode n : remainingLayerNodes) {
                    if (n.preds.size() == 1) {
                        LayoutEdge predEdge = n.preds.get(0);
                        LayoutNode fromNode = predEdge.from;
                        fromNode.succs.remove(predEdge);
                        for (LayoutEdge e : n.succs) {
                            e.from = fromNode;
                            e.relativeFrom = predEdge.relativeFrom;
                            fromNode.succs.add(e);
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
                for (LayoutEdge e : n.preds) {
                    if (e.link != null) {
                        layoutedLinks.add(e.link);
                    }
                }
                if (n.vertex != null) {
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

            // Reset all values before assigning y-coordinates
            for (LayoutNode n : nodes) {
                if (n.vertex != null) {
                    updateNodeWithReversedEdges(n);
                } else {
                    n.height = DUMMY_HEIGHT;
                }
                n.y = 0;
            }

            int curY = 0;

            for (int i = 0; i < layers.size(); i++) {
                List<LayoutNode> layer = layers.get(i);
                int maxHeight = 0;
                int baseLine = 0;
                int bottomBaseLine = 0;
                for (LayoutNode n : layer) {
                    maxHeight = Math.max(maxHeight, n.height - n.yOffset - n.bottomYOffset);
                    baseLine = Math.max(baseLine, n.yOffset);
                    bottomBaseLine = Math.max(bottomBaseLine, n.bottomYOffset);
                }

                int maxXOffset = 0;
                for (LayoutNode n : layer) {
                    if (n.vertex == null) {
                        // Dummy node
                        n.y = curY;
                        n.height = maxHeight + baseLine + bottomBaseLine;

                    } else {
                        n.y = curY + baseLine + (maxHeight - (n.height - n.yOffset - n.bottomYOffset)) / 2 - n.yOffset;
                    }

                    for (LayoutEdge e : n.succs) {
                        int curXOffset = Math.abs(n.x - e.to.x);
                        maxXOffset = Math.max(curXOffset, maxXOffset);
                    }
                }

                curY += maxHeight + baseLine + bottomBaseLine;
                curY += LAYER_OFFSET + ((int) (Math.sqrt(maxXOffset) * 1.5));
            }
        }
    }

    private class WriteResult {

        private List<Point> edgePoints(LayoutEdge e) {
            ArrayList<Point> points = new ArrayList<>();

            Point p = new Point(e.to.x + e.relativeTo,
                    e.to.y + e.to.yOffset + e.link.getTo().getRelativePosition().y);
            points.add(p);
            if (e.to.inOffsets.containsKey(e.relativeTo)) {
                points.add(new Point(p.x,
                        p.y + e.to.inOffsets.get(e.relativeTo) + e.link.getTo().getRelativePosition().y));
            }

            LayoutNode cur = e.from;
            LayoutEdge curEdge = e;
            while (cur.vertex == null && !cur.preds.isEmpty()) {
                if (points.size() > 1 && points.get(points.size() - 1).x == cur.x + cur.width / 2
                        && points.get(points.size() - 2).x == cur.x + cur.width / 2) {
                    // On the same vertical line, can remove previous point
                    points.remove(points.size() - 1);
                }
                // Top of the dummy node
                points.add(new Point(cur.x + cur.width / 2, cur.y + cur.height));
                if (points.size() > 1 && points.get(points.size() - 1).x == cur.x + cur.width / 2
                        && points.get(points.size() - 2).x == cur.x + cur.width / 2) {
                    points.remove(points.size() - 1);
                }
                // Bottom of the dummy node
                points.add(new Point(cur.x + cur.width / 2, cur.y));
                assert cur.preds.size() == 1;
                curEdge = cur.preds.get(0);
                cur = curEdge.from;
            }

            p = new Point(cur.x + curEdge.relativeFrom, cur.y + cur.height - cur.bottomYOffset
                    + (curEdge.link == null ? 0 : curEdge.link.getFrom().getRelativePosition().y));
            if (curEdge.from.outOffsets.containsKey(curEdge.relativeFrom)) {
                points.add(new Point(p.x, p.y + curEdge.from.outOffsets.get(curEdge.relativeFrom)
                        + (curEdge.link == null ? 0 : curEdge.link.getFrom().getRelativePosition().y)));
            }
            points.add(p);

            Collections.reverse(points);

            if (reversedLinks.contains(e.link)) {
                Collections.reverse(points);

                assert reversedLinkStartPoints.containsKey(e.link);
                for (Point p1 : reversedLinkStartPoints.get(e.link)) {
                    points.add(new Point(p1.x + cur.x, p1.y + cur.y));
                }

                assert reversedLinkEndPoints.containsKey(e.link);
                for (Point p1 : reversedLinkEndPoints.get(e.link)) {
                    points.add(0, new Point(p1.x + e.to.x, p1.y + e.to.y));
                }
            }

            return points;
        }

        void run() {
            HashMap<Vertex, Point> vertexPositions = new HashMap<>();
            HashMap<Link, List<Point>> linkPositions = new HashMap<>();

            for (LayoutNode n : nodes) {

                if (n.vertex != null) {
                    assert !vertexPositions.containsKey(n.vertex);
                    vertexPositions.put(n.vertex, new Point(n.x + n.xOffset, n.y + n.yOffset));
                } else {
                    continue;
                }

                // All edges can be drawn from bottom up, the links are stored in the preds list
                // of each node
                for (LayoutEdge e : n.preds) {
                    if (e.link != null && !linkPositions.containsKey(e.link)) {
                        List<Point> points = edgePoints(e);
                        assert !linkPositions.containsKey(e.link);
                        linkPositions.put(e.link, points);
                    }
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
