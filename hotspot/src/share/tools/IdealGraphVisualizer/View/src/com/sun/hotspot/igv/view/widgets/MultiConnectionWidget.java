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

import com.sun.hotspot.igv.graph.Connection;
import com.sun.hotspot.igv.graph.InputSlot;
import com.sun.hotspot.igv.graph.OutputSlot;
import com.sun.hotspot.igv.graph.Slot;
import com.sun.hotspot.igv.view.DiagramScene;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.netbeans.api.visual.action.PopupMenuProvider;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.Widget;

/**
 *
 * @author Thomas Wuerthinger
 */
public class MultiConnectionWidget extends Widget implements PopupMenuProvider {

    public final int BORDER = 4;
    public final int HOVER_STROKE_WIDTH = 3;

    private static class Route {

        public Point from;
        public Point to;
        public SortedSet<InputSlot> inputSlots;
        public boolean decorateStart;
        public boolean decorateEnd;

        public Route(Point from, Point to) {
            assert from != null;
            assert to != null;
            this.from = from;
            this.to = to;
            this.inputSlots = new TreeSet<InputSlot>();
        }

        @Override
        public boolean equals(Object obj) {

            if (obj instanceof Route) {
                Route r = (Route) obj;
                return r.from.equals(from) && r.to.equals(to);
            }

            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            return ((((from.x * 1711) + from.y) * 1711 + to.x) * 1711 + to.y);
        }
    }
    private Rectangle clientArea;
    private OutputSlot outputSlot;
    private Map<Route, SortedSet<InputSlot>> routeMap;
    private List<Route> routeList;
    private Color color;
    private DiagramScene diagramScene;
    private boolean popupVisible;

    /** Creates a new instance of MultiConnectionWidget */
    public MultiConnectionWidget(OutputSlot outputSlot, DiagramScene scene) {
        super(scene);

        this.diagramScene = scene;
        this.outputSlot = outputSlot;
        this.setCheckClipping(true);

        routeMap = new HashMap<Route, SortedSet<InputSlot>>();
        routeList = new ArrayList<Route>();
        color = Color.BLACK;

        for (Connection c : outputSlot.getConnections()) {
            List<Point> controlPoints = c.getControlPoints();
            InputSlot inputSlot = (InputSlot) c.getTo();
            color = c.getColor();

            for (int i = 1; i < controlPoints.size(); i++) {
                Point prev = controlPoints.get(i - 1);
                Point cur = controlPoints.get(i);

                if (prev != null && cur != null) {
                    Route r = new Route(prev, cur);
                    if (routeMap.containsKey(r)) {
                        SortedSet<InputSlot> set = routeMap.get(r);
                        set.add(inputSlot);
                    } else {
                        SortedSet<InputSlot> set = new TreeSet<InputSlot>(Slot.slotFigureComparator);
                        set.add(inputSlot);
                        routeMap.put(r, set);
                        routeList.add(r);
                    }
                }
            }
        }

        if (routeList.size() == 0) {
            clientArea = new Rectangle();
        } else {
            for (Route r : routeList) {

                int x = r.from.x;
                int y = r.from.y;

                int x2 = r.to.x;
                int y2 = r.to.y;

                if (x > x2) {
                    int tmp = x;
                    x = x2;
                    x2 = tmp;
                }

                if (y > y2) {
                    int tmp = y;
                    y = y2;
                    y2 = tmp;
                }

                int width = x2 - x + 1;
                int height = y2 - y + 1;

                Rectangle rect = new Rectangle(x, y, width, height);
                if (clientArea == null) {
                    clientArea = rect;
                } else {
                    clientArea = clientArea.union(rect);
                }
            }

            clientArea.grow(BORDER, BORDER);
        }
    }

    private void setHoverPosition(Point location) {
        Route r = getNearest(location);
    }

    private Route getNearest(Point localLocation) {

        double minDist = Double.MAX_VALUE;
        Route result = null;
        for (Route r : routeList) {
            double dist = Line2D.ptSegDistSq((double) r.from.x, (double) r.from.y, (double) r.to.x, (double) r.to.y, (double) localLocation.x, (double) localLocation.y);
            if (dist < minDist) {
                result = r;
                minDist = dist;
            }
        }

        assert result != null;

        return result;
    }

    @Override
    public boolean isHitAt(Point localLocation) {
        if (!super.isHitAt(localLocation)) {
            return false;
        }

        for (Route r : routeList) {
            double dist = Line2D.ptSegDistSq((double) r.from.x, (double) r.from.y, (double) r.to.x, (double) r.to.y, (double) localLocation.x, (double) localLocation.y);
            if (dist < BORDER * BORDER) {
                setHoverPosition(localLocation);
                return true;
            }
        }

        return false;
    }

    @Override
    protected Rectangle calculateClientArea() {
        return clientArea;
    }

    @Override
    protected void paintWidget() {
        Graphics2D g = getScene().getGraphics();
        //g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(this.color);
        ObjectState state = this.getState();
        float width = 1.0f;
        if (state.isHovered() || this.popupVisible) {
            width = HOVER_STROKE_WIDTH;
        }

        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(width));
        for (Route r : routeList) {
            g.drawLine(r.from.x, r.from.y, r.to.x, r.to.y);
        }
        g.setStroke(oldStroke);
    }

    @Override
    protected void notifyStateChanged(ObjectState previousState, ObjectState state) {

        boolean repaint = false;

        if (state.isHovered() != previousState.isHovered()) {
            repaint = true;
        }

        repaint();
    }

    public JPopupMenu getPopupMenu(Widget widget, Point localLocation) {
        JPopupMenu menu = new JPopupMenu();
        Route r = getNearest(localLocation);
        assert r != null;
        assert routeMap.containsKey(r);

        menu.add(diagramScene.createGotoAction(outputSlot.getFigure()));
        menu.addSeparator();

        SortedSet<InputSlot> set = this.routeMap.get(r);
        for (InputSlot s : set) {
            menu.add(diagramScene.createGotoAction(s.getFigure()));
        }

        final MultiConnectionWidget w = this;
        menu.addPopupMenuListener(new PopupMenuListener() {

            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                w.popupVisible = true;
                w.repaint();
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                w.popupVisible = false;
                w.repaint();
            }

            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });

        return menu;
    }
}
