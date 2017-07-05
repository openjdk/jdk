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
package com.sun.hotspot.igv.view.widgets;

import com.sun.hotspot.igv.graph.Figure;
import com.sun.hotspot.igv.graph.OutputSlot;
import com.sun.hotspot.igv.graph.Slot;
import com.sun.hotspot.igv.view.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import org.netbeans.api.visual.widget.Widget;

/**
 *
 * @author Thomas Wuerthinger
 */
public abstract class SlotWidget extends Widget {

    private Slot slot;
    private FigureWidget figureWidget;
    private Image bufferImage;
    private static double TEXT_ZOOM_FACTOR = 0.9;
    private static double ZOOM_FACTOR = 0.6;
    private DiagramScene scene;

    public SlotWidget(Slot slot, DiagramScene scene, Widget parent, FigureWidget fw) {
        super(scene);
        this.scene = scene;
        this.slot = slot;
        figureWidget = fw;
        this.setToolTipText("<HTML>" + slot.getName() + "</HTML>");
        this.setCheckClipping(true);
    }

    public Point getAnchorPosition() {
        Point p = new Point(figureWidget.getFigure().getPosition());
        Point p2 = slot.getRelativePosition();
        p.translate(p2.x, p2.y);
        return p;
    }

    protected void init() {

        Point p = calculateRelativeLocation();
        Rectangle r = calculateClientArea();
        p = new Point(p.x, p.y - r.height / 2);
        this.setPreferredLocation(p);
    }

    public Slot getSlot() {
        return slot;
    }

    public FigureWidget getFigureWidget() {
        return figureWidget;
    }

    @Override
    protected void paintWidget() {

        if (scene.getRealZoomFactor() < ZOOM_FACTOR) {
            return;
        }

        if (bufferImage == null) {
            Graphics2D g = this.getGraphics();
            g.setColor(Color.DARK_GRAY);
            int w = this.getBounds().width;
            int h = this.getBounds().height;

            if (getSlot().getShortName() != null && getSlot().getShortName().length() > 0 && scene.getRealZoomFactor() >= TEXT_ZOOM_FACTOR) {
                Font f = new Font("Arial", Font.PLAIN, 8);
                g.setFont(f.deriveFont(7.5f));
                Rectangle2D r1 = g.getFontMetrics().getStringBounds(getSlot().getShortName(), g);
                g.drawString(getSlot().getShortName(), (int) (this.getBounds().width - r1.getWidth()) / 2, (int) (this.getBounds().height + r1.getHeight()) / 2);
            } else {

                if (slot instanceof OutputSlot) {
                    g.fillArc(w / 4, -h / 4 - 1, w / 2, h / 2, 180, 180);
                } else {
                    g.fillArc(w / 4, 3 * h / 4, w / 2, h / 2, 0, 180);
                }
            }
        }
    }

    @Override
    protected Rectangle calculateClientArea() {
        return new Rectangle(0, 0, Figure.SLOT_WIDTH, Figure.SLOT_WIDTH);
    }

    protected abstract Point calculateRelativeLocation();

    protected double calculateRelativeY(int size, int index) {
        assert index >= 0 && index < size;
        assert size > 0;
        double height = getFigureWidget().getBounds().getHeight();
        return height * (index + 1) / (size + 1);
    }
}
