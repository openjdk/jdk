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
import com.sun.hotspot.igv.view.DiagramScene;
import com.sun.hotspot.igv.view.SlotLayout;
import com.sun.hotspot.igv.util.DoubleClickHandler;
import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.util.PropertiesSheet;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import org.netbeans.api.visual.action.PopupMenuProvider;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.Widget;
import org.netbeans.api.visual.layout.LayoutFactory;
import org.netbeans.api.visual.widget.LabelWidget;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;

/**
 *
 * @author Thomas Wuerthinger
 */
public class FigureWidget extends Widget implements Properties.Provider, PopupMenuProvider, DoubleClickHandler {

    public static final boolean VERTICAL_LAYOUT = true;
    public static final int DEPTH = 5;
    public static final int MAX_STRING_LENGTH = 20;
    private static final double LABEL_ZOOM_FACTOR = 0.3;
    private static final double ZOOM_FACTOR = 0.1;
    private Font font;
    private Font boldFont;
    private Figure figure;
    private Widget leftWidget;
    private Widget rightWidget;
    private Widget middleWidget;
    private ArrayList<LabelWidget> labelWidgets;
    private DiagramScene diagramScene;
    private boolean boundary;
    private Node node;

    public void setBoundary(boolean b) {
        boundary = b;
    }

    public boolean isBoundary() {
        return boundary;
    }

    public Node getNode() {
        return node;
    }

