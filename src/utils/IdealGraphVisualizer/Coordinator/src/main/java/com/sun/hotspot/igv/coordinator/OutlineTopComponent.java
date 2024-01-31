/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.coordinator;

import com.sun.hotspot.igv.connection.Server;
import com.sun.hotspot.igv.coordinator.actions.*;
import com.sun.hotspot.igv.data.*;
import com.sun.hotspot.igv.data.serialization.ParseMonitor;
import com.sun.hotspot.igv.data.serialization.Parser;
import com.sun.hotspot.igv.data.services.GraphViewer;
import com.sun.hotspot.igv.data.services.GroupCallback;
import com.sun.hotspot.igv.data.services.InputGraphProvider;
import com.sun.hotspot.igv.util.LookupHistory;
import com.sun.hotspot.igv.view.DiagramViewModel;
import com.sun.hotspot.igv.view.EditorTopComponent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.*;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.ErrorManager;
import org.openide.actions.GarbageCollectAction;
import org.openide.awt.Toolbar;
import org.openide.awt.ToolbarPool;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.explorer.view.BeanTreeView;
import org.openide.modules.Places;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 *
 * @author Thomas Wuerthinger
 */
public final class OutlineTopComponent extends TopComponent implements ExplorerManager.Provider, ChangedListener<InputGraphProvider> {

    public static OutlineTopComponent instance;
    public static final String PREFERRED_ID = "OutlineTopComponent";
    private ExplorerManager manager;
    private final GraphDocument document;
    private FolderNode root;
    private SaveAllAction saveAllAction;
    private RemoveAllAction removeAllAction;
    private GraphNode[] selectedGraphs = new GraphNode[0];
    private final Set<FolderNode> selectedFolders = new HashSet<>();
    private static final int WORKUNITS = 10000;
    private static final int STATE_FORMAT_VERSION = 1;
    private static final RequestProcessor RP = new RequestProcessor("OutlineTopComponent", 1);

    private OutlineTopComponent() {
        initComponents();

        setName(NbBundle.getMessage(OutlineTopComponent.class, "CTL_OutlineTopComponent"));
        setToolTipText(NbBundle.getMessage(OutlineTopComponent.class, "HINT_OutlineTopComponent"));

        document = new GraphDocument();
        initListView();
        initToolbar();
        initReceivers();
    }

    private void initListView() {
        manager = new ExplorerManager();
        root = new FolderNode(document);
        manager.setRootContext(root);
        ((BeanTreeView) this.treeView).setRootVisible(false);

        associateLookup(ExplorerUtils.createLookup(manager, getActionMap()));
    }

    private void initToolbar() {
        Toolbar toolbar = new Toolbar();
        toolbar.setBorder((Border) UIManager.get("Nb.Editor.Toolbar.border")); //NOI18N
        toolbar.setMinimumSize(new Dimension(0,0)); // MacOS BUG with ToolbarWithOverflow

        this.add(toolbar, BorderLayout.NORTH);

        toolbar.add(ImportAction.get(ImportAction.class));
        toolbar.add(SaveAsAction.get(SaveAsAction.class).createContextAwareInstance(this.getLookup()));

        saveAllAction = SaveAllAction.get(SaveAllAction.class);
        saveAllAction.setEnabled(false);
        toolbar.add(saveAllAction);

        toolbar.add(RemoveAction.get(RemoveAction.class).createContextAwareInstance(this.getLookup()));

        removeAllAction = RemoveAllAction.get(RemoveAllAction.class);
        removeAllAction.setEnabled(false);
        toolbar.add(removeAllAction);

        toolbar.add(GarbageCollectAction.get(GarbageCollectAction.class).getToolbarPresenter());

        for (Toolbar tb : ToolbarPool.getDefault().getToolbars()) {
            tb.setVisible(false);
        }

        document.getChangedEvent().addListener(g -> documentChanged());
        loadState();
    }

