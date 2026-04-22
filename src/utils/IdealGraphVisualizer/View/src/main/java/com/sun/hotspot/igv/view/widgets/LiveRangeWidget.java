/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.graph.Diagram;
import com.sun.hotspot.igv.graph.Figure;
import com.sun.hotspot.igv.graph.LiveRangeSegment;
import com.sun.hotspot.igv.util.DoubleClickAction;
import com.sun.hotspot.igv.util.DoubleClickHandler;
import com.sun.hotspot.igv.util.PropertiesConverter;
import com.sun.hotspot.igv.util.PropertiesSheet;
import com.sun.hotspot.igv.view.DiagramScene;
import com.sun.hotspot.igv.view.DiagramViewModel;
import com.sun.hotspot.igv.view.actions.CustomSelectAction;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JPopupMenu;
import org.netbeans.api.visual.action.ActionFactory;
import org.netbeans.api.visual.action.PopupMenuProvider;
import org.netbeans.api.visual.action.SelectProvider;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.Widget;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;

public class LiveRangeWidget extends Widget implements Properties.Provider, PopupMenuProvider, DoubleClickHandler {

    private final LiveRangeSegment liveRangeSegment;
    private final DiagramScene scene;
    private int length;
    private Rectangle clientArea;
    private final Node node;
    private static final float NORMAL_THICKNESS = 1.4f;
    private static final float SELECTED_THICKNESS = 2.2f;
    private boolean highlighted;
    private static final Color HIGHLIGHTED_COLOR = Color.BLUE;

    private static final int RANGE_WIDTH = 4;

    public LiveRangeWidget(LiveRangeSegment liveRangeSegment, DiagramScene scene, int length) {
        super(scene);
        this.liveRangeSegment = liveRangeSegment;
        this.scene = scene;
        this.length = length;

        getActions().addAction(new DoubleClickAction(this));
        getActions().addAction(ActionFactory.createPopupMenuAction(this));

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
        getActions().addAction(new CustomSelectAction(new SelectProvider() {
            @Override
            public boolean isAimingAllowed(Widget widget, Point localLocation, boolean invertSelection) {
                return true;
            }

            @Override
            public boolean isSelectionAllowed(Widget widget, Point localLocation, boolean invertSelection) {
                return true;
            }

            @Override
            public void select(Widget widget, Point localLocation, boolean invertSelection) {
                scene.userSelectionSuggested(liveRangeSegment.getSegmentSet(), invertSelection);
            }
        }));
    }

    public void setLength(int length) {
        this.length = length;
        updateClientArea();
    }

    private void updateClientArea() {
        clientArea = new Rectangle(RANGE_WIDTH * 2, length);
        clientArea.grow(RANGE_WIDTH * 2, RANGE_WIDTH * 2);
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
        boolean selected = scene.getSelectedObjects().contains(liveRangeSegment);
        g.setStroke(new BasicStroke(selected ? SELECTED_THICKNESS : NORMAL_THICKNESS));
        g.setColor(highlighted ? HIGHLIGHTED_COLOR : liveRangeSegment.getColor());
        if (highlighted) {
            g.setStroke(new BasicStroke(2));
        }
        int start = 0;
        int end = length;
        if (length == 0 && !liveRangeSegment.isInstantaneous()) {
            // Continuation segment in empty basic block.
            assert liveRangeSegment.getStart() == null && liveRangeSegment.getEnd() == null;
            start = -2;
            end = 3;
        }
        g.drawLine(0, start, 0, end);
        if (liveRangeSegment.isOpening()) {
            g.drawLine(-RANGE_WIDTH, 0, RANGE_WIDTH, 0);
        }
        if (liveRangeSegment.isClosing()) {
            g.drawLine(-RANGE_WIDTH, end, RANGE_WIDTH, end);
        }
    }

    @Override
    protected void notifyStateChanged(ObjectState previousState, ObjectState state) {
        super.notifyStateChanged(previousState, state);
        if (previousState.isHighlighted() != state.isHighlighted()) {
            for (LiveRangeSegment segment : liveRangeSegment.getSegmentSet()) {
                LiveRangeWidget figureWidget = scene.getWidget(segment);
                figureWidget.highlighted = state.isHighlighted();
                figureWidget.revalidate(true);
            }
        }
    }

    @Override
    public JPopupMenu getPopupMenu(Widget widget, Point point) {
        Diagram diagram = this.scene.getModel().getDiagram();
        InputGraph graph = diagram.getInputGraph();
        int liveRangeId = liveRangeSegment.getLiveRange().getId();

        JPopupMenu menu = scene.createPopupMenu();
        menu.addSeparator();
        Set<Figure> figures = new HashSet<>();
        for (InputNode node : graph.getRelatedNodes(liveRangeId)) {
            figures.add((diagram.getFigure(node)));
        }
        menu.add(scene.createGotoNodesAction("Select nodes", figures));
        menu.addSeparator();
        for (InputNode node : graph.getDefNodes(liveRangeId)) {
            menu.add(scene.createGotoAction(diagram.getFigure(node)));
        }
        menu.addSeparator();
        for (InputNode node : graph.getUseNodes(liveRangeId)) {
            menu.add(scene.createGotoAction(diagram.getFigure(node)));
        }
        return menu;
    }

    @Override
    public void handleDoubleClick(Widget w, WidgetAction.WidgetMouseEvent e) {
        DiagramViewModel model = this.scene.getModel();
        Set<Integer> nodes = new HashSet<>();
        InputGraph graph = model.getDiagram().getInputGraph();
        int liveRangeId = liveRangeSegment.getLiveRange().getId();
        for (InputNode node : graph.getRelatedNodes(liveRangeId)) {
            nodes.add(node.getId());
        }
        model.showOnly(nodes);
    }

    @Override
    public Properties getProperties() {
        return liveRangeSegment.getProperties();
    }
}
