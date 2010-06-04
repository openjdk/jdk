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
package com.sun.hotspot.igv.filterwindow;

import com.sun.hotspot.igv.filterwindow.actions.MoveFilterDownAction;
import com.sun.hotspot.igv.filterwindow.actions.MoveFilterUpAction;
import com.sun.hotspot.igv.filterwindow.actions.NewFilterAction;
import com.sun.hotspot.igv.filterwindow.actions.RemoveFilterAction;
import com.sun.hotspot.igv.filterwindow.actions.RemoveFilterSettingsAction;
import com.sun.hotspot.igv.filterwindow.actions.SaveFilterSettingsAction;
import com.sun.hotspot.igv.filter.CustomFilter;
import com.sun.hotspot.igv.filter.Filter;
import com.sun.hotspot.igv.filter.FilterChain;
import com.sun.hotspot.igv.filter.FilterSetting;
import com.sun.hotspot.igv.data.ChangedEvent;
import com.sun.hotspot.igv.data.ChangedListener;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComboBox;
import javax.swing.UIManager;
import javax.swing.border.Border;
import org.openide.DialogDisplayer;
import org.openide.ErrorManager;
import org.openide.NotifyDescriptor;
import org.openide.awt.ToolbarPool;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.awt.Toolbar;
import org.openide.filesystems.FileLock;
import org.openide.util.actions.SystemAction;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.openide.filesystems.Repository;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileObject;

/**
 *
 * @author Thomas Wuerthinger
 */
public final class FilterTopComponent extends TopComponent implements LookupListener, ExplorerManager.Provider {

    private static FilterTopComponent instance;
    public static final String FOLDER_ID = "Filters";
    public static final String AFTER_ID = "after";
    public static final String ENABLED_ID = "enabled";
    public static final String PREFERRED_ID = "FilterTopComponent";
    private CheckListView view;
    private ExplorerManager manager;
    private FilterChain filterChain;
    private FilterChain sequence;
    private Lookup.Result result;
    private JComboBox comboBox;
    private List<FilterSetting> filterSettings;
    private FilterSetting customFilterSetting = new FilterSetting("-- Custom --");
    private ChangedEvent<FilterTopComponent> filterSettingsChangedEvent;
    private ActionListener comboBoxActionListener = new ActionListener() {

        public void actionPerformed(ActionEvent e) {
            comboBoxSelectionChanged();
        }
    };

    public ChangedEvent<FilterTopComponent> getFilterSettingsChangedEvent() {
        return filterSettingsChangedEvent;
    }

    public FilterChain getSequence() {
        return sequence;
    }

    public void updateSelection() {
        Node[] nodes = this.getExplorerManager().getSelectedNodes();
        int[] arr = new int[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            int index = sequence.getFilters().indexOf(((FilterNode) nodes[i]).getFilter());
            arr[i] = index;
        }
        view.showSelection(arr);
    }

    private void comboBoxSelectionChanged() {

        Object o = comboBox.getSelectedItem();
        if (o == null) {
            return;
        }
        assert o instanceof FilterSetting;
        FilterSetting s = (FilterSetting) o;

        if (s != customFilterSetting) {
            FilterChain chain = getFilterChain();
            chain.beginAtomic();
            List<Filter> toRemove = new ArrayList<Filter>();
            for (Filter f : chain.getFilters()) {
                if (!s.containsFilter(f)) {
                    toRemove.add(f);
                }
            }
            for (Filter f : toRemove) {
                chain.removeFilter(f);
            }

            for (Filter f : s.getFilters()) {
                if (!chain.containsFilter(f)) {
                    chain.addFilter(f);
                }
            }

            chain.endAtomic();
            filterSettingsChangedEvent.fire();
        } else {
            this.updateComboBoxSelection();
        }

        SystemAction.get(RemoveFilterSettingsAction.class).setEnabled(comboBox.getSelectedItem() != this.customFilterSetting);
        SystemAction.get(SaveFilterSettingsAction.class).setEnabled(comboBox.getSelectedItem() == this.customFilterSetting);
    }

    private void updateComboBox() {
        comboBox.removeAllItems();
        comboBox.addItem(customFilterSetting);
        for (FilterSetting s : filterSettings) {
            comboBox.addItem(s);
        }

        this.updateComboBoxSelection();
    }

