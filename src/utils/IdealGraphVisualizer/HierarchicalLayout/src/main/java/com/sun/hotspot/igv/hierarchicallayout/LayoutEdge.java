/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.layout.Link;

public class LayoutEdge {

    private LayoutNode from;
    private LayoutNode to;
    // Horizontal distance relative to start of 'from'.
    private int relativeFromX;
    // Horizontal distance relative to start of 'to'.
    private int relativeToX;
    private Link link;
    private boolean isReversed;

    public int getStartX() {
        return relativeFromX + from.getLeft();
    }

    public int getEndX() {
        return relativeToX + to.getLeft();
    }

    public LayoutEdge(LayoutNode from, LayoutNode to) {
        this.from = from;
        this.to = to;
        this.isReversed = false;
    }

    public LayoutEdge(LayoutNode from, LayoutNode to, int relativeFromX, int relativeToX, Link link) {
        this(from, to);
        this.relativeFromX = relativeFromX;
        this.relativeToX = relativeToX;
        this.link = link;
    }

    public void reverse() {
        isReversed = !isReversed;
    }

    public boolean isReversed() {
        return isReversed;
    }

    @Override
    public String toString() {
        return "Edge " + from + ", " + to;
    }

    public LayoutNode getFrom() {
        return from;
    }

    public void setFrom(LayoutNode from) {
        this.from = from;
    }

    public LayoutNode getTo() {
        return to;
    }

    public void setTo(LayoutNode to) {
        this.to = to;
    }

    public int getFromX() {
        return from.getX() + getRelativeFromX();
    }

    public int getToX() {
        return to.getX() + getRelativeToX();
    }

    public int getRelativeFromX() {
        return relativeFromX;
    }
    public void setRelativeFromX(int relativeFromX) {
        this.relativeFromX = relativeFromX;
    }

    public int getRelativeToX() {
        return relativeToX;
    }

    public void setRelativeToX(int relativeToX) {
        this.relativeToX = relativeToX;
    }

    public Link getLink() {
        return link;
    }

    public void setLink(Link link) {
        this.link = link;
    }
}
