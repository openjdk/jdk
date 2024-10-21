/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.hotspot.igv.layout.LayoutManager;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 *
 * @author Thomas Wuerthinger
 */
public class HierarchicalClusterLayoutManager extends LayoutManager {

    private final LayoutManager subManager;
    private final LayoutManager manager;

    public HierarchicalClusterLayoutManager() {
        this.manager =  new HierarchicalLayoutManager();
        this.subManager =  new HierarchicalLayoutManager();
    }

    @Override
    public void setCutEdges(boolean enable) {
        manager.setCutEdges(enable);
        subManager.setCutEdges(enable);
        maxLayerLength = enable ? 10 : -1;
    }

    public void doLayout(LayoutGraph graph) {
        HashMap<Cluster, List<Link>> listsConnection = new HashMap<>();
        HashMap<Cluster, HashMap<Port, ClusterInputSlotNode>> clusterInputSlotHash = new HashMap<>();
        HashMap<Cluster, HashMap<Port, ClusterOutputSlotNode>> clusterOutputSlotHash = new HashMap<>();

        HashMap<Cluster, ClusterNode> clusterNodes = new HashMap<>();
        HashMap<Cluster, Set<ClusterInputSlotNode>> clusterInputSlotSet = new HashMap<>();
        HashMap<Cluster, Set<ClusterOutputSlotNode>> clusterOutputSlotSet = new HashMap<>();
        Set<Link> clusterEdges = new HashSet<>();
        Set<Link> interClusterEdges = new HashSet<>();
        HashMap<Link, ClusterOutgoingConnection> linkClusterOutgoingConnection = new HashMap<>();
        HashMap<Link, InterClusterConnection> linkInterClusterConnection = new HashMap<>();
        HashMap<Link, ClusterIngoingConnection> linkClusterIngoingConnection = new HashMap<>();
        Set<ClusterNode> clusterNodeSet = new HashSet<>();

        Set<Cluster> clusters = new TreeSet<>();
        for (Vertex v : graph.getVertices()) {
            if (v.getCluster() != null) {
                clusters.add(v.getCluster());
            }
        }

        int z = 0;
        for (Cluster c : clusters) {
            listsConnection.put(c, new ArrayList<>());
            clusterInputSlotHash.put(c, new HashMap<>());
            clusterOutputSlotHash.put(c, new HashMap<>());
            clusterOutputSlotSet.put(c, new TreeSet<>());
            clusterInputSlotSet.put(c, new TreeSet<>());

            String blockLabel = "B" + c;
            Canvas canvas = new Canvas();
            FontMetrics fontMetrics = canvas.getFontMetrics(TITLE_FONT);
            Dimension emptySize = new Dimension(fontMetrics.stringWidth(blockLabel) + ClusterNode.PADDING * 2,
                    fontMetrics.getHeight() + ClusterNode.PADDING * 2);
            ClusterNode cn = new ClusterNode(c, "" + z, fontMetrics.getHeight(), emptySize);

            clusterNodes.put(c, cn);
            clusterNodeSet.add(cn);
            z++;
        }

        // Add cluster edges
        for (Cluster c : clusters) {
            ClusterNode start = clusterNodes.get(c);
            for (Cluster succ : c.getSuccessors()) {
                ClusterNode end = clusterNodes.get(succ);
                if (end != null && start != end) {
                    ClusterEdge e = new ClusterEdge(start, end);
                    clusterEdges.add(e);
                    interClusterEdges.add(e);
                }
            }
        }

        for (Vertex v : graph.getVertices()) {
            Cluster c = v.getCluster();
            assert c != null : "Cluster of vertex " + v + " is null!";
            clusterNodes.get(c).addSubNode(v);
        }

        for (Link l : graph.getLinks()) {
            Port fromPort = l.getFrom();
            Port toPort = l.getTo();
            Vertex fromVertex = fromPort.getVertex();
            Vertex toVertex = toPort.getVertex();
            Cluster fromCluster = fromVertex.getCluster();
            Cluster toCluster = toVertex.getCluster();

            assert listsConnection.containsKey(fromCluster);
            assert listsConnection.containsKey(toCluster);

            if (fromCluster == toCluster) {
                listsConnection.get(fromCluster).add(l);
                clusterNodes.get(fromCluster).addSubEdge(l);
            } else {
                ClusterInputSlotNode inputSlotNode;
                ClusterOutputSlotNode outputSlotNode;

                outputSlotNode = clusterOutputSlotHash.get(fromCluster).get(fromPort);
                inputSlotNode = clusterInputSlotHash.get(toCluster).get(fromPort);

                if (outputSlotNode == null) {
                    outputSlotNode = new ClusterOutputSlotNode(clusterNodes.get(fromCluster), "Out " + fromCluster.toString() + " " + fromPort);
                    clusterOutputSlotSet.get(fromCluster).add(outputSlotNode);
                    ClusterOutgoingConnection conn = new ClusterOutgoingConnection(outputSlotNode, l);
                    outputSlotNode.setOutgoingConnection(conn);
                    clusterNodes.get(fromCluster).addSubEdge(conn);
                    clusterOutputSlotHash.get(fromCluster).put(fromPort, outputSlotNode);

                    linkClusterOutgoingConnection.put(l, conn);
                } else {
                    linkClusterOutgoingConnection.put(l, outputSlotNode.getOutgoingConnection());
                }

                if (inputSlotNode == null) {
                    inputSlotNode = new ClusterInputSlotNode(clusterNodes.get(toCluster), "In " + toCluster.toString() + " " + fromPort);
                    clusterInputSlotSet.get(toCluster).add(inputSlotNode);
                }

                ClusterIngoingConnection conn = new ClusterIngoingConnection(inputSlotNode, l);
                clusterNodes.get(toCluster).addSubEdge(conn);
                clusterInputSlotHash.get(toCluster).put(fromPort, inputSlotNode);

                linkClusterIngoingConnection.put(l, conn);


                InterClusterConnection interConn = new InterClusterConnection(outputSlotNode, inputSlotNode);
                linkInterClusterConnection.put(l, interConn);
                clusterEdges.add(interConn);
            }
        }

        for (Cluster c : clusters) {
            ClusterNode n = clusterNodes.get(c);
            subManager.doLayout(new LayoutGraph(n.getSubEdges(), n.getSubNodes()));
            n.updateSize();
        }

        Set<Vertex> roots = new LayoutGraph(interClusterEdges).findRootVertices();
        for (Vertex v : roots) {
            assert v instanceof ClusterNode;
            ((ClusterNode) v).setRoot(true);
        }

        manager.doLayout(new LayoutGraph(clusterEdges, clusterNodeSet));

        for (Cluster c : clusters) {
            ClusterNode n = clusterNodes.get(c);
            c.setBounds(new Rectangle(n.getPosition(), n.getSize()));
        }

        for (Link l : graph.getLinks()) {
            if (linkInterClusterConnection.containsKey(l)) {
                ClusterOutgoingConnection conn1 = linkClusterOutgoingConnection.get(l);
                InterClusterConnection conn2 = linkInterClusterConnection.get(l);
                ClusterIngoingConnection conn3 = linkClusterIngoingConnection.get(l);

                assert conn1 != null;
                assert conn2 != null;
                assert conn3 != null;

                List<Point> points = new ArrayList<>();

                points.addAll(conn1.getControlPoints());
                points.addAll(conn2.getControlPoints());
                points.addAll(conn3.getControlPoints());

                l.setControlPoints(points);
            }
        }
    }
}
