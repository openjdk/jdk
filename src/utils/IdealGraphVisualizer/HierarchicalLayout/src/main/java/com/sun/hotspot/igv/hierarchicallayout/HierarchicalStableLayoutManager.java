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

import static com.sun.hotspot.igv.hierarchicallayout.LayoutNode.NODE_POS_COMPARATOR;
import static com.sun.hotspot.igv.hierarchicallayout.LayoutNode.NODE_PROCESSING_UP_COMPARATOR;
import com.sun.hotspot.igv.layout.LayoutGraph;
import com.sun.hotspot.igv.layout.LayoutManager;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Vertex;
import com.sun.hotspot.igv.util.Statistics;
import java.awt.Dimension;
import java.awt.Point;
import java.util.*;

public class HierarchicalStableLayoutManager extends LayoutManager {

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
    private final LinkedHashMap<Vertex, LayoutNode> vertexToLayoutNode;
    private final HierarchicalLayoutManager manager;

    // Algorithm global data structures
    private Set<? extends Vertex> currentVertices;
    private Set<? extends Link> currentLinks;
    private Set<Link> reversedLinks;
    private List<LayoutNode> nodes;
    private HashMap<Link, List<Point>> reversedLinkStartPoints;
    private HashMap<Link, List<Point>> reversedLinkEndPoints;
    private HashMap<Integer, LayoutLayer> layers;
    private HashMap<Vertex, VertexAction> vertexToAction;
    private List<VertexAction> vertexActions;
    private List<LinkAction> linkActions;
    private HashSet<? extends Vertex> oldVertices;
    private HashSet<? extends Link> oldLinks;

    public HierarchicalStableLayoutManager() {
        oldVertices = new HashSet<>();
        oldLinks = new HashSet<>();
        manager = new HierarchicalLayoutManager(true);
        vertexToLayoutNode = new LinkedHashMap<>();
        nodes = new ArrayList<>();
    }

    @Override
    public void setCutEdges(boolean enable) {
        manager.setCutEdges(enable);
        maxLayerLength = enable ? 10 : -1;
    }

    @Override
    public void doLayout(LayoutGraph graph) {
        manager.doLayout(graph);
        nodes = manager.getNodes();
    }

    private int calculateOptimalBoth(LayoutNode n) {
        if (!n.hasPreds() && !n.hasSuccs()) {
            return n.getX();
        }

        int[] values = new int[n.getPreds().size() + n.getSuccs().size()];
        int i = 0;

        for (LayoutEdge e : n.getPreds()) {
            values[i] = e.getFromX() - e.getRelativeToX();
            i++;
        }

        for (LayoutEdge e : n.getSuccs()) {
            values[i] = e.getToX() - e.getRelativeFromX();
            i++;
        }

        return Statistics.median(values);
    }

    /**
     * Adjust the X-coordinates of the nodes in the given layer, as a new node has
     * been inserted at that layer
     */
    private void adjustXCoordinates(int layer) {
        LayoutLayer nodes = layers.get(layer);
        ArrayList<Integer> space = new ArrayList<>();
        List<LayoutNode> nodeProcessingOrder = new ArrayList<>();

        nodes.sort(NODE_POS_COMPARATOR);

        int curX = 0;
        for (LayoutNode n : nodes) {
            space.add(curX);
            curX += n.getWidth() + NODE_OFFSET;
            nodeProcessingOrder.add(n);
        }

        nodeProcessingOrder.sort(NODE_PROCESSING_UP_COMPARATOR);
        NodeRow r = new NodeRow(space);
        for (LayoutNode n : nodeProcessingOrder) {
            int optimal = calculateOptimalBoth(n);
            r.insert(n, optimal);
        }
    }

    private void ensureNeighborEdgeConsistency() {
        for (LayoutNode n : nodes) {
            n.getSuccs().removeIf(e -> !nodes.contains(e.getTo()));
            n.getPreds().removeIf(e -> !nodes.contains(e.getFrom()));
        }
    }

