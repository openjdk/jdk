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

import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.filter.FilterChain;
import com.sun.hotspot.igv.graph.Diagram;
import com.sun.hotspot.igv.graph.Figure;
import com.sun.hotspot.igv.view.actions.EnableBlockLayoutAction;
import com.sun.hotspot.igv.view.actions.ExpandPredecessorsAction;
import com.sun.hotspot.igv.view.actions.ExpandSuccessorsAction;
import com.sun.hotspot.igv.view.actions.ExtractAction;
import com.sun.hotspot.igv.view.actions.HideAction;
import com.sun.hotspot.igv.view.actions.NextDiagramAction;
import com.sun.hotspot.igv.view.actions.NodeFindAction;
import com.sun.hotspot.igv.view.actions.OverviewAction;
import com.sun.hotspot.igv.view.actions.PredSuccAction;
import com.sun.hotspot.igv.view.actions.PrevDiagramAction;
import com.sun.hotspot.igv.view.actions.ShowAllAction;
import com.sun.hotspot.igv.view.actions.ZoomInAction;
import com.sun.hotspot.igv.view.actions.ZoomOutAction;
import com.sun.hotspot.igv.data.ChangedListener;
import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.data.Properties.PropertyMatcher;
import com.sun.hotspot.igv.filter.FilterChainProvider;
import com.sun.hotspot.igv.util.RangeSlider;
import com.sun.hotspot.igv.util.RangeSliderModel;
import com.sun.hotspot.igv.svg.BatikSVG;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import org.openide.DialogDisplayer;
import org.openide.actions.FindAction;
import org.openide.actions.RedoAction;
import org.openide.actions.UndoAction;
import org.openide.awt.Toolbar;
import org.openide.awt.ToolbarPool;
import org.openide.awt.UndoRedo;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallbackSystemAction;
import org.openide.util.actions.SystemAction;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.openide.NotifyDescriptor;

/**
 *
 * @author Thomas Wuerthinger
 */
public final class EditorTopComponent extends TopComponent implements ChangedListener<RangeSliderModel>, PropertyChangeListener {

