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
package com.sun.hotspot.igv.view.widgets;

import com.sun.hotspot.igv.graph.Diagram;
import com.sun.hotspot.igv.graph.Figure;
import com.sun.hotspot.igv.graph.Slot;
import com.sun.hotspot.igv.util.DoubleClickHandler;
import com.sun.hotspot.igv.view.DiagramScene;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.Widget;

/**
 *
 * @author Thomas Wuerthinger
 */
public abstract class SlotWidget extends Widget implements DoubleClickHandler {

    private Slot slot;
    private FigureWidget figureWidget;
    protected static double TEXT_ZOOM_FACTOR = 0.9;
    protected static double ZOOM_FACTOR = 0.6;
    private DiagramScene diagramScene;

    public SlotWidget(Slot slot, DiagramScene scene, FigureWidget fw) {
        super(scene);
        this.diagramScene = scene;
        this.slot = slot;
        figureWidget = fw;
        if (slot.hasSourceNodes()) {
            this.setToolTipText("<HTML>" + slot.getToolTipText() + "</HTML>");
        }
        // No clipping, to let input slots draw gap markers outside their bounds.
        this.setCheckClipping(false);
        fw.addChild(this);
        if (slot.shouldShowName()) {
            Point p = slot.getRelativePosition();
            p.x -= slot.getWidth() / 2;
            p.y -= slot.getHeight() / 2;
            p.y += yOffset();
            this.setPreferredLocation(p);
        }
    }

    @Override
    protected void notifyStateChanged(ObjectState previousState, ObjectState state) {
        super.notifyStateChanged(previousState, state);
        repaint();
    }

    public Slot getSlot() {
        return slot;
    }

    public FigureWidget getFigureWidget() {
        return figureWidget;
    }

    @Override
    protected void paintWidget() {

        if (getScene().getZoomFactor() < ZOOM_FACTOR) {
            return;
        }

        Graphics2D g = this.getGraphics();
        // g.setColor(Color.DARK_GRAY);
        int w = this.getBounds().width;
        int h = this.getBounds().height;

        if (getSlot().hasSourceNodes()) {
            final int SMALLER = 0;
            g.setColor(getSlot().getColor());

            int FONT_OFFSET = 2;

            int s = h - SMALLER;
            int rectW = s;

            Font font = Diagram.SLOT_FONT;
            if (this.getState().isSelected()) {
                font = font.deriveFont(Font.BOLD);
                g.setStroke(new BasicStroke(1.5f));
            } else {
                g.setStroke(new BasicStroke(1f));
            }

            if (getSlot().shouldShowName()) {
                g.setFont(font);
                Rectangle2D r1 = g.getFontMetrics().getStringBounds(getSlot().getShortName(), g);
                rectW = (int) r1.getWidth() + FONT_OFFSET * 2;
            }
            g.fillRect(w / 2 - rectW / 2, 0, rectW - 1, s - 1);

            if (this.getState().isHighlighted()) {
                g.setColor(Color.BLUE);
            } else {
                g.setColor(Color.BLACK);
            }
            g.drawRect(w / 2 - rectW / 2, 0, rectW - 1, s - 1);

            if (getSlot().shouldShowName() && getScene().getZoomFactor() >= TEXT_ZOOM_FACTOR) {
                Rectangle2D r1 = g.getFontMetrics().getStringBounds(getSlot().getShortName(), g);
                g.drawString(getSlot().getShortName(), (int) (w - r1.getWidth()) / 2, g.getFontMetrics().getAscent() - 1);//(int) (r1.getHeight()));
            }

        } else {

            if (this.getSlot().getConnections().isEmpty() &&
                !getFigureWidget().getFigure().getDiagram().isCFG()) {
                if (this.getState().isHighlighted()) {
                    g.setColor(Color.BLUE);
                } else {
                    g.setColor(Color.BLACK);
                }
                int r = 2;
                g.fillOval(w / 2 - r, h / 2 - r, 2 * r, 2 * r);
            }
        }
    }

    @Override
    protected Rectangle calculateClientArea() {
        return new Rectangle(0, 0, slot.getWidth(), slot.getHeight());
    }

    protected abstract int yOffset();

    @Override
    public void handleDoubleClick(Widget w, WidgetAction.WidgetMouseEvent e) {
        Set<Integer> hiddenNodes = new HashSet<>(diagramScene.getModel().getHiddenNodes());
        if (diagramScene.isAllVisible()) {
            hiddenNodes = new HashSet<>(diagramScene.getModel().getGroup().getAllNodes());
        }

        boolean progress = false;
        for (Figure f : diagramScene.getModel().getDiagram().getFigures()) {
            for (Slot s : f.getSlots()) {
                if (!Collections.disjoint(s.getSource().getSourceNodesAsSet(), slot.getSource().getSourceNodesAsSet())) {
                    progress = true;
                    hiddenNodes.remove(f.getInputNode().getId());
                }
            }
        }

        if (progress) {
            this.diagramScene.getModel().setHiddenNodes(hiddenNodes);
        }
    }
}