    private void generateActions() {
        HashSet<Link> oldLinks = new HashSet<>(this.oldLinks);

        HashSet<Vertex> addedVertices = new HashSet<>(currentVertices);
        addedVertices.removeAll(oldVertices);

        HashSet<Vertex> removedVertices = new HashSet<>(oldVertices);
        removedVertices.removeAll(currentVertices);

        HashSet<Link> addedLinks = new HashSet<>(currentLinks);
        HashSet<Link> removedLinks = new HashSet<>(oldLinks);
        for (Link currentLink : currentLinks) {
            for (Link oldLink : oldLinks) {
                if (currentLink.equals(oldLink)) {
                    addedLinks.remove(currentLink);
                    removedLinks.remove(oldLink);
                    break;
                }
            }
        }

        for (Vertex addedVertex : addedVertices) {
            VertexAction vertexAction = new VertexAction(addedVertex, Action.ADD);
            vertexActions.add(vertexAction);
            vertexToAction.put(addedVertex, vertexAction);
        }

        for (Vertex removedVertex : removedVertices) {
            VertexAction vertexAction = new VertexAction(removedVertex, Action.REMOVE);
            vertexActions.add(vertexAction);
            vertexToAction.put(removedVertex, vertexAction);
        }

        for (Link addedLink : addedLinks) {
            Vertex toLink = addedLink.getTo().getVertex();
            Vertex fromLink = addedLink.getFrom().getVertex();
            LinkAction linkAction = new LinkAction(addedLink, Action.ADD);

            if (addedVertices.contains(toLink)) {
                vertexToAction.get(toLink).linkActions.add(linkAction);
            }
            if (addedVertices.contains(fromLink)) {
                vertexToAction.get(fromLink).linkActions.add(linkAction);
            }
            if (!addedVertices.contains(toLink) && !addedVertices.contains(fromLink)) {
                linkActions.add(linkAction);
            }
        }

        for (Link removedLink : removedLinks) {
            Vertex toVertex = removedLink.getTo().getVertex();
            Vertex fromVertex = removedLink.getFrom().getVertex();
            LinkAction linkAction = new LinkAction(removedLink, Action.REMOVE);

            if (removedVertices.contains(toVertex)) {
                vertexToAction.get(toVertex).linkActions.add(linkAction);
            }
            if (removedVertices.contains(fromVertex)) {
                vertexToAction.get(fromVertex).linkActions.add(linkAction);
            }
            if (!removedVertices.contains(toVertex) && !removedVertices.contains(fromVertex)) {
                linkActions.add(linkAction);
            }
        }

        vertexActions.sort(vertexActionComparator);
    }

