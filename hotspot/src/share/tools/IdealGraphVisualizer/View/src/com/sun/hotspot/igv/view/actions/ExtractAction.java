/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */
package com.sun.hotspot.igv.view.actions;

import com.sun.hotspot.igv.view.EditorTopComponent;
import java.awt.Event;
import java.awt.event.KeyEvent;
import javax.swing.Action;
import javax.swing.KeyStroke;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;

/**
 *
 * @author Thomas Wuerthinger
 */
public final class ExtractAction extends CallableSystemAction {

    public void performAction() {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            editor.extract();
        }
    }

    public ExtractAction() {
        putValue(Action.SHORT_DESCRIPTION, "Extract current set of selected nodes");
        putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_X, Event.CTRL_MASK, false));
    }

    public String getName() {
        return "Extract action";
    }

    @Override
    protected void initialize() {
        super.initialize();
    }

    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }

    @Override
    protected String iconResource() {
        return "com/sun/hotspot/igv/view/images/extract.gif";
    }
}
