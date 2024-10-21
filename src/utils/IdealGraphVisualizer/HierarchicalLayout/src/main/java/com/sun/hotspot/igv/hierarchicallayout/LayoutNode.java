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

public class LayoutNode {

    public static final Comparator<LayoutNode> LAYOUT_NODE_DEGREE_COMPARATOR = Comparator.comparingInt(LayoutNode::getDegree);
    public static final Comparator<LayoutNode> LAYOUT_NODE_PRIORITY_COMPARATOR = Comparator.comparingInt(LayoutNode::getVertexPriority);
    public static final Comparator<LayoutNode> NODE_POS_COMPARATOR = Comparator.comparingInt(LayoutNode::getPos);
    public static final Comparator<LayoutNode> NODE_X_COMPARATOR = Comparator.comparingInt(LayoutNode::getX);
    public static final Comparator<LayoutNode> ROOTS_FIRST_COMPARATOR = Comparator.comparing(LayoutNode::isRoot);
    public static final Comparator<LayoutNode> ROOTS_FIRST_VERTEX_COMPARATOR = ROOTS_FIRST_COMPARATOR.thenComparing(LayoutNode::getVertex);
    public static final Comparator<LayoutNode> CROSSING_NODE_COMPARATOR = Comparator.comparingDouble(LayoutNode::getWeightedPosition);
    public static final Comparator<LayoutNode> DUMMY_NODES_FIRST = Comparator.comparing(LayoutNode::isDummy).reversed();
    public static final Comparator<LayoutNode> NODE_PROCESSING_DOWN_COMPARATOR = DUMMY_NODES_FIRST.thenComparingInt(LayoutNode::getOutDegree);
    public static final Comparator<LayoutNode> NODE_PROCESSING_UP_COMPARATOR = DUMMY_NODES_FIRST.thenComparing(LayoutNode::getInDegree);
    public static final Comparator<LayoutNode> DUMMY_NODES_THEN_OPTIMAL_X = DUMMY_NODES_FIRST.thenComparing(LayoutNode::getOptimalX);

    public static final int DUMMY_HEIGHT = 1;
    public static final int DUMMY_WIDTH = 1;

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

    // TODO make final
    private Vertex vertex; // Only used for non-dummy nodes, otherwise null

    private final List<LayoutEdge> preds = new ArrayList<>();
    private final List<LayoutEdge> succs = new ArrayList<>();
    private final HashMap<Integer, Integer> outOffsets = new HashMap<>(); // deprecated
    private final HashMap<Integer, Integer> inOffsets = new HashMap<>(); // deprecated
    private final HashMap<Link, List<Point>> reversedLinkStartPoints = new HashMap<>();
    private final HashMap<Link, List<Point>> reversedLinkEndPoints = new HashMap<>();
    private int pos = -1; // Position within layer

    private float weightedPosition = 0;
    private boolean reverseLeft = false;

    public LayoutNode(Vertex v) {
        vertex = v;
        if (v == null) {
            height = DUMMY_HEIGHT;
            width = DUMMY_WIDTH;
        } else {
            Dimension size = v.getSize();
            height = size.height;
            width = size.width;
        }
    }

    public LayoutNode() {
        this(null);
    }

    public int getOutDegree() {
        return succs.size();
    }

    public int getInDegree() {
        return preds.size();
    }

    public int getDegree() {
        return preds.size() + succs.size();
    }

    public float averagePosition(boolean weightedDegree) {
        float totalWeightedPosition = 0;
        float totalWeight = 0;

        for (LayoutEdge predEdge : preds) {
            LayoutNode predNode = predEdge.getFrom();
            int weight = weightedDegree ? predNode.getDegree() : 1;
            totalWeightedPosition += weight * predEdge.getStartX();
            totalWeight += weight;
        }
        for (LayoutEdge succEdge : succs) {
            LayoutNode succNode = succEdge.getTo();
            int weight = weightedDegree ? succNode.getDegree() : 1;
            totalWeightedPosition += weight * succEdge.getEndX();
            totalWeight += weight;
        }

        // Calculate the (weighted) average position for the node based on neighbor positions and weights (degree)
        return totalWeight > 0 ? totalWeightedPosition / totalWeight : 0;
    }

    public int getLeft() {
        return x + leftMargin;
    }

    public int getOuterLeft() {
        return x;
    }

    public int getOuterWidth() {
        return leftMargin + width + rightMargin;
    }

    public int getOuterHeight() {
        return topMargin + height + bottomMargin;
    }

    public int getRight() {
        return x + leftMargin + width;
    }

    public int getOuterRight() {
        return x + leftMargin + width + rightMargin;
    }

    public int getCenterX() {
        return x + leftMargin + (width / 2);
    }

    public int getTop() {
        return y + topMargin;
    }

    public int getBottom() {
        return y + topMargin + height;
    }

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

    public int getVertexPriority() {
        return vertex.getPriority();
    }

    public void setVertex(Vertex vertex) {
        this.vertex = vertex;
    }

    public List<LayoutEdge> getPreds() {
        return preds;
    }

    public boolean isRoot() {
        return preds.isEmpty();
    }

    public boolean hasPreds() {
        return !preds.isEmpty();
    }

    public boolean hasSuccs() {
        return !succs.isEmpty();
    }

    public List<LayoutEdge> getSuccs() {
        return succs;
    }

    public HashMap<Integer, Integer> getOutOffsets() {
        return outOffsets;
    }

    public HashMap<Integer, Integer> getInOffsets() {
        return inOffsets;
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

    public float getWeightedPosition() {
        return weightedPosition;
    }

    public void setWeightedPosition(float weightedPosition) {
        this.weightedPosition = weightedPosition;
    }

    public boolean isReverseLeft() {
        return reverseLeft;
    }

    public void setReverseLeft(boolean reverseLeft) {
        this.reverseLeft = reverseLeft;
    }
}
