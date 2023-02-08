/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.hotspot.igv.coordinator;

import com.sun.hotspot.igv.coordinator.actions.RemoveCookie;
import com.sun.hotspot.igv.data.*;
import com.sun.hotspot.igv.util.PropertiesSheet;
import com.sun.hotspot.igv.util.StringUtils;
import java.awt.Image;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Action;
import org.openide.actions.PropertiesAction;
import org.openide.actions.RenameAction;
import org.openide.nodes.*;
import org.openide.util.ImageUtilities;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

/**
 *
 * @author Thomas Wuerthinger
 */
public class FolderNode extends AbstractNode {

    private final InstanceContent content;
    private final FolderChildren children;
    // NetBeans node corresponding to each opened graph. Used to highlight the
    // focused graph in the Outline window.
    private static final Map<InputGraph, GraphNode> graphNodeMap = new HashMap<>();
    private boolean selected = false;

    private static class FolderChildren extends Children.Keys<FolderElement> implements ChangedListener {

        private final Folder folder;

        public FolderChildren(Folder folder) {
            this.folder = folder;
            folder.getChangedEvent().addListener(this);
        }

        public Folder getFolder() {
            return folder;
        }

        @Override
        protected Node[] createNodes(FolderElement folderElement) {
            if (folderElement instanceof InputGraph) {
                InputGraph inputGraph = (InputGraph) folderElement;
                GraphNode graphNode = new GraphNode(inputGraph);
                graphNodeMap.put(inputGraph, graphNode);
                return new Node[]{graphNode};
            } else if (folderElement instanceof Group) {
                return new Node[]{new FolderNode((Group) folderElement)};
            } else {
                return null;
            }
        }

        @Override
        protected void destroyNodes(Node[] nodes) {
            for (Node n : nodes) {
                // Each node is only present once in the graphNode map.
                graphNodeMap.values().remove(n);
            }
            for (Node node : getNodes()) {
                node.setDisplayName(node.getDisplayName());
            }
        }

        @Override
        public void addNotify() {
            super.addNotify();
            setKeys(folder.getElements());
        }

        @Override
        public void changed(Object source) {
            addNotify();
        }
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        if (children.folder instanceof Properties.Entity) {
            Properties.Entity p = (Properties.Entity) children.folder;
            PropertiesSheet.initializeSheet(p.getProperties(), s);
        }
        return s;
    }

    @Override
    public Image getIcon(int i) {
        if (selected) {
            return ImageUtilities.loadImage("com/sun/hotspot/igv/coordinator/images/folder_selected.png");
        } else {
            return ImageUtilities.loadImage("com/sun/hotspot/igv/coordinator/images/folder.png");
        }
    }

    protected FolderNode(Folder folder) {
        this(folder, new FolderChildren(folder), new InstanceContent());
    }

    private FolderNode(final Folder folder, FolderChildren children, InstanceContent content) {
        super(children, new AbstractLookup(content));
        this.content = content;
        this.children = children;
        if (folder instanceof FolderElement) {
            final FolderElement folderElement = (FolderElement) folder;
            this.setDisplayName(folderElement.getName());
            this.content.add((RemoveCookie) () -> {
                children.destroyNodes(children.getNodes());
                folderElement.getParent().removeElement(folderElement);
            });
        }
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        fireDisplayNameChange(null, null);
        fireIconChange();
    }

    @Override
    public boolean canRename() {
        return true;
    }

    @Override
    public void setName(String name) {
        children.getFolder().setName(name);
        fireDisplayNameChange(null, null);
    }

    @Override
    public String getName() {
        return children.getFolder().getName();
    }

    @Override
    public String getDisplayName() {
        return children.getFolder().getDisplayName();
    }

    @Override
    public String getHtmlDisplayName() {
        String htmlDisplayName = StringUtils.escapeHTML(getDisplayName());
        if (selected) {
            htmlDisplayName = "<b>" + htmlDisplayName + "</b>";
        }
        return htmlDisplayName;
    }

    public boolean isRootNode() {
        Folder folder = getFolder();
        return (folder instanceof GraphDocument);
    }

    @Override
    public Image getOpenedIcon(int i) {
        return getIcon(i);
    }

    @Override
    public Action[] getActions(boolean b) {
        return new Action[]{
                RenameAction.findObject(RenameAction.class, true),
                PropertiesAction.findObject(PropertiesAction.class, true),
        };
    }

    public static void clearGraphNodeMap() {
        graphNodeMap.clear();
    }

    public static GraphNode getGraphNode(InputGraph graph) {
        return graphNodeMap.get(graph);
    }

    public Folder getFolder() {
        return children.getFolder();
    }
}
