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

import static com.sun.hotspot.igv.hierarchicallayout.LayoutManager.*;
import static com.sun.hotspot.igv.hierarchicallayout.LayoutNode.NODES_OPTIMAL_X;
import static com.sun.hotspot.igv.hierarchicallayout.LayoutNode.NODE_X_COMPARATOR;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Represents a layer in a hierarchical graph layout.
 * Each LayoutLayer contains a collection of LayoutNodes positioned at the same vertical level.
 * Provides methods to manage the nodes within the layer, including positioning, sorting,
 * and adjusting the layout to minimize overlaps and improve visual clarity.
 */
public class LayoutLayer extends ArrayList<LayoutNode> {

    private int height = 0;
    private int y = 0;

    /**
     * Counts the number of edge crossings between the neighbors of two nodes.
     * <p>
     * This method compares the positions of adjacent nodes for the given neighbor type
     * (predecessors or successors) to determine the number of crossing edges between them.
     *
     * @param node1        the first layout node
     * @param node2        the second layout node
     * @param neighborType the type of neighbors to consider (predecessors or successors)
     * @return the number of crossings between the neighbors of the two nodes
     */
    public static int calculateEdgeCrossings(LayoutNode node1, LayoutNode node2, LayoutNode.NeighborType neighborType) {
        int crossings = 0;
        for (int neighborX1 : node1.getAdjacentX(neighborType)) {
            for (int neighborX2 : node2.getAdjacentX(neighborType)) {
                if (neighborX1 > neighborX2) {
                    crossings++;
                }
            }
        }
        return crossings;
    }

    /**
     * Adds all LayoutNodes from the specified collection to this layer.
     * Updates the layer's height based on the nodes added.
     *
     * @param c The collection of LayoutNodes to be added.
     * @return true if this layer changed as a result of the call.
     */
    @Override
    public boolean addAll(Collection<? extends LayoutNode> c) {
        c.forEach(this::updateLayerHeight);
        return super.addAll(c);
    }

    /**
     * Adds a single LayoutNode to this layer.
     * Updates the layer's height based on the node added.
     *
     * @param n The LayoutNode to be added.
     * @return true if the node was added successfully.
     */
    @Override
    public boolean add(LayoutNode n) {
        updateLayerHeight(n);
        return super.add(n);
    }

    /**
     * Updates the layer's height if the outer height of the given node exceeds the current height.
     *
     * @param n The LayoutNode whose height is to be considered.
     */
    private void updateLayerHeight(LayoutNode n) {
        height = Math.max(height, n.getOuterHeight());
    }

    /**
     * Calculates and returns the maximum height among the nodes in this layer, including their margins.
     * Adjusts the top and bottom margins of non-dummy nodes to be equal, effectively centering them vertically.
     *
     * @return The maximum outer height of nodes in this layer.
     */
    public int calculateMaxLayerHeight() {
        int maxLayerHeight = 0;
        for (LayoutNode layoutNode : this) {
            if (!layoutNode.isDummy()) {
                // Center the node by setting equal top and bottom margins
                layoutNode.centerNode();
            }
            maxLayerHeight = Math.max(maxLayerHeight, layoutNode.getOuterHeight());
        }
        return maxLayerHeight;
    }

    /**
     * Calculates and returns the total height of this layer, including additional padding
     * based on the maximum horizontal offset among the edges of its nodes.
     * This padding helps in scaling the layer vertically to accommodate edge bends and crossings.
     *
     * @return The total padded height of the layer.
     */
    public int calculatePaddedHeight() {
        int maxXOffset = 0;

        for (LayoutNode layoutNode : this) {
            for (LayoutEdge succEdge : layoutNode.getSuccessors()) {
                maxXOffset = Math.max(Math.abs(succEdge.getStartX() - succEdge.getEndX()), maxXOffset);
            }
        }

        int scalePaddedBottom = this.getHeight();
        scalePaddedBottom += (int) (SCALE_LAYER_PADDING * Math.max((int) (Math.sqrt(maxXOffset) * 2), LAYER_OFFSET * 3));
        return scalePaddedBottom;
    }

