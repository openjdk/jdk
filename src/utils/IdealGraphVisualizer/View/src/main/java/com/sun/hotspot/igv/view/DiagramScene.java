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

import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.data.*;
import com.sun.hotspot.igv.graph.*;
import com.sun.hotspot.igv.hierarchicallayout.*;
import com.sun.hotspot.igv.layout.Cluster;
import com.sun.hotspot.igv.hierarchicallayout.LayoutGraph;
import com.sun.hotspot.igv.selectioncoordinator.SelectionCoordinator;
import com.sun.hotspot.igv.util.ColorIcon;
import com.sun.hotspot.igv.util.DoubleClickAction;
import com.sun.hotspot.igv.util.DoubleClickHandler;
import com.sun.hotspot.igv.util.PropertiesSheet;
import com.sun.hotspot.igv.view.actions.CustomSelectAction;
import com.sun.hotspot.igv.view.actions.CustomizablePanAction;
import com.sun.hotspot.igv.view.actions.MouseZoomAction;
import com.sun.hotspot.igv.view.widgets.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import javax.swing.*;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;
import javax.swing.border.Border;
import javax.swing.event.UndoableEditEvent;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import org.netbeans.api.visual.action.*;
import org.netbeans.api.visual.layout.LayoutFactory;
import org.netbeans.api.visual.model.*;
import org.netbeans.api.visual.widget.LayerWidget;
import org.netbeans.api.visual.widget.Widget;
import org.openide.awt.UndoRedo;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

/**
 *
 * @author Thomas Wuerthinger
 */
public class DiagramScene extends ObjectScene implements DiagramViewer, DoubleClickHandler {

    private final CustomizablePanAction panAction;
    private final WidgetAction hoverAction;
    private final WidgetAction selectAction;
    private final Lookup lookup;
    private final InstanceContent content;
    private final Action[] actions;
    private final Action[] actionsWithSelection;
    private final JScrollPane scrollPane;
    private UndoRedo.Manager undoRedoManager;
    private final LayerWidget mainLayer;
    private final LayerWidget blockLayer;
    private final LayerWidget connectionLayer;
    private final LayerWidget segmentLayer;
    private final Widget shadowWidget;
    private final Widget pointerWidget;
    private final DiagramViewModel model;
    private ModelState modelState;
    private boolean rebuilding;

    private final Map<OutputSlot, Set<LineWidget>> outputSlotToLineWidget = new HashMap<>();
    private final Map<InputSlot, Set<LineWidget>> inputSlotToLineWidget = new HashMap<>();
    private final FreeInteractiveLayoutManager freeInteractiveLayoutManager;
    private final HierarchicalStableLayoutManager hierarchicalStableLayoutManager;
    private HierarchicalLayoutManager seaLayoutManager;
    private LayoutMover layoutMover;

    /**
     * The alpha level of partially visible figures.
     */
    public static final float ALPHA = 0.4f;

    /**
     * The offset of the graph to the border of the window showing it.
     */
    public static final int BORDER_SIZE = 50;
    public static final int UNDOREDO_LIMIT = 100;
    public static final int SCROLL_UNIT_INCREMENT = 80;
    public static final int SCROLL_BLOCK_INCREMENT = 400;
    public static final float ZOOM_MAX_FACTOR = 4.0f;
    public static final float ZOOM_MIN_FACTOR = 0.25f;
    public static final float ZOOM_INCREMENT = 1.5f;
    public static final int ANIMATION_LIMIT = 40;

    @SuppressWarnings("unchecked")
    public <T> T getWidget(Object o) {
        Widget w = findWidget(o);
        return (T) w;
    }

    @SuppressWarnings("unchecked")
    public <T> T getWidget(Object o, Class<T> klass) {
        Widget w = findWidget(o);
        return (T) w;
    }

    public double getZoomMinFactor() {
        double factorWidth = scrollPane.getViewport().getViewRect().getWidth() / getBounds().getWidth() ;
        double factorHeight = scrollPane.getViewport().getViewRect().getHeight() / getBounds().getHeight();
        double zoomToFit = 0.98 * Math.min(factorWidth, factorHeight);
        return Math.min(zoomToFit, ZOOM_MIN_FACTOR);
    }

    public double getZoomMaxFactor() {
        return ZOOM_MAX_FACTOR;
    }

    @Override
    public void zoomIn(Point zoomCenter, double factor) {
        centredZoom(getZoomFactor() * factor, zoomCenter);
    }

    @Override
    public void zoomOut(Point zoomCenter, double factor) {
        centredZoom(getZoomFactor() / factor, zoomCenter);
    }

    @Override
    public void setZoomPercentage(int percentage) {
        centredZoom((double)percentage / 100.0, null);
    }

    @Override
    public int getZoomPercentage() {
        return (int) (getZoomFactor() * 100);
    }

    private void centredZoom(double zoomFactor, Point zoomCenter) {
        zoomFactor = Math.max(zoomFactor, getZoomMinFactor());
        zoomFactor = Math.min(zoomFactor,  getZoomMaxFactor());

        double oldZoom = getZoomFactor();
        Rectangle visibleRect = getView().getVisibleRect();
        if (zoomCenter == null) {
            zoomCenter = new Point(visibleRect.x + visibleRect.width / 2, visibleRect.y + visibleRect.height / 2);
            zoomCenter =  getScene().convertViewToScene(zoomCenter);
        }

        setZoomFactor(zoomFactor);
        validateAll();

        Point location = getScene().getLocation();
        visibleRect.x += (int)(zoomFactor * (double)(location.x + zoomCenter.x)) - (int)(oldZoom * (double)(location.x + zoomCenter.x));
        visibleRect.y += (int)(zoomFactor * (double)(location.y + zoomCenter.y)) - (int)(oldZoom * (double)(location.y + zoomCenter.y));

        // Ensure to be within area
        visibleRect.x = Math.max(0, visibleRect.x);
        visibleRect.y = Math.max(0, visibleRect.y);

        getView().scrollRectToVisible(visibleRect);
        validateAll();
        zoomChangedEvent.fire();
    }

    private final ChangedEvent<DiagramViewer> zoomChangedEvent = new ChangedEvent<>(this);

    @Override
    public ChangedEvent<DiagramViewer> getZoomChangedEvent() {
        return zoomChangedEvent;
    }

    private final ControllableChangedListener<SelectionCoordinator> highlightedCoordinatorListener = new ControllableChangedListener<SelectionCoordinator>() {

        @Override
        public void filteredChanged(SelectionCoordinator coordinator) {
            if (model.getGlobalSelection()) {
                Set<Integer> ids = coordinator.getHighlightedObjects();
                Set<Object> highlightedObjects = new HashSet<>();
                for (Figure figure : getModel().getDiagram().getFigures()) {
                    if (ids.contains(figure.getInputNode().getId())) {
                        highlightedObjects.add(figure);
                    }
                    for (Slot slot : figure.getSlots()) {
                        if (!Collections.disjoint(slot.getSource().getSourceNodesAsSet(), ids)) {
                            highlightedObjects.add(slot);
                        }
                    }
                }
                setHighlightedObjects(highlightedObjects);
                validateAll();
            }
        }
    };
    private final ControllableChangedListener<SelectionCoordinator> selectedCoordinatorListener = new ControllableChangedListener<SelectionCoordinator>() {

        @Override
        public void filteredChanged(SelectionCoordinator coordinator) {
            if (model.getGlobalSelection()) {
                Set<Integer> ids = coordinator.getSelectedObjects();
                Set<Figure> selectedFigures = new HashSet<>();
                for (Figure figure : getModel().getDiagram().getFigures()) {
                    if (ids.contains(figure.getInputNode().getId())) {
                        selectedFigures.add(figure);
                    }
                }
                setFigureSelection(selectedFigures);
                centerSelectedFigures();
                centerSelectedLiveRanges();
                validateAll();
            }
        }
    };

    public void colorSelectedFigures(Color color) {
        for (Figure figure : model.getSelectedFigures()) {
            figure.setCustomColor(color);
            FigureWidget figureWidget = getWidget(figure);
            if (figureWidget != null) {
                figureWidget.refreshColor();
            }
        }
        validateAll();
    }

    private Point getScrollPosition() {
        return scrollPane.getViewport().getViewPosition();
    }

    private void setScrollPosition(Point position) {
        scrollPane.getViewport().setViewPosition(position);
    }

    private JScrollPane createScrollPane(MouseZoomAction mouseZoomAction) {
        setBackground(Color.WHITE);
        setOpaque(true);

        JComponent viewComponent = createView();
        viewComponent.setBackground(Color.WHITE);
        viewComponent.setOpaque(true);

        JPanel centeringPanel = new JPanel(new GridBagLayout());
        centeringPanel.setBackground(Color.WHITE);
        centeringPanel.setOpaque(true);
        centeringPanel.add(viewComponent);

        JScrollPane scrollPane = new JScrollPane(centeringPanel,  VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_ALWAYS);
        scrollPane.setBackground(Color.WHITE);
        scrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        scrollPane.getVerticalScrollBar().setBlockIncrement(SCROLL_BLOCK_INCREMENT);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        scrollPane.getHorizontalScrollBar().setBlockIncrement(SCROLL_BLOCK_INCREMENT);
        scrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);