    private void documentChanged() {
        boolean enableButton = !document.getElements().isEmpty();
        saveAllAction.setEnabled(enableButton);
        removeAllAction.setEnabled(enableButton);
    }

    private void initReceivers() {
        final GroupCallback callback = g -> {
            synchronized(OutlineTopComponent.this) {
                g.setParent(getDocument());
                getDocument().addElement(g);
            }
        };

        new Server(callback);
    }

    public void clear() {
        document.clear();
        FolderNode.clearGraphNodeMap();
        root = new FolderNode(document);
        manager.setRootContext(root);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return manager;
    }

    public GraphDocument getDocument() {
        return document;
    }

    /**
     * Gets default instance. Do not use directly: reserved for *.settings files only,
     * i.e. deserialization routines; otherwise you could get a non-deserialized instance.
     * To obtain the singleton instance, use {@link #findInstance()}.
     */
    public static synchronized OutlineTopComponent getDefault() {
        if (instance == null) {
            instance = new OutlineTopComponent();
        }
        return instance;
    }

    /**
     * Obtain the OutlineTopComponent instance. Never call {@link #getDefault} directly!
     */
    public static synchronized OutlineTopComponent findInstance() {
        TopComponent win = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (win == null) {
            ErrorManager.getDefault().log(ErrorManager.WARNING, "Cannot find Outline component. It will not be located properly in the window system.");
            return getDefault();
        }
        if (win instanceof OutlineTopComponent) {
            return (OutlineTopComponent) win;
        }
        ErrorManager.getDefault().log(ErrorManager.WARNING, "There seem to be multiple components with the '" + PREFERRED_ID + "' ID. That is a potential source of errors and unexpected behavior.");
        return getDefault();
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_ALWAYS;
    }

    @Override
    public void componentOpened() {
        LookupHistory.addListener(InputGraphProvider.class, this);
        this.requestActive();
    }

    @Override
    public void componentClosed() {
        LookupHistory.removeListener(InputGraphProvider.class, this);
    }

    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    @Override
    public void requestActive() {
        super.requestActive();
        treeView.requestFocus();
    }

    @Override
    public boolean requestFocus(boolean temporary) {
        treeView.requestFocus();
        return super.requestFocus(temporary);
    }

    @Override
    protected boolean requestFocusInWindow(boolean temporary) {
        treeView.requestFocus();
        return super.requestFocusInWindow(temporary);
    }

    @Override
    public void changed(InputGraphProvider lastProvider) {
        for (GraphNode graphNode : selectedGraphs) {
            graphNode.setSelected(false);
        }
        for (FolderNode folderNode : selectedFolders) {
            folderNode.setSelected(false);
        }
        selectedGraphs = new GraphNode[0];
        selectedFolders.clear();
        if (lastProvider != null) {
            // Try to fetch and select the latest active graph.
            InputGraph graph = lastProvider.getGraph();
            if (graph != null) {
                if (graph.isDiffGraph()) {
                    EditorTopComponent editor = EditorTopComponent.getActive();
                    if (editor != null) {
                        InputGraph firstGraph = editor.getModel().getFirstGraph();
                        GraphNode firstNode = FolderNode.getGraphNode(firstGraph);
                        InputGraph secondGraph = editor.getModel().getSecondGraph();
                        GraphNode secondNode = FolderNode.getGraphNode(secondGraph);
                        if (firstNode != null && secondNode != null) {
                            selectedGraphs = new GraphNode[]{firstNode, secondNode};
                        }
                    }
                } else {
                    GraphNode graphNode = FolderNode.getGraphNode(graph);
                    if (graphNode != null) {
                        selectedGraphs = new GraphNode[]{graphNode};
                    }
                }
            }
        }
        try {
            for (GraphNode graphNode : selectedGraphs) {
                Node parentNode = graphNode.getParentNode();
                if (parentNode instanceof FolderNode) {
                    FolderNode folderNode = (FolderNode) graphNode.getParentNode();
                    folderNode.setSelected(true);
                    selectedFolders.add(folderNode);
                }
                graphNode.setSelected(true);
            }
            manager.setSelectedNodes(selectedGraphs);
        } catch (Exception e) {
            Exceptions.printStackTrace(e);
        }
    }

