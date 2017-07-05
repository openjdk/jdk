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

import com.sun.hotspot.igv.view.widgets.BlockWidget;
import com.sun.hotspot.igv.view.widgets.LineWidget;
import com.sun.hotspot.igv.util.DoubleClickAction;
import com.sun.hotspot.igv.data.InputBlock;
import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.graph.Connection;
import com.sun.hotspot.igv.graph.Diagram;
import com.sun.hotspot.igv.graph.Figure;
import com.sun.hotspot.igv.graph.InputSlot;
import com.sun.hotspot.igv.graph.OutputSlot;
import com.sun.hotspot.igv.graph.Slot;
import com.sun.hotspot.igv.hierarchicallayout.HierarchicalClusterLayoutManager;
import com.sun.hotspot.igv.hierarchicallayout.OldHierarchicalLayoutManager;
import com.sun.hotspot.igv.hierarchicallayout.HierarchicalLayoutManager;
import com.sun.hotspot.igv.view.widgets.FigureWidget;
import com.sun.hotspot.igv.view.widgets.InputSlotWidget;
import com.sun.hotspot.igv.view.widgets.OutputSlotWidget;
import com.sun.hotspot.igv.view.widgets.SlotWidget;
import com.sun.hotspot.igv.layout.LayoutGraph;
import com.sun.hotspot.igv.data.services.Scheduler;
import com.sun.hotspot.igv.data.ChangedListener;
import com.sun.hotspot.igv.graph.Block;
import com.sun.hotspot.igv.util.ColorIcon;
import com.sun.hotspot.igv.util.ExtendedSelectAction;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import org.netbeans.api.visual.action.ActionFactory;
import org.netbeans.api.visual.action.PopupMenuProvider;
import org.netbeans.api.visual.action.RectangularSelectDecorator;
import org.netbeans.api.visual.action.RectangularSelectProvider;
import org.netbeans.api.visual.action.SelectProvider;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.animator.SceneAnimator;
import org.netbeans.api.visual.layout.LayoutFactory;
import org.netbeans.api.visual.widget.ConnectionWidget;
import org.netbeans.api.visual.widget.LayerWidget;
import org.netbeans.api.visual.widget.Scene;
import org.netbeans.api.visual.widget.Widget;
import org.netbeans.api.visual.widget.LabelWidget;
import org.openide.awt.UndoRedo;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

/**
 *
 * @author Thomas Wuerthinger
 */
public class DiagramScene extends Scene implements ChangedListener<DiagramViewModel> {

    private HashMap<Figure, FigureWidget> figureWidgets;
    private HashMap<Slot, SlotWidget> slotWidgets;
    private HashMap<Connection, ConnectionWidget> connectionWidgets;
    private HashMap<InputBlock, BlockWidget> blockWidgets;
    private Widget hoverWidget;
    private WidgetAction hoverAction;
    private List<FigureWidget> selectedWidgets;
    private Lookup lookup;
    private InstanceContent content;
    private Action[] actions;
    private LayerWidget connectionLayer;
    private JScrollPane scrollPane;
    private UndoRedo.Manager undoRedoManager;
    private LayerWidget mainLayer;
    private LayerWidget slotLayer;
    private LayerWidget blockLayer;
    private double realZoomFactor;
    private BoundedZoomAction zoomAction;
    private WidgetAction panAction;
    private Widget topLeft;
    private Widget bottomRight;
    private LayerWidget startLayer;
    private LabelWidget startLabel;
    private DiagramViewModel model;
    private DiagramViewModel modelCopy;
    public static final int AFTER = 1;
    public static final int BEFORE = 1;
    public static final float ALPHA = 0.4f;
    public static final int GRID_SIZE = 30;
    public static final int BORDER_SIZE = 20;
    public static final int UNDOREDO_LIMIT = 100;
    public static final int SCROLL_UNIT_INCREMENT = 80;
    public static final int SCROLL_BLOCK_INCREMENT = 400;
    public static final float ZOOM_MAX_FACTOR = 3.0f;
    public static final float ZOOM_MIN_FACTOR = 0.0f;//0.15f;
    public static final float ZOOM_INCREMENT = 1.5f;
    public static final int SLOT_OFFSET = 6;
    public static final int ANIMATION_LIMIT = 40;
    private PopupMenuProvider popupMenuProvider = new PopupMenuProvider() {

        public JPopupMenu getPopupMenu(Widget widget, Point localLocation) {
            return DiagramScene.this.createPopupMenu();
        }
    };
    private RectangularSelectDecorator rectangularSelectDecorator = new RectangularSelectDecorator() {

        public Widget createSelectionWidget() {
            Widget widget = new Widget(DiagramScene.this);
            widget.setBorder(BorderFactory.createLineBorder(Color.black, 2));
            widget.setForeground(Color.red);
            return widget;
        }
    };
    private RectangularSelectProvider rectangularSelectProvider = new RectangularSelectProvider() {

        public void performSelection(Rectangle rectangle) {
            if (rectangle.width < 0) {
                rectangle.x += rectangle.width;
                rectangle.width *= -1;
            }

            if (rectangle.height < 0) {
                rectangle.y += rectangle.height;
                rectangle.height *= -1;
            }

            boolean updated = false;
            for (Figure f : getModel().getDiagramToView().getFigures()) {
                FigureWidget w = figureWidgets.get(f);
                Rectangle r = new Rectangle(w.getBounds());
                r.setLocation(w.getLocation());
                if (r.intersects(rectangle)) {
                    if (!selectedWidgets.contains(w)) {
                        addToSelection(w);
                        updated = true;
                    }
                } else {
                    if (selectedWidgets.contains(w)) {
                        selectedWidgets.remove(w);
                        content.remove(w.getNode());
                        w.setState(w.getState().deriveSelected(false));
                        updated = true;
                    }
                }
            }

            if (updated) {
                selectionUpdated();
            }
        }
    };
    private SelectProvider selectProvider = new SelectProvider() {

        public boolean isAimingAllowed(Widget widget, Point point, boolean b) {
            return false;
        }

        public boolean isSelectionAllowed(Widget widget, Point point, boolean b) {
            return widget instanceof FigureWidget || widget == DiagramScene.this;
        }

        public void select(Widget w, Point point, boolean change) {

            boolean updated = false;

            if (w == DiagramScene.this) {
                if (DiagramScene.this.selectedWidgets.size() != 0) {
                    clearSelection();
                    selectionUpdated();
                }
                return;
            }

            FigureWidget widget = (FigureWidget) w;


            if (change) {
                if (widget.getState().isSelected()) {
                    assert selectedWidgets.contains(widget);
                    widget.setState(widget.getState().deriveSelected(false));
                    selectedWidgets.remove(widget);
                    content.remove(widget.getNode());
                    updated = true;
                } else {
                    assert !selectedWidgets.contains(widget);
                    addToSelection(widget);
                    updated = true;
                    assert widget.getState().isSelected();
                }
            } else {

                if (widget.getState().isSelected()) {
                    assert selectedWidgets.contains(widget);
                } else {

                    assert !selectedWidgets.contains(widget);
                    clearSelection();
                    addToSelection(widget);
                    updated = true;
                    assert widget.getState().isSelected();
                }
            }

            if (updated) {
                selectionUpdated();
            }

        }
    };