    private void findInitialReversedLinks() {
        for (Link oldLink : oldLinks) {
            for (Link currentLink : currentLinks) {
                if (currentLink.equals(oldLink)) {
                    if (vertexToLayoutNode.get(currentLink.getFrom().getVertex()).getLayer() > vertexToLayoutNode
                            .get(currentLink.getTo().getVertex()).getLayer()) {
                        // Link is reversed
                        reversedLinks.add(currentLink);
                        updateReversedLinkPositions(currentLink);
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
        // Reset node data in case there were previous reversed edges
        node.setWidth((int) node.getVertex().getSize().getWidth());
        node.setHeight((int) node.getVertex().getSize().getHeight());
        node.setTopMargin(0);
        node.setBottomMargin(0);
        node.setLeftMargin(0);
        node.getInOffsets().clear();
        node.getOutOffsets().clear();

        SortedSet<Integer> reversedDown = new TreeSet<>();

        // Reset relativeFrom for all succ edges
        for (LayoutEdge e : node.getSuccs()) {
            if (e.getLink() == null) {
                continue;
            }
            e.setRelativeFromX(e.getLink().getFrom().getRelativePosition().x);
            if (reversedLinks.contains(e.getLink())) {
                e.setRelativeFromX(e.getLink().getTo().getRelativePosition().x);
                reversedDown.add(e.getRelativeFromX());
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
        for (LayoutEdge e : node.getPreds()) {
            if (e.getLink() == null) {
                continue;
            }
            e.setRelativeToX(e.getLink().getTo().getRelativePosition().x);
            if (reversedLinks.contains(e.getLink())) {
                e.setRelativeToX(e.getLink().getFrom().getRelativePosition().x);
                reversedUp.add(e.getRelativeToX());
            }
        }

        final int offset = NODE_OFFSET;

        int curY = 0;
        int curWidth = node.getWidth() + reversedDown.size() * offset;
        for (int pos : reversedDown) {
            ArrayList<LayoutEdge> reversedSuccs = new ArrayList<>();
            for (LayoutEdge e : node.getSuccs()) {
                if (e.getRelativeFromX() == pos && reversedLinks.contains(e.getLink())) {
                    reversedSuccs.add(e);
                    e.setRelativeFromX(curWidth);
                }
            }

            ArrayList<Point> startPoints = new ArrayList<>();
            startPoints.add(new Point(curWidth, curY));
            startPoints.add(new Point(pos, curY));
            startPoints.add(new Point(pos, reversedDown.size() * offset));
            for (LayoutEdge e : reversedSuccs) {
                reversedLinkStartPoints.put(e.getLink(), startPoints);
            }

            node.getInOffsets().put(pos, -curY);
            curY += offset;
            node.setHeight(node.getHeight() + offset);
            node.setTopMargin(node.getTopMargin() + offset);
            curWidth -= offset;
        }

        int widthFactor = reversedDown.size();
        node.setWidth(node.getWidth() + widthFactor * offset);

        int curX = 0;
        int minX = 0;
        if (hasReversedDown) {
            minX = -offset * reversedUp.size();
        }

        int oldNodeHeight = node.getHeight();
        for (int pos : reversedUp) {
            ArrayList<LayoutEdge> reversedPreds = new ArrayList<>();
            for (LayoutEdge e : node.getPreds()) {
                if (e.getRelativeToX() == pos && reversedLinks.contains(e.getLink())) {
                    if (hasReversedDown) {
                        e.setRelativeToX(curX - offset);
                    } else {
                        e.setRelativeToX(node.getWidth() + offset);
                    }

                    reversedPreds.add(e);
                }
            }
            node.setHeight(node.getHeight() + offset);
            ArrayList<Point> endPoints = new ArrayList<>();

            node.setWidth(node.getWidth() + offset);
            if (hasReversedDown) {
                curX -= offset;
                endPoints.add(new Point(curX, node.getHeight()));
            } else {
                curX += offset;
                endPoints.add(new Point(node.getWidth(), node.getHeight()));
            }

            node.getOutOffsets().put(pos - minX, curX);
            curX += offset;
            node.setBottomMargin(node.getBottomMargin() + offset);

            endPoints.add(new Point(pos, node.getHeight()));
            endPoints.add(new Point(pos, oldNodeHeight));
            for (LayoutEdge e : reversedPreds) {
                reversedLinkEndPoints.put(e.getLink(), endPoints);
            }
        }

        if (minX < 0) {
            for (LayoutEdge e : node.getPreds()) {
                e.setRelativeToX(e.getRelativeToX() - minX);
            }

            for (LayoutEdge e : node.getSuccs()) {
                e.setRelativeFromX(e.getRelativeFromX() - minX);
            }

            node.setLeftMargin(-minX);
            node.setWidth(node.getWidth() - minX);
        }
    }

    public void updateLayout(Set<? extends Vertex> vertices, Set<? extends Link> links) {
        currentVertices = vertices;
        currentLinks = links;

        reversedLinks = new HashSet<>();
        reversedLinkStartPoints = new HashMap<>();
        reversedLinkEndPoints = new HashMap<>();
        vertexActions = new LinkedList<>();
        linkActions = new LinkedList<>();
        vertexToAction = new HashMap<>();

        new ProcessInput().run();

        generateActions();

        new BuildDatastructure().run();

        findInitialReversedLinks();

        // Only apply updates if there are any
        if (!linkActions.isEmpty() || !vertexActions.isEmpty()) {
            new ApplyActionUpdates().run();
        }

        new AssignYCoordinates().run();

        new WriteResult().run();


        oldVertices = new HashSet<>(currentVertices);
        oldLinks = new HashSet<>(currentLinks);
    }

    private void straightenEdges() {
        for (int i = 0; i < layers.size(); i++) {
            straightenLayer(i);
        }

        for (int i = layers.size() - 1; i >= 0; i--) {
            straightenLayer(i);
        }
    }

    private void straightenLayer(int layerNr) {
        LayoutLayer layer = layers.get(layerNr);
        for (LayoutNode node : layer) {
            straightenDown(node);
        }
        for (int i = layer.size() - 1; i >= 0; i--) {
            LayoutNode node = layer.get(i);
            straightenDown(node);
        }
    }

    private void straightenDown(LayoutNode node) {
        if (node.getSuccs().size() == 1) {
            LayoutEdge succEdge = node.getSuccs().get(0);
            LayoutNode succDummy = succEdge.getTo();
            if (!succDummy.isDummy()) return;
            if (node.isDummy()) {
                tryAlignDummy(node.getX(), succDummy);
            } else {
                tryAlignDummy(succEdge.getStartX(), succDummy);
            }
        }
    }

    private void tryAlignDummy(int x, LayoutNode dummy) {
        if (x == dummy.getX()) return;
        LayoutLayer nextLayer = layers.get(dummy.getLayer());

        if (dummy.getX() < x) {
            // try move nextDummyNode.x to the right
            int rightPos = dummy.getPos() + 1;
            if (rightPos < nextLayer.size()) {
                // we have a right neighbor
                LayoutNode rightNode = nextLayer.get(rightPos);
                int rightShift = x - dummy.getX();
                if (dummy.getRight() + rightShift <= rightNode.getLeft()) {
                    // it is possible to shift nextDummyNode right
                    dummy.setX(x);
                }
            } else {
                // nextDummyNode is the right-most node, so we can always move nextDummyNode to the right
                dummy.setX(x);
            }
        } else {
            // try move nextDummyNode.x to the left
            int leftPos = dummy.getPos() - 1;
            if (leftPos >= 0) {
                // we have a left neighbor
                LayoutNode leftNode = nextLayer.get(leftPos);
                int leftShift = dummy.getX() - x;
                if (leftNode.getRight() <= dummy.getLeft() - leftShift) {
                    // it is possible to shift nextDummyNode left
                    dummy.setX(x);
                }
            } else {
                // nextDummyNode is the left-most node, so we can always move nextDummyNode to the left
                dummy.setX(x);
            }
        }
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

    public static class NodeRow {

        private final TreeSet<LayoutNode> treeSet;
        private final ArrayList<Integer> space;

        public NodeRow(ArrayList<Integer> space) {
            treeSet = new TreeSet<>(NODE_POS_COMPARATOR);
            this.space = space;
        }

        public int offset(LayoutNode n1, LayoutNode n2) {
            int v1 = space.get(n1.getPos()) + n1.getWidth();
            int v2 = space.get(n2.getPos());
            return v2 - v1;
        }

        public void insert(LayoutNode n, int pos) {

            SortedSet<LayoutNode> headSet = treeSet.headSet(n);

            LayoutNode leftNeighbor;
            int minX = Integer.MIN_VALUE;
            if (!headSet.isEmpty()) {
                leftNeighbor = headSet.last();
                minX = leftNeighbor.getX() + leftNeighbor.getWidth() + offset(leftNeighbor, n);
            }

            if (pos < minX) {
                n.setX(minX);
            } else {

                LayoutNode rightNeighbor;
                SortedSet<LayoutNode> tailSet = treeSet.tailSet(n);
                int maxX = Integer.MAX_VALUE;
                if (!tailSet.isEmpty()) {
                    rightNeighbor = tailSet.first();
                    maxX = rightNeighbor.getX() - offset(n, rightNeighbor) - n.getWidth();
                }

                n.setX(Math.min(pos, maxX));

                assert minX <= maxX : minX + " vs " + maxX;
            }

            treeSet.add(n);
        }
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
                if (!node.isDummy()) {
                    for (Vertex vertex : currentVertices) {
                        if (vertex.equals(node.getVertex())) {
                            Dimension size = vertex.getSize();
                            if (node.getWidth() < (int) size.getWidth()) {
                                layersToUpdate.add(node.getLayer());
                            }
                            node.setWidth((int) size.getWidth());
                            node.setHeight((int) size.getHeight());
                            node.setVertex(vertex);
                            break;
                        }
                    }
                    vertexToLayoutNode.put(node.getVertex(), node);
                } else {
                    node.setHeight(0);
                    node.setWidth(0);
                }
                for (LayoutEdge edge : node.getPreds()) {
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
                for (LayoutEdge edge : node.getSuccs()) {
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

            LayoutLayer layerNodes = layers.get(layer);
            layerNodes.sort(NODE_POS_COMPARATOR);
            int edgeCrossings = Integer.MAX_VALUE;
            int optimalPos = -1;

            // Try each possible position in the layer
            for (int i = 0; i < layerNodes.size() + 1; i++) {
                int xCoord;
                if (i == 0) {
                    xCoord = layerNodes.get(i).getX() - node.getWidth() - 1;
                } else {
                    xCoord = layerNodes.get(i - 1).getX() + layerNodes.get(i - 1).getWidth() + 1;
                }

                int currentCrossings = 0;

                if (layers.containsKey(layer - 1)) {
                    LayoutLayer predNodes = layers.get(layer - 1);
                    // For each link with an end point in vertex, check how many edges cross it
                    for (LayoutEdge edge : node.getPreds()) {
                        if (edge.getFrom().getLayer() == layer - 1) {
                            int fromNodeXCoord = edge.getFromX();
                            int toNodeXCoord = xCoord;
                            if (!node.isDummy()) {
                                toNodeXCoord += edge.getRelativeToX();
                            }
                            for (LayoutNode n : predNodes) {
                                for (LayoutEdge e : n.getSuccs()) {
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
                // Edge crossings across current layer and layer below
                if (layers.containsKey(layer + 1)) {
                    LayoutLayer succsNodes = layers.get(layer + 1);
                    // For each link with an end point in vertex, check how many edges cross it
                    for (LayoutEdge edge : node.getSuccs()) {
                        if (edge.getTo().getLayer() == layer + 1) {
                            int toNodeXCoord = edge.getToX();
                            int fromNodeXCoord = xCoord;
                            if (!node.isDummy()) {
                                fromNodeXCoord += edge.getRelativeFromX();
                            }
                            for (LayoutNode n : succsNodes) {
                                for (LayoutEdge e : n.getPreds()) {
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
            if (!node.isDummy()) {
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
            n.getSuccs().add(e);
            LayoutEdge result = new LayoutEdge(e.getFrom(), n, e.getRelativeFromX(), n.getWidth() / 2, e.getLink());
            n.getPreds().add(result);
            e.setRelativeFromX(n.getWidth() / 2);
            e.getFrom().getSuccs().remove(e);
            e.getFrom().getSuccs().add(result);
            e.setFrom(n);
            insertNode(n, layer);
            return result;
        }

        private void insertDummyNodes(LayoutEdge edge) {
            LayoutNode from = edge.getFrom();
            LayoutNode to = edge.getTo();

            boolean hasEdgeFromSamePort = false;
            LayoutEdge edgeFromSamePort = new LayoutEdge(from, to);

            for (LayoutEdge e : edge.getFrom().getSuccs()) {
                if (e.getRelativeFromX() == edge.getRelativeFromX() && e.getTo().isDummy()) {
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
                while (curEdge.getTo().getLayer() < to.getLayer() - 1 && curEdge.getTo().isDummy() && newEdge) {
                    // Traverse down the chain of dummy nodes linking together the edges originating
                    // from the same port
                    newEdge = false;
                    if (curEdge.getTo().getSuccs().size() == 1) {
                        curEdge = curEdge.getTo().getSuccs().get(0);
                        newEdge = true;
                    } else {
                        for (LayoutEdge e : curEdge.getTo().getSuccs()) {
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

                edge.setFrom(prevDummy);
                edge.setRelativeFromX(prevDummy.getWidth() / 2);
                from.getSuccs().remove(edge);
                prevDummy.getSuccs().add(edge);
                processSingleEdge(edge);
            }
        }

        private boolean canMoveNodeUp(LayoutNode node) {
            if (node.getLayer() == 0) {
                return false;
            }
            int newLayer = node.getLayer() - 1;
            for (LayoutEdge e : node.getPreds()) {
                if (!e.getFrom().isDummy() && e.getFrom().getLayer() == newLayer) {
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
            for (LayoutEdge e : node.getSuccs()) {
                if (!e.getTo().isDummy() && e.getTo().getLayer() == newLayer) {
                    return false;
                }
            }
            return true;
        }

        private void moveNodeUp(LayoutNode node) {
            assert canMoveNodeUp(node);

            List<LayoutEdge> previousPredEdges = List.copyOf(node.getPreds());
            for (LayoutEdge edge : previousPredEdges) {
                LayoutNode predNode = edge.getFrom();
                assert predNode.isDummy();
                for (LayoutEdge e : predNode.getPreds()) {
                    e.setTo(edge.getTo());
                    e.setRelativeToX(edge.getRelativeToX());
                    node.getPreds().add(e);
                    node.getPreds().remove(edge);
                }
                removeNode(predNode, false);
            }

            removeNode(node, false);
            insertNode(node, node.getLayer() - 1);

            for (LayoutEdge edge : List.copyOf(node.getSuccs())) {
                processSingleEdge(edge);
            }
        }

        private void moveNodeDown(LayoutNode node) {
            assert canMoveNodeDown(node);

            List<LayoutEdge> previousSuccEdges = List.copyOf(node.getSuccs());
            for (LayoutEdge edge : previousSuccEdges) {
                LayoutNode succNode = edge.getTo();
                assert succNode.isDummy();
                for (LayoutEdge e : succNode.getSuccs()) {
                    e.setFrom(edge.getFrom());
                    e.setRelativeFromX(edge.getRelativeFromX());
                    node.getSuccs().add(e);
                    node.getSuccs().remove(edge);
                }
                removeNode(succNode, false);
            }
            removeNode(node, false);
            insertNode(node, node.getLayer() + 1);

            for (LayoutEdge edge : List.copyOf(node.getPreds())) {
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

                for (LayoutEdge e : n.getSuccs()) {
                    if (!portHashes.containsKey(e.getRelativeFromX())) {
                        portHashes.put(e.getRelativeFromX(), new ArrayList<>());
                    }
                    portHashes.get(e.getRelativeFromX()).add(e);
                }

                for (Integer i : portHashes.keySet()) {
                    List<LayoutEdge> edges = portHashes.get(i);

                    LayoutNode dummy = new LayoutNode();
                    LayoutEdge newEdge = new LayoutEdge(n, dummy, i, dummy.getWidth() / 2, edges.get(0).getLink());
                    n.getSuccs().add(newEdge);
                    dummy.getPreds().add(newEdge);

                    for (LayoutEdge e : edges) {
                        e.setFrom(dummy);
                        e.setRelativeFromX(dummy.getWidth() / 2);
                        n.getSuccs().remove(e);
                        dummy.getSuccs().add(e);
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

            fromNode.getSuccs().add(edge);
            toNode.getPreds().add(edge);

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
            Dimension size = action.vertex.getSize();
            node.setWidth((int) size.getWidth());
            node.setHeight((int) size.getHeight());

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
                    node.getPreds().add(e);
                } else if (l.getFrom().getVertex().equals(action.vertex)
                        && nodes.contains(vertexToLayoutNode.get(l.getTo().getVertex()))) {
                    LayoutEdge e = new LayoutEdge(node, vertexToLayoutNode.get(l.getTo().getVertex()), l.getFrom().getRelativePosition().x, l.getTo().getRelativePosition().x, null);
                    node.getSuccs().add(e);
                }
            }
            insertNode(node, layer);
            node.getSuccs().clear();
            node.getPreds().clear();

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
                reversedLinkEndPoints.remove(l);
                reversedLinkStartPoints.remove(l);
            }

            // Remove preds-edges bottom up, starting at "to" node
            // Cannot start from "from" node since there might be joint edges
            List<LayoutEdge> toNodePredsEdges = List.copyOf(toNode.getPreds());
            for (LayoutEdge edge : toNodePredsEdges) {
                LayoutNode n = edge.getFrom();
                LayoutEdge edgeToRemove;

                if (edge.getLink() != null && edge.getLink().equals(l)) {
                    toNode.getPreds().remove(edge);
                    edgeToRemove = edge;
                } else {
                    // Wrong edge, look at next
                    continue;
                }

                if (!n.isDummy() && n.getVertex().equals(from)) {
                    // No dummy nodes inbetween 'from' and 'to' vertex
                    n.getSuccs().remove(edgeToRemove);
                    break;
                } else {
                    // Must remove edges between dummy nodes
                    boolean found = true;
                    LayoutNode prev = toNode;
                    while (n.isDummy() && found) {
                        found = false;

                        if (n.getSuccs().size() <= 1 && n.getPreds().size() <= 1) {
                            // Dummy node used only for this link, remove if not already removed
                            if (nodes.contains(n)) {
                                removeNode(n, true);
                            }
                        } else {
                            // anchor node, should not be removed
                            break;
                        }

                        if (n.getPreds().size() == 1) {
                            n.getSuccs().remove(edgeToRemove);
                            prev = n;
                            edgeToRemove = n.getPreds().get(0);
                            n = edgeToRemove.getFrom();
                            found = true;
                        }
                    }

                    n.getSuccs().remove(edgeToRemove);
                    prev.getPreds().remove(edgeToRemove);
                }
                break;
            }

            ensureNeighborEdgeConsistency();
        }

        private void removeNode(LayoutNode node, boolean shouldRemoveEmptyLayers) {
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
                if (!n.isDummy() || n.getPreds().size() > 1) {
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
                    if (n.getPreds().size() == 1) {
                        LayoutEdge predEdge = n.getPreds().get(0);
                        LayoutNode fromNode = predEdge.getFrom();
                        fromNode.getSuccs().remove(predEdge);
                        for (LayoutEdge e : n.getSuccs()) {
                            e.setFrom(fromNode);
                            e.setRelativeFromX(predEdge.getRelativeFromX());
                            fromNode.getSuccs().add(e);
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

            removeNode(node, true);
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
                for (LayoutEdge e : n.getPreds()) {
                    if (e.getLink() != null) {
                        layoutedLinks.add(e.getLink());
                    }
                }
                if (!n.isDummy()) {
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

            straightenEdges();
        }
    }

    private class AssignYCoordinates {
        void run() {

            // Reset all values before assigning y-coordinates
            for (LayoutNode n : nodes) {
                if (!n.isDummy()) {
                    updateNodeWithReversedEdges(n);
                } else {
                    n.setHeight(0);
                }
                n.setY(0);
            }

            int curY = 0;

            for (int i = 0; i < layers.size(); i++) {
                LayoutLayer layer = layers.get(i);
                int maxHeight = 0;
                int baseLine = 0;
                int bottomBaseLine = 0;
                for (LayoutNode n : layer) {
                    maxHeight = Math.max(maxHeight, n.getHeight() - n.getTopMargin() - n.getBottomMargin());
                    baseLine = Math.max(baseLine, n.getTopMargin());
                    bottomBaseLine = Math.max(bottomBaseLine, n.getBottomMargin());
                }

                int maxXOffset = 0;
                for (LayoutNode n : layer) {
                    if (n.isDummy()) {
                        // Dummy node
                        n.setY(curY);
                        n.setHeight(maxHeight + baseLine + bottomBaseLine);

                    } else {
                        n.setY(curY + baseLine + (maxHeight - (n.getHeight() - n.getTopMargin() - n.getBottomMargin())) / 2 - n.getTopMargin());
                    }

                    for (LayoutEdge e : n.getSuccs()) {
                        int curXOffset = Math.abs(n.getX() - e.getTo().getX());
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

            Point p = new Point(e.getToX(),
                    e.getTo().getY() + e.getTo().getTopMargin() + e.getLink().getTo().getRelativePosition().y);
            points.add(p);
            if (e.getTo().getInOffsets().containsKey(e.getRelativeToX())) {
                points.add(new Point(p.x,
                        p.y + e.getTo().getInOffsets().get(e.getRelativeToX()) + e.getLink().getTo().getRelativePosition().y));
            }

            LayoutNode cur = e.getFrom();
            LayoutEdge curEdge = e;
            while (cur.isDummy() && cur.hasPreds()) {
                if (points.size() > 1 && points.get(points.size() - 1).x == cur.getX() + cur.getWidth() / 2
                        && points.get(points.size() - 2).x == cur.getX() + cur.getWidth() / 2) {
                    // On the same vertical line, can remove previous point
                    points.remove(points.size() - 1);
                }
                // Top of the dummy node
                points.add(new Point(cur.getX() + cur.getWidth() / 2, cur.getY() + cur.getHeight()));
                if (points.size() > 1 && points.get(points.size() - 1).x == cur.getX() + cur.getWidth() / 2
                        && points.get(points.size() - 2).x == cur.getX() + cur.getWidth() / 2) {
                    points.remove(points.size() - 1);
                }
                // Bottom of the dummy node
                points.add(new Point(cur.getX() + cur.getWidth() / 2, cur.getY()));
                assert cur.getPreds().size() == 1;
                curEdge = cur.getPreds().get(0);
                cur = curEdge.getFrom();
            }

            p = new Point(cur.getX() + curEdge.getRelativeFromX(), cur.getY() + cur.getHeight() - cur.getBottomMargin()
                    + (curEdge.getLink() == null ? 0 : curEdge.getLink().getFrom().getRelativePosition().y));
            if (curEdge.getFrom().getOutOffsets().containsKey(curEdge.getRelativeFromX())) {
                points.add(new Point(p.x, p.y + curEdge.getFrom().getOutOffsets().get(curEdge.getRelativeFromX())
                        + (curEdge.getLink() == null ? 0 : curEdge.getLink().getFrom().getRelativePosition().y)));
            }
            points.add(p);

            Collections.reverse(points);

            if (reversedLinks.contains(e.getLink())) {
                Collections.reverse(points);

                assert reversedLinkStartPoints.containsKey(e.getLink());
                for (Point p1 : reversedLinkStartPoints.get(e.getLink())) {
                    points.add(new Point(p1.x + cur.getX(), p1.y + cur.getY()));
                }

                assert reversedLinkEndPoints.containsKey(e.getLink());
                for (Point p1 : reversedLinkEndPoints.get(e.getLink())) {
                    points.add(0, new Point(p1.x + e.getTo().getX(), p1.y + e.getTo().getY()));
                }
            }

            return points;
        }

        void run() {
            HashMap<Vertex, Point> vertexPositions = new HashMap<>();
            HashMap<Link, List<Point>> linkPositions = new HashMap<>();

            for (LayoutNode n : nodes) {

                if (!n.isDummy()) {
                    assert !vertexPositions.containsKey(n.getVertex());
                    vertexPositions.put(n.getVertex(), new Point(n.getLeft(), n.getTop()));
                } else {
                    continue;
                }

                // All edges can be drawn from bottom up, the links are stored in the preds list
                // of each node
                for (LayoutEdge e : n.getPreds()) {
                    if (e.getLink() != null && !linkPositions.containsKey(e.getLink())) {
                        List<Point> points = edgePoints(e);
                        assert !linkPositions.containsKey(e.getLink());
                        linkPositions.put(e.getLink(), points);
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
