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

import static com.sun.hotspot.igv.hierarchicallayout.LayoutManager.NODE_OFFSET;

/**
 * Represents a node in a hierarchical graph layout.
 * A LayoutNode can be either an actual vertex from the graph or a dummy node inserted during the layout process.
 * It stores layout-related properties such as position, size, margins, and connections to predecessor and successor nodes.
 */
public class LayoutNode {

    // Comparator constants for sorting LayoutNodes in various ways
    public static final Comparator<LayoutNode> NODE_PRIORITY = Comparator.comparingInt(LayoutNode::getPriority).reversed();
    public static final Comparator<LayoutNode> LAYOUT_NODE_DEGREE_COMPARATOR = Comparator.comparingInt(LayoutNode::getDegree);
    public static final Comparator<LayoutNode> NODE_POS_COMPARATOR = Comparator.comparingInt(LayoutNode::getPos);
    public static final Comparator<LayoutNode> NODE_X_COMPARATOR = Comparator.comparingInt(LayoutNode::getX);
    public static final Comparator<LayoutNode> DUMMY_NODES_FIRST = Comparator.comparing(LayoutNode::isDummy).reversed();
    public static final Comparator<LayoutNode> DUMMY_NODES_LAST = Comparator.comparing(LayoutNode::isDummy);
    public static final Comparator<LayoutNode> NODE_PROCESSING_DOWN_COMPARATOR = DUMMY_NODES_FIRST.thenComparingInt(LayoutNode::getInDegree);
    public static final Comparator<LayoutNode> NODE_PROCESSING_UP_COMPARATOR = DUMMY_NODES_FIRST.thenComparing(LayoutNode::getOutDegree);
    public static final Comparator<LayoutNode> DUMMY_NODES_THEN_OPTIMAL_X = DUMMY_NODES_FIRST.thenComparing(LayoutNode::getOptimalX);
    public static final Comparator<LayoutNode> NODES_OPTIMAL_X = Comparator.comparingInt(LayoutNode::getOptimalX);


    // Default dimensions for dummy nodes
    public static final int DUMMY_HEIGHT = 1;
    public static final int DUMMY_WIDTH = 1;

    // Layout properties
    private int layer = -1;
    private int optimal_x;
    private int x;
    private int y;
    private int width;
    private int height;
    private int topMargin;
    private int bottomMargin;
    private int rightMargin;
    private int leftMargin;

    private final Vertex vertex; // Associated graph vertex; null for dummy nodes

    private final List<LayoutEdge> preds = new ArrayList<>(); // Incoming edges
    private final List<LayoutEdge> succs = new ArrayList<>(); // Outgoing edges
    private final HashMap<Link, List<Point>> reversedLinkStartPoints = new HashMap<>(); // Start points of reversed edges
    private final HashMap<Link, List<Point>> reversedLinkEndPoints = new HashMap<>();   // End points of reversed edges
    private int pos = -1; // Position within its layer


    /**
     * Constructs a LayoutNode associated with the given Vertex.
     * Initializes the node's size based on the vertex's dimensions.
     *
     * @param v The Vertex associated with this LayoutNode. If null, the node is a dummy node.
     */
    public LayoutNode(Vertex v) {
        vertex = v;
        initSize();
    }

    /**
     * Constructs a dummy LayoutNode
     */
    public LayoutNode() {
        this(null);
    }

    /**
     * Initializes the size and margins of the node.
     * If the node represents a real vertex, it uses the vertex's size.
     * Dummy nodes use default dimensions.
     */
    public void initSize() {
        if (vertex == null) {
            height = DUMMY_HEIGHT;
            width = DUMMY_WIDTH;
        } else {
            Dimension size = vertex.getSize();
            height = size.height;
            width = size.width;
        }
        setTopMargin(0);
        setBottomMargin(0);
        setLeftMargin(0);
        setRightMargin(0);
    }

