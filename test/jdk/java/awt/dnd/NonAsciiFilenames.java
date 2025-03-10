/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4187490
 * @summary Verify that Non-ASCII file names can be dragged and dropped
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual NonAsciiFilenames
 */

import java.awt.Color;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.File;
import java.util.AbstractList;

import javax.swing.JFrame;
import javax.swing.JLabel;

public class NonAsciiFilenames {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                This test must be run on an OS which does not use ISO 8859-1
                as its default encoding.

                Open a native file browsing application, such as Windows
                Explorer. Try to find a file whose name uses non-ISO 8859-1
                characters. Create a file and name it such that it contains
                non-ISO 8859-1 characters (For ex. é, à, ö, €, ¥). Drag
                the file from the native application and drop it on the test
                Frame. If the file name appears normally, then the test passes.
                If boxes or question marks appear for characters, or if you see
                the word "Error", then the test fails.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(NonAsciiFilenames::createUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createUI() {
        JFrame frame = new JFrame();
        frame.setTitle("DropLabel test");
        frame.getContentPane().add(new DropLabel("Drop here"));
        frame.setSize(300, 100);
        return frame;
    }
}

class DropLabel extends JLabel implements DropTargetListener {
    public DropLabel(String s) {
        setText(s);
        new DropTarget(this, DnDConstants.ACTION_COPY, this, true);
        showDrop(false);
    }

    private void showDrop(boolean b) {
        setForeground(b ? Color.white : Color.black);
    }

    /**
     * Configure to desired flavor of dropped data.
     */
    private DataFlavor getDesiredFlavor() {
        return DataFlavor.javaFileListFlavor;
    }

    /**
     * Check to make sure that the contains the expected object types.
     */
    private void checkDroppedData(Object data) {
        System.out.println("Got data: " + data.getClass().getName());
        if (data instanceof AbstractList) {
            AbstractList files = (AbstractList) data;
            if (((File) files.get(0)).isFile())
                setText(((File) files.get(0)).toString());
            else
                setText("Error: not valid file: " +
                        ((File) files.get(0)).toString());
        } else {
            System.out.println("Error: wrong type of data dropped");
        }
    }

    private boolean isDragOk(DropTargetDragEvent e) {
        boolean canDrop = false;
        try {
            canDrop = e.isDataFlavorSupported(getDesiredFlavor());
        } catch (Exception ex) {
        }

        if (canDrop)
            e.acceptDrag(DnDConstants.ACTION_COPY);
        else
            e.rejectDrag();
        showDrop(canDrop);
        return canDrop;
    }

    public void dragEnter(DropTargetDragEvent e) {
        isDragOk(e);
    }


    public void dragOver(DropTargetDragEvent e) {
        isDragOk(e);
    }

    public void dropActionChanged(DropTargetDragEvent e) {
        isDragOk(e);
    }

    public void dragExit(DropTargetEvent e) {
        showDrop(false);
    }

    public void drop(DropTargetDropEvent e) {
        try {
            e.acceptDrop(DnDConstants.ACTION_COPY);
            checkDroppedData(e.getTransferable().
                    getTransferData(getDesiredFlavor()));
        } catch (Exception err) {
        }
        e.dropComplete(true);
        showDrop(false);
    }
}
