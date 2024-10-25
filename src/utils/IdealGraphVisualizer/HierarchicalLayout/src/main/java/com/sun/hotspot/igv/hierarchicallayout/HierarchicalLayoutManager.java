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

import static com.sun.hotspot.igv.hierarchicallayout.LayoutEdge.LAYOUT_EDGE_LAYER_COMPARATOR;
import static com.sun.hotspot.igv.hierarchicallayout.LayoutGraph.LINK_COMPARATOR;
import static com.sun.hotspot.igv.hierarchicallayout.LayoutNode.*;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Vertex;
import com.sun.hotspot.igv.util.Statistics;
import java.awt.Point;
import java.util.*;


public class HierarchicalLayoutManager extends LayoutManager {

    private LayoutGraph graph;

    public HierarchicalLayoutManager() {
        maxLayerLength = -1;
    }

    private void insertNodeAndAdjustLayer(LayoutNode node) {
        LayoutLayer layer = graph.getLayer(node.getLayer());
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

        graph.addNode(node);
    }

    private void applyRemoveLinkAction(Link link) {
        Vertex from = link.getFrom().getVertex();
        Vertex to = link.getTo().getVertex();
        LayoutNode toNode = graph.getLayoutNode(to);
        LayoutNode fromNode = graph.getLayoutNode(from);

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
                        if (graph.getDummyNodes().contains(predNode)) {
                            graph.removeDummyNode(predNode);
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

    private void removeEdges(LayoutNode movedNode) {
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
    }

    private void addEdges(LayoutNode movedNode) {
        // BuildDatastructure
        List<Link> nodeLinks = new ArrayList<>(graph.getInputLinks(movedNode.getVertex()));
        nodeLinks.addAll(graph.getOutputLinks(movedNode.getVertex()));
        nodeLinks.sort(LINK_COMPARATOR);

        Set<LayoutNode> reversedLayoutNodes = new HashSet<>();
        for (Link link : nodeLinks) {
            if (link.getFrom().getVertex() == link.getTo().getVertex()) continue;
            LayoutEdge layoutEdge = graph.createLayoutEdge(link);

            LayoutNode fromNode = layoutEdge.getFrom();
            LayoutNode toNode = layoutEdge.getTo();

            if (fromNode.getLayer() > toNode.getLayer()) {
                reverseEdge(layoutEdge);
                reversedLayoutNodes.add(fromNode);
                reversedLayoutNodes.add(toNode);
            }
        }

        // ReverseEdges
        for (LayoutNode layoutNode : reversedLayoutNodes) {
            computeReversedLinkPoints(layoutNode, false);
        }

        // CreateDummyNodes
        createDummiesForNodeSuccessor(movedNode, true);
        for (LayoutEdge predEdge : movedNode.getPreds()) {
            insertDummyNodes(predEdge);
        }

        graph.updatePositions();
    }

    /**
     * Find the optimal position within the given layer to insert the given node.
     * The optimum is given by the least amount of edge crossings.
     */
    private int optimalPosition(LayoutNode node, int layerNr) {
        LayoutLayer layer = graph.getLayer(layerNr);
        layer.sort(NODE_POS_COMPARATOR);
        int edgeCrossings = Integer.MAX_VALUE;
        int optimalPos = -1;

        // Try each possible position in the layer
        for (int i = 0; i < layer.size() + 1; i++) {
            int xCoord;
            if (i == 0) {
                xCoord = layer.get(i).getX() - node.getWidth() - 1;
            } else {
                xCoord = layer.get(i - 1).getX() + layer.get(i - 1).getWidth() + 1;
            }

            int currentCrossings = 0;

            if (0 <= layerNr - 1) {
                // For each link with an end point in vertex, check how many edges cross it
                for (LayoutEdge edge : node.getPreds()) {
                    if (edge.getFrom().getLayer() == layerNr - 1) {
                        int fromNodeXCoord = edge.getFromX();
                        int toNodeXCoord = xCoord;
                        if (!node.isDummy()) {
                            toNodeXCoord += edge.getRelativeToX();
                        }
                        for (LayoutNode n : graph.getLayer(layerNr - 1)) {
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
            if (layerNr + 1 < graph.getLayerCount()) {
                // For each link with an end point in vertex, check how many edges cross it
                for (LayoutEdge edge : node.getSuccs()) {
                    if (edge.getTo().getLayer() == layerNr + 1) {
                        int toNodeXCoord = edge.getToX();
                        int fromNodeXCoord = xCoord;
                        if (!node.isDummy()) {
                            fromNodeXCoord += edge.getRelativeFromX();
                        }
                        for (LayoutNode n : graph.getLayer(layerNr + 1)) {
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
        return optimalPos;
    }

    private void removeDummyLayer(int emptyLayerNr) {
        LayoutLayer layer = graph.getLayer(emptyLayerNr);
        if (!layer.isDummyLayer()) return;

        for (LayoutNode dummyNode : layer) {


            LayoutEdge predEdge = dummyNode.getPreds().get(0);
            LayoutNode fromNode = predEdge.getFrom(); // Root
            // remove the dummy node from Root
            fromNode.getSuccs().remove(predEdge);
            if (predEdge.getLink() != null) {
                LayoutEdge succEdge = dummyNode.getSuccs().get(0);
                succEdge.setLink(predEdge.getLink());
            }

            // modify succEdge to come from fromNode and add to succs
            for (LayoutEdge succEdge : dummyNode.getSuccs()) {
                succEdge.setFrom(fromNode);
                fromNode.getSuccs().add(succEdge);
                succEdge.setRelativeFromX(predEdge.getRelativeFromX());
            }

            graph.removeDummyNode(dummyNode);
        }


        graph.removeLayer(emptyLayerNr);
    }

    // check that NO neighbors of node are in a given layer
    private int insertNewLayerIfNeeded(LayoutNode node, int layerNr) {
        for (Link inputLink : graph.getInputLinks(node.getVertex())) {
            if (inputLink.getFrom().getVertex() == inputLink.getTo().getVertex()) continue;
            LayoutNode fromNode = graph.getLayoutNode(inputLink.getFrom().getVertex());
            if (fromNode.getLayer() == layerNr) {
                moveExpandLayerDown(layerNr + 1);
                return layerNr + 1;
            }
        }
        for (Link outputLink : graph.getOutputLinks(node.getVertex())) {
            if (outputLink.getFrom().getVertex() == outputLink.getTo().getVertex()) continue;
            LayoutNode toNode = graph.getLayoutNode(outputLink.getTo().getVertex());
            if (toNode.getLayer() == layerNr) {
                moveExpandLayerDown(layerNr);
                return layerNr;
            }
        }
        return layerNr;

    }

    private void moveExpandLayerDown(int layerNr) {
        LayoutLayer oldLayer = graph.getLayer(layerNr);
        LayoutLayer newLayerAbove =  graph.createNewLayer(layerNr);

        for (LayoutNode node : oldLayer) {
            for (LayoutEdge predEdge : node.getPreds()) {
                int x = predEdge.getStartX();
                LayoutNode dummyNode = insertDummyBetweenSourceAndEdge(predEdge);
                dummyNode.setX(x);
                dummyNode.setOptimalX(x);
                graph.insertNodeIntoLayer(dummyNode, newLayerAbove);
            }
        }
        newLayerAbove.sortNodesByXAndSetPositions();
    }

    public void moveLink(Point linkPos, int shiftX) {
        int layerNr = graph.findLayer(linkPos.y);
        for (LayoutNode node : graph.getLayer(layerNr)) {
            if (node.isDummy() && linkPos.x == node.getX()) {
                LayoutLayer layer = graph.getLayer(layerNr);
                if (layer.contains(node)) {
                    node.setX(linkPos.x + shiftX);
                    layer.sortNodesByXAndSetPositions();
                    break;
                }
            }
        }
        writeBack();
    }

    public void moveVertices(Set<? extends Vertex> movedVertices) {
        for (Vertex vertex : movedVertices) {
            moveVertex(vertex);
        }
        writeBack();
    }

    private void moveVertex(Vertex movedVertex) {
        Point newLoc = movedVertex.getPosition();
        LayoutNode movedNode = graph.getLayoutNode(movedVertex);

        int layerNr = graph.findLayer(newLoc.y + movedNode.getOuterHeight() / 2);
        if (movedNode.getLayer() == layerNr) { // we move the node in the same layer
            LayoutLayer layer = graph.getLayer(layerNr);
            if (layer.contains(movedNode)) {
                System.out.println(movedNode.getX() + " -> " + newLoc.x);
                movedNode.setX(newLoc.x);
                // TODO: only call once per moveVertices()
                layer.sortNodesByXAndSetPositions();
            }
        } else { // only remove edges if we moved the node to a new layer
            // TODO
            removeEdges(movedNode);
            layerNr = insertNewLayerIfNeeded(movedNode, layerNr);
            // remove from old layer and update positions in old layer
            int oldLayerNr = movedNode.getLayer();
            graph.removeNode(movedNode);
            insertNodeAndAdjustLayer(movedNode);
            removeDummyLayer(oldLayerNr);
            addEdges(movedNode);
        }
    }

    private void writeBack() {
        optimizeBackedgeCrossing();
        straightenEdges();
        new WriteResult().run();
    }


    @Override
    public void setCutEdges(boolean enable) {
        maxLayerLength = enable ? 10 : -1;
    }

    public void doLayout(LayoutGraph graph) {

        this.graph = graph;

        // STEP 2: Reverse edges, handle backedges
        new ReverseEdges().run(true);

        // STEP 3: Assign layers and create dummy nodes
        new LayerManager().run(true);

        // STEP 5: Crossing Reduction
        new CrossingReduction().run();

        // STEP 6: Assign X coordinates
        new AssignXCoordinates().run();

        // STEP 7: Write back to interface
        new WriteResult().run();
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

        boolean hasReversedDown = computeReversedStartPoints(node, reverseLeft);
        boolean hasReversedUP = computeReversedEndPoints(node, hasReversedDown != reverseLeft);
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
                LayoutNode dummyNode = insertDummyBetweenSourceAndEdge(prevEdge);
                if (graph.getLayer(l).isEmpty()) {
                    dummyNode.setPos(0);
                } else {
                    dummyNode.setPos(optimalPosition(dummyNode, l));
                }
                graph.insertNodeIntoLayer(dummyNode, graph.getLayer(l));
                prevEdge = dummyNode.getPreds().get(0);
            }
        }
    }

    private LayoutNode insertDummyBetweenSourceAndEdge(LayoutEdge layoutEdge) {
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

    public void createDummiesForNodeSuccessor(LayoutNode layoutNode, boolean optimalPos) {
        HashMap<Integer, List<LayoutEdge>> portsToUnprocessedEdges = new HashMap<>();
        ArrayList<LayoutEdge> succs = new ArrayList<>(layoutNode.getSuccs());
        HashMap<Integer, LayoutNode> portToTopNode = new HashMap<>();
        HashMap<Integer, HashMap<Integer, LayoutNode>> portToBottomNodeMapping = new HashMap<>();
        for (LayoutEdge succEdge : succs) {
            int startPort = succEdge.getRelativeFromX();
            LayoutNode fromNode = succEdge.getFrom();
            LayoutNode toNode = succEdge.getTo();

            // edge is longer than one layer => needs dummy nodes
            if (fromNode.getLayer() != toNode.getLayer() - 1) {
                // the edge needs to be cut
                if (maxLayerLength != -1 && toNode.getLayer() - fromNode.getLayer() > maxLayerLength) {
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
                            graph.addDummyNode(topCutNode);
                            graph.getLayer(topCutNode.getLayer()).add(topCutNode);
                        }
                        portToTopNode.put(startPort, topCutNode);
                        portToBottomNodeMapping.put(startPort, new HashMap<>());
                    }
                    LayoutEdge edgeToTopCut = new LayoutEdge(fromNode, topCutNode, succEdge.getRelativeFromX(), topCutNode.getWidth() / 2, succEdge.getLink());
                    if (succEdge.isReversed()) edgeToTopCut.reverse();
                    fromNode.getSuccs().add(edgeToTopCut);
                    topCutNode.getPreds().add(edgeToTopCut);

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
                            graph.addDummyNode(bottomCutNode);
                            graph.getLayer(bottomCutNode.getLayer()).add(bottomCutNode);
                        }
                        layerToBottomNode.put(toNode.getLayer(), bottomCutNode);
                    }
                    LayoutEdge bottomEdge = new LayoutEdge(bottomCutNode, toNode, bottomCutNode.getWidth() / 2, succEdge.getRelativeToX(), succEdge.getLink());
                    if (succEdge.isReversed()) bottomEdge.reverse();
                    toNode.getPreds().add(bottomEdge);
                    bottomCutNode.getSuccs().add(bottomEdge);

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
                            graph.addDummyNode(dummyNode);
                            graph.getLayer(dummyNode.getLayer()).add(dummyNode);
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
                        graph.addDummyNode(dummyNode);
                        graph.getLayer(dummyNode.getLayer()).add(dummyNode);
                    }
                }
            }
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
        for (LayoutNode node : graph.getLayoutNodes()) {
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
        LayoutLayer nextLayer = graph.getLayer(dummy.getLayer());
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

    private void straightenLayer(LayoutLayer layer) {
        for (LayoutNode node : layer) {
            straightenDown(node);
        }
        for (int i = layer.size() - 1; i >= 0; i--) {
            straightenDown(layer.get(i));
        }
    }

    private void straightenEdges() {
        for (int i = 0; i < graph.getLayerCount(); i++) {
            straightenLayer(graph.getLayer(i));
        }
        for (int i = graph.getLayerCount() - 1; i >= 0; i--) {
            straightenLayer(graph.getLayer(i));
        }
    }

    private class ReverseEdges {

        private HashSet<LayoutNode> visited;
        private HashSet<LayoutNode> active;

        private void run(boolean prioritizeControl) {
            // Remove self-edges
            for (LayoutNode node : graph.getLayoutNodes()) {
                ArrayList<LayoutEdge> succs = new ArrayList<>(node.getSuccs());
                for (LayoutEdge e : succs) {
                    if (e.getTo() == node) {
                        node.getSuccs().remove(e);
                        node.getPreds().remove(e);
                    }
                }
            }

            // Reverse inputs of roots
            for (LayoutNode node : graph.getLayoutNodes()) {
                if (node.getVertex().isRoot()) {
                    for (LayoutEdge predEdge : new ArrayList<>(node.getPreds())) {
                        reverseEdge(predEdge);
                    }
                }
            }

            visited = new HashSet<>();
            active = new HashSet<>();
            List<LayoutNode> layoutNodes = new ArrayList<>(graph.getLayoutNodes());

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
            for (LayoutNode node : graph.getLayoutNodes()) {
                computeReversedLinkPoints(node, false);
            }
        }

        private void controlFlowBackEdges() {
            ArrayList<LayoutNode> workingList = new ArrayList<>();
            for (LayoutNode node : graph.getLayoutNodes()) {
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
                        // Encountered back edge
                        reverseEdge(succEdge);
                    } else if (!visited.contains(succEdge.getTo())) {
                        workingList.push(succEdge.getTo());
                    }
                }
            }
        }

    }


    private class LayerManager {

        private void assignLayers(boolean prioritizeControl) {
            ArrayList<LayoutNode> workingList = new ArrayList<>();

            // add all root nodes to layer 0
            for (LayoutNode node : graph.getLayoutNodes()) {
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
            for (LayoutNode node : graph.getLayoutNodes()) {
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

            int layerCount = layer - 1;
            for (LayoutNode n : graph.getLayoutNodes()) {
                n.setLayer((layerCount - 1 - n.getLayer()));
            }

            graph.initLayers(layerCount);
        }

        private void createDummyNodes() {
            List<LayoutNode> layoutNodes = new ArrayList<>(graph.getLayoutNodes());
            layoutNodes.sort(LAYOUT_NODE_DEGREE_COMPARATOR);

            // Generate initial ordering
            HashSet<LayoutNode> visited = new HashSet<>();
            for (LayoutNode layoutNode : layoutNodes) {
                if (layoutNode.getLayer() == 0) {
                    graph.getLayer(0).add(layoutNode);
                    visited.add(layoutNode);
                } else if (!layoutNode.hasPreds()) {
                    graph.getLayer(layoutNode.getLayer()).add(layoutNode);
                    visited.add(layoutNode);
                }
            }

            for (LayoutNode layoutNode : layoutNodes) {
                createDummiesForNodeSuccessor(layoutNode, false);
            }

            for (int i = 0; i < graph.getLayerCount() - 1; i++) {
                for (LayoutNode n : graph.getLayer(i)) {
                    for (LayoutEdge e : n.getSuccs()) {
                        if (e.getTo().isDummy()) continue;
                        if (!visited.contains(e.getTo())) {
                            visited.add(e.getTo());
                            graph.getLayer(i + 1).add(e.getTo());
                            e.getTo().setLayer(i + 1);
                        }
                    }
                }
            }
        }


        public void run(boolean prioritizeControl) {
            assignLayers(prioritizeControl);
            createDummyNodes();
            graph.updatePositions();
        }
    }

    private class CrossingReduction {

        public CrossingReduction() {
        }

        private void run() {
            for (int i = 0; i < CROSSING_ITERATIONS; i++) {
                downSweep();
                upSweep();
            }
            downSweep();
            graph.updatePositions();
        }

        private void doAveragePositions(LayoutLayer layer) {
            for (LayoutNode node : layer) {
                node.setWeightedPosition(node.averagePosition(true));
            }
            layer.sort(CROSSING_NODE_COMPARATOR);
            int x = 0;
            for (LayoutNode n : layer) {
                n.setWeightedPosition(x);
                x += n.getOuterWidth() + NODE_OFFSET;
            }
        }

        private void doMedianPositions(LayoutLayer layer, boolean usePred) {
            for (LayoutNode node : layer) {
                int size = usePred ? node.getPreds().size() : node.getSuccs().size();
                if (size == 0) continue;
                float[] values = new float[size];
                for (int j = 0; j < size; j++) {
                    LayoutNode predNode = usePred ? node.getPreds().get(j).getFrom() : node.getSuccs().get(j).getTo();
                    values[j] = predNode.getWeightedPosition();
                }
                Arrays.sort(values);
                if (values.length % 2 == 0) {
                    node.setWeightedPosition((values[size / 2 - 1] + values[size / 2]) / 2);
                } else {
                    node.setWeightedPosition(values[size / 2]);
                }
            }
            layer.sort(CROSSING_NODE_COMPARATOR);
            int x = 0;
            for (LayoutNode n : layer) {
                n.setWeightedPosition(x);
                x += n.getOuterWidth() + NODE_OFFSET;
            }
        }

        private void placeLeavesAndRoots(LayoutLayer layer, boolean usePred) {
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
            layer.sort(CROSSING_NODE_COMPARATOR);
            int x = 0;
            for (LayoutNode n : layer) {
                n.setWeightedPosition(x);
                x += n.getOuterWidth() + NODE_OFFSET;
            }
        }

        private void downSweep() {
            for (int i = 0; i < graph.getLayerCount(); i++) {
                doAveragePositions(graph.getLayer(i));
            }
            for (int i = 1; i < graph.getLayerCount(); i++) {
                doMedianPositions(graph.getLayer(i), true);
                placeLeavesAndRoots(graph.getLayer(i), true);
            }
        }

        private void upSweep() {
            for (int i = graph.getLayerCount() - 1; i >= 0; i--) {
                doAveragePositions(graph.getLayer(i));
            }
            for (int i = graph.getLayerCount() - 2; i >= 0; i--) {
                doMedianPositions(graph.getLayer(i), false);
                placeLeavesAndRoots(graph.getLayer(i), false);
            }
        }
    }

    private class AssignXCoordinates {

        int[][] space;
        LayoutNode[][] downProcessingOrder;
        LayoutNode[][] upProcessingOrder;

        private void createArrays() {
            space = new int[graph.getLayerCount()][];
            downProcessingOrder = new LayoutNode[graph.getLayerCount()][];
            upProcessingOrder = new LayoutNode[graph.getLayerCount()][];
            for (int i = 0; i < graph.getLayerCount(); i++) {
                LayoutLayer layer = graph.getLayer(i);
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
            for (LayoutNode layoutNode : graph.getLayoutNodes()) {
                layoutNode.setX(space[layoutNode.getLayer()][layoutNode.getPos()]);
            }
            for (LayoutNode dummyNode : graph.getDummyNodes()) {
                dummyNode.setX(space[dummyNode.getLayer()][dummyNode.getPos()]);
            }
        }

        private void run() {
            createArrays();
            initialPositions();
            for (int i = 0; i < SWEEP_ITERATIONS; i++) {
                sweepDown();
                sweepUp();
            }
            optimizeBackedgeCrossing();
            straightenEdges();
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

                node.setX(Math.min(Math.max(node.getOptimalX(), minX), maxX));
                treeSet.add(node);
            }
        }

        private void sweepUp() {
            for (int i = graph.getLayerCount() - 2; i >= 0; i--) {
                for (LayoutNode node : upProcessingOrder[i]) {
                    node.setOptimalX(calculateOptimalUp(node));
                }
                processRow(space[i], upProcessingOrder[i]);
            }
        }

        private void sweepDown() {
            for (int i = 1; i < graph.getLayerCount(); i++) {
                for (LayoutNode node : downProcessingOrder[i]) {
                    node.setOptimalX(calculateOptimalDown(node));
                }
                processRow(space[i], downProcessingOrder[i]);
            }
        }
    }

    private class WriteResult {

        private HashMap<Link, List<Point>> computeLinkPositions() {
            HashMap<Link, List<Point>> linkToSplitEndPoints = new HashMap<>();
            HashMap<Link, List<Point>> linkPositions = new HashMap<>();

            for (LayoutNode layoutNode : graph.getLayoutNodes()) {
                for (LayoutEdge predEdge : layoutNode.getPreds()) {
                    LayoutNode fromNode = predEdge.getFrom();
                    LayoutNode toNode = predEdge.getTo();

                    ArrayList<Point> linkPoints = new ArrayList<>();
                    // input edge stub
                    linkPoints.add(new Point(predEdge.getEndX(), toNode.getTop()));
                    linkPoints.add(new Point(predEdge.getEndX(), graph.getLayer(toNode.getLayer()).getTop() - LAYER_OFFSET));

                    LayoutEdge curEdge = predEdge;
                    while (fromNode.isDummy() && fromNode.hasPreds()) {
                        linkPoints.add(new Point(fromNode.getCenterX(), graph.getLayer(fromNode.getLayer()).getBottom() + LAYER_OFFSET));
                        linkPoints.add(new Point(fromNode.getCenterX(), graph.getLayer(fromNode.getLayer()).getTop() - LAYER_OFFSET));
                        curEdge = fromNode.getPreds().get(0);
                        fromNode = curEdge.getFrom();
                    }
                    linkPoints.add(new Point(curEdge.getStartX(), graph.getLayer(fromNode.getLayer()).getBottom() + LAYER_OFFSET));
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

            for (LayoutNode layoutNode : graph.getLayoutNodes()) {
                for (LayoutEdge succEdge : layoutNode.getSuccs()) {
                    if (succEdge.getLink() == null) continue;

                    LayoutNode fromNode = succEdge.getFrom();
                    LayoutNode toNode = succEdge.getTo();

                    ArrayList<Point> linkPoints = new ArrayList<>();
                    linkPoints.add(new Point(succEdge.getStartX(), fromNode.getBottom()));
                    linkPoints.add(new Point(succEdge.getStartX(), graph.getLayer(fromNode.getLayer()).getBottom() + LAYER_OFFSET));

                    LayoutEdge curEdge = succEdge;
                    while (toNode.isDummy() && toNode.hasSuccs()) {
                        linkPoints.add(new Point(toNode.getCenterX(), graph.getLayer(toNode.getLayer()).getTop() - LAYER_OFFSET));
                        linkPoints.add(new Point(toNode.getCenterX(), graph.getLayer(toNode.getLayer()).getBottom() + LAYER_OFFSET));
                        curEdge = toNode.getSuccs().get(0);
                        toNode = curEdge.getTo();
                    }
                    linkPoints.add(new Point(curEdge.getEndX(), graph.getLayer(toNode.getLayer()).getTop() - LAYER_OFFSET));
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

        public void run() {
            // Assign Y coordinates
            graph.positionLayers();

            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;

            HashMap<Link, List<Point>> linkPositions = computeLinkPositions();
            for (List<Point> points : linkPositions.values()) {
                for (Point point : points) {
                    if (point != null) {
                        minX = Math.min(minX, point.x);
                        minY = Math.min(minY, point.y);
                    }
                }
            }

            for (LayoutNode layoutNode : graph.getLayoutNodes()) {
                minX = Math.min(minX, layoutNode.getX());
                minY = Math.min(minY, layoutNode.getY());
            }

            for (LayoutNode dummyNode : graph.getDummyNodes()) {
                minX = Math.min(minX, dummyNode.getX());
                minY = Math.min(minY, dummyNode.getY());
            }

            for (LayoutLayer layer : graph.getLayers()) {
                minY = Math.min(minY, layer.getTop());
            }

            for (LayoutNode layoutNode : graph.getLayoutNodes()) {
                layoutNode.setX(layoutNode.getX() - minX);
                layoutNode.setY(layoutNode.getY() - minY);
            }
            for (LayoutNode dummyNode : graph.getDummyNodes()) {
                dummyNode.setX(dummyNode.getX() - minX);
                dummyNode.setY(dummyNode.getY() - minY);
            }

            for (LayoutLayer layer : graph.getLayers()) {
                layer.shiftTop(-minY);
            }

            // Shift vertices by minX/minY
            for (LayoutNode layoutNode : graph.getLayoutNodes()) {
                Vertex vertex = layoutNode.getVertex();
                vertex.setPosition(new Point(layoutNode.getLeft(), layoutNode.getTop()));
            }

            // shift links by minX/minY
            for (Map.Entry<Link, List<Point>> entry : linkPositions.entrySet()) {
                Link link = entry.getKey();
                List<Point> points = entry.getValue();
                for (Point p : points) {
                    if (p != null) {
                        p.x -= minX;
                        p.y -= minY;
                    }
                }

                // write points back to links
                link.setControlPoints(points);
            }
        }
    }
}