    public int getPriority() {
        if (vertex == null) {
            return 0;
        } else {
            return vertex.getPriority();
        }
    }

    /**
     * Calculates the optimal x-coordinate based on the positions of predecessor nodes.
     * Useful when layering nodes from top to bottom.
     *
     * @return The calculated optimal x-coordinate.
     */
    public int calculateOptimalXFromPredecessors() {
        int numPreds = preds.size();
        if (numPreds == 0) {
            return getX();
        }

        List<Integer> positions = new ArrayList<>(numPreds);
        for (LayoutEdge edge : preds) {
            positions.add(edge.getStartX() - edge.getRelativeToX());
        }

        Collections.sort(positions);
        int midIndex = numPreds / 2;
        return (numPreds % 2 == 0)
                ? (positions.get(midIndex - 1) + positions.get(midIndex)) / 2
                : positions.get(midIndex);
    }

    /**
     * Calculates the optimal x-coordinate based on the positions of successor nodes.
     * Useful when layering nodes from bottom to top.
     *
     * @return The calculated optimal x-coordinate.
     */
    public int calculateOptimalXFromSuccessors() {
        int numSuccs = succs.size();
        if (numSuccs == 0) {
            return getX();
        }

        List<Integer> positions = new ArrayList<>(numSuccs);
        for (LayoutEdge edge : succs) {
            positions.add(edge.getEndX() - edge.getRelativeFromX());
        }

        Collections.sort(positions);
        int midIndex = numSuccs / 2;
        return (numSuccs % 2 == 0)
                ? (positions.get(midIndex - 1) + positions.get(midIndex)) / 2
                : positions.get(midIndex);
    }

    /**
     * Calculates the node's out-degree (number of outgoing edges).
     *
     * @return The out-degree of the node.
     */
    public int getOutDegree() {
        return succs.size();
    }

    /**
     * Calculates the node's in-degree (number of incoming edges).
     *
     * @return The in-degree of the node.
     */
    public int getInDegree() {
        return preds.size();
    }

    /**
     * Calculates the total degree of the node (sum of in-degree and out-degree).
     *
     * @return The total degree of the node.
     */
    public int getDegree() {
        return preds.size() + succs.size();
    }

    /**
     * Enum to specify the type of neighbors to consider when computing the barycenter.
     */
    public enum NeighborType {
        PREDECESSORS,
        SUCCESSORS,
        BOTH
    }

    public int getPredecessorMedian() {
        if (hasPredecessors()) {
            int size = getInDegree();
            int[] values = new int[size];
            for (int j = 0; j < getInDegree(); j++) {
                values[j] = getPredecessorEdge(j).getFromX() - getPredecessorEdge(j).getRelativeToX();
            }
            Arrays.sort(values);
            if (values.length % 2 == 0) {
                return (values[size / 2 - 1] + values[size / 2]) / 2;
            } else {
                return values[size / 2];
            }
        } else {
            return getX();
        }
    }
    /**
     * Computes the barycenter (average x-coordinate) of this node based on its neighboring nodes.
     * The calculation can include predecessors, successors, or both, depending on the specified
     * neighbor type. Optionally, the positions can be weighted by the degree (number of connections)
     * of each neighboring node.
     *
     * @param neighborType Specifies which neighbors to include in the calculation:
     *                     - PREDECESSORS: Include only predecessor nodes.
     *                     - SUCCESSORS: Include only successor nodes.
     *                     - BOTH: Include both predecessors and successors.
     * @param weighted     If true, weights each neighbor's x-coordinate by its degree;
     *                     if false, all neighbors are weighted equally (weight of 1).
     * @return The computed barycenter x-coordinate. Returns 0 if there are no neighbors.
     */
    public int computeBarycenterX(NeighborType neighborType, boolean weighted) {
        int totalWeightedPosition = 0;
        int totalWeight = 0;

        // Include predecessors if specified
        if (neighborType == NeighborType.PREDECESSORS || neighborType == NeighborType.BOTH) {
            for (LayoutEdge predEdge : preds) {
                LayoutNode predNode = predEdge.getFrom();
                int weight = weighted ? predNode.getDegree() : 1;
                totalWeightedPosition += weight * predEdge.getStartX();
                totalWeight += weight;
            }
        }

        // Include successors if specified
        if (neighborType == NeighborType.SUCCESSORS || neighborType == NeighborType.BOTH) {
            for (LayoutEdge succEdge : succs) {
                LayoutNode succNode = succEdge.getTo();
                int weight = weighted ? succNode.getDegree() : 1;
                totalWeightedPosition += weight * succEdge.getEndX();
                totalWeight += weight;
            }
        }

        // Calculate the (weighted) average position for the node based on neighbor positions and weights (degree)
        return totalWeight > 0 ? totalWeightedPosition / totalWeight : 0;
    }

