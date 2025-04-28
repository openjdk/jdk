/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.layout.Cluster;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Segment;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.util.*;

public class HierarchicalCFGLayoutManager extends LayoutManager {

    private final FontMetrics fontMetrics;
    private final HierarchicalLayoutManager manager;
    private final Set<? extends Cluster> clusters;
    private final Set<? extends Link> clusterLinks;
    Map<Cluster, ClusterNode> clusterNodesMap;
    Map<Link, ClusterEdge> clusterEdgesMap;
    private List<Segment> segments;

    public HierarchicalCFGLayoutManager(Set<? extends Link> clusterLinks, Set<? extends Cluster> clusters) {
        this.clusterLinks = clusterLinks;
        this.clusters = clusters;
        this.segments = new ArrayList<>();
        // Anticipate block label sizes to dimension blocks appropriately.
        Canvas canvas = new Canvas();
        Font font = new Font("Arial", Font.BOLD, 14);
        fontMetrics = canvas.getFontMetrics(font);
        manager = new HierarchicalLayoutManager();
        manager.setLayoutSelfEdges(true);
    }

    @Override
    public void setCutEdges(boolean enable) {
        manager.setCutEdges(enable);
    }

    private void doLinearLayout(ClusterNode clusterNode) {
        Cluster cluster = clusterNode.getCluster();
        clusterNode.groupSegments();
        LayoutGraph graph = new LayoutGraph(clusterNode.getSubEdges(), clusterNode.getSubNodes());

        // Compute list of vertices that are actually laid out.
        List<Vertex> vertices = new ArrayList<>(cluster.getVertices().size());
        for (Vertex vertex : cluster.getVertices()) {
            if (graph.containsVertex(vertex)) { // The vertex is visible.
                vertices.add(vertex);
            }
        }
        int curY = 0;
        for (Vertex vertex : vertices) {
            vertex.setPosition(new Point(0, curY));
            curY += vertex.getSize().height;
        }

        // If live segments are available, compute their position.
        if (vertices.isEmpty()) {
            int x = ClusterNode.EMPTY_BLOCK_LIVE_RANGE_X_OFFSET;
            final int y = ClusterNode.EMPTY_BLOCK_LIVE_RANGE_Y_OFFSET;
            for (Segment s : clusterNode.getSubSegments()) {
                s.setStartPoint(new Point(x, y));
                s.setEndPoint(new Point(x, y));
                if (s.isLastOfLiveRange()) {
                    x += s.getCluster().getLiveRangeSeparation();
                }
            }
        } else {
            Vertex first = vertices.get(0);
            int x = (int)first.getSize().getWidth();
            int entryY = (int)first.getPosition().getY();
            Vertex last = vertices.get(vertices.size() - 1);
            int exitY = (int)last.getPosition().getY() + (int)last.getSize().getHeight();
            for (Segment s : clusterNode.getSubSegments()) {
                Vertex start = s.getStart();
                Vertex end = s.getEnd();
                int startY = s.getStart() == null ? entryY : (start.getPosition().y + (int)(start.getSize().getHeight() / 2));
                s.setStartPoint(new Point(x, startY));
                int endY = end == null ? exitY : (end.getPosition().y + (int)(end.getSize().getHeight() / 2));
                if (s.isInstantaneous()) {
                    endY = startY;
                }
                s.setEndPoint(new Point(x, endY));
                if (s.isLastOfLiveRange()) {
                    x += s.getCluster().getLiveRangeSeparation();
                }
            }
        }

        clusterNode.updateSize();
    }

    public void setSegments(List<Segment> segments) {
        this.segments = segments;
    }

    public void doLayout(LayoutGraph graph) {
        // Create cluster-level nodes and edges.
        clusterNodesMap = createClusterNodes(graph.getVertices());
        assert clusterNodesMap.size() == clusters.size();
        clusterEdgesMap = createClusterEdges(clusterNodesMap);
        assert clusterEdgesMap.size() == clusterLinks.size();

        // Compute sub-segments in every cluster.
        for (Segment s : segments) {
            Cluster c = s.getCluster();
            assert c != null : "Cluster of segment " + s + " is null!";
            clusterNodesMap.get(c).addSubSegment(s);
        }

        // Compute layout for each cluster.
        for (ClusterNode clusterNode : clusterNodesMap.values()) {
            doLinearLayout(clusterNode);
        }

        // mark root nodes
        LayoutGraph clusterGraph = new LayoutGraph(clusterEdgesMap.values(), clusterNodesMap.values());
        for (Vertex rootVertex : clusterGraph.findRootVertices()) {
            assert rootVertex instanceof ClusterNode;
            ((ClusterNode) rootVertex).setRoot(true);
        }

        // Compute inter-cluster layout.
        manager.doLayout(clusterGraph);

        // Write back results.
        writeBackClusterBounds();
        writeBackClusterEdgePoints();
    }

    private Map<Cluster, ClusterNode> createClusterNodes(SortedSet<Vertex> vertices) {
        Map<Cluster, ClusterNode> clusterNodes = new HashMap<>();
        for (Cluster cluster : clusters) {
            String blockLabel = "B" + cluster;
            Dimension emptySize = new Dimension(fontMetrics.stringWidth(blockLabel) + ClusterNode.PADDING,
                                                fontMetrics.getHeight() + ClusterNode.PADDING);
            ClusterNode clusterNode = new ClusterNode(cluster, cluster.toString(), fontMetrics.getHeight(), emptySize);
            clusterNodes.put(cluster, clusterNode);
        }

        for (Vertex vertex : vertices) {
            Cluster cluster = vertex.getCluster();
            clusterNodes.get(cluster).addSubNode(vertex);
        }
        return clusterNodes;
    }

    private Map<Link, ClusterEdge> createClusterEdges(Map<Cluster, ClusterNode> clusterNodes) {
        Map<Link, ClusterEdge> clusterEdges = new HashMap<>();

        for (Link clusterLink : clusterLinks) {
            ClusterNode fromClusterNode = clusterNodes.get(clusterLink.getFromCluster());
            ClusterNode toClusterNode = clusterNodes.get(clusterLink.getToCluster());
            assert fromClusterNode != null;
            assert toClusterNode != null;
            clusterEdges.put(clusterLink, new ClusterEdge(fromClusterNode, toClusterNode));
        }

        return clusterEdges;
    }

    private void writeBackClusterBounds() {
        assert clusterNodesMap.size() == clusters.size();
        for (ClusterNode clusterNode : clusterNodesMap.values()) {
            clusterNode.updateClusterBounds();
        }
    }

    private void writeBackClusterEdgePoints() {
        assert clusterEdgesMap.size() == clusterLinks.size();
        for (Link clusterLink : clusterLinks) {
            ClusterEdge clusterEdge = clusterEdgesMap.get(clusterLink);
            if (clusterEdge.getControlPoints() != null) {
                clusterLink.setControlPoints(clusterEdge.getControlPoints());
            } else {
                clusterLink.setControlPoints(new ArrayList<>());
            }
        }
    }
}