    private FigureWidget getFigureWidget(Figure f) {
        return figureWidgets.get(f);
    }
    private FocusListener focusListener = new FocusListener() {

        public void focusGained(FocusEvent e) {
            DiagramScene.this.getView().requestFocus();
        }

        public void focusLost(FocusEvent e) {
        }
    };
    private MouseWheelListener mouseWheelListener = new MouseWheelListener() {

        public void mouseWheelMoved(MouseWheelEvent e) {
            DiagramScene.this.zoomAction.mouseWheelMoved(DiagramScene.this, new WidgetAction.WidgetMouseWheelEvent(0, e));
            DiagramScene.this.validate();
        }
    };
    private MouseListener mouseListener = new MouseListener() {

        public void mouseClicked(MouseEvent e) {
            DiagramScene.this.panAction.mouseClicked(DiagramScene.this, new WidgetAction.WidgetMouseEvent(0, e));
        }

        public void mousePressed(MouseEvent e) {
            DiagramScene.this.panAction.mousePressed(DiagramScene.this, new WidgetAction.WidgetMouseEvent(0, e));
        }

        public void mouseReleased(MouseEvent e) {
            DiagramScene.this.panAction.mouseReleased(DiagramScene.this, new WidgetAction.WidgetMouseEvent(0, e));
        }

        public void mouseEntered(MouseEvent e) {
            DiagramScene.this.panAction.mouseEntered(DiagramScene.this, new WidgetAction.WidgetMouseEvent(0, e));
        }

        public void mouseExited(MouseEvent e) {
            DiagramScene.this.panAction.mouseExited(DiagramScene.this, new WidgetAction.WidgetMouseEvent(0, e));
        }
    };
    private MouseMotionListener mouseMotionListener = new MouseMotionListener() {

        public void mouseDragged(MouseEvent e) {
            DiagramScene.this.panAction.mouseDragged(DiagramScene.this, new WidgetAction.WidgetMouseEvent(0, e));
        }

        public void mouseMoved(MouseEvent e) {
        }
    };
    private ScrollChangeListener scrollChangeListener = new ScrollChangeListener();

    private class ScrollChangeListener implements ChangeListener {

        private Map<Widget, Point> relativePositions = new HashMap<Widget, Point>();
        private Point oldPosition;

        public void register(Widget w, Point p) {
            relativePositions.put(w, p);
        }

        public void unregister(Widget w) {
            relativePositions.remove(w);
        }

