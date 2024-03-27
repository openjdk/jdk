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
import com.sun.hotspot.igv.data.serialization.Printer;
import com.sun.hotspot.igv.data.serialization.Printer.GraphContext;
import com.sun.hotspot.igv.data.services.GraphViewer;
import com.sun.hotspot.igv.data.services.GroupCallback;
import com.sun.hotspot.igv.data.services.InputGraphProvider;
import com.sun.hotspot.igv.settings.Settings;
import com.sun.hotspot.igv.util.LookupHistory;
import com.sun.hotspot.igv.view.EditorTopComponent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import javax.swing.*;
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
import org.openide.util.*;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import javax.swing.JButton;

/**
 *
 * @author Thomas Wuerthinger
 */
public final class OutlineTopComponent extends TopComponent implements ExplorerManager.Provider, ChangedListener<InputGraphProvider> {

    public static OutlineTopComponent instance;
    public static final String PREFERRED_ID = "OutlineTopComponent";
    private ExplorerManager manager;
    private static final GraphDocument document = new GraphDocument();
    private FolderNode root;
    private SaveAllAction saveAllAction;
    private RemoveAllAction removeAllAction;
    private JButton changeWorkspaceButton;
    private GraphNode[] selectedGraphs = new GraphNode[0];
    private final Set<FolderNode> selectedFolders = new HashSet<>();
    private static final int WORKUNITS = 10000;
    private static final int STATE_FORMAT_VERSION = 1;
    private static final RequestProcessor RP = new RequestProcessor("OutlineTopComponent", 1);

    private OutlineTopComponent() {
        initComponents();

        setName(NbBundle.getMessage(OutlineTopComponent.class, "CTL_OutlineTopComponent"));
        setToolTipText(NbBundle.getMessage(OutlineTopComponent.class, "HINT_OutlineTopComponent"));

        initListView();
        initToolbar();
        initReceivers();

        String userDirectory = Places.getUserDirectory().getAbsolutePath();
        setWorkspacePath(userDirectory);
        loadWorkspace();
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

        saveAllAction = SaveAllAction.get(SaveAllAction.class);
        saveAllAction.setEnabled(false);
        toolbar.add(saveAllAction);

        toolbar.add(SaveAsAction.get(SaveAsAction.class).createContextAwareInstance(this.getLookup()));
        toolbar.add(RemoveAction.get(RemoveAction.class).createContextAwareInstance(this.getLookup()));

        removeAllAction = RemoveAllAction.get(RemoveAllAction.class);
        removeAllAction.setEnabled(false);
        toolbar.add(removeAllAction);

        for (Toolbar tb : ToolbarPool.getDefault().getToolbars()) {
            if (tb.getName().equals("GlobalToolbar")) {
                continue;
            }
            tb.setVisible(false);
        }

        JToolBar globalToolbar = ToolbarPool.getDefault().findToolbar("GlobalToolbar");
        if (globalToolbar != null) {
            Icon folderIcon = UIManager.getIcon("FileView.hardDriveIcon");
            changeWorkspaceButton = new JButton("Select a workspace...", folderIcon);
            changeWorkspaceButton.setToolTipText("Select a workspace...");
            changeWorkspaceButton.addActionListener(this::onChangeWorkspaceClicked);
            globalToolbar.add(changeWorkspaceButton);
            globalToolbar.add(Box.createHorizontalGlue());
            globalToolbar.add(GarbageCollectAction.get(GarbageCollectAction.class).getToolbarPresenter());
            globalToolbar.revalidate();
            globalToolbar.repaint();
        }

        document.getChangedEvent().addListener(g -> documentChanged());
    }

