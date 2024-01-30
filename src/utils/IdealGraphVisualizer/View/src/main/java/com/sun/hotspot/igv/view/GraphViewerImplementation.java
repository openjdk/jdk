/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.view;

import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.services.GraphViewer;
import com.sun.hotspot.igv.difference.Difference;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Thomas Wuerthinger
 */
@ServiceProvider(service=GraphViewer.class)
public class GraphViewerImplementation implements GraphViewer {

    @Override
    public InputGraph viewDifference(InputGraph firstGraph, InputGraph secondGraph) {
        if (firstGraph.getGroup() != secondGraph.getGroup()) {
            InputGraph diffGraph = Difference.createDiffGraph(firstGraph, secondGraph);
            return view(diffGraph, true);
        } else {
            if (view(firstGraph, true) != null) {
                EditorTopComponent etc = EditorTopComponent.findEditorForGraph(firstGraph);
                if (etc != null) {
                    etc.getModel().selectDiffGraph(secondGraph);
                    etc.requestActive();
                    return etc.getModel().getGraph();
                }
            }
            return null;
        }
    }

    @Override
    public InputGraph view(InputGraph graph, boolean newTab) {
        if (!newTab) {
            EditorTopComponent etc = EditorTopComponent.findEditorForGraph(graph);
            if (etc != null) {
                etc.getModel().selectGraph(graph);
                etc.requestActive();
                return etc.getModel().getGraph();
            }
        }
        DiagramViewModel model = new DiagramViewModel(graph);
        EditorTopComponent etc = new EditorTopComponent(model);
        etc.open();
        etc.requestActive();
        return etc.getModel().getGraph();
    }
}
