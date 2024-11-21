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

import static com.sun.hotspot.igv.hierarchicallayout.LayoutManager.NODE_OFFSET;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.Dimension;
import java.awt.Point;
import java.util.*;

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
    public static final Comparator<LayoutNode> NODES_OPTIMAL_X = Comparator.comparingInt(LayoutNode::getOptimalX);
    public static final Comparator<LayoutNode> NODES_OPTIMAL_DIFFERENCE = Comparator.comparingInt(LayoutNode::getOptimalDifference).reversed();

    // Default dimensions for dummy nodes
    public static final int DUMMY_HEIGHT = 1;
    public static final int DUMMY_WIDTH = 1;
    private final Vertex vertex; // Associated graph vertex; null for dummy nodes
    private final List<LayoutEdge> preds = new ArrayList<>(); // Incoming edges
    private final List<LayoutEdge> succs = new ArrayList<>(); // Outgoing edges
    private final HashMap<Link, List<Point>> reversedLinkStartPoints = new HashMap<>(); // Start points of reversed edges
    private final HashMap<Link, List<Point>> reversedLinkEndPoints = new HashMap<>();   // End points of reversed edges
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
        topMargin = 0;
        bottomMargin = 0;
        leftMargin = 0;
        rightMargin = 0;
    }

    public int getPriority() {
        if (vertex == null) {
            return 0;
        } else {
            return vertex.getPriority();
        }
    }

    public int calculateOptimalXFromPredecessors(boolean useMedian) {
        int numPreds = preds.size();

        // If there are no predecessors, retain the current x position
        if (numPreds == 0) {
            return getX();
        }

        // Collect the x positions from all predecessor edges
        List<Integer> positions = new ArrayList<>(numPreds);
        for (LayoutEdge edge : preds) {
            positions.add(edge.getStartX() - edge.getRelativeToX());
        }

        if (useMedian) {
            // Calculate the median position
            Collections.sort(positions);
            int midIndex = numPreds / 2;

            if (numPreds % 2 == 0) {
                // Even number of predecessors: average the two middle values
                return (positions.get(midIndex - 1) + positions.get(midIndex)) / 2;
            } else {
                // Odd number of predecessors: take the middle value
                return positions.get(midIndex);
            }
        } else {
            // Calculate the average position
            long sum = 0;
            for (int pos : positions) {
                sum += pos;
            }
            // Integer division is used; adjust as needed for rounding
            return (int) (sum / numPreds);
        }
    }


    public int calculateOptimalXFromSuccessors(boolean useMedian) {
        int numSuccs = succs.size();

        // If there are no successors, retain the current x position
        if (numSuccs == 0) {
            return getX();
        }

        // Collect the x positions from all successor edges
        List<Integer> positions = new ArrayList<>(numSuccs);
        for (LayoutEdge edge : succs) {
            positions.add(edge.getEndX() - edge.getRelativeFromX());
        }

        if (useMedian) {
            // Calculate the median position
            Collections.sort(positions);
            int midIndex = numSuccs / 2;

            if (numSuccs % 2 == 0) {
                // Even number of successors: average the two middle values
                return (positions.get(midIndex - 1) + positions.get(midIndex)) / 2;
            } else {
                // Odd number of successors: take the middle value
                return positions.get(midIndex);
            }
        } else {
            // Calculate the average position
            long sum = 0;
            for (int pos : positions) {
                sum += pos;
            }
            // Integer division is used; adjust as needed for rounding
            return (int) (sum / numSuccs);
        }
    }

    public int calculateOptimalXFromNeighbors() {
        int num = succs.size() + preds.size();
        if (num == 0) {
            return getX();
        }

        List<Integer> positions = new ArrayList<>(num);
        for (LayoutEdge edge : succs) {
            positions.add(edge.getEndX() - edge.getRelativeFromX());
        }
        for (LayoutEdge edge : preds) {
            positions.add(edge.getEndX() - edge.getRelativeFromX());
        }

        int sum = 0;
        for (int pos : positions) {
            sum += pos;
        }
        return sum / num;

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

    /**
     * Calculates the absolute difference between the optimal X position and the current X position.
     *
     * @return The absolute difference as an integer.
     */
    public int getOptimalDifference() {
        return Math.abs(getOptimalX() - getX());
    }

    public void shiftX(int shift) {
        this.x += shift;
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

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    /**
     * Centers the node by setting equal top and bottom margins.
     * The larger of the two margins is applied to both.
     */
    public void centerNode() {
        int offset = Math.max(topMargin, bottomMargin);
        topMargin = offset;
        bottomMargin = offset;
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

    public Vertex getVertex() {
        return vertex;
    }

    public boolean hasPredecessors() {
        return !preds.isEmpty();
    }

    public boolean hasSuccessors() {
        return !succs.isEmpty();
    }

    public void clearSuccessors() {
        succs.clear();
    }

    public void clearPredecessors() {
        preds.clear();
    }

    public List<LayoutEdge> getSuccessors() {
        return Collections.unmodifiableList(succs);
    }

    public List<LayoutEdge> getPredecessors() {
        return Collections.unmodifiableList(preds);
    }

    public void addSuccessor(LayoutEdge successor) {
        succs.add(successor);
    }

    public void removeSuccessor(LayoutEdge successor) {
        succs.remove(successor);
    }

    public void addPredecessor(LayoutEdge predecessor) {
        preds.add(predecessor);
    }

    public void removePredecessor(LayoutEdge predecessor) {
        preds.remove(predecessor);
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

    public Map<Link, List<Point>> getReversedLinkStartPoints() {
        return Collections.unmodifiableMap(reversedLinkStartPoints);
    }

    public Map<Link, List<Point>> getReversedLinkEndPoints() {
        return Collections.unmodifiableMap(reversedLinkEndPoints);
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
        reversedLinkStartPoints.clear();
        TreeMap<Integer, ArrayList<LayoutEdge>> sortedDownMap = new TreeMap<>(Collections.reverseOrder());
        for (LayoutEdge succEdge : getSuccessors()) {
            if (succEdge.isReversed()) {
                succEdge.setRelativeFromX(succEdge.getLink().getTo().getRelativePosition().x);
                sortedDownMap.putIfAbsent(succEdge.getRelativeFromX(), new ArrayList<>());
                sortedDownMap.get(succEdge.getRelativeFromX()).add(succEdge);
            }
        }

        int offset = NODE_OFFSET + LayoutNode.DUMMY_WIDTH;
        int currentX = width;
        int startY = 0;
        int currentY = 0;
        for (Map.Entry<Integer, ArrayList<LayoutEdge>> entry : sortedDownMap.entrySet()) {
            int startX = entry.getKey();
            ArrayList<LayoutEdge> reversedSuccs = entry.getValue();

            currentX += offset;
            currentY -= offset;
            topMargin += offset;

            ArrayList<Point> startPoints = new ArrayList<>();
            startPoints.add(new Point(currentX, currentY));
            startPoints.add(new Point(startX, currentY));
            startPoints.add(new Point(startX, startY));
            for (LayoutEdge revEdge : reversedSuccs) {
                revEdge.setRelativeFromX(currentX);
                reversedLinkStartPoints.put(revEdge.getLink(), startPoints);
            }
        }
        rightMargin += sortedDownMap.size() * offset;
        return !sortedDownMap.isEmpty();
    }

    /**
     * Computes the end points for reversed incoming edges.
     * Adjusts the node's margins and records the necessary points for edge routing.
     */
    private void computeReversedEdgeEndPoints(boolean reverseLeft) {
        reversedLinkEndPoints.clear();
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
        int startY = height;
        int currentY = height;
        for (Map.Entry<Integer, ArrayList<LayoutEdge>> entry : sortedUpMap.entrySet()) {
            int startX = entry.getKey();
            ArrayList<LayoutEdge> reversedPreds = entry.getValue();

            currentX += offsetX;
            currentY += offset;
            bottomMargin += offset;

            ArrayList<Point> endPoints = new ArrayList<>();
            endPoints.add(new Point(currentX, currentY));
            endPoints.add(new Point(startX, currentY));
            endPoints.add(new Point(startX, startY));
            for (LayoutEdge revEdge : reversedPreds) {
                revEdge.setRelativeToX(currentX);
                reversedLinkEndPoints.put(revEdge.getLink(), endPoints);
            }
        }
        if (reverseLeft) {
            leftMargin += sortedUpMap.size() * offset;
        } else {
            rightMargin += sortedUpMap.size() * offset;
        }
    }

    /**
     * Computes the reversed link points for both incoming and outgoing reversed edges.
     * Adjusts node margins to accommodate the routing of reversed edges.
     */
    public void computeReversedLinkPoints() {
        initSize();
        boolean hasReversedDown = computeReversedEdgeStartPoints();
        computeReversedEdgeEndPoints(hasReversedDown);
    }

    /**
     * Enum to specify the type of neighbors to consider when computing the barycenter.
     */
    public enum NeighborType {
        PREDECESSORS,
        SUCCESSORS,
        BOTH
    }
}
