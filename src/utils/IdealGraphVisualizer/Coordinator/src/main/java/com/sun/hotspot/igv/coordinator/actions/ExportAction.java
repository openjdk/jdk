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

package com.sun.hotspot.igv.coordinator.actions;

import com.sun.hotspot.igv.coordinator.FolderNode;
import com.sun.hotspot.igv.coordinator.OutlineTopComponent;
import com.sun.hotspot.igv.data.Folder;
import com.sun.hotspot.igv.data.GraphDocument;
import com.sun.hotspot.igv.data.Group;
import javax.swing.Action;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.actions.NodeAction;


public final class ExportAction extends NodeAction {

    public ExportAction() {
        putValue(Action.SHORT_DESCRIPTION, "Export selected groups to XML file...");
        putValue(Action.SMALL_ICON, ImageUtilities.loadImageIcon(iconResource(), true));
    }

    @Override
    protected void performAction(Node[] activatedNodes) {
        GraphDocument doc = new GraphDocument();
        for (Node node : activatedNodes) {
            if (node instanceof FolderNode folderNode) {
                Folder folder = folderNode.getFolder();
                if (folder instanceof Group group) {
                    doc.addElement(group);
                }
            }
        }

        OutlineTopComponent.exportToXML(doc);
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(ExportAction.class, "CTL_ExportAction");
    }

    @Override
    protected String iconResource() {
        return "com/sun/hotspot/igv/coordinator/images/export.png";
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }

    @Override
    protected boolean enable(Node[] nodes) {
        if (nodes.length > 0) {
            for (Node n : nodes) {
                if (!(n instanceof FolderNode) || ((FolderNode) n).isRootNode()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
