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

    private LayoutGraph graph;
    private LayoutGraph prevGraph;

    int maxLayerLength;

    public void setCutEdges(boolean cutEdges) {
        maxLayerLength = cutEdges ? 10 : -1;
        manager.setCutEdges(cutEdges);
    }

    @Override
    public void doLayout(LayoutGraph graph) {}

    public HierarchicalStableLayoutManager() {
        manager = new HierarchicalLayoutManager();
    }


    public void updateLayout(Set<? extends Vertex> vertices, Set<? extends Link> links) {
        graph = new LayoutGraph(links, vertices);

        if (prevGraph == null) {
            manager.doLayout(graph);
        } else {
            // Reverse edges, handle back-edges
            HierarchicalLayoutManager.ReverseEdges.apply(graph);

            // Apply updates if there are any
            ApplyActionUpdates.apply(prevGraph, graph);

            //  Assign Y-Coordinates, Write back to interface
            HierarchicalLayoutManager.WriteResult.apply(graph);
        }

        prevGraph = graph;
    }

    private static class ApplyActionUpdates {

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
            graph.addEdges(movedNode, 10);
        }

        private static boolean canMoveNodeUp(LayoutNode node) {
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

        private static boolean canMoveNodeDown(LayoutNode node) {
            //if (node.getLayer() == graph.getLayerCount() - 1) return false;

            int newLayer = node.getLayer() + 1;
            for (LayoutEdge succEdge : node.getSuccessors()) {
                LayoutNode toNode = succEdge.getTo();
                if (!toNode.isDummy() && toNode.getLayer() == newLayer) {
                    return false;
                }
            }
            return true;
        }

        private static void handleNeighborNodesOnSameLayer(LayoutGraph graph, LayoutNode from, LayoutNode to) {
            if (canMoveNodeDown(to)) {
                graph.removeNodeAndEdges(to);
                //insertNode(to, to.getLayer() + 1);
            } else if (canMoveNodeUp(from)) {
                graph.removeNodeAndEdges(from);
                //insertNode(from, from.getLayer() - 1);
            } else {
                // TODO
                // expandNewLayerBeneath(to);
                // Create a new layer at node.layer + 1 and move the given node there.
                // Adjust remaining layers numbers
            }
        }

        private static void applyAddLinkAction(LayoutGraph graph, Link l) {
            Vertex from = l.getFrom().getVertex();
            Vertex to = l.getTo().getVertex();
            LayoutNode fromNode = graph.getLayoutNode(from);
            LayoutNode toNode = graph.getLayoutNode(to);

            // TODO
            handleNeighborNodesOnSameLayer(graph, fromNode, toNode);

            boolean reversedLink = false;
            if (reversedLink) {
                //updateReversedLinkPositions(l);
                LayoutNode fromNode2 = graph.getLayoutNode(l.getFrom().getVertex());
                LayoutNode toNode2 =  graph.getLayoutNode(l.getTo().getVertex());

                // TODO
                //updateNodeWithReversedEdges(fromNode2);
                //updateNodeWithReversedEdges(toNode2);
            }

            // Edge span multiple layers - must insert dummy nodes
            //insertDummyNodes(edge);
        }


        /**
         * Determines and applies the optimal layer and position for inserting a vertex into the graph layout.
         * <p>
         * The optimal layer minimizes reversed edges and edge lengths. If multiple layers yield the
         * same result, the bottom-most layer is chosen.
         * <p>
         * Within the selected layer, the vertex is inserted at the position that minimizes
         * edge crossings. This is determined by temporarily linking the vertex with its associated
         * edges and evaluating each possible position in the layer.
         *
         * @param graph the layout graph where the vertex will be inserted
         * @param vertex the vertex to be inserted
         */
        private static void applyAddVertexAction(LayoutGraph graph, Vertex vertex) {
           // TODO
        }

        private static void applyRemoveVertexAction(LayoutGraph graph, Vertex vertex) {
            LayoutNode node = graph.getLayoutNode(vertex);
            graph.removeNodeAndEdges(node);
            graph.removeEmptyLayers();
        }

        static void apply(LayoutGraph prevGraph, LayoutGraph graph) {
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

            for (Vertex vertex : removedVertices) {
                applyRemoveVertexAction(graph, vertex);
            }

            for (Link link : removedLinks) {
                graph.removeLink(link);
            }

            for (Vertex vertex : addedVertices) {
                applyAddVertexAction(graph, vertex);
            }

            for (Link link : addedLinks) {
                applyAddLinkAction(graph, link);
            }
        }
    }
}
