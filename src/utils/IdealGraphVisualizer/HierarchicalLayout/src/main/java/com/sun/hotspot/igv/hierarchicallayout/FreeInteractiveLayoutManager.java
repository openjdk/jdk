/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.sun.hotspot.igv.hierarchicallayout.LayoutNode.LAYOUT_NODE_DEGREE_COMPARATOR;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.Point;
import java.util.*;

public class FreeInteractiveLayoutManager extends LayoutManager implements LayoutMover {

    private boolean cutEdges = false;

    private static final int LINE_OFFSET = 10;

    private Map<Vertex, LayoutNode> layoutNodes;

    private LayoutGraph prevGraph;

    private final Random random = new Random(42);

    // Create a comparator to sort nodes by the number of unassigned neighbors
    private final Comparator<LayoutNode> LeastUnassignedNeighborsComparator = Comparator.comparingInt(node -> {
        Vertex vertex = node.getVertex();
        int unassignedNeighbors = 0;
        for (Vertex neighborVertex : prevGraph.getNeighborVertices(vertex)) {
            if (!layoutNodes.containsKey(neighborVertex)) {
                unassignedNeighbors++;
            }
        }
        return unassignedNeighbors;
    });

    public FreeInteractiveLayoutManager() {
        this.cutEdges = false;
        this.layoutNodes = new HashMap<>();
        this.prevGraph = null;
    }

    @Override
    public void moveLink(Point linkPos, int shiftX) {}

    @Override
    public void moveVertices(Set<? extends Vertex> movedVertices) {
        for (Vertex v : movedVertices) {
            moveVertex(v);
        }
    }

    @Override
    public void moveVertex(Vertex vertex) {
        System.out.println("moveVertex " + vertex.getPosition());
        assert prevGraph.containsVertex(vertex);
        LayoutNode layoutNode = layoutNodes.get(vertex);
        layoutNode.setX(vertex.getPosition().x);
        layoutNode.setY(vertex.getPosition().y);
        for (Link link : prevGraph.getAllLinks(vertex)) {
            setLinkControlPoints(link);
        }
    }

    @Override
    public boolean isFreeForm() {
        return true;
    }

    public void setCutEdges(boolean enable) {
        this.cutEdges = enable;
    }

    @Override
    public void doLayout(LayoutGraph graph) {
        System.out.println("doLayout");
        prevGraph = graph;
        if (layoutNodes.isEmpty()) {
            HierarchicalLayoutManager manager = new HierarchicalLayoutManager();
            manager.doLayout(graph);
            for (LayoutNode node : graph.getLayoutNodes()) {
                layoutNodes.put(node.getVertex(), node);
            }
            graph.clearLayout();
        } else {
            // add new vertices to layoutNodes, x/y from barycenter
            List<LayoutNode> newLayoutNodes = new ArrayList<>();

            // Set up layout nodes for each vertex
            for (Vertex vertex : prevGraph.getVertices()) {
                if (!layoutNodes.containsKey(vertex)) {
                    LayoutNode addedNode = new LayoutNode(vertex);
                    newLayoutNodes.add(addedNode);

                }
            }

            positionNewLayoutNodes(newLayoutNodes);
        }

        // Write back vertices
        for (Vertex vertex : prevGraph.getVertices()) {
            LayoutNode layoutNode = layoutNodes.get(vertex);
            assert layoutNode != null;
            vertex.setPosition(new Point(layoutNode.getLeft(), layoutNode.getTop()));
        }

        // Write back links
        for (Link link : prevGraph.getLinks()) {
            setLinkControlPoints(link);
        }
    }

    // Constants for offsets and displacements
    private static final int MAX_OFFSET_AROUND_NEIGHBOR = 50; // Max offset for random positioning around a neighbor
    private static final int MAX_OFFSET_AROUND_ORIGIN = 100; // Max offset for random positioning around origin
    private static final int DISPLACEMENT_RANGE_BARYCENTER = 20; // Displacement range for barycenter calculation
    private static final int DISPLACEMENT_RANGE_REFINEMENT = 10; // Fine displacement range for refinement

