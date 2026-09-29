/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 7075105
  @summary WIN: Provide a way to format HTML on drop
  @library /java/awt/regtesthelpers
  @run main/manual ManualHTMLDataFlavorTest
*/


import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.IOException;

public class ManualHTMLDataFlavorTest {

    static class DropPane extends Panel implements DropTargetListener {

        DropPane() {
            requestFocus();
            setBackground(Color.red);
            setDropTarget(new DropTarget(this, DnDConstants.ACTION_COPY, this));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(400, 400);
        }

        @Override
        public void dragEnter(DropTargetDragEvent dtde) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        }

        @Override
        public void dragOver(DropTargetDragEvent dtde) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        }

        @Override
        public void dropActionChanged(DropTargetDragEvent dtde) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        }

        @Override
        public void dragExit(DropTargetEvent dte) {}

        @Override
        public void drop(DropTargetDropEvent dtde) {
            if (!dtde.isDataFlavorSupported(DataFlavor.allHtmlFlavor)) {
                ManualHTMLDataFlavorTest.log("DataFlavor.allHtmlFlavor is not present in the system clipboard");
                dtde.rejectDrop();
                return;
            } else if (!dtde.isDataFlavorSupported(DataFlavor.fragmentHtmlFlavor)) {
                ManualHTMLDataFlavorTest.log("DataFlavor.fragmentHtmlFlavor is not present in the system clipboard");
                dtde.rejectDrop();
                return;
            } else if (!dtde.isDataFlavorSupported(DataFlavor.selectionHtmlFlavor)) {
                ManualHTMLDataFlavorTest.log("DataFlavor.selectionHtmlFlavor is not present in the system clipboard");
                dtde.rejectDrop();
                return;
            }

            dtde.acceptDrop(DnDConstants.ACTION_COPY);

            Transferable t = dtde.getTransferable();
            try {
                ManualHTMLDataFlavorTest.log("ALL:");
                ManualHTMLDataFlavorTest.log(t.getTransferData(DataFlavor.allHtmlFlavor).toString());
                t.getTransferData(DataFlavor.allHtmlFlavor).toString();
                ManualHTMLDataFlavorTest.log("FRAGMENT:");
                ManualHTMLDataFlavorTest.log(t.getTransferData(DataFlavor.fragmentHtmlFlavor).toString());
                ManualHTMLDataFlavorTest.log("SELECTION:");
                ManualHTMLDataFlavorTest.log(t.getTransferData(DataFlavor.selectionHtmlFlavor).toString());
            } catch (UnsupportedFlavorException | IOException e) {
                e.printStackTrace();
            }

        }
    }

    static final String INSTRUCTIONS = """
        1) The test contains a drop-aware panel with a red background.
        2) Open some page in a browser, select some text.
           Drag and drop it on the red panel.
           IMPORTANT NOTE: the page should be stored locally.
           Otherwise for instance Internet Explorer may prohibit drag and drop from
           the browser to other applications because of protected mode restrictions.
           On MacOS do NOT use Safari, it does not provide the needed DataFlavor.
        3) Check the data in the output area of this window.
        5) The output should not contain information that any of
           flavors is not present in the system clipboard.
        6) The output should contain data in three different formats
           provided by the system clipboard.
            - Data after the "ALL:" marker should include the data
              from the "SELECTION:" marker".
            - Data after the "FRAGMENT" marker should include the data
              from the "SELECTION:" marker and may be some closing
              tags could be added to the mark-up.
            - Data after the "SELECTION:" marker should correspond
              to the data selected in the browser.
        7) If the above requirements are met, the test is passed.
    """;

   static Frame createDropWindow() {
        Frame frame = new Frame("Manual HTML DataFlavor Test");
        frame.add(new DropPane());
        frame.setAlwaysOnTop(true);
        frame.pack();
        return frame;
    }

   static void log(String msg) {
       PassFailJFrame.log(msg);
   }

   public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .rows(25)
            .columns(50)
            .testUI(ManualHTMLDataFlavorTest::createDropWindow)
            .logArea()
            .build()
            .awaitAndCheck();
   }
}
