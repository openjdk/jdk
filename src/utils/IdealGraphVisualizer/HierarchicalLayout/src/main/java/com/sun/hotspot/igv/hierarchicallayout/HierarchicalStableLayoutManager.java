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

import static com.sun.hotspot.igv.hierarchicallayout.LayoutGraph.LINK_COMPARATOR;
import static com.sun.hotspot.igv.hierarchicallayout.LayoutNode.NODE_POS_COMPARATOR;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Vertex;
import java.util.*;

public class HierarchicalStableLayoutManager extends LayoutManager {

    int maxLayerLength;

    private final HierarchicalLayoutManager manager;
    private final HashMap<Vertex, VertexAction> vertexToAction;
    private final List<VertexAction> vertexActions;
    private final List<LinkAction> linkActions;

    private enum Action {
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

    public HierarchicalStableLayoutManager() {
        manager = new HierarchicalLayoutManager();
        vertexActions = new LinkedList<>();
        linkActions = new LinkedList<>();
        vertexToAction = new HashMap<>();
    }

    public void setCutEdges(boolean cutEdges) {
        maxLayerLength = cutEdges ? 10 : -1;
        manager.setCutEdges(cutEdges);
    }

    private LayoutGraph prevGraph;

    @Override
    public void doLayout(LayoutGraph graph) {
        if (this.prevGraph == null) {
            manager.doLayout(graph);
        } else {
            updateLayout(graph);
            HierarchicalLayoutManager.WriteResult.apply(graph);
        }
        this.prevGraph = graph;

        System.out.println("real vertex in g");
        for (Vertex v : graph.getVertices()) {
            System.out.print(v.getPosition().x + " " + v.getPosition().y + ", ");
        }
        System.out.println();
        System.out.println("computed LayoutNode->Vertex in graph");
        for (LayoutNode node : prevGraph.getLayoutNodes()) {
            System.out.print(node.getVertex().getPosition().x + " " + node.getVertex().getPosition().y + ", ");
        }
        System.out.println();
    }

    public void updateLayout(LayoutGraph graph) {
        HashSet<LayoutNode> newLayoutNode = new HashSet<>();

        // Reset layout structures
        graph.clearLayout();

        // create empty layers
        graph.initLayers(prevGraph.getLayerCount());

        // Set up layout nodes for each vertex
        for (Vertex vertex : graph.getVertices()) {
            LayoutNode newNode = graph.createLayoutNode(vertex);
            if (prevGraph.hasLayoutNode(vertex)) {
                LayoutNode prevNode = prevGraph.getLayoutNode(vertex);
                newNode.setPos(prevNode.getPos());
                newNode.setX(prevNode.getX());
                newNode.setY(prevNode.getY());
                graph.addNodeToLayer(newNode, prevNode.getLayer());
            } else {
                newLayoutNode.add(newNode);
            }
        }

        System.out.println("addedVertices cnt " + newLayoutNode.size());

        // Set up layout edges in a sorted order for reproducibility
        List<Link> sortedLinks = new ArrayList<>(graph.getLinks());
        sortedLinks.sort(LINK_COMPARATOR);
        for (Link link : sortedLinks) {
            graph.createLayoutEdge(link);
        }

        for (LayoutNode node : graph.getLayoutNodes()) {
           // graph.addEdges(node, maxLayerLength);
        }
    }

