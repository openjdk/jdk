/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.util.ContextAction;
import com.sun.hotspot.igv.view.DiagramViewModel;
import javax.swing.Action;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;


public abstract class ModelAwareAction extends ContextAction<DiagramViewModel> {

    public ModelAwareAction() {
        putValue(Action.SMALL_ICON , ImageUtilities.loadImageIcon(iconResource(), true));
        putValue(Action.SHORT_DESCRIPTION, getDescription());
    }

    protected abstract String getDescription();

    public abstract String getName();

    public abstract void performAction(DiagramViewModel model);

    public abstract boolean isEnabled(DiagramViewModel model);

    @Override
    public Class<DiagramViewModel> contextClass() {
        return DiagramViewModel.class;
    }

    @Override
    public void addContextListener(DiagramViewModel model) {
        model.getSelectedElementsChangedEvent().addListener(this);
        model.getDiagramChangedEvent().addListener(this);
        model.getGraphChangedEvent().addListener(this);
        model.getHiddenNodesChangedEvent().addListener(this);
    }

    @Override
    public void removeContextListener(DiagramViewModel model) {
        model.getSelectedElementsChangedEvent().removeListener(this);
        model.getDiagramChangedEvent().removeListener(this);
        model.getGraphChangedEvent().removeListener(this);
        model.getHiddenNodesChangedEvent().removeListener(this);
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return this;
    }
}