        public void stateChanged(ChangeEvent e) {
            Point p = DiagramScene.this.getScrollPane().getViewport().getViewPosition();
            if (oldPosition == null || !p.equals(oldPosition)) {
                for (Widget w : relativePositions.keySet()) {
                    Point curPoint = relativePositions.get(w);
                    Point newPoint = new Point(p.x + curPoint.x, p.y + curPoint.y);
                    w.setPreferredLocation(newPoint);
                    DiagramScene.this.validate();
                }
                oldPosition = p;
            }
        }
    }

    public Point getScrollPosition() {
        return getScrollPane().getViewport().getViewPosition();
    }

    public void setScrollPosition(Point p) {
        getScrollPane().getViewport().setViewPosition(p);
    }

    public DiagramScene(Action[] actions, DiagramViewModel model) {
        this.actions = actions;
        selectedWidgets = new ArrayList<FigureWidget>();
        content = new InstanceContent();
        lookup = new AbstractLookup(content);
        this.setCheckClipping(true);
        this.getInputBindings().setZoomActionModifiers(0);

        JComponent comp = this.createView();
        comp.setDoubleBuffered(true);
        comp.setBackground(Color.WHITE);
        comp.setOpaque(true);

        this.setBackground(Color.WHITE);
        this.setOpaque(true);
        scrollPane = new JScrollPane(comp);
        scrollPane.setBackground(Color.WHITE);
        scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        scrollPane.getVerticalScrollBar().setBlockIncrement(SCROLL_BLOCK_INCREMENT);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        scrollPane.getHorizontalScrollBar().setBlockIncrement(SCROLL_BLOCK_INCREMENT);
        scrollPane.getViewport().addChangeListener(scrollChangeListener);
        hoverAction = this.createWidgetHoverAction();

        blockLayer = new LayerWidget(this);
        this.addChild(blockLayer);

        startLayer = new LayerWidget(this);
        this.addChild(startLayer);
        // TODO: String startLabelString = "Loading graph with " + originalDiagram.getFigures().size() + " figures and " + originalDiagram.getConnections().size() + " connections...";
        String startLabelString = "";
        LabelWidget w = new LabelWidget(this, startLabelString);
        scrollChangeListener.register(w, new Point(10, 10));
        w.setAlignment(LabelWidget.Alignment.CENTER);
        startLabel = w;
        startLayer.addChild(w);

        mainLayer = new LayerWidget(this);
        this.addChild(mainLayer);

        topLeft = new Widget(this);
        topLeft.setPreferredLocation(new Point(-BORDER_SIZE, -BORDER_SIZE));
        this.addChild(topLeft);


        bottomRight = new Widget(this);
        bottomRight.setPreferredLocation(new Point(-BORDER_SIZE, -BORDER_SIZE));
        this.addChild(bottomRight);

        slotLayer = new LayerWidget(this);
        this.addChild(slotLayer);

        connectionLayer = new LayerWidget(this);
        this.addChild(connectionLayer);

        LayerWidget selectionLayer = new LayerWidget(this);
        this.addChild(selectionLayer);

        this.setLayout(LayoutFactory.createAbsoluteLayout());

        this.getActions().addAction(hoverAction);
        zoomAction = new BoundedZoomAction(1.1, false);
        zoomAction.setMaxFactor(ZOOM_MAX_FACTOR);
        zoomAction.setMinFactor(ZOOM_MIN_FACTOR);
        this.getActions().addAction(ActionFactory.createMouseCenteredZoomAction(1.1));
        panAction = new ExtendedPanAction();
        this.getActions().addAction(panAction);
        this.getActions().addAction(ActionFactory.createPopupMenuAction(popupMenuProvider));

        LayerWidget selectLayer = new LayerWidget(this);
        this.addChild(selectLayer);
        this.getActions().addAction(ActionFactory.createRectangularSelectAction(rectangularSelectDecorator, selectLayer, rectangularSelectProvider));

        blockWidgets = new HashMap<InputBlock, BlockWidget>();

        boolean b = this.getUndoRedoEnabled();
        this.setUndoRedoEnabled(false);
        this.setNewModel(model);
        this.setUndoRedoEnabled(b);
    }

    private void selectionUpdated() {
        getModel().setSelectedNodes(this.getSelectedNodes());
        addUndo();
    }

    public DiagramViewModel getModel() {
        return model;
    }

    public void setRealZoomFactor(double d) {
        this.realZoomFactor = d;
    }

    public double getRealZoomFactor() {
        if (realZoomFactor == 0.0) {
            return getZoomFactor();
        } else {
            return realZoomFactor;
        }
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    public boolean isAllVisible() {
        return getModel().getHiddenNodes().size() == 0;
    }

    public Action createGotoAction(final Figure f) {
        final DiagramScene diagramScene = this;
        Action a = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                diagramScene.gotoFigure(f);
            }
        };

        a.setEnabled(true);
        a.putValue(Action.SMALL_ICON, new ColorIcon(f.getColor()));
        String name = f.getLines()[0];

        name += " (";

