/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * The LayoutNode class represents a node in a hierarchical graph layout.
 * It can be either an actual node from the original graph or a temporary "dummy" node added during the layout process
 * to handle complex edge connections.
 * This class stores important layout information like the node's position (x and y coordinates),
 * size (width and height), layer level, and connections to other nodes through incoming and outgoing edges.
 * It provides methods to calculate optimal positions, manage margins, and handle reversed edges,
 * all aimed at arranging the nodes neatly in layers to create a clear and visually organized graph display.
 */
public class LayoutNode {

    // Comparator constants for sorting LayoutNodes in various ways
    public static final Comparator<LayoutNode> LAYOUT_NODE_DEGREE_COMPARATOR = Comparator.comparingInt(LayoutNode::getDegree);
    public static final Comparator<LayoutNode> NODE_POS_COMPARATOR = Comparator.comparingInt(LayoutNode::getPos);
    public static final Comparator<LayoutNode> NODE_X_COMPARATOR = Comparator.comparingInt(LayoutNode::getX);
    public static final Comparator<LayoutNode> NODE_CROSSING_COMPARATOR = Comparator.comparingInt(LayoutNode::getCrossingNumber);


    // Default dimensions for dummy nodes
    public static final int DUMMY_HEIGHT = 1;
    public static final int DUMMY_WIDTH = 1;
    public static final int REVERSE_EDGE_OFFSET = NODE_OFFSET + LayoutNode.DUMMY_WIDTH;
    private Vertex vertex; // Associated graph vertex; null for dummy nodes
    private final List<LayoutEdge> preds = new ArrayList<>(); // Incoming edges
    private final List<LayoutEdge> succs = new ArrayList<>(); // Outgoing edges
    private LayoutEdge selfEdge = null;
    private final HashMap<Link, List<Point>> reversedLinkStartPoints = new HashMap<>(); // Start points of reversed edges
    private final HashMap<Link, List<Point>> reversedLinkEndPoints = new HashMap<>();   // End points of reversed edges
    // Layout properties
    private int layer = -1;
    private int x;
    private int y;
    private int width;
    private int height;
    private int topMargin;
    private int bottomMargin;
    private int rightMargin;
    private int leftMargin;
    private int pos = -1; // Position within its layer
    private boolean reverseLeft = false;
    private int crossingNumber = 0;

    public boolean hasSelfEdge() {
        return selfEdge != null;
    }

    public void setSelfEdge(LayoutEdge selfEdge) {
        this.selfEdge = selfEdge;
        if (selfEdge != null) {
            topMargin += REVERSE_EDGE_OFFSET;
            bottomMargin += REVERSE_EDGE_OFFSET;
            rightMargin += REVERSE_EDGE_OFFSET;
        }
    }

