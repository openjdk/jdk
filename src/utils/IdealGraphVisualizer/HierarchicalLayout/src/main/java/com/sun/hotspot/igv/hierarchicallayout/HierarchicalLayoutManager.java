/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 * @author Thomas Wuerthinger
 */
public class HierarchicalLayoutManager extends LayoutManager implements LayoutMover {

    int maxLayerLength;
    private LayoutGraph graph;
    private boolean layoutSelfEdges = false;

    public HierarchicalLayoutManager() {
        setCutEdges(false);
        setLayoutSelfEdges(false);
    }

    public void setLayoutSelfEdges(boolean layoutSelfEdges) {
        this.layoutSelfEdges = layoutSelfEdges;
    }

    @Override
    public void setCutEdges(boolean enable) {
        maxLayerLength = enable ? 10 : -1;
    }

    @Override
    public void doLayout(LayoutGraph layoutGraph) {
        layoutGraph.initializeLayout();

        removeSelfEdges(layoutGraph);

        // STEP 1: Reverse edges
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
        graph.updateLayerMinXSpacing();
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

    /**
     * Removes self-edges from nodes in the graph. If self-edges are to be included in the layout
     * (`layoutSelfEdges` is true), it stores them in the node for later processing and marks the graph
     * to display self-edges
     */
    private void removeSelfEdges(LayoutGraph graph) {
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
            if (layoutSelfEdges) {
                for (LayoutEdge selfEdge : selfEdges) {
                    node.setSelfEdge(selfEdge);
                }
                graph.setShowSelfEdges(true);
            }
        }
        if (layoutSelfEdges) {
            graph.setShowSelfEdges(true);
        }
    }

    public List<LayoutNode> getNodes() {
        return graph.getAllNodes();
    }

    public static class ReverseEdges {

        static public void apply(LayoutGraph graph) {
            reverseRootInputs(graph);
            depthFirstSearch(graph);

            for (LayoutNode node : graph.getLayoutNodes()) {
                node.computeReversedLinkPoints(false);
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
            assignLayers(graph);
            createDummyNodes(graph, maxLayerLength);
            graph.updatePositions();
        }
    }

    static class CrossingReduction {

        public static void apply(LayoutGraph graph) {
            for (int i = 0; i < graph.getLayerCount(); i++) {
                graph.getLayer(i).updateNodeIndices();
                graph.getLayer(i).initXPositions();
            }

            for (int i = 0; i < CROSSING_ITERATIONS; i++) {
                downSweep(graph);
                upSweep(graph);
            }
            downSweep(graph);
        }

        private static void downSweep(LayoutGraph graph) {

            for (int i = 1; i < graph.getLayerCount(); i++) {
                for (LayoutNode n : graph.getLayer(i)) {
                    n.setCrossingNumber(0);
                }
                for (LayoutNode n : graph.getLayer(i)) {
                    int sum = 0;
                    int count = 0;
                    for (LayoutEdge e : n.getPredecessors()) {
                        sum += e.getStartX();
                        count++;
                    }

                    if (count > 0) {
                        sum /= count;
                        n.setCrossingNumber(sum);
                    }
                }
                updateCrossingNumbers(graph.getLayer(i), true);
                graph.getLayer(i).sort(NODE_CROSSING_COMPARATOR);
                graph.getLayer(i).initXPositions();
                graph.getLayer(i).updateNodeIndices();
            }
        }

        private static void updateCrossingNumbers(LayoutLayer layer, boolean down) {
            for (int i = 0; i < layer.size(); i++) {
                LayoutNode n = layer.get(i);
                LayoutNode prev = null;
                if (i > 0) {
                    prev = layer.get(i - 1);
                }
                LayoutNode next = null;
                if (i < layer.size() - 1) {
                    next = layer.get(i + 1);
                }
                boolean cond = !n.hasSuccessors();
                if (down) {
                    cond = !n.hasPredecessors();
                }
                if (cond) {
                    if (prev != null && next != null) {
                        n.setCrossingNumber((prev.getCrossingNumber() + next.getCrossingNumber()) / 2);
                    } else if (prev != null) {
                        n.setCrossingNumber(prev.getCrossingNumber());
                    } else if (next != null) {
                        n.setCrossingNumber(next.getCrossingNumber());
                    }
                }
            }
        }

        private static void upSweep(LayoutGraph graph) {
            for (int i = graph.getLayerCount() - 2; i >= 0; i--) {
                for (LayoutNode n : graph.getLayer(i)) {
                    n.setCrossingNumber(0);
                }
                for (LayoutNode n : graph.getLayer(i)) {
                    int count = 0;
                    int sum = 0;
                    for (LayoutEdge e : n.getSuccessors()) {
                        sum += e.getEndX();
                        count++;
                    }
                    if (count > 0) {
                        sum /= count;
                        n.setCrossingNumber(sum);
                    }

                }
                updateCrossingNumbers(graph.getLayer(i), false);
                graph.getLayer(i).sort(NODE_CROSSING_COMPARATOR);
                graph.getLayer(i).initXPositions();
                graph.getLayer(i).updateNodeIndices();
            }
        }
    }