    public void positionNewLayoutNodes(List<LayoutNode> newLayoutNodes) {
        Random random = new Random();

        // First pass: Initial positioning based on unassigned neighbors
        newLayoutNodes.sort(LeastUnassignedNeighborsComparator);

        for (LayoutNode node : newLayoutNodes) {
            Vertex vertex = node.getVertex();

            // Gather assigned neighbors
            List<LayoutNode> assignedNeighbors = new ArrayList<>();
            for (Vertex neighborVertex : prevGraph.getNeighborVertices(vertex)) {
                if (layoutNodes.containsKey(neighborVertex)) {
                    assignedNeighbors.add(layoutNodes.get(neighborVertex));
                }
            }

            if (!assignedNeighbors.isEmpty()) {
                if (assignedNeighbors.size() == 1) {
                    // Single neighbor: Random position around the neighbor
                    setRandomPositionAroundNode(node, assignedNeighbors.get(0));
                } else {
                    // Multiple neighbors: Calculate barycenter with displacement
                    calculateBarycenterWithDisplacement(node, assignedNeighbors, DISPLACEMENT_RANGE_BARYCENTER);
                }
            } else {
                // No neighbors: Position randomly around (0, 0)
                setRandomPositionAroundOrigin(node, random);
            }

            // Add the new node to the layout
            layoutNodes.put(vertex, node);
        }

        // Second pass: Refine positions based on neighbor degree
        newLayoutNodes.sort(LAYOUT_NODE_DEGREE_COMPARATOR.reversed());

        for (LayoutNode node : newLayoutNodes) {
            Vertex vertex = node.getVertex();

            // Gather assigned neighbors
            List<LayoutNode> assignedNeighbors = new ArrayList<>();
            for (Vertex neighborVertex : prevGraph.getNeighborVertices(vertex)) {
                if (layoutNodes.containsKey(neighborVertex)) {
                    assignedNeighbors.add(layoutNodes.get(neighborVertex));
                }
            }

            if (!assignedNeighbors.isEmpty()) {
                // Refine position based on weighted barycenter
                calculateBarycenterWithDisplacement(node, assignedNeighbors, DISPLACEMENT_RANGE_REFINEMENT);
            }

            // Ensure node's position remains updated in the layout
            layoutNodes.put(vertex, node);
        }
    }

    // Utility method: Random position around a given node
    private void setRandomPositionAroundNode(LayoutNode node, LayoutNode neighbor) {
        int randomX = neighbor.getX() + random.nextInt(MAX_OFFSET_AROUND_NEIGHBOR + 1);
        int randomY = neighbor.getY() + random.nextInt(MAX_OFFSET_AROUND_NEIGHBOR + 1);
        node.setX(randomX);
        node.setY(randomY);
    }

    // Utility method: Random position around origin
    private void setRandomPositionAroundOrigin(LayoutNode node, Random random) {
        int randomX = random.nextInt(MAX_OFFSET_AROUND_ORIGIN + 1);
        int randomY = random.nextInt(MAX_OFFSET_AROUND_ORIGIN + 1);
        node.setX(randomX);
        node.setY(randomY);
    }

    // Utility method: Calculate barycenter with displacement
    private void calculateBarycenterWithDisplacement(LayoutNode node, List<LayoutNode> neighbors, int displacementRange) {
        double barycenterX = 0, barycenterY = 0;
        for (LayoutNode neighbor : neighbors) {
            barycenterX += neighbor.getX();
            barycenterY += neighbor.getY();
        }
        barycenterX /= neighbors.size();
        barycenterY /= neighbors.size();

        // Add random displacement for slight separation
        int displacementX = random.nextInt(displacementRange + 1);
        int displacementY = random.nextInt(displacementRange + 1);
        node.setX((int) barycenterX + displacementX);
        node.setY((int) barycenterY + displacementY);
    }

    private void setLinkControlPoints(Link link) {
        if (link.getFrom().getVertex() == link.getTo().getVertex()) return;
        System.out.println("setLinkControlPoints");
        if (!link.getControlPoints().isEmpty()) System.out.println("old link: " + link.getControlPoints().get(0));

        LayoutNode from = layoutNodes.get(link.getFrom().getVertex());
        LayoutNode to = layoutNodes.get(link.getTo().getVertex());
        int relativeFromX = link.getFrom().getRelativePosition().x;
        int relativeToX = link.getTo().getRelativePosition().x;

        int startX = from.getLeft() + relativeFromX;
        int startY = from.getBottom();
        int endX = to.getLeft() + relativeToX;
        int endY = to.getTop();

        Point startPoint = new Point(startX, startY);
        Point endPoint = new Point(endX, endY);
        List<Point> line = new ArrayList<>();
        line.add(startPoint);
        line.add(startPoint);

        //line.add(new Point(startPoint.x, startPoint.y + LINE_OFFSET));
        //line.add(new Point(endPoint.x, endPoint.y - LINE_OFFSET));
        line.add(endPoint);
        line.add(endPoint);
        link.setControlPoints(line);
        if (!link.getControlPoints().isEmpty()) System.out.println("new link: " + link.getControlPoints().get(0));
    }
}