    private void onChangeWorkspaceClicked(ActionEvent event) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Workspace Folder");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);

        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            File newWorkspace = fileChooser.getSelectedFile();
            String workspacePath = newWorkspace.getAbsolutePath();
            if (!workspacePath.isEmpty()) {
                saveWorkspace();
                setWorkspacePath(workspacePath);
                loadWorkspace();
            }
        }
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

    public static GraphDocument getDocument() {
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
                    InputGraph firstGraph = graph.getFirstGraph();
                    GraphNode firstNode = FolderNode.getGraphNode(firstGraph);
                    InputGraph secondGraph = graph.getSecondGraph();
                    GraphNode secondNode = FolderNode.getGraphNode(secondGraph);
                    if (firstNode != null && secondNode != null) {
                        selectedGraphs = new GraphNode[]{firstNode, secondNode};
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

    private void loadWorkspace() {
        clear();
        ((BeanTreeView) this.treeView).setRootVisible(false);

        try {
            loadGraphDocument(getWorkspaceGraphsPath());
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
        super.readExternal(objectInput);
    }

    private String workspacePath;

    private void setWorkspacePath(String path) {
        changeWorkspaceButton.setText(path);
        workspacePath = path;
    }

    public static String WORKSPACE_XML_FILE = "graphs.xml";

    private String getWorkspaceGraphsPath() {
        return workspacePath + "/" + WORKSPACE_XML_FILE;
    }


    public void saveWorkspace() {
        try {
            saveGraphDocument(getDocument(), getWorkspaceGraphsPath(), true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeExternal(ObjectOutput objectOutput) throws IOException {
        super.writeExternal(objectOutput);
        saveWorkspace();
    }

    private static File chooseFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(ImportAction.getFileFilter());
        fc.setCurrentDirectory(new File(Settings.get().get(Settings.DIRECTORY, Settings.DIRECTORY_DEFAULT)));

        if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (!file.getName().contains(".")) {
                file = new File(file.getAbsolutePath() + ".xml");
            }

            File dir = file;
            if (!dir.isDirectory()) {
                dir = dir.getParentFile();
            }
            Settings.get().put(Settings.DIRECTORY, dir.getAbsolutePath());
            return file;
        }
        return null;
    }

    private void loadStates(String path) throws IOException {
        RP.post(() -> {
            try {
                if (Files.notExists(Path.of(path))) {
                    return;
                }
                FileInputStream fis = new FileInputStream(path);
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


    public void loadGraphDocument(String path) throws IOException {
        RP.post(() -> {
            if (Files.notExists(Path.of(path))) {
                return;
            }
            File file = new File(path);
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

    public static void saveGraphDocument(GraphDocument doc, String path, boolean saveState) throws IOException {
        final File graphFile;
        if (path == null || path.isEmpty()) {
            graphFile = OutlineTopComponent.chooseFile();
        } else {
            graphFile = new File(path);
        }
        if (graphFile == null) {
            return;
        }

        Set<GraphContext> saveContexts = new HashSet<>();
        if (saveState) {
            WindowManager manager = WindowManager.getDefault();
            for (Mode mode : manager.getModes()) {
                List<TopComponent> compList = new ArrayList<>(Arrays.asList(manager.getOpenedTopComponents(mode)));
                for (TopComponent comp : compList) {
                    if (comp instanceof EditorTopComponent etc) {
                        InputGraph openedGraph = etc.getModel().getFirstGraph();
                        int posDiff = etc.getModel().getSecondPosition() - etc.getModel().getFirstPosition();
                        Set<Integer> hiddenNodes = new HashSet<>(etc.getModel().getHiddenNodes());
                        Set<Integer> selectedNodes = new HashSet<>(etc.getModel().getSelectedNodes());
                        GraphContext graphContext = new GraphContext(openedGraph, posDiff, hiddenNodes, selectedNodes);
                        saveContexts.add(graphContext);
                    }
                }
            }
        }

        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(graphFile.toPath()))) {
            Printer printer = new Printer();
            printer.exportGraphDocument(writer, doc, saveContexts);
        }

    }



    private InputGraph findGraph(int groupIdx, int graphIdx) {
        FolderElement folderElement = document.getElements().get(groupIdx);
        if (folderElement instanceof Group group) {
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

        setLayout(new BorderLayout());
        add(treeView, BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private JScrollPane treeView;
    // End of variables declaration//GEN-END:variables
}
