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

import static com.sun.hotspot.igv.hierarchicallayout.LayoutNode.*;
import com.sun.hotspot.igv.layout.LayoutGraph;
import com.sun.hotspot.igv.layout.LayoutManager;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Vertex;
import com.sun.hotspot.igv.util.Statistics;
import java.awt.Point;
import java.util.*;


public class HierarchicalLayoutManager extends LayoutManager {

    public static final Comparator<Link> LINK_COMPARATOR =
            Comparator.comparing((Link l) -> l.getFrom().getVertex())
                    .thenComparing(l -> l.getTo().getVertex())
                    .thenComparingInt(l -> l.getFrom().getRelativePosition().x)
                    .thenComparingInt(l -> l.getTo().getRelativePosition().x);

    public static final Comparator<LayoutEdge> LAYOUT_EDGE_LAYER_COMPARATOR = Comparator.comparingInt(e -> e.getTo().getLayer());

    private final boolean combine;
    // Algorithm global datastructures
    private final List<LayoutNode> dummyNodes;
    private final LinkedHashMap<Vertex, LayoutNode> vertexToLayoutNode;
    private LayoutGraph graph;
    private int layerCount;
    private List<LayoutLayer> layers;

    public HierarchicalLayoutManager() {
        this(true);
    }

    public HierarchicalLayoutManager(boolean combineEdges) {
        combine = combineEdges;
        maxLayerLength = -1;
        vertexToLayoutNode = new LinkedHashMap<>();
        dummyNodes = new ArrayList<>();
    }

    private void assertLinks() {
        for (LayoutNode node : getLayoutNodes()) {
            if (!node.isDummy()) {
                for (LayoutEdge predEdge : node.getPreds()) {
                    assert predEdge.getLink() != null;
                }
                for (LayoutEdge succEdge : node.getSuccs()) {
                    if (succEdge.getTo().isDummy()) {
                        assert succEdge.getTo().hasSuccs() || succEdge.getLink() != null;
                    } else {
                        assert succEdge.getLink() != null;
                    }
                }
            }
        }
    }

    private void assertNodeSynced() {
        for (LayoutLayer layer : layers) {
            for (LayoutNode node : layer) {
                assert dummyNodes.contains(node) | getLayoutNodes().contains(node);
            }
        }

        for (LayoutNode node : dummyNodes) {
            boolean found = false;
            for (LayoutLayer layer : layers) {
                if (layer.contains(node)) {
                    found = true;
                    break;
                }
            }
            assert found;
        }

        for (LayoutNode node : getLayoutNodes()) {
            boolean found = false;
            for (LayoutLayer layer : layers) {
                if (layer.contains(node)) {
                    found = true;
                    break;
                }
            }
            assert found;
        }
    }

    private void assertLayerNr() {
        for (int l = 0; l < layers.size(); ++l) {
            LayoutLayer layer = layers.get(l);
            for (LayoutNode node : layer) {
                assert node.getLayer() == l;
            }
        }
    }

    private void assertOrder() {
        for (int l = 0; l < layers.size(); ++l) {
            LayoutLayer layer = layers.get(l);
            for (int pos = 1; pos < layer.size(); ++pos) {
                LayoutNode leftNode = layer.get(pos - 1);
                LayoutNode rightNode = layer.get(pos);
                assert leftNode.getPos() + 1 == rightNode.getPos();
                //assert leftNode.x <= rightNode.x;
            }
            for (LayoutNode node : layer) {
                assert node.getLayer() >= 0;
                assert node.getLayer() < layers.size();
                assert node.getPos() >= 0;
                assert node.getPos() < layer.size();
                assert node.getLayer() == l;
                assert node.getPos() < layers.get(node.getLayer()).size();
            }
        }
        assertNodeSynced();
        for (LayoutNode node : dummyNodes) {
            assert node.getLayer() >= 0;
            assert node.getLayer() < layers.size();
            assert node.getPos() >= 0;
            assert node.getPos() < layers.get(node.getLayer()).size();
        }

        for (LayoutNode node : getLayoutNodes()) {
            assert node.getLayer() >= 0;
            assert node.getLayer() < layers.size();
            assert node.getPos() >= 0;
            assert node.getPos() < layers.get(node.getLayer()).size();
        }

        assertNodeSynced();
    }

    private void insertNodeAndAdjustLayer(LayoutNode node) {
        LayoutLayer layer = layers.get(node.getLayer());
        int pos = node.getPos();

        // update pos of the nodes right (and including) of pos
        for (LayoutNode n : layer) {
            if (n.getPos() >= pos) {
                n.setPos(n.getPos() + 1);
            }
        }

        // insert in layer at pos
        if (pos < layer.size()) {
            layer.add(pos, node);
        } else {
            layer.add(node);
        }

        int minX = node.getPos() == 0 ? 0 : layer.get(node.getPos() - 1).getOuterRight() + NODE_OFFSET;
        node.setX(Math.max(node.getX(), minX));

        // update x of the nodes right of inserted node at pos
        int prevRightSide = node.getOuterRight();
        for (LayoutNode n : layer) {
            if (n.getPos() > pos) {
                n.setX(Math.max(n.getX(), prevRightSide + NODE_OFFSET));
                prevRightSide = n.getOuterRight();
            }
        }
        // adjust Y of movedNode
        node.setY(layer.getTop());

        addNode(node);
        assertOrder();
    }

    private void addNode(LayoutNode node) {
        if (node.isDummy()) {
            dummyNodes.add(node);
        } else {
            vertexToLayoutNode.put(node.getVertex(), node);
        }
    }

    // remove a node : do not update x, but assert that pos withing layer is correct
    private void removeNode(LayoutNode node) {
        int layer = node.getLayer();
        layers.get(layer).remove(node);
        updateLayerPositions(layers.get(layer));
        // Remove node from graph layout
        if (node.isDummy()) {
            assert node.isDummy();
            dummyNodes.remove(node);
        } else {
            vertexToLayoutNode.remove(node.getVertex());
        }
    }

