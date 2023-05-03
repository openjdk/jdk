/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Frame;
import java.awt.TextArea;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class SourceFrame extends Frame
        implements DragSourceListener,DragGestureListener {

    DragSource dragSource;
    TransferableObject transferableObject;
        TextArea textArea;

    public SourceFrame() {

        super("SkipDropCompleteTest Source Frame");

        dragSource = new DragSource();
        textArea = new TextArea("Drag the Text from the SourceFrame\n" +
            "and drop it on the TextArea in the\n" +
            "Target Frame.\n" +
            "Try to do some operation, like closing\n" +
            "of the frame.\n"+
            "See whether the application hangs.");
        add(textArea);

        dragSource.createDefaultDragGestureRecognizer(textArea, DnDConstants.ACTION_COPY, this);

        addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                        System.exit(0);
                }
        });

        setSize(250,250);
                setLocation(50,50);
                setBackground(Color.red);
                this.setVisible(true);
    }

    public void dragEnter(DragSourceDragEvent dsde) { }

    public void dragOver(DragSourceDragEvent dsde) { }

    public void dragExit(DragSourceEvent dse) { }

    public void dropActionChanged(DragSourceDragEvent dsde ) { }

    public void dragDropEnd(DragSourceDropEvent dsde) { }

    public void dragGestureRecognized(DragGestureEvent dge) {
        transferableObject = new TransferableObject(textArea.getText());
        dragSource.startDrag(dge, DragSource.DefaultCopyDrop,
                transferableObject, this);
    }
}
