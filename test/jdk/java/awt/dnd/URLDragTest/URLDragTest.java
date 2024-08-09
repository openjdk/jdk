/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;

/*
 * @test
 * @bug 8031964
 * @summary Dragging images from the browser does not work.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual URLDragTest
*/

public class URLDragTest {
    private static final String INSTRUCTIONS = """
            1) When the test starts, open any browser.
            2) Drag any image from the browser page onto the RED window.
            3) When the image is dropped you should see the list of available
               DataFlavors in the log area below the instruction window.
            4) If you see application/x-java-url and text/uri-list flavors in
               the logs then please press PASS, else FAIL.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .logArea(8)
                .testUI(URLDragTest::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame frame = new Frame("Browser Image DnD Test");
        frame.setBackground(Color.RED);
        frame.setDropTarget(new DropTarget(frame,
                DnDConstants.ACTION_COPY,
                new DropTargetAdapter() {
                    @Override
                    public void dragEnter(DropTargetDragEvent dtde) {
                        dtde.acceptDrag(DnDConstants.ACTION_COPY);
                    }

                    @Override
                    public void dragOver(DropTargetDragEvent dtde) {
                        dtde.acceptDrag(DnDConstants.ACTION_COPY);
                    }

                    @Override
                    public void drop(DropTargetDropEvent dtde) {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        dtde.getCurrentDataFlavorsAsList()
                                .stream()
                                .map(DataFlavor::toString)
                                .forEach(PassFailJFrame::log);
                    }
                }));

        frame.setSize(400, 200);
        frame.setAlwaysOnTop(true);
        return frame;
    }
}
