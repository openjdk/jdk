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

import static com.sun.hotspot.igv.hierarchicallayout.LayoutGraph.LINK_COMPARATOR;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.Point;
import java.util.*;

public class FreeInteractiveLayoutManager extends LayoutManager implements LayoutMover {

    private boolean cutEdges;

    @Override
    public void moveLink(Point linkPos, int shiftX) {

    }

    @Override
    public void moveVertices(Set<? extends Vertex> movedVertices) {

    }

    @Override
    public void moveVertex(Vertex movedVertex) {

    }

    public FreeInteractiveLayoutManager() {
        this.cutEdges = false;
    }

    public void setCutEdges(boolean enable) {
        this.cutEdges = enable;
    }

    private LayoutGraph graph;

    @Override
    public void doLayout(LayoutGraph graph) {
        if (this.graph == null) {
            HierarchicalLayoutManager manager = new HierarchicalLayoutManager();
            manager.doLayout(graph);
        } else {
            updateLayout(graph);
            HierarchicalLayoutManager.WriteResult.apply(graph);
        }

        this.graph = graph;
    }

    public void updateLayout(LayoutGraph graph) {
        HashSet<LayoutNode> newLayoutNode = new HashSet<>();

        // Reset layout structures
        graph.clearLayout();

        // create empty layers
        graph.initLayers(graph.getLayerCount());

        // Set up layout nodes for each vertex
        for (Vertex vertex : graph.getVertices()) {
            LayoutNode newNode = graph.createLayoutNode(vertex);
            if (graph.hasLayoutNode(vertex)) {
                LayoutNode prevNode = graph.getLayoutNode(vertex);
                newNode.setPos(prevNode.getPos());
                newNode.setX(prevNode.getX());
                newNode.setY(prevNode.getY());
                graph.addNodeToLayer(newNode, prevNode.getLayer());
            } else {
                newLayoutNode.add(newNode);
            }
        }

        for (Link link : graph.getLinks()) {
            graph.createLayoutEdge(link);
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
