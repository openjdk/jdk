/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.Set;
import javax.swing.Action;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.Utilities;

/**
 *
 * @author Thomas Wuerthinger
 */
public final class HideAction extends ContextAction<DiagramViewModel> {

    public HideAction() {
        putValue(Action.SHORT_DESCRIPTION, "Hide selected nodes");
        // D is the Control key on most platforms, the Command (meta) key on Macintosh
        putValue(Action.ACCELERATOR_KEY, Utilities.stringToKey("D-H"));
    }

    @Override
    public String getName() {
        return "Hide";
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public void performAction(DiagramViewModel model) {
        Set<Integer> selectedNodes = model.getSelectedNodes();
        HashSet<Integer> nodes = new HashSet<>(model.getHiddenNodes());
        nodes.addAll(selectedNodes);
        model.showNot(nodes);
    }

    @Override
    protected String iconResource() {
        return "com/sun/hotspot/igv/view/images/hide.gif";
    }

    @Override
    public boolean isEnabled(DiagramViewModel model) {
        return model != null && !model.getSelectedNodes().isEmpty();
    }

    @Override
    public Class<DiagramViewModel> contextClass() {
        return DiagramViewModel.class;
    }

    @Override
    public void addContextListener(DiagramViewModel model) {
        model.getViewChangedEvent().addListener(this);
        model.getDiagramChangedEvent().addListener(this);
        model.getViewPropertiesChangedEvent().addListener(this);
        model.getHiddenNodesChangedEvent().addListener(this);
    }

    @Override
    public void removeContextListener(DiagramViewModel model) {
        model.getViewChangedEvent().removeListener(this);
        model.getDiagramChangedEvent().removeListener(this);
        model.getViewPropertiesChangedEvent().removeListener(this);
        model.getHiddenNodesChangedEvent().removeListener(this);
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return this;
    }
}