    private void generateActions(SortedSet<Vertex> newVertices, Set<Link> newLinks) {
        vertexActions.clear();
        linkActions.clear();
        vertexToAction.clear();

        HashSet<Link> oldLinks = new HashSet<>(prevGraph.getLinks());
        HashSet<Vertex> oldVertices = new HashSet<>(prevGraph.getVertices());

        HashSet<Vertex> addedVertices = new HashSet<>(newVertices);
        addedVertices.removeAll(oldVertices);

        HashSet<Vertex> removedVertices = new HashSet<>(oldVertices);
        removedVertices.removeAll(newVertices);

        HashSet<Link> addedLinks = new HashSet<>(newLinks);
        HashSet<Link> removedLinks = new HashSet<>(oldLinks);
        for (Link link1 : newLinks) {
            for (Link link2 : oldLinks) {
                if (link1.equals(link2)) {
                    addedLinks.remove(link1);
                    removedLinks.remove(link2);
                    break;
                }
            }
        }

        assert oldLinks.size() + addedLinks.size() - removedLinks.size() == newLinks.size();
        assert oldVertices.size() + addedVertices.size() - removedVertices.size() == newVertices.size();


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

    public static final Comparator<LayoutNode> nodeProcessingUpComparator = (n1, n2) -> {
        if (n1.isDummy()) {
            if (n2.isDummy()) {
                return 0;
            }
            return -1;
        }
        if (n2.isDummy()) {
            return 1;
        }
        return n1.getSuccessors().size() - n2.getSuccessors().size();
    };

    public static class NodeRow {

        private final TreeSet<LayoutNode> treeSet;
        private final ArrayList<Integer> space;

        public NodeRow(ArrayList<Integer> space) {
            treeSet = new TreeSet<>(NODE_POS_COMPARATOR);
            this.space = space;
        }

        public int offset(LayoutNode n1, LayoutNode n2) {
            int v1 = space.get(n1.getPos()) + n1.getOuterWidth();
            int v2 = space.get(n2.getPos());
            return v2 - v1;
        }

        public void insert(LayoutNode n, int pos) {
            SortedSet<LayoutNode> headSet = treeSet.headSet(n);
            LayoutNode leftNeighbor;
            int minX = Integer.MIN_VALUE;
            if (!headSet.isEmpty()) {
                leftNeighbor = headSet.last();
                minX = leftNeighbor.getOuterRight() + offset(leftNeighbor, n);
            }

            if (pos < minX) {
                n.setX(minX);
            } else {

                LayoutNode rightNeighbor;
                SortedSet<LayoutNode> tailSet = treeSet.tailSet(n);
                int maxX = Integer.MAX_VALUE;
                if (!tailSet.isEmpty()) {
                    rightNeighbor = tailSet.first();
                    maxX = rightNeighbor.getX() - offset(n, rightNeighbor) - n.getOuterWidth();
                }

                n.setX(Math.min(pos, maxX));

                assert minX <= maxX : minX + " vs " + maxX;
            }

            treeSet.add(n);
        }
    }


    private int calculateOptimalBoth(LayoutNode n) {
        if (!n.hasPredecessors() && !n.hasSuccessors()) {
            return n.getX();
        }

        int[] values = new int[n.getPredecessors().size() + n.getSuccessors().size()];
        int i = 0;

        for (LayoutEdge e : n.getPredecessors()) {
            values[i] = e.getFromX() - e.getRelativeToX();
            i++;
        }

        for (LayoutEdge e : n.getSuccessors()) {
            values[i] = e.getToX() - e.getRelativeFromX();
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

    private class ApplyActionUpdates {
        /**
         * Find the optimal position within the given layerNr to insert the given node.
         * The optimum is given by the least amount of edge crossings.
         */
        private int optimalPosition(LayoutNode node, int layerNr) {
            LayoutLayer layer = prevGraph.getLayer(layerNr);
            layer.sort(NODE_POS_COMPARATOR);
            int edgeCrossings = Integer.MAX_VALUE;
            int optimalPos = -1;

            // Try each possible position in the layerNr
            for (int i = 0; i < layer.size() + 1; i++) {
                int xCoord;
                if (i == 0) {
                    xCoord = layer.get(i).getX() - node.getOuterWidth() - 1;
                } else {
                    xCoord = layer.get(i - 1).getX() + layer.get(i - 1).getOuterWidth() + 1;
                }

                int currentCrossings = 0;

                if (prevGraph.hasLayer(layerNr - 1)) {
                    List<LayoutNode> predNodes = prevGraph.getLayer(layerNr - 1);
                    // For each link with an end point in vertex, check how many edges cross it
                    for (LayoutEdge edge : node.getPredecessors()) {
                        if (edge.getFrom().getLayer() == layerNr - 1) {
                            int fromNodeXCoord = edge.getFrom().getX();
                            if (!edge.getFrom().isDummy()) {
                                fromNodeXCoord += edge.getRelativeFromX();
                            }
                            int toNodeXCoord = xCoord;
                            if (!node.isDummy()) {
                                toNodeXCoord += edge.getRelativeToX();
                            }
                            for (LayoutNode n : predNodes) {
                                for (LayoutEdge e : n.getSuccessors()) {
                                    if (e.getTo() == null) {
                                        continue;
                                    }
                                    int compFromXCoord = e.getFrom().getX();
                                    if (!e.getFrom().isDummy()) {
                                        compFromXCoord += e.getRelativeFromX();
                                    }
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
                if (prevGraph.hasLayer(layerNr + 1)) {
                    List<LayoutNode> succsNodes = prevGraph.getLayer(layerNr + 1);
                    // For each link with an end point in vertex, check how many edges cross it
                    for (LayoutEdge edge : node.getSuccessors()) {
                        if (edge.getTo().getLayer() == layerNr + 1) {
                            int toNodeXCoord = edge.getTo().getX();
                            if (!edge.getTo().isDummy()) {
                                toNodeXCoord += edge.getRelativeToX();
                            }
                            int fromNodeXCoord = xCoord;
                            if (!node.isDummy()) {
                                fromNodeXCoord += edge.getRelativeFromX();
                            }
                            for (LayoutNode n : succsNodes) {
                                for (LayoutEdge e : n.getPredecessors()) {
                                    if (e.getFrom() == null) {
                                        continue;
                                    }
                                    int compFromXCoord = e.getFrom().getX();
                                    if (!e.getFrom().isDummy()) {
                                        compFromXCoord += e.getRelativeFromX();
                                    }
                                    int compToXCoord = e.getTo().getX();
                                    if (!e.getTo().isDummy()) {
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


        private void applyAddLinkAction(Link link) {
            prevGraph.addLink(link);

            // if (toNode.layer == fromNode.layer) handleNeighborNodesOnSameLayer(fromNode, toNode);
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
            } else if (prevGraph.hasLayers()) {
                return 0;
            }

            int reversedEdges = Integer.MAX_VALUE;
            int totalEdgeLength = Integer.MAX_VALUE;
            int neighborsOnSameLayer = Integer.MAX_VALUE;
            int layer = -1;
            for (int i = 0; i < prevGraph.getLayerCount(); i++) {
                int curReversedEdges = 0;
                int curTotalEdgeLength = 0;
                int curNeighborsOnSameLayer = 0;
                for (Link link : links) {
                    LayoutNode fromNode = prevGraph.getLayoutNode(link.getFrom().getVertex());
                    LayoutNode toNode = prevGraph.getLayoutNode(link.getTo().getVertex());
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
            List<Link> links = new ArrayList<>();
            for (LinkAction a : action.linkActions) {
                links.add(a.link);
            }
            int layer = optimalLayer(action.vertex, links);

            prevGraph.addVertex(action.vertex);
            LayoutNode node = prevGraph.createLayoutNode(action.vertex);

            if (prevGraph.getLayer(layer).isEmpty()) {
                node.setPos(0);
            } else {
                node.setPos(optimalPosition(node, layer));
            }

            layer = prevGraph.insertNewLayerIfNeeded(node, layer);
            prevGraph.addNodeToLayer(node, layer);
            node.setX(0);
            prevGraph.getLayer(layer).add(node.getPos(), node);
            prevGraph.getLayer(layer).sortNodesByX();
            prevGraph.removeEmptyLayers();
            prevGraph.addEdges(node, maxLayerLength);

            // Add associated edges
            for (LinkAction a : action.linkActions) {
                if (a.action == Action.ADD) {
                    applyAddLinkAction(a.link);
                }
            }
        }

        private void applyRemoveLinkAction(Link link) {
            prevGraph.removeLink(link);
        }

        private void applyRemoveVertexAction(VertexAction action) {
           prevGraph.removeVertex(action.vertex);

            // Remove associated edges
            for (LinkAction a : action.linkActions) {
                if (a.action == Action.REMOVE) {
                    applyRemoveLinkAction(a.link);
                }
            }
        }

        void run(SortedSet<Vertex> vertices, Set<Link> links) {
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
            for (LayoutNode n : prevGraph.getLayoutNodes()) {
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
            superfluousLinks.removeAll(links);
            for (Link l : superfluousLinks) {
                applyRemoveLinkAction(l);
                layoutedLinks.remove(l);
            }
            Set<Link> missingLinks = new HashSet<>(links);
            missingLinks.removeAll(layoutedLinks);
            for (Link l : missingLinks) {
                applyAddLinkAction(l);
                layoutedLinks.add(l);
            }
            assert vertices.size() == layoutedNodes.size();
            assert links.size() == layoutedLinks.size();
        }
    }

}
