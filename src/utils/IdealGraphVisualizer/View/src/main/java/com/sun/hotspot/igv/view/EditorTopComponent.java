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
package com.sun.hotspot.igv.view;

import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.data.*;
import com.sun.hotspot.igv.data.Properties.PropertyMatcher;
import com.sun.hotspot.igv.data.services.InputGraphProvider;
import com.sun.hotspot.igv.filter.FilterChain;
import com.sun.hotspot.igv.filter.FilterChainProvider;
import com.sun.hotspot.igv.graph.Diagram;
import com.sun.hotspot.igv.graph.Figure;
import com.sun.hotspot.igv.settings.Settings;
import com.sun.hotspot.igv.util.LookupHistory;
import com.sun.hotspot.igv.util.RangeSlider;
import com.sun.hotspot.igv.view.actions.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.List;
import java.util.*;
import javax.swing.*;
import javax.swing.border.Border;
import org.openide.actions.RedoAction;
import org.openide.actions.UndoAction;
import org.openide.awt.Toolbar;
import org.openide.awt.ToolbarPool;
import org.openide.awt.UndoRedo;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;


/**
 *
 * @author Thomas Wuerthinger
 */
public final class EditorTopComponent extends TopComponent {

    private final DiagramViewer scene;
    private final InstanceContent graphContent;
    private final JComponent satelliteComponent;
    private final JPanel centerPanel;
    private final CardLayout cardLayout;
    private final Toolbar quickSearchToolbar;
    private static final JPanel quickSearchPresenter = (JPanel) ((Presenter.Toolbar) Utilities.actionsForPath("Actions/Search").get(0)).getToolbarPresenter();
    private static final String PREFERRED_ID = "EditorTopComponent";
    private static final String SATELLITE_STRING = "satellite";
    private static final String SCENE_STRING = "scene";

    public EditorTopComponent(Diagram diagram) {
        initComponents();

        LookupHistory.init(InputGraphProvider.class);
        setFocusable(true);
        FilterChain filterChain;
        FilterChain sequence;
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
                ReduceDiffAction.get(ReduceDiffAction.class),
                ExpandDiffAction.get(ExpandDiffAction.class),
                null,
                ExtractAction.get(ExtractAction.class),
                HideAction.get(HideAction.class),
                ShowAllAction.get(ShowAllAction.class),
                null,
                ZoomOutAction.get(ZoomOutAction.class),
                ZoomInAction.get(ZoomInAction.class),
        };

        Action[] actionsWithSelection = new Action[]{
                ExtractAction.get(ExtractAction.class),
                HideAction.get(HideAction.class),
                null,
                ExpandPredecessorsAction.get(ExpandPredecessorsAction.class),
                ExpandSuccessorsAction.get(ExpandSuccessorsAction.class)
        };

        JPanel container = new JPanel(new BorderLayout());

