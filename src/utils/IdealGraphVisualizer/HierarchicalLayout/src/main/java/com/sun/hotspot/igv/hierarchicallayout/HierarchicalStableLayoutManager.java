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

    int maxLayerLength;

    public void setCutEdges(boolean cutEdges) {
        maxLayerLength = cutEdges ? 10 : -1;
        manager.setCutEdges(cutEdges);
    }

    @Override
    public void doLayout(LayoutGraph graph) {

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
        private void adjustXCoordinates(int layerNr) {
            // TODO
            LayoutLayer layer = graph.getLayer(layerNr);
            for (LayoutNode n : layer) {
                //int optimal = n.calculateOptimalXFromNeighbors();
            }
        }

        private void updateReversedLinkPositions(Link link) {
            LayoutNode fromNode = graph.getLayoutNode(link.getFrom().getVertex());
            LayoutNode toNode =  graph.getLayoutNode(link.getTo().getVertex());

            // TODO
            updateNodeWithReversedEdges(fromNode);
            updateNodeWithReversedEdges(toNode);
        }

        private void updateNodeWithReversedEdges(LayoutNode node) {
           // TODO
        }

        /**
         * Find the optimal position within the given layerNr to insert the given node.
         * The optimum is given by the least amount of edge crossings.
         */
        private int optimalPosition(LayoutNode node, int layerNr) {
            LayoutLayer layoutLayer = graph.getLayer(layerNr);
            layoutLayer.sortNodesByX();

            int optimalPos = 0;
            // TODO
            // Try each possible position in the layerNr

            return optimalPos;
        }

        /**
         * Insert node at the assigned layer, updating the positions of the nodes within
         * the layer
         */
        private void insertNode(LayoutNode node, int layerNr) {
            LayoutLayer layoutLayer = graph.getLayer(layerNr);
            // TODO

            adjustXCoordinates(layerNr);
        }


        private void insertDummyNodes(LayoutEdge edge) {
            LayoutNode from = edge.getFrom();
            LayoutNode to = edge.getTo();
            // TODO
        }

        private boolean canMoveNodeUp(LayoutNode node) {
            if (node.getLayer() == 0) {
                return false;
            }
            int newLayer = node.getLayer() - 1;
            for (LayoutEdge predEdge : node.getPredecessors()) {
                LayoutNode fromNode = predEdge.getFrom();
                if (!fromNode.isDummy() && fromNode.getLayer() == newLayer) {
                    return false;
                }
            }
            return true;
        }

        private boolean canMoveNodeDown(LayoutNode node) {
            if (node.getLayer() == graph.getLayerCount() - 1) {
                return false;
            }
            int newLayer = node.getLayer() + 1;
            for (LayoutEdge succEdge : node.getSuccessors()) {
                LayoutNode toNode = succEdge.getTo();
                if (!toNode.isDummy() && toNode.getLayer() == newLayer) {
                    return false;
                }
            }
            return true;
        }

        private void moveNodeUp(LayoutNode node) {
            // TODO
            removeNodeWithoutRemovingLayer(node);
            insertNode(node, node.getLayer() - 1);

        }

        private void moveNodeDown(LayoutNode node) {
            // TODO
            removeNodeWithoutRemovingLayer(node);
            insertNode(node, node.getLayer() + 1);
        }

        private void handleNeighborNodesOnSameLayer(LayoutNode from, LayoutNode to) {
            if (canMoveNodeDown(to)) {
                moveNodeDown(to);
            } else if (canMoveNodeUp(from)) {
                moveNodeUp(from);
            } else {
                // TODO
                // expandNewLayerBeneath(to);
                // Create a new layer at node.layer + 1 and move the given node there.
                // Adjust remaining layers numbers
            }
        }

        private void applyAddLinkAction(Link l) {
            Vertex from = l.getFrom().getVertex();
            Vertex to = l.getTo().getVertex();
            LayoutNode fromNode = graph.getLayoutNode(from);
            LayoutNode toNode = graph.getLayoutNode(to);

            // TODO
            handleNeighborNodesOnSameLayer(fromNode, toNode);

            boolean reversedLink = false;
            if (reversedLink) {
                updateReversedLinkPositions(l);
            }

            // Edge span multiple layers - must insert dummy nodes
            //insertDummyNodes(edge);
        }

        /**
         * Calculate which layer the given vertex should be inserted at to minimize
         * reversed edges and edge lengths
         * If there are multiple options, choose the bottom-most layer
         *
         * @return the optimal layer to insert the given vertex
         */
        private int optimalLayer(Vertex vertex, List<Link> links) {
            int optimalLayer = 0;
            // TODO
            return optimalLayer;
        }

        private void applyAddVertexAction(VertexAction action) {

            List<Link> links = new ArrayList<>();
            for (LinkAction a : action.linkActions) {
                links.add(a.link);
            }
            int layer = optimalLayer(action.vertex, links);

            // Temporarily add the links so that the node insertion accounts for edge
            // crossings

            // insertNode(node, layer);

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
            LayoutNode toNode = graph.getLayoutNode(to);
            LayoutNode fromNode = graph.getLayoutNode(from);

            // if reversed edge

            // Remove preds-edges bottom up, starting at "to" node
            // Cannot start from "from" node since there might be joint edges
            // TODO
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
