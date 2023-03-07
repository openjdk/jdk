/*
 * Copyright (c) 2008, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.filterwindow;

import com.sun.hotspot.igv.data.ChangedEvent;
import com.sun.hotspot.igv.data.ChangedListener;
import com.sun.hotspot.igv.filter.CustomFilter;
import com.sun.hotspot.igv.filter.Filter;
import com.sun.hotspot.igv.filter.FilterChain;
import com.sun.hotspot.igv.filterwindow.actions.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.*;
import javax.swing.border.Border;
import org.openide.DialogDisplayer;
import org.openide.ErrorManager;
import org.openide.NotifyDescriptor;
import org.openide.awt.Toolbar;
import org.openide.awt.ToolbarPool;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.*;
import org.openide.util.actions.SystemAction;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 *
 * @author Thomas Wuerthinger
 */
public final class FilterTopComponent extends TopComponent implements ExplorerManager.Provider {

    private static FilterTopComponent instance;
    public static final String FOLDER_ID = "Filters";
    public static final String AFTER_ID = "after";
    public static final String ENABLED_ID = "enabled";
    public static final String PREFERRED_ID = "FilterTopComponent";
    public static final String JAVASCRIPT_HELPER_ID = "JavaScriptHelper";
    private final CheckListView view;
    private final ExplorerManager manager;
    private final ScriptEngine engine;
    private final JComboBox<FilterChain> comboBox;
    private final FilterChain allFilterChains = new FilterChain();
    private static final FilterChain defaultFilterChain = new FilterChain("DEFAULT");
    private FilterChain customFilterChain;
    private final ChangedEvent<FilterTopComponent> filterSettingsChangedEvent;
    private ChangedEvent<JComboBox<FilterChain>> filterChainSelectionChangedEvent;
    private final ActionListener comboBoxSelectionChangedListener = l -> comboBoxSelectionChanged();
    private static final String CUSTOM_LABEL = "--Local--";
    private static final String GLOBAL_LABEL = "Global";


    public static FilterChain createNewDefaultFilterChain() {
        FilterChain newCustomFilterChain = new FilterChain(CUSTOM_LABEL);
        newCustomFilterChain.addFilters(defaultFilterChain.getFilters());
        return newCustomFilterChain;
    }

    static class CustomCellRenderer extends DefaultListCellRenderer {

        public CustomCellRenderer() {
            setOpaque(true);
        }

        public Component getListCellRendererComponent(JList jc, Object val, int idx,
                                                      boolean isSelected, boolean cellHasFocus) {
            setText(" " + val.toString());
            if (idx == 0) {
                setForeground(Color.GRAY);
            } else {
                setForeground(Color.BLACK);
            }
            if (isSelected) {
                setBackground(Color.LIGHT_GRAY);
            } else {
                setBackground(Color.WHITE);
            }
            return this;
        }
    }

    private FilterTopComponent() {
        filterSettingsChangedEvent = new ChangedEvent<>(this);
        initComponents();
        setName(NbBundle.getMessage(FilterTopComponent.class, "CTL_FilterTopComponent"));
        setToolTipText(NbBundle.getMessage(FilterTopComponent.class, "HINT_FilterTopComponent"));

        ScriptEngineManager sem = new ScriptEngineManager();
        engine = sem.getEngineByName("ECMAScript");
        try {
            engine.eval(getJsHelperText());
        } catch (ScriptException ex) {
            Exceptions.printStackTrace(ex);
        }
        engine.getContext().getBindings(ScriptContext.ENGINE_SCOPE).put("IO", System.out);

        initFilters();
        customFilterChain = createNewDefaultFilterChain();
        comboBox = new JComboBox<>();
        comboBox.setRenderer(new CustomCellRenderer());
        comboBox.addItem(customFilterChain);
        FilterChain globalFilterChain = new FilterChain(GLOBAL_LABEL);
        globalFilterChain.addFilters(defaultFilterChain.getFilters());
        comboBox.addItem(globalFilterChain);
        comboBox.setSelectedItem(customFilterChain);
        filterChainSelectionChangedEvent = new ChangedEvent<>(comboBox);

        manager = new ExplorerManager();
        manager.setRootContext(new AbstractNode(new FilterChildren()));
        associateLookup(ExplorerUtils.createLookup(manager, getActionMap()));
        view = new CheckListView();

        ToolbarPool.getDefault().setPreferredIconSize(16);
        Toolbar toolBar = new Toolbar();
        toolBar.setBorder((Border) UIManager.get("Nb.Editor.Toolbar.border")); //NOI18N
        toolBar.setMinimumSize(new Dimension(0,0)); // MacOS BUG with ToolbarWithOverflow

        toolBar.add(comboBox);
        this.add(toolBar, BorderLayout.NORTH);
        toolBar.add(SaveFilterSettingsAction.get(SaveFilterSettingsAction.class));
        toolBar.add(RemoveFilterSettingsAction.get(RemoveFilterSettingsAction.class));
        toolBar.addSeparator();
        toolBar.add(NewFilterAction.get(NewFilterAction.class));
        toolBar.add(RemoveFilterAction.get(RemoveFilterAction.class).createContextAwareInstance(this.getLookup()));
        toolBar.add(MoveFilterUpAction.get(MoveFilterUpAction.class).createContextAwareInstance(this.getLookup()));
        toolBar.add(MoveFilterDownAction.get(MoveFilterDownAction.class).createContextAwareInstance(this.getLookup()));
        this.add(view, BorderLayout.CENTER);

        comboBox.addActionListener(comboBoxSelectionChangedListener);
        comboBoxSelectionChanged();
    }

