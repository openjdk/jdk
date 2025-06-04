/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetListener;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.openide.windows.TopComponent;

/**
 * This TopComponent is displayed in the editor location if no graphs have been loaded
 * and allows the user to easily drag and drop graph files to be opened.
 */
public class PlaceholderTopComponent extends TopComponent {
    public PlaceholderTopComponent(DropTargetListener fileDropListener) {
        setLayout(new BorderLayout());
        JPanel container = new JPanel(new BorderLayout());
        container.add(new JLabel("Drop file here to open", SwingConstants.CENTER), BorderLayout.CENTER);
        container.setDropTarget(new DropTarget(container, fileDropListener));
        add(container, BorderLayout.CENTER);
        setName("Welcome");
    }
}