    /**
     * Centers all nodes in this layer vertically within the layer's assigned space.
     * Adjusts each node's Y-coordinate so that it is centered based on the layer's top and height.
     */
    public void centerNodesVertically() {
        for (LayoutNode layoutNode : this) {
            int centeredY = getTop() + (getHeight() - layoutNode.getOuterHeight()) / 2;
            layoutNode.setY(centeredY);
        }
    }

    /**
     * Shifts the top Y-coordinate of this layer by the specified amount.
     * Useful for moving the entire layer up or down.
     *
     * @param shift The amount to shift the layer's top position. Positive values move it down.
     */
    public void moveLayerVertically(int shift) {
        y += shift;
    }

    /**
     * Gets the top Y-coordinate of this layer.
     *
     * @return The Y-coordinate representing the top of the layer.
     */
    public int getTop() {
        return y;
    }

    /**
     * Sets the top Y-coordinate of this layer.
     *
     * @param top The Y-coordinate representing the top of the layer.
     */
    public void setTop(int top) {
        y = top;
    }

    public int getCenter() {
        return y + height / 2;
    }

    /**
     * Gets the bottom Y-coordinate of this layer.
     *
     * @return The Y-coordinate representing the bottom of the layer.
     */
    public int getBottom() {
        return y + height;
    }

    /**
     * Gets the height of this layer.
     *
     * @return The height of the layer.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Sets the height of this layer.
     *
     * @param height The height to set for the layer.
     */
    public void setHeight(int height) {
        this.height = height;
    }