    public ChangedEvent<FilterTopComponent> getFilterSettingsChangedEvent() {
        return filterSettingsChangedEvent;
    }

    public void setFilterChainSelectionChangedListener(ChangedListener<JComboBox<FilterChain>> listener) {
        filterChainSelectionChangedEvent = new ChangedEvent<>(comboBox);
        filterChainSelectionChangedEvent.addListener(listener);
    }

    public FilterChain getAllFilterChains() {
        return allFilterChains;
    }

    public FilterChain getCurrentChain() {
        return (FilterChain) comboBox.getSelectedItem();
    }

    public void selectFilterChain(FilterChain filterChain) {
        comboBox.setSelectedItem(filterChain);
        if (comboBox.getSelectedIndex() < 0) {
            comboBox.setSelectedIndex(0);
        }
    }

    public void setCustomFilterChain(FilterChain filterChain) {
        comboBox.removeActionListener(comboBoxSelectionChangedListener);
        comboBox.removeItem(customFilterChain);
        customFilterChain = filterChain;
        comboBox.insertItemAt(customFilterChain, 0);
        comboBox.addActionListener(comboBoxSelectionChangedListener);
    }

    private void comboBoxSelectionChanged() {
        FilterChain currentChain = getCurrentChain();
        if (currentChain == null) {
            return;
        }

        filterSettingsChangedEvent.fire();
        filterChainSelectionChangedEvent.fire();
        currentChain.getChangedEvent().fire();
        SystemAction.get(RemoveFilterSettingsAction.class).setEnabled(currentChain != customFilterChain);
        SystemAction.get(SaveFilterSettingsAction.class).setEnabled(true);
    }

    public void addFilterSetting() {
        NotifyDescriptor.InputLine l = new NotifyDescriptor.InputLine("Name of the new profile:", "Filter Profile");
        if (DialogDisplayer.getDefault().notify(l) == NotifyDescriptor.OK_OPTION) {
            String name = l.getInputText();
            for (int i=0; i<comboBox.getItemCount(); i++) {
                FilterChain s = comboBox.getItemAt(i);
                if (s.getName().equals(name)) {
                    NotifyDescriptor.Confirmation conf = new NotifyDescriptor.Confirmation("Filter profile \"" + name + "\" already exists, do you want to replace it?", "Filter");
                    if (DialogDisplayer.getDefault().notify(conf) == NotifyDescriptor.YES_OPTION) {
                        comboBox.removeItem(s);
                        break;
                    } else {
                        return;
                    }
                }
            }

            FilterChain setting = new FilterChain(name);
            FilterChain chain = getCurrentChain();
            for (Filter f : chain.getFilters()) {
                setting.addFilter(f);
            }

            comboBox.addItem(setting);
            comboBox.setSelectedItem(setting);
        }
    }

    public void removeFilterSetting() {
        if (getCurrentChain() != customFilterChain) {
            FilterChain filter = getCurrentChain();
            NotifyDescriptor.Confirmation l = new NotifyDescriptor.Confirmation("Do you really want to remove filter profile \"" + filter + "\"?", "Filter Profile");
            if (DialogDisplayer.getDefault().notify(l) == NotifyDescriptor.YES_OPTION) {
                comboBox.removeItem(filter);
            }
        }
    }

    private class FilterChildren extends Children.Keys<Filter> implements ChangedListener<CheckNode> {

        private final HashMap<Filter, Node> nodeHash = new HashMap<>();

        @Override
        protected Node[] createNodes(Filter filter) {
            if (nodeHash.containsKey(filter)) {
                return new Node[]{nodeHash.get(filter)};
            }

            FilterNode node = new FilterNode(filter);
            node.getSelectionChangedEvent().addListener(this);
            nodeHash.put(filter, node);
            return new Node[]{node};
        }

