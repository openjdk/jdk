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
package com.sun.hotspot.igv.view.widgets;

import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputLiveRange;
import com.sun.hotspot.igv.data.LivenessInfo;
import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.graph.Diagram;
import com.sun.hotspot.igv.graph.Figure;
import com.sun.hotspot.igv.graph.LiveRangeSegment;
import com.sun.hotspot.igv.graph.Slot;
import com.sun.hotspot.igv.util.DoubleClickAction;
import com.sun.hotspot.igv.util.DoubleClickHandler;
import com.sun.hotspot.igv.util.PropertiesConverter;
import com.sun.hotspot.igv.util.PropertiesSheet;
import com.sun.hotspot.igv.view.DiagramScene;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import org.netbeans.api.visual.action.PopupMenuProvider;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.layout.LayoutFactory;
import org.netbeans.api.visual.layout.LayoutFactory.SerialAlignment;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.ImageWidget;
import org.netbeans.api.visual.widget.LabelWidget;
import org.netbeans.api.visual.widget.Widget;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.ImageUtilities;

/**
 *
 * @author Thomas Wuerthinger
 */
public class FigureWidget extends Widget implements Properties.Provider, PopupMenuProvider, DoubleClickHandler {

    private static final double LABEL_ZOOM_FACTOR = 0.3;
    private final Figure figure;
    private final Widget middleWidget;
    private final ArrayList<LabelWidget> labelWidgets;
    private final DiagramScene diagramScene;
    private boolean boundary;
    private static final Image warningSign = ImageUtilities.loadImage("com/sun/hotspot/igv/view/images/warning.png");

    public void setBoundary(boolean b) {
        boundary = b;
    }

    public boolean isBoundary() {
        return boundary;
    }

    @Override
    public boolean isHitAt(Point localLocation) {
        return middleWidget.isHitAt(localLocation);
    }

    private void formatExtraLabel(boolean selected) {
        // If the figure contains an extra label, use a light italic font to
        // differentiate it from the regular label.
        if (getFigure().getProperties().get("extra_label") != null) {
            LabelWidget extraLabelWidget = labelWidgets.get(labelWidgets.size() - 1);
            extraLabelWidget.setFont(Diagram.FONT.deriveFont(Font.ITALIC));
            extraLabelWidget.setForeground(getTextColorHelper(figure.getColor(), !selected));
        }
    }

    public static Color getTextColor(Color color) {
        return getTextColorHelper(color, false);
    }

    private static Color getTextColorHelper(Color bg, boolean useGrey) {
        double brightness = bg.getRed() * 0.21 + bg.getGreen() * 0.72 + bg.getBlue() * 0.07;
        if (brightness < 150) {
            return useGrey ? Color.LIGHT_GRAY : Color.WHITE;
        } else {
            return useGrey ? Color.DARK_GRAY : Color.BLACK;
        }
    }

    public FigureWidget(final Figure f, DiagramScene scene) {
        super(scene);

        assert this.getScene() != null;
        assert this.getScene().getView() != null;

        this.figure = f;
        this.setCheckClipping(true);
        this.diagramScene = scene;

        middleWidget = new Widget(scene);
        middleWidget.setPreferredBounds(new Rectangle(0, 0, f.getWidth(), f.getHeight()));
        middleWidget.setLayout(LayoutFactory.createHorizontalFlowLayout(SerialAlignment.CENTER, 0));
        middleWidget.setOpaque(true);
        middleWidget.getActions().addAction(new DoubleClickAction(this));
        middleWidget.setCheckClipping(false);
        this.addChild(middleWidget);

        Widget textWidget = new Widget(scene);
        SerialAlignment textAlign = scene.getModel().getShowCFG() ?
                LayoutFactory.SerialAlignment.LEFT_TOP :
                LayoutFactory.SerialAlignment.CENTER;
        textWidget.setLayout(LayoutFactory.createVerticalFlowLayout(textAlign, 0));
        middleWidget.addChild(textWidget);

        String[] strings = figure.getLines();
        labelWidgets = new ArrayList<>(strings.length);

        for (String displayString : strings) {
            LabelWidget lw = new LabelWidget(scene);
            labelWidgets.add(lw);
            textWidget.addChild(lw);
            lw.setLabel(displayString);
            lw.setFont(Diagram.FONT);
            lw.setAlignment(LabelWidget.Alignment.CENTER);
            lw.setVerticalAlignment(LabelWidget.VerticalAlignment.CENTER);
            lw.setCheckClipping(false);
        }
        formatExtraLabel(false);
        refreshColor();

        for (int i=1; i < labelWidgets.size(); i++) {
            labelWidgets.get(i).setFont(Diagram.FONT.deriveFont(Font.ITALIC));
            labelWidgets.get(i).setForeground(Color.DARK_GRAY);
        }


        int textHeight = f.getHeight() - 2 * Figure.PADDING - f.getSlotsHeight();
        if (getFigure().getWarning() != null) {
            ImageWidget warningWidget = new ImageWidget(scene, warningSign);
            warningWidget.setToolTipText(getFigure().getWarning());
            middleWidget.addChild(warningWidget);
            int textWidth = f.getWidth() - 4 * Figure.BORDER;
            textWidth -= Figure.WARNING_WIDTH + Figure.PADDING;
            textWidget.setPreferredBounds(new Rectangle(0, 0, textWidth, textHeight));
        } else {
            int textWidth = f.getWidth() - 4 * Figure.BORDER;
            textWidget.setPreferredBounds(new Rectangle(0, 0, textWidth, textHeight));
        }

        // Initialize node for property sheet
        Node node = new AbstractNode(Children.LEAF) {

            @Override
            protected Sheet createSheet() {
                Sheet s = super.createSheet();
                PropertiesSheet.initializeSheet(f.getProperties(), s);
                return s;
            }
        };
        node.setDisplayName(getName());

        this.setToolTipText(PropertiesConverter.convertToHTML(f.getProperties()));
    }

