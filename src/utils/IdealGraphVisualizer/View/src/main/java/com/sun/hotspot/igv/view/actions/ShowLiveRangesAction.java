/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.view.actions;

import com.sun.hotspot.igv.view.EditorTopComponent;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import org.openide.util.ImageUtilities;

public class ShowLiveRangesAction extends AbstractAction implements PropertyChangeListener {

    private boolean selected;
    private AbstractAction parentAction;

    public ShowLiveRangesAction(AbstractAction action, boolean select) {
        this.parentAction = action;
        this.selected = select;
        this.parentAction.addPropertyChangeListener(this);
        putValue(SELECTED_KEY, this.selected);
        putValue(SMALL_ICON, new ImageIcon(ImageUtilities.loadImage(iconResource())));
        putValue(SHORT_DESCRIPTION, "Show live ranges in control-flow graph view (if liveness information is available)");
        enableIfParentSelected();
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        this.selected = isSelected();
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            editor.getModel().setShowLiveRanges(this.selected);
        }
    }

    protected String iconResource() {
        return "com/sun/hotspot/igv/view/images/showLiveRanges.png";
    }

    private boolean isSelected() {
        return (Boolean)getValue(SELECTED_KEY);
    }

    private void enableIfParentSelected() {
        boolean enable = parentAction.isEnabled() && (Boolean)parentAction.getValue(SELECTED_KEY);
        if (enable != this.isEnabled()) {
            if (enable) {
                putValue(SELECTED_KEY, this.selected);
            } else {
                this.selected = isSelected();
                putValue(SELECTED_KEY, false);
            }
        }
        this.setEnabled(enable);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() == this.parentAction) {
            enableIfParentSelected();
        }
    }
}
