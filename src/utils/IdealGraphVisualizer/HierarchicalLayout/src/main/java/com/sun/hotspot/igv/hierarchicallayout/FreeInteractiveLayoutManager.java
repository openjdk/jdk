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
    public void moveLink(Point linkPos, int shiftX) {

    }

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

            /*
            newLayoutNodes.sort(LeastUnassignedNeighborsComparator);

            // Place each newLayoutNode by calculating its position
            for (LayoutNode node : newLayoutNodes) {
                Vertex vertex = node.getVertex();

                // Gather positions of all assigned neighbors
                List<LayoutNode> assignedNeighbors = new ArrayList<>();
                for (Vertex neighborVertex : prevGraph.getNeighborVertices(vertex)) {
                    if (layoutNodes.containsKey(neighborVertex)) {
                        assignedNeighbors.add(layoutNodes.get(neighborVertex));
                    }
                }

                if (!assignedNeighbors.isEmpty()) {
                    if (assignedNeighbors.size() == 1) {
                        // If there's only one neighbor, use its position and add randomness
                        LayoutNode neighbor = assignedNeighbors.get(0);
                        int randomX = random.nextInt(101); // Random value between 0 and 100
                        int randomY = random.nextInt(101); // Random value between 0 and 100
                        node.setX(neighbor.getX() + randomX);
                        node.setY(neighbor.getY() + randomY);
                    } else {
                        // Calculate barycenter (average of neighbors' positions)
                        double barycenterX = 0;
                        double barycenterY = 0;
                        for (LayoutNode neighbor : assignedNeighbors) {
                            barycenterX += neighbor.getX();
                            barycenterY += neighbor.getY();
                        }
                        barycenterX /= assignedNeighbors.size();
                        barycenterY /= assignedNeighbors.size();

                        // Set node's position based on barycenter
                        node.setX((int) barycenterX);
                        node.setY((int) barycenterY);
                    }
                } else {
                    // No neighbors in layoutNodes, set to a random default position
                    int randomX = random.nextInt(101); // Random value between 0 and 100
                    int randomY = random.nextInt(101); // Random value between 0 and 100
                    node.setX(randomX);
                    node.setY(randomY);
                }

                // Add the node to the layoutNodes map
                layoutNodes.put(vertex, node);
            }

             */
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

    public void positionNewLayoutNodes(List<LayoutNode> newLayoutNodes) {
        Random random = new Random();

        // First pass: Initial positioning
        newLayoutNodes.sort(LeastUnassignedNeighborsComparator);

        for (LayoutNode node : newLayoutNodes) {
            Vertex vertex = node.getVertex();

            List<LayoutNode> assignedNeighbors = new ArrayList<>();
            for (Vertex neighborVertex : prevGraph.getNeighborVertices(vertex)) {
                if (layoutNodes.containsKey(neighborVertex)) {
                    assignedNeighbors.add(layoutNodes.get(neighborVertex));
                }
            }

            if (!assignedNeighbors.isEmpty()) {
                double barycenterX = 0;
                double barycenterY = 0;
                for (LayoutNode neighbor : assignedNeighbors) {
                    barycenterX += neighbor.getX();
                    barycenterY += neighbor.getY();
                }
                barycenterX /= assignedNeighbors.size();
                barycenterY /= assignedNeighbors.size();
                node.setX((int) barycenterX);
                node.setY((int) barycenterY);
            } else {
                node.setX(0);
                node.setY(0);
            }

            layoutNodes.put(vertex, node);
        }

        // Refine positions in second pass: Sort newLayoutNodes by descending neighbor degree.
        newLayoutNodes.sort(LAYOUT_NODE_DEGREE_COMPARATOR.reversed());

        for (LayoutNode node : newLayoutNodes) {
            Vertex vertex = node.getVertex();

            // Assert all neighbors are now assigned
            for (Vertex neighborVertex : prevGraph.getNeighborVertices(vertex)) {
                assert layoutNodes.containsKey(neighborVertex) : "Neighbor is not assigned";
            }

            // Gather assigned neighbors
            List<LayoutNode> assignedNeighbors = new ArrayList<>();
            for (Vertex neighborVertex : prevGraph.getNeighborVertices(vertex)) {
                assignedNeighbors.add(layoutNodes.get(neighborVertex));
            }

            if (assignedNeighbors.isEmpty()) {
                // No neighbors: Position randomly around (0, 0)
                int randomX = random.nextInt(101); // Random value between 0 and 100
                int randomY = random.nextInt(101); // Random value between 0 and 100
                node.setX(randomX);
                node.setY(randomY);
            } else if (assignedNeighbors.size() == 1) {
                // Only one neighbor: Position randomly around the neighbor
                LayoutNode neighbor = assignedNeighbors.get(0);
                int randomX = neighbor.getX() + random.nextInt(101); // Add random offset
                int randomY = neighbor.getY() + random.nextInt(101); // Add random offset
                node.setX(randomX);
                node.setY(randomY);
            } else {
                // Multiple neighbors: Calculate barycenter
                double barycenterX = 0;
                double barycenterY = 0;
                for (LayoutNode neighbor : assignedNeighbors) {
                    barycenterX += neighbor.getX();
                    barycenterY += neighbor.getY();
                }
                barycenterX /= assignedNeighbors.size();
                barycenterY /= assignedNeighbors.size();
                node.setX((int) barycenterX);
                node.setY((int) barycenterY);
            }

            // Update position in layoutNodes
            layoutNodes.put(vertex, node);
        }    }


    private void setLinkControlPoints(Link link) {
        if (link.getFrom().getVertex() == link.getTo().getVertex()) return;
        LayoutEdge edge = new LayoutEdge(
                layoutNodes.get(link.getFrom().getVertex()),
                layoutNodes.get(link.getTo().getVertex()),
                link.getFrom().getRelativePosition().x,
                link.getTo().getRelativePosition().x,
                link);
        Point startPoint = new Point(edge.getStartX(), edge.getStartY());
        Point endPoint = new Point(edge.getEndX(), edge.getEndY());
        List<Point> line = new ArrayList<>(4);
        line.add(startPoint);
        line.add(new Point(startPoint.x, startPoint.y + LINE_OFFSET));
        line.add(new Point(endPoint.x, endPoint.y - LINE_OFFSET));
        line.add(endPoint);
        link.setControlPoints(line);
    }

    private static Point adjustPoint(Point from, Point to, double x) {
        // Calculate the difference in x and y coordinates
        double dx = to.x - from.x;
        double dy = to.y - from.y;

        // Calculate the current length of the line
        double currentLength = Math.sqrt(dx * dx + dy * dy);

        // Scale the differences to make the line length equal to x
        double scale = x / currentLength;

        // Calculate the new coordinates for the point "to"
        int newX = (int) Math.round(from.x + dx * scale);
        int newY = (int) Math.round(from.y + dy * scale);

        return new Point(newX, newY);
    }

    public void updateLayout(LayoutGraph graph) {
        HashSet<LayoutNode> newLayoutNode = new HashSet<>();

        // Reset layout structures
        graph.clearLayout();

        // Set up layout nodes for each vertex
        for (Vertex vertex : graph.getVertices()) {
            LayoutNode newNode = graph.createLayoutNode(vertex);
            if (layoutNodes.containsKey(vertex)) {
                LayoutNode cachedNode = layoutNodes.get(vertex);
                newNode.setX(cachedNode.getX());
                newNode.setY(cachedNode.getY());
            } else {
                newLayoutNode.add(newNode);
            }
        }
    }

    private int calculateOptimalBoth(LayoutNode n) {
        if (!n.hasPredecessors() && !n.hasSuccessors()) {
            return n.getX();
        }

        int[] values = new int[n.getPredecessors().size() + n.getSuccessors().size()];
        int i = 0;

        for (LayoutEdge e : n.getPredecessors()) {
            values[i] = e.getFromX() - e.getRelativeToX();
            i++;
        }

        for (LayoutEdge e : n.getSuccessors()) {
            values[i] = e.getToX() - e.getRelativeFromX();
            i++;
        }

        return median(values);
    }

    public static int median(int[] values) {
        Arrays.sort(values);
        if (values.length % 2 == 0) {
            return (values[values.length / 2 - 1] + values[values.length / 2]) / 2;
        } else {
            return values[values.length / 2];
        }
    }
}
