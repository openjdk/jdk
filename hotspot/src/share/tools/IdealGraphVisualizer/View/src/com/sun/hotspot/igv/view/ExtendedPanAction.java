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
package com.sun.hotspot.igv.view;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import org.netbeans.api.visual.widget.Scene;
import org.netbeans.api.visual.widget.Widget;
import org.netbeans.api.visual.action.WidgetAction;

import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * @author David Kaspar
 * @author Thomas Wuerthinger
 */
public class ExtendedPanAction extends WidgetAction.LockedAdapter {

    private Scene scene;
    private JScrollPane scrollPane;
    private Point lastLocation;

    protected boolean isLocked() {
        return scrollPane != null;
    }

    @Override
    public State mousePressed(Widget widget, WidgetMouseEvent event) {
        if (event.getButton() == MouseEvent.BUTTON2 || event.getButton() == MouseEvent.BUTTON1 && ((event.getModifiers() & MouseEvent.CTRL_MASK) != 0)) {
            scene = widget.getScene();
            scrollPane = findScrollPane(scene.getView());
            if (scrollPane != null) {
                lastLocation = scene.convertSceneToView(widget.convertLocalToScene(event.getPoint()));
                SwingUtilities.convertPointToScreen(lastLocation, scrollPane.getViewport().getView());
                return State.createLocked(widget, this);
            }
        }
        return State.REJECTED;
    }

    private JScrollPane findScrollPane(JComponent component) {
        for (;;) {
            if (component == null) {
                return null;
            }
            if (component instanceof JScrollPane) {
                return ((JScrollPane) component);
            }
            Container parent = component.getParent();
            if (!(parent instanceof JComponent)) {
                return null;
            }
            component = (JComponent) parent;
        }
    }

    @Override
    public State mouseReleased(Widget widget, WidgetMouseEvent event) {
        boolean state = pan(widget, event.getPoint());
        if (state) {
            scrollPane = null;
        }
        return state ? State.createLocked(widget, this) : State.REJECTED;
    }

    @Override
    public State mouseDragged(Widget widget, WidgetMouseEvent event) {
        return pan(widget, event.getPoint()) ? State.createLocked(widget, this) : State.REJECTED;
    }

    private boolean pan(Widget widget, Point newLocation) {
        if (scrollPane == null || scene != widget.getScene()) {
            return false;
        }
        newLocation = scene.convertSceneToView(widget.convertLocalToScene(newLocation));
        SwingUtilities.convertPointToScreen(newLocation, scrollPane.getViewport().getView());
        Point viewPosition = scrollPane.getViewport().getViewPosition();
        Dimension viewSize = scrollPane.getViewport().getViewSize();
        Dimension viewPortSize = scrollPane.getViewport().getSize();

        int xOffset = lastLocation.x - newLocation.x;
        int yOffset = lastLocation.y - newLocation.y;

        if (viewPortSize.height == viewSize.height) {
            yOffset = 0;
        }

        if (viewPortSize.width == viewSize.width) {
            xOffset = 0;
        }

        if (xOffset == 0 && yOffset == 0) {
            return true;
        }
        viewPosition = new Point(viewPosition.x + xOffset, viewPosition.y + yOffset);
        viewPosition.x = Math.max(viewPosition.x, 0);
        viewPosition.y = Math.max(viewPosition.y, 0);
        viewPosition.x = Math.min(viewPosition.x, scrollPane.getViewport().getView().getSize().width - scrollPane.getViewport().getSize().width);
        viewPosition.y = Math.min(viewPosition.y, scrollPane.getViewport().getView().getSize().height - scrollPane.getViewport().getSize().height);

        scrollPane.getViewport().setViewPosition(viewPosition);
        scrollPane.getViewport().getView().repaint();
        lastLocation = newLocation;
        return true;
    }
}
