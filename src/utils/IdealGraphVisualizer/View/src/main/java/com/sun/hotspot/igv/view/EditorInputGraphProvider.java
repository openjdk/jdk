/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.view;

import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.data.services.InputGraphProvider;
import java.util.Collection;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Thomas Wuerthinger
 */
@ServiceProvider(service=InputGraphProvider.class)
public class EditorInputGraphProvider implements InputGraphProvider {

    private final EditorTopComponent editor;

    public EditorInputGraphProvider() {
        editor = null;
    }

    public EditorInputGraphProvider(EditorTopComponent editor) {
        this.editor = editor;
    }

    @Override
    public InputGraph getGraph() {
        if (editor != null && EditorTopComponent.isOpen(editor)) {
            return editor.getModel().getGraph();
        } else {
            return null;
        }
    }

    @Override
    public void centerSelectedNodes() {
        if (editor != null && EditorTopComponent.isOpen(editor)) {
            editor.centerSelectedNodes();
            editor.requestActive();
        }
    }

    @Override
    public void addSelectedNodes(Collection<InputNode> nodes, boolean showIfHidden) {
        if (editor != null && EditorTopComponent.isOpen(editor)) {
            editor.addSelectedNodes(nodes, showIfHidden);
            editor.requestActive();
        }
    }

    @Override
    public void clearSelectedElements() {
        if (editor != null && EditorTopComponent.isOpen(editor)) {
            editor.clearSelectedElements();
            editor.requestActive();
        }
    }

    @Override
    public Iterable<InputGraph> searchBackward() {
        if (editor != null && EditorTopComponent.isOpen(editor)) {
            return editor.getModel().getGraphsBackward();
        } else {
            return null;
        }
    }

    @Override
    public Iterable<InputGraph> searchForward() {
        if (editor != null && EditorTopComponent.isOpen(editor)) {
            return editor.getModel().getGraphsForward();
        } else {
            return null;
        }
    }
}
