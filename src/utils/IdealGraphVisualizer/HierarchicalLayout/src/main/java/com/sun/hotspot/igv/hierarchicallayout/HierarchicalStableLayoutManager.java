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
import java.awt.Dimension;
import java.awt.Point;
import java.util.*;

public class HierarchicalStableLayoutManager {

    public static final int DUMMY_HEIGHT = 1;
    public static final int DUMMY_WIDTH = 1;
    public static final int X_OFFSET = 8;
    public static final int LAYER_OFFSET = 8;
    // Algorithm global datastructures
    private HashSet<? extends Vertex> currentVertices;
    private HashSet<? extends Link> currentLinks;
    private Set<Link> reversedLinks;
    private List<LayoutNode> nodes;
    private final List<LayoutNode> oldNodes;
    private final HashMap<Vertex, LayoutNode> vertexToLayoutNode;
    private final HashMap<Vertex, LayoutNode> oldVertexToLayoutNode;
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
    private final boolean shouldRemoveEmptyLayers = false;
    private final boolean shouldComputeLayoutScore = false;

    enum Action {
        ADD,
        REMOVE
    }

    private class VertexAction {
        public Vertex vertex;
        public List<LinkAction> linkActions = new LinkedList<>();
        public Action action;

        public VertexAction(Vertex vertex, Action action) {
            this.vertex = vertex;
            this.action = action;
        }
    }

    private class LinkAction {
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
        oldVertexToLayoutNode = new HashMap<>();
        oldNodes = new ArrayList<>();
    }

