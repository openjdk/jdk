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

import java.util.HashSet;
import java.util.Set;

import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.view.DiagramViewModel;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;


/**
 * @author Thomas Wuerthinger
 */
@ActionID(category = "View", id = "com.sun.hotspot.igv.view.actions.ExtractAction")
@ActionRegistration(displayName = "#CTL_ExtractAction")
@ActionReferences({
        @ActionReference(path = "Menu/View", position = 350),
        @ActionReference(path = "Shortcuts", name = "D-X")
})
@Messages({
        "CTL_ExtractAction=Extract",
        "HINT_ExtractAction=Extract selected nodes and live ranges"
})
public final class ExtractAction extends ModelAwareAction {

    @Override
    protected String iconResource() {
        return "com/sun/hotspot/igv/view/images/extract.gif"; // NOI18N
    }

    @Override
    protected String getDescription() {
        return NbBundle.getMessage(ExtractAction.class, "HINT_ExtractAction");
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(ExtractAction.class, "CTL_ExtractAction");
    }

    @Override
    public void performAction(DiagramViewModel model) {
        Set<Integer> nodes = new HashSet<>(model.getSelectedNodes());
        InputGraph graph = model.getDiagram().getInputGraph();
        for (int liveRangeId : model.getSelectedLiveRanges()) {
            for (InputNode node : graph.getRelatedNodes(liveRangeId)) {
                nodes.add(node.getId());
            }
        }
        model.showOnly(nodes);
    }

    @Override
    public boolean isEnabled(DiagramViewModel model) {
        return model != null &&
               !(model.getSelectedNodes().isEmpty() && model.getSelectedLiveRanges().isEmpty());
    }
}
