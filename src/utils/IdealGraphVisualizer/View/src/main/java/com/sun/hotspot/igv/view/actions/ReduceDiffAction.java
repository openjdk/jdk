/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.view.DiagramViewModel;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;


@ActionID(category = "View", id = "com.sun.hotspot.igv.view.actions.ReduceDiffAction")
@ActionRegistration(displayName = "#CTL_ReduceDiffAction")
@ActionReferences({
        @ActionReference(path = "Menu/View", position = 200),
        @ActionReference(path = "Shortcuts", name = "DS-LEFT"),
        @ActionReference(path = "Shortcuts", name = "D-DOWN")
})
@Messages({
        "CTL_ReduceDiffAction=Reduce difference selection",
        "HINT_ReduceDiffAction=Reduce the difference selection"
})
public final class ReduceDiffAction extends ModelAwareAction {

    @Override
    protected String iconResource() {
        return "com/sun/hotspot/igv/view/images/shrink_right.png"; // NOI18N
    }

    @Override
    protected String getDescription() {
        return NbBundle.getMessage(NextDiagramAction.class, "HINT_ReduceDiffAction");
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(NextDiagramAction.class, "CTL_ReduceDiffAction");
    }

    @Override
    public void performAction(DiagramViewModel model) {
        int firstPos = model.getFirstPosition();
        int secondPos = model.getSecondPosition();
        if (firstPos < secondPos) {
            model.setPositions(firstPos, secondPos - 1);
        }
    }

    @Override
    public boolean isEnabled(DiagramViewModel model) {
        return model.getFirstPosition() != model.getSecondPosition();
    }
}