    public void addFilterSetting() {
        NotifyDescriptor.InputLine l = new NotifyDescriptor.InputLine("Enter a name:", "Filter");
        if (DialogDisplayer.getDefault().notify(l) == NotifyDescriptor.OK_OPTION) {
            String name = l.getInputText();

            FilterSetting toRemove = null;
            for (FilterSetting s : filterSettings) {
                if (s.getName().equals(name)) {
                    NotifyDescriptor.Confirmation conf = new NotifyDescriptor.Confirmation("Filter \"" + name + "\" already exists, to you want to overwrite?", "Filter");
                    if (DialogDisplayer.getDefault().notify(conf) == NotifyDescriptor.YES_OPTION) {
                        toRemove = s;
                        break;
                    } else {
                        return;
                    }
                }
            }

            if (toRemove != null) {
                filterSettings.remove(toRemove);
            }
            FilterSetting setting = createFilterSetting(name);
            filterSettings.add(setting);

            // Sort alphabetically
            Collections.sort(filterSettings, new Comparator<FilterSetting>() {

                public int compare(FilterSetting o1, FilterSetting o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });

            updateComboBox();
        }
    }

    public boolean canRemoveFilterSetting() {
        return comboBox.getSelectedItem() != customFilterSetting;
    }

    public void removeFilterSetting() {
        if (canRemoveFilterSetting()) {
            Object o = comboBox.getSelectedItem();
            assert o instanceof FilterSetting;
            FilterSetting f = (FilterSetting) o;
            assert f != customFilterSetting;
            assert filterSettings.contains(f);
            NotifyDescriptor.Confirmation l = new NotifyDescriptor.Confirmation("Do you really want to remove filter \"" + f + "\"?", "Filter");
            if (DialogDisplayer.getDefault().notify(l) == NotifyDescriptor.YES_OPTION) {
                filterSettings.remove(f);
                updateComboBox();
            }
        }
    }

    private FilterSetting createFilterSetting(String name) {
        FilterSetting s = new FilterSetting(name);
        FilterChain chain = this.getFilterChain();
        for (Filter f : chain.getFilters()) {
            s.addFilter(f);
        }
        return s;
    }

    private void updateComboBoxSelection() {
        List<Filter> filters = this.getFilterChain().getFilters();
        boolean found = false;
        for (FilterSetting s : filterSettings) {
            if (s.getFilterCount() == filters.size()) {
                boolean ok = true;
                for (Filter f : filters) {
                    if (!s.containsFilter(f)) {
                        ok = false;
                    }
                }

                if (ok) {
                    if (comboBox.getSelectedItem() != s) {
                        comboBox.setSelectedItem(s);
                    }
                    found = true;
                    break;
                }
            }
        }

        if (!found && comboBox.getSelectedItem() != customFilterSetting) {
            comboBox.setSelectedItem(customFilterSetting);
        }
    }

    private class FilterChildren extends Children.Keys implements ChangedListener<CheckNode> {

        //private Node[] oldSelection;
        //private ArrayList<Node> newSelection;
        private HashMap<Object, Node> nodeHash = new HashMap<Object, Node>();

        protected Node[] createNodes(Object object) {
            if (nodeHash.containsKey(object)) {
                return new Node[]{nodeHash.get(object)};
            }

            assert object instanceof Filter;
            Filter filter = (Filter) object;
            com.sun.hotspot.igv.filterwindow.FilterNode node = new com.sun.hotspot.igv.filterwindow.FilterNode(filter);
            node.getSelectionChangedEvent().addListener(this);
            nodeHash.put(object, node);
            return new Node[]{node};
        }

        public FilterChildren() {
            sequence.getChangedEvent().addListener(new ChangedListener<FilterChain>() {

                public void changed(FilterChain source) {
                    addNotify();
                }
            });

            setBefore(false);
        }

        protected void addNotify() {
            setKeys(sequence.getFilters());
            updateSelection();
        }

        public void changed(CheckNode source) {
            FilterNode node = (FilterNode) source;
            Filter f = node.getFilter();
            FilterChain chain = getFilterChain();
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
            updateComboBoxSelection();
        }
    }

    public FilterChain getFilterChain() {
        return filterChain;/*
    EditorTopComponent tc = EditorTopComponent.getActive();
    if (tc == null) {
    return filterChain;
    }
    return tc.getFilterChain();*/
    }

    private FilterTopComponent() {
        filterSettingsChangedEvent = new ChangedEvent<FilterTopComponent>(this);
        initComponents();
        setName(NbBundle.getMessage(FilterTopComponent.class, "CTL_FilterTopComponent"));
        setToolTipText(NbBundle.getMessage(FilterTopComponent.class, "HINT_FilterTopComponent"));
        //        setIcon(Utilities.loadImage(ICON_PATH, true));

        sequence = new FilterChain();
        filterChain = new FilterChain();
        initFilters();
        manager = new ExplorerManager();
        manager.setRootContext(new AbstractNode(new FilterChildren()));
        associateLookup(ExplorerUtils.createLookup(manager, getActionMap()));
        view = new CheckListView();

        ToolbarPool.getDefault().setPreferredIconSize(16);
        Toolbar toolBar = new Toolbar();
        Border b = (Border) UIManager.get("Nb.Editor.Toolbar.border"); //NOI18N
        toolBar.setBorder(b);
        comboBox = new JComboBox();
        toolBar.add(comboBox);
        this.add(toolBar, BorderLayout.NORTH);
        toolBar.add(SaveFilterSettingsAction.get(SaveFilterSettingsAction.class));
        toolBar.add(RemoveFilterSettingsAction.get(RemoveFilterSettingsAction.class));
        toolBar.addSeparator();
        toolBar.add(MoveFilterUpAction.get(MoveFilterUpAction.class).createContextAwareInstance(this.getLookup()));
        toolBar.add(MoveFilterDownAction.get(MoveFilterDownAction.class).createContextAwareInstance(this.getLookup()));
        toolBar.add(RemoveFilterAction.get(RemoveFilterAction.class).createContextAwareInstance(this.getLookup()));
        toolBar.add(NewFilterAction.get(NewFilterAction.class));
        this.add(view, BorderLayout.CENTER);

        filterSettings = new ArrayList<FilterSetting>();
        updateComboBox();

        comboBox.addActionListener(comboBoxActionListener);
        setChain(filterChain);
    }

    public void newFilter() {
        CustomFilter cf = new CustomFilter("My custom filter", "");
        if (cf.openInEditor()) {
            sequence.addFilter(cf);
            FileObject fo = getFileObject(cf);
            FilterChangedListener listener = new FilterChangedListener(fo, cf);
            listener.changed(cf);
            cf.getChangedEvent().addListener(listener);
        }
    }

    public void removeFilter(Filter f) {
        com.sun.hotspot.igv.filter.CustomFilter cf = (com.sun.hotspot.igv.filter.CustomFilter) f;

        sequence.removeFilter(cf);
        try {
            getFileObject(cf).delete();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

    }

    private static class FilterChangedListener implements ChangedListener<Filter> {

        private FileObject fileObject;
        private CustomFilter filter;

        public FilterChangedListener(FileObject fo, CustomFilter cf) {
            fileObject = fo;
            filter = cf;
        }

        public void changed(Filter source) {
            try {
                if (!fileObject.getName().equals(filter.getName())) {
                    FileLock lock = fileObject.lock();
                    fileObject.move(lock, fileObject.getParent(), filter.getName(), "");
                    lock.releaseLock();
                    FileObject newFileObject = fileObject.getParent().getFileObject(filter.getName());
                    fileObject = newFileObject;

                }

                FileLock lock = fileObject.lock();
                OutputStream os = fileObject.getOutputStream(lock);
                Writer w = new OutputStreamWriter(os);
                String s = filter.getCode();
                w.write(s);
                w.close();
                lock.releaseLock();

            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    public void initFilters() {

        FileSystem fs = Repository.getDefault().getDefaultFileSystem();
        FileObject folder = fs.getRoot().getFileObject(FOLDER_ID);
        FileObject[] children = folder.getChildren();

        List<CustomFilter> customFilters = new ArrayList<CustomFilter>();
        HashMap<CustomFilter, String> afterMap = new HashMap<CustomFilter, String>();
        Set<CustomFilter> enabledSet = new HashSet<CustomFilter>();
        HashMap<String, CustomFilter> map = new HashMap<String, CustomFilter>();

        for (final FileObject fo : children) {
            InputStream is = null;

            String code = "";
            FileLock lock = null;
            try {
                lock = fo.lock();
                is = fo.getInputStream();
                BufferedReader r = new BufferedReader(new InputStreamReader(is));
                String s;
                StringBuffer sb = new StringBuffer();
                while ((s = r.readLine()) != null) {
                    sb.append(s);
                    sb.append("\n");
                }
                code = sb.toString();

            } catch (FileNotFoundException ex) {
                Exceptions.printStackTrace(ex);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                try {
                    is.close();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
                lock.releaseLock();
            }

            String displayName = fo.getName();


            final CustomFilter cf = new CustomFilter(displayName, code);
            map.put(displayName, cf);

            String after = (String) fo.getAttribute(AFTER_ID);
            afterMap.put(cf, after);

            Boolean enabled = (Boolean) fo.getAttribute(ENABLED_ID);
            if (enabled != null && (boolean) enabled) {
                enabledSet.add(cf);
            }

            cf.getChangedEvent().addListener(new FilterChangedListener(fo, cf));

            customFilters.add(cf);
        }

        for (int j = 0; j < customFilters.size(); j++) {
            for (int i = 0; i < customFilters.size(); i++) {
                List<CustomFilter> copiedList = new ArrayList<CustomFilter>(customFilters);
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
            sequence.addFilter(cf);
            if (enabledSet.contains(cf)) {
                filterChain.addFilter(cf);
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
     * To obtain the singleton instance, use {@link findInstance}.
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

    @Override
    public void componentOpened() {
        Lookup.Template<FilterChain> tpl = new Lookup.Template<FilterChain>(FilterChain.class);
        result = Utilities.actionsGlobalContext().lookup(tpl);
        result.addLookupListener(this);
    }

    @Override
    public void componentClosed() {
        result.removeLookupListener(this);
        result = null;
    }

    public void resultChanged(LookupEvent lookupEvent) {
        setChain(Utilities.actionsGlobalContext().lookup(FilterChain.class));
    /*
    EditorTopComponent tc = EditorTopComponent.getActive();
    if (tc != null) {
    setChain(tc.getFilterChain());
    }*/
    }

    public void setChain(FilterChain chain) {
        updateComboBoxSelection();
    }

    private FileObject getFileObject(CustomFilter cf) {
        FileObject fo = Repository.getDefault().getDefaultFileSystem().getRoot().getFileObject(FOLDER_ID + "/" + cf.getName());
        if (fo == null) {
            try {
                fo = org.openide.filesystems.Repository.getDefault().getDefaultFileSystem().getRoot().getFileObject(FOLDER_ID).createData(cf.getName());
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return fo;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);

        out.writeInt(filterSettings.size());
        for (FilterSetting f : filterSettings) {
            out.writeUTF(f.getName());

            out.writeInt(f.getFilterCount());
            for (Filter filter : f.getFilters()) {
                CustomFilter cf = (CustomFilter) filter;
                out.writeUTF(cf.getName());
            }
        }

        CustomFilter prev = null;
        for (Filter f : this.sequence.getFilters()) {
            CustomFilter cf = (CustomFilter) f;
            FileObject fo = getFileObject(cf);
            if (getFilterChain().containsFilter(cf)) {
                fo.setAttribute(ENABLED_ID, true);
            } else {
                fo.setAttribute(ENABLED_ID, false);
            }

            if (prev == null) {
                fo.setAttribute(AFTER_ID, null);
            } else {
                fo.setAttribute(AFTER_ID, prev.getName());
            }

            prev = cf;
        }
    }

    public CustomFilter findFilter(String name) {
        for (Filter f : sequence.getFilters()) {

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
            FilterSetting s = new FilterSetting(name);
            int filterCount = in.readInt();
            for (int j = 0; j < filterCount; j++) {
                String filterName = in.readUTF();
                CustomFilter filter = findFilter(filterName);
                if (filter != null) {
                    s.addFilter(filter);
                }
            }

            filterSettings.add(s);
        }
        updateComboBox();
    }

    final static class ResolvableHelper implements Serializable {

        private static final long serialVersionUID = 1L;

        public Object readResolve() {
            return FilterTopComponent.getDefault();
        }
    }
}