    /**
     * Gets the left boundary (excluding left margin) of the node.
     *
     * @return The x-coordinate of the left boundary.
     */
    public int getLeft() {
        return x + leftMargin;
    }

    /**
     * Gets the outer left boundary (including left margin) of the node.
     *
     * @return The x-coordinate of the outer left boundary.
     */
    public int getOuterLeft() {
        return x;
    }

    /**
     * Gets the total width of the node, including left and right margins.
     *
     * @return The total outer width.
     */
    public int getOuterWidth() {
        return leftMargin + width + rightMargin;
    }

    /**
     * Gets the total height of the node, including top and bottom margins.
     *
     * @return The total outer height.
     */
    public int getOuterHeight() {
        return topMargin + height + bottomMargin;
    }

    /**
     * Gets the right boundary (excluding right margin) of the node.
     *
     * @return The x-coordinate of the right boundary.
     */
    public int getRight() {
        return x + leftMargin + width;
    }

    /**
     * Gets the outer right boundary (including right margin) of the node.
     *
     * @return The x-coordinate of the outer right boundary.
     */
    public int getOuterRight() {
        return x + leftMargin + width + rightMargin;
    }

    /**
     * Gets the horizontal center point of the node.
     *
     * @return The x-coordinate of the center.
     */
    public int getCenterX() {
        return x + leftMargin + (width / 2);
    }

    /**
     * Gets the top boundary (excluding top margin) of the node.
     *
     * @return The y-coordinate of the top boundary.
     */
    public int getTop() {
        return y + topMargin;
    }

    /**
     * Gets the bottom boundary (excluding bottom margin) of the node.
     *
     * @return The y-coordinate of the bottom boundary.
     */
    public int getBottom() {
        return y + topMargin + height;
    }

    /**
     * Checks if the node is a dummy node.
     *
     * @return True if the node is a dummy node; false otherwise.
     */
    public boolean isDummy() {
        return vertex == null;
    }

    @Override
    public String toString() {
        if (vertex != null) {
            return vertex.toString();
        } else {
            return "dummy";
        }
    }

    public int getOptimalX() {
        return optimal_x;
    }

