/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.data.ChangedEvent;
import com.sun.hotspot.igv.data.InputLiveRange;
import com.sun.hotspot.igv.data.InputNode;
import java.awt.*;
import java.util.Collection;
import javax.swing.JComponent;
import org.openide.awt.UndoRedo;
import org.openide.util.Lookup;

/**
 *
 * @author Thomas Wuerthinger
 */
public interface DiagramViewer {

    enum InteractionMode {
        SELECTION,
        PANNING,
    }

    DiagramViewModel getModel();

    void paint(Graphics2D generator);

    Lookup getLookup();

    JComponent createSatelliteView();

    Component getComponent();

    double getZoomMinFactor();

    double getZoomMaxFactor();

    void zoomOut(Point zoomCenter, double speed);

    void zoomIn(Point zoomCenter, double speed);

    void setZoomPercentage(int percentage);

    int getZoomPercentage();

    ChangedEvent<DiagramViewer> getZoomChangedEvent();

    void resetUndoRedoManager();

    UndoRedo getUndoRedo();

    void componentHidden();

    void componentShowing();

    void centerSelectedFigures();

    void centerSelectedLiveRanges();

    void addSelectedNodes(Collection<InputNode> nodes, boolean showIfHidden);

    void addSelectedLiveRanges(Collection<InputLiveRange> liveRanges, boolean showIfHidden);

    void addSelectedElements(Collection<InputNode> nodes, Collection<InputLiveRange> liveRanges, boolean showIfHidden);

    void clearSelectedElements();

    void setInteractionMode(InteractionMode mode);

    Rectangle getBounds();

    JComponent getView();

    void colorSelectedFigures(Color color);
}