    private DiagramScene scene;
    private InstanceContent content;
    private FindPanel findPanel;
    private EnableBlockLayoutAction blockLayoutAction;
    private OverviewAction overviewAction;
    private PredSuccAction predSuccAction;
    private boolean notFirstTime;
    private ExtendedSatelliteComponent satelliteComponent;
    private JPanel centerPanel;
    private CardLayout cardLayout;
    private RangeSlider rangeSlider;
    private JToggleButton overviewButton;
    private static final String PREFERRED_ID = "EditorTopComponent";
    private static final String SATELLITE_STRING = "satellite";
    private static final String SCENE_STRING = "scene";
    private DiagramViewModel rangeSliderModel;
    private ExportCookie exportCookie = new ExportCookie() {

        public void export(File f) {

            Graphics2D svgGenerator = BatikSVG.createGraphicsObject();

            if (svgGenerator == null) {
                NotifyDescriptor message = new NotifyDescriptor.Message("For export to SVG files the Batik SVG Toolkit must be intalled.", NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notifyLater(message);
            } else {
                scene.paint(svgGenerator);
                FileOutputStream os = null;
                try {
                    os = new FileOutputStream(f);
                    Writer out = new OutputStreamWriter(os, "UTF-8");
                    BatikSVG.printToStream(svgGenerator, out, true);
                } catch (FileNotFoundException e) {
                    NotifyDescriptor message = new NotifyDescriptor.Message("For export to SVG files the Batik SVG Toolkit must be intalled.", NotifyDescriptor.ERROR_MESSAGE);
                    DialogDisplayer.getDefault().notifyLater(message);

                } catch (UnsupportedEncodingException e) {
                } finally {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e) {
                        }
                    }
                }

            }
        }
    };

    private void updateDisplayName() {
        setDisplayName(getDiagram().getName());
    }

    public EditorTopComponent(Diagram diagram) {

        FilterChain filterChain = null;
        FilterChain sequence = null;
        FilterChainProvider provider = Lookup.getDefault().lookup(FilterChainProvider.class);
        if (provider == null) {
            filterChain = new FilterChain();
            sequence = new FilterChain();
        } else {
            filterChain = provider.getFilterChain();
            sequence = provider.getSequence();
        }

        setName(NbBundle.getMessage(EditorTopComponent.class, "CTL_EditorTopComponent"));
        setToolTipText(NbBundle.getMessage(EditorTopComponent.class, "HINT_EditorTopComponent"));

        Action[] actions = new Action[]{
            PrevDiagramAction.get(PrevDiagramAction.class),
            NextDiagramAction.get(NextDiagramAction.class),
            null,
            ExtractAction.get(ExtractAction.class),
            ShowAllAction.get(HideAction.class),
            ShowAllAction.get(ShowAllAction.class),
            null,
            ZoomInAction.get(ZoomInAction.class),
            ZoomOutAction.get(ZoomOutAction.class),
            null,
            ExpandPredecessorsAction.get(ExpandPredecessorsAction.class),
            ExpandSuccessorsAction.get(ExpandSuccessorsAction.class)
        };


        initComponents();

        ActionMap actionMap = getActionMap();

        ToolbarPool.getDefault().setPreferredIconSize(16);
        Toolbar toolBar = new Toolbar();
        Border b = (Border) UIManager.get("Nb.Editor.Toolbar.border"); //NOI18N
        toolBar.setBorder(b);
        JPanel container = new JPanel();
        this.add(container, BorderLayout.NORTH);
        container.setLayout(new BorderLayout());
        container.add(BorderLayout.NORTH, toolBar);

        rangeSliderModel = new DiagramViewModel(diagram.getGraph().getGroup(), filterChain, sequence);
        rangeSliderModel.selectGraph(diagram.getGraph());
        rangeSlider = new RangeSlider();
        rangeSlider.setModel(rangeSliderModel);
        rangeSliderModel.getChangedEvent().addListener(this);
        container.add(BorderLayout.CENTER, rangeSlider);

        scene = new DiagramScene(actions, rangeSliderModel);
        content = new InstanceContent();
        this.associateLookup(new ProxyLookup(new Lookup[]{scene.getLookup(), new AbstractLookup(content)}));
        content.add(exportCookie);
        content.add(rangeSliderModel);


        findPanel = new FindPanel(diagram.getFigures());
        findPanel.setMaximumSize(new Dimension(200, 50));
        toolBar.add(findPanel);
        toolBar.add(NodeFindAction.get(NodeFindAction.class));
        toolBar.addSeparator();
        toolBar.add(NextDiagramAction.get(NextDiagramAction.class));
        toolBar.add(PrevDiagramAction.get(PrevDiagramAction.class));
        toolBar.addSeparator();
        toolBar.add(ExtractAction.get(ExtractAction.class));
        toolBar.add(ShowAllAction.get(HideAction.class));
        toolBar.add(ShowAllAction.get(ShowAllAction.class));
        toolBar.addSeparator();
        toolBar.add(ShowAllAction.get(ZoomInAction.class));
        toolBar.add(ShowAllAction.get(ZoomOutAction.class));

        blockLayoutAction = new EnableBlockLayoutAction();
        JToggleButton button = new JToggleButton(blockLayoutAction);
        button.setSelected(true);
        toolBar.add(button);
        blockLayoutAction.addPropertyChangeListener(this);

        overviewAction = new OverviewAction();
        overviewButton = new JToggleButton(overviewAction);
        overviewButton.setSelected(false);
        toolBar.add(overviewButton);
        overviewAction.addPropertyChangeListener(this);

        predSuccAction = new PredSuccAction();
        button = new JToggleButton(predSuccAction);
        button.setSelected(true);
        toolBar.add(button);
        predSuccAction.addPropertyChangeListener(this);

        toolBar.addSeparator();
        toolBar.add(UndoAction.get(UndoAction.class));
        toolBar.add(RedoAction.get(RedoAction.class));

        centerPanel = new JPanel();
        this.add(centerPanel, BorderLayout.CENTER);
        cardLayout = new CardLayout();
        centerPanel.setLayout(cardLayout);
        centerPanel.add(SCENE_STRING, scene.getScrollPane());
        centerPanel.setBackground(Color.WHITE);
        satelliteComponent = new ExtendedSatelliteComponent(scene);
        satelliteComponent.setSize(200, 200);
        centerPanel.add(SATELLITE_STRING, satelliteComponent);

        CallbackSystemAction callFindAction = (CallbackSystemAction) SystemAction.get(FindAction.class);
        NodeFindAction findAction = NodeFindAction.get(NodeFindAction.class);
        Object key = callFindAction.getActionMapKey();
        actionMap.put(key, findAction);

        scene.getScrollPane().addKeyListener(keyListener);
        scene.getView().addKeyListener(keyListener);
        satelliteComponent.addKeyListener(keyListener);

        scene.getScrollPane().addHierarchyBoundsListener(new HierarchyBoundsListener() {

            public void ancestorMoved(HierarchyEvent e) {
            }

            public void ancestorResized(HierarchyEvent e) {
                if (!notFirstTime && scene.getScrollPane().getBounds().width > 0) {
                    notFirstTime = true;
                    SwingUtilities.invokeLater(new Runnable() {

                        public void run() {
                            Figure f = EditorTopComponent.this.scene.getModel().getDiagramToView().getRootFigure();
                            if (f != null) {
                                scene.setUndoRedoEnabled(false);
                                scene.gotoFigure(f);
                                scene.setUndoRedoEnabled(true);
                            }
                        }
                    });
                }
            }
        });

        updateDisplayName();
    }
    private KeyListener keyListener = new KeyListener() {

        public void keyTyped(KeyEvent e) {
        }

        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_S) {
                EditorTopComponent.this.overviewButton.setSelected(true);
                EditorTopComponent.this.overviewAction.setState(true);
            }
        }

        public void keyReleased(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_S) {
                EditorTopComponent.this.overviewButton.setSelected(false);
                EditorTopComponent.this.overviewAction.setState(false);
            }
        }
    };

    public DiagramViewModel getDiagramModel() {
        return scene.getModel();
    }

    private void showSatellite() {
        cardLayout.show(centerPanel, SATELLITE_STRING);
        satelliteComponent.requestFocus();

    }

    private void showScene() {
        cardLayout.show(centerPanel, SCENE_STRING);
        scene.getView().requestFocus();
    }

    public void findNode() {
        findPanel.find();
    }

    public void zoomOut() {
        double zoom = scene.getZoomFactor();
        Point viewPosition = scene.getScrollPane().getViewport().getViewPosition();
        double newZoom = zoom / DiagramScene.ZOOM_INCREMENT;
        if (newZoom > DiagramScene.ZOOM_MIN_FACTOR) {
            scene.setZoomFactor(newZoom);
            scene.validate();
            scene.getScrollPane().getViewport().setViewPosition(new Point((int) (viewPosition.x / DiagramScene.ZOOM_INCREMENT), (int) (viewPosition.y / DiagramScene.ZOOM_INCREMENT)));
            this.satelliteComponent.update();
        }
    }

    public void zoomIn() {
        double zoom = scene.getZoomFactor();
        Point viewPosition = scene.getScrollPane().getViewport().getViewPosition();
        double newZoom = zoom * DiagramScene.ZOOM_INCREMENT;
        if (newZoom < DiagramScene.ZOOM_MAX_FACTOR) {
            scene.setZoomFactor(newZoom);
            scene.validate();
            scene.getScrollPane().getViewport().setViewPosition(new Point((int) (viewPosition.x * DiagramScene.ZOOM_INCREMENT), (int) (viewPosition.y * DiagramScene.ZOOM_INCREMENT)));
            this.satelliteComponent.update();
        }
    }

    public void showPrevDiagram() {
        int fp = getModel().getFirstPosition();
        int sp = getModel().getSecondPosition();
        if (fp != 0) {
            fp--;
            sp--;
            getModel().setPositions(fp, sp);
        }
    }

    public DiagramViewModel getModel() {
        return scene.getModel();
    }

    public FilterChain getFilterChain() {
        return this.scene.getModel().getFilterChain();
    }

    public static EditorTopComponent getActive() {
        Set<? extends Mode> modes = WindowManager.getDefault().getModes();
        for (Mode m : modes) {
            TopComponent tc = m.getSelectedTopComponent();
            if (tc instanceof EditorTopComponent) {
                return (EditorTopComponent) tc;
            }
        }
        return null;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
        // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
        private void initComponents() {
                jCheckBox1 = new javax.swing.JCheckBox();

                org.openide.awt.Mnemonics.setLocalizedText(jCheckBox1, "jCheckBox1");
                jCheckBox1.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
                jCheckBox1.setMargin(new java.awt.Insets(0, 0, 0, 0));

                setLayout(new java.awt.BorderLayout());

        }// </editor-fold>//GEN-END:initComponents
        // Variables declaration - do not modify//GEN-BEGIN:variables
        private javax.swing.JCheckBox jCheckBox1;
        // End of variables declaration//GEN-END:variables
    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }

    @Override
    public void componentOpened() {
    }

    @Override
    public void componentClosed() {
    }

    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    public void changed(RangeSliderModel model) {
        updateDisplayName();
    }

    public boolean showPredSucc() {
        return (Boolean) predSuccAction.getValue(PredSuccAction.STATE);
    }

    public void setSelection(PropertyMatcher matcher) {

        Properties.PropertySelector<Figure> selector = new Properties.PropertySelector<Figure>(scene.getModel().getDiagramToView().getFigures());
        List<Figure> list = selector.selectMultiple(matcher);
        boolean b = scene.getUndoRedoEnabled();
        scene.setUndoRedoEnabled(false);
        scene.gotoFigures(list);
        scene.setUndoRedoEnabled(b);
        scene.setSelection(list);
    }

    public void setSelectedNodes(Set<InputNode> nodes) {

        List<Figure> list = new ArrayList<Figure>();
        Set<Integer> ids = new HashSet<Integer>();
        for (InputNode n : nodes) {
            ids.add(n.getId());
        }

        for (Figure f : scene.getModel().getDiagramToView().getFigures()) {
            for (InputNode n : f.getSource().getSourceNodes()) {
                if (ids.contains(n.getId())) {
                    list.add(f);
                    break;
                }
            }
        }

        scene.gotoFigures(list);
        scene.setSelection(list);
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() == this.predSuccAction) {
            boolean b = (Boolean) predSuccAction.getValue(PredSuccAction.STATE);
            this.getModel().setShowNodeHull(b);
        } else if (evt.getSource() == this.overviewAction) {
            boolean b = (Boolean) overviewAction.getValue(OverviewAction.STATE);
            if (b) {
                showSatellite();
            } else {
                showScene();
            }
        } else if (evt.getSource() == this.blockLayoutAction) {
            boolean b = (Boolean) blockLayoutAction.getValue(EnableBlockLayoutAction.STATE);
            System.out.println("Showblocks = " + b);
            this.getModel().setShowBlocks(b);
        } else {
            assert false : "Unknown event source";
        }
    }

    public void extract() {
        scene.showOnly(scene.getSelectedNodes());
    }

    public void hideNodes() {
        Set<Integer> selectedNodes = this.scene.getSelectedNodes();
        HashSet<Integer> nodes = new HashSet<Integer>(scene.getModel().getHiddenNodes());
        nodes.addAll(selectedNodes);
        this.scene.showNot(nodes);
    }

    public void expandPredecessors() {
        Set<Figure> oldSelection = scene.getSelectedFigures();
        Set<Figure> figures = new HashSet<Figure>();

        for (Figure f : this.getDiagramModel().getDiagramToView().getFigures()) {
            boolean ok = false;
            if (oldSelection.contains(f)) {
                ok = true;
            } else {
                for (Figure pred : f.getSuccessors()) {
                    if (oldSelection.contains(pred)) {
                        ok = true;
                        break;
                    }
                }
            }

            if (ok) {
                figures.add(f);
            }
        }

        scene.showAll(figures);
    }

    public void expandSuccessors() {
        Set<Figure> oldSelection = scene.getSelectedFigures();
        Set<Figure> figures = new HashSet<Figure>();

        for (Figure f : this.getDiagramModel().getDiagramToView().getFigures()) {
            boolean ok = false;
            if (oldSelection.contains(f)) {
                ok = true;
            } else {
                for (Figure succ : f.getPredecessors()) {
                    if (oldSelection.contains(succ)) {
                        ok = true;
                        break;
                    }
                }
            }

            if (ok) {
                figures.add(f);
            }
        }

        scene.showAll(figures);
    }

    public void showAll() {
        scene.showNot(new HashSet<Integer>());
    }

    public Diagram getDiagram() {
        return getDiagramModel().getDiagramToView();
    }

    @Override
    protected void componentActivated() {
    }

    @Override
    public void requestFocus() {
        super.requestFocus();
        scene.getView().requestFocus();
    }

    @Override
    public boolean requestFocusInWindow() {
        super.requestFocusInWindow();
        return scene.getView().requestFocusInWindow();
    }

    @Override
    public UndoRedo getUndoRedo() {
        return scene.getUndoRedo();
    }
}