    private String shortenString(String string) {
        if (string.length() > MAX_STRING_LENGTH) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < string.length(); i++) {
                char c = string.charAt(i);
                if (!Character.isLetter(c) || Character.isUpperCase(c)) {
                    sb.append(c);
                }
            }
            string = sb.toString();
        }
        return string;
    }

    public FigureWidget(final Figure f, DiagramScene s, Widget parent) {

        super(s);


        font = f.getDiagram().getFont();
        boldFont = f.getDiagram().getFont().deriveFont(Font.BOLD);
        this.setCheckClipping(true);
        this.diagramScene = s;

        parent.addChild(this);
        this.figure = f;
        this.resolveBounds(null, calculateClientArea());

        leftWidget = new Widget(s);
        this.addChild(leftWidget);
        leftWidget.setLayout(new SlotLayout(SlotLayout.HorizontalAlignment.Right, VERTICAL_LAYOUT));//LayoutFactory.createVerticalFlowLayout(LayoutFactory.SerialAlignment.JUSTIFY, 0));

        middleWidget = new Widget(s);
        this.addChild(middleWidget);

        if (VERTICAL_LAYOUT) {
            this.setLayout(LayoutFactory.createVerticalFlowLayout());
        } else {
            this.setLayout(LayoutFactory.createHorizontalFlowLayout());
        }

        middleWidget.setLayout(LayoutFactory.createVerticalFlowLayout());

        middleWidget.setBackground(f.getColor());
        middleWidget.setOpaque(true);
        assert this.getScene() != null;
        assert this.getScene().getView() != null;
        middleWidget.setBorder(BorderFactory.createLineBorder(Color.BLACK));


        labelWidgets = new ArrayList<LabelWidget>();

        String[] strings = figure.getLines();

        for (String cur : strings) {

            String displayString = cur;

            LabelWidget lw = new LabelWidget(s);
            labelWidgets.add(lw);
            middleWidget.addChild(lw);
            lw.setLabel(displayString);

            lw.setFont(font);
            lw.setForeground(Color.BLACK);
            lw.setAlignment(LabelWidget.Alignment.CENTER);
            lw.setVerticalAlignment(LabelWidget.VerticalAlignment.CENTER);
            lw.setMaximumSize(new Dimension(f.getWidth(), 20000));
            lw.setMinimumSize(new Dimension(f.getWidth(), 20));
        }

        rightWidget = new Widget(s);
        this.addChild(rightWidget);
        rightWidget.setLayout(new SlotLayout(SlotLayout.HorizontalAlignment.Left, VERTICAL_LAYOUT));//LayoutFactory.createVerticalLayout(LayoutFactory.SerialAlignment.JUSTIFY, 0));

        // Initialize node for property sheet
        node = new AbstractNode(Children.LEAF) {

            @Override
            protected Sheet createSheet() {
                Sheet s = super.createSheet();
                PropertiesSheet.initializeSheet(f.getProperties(), s);
                return s;
            }
        };
        node.setDisplayName(getName());
    }
    private boolean firstTime = true;

    @Override
    protected void paintWidget() {
        if (firstTime) {
            firstTime = false;
            for (LabelWidget w : labelWidgets) {
                String cur = w.getLabel();
                Graphics graphics = this.getGraphics();
                if (graphics.getFontMetrics().stringWidth(cur) > figure.getWidth()) {
                    w.setLabel(shortenString(cur));
                }
            }
        }
        super.paintWidget();
    }

    public Widget getLeftWidget() {
        return leftWidget;
    }

    public Widget getRightWidget() {
        return rightWidget;
    }

    @Override
    protected void notifyStateChanged(ObjectState previousState, ObjectState state) {
        super.notifyStateChanged(previousState, state);

        Color borderColor = Color.BLACK;
        int thickness = 1;
        boolean repaint = false;
        Font f = font;
        if (state.isSelected()) {
            thickness = 1;
            f = boldFont;
        }

        if (state.isHovered()) {
            borderColor = Color.BLUE;
        }

        if (state.isHovered() != previousState.isHovered()) {
            repaint = true;
        }

        if (state.isSelected() != previousState.isSelected()) {
            repaint = true;
        }

        if (repaint) {
            middleWidget.setBorder(BorderFactory.createLineBorder(borderColor, thickness));
            for (LabelWidget labelWidget : labelWidgets) {
                labelWidget.setFont(f);
            }
            repaint();
        }
    }

    public String getName() {
        return getProperties().get("name");
    }

    public Properties getProperties() {
        return figure.getProperties();
    }

    public Figure getFigure() {
        return figure;
    }

    @Override
    protected void paintChildren() {

        if (diagramScene.getRealZoomFactor() < ZOOM_FACTOR && diagramScene.getModel().getShowBlocks()) {
            return;
        }

        Composite oldComposite = null;
        if (boundary) {
            oldComposite = getScene().getGraphics().getComposite();
            float alpha = DiagramScene.ALPHA;
            this.getScene().getGraphics().setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }

        if (diagramScene.getRealZoomFactor() < LABEL_ZOOM_FACTOR) {

            for (LabelWidget labelWidget : labelWidgets) {
                labelWidget.setVisible(false);
            }
            super.paintChildren();
            for (LabelWidget labelWidget : labelWidgets) {
                labelWidget.setVisible(true);
            }

        } else {
            super.paintChildren();
        }

        if (boundary) {
            getScene().getGraphics().setComposite(oldComposite);
        }
    }

    public JPopupMenu getPopupMenu(Widget widget, Point point) {
        JPopupMenu m = diagramScene.createPopupMenu();

        JMenu predecessors = new JMenu("Predecessors");
        addFigureToSubMenu(predecessors, getFigure(), false, DEPTH);

        JMenu successors = new JMenu("Successors");
        addFigureToSubMenu(successors, getFigure(), true, DEPTH);

        m.addSeparator();
        m.add(predecessors);
        m.add(successors);
        return m;
    }

    public void addFigureToSubMenu(JMenu subMenu, final Figure f, boolean successor, int depth) {
        Set<Figure> set = f.getPredecessorSet();
        if (successor) {
            set = f.getSuccessorSet();
        }

        int count = set.size();
        if (set.contains(f)) {
            count--;
        }

        for (Figure f2 : set) {
            if (f2 == f) {
                continue;
            }

            count--;
            addFigureToMenu(subMenu, f2, successor, depth - 1);
            if (count > 0) {
                subMenu.addSeparator();
            }
        }
    }

    public void addFigureToMenu(JMenu m, final Figure f, boolean successor, int depth) {

        Action a = diagramScene.createGotoAction(f);


        m.add(a);

        if (depth > 0) {
            String name = "Predecessors";
            if (successor) {
                name = "Successors";
            }

            JMenu subMenu = new JMenu(name);
            addFigureToSubMenu(subMenu, f, successor, depth);
            m.add(subMenu);
        }

    }

    public void handleDoubleClick(Widget w, WidgetAction.WidgetMouseEvent e) {

        if (diagramScene.isAllVisible()) {
            Set<Integer> hiddenNodes = new HashSet<Integer>(diagramScene.getModel().getGraphToView().getGroup().getAllNodes());
            hiddenNodes.removeAll(this.getFigure().getSource().getSourceNodesAsSet());
            this.diagramScene.showNot(hiddenNodes);
        } else if (isBoundary()) {

            Set<Integer> hiddenNodes = new HashSet<Integer>(diagramScene.getModel().getHiddenNodes());
            hiddenNodes.removeAll(this.getFigure().getSource().getSourceNodesAsSet());
            this.diagramScene.showNot(hiddenNodes);
        } else {
            Set<Integer> hiddenNodes = new HashSet<Integer>(diagramScene.getModel().getHiddenNodes());
            hiddenNodes.addAll(this.getFigure().getSource().getSourceNodesAsSet());
            this.diagramScene.showNot(hiddenNodes);
        }
    }
}
