/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */
package sun.swing;

import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Data model for a type-face selection combo-box.
 */
public abstract class AbstractFilterComboBoxModel
        extends AbstractListModel<FileFilter>
        implements ComboBoxModel<FileFilter>, PropertyChangeListener {

    protected FileFilter[] filters;

    protected AbstractFilterComboBoxModel() {
        this.filters = getFileChooser().getChoosableFileFilters();
    }

    protected abstract JFileChooser getFileChooser();

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        String property = event.getPropertyName();
        if (property == JFileChooser.CHOOSABLE_FILE_FILTER_CHANGED_PROPERTY) {
            this.filters = (FileFilter[]) event.getNewValue();
            fireContentsChanged(this, -1, -1);
        } else if (property == JFileChooser.FILE_FILTER_CHANGED_PROPERTY) {
            fireContentsChanged(this, -1, -1);
        }
    }

    @Override
    public void setSelectedItem(Object filter) {
        if (filter != null) {
            getFileChooser().setFileFilter((FileFilter) filter);
            fireContentsChanged(this, -1, -1);
        }
    }

    @Override
    public Object getSelectedItem() {
        // Ensure that the current filter is in the list.
        // NOTE: we should not have to do this, since JFileChooser adds
        // the filter to the choosable filters list when the filter
        // is set. Lets be paranoid just in case someone overrides
        // setFileFilter in JFileChooser.
        FileFilter currentFilter = getFileChooser().getFileFilter();
        if (currentFilter != null) {
            for (FileFilter filter : this.filters) {
                if (filter == currentFilter) {
                    return currentFilter;
                }
            }
            getFileChooser().addChoosableFileFilter(currentFilter);
        }
        return currentFilter;
    }

    @Override
    public int getSize() {
        return (this.filters != null)
                ? filters.length
                : 0;
    }

    @Override
    public FileFilter getElementAt(int index) {
        if (index >= getSize()) {
            // This shouldn't happen. Try to recover gracefully.
            return getFileChooser().getFileFilter();
        }
        return (this.filters != null)
                ? filters[index]
                : null;
    }
}