    public void updatePosition() {
        setPreferredLocation(figure.getPosition());
    }

    public int getFigureHeight() {
        return middleWidget.getPreferredBounds().height;
    }

    public static class RoundedBorder extends LineBorder {

        final float RADIUS = 3f;

        public RoundedBorder(Color color, int thickness)  {
            super(color, thickness);
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            if ((this.thickness > 0) && (g instanceof Graphics2D)) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color oldColor = g2d.getColor();
                g2d.setColor(this.lineColor);
                int offs = this.thickness;
                int size = offs + offs;
                Shape outer = new RoundRectangle2D.Float(x, y, width, height, RADIUS, RADIUS);
                Shape inner = new RoundRectangle2D.Float(x + offs, y + offs, width - size, height - size, RADIUS, RADIUS);
                Path2D path = new Path2D.Float(Path2D.WIND_EVEN_ODD);
                path.append(outer, false);
                path.append(inner, false);
                g2d.fill(path);
                g2d.setColor(oldColor);
            }
        }
    }

    public void refreshColor() {
        middleWidget.setBackground(figure.getColor());
        for (LabelWidget lw : labelWidgets) {
            lw.setForeground(getTextColor(figure.getColor()));
        }
    }

    @Override
    protected void notifyStateChanged(ObjectState previousState, ObjectState state) {
        super.notifyStateChanged(previousState, state);

        Font font = Diagram.FONT;
        Color borderColor = Color.BLACK;
        Color innerBorderColor = getFigure().getColor();
        if (state.isSelected()) {
            font = Diagram.BOLD_FONT;
            innerBorderColor = Color.BLACK;
        }

        if (state.isHighlighted()) {
            innerBorderColor = borderColor = Color.BLUE;
        }

        Border innerBorder = new RoundedBorder(borderColor, Figure.BORDER);
        Border outerBorder = new RoundedBorder(innerBorderColor, Figure.BORDER);
        Border roundedBorder = BorderFactory.createCompoundBorder(innerBorder, outerBorder);
        middleWidget.setBorder(roundedBorder);

        for (LabelWidget labelWidget : labelWidgets) {
            labelWidget.setFont(font);
        }
        formatExtraLabel(state.isSelected());
        repaint();
    }

    public String getName() {
        return getProperties().get("name");
    }

    @Override
    public Properties getProperties() {
        return figure.getProperties();
    }

    public Figure getFigure() {
        return figure;
    }

    @Override
    protected void paintChildren() {
        refreshColor();
        Composite oldComposite = null;
        if (boundary) {
            oldComposite = getScene().getGraphics().getComposite();
            float alpha = DiagramScene.ALPHA;
            this.getScene().getGraphics().setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }

        if (diagramScene.getZoomFactor() < LABEL_ZOOM_FACTOR) {
            for (LabelWidget labelWidget : labelWidgets) {
                labelWidget.setVisible(false);
            }
            super.paintChildren();
            for (LabelWidget labelWidget : labelWidgets) {
                labelWidget.setVisible(true);
            }
        } else {
            Color oldColor = null;
            if (boundary) {
                for (LabelWidget labelWidget : labelWidgets) {
                    oldColor = labelWidget.getForeground();
                    labelWidget.setForeground(Color.BLACK);
                }
            }
            super.paintChildren();
            if (boundary) {
                for (LabelWidget labelWidget : labelWidgets) {
                    labelWidget.setForeground(oldColor);
                }
            }
        }

        if (boundary) {
            getScene().getGraphics().setComposite(oldComposite);
        }
    }

    @Override
    public JPopupMenu getPopupMenu(Widget widget, Point point) {
        JPopupMenu menu = diagramScene.createPopupMenu();
        menu.addSeparator();

        build(menu, getFigure(), this, false, diagramScene);
        menu.addSeparator();
        build(menu, getFigure(), this, true, diagramScene);

        if (diagramScene.getModel().getShowCFG() &&
            diagramScene.getModel().getShowLiveRanges()) {
            InputGraph graph = diagramScene.getModel().getGraph();
            LivenessInfo l = graph.getLivenessInfoForNode(getFigure().getInputNode());
            if (l != null) {
                Set<InputLiveRange> liveRanges = new HashSet<>();
                if (l.def != null) {
                    liveRanges.add(graph.getLiveRange(l.def));
                }
                if (l.use != null) {
                    for (int use : l.use) {
                        liveRanges.add(graph.getLiveRange(use));
                    }
                }
                if (l.join != null) {
                    for (int join : l.join) {
                        liveRanges.add(graph.getLiveRange(join));
                    }
                }
                if (!liveRanges.isEmpty()) {
                    menu.addSeparator();
                    menu.add(diagramScene.createGotoLiveRangeAction("Select live ranges", liveRanges));
                    menu.addSeparator();
                    if (l.def != null) {
                        menu.add(diagramScene.createGotoLiveRangeAction(graph.getLiveRange(l.def)));
                    }
                    menu.addSeparator();
                    if (l.use != null) {
                        for (int use : l.use) {
                            menu.add(diagramScene.createGotoLiveRangeAction(graph.getLiveRange(use)));
                        }
                    }
                    if (l.join != null) {
                        for (int join : l.join) {
                            menu.add(diagramScene.createGotoLiveRangeAction(graph.getLiveRange(join)));
                        }
                    }
                }
            }
        }

        return menu;
    }

    public static void build(JPopupMenu menu, Figure figure, FigureWidget figureWidget, boolean successors, DiagramScene diagramScene) {
        Set<Figure> set = figure.getPredecessorSet();
        if (successors) {
            set = figure.getSuccessorSet();
        }

        boolean first = true;
        for (Figure f : set) {
            if (f == figure) {
                continue;
            }

            if (first) {
                first = false;
            } else {
                menu.addSeparator();
            }

            Action go = diagramScene.createGotoAction(f);
            menu.add(go);

            JMenu preds = new JMenu("Nodes Above");
            preds.addMenuListener(figureWidget.new NeighborMenuListener(preds, f, false));
            menu.add(preds);

            JMenu succs = new JMenu("Nodes Below");
            succs.addMenuListener(figureWidget.new NeighborMenuListener(succs, f, true));
            menu.add(succs);
        }

        if (figure.getPredecessorSet().isEmpty() && figure.getSuccessorSet().isEmpty()) {
            menu.add("(none)");
        }
    }

    /**
     * Builds the submenu for a figure's neighbors on demand.
     */
    public class NeighborMenuListener implements MenuListener {

        private final JMenu menu;
        private final Figure figure;
        private final boolean successors;

        public NeighborMenuListener(JMenu menu, Figure figure, boolean successors) {
            this.menu = menu;
            this.figure = figure;
            this.successors = successors;
        }

        @Override
        public void menuSelected(MenuEvent e) {
            if (menu.getItemCount() > 0) {
                // already built before
                return;
            }

            build(menu.getPopupMenu(), figure, FigureWidget.this, successors, diagramScene);
        }

        @Override
        public void menuDeselected(MenuEvent e) {
            // ignore
        }

        @Override
        public void menuCanceled(MenuEvent e) {
            // ignore
        }
    }

    @Override
    public void handleDoubleClick(Widget w, WidgetAction.WidgetMouseEvent e) {
        if (diagramScene.isAllVisible()) {
            final Set<Integer> hiddenNodes = new HashSet<>(diagramScene.getModel().getGroup().getAllNodes());
            hiddenNodes.remove(this.getFigure().getInputNode().getId());
            this.diagramScene.getModel().setHiddenNodes(hiddenNodes);
        } else if (isBoundary()) {
            final Set<Integer> hiddenNodes = new HashSet<>(diagramScene.getModel().getHiddenNodes());
            hiddenNodes.remove(this.getFigure().getInputNode().getId());
            this.diagramScene.getModel().setHiddenNodes(hiddenNodes);
        } else {
            final Set<Integer> hiddenNodes = new HashSet<>(diagramScene.getModel().getHiddenNodes());
            hiddenNodes.add(this.getFigure().getInputNode().getId());
            this.diagramScene.getModel().setHiddenNodes(hiddenNodes);
        }
    }
}
