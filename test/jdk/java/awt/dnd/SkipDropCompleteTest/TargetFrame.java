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

import java.awt.Frame;
import java.awt.TextArea;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class TargetFrame extends Frame implements DropTargetListener{

    DropTarget dropTarget;
    TextArea textArea;

    public TargetFrame() {
        super("SkipDropCompleteTest Target Frame");
        textArea = new TextArea();
        add(textArea);

        addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                        System.exit(0);
                }
        });

        setSize(250,250);
        setLocation(350,50);
        this.setVisible(true);

        dropTarget = new DropTarget(textArea,this);
    }

    public void dragEnter(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
        }

    public void dragOver(DropTargetDragEvent dtde) {}

    public void dragExit(DropTargetEvent dte) { }

    public void dropActionChanged(DropTargetDragEvent dtde ) {}

    public void drop(DropTargetDropEvent dtde) {
        try {
            Transferable transferable = dtde.getTransferable();
            dtde.acceptDrop(DnDConstants.ACTION_MOVE);

            String str = (String)transferable.getTransferData(TransferableObject.stringFlavor);
            textArea.setText(str);
        } catch (Exception ufException ) {
                  ufException.printStackTrace();
                  System.err.println( "Exception" + ufException.getMessage());
                  dtde.rejectDrop();
        }
    }
}
