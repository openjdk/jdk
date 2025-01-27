/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.graph.LiveRangeSegment;
import com.sun.hotspot.igv.util.PropertiesConverter;
import com.sun.hotspot.igv.util.PropertiesSheet;
import com.sun.hotspot.igv.view.DiagramScene;
import java.awt.*;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.Widget;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;

public class LiveRangeWidget extends Widget implements Properties.Provider {

    private final LiveRangeSegment liveRangeSegment;
    private final DiagramScene scene;
    private int length;
    private LiveRangeWidget next;
    private Rectangle clientArea;
    private final Node node;
    private static final float NORMAL_THICKNESS = 1.4f;
    private static final float SELECTED_THICKNESS = 2.2f;
    private boolean highlighted;
    private boolean selected;
    private static final Color NORMAL_COLOR = Color.BLACK;
    private static final Color HIGHLIGHTED_COLOR = Color.BLUE;

    private static final int RANGE_WIDTH = 4;

    public LiveRangeWidget(LiveRangeSegment liveRangeSegment, DiagramScene scene, int length, LiveRangeWidget next) {
        super(scene);
        this.liveRangeSegment = liveRangeSegment;
        this.scene = scene;
        this.length = length;
        this.next = next;

        updateClientArea();

        // Initialize node for property sheet
        node = new AbstractNode(Children.LEAF) {
            @Override
            protected Sheet createSheet() {
                Sheet s = super.createSheet();
                PropertiesSheet.initializeSheet(liveRangeSegment.getProperties(), s);
                return s;
            }
        };
        node.setDisplayName("L" + liveRangeSegment.getLiveRange().getId());

        this.setToolTipText(PropertiesConverter.convertToHTML(liveRangeSegment.getProperties()));
    }

    public void setLength(int length) {
        this.length = length;
        updateClientArea();
    }

    private void updateClientArea() {
        clientArea = new Rectangle(RANGE_WIDTH * 2, length);
        clientArea.grow(RANGE_WIDTH * 2, RANGE_WIDTH * 2);
    }

    public void setNext(LiveRangeWidget next) {
        this.next = next;
    }

    @Override
    protected Rectangle calculateClientArea() {
        return clientArea;
    }

    @Override
    protected void paintWidget() {
        if (scene.getZoomFactor() < 0.1) {
            return;
        }
        Graphics2D g = getScene().getGraphics();
        g.setPaint(this.getBackground());
        g.setStroke(new BasicStroke(selected ? SELECTED_THICKNESS : NORMAL_THICKNESS));
        g.setColor(highlighted ? HIGHLIGHTED_COLOR : NORMAL_COLOR);
        g.drawLine(- RANGE_WIDTH, 0, RANGE_WIDTH, 0);
        if (length != 0) {
            g.drawLine(0, 0, 0, length);
            g.drawLine(- RANGE_WIDTH, length, RANGE_WIDTH, length);
        }
    }

    @Override
    protected void notifyStateChanged(ObjectState previousState, ObjectState state) {
        super.notifyStateChanged(previousState, state);
        if (previousState.isSelected() != state.isSelected()) {
            setSelected(state.isSelected());
        }
        if (previousState.isHighlighted() != state.isHighlighted()) {
            setHighlighted(state.isHighlighted());
        }
    }

    private void setSelected(boolean enable) {
        if (enable == selected) {
            return; // end recursion
        }
        selected = enable;
        revalidate(true);
        if (next != null) {
            next.setSelected(enable);
        }
    }

    private void setHighlighted(boolean enable) {
        if (enable == highlighted) {
            return; // end recursion
        }
        highlighted = enable;
        revalidate(true);
        if (next != null) {
            next.setHighlighted(enable);
        }
    }

    @Override
    public Properties getProperties() {
        return liveRangeSegment.getProperties();
    }
}
