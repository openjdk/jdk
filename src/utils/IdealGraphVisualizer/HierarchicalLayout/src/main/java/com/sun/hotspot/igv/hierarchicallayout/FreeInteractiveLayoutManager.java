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

    // Constants for offsets and displacements
    private static final int MAX_OFFSET_AROUND_NEIGHBOR = 200; // Max offset for random positioning around a neighbor
    private static final int MAX_OFFSET_AROUND_ORIGIN = 200; // Max offset for random positioning around origin
    private static final int DISPLACEMENT_RANGE_BARYCENTER = 100; // Displacement range for barycenter calculation
    private static final int DISPLACEMENT_RANGE_SINGLE = 200;

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
        prevGraph = graph;
        if (layoutNodes.isEmpty()) {
            HierarchicalLayoutManager manager = new HierarchicalLayoutManager();
            manager.doLayout(graph);
            for (LayoutNode node : graph.getLayoutNodes()) {
                node.initSize();
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
                    addedNode.initSize();
                    newLayoutNodes.add(addedNode);
                }
            }

            positionNewLayoutNodes(newLayoutNodes);
        }

        // Write back vertices
        for (Vertex vertex : prevGraph.getVertices()) {
            LayoutNode layoutNode = layoutNodes.get(vertex);
            layoutNode.setVertex(vertex);
            vertex.setPosition(new Point(layoutNode.getLeft(), layoutNode.getTop()));
        }

        // Write back links
        for (Link link : prevGraph.getLinks()) {
            setLinkControlPoints(link);
        }
    }

    public void positionNewLayoutNodes(List<LayoutNode> newLayoutNodes) {
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
                    // Single neighbor: position around the neighbor
                    setPositionAroundSingleNode(node, assignedNeighbors.get(0), DISPLACEMENT_RANGE_SINGLE);
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

        // Collect all nodes (existing and new)
        Collection<LayoutNode> allNodes = layoutNodes.values();

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
                // Refine position based on force-based method
                applyForceBasedAdjustment(node, assignedNeighbors, allNodes);
            }

            // Ensure node's position remains updated in the layout
            layoutNodes.put(vertex, node);
        }
    }

    /**
     * Applies a force-based adjustment to the position of a given layout node
     * based on repulsive forces from all other nodes and attractive forces from its assigned neighbors.
     * <p>
     * This method simulates a physical system where nodes repel each other to maintain spacing
     * and are pulled towards their neighbors to maintain connectivity. The forces are calculated
     * using Coulomb's law for repulsion and Hooke's law for attraction. The system iterates for
     * a fixed number of iterations to stabilize the position of the node.
     *
     * @param node               The node whose position is being adjusted.
     * @param assignedNeighbors  A list of neighboring nodes that attract this node.
     * @param allNodes           A collection of all nodes in the layout, used for repulsive forces.
     */
    private void applyForceBasedAdjustment(LayoutNode node, List<LayoutNode> assignedNeighbors, Collection<LayoutNode> allNodes) {
        // Constants for force-based adjustment
        final int ITERATIONS = 50; // Number of simulation iterations.
        final double REPULSION_CONSTANT = 1000; // Magnitude of repulsive forces.
        final double SPRING_CONSTANT = 0.2; // Strength of attractive forces to neighbors.
        final double DAMPING = 0.8; // Damping factor to reduce displacement and ensure convergence.
        final double IDEAL_LENGTH = 100; // Ideal distance between a node and its neighbors.

        double posX = node.getX();
        double posY = node.getY();
        double dx = 0, dy = 0; // Displacement

        for (int i = 0; i < ITERATIONS; i++) {
            double netForceX = 0;
            double netForceY = 0;

            // Repulsive forces from all other nodes
            for (LayoutNode otherNode : allNodes) {
                if (otherNode == node) continue; // Skip self

                double deltaX = posX - otherNode.getX();
                double deltaY = posY - otherNode.getY();
                double distanceSquared = deltaX * deltaX + deltaY * deltaY;
                double distance = Math.sqrt(distanceSquared);

                // If distance is zero, add small random noise to deltaX and deltaY
                if (distance == 0) {
                    deltaX = random.nextDouble() * 0.1 - 0.05; // Random value between -0.05 and 0.05
                    deltaY = random.nextDouble() * 0.1 - 0.05;
                    distanceSquared = deltaX * deltaX + deltaY * deltaY;
                    distance = Math.sqrt(distanceSquared);
                }

                // Repulsive force (Coulomb's law)
                double repulsiveForce = REPULSION_CONSTANT / distanceSquared;
                netForceX += (deltaX / distance) * repulsiveForce;
                netForceY += (deltaY / distance) * repulsiveForce;
            }

            // Attractive forces to assigned neighbors
            for (LayoutNode neighbor : assignedNeighbors) {
                double deltaX = neighbor.getX() - posX;
                double deltaY = neighbor.getY() - posY;
                double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

                // If distance is zero, add small random noise to deltaX and deltaY
                if (distance == 0) {
                    deltaX = random.nextDouble() * 0.1 - 0.05; // Random value between -0.05 and 0.05
                    deltaY = random.nextDouble() * 0.1 - 0.05;
                    distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                }

                // Attractive force (Hooke's law)
                double displacement = distance - IDEAL_LENGTH;
                double attractiveForce = SPRING_CONSTANT * displacement;
                netForceX += (deltaX / distance) * attractiveForce;
                netForceY += (deltaY / distance) * attractiveForce;
            }

            // Update displacement with damping
            dx = (dx + netForceX) * DAMPING;
            dy = (dy + netForceY) * DAMPING;

            // Update node position
            posX += dx;
            posY += dy;
        }

        // Set final position
        node.setX((int) posX);
        node.setY((int) posY);
    }

    // Utility method: position around a given node
    private void setPositionAroundSingleNode(LayoutNode node, LayoutNode neighbor, int displacement) {
        boolean neighborIsPredecessor = prevGraph.isPredecessorVertex(node.getVertex(), neighbor.getVertex());
        boolean neighborIsSuccessor = prevGraph.isSuccessorVertex(node.getVertex(), neighbor.getVertex());

        int shiftY = 0;
        if (neighborIsPredecessor) {
            shiftY = displacement;
        } else if (neighborIsSuccessor) {
            shiftY = -displacement;
        }
        assert shiftY != 0;

        int randomY = neighbor.getY() + random.nextInt(MAX_OFFSET_AROUND_NEIGHBOR + 1) + shiftY;
        int randomX = neighbor.getX() + random.nextInt(MAX_OFFSET_AROUND_NEIGHBOR + 1);
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

    /**
     * Sets control points for a given link based on its start and end layout nodes.
     * <p>
     * Calculates the start and end points, applies offsets for curvature, and updates
     * the link's control points.
     *
     * @param link The link to process.
     */
    private void setLinkControlPoints(Link link) {
        if (link.getFrom().getVertex() == link.getTo().getVertex()) return; // Skip self-links

        LayoutNode from = layoutNodes.get(link.getFrom().getVertex());
        from.setVertex(link.getFrom().getVertex());
        from.updateSize();

        LayoutNode to = layoutNodes.get(link.getTo().getVertex());
        to.setVertex(link.getTo().getVertex());
        to.updateSize();

        Point startPoint = new Point(from.getLeft() + link.getFrom().getRelativePosition().x, from.getBottom());
        Point endPoint = new Point(to.getLeft() + link.getTo().getRelativePosition().x, to.getTop());

        List<Point> controlPoints = new ArrayList<>();
        controlPoints.add(startPoint);
        controlPoints.add(new Point(startPoint.x, startPoint.y + LINE_OFFSET));
        controlPoints.add(new Point(endPoint.x, endPoint.y - LINE_OFFSET));
        controlPoints.add(endPoint);

        link.setControlPoints(controlPoints);
    }
}
