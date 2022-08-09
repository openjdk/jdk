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
package com.sun.hotspot.igv.coordinator;

import com.sun.hotspot.igv.connection.Server;
import com.sun.hotspot.igv.coordinator.actions.*;
import com.sun.hotspot.igv.data.GraphDocument;
import com.sun.hotspot.igv.data.Group;
import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.services.GroupCallback;
import com.sun.hotspot.igv.data.services.InputGraphProvider;
import com.sun.hotspot.igv.util.LookupHistory;
import com.sun.hotspot.igv.view.EditorTopComponent;
import java.awt.BorderLayout;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import javax.swing.*;
import javax.swing.border.Border;
import org.openide.ErrorManager;
import org.openide.actions.GarbageCollectAction;
import org.openide.awt.Toolbar;
import org.openide.awt.ToolbarPool;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.explorer.view.BeanTreeView;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.actions.NodeAction;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 *
 * @author Thomas Wuerthinger
 */
public final class OutlineTopComponent extends TopComponent implements ExplorerManager.Provider, LookupListener {

    public static OutlineTopComponent instance;
    public static final String PREFERRED_ID = "OutlineTopComponent";
    private Lookup.Result result = null;
    private ExplorerManager manager;
    private GraphDocument document;
    private FolderNode root;
    private Server server;
    private Server binaryServer;
    private SaveAllAction saveAllAction;
    private RemoveAllAction removeAllAction;


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
        Border b = (Border) UIManager.get("Nb.Editor.Toolbar.border"); //NOI18N
        toolbar.setBorder(b);
        this.add(toolbar, BorderLayout.NORTH);

        toolbar.add(ImportAction.get(ImportAction.class));
        toolbar.add(((NodeAction) SaveAsAction.get(SaveAsAction.class)).createContextAwareInstance(this.getLookup()));

        saveAllAction = SaveAllAction.get(SaveAllAction.class);
        saveAllAction.setEnabled(false);
        toolbar.add(saveAllAction);

        toolbar.add(((NodeAction) RemoveAction.get(RemoveAction.class)).createContextAwareInstance(this.getLookup()));

        removeAllAction = RemoveAllAction.get(RemoveAllAction.class);
        removeAllAction.setEnabled(false);
        toolbar.add(removeAllAction);

        toolbar.add(GarbageCollectAction.get(GarbageCollectAction.class).getToolbarPresenter());

        for (Toolbar tb : ToolbarPool.getDefault().getToolbars()) {
            tb.setVisible(false);
        }

        document.getChangedEvent().addListener(g -> documentChanged());
    }

    private void documentChanged() {
        boolean enableButton = !document.getElements().isEmpty();
        saveAllAction.setEnabled(enableButton);
        removeAllAction.setEnabled(enableButton);
    }

    private void initReceivers() {

        final GroupCallback callback = new GroupCallback() {

            @Override
            public void started(Group g) {
                synchronized(OutlineTopComponent.this) {
                    getDocument().addElement(g);
                    g.setParent(getDocument());
                }
            }
        };

        server = new Server(getDocument(), callback, false);
        binaryServer = new Server(getDocument(), callback, true);
    }

    // Fetch and select the latest active graph.
    private void updateGraphSelection() {
        final InputGraphProvider p = LookupHistory.getLast(InputGraphProvider.class);
        if (p != null) {
            try {
                InputGraph graph = p.getGraph();
                if (graph.isDiffGraph()) {
                    EditorTopComponent editor = EditorTopComponent.getActive();
                    if (editor != null) {
                        InputGraph firstGraph = editor.getModel().getFirstGraph();
                        InputGraph secondGraph = editor.getModel().getSecondGraph();
                        manager.setSelectedNodes(new GraphNode[]{FolderNode.getGraphNode(firstGraph), FolderNode.getGraphNode(secondGraph)});
                    }
                } else {
                    manager.setSelectedNodes(new GraphNode[]{FolderNode.getGraphNode(graph)});
                }
            } catch (Exception e) {
                Exceptions.printStackTrace(e);
            }
        }
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
     * To obtain the singleton instance, use {@link findInstance}.
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
        Lookup.Template<InputGraphProvider> tpl = new Lookup.Template<InputGraphProvider>(InputGraphProvider.class);
        result = Utilities.actionsGlobalContext().lookup(tpl);
        result.addLookupListener(this);
        updateGraphSelection();
        this.requestActive();
    }

    @Override
    public void componentClosed() {
        result.removeLookupListener(this);
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
    public void resultChanged(LookupEvent lookupEvent) {
        // Highlight the focused graph, if available, in the outline.
        if (result.allItems().isEmpty()) {
            return;
        }
        // Wait for LookupHistory to be updated with the last active graph
        // before selecting it.
        SwingUtilities.invokeLater(() -> updateGraphSelection());
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
        // Not called when user starts application for the first time
        super.readExternal(objectInput);
        ((BeanTreeView) this.treeView).setRootVisible(false);
    }

    @Override
    public void writeExternal(ObjectOutput objectOutput) throws IOException {
        super.writeExternal(objectOutput);
    }

    static final class ResolvableHelper implements Serializable {

        private static final long serialVersionUID = 1L;

        public Object readResolve() {
            return OutlineTopComponent.getDefault();
        }
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
