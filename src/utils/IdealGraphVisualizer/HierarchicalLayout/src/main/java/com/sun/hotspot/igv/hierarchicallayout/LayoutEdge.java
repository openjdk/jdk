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

import com.sun.hotspot.igv.layout.Link;

/**
 * The LayoutEdge class represents a connection between two nodes (LayoutNode) in a hierarchical graph layout.
 * It stores information about the starting node (from), the ending node (to), and the positions where
 * the edge connects to these nodes. It also keeps track of whether the edge has been reversed,
 * which is useful for handling edges that go against the main flow in hierarchical layouts (like loops or back edges).
 * This class ensures that edges are drawn correctly between nodes,
 * helping to create clear and understandable visualizations of the graph.
 */
public class LayoutEdge {

    private LayoutNode from;
    private LayoutNode to;
    // Horizontal distance relative to start of 'from'.
    private int relativeFromX;
    // Horizontal distance relative to start of 'to'.
    private int relativeToX;
    private Link link;
    private boolean isReversed;

    /**
     * Constructs a LayoutEdge between two nodes with the specified link.
     * The relative positions are set to zero by default.
     *
     * @param from The source LayoutNode.
     * @param to   The target LayoutNode.
     * @param link The Link associated with this edge.
     */
    public LayoutEdge(LayoutNode from, LayoutNode to, Link link) {
        this.from = from;
        this.to = to;
        this.link = link;
        this.isReversed = false;
    }

    /**
     * Constructs a LayoutEdge between two nodes with specified relative positions and link.
     *
     * @param from          The source LayoutNode.
     * @param to            The target LayoutNode.
     * @param relativeFromX The horizontal distance relative to the start of 'from' node.
     * @param relativeToX   The horizontal distance relative to the start of 'to' node.
     * @param link          The Link associated with this edge.
     */
    public LayoutEdge(LayoutNode from, LayoutNode to, int relativeFromX, int relativeToX, Link link) {
        this(from, to, link);
        this.relativeFromX = relativeFromX;
        this.relativeToX = relativeToX;
    }

    /**
     * Gets the absolute x-coordinate of the starting point of the edge.
     *
     * @return The x-coordinate of the edge's starting point.
     */
    public int getStartX() {
        return relativeFromX + from.getLeft();
    }

    /**
     * Gets the absolute y-coordinate of the starting point of the edge.
     *
     * @return The y-coordinate of the edge's starting point.
     */
    public int getStartY() {
        return from.getBottom();
    }

    /**
     * Gets the absolute x-coordinate of the ending point of the edge.
     *
     * @return The x-coordinate of the edge's ending point.
     */
    public int getEndX() {
        return relativeToX + to.getLeft();
    }

    /**
     * Gets the absolute y-coordinate of the ending point of the edge.
     *
     * @return The y-coordinate of the edge's ending point.
     */
    public int getEndY() {
        return to.getTop();
    }

    /**
     * Reverses the direction of the edge.
     * Marks the edge as reversed, which is used to represent back edges in hierarchical layouts.
     */
    public void reverse() {
        isReversed = !isReversed;
    }

    /**
     * Checks if the edge is reversed.
     *
     * @return True if the edge is reversed; false otherwise.
     */
    public boolean isReversed() {
        return isReversed;
    }

    @Override
    public String toString() {
        return "Edge " + from + ", " + to;
    }

    /**
     * Gets the source node of the edge.
     *
     * @return The source LayoutNode.
     */
    public LayoutNode getFrom() {
        return from;
    }

    /**
     * Sets the source node of the edge.
     *
     * @param from The LayoutNode to set as the source.
     */
    public void setFrom(LayoutNode from) {
        this.from = from;
    }

    /**
     * Gets the target node of the edge.
     *
     * @return The target LayoutNode.
     */
    public LayoutNode getTo() {
        return to;
    }

    /**
     * Sets the target node of the edge.
     *
     * @param to The LayoutNode to set as the target.
     */
    public void setTo(LayoutNode to) {
        this.to = to;
    }

    /**
     * Gets the absolute x-coordinate of the source node's connection point for this edge.
     *
     * @return The x-coordinate of the source node's connection point.
     */
    public int getFromX() {
        return from.getX() + getRelativeFromX();
    }

    /**
     * Gets the absolute x-coordinate of the target node's connection point for this edge.
     *
     * @return The x-coordinate of the target node's connection point.
     */
    public int getToX() {
        return to.getX() + getRelativeToX();
    }

    /**
     * Gets the relative horizontal position from the source node's left boundary to the edge's starting point.
     *
     * @return The relative x-coordinate from the source node.
     */
    public int getRelativeFromX() {
        return relativeFromX;
    }

    /**
     * Sets the relative horizontal position from the source node's left boundary to the edge's starting point.
     *
     * @param relativeFromX The relative x-coordinate to set.
     */
    public void setRelativeFromX(int relativeFromX) {
        this.relativeFromX = relativeFromX;
    }

    /**
     * Gets the relative horizontal position from the target node's left boundary to the edge's ending point.
     *
     * @return The relative x-coordinate to the target node.
     */
    public int getRelativeToX() {
        return relativeToX;
    }

    /**
     * Sets the relative horizontal position from the target node's left boundary to the edge's ending point.
     *
     * @param relativeToX The relative x-coordinate to set.
     */
    public void setRelativeToX(int relativeToX) {
        this.relativeToX = relativeToX;
    }

    /**
     * Gets the Link associated with this edge.
     *
     * @return The Link object.
     */
    public Link getLink() {
        return link;
    }

    public void setLink(Link link) {
        this.link = link;
    }
}