    /**
     * Checks if this layer contains only dummy nodes.
     *
     * @return true if all nodes in the layer are dummy nodes; false otherwise.
     */
    public boolean containsOnlyDummyNodes() {
        for (LayoutNode node : this) {
            if (!node.isDummy()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sorts the nodes in this layer by their X-coordinate in increasing order.
     * Assigns position indices to nodes based on the sorted order.
     * Adjusts the X-coordinates of nodes to ensure minimum spacing between them.
     */
    public void sortNodesByX() {
        if (isEmpty()) return;

        sort(NODE_X_COMPARATOR); // Sort nodes in the layer increasingly by x

        updateNodeIndices();
        updateMinXSpacing(false);
    }

    /**
     * Ensures nodes have minimum horizontal spacing by adjusting their X positions.
     *
     * @param startFromZero if true, starts positioning from X = 0; otherwise, uses the first node's current X.
     */
    public void updateMinXSpacing(boolean startFromZero) {
        if (isEmpty()) {
            return; // No nodes to adjust.
        }

        int minX = startFromZero ? 0 : this.get(0).getX();

        for (LayoutNode node : this) {
            int x = Math.max(node.getX(), minX);
            node.setX(x);
            minX = x + node.getOuterWidth() + NODE_OFFSET;
        }
    }

    /**
     * Initializes nodes' X positions with spacing.
     */
    public void initXPositions() {
        int curX = 0;
        for (LayoutNode node : this) {
            node.setX(curX);
            curX += node.getOuterWidth() + NODE_OFFSET;
        }
    }

    /**
     * Updates the position indices of the nodes in this layer based on their order in the list.
     * Useful after nodes have been added or removed to ensure position indices are consistent.
     */
    public void updateNodeIndices() {
        int pos = 0;
        for (LayoutNode layoutNode : this) {
            layoutNode.setPos(pos);
            pos++;
        }
    }

    /**
     * Attempts to move the specified node to the right within the layer to the given X-coordinate.
     * Ensures that the node does not overlap with its right neighbor by checking required spacing.
     * If movement is possible without causing overlap, the node's X-coordinate is updated.
     *
     * @param layoutNode The node to move.
     * @param newX       The desired new X-coordinate for the node.
     */
    public void tryShiftNodeRight(LayoutNode layoutNode, int newX) {
        int currentX = layoutNode.getX();
        int shiftAmount = newX - currentX;
        int rightPos = layoutNode.getPos() + 1;

        if (rightPos < size()) {
            // There is a right neighbor
            LayoutNode rightNeighbor = get(rightPos);
            int proposedRightEdge = layoutNode.getRight() + shiftAmount;
            int requiredLeftEdge = rightNeighbor.getOuterLeft() - NODE_OFFSET;

            if (proposedRightEdge <= requiredLeftEdge) {
                layoutNode.setX(newX);
            }
        } else {
            // No right neighbor; safe to move freely to the right
            layoutNode.setX(newX);
        }
    }

    /**
     * Attempts to move the specified node to the left within the layer to the given X-coordinate.
     * Ensures that the node does not overlap with its left neighbor by checking required spacing.
     * If movement is possible without causing overlap, the node's X-coordinate is updated.
     *
     * @param layoutNode The node to move.
     * @param newX       The desired new X-coordinate for the node.
     */
    public void tryShiftNodeLeft(LayoutNode layoutNode, int newX) {
        int currentX = layoutNode.getX();
        int shiftAmount = currentX - newX;
        int leftPos = layoutNode.getPos() - 1;

        if (leftPos >= 0) {
            // There is a left neighbor
            LayoutNode leftNeighbor = get(leftPos);
            int proposedLeftEdge = layoutNode.getLeft() - shiftAmount;
            int requiredRightEdge = leftNeighbor.getOuterRight() + NODE_OFFSET;

            if (requiredRightEdge <= proposedLeftEdge) {
                layoutNode.setX(newX);
            }
        } else {
            // No left neighbor; safe to move freely to the left
            layoutNode.setX(newX);
        }
    }

    /**
     * Swaps two nodes
     *
     * @param i index of the first node
     * @param j index of the second node
     */
    public void swapNodes(int i, int j) {
        LayoutNode temp = get(i);
        set(i, get(j));
        set(j, temp);
    }

    /**
     * Reduces edge crossings in the layer by iteratively swapping adjacent nodes.
     * <p>
     * Nodes are swapped if the swap decreases edge crossings based on the given neighbor type.
     * The process continues until no improvements are found or the iteration limit is reached.
     *
     * @param neighborType the type of neighbors (predecessors or successors) to consider
     */
    public void reduceCrossings(LayoutNode.NeighborType neighborType) {
        final int MAX_ITERATIONS = 1000; // Limit for safety
        boolean improved = true;
        int iteration = 0;

        while (improved && iteration < MAX_ITERATIONS) {
            improved = false;
            iteration++;
            for (int i = 0; i < size() - 1; i++) {
                LayoutNode node1 = get(i);
                LayoutNode node2 = get(i + 1);
                int crossingsBefore = calculateEdgeCrossings(node1, node2, neighborType);
                swapNodes(i, i + 1);
                int crossingsAfter = calculateEdgeCrossings(node1, node2, neighborType);
                if (crossingsAfter >= crossingsBefore) {
                    // Swap back if no improvement
                    swapNodes(i, i + 1);
                } else {
                    improved = true;
                    // Update positions for the swapped nodes
                    node1.setX(i);
                    node2.setX(i + 1);
                }
            }
        }
    }

    /**
     * Orders the nodes in the layer based on barycenters and adjusts their X positions.
     * <p>
     * The barycenter is calculated for each node based on the specified neighbor type
     * (predecessors or successors). If no neighbors of the given type exist, the opposite
     * type is used as a fallback. After computing barycenters, the nodes are sorted and
     * their positions adjusted with minimum horizontal spacing.
     *
     * @param neighborType the type of neighbors to consider (predecessors or successors)
     */
    public void orderByBarycenters(LayoutNode.NeighborType neighborType) {
        for (LayoutNode node : this) {
            int barycenter;
            if (node.hasNeighborsOfType(neighborType)) {
                barycenter = node.computeBarycenterX(neighborType, false);
            } else {
                // Fallback to the opposite neighbor type
                barycenter = (neighborType == LayoutNode.NeighborType.SUCCESSORS)
                        ? node.computeBarycenterX(LayoutNode.NeighborType.PREDECESSORS, false)
                        : node.computeBarycenterX(LayoutNode.NeighborType.SUCCESSORS, false);
            }
            node.setOptimalX(barycenter);
        }
        // Sort nodes based on optimal X positions
        sort(NODES_OPTIMAL_X);
        // Update minimum horizontal spacing, starting from X = 0
        updateMinXSpacing(true);
    }

}
