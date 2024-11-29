/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.hotspot.igv.layout.Port;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.Dimension;
import java.awt.Point;
import java.util.Objects;

/**
 *
 * @author Thomas Wuerthinger
 */
public class ClusterInputSlotNode implements Vertex {

    private Point position;
    private final Port inputSlot;
    private final Port outputSlot;
    private final ClusterNode blockNode;

    private final String id;

    @Override
    public String toString() {
        return id;
    }

    public ClusterInputSlotNode(ClusterNode n, String id) {
        this.blockNode = n;
        this.id = id;
        this.position = new Point(0, 0);

        n.addSubNode(this);

        final Vertex thisNode = this;

        outputSlot = new Port() {

            public Point getRelativePosition() {
                return new Point(0, 0);
            }

            public Vertex getVertex() {
                return thisNode;
            }

            @Override
            public String toString() {
                return "OutPort of " + thisNode;
            }
        };

        inputSlot = new Port() {

            public Point getRelativePosition() {
                Point p = new Point(thisNode.getPosition());
                p.x += ClusterNode.PADDING;
                p.y = 0;
                return p;
            }

            public Vertex getVertex() {
                return blockNode;
            }

            @Override
            public String toString() {
                return "InPort of " + thisNode;
            }
        };
    }

    public Port getInputSlot() {
        return inputSlot;
    }

    public Port getOutputSlot() {
        return outputSlot;
    }

    public Dimension getSize() {
        return new Dimension(0, 0);
    }

    public void setPosition(Point p) {
        this.position = p;
    }

    public Point getPosition() {
        return position;
    }

    public Cluster getCluster() {
        return null;
    }

    public boolean isRoot() {
        return true;
    }

    public int compareTo(Vertex o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ClusterInputSlotNode other)) return false;
        return Objects.equals(this.id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
