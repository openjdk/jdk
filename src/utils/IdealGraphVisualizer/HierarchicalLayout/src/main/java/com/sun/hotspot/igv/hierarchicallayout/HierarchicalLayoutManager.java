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
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.Point;
import java.util.*;


public class HierarchicalLayoutManager extends LayoutManager implements LayoutMover {

    int maxLayerLength;
    private LayoutGraph graph;

    public HierarchicalLayoutManager() {
        setCutEdges(false);
    }

    @Override
    public void setCutEdges(boolean enable) {
        maxLayerLength = enable ? 10 : -1;
    }

    @Override
    public void doLayout(LayoutGraph layoutGraph) {
        layoutGraph.initializeLayout();

        // STEP 1: Remove self edges and reverse edges
        ReverseEdges.apply(layoutGraph);

        // STEP 2: Assign layers and create dummy nodes
        LayerManager.apply(layoutGraph, maxLayerLength);

        // STEP 3: Crossing Reduction
        CrossingReduction.apply(layoutGraph);

        // STEP 4: Assign X coordinates
        AssignXCoordinates.apply(layoutGraph);

        // STEP 5: Write back to interface
        WriteResult.apply(layoutGraph);

        graph = layoutGraph;
    }

    @Override
    public void moveLink(Point linkPos, int shiftX) {
        int layerNr = graph.findLayer(linkPos.y);
        for (LayoutNode node : graph.getLayer(layerNr)) {
            if (node.isDummy() && linkPos.x == node.getX()) {
                LayoutLayer layer = graph.getLayer(layerNr);
                if (layer.contains(node)) {
                    node.setX(linkPos.x + shiftX);
                    layer.sortNodesByX();
                    break;
                }
            }
        }
        writeBack();
    }

    @Override
    public void moveVertices(Set<? extends Vertex> movedVertices) {
        for (Vertex vertex : movedVertices) {
            moveVertex(vertex);
        }
        writeBack();
    }

    private void writeBack() {
        graph.optimizeBackEdgeCrossings();
        graph.straightenEdges();
        WriteResult.apply(graph);
    }

    @Override
    public void moveVertex(Vertex movedVertex) {
        Point newLoc = movedVertex.getPosition();
        LayoutNode movedNode = graph.getLayoutNode(movedVertex);
        assert !movedNode.isDummy();

        int layerNr = graph.findLayer(newLoc.y + movedNode.getOuterHeight() / 2);
        if (movedNode.getLayer() == layerNr) { // we move the node in the same layer
            LayoutLayer layer = graph.getLayer(layerNr);
            if (layer.contains(movedNode)) {
                movedNode.setX(newLoc.x);
                layer.sortNodesByX();
            }
        } else { // only remove edges if we moved the node to a new layer
            if (maxLayerLength > 0) return; // TODO: not implemented
            graph.removeNodeAndEdges(movedNode);
            layerNr = graph.insertNewLayerIfNeeded(movedNode, layerNr);
            graph.addNodeToLayer(movedNode, layerNr);
            movedNode.setX(newLoc.x);
            graph.getLayer(layerNr).sortNodesByX();
            graph.removeEmptyLayers();
            graph.addEdges(movedNode, maxLayerLength);
        }
    }

    public static class ReverseEdges {

        static public void apply(LayoutGraph graph) {
            removeSelfEdges(graph);
            reverseRootInputs(graph);
            depthFirstSearch(graph);
        }

        private static void removeSelfEdges(LayoutGraph graph) {
            for (LayoutNode node : graph.getLayoutNodes()) {
                // Collect self-edges first to avoid concurrent modification
                List<LayoutEdge> selfEdges = new ArrayList<>();
                for (LayoutEdge edge : node.getSuccessors()) {
                    if (edge.getTo() == node) {
                        selfEdges.add(edge);
                    }
                }

                // Remove each self-edge
                for (LayoutEdge edge : selfEdges) {
                    node.removeSuccessor(edge);
                    node.removePredecessor(edge);
                }
            }
        }

