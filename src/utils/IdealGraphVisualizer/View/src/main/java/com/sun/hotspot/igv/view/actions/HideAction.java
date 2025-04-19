/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.view.DiagramViewModel;
import java.util.HashSet;
import java.util.Set;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;


/**
 * @author Thomas Wuerthinger
 */
@ActionID(category = "View", id = "com.sun.hotspot.igv.view.actions.HideAction")
@ActionRegistration(displayName = "#CTL_HideAction")
@ActionReferences({
        @ActionReference(path = "Menu/View", position = 400),
        @ActionReference(path = "Shortcuts", name = "D-H")
})
@Messages({
        "CTL_HideAction=Hide",
        "HINT_HideAction=Hide selected nodes and live ranges"
})
public final class HideAction extends ModelAwareAction {

    @Override
    protected String iconResource() {
        return "com/sun/hotspot/igv/view/images/hide.gif"; // NOI18N
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(HideAction.class, "CTL_HideAction");
    }

    @Override
    protected String getDescription() {
        return NbBundle.getMessage(HideAction.class, "HINT_HideAction");
    }

    @Override
    public void performAction(DiagramViewModel model) {
        Set<Integer> selectedNodes = model.getSelectedNodes();
        HashSet<Integer> nodes = new HashSet<>(model.getHiddenNodes());
        nodes.addAll(selectedNodes);
        InputGraph graph = model.getDiagram().getInputGraph();
        for (int liveRangeId : model.getSelectedLiveRanges()) {
            for (InputNode node : graph.getRelatedNodes(liveRangeId)) {
                nodes.add(node.getId());
            }
        }
        model.setHiddenNodes(nodes);
    }

    @Override
    public boolean isEnabled(DiagramViewModel model) {
        return model != null &&
               !(model.getSelectedNodes().isEmpty() && model.getSelectedLiveRanges().isEmpty());
    }
}
