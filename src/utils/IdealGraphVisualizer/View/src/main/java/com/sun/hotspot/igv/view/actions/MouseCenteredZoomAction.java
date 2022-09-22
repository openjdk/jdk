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
package com.sun.hotspot.igv.view.actions;

import com.sun.hotspot.igv.view.DiagramScene;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.widget.Widget;
import org.openide.util.Utilities;


public class MouseCenteredZoomAction extends WidgetAction.Adapter {

    private static final int modifiers = Utilities.isMac() ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK;
    private final DiagramScene scene;

    public MouseCenteredZoomAction(DiagramScene scene) {
        this.prev_n = 0;
        this.scene = scene;
    }

    private int prev_n;
    @Override
    public State mouseWheelMoved(Widget widget, WidgetMouseWheelEvent event) {
        if ((event.getModifiersEx() & modifiers) != modifiers) {
            // If modifier key is not pressed, use wheel for panning
            JComponent view = scene.getView();
            Rectangle visibleRect = view.getVisibleRect();
            int amount = event.getWheelRotation() * 64;
            switch (event.getModifiers() & 11) {
                case 0:
                    visibleRect.y += amount;
                    break;
                case 1:
                    visibleRect.x += amount;
                    break;
                default:
                    return State.REJECTED;
            }
            view.scrollRectToVisible(visibleRect);
            return State.CONSUMED;
        }

        Point mouseLocation = widget.convertLocalToScene(event.getPoint());
        int n = event.getWheelRotation();
        if (n > 0) {
            if (prev_n == n) {
                scene.zoomOut(mouseLocation);
            } else {
                prev_n = n;
            }
        } else if (n < 0) {
            if (prev_n == n) {
                scene.zoomIn(mouseLocation);
            } else {
                prev_n = n;
            }
        }
        return State.CONSUMED;
    }
}
