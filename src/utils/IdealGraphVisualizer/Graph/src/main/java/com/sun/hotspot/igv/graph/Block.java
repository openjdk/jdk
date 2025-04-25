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
package com.sun.hotspot.igv.graph;

import com.sun.hotspot.igv.data.InputBlock;
import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.layout.Cluster;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.*;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Block implements Cluster {

    protected final InputBlock inputBlock;
    private Rectangle bounds;
    private final Diagram diagram;
    private List<Integer> liveRangeIds;
    int liveRangeSeparation = -1;

    public Block(InputBlock inputBlock, Diagram diagram) {
        this.inputBlock = inputBlock;
        this.diagram = diagram;
    }

    public InputBlock getInputBlock() {
        return inputBlock;
    }

    public Set<? extends Cluster> getSuccessors() {
        Set<Block> succs = new HashSet<Block>();
        for (InputBlock b : inputBlock.getSuccessors()) {
            if (diagram.hasBlock(b)) {
                succs.add(diagram.getBlock(b));
            }
        }
        return succs;
    }

    public List<? extends Vertex> getVertices() {
        List<Vertex> vertices = new ArrayList<>();
        for (InputNode inputNode : inputBlock.getNodes()) {
            if (diagram.hasFigure(inputNode)) {
                vertices.add(diagram.getFigure(inputNode));
            }
        }
        return vertices;
    }

    public int getLiveRangeSeparation() {
        assert liveRangeSeparation > 0;
        return liveRangeSeparation;
    }

    public void setBounds(Rectangle r) {
        this.bounds = r;
    }

    @Override
    public void setPosition(Point p) {
        if (bounds != null) {
            bounds.setLocation(p);
        }
    }

    @Override
    public Point getPosition() {
        return bounds.getLocation();
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    public List<Integer> getLiveRangeIds() {
        return liveRangeIds;
    }

    public void setLiveRangeIds(List<Integer> liveRangeIds) {
        this.liveRangeIds = liveRangeIds;
        int extraDigits = 0;
        if (!liveRangeIds.isEmpty()) {
            extraDigits = (int)java.lang.Math.log10(Collections.max(liveRangeIds));
        }
        liveRangeSeparation = 20 + extraDigits * 7;
    }

    public int compareTo(Cluster o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public String toString() {
        return inputBlock.getName();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Block other = (Block) obj;
        return inputBlock.equals(other.inputBlock);
    }

    @Override
    public int hashCode() {
        return inputBlock.hashCode();
    }
}