        DiagramViewModel diagramViewModel = new DiagramViewModel(diagram.getGraph().getGroup(), filterChain, sequence);
        RangeSlider rangeSlider = new RangeSlider();
        rangeSlider.setModel(diagramViewModel);
        if (diagram.getGraph().getGroup().getGraphsCount() == 1) {
            rangeSlider.setVisible(false);
        }
        JScrollPane pane = new JScrollPane(rangeSlider, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        container.add(BorderLayout.CENTER, pane);
        add(container, BorderLayout.NORTH);

        scene = new DiagramScene(actions, actionsWithSelection, diagramViewModel);
        graphContent = new InstanceContent();
        InstanceContent content = new InstanceContent();
        content.add(new ExportGraph());
        content.add(diagramViewModel);
        associateLookup(new ProxyLookup(scene.getLookup(), new AbstractLookup(graphContent), new AbstractLookup(content)));

        diagramViewModel.getDiagramChangedEvent().addListener(source -> {
            setDisplayName(getDiagram().getName());
            setToolTipText(getDiagram().getGraph().getGroup().getName());
            Collection<Object> list = new ArrayList<>();
            list.add(new EditorInputGraphProvider(EditorTopComponent.this));
            graphContent.set(list, null);
        });
        diagramViewModel.selectGraph(diagram.getGraph());

        Group group = getDiagram().getGraph().getGroup();
        group.getChangedEvent().addListener(g -> closeOnRemovedOrEmptyGroup());
        if (group.getParent() instanceof GraphDocument) {
            final GraphDocument doc = (GraphDocument) group.getParent();
            doc.getChangedEvent().addListener(d -> closeOnRemovedOrEmptyGroup());
        }

        cardLayout = new CardLayout();
        centerPanel = new JPanel();
        centerPanel.setLayout(cardLayout);
        centerPanel.setBackground(Color.WHITE);
        satelliteComponent = scene.createSatelliteView();
        satelliteComponent.setSize(200, 200);
        // needed to update when the satellite component is moved
        satelliteComponent.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                centerPanel.repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {}
        });
        centerPanel.add(SCENE_STRING, scene.getComponent());
        centerPanel.add(SATELLITE_STRING, satelliteComponent);
        add(centerPanel, BorderLayout.CENTER);

        ToolbarPool.getDefault().setPreferredIconSize(16);
        Toolbar toolBar = new Toolbar();
        toolBar.setBorder((Border) UIManager.get("Nb.Editor.Toolbar.border")); //NOI18N
        toolBar.setMinimumSize(new Dimension(0,0)); // MacOS BUG with ToolbarWithOverflow

        toolBar.add(PrevDiagramAction.get(PrevDiagramAction.class));
        toolBar.add(NextDiagramAction.get(NextDiagramAction.class));
        toolBar.addSeparator();
        toolBar.add(ReduceDiffAction.get(ReduceDiffAction.class));
        toolBar.add(ExpandDiffAction.get(ExpandDiffAction.class));
        toolBar.addSeparator();
        toolBar.add(ExtractAction.get(ExtractAction.class));
        toolBar.add(HideAction.get(HideAction.class));
        toolBar.add(ShowAllAction.get(ShowAllAction.class));

        toolBar.addSeparator();
        ButtonGroup layoutButtons = new ButtonGroup();

        JToggleButton seaLayoutButton = new JToggleButton(new EnableSeaLayoutAction(this));
        seaLayoutButton.setSelected(Settings.get().getInt(Settings.DEFAULT_VIEW, Settings.DEFAULT_VIEW_DEFAULT) == Settings.DefaultView.SEA_OF_NODES);
        layoutButtons.add(seaLayoutButton);
        toolBar.add(seaLayoutButton);

        JToggleButton blockLayoutButton = new JToggleButton(new EnableBlockLayoutAction(this));
        blockLayoutButton.setSelected(Settings.get().getInt(Settings.DEFAULT_VIEW, Settings.DEFAULT_VIEW_DEFAULT) == Settings.DefaultView.CLUSTERED_SEA_OF_NODES);
        layoutButtons.add(blockLayoutButton);
        toolBar.add(blockLayoutButton);

        EnableCFGLayoutAction cfgLayoutAction = new EnableCFGLayoutAction(this);
        JToggleButton cfgLayoutButton = new JToggleButton(cfgLayoutAction);
        cfgLayoutButton.setSelected(Settings.get().getInt(Settings.DEFAULT_VIEW, Settings.DEFAULT_VIEW_DEFAULT) == Settings.DefaultView.CONTROL_FLOW_GRAPH);
        layoutButtons.add(cfgLayoutButton);
        toolBar.add(cfgLayoutButton);

        toolBar.addSeparator();
        toolBar.add(new JToggleButton(new OverviewAction(centerPanel)));
        toolBar.add(new JToggleButton(new PredSuccAction()));
        toolBar.add(new JToggleButton(new ShowEmptyBlocksAction(cfgLayoutAction, true)));
        toolBar.add(new JToggleButton(new HideDuplicatesAction()));

        toolBar.addSeparator();
        UndoAction undoAction = UndoAction.get(UndoAction.class);
        undoAction.putValue(Action.SHORT_DESCRIPTION, "Undo");
        toolBar.add(undoAction);
        RedoAction redoAction = RedoAction.get(RedoAction.class);
        redoAction.putValue(Action.SHORT_DESCRIPTION, "Redo");
        toolBar.add(redoAction);

        toolBar.addSeparator();
        toolBar.add(new JToggleButton(new SelectionModeAction()));
        toolBar.addSeparator();
        toolBar.add(new ZoomLevelAction(scene));
        toolBar.add(Box.createHorizontalGlue());

        quickSearchToolbar = new Toolbar();
        quickSearchToolbar.setLayout(new BoxLayout(quickSearchToolbar, BoxLayout.LINE_AXIS));
        quickSearchToolbar.setBorder((Border) UIManager.get("Nb.Editor.Toolbar.border")); //NOI18N
        quickSearchPresenter.setMinimumSize(quickSearchPresenter.getPreferredSize());
        quickSearchPresenter.setAlignmentX(Component.RIGHT_ALIGNMENT);
        quickSearchToolbar.add(quickSearchPresenter);

        // Needed for toolBar to use maximal available width
        JPanel toolbarPanel = new JPanel(new GridLayout(1, 0));
        toolbarPanel.add(toolBar);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.LINE_AXIS));
        topPanel.add(toolbarPanel);
        topPanel.add(quickSearchToolbar);
        container.add(BorderLayout.NORTH, topPanel);

        getModel().getDiagramChangedEvent().fire();
    }

    public DiagramViewModel getModel() {
        return scene.getModel();
    }

    private Diagram getDiagram() {
        return getModel().getDiagramToView();
    }

    public void setSelectionMode(boolean enable) {
        if (enable) {
            scene.setInteractionMode(DiagramViewer.InteractionMode.SELECTION);
        } else {
            scene.setInteractionMode(DiagramViewer.InteractionMode.PANNING);
        }
    }

    public void showSatellite(boolean enable) {
        if (enable) {
            cardLayout.show(centerPanel, SATELLITE_STRING);
            satelliteComponent.requestFocus();
        } else {
            cardLayout.show(centerPanel, SCENE_STRING);
            scene.getComponent().requestFocus();
        }
    }

    public void zoomOut() {
        scene.zoomOut(null, DiagramScene.ZOOM_INCREMENT);
    }

    public void zoomIn() {
        scene.zoomIn(null, DiagramScene.ZOOM_INCREMENT);
    }

    public void setZoomLevel(int percentage) {
        scene.setZoomPercentage(percentage);
    }

    public static boolean isOpen(EditorTopComponent editor) {
        return WindowManager.getDefault().isOpenedEditorTopComponent(editor);
    }

    public static EditorTopComponent getActive() {
        TopComponent topComponent = getRegistry().getActivated();
        if (topComponent instanceof EditorTopComponent) {
            return (EditorTopComponent) topComponent;
        }
        return null;
    }

    public static EditorTopComponent findEditorForGraph(InputGraph graph) {
        WindowManager manager = WindowManager.getDefault();
        for (Mode m : manager.getModes()) {
            List<TopComponent> l = new ArrayList<>();
            l.add(m.getSelectedTopComponent());
            l.addAll(Arrays.asList(manager.getOpenedTopComponents(m)));
            for (TopComponent t : l) {
                if (t instanceof EditorTopComponent) {
                    EditorTopComponent etc = (EditorTopComponent) t;
                    if (etc.getModel().getGroup().getGraphs().contains(graph)) {
                        return etc;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }

    private void closeOnRemovedOrEmptyGroup() {
        Group group = getModel().getGroup();
        if (!group.getParent().getElements().contains(group) ||
            group.getGraphs().isEmpty()) {
            close();
        }
    }

    public void setSelection(PropertyMatcher matcher) {
        Properties.PropertySelector<Figure> selector = new Properties.PropertySelector<>(getDiagram().getFigures());
        List<Figure> list = selector.selectMultiple(matcher);
        setSelectedFigures(list);
    }

    public void setSelectedFigures(List<Figure> list) {
        scene.setSelection(list);
        scene.centerFigures(list);
    }

    public void setSelectedNodes(Set<InputNode> nodes) {
        List<Figure> list = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        for (InputNode n : nodes) {
            ids.add(n.getId());
        }
        for (Figure f : getDiagram().getFigures()) {
            for (InputNode n : f.getSource().getSourceNodes()) {
                if (ids.contains(n.getId())) {
                    list.add(f);
                    break;
                }
            }
        }
        setSelectedFigures(list);
    }

    public void setSelectedNodes(InputBlock b) {
        List<Figure> list = new ArrayList<>();
        for (Figure f : getDiagram().getFigures()) {
            if (f.getBlock() == b) {
                list.add(f);
            }
        }
        setSelectedFigures(list);
    }

    public Rectangle getSceneBounds() {
        return scene.getBounds();
    }

    public void paintScene(Graphics2D generator) {
        scene.paint(generator);
    }

    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    @Override
    public void componentClosed() {
        super.componentClosed();
        getModel().close();
        LookupHistory.terminate(InputGraphProvider.class);
    }

    @Override
    protected void componentHidden() {
        super.componentHidden();
        scene.componentHidden();
    }

    @Override
    protected void componentShowing() {
        super.componentShowing();
        scene.componentShowing();
    }

    @Override
    public void requestActive() {
        super.requestActive();
        scene.getComponent().requestFocus();
    }

    @Override
    protected void componentActivated() {
        super.componentActivated();
        quickSearchToolbar.add(quickSearchPresenter);
        quickSearchPresenter.revalidate();
    }

    @Override
    public UndoRedo getUndoRedo() {
        return scene.getUndoRedo();
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
}
