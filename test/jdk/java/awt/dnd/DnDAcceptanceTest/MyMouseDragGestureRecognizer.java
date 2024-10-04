/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

/* Due to 2 AWT bugs:
 * 4117525  mouseDragged events on Win32 do not set BUTTON1_MASK correctly
 * 4117523  Solaris: MousePressed event has modifier=0 when left button is
 *
 * MyMouseDragGestureRecognizer is subclass from MouseDragGestureRecognizer
 * for workaround the problems.
 */

import java.awt.Component;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.MouseDragGestureRecognizer;
import java.awt.event.MouseEvent;

class MyMouseDragGestureRecognizer extends MouseDragGestureRecognizer {

    MyMouseDragGestureRecognizer(DragSource ds, Component c, int actions,
                               DragGestureListener dgl) {
        super(ds, c, actions, dgl);
    }

    public void mousePressed(MouseEvent e) {
        System.err.println("mouse pressed");
        appendEvent(e);
        fireDragGestureRecognized(DnDConstants.ACTION_COPY, e.getPoint());
    }
}
