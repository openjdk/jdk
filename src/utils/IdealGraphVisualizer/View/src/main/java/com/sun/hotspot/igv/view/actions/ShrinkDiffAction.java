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

import com.sun.hotspot.igv.data.ChangedListener;
import com.sun.hotspot.igv.util.ContextAction;
import com.sun.hotspot.igv.view.DiagramViewModel;
import javax.swing.Action;
import javax.swing.ImageIcon;
import org.openide.util.*;

public final class ShrinkDiffAction extends ContextAction<DiagramViewModel> implements ChangedListener<DiagramViewModel> {
    private DiagramViewModel model;

    public ShrinkDiffAction() {
        putValue(Action.SHORT_DESCRIPTION, "Reduce the difference selection");
        putValue(Action.SMALL_ICON, new ImageIcon(ImageUtilities.loadImage("com/sun/hotspot/igv/view/images/shrink_right.png")));
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(ShrinkDiffAction.class, "CTL_ShrinkDiffAction");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public Class<DiagramViewModel> contextClass() {
        return DiagramViewModel.class;
    }

    @Override
    public void performAction(DiagramViewModel model) {
        int firstPos = model.getFirstPosition();
        int secondPos = model.getSecondPosition();
        if (secondPos != model.getPositions().size() - 1) {
            model.setPositions(firstPos, (firstPos < secondPos) ? secondPos - 1 : secondPos);
        }
    }

    @Override
    public void update(DiagramViewModel model) {
        super.update(model);

        if (this.model != model) {
            if (this.model != null) {
                this.model.getDiagramChangedEvent().removeListener(this);
            }

            this.model = model;
            if (this.model != null) {
                this.model.getDiagramChangedEvent().addListener(this);
            }
        }
    }

    @Override
    public boolean isEnabled(DiagramViewModel model) {
        return model.getSecondPosition() != model.getPositions().size() - 1;
    }

    @Override
    public Action createContextAwareInstance(Lookup arg0) {
        return new ShrinkDiffAction();
    }

    @Override
    public void changed(DiagramViewModel source) {
        update(source);
    }
}
