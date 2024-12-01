/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.coordinator.actions;

import com.sun.hotspot.igv.coordinator.OutlineTopComponent;
import javax.swing.Action;
import org.openide.util.HelpCtx;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.actions.CallableSystemAction;


public final class SaveAction extends CallableSystemAction {

    public SaveAction() {
        putValue(Action.SHORT_DESCRIPTION, "Save");
        // D is the Control key on most platforms, the Command (meta) key on Macintosh
        putValue(Action.ACCELERATOR_KEY, Utilities.stringToKey("D-S"));
        putValue(Action.SMALL_ICON, ImageUtilities.loadImageIcon(iconResource(), true));
    }

    @Override
    public void performAction() {
        OutlineTopComponent.findInstance().save();
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(SaveAction.class, "CTL_SaveAction");
    }

    @Override
    protected String iconResource() {
        return "com/sun/hotspot/igv/coordinator/images/save.png";
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }
}
