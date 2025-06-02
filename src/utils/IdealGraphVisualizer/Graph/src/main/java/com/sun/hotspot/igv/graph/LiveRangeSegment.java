/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.data.InputLiveRange;
import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.layout.Segment;
import java.awt.Color;
import java.awt.Point;
import java.util.Set;

public class LiveRangeSegment extends Properties.Entity implements Segment {

    private InputLiveRange liveRange;
    private Block block;
    private Figure start;
    private Figure end;
    private Point startPoint;
    private Point endPoint;
    private boolean lastOfLiveRange;
    private boolean instantaneous;
    private boolean opening;
    private boolean closing;
    private Set<LiveRangeSegment> segmentSet;
    private Color color;

    protected LiveRangeSegment(InputLiveRange liveRange, Block block, Figure start, Figure end) {
        this.block = block;
        this.liveRange = liveRange;
        this.start = start;
        this.end = end;
        assert(start == null || end == null || (start.getBlock() == end.getBlock()));
        lastOfLiveRange = true;
        this.color = Color.BLACK;
    }

    public InputLiveRange getLiveRange() {
        return liveRange;
    }

    public Block getCluster() {
        return block;
    }

    public Figure getStart() {
        return start;
    }

    public Figure getEnd() {
        return end;
    }

    public Point getStartPoint() {
        return startPoint;
    }

    public void setStartPoint(Point startPoint) {
        this.startPoint = startPoint;
    }

    public Point getEndPoint() {
        return endPoint;
    }

    public void setEndPoint(Point endPoint) {
        this.endPoint = endPoint;
    }

    public void setLastOfLiveRange(boolean lastOfLiveRange) {
        this.lastOfLiveRange = lastOfLiveRange;
    }

    public boolean isLastOfLiveRange() {
        return lastOfLiveRange;
    }

    public int parentId() {
        return this.liveRange.getId();
    }

    public void setInstantaneous(boolean instantaneous) {
        this.instantaneous = instantaneous;
    }

    public boolean isInstantaneous() {
        return instantaneous;
    }

    public void setOpening(boolean opening) {
        this.opening = opening;
    }

    public boolean isOpening() {
        return opening;
    }

    public void setClosing(boolean closing) {
        this.closing = closing;
    }

    public boolean isClosing() {
        return closing;
    }

    public Set<LiveRangeSegment> getSegmentSet() {
        return segmentSet;
    }

    public void setSegmentSet(Set<LiveRangeSegment> segmentSet) {
        this.segmentSet = segmentSet;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    @Override
    public String toString() {
        return "LiveRangeSegment(" + liveRange + "@B" + block + ", " + start + ", " + end + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LiveRangeSegment)) {
            return false;
        }
        LiveRangeSegment other = (LiveRangeSegment)o;
        if (getStart() == null && other.getStart() != null) {
            return false;
        }
        if (getStart() != null && other.getStart() == null) {
            return false;
        }
        if (getEnd() == null && other.getEnd() != null) {
            return false;
        }
        if (getEnd() != null && other.getEnd() == null) {
            return false;
        }
        return getLiveRange().equals(((LiveRangeSegment)o).getLiveRange())
            && (getStart() == null || getStart().equals(((LiveRangeSegment)o).getStart()))
            && (getEnd() == null || getEnd().equals(((LiveRangeSegment)o).getEnd()));
    }

    @Override
    public Properties getProperties() {
        return liveRange.getProperties();
    }

}
