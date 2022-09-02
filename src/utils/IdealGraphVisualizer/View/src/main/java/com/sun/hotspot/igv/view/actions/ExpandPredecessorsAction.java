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

import com.sun.hotspot.igv.graph.Figure;
import com.sun.hotspot.igv.view.EditorTopComponent;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;

import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Thomas Wuerthinger
 */
public final class ExpandPredecessorsAction extends CallableSystemAction {

    @Override
    public void performAction() {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            Set<Figure> oldSelection = editor.getModel().getSelectedFigures();
            Set<Figure> figures = new HashSet<>();

            for (Figure f : editor.getModel().getDiagramToView().getFigures()) {
                boolean ok = false;
                if (oldSelection.contains(f)) {
                    ok = true;
                } else {
                    for (Figure pred : f.getSuccessors()) {
                        if (oldSelection.contains(pred)) {
                            ok = true;
                            break;
                        }
                    }
                }

                if (ok) {
                    figures.add(f);
                }
            }

            editor.getModel().showAll(figures);
        }
    }

    @Override
    public String getName() {
        return "Expand Above";
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