    static class AssignXCoordinates {

        private static List<ArrayList<Integer>> space;
        private static List<ArrayList<LayoutNode>> downProcessingOrder;
        private static List<ArrayList<LayoutNode>> upProcessingOrder;

        private static final Comparator<LayoutNode> nodeProcessingDownComparator = (n1, n2) -> {
            if (n1.isDummy()) {
                if (n2.isDummy()) {
                    return 0;
                }
                return -1;
            }
            if (n2.isDummy()) {
                return 1;
            }
            return n1.getInDegree() - n2.getInDegree();
        };

        private static final Comparator<LayoutNode> nodeProcessingUpComparator = (n1, n2) -> {
            if (n1.isDummy()) {
                if (n2.isDummy()) {
                    return 0;
                }
                return -1;
            }
            if (n2.isDummy()) {
                return 1;
            }
            return n1.getOutDegree() - n2.getOutDegree();
        };

        public static void apply(LayoutGraph graph) {
            space = new ArrayList<>(graph.getLayerCount());
            downProcessingOrder = new ArrayList<>(graph.getLayerCount());
            upProcessingOrder = new ArrayList<>(graph.getLayerCount());

            for (int i = 0; i < graph.getLayerCount(); i++) {
                // Add a new empty list for each layer
                space.add(new ArrayList<>());
                downProcessingOrder.add(new ArrayList<>());
                upProcessingOrder.add(new ArrayList<>());

                int curX = 0;
                for (LayoutNode n : graph.getLayer(i)) {
                    // Add the current position to space and increment curX
                    space.get(i).add(curX);
                    curX += n.getOuterWidth() + NODE_OFFSET;

                    // Add the current node to processing orders
                    downProcessingOrder.get(i).add(n);
                    upProcessingOrder.get(i).add(n);
                }

                // Sort the processing orders
                downProcessingOrder.get(i).sort(nodeProcessingDownComparator);
                upProcessingOrder.get(i).sort(nodeProcessingUpComparator);
            }

            for (LayoutNode n : graph.getLayoutNodes()) {
                n.setX(space.get(n.getLayer()).get(n.getPos()));
            }

            for (LayoutNode n : graph.getDummyNodes()) {
                n.setX(space.get(n.getLayer()).get(n.getPos()));
            }

            for (int i = 0; i < SWEEP_ITERATIONS; i++) {
                sweepDown(graph);
                adjustSpace(graph);
                sweepUp(graph);
                adjustSpace(graph);
            }

            sweepDown(graph);
            adjustSpace(graph);
            sweepUp(graph);
        }

        private static void adjustSpace(LayoutGraph graph) {
            for (int i = 0; i < graph.getLayerCount(); i++) {
                for (LayoutNode n : graph.getLayer(i)) {
                    space.get(i).add(n.getX());
                }
            }
        }

        private static void sweepUp(LayoutGraph graph) {
            for (int i = graph.getLayerCount() - 1; i >= 0; i--) {
                NodeRow r = new NodeRow(space.get(i));
                for (LayoutNode n : upProcessingOrder.get(i)) {
                    int optimal = n.calculateOptimalXFromSuccessors(true);
                    r.insert(n, optimal);
                }
            }
        }

        private static void sweepDown(LayoutGraph graph) {
            for (int i = 1; i < graph.getLayerCount(); i++) {
                NodeRow r = new NodeRow(space.get(i));
                for (LayoutNode n : downProcessingOrder.get(i)) {
                    int optimal = n.calculateOptimalXFromPredecessors(true);
                    r.insert(n, optimal);
                }
            }
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
            }

            treeSet.add(n);
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
                    linkPoints.add(new Point(predEdge.getEndX(), predEdge.getEndY()));
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
                    linkPoints.add(new Point(curEdge.getStartX(), curEdge.getStartY()));

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

            if (graph.showSelfEdges()) {
                for (LayoutNode layoutNode : graph.getLayoutNodes()) {
                    if (layoutNode.hasSelfEdge()) {
                        LayoutEdge selfEdge = layoutNode.getSelfEdge();
                        ArrayList<Point> points = layoutNode.getSelfEdgePoints();
                        for (Point point : points) {
                            point.setLocation(point.getX() + layoutNode.getLeft(), point.getY() + layoutNode.getTop());
                        }
                        linkPositions.put(selfEdge.getLink(), points);
                    }
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
