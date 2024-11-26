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
import static com.sun.hotspot.igv.hierarchicallayout.LayoutNode.NODE_X_COMPARATOR;
import java.util.ArrayList;
import java.util.Collection;

public class LayoutLayer extends ArrayList<LayoutNode> {

    private int height = 0;
    private int y = 0;

    @Override
    public boolean addAll(Collection<? extends LayoutNode> c) {
        c.forEach(this::updateHeight);
        return super.addAll(c);
    }

    private void updateHeight(LayoutNode n) {
        height = Math.max(height, n.getOuterHeight());
    }

    @Override
    public boolean add(LayoutNode n) {
        updateHeight(n);
        return super.add(n);
    }

    public int calculateMaxLayerHeight() {
        int maxLayerHeight = 0;
        for (LayoutNode layoutNode : this) {
            if (!layoutNode.isDummy()) {
                // Center the node by setting equal top and bottom margins
                int offset = Math.max(layoutNode.getTopMargin(), layoutNode.getBottomMargin());
                layoutNode.setTopMargin(offset);
                layoutNode.setBottomMargin(offset);
            }
            maxLayerHeight = Math.max(maxLayerHeight, layoutNode.getOuterHeight());
        }
        return maxLayerHeight;
    }

    public int calculateScalePaddedBottom() {
        int maxXOffset = 0;

        for (LayoutNode layoutNode : this) {
            for (LayoutEdge succEdge : layoutNode.getSuccs()) {
                maxXOffset = Math.max(Math.abs(succEdge.getStartX() - succEdge.getEndX()), maxXOffset);
            }
        }

        int scalePaddedBottom = this.getHeight();
        scalePaddedBottom += (int) (SCALE_LAYER_PADDING * Math.max((int) (Math.sqrt(maxXOffset) * 2), LAYER_OFFSET * 3));
        return scalePaddedBottom;
    }

    public void centerNodesVertically() {
        for (LayoutNode layoutNode : this) {
            int centeredY = getTop() + (getHeight() - layoutNode.getOuterHeight()) / 2;
            layoutNode.setY(centeredY);
        }
    }

    public void setTop(int top) {
        y = top;
    }

    public void shiftTop(int shift) {
        y += shift;
    }

    public int getTop() {
        return y;
    }

    public int getCenter() {
        return y + height / 2;
    }

    public int getBottom() {
        return y + height;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    // Layer contains no non-dummy nodes
    public boolean isDummyLayer() {
        for (LayoutNode node : this) {
            if (!node.isDummy()) {
                return false;
            }
        }
        return true;
    }

    public void sortNodesByXAndSetPositions() {
        if (this.isEmpty()) return;

        // Sort nodes in the layer increasingly by x
        this.sort(NODE_X_COMPARATOR);

        int pos = 0;
        int minX = this.get(0).getX(); // Starting X position for the first node

        for (LayoutNode node : this) {
            node.setPos(pos);
            pos++;

            // Set the X position of the node to at least minX, ensuring spacing
            int x = Math.max(node.getX(), minX);
            node.setX(x);

            // Update minX for the next node based on the current node's outer width and offset
            minX = x + node.getOuterWidth() + NODE_OFFSET;
        }
    }

    public void updateLayerPositions() {
        int pos = 0;
        for (LayoutNode layoutNode : this) {
            layoutNode.setPos(pos);
            pos++;
        }
    }

    public void attemptMoveRight(LayoutNode layoutNode, int newX) {
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

    public void attemptMoveLeft(LayoutNode layoutNode, int newX) {
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
}