        public FilterChildren() {
            allFilterChains.getChangedEvent().addListener(source -> addNotify());
            setBefore(false);
        }

        @Override
        protected void addNotify() {
            setKeys(allFilterChains.getFilters());
            updateSelection();
        }

        private void updateSelection() {
            Node[] nodes = getExplorerManager().getSelectedNodes();
            int[] arr = new int[nodes.length];
            for (int i = 0; i < nodes.length; i++) {
                int index = allFilterChains.getFilters().indexOf(((FilterNode) nodes[i]).getFilter());
                arr[i] = index;
            }
            view.showSelection(arr);
        }

        @Override
        public void changed(CheckNode source) {
            FilterNode node = (FilterNode) source;
            Filter f = node.getFilter();
            FilterChain chain = getCurrentChain();
            if (node.isSelected()) {
                if (!chain.containsFilter(f)) {
                    chain.addFilter(f);
                }
            } else {
                if (chain.containsFilter(f)) {
                    chain.removeFilter(f);
                }
            }
            view.revalidate();
            view.repaint();
        }
    }

    private static String getJsHelperText() {
        InputStream is = null;
        StringBuilder sb = new StringBuilder("if (typeof importPackage === 'undefined') { try { load('nashorn:mozilla_compat.js'); } catch (e) {} }"
                + "importPackage(Packages.com.sun.hotspot.igv.filter);"
                + "importPackage(Packages.com.sun.hotspot.igv.graph);"
                + "importPackage(Packages.com.sun.hotspot.igv.data);"
                + "importPackage(Packages.com.sun.hotspot.igv.util);"
                + "importPackage(java.awt);");
        try {
            FileObject fo = FileUtil.getConfigRoot().getFileObject(JAVASCRIPT_HELPER_ID);
            is = fo.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            String s;
            while ((s = r.readLine()) != null) {
                sb.append(s);
                sb.append("\n");
            }

        } catch (IOException ex) {
            Logger.getLogger("global").log(Level.SEVERE, null, ex);
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return sb.toString();
    }

    public void newFilter() {
        CustomFilter cf = new CustomFilter("My custom filter", "", engine);
        if (cf.openInEditor()) {
            allFilterChains.addFilter(cf);
            FileObject fo = getFileObject(cf);
            FilterChangedListener listener = new FilterChangedListener(fo, cf);
            listener.changed(cf);
            cf.getChangedEvent().addListener(listener);
        }
    }

    public void removeFilter(Filter f) {
        CustomFilter cf = (CustomFilter) f;

        allFilterChains.removeFilter(cf);
        try {
            getFileObject(cf).delete();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

    }

    private static class FilterChangedListener implements ChangedListener<Filter> {

        private FileObject fileObject;
        private final CustomFilter filter;

        public FilterChangedListener(FileObject fo, CustomFilter cf) {
            fileObject = fo;
            filter = cf;
        }

        @Override
        public void changed(Filter source) {
            try {
                if (!fileObject.getName().equals(filter.getName())) {
                    FileLock lock = fileObject.lock();
                    fileObject.move(lock, fileObject.getParent(), filter.getName(), "");
                    lock.releaseLock();
                    fileObject = fileObject.getParent().getFileObject(filter.getName());
                }

                FileLock lock = fileObject.lock();
                OutputStream os = fileObject.getOutputStream(lock);
                try (Writer w = new OutputStreamWriter(os)) {
                    String s = filter.getCode();
                    w.write(s);
                }
                lock.releaseLock();

            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    public void initFilters() {
        FileObject folder = FileUtil.getConfigRoot().getFileObject(FOLDER_ID);
        FileObject[] children = folder.getChildren();

        List<CustomFilter> customFilters = new ArrayList<>();
        HashMap<CustomFilter, String> afterMap = new HashMap<>();
        Set<CustomFilter> enabledSet = new HashSet<>();
        HashMap<String, CustomFilter> map = new HashMap<>();

        for (final FileObject fo : children) {
            InputStream is = null;

            String code = "";
            FileLock lock = null;
            try {
                lock = fo.lock();
                is = fo.getInputStream();
                BufferedReader r = new BufferedReader(new InputStreamReader(is));
                String s;
                StringBuilder sb = new StringBuilder();
                while ((s = r.readLine()) != null) {
                    sb.append(s);
                    sb.append("\n");
                }
                code = sb.toString();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                try {
                    assert is != null;
                    is.close();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
                lock.releaseLock();
            }

            String displayName = fo.getName();

            final CustomFilter cf = new CustomFilter(displayName, code, engine);
            map.put(displayName, cf);

            String after = (String) fo.getAttribute(AFTER_ID);
            afterMap.put(cf, after);

            Boolean enabled = (Boolean) fo.getAttribute(ENABLED_ID);
            if (enabled != null && enabled) {
                enabledSet.add(cf);
            }

            cf.getChangedEvent().addListener(new FilterChangedListener(fo, cf));

            customFilters.add(cf);
        }

        for (int j = 0; j < customFilters.size(); j++) {
            for (int i = 0; i < customFilters.size(); i++) {
                List<CustomFilter> copiedList = new ArrayList<>(customFilters);
                for (CustomFilter cf : copiedList) {

                    String after = afterMap.get(cf);

                    if (map.containsKey(after)) {
                        CustomFilter afterCf = map.get(after);
                        int index = customFilters.indexOf(afterCf);
                        int currentIndex = customFilters.indexOf(cf);

                        if (currentIndex < index) {
                            customFilters.remove(currentIndex);
                            customFilters.add(index, cf);
                        }
                    }
                }
            }
        }

        for (CustomFilter cf : customFilters) {
            allFilterChains.addFilter(cf);
            if (enabledSet.contains(cf)) {
                defaultFilterChain.addFilter(cf);
            }
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new java.awt.BorderLayout());

    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    /**
     * Gets default instance. Do not use directly: reserved for *.settings files only,
     * i.e. deserialization routines; otherwise you could get a non-deserialized instance.
     * To obtain the singleton instance, use {@link #findInstance()}.
     */
    public static synchronized FilterTopComponent getDefault() {
        if (instance == null) {
            instance = new FilterTopComponent();
        }
        return instance;
    }

    /**
     * Obtain the FilterTopComponent instance. Never call {@link #getDefault} directly!
     */
    public static synchronized FilterTopComponent findInstance() {
        TopComponent win = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (win == null) {
            ErrorManager.getDefault().log(ErrorManager.WARNING, "Cannot find Filter component. It will not be located properly in the window system.");
            return getDefault();
        }
        if (win instanceof FilterTopComponent) {
            return (FilterTopComponent) win;
        }
        ErrorManager.getDefault().log(ErrorManager.WARNING, "There seem to be multiple components with the '" + PREFERRED_ID + "' ID. That is a potential source of errors and unexpected behavior.");
        return getDefault();
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_ALWAYS;
    }

    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return manager;
    }

    private FileObject getFileObject(CustomFilter cf) {
        FileObject fo = FileUtil.getConfigRoot().getFileObject(FOLDER_ID + "/" + cf.getName());
        if (fo == null) {
            try {
                fo = FileUtil.getConfigRoot().getFileObject(FOLDER_ID).createData(cf.getName());
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return fo;
    }

    @Override
    public boolean requestFocus(boolean temporary) {
        view.requestFocus();
        return super.requestFocus(temporary);
    }

    @Override
    protected boolean requestFocusInWindow(boolean temporary) {
        view.requestFocus();
        return super.requestFocusInWindow(temporary);
    }

    @Override
    public void requestActive() {
        super.requestActive();
        view.requestFocus();
    }


    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeInt(comboBox.getItemCount()-1);
        for (int i=0; i<comboBox.getItemCount(); i++) {
            FilterChain filterChain = comboBox.getItemAt(i);
            if (filterChain != customFilterChain) {
                out.writeUTF(filterChain.getName());
                out.writeInt(filterChain.getFilters().size());
                for (Filter filter : filterChain.getFilters()) {
                    CustomFilter cf = (CustomFilter) filter;
                    out.writeUTF(cf.getName());
                }
            }
        }
    }

    public CustomFilter findFilter(String name) {
        for (Filter f : allFilterChains.getFilters()) {
            CustomFilter cf = (CustomFilter) f;
            if (cf.getName().equals(name)) {
                return cf;
            }
        }

        return null;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);

        int filterSettingsCount = in.readInt();
        for (int i = 0; i < filterSettingsCount; i++) {
            String name = in.readUTF();
            int filterCount = in.readInt();
            FilterChain filterChain = new FilterChain(name);
            for (int j = 0; j < filterCount; j++) {
                String filterName = in.readUTF();
                CustomFilter filter = findFilter(filterName);
                if (filter != null) {
                    filterChain.addFilter(filter);
                }
            }
            for (int cnt=0; cnt<comboBox.getItemCount(); cnt++) {
                FilterChain s = comboBox.getItemAt(cnt);
                if (s.getName().equals(name)) {
                    comboBox.removeItem(s);
                }
            }
            comboBox.addItem(filterChain);
        }
    }
}
