/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.controlflow;

import com.sun.hotspot.igv.data.InputBlockEdge;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Port;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import org.netbeans.api.visual.widget.ConnectionWidget;

/**
 *
 * @author Thomas Wuerthinger
 */
public class BlockConnectionWidget extends ConnectionWidget implements Link {

    private BlockWidget from;
    private BlockWidget to;
    private Port inputSlot;
    private Port outputSlot;
    private List<Point> points;
    private InputBlockEdge edge;

    public BlockConnectionWidget(ControlFlowScene scene, InputBlockEdge edge) {
        super(scene);

        this.edge = edge;
        this.from = (BlockWidget) scene.findWidget(edge.getFrom());
        this.to = (BlockWidget) scene.findWidget(edge.getTo());
        inputSlot = to.getInputSlot();
        outputSlot = from.getOutputSlot();
        points = new ArrayList<Point>();
    }

    public InputBlockEdge getEdge() {
        return edge;
    }

    public Port getTo() {
        return inputSlot;
    }

    public Port getFrom() {
        return outputSlot;
    }

    public void setControlPoints(List<Point> p) {
        this.points = p;
    }

    @Override
    public List<Point> getControlPoints() {
        return points;
    }

    @Override
    public String toString() {
        return "Connection[ " + from.toString() + " - " + to.toString() + "]";
    }
}