    public LayoutEdge getSelfEdge() {
        return selfEdge;
    }

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
        if (hasSelfEdge()) {
            topMargin += REVERSE_EDGE_OFFSET;
            bottomMargin += REVERSE_EDGE_OFFSET;
            rightMargin += REVERSE_EDGE_OFFSET;
        }
    }

    public int getCrossingNumber() {
        return crossingNumber;
    }

    public void setCrossingNumber(int crossingNumber) {
        this.crossingNumber = crossingNumber;
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

    public int getHeight() {
        return height;
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

    public Vertex getVertex() {
        return vertex;
    }

    public void setVertex(Vertex vertex) {
        this.vertex = vertex;
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

    public List<LayoutEdge> getSuccessorsRaw() {
        return succs;
    }

    public List<LayoutEdge> getPredecessors() {
        return Collections.unmodifiableList(preds);
    }

    public List<LayoutEdge> getPredecessorsRaw() {
        return preds;
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

    private int getBackedgeCrossingScore() {
        int score = 0;
        for (LayoutEdge predEdge : preds) {
            if (predEdge.isReversed()) {
                List<Point> points = reversedLinkEndPoints.get(predEdge.getLink());
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
        for (LayoutEdge succEdge : succs) {
            if (succEdge.isReversed()) {
                List<Point> points = reversedLinkStartPoints.get(succEdge.getLink());
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

    private boolean computeReversedStartPoints(boolean left) {
        TreeMap<Integer, ArrayList<LayoutEdge>> sortedDownMap = left ? new TreeMap<>() : new TreeMap<>(Collections.reverseOrder());
        for (LayoutEdge succEdge : succs) {
            if (succEdge.isReversed()) {
                succEdge.setRelativeFromX(succEdge.getLink().getTo().getRelativePosition().x);
                sortedDownMap.putIfAbsent(succEdge.getRelativeFromX(), new ArrayList<>());
                sortedDownMap.get(succEdge.getRelativeFromX()).add(succEdge);
            }
        }

        int offset = REVERSE_EDGE_OFFSET;
        int offsetX = left ? -offset : offset;
        int currentX = left ? 0 : width;
        int startY = 0;
        int currentY = 0;
        for (Map.Entry<Integer, ArrayList<LayoutEdge>> entry : sortedDownMap.entrySet()) {
            int startX = entry.getKey();
            ArrayList<LayoutEdge> reversedSuccs = entry.getValue();

            currentX += offsetX;
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
        if (left) {
            leftMargin += sortedDownMap.size() * offset;
        } else {
            rightMargin += sortedDownMap.size() * offset;
        }
        return !sortedDownMap.isEmpty();
    }

    private boolean computeReversedEndPoints(boolean left) {
        TreeMap<Integer, ArrayList<LayoutEdge>> sortedUpMap = left ? new TreeMap<>() : new TreeMap<>(Collections.reverseOrder());
        for (LayoutEdge predEdge : preds) {
            if (predEdge.isReversed()) {
                predEdge.setRelativeToX(predEdge.getLink().getFrom().getRelativePosition().x);
                sortedUpMap.putIfAbsent(predEdge.getRelativeToX(), new ArrayList<>());
                sortedUpMap.get(predEdge.getRelativeToX()).add(predEdge);
            }
        }

        int offset = REVERSE_EDGE_OFFSET;
        int offsetX = left ? -offset : offset;
        int currentX = left ? 0 : getWidth();
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
        if (left) {
            leftMargin += sortedUpMap.size() * offset;
        } else {
            rightMargin += sortedUpMap.size() * offset;
        }

        return !sortedUpMap.isEmpty();
    }

    public void computeReversedLinkPoints(boolean reverseLeft) {
        this.reverseLeft = reverseLeft;

        initSize();
        reversedLinkStartPoints.clear();
        reversedLinkEndPoints.clear();

        boolean hasReversedDown = computeReversedStartPoints(reverseLeft);
        boolean hasReversedUP = computeReversedEndPoints(hasReversedDown != reverseLeft);
    }

    public boolean isReverseRight() {
        return !reverseLeft;
    }

    public void optimizeBackEdgeCrossing() {
        if (reversedLinkStartPoints.isEmpty() && reversedLinkEndPoints.isEmpty()) return;
        int orig_score = getBackedgeCrossingScore();
        computeReversedLinkPoints(isReverseRight());
        int reverse_score = getBackedgeCrossingScore();
        if (orig_score > reverse_score) {
            computeReversedLinkPoints(isReverseRight());
        }
    }

    public ArrayList<Point> getSelfEdgePoints() {
        ArrayList<Point> points = new ArrayList<>();

        Link selfEdgeLink = getSelfEdge().getLink();

        points.add(new Point(selfEdgeLink.getFrom().getRelativePosition().x,  selfEdgeLink.getFrom().getRelativePosition().y-REVERSE_EDGE_OFFSET));
        points.add(new Point(width + REVERSE_EDGE_OFFSET,  selfEdgeLink.getFrom().getRelativePosition().y-REVERSE_EDGE_OFFSET));
        points.add(new Point(width + REVERSE_EDGE_OFFSET, height + REVERSE_EDGE_OFFSET));
        points.add(new Point(selfEdgeLink.getTo().getRelativePosition().x,  height + REVERSE_EDGE_OFFSET));
        return points;
    }
}