        // remove the default MouseWheelListener of the JScrollPane
        for (MouseWheelListener listener: scrollPane.getMouseWheelListeners()) {
            scrollPane.removeMouseWheelListener(listener);
        }

        // add a new MouseWheelListener for zooming if the mouse is outside the viewComponent
        // but still inside the scrollPane
        scrollPane.addMouseWheelListener(mouseZoomAction);
        return scrollPane;
    }

    public DiagramScene(Action[] actions, Action[] actionsWithSelection, DiagramViewModel model) {
        this.actions = actions;
        this.actionsWithSelection = actionsWithSelection;

        content = new InstanceContent();
        lookup = new AbstractLookup(content);

        setCheckClipping(true);

        MouseZoomAction mouseZoomAction = new MouseZoomAction(this);
        scrollPane = createScrollPane(mouseZoomAction);

        hoverAction = createObjectHoverAction();

        // This panAction handles the event only when the left mouse button is
        // pressed without any modifier keys, otherwise it will not consume it
        // and the selection action (below) will handle the event
        panAction = new CustomizablePanAction(MouseEvent.BUTTON1_DOWN_MASK);
        getActions().addAction(panAction);

        // handle default double-click, when not handled by other DoubleClickHandler
        getActions().addAction(new DoubleClickAction(this));

        selectAction = new CustomSelectAction(new SelectProvider() {
            public boolean isAimingAllowed(Widget widget, Point localLocation, boolean invertSelection) {
                return false;
            }

            public boolean isSelectionAllowed(Widget widget, Point localLocation, boolean invertSelection) {
                return findObject(widget) != null;
            }

            public void select(Widget widget, Point localLocation, boolean invertSelection) {
                EditorTopComponent editor = EditorTopComponent.getActive();
                if (editor != null) {
                    editor.requestActive();
                }
                Object object = findObject(widget);
                setFocusedObject(object);
                if (object != null) {
                    if (!invertSelection && getSelectedObjects().contains(object)) {
                        return;
                    }
                    userSelectionSuggested(Collections.singleton(object), invertSelection);
                } else {
                    userSelectionSuggested(Collections.emptySet(), invertSelection);
                }
            }
        });

        getActions().addAction(selectAction);

        Border emptyBorder = BorderFactory.createEmptyBorder(BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE);

        blockLayer = new LayerWidget(this);
        blockLayer.setBorder(emptyBorder);
        addChild(blockLayer);

        connectionLayer = new LayerWidget(this);
        connectionLayer.setBorder(emptyBorder);
        addChild(connectionLayer);

        segmentLayer = new LayerWidget(this);
        segmentLayer.setBorder(emptyBorder);
        addChild(segmentLayer);

        mainLayer = new LayerWidget(this);
        mainLayer.setBorder(emptyBorder);
        addChild(mainLayer);

        pointerWidget = new Widget(DiagramScene.this);
        addChild(pointerWidget);

        shadowWidget = new Widget(DiagramScene.this);
        addChild(shadowWidget);

        setLayout(LayoutFactory.createAbsoluteLayout());
        getActions().addAction(mouseZoomAction);
        getActions().addAction(ActionFactory.createPopupMenuAction((widget, localLocation) -> createPopupMenu()));

        LayerWidget selectLayer = new LayerWidget(this);
        addChild(selectLayer);
        RectangularSelectDecorator rectangularSelectDecorator = () -> {
            Widget widget = new Widget(this);
            widget.setBorder(BorderFactory.createLineBorder(Color.black, 2));
            return widget;
        };
        RectangularSelectProvider rectangularSelectProvider = rectangle -> {
            if (rectangle.width < 0) {
                rectangle.x += rectangle.width;
                rectangle.width *= -1;
            }

            if (rectangle.height < 0) {
                rectangle.y += rectangle.height;
                rectangle.height *= -1;
            }

            clearSelectedElements();
            Set<Object> selectedObjects = new HashSet<>();
            for (Figure f : getModel().getDiagram().getFigures()) {
                FigureWidget w = getWidget(f);
                if (w != null) {
                    assert w.getBounds() != null;
                    Rectangle r = new Rectangle(w.getBounds());
                    r.setLocation(w.getLocation());

                    if (r.intersects(rectangle)) {
                        selectedObjects.add(f);
                    }

                    for (Slot s : f.getSlots()) {
                        SlotWidget sw = getWidget(s);
                        assert sw.getBounds() != null;
                        Rectangle r2 = new Rectangle(sw.getBounds());
                        r2.setLocation(sw.convertLocalToScene(new Point(0, 0)));

                        if (r2.intersects(rectangle)) {
                            selectedObjects.add(s);
                        }
                    }
                } else {
                    assert false : "w should not be null here!";
                }
            }

            Set<Object> symmetricDiff = new HashSet<>(getSelectedObjects());
            symmetricDiff.addAll(selectedObjects);
            Set<Object> tmp = new HashSet<>(getSelectedObjects());
            tmp.retainAll(selectedObjects);
            symmetricDiff.removeAll(tmp);
            setSelectedObjects(symmetricDiff);
        };
        getActions().addAction(ActionFactory.createRectangularSelectAction(rectangularSelectDecorator, selectLayer, rectangularSelectProvider));

        ObjectSceneListener selectionChangedListener = new ObjectSceneListener() {

            @Override
            public void objectAdded(ObjectSceneEvent arg0, Object arg1) {}

            @Override
            public void objectRemoved(ObjectSceneEvent arg0, Object arg1) {}

            @Override
            public void objectStateChanged(ObjectSceneEvent e, Object o, ObjectState oldState, ObjectState newState) {}

            @Override
            public void selectionChanged(ObjectSceneEvent e, Set<Object> oldSet, Set<Object> newSet) {
                DiagramScene scene = (DiagramScene) e.getObjectScene();
                if (scene.isRebuilding()) {
                    return;
                }

                // Remove duplicate live range segments (i.e. segments that
                // represent the same live range in different basic blocks).
                Set<Object> newUnique = new HashSet<>();
                Set<Integer> visitedLiveRanges = new HashSet<>();
                for (Object o : newSet) {
                    if (o instanceof Properties.Provider &&
                        o instanceof LiveRangeSegment) {
                        int liveRangeId = ((LiveRangeSegment) o).getLiveRange().getId();
                        if (!visitedLiveRanges.contains(liveRangeId)) {
                            newUnique.add(o);
                            visitedLiveRanges.add(liveRangeId);
                        }
                    } else {
                        newUnique.add(o);
                    }
                }

                content.set(newUnique, null);

                Set<Integer> nodeSelection = new HashSet<>();
                Set<Integer> liveRangeSelection = new HashSet<>();
                for (Object o : newUnique) {
                    if (o instanceof Properties.Provider) {
                        final Properties.Provider provider = (Properties.Provider) o;
                        AbstractNode node = new AbstractNode(Children.LEAF) {

                            @Override
                            protected Sheet createSheet() {
                                Sheet s = super.createSheet();
                                PropertiesSheet.initializeSheet(provider.getProperties(), s);
                                return s;
                            }
                        };
                        String displayName = null;
                        if (o instanceof Figure || o instanceof Slot) {
                            displayName = provider.getProperties().get("idx") + " " +
                                          provider.getProperties().get("name");
                        } else if (o instanceof LiveRangeSegment) {
                            displayName = "L" + ((LiveRangeSegment) o).getLiveRange().getId();
                        }
                        node.setDisplayName(displayName);
                        content.add(node);
                    }


                    if (o instanceof Figure) {
                        nodeSelection.add(((Figure) o).getInputNode().getId());
                    } else if (o instanceof Slot) {
                        nodeSelection.addAll(((Slot) o).getSource().getSourceNodesAsSet());
                    } else if (o instanceof LiveRangeSegment) {
                        liveRangeSelection.add(((LiveRangeSegment) o).getLiveRange().getId());
                    }
                }
                getModel().setSelectedNodes(nodeSelection);
                getModel().setSelectedLiveRanges(liveRangeSelection);

                boolean b = selectedCoordinatorListener.isEnabled();
                selectedCoordinatorListener.setEnabled(false);
                SelectionCoordinator.getInstance().setSelectedObjects(nodeSelection);
                selectedCoordinatorListener.setEnabled(b);
            }

            @Override
            public void highlightingChanged(ObjectSceneEvent e, Set<Object> oldSet, Set<Object> newSet) {
                Set<Integer> nodeHighlighting = new HashSet<>();
                for (Object o : newSet) {
                    if (o instanceof Figure) {
                        nodeHighlighting.add(((Figure) o).getInputNode().getId());
                    } else if (o instanceof Slot) {
                        nodeHighlighting.addAll(((Slot) o).getSource().getSourceNodesAsSet());
                    }
                }
                highlightedCoordinatorListener.setEnabled(false);
                SelectionCoordinator.getInstance().setHighlightedObjects(nodeHighlighting);
                highlightedCoordinatorListener.setEnabled(true);
            }

            @Override
            public void hoverChanged(ObjectSceneEvent e, Object oldObject, Object newObject) {
                Set<Object> newHighlightedObjects = new HashSet<>(getHighlightedObjects());
                if (oldObject != null) {
                    newHighlightedObjects.remove(oldObject);
                }
                if (newObject != null) {
                    newHighlightedObjects.add(newObject);
                }
                setHighlightedObjects(newHighlightedObjects);
            }

            @Override
            public void focusChanged(ObjectSceneEvent arg0, Object arg1, Object arg2) {
            }
        };
        addObjectSceneListener(selectionChangedListener, ObjectSceneEventType.OBJECT_SELECTION_CHANGED, ObjectSceneEventType.OBJECT_HIGHLIGHTING_CHANGED, ObjectSceneEventType.OBJECT_HOVER_CHANGED);

        model.getDiagramChangedEvent().addListener(m -> update());
        model.getGraphChangedEvent().addListener(m -> graphChanged());
        model.getHiddenNodesChangedEvent().addListener(m -> hiddenNodesChanged());
        scrollPane.addHierarchyBoundsListener(new HierarchyBoundsListener() {
            @Override
            public void ancestorMoved(HierarchyEvent e) {}

            @Override
            public void ancestorResized(HierarchyEvent e) {
                if (scrollPane.getBounds().width > 0) {
                    centerRootNode();
                    scrollPane.removeHierarchyBoundsListener(this);
                }
            }
        });

        freeInteractiveLayoutManager = new FreeInteractiveLayoutManager();
        hierarchicalStableLayoutManager = new HierarchicalStableLayoutManager();
        seaLayoutManager = new HierarchicalLayoutManager();

        this.model = model;
        modelState = new ModelState(model);
    }

    @Override
    public DiagramViewModel getModel() {
        return model;
    }

    @Override
    public Component getComponent() {
        return scrollPane;
    }

    public boolean isAllVisible() {
        return model.getHiddenNodes().isEmpty();
    }

    public Action createGotoAction(final Figure figure) {
        String name = figure.getLines()[0];
        name += " (";
        if (figure.getCluster() != null) {
            name += "B" + figure.getCluster().toString();
        }
        boolean isHidden = !getWidget(figure, FigureWidget.class).isVisible();
        if (isHidden) {
            if (figure.getCluster() != null) {
                name += ", ";
            }
            name += "hidden";
        }
        name += ")";
        Action action = new AbstractAction(name, new ColorIcon(figure.getColor())) {
            @Override
            public void actionPerformed(ActionEvent e) {
                setFigureSelection(Collections.singleton(figure));
                model.showFigures(model.getSelectedFigures());
                centerSelectedFigures();
            }
        };

        action.setEnabled(true);
        return action;
    }

    public Action createGotoAction(final Block block) {
        String name = "B" + block.getInputBlock().getName();
        Action action = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                gotoBlock(block);
            }
        };
        action.setEnabled(true);
        return action;
    }

    public Action createGotoNodesAction(String name, Set<Figure> figures) {
        String iconResource = "com/sun/hotspot/igv/view/images/selectNodes.png";
        Action action = new AbstractAction(name, new ImageIcon(ImageUtilities.loadImage(iconResource))) {
            @Override
            public void actionPerformed(ActionEvent e) {
                setFigureSelection(figures);
                model.showFigures(model.getSelectedFigures());
                centerSelectedFigures();
            }
        };

        action.setEnabled(true);
        return action;
    }

    private Action createGotoLiveRangeAction(String name, String iconResource, Set<InputLiveRange> liveRanges) {
        Action action = new AbstractAction(name, new ImageIcon(ImageUtilities.loadImage(iconResource))) {
            @Override
            public void actionPerformed(ActionEvent e) {
                Set<LiveRangeSegment> segments = liveRangeSegmentSet(liveRanges);
                setLiveRangeSegmentSelection(segments);
                Diagram diagram = getModel().getDiagram();
                Set<Figure> figures = new HashSet<>();
                for (InputLiveRange liveRange : liveRanges) {
                    for (InputNode node : diagram.getInputGraph().getRelatedNodes(liveRange.getId())) {
                        figures.add((diagram.getFigure(node)));
                    }
                }
                model.showFigures(figures);
                centerSelectedLiveRanges();
            }
        };

        action.setEnabled(true);
        return action;
    }

    public Action createGotoLiveRangeAction(String name, Set<InputLiveRange> liveRanges) {
        return createGotoLiveRangeAction(name, "com/sun/hotspot/igv/view/images/selectLiveRanges.png", liveRanges);
    }

    public Action createGotoLiveRangeAction(InputLiveRange liveRange) {
        return createGotoLiveRangeAction("L" + liveRange.getId(),
                                         "com/sun/hotspot/igv/view/images/liveRange.png",
                                         Collections.singleton(liveRange));
    }

    private void clearObjects() {
        Set<Object> objectSet = new HashSet<>(getObjects());
        for (Object object : objectSet) {
            if (isObject(object)) {
                removeObject(object);
            }
        }
    }

    private void updateFigureTexts() {
        for (Figure figure : getModel().getDiagram().getFigures()) {
            // Update node text, since it might differ across views.
            figure.updateLines();
        }
    }

    private void updateFigureWidths() {
        if (getModel().getShowCFG()) {
            Map<Block, Integer> maxWidth = new HashMap<>();
            for (Block block : getModel().getDiagram().getBlocks()) {
                maxWidth.put(block, 10);
            }
            for (Figure figure : getModel().getDiagram().getFigures()) {
                // Compute max node width in each block.
                if (figure.getWidth() > maxWidth.get(figure.getBlock())) {
                    maxWidth.put(figure.getBlock(), figure.getWidth());
                }
            }
            for (Figure figure : getModel().getDiagram().getFigures()) {
                // Set all nodes' width to the maximum width in the blocks?
                figure.setWidth(maxWidth.get(figure.getBlock()));
            }
        }
    }

    private MoveProvider getFigureMoveProvider() {
        return new MoveProvider() {

            private boolean hasMoved = false; // Flag to track movement
            private int startLayerY;

            private void setFigureShadow(Figure f) {
                FigureWidget fw = getWidget(f);
                Color c = f.getColor();
                Border border = new FigureWidget.RoundedBorder(new Color(0,0,0, 50), 1);
                shadowWidget.setBorder(border);
                shadowWidget.setBackground(new Color(c.getRed(), c.getGreen(), c.getBlue(), 50));
                shadowWidget.setPreferredLocation(fw.getPreferredLocation());
                shadowWidget.setPreferredSize(f.getSize());
                shadowWidget.setVisible(true);
                shadowWidget.setOpaque(true);
                shadowWidget.revalidate();
                shadowWidget.repaint();
            }

            private void setMovePointer(Figure f) {
                Border border = new FigureWidget.RoundedBorder(Color.RED, 1);
                pointerWidget.setBorder(border);
                pointerWidget.setBackground(Color.RED);
                pointerWidget.setPreferredBounds(new Rectangle(0, 0, 3, f.getSize().height));
                pointerWidget.setVisible(false);
                pointerWidget.setOpaque(true);
            }


            @Override
            public void movementStarted(Widget widget) {
                if (layoutMover == null) return; // Do nothing if layoutMover is not available

                widget.bringToFront();
                startLayerY = widget.getLocation().y;
                hasMoved = false; // Reset the movement flag
                if (layoutMover.isFreeForm()) return;

                Set<Figure> selectedFigures = model.getSelectedFigures();
                if (selectedFigures.size() == 1) {
                    Figure selectedFigure = selectedFigures.iterator().next();
                    setFigureShadow(selectedFigure);
                    setMovePointer(selectedFigure);
                }
            }

            @Override
            public void movementFinished(Widget widget) {
                shadowWidget.setVisible(false);
                pointerWidget.setVisible(false);
                if (layoutMover == null || !hasMoved) return; // Do nothing if layoutMover is not available or no movement occurred
                rebuilding = true;

                Set<Figure> movedFigures = new HashSet<>(model.getSelectedFigures());
                for (Figure figure : movedFigures) {
                    FigureWidget fw = getWidget(figure);
                    figure.setPosition(new Point(fw.getLocation().x, fw.getLocation().y));
                }

                layoutMover.moveVertices(movedFigures);
                rebuildConnectionLayer();

                for (FigureWidget fw : getVisibleFigureWidgets()) {
                    fw.updatePosition();
                }

                validateAll();
                addUndo();
                rebuilding = false;
            }

            private static final int MAGNET_SIZE = 5;

            private int magnetToStartLayerY(Widget widget, Point location) {
                int shiftY = location.y - widget.getLocation().y;
                if (Math.abs(location.y - startLayerY) <= MAGNET_SIZE) {
                    if (Math.abs(widget.getLocation().y - startLayerY) > MAGNET_SIZE) {
                        shiftY = startLayerY - widget.getLocation().y;
                    } else {
                        shiftY = 0;
                    }
                }
                return shiftY;
            }

            @Override
            public Point getOriginalLocation(Widget widget) {
                if (layoutMover == null) return widget.getLocation(); // default behavior
                return ActionFactory.createDefaultMoveProvider().getOriginalLocation(widget);
            }

            @Override
            public void setNewLocation(Widget widget, Point location) {
                if (layoutMover == null) return; // Do nothing if layoutMover is not available
                hasMoved = true; // Mark that a movement occurred

                int shiftX = location.x - widget.getLocation().x;
                int shiftY;
                if (layoutMover.isFreeForm()) {
                    shiftY = location.y - widget.getLocation().y;
                } else {
                    shiftY = magnetToStartLayerY(widget, location);
                }
                List<Figure> selectedFigures = new ArrayList<>( model.getSelectedFigures());
                selectedFigures.sort(Comparator.comparingInt(f -> f.getPosition().x));
                for (Figure figure : selectedFigures) {
                    FigureWidget fw = getWidget(figure);
                    for (InputSlot inputSlot : figure.getInputSlots()) {
                        assert inputSlot != null;
                        if (inputSlotToLineWidget.containsKey(inputSlot)) {
                            for (LineWidget lw : inputSlotToLineWidget.get(inputSlot)) {
                                assert lw != null;
                                Point fromPt = lw.getFrom();
                                Point toPt = lw.getTo();
                                if (toPt == null || fromPt == null) {
                                    continue;
                                }
                                int xTo = toPt.x + shiftX;
                                int yTo = toPt.y + shiftY;
                                lw.setTo(new Point(xTo, yTo));
                                if (!layoutMover.isFreeForm()) {
                                    lw.setFrom(new Point(fromPt.x + shiftX, fromPt.y));
                                    LineWidget pred = lw.getPredecessor();
                                    pred.setTo(new Point(pred.getTo().x + shiftX, pred.getTo().y));
                                    pred.revalidate();
                                    lw.revalidate();
                                }
                            }
                        }
                    }
                    for (OutputSlot outputSlot : figure.getOutputSlots()) {
                        assert outputSlot != null;
                        if (outputSlotToLineWidget.containsKey(outputSlot)) {
                            for (LineWidget lw : outputSlotToLineWidget.get(outputSlot)) {
                                assert lw != null;
                                Point fromPt = lw.getFrom();
                                Point toPt = lw.getTo();
                                if (toPt == null || fromPt == null) {
                                    continue;
                                }
                                int xFrom = fromPt.x + shiftX;
                                int yFrom = fromPt.y + shiftY;
                                lw.setFrom(new Point(xFrom, yFrom));
                                if (!layoutMover.isFreeForm()) {
                                    lw.setTo(new Point(toPt.x + shiftX, toPt.y));
                                    for (LineWidget succ : lw.getSuccessors()) {
                                        succ.setFrom(new Point(succ.getFrom().x + shiftX, succ.getFrom().y));
                                        succ.revalidate();
                                    }
                                    lw.revalidate();
                                }
                            }
                        }
                    }
                    Point newLocation = new Point(fw.getLocation().x + shiftX, fw.getLocation().y + shiftY);
                    ActionFactory.createDefaultMoveProvider().setNewLocation(fw, newLocation);
                }

                if (selectedFigures.size() == 1 && !layoutMover.isFreeForm()) {
                    FigureWidget fw = getWidget(selectedFigures.iterator().next());
                    pointerWidget.setVisible(true);
                    Point newLocation = new Point(fw.getLocation().x + shiftX -3, fw.getLocation().y + shiftY);
                    ActionFactory.createDefaultMoveProvider().setNewLocation(pointerWidget, newLocation);
                }
                connectionLayer.revalidate();
                connectionLayer.repaint();
            }
        };
    }

    private void rebuildMainLayer() {
        mainLayer.removeChildren();
        for (Figure figure : getModel().getDiagram().getFigures()) {
            FigureWidget figureWidget = new FigureWidget(figure, this);
            figureWidget.setVisible(false);
            figureWidget.getActions().addAction(ActionFactory.createPopupMenuAction(figureWidget));
            figureWidget.getActions().addAction(selectAction);
            figureWidget.getActions().addAction(hoverAction);
            figureWidget.getActions().addAction(ActionFactory.createMoveAction(null, getFigureMoveProvider()));
            addObject(figure, figureWidget);
            mainLayer.addChild(figureWidget);

            for (InputSlot inputSlot : figure.getInputSlots()) {
                SlotWidget slotWidget = new InputSlotWidget(inputSlot, this, figureWidget);
                slotWidget.getActions().addAction(new DoubleClickAction(slotWidget));
                slotWidget.getActions().addAction(hoverAction);
                slotWidget.getActions().addAction(selectAction);
                addObject(inputSlot, slotWidget);
            }

            for (OutputSlot outputSlot : figure.getOutputSlots()) {
                SlotWidget slotWidget = new OutputSlotWidget(outputSlot, this, figureWidget);
                slotWidget.getActions().addAction(new DoubleClickAction(slotWidget));
                slotWidget.getActions().addAction(hoverAction);
                slotWidget.getActions().addAction(selectAction);
                addObject(outputSlot, slotWidget);
            }
        }
    }

    private void rebuildBlockLayer() {
        blockLayer.removeChildren();
        if (getModel().getShowBlocks() || getModel().getShowCFG()) {
            for (Block block : getModel().getDiagram().getBlocks()) {
                BlockWidget blockWidget = new BlockWidget(this, block);
                blockWidget.getActions().addAction(new DoubleClickAction(blockWidget));
                blockWidget.setVisible(false);
                addObject(block, blockWidget);
                blockLayer.addChild(blockWidget);
            }
        }
    }

    private void update() {
        rebuilding = true;
        clearObjects();
        updateFigureTexts();
        updateFigureWidths();
        rebuildMainLayer();
        rebuildBlockLayer();
        rebuildSegmentLayer();
        relayout();
        rebuilding = false;
    }

    private void hiddenNodesChanged() {
        relayout();
        addUndo();
    }

    private void relayout() {
        rebuilding = true;
        Set<FigureWidget> oldVisibleFigureWidgets = getVisibleFigureWidgets();
        Set<LiveRangeWidget> oldVisibleLiveRangeWidgets = getVisibleLiveRangeWidgets();
        Set<BlockWidget> oldVisibleBlockWidgets = getVisibleBlockWidgets();

        updateVisibleFigureWidgets();
        updateNodeHull();
        updateVisibleLiveRangeWidgets();
        updateVisibleBlockWidgets();
        validateAll();

        Set<Figure> visibleFigures = getVisibleFigures();
        Set<Connection> visibleConnections = getVisibleConnections();
        List<LiveRangeSegment> visibleLiveRangeSegments = getVisibleLiveRangeSegments();
        if (getModel().getShowFreeInteractive()) {
            doFreeInteractiveLayout(visibleFigures, visibleConnections);
        } else if (getModel().getShowStableSea()) {
            doStableSeaLayout(visibleFigures, visibleConnections);
        } else if (getModel().getShowSea()) {
            doSeaLayout(visibleFigures, visibleConnections);
        } else if (getModel().getShowBlocks()) {
            doClusteredLayout(visibleFigures, visibleConnections);
        } else if (getModel().getShowCFG()) {
            doCFGLayout(visibleFigures, visibleConnections, visibleLiveRangeSegments);
        }
        rebuildConnectionLayer();
        if (getModel().getShowCFG() && getModel().getShowLiveRanges()) {
            updateLiveRangeIdsInBlockWidgets();
            repaintLiveRangeWidgets();
        }

        updateFigureWidgetLocations(oldVisibleFigureWidgets);
        updateLiveRangeWidgetLocations(oldVisibleLiveRangeWidgets);
        updateBlockWidgetBounds(oldVisibleBlockWidgets);
        validateAll();
        setElementSelection(model.getSelectedFigures(),
                            model.getSelectedLiveRangeSegments());
        centerSelectedFigures();
        centerSelectedLiveRanges();
        rebuilding = false;
    }

    public void validateAll() {
        validate();
        scrollPane.validate();
    }

    private void graphChanged() {
        centerRootNode();
        addUndo();
    }

    private void centerRootNode() {
        if (getModel().getSelectedNodes().isEmpty()) {
            Figure rootFigure = getModel().getDiagram().getRootFigure();
            if (rootFigure != null) {
                int rootId = rootFigure.getInputNode().getId();
                if (!getModel().getHiddenNodes().contains(rootId)) {
                    FigureWidget rootWidget = getWidget(rootFigure);
                    if (rootWidget != null) {
                        Rectangle bounds = rootWidget.getBounds();
                        if (bounds != null) {
                            Point location = rootWidget.getLocation();
                            centerRectangle(new Rectangle(location.x, location.y, bounds.width, bounds.height));
                        }
                    }
                }
            }
        }
    }

    protected boolean isRebuilding() {
        return rebuilding;
    }

    public boolean isVisibleBlockConnection(BlockConnection blockConnection) {
        Widget w1 = getWidget(blockConnection.getFromCluster());
        Widget w2 = getWidget(blockConnection.getToCluster());
        return w1.isVisible() && w2.isVisible();
    }

    private boolean isVisibleFigureConnection(FigureConnection figureConnection) {
        // Generally, a connection is visible if its source and destination
        // widgets are visible. An exception is Figure connections in the CFG
        // view, which are never shown.
        if (getModel().getShowCFG()) {
            return false;
        }
        Widget w1 = getWidget(figureConnection.getFrom().getVertex());
        Widget w2 = getWidget(figureConnection.getTo().getVertex());
        return w1.isVisible() && w2.isVisible();
    }

    private boolean isVisibleBlock(Block b) {
        BlockWidget bw = getWidget(b);
        return bw != null && getWidget(b, BlockWidget.class).isVisible();
    }

    private boolean isVisibleLiveRange(int liveRangeId) {
        if (!getModel().getShowLiveRanges()) {
            return false;
        }
        Set<InputNode> relatedNodes = getModel().getGraph().getRelatedNodes(liveRangeId);
        for (InputNode n : relatedNodes) {
            if (!getModel().getDiagram().hasFigure(n)) {
                return false;
            }
            Figure f = getModel().getDiagram().getFigure(n);
            FigureWidget fw = getWidget(f);
            if (isVisibleBlock(f.getBlock()) &&
                (fw == null || !fw.isVisible())) {
                return false;
            }
        }
        return true;
    }

    private boolean isVisibleLiveRangeSegment(LiveRangeSegment s) {
        return isVisibleLiveRange(s.getLiveRange().getId()) &&
               isVisibleBlock(s.getCluster());
    }


    private void doFreeInteractiveLayout(Set<Figure> visibleFigures, Set<Connection> visibleConnections) {
        layoutMover = freeInteractiveLayoutManager;
        freeInteractiveLayoutManager.setCutEdges(model.getCutEdges());
        freeInteractiveLayoutManager.doLayout(new LayoutGraph(visibleConnections, visibleFigures));
    }

    private void doStableSeaLayout(Set<Figure> visibleFigures, Set<Connection> visibleConnections) {
        layoutMover = null;
        boolean enable = model.getCutEdges();
        boolean previous = hierarchicalStableLayoutManager.getCutEdges();
        hierarchicalStableLayoutManager.setCutEdges(enable);
        if (enable != previous) {
            hierarchicalStableLayoutManager.doLayout(new LayoutGraph(visibleConnections, visibleFigures));
        } else {
            hierarchicalStableLayoutManager.updateLayout(visibleFigures, visibleConnections);
        }
    }

    private void doSeaLayout(Set<Figure> visibleFigures, Set<Connection> visibleConnections) {
        seaLayoutManager = new HierarchicalLayoutManager();
        layoutMover = seaLayoutManager;
        seaLayoutManager.setCutEdges(model.getCutEdges());
        seaLayoutManager.doLayout(new LayoutGraph(visibleConnections, visibleFigures));
    }

    private void doClusteredLayout(Set<Figure> visibleFigures, Set<Connection> visibleConnections) {
        layoutMover = null;
        HierarchicalClusterLayoutManager clusterLayoutManager = new HierarchicalClusterLayoutManager();
        clusterLayoutManager.setCutEdges(model.getCutEdges());
        clusterLayoutManager.doLayout(new LayoutGraph(visibleConnections, visibleFigures));
    }

    private void doCFGLayout(Set<Figure> visibleFigures, Set<Connection> visibleConnections, List<LiveRangeSegment> segments) {
        layoutMover = null;
        HierarchicalCFGLayoutManager cfgLayoutManager = new HierarchicalCFGLayoutManager(getVisibleBlockConnections(), getVisibleBlocks());
        cfgLayoutManager.setCutEdges(model.getCutEdges());
        cfgLayoutManager.setSegments(new ArrayList<>(segments));
        cfgLayoutManager.doLayout(new LayoutGraph(visibleConnections, visibleFigures));
    }

    private boolean shouldAnimate() {
        int visibleFigureCount = 0;
        for (Figure figure : getModel().getDiagram().getFigures()) {
            if (getWidget(figure, FigureWidget.class).isVisible()) {
                visibleFigureCount++;
            }
        }
        return visibleFigureCount <= ANIMATION_LIMIT;
    }

    private final Point specialNullPoint = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);

    private MoveProvider getFigureConnectionMoveProvider() {
        return new MoveProvider() {

            Point startLocation;
            Point originalPosition;

            @Override
            public void movementStarted(Widget widget) {
                if (layoutMover == null) return; // Do nothing if layoutMover is not available
                LineWidget lw = (LineWidget) widget;
                startLocation = lw.getClientAreaLocation();
                originalPosition = lw.getFrom();
            }

            @Override
            public void movementFinished(Widget widget) {
                if (layoutMover == null) return; // Do nothing if layoutMover is not available
                LineWidget lineWidget = (LineWidget) widget;
                if (lineWidget.getPredecessor() == null) return;
                if (lineWidget.getSuccessors().isEmpty()) return;
                if (lineWidget.getFrom().x != lineWidget.getTo().x) return;

                int shiftX = lineWidget.getClientAreaLocation().x - startLocation.x;
                if (shiftX == 0) return;

                rebuilding = true;
                layoutMover.moveLink(originalPosition, shiftX);
                rebuildConnectionLayer();
                for (FigureWidget fw : getVisibleFigureWidgets()) {
                    fw.updatePosition();
                }
                validateAll();
                addUndo();
                rebuilding = false;
            }

            @Override
            public Point getOriginalLocation(Widget widget) {
                if (layoutMover == null) return widget.getLocation(); // default behavior
                LineWidget lineWidget = (LineWidget) widget;
                return lineWidget.getClientAreaLocation();
            }

            @Override
            public void setNewLocation(Widget widget, Point location) {
                if (layoutMover == null) return; // Do nothing if layoutMover is not available
                LineWidget lineWidget = (LineWidget) widget;
                if (lineWidget.getPredecessor() == null) return;
                if (lineWidget.getSuccessors().isEmpty()) return;
                if (lineWidget.getFrom().x != lineWidget.getTo().x) return;

                int shiftX = location.x - lineWidget.getClientAreaLocation().x;
                if (shiftX == 0) return;

                Point oldFrom = lineWidget.getFrom();
                Point newFrom = new Point(oldFrom.x + shiftX, oldFrom.y);

                Point oldTo = lineWidget.getTo();
                Point newTo = new Point(oldTo.x + shiftX, oldTo.y);

                lineWidget.setTo(newTo);
                lineWidget.setFrom(newFrom);
                lineWidget.revalidate();

                LineWidget predecessor = lineWidget.getPredecessor();
                Point toPt = predecessor.getTo();
                predecessor.setTo(new Point(toPt.x + shiftX, toPt.y));
                predecessor.revalidate();

                for (LineWidget successor : lineWidget.getSuccessors()) {
                    Point fromPt = successor.getFrom();
                    successor.setFrom(new Point(fromPt.x + shiftX, fromPt.y));
                    successor.revalidate();
                }
            }
        };
    }

    private void processOutputSlot(OutputSlot outputSlot, List<FigureConnection> connections, int controlPointIndex, Point lastPoint, LineWidget predecessor) {
        Map<Point, List<FigureConnection>> pointMap = new HashMap<>(connections.size());

        for (FigureConnection connection : connections) {
            if (isVisibleFigureConnection(connection)) {
                List<Point> controlPoints = connection.getControlPoints();
                if (controlPointIndex < controlPoints.size()) {
                    Point currentPoint = controlPoints.get(controlPointIndex);
                    if (currentPoint == null) { // Long connection, has been cut vertically.
                        currentPoint = specialNullPoint;
                    } else {
                        currentPoint = new Point(currentPoint.x, currentPoint.y);
                    }
                    if (pointMap.containsKey(currentPoint)) {
                        pointMap.get(currentPoint).add(connection);
                    } else {
                        pointMap.put(currentPoint, new ArrayList<>(Collections.singletonList(connection)));
                    }
                }
            }
        }

        for (Point currentPoint : pointMap.keySet()) {
            List<FigureConnection> connectionList = pointMap.get(currentPoint);

            boolean isBold = false;
            boolean isDashed = true;
            boolean isVisible = true;
            for (FigureConnection c : connectionList) {
                if (c.getStyle() == Connection.ConnectionStyle.BOLD) {
                    isBold = true;
                } else if (c.getStyle() == Connection.ConnectionStyle.INVISIBLE) {
                    isVisible = false;
                }
                if (c.getStyle() != Connection.ConnectionStyle.DASHED) {
                    isDashed = false;
                }
            }

            LineWidget newPredecessor = predecessor;
            if (currentPoint != specialNullPoint && lastPoint != specialNullPoint && lastPoint != null) {
                Point src = new Point(lastPoint);
                Point dest = new Point(currentPoint);
                newPredecessor = new LineWidget(this, outputSlot, connectionList, src, dest, predecessor, isBold, isDashed);
                newPredecessor.setVisible(isVisible);

                if (predecessor == null) {
                    assert outputSlot != null;
                    if (outputSlotToLineWidget.containsKey(outputSlot)) {
                        outputSlotToLineWidget.get(outputSlot).add(newPredecessor);
                    } else {
                        outputSlotToLineWidget.put(outputSlot, new HashSet<>(Collections.singleton(newPredecessor)));
                    }
                }

                newWidgets.add(newPredecessor);
                addObject(new ConnectionSet(connectionList), newPredecessor);
                newPredecessor.getActions().addAction(hoverAction);
                newPredecessor.getActions().addAction(ActionFactory.createMoveAction(null, getFigureConnectionMoveProvider()));
            }

            processOutputSlot(outputSlot, connectionList, controlPointIndex + 1, currentPoint, newPredecessor);
        }

        if (pointMap.isEmpty()) {
            for (FigureConnection connection : connections) {
                if (isVisibleFigureConnection(connection)) {
                    InputSlot inputSlot = connection.getInputSlot();
                    if (predecessor != null) {
                        assert inputSlot != null;
                        if (inputSlotToLineWidget.containsKey(inputSlot)) {
                            inputSlotToLineWidget.get(inputSlot).add(predecessor);
                        } else {
                            inputSlotToLineWidget.put(inputSlot, new HashSet<>(Collections.singleton(predecessor)));
                        }
                    }
                }
            }
        }
    }

    private void processFreeForm(OutputSlot outputSlot, List<FigureConnection> connections) {
        for (FigureConnection connection : connections) {
            if (isVisibleFigureConnection(connection)) {
                boolean isBold = false;
                boolean isDashed = true;
                boolean isVisible = true;
                if (connection.getStyle() == Connection.ConnectionStyle.BOLD) {
                    isBold = true;
                } else if (connection.getStyle() == Connection.ConnectionStyle.INVISIBLE) {
                    isVisible = false;
                }
                if (connection.getStyle() != Connection.ConnectionStyle.DASHED) {
                    isDashed = false;
                }


                List<Point> controlPoints = connection.getControlPoints();
                if (controlPoints.size() <= 2) continue;
                Point firstPoint = controlPoints.get(0); // First point
                Point lastPoint = controlPoints.get(controlPoints.size() - 1); // Last point
                List<FigureConnection> connectionList = new ArrayList<>(Collections.singleton(connection));
                LineWidget line = new LineWidget(this, outputSlot, connectionList, firstPoint, lastPoint, null, isBold, isDashed);
                line.setFromControlYOffset(50);
                line.setToControlYOffset(-50);
                line.setVisible(isVisible);
                connectionLayer.addChild(line);

                addObject(new ConnectionSet(connectionList), line);
                line.getActions().addAction(hoverAction);

                if (outputSlotToLineWidget.containsKey(outputSlot)) {
                    outputSlotToLineWidget.get(outputSlot).add(line);
                } else {
                    outputSlotToLineWidget.put(outputSlot, new HashSet<>(Collections.singleton(line)));
                }

                InputSlot inputSlot = connection.getInputSlot();
                if (inputSlotToLineWidget.containsKey(inputSlot)) {
                    inputSlotToLineWidget.get(inputSlot).add(line);
                } else {
                    inputSlotToLineWidget.put(inputSlot, new HashSet<>(Collections.singleton(line)));
                }
            }
        }
    }

    private void processBlockConnection(BlockConnection blockConnection) {
        boolean isDashed = blockConnection.getStyle() == Connection.ConnectionStyle.DASHED;
        boolean isBold = blockConnection.getStyle() == Connection.ConnectionStyle.BOLD;
        boolean isVisible = blockConnection.getStyle() != Connection.ConnectionStyle.INVISIBLE;
        Point lastPoint = null;
        LineWidget predecessor = null;
        for (Point currentPoint : blockConnection.getControlPoints()) {
            if (currentPoint == null) { // Long connection, has been cut vertically.
                currentPoint = specialNullPoint;
            } else if (lastPoint != specialNullPoint && lastPoint != null) {
                List<BlockConnection> connectionList = Collections.singletonList(blockConnection);
                Point src = new Point(lastPoint);
                Point dest = new Point(currentPoint);
                predecessor = new LineWidget(this, null, connectionList, src, dest, predecessor, isBold, isDashed);
                predecessor.setVisible(isVisible);
                connectionLayer.addChild(predecessor);
                addObject(new ConnectionSet(connectionList), predecessor);
                predecessor.getActions().addAction(hoverAction);
            }
            lastPoint = currentPoint;
        }
    }

    @Override
    public void setInteractionMode(InteractionMode mode) {
        panAction.setEnabled(mode == InteractionMode.PANNING);
        // When panAction is not enabled, it does not consume the event
        // and the selection action handles it instead
    }

    @Override
    public void handleDoubleClick(Widget w, WidgetAction.WidgetMouseEvent e) {
        clearSelectedElements();
    }

    private class ConnectionSet {

        private Collection<? extends Connection> connections;

        public ConnectionSet(Collection<? extends Connection> connections) {
            this.connections = connections;
        }
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    private void gotoBlock(final Block block) {
        BlockWidget bw = getWidget(block);
        if (bw != null) {
            centerRectangle(bw.getBounds());
        }
    }

    public Set<LiveRangeSegment> liveRangeSegmentSet(Collection<InputLiveRange> liveRanges) {
        Set<Integer> liveRangeIds = new HashSet<>();
        for (InputLiveRange liveRange : liveRanges) {
            liveRangeIds.add(liveRange.getId());
        }
        Set<LiveRangeSegment> segments = new HashSet<>();
        for (LiveRangeSegment segment : model.getDiagram().getLiveRangeSegments()) {
            if (liveRangeIds.contains(segment.getLiveRange().getId())) {
                segments.add(segment);
            }
        }
        return segments;
    }

    private Set<Figure> figureSet(Collection<InputNode> nodes) {
        Set<Integer> nodeIds = new HashSet<>(model.getSelectedNodes());
        for (InputNode inputNode : nodes) {
            nodeIds.add(inputNode.getId());
        }
        Set<Figure> selectedFigures = new HashSet<>();
        for (Figure figure : model.getDiagram().getFigures()) {
            if (nodeIds.contains(figure.getInputNode().getId())) {
                selectedFigures.add(figure);
            }
        }
        return selectedFigures;
    }

    @Override
    public void addSelectedNodes(Collection<InputNode> nodes, boolean showIfHidden) {
        setFigureSelection(figureSet(nodes));
        if (showIfHidden) {
            model.showFigures(model.getSelectedFigures());
        }
    }

    @Override
    public void addSelectedLiveRanges(Collection<InputLiveRange> liveRanges, boolean showIfHidden) {
        setLiveRangeSegmentSelection(liveRangeSegmentSet(liveRanges));
    }

    @Override
    public void addSelectedElements(Collection<InputNode> nodes,
                                    Collection<InputLiveRange> liveRanges,
                                    boolean showIfHidden) {
        setElementSelection(figureSet(nodes), liveRangeSegmentSet(liveRanges));
        if (showIfHidden) {
            model.showFigures(model.getSelectedFigures());
        }
    }

    @Override
    public void clearSelectedElements() {
        setSelectedObjects(Collections.emptySet());
    }

    @Override
    public void centerSelectedFigures() {
        Set<Figure> selectedFigures = model.getSelectedFigures();
        Rectangle overallRect = null;
        for (Figure figure : selectedFigures) {
            FigureWidget figureWidget = getWidget(figure);
            if (figureWidget != null) {
                Rectangle bounds = figureWidget.getBounds();
                if (bounds != null) {
                    Point location = figureWidget.getLocation();
                    Rectangle figureRect = new Rectangle(location.x, location.y, bounds.width, bounds.height);
                    if (overallRect == null) {
                        overallRect = figureRect;
                    } else {
                        overallRect = overallRect.union(figureRect);
                    }
                }
            }
        }
        if (overallRect != null) {
            centerRectangle(overallRect);
        }
    }

    @Override
    public void centerSelectedLiveRanges() {
        Set<LiveRangeSegment> selectedLiveRanges = model.getSelectedLiveRangeSegments();
        Rectangle overallRect = null;
        for (LiveRangeSegment segment : selectedLiveRanges) {
            LiveRangeWidget liveRangeWidget = getWidget(segment);
            if (liveRangeWidget != null) {
                Rectangle bounds = liveRangeWidget.getBounds();
                if (bounds != null) {
                    Point location = liveRangeWidget.getLocation();
                    Rectangle rect = new Rectangle(location.x, location.y, bounds.width, bounds.height);
                    if (overallRect == null) {
                        overallRect = rect;
                    } else {
                        overallRect = overallRect.union(rect);
                    }
                }
            }
        }
        if (overallRect != null) {
            centerRectangle(overallRect);
        }
    }

    private void centerRectangle(Rectangle r) {
        Rectangle rect = convertSceneToView(r);
        Rectangle viewRect = scrollPane.getViewport().getViewRect();

        double factor = Math.min(viewRect.getWidth() / rect.getWidth(),  viewRect.getHeight() / rect.getHeight());
        double zoomFactor = getZoomFactor();
        double newZoomFactor = zoomFactor * factor;
        if (factor < 1.0 || zoomFactor < 1.0) {
            newZoomFactor = Math.min(1.0, newZoomFactor);
            centredZoom(newZoomFactor, null);
            factor = newZoomFactor / zoomFactor;
            rect.x *= factor;
            rect.y *= factor;
            rect.width *= factor;
            rect.height *= factor;
        }
        viewRect.x = rect.x + rect.width / 2 - viewRect.width / 2;
        viewRect.y = rect.y + rect.height / 2 - viewRect.height / 2;
        // Ensure to be within area
        viewRect.x = Math.max(0, viewRect.x);
        viewRect.x = Math.min(getView().getBounds().width - viewRect.width, viewRect.x);
        viewRect.y = Math.max(0, viewRect.y);
        viewRect.y = Math.min(getView().getBounds().height - viewRect.height, viewRect.y);
        getView().scrollRectToVisible(viewRect);
    }

    private void setFigureSelection(Set<Figure> list) {
        super.setSelectedObjects(new HashSet<>(list));
    }

    private void setLiveRangeSegmentSelection(Set<LiveRangeSegment> list) {
        super.setSelectedObjects(new HashSet<>(list));
    }

    private void setElementSelection(Set<Figure> figures, Set<LiveRangeSegment> segments) {
        Set<Object> elements = new HashSet<>(figures);
        elements.addAll(segments);
        super.setSelectedObjects(elements);
    }

    @Override
    public void resetUndoRedoManager() {
        undoRedoManager = new UndoRedo.Manager();
        undoRedoManager.setLimit(UNDOREDO_LIMIT);
    }

    private UndoRedo.Manager getUndoRedoManager() {
        if (undoRedoManager == null) {
            resetUndoRedoManager();
        }
        return undoRedoManager;
    }

    @Override
    public UndoRedo getUndoRedo() {
        return getUndoRedoManager();
    }

    @Override
    public void componentHidden() {
        SelectionCoordinator.getInstance().getHighlightedChangedEvent().removeListener(highlightedCoordinatorListener);
        SelectionCoordinator.getInstance().getSelectedChangedEvent().removeListener(selectedCoordinatorListener);
    }

    @Override
    public void componentShowing() {
        SelectionCoordinator.getInstance().getHighlightedChangedEvent().addListener(highlightedCoordinatorListener);
        SelectionCoordinator.getInstance().getSelectedChangedEvent().addListener(selectedCoordinatorListener);
    }

    private final ArrayList<LineWidget> newWidgets = new ArrayList<>();

    private void rebuildConnectionLayer() {
        outputSlotToLineWidget.clear();
        inputSlotToLineWidget.clear();
        connectionLayer.removeChildren();
        newWidgets.clear();
        for (Figure figure : getModel().getDiagram().getFigures()) {
            for (OutputSlot outputSlot : figure.getOutputSlots()) {
                List<FigureConnection> connectionList = new ArrayList<>(outputSlot.getConnections());
                if (layoutMover != null && layoutMover.isFreeForm()) {
                    processFreeForm(outputSlot, connectionList);
                } else {
                    processOutputSlot(outputSlot, connectionList, 0, null, null);
                }
            }
        }

        if (getModel().getShowCFG()) {
            for (BlockConnection blockConnection : getModel().getDiagram().getBlockConnections()) {
                if (isVisibleBlockConnection(blockConnection)) {
                    processBlockConnection(blockConnection);
                }
            }
        }

        connectionLayer.addChildren(newWidgets);
        newWidgets.clear();
    }

    private void rebuildSegmentLayer() {
        segmentLayer.removeChildren();
        if (getModel().getShowCFG() && getModel().getShowLiveRanges()) {
            Map<Integer, Set<LiveRangeSegment>> segments = new HashMap<>();
            for (LiveRangeSegment segment : getModel().getDiagram().getLiveRangeSegments()) {
                int liveRangeId = segment.getLiveRange().getId();
                if (!segments.containsKey(liveRangeId)) {
                    segments.put(liveRangeId, new HashSet<>());
                }
                segments.get(liveRangeId).add(segment);
            }
            for (Set<LiveRangeSegment> segmentSet : segments.values()) {
                for (LiveRangeSegment segment : segmentSet) {
                    segment.setStartPoint(null);
                    segment.setEndPoint(null);
                    segment.setSegmentSet(segmentSet);
                    LiveRangeWidget segmentWidget = new LiveRangeWidget(segment, this, 0);
                    segmentWidget.setVisible(false);
                    addObject(segment, segmentWidget);
                    segmentWidget.getActions().addAction(hoverAction);
                    segmentLayer.addChild(segmentWidget);
                }
            }
        }
    }

   private Set<FigureWidget> getVisibleFigureWidgets() {
        Set<FigureWidget> visibleFigureWidgets = new HashSet<>();
        for (Figure figure : getModel().getDiagram().getFigures()) {
            FigureWidget figureWidget = getWidget(figure);
            if (figureWidget != null && figureWidget.isVisible()) {
                visibleFigureWidgets.add(figureWidget);
            }
        }
        return visibleFigureWidgets;
    }

    private Set<LiveRangeWidget> getVisibleLiveRangeWidgets() {
        Set<LiveRangeWidget> visibleLiveRangeWidgets = new HashSet<>();
        for (LiveRangeSegment segment : getModel().getDiagram().getLiveRangeSegments()) {
            LiveRangeWidget liveRangeWidget = getWidget(segment);
            if (liveRangeWidget != null && liveRangeWidget.isVisible()) {
                visibleLiveRangeWidgets.add(liveRangeWidget);
            }
        }
        return visibleLiveRangeWidgets;
    }

    private Set<BlockWidget> getVisibleBlockWidgets() {
        Set<BlockWidget> visibleBlockWidgets = new HashSet<>();
        if (getModel().getShowBlocks() || getModel().getShowCFG()) {
            for (Block block : getModel().getDiagram().getBlocks()) {
                BlockWidget blockWidget = getWidget(block);
                if (blockWidget.isVisible()) {
                    visibleBlockWidgets.add(blockWidget);
                }
            }
        }
        return visibleBlockWidgets;
    }

    private void updateVisibleFigureWidgets() {
        for (Figure figure : getModel().getDiagram().getFigures()) {
            FigureWidget figureWidget = getWidget(figure);
            figureWidget.setBoundary(false);
            figureWidget.setVisible(!model.getHiddenNodes().contains(figure.getInputNode().getId()));
        }
    }

    private void updateVisibleLiveRangeWidgets() {
        if (getModel().getShowCFG() && getModel().getShowLiveRanges()) {
            for (LiveRangeSegment segment : getModel().getDiagram().getLiveRangeSegments()) {
                LiveRangeWidget liveRangeWidget = getWidget(segment);
                boolean visible = true;
                for (InputNode n : getModel().getDiagram().getInputGraph().getRelatedNodes(segment.getLiveRange().getId())) {
                    if (!getModel().getDiagram().hasFigure(n)) {
                        visible = false;
                        break;
                    }
                    FigureWidget f = getWidget(getModel().getDiagram().getFigure(n));
                    if (!f.isVisible()) {
                        visible = false;
                        break;
                    }
                }
                liveRangeWidget.setVisible(visible);
            }
        }
    }

    private void updateNodeHull() {
        if (getModel().getShowNodeHull()) {
            List<FigureWidget> boundaries = new ArrayList<>();
            for (Figure figure : getModel().getDiagram().getFigures()) {
                FigureWidget figureWidget = getWidget(figure);
                if (!figureWidget.isVisible()) {
                    Set<Figure> neighborSet = new HashSet<>(figure.getPredecessorSet());
                    neighborSet.addAll(figure.getSuccessorSet());
                    boolean hasVisibleNeighbor = false;
                    for (Figure neighbor : neighborSet) {
                        FigureWidget neighborWidget = getWidget(neighbor);
                        if (neighborWidget.isVisible()) {
                            hasVisibleNeighbor = true;
                            break;
                        }
                    }
                    if (hasVisibleNeighbor) {
                        figureWidget.setBoundary(true);
                        boundaries.add(figureWidget);
                    }
                }
            }
            for (FigureWidget figureWidget : boundaries) {
                figureWidget.setVisible(true);
            }
        } else {
            getModel().getSelectedNodes().removeAll(getModel().getHiddenNodes());
        }
    }

    private void updateLiveRangeWidgetLocations(Set<LiveRangeWidget> oldVisibleLiveRangeWidgets) {
        if (getModel().getShowCFG() && getModel().getShowLiveRanges()) {
            boolean doAnimation = shouldAnimate();
            for (LiveRangeSegment segment : getModel().getDiagram().getLiveRangeSegments()) {
                LiveRangeWidget liveRangeWidget = getWidget(segment);
                if (liveRangeWidget.isVisible()) {
                    Point location = new Point(segment.getStartPoint());
                    if (doAnimation && oldVisibleLiveRangeWidgets.contains(liveRangeWidget)) {
                        getSceneAnimator().animatePreferredLocation(liveRangeWidget, location);
                    } else {
                        liveRangeWidget.setPreferredLocation(location);
                    }
                }
            }
        }
    }

    private void updateVisibleBlockWidgets() {
        if (getModel().getShowBlocks() || getModel().getShowCFG()) {
            Set<Block> visibleBlocks = new HashSet<>();
            for (Figure figure : getModel().getDiagram().getFigures()) {
                FigureWidget figureWidget = getWidget(figure);
                if (figureWidget.isVisible()) {
                    visibleBlocks.add(figure.getBlock());
                }
            }
            for (LiveRangeSegment segment : getModel().getDiagram().getLiveRangeSegments()) {
                LiveRangeWidget liveRangeWidget = getWidget(segment);
                if (liveRangeWidget != null && liveRangeWidget.isVisible()) {
                    visibleBlocks.add(segment.getCluster());
                }
            }
            if (getModel().getShowCFG() && getModel().getShowEmptyBlocks()) {
                // Add remaining blocks.
                visibleBlocks.addAll(getModel().getDiagram().getBlocks());
            }
            if (getModel().getShowCFG()) {
                // Blockless figures and artificial blocks are hidden in this view.
                for (Figure figure : getModel().getDiagram().getFigures()) {
                    if (figure.getBlock().getInputBlock().isArtificial()) {
                        FigureWidget figureWidget = getWidget(figure);
                        figureWidget.setVisible(false);
                    }
                }
            }

            for (Block block : getModel().getDiagram().getBlocks()) {
                // A block is visible if it is marked as such, except for
                // artificial or null blocks in the CFG view.
                boolean visibleAfter = visibleBlocks.contains(block) &&
                        !(getModel().getShowCFG() && (block.getInputBlock().isArtificial() || block.getInputBlock().getNodes().isEmpty()));
                BlockWidget blockWidget = getWidget(block);
                blockWidget.setVisible(visibleAfter);

                // Update node width for live range layout.
                int nodeWidth = ClusterNode.EMPTY_BLOCK_LIVE_RANGE_X_OFFSET;
                for (InputNode n : block.getInputBlock().getNodes()) {
                    if (!getModel().getDiagram().hasFigure(n)) {
                        // n might not be visible (e.g. filtered out).
                        continue;
                    }
                    Figure f = getModel().getDiagram().getFigure(n);
                    FigureWidget figureWidget = getWidget(f);
                    if (figureWidget != null && figureWidget.isVisible()) {
                        nodeWidth = f.getWidth();
                        break;
                    }
                }
                blockWidget.setNodeWidth(nodeWidth);
            }
        }
    }

    private Set<Figure> getVisibleFigures() {
        HashSet<Figure> visibleFigures = new HashSet<>();
        for (Figure figure : getModel().getDiagram().getFigures()) {
            FigureWidget figureWidget = getWidget(figure);
            if (figureWidget.isVisible()) {
                visibleFigures.add(figure);
            }
        }
        return visibleFigures;
    }

    private Set<Cluster> getVisibleBlocks() {
        Set<Cluster> visibleBlocks = new HashSet<>();
        for (Block b : getModel().getDiagram().getBlocks()) {
            BlockWidget w = getWidget(b);
            if (w.isVisible()) {
                visibleBlocks.add(b);
            }
        }
        return visibleBlocks;
    }

    private Set<Connection> getVisibleBlockConnections() {
        Set<Connection> clusterLinks = new HashSet<>();
        for (BlockConnection c : getModel().getDiagram().getBlockConnections()) {
            if (isVisibleBlockConnection(c)) {
                clusterLinks.add(c);
            }
        }
        return clusterLinks;
    }

    private HashSet<Connection> getVisibleConnections() {
        HashSet<Connection> visibleConnections = new HashSet<>();
        for (FigureConnection connection : getModel().getDiagram().getConnections()) {
            if (isVisibleFigureConnection(connection)) {
                visibleConnections.add(connection);
            }
        }
        return visibleConnections;
    }

    private List<LiveRangeSegment> getVisibleLiveRangeSegments() {
        List<LiveRangeSegment> visibleLiveRangeSegments = new ArrayList<>();
        for (LiveRangeSegment segment : getModel().getDiagram().getLiveRangeSegments()) {
            if (isVisibleLiveRangeSegment(segment)) {
                visibleLiveRangeSegments.add(segment);
            }
        }
        return visibleLiveRangeSegments;
    }

    private void updateFigureWidgetLocations(Set<FigureWidget> oldVisibleFigureWidgets) {
        boolean doAnimation = shouldAnimate();
        for (Figure figure : getModel().getDiagram().getFigures()) {
            FigureWidget figureWidget = getWidget(figure);
            if (figureWidget.isVisible()) {
                Point location = new Point(figure.getPosition());
                if (doAnimation && oldVisibleFigureWidgets.contains(figureWidget)) {
                    getSceneAnimator().animatePreferredLocation(figureWidget, location);
                } else {
                    figureWidget.setPreferredLocation(location);
                }
            }
        }
    }

    private void updateBlockWidgetBounds(Set<BlockWidget> oldVisibleBlockWidgets) {
        if (getModel().getShowBlocks() || getModel().getShowCFG()) {
            boolean doAnimation = shouldAnimate();
            for (Block block : getModel().getDiagram().getBlocks()) {
                BlockWidget blockWidget = getWidget(block);
                if (blockWidget != null && blockWidget.isVisible()) {
                    Rectangle bounds = new Rectangle(block.getBounds());
                    if (doAnimation && oldVisibleBlockWidgets.contains(blockWidget)) {
                        getSceneAnimator().animatePreferredBounds(blockWidget, bounds);
                    } else {
                        blockWidget.setPreferredBounds(bounds);
                    }
                }
            }
        }
    }

    private void updateLiveRangeIdsInBlockWidgets() {
        for (Block block : getModel().getDiagram().getBlocks()) {
            BlockWidget blockWidget = getWidget(block);
            if (blockWidget != null && blockWidget.isVisible()) {
                List<Integer> liveRangeIds = new ArrayList<>();
                for (Integer liveRangeId : block.getLiveRangeIds()) {
                    if (isVisibleLiveRange(liveRangeId)) {
                        liveRangeIds.add(liveRangeId);
                    }
                }
                blockWidget.setLiveRangeIds(liveRangeIds);
            }
        }
    }

    private void repaintLiveRangeWidgets() {
        for (LiveRangeSegment segment : getModel().getDiagram().getLiveRangeSegments()) {
            LiveRangeWidget liveRangeWidget = getWidget(segment);
            if (liveRangeWidget.isVisible()) {
                assert segment.getStartPoint().x == segment.getEndPoint().x;
                int length = segment.getEndPoint().y - segment.getStartPoint().y;
                liveRangeWidget.setLength(length);
                liveRangeWidget.repaint();
            }
        }
    }

    public JPopupMenu createPopupMenu() {
        JPopupMenu menu = new JPopupMenu();

        Action[] currentActions = actionsWithSelection;
        if (getSelectedObjects().isEmpty()) {
            currentActions = actions;
        }
        for (Action a : currentActions) {
            if (a == null) {
                menu.addSeparator();
            } else {
                menu.add(a);
            }
        }
        return menu;
    }

    private boolean undoRedoEnabled = true;

    private static class DiagramUndoRedo extends AbstractUndoableEdit {

        private final ModelState oldState;
        private final ModelState newState;
        private Point oldScrollPosition;
        private Point newScrollPosition;
        private final DiagramScene scene;

        public DiagramUndoRedo(DiagramScene scene, Point oldScrollPosition, ModelState oldState, ModelState newState) {
            assert oldState != null;
            assert newState != null;
            this.oldState = oldState;
            this.newState = newState;
            this.scene = scene;
            this.oldScrollPosition = oldScrollPosition;
        }

        @Override
        public void redo() throws CannotRedoException {
            super.redo();
            scene.undoRedoEnabled = false;
            oldScrollPosition = scene.getScrollPosition();
            scene.getModel().setHiddenNodes(newState.hiddenNodes);
            scene.getModel().setPositions(newState.firstPos, newState.secondPos);
            scene.setScrollPosition(newScrollPosition);
            scene.undoRedoEnabled = true;
        }

        @Override
        public void undo() throws CannotUndoException {
            super.undo();
            scene.undoRedoEnabled = false;
            newScrollPosition = scene.getScrollPosition();
            scene.getModel().setHiddenNodes(oldState.hiddenNodes);
            scene.getModel().setPositions(oldState.firstPos, oldState.secondPos);
            scene.setScrollPosition(oldScrollPosition);
            scene.undoRedoEnabled = true;
        }
    }

    private static class ModelState {
        public final Set<Integer> hiddenNodes;
        public final int firstPos;
        public final int secondPos;


        public ModelState(DiagramViewModel model) {
            hiddenNodes = new HashSet<>(model.getHiddenNodes());
            firstPos = model.getFirstPosition();
            secondPos = model.getSecondPosition();
        }
    }

    private void addUndo() {
        if (undoRedoEnabled) {
            ModelState newModelState = new ModelState(model);
            DiagramUndoRedo undoRedo = new DiagramUndoRedo(this, getScrollPosition(), modelState, newModelState);
            getUndoRedoManager().undoableEditHappened(new UndoableEditEvent(this, undoRedo));
            modelState = newModelState;
        }
    }
}
