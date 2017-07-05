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
package com.sun.hotspot.igv.coordinator;

import com.sun.hotspot.igv.coordinator.actions.ImportAction;
import com.sun.hotspot.igv.coordinator.actions.RemoveAction;
import com.sun.hotspot.igv.coordinator.actions.RemoveAllAction;
import com.sun.hotspot.igv.coordinator.actions.SaveAllAction;
import com.sun.hotspot.igv.coordinator.actions.SaveAsAction;
import com.sun.hotspot.igv.coordinator.actions.StructuredViewAction;
import com.sun.hotspot.igv.data.GraphDocument;
import com.sun.hotspot.igv.data.ChangedListener;
import com.sun.hotspot.igv.data.Group;
import com.sun.hotspot.igv.data.services.GroupCallback;
import com.sun.hotspot.igv.data.services.GroupOrganizer;
import com.sun.hotspot.igv.data.services.GroupReceiver;
import java.awt.BorderLayout;
import java.awt.Component;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import org.openide.ErrorManager;
import org.openide.awt.Toolbar;
import org.openide.awt.ToolbarPool;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.explorer.view.BeanTreeView;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
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
    private ExplorerManager manager;
    private GraphDocument document;
    private FolderNode root;
    private GroupOrganizer organizer;

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
        organizer = new StandardGroupOrganizer();
        root = new FolderNode("", organizer, new ArrayList<String>(), document.getGroups());
        manager.setRootContext(root);
        ((BeanTreeView) this.jScrollPane1).setRootVisible(false);

        document.getChangedEvent().addListener(new ChangedListener<GraphDocument>() {

            public void changed(GraphDocument document) {
                updateStructure();
            }
        });

        associateLookup(ExplorerUtils.createLookup(manager, getActionMap()));
    }

    private void initToolbar() {

        Toolbar toolbar = new Toolbar();
        Border b = (Border) UIManager.get("Nb.Editor.Toolbar.border"); //NOI18N
        toolbar.setBorder(b);
        this.add(toolbar, BorderLayout.NORTH);

        toolbar.add(ImportAction.get(ImportAction.class));
        toolbar.add(((NodeAction) RemoveAction.get(RemoveAction.class)).createContextAwareInstance(this.getLookup()));
        toolbar.add(RemoveAllAction.get(RemoveAllAction.class));

        toolbar.add(((NodeAction) SaveAsAction.get(SaveAsAction.class)).createContextAwareInstance(this.getLookup()));
        toolbar.add(SaveAllAction.get(SaveAllAction.class));

        toolbar.add(StructuredViewAction.get(StructuredViewAction.class).getToolbarPresenter());

        for (Toolbar tb : ToolbarPool.getDefault().getToolbars()) {
            tb.setVisible(false);
        }

        initOrganizers();
    }

    public void setOrganizer(GroupOrganizer organizer) {
        this.organizer = organizer;
        updateStructure();
    }

    private void initOrganizers() {

    }

    private void initReceivers() {

        final GroupCallback callback = new GroupCallback() {

            public void started(Group g) {
                getDocument().addGroup(g);
            }
        };

        Collection<? extends GroupReceiver> receivers = Lookup.getDefault().lookupAll(GroupReceiver.class);
        if (receivers.size() > 0) {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

            for (GroupReceiver r : receivers) {
                Component c = r.init(callback);
                panel.add(c);
            }

            jPanel2.add(panel, BorderLayout.PAGE_START);
        }
    }

    private void updateStructure() {
        root.init("", organizer, new ArrayList<String>(), document.getGroups());
    }

    public void clear() {
        document.clear();
    }

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
        this.requestActive();
    }

    @Override
    public void componentClosed() {
    }

    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    public void resultChanged(LookupEvent lookupEvent) {
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
        // Not called when user starts application for the first time
        super.readExternal(objectInput);
        ((BeanTreeView) this.jScrollPane1).setRootVisible(false);
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

        jPanel2 = new javax.swing.JPanel();
        jScrollPane1 = new BeanTreeView();

        setLayout(new java.awt.BorderLayout());

        jPanel2.setLayout(new java.awt.BorderLayout());
        jPanel2.add(jScrollPane1, java.awt.BorderLayout.CENTER);

        add(jPanel2, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables
}
