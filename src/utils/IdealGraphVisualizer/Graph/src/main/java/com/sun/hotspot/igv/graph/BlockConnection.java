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
package com.sun.hotspot.igv.graph;

import com.sun.hotspot.igv.layout.Port;
import java.awt.Color;
import java.awt.Point;
import java.util.List;

public class BlockConnection implements Connection {

    @Override
    public boolean isVIP() {
        return style == ConnectionStyle.BOLD;
    }

    private Block sourceBlock;
    private Block destinationBlock;
    private Color color;
    private ConnectionStyle style;
    private List<Point> controlPoints;
    private String label;

    public BlockConnection(Block src, Block dst, String label) {
        this.sourceBlock = src;
        this.destinationBlock = dst;
        this.label = label;
        this.color = Color.BLACK;
        this.style = ConnectionStyle.NORMAL;
    }

    public Color getColor() {
        return color;
    }

    public ConnectionStyle getStyle() {
        return style;
    }

    public void setColor(Color c) {
        color = c;
    }

    public void setStyle(ConnectionStyle s) {
        style = s;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String getToolTipText() {
        return "B" + sourceBlock.getInputBlock().getName() + " → " +
               "B" + destinationBlock.getInputBlock().getName();
    }

    @Override
    public String toString() {
        return "BlockConnection('" + label + "', " + getFromCluster() + " to " + getToCluster() + ")";
    }

    @Override
    public Port getFrom() {
        return null;
    }

    @Override
    public Block getFromCluster() {
        return sourceBlock;
    }

    @Override
    public Port getTo() {
        return null;
    }

    @Override
    public Block getToCluster() {
        return destinationBlock;
    }

    @Override
    public List<Point> getControlPoints() {
        return controlPoints;
    }

    @Override
    public void setControlPoints(List<Point> list) {
        controlPoints = list;
    }

    @Override
    public boolean isAlwaysVisible() {
        return true;
    }

    @Override
    public boolean hasSlots() {
        return false;
    }
}
