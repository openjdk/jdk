/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
 * @test
 * @bug 4414739
 * @requires (os.family == "windows")
 * @summary verifies that getDropSuccess() returns correct value for moving
            a file from a Java drag source to the Windows shell
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual WinMoveFileToShellTest
 */

public class WinMoveFileToShellTest {
    private static final String INSTRUCTIONS = """
            Drag from the frame titled "Drag Frame" and drop on to Windows Desktop.
            After Drag and Drop, check for "Drop Success" status in the log area.
            If "Drop Success" is true press PASS else FAIL.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .title("Test Instructions")
                      .instructions(INSTRUCTIONS)
                      .columns(40)
                      .testUI(WinMoveFileToShellTest::createAndShowUI)
                      .logArea(5)
                      .build()
                      .awaitAndCheck();
    }

    private static Frame createAndShowUI() {
        Frame frame = new Frame("Drag Frame");
        final DragSourceListener dsl = new DragSourceAdapter() {
            public void dragDropEnd(DragSourceDropEvent e) {
                PassFailJFrame.log("Drop Success: " + e.getDropSuccess());
            }
        };

        DragGestureListener dgl = dge -> {
            File file = new File(System.getProperty("test.classes", ".")
                                 + File.separator + "move.me");
            try {
                file.createNewFile();
            } catch (IOException exc) {
                exc.printStackTrace();
            }
            ArrayList<File> list = new ArrayList<>();
            list.add(file);
            dge.startDrag(null, new FileListSelection(list), dsl);
        };

        new DragSource().createDefaultDragGestureRecognizer(frame,
                                                            DnDConstants.ACTION_MOVE, dgl);
        frame.setSize(200, 100);
        return frame;
    }

    private static class FileListSelection implements Transferable {
        private static final int FL = 0;

        private static final DataFlavor[] flavors =
                new DataFlavor[] { DataFlavor.javaFileListFlavor };


        private List data;

        public FileListSelection(List data) {
            this.data = data;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return flavors.clone();
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            for (DataFlavor dataFlavor : flavors) {
                if (flavor.equals(dataFlavor)) {
                    return true;
                }
            }
            return false;
        }

        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException, IOException
        {
            if (flavor.equals(flavors[FL])) {
                return data;
            } else {
                throw new UnsupportedFlavorException(flavor);
            }
        }
    }
}
