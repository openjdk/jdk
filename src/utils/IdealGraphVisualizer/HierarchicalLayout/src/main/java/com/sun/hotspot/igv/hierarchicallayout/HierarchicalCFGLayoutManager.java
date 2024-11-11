/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.layout.*;
import java.awt.*;
import java.util.*;

public class HierarchicalCFGLayoutManager extends LayoutManager {

    private final FontMetrics fontMetrics;
    // Lays out nodes within a single cluster (basic block).
    private final LayoutManager subManager;
    // Lays out clusters in the CFG.
    private final LayoutManager manager;
    private final Set<? extends Cluster> clusters;
    private final Set<? extends Link> clusterLinks;

    public HierarchicalCFGLayoutManager(Set<? extends Link> clusterLinks, Set<? extends Cluster> clusters) {
        this.clusterLinks = clusterLinks;
        this.clusters = clusters;
        // Anticipate block label sizes to dimension blocks appropriately.
        Canvas canvas = new Canvas();
        fontMetrics = canvas.getFontMetrics(TITLE_FONT);
        this.subManager = new LinearLayoutManager(clusters);
        this.manager =  new HierarchicalLayoutManager();
    }

    @Override
    public void setCutEdges(boolean enable) {
        manager.setCutEdges(enable);
        subManager.setCutEdges(enable);
        maxLayerLength = enable ? 10 : -1;
    }

    public void doLayout(LayoutGraph graph) {
        // Create cluster-level nodes and edges.
        Map<Cluster, ClusterNode> clusterNodes = createClusterNodes(graph.getVertices());
        Set<ClusterEdge> clusterEdges = createClusterEdges(clusterNodes);

        // Compute layout for each cluster.
        for (ClusterNode clusterNode : clusterNodes.values()) {
            subManager.doLayout(new LayoutGraph(clusterNode.getSubEdges(), clusterNode.getSubNodes()));
            clusterNode.updateSize();
        }

        // mark root nodes
        LayoutGraph clusterGraph = new LayoutGraph(clusterEdges, clusterNodes.values());
        for (Vertex rootVertex : clusterGraph.findRootVertices()) {
            assert rootVertex instanceof ClusterNode;
            ((ClusterNode) rootVertex).setRoot(true);
        }

        // Compute inter-cluster layout.
        manager.doLayout(clusterGraph);

        // Write back results.
        writeBackClusterBounds(clusterNodes);
        writeBackClusterEdgePoints(clusterEdges);
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

    private Set<ClusterEdge> createClusterEdges(Map<Cluster, ClusterNode> clusterNodes) {
        Set<ClusterEdge> clusterEdges = new HashSet<>();
        for (Cluster c : clusters) {
            ClusterNode start = clusterNodes.get(c);
            for (Cluster succ : c.getSuccessors()) {
                ClusterNode end = clusterNodes.get(succ);
                if (end != null) {
                    ClusterEdge e = new ClusterEdge(start, end);
                    clusterEdges.add(e);
                }
            }
        }
        return clusterEdges;
    }

    private void writeBackClusterBounds(Map<Cluster, ClusterNode> clusterNode) {
        for (Cluster c : clusters) {
            ClusterNode n = clusterNode.get(c);
            c.setBounds(new Rectangle(n.getPosition(), n.getSize()));
        }
    }

    private void writeBackClusterEdgePoints(Set<ClusterEdge> clusterEdges) {
        // Map from "primitive" cluster edges to their input links.
        Map<AbstractMap.SimpleEntry<Cluster, Cluster>, Link> linkMap = new HashMap<>();

        for (Link clusterLink : clusterLinks) {
            linkMap.put(new AbstractMap.SimpleEntry<>(clusterLink.getFromCluster(), clusterLink.getToCluster()), clusterLink);
        }

        for (ClusterEdge clusterEdge : clusterEdges) {
            Link clusterLink = linkMap.get(new AbstractMap.SimpleEntry<>(clusterEdge.getFromCluster(), clusterEdge.getToCluster()));
            clusterLink.setControlPoints(clusterEdge.getControlPoints());
        }
    }
}