        private static void reverseRootInputs(LayoutGraph graph) {
            for (LayoutNode node : graph.getLayoutNodes()) {
                if (node.getVertex().isRoot()) {
                    for (LayoutEdge predEdge : new ArrayList<>(node.getPredecessors())) {
                        reverseEdge(predEdge);
                    }
                }
            }
        }

        public static void reverseEdge(LayoutEdge edge) {
            edge.reverse();

            LayoutNode fromNode = edge.getFrom();
            LayoutNode toNode = edge.getTo();
            int relativeFrom = edge.getRelativeFromX();
            int relativeTo = edge.getRelativeToX();

            edge.setFrom(toNode);
            edge.setTo(fromNode);
            edge.setRelativeFromX(relativeTo);
            edge.setRelativeToX(relativeFrom);

            fromNode.removeSuccessor(edge);
            fromNode.addPredecessor(edge);
            toNode.removePredecessor(edge);
            toNode.addSuccessor(edge);

            fromNode.computeReversedLinkPoints(false);
            toNode.computeReversedLinkPoints(false);
        }

        private static void depthFirstSearch(LayoutGraph graph) {
            Set<LayoutNode> visited = new HashSet<>();
            Set<LayoutNode> active = new HashSet<>();

            for (LayoutNode startNode : graph.getLayoutNodes()) {
                Deque<LayoutNode> stack = new ArrayDeque<>();
                stack.push(startNode);

                while (!stack.isEmpty()) {
                    LayoutNode node = stack.pop();

                    if (visited.contains(node)) {
                        active.remove(node);
                        continue;
                    }

                    stack.push(node);
                    visited.add(node);
                    active.add(node);

                    for (LayoutEdge edge : new ArrayList<>(node.getSuccessors())) {
                        LayoutNode successor = edge.getTo();
                        if (active.contains(successor)) {
                            reverseEdge(edge);
                        } else if (!visited.contains(successor)) {
                            stack.push(successor);
                        }
                    }
                }
            }
        }
    }

    public static class LayerManager {