    private int calculateOptimalBoth(LayoutNode n) {
        if (n.preds.size() == 0 && n.succs.size() == 0) {
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

        return median(values);
    }

    private int median(int[] values) {
        Arrays.sort(values);
        if (values.length % 2 == 0) {
            return (values[values.length / 2 - 1] + values[values.length / 2]) / 2;
        } else {
            return values[values.length / 2];
        }
    }

    /**
     * Adjust the X-coordinates of the nodes in the given layer, as a new node has
     * been inserted at that layer
     *
     * @param newNode
     * @param layer
     */
    private void adjustXCoordinates(int layer) {
        List<LayoutNode> nodes = layers.get(layer);
        ArrayList<Integer> space = new ArrayList<>();
        List<LayoutNode> nodeProcessingOrder = new ArrayList<>();

        nodes.sort(nodePositionComparator);

        int curX = 0;
        for (LayoutNode n : nodes) {
            space.add(curX);
            curX += n.width + X_OFFSET;
            nodeProcessingOrder.add(n);
        }

        nodeProcessingOrder.sort(nodeProcessingUpComparator);
        NodeRow r = new NodeRow(space);
        for (LayoutNode n : nodeProcessingOrder) {
            int optimal = calculateOptimalBoth(n);
            r.insert(n, optimal);
        }
    }

    /**
     * Ensure that the datastructures nodes and layerNodes are consistent
     */
    private void sanityCheckNodesAndLayerNodes() {
        int nodeCount = 0;
        for (int i = 0; i < layers.keySet().size(); i++) {
            assert layers.containsKey(i);
            layers.get(i).sort(nodePositionComparator);
            int nodePos = 0;
            for (LayoutNode n : layers.get(i)) {
                assert n.layer == i;
                assert nodes.contains(n);
                assert n.pos == nodePos;
                nodePos += 1;
                nodeCount += 1;
            }
        }
        for (LayoutNode n : nodes) {
            assert n.vertex == null || vertexToLayoutNode.get(n.vertex).equals(n);
            assert n.layer < layers.keySet().size();
            assert layers.get(n.layer).contains(n);
        }
        assert nodeCount == nodes.size();
    }

    private void sanityCheckEdges() {
        for (LayoutNode n : nodes) {
            for (LayoutEdge e : n.preds) {
                assert e.to.equals(n);
                assert e.from.layer == n.layer - 1;
            }
            for (LayoutEdge e : n.succs) {
                assert e.from.equals(n);
                assert e.to.layer == n.layer + 1;
            }
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
        boolean hasReversedDown = reversedDown.size() > 0;

        SortedSet<Integer> reversedUp = null;
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
            node.width += -minX;
        }
    }

    /**
     * Used to compare nodes across consecutive graphs
     */
    private void copyOldNodes() {
        oldNodes.clear();
        oldVertexToLayoutNode.clear();
        for (LayoutNode node : nodes) {
            if (node.vertex != null) {
                LayoutNode nodeCopy = new LayoutNode();
                nodeCopy.x = node.x;
                nodeCopy.y = node.y;
                nodeCopy.xOffset = node.xOffset;
                nodeCopy.yOffset = node.yOffset;
                oldVertexToLayoutNode.put(node.vertex, nodeCopy);
            }
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

            new ApplyActionUpdates().run();

            new AssignYCoordinates().run();

            new WriteResult().run();

            if (!shouldComputeLayoutScore) {
                new ComputeLayoutScore().run();
            }

            sanityCheckEdges();
            sanityCheckNodesAndLayerNodes();
        }

        copyOldNodes();
        oldVertices = new HashSet<>(currentVertices);
        oldLinks = new HashSet<>(currentLinks);
    }

    private class ProcessInput {
        public void removeDuplicateLinks() {
            HashSet<Link> links = new HashSet<>();
            for (Link link1 : currentLinks) {
                if (link1.getTo().getVertex().equals(link1.getFrom().getVertex())) {
                    // self-edge
                    continue;
                }
                boolean duplicate = false;
                for (Link link2 : links) {
                    if (link1.equals(link2)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    links.add(link1);
                }
            }
            currentLinks = links;
        }

        private void run() {
            removeDuplicateLinks();
        }
    }

    private class BuildDatastructure {

        // In case there are changes in the node size, it's layer must be updated
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
                            if (node.width != (int) size.getWidth()) {
                                node.width = (int) size.getWidth();
                                layersToUpdate.add(node.layer);
                            }
                            node.height = (int) size.getHeight();
                            node.vertex = vertex;
                            node.yOffset = 0;
                            node.bottomYOffset = 0;
                            node.inOffsets.clear();
                            node.outOffsets.clear();
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
            sanityCheckNodesAndLayerNodes();
        }
    }

    private class ApplyActionUpdates {
        /**
         * Find the optimal position within the given layer to insert the given node.
         * The optimum is given by the least amount of edge crossings.
         *
         * @param node
         * @param layer
         * @return
         */
        private int optimalPosition(LayoutNode node, int layer) {
            assert layers.keySet().contains(layer);

            List<LayoutNode> layerNodes = layers.get(layer);
            layerNodes.sort(nodePositionComparator);
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

                if (layers.keySet().contains(layer - 1)) {
                    List<LayoutNode> predNodes = layers.get(layer - 1);
                    // For each link with an end point in vertex, check how many edges crosses it
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
                if (layers.keySet().contains(layer + 1)) {
                    List<LayoutNode> succsNodes = layers.get(layer + 1);
                    // For each link with an end point in vertex, check how many edges crosses it
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
         *
         * @param node
         * @param layer
         */
        private void insertNode(LayoutNode node, int layer) {
            node.layer = layer;
            List<LayoutNode> layerNodes = layers.get(layer);

            if (layerNodes.size() == 0) {
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

            if (!nodes.contains(node)) {
                nodes.add(node);
            }
            if (node.vertex != null) {
                vertexToLayoutNode.put(node.vertex, node);
            }

            adjustXCoordinates(layer);

            sanityCheckNodesAndLayerNodes();
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
                removeNode(predNode);
            }

            removeNode(node);
            insertNode(node, node.layer - 1);

            for (LayoutEdge edge : List.copyOf(node.succs)) {
                processSingleEdge(edge);
            }

            sanityCheckEdges();
            sanityCheckNodesAndLayerNodes();
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
                removeNode(succNode);
            }

            removeNode(node);
            insertNode(node, node.layer + 1);

            for (LayoutEdge edge : List.copyOf(node.preds)) {
                processSingleEdge(edge);
            }

            sanityCheckEdges();
            sanityCheckNodesAndLayerNodes();
        }

        private void handleNeighborNodesOnSameLayer(LayoutNode from, LayoutNode to) {
            if (canMoveNodeDown(to)) {
                moveNodeDown(to);
            } else if (canMoveNodeUp(from)) {
                moveNodeUp(from);
            } else {
                expandNewLayerBeneath(to);
            }

            sanityCheckEdges();
            sanityCheckNodesAndLayerNodes();
        }

        /**
         * Create a new layer at node.layer + 1 and move the given node there. Adjust
         * remaining layers numbers
         *
         * @param node
         */
        private void expandNewLayerBeneath(LayoutNode node) {
            int layer = node.layer + 1;

            for (int i = layers.size() - 1; i >= layer; i--) {
                List<LayoutNode> list = layers.get(i);
                layers.remove(i);
                layers.put(i + 1, list);
                for (LayoutNode n : list) {
                    n.layer += 1;
                }
            }

            // Remove node from prev layer and update that layer's remaining nodes'
            // positions
            assert layers.get(layer - 1).contains(node);
            layers.get(layer - 1).remove(node);
            for (LayoutNode n : layers.get(layer - 1)) {
                if (n.pos > node.pos) {
                    n.pos -= 1;
                }
            }
            // Create a new layer and add the node to that layer
            List<LayoutNode> l = new ArrayList<>();
            l.add(node);
            layers.put(layer, l);
            node.layer = layer;
            node.pos = 0;

            for (int i = 0; i < layers.keySet().size(); i++) {
                assert layers.keySet().contains(i);
                for (LayoutNode n : layers.get(i)) {
                    assert n.layer == i;
                    assert nodes.contains(n);
                }
            }

            // Add dummy nodes for edges going across new layer. One for each port on the
            // nodes that has outgoing edges
            List<LayoutNode> predLayer = List.copyOf(layers.get(layer - 1));
            for (LayoutNode n : predLayer) {
                HashMap<Integer, List<LayoutEdge>> portHashes = new HashMap<>();

                for (LayoutEdge e : n.succs) {
                    if (!portHashes.keySet().contains(e.relativeFrom)) {
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
                    }

                    insertNode(dummy, layer);
                }
            }

            // Update edges going into the node, adding dummy node to bridge the gap where
            // the node used to be
            for (LayoutEdge edge : List.copyOf(node.preds)) {
                LayoutNode dummy = new LayoutNode();
                dummy.width = DUMMY_WIDTH;
                dummy.height = DUMMY_HEIGHT;

                LayoutEdge e = new LayoutEdge();
                e.to = edge.to;
                e.relativeTo = edge.relativeTo;
                e.from = dummy;
                e.relativeFrom = dummy.width / 2;
                e.link = edge.link;
                dummy.succs.add(e);
                node.preds.add(e);

                edge.to = dummy;
                edge.relativeTo = dummy.width / 2;
                dummy.preds.add(edge);
                node.preds.remove(edge);

                insertNode(dummy, layer - 1);
            }

            sanityCheckEdges();
            sanityCheckNodesAndLayerNodes();
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

            sanityCheckEdges();
        }

        /**
         * Calculate which layer the given vertex should be inserted at to minimize
         * reversed edges and edge lengths
         * If there are multiple options, choose the bottom most layer
         *
         * @param vertex
         * @param links
         * @return
         */
        private int optimalLayer(Vertex vertex, List<Link> links) {
            if (vertex.isRoot()) {
                return 0;
            }

            int reversedEdges = Integer.MAX_VALUE;
            int totalEdgeLength = Integer.MAX_VALUE;
            int neighborsOnSameLayer = Integer.MAX_VALUE;
            int layer = -1;
            for (int i = 0; i < layers.size(); i++) {
                // System.out.println("Testing layer " + i);
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
            // System.out.println("Adding vertex " + action.vertex);
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
                // System.out.println("Associated action " + a.action + " on " + a.link);
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

                        if (n.vertex == null && n.succs.size() <= 1 && n.preds.size() <= 1) {
                            // Dummy node used only for this link, remove if not already removed
                            if (nodes.contains(n)) {
                                removeNode(n);
                            }
                        } else {
                            // anchor node, should not be removed
                            break;
                        }

                        if (n.preds.size() == 1) {
                            prev = n;
                            edgeToRemove = n.preds.get(0);
                            n = edgeToRemove.from;
                            found = true;
                        }
                    }

                    n.succs.remove(edgeToRemove);
                    prev.preds.remove(edgeToRemove);
                }
                sanityCheckEdges();
                break;
            }
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
                if (n.vertex != null) {
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
                    assert n.preds.size() == 1;
                    LayoutEdge predEdge = n.preds.get(0);
                    LayoutNode fromNode = predEdge.from;
                    fromNode.succs.remove(predEdge);
                    for (LayoutEdge e : n.succs) {
                        e.from = fromNode;
                        e.relativeFrom = predEdge.relativeFrom;
                        fromNode.succs.add(e);
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
            // System.out.println("Removing " + node);

            assert nodes.contains(node);

            // Remove associated edges
            for (LinkAction a : action.linkActions) {
                // System.out.println("Associated action " + a.action + " on " + a.link);
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
                        System.out.println("Should not be here");
                }
            }

            for (LinkAction action : linkActions) {
                if (action.action == Action.ADD) {
                    applyAddLinkAction(action.link);
                }
            }

            sanityCheckEdges();
            sanityCheckNodesAndLayerNodes();

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
            Set<Link> missingLinks = new HashSet<>(currentLinks);
            missingLinks.removeAll(layoutedLinks);
            for (Link l : missingLinks) {
                applyAddLinkAction(l);
                layoutedLinks.add(l);
            }
            Set<Link> superfluousLinks = new HashSet<>(layoutedLinks);
            superfluousLinks.removeAll(currentLinks);
            for (Link l : superfluousLinks) {
                applyRemoveLinkAction(l);
                layoutedLinks.remove(l);
            }
            assert currentVertices.size() == layoutedNodes.size();
            assert currentLinks.size() == layoutedLinks.size();
        }
    }

    private static final Comparator<LayoutNode> nodePositionComparator = Comparator.comparingInt(n -> n.pos);
    private static final Comparator<LayoutNode> nodeProcessingUpComparator = (n1, n2) -> {
        if (n1.vertex == null) {
            if (n2.vertex == null) {
                return 0;
            }
            return -1;
        }
        if (n2.vertex == null) {
            return 1;
        }
        return n1.succs.size() - n2.succs.size();
    };

    private static class NodeRow {

        private final TreeSet<LayoutNode> treeSet;
        private final ArrayList<Integer> space;

        public NodeRow(ArrayList<Integer> space) {
            treeSet = new TreeSet<>(nodePositionComparator);
            this.space = space;
        }

        public int offset(LayoutNode n1, LayoutNode n2) {
            int v1 = space.get(n1.pos) + n1.width;
            int v2 = space.get(n2.pos);
            return v2 - v1;
        }

        public void insert(LayoutNode n, int pos) {

            SortedSet<LayoutNode> headSet = treeSet.headSet(n);

            LayoutNode leftNeighbor;
            int minX = Integer.MIN_VALUE;
            if (!headSet.isEmpty()) {
                leftNeighbor = headSet.last();
                minX = leftNeighbor.x + leftNeighbor.width + offset(leftNeighbor, n);
            }

            if (pos < minX) {
                n.x = minX;
            } else {

                LayoutNode rightNeighbor;
                SortedSet<LayoutNode> tailSet = treeSet.tailSet(n);
                int maxX = Integer.MAX_VALUE;
                if (!tailSet.isEmpty()) {
                    rightNeighbor = tailSet.first();
                    maxX = rightNeighbor.x - offset(n, rightNeighbor) - n.width;
                }

                n.x = Math.min(pos, maxX);

                assert minX <= maxX : minX + " vs " + maxX;
            }

            treeSet.add(n);
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

    private class ComputeLayoutScore {
        /**
         * https://www.geeksforgeeks.org/check-if-two-given-line-segments-intersect/
         *
         * @param p
         * @param q
         * @param r
         * @return true if point q lies on line segment 'pr'
         */
        private boolean onSegment(Point p, Point q, Point r) {
            if (q.x <= Math.max(p.x, r.x) && q.x >= Math.min(p.x, r.x) &&
                    q.y <= Math.max(p.y, r.y) && q.y >= Math.min(p.y, r.y))
                return true;

            return false;
        }

        /**
         * To find orientation of ordered triplet (p, q, r).
         * https://www.geeksforgeeks.org/check-if-two-given-line-segments-intersect/
         *
         * @return 0 --> p, q and r are collinear, 1 --> Clockwise, 2 -->
         *         Counterclockwise
         */
        private int orientation(Point p, Point q, Point r) {
            int val = (q.y - p.y) * (r.x - q.x) -
                    (q.x - p.x) * (r.y - q.y);

            if (val == 0)
                return 0; // collinear

            return (val > 0) ? 1 : 2; // clock or counterclock wise
        }

        /**
         * https://www.geeksforgeeks.org/check-if-two-given-line-segments-intersect/
         *
         * @return true if line segment 'p1q1' and 'p2q2' intersect.
         */
        private boolean doIntersect(Point p1, Point q1, Point p2, Point q2) {
            // Share the same port
            if (p1.equals(p2) || q1.equals(q2) || p1.equals(q2) || q1.equals(p2)) {
                return false;
            }

            // Find the four orientations needed for general and
            // special cases
            int o1 = orientation(p1, q1, p2);
            int o2 = orientation(p1, q1, q2);
            int o3 = orientation(p2, q2, p1);
            int o4 = orientation(p2, q2, q1);

            // General case
            if (o1 != o2 && o3 != o4)
                return true;

            // Special Cases
            // p1, q1 and p2 are collinear and p2 lies on segment p1q1
            if (o1 == 0 && onSegment(p1, p2, q1)) {
                return true;
            }

            // p1, q1 and q2 are collinear and q2 lies on segment p1q1
            if (o2 == 0 && onSegment(p1, q2, q1)) {
                return true;
            }

            // p2, q2 and p1 are collinear and p1 lies on segment p2q2
            if (o3 == 0 && onSegment(p2, p1, q2)) {
                return true;
            }

            // p2, q2 and q1 are collinear and q1 lies on segment p2q2
            if (o4 == 0 && onSegment(p2, q1, q2)) {
                return true;
            }

            return false; // Doesn't fall in any of the above cases
        }

        private int totalEdgeCrossings() {
            HashMap<Link, List<Point>> linkPositions = new HashMap<>();
            for (LayoutNode node : nodes) {
                if (node.vertex == null) {
                    continue;
                }
                for (LayoutEdge e : node.preds) {
                    if (e.link != null && !linkPositions.keySet().contains(e.link)) {
                        List<Point> points = new WriteResult().edgePoints(e);

                        // Merged edges creates duplicate edge segments
                        for (Link l : linkPositions.keySet()) {
                            // Exists edge from same vertex, same port
                            if (l.getFrom().getVertex().equals(e.link.getFrom().getVertex())
                                    && l.getFrom().getRelativePosition().x == e.link.getFrom()
                                    .getRelativePosition().x) {
                                List<Point> duplicatePoints = new ArrayList<>();
                                for (Point p : points) {
                                    if (linkPositions.get(l).contains(p)) {
                                        duplicatePoints.add(p);
                                    }
                                }
                                if (duplicatePoints.size() > 1) {
                                    // Should not remove anchor point
                                    duplicatePoints.remove(duplicatePoints.size() - 1);
                                    points.removeAll(duplicatePoints);
                                }
                            }
                        }

                        linkPositions.put(e.link, points);
                    }
                }
            }

            int crossings = 0;

            for (Link l1 : linkPositions.keySet()) {
                for (Link l2 : linkPositions.keySet()) {
                    if (l1.equals(l2) || (l1.getFrom().getVertex().equals(l2.getFrom().getVertex())
                            && l1.getFrom().getRelativePosition().x == l2.getFrom().getRelativePosition().x)) {
                        continue;
                    }

                    List<Point> pointsLink1 = linkPositions.get(l1);
                    List<Point> pointsLink2 = linkPositions.get(l2);

                    for (int i = 1; i < pointsLink1.size(); i++) {
                        Point p1 = pointsLink1.get(i - 1);
                        Point q1 = pointsLink1.get(i);
                        for (int j = 1; j < pointsLink2.size(); j++) {
                            Point p2 = pointsLink2.get(j - 1);
                            Point q2 = pointsLink2.get(j);

                            if (doIntersect(p1, q1, p2, q2)) {
                                crossings += 1;
                            }
                        }
                    }
                }
            }
            // Double counting every crossing
            return crossings / 2;
        }

        private float averageEdgeLength() {
            float totLength = 0;
            int edgeCount = 0;

            for (LayoutNode node : nodes) {
                if (node.vertex == null)
                    continue;
                for (LayoutEdge e : node.preds) {
                    if (e.link == null)
                        continue;
                    List<Point> points = new WriteResult().edgePoints(e);
                    float edgeLength = 0;
                    Point prevPoint = points.get(0);
                    for (int i = 1; i < points.size(); i++) {
                        Point point = points.get(i);
                        edgeLength += prevPoint.distance(point);
                        prevPoint = point;
                    }
                    totLength += edgeLength;
                    edgeCount += 1;
                }
            }

            if (edgeCount > 0) {
                return totLength / edgeCount;
            } else {
                return 0;
            }
        }

        /**
         * Computes how much the bends in an edge deviates from being straight, on
         * average
         *
         * @return
         */
        private float averageEdgeBendDegrees() {
            float totDegree = 0;
            int edgeCount = 0;

            for (LayoutNode node : nodes) {
                if (node.vertex == null)
                    continue;
                for (LayoutEdge e : node.preds) {
                    if (e.link == null)
                        continue;
                    List<Point> points = new WriteResult().edgePoints(e);
                    Point prevPoint = points.get(0);
                    Point curPoint = points.get(1);
                    for (int i = 2; i < points.size(); i++) {
                        Point nextPoint = points.get(i);

                        double x1 = prevPoint.getX() - curPoint.getX();
                        double y1 = prevPoint.getY() - curPoint.getY();
                        double x2 = nextPoint.getX() - curPoint.getX();
                        double y2 = nextPoint.getY() - curPoint.getY();

                        double dotProduct = x1 * x2 + y1 * y2;
                        double prevMagnitude = Math.sqrt(Math.pow(x1, 2) + Math.pow(y1, 2));
                        double nextMagnitude = Math.sqrt(Math.pow(x2, 2) + Math.pow(y2, 2));
                        double cos = dotProduct / (prevMagnitude * nextMagnitude);
                        double angle = Math.acos(cos);

                        if (angle < Math.PI) {
                            totDegree += Math.abs(Math.PI - angle);
                        }

                        prevPoint = curPoint;
                        curPoint = nextPoint;
                    }
                    edgeCount += 1;
                }
            }

            if (edgeCount > 0) {
                return totDegree / edgeCount;
            } else {
                return 0;
            }
        }

        private int reversedEdges() {
            return reversedLinks.size();
        }

        private float averageNodeDisplacement() {
            HashSet<Vertex> commonVertices = new HashSet<>(oldVertices);
            commonVertices.retainAll(currentVertices);

            float totalDisplacement = 0;
            for (Vertex vertex : commonVertices) {
                LayoutNode node = vertexToLayoutNode.get(vertex);
                int x1 = node.x + node.xOffset;
                int y1 = node.y + node.yOffset;
                LayoutNode oldNode = oldVertexToLayoutNode.get(vertex);
                int x2 = oldNode.x + oldNode.xOffset;
                int y2 = oldNode.y + oldNode.yOffset;
                totalDisplacement += Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
            }
            return totalDisplacement / commonVertices.size();
        }

        public void run() {
            int edgeCrossings = totalEdgeCrossings();
            float edgeBendsDeg = averageEdgeBendDegrees();
            float edgeLength = averageEdgeLength();
            float nodeDisplacement = averageNodeDisplacement();
            int reversedEdges = reversedEdges();
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
            while (cur.vertex == null && cur.preds.size() != 0) {
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
            assert currentLinks.size() == linkPositions.keySet().size();

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
