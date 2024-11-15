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

import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.Dimension;
import java.awt.Point;
import java.util.*;

public class HierarchicalStableLayoutManager extends LayoutManager{

    private final HierarchicalLayoutManager manager;
    private HashMap<Vertex, VertexAction> vertexToAction;
    private List<VertexAction> vertexActions;
    private List<LinkAction> linkActions;
    private boolean shouldRedrawLayout = true;

    private LayoutGraph graph;
    private LayoutGraph prevGraph;

    public void setCutEdges(boolean cutEdges) {
        manager.setCutEdges(cutEdges);
    }

    @Override
    public void doLayout(LayoutGraph graph) {}

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
        manager = new HierarchicalLayoutManager();
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
        HashSet<Vertex> addedVertices = new HashSet<>(graph.getVertices());
        addedVertices.removeAll(prevGraph.getVertices());

        HashSet<Vertex> removedVertices = new HashSet<>(prevGraph.getVertices());
        removedVertices.removeAll(graph.getVertices());

        HashSet<Link> addedLinks = new HashSet<>(graph.getLinks());
        HashSet<Link> removedLinks = new HashSet<>(prevGraph.getLinks());
        for (Link currLink : graph.getLinks()) {
            for (Link prevLink : prevGraph.getLinks()) {
                if (currLink.equals(prevLink)) {
                    addedLinks.remove(currLink);
                    removedLinks.remove(prevLink);
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

    public void updateLayout(Set<? extends Vertex> vertices, Set<? extends Link> links) {
        graph = new LayoutGraph(links, vertices);
        vertexActions = new LinkedList<>();
        linkActions = new LinkedList<>();
        vertexToAction = new HashMap<>();

        if (shouldRedrawLayout) {
            manager.doLayout(graph);
            shouldRedrawLayout = false;
        } else {
            generateActions();

            // Reverse edges, handle back-edges
            HierarchicalLayoutManager.ReverseEdges.apply(graph);

            // Only apply updates if there are any
            if (!linkActions.isEmpty() || !vertexActions.isEmpty()) {
                new ApplyActionUpdates().run();
            }

            // HierarchicalLayoutManager.LayerManager.apply(currGraph, maxLayerLength);

            // HierarchicalLayoutManager.CrossingReduction.apply(currGraph);

            // HierarchicalLayoutManager.AssignXCoordinates.apply(currGraph);

            //  Assign Y-Coordinates, Write back to interface
            HierarchicalLayoutManager.WriteResult.apply(graph);
        }

        prevGraph = graph;
    }

    private class ApplyActionUpdates {
        public void moveVertex(LayoutGraph graph, Vertex movedVertex) {
            Point newLoc = movedVertex.getPosition();
            LayoutNode movedNode = graph.getLayoutNode(movedVertex);
            graph.removeNodeAndEdges(movedNode);
            graph.removeEmptyLayers();

            int layerNr = 42;
            layerNr = graph.insertNewLayerIfNeeded(movedNode, layerNr);
            graph.addNodeToLayer(movedNode, layerNr);
            movedNode.setX(newLoc.x);
            graph.getLayer(layerNr).sortNodesByX();
            graph.removeEmptyLayers();
            graph.addEdges(movedNode, maxLayerLength);
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
                int optimal = n.calculateOptimalXFromNeighbors();
                r.insert(n, optimal);
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
         * Find the optimal position within the given layer to insert the given node.
         * The optimum is given by the least amount of edge crossings.
         */
        private int optimalPosition(LayoutNode node, int layer) {

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


            Point newLoc = movedVertex.getPosition();
            LayoutNode movedNode = graph.getLayoutNode(movedVertex);
            graph.removeEdges(movedNode);

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
                                removeNode(n, true);
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
        }

        private void removeNodeWithoutRemovingLayer(LayoutNode node) {
            removeNode(node, false);
        }

        private void removeNode(LayoutNode node, boolean removeEmptyLayers) {
            if (removeEmptyLayers) {
                graph.removeEmptyLayers();
            }

            // Remove node from graph layout
            graph.removeNode(node);
        }


        void run() {
            for (VertexAction action : vertexActions) {
                if (action.action == Action.REMOVE) {
                    LayoutNode node = graph.getLayoutNode(action.vertex);
                    graph.removeNodeAndEdges(node);
                    graph.removeEmptyLayers();
                }
            }

            for (LinkAction action : linkActions) {
                if (action.action == Action.REMOVE) {
                    applyRemoveLinkAction(action.link);
                }
            }

            for (VertexAction action : vertexActions) {
                if (action.action == Action.ADD) {
                    applyAddVertexAction(action);
                }
            }

            for (LinkAction action : linkActions) {
                if (action.action == Action.ADD) {
                    applyAddLinkAction(action.link);
                }
            }
        }
    }
}
