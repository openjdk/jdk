/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.*;

import com.sun.hotspot.igv.view.EditorTopComponent;
import org.openide.util.ImageUtilities;

/**
 *
 * @author Thomas Wuerthinger
 */
public class OverviewAction extends AbstractAction {
    private static final String SATELLITE_STRING = "satellite";
    private static final String SCENE_STRING = "scene";

    public OverviewAction(JPanel panel) {
        int keyCode = KeyEvent.VK_S;
        panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(keyCode, 0, false), SATELLITE_STRING);
        panel.getActionMap().put(SATELLITE_STRING,
                new AbstractAction(SATELLITE_STRING) {
                    @Override public void actionPerformed(ActionEvent e) {
                        OverviewAction.this.setSelected(true);
                    }
                });
        panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(keyCode, 0, true), SCENE_STRING);
        panel.getActionMap().put(SCENE_STRING,
                new AbstractAction(SCENE_STRING) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        OverviewAction.this.setSelected(false);
                    }
                });

        putValue(AbstractAction.SMALL_ICON, new ImageIcon(ImageUtilities.loadImage(iconResource())));
        putValue(Action.SELECTED_KEY, false);
        putValue(Action.SHORT_DESCRIPTION, "Show satellite view of whole graph (hold S-KEY)");
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            boolean selected = (boolean)getValue(SELECTED_KEY);
            editor.showSatellite(selected);
        }
    }

    public void setSelected(boolean selected) {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            putValue(Action.SELECTED_KEY, selected);
            editor.showSatellite(selected);
        }
    }

    protected String iconResource() {
        return "com/sun/hotspot/igv/view/images/overview.png";
    }
}