    private void loadState() {
        ((BeanTreeView) this.treeView).setRootVisible(false);

        String graphsPath = getGraphsPath();
        if (graphsPath.isEmpty()) {
            return;
        }
        try {
            loadFile(graphsPath);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return;
        }

        final String openedPath = getOpenedPath();
        if (openedPath.isEmpty()) {
            return;
        }

        RP.post(() -> {
            try {
                FileInputStream fis = new FileInputStream(openedPath);
                ObjectInputStream in = new ObjectInputStream(fis);

                int formatVersion = in.readInt();
                assert formatVersion <= STATE_FORMAT_VERSION;

                final GraphViewer viewer = Lookup.getDefault().lookup(GraphViewer.class);
                assert viewer != null;
                int tabCount = in.readInt();
                for (int i = 0; i < tabCount; i++) {
                    final boolean isDiffGraph = in.readBoolean();
                    int firstGroupIdx = in.readInt();
                    int firstGraphIdx = in.readInt();
                    String firstGraphTag = in.readUTF();
                    final InputGraph firstGraph = findGraph(firstGroupIdx, firstGraphIdx);
                    if (firstGraph == null || firstGraph.getGroup() == null ||
                            !firstGraphTag.equals(firstGraph.getGroup().getName() + "#" + firstGraph.getName())) {
                        break;
                    }
                    final InputGraph secondGraph;
                    if (isDiffGraph) {
                        int secondGroupIdx = in.readInt();
                        int secondGraphIdx = in.readInt();
                        String secondGraphTag = in.readUTF();
                        secondGraph = findGraph(secondGroupIdx, secondGraphIdx);
                        if (secondGraph == null || secondGraph.getGroup() == null ||
                                !secondGraphTag.equals(secondGraph.getGroup().getName() + "#" + secondGraph.getName())) {
                            break;
                        }
                    } else {
                        secondGraph = null;
                    }
                    final Set<Integer> hiddenNodes = new HashSet<>();
                    int hiddenNodeCount = in.readInt();
                    for (int j = 0; j < hiddenNodeCount; j++) {
                        int hiddenNodeID = in.readInt();
                        hiddenNodes.add(hiddenNodeID);
                    }

                    SwingUtilities.invokeLater(() -> {
                        InputGraph openedGraph;
                        if (isDiffGraph) {
                            openedGraph = viewer.viewDifference(firstGraph, secondGraph);
                        } else {
                            openedGraph = viewer.view(firstGraph, true);
                        }
                        if (openedGraph != null) {
                            EditorTopComponent etc = EditorTopComponent.findEditorForGraph(openedGraph);
                            if (etc != null) {
                                etc.getModel().setHiddenNodes(hiddenNodes);
                            }
                        }
                    });
                }
                in.close();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        });
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
        super.readExternal(objectInput);
    }

    private String getCustomSettingsPath() {
        return Places.getUserDirectory().getAbsolutePath();
    }

    private String getGraphsPath() {
        String igvSettingsPath = getCustomSettingsPath();
        if (!igvSettingsPath.isEmpty()) {
            igvSettingsPath += "/graphs.xml";
        }
        return igvSettingsPath;
    }

    private String getOpenedPath() {
        String igvSettingsPath = getCustomSettingsPath();
        if (!igvSettingsPath.isEmpty()) {
            igvSettingsPath += "/state.igv";
        }
        return igvSettingsPath;
    }

    @Override
    public void writeExternal(ObjectOutput objectOutput) throws IOException {
        super.writeExternal(objectOutput);
        saveState();
    }

    private void saveState() throws IOException {
        String graphsPath = getGraphsPath();
        if (graphsPath.isEmpty()) {
            return;
        }
        File file = new File(graphsPath);
        SaveAsAction.export(file, getDocument());

        String openedPath = getOpenedPath();
        if (openedPath.isEmpty()) {
            return;
        }
        FileOutputStream fos = new FileOutputStream(openedPath);
        ObjectOutputStream out = new ObjectOutputStream(fos);

        List<EditorTopComponent> editorTabs = new ArrayList<>();
        WindowManager manager = WindowManager.getDefault();
        for (Mode mode : manager.getModes()) {
            List<TopComponent> compList = new ArrayList<>(Arrays.asList(manager.getOpenedTopComponents(mode)));
            for (TopComponent comp : compList) {
                if (comp instanceof EditorTopComponent) {
                    editorTabs.add((EditorTopComponent) comp);
                }
            }
        }

        out.writeInt(STATE_FORMAT_VERSION);

        int tabCount = editorTabs.size();
        out.writeInt(tabCount);
        for (EditorTopComponent etc : editorTabs) {
            DiagramViewModel model = etc.getModel();
            boolean isDiffGraph = model.getGraph().isDiffGraph();
            out.writeBoolean(isDiffGraph);
            if (isDiffGraph) {
                InputGraph firstGraph = model.getFirstGraph();
                InputGraph secondGraph = model.getSecondGraph();
                out.writeInt(firstGraph.getGroup().getIndex());
                out.writeInt(firstGraph.getIndex());
                out.writeUTF(firstGraph.getGroup().getName() + "#" + firstGraph.getName());
                out.writeInt(secondGraph.getGroup().getIndex());
                out.writeInt(secondGraph.getIndex());
                out.writeUTF(secondGraph.getGroup().getName() + "#" + secondGraph.getName());
            } else {
                InputGraph graph = model.getGraph();
                out.writeInt(graph.getGroup().getIndex());
                out.writeInt(graph.getIndex());
                out.writeUTF(graph.getGroup().getName() + "#" + graph.getName());
            }
            int hiddenNodeCount = model.getHiddenNodes().size();
            out.writeInt(hiddenNodeCount);
            for (int hiddenNodeID : model.getHiddenNodes()) {
                out.writeInt(hiddenNodeID);
            }
        }
        out.close();
    }

    public void loadFile(String absolutePath) throws IOException {
        RP.post(() -> {
            File file = new File(absolutePath);

            final FileChannel channel;
            final long start;
            try {
                channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
                start = channel.size();
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
                return;
            }

            final ProgressHandle handle = ProgressHandleFactory.createHandle("Opening file " + file.getName());
            handle.start(WORKUNITS);

            ParseMonitor monitor = new ParseMonitor() {
                @Override
                public void updateProgress() {
                    try {
                        int prog = (int) (WORKUNITS * (double) channel.position() / (double) start);
                        handle.progress(prog);
                    } catch (IOException ignored) {}
                }
                @Override
                public void setState(String state) {
                    updateProgress();
                    handle.progress(state);
                }
            };
            try {
                if (file.getName().endsWith(".xml")) {
                    final Parser parser = new Parser(channel, monitor, null);
                    parser.setInvokeLater(false);
                    final GraphDocument parsedDoc = parser.parse();
                    getDocument().addGraphDocument(parsedDoc);
                    SwingUtilities.invokeLater(this::requestActive);
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            handle.finish();
        });
    }

    public InputGraph findGraph(int groupIdx, int graphIdx) {
        FolderElement folderElement = document.getElements().get(groupIdx);
        if (folderElement instanceof Group) {
            Group group = (Group) folderElement;
            return group.getGraphs().get(graphIdx);
        }
        return null;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        treeView = new BeanTreeView();

        setLayout(new java.awt.BorderLayout());
        add(treeView, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane treeView;
    // End of variables declaration//GEN-END:variables
}
