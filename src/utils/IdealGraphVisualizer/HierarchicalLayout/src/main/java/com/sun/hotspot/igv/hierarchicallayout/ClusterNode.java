/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.hotspot.igv.layout.Port;
import com.sun.hotspot.igv.layout.Segment;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.*;

/**
 *
 * @author Thomas Wuerthinger
 */
public class ClusterNode implements Vertex {

    public static final int PADDING = 8;
    private Cluster cluster;
    private Port inputSlot;
    private final Set<Vertex> subNodes;
    private Dimension size;
    private Point position;
    private final Set<Link> subEdges;
    private final List<Segment> subSegments;
    private boolean root;
    private final String name;
    private final int headerVerticalSpace;
    private final Dimension emptySize;

    public static final int EMPTY_BLOCK_LIVE_RANGE_X_OFFSET = 20;
    public static final int EMPTY_BLOCK_LIVE_RANGE_Y_OFFSET = 6;

    public ClusterNode(Cluster cluster, String name, int headerVerticalSpace, Dimension emptySize) {
        this.subNodes = new HashSet<>();
        this.subEdges = new HashSet<>();
        this.subSegments = new ArrayList<>();
        this.cluster = cluster;
        this.position = new Point(0, 0);
        this.name = name;
        this.headerVerticalSpace = headerVerticalSpace;
        this.emptySize = emptySize;
        if (emptySize.width > 0 || emptySize.height > 0) {
            updateSize();
        }
    }

    public void updateClusterBounds() {
        cluster.setBounds(new Rectangle(position, size));
    }

    public String getName() {
        return name;
    }

    public void addSubNode(Vertex v) {
        subNodes.add(v);
    }

    public void addSubEdge(Link l) {
        subEdges.add(l);
    }

    public Set<Link> getSubEdges() {
        return Collections.unmodifiableSet(subEdges);
    }

    public void addSubSegment(Segment s) {
        subSegments.add(s);
    }

    public void groupSegments() {
        for (int i = 1; i < subSegments.size(); i++) {
            if (subSegments.get(i).parentId() == subSegments.get(i - 1).parentId()) {
                subSegments.get(i - 1).setLastOfLiveRange(false);
            } else {
                subSegments.get(i - 1).setLastOfLiveRange(true);
            }
        }
    }

    public void updateSize() {
        calculateSize();

        final ClusterNode widget = this;
        inputSlot = new Port() {

            public Point getRelativePosition() {
                return new Point(size.width / 2, 0);
            }

            public Vertex getVertex() {
                return widget;
            }

            @Override
            public String toString() {
                return "ClusterInput(" + name + ")";
            }
        };
    }

    private void calculateSize() {

        if (subNodes.isEmpty() && subSegments.isEmpty()) {
            size = emptySize;
            return;
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;


        for (Vertex n : subNodes) {
            Point p = n.getPosition();
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x + n.getSize().width);
            maxY = Math.max(maxY, p.y + n.getSize().height);
        }

        for (Link l : subEdges) {
            List<Point> points = l.getControlPoints();
            for (Point p : points) {
                if (p != null) {
                    minX = Math.min(minX, p.x);
                    maxX = Math.max(maxX, p.x);
                    minY = Math.min(minY, p.y);
                    maxY = Math.max(maxY, p.y);
                }
            }
        }

        for (Segment segment : subSegments) {
            Point s = segment.getStartPoint();
            minX = Math.min(minX, s.x);
            maxX = Math.max(maxX, s.x + cluster.getLiveRangeSeparation());
        }
        if (!subSegments.isEmpty()) {
            maxX += cluster.getLiveRangeSeparation();
        }
        if (subNodes.isEmpty()) {
            maxX += ClusterNode.EMPTY_BLOCK_LIVE_RANGE_X_OFFSET;
        }

        size = new Dimension(maxX - minX, maxY - minY + headerVerticalSpace);

        // Normalize coordinates
        for (Vertex n : subNodes) {
            n.setPosition(new Point(n.getPosition().x - minX,
                                    n.getPosition().y - minY + headerVerticalSpace));
        }

        for (Link l : subEdges) {
            List<Point> points = new ArrayList<>(l.getControlPoints());
            for (Point p : points) {
                p.x -= minX;
                p.y = p.y - minY + headerVerticalSpace;
            }
            l.setControlPoints(points);

        }

        size.width += 2 * PADDING;
        size.height += 2 * PADDING;
    }

    public Port getInputSlot() {
        return inputSlot;

    }

    public Dimension getSize() {
        return size;
    }

    public Point getPosition() {
        return position;
    }

    public void setPosition(Point pos) {
        int startX = pos.x + PADDING;
        int startY = pos.y + PADDING;

        int minY = Integer.MAX_VALUE;
        this.position = pos;
        for (Vertex n : subNodes) {
            Point cur = new Point(n.getPosition());
            cur.translate(startX, startY);
            n.setPosition(cur);
            minY = Math.min(minY, cur.y);
        }

        for (Link e : subEdges) {
            List<Point> arr = e.getControlPoints();
            ArrayList<Point> newArr = new ArrayList<>(arr.size());
            for (Point p : arr) {
                if (p != null) {
                    Point p2 = new Point(p);
                    p2.translate(startX, startY);
                    newArr.add(p2);
                } else {
                    newArr.add(null);
                }
            }

            e.setControlPoints(newArr);
        }

        if (subNodes.isEmpty()) {
            minY = startY + 12;
        }
        for (Segment s : subSegments) {
            s.getStartPoint().translate(startX + cluster.getLiveRangeSeparation(), minY);
            s.getEndPoint().translate(startX + cluster.getLiveRangeSeparation(), minY);
        }
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster c) {
        cluster = c;
    }

    public void setRoot(boolean b) {
        root = b;
    }

    public boolean isRoot() {
        return root;
    }

    public int compareTo(Vertex o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public String toString() {
        return name;
    }

    public Set<? extends Vertex> getSubNodes() {
        return subNodes;
    }

    public List<? extends Segment> getSubSegments() {
        return subSegments;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ClusterNode other)) return false;
        return Objects.equals(this.name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
