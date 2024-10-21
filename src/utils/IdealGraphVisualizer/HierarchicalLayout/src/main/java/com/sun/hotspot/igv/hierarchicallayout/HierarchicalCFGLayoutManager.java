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


import com.sun.hotspot.igv.layout.LayoutManager;
import com.sun.hotspot.igv.layout.*;
import java.awt.*;
import java.util.*;

public class HierarchicalCFGLayoutManager extends LayoutManager {

    private final FontMetrics fontMetrics;
    // Lays out nodes within a single cluster (basic block).
    private LayoutManager subManager;
    // Lays out clusters in the CFG.
    private final LayoutManager manager;
    private Set<Cluster> clusters;

    public HierarchicalCFGLayoutManager() {
        // Anticipate block label sizes to dimension blocks appropriately.
        Canvas canvas = new Canvas();
        fontMetrics = canvas.getFontMetrics(TITLE_FONT);
        this.manager =  new HierarchicalLayoutManager();
    }

    public void setSubManager(LayoutManager manager) {
        this.subManager = manager;
    }

    public void setClusters(Set<Cluster> clusters) {
        this.clusters = clusters;
    }

    @Override
    public void setCutEdges(boolean enable) {
        manager.setCutEdges(enable);
        subManager.setCutEdges(enable);
        maxLayerLength = enable ? 10 : -1;
    }

    public void doLayout(LayoutGraph graph) {

        // Create cluster-level nodes and edges.
        Map<Cluster, ClusterNode> clusterNode = createClusterNodes(graph);
        Set<ClusterEdge> clusterEdges = createClusterEdges(clusterNode);
        markRootClusters(clusterEdges);

        // Compute layout for each cluster.
        for (Cluster c : clusters) {
            ClusterNode n = clusterNode.get(c);
            subManager.doLayout(new LayoutGraph(n.getSubEdges(), n.getSubNodes()));
            n.updateSize();
        }

        // Compute inter-cluster layout.
        manager.doLayout(new LayoutGraph(clusterEdges, new HashSet<>(clusterNode.values())));

        // Write back results.
        writeBackClusterBounds(clusterNode);
        writeBackClusterEdgePoints(graph, clusterEdges);
    }

    private Map<Cluster, ClusterNode> createClusterNodes(LayoutGraph graph) {
        Map<Cluster, ClusterNode> clusterNode = new HashMap<>();
        for (Cluster c : clusters) {
            String blockLabel = "B" + c;
            Dimension emptySize = new Dimension(fontMetrics.stringWidth(blockLabel) + ClusterNode.PADDING,
                                                fontMetrics.getHeight() + ClusterNode.PADDING);
            ClusterNode cn = new ClusterNode(c, c.toString(), fontMetrics.getHeight(), emptySize);
            clusterNode.put(c, cn);
        }

        for (Vertex v : graph.getVertices()) {
            Cluster c = v.getCluster();
            assert c != null : "Cluster of vertex " + v + " is null!";
            clusterNode.get(c).addSubNode(v);
        }
        return clusterNode;
    }

    private Set<ClusterEdge> createClusterEdges(Map<Cluster, ClusterNode> clusterNode) {
        Set<ClusterEdge> clusterEdges = new HashSet<>();
        for (Cluster c : clusters) {
            ClusterNode start = clusterNode.get(c);
            for (Cluster succ : c.getSuccessors()) {
                ClusterNode end = clusterNode.get(succ);
                if (end != null) {
                    ClusterEdge e = new ClusterEdge(start, end);
                    clusterEdges.add(e);
                }
            }
        }
        return clusterEdges;
    }

    private void markRootClusters(Set<ClusterEdge> clusterEdges) {
        Set<Vertex> roots = new LayoutGraph(clusterEdges).findRootVertices();
        for (Vertex v : roots) {
            assert v instanceof ClusterNode;
            ((ClusterNode) v).setRoot(true);
        }
    }

    private void writeBackClusterBounds(Map<Cluster, ClusterNode> clusterNode) {
        for (Cluster c : clusters) {
            ClusterNode n = clusterNode.get(c);
            c.setBounds(new Rectangle(n.getPosition(), n.getSize()));
        }
    }

    private void writeBackClusterEdgePoints(LayoutGraph graph, Set<ClusterEdge> clusterEdges) {
        // Map from "primitive" cluster edges to their input links.
        Map<AbstractMap.SimpleEntry<Cluster, Cluster>, Link> inputLink = new HashMap<>();
        for (Link l : graph.getLinks()) {
            inputLink.put(new AbstractMap.SimpleEntry<>(l.getFromCluster(), l.getToCluster()), l);
        }
        for (ClusterEdge ce : clusterEdges) {
            assert (ce.getControlPoints() != null);
            Link l = inputLink.get(new AbstractMap.SimpleEntry<>(ce.getFromCluster(), ce.getToCluster()));
            l.setControlPoints(ce.getControlPoints());
        }
    }
}
