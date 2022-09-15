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
package com.sun.hotspot.igv.view.actions;

import com.sun.hotspot.igv.util.ContextAction;
import com.sun.hotspot.igv.view.DiagramViewModel;
import javax.swing.Action;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;


@ActionID(category = "View", id = "com.sun.hotspot.igv.view.actions.PrevDiagramAction")
@ActionRegistration(displayName = "#CTL_PrevDiagramAction")
@ActionReferences({
        @ActionReference(path = "Menu/View", position = 100),
        @ActionReference(path = "Shortcuts", name = "D-LEFT")
})
@Messages({
        "CTL_PrevDiagramAction=Show previous graph",
        "HINT_PrevDiagramAction=Show previous graph of current group"
})
public final class PrevDiagramAction extends ContextAction<DiagramViewModel> {

    public PrevDiagramAction() {
        putValue(Action.SHORT_DESCRIPTION, Bundle.HINT_PrevDiagramAction());
        putValue(Action.SMALL_ICON , ImageUtilities.loadImageIcon(iconResource(), true));
    }

    @Override
    protected String iconResource() {
        return "com/sun/hotspot/igv/view/images/prev_diagram.png"; // NOI18N
    }

    @Override
    public String getName() {
        return Bundle.CTL_PrevDiagramAction();
    }

    @Override
    public void performAction(DiagramViewModel model) {
        if (model.getFirstPosition() != 0) {
            model.setPositions(model.getFirstPosition() - 1, model.getSecondPosition() - 1);
        }
    }

    @Override
    public boolean isEnabled(DiagramViewModel model) {
        return model.getFirstPosition() != 0;
    }

    @Override
    public Class<DiagramViewModel> contextClass() {
        return DiagramViewModel.class;
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return this;
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
}