        if (f.getCluster() != null) {
            name += "B" + f.getCluster().toString();
        }
        if (!this.getFigureWidget(f).isVisible()) {
            if (f.getCluster() != null) {
                name += ", ";
            }
            name += "hidden";
        }
        name += ")";
        a.putValue(Action.NAME, name);
        return a;
    }

    public void setNewModel(DiagramViewModel model) {
        if (this.model != null) {
            this.model.getDiagramChangedEvent().removeListener(this);
            this.model.getViewPropertiesChangedEvent().removeListener(this);
        }
        this.model = model;

        if (this.model == null) {
            this.modelCopy = null;
        } else {
            this.modelCopy = this.model.copy();
        }

        model.getDiagramChangedEvent().addListener(this);
        model.getViewPropertiesChangedEvent().addListener(this);

        update();
    }

    private void update() {

        /*if (startLabel != null) {
        // Animate fade-out
        final LabelWidget labelWidget = this.startLabel;
        labelWidget.setVisible(true);
        RequestProcessor.getDefault().post(new Runnable() {
        public void run() {
        final int Sleep = 200;
        final int Progress = 10;
        for (int i = 0; i < 255 / Progress + 1; i++) {
        try {
        SwingUtilities.invokeAndWait(new Runnable() {
        public void run() {
        Color c = labelWidget.getForeground();
        int v = c.getRed();
        v += Progress;
        if (v > 255) {
        v = 255;
        }
        labelWidget.setForeground(new Color(v, v, v, 255 - v));
        labelWidget.getScene().validate();
        }
        });
        } catch (InterruptedException ex) {
        } catch (InvocationTargetException ex) {
        }
        try {
        Thread.sleep(Sleep);
        } catch (InterruptedException ex) {
        }
        }
        labelWidget.setVisible(false);
        DiagramScene.this.scrollChangeListener.unregister(labelWidget);
        }
        }, 1000);
        startLabel = null;
        }*/

        slotLayer.removeChildren();
        mainLayer.removeChildren();
        blockLayer.removeChildren();

        blockWidgets.clear();
        figureWidgets = new HashMap<Figure, FigureWidget>();
        slotWidgets = new HashMap<Slot, SlotWidget>();
        connectionWidgets = new HashMap<Connection, ConnectionWidget>();

        WidgetAction selectAction = new ExtendedSelectAction(selectProvider);
        Diagram d = getModel().getDiagramToView();

        if (getModel().getShowBlocks()) {
            Scheduler s = Lookup.getDefault().lookup(Scheduler.class);
            Collection<InputBlock> newBlocks = new ArrayList<InputBlock>(s.schedule(d.getGraph()));
            d.schedule(newBlocks);
        }

        for (Figure f : d.getFigures()) {
            FigureWidget w = new FigureWidget(f, this, mainLayer);
            w.getActions().addAction(selectAction);
            w.getActions().addAction(hoverAction);
            w.getActions().addAction(ActionFactory.createPopupMenuAction(w));
            w.getActions().addAction(new DoubleClickAction(w));
            w.setVisible(false);

            figureWidgets.put(f, w);

            for (InputSlot s : f.getInputSlots()) {
                SlotWidget sw = new InputSlotWidget(s, this, slotLayer, w);
                slotWidgets.put(s, sw);
                sw.getActions().addAction(selectAction);
            }

            for (OutputSlot s : f.getOutputSlots()) {
                SlotWidget sw = new OutputSlotWidget(s, this, slotLayer, w);
                slotWidgets.put(s, sw);
                sw.getActions().addAction(selectAction);
            }
        }

        if (getModel().getShowBlocks()) {
            for (InputBlock bn : d.getGraph().getBlocks()) {
                BlockWidget w = new BlockWidget(this, d, bn);
                w.setVisible(false);
                blockWidgets.put(bn, w);
                blockLayer.addChild(w);
            }
        }

        this.smallUpdate(true);

    }

    private void smallUpdate(boolean relayout) {

        this.updateHiddenNodes(model.getHiddenNodes(), relayout);
        boolean b = this.getUndoRedoEnabled();
        this.setUndoRedoEnabled(false);
        this.setSelection(getModel().getSelectedNodes());
        this.setUndoRedoEnabled(b);
        this.validate();
    }

    private boolean isVisible(Connection c) {
        FigureWidget w1 = figureWidgets.get(c.getInputSlot().getFigure());
        FigureWidget w2 = figureWidgets.get(c.getOutputSlot().getFigure());

        if (w1.isVisible() && w2.isVisible()) {
            return true;
        }

        return false;
    }

    private void relayout(Set<Widget> oldVisibleWidgets) {

        Diagram diagram = getModel().getDiagramToView();

        HashSet<Figure> figures = new HashSet<Figure>();

        for (Figure f : diagram.getFigures()) {
            FigureWidget w = figureWidgets.get(f);
            if (w.isVisible()) {
                figures.add(f);
            }
        }

        HashSet<Connection> edges = new HashSet<Connection>();

        for (Connection c : diagram.getConnections()) {
            if (isVisible(c)) {
                edges.add(c);
            }
        }

        if (getModel().getShowBlocks()) {
            HierarchicalClusterLayoutManager m = new HierarchicalClusterLayoutManager(OldHierarchicalLayoutManager.Combine.SAME_OUTPUTS);
            HierarchicalLayoutManager manager = new HierarchicalLayoutManager(HierarchicalLayoutManager.Combine.SAME_OUTPUTS);
            manager.setMaxLayerLength(9);
            manager.setMinLayerDifference(3);
            m.setManager(manager);
            m.setSubManager(new HierarchicalLayoutManager(HierarchicalLayoutManager.Combine.SAME_OUTPUTS));
            m.doLayout(new LayoutGraph(edges, figures));

        } else {
            HierarchicalLayoutManager manager = new HierarchicalLayoutManager(HierarchicalLayoutManager.Combine.SAME_OUTPUTS);
            manager.setMaxLayerLength(10);
            manager.doLayout(new LayoutGraph(edges, figures));
        }

        int maxX = -BORDER_SIZE;
        int maxY = -BORDER_SIZE;
        for (Figure f : diagram.getFigures()) {
            FigureWidget w = figureWidgets.get(f);
            if (w.isVisible()) {
                Point p = f.getPosition();
                Dimension d = f.getSize();
                maxX = Math.max(maxX, p.x + d.width);
                maxY = Math.max(maxY, p.y + d.height);
            }
        }

        for (Connection c : diagram.getConnections()) {
            List<Point> points = c.getControlPoints();
            FigureWidget w1 = figureWidgets.get((Figure) c.getTo().getVertex());
            FigureWidget w2 = figureWidgets.get((Figure) c.getFrom().getVertex());
            if (w1.isVisible() && w2.isVisible()) {
                for (Point p : points) {
                    if (p != null) {
                        maxX = Math.max(maxX, p.x);
                        maxY = Math.max(maxY, p.y);
                    }
                }
            }
        }

        if (getModel().getShowBlocks()) {
            for (Block b : diagram.getBlocks()) {
                BlockWidget w = blockWidgets.get(b.getInputBlock());
                if (w != null && w.isVisible()) {
                    Rectangle r = b.getBounds();
                    maxX = Math.max(maxX, r.x + r.width);
                    maxY = Math.max(maxY, r.y + r.height);
                }
            }
        }

        bottomRight.setPreferredLocation(new Point(maxX + BORDER_SIZE, maxY + BORDER_SIZE));
        int offx = 0;
        int offy = 0;
        int curWidth = maxX + 2 * BORDER_SIZE;
        int curHeight = maxY + 2 * BORDER_SIZE;

        Rectangle bounds = this.getScrollPane().getBounds();
        if (curWidth < bounds.width) {
            offx = (bounds.width - curWidth) / 2;
        }

        if (curHeight < bounds.height) {
            offy = (bounds.height - curHeight) / 2;
        }

        final int offx2 = offx;
        final int offy2 = offy;

        SceneAnimator animator = this.getSceneAnimator();
        connectionLayer.removeChildren();
        int visibleFigureCount = 0;
        for (Figure f : diagram.getFigures()) {
            if (figureWidgets.get(f).isVisible()) {
                visibleFigureCount++;
            }
        }

        for (Figure f : diagram.getFigures()) {
            for (OutputSlot s : f.getOutputSlots()) {
                SceneAnimator anim = animator;
                if (visibleFigureCount > ANIMATION_LIMIT) {
                    anim = null;
                }
                processOutputSlot(s, s.getConnections(), 0, null, null, offx2, offy2, anim);
            }
        }

        for (Figure f : diagram.getFigures()) {
            FigureWidget w = figureWidgets.get(f);
            if (w.isVisible()) {
                Point p = f.getPosition();
                Point p2 = new Point(p.x + offx2, p.y + offy2);
                Rectangle r = new Rectangle(p.x + offx2, p.y + offy2, f.getSize().width, f.getSize().height);
                if (oldVisibleWidgets.contains(w)) {
                    if (visibleFigureCount > ANIMATION_LIMIT) {
                        w.setPreferredLocation(p2);
                    } else {
                        animator.animatePreferredLocation(w, p2);
                    }
                } else {
                    w.setPreferredLocation(p2);
                }
            }
        }

        if (getModel().getShowBlocks()) {
            for (Block b : diagram.getBlocks()) {
                BlockWidget w = blockWidgets.get(b.getInputBlock());
                if (w != null && w.isVisible()) {
                    Point location = new Point(b.getBounds().x + offx2, b.getBounds().y + offy2);
                    Rectangle r = new Rectangle(location.x, location.y, b.getBounds().width, b.getBounds().height);
                    if (oldVisibleWidgets.contains(w)) {
                        if (visibleFigureCount > ANIMATION_LIMIT) {
                            w.setPreferredBounds(r);
                        } else {
                            animator.animatePreferredBounds(w, r);
                        }
                    } else {
                        w.setPreferredBounds(r);
                    }
                }
            }
        }
    }
    private final Point specialNullPoint = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);

    private void processOutputSlot(OutputSlot s, List<Connection> connections, int controlPointIndex, Point lastPoint, LineWidget predecessor, int offx, int offy, SceneAnimator animator) {
        Map<Point, List<Connection>> pointMap = new HashMap<Point, List<Connection>>();

        for (Connection c : connections) {

            if (!isVisible(c)) {
                continue;
            }

            List<Point> controlPoints = c.getControlPoints();
            if (controlPointIndex >= controlPoints.size()) {
                continue;
            }

            Point cur = controlPoints.get(controlPointIndex);
            if (cur == null) {
                cur = specialNullPoint;
            } else if (controlPointIndex == 0 && !s.getShowName()) {
                cur = new Point(cur.x, cur.y - SLOT_OFFSET);
            } else if (controlPointIndex == controlPoints.size() - 1 && !c.getInputSlot().getShowName()) {
                cur = new Point(cur.x, cur.y + SLOT_OFFSET);
            }

            if (pointMap.containsKey(cur)) {
                pointMap.get(cur).add(c);
            } else {
                List<Connection> newList = new ArrayList<Connection>(2);
                newList.add(c);
                pointMap.put(cur, newList);
            }

        }

        for (Point p : pointMap.keySet()) {
            List<Connection> connectionList = pointMap.get(p);

            boolean isBold = false;
            boolean isDashed = true;

            for (Connection c : connectionList) {

                if (c.getStyle() == Connection.ConnectionStyle.BOLD) {
                    isBold = true;
                }

                if (c.getStyle() != Connection.ConnectionStyle.DASHED) {
                    isDashed = false;
                }
            }

            LineWidget newPredecessor = predecessor;
            if (p == specialNullPoint) {

            } else if (lastPoint == specialNullPoint) {

            } else if (lastPoint != null) {
                Point p1 = new Point(lastPoint.x + offx, lastPoint.y + offy);
                Point p2 = new Point(p.x + offx, p.y + offy);
                LineWidget w = new LineWidget(this, s, connectionList, p1, p2, predecessor, animator, isBold, isDashed);
                newPredecessor = w;
                connectionLayer.addChild(w);
                w.getActions().addAction(hoverAction);
            }

            processOutputSlot(s, connectionList, controlPointIndex + 1, p, newPredecessor, offx, offy, animator);
        }
    }

    private void clearSelection() {
        if (selectedWidgets.size() == 0) {
            return;
        }
        for (FigureWidget w : selectedWidgets) {
            assert w.getState().isSelected();
            w.setState(w.getState().deriveSelected(false));
            content.remove(w.getNode());
        }
        selectedWidgets.clear();
    }

    public Lookup getLookup() {
        return lookup;
    }

    public void gotoFigures(final List<Figure> figures) {
        Rectangle overall = null;
        showFigures(figures);
        for (Figure f : figures) {

            FigureWidget fw = getFigureWidget(f);
            if (fw != null) {
                Rectangle r = fw.getBounds();
                Point p = fw.getLocation();
                Rectangle r2 = new Rectangle(p.x, p.y, r.width, r.height);

                if (overall == null) {
                    overall = r2;
                } else {
                    overall = overall.union(r2);
                }
            }
        }
        if (overall != null) {
            centerRectangle(overall);
        }
    }

    private Point calcCenter(Rectangle r) {

        Point center = new Point((int) r.getCenterX(), (int) r.getCenterY());
        center.x -= getScrollPane().getViewport().getViewRect().width / 2;
        center.y -= getScrollPane().getViewport().getViewRect().height / 2;

        // Ensure to be within area
        center.x = Math.max(0, center.x);
        center.x = Math.min(getScrollPane().getViewport().getViewSize().width - getScrollPane().getViewport().getViewRect().width, center.x);
        center.y = Math.max(0, center.y);
        center.y = Math.min(getScrollPane().getViewport().getViewSize().height - getScrollPane().getViewport().getViewRect().height, center.y);

        return center;
    }

    private void centerRectangle(Rectangle r) {

        if (getScrollPane().getViewport().getViewRect().width == 0 || getScrollPane().getViewport().getViewRect().height == 0) {
            return;
        }

        Rectangle r2 = new Rectangle(r.x, r.y, r.width, r.height);
        r2 = convertSceneToView(r2);

        double factorX = (double) r2.width / (double) getScrollPane().getViewport().getViewRect().width;
        double factorY = (double) r2.height / (double) getScrollPane().getViewport().getViewRect().height;
        double factor = Math.max(factorX, factorY);
        if (factor >= 1.0) {
            Point p = getScrollPane().getViewport().getViewPosition();
            setZoomFactor(getZoomFactor() / factor);
            r2.x /= factor;
            r2.y /= factor;
            r2.width /= factor;
            r2.height /= factor;
            getScrollPane().getViewport().setViewPosition(calcCenter(r2));
        } else {
            getScrollPane().getViewport().setViewPosition(calcCenter(r2));
        }
    }

    private void addToSelection(Figure f) {
        FigureWidget w = getFigureWidget(f);
        addToSelection(w);
    }

    private void addToSelection(FigureWidget w) {
        assert !selectedWidgets.contains(w);
        selectedWidgets.add(w);
        content.add(w.getNode());
        w.setState(w.getState().deriveSelected(true));
    }

    private void setSelection(Set<Integer> nodes) {
        clearSelection();
        for (Figure f : getModel().getDiagramToView().getFigures()) {
            if (doesIntersect(f.getSource().getSourceNodesAsSet(), nodes)) {
                addToSelection(f);
            }
        }
        selectionUpdated();
        this.validate();
    }

    public void setSelection(Collection<Figure> list) {
        clearSelection();
        for (Figure f : list) {
            addToSelection(f);
        }

        selectionUpdated();
        this.validate();
    }

    public Set<Figure> getSelectedFigures() {
        Set<Figure> result = new HashSet<Figure>();
        for (Widget w : selectedWidgets) {
            if (w instanceof FigureWidget) {
                FigureWidget fw = (FigureWidget) w;
                if (fw.getState().isSelected()) {
                    result.add(fw.getFigure());
                }
            }
        }
        return result;
    }

    public Set<Integer> getSelectedNodes() {
        Set<Integer> result = new HashSet<Integer>();
        for (Widget w : selectedWidgets) {
            if (w instanceof FigureWidget) {
                FigureWidget fw = (FigureWidget) w;
                if (fw.getState().isSelected()) {
                    result.addAll(fw.getFigure().getSource().getSourceNodesAsSet());
                }
            }
        }
        return result;
    }

    private UndoRedo.Manager getUndoRedoManager() {
        if (undoRedoManager == null) {
            undoRedoManager = new UndoRedo.Manager();
            undoRedoManager.setLimit(UNDOREDO_LIMIT);
        }

        return undoRedoManager;
    }

    public UndoRedo getUndoRedo() {
        return getUndoRedoManager();
    }

    private boolean isVisible(Figure f) {
        for (Integer n : f.getSource().getSourceNodesAsSet()) {
            if (getModel().getHiddenNodes().contains(n)) {
                return false;
            }
        }
        return true;
    }

    private boolean doesIntersect(Set s1, Set s2) {
        if (s1.size() > s2.size()) {
            Set tmp = s1;
            s1 = s2;
            s2 = tmp;
        }

        for (Object o : s1) {
            if (s2.contains(o)) {
                return true;
            }
        }

        return false;
    }

    public void showNot(final Set<Integer> nodes) {
        updateHiddenNodes(nodes, true);
    }

    public void showOnly(final Set<Integer> nodes) {
        HashSet<Integer> allNodes = new HashSet<Integer>(getModel().getGraphToView().getGroup().getAllNodes());
        allNodes.removeAll(nodes);
        updateHiddenNodes(allNodes, true);
    }

    private void updateHiddenNodes(Set<Integer> newHiddenNodes, boolean doRelayout) {

        Set<InputBlock> visibleBlocks = new HashSet<InputBlock>();

        Diagram diagram = getModel().getDiagramToView();
        assert diagram != null;

        Set<Widget> oldVisibleWidgets = new HashSet<Widget>();

        for (Figure f : diagram.getFigures()) {
            FigureWidget w = figureWidgets.get(f);
            if (w.isVisible()) {
                oldVisibleWidgets.add(w);
            }
        }

        if (getModel().getShowBlocks()) {
            for (InputBlock b : diagram.getGraph().getBlocks()) {
                BlockWidget w = blockWidgets.get(b);
                if (w.isVisible()) {
                    oldVisibleWidgets.add(w);
                }
            }
        }

        for (Figure f : diagram.getFigures()) {
            boolean hiddenAfter = doesIntersect(f.getSource().getSourceNodesAsSet(), newHiddenNodes);

            FigureWidget w = this.figureWidgets.get(f);
            w.setBoundary(false);
            if (!hiddenAfter) {
                // Figure is shown
                w.setVisible(true);
                for (InputNode n : f.getSource().getSourceNodes()) {
                    visibleBlocks.add(diagram.getGraph().getBlock(n));
                }
            } else {
                // Figure is hidden
                w.setVisible(false);
            }
        }

        if (getModel().getShowNodeHull()) {
            List<FigureWidget> boundaries = new ArrayList<FigureWidget>();
            for (Figure f : diagram.getFigures()) {
                FigureWidget w = this.figureWidgets.get(f);
                if (!w.isVisible()) {
                    Set<Figure> set = new HashSet<Figure>(f.getPredecessorSet());
                    set.addAll(f.getSuccessorSet());

                    boolean b = false;
                    for (Figure neighbor : set) {
                        FigureWidget neighborWidget = figureWidgets.get(neighbor);
                        if (neighborWidget.isVisible()) {
                            b = true;
                            break;
                        }
                    }

                    if (b) {
                        w.setBoundary(true);
                        for (InputNode n : f.getSource().getSourceNodes()) {
                            visibleBlocks.add(diagram.getGraph().getBlock(n));
                        }
                        boundaries.add(w);
                    }
                }
            }

            for (FigureWidget w : boundaries) {
                if (w.isBoundary()) {
                    w.setVisible(true);
                }
            }
        }

        if (getModel().getShowBlocks()) {
            for (InputBlock b : diagram.getGraph().getBlocks()) {

                boolean visibleAfter = visibleBlocks.contains(b);

                BlockWidget w = blockWidgets.get(b);
                if (visibleAfter) {
                    // Block must be shown
                    w.setVisible(true);
                } else {
                    // Block must be hidden
                    w.setVisible(false);
                }
            }
        }

        getModel().setHiddenNodes(newHiddenNodes);
        if (doRelayout) {
            relayout(oldVisibleWidgets);
        }
        this.validate();
        addUndo();
    }

    private void showFigures(Collection<Figure> f) {
        HashSet<Integer> newHiddenNodes = new HashSet<Integer>(getModel().getHiddenNodes());
        for (Figure fig : f) {
            newHiddenNodes.removeAll(fig.getSource().getSourceNodesAsSet());
        }
        updateHiddenNodes(newHiddenNodes, true);
    }

    private void showFigure(Figure f) {
        HashSet<Integer> newHiddenNodes = new HashSet<Integer>(getModel().getHiddenNodes());
        newHiddenNodes.removeAll(f.getSource().getSourceNodesAsSet());
        updateHiddenNodes(newHiddenNodes, true);
    }

    public void showAll(final Collection<Figure> f) {
        showFigures(f);

    }

    public void show(final Figure f) {
        showFigure(f);
    }

    public void gotoFigure(final Figure f) {

        if (!isVisible(f)) {
            showFigure(f);
        }

        FigureWidget fw = getFigureWidget(f);
        if (fw != null) {
            Rectangle r = fw.getBounds();
            Point p = fw.getLocation();
            centerRectangle(new Rectangle(p.x, p.y, r.width, r.height));

            // Select figure
            clearSelection();
            addToSelection(fw);
            selectionUpdated();
        }
    }

    public JPopupMenu createPopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        for (Action a : actions) {
            if (a == null) {
                menu.addSeparator();
            } else {
                menu.add(a);
            }
        }
        return menu;
    }

    private static class DiagramUndoRedo extends AbstractUndoableEdit implements ChangedListener<DiagramViewModel> {

        private DiagramViewModel oldModel;
        private DiagramViewModel newModel;
        private Point oldScrollPosition;
        private DiagramScene scene;

        public DiagramUndoRedo(DiagramScene scene, Point oldScrollPosition, DiagramViewModel oldModel, DiagramViewModel newModel) {
            assert oldModel != null;
            assert newModel != null;
            this.oldModel = oldModel;
            this.newModel = newModel;
            this.scene = scene;
            this.oldScrollPosition = oldScrollPosition;
        }

        @Override
        public void redo() throws CannotRedoException {
            super.redo();
            boolean b = scene.getUndoRedoEnabled();
            scene.setUndoRedoEnabled(false);
            scene.getModel().getViewChangedEvent().addListener(this);
            scene.getModel().setData(newModel);
            scene.getModel().getViewChangedEvent().removeListener(this);
            scene.setUndoRedoEnabled(b);
        }

        @Override
        public void undo() throws CannotUndoException {
            super.undo();
            boolean b = scene.getUndoRedoEnabled();
            scene.setUndoRedoEnabled(false);
            scene.getModel().getViewChangedEvent().addListener(this);
            scene.getModel().setData(oldModel);
            scene.getModel().getViewChangedEvent().removeListener(this);

            SwingUtilities.invokeLater(new Runnable() {

                public void run() {
                    scene.setScrollPosition(oldScrollPosition);
                }
            });

            scene.setUndoRedoEnabled(b);
        }

        public void changed(DiagramViewModel source) {
            scene.getModel().getViewChangedEvent().removeListener(this);
            if (oldModel.getSelectedNodes().equals(newModel.getHiddenNodes())) {
                scene.smallUpdate(false);
            } else {
                scene.smallUpdate(true);
            }
        }
    }
    private boolean undoRedoEnabled = true;

    public void setUndoRedoEnabled(boolean b) {
        this.undoRedoEnabled = b;
    }

    public boolean getUndoRedoEnabled() {
        return undoRedoEnabled;
    }

    public void changed(DiagramViewModel source) {
        assert source == model : "Receive only changed event from current model!";
        assert source != null;
        update();
    }

    private void addUndo() {

        DiagramViewModel newModelCopy = model.copy();

        if (undoRedoEnabled) {
            this.getUndoRedoManager().undoableEditHappened(new UndoableEditEvent(this, new DiagramUndoRedo(this, this.getScrollPosition(), modelCopy, newModelCopy)));
        }

        this.modelCopy = newModelCopy;
    }
}