        private static void assignLayerDownwards(LayoutGraph graph) {
            ArrayList<LayoutNode> workingList = new ArrayList<>();

            // add all root nodes to layer 0
            for (LayoutNode node : graph.getLayoutNodes()) {
                if (!node.hasPredecessors()) {
                    workingList.add(node);
                    node.setLayer(0);
                }
            }

            // assign layers downwards starting from roots
            int layer = 1;
            while (!workingList.isEmpty()) {
                ArrayList<LayoutNode> newWorkingList = new ArrayList<>();
                for (LayoutNode node : workingList) {
                    for (LayoutEdge succEdge : node.getSuccessors()) {
                        LayoutNode succNode = succEdge.getTo();
                        if (succNode.getLayer() == -1) {
                            // This node was not assigned before.
                            boolean assignedPred = true;
                            for (LayoutEdge predEdge : succNode.getPredecessors()) {
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

            int layerCount = layer - 1;
            for (LayoutNode n : graph.getLayoutNodes()) {
                n.setLayer((layerCount - 1 - n.getLayer()));
            }
        }

        private static void assignLayerUpwards(LayoutGraph graph) {
            ArrayList<LayoutNode> workingList = new ArrayList<>();
            // add all leaves to working list, reset layer of non-leave nodes
            for (LayoutNode node : graph.getLayoutNodes()) {
                if (!node.hasSuccessors()) {
                    workingList.add(node);
                } else {
                    node.setLayer(-1);
                }
            }

            // assign layer upwards starting from leaves
            // sinks non-leave nodes down as much as possible
            int layer = 1;
            while (!workingList.isEmpty()) {
                ArrayList<LayoutNode> newWorkingList = new ArrayList<>();
                for (LayoutNode node : workingList) {
                    if (node.getLayer() < layer) {
                        for (LayoutEdge predEdge : node.getPredecessors()) {
                            LayoutNode predNode = predEdge.getFrom();
                            if (predNode.getLayer() == -1) {
                                // This node was not assigned before.
                                boolean assignedSucc = true;
                                for (LayoutEdge succEdge : predNode.getSuccessors()) {
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


        static private void assignLayers(LayoutGraph graph) {
            assignLayerDownwards(graph);
            assignLayerUpwards(graph);
        }

        static private void createDummyNodes(LayoutGraph graph, int maxLayerLength) {
            List<LayoutNode> layoutNodes = new ArrayList<>(graph.getLayoutNodes());
            layoutNodes.sort(LAYOUT_NODE_DEGREE_COMPARATOR);

            // Generate initial ordering
            HashSet<LayoutNode> visited = new HashSet<>();
            for (LayoutNode layoutNode : layoutNodes) {
                if (layoutNode.getLayer() == 0) {
                    graph.getLayer(0).add(layoutNode);
                    visited.add(layoutNode);
                } else if (!layoutNode.hasPredecessors()) {
                    graph.getLayer(layoutNode.getLayer()).add(layoutNode);
                    visited.add(layoutNode);
                }
            }

            for (LayoutNode layoutNode : layoutNodes) {
                graph.createDummiesForNodeSuccessor(layoutNode, maxLayerLength);
            }

            for (int i = 0; i < graph.getLayerCount() - 1; i++) {
                for (LayoutNode n : graph.getLayer(i)) {
                    for (LayoutEdge e : n.getSuccessors()) {
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

        static public void apply(LayoutGraph graph, int maxLayerLength) {
            int cntUnassigned = 0;
            for (LayoutNode node : graph.getLayoutNodes()) {
                if (node.getLayer() < 0) {
                    cntUnassigned++;
                }
            }
            System.out.println("cntUnassigned before " + cntUnassigned);

            assignLayers(graph);

            cntUnassigned = 0;
            int maxLayer = 0;
            for (LayoutNode node : graph.getLayoutNodes()) {
                maxLayer = Math.max(maxLayer, node.getLayer());
                if (node.getLayer() < 0) {
                    cntUnassigned++;
                }
            }
            System.out.println("cntUnassigned after " + cntUnassigned);

            graph.initLayers(maxLayer + 1);


            for (LayoutNode node : graph.getLayoutNodes()) {
                int currentLayer = node.getLayer();
                for (LayoutEdge e : node.getSuccessors()) {
                    int layerBelow = e.getTo().getLayer();
                    assert layerBelow > currentLayer;  // TODO: fails
                }

                for (LayoutEdge e : node.getPredecessors()) {
                    int layerAbove = e.getFrom().getLayer();
                    assert layerAbove < currentLayer;
                }
            }


            createDummyNodes(graph, maxLayerLength);
            graph.updatePositions();
        }
    }


    public static class CrossingReduction {

        /**
         * Applies the crossing reduction algorithm to the given graph.
         *
         * @param hierarchicalGraph the layout graph to optimize
         */
        public static void apply(LayoutGraph hierarchicalGraph) {
            hierarchicalGraph.updateSpacings(true);

            for (int i = 0; i < CROSSING_ITERATIONS; i++) {
                sweep(hierarchicalGraph, true);  // Downwards sweep
                sweep(hierarchicalGraph, false); // Upwards sweep
            }

            for (LayoutLayer layer : hierarchicalGraph.getLayers()) {
                layer.sort(NODE_X_COMPARATOR);
                layer.updateMinXSpacing(true);
                layer.updateNodeIndices();
            }
        }

        /**
         * Performs a sweep over the layers to reorder nodes and reduce crossings.
         *
         * @param hierarchicalGraph the layout graph
         * @param downwards         direction of the sweep
         */
        private static void sweep(LayoutGraph hierarchicalGraph, boolean downwards) {
            List<LayoutLayer> layers = hierarchicalGraph.getLayers();

            if (downwards) {
                // Process layers from top to bottom
                for (int i = 1; i < layers.size(); i++) {
                    LayoutLayer layer = layers.get(i);
                    layer.orderByBarycenters(NeighborType.PREDECESSORS);
                    layer.reduceCrossings(NeighborType.PREDECESSORS);
                }
            } else {
                // Process layers from bottom to top
                for (int i = layers.size() - 2; i >= 0; i--) {
                    LayoutLayer layer = layers.get(i);
                    layer.orderByBarycenters(NeighborType.SUCCESSORS);
                    layer.reduceCrossings(NeighborType.SUCCESSORS);
                }
            }
            for (LayoutLayer layer : layers) {
                layer.updateMinXSpacing(false);
            }
        }
    }

    public static class AssignXCoordinates {

        static public void apply(LayoutGraph graph) {

            for (LayoutLayer layer : graph.getLayers()) {
                layer.initXPositions();
            }
            for (int k = 0; k < SWEEP_ITERATIONS; k++) {

                for (int i = graph.getLayerCount() - 2; i >= 0; i--) {
                    LayoutLayer layer = graph.getLayer(i);
                    processLayerUp(layer);
                }
                shiftToZero(graph);

                for (int i = 0; i < graph.getLayerCount(); i++) {
                    LayoutLayer layer = graph.getLayer(i);
                    processLayerDown(layer);
                }
                shiftToZero(graph);

                optimizeByNeighborhood(graph);
                shiftToZero(graph);


            }
            for (int i = graph.getLayerCount() - 2; i >= 0; i--) {
                LayoutLayer layer = graph.getLayer(i);
                processLayerUp(layer);
            }
            shiftToZero(graph);
            graph.optimizeBackEdgeCrossings();
            graph.straightenEdges();
        }

        static private void optimizeByNeighborhood(LayoutGraph graph) {
            List<LayoutNode> processNodes = new ArrayList<>(graph.getLayoutNodes());
            for (LayoutNode layoutNode : processNodes) {
                layoutNode.setOptimalX(layoutNode.calculateOptimalXFromNeighbors());
            }
            processNodes.sort(NODES_OPTIMAL_DIFFERENCE);
            for (LayoutNode layoutNode : processNodes) {
                layoutNode.setX(layoutNode.calculateOptimalXFromNeighbors());
                graph.getLayer(layoutNode.getLayer()).updateMinXSpacing(false);
            }
        }

        static private void shiftToZero(LayoutGraph graph) {
            int minX = Integer.MAX_VALUE;
            List<LayoutNode> allNodes = graph.getAllNodes();
            Set<LayoutNode> uniqueNodes = new LinkedHashSet<>(allNodes);
            assert uniqueNodes.size() == allNodes.size();
            for (LayoutNode node : allNodes) {
                minX = Math.min(node.getX(), minX);
            }
            if (0 < minX && minX < Integer.MAX_VALUE) {
                for (LayoutNode node : allNodes) {
                    node.shiftX(-minX);
                }
            }
        }

        static private void processLayerUp(LayoutLayer layer) {
            layer.sort(NODE_PRIORITY.thenComparing(DUMMY_NODES_FIRST).thenComparingInt(LayoutNode::getInDegree));
            ArrayList<LayoutNode> leaves = new ArrayList<>();
            for (LayoutNode node : layer) {
                int optimalX = Integer.MAX_VALUE;
                if (node.hasSuccessors()) {
                    optimalX = node.calculateOptimalXFromSuccessors(true);
                } else {
                    leaves.add(node);
                }
                node.setX(optimalX);
            }
            layer.sort(NODE_X_COMPARATOR);
            layer.updateMinXSpacing(false);
            for (LayoutNode leaf : leaves) {
                int leafWidth = leaf.getOuterWidth() + 2 * NODE_OFFSET;
                int prevRight = 0;
                for (LayoutNode node : layer) {
                    int currLeft = node.getOuterLeft();
                    if (prevRight + leafWidth <= currLeft) {
                        int x = prevRight + NODE_OFFSET;
                        leaf.setX(x);
                        break;
                    }
                    prevRight = node.getOuterRight();
                }
                layer.sort(NODE_X_COMPARATOR);
            }
        }

        static private void processLayerDown(LayoutLayer layer) {
            for (LayoutNode node : layer) {
                if (node.isDummy()) {
                    node.setX(node.calculateOptimalXFromPredecessors(true));
                } else if (!node.hasSuccessors()) {
                    node.setX(node.calculateOptimalXFromPredecessors(false));
                } else if (!node.hasPredecessors()) {
                    node.setX(node.calculateOptimalXFromSuccessors(false));
                } else {
                    node.setX(node.calculateOptimalXFromNeighbors());
                }
            }
            layer.sort(NODE_X_COMPARATOR);
            layer.updateMinXSpacing(false);
        }

    }

    public static class WriteResult {

        private static HashMap<Link, List<Point>> computeLinkPositions(LayoutGraph graph) {
            HashMap<Link, List<Point>> linkToSplitEndPoints = new HashMap<>();
            HashMap<Link, List<Point>> linkPositions = new HashMap<>();

            for (LayoutNode layoutNode : graph.getLayoutNodes()) {
                for (LayoutEdge predEdge : layoutNode.getPredecessors()) {
                    LayoutNode fromNode = predEdge.getFrom();
                    LayoutNode toNode = predEdge.getTo();

                    ArrayList<Point> linkPoints = new ArrayList<>();
                    // input edge stub
                    linkPoints.add(new Point(predEdge.getEndX(), toNode.getTop()));
                    linkPoints.add(new Point(predEdge.getEndX(), graph.getLayer(toNode.getLayer()).getTop() - LAYER_OFFSET));

                    LayoutEdge curEdge = predEdge;
                    while (fromNode.isDummy() && fromNode.hasPredecessors()) {
                        linkPoints.add(new Point(fromNode.getCenterX(), graph.getLayer(fromNode.getLayer()).getBottom() + LAYER_OFFSET));
                        linkPoints.add(new Point(fromNode.getCenterX(), graph.getLayer(fromNode.getLayer()).getTop() - LAYER_OFFSET));
                        curEdge = fromNode.getPredecessors().get(0);
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
                for (LayoutEdge succEdge : layoutNode.getSuccessors()) {
                    if (succEdge.getLink() == null) continue;

                    LayoutNode fromNode = succEdge.getFrom();
                    LayoutNode toNode = succEdge.getTo();

                    ArrayList<Point> linkPoints = new ArrayList<>();
                    linkPoints.add(new Point(succEdge.getStartX(), fromNode.getBottom()));
                    linkPoints.add(new Point(succEdge.getStartX(), graph.getLayer(fromNode.getLayer()).getBottom() + LAYER_OFFSET));

                    LayoutEdge curEdge = succEdge;
                    while (toNode.isDummy() && toNode.hasSuccessors()) {
                        linkPoints.add(new Point(toNode.getCenterX(), graph.getLayer(toNode.getLayer()).getTop() - LAYER_OFFSET));
                        linkPoints.add(new Point(toNode.getCenterX(), graph.getLayer(toNode.getLayer()).getBottom() + LAYER_OFFSET));
                        curEdge = toNode.getSuccessors().get(0);
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

        public static void apply(LayoutGraph graph) {
            // Assign Y coordinates
            graph.positionLayers();

            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;

            HashMap<Link, List<Point>> linkPositions = computeLinkPositions(graph);

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
                layer.moveLayerVertically(-minY);
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