    private void applyRemoveLinkAction(Link link) {
        Vertex from = link.getFrom().getVertex();
        Vertex to = link.getTo().getVertex();
        LayoutNode toNode = vertexToLayoutNode.get(to);
        LayoutNode fromNode = vertexToLayoutNode.get(from);

        if (toNode.getLayer() < fromNode.getLayer()) {
            // Reversed edge
            toNode = fromNode;
            toNode.getReversedLinkEndPoints().remove(link);
            fromNode.getReversedLinkStartPoints().remove(link);
        }

        // Remove preds-edges bottom up, starting at "to" node
        // Cannot start from "from" node since there might be joint edges
        List<LayoutEdge> toNodePredsEdges = List.copyOf(toNode.getPreds());
        for (LayoutEdge edge : toNodePredsEdges) {
            LayoutNode predNode = edge.getFrom();
            LayoutEdge edgeToRemove;

            if (edge.getLink() != null && edge.getLink().equals(link)) {
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
                        if (dummyNodes.contains(predNode)) {
                            removeNode(predNode);
                        }
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

    private boolean tryMoveNodeInSamePosition(LayoutNode node, int x, int layerNr) {
        LayoutLayer layer = layers.get(layerNr);
        assert layer.contains(node);
        int leftBound = Integer.MIN_VALUE;
        int rightBound = Integer.MAX_VALUE;
        if (node.getPos() > 0) {
            LayoutNode leftNode = layer.get(node.getPos() - 1);
            leftBound = leftNode.getOuterRight();
        }
        if (node.getPos() < layer.size() - 1) {
            LayoutNode rightNode = layer.get(node.getPos() + 1);
            rightBound = rightNode.getOuterLeft();
        }

        // the node did not change position withing the layer
        if (leftBound < x && x < rightBound) {
            x = Math.max(x, leftBound + NODE_OFFSET);
            x = Math.min(x, rightBound - NODE_OFFSET - node.getOuterWidth());
            // same layer and position, just adjust x pos
            node.setX(x);
            return true;
        }
        return false;
    }

    public void removeEdges(LayoutNode movedNode) {
        for (Link inputLink : graph.getInputLinks(movedNode.getVertex())) {
            if (inputLink.getFrom().getVertex() == inputLink.getTo().getVertex()) continue;
            applyRemoveLinkAction(inputLink);
        }
        for (Link outputLink : graph.getOutputLinks(movedNode.getVertex())) {
            if (outputLink.getFrom().getVertex() == outputLink.getTo().getVertex()) continue;
            applyRemoveLinkAction(outputLink);
        }

        // remove link connected to movedNode
        for (Link link : graph.getLinks()) {
            if (link.getTo().getVertex() == movedNode.getVertex()) {
                link.setControlPoints(new ArrayList<>());
                movedNode.getReversedLinkStartPoints().remove(link);
            } else if (link.getFrom().getVertex() == movedNode.getVertex()) {
                link.setControlPoints(new ArrayList<>());
                movedNode.getReversedLinkEndPoints().remove(link);
            }
        }
        movedNode.setHeight(movedNode.getVertex().getSize().height);
        movedNode.setWidth(movedNode.getVertex().getSize().width);

        assert !movedNode.hasSuccs();
        assert !movedNode.hasPreds();
    }

    public void addEdges(LayoutNode movedNode) {

        // BuildDatastructure
        List<Link> nodeLinks = new ArrayList<>(graph.getInputLinks(movedNode.getVertex()));
        nodeLinks.addAll(graph.getOutputLinks(movedNode.getVertex()));
        nodeLinks.sort(LINK_COMPARATOR);

        Set<LayoutNode> reversedLayoutNodes = new HashSet<>();
        assertOrder();
        for (Link link : nodeLinks) {
            if (link.getFrom().getVertex() == link.getTo().getVertex()) continue;

            assertOrder();
            LayoutEdge layoutEdge = createLayoutEdge(link);
            assertOrder();

            LayoutNode fromNode = layoutEdge.getFrom();
            LayoutNode toNode = layoutEdge.getTo();

            assert fromNode.getLayer() != toNode.getLayer();
            if (fromNode.getLayer() > toNode.getLayer()) {
                assertOrder();
                reverseEdge(layoutEdge);
                assertOrder();
                reversedLayoutNodes.add(fromNode);
                reversedLayoutNodes.add(toNode);
            }
        }
        assertOrder();


        // ReverseEdges
        for (LayoutNode layoutNode : reversedLayoutNodes) {
            computeReversedLinkPoints(layoutNode, false);
        }
        assertOrder();

        // CreateDummyNodes
        createDummiesForNodeSuccessor(movedNode, true);
        for (LayoutEdge predEdge : movedNode.getPreds()) {
            insertDummyNodes(predEdge);
        }

        assertNodeSynced();
        assertLayerNr();

        updatePositions();

        assertNodeSynced();
        assertLayerNr();
        assertOrder();

        assertOrder();
    }

    /**
     * Find the optimal position within the given layer to insert the given node.
     * The optimum is given by the least amount of edge crossings.
     */
    private int optimalPosition(LayoutNode node, int layer) {
        assert 0 <= layer && layer < layers.size();

        layers.get(layer).sort(NODE_POS_COMPARATOR);
        int edgeCrossings = Integer.MAX_VALUE;
        int optimalPos = -1;

        // Try each possible position in the layer
        for (int i = 0; i < layers.get(layer).size() + 1; i++) {
            int xCoord;
            if (i == 0) {
                xCoord = layers.get(layer).get(i).getX() - node.getWidth() - 1;
            } else {
                xCoord = layers.get(layer).get(i - 1).getX() + layers.get(layer).get(i - 1).getWidth() + 1;
            }

            int currentCrossings = 0;

            if (0 <= layer - 1) {
                // For each link with an end point in vertex, check how many edges cross it
                for (LayoutEdge edge : node.getPreds()) {
                    if (edge.getFrom().getLayer() == layer - 1) {
                        int fromNodeXCoord = edge.getFromX();
                        int toNodeXCoord = xCoord;
                        if (!node.isDummy()) {
                            toNodeXCoord += edge.getRelativeToX();
                        }
                        for (LayoutNode n : layers.get(layer - 1)) {
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
            if (layer + 1 < layers.size()) {
                // For each link with an end point in vertex, check how many edges cross it
                for (LayoutEdge edge : node.getSuccs()) {
                    if (edge.getTo().getLayer() == layer + 1) {
                        int toNodeXCoord = edge.getToX();
                        int fromNodeXCoord = xCoord;
                        if (!node.isDummy()) {
                            fromNodeXCoord += edge.getRelativeFromX();
                        }
                        for (LayoutNode n : layers.get(layer + 1)) {
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

    private int findLayer(int y) {
        int optimalLayer = -1;
        int minDistance = Integer.MAX_VALUE;
        for (int l = 0; l < layers.size(); l++) {
            int layerY = layers.get(l).getCenter();
            int distance = Math.abs(layerY - y);
            if (distance < minDistance) {
                minDistance = distance;
                optimalLayer = l;
            }
        }
        assert 0 <= optimalLayer : "did not find a layer to place";
        return optimalLayer;
    }

    public void removeEmptyLayers(int emptyLayerNr) {
        for (LayoutNode layoutNode : getLayoutNodes()) {
            for (LayoutEdge predEdge : layoutNode.getPreds()) {
                assert predEdge.getLink() != null;
            }
        }
        for (LayoutNode layoutNode : layers.get(emptyLayerNr)) {
            assert layoutNode.isDummy() : "has to be empty layer";
        }

        if (0 < emptyLayerNr && emptyLayerNr < layers.size() - 1) {
            LayoutLayer emptyLayer = layers.get(emptyLayerNr);
            for (LayoutNode dummyNode : emptyLayer) {
                assert dummyNode.isDummy();
                LayoutEdge predEdge = dummyNode.getPreds().get(0);
                LayoutNode fromNode = predEdge.getFrom(); // Root
                // remove the dummy node from Root
                fromNode.getSuccs().remove(predEdge);
                if (predEdge.getLink() != null) {
                    assert dummyNode.getSuccs().size() == 1;
                    LayoutEdge succEdge = dummyNode.getSuccs().get(0);
                    succEdge.setLink(predEdge.getLink());
                }

                // modify succEdge to come from fromNode and add to succs
                for (LayoutEdge succEdge : dummyNode.getSuccs()) {
                    succEdge.setFrom(fromNode);
                    fromNode.getSuccs().add(succEdge);
                    succEdge.setRelativeFromX(predEdge.getRelativeFromX());
                }
                assert dummyNode.isDummy();
                dummyNodes.remove(dummyNode);
            }

            for (LayoutNode layoutNode : getLayoutNodes()) {
                for (LayoutEdge predEdge : layoutNode.getPreds()) {
                    assert predEdge.getLink() != null;
                }
            }
        }

        // Create a new ArrayList to store the compacted layers
        List<LayoutLayer> compactedLayers = new ArrayList<>();

        // Copy upper part from layers to compactedLayers
        compactedLayers.addAll(layers.subList(0, emptyLayerNr));

        // Copy lower part from layers to compactedLayers
        compactedLayers.addAll(layers.subList(emptyLayerNr + 1, layers.size()));

        // Replace the old layers list with the new compacted one
        layers = compactedLayers;

        for (int l = emptyLayerNr; l < layers.size(); l++) {
            for (LayoutNode layoutNode : layers.get(l)) {
                layoutNode.setLayer(l);
            }
        }

        for (LayoutNode layoutNode : getLayoutNodes()) {
            for (LayoutEdge predEdge : layoutNode.getPreds()) {
                assert predEdge.getLink() != null;
            }
        }
    }

    public void moveNode(LayoutNode node, int newX, int newLayerNr) {
        LayoutLayer layer = layers.get(newLayerNr);
        int newPos = layer.findPosInLayer(newX);

        // remove from old layer and update positions in old layer
        int oldLayerNr = node.getLayer();
        removeNode(node);

        // set x of movedNode
        node.setX(newX);

        boolean shouldRemoveEmptyLayers = false;
        if (node.getLayer() != newLayerNr) { // insert into a different layer
            node.setLayer(newLayerNr);
            node.setPos(newPos);
            shouldRemoveEmptyLayers = true;
            for (LayoutNode layoutNode : layers.get(oldLayerNr)) {
                if (!layoutNode.isDummy()) {
                    shouldRemoveEmptyLayers = false;
                    break;
                }
            }
        } else { // move within the same layer
            //assert movedNode.pos != newPos; // handled before
            if (node.getPos() < newPos) { // moved to the right
                // adjust because we have already removed movedNode in this layer
                node.setPos(newPos - 1);
            } else { // moved to the left
                node.setPos(newPos);
            }
        }
        insertNodeAndAdjustLayer(node);

        if (shouldRemoveEmptyLayers) {
            removeEmptyLayers(oldLayerNr);
        }
    }

    // check that NO neighbors of node are in a given layer
    private int insertNewLayerIfNeeded(LayoutNode node, int layerNr) {
        for (LayoutNode layoutNode : getLayoutNodes()) {
            for (LayoutEdge predEdge : layoutNode.getPreds()) {
                assert predEdge.getLink() != null;
            }
        }
        for (Link inputLink : graph.getInputLinks(node.getVertex())) {
            if (inputLink.getFrom().getVertex() == inputLink.getTo().getVertex()) continue;
            LayoutNode fromNode = vertexToLayoutNode.get(inputLink.getFrom().getVertex());
            if (fromNode.getLayer() == layerNr) {
                moveExpandLayerDown(layerNr + 1);
                return layerNr + 1;
            }
        }
        for (Link outputLink : graph.getOutputLinks(node.getVertex())) {
            if (outputLink.getFrom().getVertex() == outputLink.getTo().getVertex()) continue;
            LayoutNode toNode = vertexToLayoutNode.get(outputLink.getTo().getVertex());
            if (toNode.getLayer() == layerNr) {
                moveExpandLayerDown(layerNr);
                return layerNr;
            }
        }

        for (LayoutNode layoutNode : getLayoutNodes()) {
            for (LayoutEdge predEdge : layoutNode.getPreds()) {
                assert predEdge.getLink() != null;
            }
        }
        return layerNr;

    }

    private void moveExpandLayerDown(int layerNr) {
        assert layerNr < layers.size();

        layers.add(layerNr, new LayoutLayer());

        for (LayoutNode oldNodeBelow : layers.get(layerNr)) {
            for (LayoutEdge predEdge : oldNodeBelow.getPreds()) {
                LayoutNode dummyNode = createDummyBetween(predEdge);
                assert dummyNode.isDummy();
                dummyNodes.add(dummyNode);
                dummyNode.setLayer(layerNr);
                dummyNode.setX(oldNodeBelow.getX());
                layers.get(layerNr).add(dummyNode);
            }
        }

        layers.get(layerNr).sort(NODE_X_COMPARATOR);
        updateLayerPositions(layers.get(layerNr));


        // update layer field in nodes below layerNr
        for (int l = layerNr + 1; l < layers.size(); l++) {
            for (LayoutNode layoutNode : layers.get(l)) {
                layoutNode.setLayer(l);
            }
        }

        assertPos();
        assertOrder();
    }

    public void assertPos() {
        for (LayoutLayer layer : layers) {
            for (LayoutNode node : layer) {
                assert node.getLayer() >= 0;
                assert node.getLayer() < layers.size();
                assert node.getPos() >= 0;
                assert node.getPos() < layer.size();
            }
        }
    }

    private LayoutNode findDummyNode(LayoutNode layoutNode, Point startPoint, boolean searchPred) {
        for (LayoutEdge edge : searchPred ? layoutNode.getPreds() : layoutNode.getSuccs()) {
            LayoutNode node = searchPred ? edge.getFrom() : edge.getTo();
            if (node.isDummy()) {
                int y = searchPred ? layers.get(node.getLayer()).getBottom() + LAYER_OFFSET : layers.get(node.getLayer()).getTop() - LAYER_OFFSET;
                if (startPoint.x == node.getCenterX() && startPoint.y == y) {
                    return node;
                }
                LayoutNode resultNode = findDummyNode(node, startPoint, searchPred);
                if (resultNode != null) return resultNode;
            }
        }
        return null;
    }

    public boolean moveLink(Vertex linkFromVertex, Point oldFrom, Point newFrom) {
        LayoutNode fromNode = vertexToLayoutNode.get(linkFromVertex);
        boolean isReversed = fromNode.getY() > oldFrom.y;
        LayoutNode movedNode = findDummyNode(fromNode, oldFrom, isReversed);
        if (movedNode != null) {
            Point newLocation = new Point(newFrom.x, newFrom.y + movedNode.getHeight() / 2);
            int newLayerNr = findLayer(newLocation.y);
            if (movedNode.getLayer() == newLayerNr) { // we move the node in the same layer
                boolean hasSamePos = tryMoveNodeInSamePosition(movedNode, newLocation.x, newLayerNr);
                if (!hasSamePos) {
                    moveNode(movedNode, newLocation.x, movedNode.getLayer());
                }
            }
            return true;
        }
        return false;
    }

    public void writeBack() {
        optimizeBackedgeCrossing();
        straightenEdges();
        assertOrder();
        new AssignYCoordinates().run();
        assertOrder();
        new WriteResult().run();
        assertOrder();
    }

    public void moveVertex(Vertex movedVertex, Point newLoc) {
        LayoutNode movedNode = vertexToLayoutNode.get(movedVertex);
        Point newLocation = new Point(newLoc.x, newLoc.y + movedNode.getHeight() / 2);

        int newLayerNr = findLayer(newLocation.y);
        if (movedNode.getLayer() == newLayerNr) { // we move the node in the same layer
            boolean hasSamePos = tryMoveNodeInSamePosition(movedNode, newLocation.x, newLayerNr);
            if (!hasSamePos) {
                moveNode(movedNode, newLocation.x, movedNode.getLayer());
            }
        } else { // only remove edges if we moved the node to a new layer
            newLayerNr = insertNewLayerIfNeeded(movedNode, newLayerNr);
            assertOrder();
            removeEdges(movedNode);
            assertOrder();
            moveNode(movedNode, newLocation.x, newLayerNr);
            assertPos();
            assertOrder();
            addEdges(movedNode);
            assertPos();
        }

        assertOrder();
        writeBack();
    }

    @Override
    public void setCutEdges(boolean enable) {
        maxLayerLength = enable ? 10 : -1;
    }

    public void doLayout(LayoutGraph graph) {

        this.graph = graph;

        // #############################################################
        // Step 1: Build up data structure
        // creates:
        // resets vertexToLayoutNode.clear();
        //        nodes.clear();
        //  - LayoutNodes : nodes, vertexToLayoutNode
        //  - LayoutEdge :  LayoutNode.preds, LayoutNode.succs
        new BuildDatastructure().run();
        assertNodePos();

        // #############################################################
        // STEP 2: Reverse edges, handle backedges
        // resets reversedLinkStartPoints
        // resets reversedLinkEndPoints
        // - LayoutEdge e : reverse backedges
        // - set reversedLinkEndPoints.put(link, endpoints);
        // - set reversedLinkStartPoints.put(link, start-points);
        // - LayoutNode n with reversed edges:
        //      - n.yOffset
        //      - n.xOffset
        //      - n.width
        new ReverseEdges().run(true);
        assertNodePos();

        // #############################################################
        // STEP 3: Assign layers
        // creates layers = new ArrayList<>(layers.size());
        // - sets LayoutNode.layer and inserts in layers[n.layer]
        new AssignLayers().run(true);
        assertNodePos();

        // #############################################################
        // STEP 4: Create dummy nodes
        // - takes List<LayoutNode> nodes
        // - replaces single LayoutEdge between LayoutNodes with a chain of new LayoutEdges and dummy LayoutNodes
        // - adds the new dummy LayoutNodes to nodes
        new CreateDummyNodes().run();
        assertLayerNr();
        assertNodePos();
        assertNodeSynced();

        // #############################################################
        // STEP 5: Crossing Reduction
        // - sets for each LayoutNode n.pos
        new CrossingReduction().run();
        assertNodePos();
        assertOrder();

        // #############################################################
        // STEP 6: Assign X coordinates
        // for all LayoutNode n in nodes
        // - sets n.x
        new AssignXCoordinates().run();
        assertOrder();

        // #############################################################
        // STEP 7: Assign Y coordinates
        // for LayoutLayer layer: layer.y, layer.height
        // for each LayoutNode n
        //  - node.y
        //  - node.topYOffset
        //  - node.bottomYOffset
        new AssignYCoordinates().run();
        assertOrder();

        // #############################################################
        // STEP 8: Write back to interface
        new WriteResult().run();
        assertOrder();
    }

    public List<LayoutNode> getNodes() {
        List<LayoutNode> allNodes = new ArrayList<>(dummyNodes);
        allNodes.addAll(getLayoutNodes());
        return allNodes;
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

    private void reverseEdge(LayoutEdge layoutEdge) {
        layoutEdge.reverse();

        LayoutNode oldFrom = layoutEdge.getFrom();
        LayoutNode oldTo = layoutEdge.getTo();
        int oldRelativeFrom = layoutEdge.getRelativeFromX();
        int oldRelativeTo = layoutEdge.getRelativeToX();

        layoutEdge.setFrom(oldTo);
        layoutEdge.setTo(oldFrom);
        layoutEdge.setRelativeFromX(oldRelativeTo);
        layoutEdge.setRelativeToX(oldRelativeFrom);

        oldFrom.getSuccs().remove(layoutEdge);
        oldFrom.getPreds().add(layoutEdge);
        oldTo.getPreds().remove(layoutEdge);
        oldTo.getSuccs().add(layoutEdge);
    }

    private boolean computeReversedStartPoints(LayoutNode node, boolean left) {
        TreeMap<Integer, ArrayList<LayoutEdge>> sortedDownMap = left ? new TreeMap<>() : new TreeMap<>(Collections.reverseOrder());
        for (LayoutEdge succEdge : node.getSuccs()) {
            if (succEdge.isReversed()) {
                succEdge.setRelativeFromX(succEdge.getLink().getTo().getRelativePosition().x);
                sortedDownMap.putIfAbsent(succEdge.getRelativeFromX(), new ArrayList<>());
                sortedDownMap.get(succEdge.getRelativeFromX()).add(succEdge);
            }
        }

        int offset = NODE_OFFSET + LayoutNode.DUMMY_WIDTH;
        int offsetX = left ? -offset : offset;
        int currentX = left ? 0 : node.getWidth();
        int startY = 0;
        int currentY = 0;
        for (Map.Entry<Integer, ArrayList<LayoutEdge>> entry : sortedDownMap.entrySet()) {
            int startX = entry.getKey();
            ArrayList<LayoutEdge> reversedSuccs = entry.getValue();

            currentX += offsetX;
            currentY -= offset;
            node.setTopMargin(node.getTopMargin() + offset);

            ArrayList<Point> startPoints = new ArrayList<>();
            startPoints.add(new Point(currentX, currentY));
            startPoints.add(new Point(startX, currentY));
            startPoints.add(new Point(startX, startY));
            for (LayoutEdge revEdge : reversedSuccs) {
                revEdge.setRelativeFromX(currentX);
                node.getReversedLinkStartPoints().put(revEdge.getLink(), startPoints);
            }
        }
        node.setLeftMargin(node.getLeftMargin() + (left ? sortedDownMap.size() * offset : 0));
        node.setRightMargin(node.getRightMargin() + (left ? 0 : sortedDownMap.size() * offset));

        return !sortedDownMap.isEmpty();
    }

    private boolean computeReversedEndPoints(LayoutNode node, boolean left) {
        TreeMap<Integer, ArrayList<LayoutEdge>> sortedUpMap = left ? new TreeMap<>() : new TreeMap<>(Collections.reverseOrder());
        for (LayoutEdge predEdge : node.getPreds()) {
            if (predEdge.isReversed()) {
                predEdge.setRelativeToX(predEdge.getLink().getFrom().getRelativePosition().x);
                sortedUpMap.putIfAbsent(predEdge.getRelativeToX(), new ArrayList<>());
                sortedUpMap.get(predEdge.getRelativeToX()).add(predEdge);
            }
        }

        int offset = NODE_OFFSET + LayoutNode.DUMMY_WIDTH;
        int offsetX = left ? -offset : offset;
        int currentX = left ? 0 : node.getWidth();
        int startY = node.getHeight();
        int currentY = node.getHeight();
        for (Map.Entry<Integer, ArrayList<LayoutEdge>> entry : sortedUpMap.entrySet()) {
            int startX = entry.getKey();
            ArrayList<LayoutEdge> reversedPreds = entry.getValue();

            currentX += offsetX;
            currentY += offset;
            node.setBottomMargin(node.getBottomMargin() + offset);

            ArrayList<Point> endPoints = new ArrayList<>();
            endPoints.add(new Point(currentX, currentY));
            endPoints.add(new Point(startX, currentY));
            endPoints.add(new Point(startX, startY));
            for (LayoutEdge revEdge : reversedPreds) {
                revEdge.setRelativeToX(currentX);
                node.getReversedLinkEndPoints().put(revEdge.getLink(), endPoints);
            }
        }
        node.setLeftMargin(node.getLeftMargin() + (left ? sortedUpMap.size() * offset : 0));
        node.setRightMargin(node.getRightMargin() + (left ? 0 : sortedUpMap.size() * offset));

        return !sortedUpMap.isEmpty();
    }

    private void computeReversedLinkPoints(LayoutNode node, boolean reverseLeft) {
        // reset node, except (x, y)
        node.setReverseLeft(reverseLeft);
        node.setWidth(node.getVertex().getSize().width);
        node.setHeight(node.getVertex().getSize().height);
        node.setTopMargin(0);
        node.setBottomMargin(0);
        node.setLeftMargin(0);
        node.setRightMargin(0);
        node.getReversedLinkStartPoints().clear();
        node.getReversedLinkEndPoints().clear();

        //boolean reverseLeft = false; // default is false
        boolean hasReversedDown = computeReversedStartPoints(node, reverseLeft);
        boolean hasReversedUP = computeReversedEndPoints(node, hasReversedDown != reverseLeft);
        assert !hasReversedDown || !node.getReversedLinkStartPoints().isEmpty();
        assert !hasReversedUP || !node.getReversedLinkEndPoints().isEmpty();
    }

    private Collection<LayoutNode> getLayoutNodes() {
        return vertexToLayoutNode.values();
    }

    private void insertDummyNodes(LayoutEdge edge) {
        LayoutNode fromNode = edge.getFrom();
        LayoutNode toNode = edge.getTo();
        if (Math.abs(toNode.getLayer() - fromNode.getLayer()) <= 1) return;

        boolean hasEdgeFromSamePort = false;
        LayoutEdge edgeFromSamePort = new LayoutEdge(fromNode, toNode);
        if (edge.isReversed()) edgeFromSamePort.reverse();

        for (LayoutEdge succEdge : fromNode.getSuccs()) {
            if (succEdge.getRelativeFromX() == edge.getRelativeFromX() && succEdge.getTo().isDummy()) {
                edgeFromSamePort = succEdge;
                hasEdgeFromSamePort = true;
                break;
            }
        }

        if (!hasEdgeFromSamePort) {
            processSingleEdge(edge);
        } else {
            LayoutEdge curEdge = edgeFromSamePort;
            boolean newEdge = true;
            while (curEdge.getTo().getLayer() < toNode.getLayer() - 1 && curEdge.getTo().isDummy() && newEdge) {
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
            fromNode.getSuccs().remove(edge);
            prevDummy.getSuccs().add(edge);
            processSingleEdge(edge);
        }
    }

    private void processSingleEdge(LayoutEdge layoutEdge) {
        LayoutNode layoutNode = layoutEdge.getTo();
        if (layoutEdge.getTo().getLayer() - 1 > layoutEdge.getFrom().getLayer()) {
            LayoutEdge prevEdge = layoutEdge;
            for (int l = layoutNode.getLayer() - 1; l > prevEdge.getFrom().getLayer(); l--) {
                LayoutNode dummyNode = createDummyBetween(prevEdge);
                insertNode(dummyNode, l);
                prevEdge = dummyNode.getPreds().get(0);
            }
        }
    }

    private LayoutNode createDummyBetween(LayoutEdge layoutEdge) {
        LayoutNode dummyNode = new LayoutNode();
        dummyNode.getSuccs().add(layoutEdge);
        LayoutEdge result = new LayoutEdge(layoutEdge.getFrom(), dummyNode, layoutEdge.getRelativeFromX(), 0, null);
        if (layoutEdge.isReversed()) result.reverse();
        dummyNode.getPreds().add(result);
        layoutEdge.setRelativeFromX(0);
        layoutEdge.getFrom().getSuccs().remove(layoutEdge);
        layoutEdge.getFrom().getSuccs().add(result);
        layoutEdge.setFrom(dummyNode);
        return dummyNode;
    }

    private void insertNode(LayoutNode node, int layer) {
        node.setLayer(layer);
        List<LayoutNode> layerNodes = layers.get(layer);

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

        addNode(node);

        if (!node.isDummy()) {
            vertexToLayoutNode.put(node.getVertex(), node);
        }
    }

    public void createDummiesForNodeSuccessor(LayoutNode layoutNode, boolean optimalPos) {
        assert !layoutNode.isDummy();

        if (combine) {
            HashMap<Integer, List<LayoutEdge>> portsToUnprocessedEdges = new HashMap<>();
            ArrayList<LayoutEdge> succs = new ArrayList<>(layoutNode.getSuccs());
            HashMap<Integer, LayoutNode> portToTopNode = new HashMap<>();
            HashMap<Integer, HashMap<Integer, LayoutNode>> portToBottomNodeMapping = new HashMap<>();
            for (LayoutEdge succEdge : succs) {
                int startPort = succEdge.getRelativeFromX();
                LayoutNode fromNode = succEdge.getFrom();
                LayoutNode toNode = succEdge.getTo();
                assert fromNode.getLayer() < toNode.getLayer();

                // edge is longer than one layer => needs dummy nodes
                if (fromNode.getLayer() != toNode.getLayer() - 1) {
                    // the edge needs to be cut
                    if (maxLayerLength != -1 && toNode.getLayer() - fromNode.getLayer() > maxLayerLength) {
                        assert maxLayerLength > 2;
                        // remove the succEdge before replacing it
                        toNode.getPreds().remove(succEdge);
                        fromNode.getSuccs().remove(succEdge);

                        LayoutNode topCutNode = portToTopNode.get(startPort);
                        if (topCutNode == null) {
                            topCutNode = new LayoutNode();
                            topCutNode.setLayer(fromNode.getLayer() + 1);
                            if (optimalPos) {
                                topCutNode.setPos(optimalPosition(topCutNode, topCutNode.getLayer()));
                                topCutNode.setX(0);
                                insertNodeAndAdjustLayer(topCutNode);
                            } else {
                                assert topCutNode.isDummy();
                                dummyNodes.add(topCutNode);
                                layers.get(topCutNode.getLayer()).add(topCutNode);
                            }
                            portToTopNode.put(startPort, topCutNode);
                            portToBottomNodeMapping.put(startPort, new HashMap<>());
                        }
                        assert !fromNode.isDummy();
                        LayoutEdge edgeToTopCut = new LayoutEdge(fromNode, topCutNode, succEdge.getRelativeFromX(), topCutNode.getWidth() / 2, succEdge.getLink());
                        if (succEdge.isReversed()) edgeToTopCut.reverse();
                        fromNode.getSuccs().add(edgeToTopCut);
                        topCutNode.getPreds().add(edgeToTopCut);
                        assert topCutNode.isDummy();

                        HashMap<Integer, LayoutNode> layerToBottomNode = portToBottomNodeMapping.get(startPort);
                        LayoutNode bottomCutNode = layerToBottomNode.get(toNode.getLayer());
                        if (bottomCutNode == null) {
                            bottomCutNode = new LayoutNode();
                            bottomCutNode.setLayer(toNode.getLayer() - 1);
                            if (optimalPos) {
                                bottomCutNode.setPos(optimalPosition(bottomCutNode, bottomCutNode.getLayer()));
                                bottomCutNode.setX(0);
                                insertNodeAndAdjustLayer(bottomCutNode);
                            } else {
                                assert bottomCutNode.isDummy();
                                dummyNodes.add(bottomCutNode);
                                layers.get(bottomCutNode.getLayer()).add(bottomCutNode);
                            }
                            layerToBottomNode.put(toNode.getLayer(), bottomCutNode);
                        }
                        LayoutEdge bottomEdge = new LayoutEdge(bottomCutNode, toNode, bottomCutNode.getWidth() / 2, succEdge.getRelativeToX(), succEdge.getLink());
                        if (succEdge.isReversed()) bottomEdge.reverse();
                        toNode.getPreds().add(bottomEdge);
                        bottomCutNode.getSuccs().add(bottomEdge);
                        assert bottomCutNode.isDummy();

                    } else { // the edge is not cut, but needs dummy nodes
                        portsToUnprocessedEdges.putIfAbsent(startPort, new ArrayList<>());
                        portsToUnprocessedEdges.get(startPort).add(succEdge);
                    }
                }
            }

            for (Map.Entry<Integer, List<LayoutEdge>> portToUnprocessedEdges : portsToUnprocessedEdges.entrySet()) {
                Integer startPort = portToUnprocessedEdges.getKey();
                List<LayoutEdge> unprocessedEdges = portToUnprocessedEdges.getValue();
                unprocessedEdges.sort(LAYOUT_EDGE_LAYER_COMPARATOR);

                if (unprocessedEdges.size() == 1) {
                    // process a single edge
                    LayoutEdge singleEdge = unprocessedEdges.get(0);
                    assert singleEdge.getLink() != null;
                    LayoutNode fromNode = singleEdge.getFrom();
                    if (singleEdge.getTo().getLayer() > fromNode.getLayer() + 1) {
                        LayoutEdge previousEdge = singleEdge;
                        for (int i = fromNode.getLayer() + 1; i < previousEdge.getTo().getLayer(); i++) {
                            LayoutNode dummyNode = new LayoutNode();
                            dummyNode.setLayer(i);
                            dummyNode.getPreds().add(previousEdge);
                            if (optimalPos) {
                                dummyNode.setPos(optimalPosition(dummyNode, dummyNode.getLayer()));
                                dummyNode.setX(0);
                                insertNodeAndAdjustLayer(dummyNode);
                            } else {
                                assert dummyNode.isDummy();
                                dummyNodes.add(dummyNode);
                                layers.get(dummyNode.getLayer()).add(dummyNode);
                            }
                            LayoutEdge dummyEdge = new LayoutEdge(dummyNode, previousEdge.getTo(), dummyNode.getWidth() / 2, previousEdge.getRelativeToX(), null);
                            if (previousEdge.isReversed()) dummyEdge.reverse();
                            dummyNode.getSuccs().add(dummyEdge);
                            previousEdge.setRelativeToX(dummyNode.getWidth() / 2);
                            previousEdge.getTo().getPreds().remove(previousEdge);
                            previousEdge.getTo().getPreds().add(dummyEdge);
                            previousEdge.setTo(dummyNode);
                            previousEdge = dummyEdge;
                        }
                        previousEdge.setLink(singleEdge.getLink());
                    }
                } else {
                    int lastLayer = unprocessedEdges.get(unprocessedEdges.size() - 1).getTo().getLayer();
                    int dummyCnt = lastLayer - layoutNode.getLayer() - 1;
                    LayoutEdge[] newDummyEdges = new LayoutEdge[dummyCnt];
                    LayoutNode[] newDummyNodes = new LayoutNode[dummyCnt];

                    newDummyNodes[0] = new LayoutNode();
                    newDummyNodes[0].setLayer(layoutNode.getLayer() + 1);
                    newDummyEdges[0] = new LayoutEdge(layoutNode, newDummyNodes[0], startPort, newDummyNodes[0].getWidth() / 2, null);
                    newDummyNodes[0].getPreds().add(newDummyEdges[0]);
                    layoutNode.getSuccs().add(newDummyEdges[0]);
                    for (int j = 1; j < dummyCnt; j++) {
                        newDummyNodes[j] = new LayoutNode();
                        newDummyNodes[j].setLayer(layoutNode.getLayer() + j + 1);
                        newDummyEdges[j] = new LayoutEdge(newDummyNodes[j - 1], newDummyNodes[j]);
                        newDummyNodes[j].getPreds().add(newDummyEdges[j]);
                        newDummyNodes[j - 1].getSuccs().add(newDummyEdges[j]);
                    }
                    for (LayoutEdge unprocessedEdge : unprocessedEdges) {
                        assert unprocessedEdge.getLink() != null;
                        assert unprocessedEdge.getTo().getLayer() - layoutNode.getLayer() - 2 >= 0;
                        assert unprocessedEdge.getTo().getLayer() - layoutNode.getLayer() - 2 < dummyCnt;
                        LayoutNode anchorNode = newDummyNodes[unprocessedEdge.getTo().getLayer() - layoutNode.getLayer() - 2];
                        anchorNode.getSuccs().add(unprocessedEdge);
                        unprocessedEdge.setFrom(anchorNode);
                        unprocessedEdge.setRelativeFromX(anchorNode.getWidth() / 2);
                        layoutNode.getSuccs().remove(unprocessedEdge);
                    }
                    for (LayoutNode dummyNode : newDummyNodes) {
                        if (optimalPos) {
                            dummyNode.setPos(optimalPosition(dummyNode, dummyNode.getLayer()));
                            dummyNode.setX(0);
                            insertNodeAndAdjustLayer(dummyNode);
                        } else {
                            assert dummyNode.isDummy();
                            dummyNodes.add(dummyNode);
                            layers.get(dummyNode.getLayer()).add(dummyNode);
                        }
                    }
                }
            }
        } else {
            for (LayoutEdge e : List.copyOf(layoutNode.getSuccs())) {
                processSingleEdge(e);
            }
        }
    }

    private void updateLayerPositions(LayoutLayer layer) {
        int pos = 0;
        for (LayoutNode layoutNode : layer) {
            layoutNode.setPos(pos);
            pos++;
        }
    }

    private void updatePositions() {
        for (LayoutLayer layer : layers) {
            updateLayerPositions(layer);
        }
    }

    public int getBackedgeCrossingScore(LayoutNode node) {
        int score = 0;
        for (LayoutEdge predEdge : node.getPreds()) {
            if (predEdge.isReversed()) {
                List<Point> points = node.getReversedLinkEndPoints().get(predEdge.getLink());
                if (points != null) {
                    int x0 = points.get(points.size() - 1).x;
                    int xn = points.get(0).x;
                    int startPoint = predEdge.getStartX();
                    int endPoint = predEdge.getEndX();
                    int win = (x0 < xn) ? (startPoint - endPoint) : (endPoint - startPoint);
                    score += win;
                }
            }
        }
        for (LayoutEdge succEdge : node.getSuccs()) {
            if (succEdge.isReversed()) {
                List<Point> points = node.getReversedLinkStartPoints().get(succEdge.getLink());
                if (points != null) {
                    int x0 = points.get(points.size() - 1).x;
                    int xn = points.get(0).x;
                    int startPoint = succEdge.getStartX();
                    int endPoint = succEdge.getEndX();
                    int win = (x0 > xn) ? (startPoint - endPoint) : (endPoint - startPoint);
                    score += win;
                }
            }
        }
        return score;
    }

    public void optimizeBackedgeCrossing() {
        if (!combine) {
            return;
        }
        for (LayoutNode node : getLayoutNodes()) {
            if (node.getReversedLinkStartPoints().isEmpty() && node.getReversedLinkEndPoints().isEmpty()) continue;
            int orig_score = getBackedgeCrossingScore(node);
            computeReversedLinkPoints(node, !node.isReverseLeft());
            int reverse_score = getBackedgeCrossingScore(node);
            if (orig_score > reverse_score) {
                computeReversedLinkPoints(node, !node.isReverseLeft());
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
                if (dummy.getRight() + rightShift <= rightNode.getOuterLeft() - NODE_OFFSET) {
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
                if (leftNode.getOuterRight() + NODE_OFFSET <= dummy.getLeft() - leftShift) {
                    // it is possible to shift nextDummyNode left
                    dummy.setX(x);
                }
            } else {
                // nextDummyNode is the left-most node, so we can always move nextDummyNode to the left
                dummy.setX(x);
            }
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

    private void straightenUp(LayoutNode node) {
        if (node.getPreds().size() == 1) {
            LayoutEdge predEdge = node.getPreds().get(0);
            if (predEdge.getTo().getSuccs().size() != 1) return;
            LayoutNode predDummy = predEdge.getFrom();
            if (!predDummy.isDummy()) return;
            if (node.isDummy()) {
                tryAlignDummy(node.getX(), predDummy);
            } else {
                tryAlignDummy(predEdge.getEndX(), predDummy);
            }
        }
    }

    private void straightenLayer(LayoutLayer layer) {
        for (LayoutNode node : layer) {
            //if (!dummy.isDummy()) continue;
            straightenDown(node);
            //straightenUp(node);
        }
        for (int i = layer.size() - 1; i >= 0; i--) {
            LayoutNode node = layer.get(i);
            //if (!dummy.isDummy()) continue;
            straightenDown(node);
            //straightenUp(node);
        }
    }

    private void straightenEdges() {
        for (LayoutLayer layer : layers) {
            for (int pos = 1; pos < layer.size(); ++pos) {
                LayoutNode leftNode = layer.get(pos - 1);
                LayoutNode rightNode = layer.get(pos);
                assert leftNode.getPos() + 1 == rightNode.getPos();
                assert leftNode.getX() <= rightNode.getX();
                assert leftNode.getRight() <= rightNode.getLeft();
            }
        }
        for (LayoutLayer layer : layers) {
            straightenLayer(layer);
        }
        for (int i = layers.size() - 1; i >= 0; i--) {
            straightenLayer(layers.get(i));
        }
    }

    private void assertYLayer() {
        for (int i = 1; i < layers.size(); i++) {
            LayoutLayer topLayer = layers.get(i - 1);
            LayoutLayer bottomLayer = layers.get(i);
            assert topLayer.getBottom() + LAYER_OFFSET <= bottomLayer.getTop();
        }
    }

    private void assertNodePos() {
        for (LayoutNode n : getLayoutNodes()) {
            assert n.getX() >= 0;
            assert n.getY() >= 0;
            assert n.getTopMargin() >= 0;
            assert n.getBottomMargin() >= 0;
            assert n.getHeight() >= 0;
            assert n.getWidth() >= 0;

            for (LayoutEdge e : n.getPreds()) {
                assert e.getFrom().getX() >= 0;
                assert e.getFrom().getY() >= 0;
                assert e.getTo().getX() >= 0;
                assert e.getTo().getY() >= 0;
            }

            for (LayoutEdge e : n.getSuccs()) {
                assert e.getFrom().getX() >= 0;
                assert e.getFrom().getY() >= 0;
                assert e.getTo().getX() >= 0;
                assert e.getTo().getY() >= 0;
            }
        }
        for (LayoutNode n : dummyNodes) {
            assert n.getX() >= 0;
            assert n.getY() >= 0;
            assert n.getHeight() >= 0;
            assert n.getWidth() >= 0;

            for (LayoutEdge e : n.getPreds()) {
                assert e.getFrom().getX() >= 0;
                assert e.getFrom().getY() >= 0;
                assert e.getTo().getX() >= 0;
                assert e.getTo().getY() >= 0;
            }

            for (LayoutEdge e : n.getSuccs()) {
                assert e.getFrom().getX() >= 0;
                assert e.getFrom().getY() >= 0;
                assert e.getTo().getX() >= 0;
                assert e.getTo().getY() >= 0;
            }
        }

        for (Vertex v : graph.getVertices()) {
            LayoutNode n = vertexToLayoutNode.get(v);
            assert n.getX() >= 0;
            assert n.getY() >= 0;
            assert n.getHeight() >= 0;
            assert n.getWidth() >= 0;
        }
    }

    private class BuildDatastructure {

        private void run() {
            // cleanup
            vertexToLayoutNode.clear();
            dummyNodes.clear();

            // Set up nodes
            for (Vertex v : graph.getVertices()) {
                LayoutNode node = new LayoutNode(v);
                vertexToLayoutNode.put(v, node);
            }

            // Set up edges
            List<Link> links = new ArrayList<>(graph.getLinks());
            links.sort(LINK_COMPARATOR);
            for (Link link : links) {
                createLayoutEdge(link);
            }
        }
    }

    private class ReverseEdges {

        private HashSet<LayoutNode> visited;
        private HashSet<LayoutNode> active;

        private void run(boolean prioritizeControl) {
            // Remove self-edges
            for (LayoutNode node : getLayoutNodes()) {
                ArrayList<LayoutEdge> succs = new ArrayList<>(node.getSuccs());
                for (LayoutEdge e : succs) {
                    assert e.getFrom() == node;
                    if (e.getTo() == node) {
                        node.getSuccs().remove(e);
                        node.getPreds().remove(e);
                    }
                }
            }

            // Reverse inputs of roots
            for (LayoutNode node : getLayoutNodes()) {
                if (node.getVertex().isRoot()) {
                    for (LayoutEdge predEdge : new ArrayList<>(node.getPreds())) {
                        reverseEdge(predEdge);
                    }
                }
            }

            visited = new HashSet<>();
            active = new HashSet<>();
            List<LayoutNode> layoutNodes = new ArrayList<>(getLayoutNodes());

            if (prioritizeControl) {
                // detect back-edges in control-flow
                controlFlowBackEdges();

                visited.clear();
                active.clear();

                // Start DFS and reverse back edges
                layoutNodes.sort(ROOTS_FIRST_VERTEX_COMPARATOR);
            }

            for (LayoutNode node : layoutNodes) {
                DFS(node);
            }
            for (LayoutNode node : getLayoutNodes()) {
                computeReversedLinkPoints(node, false);
            }
        }

        private void controlFlowBackEdges() {
            ArrayList<LayoutNode> workingList = new ArrayList<>();
            for (LayoutNode node : getLayoutNodes()) {
                if (!node.hasPreds()) {
                    workingList.add(node);
                }
            }
            // detect back-edges in control-flow
            while (!workingList.isEmpty()) {
                ArrayList<LayoutNode> newWorkingList = new ArrayList<>();
                for (LayoutNode node : workingList) {
                    if (node.getVertex().getPriority() < 4 && !node.getVertex().isRoot()) {
                        continue;
                    }
                    visited.add(node);
                    ArrayList<LayoutEdge> succs = new ArrayList<>(node.getSuccs());
                    for (LayoutEdge edge : succs) {
                        if (edge.isReversed()) {
                            continue;
                        }

                        LayoutNode succNode = edge.getTo();
                        if (visited.contains(succNode)) {
                            // we found a back edge, reverse it
                            reverseEdge(edge);

                        } else {
                            newWorkingList.add(succNode);
                        }
                    }
                }
                workingList = newWorkingList;
            }

        }

        private void DFS(LayoutNode startNode) {
            if (visited.contains(startNode)) {
                return;
            }

            Stack<LayoutNode> workingList = new Stack<>();
            workingList.push(startNode);

            while (!workingList.empty()) {
                LayoutNode node = workingList.pop();

                if (visited.contains(node)) {
                    // Node no longer active
                    active.remove(node);
                    continue;
                }

                // Repush immediately to know when no longer active
                workingList.push(node);
                visited.add(node);
                active.add(node);

                ArrayList<LayoutEdge> succs = new ArrayList<>(node.getSuccs());
                for (LayoutEdge succEdge : succs) {
                    if (active.contains(succEdge.getTo())) {
                        assert visited.contains(succEdge.getTo());
                        // Encountered back edge
                        reverseEdge(succEdge);
                    } else if (!visited.contains(succEdge.getTo())) {
                        workingList.push(succEdge.getTo());
                    }
                }
            }
        }

    }

    private class AssignLayers {

        private void run(boolean prioritizeControl) {
            ArrayList<LayoutNode> workingList = new ArrayList<>();

            // add all root nodes to layer 0
            for (LayoutNode node : getLayoutNodes()) {
                if (!node.hasPreds()) {
                    workingList.add(node);
                    node.setLayer(0);
                }
            }

            // assign layers downwards starting from roots
            int layer = 1;
            while (!workingList.isEmpty()) {
                ArrayList<LayoutNode> newWorkingList = new ArrayList<>();
                if (prioritizeControl) {
                    workingList.sort(LAYOUT_NODE_PRIORITY_COMPARATOR);
                }
                for (LayoutNode node : workingList) {
                    for (LayoutEdge succEdge : node.getSuccs()) {
                        LayoutNode succNode = succEdge.getTo();
                        if (succNode.getLayer() == -1) {
                            // This node was not assigned before.
                            boolean assignedPred = true;
                            for (LayoutEdge predEdge : succNode.getPreds()) {
                                LayoutNode predNode = predEdge.getFrom();
                                if (predNode.getLayer() == -1 || predNode.getLayer() >= layer) {
                                    // This now has an unscheduled successor or a successor that was scheduled only in this round.
                                    assignedPred = false;
                                    break;
                                }
                            }
                            if (assignedPred) {
                                // This successor node can be assigned.
                                succNode.setLayer(layer);
                                newWorkingList.add(succNode);
                            }
                        }
                    }
                }
                workingList = newWorkingList;
                layer++;
            }

            // add all leaves to working list, reset layer of non-leave nodes
            for (LayoutNode node : getLayoutNodes()) {
                if (!node.hasSuccs()) {
                    node.setLayer((layer - 2 - node.getLayer()));
                    workingList.add(node);
                } else {
                    node.setLayer(-1);
                }
            }

            // assign layer upwards starting from leaves
            // sinks non-leave nodes down as much as possible
            layer = 1;
            while (!workingList.isEmpty()) {
                ArrayList<LayoutNode> newWorkingList = new ArrayList<>();
                if (prioritizeControl) {
                    workingList.sort(LAYOUT_NODE_PRIORITY_COMPARATOR);
                }
                for (LayoutNode node : workingList) {
                    if (node.getLayer() < layer) {
                        for (LayoutEdge predEdge : node.getPreds()) {
                            LayoutNode predNode = predEdge.getFrom();
                            if (predNode.getLayer() == -1) {
                                // This node was not assigned before.
                                boolean assignedSucc = true;
                                for (LayoutEdge succEdge : predNode.getSuccs()) {
                                    LayoutNode succNode = succEdge.getTo();
                                    if (succNode.getLayer() == -1 || succNode.getLayer() >= layer) {
                                        // This now has an unscheduled successor or a successor that was scheduled only in this round.
                                        assignedSucc = false;
                                        break;
                                    }
                                }

                                if (assignedSucc) {
                                    // This predecessor node can be assigned.
                                    predNode.setLayer(layer);
                                    newWorkingList.add(predNode);
                                }
                            }
                        }
                    } else {
                        newWorkingList.add(node);
                    }
                }

                workingList = newWorkingList;
                layer++;
            }

            layerCount = layer - 1;
            for (LayoutNode n : getLayoutNodes()) {
                n.setLayer((layerCount - 1 - n.getLayer()));
            }
        }
    }

    private class CreateDummyNodes {

        private void run() {
            layers = new ArrayList<>(layerCount);
            for (int i = 0; i < layerCount; i++) {
                layers.add(new LayoutLayer());
            }

            List<LayoutNode> layoutNodes = getNodes();
            layoutNodes.sort(LAYOUT_NODE_DEGREE_COMPARATOR);

            // Generate initial ordering
            HashSet<LayoutNode> visited = new HashSet<>();
            for (LayoutNode layoutNode : layoutNodes) {
                if (layoutNode.getLayer() == 0) {
                    layers.get(0).add(layoutNode);
                    visited.add(layoutNode);
                } else if (!layoutNode.hasPreds()) {
                    layers.get(layoutNode.getLayer()).add(layoutNode);
                    visited.add(layoutNode);
                }
            }

            for (LayoutNode layoutNode : layoutNodes) {
                assert !layoutNode.isDummy();
                createDummiesForNodeSuccessor(layoutNode, false);
            }

            for (int i = 0; i < layers.size() - 1; i++) {
                for (LayoutNode n : layers.get(i)) {
                    for (LayoutEdge e : n.getSuccs()) {
                        if (e.getTo().isDummy()) continue;
                        if (!visited.contains(e.getTo())) {
                            visited.add(e.getTo());
                            layers.get(i + 1).add(e.getTo());
                            e.getTo().setLayer(i + 1);
                        }
                    }
                }
            }

            updatePositions();

            assertLayerNr();
            assertNodeSynced();
            assertLinks();
        }
    }

    private class CrossingReduction {

        public CrossingReduction() {
        }

        private void run() {
            // The nodes order within layers determines the edge crossings in the layout,
            // thus a good ordering is one with few edge crossings

            // Done in previous pass:  compute an initial ordering (x needs to be set for sweep to work)
            // 1) An initial ordering within each rank is computed
            // DFS or BFS starting with nodes of minimum rank. Nodes are assigned positions in their layers in
            // left-to-right order as the search progresses. This strategy ensures that the initial ordering of a
            // tree has no crossing.

            // 2) Then a sequence of iterations is performed to try to improve the orderings.
            // Each iteration traverses from the first rank to the last one (down), or vice versa (up).
            for (int i = 0; i < CROSSING_ITERATIONS; i++) { // CROSSING_ITERATIONS = 12 (resulting in 24 sweeps)
                // At each iteration, if number of crossings improves (at least a few percent), new ordering is saved

                // When equality occurs when comparing median values or number of edge crossings, flip every other pass

                downSweep();
                upSweep();
            }
            downSweep();
            updatePositions();
            assertOrder();

            // TODO
            // Note: run() twice and pick the better solution of initial order determined
            //  a) by starting with nodes of minimal rank and searching out-edges
            //  b) by starting with nodes of maximal rank and searching in-edges,
        }

        private void doAveragePositions(LayoutLayer layer) {
            for (LayoutNode node : layer) {
                node.setWeightedPosition(node.averagePosition(true));
            }
            // TODO: resolve no adjacent nodes on the neighboring layer?
            layer.sort(CROSSING_NODE_COMPARATOR);
            // TODO: a) recalculate x OR b) shift all overlapping to the right c) skip, since CrossingReduction has no x
            int x = 0;
            for (LayoutNode n : layer) {
                n.setWeightedPosition(x);
                x += n.getOuterWidth() + NODE_OFFSET;
            }
        }

        private void doMedianPositions(LayoutLayer layer, boolean usePred) {
            for (LayoutNode node : layer) {
                int size = usePred ? node.getPreds().size() : node.getSuccs().size();
                if (size == 0) continue; // TODO: fill in empty positions or use succs nodes for weightedPosition
                float[] values = new float[size];
                for (int j = 0; j < size; j++) {
                    LayoutNode predNode = usePred ? node.getPreds().get(j).getFrom() : node.getSuccs().get(j).getTo();
                    values[j] = predNode.getWeightedPosition();
                }
                Arrays.sort(values);
                if (values.length % 2 == 0) {
                    // TODO: interpolated value biased toward the side where nodes are more closely packed
                    node.setWeightedPosition((values[size / 2 - 1] + values[size / 2]) / 2);
                } else {
                    node.setWeightedPosition(values[size / 2]);
                }
            }
            // TODO: resolve no adjacent nodes on the neighboring layer?
            layer.sort(CROSSING_NODE_COMPARATOR);
            // TODO: a) recalculate x OR b) shift all overlapping to the right c) skip, since CrossingReduction has no x
            int x = 0;
            for (LayoutNode n : layer) {
                n.setWeightedPosition(x);
                x += n.getOuterWidth() + NODE_OFFSET;
            }
        }

        private void placeLeavesAndRoots(LayoutLayer layer, boolean usePred) {
            // TODO: sort into remaining positions
            // Nodes that have no adjacent nodes on the neighboring layer:
            // leave fixed in their current positions with non-fixed nodes sorted into the remaining positions
            for (int j = 0; j < layer.size(); j++) {
                LayoutNode node = layer.get(j);
                if (usePred ? !node.hasPreds() : !node.hasSuccs()) {
                    float prevWeight = (j > 0) ? layer.get(j - 1).getWeightedPosition() : 0;
                    float nextWeight = (j < layer.size() - 1) ? layer.get(j + 1).getWeightedPosition() : 0;
                    node.setWeightedPosition((prevWeight + nextWeight) / 2);
                }
            }
            // TODO: resolve no adjacent nodes on the neighboring layer?
            layer.sort(CROSSING_NODE_COMPARATOR);
            // TODO: a) recalculate x OR b) shift all overlapping to the right c) skip, since CrossingReduction has no x
            int x = 0;
            for (LayoutNode n : layer) {
                n.setWeightedPosition(x);
                x += n.getOuterWidth() + NODE_OFFSET;
            }
        }

        private void neighborSwapping() {
            boolean improved = true;
            while (improved) {
                improved = false;
                for (int i = 1; i < layers.size(); i++) {
                    LayoutLayer prevLayer = layers.get(i - 1);
                    LayoutLayer layer = layers.get(i);
                    for (int j = 0; j < layer.size() - 1; j++) {
                        int origCrossings = crossings(prevLayer, layer);
                        layer.swap(j, j + 1);
                        if (crossings(prevLayer, layer) < origCrossings) {
                            improved = true;
                        } else { // swap back
                            layer.swap(j, j + 1);
                        }
                    }

                    // TODO:  swapping of neighboring nodes.:
                    // repeatedly exchanges adjacent nodes on the same layer if this decreases the number of crossings.
                    // Iterates as long as the number of edge crossings can be reduced (or sufficient small) by swapping.
                    // crossing(v,w)> crossing(w,v): count the number of edge crossings where b appears to the left of w in the layer
                    // if improved -> exchange v and w
                }
            }
        }

        // counts the number of edge crossings between two layers
        private int crossings(LayoutLayer topLayer, LayoutLayer bottomLayer) {
            int crossings = 0;
            for (LayoutNode topNode : topLayer) {
                for (LayoutEdge topEdge : topNode.getSuccs()) {
                    for (LayoutNode bottomNode : bottomLayer) {
                        for (LayoutEdge bottomEdge : bottomNode.getPreds()) {
                            if (topEdge == bottomEdge) continue;
                            if (edgesIntersect(topEdge, bottomEdge)) {
                                crossings += 1;
                            }
                        }
                    }
                }
            }
            return crossings;

        }

        public boolean edgesIntersect(LayoutEdge e1, LayoutEdge e2) {
            int s1 = e1.getStartX();
            int s2 = e2.getStartX();
            int d1 = e1.getEndX();
            int d2 = e2.getEndX();
            return (s1 >= s2 || d1 >= d2) && (s2 >= s1 || d2 >= d1);
        }

        private void downSweep() {
            for (LayoutLayer layer : layers) {
                doAveragePositions(layer);
            }
            for (int i = 1; i < layers.size(); i++) {
                doMedianPositions(layers.get(i), true);
                placeLeavesAndRoots(layers.get(i), true);
            }
            neighborSwapping();
        }

        private void upSweep() {
            for (int i = layers.size() - 1; i >= 0; i--) {
                doAveragePositions(layers.get(i));
            }
            for (int i = layers.size() - 2; i >= 0; i--) {
                doMedianPositions(layers.get(i), false);
                placeLeavesAndRoots(layers.get(i), false);
            }
            neighborSwapping();
        }
    }

    private class AssignXCoordinates {

        int[][] space;
        LayoutNode[][] downProcessingOrder;
        LayoutNode[][] upProcessingOrder;

        private void createArrays() {
            space = new int[layers.size()][];
            downProcessingOrder = new LayoutNode[layers.size()][];
            upProcessingOrder = new LayoutNode[layers.size()][];
            for (int i = 0; i < layers.size(); i++) {
                LayoutLayer layer = layers.get(i);
                space[i] = new int[layer.size()];
                downProcessingOrder[i] = new LayoutNode[layer.size()];
                upProcessingOrder[i] = new LayoutNode[layer.size()];
                int curX = 0;
                for (int j = 0; j < layer.size(); j++) {
                    space[i][j] = curX;
                    LayoutNode node = layer.get(j);
                    curX += node.getOuterWidth() + NODE_OFFSET;
                    downProcessingOrder[i][j] = node;
                    upProcessingOrder[i][j] = node;
                }
                Arrays.sort(downProcessingOrder[i], NODE_PROCESSING_DOWN_COMPARATOR);
                Arrays.sort(upProcessingOrder[i], NODE_PROCESSING_UP_COMPARATOR);
            }
        }

        private void initialPositions() {
            for (LayoutNode layoutNode : getLayoutNodes()) {
                assert layoutNode.getLayer() >= 0;
                assert layoutNode.getLayer() < layers.size();
                assert layoutNode.getPos() >= 0;
                assert layoutNode.getPos() < layers.get(layoutNode.getLayer()).size();
                layoutNode.setX(space[layoutNode.getLayer()][layoutNode.getPos()]);
            }
            for (LayoutNode dummyNode : dummyNodes) {
                assert dummyNode.getLayer() >= 0;
                assert dummyNode.getLayer() < layers.size();
                assert dummyNode.getPos() >= 0;
                assert dummyNode.getPos() < layers.get(dummyNode.getLayer()).size();
                dummyNode.setX(space[dummyNode.getLayer()][dummyNode.getPos()]);
            }
        }

        private void run() {
            createArrays();
            initialPositions();
            assertOrder();
            for (int i = 0; i < SWEEP_ITERATIONS; i++) {
                sweepDown();
                sweepUp();
            }
            optimizeBackedgeCrossing();
            straightenEdges();

            assertOrder();
        }

        private int calculateOptimalDown(LayoutNode node) {
            int size = node.getPreds().size();
            if (size == 0) {
                return node.getX();
            }
            int[] values = new int[size];
            for (int i = 0; i < size; i++) {
                LayoutEdge edge = node.getPreds().get(i);
                values[i] = edge.getStartX() - edge.getRelativeToX();
            }
            return Statistics.median(values);
        }

        private int calculateOptimalUp(LayoutNode node) {
            int size = node.getSuccs().size();
            if (size == 0) {
                return node.getX();
            }
            int[] values = new int[size];
            for (int i = 0; i < size; i++) {
                LayoutEdge edge = node.getSuccs().get(i);
                values[i] = edge.getEndX() - edge.getRelativeFromX();
            }
            return Statistics.median(values);
        }

        private void processRow(int[] space, LayoutNode[] processingOrder) {
            Arrays.sort(processingOrder, DUMMY_NODES_THEN_OPTIMAL_X);
            TreeSet<LayoutNode> treeSet = new TreeSet<>(NODE_POS_COMPARATOR);
            for (LayoutNode node : processingOrder) {
                int minX = Integer.MIN_VALUE;
                SortedSet<LayoutNode> headSet = treeSet.headSet(node, false);
                if (!headSet.isEmpty()) {
                    LayoutNode leftNeighbor = headSet.last();
                    minX = leftNeighbor.getOuterLeft() + space[node.getPos()] - space[leftNeighbor.getPos()];
                }

                int maxX = Integer.MAX_VALUE;
                SortedSet<LayoutNode> tailSet = treeSet.tailSet(node, false);
                if (!tailSet.isEmpty()) {
                    LayoutNode rightNeighbor = tailSet.first();
                    maxX = rightNeighbor.getOuterLeft() + space[node.getPos()] - space[rightNeighbor.getPos()];
                }

                assert minX <= maxX : minX + " vs " + maxX;
                node.setX(Math.min(Math.max(node.getOptimalX(), minX), maxX));
                treeSet.add(node);
            }
        }

        private void sweepUp() {
            for (int i = layers.size() - 2; i >= 0; i--) {
                for (LayoutNode node : upProcessingOrder[i]) {
                    node.setOptimalX(calculateOptimalUp(node));
                }
                processRow(space[i], upProcessingOrder[i]);
            }
        }

        private void sweepDown() {
            for (int i = 1; i < layers.size(); i++) {
                for (LayoutNode node : downProcessingOrder[i]) {
                    node.setOptimalX(calculateOptimalDown(node));
                }
                processRow(space[i], downProcessingOrder[i]);
            }
        }
    }

    private class AssignYCoordinates {

        private void updateLayerHeight(LayoutLayer layer, int y) {
            layer.setTop(y);
            int maxLayerHeight = 0;
            for (LayoutNode layoutNode : layer) {
                if (!layoutNode.isDummy()) {
                    // center the node
                    int offset = Math.max(layoutNode.getTopMargin(), layoutNode.getBottomMargin());
                    layoutNode.setTopMargin(offset);
                    layoutNode.setBottomMargin(offset);
                }
                maxLayerHeight = Math.max(maxLayerHeight, layoutNode.getOuterHeight());
            }
            layer.setHeight(maxLayerHeight);

            for (LayoutNode layoutNode : layer) {
                layoutNode.setY(y + (layer.getHeight() - layoutNode.getOuterHeight()) / 2);
            }
        }

        private int getScaledLayerPadding(LayoutLayer layer) {
            int maxXOffset = 0;

            for (LayoutNode layoutNode : layer) {
                for (LayoutEdge succEdge : layoutNode.getSuccs()) {
                    maxXOffset = Math.max(Math.abs(succEdge.getStartX() - succEdge.getEndX()), maxXOffset);
                }
            }

            return (int) (SCALE_LAYER_PADDING * Math.max((int) (Math.sqrt(maxXOffset) * 2), LAYER_OFFSET * 3));
        }

        private void run() {
            int currentY = 0;
            for (LayoutLayer layer : layers) {
                updateLayerHeight(layer, currentY);
                currentY += layer.getHeight() + getScaledLayerPadding(layer);
            }

            assertYLayer();
        }
    }

    private class WriteResult {

        private HashMap<Vertex, Point> computeVertexPositions() {
            HashMap<Vertex, Point> vertexPositions = new HashMap<>();
            for (Map.Entry<Vertex, LayoutNode> entry : vertexToLayoutNode.entrySet()) {
                Vertex v = entry.getKey();
                LayoutNode n = entry.getValue();
                vertexPositions.put(v, new Point(n.getLeft(), n.getTop()));
            }
            return vertexPositions;
        }

        private HashMap<Link, List<Point>> computeLinkPositions() {
            HashMap<Link, List<Point>> linkToSplitEndPoints = new HashMap<>();
            HashMap<Link, List<Point>> linkPositions = new HashMap<>();

            for (LayoutNode layoutNode : getLayoutNodes()) {
                for (LayoutEdge predEdge : layoutNode.getPreds()) {
                    assert predEdge.getLink() != null;

                    LayoutNode fromNode = predEdge.getFrom();
                    LayoutNode toNode = predEdge.getTo();

                    ArrayList<Point> linkPoints = new ArrayList<>();
                    // input edge stub
                    linkPoints.add(new Point(predEdge.getEndX(), toNode.getTop()));
                    linkPoints.add(new Point(predEdge.getEndX(), layers.get(toNode.getLayer()).getTop() - LAYER_OFFSET));

                    LayoutEdge curEdge = predEdge;
                    while (fromNode.isDummy() && fromNode.hasPreds()) {
                        linkPoints.add(new Point(fromNode.getCenterX(), layers.get(fromNode.getLayer()).getBottom() + LAYER_OFFSET));
                        linkPoints.add(new Point(fromNode.getCenterX(), layers.get(fromNode.getLayer()).getTop() - LAYER_OFFSET));
                        curEdge = fromNode.getPreds().get(0);
                        fromNode = curEdge.getFrom();
                    }
                    linkPoints.add(new Point(curEdge.getStartX(), layers.get(fromNode.getLayer()).getBottom() + LAYER_OFFSET));
                    // output edge stub
                    linkPoints.add(new Point(curEdge.getStartX(), fromNode.getBottom()));

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


            for (LayoutNode layoutNode : getLayoutNodes()) {
                for (LayoutEdge succEdge : layoutNode.getSuccs()) {
                    if (succEdge.getLink() == null) continue;

                    LayoutNode fromNode = succEdge.getFrom();
                    LayoutNode toNode = succEdge.getTo();

                    ArrayList<Point> linkPoints = new ArrayList<>();
                    linkPoints.add(new Point(succEdge.getStartX(), fromNode.getBottom()));
                    linkPoints.add(new Point(succEdge.getStartX(), layers.get(fromNode.getLayer()).getBottom() + LAYER_OFFSET));

                    LayoutEdge curEdge = succEdge;
                    while (toNode.isDummy() && toNode.hasSuccs()) {
                        linkPoints.add(new Point(toNode.getCenterX(), layers.get(toNode.getLayer()).getTop() - LAYER_OFFSET));
                        linkPoints.add(new Point(toNode.getCenterX(), layers.get(toNode.getLayer()).getBottom() + LAYER_OFFSET));
                        curEdge = toNode.getSuccs().get(0);
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
                        assert !linkPositions.containsKey(succEdge.getLink());
                    }
                    linkPositions.put(succEdge.getLink(), linkPoints);
                }
            }

            return linkPositions;
        }

        public void run() {
            // takes dummyNodes, layers, vertexToLayoutNode
            assert dummyNodes.size() == (new HashSet<>(dummyNodes)).size();

            assertOrder();

            // takes vertexToLayoutNode
            HashMap<Vertex, Point> vertexPositions = computeVertexPositions();
            assertOrder();

            // takes vertexToLayoutNode
            HashMap<Link, List<Point>> linkPositions = computeLinkPositions();
            assertOrder();

            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            for (Point point : vertexPositions.values()) {
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
            }

            for (List<Point> points : linkPositions.values()) {
                for (Point point : points) {
                    if (point != null) {
                        minX = Math.min(minX, point.x);
                        minY = Math.min(minY, point.y);
                    }
                }
            }

            assertOrder();

            for (LayoutNode layoutNode : getLayoutNodes()) {
                minX = Math.min(minX, layoutNode.getX());
                minY = Math.min(minY, layoutNode.getY());
            }
            for (LayoutNode dummyNode : dummyNodes) {
                minX = Math.min(minX, dummyNode.getX());
                minY = Math.min(minY, dummyNode.getY());
            }

            for (LayoutLayer layer : layers) {
                minY = Math.min(minY, layer.getTop());
            }

            for (LayoutNode layoutNode : getLayoutNodes()) {
                layoutNode.setX(layoutNode.getX() - minX);
                assert layoutNode.getX() >= 0;
                layoutNode.setY(layoutNode.getY() - minY);
                assert layoutNode.getY() >= 0;
            }
            for (LayoutNode dummyNode : dummyNodes) {
                dummyNode.setX(dummyNode.getX() - minX);
                assert dummyNode.getX() >= 0;
                dummyNode.setY(dummyNode.getY() - minY);
                assert dummyNode.getY() >= 0;
            }

            assertOrder();

            for (LayoutLayer layer : layers) {
                layer.shiftTop(-minY);
                assert layer.getTop() >= 0;
            }

            assertOrder();

            // shift vertices by minX/minY
            for (Map.Entry<Vertex, Point> entry : vertexPositions.entrySet()) {
                Point point = entry.getValue();
                point.x -= minX;
                point.y -= minY;
                assert point.x >= 0;
                assert point.y >= 0;
                Vertex vertex = entry.getKey();
                vertex.setPosition(point);
            }

            assertOrder();

            // shift links by minX/minY
            for (Map.Entry<Link, List<Point>> entry : linkPositions.entrySet()) {
                Link link = entry.getKey();
                List<Point> points = entry.getValue();
                for (Point p : points) {
                    if (p != null) {
                        p.x -= minX;
                        p.y -= minY;
                        assert p.x >= 0;
                        assert p.y >= 0;
                    }
                }

                // write points back to links
                link.setControlPoints(points);
            }

            assertOrder();
            assertNodePos();
        }
    }
}
