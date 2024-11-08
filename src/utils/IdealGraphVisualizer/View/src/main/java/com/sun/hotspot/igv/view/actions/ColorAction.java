/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.hotspot.igv.view.EditorTopComponent;
import java.awt.Color;
import javax.swing.JColorChooser;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;



@ActionID(category = "View", id = "com.sun.hotspot.igv.view.actions.ColorAction")
@ActionRegistration(displayName = "#CTL_ColorAction")
@ActionReferences({
        @ActionReference(path = "Menu/View", position = 360),
        @ActionReference(path = "Shortcuts", name = "D-C")
})
@Messages({
        "CTL_ColorAction=Color",
        "HINT_ColorAction=Color current set of selected nodes"
})
public final class ColorAction extends ModelAwareAction {

    @Override
    protected String iconResource() {
        return "com/sun/hotspot/igv/view/images/color.gif"; // NOI18N
    }

    @Override
    protected String getDescription() {
        return NbBundle.getMessage(ColorAction.class, "HINT_ColorAction");
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(ColorAction.class, "CTL_ColorAction");
    }

    @Override
    public void performAction(DiagramViewModel model) {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            Color selectedColor = JColorChooser.showDialog(null, "Choose a Color", Color.WHITE);
            if (selectedColor != null) {
                editor.colorSelectedFigures(selectedColor);
            }
        }
    }

    @Override
    public boolean isEnabled(DiagramViewModel model) {
        return model != null && !model.getSelectedNodes().isEmpty();
    }
}