    public void setOptimalX(int optimal_x) {
        this.optimal_x = optimal_x;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    public int getLeftMargin() {
        return leftMargin;
    }

    public void setLeftMargin(int leftMargin) {
        this.leftMargin = leftMargin;
    }

    public int getTopMargin() {
        return topMargin;
    }

    public void setTopMargin(int topMargin) {
        this.topMargin = topMargin;
    }

    public int getRightMargin() {
        return rightMargin;
    }



    public List<Integer> getAdjacentX(NeighborType neighborType) {
        List<Integer> adjacentX = new ArrayList<>();
        if (neighborType == NeighborType.PREDECESSORS || neighborType == NeighborType.BOTH) {
            for (LayoutEdge predEdge : preds) {
                adjacentX.add(predEdge.getFromX());
            }
        }
        if (neighborType == NeighborType.SUCCESSORS || neighborType == NeighborType.BOTH) {
            for (LayoutEdge succEdge : succs) {
                adjacentX.add(succEdge.getToX());
            }
        }
        return adjacentX;
    }

    public void setRightMargin(int rightMargin) {
        this.rightMargin = rightMargin;
    }

    public int getBottomMargin() {
        return bottomMargin;
    }

    public void setBottomMargin(int bottomMargin) {
        this.bottomMargin = bottomMargin;
    }

    public Vertex getVertex() {
        return vertex;
    }

    public LayoutEdge getPredecessorEdge(int i) {
        return preds.get(i);
    }

    public LayoutEdge getSuccessorEdge(int i) {
        return succs.get(i);
    }

    public List<LayoutEdge> getPredecessors() {
        return preds;
    }

    public boolean hasPredecessors() {
        return !preds.isEmpty();
    }

    public boolean hasSuccessors() {
        return !succs.isEmpty();
    }

    /**
     * Determines if the node has neighbors of the specified type.
     *
     * @param neighborType the type of neighbors to check for (PREDECESSORS, SUCCESSORS, or BOTH)
     * @return {@code true} if the node has neighbors of the specified type; {@code false} otherwise
     */
    public boolean hasNeighborsOfType(NeighborType neighborType) {
        if (neighborType.equals(NeighborType.PREDECESSORS)) {
            return hasPredecessors();
        } else if (neighborType.equals(NeighborType.SUCCESSORS)) {
            return hasSuccessors();
        } else if (neighborType.equals(NeighborType.BOTH)) {
            return hasPredecessors() || hasSuccessors();
        } else {
            return false;
        }
    }

    public List<LayoutEdge> getSuccessors() {
        return succs;
    }

    public HashMap<Link, List<Point>> getReversedLinkStartPoints() {
        return reversedLinkStartPoints;
    }

    public HashMap<Link, List<Point>> getReversedLinkEndPoints() {
        return reversedLinkEndPoints;
    }

    public int getPos() {
        return pos;
    }

    public void setPos(int pos) {
        this.pos = pos;
    }


    /**
     * Groups the successor edges by their relative x-coordinate from the current node.
     *
     * @return A map of relative x-coordinate to list of successor edges.
     */
    public Map<Integer, List<LayoutEdge>> groupSuccessorsByX() {
        Map<Integer, List<LayoutEdge>> result = new HashMap<>();
        for (LayoutEdge succEdge : succs) {
            result.computeIfAbsent(succEdge.getRelativeFromX(), k -> new ArrayList<>()).add(succEdge);
        }
        return result;
    }

    /**
     * Computes the start points for reversed outgoing edges.
     * Adjusts the node's margins and records the necessary points for edge routing.
     */
    private boolean computeReversedEdgeStartPoints() {
        TreeMap<Integer, ArrayList<LayoutEdge>> sortedDownMap = new TreeMap<>(Collections.reverseOrder());
        for (LayoutEdge succEdge : getSuccessors()) {
            if (succEdge.isReversed()) {
                succEdge.setRelativeFromX(succEdge.getLink().getTo().getRelativePosition().x);
                sortedDownMap.putIfAbsent(succEdge.getRelativeFromX(), new ArrayList<>());
                sortedDownMap.get(succEdge.getRelativeFromX()).add(succEdge);
            }
        }

        int offset = NODE_OFFSET + LayoutNode.DUMMY_WIDTH;
        int currentX = getWidth();
        int startY = 0;
        int currentY = 0;
        for (Map.Entry<Integer, ArrayList<LayoutEdge>> entry : sortedDownMap.entrySet()) {
            int startX = entry.getKey();
            ArrayList<LayoutEdge> reversedSuccs = entry.getValue();

            currentX += offset;
            currentY -= offset;
            setTopMargin(getTopMargin() + offset);

            ArrayList<Point> startPoints = new ArrayList<>();
            startPoints.add(new Point(currentX, currentY));
            startPoints.add(new Point(startX, currentY));
            startPoints.add(new Point(startX, startY));
            for (LayoutEdge revEdge : reversedSuccs) {
                revEdge.setRelativeFromX(currentX);
                getReversedLinkStartPoints().put(revEdge.getLink(), startPoints);
            }
        }
        setLeftMargin(getLeftMargin());
        setRightMargin(getRightMargin() + (sortedDownMap.size() * offset));
        return !sortedDownMap.isEmpty();
    }

    /**
     * Computes the end points for reversed incoming edges.
     * Adjusts the node's margins and records the necessary points for edge routing.
     */
    private void computeReversedEdgeEndPoints(boolean reverseLeft) {
        TreeMap<Integer, ArrayList<LayoutEdge>> sortedUpMap = reverseLeft ? new TreeMap<>() : new TreeMap<>(Collections.reverseOrder());
        for (LayoutEdge predEdge : getPredecessors()) {
            if (predEdge.isReversed()) {
                predEdge.setRelativeToX(predEdge.getLink().getFrom().getRelativePosition().x);
                sortedUpMap.putIfAbsent(predEdge.getRelativeToX(), new ArrayList<>());
                sortedUpMap.get(predEdge.getRelativeToX()).add(predEdge);
            }
        }

        int offset = NODE_OFFSET + LayoutNode.DUMMY_WIDTH;
        int offsetX = reverseLeft ? -offset : offset;
        int currentX = reverseLeft ? 0 : getWidth();
        int startY = getHeight();
        int currentY = getHeight();
        for (Map.Entry<Integer, ArrayList<LayoutEdge>> entry : sortedUpMap.entrySet()) {
            int startX = entry.getKey();
            ArrayList<LayoutEdge> reversedPreds = entry.getValue();

            currentX += offsetX;
            currentY += offset;
            setBottomMargin(getBottomMargin() + offset);

            ArrayList<Point> endPoints = new ArrayList<>();
            endPoints.add(new Point(currentX, currentY));
            endPoints.add(new Point(startX, currentY));
            endPoints.add(new Point(startX, startY));
            for (LayoutEdge revEdge : reversedPreds) {
                revEdge.setRelativeToX(currentX);
                getReversedLinkEndPoints().put(revEdge.getLink(), endPoints);
            }
        }
        setLeftMargin(getLeftMargin() + (reverseLeft ? sortedUpMap.size() * offset : 0));
        setRightMargin(getRightMargin() + (reverseLeft ? 0 : sortedUpMap.size() * offset));

    }

    /**
     * Computes the reversed link points for both incoming and outgoing reversed edges.
     * Adjusts node margins to accommodate the routing of reversed edges.
     */
    public void computeReversedLinkPoints() {
        initSize();
        getReversedLinkStartPoints().clear();
        getReversedLinkEndPoints().clear();

        boolean hasReversedDown = computeReversedEdgeStartPoints();
        computeReversedEdgeEndPoints(hasReversedDown);
    }

    /**
     * Calculates the optimal x-coordinate based on both predecessors and successors.
     * Useful when balancing the node's position in the layer to minimize edge crossings.
     *
     * @return The calculated optimal x-coordinate.
     */
    public int calculateOptimalXFromNeighbors() {
        if (preds.isEmpty() && succs.isEmpty()) {
            return x;
        }

        int[] values = new int[preds.size() + succs.size()];
        int i = 0;

        for (LayoutEdge edge : preds) {
            values[i] = edge.getFromX() - edge.getRelativeToX();
            i++;
        }

        for (LayoutEdge edge : succs) {
            values[i] = edge.getToX() - edge.getRelativeFromX();
            i++;
        }

        Arrays.sort(values);
        int middle = values.length / 2;
        return (values.length % 2 == 0) ? (values[middle - 1] + values[middle]) / 2 : values[middle];
    }
}
